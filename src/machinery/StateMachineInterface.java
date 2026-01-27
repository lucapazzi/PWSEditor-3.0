package machinery;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * Represents a state machine.
 */
public interface StateMachineInterface extends Serializable {
    /**
     * Returns the machine name.
     *
     * @return machine name
     */
    String getName();

    /**
     * Returns all states in creation order.
     *
     * @return all states in creation order
     */
    List<StateInterface> getStates();

    /**
     * Returns logical (non-pseudo) states.
     *
     * @return logical (non-pseudo) states
     */
    List<StateInterface> getLogicalStates();

    /**
     * Returns all transitions.
     *
     * @return all transitions
     */
    List<TransitionInterface> getTransitions();

    /**
     * Returns states targeted by transitions from the pseudostate.
     *
     * @return states targeted by transitions from the pseudostate
     */
    List<StateInterface> getInitialStates();

    /**
     * Adds a state to the machine.
     *
     * @param state state to add
     */
    void addState(StateInterface state);

    /**
     * Adds a transition to the machine.
     *
     * @param transition transition to add
     */
    void addTransition(TransitionInterface transition);

    /**
     * Returns the current state.
     *
     * @return current state
     */
    StateInterface getCurrentState();

    /**
     * Sets the current state.
     *
     * @param state new current state
     */
    void setCurrentState(StateInterface state);

    /**
     * Returns the set of events (triggers) associated with controllable transitions.
     */
    Set<String> getEvents();

    void setName(String newName);
}
