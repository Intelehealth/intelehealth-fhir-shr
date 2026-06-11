package org.openmrs.module.ihshr.parser;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.ihshr.config.ShrLookupLoader;
import org.openmrs.module.ihshr.domain.ParsedExamCategory;
import org.openmrs.module.ihshr.domain.ParsedFinding;
import org.openmrs.module.ihshr.fhir.PhysicalExamObservationBuilder;

import org.json.JSONObject;

import ca.uhn.fhir.context.FhirContext;

/**
 * Writes full physical exam parse + FHIR output to {@code target/physical_exam_final_output.json}.
 */
public class PhysicalExamFinalOutputWriterTest {
	
	public static final String USER_JSON = "{\"en\":\"<br/>►<b>General exams: </b><br/>"
	        + "• Eyes: Jaundice-no jaundice seen, [picture taken]. <br/>"
	        + "• Eyes: Pallor-normal pallor. <br/>"
	        + "• Arm-Pinch skin* - pinch test normal. <br/>"
	        + "• Nail abnormality-nails normal. <br/>"
	        + "• Nail anemia-Nails are normal. <br/>"
	        + "• Ankle-no pedal oedema. -<br/>"
	        + "►<b>Mouth: </b><br/>"
	        + "•  back of throat normal. -<br/>"
	        + "►<b>Any Location: </b><br/>"
	        + "• Skin Rash:-no rash. <br/>"
	        + "• Ulcer:-no ulcer. -<br/>"
	        + "►<b>Abdomen: </b><br/>"
	        + "•  no tenderness.\","
	        + "\"l-en\":\"►<b>General exams: </b><br/>"
	        + "• Eyes: Jaundice-● Is there jaundice?*<br/>"
	        + "•No,[picture taken]-<br/>"
	        + "• Eyes: Pallor-● Is there pallor?*<br/>"
	        + "•Normal-<br/>"
	        + "• Arm-● Pinch skin*<br/>"
	        + "•Normal-<br/>"
	        + "• Nail abnormality-● Is there any nail abnormality?*<br/>"
	        + "•Nails are normal-<br/>"
	        + "• Nail anemia-● Are the nails pale?*<br/>"
	        + "•Nails are normal-<br/>"
	        + "• Ankle-● Is there ankle oedema?<br/>"
	        + "•No oedema--►<b>Mouth: </b><br/>"
	        + "• Back of throat (redness, swelling)-● Ask patient to open mouth and stick tongue out.  Shine a torch to examine the back of throat and look for swelling/redness *<br/>"
	        + "•Normal--►<b>Any Location: </b><br/>" + "• Skin Rash-● Is there any rash? *<br/>" + "•No-<br/>"
	        + "• Ulcer-● Is there an ulcer? *<br/>" + "•No--►<b>Abdomen: </b><br/>"
	        + "• Tenderness-● Is there abdominal tenderness?*<br/>" + "•No tenderness--\"}";
	
	private static final String OBS_UUID = "920352ce-4e64-4b55-9dfe-883332b546c0";
	
	private final FhirContext fhirContext = FhirContext.forR4();
	
	@Before
	public void loadLookups() {
		System.setProperty("ihshr.shr.lookup.dir", "src/main/resources/shr-config");
		ShrLookupLoader.clearCache();
	}
	
	@Test
	public void writeFinalOutputFile() throws Exception {
		File out = new File("target/physical_exam_final_output.json");
		out.getParentFile().mkdirs();
		
		List<ParsedExamCategory> categories = new PhysicalExamParser().parse(USER_JSON);
		String sharedNote = PhysicalExamObservationBuilder.stripHtmlForNote(USER_JSON);
		Observation source = sampleSourceObservation();
		PhysicalExamObservationBuilder builder = new PhysicalExamObservationBuilder();
		
		StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		sb.append("  \"source\": \"physical_exam_final_output\",\n");
		sb.append("  \"obsUuid\": \"").append(OBS_UUID).append("\",\n");
		sb.append("  \"parsedFrom\": \"en\",\n");
		sb.append("  \"lEnIgnored\": true,\n");
		sb.append("  \"input\": ").append(new JSONObject(USER_JSON).toString(2)).append(",\n");
		sb.append("  \"sharedNote\": ").append(JSONObject.quote(sharedNote)).append(",\n");
		sb.append("  \"parsedCategories\": [\n");
		for (int c = 0; c < categories.size(); c++) {
			ParsedExamCategory category = categories.get(c);
			sb.append("    {\n");
			sb.append("      \"categoryName\": ").append(jsonString(category.getCategoryName())).append(",\n");
			sb.append("      \"findings\": [\n");
			List<ParsedFinding> findings = category.getFindings();
			for (int f = 0; f < findings.size(); f++) {
				ParsedFinding finding = findings.get(f);
				sb.append("        { \"item\": ").append(jsonString(finding.getItem()));
				sb.append(", \"finding\": ").append(jsonString(finding.getFinding())).append(" }");
				sb.append(f + 1 < findings.size() ? ",\n" : "\n");
			}
			sb.append("      ]\n");
			sb.append("    }");
			sb.append(c + 1 < categories.size() ? ",\n" : "\n");
		}
		sb.append("  ],\n");
		sb.append("  \"fhirTransactionBundles\": [\n");
		for (int c = 0; c < categories.size(); c++) {
			ParsedExamCategory category = categories.get(c);
			Observation obs = builder.build(source, OBS_UUID, category, sharedNote);
			Bundle bundle = new Bundle();
			bundle.setType(Bundle.BundleType.TRANSACTION);
			Bundle.BundleEntryComponent entry = bundle.addEntry();
			entry.setResource(obs);
			entry.getRequest().setUrl("Observation/" + obs.getIdentifierFirstRep().getValue())
			        .setMethod(Bundle.HTTPVerb.PUT);
			String bundleJson = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
			sb.append("    ");
			sb.append(bundleJson.replace("\n", "\n    ").trim());
			sb.append(c + 1 < categories.size() ? ",\n" : "\n");
		}
		sb.append("  ]\n");
		sb.append("}\n");
		
		try (Writer w = new FileWriter(out)) {
			w.write(sb.toString());
		}
		System.out.println("Wrote: " + out.getAbsolutePath());
	}
	
	private static String jsonString(String value) {
		return JSONObject.quote(value == null ? "" : value);
	}
	
	private static Observation sampleSourceObservation() {
		Observation source = new Observation();
		source.getSubject().setReference("Patient/test-patient-uuid");
		source.getEncounter().setReference("Encounter/test-encounter-uuid");
		source.setEffective(new org.hl7.fhir.r4.model.DateTimeType(new Date()));
		source.getCode().setText("Physical examination");
		return source;
	}
}
