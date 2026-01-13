<!-- Copilot instructions for AI coding agents in this repo -->
# PWSEditor — Copilot Instructions

Help AI contributors become productive in this Part-Whole Statecharts (PWS) editing environment.

## Quick Start

**No build system** — manual compilation and execution:

```sh
# Compile all sources
javac -d out $(find src -name '*.java')

# Run the main editor
java -cp out pws.editor.PWSEditor

# Run demo editor (legacy entry point)
java -cp out editor.Main
```

## Architecture & Big Picture

**Four-layer design:**

1. **Model Layer** (`machinery/`, `pws/`)
   - `machinery/{State,Transition,StateMachine}.java` — base statechart model (generic)
   - `pws/{PWSState,PWSTransition,PWSStateMachine}.java` — PWS-specific model extending base classes
   - `PWSStateMachine` wraps an `Assembly` (collection of state machines) for specifying part–whole hierarchies
   - Each `PWSState` holds three semantics: `stateSemantics` (computed), `constraintsSemantics` (user-defined), and `reactiveSemantics` (exit zones)

2. **Symbolic Algebra & Propositions** (`smalgebra/`)
   - `SMProposition` — interface for symbolic state expressions (guards, constraints, semantics)
   - Concrete implementations: `BasicStateProposition` (e.g., `m1.S1`), `AndProposition`, `OrProposition`, `NotProposition`, `TrueProposition`, `FalseProposition`
   - `SMExpressionParser` — parses user constraint strings into `SMProposition` trees
   - Propositions support `evaluate()`, `transform()`, `ontoImplies()`, and `toConf()` for semantic analysis

3. **Semantics & Analysis** (`pws/editor/semantics/`, `assembly/`)
   - `Configuration` — immutable map from machine IDs to state sets, with entailment checking via `implies()`
   - `Semantics` — normalized set of configurations with auto-deduplication; `addConfiguration()` removes subsumed configurations
   - `SemanticsVisitor` — fixed-point computation: traverses transitions to propagate semantics from sources to targets
   - `ExitZone` — captures reactive (autonomous) transitions: pairs a configuration with an action list
   - `AssemblyGenerator` — generates all possible `Assembly` instances from a template for truth-table and LTL analysis
   - `LTLFormula`, `LTLParser`, `LTLAnalyzer` — experimental LTL model checking support

4. **UI & Visualization** (`editor/`, `pws/editor/`)
   - `PWSEditor` — main frame managing tabs (controller editor, assembly panel, machine library)
   - `PWSStateMachinePanel` — Swing canvas for graphical state and transition editing
   - `pws/editor/annotation/*` — interactive in-place editors for guards/actions/semantics labels
   - `PWSPanel` — assembly machine list with embedded state-machine editor for selected machines
   - `MachineLibraryPanel` — reusable machine repository UI

## Key Patterns

- **Explicit Model/View Separation:** Model logic lives in `machinery/` and `pws/`; UI in `pws/editor/` and `editor/`. Route model changes through setters on `PWSState`/`PWSTransition`; trigger UI repaints via panel callbacks.
- **Interface Suffix Convention:** All contracts use `*Interface.java` naming (`StateInterface`, `TransitionInterface`, `AssemblyInterface`). When adding new types, follow this pattern.
- **Annotation Widgets System:** `pws/editor/annotation/{Annotation,GuardAnnotation,ActionAnnotation,StateSemanticsAnnotation,TransitionSemanticsAnnotation}.java` extend `JComponent` and are draggable and snappable to grid. They hold references to model objects and update them on edit completion. Use `Consumer<T>` callbacks to notify model updates.
- **Transient UI State:** All annotation fields in model classes (e.g., `annotation` in `PWSState`) are marked `transient`; they are reconstructed by panels during rendering, not serialized. Always mark UI-only references as `transient`.
- **Semantics as Normalized Sets:** `Semantics.addConfiguration()` normalizes configurations (removes subsumed ones); configuration `implies()` checks entailment; `Semantics.bottom()` creates empty semantics. Never manually iterate and filter configurations—use `addConfiguration()` for automatic normalization.
- **Symbolic Propositions as Model Properties:** Guards, constraints, and actions use `SMProposition` objects. Parse user input via `SMExpressionParser.parse()`, evaluate via `proposition.evaluate(assembly)`, and convert to semantics via `proposition.toConf(assembly).toSemantics()`.
- **Assembly Cloning on Reuse:** When adding machines from `MachineLibrary` to an `Assembly`, always clone them: `assembly.addStateMachine(id, library.get(key).clone())`. Direct references cause cross-contamination.

## Data Flow

1. **Editing → Model:**
   - User edits state/transition in canvas → `PWSStateMachinePanel` intercepts mouse events → creates/updates `PWSState` or `PWSTransition` → triggers `repaint()`
   - In-place annotation editors (e.g., guard text field) call `transition.setGuardProposition()` directly

2. **Model → Semantics:**
   - `PWSStateMachine.getAssembly()` retrieves assembly; `AssemblyGenerator.generateAllAssemblies()` expands it into concrete configurations
   - `SemanticsVisitor` traverses assembly machines and computes `constraintsSemantics` (from user constraint) and `stateSemantics` (inferred) per state
   - `Semantics.addConfiguration()` adds configurations incrementally, auto-normalizing

3. **Rendering:**
   - `PWSStateMachinePanel.paintComponent()` calls `drawStateAnnotations()`, `drawTransitions()` to render states, transitions, and optional annotation overlays
   - Annotations are positioned relative to their content; dragging them updates model via `setLocation()`

## Critical Integration Points

- **Assembly ↔ Multiple Machines:** `Assembly.stateMachines` is a `LinkedHashMap` keyed by machine ID. `MachineLibrary` stores reusable `StateMachine` templates. When adding a machine from the library to the assembly, clone it.
- **Persistence:** `BinaryModelSerializer.saveModelAndLibrary(model, library, file)` writes a `PWSStateMachine` and its library as consecutive Java objects; load with `loadModelAndLibrary()` handling backward compatibility (older files lack library).
- **Menu Actions:** `PWSEditor` menu bar (File, Edit, View) routes through `PWSStateMachineEditor` (base editor wrapper). Guard your handlers with null checks on `baseEditor`, `embeddedEditor`, and `assemblyPanel`.

## Developer Workflows & Gotchas

- **Swing Thread:** Always update UI on the AWT event thread. If computing semantics off-thread, wrap UI callbacks in `SwingUtilities.invokeLater()`.
- **Serialization:** Add `serialVersionUID = 1L` to model classes to avoid deserialization failures after changes. Mark UI-only fields as `transient`. See `PWSStateMachine`, `LTLFormula`, `MachineLibrary` for examples.
- **Package-Scoped Visibility:** Most model and UI classes default to package scope. Avoid moving files across packages without updating import statements.
- **Annotation Lifecycle:** Annotations are created on-demand in `PWSStateMachinePanel` and attached to states/transitions; they are *not* serialized (marked `transient`). Recreate them during rendering if needed via `restoreVisibleStateAnnotations()`.
- **Assembly ID Consistency:** All `Semantics` and `Configuration` objects reference an assembly ID string. Verify ID matches when combining semantics across assembly boundaries—mismatch throws `IllegalArgumentException`.
- **Fixed-Point Semantics Computation:** `SemanticsVisitor.computeAllStateSemantics()` uses chaotic iteration with a worklist. Seed pseudostate with `assembly.calculateInitialStateSemantics()`, then propagate through transitions until no changes. Call `pwsStateMachine.recalculateSemantics()` to recompute after model changes.
- **Proposition Transformation:** Use `SMProposition.transform(machineId, fromState, toState, assembly)` to rename states in constraints. This recursively rewrites `BasicStateProposition` nodes matching the old state name.

## Where to Look for Examples

- `PWSEditor.java` — main frame layout, menu creation, save/load orchestration
- `PWSStateMachinePanel.java` — mouse handlers for state/transition creation, annotation rendering, grid snapping
- `pws/editor/annotation/{GuardAnnotation,ActionAnnotation}.java` — example annotation subclasses with custom painting and edit logic
- `Semantics.java` — configuration normalization and implication logic
- `Assembly.java` and `AssemblyGenerator.java` — assembly composition and instantiation patterns
- `SaveLoadSmokeTest.java` — canonical save/load example

## Common Tasks

- **Add a new model property to states/transitions:** Define the field in `PWSState`/`PWSTransition`, add getter/setter, update relevant annotation widgets (if UI-editable), and update serialization if needed.
- **Add a menu action:** Edit `PWSEditor.createMenuBar()`, register handler in appropriate listener, and call model/view methods.
- **Extend semantics computation:** Add logic to `SemanticsVisitor` or `Semantics` class; update `PWSStateMachinePanel` rendering if annotation display changes.
- **Refactor UI layout:** Preserve existing event handlers and model callbacks; only restructure `JPanel` composition and layout managers.
