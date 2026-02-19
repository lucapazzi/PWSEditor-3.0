# Open Issue: Exit-Zone Internality vs Constraints

## Status
Implemented as a testing option (default remains unchanged).

## Background
Reactive exit-zones are generated from state semantics (SS). A zone is considered
"internal" when its target is already reachable in the source state's semantics.

This caused confusion in cases where:
- target is allowed by constraints (CS),
- but target is not yet present in SS.

Example:
- SS contains `(cover.Open, laser.Off)`
- CS allows both `(cover.Open, laser.Off)` and `(cover.Closed, laser.Off)`
- autonomous `cover: Open -> Closed` still appears as an exit-zone (non-internal)
  under SS-only internality.

## New Testing Option
Menu:
- `Testing -> Treat CS-covered targets as internal exit zones`

Behavior:
- `OFF` (default): internality checked against `SS` only.
- `ON`: internality checked against `SS ∪ explicit CS`.

Notes:
- "Explicit CS" means non-`ANY` constraints.
- This option changes:
  - internal/uncovered classification and guard validation,
  - and (new) fixed-point semantics growth via internal-closure.

## LFP Effect In Testing Mode
When the option is `ON`, state semantics is closed under internal autonomous
exit-zones during fixed-point iteration:
- internality base = `SS ∪ explicit CS`,
- internal codomain is OR-ed into SS,
- codomain is clipped by explicit CS when present.

So this testing mode can add configurations to computed state semantics and
therefore can change the least fixed point.

## Implemented Code Paths
- Policy flag and helper:
  - `src/pws/PWSStateMachine.java`
    - `isConstraintAwareExitZoneInternalityEnabled()`
    - `setConstraintAwareExitZoneInternalityEnabled(boolean)`
    - `isExitZoneInternal(...)`
    - `closeStateSemanticsWithInternalExitZones(...)`
- Testing menu wiring:
  - `src/pws/editor/PWSEditor.java`
- Fixed-point integration:
  - `src/pws/editor/semantics/SemanticsVisitor.java`
- Internality consumers updated to use the shared helper:
  - `src/pws/editor/annotation/StateSemanticsAnnotation.java`
  - `src/pws/editor/annotation/GuardAnnotation.java`
  - `src/pws/editor/ExtendedDashboardDialog.java`
  - `src/pws/editor/ControllerReportDialog.java`
