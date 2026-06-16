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
	
	public static List<ShrFhirQuery> buildHistoryQueries(String cruid, ShrHistoryRequest request) {
		if (request.getView() == ShrPullView.DEFAULT) {
			return singleton("default-encounter-timeline", "Encounter", buildDefaultTimelineUrl(cruid, request));
		}
		if (request.getView() == ShrPullView.PROBLEMS) {
			return singleton("problems", "Condition", buildProblemsUrl(cruid, request));
		}
		if (request.getView() == ShrPullView.MEDICATIONS) {
			return singleton("medications", "MedicationRequest", buildMedicationsUrl(cruid, request));
		}
		if (request.getView() == ShrPullView.VITALS) {
			return singleton("vitals", "Observation", buildVitalsUrl(cruid, request));
		}
		if (request.getView() == ShrPullView.LABS) {
			return singleton("labs", "Observation", buildLabsUrl(cruid, request));
		}
		if (request.getView() == ShrPullView.FAMILY_HISTORY) {
			return singleton("family-history", "FamilyMemberHistory", buildFamilyHistoryUrl(cruid, request));
		}
		if (request.getView() == ShrPullView.REFERRALS) {
			return singleton("referrals", "ServiceRequest", buildReferralsUrl(cruid, request));
		}
		if (request.getView() == ShrPullView.FOLLOW_UP) {
			return singleton("follow-up", "Observation", buildFollowUpUrl(cruid, request));
		}
		if (request.getView() == ShrPullView.DOCUMENTS) {
			return singleton("documents", "DocumentReference", buildDocumentsUrl(cruid, request));
		}
		return buildCustomQueries(cruid, request);
	}
	
	public static ShrFhirQuery buildRefreshQuery(String cruid, String since, int count, boolean includeLocalEcho) {
		ShrPullUrlBuilder builder = new ShrPullUrlBuilder();
		applySubject(builder, "Encounter", cruid);
		applyNoEcho(builder, includeLocalEcho);
		builder.param("_lastUpdated", "ge" + since).param("_count", String.valueOf(count))
		        .param("_revinclude", "Observation:encounter").param("_revinclude", "Condition:encounter")
		        .param("_revinclude", "MedicationRequest:encounter").param("_revinclude", "ServiceRequest:encounter")
		        .param("_revinclude", "DocumentReference:encounter").param("_revinclude", "Provenance:target")
		        .param("_format", "application/fhir+json");
		String url = builder.build("Encounter");
		return new ShrFhirQuery("refresh-encounter-timeline", "Encounter", url);
	}
	
	public static String buildResourceSearchUrl(String cruid, String resourceType,
	        java.util.Map<String, String> extraParams, boolean includeLocalEcho) {
		ShrPullUrlBuilder builder = new ShrPullUrlBuilder();
		applySubject(builder, resourceType, cruid);
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
	
	private static List<ShrFhirQuery> buildCustomQueries(String cruid, ShrHistoryRequest request) {
		List<ShrFhirQuery> queries = new ArrayList<ShrFhirQuery>();
		for (ShrPullRecordType type : request.getRecordTypes()) {
			queries.add(new ShrFhirQuery("custom-" + type.name().toLowerCase(), type.getFhirType(), buildCustomTypeUrl(type,
			    cruid, request)));
		}
		return queries;
	}
	
	private static String buildDefaultTimelineUrl(String cruid, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = new ShrPullUrlBuilder();
		applySubject(builder, "Encounter", cruid);
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
	
	private static String buildProblemsUrl(String cruid, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = baseSubjectBuilder("Condition", cruid, request);
		builder.param("clinical-status", StringUtils.defaultIfBlank(request.getStatus(), "active"));
		builder.param("category", "problem-list-item");
		applyDateRange(builder, "recorded-date", request);
		builder.param("_sort", request.isDescendingSort() ? "-recorded-date" : "recorded-date");
		applyConditionCode(builder, "code", request);
		return builder.build("Condition");
	}
	
	private static String buildMedicationsUrl(String cruid, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = baseSubjectBuilder("MedicationRequest", cruid, request);
		builder.param("status", StringUtils.defaultIfBlank(request.getStatus(), "active"));
		applyDateRange(builder, "authoredon", request);
		builder.param("_sort", request.isDescendingSort() ? "-authoredon" : "authoredon");
		builder.param("_include", "MedicationRequest:medication");
		builder.param("_include", "MedicationRequest:requester");
		applyConditionCode(builder, "reason-code", request);
		return builder.build("MedicationRequest");
	}
	
	private static String buildVitalsUrl(String cruid, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = trendSubjectBuilder("Observation", cruid, request);
		builder.param("category", "vital-signs");
		builder.param("code", "http://loinc.org|8480-6,http://loinc.org|8462-4,http://loinc.org|8867-4");
		applyDateRange(builder, "date", request);
		builder.param("_sort", request.isDescendingSort() ? "-date" : "date");
		builder.param("_count", "200");
		return builder.build("Observation");
	}
	
	private static String buildLabsUrl(String cruid, ShrHistoryRequest request) {
		String composite = request.formattedLabCompositeToken();
		if (composite == null) {
			throw new ShrPullException(ShrPullErrorCode.INVALID_FILTER,
			        "labs view requires labComposite or labCode + labValue (optional labComparator, default gt)");
		}
		ShrPullUrlBuilder builder = trendSubjectBuilder("Observation", cruid, request);
		builder.param("category", "laboratory");
		builder.param("code-value-quantity", composite);
		applyDateRange(builder, "date", request);
		builder.param("_sort", request.isDescendingSort() ? "-date" : "date");
		builder.param("_count", String.valueOf(Math.max(request.getCount(), 200)));
		return builder.build("Observation");
	}
	
	private static ShrPullUrlBuilder trendSubjectBuilder(String resourceType, String cruid, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = new ShrPullUrlBuilder();
		applySubject(builder, resourceType, cruid);
		applyNoEcho(builder, request.isIncludeLocalEcho());
		applySourceOrg(builder, request);
		builder.param("_format", "application/fhir+json");
		return builder;
	}
	
	private static String buildFamilyHistoryUrl(String cruid, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = new ShrPullUrlBuilder();
		applySubject(builder, "FamilyMemberHistory", cruid);
		applyNoEcho(builder, request.isIncludeLocalEcho());
		builder.param("status", StringUtils.defaultIfBlank(request.getStatus(), "completed"));
		applyDateRange(builder, "date", request);
		builder.param("_sort", request.isDescendingSort() ? "-date" : "date");
		builder.param("_count", String.valueOf(request.getCount()));
		applySourceOrg(builder, request);
		builder.param("_format", "application/fhir+json");
		return builder.build("FamilyMemberHistory");
	}
	
	private static String buildFollowUpUrl(String cruid, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = baseSubjectBuilder("Observation", cruid, request);
		builder.param("code", org.openmrs.module.ihshr.utils.FollowUpConstants.conceptSearchToken());
		applyDateRange(builder, "date", request);
		builder.param("_sort", request.isDescendingSort() ? "-date" : "date");
		return builder.build("Observation");
	}
	
	private static String buildReferralsUrl(String cruid, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = baseSubjectBuilder("ServiceRequest", cruid, request);
		builder.param("intent", "referral");
		builder.param("category", org.openmrs.module.ihshr.utils.ReferralConstants.SERVICE_REQUEST_TYPE_SYSTEM + "|"
		        + org.openmrs.module.ihshr.utils.ReferralConstants.SERVICE_REQUEST_TYPE_REFERRAL);
		applyDateRange(builder, "authored", request);
		builder.param("_sort", request.isDescendingSort() ? "-authored" : "authored");
		builder.param("_include", "ServiceRequest:requester");
		builder.param("_include", "ServiceRequest:encounter");
		return builder.build("ServiceRequest");
	}
	
	private static String buildDocumentsUrl(String cruid, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = baseSubjectBuilder("DocumentReference", cruid, request);
		builder.param("status", StringUtils.defaultIfBlank(request.getStatus(), "current"));
		applyDateRange(builder, "date", request);
		builder.param("_sort", request.isDescendingSort() ? "-date" : "date");
		return builder.build("DocumentReference");
	}
	
	private static String buildCustomTypeUrl(ShrPullRecordType type, String cruid, ShrHistoryRequest request) {
		switch (type) {
			case ENCOUNTER:
				return buildFilteredEncounterUrl(cruid, request);
			case CONDITION:
				return buildFilteredConditionUrl(cruid, request);
			case OBSERVATION:
				return buildFilteredObservationUrl(cruid, request);
			case MEDICATION_REQUEST:
				return buildFilteredMedicationRequestUrl(cruid, request);
			case SERVICE_REQUEST:
				return buildFilteredServiceRequestUrl(cruid, request);
			case DOCUMENT_REFERENCE:
				return buildDocumentsUrl(cruid, request);
			case FAMILY_MEMBER_HISTORY:
				return buildFamilyHistoryUrl(cruid, request);
			default:
				throw new ShrPullException(ShrPullErrorCode.INVALID_FILTER, "Unsupported custom record type: " + type);
		}
	}
	
	private static String buildFilteredEncounterUrl(String cruid, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = baseSubjectBuilder("Encounter", cruid, request);
		applyDateRange(builder, "date", request);
		builder.param("_sort", request.isDescendingSort() ? "-date" : "date");
		applyConditionCode(builder, "reason-code", request);
		builder.param("_include", "Encounter:participant");
		builder.param("_revinclude", "Observation:encounter");
		return builder.build("Encounter");
	}
	
	private static String buildFilteredConditionUrl(String cruid, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = baseSubjectBuilder("Condition", cruid, request);
		applyDateRange(builder, "recorded-date", request);
		builder.param("_sort", request.isDescendingSort() ? "-recorded-date" : "recorded-date");
		applyConditionCode(builder, "code", request);
		applyFreeText(builder, "code:text", request);
		if (StringUtils.isNotBlank(request.getStatus())) {
			builder.param("clinical-status", request.getStatus());
		}
		return builder.build("Condition");
	}
	
	private static String buildFilteredObservationUrl(String cruid, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = baseSubjectBuilder("Observation", cruid, request);
		applyDateRange(builder, "date", request);
		builder.param("_sort", request.isDescendingSort() ? "-date" : "date");
		applyConditionCode(builder, "code", request);
		applyFreeText(builder, "code:text", request);
		if (StringUtils.isNotBlank(request.getStatus())) {
			builder.param("status", request.getStatus());
		}
		return builder.build("Observation");
	}
	
	private static String buildFilteredMedicationRequestUrl(String cruid, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = baseSubjectBuilder("MedicationRequest", cruid, request);
		applyDateRange(builder, "authoredon", request);
		builder.param("_sort", request.isDescendingSort() ? "-authoredon" : "authoredon");
		applyConditionCode(builder, "reason-code", request);
		if (StringUtils.isNotBlank(request.getStatus())) {
			builder.param("status", request.getStatus());
		}
		return builder.build("MedicationRequest");
	}
	
	private static String buildFilteredServiceRequestUrl(String cruid, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = baseSubjectBuilder("ServiceRequest", cruid, request);
		applyDateRange(builder, "authored", request);
		builder.param("_sort", request.isDescendingSort() ? "-authored" : "authored");
		applyConditionCode(builder, "reason-code", request);
		if (StringUtils.isNotBlank(request.getStatus())) {
			builder.param("status", request.getStatus());
		}
		return builder.build("ServiceRequest");
	}
	
	private static ShrPullUrlBuilder baseSubjectBuilder(String resourceType, String cruid, ShrHistoryRequest request) {
		ShrPullUrlBuilder builder = new ShrPullUrlBuilder();
		applySubject(builder, resourceType, cruid);
		applyNoEcho(builder, request.isIncludeLocalEcho());
		builder.param("_count", String.valueOf(request.getCount()));
		applySourceOrg(builder, request);
		builder.param("_format", "application/fhir+json");
		return builder;
	}
	
	private static void applySubject(ShrPullUrlBuilder builder, String resourceType, String cruid) {
		String token = CruidConstants.IDENTIFIER_SYSTEM + "|" + cruid.trim();
		if ("Encounter".equals(resourceType) || "Condition".equals(resourceType) || "Observation".equals(resourceType)
		        || "MedicationRequest".equals(resourceType) || "ServiceRequest".equals(resourceType)
		        || "DocumentReference".equals(resourceType) || "FamilyMemberHistory".equals(resourceType)) {
			builder.param("patient.identifier", token);
			return;
		}
		builder.param("subject.identifier", token);
	}
	
	private static void applyNoEcho(ShrPullUrlBuilder builder, boolean includeLocalEcho) {
		if (includeLocalEcho || !ShrPullSettings.sourceFilterEnabled()) {
			return;
		}
		builder.param("_source:not", ShrPushMetaApplicator.resolveInstallationSourceUri());
	}
	
	private static void applySourceOrg(ShrPullUrlBuilder builder, ShrHistoryRequest request) {
		if (!ShrPullSettings.sourceFilterEnabled()) {
			return;
		}
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
