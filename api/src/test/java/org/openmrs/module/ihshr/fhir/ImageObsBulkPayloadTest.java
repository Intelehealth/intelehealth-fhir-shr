package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.openmrs.module.ihshr.utils.ImageObsConstants;

import ca.uhn.fhir.context.FhirContext;

/**
 * Build bundles for all image test rows whose files exist in complex_obs directory. Outputs: -
 * target/test-output/image-bulk-bundles.ndjson - target/test-output/image-bulk-summary.txt
 */
public class ImageObsBulkPayloadTest {
	
	private final FhirContext fhirContext = FhirContext.forR4();
	
	@Test
	public void printBulkImageBundlesFromRealDirectory() throws Exception {
		String json = new String(Files.readAllBytes(Paths.get("src/main/resources/testdata/image.json")),
		        StandardCharsets.UTF_8);
		JSONObject root = new JSONObject(json);
		JSONArray arr = root
		        .getJSONArray("SELECT * from obs where concept_id =163371 or concept_id =163372\norder by obs_id desc limit 5");
		
		Path realDir = Paths.get("/home/proshanto/.OpenMRS/complex_obs");
		System.setProperty(ImageObsConstants.COMPLEX_OBS_DIR_PROPERTY, realDir.toString());
		try {
			List<String> ndjsonLines = new ArrayList<String>();
			List<String> summary = new ArrayList<String>();
			int found = 0;
			int skipped = 0;
			
			for (int i = 0; i < arr.length(); i++) {
				JSONObject row = arr.getJSONObject(i);
				String obsUuid = row.optString("uuid");
				String valueComplex = row.optString("value_complex");
				String comments = row.optString("comments");
				String fileName = extractFileName(valueComplex);
				Path filePath = realDir.resolve(fileName == null ? "" : fileName);
				
				if (fileName == null || !Files.exists(filePath)) {
					skipped++;
					summary.add("SKIP obs=" + obsUuid + " file=" + fileName);
					continue;
				}
				
				Bundle bundle = new ImageObsTransfer().buildTransactionBundle(sampleSourceObservation(), obsUuid,
				    valueComplex, comments);
				if (bundle == null) {
					skipped++;
					summary.add("SKIP-BUNDLE obs=" + obsUuid + " file=" + fileName);
					continue;
				}
				
				found++;
				String compact = fhirContext.newJsonParser().encodeResourceToString(bundle);
				ndjsonLines.add(compact);
				
				int size = bundle.getEntry().get(0).getResource().fhirType().equals("Binary") ? ((org.hl7.fhir.r4.model.Binary) bundle
				        .getEntry().get(0).getResource()).getData().length
				        : -1;
				summary.add("OK obs=" + obsUuid + " file=" + fileName + " bytes=" + size + " entries="
				        + bundle.getEntry().size());
			}
			
			Path outDir = Paths.get("target/test-output");
			Files.createDirectories(outDir);
			Path ndjson = outDir.resolve("image-bulk-bundles.ndjson");
			Path summaryFile = outDir.resolve("image-bulk-summary.txt");
			Files.write(ndjson, String.join("\n", ndjsonLines).getBytes(StandardCharsets.UTF_8));
			Files.write(summaryFile, String.join("\n", summary).getBytes(StandardCharsets.UTF_8));
			
			System.err.println("Wrote NDJSON bundles: " + ndjson.toAbsolutePath());
			System.err.println("Wrote summary: " + summaryFile.toAbsolutePath());
			System.err.println("Found bundles=" + found + " skipped=" + skipped);
			
			assertTrue("Expected at least one real image bundle", found > 0);
		}
		finally {
			System.clearProperty(ImageObsConstants.COMPLEX_OBS_DIR_PROPERTY);
		}
	}
	
	private static String extractFileName(String valueComplex) {
		if (valueComplex == null) {
			return null;
		}
		String v = valueComplex.trim();
		if (v.isEmpty()) {
			return null;
		}
		if (v.contains("|")) {
			return v.substring(v.indexOf('|') + 1).trim();
		}
		return v;
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
