## Context

`EDT-MCP` уже имеет long-running runtime surface, task-backed execution, progress reporting и
runtime-target resolution для configuration projects и infobase applications. Это даёт хороший
фундамент для запуска unit tests, но сам тестовый слой пока отсутствует: MCP-клиент не может
инициировать supported test run и получить machine-readable итог без ручного EDT handoff.

По внешним источникам и по 1С-экосистеме роли инструментов расходятся:

- `YAxUnit` — unit-first engine с EDT-oriented workflow и отчётами для автоматизации.
- `Vanessa Automation` / `Vanessa-ADD` — более широкий orchestration layer для BDD/smoke/scenario
  testing, но с заметно более тяжёлым runtime contract (`TestManager`, `VAParams`, browser/client
  orchestration, external EPF flow).
- `edt-test-runner` — удобный EDT plugin для UI-driven работы с YAxUnit, но слишком жёсткая
  внешняя зависимость для repo-owned MCP capability.

Поэтому первый rollout должен добавить unit-test capability как узкий, честный и проверяемый
runtime surface поверх уже существующего `EDT-MCP`, а не тащить в plugin весь внешний test
ecosystem.

## Related Work

- `mcp-onec-test-runner` показывает, что MCP-driven запуск `YAxUnit` уже востребован и practically
  useful для AI-assisted 1C workflow.
- При этом его архитектура — это отдельный standalone MCP server со своей project configuration
  model (`base-path`, `source-set`, `connection-string`, builder strategy, EDT CLI bootstrap), а не
  capability внутри существующего EDT plugin/runtime surface.
- Этот prior art полезен как reference для provider choice и report UX, но не как drop-in
  architecture для `EDT-MCP`.
- Дополнительное ограничение: documented EDT path у `mcp-onec-test-runner` ориентирован на
  `1C:EDT 2025.1+`, тогда как текущий live contour `EDT-MCP` ещё эксплуатируется на compatibility
  line `2024.2.5.16`. Поэтому перенимать их EDT CLI strategy без adaptation было бы overclaim.
- Отдельно зафиксирован будущий `WebSocket` fast path, но он у external server пока не является
  базовым закрытым контрактом. Значит, `EDT-MCP` first rollout тоже не должен строиться вокруг
  недоказанной persistent-session semantics.

## Goals / Non-Goals

- Goals:
  - запускать supported unit tests через MCP на выбранной configuration project/application target
  - использовать `YAxUnit` как единственный provider первого rollout-а
  - встроить запуск в текущий async-first task/progress contract
  - возвращать retained final summary и stable `runId`
  - давать read-only retrieval сохранённого отчёта через отдельный tool
  - fail-close при неподготовленном runtime, неизвестном provider или unsafe overlap
- Non-Goals:
  - запускать BDD/smoke/scenario tests через `Vanessa Automation` в этом change
  - реализовывать debug execution или step-through тестов
  - поддерживать extension projects в первом rollout-е
  - делать обязательной внешнюю зависимость на `edt-test-runner`
  - дублировать standalone architecture external MCP test runners внутри `EDT-MCP`
  - обещать "запуск тестов без сборки/обновления" как базовый contract первого rollout-а
  - строить первый rollout вокруг persistent WebSocket/hot-session execution
  - строить generic artifact download surface для произвольных бинарных файлов

## Decisions

- Decision: выбрать `YAxUnit` как provider первого rollout-а.
  - Alternatives considered:
    - использовать `Vanessa Automation` как основной engine и поверх него выделить unit-test mode
    - делать provider-neutral surface сразу для `YAxUnit` и `Vanessa`
    - опираться на legacy `xUnitFor1C`
  - Rationale: `YAxUnit` ближе всего к unit-test use case и EDT workflow, тогда как `Vanessa`
    решает более широкий и более тяжёлый класс задач. Provider-neutral design можно оставить
    внутри кода, но public scope первого rollout-а должен быть узким.

- Decision: реализовать repo-owned runner inside `EDT-MCP`, а не требовать внешний
  `edt-test-runner` plugin как обязательный слой.
  - Alternatives considered:
    - встроить прямую зависимость на `edt-test-runner`
    - ограничиться запуском существующей EDT launch configuration из UI ecosystem
    - повторить standalone-server architecture `mcp-onec-test-runner`
  - Rationale: `EDT-MCP` должен оставаться самодостаточным transport/runtime layer. Внешний UI
    plugin допустим как reference, но не как hard runtime prerequisite. Аналогично, внешний
    standalone MCP server полезен как prior art, но не должен подменять уже существующую
    EDT-native project/runtime model `EDT-MCP`.

- Decision: ограничить первый rollout configuration projects only.
  - Alternatives considered:
    - сразу поддержать и extension projects через parent runtime target
  - Rationale: extension-path уже сложнее по routing и runtime identity, а unit-test surface нужно
    сначала стабилизировать на configuration-only contour.

- Decision: сделать `run_unit_tests` async-first tool.
  - Alternatives considered:
    - оставить sync-first запуск с optional progress
  - Rationale: тестовый прогон — типично долгий runtime path с логами, отчётами и возможным
    ожиданием синхронизации. Async-first contract лучше соответствует уже проверенной архитектуре
    long-running operations.

- Decision: разделить запуск и retrieval результатов на `run_unit_tests` + `get_test_run_report`.
  - Alternatives considered:
    - возвращать только filesystem paths до отчётов в final task result
    - запихивать весь raw report в `tasks/result`
  - Rationale: filesystem paths неустойчивы для удалённого MCP endpoint, а большие raw reports не
    подходят как единственный terminal payload. Stable `runId` и read-only retrieval дают более
    честный контракт.

- Decision: использовать public EDT runtime bridge как launch path для первого rollout-а; на
  supported compatibility line `RuntimeExecutionArguments#setStartupOption(...)` уже позволяет
  честно передать YAxUnit startup contract, поэтому отдельный launch-configuration fallback в этом
  change не нужен.
  - Alternatives considered:
    - сразу строить решение только на launch configurations
    - добавлять temporary launch-configuration adapter заранее "на всякий случай"
  - Rationale: public runtime bridge предпочтителен и на целевом baseline уже предоставляет
    supported startup option API. Добавлять второй launch path без runtime evidence было бы лишней
    сложностью и расширением surface beyond the verified need.

- Decision: не включать rebuild-free fast path или persistent hot session в первый rollout.
  - Alternatives considered:
    - оптимизировать первый rollout вокруг long-lived WebSocket session с почти мгновенным rerun
    - обещать selective update only for test extension как часть базового contract
  - Rationale: даже external prior art и живое обсуждение YAxUnit показывают, что при изменении
    тестируемого кода rebuild/update semantics никуда не исчезают. Поэтому v1 должен сначала
    закрыть корректный и проверяемый baseline, а ускоряющие режимы выносить в follow-up change.

## Risks / Trade-offs

- Если будущая compatibility line уберёт или изменит `RuntimeExecutionArguments#setStartupOption`,
  может потребоваться отдельный launch-configuration fallback. Текущий rollout этого не доказывает
  для произвольных версий EDT за пределами поддержанного baseline.
- External prior art может подтолкнуть к избыточному scope creep: build orchestration, project
  discovery, standalone config parsing и hot-session execution. Для `EDT-MCP` это риск размыть
  change beyond the currently justified capability.
- Unit tests могут менять состояние infobase; без fail-closed scheduling и preflight такие runs
  будут конфликтовать с `update_database` и другими runtime operations.
- Полный raw JUnit/XML/Allure artifact может быть слишком большим для обычного terminal payload;
  поэтому первый rollout должен отделять summary от report retrieval.
- Live verification требует реального contour с установленным YAxUnit. Source-only validation не
  докажет runtime viability.

## Migration Plan

1. Зафиксировать public contract и scope boundaries в OpenSpec.
2. Добавить shared runtime adapter и retained run store.
3. Реализовать `run_unit_tests` как async-first tool.
4. Реализовать `get_test_run_report` как read-only retrieval surface.
5. Обновить README, testing docs и agent guidance с явными prerequisites и limitations.

## Open Questions

- Нужно ли в первом rollout-е возвращать raw JUnit XML целиком, или достаточно summary плюс
  текстовый failure/log report с manifest metadata?
