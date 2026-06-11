package org.openmrs.module.ihshr.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;
import org.openmrs.module.ihshr.domain.ParsedFamilyHistoryRelative;

public class FamilyHistoryParserTest {
	
	/**
	 * Section 7.3 example (doc): leading bullet, Hindi relative आजोबा, period-separated conditions.
	 */
	public static final String DOC_SECTION_73_EXAMPLE = "{\"en\":\"•Do you have a family history of any of the following? : "
	        + " High BP, आजोबा ,father. Diabetes, आजोबा ..<br/><br/>\",\"l-en\":\"…\"}";
	
	@Test
	public void parse_docSection73Example_multilingualRelatives() {
		List<ParsedFamilyHistoryRelative> relatives = new FamilyHistoryParser().parse(DOC_SECTION_73_EXAMPLE);
		assertEquals(2, relatives.size());
		ParsedFamilyHistoryRelative grandfather = findByRole(relatives, "GRFTH");
		ParsedFamilyHistoryRelative father = findByRole(relatives, "FTH");
		assertEquals("आजोबा", grandfather.getRelativeLabel());
		assertEquals(2, grandfather.getConditions().size());
		assertTrue(grandfather.getConditions().contains("High BP"));
		assertTrue(grandfather.getConditions().contains("Diabetes"));
		assertEquals("father", father.getRelativeLabel());
		assertEquals(1, father.getConditions().size());
		assertEquals("High BP", father.getConditions().get(0));
	}
	
	@Test
	public void matchesValueText_docSection73Example() {
		assertTrue(ClinicalJsonValueTexts.hasEnClinicalJson(DOC_SECTION_73_EXAMPLE));
		assertTrue(org.openmrs.module.ihshr.utils.FamilyHistoryMatcher.matchesValueText(DOC_SECTION_73_EXAMPLE));
	}
	
	private static ParsedFamilyHistoryRelative findByRole(List<ParsedFamilyHistoryRelative> relatives, String role) {
		for (ParsedFamilyHistoryRelative r : relatives) {
			if (role.equals(r.getRoleCode())) {
				return r;
			}
		}
		return null;
	}
	
	@Test
	public void parse_motherHighBp_invertsToOneRelative() throws Exception {
		String raw = readTestDataLine("testdata/Sample_Family_History.json", 4);
		List<ParsedFamilyHistoryRelative> relatives = new FamilyHistoryParser().parse(raw);
		assertEquals(1, relatives.size());
		assertEquals("MTH", relatives.get(0).getRoleCode());
		assertEquals(1, relatives.get(0).getConditions().size());
		assertEquals("High BP", relatives.get(0).getConditions().get(0));
	}
	
	@Test
	public void parse_none_producesNoRelatives() throws Exception {
		String raw = readTestDataLine("testdata/Sample_Family_History.json", 2);
		assertTrue(new FamilyHistoryParser().parse(raw).isEmpty());
	}
	
	@Test
	public void parse_jaundiceMotherBrother_mergesConditionsForMother() throws Exception {
		String raw = readTestDataLine("testdata/Sample_Family_History.json", 15);
		List<ParsedFamilyHistoryRelative> relatives = new FamilyHistoryParser().parse(raw);
		assertEquals(2, relatives.size());
	}
	
	@Test
	public void hasEnClinicalJson_required() throws Exception {
		assertTrue(ClinicalJsonValueTexts.hasEnClinicalJson(readTestDataLine("testdata/Sample_Family_History.json", 1)));
	}
	
	private static String readTestDataLine(String resource, int lineNumber) throws Exception {
		InputStream in = FamilyHistoryParserTest.class.getClassLoader().getResourceAsStream(resource);
		if (in == null) {
			throw new IllegalStateException("Missing test resource: " + resource);
		}
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			return reader.lines().skip(lineNumber - 1).findFirst().orElseThrow(IllegalStateException::new);
		}
	}
}
