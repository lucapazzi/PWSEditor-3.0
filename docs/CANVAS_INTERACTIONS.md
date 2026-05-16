# PWSEditor User Manual

This guide summarizes the core canvas interactions shared by the controller editor and the non-PWS component-machine editor.

## 1. Selection Model

- `Shift + Left click` toggles selection on a single object.
- `Cmd/Ctrl + A` selects all selectable objects in the active canvas.
- Objects that support selection:
  - States
  - The original pseudostate node
  - Pseudostate aliases
  - Transition curves
  - Trigger labels
  - In the controller editor, dashboard/annotation widgets (state dashboards and transition annotations)
- `Shift + Left drag` on empty space extends a rectangular selection.
- Left-clicking without `Shift` does not add to persistent multi-selection. It keeps legacy direct-drag behavior for single-object editing.

## 2. Dragging Selected Objects

- After objects are selected, start a normal left-drag from any selected object to move all selected objects together.
- Group dragging supports mixed selections (states, aliases, transitions by control point, trigger labels).
- With snap-to-grid enabled, selected objects snap on release.

## 3. Transition Creation Shortcuts

- `Cmd/Ctrl + Left drag` from a state creates a guard-triggered transition.
- `Cmd/Ctrl + Shift + Left drag` from a non-pseudostate creates an event-triggered transition (default event `ev`).
- `Cmd/Ctrl + Left drag` from the pseudostate (or one of its aliases) creates an initial `_init` transition.
- Right-click a timed state and choose `Create timeout transition: choose arrival state` to create its timeout transition.

These commands are intentionally separate from `Shift + Left` selection gestures to avoid shortcut conflicts.

## 4. Export Behavior

- `File -> Export as PDF` (`Cmd/Ctrl + Shift + E`)
- `File -> Export as PNG`
- Export first asks for destination (`Save to File` or `Save to Clipboard`).
- If `Save to File` is chosen, a standard system save dialog opens.

If one or more objects are selected, PDF and PNG export render only selected objects and
does not include nearby unselected objects.  
If nothing is selected, export uses the full canvas.

For PWS controller exports: if exactly one endpoint is selected (only the state
or only its dashboard), the dashed connector between state and dashboard is not
exported. The connector is exported only when both endpoints are selected.

Controller workspaces also expose code-generation exports:
- `File -> Export as ST` writes an IEC 61131-3 Structured Text file.
- `File -> Export as PLCOpen XML` writes a PLCopen XML file for PLC tools.
- These exports include the controller function block and simple assembly component function blocks.

## 5. Timed States

- Right-click a normal state and toggle `Timed state` to attach a time badge.
- Use `Edit timed label...` to change the badge text, for example `10s` or `T#10s`.
- Drag or edit the badge position directly from the canvas.
- Each timed state may have one timeout transition.
- Removing the timed marker also removes the state's timeout transition after confirmation.

## 6. Notes

- Pseudostate origin selection is supported the same way as alias selection (`Shift + Left click`).
- Right-click context menus and existing link-mode workflows remain available.
- `Cmd/Ctrl + E` toggles `Edit mode` in the active canvas.
- In `PWSEditor`, `Cmd/Ctrl + A` and `Cmd/Ctrl + E` apply to the active state-machine panel (controller or embedded machine editor), based on focus/last interaction.

## 7. File Menu Shortcuts

Controller editor (`PWSEditor`):
- `Cmd/Ctrl + N`: New
- `Cmd/Ctrl + O`: Open
- `Cmd/Ctrl + S`: Save
- `Cmd/Ctrl + Shift + S`: Save As
- `Cmd/Ctrl + W`: Close
- `Cmd/Ctrl + Shift + E`: Export as PDF
- `Cmd/Ctrl + P`: Export as PNG
- `Cmd/Ctrl + Q`: Exit

Controlled editor (`StateMachineEditor`):
- `Cmd/Ctrl + O`: Load Single Machine
- `Cmd/Ctrl + S`: Save Single Machine
- `Cmd/Ctrl + W`: Close Editor
- `Cmd/Ctrl + Shift + E`: Export as PDF
- `Cmd/Ctrl + P`: Export as PNG

## 8. Assembly Components Preview Panel

- The assembly components preview panel is in the **left side**, under the controller editor.
- It is **hidden by default**.
- Use `View -> Show assembly components` to show/hide it.
- Previews are **read-only**. Use them for navigation and context.
- Clicking a component preview selects that assembly component and opens it in the component editor on the right.
- The panel height is resizable against the controller editor by dragging the horizontal divider.
- Preview drawings reflect pseudostate alias routing and stored trigger-label offsets.
