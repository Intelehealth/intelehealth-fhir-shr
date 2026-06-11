package org.openmrs.module.ihshr.synclog;

import java.util.Calendar;
import java.util.Date;

/**
 * Exponential backoff for SHR push retries (doc §3.2, §13.3): 1m, 5m, 15m, 1h, 6h, 12h, 24h; max 10
 * attempts.
 */
public final class ShrSyncRetryPolicy {
	
	public static final int MAX_ATTEMPTS = 10;
	
	private static final int[] BACKOFF_MINUTES = { 1, 5, 15, 60, 360, 720, 1440 };
	
	private ShrSyncRetryPolicy() {
	}
	
	public static boolean canRetry(int attemptNumber) {
		return attemptNumber < MAX_ATTEMPTS;
	}
	
	public static Date computeNextRetryAt(int failedAttemptNumber) {
		int index = failedAttemptNumber - 1;
		if (index < 0) {
			index = 0;
		}
		if (index >= BACKOFF_MINUTES.length) {
			index = BACKOFF_MINUTES.length - 1;
		}
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.MINUTE, BACKOFF_MINUTES[index]);
		return cal.getTime();
	}
}
