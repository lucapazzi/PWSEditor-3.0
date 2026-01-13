package assembly;

import java.io.Serializable;

/** Action emitted by a machine, identified by machine id and event name. */
public class Action implements Serializable {
    private String id;
    private String event;

    public Action(String id, String event) {
        this.id = id;
        this.event = event;
    }

    public String getMachineId() {
        return id;
    }

    public String getEvent() {
        return event;
    }

    @Override
    public String toString() {
        return id + "." + event;
    }
}
