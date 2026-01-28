package pws.editor;

import assembly.Assembly;
import machinery.StateMachine;
import editor.StateMachinePanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/** Dialog for editing a machine instance within an assembly. */
public class AssemblyMachineEditDialog extends JDialog {

    private boolean confirmed = false;

    /**
     * Creates a dialog to edit a machine inside an assembly.
     *
     * @param owner dialog owner
     * @param assembly assembly containing the machine
     * @param machineId identifier of the machine to edit
     */
    public AssemblyMachineEditDialog(Window owner, Assembly assembly, String machineId) {
        super(owner, "Edit assembly machine", ModalityType.APPLICATION_MODAL);
        StateMachine machine = assembly.getStateMachines().get(machineId);
        if (machine == null) {
            throw new IllegalArgumentException("Unknown machine id: " + machineId);
        }

        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4,4,4,4);
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.EAST;
        top.add(new JLabel("Identifier:"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST; gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField idField = new JTextField(machineId, 20);
        top.add(idField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.EAST; gbc.fill = GridBagConstraints.NONE;
        top.add(new JLabel("Machine name:"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST; gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField nameField = new JTextField(machine.getName(), 20);
        top.add(nameField, gbc);

        StateMachinePanel smPanel = new StateMachinePanel(machine);
        smPanel.setPreferredSize(new Dimension(600, 360));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        buttons.add(ok);
        buttons.add(cancel);

        ok.addActionListener(e -> {
            String newId = idField.getText().trim();
            String newName = nameField.getText().trim();
            if (newId.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Identifier cannot be empty", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!newId.equals(machineId) && assembly.getStateMachines().containsKey(newId)) {
                JOptionPane.showMessageDialog(this, "Identifier already exists: " + newId, "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            // Apply changes
            if (!newName.isEmpty()) {
                machine.setName(newName);
                // Sync library name mapping if this machine is in the library
                assembly.getMachineLibrary().syncMachineName(machine);
            }
            if (!newId.equals(machineId)) {
                if (owner instanceof pws.editor.PWSEditor pe) {
                    pe.renameAssemblyMachineId(machineId, newId);
                } else {
                    // Remove old mapping and insert new one pointing to the same machine object
                    assembly.getStateMachines().remove(machineId);
                    assembly.addStateMachine(newId, machine);
                }
            }
            confirmed = true;
            setVisible(false);
            dispose();
        });

        cancel.addActionListener(e -> {
            confirmed = false;
            setVisible(false);
            dispose();
        });

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(new JScrollPane(smPanel), BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Returns whether the user confirmed the dialog.
     *
     * @return true if the user confirmed the dialog
     */
    public boolean isConfirmed() {
        return confirmed;
    }
}
