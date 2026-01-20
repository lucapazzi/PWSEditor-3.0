package pws.editor;

import assembly.Assembly;
import assembly.AssemblyInterface;
import assembly.GuardActionsPair;
import editor.StateMachineEditor;
import editor.StateMachinePanel;
import machinery.StateMachine;
import pws.PWSState;
import pws.PWSStateMachine;
import serializer.BinaryModelSerializer;
import java.util.Map;
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
    private JTabbedPane tabbedPane;         // Panel to switch between baseEditor and assemblyPanel
    private StateMachineEditor embeddedEditor = null; // single reusable embedded editor for assembly machines
    private JPanel machineEditorContainer; // promoted so removal callback can clear it
    private String embeddedMachineId = null;
    private CardLayout topCardsLayout;      // CardLayout for assembly/library switch
    private JPanel topSwitchPanel;          // Panel containing assembly/library cards
    private JToggleButton btnLibraryToggle; // Library toggle button reference

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
        } else {
            this.pwsStateMachine = new PWSStateMachine(machine.getName());
        }
        initComponents();
        // Initialize file manager and document wrapper
        this.fileManager = new PWSFileManager(this);
        this.currentDocument = new PWSDocument(this.pwsStateMachine, this.pwsStateMachine.getAssembly().getMachineLibrary());
        updateWindowTitle();
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
    private void initComponents() {
        // Don't set the menu bar at the frame level anymore
        // setJMenuBar(createMenuBar());

        // Left editor area (wrapped with a header)
        baseEditor = new PWSStateMachineEditor(pwsStateMachine, "PWSMachine");
        JPanel editorInner = new JPanel(new BorderLayout());
        editorInner.add(baseEditor.getContentPane(), BorderLayout.CENTER);

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
        Component controllerPanel = baseEditor.getStateMachinePanel();
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
        assemblyPanel = new PWSPanel(pwsStateMachine.getAssembly());

        JPanel rightTop = new JPanel(new BorderLayout());
        // Create a split view: Assembly | Library
        this.libraryPanel = new MachineLibraryPanel(pwsStateMachine.getAssembly());

        JPanel assemblyWrapper = new JPanel(new BorderLayout());
        JLabel rightHeader = new JLabel("Assembly", SwingConstants.CENTER);
        rightHeader.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        rightHeader.setFont(rightHeader.getFont().deriveFont(Font.BOLD));
        assemblyWrapper.add(rightHeader, BorderLayout.NORTH);
        assemblyWrapper.add(assemblyPanel, BorderLayout.CENTER);

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
        JToggleButton btnAssembly = new JToggleButton("Assembly");
        btnLibraryToggle = new JToggleButton("Library");
        ButtonGroup bg = new ButtonGroup();
        bg.add(btnAssembly); bg.add(btnLibraryToggle);
        btnAssembly.setSelected(true);
        tb.add(btnAssembly); tb.add(btnLibraryToggle);

        btnAssembly.addActionListener(a -> topCardsLayout.show(topSwitchPanel, "assembly"));
        btnLibraryToggle.addActionListener(a -> topCardsLayout.show(topSwitchPanel, "library"));

        topCardPanel.add(tb, BorderLayout.NORTH);
        topCardPanel.add(topSwitchPanel, BorderLayout.CENTER);

        // Create the machine editor container (bottom half of the right area)
        machineEditorContainer = new JPanel(new BorderLayout());
        JLabel placeholder = new JLabel("Select a machine (assembly or library) to edit", SwingConstants.CENTER);
        machineEditorContainer.add(placeholder, BorderLayout.CENTER);

        // Vertical split on the right: top cards (assembly/library) above the embedded editor
        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topCardPanel, machineEditorContainer);
        rightSplit.setResizeWeight(0.25);
        rightSplit.setOneTouchExpandable(true);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftWrapper, rightSplit);
        split.setResizeWeight(0.7);
        getContentPane().add(split, BorderLayout.CENTER);

        // Wire selection from the assembly panel to show the selected machine in the embedded editor
        assemblyPanel.setMachineSelectionListener(new pws.editor.PWSPanel.MachineSelectionListener() {
            @Override
            public void machineSelected(String id) {
                StateMachine machine = pwsStateMachine.getAssembly().getStateMachines().get(id);
                if (machine != null) {
                    SwingUtilities.invokeLater(() -> {
                        machineEditorContainer.removeAll();
                        try {
                            String title = id + " : " + (machine.getName() != null ? machine.getName() : "");
                            if (embeddedEditor == null) {
                                embeddedEditor = new StateMachineEditor(machine, pwsStateMachine.getAssembly(), title);
                                embeddedEditor.setCloseCallback(() -> {
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
            }

            @Override
            public void machineEdited(String id) {
                // Refresh library panel in case the edited machine is in the library
                SwingUtilities.invokeLater(() -> {
                    if (libraryPanel != null) {
                        libraryPanel.refreshList();
                    }
                });
            }
        });

        // Wire library selection to show selected library machine in the same embedded editor
        libraryPanel.setLibrarySelectionListener(new MachineLibraryPanel.LibrarySelectionListener() {
            @Override
            public void librarySelected(String key) {
                StateMachine machine = pwsStateMachine.getAssembly().getMachineLibrary().get(key);
                if (machine != null) {
                    SwingUtilities.invokeLater(() -> {
                        machineEditorContainer.removeAll();
                        try {
                            String title = machine.getName() != null ? machine.getName() : "Unnamed";
                            if (embeddedEditor == null) {
                                embeddedEditor = new StateMachineEditor(machine, pwsStateMachine.getAssembly(), title);
                                embeddedEditor.setCloseCallback(() -> {
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

        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.addActionListener(e -> {
            fileManager.save();
        });
        fileMenu.add(saveItem);

        JMenuItem saveAsItem = new JMenuItem("Save As...");
        saveAsItem.addActionListener(e -> {
            fileManager.saveAs();
        });
        fileMenu.add(saveAsItem);

        JMenuItem closeItem = new JMenuItem("Close");
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
        // PDF export preference (vector vs raster)
        JCheckBoxMenuItem preferVectorItem = new JCheckBoxMenuItem("Prefer vector PDF export", false);
        preferVectorItem.addActionListener(e -> {
            utility.PDFExporter.setPreferVector(preferVectorItem.isSelected());
        });
        fileMenu.add(preferVectorItem);

        JMenuItem exportPDFItem = new JMenuItem("Export as PDF");
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
//                    JOptionPane.showMessageDialog(PWSEditor.this, "Stato sorgente o target non trovato.");
//                }
//            }
//        });
//        editMenu.add(addTransitionItem);

        JCheckBoxMenuItem editModeItem = new JCheckBoxMenuItem("Edit mode", true);
        editModeItem.addActionListener(e -> baseEditor.getStateMachinePanel().setEditMode(editModeItem.isSelected()));
        editMenu.add(editModeItem);

        menuBar.add(editMenu);

        // --- View menu: toggle state dashboards ---
        JMenu viewMenu = new JMenu("View");
        JCheckBoxMenuItem showStateAnn = new JCheckBoxMenuItem("Show state dashboards", true);
        showStateAnn.addActionListener(e -> {
            boolean show = showStateAnn.isSelected();
            // Retrieve the PWSStateMachinePanel and toggle annotations/dashboards
            PWSStateMachinePanel panel =
                (PWSStateMachinePanel)((PWSStateMachineEditor) baseEditor).getStateMachinePanel();
            panel.setShowStateAnnotations(show);
            panel.repaint();
            // Mark document dirty when the user toggles global dashboards
            markDocumentDirty();
        });
        viewMenu.add(showStateAnn);

        // Ensure dashboards are visible at startup (preserve per-state visibility)
        try {
            PWSStateMachinePanel panel = (PWSStateMachinePanel)((PWSStateMachineEditor) baseEditor).getStateMachinePanel();
            panel.setShowStateAnnotations(true);
            // Ensure any saved annotation components are restored and shown where appropriate
            panel.restoreVisibleStateAnnotations();
            panel.repaint();
        } catch (Exception ex) {
            // Ignore if panel is not yet ready
        }

        JCheckBoxMenuItem showGridItem = new JCheckBoxMenuItem("Show grid", true);
        showGridItem.addActionListener(e -> {
            PWSStateMachinePanel panel =
                (PWSStateMachinePanel)((PWSStateMachineEditor) baseEditor).getStateMachinePanel();
            panel.setShowGrid(showGridItem.isSelected());
            panel.repaint();
        });
        viewMenu.add(showGridItem);

        JCheckBoxMenuItem snapToGridItem = new JCheckBoxMenuItem("Snap to grid", true);
        snapToGridItem.addActionListener(e -> {
            PWSStateMachinePanel panel =
                (PWSStateMachinePanel)((PWSStateMachineEditor) baseEditor).getStateMachinePanel();
            panel.setSnapToGrid(snapToGridItem.isSelected());
        });
        viewMenu.add(snapToGridItem);

        JMenuItem gridSizeItem = new JMenuItem("Set grid size...");
        gridSizeItem.addActionListener(e -> {
            PWSStateMachinePanel panel =
                (PWSStateMachinePanel)((PWSStateMachineEditor) baseEditor).getStateMachinePanel();
            String input = JOptionPane.showInputDialog(this, "Grid size (pixels):", panel.getGridSize());
            if (input != null) {
                try {
                    int size = Integer.parseInt(input.trim());
                    if (size > 0) {
                        panel.setGridSize(size);
                        panel.repaint();
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid number", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        viewMenu.add(gridSizeItem);

        // LTL Formula editor for the current assembly
        JMenuItem ltlEditorItem = new JMenuItem("LTL Editor...");
        ltlEditorItem.addActionListener(e -> {
            pws.editor.LTLFormulaEditorDialog dlg = new pws.editor.LTLFormulaEditorDialog(PWSEditor.this, pwsStateMachine.getAssembly());
            dlg.setVisible(true);
        });
        // Disabled by default (grayed out)
        ltlEditorItem.setEnabled(false);
        viewMenu.add(ltlEditorItem);

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
    }

    public PWSDocument getDocument() { return this.currentDocument; }

    // Helper for PWSFileManager to access the base editor
    public StateMachineEditor getBaseEditor() { return this.baseEditor; }

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
                            PWSStateMachinePanel panel = (PWSStateMachinePanel) ((PWSStateMachineEditor) baseEditor).getStateMachinePanel();
                            panel.setShowStateAnnotations(true);
                            panel.restoreVisibleStateAnnotations();
                            panel.repaint();
                        } catch (Exception ignore) {}
                        // Clear dirty flag and refresh title
                        currentDocument.setDirty(false);
                        updateWindowTitle();
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
        if (this.currentDocument != null) {
            this.currentDocument.setDirty(true);
            updateWindowTitle();
        }
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

        // Here I create a state machine for adding to the assembly with id "m1"
        StateMachine stateMachine1 = new StateMachine("M1");
        pwsStateMachine.getAssembly().addStateMachine("m1", stateMachine1);
        SwingUtilities.invokeLater(() -> {
            PWSEditor editor = new PWSEditor(pwsStateMachine);
            editor.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            editor.setSize(1000, 600);
            editor.setLocationRelativeTo(null);
            editor.setVisible(true);
        });
    }
}
