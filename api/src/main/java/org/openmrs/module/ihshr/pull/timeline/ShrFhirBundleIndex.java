package org.openmrs.module.ihshr.pull.timeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

/**
 * Indexes FHIR resources from a searchset/collection bundle for reference resolution.
 */
public final class ShrFhirBundleIndex {
	
	private final Map<String, Resource> byCanonicalKey = new HashMap<String, Resource>();
	
	public ShrFhirBundleIndex(Bundle bundle) {
		if (bundle == null || !bundle.hasEntry()) {
			return;
		}
		for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
			if (entry.hasResource()) {
				index(entry.getResource());
			}
		}
	}
	
	private void index(Resource resource) {
		if (resource == null) {
			return;
		}
		String type = resource.fhirType();
		String idPart = resource.getIdElement().getIdPart();
		if (StringUtils.isNotBlank(idPart)) {
			byCanonicalKey.put(type + "/" + idPart, resource);
		}
		String fullId = resource.getIdElement().getValue();
		if (StringUtils.isNotBlank(fullId)) {
			byCanonicalKey.put(stripHistory(fullId), resource);
		}
	}
	
	public Resource resolve(Reference reference) {
		if (reference == null) {
			return null;
		}
		String key = referenceKey(reference);
		if (key == null) {
			return null;
		}
		Resource hit = byCanonicalKey.get(key);
		if (hit != null) {
			return hit;
		}
		int slash = key.lastIndexOf('/');
		if (slash >= 0) {
			String type = key.substring(0, slash);
			String id = key.substring(slash + 1);
			for (Map.Entry<String, Resource> entry : byCanonicalKey.entrySet()) {
				if (entry.getKey().endsWith("/" + id) && entry.getKey().startsWith(type)) {
					return entry.getValue();
				}
			}
		}
		return null;
	}
	
	@SuppressWarnings("unchecked")
	public <T extends Resource> T resolve(Reference reference, Class<T> type) {
		Resource resource = resolve(reference);
		if (resource != null && type.isInstance(resource)) {
			return (T) resource;
		}
		return null;
	}
	
	public static String referenceKey(Reference reference) {
		if (reference == null) {
			return null;
		}
		if (reference.hasReference()) {
			return canonicalReference(reference.getReference());
		}
		return null;
	}
	
	public static String canonicalReference(String reference) {
		if (StringUtils.isBlank(reference)) {
			return null;
		}
		String trimmed = reference.trim();
		if (trimmed.startsWith("urn:uuid:")) {
			return trimmed;
		}
		int q = trimmed.indexOf('?');
		if (q >= 0) {
			trimmed = trimmed.substring(0, q);
		}
		int hash = trimmed.indexOf("#");
		if (hash >= 0) {
			trimmed = trimmed.substring(0, hash);
		}
		if (trimmed.contains("/")) {
			String[] parts = trimmed.split("/");
			if (parts.length >= 2) {
				String type = parts[parts.length - 2];
				String id = parts[parts.length - 1];
				if (StringUtils.isNotBlank(type) && StringUtils.isNotBlank(id)) {
					return type + "/" + id;
				}
			}
		}
		return stripHistory(trimmed);
	}
	
	public static boolean referencesSame(String left, String right) {
		String a = canonicalReference(left);
		String b = canonicalReference(right);
		return a != null && a.equals(b);
	}
	
	public List<Resource> allResources() {
		Set<Resource> unique = new LinkedHashSet<Resource>();
		for (Resource resource : byCanonicalKey.values()) {
			if (resource != null) {
				unique.add(resource);
			}
		}
		return new ArrayList<Resource>(unique);
	}
	
	private static String stripHistory(String value) {
		int history = value.indexOf("/_history/");
		if (history >= 0) {
			return value.substring(0, history);
		}
		return value;
	}
}
