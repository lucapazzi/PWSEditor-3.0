package editor;

import assembly.Assembly;
import machinery.*;
import pws.PWSStateMachine;
// SVG export removed: not used when exporting PDFs

import javax.swing.*;
import java.awt.*;
import java.io.*;

public class StateMachineEditor extends JFrame {

    protected StateMachine stateMachine;
    protected StateMachinePanel statePanel;
    protected Assembly assembly;
    private Runnable closeCallback = null;

    // Callback interface for close requests
    public void setCloseCallback(Runnable callback) {
        this.closeCallback = callback;
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
        getContentPane().add(statePanel, BorderLayout.CENTER);
        setJMenuBar(createMenuBar());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
    }

    protected JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // File Menu
        JMenu fileMenu = new JMenu("File");
// --- Existing File Menu Items above ---

// Load Single Machine
        JMenuItem loadMachineItem = new JMenuItem("Load Single Machine");
        loadMachineItem.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Machine File (sm)", "sm"));
            int option = fileChooser.showOpenDialog(StateMachineEditor.this);
            if (option == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                    // Assume the file contains a serialized StateMachine object.
                    StateMachine loadedMachine = (StateMachine) ois.readObject();

                    // Create a deep clone using your new clone() method.
                    StateMachine clonedMachine = loadedMachine.clone();

                    // Option B: Update the current state machine with the clone's data.
                    stateMachine.setStates(clonedMachine.getStates());
                    stateMachine.setTransitions(clonedMachine.getTransitions());
                    stateMachine.setEvents(clonedMachine.getEvents());
                    stateMachine.setName(clonedMachine.getName());

                    // If pseudoState is accessible via a getter, update it as well:
                    // (Alternatively, ensure that your clone() method already updates the pseudoState field.)
                    stateMachine.setPseudoState(clonedMachine.getPseudoState());
                    // Or if pseudoState is a protected field you can do:
                    // stateMachine.pseudoState = clonedMachine.getPseudoState();

                    JOptionPane.showMessageDialog(StateMachineEditor.this,
                            "Machine successfully loaded: " + clonedMachine.getName());
                    statePanel.repaint();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(StateMachineEditor.this,
                            "Error loading machine: " + ex.getMessage());
                }
            }
        });

// Save Single Machine
        JMenuItem saveMachineItem = new JMenuItem("Save Single Machine");
        saveMachineItem.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Machine File (sm)", "sm"));
            int option = fileChooser.showSaveDialog(StateMachineEditor.this);
            if (option == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                if (!file.getName().toLowerCase().endsWith(".sm")) {
                    file = new File(file.getAbsolutePath() + ".sm");
                }
                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                    oos.writeObject(stateMachine);
                    oos.flush();
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
        exportPDFItem.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(
                    new javax.swing.filechooser.FileNameExtensionFilter("PDF File", "pdf"));

            if (fileChooser.showSaveDialog(StateMachineEditor.this)
                    == JFileChooser.APPROVE_OPTION) {

                File file = fileChooser.getSelectedFile();
                if (!file.getName().toLowerCase().endsWith(".pdf")) {
                    file = new File(file.getAbsolutePath() + ".pdf");
                }

                try {
                    utility.PDFExporter.exportPanelToPDF(statePanel, file);
                    JOptionPane.showMessageDialog(StateMachineEditor.this,
                            "PDF file successfully saved.");
                } catch (IOException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(StateMachineEditor.this,
                            "Error saving PDF: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        fileMenu.add(exportPDFItem);
        fileMenu.add(closeEditorItem);
        menuBar.add(fileMenu);

        // Edit Menu
        JMenu editMenu = new JMenu("Edit");

        editMenu.addSeparator();

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
        JCheckBoxMenuItem editModeItem = new JCheckBoxMenuItem("Edit mode", true);
        editModeItem.addActionListener(e -> statePanel.setEditMode(editModeItem.isSelected()));
        editMenu.add(editModeItem);

        menuBar.add(editMenu);
        // View menu (grid and snapping)
        JMenu viewMenu = new JMenu("View");

        JCheckBoxMenuItem showGridItem = new JCheckBoxMenuItem("Show grid", true);
        showGridItem.addActionListener(e -> statePanel.setShowGrid(showGridItem.isSelected()));
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

    public void setStateMachine(PWSStateMachine stateMachine) {
        this.stateMachine = stateMachine;
        if (this.statePanel != null) this.statePanel.setStateMachine(stateMachine);
    }

    // Generic binder for machinery.StateMachine instances so external callers can swap the edited machine.
    public void bindStateMachine(StateMachine sm) {
        this.stateMachine = sm;
        if (this.statePanel != null) this.statePanel.setStateMachine(sm);
        if (this.statePanel != null) {
            this.statePanel.revalidate();
            this.statePanel.repaint();
        }
    }
}