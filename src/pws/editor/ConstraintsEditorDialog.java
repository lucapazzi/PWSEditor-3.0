package pws.editor;

import assembly.Assembly;
import machinery.StateMachine;
import machinery.StateInterface;
import pws.PWSState;
import pws.editor.semantics.Semantics;
import pws.editor.semantics.Configuration;
import smalgebra.BasicStateProposition;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Visual dialog for editing constraint semantics of a PWS state.
 * Uses an "add machine constraint" approach where you explicitly add only
 * the machines you want to constrain.
 */
public class ConstraintsEditorDialog extends JDialog {
    private PWSState state;
    private Assembly assembly;
    private JPanel constraintLinesPanel;
    private JLabel previewLabel;
    private List<ConstraintLinePanel> constraintLines = new ArrayList<>();
    
    // Cache of machine IDs and their states
    private Map<String, List<String>> machineStates = new LinkedHashMap<>();

    /**
     * Creates a dialog to edit constraint semantics for a state.
     *
     * @param state state whose constraints are edited
     * @param assembly assembly context
     */
    public ConstraintsEditorDialog(PWSState state, Assembly assembly) {
        this.state = state;
        this.assembly = assembly;
        
        // Build machine states cache
        buildMachineStatesCache();
        
        setModal(true);
        setTitle("Edit Constraints: " + state.getName());
        setLayout(new BorderLayout(8, 8));
        
        // Main panel with constraints
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        
        // Header with instructions
        JLabel instructionLabel = new JLabel(
            "<html><b>Build constraints:</b> Add machine constraints. Lines are OR-joined.<br>" +
            "<i>Tip: Only add machines you want to constrain. Unmentioned machines allow any state.</i></html>");
        instructionLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        mainPanel.add(instructionLabel, BorderLayout.NORTH);
        
        // Scrollable constraint lines panel
        constraintLinesPanel = new JPanel();
        constraintLinesPanel.setLayout(new BoxLayout(constraintLinesPanel, BoxLayout.Y_AXIS));
        constraintLinesPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Constraint Lines (OR-joined)",
            TitledBorder.LEFT, TitledBorder.TOP));
        
        JScrollPane scrollPane = new JScrollPane(constraintLinesPanel);
        scrollPane.setPreferredSize(new Dimension(450, 180));
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        
        // Add line button panel
        JPanel addButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addLineButton = new JButton("+ Add Constraint Line");
        addLineButton.addActionListener(e -> addConstraintLine(null));
        addButtonPanel.add(addLineButton);
        
        // Preview panel
        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Preview",
            TitledBorder.LEFT, TitledBorder.TOP));
        previewLabel = new JLabel(" ");
        previewLabel.setFont(previewLabel.getFont().deriveFont(Font.ITALIC));
        previewPanel.add(previewLabel, BorderLayout.CENTER);
        
        JPanel bottomMainPanel = new JPanel(new BorderLayout());
        bottomMainPanel.add(addButtonPanel, BorderLayout.NORTH);
        bottomMainPanel.add(previewPanel, BorderLayout.CENTER);
        mainPanel.add(bottomMainPanel, BorderLayout.SOUTH);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton applyButton = new JButton("Apply");
        JButton cancelButton = new JButton("Cancel");
        
        applyButton.addActionListener(e -> {
            applyConstraints();
            dispose();
        });
        cancelButton.addActionListener(e -> dispose());
        
        buttonPanel.add(applyButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);
        
        // Initialize with existing constraints
        initializeFromState();
        
        // If no constraints, add one empty line
        if (constraintLines.isEmpty()) {
            addConstraintLine(null);
        }
        
        pack();
        setMinimumSize(new Dimension(400, 300));
        setLocationRelativeTo(null);
    }
    
    private void buildMachineStatesCache() {
        for (Map.Entry<String, StateMachine> entry : assembly.getStateMachines().entrySet()) {
            String machineId = entry.getKey();
            StateMachine machine = entry.getValue();
            List<String> states = new ArrayList<>();
            for (StateInterface si : machine.getStates()) {
                // Skip pseudostates - they shouldn't be selectable as constraints
                String name = si.getName();
                if ("PseudoState".equals(name)) {
                    continue;
                }
                states.add(name);
            }
            Collections.sort(states);
            machineStates.put(machineId, states);
        }
    }
    
    private void initializeFromState() {
        // First, try to use the raw constraint text (preserves partial specifications)
        String rawText = state.getRawConstraintText();
        if (rawText != null && !rawText.isBlank()) {
            // Parse raw text lines - each line is a constraint
            for (String line : rawText.split("\\r?\\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if ("ANY".equalsIgnoreCase(trimmed)) {
                    // Explicit ANY means no constraints for this line.
                    continue;
                }
                
                // Remove surrounding parentheses if present
                if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
                    trimmed = trimmed.substring(1, trimmed.length() - 1);
                }
                
                // Parse machine.state pairs
                Map<String, String> selections = new LinkedHashMap<>();
                for (String pair : trimmed.split(",")) {
                    String p = pair.trim();
                    String machine = null, stateName = null;
                    if (p.contains(":")) {
                        String[] parts = p.split(":", 2);
                        machine = parts[0].trim();
                        stateName = parts[1].trim();
                    } else if (p.contains(".")) {
                        String[] parts = p.split("\\.", 2);
                        machine = parts[0].trim();
                        stateName = parts[1].trim();
                    }
                    if (machine != null && stateName != null) {
                        selections.put(machine, stateName);
                    }
                }
                if (!selections.isEmpty()) {
                    addConstraintLine(selections);
                }
            }
            return;
        }
        
        // Fallback: use expanded semantics (only if no raw text)
        Semantics sem = state.getConstraintsSemantics();
        if (sem == null || sem.getConfigurations().isEmpty()) {
            return;
        }
        
        // Each configuration becomes a constraint line
        for (Configuration config : sem.getConfigurations()) {
            // Extract only the explicitly set machine:state pairs
            Map<String, String> selections = new LinkedHashMap<>();
            for (BasicStateProposition bsp : config.getBasicStatePropositions()) {
                selections.put(bsp.getMachineId(), bsp.getStateName());
            }
            if (!selections.isEmpty()) {
                addConstraintLine(selections);
            }
        }
    }
    
    private void addConstraintLine(Map<String, String> initialSelections) {
        ConstraintLinePanel linePanel = new ConstraintLinePanel(initialSelections);
        constraintLines.add(linePanel);
        constraintLinesPanel.add(linePanel);
        constraintLinesPanel.revalidate();
        constraintLinesPanel.repaint();
        updatePreview();
    }
    
    private void removeConstraintLine(ConstraintLinePanel linePanel) {
        constraintLines.remove(linePanel);
        constraintLinesPanel.remove(linePanel);
        constraintLinesPanel.revalidate();
        constraintLinesPanel.repaint();
        updatePreview();
    }
    
    private void updatePreview() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (ConstraintLinePanel line : constraintLines) {
            String lineText = line.toConstraintString();
            if (!lineText.isEmpty()) {
                if (!first) sb.append(", ");
                first = false;
                sb.append("(").append(lineText).append(")");
            }
        }
        if (sb.length() == 0) {
            previewLabel.setText("<html><i>ANY</i></html>");
        } else {
            previewLabel.setText("<html><code>" + sb.toString() + "</code></html>");
        }
    }
    
    private void applyConstraints() {
        Semantics result = Semantics.bottom(assembly);
        StringBuilder rawText = new StringBuilder();
        boolean firstLine = true;
        boolean hasAnyConstraint = false;
        
        for (ConstraintLinePanel line : constraintLines) {
            if (line.hasAnyConstraint()) {
                hasAnyConstraint = true;
            }
            Semantics lineSem = line.toSemantics();
            if (lineSem != null) {
                result = result.OR(lineSem);
                
                String lineText = line.toConstraintString();
                if (!lineText.isEmpty()) {
                    if (!firstLine) rawText.append("\n");
                    firstLine = false;
                    rawText.append(lineText);
                }
            }
        }
        
        String raw = rawText.toString().trim();
        if (!hasAnyConstraint) {
            // Explicit ANY = top semantics (all configurations allowed).
            result = Semantics.top(assembly);
            raw = "ANY";
        }
        state.setConstraintsSemantics(result);
        state.setRawConstraintText(raw);
    }
    
    /**
     * Inner class representing a single constraint line.
     * Users can add/remove individual machine constraints within this line.
     */
    private class ConstraintLinePanel extends JPanel {
        private JPanel machineConstraintsPanel;
        private List<MachineConstraintPanel> machineConstraints = new ArrayList<>();
        
        ConstraintLinePanel(Map<String, String> initialSelections) {
            setLayout(new BorderLayout(4, 2));
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
            
            // Panel for machine constraints (flows left to right)
            machineConstraintsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            
            // Add existing constraints
            if (initialSelections != null && !initialSelections.isEmpty()) {
                for (Map.Entry<String, String> entry : initialSelections.entrySet()) {
                    addMachineConstraint(entry.getKey(), entry.getValue());
                }
            }
            
            add(machineConstraintsPanel, BorderLayout.CENTER);
            
            // Right side buttons panel
            JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
            
            // Add machine button
            JButton addMachineBtn = new JButton("+");
            addMachineBtn.setToolTipText("Add machine constraint");
            addMachineBtn.setFont(addMachineBtn.getFont().deriveFont(Font.BOLD));
            addMachineBtn.setMargin(new Insets(2, 6, 2, 6));
            addMachineBtn.addActionListener(e -> showAddMachinePopup(addMachineBtn));
            buttonsPanel.add(addMachineBtn);
            
            // Remove line button
            JButton removeLineBtn = new JButton("×");
            removeLineBtn.setToolTipText("Remove this constraint line");
            removeLineBtn.setFont(removeLineBtn.getFont().deriveFont(Font.BOLD, 14f));
            removeLineBtn.setMargin(new Insets(0, 6, 0, 6));
            removeLineBtn.setForeground(new Color(180, 0, 0));
            removeLineBtn.addActionListener(e -> removeConstraintLine(this));
            buttonsPanel.add(removeLineBtn);
            
            add(buttonsPanel, BorderLayout.EAST);
        }
        
        private void showAddMachinePopup(JButton button) {
            JPopupMenu popup = new JPopupMenu();
            
            // Get machines not already constrained in this line
            Set<String> usedMachines = new HashSet<>();
            for (MachineConstraintPanel mcp : machineConstraints) {
                usedMachines.add(mcp.getMachineId());
            }
            
            boolean hasAvailable = false;
            for (String machineId : machineStates.keySet()) {
                if (!usedMachines.contains(machineId)) {
                    hasAvailable = true;
                    JMenu machineMenu = new JMenu(machineId);
                    for (String stateName : machineStates.get(machineId)) {
                        JMenuItem stateItem = new JMenuItem(stateName);
                        stateItem.addActionListener(e -> {
                            addMachineConstraint(machineId, stateName);
                            updatePreview();
                        });
                        machineMenu.add(stateItem);
                    }
                    popup.add(machineMenu);
                }
            }
            
            if (!hasAvailable) {
                JMenuItem noMore = new JMenuItem("(all machines constrained)");
                noMore.setEnabled(false);
                popup.add(noMore);
            }
            
            popup.show(button, 0, button.getHeight());
        }
        
        private void addMachineConstraint(String machineId, String stateName) {
            MachineConstraintPanel mcp = new MachineConstraintPanel(machineId, stateName);
            machineConstraints.add(mcp);
            machineConstraintsPanel.add(mcp);
            machineConstraintsPanel.revalidate();
            machineConstraintsPanel.repaint();
        }
        
        private void removeMachineConstraint(MachineConstraintPanel mcp) {
            machineConstraints.remove(mcp);
            machineConstraintsPanel.remove(mcp);
            machineConstraintsPanel.revalidate();
            machineConstraintsPanel.repaint();
            updatePreview();
        }
        
        String toConstraintString() {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (MachineConstraintPanel mcp : machineConstraints) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(mcp.getMachineId()).append(".").append(mcp.getStateName());
            }
            return sb.toString();
        }
        
        Semantics toSemantics() {
            if (machineConstraints.isEmpty()) {
                return null; // Empty line doesn't contribute
            }
            
            Semantics configSem = Semantics.top(assembly);
            for (MachineConstraintPanel mcp : machineConstraints) {
                BasicStateProposition bsp = new BasicStateProposition(mcp.getMachineId(), mcp.getStateName());
                configSem = configSem.AND(bsp.toSemantics(assembly));
            }
            return configSem;
        }

        boolean hasAnyConstraint() {
            if (machineConstraints.isEmpty()) {
                return false;
            }
            return true;
        }
        
        /**
         * Inner class representing a single machine:state constraint chip.
         */
        private class MachineConstraintPanel extends JPanel {
            private String machineId;
            private JComboBox<String> stateCombo;
            
            MachineConstraintPanel(String machineId, String stateName) {
                this.machineId = machineId;
                setLayout(new FlowLayout(FlowLayout.LEFT, 2, 0));
                setBackground(new Color(230, 240, 250));
                setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(150, 180, 210), 1),
                    BorderFactory.createEmptyBorder(2, 4, 2, 2)));
                
                // Machine label
                JLabel machineLabel = new JLabel(machineId + ".");
                machineLabel.setFont(machineLabel.getFont().deriveFont(Font.BOLD, 11f));
                add(machineLabel);
                
                // State dropdown (so user can change the state)
                stateCombo = new JComboBox<>();
                for (String s : machineStates.get(machineId)) {
                    stateCombo.addItem(s);
                }
                stateCombo.setSelectedItem(stateName);
                stateCombo.setFont(stateCombo.getFont().deriveFont(11f));
                stateCombo.addActionListener(e -> updatePreview());
                add(stateCombo);
                
                // Remove button
                JButton removeBtn = new JButton("×");
                removeBtn.setFont(removeBtn.getFont().deriveFont(10f));
                removeBtn.setMargin(new Insets(0, 3, 0, 3));
                removeBtn.setToolTipText("Remove " + machineId);
                removeBtn.addActionListener(e -> removeMachineConstraint(this));
                add(removeBtn);
            }
            
            String getMachineId() {
                return machineId;
            }
            
            String getStateName() {
                return (String) stateCombo.getSelectedItem();
            }
        }
    }
}
