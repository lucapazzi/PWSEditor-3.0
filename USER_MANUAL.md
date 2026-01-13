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
9. [Saving & Loading](#saving--loading)
10. [Menu Reference](#menu-reference)
11. [Tips & Troubleshooting](#tips--troubleshooting)

---

## Getting Started

### Installation

PWSEditor is a Java application. Ensure you have **Java 11 or later** installed on your system.

### Running PWSEditor

From the command line, navigate to the PWSEditor directory and run:

```bash
javac -d out $(find src -name '*.java')
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
- **Constraint**: Rules you defined
- **Computed**: Derived semantics based on structure
- **Violations**: Misaligned configurations highlighted in red
- **Reactive Space**: Transitions enabled from this state

---

## Saving & Loading

### Saving a Project

1. Go to **File → Save All**
2. Choose a location and filename
3. The entire PWS model (controller + assembly + library) is saved as a `.bin` file

### Loading a Project

1. Go to **File → Load All**
2. Select a previously saved `.bin` file
3. The controller, assembly, and library are restored

### Auto-Naming

When you save, files are named based on the machine name:
- Default: `PWSEditor_<timestamp>.bin`
- Customizable by renaming the file

### Saving Just the Library

1. Go to **File → Save Library...**
2. Choose location and name (`.mlib` extension recommended)
3. The library is saved separately

### Exporting as SVG

To create a graphic representation of your state machine:

1. Go to **File → Export as SVG**
2. Choose the export format and location
3. An SVG image is created showing the current diagram

Use this for documentation, presentations, or publishing.

---

## Menu Reference

### File Menu

| Option | Description |
|--------|-------------|
| **Save All** | Save controller + assembly + library to `.bin` file |
| **Load All** | Load controller + assembly + library from `.bin` file |
| **Save Library...** | Save library only to `.mlib` file |
| **Load Library...** | Load library from `.mlib` file |
| **Export as SVG** | Export current diagram as SVG image |
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

### View Menu

| Option | Description |
|--------|-------------|
| **Show State Annotations** | Toggle display of semantic annotations |
| **Grid Size** | Adjust snap-to-grid size |
| **Zoom In / Zoom Out** | Adjust diagram magnification |
| **Fit to Window** | Auto-fit diagram to current window |

### Assembly Menu

| Option | Description |
|--------|-------------|
| **Add Machine** | Add new machine to assembly |
| **Remove Machine** | Remove selected machine from assembly |
| **Clone Machine** | Duplicate a machine with new ID |

---

## Tips & Troubleshooting

### General Tips

1. **Save frequently**: Use **File → Save All** regularly to avoid losing work
2. **Use the library**: Build reusable machine templates to speed up future designs
3. **Name clearly**: Use descriptive names for states and machines for clarity
4. **Align visually**: Use grid snapping and arrow keys to keep diagrams organized
5. **Export documentation**: Use SVG export to document your designs

### Common Issues

#### "Transition won't appear"
- **Solution**: Ensure you clicked the exact target state, not empty space
- Try creating the transition via the **Edit → Add Transition** menu instead

#### "Annotations are cluttered"
- **Solution**: Use **View → Show State Annotations** to hide them temporarily
- Drag annotations to reorganize (they snap to grid)

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
- **Solution**: Enable **Grid Snapping** from **View** menu
- Use arrow keys to nudge selected states for fine positioning

### Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| **Arrow Keys** | Move selected state |
| **Delete** | Delete selected state/transition |
| **Ctrl+S** | Quick save (if supported) |
| **Escape** | Deselect current selection |

### Performance Tips

- **Large assemblies**: For systems with many machines, consider breaking into smaller assemblies
- **Complex guards**: Keep guard conditions simple and readable
- **Annotations**: Disable annotations display for large diagrams to improve responsiveness

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

**Questions or suggestions?** Reach out to the development team or check the project repository for updates.

**Happy modeling!**
