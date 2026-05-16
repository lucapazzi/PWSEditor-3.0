# PWSEditor User Manual

The primary interaction guide is:

- `docs/CANVAS_INTERACTIONS.md`

Additional user-facing semantics notes:

- `docs/INTERNAL_CONFIGURATIONS.md`
- `docs/CBC_OBLIGATIONS.md`
- `docs/DEADLOCK_FRAMEWORK.md`
- `docs/WARNING_POLICY.md`
- `docs/ST_EXPORT.md`

## Import and Export

- `File -> Open...` imports `.pws` workspaces.
- The standalone state-machine editor supports `.sm` single-machine files, and the library panel supports `.mlib` machine-library files.
- `File -> Export as PDF` and `File -> Export as PNG` export the active canvas; selected objects are exported alone when a selection exists.
- `File -> Export as ST` exports the controller, simple assembly machines, and a generated `PLC_PRG` scaffold as IEC 61131-3 Structured Text.
- `File -> Export as PLCOpen XML` exports the same controller/component function blocks plus `PLC_PRG` in PLCopen XML. PLCopen XML import is not currently implemented.
- Export naming, translation rules, and current exporter constraints are documented in `docs/ST_EXPORT.md`.

## Timed States and Timeout Transitions

- Timed states are available in both the controller editor and the component-machine editor.
- Right-click a state and toggle **Timed state** to show a time badge.
- Use **Edit timed label...** to edit the time value shown in the badge.
- A timed state may have one timeout transition, created with **Create timeout transition: choose arrival state**.
- Timeout transitions are drawn with the same timeout marker in controller and component editors.
- In component machines, timeout transitions are treated as reactive internal evolution when computing reactive spaces and exit zones.

## Fail-State Behavior (Obligations)

When a controller state is marked **Fail state**, the editor masks only a specific subset of obligations:

- masked: uncovered exit-zone checks
- masked: primary deadlock checks
- masked: secondary (internal) deadlock checks

These checks are still active for fail states:

- orphan guard checks
- orphan action checks
- constraint-violation checks
- orphan exit-zone checks
- unreachable-state detection

Fail-state marking does not stop exit-zone computation. It changes how selected diagnostics are interpreted and reported.

See detailed rationale and examples:

- `docs/CBC_OBLIGATIONS.md` (section: Fail-state masking)
- `docs/INTERNAL_CONFIGURATIONS.md` (section: Fail-State Note)
- `docs/DEADLOCK_FRAMEWORK.md` (section: Fail-state masking)

## Assembly Components Preview Panel (Current UI)

- Located on the **left side**, below the controller editor.
- Hidden by default.
- Toggle from `View -> Show assembly components`.
- Previews are read-only navigation cards.
- Clicking a preview opens/selects that component in the embedded component editor.
- The preview area is vertically resizable against the controller editor using the divider.
