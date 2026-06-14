package org.openmrs.module.ihshr.fhir;

import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.AllergyIntolerance.AllergyIntoleranceCategory;
import org.hl7.fhir.r4.model.AllergyIntolerance.AllergyIntoleranceType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.MedicationStatement.MedicationStatementStatus;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationComponentComponent;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.hl7.fhir.r4.model.StringType;
import org.openmrs.module.ihshr.backlog.UnmappedTermArtifact;
import org.openmrs.module.ihshr.config.ClinicalTermCodingResolver;
import org.openmrs.module.ihshr.domain.ParsedMedicalHistoryTopic;
import org.openmrs.module.ihshr.utils.MedicalHistoryConstants;

import com.google.gson.JsonObject;

public class MedicalHistoryResourceBuilder {
	
	private static final Pattern SMOKING_DURATION = Pattern.compile("Since\\s*-\\s*([^.<]+)", Pattern.CASE_INSENSITIVE);
	
	public Observation buildObservation(Observation source, String obsUuid, ParsedMedicalHistoryTopic topic,
	        JsonObject config, String sharedNote, int index) {
		Observation observation = new Observation();
		observation.setId((String) null);
		
		Identifier identifier = new Identifier();
		identifier.setSystem(MedicalHistoryConstants.IDENTIFIER_SYSTEM);
		identifier.setValue(MedicalHistoryConstants.topicIdentifier(obsUuid, slug(topic.getTopicKey()), index));
		observation.addIdentifier(identifier);
		
		observation.setStatus(ObservationStatus.FINAL);
		
		if (config.has("category")) {
			CodeableConcept category = new CodeableConcept();
			category.addCoding(new Coding().setSystem(MedicalHistoryConstants.OBS_CATEGORY_SYSTEM).setCode(
			    config.get("category").getAsString()));
			observation.addCategory(category);
		}
		
		CodeableConcept code = new CodeableConcept();
		if (config.has("code_system") && config.has("code")) {
			code.addCoding(new Coding().setSystem(config.get("code_system").getAsString())
			        .setCode(config.get("code").getAsString())
			        .setDisplay(config.has("display") ? config.get("display").getAsString() : topic.getTopicLabel()));
		}
		code.setText(config.has("code_text") ? config.get("code_text").getAsString() : topic.getTopicLabel());
		observation.setCode(code);
		
		applySmokingValue(observation, topic, config);
		if (!observation.hasValue()) {
			if (config.has("value_as_codeable_text") && config.get("value_as_codeable_text").getAsBoolean()) {
				observation.setValue(new CodeableConcept().setText(topic.getValue()));
			} else {
				observation.setValue(new StringType(topic.getValue()));
			}
		}
		
		copySubjectEncounter(source, observation);
		if (StringUtils.isNotBlank(sharedNote)) {
			observation.addNote().setText(sharedNote);
		}
		return observation;
	}
	
	private void applySmokingValue(Observation observation, ParsedMedicalHistoryTopic topic, JsonObject config) {
		if (!config.has("value_map")) {
			return;
		}
		String lower = topic.getValue().toLowerCase(Locale.ROOT);
		if (lower.contains("smoker") && !lower.contains("non-smoker") && !lower.contains("never")) {
			JsonObject smoker = config.getAsJsonObject("value_map").getAsJsonObject("smoker");
			if (smoker != null) {
				CodeableConcept value = new CodeableConcept();
				value.addCoding(new Coding().setSystem(smoker.get("system").getAsString())
				        .setCode(smoker.get("code").getAsString()).setDisplay(smoker.get("display").getAsString()));
				observation.setValue(value);
			}
		}
		if (config.has("extract_duration_to_component")) {
			Matcher m = SMOKING_DURATION.matcher(topic.getValue());
			if (m.find()) {
				JsonObject compCfg = config.getAsJsonObject("extract_duration_to_component");
				ObservationComponentComponent component = new ObservationComponentComponent();
				component.setCode(new CodeableConcept().addCoding(new Coding()
				        .setSystem(compCfg.get("system").getAsString()).setCode(compCfg.get("code").getAsString())
				        .setDisplay(compCfg.get("display").getAsString())));
				component.setValue(new StringType(m.group(1).trim()));
				observation.addComponent(component);
			}
		}
	}
	
	public AllergyIntolerance buildAllergy(Observation source, String obsUuid, ParsedMedicalHistoryTopic topic,
	        JsonObject config, String sharedNote, int index) {
		AllergyIntolerance allergy = new AllergyIntolerance();
		allergy.setId((String) null);
		
		Identifier identifier = new Identifier();
		identifier.setSystem(MedicalHistoryConstants.IDENTIFIER_SYSTEM);
		identifier.setValue(MedicalHistoryConstants.topicIdentifier(obsUuid, slug(topic.getTopicKey()), index));
		allergy.addIdentifier(identifier);
		
		allergy.setType(AllergyIntoleranceType.ALLERGY);
		allergy.addCategory(AllergyIntoleranceCategory.ENVIRONMENT);
		
		String clinicalCode = config.has("clinical_status") ? config.get("clinical_status").getAsString() : "active";
		String verificationCode = config.has("verification_status") ? config.get("verification_status").getAsString()
		        : "confirmed";
		allergy.setClinicalStatus(new CodeableConcept().addCoding(new Coding().setSystem(
		    MedicalHistoryConstants.ALLERGY_CLINICAL_STATUS_SYSTEM).setCode(clinicalCode)));
		allergy.setVerificationStatus(new CodeableConcept().addCoding(new Coding().setSystem(
		    MedicalHistoryConstants.ALLERGY_VERIFICATION_SYSTEM).setCode(verificationCode)));
		
		allergy.setCode(new CodeableConcept().setText(topic.getValue()));
		
		if (source != null && source.hasSubject()) {
			allergy.setPatient(source.getSubject().copy());
		}
		if (StringUtils.isNotBlank(sharedNote)) {
			allergy.addNote().setText(sharedNote);
		}
		return allergy;
	}
	
	public MedicationStatement buildMedicationStatement(Observation source, String obsUuid, ParsedMedicalHistoryTopic topic,
	        JsonObject config, String sharedNote, int index) {
		MedicationStatement statement = new MedicationStatement();
		statement.setId((String) null);
		
		Identifier identifier = new Identifier();
		identifier.setSystem(MedicalHistoryConstants.IDENTIFIER_SYSTEM);
		identifier.setValue(MedicalHistoryConstants.topicIdentifier(obsUuid, slug(topic.getTopicKey()), index));
		statement.addIdentifier(identifier);
		
		String status = config.has("default_status") ? config.get("default_status").getAsString() : "active";
		statement.setStatus(MedicationStatementStatus.fromCode(status));
		
		statement.setMedication(new CodeableConcept().setText(extractMedicationText(topic.getValue())));
		
		if (source != null && source.hasSubject()) {
			statement.setSubject(source.getSubject().copy());
		}
		if (StringUtils.isNotBlank(sharedNote)) {
			statement.addNote().setText(sharedNote);
		}
		return statement;
	}
	
	public Condition buildProblemCondition(Observation source, String obsUuid, String conditionText, String sharedNote,
	        int index, String lookupFile) {
		Condition condition = new Condition();
		condition.setId((String) null);
		
		Identifier identifier = new Identifier();
		identifier.setSystem(MedicalHistoryConstants.IDENTIFIER_SYSTEM);
		identifier.setValue(MedicalHistoryConstants.topicIdentifier(obsUuid, "medical-history", index));
		condition.addIdentifier(identifier);
		
		CodeableConcept category = new CodeableConcept();
		category.addCoding(new Coding().setSystem(MedicalHistoryConstants.CONDITION_CATEGORY_SYSTEM).setCode(
		    MedicalHistoryConstants.CONDITION_CATEGORY_CODE));
		condition.addCategory(category);
		
		condition.setVerificationStatus(new CodeableConcept().addCoding(new Coding().setSystem(
		    MedicalHistoryConstants.VERIFICATION_STATUS_SYSTEM).setCode("confirmed")));
		condition.setClinicalStatus(new CodeableConcept().addCoding(new Coding().setSystem(
		    MedicalHistoryConstants.CLINICAL_STATUS_SYSTEM).setCode("active")));
		
		CodeableConcept code = new CodeableConcept();
		code.setText(conditionText);
		if (StringUtils.isNotBlank(lookupFile)) {
			Coding snomed = ClinicalTermCodingResolver.resolveMapping(conditionText, lookupFile,
			    UnmappedTermArtifact.MEDICAL_HISTORY_CONDITION);
			if (snomed != null) {
				code.addCoding(snomed);
			}
		}
		condition.setCode(code);
		
		if (source != null) {
			if (source.hasSubject()) {
				condition.setSubject(source.getSubject().copy());
			}
			if (source.hasEncounter()) {
				condition.setEncounter(source.getEncounter().copy());
			}
			Date recorded = source.getEffectiveDateTimeType() != null ? source.getEffectiveDateTimeType().getValue()
			        : (source.getIssued() != null ? source.getIssued() : null);
			if (recorded != null) {
				condition.setRecordedDate(recorded);
				condition.setOnset(new DateTimeType(recorded));
			}
		}
		if (StringUtils.isNotBlank(sharedNote)) {
			condition.addNote().setText(sharedNote);
		}
		return condition;
	}
	
	private static String extractMedicationText(String value) {
		if (StringUtils.isBlank(value)) {
			return value;
		}
		int idx = value.indexOf("Medication Name -");
		if (idx >= 0) {
			String rest = value.substring(idx + "Medication Name -".length()).trim();
			int dot = rest.indexOf('.');
			if (dot > 0) {
				return rest.substring(0, dot).trim();
			}
			return rest;
		}
		return value;
	}
	
	private static void copySubjectEncounter(Observation source, Observation target) {
		if (source == null) {
			return;
		}
		if (source.hasSubject()) {
			target.setSubject(source.getSubject().copy());
		}
		if (source.hasEncounter()) {
			target.setEncounter(source.getEncounter().copy());
		}
		if (source.getEffectiveDateTimeType() != null) {
			target.setEffective(source.getEffectiveDateTimeType().copy());
		} else if (source.getIssued() != null) {
			target.setIssued(source.getIssued());
		}
	}
	
	private static String slug(String topicKey) {
		return topicKey.replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
	}
	
}
