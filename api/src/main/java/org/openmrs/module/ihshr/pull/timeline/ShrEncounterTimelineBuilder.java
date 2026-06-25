package org.openmrs.module.ihshr.pull.timeline;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.FamilyMemberHistory;
import org.hl7.fhir.r4.model.FamilyMemberHistory.FamilyMemberHistoryConditionComponent;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationComponentComponent;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Provenance.ProvenanceAgentComponent;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.openmrs.module.ihshr.fhir.ShrPushMetaApplicator;
import org.openmrs.module.ihshr.pull.ShrHistoryRequest;
import org.openmrs.module.ihshr.pull.ShrPullRecordType;
import org.openmrs.module.ihshr.pull.ShrRecordTypeSelection;
import org.openmrs.module.ihshr.utils.DiagnosisConstants;
import org.openmrs.module.ihshr.utils.ReferralConstants;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builds doc §10.2 / §10.3 timeline cards from a merged FHIR bundle.
 */
public final class ShrEncounterTimelineBuilder {
	
	private static final String BINARY_API_PREFIX = "ws/rest/v1/ihshr/shr/binary/";
	
	private static final String DOCTOR_DETAILS_LABEL = "doctor details";
	
	private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
	
	private static final DateTimeFormatter ISO_INSTANT = DateTimeFormatter.ISO_INSTANT;
	
	private ShrEncounterTimelineBuilder() {
	}
	
	public static ShrTimelineBuildResult build(Bundle bundle, ShrHistoryRequest request) {
		ShrFhirBundleIndex index = new ShrFhirBundleIndex(bundle);
		List<Map<String, Object>> timeline = new ArrayList<Map<String, Object>>();
		List<String> warnings = new ArrayList<String>();
		
		List<Encounter> encounters = listEncounters(bundle);
		sortEncounters(encounters, request != null && request.isDescendingSort());
		
		Set<String> usedConditions = new LinkedHashSet<String>();
		Set<String> usedMedications = new LinkedHashSet<String>();
		Set<String> usedObservations = new LinkedHashSet<String>();
		Set<String> usedDocuments = new LinkedHashSet<String>();
		Set<String> usedServiceRequests = new LinkedHashSet<String>();
		Set<String> usedFamilyMemberHistories = new LinkedHashSet<String>();
		
		for (Encounter encounter : encounters) {
			if (!ShrRecordTypeSelection.includes(request, ShrPullRecordType.ENCOUNTER)) {
				continue;
			}
			String encounterKey = encounterCanonicalKey(encounter);
			Map<String, Object> card = new LinkedHashMap<String, Object>();
			card.put("encounterId", encounter.getIdElement().getIdPart());
			card.put("encounterReference", encounterKey);
			card.put("visitDateTime", formatInstant(encounter.hasPeriod() ? encounter.getPeriod().getStart() : null));
			card.put(
			    "facility",
			    ShrRecordTypeSelection.includes(request, ShrPullRecordType.ENCOUNTER) ? resolveFacility(encounter, index,
			        warnings, encounterKey) : Collections.<String, Object> emptyMap());
			card.put(
			    "clinician",
			    ShrRecordTypeSelection.includes(request, ShrPullRecordType.ENCOUNTER) ? resolveClinician(encounter, index,
			        warnings, encounterKey) : Collections.<String, Object> emptyMap());
			card.put(
			    "diagnoses",
			    ShrRecordTypeSelection.includes(request, ShrPullRecordType.CONDITION) ? resolveDiagnoses(encounter,
			        encounterKey, index, usedConditions) : Collections.<Map<String, Object>> emptyList());
			card.put(
			    "prescriptions",
			    ShrRecordTypeSelection.includes(request, ShrPullRecordType.MEDICATION_REQUEST) ? resolvePrescriptions(
			        encounterKey, index, usedMedications) : Collections.<Map<String, Object>> emptyList());
			Map<String, List<Map<String, Object>>> observations = ShrRecordTypeSelection.includes(request,
			    ShrPullRecordType.OBSERVATION) ? resolveObservations(encounterKey, index, usedObservations)
			        : emptyObservationGroups();
			card.put("vitals", observations.get("vitals"));
			card.put("labs", resolveLabs(encounterKey, index, observations.get("labs"), usedServiceRequests, request));
			card.put("familyHistory", Collections.<Map<String, Object>> emptyList());
			card.put(
			    "documents",
			    ShrRecordTypeSelection.includes(request, ShrPullRecordType.DOCUMENT_REFERENCE) ? resolveDocuments(
			        encounterKey, index, usedDocuments) : Collections.<Map<String, Object>> emptyList());
			card.put(
			    "source",
			    ShrRecordTypeSelection.includes(request, ShrPullRecordType.PROVENANCE) ? resolveSource(encounter,
			        encounterKey, index, warnings) : Collections.<String, Object> emptyMap());
			if (ShrRecordTypeSelection.includes(request, ShrPullRecordType.OBSERVATION)) {
				applyDoctorDetails(encounterKey, index, card, usedObservations);
			}
			timeline.add(card);
		}
		
		if (ShrRecordTypeSelection.includes(request, ShrPullRecordType.OBSERVATION)
		        || ShrRecordTypeSelection.includes(request, ShrPullRecordType.SERVICE_REQUEST)) {
			appendOrphanLabCards(bundle, index, usedObservations, usedServiceRequests, request, timeline);
		}
		
		if (ShrRecordTypeSelection.includes(request, ShrPullRecordType.FAMILY_MEMBER_HISTORY)) {
			appendFamilyHistoryCard(bundle, index, usedFamilyMemberHistories, timeline);
		}
		
		return new ShrTimelineBuildResult(timeline, warnings);
	}
	
	private static Map<String, List<Map<String, Object>>> emptyObservationGroups() {
		Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<String, List<Map<String, Object>>>();
		grouped.put("vitals", Collections.<Map<String, Object>> emptyList());
		grouped.put("labs", Collections.<Map<String, Object>> emptyList());
		return grouped;
	}
	
	private static List<Encounter> listEncounters(Bundle bundle) {
		List<Encounter> encounters = new ArrayList<Encounter>();
		if (bundle == null || !bundle.hasEntry()) {
			return encounters;
		}
		for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
			if (entry.hasResource() && entry.getResource() instanceof Encounter) {
				encounters.add((Encounter) entry.getResource());
			}
		}
		return encounters;
	}
	
	private static void sortEncounters(List<Encounter> encounters, boolean descending) {
		Comparator<Encounter> comparator = Comparator.comparing(
		    encounter -> encounter.hasPeriod() && encounter.getPeriod().hasStart() ? encounter.getPeriod().getStart()
		            : new Date(0L));
		if (descending) {
			comparator = comparator.reversed();
		}
		Collections.sort(encounters, comparator);
	}
	
	private static Map<String, Object> resolveFacility(Encounter encounter, ShrFhirBundleIndex index, List<String> warnings,
	        String encounterKey) {
		Map<String, Object> facility = new LinkedHashMap<String, Object>();
		if (encounter.hasLocation()) {
			for (Encounter.EncounterLocationComponent locationComponent : encounter.getLocation()) {
				if (locationComponent == null || !locationComponent.hasLocation()) {
					continue;
				}
				Reference ref = locationComponent.getLocation();
				Location location = index.resolve(ref, Location.class);
				if (location != null && StringUtils.isNotBlank(location.getName())) {
					facility.put("name", location.getName());
					facility.put("reference", ShrFhirBundleIndex.referenceKey(ref));
					return facility;
				}
				if (ref.hasDisplay()) {
					facility.put("name", ref.getDisplay());
					facility.put("reference", ShrFhirBundleIndex.referenceKey(ref));
					return facility;
				}
			}
		}
		if (encounter.hasServiceProvider()) {
			Reference ref = encounter.getServiceProvider();
			Organization org = index.resolve(ref, Organization.class);
			if (org != null && org.hasName()) {
				facility.put("name", org.getName());
				facility.put("reference", ShrFhirBundleIndex.referenceKey(ref));
				return facility;
			}
			if (ref.hasDisplay()) {
				facility.put("name", ref.getDisplay());
				facility.put("reference", ShrFhirBundleIndex.referenceKey(ref));
				return facility;
			}
		}
		String originFacilityName = ShrPushMetaApplicator.originTagFacilityName(encounter);
		if (StringUtils.isNotBlank(originFacilityName)) {
			facility.put("name", originFacilityName);
			return facility;
		}
		warnings.add("Facility name not resolved for " + encounterKey);
		return facility;
	}
	
	private static Map<String, Object> resolveClinician(Encounter encounter, ShrFhirBundleIndex index,
	        List<String> warnings, String encounterKey) {
		Map<String, Object> clinician = new LinkedHashMap<String, Object>();
		if (!encounter.hasParticipant()) {
			warnings.add("Clinician not found for " + encounterKey);
			return clinician;
		}
		for (Encounter.EncounterParticipantComponent participant : encounter.getParticipant()) {
			if (participant == null || !participant.hasIndividual()) {
				continue;
			}
			Reference ref = participant.getIndividual();
			Practitioner practitioner = index.resolve(ref, Practitioner.class);
			if (practitioner != null && practitioner.hasName()) {
				HumanName name = practitioner.getNameFirstRep();
				if (name.hasText()) {
					clinician.put("name", name.getText());
				} else {
					clinician.put("name", name.getNameAsSingleString());
				}
			} else if (ref.hasDisplay()) {
				clinician.put("name", ref.getDisplay());
			}
			if (participant.hasType() && participant.getTypeFirstRep().hasText()) {
				clinician.put("role", participant.getTypeFirstRep().getText());
			} else if (participant.hasType() && participant.getTypeFirstRep().hasCoding()) {
				Coding coding = participant.getTypeFirstRep().getCodingFirstRep();
				clinician.put("role", StringUtils.defaultIfBlank(coding.getDisplay(), coding.getCode()));
			}
			clinician.put("reference", ShrFhirBundleIndex.referenceKey(ref));
			if (clinician.containsKey("name")) {
				return clinician;
			}
		}
		warnings.add("Clinician not resolved for " + encounterKey);
		return clinician;
	}
	
	private static List<Map<String, Object>> resolveDiagnoses(Encounter encounter, String encounterKey,
	        ShrFhirBundleIndex index, Set<String> usedConditions) {
		List<Map<String, Object>> diagnoses = new ArrayList<Map<String, Object>>();
		Set<String> seen = new LinkedHashSet<String>();
		Map<String, Integer> ranksByCondition = diagnosisRanksByCondition(encounter);
		
		if (encounter.hasDiagnosis()) {
			for (Encounter.DiagnosisComponent diagnosisComponent : encounter.getDiagnosis()) {
				if (diagnosisComponent == null || !diagnosisComponent.hasCondition()) {
					continue;
				}
				Condition condition = index.resolve(diagnosisComponent.getCondition(), Condition.class);
				if (condition != null) {
					Integer rank = diagnosisComponent.hasRank() ? diagnosisComponent.getRank() : ranksByCondition
					        .get(conditionCanonicalKey(condition));
					addDiagnosis(diagnoses, seen, condition, rank);
					markUsed(usedConditions, condition);
				}
			}
		}
		
		for (Resource resource : index.allResources()) {
			if (!(resource instanceof Condition)) {
				continue;
			}
			Condition condition = (Condition) resource;
			if (!matchesEncounter(condition.getEncounter(), encounterKey)) {
				continue;
			}
			if (!isEncounterDiagnosis(condition)) {
				continue;
			}
			addDiagnosis(diagnoses, seen, condition, ranksByCondition.get(conditionCanonicalKey(condition)));
			markUsed(usedConditions, condition);
		}
		return diagnoses;
	}
	
	private static Map<String, Integer> diagnosisRanksByCondition(Encounter encounter) {
		Map<String, Integer> ranks = new LinkedHashMap<String, Integer>();
		if (!encounter.hasDiagnosis()) {
			return ranks;
		}
		for (Encounter.DiagnosisComponent diagnosisComponent : encounter.getDiagnosis()) {
			if (diagnosisComponent == null || !diagnosisComponent.hasCondition() || !diagnosisComponent.hasRank()) {
				continue;
			}
			String conditionKey = ShrFhirBundleIndex.referenceKey(diagnosisComponent.getCondition());
			if (conditionKey != null) {
				ranks.put(conditionKey, diagnosisComponent.getRank());
			}
		}
		return ranks;
	}
	
	private static void addDiagnosis(List<Map<String, Object>> diagnoses, Set<String> seen, Condition condition, Integer rank) {
		String key = conditionCanonicalKey(condition);
		if (key == null || seen.contains(key)) {
			return;
		}
		seen.add(key);
		Map<String, Object> row = new LinkedHashMap<String, Object>();
		row.put("conditionId", condition.getIdElement().getIdPart());
		row.put("text", conditionText(condition));
		Coding primary = primaryCoding(condition.getCode());
		if (primary != null) {
			row.put("code", primary.getCode());
			row.put("system", primary.getSystem());
			row.put("display", primary.getDisplay());
		}
		row.put("verificationStatus", codeValue(condition.getVerificationStatus()));
		row.put("clinicalStatus", codeValue(condition.getClinicalStatus()));
		if (rank != null) {
			row.put("rank", rank);
		}
		Map<String, Object> note = conditionNote(condition);
		if (note != null) {
			row.put("note", note);
		}
		diagnoses.add(row);
	}
	
	private static Map<String, Object> conditionNote(Condition condition) {
		if (!condition.hasNote()) {
			return null;
		}
		StringBuilder text = new StringBuilder();
		for (Annotation annotation : condition.getNote()) {
			if (annotation == null || !annotation.hasText()) {
				continue;
			}
			if (text.length() > 0) {
				text.append("\n");
			}
			text.append(annotation.getText());
		}
		if (text.length() == 0) {
			return null;
		}
		Map<String, Object> note = new LinkedHashMap<String, Object>();
		note.put("text", text.toString());
		return note;
	}
	
	private static boolean isEncounterDiagnosis(Condition condition) {
		if (!condition.hasCategory()) {
			return true;
		}
		for (CodeableConcept category : condition.getCategory()) {
			if (category == null || !category.hasCoding()) {
				continue;
			}
			for (Coding coding : category.getCoding()) {
				if (DiagnosisConstants.CONDITION_CATEGORY_CODE.equals(coding.getCode())) {
					return true;
				}
			}
		}
		return false;
	}
	
	private static List<Map<String, Object>> resolvePrescriptions(String encounterKey, ShrFhirBundleIndex index,
	        Set<String> usedMedications) {
		List<Map<String, Object>> prescriptions = new ArrayList<Map<String, Object>>();
		for (Resource resource : index.allResources()) {
			if (!(resource instanceof MedicationRequest)) {
				continue;
			}
			MedicationRequest medicationRequest = (MedicationRequest) resource;
			if (!matchesEncounter(medicationRequest.getEncounter(), encounterKey)) {
				continue;
			}
			Map<String, Object> row = prescriptionRow(medicationRequest);
			prescriptions.add(row);
			markUsed(usedMedications, medicationRequest);
		}
		return prescriptions;
	}
	
	private static Map<String, Object> prescriptionRow(MedicationRequest medicationRequest) {
		Map<String, Object> row = new LinkedHashMap<String, Object>();
		row.put("medicationRequestId", medicationRequest.getIdElement().getIdPart());
		row.put("status", medicationRequest.hasStatus() ? medicationRequest.getStatus().toCode() : null);
		
		Map<String, Object> medication = medicationSummary(medicationRequest);
		if (!medication.isEmpty()) {
			row.put("medication", medication);
		}
		
		if (medicationRequest.hasDosageInstruction()) {
			Dosage dosage = medicationRequest.getDosageInstructionFirstRep();
			if (dosage.hasText()) {
				Map<String, String> parsedDosage = parseDosageInstructionText(dosage.getText());
				if (StringUtils.isNotBlank(parsedDosage.get("strength"))) {
					row.put("strength", parsedDosage.get("strength"));
				}
				if (StringUtils.isNotBlank(parsedDosage.get("frequency"))) {
					row.put("frequency", parsedDosage.get("frequency"));
				}
				if (StringUtils.isNotBlank(parsedDosage.get("instructions"))) {
					row.put("instructions", parsedDosage.get("instructions"));
				}
			}
			applyDosageDuration(dosage, row);
		}
		
		if (medicationRequest.hasDispenseRequest() && medicationRequest.getDispenseRequest().hasQuantity()
		        && medicationRequest.getDispenseRequest().getQuantity().hasUnit()) {
			row.put("unit", medicationRequest.getDispenseRequest().getQuantity().getUnit());
		}
		
		row.put("text", formatPrescriptionSummary(row));
		return row;
	}
	
	private static Map<String, Object> medicationSummary(MedicationRequest medicationRequest) {
		Map<String, Object> medication = new LinkedHashMap<String, Object>();
		if (medicationRequest.hasMedicationReference()) {
			Reference reference = medicationRequest.getMedicationReference();
			String referenceKey = ShrFhirBundleIndex.referenceKey(reference);
			if (referenceKey != null) {
				medication.put("reference", referenceKey);
			}
			if (reference.hasDisplay()) {
				medication.put("display", reference.getDisplay());
			}
		} else if (medicationRequest.hasMedicationCodeableConcept()) {
			CodeableConcept concept = medicationRequest.getMedicationCodeableConcept();
			if (concept.hasText()) {
				medication.put("display", concept.getText());
			} else {
				Coding coding = primaryCoding(concept);
				if (coding != null) {
					medication.put("display", StringUtils.defaultIfBlank(coding.getDisplay(), coding.getCode()));
					if (coding.hasCode()) {
						medication.put("code", coding.getCode());
					}
					if (coding.hasSystem()) {
						medication.put("system", coding.getSystem());
					}
				}
			}
		}
		return medication;
	}
	
	/**
	 * OpenMRS drug order dosage text: {@code conceptUuid|strength|frequency|instructions}.
	 */
	private static Map<String, String> parseDosageInstructionText(String raw) {
		Map<String, String> parsed = new LinkedHashMap<String, String>();
		if (StringUtils.isBlank(raw)) {
			return parsed;
		}
		String[] parts = raw.split("\\|", -1);
		if (parts.length > 0 && StringUtils.isNotBlank(parts[0])) {
			parsed.put("conceptCode", parts[0].trim());
		}
		if (parts.length > 1 && StringUtils.isNotBlank(parts[1])) {
			parsed.put("strength", parts[1].trim());
		}
		if (parts.length > 2 && StringUtils.isNotBlank(parts[2])) {
			parsed.put("frequency", parts[2].trim());
		}
		if (parts.length > 3 && StringUtils.isNotBlank(parts[3])) {
			parsed.put("instructions", parts[3].trim());
		}
		return parsed;
	}
	
	private static void applyDosageDuration(Dosage dosage, Map<String, Object> row) {
		if (!dosage.hasTiming() || !dosage.getTiming().hasRepeat()) {
			return;
		}
		if (dosage.getTiming().getRepeat().hasDuration()) {
			row.put("duration", dosage.getTiming().getRepeat().getDuration().doubleValue());
		}
		if (dosage.getTiming().getRepeat().hasDurationUnit()) {
			row.put("durationUnit", dosage.getTiming().getRepeat().getDurationUnit().toCode());
		}
	}
	
	private static String formatPrescriptionSummary(Map<String, Object> row) {
		StringBuilder text = new StringBuilder();
		@SuppressWarnings("unchecked")
		Map<String, Object> medication = (Map<String, Object>) row.get("medication");
		if (medication != null && medication.get("display") != null) {
			text.append(medication.get("display"));
		}
		appendSummaryPart(text, row.get("strength"));
		appendSummaryPart(text, row.get("frequency"));
		if (row.get("duration") != null) {
			appendSummaryPart(text, row.get("duration") + StringUtils.defaultString((String) row.get("durationUnit"), ""));
		}
		if (row.get("unit") != null) {
			appendSummaryPart(text, row.get("unit"));
		}
		appendSummaryPart(text, row.get("instructions"));
		return text.toString().trim();
	}
	
	private static void appendSummaryPart(StringBuilder text, Object part) {
		if (part == null || StringUtils.isBlank(part.toString())) {
			return;
		}
		if (text.length() > 0) {
			text.append(" — ");
		}
		text.append(part);
	}
	
	private static List<Map<String, Object>> resolveLabs(String encounterKey, ShrFhirBundleIndex index,
	        List<Map<String, Object>> observationLabs, Set<String> usedServiceRequests, ShrHistoryRequest request) {
		List<Map<String, Object>> labs = new ArrayList<Map<String, Object>>();
		if (observationLabs != null && ShrRecordTypeSelection.includes(request, ShrPullRecordType.OBSERVATION)) {
			labs.addAll(observationLabs);
		}
		if (ShrRecordTypeSelection.includes(request, ShrPullRecordType.SERVICE_REQUEST)) {
			labs.addAll(resolveLabOrders(encounterKey, index, usedServiceRequests));
		}
		return labs;
	}
	
	private static List<Map<String, Object>> resolveLabOrders(String encounterKey, ShrFhirBundleIndex index,
	        Set<String> usedServiceRequests) {
		List<Map<String, Object>> labOrders = new ArrayList<Map<String, Object>>();
		for (Resource resource : index.allResources()) {
			if (!(resource instanceof ServiceRequest)) {
				continue;
			}
			ServiceRequest serviceRequest = (ServiceRequest) resource;
			if (!matchesEncounter(serviceRequest.getEncounter(), encounterKey) || !isLabOrder(serviceRequest)) {
				continue;
			}
			labOrders.add(labOrderRow(serviceRequest, index));
			markUsed(usedServiceRequests, serviceRequest);
		}
		return labOrders;
	}
	
	/**
	 * Lab orders are exported as {@link ServiceRequest} resources without an {@code identifier}.
	 * Referrals and other structured IH orders carry an identifier.
	 */
	private static boolean isLabOrder(ServiceRequest serviceRequest) {
		return serviceRequest != null && !isReferralServiceRequest(serviceRequest) && !serviceRequest.hasIdentifier();
	}
	
	private static boolean isReferralServiceRequest(ServiceRequest serviceRequest) {
		if (!serviceRequest.hasCategory()) {
			return false;
		}
		for (CodeableConcept category : serviceRequest.getCategory()) {
			if (category == null || !category.hasCoding()) {
				continue;
			}
			for (Coding coding : category.getCoding()) {
				if (ReferralConstants.SERVICE_REQUEST_TYPE_SYSTEM.equals(coding.getSystem())
				        && ReferralConstants.SERVICE_REQUEST_TYPE_REFERRAL.equals(coding.getCode())) {
					return true;
				}
			}
		}
		return false;
	}
	
	private static Map<String, Object> labOrderRow(ServiceRequest serviceRequest, ShrFhirBundleIndex index) {
		Map<String, Object> row = new LinkedHashMap<String, Object>();
		row.put("kind", "order");
		row.put("resourceType", "ServiceRequest");
		row.put("serviceRequestId", serviceRequest.getIdElement().getIdPart());
		row.put("label", serviceRequestLabel(serviceRequest));
		row.put("status", serviceRequest.hasStatus() ? serviceRequest.getStatus().toCode() : null);
		if (serviceRequest.hasPriority()) {
			row.put("priority", serviceRequest.getPriority().toCode());
		}
		if (serviceRequest.hasAuthoredOn()) {
			row.put("orderedAt", formatInstant(serviceRequest.getAuthoredOn()));
		}
		if (serviceRequest.hasRequester()) {
			row.put("requester", displayFromReference(serviceRequest.getRequester(), index));
		}
		Coding coding = primaryCoding(serviceRequest.getCode());
		if (coding != null) {
			row.put("code", coding.getCode());
			row.put("system", coding.getSystem());
		}
		row.put("text", formatLabOrderSummary(row));
		return row;
	}
	
	private static String serviceRequestLabel(ServiceRequest serviceRequest) {
		if (serviceRequest.hasCode()) {
			if (serviceRequest.getCode().hasText()) {
				return serviceRequest.getCode().getText();
			}
			Coding coding = primaryCoding(serviceRequest.getCode());
			if (coding != null) {
				return StringUtils.defaultIfBlank(coding.getDisplay(), coding.getCode());
			}
		}
		return "Lab order";
	}
	
	private static String formatLabOrderSummary(Map<String, Object> row) {
		StringBuilder text = new StringBuilder();
		if (row.get("label") != null) {
			text.append(row.get("label"));
		}
		appendSummaryPart(text, row.get("status"));
		appendSummaryPart(text, row.get("priority"));
		appendSummaryPart(text, row.get("requester"));
		return text.toString().trim();
	}
	
	private static List<Map<String, Object>> resolveFamilyHistories(ShrFhirBundleIndex index,
	        Set<String> usedFamilyMemberHistories) {
		List<Map<String, Object>> familyHistory = new ArrayList<Map<String, Object>>();
		for (Resource resource : index.allResources()) {
			if (!(resource instanceof FamilyMemberHistory)) {
				continue;
			}
			FamilyMemberHistory history = (FamilyMemberHistory) resource;
			if (usedFamilyMemberHistories.contains(resourceKey(history))) {
				continue;
			}
			familyHistory.add(familyHistoryRow(history));
			markUsed(usedFamilyMemberHistories, history);
		}
		return familyHistory;
	}
	
	private static Map<String, Object> familyHistoryRow(FamilyMemberHistory history) {
		Map<String, Object> row = new LinkedHashMap<String, Object>();
		row.put("resourceType", "FamilyMemberHistory");
		row.put("familyMemberHistoryId", history.getIdElement().getIdPart());
		String relationship = familyHistoryRelationshipLabel(history);
		row.put("label", relationship);
		List<Map<String, Object>> conditions = familyHistoryConditions(history);
		row.put("conditions", conditions);
		row.put("value", formatFamilyHistoryValue(conditions));
		if (history.hasStatus()) {
			row.put("status", history.getStatus().toCode());
		}
		if (history.hasDate()) {
			row.put("recordedAt", formatInstant(history.getDate()));
		}
		Map<String, Object> note = familyHistoryNote(history);
		if (note != null) {
			row.put("note", note);
		}
		if (StringUtils.isNotBlank(relationship)) {
			Map<String, Object> relationshipMap = new LinkedHashMap<String, Object>();
			relationshipMap.put("text", relationship);
			if (history.hasRelationship()) {
				Coding coding = primaryCoding(history.getRelationship());
				if (coding != null) {
					relationshipMap.put("code", coding.getCode());
					relationshipMap.put("system", coding.getSystem());
					relationshipMap.put("display", coding.getDisplay());
				}
			}
			row.put("relationship", relationshipMap);
		}
		return row;
	}
	
	private static String familyHistoryRelationshipLabel(FamilyMemberHistory history) {
		if (history.hasRelationship()) {
			if (history.getRelationship().hasText()) {
				return history.getRelationship().getText();
			}
			Coding coding = primaryCoding(history.getRelationship());
			if (coding != null) {
				return StringUtils.defaultIfBlank(coding.getDisplay(), coding.getCode());
			}
		}
		return "Family member";
	}
	
	private static List<Map<String, Object>> familyHistoryConditions(FamilyMemberHistory history) {
		List<Map<String, Object>> conditions = new ArrayList<Map<String, Object>>();
		if (!history.hasCondition()) {
			return conditions;
		}
		for (FamilyMemberHistoryConditionComponent conditionComponent : history.getCondition()) {
			if (conditionComponent == null || !conditionComponent.hasCode()) {
				continue;
			}
			Map<String, Object> condition = new LinkedHashMap<String, Object>();
			condition.put("text", codeableConceptText(conditionComponent.getCode()));
			Coding coding = primaryCoding(conditionComponent.getCode());
			if (coding != null) {
				condition.put("code", coding.getCode());
				condition.put("system", coding.getSystem());
				condition.put("display", coding.getDisplay());
			}
			conditions.add(condition);
		}
		return conditions;
	}
	
	private static String formatFamilyHistoryValue(List<Map<String, Object>> conditions) {
		if (conditions == null || conditions.isEmpty()) {
			return null;
		}
		StringBuilder text = new StringBuilder();
		for (Map<String, Object> condition : conditions) {
			if (condition == null || condition.get("text") == null) {
				continue;
			}
			if (text.length() > 0) {
				text.append(", ");
			}
			text.append(condition.get("text"));
		}
		return text.length() > 0 ? text.toString() : null;
	}
	
	private static Map<String, Object> familyHistoryNote(FamilyMemberHistory history) {
		if (!history.hasNote()) {
			return null;
		}
		StringBuilder text = new StringBuilder();
		for (Annotation annotation : history.getNote()) {
			if (annotation == null || !annotation.hasText()) {
				continue;
			}
			if (text.length() > 0) {
				text.append("\n");
			}
			text.append(annotation.getText());
		}
		if (text.length() == 0) {
			return null;
		}
		Map<String, Object> note = new LinkedHashMap<String, Object>();
		note.put("text", text.toString());
		return note;
	}
	
	private static Map<String, List<Map<String, Object>>> resolveObservations(String encounterKey, ShrFhirBundleIndex index,
	        Set<String> usedObservations) {
		List<Map<String, Object>> vitals = new ArrayList<Map<String, Object>>();
		List<Map<String, Object>> labs = new ArrayList<Map<String, Object>>();
		for (Resource resource : index.allResources()) {
			if (!(resource instanceof Observation)) {
				continue;
			}
			Observation observation = (Observation) resource;
			if (!matchesEncounter(observation.getEncounter(), encounterKey)) {
				continue;
			}
			if (isDoctorDetailsObservation(observation)) {
				continue;
			}
			Map<String, Object> row = observationRow(observation);
			if (isLabObservation(observation)) {
				labs.add(row);
			} else if (isVitalObservation(observation)) {
				vitals.add(row);
			}
			markUsed(usedObservations, observation);
		}
		Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<String, List<Map<String, Object>>>();
		grouped.put("vitals", vitals);
		grouped.put("labs", labs);
		return grouped;
	}
	
	/**
	 * Doctor profile is pushed as an {@link Observation} with label "Doctor details" and a JSON
	 * {@code valueString}. It is not a lab result — expose it as structured {@code doctorDetails}.
	 */
	private static void applyDoctorDetails(String encounterKey, ShrFhirBundleIndex index, Map<String, Object> card,
	        Set<String> usedObservations) {
		for (Resource resource : index.allResources()) {
			if (!(resource instanceof Observation)) {
				continue;
			}
			Observation observation = (Observation) resource;
			if (!matchesEncounter(observation.getEncounter(), encounterKey) || !isDoctorDetailsObservation(observation)) {
				continue;
			}
			Map<String, Object> details = parseDoctorDetails(observation);
			if (details != null && !details.isEmpty()) {
				card.put("doctorDetails", details);
				enrichClinicianFromDoctorDetails(card, details);
			}
			markUsed(usedObservations, observation);
		}
	}
	
	private static boolean isDoctorDetailsObservation(Observation observation) {
		String label = observationLabel(observation);
		return label != null && DOCTOR_DETAILS_LABEL.equalsIgnoreCase(label.trim());
	}
	
	/**
	 * SHR stores vitals as plain {@link Observation} resources without {@code identifier}.
	 * Structured clinical data (physical exam, follow-up, smoking history, etc.) is pushed with an
	 * identifier.
	 */
	private static boolean isVitalObservation(Observation observation) {
		if (observation.hasIdentifier()) {
			return false;
		}
		String category = observationCategory(observation);
		if ("survey".equals(category) || "social-history".equals(category)) {
			return false;
		}
		return true;
	}
	
	private static boolean isLabObservation(Observation observation) {
		if (isDoctorDetailsObservation(observation)) {
			return false;
		}
		return "laboratory".equals(observationCategory(observation)) && observation.hasIdentifier();
	}
	
	private static Map<String, Object> parseDoctorDetails(Observation observation) {
		if (!observation.hasValue()) {
			return null;
		}
		String raw = formatObservationValue(observation);
		if (StringUtils.isBlank(raw)) {
			return null;
		}
		try {
			return JSON_MAPPER.readValue(raw, new TypeReference<Map<String, Object>>() {});
		}
		catch (Exception ex) {
			Map<String, Object> fallback = new LinkedHashMap<String, Object>();
			fallback.put("raw", raw);
			return fallback;
		}
	}
	
	@SuppressWarnings("unchecked")
	private static void enrichClinicianFromDoctorDetails(Map<String, Object> card, Map<String, Object> details) {
		Map<String, Object> clinician = (Map<String, Object>) card.get("clinician");
		if (clinician == null) {
			clinician = new LinkedHashMap<String, Object>();
			card.put("clinician", clinician);
		}
		if (!clinician.containsKey("name") && details.get("name") != null) {
			clinician.put("name", String.valueOf(details.get("name")));
		}
		if (!clinician.containsKey("specialization") && details.get("specialization") != null) {
			clinician.put("specialization", String.valueOf(details.get("specialization")));
		}
		if (!clinician.containsKey("qualification") && details.get("qualification") != null) {
			clinician.put("qualification", String.valueOf(details.get("qualification")));
		}
		if (details.get("uuid") != null) {
			clinician.put("uuid", String.valueOf(details.get("uuid")));
		}
		if (details.get("signature") != null) {
			clinician.put("signatureUrl", String.valueOf(details.get("signature")));
		}
	}
	
	private static List<Map<String, Object>> resolveDocuments(String encounterKey, ShrFhirBundleIndex index,
	        Set<String> usedDocuments) {
		List<Map<String, Object>> documents = new ArrayList<Map<String, Object>>();
		for (Resource resource : index.allResources()) {
			if (!(resource instanceof DocumentReference)) {
				continue;
			}
			DocumentReference documentReference = (DocumentReference) resource;
			if (!documentReferencesEncounter(documentReference, encounterKey)) {
				continue;
			}
			Map<String, Object> row = new LinkedHashMap<String, Object>();
			String id = documentReference.getIdElement().getIdPart();
			row.put("documentReferenceId", id);
			row.put("title", documentTitle(documentReference));
			row.put("binaryUrl", id != null ? BINARY_API_PREFIX + id : null);
			if (documentReference.hasContent()) {
				DocumentReference.DocumentReferenceContentComponent content = documentReference.getContentFirstRep();
				if (content.hasAttachment() && content.getAttachment().hasContentType()) {
					row.put("contentType", content.getAttachment().getContentType());
				}
			}
			documents.add(row);
			markUsed(usedDocuments, documentReference);
		}
		return documents;
	}
	
	private static Map<String, Object> resolveSource(Encounter encounter, String encounterKey, ShrFhirBundleIndex index,
	        List<String> warnings) {
		Map<String, Object> source = new LinkedHashMap<String, Object>();
		Provenance provenance = findProvenanceForTarget(index, encounterKey);
		if (provenance != null && provenance.hasAgent()) {
			for (ProvenanceAgentComponent agent : provenance.getAgent()) {
				if (agent == null) {
					continue;
				}
				if (isAuthorAgent(agent) && agent.hasWho()) {
					source.put("asserter", displayFromReference(agent.getWho(), index));
				}
				if (agent.hasOnBehalfOf()) {
					source.put("organization", displayFromReference(agent.getOnBehalfOf(), index));
				}
			}
		}
		if (encounter.hasMeta() && encounter.getMeta().hasSource()) {
			source.put("installationUri", encounter.getMeta().getSource());
		}
		if (!source.containsKey("asserter") && !source.containsKey("organization")) {
			warnings.add("Provenance source not resolved for " + encounterKey);
		}
		return source;
	}
	
	private static void appendOrphanLabCards(Bundle bundle, ShrFhirBundleIndex index, Set<String> usedObservations,
	        Set<String> usedServiceRequests, ShrHistoryRequest request, List<Map<String, Object>> timeline) {
		if (bundle == null || !bundle.hasEntry()) {
			return;
		}
		List<Map<String, Object>> orphanLabs = new ArrayList<Map<String, Object>>();
		if (ShrRecordTypeSelection.includes(request, ShrPullRecordType.OBSERVATION)) {
			for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
				if (!entry.hasResource() || !(entry.getResource() instanceof Observation)) {
					continue;
				}
				Observation observation = (Observation) entry.getResource();
				if (usedObservations.contains(resourceKey(observation))) {
					continue;
				}
				if (!isLabObservation(observation)) {
					continue;
				}
				if (observation.hasEncounter()) {
					continue;
				}
				orphanLabs.add(observationRow(observation));
			}
		}
		if (ShrRecordTypeSelection.includes(request, ShrPullRecordType.SERVICE_REQUEST)) {
			for (Resource resource : index.allResources()) {
				if (!(resource instanceof ServiceRequest)) {
					continue;
				}
				ServiceRequest serviceRequest = (ServiceRequest) resource;
				if (usedServiceRequests.contains(resourceKey(serviceRequest))) {
					continue;
				}
				if (!isLabOrder(serviceRequest) || serviceRequest.hasEncounter()) {
					continue;
				}
				orphanLabs.add(labOrderRow(serviceRequest, index));
				markUsed(usedServiceRequests, serviceRequest);
			}
		}
		if (orphanLabs.isEmpty()) {
			return;
		}
		Map<String, Object> card = new LinkedHashMap<String, Object>();
		card.put("encounterId", null);
		card.put("encounterReference", null);
		card.put("visitDateTime", orphanVisitDateTime(orphanLabs));
		card.put("facility", Collections.emptyMap());
		card.put("clinician", Collections.emptyMap());
		card.put("diagnoses", Collections.emptyList());
		card.put("prescriptions", Collections.emptyList());
		card.put("vitals", Collections.emptyList());
		card.put("labs", orphanLabs);
		card.put("familyHistory", Collections.emptyList());
		card.put("documents", Collections.emptyList());
		card.put("source", Collections.emptyMap());
		card.put("orphanLabVisit", Boolean.TRUE);
		timeline.add(card);
	}
	
	private static String orphanVisitDateTime(List<Map<String, Object>> orphanLabs) {
		for (Map<String, Object> lab : orphanLabs) {
			if (lab.get("observedAt") instanceof String) {
				return (String) lab.get("observedAt");
			}
			if (lab.get("orderedAt") instanceof String) {
				return (String) lab.get("orderedAt");
			}
		}
		return null;
	}
	
	private static void appendFamilyHistoryCard(Bundle bundle, ShrFhirBundleIndex index,
	        Set<String> usedFamilyMemberHistories, List<Map<String, Object>> timeline) {
		List<Map<String, Object>> familyHistory = resolveFamilyHistories(index, usedFamilyMemberHistories);
		if (familyHistory.isEmpty()) {
			return;
		}
		Map<String, Object> card = new LinkedHashMap<String, Object>();
		card.put("encounterId", null);
		card.put("encounterReference", null);
		card.put("visitDateTime", familyHistoryVisitDateTime(familyHistory));
		card.put("facility", Collections.emptyMap());
		card.put("clinician", Collections.emptyMap());
		card.put("diagnoses", Collections.emptyList());
		card.put("prescriptions", Collections.emptyList());
		card.put("vitals", Collections.emptyList());
		card.put("labs", Collections.emptyList());
		card.put("familyHistory", familyHistory);
		card.put("documents", Collections.emptyList());
		card.put("source", Collections.emptyMap());
		card.put("familyHistoryVisit", Boolean.TRUE);
		timeline.add(card);
	}
	
	private static String familyHistoryVisitDateTime(List<Map<String, Object>> familyHistory) {
		for (Map<String, Object> row : familyHistory) {
			if (row.get("recordedAt") instanceof String) {
				return (String) row.get("recordedAt");
			}
		}
		return null;
	}
	
	private static Provenance findProvenanceForTarget(ShrFhirBundleIndex index, String targetKey) {
		for (Resource resource : index.allResources()) {
			if (!(resource instanceof Provenance)) {
				continue;
			}
			Provenance provenance = (Provenance) resource;
			for (Reference target : provenance.getTarget()) {
				if (ShrFhirBundleIndex.referencesSame(ShrFhirBundleIndex.referenceKey(target), targetKey)) {
					return provenance;
				}
			}
		}
		return null;
	}
	
	private static boolean isAuthorAgent(ProvenanceAgentComponent agent) {
		if (!agent.hasType() || !agent.getType().hasCoding()) {
			return true;
		}
		for (Coding coding : agent.getType().getCoding()) {
			if ("author".equalsIgnoreCase(coding.getCode())) {
				return true;
			}
		}
		return false;
	}
	
	private static String displayFromReference(Reference reference, ShrFhirBundleIndex index) {
		if (reference == null) {
			return null;
		}
		if (reference.hasDisplay()) {
			return reference.getDisplay();
		}
		Practitioner practitioner = index.resolve(reference, Practitioner.class);
		if (practitioner != null && practitioner.hasName()) {
			return practitioner.getNameFirstRep().getNameAsSingleString();
		}
		Organization organization = index.resolve(reference, Organization.class);
		if (organization != null && organization.hasName()) {
			return organization.getName();
		}
		return ShrFhirBundleIndex.referenceKey(reference);
	}
	
	private static Map<String, Object> observationRow(Observation observation) {
		Map<String, Object> row = new LinkedHashMap<String, Object>();
		row.put("kind", "result");
		row.put("resourceType", "Observation");
		row.put("observationId", observation.getIdElement().getIdPart());
		row.put("label", observationLabel(observation));
		row.put("value", formatObservationValue(observation));
		row.put("unit", observationUnit(observation));
		row.put("category", observationCategory(observation));
		row.put("observedAt", formatInstant(observation.hasEffectiveDateTimeType() ? observation.getEffectiveDateTimeType()
		        .getValue() : null));
		Coding coding = primaryCoding(observation.getCode());
		if (coding != null) {
			row.put("code", coding.getCode());
			row.put("system", coding.getSystem());
		}
		return row;
	}
	
	private static String observationLabel(Observation observation) {
		if (observation.hasCode()) {
			if (observation.getCode().hasText()) {
				return observation.getCode().getText();
			}
			Coding coding = primaryCoding(observation.getCode());
			if (coding != null) {
				return StringUtils.defaultIfBlank(coding.getDisplay(), coding.getCode());
			}
		}
		return "Observation";
	}
	
	private static String formatObservationValue(Observation observation) {
		if (observation.hasValueQuantity()) {
			return observation.getValueQuantity().getValue() != null ? observation.getValueQuantity().getValue()
			        .toPlainString() : null;
		}
		if (observation.hasValueStringType()) {
			return observation.getValueStringType().getValue();
		}
		if (observation.hasValueCodeableConcept()) {
			return codeableConceptText(observation.getValueCodeableConcept());
		}
		if (observation.hasValueBooleanType()) {
			return String.valueOf(observation.getValueBooleanType().getValue());
		}
		if (observation.hasValueIntegerType()) {
			return observation.getValueIntegerType().getValue() != null ? observation.getValueIntegerType().getValue()
			        .toString() : null;
		}
		if (observation.hasComponent()) {
			StringBuilder components = new StringBuilder();
			for (ObservationComponentComponent component : observation.getComponent()) {
				if (component == null) {
					continue;
				}
				String componentValue = formatComponentValue(component);
				if (StringUtils.isBlank(componentValue)) {
					continue;
				}
				if (components.length() > 0) {
					components.append(", ");
				}
				String label = component.hasCode() && component.getCode().hasText() ? component.getCode().getText()
				        : "component";
				components.append(label).append(" ").append(componentValue);
			}
			return components.length() > 0 ? components.toString() : null;
		}
		return null;
	}
	
	private static String formatComponentValue(ObservationComponentComponent component) {
		if (component.hasValueQuantity()) {
			Quantity quantity = component.getValueQuantity();
			return quantity.getValue() != null ? quantity.getValue().toPlainString()
			        + StringUtils.defaultString(quantity.getUnit(), "") : null;
		}
		if (component.hasValueStringType()) {
			return component.getValueStringType().getValue();
		}
		if (component.hasValueCodeableConcept()) {
			return codeableConceptText(component.getValueCodeableConcept());
		}
		if (component.hasValueBooleanType()) {
			return String.valueOf(component.getValueBooleanType().getValue());
		}
		if (component.hasValueIntegerType()) {
			return component.getValueIntegerType().getValue() != null ? component.getValueIntegerType().getValue()
			        .toString() : null;
		}
		return null;
	}
	
	private static String codeableConceptText(CodeableConcept concept) {
		if (concept == null) {
			return null;
		}
		if (concept.hasText()) {
			return concept.getText();
		}
		Coding coding = primaryCoding(concept);
		if (coding != null) {
			return StringUtils.defaultIfBlank(coding.getDisplay(), coding.getCode());
		}
		return null;
	}
	
	private static String observationUnit(Observation observation) {
		if (observation.hasValueQuantity() && observation.getValueQuantity().hasUnit()) {
			return observation.getValueQuantity().getUnit();
		}
		return null;
	}
	
	private static String observationCategory(Observation observation) {
		if (!observation.hasCategory()) {
			return null;
		}
		for (CodeableConcept category : observation.getCategory()) {
			if (category == null || !category.hasCoding()) {
				continue;
			}
			for (Coding coding : category.getCoding()) {
				if (StringUtils.isNotBlank(coding.getCode())) {
					return coding.getCode();
				}
			}
		}
		return null;
	}
	
	private static String conditionText(Condition condition) {
		if (condition.hasCode() && condition.getCode().hasText()) {
			return condition.getCode().getText();
		}
		Coding coding = primaryCoding(condition.getCode());
		if (coding != null) {
			return StringUtils.defaultIfBlank(coding.getDisplay(), coding.getCode());
		}
		return null;
	}
	
	private static String codeValue(CodeableConcept concept) {
		if (concept == null || !concept.hasCoding()) {
			return null;
		}
		return concept.getCodingFirstRep().getCode();
	}
	
	private static Coding primaryCoding(CodeableConcept concept) {
		if (concept == null || !concept.hasCoding()) {
			return null;
		}
		return concept.getCodingFirstRep();
	}
	
	private static String documentTitle(DocumentReference documentReference) {
		if (documentReference.hasDescription()) {
			return documentReference.getDescription();
		}
		if (documentReference.hasContent() && documentReference.getContentFirstRep().hasAttachment()) {
			if (documentReference.getContentFirstRep().getAttachment().hasTitle()) {
				return documentReference.getContentFirstRep().getAttachment().getTitle();
			}
		}
		return "Document";
	}
	
	private static boolean documentReferencesEncounter(DocumentReference documentReference, String encounterKey) {
		if (!documentReference.hasContext() || !documentReference.getContext().hasEncounter()) {
			return false;
		}
		for (Reference encounterRef : documentReference.getContext().getEncounter()) {
			if (matchesEncounter(encounterRef, encounterKey)) {
				return true;
			}
		}
		return false;
	}
	
	private static boolean matchesEncounter(Reference encounterReference, String encounterKey) {
		return ShrFhirBundleIndex.referencesSame(ShrFhirBundleIndex.referenceKey(encounterReference), encounterKey);
	}
	
	private static String encounterCanonicalKey(Encounter encounter) {
		return "Encounter/" + encounter.getIdElement().getIdPart();
	}
	
	private static String conditionCanonicalKey(Condition condition) {
		String id = condition.getIdElement().getIdPart();
		return id == null ? null : "Condition/" + id;
	}
	
	private static String resourceKey(Resource resource) {
		return resource.fhirType() + "/" + resource.getIdElement().getIdPart();
	}
	
	private static void markUsed(Set<String> used, Resource resource) {
		used.add(resourceKey(resource));
	}
	
	private static String formatInstant(Date date) {
		if (date == null) {
			return null;
		}
		return ISO_INSTANT.format(Instant.ofEpochMilli(date.getTime()).atOffset(ZoneOffset.UTC));
	}
	
	private static Date parseInstant(String value) {
		if (StringUtils.isBlank(value)) {
			return null;
		}
		try {
			return Date.from(Instant.parse(value));
		}
		catch (Exception ex) {
			return null;
		}
	}
	
	public static final class ShrTimelineBuildResult {
		
		private final List<Map<String, Object>> timeline;
		
		private final List<String> warnings;
		
		public ShrTimelineBuildResult(List<Map<String, Object>> timeline, List<String> warnings) {
			this.timeline = timeline;
			this.warnings = warnings;
		}
		
		public List<Map<String, Object>> getTimeline() {
			return timeline;
		}
		
		public List<String> getWarnings() {
			return warnings;
		}
	}
}
