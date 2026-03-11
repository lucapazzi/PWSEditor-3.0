# PWSEditor User Manual

The primary interaction guide is:

- `docs/CANVAS_INTERACTIONS.md`

Additional user-facing semantics notes:

- `docs/INTERNAL_CONFIGURATIONS.md`
- `docs/CBC_OBLIGATIONS.md`
- `docs/DEADLOCK_FRAMEWORK.md`
- `docs/WARNING_POLICY.md`

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
