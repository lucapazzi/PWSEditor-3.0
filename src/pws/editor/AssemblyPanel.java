package pws.editor;

import assembly.AssemblyInterface;
import machinery.StateMachine;
import pws.PWSStateMachine;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/** Panel for viewing and editing an assembly's machines. */
@SuppressWarnings("this-escape")
public class AssemblyPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private AssemblyInterface assembly;
    private DefaultListModel<String> listModel;
    private JList<String> stateMachineList;

    /**
     * Creates a panel for editing an assembly's machines.
     *
     * @param assembly assembly to display
     */
    public AssemblyPanel(AssemblyInterface assembly) {
        this.assembly = assembly;
        setLayout(new BorderLayout());
        listModel = new DefaultListModel<>();
        stateMachineList = new JList<>(listModel);
        refreshList();
        add(new JScrollPane(stateMachineList), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        JButton addButton = new JButton("Add");
        JButton editButton = new JButton("Edit");
        JButton removeButton = new JButton("Remove");

        addButton.addActionListener(e -> onAdd());
        editButton.addActionListener(e -> onEdit());
        removeButton.addActionListener(e -> onRemove());

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(removeButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void refreshList() {
        listModel.clear();
        for(String id: assembly.getStateMachines().keySet()) {
            StateMachine machine = assembly.getStateMachines().get(id);
            listModel.addElement(id + " - " + machine.getName());
        }
    }

    private void onAdd() {
        // Ask for a unique identifier
        String id = JOptionPane.showInputDialog(this, "Enter a unique identifier:");
        if (id == null || id.trim().isEmpty()) {
            return;
        }
        // Ask whether to create a new machine or use an existing one
        int option = JOptionPane.showOptionDialog(this,
                "Do you want to create a new machine or select an existing one?",
                "Add StateMachine",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new Object[] {"New", "Existing"},
                "New");
        if(option == JOptionPane.YES_OPTION) {
            // Create a new PWSStateMachine
            String name = JOptionPane.showInputDialog(this, "Enter the machine name:");
            if(name == null || name.trim().isEmpty()){
                return;
            }
            StateMachine newMachine = new PWSStateMachine(name);
            assembly.addStateMachine(id, newMachine);
            // schedule semantics recalculation if hosted in PWSEditor
            java.awt.Container win = javax.swing.SwingUtilities.getWindowAncestor(this);
            if (win instanceof pws.editor.PWSEditor) {
                ((pws.editor.PWSEditor) win).scheduleSemanticsRecalculation();
            }
        } else if(option == JOptionPane.NO_OPTION) {
            // Select an existing machine: show a list of existing identifiers
            Map<String, StateMachine> machines = assembly.getStateMachines();
            if(machines.isEmpty()){
                JOptionPane.showMessageDialog(this, "There are no existing machines. A new machine will be created.");
                String name = JOptionPane.showInputDialog(this, "Enter the machine name:");
                if(name == null || name.trim().isEmpty()){
                    return;
                }
                StateMachine newMachine = new PWSStateMachine(name);
                assembly.addStateMachine(id, newMachine);
            } else {
                Object[] options = machines.keySet().toArray();
                String selectedId = (String) JOptionPane.showInputDialog(this, "Select a machine:",
                        "Existing machines", JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
                if(selectedId != null) {
                    StateMachine existingMachine = machines.get(selectedId);
                    // Clone the selected machine to avoid shared mutable state
                    StateMachine toAdd = existingMachine.clone();
                    assembly.addStateMachine(id, toAdd);
                    java.awt.Container win = javax.swing.SwingUtilities.getWindowAncestor(this);
                    if (win instanceof pws.editor.PWSEditor) {
                        ((pws.editor.PWSEditor) win).scheduleSemanticsRecalculation();
                    }
                }
            }
        }
        refreshList();
    }

    private void onEdit() {
        String selected = stateMachineList.getSelectedValue();
        if(selected == null) return;
        // Extract the identifier (assuming format "id - name")
        String id = selected.split(" - ")[0];
        Object[] options = new Object[] {"Change identifier", "Change machine", "Change name", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this, "Edit which property?", "Edit Machine",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice == 0) { // Change identifier
            String newId = JOptionPane.showInputDialog(this, "New identifier:", id);
            if (newId != null && !newId.trim().isEmpty() && !assembly.getStateMachines().containsKey(newId)) {
                java.awt.Container win = javax.swing.SwingUtilities.getWindowAncestor(this);
                if (win instanceof pws.editor.PWSEditor pe) {
                    pe.renameAssemblyMachineId(id, newId);
                } else {
                    StateMachine m = assembly.getStateMachines().remove(id);
                    assembly.addStateMachine(newId, m);
                }
            }
        } else if (choice == 1) { // Change machine association
            Map<String, StateMachine> machines = assembly.getStateMachines();
            Object[] optionsMachines = machines.keySet().toArray();
            String selectedId = (String) JOptionPane.showInputDialog(this, "Select a machine:",
                    "Select Machine", JOptionPane.PLAIN_MESSAGE, null, optionsMachines, id);
            if (selectedId != null) {
                StateMachine existing = machines.get(selectedId);
                assembly.getStateMachines().put(id, existing.clone());
                java.awt.Container win = javax.swing.SwingUtilities.getWindowAncestor(this);
                if (win instanceof pws.editor.PWSEditor) {
                    ((pws.editor.PWSEditor) win).scheduleSemanticsRecalculation();
                }
            }
        } else if (choice == 2) { // Change name
            String newName = JOptionPane.showInputDialog(this, "Edit the machine name:",
                    assembly.getStateMachines().get(id).getName());
            if(newName != null && !newName.trim().isEmpty()){
                assembly.getStateMachines().get(id).setName(newName);
                java.awt.Container win = javax.swing.SwingUtilities.getWindowAncestor(this);
                if (win instanceof pws.editor.PWSEditor) {
                    ((pws.editor.PWSEditor) win).scheduleSemanticsRecalculation();
                }
            }
        }
        refreshList();
    }

    private void onRemove() {
        String selected = stateMachineList.getSelectedValue();
        if(selected == null) return;
        String id = selected.split(" - ")[0];
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to remove the machine with identifier " + id + "?",
                "Confirm", JOptionPane.YES_NO_OPTION);
        if(confirm == JOptionPane.YES_OPTION) {
            assembly.getStateMachines().remove(id);
            java.awt.Container win = javax.swing.SwingUtilities.getWindowAncestor(this);
            if (win instanceof pws.editor.PWSEditor) {
                ((pws.editor.PWSEditor) win).scheduleSemanticsRecalculation();
            }
            refreshList();
        }
    }
}
