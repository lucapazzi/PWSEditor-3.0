## Unreleased - 2026-01-24

Summary: UI integration and robustness improvements for the PWS editor.

- UI: Left controller placeholder, header + menu layout; Assembly/Library CardLayout with toggle toolbar; embedded machine editor container reusing a single `embeddedEditor`.
- File management: integrated `PWSFileManager` / `PWSDocument` with `Save`, `Save As`, `Open`, `New`, `Close` and menu enable/disable tied to document presence.
- Assembly/Library: `PWSPanel` and `MachineLibraryPanel` listeners open machines in the embedded editor; library machines use `lib:` prefix for `embeddedMachineId`.
- Semantics & LTL: added cancellable `scheduleSemanticsRecalculation()` worker that calls `recalculateSemantics()`, updates annotations, marks document dirty, and triggers `runLTLChecks()`; added `ltlEditor` + checks dialog integration.
- Semantics: fix for autonomous transition exit zones after reload by recomputing reactive zones from current base/constraints instead of relying on stale cached semantics.
- Export & utilities: PDF export menu (vector preference) using `utility.PDFExporter` with graceful handling when PDFBox is unavailable.
- Misc: simplified logging in `main()`, helper `AppendingObjectOutputStream`, improved focus handling and many EDT/safety wrappers.

Risks & follow-ups:
- `initComponents()` contains places that assume `pwsStateMachine` non-null — consider defensive null checks.
- Validate `embeddedEditor.bindStateMachine(...)` covers all state transitions; test concurrency between semantics recalculation and file ops.
