# SHR lookup configuration (per implementation guide §8, §16.3)

Deploy these JSON files on the OpenMRS host and point the module at them.

## Runtime path (production)

```text
/opt/intelehealth/shr-config/lookups/
├── chief-complaint-mappings.json
├── physical-exam-mappings.json
├── exam-bodysite-mappings.json
├── family-relationships.json
├── family-history-conditions.json
└── patient-history-topics.json
```

OpenMRS global property (doc name): `intelehealth.shr.lookup_dir` = `/opt/intelehealth/shr-config`  
Module property (classpath fallback): `ihshr.shr.lookup.dir` (same value)

Install example (from guide §16.3):

```bash
mkdir -p /opt/intelehealth/shr-config/lookups
cp shr-config/lookups/*.json /opt/intelehealth/shr-config/lookups/
python3 -c "import json, glob; [json.load(open(f)) for f in glob.glob('/opt/intelehealth/shr-config/lookups/*.json')]"
```

## Bundled defaults

The same files ship inside the OMOD JAR at `shr-config/lookups/` and are used when no file exists on disk.

## File roles

| File | Used by |
|------|---------|
| `chief-complaint-mappings.json` | Chief complaint Condition SNOMED codes |
| `physical-exam-mappings.json` | Physical exam component finding codes |
| `exam-bodysite-mappings.json` | Physical exam category bodySite |
| `family-relationships.json` | Family history relative v3-RoleCode |
| `family-history-conditions.json` | Family history conditions + medical history Condition codes |
| `patient-history-topics.json` | Medical history topic → FHIR resource mapping |

Edit JSON on disk and restart sync (or call `MedicalHistoryTopicConfig.reload()` / `ShrLookupLoader.clearCache()` after a future hot-reload hook).

## §8.6 Unmapped term backlog

When structured obs export emits FHIR with **text-only** codes (no SNOMED from lookup JSON), rows are stored in **`ihshr_unmapped_term`** (equivalent to doc `intelehealth_unmapped_<artifact>`).

| Column | Purpose |
|--------|---------|
| `artifact` | e.g. `chief_complaint`, `physical_exam`, `medical_history_condition` |
| `term_text` | Phrase that failed lookup |
| `lookup_file` | JSON file to update (e.g. `chief-complaint-mappings.json`) |
| `obs_uuid` / `encounter_uuid` / `patient_uuid` | Source context |
| `occurrences` | Incremented on re-export of same (artifact, term, obs) |

**Reporting API** (after module restart / Liquibase):

```bash
# Top 50 unresolved chief-complaint terms
curl -u admin:Admin123 'http://localhost:8082/openmrs/health-record-exchange/api/v1/backlog/top?artifact=chief_complaint&limit=50'

# CSV export for curation
curl -u admin:Admin123 'http://localhost:8082/openmrs/health-record-exchange/api/v1/backlog/export.csv?artifact=physical_exam'

# Mark resolved after adding JSON mapping
curl -u admin:Admin123 -X POST 'http://localhost:8082/openmrs/health-record-exchange/api/v1/backlog/resolve?artifact=chief_complaint&termText=Some%20Symptom'
```

**Weekly log report:** register scheduler task `org.openmrs.module.ihshr.scheduler.UnmappedTermBacklogReportTask`, or `POST .../backlog/report`.

Disable persistence: global property `ihshr.unmapped.backlog.enabled` = `false`.

## §8.6 Unmapped term backlog

When structured obs export emits FHIR with **text-only** codes (no SNOMED from lookup JSON), rows are stored in **`ihshr_unmapped_term`** (equivalent to doc `intelehealth_unmapped_<artifact>`).

| Column | Purpose |
|--------|---------|
| `artifact` | `chief_complaint`, `physical_exam`, `family_history_condition`, etc. |
| `term_text` | Phrase that failed lookup |
| `lookup_file` | e.g. `chief-complaint-mappings.json` |
| `obs_uuid` / `encounter_uuid` / `patient_uuid` | Source context |
| `occurrences` | Incremented on re-export of same (artifact, term, obs) |

**Reporting API** (after module start):

```bash
# Top 50 unresolved chief-complaint terms
curl -u admin:Admin123 'http://localhost:8082/openmrs/health-record-exchange/api/v1/backlog/top?artifact=chief_complaint&limit=50'

# CSV export for curation
curl -u admin:Admin123 'http://localhost:8082/openmrs/health-record-exchange/api/v1/backlog/export.csv?artifact=physical_exam'

# Mark resolved after adding to lookup JSON
curl -u admin:Admin123 -X POST 'http://localhost:8082/openmrs/health-record-exchange/api/v1/backlog/resolve?artifact=chief_complaint&termText=Some%20Symptom'
```

**Weekly log report:** register scheduler task `org.openmrs.module.ihshr.scheduler.UnmappedTermBacklogReportTask`, or `POST .../backlog/report`.

Global property: `ihshr.unmapped.backlog.enabled` (default `true`).

## Verify FHIR payloads against the implementation doc

From the **ihshr repo root**:

```bash
./scripts/verify-shr-fhir-payloads.sh
```

This will:

1. Run `StructuredObsFhirPayloadTest` (prints FHIR transaction bundles to stderr).
2. Save output to `/tmp/shr-fhir-payloads.log`.
3. Print a **PASS/FAIL** checklist for doc sections 7.1–7.4 plus compliance markers (negative filter, legacy HTML skip, etc.; typically **39** rules total).

Options:

```bash
# Only verify an existing log:
python3 scripts/verify_shr_fhir_payloads.py --log /tmp/shr-fhir-payloads.log

# Custom log path:
LOG=/tmp/my-payloads.log ./scripts/verify-shr-fhir-payloads.sh
```

Inspect raw JSON:

```bash
less /tmp/shr-fhir-payloads.log
grep '^#' /tmp/shr-fhir-payloads.log
```
