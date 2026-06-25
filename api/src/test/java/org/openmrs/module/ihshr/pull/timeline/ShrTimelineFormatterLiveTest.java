package org.openmrs.module.ihshr.pull.timeline;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Bundle;
import org.junit.Assume;
import org.junit.Test;
import org.openmrs.module.ihshr.fhir.ShrPushMetaApplicator;
import org.openmrs.module.ihshr.pull.ShrBundleMerger;
import org.openmrs.module.ihshr.pull.ShrFhirQuery;
import org.openmrs.module.ihshr.pull.ShrHistoryRequest;
import org.openmrs.module.ihshr.pull.ShrPullResult;
import org.openmrs.module.ihshr.pull.ShrQueryTranslator;
import org.openmrs.module.ihshr.pull.ShrResolvedPatient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import ca.uhn.fhir.context.FhirContext;

/**
 * Live SHR sample: reads {@code target/test-output/shr-live-encounter-bundle.json} and writes the
 * same JSON shape as {@code GET .../ihshr/shr/history/ uuid}?view=default&format=timeline}.
 */
public class ShrTimelineFormatterLiveTest {
	
	private static final String SAMPLE_CRUID = "93749568-ec3a-4991-991c-38d4875fcf1c";
	
	private static final Path LIVE_BUNDLE = Paths.get("target/test-output/shr-live-encounter-bundle.json");
	
	private static final Path OUTPUT = Paths.get("target/test-output/shr-live-pull-api-timeline-response.json");
	
	private final FhirContext fhirContext = FhirContext.forR4();
	
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	
	@Test
	public void printTimelineFromLiveShrBundle() throws Exception {
		Assume.assumeTrue("Live bundle not present: " + LIVE_BUNDLE.toAbsolutePath(), Files.isRegularFile(LIVE_BUNDLE));
		String json = new String(Files.readAllBytes(LIVE_BUNDLE), StandardCharsets.UTF_8);
		Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, json);
		
		ShrHistoryRequest request = ShrHistoryRequest.fromQueryParams(params("view", "default", "format", "timeline",
		    "count", "50"));
		List<ShrFhirQuery> queries = ShrQueryTranslator.buildHistoryQueries(SAMPLE_CRUID, request);
		
		List<ShrBundleMerger.ExecutedQuery> executed = new ArrayList<ShrBundleMerger.ExecutedQuery>();
		executed.add(new ShrBundleMerger.ExecutedQuery(queries.get(0), bundle));
		
		ShrResolvedPatient patient = new ShrResolvedPatient();
		patient.setOpenmrsPatientUuid("openmrs-patient-uuid-from-doctor-ui");
		patient.setOpenmrsPatientDisplay("Abdul Based");
		patient.setCruid(SAMPLE_CRUID);
		patient.setShrPatientFound(true);
		patient.setShrPatientId("3446");
		patient.setShrPatientDisplay("Abdul Based (OpenMRS ID: 1641D-9)");
		
		ShrPullResult result = new ShrPullResult();
		result.setPatient(patient);
		result.setRequest(request);
		result.setExecuted(executed);
		result.setMerged(bundle);
		result.setMergedFlag(false);
		result.setPagination(ShrBundleMerger.extractPaginationLinks(bundle));
		result.setTotal(ShrBundleMerger.extractTotal(bundle));
		result.setSourceUri(ShrPushMetaApplicator.resolveInstallationSourceUri());
		
		Map<String, Object> pullApiBody = ShrTimelineFormatter.format(result);
		Files.write(OUTPUT, gson.toJson(pullApiBody).getBytes(StandardCharsets.UTF_8));
		System.err.println("Wrote pull API timeline response: " + OUTPUT.toAbsolutePath());
		System.err.println(gson.toJson(pullApiBody));
		
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> cards = (List<Map<String, Object>>) pullApiBody.get("timeline");
		org.junit.Assert.assertNotNull(cards);
		org.junit.Assert.assertEquals(7, cards.size());
	}
	
	private static Map<String, String> params(String... kv) {
		Map<String, String> map = new java.util.LinkedHashMap<String, String>();
		for (int i = 0; i + 1 < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}
}
