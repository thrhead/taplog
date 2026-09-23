# Management UI Contract

## Navigation

The management graph is limited to:

- Management entry point
- Active/archived Record list
- Record create/edit
- Record assignment (valid active Targets plus distinct no-Target option)
- Active/archived Target list
- Target create/edit
- Permanent-delete impact confirmation for permitted Record/scope deletion

No Home dashboard, Timeline, Event execution, Duration/State execution, NFC, Widget, Quick Settings, parser, backup, statistics, monetization, AI, or sync destination is added.

## Required screen states

Each list and editor has deterministic loading, empty, content, invalid, conflict, storage-error, and success rendering as applicable. Destructive confirmation is used only for permanent deletion. Archive, unarchive, link, unlink, and relink have direct action affordances and do not require confirmation.

## Localization contract

UI copy is selected by (result category, ResultReason) or a stable management state key. Raw exception text is never rendered. Applied feedback is shown only after the core commit returns Applied; feedback rendering failure cannot reverse or relabel the committed state.

## History and orphan visibility

Management surfaces may show lifecycle/assignment status and core-provided effects, but they do not display reconstructed historical data as if it were current. Relinking enables future use only; existing orphaned historical bindings remain orphaned.
