package org.openmrs.module.ihshr.pull;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.ihshr.fhir.ShrPushMetaApplicator;
import org.openmrs.module.ihshr.utils.CruidConstants;

/**
 * Translates doc §4.2 filter form and §9 pull queries to FHIR search URLs.
 */
public final class ShrQueryTranslator {
	
	private ShrQueryTranslator() {
	}
	
	public static String buildPatientResolveUrl(String cruid) {
		return new ShrPullUrlBuilder().param("identifier", CruidConstants.IDENTIFIER_SYSTEM + "|" + cruid.trim()).build(
		    "Patient");
	}
	
	public static List<ShrFhirQuery> buildHistoryQueries(String shrPatientId, ShrHistoryRequest request) {
		if (request.getView() == ShrPullView.DEFAULT) {
			return singleton("default-encounter-timeline", "Encounter", buildDefaultTimelineUrl(shrPatientId, request));
		}
		if (request.getView() == ShrPullView.PROBLEMS) {
			return singleton("problems", "Condition", buildProblemsUrl(shrPatientId, request));
		}
		if (request.getView() == ShrPullView.MEDICATIONS) {
			return singleton("medications", "MedicationRequest", buildMedicationsUrl(shrPatientId, request));
		}
		if (request.getView() == ShrPullView.VITALS) {
			return singleton("vitals", "Observation", buildVitalsUrl(shrPatientId, request));
		}
		if (request.getView() == ShrPullView.FAMILY_HISTORY) {
			return singleton("family-history", "FamilyMemberHistory", buildFamilyHistoryUrl(shrPatientId, request));
		}
		if (request.getView() == ShrPullView.REFERRALS) {
			return singleton("referrals", "ServiceRequest", buildReferralsUrl(shrPatientId, request));
		}
		if (request.getView() == ShrPullView.FOLLOW_UP) {
			return singleton("follow-up", "Observation", buildFollowUpUrl(shrPatientId, request));
		}
		if (request.getView() == ShrPullView.DOCUMENTS) {
			return singleton("documents", "DocumentReference", buildDocumentsUrl(shrPatientId, request));
		}
		return buildCustomQueries(shrPatientId, request);
	}
	
	public static ShrFhirQuery buildRefreshQuery(String shrPatientId, String since, int count, boolean includeLocalEcho) {
		ShrPullUrlBuilder builder = new ShrPullUrlBuilder();
		applyNoEcho(builder, includeLocalEcho);
		builder.param("_since", since)
		        .param("_type",
		            "Encounter,Observation,Condition,MedicationRequest,ServiceRequest,DocumentReference,FamilyMemberHistory,Provenance")
		        .param("_count", String.valueOf(count)).param("_format", "application/fhir+json");
		String url = builder.build("Patient/" + shrPatientId + "/$everything");
		return new ShrFhirQuery("refresh-everything", "Bundle", url);
	}
	
	public static String buildResourceSearchUrl(String shrPatientId, String resourceType,
	        java.util.Map<String, String> extraParams, boolean includeLocalEcho) {
		ShrPullUrlBuilder builder = new ShrPullUrlBuilder();
		applySubject(builder, resourceType, shrPatientId);
		applyNoEcho(builder, includeLocalEcho);
		builder.param("_format", "application/fhir+json");
		if (extraParams != null) {
			for (java.util.Map.Entry<String, String> entry : extraParams.entrySet()) {
				if (isReservedParam(entry.getKey())) {
					continue;
				}
				builder.param(entry.getKey(), entry.getValue());
			}
		}
		if (extraParams == null || !extraParams.containsKey("_count")) {
			builder.param("_count", String.valueOf(ShrPullSettings.defaultCount()));
		}
		return builder.build(resourceType);
	}
	
	private static List<ShrFhirQuery> buildCustomQueries(String shrPatientId, ShrHistoryRequest request) {
		List<ShrFhirQuery> queries = new ArrayList<ShrFhirQuery>();
		for (ShrPullRecordType type : request.getRecordTypes()) {
			queries.add(new ShrFhirQuery("custom-" + type.name().toLowerCase(), type.getFhirType(), buildCustomTypeUrl(type,
			    shrPatientId, request)));
		}
		return queries;
	}
	
	private static String buildDefaultTimelineUrl(String shrPatientId, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = new ShrPullUrlBuilder();
		builder.param("subject", "Patient/" + shrPatientId);
		applyNoEcho(builder, request.isIncludeLocalEcho());
		builder.param("class", "AMB");
		applyDateRange(builder, "date", request);
		builder.param("_sort", request.isDescendingSort() ? "-date" : "date");
		builder.param("_count", String.valueOf(request.getCount()));
		builder.param("_include", "Encounter:participant");
		builder.param("_include", "Encounter:location");
		builder.param("_include", "Encounter:diagnosis");
		builder.param("_revinclude", "Observation:encounter");
		builder.param("_revinclude", "Condition:encounter");
		builder.param("_revinclude", "MedicationRequest:encounter");
		builder.param("_revinclude", "ServiceRequest:encounter");
		builder.param("_revinclude", "DocumentReference:encounter");
		builder.param("_revinclude", "Provenance:target");
		applySourceOrg(builder, request);
		applyConditionCodeForEncounter(builder, request);
		builder.param("_format", "application/fhir+json");
		return builder.build("Encounter");
	}
	
	private static String buildProblemsUrl(String shrPatientId, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = baseSubjectBuilder("Condition", shrPatientId, request);
		builder.param("clinical-status", StringUtils.defaultIfBlank(request.getStatus(), "active"));
		builder.param("category", "problem-list-item");
		applyDateRange(builder, "recorded-date", request);
		builder.param("_sort", request.isDescendingSort() ? "-recorded-date" : "recorded-date");
		applyConditionCode(builder, "code", request);
		return builder.build("Condition");
	}
	
	private static String buildMedicationsUrl(String shrPatientId, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = baseSubjectBuilder("MedicationRequest", shrPatientId, request);
		builder.param("status", StringUtils.defaultIfBlank(request.getStatus(), "active"));
		applyDateRange(builder, "authoredon", request);
		builder.param("_sort", request.isDescendingSort() ? "-authoredon" : "authoredon");
		builder.param("_include", "MedicationRequest:medication");
		builder.param("_include", "MedicationRequest:requester");
		applyConditionCode(builder, "reason-code", request);
		return builder.build("MedicationRequest");
	}
	
	private static String buildVitalsUrl(String shrPatientId, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = baseSubjectBuilder("Observation", shrPatientId, request);
		builder.param("category", "vital-signs");
		builder.param("code", "http://loinc.org|8480-6,http://loinc.org|8462-4,http://loinc.org|8867-4");
		applyDateRange(builder, "date", request);
		builder.param("_sort", request.isDescendingSort() ? "-date" : "date");
		builder.param("_count", "200");
		return builder.build("Observation");
	}
	
	private static String buildFamilyHistoryUrl(String shrPatientId, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = new ShrPullUrlBuilder();
		builder.param("patient", "Patient/" + shrPatientId);
		applyNoEcho(builder, request.isIncludeLocalEcho());
		builder.param("status", StringUtils.defaultIfBlank(request.getStatus(), "completed"));
		applyDateRange(builder, "date", request);
		builder.param("_sort", request.isDescendingSort() ? "-date" : "date");
		builder.param("_count", String.valueOf(request.getCount()));
		applySourceOrg(builder, request);
		builder.param("_format", "application/fhir+json");
		return builder.build("FamilyMemberHistory");
	}
	
	private static String buildFollowUpUrl(String shrPatientId, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = baseSubjectBuilder("Observation", shrPatientId, request);
		builder.param("code", org.openmrs.module.ihshr.utils.FollowUpConstants.conceptSearchToken());
		applyDateRange(builder, "date", request);
		builder.param("_sort", request.isDescendingSort() ? "-date" : "date");
		return builder.build("Observation");
	}
	
	private static String buildReferralsUrl(String shrPatientId, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = baseSubjectBuilder("ServiceRequest", shrPatientId, request);
		builder.param("category", org.openmrs.module.ihshr.utils.ReferralConstants.SERVICE_REQUEST_TYPE_SYSTEM + "|"
		        + org.openmrs.module.ihshr.utils.ReferralConstants.SERVICE_REQUEST_TYPE_REFERRAL);
		applyDateRange(builder, "authored", request);
		builder.param("_sort", request.isDescendingSort() ? "-authored" : "authored");
		builder.param("_include", "ServiceRequest:requester");
		builder.param("_include", "ServiceRequest:encounter");
		return builder.build("ServiceRequest");
	}
	
	private static String buildDocumentsUrl(String shrPatientId, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = baseSubjectBuilder("DocumentReference", shrPatientId, request);
		builder.param("status", StringUtils.defaultIfBlank(request.getStatus(), "current"));
		applyDateRange(builder, "date", request);
		builder.param("_sort", request.isDescendingSort() ? "-date" : "date");
		return builder.build("DocumentReference");
	}
	
	private static String buildCustomTypeUrl(ShrPullRecordType type, String shrPatientId, ShrHistoryRequest request) {
		switch (type) {
			case ENCOUNTER:
				return buildFilteredEncounterUrl(shrPatientId, request);
			case CONDITION:
				return buildFilteredConditionUrl(shrPatientId, request);
			case OBSERVATION:
				return buildFilteredObservationUrl(shrPatientId, request);
			case MEDICATION_REQUEST:
				return buildFilteredMedicationRequestUrl(shrPatientId, request);
			case SERVICE_REQUEST:
				return buildFilteredServiceRequestUrl(shrPatientId, request);
			case DOCUMENT_REFERENCE:
				return buildDocumentsUrl(shrPatientId, request);
			case FAMILY_MEMBER_HISTORY:
				return buildFamilyHistoryUrl(shrPatientId, request);
			default:
				throw new ShrPullException(ShrPullErrorCode.INVALID_FILTER, "Unsupported custom record type: " + type);
		}
	}
	
	private static String buildFilteredEncounterUrl(String shrPatientId, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = baseSubjectBuilder("Encounter", shrPatientId, request);
		applyDateRange(builder, "date", request);
		builder.param("_sort", request.isDescendingSort() ? "-date" : "date");
		applyConditionCode(builder, "reason-code", request);
		builder.param("_include", "Encounter:participant");
		builder.param("_revinclude", "Observation:encounter");
		return builder.build("Encounter");
	}
	
	private static String buildFilteredConditionUrl(String shrPatientId, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = baseSubjectBuilder("Condition", shrPatientId, request);
		applyDateRange(builder, "recorded-date", request);
		builder.param("_sort", request.isDescendingSort() ? "-recorded-date" : "recorded-date");
		applyConditionCode(builder, "code", request);
		applyFreeText(builder, "code:text", request);
		if (StringUtils.isNotBlank(request.getStatus())) {
			builder.param("clinical-status", request.getStatus());
		}
		return builder.build("Condition");
	}
	
	private static String buildFilteredObservationUrl(String shrPatientId, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = baseSubjectBuilder("Observation", shrPatientId, request);
		applyDateRange(builder, "date", request);
		builder.param("_sort", request.isDescendingSort() ? "-date" : "date");
		applyConditionCode(builder, "code", request);
		applyFreeText(builder, "code:text", request);
		if (StringUtils.isNotBlank(request.getStatus())) {
			builder.param("status", request.getStatus());
		}
		return builder.build("Observation");
	}
	
	private static String buildFilteredMedicationRequestUrl(String shrPatientId, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = baseSubjectBuilder("MedicationRequest", shrPatientId, request);
		applyDateRange(builder, "authoredon", request);
		builder.param("_sort", request.isDescendingSort() ? "-authoredon" : "authoredon");
		applyConditionCode(builder, "reason-code", request);
		if (StringUtils.isNotBlank(request.getStatus())) {
			builder.param("status", request.getStatus());
		}
		return builder.build("MedicationRequest");
	}
	
	private static String buildFilteredServiceRequestUrl(String shrPatientId, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = baseSubjectBuilder("ServiceRequest", shrPatientId, request);
		applyDateRange(builder, "authored", request);
		builder.param("_sort", request.isDescendingSort() ? "-authored" : "authored");
		applyConditionCode(builder, "reason-code", request);
		if (StringUtils.isNotBlank(request.getStatus())) {
			builder.param("status", request.getStatus());
		}
		return builder.build("ServiceRequest");
	}
	
	private static ShrPullUrlBuilder baseSubjectBuilder(String resourceType, String shrPatientId, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = new ShrPullUrlBuilder();
		applySubject(builder, resourceType, shrPatientId);
		applyNoEcho(builder, request.isIncludeLocalEcho());
		builder.param("_count", String.valueOf(request.getCount()));
		applySourceOrg(builder, request);
		builder.param("_format", "application/fhir+json");
		return builder;
	}
	
	private static void applySubject(ShrPullUrlBuilder builder, String resourceType, String shrPatientId) {
		if ("FamilyMemberHistory".equals(resourceType)) {
			builder.param("patient", "Patient/" + shrPatientId);
		} else {
			builder.param("subject", "Patient/" + shrPatientId);
		}
	}
	
	private static void applyNoEcho(ShrPullUrlBuilder builder, boolean includeLocalEcho) {
		if (!includeLocalEcho) {
			builder.param("_source:not", ShrPushMetaApplicator.resolveInstallationSourceUri());
		}
	}
	
	private static void applySourceOrg(ShrPullUrlBuilder builder, ShrHistoryRequest request) {
		if (StringUtils.isNotBlank(request.getSourceOrg())) {
			builder.param("_source", request.getSourceOrg().trim());
		}
	}
	
	private static void applyDateRange(ShrPullUrlBuilder builder, String paramName, ShrHistoryRequest request) {
		if (request.getDateFrom() != null) {
			builder.param(paramName, "ge" + request.getDateFrom());
		}
		if (request.getDateTo() != null) {
			builder.param(paramName, "le" + request.getDateTo());
		}
	}
	
	private static void applyConditionCode(ShrPullUrlBuilder builder, String paramName, ShrHistoryRequest request) {
		String token = request.formattedConditionToken();
		if (token != null) {
			builder.param(paramName, token);
		}
	}
	
	private static void applyConditionCodeForEncounter(ShrPullUrlBuilder builder, ShrHistoryRequest request) {
		applyConditionCode(builder, "reason-code", request);
	}
	
	private static void applyFreeText(ShrPullUrlBuilder builder, String paramName, ShrHistoryRequest request) {
		if (StringUtils.isNotBlank(request.getFreeText())) {
			builder.param(paramName, request.getFreeText().trim());
		}
	}
	
	private static boolean isReservedParam(String key) {
		return "mpiId".equalsIgnoreCase(key) || "openmrsPatientUuid".equalsIgnoreCase(key) || "format".equalsIgnoreCase(key)
		        || "view".equalsIgnoreCase(key) || "includeLocalEcho".equalsIgnoreCase(key);
	}
	
	private static List<ShrFhirQuery> singleton(String id, String type, String url) {
		List<ShrFhirQuery> list = new ArrayList<ShrFhirQuery>();
		list.add(new ShrFhirQuery(id, type, url));
		return list;
	}
}
