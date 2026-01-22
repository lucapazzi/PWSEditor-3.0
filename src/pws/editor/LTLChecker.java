package pws.editor;

import assembly.Assembly;
import assembly.LTLFormula;
import assembly.LTLParser;
import machinery.StateInterface;
import machinery.TransitionInterface;
import pws.PWSState;
import pws.PWSStateMachine;
import pws.editor.semantics.Semantics;
import smalgebra.BasicStateProposition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Minimal LTL checker for response-style formulas: G (p -> F q). */
public final class LTLChecker {
    private LTLChecker() {
    }

    public static List<LTLCheckResult> check(PWSStateMachine machine) {
        List<LTLCheckResult> results = new ArrayList<>();
        if (machine == null || machine.getAssembly() == null) return results;

        Assembly assembly = machine.getAssembly();
        List<LTLFormula> formulas = assembly.getLTLFormulas();
        if (formulas == null || formulas.isEmpty()) return results;

        Map<StateInterface, List<StateInterface>> adjacency = buildAdjacency(machine);

        for (LTLFormula f : formulas) {
            String text = f.getFormulaText() == null ? "" : f.getFormulaText().trim();
            if (text.isEmpty()) {
                results.add(new LTLCheckResult(f.getId(), text, LTLCheckResult.Status.SKIPPED, "Empty formula", null));
                continue;
            }
            try {
                LTLParser.Node node = LTLParser.parse(text);
                ResponsePattern pattern = ResponsePattern.from(node);
                if (pattern == null) {
                    results.add(new LTLCheckResult(f.getId(), text, LTLCheckResult.Status.SKIPPED,
                        "Unsupported formula (supported: G(p -> F q))", null));
                    continue;
                }
                CheckContext ctx = buildContext(machine, adjacency, assembly, pattern);
                if (ctx.violating.isEmpty()) {
                    String msg = buildPassMessage(ctx);
                    results.add(new LTLCheckResult(f.getId(), text, LTLCheckResult.Status.PASS, msg, null));
                } else {
                    String msg = buildFailMessage(ctx);
                    results.add(new LTLCheckResult(f.getId(), text, LTLCheckResult.Status.FAIL, msg, ctx.violating));
                }
            } catch (Exception ex) {
                results.add(new LTLCheckResult(f.getId(), text, LTLCheckResult.Status.ERROR, ex.getMessage(), null));
            }
        }
        return results;
    }

    private static Map<StateInterface, List<StateInterface>> buildAdjacency(PWSStateMachine machine) {
        Map<StateInterface, List<StateInterface>> adjacency = new HashMap<>();
        for (StateInterface s : machine.getStates()) {
            adjacency.put(s, new ArrayList<>());
        }
        for (TransitionInterface t : machine.getTransitions()) {
            StateInterface src = t.getSource();
            StateInterface dst = t.getTarget();
            if (src != null && dst != null) {
                adjacency.computeIfAbsent(src, k -> new ArrayList<>()).add(dst);
            }
        }
        return adjacency;
    }

    private static CheckContext buildContext(PWSStateMachine machine,
                                             Map<StateInterface, List<StateInterface>> adjacency,
                                             Assembly assembly,
                                             ResponsePattern pattern) {
        List<String> violating = new ArrayList<>();
        List<PWSState> pStates = new ArrayList<>();
        Set<StateInterface> qStates = new HashSet<>();

        for (StateInterface s : machine.getStates()) {
            if (!(s instanceof PWSState)) continue;
            PWSState ps = (PWSState) s;
            if (ps.isPseudoState()) continue;
            if (propositionHolds(ps, pattern.p, assembly)) {
                pStates.add(ps);
            }
            if (propositionHolds(ps, pattern.q, assembly)) {
                qStates.add(ps);
            }
        }

        for (PWSState start : pStates) {
            if (isReachable(start, qStates, adjacency)) continue;
            violating.add(start.getName());
        }
        return new CheckContext(pStates, qStates, violating, adjacency);
    }

    private static boolean propositionHolds(PWSState state, BasicStateProposition prop, Assembly assembly) {
        if (state.getStateSemantics() == null) return false;
        Semantics stateSem = state.getStateSemantics();
        if (stateSem.ISEMPTY()) return false;
        Semantics propSem = prop.toSemantics(assembly);
        return stateSem.implies(propSem);
    }

    private static boolean isReachable(StateInterface start,
                                       Set<StateInterface> targets,
                                       Map<StateInterface, List<StateInterface>> adjacency) {
        if (targets.contains(start)) return true;
        Deque<StateInterface> work = new ArrayDeque<>();
        Set<StateInterface> seen = new HashSet<>();
        work.add(start);
        seen.add(start);
        while (!work.isEmpty()) {
            StateInterface cur = work.poll();
            List<StateInterface> nexts = adjacency.get(cur);
            if (nexts == null) continue;
            for (StateInterface next : nexts) {
                if (!seen.add(next)) continue;
                if (targets.contains(next)) return true;
                work.add(next);
            }
        }
        return false;
    }

    private static List<String> findExamplePath(StateInterface start,
                                                Set<StateInterface> targets,
                                                Map<StateInterface, List<StateInterface>> adjacency) {
        Map<StateInterface, StateInterface> prev = new HashMap<>();
        Deque<StateInterface> work = new ArrayDeque<>();
        Set<StateInterface> seen = new HashSet<>();
        work.add(start);
        seen.add(start);
        StateInterface found = null;
        while (!work.isEmpty()) {
            StateInterface cur = work.poll();
            if (targets.contains(cur)) {
                found = cur;
                break;
            }
            List<StateInterface> nexts = adjacency.get(cur);
            if (nexts == null) continue;
            for (StateInterface next : nexts) {
                if (!seen.add(next)) continue;
                prev.put(next, cur);
                work.add(next);
            }
        }
        if (found == null) return null;
        List<String> path = new ArrayList<>();
        StateInterface cur = found;
        while (cur != null) {
            path.add(0, cur.getName());
            cur = prev.get(cur);
        }
        return path;
    }

    private static String buildPassMessage(CheckContext ctx) {
        if (ctx.pStates.isEmpty()) {
            return "No states satisfy p; formula holds vacuously.";
        }
        if (ctx.qStates.isEmpty()) {
            return "No states satisfy q, but no p-states were found (vacuous).";
        }
        PWSState start = ctx.pStates.get(0);
        List<String> path = findExamplePath(start, ctx.qStates, ctx.adjacency);
        String pList = joinStateNames(ctx.pStates);
        String qList = joinStateNames(ctx.qStates);
        if (path == null) {
            return "States satisfying p: " + pList + ". States satisfying q: " + qList + ".";
        }
        return "States satisfying p: " + pList + ". States satisfying q: " + qList +
            ". Example path from " + start.getName() + " to q: " + String.join(" -> ", path) + ".";
    }

    private static String buildFailMessage(CheckContext ctx) {
        if (ctx.qStates.isEmpty()) {
            return "No states satisfy q; all p-states violate the response.";
        }
        String viol = ctx.violating.isEmpty() ? "-" : String.join(", ", ctx.violating);
        String pList = joinStateNames(ctx.pStates);
        String qList = joinStateNames(ctx.qStates);
        return "States satisfying p: " + pList + ". States satisfying q: " + qList +
            ". No path to q from: " + viol + ".";
    }

    private static String joinStateNames(Iterable<? extends StateInterface> states) {
        List<String> names = new ArrayList<>();
        for (StateInterface s : states) {
            names.add(s.getName());
        }
        return names.isEmpty() ? "-" : String.join(", ", names);
    }

    private static class CheckContext {
        private final List<PWSState> pStates;
        private final Set<StateInterface> qStates;
        private final List<String> violating;
        private final Map<StateInterface, List<StateInterface>> adjacency;

        private CheckContext(List<PWSState> pStates,
                             Set<StateInterface> qStates,
                             List<String> violating,
                             Map<StateInterface, List<StateInterface>> adjacency) {
            this.pStates = pStates;
            this.qStates = qStates;
            this.violating = violating;
            this.adjacency = adjacency;
        }
    }

    private static class ResponsePattern {
        private final BasicStateProposition p;
        private final BasicStateProposition q;

        private ResponsePattern(BasicStateProposition p, BasicStateProposition q) {
            this.p = p;
            this.q = q;
        }

        static ResponsePattern from(LTLParser.Node node) {
            if (!(node instanceof LTLParser.Unary g) || !"G".equals(g.op)) return null;
            if (!(g.child instanceof LTLParser.Binary imp) || !"->".equals(imp.op)) return null;
            if (!(imp.left instanceof LTLParser.Atom leftAtom)) return null;
            if (!(imp.right instanceof LTLParser.Unary f) || !"F".equals(f.op)) return null;
            if (!(f.child instanceof LTLParser.Atom rightAtom)) return null;
            BasicStateProposition p = parseAtom(leftAtom.name);
            BasicStateProposition q = parseAtom(rightAtom.name);
            if (p == null || q == null) return null;
            return new ResponsePattern(p, q);
        }

        private static BasicStateProposition parseAtom(String atom) {
            if (atom == null) return null;
            int dot = atom.indexOf('.');
            if (dot <= 0 || dot >= atom.length() - 1) return null;
            String machineId = atom.substring(0, dot).trim();
            String stateName = atom.substring(dot + 1).trim();
            if (machineId.isEmpty() || stateName.isEmpty()) return null;
            return new BasicStateProposition(machineId, stateName);
        }
    }
}
