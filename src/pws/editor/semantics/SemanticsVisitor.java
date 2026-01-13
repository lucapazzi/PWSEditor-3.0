package pws.editor.semantics;

import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import pws.editor.semantics.ExitZone;

import assembly.Action;
import smalgebra.TrueProposition;

import machinery.StateInterface;
import pws.PWSState;
import pws.PWSStateMachine;
import pws.PWSTransition;
import machinery.TransitionInterface;
import assembly.Assembly;
import pws.editor.semantics.Semantics;
import pws.PWSStateMachine;
import pws.editor.semantics.ExitZone;

/**
 * Visitor that computes fixed‑point semantics for all states in a PWSStateMachine.
 * Each state’s semantics is the union of the contributions of its incoming
 * transitions, where:
 *
 * <ul>
 *   <li>triggerable (and initial) transitions apply their guard AND then any actions to
 *       the source state’s <i>stateSemantics</i>;</li>
 *   <li>reactive (autonomous) transitions apply internal transformations
 *       (via ExitZone) to the source state’s <i>reactiveSemantics</i> and then any actions.</li>
 * </ul>
 */
public class SemanticsVisitor {
    private static final Logger logger = Logger.getLogger(SemanticsVisitor.class.getName());

    /**
     * Iteratively computes a semantics map for every PWSState until convergence.
     */
    public static Map<PWSState, Semantics> computeAllStateSemantics(PWSStateMachine machine) {
        logger.info("Starting fixed-point semantics computation (worklist) for machine '" + machine.getName() + "'.");

        Assembly asm = machine.getAssembly();
        String asmId = asm.getAssemblyId();
        Map<PWSState, Semantics> semMap = new HashMap<>();
        // Initialize all states to bottom
        for (StateInterface si : machine.getStates()) {
            semMap.put((PWSState) si, Semantics.bottom(asmId));
        }
        // Seed pseudostate with top (all configurations)
        PWSState pseudo = null;
        for (PWSState s : semMap.keySet()) {
            if (s.isPseudoState()) {
                pseudo = s;
                break;
            }
        }
        if (pseudo == null) {
            throw new IllegalStateException("No pseudostate found in machine.");
        }
        // seed pseudostate with initial assembly semantics
        semMap.put(pseudo, asm.calculateInitialStateSemantics());

        // Worklist of states to process
        Deque<PWSState> worklist = new ArrayDeque<>();
        worklist.add(pseudo);

        // Chaotic iteration until fixed-point
        while (!worklist.isEmpty()) {
            PWSState src = worklist.poll();
            Semantics base = semMap.get(src);

            for (TransitionInterface ti : machine.getTransitions()) {
                if (!(ti instanceof PWSTransition)) continue;
                PWSTransition t = (PWSTransition) ti;
                if (t.getSource() != src || !t.isEnabled()) continue;

                Semantics contrib = machine.computeTransitionContribution(t, base);
                PWSState tgt = (PWSState) t.getTarget();
                Semantics oldSem = semMap.get(tgt);
                Semantics combined = oldSem.OR(contrib);
                if (!combined.equals(oldSem)) {
                    semMap.put(tgt, combined);
                    worklist.add(tgt);
                }
            }
        }

        // (Removed POST-FIXPOINT EXIT-ZONE UPDATE)
        logger.info("Completed worklist semantics computation for machine '" + machine.getName() + "'.");
        return semMap;
    }

    /**
     * Compute the semantics for a single target state in one iteration of the fixed-point algorithm.
     *
     * <p>This method aggregates the contributions of all incoming transitions whose target is the specified state.
     * It handles two kinds of transitions:
     * <ul>
     *   <li><b>Triggerable or initial transitions</b>: applies the guard proposition AND-ed with the source state's
     *       current semantics.</li>
     *   <li><b>Reactive (autonomous) transitions</b>: for each exit zone associated with the source state, applies
     *       the corresponding internal state-machine transition to the reactive semantics, then ORs the results.</li>
     * </ul>
     *
     * <p>After processing all transitions, the aggregated semantics captures the new “stateSemantics” for the target.
     *
     * @param target    the PWSState for which to compute updated semantics
     * @param machine   the state machine containing the transitions and assembly context
     * @param currentMap map of PWSState to their current semantics from the previous iteration
     * @return the newly computed Semantics for the target state
     */
    private static Semantics computeStateSemanticsOnce(
            PWSState target,
            PWSStateMachine machine,
            Map<PWSState, Semantics> currentMap) {
        logger.info(">> computeStateSemanticsOnce START for target='" + target.getName() + "'");
        logger.info("    Current semantics for state '" + target.getName() + "': " + currentMap.get(target));


        // Retrieve the assembly context for semantics conversions
        Assembly asm = machine.getAssembly();
        // Initialize accumulator to ⊥ (no configurations) for fixed-point aggregation
        Semantics agg = Semantics.bottom(asm.getAssemblyId());


        // Iterate through all transitions in the machine
        for (TransitionInterface ti : machine.getTransitions()) {
            // Skip any non-PWS transitions
            if (!(ti instanceof PWSTransition)) continue;
            // Cast to PWS-specific transition type
            PWSTransition t = (PWSTransition) ti;
            // Only process transitions whose target matches the current state
            if (t.getTarget() != target) continue;
            PWSState src = (PWSState) t.getSource();
            // Use working semantics instead of state fields
            Semantics base = currentMap.get(src);
            Semantics contrib = machine.computeTransitionContribution(t, base);
            logger.info("Transition from '" + src.getName() + "': "
                    + currentMap.get(src)
                    + " contributes " + contrib
                    + " to state '" + target.getName() + "'");
            // OR-accumulate the contribution into the aggregate for target state
            agg = agg.OR(contrib);
        }

        logger.info("<< computeStateSemanticsOnce END for target='" + target.getName() + "': result=" + agg);
        return agg;
    }
    // (Removed computeTransitionContribution; now delegated to machine)
}