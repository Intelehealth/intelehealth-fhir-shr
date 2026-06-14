package org.openmrs.module.ihshr.fhir;

import java.util.Date;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.openmrs.module.ihshr.backlog.UnmappedTermArtifact;
import org.openmrs.module.ihshr.config.ClinicalTermCodingResolver;
import org.openmrs.module.ihshr.domain.ParsedReferral;
import org.openmrs.module.ihshr.utils.ReferralConstants;

public class ReferralServiceRequestBuilder {
	
	private static final String REFERRAL_SPECIALTY_MAPPINGS = "referral-specialty-mappings.json";
	
	public ServiceRequest build(Observation sourceObs, String obsUuid, ParsedReferral referral) {
		ServiceRequest serviceRequest = new ServiceRequest();
		serviceRequest.setId((String) null);
		serviceRequest.setStatus(ServiceRequest.ServiceRequestStatus.ACTIVE);
		// The HAPI FHIR R4 model used by this repo does not support intent=referral. We mark
		// referrals using a category token and keep a supported intent value.
		serviceRequest.setIntent(ServiceRequest.ServiceRequestIntent.ORDER);
		
		CodeableConcept referralType = new CodeableConcept();
		referralType.addCoding(new Coding().setSystem(ReferralConstants.SERVICE_REQUEST_TYPE_SYSTEM)
		        .setCode(ReferralConstants.SERVICE_REQUEST_TYPE_REFERRAL).setDisplay("Referral"));
		serviceRequest.addCategory(referralType);
		
		Identifier identifier = new Identifier();
		identifier.setSystem(ReferralConstants.IDENTIFIER_SYSTEM);
		identifier.setValue(ReferralConstants.serviceRequestIdentifier(obsUuid, referral.getIndex()));
		serviceRequest.addIdentifier(identifier);
		
		CodeableConcept code = new CodeableConcept();
		code.setText(referral.getSpecialty());
		Coding snomed = ClinicalTermCodingResolver.resolveMapping(referral.getSpecialty(), REFERRAL_SPECIALTY_MAPPINGS,
		    UnmappedTermArtifact.REFERRAL_SPECIALTY);
		if (snomed != null) {
			code.addCoding(snomed);
		}
		serviceRequest.setCode(code);
		
		if (StringUtils.isNotBlank(referral.getCategory())) {
			CodeableConcept category = new CodeableConcept();
			category.setText(referral.getCategory());
			category.addCoding(new Coding().setSystem(ReferralConstants.CATEGORY_SYSTEM)
			        .setCode(normalizeToken(referral.getCategory())).setDisplay(referral.getCategory()));
			serviceRequest.addCategory(category);
		}
		
		serviceRequest.setPriority(resolvePriority(referral));
		
		if (StringUtils.isNotBlank(referral.getNotes())) {
			serviceRequest.addNote(new Annotation().setText(referral.getNotes()));
		}
		
		if (sourceObs != null) {
			if (sourceObs.hasSubject()) {
				serviceRequest.setSubject(sourceObs.getSubject().copy());
			}
			if (sourceObs.hasEncounter()) {
				serviceRequest.setEncounter(sourceObs.getEncounter().copy());
			}
			if (sourceObs.hasPerformer()) {
				serviceRequest.setRequester(sourceObs.getPerformerFirstRep().copy());
			}
			Date authored = sourceObs.getEffectiveDateTimeType() != null ? sourceObs.getEffectiveDateTimeType().getValue()
			        : (sourceObs.getIssued() != null ? sourceObs.getIssued() : null);
			if (authored != null) {
				serviceRequest.setAuthoredOn(authored);
			}
		}
		
		return serviceRequest;
	}
	
	private static ServiceRequest.ServiceRequestPriority resolvePriority(ParsedReferral referral) {
		String priority = StringUtils.trimToEmpty(referral.getPriorityText());
		if (priority.isEmpty() && StringUtils.isNotBlank(referral.getCategory())) {
			priority = referral.getCategory();
		}
		String token = priority.trim().toLowerCase(Locale.ROOT);
		if ("urgent".equals(token)) {
			return ServiceRequest.ServiceRequestPriority.URGENT;
		}
		if ("asap".equals(token)) {
			return ServiceRequest.ServiceRequestPriority.ASAP;
		}
		if ("stat".equals(token)) {
			return ServiceRequest.ServiceRequestPriority.STAT;
		}
		return ServiceRequest.ServiceRequestPriority.ROUTINE;
	}
	
	private static String normalizeToken(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
	}
	
}
