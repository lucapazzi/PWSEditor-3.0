package smalgebra;

import assembly.AssemblyInterface;

/**
 * Rappresenta la disgiunzione logica (OR) di due SMProposition.
 */
public class OrProposition implements SMProposition {
    private static final long serialVersionUID = 1L;
    private final SMProposition left;
    private final SMProposition right;

    /**
     * Creates a disjunction of two propositions.
     *
     * @param left left operand
     * @param right right operand
     */
    public OrProposition(SMProposition left, SMProposition right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean evaluate(AssemblyInterface assembly) {
        return left.evaluate(assembly) || right.evaluate(assembly);
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
        return "(" + left + " OR " + right + ")";
    }

    @Override
    public SMProposition clone() {
        return new OrProposition(this.left.clone(), this.right.clone());
    }
}
