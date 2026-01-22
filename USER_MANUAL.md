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
10. [File Management](#file-management)
11. [Menu Reference](#menu-reference)
12. [Tips & Troubleshooting](#tips--troubleshooting)

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
| **Guard** | A boolean condition that must be true to enable a transition |
| **Action** | An emission (event output) that occurs when a transition fires |
| **Constraint Semantics** | User-specified allowed configurations for a state |
| **Computed Semantics** | Semantics inferred from state machine structure |
| **Assembly** | A collection of component machines forming a part-whole hierarchy |
| **Machine Library** | A repository of reusable state machine templates |
| **Exit Zone** | An autonomous transition point in a component machine that enables PWS-level transitions |
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

### Autonomous Transitions

A transition can be marked as **autonomous** (self-triggering) to evolve without external events. This is useful for modeling fail-safe repair or guard-only transitions.

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

1. **Right-click** a state
2. Select **Edit Constraints** (or **View → Edit Constraints** from the menu)
3. In the dialog, enter configurations in the format:
   ```
   machine1.state1, machine2.state2
   machine1.state3, machine2.state4
   ```
4. Each line represents one allowed configuration (a conjunction)
5. Multiple lines create a disjunction (OR)

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
| **Green underline** | Configuration covered by an outgoing transition |
| **Red double underline** | Deadlock configuration (see [Deadlock Detection](#deadlock-detection)) |
| **Green border** | State is well-formed (all checks pass) |
| **Red border** | State has issues (constraint violations, uncovered exit zones, or deadlocks) |

---

## Deadlock Detection

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
| **Red double underline** | True deadlock: configuration cannot evolve AND is not covered by any transition |
| **Green underline** | Covered: configuration has a way out via an outgoing transition |
| **No underline** | Configuration can evolve internally (not a deadlock risk) |
| **Red border** | State contains at least one true deadlock configuration |

### Example Scenario

Consider a state with computed semantics `{(m1.A, m2.X), (m1.B, m2.Y)}`:

1. If `(m1.A, m2.X)` can evolve to `(m1.B, m2.Y)` via autonomous transitions → **OK**
2. If `(m1.A, m2.X)` cannot reach `(m1.B, m2.Y)` but is covered by transition guard `[m1.A]` → **OK** (green underline)
3. If `(m1.A, m2.X)` cannot reach `(m1.B, m2.Y)` AND no transition covers it → **DEADLOCK** (red double underline)

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
| Green underline | Configuration covered by outgoing transition |
| Red double underline | Deadlock configuration (needs attention) |
| Green border | State is well-formed |
| Red border | State has issues to resolve |

#### Exit Zone Colors

| Color | Meaning |
|-------|---------|
| Green | Exit zone is covered by an autonomous transition |
| Dark yellow | Exit zone only in constraint semantics (CS-only) |
| Light red | Exit zone only in state semantics (SS-only) |
| Dark red bold | Exit zone in both CS and SS but not covered |

### Common Issues

#### "Transition won't appear"
- **Solution**: Ensure you clicked the exact target state, not empty space
- Try creating the transition via the **Edit → Add Transition** menu instead

#### "Annotations are cluttered"
- **Solution**: Use **View → Show State Annotations** to hide them temporarily
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
- **Visual indicators**: Red double underline for true deadlocks, green underline for covered configurations
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
