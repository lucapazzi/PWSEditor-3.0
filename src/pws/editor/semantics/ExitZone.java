package pws.editor.semantics;

import machinery.Transition;
import machinery.StateInterface;
import machinery.StateMachine;
import assembly.AssemblyInterface;
import smalgebra.BasicStateProposition;

import java.io.Serializable;
import java.util.Objects;

/**
 * An ExitZone represents one autonomous-transition boundary crossing from a
 * PWSState's reactive semantics.
 *
 * <p>Each ExitZone records:
 * <ul>
 *   <li><b>stateMachineId</b>: the ID of the component state machine where the autonomous transition occurs;</li>
 *   <li><b>transition</b>: the specific autonomous transition object;</li>
 *   <li><b>source</b>: a BasicStateProposition naming the machine and source state, indicating
 *       the condition under which the autonomous transition fires;</li>
 *   <li><b>target</b>: a BasicStateProposition naming the machine and target state, indicating
 *       the machine-level destination that would take the current configuration
 *       outside the state's allowed constraint domain.</li>
 * </ul>
 *
 * <p>In computing reactive transitions, we match an ExitZone’s target proposition against
 * the guard proposition of a PWSTransition.  Whenever they coincide, we apply an
 * transformation of the eligible source semantics by invoking
 * {@code stateSemantics.transformByMachineTransition(...)} with the zone’s machineId
 * and transition. Multiple matching zones are OR-ed together to yield the transition’s
 * contribution to the target state’s overall semantics.
 */

public class ExitZone implements Serializable {
    private static final long serialVersionUID = 1L;
    private String stateMachineId = null;
    private Transition transition = null;
    private BasicStateProposition source = null;
    private BasicStateProposition target = null;

    /**
     * Creates an exit-zone for an autonomous transition.
     *
     * @param stateMachineId machine identifier
     * @param transition autonomous transition
     * @param source source proposition
     * @param target target proposition
     */
    public ExitZone(String stateMachineId, Transition transition, BasicStateProposition source, BasicStateProposition target) {
        this.stateMachineId = stateMachineId;
        this.transition = transition;
        this.source = source;
        this.target = target;
    }

    /**
     * Returns the machine identifier.
     *
     * @return machine identifier
     */
    public String getStateMachineId() {
        return stateMachineId;
    }

    /**
     * Sets the machine identifier.
     *
     * @param stateMachineId machine identifier
     */
    public void setStateMachineId(String stateMachineId) {
        this.stateMachineId = stateMachineId;
    }

    /**
     * Sets the autonomous transition.
     *
     * @param transition autonomous transition
     */
    public void setTransition(Transition transition) {
        this.transition = transition;
    }

    /**
     * Returns the autonomous transition.
     *
     * @return autonomous transition
     */
    public Transition getTransition() {
        return transition;
    }

    /**
     * Sets the source proposition.
     *
     * @param source source proposition
     */
    public void setSource(BasicStateProposition source) {
        this.source = source;
    }

    /**
     * Sets the target proposition.
     *
     * @param target target proposition
     */
    public void setTarget(BasicStateProposition target) {
        this.target = target;
    }

    /**
     * Returns the source proposition.
     *
     * @return source proposition
     */
    public BasicStateProposition getSource() {
        return source;
    }

    /**
     * Returns the target proposition.
     *
     * @return target proposition
     */
    public BasicStateProposition getTarget() {
        return target;
    }

    /**
     * Returns true if the source proposition still matches a state in the assembly.
     */
    public boolean hasMatchingSourceState(AssemblyInterface assembly) {
        if (source == null) {
            return false;
        }
        if (assembly == null) {
            return true; // can't verify without assembly; avoid false positives
        }
        StateMachine machine = assembly.getStateMachines().get(source.getMachineId());
        if (machine == null || machine.getStates() == null) {
            return false;
        }
        for (StateInterface s : machine.getStates()) {
            if (s != null && source.getStateName().equals(s.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the exit zone refers to a source state that no longer exists.
     */
    public boolean isOrphanSource(AssemblyInterface assembly) {
        return !hasMatchingSourceState(assembly);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ExitZone exitZone)) return false;
        return Objects.equals(stateMachineId, exitZone.stateMachineId) && Objects.equals(transition, exitZone.transition) && Objects.equals(source, exitZone.source) && Objects.equals(target, exitZone.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stateMachineId, transition, source, target);
    }

    @Override
    public String toString() {
        // return source.getMachineId() + ":" + " (" + source.getStateName() + "->" + target.getStateName() + ")";
        // return source.toString() + "⧴" + target.toString();
        return target.toString();
    }
}
