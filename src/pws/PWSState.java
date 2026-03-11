package pws;

import assembly.Assembly;
import machinery.State;
import pws.editor.annotation.StateSemanticsAnnotation;
import pws.editor.semantics.Configuration;
import pws.editor.semantics.ExitZone;
import pws.editor.semantics.Semantics;
import smalgebra.BasicStateProposition;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** PWS-specific state with semantics, constraints, and UI annotation. */
public class PWSState extends State {
    private static final long serialVersionUID = 1L;
    private transient StateSemanticsAnnotation annotation;
    private boolean annotationVisible = false; // Di default nascosta.
    private boolean annotationMinimized = false; // Whether the dashboard is minimized (small square)
    // State semantics
    private Semantics stateSemantics;
    // Constraints semantics.
    private Semantics constraintsSemantics;
    // Reactive semantics.
    private HashSet<ExitZone> reactiveSemantics;
    // Exit zones from Constraint Semantics only (drawn blue)
    private HashSet<ExitZone> csOnlyExitZones = new HashSet<>();
    // Exit zones from State Semantics only (drawn red)
    private HashSet<ExitZone> ssOnlyExitZones = new HashSet<>();
    // Fail state flag (no exit-zone coverage required)
    private boolean failState = false;
    // Stores the raw constraint text entered by the user
    private String rawConstraintText;
    // Cached deadlock configurations (computed during semantics recalculation)
    private transient Set<Configuration> deadlockConfigurations = new HashSet<>();
    // Provenance for configurations derived by internal exit-zone closure.
    private transient Map<String, LinkedHashSet<String>> internalClosureDerivations = new LinkedHashMap<>();

    public PWSState(String name, Point position, Assembly assembly) {
        super(name, position);
        stateSemantics = new Semantics(assembly.getAssemblyId());
        // New states default to ANY constraints (top semantics).
        constraintsSemantics = Semantics.top(assembly);
        rawConstraintText = "ANY";
        reactiveSemantics = new HashSet<ExitZone>();
    }

    // Getters and setters for constraints semantics.
    public Semantics getConstraintsSemantics() {
        return constraintsSemantics;
    }

    public void setConstraintsSemantics(Semantics constraintsSemantics) {
        this.constraintsSemantics = constraintsSemantics;
        if (annotation != null) {
            annotation.setContent(this); // Updated to use 'this'
            annotation.repaint();
        }
    }

    // Getters and setters for autonomous semantics.
    public HashSet<ExitZone> getReactiveSemantics() {
        return reactiveSemantics;
    }

    public void setReactiveSemantics(HashSet<ExitZone> reactiveSemantics) {
        this.reactiveSemantics = reactiveSemantics;
        if (annotation != null) {
            annotation.setContent(this); // Updated to use 'this'
            annotation.repaint();
        }
    }

    /** Returns exit zones that appear only in Constraint Semantics (drawn blue). */
    public HashSet<ExitZone> getCsOnlyExitZones() {
        return csOnlyExitZones;
    }

    /** Sets exit zones that appear only in Constraint Semantics. */
    public void setCsOnlyExitZones(HashSet<ExitZone> csOnlyExitZones) {
        this.csOnlyExitZones = csOnlyExitZones;
    }

    /** Returns exit zones that appear only in State Semantics (drawn red). */
    public HashSet<ExitZone> getSsOnlyExitZones() {
        return ssOnlyExitZones;
    }

    /** Sets exit zones that appear only in State Semantics. */
    public void setSsOnlyExitZones(HashSet<ExitZone> ssOnlyExitZones) {
        this.ssOnlyExitZones = ssOnlyExitZones;
    }

    public StateSemanticsAnnotation getAnnotation() {
        return annotation;
    }

    public void setAnnotation(StateSemanticsAnnotation annotation) {
        this.annotation = annotation;
    }

    public boolean isAnnotationVisible() {
        return annotationVisible;
    }

    public void setAnnotationVisible(boolean visible) {
        this.annotationVisible = visible;
        if (annotation != null) {
            annotation.setVisible(visible);
        }
    }

    /** Returns whether the dashboard is minimized. */
    public boolean isAnnotationMinimized() {
        return annotationMinimized;
    }

    /** Sets whether the dashboard is minimized. */
    public void setAnnotationMinimized(boolean minimized) {
        this.annotationMinimized = minimized;
        if (annotation != null) {
            annotation.setMinimized(minimized);
        }
    }

    /** Returns whether this state is marked as a fail state. */
    public boolean isFailState() {
        return failState;
    }

    /** Sets whether this state is marked as a fail state. */
    public void setFailState(boolean failState) {
        this.failState = failState;
        if (annotation != null) {
            annotation.repaint();
        }
    }

    public Semantics getStateSemantics() {
        return stateSemantics;
    }

    // Method to uniquely identify the pseudo-state
    public boolean isPseudoState() {
        // Ensure the pseudo-state name is exactly "PseudoState"
        return "PseudoState".equals(getName());
    }

    public void setStateSemantics(Semantics stateSemantics) {
        this.stateSemantics = stateSemantics;
        if (annotation != null) {
            annotation.setContent(this); // Updated to use 'this'
            annotation.repaint();
        }
    }

    /** Sets the raw constraint text for this state (compact form). */
    public void setRawConstraintText(String text) {
        this.rawConstraintText = text;
    }

    /** Returns the raw constraint text, or null if none was set. */
    public String getRawConstraintText() {
        return rawConstraintText;
    }

    /** Returns cached deadlock configurations for this state. */
    public Set<Configuration> getDeadlockConfigurations() {
        return deadlockConfigurations != null ? deadlockConfigurations : new HashSet<>();
    }

    /** Sets the cached deadlock configurations (called during semantics recalculation). */
    public void setDeadlockConfigurations(Set<Configuration> deadlocks) {
        this.deadlockConfigurations = deadlocks != null ? deadlocks : new HashSet<>();
    }

    /** Clears provenance for configurations derived via internal exit-zone closure. */
    public void clearInternalClosureDerivations() {
        getInternalClosureDerivations().clear();
    }

    /** Records one derivation detail for a configuration added by internal exit-zone closure. */
    public void addInternalClosureDerivation(Configuration cfg, String detail) {
        if (cfg == null || detail == null || detail.isBlank()) {
            return;
        }
        getInternalClosureDerivations()
                .computeIfAbsent(cfg.toString(), k -> new LinkedHashSet<>())
                .add(detail);
    }

    /** Returns whether the configuration was derived via internal exit-zone closure. */
    public boolean isDerivedByInternalClosure(Configuration cfg) {
        if (cfg == null) return false;
        return getInternalClosureDerivations().containsKey(cfg.toString());
    }

    /** Returns derivation details for a configuration added by internal exit-zone closure. */
    public List<String> getInternalClosureDerivationDetails(Configuration cfg) {
        if (cfg == null) return List.of();
        LinkedHashSet<String> details = getInternalClosureDerivations().get(cfg.toString());
        if (details == null || details.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(details);
    }

    /** Returns all cached derivation details keyed by configuration string. */
    public Map<String, LinkedHashSet<String>> getInternalClosureDerivations() {
        if (internalClosureDerivations == null) {
            internalClosureDerivations = new LinkedHashMap<>();
        }
        return internalClosureDerivations;
    }
}
