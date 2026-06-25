package org.openmrs.module.ihshr.pull;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ShrPullFormatTest {
	
	@Test
	public void parse_defaultsToEnvelope() {
		assertEquals(ShrPullFormat.ENVELOPE, ShrPullFormat.parse(null));
		assertEquals(ShrPullFormat.ENVELOPE, ShrPullFormat.parse(""));
	}
	
	@Test
	public void parse_acceptsKnownValues() {
		assertEquals(ShrPullFormat.TIMELINE, ShrPullFormat.parse("timeline"));
		assertEquals(ShrPullFormat.FHIR, ShrPullFormat.parse("fhir"));
	}
	
	@Test(expected = ShrPullException.class)
	public void parse_rejectsUnknownValue() {
		ShrPullFormat.parse("custom");
	}
}
