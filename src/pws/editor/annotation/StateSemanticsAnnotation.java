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
    private static final Color PARTIAL_DEADLOCK_COLOR = new Color(200, 160, 0);
    private final java.util.List<HitArea> configHitAreas = new ArrayList<>();
    private final java.util.List<HitArea> exitZoneHitAreas = new ArrayList<>();
    private HitArea headerHitArea = null;
    private boolean csOnlyWarningActive = false;

    private static class HitArea {
        private final Rectangle bounds;
        private final String tooltip;

        private HitArea(Rectangle bounds, String tooltip) {
            this.bounds = bounds;
            this.tooltip = tooltip;
        }
    }

    private Font getBaseFont() {
        Font f = getFont();
        if (f == null) {
            f = new Font("Dialog", Font.PLAIN, 12);
        }
        return f;
    }

    private float getBaseFontSize() {
        return getBaseFont().getSize2D();
    }

    private Font getNormalFont() {
        float size = getBaseFontSize();
        return getBaseFont().deriveFont(Font.PLAIN, size);
    }

    private Font getSmallFont() {
        float size = Math.max(8f, getBaseFontSize() - 3f);
        return getBaseFont().deriveFont(Font.ITALIC, size);
    }

    public StateSemanticsAnnotation(PWSState content) {
        this(content, null, null);
    }

    public StateSemanticsAnnotation(PWSState content, Assembly assembly, PWSStateMachinePanel panel) {
        super(content);
        this.assembly = assembly;
        this.panel = panel;
        setOpaque(true);
        setBackground(Color.WHITE);
        ToolTipManager.sharedInstance().registerComponent(this);
        // Enable dynamic tooltips via getToolTipText override.
        setToolTipText(" ");
        
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

    @Override
    public String getToolTipText(MouseEvent e) {
        if (e == null || minimized) return null;
        Point p = e.getPoint();
        if (headerHitArea != null && headerHitArea.bounds != null && headerHitArea.bounds.contains(p)) {
            return headerHitArea.tooltip;
        }
        for (HitArea area : exitZoneHitAreas) {
            if (area != null && area.bounds != null && area.bounds.contains(p)) {
                return area.tooltip;
            }
        }
        for (HitArea area : configHitAreas) {
            if (area != null && area.bounds != null && area.bounds.contains(p)) {
                return area.tooltip;
            }
        }
        if (csOnlyWarningActive) {
            return "CS-only exit zones are provisional (from constraints only). Shown in blue.";
        }
        return null;
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

            JCheckBoxMenuItem showMachineIdsItem = new JCheckBoxMenuItem(
                    "Show machine IDs in exit zones", isShowExitZoneMachineIds());
            showMachineIdsItem.addActionListener(ae -> {
                setShowExitZoneMachineIds(showMachineIdsItem.isSelected());
                refreshExitZoneLabelLayout();
            });
            popup.add(showMachineIdsItem);
            
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

    private List<pws.editor.semantics.Configuration> buildConstraintConfigurations(PWSState state, Assembly asm) {
        List<pws.editor.semantics.Configuration> configs = new ArrayList<>();
        if (state == null) return configs;
        String raw = state.getRawConstraintText();
        boolean hasRaw = raw != null && !raw.isBlank();
        boolean rawAny = hasRaw && "ANY".equalsIgnoreCase(raw.trim());
        String assemblyId = (asm != null && asm.getAssemblyId() != null) ? asm.getAssemblyId() : "";
        if (hasRaw) {
            if (rawAny) {
                configs.add(new pws.editor.semantics.Configuration(assemblyId));
                return configs;
            }
            String[] lines = raw.split("\\r?\\n");
            for (String line : lines) {
                if (line == null) continue;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
                    trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
                }
                if (trimmed.isEmpty()) {
                    configs.add(new pws.editor.semantics.Configuration(assemblyId));
                    continue;
                }
                String[] parts = trimmed.split(",");
                List<BasicStateProposition> props = new ArrayList<>();
                for (String part : parts) {
                    if (part == null) continue;
                    String token = part.trim();
                    if (token.isEmpty()) continue;
                    int dot = token.indexOf('.');
                    if (dot <= 0 || dot >= token.length() - 1) {
                        continue;
                    }
                    String machineId = token.substring(0, dot).trim();
                    String stateName = token.substring(dot + 1).trim();
                    if (machineId.isEmpty() || stateName.isEmpty()) continue;
                    props.add(new BasicStateProposition(machineId, stateName));
                }
                if (!props.isEmpty()) {
                    configs.add(pws.editor.semantics.Configuration.fromBasicStatePropositions(assemblyId, props));
                }
            }
            if (!configs.isEmpty()) {
                return configs;
            }
        }
        Semantics cs = state.getConstraintsSemantics();
        if (cs != null && !cs.getConfigurations().isEmpty()) {
            for (Object cfg : cs.getConfigurations()) {
                if (cfg instanceof pws.editor.semantics.Configuration c) {
                    configs.add(c);
                }
            }
            if (!configs.isEmpty()) {
                return configs;
            }
        }
        configs.add(new pws.editor.semantics.Configuration(assemblyId));
        return configs;
    }

    private String buildEvolutionHint(pws.editor.semantics.Configuration cfg, PWSState state, Assembly asm) {
        if (cfg == null || state == null || asm == null) return null;
        Map<String, machinery.StateMachine> machines = asm.getStateMachines();
        if (machines == null || machines.isEmpty()) return null;
        java.util.LinkedHashSet<String> exitZoneLabels = new java.util.LinkedHashSet<>();
        java.util.LinkedHashSet<String> nextConfigLabels = new java.util.LinkedHashSet<>();
        for (Map.Entry<String, machinery.StateMachine> entry : machines.entrySet()) {
            String machineId = entry.getKey();
            machinery.StateMachine machine = entry.getValue();
            if (machine == null) continue;
            String currentStateName = cfg.getStateName(machineId);
            if (currentStateName == null) continue;
            for (machinery.TransitionInterface ti : machine.getTransitions()) {
                if (!(ti instanceof machinery.Transition t)) continue;
                if (!t.isEnabled() || !t.isAutonomous()) continue;
                if (t.getSource() == null || t.getTarget() == null) continue;
                if (!currentStateName.equals(t.getSource().getName())) continue;
                String targetStateName = t.getTarget().getName();
                pws.editor.semantics.Configuration nextCfg = cfg.replaceConstraint(machineId, targetStateName);
                if (nextCfg == null || nextCfg.equals(cfg)) continue;
                ExitZone ez = findExitZoneForTransition(state, machineId, t, currentStateName, targetStateName);
                if (ez != null) {
                    String targetKey = getExitZoneTargetKey(ez);
                    String label = formatExitZoneLabel(ez, targetKey, true);
                    exitZoneLabels.add(label);
                } else {
                    nextConfigLabels.add(nextCfg.toString());
                }
            }
        }
        if (exitZoneLabels.isEmpty() && nextConfigLabels.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (!exitZoneLabels.isEmpty()) {
            sb.append("Evolves to exit zone");
            if (exitZoneLabels.size() > 1) sb.append("s");
            sb.append(" ").append(String.join(", ", exitZoneLabels)).append(".");
        }
        if (!nextConfigLabels.isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("Evolves to configuration");
            if (nextConfigLabels.size() > 1) sb.append("s");
            sb.append(" ").append(String.join(", ", nextConfigLabels)).append(".");
        }
        return sb.toString();
    }

    private ExitZone findExitZoneForTransition(PWSState state,
                                               String machineId,
                                               machinery.Transition transition,
                                               String sourceState,
                                               String targetState) {
        if (state == null) return null;
        List<ExitZone> zones = new ArrayList<>();
        if (state.getReactiveSemantics() != null) {
            zones.addAll(state.getReactiveSemantics());
        }
        if (state.getCsOnlyExitZones() != null) {
            zones.addAll(state.getCsOnlyExitZones());
        }
        if (state.getSsOnlyExitZones() != null) {
            zones.addAll(state.getSsOnlyExitZones());
        }
        for (ExitZone ez : zones) {
            if (ez == null) continue;
            if (transition != null && ez.getTransition() == transition) {
                return ez;
            }
            BasicStateProposition src = ez.getSource();
            BasicStateProposition tgt = ez.getTarget();
            if (src == null || tgt == null) continue;
            if (machineId != null && !machineId.equals(src.getMachineId())) continue;
            if (sourceState != null && !sourceState.equals(src.getStateName())) continue;
            if (targetState != null && targetState.equals(tgt.getStateName())) {
                return ez;
            }
        }
        return null;
    }

    @Override
    protected String buildDisplayText() {
        return "";
    }

    // Border thickness constant for consistent styling
    private static final int BORDER_THICKNESS = 2;
    private static final int CORNER_RADIUS = 8;
    private static final String EXIT_ZONE_ARROW = "→";
    private static boolean showExitZoneMachineIds = true;

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
        return showExitZoneMachineIds ? (prop.getMachineId() + ":" + prop.getStateName()) : prop.getStateName();
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
        if (showExitZoneMachineIds && source != null && target != null) {
            return source.getMachineId() + ":" + source.getStateName() + EXIT_ZONE_ARROW + target.getStateName();
        }
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

    private boolean hasCsOnlyWarning(PWSState state) {
        return state != null && state.getCsOnlyExitZones() != null && !state.getCsOnlyExitZones().isEmpty();
    }

    private void updateCsOnlyTooltip(boolean hasWarning) {
        csOnlyWarningActive = hasWarning;
    }

    private void refreshExitZoneLabelLayout() {
        if (panel == null) {
            setSize(getPreferredSize());
            revalidate();
            repaint();
            return;
        }
        PWSStateMachine sm = panel.getStateMachine();
        if (sm != null) {
            for (machinery.StateInterface si : sm.getStates()) {
                if (si instanceof PWSState ps) {
                    StateSemanticsAnnotation ann = ps.getAnnotation();
                    if (ann != null) {
                        ann.setSize(ann.getPreferredSize());
                        ann.revalidate();
                        ann.repaint();
                    }
                }
            }
        }
        panel.revalidate();
        panel.repaint();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        
        // Enable anti-aliasing for smooth rendering
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        
        if (content == null) {
            setToolTipText(null);
            g2d.dispose();
            return;
        }

        boolean hasCsOnlyWarning = hasCsOnlyWarning(content);
        updateCsOnlyTooltip(hasCsOnlyWarning);

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
        
        g2d.setFont(getNormalFont());
        g2d.setColor(Color.BLACK);

        PWSState state = content;
        FontMetrics fm = g2d.getFontMetrics(getNormalFont());
        Font smallFont = getSmallFont();
        FontMetrics fmSmall = g2d.getFontMetrics(smallFont);
        int lineHeight = fm.getHeight();
        int smallLineHeight = fmSmall.getHeight();

        int padding = 6;
        int y = padding;

        PWSStateMachine pwsMachine = (panel != null) ? panel.getStateMachine()
                : (getParent() instanceof PWSStateMachinePanel p ? p.getStateMachine() : null);
        Assembly asm = (pwsMachine != null) ? pwsMachine.getAssembly() : assembly;

        Semantics constraintsSem = state.getConstraintsSemantics();
        String rawConstraint = state.getRawConstraintText();
        boolean hasRaw = rawConstraint != null && !rawConstraint.isBlank();
        boolean rawAny = hasRaw && "ANY".equalsIgnoreCase(rawConstraint.trim());
        boolean hasCs = constraintsSem != null && !constraintsSem.getConfigurations().isEmpty();
        boolean anyConstraint = state.isPseudoState() || rawAny || (!hasRaw && !hasCs);

        List<pws.editor.semantics.Configuration> constraintCfgList = buildConstraintConfigurations(state, asm);
        List<pws.editor.semantics.Configuration> cfgList = new ArrayList<>();
        if (state.getStateSemantics() != null) {
            for (Object cfg : state.getStateSemantics().getConfigurations()) {
                if (cfg instanceof pws.editor.semantics.Configuration c) {
                    cfgList.add(c);
                }
            }
        }

        List<String> machineIds = new ArrayList<>();
        if (asm != null && asm.getStateMachines() != null) {
            machineIds.addAll(asm.getStateMachines().keySet());
        }

        int colGap = 10;
        int tableWidth = 0;
        int startX = padding;
        int[] colWidths = null;
        if (!machineIds.isEmpty()) {
            colWidths = new int[machineIds.size()];
            for (int i = 0; i < machineIds.size(); i++) {
                int headerWidth = fmSmall.stringWidth(machineIds.get(i));
                int dashWidth = fm.stringWidth("-");
                colWidths[i] = Math.max(headerWidth, dashWidth);
            }
            for (pws.editor.semantics.Configuration cfg : constraintCfgList) {
                for (int i = 0; i < machineIds.size(); i++) {
                    String cell = cfg.getStateName(machineIds.get(i));
                    if (cell == null || cell.isBlank()) {
                        cell = "-";
                    }
                    colWidths[i] = Math.max(colWidths[i], fm.stringWidth(cell));
                }
            }
            for (pws.editor.semantics.Configuration cfg : cfgList) {
                for (int i = 0; i < machineIds.size(); i++) {
                    String cell = cfg.getStateName(machineIds.get(i));
                    if (cell == null || cell.isBlank()) {
                        cell = "-";
                    }
                    colWidths[i] = Math.max(colWidths[i], fm.stringWidth(cell));
                }
            }
            for (int i = 0; i < colWidths.length; i++) {
                tableWidth += colWidths[i];
                if (i < colWidths.length - 1) {
                    tableWidth += colGap;
                }
            }
            startX = Math.max(padding, (getWidth() - tableWidth) / 2);
        }

        // Precompute coverage/deadlock sets for drawing and status.
        Set<String> coveredCfgStrs = new HashSet<>();
        if (pwsMachine != null && asm != null && state.getStateSemantics() != null) {
            for (machinery.TransitionInterface ti2 : pwsMachine.getTransitions()) {
                if (ti2 instanceof pws.PWSTransition pt2 && pt2.getSource() == state && pt2.isEnabled()) {
                    smalgebra.SMProposition guardProp = pt2.getGuardProposition();
                    Semantics guardSem = guardProp.toSemantics(asm)
                                        .AND(state.getStateSemantics());
                    for (Object c : guardSem.getConfigurations()) {
                        coveredCfgStrs.add(c.toString());
                    }
                }
            }
        }
        Set<String> deadlockCfgStrs = new HashSet<>();
        Set<pws.editor.semantics.Configuration> deadlocks = state.getDeadlockConfigurations();
        if (deadlocks != null) {
            for (pws.editor.semantics.Configuration dc : deadlocks) {
                deadlockCfgStrs.add(dc.toString());
            }
        }
        Set<smalgebra.BasicStateProposition> coveredGuards = new HashSet<>();
        if (pwsMachine != null) {
            for (machinery.TransitionInterface ti : pwsMachine.getTransitions()) {
                if (ti instanceof pws.PWSTransition pt) {
                    if (pt.isEnabled() && !pt.isTriggerable() && pt.getSource() == state
                            && pt.getGuardProposition() instanceof smalgebra.BasicStateProposition) {
                        coveredGuards.add((smalgebra.BasicStateProposition) pt.getGuardProposition());
                    }
                }
            }
        }

        List<String> statusIssues = new ArrayList<>();
        if (!state.isPseudoState() &&
            (state.getStateSemantics() == null || state.getStateSemantics().getConfigurations().isEmpty())) {
            statusIssues.add("State is unreachable (no configurations).");
        }
        if (!anyConstraint && constraintsSem != null && state.getStateSemantics() != null) {
            for (Object cfgObj : state.getStateSemantics().getConfigurations()) {
                if (cfgObj instanceof pws.editor.semantics.Configuration cfg) {
                    if (!cfg.implies(constraintsSem)) {
                        statusIssues.add("Some configurations violate constraints.");
                        break;
                    }
                }
            }
        }
        boolean hasOrphan = false;
        boolean hasUncovered = false;
        boolean coverageRequired = !state.isFailState();
        Semantics ssCheck = state.getStateSemantics();
        if (state.getReactiveSemantics() != null) {
            for (ExitZone ez : state.getReactiveSemantics()) {
                if (ez.isOrphanSource(asm)) {
                    hasOrphan = true;
                    continue;
                }
                boolean isInternal = false;
                if (ssCheck != null && asm != null && ez.getTarget() != null) {
                    Semantics targetAndSem = ez.getTarget().toSemantics(asm).AND(ssCheck);
                    isInternal = !targetAndSem.ISEMPTY();
                }
                if (coverageRequired && !isInternal && !coveredGuards.contains(ez.getTarget())) {
                    hasUncovered = true;
                }
            }
        }
        if (coverageRequired && hasUncovered) {
            statusIssues.add("Some exit zones are not covered by autonomous transitions.");
        }
        if (hasOrphan) {
            statusIssues.add("Orphan exit zones — no matching source state.");
        }
        for (String deadlockStr : deadlockCfgStrs) {
            if (!coveredCfgStrs.contains(deadlockStr)) {
                statusIssues.add("True deadlock configurations exist.");
                break;
            }
        }

        boolean allOk = statusIssues.isEmpty();
        Color borderColor = allOk ? new Color(0, 140, 0) : new Color(180, 0, 0);

        int bandHeight = lineHeight + 4;
        int bandX = BORDER_THICKNESS;
        int bandY = BORDER_THICKNESS;
        int bandW = getWidth() - BORDER_THICKNESS * 2;
        g2d.setColor(borderColor);
        g2d.fillRoundRect(bandX, bandY, bandW, bandHeight, CORNER_RADIUS, CORNER_RADIUS);
        g2d.setColor(Color.WHITE);
        String stateName = state.getName() != null ? state.getName() : "";
        int nameWidth = fm.stringWidth(stateName);
        int nameX = Math.max(bandX + 4, (getWidth() - nameWidth) / 2);
        int nameY = bandY + (bandHeight - lineHeight) / 2 + fm.getAscent();
        g2d.drawString(stateName, nameX, nameY);
        String headerTip = allOk
                ? "Green: state is well-formed (constraints satisfied, exit zones covered, no true deadlocks)."
                : "Red: " + (statusIssues.isEmpty() ? "state has issues." : String.join("; ", statusIssues));
        if (state.isFailState()) {
            headerTip += " Fail state: exit-zone coverage not required.";
        }
        headerHitArea = new HitArea(
                new Rectangle(nameX, nameY - fm.getAscent(), Math.max(1, nameWidth), fm.getHeight()),
                headerTip);

        y = bandY + bandHeight + padding;
        g2d.setFont(getNormalFont());
        g2d.setColor(Color.BLACK);
        
        // Draw subtle section label for constraints
        y += smallLineHeight;
        g2d.setFont(smallFont);
        g2d.setColor(new Color(150, 150, 150));
        g2d.drawString("constraints", padding, y);
        
        // 1) Constraint semantics: stacked matrix (one configuration per row)
        if (!machineIds.isEmpty() && colWidths != null) {
            y += smallLineHeight;
            g2d.setFont(smallFont);
            g2d.setColor(new Color(130, 130, 130));
            int headerX = startX;
            for (int i = 0; i < machineIds.size(); i++) {
                String header = machineIds.get(i);
                int hw = fmSmall.stringWidth(header);
                g2d.drawString(header, headerX + (colWidths[i] - hw) / 2, y);
                headerX += colWidths[i] + colGap;
            }
            g2d.setFont(getNormalFont());
        }

        y += lineHeight;
        if (constraintCfgList.isEmpty()) {
            // Keep an empty line for layout consistency.
        }
        for (pws.editor.semantics.Configuration cfg : constraintCfgList) {
            g2d.setColor(new Color(0, 70, 180)); // Darker blue for better contrast
            int rowWidth = tableWidth;
            int rowStart = startX;
            if (!machineIds.isEmpty() && colWidths != null) {
                int cellX = startX;
                for (int i = 0; i < machineIds.size(); i++) {
                    String cell = cfg.getStateName(machineIds.get(i));
                    if (cell == null || cell.isBlank()) {
                        cell = "-";
                    }
                    int cw = fm.stringWidth(cell);
                    g2d.drawString(cell, cellX + (colWidths[i] - cw) / 2, y);
                    cellX += colWidths[i] + colGap;
                }
            } else {
                String display = cfg.getBasicStatePropositions().isEmpty() ? "ANY" : cfg.toString();
                int sw = fm.stringWidth(display);
                rowWidth = sw;
                rowStart = (getWidth() - sw) / 2;
                g2d.drawString(display, rowStart, y);
            }
            y += lineHeight;
        }
        if (!constraintCfgList.isEmpty()) {
            y -= lineHeight;
        }
        
        // Draw separator line
        y += 3;
        g2d.setColor(new Color(200, 200, 200));
        g2d.drawLine(padding + 2, y, getWidth() - padding - 2, y);
        
        // Draw subtle section label for configurations
        y += smallLineHeight + 1;
        g2d.setFont(smallFont);
        g2d.setColor(new Color(150, 150, 150));
        g2d.drawString("configs", padding, y);
        g2d.setFont(getNormalFont());

        // 2) Actual state semantics: one configuration per line, stacked as a compact matrix

        configHitAreas.clear();
        if (!machineIds.isEmpty() && colWidths != null) {
            // Header row: machine ids
            y += smallLineHeight;
            g2d.setFont(smallFont);
            g2d.setColor(new Color(130, 130, 130));
            int headerX = startX;
            for (int i = 0; i < machineIds.size(); i++) {
                String header = machineIds.get(i);
                int hw = fmSmall.stringWidth(header);
                g2d.drawString(header, headerX + (colWidths[i] - hw) / 2, y);
                headerX += colWidths[i] + colGap;
            }
            g2d.setFont(getNormalFont());
        }

        y += lineHeight;
        if (cfgList.isEmpty()) {
            // Keep an empty line for layout consistency.
        }
        for (pws.editor.semantics.Configuration cfg : cfgList) {
            String s = cfg.toString();
            boolean isEmptyConfig = cfg.getBasicStatePropositions().isEmpty();
            boolean isDeadlock = deadlockCfgStrs.contains(s);
            boolean isCovered = coveredCfgStrs.contains(s);
            boolean canEvolve = !isDeadlock && !isEmptyConfig;
            boolean satisfiesConstraint = anyConstraint;
            if (!satisfiesConstraint && constraintsSem != null) {
                satisfiesConstraint = cfg.implies(constraintsSem);
            }
            boolean isTrueDeadlock = isDeadlock && !isCovered;
            java.util.List<String> componentDeadlocks = findComponentDeadlocks(cfg, asm);
            boolean hasComponentDeadlock = !componentDeadlocks.isEmpty();

            if (isEmptyConfig) {
                g2d.setColor(new Color(100, 100, 100));
            } else {
                g2d.setColor(satisfiesConstraint ? Color.GREEN.darker() : Color.RED);
            }

            int rowWidth = tableWidth;
            int rowStart = startX;
            if (!machineIds.isEmpty() && colWidths != null) {
                int cellX = startX;
                for (int i = 0; i < machineIds.size(); i++) {
                    String cell = cfg.getStateName(machineIds.get(i));
                    if (cell == null || cell.isBlank()) {
                        cell = "-";
                    }
                    int cw = fm.stringWidth(cell);
                    g2d.drawString(cell, cellX + (colWidths[i] - cw) / 2, y);
                    cellX += colWidths[i] + colGap;
                }
            } else {
                int sw = fm.stringWidth(s);
                rowWidth = sw;
                rowStart = (getWidth() - sw) / 2;
                g2d.drawString(s, rowStart, y);
            }

            if (rowWidth > 0) {
                if (isTrueDeadlock) {
                    g2d.setColor(Color.RED);
                    g2d.drawLine(rowStart, y + 1, rowStart + rowWidth, y + 1);
                } else if (hasComponentDeadlock) {
                    g2d.setColor(PARTIAL_DEADLOCK_COLOR);
                    g2d.drawLine(rowStart, y + 1, rowStart + rowWidth, y + 1);
                } else if (canEvolve) {
                    g2d.setColor(Color.GREEN.darker());
                    g2d.drawLine(rowStart, y + 1, rowStart + rowWidth, y + 1);
                }
            }

            StringBuilder tip = new StringBuilder();
            if (isEmptyConfig) {
                tip.append("No component machines configured.");
            } else if (isTrueDeadlock) {
                tip.append("True deadlock: cannot evolve internally and not covered by any transition.");
            } else if (canEvolve) {
                tip.append("Can evolve internally.");
                String evolutionHint = buildEvolutionHint(cfg, state, asm);
                if (evolutionHint != null && !evolutionHint.isBlank()) {
                    tip.append(" ").append(evolutionHint);
                }
            } else {
                tip.append("Internally stuck but covered by an outgoing transition.");
            }
            if (!componentDeadlocks.isEmpty()) {
                if (tip.length() > 0) tip.append(" ");
                tip.append("Component deadlock: ").append(String.join(", ", componentDeadlocks)).append(".");
            }
            if (rowWidth > 0) {
                configHitAreas.add(new HitArea(
                        new Rectangle(rowStart, y - fm.getAscent(), rowWidth, fm.getHeight()),
                        tip.toString()));
            }
            y += lineHeight;
        }
        if (!cfgList.isEmpty()) {
            y -= lineHeight;
        }
        
        // Draw separator line before exit zones
        y += 3;
        g2d.setColor(new Color(200, 200, 200));
        g2d.drawLine(padding + 2, y, getWidth() - padding - 2, y);
        
        // Draw subtle section label for exit zones
        y += smallLineHeight + 1;
        g2d.setFont(smallFont);
        g2d.setColor(new Color(150, 150, 150));
        g2d.drawString("exit zones", padding, y);
        if (hasCsOnlyWarning) {
            int labelWidth = fmSmall.stringWidth("exit zones");
            int iconW = 12;
            int iconH = 10;
            int iconX = padding + labelWidth + 5;
            int iconY = y - fmSmall.getAscent() + (fmSmall.getHeight() - iconH) / 2;
            Color warn = new Color(0, 70, 180);
            g2d.setColor(warn);
            int[] xs = { iconX, iconX + iconW, iconX + iconW / 2 };
            int[] ys = { iconY + iconH, iconY + iconH, iconY };
            g2d.fillPolygon(xs, ys, 3);
        }
        g2d.setFont(getNormalFont());

        // 3) Reactive exit zones: stacked lines, colored by origin and coverage
        y += lineHeight;
        exitZoneHitAreas.clear();
        try {
            // Get CS-only and SS-only sets for later detailed analysis (used in extended dashboard)
            Set<ExitZone> csOnly = state.getCsOnlyExitZones();
            Set<ExitZone> ssOnly = state.getSsOnlyExitZones();
            Semantics ss = state.getStateSemantics();
            Color failCoverageColor = new Color(180, 140, 0);
            // Prepare list of exit-zones (SS first, then CS-only warnings)
            List<ExitZone> zones = new ArrayList<>(state.getReactiveSemantics());
            if (csOnly != null && !csOnly.isEmpty()) {
                for (ExitZone ez : csOnly) {
                    if (!zones.contains(ez)) {
                        zones.add(ez);
                    }
                }
            }
            List<String> zoneLabels = buildExitZoneLabels(zones);
            // Draw each exit-zone on its own line, centered.
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
                boolean isCovered = coverageRequired && !isOrphan && !isInternal && coveredGuards.contains(ez.getTarget());
                boolean isCsOnly = csOnly != null && csOnly.contains(ez);
                Color ezColor;
                if (isCsOnly) {
                    ezColor = new Color(0, 70, 180);
                } else {
                    ezColor = isOrphan
                            ? new Color(180, 0, 0)
                            : (isInternal ? new Color(120, 120, 120)
                                          : (coverageRequired
                                              ? (isCovered ? Color.GREEN.darker() : new Color(180, 0, 0))
                                              : failCoverageColor));
                }
                g2d.setColor(ezColor);
                g2d.setFont(baseFont);
                int txtWidth = fm.stringWidth(txt);
                int exitX = (getWidth() - txtWidth) / 2;
                g2d.drawString(txt, exitX, y);
                String status;
                if (isCsOnly) {
                    status = "Provisional (constraints only).";
                } else if (isOrphan) {
                    status = "Orphan (no matching source state).";
                } else if (isInternal) {
                    status = "Internal (reachable within current state semantics).";
                } else if (!coverageRequired) {
                    status = "Coverage not required for fail state.";
                } else if (isCovered) {
                    status = "Covered by an autonomous transition.";
                } else {
                    status = "Uncovered by autonomous transitions.";
                }
                exitZoneHitAreas.add(new HitArea(
                        new Rectangle(exitX, y - fm.getAscent(), txtWidth, fm.getHeight()),
                        "Exit zone " + txt + " - " + status));
                y += lineHeight;
            }
            if (!zones.isEmpty()) {
                y -= lineHeight;
            }
            // Draw border based on overall status.
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

    private java.util.List<String> findComponentDeadlocks(pws.editor.semantics.Configuration cfg, Assembly asm) {
        java.util.List<String> results = new ArrayList<>();
        if (cfg == null || asm == null) return results;
        for (BasicStateProposition bsp : cfg.getBasicStatePropositions()) {
            if (bsp == null) continue;
            String machineId = bsp.getMachineId();
            String stateName = bsp.getStateName();
            if (machineId == null || stateName == null) continue;
            machinery.StateMachine machine = asm.getStateMachines().get(machineId);
            if (machine == null) continue;
            machinery.StateInterface state = null;
            for (machinery.StateInterface si : machine.getStates()) {
                if (si != null && stateName.equals(si.getName())) {
                    state = si;
                    break;
                }
            }
            if (state == null || "PseudoState".equals(state.getName())) continue;
            boolean hasEnabledOutgoing = false;
            for (machinery.TransitionInterface ti : machine.getTransitions()) {
                if (ti != null && ti.getSource() == state && isTransitionEnabled(ti)) {
                    hasEnabledOutgoing = true;
                    break;
                }
            }
            if (!hasEnabledOutgoing) {
                results.add(machineId + "." + stateName);
            }
        }
        return results;
    }

    private boolean isTransitionEnabled(machinery.TransitionInterface t) {
        if (t instanceof machinery.Transition trans) {
            return trans.isEnabled();
        }
        return true;
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
                boolean coverageRequired = !state.isFailState();
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
                    if (coverageRequired && !isInternal && !coveredGuards.contains(ez.getTarget())) {
                        hasUncovered = true;
                    }
                }
                if (coverageRequired && hasUncovered) {
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
        if (constraintSem == null || constraintSem.isBlank()) {
            constraintSem = "ANY";
        }
        List<ExitZone> zones = (state.getReactiveSemantics() == null)
            ? Collections.emptyList()
            : new ArrayList<>(state.getReactiveSemantics());
        Set<ExitZone> csOnly = state.getCsOnlyExitZones();
        if (csOnly != null && !csOnly.isEmpty()) {
            for (ExitZone ez : csOnly) {
                if (!zones.contains(ez)) {
                    zones.add(ez);
                }
            }
        }
        List<String> zoneLabels = buildExitZoneLabels(zones);

        FontMetrics fm = getFontMetrics(getNormalFont());
        FontMetrics fmSmall = getFontMetrics(getSmallFont());
        int exitMaxWidth = 0;
        for (String label : zoneLabels) {
            if (label == null) continue;
            exitMaxWidth = Math.max(exitMaxWidth, fm.stringWidth(label));
        }
        int exitRows = Math.max(1, zoneLabels.size());

        Assembly asm = (assembly != null) ? assembly : (panel != null ? panel.getStateMachine().getAssembly() : null);
        List<pws.editor.semantics.Configuration> constraintCfgList = buildConstraintConfigurations(state, asm);
        int maxWidth = 0;

        int matrixWidth = 0;
        int colGap = 10;
        List<pws.editor.semantics.Configuration> cfgList = new ArrayList<>();
        if (state.getStateSemantics() != null) {
            for (Object cfg : state.getStateSemantics().getConfigurations()) {
                if (cfg instanceof pws.editor.semantics.Configuration c) {
                    cfgList.add(c);
                }
            }
        }
        List<String> machineIds = new ArrayList<>();
        if (asm != null && asm.getStateMachines() != null) {
            machineIds.addAll(asm.getStateMachines().keySet());
        }
        if (!machineIds.isEmpty()) {
            int[] colWidths = new int[machineIds.size()];
            for (int i = 0; i < machineIds.size(); i++) {
                int headerWidth = fmSmall.stringWidth(machineIds.get(i));
                int dashWidth = fm.stringWidth("-");
                colWidths[i] = Math.max(headerWidth, dashWidth);
            }
            for (pws.editor.semantics.Configuration cfg : constraintCfgList) {
                for (int i = 0; i < machineIds.size(); i++) {
                    String cell = cfg.getStateName(machineIds.get(i));
                    if (cell == null || cell.isBlank()) {
                        cell = "-";
                    }
                    colWidths[i] = Math.max(colWidths[i], fm.stringWidth(cell));
                }
            }
            for (pws.editor.semantics.Configuration cfg : cfgList) {
                for (int i = 0; i < machineIds.size(); i++) {
                    String cell = cfg.getStateName(machineIds.get(i));
                    if (cell == null || cell.isBlank()) {
                        cell = "-";
                    }
                    colWidths[i] = Math.max(colWidths[i], fm.stringWidth(cell));
                }
            }
            for (int i = 0; i < colWidths.length; i++) {
                matrixWidth += colWidths[i];
                if (i < colWidths.length - 1) {
                    matrixWidth += colGap;
                }
            }
        } else {
            for (pws.editor.semantics.Configuration cfg : constraintCfgList) {
                matrixWidth = Math.max(matrixWidth, fm.stringWidth(cfg.toString()));
            }
            for (pws.editor.semantics.Configuration cfg : cfgList) {
                matrixWidth = Math.max(matrixWidth, fm.stringWidth(cfg.toString()));
            }
            if (matrixWidth == 0) {
                matrixWidth = fm.stringWidth(constraintSem);
            }
        }
        maxWidth = Math.max(maxWidth, matrixWidth);
        maxWidth = Math.max(maxWidth, exitMaxWidth);
        // Account for section labels width
        String stateName = state.getName() != null ? state.getName() : "";
        maxWidth = Math.max(maxWidth, fm.stringWidth(stateName) + 24);
        maxWidth = Math.max(maxWidth, fmSmall.stringWidth("constraints") + 20);
        maxWidth = Math.max(maxWidth, fmSmall.stringWidth("exit zones") + 20);
        maxWidth = Math.max(maxWidth, fmSmall.stringWidth("configs") + 20);
        
        // Match the exact y-positions used in paintComponent:
        // padding=6, then for each section: smallLineHeight + lineHeight + separator(~4)
        int padding = 6;
        int lineHeight = fm.getHeight();
        int smallLineHeight = fmSmall.getHeight();
        
        int constraintRows = Math.max(1, constraintCfgList.size());
        int cfgRows = Math.max(1, cfgList.size());
        int headerHeight = machineIds.isEmpty() ? 0 : smallLineHeight;
        int bandHeight = lineHeight + 4;

        // Section 1: header band + constraints label + header + rows
        // Section 2: separator + configs label + header + rows
        // Section 3: separator + exit zones label + content
        int totalHeight = padding +
                          bandHeight + padding +  // header band
                          smallLineHeight + headerHeight + (constraintRows * lineHeight) +  // constraints section
                          4 + smallLineHeight + headerHeight + (cfgRows * lineHeight) +  // configs section (with separator)
                          4 + smallLineHeight + (exitRows * lineHeight) +  // exit zones section (label + content)
                          padding;
        
        // Add padding for borders
        return new Dimension(maxWidth + 24, totalHeight + 4);
    }
}
