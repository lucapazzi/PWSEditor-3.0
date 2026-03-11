# Internal Configurations, Drift, and Exit-Zones

## Purpose

This note explains three closely related ideas in PWSEditor:

- what an **internal configuration** is
- how configurations **expand by drifting**
- how autonomous drift becomes an **exit-zone** when contrasted with state constraints (`CS`)

The goal is to make the relation between **computed state semantics** (`SS`), **constraint semantics** (`CS`), and **autonomous component evolution** explicit.

## Core Terms

- **CS (Constraint Semantics)**:
  the user-declared set of configurations allowed in a controller state
- **SS (State Semantics)**:
  the computed set of configurations currently known to be reachable in that controller state
- **Internal configuration**:
  a configuration that belongs to the state's computed `SS`
- **Drift**:
  autonomous component-machine evolution while the controller remains in the same controller state
- **Exit-zone (EZ)**:
  a boundary condition produced by autonomous component evolution that may require controller reaction

In informal discussion, "drift" means:

```text
controller state stays fixed
component machine evolves autonomously
configuration changes inside that same controller state
```

## What Counts As An Internal Configuration

A configuration may enter a state's computed `SS` in two ways:

1. It is contributed directly by an incoming controller transition.
2. It is added later by **internal drift closure** from a configuration already in `SS`.

The second case is the important one here.

Example:

```text
initial SS in state FailSafe:
  (cover.Open, laser.Off)

cover has autonomous transition:
  Open -> Closed
```

If that autonomous move is considered internal for `FailSafe`, then the codomain

```text
(cover.Closed, laser.Off)
```

can be absorbed into `SS`.

## How Configurations Expand By Drifting

PWSEditor closes state semantics under internal autonomous evolution.

Conceptually:

```text
start from current SS
find enabled autonomous component transitions from SS
classify each as internal or boundary
absorb compatible internal codomain into SS
repeat until no new configurations are added
```

The concrete absorption rule is:

```text
codomain  = result of applying the autonomous component transition
absorbed  = codomain AND CS   (when explicit CS exists)
absorbed  = codomain          (otherwise)
SS := SS OR absorbed
```

So drift is not just a visual status. It can enlarge the state's computed semantics.

### Provenance of Drifted Configurations

Configurations added by internal drift closure are now reported as such:

- the **dashboard hover text** says which prior configuration and autonomous component transition produced the row
- **Show Extended Details...** lists the same derivation explicitly

This keeps the main dashboard readable while still exposing the provenance of absorbed rows.

## When Drift Stays Internal vs Becomes An Exit-Zone

Autonomous component evolution always starts from a configuration already compatible with the current state. The question is whether the target still belongs "inside" the same controller state, or whether it crosses a boundary.

PWSEditor first records a classical autonomous exit-zone candidate from an enabled autonomous component transition whose source is reachable from the current `SS`.

It then applies an **internality test**.

### Default Rule

Without the extra View-menu option:

```text
internal if target ∩ SS != empty
```

So the target is internal only if it already intersects the currently computed state semantics.

### Constraint-Aware Rule

With:

```text
View -> Treat CS-covered targets as internal exit zones
```

the test becomes:

```text
internal if target ∩ (SS ∪ explicit CS) != empty
```

This is a broader rule:

- a target already in `SS` is internal
- a target not yet in `SS` but explicitly allowed by `CS` is also treated as internal

If a zone is internal, PWSEditor tries to absorb the corresponding codomain into `SS`.

## The Important Contrast: Target-Level Internality vs Configuration-Level Absorption

There are two different checks:

1. **Target-level internality**
   This is the proposition-level test above (`target ∩ SS` or `target ∩ (SS ∪ CS)`).
2. **Configuration-level absorption**
   This is the concrete codomain test:

```text
absorbed = codomain
```

That distinction matters.

### Case Table

| Situation | Internal? | Added to SS? | Result |
|----------|-----------|--------------|--------|
| Target already intersects `SS` | Yes | Usually already present | Gray internal EZ |
| Target intersects explicit `CS`, codomain also satisfies `CS` | Yes (constraint-aware rule) | Yes | Drifted config is absorbed into `SS` |
| Target intersects explicit `CS`, but codomain does **not** satisfy `CS` | Yes at target level | Yes | Drifted config is absorbed into `SS` and shown as a constraint violation |
| Target intersects neither `SS` nor explicit `CS` | No | No | Boundary EZ |

The third row is the subtle one. A target proposition may look "allowed" at the machine-state level, while the full concrete codomain is still incompatible with `CS` after all machines are considered together. Under soft constraints, that configuration is still kept in `SS`; `CS` is diagnostic, not a filter.

## Examples

### Example 1: Drift Absorbed Into SS

Suppose `FailSafe` has:

```text
CS:
  (cover.Open, laser.Off)
  (cover.Closed, laser.Off)

SS initially:
  (cover.Open, laser.Off)

autonomous component transition:
  cover: Open -> Closed
```

Then:

```text
codomain = (cover.Closed, laser.Off)
absorbed = codomain = (cover.Closed, laser.Off)
```

So the configuration is added to `SS`.

Final effect:

- `SS` contains both `(cover.Open, laser.Off)` and `(cover.Closed, laser.Off)`
- `Open -> Closed` becomes an internal drift step
- the dashboard can show the added row as derived via internal closure

### Example 2: Target Allowed, Concrete Codomain Rejected

Suppose `FailSafe` instead has:

```text
CS:
  (cover.Open, laser.Off)
  (cover.Closed, laser.On)

SS initially:
  (cover.Open, laser.Off)

autonomous component transition:
  cover: Open -> Closed
```

The component codomain is still:

```text
(cover.Closed, laser.Off)
```

but now:

```text
absorbed = codomain = (cover.Closed, laser.Off)
```

So the configuration is still added to `SS`, but it is displayed as violating `CS`.

This is why "target allowed by CS" is not enough by itself to guarantee a green row. The **whole resulting configuration** may still violate `CS`, even though it remains part of computed semantics.

## How CS Forms Exit-Zones

There are three relevant interactions with `CS`.

### 1. CS Does Not Restrict What Drift May Add

For internal drift closure:

```text
the full codomain is absorbed into SS
```

`CS` remains relevant as a diagnostic and as an aid to internality/provisional exit-zone analysis, but it no longer clips the absorbed codomain.

### 2. CS Can Make A Target Count As Internal

With the View-menu option enabled, explicit `CS` participates in the internality test:

```text
internal if target ∩ (SS ∪ explicit CS) != empty
```

This is what lets a not-yet-reached target be treated as internally admissible.

### 3. CS Also Produces Provisional Exit-Zones

PWSEditor also computes **CS-only provisional exit-zones** from explicit constraints. These are not the same as absorbed internal configurations:

- they are derived from explicit constraints
- they are shown in blue
- they preview boundaries implied by constraints even before `SS` fully reaches them

For partial constraints, unspecified machines are treated as `ANY`, so autonomous transitions of those unconstrained machines are not reported as provisional exit-zones.

## Incoming Transition Soft Constraints vs Internal Drift

Do not confuse internal drift with how incoming controller transitions interact with constraints.

For incoming controller transitions:

```text
accepted = contribution
```

The full contribution is inserted into computed `SS`.
If some resulting configurations do not satisfy `CS`, they remain in `SS` and are shown as constraint violations; they are not treated as exit-zones.

So:

- **internal drift closure** adds autonomous codomain into `SS`
- **incoming controller transitions** also add their full codomain into `SS`
- **constraints** diagnose incompatible configurations instead of filtering them out

## Fail-State Note

Marking a controller state as **Fail state** does **not** stop exit-zones from being computed.
PWSEditor still computes:

- reactive exit-zones from computed `SS`
- CS-only provisional exit-zones from explicit `CS`

What changes is the **coverage obligation**:

- exit-zone coverage is treated as **not required**
- uncovered exit-zones in fail states are excluded from the normal coverage obligation
- controller-report uncovered-exit-zone warnings skip fail states

So fail-state marking affects how exit-zones are **interpreted and reported**, not how
they are **formed**.

## Practical Reading Guide

When looking at one dashboard row in `configs`, ask:

1. Was this row contributed directly by an incoming controller transition?
2. Or was it added later by autonomous internal drift closure?
3. If it was drifted in, did that happen because:
   - the target was already in `SS`, or
   - the target was admitted by explicit `CS` and then the concrete codomain survived clipping by `CS`?

If you need that provenance, use:

- row hover text in the dashboard
- **Show Extended Details...**

Those are now the intended places for this information, rather than adding extra inline symbols to the dashboard matrix itself.
