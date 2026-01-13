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

- **Event- and guard-triggered transitions:**  
  Supports event-triggered transitions with guard predicates and action
  emissions, as well as guard-only transitions for autonomous evolution and
  fail-safe repair.

- **Editable guards and actions:**  
  Triggers, guards, and emissions can be edited via in-place annotation
  widgets.

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
