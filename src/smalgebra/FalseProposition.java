package smalgebra;

import assembly.AssemblyInterface;

/** Proposition that always evaluates to false. */
public class FalseProposition implements SMProposition {
    private static final long serialVersionUID = 1L;

    /** Creates a false proposition. */
    public FalseProposition() {
    }

    @Override
    public boolean evaluate(AssemblyInterface assembly) {
        return false;
    }

    @Override
    public String toString() {
        return "FALSE";
    }

    @Override
    public SMProposition clone() {
        return new FalseProposition();
    }
}
