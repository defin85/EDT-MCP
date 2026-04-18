## 1. Platform Baseline

- [x] 1.1 Confirm the compatibility branch strategy and record the chosen base for the 2024.2.5
      line.
- [x] 1.2 Retarget the Tycho target platform to the official EDT Ruby 2024.2 update site and
      remove unavailable install units.
- [x] 1.3 Reconcile `Import-Package` ranges and other OSGi dependencies with APIs available in EDT
      2024.2.

## 2. Runtime Compatibility

- [x] 2.1 Replace newer application update API usage in `get_applications` with
      2024.2-compatible synchronization/equality state lookups.
- [x] 2.2 Rework `update_database` to use `IInfobaseApplication` and
      `IInfobaseSynchronizationManager`.
- [x] 2.3 Rework `debug_launch` so pre-launch update checks and execution use the 2024.2
      synchronization APIs.
- [x] 2.4 Decide and document the 2024.2 behavior for `fullUpdate`.

## 3. Verification

- [x] 3.1 Run `mvn -f mcp/pom.xml -DskipTests package` until bundle, feature, and repository
      modules resolve on the 2024.2 line.
- [x] 3.2 Install the packaged plugin into a real EDT Ruby 2024.2.5 runtime and verify server
      startup plus `get_projects`, `get_applications`, `update_database`, and `debug_launch`.

## 4. Documentation

- [x] 4.1 Update `README.md` and release notes to state the supported EDT line and any known
      compatibility limitations honestly.
