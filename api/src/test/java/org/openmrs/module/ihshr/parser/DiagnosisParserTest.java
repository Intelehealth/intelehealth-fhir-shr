package org.openmrs.module.ihshr.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.openmrs.module.ihshr.domain.ParsedDiagnosis;
import org.openmrs.module.ihshr.fhir.DiagnosisConditionBuilder;

public class DiagnosisParserTest {
	
	@Test
	public void parse_structuredPrimaryConfirmed() {
		ParsedDiagnosis parsed = new DiagnosisParser().parse("82272006::Acute rhinitis:Primary & Confirmed");
		assertNotNull(parsed);
		assertEquals("82272006", parsed.getCode());
		assertEquals("Acute rhinitis", parsed.getDiagnosisText());
		assertEquals("Primary", parsed.getDiagnosisType());
		assertEquals("confirmed", parsed.getDiagnosisCategory());
		assertEquals("http://snomed.info/sct", DiagnosisConditionBuilder.detectCodeSystem(parsed.getCode()));
	}
	
	@Test
	public void parse_structuredSecondaryUnderEvaluation() {
		ParsedDiagnosis parsed = new DiagnosisParser().parse("J30.0::Acute rhinitis:Secondary & Under evaluation");
		assertNotNull(parsed);
		assertEquals("Secondary", parsed.getDiagnosisType());
		assertEquals("unconfirmed", parsed.getDiagnosisCategory());
		assertEquals("http://hl7.org/fhir/sid/icd-10", DiagnosisConditionBuilder.detectCodeSystem(parsed.getCode()));
	}
	
	@Test
	public void parse_structuredFallbackCodeSystem() {
		ParsedDiagnosis parsed = new DiagnosisParser()
		        .parse("NA::Rheumatic fever without heart involvement:Primary & Provisional");
		assertNotNull(parsed);
		assertEquals("NA", parsed.getCode());
		assertEquals("urn:intelehealth:concept", DiagnosisConditionBuilder.detectCodeSystem(parsed.getCode()));
		assertEquals("provisional", parsed.getDiagnosisCategory());
	}
	
	@Test
	public void parse_jsonDiagnosisWithType() {
		ParsedDiagnosis parsed = new DiagnosisParser().parse("{\"diagnosis\":\"pain\",\"type\":\"Provisional\"}");
		assertNotNull(parsed);
		assertNull(parsed.getCode());
		assertEquals("pain", parsed.getDiagnosisText());
		assertEquals("provisional", parsed.getDiagnosisCategory());
	}
	
	@Test
	public void parse_jsonDiagnosisOnly() {
		ParsedDiagnosis parsed = new DiagnosisParser().parse("{\"diagnosis\":\"Viral fever.\"}");
		assertNotNull(parsed);
		assertEquals("Viral fever", parsed.getDiagnosisText());
	}
	
	@Test
	public void parse_sampleDiagnosisJson_shouldHandleAllDiagnosisRows() throws Exception {
		String json = new String(Files.readAllBytes(Paths.get("src/main/resources/testdata/diagnosis.json")),
		        StandardCharsets.UTF_8);
		JSONObject root = new JSONObject(json);
		JSONArray arr = root.getJSONArray("SELECT * from obs where concept_id =163219\n");
		DiagnosisParser parser = new DiagnosisParser();
		int diagnosisRows = 0;
		int parsedRows = 0;
		int blankDiagnosisRows = 0;
		
		for (int i = 0; i < arr.length(); i++) {
			JSONObject row = arr.getJSONObject(i);
			if (row.optInt("concept_id") != 163219) {
				continue;
			}
			diagnosisRows++;
			String valueText = row.optString("value_text", "");
			ParsedDiagnosis parsed = parser.parse(valueText);
			if (parsed != null && parsed.getDiagnosisText() != null) {
				parsedRows++;
			} else {
				JSONObject raw = valueText.startsWith("{") ? new JSONObject(valueText) : null;
				if (raw != null && raw.has("diagnosis") && raw.optString("diagnosis").trim().isEmpty()) {
					blankDiagnosisRows++;
				}
			}
		}
		
		assertTrue("Expected diagnosis rows in sample file", diagnosisRows > 0);
		assertEquals("Only blank diagnosis rows may be skipped", diagnosisRows - blankDiagnosisRows, parsedRows);
	}
}
