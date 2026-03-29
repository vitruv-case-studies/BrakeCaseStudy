#!/usr/bin/env bash
#
# setup-merge-driver.sh — Configure Git to use the Vitruvius semantic merge driver.
#
# Run this once in any Git repository that contains a Vitruvius VSUM
# with .model files. It configures:
#   1. .git/config  — registers the 'vitruvius' merge driver
#   2. .gitattributes — associates *.model files with the driver
#   3. .vitruvius/merge-driver.properties — lists the Reaction specifications
#
# Usage:
#   ./scripts/setup-merge-driver.sh [--specs "fully.qualified.Spec1,fully.qualified.Spec2,..."]
#
# If --specs is omitted, the BrakeCaseStudy default specifications are used.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git rev-parse --show-toplevel)"

# Default specifications for BrakeCaseStudy
DEFAULT_SPECS="mir.reactions.brakesystem2cad.Brakesystem2cadChangePropagationSpecification,mir.reactions.cad2brakesystem.Cad2brakesystemChangePropagationSpecification,mir.reactions.brakesystem2safety.Brakesystem2safetyChangePropagationSpecification"

SPECS="$DEFAULT_SPECS"
while [[ $# -gt 0 ]]; do
    case $1 in
        --specs) SPECS="$2"; shift 2 ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

echo "[setup] Repository root: $REPO_ROOT"

# 1. Register the merge driver in .git/config
DRIVER_CMD="${SCRIPT_DIR}/vitruvius-merge.sh %O %A %B %L %P"
git config merge.vitruvius.name "Vitruvius semantic merge"
git config merge.vitruvius.driver "$DRIVER_CMD"
echo "[setup] Registered merge driver 'vitruvius' in .git/config"

# 2. Add *.model to .gitattributes (if not already present)
GITATTRIBUTES="${REPO_ROOT}/.gitattributes"
if ! grep -q 'merge=vitruvius' "$GITATTRIBUTES" 2>/dev/null; then
    echo '*.model merge=vitruvius' >> "$GITATTRIBUTES"
    echo "[setup] Added '*.model merge=vitruvius' to .gitattributes"
else
    echo "[setup] .gitattributes already configured"
fi

# 3. Write merge-driver.properties
mkdir -p "${REPO_ROOT}/.vitruvius"
cat > "${REPO_ROOT}/.vitruvius/merge-driver.properties" <<EOF
# Vitruvius Git merge driver configuration.
# Comma-separated fully qualified class names of ChangePropagationSpecification implementations.
specifications=${SPECS}
EOF
echo "[setup] Wrote .vitruvius/merge-driver.properties"

echo ""
echo "Done. To use:"
echo "  1. Build the project:  ./mvnw clean install -Dmaven.test.skip=true"
echo "  2. Merge as usual:     git merge <branch>"
echo ""
echo "The semantic merge driver will be invoked automatically for *.model files."
