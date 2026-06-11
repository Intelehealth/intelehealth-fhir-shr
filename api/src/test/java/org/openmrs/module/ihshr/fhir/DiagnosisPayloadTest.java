package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.EncounterStatus;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Test;
import org.openmrs.module.ihshr.utils.DiagnosisConstants;

import ca.uhn.fhir.context.FhirContext;

/**
 * Prints the final SHR diagnosis transaction bundle (doc §5.4, §6.4–6.5): Condition(s) first,
 * Encounter with {@code diagnosis[]} last. Run:
 * {@code mvn test -pl api -Dtest=DiagnosisPayloadTest#printDiagnosisTransactionBundle} From
 * {@code testdata/diagnosis.json}:
 * {@code mvn test -pl api -Dtest=DiagnosisPayloadTest#printDiagnosisTransactionBundleFromTestData}
 */
public class DiagnosisPayloadTest {
	
	private final FhirContext fhirContext = FhirContext.forR4();
	
	@Test
	public void printDiagnosisTransactionBundle() throws Exception {
		Observation source = sampleSourceObservation();
		DiagnosisTransfer transfer = new DiagnosisTransfer();
		
		// Doc §6.4 worked example + §6.5 multiple diagnoses on one visit
		DiagnosisBuildResult primary = transfer.build(source, "00b8c64d-f7f8-4eca-b6d4-772f4a4381ca",
		    "82272006::Acute rhinitis:Primary & Confirmed", 3);
		DiagnosisBuildResult secondary = transfer.build(source, "65355902-04d5-4c88-b1a6-975574ef21fb",
		    "J30.0::Acute rhinitis:Secondary & Provisional", 3);
		
		assertTrue(primary != null && primary.getCondition() != null);
		assertTrue(secondary != null && secondary.getCondition() != null);
		
		Bundle bundle = buildDiagnosisTransactionBundle(primary, secondary);
		String payload = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
		
		System.err.println();
		System.err.println("################################################################");
		System.err.println("# DIAGNOSIS (concept 163219) — transaction bundle");
		System.err.println("# Entry order: Condition(s) first, Encounter last (doc §5.4)");
		System.err.println("################################################################");
		System.err.println(payload);
		
		Path out = Paths.get("target/test-output/diagnosis-sample-bundle.json");
		Files.createDirectories(out.getParent());
		Files.write(out, payload.getBytes(StandardCharsets.UTF_8));
		System.err.println("Wrote sample bundle to: " + out.toAbsolutePath());
		
		assertEquals(Bundle.BundleType.TRANSACTION, bundle.getType());
		assertEquals(3, bundle.getEntry().size());
		assertEquals("Condition", bundle.getEntry().get(0).getResource().fhirType());
		assertEquals("Condition", bundle.getEntry().get(1).getResource().fhirType());
		assertEquals("Encounter", bundle.getEntry().get(2).getResource().fhirType());
	}
	
	@Test
	public void printDiagnosisTransactionBundleFromTestData() throws Exception {
		// encounter_id 3226 from diagnosis.json: one Primary + one Secondary
		String primaryUuid = "9ae20d52-edbd-483d-9381-eb894a27c000";
		String primaryValue = "NA::Rheumatic fever without cardiac involvement:Primary & Provisional";
		String secondaryUuid = "5efa0b1b-10ef-4d66-9110-fcb2d1fc7845";
		String secondaryValue = "NA::Syncope and collapse:Secondary & Confirmed";
		
		Observation source = sampleSourceObservation();
		source.getEncounter().setReference("Encounter/encounter-3226-from-testdata");
		
		DiagnosisTransfer transfer = new DiagnosisTransfer();
		DiagnosisBuildResult primary = transfer.build(source, primaryUuid, primaryValue, 3);
		DiagnosisBuildResult secondary = transfer.build(source, secondaryUuid, secondaryValue, 3);
		
		Bundle bundle = buildDiagnosisTransactionBundle("encounter-3226-from-testdata", primary, secondary);
		String payload = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
		
		System.err.println();
		System.err.println("################################################################");
		System.err.println("# DIAGNOSIS from testdata/diagnosis.json (encounter_id=3226)");
		System.err.println("# Primary obs: " + primaryUuid);
		System.err.println("# Secondary obs: " + secondaryUuid);
		System.err.println("################################################################");
		System.err.println(payload);
		
		Path out = Paths.get("target/test-output/diagnosis-testdata-bundle.json");
		Files.createDirectories(out.getParent());
		Files.write(out, payload.getBytes(StandardCharsets.UTF_8));
		System.err.println("Wrote sample bundle to: " + out.toAbsolutePath());
		
		assertEquals(3, bundle.getEntry().size());
	}
	
	private Bundle buildDiagnosisTransactionBundle(DiagnosisBuildResult... builtDiagnoses) {
		return buildDiagnosisTransactionBundle("test-encounter-uuid", builtDiagnoses);
	}
	
	private Bundle buildDiagnosisTransactionBundle(String encounterId, DiagnosisBuildResult... builtDiagnoses) {
		Bundle transactionBundle = new Bundle();
		transactionBundle.setType(Bundle.BundleType.TRANSACTION);
		
		List<Encounter.DiagnosisComponent> encounterDiagnosis = new ArrayList<Encounter.DiagnosisComponent>();
		
		for (DiagnosisBuildResult built : builtDiagnoses) {
			Condition condition = built.getCondition();
			ShrPushMetaApplicator.applyPushMeta(condition);
			
			String conditionId = condition.getIdentifierFirstRep().getValue();
			Bundle.BundleEntryComponent condEntry = transactionBundle.addEntry();
			condEntry.setResource(condition);
			condEntry.getRequest().setMethod(Bundle.HTTPVerb.PUT).setUrl("Condition/" + conditionId);
			
			Encounter.DiagnosisComponent dc = new Encounter.DiagnosisComponent();
			dc.setCondition(new Reference("Condition/" + conditionId));
			dc.setUse(new CodeableConcept().addCoding(new Coding().setSystem(DiagnosisConstants.DIAGNOSIS_ROLE_SYSTEM)
			        .setCode(DiagnosisConstants.DIAGNOSIS_ROLE_CODE)));
			dc.setRank(built.getRank());
			encounterDiagnosis.add(dc);
		}
		
		Encounter encounter = sampleEncounter(encounterId);
		encounter.setDiagnosis(encounterDiagnosis);
		ShrPushMetaApplicator.applyPushMeta(encounter);
		
		Bundle.BundleEntryComponent encEntry = transactionBundle.addEntry();
		encEntry.setResource(encounter);
		encEntry.getRequest().setMethod(Bundle.HTTPVerb.PUT).setUrl("Encounter/" + encounter.getIdElement().getIdPart());
		
		return transactionBundle;
	}
	
	private static Observation sampleSourceObservation() {
		Observation source = new Observation();
		source.setId("source-obs-id");
		source.getSubject().setReference("Patient/test-patient-uuid");
		source.getEncounter().setReference("Encounter/test-encounter-uuid");
		source.setEffective(new org.hl7.fhir.r4.model.DateTimeType(new Date()));
		source.addPerformer(new Reference("Practitioner/test-practitioner-uuid"));
		source.getCode().setText("Diagnosis");
		return source;
	}
	
	private static Encounter sampleEncounter(String encounterId) {
		Encounter encounter = new Encounter();
		encounter.setId(encounterId);
		encounter.setStatus(EncounterStatus.FINISHED);
		encounter.getSubject().setReference("Patient/test-patient-uuid");
		encounter.getClass_().setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode").setCode("AMB");
		encounter.setPeriod(new org.hl7.fhir.r4.model.Period().setStart(new Date()));
		return encounter;
	}
}
