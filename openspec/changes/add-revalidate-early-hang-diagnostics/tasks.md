## 1. Contract And Scope

- [x] 1.1 Зафиксировать scope как observability/hardening change для раннего
      object-scoped `revalidate_objects` hang, а не как доказанный root-cause
      fix EDT.
- [x] 1.2 Определить обязательные ранние checkpoints и correlation fields для
      диагностики `revalidate_objects`.
- [x] 1.3 Зафиксировать ожидания от watchdog для refresh-stage path:
      one-shot warning, thread dump evidence и различимость раннего hang-а от
      поздних build/derived-data waits.

## 2. Runtime Diagnostics

- [x] 2.1 Добавить checkpoints до прежней слепой зоны:
      `project handle resolved`, `project exists`, `project is open`,
      `refresh stage start`, `reporter.stage(refresh) start/returned`,
      `refreshLocal start/completed`.
- [x] 2.2 Расширить watchdog coverage на refresh-stage path так, чтобы ранний
      hang мог оставить evidence до `object lookup completed` и
      `scheduleValidation returned`.
- [x] 2.3 Обеспечить единый diagnostic label с `requestId`, `operationId`,
      `projectName`, object summary и executor thread.

## 3. Verification And Runbook

- [x] 3.1 Добавить focused coverage на diagnostic label formatting и watchdog
      wiring там, где это практично покрывается unit-level.
- [x] 3.2 Повторить live reproduce в `TP1141` и собрать `.metadata/.log`
      evidence; если проблема не воспроизводится, явно зафиксировать этот
      runtime evidence gap вместо ложного claim о root-cause fix.
- [x] 3.3 Обновить agent-facing verification notes и incident runbook:
      какие checkpoints искать, как трактовать отсутствие следующего checkpoint
      и где ожидать `watchdog fired` / thread dump markers.

  2026-04-21: до рестарта EDT live reproduce на `TP1141` (`requestId=303`,
  `operationId=a13d45cb-965b-4254-b9f0-b33e6cf1d1f1`) ещё показывал только
  старый subset checkpoints. После рестарта `TP1141` с новым plugin/runtime
  live reproduce подтвердил parity с checked-in change:
  `requestId=503` (`operationId=b6f3dd3e-e275-4b2c-895d-38f111869163`) и
  повторный `requestId=504`
  (`operationId=d3428167-ddde-4493-959c-20988065ceb3`) оба завершились успешно,
  а `.metadata/.log` показал полный ранний trail:
  `start -> resolving workspace project handle -> project handle resolved ->
  project exists -> project is open -> refresh stage start ->
  reporter.stage(refresh) start/returned -> refreshLocal start/completed ->
  refresh stage completed -> object lookup completed ->
  scheduleValidation returned -> waiting for build jobs ->
  waiting for derived data -> build and derived-data wait completed`.
  На текущем установленном `TP1141` runtime deployment gap больше не наблюдается.
