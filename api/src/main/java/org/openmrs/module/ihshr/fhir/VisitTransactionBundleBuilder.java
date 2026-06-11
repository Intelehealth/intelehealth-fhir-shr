package org.openmrs.module.ihshr.fhir;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Device;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.openmrs.module.ihshr.fhir.provenance.ProvenanceAssertionClass;
import org.openmrs.module.ihshr.fhir.provenance.ShrProvenanceAgentBundleSupport;
import org.openmrs.module.ihshr.fhir.provenance.ShrProvenanceBuilder;
import org.openmrs.module.ihshr.fhir.provenance.ShrProvenanceBundleSupport;
import org.openmrs.module.ihshr.fhir.provenance.ShrProvenanceReferences;

/**
 * Assembles one atomic FHIR {@code transaction} bundle for an entire OpenMRS visit (doc §3.1).
 */
public class VisitTransactionBundleBuilder {
	
	private final String visitUuid;
	
	private final VisitPushCruidSupport cruidSupport;
	
	private final Bundle bundle;
	
	private final Set<String> addedCruids = new LinkedHashSet<>();
	
	private final Map<String, Bundle.BundleEntryComponent> entriesByKey = new LinkedHashMap<>();
	
	private final Map<ProvenanceAssertionClass, Set<String>> provenanceTargets = new LinkedHashMap<>();
	
	private final Map<ProvenanceAssertionClass, String> provenanceSourceObsUuid = new LinkedHashMap<>();
	
	private Reference provenanceDoctorAuthor;
	
	private Reference provenanceHealthWorkerAuthor;
	
	public VisitTransactionBundleBuilder(String visitUuid, VisitPushCruidSupport cruidSupport) {
		if (StringUtils.isBlank(visitUuid)) {
			throw new IllegalArgumentException("visitUuid is required");
		}
		if (cruidSupport == null) {
			throw new IllegalArgumentException("cruidSupport is required");
		}
		this.visitUuid = visitUuid.trim();
		this.cruidSupport = cruidSupport;
		this.bundle = new Bundle();
		this.bundle.setType(Bundle.BundleType.TRANSACTION);
	}
	
	public String getVisitUuid() {
		return visitUuid;
	}
	
	public Bundle getBundle() {
		return bundle;
	}
	
	public boolean hasClinicalEntries() {
		for (Bundle.BundleEntryComponent entry : entriesByKey.values()) {
			if (entry.hasResource() && isClinicalResource(entry.getResource())) {
				return true;
			}
		}
		return false;
	}
	
	public int clinicalEntryCount() {
		int count = 0;
		for (Bundle.BundleEntryComponent entry : entriesByKey.values()) {
			if (entry.hasResource() && isClinicalResource(entry.getResource())) {
				count++;
			}
		}
		return count;
	}
	
	/**
	 * Adds or replaces a {@code PUT} entry (deduped by resource type + id or request URL).
	 */
	public boolean addPutResource(Resource resource, String logPrefix) {
		return addPutResource(resource, logPrefix, false);
	}
	
	/**
	 * @param prepend when true, inserts before existing entries so referenced parents are processed first
	 */
	public boolean addPutResource(Resource resource, String logPrefix, boolean prepend) {
		if (resource == null) {
			return false;
		}
		if (requiresPatientSubject(resource)) {
			Reference subject = ShrCruidPatientSupport.extractSubjectReference(resource);
			if (!ensurePatient(subject, logPrefix)) {
				return false;
			}
		}
		ShrPushMetaApplicator.applyPushMeta(resource);
		String putUrl = VisitPushResourceIds.putUrl(resource);
		if (StringUtils.isBlank(putUrl)) {
			return false;
		}
		Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
		entry.setResource(resource);
		entry.getRequest().setMethod(Bundle.HTTPVerb.PUT).setUrl(putUrl);
		return prepend ? prependEntry(entry) : putEntry(entry);
	}
	
	public Set<String> getIncludedEncounterIds() {
		return getEncounterResources().stream().map(VisitPushResourceIds::resolvePutResourceId)
		        .filter(StringUtils::isNotBlank).collect(Collectors.toCollection(LinkedHashSet::new));
	}
	
	public List<Encounter> getEncounterResources() {
		List<Encounter> encounters = new ArrayList<>();
		for (Bundle.BundleEntryComponent entry : entriesByKey.values()) {
			if (entry.getResource() instanceof Encounter) {
				encounters.add((Encounter) entry.getResource());
			}
		}
		return encounters;
	}
	
	/**
	 * Merges a pre-built entry (e.g. image Binary / conditional DocumentReference).
	 */
	public boolean mergeEntry(Bundle.BundleEntryComponent entry, Reference subjectForCruid, String logPrefix) {
		if (entry == null || !entry.hasResource()) {
			return false;
		}
		if (subjectForCruid != null && !ensurePatient(subjectForCruid, logPrefix)) {
			return false;
		}
		if (entry.getResource() instanceof Resource) {
			ShrPushMetaApplicator.applyPushMeta(entry.getResource());
		}
		return putEntry(entry);
	}
	
	public void registerProvenanceTarget(ProvenanceAssertionClass assertionClass, Resource resource, String sourceObsUuid) {
		if (assertionClass == null || resource == null) {
			return;
		}
		String target = ShrProvenanceBuilder.canonicalTargetReference(resource.fhirType(),
		    VisitPushResourceIds.resolvePutResourceId(resource));
		registerProvenanceTarget(assertionClass, target, sourceObsUuid);
	}
	
	public void registerProvenanceTarget(ProvenanceAssertionClass assertionClass, String targetReference,
	        String sourceObsUuid) {
		if (assertionClass == null || StringUtils.isBlank(targetReference)) {
			return;
		}
		provenanceTargets.computeIfAbsent(assertionClass, k -> new LinkedHashSet<>()).add(targetReference.trim());
		if (StringUtils.isNotBlank(sourceObsUuid) && !provenanceSourceObsUuid.containsKey(assertionClass)) {
			provenanceSourceObsUuid.put(assertionClass, sourceObsUuid.trim());
		}
	}
	
	public void setProvenanceDoctorAuthor(Reference author) {
		if (author != null && author.hasReference()) {
			this.provenanceDoctorAuthor = author;
		}
	}
	
	public void setProvenanceHealthWorkerAuthor(Reference author) {
		if (author != null && author.hasReference()) {
			this.provenanceHealthWorkerAuthor = author;
		}
	}
	
	public void captureProvenanceAuthorsFromEncounter(Encounter encounter) {
		if (encounter == null) {
			return;
		}
		Reference participant = EncounterPractitionerSupport.extractParticipantIndividualReference(encounter);
		if (participant == null) {
			return;
		}
		if (EncounterPractitionerSupport.isVisitCompleteEncounter(encounter)) {
			setProvenanceDoctorAuthor(participant);
		} else if (provenanceDoctorAuthor == null) {
			setProvenanceDoctorAuthor(participant);
		}
		if (EncounterPractitionerSupport.isVitalsEncounter(encounter)) {
			setProvenanceHealthWorkerAuthor(participant);
		} else if (provenanceHealthWorkerAuthor == null) {
			setProvenanceHealthWorkerAuthor(participant);
		}
	}
	
	public void registerProvenanceTargets(ProvenanceAssertionClass assertionClass, List<String> targetReferences,
	        String sourceObsUuid) {
		if (targetReferences == null) {
			return;
		}
		for (String target : targetReferences) {
			registerProvenanceTarget(assertionClass, target, sourceObsUuid);
		}
	}
	
	/**
	 * Appends one {@link Provenance} entry per assertion class, then returns the transaction bundle.
	 */
	public Bundle build() {
		List<Bundle.BundleEntryComponent> patientEntries = new ArrayList<>();
		for (Bundle.BundleEntryComponent existing : bundle.getEntry()) {
			if (existing.getResource() instanceof org.hl7.fhir.r4.model.Patient) {
				patientEntries.add(existing);
			}
		}
		bundle.getEntry().clear();
		for (Bundle.BundleEntryComponent patientEntry : patientEntries) {
			bundle.addEntry(patientEntry);
		}
		for (Bundle.BundleEntryComponent entry : entriesByKey.values()) {
			bundle.addEntry(entry);
		}
		Date now = new Date();
		if (!provenanceTargets.isEmpty()) {
			ShrProvenanceAgentBundleSupport.ensureAgentResourcesInBundle(bundle);
		}
		for (Map.Entry<ProvenanceAssertionClass, Set<String>> classEntry : provenanceTargets.entrySet()) {
			List<String> targets = new ArrayList<>(classEntry.getValue());
			if (targets.isEmpty()) {
				continue;
			}
			ProvenanceAssertionClass assertionClass = classEntry.getKey();
			String sourceObsUuid = provenanceSourceObsUuid.get(assertionClass);
			ShrProvenanceBundleSupport.appendProvenanceEntry(bundle, assertionClass, visitUuid, targets, now, now,
			    sourceObsUuid, resolveProvenanceAuthor(assertionClass));
		}
		ShrNarrativeSupport.stripBundleNarratives(bundle);
		return bundle;
	}
	
	private boolean putEntry(Bundle.BundleEntryComponent entry) {
		String key = VisitPushResourceIds.entryKey(entry);
		if (StringUtils.isBlank(key)) {
			return false;
		}
		entriesByKey.put(key, entry);
		return true;
	}
	
	private boolean prependEntry(Bundle.BundleEntryComponent entry) {
		String key = VisitPushResourceIds.entryKey(entry);
		if (StringUtils.isBlank(key)) {
			return false;
		}
		if (entriesByKey.containsKey(key)) {
			return false;
		}
		LinkedHashMap<String, Bundle.BundleEntryComponent> reordered = new LinkedHashMap<>();
		reordered.put(key, entry);
		reordered.putAll(entriesByKey);
		entriesByKey.clear();
		entriesByKey.putAll(reordered);
		return true;
	}
	
	private Reference resolveProvenanceAuthor(ProvenanceAssertionClass assertionClass) {
		switch (assertionClass) {
			case VISIT_COMPLETION:
			case DIAGNOSES:
			case PRESCRIPTIONS:
			case ORDERS_REFERRALS:
				if (provenanceDoctorAuthor != null) {
					return provenanceDoctorAuthor;
				}
				break;
			default:
				if (provenanceHealthWorkerAuthor != null) {
					return provenanceHealthWorkerAuthor;
				}
				if (provenanceDoctorAuthor != null) {
					return provenanceDoctorAuthor;
				}
				break;
		}
		return ShrProvenanceReferences.authorForAssertionClass(assertionClass);
	}
	
	private static boolean isClinicalResource(org.hl7.fhir.r4.model.Resource resource) {
		return !(resource instanceof Provenance) && !(resource instanceof org.hl7.fhir.r4.model.Patient)
		        && !(resource instanceof Device) && !(resource instanceof Organization)
		        && !(resource instanceof org.hl7.fhir.r4.model.Practitioner);
	}
	
	private static boolean requiresPatientSubject(Resource resource) {
		// Catalog/supporting resources referenced by clinical entries (e.g. MedicationRequest.medication).
		return !(resource instanceof Medication);
	}
	
	private boolean ensurePatient(Reference subject, String logPrefix) {
		if (subject == null || !subject.hasReference()) {
			return false;
		}
		return cruidSupport.ensurePatient(bundle, addedCruids, subject, logPrefix);
	}
}
