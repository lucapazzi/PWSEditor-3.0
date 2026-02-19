package pws.editor.semantics;

import assembly.Assembly;
import machinery.StateInterface;
import machinery.TransitionInterface;
import pws.PWSState;
import pws.PWSStateMachine;
import pws.PWSTransition;
import smalgebra.BasicStateProposition;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
        Map<PWSState, HashSet<ExitZone>> incomingOverflowZones = new HashMap<>();
        // Initialize all states to bottom
        for (StateInterface si : machine.getStates()) {
            PWSState ps = (PWSState) si;
            semMap.put(ps, Semantics.bottom(asmId));
            incomingOverflowZones.put(ps, new HashSet<>());
        }
        // Seed pseudostate with assembly closure (initial semantics closed under exit zones)
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
        // seed pseudostate with assembly closure
        Semantics closure = machine.calculateAssemblyClosure();
        if (closure == null) {
            closure = asm.calculateInitialStateSemantics();
        }
        semMap.put(pseudo, closure);

        // Worklist of states to process
        Deque<PWSState> worklist = new ArrayDeque<>();
        worklist.add(pseudo);

        int step = 0;
        // Chaotic iteration until fixed-point
        while (!worklist.isEmpty()) {
            PWSState src = worklist.poll();
            step++;
            Semantics base = semMap.get(src);

            logger.info(String.format("--> Step %d: processing state '%s' (worklist size %d) base=%s",
                    step, src.getName(), worklist.size(), base));

            for (TransitionInterface ti : machine.getTransitions()) {
                if (!(ti instanceof PWSTransition)) continue;
                PWSTransition t = (PWSTransition) ti;
                if (t.getSource() != src || !t.isEnabled()) continue;

                Semantics contrib = machine.computeTransitionContribution(t, base);
                if (contrib == null || contrib.ISEMPTY()) {
                    logger.fine(String.format("    transition '%s' -> '%s' contributed no configs",
                            t.getSource().getName(), t.getTarget().getName()));
                    continue;
                }

                PWSState tgt = (PWSState) t.getTarget();
                Semantics accepted = contrib;
                if (hasExplicitConstraints(tgt)) {
                    Semantics cs = tgt.getConstraintsSemantics();
                    if (cs != null) {
                        accepted = contrib.AND(cs);
                        Semantics overflow = contrib.AND(cs.NOT(asm));
                        if (!overflow.ISEMPTY()) {
                            incomingOverflowZones.computeIfAbsent(tgt, k -> new HashSet<>())
                                    .addAll(buildConstraintOverflowExitZones(overflow, cs, asm));
                        }
                    }
                }
                if (accepted == null || accepted.ISEMPTY()) {
                    continue;
                }

                Semantics oldSem = semMap.get(tgt);
                Semantics combined = oldSem.OR(accepted);
                combined = machine.closeStateSemanticsWithInternalExitZones(tgt, combined);
                if (!combined.equals(oldSem)) {
                    semMap.put(tgt, combined);
                    worklist.add(tgt);
                    logger.info(String.format("    transition '%s' -> '%s' added %d configs: %d -> %d (enqueued '%s')",
                            t.getSource().getName(),
                            tgt.getName(),
                            accepted.getConfigurations().size(),
                            oldSem.getConfigurations().size(),
                            combined.getConfigurations().size(),
                            tgt.getName()));
                } else {
                    logger.fine(String.format("    transition '%s' -> '%s' produced no new configurations",
                            t.getSource().getName(), tgt.getName()));
                }
            }
        }

        // Store transition-generated overflow exit zones on each state.
        for (Map.Entry<PWSState, HashSet<ExitZone>> e : incomingOverflowZones.entrySet()) {
            e.getKey().setIncomingTransitionOverflowExitZones(e.getValue());
        }

        // (Removed POST-FIXPOINT EXIT-ZONE UPDATE)
        logger.info("Completed worklist semantics computation for machine '" + machine.getName() + "' in " + step + " steps.");
        return semMap;
    }

    private static boolean hasExplicitConstraints(PWSState state) {
        if (state == null || state.isPseudoState()) {
            return false;
        }
        String raw = state.getRawConstraintText();
        if (raw != null && !raw.isBlank()) {
            return !"ANY".equalsIgnoreCase(raw.trim());
        }
        Semantics cs = state.getConstraintsSemantics();
        return cs != null && !cs.getConfigurations().isEmpty();
    }

    private static Set<ExitZone> buildConstraintOverflowExitZones(Semantics overflow, Semantics cs, Assembly asm) {
        Set<ExitZone> zones = new HashSet<>();
        if (overflow == null || overflow.ISEMPTY()) {
            return zones;
        }
        for (Configuration cfg : overflow.getConfigurations()) {
            Set<BasicStateProposition> candidates = new LinkedHashSet<>();
            for (BasicStateProposition bsp : cfg.getBasicStatePropositions()) {
                if (bsp == null) {
                    continue;
                }
                if (cs == null || asm == null) {
                    candidates.add(bsp);
                    continue;
                }
                try {
                    Semantics bspSem = bsp.toSemantics(asm);
                    if (bspSem.AND(cs).ISEMPTY()) {
                        candidates.add(bsp);
                    }
                } catch (Exception ignore) {
                    candidates.add(bsp);
                }
            }
            // If no single proposition is clearly outside CS, keep a conservative marker.
            if (candidates.isEmpty()) {
                candidates.addAll(cfg.getBasicStatePropositions());
            }
            for (BasicStateProposition bsp : candidates) {
                if (bsp == null) {
                    continue;
                }
                zones.add(new ExitZone(
                        bsp.getMachineId(),
                        null,
                        new BasicStateProposition(bsp.getMachineId(), bsp.getStateName()),
                        new BasicStateProposition(bsp.getMachineId(), bsp.getStateName())));
            }
        }
        return zones;
    }

}
