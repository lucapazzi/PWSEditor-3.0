package pws.editor;

import assembly.Assembly;
import assembly.MachineLibrary;
import editor.StateMachinePanel;
import machinery.StateMachine;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import serializer.JsonModelSerializer;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Panel for listing and importing machines in the library. */
public class MachineLibraryPanel extends JPanel {

    private final Assembly assembly;
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> list;
    private Runnable beforeSaveLibrary = null;

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
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list,
                                                          Object value,
                                                          int index,
                                                          boolean isSelected,
                                                          boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                String name = value != null ? value.toString() : "";
                StateMachine machine = assembly.getMachineLibrary().getByName(name);
                if (machine != null && hasComponentDeadlocks(machine)) {
                    label.setText("<html><font color='red'>*</font> " + escapeHtml(name) + "</html>");
                } else {
                    label.setText(name);
                }
                return label;
            }
        });
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
        JButton saveBtn = new JButton("Save");

        newBtn.addActionListener(e -> onNew());
        editBtn.addActionListener(e -> onEdit());
        // Load button now replaces the entire library (Load Library)
        loadBtn.addActionListener(e -> onLoadLibrary());
        // Save button exports the whole MachineLibrary (Save Library)
        saveBtn.addActionListener(e -> onSaveLibrary());

        buttons.add(newBtn);
        buttons.add(editBtn);
        buttons.add(loadBtn);
        buttons.add(saveBtn);
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
     * Sets a callback invoked right before saving the library.
     *
     * @param r callback to invoke
     */
    public void setBeforeSaveLibrary(Runnable r) {
        this.beforeSaveLibrary = r;
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

    private boolean hasComponentDeadlocks(StateMachine machine) {
        if (machine == null) return false;
        for (machinery.StateInterface state : machine.getStates()) {
            if (state == null || "PseudoState".equals(state.getName())) continue;
            boolean hasEnabledOutgoing = false;
            for (machinery.TransitionInterface t : machine.getTransitions()) {
                if (t != null && t.getSource() == state && isTransitionEnabled(t)) {
                    hasEnabledOutgoing = true;
                    break;
                }
            }
            if (!hasEnabledOutgoing) {
                return true;
            }
        }
        return false;
    }

    private boolean isTransitionEnabled(machinery.TransitionInterface t) {
        if (t instanceof machinery.Transition trans) {
            return trans.isEnabled();
        }
        return true;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
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
        fc.setFileFilter(new FileNameExtensionFilter("Machine File (.sm)", "sm"));
        int res = fc.showOpenDialog(this);
        if (res != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        try {
            JsonModelSerializer.LoadedStateMachine loaded = JsonModelSerializer.loadStateMachineWithAnnotations(file);
            StateMachine sm = loaded != null ? loaded.getModel() : null;
            StateMachinePanel.AliasData aliasData = loaded != null ? loaded.getAnnotations() : null;
            if (sm != null) {
                String key = assembly.getMachineLibrary().addMachine(sm, aliasData);
                refreshList();
                if (listener != null) listener.libraryLoaded(key);
            } else {
                JOptionPane.showMessageDialog(this, "File does not contain a StateMachine.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "Error loading machine: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Load a MachineLibrary (.mlib) and replace the current library contents.
     */
    private void onLoadLibrary() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Machine Library (.mlib)", "mlib"));
        int res = fc.showOpenDialog(this);
        if (res != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        try {
            MachineLibrary loaded = JsonModelSerializer.loadMachineLibrary(file);
            if (loaded != null) {
                MachineLibrary current = assembly.getMachineLibrary();
                current.clear();
                for (java.util.Map.Entry<String, machinery.StateMachine> entry : loaded.getMachines().entrySet()) {
                    current.addMachine(entry.getKey(), entry.getValue(), loaded.getAliasData(entry.getKey()));
                }
                refreshList();
                // Notify listener about a loaded library; pick first key if present
                if (listener != null) {
                    String firstKey = current.getMachines().keySet().stream().findFirst().orElse(null);
                    if (firstKey != null) listener.libraryLoaded(firstKey);
                }
                JOptionPane.showMessageDialog(this, "Library loaded successfully.");
            }
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "Error loading library: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onSaveLibrary() {
        if (beforeSaveLibrary != null) {
            beforeSaveLibrary.run();
        }
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Machine Library (.mlib)", "mlib"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".mlib")) {
            file = new File(file.getAbsolutePath() + ".mlib");
        }
        try {
            JsonModelSerializer.saveMachineLibrary(assembly.getMachineLibrary(), file);
            JOptionPane.showMessageDialog(this, "Library saved successfully.");
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "Error saving library: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
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
