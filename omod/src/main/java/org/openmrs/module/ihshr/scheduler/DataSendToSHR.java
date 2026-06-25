package org.openmrs.module.ihshr.scheduler;

import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.FamilyMemberHistory;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.openmrs.module.ihmodule.api.patientexchange.config.FhirContextHolder;
import org.openmrs.module.ihshr.config.FhirConfig;
import org.openmrs.module.ihshr.datatype.EncounterType;
import org.openmrs.module.ihshr.datatype.OrderType;
import org.openmrs.module.ihmodule.api.patientexchange.domain.CompeletdVisit;
import org.openmrs.module.ihshr.domain.CompletedRecord;
import org.openmrs.module.ihshr.domain.FhirResponse;
import org.openmrs.module.ihshr.domain.ParsedExamCategory;
import org.openmrs.module.ihshr.fhir.ChiefComplaintBuildResult;
import org.openmrs.module.ihshr.fhir.ChiefComplaintTransfer;
import org.openmrs.module.ihshr.fhir.DiagnosisBuildResult;
import org.openmrs.module.ihshr.fhir.DiagnosisTransfer;
import org.openmrs.module.ihshr.fhir.EncounterLocationSupport;
import org.openmrs.module.ihshr.fhir.EncounterPractitionerSupport;
import org.openmrs.module.ihshr.fhir.FamilyHistoryBuildResult;
import org.openmrs.module.ihshr.fhir.FamilyHistoryTransfer;
import org.openmrs.module.ihshr.fhir.ImageObsTransfer;
import org.openmrs.module.ihshr.fhir.MedicalHistoryBuildResult;
import org.openmrs.module.ihshr.fhir.MedicalHistoryTransfer;
import org.openmrs.module.ihshr.fhir.FollowUpBuildResult;
import org.openmrs.module.ihshr.fhir.FollowUpTransfer;
import org.openmrs.module.ihshr.fhir.ReferralBuildResult;
import org.openmrs.module.ihshr.fhir.ReferralTransfer;
import org.openmrs.module.ihshr.backlog.UnmappedTermBacklogContext;
import org.openmrs.module.ihshr.fhir.PhysicalExamObservationBuilder;
import org.openmrs.module.ihshr.fhir.ShrCruidPatientSupport;
import org.openmrs.module.ihshr.fhir.ShrNarrativeSupport;
import org.openmrs.module.ihshr.fhir.VisitPushCruidSupport;
import org.openmrs.module.ihshr.fhir.VisitPushResourceIds;
import org.openmrs.module.ihshr.fhir.EncounterPartOfSupport;
import org.openmrs.module.ihshr.fhir.VisitTransactionBundleBuilder;
import org.openmrs.module.ihshr.fhir.provenance.ProvenanceAssertionClass;
import org.openmrs.module.ihshr.fhir.ShrIgOnlineLoader;
import org.openmrs.module.ihshr.fhir.ShrIgProfileUrls;
import org.openmrs.module.ihshr.fhir.ShrPushMetaApplicator;
import org.openmrs.module.ihshr.fhir.ShrFhirValidator;
import org.openmrs.module.ihshr.fhir.StructuredObsContextSupport;
import org.openmrs.module.ihshr.parser.ClinicalJsonValueTexts;
import org.openmrs.module.ihshr.parser.PhysicalExamParser;
import org.openmrs.module.ihshr.utils.ChiefComplaintConstants;
import org.openmrs.module.ihshr.utils.ChiefComplaintMatcher;
import org.openmrs.module.ihshr.utils.FamilyHistoryConstants;
import org.openmrs.module.ihshr.utils.FamilyHistoryMatcher;
import org.openmrs.module.ihshr.utils.MedicalHistoryConstants;
import org.openmrs.module.ihshr.utils.MedicalHistoryMatcher;
import org.openmrs.module.ihshr.utils.PhysicalExamConstants;
import org.openmrs.module.ihshr.utils.PhysicalExamMatcher;
import org.openmrs.module.ihshr.utils.FollowUpConstants;
import org.openmrs.module.ihshr.utils.FollowUpMatcher;
import org.openmrs.module.ihshr.utils.ReferralConstants;
import org.openmrs.module.ihshr.utils.ReferralMatcher;
import org.openmrs.module.ihmodule.api.patientexchange.model.DataExchangeAuditLog;
import org.openmrs.module.ihmodule.api.patientexchange.service.DataExchangeAuditLogService;
import org.openmrs.module.ihshr.config.PublishedConfigShrSyncGateService;
import org.openmrs.module.ihshr.service.CommonOperationService;
import org.openmrs.module.ihshr.synclog.IntelehealthShrSyncLog;
import org.openmrs.module.ihshr.synclog.ObsPushContext;
import org.openmrs.module.ihshr.synclog.ShrSyncLogService;
import org.openmrs.module.ihshr.synclog.ShrSyncLogStatus;
import org.openmrs.module.ihshr.synclog.ShrVisitSyncPushContract;
import org.openmrs.module.ihshr.service.ConfigDataSyncService;
import org.openmrs.module.ihshr.service.ShrMarkerAccess;
import org.openmrs.module.ihshr.utils.DateUtils;
import org.openmrs.module.ihshr.utils.DiagnosisConstants;
import org.openmrs.module.ihshr.utils.HttpWebClient;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.ihshr.utils.IHConstant;
import org.openmrs.module.ihshr.config.StructuredObsConceptSettings;
import org.openmrs.module.ihshr.utils.ImageObsConstants;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;

@Component("healthRecordDataSendToSHR")
public class DataSendToSHR extends IHConstant implements ShrVisitSyncPushContract {
	
	private FhirConfig firFhirConfig;
	
	private CommonOperationService commonOperationService;
	
	private ConfigDataSyncService configDataSyncService;
	
	private DataExchangeAuditLogService dataExchangeService;
	
	private ShrSyncLogService shrSyncLogService;
	
	private PublishedConfigShrSyncGateService publishedConfigShrSyncGateService;
	
	private void ensureDependencies() {
		if (firFhirConfig == null) {
			firFhirConfig = Context.getRegisteredComponent("ihshrFhirConfig", FhirConfig.class);
		}
		if (commonOperationService == null) {
			commonOperationService = Context.getRegisteredComponent("ihshrCommonOperationService",
			    CommonOperationService.class);
		}
		if (configDataSyncService == null) {
			configDataSyncService = Context
			        .getRegisteredComponent("ihshrConfigDataSyncService", ConfigDataSyncService.class);
		}
		if (dataExchangeService == null) {
			dataExchangeService = Context.getRegisteredComponent("dataExchangeAuditLogService",
			    DataExchangeAuditLogService.class);
		}
		if (shrSyncLogService == null) {
			shrSyncLogService = Context.getRegisteredComponent("shrSyncLogService", ShrSyncLogService.class);
		}
		if (publishedConfigShrSyncGateService == null) {
			publishedConfigShrSyncGateService = Context.getRegisteredComponent("publishedConfigShrSyncGateService",
			    PublishedConfigShrSyncGateService.class);
		}
	}
	
	// invoked by HealthRecordSyncTask via OpenMRS Scheduler
	public void syncHealthRecords() throws ParseException, UnsupportedEncodingException, DataFormatException {
		ensureDependencies();
		exportResource(Location.class);
		exportResource(Practitioner.class);
		pushCompletedVisits();
		System.err.println("(HRE) Transfer completed ............");
	}
	
	private static final int VISIT_LINK_RETRY_ATTEMPTS = 15;
	
	private static final long VISIT_LINK_RETRY_DELAY_MS = 2000L;
	
	public void syncHealthRecordsByEncounterId(Integer encounterId) throws ParseException, UnsupportedEncodingException,
	        DataFormatException {
		ensureDependencies();
		if (encounterId == null) {
			System.err.println("[VisitPush] Skipped: encounterId is null");
			return;
		}
		CompeletdVisit visit = waitForCompletedVisit(encounterId);
		if (visit == null) {
			System.err.println("[VisitPush] No eligible visit-complete encounter for encounter_id=" + encounterId
			        + " after " + VISIT_LINK_RETRY_ATTEMPTS + " attempt(s)");
			logVisitLinkDiagnostics(encounterId);
			return;
		}
		pushSingleVisit(visit);
		System.err.println("[VisitPush] Transfer completed for encounter_id=" + encounterId);
	}
	
	private CompeletdVisit waitForCompletedVisit(int encounterId) {
		CompeletdVisit visit = null;
		for (int attempt = 1; attempt <= VISIT_LINK_RETRY_ATTEMPTS; attempt++) {
			Context.clearSession();
			visit = resolveCompletedVisitForPush(encounterId);
			if (visit != null) {
				return visit;
			}
			if (attempt < VISIT_LINK_RETRY_ATTEMPTS) {
				System.err.println("[VisitPush] Visit not yet linkable for encounter_id=" + encounterId + "; retry "
				        + attempt + "/" + VISIT_LINK_RETRY_ATTEMPTS);
				try {
					Thread.sleep(VISIT_LINK_RETRY_DELAY_MS);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					return null;
				}
			}
		}
		return visit;
	}
	
	private CompeletdVisit resolveCompletedVisitForPush(int encounterId) {
		CompeletdVisit visit = resolveCompletedVisitFromOpenMrsApi(encounterId);
		if (visit != null) {
			return visit;
		}
		return commonOperationService.getCompletedVisitByEncounterIdForPush(encounterId,
		    EncounterType.VISIT_COMPLETE.getValue());
	}
	
	private CompeletdVisit resolveCompletedVisitFromOpenMrsApi(int encounterId) {
		org.openmrs.Encounter encounter = Context.getEncounterService().getEncounter(encounterId);
		if (encounter == null || Boolean.TRUE.equals(encounter.getVoided())) {
			return null;
		}
		if (encounter.getEncounterType() == null
		        || encounter.getEncounterType().getEncounterTypeId() != EncounterType.VISIT_COMPLETE.getValue()) {
			return null;
		}
		Visit visit = encounter.getVisit();
		if (visit == null || visit.getVisitId() == null) {
			return null;
		}
		Patient patient = encounter.getPatient();
		if (patient == null || StringUtils.isBlank(patient.getUuid())) {
			return null;
		}
		CompeletdVisit completedVisit = new CompeletdVisit();
		completedVisit.setVisit(visit.getUuid());
		completedVisit.setPatient(patient.getUuid());
		completedVisit.setVisitId(visit.getVisitId());
		Date updated = encounter.getDateChanged() != null ? encounter.getDateChanged() : encounter.getEncounterDatetime();
		completedVisit.setDate(updated != null ? updated.toString() : "");
		return completedVisit;
	}
	
	private void logVisitLinkDiagnostics(int encounterId) {
		try {
			Context.clearSession();
			org.openmrs.Encounter encounter = Context.getEncounterService().getEncounter(encounterId);
			if (encounter == null) {
				System.err.println("[VisitPush] Diagnostic: encounter_id=" + encounterId + " not found");
				return;
			}
			Integer typeId = encounter.getEncounterType() != null ? encounter.getEncounterType().getEncounterTypeId() : null;
			boolean hasVisit = encounter.getVisit() != null && encounter.getVisit().getVisitId() != null;
			System.err.println("[VisitPush] Diagnostic: encounter_id=" + encounterId + " type=" + typeId + " expected="
			        + EncounterType.VISIT_COMPLETE.getValue() + " hasVisit=" + hasVisit
			        + (hasVisit ? " visit_id=" + encounter.getVisit().getVisitId() : ""));
			if (encounter.getPatient() != null) {
				String mpi = commonOperationService.getMPIUsingPatientReference(encounter.getPatient().getUuid());
				System.err.println("[VisitPush] Diagnostic: patient_uuid=" + encounter.getPatient().getUuid() + " MPI="
				        + (StringUtils.isNotBlank(mpi) ? mpi : "MISSING"));
			}
		}
		catch (Exception ex) {
			System.err.println("[VisitPush] Diagnostic failed for encounter_id=" + encounterId + ": " + ex.getMessage());
		}
	}
	
	private void exportResource(Class<? extends IBaseResource> resourceType) throws ParseException,
	        UnsupportedEncodingException, DataFormatException {
		String markerName = resourceType.getSimpleName().toUpperCase() + "_EXPORT";
		String lastSyncTime = ShrMarkerAccess.lastSyncTime(markerName);
		int pageSize = 1000;
		int offset = 0;
		boolean continueFetching = true;
		System.err.println("Resource last updated at : " + lastSyncTime);
		IGenericClient client = firFhirConfig.getLocalOpenMRSFhirContext();
		
		Date lastUpdated = DateUtils.strToDate("yyyy-MM-dd HH:mm:ss", lastSyncTime);
		Date currentTime = new Date();
		
		// Time difference check (in milliseconds)
		long diffMillis = currentTime.getTime() - lastUpdated.getTime();
		long diffMinutes = diffMillis / (60 * 1000);
		
		// If last sync was less than 30 minutes ago, skip export
		if (diffMinutes < 60) {
			System.err.println("Export skipped: Last sync was only " + diffMinutes + " minutes ago.");
			return;
		}
		
		Bundle fullBundle = new Bundle();
		
		try {
			// System.out.println("Marker: "+marker);
			while (continueFetching) {
				String url = getLocalOpenmrsURL() + "/ws/fhir2/R4" + "/" + resourceType.getSimpleName() + "?_count="
				        + pageSize + "&_getpagesoffset=" + offset + "&_sort=_lastUpdated";
				
				System.out.println("URL to fetch: >>>> " + url);
				
				Bundle bundle = client.search().byUrl(url).returnBundle(Bundle.class).execute();
				
				if (bundle.hasEntry()) {
					System.out.println("Found: " + bundle.getEntry().size());
					fullBundle.getEntry().addAll(bundle.getEntry());
				}
				
				if (!bundle.hasEntry() || bundle.getEntry().size() < pageSize) {
					continueFetching = false;
				} else {
					offset += pageSize;
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
		exportBundle(fullBundle, lastSyncTime);
		System.out.println("Got full bundle size: " + fullBundle.getEntry().size());
		ShrMarkerAccess.updateLastSync(markerName);
	}
	
	public void exportBundle(Bundle originalTasksBundle, String markerLastSyncTime) throws ParseException,
	        UnsupportedEncodingException, DataFormatException {
		ensureDependencies();
		Date lastUpdated = DateUtils.strToDate("yyyy-MM-dd HH:mm:ss", markerLastSyncTime);
		int totalEntry = originalTasksBundle.getEntry().size();
		int totalSend = 0;
		if (originalTasksBundle.hasEntry()) {
			for (BundleEntryComponent bundleEntry : originalTasksBundle.getEntry()) {
				Bundle transactionBundle = new Bundle();
				transactionBundle.setType(Bundle.BundleType.TRANSACTION);
				Resource resource = (Resource) bundleEntry.getResource();
				if (resource.getMeta() != null && resource.getMeta().getLastUpdated().after(lastUpdated)) {
					ShrPushMetaApplicator.applyPushMeta(resource);
					Bundle.BundleEntryComponent component = transactionBundle.addEntry();
					component.setResource(resource);
					component.getRequest().setUrl(resource.fhirType() + "/" + resource.getIdElement().getIdPart())
					        .setMethod(Bundle.HTTPVerb.PUT);
					FhirResponse res = firFhirConfig.postTransactionBundle(transactionBundle);
					
					System.out.println(res);
					if (res.getStatusCode().contains("200") || res.getStatusCode().contains("201")) {
						totalSend++;
					}
				}
			}
		}
		System.out.println("Total send : " + totalSend + " / " + totalEntry);
	}
	
	private void transferMedication() throws UnsupportedEncodingException, DataFormatException, ParseException {
		String markerName = getExportMedication();
		List<CompletedRecord> medications = commonOperationService.getCompletedMedication(ShrMarkerAccess
		        .lastSyncTime(markerName));
		
		System.err.println("Total medication to send: " + medications.size());
		
		int medicationSendingError = 0;
		
		for (CompletedRecord theMedication : medications) {
			try {
				send("Medication", theMedication.getUuid());
			}
			catch (Exception e) {
				System.err.println(e);
				medicationSendingError++;
			}
		}
		
		System.err.format("Total Medication found: %d, Successfully Send %d, Error %d\n", medications.size(),
		    medications.size() - medicationSendingError, medicationSendingError);
		
		if (medications.size() > 0) {
			ShrMarkerAccess.updateLastSync(markerName);
		}
	}
	
	private void pushCompletedVisits() throws ParseException, UnsupportedEncodingException, DataFormatException {
		String markerName = getExportEncounter();
		List<CompeletdVisit> visits = commonOperationService.getCompletedVisit(ShrMarkerAccess.lastSyncTime(markerName),
		    EncounterType.VISIT_COMPLETE.getValue());
		System.err.println("[VisitPush] Total completed visits: " + visits.size());
		int visitErrors = 0;
		for (CompeletdVisit theVisit : visits) {
			try {
				pushSingleVisit(theVisit);
			}
			catch (Exception e) {
				System.err.println("[VisitPush] Failed visit uuid=" + theVisit.getVisit() + ": " + e.getMessage());
				e.printStackTrace();
				visitErrors++;
			}
		}
		System.err.format("[VisitPush] Visits=%d errors=%d success=%d%n", visits.size(), visitErrors, visits.size()
		        - visitErrors);
		if (!visits.isEmpty()) {
			ShrMarkerAccess.updateLastSync(markerName);
		}
	}
	
	@Override
	public boolean pushVisitForSyncLog(IntelehealthShrSyncLog pushRow, String operationLabel) {
		ensureDependencies();
		try {
			CompeletdVisit visit = commonOperationService.getCompletedVisitByVisitUuid(pushRow.getVisitUuid(),
			    EncounterType.VISIT_COMPLETE.getValue());
			if (visit == null) {
				shrSyncLogService.markFailed(pushRow, null,
				    "Completed visit not found for visit_uuid=" + pushRow.getVisitUuid(), false);
				return true;
			}
			pushSingleVisit(visit, pushRow);
			return true;
		}
		catch (IllegalStateException ex) {
			System.err.println("[VisitPush] " + operationLabel + " for visit " + pushRow.getVisitUuid() + " failed: "
			        + ex.getMessage());
			shrSyncLogService.markFailed(pushRow, null, ex.getMessage(), false);
			return true;
		}
		catch (Exception ex) {
			System.err.println("[VisitPush] " + operationLabel + " failed for sync log id=" + pushRow.getId() + ": "
			        + ex.getMessage());
			ex.printStackTrace();
			shrSyncLogService.markFailed(pushRow, null, ex.getMessage(), false);
			return true;
		}
	}
	
	private void pushSingleVisit(CompeletdVisit theVisit) throws ParseException, UnsupportedEncodingException,
	        DataFormatException {
		pushSingleVisit(theVisit, null);
	}
	
	/**
	 * Builds one visit-scoped FHIR transaction bundle and enqueues or replays SHR push via sync
	 * log.
	 * <p>
	 * Entry points: {@link #syncHealthRecordsByEncounterId} (visit-complete event / manual),
	 * {@link #pushVisitForSyncLog} (retry from {@code intelehealth_shr_sync_log}). When
	 * {@code existingSyncLog} is null, {@link #enqueueVisitBundlePush} creates a PENDING row then
	 * posts if {@code fhir_module.shr} is enabled; otherwise
	 * {@link #pushRegeneratedBundleForSyncLog} refreshes the stored bundle on replay.
	 */
	private void pushSingleVisit(CompeletdVisit theVisit, IntelehealthShrSyncLog existingSyncLog) throws ParseException,
	        UnsupportedEncodingException, DataFormatException {
		String visitUuid = theVisit.getVisit();
		ObsPushContext pushContext = commonOperationService.findVisitPushContext(theVisit.getVisitId(), visitUuid);
		VisitPushCruidSupport cruidSupport = (bundle, addedCruids, subject, logPrefix) -> ensureCruidPatientInBundle(bundle,
		    addedCruids, subject, logPrefix);
		VisitTransactionBundleBuilder builder = new VisitTransactionBundleBuilder(visitUuid, cruidSupport);
		
		// Parent visit Encounter + visit-complete child; then all clinical child encounters for this visit.
		String visitCompleteEncounterUuid = pushContext.getTriggerEncounterUuid();
		ensureVisitParentEncounterInBuilder(builder, visitUuid, visitCompleteEncounterUuid);
		if (visitCompleteEncounterUuid != null) {
			addEncounterResourceToVisitBuilder(builder, visitCompleteEncounterUuid, true);
		}
		
		List<CompletedRecord> childEncounters = commonOperationService.getCompletedEncounter(theVisit.getVisitId());
		HashSet<Integer> encounterIds = new HashSet<>();
		for (CompletedRecord enc : childEncounters) {
			encounterIds.add(enc.getId());
			addEncounterResourceToVisitBuilder(builder, enc.getUuid(), false);
		}
		resolveEncounterPartOfReferences(builder);
		includeAllEncounterReferencedResources(builder);
		
		if (!encounterIds.isEmpty()) {
			populateVisitObservations(builder, encounterIds, visitUuid);
			populateVisitOrders(builder, new ArrayList<>(encounterIds));
		}
		
		if (!builder.hasClinicalEntries()) {
			System.err.println("[VisitPush] Skip empty bundle for visit=" + visitUuid);
			if (existingSyncLog != null) {
				shrSyncLogService.markFailed(existingSyncLog, null, "Empty visit bundle on sync replay", false);
			}
			return;
		}
		
		Bundle transactionBundle = builder.build();
		validateOrThrow(transactionBundle, "[VisitPush]");
		String finalPayload = newJsonParser().setPrettyPrint(true).encodeResourceToString(transactionBundle);
		System.err.println("[VisitPush] Posting visit=" + visitUuid + " clinicalEntries=" + builder.clinicalEntryCount()
		        + " bundleEntries=" + transactionBundle.getEntry().size());
		System.err.println("[VisitPush] Final FHIR transaction bundle (visit=" + visitUuid + "):");
		System.err.println("-------------------------------------------------------------------------------------");
		System.err.println(finalPayload);
		if (existingSyncLog != null) {
			pushRegeneratedBundleForSyncLog(transactionBundle, pushContext, existingSyncLog, "[VisitPush]");
		}
		else {
			enqueueVisitBundlePush(transactionBundle, pushContext, "[VisitPush]");
		}
	}
	
	private void addEncounterResourceToVisitBuilder(VisitTransactionBundleBuilder builder, String encounterUuid,
	        boolean visitCompletion) throws ParseException, UnsupportedEncodingException, DataFormatException {
		Encounter encounter = fetchSourceEncounter(encounterUuid);
		if (encounter == null) {
			System.err.println("[VisitPush] Encounter not found uuid=" + encounterUuid);
			return;
		}
		validateOrThrow(encounter, "[Encounter]");
		if (!builder.addPutResource(encounter, "[Encounter]")) {
			return;
		}
		builder.captureProvenanceAuthorsFromEncounter(encounter);
		if (visitCompletion) {
			builder.registerProvenanceTarget(ProvenanceAssertionClass.VISIT_COMPLETION, encounter, null);
		}
	}
	
	/**
	 * OpenMRS FHIR2 sets child {@code Encounter.partOf} to {@code Encounter/ visit.uuid} . That id
	 * is the visit table uuid, not necessarily an encounter row, so we ensure a parent Encounter
	 * exists in the bundle before children.
	 */
	private void ensureVisitParentEncounterInBuilder(VisitTransactionBundleBuilder builder, String visitUuid,
	        String templateEncounterUuid) throws ParseException, UnsupportedEncodingException, DataFormatException {
		if (builder.getIncludedEncounterIds().contains(visitUuid)) {
			return;
		}
		Encounter parent = fetchSourceEncounter(visitUuid);
		if (parent != null) {
			validateOrThrow(parent, "[Encounter]");
			if (builder.addPutResource(parent, "[Encounter]", true)) {
				System.err.println("[VisitPush] Included visit parent Encounter from FHIR2 id=" + visitUuid);
			}
			return;
		}
		Encounter template = StringUtils.isNotBlank(templateEncounterUuid) ? fetchSourceEncounter(templateEncounterUuid)
		        : null;
		parent = EncounterPartOfSupport.buildVisitParentPlaceholder(visitUuid, template);
		validateOrThrow(parent, "[Encounter]");
		if (builder.addPutResource(parent, "[Encounter]", true)) {
			System.err.println("[VisitPush] Added visit-level parent Encounter placeholder id=" + visitUuid);
		}
	}
	
	private void resolveEncounterPartOfReferences(VisitTransactionBundleBuilder builder) throws ParseException,
	        UnsupportedEncodingException, DataFormatException {
		Set<String> included = builder.getIncludedEncounterIds();
		Set<String> missingParents = new LinkedHashSet<>();
		for (String parentId : EncounterPartOfSupport.collectPartOfParentIds(builder.getEncounterResources())) {
			if (!included.contains(parentId)) {
				missingParents.add(parentId);
			}
		}
		for (String parentId : missingParents) {
			System.err.println("[VisitPush] Resolving missing partOf parent Encounter id=" + parentId);
			addEncounterResourceToVisitBuilder(builder, parentId, false);
			included = builder.getIncludedEncounterIds();
			if (!included.contains(parentId)) {
				Encounter template = builder.getEncounterResources().isEmpty() ? null
				        : builder.getEncounterResources().get(0);
				Encounter placeholder = EncounterPartOfSupport.buildVisitParentPlaceholder(parentId, template);
				validateOrThrow(placeholder, "[Encounter]");
				builder.addPutResource(placeholder, "[Encounter]", true);
				included = builder.getIncludedEncounterIds();
			}
		}
		included = builder.getIncludedEncounterIds();
		for (Encounter encounter : builder.getEncounterResources()) {
			EncounterPartOfSupport.stripUnresolvedPartOf(encounter, included);
		}
	}
	
	private void includeAllEncounterReferencedResources(VisitTransactionBundleBuilder builder) throws ParseException,
	        UnsupportedEncodingException, DataFormatException {
		for (Encounter encounter : builder.getEncounterResources()) {
			includeReferencedEncounterResources(builder, encounter);
		}
	}
	
	private void includeReferencedEncounterResources(VisitTransactionBundleBuilder builder, Encounter encounter)
	        throws ParseException, UnsupportedEncodingException, DataFormatException {
		if (encounter == null) {
			return;
		}
		Reference practitionerRef = EncounterPractitionerSupport.extractParticipantIndividualReference(encounter);
		if (practitionerRef != null) {
			includeReferencedSupportingResource(builder, practitionerRef, "Practitioner", "[Practitioner]");
		}
		for (Reference locationRef : EncounterLocationSupport.extractLocationReferences(encounter)) {
			includeReferencedSupportingResource(builder, locationRef, "Location", "[Location]");
		}
	}
	
	private void includeReferencedSupportingResource(VisitTransactionBundleBuilder builder, Reference reference,
	        String resourceType, String logPrefix) throws ParseException, UnsupportedEncodingException, DataFormatException {
		String resourceId = referenceResourceId(reference);
		if (StringUtils.isBlank(resourceId)) {
			return;
		}
		if (builder.hasPutResource(resourceType, resourceId)) {
			return;
		}
		Resource resource = fetchFhirResource(resourceType, resourceId);
		if (resource == null) {
			System.err.println("[VisitPush] Encounter references " + resourceType + "/" + resourceId
			        + " but resource was not found in local FHIR2");
			return;
		}
		validateOrThrow(resource, logPrefix);
		if (!builder.addPutResource(resource, logPrefix, true)) {
			System.err.println("[VisitPush] Failed to include " + resourceType + "/" + resourceId
			        + " referenced by an Encounter in the visit bundle");
		}
	}
	
	private void addFhirResourceToVisitBuilder(VisitTransactionBundleBuilder builder, String resourceType, String uuid,
	        ProvenanceAssertionClass provenanceClass) throws ParseException, UnsupportedEncodingException,
	        DataFormatException {
		Resource resource = fetchFhirResource(resourceType, uuid);
		if (resource == null) {
			return;
		}
		if (resource instanceof MedicationRequest) {
			includeReferencedMedication(builder, (MedicationRequest) resource);
		}
		validateOrThrow(resource, "[" + resourceType + "]");
		if (!builder.addPutResource(resource, "[" + resourceType + "]")) {
			return;
		}
		if (provenanceClass != null) {
			builder.registerProvenanceTarget(provenanceClass, resource, null);
		}
	}
	
	private void includeReferencedMedication(VisitTransactionBundleBuilder builder, MedicationRequest medicationRequest)
	        throws ParseException, UnsupportedEncodingException, DataFormatException {
		if (medicationRequest == null || !medicationRequest.hasMedicationReference()) {
			return;
		}
		String medicationId = referenceResourceId(medicationRequest.getMedicationReference());
		if (StringUtils.isBlank(medicationId)) {
			return;
		}
		Resource medication = fetchFhirResource("Medication", medicationId);
		if (medication == null) {
			System.err.println("[VisitPush] MedicationRequest/" + medicationRequest.getIdElement().getIdPart()
			        + " references Medication/" + medicationId + " but medication was not found in local FHIR2");
			return;
		}
		validateOrThrow(medication, "[Medication]");
		if (!builder.addPutResource(medication, "[Medication]", true)) {
			System.err.println("[VisitPush] Failed to include Medication/" + medicationId + " for MedicationRequest/"
			        + medicationRequest.getIdElement().getIdPart());
		}
	}
	
	private static String referenceResourceId(Reference reference) {
		if (reference == null || StringUtils.isBlank(reference.getReference())) {
			return null;
		}
		String ref = reference.getReference().trim();
		int slash = ref.lastIndexOf('/');
		return slash >= 0 ? ref.substring(slash + 1) : ref;
	}
	
	private Resource fetchFhirResource(String resourceType, String uuid) throws ParseException,
	        UnsupportedEncodingException, DataFormatException {
		String data = HttpWebClient.get(getLocalOpenmrsURL(), "/ws/fhir2/R4/" + resourceType + "?_id=" + uuid,
		    firFhirConfig.getOpenMRSCredentials()[0], firFhirConfig.getOpenMRSCredentials()[1]);
		Bundle searchBundle = newJsonParser().parseResource(Bundle.class, data);
		if (!searchBundle.hasEntry()) {
			return null;
		}
		return searchBundle.getEntryFirstRep().getResource();
	}
	
	private void populateVisitOrders(VisitTransactionBundleBuilder builder, List<Integer> encounterIds)
	        throws ParseException, UnsupportedEncodingException, DataFormatException {
		if (encounterIds == null || encounterIds.isEmpty()) {
			return;
		}
		List<CompletedRecord> serviceRequests = commonOperationService.getCompletedServiceRequest(encounterIds,
		    OrderType.LAB_ORDER.getValue());
		for (CompletedRecord row : serviceRequests) {
			addFhirResourceToVisitBuilder(builder, "ServiceRequest", row.getUuid(),
			    ProvenanceAssertionClass.ORDERS_REFERRALS);
		}
		List<CompletedRecord> medicationRequests = commonOperationService.getCompletedServiceRequest(encounterIds,
		    OrderType.DRUG_ORDER.getValue());
		for (CompletedRecord row : medicationRequests) {
			addFhirResourceToVisitBuilder(builder, "MedicationRequest", row.getUuid(),
			    ProvenanceAssertionClass.PRESCRIPTIONS);
		}
	}
	
	private void populateVisitObservations(VisitTransactionBundleBuilder builder, HashSet<Integer> encounterIds,
	        String visitUuid) throws ParseException, UnsupportedEncodingException, DataFormatException {
		
		List<ArrayList<Integer>> partitions = getPartitions(encounterIds, 25);
		
		for (ArrayList<Integer> subset : partitions) {
			commonOperationService.logObsDiagnosticsForEncounters(subset);
			List<CompletedRecord> obs = commonOperationService.getCompletedObs(subset);
			Map<Integer, List<CompletedRecord>> diagnosisObsByEncounter = new LinkedHashMap<Integer, List<CompletedRecord>>();
			System.err.println("[Observation] Loaded " + obs.size() + " non-voided obs for encounter_id(s) " + subset);
			for (CompletedRecord theObs : obs) {
				try {
					int conceptId = theObs.getConceptId();
					// 163202 = medication, 163206 = test — handled elsewhere, not as visit observations
					if (conceptId == 163206 || conceptId == 163202) {
						continue;
					}
					boolean familyHistory = isFamilyHistory(theObs);
					boolean medicalHistory = !familyHistory && isMedicalHistory(theObs);
					boolean physicalExam = !familyHistory && !medicalHistory && isPhysicalExam(theObs);
					boolean chiefComplaint = !familyHistory && !medicalHistory && !physicalExam && isChiefComplaint(theObs);
					boolean diagnosis = !familyHistory && !medicalHistory && !physicalExam && !chiefComplaint
					        && isDiagnosis(theObs);
					boolean referral = !familyHistory && !medicalHistory && !physicalExam && !chiefComplaint && !diagnosis
					        && isReferral(theObs);
					boolean followUp = !familyHistory && !medicalHistory && !physicalExam && !chiefComplaint && !diagnosis
					        && !referral && isFollowUp(theObs);
					boolean imageObs = !familyHistory && !medicalHistory && !physicalExam && !chiefComplaint && !diagnosis
					        && !referral && !followUp && isImageObs(theObs);
					int valueTextLen = theObs.getValueText() == null ? 0 : theObs.getValueText().length();
					System.err.println("[Observation] uuid=" + theObs.getUuid() + " conceptId=" + theObs.getConceptId()
					        + " valueTextLen=" + valueTextLen + " familyHistory=" + familyHistory + " medicalHistory="
					        + medicalHistory + " physicalExam=" + physicalExam + " chiefComplaint=" + chiefComplaint
					        + " diagnosis=" + diagnosis + " referral=" + referral + " followUp=" + followUp + " imageObs="
					        + imageObs + " match=" + describeObservationMatch(theObs));
					if (familyHistory) {
						System.err.println("[FamilyHistory] Routing obs_id=" + theObs.getId() + " uuid=" + theObs.getUuid());
						addFamilyHistoryToVisitBuilder(builder, theObs.getUuid(), theObs.getValueText());
					} else if (medicalHistory) {
						System.err
						        .println("[MedicalHistory] Routing obs_id=" + theObs.getId() + " uuid=" + theObs.getUuid());
						addMedicalHistoryToVisitBuilder(builder, theObs.getUuid(), theObs.getValueText());
					} else if (physicalExam) {
						System.err.println("[PhysicalExam] Routing obs_id=" + theObs.getId() + " uuid=" + theObs.getUuid()
						        + " conceptId=" + theObs.getConceptId());
						addPhysicalExamToVisitBuilder(builder, theObs.getUuid(), theObs.getValueText());
					} else if (chiefComplaint) {
						System.err.println("[ChiefComplaint] Routing obs_id=" + theObs.getId() + " uuid=" + theObs.getUuid()
						        + " conceptId=" + theObs.getConceptId()
						        + " — chief complaint (raw HTML or en JSON / concept 163212)");
						addChiefComplaintToVisitBuilder(builder, theObs.getUuid(), theObs.getValueText());
					} else if (diagnosis) {
						Integer encounterId = theObs.getEncounterId();
						if (encounterId == null) {
							System.err.println("[Diagnosis] Skipping obs without encounter_id, uuid=" + theObs.getUuid());
							continue;
						}
						if (!diagnosisObsByEncounter.containsKey(encounterId)) {
							diagnosisObsByEncounter.put(encounterId, new ArrayList<CompletedRecord>());
						}
						diagnosisObsByEncounter.get(encounterId).add(theObs);
						System.err.println("[Diagnosis] Queued obs_id=" + theObs.getId() + " uuid=" + theObs.getUuid()
						        + " encounter_id=" + encounterId + " for grouped encounter diagnosis update");
					} else if (referral) {
						System.err.println("[Referral] Routing obs_id=" + theObs.getId() + " uuid=" + theObs.getUuid()
						        + " conceptId=" + theObs.getConceptId());
						addReferralToVisitBuilder(builder, theObs.getUuid(), theObs.getValueText());
					} else if (followUp) {
						System.err.println("[FollowUp] Routing obs_id=" + theObs.getId() + " uuid=" + theObs.getUuid()
						        + " conceptId=" + theObs.getConceptId());
						addFollowUpToVisitBuilder(builder, theObs.getUuid(), theObs.getValueText());
					} else if (imageObs) {
						addComplexImageToVisitBuilder(builder, theObs);
					} else {
						addFhirResourceToVisitBuilder(builder, "Observation", theObs.getUuid(),
						    ProvenanceAssertionClass.VITALS);
					}
				}
				catch (Exception e) {
					System.err.println(e);
					throw e instanceof RuntimeException ? (RuntimeException) e : new IllegalStateException(e);
				}
			}
			
			for (Map.Entry<Integer, List<CompletedRecord>> e : diagnosisObsByEncounter.entrySet()) {
				List<CompletedRecord> diagnosisRows = e.getValue();
				if (diagnosisRows == null || diagnosisRows.isEmpty()) {
					continue;
				}
				addDiagnosisToVisitBuilder(builder, diagnosisRows);
			}
		}
	}
	
	private boolean isFamilyHistory(CompletedRecord theObs) {
		return FamilyHistoryMatcher.matchesConceptId(theObs.getConceptId())
		        || commonOperationService.isFamilyHistoryConceptId(theObs.getConceptId());
	}
	
	private boolean isMedicalHistory(CompletedRecord theObs) {
		return MedicalHistoryMatcher.matchesConceptId(theObs.getConceptId())
		        || commonOperationService.isMedicalHistoryConceptId(theObs.getConceptId());
	}
	
	private boolean isPhysicalExam(CompletedRecord theObs) {
		return PhysicalExamMatcher.matchesConceptId(theObs.getConceptId())
		        || commonOperationService.isPhysicalExamConceptId(theObs.getConceptId());
	}
	
	private boolean isChiefComplaint(CompletedRecord theObs) {
		return isChiefComplaintByConcept(theObs);
	}
	
	private boolean isChiefComplaintByConcept(CompletedRecord theObs) {
		return ChiefComplaintMatcher.matchesConceptId(theObs.getConceptId())
		        || commonOperationService.isChiefComplaintConceptId(theObs.getConceptId());
	}
	
	private boolean isDiagnosis(CompletedRecord theObs) {
		return commonOperationService.isDiagnosisConceptId(theObs.getConceptId());
	}
	
	private boolean isReferral(CompletedRecord theObs) {
		return ReferralMatcher.isReferralObs(theObs);
	}
	
	private boolean isFollowUp(CompletedRecord theObs) {
		return FollowUpMatcher.isFollowUpObs(theObs);
	}
	
	private boolean isImageObs(CompletedRecord theObs) {
		Integer conceptId = theObs.getConceptId();
		return conceptId != null && StructuredObsConceptSettings.imageConceptIds().contains(conceptId);
	}
	
	private String describeObservationMatch(CompletedRecord theObs) {
		Integer conceptId = theObs.getConceptId();
		if (FamilyHistoryMatcher.matchesConceptId(conceptId) || commonOperationService.isFamilyHistoryConceptId(conceptId)) {
			return FamilyHistoryMatcher.describeMatch(theObs);
		}
		if (MedicalHistoryMatcher.matchesConceptId(conceptId) || commonOperationService.isMedicalHistoryConceptId(conceptId)) {
			return MedicalHistoryMatcher.describeMatch(theObs);
		}
		if (isChiefComplaintByConcept(theObs)) {
			return ChiefComplaintMatcher.describeMatch(theObs);
		}
		if (conceptId != null && conceptId.intValue() == PhysicalExamConstants.PHYSICAL_EXAM_CONCEPT_ID) {
			return "physical-exam-concept-163213";
		}
		if (commonOperationService.isPhysicalExamConceptId(conceptId)) {
			return "physical-exam-conceptId=" + conceptId;
		}
		if (isDiagnosis(theObs)) {
			return "diagnosis-concept-163219";
		}
		if (isReferral(theObs)) {
			return ReferralMatcher.describeMatch(theObs);
		}
		if (matchesFollowUpConcept(theObs)) {
			return FollowUpMatcher.describeMatch(theObs);
		}
		if (isImageObs(theObs)) {
			return "image-obs-complex-conceptId=" + theObs.getConceptId();
		}
		return "none";
	}
	
	/**
	 * Pushes one physical-exam obs (concept 163213) as one FHIR {@code Observation} per parsed
	 * category (e.g. Eyes, Cardiovascular), each with finding {@code component}s. Routed from
	 * {@link #populateVisitObservations} when the obs concept matches physical examination.
	 */
	private void addPhysicalExamToVisitBuilder(VisitTransactionBundleBuilder builder, String obsUuid,
	        String cachedValueText) throws ParseException, UnsupportedEncodingException, DataFormatException {
		System.err.println("[PhysicalExam] Fetching source Observation from OpenMRS, uuid=" + obsUuid);
		String data = HttpWebClient.get(getLocalOpenmrsURL(), "/ws/fhir2/R4/Observation?_id=" + obsUuid,
		    firFhirConfig.getOpenMRSCredentials()[0], firFhirConfig.getOpenMRSCredentials()[1]);
		Bundle theBundle = newJsonParser().parseResource(Bundle.class, data);
		System.err.println("[PhysicalExam] FHIR search bundle entries: "
		        + (theBundle.hasEntry() ? theBundle.getEntry().size() : 0));
		
		Observation sourceObs = null;
		if (theBundle.hasEntry()) {
			for (BundleEntryComponent entry : theBundle.getEntry()) {
				if (entry.getResource() instanceof Observation) {
					sourceObs = (Observation) entry.getResource();
					break;
				}
			}
		}
		if (sourceObs == null) {
			System.err.println("[PhysicalExam] No Observation in bundle; skipping, uuid=" + obsUuid);
			return;
		}
		
		// Prefer queued value_text from the visit row; fall back to DB then FHIR valueString.
		String valueText = cachedValueText;
		if (StringUtils.isBlank(valueText)) {
			valueText = commonOperationService.getObsValueText(obsUuid);
		}
		if (StringUtils.isBlank(valueText) && sourceObs.hasValueStringType()) {
			valueText = sourceObs.getValueStringType().getValueAsString();
			System.err.println("[PhysicalExam] Using valueString from FHIR Observation");
		}
		
		List<ParsedExamCategory> categories = new PhysicalExamParser().parse(valueText);
		System.err.println("[PhysicalExam] Parsed categories: " + categories.size() + " uuid=" + obsUuid);
		if (categories.isEmpty()) {
			System.err.println("[PhysicalExam] No categories parsed; skipping, uuid=" + obsUuid);
			return;
		}
		
		// Full stripped obs text attached as note on every category Observation.
		String sharedNote = PhysicalExamObservationBuilder.stripHtmlForNote(valueText);
		System.err.println("[PhysicalExam] Shared note length=" + sharedNote.length());
		StructuredObsContextSupport.populateBacklogContext(sourceObs, obsUuid,
		    PhysicalExamConstants.PHYSICAL_EXAM_CONCEPT_ID);
		try {
			PhysicalExamObservationBuilder examBuilder = new PhysicalExamObservationBuilder();
			List<Observation> categoryObservations = new ArrayList<>();
			int categoryIndex = 0;
			for (ParsedExamCategory category : categories) {
				categoryIndex++;
				Observation categoryObs = examBuilder.build(sourceObs, obsUuid, category, sharedNote);
				categoryObservations.add(categoryObs);
				String identifier = categoryObs.hasIdentifier() ? categoryObs.getIdentifierFirstRep().getValue() : obsUuid;
				System.err.println("[PhysicalExam] Prepared category " + categoryIndex + "/" + categories.size()
				        + " identifier=" + identifier + " components=" + categoryObs.getComponent().size());
			}
			// Physical exam exports Observations only (no Conditions); empty list satisfies shared helper signature.
			addStructuredResourcesToVisitBuilder(builder, new ArrayList<Condition>(), categoryObservations, obsUuid,
			    ProvenanceAssertionClass.PHYSICAL_EXAMINATION, "[PhysicalExam]");
			System.err.println("[PhysicalExam] Done uuid=" + obsUuid + " added " + categories.size()
			        + " category Observation(s)");
		}
		finally {
			UnmappedTermBacklogContext.clear();
		}
	}
	
	private boolean matchesFollowUpConcept(CompletedRecord theObs) {
		return FollowUpMatcher.matchesConceptId(theObs.getConceptId());
	}
	
	private void addFollowUpToVisitBuilder(VisitTransactionBundleBuilder builder, String obsUuid, String cachedValueText)
	        throws ParseException, UnsupportedEncodingException, DataFormatException {
		System.err.println("[FollowUp] Fetching source Observation from OpenMRS, uuid=" + obsUuid);
		Observation sourceObs = fetchSourceObservation(obsUuid, "[FollowUp]");
		if (sourceObs == null) {
			System.err.println("[FollowUp] No Observation in bundle; skipping, uuid=" + obsUuid);
			return;
		}
		String valueText = resolveValueText(obsUuid, cachedValueText, sourceObs, "[FollowUp]");
		FollowUpBuildResult built = new FollowUpTransfer().build(sourceObs, obsUuid, valueText);
		if (!built.hasObservation()) {
			System.err.println("[FollowUp] Nothing parsed or denied; skipping, uuid=" + obsUuid);
			return;
		}
		addClinicalResourcesToVisitBuilder(builder, java.util.Collections.<Resource> singletonList(built.getObservation()),
		    obsUuid, ProvenanceAssertionClass.VITALS, "[FollowUp]");
		System.err.println("[FollowUp] Done uuid=" + obsUuid);
	}
	
	private void addReferralToVisitBuilder(VisitTransactionBundleBuilder builder, String obsUuid, String cachedValueText)
	        throws ParseException, UnsupportedEncodingException, DataFormatException {
		System.err.println("[Referral] Fetching source Observation from OpenMRS, uuid=" + obsUuid);
		Observation sourceObs = fetchSourceObservation(obsUuid, "[Referral]");
		if (sourceObs == null) {
			System.err.println("[Referral] No Observation in bundle; skipping, uuid=" + obsUuid);
			return;
		}
		String valueText = resolveValueText(obsUuid, cachedValueText, sourceObs, "[Referral]");
		StructuredObsContextSupport.populateBacklogContext(sourceObs, obsUuid, ReferralConstants.REFERRAL_CONCEPT_ID);
		ReferralBuildResult built;
		try {
			built = new ReferralTransfer().build(sourceObs, obsUuid, valueText);
		}
		finally {
			UnmappedTermBacklogContext.clear();
		}
		System.err.println("[Referral] Built " + built.totalResourceCount() + " ServiceRequest(s), uuid=" + obsUuid);
		if (built.totalResourceCount() == 0) {
			System.err.println("[Referral] Nothing parsed; skipping, uuid=" + obsUuid);
			return;
		}
		addClinicalResourcesToVisitBuilder(builder, new ArrayList<Resource>(built.getServiceRequests()), obsUuid,
		    ProvenanceAssertionClass.ORDERS_REFERRALS, "[Referral]");
		System.err.println("[Referral] Done uuid=" + obsUuid + " added " + built.totalResourceCount() + " resource(s)");
	}
	
	/**
	 * Pushes one chief-complaint obs (concept 163212) as FHIR {@code Condition}(s) for each parsed
	 * symptom plus optional associated-symptom {@code Observation}(s) linked via {@code focus}.
	 * Routed from {@link #populateVisitObservations} when the obs concept matches chief complaint.
	 */
	private void addChiefComplaintToVisitBuilder(VisitTransactionBundleBuilder builder, String obsUuid,
	        String cachedValueText) throws ParseException, UnsupportedEncodingException, DataFormatException {
		System.err.println("[ChiefComplaint] Fetching source Observation from OpenMRS, uuid=" + obsUuid);
		String data = HttpWebClient.get(getLocalOpenmrsURL(), "/ws/fhir2/R4/Observation?_id=" + obsUuid,
		    firFhirConfig.getOpenMRSCredentials()[0], firFhirConfig.getOpenMRSCredentials()[1]);
		Bundle theBundle = newJsonParser().parseResource(Bundle.class, data);
		System.err.println("[ChiefComplaint] FHIR search bundle entries: "
		        + (theBundle.hasEntry() ? theBundle.getEntry().size() : 0));
		
		Observation sourceObs = null;
		if (theBundle.hasEntry()) {
			for (BundleEntryComponent entry : theBundle.getEntry()) {
				if (entry.getResource() instanceof Observation) {
					sourceObs = (Observation) entry.getResource();
					break;
				}
			}
		}
		if (sourceObs == null) {
			System.err.println("[ChiefComplaint] No Observation in bundle; skipping, uuid=" + obsUuid);
			return;
		}
		
		// Prefer queued value_text from the visit row; fall back to DB then FHIR valueString.
		String valueText = cachedValueText;
		if (StringUtils.isBlank(valueText)) {
			valueText = commonOperationService.getObsValueText(obsUuid);
		}
		if (StringUtils.isBlank(valueText) && sourceObs.hasValueStringType()) {
			valueText = sourceObs.getValueStringType().getValueAsString();
			System.err.println("[ChiefComplaint] Using valueString from FHIR Observation");
		}
		
		// Layer 1 backlog: record unmapped symptom terms during ClinicalTermCodingResolver lookups.
		StructuredObsContextSupport.populateBacklogContext(sourceObs, obsUuid,
		    ChiefComplaintConstants.CHIEF_COMPLAINT_CONCEPT_ID);
		ChiefComplaintBuildResult built;
		try {
			built = new ChiefComplaintTransfer().build(sourceObs, obsUuid, valueText);
		}
		finally {
			UnmappedTermBacklogContext.clear();
		}
		System.err.println("[ChiefComplaint] Built " + built.getConditions().size() + " Condition(s), "
		        + built.getAssociatedSymptomObservations().size() + " associated Observation(s), uuid=" + obsUuid);
		if (built.totalResourceCount() == 0) {
			System.err.println("[ChiefComplaint] Nothing parsed; skipping, uuid=" + obsUuid);
			return;
		}
		if (built.isOrphanAssociatedSymptoms()) {
			System.err
			        .println("[ChiefComplaint] WARNING: Associated symptoms block without chief-complaint symptoms, obs uuid="
			                + obsUuid);
		}
		
		addStructuredResourcesToVisitBuilder(builder, built.getConditions(), built.getAssociatedSymptomObservations(),
		    obsUuid, ProvenanceAssertionClass.CHIEF_COMPLAINT, "[ChiefComplaint]");
		System.err
		        .println("[ChiefComplaint] Done uuid=" + obsUuid + " added " + built.totalResourceCount() + " resource(s)");
	}
	
	/**
	 * Pushes all diagnosis obs (concept 163219) for one OpenMRS encounter as FHIR {@code Condition}
	 * resources plus a single updated {@code Encounter} with {@code diagnosis[]} (ranked
	 * primary/secondary). Rows are pre-grouped by {@code encounter_id} in
	 * {@link #populateVisitObservations}; this method runs once per group.
	 */
	private void addDiagnosisToVisitBuilder(VisitTransactionBundleBuilder builder, List<CompletedRecord> diagnosisRows)
	        throws ParseException, UnsupportedEncodingException, DataFormatException {
		if (diagnosisRows == null || diagnosisRows.isEmpty()) {
			return;
		}
		
		// All rows share the same encounter; use the first obs only to resolve the FHIR Encounter reference.
		Observation firstObs = fetchSourceObservation(diagnosisRows.get(0).getUuid(), "[Diagnosis]");
		if (firstObs == null || !firstObs.hasEncounter() || !firstObs.getEncounter().hasReference()) {
			System.err.println("[Diagnosis] Missing source encounter reference; skipping grouped diagnosis send");
			return;
		}
		
		String encounterRef = firstObs.getEncounter().getReference();
		String encounterUuid = encounterRef.split("/")[1];
		System.err.println("[Diagnosis] Encounter uuid=" + encounterUuid + " diagnosisCount=" + diagnosisRows.size());
		
		Encounter sourceEncounter = fetchSourceEncounter(encounterUuid);
		if (sourceEncounter == null) {
			System.err.println("[Diagnosis] Unable to fetch source encounter for uuid=" + encounterUuid);
			return;
		}
		
		// Child encounters may lack participant; parent supplies asserter on Condition (doc §6).
		Encounter parentEncounter = fetchParentEncounterIfNeeded(sourceEncounter);
		
		List<Encounter.DiagnosisComponent> diagnosisComponents = new ArrayList<Encounter.DiagnosisComponent>();
		List<Resource> diagnosisResources = new ArrayList<>();
		DiagnosisTransfer transfer = new DiagnosisTransfer();
		int unknownRankBase = 3;
		for (CompletedRecord row : diagnosisRows) {
			Observation sourceObs = fetchSourceObservation(row.getUuid(), "[Diagnosis]");
			if (sourceObs == null) {
				continue;
			}
			// Parse value_text (JSON or "code::name:Primary & Confirmed") → Condition + rank.
			DiagnosisBuildResult built = transfer.build(sourceObs, row.getUuid(), row.getValueText(), unknownRankBase++,
			        sourceEncounter, parentEncounter);
			if (built == null || built.getCondition() == null) {
				System.err.println("[Diagnosis] Could not parse diagnosis obs uuid=" + row.getUuid() + " value_text="
				        + row.getValueText());
				continue;
			}
			
			Condition condition = built.getCondition();
			validateOrThrow(condition, "[Diagnosis]");
			String conditionId = VisitPushResourceIds.resolvePutResourceId(condition);
			builder.addPutResource(condition, "[Diagnosis]");
			diagnosisResources.add(condition);
			
			// Link this Condition on the Encounter with HL7 diagnosis role + primary/secondary rank.
			Encounter.DiagnosisComponent dc = new Encounter.DiagnosisComponent();
			dc.setCondition(new Reference("Condition/" + conditionId));
			dc.setUse(new CodeableConcept().addCoding(new Coding().setSystem(DiagnosisConstants.DIAGNOSIS_ROLE_SYSTEM)
			        .setCode(DiagnosisConstants.DIAGNOSIS_ROLE_CODE)));
			dc.setRank(built.getRank());
			diagnosisComponents.add(dc);
			
			System.err.println("[Diagnosis] Prepared Condition/" + conditionId + " rank=" + built.getRank());
		}
		
		if (diagnosisComponents.isEmpty()) {
			System.err.println("[Diagnosis] No parsed diagnosis conditions for encounter uuid=" + encounterUuid);
			return;
		}
		
		// One PUT Encounter carries the full ranked diagnosis list for this visit child encounter.
		sourceEncounter.setDiagnosis(diagnosisComponents);
		validateOrThrow(sourceEncounter, "[Diagnosis]");
		builder.addPutResource(sourceEncounter, "[Diagnosis]");
		diagnosisResources.add(sourceEncounter);
		for (Resource resource : diagnosisResources) {
			builder.registerProvenanceTarget(ProvenanceAssertionClass.DIAGNOSES, resource, diagnosisRows.get(0).getUuid());
		}
		System.err.println("[Diagnosis] Added encounter uuid=" + encounterUuid + " with " + diagnosisComponents.size()
		        + " diagnosis(es)");
	}
	
	private void addComplexImageToVisitBuilder(VisitTransactionBundleBuilder builder, CompletedRecord imageObs)
	        throws ParseException, UnsupportedEncodingException, DataFormatException {
		System.err.println("[ImageObs] Routing obs uuid=" + imageObs.getUuid() + " conceptId=" + imageObs.getConceptId()
		        + " value_complex=" + imageObs.getValueComplex() + " comments=" + imageObs.getComments());
		Observation sourceObs = fetchSourceObservation(imageObs.getUuid(), "[ImageObs]");
		if (sourceObs == null) {
			System.err.println("[ImageObs] No source Observation for uuid=" + imageObs.getUuid());
			return;
		}
		try {
			boolean added = new ImageObsTransfer().appendToVisitBuilder(builder, sourceObs, imageObs.getUuid(),
			    imageObs.getValueComplex(), imageObs.getComments());
			if (!added) {
				System.err.println("[ImageObs] Skipping - file missing or invalid value_complex for obs uuid="
				        + imageObs.getUuid());
			}
		}
		catch (Exception ex) {
			throw new IllegalStateException("[ImageObs] Failed to build image resources for obs " + imageObs.getUuid()
			        + ": " + ex.getMessage(), ex);
		}
	}
	
	private void addFamilyHistoryToVisitBuilder(VisitTransactionBundleBuilder builder, String obsUuid, String cachedValueText)
	        throws ParseException, UnsupportedEncodingException, DataFormatException {
		Observation sourceObs = fetchSourceObservation(obsUuid, "[FamilyHistory]");
		if (sourceObs == null) {
			return;
		}
		String valueText = resolveValueText(obsUuid, cachedValueText, sourceObs, "[FamilyHistory]");
		StructuredObsContextSupport.populateBacklogContext(sourceObs, obsUuid,
		    FamilyHistoryConstants.FAMILY_HISTORY_CONCEPT_ID);
		FamilyHistoryBuildResult built;
		try {
			built = new FamilyHistoryTransfer().build(sourceObs, obsUuid, valueText);
		}
		finally {
			UnmappedTermBacklogContext.clear();
		}
		System.err.println("[FamilyHistory] Built " + built.getFamilyMemberHistories().size()
		        + " FamilyMemberHistory resource(s), uuid=" + obsUuid);
		if (built.totalResourceCount() == 0) {
			System.err.println("[FamilyHistory] Nothing parsed (e.g. None only); skipping send, uuid=" + obsUuid);
			return;
		}
		List<Resource> resources = new ArrayList<>();
		resources.addAll(built.getFamilyMemberHistories());
		addClinicalResourcesToVisitBuilder(builder, resources, obsUuid, ProvenanceAssertionClass.FAMILY_HISTORY,
		    "[FamilyHistory]");
		System.err.println("[FamilyHistory] Done uuid=" + obsUuid);
	}
	
	private void addMedicalHistoryToVisitBuilder(VisitTransactionBundleBuilder builder, String obsUuid,
	        String cachedValueText) throws ParseException, UnsupportedEncodingException, DataFormatException {
		Observation sourceObs = fetchSourceObservation(obsUuid, "[MedicalHistory]");
		if (sourceObs == null) {
			return;
		}
		String valueText = resolveValueText(obsUuid, cachedValueText, sourceObs, "[MedicalHistory]");
		StructuredObsContextSupport.populateBacklogContext(sourceObs, obsUuid,
		    MedicalHistoryConstants.PATIENT_MEDICAL_HISTORY_CONCEPT_ID);
		MedicalHistoryBuildResult built;
		try {
			built = new MedicalHistoryTransfer().build(sourceObs, obsUuid, valueText);
		}
		finally {
			UnmappedTermBacklogContext.clear();
		}
		System.err.println("[MedicalHistory] Built obs=" + built.getObservations().size() + " allergy="
		        + built.getAllergyIntolerances().size() + " medStmt=" + built.getMedicationStatements().size()
		        + " conditions=" + built.getConditions().size() + " uuid=" + obsUuid);
		if (built.totalResourceCount() == 0) {
			System.err.println("[MedicalHistory] All topics negative or unmapped; skipping send, uuid=" + obsUuid);
			return;
		}
		List<Resource> resources = new ArrayList<>();
		resources.addAll(built.getObservations());
		resources.addAll(built.getAllergyIntolerances());
		resources.addAll(built.getMedicationStatements());
		resources.addAll(built.getConditions());
		addClinicalResourcesToVisitBuilder(builder, resources, obsUuid, ProvenanceAssertionClass.PATIENT_MEDICAL_HISTORY,
		    "[MedicalHistory]");
		System.err.println("[MedicalHistory] Done uuid=" + obsUuid + " added " + built.totalResourceCount() + " resource(s)");
	}
	
	private Observation fetchSourceObservation(String obsUuid, String logPrefix) throws ParseException,
	        UnsupportedEncodingException, DataFormatException {
		System.err.println(logPrefix + " Fetching source Observation from OpenMRS, uuid=" + obsUuid);
		String data = HttpWebClient.get(getLocalOpenmrsURL(), "/ws/fhir2/R4/Observation?_id=" + obsUuid,
		    firFhirConfig.getOpenMRSCredentials()[0], firFhirConfig.getOpenMRSCredentials()[1]);
		Bundle theBundle = newJsonParser().parseResource(Bundle.class, data);
		if (theBundle.hasEntry()) {
			for (BundleEntryComponent entry : theBundle.getEntry()) {
				if (entry.getResource() instanceof Observation) {
					return (Observation) entry.getResource();
				}
			}
		}
		System.err.println(logPrefix + " No Observation in bundle; skipping structured send, uuid=" + obsUuid);
		return null;
	}
	
	private Encounter fetchSourceEncounter(String encounterUuid) throws ParseException, UnsupportedEncodingException,
	        DataFormatException {
		System.err.println("[Diagnosis] Fetching source Encounter from OpenMRS, uuid=" + encounterUuid);
		String data = HttpWebClient.get(getLocalOpenmrsURL(), "/ws/fhir2/R4/Encounter?_id=" + encounterUuid,
		    firFhirConfig.getOpenMRSCredentials()[0], firFhirConfig.getOpenMRSCredentials()[1]);
		Bundle theBundle = newJsonParser().parseResource(Bundle.class, data);
		if (theBundle.hasEntry()) {
			for (BundleEntryComponent entry : theBundle.getEntry()) {
				if (entry.getResource() instanceof Encounter) {
					return (Encounter) entry.getResource();
				}
			}
		}
		return null;
	}
	
	private Encounter fetchParentEncounterIfNeeded(Encounter sourceEncounter) throws ParseException,
	        UnsupportedEncodingException, DataFormatException {
		if (sourceEncounter == null
		        || EncounterPractitionerSupport.extractParticipantIndividualReference(sourceEncounter) != null) {
			return null;
		}
		if (!sourceEncounter.hasPartOf() || !sourceEncounter.getPartOf().hasReference()) {
			return null;
		}
		String parentRef = sourceEncounter.getPartOf().getReference();
		if (StringUtils.isBlank(parentRef) || !parentRef.startsWith("Encounter/")) {
			return null;
		}
		String parentUuid = parentRef.substring("Encounter/".length());
		System.err.println("[Diagnosis] Diagnosis encounter has no participant; fetching parent Encounter uuid="
		        + parentUuid);
		return fetchSourceEncounter(parentUuid);
	}
	
	private String resolveValueText(String obsUuid, String cachedValueText, Observation sourceObs, String logPrefix) {
		String valueText = cachedValueText;
		if (StringUtils.isBlank(valueText)) {
			valueText = commonOperationService.getObsValueText(obsUuid);
		}
		if (StringUtils.isBlank(valueText) && sourceObs.hasValueStringType()) {
			valueText = sourceObs.getValueStringType().getValueAsString();
			System.err.println(logPrefix + " Using valueString from FHIR Observation");
		}
		return valueText;
	}
	
	private void addStructuredResourcesToVisitBuilder(VisitTransactionBundleBuilder builder, List<Condition> conditions,
	        List<Observation> observations, String sourceObsUuid, ProvenanceAssertionClass assertionClass, String logPrefix)
	        throws ParseException, UnsupportedEncodingException, DataFormatException {
		List<Resource> resources = new ArrayList<>();
		if (conditions != null) {
			resources.addAll(conditions);
		}
		if (observations != null) {
			resources.addAll(observations);
		}
		addClinicalResourcesToVisitBuilder(builder, resources, sourceObsUuid, assertionClass, logPrefix);
	}
	
	private void addClinicalResourcesToVisitBuilder(VisitTransactionBundleBuilder builder, List<Resource> clinicalResources,
	        String sourceObsUuid, ProvenanceAssertionClass assertionClass, String logPrefix) throws ParseException,
	        UnsupportedEncodingException, DataFormatException {
		if (clinicalResources == null || clinicalResources.isEmpty()) {
			return;
		}
		for (Resource resource : clinicalResources) {
			validateOrThrow(resource, logPrefix);
			if (!builder.addPutResource(resource, logPrefix)) {
				continue;
			}
			builder.registerProvenanceTarget(assertionClass, resource, sourceObsUuid);
		}
	}
	
	private void pushRegeneratedBundleForSyncLog(Bundle transactionBundle, ObsPushContext context,
	        IntelehealthShrSyncLog pushRow, String logPrefix) {
		if (context == null) {
			throw new IllegalStateException("ObsPushContext is required for SHR sync log push");
		}
		validateOrThrow(transactionBundle, logPrefix);
		shrSyncLogService.refreshRequestBundle(pushRow, transactionBundle);
		if (!publishedConfigShrSyncGateService.isShrSyncEnabled()) {
			System.err.println(logPrefix + " SHR sync disabled; sync log id=" + pushRow.getId() + " visit="
			        + context.getVisitUuid() + " remains PENDING");
			return;
		}
		shrSyncLogService.tryPushPendingRow(pushRow, transactionBundle);
		if (pushRow.getStatus() == ShrSyncLogStatus.SUCCESS) {
			System.err.println(logPrefix + " SHR sync log SUCCESS visit=" + context.getVisitUuid() + " id="
			        + pushRow.getId());
		}
	}
	
	private void enqueueVisitBundlePush(Bundle transactionBundle, ObsPushContext context, String logPrefix) {
		if (context == null) {
			throw new IllegalStateException("ObsPushContext is required for SHR sync log push");
		}
		validateOrThrow(transactionBundle, logPrefix);
		String payload = newJsonParser().setPrettyPrint(true).encodeResourceToString(transactionBundle);
		IntelehealthShrSyncLog pending = shrSyncLogService.createPending(context, payload);
		if (!publishedConfigShrSyncGateService.isShrSyncEnabled()) {
			System.err.println(logPrefix
			        + " SHR sync disabled by published config (fhir_module.shr=false); stored PENDING sync log id="
			        + pending.getId() + " visit=" + context.getVisitUuid()
			        + " (ShrSyncRetryTask will push when sync is enabled)");
			return;
		}
		shrSyncLogService.tryPushPendingRow(pending, transactionBundle);
		if (pending.getStatus() == ShrSyncLogStatus.SUCCESS) {
			System.err.println(logPrefix + " SHR sync log SUCCESS visit=" + context.getVisitUuid());
		}
	}
	
	private List<ArrayList<Integer>> getPartitions(HashSet<Integer> encounterIds, int partitionSize) {
		List<Integer> encounterList = new ArrayList<>(encounterIds); // Convert to list for indexing

		// Partition encounterIds into subsets of 25
		List<ArrayList<Integer>> partitions = new ArrayList<>();

		for (int i = 0; i < encounterList.size(); i += partitionSize) {
			partitions
					.add(new ArrayList<>(encounterList.subList(i, Math.min(i + partitionSize, encounterList.size()))));
		}
		return partitions;
	}
	
	private void send(String resource, String uuid) throws ParseException, UnsupportedEncodingException, DataFormatException {
		System.err.println("resource: " + resource);
		
		String data = HttpWebClient.get(getLocalOpenmrsURL(), "/ws/fhir2/R4/" + resource + "?_id=" + uuid,
		    firFhirConfig.getOpenMRSCredentials()[0], firFhirConfig.getOpenMRSCredentials()[1]);
		Bundle theBundle = newJsonParser().parseResource(Bundle.class, data);
		
		sendFHIRBundle(theBundle, resource);
		
		if (theBundle.hasEntry()) {
			System.err.println("Got  bundle size: " + theBundle.getEntry().size());
		}
	}
	
	public void sendFHIRBundle(Bundle originalTasksBundle, String resourceType) throws ParseException,
	        UnsupportedEncodingException, DataFormatException {
		ensureDependencies();
		
		if (originalTasksBundle.hasEntry()) {
			for (BundleEntryComponent bundleEntry : originalTasksBundle.getEntry()) {
				Resource resource = (Resource) bundleEntry.getResource();
				System.err.println("resource.getMeta().getLastUpdated():::" + resource.getMeta().getLastUpdated());
				String resourceId = resource.getIdElement().getIdPart();
				
				Bundle transactionBundle = new Bundle();
				transactionBundle.setType(Bundle.BundleType.TRANSACTION);
				Set<String> addedCruids = new HashSet<>();
				Bundle.BundleEntryComponent component = transactionBundle.addEntry();
				
				if (resourceType.equalsIgnoreCase("Encounter")) {
					Encounter encounter = (Encounter) bundleEntry.getResource();
					if (!ensureCruidPatientInBundle(transactionBundle, addedCruids, encounter.getSubject(),
					    "[Encounter]")) {
						continue;
					}
					ShrPushMetaApplicator.applyPushMeta(encounter);
					validateOrThrow(encounter, "[Encounter]");
					component.setResource(encounter);
					
				} else if (resourceType.equalsIgnoreCase("Observation")) {
					Observation observation = (Observation) bundleEntry.getResource();
					if (!ensureCruidPatientInBundle(transactionBundle, addedCruids, observation.getSubject(),
					    "[Observation]")) {
						continue;
					}
					ShrPushMetaApplicator.applyPushMeta(observation);
					validateOrThrow(observation, "[Observation]");
					component.setResource(observation);
					
				} else if (resourceType.equalsIgnoreCase("MedicationRequest")) {
					MedicationRequest medicationRequest = (MedicationRequest) bundleEntry.getResource();
					if (!ensureCruidPatientInBundle(transactionBundle, addedCruids, medicationRequest.getSubject(),
					    "[MedicationRequest]")) {
						continue;
					}
					ShrPushMetaApplicator.applyPushMeta(medicationRequest);
					validateOrThrow(medicationRequest, "[MedicationRequest]");
					component.setResource(medicationRequest);
					
				} else if (resourceType.equalsIgnoreCase("ServiceRequest")) {
					ServiceRequest serviceRequest = (ServiceRequest) bundleEntry.getResource();
					if (!ensureCruidPatientInBundle(transactionBundle, addedCruids, serviceRequest.getSubject(),
					    "[ServiceRequest]")) {
						continue;
					}
					ShrPushMetaApplicator.applyPushMeta(serviceRequest);
					validateOrThrow(serviceRequest, "[ServiceRequest]");
					component.setResource(serviceRequest);
				} else {
					Reference subject = ShrCruidPatientSupport.extractSubjectReference(resource);
					if (subject != null
					        && !ensureCruidPatientInBundle(transactionBundle, addedCruids, subject, "[" + resourceType + "]")) {
						continue;
					}
					ShrPushMetaApplicator.applyPushMeta(resource);
					component.setResource(resource);
				}
				
				component.getRequest().setUrl(resource.fhirType() + "/" + resourceId).setMethod(Bundle.HTTPVerb.PUT);
				
				ShrNarrativeSupport.stripBundleNarratives(transactionBundle);
				String payload = newJsonParser().setPrettyPrint(true).encodeResourceToString(transactionBundle);
				
				DataExchangeAuditLog log = new DataExchangeAuditLog();
				log.setResourceName(resourceType);
				log.setResourceUuid(resourceId);
				log.setRequest(payload);
				log.setRequestUrl(getShrUrl());
				
				try {
					// Validate the whole transaction bundle before sending to SHR.
					validateOrThrow(transactionBundle, "[" + resourceType + " Bundle]");
					DataExchangeAuditLog uLog = dataExchangeService.save(log);
					
					FhirResponse res = firFhirConfig.postTransactionBundle(transactionBundle);
					
					uLog.setResponse(res.getResponse());
					uLog.setResponseStatus(res.getStatusCode());
					if (res.getStatusCode().equals("200")) {
						Bundle remoteBundle = newJsonParser().parseResource(Bundle.class, res.getResponse());
						System.err.println("Response from central fhir: " + res.getResponse());
						uLog.setFhirId(extractResourceId(remoteBundle));
					} else {
						uLog.setStatus(false);
					}
					uLog.setChangedBy(1); // Admin-OpenMRS
					uLog.setDateChanged(DateUtils.toFormattedDateNow());
					dataExchangeService.update(uLog);
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
			
		}
		System.err.println("Done");
	}
	
	/**
	 * Resolves facility MPI (= CRUID) and adds a shadow {@link org.hl7.fhir.r4.model.Patient} entry
	 * with {@code ifNoneExist} on
	 * {@link org.openmrs.module.ihshr.utils.CruidConstants#IDENTIFIER_SYSTEM}.
	 */
	private boolean ensureCruidPatientInBundle(Bundle transactionBundle, Set<String> addedCruids, Reference subject,
	        String logPrefix) {
		if (subject == null || !subject.hasReference()) {
			System.err.println(logPrefix + " No patient subject reference; skipping SHR push");
			return false;
		}
		if (ShrCruidPatientSupport.isInBundlePatientReference(subject.getReference())) {
			return true;
		}
		String openMrsPatientUuid = ShrCruidPatientSupport.extractOpenMrsPatientUuid(subject.getReference());
		if (openMrsPatientUuid == null) {
			System.err.println(logPrefix + " Subject is not an OpenMRS Patient reference: " + subject.getReference());
			return false;
		}
		String cruid = commonOperationService.getMPIUsingPatientReference(openMrsPatientUuid);
		if (StringUtils.isBlank(cruid)) {
			System.err.println(logPrefix + " Patient uuid=" + openMrsPatientUuid
			        + " has no CRUID (MPI identifier); skipping SHR push — sync to Client Registry first");
			return false;
		}
		String patientFullUrl = ShrCruidPatientSupport.addPatientEntryIfAbsent(transactionBundle, addedCruids, cruid.trim());
		ShrCruidPatientSupport.rewriteSubjectReference(subject, patientFullUrl);
		System.err.println(logPrefix + " Linked subject to CRUID " + cruid.trim() + " via " + patientFullUrl);
		return true;
	}
	
	private String extractResourceId(Bundle bundle) {
		if (bundle.getEntry().size() != 1)
			return null;
		Resource resource = bundle.getEntryFirstRep().getResource();
		return resource.getIdElement().getIdPart();
	}
	
	private volatile ShrIgOnlineLoader igOnlineValidator;
	
	private volatile boolean igOnlineValidationDisabled;
	
	private volatile ShrFhirValidator baseValidator;
	
	private volatile boolean baseValidationDisabled;
	
	private void validateOrThrow(org.hl7.fhir.instance.model.api.IBaseResource resource, String logPrefix) {
		if (baseValidationDisabled && igOnlineValidationDisabled) {
			return;
		}
		// Online IG validation: fetch StructureDefinitions from the IG canonical site and validate
		// against the profile URL for the resource type.
		String profileUrl = resolveIhProfileUrl(resource);
		if (profileUrl != null) {
			ShrIgOnlineLoader onlineLoader = getIgOnlineValidator();
			if (onlineLoader != null) {
				onlineLoader.validateOrThrow(resource, profileUrl);
				System.err.println(logPrefix + " IG validation passed: " + profileUrl);
				return;
			}
		}
		
		if (baseValidationDisabled) {
			return;
		}
		try {
			getBaseValidator().validateOrThrow(resource);
			System.err.println(logPrefix + " Validation passed (base R4)");
		}
		catch (NoClassDefFoundError | ExceptionInInitializerError e) {
			baseValidationDisabled = true;
			System.err.println("[Validation] Base R4 validator disabled; continuing without validation. Cause: "
			        + e.getClass().getName() + " - " + e.getMessage());
		}
	}
	
	private ShrFhirValidator getBaseValidator() {
		if (baseValidator != null) {
			return baseValidator;
		}
		synchronized (this) {
			if (baseValidator == null) {
				baseValidator = new ShrFhirValidator();
			}
			return baseValidator;
		}
	}
	
	private IParser newJsonParser() {
		return FhirContextHolder.R4.newJsonParser();
	}
	
	private ShrIgOnlineLoader getIgOnlineValidator() {
		if (igOnlineValidationDisabled) {
			return null;
		}
		if (igOnlineValidator != null) {
			return igOnlineValidator;
		}
		synchronized (this) {
			if (igOnlineValidationDisabled) {
				return null;
			}
			if (igOnlineValidator != null) {
				return igOnlineValidator;
			}
			try {
				igOnlineValidator = new ShrIgOnlineLoader();
			}
			catch (Throwable t) {
				igOnlineValidationDisabled = true;
				System.err.println("[Validation] Online IG validator disabled; falling back to base R4 validation. Cause: "
				        + t.getClass().getName() + " - " + t.getMessage());
				return null;
			}
			return igOnlineValidator;
		}
	}
	
	private String resolveIhProfileUrl(IBaseResource resource) {
		if (resource == null) {
			return null;
		}
		if (resource instanceof Bundle) {
			return ShrIgProfileUrls.IH_TRANSACTION_BUNDLE;
		}
		if (resource instanceof Encounter) {
			return ShrIgProfileUrls.IH_ENCOUNTER;
		}
		if (resource instanceof Observation) {
			return ShrIgProfileUrls.IH_OBSERVATION;
		}
		if (resource instanceof MedicationRequest) {
			return ShrIgProfileUrls.IH_MEDICATION_REQUEST;
		}
		if (resource instanceof ServiceRequest) {
			return ShrIgProfileUrls.IH_SERVICE_REQUEST;
		}
		if (resource instanceof Condition) {
			return ShrIgProfileUrls.IH_CONDITION;
		}
		if (resource instanceof FamilyMemberHistory) {
			return ShrIgProfileUrls.IH_FAMILY_MEMBER_HISTORY;
		}
		if (resource instanceof AllergyIntolerance) {
			return ShrIgProfileUrls.IH_ALLERGY_INTOLERANCE;
		}
		if (resource instanceof MedicationStatement) {
			return ShrIgProfileUrls.IH_MEDICATION_STATEMENT;
		}
		if (resource instanceof Location) {
			return ShrIgProfileUrls.IH_LOCATION;
		}
		if (resource instanceof Practitioner) {
			return ShrIgProfileUrls.IH_PRACTITIONER;
		}
		return null;
	}
}
