package smalgebra;

import assembly.Assembly;
import assembly.AssemblyInterface;
import pws.editor.semantics.Configuration;
import pws.editor.semantics.Semantics;

import java.io.Serializable;
import java.util.Set;

public interface SMProposition extends Cloneable, Serializable {


    /**
     * Returns a deep copy of this proposition.
     *
     * @return a deep copy of this proposition
     */
    SMProposition clone();

    /**
     * Evaluates the proposition on the given assembly.
     *
     * @param assembly assembly context
     * @return true if the proposition holds
     */
    boolean evaluate(AssemblyInterface assembly);

    /**
     * Transforms the expression by replacing, for the given machine, state fromState with toState.
     * (Ontological validity of the expression is no longer checked.)
     *
     * @param machineId machine identifier
     * @param fromState state to replace
     * @param toState replacement state
     * @param assembly assembly context
     * @return transformed proposition
     */
    default SMProposition transform(String machineId, String fromState, String toState, AssemblyInterface assembly) {
        if (this instanceof BasicStateProposition) {
            BasicStateProposition bsp = (BasicStateProposition) this;
            if (bsp.getMachineId().equals(machineId) && bsp.getStateName().equals(fromState)) {
                return new BasicStateProposition(machineId, toState);
            } else {
                return bsp;
            }
        } else if (this instanceof AndProposition) {
            AndProposition ap = (AndProposition) this;
            SMProposition newLeft = ap.getLeft().transform(machineId, fromState, toState, assembly);
            SMProposition newRight = ap.getRight().transform(machineId, fromState, toState, assembly);
            return new AndProposition(newLeft, newRight);
        } else if (this instanceof OrProposition) {
            OrProposition op = (OrProposition) this;
            SMProposition newLeft = op.getLeft().transform(machineId, fromState, toState, assembly);
            SMProposition newRight = op.getRight().transform(machineId, fromState, toState, assembly);
            return new OrProposition(newLeft, newRight);
        } else if (this instanceof NotProposition) {
            NotProposition np = (NotProposition) this;
            SMProposition newProp = np.getProposition().transform(machineId, fromState, toState, assembly);
            return new NotProposition(newProp);
        } else {
            return this;
        }
    }

    /**
     * Ontologically, A ontoImplies B if for every configuration where A is true, B is true.
     *
     * @param other other proposition
     * @param assembly assembly context
     * @return true if this ontologically implies other
     */
    default boolean ontoImplies(SMProposition other, AssemblyInterface assembly) {
        for (AssemblyInterface conf : assembly.getAllConcreteAssemblies()) {
            if (this.evaluate(conf) && !other.evaluate(conf)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Ontologically, A ontoEquiv B if A ontoImplies B and B ontoImplies A.
     *
     * @param other other proposition
     * @param assembly assembly context
     * @return true if propositions are ontologically equivalent
     */
    default boolean ontoEquiv(SMProposition other, AssemblyInterface assembly) {
        return this.ontoImplies(other, assembly) && other.ontoImplies(this, assembly);
    }

    default SMProposition andBSP(BasicStateProposition bsp) {
        return new AndProposition(bsp, this);
    }

    default SMProposition negate() {
        return new NotProposition(this);
    }

    /**
     * Ontologically, A ontoEquiv B if A ontoImplies B and B ontoImplies A.
     *
     * @param other other proposition
     * @param assembly assembly context
     * @return true if propositions are ontologically equivalent
     */
    default boolean ontoEquiv(SMProposition other, Assembly assembly) {
        return this.ontoImplies(other, assembly) && other.ontoImplies(this, assembly);
    }

    /**
     * Converts the expression to negative normal form (NNF),
     * where negations appear only directly in front of atoms.
     *
     * @return proposition in NNF
     */
    default SMProposition toNNF() {
        if (this instanceof BasicStateProposition) {
            return this;
        } else if (this instanceof NotProposition) {
            SMProposition inner = ((NotProposition) this).getProposition();
            if (inner instanceof NotProposition) {
                // double negation: ¬(¬A) = A
                return ((NotProposition) inner).getProposition().toNNF();
            } else if (inner instanceof AndProposition) {
                // ¬(A ∧ B) = ¬A ∨ ¬B
                SMProposition left = new NotProposition(((AndProposition) inner).getLeft()).toNNF();
                SMProposition right = new NotProposition(((AndProposition) inner).getRight()).toNNF();
                return new OrProposition(left, right);
            } else if (inner instanceof OrProposition) {
                // ¬(A ∨ B) = ¬A ∧ ¬B
                SMProposition left = new NotProposition(((OrProposition) inner).getLeft()).toNNF();
                SMProposition right = new NotProposition(((OrProposition) inner).getRight()).toNNF();
                return new AndProposition(left, right);
            } else {
                return new NotProposition(inner.toNNF());
            }
        } else if (this instanceof AndProposition) {
            SMProposition left = ((AndProposition) this).getLeft().toNNF();
            SMProposition right = ((AndProposition) this).getRight().toNNF();
            return new AndProposition(left, right);
        } else if (this instanceof OrProposition) {
            SMProposition left = ((OrProposition) this).getLeft().toNNF();
            SMProposition right = ((OrProposition) this).getRight().toNNF();
            return new OrProposition(left, right);
        }
        return this; // default
    }

    /**
     * Converts the expression to conjunctive normal form (CNF).
     *
     * @return proposition in CNF
     */
    default SMProposition toCNF() {
        SMProposition nnf = this.toNNF();
        return distributeOrOverAnd(nnf);
    }

    /**
     * Converts the expression to disjunctive normal form (DNF).
     *
     * @return proposition in DNF
     */
    default SMProposition toDNF() {
        SMProposition nnf = this.toNNF();
        return distributeAndOverOr(nnf);
    }

    /**
     * Distributes OR over AND to obtain CNF.
     * Implements the rule: A ∨ (B ∧ C) = (A ∨ B) ∧ (A ∨ C)
     *
     * @param expr expression to transform
     * @return transformed expression
     */
    static SMProposition distributeOrOverAnd(SMProposition expr) {
        if (expr instanceof OrProposition) {
            SMProposition left = distributeOrOverAnd(((OrProposition) expr).getLeft());
            SMProposition right = distributeOrOverAnd(((OrProposition) expr).getRight());
            // If either side is a conjunction, apply distribution.
            if (left instanceof AndProposition) {
                SMProposition a = ((AndProposition) left).getLeft();
                SMProposition b = ((AndProposition) left).getRight();
                return new AndProposition(
                        distributeOrOverAnd(new OrProposition(a, right)),
                        distributeOrOverAnd(new OrProposition(b, right))
                );
            } else if (right instanceof AndProposition) {
                SMProposition a = ((AndProposition) right).getLeft();
                SMProposition b = ((AndProposition) right).getRight();
                return new AndProposition(
                        distributeOrOverAnd(new OrProposition(left, a)),
                        distributeOrOverAnd(new OrProposition(left, b))
                );
            } else {
                return new OrProposition(left, right);
            }
        } else if (expr instanceof AndProposition) {
            SMProposition left = distributeOrOverAnd(((AndProposition) expr).getLeft());
            SMProposition right = distributeOrOverAnd(((AndProposition) expr).getRight());
            return new AndProposition(left, right);
        }
        // For NotProposition and BasicStateProposition, distribution does not change anything.
        return expr;
    }

    /**
     * Distributes AND over OR to obtain DNF.
     * Implements the rule: A ∧ (B ∨ C) = (A ∧ B) ∨ (A ∧ C)
     *
     * @param expr expression to transform
     * @return transformed expression
     */
    static SMProposition distributeAndOverOr(SMProposition expr) {
        if (expr instanceof AndProposition) {
            SMProposition left = distributeAndOverOr(((AndProposition) expr).getLeft());
            SMProposition right = distributeAndOverOr(((AndProposition) expr).getRight());
            // If either side is a disjunction, apply distribution.
            if (left instanceof OrProposition) {
                SMProposition a = ((OrProposition) left).getLeft();
                SMProposition b = ((OrProposition) left).getRight();
                return new OrProposition(
                        distributeAndOverOr(new AndProposition(a, right)),
                        distributeAndOverOr(new AndProposition(b, right))
                );
            } else if (right instanceof OrProposition) {
                SMProposition a = ((OrProposition) right).getLeft();
                SMProposition b = ((OrProposition) right).getRight();
                return new OrProposition(
                        distributeAndOverOr(new AndProposition(left, a)),
                        distributeAndOverOr(new AndProposition(left, b))
                );
            } else {
                return new AndProposition(left, right);
            }
        } else if (expr instanceof OrProposition) {
            SMProposition left = distributeAndOverOr(((OrProposition) expr).getLeft());
            SMProposition right = distributeAndOverOr(((OrProposition) expr).getRight());
            return new OrProposition(left, right);
        }
        // For NotProposition and BasicStateProposition, distribution does not change anything.
        return expr;
    }

    /**
     * Evaluates the SMProposition on a given fully-specified configuration by creating an ad hoc Assembly.
     * It creates an Assembly with the assemblyId from the configuration and sets each machine's current state
     * according to the BasicStatePropositions in the configuration, then calls evaluate(AssemblyInterface).
     *
     * @param config configuration to evaluate
     * @param properAssembly assembly context with state machines
     * @return true if the proposition holds for the configuration
     */
    default boolean evaluateConfiguration(Configuration config, AssemblyInterface properAssembly) {
        // Use the provided fully-initialized assembly instead of creating a new one.
        AssemblyInterface adHocAssembly = properAssembly; // .clone(); // Or properAssembly, if clone is not needed.

        // For each BasicStateProposition in the configuration, set the corresponding machine's current state.
        for (BasicStateProposition bsp : config.getBasicStatePropositions()) {
            machinery.StateMachine machine = adHocAssembly.getStateMachines().get(bsp.getMachineId());
            if (machine != null) {
                for (machinery.StateInterface state : machine.getStates()) {
                    if (state.getName().equals(bsp.getStateName())) {
                        machine.setCurrentState(state);
                        break;
                    }
                }
            }
        }

        // Evaluate the proposition on the ad hoc Assembly.
        return evaluate(adHocAssembly);
    }

    /**
     * Converts this SMProposition into a Semantics object by evaluating it over
     * the universe of fully-specified configurations generated from the provided Assembly.
     * Only those configurations for which the proposition evaluates to true are included.
     *
     * @param assembly the Assembly instance used to generate the universe of configurations.
     * @return a Semantics object representing the set of configurations where this proposition holds.
     */
    default Semantics toSemantics(Assembly assembly) {
        Semantics result = new Semantics(assembly.getAssemblyId());
        // Generate the full universe of configurations from the Assembly.
        Set<Configuration> universe = assembly.generateUniverse();
        for (Configuration config : universe) {
            // Evaluate the proposition on the configuration using evaluateConfiguration.
            if (this.evaluateConfiguration(config, assembly)) {
                result.addConfiguration(config);
            }
        }
        return result;
    }
}
