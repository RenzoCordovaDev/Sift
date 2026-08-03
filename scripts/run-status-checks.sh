#!/bin/bash
# Runs and publishes the deterministic status checks required by the
# `protect-develop`/`protect-main` GitHub rulesets (see
# LOCAL_AUTOMATION_SETUP.md sections 5-6): commitlint, unit-tests, and
# jacoco-coverage-80. `code-review-agent` is not run here since it
# requires an actual code review judgment — publish it separately with
# scripts/status-checks/code-review.sh once the review is done.
set -euo pipefail
cd "$(dirname "$0")/.."

FAILED=0
scripts/status-checks/commitlint.sh "$@" || FAILED=1
scripts/status-checks/unit-tests.sh || FAILED=1
scripts/status-checks/jacoco-coverage.sh || FAILED=1

if [ "$FAILED" -ne 0 ]; then
    echo "One or more status checks failed. See output above." >&2
    exit 1
fi

echo "All deterministic status checks passed."
