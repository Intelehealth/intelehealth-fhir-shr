package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.openmrs.module.ihshr.utils.ImageObsConstants;

public class ImageObsTransferTest {
	
	@Test
	public void buildTransactionBundle_fromImageTestData_shouldCreateBinaryAndDocumentReference() throws Exception {
		String json = new String(Files.readAllBytes(Paths.get("src/main/resources/testdata/image.json")),
		        StandardCharsets.UTF_8);
		JSONObject root = new JSONObject(json);
		JSONArray arr = root
		        .getJSONArray("SELECT * from obs where concept_id =163371 or concept_id =163372\norder by obs_id desc limit 5");
		JSONObject row = arr.getJSONObject(0);
		
		String obsUuid = row.getString("uuid");
		String valueComplex = row.getString("value_complex");
		String comments = row.optString("comments");
		
		String fileName = valueComplex.substring(valueComplex.indexOf('|') + 1).trim();
		Path tempDir = Files.createTempDirectory("ihshr-image-test");
		Files.write(tempDir.resolve(fileName), "fake-image-bytes".getBytes(StandardCharsets.UTF_8));
		
		System.setProperty(ImageObsConstants.COMPLEX_OBS_DIR_PROPERTY, tempDir.toString());
		try {
			Observation source = sampleSourceObservation();
			Bundle bundle = new ImageObsTransfer().buildTransactionBundle(source, obsUuid, valueComplex, comments);
			
			assertNotNull(bundle);
			assertEquals(Bundle.BundleType.TRANSACTION, bundle.getType());
			assertEquals(2, bundle.getEntry().size());
			assertEquals("Binary", bundle.getEntry().get(0).getResource().fhirType());
			assertEquals("PUT", bundle.getEntry().get(0).getRequest().getMethod().toCode());
			assertEquals("Binary/" + obsUuid, bundle.getEntry().get(0).getRequest().getUrl());
			assertEquals("DocumentReference", bundle.getEntry().get(1).getResource().fhirType());
			assertEquals("PUT", bundle.getEntry().get(1).getRequest().getMethod().toCode());
			
			DocumentReference doc = (DocumentReference) bundle.getEntry().get(1).getResource();
			assertEquals(obsUuid, doc.getIdentifierFirstRep().getValue());
			assertTrue(doc.getContentFirstRep().getAttachment().getUrl().startsWith("urn:uuid:img-"));
			assertEquals("image/jpeg", doc.getContentFirstRep().getAttachment().getContentType());
		}
		finally {
			System.clearProperty(ImageObsConstants.COMPLEX_OBS_DIR_PROPERTY);
		}
	}
	
	@Test
	public void appendToVisitBuilder_shouldMergeBinaryDocumentReferenceAndProvenance() throws Exception {
		String obsUuid = "img-obs-visit-test";
		Path tempDir = Files.createTempDirectory("ihshr-image-visit-test");
		Files.write(tempDir.resolve("photo.jpg"), "bytes".getBytes(StandardCharsets.UTF_8));
		System.setProperty(ImageObsConstants.COMPLEX_OBS_DIR_PROPERTY, tempDir.toString());
		try {
			Observation source = sampleSourceObservation();
			VisitTransactionBundleBuilder builder = new VisitTransactionBundleBuilder("visit-img-1",
			        (bundle, addedCruids, subject, logPrefix) -> true);
			boolean added = new ImageObsTransfer().appendToVisitBuilder(builder, source, obsUuid, "complex|photo.jpg",
			    "wound photo");
			assertTrue(added);
			Bundle bundle = builder.build();
			boolean hasBinary = false;
			boolean hasDocRef = false;
			boolean hasProvenance = false;
			for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
				if (entry.getResource() instanceof Binary) {
					hasBinary = true;
					assertEquals("PUT", entry.getRequest().getMethod().toCode());
					assertEquals("Binary/" + obsUuid, entry.getRequest().getUrl());
				}
				if (entry.getResource() instanceof DocumentReference) {
					hasDocRef = true;
					assertEquals("PUT", entry.getRequest().getMethod().toCode());
					assertEquals("DocumentReference/" + obsUuid, entry.getRequest().getUrl());
					assertEquals(obsUuid, ((DocumentReference) entry.getResource()).getIdElement().getIdPart());
				}
				if (entry.getResource() instanceof Provenance) {
					hasProvenance = true;
				}
			}
			assertTrue(hasBinary);
			assertTrue(hasDocRef);
			assertTrue(hasProvenance);
		}
		finally {
			System.clearProperty(ImageObsConstants.COMPLEX_OBS_DIR_PROPERTY);
		}
	}
	
	@Test
	public void appendToVisitBuilder_withRealCruidRewrite_shouldMergeDocumentReferenceAfterBinary() throws Exception {
		String obsUuid = "img-obs-cruid-test";
		Path tempDir = Files.createTempDirectory("ihshr-image-cruid-test");
		Files.write(tempDir.resolve("photo.jpg"), "bytes".getBytes(StandardCharsets.UTF_8));
		System.setProperty(ImageObsConstants.COMPLEX_OBS_DIR_PROPERTY, tempDir.toString());
		try {
			Observation source = sampleSourceObservation();
			VisitTransactionBundleBuilder builder = new VisitTransactionBundleBuilder("visit-img-cruid",
			        (bundle, addedCruids, subject, logPrefix) -> {
				        if (ShrCruidPatientSupport.isInBundlePatientReference(subject.getReference())) {
					        return true;
				        }
				        String patientFullUrl = ShrCruidPatientSupport.fullUrlForCruid("test-cruid");
				        ShrCruidPatientSupport.rewriteSubjectReference(subject, patientFullUrl);
				        return true;
			        });
			assertTrue(new ImageObsTransfer().appendToVisitBuilder(builder, source, obsUuid, "complex|photo.jpg",
			    "wound photo"));
			Bundle bundle = builder.build();
			assertTrue(bundle.getEntry().stream().anyMatch(e -> e.getResource() instanceof DocumentReference));
		}
		finally {
			System.clearProperty(ImageObsConstants.COMPLEX_OBS_DIR_PROPERTY);
		}
	}
	
	private static Observation sampleSourceObservation() {
		Observation source = new Observation();
		source.setId("source-image-obs");
		source.getSubject().setReference("Patient/test-patient-uuid");
		source.getEncounter().setReference("Encounter/test-encounter-uuid");
		source.setEffective(new org.hl7.fhir.r4.model.DateTimeType(new Date()));
		source.addPerformer(new Reference("Practitioner/test-practitioner-uuid"));
		return source;
	}
}
