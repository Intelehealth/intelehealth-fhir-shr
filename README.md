# IHSHR OpenMRS Module

Shared Health Record (SHR) integration module for OpenMRS, migrated from the standalone Spring Boot application `intelehealth-fhir-health-record-exchange`.

## Structure

| Module | Package | Contents |
|--------|---------|----------|
| `api` | `org.openmrs.module.ihshr` | Services, FHIR config, domain/DTOs, Hibernate repositories, utilities |
| `omod` | `org.openmrs.module.ihshr.web`, `.scheduler`, `.event`, `.globalexp` | REST controllers, encounter event listener, scheduled sync task, exception handling |

## REST endpoints (unchanged paths)

- `GET /health-record-exchange/api/v1/bundle/{resourceType}`
- `GET /health-record-exchange/api/v1/shr/bundle/{resourceType}`
- `GET /health-record-exchange/api/v1/control/activity` (or `module/ihshr/hreActivity.form` on OpenMRS module servlet)
- `GET /health-record-exchange/api/v1/shr/history/{uuid}` (or `module/ihshr/shrHistory.form?openmrsPatientUuid=...`)
- `GET /health-record-exchange/api/v1/shr/capability-statement` (or `/ws/rest/v1/ihshr/shr/capability-statement`) — ihshr SHR integration requirements CapabilityStatement JSON
- `GET /health-record-exchange/api/v1/diagnostic/get-report`

## Scheduler

Register in **Administration → Manage Scheduler**:

- **Task class:** `org.openmrs.module.ihshr.scheduler.HealthRecordSyncTask` (delegates to `DataSendToSHR`)
- **Interval:** e.g. every 60 seconds (replaces Spring `@Scheduled` in the original app)
- **Task class:** `org.openmrs.module.ihshr.scheduler.ShrSyncRetryTask` — replays failed visit pushes from `intelehealth_shr_sync_log` (doc §3.2, §13.4); interval e.g. every 60 seconds

## Visit-wide SHR push (`pushCompletedVisits`)

Each completed visit is exported as **one FHIR `transaction` Bundle** containing the visit-complete Encounter, child Encounters, all clinical resources (Observations, Conditions, orders, family history, images with Binary/DocumentReference, etc.), and **one Provenance entry per assertion class**. The bundle is posted atomically to SHR and logged in `intelehealth_shr_sync_log` (one row per visit push attempt):

| Step | Behavior |
|------|----------|
| **9 – PENDING** | Before the SHR HTTP call, insert a row with `status=PENDING`, full visit Bundle JSON in `request_bundle` (UTF-8), `visit_uuid`, and `trigger_encounter_uuid`. |
| **16 – SUCCESS** | On HTTP 200, set `status=SUCCESS`, store response bundle and `resource_map` (OpenMRS id → SHR id). |
| **3.2 – Failure** | On error, set `FAILED` or `FAILED_PERMANENT` (4xx), schedule `next_retry_at` with exponential backoff (max 10 attempts). |
| **§13.2 – Badge** | On success, update visit attributes `shr_last_push_status` / `shr_last_push_at` when those attribute types exist. |

### Push pipeline (implementation)

This section documents **how the ihshr module builds bundles**. The FHIR payload contract (profiles, meta, identifiers) is defined in the [IH Implementation Guide](https://ih-ig.mpower-social.com) — see **SHR Push Conformance**.

| Stage | Component | Behavior |
|-------|-----------|----------|
| Trigger | `EncounterCreatedEventListener` | Fires on `Encounter.CREATED`; filtered to visit-complete encounter type in production |
| Orchestration | `DataSendToSHR` | Builds visit-scoped transaction bundle, applies push meta, validates against IG profiles, POSTs to SHR |
| Bundle assembly | `VisitTransactionBundleBuilder` | Dedupes PUT entries, bootstraps Patient via CRUID `ifNoneExist`, appends Provenance per assertion class |
| Push meta | `ShrPushMetaApplicator` | Sets `meta.source` and `meta.tag` on every resource (doc §5.3 no-echo) |
| Structured obs | `ChiefComplaintTransfer`, `PhysicalExamObservationBuilder`, `FamilyHistoryTransfer`, `MedicalHistoryTransfer`, `ReferralServiceRequestBuilder`, `ImageObsTransfer` | Parse OpenMRS obs JSON → FHIR resources |
| FHIR2 export | `fetchFhirResource()` | Location, Practitioner, Encounter, MedicationRequest, ServiceRequest from OpenMRS FHIR2 API |
| Transport | OpenHIM → SHR | Bundle POST to `ihshr.opencr.shr.url` |
| Retry | `ShrSyncRetryTask` | Replays failed rows from `intelehealth_shr_sync_log` |

### Resource builders by OpenMRS concept

| Concept ID | Section | Java entry point | FHIR output |
|------------|---------|------------------|-------------|
| — | Location / Practitioner / Encounter / orders | `DataSendToSHR.addFhirResourceToVisitBuilder()` | Location, Practitioner, Encounter, MedicationRequest, ServiceRequest |
| 163212 | Chief complaint | `ChiefComplaintTransfer` | Condition, Observation (associated symptoms) |
| 163213 | Physical examination | `PhysicalExamObservationBuilder` | Observation (one per category) |
| 163211 | Family history | `FamilyHistoryTransfer` | FamilyMemberHistory |
| 163210 | Patient medical history | `MedicalHistoryTransfer` | Observation, AllergyIntolerance, MedicationStatement, Condition |
| 163219 | Diagnosis | `DiagnosisTransfer` | Condition + Encounter.diagnosis |
| 165238 | Referral | `ReferralServiceRequestBuilder` | ServiceRequest |
| 163371/163372 | Clinical image | `ImageObsTransfer` | Binary, DocumentReference |

Verify rows:

```sql
SELECT id, visit_uuid, attempt_number, status, http_status_code, started_at, completed_at, next_retry_at
FROM intelehealth_shr_sync_log
ORDER BY id DESC
LIMIT 20;
```

## Configuration

On module install/start, IHSHR registers defaults in **Administration → Manage Global Properties** (names prefixed with `ihshr.`). You can change values there at any time; the module reads the latest value on each sync or REST call (no redeploy required).

| Global property | Purpose |
|-----------------|--------|
| `ihshr.local.openmrs.url` | Local OpenMRS base URL |
| `ihshr.local.openmrs.clientid.password.basic.auth` | Local OpenMRS basic auth (`user:password`) |
| `ihshr.opencr.shr.url` | Central SHR FHIR URL for bundle upload |
| `ihshr.opencr.openhim.url` | OpenCR FHIR via OpenHIM |
| `ihshr.opencr.openhim.clientid.password.basic.auth` | OpenCR basic auth |
| `ihshr.gofr.openhim.url` | GOFR FHIR URL |
| `ihshr.gofr.openhim.clientid.password.basic.auth` | GOFR basic auth |
| `ihshr.resource.*.export` | IH marker names for each resource type |
| `ihshr.shr.lookup.dir` or `intelehealth.shr.lookup_dir` | Base directory for SNOMED lookup JSON (`lookups/` subfolder, default `/opt/intelehealth/shr-config`) |
| `intelehealth.shr.source.uri` | Value for `meta.source` on every SHR push resource (doc §5.3; unique per installation) |
| `ihshr.ig.canonical` | IG canonical URL for online profile validation (default `https://ih-ig.mpower-social.com`) |

Classpath file `api/src/main/resources/ihshr.properties` is used only when a global property is empty or missing (e.g. before first startup).

### Lookup JSON files (§8)

Source copies live in [`shr-config/lookups/`](shr-config/lookups/). At deploy, copy them to `/opt/intelehealth/shr-config/lookups/` on the server (see [`shr-config/README.md`](shr-config/README.md)).

### Verify FHIR payloads (doc compliance checklist)

```bash
./scripts/verify-shr-fhir-payloads.sh
```

Prints PASS/FAIL for chief complaint, physical exam, family history, and medical history rules; full JSON is in `/tmp/shr-fhir-payloads.log`.

## Build

Install the doctor UI API module first (provides `IHMarker` / `IHMarkerService`):

```bash
cd ../intelehealth-fhir-doctor-ui-api && mvn clean install -DskipTests
cd ../shr && mvn clean package -DskipTests
```

Deploy `omod/target/ihshr-1.0.0-SNAPSHOT.omod` to your OpenMRS instance.

OpenMRS module id: `ihshr` (`org.openmrs.module.ihshr`).

## Troubleshooting startup

If OpenMRS fails at startup with `A user context must first be passed to setUserContext()` during `SchedulerUtil.startup`, rebuild and redeploy **ihmodule** after the `APIfordoctorUIActivator` session fix (it must not call `Context.closeSession()` during the initial Spring context refresh when a session is already open).

If startup fails with `org.json.JSONException referenced from a method is not visible from class loader` on `mpiDuplicateReviewPatientActionService`, rebuild and redeploy **ihmodule** (API interfaces must not declare `org.json.JSONException` in `throws` clauses; that type is only visible inside the module classloader and breaks Spring JDK proxies).

## Requirements

- OpenMRS Platform 2.4.3+
- `webservices.rest` module
- `fhir2` module
- `event` module (`org.openmrs.event`) — publishes `Encounter.CREATED` domain events
- `ihmodule` module ([intelehealth-fhir-doctor-ui-api](../intelehealth-fhir-doctor-ui-api)) — sync markers (`ih_marker` table, `IHMarkerService`)
