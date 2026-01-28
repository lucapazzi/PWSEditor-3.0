package pws;

import pws.editor.semantics.ExitZone;
import smalgebra.TrueProposition;
import smalgebra.FalseProposition;
import smalgebra.AndProposition;
import smalgebra.OrProposition;
import smalgebra.NotProposition;
import assembly.Action;
import assembly.ActionList;
import assembly.Assembly;
import assembly.LTLFormula;
import machinery.*;
import pws.editor.semantics.Semantics;
import pws.editor.semantics.SemanticsVisitor;
import pws.editor.semantics.Configuration;
import smalgebra.BasicStateProposition;
import smalgebra.SMProposition;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** PWS state machine with assembly-level semantics helpers. */
public class PWSStateMachine extends StateMachine {
    // Field to hold the Assembly that belongs to this PWSStateMachine.
    private Assembly assembly;

    private static final long serialVersionUID = 1L;

    // Constructor that accepts a name.
    public PWSStateMachine(String name) {
        super(name);
        // Instantiate a default assembly.
        this.setAssembly(new Assembly("PWSEditorAssembly"));
        fixPseudoState();
    }

    // Getter for the assembly.
    public Assembly getAssembly() {
        return assembly;
    }

    // Setter for the assembly.
    public void setAssembly(Assembly assembly) {
        this.assembly = assembly;
    }

    /**
     * Private helper to replace the pseudo-state created by the base constructor with a PWSState.
     * The default object is removed and replaced with a PWSState instance.
     */
    private void fixPseudoState() {
        if (!states.isEmpty() && states.get(0).getName().equals("PseudoState")) {
            states.remove(0);
        }
        // Create the new pseudo-state as a PWSState
        PWSState pseudo = new PWSState("PseudoState", new Point(20, 20), this.assembly);
        this.pseudoState = pseudo;
        states.add(0, pseudo);
    }

    /**
     * Recalculates and applies the semantics for all states and transitions in this PWSStateMachine.
     *
     * Steps performed:
     * 1) Initialize the pseudostate semantics by calling assembly.calculateInitialStateSemantics().
     * 2) Compute a fixed-point over all other states' semantics via SemanticsVisitor.
     * 3) Assign the newly computed semantics back to each PWSState, skipping the pseudostate to preserve its initial semantics.
     * 4) Update each PWSTransition’s transitionSemantics by computing its pre- and post-conditions.
     */
    public void recalculateSemantics() {
        // Initialize pseudostate semantics
        if (pseudoState instanceof PWSState) {
            PWSState pseudo = (PWSState) pseudoState;
            Semantics init = assembly.calculateInitialStateSemantics();
            pseudo.setStateSemantics(init);
        }

        // Compute fixed-point semantics for all states via SemanticsVisitor
        Map<PWSState, Semantics> semMap = SemanticsVisitor.computeAllStateSemantics(this);

        // ----------------------------------------------------------------------
        // STATE SEMANTICS WRITE-BACK
        // Copy the fixed-point semantics from the visitor’s map into each state’s
        // own field so that all UI annotations (state semantics, exit-zones, etc.)
        // pick up the freshly computed values.
        // ----------------------------------------------------------------------
        // Assign semantics to non-pseudostates
        for (StateInterface s : getStates()) {
            if (s instanceof PWSState && s != pseudoState) {
                PWSState ps = (PWSState) s;
                ps.setStateSemantics(semMap.get(ps));
            }
        }

        // ----------------------------------------------------------------------
        // REACTIVE SEMANTICS: Compute exit zones from SS only, keep CS-only as warnings
        // - CS-only (blue): exit zones present in CS but not in SS (informational)
        // - SS-only (red): exit zones present in SS but not in CS
        // - Reactive semantics used for autonomy is SS-only
        // ----------------------------------------------------------------------
        for (StateInterface si : getStates()) {
            if (si instanceof PWSState ps && si != pseudoState) {
                HashSet<ExitZone> ssZones = new HashSet<>(this.findExitZones(ps.getStateSemantics()));
                HashSet<ExitZone> csZones = new HashSet<>();
                if (hasExplicitConstraints(ps)) {
                    Semantics cs = ps.getConstraintsSemantics();
                    if (cs != null) {
                        csZones.addAll(this.findExitZones(cs));
                    }
                }

                // CS-only: in CS but not in SS
                HashSet<ExitZone> csOnly = new HashSet<>(csZones);
                csOnly.removeAll(ssZones);
                ps.setCsOnlyExitZones(csOnly);

                // SS-only: in SS but not in CS
                HashSet<ExitZone> ssOnly = new HashSet<>(ssZones);
                ssOnly.removeAll(csZones);
                ps.setSsOnlyExitZones(ssOnly);

                // Reactive semantics used for autonomous reasoning: SS-only
                ps.setReactiveSemantics(ssZones);
            }
        }

        // ----------------------------------------------------------------------
        // DEADLOCK DETECTION: Compute and cache deadlock configurations for each state
        // This is done once during semantics recalculation, not at paint time.
        // ----------------------------------------------------------------------
        for (StateInterface si : getStates()) {
            if (si instanceof PWSState ps) {
                if (ps.getStateSemantics() != null && assembly != null) {
                    java.util.Set<pws.editor.semantics.Configuration> deadlocks = 
                        ps.getStateSemantics().findDeadlockConfigurations(assembly);
                    ps.setDeadlockConfigurations(deadlocks);
                } else {
                    ps.setDeadlockConfigurations(new java.util.HashSet<>());
                }
            }
        }
// ----------------------------------------------------------------------
// TRANSITION SEMANTICS UPDATE
// We still need to recalculate each transition’s pre/post‐semantics
// (for guard badges, action badges and reactive‐zone markers) even
// after the visitor has computed full state semantics.
// ----------------------------------------------------------------------
//        // Update each PWSTransition’s semantics
        for (TransitionInterface t : transitions) {
            if (t instanceof PWSTransition) {
                PWSTransition pt = (PWSTransition) t;
                Semantics ts = computeTransitionSemantics(pt);
                pt.setTransitionSemantics(ts);
            }
        }
        // ----------------------------------------------------------------------
        // LEGACY REACTIVE EXIT-ZONES WRITE-BACK (NO-OP)
        // The reactive exit-zone computation has been moved into
        // SemanticsVisitor.computeAllStateSemantics. This block once updated
        // each state’s exit-zones but now remains commented out for reference.
        // ----------------------------------------------------------------------
//        for (StateInterface si : getStates()) {
//            if (si instanceof PWSState ps && !ps.isPseudoState()) {
//                // Base semantics from stateSemantics
//                Semantics baseSem = ps.getStateSemantics();
//                // Compute exit-zones based on current state semantics
//                HashSet<ExitZone> reactiveZones = computeReactiveSemantics(baseSem);
//                ps.setReactiveSemantics(reactiveZones);
//            }
//        }
    }

    /**
     * Updates the exit zones (Reactive Semantics) for a single state based on
     * its current Constraint Semantics (CS) and State Semantics (SS).
     * 
     * This method should be called when CS is edited directly (without running
     * a full fixed-point computation) to immediately reflect the new exit zones.
     *
     * @param ps the PWSState whose exit zones should be updated
     */
    public void updateExitZonesForState(PWSState ps) {
        if (ps == null || ps == pseudoState) return;
        
        HashSet<ExitZone> ssZones = new HashSet<>(this.findExitZones(ps.getStateSemantics()));
        HashSet<ExitZone> csZones = new HashSet<>();
        if (hasExplicitConstraints(ps)) {
            Semantics cs = ps.getConstraintsSemantics();
            if (cs != null) {
                csZones.addAll(this.findExitZones(cs));
            }
        }

        // CS-only: in CS but not in SS
        HashSet<ExitZone> csOnly = new HashSet<>(csZones);
        csOnly.removeAll(ssZones);
        ps.setCsOnlyExitZones(csOnly);

        // SS-only: in SS but not in CS
        HashSet<ExitZone> ssOnly = new HashSet<>(ssZones);
        ssOnly.removeAll(csZones);
        ps.setSsOnlyExitZones(ssOnly);

        // Reactive semantics used for autonomous reasoning: SS-only
        ps.setReactiveSemantics(ssZones);
    }

    /**
     * Renames an assembly machine identifier and updates all related references
     * in constraints, computed semantics, guards, actions, and exit zones.
     */
    public void renameAssemblyMachineId(String oldId, String newId) {
        if (oldId == null || newId == null || oldId.equals(newId)) return;
        if (assembly == null) return;

        StateMachine machine = assembly.getStateMachines().remove(oldId);
        if (machine != null) {
            assembly.addStateMachine(newId, machine);
        }

        List<LTLFormula> formulas = assembly.getLTLFormulas();
        if (formulas != null) {
            for (LTLFormula f : formulas) {
                String text = f.getFormulaText();
                String updated = renameMachineIdInText(text, oldId, newId);
                if (updated != null && !updated.equals(text)) {
                    f.setFormulaText(updated);
                }
            }
        }

        for (StateInterface si : getStates()) {
            if (si instanceof PWSState ps) {
                if (!ps.isPseudoState()) {
                    ps.setRawConstraintText(renameMachineIdInText(ps.getRawConstraintText(), oldId, newId));
                }
                ps.setConstraintsSemantics(renameMachineIdInSemantics(ps.getConstraintsSemantics(), oldId, newId));
                ps.setStateSemantics(renameMachineIdInSemantics(ps.getStateSemantics(), oldId, newId));
                ps.setDeadlockConfigurations(renameMachineIdInConfigs(ps.getDeadlockConfigurations(), oldId, newId));
                renameExitZones(ps.getReactiveSemantics(), oldId, newId);
                renameExitZones(ps.getCsOnlyExitZones(), oldId, newId);
                renameExitZones(ps.getSsOnlyExitZones(), oldId, newId);
            }
        }

        for (TransitionInterface t : transitions) {
            if (t instanceof PWSTransition pt) {
                SMProposition oldGuard = pt.getGuardProposition();
                SMProposition newGuard = renameMachineIdInProposition(oldGuard, oldId, newId);
                if (newGuard != oldGuard) {
                    pt.setGuardProposition(newGuard);
                    if (pt.getGuardAnnotation() != null) {
                        pt.getGuardAnnotation().setContent(newGuard);
                    }
                }

                ActionList actions = pt.getActionList();
                ActionList renamedActions = renameMachineIdInActions(actions, oldId, newId);
                if (renamedActions != actions) {
                    pt.setActionList(renamedActions);
                    if (pt.getActionAnnotation() != null) {
                        pt.getActionAnnotation().setContent(renamedActions);
                    }
                }

                Semantics ts = pt.getTransitionSemantics();
                Semantics tsRenamed = renameMachineIdInSemantics(ts, oldId, newId);
                if (tsRenamed != ts) {
                    pt.setTransitionSemantics(tsRenamed);
                }
            }
        }
    }

    /**
     * Renames a state inside an assembly machine and updates all related references.
     */
    public void renameAssemblyStateName(StateMachine machine, String oldName, String newName) {
        if (machine == null || oldName == null || newName == null || oldName.equals(newName)) return;
        if (assembly == null) return;

        Set<String> ids = new HashSet<>();
        for (Map.Entry<String, StateMachine> entry : assembly.getStateMachines().entrySet()) {
            if (entry.getValue() == machine) {
                ids.add(entry.getKey());
            }
        }
        if (ids.isEmpty()) return;

        for (String machineId : ids) {
            List<LTLFormula> formulas = assembly.getLTLFormulas();
            if (formulas != null) {
                for (LTLFormula f : formulas) {
                    String text = f.getFormulaText();
                    String updated = renameStateNameInText(text, machineId, oldName, newName);
                    if (updated != null && !updated.equals(text)) {
                        f.setFormulaText(updated);
                    }
                }
            }

            for (StateInterface si : getStates()) {
                if (si instanceof PWSState ps) {
                    if (!ps.isPseudoState()) {
                        ps.setRawConstraintText(renameStateNameInText(ps.getRawConstraintText(), machineId, oldName, newName));
                    }
                    ps.setConstraintsSemantics(renameStateNameInSemantics(ps.getConstraintsSemantics(), machineId, oldName, newName));
                    ps.setStateSemantics(renameStateNameInSemantics(ps.getStateSemantics(), machineId, oldName, newName));
                    ps.setDeadlockConfigurations(renameStateNameInConfigs(ps.getDeadlockConfigurations(), machineId, oldName, newName));
                    renameExitZonesStateName(ps.getReactiveSemantics(), machineId, oldName, newName);
                    renameExitZonesStateName(ps.getCsOnlyExitZones(), machineId, oldName, newName);
                    renameExitZonesStateName(ps.getSsOnlyExitZones(), machineId, oldName, newName);
                }
            }

            for (TransitionInterface t : transitions) {
                if (t instanceof PWSTransition pt) {
                    SMProposition oldGuard = pt.getGuardProposition();
                    SMProposition newGuard = renameStateNameInProposition(oldGuard, machineId, oldName, newName);
                    if (newGuard != oldGuard) {
                        pt.setGuardProposition(newGuard);
                        if (pt.getGuardAnnotation() != null) {
                            pt.getGuardAnnotation().setContent(newGuard);
                        }
                    }

                    Semantics ts = pt.getTransitionSemantics();
                    Semantics tsRenamed = renameStateNameInSemantics(ts, machineId, oldName, newName);
                    if (tsRenamed != ts) {
                        pt.setTransitionSemantics(tsRenamed);
                    }
                }
            }
        }
    }

    private static String renameMachineIdInText(String text, String oldId, String newId) {
        if (text == null || text.isBlank()) return text;
        String pattern = "(?<![A-Za-z0-9_])" + Pattern.quote(oldId) + "(?=[.:])";
        return text.replaceAll(pattern, newId);
    }

    private static String renameStateNameInText(String text, String machineId, String oldName, String newName) {
        if (text == null || text.isBlank()) return text;
        String pattern = "(?<![A-Za-z0-9_])" + Pattern.quote(machineId) + "([.:])" + Pattern.quote(oldName) + "(?![A-Za-z0-9_])";
        return text.replaceAll(pattern, machineId + "$1" + newName);
    }

    private static SMProposition renameMachineIdInProposition(SMProposition prop, String oldId, String newId) {
        if (prop == null) return null;
        if (prop instanceof BasicStateProposition bsp) {
            if (oldId.equals(bsp.getMachineId())) {
                return new BasicStateProposition(newId, bsp.getStateName());
            }
            return prop;
        }
        if (prop instanceof AndProposition and) {
            SMProposition left = renameMachineIdInProposition(and.getLeft(), oldId, newId);
            SMProposition right = renameMachineIdInProposition(and.getRight(), oldId, newId);
            return (left == and.getLeft() && right == and.getRight()) ? prop : new AndProposition(left, right);
        }
        if (prop instanceof OrProposition or) {
            SMProposition left = renameMachineIdInProposition(or.getLeft(), oldId, newId);
            SMProposition right = renameMachineIdInProposition(or.getRight(), oldId, newId);
            return (left == or.getLeft() && right == or.getRight()) ? prop : new OrProposition(left, right);
        }
        if (prop instanceof NotProposition not) {
            SMProposition inner = renameMachineIdInProposition(not.getProposition(), oldId, newId);
            return (inner == not.getProposition()) ? prop : new NotProposition(inner);
        }
        if (prop instanceof TrueProposition || prop instanceof FalseProposition) {
            return prop;
        }
        return prop;
    }

    private static SMProposition renameStateNameInProposition(SMProposition prop, String machineId, String oldName, String newName) {
        if (prop == null) return null;
        if (prop instanceof BasicStateProposition bsp) {
            if (machineId.equals(bsp.getMachineId()) && oldName.equals(bsp.getStateName())) {
                return new BasicStateProposition(machineId, newName);
            }
            return prop;
        }
        if (prop instanceof AndProposition and) {
            SMProposition left = renameStateNameInProposition(and.getLeft(), machineId, oldName, newName);
            SMProposition right = renameStateNameInProposition(and.getRight(), machineId, oldName, newName);
            return (left == and.getLeft() && right == and.getRight()) ? prop : new AndProposition(left, right);
        }
        if (prop instanceof OrProposition or) {
            SMProposition left = renameStateNameInProposition(or.getLeft(), machineId, oldName, newName);
            SMProposition right = renameStateNameInProposition(or.getRight(), machineId, oldName, newName);
            return (left == or.getLeft() && right == or.getRight()) ? prop : new OrProposition(left, right);
        }
        if (prop instanceof NotProposition not) {
            SMProposition inner = renameStateNameInProposition(not.getProposition(), machineId, oldName, newName);
            return (inner == not.getProposition()) ? prop : new NotProposition(inner);
        }
        if (prop instanceof TrueProposition || prop instanceof FalseProposition) {
            return prop;
        }
        return prop;
    }

    private static Semantics renameMachineIdInSemantics(Semantics sem, String oldId, String newId) {
        if (sem == null) return null;
        boolean changed = false;
        Semantics out = new Semantics(sem.getAssemblyId());
        for (Configuration cfg : sem.getConfigurations()) {
            Configuration renamed = renameMachineIdInConfig(cfg, oldId, newId);
            if (renamed != cfg) changed = true;
            out.addConfiguration(renamed);
        }
        return changed ? out : sem;
    }

    private static Semantics renameStateNameInSemantics(Semantics sem, String machineId, String oldName, String newName) {
        if (sem == null) return null;
        boolean changed = false;
        Semantics out = new Semantics(sem.getAssemblyId());
        for (Configuration cfg : sem.getConfigurations()) {
            Configuration renamed = renameStateNameInConfig(cfg, machineId, oldName, newName);
            if (renamed != cfg) changed = true;
            out.addConfiguration(renamed);
        }
        return changed ? out : sem;
    }

    private static Configuration renameMachineIdInConfig(Configuration cfg, String oldId, String newId) {
        if (cfg == null) return null;
        boolean changed = false;
        List<BasicStateProposition> props = new ArrayList<>();
        for (BasicStateProposition bsp : cfg.getBasicStatePropositions()) {
            if (oldId.equals(bsp.getMachineId())) {
                props.add(new BasicStateProposition(newId, bsp.getStateName()));
                changed = true;
            } else {
                props.add(bsp);
            }
        }
        return changed ? Configuration.fromBasicStatePropositions(cfg.getAssemblyId(), props) : cfg;
    }

    private static Configuration renameStateNameInConfig(Configuration cfg, String machineId, String oldName, String newName) {
        if (cfg == null) return null;
        boolean changed = false;
        List<BasicStateProposition> props = new ArrayList<>();
        for (BasicStateProposition bsp : cfg.getBasicStatePropositions()) {
            if (machineId.equals(bsp.getMachineId()) && oldName.equals(bsp.getStateName())) {
                props.add(new BasicStateProposition(machineId, newName));
                changed = true;
            } else {
                props.add(bsp);
            }
        }
        return changed ? Configuration.fromBasicStatePropositions(cfg.getAssemblyId(), props) : cfg;
    }

    private static Set<Configuration> renameMachineIdInConfigs(Set<Configuration> configs, String oldId, String newId) {
        if (configs == null) return null;
        boolean changed = false;
        Set<Configuration> out = new HashSet<>();
        for (Configuration cfg : configs) {
            Configuration renamed = renameMachineIdInConfig(cfg, oldId, newId);
            if (renamed != cfg) changed = true;
            out.add(renamed);
        }
        return changed ? out : configs;
    }

    private static Set<Configuration> renameStateNameInConfigs(Set<Configuration> configs, String machineId, String oldName, String newName) {
        if (configs == null) return null;
        boolean changed = false;
        Set<Configuration> out = new HashSet<>();
        for (Configuration cfg : configs) {
            Configuration renamed = renameStateNameInConfig(cfg, machineId, oldName, newName);
            if (renamed != cfg) changed = true;
            out.add(renamed);
        }
        return changed ? out : configs;
    }

    private static ActionList renameMachineIdInActions(ActionList actions, String oldId, String newId) {
        if (actions == null) return null;
        boolean changed = false;
        ActionList out = new ActionList();
        for (Action a : actions) {
            if (a != null && oldId.equals(a.getMachineId())) {
                out.add(new Action(newId, a.getEvent()));
                changed = true;
            } else {
                out.add(a);
            }
        }
        return changed ? out : actions;
    }

    private static void renameExitZones(Set<ExitZone> zones, String oldId, String newId) {
        if (zones == null || zones.isEmpty()) return;
        for (ExitZone ez : zones) {
            if (ez == null) continue;
            if (oldId.equals(ez.getStateMachineId())) {
                ez.setStateMachineId(newId);
            }
            BasicStateProposition src = ez.getSource();
            if (src != null && oldId.equals(src.getMachineId())) {
                ez.setSource(new BasicStateProposition(newId, src.getStateName()));
            }
            BasicStateProposition tgt = ez.getTarget();
            if (tgt != null && oldId.equals(tgt.getMachineId())) {
                ez.setTarget(new BasicStateProposition(newId, tgt.getStateName()));
            }
        }
    }

    private static void renameExitZonesStateName(Set<ExitZone> zones, String machineId, String oldName, String newName) {
        if (zones == null || zones.isEmpty()) return;
        for (ExitZone ez : zones) {
            if (ez == null) continue;
            BasicStateProposition src = ez.getSource();
            if (src != null && machineId.equals(src.getMachineId()) && oldName.equals(src.getStateName())) {
                ez.setSource(new BasicStateProposition(machineId, newName));
            }
            BasicStateProposition tgt = ez.getTarget();
            if (tgt != null && machineId.equals(tgt.getMachineId()) && oldName.equals(tgt.getStateName())) {
                ez.setTarget(new BasicStateProposition(machineId, newName));
            }
        }
    }

    /**
     * LEGACY: Old transition-semantics implementation.
     * This method has been replaced by
     * SemanticsVisitor.computeTransitionContribution(t, base, asm)
     * for fixed-point computation in the visitor.
     * It remains here only to support UI tasks such as
     * displaying per-transition semantics in the editor.
     */
    public Semantics computeTransitionSemantics(PWSTransition t) {
        // Legacy API: use the state’s current semantics as base
        Semantics base = ((PWSState) t.getSource()).getStateSemantics();
        return computeTransitionContribution(t, base);
    }

    /**
     * Compute semantics for a triggerable or initial transition.
     */
//    private Semantics computeTriggerableSemantics(PWSTransition t) {
//        // Get the source state for this transition
//        PWSState src = (PWSState) t.getSource();
//        // Retrieve the full semantics of the source state
//        Semantics stateSem = src.getStateSemantics();
//        // Convert the transition's guard proposition into semantics
//        Semantics guardSem = t.getGuardProposition().toSemantics(assembly);
//        // Compute the intersection of stateSem and the guard semantics
//        Semantics result = stateSem.AND(guardSem);
//        // Apply each associated action event to the result
//        for (Action a : t.getActionList()) {
//            // Transform semantics by this machine event
//            result = result.transformByMachineEvent(a.getMachineId(), a.getEvent(), assembly);
//        }
//        // Return the combined semantics for this triggerable transition
//        return result;
//    }

    /**
     * Compute semantics for a triggerable or initial transition using supplied base semantics.
     * @param t     the transition
     * @param base  the working semantics of the source state
     * @return the transition’s contribution
     */
    public Semantics computeTriggerableSemantics(PWSTransition t, Semantics base) {
        Semantics guardSem = t.getGuardProposition().toSemantics(assembly);
        Semantics result = base.AND(guardSem);
        for (Action a : t.getActionList()) {
            result = result.transformByMachineEvent(a.getMachineId(), a.getEvent(), assembly);
        }
        return result;
    }

    /**
     * LEGACY: Compute semantics for a reactive (autonomous) transition.
     */
    private Semantics computeReactiveTransitionSemantics(PWSTransition t) {
        // Retrieve the source state of the transition
        PWSState src = (PWSState) t.getSource();
        // Get the current full semantics of the source state
        Semantics stateSem = src.getStateSemantics();
        if (t.getGuardProposition() instanceof TrueProposition) {
            Semantics result = (stateSem == null) ? Semantics.bottom(assembly.getAssemblyId()) : stateSem.clone();
            for (Action a : t.getActionList()) {
                result = result.transformByMachineEvent(a.getMachineId(), a.getEvent(), assembly);
            }
            return result;
        }
        // Cast the transition guard to a BasicStateProposition to use as the reactive trigger
        // Determine the guard proposition for this transition (could be BasicStateProposition or TrueProposition)
        SMProposition guardProp = t.getGuardProposition();
        // Initialize accumulator to ⊥ for reactive contributions
        Semantics result = Semantics.bottom(assembly.getAssemblyId());
        // Iterate over all exit zones of the source state
        for (ExitZone ez : src.getReactiveSemantics()) {
            // Check if this exit zone's target proposition matches the transition guard
            if (guardProp instanceof TrueProposition
                    || ez.getTarget().equals(guardProp)) {
                // Apply the internal machine transition effect on the matching configurations
                Semantics frag = stateSem.transformByMachineTransition(
                        ez.getStateMachineId(),
                        ez.getTransition(),
                        assembly
                );
                // Accumulate this reactive firing into the result
                result = result.OR(frag);
            }
        }
        // Apply any post-actions associated with the transition
        for (Action a : t.getActionList()) {
            result = result.transformByMachineEvent(a.getMachineId(), a.getEvent(), assembly);
        }
        // Return the combined reactive semantics for this transition
        return result;
    }

    /**
     * Compute semantics for a reactive (autonomous) transition using supplied base semantics.
     * @param t     the transition
     * @param base  the working semantics of the source state
     * @return the transition’s contribution
     */
    public Semantics computeReactiveTransitionSemantics(PWSTransition t, Semantics base) {
        if (t.getGuardProposition() instanceof TrueProposition) {
            Semantics result = (base == null) ? Semantics.bottom(assembly.getAssemblyId()) : base.clone();
            for (Action a : t.getActionList()) {
                result = result.transformByMachineEvent(a.getMachineId(), a.getEvent(), assembly);
            }
            return result;
        }
        Semantics result = Semantics.bottom(assembly.getAssemblyId());
        PWSState src = (PWSState) t.getSource();
        // Compute exit zones on the fly from the current base semantics (SS only).
        // This avoids relying on cached reactiveSemantics that may be stale after load.
        HashSet<ExitZone> reactiveZones = new HashSet<>();
        if (base != null) {
            reactiveZones.addAll(findExitZones(base));
        }
        for (ExitZone ez : reactiveZones) {
            if (t.getGuardProposition() instanceof TrueProposition
                    || ez.getTarget().equals(t.getGuardProposition())) {
                Semantics frag = base.transformByMachineTransition(
                        ez.getStateMachineId(), ez.getTransition(), assembly);
                result = result.OR(frag);
            }
        }
        for (Action a : t.getActionList()) {
            result = result.transformByMachineEvent(a.getMachineId(), a.getEvent(), assembly);
        }
        return result;
    }

    /**
     * Returns true when a state has explicit (non-ANY) constraints.
     */
    private boolean hasExplicitConstraints(PWSState ps) {
        if (ps == null || ps.isPseudoState()) return false;
        String raw = ps.getRawConstraintText();
        if (raw != null && !raw.isBlank()) {
            return !"ANY".equalsIgnoreCase(raw.trim());
        }
        Semantics cs = ps.getConstraintsSemantics();
        return cs != null && !cs.getConfigurations().isEmpty();
    }

    /**
     * Compute a transition’s contribution given a working base semantics.
     * @param t     the transition
     * @param base  the working semantics of the source state
     * @return the transition’s contribution
     */
    public Semantics computeTransitionContribution(PWSTransition t, Semantics base) {
        if (t.isTriggerable() || ((PWSState) t.getSource()).isPseudoState()) {
            return computeTriggerableSemantics(t, base);
        } else {
            return computeReactiveTransitionSemantics(t, base);
        }
    }

    /**
     * Computes the reactive exit-zones for this state machine given a base semantics.
     *
     * <p>The <b>base semantics</b> is typically associated with a PWSState and denotes its
     * current set of configurations.</p>
     *
     * <p>An <b>exit-zone</b> represents a configuration under which an autonomous
     * (trigger-free) transition in one of the component machines can fire.</p>
     *
     * <p>Concretely, for each autonomous transition:</p>
     * <ol>
     *   <li>The transition’s <i>source state</i> must be included in the provided base semantics.</li>
     * </ol>
     * <p>If the source condition holds, an ExitZone is recorded for the autonomous transition.
     * The target may already be part of the semantics (internal evolution) or may extend it.</p>
     *
     * @param baseSemantics the current fixed-point semantics of a source state
     * @return a set of ExitZone objects indicating configurations that immediately trigger
     *         autonomous transitions not yet reflected in the base semantics
     */
    public HashSet<ExitZone> findExitZones(Semantics baseSemantics) {
        HashSet<ExitZone> reactiveSem = new HashSet<>();
        Map<String, StateMachine> stateMachines = assembly.getStateMachines();
        if (stateMachines != null) {
            for (Map.Entry<String, StateMachine> entry : stateMachines.entrySet()) {
                String machineId = entry.getKey();
                StateMachine machine = entry.getValue();
                List<TransitionInterface> allTransitions = machine.getTransitions();
                if (allTransitions != null) {
                    for (TransitionInterface t : allTransitions) {
                        if (t instanceof Transition) {
                            Transition transition = (Transition) t;
                            // Only consider enabled autonomous transitions
                            if (transition.isEnabled() && transition.isAutonomous()) {
                                State sourceState = (State) transition.getSource();
                                State targetState = (State) transition.getTarget();
                                BasicStateProposition bs_source = new BasicStateProposition(machineId, sourceState.getName());
                                BasicStateProposition bs_target = null;
                                // An autonomous transition yields an exit-zone when its source
                                // intersects the current semantics (target may or may not be included).
                                Semantics sourceAndSem = bs_source.toSemantics( assembly ).AND(baseSemantics);
                                if (!sourceAndSem.ISEMPTY()) {
                                    bs_target = new BasicStateProposition(machineId, targetState.getName());
                                    ExitZone ez = new ExitZone(
                                            machineId,
                                            transition,
                                            bs_source,
                                            bs_target
                                    );
                                    reactiveSem.add(ez);
                                }
                            }
                        }
                    }
                }
            }
        }
        return reactiveSem;
    }

    @Override
    public PWSStateMachine clone() {
        PWSStateMachine cloned = new PWSStateMachine(this.getName());
        cloned.setAssembly(this.getAssembly());
        // Note: to clone states and transitions, a custom copy strategy is required
        // and can be implemented as needed.
        return cloned;
    }
}
