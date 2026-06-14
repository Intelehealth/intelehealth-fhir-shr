package org.openmrs.module.ihshr.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtils {
	
	public static Date strToDate(String format, String date) throws ParseException {
		return new SimpleDateFormat(format).parse(date);
	}
	
	public static Date toDate(String date) throws ParseException {
		return strToDate("yyyy-MM-dd", date);
	}
	
	public static String toFormattedDateNow(String format) {
		return new SimpleDateFormat(format).format(new Date());
	}
	
	public static String toFormattedDateNow() {
		return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
	}
	
	/**
	 * Converts {@code ih_marker.last_sync_time} values (often {@code yyyy-MM-dd HH:mm:ss}) into a
	 * string accepted by HAPI FHIR {@code DateRangeParam} / {@code _lastUpdated} search.
	 */
	public static String toFhirLastUpdatedParam(String lastSyncTime) {
		if (lastSyncTime == null) {
			return null;
		}
		String value = lastSyncTime.trim();
		if (value.isEmpty()) {
			return value;
		}
		if (value.length() >= 11 && value.charAt(10) == ' ') {
			return value.substring(0, 10) + 'T' + value.substring(11);
		}
		return value;
	}
	
	public static void main(String[] args) {
		System.out.println(toFormattedDateNow("yyyy-MM-dd HH:mm:ss"));
	}
}
