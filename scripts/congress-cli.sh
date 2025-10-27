#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PROJECT_ROOT=$(cd "${SCRIPT_DIR}/.." && pwd)

if [[ ! -x "${PROJECT_ROOT}/gradlew" ]]; then
  echo "Unable to locate Gradle wrapper. Run this script from within the repository." >&2
  exit 1
fi

exec "${PROJECT_ROOT}/gradlew" :tools:congress-cli:run --args "$*"
