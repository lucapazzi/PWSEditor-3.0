package assembly;

import java.io.Serializable;

/** Action emitted by a machine, identified by machine id and event name. */
public class Action implements Serializable {
    private static final long serialVersionUID = 1L;
    private String id;
    private String event;

    /**
     * Creates an action for a machine and event.
     *
     * @param id machine identifier
     * @param event event name
     */
    public Action(String id, String event) {
        this.id = id;
        this.event = event;
    }

    /**
     * Returns the machine identifier that emits the action.
     *
     * @return machine identifier that emits the action
     */
    public String getMachineId() {
        return id;
    }

    /**
     * Returns the event name associated with the action.
     *
     * @return event name associated with the action
     */
    public String getEvent() {
        return event;
    }

    @Override
    public String toString() {
        return id + "." + event;
    }
}
