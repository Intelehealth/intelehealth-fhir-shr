package org.openmrs.module.ihshr.pull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Resource;

/**
 * Merges multiple SHR searchset Bundles (doc §4 Phase C).
 */
public final class ShrBundleMerger {
	
	private ShrBundleMerger() {
	}
	
	public static Bundle merge(List<Bundle> bundles) {
		Bundle merged = new Bundle();
		merged.setType(Bundle.BundleType.COLLECTION);
		if (bundles == null) {
			return merged;
		}
		Set<String> seen = new HashSet<String>();
		for (Bundle source : bundles) {
			if (source == null || !source.hasEntry()) {
				continue;
			}
			for (BundleEntryComponent entry : source.getEntry()) {
				if (!entry.hasResource()) {
					continue;
				}
				Resource resource = entry.getResource();
				String key = resourceKey(resource);
				if (key == null || seen.contains(key)) {
					continue;
				}
				seen.add(key);
				BundleEntryComponent copy = merged.addEntry();
				copy.setResource(resource);
				if (entry.hasFullUrl()) {
					copy.setFullUrl(entry.getFullUrl());
				}
			}
		}
		copyPaginationLinks(bundles, merged);
		return merged;
	}
	
	public static Map<String, String> extractPaginationLinks(Bundle bundle) {
		Map<String, String> links = new HashMap<String, String>();
		if (bundle == null || !bundle.hasLink()) {
			return links;
		}
		bundle.getLink().forEach(link -> {
			if (link.hasRelation() && link.hasUrl()) {
				links.put(link.getRelation(), link.getUrl());
			}
		});
		return links;
	}
	
	private static void copyPaginationLinks(List<Bundle> sources, Bundle merged) {
		for (Bundle source : sources) {
			if (source != null && source.hasLink()) {
				merged.getLink().clear();
				merged.getLink().addAll(source.getLink());
				return;
			}
		}
	}
	
	static String resourceKey(Resource resource) {
		if (resource == null) {
			return null;
		}
		if (resource.getIdElement() != null && resource.getIdElement().hasIdPart()) {
			return resource.fhirType() + "/" + resource.getIdElement().getIdPart();
		}
		if (resource.getMeta() != null && resource.getMeta().hasVersionId()) {
			return resource.fhirType() + "/meta/" + resource.getMeta().getVersionId();
		}
		return resource.fhirType() + "/@" + System.identityHashCode(resource);
	}
	
	public static Integer extractTotal(Bundle bundle) {
		if (bundle != null && bundle.hasTotal()) {
			return bundle.getTotal();
		}
		return null;
	}
	
	public static List<Map<String, Object>> toBundleSummaries(List<ExecutedQuery> executed) {
		List<Map<String, Object>> summaries = new ArrayList<Map<String, Object>>();
		if (executed == null) {
			return summaries;
		}
		for (ExecutedQuery row : executed) {
			Map<String, Object> item = new HashMap<String, Object>();
			item.put("queryId", row.getQuery().getQueryId());
			item.put("resourceType", row.getQuery().getResourceType());
			item.put("url", row.getQuery().getUrl());
			item.put("entryCount", row.getBundle() != null && row.getBundle().hasEntry() ? row.getBundle().getEntry().size()
			        : 0);
			summaries.add(item);
		}
		return summaries;
	}
	
	public static final class ExecutedQuery {
		
		private final ShrFhirQuery query;
		
		private final Bundle bundle;
		
		public ExecutedQuery(ShrFhirQuery query, Bundle bundle) {
			this.query = query;
			this.bundle = bundle;
		}
		
		public ShrFhirQuery getQuery() {
			return query;
		}
		
		public Bundle getBundle() {
			return bundle;
		}
	}
}
