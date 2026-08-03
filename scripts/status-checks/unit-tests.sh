#!/bin/bash
# Runs the JVM unit test suite and publishes the result as the
# `unit-tests` GitHub commit status (DEVELOPMENT_STANDARDS.md section 10).
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/status-checks/lib.sh

if ./gradlew test --console=plain; then
    post_status success unit-tests "./gradlew test passed"
else
    post_status failure unit-tests "./gradlew test failed — see Gradle output"
    exit 1
fi
