package pws.editor;

import assembly.Assembly;
import machinery.StateInterface;
import machinery.StateMachine;
import pws.PWSTransition;
import pws.editor.semantics.Configuration;
import pws.editor.semantics.Semantics;
import smalgebra.BasicStateProposition;
import smalgebra.FalseProposition;
import smalgebra.OrProposition;
import smalgebra.SMProposition;
import smalgebra.TrueProposition;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.StringJoiner;

/** Dialog that reuses the "add machine constraint" UI to compose trigger guards. */
public class GuardEditorDialog extends JDialog {
    private final Assembly assembly;
    private final PWSTransition transition;
    private final Consumer<SMProposition> onApply;
    private final Map<String, List<String>> machineStates = new LinkedHashMap<>();
    private final java.util.List<GuardLinePanel> guardLines = new ArrayList<>();
    private final JPanel guardLinesPanel;
    private final JLabel previewLabel;

    public GuardEditorDialog(Window owner,
                             PWSTransition transition,
                             Assembly assembly,
                             Consumer<SMProposition> onApply) {
        super(owner, "Advanced Guard Editor", ModalityType.APPLICATION_MODAL);
        this.transition = transition;
        this.assembly = assembly;
        this.onApply = onApply;

        buildMachineStatesCache();

        setLayout(new BorderLayout(8, 8));
        applyContentBorder();

        JLabel instructions = new JLabel(
                "<html><b>Build guard conditions:</b> create machine-state combinations (lines are OR-joined).<br>" +
                        "<i>Line with no machines = ANY (true guard).</i></html>");
        instructions.setBorder(BorderFactory.createEmptyBorder(5, 10, 0, 10));
        add(instructions, BorderLayout.NORTH);

        JLabel hint = new JLabel("<html><i>Machine list is filtered to states implied by the source state's semantics.</i></html>");
        hint.setBorder(BorderFactory.createEmptyBorder(0, 10, 6, 10));
        add(hint, BorderLayout.BEFORE_FIRST_LINE);

        guardLinesPanel = new JPanel();
        guardLinesPanel.setLayout(new BoxLayout(guardLinesPanel, BoxLayout.Y_AXIS));
        guardLinesPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Guard lines (OR-joined)",
                TitledBorder.LEFT, TitledBorder.TOP));

        JScrollPane scrollPane = new JScrollPane(guardLinesPanel);
        scrollPane.setPreferredSize(new Dimension(450, 220));
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        JButton addLineButton = new JButton("+ Add guard line");
        addLineButton.addActionListener(e -> addGuardLine(null));
        JPanel addLinePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addLinePanel.add(addLineButton);
        centerPanel.add(addLinePanel, BorderLayout.NORTH);

        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Preview", TitledBorder.LEFT, TitledBorder.TOP));
        previewLabel = new JLabel(" ");
        previewLabel.setFont(previewLabel.getFont().deriveFont(Font.ITALIC));
        previewPanel.add(previewLabel, BorderLayout.CENTER);
        centerPanel.add(previewPanel, BorderLayout.SOUTH);

        add(centerPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton setAnyBtn = new JButton("Set guard = ANY");
        JButton setFalseBtn = new JButton("Set guard = FALSE");
        JButton applyButton = new JButton("Apply");
        JButton cancelButton = new JButton("Cancel");

        setAnyBtn.addActionListener(this::setGuardToAny);
        setFalseBtn.addActionListener(this::setGuardToFalse);
        applyButton.addActionListener(e -> applyGuard());
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(setFalseBtn);
        buttonPanel.add(setAnyBtn);
        buttonPanel.add(Box.createHorizontalStrut(12));
        buttonPanel.add(applyButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

        initializeFromGuard();
        if (guardLines.isEmpty()) {
            addGuardLine(null);
        }

        pack();
        setMinimumSize(new Dimension(420, 360));
        setLocationRelativeTo(owner);
        updatePreview();
    }

    private void applyContentBorder() {
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    }

    private void buildMachineStatesCache() {
        if (assembly == null) return;

        // Gather states implied by source state semantics to guide the user.
        Map<String, java.util.Set<String>> allowedBySemantics = new LinkedHashMap<>();
        if (transition != null && transition.getSource() instanceof pws.PWSState ps) {
            Semantics sem = ps.getStateSemantics();
            if (sem != null) {
                sem.getConfigurations().forEach(cfg -> {
                    cfg.getBasicStatePropositions().forEach(bsp ->
                            allowedBySemantics
                                    .computeIfAbsent(bsp.getMachineId(), k -> new java.util.LinkedHashSet<>())
                                    .add(bsp.getStateName()));
                });
            }
        }

        for (Map.Entry<String, StateMachine> entry : assembly.getStateMachines().entrySet()) {
            String machineId = entry.getKey();
            StateMachine machine = entry.getValue();
            List<String> states = new ArrayList<>();
            for (StateInterface si : machine.getStates()) {
                String name = si.getName();
                if ("PseudoState".equals(name)) {
                    continue;
                }
                states.add(name);
            }
            Collections.sort(states);

            // Restrict to states that appear in the source semantics if available.
            java.util.Set<String> allowed = allowedBySemantics.get(machineId);
            if (allowed != null && !allowed.isEmpty()) {
                List<String> filtered = new ArrayList<>();
                for (String s : states) {
                    if (allowed.contains(s)) {
                        filtered.add(s);
                    }
                }
                states = filtered;
            }

            machineStates.put(machineId, states);
        }
    }

    private void initializeFromGuard() {
        if (transition == null || assembly == null) return;
        SMProposition guard = transition.getGuardProposition();
        if (guard == null || guard instanceof TrueProposition || guard instanceof FalseProposition) {
            return; // leave empty (ANY or FALSE placeholder)
        }

        List<List<BasicStateProposition>> products = parseProductsFromGuard(guard);
        if (products != null && !products.isEmpty()) {
            for (List<BasicStateProposition> product : products) {
                Map<String, String> selections = new LinkedHashMap<>();
                for (BasicStateProposition bsp : product) {
                    selections.put(bsp.getMachineId(), bsp.getStateName());
                }
                if (!selections.isEmpty()) {
                    addGuardLine(selections);
                }
            }
            return;
        }

        // Fallback: semantics-based (may expand), only if parsing failed
        Semantics sem = guard.toSemantics(assembly);
        if (sem == null || sem.getConfigurations().isEmpty()) {
            return;
        }
        for (Configuration config : sem.getConfigurations()) {
            Map<String, String> selections = new LinkedHashMap<>();
            for (BasicStateProposition bsp : config.getBasicStatePropositions()) {
                selections.put(bsp.getMachineId(), bsp.getStateName());
            }
            if (!selections.isEmpty()) {
                addGuardLine(selections);
            }
        }
    }

    private void addGuardLine(Map<String, String> initialSelections) {
        GuardLinePanel line = new GuardLinePanel(initialSelections);
        guardLines.add(line);
        guardLinesPanel.add(line);
        guardLinesPanel.revalidate();
        guardLinesPanel.repaint();
        updatePreview();
    }

    private void removeGuardLine(GuardLinePanel line) {
        guardLines.remove(line);
        guardLinesPanel.remove(line);
        guardLinesPanel.revalidate();
        guardLinesPanel.repaint();
        updatePreview();
    }

    private void applyGuard() {
        if (assembly == null) {
            dispose();
            return;
        }
        if (onApply != null) {
            SMProposition prop = buildPropositionFromLines();
            onApply.accept(prop);
        }
        dispose();
    }

    private void setGuardToAny(ActionEvent e) {
        if (onApply != null) {
            onApply.accept(new TrueProposition());
        }
        dispose();
    }

    private void setGuardToFalse(ActionEvent e) {
        if (onApply != null) {
            onApply.accept(new FalseProposition());
        }
        dispose();
    }

    private SMProposition convertSemanticsToProposition(Semantics sem) {
        SMProposition prop = null;
        for (Configuration config : sem.getConfigurations()) {
            SMProposition configProp = config.toSMProposition();
            if (prop == null) {
                prop = configProp;
            } else {
                prop = new OrProposition(prop, configProp);
            }
        }
        return prop != null ? prop : new TrueProposition();
    }

    private void updatePreview() {
        if (assembly == null) {
            previewLabel.setText("<html><i>Assembly unavailable.</i></html>");
            return;
        }
        SMProposition prop = buildPropositionFromLines();
        List<List<BasicStateProposition>> products = expandToProducts(prop);
        if (products.isEmpty() || (products.size() == 1 && products.get(0).isEmpty())) {
            previewLabel.setText("<html><i>ANY (true guard)</i></html>");
            return;
        }
        StringJoiner sj = new StringJoiner(", ");
        for (List<BasicStateProposition> product : products) {
            StringJoiner andJoin = new StringJoiner(", ");
            for (BasicStateProposition bsp : product) {
                andJoin.add(bsp.toString());
            }
            sj.add("(" + andJoin + ")");
        }
        previewLabel.setText("<html><code>" + sj + "</code></html>");
    }

    private class GuardLinePanel extends JPanel {
        private final List<MachineConstraintChip> chips = new ArrayList<>();

        GuardLinePanel(Map<String, String> initialSelections) {
            setLayout(new BorderLayout(4, 4));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 200)),
                    BorderFactory.createEmptyBorder(6, 6, 6, 6)));
            JPanel chipPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            chipPanel.setOpaque(false);
            add(chipPanel, BorderLayout.CENTER);

            JButton addMachineBtn = new JButton("+");
            addMachineBtn.setToolTipText("Add machine constraint");
            addMachineBtn.addActionListener(e -> showAddMachinePopup(addMachineBtn));
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
            buttonPanel.setOpaque(false);
            buttonPanel.add(addMachineBtn);
            JButton removeLineBtn = new JButton("×");
            removeLineBtn.setToolTipText("Remove this line");
            removeLineBtn.addActionListener(e -> removeGuardLine(this));
            buttonPanel.add(removeLineBtn);
            add(buttonPanel, BorderLayout.EAST);

            if (initialSelections != null) {
                for (Map.Entry<String, String> entry : initialSelections.entrySet()) {
                    addMachineConstraint(entry.getKey(), entry.getValue());
                }
            }
        }

        private void showAddMachinePopup(JButton button) {
            JPopupMenu popup = new JPopupMenu();
            java.util.Set<String> used = new java.util.HashSet<>();
            for (MachineConstraintChip chip : chips) {
                used.add(chip.getMachineId());
            }
            machineStates.forEach((machineId, states) -> {
                if (used.contains(machineId) || states.isEmpty()) return;
                JMenu machineMenu = new JMenu(machineId);
                for (String stateName : states) {
                    JMenuItem stateItem = new JMenuItem(stateName);
                    stateItem.addActionListener(e -> addMachineConstraint(machineId, stateName));
                    machineMenu.add(stateItem);
                }
                popup.add(machineMenu);
            });
            if (popup.getComponentCount() == 0) {
                JMenuItem none = new JMenuItem("No machines available");
                none.setEnabled(false);
                popup.add(none);
            }
            popup.show(button, 0, button.getHeight());
        }

        private void addMachineConstraint(String machineId, String stateName) {
            MachineConstraintChip chip = new MachineConstraintChip(this, machineId, stateName);
            chips.add(chip);
            ((JPanel) getComponent(0)).add(chip);
            revalidate();
            repaint();
            updatePreview();
        }

        private void removeChip(MachineConstraintChip chip) {
            chips.remove(chip);
            ((JPanel) getComponent(0)).remove(chip);
            revalidate();
            repaint();
            updatePreview();
        }

        java.util.List<BasicStateProposition> toProduct() {
            java.util.List<BasicStateProposition> props = new ArrayList<>();
            for (MachineConstraintChip chip : chips) {
                props.add(new BasicStateProposition(chip.getMachineId(), chip.getStateName()));
            }
            return props;
        }
    }

    private class MachineConstraintChip extends JPanel {
        private final GuardLinePanel owner;
        private final String machineId;
        private final JComboBox<String> stateCombo;

        MachineConstraintChip(GuardLinePanel owner, String machineId, String stateName) {
            this.owner = owner;
            this.machineId = machineId;
            setLayout(new FlowLayout(FlowLayout.LEFT, 2, 0));
            setBackground(new Color(230, 240, 250));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(150, 180, 210), 1),
                    BorderFactory.createEmptyBorder(2, 4, 2, 2)));

            JLabel machineLabel = new JLabel(machineId + ".");
            machineLabel.setFont(machineLabel.getFont().deriveFont(Font.BOLD, 11f));
            add(machineLabel);

            stateCombo = new JComboBox<>();
            machineStates.getOrDefault(machineId, Collections.emptyList()).forEach(stateCombo::addItem);
            stateCombo.setSelectedItem(stateName);
            stateCombo.setFont(stateCombo.getFont().deriveFont(11f));
            stateCombo.addActionListener(e -> updatePreview());
            add(stateCombo);

            JButton removeBtn = new JButton("×");
            removeBtn.setFont(removeBtn.getFont().deriveFont(10f));
            removeBtn.setMargin(new Insets(0, 3, 0, 3));
            removeBtn.setToolTipText("Remove " + machineId);
            removeBtn.addActionListener(e -> {
                owner.removeChip(this);
            });
            add(removeBtn);
        }

        String getMachineId() {
            return machineId;
        }

        Semantics toSemantics() {
            BasicStateProposition bsp = new BasicStateProposition(machineId, getStateName());
            return bsp.toSemantics(assembly);
        }

        String getStateName() {
            return (String) stateCombo.getSelectedItem();
        }
    }

    private SMProposition buildPropositionFromLines() {
        SMProposition prop = null;
        boolean anyLine = false;
        for (GuardLinePanel line : guardLines) {
            List<BasicStateProposition> product = line.toProduct();
            if (product.isEmpty()) {
                continue;
            }
            anyLine = true;
            SMProposition conj = null;
            for (BasicStateProposition bsp : product) {
                conj = (conj == null) ? bsp : new smalgebra.AndProposition(conj, bsp);
            }
            prop = (prop == null) ? conj : new smalgebra.OrProposition(prop, conj);
        }
        if (!anyLine) {
            return new TrueProposition();
        }
        return prop != null ? prop : new TrueProposition();
    }

    private List<List<BasicStateProposition>> parseProductsFromGuard(SMProposition prop) {
        List<List<BasicStateProposition>> result = new ArrayList<>();
        if (prop == null || prop instanceof FalseProposition) return result;
        if (prop instanceof TrueProposition) {
            result.add(new ArrayList<>()); // empty product
            return result;
        }
        if (prop instanceof BasicStateProposition bsp) {
            List<BasicStateProposition> single = new ArrayList<>();
            single.add(bsp);
            result.add(single);
            return result;
        }
        if (prop instanceof smalgebra.AndProposition and) {
            List<List<BasicStateProposition>> left = parseProductsFromGuard(and.getLeft());
            List<List<BasicStateProposition>> right = parseProductsFromGuard(and.getRight());
            for (List<BasicStateProposition> l : left) {
                for (List<BasicStateProposition> r : right) {
                    List<BasicStateProposition> combo = new ArrayList<>(l.size() + r.size());
                    combo.addAll(l);
                    combo.addAll(r);
                    result.add(combo);
                }
            }
            return result;
        }
        if (prop instanceof smalgebra.OrProposition or) {
            result.addAll(parseProductsFromGuard(or.getLeft()));
            result.addAll(parseProductsFromGuard(or.getRight()));
            return result;
        }
        // unsupported proposition type -> return empty to trigger fallback
        return result;
    }

    private List<List<BasicStateProposition>> expandToProducts(SMProposition prop) {
        List<List<BasicStateProposition>> result = new ArrayList<>();
        if (prop == null || prop instanceof FalseProposition) return result;
        if (prop instanceof TrueProposition) {
            result.add(new ArrayList<>());
            return result;
        }
        if (prop instanceof BasicStateProposition bsp) {
            List<BasicStateProposition> single = new ArrayList<>();
            single.add(bsp);
            result.add(single);
            return result;
        }
        if (prop instanceof smalgebra.AndProposition and) {
            List<List<BasicStateProposition>> left = expandToProducts(and.getLeft());
            List<List<BasicStateProposition>> right = expandToProducts(and.getRight());
            for (List<BasicStateProposition> l : left) {
                for (List<BasicStateProposition> r : right) {
                    List<BasicStateProposition> combo = new ArrayList<>(l.size() + r.size());
                    combo.addAll(l);
                    combo.addAll(r);
                    result.add(combo);
                }
            }
            return result;
        }
        if (prop instanceof smalgebra.OrProposition or) {
            result.addAll(expandToProducts(or.getLeft()));
            result.addAll(expandToProducts(or.getRight()));
            return result;
        }
        return result;
    }
}
