package pws.editor.semantics;

import assembly.Assembly;
import machinery.StateInterface;
import machinery.TransitionInterface;
import pws.PWSState;
import pws.PWSStateMachine;
import pws.PWSTransition;
import smalgebra.TrueProposition;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

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

}
