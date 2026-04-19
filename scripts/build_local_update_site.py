#!/usr/bin/env python3
from __future__ import annotations

import argparse
import shutil
import time
import zipfile
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-repository", required=True)
    parser.add_argument("--composite-root", required=True)
    parser.add_argument("--zip-output", required=True)
    parser.add_argument("--plugin-id", default="com.ditrix.edt.mcp.server")
    parser.add_argument("--repository-name", default="EDT MCP Local Composite Repository")
    return parser.parse_args()


def detect_version(source_repository: Path, plugin_id: str) -> str:
    matches = sorted((source_repository / "plugins").glob(f"{plugin_id}_*.jar"))
    if len(matches) != 1:
        raise SystemExit(f"expected exactly one plugin artifact for {plugin_id}, found {len(matches)}")
    return matches[0].stem.split(f"{plugin_id}_", 1)[1]


def copy_repository(source_repository: Path, composite_root: Path, version: str) -> None:
    releases_dir = composite_root / "releases"
    releases_dir.mkdir(parents=True, exist_ok=True)
    destination = releases_dir / version
    if destination.exists():
        shutil.rmtree(destination)
    shutil.copytree(source_repository, destination)


def iter_children(composite_root: Path) -> list[str]:
    releases_dir = composite_root / "releases"
    if not releases_dir.exists():
        return []
    return [f"releases/{path.name}" for path in sorted(p for p in releases_dir.iterdir() if p.is_dir())]


def write_composite_xml(path: Path, kind: str, repository_name: str, children: list[str]) -> None:
    if kind == "metadata":
        pi = "compositeMetadataRepository"
        repo_type = "org.eclipse.equinox.internal.p2.metadata.repository.CompositeMetadataRepository"
        root_name = "compositeContent.xml"
    else:
        pi = "compositeArtifactRepository"
        repo_type = "org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository"
        root_name = "compositeArtifacts.xml"

    timestamp = str(int(time.time() * 1000))
    lines = [
        "<?xml version='1.0' encoding='UTF-8'?>",
        f"<?{pi} version='1.0.0'?>",
        f"<repository name='{repository_name}' type='{repo_type}' version='1.0.0'>",
        "  <properties size='1'>",
        f"    <property name='p2.timestamp' value='{timestamp}'/>",
        "  </properties>",
        f"  <children size='{len(children)}'>",
    ]
    lines.extend(f"    <child location='{child}'/>" for child in children)
    lines.extend(["  </children>", "</repository>", ""])
    (path / root_name).write_text("\n".join(lines), encoding="utf-8")


def write_p2_index(path: Path) -> None:
    (path / "p2.index").write_text(
        "\n".join(
            [
                "version = 1",
                "metadata.repository.factory.order = compositeContent.xml,!",
                "artifact.repository.factory.order = compositeArtifacts.xml,!",
                "",
            ]
        ),
        encoding="utf-8",
    )


def build_zip(source_dir: Path, zip_output: Path) -> None:
    zip_output.parent.mkdir(parents=True, exist_ok=True)
    if zip_output.exists():
        zip_output.unlink()
    with zipfile.ZipFile(zip_output, mode="w", compression=zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(source_dir.rglob("*")):
            if path.is_dir():
                continue
            archive.write(path, path.relative_to(source_dir))


def main() -> int:
    args = parse_args()
    source_repository = Path(args.source_repository).resolve()
    composite_root = Path(args.composite_root).resolve()
    zip_output = Path(args.zip_output).resolve()

    if not source_repository.exists():
        raise SystemExit(f"source repository does not exist: {source_repository}")

    composite_root.mkdir(parents=True, exist_ok=True)
    version = detect_version(source_repository, args.plugin_id)
    copy_repository(source_repository, composite_root, version)
    children = iter_children(composite_root)
    write_composite_xml(composite_root, "artifact", args.repository_name, children)
    write_composite_xml(composite_root, "metadata", args.repository_name, children)
    write_p2_index(composite_root)
    build_zip(composite_root, zip_output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
