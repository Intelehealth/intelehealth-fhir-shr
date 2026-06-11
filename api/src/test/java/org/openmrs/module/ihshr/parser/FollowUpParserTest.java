package org.openmrs.module.ihshr.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalTime;

import org.junit.Test;
import org.openmrs.module.ihshr.domain.ParsedFollowUp;

public class FollowUpParserTest {
	
	private final FollowUpParser parser = new FollowUpParser();
	
	@Test
	public void parse_sampleFollowUp_shouldExtractAllFields() {
		ParsedFollowUp parsed = parser.parse("2026-06-11,Time:10:00 AM,Remark:NA,Type:In person");
		assertNotNull(parsed);
		assertEquals("2026-06-11", parsed.getDateText());
		assertEquals("10:00 AM", parsed.getTimeText());
		assertEquals("NA", parsed.getRemark());
		assertEquals("In person", parsed.getVisitType());
		assertNotNull(parsed.getScheduledDateTime());
	}
	
	@Test
	public void parse_noValue_shouldReturnNull() {
		assertNull(parser.parse("No"));
		assertTrue(FollowUpParser.isDenied("No"));
	}
	
	@Test
	public void parse_jsonWrapper_shouldExtractEnglishValue() {
		String valueText = "{\"en\":\"2026-06-11,Time:10:00 AM,Remark:NA,Type:In person\"}";
		ParsedFollowUp parsed = parser.parse(valueText);
		assertNotNull(parsed);
		assertEquals("In person", parsed.getVisitType());
	}
	
	@Test
	public void parseTime_shouldHandleAmPm() {
		assertEquals(LocalTime.of(10, 0), FollowUpParser.parseTime("10:00 AM"));
		assertEquals(LocalTime.of(14, 30), FollowUpParser.parseTime("2:30 PM"));
	}
	
	@Test
	public void isDenied_shouldRejectCaseInsensitiveNo() {
		assertTrue(FollowUpParser.isDenied("no"));
		assertFalse(FollowUpParser.isDenied("2026-06-11,Time:10:00 AM"));
	}
	
}
