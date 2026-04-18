# Change: Add EDT Ruby 2024.2.5 compatibility

## Why

The current repository line targets newer EDT platform APIs and target units, while a supported
build for EDT Ruby 2024.2.5 is needed for users who cannot move to the newer line yet. Without an
explicit compatibility change, the build, runtime tool behavior, and documentation will drift into
an unsupported "works by accident" state for 2024.2.5.

## What Changes

- Add an explicit compatibility capability and acceptance criteria for the EDT Ruby 2024.2.5 line.
- Retarget the build, target platform, and bundle imports to the official EDT 2024.2 update site.
- Rework `get_applications`, `update_database`, and `debug_launch` onto the 2024.2-compatible
  infobase synchronization APIs.
- Document any intentionally degraded behavior, especially around `fullUpdate`, instead of
  implying parity with newer EDT lines.

## Impact

- Affected specs: `edt-platform-compatibility`
- Affected code: Tycho target platform, bundle manifest imports, application/update tools,
  packaging modules, `README.md`
- Validation: `mvn -f mcp/pom.xml -DskipTests package` on the 2024.2 line plus manual checks in a
  real EDT Ruby 2024.2.5 installation
