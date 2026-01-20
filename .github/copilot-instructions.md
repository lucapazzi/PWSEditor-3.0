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

# PWSEditor — Copilot Instructions (concise)

Purpose: help an AI coding agent become productive quickly in this repo.

**Quick Start (build & run)**

- Compile: `javac -d out -cp "lib/pdfbox-2.0.29.jar:lib/fontbox-2.0.29.jar:lib/commons-logging-1.2.jar" -sourcepath src src/pws/editor/PWSEditor.java`
- Run main editor: `java -cp out pws.editor.PWSEditor`
- Legacy demo: `java -cp out editor.Main`
- Javadoc: use `scripts/generate-javadoc.sh` (produces `docs/javadoc`)

**Big-picture architecture (4 layers)**

- Model: `machinery/` + `pws/` — core State/Transition/StateMachine and PWS extensions; `PWSStateMachine` wraps an `Assembly`.
- Symbolic algebra: `smalgebra/` — `SMProposition` tree, parsed by `SMExpressionParser` and used for guards/constraints.
- Semantics & analysis: `assembly/` + `pws/editor/semantics/` — `Configuration`, `Semantics` (normalized set; use `addConfiguration()`), and `SemanticsVisitor` fixed-point propagation.
- UI: `editor/`, `pws/editor/` — Swing panels (`PWSEditor`, `PWSStateMachinePanel`, annotation widgets).

**Project-specific conventions / gotchas**

- Interface suffix: use `*Interface.java` for contracts (e.g., `StateInterface`).
- UI-only fields are `transient`; annotations are recreated at render time (see `PWSStateMachinePanel`).
- Serialization: add `serialVersionUID = 1L` to model classes when changing serialized fields.
- Semantics normalization: always use `Semantics.addConfiguration()` (it removes subsumed configs); do not filter raw collections.
- When adding a machine from `MachineLibrary`, clone it: `assembly.addStateMachine(id, library.get(key).clone())` to avoid shared mutable state.

**Key integration points**

- Persistence: `serializer/BinaryModelSerializer.java` — writes model + library as Java objects; maintain backward compatibility when changing formats.
- Assembly generation: `assembly/AssemblyGenerator.java` expands templates for truth-table / LTL analysis — changes here affect semantics computation broadly.
- Propositions: `smalgebra/SMExpressionParser.java` and `SMProposition` implementations are central when editing guards/constraints.

**Developer workflows & commands**

- Build once-source: run the `javac` command above (workspace `tasks.json` has a `compile` task labeled `compile`).
- Run the app: use `java -cp out pws.editor.PWSEditor` after compiling.
- Tests: tests under `test/` are plain Java smoke tests; compile them into `out` and run via `java`.

**Files to inspect for most tasks**

- UI / editor: `src/pws/editor/PWSEditor.java`, `src/pws/editor/PWSStateMachinePanel.java`, `src/editor/StateMachinePanel.java`
- Semantics and assembly: `src/pws/`, `src/assembly/AssemblyGenerator.java`, `src/pws/editor/semantics/Semantics.java`
- Symbolic algebra: `src/smalgebra/SMExpressionParser.java`, `src/smalgebra/BasicStateProposition.java`
- Persistence: `src/serializer/BinaryModelSerializer.java`, `src/test/SaveLoadSmokeTest.java`

If anything above is unclear or you want more examples (e.g., a small PR-ready checklist for changing serialized fields or adding a new proposition type), tell me which area to expand and I’ll update this file.
   - `Semantics.addConfiguration()` adds configurations incrementally, auto-normalizing
