# PWSEditor User Manual

## Table of Contents

1. [Getting Started](#getting-started)
2. [Concepts & Terminology](#concepts--terminology)
3. [PWS in Practice](#pws-in-practice)
4. [The Main Interface](#the-main-interface)
5. [Working with States](#working-with-states)
6. [Working with Transitions](#working-with-transitions)
7. [Managing Assemblies](#managing-assemblies)
8. [Using the Machine Library](#using-the-machine-library)
9. [Semantic Constraints & Annotations](#semantic-constraints--annotations)
10. [Deadlock Detection](#deadlock-detection)
11. [Exit Zones](#exit-zones)
12. [File Management](#file-management)
13. [Menu Reference](#menu-reference)
14. [Testing Features](#testing-features)
15. [Controller Report](#controller-report)
16. [Tips & Troubleshooting](#tips--troubleshooting)

---

## Getting Started

### Installation

PWSEditor is a Java application. Ensure you have **Java 17 or later** installed on your system.

### Running PWSEditor

From the command line, navigate to the PWSEditor directory and run:

```bash
./scripts/build.sh
java -cp "out:lib/*" pws.editor.PWSEditor
```

PWSEditor launches with **no controller loaded**. Use **File -> New** or **File -> Open...**
to start working on a workspace.

---

## Concepts & Terminology

### Part-Whole Statecharts (PWS)

A **Part-Whole Statechart** is a behavioral modeling formalism that describes:

- **Controller**: A top-level state machine that controls or coordinates behavior
- **Assembly**: A collection of component state machines that can operate synchronously and asynchronously
- **States**: Control points in a state machine
- **Transitions**: Connections between states, optionally triggered by events and guarded by conditions
- **Semantics**: Formal specifications of allowed system configurations

#### PWS in Practice

Think of PWS as a two-layer model:
- **Assembly**: Component machines are referred to collectively as the assembly.
- **Component machine interface**: Each component exposes observable states (`m.S`) and accepted events (`m.e`).
- **Controller**: The controller watches component conditions and coordinates behavior by emitting actions to components.

PWS execution model vs PWSEditor analysis:
- **PWS execution model**: Runtime evolution applies enabled transitions to concrete configurations. PWS models can be translated to execution languages (e.g., Structured Text, `ST`) and other real-time execution models.
- **PWSEditor analysis model**: The editor keeps track of reached configuration sets per controller state to compute semantics, coverage, deadlocks, and exit zones.

Use this transition notation in examples:
- `trigger [guard] <m.e>`
- Actions are written as `<m.e>` and are **not** preceded by `/`.

Execution order for a transition contribution:
1. Trigger is satisfied (explicit event, hidden `_init` for initial transitions, or no trigger for autonomous transitions).
2. Guard evaluates to true.
3. Actions are applied with **strict semantics**.
4. Destination constraints filter accepted configurations; overflow is tracked as `T|...` markers.

Transition families in PWS:
- **Triggered**: Event + guard.
- **Autonomous**: Guard only (used to react to component changes/exit zones).
- **Initial**: Special triggered transition from the pseudo-state via hidden `_init`.

Strict action consequence:
- If the controller emits `<m.e>`, only configurations where `e` is enabled in component `m` move forward.
- Configurations where `e` is not enabled are discarded.

Exit-zone intuition:
- Exit zones represent "boundary reached" situations in component behavior that require controller attention.
- Autonomous controller transitions can monitor these conditions and react immediately.

Partial-constraint intuition:
- If a constraint mentions only some machines, unspecified machines are implicitly expanded to all their possible states.

Mini example:
- Transition label: `button_pressed [isReady] <alarm.beep>`
- Meaning: when `button_pressed` occurs and `isReady` is true, the controller emits `beep` to component `alarm`.
- Under strict semantics, only configurations where `alarm.beep` is enabled can reach the target state.

### Key Terms

| Term | Definition |
|------|-----------|
| **State** | A control point in a state machine where the system can reside |
| **Pseudo-State** | A special anchor state (small filled circle) used for initial transitions and assembly-closure semantics |
| **Transition** | A directed arc connecting states, optionally with triggers, guards, and actions |
| **Triggered Transition** | A transition that fires when a specific event occurs (and guard is satisfied) |
| **Autonomous Transition** | A transition without a trigger event; fires based on guard condition alone (monitors exit zones) |
| **Initial Transition** | A transition from the pseudo-state; triggered by a hidden `_init` event at system startup (the trigger is not shown and `_init` is reserved) |
| **Guard** | A boolean condition that must be true to enable a transition |
| **Action** | An emission (event output) that occurs when a transition fires |
| **Constraint Semantics** | User-specified allowed configurations for a state |
| **Computed Semantics** | Semantics inferred from state machine structure |
| **Assembly** | A collection of component machines forming a part-whole hierarchy |
| **Machine Library** | A repository of reusable state machine templates |
| **Exit Zone** | A boundary condition created by a component machine autonomous transition that would leave a state's allowed configurations |
| **Deadlock Configuration** | A configuration with no escape path to an outgoing transition (primary deadlock); if it also cannot evolve internally, it is a secondary deadlock |

---

## The Main Interface

### Layout Overview

The PWSEditor window is divided into three main areas:

```
┌─────────────────────────────────────────────────┐
│                    Menu Bar                      │
├──────────────────────┬──────────────────────────┤
│   Controller Editor  │  Assembly & Library      │
│   (left panel)       │  (right-top)             │
│                      ├──────────────────────────┤
│   Edit states and    │  Embedded Machine       │
│   transitions here   │  Editor (right-bottom)  │
│                      │                          │
└──────────────────────┴──────────────────────────┘
```

### Left Panel: Controller Editor

Edit the main controller state machine here. The canvas displays:
- **States**: Circles (or pseudo-states as filled circles)
- **Transitions**: Arrows between states with optional labels
- **Annotations**: Floating text boxes for guards, actions, and semantics

### Right Panel: Assembly Management

- **Top Section**: Toggles between **Assembly** and **Library** views
  - **Assembly view**: Lists component machines in the current assembly
    - **Initial Configurations Dashboard** (bottom of the Assembly view): shows the
      **closure** for the assembly
  - **Library view**: Lists saved, reusable machine templates
- **Bottom Section**: Embedded editor for viewing/editing selected assembly machines

---

## Working with States

### Creating a State

1. **Right-click** on an empty area of the canvas (left panel)
2. Select **"Add State"** from the context menu
3. The state is created immediately at the click location

There is no menu item for adding states; use the right-click context menu on the canvas.

New states are created immediately with an auto-generated name (`S`, `S1`, `S2`, ...), and with constraint semantics set to **ANY** by default. There is no constraint editor or confirmation dialog during creation.

### Editing a State

1. **Double-click** the state to rename it
2. **Right-click** the state to see options:
   - **Show Dashboard**: checkbox toggle for the state's dashboard
  - **Fail state**: Marks the state as a fail-safe/incongruence. Fail states are drawn with a thicker **dashed yellow** border; exit-zone, primary-deadlock, and secondary-deadlock checks are masked.
   - **Delete State**: Remove the state

To edit constraint semantics, right-click the state's dashboard and choose **Edit Constraints Semantics**.

### The Pseudo-State

The pseudo-state is automatically created and appears as a **small filled circle**. It is the anchor for **initial transitions** and represents the **assembly-closure baseline** used when computing initial semantics.

### Component Machine States (Embedded Editor)

When editing a **component machine** in the embedded editor (right-bottom panel), state diagnostics are shown directly on the state border:

- **Unreachable state**: Red outer ring. A state is unreachable if there is no path from the pseudo-state via **enabled** transitions.
- **Deadlock state**: Red border. A deadlock is a reachable state with **no enabled outgoing transitions**.
- **Manually marked Fail state**: Dashed yellow border.

Right-click a component state to toggle **Fail state** manually. This option is **disabled** for unreachable states (which are already treated as fail by unreachability).
Manual component Fail states are saved with the model and restored on load.

---

## Assembly Initial Configurations Dashboard

In the **Assembly** view (right panel), the **Initial Configurations Dashboard** now focuses on the **closure** only:

- **Closure**: the transitive closure obtained by repeatedly applying exit zones until
  no new configurations appear, rendered as a table (same layout as state dashboards).

Each row is color-coded:
- **Green**: covered by at least one **enabled initial transition** guard.
- **Red**: not covered by any enabled initial transition guard.

**Hover** a row to see a tooltip explaining the coverage status.

The panel refreshes automatically whenever the assembly or any component machine changes
(including enabling/disabling transitions). Disabled **initial transitions** are ignored when
computing initial configurations and coverage.

#### Pseudo-State Aliases

You can create **multiple aliases** of the pseudo-state to keep diagrams readable:

1. **Right-click** an empty area of the canvas
2. Select **"Create pseudostate alias"**
3. Drag the alias to the desired location

All aliases are equivalent to the real pseudo-state:
- Initial transitions created from an alias behave exactly like those created from the original
- You can create initial transitions directly from an alias with **Cmd/Ctrl + drag**
- Each initial transition remembers which alias it originates from (anchoring is preserved)
- You can delete aliases, but **at least one** pseudo-state (original or alias) must remain

**Note:** Pseudo-states do not have dashboards; dashboard options apply to regular states only.

### Visibility and Layout

- **Snap to Grid**: States and annotations automatically snap to a grid for clean alignment
- **Drag States**: Click and drag states to reposition them
- **Grid Size**: Adjustable from the **View** menu for fine-grained control

---

## Working with Transitions

### Creating a Transition

Primary gesture (recommended):

1. Click on the source state and **drag with Cmd/Ctrl held down**
2. Keep dragging until the pointer exits the source state boundary
3. Release over another state to create the transition

Gesture variants:

- **Cmd/Ctrl + drag** from a normal state: creates a **guard-triggered** transition (no explicit event label)
- **Cmd/Ctrl + Shift + drag** from a normal state: creates an **event-triggered** transition with default event name **`ev`**
- **Cmd/Ctrl + drag** from the pseudo-state (or any alias): creates an **initial** transition with hidden **`_init`** trigger

Transition endpoints must be different. **Self-loop transitions are not supported**.

Alternative (existing workflow):

- Right-click a state and choose **Create transition: choose arrival state** (link mode)
- For initial transitions, right-click the pseudo-state (or alias) and choose **Add initial transition**

### Editing Transition Properties

Click on the transition (the arrow) to select it. You can then:

1. **Edit the Trigger Event**: Add an event that triggers the transition
2. **Edit the Guard**: Add a boolean condition over machine states (e.g., `m1.S` is true iff machine `m1` is in state `S`)
3. **Edit Actions**: Add emissions (actions that occur when the transition fires)

Use **in-place editors** (floating text boxes) to directly modify:
- **Trigger labels**: Double-click the trigger label to rename the trigger event
- **Guard annotations**: Use the annotation popup menu to choose/edit the guard
- **Action annotations**: Use the annotation popup menu to insert/remove actions
- **Semantics annotations**: View computed semantics (read-only)

If guard/action labels are hidden, right-click the transition control handle and use **Show Guard** / **Show Action** first.

Renaming a trigger (double-click the trigger label) automatically recomputes semantics so the new trigger takes effect immediately.

### Understanding Transition Types

PWSEditor supports two fundamentally different transition types:

#### Triggered Transitions

A **triggered transition** has a **trigger event** (shown as text on the arrow). These transitions:
- Fire when the specified event occurs **AND** the guard condition is satisfied
- Require external stimulus to activate
- Are the most common type in reactive systems

Example: A transition labeled `button_pressed [isReady] <alarm.beep>` fires when:
1. The event `button_pressed` occurs
2. The guard `isReady` evaluates to true
3. The action `alarm.beep` is emitted

Actions are not preceded by `/`; they use the form `<m.e>`.

#### Action Semantics (Strict)

When a transition fires and emits an action `m.event`, the controller applies that event to the component machine `m` as a **strict event occurrence**:
- Only configurations where `event` is enabled in machine `m` are transferred to the target state.
- Configurations where `event` cannot fire are **discarded** (they do not move to the target state).

This models actions as concrete events that must occur. If you want to keep non-enabled configurations, you must model that explicitly (e.g., by splitting the transition with a guard or adding a parallel transition without the action).

When the destination state has explicit constraints, transition contributions are filtered at arrival:
- Accepted part: `contribution ∧ destination constraints`
- Overflow part: `contribution ∧ NOT(destination constraints)`

The accepted part is added to destination state semantics. The overflow part is not added; it is reported as a special incoming transition exit-zone marker (`T|...`).

#### Autonomous Transitions

An **autonomous transition** has **no trigger event** — it fires based purely on its guard condition. These transitions:
- React to **exit zones** in component machines (when component machines reach certain states that satisfy the guard)
- Enable the PWS controller to respond to internal configuration changes
- Are essential for modeling fail-safe recovery, monitoring, and self-adaptation

Example: A transition with guard `[(m1.Failed), (m2.Error)]` (no trigger) fires automatically when either component machine `m1` reaches state `Failed` or `m2` reaches state `Error`.

**When to Use Autonomous Transitions:**
- Monitoring component machine states (e.g., detecting failures)
- Implementing recovery or fallback behaviors
- Modeling self-triggered evolution based on configuration
- Creating guard-only transitions that react to exit zones

#### Initial Transitions (Special Case)

**Initial transitions** (from the pseudo-state or its aliases) are a special case that deserves particular attention. Although they have no visible trigger event (like autonomous transitions), they are fundamentally different:

- **Initial transitions are event-triggered** by a hidden system startup event (conceptually `_init`)
- The `_init` trigger is **not displayed** on the transition label and is **reserved** (cannot be used on other triggered transitions)
- When the controller starts, the system "emits" this hidden event, triggering the initial transition(s)
- Multiple initial transitions can have different guards to select the appropriate starting state
- Initial transitions **accept TRUE as a valid guard** — this simply means "fire at startup without additional conditions"
- In dashboards and extended details, transitions from the pseudo-state are labeled **[initial]** to distinguish them from autonomous transitions

**Key insight**: Initial transitions behave like triggered transitions, not autonomous transitions:
- Autonomous transitions monitor exit zones and fire when component machines reach certain states
- Initial transitions fire once at startup based on their guard condition

This distinction is important for understanding controller reports, which correctly classify:
- **Initial**: Transitions from pseudo-state (hidden `_init` trigger)
- **Autonomous**: Guard-driven transitions that monitor component machine states  
- **Triggered**: Transitions with explicit event triggers

### Guard Conventions and Visual Feedback

The guard expression determines when a transition can fire. PWSEditor provides visual feedback to help you identify problematic guard configurations:

#### Default Guard for New Transitions

- **Triggered transitions** (with an event): Default to **TRUE** guard — the transition fires whenever the event occurs
- **Initial transitions** (from pseudo-state): Default to **TRUE** guard — fire at system startup (hidden **_init** trigger)
- **Autonomous transitions** (no event, not from pseudo-state): Default to **FALSE** guard — this is a **placeholder** indicating you need to specify a meaningful guard

#### Default Annotation Visibility for New Transitions

- **Triggered transitions**: guard label starts **hidden**; action label starts **visible**
- **Initial transitions**: guard label starts **visible**; action label starts **hidden**
- **Autonomous transitions**: guard label starts **visible** (and the guard toggle is not shown); action label starts **hidden**
- **Transition semantics label**: starts **hidden** for all transition types

When editing an autonomous transition guard:
- If the source state has **exit zones** (including provisional CS-only ones), the guard menu lists those exit-zone propositions.
- Internal (gray) exit zones are **not selectable** for autonomous guards; **provisional (blue)** exit zones are selectable.
- Incoming overflow markers (`T|...`) are also selectable (their target proposition appears in the menu).
- If the source state has **no exit zones**, the menu offers **TRUE** (fire immediately). Use **Remove guard** to go back to **FALSE** (never fires).

**Autonomous guard styling:** For autonomous transitions, each exit-zone proposition inside the guard is drawn **bold and underlined** to make reactive conditions stand out. TRUE/FALSE remain normal weight.

#### Advanced Guard Editor (Triggered Transitions)

Triggered transitions provide an **Advanced Guard Editor** (via the guard annotation’s context menu) that mirrors the constraints editor:
- Build guards as **OR-joined lines** of machine/state selections
- Each line is a **product** (configuration) displayed as `(m1.S, m2.T)`
- The preview uses the same **configuration format** (sum of products), not expanded disjunctions
- The machine/state pickers are **filtered to states implied by the source state’s semantics**
- Leaving all lines empty yields **ANY** (TRUE guard)

#### Triggered Guard Partitioning (Determinism)

Triggered transitions are **partitioned by source state + trigger event**. This partitioning **must be complete** to ensure determinism:
- **Disjointness**: guards for the same (source, trigger) must not overlap
- **Completeness**: together they should cover all configurations of the source state

Behavior in the editor:
- Overlapping guards in the same (source, trigger) group are shown in **red**
- Empty guards (no matching source semantics) are shown in **orange**
- Incomplete partitions (some source semantics not covered for that trigger) are shown in **orange**
- Guard menus filter out options that would **overlap** existing guards in the same group

**Initial transitions** are a special case of triggered transitions with a hidden **_init** event, and they participate in the same partitioning rules from the pseudo-state.

#### Problematic Guards (Red Highlighting)

Guards that appear in **red** indicate potential issues:

| Guard Condition | Problem | Explanation |
|-----------------|---------|-------------|
| **FALSE** on any transition | Placeholder | The transition can never fire. Edit the guard to specify a real condition. |
| **Orphan guard** | Exit zone no longer exists | The guard references an exit zone that is no longer present in the state’s reactive semantics. |
| **Overlapping triggered guard** | Non-deterministic | Overlaps another guard with the same source and trigger. |

**Tooltips**: Hover over a red guard to see an explanation of the specific problem.

Guards that appear in **orange** indicate either:
- an empty triggered guard (no matching source semantics), or
- an incomplete triggered partition (some source semantics are not covered for that trigger).
Hover over an orange guard to see which trigger is missing coverage.

**Note:** A **TRUE** guard on an autonomous transition is **allowed** and shown in black. It means the transition fires immediately upon entering the source state, and the destination inherits the source state's full semantics.

**How to Fix:**
- **FALSE guards**: Replace with a meaningful condition like `(m1.Active)` or `(m1.Failed), (m2.Error)`
- **Orphan guards**: Update the guard to reference exit zones that still exist in the state’s reactive semantics

### Autonomous Transitions and Exit Zones

**Exit zones** are the key to understanding autonomous transitions:

1. An **exit zone** is derived from a component machine autonomous transition that would move the system **outside** the current state's allowed configurations
2. When such a component transition becomes enabled in an allowed configuration, that configuration is an **exit zone**
3. PWS-level autonomous transitions can use guards that match exit-zone targets to react to those boundary conditions

Example workflow:
1. Component machine `monitor` has states: `OK`, `Warning`, `Critical`, with an autonomous transition `Warning → Critical`
2. This creates an exit zone with target `monitor.Critical`
3. The PWS controller has an autonomous transition with guard `[monitor.Critical]`
4. When the exit zone is reached, the controller moves to a recovery or alarm state

This mechanism allows PWS controllers to **observe and react** to their component machines without explicit events.

### Disabling a Transition

Transitions can be **enabled or disabled**:
- Disabled transitions are drawn in **lighter gray** and do not contribute to semantics
- Useful for conditional behavior without deleting structure

---

## Managing Assemblies

### What is an Assembly?

An **Assembly** is a collection of component state machines that work together. The controller state machine manages or coordinates these components.

### Viewing Assembly Machines

1. Click the **Assembly** view in the right panel
2. A list of all machines in the assembly appears
3. Each entry shows: `[id] - [name]`
4. Drag a row up or down to reorder the assembly; dashboards and the initial-configurations view refresh to match the new order

### Adding a Machine to the Assembly

**From Scratch:**
1. Click **Add** in the Assembly panel
2. Enter a machine ID and name
3. A new empty machine is created and added

**From Library:**
1. Click **Add** in the Assembly panel
2. Choose **Create new machine** or **Choose from library**
3. If choosing from the library, select a machine and then choose **Reference (shared)** or **Clone (independent)**

### Editing an Assembly Machine

1. **Double-click** a machine in the Assembly list
2. An **embedded editor** opens in the bottom-right panel
3. Edit the machine's states and transitions
4. Changes are reflected immediately

### Removing a Machine

1. Select the machine in the Assembly list
2. Click **Remove**
3. The machine is removed from the assembly (not the library)

### Cloning/Detaching a Machine

1. Select a machine in the Assembly list
2. Click **Detach/Clone**
3. A copy is created and stored in the library; the assembly entry is reassigned to the cloned instance
4. Useful for breaking shared references before further edits

---

## Using the Machine Library

### What is the Library?

The **Machine Library** is a repository of reusable state machine templates. Save machines to the library once and reuse them across multiple assemblies without duplicating structure.

### Switching to Library View

1. Click the **Library** toggle button in the right panel (top-right)
2. The library machine list appears

### Adding a Machine to the Library

**From the Assembly:**
1. Select a machine in the Assembly list
2. Click **Detach/Clone**
3. The cloned machine is added to the library

**Creating a New Library Machine:**
1. In the Library view, click **Add**
2. Create and design the machine in the embedded editor
3. The machine is automatically saved to the library

### Loading the Library

1. Switch to the **Library** view
2. Click **Load**
3. Select a `.mlib` file (library file)
4. The library is loaded with all previously saved machines

### Saving the Library

1. Switch to the **Library** view
2. Click **Save**
3. Choose a location and filename
4. The entire library is saved as a `.mlib` file

**Note:** Pseudo-state aliases and per-transition alias anchoring are preserved in library entries, including `.mlib` save/load and `.sm` import/export.

### Sharing Machines

To share reusable machines across projects:
1. Save the library to a `.mlib` file
2. Send the file to a colleague
3. They can load it with **Library** view → **Load**

---

## Semantic Constraints & Annotations

### Overview

**Semantics** describes allowed system configurations. Each state has two types:

- **Constraint Semantics**: User-specified allowed configurations
- **Computed Semantics**: Inferred from state machine structure

### Viewing Annotations

1. Go to **View → Toggle state dashboards** to show or hide annotation visibility
2. Each dashboard appears as a floating box near its state and uses a compact layout:
   - A colored state-name band at the top (green/gold/red) that matches the border; hover the name for a status explanation
   - **Constraints** and **configs** shown as a matrix (columns = assembly machine IDs in list order, rows = configurations, `-` means unspecified)
   - **Exit zones** stacked one per line; hover each exit zone for coverage/status details

### Component Fail States in Controller Semantics

If a **component Fail state** appears inside a controller state’s semantics, the dashboard shows a **warning**:

- **Warning: includes component fail states: m.FailState, ...**

This is a warning only: it does **not** automatically mark the controller state as Fail. It highlights that the controller state admits configurations where a component is in a fail state.

### Dashboard Minimization

State dashboards can be **minimized** to save screen space while still providing status feedback:

#### Minimizing a Dashboard

1. **Double-click** on any visible state dashboard
2. The dashboard shrinks to a small colored indicator (approximately 16×16 pixels)
3. The color reflects the overall state status:
   - **Green**: All OK — no issues detected
   - **Gold/Yellow**: State is unreachable
   - **Red**: Has issues — constraint violations, uncovered exit zones, or deadlocks

#### Restoring a Dashboard

1. **Double-click** on the minimized indicator
2. The dashboard expands back to its full size
3. The original position and size are preserved

#### Use Cases

- **Large diagrams**: Minimize dashboards to reduce visual clutter while keeping status visible
- **Quick status check**: The colored indicator provides at-a-glance health status
- **Focused editing**: Minimize dashboards for states you're not currently working on
- **Presentations**: Show clean diagrams with minimal annotations but preserve status indicators

#### Persistence

The minimized/expanded state is saved with the document, so dashboards will retain their state when you reopen the file.

### Editing Constraint Semantics

PWSEditor provides a visual Constraints Editor that makes it easy to build constraints using dropdown menus.

#### Opening the Editor

1. **Right-click** on a state's dashboard (the annotation box)
2. Select **Edit Constraints Semantics**

> **Note**: Pseudostates always have constraint "ANY" and cannot be edited.

**Quick reset to ANY:** Right-click the dashboard and choose **Set Constraints to ANY** to restore the default “allow all configurations” constraint.

**Exit-zone labels:** The same dashboard menu includes **Show machine IDs in exit zones** to toggle whether labels include machine IDs (default: on).

> **Tip**: When the constraints editor is empty (effectively `ANY`), the dashboard shows "ANY" in the constraints line. Once you add a constraint, the explicit text replaces ANY, and deleting all constraints resumes the default ANY display.

> **New behavior:** When the editor is empty, the constraint is recorded as an explicit `ANY` (Semantics.top). This means **all configurations satisfy constraints**, but exit-zone and deadlock analyses still run as usual. If you later add or delete constraints, the dashboards and reports update immediately.

#### The Visual Constraints Editor

The editor uses an **"add machine constraint"** approach where you explicitly add only the machines you want to constrain:

```
┌─────────────────────────────────────────────────────────────┐
│ Edit Constraints: StateName                                 │
├─────────────────────────────────────────────────────────────┤
│ Build constraints: Add machine constraints. Lines are       │
│ OR-joined.                                                  │
│ Tip: Only add machines you want to constrain. Unmentioned   │
│ machines allow any state.                                   │
├─────────────────────────────────────────────────────────────┤
│ Constraint Lines (OR-joined)                                │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ [m1.▼A ] [×]                              [+]  [×]      │ │
│ └─────────────────────────────────────────────────────────┘ │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ [m1.▼B ] [m2.▼X ] [×]                     [+]  [×]      │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ [+ Add Constraint Line]                                     │
├─────────────────────────────────────────────────────────────┤
│ Preview: (m1.A), (m1.B, m2.X)                               │
├─────────────────────────────────────────────────────────────┤
│                                    [Apply]  [Cancel]        │
└─────────────────────────────────────────────────────────────┘
```

#### Editor Features

| Element | Description |
|---------|-------------|
| **Constraint Line** | Each line represents one allowed configuration (OR-joined) |
| **Machine Chip** | A styled box showing `machineId.state` with dropdown and remove button |
| **[+] Button (in line)** | Add a machine constraint to this line - shows a popup menu with available machines and their states |
| **[×] Button (chip)** | Remove a specific machine constraint from the line |
| **[×] Button (line)** | Remove the entire constraint line |
| **[+ Add Constraint Line]** | Add a new (empty) constraint line |
| **Preview** | Shows the resulting constraint in text format |

#### Building Constraints Step-by-Step

**Example 1: Single machine constraint `(m1.A)`**
1. Click **[+]** in an empty line
2. Select machine **m1** from the popup
3. Select state **A** from the submenu
4. Result: One chip showing `m1.A`

**Example 2: Multi-machine constraint `(m1.A, m2.X)`**
1. Click **[+]** → select **m1** → select **A**
2. Click **[+]** again → select **m2** → select **X**
3. Result: Two chips showing `m1.A` and `m2.X`

**Example 3: Multiple OR-joined constraints `(m1.A), (m1.B, m2.X)`**
1. Build first line: `m1.A`
2. Click **[+ Add Constraint Line]**
3. Build second line: `m1.B`, then add `m2.X`
4. Result: Two lines, meaning "m1 in A" OR "m1 in B AND m2 in X"

#### Changing a State

Each machine chip has a dropdown - simply click it to change the state without removing and re-adding.

#### Preserving Partial Specifications

When you re-open the editor, your **original partial specification is preserved**. For example:
- You enter `m1.A` (partial - doesn't mention m2)
- The system computes the full semantics internally
- When you edit again, you see `m1.A` (not the expanded version)

This makes it easy to maintain and understand your constraints.

#### What States Are Available?

- All regular states from each machine in the assembly
- **Pseudostates are excluded** - they cannot be selected as constraint targets

### Understanding Configurations

A **configuration** specifies which state each machine is in. For example:
```
m1.S1, m2.S2
```
means "Machine m1 in state S1 AND Machine m2 in state S2"

Multiple configurations:
```
m1.S1, m2.S2
m1.S3, m2.S4
```
means "(m1.S1 AND m2.S2) OR (m1.S3 AND m2.S4)"

### Partial Configurations (Implicit Expansion)

When you specify a constraint that references only **some** of the machines in the assembly, PWSEditor automatically treats it as a constraint over **all possible combinations** of the unspecified machines.

#### Example

Suppose your assembly has two machines:
- **m1** with states `{A, B}`
- **m2** with states `{X, Y}`

If you specify the constraint:
```
m1.A
```

This **partial configuration** is automatically interpreted as:
```
(m1.A, m2.X) OR (m1.A, m2.Y)
```

In other words, specifying `(m1.A)` means "machine m1 is in state A, **regardless of** machine m2's state."

**Dashboard display note:** The dashboard does **not** expand partial constraints into all combinations. It shows each constraint line as entered in the compact matrix, using `-` for unspecified machines.

#### How It Works

PWSEditor uses an implication-based semantics where:
- A **fully-specified** configuration like `(m1.A, m2.X)` is more specific
- A **partial** configuration like `(m1.A)` is more general

The key rule is: **A configuration C1 implies configuration C2 if C1 contains all the constraints that C2 specifies (and possibly more).**

This means:
- `(m1.A, m2.X)` implies `(m1.A)` ✓ (C1 is more specific)
- `(m1.A, m2.Y)` implies `(m1.A)` ✓ (C1 is more specific)
- `(m1.A)` does NOT imply `(m1.A, m2.X)` ✗ (C2 has a constraint m2.X that C1 doesn't specify)

#### Practical Benefits

This partial configuration feature provides several advantages:

1. **Concise Constraints**: Instead of listing every possible combination, specify only the relevant machine states
2. **Maintenance**: When you add states to machine m2, a constraint like `(m1.A)` automatically includes the new combinations
3. **Abstraction**: Focus on what matters for the constraint without over-specifying

#### Normalization

PWSEditor automatically **normalizes** the set of configurations to keep only the most general ones. If you add both:
```
m1.A
m1.A, m2.X
```

The second line `(m1.A, m2.X)` is **subsumed** by the first `(m1.A)` and will be removed during normalization, since `(m1.A)` already covers all m2 states.

#### Guard Evaluation

The same logic applies to **transition guards**. A guard expression like `[m1.A]` will match any fully-specified configuration where m1 is in state A, regardless of the other machines' states.

### Semantics Display

Hover over dashboard elements to see:
- **State name band**: Explains why the border is green/gold/red (reachable, unreachable, constraint violations, exit-zone coverage, deadlocks)
- **Configuration rows**: Each row is one configuration; tooltips explain evolution. If it can evolve internally, the tooltip lists target configurations and/or exit zones (possibly multiple)
- **Exit zones**: Each line has a tooltip showing whether it is covered, uncovered, internal, orphan, or provisional

### Understanding Configuration Colors

In the state annotation dashboard, each configuration row has two independent visual attributes:

#### Row/Cell Color (Constraint Satisfaction)

| Color | Meaning |
|-------|---------|
| **Blue** | Constraint semantics row (user-defined) |
| **Green** | Computed configuration row that satisfies constraints |
| **Red** | Computed configuration row that violates constraints |
| **Gray** | Empty configuration (no component machines) |

#### Underline (Evolution Capability)

| Underline | Meaning |
|-----------|---------|
| **Green underline** | Configuration can evolve internally via autonomous transitions (may still be primary deadlock if no escape path exists) |
| **Red underline** | Secondary (internal) deadlock: cannot evolve internally **and** has no escape path to any outgoing transition |
| **No underline** | Internally stuck but **covered** by an outgoing transition |

> **Tip:** Empty constraint boxes mean the constraint semantics are `ANY`, so every computed configuration satisfies them (green text). The underline, exit-zone list, and controller report still tell you whether each configuration can evolve and whether it is a primary/secondary deadlock.

#### Combined Meanings

The text color and underline are **independent** — a configuration can have any combination:

| Example | Text | Underline | Meaning |
|---------|------|-----------|---------|
| `(m1.S)` | Green | Green underline | Satisfies constraints AND can evolve internally ✓ |
| `(m1.S)` | Green | No underline | Satisfies constraints, internally stuck but covered ✓ |
| `(m1.S)` | Green | Red underline | Satisfies constraints but is a **secondary (internal) deadlock** ✗ |
| `(m1.S)` | Red | Green underline | **Violates constraints** but can evolve internally ⚠ |
| `(m1.S)` | Red | No underline | Violates constraints, internally stuck but covered ⚠ |
| `(m1.S)` | Red | Red underline | Violates constraints AND is a **secondary (internal) deadlock** ✗ |

**Example from screenshot**: State S1 has constraint `(m1.T)` but computed semantics `(m1.T) (m1.S)`:
- `(m1.T)` is **green** (satisfies constraint) with **no underline** (internally stuck but covered)
- `(m1.S)` is **red** (violates constraint) with **green underline** (can evolve via autonomous transition)

### Dashboard Border Color

The border color of the state dashboard indicates the overall health of the state:

| Border | Meaning |
|--------|---------|
| **Green border** | All OK — no issues detected |
| **Gold/Yellow border** | State is unreachable (no computed configurations) |
| **Red border** | Has issues — one or more non-reachability problems need attention |

The state name is shown in a colored band at the top of the dashboard; the band color matches the border. Hover the name to see a concise explanation of the status.

**Configuration tooltips** also report whether the configuration **satisfies constraints** (or if constraints are **ANY**).

**Gold/Yellow border trigger**:
- **Unreachable state**: Empty state semantics (no configurations) — the state cannot be reached

**Red border triggers** (any of these conditions):
- **Constraint violations**: Computed configurations that don't satisfy user-defined constraints
- **Uncovered exit zones**: Exit zones not handled by any autonomous transition
- **Primary deadlocks**: Configurations with no escape path to an outgoing controller transition
- **Secondary deadlocks**: Internally stuck configurations that are also primary deadlocks

### Unreachable States

A state is **unreachable** when its computed semantics is empty — meaning no configurations are allowed. This typically happens when:

1. **Over-constrained**: The user-defined constraints are too restrictive and conflict with incoming transitions
2. **No valid path**: No combination of component machine states can satisfy the constraints while being reachable from previous states

**Example**: If a state has constraint `(m1.T)` but no incoming transition can lead to a configuration where m1 is in state T, the state has empty semantics and is marked with a gold/yellow border.

**How to fix**:
- Review and relax the constraints
- Check that incoming transitions can actually reach configurations that satisfy the constraints
- Verify the assembly machine structure allows the required states

---

## Deadlock Detection

### Overview

**Deadlock detection** identifies controller configurations that have no escape path out. PWSEditor distinguishes **primary** and **secondary (internal)** deadlocks in the state semantics annotation and reports.

PWSEditor also flags **component-level deadlocks** inside the assembly machines themselves. These are local deadlocks that exist regardless of controller logic and should be resolved at the component level.

### Primary vs Secondary Deadlock

1. **Primary deadlock configuration**: no escape path exists from the configuration to any enabled outgoing controller transition (excluding self-loops), either directly or after internal autonomous evolution.
2. **Secondary (internal) deadlock configuration**: the configuration is primary deadlocked **and** cannot evolve internally via autonomous component transitions.

Secondary deadlocks are stricter:
- Secondary implies primary.
- Primary does not imply secondary.

### How Deadlock Detection Works

#### Step 1: Reachability Analysis

For each configuration in a state's computed semantics, PWSEditor computes which other configurations are **reachable** via autonomous transitions:

```
Configuration C1 can reach C2 if:
  - There exists an autonomous transition in component machine M
  - The transition changes M's state from S1 to S2
  - C1 contains M.S1 and C2 is C1 with M.S1 replaced by M.S2
```

This analysis uses a worklist algorithm to compute the transitive closure of reachable configurations.

#### Step 2: Evolution Check

A configuration is flagged as a potential deadlock if its **reachable set is empty** — meaning no autonomous transitions are available from that configuration. This happens when all component machines in the configuration are in states with no outgoing autonomous transitions.

**Example**: If machine `m1` has states `S` and `T` with no outgoing autonomous transitions, then configurations `(m1.S)` and `(m1.T)` cannot evolve internally and are potential deadlocks.

**Note**: This is different from checking if a configuration can reach "all other configurations". A configuration that can reach *some* configurations (but not all) can still evolve and is NOT a deadlock.

#### Step 3: Transition Coverage and Escape-Path Check

PWSEditor first evaluates direct transition coverage, then checks for escape paths through internal evolution.

Direct coverage uses strict semantics:

- **Triggered transitions**: The configuration is covered only if the guard is satisfied **and** all actions on the transition are enabled from that configuration
- **Autonomous PWS transitions**: The configuration is covered only if guard and actions are jointly fireable under strict action semantics

Escape-path rule:
- A configuration is considered to have a way out if it is directly covered, **or** if it can evolve internally to another configuration that is directly covered.
- Configurations with no escape path are primary deadlocks. If they also fail internal evolution, they are secondary deadlocks.

### Component Deadlocks (Local to Assembly Machines)

A **component deadlock state** is a state in a component machine that has **no enabled outgoing transitions** (triggered or autonomous). If the component reaches such a state, it cannot leave it without changing the component itself.

In practice, a controller can only move a component out of a state if:
- The component has at least one **enabled triggerable transition** leaving that state, **and**
- A controller action emits the corresponding event.

If no outgoing transitions exist, the component is **deadlocked at the component level** and the controller cannot fix it.

### Visual Indicators

In the state semantics annotation dashboard:

| Visual | Meaning |
|--------|---------|
| **Red underline** | Secondary (internal) deadlock: configuration cannot evolve AND has no escape path to any outgoing transition |
| **No underline** | Internally stuck but covered by an outgoing transition |
| **Green underline** | Configuration can evolve internally (may still be primary deadlock if no escape path exists) |
| **Yellow underline** | Configuration contains a component deadlock state |

Primary deadlocks can still show a **green underline** when internal evolution exists but does not lead to any outgoing controller transition.

In component machine editors (assembly/library):

| Visual | Meaning |
|--------|---------|
| **Red ring around a state** | Unreachable component state (no path from pseudo-state) |
| **Red border** | Component deadlock state (no enabled outgoing transitions) |
| **Dashed yellow border** | Manually marked Fail state |
| **Red border around the machine panel** | At least one deadlock state exists in the component |
| **Red * in Assembly/Library list** | The component contains at least one deadlock state |
| **Tooltip on unreachable state** | "Unreachable state (no path from the initial pseudostate)" |
| **Tooltip on deadlock state** | "Deadlock state (no enabled outgoing transitions)" |
| **Tooltip on yellow‑underlined config** | Lists the deadlocked component state(s) in that configuration |

**Examples**:
1. If `(m1.A, m2.X)` can evolve to `(m1.B, m2.X)` via an autonomous transition in m1 → **OK** (green underline)
2. If `(m1.A, m2.X)` cannot evolve but is covered by transition guard `[m1.A]` → **OK** (no underline)
3. If `(m1.A, m2.X)` cannot evolve AND no transition covers it → **SECONDARY DEADLOCK** (red underline)

### Fail-State Masking

If a controller state is marked as **Fail state**, primary and secondary deadlock checks are masked for that state.  
Exit-zone coverage is also not required for fail states.

### Resolving Deadlocks

When you see red configurations, consider these solutions:

1. **Add autonomous transitions**: In component machines, add transitions that allow the stuck configuration to evolve
2. **Add PWS transitions**: Create an outgoing transition with a guard that covers the deadlock configuration
3. **Refine constraints**: Adjust constraint semantics to exclude the problematic configuration if it's not a valid system state
4. **Review assembly structure**: The deadlock may indicate a design issue in how component machines interact

### Automatic Recalculation

Deadlock detection is automatically recalculated whenever you:
- Add or remove states or transitions
- Change transition guards or actions
- Edit constraint semantics
- Enable or disable transitions
- Modify the assembly (add/remove/edit component machines)

---

## Exit Zones

### Overview

**Exit zones** identify boundary conditions where controller attention is required. In the current implementation, there are two semantic families:
- **Autonomous boundary exit zones**: classical zones generated by enabled autonomous component transitions that would leave the current state's allowed semantics.
- **Incoming transition overflow markers (`T|...`)**: generated when an incoming controller transition (under strict action semantics) contributes configurations that fall outside destination constraints.

Both families are shown in the dashboard exit-zone list and both are checked for autonomous coverage.

### What is an Exit Zone?

An exit zone entry occurs in one of these cases:
1. **Autonomous boundary case (classical)**:
   - A component machine has an enabled autonomous transition.
   - The transition source is compatible with current state semantics.
   - The transition target is outside current state semantics.
2. **Incoming overflow case (`T|...`)**:
   - An incoming controller transition contributes semantics to this state.
   - Under strict action semantics and destination constraints, part of that contribution falls outside constraints.
   - The out-of-constraint part is not added to state semantics and is surfaced as a `T|` marker.

### Exit Zone Structure

The exit-zone list, tooltips, and extended details expose:
- **Machine ID** and **target proposition**
- **Origin category** (CS-only, SS-only, both, or incoming overflow)
- **Coverage status** (covered/uncovered/internal/orphan/not required)

For classical zones, the source/target component transition is shown explicitly (`m:S→T`).  
For incoming overflow markers, the row is shown as `T|m:S`. The dashboard tooltip shows coverage status, and **Show Extended Details...** lists origin category and producing incoming transition(s).

### How Exit Zones Are Computed

Classical autonomous boundary zones are computed from autonomous component transitions:

```
1. Source proposition intersects state semantics (reachable source)
2. Target proposition does not intersect state semantics (outside target)
3. Record exit zone
```

Incoming overflow markers are computed from incoming controller transitions:

```
1. Compute incoming transition contribution under strict action semantics
2. Split by destination constraints:
   accepted = contribution AND CS
   overflow = contribution AND NOT(CS)
3. Add only accepted to state semantics
4. Convert overflow to T| exit-zone markers
```

### Exit Zones and Partial Configurations

A key insight is how exit zones interact with **partial configurations** (constraints that don't specify all machines).

#### Example: No Exit Zone with Partial Constraints

Suppose your assembly has:
- **m1** with states `{A, B}`
- **m2** with states `{X, Y}` and an autonomous transition X → Y

If the state's constraint is the partial configuration `(m1.A)`:
- This implicitly represents ALL m2 states: `{(m1.A, m2.X), (m1.A, m2.Y)}`
- When m2 transitions from X to Y:
  - Source `(m2.X)` intersected with `(m1.A)` = `(m1.A, m2.X)` — NOT EMPTY ✓
  - Target `(m2.Y)` intersected with `(m1.A)` = `(m1.A, m2.Y)` — NOT EMPTY!
- **Result**: NO exit zone is generated

**Why?** Because the partial constraint `(m1.A)` doesn't restrict m2 at all. The transition from X to Y keeps the system **within** the allowed configurations—it just moves from `(m1.A, m2.X)` to `(m1.A, m2.Y)`, both of which satisfy `(m1.A)`.

#### Example: Exit Zone with Full Specification

With the same assembly, if the constraint is fully specified as `(m1.A, m2.X)`:
- Source `(m2.X)` intersected with `(m1.A, m2.X)` = `(m1.A, m2.X)` — NOT EMPTY ✓
- Target `(m2.Y)` intersected with `(m1.A, m2.X)` = conflict (m2.X vs m2.Y) — EMPTY ✓
- **Result**: EXIT ZONE is generated

Here, the transition from X to Y would take the system **outside** the allowed configuration, so the controller needs to be aware of this boundary.

### Semantic Meaning

This behavior reflects an important design principle:

> **Partial configurations represent abstraction over unspecified machines.**

When you don't constrain a machine in your semantics, you're saying "I don't care what state that machine is in." Consequently:
- Autonomous transitions in unconstrained machines are **internal evolution**—they don't require controller intervention
- Only transitions that would violate **specified** constraints generate exit zones

**Provisional (CS-only) exit zones follow the same rule:** they are computed **only** from explicitly constrained machines and **exclude internal transitions** with respect to the constraints. If a constraint line does not mention a machine, that machine is treated as **ANY**, and its autonomous transitions are **not** reported as provisional exit zones.

### Exit Zones and Controller Design

Exit zones serve two purposes:

1. **Reactive Transitions**: Exit zones enable autonomous PWS-level transitions. When an exit zone's target matches a PWS transition's guard, that transition can fire.

2. **Coverage Analysis**: The annotation dashboard shows which exit zones are "covered" by outgoing transitions (green) versus uncovered (red). Uncovered exit zones may indicate missing transitions in your controller design.

For incoming overflow markers, coverage still means: an outgoing **autonomous** controller transition with a guard matching the marker target proposition.

### Impact on Overall App Behavior

The new typology changes behavior in a few important ways:
- **State semantics are constraint-safe at destination**: incoming transition results outside destination constraints are no longer inserted into computed semantics.
- **Boundary information is preserved**: filtered-out parts are surfaced as `T|` markers in the exit-zone list.
- **Status and border color can still turn red**: uncovered `T|` markers contribute to exit-zone coverage issues, even though the violating configurations are excluded from computed semantics.
- **Extended Dashboard gains diagnostics**: `EXIT ZONES ANALYSIS` now reports origin category, expected guard, coverage, and incoming producer transitions for overflow markers.
- **Controller Report includes them**: uncovered exit-zone checks include incoming overflow markers.

### Exit Zone Colors in the Dashboard

The dashboard uses this scheme for exit-zone rows:

| Color | Meaning |
|-------|---------|  
| **Green** | Exit zone is covered by an autonomous PWS transition ✓ |
| **Blue** | **Provisional** exit zone (CS-only; derived from constraints only) |
| **Red** | Exit zone is uncovered or orphan (no matching source state) ✗ |
| **Gray** | Internal exit zone (target already in semantics) — not selectable for autonomous guards |
| **Amber** | Coverage not required (fail state) |
| **Purple/Magenta** | Uncovered incoming overflow marker (`T|...`) |
| **`T|` prefix** | Incoming transition codomain overflow marker |

Internal exit zones (gray) represent autonomous component evolution that stays within the current state's semantics. They are **not selectable** as guards for autonomous PWS transitions. Provisional (blue) exit zones **are selectable** and indicate constraints-only boundaries.

**Provisional indicator:** A small **blue triangle** next to the “exit zones” label indicates that provisional (CS-only) exit zones are present.

**Label format:**
- Classical zones: `m:S→T` (machine ID + source state → target state)
- Incoming overflow markers: `T|m:S` (target proposition only)
- To hide machine IDs in labels, right-click the state dashboard and toggle **Show machine IDs in exit zones**.

**Hover details:** Exit zones are displayed one per line; hover a line to see coverage/status details. For producer-transition and origin-category analysis, use **Show Extended Details...**.

**Note**: For detailed analysis (including origin category, producer transitions for incoming overflow, expected guard, and coverage transitions), right-click the state dashboard and select **"Show Extended Details..."**.

**Design tips:**
1. **Precise Control**: Use fully-specified configurations when you need exact control over which component states are allowed. This generates exit zones for any transition that would leave those exact configurations.
2. **Incremental Refinement**: Start with partial constraints to focus on key machines, then add more constraints as needed based on the exit zones that appear.

---

## File Management

### Document Model

PWSEditor uses a document-based model with the following features:

- **Single document at a time**: One PWS workspace per editor window
- **Dirty tracking**: Unsaved changes are tracked and indicated in the window title
- **Annotation persistence**: Guard, action, and semantics annotation positions (plus the exit-zone label toggle) are saved with the document

### Window Title

The window title shows the current document status:

```
PWSEditor : filename    (saved document)
PWSEditor : filename *  (unsaved changes)
PWSEditor : Untitled    (new unsaved document)
```

### Creating a New Document

1. Go to **File → New**
2. If there are unsaved changes, you'll be asked whether to continue and discard them
3. A fresh untitled workspace is created
4. The current machine library is preserved

### Opening a Document

1. Go to **File → Open...**
2. Select a `.pws` file (PWS Workspace format)
3. The document loads with its saved controller, assembly, annotations, and workspace layout
4. If both the current library and the file library are non-empty, PWSEditor asks which library to keep

### Saving a Document

**Save (Ctrl+S equivalent):**
1. Go to **File → Save**
2. If the document has never been saved, a Save As dialog appears
3. Otherwise, the document is saved to its current location

**Save As:**
1. Go to **File → Save As...**
2. Choose a new location and filename
3. The document is saved to the new location

### Closing a Document

1. Go to **File → Close**
2. If there are unsaved changes, you'll be asked whether to continue and discard them
3. The current document is replaced with a fresh untitled workspace

### File Format

PWSEditor saves documents in `.pws` format, which includes:
- The PWS controller state machine
- All component machines in the assembly
- The machine library
- Annotation positions (guards, actions, semantics dashboards) and exit-zone label toggle
- **View settings**: dashboard visibility, grid visibility, snap-to-grid, grid size, edit mode, state size, state border thickness, and state font size
- **Pseudo-state aliases and per-transition alias anchoring** (controller, assembly machines, and library entries)
- **Window size, panel split positions, and Assembly/Library panel selection** (layout restoration)

Single machine files (`.sm`) and library files (`.mlib`) also preserve pseudo-state aliases and transition anchoring.

### Legacy Format Support

The current UI supports `.pws` workspaces, `.sm` single-machine files, and `.mlib` library files.
Legacy `.bin` workspace loading is not exposed in the current UI.

### Exporting

**Export as PDF:**
1. Go to **File → Export as PDF**
2. Choose destination (**Save to File** or **Save to Clipboard**)
3. If saving to file, choose location and filename in the standard save dialog
4. A **vector PDF** of the current diagram is created or copied to clipboard

**Export as PNG:**
1. Go to **File → Export as PNG**
2. Choose destination (**Save to File** or **Save to Clipboard**)
3. If saving to file, choose location and filename in the standard save dialog
4. A **PNG snapshot** of the current diagram is created or copied to clipboard

---

## Menu Reference

### File Menu

| Option | Description |
|--------|-------------|
| **New** | Create a new untitled PWS workspace (preserving the current library contents) |
| **Open...** | Open an existing `.pws` workspace file |
| **Save** | Save current document (prompts for location if new) |
| **Save As...** | Save current document to a new location |
| **Close** | Replace the current document with a new untitled workspace |
| **Export as PDF** | Export current diagram as a vector PDF |
| **Export as PNG** | Export current diagram as a PNG image |
| **Exit** | Close the editor |

### Edit Menu

| Option | Description |
|--------|-------------|
| **Undo** | Undo the last document change |
| **Redo** | Redo the last undone change |
| **Select All** | Select all objects in the active editor panel |
| **Edit mode** | Toggle edit vs. view mode (controls control handles/interaction) |

### View Menu

| Option | Description |
|--------|-------------|
| **Toggle state dashboards** | Toggle display of semantic annotations |
| **Show exit-zone machine IDs** | Toggle machine IDs in exit-zone labels and related dashboard displays |
| **Show Grid** | Toggle grid display |
| **Snap to Grid** | Toggle automatic grid snapping |
| **Set grid size...** | Adjust snap-to-grid size |
| **State size** | Choose state diameter (Small/Medium/Large) |
| **State border thickness** | Choose state outline thickness (Thin/Medium/Thick) |
| **State font size** | Choose font size for labels and annotations (Small/Medium/Large) |
| **LTL Editor...** | Present in the menu but currently disabled |
| **Check now** | Present in the menu but currently disabled |

### Testing Menu

| Option | Description |
|--------|-------------|
| **Disable exit-zone computation** | Temporarily disable computed exit zones |
| **Treat CS-covered targets as internal exit zones** | Toggle the experimental constraint-aware exit-zone internality behavior |

### Info Menu

| Option | Description |
|--------|-------------|
| **Show Info** | Open the application information dialog |

---

## Testing Features

The **Testing** menu contains analysis toggles for comparing alternative semantics behaviors. These options are intended for experimentation and debugging; they change how the editor computes or classifies exit-zone-related results.

If you are building or reviewing a normal model, keep both options at their defaults unless you are intentionally testing a semantic variant.

### Disable Exit-Zone Computation

This checkbox turns off computed exit-zone generation for the current controller model.

When enabled:
- Classical autonomous boundary exit zones are not generated
- Provisional constraints-only exit zones are not generated
- Incoming overflow markers (`T|...`) are not generated
- Coverage and report results that depend on those computed exit zones change accordingly

What remains active:
- State semantics computation
- Constraint checking
- Deadlock analysis
- Transition semantics computation

Use this option when you want to inspect the model without any exit-zone-driven diagnostics.

**Persistence:** this toggle is not currently stored in `.pws` workspace data. Reopening the workspace restores the normal default, with exit-zone computation enabled.

**Example:**

Suppose the assembly has one machine `m1` with an autonomous transition:

```text
A -> B
```

and controller state `S` currently has computed semantics:

```text
m1.A
```

Normal behavior:
- The dashboard for `S` shows an exit zone `m1:A→B`
- If no autonomous controller transition covers it, the exit zone is reported as uncovered

With **Disable exit-zone computation** enabled:
- That exit zone is not generated
- The uncovered-exit-zone warning disappears
- The rest of the state semantics still stays computed normally

### Treat CS-Covered Targets as Internal Exit Zones

This checkbox enables an **experimental constraint-aware internality mode**.

Default behavior:
- An exit zone is treated as **internal** only if its target intersects the state's **computed state semantics** (`SS`)

With this option enabled:
- An exit zone is treated as **internal** if its target intersects `SS` **or** the state's **explicit constraint semantics** (`CS`)
- This applies only when the state has explicit constraints

Practical effects:
- Some exit zones that would normally appear as external boundaries may instead be shown as **internal**
- Exit-zone coverage status, dashboard coloring, and controller-report results may change
- During fixed-point semantics propagation, internal codomain consistent with `SS ∪ CS` can be folded back into the state's computed semantics, clipped by explicit constraints

Use this option to compare the default **SS-only** interpretation against a more **constraint-aware** interpretation of internal autonomous evolution.

**Persistence:** this toggle is saved with workspace annotation/UI data and restored when the workspace is reopened. New documents reset it to **off**.

**Example:**

Suppose controller state `S` has explicit constraints:

```text
m1.A
m1.B
```

so both `A` and `B` are allowed by constraints, but the current computed semantics has only:

```text
m1.A
```

and machine `m1` has an autonomous transition:

```text
A -> B
```

Default behavior:
- `m1:A→B` is treated as an exit zone, because `B` is not yet in the current computed state semantics

With **Treat CS-covered targets as internal exit zones** enabled:
- `m1:A→B` is treated as internal, because `B` is explicitly allowed by the state constraints
- The editor may absorb that internal codomain into the state's computed semantics instead of treating it as an external boundary

---

## Controller Report

The **Controller Report** provides a comprehensive overview of all issues in your controller design. It enumerates problems that need attention and correlates with the visual indicators (red highlights) shown on the diagram.

### Accessing the Report

**Right-click** on an empty area of the controller canvas (the same menu used to add states) and select **"Controller Report..."** to open the report dialog.

### Report Sections

The report is organized into several sections:

#### Summary
Shows controller statistics (states, transitions, assembly machines) and a quick count of all detected issues.

#### Guard Problems
Lists transitions with problematic guard conditions:

| Problem Type | Description | Visual Indicator |
|--------------|-------------|------------------|
| **FALSE Guard** | Placeholder that needs to be set — transition will never fire | Red guard label `[FALSE]` |
| **Orphan Guard** | References an exit zone that no longer exists | Red guard label |

**How to Fix:**
- **FALSE**: Edit the guard to specify a meaningful condition (e.g., `m1.Failed`)
- **Orphan**: Update the guard to reference exit zones that still exist

**Note:** A **TRUE** guard on an autonomous transition is allowed and is **not** reported as a problem. It means the transition fires immediately upon entering the source state.
Partition completeness warnings (orange guard labels) are visual-only and are not currently listed in the report.

#### Action Problems (Orphan Actions)
Lists actions that reference events not reachable from the source state's semantics or constraints. An action is "orphan" when:
- The machine it references is not included in the source state's semantics
- The event (trigger) it references cannot be fired from any state in the source semantics

Orphan actions are shown in **red** on the diagram.

**How to Fix:** 
- Remove the orphan action from the transition
- Or update the source state's constraints to include machine states that enable the referenced events

#### Uncovered Exit Zones
Lists exit zones that have no covering autonomous transition. This includes:
- classical autonomous boundary zones
- incoming transition codomain overflow markers (`T|...`)

Each listed zone should ideally be handled by an autonomous transition with a matching guard.

**Fail states:** Exit zones in fail states are **excluded** from this section because coverage is not required.

**How to Fix:** Add autonomous transitions with guards matching the listed exit zones.

#### Orphan Exit Zones
Lists exit zones whose **source state no longer exists** in the assembly. These are inconsistent/stale exit zones and are shown in **red**.

**How to Fix:** Restore the missing source state/transition or recompute semantics to remove stale exit zones.

#### Constraint Violations
Lists configurations that appear in computed state semantics but violate user-defined constraints. These are also shown in **red** in state dashboards.

**How to Fix:** Review and adjust either the constraints or the transition structure.

#### Unreachable States
Lists states with **no computed configurations** (the state is unreachable). These states show a gold/yellow dashboard with the message **"State is unreachable (no configurations)"**.

**How to Fix:** Add or adjust incoming transitions/guards so the state can be reached, or remove the unused state.

#### Primary Deadlock Configurations
Lists configurations with **no escape path to an outgoing controller transition** under strict action semantics.

**How to Fix:** Add enabled outgoing transitions that can fire either directly from these configurations or after internal evolution (self-loops do not count as exits), or mark the state as Fail if sink behavior is intentional.

#### Secondary (Internal) Deadlock Configurations
Lists configurations that are both:
- with no escape path to outgoing transitions, and
- unable to evolve internally via autonomous component transitions.

These are stricter deadlocks and are shown with red underlines in dashboards.

#### LTL Formula Verification
*(Coming soon)* Once LTL verification is implemented, this section will show which formulas are satisfied and which are violated.

#### Overall Status
Shows a final assessment: either **"CONTROLLER IS WELL-FORMED"** (no issues) or a summary of outstanding problems.

### Report and Diagram Correlation

The report correlates with visual indicators on the diagram:
- **Red guard labels**: Problematic guards (FALSE, orphan)
- **Red action labels**: Orphan actions (not reachable from source state semantics)
- **Red text in dashboards**: Constraint violations
- **Red underline in dashboards**: Secondary (internal) deadlocks
- **Primary and secondary deadlocks appear in separate controller-report sections, even when no explicit constraint is specified.**
- **Uncovered exit zones**: No visual indicator on transitions, but shown in state dashboards
- **Orphan exit zones**: Shown in red in state dashboards and listed in the report
- **Unreachable states**: Gold/yellow dashboard with "State is unreachable (no configurations)"
- **Dashed yellow state border**: Fail state — exit-zone and deadlock checks are masked for that state

Use the report to get a comprehensive overview, then use the diagram to locate and fix individual issues.

---

## Tips & Troubleshooting

### General Tips

1. **Save frequently**: Use **File → Save** regularly to avoid losing work
2. **Watch the title bar**: An asterisk (*) indicates unsaved changes
3. **Use the library**: Build reusable machine templates to speed up future designs
4. **Name clearly**: Use descriptive names for states and machines for clarity
5. **Align visually**: Use grid snapping and drag repositioning to keep diagrams organized
6. **Export documentation**: Use PDF export for vector output or PNG export for quick sharing
7. **Monitor deadlocks**: Pay attention to red underlines (secondary deadlocks) and report sections (primary + secondary)

### Understanding Visual Feedback

#### State Annotation Colors

| Visual Element | Meaning |
|----------------|---------|
| Blue text (top row) | Constraint semantics you defined |
| Green text | Configuration satisfies constraints |
| Red text | Configuration violates constraints |
| Green underline | Configuration can evolve internally via autonomous transitions (may still be primary deadlock if no escape path exists) |
| Red underline | Secondary (internal) deadlock: internally stuck and has no escape path to any outgoing transition |
| No underline | Internally stuck but covered by an outgoing transition |
| Red (in exit zone list) | Exit zone is uncovered or orphan (no matching source state) |
| Green (in exit zone list) | Exit zone is covered by a PWS autonomous transition |
| Blue (in exit zone list) | Provisional exit zone (CS-only; derived from constraints only) |
| Gray (in exit zone list) | Internal exit zone (target already in semantics) — not selectable for autonomous guards |
| `T|` prefix (in exit zone list) | Incoming transition codomain overflow marker (outside destination constraints) |

**Tip**: Right-click on the dashboard and select **"Show Extended Details..."** for detailed exit zone origin analysis.
- Drag annotations to reorganize (they snap to grid)

#### "Red text or red underline appears"
- **Cause**: Constraint violations (red text) or secondary deadlocks (red underline)
- **Solution**: 
  - Add autonomous transitions in component machines
  - Add PWS transitions with guards covering the deadlock
  - Review if the configuration should be excluded via constraints

#### "State has red border"
- **Cause**: One or more issues detected:
  - Configurations violate constraints
  - Exit zones not covered by transitions
  - Orphan exit zones (missing source state)
  - Deadlock configurations present
- **Solution**: Check each row of the annotation for red items and address them

#### "Pseudo-state or aliases missing"
- **Solution**: At least one pseudo-state always remains; if the original was deleted, an alias may have been promoted to become the real pseudo-state
- Recreate missing aliases from the canvas menu and reattach initial transitions if needed

#### "Library didn't load"
- **Solution**: Ensure the `.mlib` file is valid and matches the current assembly format
- Check that machine IDs in the library don't conflict with assembly machine IDs

#### "File won't save"
- **Solution**: Check write permissions in the target directory
- Ensure the filename doesn't contain invalid characters
- Try saving to a different location

#### "States overlap when editing"
- **Solution**: Enable **Snap to Grid** from **View** menu
- Drag states to reposition them precisely

#### "Semantics not updating"
- **Solution**: Semantics are recalculated automatically; if the UI seems stale, try making a small edit (e.g., toggle a dashboard or edit a guard) to retrigger calculation
- Check that all component machines are properly configured
- Verify transitions have correct guards

### Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| **Cmd/Ctrl + N** | New workspace |
| **Cmd/Ctrl + O** | Open workspace |
| **Cmd/Ctrl + S** | Save |
| **Cmd/Ctrl + Shift + S** | Save As |
| **Cmd/Ctrl + Z** | Undo |
| **Cmd/Ctrl + Shift + Z** | Redo |
| **Cmd/Ctrl + A** | Select all objects in the active panel |
| **Cmd/Ctrl + E** | Toggle edit mode |
| **W/A/S/D** | Pan the diagram |
| **Cmd/Ctrl + Shift + E** | Export as PDF |
| **Cmd/Ctrl + P** | Export as PNG |
| **Cmd/Ctrl + Drag (from state)** | Create a guard-triggered transition to the release target |
| **Cmd/Ctrl + Shift + Drag (from state)** | Create an event-triggered transition with default event `ev` |
| **Cmd/Ctrl + Drag (from pseudo-state/alias)** | Create an initial transition (hidden `_init`) |
| **Double-click state** | Rename state |
| **Right-click** | Context menu |

### Performance Tips

- **Large assemblies**: For systems with many machines, consider breaking into smaller assemblies
- **Complex guards**: Keep guard conditions simple and readable
- **Annotations**: Disable annotations display for large diagrams to improve responsiveness
- **Semantics calculation**: Large state spaces may cause slower recalculation—be patient

### Transition Features

#### Disabling Transitions

Transitions can be temporarily disabled without deleting them:

1. Right-click on the transition control handle
2. Select **Disable Transition** (or **Enable Transition** to re-enable)
3. Disabled transitions appear in light gray
4. Disabled transitions do not contribute to semantics calculations

This is useful for:
- Testing alternative behaviors
- Temporarily excluding paths without losing the structure
- Debugging semantic issues

#### Transition Annotations

Each transition can have visible annotations:

- **Guard annotation**: Shows the guard condition `[guard]`
- **Action annotation**: Shows actions `〈 action1, action2 〉`
- **Semantics annotation**: Shows computed transition semantics

Toggle visibility via the transition's right-click menu.  
Default visibility for newly created transitions:
- Triggered: guard hidden, action visible, semantics hidden
- Initial: guard visible, action hidden, semantics hidden
- Autonomous: guard visible, action hidden, semantics hidden

**Note:** For autonomous transitions, the guard toggle is not shown (guards remain visible by default).

---

## Workflow Example: Building a Traffic Light Controller

Here's a step-by-step example to get started:

### Step 1: Create the Controller

1. Open PWSEditor
2. Go to **File → New**
3. Right-click the canvas and select **Add State**
4. Create three states: `Red`, `Yellow`, `Green`
5. Add transitions:
   - `Red` → `Green`
   - `Green` → `Yellow`
   - `Yellow` → `Red`

### Step 2: Add Timing Semantics

1. Right-click the `Red` state dashboard
2. Select **Edit Constraints Semantics**
3. Enter: `timer.idle, light.red` (assuming machines named `timer` and `light`)
4. Repeat for other states

### Step 3: Create Component Machines

1. Click the **Assembly** view
2. Click **Add** to create a new machine
3. Name it `timer` (with ID `timer`)
4. Create two states: `idle`, `running`
5. Optional: click **Detach/Clone** to store a reusable copy in the library

### Step 4: Add Another Machine

1. Click **Add** again
2. Create machine `light` with states: `red`, `yellow`, `green`
3. Optional: click **Detach/Clone** to store a reusable copy in the library

### Step 5: Test and Save

1. Go to **File → Save**
2. Name your project `traffic_light.pws`
3. Go to **File → Export as PDF** or **File → Export as PNG** to generate a diagram

---

## Additional Resources

For more information about Part-Whole Statecharts theory, see the project README.md or documentation at your institution's research resources.

---

## Recent Changes & Version History

### Version 3.0 Updates

#### New File Management System
- **Document-based workflow**: New/Open/Save/Save As/Close operations
- **Dirty tracking**: Unsaved changes indicated by asterisk (*) in window title
- **Annotation persistence**: Guard, action, and semantics annotation positions (plus the exit-zone label toggle) are saved and restored
- **New file format**: `.pws` extension for workspace files

#### Enhanced Deadlock Detection
- **Primary deadlocks**: configurations with no escape path to an outgoing controller transition
- **Secondary deadlocks**: primary deadlocks that also cannot evolve internally
- **Correct single-config handling**: Configurations like `(m1.T)` are correctly identified as deadlocks when state T has no outgoing autonomous transitions
- **Visual indicators**: Red underline for secondary deadlocks, green underline for configurations that can evolve internally (possibly still primary deadlocks), no underline for internally stuck but covered
- **Border feedback**: State annotation border color reflects overall state health

#### Automatic Semantics Recalculation
All model changes now automatically trigger semantics recalculation:
- Adding/removing states and transitions
- Changing guards and actions
- Editing constraint semantics
- Enabling/disabling transitions
- Modifying assembly machines

#### Transition Enable/Disable
- Transitions can be enabled or disabled via right-click menu
- Disabled transitions appear in light gray
- Disabled transitions are excluded from semantics calculations

#### PDF Export
- Export diagrams as **vector PDF** documents for documentation and sharing

#### UI Improvements
- Window title shows document name and dirty status
- Improved annotation positioning on file load
- Better grid snapping for annotations

---

**Questions or suggestions?** Reach out to the development team or check the project repository for updates.

**Happy modeling!**
