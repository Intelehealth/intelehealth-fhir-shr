package org.openmrs.module.ihshr.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.openmrs.module.ihshr.domain.ParsedExamCategory;
import org.openmrs.module.ihshr.domain.ParsedFinding;
import org.openmrs.module.ihshr.fhir.PhysicalExamObservationBuilder;
import org.openmrs.module.ihshr.utils.PhysicalExamMatcher;

public class PhysicalExamParserTest {
	
	/** Plain HTML (no JSON wrapper) as saved by some clients. */
	public static final String PLAIN_HTML_GENERAL_EXAMS = "<b>General exams: </b><br/>" + "In-person consultation. <br/>"
	        + "Eyes: Jaundice-<br/>" + "Eyes: Pallor-<br/>" + "Arm-Pinch skin*<br/>" + "Nail abnormality-<br/>"
	        + "Nail anemia-<br/>" + "Ankle--";
	
	public static final String SAMPLE_VALUE_JSON = "{\"en\":\"<br/>\\u25ba<b>General exams: </b><br/>"
	        + "\\u2022 Eyes: Jaundice-no jaundice seen. <br/>" + "\\u2022 Eyes: Pallor-normal pallor. <br/>"
	        + "\\u2022 Arm-Pinch skin* - pinch test normal. <br/>" + "\\u2022 Nail abnormality-nails normal. <br/>"
	        + "\\u2022 Nail anemia-Nails are normal. <br/>" + "\\u2022 Ankle-no pedal oedema.\","
	        + "\"l-en\":\"\\u25ba<b>General exams: </b><br/>" + "\\u2022 Eyes: Jaundice-\\u25cf Is there jaundice?*<br/>"
	        + "\\u2022No-<br/>" + "\\u2022 Eyes: Pallor-\\u25cf Is there pallor?*<br/>" + "\\u2022Normal-<br/>"
	        + "\\u2022 Arm-\\u25cf Pinch skin*<br/>" + "\\u2022Normal-<br/>"
	        + "\\u2022 Nail abnormality-\\u25cf Is there any nail abnormality?*<br/>" + "\\u2022Nails are normal-<br/>"
	        + "\\u2022 Nail anemia-\\u25cf Are the nails pale?*<br/>" + "\\u2022Nails are normal-<br/>"
	        + "\\u2022 Ankle-\\u25cf Is there ankle oedema?<br/>" + "\\u2022No oedema--\"}";
	
	@Test
	public void stripHtmlForNote_removesBulletCharacters() {
		String note = PhysicalExamObservationBuilder.stripHtmlForNote(SAMPLE_VALUE_JSON);
		assertFalse(note.contains("\u2022"));
		assertFalse(note.contains("•"));
		assertTrue(note.contains("Eyes: Jaundice-no jaundice seen"));
	}
	
	@Test
	public void stripHtmlForNote_removesPictureTakenMarkers() {
		String note = PhysicalExamObservationBuilder.stripHtmlForNote(DOC_SECTION_72_EXAMPLE);
		assertFalse(note.toLowerCase().contains("[picture taken]"));
		assertTrue(note.contains("no jaundice seen"));
		assertTrue(note.contains("normal pallor"));
		assertTrue(note.contains("nails normal"));
	}
	
	@Test
	public void extractClinicalHtml_usesEnKeyOnly() {
		String html = PhysicalExamValueTexts.extractClinicalHtml(SAMPLE_VALUE_JSON);
		assertTrue(html.contains("General exams"));
		assertTrue(html.contains("no jaundice seen"));
		assertFalse("l-en template must not be parsed", html.contains("Is there jaundice"));
		assertFalse(html.contains("Is there pallor?"));
	}
	
	@Test
	public void hasEnClinicalJson_detectsSample() {
		assertTrue(PhysicalExamValueTexts.hasEnClinicalJson(SAMPLE_VALUE_JSON));
		assertTrue(PhysicalExamMatcher.matchesValueText(SAMPLE_VALUE_JSON));
	}
	
	@Test
	public void matchesValueText_plainHtmlCategoryHeadings() {
		assertFalse(PhysicalExamValueTexts.hasEnClinicalJson(PLAIN_HTML_GENERAL_EXAMS));
		assertTrue(PhysicalExamMatcher.matchesValueText(PLAIN_HTML_GENERAL_EXAMS));
	}
	
	@Test
	public void parse_plainHtmlGeneralExams_brSeparatedLines() {
		List<ParsedExamCategory> categories = new PhysicalExamParser().parse(PLAIN_HTML_GENERAL_EXAMS);
		assertEquals(1, categories.size());
		
		ParsedExamCategory general = categories.get(0);
		assertEquals("General exams", general.getCategoryName());
		assertEquals(7, general.getFindings().size());
		
		assertFinding(general.getFindings().get(0), "In-person consultation", "");
		assertFinding(general.getFindings().get(1), "Eyes: Jaundice", "");
		assertFinding(general.getFindings().get(2), "Eyes: Pallor", "");
		assertFinding(general.getFindings().get(3), "Arm-Pinch skin", "");
		assertFinding(general.getFindings().get(4), "Nail abnormality", "");
		assertFinding(general.getFindings().get(5), "Nail anemia", "");
		assertFinding(general.getFindings().get(6), "Ankle", "");
	}
	
	/** Section 7.2 example from OpenMRS_HAPI_SHR_Implementation_Final.docx (pages 39–42). */
	public static final String DOC_SECTION_72_EXAMPLE = "<b>General exams: </b><br/>"
	        + "• Eyes: Jaundice-no jaundice seen, [picture taken]. <br/>"
	        + "• Eyes: Pallor-normal pallor, [picture taken]. <br/>"
	        + "• Arm-Pinch skin* - appears slow on pinch test. <br/>"
	        + "• Nail abnormality-nails normal, [picture taken]. <br/>" + "• Nail anemia-Nails are not pale. <br/>"
	        + "• Ankle-no pedal oedema. <br/>" + "<b>Mouth: </b><br/>" + "• tongue is not dry. <br/>"
	        + "• back of throat normal. <br/>";
	
	@Test
	public void parse_docSection72Example_hyphenSeparatesItemAndFinding() {
		List<ParsedExamCategory> categories = new PhysicalExamParser().parse(DOC_SECTION_72_EXAMPLE);
		assertEquals(2, categories.size());
		
		ParsedExamCategory general = categories.get(0);
		assertEquals("General exams", general.getCategoryName());
		assertEquals(6, general.getFindings().size());
		assertFinding(general.getFindings().get(0), "Eyes: Jaundice", "no jaundice seen");
		assertFinding(general.getFindings().get(1), "Eyes: Pallor", "normal pallor");
		assertFinding(general.getFindings().get(2), "Arm-Pinch skin*", "appears slow on pinch test");
		assertFinding(general.getFindings().get(3), "Nail abnormality", "nails normal");
		assertFinding(general.getFindings().get(4), "Nail anemia", "Nails are not pale");
		assertFinding(general.getFindings().get(5), "Ankle", "no pedal oedema");
		
		ParsedExamCategory mouth = categories.get(1);
		assertEquals("Mouth", mouth.getCategoryName());
		assertEquals(2, mouth.getFindings().size());
		assertFinding(mouth.getFindings().get(0), "tongue is not dry", "");
		assertFinding(mouth.getFindings().get(1), "back of throat normal", "");
	}
	
	@Test
	public void parse_generalExamsCategoryAndSixFindings() {
		List<ParsedExamCategory> categories = new PhysicalExamParser().parse(SAMPLE_VALUE_JSON);
		assertEquals(1, categories.size());
		
		ParsedExamCategory general = categories.get(0);
		assertEquals("General exams", general.getCategoryName());
		assertEquals(6, general.getFindings().size());
		
		assertFinding(general.getFindings().get(0), "Eyes: Jaundice", "no jaundice seen");
		assertFinding(general.getFindings().get(1), "Eyes: Pallor", "normal pallor");
		assertFinding(general.getFindings().get(2), "Arm-Pinch skin*", "pinch test normal");
		assertFinding(general.getFindings().get(3), "Nail abnormality", "nails normal");
		assertFinding(general.getFindings().get(4), "Nail anemia", "Nails are normal");
		assertFinding(general.getFindings().get(5), "Ankle", "no pedal oedema");
	}
	
	private static void assertFinding(ParsedFinding finding, String item, String value) {
		assertEquals(item, finding.getItem());
		assertEquals(value, finding.getFinding());
	}
	
}
