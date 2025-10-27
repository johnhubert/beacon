#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PROJECT_ROOT=$(cd "${SCRIPT_DIR}/.." && pwd)

TOOL_SCRIPT="${PROJECT_ROOT}/build/tools/scripts/congress-cli.sh"

if [[ ! -x "${TOOL_SCRIPT}" ]]; then
  echo "Unable to locate congress-cli distribution. Run './gradlew buildTools' first." >&2
  exit 1
fi

exec "${TOOL_SCRIPT}" "$@"
