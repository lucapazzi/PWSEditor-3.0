package utility;

import assembly.AssemblyInterface;
import machinery.StateInterface;
import machinery.StateMachine;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Swing dialogs used by the editor for guard/action selection. */
public class PWSDialogs {
    /** Utility class; do not instantiate. */
    private PWSDialogs() {
    }

    /**
     * Shows a dialog to choose a guard and actions.
     * The guard is a "machineId.stateName" pair and for actions, for each machineId,
     * the user can choose a single action in the "machineId.event" format.
     *
     * @param assembly The assembly containing the base state machines.
     * @return An array of two strings: [guard, actions]. If the user cancels, returns {"", ""}.
     */
    public static String[] askForGuardAndAction(AssemblyInterface assembly) {
        // Build the list of guards.
        List<String> guardOptions = new ArrayList<>();
        // For actions, we want a map from machineId to a list of actions.
        Map<String, List<String>> actionOptionsMap = new LinkedHashMap<>();

        // Assume assembly.getStateMachines() returns a Map<String, StateMachine>
        Map<String, StateMachine> machines = assembly.getStateMachines();
        for (Map.Entry<String, StateMachine> entry : machines.entrySet()) {
            String machineId = entry.getKey();
            StateMachine machine = entry.getValue();

            // For guards: for each machine state, add "machineId.stateName".
            for (StateInterface s : machine.getStates()) {
                guardOptions.add(machineId + "." + s.getName());
            }

            // For actions: for each machine event, add "machineId.event".
            List<String> actionList = new ArrayList<>();
            if (machine.getEvents() != null) {
                for (String event : machine.getEvents()) {
                    actionList.add(machineId + "." + event);
                }
            }
            actionOptionsMap.put(machineId, actionList);
        }

        // Create the dialog panel.
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // Row 0: label and JComboBox for the guard.
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Select a guard (m.S):"), gbc);
        gbc.gridx = 1;
        JComboBox<String> guardCombo = new JComboBox<>(guardOptions.toArray(new String[0]));
        guardCombo.setPreferredSize(new Dimension(200, 25));
        panel.add(guardCombo, gbc);

        // Next rows: for each machineId, add a row for action selection.
        Map<String, JComboBox<String>> actionCombos = new LinkedHashMap<>();
        int row = 1;
        for (Map.Entry<String, List<String>> entry : actionOptionsMap.entrySet()) {
            String machineId = entry.getKey();
            List<String> actions = entry.getValue();

            gbc.gridx = 0;
            gbc.gridy = row;
            panel.add(new JLabel("Action for " + machineId + ":"), gbc);

            gbc.gridx = 1;
            // Create a JComboBox for actions with a default "None" option.
            String[] actionArray = new String[actions.size() + 1];
            actionArray[0] = "None";
            for (int i = 0; i < actions.size(); i++) {
                actionArray[i + 1] = actions.get(i);
            }
            JComboBox<String> actionCombo = new JComboBox<>(actionArray);
            actionCombo.setPreferredSize(new Dimension(200, 25));
            panel.add(actionCombo, gbc);
            actionCombos.put(machineId, actionCombo);
            row++;
        }

        int result = JOptionPane.showConfirmDialog(null, panel, "Select Guard and Action",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String selectedGuard = (String) guardCombo.getSelectedItem();
            // For actions, collect one action per machineId (skipping "None")
            List<String> chosenActions = new ArrayList<>();
            for (Map.Entry<String, JComboBox<String>> entry : actionCombos.entrySet()) {
                String selected = (String) entry.getValue().getSelectedItem();
                if (selected != null && !selected.equals("None")) {
                    chosenActions.add(selected);
                }
            }
            // Build the action string, comma-separated.
            String actionsStr = String.join(", ", chosenActions);
            return new String[] { selectedGuard != null ? selectedGuard : "", actionsStr };
        } else {
            return new String[] { "", "" };
        }
    }
}
