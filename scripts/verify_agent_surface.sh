#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

required_paths=(
  "AGENTS.md"
  ".codex/config.toml"
  "docs/agent/index.md"
  "docs/agent/architecture-map.md"
  "docs/agent/verification.md"
  "docs/agent/task-artifacts.md"
  "docs/agent/codex-setup.md"
  "docs/agent/long-running-ops.md"
  "docs/agent/beads-workflow.md"
  "docs/agent/generated-tool-catalog.md"
  "openspec/AGENTS.md"
  "openspec/project.md"
  "openspec/specs/agent-productivity-surface/spec.md"
  "openspec/specs/mcp-server-core/spec.md"
  "openspec/specs/workspace-and-metadata-management/spec.md"
  "openspec/specs/bsl-analysis-and-navigation/spec.md"
  "openspec/specs/long-running-operations/spec.md"
  "mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/AGENTS.override.md"
  "mcp/tests/com.ditrix.edt.mcp.server.tests/AGENTS.override.md"
  "tests/e2e/AGENTS.override.md"
  "scripts/codex-onboard.sh"
  "scripts/generate_agent_refs.py"
  "scripts/verify_agent_surface.sh"
  ".github/workflows/agent-surface.yml"
)

for path in "${required_paths[@]}"; do
  if [[ ! -e "$path" ]]; then
    echo "missing required agent-surface path: $path" >&2
    exit 1
  fi
done

if rg -n "DO NOT BUILD YOURSELF" .github/copilot-instructions.md >/dev/null 2>&1; then
  echo "conflicting copilot instruction still present" >&2
  exit 1
fi

spec_count="$(find openspec/specs -mindepth 2 -maxdepth 2 -name spec.md | wc -l | tr -d ' ')"
if [[ "$spec_count" -lt 5 ]]; then
  echo "expected at least 5 baseline OpenSpec specs, got $spec_count" >&2
  exit 1
fi

python3 scripts/generate_agent_refs.py --check

echo "agent surface verified"
