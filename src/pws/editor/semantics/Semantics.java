package pws.editor.semantics;

import assembly.Assembly;
import machinery.StateInterface;
import machinery.StateMachine;
import machinery.Transition;
import machinery.TransitionInterface;
import smalgebra.BasicStateProposition;
import smalgebra.FalseProposition;
import smalgebra.OrProposition;
import smalgebra.SMProposition;

import java.io.Serializable;
import java.util.*;

import static assembly.AssemblyGenerator.evaluateSMPropositionOverAllFeasibleAssemblies;

/**
 * Represents a semantic domain as a normalized set of {@link Configuration} objects.
 * <p>
 * A Semantics is associated with a specific assembly (identified by assemblyId) and maintains
 * a set of configurations that are automatically normalized: when adding a configuration,
 * more specific (subsumed) configurations are removed to keep only the most general ones.
 * <p>
 * This class supports standard lattice operations:
 * <ul>
 *   <li>{@link #OR(Semantics)} - union of two semantics</li>
 *   <li>{@link #AND(Semantics)} - intersection of two semantics</li>
 *   <li>{@link #NOT(Assembly)} - complement relative to an assembly's universe</li>
 *   <li>{@link #LEQ(Semantics, Assembly)} - semantic implication check</li>
 * </ul>
 * 
 * @see Configuration
 * @see Assembly
 */
public class Semantics implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final String assemblyId;
    private final Set<Configuration> configurations;

    // ==================== Constructors ====================

    /**
     * Creates an empty Semantics for the specified assembly.
     *
     * @param assemblyId the identifier of the assembly this Semantics belongs to
     */
    public Semantics(String assemblyId) {
        this.assemblyId = assemblyId;
        this.configurations = new HashSet<>();
    }

    // ==================== Accessors ====================

    /**
     * Returns the assembly identifier this Semantics is associated with.
     *
     * @return the assembly identifier
     */
    public String getAssemblyId() {
        return assemblyId;
    }

    /**
     * Returns the set of configurations in this Semantics.
     * <p>
     * Note: The returned set is the internal set; modifications will affect this Semantics.
     *
     * @return the set of configurations
     */
    public Set<Configuration> getConfigurations() {
        return configurations;
    }

    // ==================== Core Operations ====================

    /**
     * Adds a Configuration to this Semantics with automatic normalization.
     * <p>
     * The normalization ensures that:
     * <ul>
     *   <li>If the new configuration is more specific (implies) an existing configuration,
     *       it will not be added (subsumed by existing).</li>
     *   <li>If the new configuration is more general than existing configurations,
     *       those more specific configurations are removed.</li>
     * </ul>
     *
     * @param config the configuration to add
     * @return this Semantics instance (for method chaining)
     * @throws IllegalArgumentException if the configuration belongs to a different assembly
     */
    public Semantics addConfiguration(Configuration config) {
        if (!config.getAssemblyId().equals(this.assemblyId)) {
            throw new IllegalArgumentException("The configuration belongs to a different assembly.");
        }
        // Create a copy to iterate safely while potentially modifying the set
        Set<Configuration> toCheck = new HashSet<>(configurations);
        for (Configuration existing : toCheck) {
            if (config.implies(existing)) {
                // New configuration is more specific - don't add it
                return this;
            }
            if (existing.implies(config)) {
                // Existing is more specific - remove it
                configurations.remove(existing);
            }
        }
        configurations.add(config);
        return this;
    }

    // ==================== Lattice Operations ====================

    /**
     * Computes the union (OR) of this Semantics with another.
     * <p>
     * The result contains configurations from both Semantics, with automatic
     * normalization to remove redundant (more specific) configurations.
     *
     * @param other the other Semantics to union with
     * @return a new Semantics representing the union
     * @throws IllegalArgumentException if the Semantics belong to different assemblies
     */
    public Semantics OR(Semantics other) {
        validateSameAssembly(other);
        
        Set<Configuration> unionSet = new HashSet<>();
        unionSet.addAll(this.configurations);
        unionSet.addAll(other.configurations);

        // Minimize: remove configurations that are more specific than others
        Set<Configuration> minimized = new HashSet<>(unionSet);
        for (Configuration c1 : unionSet) {
            for (Configuration c2 : unionSet) {
                if (c1 != c2 && c1.implies(c2)) {
                    // c1 is more specific than c2, so remove c1
                    minimized.remove(c1);
                }
            }
        }
        
        Semantics result = new Semantics(this.assemblyId);
        for (Configuration c : minimized) {
            result.addConfiguration(c);
        }
        return result;
    }

    /**
     * Computes the intersection (AND) of this Semantics with another.
     * <p>
     * The result is computed pairwise for every configuration from both Semantics,
     * then normalized by removing redundant configurations.
     *
     * @param other the other Semantics to intersect with
     * @return a new Semantics representing the intersection
     * @throws IllegalArgumentException if the Semantics belong to different assemblies
     */
    public Semantics AND(Semantics other) {
        validateSameAssembly(other);
        
        Set<Configuration> interSet = new HashSet<>();
        for (Configuration c1 : this.configurations) {
            for (Configuration c2 : other.configurations) {
                Configuration cInter = c1.intersect(c2);
                if (cInter != null) {
                    interSet.add(cInter);
                }
            }
        }
        
        // Minimize: remove redundant configurations
        Set<Configuration> minimized = new HashSet<>(interSet);
        for (Configuration c1 : interSet) {
            for (Configuration c2 : interSet) {
                if (c1 != c2 && c1.implies(c2)) {
                    minimized.remove(c2);
                }
            }
        }
        
        Semantics result = new Semantics(this.assemblyId);
        for (Configuration c : minimized) {
            result.addConfiguration(c);
        }
        return result;
    }

    /**
     * Computes the complement (NOT) of this Semantics relative to the assembly's universe.
     * <p>
     * The complement consists of those configurations in the universe that do NOT
     * imply any configuration in this Semantics.
     *
     * @param assembly the Assembly instance used to generate the universe
     * @return a new Semantics representing the complement
     * @throws IllegalArgumentException if assembly ID doesn't match
     */
    public Semantics NOT(Assembly assembly) {
        validateSameAssembly(assembly);
        
        Set<Configuration> universe = assembly.generateUniverse();
        Semantics result = new Semantics(this.assemblyId);
        
        for (Configuration c : universe) {
            boolean isSatisfied = false;
            for (Configuration s : this.configurations) {
                if (c.implies(s)) {
                    isSatisfied = true;
                    break;
                }
            }
            if (!isSatisfied) {
                result.addConfiguration(c);
            }
        }
        return result;
    }

    /**
     * Computes the set difference of this Semantics minus another.
     * <p>
     * Equivalent to: this AND (NOT other)
     *
     * @param other    the Semantics to subtract
     * @param assembly the Assembly for computing the complement
     * @return a new Semantics representing the difference
     */
    public Semantics DIFF(Semantics other, Assembly assembly) {
        return this.AND(other.NOT(assembly));
    }

    // ==================== Implication Operations ====================

    /**
     * Determines whether this Semantics implies another Semantics.
     * <p>
     * This Semantics implies the other if every configuration in this Semantics
     * implies at least one configuration in the other Semantics.
     *
     * @param other the Semantics to compare against
     * @return true if this implies other; false otherwise
     * @throws IllegalArgumentException if the Semantics belong to different assemblies
     */
    public boolean implies(Semantics other) {
        validateSameAssembly(other);
        
        for (Configuration config : this.configurations) {
            boolean found = false;
            for (Configuration otherConf : other.configurations) {
                if (config.implies(otherConf)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determines whether this Semantics logically implies another using universal evaluation.
     * <p>
     * This method converts both Semantics to their SMProposition representations,
     * evaluates each over all feasible assemblies, and then compares the results.
     *
     * @param other    the Semantics to compare against
     * @param assembly the Assembly used to generate the universe of configurations
     * @return true if this implies other for all feasible configurations; false otherwise
     * @throws IllegalArgumentException if the Semantics belong to different assemblies
     */
    public boolean LEQ(Semantics other, Assembly assembly) {
        validateSameAssembly(other);
        
        SMProposition s1Prop = this.toSMProposition();
        SMProposition s2Prop = other.toSMProposition();

        Semantics semS1 = evaluateSMPropositionOverAllFeasibleAssemblies(assembly, s1Prop);
        Semantics semS2 = evaluateSMPropositionOverAllFeasibleAssemblies(assembly, s2Prop);

        return semS1.implies(semS2);
    }

    /**
     * Checks if two Semantics are equivalent (mutually imply each other).
     *
     * @param other the Semantics to compare with
     * @return true if both Semantics are equivalent; false otherwise
     */
    public boolean EQ(Semantics other) {
        return this.implies(other) && other.implies(this);
    }

    // ==================== Query Operations ====================

    /**
     * Checks if this Semantics is empty (contains no configurations).
     *
     * @return true if empty; false otherwise
     */
    public boolean ISEMPTY() {
        return this.configurations.isEmpty();
    }

    // ==================== Simplification ====================

    /**
     * Simplifies this Semantics by checking for each state in the assembly
     * whether its single-state configuration implies this Semantics.
     * <p>
     * For each state machine M and state S, if the configuration {M.S} implies
     * this Semantics, that configuration is added (which may subsume others).
     *
     * @param assembly the Assembly instance to derive state machines and states from
     * @return a new simplified Semantics
     * @throws IllegalArgumentException if assembly ID doesn't match
     */
    public Semantics simplify(Assembly assembly) {
        validateSameAssembly(assembly);
        
        Semantics result = this.clone();
        
        for (String machineId : assembly.getStateMachines().keySet()) {
            StateMachine machine = assembly.getStateMachines().get(machineId);
            for (StateInterface state : machine.getStates()) {
                Configuration conf = new BasicStateProposition(machineId, state.getName()).toConf(assembly);
                Semantics singleConf = conf.toSemantics();
                if (singleConf.LEQ(result, assembly)) {
                    result.addConfiguration(conf);
                }
            }
        }
        return result;
    }

    // ==================== Transformation Operations ====================

    /**
     * Transforms this Semantics by applying an action A = M.E (machine M, event E).
     * <p>
     * The transformation works as follows:
     * <ol>
     *   <li><b>Domain:</b> Identify configurations containing {M:S} where S is a source state
     *       of transitions triggered by event E.</li>
     *   <li><b>Codomain:</b> For each configuration in the domain, replace {M:S} with {M:T}
     *       where T is the target state of the triggered transition.</li>
     *   <li><b>Result:</b> Remove domain from this Semantics and unite with codomain.</li>
     * </ol>
     * <p>
     * If multiple transitions are triggered by the same event, all are processed.
     *
     * @param machineId the identifier of the machine involved in the action
     * @param eventName the event triggering the transition
     * @param assembly  the Assembly containing the machine definition
     * @return a new Semantics with the transformation applied
     * @throws IllegalArgumentException if the machine or transition is not found
     */
    public Semantics transformByMachineEvent(String machineId, String eventName, Assembly assembly) {
        StateMachine machine = assembly.getStateMachines().get(machineId);
        if (machine == null) {
            throw new IllegalArgumentException("Machine " + machineId + " not found in assembly.");
        }

        // Collect all transitions triggered by this event
        List<TransitionInterface> triggered = new ArrayList<>();
        for (TransitionInterface ti : machine.getTransitions()) {
            if (ti.getTriggerEvent().equals(eventName)) {
                triggered.add(ti);
            }
        }
        if (triggered.isEmpty()) {
            throw new IllegalArgumentException(
                "No transition triggered by event " + eventName + " found in machine " + machineId);
        }

        // Accumulate domains and codomains for all applicable transitions
        Semantics allDomains = Semantics.bottom(assembly);
        Semantics codomainUnion = Semantics.bottom(assembly);

        for (TransitionInterface ti : triggered) {
            Transition transition = (Transition) ti;
            String sourceState = transition.getSource().getName();
            String targetState = transition.getTarget().getName();

            // Build domain: intersection of this with {machineId.sourceState}
            Configuration confSource = Configuration.fromBasicStatePropositions(
                this.assemblyId,
                List.of(new BasicStateProposition(machineId, sourceState))
            );
            Semantics domain = this.AND(confSource.toSemantics());

            if (!domain.ISEMPTY()) {
                Semantics codomain = domain.computeCodomain(machineId, assembly, sourceState, targetState);
                codomainUnion = codomainUnion.OR(codomain);
                allDomains = allDomains.OR(domain);
            }
        }

        // Remove domain, add codomain
        Semantics remainder = this.AND(allDomains.NOT(assembly));
        return remainder.OR(codomainUnion).clone();
    }

    /**
     * Transforms this Semantics by applying a specific transition.
     * <p>
     * Similar to {@link #transformByMachineEvent(String, String, Assembly)} but operates
     * on a specific transition rather than finding transitions by event name.
     *
     * @param machineId  the identifier of the machine containing the transition
     * @param transition the transition to apply
     * @param assembly   the Assembly containing the machine definition
     * @return a new Semantics with the transformation applied
     */
    public Semantics transformByMachineTransition(String machineId, Transition transition, Assembly assembly) {
        String sourceState = transition.getSource().getName();
        String targetState = transition.getTarget().getName();

        // Create configuration for the source state
        Configuration confSource = Configuration.fromBasicStatePropositions(
                this.assemblyId,
                Arrays.asList(new BasicStateProposition(machineId, sourceState))
        );
        Semantics semSource = confSource.toSemantics();

        // Build the domain (intersection with source constraint)
        Semantics domain = this.AND(semSource);
        if (domain.ISEMPTY()) {
            return this;
        }

        // Compute codomain and apply transformation
        Semantics codomain = domain.computeCodomain(machineId, assembly, sourceState, targetState);
        Semantics remainder = this.AND(semSource.NOT(assembly));
        return remainder.OR(codomain).clone();
    }

    /**
     * Computes the codomain of a transformation by replacing state constraints.
     * <p>
     * For each configuration in this Semantics that contains {machineId:sourceState},
     * creates a new configuration with {machineId:targetState} instead.
     *
     * @param machineId   the machine identifier
     * @param assembly    the Assembly (for creating result Semantics)
     * @param sourceState the source state name to replace
     * @param targetState the target state name to substitute
     * @return a new Semantics with transformed configurations
     */
    public Semantics computeCodomain(String machineId, Assembly assembly, String sourceState, String targetState) {
        Semantics codomain = new Semantics(assembly.getAssemblyId());
        for (Configuration conf : this.configurations) {
            if (conf.contains(machineId) && conf.getStateName(machineId).equals(sourceState)) {
                Configuration newConf = conf.replaceConstraint(machineId, targetState);
                codomain.addConfiguration(newConf);
            }
        }
        return codomain;
    }

    // ==================== Conversion Operations ====================

    /**
     * Converts this Semantics to its symbolic representation as an SMProposition.
     * <p>
     * The result is the disjunction (OR) of all configuration propositions.
     * Returns {@link FalseProposition} if no configurations are present.
     *
     * @return the SMProposition representation of this Semantics
     */
    public SMProposition toSMProposition() {
        if (configurations.isEmpty()) {
            return new FalseProposition();
        }
        SMProposition disj = new FalseProposition();
        for (Configuration config : configurations) {
            disj = new OrProposition(disj, config.toSMProposition());
        }
        return disj;
    }

    /**
     * Computes the complement using a hybrid symbolic/enumeration approach.
     * <p>
     * Converts this Semantics to SMProposition, negates it, and evaluates
     * the negation over all feasible concrete assemblies.
     *
     * @param assembly the Assembly for generating feasible assemblies
     * @return the complement computed via symbolic evaluation
     * @throws IllegalArgumentException if assembly ID doesn't match
     */
    public Semantics complementHybrid(Assembly assembly) {
        validateSameAssembly(assembly);
        
        SMProposition originalProp = this.toSMProposition();
        SMProposition negatedProp = originalProp.negate();
        return evaluateSMPropositionOverAllFeasibleAssemblies(assembly, negatedProp);
    }

    // ==================== Deadlock Detection ====================

    /**
     * Computes the set of configurations that are reachable from the given configuration
     * via autonomous transitions in the assembly's component machines.
     * <p>
     * A configuration C1 can reach configuration C2 if there exists an autonomous transition
     * in some component machine M that changes M's state from S1 to S2, where C1 contains M.S1
     * and C2 is the result of replacing M.S1 with M.S2 in C1.
     *
     * @param startConfig the starting configuration
     * @param assembly the Assembly containing component machines with autonomous transitions
     * @return set of configurations reachable from startConfig via autonomous transitions
     */
    public static Set<Configuration> computeReachableConfigurations(Configuration startConfig, Assembly assembly) {
        Set<Configuration> reachable = new HashSet<>();
        Set<Configuration> visited = new HashSet<>();
        Deque<Configuration> worklist = new ArrayDeque<>();
        
        worklist.add(startConfig);
        visited.add(startConfig);
        
        while (!worklist.isEmpty()) {
            Configuration current = worklist.poll();
            
            // For each component machine, check autonomous transitions
            for (Map.Entry<String, StateMachine> entry : assembly.getStateMachines().entrySet()) {
                String machineId = entry.getKey();
                StateMachine machine = entry.getValue();
                
                // Get current state for this machine in the configuration
                String currentStateName = current.getStateName(machineId);
                if (currentStateName == null) continue;
                
                // Check all autonomous transitions from the current state
                for (TransitionInterface ti : machine.getTransitions()) {
                    Transition t = (Transition) ti;
                    // Only consider enabled autonomous transitions
                    if (t.isEnabled() && t.isAutonomous() && t.getSource().getName().equals(currentStateName)) {
                        // This autonomous transition can fire
                        String targetStateName = t.getTarget().getName();
                        Configuration nextConfig = current.replaceConstraint(machineId, targetStateName);
                        
                        if (nextConfig != null && !visited.contains(nextConfig)) {
                            visited.add(nextConfig);
                            reachable.add(nextConfig);
                            worklist.add(nextConfig);
                        }
                    }
                }
            }
        }
        
        return reachable;
    }

    /**
     * Identifies "deadlock" configurations within this Semantics.
     * <p>
     * A configuration is considered a deadlock if it cannot reach ALL other configurations
     * in this Semantics via autonomous transitions of the component machines.
     * <p>
     * This is important for PWS semantics: all configurations should be connected through
     * autonomous transitions. A configuration that cannot reach all others represents a potential
     * deadlock state where the system could get stuck.
     *
     * @param assembly the Assembly containing component machines with autonomous transitions
     * @return set of configurations that are deadlocks (cannot reach all other configurations)
     */
    public Set<Configuration> findDeadlockConfigurations(Assembly assembly) {
        Set<Configuration> deadlocks = new HashSet<>();
        
        if (configurations.size() <= 1) {
            // With 0 or 1 configuration, there's nothing to connect to
            return deadlocks;
        }
        
        for (Configuration config : configurations) {
            Set<Configuration> reachable = computeReachableConfigurations(config, assembly);
            
            // Check if this configuration can reach ALL other configurations in this Semantics
            boolean canReachAll = true;
            for (Configuration other : configurations) {
                if (!other.equals(config) && !reachable.contains(other)) {
                    canReachAll = false;
                    break;
                }
            }
            
            if (!canReachAll) {
                deadlocks.add(config);
            }
        }
        
        return deadlocks;
    }

    /**
     * Checks if this Semantics has full connectivity via autonomous transitions.
     * <p>
     * Full connectivity means every configuration can reach every other configuration
     * (possibly through intermediate configurations) via autonomous transitions.
     *
     * @param assembly the Assembly containing component machines
     * @return true if all configurations are mutually reachable; false otherwise
     */
    public boolean isFullyConnected(Assembly assembly) {
        return findDeadlockConfigurations(assembly).isEmpty();
    }

    // ==================== Factory Methods ====================

    /**
     * Creates a "top" Semantics containing all fully-specified configurations from the assembly.
     *
     * @param assembly the Assembly to generate the universe from
     * @return a Semantics containing all configurations in the universe
     */
    public static Semantics top(Assembly assembly) {
        Semantics sem = new Semantics(assembly.getAssemblyId());
        sem.getConfigurations().addAll(assembly.generateUniverse());
        return sem;
    }

    /**
     * Creates a "bottom" (empty) Semantics for the given assembly.
     *
     * @param assembly the Assembly (used only for its ID)
     * @return an empty Semantics
     */
    public static Semantics bottom(Assembly assembly) {
        return new Semantics(assembly.getAssemblyId());
    }

    /**
     * Creates a "bottom" (empty) Semantics with the specified assembly ID.
     *
     * @param assemblyId the assembly identifier
     * @return an empty Semantics
     */
    public static Semantics bottom(String assemblyId) {
        return new Semantics(assemblyId);
    }

    // ==================== Clone ====================

    /**
     * Creates a deep copy of this Semantics.
     *
     * @return a new Semantics with cloned configurations
     */
    @Override
    public Semantics clone() {
        Semantics cloned = new Semantics(this.assemblyId);
        for (Configuration config : this.configurations) {
            List<BasicStateProposition> clonedProps = new ArrayList<>(config.getBasicStatePropositions());
            Configuration clonedConfig = Configuration.fromBasicStatePropositions(this.assemblyId, clonedProps);
            cloned.addConfiguration(clonedConfig);
        }
        return cloned;
    }

    // ==================== Object Methods ====================

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ");
        for (Configuration config : configurations) {
            joiner.add(config.toString());
        }
        return "{" + joiner + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Semantics)) return false;
        Semantics that = (Semantics) o;
        return Objects.equals(assemblyId, that.assemblyId)
                && Objects.equals(configurations, that.configurations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assemblyId, configurations);
    }

    // ==================== Private Helper Methods ====================

    /**
     * Validates that the other Semantics belongs to the same assembly.
     *
     * @param other the other Semantics to validate
     * @throws IllegalArgumentException if assemblies don't match
     */
    private void validateSameAssembly(Semantics other) {
        if (!this.assemblyId.equals(other.getAssemblyId())) {
            throw new IllegalArgumentException("Both Semantics must belong to the same assembly.");
        }
    }

    /**
     * Validates that the assembly matches this Semantics' assembly ID.
     *
     * @param assembly the Assembly to validate
     * @throws IllegalArgumentException if assembly ID doesn't match
     */
    private void validateSameAssembly(Assembly assembly) {
        if (!this.assemblyId.equals(assembly.getAssemblyId())) {
            throw new IllegalArgumentException("Assembly ID mismatch.");
        }
    }

    /**
     * Computes the Cartesian product of a list of lists.
     * <p>
     * Each element of the result is a combination containing one element
     * from each input list.
     *
     * @param lists the input lists
     * @return the Cartesian product as a list of combinations
     */
    @SuppressWarnings("unused")
    private static List<List<BasicStateProposition>> cartesianProduct(List<List<BasicStateProposition>> lists) {
        List<List<BasicStateProposition>> result = new ArrayList<>();
        if (lists.isEmpty()) {
            result.add(new ArrayList<>());
            return result;
        }
        cartesianProductHelper(lists, result, 0, new ArrayList<>());
        return result;
    }

    /**
     * Recursive helper for Cartesian product computation.
     */
    private static void cartesianProductHelper(List<List<BasicStateProposition>> lists,
                                               List<List<BasicStateProposition>> result,
                                               int depth,
                                               List<BasicStateProposition> current) {
        if (depth == lists.size()) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (BasicStateProposition element : lists.get(depth)) {
            current.add(element);
            cartesianProductHelper(lists, result, depth + 1, current);
            current.remove(current.size() - 1);
        }
    }
}
