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

        // --- New Composite Save/Load for Model + Layout in a Single File ---

        // Save All (model and layout)
        JMenuItem saveAllItem = new JMenuItem("Save All");
        saveAllItem.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PWS Workspace (.pws)", "pws"));
            int option = fileChooser.showSaveDialog(PWSEditor.this);
            if (option == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                // Ensure the file has the .pws extension
                if (!file.getName().toLowerCase().endsWith(".pws")) {
                    file = new File(file.getAbsolutePath() + ".pws");
                }
                try {
                    // First, serialize the annotations into a byte[] so we can write model+library
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (ObjectOutputStream tempOos = new ObjectOutputStream(baos)) {
                        ((PWSStateMachinePanel) baseEditor.getStateMachinePanel()).saveAnnotationsToStream(tempOos);
                    }
                    byte[] annotationsBytes = baos.toByteArray();

                    // Save model and machine library using the serializer helper
                    BinaryModelSerializer.saveModelAndLibrary(pwsStateMachine, pwsStateMachine.getAssembly().getMachineLibrary(), file.getAbsolutePath());

                    // Append the serialized annotations as a single byte[] object so loading can restore them
                    try (FileOutputStream fos = new FileOutputStream(file, true);
                         AppendingObjectOutputStream aout = new AppendingObjectOutputStream(fos)) {
                        aout.writeObject(annotationsBytes);
                        aout.flush();
                    }

                    JOptionPane.showMessageDialog(PWSEditor.this, "Model, library and layout saved successfully.");
                } catch (IOException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(PWSEditor.this, "Error saving: " + ex.getMessage());
                }
            }
        });
        fileMenu.add(saveAllItem);

        // Load All (model and layout)
        JMenuItem loadAllItem = new JMenuItem("Load All");
        loadAllItem.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PWS Workspace (.pws)", "pws"));
            int option = fileChooser.showOpenDialog(PWSEditor.this);
            if (option == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                try {
                    // Use the helper to read model and (optional) machine library
                    Object[] pair = BinaryModelSerializer.loadModelAndLibrary(file.getAbsolutePath());
                    Object loadedModel = pair[0];
                    Object libOrAnn = pair[1];

                    // If libOrAnn is an Exception, the library failed to deserialize
                    if (libOrAnn instanceof Exception) {
                        Exception libEx = (Exception) libOrAnn;
                        JOptionPane.showMessageDialog(PWSEditor.this, "Warning: library could not be loaded: " + libEx.getMessage(), "Warning", JOptionPane.WARNING_MESSAGE);
                        libOrAnn = null; // proceed without library
                    }

                    if (loadedModel instanceof PWSStateMachine) {
                        pwsStateMachine = (PWSStateMachine) loadedModel;

                        // If the second object is a MachineLibrary, merge its content into the assembly's library
                        if (libOrAnn instanceof assembly.MachineLibrary) {
                            assembly.MachineLibrary loadedLib = (assembly.MachineLibrary) libOrAnn;
                            assembly.Assembly asm = pwsStateMachine.getAssembly();
                            assembly.MachineLibrary currentLib = asm.getMachineLibrary();
                            // If the deserialized library is a distinct instance, merge it; if it's the same
                            // instance as the one already inside the loaded model, skip (clearing would
                            // remove the entries we just deserialized with the model).
                            if (loadedLib != currentLib) {
                                currentLib.clear();
                                // Re-add via addMachine to rebuild name-to-key mapping
                                for (Map.Entry<String, machinery.StateMachine> entry : loadedLib.getMachines().entrySet()) {
                                    currentLib.addMachine(entry.getKey(), entry.getValue());
                                }
                            }
                        }

                        // Rebuild the UI so all panels (assembly/library/editor) point to the new model
                        getContentPane().removeAll();
                        initComponents();

                        // Handle annotations: two formats are possible in files on disk:
                        // - Older files: second object is the annotations (byte[] or direct stream content)
                        // - Newer files: third object contains annotations (we need to reopen and skip first two)
                        boolean annotationsHandled = false;
                        if (libOrAnn instanceof byte[]) {
                            // libOrAnn is actually the annotations bytes from older-format save
                            byte[] annotationsBytes = (byte[]) libOrAnn;
                            try (ObjectInputStream annIn = new ObjectInputStream(new ByteArrayInputStream(annotationsBytes))) {
                                ((PWSStateMachinePanel) baseEditor.getStateMachinePanel()).loadAnnotationsFromStream(annIn);
                            }
                            annotationsHandled = true;
                        }

                        if (!annotationsHandled) {
                            // Try to read a third object (annotations) if present
                            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                                // skip model
                                ois.readObject();
                                // skip library (may be annotations in old files)
                                try {
                                    ois.readObject();
                                } catch (EOFException eof) {
                                    // nothing more
                                }
                                try {
                                    Object maybeAnn = ois.readObject();
                                    if (maybeAnn instanceof byte[]) {
                                        byte[] annotationsBytes = (byte[]) maybeAnn;
                                        try (ObjectInputStream annIn = new ObjectInputStream(new ByteArrayInputStream(annotationsBytes))) {
                                            ((PWSStateMachinePanel) baseEditor.getStateMachinePanel()).loadAnnotationsFromStream(annIn);
                                        }
                                    }
                                } catch (EOFException eof) {
                                    // no annotations present
                                }
                            } catch (IOException | ClassNotFoundException ex) {
                                // Non-fatal: annotations may not be present or may be older format
                            }
                        }

                        // Ensure state dashboards (annotations) are visible after loading
                        try {
                            PWSStateMachinePanel panel = (PWSStateMachinePanel)((PWSStateMachineEditor) baseEditor).getStateMachinePanel();
                            panel.setShowStateAnnotations(true);
                            // Reattach and show any annotations that were restored from the file
                            panel.restoreVisibleStateAnnotations();
                            panel.repaint();
                        } catch (Exception ex) {
                            // ignore
                        }
                        getContentPane().revalidate();
                        getContentPane().repaint();
                        JOptionPane.showMessageDialog(PWSEditor.this, "Model, library and layout loaded successfully.");
                    } else {
                        JOptionPane.showMessageDialog(PWSEditor.this, "The selected file does not contain valid data.");
                    }
                } catch (IOException | ClassNotFoundException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(PWSEditor.this, "Error loading: " + ex.getMessage());
                }
            }
        });
        fileMenu.add(loadAllItem);

        // Save Library (export only the MachineLibrary)
        JMenuItem saveLibItem = new JMenuItem("Save Library...");
        saveLibItem.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Machine Library (.mlib)", "mlib"));
            if (fc.showSaveDialog(PWSEditor.this) == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                if (!file.getName().toLowerCase().endsWith(".mlib")) {
                    file = new File(file.getAbsolutePath() + ".mlib");
                }
                try {
                    BinaryModelSerializer.saveModel(pwsStateMachine.getAssembly().getMachineLibrary(), file.getAbsolutePath());
                    JOptionPane.showMessageDialog(PWSEditor.this, "Library saved successfully.");
                } catch (IOException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(PWSEditor.this, "Error saving library: " + ex.getMessage());
                }
            }
        });
        fileMenu.add(saveLibItem);

        // Load Library (replace current library contents)
        JMenuItem loadLibItem = new JMenuItem("Load Library...");
        loadLibItem.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Machine Library (.mlib)", "mlib"));
            if (fc.showOpenDialog(PWSEditor.this) == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                try {
                    Object obj = BinaryModelSerializer.loadModel(file.getAbsolutePath());
                    if (obj instanceof assembly.MachineLibrary) {
                        assembly.MachineLibrary loaded = (assembly.MachineLibrary) obj;
                        assembly.MachineLibrary current = pwsStateMachine.getAssembly().getMachineLibrary();
                        current.clear();
                        for (Map.Entry<String, machinery.StateMachine> entry : loaded.getMachines().entrySet()) {
                            current.addMachine(entry.getKey(), entry.getValue());
                        }
                        if (libraryPanel != null) libraryPanel.refreshList();
                        if (assemblyPanel != null) assemblyPanel.refreshList();

                        // If the embedded editor was showing a library machine that no longer exists, clear it
                        SwingUtilities.invokeLater(() -> {
                            if (embeddedMachineId != null && embeddedMachineId.startsWith("lib:")) {
                                String key = embeddedMachineId.substring(4);
                                if (pwsStateMachine.getAssembly().getMachineLibrary().get(key) == null) {
                                    machineEditorContainer.removeAll();
                                    JLabel placeholder = new JLabel("Select a machine (assembly or library) to edit", SwingConstants.CENTER);
                                    machineEditorContainer.add(placeholder, BorderLayout.CENTER);
                                    machineEditorContainer.revalidate();
                                    machineEditorContainer.repaint();
                                    embeddedMachineId = null;
                                }
                            }
                        });

                        JOptionPane.showMessageDialog(PWSEditor.this, "Library loaded successfully.");
                    } else {
                        JOptionPane.showMessageDialog(PWSEditor.this, "The selected file does not contain a MachineLibrary.", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (IOException | ClassNotFoundException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(PWSEditor.this, "Error loading library: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        fileMenu.add(loadLibItem);

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
        exitItem.addActionListener(e -> System.exit(0));
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
