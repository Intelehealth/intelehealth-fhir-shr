package org.openmrs.module.ihshr.synclog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class ShrSyncRetryPolicyTest {
	
	@Test
	public void canRetry_stopsAtMaxAttempts() {
		assertTrue(ShrSyncRetryPolicy.canRetry(1));
		assertTrue(ShrSyncRetryPolicy.canRetry(9));
		assertFalse(ShrSyncRetryPolicy.canRetry(10));
	}
	
	@Test
	public void computeNextRetryAt_usesDocumentedBackoff() {
		assertBackoffMinutes(1, 1);
		assertBackoffMinutes(6, 720);
		assertBackoffMinutes(7, 1440);
		assertBackoffMinutes(10, 1440);
	}
	
	private static void assertBackoffMinutes(int failedAttemptNumber, long expectedMinutes) {
		Date nextRetryAt = ShrSyncRetryPolicy.computeNextRetryAt(failedAttemptNumber);
		Calendar cal = Calendar.getInstance();
		cal.setTime(nextRetryAt);
		cal.add(Calendar.MINUTE, (int) -expectedMinutes);
		long deltaMinutes = TimeUnit.MILLISECONDS.toMinutes(nextRetryAt.getTime() - cal.getTimeInMillis());
		assertEquals(expectedMinutes, deltaMinutes);
	}
}
