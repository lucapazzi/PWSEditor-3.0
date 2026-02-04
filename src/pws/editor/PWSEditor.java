package pws.editor;

import assembly.Assembly;
import assembly.AssemblyInterface;
import assembly.GuardActionsPair;
import editor.StateMachineEditor;
import editor.StateMachinePanel;
import machinery.StateMachine;
import pws.PWSState;
import pws.PWSStateMachine;
import pws.editor.annotation.StateSemanticsAnnotation;
import pws.editor.semantics.Semantics;
import serializer.JsonModelSerializer;
import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Logger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

import javax.swing.*;
import javax.swing.InputMap;
import javax.swing.ActionMap;
import javax.swing.KeyStroke;
import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;
import java.awt.*;
import java.io.*;

import pws.editor.PWSStateMachineEditor;
import pws.editor.PWSStateMachinePanel;
import javax.swing.JCheckBoxMenuItem;

/** Main Swing application window for the PWS editor. */
public class PWSEditor extends JFrame {

    // private Assembly assembly;
    private PWSStateMachine pwsStateMachine;
    // Document and file manager for classic file operations
    private PWSDocument currentDocument;
    private PWSFileManager fileManager;
    private StateMachineEditor baseEditor;  // Editor for the current state machine
    private PWSPanel assemblyPanel;         // Panel to manage the Assembly
    private MachineLibraryPanel libraryPanel; // inline library panel (exposed to menu actions)
    private InitialConfigurationsPanel initialConfigsPanel;
    private JTabbedPane tabbedPane;         // Panel to switch between baseEditor and assemblyPanel
    private StateMachineEditor embeddedEditor = null; // single reusable embedded editor for assembly machines
    private JPanel machineEditorContainer; // promoted so removal callback can clear it
    private String embeddedMachineId = null;
    private CardLayout topCardsLayout;      // CardLayout for assembly/library switch
    private JPanel topSwitchPanel;          // Panel containing assembly/library cards
    private JToggleButton btnAssembly; // Assembly toggle button reference
    private JToggleButton btnLibraryToggle; // Library toggle button reference
    private JSplitPane mainSplitPane;
    private JSplitPane rightSplitPane;
    private JSplitPane assemblySplitPane;
    // Whether the left-hand controller editor should be shown.
    private boolean controllerEditorVisible = false;
    // Menu items that depend on the controller/editor being present
    private JMenuItem saveItem;
    private JMenuItem saveAsItem;
    private JMenuItem closeItem;
    private JMenuItem exportPDFItem;
    private JCheckBoxMenuItem editModeItem;
    private JCheckBoxMenuItem showStateAnn;
    private JCheckBoxMenuItem showExitZoneMachineIdsItem;
    private JCheckBoxMenuItem showGridItem;
    private JCheckBoxMenuItem snapToGridItem;
    private JMenuItem gridSizeItem;
    private JMenu stateSizeMenu;
    private JMenu stateBorderMenu;
    private JMenu stateFontMenu;
    private JMenuItem ltlEditorItem;
    private JMenuItem ltlCheckNowItem;
    private LTLChecksDialog ltlChecksDialog;
    // Track current semantics recalculation worker for debouncing
    private SwingWorker<Void, Void> currentSemanticsWorker = null;
    private JMenuItem undoItem;
    private JMenuItem redoItem;
    private final Deque<EditorSnapshot> undoStack = new ArrayDeque<>();
    private final Deque<EditorSnapshot> redoStack = new ArrayDeque<>();
    private EditorSnapshot currentSnapshot = null;
    private boolean undoRecordingSuspended = false;
    private boolean suppressDirtyNotifications = false;
    private static final int MAX_UNDO = 100;

    // The main PWSEditor window uses a fixed title, e.g. "PWSEditor"
    /**
     * Creates the main editor window for a PWS state machine.
     *
     * @param machine state machine to edit
     */
    public PWSEditor(PWSStateMachine machine) {
        super("PWSEditor");
        // Use the specialized PWSStateMachine:
        if (machine instanceof PWSStateMachine) {
            this.pwsStateMachine = ((PWSStateMachine) machine).clone();
        } else if (machine != null) {
            this.pwsStateMachine = new PWSStateMachine(machine.getName());
        } else {
            this.pwsStateMachine = null;
        }
        // By default we start WITHOUT showing the controller editor; it will be
        // enabled when the user issues New/Open from the File menu.
        this.controllerEditorVisible = false;
        initComponents();
        // Initialize file manager. Document will be created when New/Open is used.
        this.fileManager = new PWSFileManager(this);
        this.currentDocument = null;
        updateWindowTitle();
        initializeUndoHistory();
    }

    // Helper stream that can append objects to an existing object stream
    private static class AppendingObjectOutputStream extends ObjectOutputStream {
        public AppendingObjectOutputStream(OutputStream out) throws IOException {
            super(out);
        }

        @Override
        protected void writeStreamHeader() throws IOException {
            // Do not write a header when appending
        }
    }

    private static class EditorSnapshot {
        private final String json;

        private EditorSnapshot(String json) {
            this.json = json;
        }
    }
    private void initComponents() {
        // Don't set the menu bar at the frame level anymore
        // setJMenuBar(createMenuBar());

        // Left editor area (wrapped with a header)
        JPanel editorInner = new JPanel(new BorderLayout());
        if (controllerEditorVisible && pwsStateMachine != null) {
            baseEditor = new PWSStateMachineEditor(pwsStateMachine, "PWSMachine");
            editorInner.add(baseEditor.getContentPane(), BorderLayout.CENTER);
        } else {
            // Placeholder shown until user creates/loads a document
            JLabel placeholderCtrl = new JLabel("No controller loaded. Use File -> New or Open.", SwingConstants.CENTER);
            placeholderCtrl.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
            editorInner.add(placeholderCtrl, BorderLayout.CENTER);
        }

        JPanel leftWrapper = new JPanel(new BorderLayout());
        
        // Top section: header + menu bar
        JPanel leftTopSection = new JPanel(new BorderLayout());
        JLabel leftHeader = new JLabel("Controller", SwingConstants.CENTER);
        leftHeader.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        leftHeader.setFont(leftHeader.getFont().deriveFont(Font.BOLD));
        leftTopSection.add(leftHeader, BorderLayout.NORTH);
        
        // Add menu bar below the header
        JMenuBar controllerMenuBar = createMenuBar();
        leftTopSection.add(controllerMenuBar, BorderLayout.SOUTH);
        
        leftWrapper.add(leftTopSection, BorderLayout.NORTH);
        leftWrapper.add(editorInner, BorderLayout.CENTER);

        // Ensure clicks anywhere on the left editor area transfer focus to the controller's panel
        Component controllerPanel = (baseEditor != null) ? baseEditor.getStateMachinePanel() : null;
        java.awt.event.MouseAdapter focusRequester = new java.awt.event.MouseAdapter() {
            private void requestCtrlFocus() {
                if (controllerPanel == null) return;
                // Clear any global focus owner (embedded editor may hold it)
                try {
                    java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
                } catch (Exception ignored) {}
                // Ask for focus on the controller panel on the EDT
                javax.swing.SwingUtilities.invokeLater(() -> {
                    try {
                        controllerPanel.requestFocusInWindow();
                    } catch (Exception ignored) {}
                });
            }

            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                requestCtrlFocus();
            }

            @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                requestCtrlFocus();
            }
        };
        // Install listener on header and the editor wrapper so clicks reach the state panel
        leftTopSection.addMouseListener(focusRequester);
        leftHeader.addMouseListener(focusRequester);
        editorInner.addMouseListener(focusRequester);
        leftWrapper.addMouseListener(focusRequester);
        // Also attach directly to the controller panel so clicks on its child components transfer focus
        if (controllerPanel != null) {
            controllerPanel.addMouseListener(focusRequester);
        }

        // Right area: assembly list + embedded machine editor container (also with header)
        assemblyPanel = (controllerEditorVisible && pwsStateMachine != null)
                ? new PWSPanel(pwsStateMachine.getAssembly())
                : null;

        JPanel rightTop = new JPanel(new BorderLayout());
        // Create a split view: Assembly | Library
        this.libraryPanel = new MachineLibraryPanel(pwsStateMachine.getAssembly());
        this.libraryPanel.setBeforeSaveLibrary(() -> syncEmbeddedLibraryAliasData());
        this.libraryPanel.setLibraryChangedCallback(() -> {
            markDocumentDirty();
            scheduleSemanticsRecalculation();
        });

        JPanel assemblyWrapper = new JPanel(new BorderLayout());
        JLabel rightHeader = new JLabel("Assembly", SwingConstants.CENTER);
        rightHeader.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        rightHeader.setFont(rightHeader.getFont().deriveFont(Font.BOLD));
        assemblyWrapper.add(rightHeader, BorderLayout.NORTH);
        JPanel assemblyContent = new JPanel(new BorderLayout());
        if (assemblyPanel != null) {
            assemblyContent.add(assemblyPanel, BorderLayout.CENTER);
        } else {
            JLabel placeholderAsm = new JLabel("No controller loaded. Use File -> New or Open.", SwingConstants.CENTER);
            placeholderAsm.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            assemblyContent.add(placeholderAsm, BorderLayout.CENTER);
        }

        initialConfigsPanel = new InitialConfigurationsPanel();
        if (assemblyPanel == null) {
            initialConfigsPanel.setPlaceholder("No controller loaded.");
        }

        assemblySplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, assemblyContent, initialConfigsPanel);
        assemblySplitPane.setResizeWeight(0.7);
        assemblySplitPane.setOneTouchExpandable(true);
        assemblyWrapper.add(assemblySplitPane, BorderLayout.CENTER);

        JPanel libraryWrapper = new JPanel(new BorderLayout());
        JLabel libHeader = new JLabel("Library", SwingConstants.CENTER);
        libHeader.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        libHeader.setFont(libHeader.getFont().deriveFont(Font.BOLD));
        libraryWrapper.add(libHeader, BorderLayout.NORTH);
        libraryWrapper.add(libraryPanel, BorderLayout.CENTER);

        // Create a single top area that alternates Assembly and Library (CardLayout)
        topCardsLayout = new CardLayout();
        JPanel topCardPanel = new JPanel(new BorderLayout());

        topSwitchPanel = new JPanel(topCardsLayout);
        topSwitchPanel.add(assemblyWrapper, "assembly");
        topSwitchPanel.add(libraryWrapper, "library");

        // small toolbar to switch between Assembly and Library views
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        btnAssembly = new JToggleButton("Assembly");
        btnLibraryToggle = new JToggleButton("Library");
        ButtonGroup bg = new ButtonGroup();
        bg.add(btnAssembly); bg.add(btnLibraryToggle);
        btnAssembly.setSelected(controllerEditorVisible);
        if (!controllerEditorVisible) {
            btnLibraryToggle.setSelected(true);
        }
        tb.add(btnAssembly); tb.add(btnLibraryToggle);

        btnAssembly.addActionListener(a -> {
            topCardsLayout.show(topSwitchPanel, "assembly");
            markDocumentDirty();
        });
        btnLibraryToggle.addActionListener(a -> {
            topCardsLayout.show(topSwitchPanel, "library");
            markDocumentDirty();
        });
        if (!controllerEditorVisible) {
            btnAssembly.setEnabled(false);
            topCardsLayout.show(topSwitchPanel, "library");
        }

        topCardPanel.add(tb, BorderLayout.NORTH);
        topCardPanel.add(topSwitchPanel, BorderLayout.CENTER);

        // Create the machine editor container (bottom half of the right area)
        machineEditorContainer = new JPanel(new BorderLayout());
        JLabel placeholder = new JLabel("Select a machine (assembly or library) to edit", SwingConstants.CENTER);
        machineEditorContainer.add(placeholder, BorderLayout.CENTER);

        // Vertical split on the right: top cards (assembly/library) above the embedded editor
        rightSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topCardPanel, machineEditorContainer);
        rightSplitPane.setResizeWeight(0.25);
        rightSplitPane.setOneTouchExpandable(true);

        mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftWrapper, rightSplitPane);
        mainSplitPane.setResizeWeight(0.7);
        getContentPane().add(mainSplitPane, BorderLayout.CENTER);

        // Wire selection from the assembly panel to show the selected machine in the embedded editor
        if (assemblyPanel != null) {
            assemblyPanel.setMachineSelectionListener(new pws.editor.PWSPanel.MachineSelectionListener() {
            @Override
            public void machineSelected(String id) {
                syncEmbeddedLibraryAliasData();
                StateMachine machine = pwsStateMachine.getAssembly().getStateMachines().get(id);
                if (machine != null) {
                    SwingUtilities.invokeLater(() -> {
                        machineEditorContainer.removeAll();
                        try {
                            String title = id + " : " + (machine.getName() != null ? machine.getName() : "");
                            if (embeddedEditor == null) {
                                embeddedEditor = new StateMachineEditor(machine, pwsStateMachine.getAssembly(), title);
                                embeddedEditor.setModelChangedCallback(() -> scheduleSemanticsRecalculation());
                                embeddedEditor.setCloseCallback(() -> {
                                    syncEmbeddedLibraryAliasData();
                                    embeddedEditor = null;
                                    machineEditorContainer.removeAll();
                                    JLabel placeholder = new JLabel("Select a machine (assembly or library) to edit", SwingConstants.CENTER);
                                    machineEditorContainer.add(placeholder, BorderLayout.CENTER);
                                    machineEditorContainer.revalidate();
                                    machineEditorContainer.repaint();
                                    embeddedMachineId = null;
                                        // Restore focus to the main controller panel when embedded editor is closed
                                        SwingUtilities.invokeLater(() -> {
                                            try {
                                                Component ctrl = baseEditor.getStateMachinePanel();
                                                if (ctrl != null) ctrl.requestFocusInWindow();
                                            } catch (Exception ignored) {}
                                        });
                                });
                            } else {
                                embeddedEditor.bindStateMachine(machine);
                            }

                            // remember which id is currently embedded
                            embeddedMachineId = id;

                            JMenuBar mb = embeddedEditor.getJMenuBar();
                            StateMachinePanel smPanel = embeddedEditor.getStateMachinePanel();
                            if (smPanel != null) {
                                String libKey = findLibraryKeyForMachine(machine);
                                StateMachinePanel.AliasData aliasData = (libKey != null)
                                        ? pwsStateMachine.getAssembly().getMachineLibrary().getAliasData(libKey)
                                        : pwsStateMachine.getAssembly().getAliasData(id);
                                smPanel.importAliasData(aliasData);
                            }

                            JPanel wrapper = new JPanel(new BorderLayout());
                            JPanel topArea = new JPanel(new BorderLayout());
                            if (mb != null) topArea.add(mb, BorderLayout.NORTH);
                            JLabel header = new JLabel("Assembly: " + title, SwingConstants.CENTER);
                            header.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
                            header.setFont(header.getFont().deriveFont(Font.BOLD));
                            header.setForeground(Color.BLACK);
                            header.setOpaque(true);
                            header.setBackground(new Color(245, 245, 255));
                            topArea.add(header, BorderLayout.SOUTH);

                            wrapper.add(topArea, BorderLayout.NORTH);
                            wrapper.add(smPanel, BorderLayout.CENTER);

                            machineEditorContainer.add(wrapper, BorderLayout.CENTER);
                            machineEditorContainer.revalidate();
                            machineEditorContainer.repaint();
                        } catch (Exception ex) {
                            machineEditorContainer.removeAll();
                            JPanel wrapper = new JPanel(new BorderLayout());
                            String title = id + " : " + (machine.getName() != null ? machine.getName() : "");
                            JLabel header = new JLabel(title, SwingConstants.CENTER);
                            header.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
                            header.setFont(header.getFont().deriveFont(Font.BOLD));
                            wrapper.add(header, BorderLayout.NORTH);
                            StateMachinePanel smPanel = new StateMachinePanel(machine);
                            String libKey = findLibraryKeyForMachine(machine);
                            StateMachinePanel.AliasData aliasData = (libKey != null)
                                    ? pwsStateMachine.getAssembly().getMachineLibrary().getAliasData(libKey)
                                    : pwsStateMachine.getAssembly().getAliasData(id);
                            smPanel.importAliasData(aliasData);
                            wrapper.add(smPanel, BorderLayout.CENTER);
                            machineEditorContainer.add(wrapper, BorderLayout.CENTER);
                            machineEditorContainer.revalidate();
                            machineEditorContainer.repaint();
                        }
                    });
                }
            }

            @Override
            public void machineRemoved(String id) {
                // If the removed machine is currently embedded, clear the right editor area
                if (id != null && id.equals(embeddedMachineId)) {
                    SwingUtilities.invokeLater(() -> {
                        if (machineEditorContainer != null) {
                            machineEditorContainer.removeAll();
                            JLabel placeholder = new JLabel("Select an assembly machine to edit", SwingConstants.CENTER);
                            machineEditorContainer.add(placeholder, BorderLayout.CENTER);
                            machineEditorContainer.revalidate();
                            machineEditorContainer.repaint();
                        }
                        embeddedMachineId = null;
                        // ensure focus returns to main controller panel after removal
                        try {
                            Component ctrl = baseEditor.getStateMachinePanel();
                            if (ctrl != null) ctrl.requestFocusInWindow();
                        } catch (Exception ignored) {}
                    });
                }
                // Recompute semantics since assembly changes affect configurations/exit zones
                markDocumentDirty();
                SwingUtilities.invokeLater(() -> scheduleSemanticsRecalculation());
            }

            @Override
            public void machineAddedToLibrary(String key) {
                // Refresh library panel and switch to library view
                SwingUtilities.invokeLater(() -> {
                    if (libraryPanel != null) {
                        libraryPanel.refreshList();
                    }
                    // Switch to library view to show the newly added machine
                    if (topCardsLayout != null && topSwitchPanel != null && btnLibraryToggle != null) {
                        btnLibraryToggle.setSelected(true);
                        topCardsLayout.show(topSwitchPanel, "library");
                    }
                });
                // Detach/clone changes the assembly mapping, so refresh semantics/initial configs
                markDocumentDirty();
                SwingUtilities.invokeLater(() -> scheduleSemanticsRecalculation());
            }

            @Override
            public void machineEdited(String id) {
                // Refresh library panel in case the edited machine is in the library
                SwingUtilities.invokeLater(() -> {
                    if (libraryPanel != null) {
                        libraryPanel.refreshList();
                    }
                    // Trigger semantics recalculation since assembly identifiers affect guards/actions
                    scheduleSemanticsRecalculation();
                });
                markDocumentDirty();
            }

            @Override
            public void machineAdded(String id) {
                // Trigger semantics recalculation when a new machine is added to the assembly
                SwingUtilities.invokeLater(() -> {
                    scheduleSemanticsRecalculation();
                });
                markDocumentDirty();
            }
        });
        }

        // Wire library selection to show selected library machine in the same embedded editor
        libraryPanel.setLibrarySelectionListener(new MachineLibraryPanel.LibrarySelectionListener() {
            @Override
            public void librarySelected(String key) {
                syncEmbeddedLibraryAliasData();
                StateMachine machine = pwsStateMachine.getAssembly().getMachineLibrary().get(key);
                if (machine != null) {
                    SwingUtilities.invokeLater(() -> {
                        machineEditorContainer.removeAll();
                        try {
                            String title = machine.getName() != null ? machine.getName() : "Unnamed";
                            if (embeddedEditor == null) {
                                embeddedEditor = new StateMachineEditor(machine, pwsStateMachine.getAssembly(), title);
                                embeddedEditor.setModelChangedCallback(() -> scheduleSemanticsRecalculation());
                                embeddedEditor.setCloseCallback(() -> {
                                    syncEmbeddedLibraryAliasData();
                                    embeddedEditor = null;
                                    machineEditorContainer.removeAll();
                                    JLabel placeholder = new JLabel("Select a machine (assembly or library) to edit", SwingConstants.CENTER);
                                    machineEditorContainer.add(placeholder, BorderLayout.CENTER);
                                    machineEditorContainer.revalidate();
                                    machineEditorContainer.repaint();
                                    embeddedMachineId = null;
                                });
                            } else {
                                embeddedEditor.bindStateMachine(machine);
                            }
                            embeddedMachineId = "lib:" + key;

                            JMenuBar mb = embeddedEditor.getJMenuBar();
                            StateMachinePanel smPanel = embeddedEditor.getStateMachinePanel();
                            if (smPanel != null) {
                                StateMachinePanel.AliasData aliasData = pwsStateMachine.getAssembly()
                                        .getMachineLibrary().getAliasData(key);
                                smPanel.importAliasData(aliasData);
                            }

                            JPanel wrapper = new JPanel(new BorderLayout());
                            JPanel topArea = new JPanel(new BorderLayout());
                            if (mb != null) topArea.add(mb, BorderLayout.NORTH);
                            JLabel header = new JLabel("Library: " + title, SwingConstants.CENTER);
                            header.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
                            header.setFont(header.getFont().deriveFont(Font.BOLD));
                            header.setForeground(new Color(0, 90, 160));
                            header.setOpaque(true);
                            header.setBackground(new Color(235, 245, 255));
                            topArea.add(header, BorderLayout.SOUTH);

                            wrapper.add(topArea, BorderLayout.NORTH);
                            wrapper.add(smPanel, BorderLayout.CENTER);

                            machineEditorContainer.add(wrapper, BorderLayout.CENTER);
                            machineEditorContainer.revalidate();
                            machineEditorContainer.repaint();
                        } catch (Exception ex) {
                            machineEditorContainer.removeAll();
                            JPanel wrapper = new JPanel(new BorderLayout());
                            String title = key + " : " + (machine.getName() != null ? machine.getName() : "");
                            JLabel header = new JLabel("Library: " + title, SwingConstants.CENTER);
                            header.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
                            header.setFont(header.getFont().deriveFont(Font.BOLD));
                            wrapper.add(header, BorderLayout.NORTH);
                            StateMachinePanel smPanel = new StateMachinePanel(machine);
                            StateMachinePanel.AliasData aliasData = pwsStateMachine.getAssembly()
                                    .getMachineLibrary().getAliasData(key);
                            smPanel.importAliasData(aliasData);
                            wrapper.add(smPanel, BorderLayout.CENTER);
                            machineEditorContainer.add(wrapper, BorderLayout.CENTER);
                            machineEditorContainer.revalidate();
                            machineEditorContainer.repaint();
                        }
                    });
                }
            }

            @Override
            public void libraryRemoved(String key) {
                if (embeddedMachineId != null && embeddedMachineId.equals("lib:" + key)) {
                    SwingUtilities.invokeLater(() -> {
                        machineEditorContainer.removeAll();
                        JLabel placeholder = new JLabel("Select a machine (assembly or library) to edit", SwingConstants.CENTER);
                        machineEditorContainer.add(placeholder, BorderLayout.CENTER);
                        machineEditorContainer.revalidate();
                        machineEditorContainer.repaint();
                        embeddedMachineId = null;
                    });
                }
            }

            @Override
            public void libraryRenamed(String key) {
                // Refresh assembly list so names update where referenced
                SwingUtilities.invokeLater(() -> {
                    if (assemblyPanel != null) assemblyPanel.refreshList();
                    // If currently editing this library machine, update embedded editor header
                    if (embeddedMachineId != null && embeddedMachineId.equals("lib:" + key)) {
                        StateMachine machine = pwsStateMachine.getAssembly().getMachineLibrary().get(key);
                        if (machine != null && embeddedEditor != null) {
                            embeddedEditor.bindStateMachine(machine);
                            StateMachinePanel smPanel = embeddedEditor.getStateMachinePanel();
                            if (smPanel != null) {
                                StateMachinePanel.AliasData aliasData = pwsStateMachine.getAssembly()
                                        .getMachineLibrary().getAliasData(key);
                                smPanel.importAliasData(aliasData);
                            }
                            machineEditorContainer.revalidate();
                            machineEditorContainer.repaint();
                        }
                    }
                });
            }

            @Override
            public void libraryLoaded(String key) {
                // treat as selection: open in embedded editor
                librarySelected(key);
            }
        });

        refreshInitialConfigurationsPanel();
        installUndoRedoKeyBindings();
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // File menu
        JMenu fileMenu = new JMenu("File");

//        // Save model item (existing)
//        JMenuItem saveItem = new JMenuItem("Save");
//        saveItem.addActionListener(e -> {
//            JFileChooser fileChooser = new JFileChooser();
//            int option = fileChooser.showSaveDialog(PWSEditor.this);
//            if (option == JFileChooser.APPROVE_OPTION) {
//                String filename = fileChooser.getSelectedFile().getAbsolutePath();
//                try {
//                    BinaryModelSerializer.saveModel(pwsStateMachine, filename);
//                    JOptionPane.showMessageDialog(PWSEditor.this, "Model saved successfully.");
//                } catch (IOException ex) {
//                    ex.printStackTrace();
//                    JOptionPane.showMessageDialog(PWSEditor.this, "Error saving: " + ex.getMessage());
//                }
//            }
//        });
//        fileMenu.add(saveItem);
//
//        // Load model item (existing)
//        JMenuItem loadItem = new JMenuItem("Load");
//        loadItem.addActionListener(e -> {
//            JFileChooser fileChooser = new JFileChooser();
//            int option = fileChooser.showOpenDialog(PWSEditor.this);
//            if (option == JFileChooser.APPROVE_OPTION) {
//                String filename = fileChooser.getSelectedFile().getAbsolutePath();
//                try {
//                    Object loadedModel = BinaryModelSerializer.loadModel(filename);
//                    if (loadedModel instanceof PWSStateMachine) {
//                        pwsStateMachine = (PWSStateMachine) loadedModel;
//                        baseEditor.dispose(); // Close the previous editor if needed
//                        baseEditor = new PWSStateMachineEditor(pwsStateMachine, "PWSMachine");
//
//                        JPanel editorPanel = new JPanel(new BorderLayout());
//                        editorPanel.add(baseEditor.getContentPane(), BorderLayout.CENTER);
//                        tabbedPane.setComponentAt(0, editorPanel);
//
//                        assemblyPanel = new PWSPanel(pwsStateMachine.getAssembly());
//                        tabbedPane.setComponentAt(1, assemblyPanel);
//
//                        revalidate();
//                        repaint();
//                        JOptionPane.showMessageDialog(PWSEditor.this, "Model loaded successfully.");
//                    } else {
//                        JOptionPane.showMessageDialog(PWSEditor.this, "The selected file does not contain a valid model.");
//                    }
//                } catch (IOException | ClassNotFoundException ex) {
//                    ex.printStackTrace();
//                    JOptionPane.showMessageDialog(PWSEditor.this, "Error loading: " + ex.getMessage());
//                }
//            }
//        });
//        fileMenu.add(loadItem);

        // Classic file operations
        JMenuItem newItem = new JMenuItem("New");
        newItem.addActionListener(e -> {
            if (currentDocument != null && currentDocument.isDirty()) {
                Object[] options = new Object[] {"No", "Yes"};
                int opt = JOptionPane.showOptionDialog(PWSEditor.this,
                        "Current document has unsaved changes. Continue and discard?",
                        "Unsaved changes",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        options,
                        options[1]);
                // opt == 1 => Yes
                if (opt != 1) return;
            }
            fileManager.newDocument();
        });
        fileMenu.add(newItem);

        JMenuItem openItem = new JMenuItem("Open...");
        openItem.addActionListener(e -> {
            if (currentDocument != null && currentDocument.isDirty()) {
                Object[] options = new Object[] {"No", "Yes"};
                int opt = JOptionPane.showOptionDialog(PWSEditor.this,
                        "Current document has unsaved changes. Continue and discard?",
                        "Unsaved changes",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        options,
                        options[1]);
                if (opt != 1) return;
            }
            fileManager.open();
        });
        fileMenu.add(openItem);

        saveItem = new JMenuItem("Save");
        saveItem.addActionListener(e -> {
            fileManager.save();
        });
        fileMenu.add(saveItem);

        saveAsItem = new JMenuItem("Save As...");
        saveAsItem.addActionListener(e -> {
            fileManager.saveAs();
        });
        fileMenu.add(saveAsItem);

        closeItem = new JMenuItem("Close");
        closeItem.addActionListener(e -> {
            if (currentDocument != null && currentDocument.isDirty()) {
                Object[] options = new Object[] {"No", "Yes"};
                int opt = JOptionPane.showOptionDialog(PWSEditor.this,
                        "Current document has unsaved changes. Continue and discard?",
                        "Unsaved changes",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        options,
                        options[1]);
                if (opt != 1) return;
            }
            // Create a new empty document (clears the editor)
            fileManager.newDocument();
        });
        fileMenu.add(closeItem);

        // Composite Save/Load moved to toolbar buttons

        // Library save/load moved to the Library panel buttons

        // SVG export removed — prefer PDF export

        // New: Export as PDF menu item.
        exportPDFItem = new JMenuItem("Export as PDF");
        exportPDFItem.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(
                    new javax.swing.filechooser.FileNameExtensionFilter("PDF File", "pdf"));

            if (fileChooser.showSaveDialog(PWSEditor.this)
                    == JFileChooser.APPROVE_OPTION) {

                File file = fileChooser.getSelectedFile();
                if (!file.getName().toLowerCase().endsWith(".pdf")) {
                    file = new File(file.getAbsolutePath() + ".pdf");
                }

                StateMachinePanel panel =
                        ((PWSStateMachineEditor) baseEditor).getStateMachinePanel();

                try {
                    utility.PDFExporter.exportPanelToPDF(panel, file);
                    JOptionPane.showMessageDialog(PWSEditor.this,
                            "PDF file saved successfully.");
                } catch (UnsupportedOperationException uoe) {
                    // PDF export not implemented due to missing dependency (e.g., PDFBox)
                    uoe.printStackTrace();
                    JOptionPane.showMessageDialog(PWSEditor.this,
                            "PDF export is not available: " + uoe.getMessage(),
                            "Not Available", JOptionPane.WARNING_MESSAGE);
                } catch (IOException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(PWSEditor.this,
                            "Error saving PDF: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        fileMenu.add(exportPDFItem);

        // Exit item
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> {
            if (currentDocument != null && currentDocument.isDirty()) {
                Object[] options = new Object[] {"Yes", "No", "Cancel"};
                int opt = JOptionPane.showOptionDialog(PWSEditor.this,
                        "There are unsaved changes. Save before exit?",
                        "Unsaved changes",
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        options,
                        options[0]);
                if (opt == JOptionPane.CLOSED_OPTION || opt == 2) return; // Cancel or closed
                if (opt == 0) {
                    boolean ok = fileManager.save();
                    if (!ok) return; // abort exit if save failed
                }
            }
            System.exit(0);
        });
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        menuBar.add(fileMenu);

        // --- Edit Menu (existing items) ---
        JMenu editMenu = new JMenu("Edit");

        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        undoItem = new JMenuItem("Undo");
        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask));
        undoItem.addActionListener(e -> undo());
        editMenu.add(undoItem);

        redoItem = new JMenuItem("Redo");
        redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask | InputEvent.SHIFT_DOWN_MASK));
        redoItem.addActionListener(e -> redo());
        editMenu.add(redoItem);

        editMenu.addSeparator();

//        JMenuItem addTransitionItem = new JMenuItem("Add Transition");
//        addTransitionItem.addActionListener(e -> {
//            String sourceName = JOptionPane.showInputDialog(PWSEditor.this, "Enter the source state name:");
//            String targetName = JOptionPane.showInputDialog(PWSEditor.this, "Enter the target state name:");
//            if (sourceName != null && targetName != null) {
//                machinery.StateInterface source = findStateByName(sourceName);
//                machinery.StateInterface target = findStateByName(targetName);
//                if (source != null && target != null) {
//                    String trigger = JOptionPane.showInputDialog(PWSEditor.this, "Enter trigger event (leave blank for internal):");
//                    boolean autonomous = (trigger == null || trigger.trim().isEmpty());
//                    pws.PWSTransition newTransition = new pws.PWSTransition(source, target, autonomous, trigger);
//                    GuardActionsPair gap = ((Assembly) pwsStateMachine.getAssembly()).askForGuardAndActions();
//                    if (gap != null) {
//                        newTransition.setGuardProposition(gap.getGuard());
//                        for (assembly.Action act : gap.getActions()) {
//                            newTransition.addAction(act);
//                        }
//                    }
//                    pwsStateMachine.addTransition(newTransition);
//                    baseEditor.getStateMachinePanel().repaint();
//                } else {
//                    JOptionPane.showMessageDialog(PWSEditor.this, "Source or target state not found.");
//                }
//            }
//        });
//        editMenu.add(addTransitionItem);

        editModeItem = new JCheckBoxMenuItem("Edit mode", true);
        editModeItem.addActionListener(e -> {
            setEditModeEnabled(editModeItem.isSelected());
        });
        editMenu.add(editModeItem);

        menuBar.add(editMenu);

        // --- View menu: toggle state dashboards ---
        JMenu viewMenu = new JMenu("View");
        showStateAnn = new JCheckBoxMenuItem("Toggle state dashboards", true);
        showStateAnn.addActionListener(e -> {
            applyDashboardVisibility();
            // Mark document dirty when the user toggles global dashboards
            markDocumentDirty();
        });
        viewMenu.add(showStateAnn);

        showExitZoneMachineIdsItem = new JCheckBoxMenuItem(
            "Show exit-zone machine IDs",
            StateSemanticsAnnotation.isShowExitZoneMachineIds()
        );
        showExitZoneMachineIdsItem.addActionListener(e -> {
            StateSemanticsAnnotation.setShowExitZoneMachineIds(showExitZoneMachineIdsItem.isSelected());
            if (baseEditor == null) return;
            PWSStateMachinePanel panel =
                (PWSStateMachinePanel)((PWSStateMachineEditor) baseEditor).getStateMachinePanel();
            panel.refreshStateAnnotationSizes();
            panel.repaint();
            refreshInitialConfigurationsPanel();
            markDocumentDirty();
        });
        viewMenu.add(showExitZoneMachineIdsItem);

        // Ensure dashboards are visible at startup (preserve per-state visibility)
        try {
            applyDashboardVisibility();
        } catch (Exception ex) {
            // Ignore if panel is not yet ready
        }

        showGridItem = new JCheckBoxMenuItem("Show grid", true);
        showGridItem.addActionListener(e -> {
            if (baseEditor == null) return;
            PWSStateMachinePanel panel =
                (PWSStateMachinePanel)((PWSStateMachineEditor) baseEditor).getStateMachinePanel();
            panel.setShowGrid(showGridItem.isSelected());
            panel.repaint();
            markDocumentDirty();
        });
        viewMenu.add(showGridItem);

        snapToGridItem = new JCheckBoxMenuItem("Snap to grid", true);
        snapToGridItem.addActionListener(e -> {
            if (baseEditor == null) return;
            PWSStateMachinePanel panel =
                (PWSStateMachinePanel)((PWSStateMachineEditor) baseEditor).getStateMachinePanel();
            panel.setSnapToGrid(snapToGridItem.isSelected());
            markDocumentDirty();
        });
        viewMenu.add(snapToGridItem);

        gridSizeItem = new JMenuItem("Set grid size...");
        gridSizeItem.addActionListener(e -> {
            if (baseEditor == null) return;
            PWSStateMachinePanel panel =
                (PWSStateMachinePanel)((PWSStateMachineEditor) baseEditor).getStateMachinePanel();
            String input = JOptionPane.showInputDialog(this, "Grid size (pixels):", panel.getGridSize());
            if (input != null) {
                try {
                    int size = Integer.parseInt(input.trim());
                    if (size > 0) {
                        panel.setGridSize(size);
                        panel.repaint();
                        markDocumentDirty();
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid number", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        viewMenu.add(gridSizeItem);

        stateSizeMenu = new JMenu("State size");
        ButtonGroup sizeGroup = new ButtonGroup();
        int[] sizes = new int[] {40, 50, 60};
        String[] sizeLabels = new String[] {"Small (40)", "Medium (50)", "Large (60)"};
        for (int i = 0; i < sizes.length; i++) {
            int size = sizes[i];
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(sizeLabels[i]);
            item.addActionListener(e -> {
                if (baseEditor == null) return;
                PWSStateMachinePanel panel =
                    (PWSStateMachinePanel)((PWSStateMachineEditor) baseEditor).getStateMachinePanel();
                panel.setStateDiameter(size);
                panel.repaint();
                markDocumentDirty();
            });
            sizeGroup.add(item);
            stateSizeMenu.add(item);
        }
        // Default selection based on current panel state
        try {
            PWSStateMachinePanel panel =
                (PWSStateMachinePanel)((PWSStateMachineEditor) baseEditor).getStateMachinePanel();
            int current = panel.getStateDiameter();
            for (int i = 0; i < sizes.length; i++) {
                if (sizes[i] == current) {
                    ((JRadioButtonMenuItem) stateSizeMenu.getItem(i)).setSelected(true);
                    break;
                }
            }
        } catch (Exception ignored) {}
        viewMenu.add(stateSizeMenu);

        stateBorderMenu = new JMenu("State border thickness");
        ButtonGroup borderGroup = new ButtonGroup();
        float[] thicknesses = new float[] {1.0f, 2.0f, 3.0f};
        String[] thicknessLabels = new String[] {"Thin (1)", "Medium (2)", "Thick (3)"};
        for (int i = 0; i < thicknesses.length; i++) {
            float thickness = thicknesses[i];
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(thicknessLabels[i]);
            item.addActionListener(e -> {
                if (baseEditor == null) return;
                PWSStateMachinePanel panel =
                    (PWSStateMachinePanel)((PWSStateMachineEditor) baseEditor).getStateMachinePanel();
                panel.setStateBorderThickness(thickness);
                panel.repaint();
                markDocumentDirty();
            });
            borderGroup.add(item);
            stateBorderMenu.add(item);
        }
        // Default selection based on current panel state
        try {
            PWSStateMachinePanel panel =
                (PWSStateMachinePanel)((PWSStateMachineEditor) baseEditor).getStateMachinePanel();
            float current = panel.getStateBorderThickness();
            for (int i = 0; i < thicknesses.length; i++) {
                if (Float.compare(thicknesses[i], current) == 0) {
                    ((JRadioButtonMenuItem) stateBorderMenu.getItem(i)).setSelected(true);
                    break;
                }
            }
        } catch (Exception ignored) {}
        viewMenu.add(stateBorderMenu);

        stateFontMenu = new JMenu("State font size");
        ButtonGroup fontGroup = new ButtonGroup();
        float[] fontSizes = new float[] {10f, 12f, 14f};
        String[] fontLabels = new String[] {"Small (10)", "Medium (12)", "Large (14)"};
        for (int i = 0; i < fontSizes.length; i++) {
            float size = fontSizes[i];
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(fontLabels[i]);
            item.addActionListener(e -> {
                if (baseEditor == null) return;
                PWSStateMachinePanel panel =
                    (PWSStateMachinePanel)((PWSStateMachineEditor) baseEditor).getStateMachinePanel();
                panel.setStateFontSize(size);
                panel.repaint();
                markDocumentDirty();
            });
            fontGroup.add(item);
            stateFontMenu.add(item);
        }
        // Default selection based on current panel state
        try {
            PWSStateMachinePanel panel =
                (PWSStateMachinePanel)((PWSStateMachineEditor) baseEditor).getStateMachinePanel();
            float current = panel.getStateFontSize();
            for (int i = 0; i < fontSizes.length; i++) {
                if (Float.compare(fontSizes[i], current) == 0) {
                    ((JRadioButtonMenuItem) stateFontMenu.getItem(i)).setSelected(true);
                    break;
                }
            }
        } catch (Exception ignored) {}
        viewMenu.add(stateFontMenu);

        // LTL Formula editor for the current assembly
        ltlEditorItem = new JMenuItem("LTL Editor...");
        ltlEditorItem.addActionListener(e -> {
            pws.editor.LTLFormulaEditorDialog dlg = new pws.editor.LTLFormulaEditorDialog(
                PWSEditor.this,
                pwsStateMachine.getAssembly(),
                () -> runLTLChecks(false));
            dlg.setVisible(true);
        });
        // Disabled by default (grayed out)
        ltlEditorItem.setEnabled(false);
        viewMenu.add(ltlEditorItem);

        ltlCheckNowItem = new JMenuItem("Check now");
        ltlCheckNowItem.addActionListener(e -> runLTLChecks(true));
        ltlCheckNowItem.setEnabled(false);
        viewMenu.add(ltlCheckNowItem);

        // Ensure menu items reflect initial state
        updateMenuItemsEnabledState();
        updateUndoRedoMenuItems();

        menuBar.add(viewMenu);
        return menuBar;
    }

    private machinery.StateInterface findStateByName(String name) {
        for (machinery.StateInterface s : pwsStateMachine.getStates()) {
            if (s.getName().equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }

    // Document helpers used by the file manager and panels
    public void setDocument(PWSDocument doc) {
        this.currentDocument = doc;
        updateWindowTitle();
        updateMenuItemsEnabledState();
    }

    /** Control whether the left-hand controller editor is shown. */
    public void setControllerEditorVisible(boolean visible) {
        this.controllerEditorVisible = visible;
        updateMenuItemsEnabledState();
    }

    private void updateMenuItemsEnabledState() {
        boolean ctrl = controllerEditorVisible && baseEditor != null;
        // Save/SaveAs/Close require a document
        boolean hasDoc = (currentDocument != null);
        if (saveItem != null) saveItem.setEnabled(hasDoc);
        if (saveAsItem != null) saveAsItem.setEnabled(hasDoc);
        if (closeItem != null) closeItem.setEnabled(hasDoc);

        if (exportPDFItem != null) exportPDFItem.setEnabled(ctrl);

        if (editModeItem != null) editModeItem.setEnabled(ctrl);
        if (showStateAnn != null) showStateAnn.setEnabled(ctrl);
        if (showExitZoneMachineIdsItem != null) showExitZoneMachineIdsItem.setEnabled(ctrl);
        if (showGridItem != null) showGridItem.setEnabled(ctrl);
        if (snapToGridItem != null) snapToGridItem.setEnabled(ctrl);
        if (gridSizeItem != null) gridSizeItem.setEnabled(ctrl);
        if (stateSizeMenu != null) stateSizeMenu.setEnabled(ctrl);
        if (stateBorderMenu != null) stateBorderMenu.setEnabled(ctrl);
        if (stateFontMenu != null) stateFontMenu.setEnabled(ctrl);
        if (ltlEditorItem != null) ltlEditorItem.setEnabled(ctrl);
        if (ltlCheckNowItem != null) ltlCheckNowItem.setEnabled(ctrl);
        if (ctrl) {
            syncViewMenuSelections();
        }
        updateUndoRedoMenuItems();
    }

    public void syncViewMenuSelections() {
        if (!controllerEditorVisible || baseEditor == null) return;
        PWSStateMachinePanel panel =
            (PWSStateMachinePanel)((PWSStateMachineEditor) baseEditor).getStateMachinePanel();
        if (panel == null) return;

        if (editModeItem != null) {
            editModeItem.setSelected(panel.isEditMode());
        }
        if (showGridItem != null) {
            showGridItem.setSelected(panel.isShowGrid());
        }
        if (snapToGridItem != null) {
            snapToGridItem.setSelected(panel.isSnapToGrid());
        }
        if (showExitZoneMachineIdsItem != null) {
            showExitZoneMachineIdsItem.setSelected(StateSemanticsAnnotation.isShowExitZoneMachineIds());
        }

        if (stateSizeMenu != null) {
            int[] sizes = new int[] {40, 50, 60};
            int current = panel.getStateDiameter();
            for (int i = 0; i < sizes.length && i < stateSizeMenu.getItemCount(); i++) {
                if (sizes[i] == current) {
                    JMenuItem mi = stateSizeMenu.getItem(i);
                    if (mi instanceof JRadioButtonMenuItem) {
                        ((JRadioButtonMenuItem) mi).setSelected(true);
                        break;
                    }
                }
            }
        }
        if (stateBorderMenu != null) {
            float[] thicknesses = new float[] {1.0f, 2.0f, 3.0f};
            float current = panel.getStateBorderThickness();
            for (int i = 0; i < thicknesses.length && i < stateBorderMenu.getItemCount(); i++) {
                if (Float.compare(thicknesses[i], current) == 0) {
                    JMenuItem mi = stateBorderMenu.getItem(i);
                    if (mi instanceof JRadioButtonMenuItem) {
                        ((JRadioButtonMenuItem) mi).setSelected(true);
                        break;
                    }
                }
            }
        }
        if (stateFontMenu != null) {
            float[] fontSizes = new float[] {10f, 12f, 14f};
            float current = panel.getStateFontSize();
            for (int i = 0; i < fontSizes.length && i < stateFontMenu.getItemCount(); i++) {
                if (Float.compare(fontSizes[i], current) == 0) {
                    JMenuItem mi = stateFontMenu.getItem(i);
                    if (mi instanceof JRadioButtonMenuItem) {
                        ((JRadioButtonMenuItem) mi).setSelected(true);
                        break;
                    }
                }
            }
        }
    }

    void applyDashboardVisibility() {
        if (!controllerEditorVisible || baseEditor == null) return;
        PWSStateMachinePanel panel =
            (PWSStateMachinePanel)((PWSStateMachineEditor) baseEditor).getStateMachinePanel();
        if (panel == null) return;
        boolean show = (showStateAnn == null) || showStateAnn.isSelected();
        panel.setShowStateAnnotations(show);
        if (show) {
            panel.restoreVisibleStateAnnotations();
        }
        panel.repaint();
    }

    /** Keeps the Edit mode menu item and panel in sync. */
    public void setEditModeEnabled(boolean enabled) {
        boolean changed = false;
        if (baseEditor != null && baseEditor.getStateMachinePanel() != null) {
            changed = baseEditor.getStateMachinePanel().isEditMode() != enabled;
            baseEditor.getStateMachinePanel().setEditMode(enabled);
        }
        if (editModeItem != null) {
            if (editModeItem.isSelected() != enabled) {
                changed = true;
            }
            editModeItem.setSelected(enabled);
        }
        if (changed) {
            markDocumentDirty();
        }
    }

    /** Schedule an asynchronous semantics recalculation for the current model. */
    public void scheduleSemanticsRecalculation() {
        if (this.pwsStateMachine == null) return;

        // Keep initial configurations panel in sync with assembly changes
        SwingUtilities.invokeLater(this::refreshInitialConfigurationsPanel);
        
        // Cancel any previous running worker to avoid duplicate/interleaved computations
        if (currentSemanticsWorker != null && !currentSemanticsWorker.isDone()) {
            currentSemanticsWorker.cancel(false);
        }
        
        // Indicate busy state - always use default cursor as the "old" cursor
        // to avoid stacking wait cursors
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        
        SwingWorker<Void,Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (isCancelled()) return null;
                try {
                    ((pws.PWSStateMachine) PWSEditor.this.pwsStateMachine).recalculateSemantics();
                } catch (Exception ex) {
                    throw ex;
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    if (isCancelled()) return;
                    get();
                    try {
                        if (baseEditor != null) {
                            applyDashboardVisibility();
                        }
                    } catch (Exception ignore) {}
                    refreshInitialConfigurationsPanel();
                    if (currentDocument != null) currentDocument.setDirty(true);
                    updateWindowTitle();
                    runLTLChecks(false);
                } catch (java.util.concurrent.CancellationException ce) {
                    // Worker was cancelled, ignore
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(PWSEditor.this, "Automatic semantics recalculation failed: " + ex.getMessage(), "Warning", JOptionPane.WARNING_MESSAGE);
                } finally {
                    // Always restore to default cursor
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        };
        currentSemanticsWorker = worker;
        worker.execute();
    }

    /**
     * Renames a machine identifier in the assembly and updates all related model references.
     */
    public void renameAssemblyMachineId(String oldId, String newId) {
        if (pwsStateMachine == null) return;
        if (oldId == null || newId == null || oldId.equals(newId)) return;
        pwsStateMachine.renameAssemblyMachineId(oldId, newId);
        markDocumentDirty();
        scheduleSemanticsRecalculation();
    }

    /**
     * Renames a state inside an assembly machine and updates all related references.
     */
    public void renameAssemblyStateName(machinery.StateMachine machine, String oldName, String newName) {
        if (pwsStateMachine == null) return;
        if (machine == null || oldName == null || newName == null || oldName.equals(newName)) return;
        pwsStateMachine.renameAssemblyStateName(machine, oldName, newName);
        markDocumentDirty();
        scheduleSemanticsRecalculation();
    }

    public PWSDocument getDocument() { return this.currentDocument; }

    // Helper for PWSFileManager to access the base editor
    public StateMachineEditor getBaseEditor() { return this.baseEditor; }

    // Expose the currently loaded machine library (if any) for preservation on New/Open
    public assembly.MachineLibrary getCurrentLibrary() {
        if (pwsStateMachine == null) return null;
        try {
            return pwsStateMachine.getAssembly().getMachineLibrary();
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Persist alias data for the currently embedded machine (library or assembly).
     */
    public void syncEmbeddedLibraryAliasData() {
        if (embeddedEditor == null || embeddedMachineId == null) return;
        if (pwsStateMachine == null) return;
        StateMachinePanel panel = embeddedEditor.getStateMachinePanel();
        if (panel == null) return;
        StateMachinePanel.AliasData data = panel.exportAliasData();
        if (embeddedMachineId.startsWith("lib:")) {
            String key = embeddedMachineId.substring(4);
            pwsStateMachine.getAssembly().getMachineLibrary().setAliasData(key, data);
            return;
        }

        StateMachine machine = pwsStateMachine.getAssembly()
                .getStateMachines().get(embeddedMachineId);
        String libKey = findLibraryKeyForMachine(machine);
        if (libKey != null) {
            pwsStateMachine.getAssembly().getMachineLibrary().setAliasData(libKey, data);
        } else {
            pwsStateMachine.getAssembly().setAliasData(embeddedMachineId, data);
        }
    }

    private String findLibraryKeyForMachine(StateMachine machine) {
        if (machine == null || pwsStateMachine == null) return null;
        for (Map.Entry<String, StateMachine> entry
                : pwsStateMachine.getAssembly().getMachineLibrary().getMachines().entrySet()) {
            if (entry.getValue() == machine) {
                return entry.getKey();
            }
        }
        return null;
    }

    // Rebuild UI when a new model is provided (used by open/new)
    public void rebuildUIForNewModel(PWSStateMachine model) {
        this.pwsStateMachine = model;
        getContentPane().removeAll();
        initComponents();
        revalidate();
        repaint();
        updateWindowTitle();
        // If the document is dirty, automatically recalculate semantics in background
        if (this.currentDocument != null && this.currentDocument.isDirty()) {
            // Indicate busy state
            Cursor oldCursor = getCursor();
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            SwingWorker<Void,Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    try {
                        if (PWSEditor.this.pwsStateMachine != null) {
                            ((pws.PWSStateMachine) PWSEditor.this.pwsStateMachine).recalculateSemantics();
                        }
                    } catch (Exception ex) {
                        // propagate to done()
                        throw ex;
                    }
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get(); // rethrow any exceptions
                        // Update UI on EDT
                        try {
                            applyDashboardVisibility();
                        } catch (Exception ignore) {}
                        refreshInitialConfigurationsPanel();
                        // Clear dirty flag and refresh title
                        currentDocument.setDirty(false);
                        updateWindowTitle();
                        runLTLChecks(false);
                    } catch (Exception ex) {
                        // Show a non-blocking warning
                        JOptionPane.showMessageDialog(PWSEditor.this, "Automatic semantics recalculation failed: " + ex.getMessage(), "Warning", JOptionPane.WARNING_MESSAGE);
                    } finally {
                        setCursor(oldCursor);
                    }
                }
            };
            worker.execute();
        }
    }

    public void markDocumentDirty() {
        if (suppressDirtyNotifications) {
            return;
        }
        if (this.currentDocument != null) {
            this.currentDocument.setDirty(true);
            updateWindowTitle();
        }
        recordUndoSnapshot();
    }

    public void initializeUndoHistory() {
        undoStack.clear();
        redoStack.clear();
        currentSnapshot = captureSnapshot();
        updateUndoRedoMenuItems();
    }

    private void recordUndoSnapshot() {
        if (undoRecordingSuspended) return;
        EditorSnapshot snapshot = captureSnapshot();
        if (snapshot == null) return;
        if (currentSnapshot != null && currentSnapshot.json.equals(snapshot.json)) {
            return;
        }
        if (currentSnapshot != null) {
            undoStack.push(currentSnapshot);
            while (undoStack.size() > MAX_UNDO) {
                undoStack.removeLast();
            }
        }
        currentSnapshot = snapshot;
        redoStack.clear();
        updateUndoRedoMenuItems();
    }

    private EditorSnapshot captureSnapshot() {
        if (pwsStateMachine == null) return null;
        try {
            syncEmbeddedLibraryAliasData();
            PWSStateMachinePanel.AnnotationData annotations = null;
            if (baseEditor != null && baseEditor.getStateMachinePanel() instanceof PWSStateMachinePanel p) {
                annotations = p.exportAnnotations();
            }
            JsonModelSerializer.WorkspaceUI uiState = getWorkspaceUIState();
            String json = JsonModelSerializer.savePwsWorkspaceToJson(pwsStateMachine, annotations, uiState);
            return new EditorSnapshot(json);
        } catch (Exception ex) {
            return null;
        }
    }

    private void updateUndoRedoMenuItems() {
        if (undoItem != null) undoItem.setEnabled(!undoStack.isEmpty());
        if (redoItem != null) redoItem.setEnabled(!redoStack.isEmpty());
    }

    public void performUndo() {
        undo();
    }

    public void performRedo() {
        redo();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        if (currentSnapshot != null) {
            redoStack.push(currentSnapshot);
        }
        EditorSnapshot target = undoStack.pop();
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
        EditorSnapshot target = redoStack.pop();
        applySnapshot(target);
        currentSnapshot = target;
        updateUndoRedoMenuItems();
    }

    private void applySnapshot(EditorSnapshot snapshot) {
        if (snapshot == null) return;
        undoRecordingSuspended = true;
        boolean prevSuppress = suppressDirtyNotifications;
        suppressDirtyNotifications = true;
        String restoreEmbeddedId = embeddedMachineId;
        embeddedEditor = null;
        embeddedMachineId = null;
        try {
            if (currentSemanticsWorker != null && !currentSemanticsWorker.isDone()) {
                currentSemanticsWorker.cancel(false);
            }
            JsonModelSerializer.LoadedWorkspace loaded = JsonModelSerializer.loadPwsWorkspaceFromJson(snapshot.json);
            if (loaded == null || loaded.getModel() == null) return;
            PWSStateMachine model = loaded.getModel();
            File file = (currentDocument != null) ? currentDocument.getFile() : null;
            PWSDocument doc = new PWSDocument(model, model.getAssembly().getMachineLibrary());
            doc.setFile(file);
            doc.setDirty(false);
            setDocument(doc);
            setControllerEditorVisible(true);
            rebuildUIForNewModel(model);
            try {
                applyWorkspaceUIState(loaded.getUiState());
                if (baseEditor != null && baseEditor.getStateMachinePanel() instanceof PWSStateMachinePanel panel) {
                    if (loaded.getAnnotations() != null) {
                        panel.importAnnotations(loaded.getAnnotations());
                    }
                }
                syncViewMenuSelections();
                applyDashboardVisibility();
                refreshInitialConfigurationsPanel();
                restoreEmbeddedEditorSelection(restoreEmbeddedId);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Warning: UI state could not be fully restored: " + ex.getMessage(),
                        "Warning", JOptionPane.WARNING_MESSAGE);
            }
            scheduleSemanticsRecalculation();
            if (currentDocument != null) {
                currentDocument.setDirty(true);
                updateWindowTitle();
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

    private void restoreEmbeddedEditorSelection(String embeddedId) {
        if (embeddedId == null) return;
        if (embeddedId.startsWith("lib:")) {
            String key = embeddedId.substring(4);
            if (btnLibraryToggle != null && topCardsLayout != null && topSwitchPanel != null) {
                btnLibraryToggle.setSelected(true);
                topCardsLayout.show(topSwitchPanel, "library");
            }
            if (libraryPanel != null) {
                libraryPanel.selectLibraryKey(key);
            }
        } else {
            if (btnAssembly != null && topCardsLayout != null && topSwitchPanel != null) {
                btnAssembly.setSelected(true);
                topCardsLayout.show(topSwitchPanel, "assembly");
            }
            if (assemblyPanel != null) {
                assemblyPanel.selectMachineById(embeddedId);
            }
        }
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
                redo();
            }
        });
    }

    public void onAssemblyOrderChanged() {
        markDocumentDirty();
        SwingUtilities.invokeLater(() -> {
            refreshInitialConfigurationsPanel();
            if (baseEditor != null) {
                try {
                    PWSStateMachinePanel panel =
                        (PWSStateMachinePanel)((PWSStateMachineEditor) baseEditor).getStateMachinePanel();
                    panel.refreshStateAnnotationSizes();
                    panel.repaint();
                } catch (Exception ignore) {}
            }
        });
    }

    private void refreshInitialConfigurationsPanel() {
        if (initialConfigsPanel == null) return;
        if (pwsStateMachine == null || pwsStateMachine.getAssembly() == null) {
            initialConfigsPanel.setPlaceholder("No controller loaded.");
            return;
        }
        try {
            Semantics init = pwsStateMachine.getAssembly().calculateInitialStateSemantics();
            initialConfigsPanel.setSemantics(init);
            if (pwsStateMachine instanceof PWSStateMachine pwsMachine) {
                initialConfigsPanel.setExitZones(
                    pwsMachine.findExitZones(init),
                    StateSemanticsAnnotation.isShowExitZoneMachineIds()
                );
                Semantics closure = computeExitZoneClosure(pwsMachine, init);
                initialConfigsPanel.setClosure(closure, pwsMachine);
            } else {
                initialConfigsPanel.setExitZones(null, StateSemanticsAnnotation.isShowExitZoneMachineIds());
                initialConfigsPanel.setClosure(null, null);
            }
        } catch (Exception ex) {
            initialConfigsPanel.setPlaceholder("Initial configurations unavailable.");
        }
    }

    private Semantics computeExitZoneClosure(PWSStateMachine machine, Semantics initial) {
        if (machine == null || initial == null) {
            return null;
        }
        return machine.calculateAssemblyClosure(initial);
    }

    public void updateWindowTitle() {
        String base = "PWSEditor";
        if (currentDocument != null) {
            String name = (currentDocument.getFile() != null) ? currentDocument.getFile().getName() : currentDocument.getModel().getName();
            if (name == null || name.trim().isEmpty()) name = "Untitled";
            if (currentDocument.isDirty()) name += " *";
            setTitle(base + " : " + name);
        } else {
            setTitle(base + " : Untitled");
        }
    }

    private void runLTLChecks(boolean showDialog) {
        if (!(pwsStateMachine instanceof PWSStateMachine)) return;
        List<LTLCheckResult> results = LTLChecker.check((PWSStateMachine) pwsStateMachine);
        boolean hasFailures = false;
        for (LTLCheckResult r : results) {
            if (r.getStatus() == LTLCheckResult.Status.FAIL || r.getStatus() == LTLCheckResult.Status.ERROR) {
                hasFailures = true;
                break;
            }
        }
        ensureLTLChecksDialog();
        ltlChecksDialog.setResults(results);
        if (showDialog || hasFailures) {
            ltlChecksDialog.setLocationRelativeTo(this);
            ltlChecksDialog.setVisible(true);
        }
    }

    private void ensureLTLChecksDialog() {
        if (ltlChecksDialog != null) return;
        ltlChecksDialog = new LTLChecksDialog(this);
        ltlChecksDialog.setOnOpenEditor(() -> {
            pws.editor.LTLFormulaEditorDialog dlg = new pws.editor.LTLFormulaEditorDialog(
                PWSEditor.this,
                pwsStateMachine.getAssembly(),
                () -> runLTLChecks(false));
            dlg.setVisible(true);
        });
        ltlChecksDialog.setOnRecheck(() -> runLTLChecks(true));
    }

    public JsonModelSerializer.WorkspaceUI getWorkspaceUIState() {
        JsonModelSerializer.WorkspaceUI ui = new JsonModelSerializer.WorkspaceUI();
        ui.windowWidth = getWidth() > 0 ? getWidth() : null;
        ui.windowHeight = getHeight() > 0 ? getHeight() : null;
        ui.mainDivider = (mainSplitPane != null) ? mainSplitPane.getDividerLocation() : null;
        ui.rightDivider = (rightSplitPane != null) ? rightSplitPane.getDividerLocation() : null;
        ui.assemblyDivider = (assemblySplitPane != null) ? assemblySplitPane.getDividerLocation() : null;
        if (showStateAnn != null) ui.showDashboards = showStateAnn.isSelected();
        if (showGridItem != null) ui.showGrid = showGridItem.isSelected();
        if (snapToGridItem != null) ui.snapToGrid = snapToGridItem.isSelected();
        if (editModeItem != null) ui.editMode = editModeItem.isSelected();
        if (btnLibraryToggle != null) {
            ui.topCard = btnLibraryToggle.isSelected() ? "library" : "assembly";
        }
        if (baseEditor != null && baseEditor.getStateMachinePanel() instanceof PWSStateMachinePanel panel) {
            ui.gridSize = panel.getGridSize();
        }
        return ui;
    }

    public void applyWorkspaceUIState(JsonModelSerializer.WorkspaceUI ui) {
        if (ui == null) return;
        boolean prevSuppress = suppressDirtyNotifications;
        suppressDirtyNotifications = true;
        try {
            if (ui.windowWidth != null && ui.windowHeight != null
                    && ui.windowWidth > 0 && ui.windowHeight > 0) {
                setSize(ui.windowWidth, ui.windowHeight);
            }
            if (mainSplitPane != null && ui.mainDivider != null && ui.mainDivider > 0) {
                mainSplitPane.setDividerLocation(ui.mainDivider);
            }
            if (rightSplitPane != null && ui.rightDivider != null && ui.rightDivider > 0) {
                rightSplitPane.setDividerLocation(ui.rightDivider);
            }
            if (assemblySplitPane != null && ui.assemblyDivider != null && ui.assemblyDivider > 0) {
                assemblySplitPane.setDividerLocation(ui.assemblyDivider);
            }
            if (ui.topCard != null && topCardsLayout != null && topSwitchPanel != null) {
                if ("library".equalsIgnoreCase(ui.topCard)) {
                    if (btnLibraryToggle != null) btnLibraryToggle.setSelected(true);
                    topCardsLayout.show(topSwitchPanel, "library");
                } else if ("assembly".equalsIgnoreCase(ui.topCard)) {
                    if (btnLibraryToggle != null) btnLibraryToggle.setSelected(false);
                    topCardsLayout.show(topSwitchPanel, "assembly");
                }
            }
            if (ui.showDashboards != null && showStateAnn != null) {
                showStateAnn.setSelected(ui.showDashboards);
                applyDashboardVisibility();
            }
            if (ui.editMode != null) {
                setEditModeEnabled(ui.editMode);
            }
            if (baseEditor != null && baseEditor.getStateMachinePanel() instanceof PWSStateMachinePanel panel) {
                if (ui.showGrid != null) {
                    if (showGridItem != null) showGridItem.setSelected(ui.showGrid);
                    panel.setShowGrid(ui.showGrid);
                }
                if (ui.snapToGrid != null) {
                    if (snapToGridItem != null) snapToGridItem.setSelected(ui.snapToGrid);
                    panel.setSnapToGrid(ui.snapToGrid);
                }
                if (ui.gridSize != null && ui.gridSize > 0) {
                    panel.setGridSize(ui.gridSize);
                }
            }
        } finally {
            suppressDirtyNotifications = prevSuppress;
        }
    }

    private static JWindow createSplashWindow() {
        JWindow splash = new JWindow();

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(120, 120, 120)),
                BorderFactory.createEmptyBorder(18, 22, 18, 22)));
        content.setBackground(new Color(250, 250, 250));
        content.setOpaque(true);
        content.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel title = new JLabel("PWSEditor", SwingConstants.CENTER);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));

        JLabel license = new JLabel(
                "<html><div style='text-align:center; width:320px;'>"
                        + "MIT License<br/>"
                        + "Copyright (c) 2025 Luca Pazzi (UNIMORE)<br/>"
                        + "See LICENSE for full terms."
                        + "</div></html>",
                SwingConstants.CENTER);
        license.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel hint = new JLabel("Click anywhere to continue", SwingConstants.CENTER);
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
        hint.setForeground(new Color(90, 90, 90));

        content.add(title);
        content.add(Box.createVerticalStrut(8));
        content.add(license);
        content.add(Box.createVerticalStrut(12));
        content.add(hint);

        splash.setContentPane(content);
        splash.pack();
        return splash;
    }

    private static void addMouseListenerRecursive(Component component, java.awt.event.MouseListener listener) {
        component.addMouseListener(listener);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                addMouseListenerRecursive(child, listener);
            }
        }
    }


    /**
     * Launches the editor application.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        // Simplify logs: only show the message text
        System.setProperty("java.util.logging.SimpleFormatter.format", "%5$s%n");
        Logger rootLogger = Logger.getLogger("");
        for (Handler handler : rootLogger.getHandlers()) {
            handler.setFormatter(new SimpleFormatter() {
                @Override
                public String format(LogRecord record) {
                    return record.getMessage() + System.lineSeparator();
                }
            });
        }
        PWSStateMachine pwsStateMachine = new PWSStateMachine("Whole");

        // Start with an empty assembly - user can add machines via File -> New
        SwingUtilities.invokeLater(() -> {
            PWSEditor editor = new PWSEditor(pwsStateMachine);
            editor.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            editor.setSize(1000, 600);
            editor.setLocationRelativeTo(null);

            JWindow splash = createSplashWindow();
            splash.setLocationRelativeTo(null);
            splash.setAlwaysOnTop(true);

            final boolean[] dismissed = {false};
            java.awt.event.MouseAdapter dismissListener = new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    if (dismissed[0]) return;
                    dismissed[0] = true;
                    splash.setVisible(false);
                    splash.dispose();
                    editor.setVisible(true);
                    editor.toFront();
                    editor.requestFocus();
                }
            };
            addMouseListenerRecursive(splash.getContentPane(), dismissListener);
            splash.addMouseListener(dismissListener);
            splash.setVisible(true);
        });
    }
}
