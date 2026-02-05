# Deadlock Framework (Draft)

This document captures the current deadlock notions for PWSEditor under **strict action semantics**. It is intended as a compact reference to guide implementation details and UI wording.

**Scope**
- Applies to PWS controller semantics and their component machines.
- Assumes **strict action semantics**: controller actions are concrete events that must be enabled to apply.

## 1) Core Concepts

**Configuration**
- A concrete assignment of one state per component machine (e.g., `(eng.On, brake.Ok)`).

**Controller State Semantics**
- The set of configurations associated with a controller state.

**Internal Evolution**
- Evolution via **autonomous component transitions only**.
- Controller-triggered actions are **not** internal evolution.

## 2) Component-Level Deadlock

**Component deadlock (sink)**
- A component state with **no enabled outgoing transitions** (triggered or autonomous).
- Implication: a component in such a state **cannot evolve at all** on its own.
- Design intent: such a state should be treated as **failure**. If it is not explicitly modeled as a Fail state, it should be flagged as a modeling error.

**Recoverable Fail**
- A Fail state **may** have outgoing transitions (recovery), so not all Fail states are deadlocked.

## 3) Configuration-Level Deadlock (Internal)

A configuration is **internally stuck** if it **cannot evolve internally** via autonomous component transitions.
- This is a property of the *configuration*, not just a single component.
- Internal evolution ignores controller actions.

## 4) Controller-Level Deadlock

A configuration in a controller state is a **true deadlock** if **both** hold:
1. It is **internally stuck**, and
2. It is **not covered** by any outgoing controller transition under **strict action semantics**.

**Coverage under strict action semantics**
- A configuration is covered by a controller transition only if:
  - The transition guard is satisfied, and
  - Every action `m.event` on the transition is **enabled** from that configuration.
- If any action cannot fire, the configuration **does not** transfer to the target state and is **not** covered.

## 5) Fail States in Controller Semantics

If a controller state’s semantics include component Fail states:
- This should be **visible** as a warning, since failure is part of the controller state’s meaning.
- It does **not** automatically imply a controller deadlock.

## 6) Relationship Between Levels

- **Component deadlock** contributes to internal stuckness but does **not** automatically mean controller deadlock.
- **Controller deadlock** requires *both* internal stuckness *and* lack of strict-coverage by controller transitions.
- With strict action semantics, a transition that *looks* like a way out is **not** a way out unless its actions are enabled in that configuration.

## 7) Summary Checklist

- Component sink state: treat as **failure** (or flag if not marked Fail).
- Configuration internally stuck: no autonomous component evolution.
- Controller true deadlock: internally stuck **and** not strictly covered by any outgoing controller transition.

## 8) Example: Engine May Fail to Start

**Component machine `eng`**
- States: `Off`, `On`, `Fail`
- Triggered transitions:
  - `Off --(on)--> On`
  - `Off --(on)--> Fail` (engine fails to start)
- Optional recovery:
  - `Fail --(reset)--> Off` (if modeled)

**Controller**
- State `Go` semantics: `{(eng.On), (eng.Fail)}`
- Transition `Go -> Stop2` with action `<eng.off>`
- State `Stop2` has **no outgoing transitions** in this example.

**Mapping to the framework**
- `eng.Fail` is a **Fail state**. If it has no outgoing transitions, it is also a **component deadlock (sink)**.
- Configuration `(eng.Fail)` is **internally stuck** if there are no autonomous transitions out of `Fail`.
- Under **strict action semantics**, `<eng.off>` can only fire when `eng` is in `On`.  
  Therefore:
  - `(eng.On)` is **covered** by the transition.
  - `(eng.Fail)` is **not covered** (action cannot fire), so it remains in `Go` and does **not** transfer to `Stop2`.
- If `(eng.Fail)` is internally stuck **and** not covered by any other outgoing controller transition, it is a **true controller deadlock**.
- In `Stop2`, the only configuration is `(eng.Off)`. With **no outgoing transitions**, if `eng.Off` has no autonomous evolution then `(eng.Off)` is internally stuck and **Stop2 is a true deadlock state** in this example.
