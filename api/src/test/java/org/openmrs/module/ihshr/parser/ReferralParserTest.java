package org.openmrs.module.ihshr.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.openmrs.module.ihshr.domain.ParsedReferral;

public class ReferralParserTest {
	
	private final ReferralParser parser = new ReferralParser();
	
	@Test
	public void parse_electiveReferral_shouldMapSpecialtyCategoryAndNotes() {
		List<ParsedReferral> referrals = parser.parse("General Physician::Elective:sdasfa");
		assertEquals(1, referrals.size());
		ParsedReferral referral = referrals.get(0);
		assertEquals("General Physician", referral.getSpecialty());
		assertEquals("Elective", referral.getCategory());
		assertNull(referral.getPriorityText());
		assertEquals("sdasfa", referral.getNotes());
	}
	
	@Test
	public void parse_urgentReferral_shouldMapAllFields() {
		List<ParsedReferral> referrals = parser.parse("Obstetrician & Gynecologist:PHC:Urgent:4ttr");
		assertEquals(1, referrals.size());
		ParsedReferral referral = referrals.get(0);
		assertEquals("Obstetrician & Gynecologist", referral.getSpecialty());
		assertEquals("PHC", referral.getCategory());
		assertEquals("Urgent", referral.getPriorityText());
		assertEquals("4ttr", referral.getNotes());
	}
	
	@Test
	public void parse_multilineValueText_shouldReturnMultipleReferrals() {
		String valueText = "General Physician::Elective:sdasfa<br/>Obstetrician & Gynecologist:PHC:Urgent:4ttr";
		List<ParsedReferral> referrals = parser.parse(valueText);
		assertEquals(2, referrals.size());
		assertEquals(0, referrals.get(0).getIndex());
		assertEquals(1, referrals.get(1).getIndex());
	}
	
	@Test
	public void parse_jsonWrapper_shouldExtractEnglishValue() {
		String valueText = "{\"en\":\"General Physician::Elective:sdasfa\"}";
		List<ParsedReferral> referrals = parser.parse(valueText);
		assertEquals(1, referrals.size());
		assertEquals("General Physician", referrals.get(0).getSpecialty());
	}
	
	@Test
	public void isPriorityToken_shouldRecognizeUrgentOnly() {
		assertTrue(ReferralParser.isPriorityToken("Urgent"));
		assertTrue(!ReferralParser.isPriorityToken("Elective"));
	}
	
}
