#!/bin/bash
# Publishes the `code-review-agent` GitHub commit status.
#
# Unlike the other checks, the review itself is a judgment call made by
# the code-review-agent (or a human reviewer) against
# CODE_QUALITY_STANDARDS.md and ARCHITECTURE.md — it isn't something a
# script can compute. This script only publishes the resulting verdict.
#
# Usage: code-review.sh <success|failure> "<description>"
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/status-checks/lib.sh

STATE="${1:?Usage: $0 <success|failure> \"description\"}"
DESCRIPTION="${2:?Usage: $0 <success|failure> \"description\"}"

if [ "$STATE" != "success" ] && [ "$STATE" != "failure" ]; then
    echo "State must be 'success' or 'failure', got: $STATE" >&2
    exit 1
fi

post_status "$STATE" code-review-agent "$DESCRIPTION"
