package pws.editor;

import assembly.Assembly;
import assembly.MachineLibrary;
import editor.StateMachineEditor;
import machinery.StateMachine;

import javax.swing.*;
import java.awt.*;
import java.io.Serializable;

public class MachineLibraryDialog extends JDialog implements Serializable {

    private String selectedKey = null;
    private final Assembly assembly;
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> list;

    public MachineLibraryDialog(Window owner, Assembly assembly) {
        super(owner, "Machine Library", ModalityType.APPLICATION_MODAL);
        this.assembly = assembly;

        list = new JList<>(listModel);
        refreshList();

        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(480, 240));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton selectBtn = new JButton("Select");
        JButton cancelBtn = new JButton("Cancel");

        buttons.add(selectBtn);
        buttons.add(cancelBtn);

        selectBtn.addActionListener(e -> onSelect());
        cancelBtn.addActionListener(e -> onCancel());

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(scroll, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    private void refreshList() {
        listModel.clear();
        MachineLibrary lib = assembly.getMachineLibrary();
        for (String name : lib.getNames()) {
            listModel.addElement(name != null ? name : "(null)");
        }
    }

    private void onNew() {
        String name = JOptionPane.showInputDialog(this, "Machine name:");
        if (name == null || name.trim().isEmpty()) return;
        StateMachine m = new StateMachine(name);
        String key = assembly.getMachineLibrary().addMachine(m);
        // open editor to let user edit the new machine
        SwingUtilities.invokeLater(() -> {
            StateMachineEditor editor = new StateMachineEditor(m, "Library: " + name);
            editor.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            editor.setVisible(true);
        });
        refreshList();
    }

    private void onEdit() {
        String sel = list.getSelectedValue();
        if (sel == null) return;
        String name = sel;
        StateMachine m = assembly.getMachineLibrary().getByName(name);
        if (m != null) {
            SwingUtilities.invokeLater(() -> {
                StateMachineEditor editor = new StateMachineEditor(m, "Library: " + m.getName());
                editor.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                editor.setVisible(true);
            });
        }
    }

    private void onDelete() {
        String sel = list.getSelectedValue();
        if (sel == null) return;
        String name = sel;
        String key = assembly.getMachineLibrary().getKeyByName(name);
        int confirm = JOptionPane.showConfirmDialog(this, "Remove machine from library?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            assembly.getMachineLibrary().remove(key);
            refreshList();
        }
    }

    private void onRename() {
        String sel = list.getSelectedValue();
        if (sel == null) return;
        String oldName = sel;
        String key = assembly.getMachineLibrary().getKeyByName(oldName);
        if (key == null) return;
        String newName = JOptionPane.showInputDialog(this, "New name:", oldName);
        if (newName == null || newName.trim().isEmpty()) return;
        boolean ok = assembly.getMachineLibrary().renameMachine(key, newName.trim());
        if (!ok) {
            JOptionPane.showMessageDialog(this, "Name already in use or error renaming", "Error", JOptionPane.ERROR_MESSAGE);
        }
        refreshList();
    }

    private void onSelect() {
        String sel = list.getSelectedValue();
        if (sel == null) return;
        String name = sel;
        selectedKey = assembly.getMachineLibrary().getKeyByName(name);
        setVisible(false);
        dispose();
    }

    private void onCancel() {
        selectedKey = null;
        setVisible(false);
        dispose();
    }

    public String getSelectedKey() {
        return selectedKey;
    }
}
