package org.openmrs.module.ihshr.pull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

public enum ShrPullRecordType {
	ENCOUNTER("Encounter"), CONDITION("Condition"), OBSERVATION("Observation"), MEDICATION_REQUEST("MedicationRequest"), SERVICE_REQUEST(
	        "ServiceRequest"), DOCUMENT_REFERENCE("DocumentReference"), FAMILY_MEMBER_HISTORY("FamilyMemberHistory"), PROVENANCE(
	        "Provenance");
	
	private final String fhirType;
	
	ShrPullRecordType(String fhirType) {
		this.fhirType = fhirType;
	}
	
	public String getFhirType() {
		return fhirType;
	}
	
	public static List<ShrPullRecordType> parseCsv(String csv) {
		if (StringUtils.isBlank(csv)) {
			return defaultCustomTypes();
		}
		List<ShrPullRecordType> out = new ArrayList<ShrPullRecordType>();
		for (String part : csv.split(",")) {
			String token = part.trim();
			if (token.isEmpty()) {
				continue;
			}
			out.add(fromToken(token));
		}
		if (out.isEmpty()) {
			return defaultCustomTypes();
		}
		return out;
	}
	
	public static List<ShrPullRecordType> defaultCustomTypes() {
		return Collections.unmodifiableList(Arrays.asList(ENCOUNTER, CONDITION, OBSERVATION, MEDICATION_REQUEST,
		    SERVICE_REQUEST, DOCUMENT_REFERENCE, FAMILY_MEMBER_HISTORY));
	}
	
	private static ShrPullRecordType fromToken(String token) {
		for (ShrPullRecordType type : values()) {
			if (type.name().equalsIgnoreCase(token) || type.fhirType.equalsIgnoreCase(token)) {
				return type;
			}
		}
		throw new ShrPullException(ShrPullErrorCode.INVALID_FILTER, "Unknown record type: " + token);
	}
}
