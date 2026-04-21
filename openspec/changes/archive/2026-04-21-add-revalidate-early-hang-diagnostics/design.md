## Context

Есть live-incident в `TP1141`, где object-scoped `revalidate_objects` на
`Document.вд_ОтчетностьIPC` подвешивает EDT настолько рано, что лог успевает
записать только старт операции. Это уже исключает часть поздних hypotheses
вокруг `waitForBuildAndDerivedData`, но не даёт точного ответа, где именно
происходит hang.

Сейчас честный вывод такой:

- проблема ранняя и лежит внутри revalidate path;
- текущий лог не доказывает root cause;
- без дополнительных checkpoints или thread dump мы видим только narrowing, а
  не точное место поломки.

## Goals / Non-Goals

- Goals:
  - локализовать ранний hang как минимум до конкретной подстадии refresh path
  - обеспечить thread-dump evidence для раннего зависания, а не только для
    поздних build/derived-data waits
  - сделать incident interpretation честной и повторяемой по логу
- Non-Goals:
  - обещать немедленный fix неизвестного EDT deadlock/lock wait
  - менять sync/task contract для partial `revalidate_objects`
  - вводить новый публичный MCP tool только ради этой диагностики

## Decisions

- Decision: трактовать change как observability/hardening, а не как
  root-cause fix.
  - Alternatives considered:
    - оформлять change как functional fix для `revalidate_objects`
    - откладывать change до получения thread dump
  - Rationale: evidence пока недостаточно для честного обещания fix-а, но
    достаточно, чтобы улучшить диагностируемость и сузить problem area.

- Decision: добавить checkpoints максимально рано, до и вокруг refresh-stage.
  - Alternatives considered:
    - продолжать логировать только build/derived-data waits
    - логировать только `refreshLocal completed`
  - Rationale: текущая слепая зона находится раньше, поэтому поздние checkpoints
    не помогают при этом типе hang-а.

- Decision: использовать тот же correlation label для checkpoints и watchdog.
  - Alternatives considered:
    - раздельные форматы для stage logs и thread dump warnings
    - reliance only on plain human-readable text
  - Rationale: без общей identity incident analysis по `.metadata/.log`
    становится хрупким и плохо автоматизируемым.

- Decision: watchdog остаётся one-shot и пишет thread-dump evidence в лог.
  - Alternatives considered:
    - новый MCP API для live dump retrieval
    - агрессивный periodic dump spam
  - Rationale: задача сейчас в evidence capture без раздувания runtime surface
    и без log storm.

## Risks / Trade-offs

- Если EDT/JVM зависнет на уровне, где watchdog thread не сможет выполниться,
  thread-dump evidence всё равно может не появиться.
- Дополнительные checkpoints увеличат шум в `.metadata/.log`, поэтому label и
  wording должны быть стабильными и компактными.
- Даже после change проблема может временно не воспроизводиться; это не
  означает, что root cause найден или устранён.

## Migration Plan

1. Добавить ранние checkpoints и refresh-stage watchdog coverage.
2. Обновить runbook для расследования ранних hang-ов `revalidate_objects`.
3. Переустановить plugin и повторить live reproduce на `TP1141`.
4. Зафиксировать либо новый narrowing с конкретной подстадией, либо явный
   evidence gap, если hang не воспроизводится.

## Open Questions

- Нужно ли логировать полный thread dump или достаточно фильтрованного набора
  потоков, связанных с MCP/EDT resource/build path?
- Должен ли watchdog threshold для refresh-stage совпадать с поздним build
  watchdog, или ранний path требует отдельной настройки?
