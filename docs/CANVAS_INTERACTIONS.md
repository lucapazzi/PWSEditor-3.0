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

These commands are intentionally separate from `Shift + Left` selection gestures to avoid shortcut conflicts.

## 4. Export Behavior

- `File -> Export as PDF` (`Cmd/Ctrl + Shift + E`)
- `File -> Export as PNG` is currently disabled in both editors.
- In the PDF save dialog, use `Save` to write a `.pdf` file or `Save to Clipboard` to copy the exported PDF to the system clipboard.

If one or more objects are selected, PDF export renders only selected objects and
does not include nearby unselected objects.  
If nothing is selected, export uses the full canvas.

For PWS controller exports: if exactly one endpoint is selected (only the state
or only its dashboard), the dashed connector between state and dashboard is not
exported. The connector is exported only when both endpoints are selected.

## 5. Notes

- Pseudostate origin selection is supported the same way as alias selection (`Shift + Left click`).
- Right-click context menus and existing link-mode workflows remain available.
- `Cmd/Ctrl + E` toggles `Edit mode` in the active canvas.
- In `PWSEditor`, `Cmd/Ctrl + A` and `Cmd/Ctrl + E` apply to the active state-machine panel (controller or embedded machine editor), based on focus/last interaction.

## 6. File Menu Shortcuts

Controller editor (`PWSEditor`):
- `Cmd/Ctrl + N`: New
- `Cmd/Ctrl + O`: Open
- `Cmd/Ctrl + S`: Save
- `Cmd/Ctrl + Shift + S`: Save As
- `Cmd/Ctrl + W`: Close
- `Cmd/Ctrl + Shift + E`: Export as PDF
- `Cmd/Ctrl + Q`: Exit

Controlled editor (`StateMachineEditor`):
- `Cmd/Ctrl + O`: Load Single Machine
- `Cmd/Ctrl + S`: Save Single Machine
- `Cmd/Ctrl + W`: Close Editor
- `Cmd/Ctrl + Shift + E`: Export as PDF
