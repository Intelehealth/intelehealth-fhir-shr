package org.openmrs.module.ihshr.config;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.ihmodule.api.patientexchange.config.FhirContextHolder;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.openmrs.module.ihshr.domain.FhirResponse;
import org.openmrs.module.ihshr.utils.IHConstant;
import org.openmrs.module.ihshr.utils.IhshrPropertyResolver;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.parser.LenientErrorHandler;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BasicAuthInterceptor;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;

@Component("ihshrFhirConfig")
public class FhirConfig extends IHConstant {
	
	public IGenericClient getOpenCRFhirContext() {
		String resolvedOpencrUrl = getOpencrOpenhimURL();
		IGenericClient openCr = FhirContextHolder.R4.newRestfulGenericClient(resolvedOpencrUrl);
		String[] credentials = splitCredentials(getOpencrOpenhimAuthentication(),
		    "opencr.openhim.clientid.password.basic.auth");
		openCr.registerInterceptor(new BasicAuthInterceptor(credentials[0], credentials[1]));
		return openCr;
	}
	
	public IGenericClient getLocalOpenMRSFhirContext() {
		FhirContextHolder.R4.setParserErrorHandler(new LenientErrorHandler().setErrorOnInvalidValue(false));
		String base = getLocalOpenmrsURL();
		IGenericClient openMRSServer = FhirContextHolder.R4.newRestfulGenericClient(base + "/ws/fhir2/R4");
		String[] credentials = getOpenMRSCredentials();
		openMRSServer.registerInterceptor(new BasicAuthInterceptor(credentials[0], credentials[1]));
		return openMRSServer;
	}
	
	public IGenericClient getGOFRFhirContext() {
		String resolvedGofrUrl = getGofrOpenhimURL();
		IGenericClient client = FhirContextHolder.R4.newRestfulGenericClient(resolvedGofrUrl);
		String[] credentials = splitCredentials(getGofrOpenhimAuthentication(), "gofr.openhim.clientid.password.basic.auth");
		client.registerInterceptor(new BasicAuthInterceptor(credentials[0], credentials[1]));
		return client;
	}
	
	public String[] getOpenMRSCredentials() {
		return splitCredentials(getLocalOpenmrsAuthentication(), "local.openmrs.clientid.password.basic.auth");
	}
	
	public String[] getShrCredentials() {
		return splitCredentials(getShrCredential(), "opencr.shr.credential");
	}
	
	public IGenericClient getShrFhirContext() {
		IGenericClient shrClient = FhirContextHolder.R4.newRestfulGenericClient(getShrUrl());
		String[] credentials = getShrCredentials();
		shrClient.registerInterceptor(new BasicAuthInterceptor(credentials[0], credentials[1]));
		return shrClient;
	}
	
	public FhirResponse postTransactionBundle(Bundle transactionBundle) {
		FhirResponse response = new FhirResponse();
		try {
			Bundle result = getShrFhirContext().transaction().withBundle(transactionBundle).execute();
			response.setStatusCode("200");
			response.setResponse(newJsonParser().setPrettyPrint(true).encodeResourceToString(result));
			return response;
		}
		catch (BaseServerResponseException e) {
			response.setStatusCode(String.valueOf(e.getStatusCode()));
			response.setResponse(e.getResponseBody());
			response.setMessage(e.getMessage());
			return response;
		}
		catch (Exception e) {
			response.setStatusCode("500");
			response.setMessage(e.getMessage());
			return response;
		}
	}
	
	private String[] splitCredentials(String rawCredential, String propertyName) {
		String[] parts = StringUtils.defaultString(rawCredential).split(":", 2);
		if (parts.length != 2 || StringUtils.isBlank(parts[0])) {
			throw new IllegalStateException("Invalid basic-auth config for ihshr." + propertyName
			        + ". Expected format: username:password");
		}
		return parts;
	}
	
	public IParser newJsonParser() {
		return FhirContextHolder.R4.newJsonParser();
	}
	
	public Bundle searchBundleByUrl(String urlWithQuery) {
		return getShrPullFhirContext().search().byUrl(urlWithQuery).returnBundle(Bundle.class).execute();
	}
	
	public Binary readBinary(String binaryId) {
		return getShrPullFhirContext().read().resource(Binary.class).withId(binaryId).execute();
	}
	
	public IGenericClient getShrPullFhirContext() {
		String pullUrl = IhshrPropertyResolver.resolve("shr.pull.openhim.url");
		if (StringUtils.isNotBlank(pullUrl)) {
			IGenericClient client = FhirContextHolder.R4.newRestfulGenericClient(pullUrl);
			String[] credentials = splitCredentials(getOpencrOpenhimAuthentication(),
			    "opencr.openhim.clientid.password.basic.auth");
			client.registerInterceptor(new BasicAuthInterceptor(credentials[0], credentials[1]));
			return client;
		}
		return getOpenCRFhirContext();
	}
}
