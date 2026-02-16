package smalgebra;

import assembly.AssemblyInterface;

public class TrueProposition implements SMProposition {
    private static final long serialVersionUID = 1L;

    @Override
    public boolean evaluate(AssemblyInterface assembly) {
        return true;
    }

    @Override
    public String toString() {
        return "TRUE";
    }

    @Override
    public SMProposition clone() {
        return new TrueProposition();
    }
}