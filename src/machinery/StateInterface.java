package machinery;

import java.awt.*;
import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

/** Interface for a state node in a state machine. */
public interface StateInterface extends Serializable {
    /**
     * Returns the state name.
     *
     * @return state name
     */
    String getName();

    /**
     * Sets the state name.
     *
     * @param newName new name
     */
    void setName(String newName);
    /**
     * Returns outgoing transitions.
     *
     * @return outgoing transitions
     */
    List<TransitionInterface> getOutgoingTransitions();

    /**
     * Returns incoming transitions.
     *
     * @return incoming transitions
     */
    List<TransitionInterface> getIncomingTransitions();

    /**
     * Adds an outgoing transition.
     *
     * @param transition transition to add
     */
    void addOutgoingTransition(TransitionInterface transition);

    /**
     * Adds an incoming transition.
     *
     * @param transition transition to add
     */
    void addIncomingTransition(TransitionInterface transition);

    /**
     * Returns the state position for layout.
     *
     * @return state position for layout
     */
    Point getPosition();

    /**
     * Sets the state position.
     *
     * @param p new position
     */
    void setPosition(Point p);

    default List<TransitionInterface> getTriggerableOutgoingTransitions() {
        return getOutgoingTransitions().stream()
                .filter(TransitionInterface::isTriggerable)
                .collect(Collectors.toList());
    }


    default List<TransitionInterface> getAutonomousOutgoingTransitions() {
        return getOutgoingTransitions().stream()
                .filter(TransitionInterface::isAutonomous)
                .collect(Collectors.toList());
    }


    default List<TransitionInterface> getTriggerableIncomingTransitions() {
        return getIncomingTransitions().stream()
                .filter(TransitionInterface::isTriggerable)
                .collect(Collectors.toList());
    }

    default List<TransitionInterface> getAutonomousIncomingTransitions() {
        return getIncomingTransitions().stream()
                .filter(TransitionInterface::isAutonomous)
                .collect(Collectors.toList());
    }
}
