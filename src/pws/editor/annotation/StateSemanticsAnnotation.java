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
import smalgebra.BasicStateProposition;
import java.awt.Color;
import assembly.Assembly;

public class StateSemanticsAnnotation extends Annotation<PWSState> {
    private Assembly assembly;
    private PWSStateMachinePanel panel;
    
    // Minimized state: when true, shows as a small colored square
    private boolean minimized = false;
    // Stores expanded bounds for restoration when un-minimizing
    private Rectangle expandedBounds = null;
    // Size of the minimized indicator
    private static final int MINIMIZED_SIZE = 16;

    public StateSemanticsAnnotation(PWSState content) {
        this(content, null, null);
    }

    public StateSemanticsAnnotation(PWSState content, Assembly assembly, PWSStateMachinePanel panel) {
        super(content);
        this.assembly = assembly;
        this.panel = panel;
        setOpaque(true);
        setBackground(Color.WHITE);
        
        // Add double-click listener to toggle minimized state
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    toggleMinimized();
                }
            }
        });
    }
    
    /**
     * Toggles the minimized state of this dashboard.
     * When minimized, shows as a small colored indicator.
     * When expanded, shows the full semantics information.
     */
    public void toggleMinimized() {
        if (minimized) {
            // Expanding: restore size and center on current minimized square
            minimized = false;
            Rectangle current = getBounds();
            int centerX = current.x + current.width / 2;
            int centerY = current.y + current.height / 2;

            Dimension d;
            if (expandedBounds != null) {
                d = new Dimension(expandedBounds.width, expandedBounds.height);
            } else {
                // If no saved bounds, compute preferred size
                d = getPreferredSize();
            }
            int newX = centerX - d.width / 2;
            int newY = centerY - d.height / 2;
            expandedBounds = new Rectangle(newX, newY, d.width, d.height);
            setBounds(expandedBounds);
        } else {
            // Minimizing: save current bounds and shrink around center
            expandedBounds = getBounds();
            minimized = true;
            int centerX = expandedBounds.x + expandedBounds.width / 2;
            int centerY = expandedBounds.y + expandedBounds.height / 2;
            int newX = centerX - MINIMIZED_SIZE / 2;
            int newY = centerY - MINIMIZED_SIZE / 2;
            setBounds(newX, newY, MINIMIZED_SIZE, MINIMIZED_SIZE);
        }
        // Update the PWSState's minimized flag for persistence
        if (content != null) {
            content.setAnnotationMinimized(minimized);
        }
        revalidate();
        repaint();
        if (getParent() != null) {
            getParent().repaint();
        }
        // Mark document dirty
        java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(this);
        if (w instanceof pws.editor.PWSEditor pe) {
            pe.markDocumentDirty();
        }
    }
    
    /**
     * Returns whether this dashboard is currently minimized.
     */
    public boolean isMinimized() {
        return minimized;
    }
    
    /**
     * Sets the minimized state directly (used during deserialization).
     */
    public void setMinimized(boolean minimized) {
        this.minimized = minimized;
        if (minimized) {
            Rectangle current = getBounds();
            int centerX = current.x + current.width / 2;
            int centerY = current.y + current.height / 2;
            int newX = centerX - MINIMIZED_SIZE / 2;
            int newY = centerY - MINIMIZED_SIZE / 2;
            setBounds(newX, newY, MINIMIZED_SIZE, MINIMIZED_SIZE);
        }
    }

    @Override
    protected void showPopup(MouseEvent e) {
        // Create a popup with menu items for editing constraints
        JPopupMenu popup = new JPopupMenu();
        
        if (assembly != null && panel != null) {
            // Only show "Edit Constraints" for non-pseudostates (pseudostates always have "ANY")
            if (!content.isPseudoState()) {
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

            if (!content.isPseudoState()) {
                JMenuItem setAnyItem = new JMenuItem("Set Constraints to ANY");
                setAnyItem.addActionListener(ae -> {
                    content.setConstraintsSemantics(pws.editor.semantics.Semantics.top(assembly));
                    content.setRawConstraintText("ANY");
                    PWSStateMachine sm = panel.getStateMachine();
                    if (sm != null) {
                        sm.updateExitZonesForState(content);
                    }
                    java.awt.Window w = SwingUtilities.getWindowAncestor(panel);
                    if (w instanceof pws.editor.PWSEditor pe) {
                        pe.markDocumentDirty();
                        pe.scheduleSemanticsRecalculation();
                    }
                    panel.repaint();
                });
                popup.add(setAnyItem);
            }

            JMenuItem adaptConstraintsItem = new JMenuItem("Adapt Constraints to Configurations");
            Semantics currentSem = content.getStateSemantics();
            boolean hasConfigs = currentSem != null && !currentSem.getConfigurations().isEmpty();
            boolean canAdapt = !content.isPseudoState() && hasConfigs;
            adaptConstraintsItem.setEnabled(canAdapt);
            adaptConstraintsItem.addActionListener(ae -> {
                Semantics sem = content.getStateSemantics();
                if (sem == null || sem.getConfigurations().isEmpty()) return;
                content.setConstraintsSemantics(sem.clone());
                content.setRawConstraintText(buildRawConstraintTextFromSemantics(sem));
                PWSStateMachine sm = panel.getStateMachine();
                if (sm != null) {
                    sm.updateExitZonesForState(content);
                }
                java.awt.Window w = SwingUtilities.getWindowAncestor(panel);
                if (w instanceof pws.editor.PWSEditor pe) {
                    pe.markDocumentDirty();
                    pe.scheduleSemanticsRecalculation();
                }
                panel.repaint();
            });
            popup.add(adaptConstraintsItem);
            
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

    private String buildRawConstraintTextFromSemantics(Semantics sem) {
        if (sem == null || sem.getConfigurations().isEmpty()) return "";
        StringJoiner lines = new StringJoiner("\n");
        for (pws.editor.semantics.Configuration cfg : sem.getConfigurations()) {
            java.util.List<BasicStateProposition> props = cfg.getBasicStatePropositions();
            if (props == null || props.isEmpty()) continue;
            StringJoiner sj = new StringJoiner(", ");
            for (BasicStateProposition bsp : props) {
                sj.add(bsp.getMachineId() + "." + bsp.getStateName());
            }
            lines.add("(" + sj.toString() + ")");
        }
        return lines.toString();
    }

    @Override
    protected String buildDisplayText() {
        return "";
    }

    // Border thickness constant for consistent styling
    private static final int BORDER_THICKNESS = 2;
    private static final int CORNER_RADIUS = 8;
    private static final String EXIT_ZONE_ARROW = "→";
    private static boolean showExitZoneMachineIds = false;

    public static boolean isShowExitZoneMachineIds() {
        return showExitZoneMachineIds;
    }

    public static void setShowExitZoneMachineIds(boolean show) {
        showExitZoneMachineIds = show;
    }

    private static List<String> buildExitZoneLabels(List<ExitZone> zones) {
        if (zones == null || zones.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> labels = new ArrayList<>(zones.size());
        for (ExitZone ez : zones) {
            String targetKey = getExitZoneTargetKey(ez);
            labels.add(formatExitZoneLabel(ez, targetKey, true));
        }
        return labels;
    }

    private static String getExitZoneTargetKey(ExitZone ez) {
        if (ez == null || ez.getTarget() == null) {
            return "?";
        }
        return formatExitZoneState(ez.getTarget());
    }

    private static String formatExitZoneState(BasicStateProposition prop) {
        if (prop == null) {
            return "?";
        }
        return showExitZoneMachineIds ? prop.toString() : prop.getStateName();
    }

    private static String formatExitZoneLabel(ExitZone ez, String targetKey, boolean disambiguate) {
        if (ez == null) {
            return "?";
        }
        if (!disambiguate) {
            return targetKey;
        }
        BasicStateProposition source = ez.getSource();
        BasicStateProposition target = ez.getTarget();
        if (source == null || target == null) {
            return (source != null) ? (formatExitZoneState(source) + EXIT_ZONE_ARROW + targetKey) : targetKey;
        }
        return formatExitZoneState(source) + EXIT_ZONE_ARROW + formatExitZoneState(target);
    }

    private static int measureCommaSeparatedWidth(FontMetrics fm, List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return 0;
        }
        int width = 0;
        int sepWidth = fm.stringWidth(", ");
        for (int i = 0; i < labels.size(); i++) {
            width += fm.stringWidth(labels.get(i));
            if (i < labels.size() - 1) {
                width += sepWidth;
            }
        }
        return width;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        
        // Enable anti-aliasing for smooth rendering
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        
        // If minimized, draw a small colored indicator and return
        if (minimized) {
            paintMinimized(g2d);
            g2d.dispose();
            return;
        }
        
        // Draw rounded background
        if (isOpaque()) {
            g2d.setColor(getBackground());
            g2d.fillRoundRect(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS);
        }
        
        g2d.setFont(getFont().deriveFont(Font.PLAIN, 12f));
        g2d.setColor(Color.BLACK);

        if (content == null) {
            g2d.dispose();
            return;
        }

        PWSState state = content;
        FontMetrics fm = g2d.getFontMetrics();
        FontMetrics fmSmall = g2d.getFontMetrics(getFont().deriveFont(Font.ITALIC, 9f));
        int lineHeight = fm.getHeight();
        int smallLineHeight = fmSmall.getHeight();

        int padding = 6;
        int y = padding;
        
        // Draw subtle section label for constraints
        y += smallLineHeight;
        g2d.setFont(getFont().deriveFont(Font.ITALIC, 9f));
        g2d.setColor(new Color(150, 150, 150));
        g2d.drawString("constraints", padding, y);
        
        // 1) Constraint semantics (blue, centered)
        g2d.setFont(getFont().deriveFont(Font.PLAIN, 12f));
        y += lineHeight;
        String constraintSem;
        String raw = state.getRawConstraintText();
        if (state.isPseudoState()) {
            // Pseudostate always shows "ANY"
            constraintSem = "ANY";
        } else if (raw != null && !raw.isBlank()) {
            if ("ANY".equalsIgnoreCase(raw.trim())) {
                constraintSem = "ANY";
            } else {
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
            }
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
        if (constraintSem.isBlank()) {
            constraintSem = "ANY";
        }
        g2d.setColor(new Color(0, 70, 180)); // Darker blue for better contrast
        int w1 = fm.stringWidth(constraintSem);
        g2d.drawString(constraintSem, (getWidth() - w1) / 2, y);
        
        // Draw separator line
        y += 3;
        g2d.setColor(new Color(200, 200, 200));
        g2d.drawLine(padding + 2, y, getWidth() - padding - 2, y);
        
        // Draw subtle section label for configurations
        y += smallLineHeight + 1;
        g2d.setFont(getFont().deriveFont(Font.ITALIC, 9f));
        g2d.setColor(new Color(150, 150, 150));
        g2d.drawString("configs", padding, y);
        g2d.setFont(getFont().deriveFont(Font.PLAIN, 12f));

        // 2) Actual state semantics: each configuration green if in constraints, red otherwise
        y += lineHeight;
        Semantics constraintsSem = state.getConstraintsSemantics();
        Set<?> stateConfigs = state.getStateSemantics() == null
                ? Collections.emptySet()
                : state.getStateSemantics().getConfigurations();
        String rawConstraint = state.getRawConstraintText();
        boolean hasRaw = rawConstraint != null && !rawConstraint.isBlank();
        boolean rawAny = hasRaw && "ANY".equalsIgnoreCase(rawConstraint.trim());
        boolean hasCs = constraintsSem != null && !constraintsSem.getConfigurations().isEmpty();
        boolean anyConstraint = state.isPseudoState() || rawAny || (!hasRaw && !hasCs);
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

            // Special case: empty configuration "()" means no component machines configured
            boolean isEmptyConfig = s.equals("()");
            
            // Determine status for color and underline:
            // - isDeadlock: configuration cannot evolve internally (is in deadlockConfigurations)
            // - isCovered: configuration is covered by at least one outgoing transition guard
            boolean isDeadlock = deadlockCfgStrs.contains(s);
            boolean isCovered = coveredCfgStrs.contains(s);
            boolean canEvolve = !isDeadlock && !isEmptyConfig; // Empty config can't evolve (nothing to evolve)
            boolean satisfiesConstraint = anyConstraint;
            if (!satisfiesConstraint && constraintsSem != null && stateConfigs != null) {
                // Rebuild configuration object from stateConfigs for implication check
                for (Object cfgObj : stateConfigs) {
                    if (cfgObj instanceof pws.editor.semantics.Configuration cfg && cfg.toString().equals(s)) {
                        satisfiesConstraint = cfg.implies(constraintsSem);
                        break;
                    }
                }
            }
            boolean isTrueDeadlock = isDeadlock && !isCovered;
            
            // Color logic:
            // - Gray: Empty config (no component machines) - neutral/informational
            // - Green: Satisfies constraints
            // - Red: Violates constraints
            if (isEmptyConfig) {
                g2d.setColor(new Color(100, 100, 100)); // Gray for empty config
            } else {
                g2d.setColor(satisfiesConstraint ? Color.GREEN.darker() : Color.RED);
            }
            g2d.drawString(s, x, y);
            
            // Underline logic:
            // - Green underline: can evolve internally
            // - Red underline: true deadlock (internally stuck and not covered)
            // - No underline for empty config or covered-but-stuck
            if (canEvolve) {
                int sw = fm.stringWidth(s);
                g2d.setColor(Color.GREEN.darker());
                g2d.drawLine(x, y + 1, x + sw, y + 1);
            } else if (isTrueDeadlock) {
                int sw = fm.stringWidth(s);
                g2d.setColor(Color.RED);
                g2d.drawLine(x, y + 1, x + sw, y + 1);
            }
            x += fm.stringWidth(s) + fm.charWidth(' ');
        }
        
        // Draw separator line before exit zones
        y += 3;
        g2d.setColor(new Color(200, 200, 200));
        g2d.drawLine(padding + 2, y, getWidth() - padding - 2, y);
        
        // Draw subtle section label for exit zones
        y += smallLineHeight + 1;
        g2d.setFont(getFont().deriveFont(Font.ITALIC, 9f));
        g2d.setColor(new Color(150, 150, 150));
        g2d.drawString("exit zones", padding, y);
        // Legend line for exit zone colors (inline with dashboard)
        y += smallLineHeight;
        int legendX = padding;
        g2d.setColor(new Color(150, 150, 150));
        g2d.drawString("legend: ", legendX, y);
        legendX += fmSmall.stringWidth("legend: ");
        g2d.setColor(new Color(120, 120, 120));
        g2d.drawString("internal", legendX, y);
        legendX += fmSmall.stringWidth("internal");
        g2d.setColor(new Color(150, 150, 150));
        g2d.drawString(" / ", legendX, y);
        legendX += fmSmall.stringWidth(" / ");
        g2d.setColor(Color.GREEN.darker());
        g2d.drawString("covered", legendX, y);
        legendX += fmSmall.stringWidth("covered");
        g2d.setColor(new Color(150, 150, 150));
        g2d.drawString(" / ", legendX, y);
        legendX += fmSmall.stringWidth(" / ");
        g2d.setColor(new Color(180, 0, 0));
        g2d.drawString("uncovered/orphan", legendX, y);
        g2d.setFont(getFont().deriveFont(Font.PLAIN, 12f));

        // 3) Reactive exit zones: centered, comma-separated, colored by origin and coverage
        y += lineHeight;
        try {
            // Determine covered guards for coloring
            // Note: disabled transitions do not contribute to coverage
            Set<smalgebra.BasicStateProposition> covered = new HashSet<>();
            for (machinery.TransitionInterface ti : pwsMachine.getTransitions()) {
                if (ti instanceof pws.PWSTransition) {
                    pws.PWSTransition pt = (pws.PWSTransition) ti;
                    if (pt.isEnabled() && !pt.isTriggerable() && pt.getSource() == state
                            && pt.getGuardProposition() instanceof smalgebra.BasicStateProposition) {
                        covered.add((smalgebra.BasicStateProposition) pt.getGuardProposition());
                    }
                }
            }
            // Get CS-only and SS-only sets for later detailed analysis (used in extended dashboard)
            Set<ExitZone> csOnly = state.getCsOnlyExitZones();
            Set<ExitZone> ssOnly = state.getSsOnlyExitZones();
            Semantics ss = state.getStateSemantics();
            // Prepare list of exit-zones
            List<ExitZone> zones = new ArrayList<>(state.getReactiveSemantics());
            List<String> zoneLabels = buildExitZoneLabels(zones);
            // Compute total width of comma-separated exit-zone list
            int exitTotalWidth = measureCommaSeparatedWidth(fm, zoneLabels);
            int exitX = (getWidth() - exitTotalWidth) / 2;
            // Draw each exit-zone with comma separators
            // Simplified color logic: 
            // - Green if covered by an autonomous PWS transition
            // - Red if not covered
            Font baseFont = g2d.getFont();
            for (int i = 0; i < zones.size(); i++) {
                ExitZone ez = zones.get(i);
                String txt = zoneLabels.get(i);
                boolean isOrphan = ez.isOrphanSource(asm);
                boolean isInternal = false;
                if (ss != null && asm != null && ez.getTarget() != null) {
                    Semantics targetAndSem = ez.getTarget().toSemantics(asm).AND(ss);
                    isInternal = !targetAndSem.ISEMPTY();
                }
                boolean isCovered = !isOrphan && !isInternal && covered.contains(ez.getTarget());
                Color ezColor = isOrphan
                        ? new Color(180, 0, 0)
                        : (isInternal ? new Color(120, 120, 120)
                                      : (isCovered ? Color.GREEN.darker() : new Color(180, 0, 0)));
                g2d.setColor(ezColor);
                g2d.setFont(baseFont);
                g2d.drawString(txt, exitX, y);
                exitX += g2d.getFontMetrics().stringWidth(txt);
                if (i < zones.size() - 1) {
                    String sep = ", ";
                    g2d.setColor(Color.BLACK);
                    g2d.drawString(sep, exitX, y);
                    exitX += fm.stringWidth(sep);
                }
            }
            // After drawing all semantics, adjust border color:
            boolean allOk = true;
            
            // 0) Check for empty state semantics (unreachable state)
            // A state with no configurations cannot be reached, which is a problem
            // Exception: pseudo-state always has ANY semantics
            if (!state.isPseudoState() && 
                (state.getStateSemantics() == null || state.getStateSemantics().getConfigurations().isEmpty())) {
                allOk = false;
            }
            
            // 1) Check actual semantics vs. constraints
            if (!anyConstraint && constraintsSem != null && state.getStateSemantics() != null) {
                for (Object cfgObj : state.getStateSemantics().getConfigurations()) {
                    if (cfgObj instanceof pws.editor.semantics.Configuration cfg) {
                        if (!cfg.implies(constraintsSem)) {
                            allOk = false;
                            break;
                        }
                    }
                }
            }
            // 2) Check reactive exit-zones coverage
            if (allOk) {
                Semantics ssCheck = state.getStateSemantics();
                Assembly asmCheck = asm;
                for (ExitZone ez : state.getReactiveSemantics()) {
                    if (ez.isOrphanSource(asmCheck)) {
                        allOk = false;
                        break;
                    }
                    boolean isInternal = false;
                    if (ssCheck != null && asmCheck != null && ez.getTarget() != null) {
                        Semantics targetAndSem = ez.getTarget().toSemantics(asmCheck).AND(ssCheck);
                        isInternal = !targetAndSem.ISEMPTY();
                    }
                    if (!isInternal && !covered.contains(ez.getTarget())) {
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
            Color borderColor = allOk ? new Color(0, 140, 0) : new Color(180, 0, 0);
            
            // Draw custom rounded border with thicker line
            g2d.setColor(borderColor);
            g2d.setStroke(new BasicStroke(BORDER_THICKNESS));
            g2d.drawRoundRect(BORDER_THICKNESS/2, BORDER_THICKNESS/2, 
                             getWidth() - BORDER_THICKNESS, getHeight() - BORDER_THICKNESS, 
                             CORNER_RADIUS, CORNER_RADIUS);
            
            // Add subtle status indicator glow/tint in background
            if (!allOk) {
                g2d.setColor(new Color(255, 200, 200, 40)); // Very subtle red tint
                g2d.fillRoundRect(BORDER_THICKNESS, BORDER_THICKNESS, 
                                 getWidth() - 2*BORDER_THICKNESS, getHeight() - 2*BORDER_THICKNESS, 
                                 CORNER_RADIUS - 2, CORNER_RADIUS - 2);
            }
            
            // Remove the old simple border since we draw our own
            setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        } catch (Exception ignored) {
        }
        g2d.dispose();
    }
    
    /**
     * Paints the minimized indicator - a small rounded square colored based on overall status.
     */
    private void paintMinimized(Graphics2D g2d) {
        // Determine overall status (green = all OK, red = has issues)
        boolean allOk = computeOverallStatus();
        Color statusColor = allOk ? new Color(0, 160, 0) : new Color(200, 0, 0);
        
        int size = Math.min(getWidth(), getHeight());
        int radius = size / 3;
        
        // Fill the rounded square
        g2d.setColor(statusColor);
        g2d.fillRoundRect(1, 1, size - 2, size - 2, radius, radius);
        
        // Draw a subtle border
        g2d.setColor(statusColor.darker());
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRoundRect(1, 1, size - 3, size - 3, radius, radius);
    }
    
    /**
     * Computes the overall status of this state's semantics.
     * Returns true if all OK (green), false if has issues (red).
     */
    private boolean computeOverallStatus() {
        return getOverallStatusIssues().isEmpty();
    }

    public List<String> getOverallStatusIssues() {
        List<String> issues = new ArrayList<>();
        if (content == null) return issues;

        PWSState state = content;
        try {
            PWSStateMachine sm = null;
            if (getParent() instanceof PWSStateMachinePanel p) {
                sm = p.getStateMachine();
            } else if (panel != null) {
                sm = panel.getStateMachine();
            }
            if (sm == null) return issues;

            if (!state.isPseudoState() &&
                (state.getStateSemantics() == null || state.getStateSemantics().getConfigurations().isEmpty())) {
                issues.add("State is unreachable (no configurations).");
            }

            Set<smalgebra.BasicStateProposition> coveredGuards = new HashSet<>();
            for (machinery.TransitionInterface ti : sm.getTransitions()) {
                if (ti instanceof pws.PWSTransition pt) {
                    if (pt.isEnabled() && !pt.isTriggerable() && pt.getSource() == state
                            && pt.getGuardProposition() instanceof smalgebra.BasicStateProposition) {
                        coveredGuards.add((smalgebra.BasicStateProposition) pt.getGuardProposition());
                    }
                }
            }

            String rawConstraint = state.getRawConstraintText();
            boolean hasRaw = rawConstraint != null && !rawConstraint.isBlank();
            boolean rawAny = hasRaw && "ANY".equalsIgnoreCase(rawConstraint.trim());
            Semantics cs = state.getConstraintsSemantics();
            boolean hasCs = cs != null && !cs.getConfigurations().isEmpty();
            boolean anyConstraint = state.isPseudoState() || rawAny || (!hasRaw && !hasCs);
            boolean hasExplicitConstraint = hasRaw || hasCs;
            if (!anyConstraint && hasExplicitConstraint && cs != null && state.getStateSemantics() != null) {
                for (Object cfgObj : state.getStateSemantics().getConfigurations()) {
                    if (cfgObj instanceof pws.editor.semantics.Configuration cfg) {
                        if (!cfg.implies(cs)) {
                            issues.add("Some configurations violate constraints.");
                            break;
                        }
                    }
                }
            }

            if (hasExplicitConstraint && state.getReactiveSemantics() != null) {
                Semantics ssCheck = state.getStateSemantics();
                Assembly asmCheck = sm.getAssembly();
                boolean hasOrphan = false;
                boolean hasUncovered = false;
                for (ExitZone ez : state.getReactiveSemantics()) {
                    if (ez.isOrphanSource(asmCheck)) {
                        hasOrphan = true;
                        continue;
                    }
                    boolean isInternal = false;
                    if (ssCheck != null && asmCheck != null && ez.getTarget() != null) {
                        Semantics targetAndSem = ez.getTarget().toSemantics(asmCheck).AND(ssCheck);
                        isInternal = !targetAndSem.ISEMPTY();
                    }
                    if (!isInternal && !coveredGuards.contains(ez.getTarget())) {
                        hasUncovered = true;
                    }
                }
                if (hasUncovered) {
                    issues.add("Some exit zones are not covered by autonomous transitions.");
                }
                if (hasOrphan) {
                    issues.add("Orphan exit zones — no matching source state.");
                }
            }

            Set<pws.editor.semantics.Configuration> deadlocks = state.getDeadlockConfigurations();
            if (hasExplicitConstraint && deadlocks != null && !deadlocks.isEmpty()) {
                Assembly asm = sm.getAssembly();
                Set<String> coveredCfgStrs = new HashSet<>();
                for (machinery.TransitionInterface ti : sm.getTransitions()) {
                    if (ti instanceof pws.PWSTransition pt && pt.getSource() == state && pt.isEnabled()) {
                        smalgebra.SMProposition guardProp = pt.getGuardProposition();
                        Semantics guardSem = guardProp.toSemantics(asm).AND(state.getStateSemantics());
                        for (Object cfg : guardSem.getConfigurations()) {
                            coveredCfgStrs.add(cfg.toString());
                        }
                    }
                }
                for (pws.editor.semantics.Configuration dc : deadlocks) {
                    if (!coveredCfgStrs.contains(dc.toString())) {
                        issues.add("True deadlock configurations exist.");
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // ignore and default to OK
        }
        return issues;
    }

    @Override
    public Dimension getPreferredSize() {
        // If minimized, return small fixed size
        if (minimized) {
            return new Dimension(MINIMIZED_SIZE, MINIMIZED_SIZE);
        }
        if (content == null) return new Dimension(100, 50);

        PWSState state = content;
        // Determine constraint text for sizing, matching paintComponent logic
        String raw = state.getRawConstraintText();
        String constraintSem;
        if (state.isPseudoState()) {
            constraintSem = "ANY";
        } else if (raw != null && !raw.isBlank()) {
            if ("ANY".equalsIgnoreCase(raw.trim())) {
                constraintSem = "ANY";
            } else {
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
            }
        } else {
            // Fallback to full expanded semantics only if no raw text
            Semantics cs = state.getConstraintsSemantics();
            constraintSem = (cs == null) ? "" : cs.toString();
        }
        String actualSem = (state.getStateSemantics() == null)
            ? ""
            : state.getStateSemantics().toString();
        List<ExitZone> zones = (state.getReactiveSemantics() == null)
            ? Collections.emptyList()
            : new ArrayList<>(state.getReactiveSemantics());
        List<String> zoneLabels = buildExitZoneLabels(zones);

        String[] lines = new String[] { constraintSem, actualSem };
        FontMetrics fm = getFontMetrics(getFont().deriveFont(Font.PLAIN, 12f));
        FontMetrics fmSmall = getFontMetrics(getFont().deriveFont(Font.ITALIC, 9f));
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, fm.stringWidth(line));
        }
        int exitTotalWidth = measureCommaSeparatedWidth(fm, zoneLabels);
        maxWidth = Math.max(maxWidth, exitTotalWidth);
        // Account for section labels width
        maxWidth = Math.max(maxWidth, fmSmall.stringWidth("exit zones") + 20);
        // Account for exit zones legend width
        maxWidth = Math.max(maxWidth, fmSmall.stringWidth("legend: internal / covered / uncovered/orphan") + 20);
        
        // Match the exact y-positions used in paintComponent:
        // padding=6, then for each section: smallLineHeight + lineHeight + separator(~4)
        int padding = 6;
        int lineHeight = fm.getHeight();
        int smallLineHeight = fmSmall.getHeight();
        
        // Section 1: constraints label + content
        // Section 2: separator + configs label + content  
        // Section 3: separator + exit zones label + content
        int totalHeight = padding +
                          smallLineHeight + lineHeight +  // constraints section
                          4 + smallLineHeight + lineHeight +  // configs section (with separator)
                          4 + smallLineHeight + smallLineHeight + lineHeight +  // exit zones section (label + legend + content)
                          padding;
        
        // Add padding for borders
        return new Dimension(maxWidth + 24, totalHeight + 4);
    }
}
