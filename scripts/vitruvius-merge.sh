#!/usr/bin/env bash
#
# vitruvius-merge.sh — Git custom merge driver for Vitruvius VSUM repositories.
#
# Git invokes this script once per conflicting model file during 'git merge'.
# On the first invocation it runs the full semantic merge and caches the
# result; subsequent invocations copy the cached file.
#
# Arguments (set by Git via the driver template):
#   $1 = %O  — path to the base (ancestor) temp file
#   $2 = %A  — path to the "ours" temp file (overwrite in place with result)
#   $3 = %B  — path to the "theirs" temp file
#   $4 = %L  — conflict marker size
#   $5 = %P  — pathname of the file relative to repo root
#
# Prerequisites:
#   - Java 17+
#   - The project has been built: ./mvnw clean install -Dmaven.test.skip=true
#   - .vitruvius/merge-driver.properties exists with 'specifications' property
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git rev-parse --show-toplevel)"

# Build the classpath from Maven (cached after first run)
CLASSPATH_FILE="${REPO_ROOT}/vsum/target/merge-driver-classpath.txt"
if [ ! -f "$CLASSPATH_FILE" ]; then
    echo "[vitruvius-merge] Building classpath (first run)..." >&2
    (cd "$REPO_ROOT" && ./mvnw -pl vsum dependency:build-classpath \
        -Dmdep.outputFile=target/merge-driver-classpath.txt \
        -Dmdep.includeScope=test -q) >&2
fi

CLASSPATH="$(cat "$CLASSPATH_FILE"):${REPO_ROOT}/vsum/target/classes:${REPO_ROOT}/vsum/target/test-classes"

java -cp "$CLASSPATH" \
    tools.vitruv.framework.vsum.branch.merge.GitMergeDriver \
    "$1" "$2" "$3" "$4" "$5"
