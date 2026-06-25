package org.openmrs.module.ihshr.fhir;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.openmrs.module.ihshr.domain.ParsedMedicalHistoryTopic;
import org.openmrs.module.ihshr.parser.MedicalHistoryNegativeFilter;
import org.openmrs.module.ihshr.parser.MedicalHistoryParser;
import org.openmrs.module.ihshr.parser.MedicalHistoryTopicConfig;
import com.google.gson.JsonObject;

/**
 * Builds SHR-ready patient medical history resources from one OpenMRS obs (concept 163210).
 * <p>
 * Flow: parse {@code Topic - Value} lines from obs text → drop negative answers (e.g. "No") → route
 * each positive topic to the FHIR type declared in {@code patient-history-topics.json}
 * (Observation, AllergyIntolerance, MedicationStatement, or Condition). A visit with all-negative
 * answers produces no resources.
 */
public class MedicalHistoryTransfer {
	
	private final MedicalHistoryParser parser = new MedicalHistoryParser();
	
	private final MedicalHistoryResourceBuilder builder = new MedicalHistoryResourceBuilder();
	
	public MedicalHistoryBuildResult build(Observation sourceObs, String obsUuid, String valueText) {
		if (sourceObs == null || StringUtils.isBlank(obsUuid)) {
			return MedicalHistoryBuildResult.empty();
		}
		if (StringUtils.isBlank(valueText) && sourceObs.hasValueStringType()) {
			valueText = sourceObs.getValueStringType().getValueAsString();
		}
		if (StringUtils.isBlank(valueText)) {
			return MedicalHistoryBuildResult.empty();
		}
		
		MedicalHistoryTopicConfig config = MedicalHistoryTopicConfig.getInstance();
		List<ParsedMedicalHistoryTopic> allTopics = parser.parse(valueText);
		
		// Doc §7.4: suppress "No" / negative-pattern answers — nothing is pushed for those topics.
		List<ParsedMedicalHistoryTopic> positiveTopics = new ArrayList<ParsedMedicalHistoryTopic>();
		for (ParsedMedicalHistoryTopic topic : allTopics) {
			if (!MedicalHistoryNegativeFilter.isNegative(topic.getValue(), config.getNegativePatterns())) {
				positiveTopics.add(topic);
			}
		}
		if (positiveTopics.isEmpty()) {
			return MedicalHistoryBuildResult.empty();
		}
		
		List<Observation> observations = new ArrayList<Observation>();
		List<AllergyIntolerance> allergies = new ArrayList<AllergyIntolerance>();
		List<MedicationStatement> medications = new ArrayList<MedicationStatement>();
		List<Condition> conditions = new ArrayList<Condition>();
		
		// Each topic maps to one FHIR resource type via patient-history-topics.json "resource" field.
		int conditionIndex = 0;
		for (ParsedMedicalHistoryTopic topic : positiveTopics) {
			JsonObject topicConfig = config.getTopicConfig(topic.getTopicKey());
			if (topicConfig == null || !topicConfig.has("resource")) {
				continue;
			}
			String topicNote = MedicalHistoryParser.buildTopicNoteText(topic);
			String resourceType = topicConfig.get("resource").getAsString();
			if ("Observation".equalsIgnoreCase(resourceType)) {
				// Social-history style topics (pregnancy, smoking, alcohol, etc.)
				observations.add(builder.buildObservation(sourceObs, obsUuid, topic, topicConfig, topicNote, 0));
			} else if ("AllergyIntolerance".equalsIgnoreCase(resourceType)) {
				allergies.add(builder.buildAllergy(sourceObs, obsUuid, topic, topicConfig, topicNote, 0));
			} else if ("MedicationStatement".equalsIgnoreCase(resourceType)) {
				medications.add(builder.buildMedicationStatement(sourceObs, obsUuid, topic, topicConfig, topicNote, 0));
			} else if ("Condition".equalsIgnoreCase(resourceType)) {
				// Past medical problems: one Condition per disease when comma_split is true
				// (e.g. "Diabetes, Hypertension" → two problem-list-item Conditions).
				String lookupFile = topicConfig.has("lookup_file") ? topicConfig.get("lookup_file").getAsString() : null;
				boolean commaSplit = !topicConfig.has("comma_split") || topicConfig.get("comma_split").getAsBoolean();
				List<String> diseases = commaSplit ? MedicalHistoryParser.splitMedicalHistoryConditions(topic.getValue())
				        : java.util.Collections.singletonList(topic.getValue());
				for (String disease : diseases) {
					conditionIndex++;
					conditions.add(builder.buildProblemCondition(sourceObs, obsUuid, disease, topicNote, conditionIndex,
					    lookupFile));
				}
			}
		}
		
		return new MedicalHistoryBuildResult(observations, allergies, medications, conditions);
	}
	
}
