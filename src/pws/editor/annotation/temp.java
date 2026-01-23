package pws.editor.annotation;

import pws.PWSState;
import pws.editor.PWSStateMachinePanel;
import pws.PWSStateMachine;
import java.util.*;
import java.util.List;
import java.util.ArrayList;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Collection;
import java.util.StringJoiner;

import pws.editor.semantics.ExitZone;
import pws.editor.semantics.Semantics;
import java.awt.Color;
import assembly.Assembly;

public class StateSemanticsAnnotation extends Annotation<PWSState> {
    private Assembly assembly;
    private PWSStateMachinePanel panel;

    public StateSemanticsAnnotation(PWSState content) {
        this(content, null, null);
    }

    public StateSemanticsAnnotation(PWSState content, Assembly assembly, PWSStateMachinePanel panel) {
        super(content);
        this.assembly = assembly;
        this.panel = panel;
        setOpaque(true);
        setBackground(Color.WHITE);
    }

    @Override
    protected void showPopup(MouseEvent e) {
        // Create a popup with menu items for editing constraints
        JPopupMenu popup = new JPopupMenu();
        
        if (assembly != null && panel != null) {
            JMenuItem editConstraintsItem = new JMenuItem("Edit Constraints Semantics");
            editConstraintsItem.addActionListener(ae -> {
                pws.editor.ConstraintsEditorDialog dialog = 
                    new pws.editor.ConstraintsEditorDialog(content, assembly);
                dialog.setVisible(true);
                // After editing constraints, update exit zones immediately
                PWSStateMachine sm = panel.getStateMachine();
                if (sm != null) {
                    sm.updateExitZonesForState(content);
                }
                // Mark document dirty, trigger semantics recalculation, and repaint
                java.awt.Window w = SwingUtilities.getWindowAncestor(panel);
                if (w instanceof pws.editor.PWSEditor pe) {
                    pe.markDocumentDirty();
                    pe.scheduleSemanticsRecalculation();
                }
                panel.repaint();
            });
            popup.add(editConstraintsItem);
            
            // Add "Show Extended Details" menu item
            JMenuItem showExtendedItem = new JMenuItem("Show Extended Details...");
            showExtendedItem.addActionListener(ae -> {
                PWSStateMachine sm = panel.getStateMachine();
                java.awt.Window owner = SwingUtilities.getWindowAncestor(panel);
                pws.editor.ExtendedDashboardDialog dialog = 
                    new pws.editor.ExtendedDashboardDialog(owner, content, sm, assembly);
                dialog.setVisible(true);
            });
            popup.add(showExtendedItem);
        }
        
        popup.show(this, e.getX(), e.getY());
    }

    @Override
    protected String buildDisplayText() {
        return "";
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (isOpaque()) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setFont(getFont().deriveFont(Font.PLAIN, 12f));
        g2d.setColor(Color.BLACK);

        if (content == null) return;

        PWSState state = content;
        FontMetrics fm = g2d.getFontMetrics();

        int padding = 4;
        int y = fm.getHeight() + padding;
        // 1) Constraint semantics (blue, centered)
        String constraintSem;
        String raw = state.getRawConstraintText();
        if (state.isPseudoState()) {
            // Pseudostate always shows "ANY"
            constraintSem = "ANY";
        } else if (raw != null && !raw.isBlank()) {
            // Show the user-entered compact constraints as (line1), (line2), ...
            String[] lines = raw.split("\\r?\\n");
            StringJoiner sjRaw = new StringJoiner(", ");
            for (String line : lines) {
                String s = line.trim();
                if (!s.startsWith("(")) s = "(" + s;
                if (!s.endsWith(")")) s = s + ")";
                sjRaw.add(s);
            }
            constraintSem = sjRaw.toString();
        } else {
            // Build a parenthesized OR‑joined constraint string
            Semantics cs = state.getConstraintsSemantics();
            if (cs == null) {
                constraintSem = "";
            } else {
                Collection<?> configs = cs.getConfigurations();
                if (configs.size() <= 1) {
                    // Single or none: show directly
                    constraintSem = configs.isEmpty()
                        ? ""
                        : configs.iterator().next().toString();
                } else {
                    // Multiple: wrap each in parentheses and join with OR
                    // Join multiple configurations with spaces, each wrapped in parentheses
                    StringJoiner sj = new StringJoiner(" ");
                    for (Object cfg : configs) {
                        String s = cfg.toString();
                        if (!s.startsWith("(") || !s.endsWith(")")) {
                            s = "(" + s + ")";
                        }
                        sj.add(s);
                    }
                    constraintSem = sj.toString();
                }
            }
        }
        g2d.setColor(Color.BLUE);
        int w1 = fm.stringWidth(constraintSem);
        g2d.drawString(constraintSem, (getWidth() - w1) / 2, y);

        // 2) Actual state semantics: each configuration green if in constraints, red otherwise
        y += fm.getHeight();
        Set<?> constraintsConfigs = state.getConstraintsSemantics() == null
                ? Collections.emptySet()
                : state.getConstraintsSemantics().getConfigurations();
        Set<?> stateConfigs = state.getStateSemantics() == null
                ? Collections.emptySet()
                : state.getStateSemantics().getConfigurations();
        // prepare string set of constraint configurations
        Set<String> constraintStrs = new HashSet<>();
        for (Object cfg : constraintsConfigs) {
            constraintStrs.add(cfg.toString());
        }
        List<String> cfgStrs = new ArrayList<>();
        // Compute which state configurations are covered by at least one outgoing guard
        // Note: disabled transitions do not contribute to coverage
        PWSStateMachine pwsMachine = ((PWSStateMachinePanel) getParent()).getStateMachine();
        Assembly asm = pwsMachine.getAssembly();
        Set<String> coveredCfgStrs = new HashSet<>();
        for (machinery.TransitionInterface ti2 : pwsMachine.getTransitions()) {
            if (ti2 instanceof pws.PWSTransition pt2 && pt2.getSource() == state && pt2.isEnabled()) {
                // guard proposition must hold under the state's current semantics
                smalgebra.SMProposition guardProp = pt2.getGuardProposition();
                Semantics guardSem = guardProp.toSemantics(asm)
                                    .AND(state.getStateSemantics());
                for (Object c : guardSem.getConfigurations()) {
                    coveredCfgStrs.add(c.toString());
                }
            }
        }
        for (Object cfg : stateConfigs) {
            cfgStrs.add(cfg.toString());
        }
        // Use cached deadlock configurations (computed during semantics recalculation, not at paint time)
        Set<String> deadlockCfgStrs = new HashSet<>();
        Set<pws.editor.semantics.Configuration> deadlocks = state.getDeadlockConfigurations();
        if (deadlocks != null) {
            for (pws.editor.semantics.Configuration dc : deadlocks) {
                deadlockCfgStrs.add(dc.toString());
            }
        }
        int totalWidth = 0;
        for (String s : cfgStrs) {
            totalWidth += fm.stringWidth(s) + fm.charWidth(' ');
        }
        int x = (getWidth() - totalWidth) / 2;
        for (String s : cfgStrs) {
