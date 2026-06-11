package org.openmrs.module.ihshr.config;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.ihshr.utils.ChiefComplaintConstants;
import org.openmrs.module.ihshr.utils.DiagnosisConstants;
import org.openmrs.module.ihshr.utils.FamilyHistoryConstants;
import org.openmrs.module.ihshr.utils.FollowUpConstants;
import org.openmrs.module.ihshr.utils.IhshrPropertyResolver;
import org.openmrs.module.ihshr.utils.ImageObsConstants;
import org.openmrs.module.ihshr.utils.MedicalHistoryConstants;
import org.openmrs.module.ihshr.utils.PhysicalExamConstants;
import org.openmrs.module.ihshr.utils.ReferralConstants;

/**
 * OpenMRS concept IDs used for structured observation routing (concept-only). Configure via GP
 * {@code ihshr.obs.*.concept.ids} or classpath {@code ihshr.properties}.
 */
public final class StructuredObsConceptSettings {
	
	private static final String KEY_CHIEF_COMPLAINT = "obs.chief_complaint.concept.ids";
	
	private static final String KEY_MEDICAL_HISTORY = "obs.medical_history.concept.ids";
	
	private static final String KEY_PHYSICAL_EXAM = "obs.physical_exam.concept.ids";
	
	private static final String KEY_FAMILY_HISTORY = "obs.family_history.concept.ids";
	
	private static final String KEY_DIAGNOSIS = "obs.diagnosis.concept.ids";
	
	private static final String KEY_REFERRAL = "obs.referral.concept.ids";
	
	private static final String KEY_FOLLOW_UP = "obs.follow_up.concept.ids";
	
	private static final String KEY_IMAGE = "obs.image.concept.ids";
	
	private StructuredObsConceptSettings() {
	}
	
	public static Set<Integer> chiefComplaintConceptIds() {
		return parseConceptIds(KEY_CHIEF_COMPLAINT, ChiefComplaintConstants.CHIEF_COMPLAINT_CONCEPT_ID);
	}
	
	public static int primaryChiefComplaintConceptId() {
		return primaryConceptId(chiefComplaintConceptIds(), ChiefComplaintConstants.CHIEF_COMPLAINT_CONCEPT_ID);
	}
	
	public static Set<Integer> medicalHistoryConceptIds() {
		return parseConceptIds(KEY_MEDICAL_HISTORY, MedicalHistoryConstants.PATIENT_MEDICAL_HISTORY_CONCEPT_ID);
	}
	
	public static int primaryMedicalHistoryConceptId() {
		return primaryConceptId(medicalHistoryConceptIds(), MedicalHistoryConstants.PATIENT_MEDICAL_HISTORY_CONCEPT_ID);
	}
	
	public static Set<Integer> physicalExamConceptIds() {
		return parseConceptIds(KEY_PHYSICAL_EXAM, PhysicalExamConstants.PHYSICAL_EXAM_CONCEPT_ID);
	}
	
	public static int primaryPhysicalExamConceptId() {
		return primaryConceptId(physicalExamConceptIds(), PhysicalExamConstants.PHYSICAL_EXAM_CONCEPT_ID);
	}
	
	public static Set<Integer> familyHistoryConceptIds() {
		return parseConceptIds(KEY_FAMILY_HISTORY, FamilyHistoryConstants.FAMILY_HISTORY_CONCEPT_ID);
	}
	
	public static int primaryFamilyHistoryConceptId() {
		return primaryConceptId(familyHistoryConceptIds(), FamilyHistoryConstants.FAMILY_HISTORY_CONCEPT_ID);
	}
	
	public static Set<Integer> diagnosisConceptIds() {
		return parseConceptIds(KEY_DIAGNOSIS, DiagnosisConstants.DIAGNOSIS_CONCEPT_ID);
	}
	
	public static int primaryDiagnosisConceptId() {
		return primaryConceptId(diagnosisConceptIds(), DiagnosisConstants.DIAGNOSIS_CONCEPT_ID);
	}
	
	public static Set<Integer> referralConceptIds() {
		return parseConceptIds(KEY_REFERRAL, ReferralConstants.REFERRAL_CONCEPT_ID);
	}
	
	public static int primaryReferralConceptId() {
		return primaryConceptId(referralConceptIds(), ReferralConstants.REFERRAL_CONCEPT_ID);
	}
	
	public static Set<Integer> followUpConceptIds() {
		return parseConceptIds(KEY_FOLLOW_UP, FollowUpConstants.FOLLOW_UP_CONCEPT_ID);
	}
	
	public static int primaryFollowUpConceptId() {
		return primaryConceptId(followUpConceptIds(), FollowUpConstants.FOLLOW_UP_CONCEPT_ID);
	}
	
	public static Set<Integer> imageConceptIds() {
		return parseConceptIds(KEY_IMAGE, ImageObsConstants.COMPLEX_IMAGE_CONCEPT_1,
		    ImageObsConstants.COMPLEX_IMAGE_CONCEPT_2);
	}
	
	public static boolean matchesConceptId(Integer conceptId, Set<Integer> configuredIds) {
		return conceptId != null && configuredIds.contains(conceptId);
	}
	
	private static int primaryConceptId(Set<Integer> ids, int fallback) {
		if (ids == null || ids.isEmpty()) {
			return fallback;
		}
		return ids.iterator().next();
	}
	
	private static Set<Integer> parseConceptIds(String propertyKey, int... fallbackIds) {
		String raw = resolveRaw(propertyKey);
		if (StringUtils.isBlank(raw)) {
			return fallbackSet(fallbackIds);
		}
		Set<Integer> ids = new LinkedHashSet<Integer>();
		for (String part : raw.split(",")) {
			String trimmed = StringUtils.trimToEmpty(part);
			if (trimmed.isEmpty()) {
				continue;
			}
			try {
				ids.add(Integer.valueOf(trimmed));
			}
			catch (NumberFormatException ignored) {
				// skip invalid tokens
			}
		}
		if (ids.isEmpty()) {
			return fallbackSet(fallbackIds);
		}
		return Collections.unmodifiableSet(ids);
	}
	
	private static Set<Integer> fallbackSet(int... fallbackIds) {
		Set<Integer> ids = new LinkedHashSet<Integer>();
		for (int id : fallbackIds) {
			ids.add(id);
		}
		return Collections.unmodifiableSet(ids);
	}
	
	private static String resolveRaw(String propertyKey) {
		String systemKey = IhshrPropertyResolver.GLOBAL_PROPERTY_PREFIX + propertyKey;
		String raw = System.getProperty(systemKey);
		if (StringUtils.isBlank(raw)) {
			raw = IhshrPropertyResolver.resolve(propertyKey);
		}
		return raw;
	}
}
