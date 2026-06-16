package org.openmrs.module.ihshr.pull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
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
	
	private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
	
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
		enforceAccess(request);
		ShrResolvedPatient patient = patientResolver.resolve(openmrsPatientUuid);
		if (!patient.isShrPatientFound()) {
			return emptyHistoryResult(patient, request, "SHR patient not found for CRUID " + patient.getCruid());
		}
		List<ShrFhirQuery> queries = ShrQueryTranslator.buildHistoryQueries(patient.getCruid(), request);
		return executeQueries(patient, request, queries, queries.size() > 1);
	}
	
	public ShrPullResult getPage(String openmrsPatientUuid, String pageUrl, String format, boolean includeLocalEcho) {
		ShrHistoryRequest request = new ShrHistoryRequest();
		request.setFormat(format);
		request.setIncludeLocalEcho(ShrPullAccessControl.resolveIncludeLocalEcho(includeLocalEcho));
		ShrResolvedPatient patient = patientResolver.resolve(openmrsPatientUuid);
		fhirPullClient.assertAllowedPageUrl(pageUrl);
		List<ShrFhirQuery> queries = new ArrayList<ShrFhirQuery>();
		queries.add(new ShrFhirQuery("page", "Bundle", pageUrl.trim()));
		return executeQueries(patient, request, queries, false);
	}
	
	public ShrPullResult refresh(String openmrsPatientUuid, String since, int count, boolean includeLocalEcho, String format) {
		if (StringUtils.isBlank(since)) {
			throw new ShrPullException(ShrPullErrorCode.INVALID_FILTER, "since parameter is required for refresh");
		}
		boolean resolvedEcho = ShrPullAccessControl.resolveIncludeLocalEcho(includeLocalEcho);
		ShrResolvedPatient patient = patientResolver.resolve(openmrsPatientUuid);
		ShrHistoryRequest request = buildRefreshRequest(format, count, resolvedEcho);
		if (!patient.isShrPatientFound()) {
			return emptyHistoryResult(patient, request, "SHR patient not found for CRUID " + patient.getCruid());
		}
		ShrFhirQuery query = ShrQueryTranslator.buildRefreshQuery(patient.getCruid(), since.trim(), count, resolvedEcho);
		List<ShrFhirQuery> queries = new ArrayList<ShrFhirQuery>();
		queries.add(query);
		return executeQueries(patient, request, queries, false);
	}
	
	public ShrPullResult searchResource(String openmrsPatientUuid, String resourceType, Map<String, String> extraParams,
	        boolean includeLocalEcho, String format) {
		if (StringUtils.isBlank(resourceType)) {
			throw new ShrPullException(ShrPullErrorCode.INVALID_FILTER, "resourceType is required");
		}
		boolean resolvedEcho = ShrPullAccessControl.resolveIncludeLocalEcho(includeLocalEcho);
		ShrResolvedPatient patient = patientResolver.resolve(openmrsPatientUuid);
		ShrHistoryRequest request = buildSearchRequest(format, resolvedEcho);
		if (!patient.isShrPatientFound()) {
			return emptyHistoryResult(patient, request, "SHR patient not found for CRUID " + patient.getCruid());
		}
		String url = ShrQueryTranslator.buildResourceSearchUrl(patient.getCruid(), resourceType.trim(), extraParams,
		    resolvedEcho);
		List<ShrFhirQuery> queries = new ArrayList<ShrFhirQuery>();
		queries.add(new ShrFhirQuery("search-" + resourceType, resourceType, url));
		return executeQueries(patient, request, queries, false);
	}
	
	public Map<String, Object> readBinaryContent(String binaryId) {
		ShrBinaryContent content = readBinaryBytes(binaryId);
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("id", content.getId());
		body.put("contentType", content.getContentType());
		body.put("data", content.getData() != null ? java.util.Base64.getEncoder().encodeToString(content.getData()) : null);
		return body;
	}
	
	public ShrBinaryContent readBinaryBytes(String binaryId) {
		if (StringUtils.isBlank(binaryId)) {
			throw new ShrPullException(ShrPullErrorCode.INVALID_FILTER, "Binary id is required");
		}
		Binary binary = fhirPullClient.readBinary(binaryId.trim());
		byte[] data = binary.hasData() ? binary.getData() : new byte[0];
		String contentType = binary.hasContentType() ? binary.getContentType() : "application/octet-stream";
		return new ShrBinaryContent(binary.getIdElement().getIdPart(), contentType, data);
	}
	
	private static void enforceAccess(ShrHistoryRequest request) {
		if (request != null) {
			request.setIncludeLocalEcho(ShrPullAccessControl.resolveIncludeLocalEcho(request.isIncludeLocalEcho()));
		}
	}
	
	private ShrPullResult executeQueries(ShrResolvedPatient patient, ShrHistoryRequest request, List<ShrFhirQuery> queries,
	        boolean mergeResults) {
		List<ShrBundleMerger.ExecutedQuery> executed = executeQueriesParallel(queries);
		List<Bundle> rawBundles = new ArrayList<Bundle>();
		for (ShrBundleMerger.ExecutedQuery row : executed) {
			rawBundles.add(row.getBundle());
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
	
	private List<ShrBundleMerger.ExecutedQuery> executeQueriesParallel(List<ShrFhirQuery> queries) {
		if (queries == null || queries.isEmpty()) {
			return new ArrayList<ShrBundleMerger.ExecutedQuery>();
		}
		if (queries.size() == 1) {
			List<ShrBundleMerger.ExecutedQuery> single = new ArrayList<ShrBundleMerger.ExecutedQuery>();
			ShrFhirQuery query = queries.get(0);
			single.add(new ShrBundleMerger.ExecutedQuery(query, fhirPullClient.search(query.getUrl())));
			return single;
		}
		int poolSize = Math.min(queries.size(), ShrPullSettings.maxParallelQueries());
		ExecutorService executor = Executors.newFixedThreadPool(poolSize);
		try {
			List<Future<ShrBundleMerger.ExecutedQuery>> futures = new ArrayList<Future<ShrBundleMerger.ExecutedQuery>>();
			for (final ShrFhirQuery query : queries) {
				futures.add(executor.submit(new Callable<ShrBundleMerger.ExecutedQuery>() {
					
					@Override
					public ShrBundleMerger.ExecutedQuery call() {
						return new ShrBundleMerger.ExecutedQuery(query, fhirPullClient.search(query.getUrl()));
					}
				}));
			}
			List<ShrBundleMerger.ExecutedQuery> executed = new ArrayList<ShrBundleMerger.ExecutedQuery>();
			for (Future<ShrBundleMerger.ExecutedQuery> future : futures) {
				try {
					executed.add(future.get());
				}
				catch (ExecutionException ex) {
					Throwable cause = ex.getCause();
					if (cause instanceof ShrPullException) {
						throw (ShrPullException) cause;
					}
					throw new ShrPullException(ShrPullErrorCode.SHR_UPSTREAM_ERROR, cause != null ? cause.getMessage()
					        : ex.getMessage(), cause);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					throw new ShrPullException(ShrPullErrorCode.SHR_TIMEOUT, "SHR pull query interrupted", ex);
				}
			}
			return executed;
		}
		finally {
			executor.shutdown();
		}
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
			envelope.put("bundle", toBundleJson(result.getMerged()));
		}
		return envelope;
	}
	
	private Object toBundleJson(Bundle bundle) {
		String json = encodeBundle(bundle);
		try {
			return JSON_MAPPER.readValue(json, Object.class);
		}
		catch (Exception ex) {
			return json;
		}
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
