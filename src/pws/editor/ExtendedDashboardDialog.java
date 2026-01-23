package pws.editor;

import pws.PWSState;
import pws.PWSStateMachine;
import pws.PWSTransition;
import pws.editor.semantics.Configuration;
import pws.editor.semantics.ExitZone;
import pws.editor.semantics.Semantics;
import assembly.Assembly;
import machinery.TransitionInterface;
import smalgebra.BasicStateProposition;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.util.*;

/**
 * Extended dashboard dialog that shows detailed semantics information for a state.
 * Provides comprehensive analysis including constraint semantics, computed semantics,
 * exit zones with origin information, coverage status, and deadlock analysis.
 */
public class ExtendedDashboardDialog extends JDialog {

    private final PWSState state;
    private final PWSStateMachine stateMachine;
    private final Assembly assembly;
    private final JTextPane textPane;

    // Style names for formatting
    private static final String STYLE_HEADING = "heading";
    private static final String STYLE_SUBHEADING = "subheading";
    private static final String STYLE_NORMAL = "normal";
    private static final String STYLE_GREEN = "green";
    private static final String STYLE_RED = "red";
    private static final String STYLE_BLUE = "blue";
    private static final String STYLE_ORANGE = "orange";
    private static final String STYLE_GRAY = "gray";
    private static final String STYLE_BOLD = "bold";
    private static final String STYLE_GREEN_UNDERLINE = "greenUnderline";

    public ExtendedDashboardDialog(Window owner, PWSState state, PWSStateMachine stateMachine, Assembly assembly) {
        super(owner, "Extended Dashboard: " + state.getName(), ModalityType.MODELESS);
        this.state = state;
        this.stateMachine = stateMachine;
        this.assembly = assembly;

        // Create text pane with styled document
        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        setupStyles();

        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.setPreferredSize(new Dimension(700, 500));

        // Refresh button
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> populateContent());

        // Close button
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(refreshButton);
        buttonPanel.add(closeButton);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(scrollPane, BorderLayout.CENTER);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);

        populateContent();
        pack();
        setLocationRelativeTo(owner);
    }

    private void setupStyles() {
        StyledDocument doc = textPane.getStyledDocument();
        
        // Base style
        Style defaultStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
        
        // Normal style
        Style normal = doc.addStyle(STYLE_NORMAL, defaultStyle);
        StyleConstants.setFontFamily(normal, "Monospaced");
        StyleConstants.setFontSize(normal, 12);
        
        // Heading style
        Style heading = doc.addStyle(STYLE_HEADING, normal);
        StyleConstants.setBold(heading, true);
        StyleConstants.setFontSize(heading, 14);
        StyleConstants.setForeground(heading, new Color(0, 51, 102));
        
        // Subheading style
        Style subheading = doc.addStyle(STYLE_SUBHEADING, normal);
        StyleConstants.setBold(subheading, true);
        StyleConstants.setForeground(subheading, new Color(51, 51, 51));
        
        // Green style (covered/OK)
        Style green = doc.addStyle(STYLE_GREEN, normal);
        StyleConstants.setForeground(green, new Color(0, 128, 0));
        
        // Red style (uncovered/problem)
        Style red = doc.addStyle(STYLE_RED, normal);
        StyleConstants.setForeground(red, new Color(180, 0, 0));
        
        // Blue style (constraint semantics)
        Style blue = doc.addStyle(STYLE_BLUE, normal);
        StyleConstants.setForeground(blue, new Color(0, 0, 180));
        
        // Orange style (warning)
        Style orange = doc.addStyle(STYLE_ORANGE, normal);
        StyleConstants.setForeground(orange, new Color(204, 102, 0));
        
        // Gray style (secondary info)
        Style gray = doc.addStyle(STYLE_GRAY, normal);
        StyleConstants.setForeground(gray, Color.GRAY);
        
        // Bold style
        Style bold = doc.addStyle(STYLE_BOLD, normal);
        StyleConstants.setBold(bold, true);
        
        // Green underline style (can evolve internally)
        Style greenUnderline = doc.addStyle(STYLE_GREEN_UNDERLINE, normal);
        StyleConstants.setForeground(greenUnderline, new Color(0, 128, 0));
        StyleConstants.setUnderline(greenUnderline, true);
    }

    private void populateContent() {
        StyledDocument doc = textPane.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
        } catch (BadLocationException e) {
            // ignore
        }

        appendText("═══════════════════════════════════════════════════════════════\n", STYLE_HEADING);
        appendText("EXTENDED DASHBOARD FOR STATE: " + state.getName() + "\n", STYLE_HEADING);
        appendText("═══════════════════════════════════════════════════════════════\n\n", STYLE_HEADING);

        // Section 1: Constraint Semantics
        appendConstraintSemanticsSection();
        
        // Section 2: Computed Semantics
        appendComputedSemanticsSection();
        
        // Section 3: Exit Zones Analysis
        appendExitZonesSection();
        
        // Section 4: Deadlock Analysis
        appendDeadlockAnalysisSection();
        
        // Section 5: Overall Status
        appendOverallStatusSection();

        // Scroll to top
        textPane.setCaretPosition(0);
    }

    private void appendConstraintSemanticsSection() {
        appendText("┌─────────────────────────────────────────────────────────────┐\n", STYLE_SUBHEADING);
        appendText("│ CONSTRAINT SEMANTICS (User-Defined)                         │\n", STYLE_SUBHEADING);
        appendText("└─────────────────────────────────────────────────────────────┘\n", STYLE_SUBHEADING);

        if (state.isPseudoState()) {
            appendText("  Pseudo-state: ", STYLE_NORMAL);
            appendText("ANY (all configurations allowed)\n\n", STYLE_BLUE);
            return;
        }

        String raw = state.getRawConstraintText();
        if (raw != null && !raw.isBlank()) {
            appendText("  Raw constraint text:\n", STYLE_GRAY);
            for (String line : raw.split("\\r?\\n")) {
                appendText("    " + line + "\n", STYLE_BLUE);
            }
            appendText("\n", STYLE_NORMAL);
        }

        Semantics cs = state.getConstraintsSemantics();
        if (cs != null && !cs.getConfigurations().isEmpty()) {
            appendText("  Expanded configurations:\n", STYLE_GRAY);
            for (Configuration cfg : cs.getConfigurations()) {
                appendText("    " + cfg.toString() + "\n", STYLE_BLUE);
            }
        } else {
            appendText("  No constraints defined (defaults to all configurations)\n", STYLE_GRAY);
        }
        appendText("\n", STYLE_NORMAL);
    }

    private void appendComputedSemanticsSection() {
        appendText("┌─────────────────────────────────────────────────────────────┐\n", STYLE_SUBHEADING);
        appendText("│ COMPUTED SEMANTICS (State Semantics)                        │\n", STYLE_SUBHEADING);
        appendText("└─────────────────────────────────────────────────────────────┘\n", STYLE_SUBHEADING);

        Semantics ss = state.getStateSemantics();
        Semantics cs = state.getConstraintsSemantics();
        
        // Get covered configurations from outgoing transitions
        Set<String> coveredCfgStrs = new HashSet<>();
        if (stateMachine != null) {
            for (TransitionInterface ti : stateMachine.getTransitions()) {
                if (ti instanceof PWSTransition pt && pt.isEnabled() && pt.getSource() == state) {
                    if (pt.getGuardProposition() != null && ss != null && assembly != null) {
                        for (Configuration cfg : ss.getConfigurations()) {
                            if (pt.getGuardProposition().evaluateConfiguration(cfg, assembly)) {
                                coveredCfgStrs.add(cfg.toString());
                            }
                        }
                    }
                }
            }
        }

        // Get deadlock configurations
        Set<Configuration> deadlocks = state.getDeadlockConfigurations();
        Set<String> deadlockStrs = new HashSet<>();
        if (deadlocks != null) {
            for (Configuration cfg : deadlocks) {
                deadlockStrs.add(cfg.toString());
            }
        }

        // Constraint strings for comparison
        Set<String> constraintStrs = new HashSet<>();
        if (cs != null) {
            for (Configuration cfg : cs.getConfigurations()) {
                constraintStrs.add(cfg.toString());
            }
        }

        if (ss != null && !ss.getConfigurations().isEmpty()) {
            appendText("  Configurations (shown as in dashboard):\n", STYLE_GRAY);
            appendText("    Legend: ", STYLE_GRAY);
            appendText("GRAY", STYLE_GRAY);
            appendText(" = no machines, ", STYLE_GRAY);
            appendText("RED", STYLE_RED);
            appendText(" = deadlock, ", STYLE_GRAY);
            appendText("GREEN", STYLE_GREEN);
            appendText(" = covered, ", STYLE_GRAY);
            appendText("GREEN UNDERLINE", STYLE_GREEN_UNDERLINE);
            appendText(" = can evolve\n\n", STYLE_GRAY);
            
            for (Configuration cfg : ss.getConfigurations()) {
                String cfgStr = cfg.toString();
                
                // Special case: empty configuration "()" means no component machines configured
                boolean isEmptyConfig = cfgStr.equals("()");
                
                boolean satisfiesConstraint = state.isPseudoState() || constraintStrs.isEmpty() || constraintStrs.contains(cfgStr);
                boolean isCovered = coveredCfgStrs.contains(cfgStr);
                boolean isDeadlock = deadlockStrs.contains(cfgStr);
                boolean canEvolve = !isDeadlock && !isEmptyConfig; // Empty config can't evolve
                
                // Determine style matching the dashboard:
                // - Gray: Empty config (no component machines) - neutral/informational
                // - Red: True deadlock (can't evolve AND not covered)
                // - Green: Covered by transition (has a way out)
                // - Green underline: Can evolve internally (not a deadlock risk)
                String style;
                String indicator;
                
                if (isEmptyConfig) {
                    // EMPTY CONFIG: Gray - means no component machines configured
                    style = STYLE_GRAY;
                    indicator = " ○ No component machines configured";
                } else if (!satisfiesConstraint) {
                    style = STYLE_RED;
                    indicator = " ⚠ VIOLATES CONSTRAINTS";
                } else if (isDeadlock && !isCovered) {
                    // TRUE DEADLOCK: Red (no underline)
                    style = STYLE_RED;
                    indicator = " ⛔ TRUE DEADLOCK";
                } else if (isCovered) {
                    // COVERED: Green (no underline)
                    style = STYLE_GREEN;
                    indicator = " ✓ Covered by transition";
                } else if (canEvolve) {
                    // CAN EVOLVE: Green underline
                    style = STYLE_GREEN_UNDERLINE;
                    indicator = " ↻ Can evolve internally";
                } else {
                    style = STYLE_NORMAL;
                    indicator = "";
                }
                
                appendText("    ", STYLE_NORMAL);
                appendText(cfgStr, style);
                appendText(indicator + "\n", STYLE_GRAY);
            }
        } else {
            appendText("  No computed semantics available\n", STYLE_GRAY);
        }
        appendText("\n", STYLE_NORMAL);
    }

    private void appendExitZonesSection() {
        appendText("┌─────────────────────────────────────────────────────────────┐\n", STYLE_SUBHEADING);
        appendText("│ EXIT ZONES ANALYSIS                                         │\n", STYLE_SUBHEADING);
        appendText("└─────────────────────────────────────────────────────────────┘\n", STYLE_SUBHEADING);

        Set<ExitZone> reactiveZones = state.getReactiveSemantics();
        Set<ExitZone> csOnlyZones = state.getCsOnlyExitZones();
        Set<ExitZone> ssOnlyZones = state.getSsOnlyExitZones();

        // Compute covered exit zones
        Set<BasicStateProposition> coveredGuards = new HashSet<>();
        if (stateMachine != null) {
            for (TransitionInterface ti : stateMachine.getTransitions()) {
                if (ti instanceof PWSTransition pt && pt.isEnabled() && !pt.isTriggerable() 
                        && pt.getSource() == state 
                        && pt.getGuardProposition() instanceof BasicStateProposition) {
                    coveredGuards.add((BasicStateProposition) pt.getGuardProposition());
                }
            }
        }

        if (reactiveZones == null || reactiveZones.isEmpty()) {
            appendText("  No exit zones detected for this state.\n", STYLE_GRAY);
            appendText("  (This is normal if constraints don't restrict component machine states)\n\n", STYLE_GRAY);
            return;
        }

        appendText("  Exit zones (reactive semantics):\n\n", STYLE_GRAY);

        for (ExitZone ez : reactiveZones) {
            boolean isCovered = coveredGuards.contains(ez.getTarget());
            boolean isCsOnly = csOnlyZones != null && csOnlyZones.contains(ez);
            boolean isSsOnly = ssOnlyZones != null && ssOnlyZones.contains(ez);
            
            // Determine origin
            String origin;
            if (isCsOnly) {
                origin = "CS-only (from Constraint Semantics)";
            } else if (isSsOnly) {
                origin = "SS-only (from State Semantics)";
            } else {
                origin = "Both CS and SS";
            }

            appendText("    Exit Zone: ", STYLE_BOLD);
            appendText(ez.toString() + "\n", isCovered ? STYLE_GREEN : STYLE_RED);
            
            appendText("      Machine:     ", STYLE_GRAY);
            appendText(ez.getStateMachineId() + "\n", STYLE_NORMAL);
            
            appendText("      Transition:  ", STYLE_GRAY);
            appendText(ez.getSource().getStateName() + " → " + ez.getTarget().getStateName() + "\n", STYLE_NORMAL);
            
            appendText("      Origin:      ", STYLE_GRAY);
            if (isCsOnly) {
                appendText(origin + "\n", STYLE_BLUE);
            } else if (isSsOnly) {
                appendText(origin + "\n", STYLE_ORANGE);
            } else {
                appendText(origin + "\n", STYLE_NORMAL);
            }
            
            appendText("      Coverage:    ", STYLE_GRAY);
            if (isCovered) {
                appendText("✓ COVERED by autonomous PWS transition\n", STYLE_GREEN);
            } else {
                appendText("✗ NOT COVERED - needs PWS transition with guard [" + 
                          ez.getTarget().toString() + "]\n", STYLE_RED);
            }
            appendText("\n", STYLE_NORMAL);
        }

        // Summary
        long coveredCount = reactiveZones.stream()
            .filter(ez -> coveredGuards.contains(ez.getTarget()))
            .count();
        long uncoveredCount = reactiveZones.size() - coveredCount;
        
        appendText("  Summary: ", STYLE_BOLD);
        appendText(coveredCount + " covered", STYLE_GREEN);
        appendText(", ", STYLE_NORMAL);
        if (uncoveredCount > 0) {
            appendText(uncoveredCount + " uncovered", STYLE_RED);
        } else {
            appendText("0 uncovered", STYLE_GREEN);
        }
        appendText(" (total: " + reactiveZones.size() + ")\n\n", STYLE_GRAY);
    }

    private void appendDeadlockAnalysisSection() {
        appendText("┌─────────────────────────────────────────────────────────────┐\n", STYLE_SUBHEADING);
        appendText("│ DEADLOCK ANALYSIS                                           │\n", STYLE_SUBHEADING);
        appendText("└─────────────────────────────────────────────────────────────┘\n", STYLE_SUBHEADING);

        Set<Configuration> deadlocks = state.getDeadlockConfigurations();
        
        if (deadlocks == null || deadlocks.isEmpty()) {
            appendText("  No deadlock configurations detected.\n", STYLE_GREEN);
            appendText("  All configurations can either evolve internally or are covered by transitions.\n\n", STYLE_GRAY);
            return;
        }

        // Get covered configurations
        Set<String> coveredCfgStrs = new HashSet<>();
        Semantics ss = state.getStateSemantics();
        if (stateMachine != null && ss != null && assembly != null) {
            for (TransitionInterface ti : stateMachine.getTransitions()) {
                if (ti instanceof PWSTransition pt && pt.isEnabled() && pt.getSource() == state) {
                    if (pt.getGuardProposition() != null) {
                        for (Configuration cfg : ss.getConfigurations()) {
                            if (pt.getGuardProposition().evaluateConfiguration(cfg, assembly)) {
                                coveredCfgStrs.add(cfg.toString());
                            }
                        }
                    }
                }
            }
        }

        appendText("  Deadlock configurations (cannot evolve internally):\n\n", STYLE_GRAY);

        int trueDeadlocks = 0;
        for (Configuration cfg : deadlocks) {
            String cfgStr = cfg.toString();
            boolean isCovered = coveredCfgStrs.contains(cfgStr);
            
            appendText("    " + cfgStr, isCovered ? STYLE_ORANGE : STYLE_RED);
            if (isCovered) {
                appendText(" - Has way out via transition\n", STYLE_ORANGE);
            } else {
                appendText(" - TRUE DEADLOCK (no way out!)\n", STYLE_RED);
                trueDeadlocks++;
            }
        }

        appendText("\n  Summary: ", STYLE_BOLD);
        if (trueDeadlocks == 0) {
            appendText("No true deadlocks", STYLE_GREEN);
            appendText(" (all potential deadlocks are covered by transitions)\n\n", STYLE_GRAY);
        } else {
            appendText(trueDeadlocks + " TRUE DEADLOCK(S)", STYLE_RED);
            appendText(" requiring attention\n\n", STYLE_NORMAL);
        }
    }

    private void appendOverallStatusSection() {
        appendText("┌─────────────────────────────────────────────────────────────┐\n", STYLE_SUBHEADING);
        appendText("│ OVERALL STATUS                                              │\n", STYLE_SUBHEADING);
        appendText("└─────────────────────────────────────────────────────────────┘\n", STYLE_SUBHEADING);

        boolean hasConstraintViolations = false;
        boolean hasUncoveredExitZones = false;
        boolean hasTrueDeadlocks = false;

        Semantics ss = state.getStateSemantics();
        Semantics cs = state.getConstraintsSemantics();

        // Check constraint violations
        if (!state.isPseudoState() && ss != null && cs != null) {
            Set<String> constraintStrs = new HashSet<>();
            for (Configuration cfg : cs.getConfigurations()) {
                constraintStrs.add(cfg.toString());
            }
            for (Configuration cfg : ss.getConfigurations()) {
                if (!constraintStrs.contains(cfg.toString())) {
                    hasConstraintViolations = true;
                    break;
                }
            }
        }

        // Check uncovered exit zones
        Set<ExitZone> reactiveZones = state.getReactiveSemantics();
        Set<BasicStateProposition> coveredGuards = new HashSet<>();
        if (stateMachine != null) {
            for (TransitionInterface ti : stateMachine.getTransitions()) {
                if (ti instanceof PWSTransition pt && pt.isEnabled() && !pt.isTriggerable() 
                        && pt.getSource() == state 
                        && pt.getGuardProposition() instanceof BasicStateProposition) {
                    coveredGuards.add((BasicStateProposition) pt.getGuardProposition());
                }
            }
        }
        if (reactiveZones != null) {
            for (ExitZone ez : reactiveZones) {
                if (!coveredGuards.contains(ez.getTarget())) {
                    hasUncoveredExitZones = true;
                    break;
                }
            }
        }

        // Check true deadlocks
        Set<Configuration> deadlocks = state.getDeadlockConfigurations();
        Set<String> coveredCfgStrs = new HashSet<>();
        if (stateMachine != null && ss != null && assembly != null) {
            for (TransitionInterface ti : stateMachine.getTransitions()) {
                if (ti instanceof PWSTransition pt && pt.isEnabled() && pt.getSource() == state) {
                    if (pt.getGuardProposition() != null) {
                        for (Configuration cfg : ss.getConfigurations()) {
                            if (pt.getGuardProposition().evaluateConfiguration(cfg, assembly)) {
                                coveredCfgStrs.add(cfg.toString());
                            }
                        }
                    }
                }
            }
        }
        if (deadlocks != null) {
            for (Configuration cfg : deadlocks) {
                if (!coveredCfgStrs.contains(cfg.toString())) {
                    hasTrueDeadlocks = true;
                    break;
                }
            }
        }

        // Report status
        if (!hasConstraintViolations && !hasUncoveredExitZones && !hasTrueDeadlocks) {
            appendText("  ✓ STATE IS WELL-FORMED\n\n", STYLE_GREEN);
            appendText("    • All computed configurations satisfy constraints\n", STYLE_GREEN);
            appendText("    • All exit zones are covered by autonomous transitions\n", STYLE_GREEN);
            appendText("    • No true deadlock configurations\n", STYLE_GREEN);
        } else {
            appendText("  ⚠ STATE HAS ISSUES\n\n", STYLE_RED);
            if (hasConstraintViolations) {
                appendText("    ✗ Some configurations violate constraints\n", STYLE_RED);
            }
            if (hasUncoveredExitZones) {
                appendText("    ✗ Some exit zones are not covered by transitions\n", STYLE_RED);
            }
            if (hasTrueDeadlocks) {
                appendText("    ✗ True deadlock configurations exist\n", STYLE_RED);
            }
        }
        appendText("\n", STYLE_NORMAL);
    }

    private void appendText(String text, String styleName) {
        StyledDocument doc = textPane.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), text, doc.getStyle(styleName));
        } catch (BadLocationException e) {
            // ignore
        }
    }
}
