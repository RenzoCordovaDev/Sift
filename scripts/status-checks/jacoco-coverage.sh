#!/bin/bash
# Verifies the >=80% JaCoCo coverage gate and publishes the result as the
# `jacoco-coverage-80` GitHub commit status (DEVELOPMENT_STANDARDS.md
# section 10).
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/status-checks/lib.sh

if ./gradlew jacocoCoverageVerification --console=plain; then
    post_status success jacoco-coverage-80 "Coverage >= 80% on domain/data (JaCoCo)"
else
    post_status failure jacoco-coverage-80 "Coverage below 80% threshold — see JaCoCo report"
    exit 1
fi
