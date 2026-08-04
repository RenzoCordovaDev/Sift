#!/bin/bash
# Shared helpers for the status-check scripts described in
# LOCAL_AUTOMATION_SETUP.md section 5. Sourced by the individual
# check scripts, never run directly.
set -euo pipefail

# Posts a GitHub commit status for the given context.
# Usage: post_status <state: success|failure|pending|error> <context> <description>
post_status() {
    local state="$1"
    local context="$2"
    local description="$3"
    local repo sha

    repo=$(gh repo view --json nameWithOwner -q .nameWithOwner)
    sha=$(git rev-parse HEAD)

    # GitHub truncates commit status descriptions at 140 characters.
    description="${description:0:140}"

    gh api "repos/$repo/statuses/$sha" \
        -f state="$state" \
        -f context="$context" \
        -f description="$description" >/dev/null

    echo "[$context] posted '$state' for $sha: $description"
}
