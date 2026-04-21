#!/usr/bin/env python3
"""
Manual live probe for the extension lifecycle discovery/runtime slice.

This script is intentionally scoped to the currently delivered extension tools:
    - get_applications
    - get_extension_runtime_targets
    - list_infobase_extensions
    - check_extension_applicability

It does not attempt to prove an extension apply path.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any


@dataclass
class Config:
    host: str
    port: int
    configuration_project: str
    extension_project: str
    expected_category: str
    expected_installed: list[str]

    @property
    def base_url(self) -> str:
        return f"http://{self.host}:{self.port}"

    @property
    def mcp_url(self) -> str:
        return f"{self.base_url}/mcp"

    @property
    def health_url(self) -> str:
        return f"{self.base_url}/health"


_request_id = 0


def next_id() -> int:
    global _request_id
    _request_id += 1
    return _request_id


def decode_mcp_response(body: str) -> dict[str, Any]:
    stripped = body.lstrip()
    if stripped.startswith("{"):
        return json.loads(stripped)

    data_lines = [line[6:] for line in body.splitlines() if line.startswith("data: ")]
    if not data_lines:
        raise ValueError(f"Unsupported MCP response: {body[:200]!r}")
    return json.loads(data_lines[-1])


def send_jsonrpc(url: str, method: str, params: dict[str, Any] | None = None,
                 timeout: int = 120) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "jsonrpc": "2.0",
        "id": next_id(),
        "method": method,
    }
    if params is not None:
        payload["params"] = params

    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return decode_mcp_response(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8") if exc.fp else ""
        return {"error": {"code": exc.code, "message": f"HTTP {exc.code}: {body}"}}
    except urllib.error.URLError as exc:
        return {"error": {"code": -1, "message": str(exc.reason)}}


def call_tool(url: str, tool_name: str, arguments: dict[str, Any] | None = None,
              timeout: int = 120) -> tuple[dict[str, Any], float]:
    start = time.time()
    response = send_jsonrpc(url, "tools/call", {
        "name": tool_name,
        "arguments": arguments or {},
    }, timeout=timeout)
    return response, (time.time() - start) * 1000


def assert_success(response: dict[str, Any], context: str) -> dict[str, Any]:
    if response.get("error") is not None:
        raise AssertionError(f"{context}: transport/json-rpc error {response['error']}")
    result = response.get("result")
    if not isinstance(result, dict):
        raise AssertionError(f"{context}: missing result payload")
    structured = result.get("structuredContent")
    if not isinstance(structured, dict):
        raise AssertionError(f"{context}: missing structuredContent")
    return structured


def print_step(name: str, duration_ms: float, payload: dict[str, Any]) -> None:
    print(f"PASS  {name} ({duration_ms:.0f}ms)")
    print(json.dumps(payload, ensure_ascii=False, indent=2))


def parse_args() -> Config:
    parser = argparse.ArgumentParser(
        description="Manual live probe for EDT-MCP extension lifecycle discovery/runtime tools."
    )
    parser.add_argument("--host", default=os.environ.get("MCP_HOST", "localhost"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("MCP_PORT", "8765")))
    parser.add_argument("--configuration-project",
                        default=os.environ.get("MCP_CONFIGURATION_PROJECT"))
    parser.add_argument("--extension-project", default=os.environ.get("MCP_EXTENSION_PROJECT"))
    parser.add_argument("--expected-category",
                        default=os.environ.get(
                            "MCP_EXPECTED_EXTENSION_CATEGORY",
                            "extension_runtime_headless_unsafe",
                        ))
    parser.add_argument(
        "--expected-installed",
        default=os.environ.get("MCP_EXPECTED_INSTALLED_EXTENSIONS", ""),
        help="Comma-separated list of extension names expected in the target infobase.",
    )
    args = parser.parse_args()

    if not args.configuration_project or not args.extension_project:
        parser.error("--configuration-project and --extension-project are required")

    expected_installed = [item.strip() for item in args.expected_installed.split(",") if item.strip()]
    return Config(
        host=args.host,
        port=args.port,
        configuration_project=args.configuration_project,
        extension_project=args.extension_project,
        expected_category=args.expected_category,
        expected_installed=expected_installed,
    )


def ensure_health(config: Config) -> None:
    req = urllib.request.Request(config.health_url)
    with urllib.request.urlopen(req, timeout=10) as resp:
        body = json.loads(resp.read().decode("utf-8"))
    if body.get("status") != "ok":
        raise AssertionError(f"health check returned unexpected payload: {body}")
    print(f"PASS  health ({config.health_url})")
    print(json.dumps(body, ensure_ascii=False, indent=2))


def main() -> int:
    config = parse_args()

    print("=" * 72)
    print("  EDT-MCP Extension Lifecycle Live Probe")
    print(f"  Server: {config.mcp_url}")
    print(f"  Configuration project: {config.configuration_project}")
    print(f"  Extension project: {config.extension_project}")
    print("=" * 72)

    ensure_health(config)

    applications_response, applications_ms = call_tool(
        config.mcp_url,
        "get_applications",
        {"projectName": config.configuration_project},
    )
    applications = assert_success(applications_response, "get_applications")
    if not applications.get("success"):
        raise AssertionError(f"get_applications: tool returned failure payload {applications}")
    application_id = applications.get("defaultApplicationId")
    if not application_id:
        raise AssertionError(f"get_applications: missing defaultApplicationId in {applications}")
    print_step("get_applications", applications_ms, applications)

    runtime_targets_response, runtime_targets_ms = call_tool(
        config.mcp_url,
        "get_extension_runtime_targets",
        {"projectName": config.extension_project},
    )
    runtime_targets = assert_success(runtime_targets_response, "get_extension_runtime_targets")
    if runtime_targets.get("parentProjectName") != config.configuration_project:
        raise AssertionError(
            "get_extension_runtime_targets: unexpected parent "
            f"{runtime_targets.get('parentProjectName')!r}"
        )
    print_step("get_extension_runtime_targets", runtime_targets_ms, runtime_targets)

    listing_args = {
        "projectName": config.extension_project,
        "applicationId": application_id,
    }
    list_response, list_ms = call_tool(
        config.mcp_url,
        "list_infobase_extensions",
        listing_args,
    )
    listing = assert_success(list_response, "list_infobase_extensions")
    if not listing.get("success"):
        raise AssertionError(f"list_infobase_extensions: tool returned failure payload {listing}")
    installed = listing.get("installedExtensions") or []
    if config.expected_installed and sorted(installed) != sorted(config.expected_installed):
        raise AssertionError(
            "list_infobase_extensions: unexpected installed extensions "
            f"{installed!r}, expected {config.expected_installed!r}"
        )
    print_step("list_infobase_extensions", list_ms, listing)

    applicability_response, applicability_ms = call_tool(
        config.mcp_url,
        "check_extension_applicability",
        listing_args,
    )
    applicability = assert_success(applicability_response, "check_extension_applicability")
    if applicability.get("success") is not False:
        raise AssertionError(
            "check_extension_applicability: expected fail-closed success=false payload "
            f"but got {applicability}"
        )
    if applicability.get("category") != config.expected_category:
        raise AssertionError(
            "check_extension_applicability: unexpected category "
            f"{applicability.get('category')!r}, expected {config.expected_category!r}"
        )
    if applicability.get("applicable") is not False:
        raise AssertionError(
            "check_extension_applicability: expected applicable=false but got "
            f"{applicability.get('applicable')!r}"
        )
    print_step("check_extension_applicability", applicability_ms, applicability)

    repeat_list_response, repeat_list_ms = call_tool(
        config.mcp_url,
        "list_infobase_extensions",
        listing_args,
    )
    repeat_listing = assert_success(repeat_list_response, "repeat list_infobase_extensions")
    if not repeat_listing.get("success"):
        raise AssertionError(
            "repeat list_infobase_extensions: expected listing to remain healthy "
            f"but got {repeat_listing}"
        )
    print_step("repeat list_infobase_extensions", repeat_list_ms, repeat_listing)

    print("\nProbe result: delivered discovery/inspection/fail-closed slice is healthy.")
    print("Probe result: this does not prove apply_extension_to_infobase or a headless-safe apply path.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"FAIL  {exc}", file=sys.stderr)
        raise SystemExit(1)
