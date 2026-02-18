package editor;

import assembly.Assembly;
import machinery.*;
import pws.PWSStateMachine;
import serializer.JsonModelSerializer;
// SVG export removed: not used when exporting PDFs

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.*;
import java.util.ArrayDeque;
import java.util.Deque;

/** Swing editor frame for a state machine and its canvas. */
@SuppressWarnings("this-escape")
public class StateMachineEditor extends JFrame {
    private static final long serialVersionUID = 1L;

    protected StateMachine stateMachine;
    protected StateMachinePanel statePanel;
    protected Assembly assembly;
    private transient Runnable closeCallback = null;
    private transient Runnable modelChangedCallback = null;
    private JCheckBoxMenuItem editModeItem;
    private JCheckBoxMenuItem showGridItem;
    private JMenuItem undoItem;
    private JMenuItem redoItem;
    private static final int MAX_UNDO = 100;
    private final ArrayDeque<String> undoStack = new ArrayDeque<>();
    private final ArrayDeque<String> redoStack = new ArrayDeque<>();
    private String currentSnapshot;
    private boolean suppressDirtyNotifications = false;
    private boolean undoRecordingSuspended = false;

    // Callback interface for close requests
    public void setCloseCallback(Runnable callback) {
        this.closeCallback = callback;
    }

    /** Optional callback for model changes (used by embedding editors). */
    public void setModelChangedCallback(Runnable callback) {
        this.modelChangedCallback = callback;
    }

    /** Notify host that the model changed (e.g., enabling/disabling transitions). */
    public void notifyModelChanged() {
        if (modelChangedCallback != null) {
            modelChangedCallback.run();
        }
    }

    // Default constructor (uses title "StateMachine Editor")
    public StateMachineEditor(StateMachine stateMachine, String title) {
        super(title);
        this.stateMachine = stateMachine;
        initComponents();
    }

    // New constructor that allows specifying a title (e.g. "id : M")
    public StateMachineEditor(StateMachine stateMachine, Assembly assembly, String title) {
        super(title);
        this.stateMachine = stateMachine;
        this.assembly = assembly;
        initComponents();
    }

    private void initComponents() {
        statePanel = new StateMachinePanel(stateMachine);
        statePanel.setOwningEditor(this);
        getContentPane().add(statePanel, BorderLayout.CENTER);
        setJMenuBar(createMenuBar());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        installUndoRedoKeyBindings();
        initializeUndoHistory();
    }

    protected JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        final int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        // File Menu
        JMenu fileMenu = new JMenu("File");
// --- Existing File Menu Items above ---

// Load Single Machine
        JMenuItem loadMachineItem = new JMenuItem("Load Single Machine");
        loadMachineItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, menuMask));
        loadMachineItem.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Machine File (sm)", "sm"));
            int option = fileChooser.showOpenDialog(StateMachineEditor.this);
            if (option == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                try {
                    JsonModelSerializer.LoadedStateMachine loaded = JsonModelSerializer.loadStateMachineWithAnnotations(file);
                    StateMachine loadedMachine = loaded != null ? loaded.getModel() : null;
                    if (loadedMachine == null) {
                        JOptionPane.showMessageDialog(StateMachineEditor.this,
                                "File does not contain a valid StateMachine.");
                        return;
                    }

                    stateMachine.setStates(loadedMachine.getStates());
                    stateMachine.setTransitions(loadedMachine.getTransitions());
                    stateMachine.setEvents(loadedMachine.getEvents());
                    stateMachine.setName(loadedMachine.getName());
                    stateMachine.setPseudoState(loadedMachine.getPseudoState());
                    if (loaded != null && statePanel != null) {
                        statePanel.importAliasData(loaded.getAnnotations());
                    }

                    JOptionPane.showMessageDialog(StateMachineEditor.this,
                            "Machine successfully loaded: " + loadedMachine.getName());
                    statePanel.repaint();
                    initializeUndoHistory();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(StateMachineEditor.this,
                            "Error loading machine: " + ex.getMessage());
                }
            }
        });

// Save Single Machine
        JMenuItem saveMachineItem = new JMenuItem("Save Single Machine");
        saveMachineItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, menuMask));
        saveMachineItem.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Machine File (sm)", "sm"));
            int option = fileChooser.showSaveDialog(StateMachineEditor.this);
            if (option == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                if (!file.getName().toLowerCase().endsWith(".sm")) {
                    file = new File(file.getAbsolutePath() + ".sm");
                }
                try {
                    StateMachinePanel.AliasData aliasData = (statePanel != null) ? statePanel.exportAliasData() : null;
                    JsonModelSerializer.saveStateMachine(stateMachine, aliasData, file);
                    JOptionPane.showMessageDialog(StateMachineEditor.this,
                            "Machine saved: " + stateMachine.getName());
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(StateMachineEditor.this,
                            "Error saving machine: " + ex.getMessage());
                }
            }
        });
        fileMenu.add(saveMachineItem);

// --- Then the existing Exit menu item follows ---

        JMenuItem closeEditorItem = new JMenuItem("Close Editor");
        closeEditorItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, menuMask));
        closeEditorItem.addActionListener(e -> {
            if (closeCallback != null) {
                closeCallback.run();
            } else {
                StateMachineEditor.this.dispose();
            }
        });
        fileMenu.add(loadMachineItem);
        fileMenu.add(saveMachineItem);
        fileMenu.addSeparator();

        JMenuItem exportPDFItem = new JMenuItem("Export as PDF");
        exportPDFItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, menuMask | InputEvent.SHIFT_DOWN_MASK));
        exportPDFItem.addActionListener(e -> {
            StateMachinePanel panel = statePanel;
            if (panel == null) {
                JOptionPane.showMessageDialog(StateMachineEditor.this,
                        "State machine panel is not available.",
                        "Not Available", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (showGridItem != null && panel.isShowGrid() != showGridItem.isSelected()) {
                panel.setShowGrid(showGridItem.isSelected());
            }

            utility.PDFExportDialog.Result exportTarget = utility.PDFExportDialog.showSaveDialog(StateMachineEditor.this);
            if (exportTarget.destination() == utility.PDFExportDialog.Destination.CANCEL) {
                return;
            }

            boolean selectionOnlyExport = false;
            try {
                Rectangle exportRegion = panel.hasObjectSelection()
                        ? panel.getSelectionBoundsForExport()
                        : null;
                if (exportRegion != null) {
                    selectionOnlyExport = panel.beginSelectionOnlyExport();
                }
                panel.setRenderSelectionHighlights(false);
                if (exportTarget.destination() == utility.PDFExportDialog.Destination.CLIPBOARD) {
                    utility.PDFExporter.exportPanelToClipboard(panel, exportRegion);
                    JOptionPane.showMessageDialog(StateMachineEditor.this,
                            (exportRegion != null)
                                    ? "PDF copied to clipboard (selected objects)."
                                    : "PDF copied to clipboard.");
                } else {
                    utility.PDFExporter.exportPanelToPDF(panel, exportTarget.file(), exportRegion);
                    JOptionPane.showMessageDialog(StateMachineEditor.this,
                            (exportRegion != null)
                                    ? "PDF file successfully saved (selected objects)."
                                    : "PDF file successfully saved.");
                }
            } catch (UnsupportedOperationException uoe) {
                uoe.printStackTrace();
                JOptionPane.showMessageDialog(StateMachineEditor.this,
                        "PDF export is not available: " + uoe.getMessage(),
                        "Not Available", JOptionPane.WARNING_MESSAGE);
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(StateMachineEditor.this,
                        "Error exporting PDF: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            } finally {
                if (panel != null) {
                    if (selectionOnlyExport) {
                        panel.endSelectionOnlyExport();
                    }
                    panel.setRenderSelectionHighlights(true);
                    panel.repaint();
                }
            }
        });
        fileMenu.add(exportPDFItem);

        JMenuItem saveAsPNGItem = new JMenuItem("Export as PNG");
        saveAsPNGItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, menuMask));
        saveAsPNGItem.addActionListener(e -> {
            StateMachinePanel panel = statePanel;
            if (panel == null) {
                JOptionPane.showMessageDialog(StateMachineEditor.this,
                        "State machine panel is not available.",
                        "Not Available", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (showGridItem != null && panel.isShowGrid() != showGridItem.isSelected()) {
                panel.setShowGrid(showGridItem.isSelected());
            }

            utility.PNGExportDialog.Result exportTarget = utility.PNGExportDialog.showSaveDialog(StateMachineEditor.this);
            if (exportTarget.destination() == utility.PNGExportDialog.Destination.CANCEL) {
                return;
            }

            boolean selectionOnlyExport = false;
            try {
                Rectangle exportRegion = panel.hasObjectSelection()
                        ? panel.getSelectionBoundsForExport()
                        : null;
                if (exportRegion != null) {
                    selectionOnlyExport = panel.beginSelectionOnlyExport();
                }
                panel.setRenderSelectionHighlights(false);
                if (exportTarget.destination() == utility.PNGExportDialog.Destination.CLIPBOARD) {
                    utility.PNGExporter.exportPanelToClipboard(panel, exportRegion);
                    JOptionPane.showMessageDialog(StateMachineEditor.this,
                            (exportRegion != null)
                                    ? "PNG copied to clipboard (selected objects)."
                                    : "PNG copied to clipboard.");
                } else {
                    utility.PNGExporter.exportPanelToPNG(panel, exportTarget.file(), exportRegion);
                    JOptionPane.showMessageDialog(StateMachineEditor.this,
                            (exportRegion != null)
                                    ? "PNG file successfully saved (selected objects)."
                                    : "PNG file successfully saved.");
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(StateMachineEditor.this,
                        "Error exporting PNG: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            } finally {
                if (panel != null) {
                    if (selectionOnlyExport) {
                        panel.endSelectionOnlyExport();
                    }
                    panel.setRenderSelectionHighlights(true);
                    panel.repaint();
                }
            }
        });
        fileMenu.add(saveAsPNGItem);
        fileMenu.add(closeEditorItem);
        menuBar.add(fileMenu);

        // Edit Menu
        JMenu editMenu = new JMenu("Edit");

        undoItem = new JMenuItem("Undo");
        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask));
        undoItem.addActionListener(e -> performUndo());
        editMenu.add(undoItem);

        redoItem = new JMenuItem("Redo");
        redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask | InputEvent.SHIFT_DOWN_MASK));
        redoItem.addActionListener(e -> performRedo());
        editMenu.add(redoItem);

        editMenu.addSeparator();

        JMenuItem selectAllItem = new JMenuItem("Select All");
        selectAllItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, menuMask));
        selectAllItem.addActionListener(e -> {
            if (statePanel != null) {
                statePanel.selectAllObjects();
            }
        });
        editMenu.add(selectAllItem);

// 3. Add transition
//        JMenuItem addTransitionItem = new JMenuItem("Add Transition");
//        addTransitionItem.addActionListener(e -> {
//            String sourceName = JOptionPane.showInputDialog(this, "Enter source state name:");
//            String targetName = JOptionPane.showInputDialog(this, "Enter target state name:");
//            if (sourceName != null && targetName != null) {
//                StateInterface source = findStateByName(sourceName);
//                StateInterface target = findStateByName(targetName);
//                if (source != null && target != null) {
//                    String trigger = JOptionPane.showInputDialog(this, "Enter trigger event (leave empty for autonomous):");
//                    boolean autonomous = (trigger == null || trigger.trim().isEmpty());
//                    TransitionInterface newTransition = new Transition(source, target, autonomous, trigger);
//                    stateMachine.addTransition(newTransition);
//                    statePanel.repaint();
//                } else {
//                    JOptionPane.showMessageDialog(this, "Source or target state not found.");
//                }
//            }
//        });
//        editMenu.add(addTransitionItem);

// 4. Edit mode (checkbox)
        editModeItem = new JCheckBoxMenuItem("Edit mode", true);
        editModeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, menuMask));
        editModeItem.addActionListener(e -> setEditModeEnabled(editModeItem.isSelected()));
        editMenu.add(editModeItem);

        editMenu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                updateUndoRedoMenuItems();
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });

        updateUndoRedoMenuItems();

        menuBar.add(editMenu);
        // View menu (grid and snapping)
        JMenu viewMenu = new JMenu("View");

        showGridItem = new JCheckBoxMenuItem("Show grid", statePanel != null && statePanel.isShowGrid());
        showGridItem.addActionListener(e -> setShowGridEnabled(showGridItem.isSelected()));
        viewMenu.add(showGridItem);

        JCheckBoxMenuItem snapToGridItem = new JCheckBoxMenuItem("Snap to grid", true);
        snapToGridItem.addActionListener(e -> statePanel.setSnapToGrid(snapToGridItem.isSelected()));
        viewMenu.add(snapToGridItem);

        JMenuItem gridSizeItem = new JMenuItem("Set grid size...");
        gridSizeItem.addActionListener(e -> {
            String input = JOptionPane.showInputDialog(this, "Grid size (pixels):", statePanel.getGridSize());
            if (input != null) {
                try {
                    int size = Integer.parseInt(input.trim());
                    if (size > 0) {
                        statePanel.setGridSize(size);
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid value", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        viewMenu.add(gridSizeItem);

        menuBar.add(viewMenu);

        return menuBar;
    }

    private void updateUndoRedoMenuItems() {
        if (undoItem == null || redoItem == null) {
            return;
        }
        pws.editor.PWSEditor pe = findOwningPWSEditor();
        boolean canUndo = pe != null ? pe.canUndo() : !undoStack.isEmpty();
        boolean canRedo = pe != null ? pe.canRedo() : !redoStack.isEmpty();
        undoItem.setEnabled(canUndo);
        redoItem.setEnabled(canRedo);
    }

    private pws.editor.PWSEditor findOwningPWSEditor() {
        if (statePanel == null) {
            return null;
        }
        java.awt.Window w = SwingUtilities.getWindowAncestor(statePanel);
        if (w instanceof pws.editor.PWSEditor pe) {
            return pe;
        }
        return null;
    }

    private void initializeUndoHistory() {
        undoStack.clear();
        redoStack.clear();
        currentSnapshot = captureSnapshot();
        updateUndoRedoMenuItems();
    }

    public void markDocumentDirty() {
        if (suppressDirtyNotifications || undoRecordingSuspended) {
            return;
        }
        if (findOwningPWSEditor() != null) {
            return;
        }
        String snap = captureSnapshot();
        if (snap == null) {
            return;
        }
        if (currentSnapshot != null && currentSnapshot.equals(snap)) {
            return;
        }
        if (currentSnapshot != null) {
            undoStack.push(currentSnapshot);
            while (undoStack.size() > MAX_UNDO) {
                undoStack.removeLast();
            }
        }
        currentSnapshot = snap;
        redoStack.clear();
        updateUndoRedoMenuItems();
    }

    private String captureSnapshot() {
        if (stateMachine == null) {
            return null;
        }
        try {
            StateMachinePanel.AliasData aliasData = (statePanel != null)
                    ? statePanel.exportAliasData()
                    : null;
            return JsonModelSerializer.saveStateMachineToJson(stateMachine, aliasData);
        } catch (Exception ex) {
            return null;
        }
    }

    private void applySnapshot(String json) {
        if (json == null) return;
        boolean prevSuppress = suppressDirtyNotifications;
        suppressDirtyNotifications = true;
        undoRecordingSuspended = true;
        try {
            JsonModelSerializer.LoadedStateMachine loaded = JsonModelSerializer.loadStateMachineFromJson(json);
            if (loaded == null || loaded.getModel() == null) {
                return;
            }
            StateMachine loadedMachine = loaded.getModel();
            stateMachine.setStates(loadedMachine.getStates());
            stateMachine.setTransitions(loadedMachine.getTransitions());
            stateMachine.setEvents(loadedMachine.getEvents());
            stateMachine.setName(loadedMachine.getName());
            stateMachine.setPseudoState(loadedMachine.getPseudoState());
            if (statePanel != null) {
                statePanel.importAliasData(loaded.getAnnotations());
                statePanel.revalidate();
                statePanel.repaint();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Undo/redo failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            suppressDirtyNotifications = prevSuppress;
            undoRecordingSuspended = false;
        }
    }

    private void performUndo() {
        pws.editor.PWSEditor pe = findOwningPWSEditor();
        if (pe != null) {
            pe.performUndo();
            return;
        }
        undo();
    }

    private void performRedo() {
        pws.editor.PWSEditor pe = findOwningPWSEditor();
        if (pe != null) {
            pe.performRedo();
            return;
        }
        redo();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        if (currentSnapshot != null) {
            redoStack.push(currentSnapshot);
        }
        String target = undoStack.pop();
        applySnapshot(target);
        currentSnapshot = target;
        updateUndoRedoMenuItems();
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        if (currentSnapshot != null) {
            undoStack.push(currentSnapshot);
            while (undoStack.size() > MAX_UNDO) {
                undoStack.removeLast();
            }
        }
        String target = redoStack.pop();
        applySnapshot(target);
        currentSnapshot = target;
        updateUndoRedoMenuItems();
    }

    private void installUndoRedoKeyBindings() {
        JRootPane root = getRootPane();
        if (root == null) return;
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, menuMask), "redo");
        am.put("redo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performRedo();
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask), "undo");
        am.put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performUndo();
            }
        });
    }

    private StateInterface findStateByName(String name) {
        for (StateInterface s : stateMachine.getStates()) {
            if (s.getName().equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }

    public StateMachinePanel getStateMachinePanel() {
        return statePanel;
    }

    /** Keeps the Edit mode menu item and panel in sync. */
    public void setEditModeEnabled(boolean enabled) {
        if (editModeItem != null) {
            editModeItem.setSelected(enabled);
        }
        if (statePanel != null) {
            statePanel.setEditMode(enabled);
        }
    }

    /** Keeps the Show grid menu item and panel in sync. */
    public void setShowGridEnabled(boolean enabled) {
        if (showGridItem != null) {
            showGridItem.setSelected(enabled);
        }
        if (statePanel != null) {
            statePanel.setShowGrid(enabled);
        }
    }

    public void setStateMachine(PWSStateMachine stateMachine) {
        this.stateMachine = stateMachine;
        if (this.statePanel != null) this.statePanel.setStateMachine(stateMachine);
        if (findOwningPWSEditor() == null) {
            initializeUndoHistory();
        }
    }

    // Generic binder for machinery.StateMachine instances so external callers can swap the edited machine.
    public void bindStateMachine(StateMachine sm) {
        this.stateMachine = sm;
        if (this.statePanel != null) this.statePanel.setStateMachine(sm);
        if (this.statePanel != null) {
            this.statePanel.revalidate();
            this.statePanel.repaint();
        }
        if (findOwningPWSEditor() == null) {
            initializeUndoHistory();
        }
    }
}
