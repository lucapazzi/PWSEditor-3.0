package smalgebra;

import assembly.Assembly;
import assembly.AssemblyInterface;

/**
 * Rappresenta la congiunzione logica (AND) di due SMProposition.
 */
public class AndProposition implements SMProposition {
    private static final long serialVersionUID = 1L;
    private final SMProposition left;
    private final SMProposition right;

    /**
     * Creates a conjunction of two propositions.
     *
     * @param left left operand
     * @param right right operand
     */
    public AndProposition(SMProposition left, SMProposition right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean evaluate(AssemblyInterface assembly) {
        return left.evaluate(assembly) && right.evaluate(assembly);
    }

    /**
     * Returns the left operand.
     *
     * @return left operand
     */
    public SMProposition getLeft() {
        return left;
    }

    /**
     * Returns the right operand.
     *
     * @return right operand
     */
    public SMProposition getRight() {
        return right;
    }

    @Override
    public String toString() {
        return "(" + left + " AND " + right + ")";
    }

    @Override
    public SMProposition clone() {
        return new AndProposition(this.left.clone(), this.right.clone());
    }
}
