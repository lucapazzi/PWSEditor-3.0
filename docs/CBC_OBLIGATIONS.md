# CBC Obligations and Editor Enforcement

This note collects the controller-side consistency obligations ("CBC obligations") that PWSEditor currently checks in one place.

It is meant as a review document:

- what each obligation means
- how the editor tries to prevent it while editing
- how it is diagnosed after the fact
- whether it appears in the Controller Report

## Scope

This note covers:

- guards
- actions
- exit-zone coverage and stale exit zones
- constraint consistency
- reachability
- controller deadlocks
- fail-state masking rules

It does not try to restate the full semantics model. For drift, internal configurations, and constraint-aware internal exit zones, see `docs/INTERNAL_CONFIGURATIONS.md`.

## What "enforcement" means here

In the current editor, CBC obligations are enforced in three different ways:

- **Preventive filtering**:
  the standard popup editors try to offer only guards/actions that are meaningful for the current semantics
- **Live diagnostics**:
  annotations and dashboards change color and tooltip text as soon as an obligation is violated
- **Aggregated reporting**:
  the Controller Report collects the reportable issues into one review dialog

So "enforced" here does not mean a separate hard-validation phase. In practice, the editor mostly guides and diagnoses rather than blocks.

## Main enforcement layers

- **Semantics engine**:
  `PWSStateMachine.recalculateSemantics()` recomputes state semantics (`SS`), constraint semantics (`CS`), reactive semantics / exit zones, and cached deadlock sets.
- **Guard-level checks**:
  `GuardAnnotation` filters candidate guards in the popup and colors problematic guards live.
- **Action-level checks**:
  `ActionAnnotation` filters candidate actions in the popup and colors orphan actions live.
- **State/dashboard checks**:
  `StateSemanticsAnnotation` computes the controller-state status shown by dashboard colors, exit-zone colors, and configuration underlines.
- **Detailed drill-down**:
  `ExtendedDashboardDialog` explains why configurations, exit zones, and deadlocks are classified the way they are.
- **Review report**:
  `ControllerReportDialog` gathers the reportable problems into sections.

## Quick distinction: orphan guard vs orphan action

- **Orphan guard**:
  the transition guard refers to an autonomous exit-zone target that is no longer a valid external trigger for the source state.
- **Orphan action**:
  the transition action names a `machine.event` that is not reachable from the source-side semantics that can actually reach that transition.

So the guard problem is about a stale or invalid **boundary condition**.
The action problem is about a stale or invalid **command**.

## Summary matrix

| Obligation | Meaning | Preventive filtering in editor | Live visual diagnostic | Controller Report |
|-----------|---------|--------------------------------|------------------------|-------------------|
| `FALSE` guard | Placeholder guard; transition can never fire | No hard block; standard guard workflow encourages replacement | Red guard label | Yes |
| Orphan guard | Autonomous guard target no longer valid / became internal | Yes, standard autonomous guard popup offers only eligible exit-zone targets | Red guard label | Yes |
| Triggered guard with empty source coverage | Guard does not match current source semantics | Yes, obvious empty candidates are filtered when semantics are available | Orange guard label | No |
| Overlapping triggered guards | Same source + trigger group overlaps another enabled transition | Partly; standard popup avoids obvious overlaps | Red guard label | No |
| Incomplete triggered guard partition | Same source + trigger group does not cover all source semantics | Partly; popup helps, advanced editing can still create gaps | Orange guard label | No |
| Orphan action | Action is not reachable from source semantics | Yes, action popup filters candidates when semantics are available | Red action label + tooltip | Yes |
| Constraint violation | Computed `SS` contains config outside `CS` | No | Red config text + red state status | Yes |
| Uncovered exit zone | External exit zone has no covering autonomous PWS transition | No direct block, but valid autonomous guards are easy to insert from the popup | Red exit-zone row + red state status | Yes, except fail states |
| Orphan exit zone | Exit zone references a missing component source state | No | Red exit-zone row + red state status | Yes |
| Unreachable state | State has no computed configurations | No | Gold/yellow dashboard status | Yes |
| Primary deadlock | No escape path to an enabled outgoing controller transition | No | Red state status / details | Yes, except fail states |
| Secondary deadlock | Internally stuck and primary deadlocked | No | Red underline + red state status / details | Yes, except fail states |

## Guard obligations

### 1. `FALSE` guard

- `FALSE` is the placeholder meaning "this transition never fires".
- It is treated as a real guard problem for review purposes.
- The annotation is colored red.
- The Controller Report lists it as **FALSE Guard (Placeholder)**.

This is the simplest CBC obligation: every enabled transition should have a meaningful guard.

### 2. Orphan guard

This applies to **autonomous** PWS transitions whose guard is a `BasicStateProposition`.

The guard becomes orphan when its target is no longer in the source state's set of valid autonomous guard targets. In current behavior this usually means:

- the exit zone disappeared after a semantics change
- the exit zone became **internal** (gray), so it is no longer a valid external controller trigger

Important detail:

- **gray internal exit zones are excluded**
- **blue CS-only exit zones are still valid**

The editor enforces this in two stages:

- **Prevention**:
  the standard autonomous guard popup offers only eligible exit-zone targets; it also avoids targets already covered by other enabled autonomous transitions from the same source.
- **Detection**:
  an existing guard that no longer matches the valid target set is colored red and reported as **Orphan Guard**.

### 3. Triggered guard partition obligations

These checks apply to enabled transitions grouped by:

- same source controller state
- same trigger event

Initial transitions are treated as belonging to the synthetic trigger group `_init`.

The live annotation checks three things:

- **empty source coverage**:
  the guard has empty intersection with the source state's current semantics
- **overlap**:
  two enabled transitions in the same trigger group cover some of the same source semantics
- **incomplete partition**:
  the enabled transitions in the trigger group do not jointly cover the whole source semantics

Current enforcement split:

- the **standard popup** tries to filter out obviously empty or overlapping candidates when enough semantics is available
- the **live guard annotation** still diagnoses the final result after editing
- these partition checks are **not currently included in the Controller Report**

This is one of the main completeness distinctions in the current tool: some guard obligations are diagram-level only.

## Action obligations

### Orphan action

An action is orphan when it names a `machine.event` that is not reachable from the semantics that can actually reach the transition.

The validation rule depends on transition type:

- **triggered transitions**:
  valid actions are collected from the source `SS`, restricted by the guard when possible
- **fallback for triggered transitions**:
  if `SS` is not available yet, the editor falls back to `CS`
- **autonomous transitions**:
  valid actions are collected after applying the matching exit-zone machine transition to the source semantics
- **fallback for autonomous transitions**:
  if no matching exit zone can be used, the editor falls back to plain source `SS` / `CS`

Current enforcement split:

- **Prevention**:
  the action popup filters insertable actions to the valid action set when enough semantics is available
- **Detection**:
  an action list containing invalid actions is colored red and its tooltip explains the orphan reasons
- **Reporting**:
  the Controller Report lists each invalid action as **Orphan Action**

Important caveat:

- if neither `SS` nor `CS` provides usable source semantics, the editor intentionally does **not** flag orphan actions

So orphan-action checking is semantics-aware, but it is only as strong as the available source semantics.

## Exit-zone and state obligations

### 1. Constraint violation

This is the simplest state-level consistency rule:

- every computed configuration in `SS` should satisfy the state's `CS`

If a computed configuration does not imply `CS`:

- the offending configuration row is shown in red
- the dashboard status becomes problematic
- the Controller Report lists the state under constraint problems

### 2. Uncovered exit zone

An uncovered exit zone is an external exit zone that has no enabled autonomous PWS transition covering its target.

For this check:

- orphan exit zones are treated separately
- internal gray exit zones are ignored
- fail states do not require exit-zone coverage

Enforcement surfaces:

- the exit-zone line is shown in red in the dashboard
- the dashboard header turns problematic
- the Controller Report lists the state under **Uncovered Exit Zones**

### 3. Orphan exit zone

An orphan exit zone is a stale exit zone whose component source state no longer exists in the assembly.

This is different from an uncovered exit zone:

- **uncovered** means the exit zone still exists but no autonomous PWS transition handles it
- **orphan** means the exit zone itself is stale or inconsistent

Enforcement surfaces:

- the exit-zone line is shown in red
- the dashboard status remains problematic
- the Controller Report has a separate **Orphan Exit Zones** section

### 4. Unreachable state

A controller state is unreachable when its computed `SS` is empty.

Enforcement surfaces:

- gold/yellow dashboard status
- corresponding explanation in details / tooltips
- Controller Report entry under **Unreachable States**

### 5. Primary deadlock

A primary deadlock configuration has no escape path to any enabled outgoing controller transition.

Escape path may be:

- direct transition coverage
- or internal autonomous evolution to another configuration that is directly covered

Enforcement surfaces:

- problematic dashboard status
- detailed listing in Extended Details
- Controller Report section for **Primary Deadlock Configurations**

### 6. Secondary deadlock

A secondary deadlock is stricter:

- it is primary deadlocked
- and it is also internally stuck

Enforcement surfaces:

- red underline on the configuration row
- problematic dashboard status
- detailed listing in Extended Details
- Controller Report section for **Secondary (Internal) Deadlock Configurations**

## Fail-state masking

Fail-state marking changes the obligation set.

For a controller state marked as **Fail state**:

- **uncovered exit-zone** checks are masked
- **primary deadlock** checks are masked
- **secondary deadlock** checks are masked

But fail-state marking does **not** suppress everything else. In current behavior it does **not** mask:

- orphan guards
- orphan actions
- constraint violations
- orphan exit zones
- unreachable-state detection

So fail-state marking relaxes controller reaction obligations, but it does not turn the state into a diagnostics-free region.

## Related diagnostics that are not the same as CBC obligations

The editor also shows some related signals that are useful during review but are not the same as the core controller-side CBC report sections.

- **Component deadlock states**:
  shown inside component-machine views and reflected in yellow config underlines when a controller configuration contains them
- **Component fail states**:
  shown as warnings when present inside controller configurations
- **Internal gray exit zones**:
  visible for semantics explanation, but not treated as controller obligations to cover

These diagnostics still matter, but they are not all first-class sections of the Controller Report.

## Current completeness review points

This is the current split that is worth reviewing explicitly.

### Reported in the Controller Report

- `FALSE` guards
- orphan guards
- orphan actions
- uncovered exit zones
- orphan exit zones
- constraint violations
- primary deadlocks
- secondary deadlocks
- unreachable states

### Currently diagram-level only

- triggered guard with empty source coverage
- overlapping triggered guards in the same source + trigger group
- incomplete triggered guard partitions

### Important implementation nuances

- Standard popups provide the strongest prevention. Advanced editing can still create guard formulas that are only caught after the fact by live diagnostics.
- Orphan-action detection is intentionally skipped when the source-side semantics is not available enough to decide.
- Internal gray exit zones are excluded from autonomous guard validation, so an autonomous guard can become orphan simply because the referenced exit zone became internal after recomputation.
- Disabled transitions are ignored by the main guard/action/report obligation checks.

## Relevant code locations

- `src/pws/PWSStateMachine.java`
  semantics recomputation, exit-zone classification, deadlock caching
- `src/pws/editor/annotation/GuardAnnotation.java`
  guard popup filtering and live guard diagnostics
- `src/pws/editor/annotation/ActionAnnotation.java`
  action popup filtering and orphan-action diagnostics
- `src/pws/editor/annotation/StateSemanticsAnnotation.java`
  state dashboard status, exit-zone coverage status, deadlock / constraint visualization
- `src/pws/editor/ExtendedDashboardDialog.java`
  detailed explanations for configurations, exit zones, and deadlocks
- `src/pws/editor/ControllerReportDialog.java`
  aggregated controller review report

## Review takeaway

The current editor does have a coherent CBC-obligation model, but it is split across:

- preventive UI filtering
- live annotation/dashboard diagnostics
- report-only aggregation

The main completeness question is not whether obligations exist, but whether all obligations that matter for review are represented in the **Controller Report**, or whether some should remain diagram-level only.
