#!/usr/bin/env python3
"""
Verify SHR FHIR transaction payloads from StructuredObsFhirPayloadTest log output
against OpenMRS_HAPI_SHR_Implementation_Final.docx (sections 7.1-7.4, 8, 16.3).

Usage:
  # Generate log and verify (from repo root):
  ./scripts/verify-shr-fhir-payloads.sh

  # Verify an existing log:
  python3 scripts/verify_shr_fhir_payloads.py --log /tmp/shr-fhir-payloads.log

  # Generate log only:
  python3 scripts/verify_shr_fhir_payloads.py --run --log /tmp/shr-fhir-payloads.log
"""

from __future__ import print_function

import argparse
import json
import os
import re
import subprocess
import sys

DEFAULT_LOG = "/tmp/shr-fhir-payloads.log"
DEFAULT_LOOKUP_DIR = "/opt/intelehealth/shr-config/lookups"
REQUIRED_LOOKUP_FILES = [
    "chief-complaint-mappings.json",
    "physical-exam-mappings.json",
    "exam-bodysite-mappings.json",
    "family-relationships.json",
    "family-history-conditions.json",
    "patient-history-topics.json",
    "referral-specialty-mappings.json",
]


class Check(object):
    def __init__(self, section, rule, passed, detail=""):
        self.section = section
        self.rule = rule
        self.passed = passed
        self.detail = detail


COMPLIANCE_MARKER_RE = re.compile(
    r"\[SHR-COMPLIANCE\]\s+(PASS|FAIL)\s+(\S+)\s+(.*)$"
)

PAYLOAD_MAVEN_TESTS = [
    "StructuredObsFhirPayloadTest#printAllFourTypePayloads",
    "StructuredObsFhirPayloadComplianceTest#emitComplianceMarkers",
]


def run_payload_test(repo_root, log_path, lookup_dir):
    env = os.environ.copy()
    lookup = lookup_dir or "/opt/intelehealth/shr-config"
    if lookup_dir:
        env["MAVEN_OPTS"] = (env.get("MAVEN_OPTS", "") + " -Dihshr.shr.lookup.dir=" + lookup).strip()
    exit_code = 0
    print("Writing stderr to: %s\n" % log_path)
    with open(log_path, "w") as log_file:
        for test_selector in PAYLOAD_MAVEN_TESTS:
            cmd = [
                "mvn", "test", "-pl", "api",
                "-Dtest=" + test_selector,
                "-Dihshr.shr.lookup.dir=" + lookup,
            ]
            print("Running: %s" % " ".join(cmd))
            proc = subprocess.Popen(
                cmd,
                cwd=repo_root,
                env=env,
                stdout=subprocess.PIPE,
                stderr=log_file,
                universal_newlines=True,
            )
            stdout, _ = proc.communicate()
            if stdout:
                print(stdout)
            if proc.returncode != 0:
                exit_code = proc.returncode
                print("WARNING: mvn exited with code %s for %s" % (proc.returncode, test_selector),
                      file=sys.stderr)
            log_file.write("\n")
    if exit_code != 0:
        print("WARNING: one or more mvn test runs failed (log may still be usable)", file=sys.stderr)
    return exit_code


def read_log(path):
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        return f.read()


def extract_bundles(log_text):
    """Extract JSON Bundle objects from maven test stderr log."""
    bundles = []
    i = 0
    while i < len(log_text):
        start = log_text.find('{\n  "resourceType"', i)
        if start < 0:
            start = log_text.find('{"resourceType"', i)
        if start < 0:
            break
        depth = 0
        end = start
        for j in range(start, len(log_text)):
            ch = log_text[j]
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    end = j + 1
                    break
        chunk = log_text[start:end]
        try:
            obj = json.loads(chunk)
            if obj.get("resourceType") == "Bundle":
                bundles.append(obj)
        except ValueError:
            pass
        i = end + 1
    return bundles


def bundle_resources(bundle):
    resources = []
    for entry in bundle.get("entry", []):
        res = entry.get("resource")
        if res:
            resources.append(res)
    return resources


def all_resources(log_text):
    resources = []
    for bundle in extract_bundles(log_text):
        resources.extend(bundle_resources(bundle))
    return resources


def check_lookup_files(lookup_dir):
    checks = []
    base = lookup_dir or DEFAULT_LOOKUP_DIR
    if not os.path.isdir(base):
        return [Check("Setup", "Lookup directory exists: %s" % base, False, "directory missing")]
    for name in REQUIRED_LOOKUP_FILES:
        path = os.path.join(base, name)
        ok = os.path.isfile(path)
        try:
            if ok:
                json.load(open(path, encoding="utf-8"))
        except Exception as e:
            ok = False
            detail = str(e)
        else:
            detail = "%s bytes" % os.path.getsize(path) if ok else "missing"
        checks.append(Check("Setup", "Lookup file: %s" % name, ok, detail))
    return checks


def check_compliance_markers(log_text):
    """Rules emitted by StructuredObsFhirPayloadComplianceTest (doc negative filter / skip paths)."""
    checks = []
    seen = {}
    for line in log_text.splitlines():
        match = COMPLIANCE_MARKER_RE.search(line.strip())
        if not match:
            continue
        status, rule, detail = match.group(1), match.group(2), match.group(3).strip()
        seen[rule] = (status == "PASS", detail)
    expected_rules = [
        "medical-history-all-negative",
        "family-history-none",
        "legacy-plain-html-skipped",
        "chief-complaint-plain-html-matched",
        "medical-history-allergy-dust",
        "family-history-multi-relative",
        "medical-history-comma-split",
    ]
    for rule in expected_rules:
        if rule not in seen:
            checks.append(Check("Compliance", "Compliance marker: %s" % rule, False, "missing from log"))
        else:
            passed, detail = seen[rule]
            checks.append(Check("Compliance", "Compliance marker: %s" % rule, passed, detail))
    return checks


def check_log_sections(log_text):
    checks = []
    sections = [
        ("7.1 Chief Complaint", r"# 1\. CHIEF COMPLAINT"),
        ("7.2 Physical Examination", r"# 2\. PHYSICAL EXAMINATION"),
        ("7.3 Family History", r"# 3\. FAMILY HISTORY"),
        ("7.4 Medical History", r"# 4\. PATIENT MEDICAL HISTORY"),
    ]
    for label, pattern in sections:
        found = re.search(pattern, log_text) is not None
        checks.append(Check("Log", "Section present: %s" % label, found))
    lookup_header = "/opt/intelehealth/shr-config/lookups" in log_text or "Lookup directory:" in log_text
    checks.append(Check("Log", "Lookup directory listing in log", lookup_header))
    return checks


def check_transaction_bundles(log_text):
    checks = []
    bundles = extract_bundles(log_text)
    ok = len(bundles) > 0
    checks.append(Check("Bundle", "At least one FHIR transaction Bundle in log", ok, "count=%s" % len(bundles)))
    if not bundles:
        return checks
    all_tx = all(b.get("type") == "transaction" for b in bundles)
    checks.append(Check("Bundle", 'All bundles type == "transaction"', all_tx))
    all_put = True
    for b in bundles:
        for entry in b.get("entry", []):
            req = entry.get("request", {})
            if req.get("method") != "PUT":
                all_put = False
            if not req.get("url"):
                all_put = False
    checks.append(Check("Bundle", "All entries use HTTP PUT with url", all_put))
    return checks


def check_chief_complaint(resources):
    checks = []
    conditions = [r for r in resources if r.get("resourceType") == "Condition"]
    observations = [r for r in resources if r.get("resourceType") == "Observation"]
    cc_conditions = [c for c in conditions if any(
        "::cc-" in (i.get("value") or "") for i in c.get("identifier", [])
    )]
    checks.append(Check("7.1 Chief Complaint", "At least one Condition with ::cc- identifier", len(cc_conditions) >= 1))
    if cc_conditions:
        c = cc_conditions[0]
        cat_ok = any(
            cod.get("code") == "encounter-diagnosis"
            for cat in c.get("category", [])
            for cod in cat.get("coding", [])
        )
        checks.append(Check("7.1 Chief Complaint", "Condition category encounter-diagnosis", cat_ok))
        ver_ok = any(
            cod.get("code") == "unconfirmed"
            for vs in [c.get("verificationStatus", {})]
            for cod in vs.get("coding", [])
        )
        checks.append(Check("7.1 Chief Complaint", "Condition verificationStatus unconfirmed", ver_ok))
    assoc = [o for o in observations if any(
        "::assoc-" in (i.get("value") or "") for i in o.get("identifier", [])
    )]
    checks.append(Check("7.1 Chief Complaint", "Associated symptom Observations (::assoc-pos/neg)", len(assoc) >= 1))
    if assoc:
        has_focus = any(o.get("focus") for o in assoc)
        checks.append(Check("7.1 Chief Complaint", "Associated Observations link focus -> Condition", has_focus))
    return checks


def check_physical_exam(resources):
    checks = []
    pe_obs = [r for r in resources if r.get("resourceType") == "Observation" and any(
        "::cat-" in (i.get("value") or "") for i in r.get("identifier", [])
    )]
    checks.append(Check("7.2 Physical Exam", "Category Observation(s) with ::cat- identifier", len(pe_obs) >= 1))
    if pe_obs:
        o = pe_obs[0]
        exam_cat = any(
            cod.get("code") == "exam" for cat in o.get("category", []) for cod in cat.get("coding", [])
        )
        checks.append(Check("7.2 Physical Exam", "Observation category exam", exam_cat))
        proc_code = o.get("code", {})
        has_425044008 = any(cod.get("code") == "425044008" for cod in proc_code.get("coding", []))
        checks.append(Check("7.2 Physical Exam", "Procedure code 425044008 (physical exam)", has_425044008))
        has_components = len(o.get("component", [])) >= 1
        checks.append(Check("7.2 Physical Exam", "At least one component per category", has_components))
        has_body = o.get("bodySite") is not None
        checks.append(Check("7.2 Physical Exam", "bodySite populated from exam-bodysite-mappings", has_body))
    return checks


def check_family_history(resources):
    checks = []
    fmh = [r for r in resources if r.get("resourceType") == "FamilyMemberHistory"]
    checks.append(Check("7.3 Family History", "FamilyMemberHistory resource(s) emitted", len(fmh) >= 1))
    if fmh:
        h = fmh[0]
        id_ok = any("::fam-" in (i.get("value") or "") for i in h.get("identifier", []))
        checks.append(Check("7.3 Family History", "Identifier ::fam-{role}", id_ok))
        rel = h.get("relationship", {})
        role_ok = any(cod.get("code") for cod in rel.get("coding", []))
        checks.append(Check("7.3 Family History", "relationship uses v3-RoleCode", role_ok))
        conds = h.get("condition", [])
        checks.append(Check("7.3 Family History", "condition[] lists relative conditions", len(conds) >= 1))
        if conds:
            snomed = any(
                cod.get("system", "").endswith("snomed.info/sct")
                for c in conds
                for cod in c.get("code", {}).get("coding", [])
            )
            checks.append(Check("7.3 Family History", "Condition SNOMED from family-history-conditions.json", snomed))
    return checks


def check_medical_history(resources):
    checks = []
    mh_obs = [r for r in resources if r.get("resourceType") == "Observation" and any(
        "::topic-" in (i.get("value") or "") for i in r.get("identifier", [])
    )]
    mh_cond = [r for r in resources if r.get("resourceType") == "Condition" and any(
        "::topic-medical-history" in (i.get("value") or "") for i in r.get("identifier", [])
    )]
    mh_allergy = [r for r in resources if r.get("resourceType") == "AllergyIntolerance"]
    mh_med = [r for r in resources if r.get("resourceType") == "MedicationStatement"]
    checks.append(Check("7.4 Medical History", "Positive topics emit resources", len(mh_obs) + len(mh_cond) + len(mh_allergy) + len(mh_med) >= 1))
    smoking = [o for o in mh_obs if any(cod.get("code") == "72166-2" for cod in o.get("code", {}).get("coding", []))]
    checks.append(Check("7.4 Medical History", "Smoking uses LOINC 72166-2 when present", len(smoking) >= 1 or len(mh_obs) == 0))
    if mh_cond:
        pl_ok = any(
            cod.get("code") == "problem-list-item"
            for c in mh_cond
            for cat in c.get("category", [])
            for cod in cat.get("coding", [])
        )
        checks.append(Check("7.4 Medical History", "Medical history Condition category problem-list-item", pl_ok))
    return checks


def print_report(checks):
    width = 72
    print("=" * width)
    print("SHR FHIR payload compliance checklist (implementation doc 7.1-7.4 + compliance markers)")
    print("=" * width)
    passed = 0
    failed = 0
    current_section = None
    for c in checks:
        if c.section != current_section:
            current_section = c.section
            print("\n[%s]" % current_section)
        status = "PASS" if c.passed else "FAIL"
        if c.passed:
            passed += 1
        else:
            failed += 1
        line = "  %-4s  %s" % (status, c.rule)
        if c.detail:
            line += "  (%s)" % c.detail
        print(line)
    print("\n" + "=" * width)
    print("Summary: %s passed, %s failed, %s total" % (passed, failed, passed + failed))
    print("=" * width)
    return failed == 0


def main():
    parser = argparse.ArgumentParser(description="Verify SHR FHIR payload test output against implementation doc.")
    parser.add_argument("--log", default=DEFAULT_LOG, help="Maven test stderr log file (default: %s)" % DEFAULT_LOG)
    parser.add_argument("--run", action="store_true", help="Run StructuredObsFhirPayloadTest before verifying")
    parser.add_argument("--lookup-dir", default="/opt/intelehealth/shr-config",
                        help="Lookup base dir (default: /opt/intelehealth/shr-config)")
    parser.add_argument("--repo-root", default=None, help="IHSHR repo root (auto-detected if omitted)")
    args = parser.parse_args()

    repo_root = args.repo_root
    if not repo_root:
        script_dir = os.path.dirname(os.path.abspath(__file__))
        repo_root = os.path.dirname(script_dir)

    lookup_files_dir = os.path.join(args.lookup_dir, "lookups")
    all_checks = check_lookup_files(lookup_files_dir)

    if args.run:
        code = run_payload_test(repo_root, args.log, args.lookup_dir)
        if code != 0:
            print("Note: mvn returned %s; continuing verification if log exists.\n" % code)

    if not os.path.isfile(args.log):
        print("ERROR: Log file not found: %s" % args.log, file=sys.stderr)
        print("Run with --run or generate manually:", file=sys.stderr)
        print("  mvn test -pl api -Dtest=StructuredObsFhirPayloadTest#printAllFourTypePayloads 2>> %s" % args.log,
              file=sys.stderr)
        print("  mvn test -pl api -Dtest=StructuredObsFhirPayloadComplianceTest#emitComplianceMarkers 2>> %s"
              % args.log, file=sys.stderr)
        sys.exit(1)

    log_text = read_log(args.log)
    print("Log file: %s (%s bytes)\n" % (args.log, len(log_text)))

    all_checks.extend(check_compliance_markers(log_text))
    all_checks.extend(check_log_sections(log_text))
    all_checks.extend(check_transaction_bundles(log_text))
    resources = all_resources(log_text)
    all_checks.extend(check_chief_complaint(resources))
    all_checks.extend(check_physical_exam(resources))
    all_checks.extend(check_family_history(resources))
    all_checks.extend(check_medical_history(resources))

    ok = print_report(all_checks)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
