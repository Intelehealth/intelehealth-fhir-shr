package org.openmrs.module.ihshr.pull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.EncounterStatus;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Test;
import org.openmrs.module.ihshr.fhir.ShrPushMetaApplicator;
import org.openmrs.module.ihshr.utils.CruidConstants;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import ca.uhn.fhir.context.FhirContext;

/**
 * Prints sample SHR pull API responses (resolve, query URLs, envelope, FHIR bundle). Run:
 * {@code mvn test -pl api -Dtest=ShrPullPayloadTest#printPullApiResponses}
 */
public class ShrPullPayloadTest {
	
	private static final String OPENMRS_PATIENT_UUID = "5e4b57c2-7f0b-4d63-a8bb-0c8b534d6546";
	
	private static final String SAMPLE_CRUID = "CR-2026-000001";
	
	private static final String SHR_PATIENT_ID = "shr-patient-8842";
	
	private static final String OTHER_SOURCE_URI = "https://intelehealth.org/openmrs/installation-other-facility";
	
	private final FhirContext fhirContext = FhirContext.forR4();
	
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	
	@Test
	public void printPullApiResponses() throws Exception {
		System.err.println();
		System.err.println("================================================================");
		System.err.println(" SHR Pull API — sample responses (doc §4 / §9)");
		System.err.println("================================================================");
		
		Map<String, Object> resolveResponse = sampleResolveResponse();
		printSection("GET /health-record-exchange/api/v1/shr/patients/{uuid}/resolve", resolveResponse);
		
		ShrHistoryRequest defaultRequest = ShrHistoryRequest.fromQueryParams(params("view", "default", "dateFrom",
		    "2025-05-29", "count", "50"));
		List<ShrFhirQuery> defaultQueries = ShrQueryTranslator.buildHistoryQueries(SHR_PATIENT_ID, defaultRequest);
		printSection("GET /history/{uuid}?view=default — upstream FHIR URLs", queryUrlMap(defaultQueries));
		
		ShrHistoryRequest customRequest = ShrHistoryRequest.fromQueryParams(params("view", "custom", "recordTypes",
		    "Encounter,Condition", "dateFrom", "2025-05-29"));
		List<ShrFhirQuery> customQueries = ShrQueryTranslator.buildHistoryQueries(SHR_PATIENT_ID, customRequest);
		printSection("GET /history/{uuid}?view=custom — parallel queries (merged)", queryUrlMap(customQueries));
		
		ShrFhirQuery refreshQuery = ShrQueryTranslator.buildRefreshQuery(SHR_PATIENT_ID, "2026-05-01T00:00:00Z", 50, false);
		printSection("GET /history/{uuid}/refresh?since=...", singleQueryMap(refreshQuery));
		
		String resolveUrl = ShrQueryTranslator.buildPatientResolveUrl(SAMPLE_CRUID);
		printSection("CRUID → SHR Patient lookup URL", map("url", resolveUrl));
		
		ShrPullResult defaultResult = buildDefaultTimelineResult(defaultQueries);
		Map<String, Object> defaultEnvelope = toEnvelope(defaultResult);
		printSection("GET /history/{uuid}?view=default — envelope response", defaultEnvelope);
		
		ShrPullResult customResult = buildCustomMergedResult(customQueries);
		Map<String, Object> customEnvelope = toEnvelope(customResult);
		printSection("GET /history/{uuid}?view=custom — merged envelope response", customEnvelope);
		
		String fhirOnly = encodeBundle(customResult.getMerged());
		printSection("GET /history/{uuid}?view=custom&format=fhir — raw Bundle JSON", fhirOnly);
		
		java.io.File outDir = new java.io.File("target/test-output");
		outDir.mkdirs();
		writeJson(outDir, "shr-pull-resolve-response.json", resolveResponse);
		writeJson(outDir, "shr-pull-default-envelope.json", defaultEnvelope);
		writeJson(outDir, "shr-pull-custom-envelope.json", customEnvelope);
		writeText(outDir, "shr-pull-custom-fhir-bundle.json", fhirOnly);
		writeJson(outDir, "shr-pull-query-urls.json",
		    buildQueryCatalog(defaultQueries, customQueries, refreshQuery, resolveUrl));
		
		System.err.println();
		System.err.println("Wrote samples to: " + outDir.getAbsolutePath());
		
		assertTrue(resolveUrl.contains("Patient?") && resolveUrl.contains(SAMPLE_CRUID));
		assertFalse(defaultEnvelope.isEmpty());
		assertEquals(Boolean.FALSE, ((Map<?, ?>) defaultEnvelope.get("meta")).get("merged"));
		assertEquals(Boolean.TRUE, ((Map<?, ?>) customEnvelope.get("meta")).get("merged"));
	}
	
	private ShrPullResult buildDefaultTimelineResult(List<ShrFhirQuery> queries) {
		Bundle shrBundle = sampleDefaultShrBundle();
		shrBundle.setTotal(1);
		shrBundle.addLink().setRelation("self").setUrl("http://shr/fhir/Encounter?subject=Patient/" + SHR_PATIENT_ID);
		
		List<ShrBundleMerger.ExecutedQuery> executed = new ArrayList<ShrBundleMerger.ExecutedQuery>();
		executed.add(new ShrBundleMerger.ExecutedQuery(queries.get(0), shrBundle));
		
		ShrPullResult result = new ShrPullResult();
		result.setPatient(sampleResolvedPatient());
		result.setRequest(ShrHistoryRequest.fromQueryParams(params("view", "default", "dateFrom", "2025-05-29")));
		result.setExecuted(executed);
		result.setMerged(shrBundle);
		result.setMergedFlag(false);
		result.setPagination(ShrBundleMerger.extractPaginationLinks(shrBundle));
		result.setTotal(shrBundle.getTotal());
		result.setSourceUri(ShrPushMetaApplicator.resolveInstallationSourceUri());
		return result;
	}
	
	private ShrPullResult buildCustomMergedResult(List<ShrFhirQuery> queries) {
		Bundle encounterBundle = bundleWith(sampleEncounterFromOtherFacility());
		Bundle conditionBundle = bundleWith(sampleConditionFromOtherFacility());
		
		List<ShrBundleMerger.ExecutedQuery> executed = new ArrayList<ShrBundleMerger.ExecutedQuery>();
		executed.add(new ShrBundleMerger.ExecutedQuery(queries.get(0), encounterBundle));
		executed.add(new ShrBundleMerger.ExecutedQuery(queries.get(1), conditionBundle));
		
		Bundle merged = ShrBundleMerger.merge(Arrays.asList(encounterBundle, conditionBundle));
		merged.setTotal(2);
		
		ShrPullResult result = new ShrPullResult();
		result.setPatient(sampleResolvedPatient());
		result.setRequest(ShrHistoryRequest.fromQueryParams(params("view", "custom", "recordTypes", "Encounter,Condition")));
		result.setExecuted(executed);
		result.setMerged(merged);
		result.setMergedFlag(true);
		result.setPagination(ShrBundleMerger.extractPaginationLinks(merged));
		result.setTotal(merged.getTotal());
		result.setSourceUri(ShrPushMetaApplicator.resolveInstallationSourceUri());
		return result;
	}
	
	private static Bundle sampleDefaultShrBundle() {
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.SEARCHSET);
		bundle.addEntry().setResource(sampleEncounterFromOtherFacility());
		bundle.addEntry().setResource(sampleVitalFromOtherFacility());
		bundle.addEntry().setResource(sampleProvenanceForEncounter());
		return bundle;
	}
	
	private static Encounter sampleEncounterFromOtherFacility() {
		Encounter encounter = new Encounter();
		encounter.setId("enc-other-001");
		encounter.setStatus(EncounterStatus.FINISHED);
		encounter.setSubject(new Reference("Patient/" + SHR_PATIENT_ID));
		encounter.getClass_().setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode").setCode("AMB");
		encounter.setPeriod(new org.hl7.fhir.r4.model.Period().setStart(new Date()));
		encounter.getMeta().setSource(OTHER_SOURCE_URI);
		encounter.addReasonCode(new CodeableConcept().addCoding(new Coding("http://snomed.info/sct", "386661006", "Fever")));
		return encounter;
	}
	
	private static Observation sampleVitalFromOtherFacility() {
		Observation obs = new Observation();
		obs.setId("obs-bp-001");
		obs.setStatus(ObservationStatus.FINAL);
		obs.setSubject(new Reference("Patient/" + SHR_PATIENT_ID));
		obs.setEncounter(new Reference("Encounter/enc-other-001"));
		obs.getCode().addCoding(new Coding("http://loinc.org", "85354-9", "Blood pressure panel"));
		obs.setValue(new Quantity().setValue(120).setUnit("mmHg"));
		obs.getMeta().setSource(OTHER_SOURCE_URI);
		return obs;
	}
	
	private static Condition sampleConditionFromOtherFacility() {
		Condition condition = new Condition();
		condition.setId("cond-other-001");
		condition.setSubject(new Reference("Patient/" + SHR_PATIENT_ID));
		condition.getCode().addCoding(new Coding("http://snomed.info/sct", "386661006", "Fever"));
		condition.getMeta().setSource(OTHER_SOURCE_URI);
		return condition;
	}
	
	private static Provenance sampleProvenanceForEncounter() {
		Provenance prov = new Provenance();
		prov.setId("prov-other-001");
		prov.addTarget(new Reference("Encounter/enc-other-001"));
		prov.getMeta().setSource(OTHER_SOURCE_URI);
		return prov;
	}
	
	private static Bundle bundleWith(org.hl7.fhir.r4.model.Resource... resources) {
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.SEARCHSET);
		for (org.hl7.fhir.r4.model.Resource resource : resources) {
			bundle.addEntry().setResource(resource);
		}
		return bundle;
	}
	
	private static ShrResolvedPatient sampleResolvedPatient() {
		ShrResolvedPatient patient = new ShrResolvedPatient();
		patient.setOpenmrsPatientUuid(OPENMRS_PATIENT_UUID);
		patient.setOpenmrsPatientDisplay("Jane Doe");
		patient.setCruid(SAMPLE_CRUID);
		patient.setShrPatientFound(true);
		patient.setShrPatientId(SHR_PATIENT_ID);
		patient.setShrPatientDisplay("Jane Doe");
		return patient;
	}
	
	private static Map<String, Object> sampleResolveResponse() {
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("openmrsPatientUuid", OPENMRS_PATIENT_UUID);
		body.put("openmrsPatientDisplay", "Jane Doe");
		body.put("cruid", SAMPLE_CRUID);
		body.put("shrPatientFound", true);
		body.put("shrPatientId", SHR_PATIENT_ID);
		body.put("shrPatientDisplay", "Jane Doe");
		return body;
	}
	
	private Map<String, Object> toEnvelope(ShrPullResult result) {
		Map<String, Object> envelope = new LinkedHashMap<String, Object>();
		Map<String, Object> meta = new LinkedHashMap<String, Object>();
		meta.put("sourceUri", result.getSourceUri());
		meta.put("merged", result.isMergedFlag());
		meta.put("format", result.getRequest() != null ? result.getRequest().getFormat() : "envelope");
		envelope.put("meta", meta);
		envelope.put("patient", patientMap(result.getPatient()));
		Map<String, Object> pagination = new LinkedHashMap<String, Object>();
		pagination.put("total", result.getTotal());
		pagination.put("links", result.getPagination());
		envelope.put("pagination", pagination);
		envelope.put("queries", ShrBundleMerger.toBundleSummaries(result.getExecuted()));
		if (result.getMerged() != null) {
			envelope.put("bundle", encodeBundle(result.getMerged()));
		}
		return envelope;
	}
	
	private static Map<String, Object> patientMap(ShrResolvedPatient patient) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("openmrsPatientUuid", patient.getOpenmrsPatientUuid());
		map.put("openmrsPatientDisplay", patient.getOpenmrsPatientDisplay());
		map.put("cruid", patient.getCruid());
		map.put("shrPatientFound", patient.isShrPatientFound());
		map.put("shrPatientId", patient.getShrPatientId());
		map.put("shrPatientDisplay", patient.getShrPatientDisplay());
		return map;
	}
	
	private static Map<String, Object> queryUrlMap(List<ShrFhirQuery> queries) {
		List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
		for (ShrFhirQuery query : queries) {
			Map<String, String> row = new LinkedHashMap<String, String>();
			row.put("queryId", query.getQueryId());
			row.put("resourceType", query.getResourceType());
			row.put("url", query.getUrl());
			rows.add(row);
		}
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("queries", rows);
		return body;
	}
	
	private static Map<String, Object> singleQueryMap(ShrFhirQuery query) {
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("queryId", query.getQueryId());
		body.put("resourceType", query.getResourceType());
		body.put("url", query.getUrl());
		return body;
	}
	
	private static Map<String, Object> buildQueryCatalog(List<ShrFhirQuery> defaultQueries,
	        List<ShrFhirQuery> customQueries, ShrFhirQuery refreshQuery, String resolveUrl) {
		Map<String, Object> catalog = new LinkedHashMap<String, Object>();
		catalog.put("patientResolve", resolveUrl);
		catalog.put("defaultTimeline", queryUrlMap(defaultQueries));
		catalog.put("customMultiType", queryUrlMap(customQueries));
		catalog.put("refresh", singleQueryMap(refreshQuery));
		return catalog;
	}
	
	private static Map<String, String> params(String... kv) {
		Map<String, String> map = new HashMap<String, String>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}
	
	private static Map<String, String> map(String key, String value) {
		Map<String, String> m = new LinkedHashMap<String, String>();
		m.put(key, value);
		return m;
	}
	
	private void printSection(String title, Object payload) {
		System.err.println();
		System.err.println("--- " + title + " ---");
		if (payload instanceof String) {
			System.err.println(payload);
		} else {
			System.err.println(gson.toJson(payload));
		}
	}
	
	private String encodeBundle(Bundle bundle) {
		return fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
	}
	
	private void writeJson(java.io.File dir, String name, Object payload) throws Exception {
		Files.write(new java.io.File(dir, name).toPath(), gson.toJson(payload).getBytes(StandardCharsets.UTF_8));
	}
	
	private static void writeText(java.io.File dir, String name, String text) throws Exception {
		Files.write(new java.io.File(dir, name).toPath(), text.getBytes(StandardCharsets.UTF_8));
	}
}
