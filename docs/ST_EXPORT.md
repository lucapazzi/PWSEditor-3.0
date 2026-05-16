# ST and PLCopen Export

This document describes the current export model used by PWSEditor for:

- IEC 61131-3 Structured Text (`.st`)
- PLCopen XML (`.plcopen.xml`)

The two exporters are intentionally aligned. PLCopen XML wraps the same generated Structured Text bodies inside PLCopen function blocks.

## Scope

The export includes:

- the PWS controller as one function block
- every simple assembly component machine as one function block
- one enumerated DUT for each exported machine/controller state set
- one `PLC_PRG` scaffold

The export does not include:

- nested PWS machines inside the assembly
- automatic project-specific task/resource wiring beyond the exported `PLC_PRG`

## Naming

- The exported controller function block name follows the chosen export file name when available.
- If the controller model name is `Untitled`, the exporter still tries to derive a better name from the document/export file name.
- Component FB and DUT names are derived from machine names and sanitized into valid ST identifiers.
- Event variables are exported with `_ev` suffix.

Examples:

- controller action `<main.go>` becomes `main.go_ev := TRUE;`
- trigger `go` becomes input `go_ev : BOOL := FALSE;`

## Generated Structure

For each exported machine/controller the generator emits:

1. `TYPE Stati_<Name> : (...)`
2. `FUNCTION_BLOCK <Name>`
3. `PROGRAM PLC_PRG`

For timed machines it also emits:

- `VAR`
- `timer : TON;`

For controller FBs:

- assembly components are exported as `VAR_IN_OUT`
- controller trigger events are exported as `VAR_INPUT`
- current controller state is exported as `VAR_OUTPUT`

For simple component FBs:

- trigger events are exported as `VAR_INPUT`
- current state is exported as `VAR_OUTPUT`

For `PLC_PRG`:

- one instance is declared for each simple component machine
- one instance is declared for the controller FB

## Translation Rules

### Controller

- Each logical controller state becomes one enum literal and one `CASE stato OF` branch.
- The controller pseudo-state is translated to synthetic enum state `Init`.
- Each initial transition from the pseudo-state becomes an `IF ... THEN` inside `Init`.
- A controller action `<c.e>` becomes `c.e_ev := TRUE;`.
- A guard `m.S` becomes `m.stato = Stati_<MachineType>.S`.
- A timed state label such as `25s` becomes `PT := T#25s`.
- A timeout transition is translated with condition `timer.Q`.

### Simple component machines

- Each logical state becomes one enum literal and one `CASE stato OF` branch.
- The component pseudo-state is translated to synthetic enum state `Init`.
- The initial transition out of the pseudo-state initializes the component state machine.
- If a simple component machine has multiple autonomous initial transitions, they are exported as simulation inputs `sim_event_Init_<Target>` so one initial evolution can be selected explicitly.
- Triggered transitions consume the corresponding `*_ev` input by resetting it to `FALSE` at the end of the `IF` body.
- Autonomous non-timeout transitions are exported as hidden simulation inputs named `sim_event_<Source>_<Target>`.
- These simulation inputs are declared in `VAR_INPUT`, initialized to `FALSE`, and documented as `simulation event from <Source> to <Target>`.
- When one of these simulation transitions fires, the corresponding `sim_event_*` input is reset to `FALSE` at the end of that branch.
- Timed states use one local `TON` exactly like controller timed states.

### PLC_PRG

- `PLC_PRG` instantiates the controller and all exported simple component machines.
- Each component instance is executed first.
- The controller call passes component instances by name to the controller `VAR_IN_OUT` parameters.
- After the component calls, the controller instance is executed.
- The generated `PLC_PRG` does not introduce extra local event variables for FB inputs that already have default initialization.
- The generated scaffold calls component instances as `machine();`, relying on the fact that the controller writes directly to the component event inputs through `VAR_IN_OUT`.

## Current Behavioral Conventions

- The generated code uses separate `IF` blocks, not `ELSIF`.
- Trigger inputs are initialized as `BOOL := FALSE`.
- When a trigger-driven transition fires, the consumed trigger input is reset to `FALSE` at the end of that branch.
- The exporters do not generate automatic resets for controller-to-component output events such as `main.go_ev := FALSE;`.

This means component/controller event pulse management outside the consumed trigger reset remains under PLC application control, even though a `PLC_PRG` scaffold is now generated.

## Model Constraints

These are constraints of the current modeling/export discipline, not necessarily arbitrary exporter limitations.

### Controller

- The controller must exist and must reference a non-null assembly object.
- The assembly may be empty.
- The controller must contain at least one logical state.
- The controller must contain at least one enabled initial transition from its pseudo-state.
- Per pseudo-state, the intended model discipline remains at most one initial transition.
- Each exported transition must have both source and target.
- Transitions toward the pseudo-state are not exported.

### Timed states

- A timed state must have exactly one enabled timeout transition.
- A non-timed state must not have any timeout transition.
- The timeout label must be non-blank and must be usable as an ST time literal suffix, such as `25s`, `500ms`, `2m`.

### Assembly components

- Only simple component machines are exported as component FBs.
- Nested PWS controllers inside the assembly are not exported as components.

## Exporter Constraints

These are current implementation constraints in the code generators.

### Controller guards

The controller exporters currently support only these guard forms:

- `TRUE`
- `FALSE`
- atomic machine-state propositions such as `m.S`
- `AND`
- `OR`
- `NOT`

Any other proposition shape is rejected.

Also:

- every machine referenced in a guard must exist in the assembly
- every state referenced in a guard must exist in that component machine

### Controller actions

- every action must reference an existing assembly machine
- action event names must be non-blank

### Simple component machines

For simple component FB export, the current implementation still assumes:

- at least one enabled initial transition from a pseudo-state
- transition conditions limited to:
  - trigger input
  - `sim_event_*` for autonomous non-timeout transitions
  - `timer.Q`

So unlike the controller exporter, the simple-machine exporter does not currently translate richer logical guards.

### Name collisions

Names are sanitized before export. If two exported objects collapse to the same ST/PLCopen identifier but would produce different generated content, export fails instead of silently overwriting one of them.

## PLCopen Notes

- PLCopen XML export contains the same FB logic as the ST export.
- PLCopen XML also contains a `program` POU named `PLC_PRG`.
- The FB bodies are embedded as Structured Text inside PLCopen XML.
- Native PLCopen import in PWSEditor is not implemented.
- Import compatibility depends on the target PLC toolchain. CODESYS compatibility has been the practical target so far.

## Summary

The main difference between model constraints and exporter constraints is this:

- the controller exporter supports timed states, timeout transitions, trigger conditions, and logical guards over component states
- the simple component exporter supports timed states and timeout transitions, but still assumes a simpler transition model

If these constraints change in the future, this document should be updated together with `STExporter.java` and `PLCOpenExporter.java`.
