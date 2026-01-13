package assembly;

import smalgebra.SMProposition;

import java.util.List;

/** Pair of guard proposition and actions to emit. */
public class GuardActionsPair {
    private SMProposition guard;
    private List<Action> actions;

    /**
     * Creates a guard/actions pair.
     *
     * @param guard guard proposition
     * @param actions actions to emit
     */
    public GuardActionsPair(SMProposition guard, List<Action> actions) {
        this.guard = guard;
        this.actions = actions;
    }

    /**
     * Returns the guard proposition.
     *
     * @return guard proposition
     */
    public SMProposition getGuard() {
        return guard;
    }

    /**
     * Returns the actions to emit.
     *
     * @return actions to emit
     */
    public List<Action> getActions() {
        return actions;
    }

    @Override
    public String toString() {
        return "[" + guard + "] 〈" + String.join(", ", actions.stream().map(Action::toString).toArray(String[]::new)) + "〉";
    }
}
