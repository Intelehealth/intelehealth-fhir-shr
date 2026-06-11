package org.openmrs.module.ihshr.fhir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Date;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.openmrs.module.ihshr.utils.ImageObsConstants;

/**
 * Converts OpenMRS obs_complex image rows (concept 163371/163372) into: - Binary (bytes) -
 * DocumentReference (metadata + link to Binary)
 */
public class ImageObsTransfer {
	
	public Bundle buildTransactionBundle(Observation sourceObs, String obsUuid, String valueComplex, String comments)
	        throws Exception {
		String fileName = extractFileName(valueComplex);
		if (StringUtils.isBlank(fileName)) {
			return null;
		}
		
		Path complexDir = resolveComplexObsDir();
		Path filePath = complexDir.resolve(fileName);
		if (!Files.exists(filePath)) {
			return null;
		}
		byte[] bytes = Files.readAllBytes(filePath);
		String contentType = detectContentType(fileName);
		Date creation = resolveCreationDate(sourceObs);
		
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.TRANSACTION);
		
		String binaryUrn = "urn:uuid:img-" + obsUuid;
		Binary binary = new Binary();
		binary.setId(obsUuid);
		binary.setContentType(contentType);
		binary.setData(bytes);
		ShrPushMetaApplicator.applyPushMeta(binary);
		
		Bundle.BundleEntryComponent binaryEntry = bundle.addEntry();
		binaryEntry.setFullUrl(binaryUrn);
		binaryEntry.setResource(binary);
		binaryEntry.getRequest().setMethod(Bundle.HTTPVerb.PUT).setUrl("Binary/" + obsUuid);
		
		DocumentReference docRef = new DocumentReference();
		docRef.setId(obsUuid);
		docRef.setStatus(Enumerations.DocumentReferenceStatus.CURRENT);
		
		Identifier identifier = new Identifier();
		identifier.setSystem(ImageObsConstants.IDENTIFIER_SYSTEM);
		identifier.setValue(obsUuid);
		docRef.addIdentifier(identifier);
		
		// Kept as in implementation doc sample.
		docRef.setType(new CodeableConcept().addCoding(new Coding().setSystem("http://snomed.info/sct")
		        .setCode("annotation").setDisplay("Clinical photograph")));
		
		if (sourceObs != null) {
			if (sourceObs.hasSubject()) {
				docRef.setSubject(sourceObs.getSubject().copy());
			}
			if (sourceObs.hasEncounter()) {
				docRef.getContext().addEncounter(sourceObs.getEncounter().copy());
			}
		}
		
		if (creation != null) {
			docRef.getContext().setPeriod(new org.hl7.fhir.r4.model.Period().setStart(creation));
		}
		
		Attachment att = new Attachment();
		att.setContentType(contentType);
		att.setUrl(binaryUrn);
		att.setSize(bytes.length);
		att.setHash(sha1(bytes));
		att.setTitle(StringUtils.defaultIfBlank(StringUtils.trimToNull(comments), fileName));
		if (creation != null) {
			att.setCreation(creation);
		}
		docRef.addContent().setAttachment(att);
		ShrPushMetaApplicator.applyPushMeta(docRef);
		
		Bundle.BundleEntryComponent docEntry = bundle.addEntry();
		docEntry.setResource(docRef);
		docEntry.getRequest().setMethod(Bundle.HTTPVerb.PUT).setUrl("DocumentReference/" + obsUuid);
		
		return bundle;
	}
	
	/**
	 * Adds image Binary + DocumentReference entries into a visit-wide transaction bundle.
	 */
	public boolean appendToVisitBuilder(VisitTransactionBundleBuilder builder, Observation sourceObs, String obsUuid,
	        String valueComplex, String comments) throws Exception {
		Bundle imageBundle = buildTransactionBundle(sourceObs, obsUuid, valueComplex, comments);
		if (imageBundle == null || !imageBundle.hasEntry()) {
			return false;
		}
		Reference subject = null;
		for (Bundle.BundleEntryComponent entry : imageBundle.getEntry()) {
			if (entry.getResource() instanceof DocumentReference) {
				subject = ((DocumentReference) entry.getResource()).getSubject();
				break;
			}
		}
		boolean mergedAny = false;
		for (Bundle.BundleEntryComponent entry : imageBundle.getEntry()) {
			if (!builder.mergeEntry(entry, subject, "[ImageObs]")) {
				continue;
			}
			mergedAny = true;
			if (entry.getResource() instanceof DocumentReference) {
				builder.registerProvenanceTarget(
				    org.openmrs.module.ihshr.fhir.provenance.ProvenanceAssertionClass.DOCUMENTS_IMAGES, entry.getResource(),
				    obsUuid);
			}
		}
		return mergedAny;
	}
	
	private static byte[] sha1(byte[] bytes) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		return md.digest(bytes);
	}
	
	private static Date resolveCreationDate(Observation sourceObs) {
		if (sourceObs == null) {
			return null;
		}
		if (sourceObs.hasEffectiveDateTimeType()) {
			return sourceObs.getEffectiveDateTimeType().getValue();
		}
		if (sourceObs.hasIssued()) {
			return sourceObs.getIssued();
		}
		return null;
	}
	
	private static Path resolveComplexObsDir() {
		String configured = System.getProperty(ImageObsConstants.COMPLEX_OBS_DIR_PROPERTY);
		if (StringUtils.isNotBlank(configured)) {
			return Paths.get(configured.trim());
		}
		return Paths.get(ImageObsConstants.DEFAULT_COMPLEX_OBS_DIR);
	}
	
	private static String extractFileName(String valueComplex) {
		String v = StringUtils.trimToEmpty(valueComplex);
		if (v.isEmpty()) {
			return null;
		}
		if (v.contains("|")) {
			return StringUtils.trimToNull(v.substring(v.indexOf('|') + 1));
		}
		return StringUtils.trimToNull(v);
	}
	
	private static String detectContentType(String fileName) {
		String lower = StringUtils.defaultString(fileName).toLowerCase(Locale.ROOT);
		if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
			return "image/jpeg";
		}
		if (lower.endsWith(".png")) {
			return "image/png";
		}
		if (lower.endsWith(".gif")) {
			return "image/gif";
		}
		if (lower.endsWith(".webp")) {
			return "image/webp";
		}
		if (lower.endsWith(".pdf")) {
			return "application/pdf";
		}
		return "application/octet-stream";
	}
}
