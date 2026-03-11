# Deadlock Framework

This note defines deadlock terms used by PWSEditor under **strict action semantics**.

## Scope
- Applies to controller-state semantics and component machines.
- Coverage uses strict semantics: a transition covers a configuration only if guard and all actions are fireable.
- **Self-loops are not considered exits** for deadlock coverage.

## Core terms
- **Configuration**: one concrete state per component machine, e.g. `(eng.On, brake.Ok)`.
- **Internal evolution**: only autonomous transitions inside component machines.
- **Internally stuck configuration**: no autonomous internal evolution is possible from that configuration.

## Component-level deadlock
- **Component deadlock (sink)**: a component state with no enabled outgoing transitions.
- This is local to the component and should usually be modeled/treated as failure.

## Controller-level deadlocks
- **Primary deadlock configuration**: a controller-state configuration that has **no escape path** to any enabled outgoing controller transition (excluding self-loops). Escape path means:
  - direct transition coverage from the configuration, or
  - internal autonomous evolution to another configuration that is directly covered.
- **Secondary (internal) deadlock configuration**: a primary deadlock configuration that is also internally stuck.

Relation:
- Secondary implies primary.
- Primary does not imply secondary.

## Fail-state masking
- When a controller state is flagged as **Fail state**, primary and secondary deadlock checks are masked for that state.
- Exit-zone coverage is also not required for fail states.

## Why this split matters
- A state can be primary-deadlocked even when configurations still evolve internally (example: no outgoing controller exits, but internal autonomous cycles exist).
- Secondary deadlocks identify the stricter case where there is neither controller way out nor internal evolution.

## Quick checklist
- No escape path (direct coverage or via internal evolution) => primary deadlock.
- Primary + internally stuck => secondary deadlock.
- Fail state => exit-zone coverage not required.
- Fail state => deadlock checks masked.
