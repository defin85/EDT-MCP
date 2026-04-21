## 1. Baseline And API Proof

- [x] 1.1 Зафиксировать live verdict по candidate extension apply paths: public EDT
      applicability/XML path не стал безопасным MCP backend, тогда как internal EDT
      synchronization bridge дал положительный no-op и mutation proof на demo workspace.
- [x] 1.2 Зафиксировать source-format verdict и fail-closed fallback plan: live XML-contract probe
      показал, что workspace extension project `src/` не совпадает с EDT XML export/import layout,
      поэтому для apply нужен отдельный export/staging path, а не direct `src` handoff.

## 2. Shared Extension Runtime Context

- [x] 2.1 Добавить shared extension runtime context с parent configuration project, extension
      identity и target application resolution.
- [x] 2.2 Добавить отдельную machine-readable failure surface для extension lifecycle tools
      (`extension_parent_missing`, `extension_target_not_found`, `extension_runtime_check_failed`
      или эквивалентный стабильный vocabulary).

## 3. Discovery And Inspection Tools

- [x] 3.1 Реализовать `get_extension_properties` на extension-aware project API без перегрузки
      `get_configuration_properties`.
- [x] 3.2 Реализовать `get_extension_runtime_targets`, который возвращает parent configuration
      project и доступные infobase applications для extension project.
- [x] 3.3 Реализовать `list_infobase_extensions` и fail-closed headless-safe boundary для
      `check_extension_applicability`, чтобы tool не запускал EDT runtime path, способный открыть
      интерактивный диалог доступа к ИБ или повесить bridge.

## 4. Apply Flow

- [x] 4.1 Реализовать `apply_extension_to_infobase` как explicit extension lifecycle tool, не
      переиспользуя generic `update_database` contract.
- [x] 4.2 Подключить async-first task-backed execution, progress reporting и blocking diagnostics
      для `apply_extension_to_infobase`.
- [x] 4.3 Сохранить legacy configuration-only rejection для `get_applications`, `update_database`
      и `debug_launch` на extension projects, но добавить migration hint на dedicated lifecycle
      surface.

## 5. Docs And Verification

- [x] 5.1 Обновить `README.md` честной матрицей extension lifecycle support и новыми tool
      contracts.
- [x] 5.2 Добавить repo-owned live probe и документировать canonical extension fixture/workspace
      setup для live verification текущего extension lifecycle slice.
- [x] 5.3 Прогнать `openspec validate --strict --no-interactive`, full Tycho verify и live
      verification на configuration + extension matrix для delivered discovery / inspection /
      fail-closed scope, а также manual mutation proof для internal apply backend.
- [x] 5.4 Прогнать live verification именно через публичный `apply_extension_to_infobase` на
      canonical demo fixture: bare call создаёт task, `tasks/result` возвращает final payload, а
      controlled drift в extension project переводится из `INCREMENTAL_UPDATE_REQUIRED / NOT_EQUAL`
      обратно в `UPDATED / EQUAL` и затем чисто откатывается тем же tool.
