package machinery;

import java.awt.*;
import java.util.UUID;

/** Transition between two states with trigger/guard metadata and geometry. */
@SuppressWarnings("this-escape")
public class Transition implements TransitionInterface {
    private static final long serialVersionUID = 1L;

    private String id;

    private StateInterface source;
    private StateInterface target;

    private boolean autonomous;
    private String triggerEvent;
    private Point controlPoint;

    // Field for control handle
    private Point triggerOffset;
    
    // Whether this transition is enabled (disabled transitions are ignored in semantics)
    private boolean enabled = true;
    private boolean timeoutTransition = false;

    public Transition() { }

    public Transition(StateInterface source,
                      StateInterface target,
                      boolean autonomous,
                      String triggerEvent) {
        this.id = UUID.randomUUID().toString();
        this.source = source;
        this.target = target;
        this.autonomous = autonomous;
        this.triggerEvent = triggerEvent;
        // Compute the default control point.
        if (source != null && target != null) {
            int defaultRadius = 25;
            Point centerSource = new Point(((State) source).getPosition().x + defaultRadius,
                    ((State) source).getPosition().y + defaultRadius);
            Point centerTarget = new Point(((State) target).getPosition().x + defaultRadius,
                    ((State) target).getPosition().y + defaultRadius);
            int midX = (centerSource.x + centerTarget.x) / 2;
            int midY = (centerSource.y + centerTarget.y) / 2;
            int offset = 20;
            double dx = centerTarget.x - centerSource.x;
            double dy = centerTarget.y - centerSource.y;
            double distance = Math.sqrt(dx * dx + dy * dy);
            if (distance == 0) distance = 1;
            int controlX = (int) (midX - offset * (dy / distance));
            int controlY = (int) (midY + offset * (dx / distance));
            this.controlPoint = new Point(controlX, controlY);
        }
        if (source != null) {
            source.addOutgoingTransition(this);
        }
        if (target != null) {
            target.addIncomingTransition(this);
        }
    }

    // 3-argument constructor
    public Transition(StateInterface source, StateInterface target, boolean autonomous) {
        this(source, target, autonomous, "");
    }

    public String getId() {
        return id;
    }

    /** Sets a specific id (used during deserialization). */
    public void setId(String id) {
        if (id == null || id.trim().isEmpty()) return;
        this.id = id;
    }

    @Override
    public StateInterface getSource() {
        return source;
    }

    public void setSource(StateInterface source) {
        if (this.source != null && this.source.getOutgoingTransitions() != null) {
            this.source.getOutgoingTransitions().remove(this);
        }
        this.source = source;
        if (this.source != null) {
            this.source.addOutgoingTransition(this);
        }
    }

    @Override
    public StateInterface getTarget() {
        return target;
    }

    public void setTarget(StateInterface target) {
        if (this.target != null && this.target.getIncomingTransitions() != null) {
            this.target.getIncomingTransitions().remove(this);
        }
        this.target = target;
        if (this.target != null) {
            this.target.addIncomingTransition(this);
        }
    }

    @Override
    public boolean isAutonomous() {
        return autonomous;
    }

    @Override
    public String getTriggerEvent() {
        return triggerEvent;
    }

    @Override
    public void setTriggerEvent(String event) {
        this.triggerEvent = event;
    }

    @Override
    public void fire() {
        System.out.println("Transition fired: " + source.getName() + " -> " + target.getName());
    }

    @Override
    public Point getTriggerOffset() {
        return triggerOffset;
    }

    @Override
    public void setTriggerOffset(Point offset) {
        this.triggerOffset = offset;
    }

    public Point getControlPoint() {
        // If controlPoint is already set, return it.
        if (controlPoint != null) {
            return controlPoint;
        }
        // Otherwise, compute the default control point
        if (source != null && target != null) {
            int defaultRadius = 25;
            Point centerSource = new Point(((State) source).getPosition().x + defaultRadius,
                    ((State) source).getPosition().y + defaultRadius);
            Point centerTarget = new Point(((State) target).getPosition().x + defaultRadius,
                    ((State) target).getPosition().y + defaultRadius);
            int midX = (centerSource.x + centerTarget.x) / 2;
            int midY = (centerSource.y + centerTarget.y) / 2;
            int offset = 20;
            double dx = centerTarget.x - centerSource.x;
            double dy = centerTarget.y - centerSource.y;
            double distance = Math.sqrt(dx * dx + dy * dy);
            if (distance == 0) distance = 1;
            int controlX = (int) (midX - offset * (dy / distance));
            int controlY = (int) (midY + offset * (dx / distance));
            controlPoint = new Point(controlX, controlY);
        }
        return controlPoint;
    }

    public void setControlPoint(Point controlPoint) {
        this.controlPoint = controlPoint;
    }
    
    /**
     * Returns whether this transition is enabled.
     * Disabled transitions are rendered differently and excluded from semantics computation.
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Sets whether this transition is enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Returns whether this transition is a timeout transition. */
    public boolean isTimeoutTransition() {
        return timeoutTransition;
    }

    /**
     * Marks this transition as a timeout transition.
     * Timeout transitions have no trigger label.
     */
    public void setTimeoutTransition(boolean timeoutTransition) {
        this.timeoutTransition = timeoutTransition;
        if (timeoutTransition) {
            this.triggerEvent = "";
        }
    }
}
