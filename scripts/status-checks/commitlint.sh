#!/bin/bash
# Validates every commit in the range against Conventional Commits v1.0.0
# and publishes the result as the `commitlint` GitHub commit status
# (see DEVELOPMENT_STANDARDS.md section 2 and LOCAL_AUTOMATION_SETUP.md
# section 5-6).
#
# Usage: commitlint.sh [base-ref]
#   base-ref defaults to the merge-base of HEAD against origin/develop,
#   falling back to origin/main.
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/status-checks/lib.sh

BASE_REF="${1:-}"
if [ -z "$BASE_REF" ]; then
    BASE_REF=$(git merge-base HEAD origin/develop 2>/dev/null || true)
fi
if [ -z "$BASE_REF" ]; then
    BASE_REF=$(git merge-base HEAD origin/main 2>/dev/null || true)
fi
if [ -z "$BASE_REF" ]; then
    echo "Could not determine a base ref to lint commits against. Pass one explicitly: $0 <base-ref>" >&2
    exit 1
fi

if npx --no-install commitlint --from "$BASE_REF" --to HEAD; then
    post_status success commitlint "All commits follow Conventional Commits v1.0.0"
else
    post_status failure commitlint "One or more commits violate Conventional Commits v1.0.0"
    exit 1
fi
