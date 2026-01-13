package assembly;

import machinery.StateMachine;
import pws.editor.semantics.Semantics;
import smalgebra.BasicStateProposition;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/** Defines the core API for assemblies and their analysis. */
public interface AssemblyInterface extends Serializable {
    /**
     * Returns the state machines keyed by identifier.
     *
     * @return map of state machines by identifier
     */
    Map<String, StateMachine> getStateMachines();

    /**
     * Adds a state machine to the assembly.
     *
     * @param identifier machine id
     * @param machine state machine instance
     */
    void addStateMachine(String identifier, StateMachine machine);

    /**
     * Restituisce tutte le assembly concrete generate (cioè, tutte le configurazioni possibili,
     * ottenute variando il current state di ciascuna macchina).
     *
     * @return elenco delle assembly concrete generate
     */
    List<AssemblyInterface> getAllConcreteAssemblies();

    /**
     * Returns the initial semantics for the assembly pseudostate.
     *
     * @return initial semantics for the assembly pseudostate
     */
    Semantics calculateInitialStateSemantics();

    /**
     * Returns all guard propositions in the assembly alphabet.
     *
     * @return all guard propositions in the assembly alphabet
     */
    List<BasicStateProposition> getAssemblyGuards();

    /**
     * Returns all action tokens in the assembly alphabet.
     *
     * @return all action tokens in the assembly alphabet
     */
    List<Action> getAssemblyActions();
    /**
     * Returns stored LTL formulas, if any.
     *
     * @return stored LTL formulas, if any
     */
    java.util.List<LTLFormula> getLTLFormulas();

    /**
     * Adds an LTL formula to the assembly.
     *
     * @param f formula to add
     */
    void addLTLFormula(LTLFormula f);

    /**
     * Removes an LTL formula from the assembly.
     *
     * @param f formula to remove
     */
    void removeLTLFormula(LTLFormula f);
}
