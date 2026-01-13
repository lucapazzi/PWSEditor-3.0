package pws.editor;

import assembly.Assembly;
import assembly.MachineLibrary;
import editor.StateMachineEditor;
import machinery.StateMachine;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import serializer.BinaryModelSerializer;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Panel for listing and importing machines in the library. */
public class MachineLibraryPanel extends JPanel {

    private final Assembly assembly;
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> list;

    /** Listener for library selection and lifecycle events. */
    public interface LibrarySelectionListener {
        /**
         * Called when a library entry is selected.
         *
         * @param key machine key
         */
        void librarySelected(String key);
        /**
         * Called when a library entry is removed.
         *
         * @param key machine key
         */
        void libraryRemoved(String key);
        /**
         * Called when a library entry is renamed.
         *
         * @param key machine key
         */
        void libraryRenamed(String key);
        /**
         * Called when a library entry is loaded from disk.
         *
         * @param key machine key
         */
        void libraryLoaded(String key);
    }

    private LibrarySelectionListener listener = null;

    /**
     * Creates a panel bound to the given assembly.
     *
     * @param assembly assembly context
     */
    public MachineLibraryPanel(Assembly assembly) {
        this.assembly = assembly;
        setLayout(new BorderLayout());
        list = new JList<>(listModel);
        refreshList();

        JScrollPane scroll = new JScrollPane(list);
        add(scroll, BorderLayout.CENTER);

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String sel = list.getSelectedValue();
                    if (sel == null) return;
                    String name = sel;
                    String key = assembly.getMachineLibrary().getKeyByName(name);
                    if (listener != null) listener.librarySelected(key);
                }
            }
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }

            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int idx = list.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        list.setSelectedIndex(idx);
                    }
                    JPopupMenu popup = new JPopupMenu();
                    JMenuItem renameItem = new JMenuItem("Rename");
                    JMenuItem deleteItem = new JMenuItem("Delete");
                    JMenuItem loadItem = new JMenuItem("Load...");
                    renameItem.addActionListener(a -> onRename());
                    deleteItem.addActionListener(a -> onDelete());
                    loadItem.addActionListener(a -> onLoad());
                    popup.add(renameItem);
                    popup.add(deleteItem);
                    popup.addSeparator();
                    popup.add(loadItem);
                    popup.show(list, e.getX(), e.getY());
                }
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton newBtn = new JButton("New");
        JButton editBtn = new JButton("Edit");
        JButton loadBtn = new JButton("Load");

        newBtn.addActionListener(e -> onNew());
        editBtn.addActionListener(e -> onEdit());
        loadBtn.addActionListener(e -> onLoad());

        buttons.add(newBtn);
        buttons.add(editBtn);
        buttons.add(loadBtn);
        add(buttons, BorderLayout.SOUTH);
    }

    /**
     * Sets the selection listener.
     *
     * @param l listener to set
     */
    public void setLibrarySelectionListener(LibrarySelectionListener l) {
        this.listener = l;
    }

    /**
     * Refreshes the list of machines from the library.
     */
    public void refreshList() {
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
        refreshList();
        // auto-select and notify listener so the embedded editor can open it
        if (listener != null) listener.librarySelected(key);
    }

    private void onEdit() {
        String sel = list.getSelectedValue();
        if (sel == null) return;
        String name = sel;
        String key = assembly.getMachineLibrary().getKeyByName(name);
        if (listener != null) listener.librarySelected(key);
    }

    private void onLoad() {
        JFileChooser fc = new JFileChooser();
        int res = fc.showOpenDialog(this);
        if (res != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        try {
            Object obj = BinaryModelSerializer.loadModel(file.getAbsolutePath());
            if (obj instanceof StateMachine) {
                StateMachine sm = (StateMachine) obj;
                String key = assembly.getMachineLibrary().addMachine(sm);
                refreshList();
                if (listener != null) listener.libraryLoaded(key);
            } else {
                JOptionPane.showMessageDialog(this, "File does not contain a StateMachine.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException | ClassNotFoundException ex) {
            JOptionPane.showMessageDialog(this, "Error loading machine: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onDelete() {
        String sel = list.getSelectedValue();
        if (sel == null) return;
        String name = sel;
        String key = assembly.getMachineLibrary().getKeyByName(name);
        // Check whether any assembly entries reference this machine
        boolean referenced = false;
        StateMachine libMachine = assembly.getMachineLibrary().get(key);
        for (StateMachine sm : assembly.getStateMachines().values()) {
            if (sm == libMachine) { referenced = true; break; }
        }

        if (referenced) {
            int ans = JOptionPane.showConfirmDialog(this,
                    "This machine is referenced by one or more assembly IDs. Delete anyway?\nReferences will remain but the machine will be removed from the Library.",
                    "Confirm delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ans != JOptionPane.YES_OPTION) return;
        } else {
            int confirm = JOptionPane.showConfirmDialog(this, "Remove machine from library?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
        }

        assembly.getMachineLibrary().remove(key);
        refreshList();
        if (listener != null) listener.libraryRemoved(key);
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
        if (ok && listener != null) listener.libraryRenamed(key);
    }
}
