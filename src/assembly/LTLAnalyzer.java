package assembly;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Simple semantic classifier for LTL ASTs produced by {@link LTLParser}.
 * Heuristics:
 * - If the AST contains any 'F' unary node or 'U' binary node => LIVENESS
 * - Else if the top-level node is 'G' or all temporal operators are 'G' => SAFETY
 * - Otherwise => OTHER
 */
public class LTLAnalyzer {
    /** Utility class; do not instantiate. */
    private LTLAnalyzer() {
    }

    /** Classification kinds for LTL formulas. */
    public enum Kind {
        /** Safety property. */
        SAFETY,
        /** Liveness property. */
        LIVENESS,
        /** Other or unclassified property. */
        OTHER
    }

    /**
     * Classifies an LTL AST by a simple heuristic.
     *
     * @param root AST root
     * @return classification kind
     */
    public static Kind classify(LTLParser.Node root) {
        if (root == null) return Kind.OTHER;
        // Depth-first search
        Deque<LTLParser.Node> stack = new ArrayDeque<>();
        stack.push(root);
        boolean foundF = false;
        boolean foundU = false;
        boolean foundTemporalOther = false; // G or X count
        while (!stack.isEmpty()) {
            LTLParser.Node n = stack.pop();
            if (n instanceof LTLParser.Unary u) {
                String op = u.op;
                if ("F".equals(op)) foundF = true;
                else if ("G".equals(op) || "X".equals(op) || "!".equals(op)) {
                    if (!"G".equals(op)) foundTemporalOther = true;
                }
                stack.push(u.child);
            } else if (n instanceof LTLParser.Binary b) {
                String op = b.op;
                if ("U".equals(op)) foundU = true;
                else if ("R".equals(op)) foundTemporalOther = true;
                stack.push(b.left);
                stack.push(b.right);
            } else if (n instanceof LTLParser.Atom) {
                // leaf
            }
        }

        if (foundF || foundU) return Kind.LIVENESS;

        // If root is G and no liveness operators found => safety
        if (root instanceof LTLParser.Unary u && "G".equals(u.op)) return Kind.SAFETY;

        // Otherwise, heuristically classify as OTHER
        return Kind.OTHER;
    }
}
