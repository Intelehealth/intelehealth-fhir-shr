#!/usr/bin/env bash
# Generate FHIR payload log from StructuredObsFhirPayloadTest and verify against implementation doc.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOG="${LOG:-/tmp/shr-fhir-payloads.log}"
LOOKUP_DIR="${LOOKUP_DIR:-/opt/intelehealth/shr-config}"

cd "${REPO_ROOT}"

echo "Repository: ${REPO_ROOT}"
echo "Lookup dir:   ${LOOKUP_DIR}"
echo "Log file:     ${LOG}"
echo ""

python3 "${SCRIPT_DIR}/verify_shr_fhir_payloads.py" --run --log "${LOG}" --lookup-dir "${LOOKUP_DIR}" --repo-root "${REPO_ROOT}"
EXIT=$?

echo ""
if [[ ${EXIT} -eq 0 ]]; then
  echo "All checks PASSED. Full payloads in: ${LOG}"
else
  echo "Some checks FAILED. Review: ${LOG}"
fi

exit ${EXIT}
