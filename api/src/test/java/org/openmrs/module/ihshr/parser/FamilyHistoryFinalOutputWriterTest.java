package org.openmrs.module.ihshr.parser;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.FamilyMemberHistory;
import org.hl7.fhir.r4.model.Observation;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.ihshr.config.ShrLookupLoader;
import org.openmrs.module.ihshr.domain.ParsedFamilyHistoryRelative;
import org.openmrs.module.ihshr.fhir.FamilyHistoryTransfer;
import org.openmrs.module.ihshr.fhir.FamilyMemberHistoryBuilder;

import ca.uhn.fhir.context.FhirContext;

/**
 * Writes family history parse + FHIR output for a sample {@code en} value.
 */
public class FamilyHistoryFinalOutputWriterTest {
	
	public static final String USER_JSON = "{\"en\":\"Do you have a family history of any of the following?* : "
	        + "• Heart Disease, Brother..<br/>\",\"l-en\":\"Do you have a family history of any of the following?* : "
	        + "● Heart Disease•Brother.<br/>\"}";
	
	private static final String OBS_UUID = "test-family-history-obs-uuid";
	
	private final FhirContext fhirContext = FhirContext.forR4();
	
	@Before
	public void loadLookups() {
		System.setProperty("ihshr.shr.lookup.dir", "src/main/resources/shr-config");
		ShrLookupLoader.clearCache();
	}
	
	@Test
	public void writeFinalOutputFile() throws Exception {
		List<ParsedFamilyHistoryRelative> relatives = new FamilyHistoryParser().parse(USER_JSON);
		String sharedNote = FamilyHistoryTransfer.stripHtmlForNote(USER_JSON);
		Observation source = sampleSourceObservation();
		FamilyMemberHistoryBuilder builder = new FamilyMemberHistoryBuilder();
		
		JSONObject root = new JSONObject();
		root.put("source", "family_history_final_output");
		root.put("obsUuid", OBS_UUID);
		root.put("parsedFrom", "en");
		root.put("lEnIgnored", true);
		root.put("input", new JSONObject(USER_JSON));
		root.put("sharedNote", sharedNote);
		
		JSONArray relArr = new JSONArray();
		for (ParsedFamilyHistoryRelative r : relatives) {
			JSONObject o = new JSONObject();
			o.put("relativeLabel", r.getRelativeLabel());
			o.put("roleCode", r.getRoleCode());
			o.put("mapKey", r.getMapKey());
			o.put("conditions", new JSONArray(r.getConditions()));
			relArr.put(o);
		}
		root.put("parsedRelatives", relArr);
		
		JSONArray bundles = new JSONArray();
		Bundle combined = new Bundle();
		combined.setType(Bundle.BundleType.TRANSACTION);
		for (ParsedFamilyHistoryRelative relative : relatives) {
			FamilyMemberHistory fmh = builder.build(source, OBS_UUID, relative, sharedNote);
			Bundle bundle = new Bundle();
			bundle.setType(Bundle.BundleType.TRANSACTION);
			Bundle.BundleEntryComponent entry = bundle.addEntry();
			entry.setResource(fmh);
			entry.getRequest().setUrl("FamilyMemberHistory/" + fmh.getIdentifierFirstRep().getValue())
			        .setMethod(Bundle.HTTPVerb.PUT);
			String bundleJson = fhirContext.newJsonParser().encodeResourceToString(bundle);
			bundles.put(new JSONObject(bundleJson));
			combined.getEntry().addAll(bundle.getEntry());
		}
		root.put("fhirTransactionBundles", bundles);
		root.put("fhirCombinedTransactionBundle", new JSONObject(fhirContext.newJsonParser().setPrettyPrint(true)
		        .encodeResourceToString(combined)));
		
		writeJson(new File("target/family_history_final_output.json"), root);
		writeJson(new File("target/family_history_final_fhir_output.json"),
		    root.getJSONObject("fhirCombinedTransactionBundle"));
		writeJson(new File("src/main/resources/testdata/family_history_heart_disease_brother_output.json"), root);
		writeJson(new File("src/main/resources/testdata/family_history_heart_disease_brother_fhir_output.json"),
		    root.getJSONObject("fhirCombinedTransactionBundle"));
		System.out.println("relatives=" + relatives.size());
	}
	
	private static void writeJson(File file, JSONObject json) throws Exception {
		file.getParentFile().mkdirs();
		try (Writer w = new FileWriter(file)) {
			w.write(json.toString(2));
		}
		System.out.println("Wrote: " + file.getAbsolutePath());
	}
	
	private static Observation sampleSourceObservation() {
		Observation source = new Observation();
		source.getSubject().setReference("Patient/test-patient-uuid");
		source.getEncounter().setReference("Encounter/test-encounter-uuid");
		source.setEffective(new org.hl7.fhir.r4.model.DateTimeType(new Date()));
		source.getCode().setText("Family history");
		return source;
	}
}
