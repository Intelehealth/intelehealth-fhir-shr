package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.openmrs.module.ihshr.utils.ImageObsConstants;

import ca.uhn.fhir.context.FhirContext;

/**
 * Prints final image transaction bundles: - from testdata/image.json - from real complex obs
 * directory
 */
public class ImageObsPayloadTest {
	
	private final FhirContext fhirContext = FhirContext.forR4();
	
	@Test
	public void printImageBundleFromTestData() throws Exception {
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
		
		Path tempDir = Files.createTempDirectory("ihshr-image-payload-test");
		Files.write(tempDir.resolve(fileName), "fake-image-bytes-from-testdata".getBytes(StandardCharsets.UTF_8));
		
		System.setProperty(ImageObsConstants.COMPLEX_OBS_DIR_PROPERTY, tempDir.toString());
		try {
			Bundle bundle = new ImageObsTransfer().buildTransactionBundle(sampleSourceObservation(), obsUuid, valueComplex,
			    comments);
			assertNotNull(bundle);
			String payload = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
			
			Path out = Paths.get("target/test-output/image-testdata-bundle.json");
			Files.createDirectories(out.getParent());
			Files.write(out, payload.getBytes(StandardCharsets.UTF_8));
			System.err.println("Wrote testdata image bundle: " + out.toAbsolutePath());
		}
		finally {
			System.clearProperty(ImageObsConstants.COMPLEX_OBS_DIR_PROPERTY);
		}
	}
	
	@Test
	public void printImageBundleFromRealComplexObsDirectory() throws Exception {
		String obsUuid = "c6d2102c-a0da-435f-952f-dcd8f5552412";
		String valueComplex = "jpg image |c6d2102c-a0da-435f-952f-dcd8f5552412_c6d2102c-a0da-435f-952f-dcd8f5552412.jpg";
		String comments = "ADDITIONAL_DOC";
		String parsedFileName = valueComplex.substring(valueComplex.indexOf('|') + 1).trim();
		
		Path realDir = Paths.get("/home/proshanto/.OpenMRS/complex_obs");
		Path realFile = realDir.resolve(parsedFileName);
		assertTrue("Real file missing: " + realFile, Files.exists(realFile));
		
		System.setProperty(ImageObsConstants.COMPLEX_OBS_DIR_PROPERTY, realDir.toString());
		try {
			Bundle bundle = new ImageObsTransfer().buildTransactionBundle(sampleSourceObservation(), obsUuid, valueComplex,
			    comments);
			assertNotNull(bundle);
			String payload = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
			
			Path out = Paths.get("target/test-output/image-real-bundle.json");
			Files.createDirectories(out.getParent());
			Files.write(out, payload.getBytes(StandardCharsets.UTF_8));
			System.err.println("Wrote real image bundle: " + out.toAbsolutePath());
			System.err.println("Pipe-parser extracted filename: " + parsedFileName);
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
