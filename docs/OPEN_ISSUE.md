# Exit-Zone Semantics and Constraints

## Status
Resolved and implemented.

## Current Behavior
Reactive exit-zones are generated from the portion of a state's accumulated
semantics (`Acc` / `SS`) that is also admitted by its explicit constraints.
An exit-zone is recorded when an autonomous component transition starts from an
allowed reachable configuration and leads outside the state's allowed
constraint domain.

Explicit constraints (CS) remain informative:
- they are shown in dashboards,
- they can produce provisional CS-only exit-zones,
- they do not clip accumulated state semantics during the fixed-point itself.

## Reason
This interpretation matches the intended meaning of `RS` as a boundary of the
allowed domain, not merely as a boundary of the currently accumulated
configurations. A configuration may be admitted by the constraints even if it
has not yet been accumulated into `Acc`; such a configuration should not by
itself generate a reactive "exit" warning.

## Decision
Keep the stable interpretation:
- `Acc` is computed independently from explicit constraints,
- constraint violations are reported visually and analytically,
- `RS` marks autonomous evolutions from `Acc ∩ CS` to configurations outside `CS`,
- CS-only exit-zones remain provisional, informational warnings.
