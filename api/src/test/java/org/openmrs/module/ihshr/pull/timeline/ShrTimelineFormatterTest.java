package org.openmrs.module.ihshr.pull.timeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.EncounterStatus;
import org.hl7.fhir.r4.model.FamilyMemberHistory;
import org.hl7.fhir.r4.model.FamilyMemberHistory.FamilyHistoryStatus;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationRequest.MedicationRequestDispenseRequestComponent;
import org.hl7.fhir.r4.model.MedicationRequest.MedicationRequestStatus;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.ServiceRequest.ServiceRequestStatus;
import org.hl7.fhir.r4.model.Timing;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.junit.Test;
import org.openmrs.module.ihshr.pull.ShrHistoryRequest;
import org.openmrs.module.ihshr.pull.ShrPullException;
import org.openmrs.module.ihshr.pull.ShrPullFormat;
import org.openmrs.module.ihshr.pull.ShrPullResult;
import org.openmrs.module.ihshr.utils.DiagnosisConstants;
import org.openmrs.module.ihshr.utils.ReferralConstants;

@SuppressWarnings("unchecked")
public class ShrTimelineFormatterTest {
	
	@Test
	public void format_buildsTimelineCardsFromBundle() {
		Bundle bundle = sampleBundle();
		ShrPullResult result = new ShrPullResult();
		result.setRequest(ShrHistoryRequest.fromQueryParams(params("view", "default", "format", "timeline")));
		result.setMerged(bundle);
		result.setSourceUri("https://intelehealth.org/openmrs/installation-test");
		
		Map<String, Object> response = ShrTimelineFormatter.format(result);
		
		assertEquals("timeline", ((Map<String, Object>) response.get("meta")).get("format"));
		assertEquals("1.0", ((Map<String, Object>) response.get("meta")).get("schemaVersion"));
		List<Map<String, Object>> timeline = (List<Map<String, Object>>) response.get("timeline");
		assertEquals(1, timeline.size());
		
		Map<String, Object> card = timeline.get(0);
		assertEquals("enc-001", card.get("encounterId"));
		assertEquals("Sevagram Hospital", ((Map<String, Object>) card.get("facility")).get("name"));
		assertEquals("Dr Patel", ((Map<String, Object>) card.get("clinician")).get("name"));
		
		List<Map<String, Object>> diagnoses = (List<Map<String, Object>>) card.get("diagnoses");
		assertEquals(1, diagnoses.size());
		assertEquals("Type 2 Diabetes Mellitus", diagnoses.get(0).get("text"));
		assertEquals("confirmed", diagnoses.get(0).get("verificationStatus"));
		
		List<Map<String, Object>> prescriptions = (List<Map<String, Object>>) card.get("prescriptions");
		assertEquals(1, prescriptions.size());
		assertTrue(prescriptions.get(0).get("text").toString().contains("Metformin"));
		assertEquals("active", prescriptions.get(0).get("status"));
		
		List<Map<String, Object>> vitals = (List<Map<String, Object>>) card.get("vitals");
		assertEquals(1, vitals.size());
		assertEquals("140", vitals.get(0).get("value"));
		assertEquals("mmHg", vitals.get(0).get("unit"));
		
		List<Map<String, Object>> labs = (List<Map<String, Object>>) card.get("labs");
		assertEquals(3, labs.size());
		assertEquals("8.2", labValue(labs, "HbA1c"));
		assertEquals("%", labUnit(labs, "HbA1c"));
		assertEquals("4548-4", labCode(labs, "HbA1c"));
		assertEquals("145.0", labValue(labs, "Fasting glucose"));
		assertEquals("mg/dL", labUnit(labs, "Fasting glucose"));
		assertEquals("145.0", labValue(labs, "LDL cholesterol"));
		
		List<Map<String, Object>> documents = (List<Map<String, Object>>) card.get("documents");
		assertEquals(1, documents.size());
		assertEquals("Discharge note (PDF)", documents.get(0).get("title"));
		assertEquals("ws/rest/v1/ihshr/shr/binary/doc-001", documents.get(0).get("binaryUrl"));
	}
	
	@Test(expected = ShrPullException.class)
	public void format_rejectsNonDefaultViews() {
		ShrPullResult result = new ShrPullResult();
		result.setRequest(ShrHistoryRequest.fromQueryParams(params("view", "problems", "format", "timeline")));
		result.setMerged(new Bundle());
		ShrTimelineFormatter.format(result);
	}
	
	@Test
	public void pullFormat_parsesTimeline() {
		assertEquals(ShrPullFormat.TIMELINE, ShrPullFormat.parse("timeline"));
	}
	
	@Test
	public void format_customRecordTypesLimitTimelineSections() {
		Bundle bundle = sampleBundleWithCustomRecordTypeFilter();
		ShrPullResult result = new ShrPullResult();
		result.setRequest(ShrHistoryRequest.fromQueryParams(params("view", "custom", "format", "timeline", "recordTypes",
		    "Encounter,Observation")));
		result.setMerged(bundle);
		
		Map<String, Object> response = ShrTimelineFormatter.format(result);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> timeline = (List<Map<String, Object>>) response.get("timeline");
		Map<String, Object> card = timeline.get(0);
		
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> vitals = (List<Map<String, Object>>) card.get("vitals");
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> diagnoses = (List<Map<String, Object>>) card.get("diagnoses");
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> prescriptions = (List<Map<String, Object>>) card.get("prescriptions");
		assertEquals(1, vitals.size());
		assertTrue(diagnoses.isEmpty());
		assertTrue(prescriptions.isEmpty());
	}
	
	@Test
	public void format_mapsLabOrdersFromServiceRequestWithoutIdentifier() {
		Bundle bundle = sampleBundleWithLabOrder();
		ShrPullResult result = new ShrPullResult();
		result.setRequest(ShrHistoryRequest.fromQueryParams(params("view", "default", "format", "timeline")));
		result.setMerged(bundle);
		
		Map<String, Object> response = ShrTimelineFormatter.format(result);
		List<Map<String, Object>> timeline = (List<Map<String, Object>>) response.get("timeline");
		Map<String, Object> card = timeline.get(0);
		List<Map<String, Object>> labs = (List<Map<String, Object>>) card.get("labs");
		
		Map<String, Object> labOrder = findLabByLabel(labs, "CBC");
		assertNotNull(labOrder);
		assertEquals("order", labOrder.get("kind"));
		assertEquals("ServiceRequest", labOrder.get("resourceType"));
		assertEquals("active", labOrder.get("status"));
		assertEquals("CBC", labOrder.get("text").toString().split(" — ")[0]);
	}
	
	@Test
	public void format_excludesReferralServiceRequestFromLabs() {
		Bundle bundle = sampleBundleWithReferralServiceRequest();
		ShrPullResult result = new ShrPullResult();
		result.setRequest(ShrHistoryRequest.fromQueryParams(params("view", "default", "format", "timeline")));
		result.setMerged(bundle);
		
		Map<String, Object> response = ShrTimelineFormatter.format(result);
		List<Map<String, Object>> timeline = (List<Map<String, Object>>) response.get("timeline");
		List<Map<String, Object>> labs = (List<Map<String, Object>>) timeline.get(0).get("labs");
		assertTrue(labs.isEmpty());
	}
	
	@Test
	public void format_buildsFamilyHistoryTimelineCard() {
		Bundle bundle = sampleBundleWithFamilyHistory();
		ShrPullResult result = new ShrPullResult();
		result.setRequest(ShrHistoryRequest.fromQueryParams(params("view", "default", "format", "timeline")));
		result.setMerged(bundle);
		
		Map<String, Object> response = ShrTimelineFormatter.format(result);
		List<Map<String, Object>> timeline = (List<Map<String, Object>>) response.get("timeline");
		assertEquals(2, timeline.size());
		
		Map<String, Object> encounterCard = timeline.get(0);
		assertTrue(((List<?>) encounterCard.get("familyHistory")).isEmpty());
		
		Map<String, Object> familyCard = timeline.get(1);
		assertEquals(Boolean.TRUE, familyCard.get("familyHistoryVisit"));
		List<Map<String, Object>> familyHistory = (List<Map<String, Object>>) familyCard.get("familyHistory");
		assertEquals(1, familyHistory.size());
		assertEquals("Brother", familyHistory.get(0).get("label"));
		assertEquals("High BP, Diabetes", familyHistory.get(0).get("value"));
	}
	
	@Test
	public void format_usesOriginMetaTagAsFacilityNameFallback() {
		Bundle bundle = sampleBundleWithOriginFacilityTag();
		ShrPullResult result = new ShrPullResult();
		result.setRequest(ShrHistoryRequest.fromQueryParams(params("view", "default", "format", "timeline")));
		result.setMerged(bundle);
		
		Map<String, Object> response = ShrTimelineFormatter.format(result);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> timeline = (List<Map<String, Object>>) response.get("timeline");
		Map<String, Object> card = timeline.get(0);
		
		@SuppressWarnings("unchecked")
		Map<String, Object> facility = (Map<String, Object>) card.get("facility");
		assertEquals("intelehealth", facility.get("name"));
	}
	
	@Test
	public void format_parsesStructuredPrescriptionFields() {
		Bundle bundle = sampleBundleWithStructuredPrescription();
		ShrPullResult result = new ShrPullResult();
		result.setRequest(ShrHistoryRequest.fromQueryParams(params("view", "default", "format", "timeline")));
		result.setMerged(bundle);
		
		Map<String, Object> response = ShrTimelineFormatter.format(result);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> timeline = (List<Map<String, Object>>) response.get("timeline");
		Map<String, Object> card = timeline.get(0);
		
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> prescriptions = (List<Map<String, Object>>) card.get("prescriptions");
		assertEquals(1, prescriptions.size());
		Map<String, Object> rx = prescriptions.get(0);
		
		@SuppressWarnings("unchecked")
		Map<String, Object> medication = (Map<String, Object>) rx.get("medication");
		assertEquals("Atorvastatin 20mg", medication.get("display"));
		assertEquals("Medication/med-atorvastatin", medication.get("reference"));
		assertEquals("0 - 0 - 1", rx.get("strength"));
		assertEquals("Once daily", rx.get("frequency"));
		assertEquals("Test 2", rx.get("instructions"));
		assertEquals("75 mg", rx.get("unit"));
		assertEquals(14.0, rx.get("duration"));
		assertTrue(rx.get("text").toString().contains("Atorvastatin 20mg"));
	}
	
	@Test
	public void format_includesDiagnosisRankFromEncounter() {
		Bundle bundle = sampleBundleWithDiagnosisRanks();
		ShrPullResult result = new ShrPullResult();
		result.setRequest(ShrHistoryRequest.fromQueryParams(params("view", "default", "format", "timeline")));
		result.setMerged(bundle);
		
		Map<String, Object> response = ShrTimelineFormatter.format(result);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> timeline = (List<Map<String, Object>>) response.get("timeline");
		Map<String, Object> card = timeline.get(0);
		
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> diagnoses = (List<Map<String, Object>>) card.get("diagnoses");
		assertEquals(2, diagnoses.size());
		assertEquals(1, diagnoses.get(0).get("rank"));
		assertEquals("Primary diagnosis", diagnoses.get(0).get("text"));
		assertEquals(2, diagnoses.get(1).get("rank"));
		assertEquals("Secondary diagnosis", diagnoses.get(1).get("text"));
	}
	
	@Test
	public void format_includesDiagnosisNoteTextWhenPresent() {
		Bundle bundle = sampleBundleWithDiagnosisNote();
		ShrPullResult result = new ShrPullResult();
		result.setRequest(ShrHistoryRequest.fromQueryParams(params("view", "default", "format", "timeline")));
		result.setMerged(bundle);
		
		Map<String, Object> response = ShrTimelineFormatter.format(result);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> timeline = (List<Map<String, Object>>) response.get("timeline");
		Map<String, Object> card = timeline.get(0);
		
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> diagnoses = (List<Map<String, Object>>) card.get("diagnoses");
		assertEquals(1, diagnoses.size());
		@SuppressWarnings("unchecked")
		Map<String, Object> note = (Map<String, Object>) diagnoses.get(0).get("note");
		assertNotNull(note);
		assertEquals("Duration 7 days, night fever", note.get("text"));
	}
	
	@Test
	public void format_includesCodedObservationValueForVitals() {
		Bundle bundle = sampleBundleWithCodedVital();
		ShrPullResult result = new ShrPullResult();
		result.setRequest(ShrHistoryRequest.fromQueryParams(params("view", "default", "format", "timeline")));
		result.setMerged(bundle);
		
		Map<String, Object> response = ShrTimelineFormatter.format(result);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> timeline = (List<Map<String, Object>>) response.get("timeline");
		Map<String, Object> card = timeline.get(0);
		
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> vitals = (List<Map<String, Object>>) card.get("vitals");
		assertEquals(1, vitals.size());
		assertEquals("B POSITIVE", vitals.get(0).get("value"));
		assertEquals("BLOOD TYPING", vitals.get(0).get("label"));
	}
	
	@Test
	public void format_treatsUnidentifiedExamObservationsAsVitals() {
		Bundle bundle = sampleBundleWithExamVitals();
		ShrPullResult result = new ShrPullResult();
		result.setRequest(ShrHistoryRequest.fromQueryParams(params("view", "default", "format", "timeline")));
		result.setMerged(bundle);
		
		Map<String, Object> response = ShrTimelineFormatter.format(result);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> timeline = (List<Map<String, Object>>) response.get("timeline");
		Map<String, Object> card = timeline.get(0);
		
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> vitals = (List<Map<String, Object>>) card.get("vitals");
		assertEquals(2, vitals.size());
		assertEquals("72.0", vitalValue(vitals, "Pulse"));
		assertEquals("160.0", vitalValue(vitals, "Height (cm)"));
	}
	
	@Test
	public void format_excludesIdentifiedPhysicalExamFromVitals() {
		Bundle bundle = sampleBundleWithIdentifiedPhysicalExam();
		ShrPullResult result = new ShrPullResult();
		result.setRequest(ShrHistoryRequest.fromQueryParams(params("view", "default", "format", "timeline")));
		result.setMerged(bundle);
		
		Map<String, Object> response = ShrTimelineFormatter.format(result);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> timeline = (List<Map<String, Object>>) response.get("timeline");
		Map<String, Object> card = timeline.get(0);
		
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> vitals = (List<Map<String, Object>>) card.get("vitals");
		assertTrue(vitals.isEmpty());
	}
	
	@Test
	public void format_excludesDoctorDetailsFromLabsAndParsesJson() {
		Bundle bundle = sampleBundleWithDoctorDetails();
		ShrPullResult result = new ShrPullResult();
		result.setRequest(ShrHistoryRequest.fromQueryParams(params("view", "default", "format", "timeline")));
		result.setMerged(bundle);
		
		Map<String, Object> response = ShrTimelineFormatter.format(result);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> timeline = (List<Map<String, Object>>) response.get("timeline");
		Map<String, Object> card = timeline.get(0);
		
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> labs = (List<Map<String, Object>>) card.get("labs");
		assertTrue(labs.isEmpty());
		
		@SuppressWarnings("unchecked")
		Map<String, Object> doctorDetails = (Map<String, Object>) card.get("doctorDetails");
		assertNotNull(doctorDetails);
		assertEquals("rohith M s", doctorDetails.get("name"));
		assertEquals("General Physician", doctorDetails.get("specialization"));
		
		@SuppressWarnings("unchecked")
		Map<String, Object> clinician = (Map<String, Object>) card.get("clinician");
		assertEquals("General Physician", clinician.get("specialization"));
	}
	
	private static Bundle sampleBundleWithCustomRecordTypeFilter() {
		Encounter encounter = new Encounter();
		encounter.setId("enc-filter");
		encounter.setStatus(EncounterStatus.FINISHED);
		encounter.setPeriod(new Period().setStart(new Date()));
		
		Observation pulse = examVitalObservation("obs-filter-pulse", "Pulse", 72.0, "/min");
		pulse.setEncounter(new Reference("Encounter/enc-filter"));
		
		Condition condition = new Condition();
		condition.setId("cond-filter");
		condition.setEncounter(new Reference("Encounter/enc-filter"));
		condition.addCategory(new CodeableConcept().addCoding(new Coding().setSystem(
		    DiagnosisConstants.CONDITION_CATEGORY_SYSTEM).setCode(DiagnosisConstants.CONDITION_CATEGORY_CODE)));
		condition.getCode().setText("Fever");
		
		MedicationRequest medicationRequest = new MedicationRequest();
		medicationRequest.setId("rx-filter");
		medicationRequest.setStatus(MedicationRequestStatus.ACTIVE);
		medicationRequest.setEncounter(new Reference("Encounter/enc-filter"));
		medicationRequest.getMedicationCodeableConcept().setText("Metformin");
		
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.SEARCHSET);
		bundle.addEntry().setResource(encounter);
		bundle.addEntry().setResource(pulse);
		bundle.addEntry().setResource(condition);
		bundle.addEntry().setResource(medicationRequest);
		return bundle;
	}
	
	private static Bundle sampleBundleWithLabOrder() {
		Encounter encounter = new Encounter();
		encounter.setId("enc-lab-order");
		encounter.setStatus(EncounterStatus.FINISHED);
		encounter.setPeriod(new Period().setStart(new Date()));
		
		ServiceRequest labOrder = new ServiceRequest();
		labOrder.setId("sr-cbc");
		labOrder.setStatus(ServiceRequestStatus.ACTIVE);
		labOrder.setIntent(org.hl7.fhir.r4.model.ServiceRequest.ServiceRequestIntent.ORDER);
		labOrder.setEncounter(new Reference("Encounter/enc-lab-order"));
		labOrder.getCode().setText("CBC");
		labOrder.setAuthoredOn(new Date());
		
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.SEARCHSET);
		bundle.addEntry().setResource(encounter);
		bundle.addEntry().setResource(labOrder);
		return bundle;
	}
	
	private static Bundle sampleBundleWithReferralServiceRequest() {
		Encounter encounter = new Encounter();
		encounter.setId("enc-referral");
		encounter.setStatus(EncounterStatus.FINISHED);
		encounter.setPeriod(new Period().setStart(new Date()));
		
		ServiceRequest referral = new ServiceRequest();
		referral.setId("sr-referral");
		referral.setStatus(ServiceRequestStatus.ACTIVE);
		referral.setIntent(org.hl7.fhir.r4.model.ServiceRequest.ServiceRequestIntent.ORDER);
		referral.setEncounter(new Reference("Encounter/enc-referral"));
		referral.getCode().setText("Cardiology");
		referral.addIdentifier(new Identifier().setSystem(ReferralConstants.IDENTIFIER_SYSTEM).setValue("obs-1::ref-0"));
		referral.addCategory(new CodeableConcept().addCoding(new Coding().setSystem(
		    ReferralConstants.SERVICE_REQUEST_TYPE_SYSTEM).setCode(ReferralConstants.SERVICE_REQUEST_TYPE_REFERRAL)));
		
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.SEARCHSET);
		bundle.addEntry().setResource(encounter);
		bundle.addEntry().setResource(referral);
		return bundle;
	}
	
	private static Bundle sampleBundleWithFamilyHistory() {
		Encounter encounter = new Encounter();
		encounter.setId("enc-fmh");
		encounter.setStatus(EncounterStatus.FINISHED);
		encounter.setPeriod(new Period().setStart(new Date()));
		
		FamilyMemberHistory history = new FamilyMemberHistory();
		history.setId("fmh-brother");
		history.setStatus(FamilyHistoryStatus.COMPLETED);
		history.setDate(new Date());
		history.getRelationship().setText("Brother");
		history.addCondition().getCode().setText("High BP");
		history.addCondition().getCode().setText("Diabetes");
		
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.SEARCHSET);
		bundle.addEntry().setResource(encounter);
		bundle.addEntry().setResource(history);
		return bundle;
	}
	
	private static Bundle sampleBundleWithOriginFacilityTag() {
		Encounter encounter = new Encounter();
		encounter.setId("enc-origin-facility");
		encounter.setStatus(EncounterStatus.FINISHED);
		encounter.setPeriod(new Period().setStart(new Date()));
		encounter.getMeta().addTag(
		    new Coding().setSystem("https://intelehealth.org/origin").setCode("intelehealth")
		            .setDisplay("Originated from Intelehealth"));
		
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.SEARCHSET);
		bundle.addEntry().setResource(encounter);
		return bundle;
	}
	
	private static Bundle sampleBundleWithStructuredPrescription() {
		Encounter encounter = new Encounter();
		encounter.setId("enc-rx");
		encounter.setStatus(EncounterStatus.FINISHED);
		encounter.setPeriod(new Period().setStart(new Date()));
		
		MedicationRequest medicationRequest = new MedicationRequest();
		medicationRequest.setId("rx-structured");
		medicationRequest.setStatus(MedicationRequestStatus.ACTIVE);
		medicationRequest.setEncounter(new Reference("Encounter/enc-rx"));
		medicationRequest.setMedication(new Reference("Medication/med-atorvastatin").setDisplay("Atorvastatin 20mg"));
		medicationRequest.addDosageInstruction().setText("162376AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA|0 - 0 - 1|Once daily|Test 2")
		        .setTiming(new Timing().setRepeat(new Timing.TimingRepeatComponent().setDuration(14)));
		MedicationRequestDispenseRequestComponent dispenseRequest = new MedicationRequestDispenseRequestComponent();
		dispenseRequest.setQuantity(new Quantity().setValue(0).setUnit("75 mg"));
		medicationRequest.setDispenseRequest(dispenseRequest);
		
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.SEARCHSET);
		bundle.addEntry().setResource(encounter);
		bundle.addEntry().setResource(medicationRequest);
		return bundle;
	}
	
	private static Bundle sampleBundleWithDiagnosisRanks() {
		Encounter encounter = new Encounter();
		encounter.setId("enc-dx-rank");
		encounter.setStatus(EncounterStatus.FINISHED);
		encounter.setPeriod(new Period().setStart(new Date()));
		encounter.addDiagnosis().setCondition(new Reference("Condition/cond-primary")).setRank(1);
		encounter.addDiagnosis().setCondition(new Reference("Condition/cond-secondary")).setRank(2);
		
		Condition primary = new Condition();
		primary.setId("cond-primary");
		primary.setEncounter(new Reference("Encounter/enc-dx-rank"));
		primary.addCategory(new CodeableConcept().addCoding(new Coding().setSystem(
		    DiagnosisConstants.CONDITION_CATEGORY_SYSTEM).setCode(DiagnosisConstants.CONDITION_CATEGORY_CODE)));
		primary.getCode().setText("Primary diagnosis");
		
		Condition secondary = new Condition();
		secondary.setId("cond-secondary");
		secondary.setEncounter(new Reference("Encounter/enc-dx-rank"));
		secondary.addCategory(new CodeableConcept().addCoding(new Coding().setSystem(
		    DiagnosisConstants.CONDITION_CATEGORY_SYSTEM).setCode(DiagnosisConstants.CONDITION_CATEGORY_CODE)));
		secondary.getCode().setText("Secondary diagnosis");
		
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.SEARCHSET);
		bundle.addEntry().setResource(encounter);
		bundle.addEntry().setResource(primary);
		bundle.addEntry().setResource(secondary);
		return bundle;
	}
	
	private static Bundle sampleBundleWithDiagnosisNote() {
		Encounter encounter = new Encounter();
		encounter.setId("enc-dx-note");
		encounter.setStatus(EncounterStatus.FINISHED);
		encounter.setPeriod(new Period().setStart(new Date()));
		encounter.addDiagnosis().setCondition(new Reference("Condition/cond-note"));
		
		Condition condition = new Condition();
		condition.setId("cond-note");
		condition.setEncounter(new Reference("Encounter/enc-dx-note"));
		condition.addCategory(new CodeableConcept().addCoding(new Coding().setSystem(
		    DiagnosisConstants.CONDITION_CATEGORY_SYSTEM).setCode(DiagnosisConstants.CONDITION_CATEGORY_CODE)));
		condition.getCode().setText("Fever");
		condition.addNote().setText("Duration 7 days, night fever");
		
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.SEARCHSET);
		bundle.addEntry().setResource(encounter);
		bundle.addEntry().setResource(condition);
		return bundle;
	}
	
	private static Bundle sampleBundleWithCodedVital() {
		Encounter encounter = new Encounter();
		encounter.setId("enc-coded-vital");
		encounter.setStatus(EncounterStatus.FINISHED);
		encounter.setPeriod(new Period().setStart(new Date()));
		
		Observation bloodTyping = new Observation();
		bloodTyping.setId("obs-blood-type");
		bloodTyping.setStatus(ObservationStatus.FINAL);
		bloodTyping.setEncounter(new Reference("Encounter/enc-coded-vital"));
		bloodTyping.addCategory(new CodeableConcept().addCoding(new Coding().setCode("laboratory")));
		bloodTyping.getCode().setText("BLOOD TYPING");
		bloodTyping.setValue(new CodeableConcept().setText("B POSITIVE").addCoding(
		    new Coding().setCode("9d2e9a1f-538f-11e6-9cfe-86f436325720").setDisplay("B POSITIVE")));
		
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.SEARCHSET);
		bundle.addEntry().setResource(encounter);
		bundle.addEntry().setResource(bloodTyping);
		return bundle;
	}
	
	private static Bundle sampleBundleWithExamVitals() {
		Encounter encounter = new Encounter();
		encounter.setId("enc-vitals");
		encounter.setStatus(EncounterStatus.FINISHED);
		encounter.setPeriod(new Period().setStart(new Date()));
		
		Observation pulse = examVitalObservation("obs-pulse", "Pulse", 72.0, "/min");
		Observation height = examVitalObservation("obs-height", "Height (cm)", 160.0, "cm");
		
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.SEARCHSET);
		bundle.addEntry().setResource(encounter);
		bundle.addEntry().setResource(pulse);
		bundle.addEntry().setResource(height);
		return bundle;
	}
	
	private static Bundle sampleBundleWithIdentifiedPhysicalExam() {
		Encounter encounter = new Encounter();
		encounter.setId("enc-pe");
		encounter.setStatus(EncounterStatus.FINISHED);
		encounter.setPeriod(new Period().setStart(new Date()));
		
		Observation physicalExam = new Observation();
		physicalExam.setId("obs-pe");
		physicalExam.setStatus(ObservationStatus.FINAL);
		physicalExam.setEncounter(new Reference("Encounter/enc-pe"));
		physicalExam.addIdentifier(new Identifier().setSystem("http://openmrs.org/physical-exam").setValue(
		    "obs-pe::cat-abdomen"));
		physicalExam.addCategory(new CodeableConcept().addCoding(new Coding().setCode("exam")));
		physicalExam.getCode().setText("Abdomen");
		physicalExam.setValue(new org.hl7.fhir.r4.model.StringType("None"));
		
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.SEARCHSET);
		bundle.addEntry().setResource(encounter);
		bundle.addEntry().setResource(physicalExam);
		return bundle;
	}
	
	private static Observation examVitalObservation(String id, String label, double value, String unit) {
		Observation observation = new Observation();
		observation.setId(id);
		observation.setStatus(ObservationStatus.FINAL);
		observation.setEncounter(new Reference("Encounter/enc-vitals"));
		observation.addCategory(new CodeableConcept().addCoding(new Coding().setCode("exam")));
		observation.getCode().setText(label);
		observation.setValue(new Quantity().setValue(value).setUnit(unit));
		return observation;
	}
	
	private static Bundle sampleBundleWithDoctorDetails() {
		Encounter encounter = new Encounter();
		encounter.setId("enc-doc");
		encounter.setStatus(EncounterStatus.FINISHED);
		encounter.setPeriod(new Period().setStart(new Date()));
		encounter.addParticipant().setIndividual(new Reference("Practitioner/pr-1"));
		
		Observation doctorDetails = new Observation();
		doctorDetails.setId("obs-doctor-details");
		doctorDetails.setStatus(ObservationStatus.FINAL);
		doctorDetails.setEncounter(new Reference("Encounter/enc-doc"));
		doctorDetails.addCategory(new CodeableConcept().addCoding(new Coding().setCode("laboratory")));
		doctorDetails.getCode().setText("Doctor details");
		doctorDetails.setValue(new org.hl7.fhir.r4.model.StringType(
		        "{\"name\":\"rohith M s\",\"specialization\":\"General Physician\",\"qualification\":\"MBBS, MD\"}"));
		
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.SEARCHSET);
		bundle.addEntry().setResource(encounter);
		bundle.addEntry().setResource(doctorDetails);
		return bundle;
	}
	
	private static Bundle sampleBundle() {
		Encounter encounter = new Encounter();
		encounter.setId("enc-001");
		encounter.setStatus(EncounterStatus.FINISHED);
		encounter.setPeriod(new Period().setStart(new Date()));
		encounter.addLocation().setLocation(new Reference("Location/loc-1"));
		encounter.addParticipant().setIndividual(new Reference("Practitioner/pr-1"));
		encounter.addDiagnosis().setCondition(new Reference("Condition/cond-001"));
		
		Location location = new Location();
		location.setId("loc-1");
		location.setName("Sevagram Hospital");
		
		Practitioner practitioner = new Practitioner();
		practitioner.setId("pr-1");
		practitioner.addName().setFamily("Patel").addGiven("Dr");
		
		Condition condition = new Condition();
		condition.setId("cond-001");
		condition.setEncounter(new Reference("Encounter/enc-001"));
		condition.addCategory(new CodeableConcept().addCoding(new Coding().setSystem(
		    DiagnosisConstants.CONDITION_CATEGORY_SYSTEM).setCode(DiagnosisConstants.CONDITION_CATEGORY_CODE)));
		condition.getCode().setText("Type 2 Diabetes Mellitus")
		        .addCoding(new Coding("http://snomed.info/sct", "44054006", "Type 2 Diabetes Mellitus"));
		condition.setVerificationStatus(new CodeableConcept().addCoding(new Coding().setSystem(
		    DiagnosisConstants.VERIFICATION_STATUS_SYSTEM).setCode("confirmed")));
		
		MedicationRequest medicationRequest = new MedicationRequest();
		medicationRequest.setId("rx-001");
		medicationRequest.setStatus(MedicationRequestStatus.ACTIVE);
		medicationRequest.setEncounter(new Reference("Encounter/enc-001"));
		medicationRequest.getMedicationCodeableConcept().setText("Metformin 500 mg");
		medicationRequest.addDosageInstruction().setText("BD × 30 days");
		
		Observation vital = new Observation();
		vital.setId("obs-bp-001");
		vital.setStatus(ObservationStatus.FINAL);
		vital.setEncounter(new Reference("Encounter/enc-001"));
		vital.addCategory(new CodeableConcept().addCoding(new Coding().setCode("vital-signs")));
		vital.getCode().addCoding(new Coding("http://loinc.org", "85354-9", "Blood pressure"));
		vital.setValue(new Quantity().setValue(140).setUnit("mmHg"));
		
		Observation hba1c = labObservation("obs-lab-hba1c", "4548-4", "HbA1c", 8.2, "%");
		Observation fastingGlucose = labObservation("obs-lab-glucose", "2339-0", "Fasting glucose", 145, "mg/dL");
		Observation ldl = labObservation("obs-lab-ldl", "13457-7", "LDL cholesterol", 145, "mg/dL");
		
		DocumentReference documentReference = new DocumentReference();
		documentReference.setId("doc-001");
		documentReference.getContext().addEncounter(new Reference("Encounter/enc-001"));
		documentReference.addContent().setAttachment(new Attachment().setTitle("Discharge note (PDF)"));
		
		Provenance provenance = new Provenance();
		provenance.setId("prov-001");
		provenance.addTarget(new Reference("Encounter/enc-001"));
		provenance.addAgent().setWho(new Reference("Practitioner/pr-1").setDisplay("Dr Patel"));
		provenance.getAgentFirstRep().setOnBehalfOf(new Reference("Organization/org-1").setDisplay("Sevagram Hospital"));
		
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.SEARCHSET);
		bundle.addEntry().setResource(encounter);
		bundle.addEntry().setResource(location);
		bundle.addEntry().setResource(practitioner);
		bundle.addEntry().setResource(condition);
		bundle.addEntry().setResource(medicationRequest);
		bundle.addEntry().setResource(vital);
		bundle.addEntry().setResource(hba1c);
		bundle.addEntry().setResource(fastingGlucose);
		bundle.addEntry().setResource(ldl);
		bundle.addEntry().setResource(documentReference);
		bundle.addEntry().setResource(provenance);
		return bundle;
	}
	
	private static Observation labObservation(String id, String loincCode, String label, double value, String unit) {
		Observation lab = new Observation();
		lab.setId(id);
		lab.setStatus(ObservationStatus.FINAL);
		lab.setEncounter(new Reference("Encounter/enc-001"));
		lab.addCategory(new CodeableConcept().addCoding(new Coding().setCode("laboratory")));
		lab.addIdentifier(new Identifier().setSystem("http://openmrs.org/lab").setValue(id));
		lab.getCode().addCoding(new Coding("http://loinc.org", loincCode, label));
		lab.setValue(new Quantity().setValue(value).setUnit(unit));
		return lab;
	}
	
	private static String vitalValue(List<Map<String, Object>> vitals, String label) {
		for (Map<String, Object> vital : vitals) {
			if (label.equals(vital.get("label"))) {
				return (String) vital.get("value");
			}
		}
		return null;
	}
	
	private static String labValue(List<Map<String, Object>> labs, String label) {
		for (Map<String, Object> lab : labs) {
			if (label.equals(lab.get("label"))) {
				return (String) lab.get("value");
			}
		}
		return null;
	}
	
	private static String labUnit(List<Map<String, Object>> labs, String label) {
		for (Map<String, Object> lab : labs) {
			if (label.equals(lab.get("label"))) {
				return (String) lab.get("unit");
			}
		}
		return null;
	}
	
	private static String labCode(List<Map<String, Object>> labs, String label) {
		for (Map<String, Object> lab : labs) {
			if (label.equals(lab.get("label"))) {
				return (String) lab.get("code");
			}
		}
		return null;
	}
	
	private static Map<String, Object> findLabByLabel(List<Map<String, Object>> labs, String label) {
		for (Map<String, Object> lab : labs) {
			if (label.equals(lab.get("label"))) {
				return lab;
			}
		}
		return null;
	}
	
	private static Map<String, String> params(String... kv) {
		Map<String, String> map = new java.util.LinkedHashMap<String, String>();
		for (int i = 0; i + 1 < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}
}
