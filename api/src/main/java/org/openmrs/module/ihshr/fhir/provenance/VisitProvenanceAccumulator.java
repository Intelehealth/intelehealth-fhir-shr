package org.openmrs.module.ihshr.fhir.provenance;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;

/**
 * Collects Provenance targets per visit and assertion class; flushed after a sync batch (e.g. vitals
 * sent one Observation per bundle).
 */
public class VisitProvenanceAccumulator {
	
	private final Map<String, Map<ProvenanceAssertionClass, Entry>> byVisit = new LinkedHashMap<>();
	
	public void addTarget(String visitUuid, ProvenanceAssertionClass assertionClass, String targetReference,
	        String sourceObsUuid, Date occurred) {
		if (StringUtils.isBlank(visitUuid) || assertionClass == null
		        || StringUtils.isBlank(targetReference)) {
			return;
		}
		Entry entry = byVisit.computeIfAbsent(visitUuid.trim(), k -> new LinkedHashMap<>())
		        .computeIfAbsent(assertionClass, k -> new Entry());
		entry.targets.add(targetReference.trim());
		if (StringUtils.isNotBlank(sourceObsUuid) && entry.sourceObsUuid == null) {
			entry.sourceObsUuid = sourceObsUuid.trim();
		}
		if (occurred != null && entry.occurred == null) {
			entry.occurred = occurred;
		}
	}
	
	public boolean isEmpty() {
		return byVisit.isEmpty();
	}
	
	public List<PendingProvenance> drainAll() {
		List<PendingProvenance> pending = new ArrayList<>();
		for (Map.Entry<String, Map<ProvenanceAssertionClass, Entry>> visitEntry : byVisit.entrySet()) {
			String visitUuid = visitEntry.getKey();
			for (Map.Entry<ProvenanceAssertionClass, Entry> classEntry : visitEntry.getValue().entrySet()) {
				Entry e = classEntry.getValue();
				if (e.targets.isEmpty()) {
					continue;
				}
				pending.add(new PendingProvenance(visitUuid, classEntry.getKey(),
				        new ArrayList<>(e.targets), e.sourceObsUuid, e.occurred, e.recorded));
			}
		}
		byVisit.clear();
		return pending;
	}
	
	public static Bundle buildFlushBundle(PendingProvenance pending, Reference authorWho) {
		Provenance provenance = ShrProvenanceBuilder.build(pending.assertionClass, pending.visitUuid, pending.targets,
		    pending.occurred, pending.recorded != null ? pending.recorded : new Date(), pending.sourceObsUuid,
		    authorWho);
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.TRANSACTION);
		ShrProvenanceAgentBundleSupport.ensureAgentResourcesInBundle(bundle);
		ShrProvenanceAgentBundleSupport.ensurePractitionerAuthorInBundle(bundle, authorWho);
		Bundle.BundleEntryComponent entry = bundle.addEntry();
		entry.setResource(provenance);
		String provId = ShrProvenanceBuilder.toProvenanceResourceId(pending.visitUuid, pending.assertionClass);
		entry.getRequest().setMethod(Bundle.HTTPVerb.PUT).setUrl("Provenance/" + provId);
		return bundle;
	}
	
	public static final class PendingProvenance {
		
		public final String visitUuid;
		
		public final ProvenanceAssertionClass assertionClass;
		
		public final List<String> targets;
		
		public final String sourceObsUuid;
		
		public final Date occurred;
		
		public final Date recorded;
		
		PendingProvenance(String visitUuid, ProvenanceAssertionClass assertionClass, List<String> targets,
		        String sourceObsUuid, Date occurred, Date recorded) {
			this.visitUuid = visitUuid;
			this.assertionClass = assertionClass;
			this.targets = targets;
			this.sourceObsUuid = sourceObsUuid;
			this.occurred = occurred;
			this.recorded = recorded;
		}
	}
	
	private static final class Entry {
		
		private final Set<String> targets = new LinkedHashSet<>();
		
		private String sourceObsUuid;
		
		private Date occurred;
		
		private Date recorded = new Date();
	}
}
