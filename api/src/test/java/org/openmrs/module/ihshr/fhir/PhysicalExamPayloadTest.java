package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Test;
import org.openmrs.module.ihshr.domain.ParsedExamCategory;
import org.openmrs.module.ihshr.parser.PhysicalExamParser;
import org.openmrs.module.ihshr.parser.PhysicalExamParserTest;
import org.openmrs.module.ihshr.parser.PhysicalExamValueTexts;
import org.openmrs.module.ihshr.utils.PhysicalExamConstants;

import ca.uhn.fhir.context.FhirContext;

/**
 * Builds the same transaction bundle shape as {@code DataSendToSHR#sendSingleObservation} and
 * prints JSON payload.
 */
public class PhysicalExamPayloadTest {
	
	private static final String OBS_UUID = "920352ce-4e64-4b55-9dfe-883332b546c0";
	
	private final FhirContext fhirContext = FhirContext.forR4();
	
	@Test
	public void printTransactionBundlePayload_forSamplePhysicalExamValue() {
		String valueJson = PhysicalExamParserTest.SAMPLE_VALUE_JSON;
		String clinicalHtml = PhysicalExamValueTexts.extractClinicalHtml(valueJson);
		System.err.println("[PhysicalExam-Test] Using en HTML only, length=" + clinicalHtml.length());
		System.err.println("[PhysicalExam-Test] l-en ignored: " + !clinicalHtml.contains("Is there jaundice"));
		
		List<ParsedExamCategory> categories = new PhysicalExamParser().parse(valueJson);
		assertEquals(1, categories.size());
		
		String sharedNote = PhysicalExamObservationBuilder.stripHtmlForNote(valueJson);
		Observation source = sampleSourceObservation();
		PhysicalExamObservationBuilder builder = new PhysicalExamObservationBuilder();
		
		int index = 0;
		for (ParsedExamCategory category : categories) {
			index++;
			Observation categoryObs = builder.build(source, OBS_UUID, category, sharedNote);
			Bundle transactionBundle = toTransactionBundle(categoryObs);
			String payload = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(transactionBundle);
			
			System.err.println();
			System.err.println("========== Physical exam FHIR payload " + index + "/" + categories.size() + " ("
			        + category.getCategoryName() + ") ==========");
			System.err.println(payload);
			System.err.println("========== end payload ==========");
			System.err.println();
			
			assertEquals(OBS_UUID + "::cat-general-exams", categoryObs.getIdentifierFirstRep().getValue());
			assertEquals(6, categoryObs.getComponent().size());
			assertEquals(PhysicalExamConstants.EXAM_PROCEDURE_CODE, categoryObs.getCode().getCodingFirstRep().getCode());
		}
	}
	
	private static Bundle toTransactionBundle(Observation observation) {
		Bundle transactionBundle = new Bundle();
		transactionBundle.setType(Bundle.BundleType.TRANSACTION);
		Bundle.BundleEntryComponent component = transactionBundle.addEntry();
		component.setResource(observation);
		String resourceId = observation.getIdentifierFirstRep().getValue();
		component.getRequest().setUrl("Observation/" + resourceId).setMethod(Bundle.HTTPVerb.PUT);
		return transactionBundle;
	}
	
	@Test
	public void build_doesNotCopySourceValueString() {
		String valueJson = PhysicalExamParserTest.SAMPLE_VALUE_JSON;
		List<ParsedExamCategory> categories = new PhysicalExamParser().parse(valueJson);
		Observation source = sampleSourceObservation();
		source.setValue(new org.hl7.fhir.r4.model.StringType(valueJson));
		Observation built = new PhysicalExamObservationBuilder().build(source, OBS_UUID, categories.get(0),
		    PhysicalExamObservationBuilder.stripHtmlForNote(valueJson));
		assertTrue(!built.hasValue());
	}
	
	private static Observation sampleSourceObservation() {
		Observation source = new Observation();
		source.setId("source-obs-id");
		source.getSubject().setReference("Patient/test-patient-uuid");
		source.getEncounter().setReference("Encounter/test-encounter-uuid");
		source.setEffective(new org.hl7.fhir.r4.model.DateTimeType(new Date()));
		source.addPerformer(new Reference("Practitioner/test-practitioner-uuid"));
		source.getCode().setText("Physical examination");
		return source;
	}
	
}
