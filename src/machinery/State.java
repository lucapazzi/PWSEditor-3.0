package machinery;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** State node with position and incoming/outgoing transitions. */
public class State implements StateInterface {
    private static final long serialVersionUID = 1L;
    private String name;
    private ArrayList<TransitionInterface> outgoingTransitions;
    private ArrayList<TransitionInterface> incomingTransitions;

    private Point position;
    private boolean failState = false;
    private boolean timedState = false;
    private String timeoutLabel = "T";
    private TimedBadgePosition timedBadgePosition = TimedBadgePosition.TOP;

    public State() {
        this.outgoingTransitions = new ArrayList<>();
        this.incomingTransitions = new ArrayList<>();
    }

    public State(String name,
                 Point position) {
        this.name = name;
        this.position = position;
        this.outgoingTransitions = new ArrayList<>();
        this.incomingTransitions = new ArrayList<>();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String newName) {
        this.name = newName;
    }

    @Override
    public List<TransitionInterface> getOutgoingTransitions() {
        return outgoingTransitions;
    }

    @Override
    public List<TransitionInterface> getIncomingTransitions() {
        return incomingTransitions;
    }

    @Override
    public void addOutgoingTransition(TransitionInterface transition) {
        if (!outgoingTransitions.contains(transition)) {
            outgoingTransitions.add(transition);
        }
    }

    @Override
    public void addIncomingTransition(TransitionInterface transition) {
        if (!incomingTransitions.contains(transition)) {
            incomingTransitions.add(transition);
        }
    }

    @Override
    public Point getPosition() {
        return position;
    }

    @Override
    public void setPosition(Point p) {
        this.position = p;
    }

    public boolean isFailState() {
        return failState;
    }

    public void setFailState(boolean failState) {
        this.failState = failState;
    }

    public boolean isTimedState() {
        return timedState;
    }

    public void setTimedState(boolean timedState) {
        this.timedState = timedState;
    }

    public String getTimeoutLabel() {
        return timeoutLabel == null || timeoutLabel.isBlank() ? "T" : timeoutLabel;
    }

    public void setTimeoutLabel(String timeoutLabel) {
        if (timeoutLabel == null || timeoutLabel.isBlank()) {
            this.timeoutLabel = "T";
        } else {
            this.timeoutLabel = timeoutLabel.trim();
        }
    }

    public TimedBadgePosition getTimedBadgePosition() {
        return timedBadgePosition == null ? TimedBadgePosition.TOP : timedBadgePosition;
    }

    public void setTimedBadgePosition(TimedBadgePosition timedBadgePosition) {
        this.timedBadgePosition = timedBadgePosition == null ? TimedBadgePosition.TOP : timedBadgePosition;
    }
}
