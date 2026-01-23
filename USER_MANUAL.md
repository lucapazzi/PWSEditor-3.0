# PWSEditor User Manual

## Table of Contents

1. [Getting Started](#getting-started)
2. [Concepts & Terminology](#concepts--terminology)
3. [The Main Interface](#the-main-interface)
4. [Working with States](#working-with-states)
5. [Working with Transitions](#working-with-transitions)
6. [Managing Assemblies](#managing-assemblies)
7. [Using the Machine Library](#using-the-machine-library)
8. [Semantic Constraints & Annotations](#semantic-constraints--annotations)
9. [Deadlock Detection](#deadlock-detection)
10. [Exit Zones](#exit-zones)
11. [File Management](#file-management)
12. [Menu Reference](#menu-reference)
13. [Controller Report](#controller-report)
14. [Tips & Troubleshooting](#tips--troubleshooting)

---

## Getting Started

### Installation

PWSEditor is a Java application. Ensure you have **Java 11 or later** installed on your system.

### Running PWSEditor

From the command line, navigate to the PWSEditor directory and run:

```bash
javac -d out -cp "lib/pdfbox-2.0.29.jar:lib/fontbox-2.0.29.jar:lib/commons-logging-1.2.jar" -sourcepath src src/pws/editor/PWSEditor.java
java -cp out pws.editor.PWSEditor
```

PWSEditor will launch with an empty controller editor ready for use.

---

## Concepts & Terminology

### Part-Whole Statecharts (PWS)

A **Part-Whole Statechart** is a behavioral modeling formalism that describes:

- **Controller**: A top-level state machine that controls or coordinates behavior
- **Assembly**: A collection of component state machines that can operate synchronously and asynchronously
- **States**: Control points in a state machine
- **Transitions**: Connections between states, optionally triggered by events and guarded by conditions
- **Semantics**: Formal specifications of allowed system configurations

### Key Terms

| Term | Definition |
|------|-----------|
| **State** | A control point in a state machine where the system can reside |
| **Pseudo-State** | An initial state (marked with a small filled circle) |
| **Transition** | A directed arc connecting states, optionally with triggers, guards, and actions |
| **Triggered Transition** | A transition that fires when a specific event occurs (and guard is satisfied) |
| **Autonomous Transition** | A transition without a trigger event; fires based on guard condition alone |
| **Guard** | A boolean condition that must be true to enable a transition |
| **Action** | An emission (event output) that occurs when a transition fires |
| **Constraint Semantics** | User-specified allowed configurations for a state |
| **Computed Semantics** | Semantics inferred from state machine structure |
| **Assembly** | A collection of component machines forming a part-whole hierarchy |
| **Machine Library** | A repository of reusable state machine templates |
| **Exit Zone** | A guard condition on an autonomous transition that monitors component machine states |
| **Deadlock Configuration** | A configuration that cannot evolve and has no way out via transitions |

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
  - **Assembly Tab**: Lists component machines in the current assembly
  - **Library Tab**: Lists saved, reusable machine templates
- **Bottom Section**: Embedded editor for viewing/editing selected assembly machines

---

## Working with States

### Creating a State

1. **Right-click** on an empty area of the canvas (left panel)
2. Select **"Add State"** from the context menu
3. Click to place the state on the canvas
4. Enter the state name in the dialog box

Alternatively, use the **Edit → Add State** menu.

### Editing a State

1. **Double-click** the state to select it (it will be highlighted)
2. **Right-click** the state to see options:
   - **Rename**: Change the state name
   - **Delete**: Remove the state
   - **Edit Constraints**: Define semantic constraints (see [Semantic Constraints](#semantic-constraints--annotations))
   - **Toggle Annotation**: Show/hide semantic annotations

### The Pseudo-State

The pseudo-state (initial state) is automatically created and appears as a **small filled circle**. All state machines must start from this pseudo-state. It can be used as a source for transitions to define initial behavior.

### Visibility and Layout

- **Snap to Grid**: States and annotations automatically snap to a grid for clean alignment
- **Drag States**: Click and drag states to reposition them
- **Grid Size**: Adjustable from the **View** menu for fine-grained control

---

## Working with Transitions

### Creating a Transition

1. **Right-click** on a state (the source)
2. Select **"Add Transition"** from the menu
3. Click on the target state to complete the transition
4. A curved arrow appears connecting the two states

Alternatively:
- Use **Edit → Add Transition** to create transitions with dialog options
- For an initial transition from the pseudo-state, use **Edit → Add initial transition**

### Editing Transition Properties

Click on the transition (the arrow) to select it. You can then:

1. **Edit the Trigger Event**: Add an event that triggers the transition
2. **Edit the Guard**: Add a boolean condition (e.g., `x > 5`)
3. **Edit Actions**: Add emissions (actions that occur when the transition fires)

Use **in-place editors** (floating text boxes) to directly modify:
- **Guard labels**: Click the guard text and edit
- **Action labels**: Click the action text and edit
- **Semantics labels**: View computed semantics

### Understanding Transition Types

PWSEditor supports two fundamentally different transition types:

#### Triggered Transitions

A **triggered transition** has a **trigger event** (shown as text on the arrow). These transitions:
- Fire when the specified event occurs **AND** the guard condition is satisfied
- Require external stimulus to activate
- Are the most common type in reactive systems

Example: A transition labeled `button_pressed [isReady] / beep` fires when:
1. The event `button_pressed` occurs
2. The guard `isReady` evaluates to true
3. The action `beep` is then emitted

#### Autonomous Transitions

An **autonomous transition** has **no trigger event** — it fires based purely on its guard condition. These transitions:
- React to **exit zones** in component machines (when component machines reach certain states that satisfy the guard)
- Enable the PWS controller to respond to internal configuration changes
- Are essential for modeling fail-safe recovery, monitoring, and self-adaptation

Example: A transition with guard `[m1.Failed ∨ m2.Error]` (no trigger) fires automatically when either component machine `m1` reaches state `Failed` or `m2` reaches state `Error`.

**When to Use Autonomous Transitions:**
- Monitoring component machine states (e.g., detecting failures)
- Implementing recovery or fallback behaviors
- Modeling self-triggered evolution based on configuration
- Creating guard-only transitions that react to exit zones

#### Initial Transitions (Special Case)

**Initial transitions** (from the pseudo-state) are a special case. Although they appear autonomous (no visible trigger event), they are actually **triggered by a hidden system startup event**. Therefore:

- Initial transitions **accept TRUE as a valid guard** — this means "fire at startup"
- They do NOT trigger the "TRUE on autonomous" warning
- They behave like triggered transitions: the system "sends" a startup event when initialization begins

### Guard Conventions and Visual Feedback

The guard expression determines when a transition can fire. PWSEditor provides visual feedback to help you identify problematic guard configurations:

#### Default Guard for New Transitions

- **Triggered transitions** (with an event): Default to **TRUE** guard — the transition fires whenever the event occurs
- **Initial transitions** (from pseudo-state): Default to **TRUE** guard — fire at system startup
- **Autonomous transitions** (no event, not from pseudo-state): Default to **FALSE** guard — this is a **placeholder** indicating you need to specify a meaningful guard

#### Problematic Guards (Red Highlighting)

Guards that appear in **red** indicate potential issues:

| Guard Condition | Problem | Explanation |
|-----------------|---------|-------------|
| **FALSE** on any transition | Placeholder | The transition can never fire. Edit the guard to specify a real condition. |
| **TRUE** on autonomous transition | Immediate firing | With no event required and guard always true, this transition fires immediately upon entering the source state, which is usually unintended. **Exception**: Initial transitions (from pseudo-state) are NOT flagged — they have a hidden startup trigger. |
| **Orphan guard** | References removed machines | The guard references component machine states that no longer exist in the assembly (e.g., after removing a machine). |

**Tooltips**: Hover over a red guard to see an explanation of the specific problem.

**How to Fix:**
- **FALSE guards**: Replace with a meaningful condition like `m1.Active` or `m1.Failed ∨ m2.Error`
- **TRUE on autonomous**: Either add a trigger event (making it triggered), or change the guard to a specific condition
- **Orphan guards**: Update the guard to reference only machines currently in the assembly

### Autonomous Transitions and Exit Zones

**Exit zones** are the key to understanding autonomous transitions:

1. An **exit zone** is defined by the guard expression on an autonomous transition
2. When component machines reach states that satisfy the guard, the transition becomes **enabled**
3. The PWS controller can then fire the transition to respond to the configuration

Example workflow:
1. Component machine `monitor` has states: `OK`, `Warning`, `Critical`
2. PWS controller has an autonomous transition with guard `[monitor.Critical]`
3. When `monitor` reaches `Critical` state, the autonomous transition fires
4. The controller moves to a recovery or alarm state

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

1. Click the **Assembly** tab in the right panel
2. A list of all machines in the assembly appears
3. Each entry shows: `[id] - [name]`

### Adding a Machine to the Assembly

**From Scratch:**
1. Click **Add** in the Assembly panel
2. Enter a machine ID and name
3. A new empty machine is created and added

**From Library:**
1. Go to the **Library** tab
2. Select a machine template
3. Click **Add to Assembly**
4. The machine is cloned and added to the assembly

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
3. A copy is created and added to the assembly with a new ID
4. Useful for creating similar machines with independent evolution

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
2. Click **Edit** and then **Save to Library** (in the embedded editor menu)
3. Enter a key (identifier) and machine name
4. The machine is saved to the library

**Creating a New Library Machine:**
1. In the Library view, click **Add**
2. Create and design the machine in the embedded editor
3. The machine is automatically saved to the library

### Loading the Library

1. Go to **File → Load Library...**
2. Select a `.mlib` file (library file)
3. The library is loaded with all previously saved machines

### Saving the Library

1. Go to **File → Save Library...**
2. Choose a location and filename
3. The entire library is saved as a `.mlib` file

### Sharing Machines

To share reusable machines across projects:
1. Save the library to a `.mlib` file
2. Send the file to a colleague
3. They can load it with **File → Load Library...**

---

## Semantic Constraints & Annotations

### Overview

**Semantics** describes allowed system configurations. Each state has two types:

- **Constraint Semantics**: User-specified allowed configurations
- **Computed Semantics**: Inferred from state machine structure

### Viewing Annotations

1. Go to **View → Show State Annotations** to toggle annotation visibility
2. Annotations appear as floating boxes near states, showing:
   - Constraint configurations
   - Computed configurations
   - Reactive space (enabled transitions)

### Editing Constraint Semantics

PWSEditor provides a visual Constraints Editor that makes it easy to build constraints using dropdown menus.

#### Opening the Editor

1. **Right-click** on a state's dashboard (the annotation box)
2. Select **Edit Constraints Semantics**

> **Note**: Pseudostates always have constraint "ANY" and cannot be edited.

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

Hover over or click annotation boxes to see:
- **Constraint**: Rules you defined (displayed in blue)
- **Computed**: Derived semantics based on structure
- **Violations**: Misaligned configurations highlighted in red
- **Reactive Space**: Exit zones / transitions enabled from this state

### Understanding Configuration Colors

In the state annotation dashboard:

| Color | Meaning |
|-------|---------|
| **Blue text** | Constraint semantics (user-defined) |
| **Green text** | Computed configuration that satisfies constraints |
| **Red text** | Computed configuration that violates constraints |
| **Red** | True deadlock: configuration cannot evolve AND is not covered by any transition |
| **Green** | Covered: configuration has a way out via an outgoing transition |
| **Green underline** | Configuration can evolve internally (not a deadlock risk) |
### Overview

**Deadlock detection** is a critical feature that identifies configurations where the system could get stuck with no way to evolve. PWSEditor automatically detects and highlights potential deadlock situations in the state semantics annotation.

### What is a Deadlock Configuration?

A configuration is considered a **true deadlock** if it meets BOTH of these conditions:

1. **Cannot evolve internally**: The configuration cannot reach all other configurations in the state's semantics via autonomous transitions of the component machines
2. **Not covered by any transition**: No outgoing transition (triggered or autonomous) has a guard that matches this configuration

A configuration that cannot evolve internally but IS covered by an outgoing transition is NOT a deadlock—it has a "way out" through the transition.

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

#### Step 2: Connectivity Check

A configuration is flagged as a potential deadlock if it **cannot reach ALL other configurations** in the state's semantics. This means the system could get stuck in a subset of configurations without being able to reach the full state space.

#### Step 3: Transition Coverage Check

Even if a configuration cannot reach all others internally, it may still have a way out via an outgoing transition. PWSEditor checks:

- **Triggered transitions**: If the configuration satisfies a guard, it can leave when the trigger event occurs
- **Autonomous PWS transitions**: If the configuration can reach an exit zone that matches an outgoing autonomous transition's guard

Only configurations that fail BOTH the internal reachability AND the transition coverage checks are marked as true deadlocks.

### Visual Indicators

In the state semantics annotation dashboard:

| Visual | Meaning |
|--------|---------|
| **Red** | True deadlock: configuration cannot evolve AND is not covered by any transition |
| **Green** | Covered: configuration has a way out via an outgoing transition |
| **Green underline** | Configuration can evolve internally (not a deadlock risk) |
1. If `(m1.A, m2.X)` can evolve to `(m1.B, m2.Y)` via autonomous transitions → **OK** (green underline)
2. If `(m1.A, m2.X)` cannot reach `(m1.B, m2.Y)` but is covered by transition guard `[m1.A]` → **OK** (green)
3. If `(m1.A, m2.X)` cannot reach `(m1.B, m2.Y)` AND no transition covers it → **DEADLOCK** (red)

### Resolving Deadlocks

When you see red underlined configurations, consider these solutions:

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

**Exit zones** are a fundamental concept in PWS that identify points where an autonomous transition in a component machine would take the system **outside** of the current state's allowed configurations. They represent "boundary conditions" where the controller must react.

### What is an Exit Zone?

An exit zone occurs when:

1. A component machine has an **autonomous transition** (self-triggering, no external event required)
2. The transition's **source state** is compatible with the current state's semantics
3. The transition's **target state** would take the system **outside** the current semantics

In other words, an exit zone marks a configuration where firing the autonomous transition would violate the state's constraints—the system would "exit" the allowed space.

### Exit Zone Structure

Each exit zone records:
- **Machine ID**: Which component machine contains the transition
- **Transition**: The specific autonomous transition
- **Source**: The configuration condition that enables the transition
- **Target**: The resulting configuration after the transition fires

### How Exit Zones Are Computed

For each autonomous transition in the assembly's component machines:

```
1. Create a partial configuration for the source state: (machineId.sourceState)
2. Intersect with the state's semantics
3. If intersection is NON-EMPTY (source is reachable):
   a. Create a partial configuration for the target state: (machineId.targetState)
   b. Intersect with the state's semantics
   c. If intersection is EMPTY (target is outside):
      → EXIT ZONE detected!
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

### Exit Zones and Controller Design

Exit zones serve two purposes:

1. **Reactive Transitions**: Exit zones enable autonomous PWS-level transitions. When an exit zone's target matches a PWS transition's guard, that transition can fire.

2. **Coverage Analysis**: The annotation dashboard shows which exit zones are "covered" by outgoing transitions (green) versus uncovered (red). Uncovered exit zones may indicate missing transitions in your controller design.

### Exit Zone Colors in the Dashboard

The dashboard uses a simple two-color scheme for exit zones:

| Color | Meaning |
|-------|---------|  
| **Green** | Exit zone is covered by an autonomous PWS transition ✓ |
| **Red** | Exit zone is NOT covered by any autonomous PWS transition ✗ |

**Note**: For detailed analysis including exit zone origin (CS-only, SS-only, or both), right-click on the state dashboard and select **"Show Extended Details..."** to open a comprehensive analysis window.

2. **Precise Control**: Use fully-specified configurations when you need exact control over which component states are allowed. This generates exit zones for any transition that would leave those exact configurations.

3. **Incremental Refinement**: Start with partial constraints to focus on key machines, then add more constraints as needed based on the exit zones that appear.

---

## File Management

### Document Model

PWSEditor uses a document-based model with the following features:

- **Single document at a time**: One PWS workspace per editor window
- **Dirty tracking**: Unsaved changes are tracked and indicated in the window title
- **Annotation persistence**: Guard, action, and semantics annotation positions are saved with the document

### Window Title

The window title shows the current document status:

```
PWSEditor : filename    (saved document)
PWSEditor : filename *  (unsaved changes)
PWSEditor : Untitled    (new unsaved document)
```

### Creating a New Document

1. Go to **File → New**
2. If there are unsaved changes, you'll be prompted to save
3. A fresh empty workspace is created

### Opening a Document

1. Go to **File → Open...**
2. Select a `.pws` file (PWS Workspace format)
3. The document loads with all states, transitions, annotations, and assembly

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
2. If there are unsaved changes, you'll be prompted to save
3. The editor returns to an empty state

### File Format

PWSEditor saves documents in `.pws` format, which includes:
- The PWS controller state machine
- All component machines in the assembly
- The machine library
- Annotation positions (guards, actions, semantics dashboards)

### Legacy Format Support

PWSEditor can also load legacy `.bin` files from earlier versions. The library can be saved/loaded separately as `.mlib` files.

### Exporting

**Export as PDF:**
1. Go to **File → Export as PDF**
2. Choose location and filename
3. A PDF rendering of the current diagram is created

**Export as SVG:**
1. Go to **File → Export as SVG**
2. Choose location and filename
3. An SVG vector image of the diagram is created

---

## Menu Reference

### File Menu

| Option | Description |
|--------|-------------|
| **New** | Create a new empty PWS workspace |
| **Open...** | Open an existing `.pws` workspace file |
| **Save** | Save current document (prompts for location if new) |
| **Save As...** | Save current document to a new location |
| **Close** | Close current document (prompts to save if dirty) |
| **Save Library...** | Save machine library only to `.mlib` file |
| **Load Library...** | Load machine library from `.mlib` file |
| **Export as PDF** | Export current diagram as PDF document |
| **Export as SVG** | Export current diagram as SVG vector image |
| **Exit** | Close the editor |

### Edit Menu

| Option | Description |
|--------|-------------|
| **Add State** | Add a new state to the controller |
| **Add Transition** | Create a transition between states |
| **Add initial transition** | Add transition from pseudo-state |
| **Delete Selected** | Remove selected state/transition |
| **Rename Selected** | Change name of selected state |
| **Edit Constraints** | Define semantic constraints for a state |
| **Recalculate Semantics** | Force recalculation of all state semantics |

### View Menu

| Option | Description |
|--------|-------------|
| **Edit Mode** | Toggle between edit and view modes |
| **Show State Annotations** | Toggle display of semantic annotations |
| **Show Grid** | Toggle grid display |
| **Snap to Grid** | Toggle automatic grid snapping |
| **Grid Size** | Adjust snap-to-grid size |

### LTL Menu

| Option | Description |
|--------|-------------|
| **LTL Editor** | Open the LTL formula editor |
| **Check LTL Now** | Run LTL verification on current model |

### Assembly Menu

| Option | Description |
|--------|-------------|
| **Add Machine** | Add new machine to assembly |
| **Remove Machine** | Remove selected machine from assembly |
| **Clone Machine** | Duplicate a machine with new ID |

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
| **TRUE on Autonomous** | Fires immediately upon entering source state | Red guard label `[TRUE]` |
| **Orphan Guard** | References an exit zone that no longer exists | Red guard label |

**How to Fix:**
- **FALSE**: Edit the guard to specify a meaningful condition (e.g., `m1.Failed`)
- **TRUE on Autonomous**: Either add a trigger event or change to a specific guard
- **Orphan**: Update the guard to reference exit zones that still exist

#### Action Problems (Orphan Actions)
Lists actions that reference events not reachable from the source state's semantics or constraints. An action is "orphan" when:
- The machine it references is not included in the source state's semantics
- The event (trigger) it references cannot be fired from any state in the source semantics

Orphan actions are shown in **red** on the diagram.

**How to Fix:** 
- Remove the orphan action from the transition
- Or update the source state's constraints to include machine states that enable the referenced events

#### Uncovered Exit Zones
Lists exit zones that have no covering autonomous transition. Each exit zone should ideally be handled by an autonomous transition with a matching guard.

**How to Fix:** Add autonomous transitions with guards matching the listed exit zones.

#### Constraint Violations
Lists configurations that appear in computed state semantics but violate user-defined constraints. These are also shown in **red** in state dashboards.

**How to Fix:** Review and adjust either the constraints or the transition structure.

#### Deadlock Configurations
Lists configurations where the system can get stuck — no autonomous evolution and no covering transition. These are warnings that may indicate design issues.

**How to Fix:** Add transitions that can fire from these configurations, or verify that the deadlock is intentional (e.g., final/sink states).

#### LTL Formula Verification
*(Coming soon)* Once LTL verification is implemented, this section will show which formulas are satisfied and which are violated.

#### Overall Status
Shows a final assessment: either **"CONTROLLER IS WELL-FORMED"** (no issues) or a summary of outstanding problems.

### Report and Diagram Correlation

The report correlates with visual indicators on the diagram:
- **Red guard labels**: Problematic guards (FALSE, TRUE on autonomous, orphan)
- **Red action labels**: Orphan actions (not reachable from source state semantics)
- **Red configurations in dashboards**: Constraint violations
- **Uncovered exit zones**: No visual indicator on transitions, but shown in state dashboards

Use the report to get a comprehensive overview, then use the diagram to locate and fix individual issues.

---

## Tips & Troubleshooting

### General Tips

1. **Save frequently**: Use **File → Save** regularly to avoid losing work
2. **Watch the title bar**: An asterisk (*) indicates unsaved changes
3. **Use the library**: Build reusable machine templates to speed up future designs
4. **Name clearly**: Use descriptive names for states and machines for clarity
5. **Align visually**: Use grid snapping and arrow keys to keep diagrams organized
6. **Export documentation**: Use PDF/SVG export to document your designs
7. **Monitor deadlocks**: Pay attention to red underlined configurations—they indicate potential issues

### Understanding Visual Feedback

#### State Annotation Colors

| Visual Element | Meaning |
|----------------|---------|
| Blue text (top row) | Constraint semantics you defined |
| Green text | Configuration satisfies constraints |
| Red text | Configuration violates constraints |
| Red | True deadlock (cannot evolve, not covered) |
| Green | Covered by outgoing transition |
| Green underline | Can evolve internally (not a deadlock risk) |
| Color | Meaning |
|-------|---------|
| **Green** | Exit zone is covered by an autonomous transition ✓ |
| **Red** | Exit zone is NOT covered (needs attention) ✗ |

**Tip**: Right-click on the dashboard and select **"Show Extended Details..."** for detailed exit zone origin analysis.
- Drag annotations to reorganize (they snap to grid)

#### "Red underlined configurations appear"
- **Cause**: Deadlock configurations that cannot evolve and aren't covered by transitions
- **Solution**: 
  - Add autonomous transitions in component machines
  - Add PWS transitions with guards covering the deadlock
  - Review if the configuration should be excluded via constraints

#### "State has red border"
- **Cause**: One or more issues detected:
  - Configurations violate constraints
  - Exit zones not covered by transitions
  - Deadlock configurations present
- **Solution**: Check each row of the annotation for red items and address them

#### "Pseudo-state was deleted"
- **Solution**: The pseudo-state is essential; it will be restored when you reload
- Add a fresh transition from the recreated pseudo-state

#### "Library didn't load"
- **Solution**: Ensure the `.mlib` file is valid and matches the current assembly format
- Check that machine IDs in the library don't conflict with assembly machine IDs

#### "File won't save"
- **Solution**: Check write permissions in the target directory
- Ensure the filename doesn't contain invalid characters
- Try saving to a different location

#### "States overlap when editing"
- **Solution**: Enable **Snap to Grid** from **View** menu
- Use arrow keys to nudge selected states for fine positioning

#### "Semantics not updating"
- **Solution**: Try **Edit → Recalculate Semantics** to force recalculation
- Check that all component machines are properly configured
- Verify transitions have correct guards

### Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| **Arrow Keys** | Move selected state |
| **Delete** | Delete selected state/transition |
| **Double-click state** | Rename state |
| **Right-click** | Context menu |
| **Escape** | Cancel current operation |

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
- **Action annotation**: Shows actions `{action1, action2}`
- **Semantics annotation**: Shows computed transition semantics

Toggle visibility via the transition's right-click menu.

---

## Workflow Example: Building a Traffic Light Controller

Here's a step-by-step example to get started:

### Step 1: Create the Controller

1. Open PWSEditor
2. Right-click the canvas and select **Add State**
3. Create three states: `Red`, `Yellow`, `Green`
4. Add transitions:
   - `Red` → `Green`
   - `Green` → `Yellow`
   - `Yellow` → `Red`

### Step 2: Add Timing Semantics

1. Right-click the `Red` state
2. Select **Edit Constraints**
3. Enter: `timer.idle, light.red` (assuming machines named `timer` and `light`)
4. Repeat for other states

### Step 3: Create Component Machines

1. Click the **Assembly** tab
2. Click **Add** to create a new machine
3. Name it `timer` (with ID `timer`)
4. Create two states: `idle`, `running`
5. Save to the library

### Step 4: Add Another Machine

1. Click **Add** again
2. Create machine `light` with states: `red`, `yellow`, `green`
3. Save to the library

### Step 5: Test and Save

1. Go to **File → Save All**
2. Name your project `traffic_light.bin`
3. Go to **File → Export as SVG** to generate a diagram

---

## Additional Resources

For more information about Part-Whole Statecharts theory, see the project README.md or documentation at your institution's research resources.

---

## Recent Changes & Version History

### Version 3.0 Updates

#### New File Management System
- **Document-based workflow**: New/Open/Save/Save As/Close operations
- **Dirty tracking**: Unsaved changes indicated by asterisk (*) in window title
- **Annotation persistence**: Guard, action, and semantics annotation positions are saved and restored
- **New file format**: `.pws` extension for workspace files (backward compatible with `.bin`)

#### Enhanced Deadlock Detection
- **Refined logic**: Configurations are only marked as deadlocks if they cannot evolve AND are not covered by any outgoing transition
- **Visual indicators**: Red for true deadlocks, green for covered configurations, green underline for configurations that can evolve internally
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
- Export diagrams as PDF documents for documentation and sharing

#### UI Improvements
- Window title shows document name and dirty status
- Improved annotation positioning on file load
- Better grid snapping for annotations

---

**Questions or suggestions?** Reach out to the development team or check the project repository for updates.

**Happy modeling!**
