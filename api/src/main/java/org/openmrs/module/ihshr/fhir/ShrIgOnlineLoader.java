package org.openmrs.module.ihshr.fhir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.ImplementationGuide;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;
import org.openmrs.module.ihmodule.api.patientexchange.config.FhirContextHolder;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationOptions;
import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.instance.model.api.IBaseResource;

/**
 * Online validator that loads IH IG artifacts from the canonical web site as JSON. This does NOT
 * use a package server; it fetches the generated JSON endpoints: - StructureDefinition-<id>.json -
 * ValueSet-<id>.json - CodeSystem-<id>.json This keeps implementation simple and matches your
 * request to validate by IG URL.
 */
public class ShrIgOnlineLoader {
	
	private static final Set<String> LOADED_URLS = Collections.synchronizedSet(new HashSet<String>());
	
	private final FhirContext fhirContext;
	
	private final PrePopulatedValidationSupport prePopulated;
	
	public ShrIgOnlineLoader() {
		this(FhirContextHolder.R4);
	}
	
	public ShrIgOnlineLoader(FhirContext ctx) {
		this.fhirContext = ctx;
		this.prePopulated = new PrePopulatedValidationSupport(ctx);
	}
	
	public void ensureLoadedForProfileUrl(String structureDefinitionCanonicalUrl) {
		// We rely on known IDs for IH profiles. If unknown, do nothing (base validation only).
		String id = extractIdFromCanonical(structureDefinitionCanonicalUrl);
		if (id == null) {
			return;
		}
		
		// Load the StructureDefinition itself
		loadStructureDefinitionById(id);
		
		// Always load shared SHR meta + transaction bundle profiles since these are referenced widely
		loadStructureDefinitionById("ih-shr-push-meta");
		loadStructureDefinitionById("ih-transaction-bundle");
		
		// Minimal terminology resources used by meta tags (safe even if missing)
		loadCodeSystemById("ih-meta-tag-origin");
		loadCodeSystemById("ih-meta-tag-lifecycle");
	}
	
	public ValidationResult validateOrThrow(IBaseResource resource, String profileUrl) {
		ensureLoadedForProfileUrl(profileUrl);
		
		FhirValidator validator = fhirContext.newValidator();
		FhirInstanceValidator instanceValidator = new FhirInstanceValidator(fhirContext);
		
		ValidationSupportChain supportChain = new ValidationSupportChain(new DefaultProfileValidationSupport(fhirContext),
		        prePopulated, new InMemoryTerminologyServerValidationSupport(fhirContext));
		instanceValidator.setValidationSupport(supportChain);
		validator.registerValidatorModule(instanceValidator);
		
		ValidationOptions options = new ValidationOptions();
		if (profileUrl != null && !profileUrl.trim().isEmpty()) {
			options.addProfile(profileUrl.trim());
		}
		
		ValidationResult result = validator.validateWithResult(resource, options);
		if (result.isSuccessful()) {
			return result;
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append("FHIR validation failed (").append(resource.fhirType()).append(") for profile ").append(profileUrl).append(":");
		result.getMessages().forEach(msg -> sb.append("\n - ").append(msg.getSeverity()).append(": ").append(msg.getMessage()));
		throw new IllegalArgumentException(sb.toString());
	}
	
	private void loadStructureDefinitionById(String id) {
		String jsonUrl = ShrIgProfileUrls.igCanonical() + "/StructureDefinition-" + id + ".json";
		StructureDefinition sd = fetchAndParse(jsonUrl, StructureDefinition.class);
		if (sd != null) {
			prePopulated.addStructureDefinition(sd);
		}
	}
	
	private void loadValueSetById(String id) {
		String jsonUrl = ShrIgProfileUrls.igCanonical() + "/ValueSet-" + id + ".json";
		ValueSet vs = fetchAndParse(jsonUrl, ValueSet.class);
		if (vs != null) {
			prePopulated.addValueSet(vs);
		}
	}
	
	private void loadCodeSystemById(String id) {
		String jsonUrl = ShrIgProfileUrls.igCanonical() + "/CodeSystem-" + id + ".json";
		CodeSystem cs = fetchAndParse(jsonUrl, CodeSystem.class);
		if (cs != null) {
			prePopulated.addCodeSystem(cs);
		}
	}
	
	@SuppressWarnings("unchecked")
	private <T extends IBaseResource> T fetchAndParse(String url, Class<T> type) {
		if (!LOADED_URLS.add(url)) {
			return null;
		}
		
		try {
			String json = httpGet(url);
			if (json == null || json.trim().isEmpty()) {
				return null;
			}
			IBaseResource parsed = fhirContext.newJsonParser().parseResource(json);
			if (type.isInstance(parsed)) {
				return (T) parsed;
			}
			// Some servers may return OperationOutcome or HTML; ignore silently.
			return null;
		}
		catch (Exception ignored) {
			return null;
		}
	}
	
	private static String extractIdFromCanonical(String canonicalUrl) {
		// canonicalUrl = {igCanonical}/StructureDefinition/{id}
		if (canonicalUrl == null) {
			return null;
		}
		int idx = canonicalUrl.lastIndexOf('/');
		if (idx < 0 || idx == canonicalUrl.length() - 1) {
			return null;
		}
		return canonicalUrl.substring(idx + 1);
	}
	
	private static String httpGet(String url) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		conn.setRequestMethod("GET");
		conn.setConnectTimeout(10_000);
		conn.setReadTimeout(20_000);
		conn.setRequestProperty("Accept", "application/fhir+json, application/json");
		
		int code = conn.getResponseCode();
		InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
		if (in == null) {
			return null;
		}
		try {
			return readAll(in);
		}
		finally {
			try {
				in.close();
			}
			catch (Exception ignored) {}
			conn.disconnect();
		}
	}
	
	private static String readAll(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int r;
		while ((r = in.read(buf)) != -1) {
			out.write(buf, 0, r);
		}
		return new String(out.toByteArray(), StandardCharsets.UTF_8);
	}
}
