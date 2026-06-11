package org.openmrs.module.ihshr.pull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.openmrs.module.ihshr.config.FhirConfig;
import org.openmrs.module.ihshr.fhir.ShrPushMetaApplicator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Doctor portal SHR pull orchestration (doc §4).
 */
@Service("ihshrShrPullService")
public class ShrPullService {
	
	@Autowired
	@Qualifier("ihshrShrPatientResolver")
	private ShrPatientResolver patientResolver;
	
	@Autowired
	@Qualifier("ihshrShrFhirPullClient")
	private ShrFhirPullClient fhirPullClient;
	
	@Autowired
	@Qualifier("ihshrFhirConfig")
	private FhirConfig fhirConfig;
	
	public Map<String, Object> resolvePatient(String openmrsPatientUuid) {
		ShrResolvedPatient resolved = patientResolver.resolve(openmrsPatientUuid);
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("openmrsPatientUuid", resolved.getOpenmrsPatientUuid());
		body.put("openmrsPatientDisplay", resolved.getOpenmrsPatientDisplay());
		body.put("cruid", resolved.getCruid());
		body.put("shrPatientFound", resolved.isShrPatientFound());
		body.put("shrPatientId", resolved.getShrPatientId());
		body.put("shrPatientDisplay", resolved.getShrPatientDisplay());
		return body;
	}
	
	public ShrPullResult getHistory(String openmrsPatientUuid, ShrHistoryRequest request) {
		ShrResolvedPatient patient = patientResolver.resolve(openmrsPatientUuid);
		if (!patient.isShrPatientFound()) {
			return emptyHistoryResult(patient, request, "SHR patient not found for CRUID " + patient.getCruid());
		}
		List<ShrFhirQuery> queries = ShrQueryTranslator.buildHistoryQueries(patient.getShrPatientId(), request);
		return executeQueries(patient, request, queries, queries.size() > 1);
	}
	
	public ShrPullResult getPage(String openmrsPatientUuid, String pageUrl, String format) {
		ShrResolvedPatient patient = patientResolver.resolve(openmrsPatientUuid);
		fhirPullClient.assertAllowedPageUrl(pageUrl);
		ShrHistoryRequest request = new ShrHistoryRequest();
		request.setFormat(format);
		List<ShrFhirQuery> queries = new ArrayList<ShrFhirQuery>();
		queries.add(new ShrFhirQuery("page", "Bundle", pageUrl.trim()));
		return executeQueries(patient, request, queries, false);
	}
	
	public ShrPullResult refresh(String openmrsPatientUuid, String since, int count, boolean includeLocalEcho, String format) {
		if (StringUtils.isBlank(since)) {
			throw new ShrPullException(ShrPullErrorCode.INVALID_FILTER, "since parameter is required for refresh");
		}
		ShrResolvedPatient patient = patientResolver.resolve(openmrsPatientUuid);
		if (!patient.isShrPatientFound()) {
			return emptyHistoryResult(patient, buildRefreshRequest(format, count, includeLocalEcho),
			    "SHR patient not found for CRUID " + patient.getCruid());
		}
		ShrFhirQuery query = ShrQueryTranslator.buildRefreshQuery(patient.getShrPatientId(), since.trim(), count,
		    includeLocalEcho);
		List<ShrFhirQuery> queries = new ArrayList<ShrFhirQuery>();
		queries.add(query);
		return executeQueries(patient, buildRefreshRequest(format, count, includeLocalEcho), queries, false);
	}
	
	public ShrPullResult searchResource(String openmrsPatientUuid, String resourceType, Map<String, String> extraParams,
	        boolean includeLocalEcho, String format) {
		if (StringUtils.isBlank(resourceType)) {
			throw new ShrPullException(ShrPullErrorCode.INVALID_FILTER, "resourceType is required");
		}
		ShrResolvedPatient patient = patientResolver.resolve(openmrsPatientUuid);
		if (!patient.isShrPatientFound()) {
			return emptyHistoryResult(patient, buildSearchRequest(format, includeLocalEcho),
			    "SHR patient not found for CRUID " + patient.getCruid());
		}
		String url = ShrQueryTranslator.buildResourceSearchUrl(patient.getShrPatientId(), resourceType.trim(), extraParams,
		    includeLocalEcho);
		List<ShrFhirQuery> queries = new ArrayList<ShrFhirQuery>();
		queries.add(new ShrFhirQuery("search-" + resourceType, resourceType, url));
		return executeQueries(patient, buildSearchRequest(format, includeLocalEcho), queries, false);
	}
	
	public Map<String, Object> readBinaryContent(String binaryId) {
		if (StringUtils.isBlank(binaryId)) {
			throw new ShrPullException(ShrPullErrorCode.INVALID_FILTER, "Binary id is required");
		}
		Binary binary = fhirPullClient.readBinary(binaryId.trim());
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("id", binary.getIdElement().getIdPart());
		body.put("contentType", binary.hasContentType() ? binary.getContentType() : null);
		body.put("data", binary.hasData() ? binary.getDataElement().getValueAsString() : null);
		return body;
	}
	
	private ShrPullResult executeQueries(ShrResolvedPatient patient, ShrHistoryRequest request, List<ShrFhirQuery> queries,
	        boolean mergeResults) {
		List<ShrBundleMerger.ExecutedQuery> executed = new ArrayList<ShrBundleMerger.ExecutedQuery>();
		List<Bundle> rawBundles = new ArrayList<Bundle>();
		for (ShrFhirQuery query : queries) {
			Bundle bundle = fhirPullClient.search(query.getUrl());
			executed.add(new ShrBundleMerger.ExecutedQuery(query, bundle));
			rawBundles.add(bundle);
		}
		Bundle merged = mergeResults ? ShrBundleMerger.merge(rawBundles) : firstBundle(rawBundles);
		ShrPullResult result = new ShrPullResult();
		result.setPatient(patient);
		result.setRequest(request);
		result.setExecuted(executed);
		result.setMerged(merged);
		result.setMergedFlag(mergeResults);
		result.setPagination(ShrBundleMerger.extractPaginationLinks(merged));
		result.setTotal(ShrBundleMerger.extractTotal(merged));
		result.setSourceUri(ShrPushMetaApplicator.resolveInstallationSourceUri());
		return result;
	}
	
	private static Bundle firstBundle(List<Bundle> bundles) {
		if (bundles == null || bundles.isEmpty()) {
			return new Bundle();
		}
		Bundle first = bundles.get(0);
		return first != null ? first : new Bundle();
	}
	
	private ShrPullResult emptyHistoryResult(ShrResolvedPatient patient, ShrHistoryRequest request, String message) {
		ShrPullResult result = new ShrPullResult();
		result.setPatient(patient);
		result.setRequest(request);
		result.setMerged(new Bundle());
		result.setMergedFlag(false);
		result.setEmptyMessage(message);
		result.setSourceUri(ShrPushMetaApplicator.resolveInstallationSourceUri());
		return result;
	}
	
	private static ShrHistoryRequest buildRefreshRequest(String format, int count, boolean includeLocalEcho) {
		ShrHistoryRequest request = new ShrHistoryRequest();
		request.setFormat(format);
		request.setCount(count);
		request.setIncludeLocalEcho(includeLocalEcho);
		return request;
	}
	
	private static ShrHistoryRequest buildSearchRequest(String format, boolean includeLocalEcho) {
		ShrHistoryRequest request = new ShrHistoryRequest();
		request.setFormat(format);
		request.setIncludeLocalEcho(includeLocalEcho);
		return request;
	}
	
	public String encodeBundle(Bundle bundle) {
		return fhirConfig.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
	}
	
	public Map<String, Object> toEnvelope(ShrPullResult result) {
		Map<String, Object> envelope = new LinkedHashMap<String, Object>();
		Map<String, Object> meta = new LinkedHashMap<String, Object>();
		meta.put("sourceUri", result.getSourceUri());
		meta.put("merged", result.isMergedFlag());
		meta.put("format", result.getRequest() != null ? result.getRequest().getFormat() : "envelope");
		if (StringUtils.isNotBlank(result.getEmptyMessage())) {
			meta.put("message", result.getEmptyMessage());
		}
		envelope.put("meta", meta);
		envelope.put("patient", patientMap(result.getPatient()));
		envelope.put("pagination", paginationMap(result));
		envelope.put("queries", ShrBundleMerger.toBundleSummaries(result.getExecuted()));
		if (result.getMerged() != null) {
			envelope.put("bundle", encodeBundle(result.getMerged()));
		}
		return envelope;
	}
	
	private static Map<String, Object> patientMap(ShrResolvedPatient patient) {
		Map<String, Object> map = new HashMap<String, Object>();
		if (patient == null) {
			return map;
		}
		map.put("openmrsPatientUuid", patient.getOpenmrsPatientUuid());
		map.put("openmrsPatientDisplay", patient.getOpenmrsPatientDisplay());
		map.put("cruid", patient.getCruid());
		map.put("shrPatientFound", patient.isShrPatientFound());
		map.put("shrPatientId", patient.getShrPatientId());
		map.put("shrPatientDisplay", patient.getShrPatientDisplay());
		return map;
	}
	
	private static Map<String, Object> paginationMap(ShrPullResult result) {
		Map<String, Object> pagination = new LinkedHashMap<String, Object>();
		pagination.put("total", result.getTotal());
		pagination.put("links", result.getPagination());
		return pagination;
	}
}
