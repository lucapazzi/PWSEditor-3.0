# Open Issue: Exit Zones from Constraint Semantics (CS)

## Context
Reactive exit zones (EZs) are currently computed from **both** state semantics (SS) and constraint semantics (CS). CS can be *partial* (e.g., `m1.Ready`), which implicitly allows any states of other machines, but **only explicitly mentioned machines contribute EZs**. This leads to cases where a partial CS allows `m2.Ready`, yet `m2` autonomous transitions do **not** generate EZs.

## Current Behavior (Code)
- Exit zones are computed via `findExitZones(baseSemantics)` and then merged:
  - `findExitZones(ps.getStateSemantics())`
  - `findExitZones(ps.getConstraintsSemantics())`
- Reactive semantics = union of CS and SS exit zones.
- `findExitZones` requires:
  1) `source ∧ baseSemantics` is **not empty**
  2) `target ∧ baseSemantics` is **empty**

Because partial CS does **not** explicitly include other machines’ source states, EZs for those machines are not generated.

## Problem / Question
Should **partial CS** be used to generate EZs **as if it were implicitly expanded** to all machines, or should EZs be generated **only for explicitly mentioned machines** (current behavior)?

## Why It Matters
- **Pro current behavior (conservative):**
  - Partial CS is concise and expresses “I don’t care about other machines.”
  - EZ preview remains limited to explicitly constrained machines.
- **Pro expanded behavior (preview-focused):**
  - Gives early visibility of all potential boundary conditions, even before SS is computed.
  - Aligns with the semantics of partial constraints (“all combinations allowed”).
- **Risks of expansion:**
  - Could produce many EZs (noise / clutter).
  - Might feel inconsistent with a user’s intent to ignore some machines.

## Options
1) **Keep current behavior (conservative):**
   - CS produces EZs only for explicitly mentioned machines.
2) **Expand CS for EZ computation only:**
   - Preserve concise CS input but compute EZs against the fully expanded semantics.
3) **Add a toggle / mode:**
   - “Compute EZs from CS (expanded)” vs “CS explicit only.”

## Decision Needed
Pick the intended semantics for CS-derived exit zones and update `findExitZones` / CS handling accordingly.

## Related Code Locations
- `src/pws/PWSStateMachine.java`
  - `recalculateSemantics()` (CS/SS exit-zone union)
  - `updateExitZonesForState()`
  - `findExitZones(Semantics baseSemantics)`
