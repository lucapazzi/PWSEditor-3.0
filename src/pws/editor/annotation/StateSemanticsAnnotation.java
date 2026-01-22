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
            // Always paint green for the pseudostate’s actual semantics
            boolean isGreen = state.isPseudoState() || constraintStrs.contains(s);
            g2d.setColor(isGreen ? Color.GREEN.darker() : Color.RED);
            g2d.drawString(s, x, y);
            // Determine underline: 
            // - Red for deadlock configurations that are NOT covered by any outgoing transition
            // - Green for configurations covered by an outgoing transition guard
            // - None otherwise
            boolean isDeadlock = deadlockCfgStrs.contains(s);
            boolean isCovered = coveredCfgStrs.contains(s);
            if (isDeadlock && !isCovered) {
                // True deadlock: can't evolve internally AND not covered by any transition
                int sw = fm.stringWidth(s);
                g2d.setColor(Color.RED);
                g2d.drawLine(x, y + 1, x + sw, y + 1);
                g2d.drawLine(x, y + 2, x + sw, y + 2);  // double line for emphasis
            } else if (isCovered) {
                // Covered configurations get a green underline (they have a way out)
                int sw = fm.stringWidth(s);
                g2d.setColor(Color.GREEN.darker());
                g2d.drawLine(x, y + 1, x + sw, y + 1);
            }
            x += fm.stringWidth(s) + fm.charWidth(' ');
        }

        // 3) Reactive exit zones: centered, comma-separated, colored by origin and coverage
        y += fm.getHeight();
        try {
            PWSStateMachine sm = ((PWSStateMachinePanel) getParent()).getStateMachine();
            // Determine covered guards for coloring
            // Note: disabled transitions do not contribute to coverage
            Set<smalgebra.BasicStateProposition> covered = new HashSet<>();
            for (machinery.TransitionInterface ti : sm.getTransitions()) {
                if (ti instanceof pws.PWSTransition) {
                    pws.PWSTransition pt = (pws.PWSTransition) ti;
                    if (pt.isEnabled() && !pt.isTriggerable() && pt.getSource() == state
                            && pt.getGuardProposition() instanceof smalgebra.BasicStateProposition) {
                        covered.add((smalgebra.BasicStateProposition) pt.getGuardProposition());
                    }
                }
            }
            // Get CS-only and SS-only sets for color determination
            Set<ExitZone> csOnly = state.getCsOnlyExitZones();
            Set<ExitZone> ssOnly = state.getSsOnlyExitZones();
            // Prepare list of exit-zones
            List<ExitZone> zones = new ArrayList<>(state.getReactiveSemantics());
            // Compute total width of comma-separated exit-zone list
            int exitTotalWidth = 0;
            for (int i = 0; i < zones.size(); i++) {
                String txt = zones.get(i).toString();
                exitTotalWidth += fm.stringWidth(txt);
                if (i < zones.size() - 1) {
                    exitTotalWidth += fm.stringWidth(", ");
                }
            }
            int exitX = (getWidth() - exitTotalWidth) / 2;
            // Draw each exit-zone with comma separators
            // Color logic: 
            // - If covered by a guard transition: green
            // - Else if CS-only: dark yellow
            // - Else if SS-only: light red
            // - Else (in both CS and SS): dark red (bold)
            Font baseFont = g2d.getFont();
            Font boldFont = baseFont.deriveFont(Font.BOLD);
            for (int i = 0; i < zones.size(); i++) {
                ExitZone ez = zones.get(i);
                String txt = ez.toString();
                boolean isCovered = covered.contains(ez.getTarget());
                Color ezColor;
                boolean useBold = false;
                if (isCovered) {
                    ezColor = Color.GREEN.darker();
                } else if (csOnly != null && csOnly.contains(ez)) {
                    ezColor = new Color(204, 153, 0); // Dark yellow
                } else if (ssOnly != null && ssOnly.contains(ez)) {
                    ezColor = new Color(255, 102, 102); // Light red
                } else {
                    // In both CS and SS (not covered) - dark red, bold
                    ezColor = new Color(139, 0, 0); // Dark red
                    useBold = true;
                }
                g2d.setColor(ezColor);
                g2d.setFont(useBold ? boldFont : baseFont);
                g2d.drawString(txt, exitX, y);
                exitX += g2d.getFontMetrics().stringWidth(txt);
                g2d.setFont(baseFont); // Reset font
                if (i < zones.size() - 1) {
                    String sep = ", ";
                    g2d.setColor(Color.BLACK);
                    g2d.drawString(sep, exitX, y);
                    exitX += fm.stringWidth(sep);
                }
            }
            // After drawing all semantics, adjust border color:
            boolean allOk = true;
            // 1) Check actual semantics vs. constraints
            constraintStrs.clear();
            if (state.getConstraintsSemantics() != null) {
                for (Object cfg : state.getConstraintsSemantics().getConfigurations()) {
                    constraintStrs.add(cfg.toString());
                }
            }
            for (Object cfg : state.getStateSemantics().getConfigurations()) {
                if (!state.isPseudoState() && !constraintStrs.contains(cfg.toString())) {
                    allOk = false;
                    break;
                }
            }
            // 2) Check reactive exit-zones coverage
            if (allOk) {
                for (ExitZone ez : state.getReactiveSemantics()) {
                    if (!covered.contains(ez.getTarget())) {
                        allOk = false;
                        break;
                    }
                }
            }
            // 3) Check for true deadlock configurations 
            // (configurations that cannot reach others AND are not covered by any transition)
            if (allOk) {
                for (String deadlockStr : deadlockCfgStrs) {
                    if (!coveredCfgStrs.contains(deadlockStr)) {
                        // This is a true deadlock - no way out
                        allOk = false;
                        break;
                    }
                }
            }
            // Set the border based on overall OK status
            Color borderColor = allOk ? Color.GREEN.darker() : Color.RED;
            setBorder(BorderFactory.createLineBorder(borderColor, 1));
        } catch (Exception ignored) {
        }
    }

    @Override
    public Dimension getPreferredSize() {
        if (content == null) return new Dimension(100, 50);

        PWSState state = content;
        // Determine constraint text for sizing, matching paintComponent logic
        String raw = state.getRawConstraintText();
        String constraintSem;
        if (state.isPseudoState()) {
            constraintSem = "ANY";
        } else if (raw != null && !raw.isBlank()) {
            // Show compact user-entered constraints wrapped in parentheses
            String[] linesRaw = raw.split("\\r?\\n");
            StringJoiner sjRaw = new StringJoiner(", ");
            for (String line : linesRaw) {
                String s = line.trim();
                if (!s.startsWith("(")) s = "(" + s;
                if (!s.endsWith(")")) s = s + ")";
                sjRaw.add(s);
            }
            constraintSem = sjRaw.toString();
        } else {
            // Fallback to full expanded semantics only if no raw text
            Semantics cs = state.getConstraintsSemantics();
            constraintSem = (cs == null) ? "" : cs.toString();
        }
        String actualSem = (state.getStateSemantics() == null)
            ? ""
            : state.getStateSemantics().toString();
        String autonomousSem = (state.getReactiveSemantics() == null)
            ? ""
            : state.getReactiveSemantics().toString();

        String[] lines = new String[] { constraintSem, actualSem, autonomousSem };
        FontMetrics fm = getFontMetrics(getFont().deriveFont(Font.PLAIN, 12f));
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, fm.stringWidth(line));
        }
        int totalHeight = fm.getHeight() * lines.length;
        // Add padding
        return new Dimension(maxWidth + 10, totalHeight + 10);
    }
}