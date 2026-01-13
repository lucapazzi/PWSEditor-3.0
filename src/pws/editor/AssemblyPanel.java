package pws.editor;

import assembly.AssemblyInterface;
import machinery.StateMachine;
import pws.PWSStateMachine;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/** Panel for viewing and editing an assembly's machines. */
public class AssemblyPanel extends JPanel {
    private AssemblyInterface assembly;
    private DefaultListModel<String> listModel;
    private JList<String> stateMachineList;

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
        // Chiedi un identificatore univoco
        String id = JOptionPane.showInputDialog(this, "Enter a unique identifier:");
        if (id == null || id.trim().isEmpty()) {
            return;
        }
        // Chiedi se si desidera una nuova macchina o usare una esistente
        int option = JOptionPane.showOptionDialog(this,
                "Do you want to create a new machine or select an existing one?",
                "Add StateMachine",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new Object[] {"New", "Existing"},
                "New");
        if(option == JOptionPane.YES_OPTION) {
            // Crea una nuova PWSStateMachine
            String name = JOptionPane.showInputDialog(this, "Enter the machine name:");
            if(name == null || name.trim().isEmpty()){
                return;
            }
            StateMachine newMachine = new PWSStateMachine(name);
            assembly.addStateMachine(id, newMachine);
        } else if(option == JOptionPane.NO_OPTION) {
            // Seleziona una macchina esistente: mostriamo una lista degli identificatori già presenti
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
                    // Aggiungi la stessa macchina con il nuovo identificatore
                    assembly.addStateMachine(id, existingMachine);
                }
            }
        }
        refreshList();
    }

    private void onEdit() {
        String selected = stateMachineList.getSelectedValue();
        if(selected == null) return;
        // Estrai l'identificatore (assumendo formato "id - nome")
        String id = selected.split(" - ")[0];
        String newName = JOptionPane.showInputDialog(this, "Edit the machine name:",
                assembly.getStateMachines().get(id).getName());
        if(newName != null && !newName.trim().isEmpty()){
            assembly.getStateMachines().get(id).setName(newName);
            refreshList();
        }
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
            refreshList();
        }
    }
}
