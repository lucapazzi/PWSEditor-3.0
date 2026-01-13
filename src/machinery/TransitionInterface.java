package machinery;

import java.awt.*;
import java.io.Serializable;

/** Interface for a transition between two states. */
public interface TransitionInterface extends Serializable {
    /**
     * Returns the source state.
     *
     * @return source state
     */
    StateInterface getSource();

    /**
     * Returns the target state.
     *
     * @return target state
     */
    StateInterface getTarget();

    /**
     * Returns whether the transition is autonomous (no trigger event).
     *
     * @return true if the transition is autonomous (no trigger event)
     */
    boolean isAutonomous();

    /**
     * Se la transizione è triggerable, restituisce il trigger event associato;
     * altrimenti, può restituire null o una stringa vuota.
     */
    String getTriggerEvent();

    /**
     * Sets the trigger event for a non-autonomous transition.
     *
     * @param event trigger event name
     */
    void setTriggerEvent(String event);

    /**
     * Fires the transition and updates internal state if needed.
     */
    void fire();

    /**
     * Metodo di utilità: restituisce true se la transizione è triggerable,
     * ossia non è autonoma e il trigger event non è null né vuoto.
     */
    default boolean isTriggerable() {
        return !isAutonomous() && getTriggerEvent() != null && !getTriggerEvent().isEmpty();
    }

    /**
     * Sets the visual offset used to render the trigger label.
     *
     * @param point offset point
     */
    void setTriggerOffset(Point point);

    /**
     * Returns the trigger label offset for rendering.
     *
     * @return trigger label offset for rendering
     */
    Point getTriggerOffset();
}
