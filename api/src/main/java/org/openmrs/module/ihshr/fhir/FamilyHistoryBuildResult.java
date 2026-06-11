package org.openmrs.module.ihshr.fhir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hl7.fhir.r4.model.FamilyMemberHistory;

public class FamilyHistoryBuildResult {
	
	private final List<FamilyMemberHistory> familyMemberHistories;
	
	public FamilyHistoryBuildResult(List<FamilyMemberHistory> familyMemberHistories) {
		this.familyMemberHistories = familyMemberHistories == null ? Collections.<FamilyMemberHistory> emptyList()
		        : new ArrayList<FamilyMemberHistory>(familyMemberHistories);
	}
	
	public List<FamilyMemberHistory> getFamilyMemberHistories() {
		return Collections.unmodifiableList(familyMemberHistories);
	}
	
	public int totalResourceCount() {
		return familyMemberHistories.size();
	}
	
	public static FamilyHistoryBuildResult empty() {
		return new FamilyHistoryBuildResult(Collections.<FamilyMemberHistory> emptyList());
	}
	
}
