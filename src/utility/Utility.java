package utility;

import assembly.Action;
import assembly.ActionList;
import assembly.AssemblyInterface;
import machinery.StateInterface;
import machinery.StateMachine;
import machinery.TransitionInterface;
import smalgebra.*;

/** Utility methods for transforming propositions and extracting guards/actions. */
public class Utility {
    /**
     * Transforms an SMProposition by replacing, for a given machine, every occurrence
     * of state "fromState" with "toState".
     *
     * The transformation is applied recursively over the SMProposition structure:
     *
     *   - If the proposition is elementary (BasicStateProposition) and refers to the target
     *     machine and state, a new BasicStateProposition with the substituted state is returned.
     *   - If it is composed with boolean operators (AND, OR, NOT), the transformation is applied
     *     recursively to its sub-arguments.
     *
     * @param proposition the original SMProposition
     * @param machineId   identifier of the target machine
     * @param fromState   state to replace
     * @param toState     replacement state
     * @return a new SMProposition with the substitution applied
     */
    public static SMProposition transformByMachineIdAndState(SMProposition proposition, String machineId, String fromState, String toState) {
        if (proposition instanceof BasicStateProposition) {
            BasicStateProposition bsp = (BasicStateProposition) proposition;
            // If the elementary proposition refers to the target machine and state,
            // return a new elementary proposition with the new state.
            if (bsp.getMachineId().equals(machineId) && bsp.getStateName().equals(fromState)) {
                return new BasicStateProposition(machineId, toState);
            } else {
                return proposition;
            }
        } else if (proposition instanceof AndProposition) {
            AndProposition ap = (AndProposition) proposition;
            SMProposition newLeft = transformByMachineIdAndState(ap.getLeft(), machineId, fromState, toState);
            SMProposition newRight = transformByMachineIdAndState(ap.getRight(), machineId, fromState, toState);
            return new AndProposition(newLeft, newRight);
        } else if (proposition instanceof OrProposition) {
            OrProposition op = (OrProposition) proposition;
            SMProposition newLeft = transformByMachineIdAndState(op.getLeft(), machineId, fromState, toState);
            SMProposition newRight = transformByMachineIdAndState(op.getRight(), machineId, fromState, toState);
            return new OrProposition(newLeft, newRight);
        } else if (proposition instanceof NotProposition) {
            NotProposition np = (NotProposition) proposition;
            SMProposition newProp = transformByMachineIdAndState(np.getProposition(), machineId, fromState, toState);
            return new NotProposition(newProp);
        } else {
            // If the SMProposition is not recognized, return it unchanged.
            return proposition;
        }
    }

    /**
     * Transforms the base SMProposition by applying, for each action in actions, the following procedure:
     *
     * 1. For each action (e.g., "m1.e") in actions:
     *    1.1 Retrieve the corresponding state machine from the assembly using the machineId.
     *    1.2 For each state S in the state machine:
     *          1.2.1 If the state belongs to the proposition to transform:
     *              a) For each outgoing triggerable transition from S with the same trigger as the action,
     *                 transform pre to post via Utility.transformByMachineIdAndState.
     *
     * 2. If no action produces transformations, return base; otherwise return post.
     *
     * @param base the starting SMProposition
     * @param actions the list of actions to apply
     * @param assembly the assembly used to access state machines
     * @return the resulting SMProposition
     */
    public static SMProposition applyActions(SMProposition base, ActionList actions, AssemblyInterface assembly) {
        SMProposition workCopy  = base; // .clone();

        // For each action in the list
        for (Action a : actions) {
            // split id from event
            String machineId = a.getMachineId();
            String event = a.getEvent();

            // Retrieve the state machine from the assembly via the action id
            StateMachine machine = assembly.getStateMachines().get(machineId);
            if (machine == null) {
                continue; // If no state machines are associated with that id, skip to the next action
            }

            // For each state in the state machine
            for (StateInterface s : machine.getStates()) {
                // For each transition originating from that state
                // 1.2.1 If the state belongs to the proposition to transform
                BasicStateProposition bsp = new BasicStateProposition(machineId, s.getName());
                if (bsp.ontoImplies(base,assembly)) {
                    // then for each of its transitions that has event as trigger
                    for (TransitionInterface t : s.getOutgoingTransitions()) {
                        // if t has event as trigger
                        if (t.isTriggerable() && t.getTriggerEvent().equals(event)) {
                            // Transform pre to post using the transformer
                            workCopy = Utility.transformByMachineIdAndState(workCopy, machineId, t.getSource().getName(), t.getTarget().getName());
                            // Aggregate the result with logical OR: if result is null, post becomes result; otherwise,
                            // result = OR(result, post)
                        }
                    }
                }
            }
        }
        return workCopy;
    }


}
