package assembly;

import editor.StateMachinePanel;
import machinery.StateInterface;
import machinery.StateMachine;
import pws.editor.semantics.Configuration;
import pws.editor.semantics.Semantics;
import smalgebra.BasicStateProposition;

import java.util.*;

/** Represents a PWS assembly and its state machines, guards, actions, and formulas. */
public class Assembly implements AssemblyInterface {
    private static final long serialVersionUID = 1L;
    private String assemblyId;
    private LinkedHashMap<String, StateMachine> stateMachines;
    // Repository of reusable machines (machineKey -> StateMachine)
    private MachineLibrary machineLibrary = new MachineLibrary();
    // Optional storage for LTL formulas associated with this assembly
    private java.util.ArrayList<LTLFormula> ltlFormulas = new java.util.ArrayList<>();
    // Optional UI-only alias data for assembly-local machines (machineId -> alias data)
    private LinkedHashMap<String, StateMachinePanel.AliasData> aliasDataByMachineId = new LinkedHashMap<>();

    /**
     * Creates an assembly with the given identifier.
     *
     * @param assemblyId assembly identifier
     */
    public Assembly(String assemblyId) {
        this.assemblyId = assemblyId;
        stateMachines = new LinkedHashMap<>();
    }

    /**
     * Returns the machine library for this assembly.
     *
     * @return machine library
     */
    public MachineLibrary getMachineLibrary() {
        return machineLibrary;
    }

    @Override
    public Map<String, StateMachine> getStateMachines() {
        return stateMachines;
    }

    /**
     * Returns the assembly identifier.
     *
     * @return assembly identifier
     */
    public String getAssemblyId() {
        return assemblyId;
    }


    @Override
    public void addStateMachine(String identifier, StateMachine machine) {
        stateMachines.put(identifier, machine);
    }

    /**
     * Returns alias data for a machine identifier.
     *
     * @param machineId assembly machine id
     * @return alias data or null if none
     */
    public StateMachinePanel.AliasData getAliasData(String machineId) {
        if (machineId == null) return null;
        return aliasDataByMachineId.get(machineId);
    }

    /**
     * Sets alias data for a machine identifier.
     *
     * @param machineId assembly machine id
     * @param data alias data (null to clear)
     */
    public void setAliasData(String machineId, StateMachinePanel.AliasData data) {
        if (machineId == null) return;
        if (data == null) {
            aliasDataByMachineId.remove(machineId);
        } else {
            aliasDataByMachineId.put(machineId, data);
        }
    }

    /**
     * Removes alias data for a machine identifier.
     *
     * @param machineId assembly machine id
     */
    public void removeAliasData(String machineId) {
        if (machineId == null) return;
        aliasDataByMachineId.remove(machineId);
    }

    /**
     * Clears all alias data for assembly machines.
     */
    public void clearAliasData() {
        aliasDataByMachineId.clear();
    }

    @Override
    public List<AssemblyInterface> getAllConcreteAssemblies() {
        // Implementation-specific.
        return new ArrayList<>();
    }

    /**
     * Returns a complete list of possible actions.
     * For each state machine m in the assembly and for each event e in m.getEvents(),
     * creates an action with identifier m and event e.
     *
     * Example: if the assembly contains machines "t1" and "t2" with triggers {e, f},
     * the returned list is: [ t1.e, t1.f, t2.e, t2.f ].
     *
     * @return list of possible actions
     */
    public List<Action> getAllPossibleActions() {
        List<Action> actions = new ArrayList<>();
        for (Map.Entry<String, StateMachine> entry : stateMachines.entrySet()) {
            String machineId = entry.getKey();
            StateMachine machine = entry.getValue();
            // Assume machine.getEvents() returns a Set or List of Strings.
            for (String event : machine.getEvents()) {
                actions.add(new Action(machineId, event));
            }
        }
        return actions;
    }

    @Override
    public Semantics calculateInitialStateSemantics() {
        // Generate an assemblyId: if the Assembly has its own identifier, use it; otherwise,
        // a constant value is used here.
        String assemblyId = this.getAssemblyId();
        Semantics semantics = new Semantics(assemblyId);

        Map<String, StateMachine> machines = getStateMachines();
        if (machines == null || machines.isEmpty()) {
            // No component machines configured: treat as a single empty configuration.
            semantics.addConfiguration(new Configuration(assemblyId));
            return semantics;
        }

        // For each state machine, collect propositions that represent initial states.
        List<List<BasicStateProposition>> machineInitialProps = new ArrayList<>();
        for (Map.Entry<String, StateMachine> entry : machines.entrySet()) {
            String machineId = entry.getKey();
            StateMachine machine = entry.getValue();
            List<BasicStateProposition> initialProps = new ArrayList<>();
            for (StateInterface s : machine.getStates()) {
                if (machine.getInitialStates().contains(s)) {
                    // Create the initial proposition for this machine
                    initialProps.add(new BasicStateProposition(machineId, s.getName()));
                }
            }
            // If initial states were found for the machine, add them to the list
            if (initialProps.isEmpty()) {
                // A machine with no initial states yields no valid initial configurations.
                return semantics;
            }
            machineInitialProps.add(initialProps);
        }

        // If no initial propositions are found in any machine (shouldn't happen with machines present),
        // return a Semantics containing an "empty" Configuration (interpretable as true).
        if (machineInitialProps.isEmpty()) {
            semantics.addConfiguration(new Configuration(assemblyId));
            return semantics;
        }

        // Compute the cartesian product of initial proposition lists,
        // yielding all possible initial configurations.
        List<List<BasicStateProposition>> cartesian = cartesianProduct(machineInitialProps);
        for (List<BasicStateProposition> combination : cartesian) {
            // Build a Configuration from the ordered combination of BasicStateProp.
            // Use fromBasicStatePropositions to guarantee ordering.
            List<BasicStateProposition> props = new ArrayList<>(combination);
            Configuration config = Configuration.fromBasicStatePropositions(assemblyId, props);
            semantics.addConfiguration(config);
        }

        return semantics;
    }

    // LTL formula management
    public java.util.List<LTLFormula> getLTLFormulas() {
        return ltlFormulas;
    }
    public void addLTLFormula(LTLFormula f) {
        ltlFormulas.add(f);
    }
    public void removeLTLFormula(LTLFormula f) {
        ltlFormulas.remove(f);
    }



    /**
     * Returns the list of available guards as BasicStateProposition.
     * For each state machine (machineId) in the assembly and for each state,
     * creates a BasicStateProposition in the form "machineId.stateName".
     */
    @Override
    public List<BasicStateProposition> getAssemblyGuards() {
        List<BasicStateProposition> guardList = new ArrayList<>();
        for (Map.Entry<String, StateMachine> entry : getStateMachines().entrySet()) {
            String machineId = entry.getKey();
            StateMachine machine = entry.getValue();
            for (StateInterface s : machine.getStates()) {
                String stateName = s.getName();
                // Skip the pseudostate in guard propositions
                if ("PseudoState".equals(stateName)) {
                    continue;
                }
                guardList.add(new BasicStateProposition(machineId, stateName));
            }
        }
        return guardList;
    }

    /**
     * Returns the list of available actions as Action objects.
     * For each state machine (machineId) in the assembly and for each event in machine.getEvents(),
     * creates an Action object in the form "machineId.event".
     */
    @Override
    public List<Action> getAssemblyActions() {
        List<Action> actionList = new ArrayList<>();
        for (Map.Entry<String, StateMachine> entry : getStateMachines().entrySet()) {
            String machineId = entry.getKey();
            StateMachine machine = entry.getValue();
            if (machine.getEvents() != null) {
                for (String event : machine.getEvents()) {
                    actionList.add(new Action(machineId, event));
                }
            }
        }
        return actionList;
    }

    /**
     * Generates the universe of fully-specified configurations for the given assemblyId.
     * It retrieves the Assembly instance using a registry (assumed to be available).
     *
     * @return set of all configurations in the assembly universe
     */
    public Set<Configuration> generateUniverse() {
        Set<Configuration> universe = new HashSet<>();
        Map<String, StateMachine> machines = this.getStateMachines();
        Map<String, List<String>> machineStates = new LinkedHashMap<>();
        for (Map.Entry<String, StateMachine> entry : machines.entrySet()) {
            String machineId = entry.getKey();
            StateMachine machine = entry.getValue();
            List<String> stateNames = new ArrayList<>();
            for (StateInterface s : machine.getStates()) {
                String stateName = s.getName();
                if ( !stateName.equals("PseudoState") ) {
                    stateNames.add(stateName);
                }
            }
            machineStates.put(machineId, stateNames);
        }
        // Build a list of lists of BasicStateProposition for each machine.
        List<List<BasicStateProposition>> listOfPropLists = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : machineStates.entrySet()) {
            String machineId = entry.getKey();
            List<String> states = entry.getValue();
            // If a machine has no states, skip it.
            if (states.isEmpty()) {
                continue;
            }
            List<BasicStateProposition> propList = new ArrayList<>();
            for (String state : states) {
                propList.add(new BasicStateProposition(machineId, state));
            }
            listOfPropLists.add(propList);
        }
        List<List<BasicStateProposition>> cartesian = cartesianProduct(listOfPropLists);
        for (List<BasicStateProposition> combination : cartesian) {
            Configuration config = Configuration.fromBasicStatePropositions(this.assemblyId, combination);
            universe.add(config);
        }
        return universe;
    }

    private static List<List<BasicStateProposition>> cartesianProduct(List<List<BasicStateProposition>> lists) {
        List<List<BasicStateProposition>> result = new ArrayList<>();
        if (lists.isEmpty()) {
            result.add(new ArrayList<>());
            return result;
        }
        cartesianProductHelper(lists, result, 0, new ArrayList<>());
        return result;
    }

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
