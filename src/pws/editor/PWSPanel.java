package pws.editor;

import assembly.Assembly;
import assembly.MachineLibrary;
import assembly.AssemblyInterface;
import editor.StateMachineEditor;
import machinery.StateMachine;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;

public class PWSPanel extends JPanel {

    private Assembly assembly;
    private DefaultListModel<String> listModel;
    private JList<String> machineList;

    public PWSPanel(Assembly assembly) {
        this.assembly = assembly;
        setLayout(new BorderLayout());
        listModel = new DefaultListModel<>();
        machineList = new JList<>(listModel);
        refreshList();
        JScrollPane scrollPane = new JScrollPane(machineList);
        add(scrollPane, BorderLayout.CENTER);

        // Listener per il doppio click: notifica il listener di selezione (se presente),
        // altrimenti apre un nuovo editor in finestra (comportamento legacy).
        machineList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) { // doppio click
                    String selected = machineList.getSelectedValue();
                    if (selected != null) {
                        // Assumiamo il formato "id - Nome"
                        String[] parts = selected.split(" - ");
                        if (parts.length >= 2) {
                            String id = parts[0];
                            String machineName = parts[1];
                            StateMachine machine = assembly.getStateMachines().get(id);
                            if (machine != null) {
                                if (selectionListener != null) {
                                    selectionListener.machineSelected(id);
                                } else {
                                    SwingUtilities.invokeLater(() -> {
                                        // Fallback: apri editor in finestra separata
                                        StateMachineEditor editor = new StateMachineEditor(machine, assembly, id + " : " + machineName);
                                        editor.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                                        editor.setSize(800, 600);
                                        editor.setLocationRelativeTo(null);
                                        editor.setVisible(true);
                                    });
                                }
                            }
                        }
                    }
                }
            }
        });

        // Pannello di pulsanti per aggiungere, modificare o rimuovere macchine
        JPanel buttonPanel = new JPanel();
        JButton addButton = new JButton("Add");
        JButton editButton = new JButton("Edit");
        JButton detachButton = new JButton("Detach/Clone");
        JButton removeButton = new JButton("Remove");

        addButton.addActionListener(e -> onAdd());
        editButton.addActionListener(e -> onEdit());
        detachButton.addActionListener(e -> onDetach());
        removeButton.addActionListener(e -> onRemove());

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(detachButton);
        buttonPanel.add(removeButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    // Callback interface to notify when a machine is selected (double-clicked)
    public interface MachineSelectionListener {
        void machineSelected(String id);
        void machineRemoved(String id);
        void machineAddedToLibrary(String key);
        void machineEdited(String id);
    }

    private MachineSelectionListener selectionListener = null;

    public void setMachineSelectionListener(MachineSelectionListener l) {
        this.selectionListener = l;
    }

    public void refreshList() {
        listModel.clear();
        MachineLibrary lib = assembly.getMachineLibrary();
        for (String id : assembly.getStateMachines().keySet()) {
            StateMachine machine = assembly.getStateMachines().get(id);
            String label = id + " - " + (machine != null ? machine.getName() : "(null)");
            boolean shared = false;
            if (machine != null && lib != null) {
                for (StateMachine libM : lib.getMachines().values()) {
                    if (libM == machine) { shared = true; break; }
                }
            }
            if (shared) label += " [shared]";
            listModel.addElement(label);
        }
    }

    private void onAdd() {
        String id = JOptionPane.showInputDialog(this, "Enter a unique identifier:");
        if (id == null || id.trim().isEmpty()) return;

        Object[] options = {"Create new machine", "Choose from library", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this, "Create a new machine or choose an existing one from the library?",
                "Add StateMachine to Assembly", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);

        if (choice == 2 || choice == JOptionPane.CANCEL_OPTION) return;

        if (choice == 0) {
            // Create new machine (legacy behaviour)
            String name = JOptionPane.showInputDialog(this, "Enter the machine name:");
            if (name == null || name.trim().isEmpty()) return;
            StateMachine newMachine = new StateMachine(name);
            assembly.addStateMachine(id, newMachine);
        } else if (choice == 1) {
            // Choose from library
            MachineLibraryDialog dlg = new MachineLibraryDialog(SwingUtilities.getWindowAncestor(this), assembly);
            dlg.setVisible(true);
            String selectedKey = dlg.getSelectedKey();
            if (selectedKey != null) {
                StateMachine m = assembly.getMachineLibrary().get(selectedKey);
                if (m != null) {
                    // Ask whether to reference or clone
                    Object[] assignOptions = {"Reference (shared)", "Clone (independent)", "Cancel"};
                    int assignChoice = JOptionPane.showOptionDialog(this,
                            "Assign as reference (shared) or clone (independent)?",
                            "Assign Machine", JOptionPane.YES_NO_CANCEL_OPTION,
                            JOptionPane.QUESTION_MESSAGE, null, assignOptions, assignOptions[0]);
                    if (assignChoice == JOptionPane.CANCEL_OPTION || assignChoice == 2) {
                        // user cancelled
                    } else if (assignChoice == 0) {
                        // Reference: use the same instance
                        assembly.addStateMachine(id, m);
                    } else if (assignChoice == 1) {
                        // Clone: deep clone and register in library
                        try {
                            StateMachine cloned = m.clone();
                            String newKey = assembly.getMachineLibrary().addMachine(cloned);
                            assembly.addStateMachine(id, cloned);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            JOptionPane.showMessageDialog(this, "Error cloning machine: " + ex.getMessage());
                        }
                    }
                }
            }
        }
        refreshList();
    }

    private void onEdit() {
        String selected = machineList.getSelectedValue();
        if (selected == null) return;
        String id = selected.split(" - ")[0];
        AssemblyMachineEditDialog dlg = new AssemblyMachineEditDialog(SwingUtilities.getWindowAncestor(this), assembly, id);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            refreshList();
            if (selectionListener != null) {
                selectionListener.machineEdited(id);
            }
        }
    }

    private void onDetach() {
        String selected = machineList.getSelectedValue();
        if (selected == null) return;
        String id = selected.split(" - ")[0];
        StateMachine current = assembly.getStateMachines().get(id);
        if (current == null) return;

        try {
            StateMachine cloned = current.clone();
            String newKey = assembly.getMachineLibrary().addMachine(cloned);
            // reassign the assembly id to the cloned instance
            assembly.addStateMachine(id, cloned);
            refreshList();
            if (selectionListener != null) {
                selectionListener.machineAddedToLibrary(newKey);
                selectionListener.machineSelected(id);
            }
            String machineName = cloned.getName() != null ? cloned.getName() : "Unnamed";
            JOptionPane.showMessageDialog(this, "Detached clone created in library: " + machineName);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error cloning machine: " + ex.getMessage());
        }
    }

    private void onRemove() {
        String selected = machineList.getSelectedValue();
        if (selected == null) return;
        String id = selected.split(" - ")[0];
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to remove the machine with identifier " + id + "?",
                "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            // Rimuove solo il mapping corrispondente all'id selezionato
            assembly.getStateMachines().remove(id);
            if (selectionListener != null) {
                selectionListener.machineRemoved(id);
            }
            refreshList();
        }
    }
}
