#!/usr/bin/env bash
# =============================================================================
# sync_versions.sh — Update version across all Ucotron SDKs
# Usage: ./scripts/sync_versions.sh 0.2.0
# =============================================================================
set -euo pipefail

VERSION="${1:?Usage: $0 <version>}"

echo "Syncing all SDKs to version ${VERSION}..."

# TypeScript
sed -i.bak "s/\"version\": \".*\"/\"version\": \"${VERSION}\"/" typescript/package.json
rm -f typescript/package.json.bak

# Python
sed -i.bak "s/^version = \".*\"/version = \"${VERSION}\"/" python/pyproject.toml
rm -f python/pyproject.toml.bak

# Java (root build.gradle)
sed -i.bak "s/version = '.*'/version = '${VERSION}'/" java/build.gradle
rm -f java/build.gradle.bak

# PHP — version comes from git tags, no file to update
# Go  — version comes from git tags, no file to update

echo "Done. Updated versions:"
grep '"version"' typescript/package.json
grep 'version =' python/pyproject.toml
grep 'version =' java/build.gradle
