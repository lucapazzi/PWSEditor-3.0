package machinery;

import java.awt.*;
import java.io.Serializable;
import java.util.*;
import java.util.List;

/** Mutable state machine with states, transitions, and current state. */
public class StateMachine implements StateMachineInterface, Cloneable {
    private String name;
    protected List<StateInterface> states;
    protected List<TransitionInterface> transitions;
    private StateInterface currentState;
    private Set<String> events;
    protected StateInterface pseudoState; // Initial pseudo-state

    public StateMachine(String name) {
        this.name = name;
        this.states = new ArrayList<>();
        this.transitions = new ArrayList<>();
        this.events = new HashSet<>();
        // Position adjusted so the pseudo-state is visible (e.g., (20,20)).
        this.pseudoState = new State("PseudoState", new Point(20, 20));
        this.states.add(pseudoState);
    }

    public StateMachine(String name,
                        List<StateInterface> states,
                        List<TransitionInterface> transitions,
                        StateInterface currentState,
                        Set<String> events) {
        this.name = name;
        this.states = (states != null) ? states : new ArrayList<>();
        this.transitions = (transitions != null) ? transitions : new ArrayList<>();
        this.currentState = currentState;
        this.events = (events != null) ? events : new HashSet<>();
        boolean foundPseudo = false;
        for (StateInterface s : this.states) {
            if (s.getName().equals("PseudoState")) {
                foundPseudo = true;
                this.pseudoState = s;
                break;
            }
        }
        if (!foundPseudo) {
            this.pseudoState = new State("PseudoState", new Point(20, 20));
            this.states.add(this.pseudoState);
        }
    }

    // Getters/setters and other methods remain unchanged.
    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<StateInterface> getInitialStates() {
        List<StateInterface> initialStates = new ArrayList<>();
        for (TransitionInterface t : transitions) {
            if (t.getSource() == pseudoState
                    && isTransitionEnabled(t)
                    && (t.isAutonomous() || "_init".equals(t.getTriggerEvent()))) {
                initialStates.add(t.getTarget());
            }
        }
        return initialStates;
    }

    private boolean isTransitionEnabled(TransitionInterface t) {
        if (t instanceof Transition trans) {
            return trans.isEnabled();
        }
        return true;
    }

    @Override
    public void addState(StateInterface state) {
        states.add(state);
    }

    @Override
    public void addTransition(TransitionInterface transition) {
        transitions.add(transition);
        rebuildEventsFromTransitions();
    }

    @Override
    public StateInterface getCurrentState() {
        return currentState;
    }

    @Override
    public void setCurrentState(StateInterface state) {
        this.currentState = state;
    }

//    @Override

    @Override
    public Set<String> getEvents() {
        rebuildEventsFromTransitions();
        return events;
    }

    @Override
    public void setName(String newName) {
        this.name = newName;
    }

    public List<StateInterface> getLogicalStates() {
        List<StateInterface> logicalStates = new ArrayList<>();
        for (StateInterface state : states) {
            if (state != this.pseudoState) {
                logicalStates.add(state);
            }
        }
        return logicalStates;
    }
    @Override
    public List<StateInterface> getStates() {
        return states;
    }

    public void setStates(List<StateInterface> states) {
        this.states = states;
    }

    @Override
    public List<TransitionInterface> getTransitions() {
        return transitions;
    }

    public void setTransitions(List<TransitionInterface> transitions) {
        this.transitions = (transitions != null) ? transitions : new ArrayList<>();
        rebuildEventsFromTransitions();
    }

    @Override
    public StateMachine clone() {
        // Create a new StateMachine with the same name.
        // Note: the constructor automatically creates a pseudoState.
        StateMachine clone = new StateMachine(this.getName());
        Map<StateInterface, StateInterface> stateMap = new HashMap<>();

        // --- Clone states ---
        for (StateInterface s : this.getStates()) {
            // Create a new state with the same name and a copy of its position.
            State newState = new State(s.getName(), new Point(s.getPosition()));
            stateMap.put(s, newState);
            clone.addState(newState);
        }

        // --- Clone transitions ---
        for (TransitionInterface t : this.getTransitions()) {
            // Get the cloned source and target states from the stateMap.
            StateInterface clonedSource = stateMap.get(t.getSource());
            StateInterface clonedTarget = stateMap.get(t.getTarget());
            // Create a new Transition with the same properties.
            Transition original = (Transition) t;
            Transition newTransition = new Transition(clonedSource, clonedTarget, t.isAutonomous(), t.getTriggerEvent());
            String originalId = original.getId();
            if (originalId != null) {
                newTransition.setId(originalId);
            }
            // Copy the controlPoint if it exists. This deep-copies the Point.
            Point originalControlPoint = original.getControlPoint();
            if (originalControlPoint != null) {
                newTransition.setControlPoint(new Point(originalControlPoint));
            }
            Point originalTriggerOffset = original.getTriggerOffset();
            if (originalTriggerOffset != null) {
                newTransition.setTriggerOffset(new Point(originalTriggerOffset));
            }
            newTransition.setEnabled(original.isEnabled());
            clone.addTransition(newTransition);

            // Update the incoming/outgoing relationships for the cloned states.
            if (clonedSource != null && clonedSource.getOutgoingTransitions() != null) {
                clonedSource.getOutgoingTransitions().add(newTransition);
            }
            if (clonedTarget != null && clonedTarget.getIncomingTransitions() != null) {
                clonedTarget.getIncomingTransitions().add(newTransition);
            }
        }

        // --- Clone events from transitions (avoid stale trigger cache) ---
        clone.rebuildEventsFromTransitions();

        // --- Fix duplicate pseudoState issue ---
        // The StateMachine constructor adds a default pseudoState that isn't part of the original.
        clone.getStates().remove(clone.pseudoState);
        if (stateMap.containsKey(this.pseudoState)) {
            clone.pseudoState = stateMap.get(this.pseudoState);
            if (!clone.getStates().contains(clone.pseudoState)) {
                clone.getStates().add(clone.pseudoState);
            }
        }

        return clone;
    }
    public void setEvents(Set<String> events) {
        this.events = (events != null) ? new HashSet<>(events) : new HashSet<>();
    }

    public StateInterface getPseudoState() {
        return pseudoState;
    }

    public void setPseudoState(StateInterface pseudoState) {
        this.pseudoState = pseudoState;
    }

    /**
     * Rebuilds the event set from the current triggerable transitions.
     * This keeps events consistent after trigger renames/deletions.
     */
    private void rebuildEventsFromTransitions() {
        if (events == null) {
            events = new HashSet<>();
        } else {
            events.clear();
        }
        if (transitions == null) {
            return;
        }
        for (TransitionInterface transition : transitions) {
            if (transition == null || !transition.isTriggerable()) {
                continue;
            }
            String trigger = transition.getTriggerEvent();
            if (trigger != null && !trigger.trim().isEmpty()) {
                events.add(trigger);
            }
        }
    }
}
