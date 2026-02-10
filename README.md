# PWSEditor: A Part-Whole Statecharts Editing Environment

**PWSEditor** is a graphical environment for designing and analysing
*Part-Whole Statecharts (PWS)*—a behavioural modelling formalism for
hierarchical assemblies of components interacting both synchronously and
asynchronously.  The editor supports the construction of PWS controllers,
the specification of state-level constraints, and the visualisation of
computed semantics and reactive spaces.

## Features

- **Graphical control-state editor:**  
  Create and arrange control states, initial pseudo-states, and transitions
  using an intuitive drag-and-drop interface.

- **Semantic annotation:**  
  For each control state, PWSEditor computes and displays its declared
  *constraint* (\Sem), its *computed semantics* (\Comp), and its *reactive
  space*.  Semantic violations (misaligned configurations) and uncovered
  reactive successors are highlighted.

- **State status cues:**  
  The state dashboard highlights issues with a red banner plus
  contextual detail (constraint violations, uncovered exit zones, true
  deadlocks) so you always know whether the state is well-formed.  Hovering
  or opening the Extended Dashboard reveals the same list of issues for
  quick diagnostics.

- **Event- and guard-triggered transitions:**  
  Supports event-triggered transitions with guard predicates and action
  emissions, as well as guard-only transitions for autonomous evolution and
  fail-safe repair. Transitions can be created directly by drag gestures
  (`Cmd/Ctrl + drag` for guard-triggered, `Cmd/Ctrl + Shift + drag` for
  event-triggered with default event `ev`, and `Cmd/Ctrl + drag` from the
  pseudo-state/aliases for initial `_init` transitions), with context-menu
  link mode still available as an alternative.

- **Editable guards and actions:**  
  Triggers, guards, and emissions can be edited via in-place annotation
  widgets. Newly created triggered transitions start with guard labels hidden,
  while initial transitions start with guard labels visible (and actions hidden by default), and new states
  start with `ANY` constraints.

- **Separation of model and view:**  
  PWS statecharts and their visual layout can be saved, restored, and
  selectively shown or hidden.

- **Path toward the full PWS toolchain:**  
  Future releases will integrate with model-checking, analysis, and
  code-generation tools for PWS-based controller synthesis.

## Building and Running

This repository contains the Java Swing implementation of the editor.
It currently has no Maven/Gradle build script; compilation can be performed
manually:

```sh
javac -d out $(find src -name '*.java')
```

## Generating Javadoc

You can generate API documentation with the helper script:

```sh
scripts/generate-javadoc.sh
```

The output is written to `docs/javadoc/index.html`.

## Building Locally

The `scripts/build.sh` helper compiles the editor against the bundled libraries (PDFBox, FontBox, pdfbox-graphics2d, etc.) and mirrors the manual `javac` command:

```sh
./scripts/build.sh
```

It cleans `out/` before compiling, so rerun when dependencies or sources change. You can then run the editor with `java -cp out:...`.

If you want a single entry point that builds all artifacts available on your current OS, use:

```sh
./scripts/build-all.sh
```

Optional flags:
- `--javadoc` to also generate API docs
- `--windows-installer` to build a Windows installer when run on Windows

## Building an executable jar

To generate a runnable jar with its runtime dependencies, use:

```sh
./scripts/build-jar.sh
```

This produces `dist/PWSEditor.jar` plus the dependency jars in `dist/lib/`. Run it with:

```sh
java -jar dist/PWSEditor.jar
```

## macOS desktop launcher

To generate a macOS `.app` you can double-click:

```sh
./scripts/build-macos-app.sh
```

It produces `dist/PWSEditor.app`. Drag it to your Desktop or Applications, then launch it normally.

## Windows 10/11 desktop launcher (Intel x64)

On Windows, build a package with an executable launcher (`PWSEditor.exe`) using:

```sh
./scripts/build-windows-app.sh
```

Prerequisites:
- Windows 10/11 x64 (Intel/AMD)
- JDK 17+ with `jpackage` available in `PATH`
- Run from Git Bash, MSYS2, or Cygwin

Output:
- App image in `dist/PWSEditor/`
- Launcher executable `dist/PWSEditor/PWSEditor.exe`

Optional installer build:

```sh
./scripts/build-windows-app.sh --installer
```
