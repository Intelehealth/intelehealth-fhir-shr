package org.openmrs.module.ihshr.pull.timeline;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.openmrs.module.ihshr.pull.ShrHistoryRequest;
import org.openmrs.module.ihshr.pull.ShrPullException;
import org.openmrs.module.ihshr.pull.ShrPullErrorCode;
import org.openmrs.module.ihshr.pull.ShrPullResult;
import org.openmrs.module.ihshr.pull.ShrPullView;
import org.openmrs.module.ihshr.pull.ShrResolvedPatient;
import org.openmrs.module.ihshr.pull.timeline.ShrEncounterTimelineBuilder.ShrTimelineBuildResult;
import org.openmrs.module.ihshr.pull.ShrBundleMerger;

/**
 * Formats {@link ShrPullResult} as doc §10.2 timeline JSON ({@code format=timeline}).
 */
public final class ShrTimelineFormatter {
	
	public static final String SCHEMA_VERSION = "1.0";
	
	private ShrTimelineFormatter() {
	}
	
	public static Map<String, Object> format(ShrPullResult result) {
		assertTimelineSupported(result);
		Bundle bundle = result.getMerged() != null ? result.getMerged() : new Bundle();
		ShrTimelineBuildResult built = ShrEncounterTimelineBuilder.build(bundle, result.getRequest());
		
		Map<String, Object> response = new LinkedHashMap<String, Object>();
		response.put("meta", metaMap(result));
		response.put("patient", patientMap(result.getPatient()));
		response.put("pagination", paginationMap(result));
		response.put("queries", ShrBundleMerger.toBundleSummaries(result.getExecuted()));
		response.put("timeline", built.getTimeline());
		if (!built.getWarnings().isEmpty()) {
			response.put("warnings", built.getWarnings());
		}
		if (StringUtils.isNotBlank(result.getEmptyMessage())) {
			response.put("message", result.getEmptyMessage());
		}
		return response;
	}
	
	private static void assertTimelineSupported(ShrPullResult result) {
		if (result == null || result.getRequest() == null) {
			return;
		}
		ShrPullView view = result.getRequest().getView();
		if (view != ShrPullView.DEFAULT && view != ShrPullView.CUSTOM) {
			throw new ShrPullException(ShrPullErrorCode.INVALID_FILTER,
			        "format=timeline is supported for view=default (or refresh/page Encounter bundles). Got view="
			                + view.name().toLowerCase());
		}
	}
	
	private static Map<String, Object> metaMap(ShrPullResult result) {
		Map<String, Object> meta = new LinkedHashMap<String, Object>();
		meta.put("format", "timeline");
		meta.put("schemaVersion", SCHEMA_VERSION);
		meta.put("generatedAt", Instant.now().toString());
		meta.put("sourceUri", result.getSourceUri());
		meta.put("merged", result.isMergedFlag());
		if (result.getRequest() != null) {
			meta.put("view", result.getRequest().getView().name().toLowerCase());
		}
		return meta;
	}
	
	private static Map<String, Object> patientMap(ShrResolvedPatient patient) {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
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
