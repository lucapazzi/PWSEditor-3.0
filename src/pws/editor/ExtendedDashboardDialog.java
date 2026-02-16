package pws.editor;

import pws.PWSState;
import pws.PWSStateMachine;
import pws.PWSTransition;
import pws.editor.semantics.Configuration;
import pws.editor.semantics.ExitZone;
import pws.editor.semantics.Semantics;
import assembly.Assembly;
import machinery.StateMachine;
import machinery.Transition;
import machinery.TransitionInterface;
import smalgebra.BasicStateProposition;
import pws.editor.annotation.StateSemanticsAnnotation;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.util.*;

/**
 * Extended dashboard dialog that shows detailed semantics information for a state.
 * Provides comprehensive analysis including constraint semantics, computed semantics,
 * exit zones with origin information, coverage status, and deadlock analysis.
 */
@SuppressWarnings("this-escape")
public class ExtendedDashboardDialog extends JDialog {
    private static final long serialVersionUID = 1L;

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
    private static final String STYLE_ORANGE_UNDERLINE = "orangeUnderline";
    private static final String STYLE_GRAY = "gray";
    private static final String STYLE_BOLD = "bold";
    private static final String STYLE_GREEN_UNDERLINE = "greenUnderline";
    private static final String STYLE_RED_UNDERLINE = "redUnderline";

    private static class OneStepEvolution {
        private final String machineId;
        private final String machineName;
        private final Transition transition;
        private final Configuration nextConfig;
        private final java.util.List<PWSTransition> coveredTransitions;

        private OneStepEvolution(String machineId,
                                 String machineName,
                                 Transition transition,
                                 Configuration nextConfig,
                                 java.util.List<PWSTransition> coveredTransitions) {
            this.machineId = machineId;
            this.machineName = machineName;
            this.transition = transition;
            this.nextConfig = nextConfig;
            this.coveredTransitions = coveredTransitions != null ? coveredTransitions : Collections.emptyList();
        }
    }

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

        // Orange underline style (component deadlock marker)
        Style orangeUnderline = doc.addStyle(STYLE_ORANGE_UNDERLINE, normal);
        StyleConstants.setForeground(orangeUnderline, new Color(204, 102, 0));
        StyleConstants.setUnderline(orangeUnderline, true);
        
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
        
        // Red underline style (constraint-violating config with evolution)
        Style redUnderline = doc.addStyle(STYLE_RED_UNDERLINE, normal);
        StyleConstants.setForeground(redUnderline, new Color(180, 0, 0));
        StyleConstants.setUnderline(redUnderline, true);
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

        // Section 4: Orphan Exit Zones
        appendOrphanExitZonesSection();
        
        // Section 5: Deadlock Analysis
        appendDeadlockAnalysisSection();
        
        // Section 6: Overall Status
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
        if (raw != null && !raw.isBlank() && "ANY".equalsIgnoreCase(raw.trim())) {
            appendText("  Explicit constraint:\n", STYLE_GRAY);
            appendText("    ANY (all configurations allowed)\n\n", STYLE_BLUE);
            appendText("  Note: new states are created with explicit ANY by default.\n\n", STYLE_GRAY);
            return;
        }
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
            appendText("  No explicit constraints found; treated as ANY (all configurations allowed)\n", STYLE_GRAY);
        }
        appendText("\n", STYLE_NORMAL);
    }

    private void appendComputedSemanticsSection() {
        appendText("┌─────────────────────────────────────────────────────────────┐\n", STYLE_SUBHEADING);
        appendText("│ COMPUTED SEMANTICS (State Semantics)                        │\n", STYLE_SUBHEADING);
        appendText("└─────────────────────────────────────────────────────────────┘\n", STYLE_SUBHEADING);

        Semantics ss = state.getStateSemantics();
        Semantics cs = state.getConstraintsSemantics();
        String rawConstraint = state.getRawConstraintText();
        boolean hasRaw = rawConstraint != null && !rawConstraint.isBlank();
        boolean rawAny = hasRaw && "ANY".equalsIgnoreCase(rawConstraint.trim());
        boolean hasCs = cs != null && !cs.getConfigurations().isEmpty();
        boolean anyConstraint = state.isPseudoState() || rawAny || (!hasRaw && !hasCs);
        
        // Get covered configurations from outgoing transitions
        Set<String> coveredCfgStrs = new HashSet<>();
        Map<String, java.util.List<PWSTransition>> coveredByTransition = new HashMap<>();
        if (ss != null && stateMachine != null && assembly != null) {
            for (Configuration cfg : ss.getConfigurations()) {
                java.util.List<PWSTransition> covering = findCoveringTransitions(cfg);
                if (!covering.isEmpty()) {
                    String cfgStr = cfg.toString();
                    coveredCfgStrs.add(cfgStr);
                    coveredByTransition.put(cfgStr, covering);
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

        if (ss != null && !ss.getConfigurations().isEmpty()) {
            appendText("  Configurations (shown as in dashboard):\n", STYLE_GRAY);
            appendText("    Legend:\n", STYLE_GRAY);
            appendText("      Text color:\n", STYLE_GRAY);
            appendText("        GRAY", STYLE_GRAY);
            appendText(" = no machines\n", STYLE_GRAY);
            appendText("        ", STYLE_GRAY);
            appendText("GREEN", STYLE_GREEN);
            appendText(" text = satisfies constraints\n", STYLE_GRAY);
            appendText("        ", STYLE_GRAY);
            appendText("RED", STYLE_RED);
            appendText(" text = violates constraints\n", STYLE_GRAY);
            appendText("      Underline (internal-evolution status):\n", STYLE_GRAY);
            appendText("        ", STYLE_GRAY);
            appendText("GREEN underline", STYLE_GREEN_UNDERLINE);
            appendText(" = can evolve internally\n", STYLE_GRAY);
            appendText("        ", STYLE_GRAY);
            appendText("YELLOW underline", STYLE_ORANGE_UNDERLINE);
            appendText(" = internally stuck due to component deadlock (listed below)\n", STYLE_GRAY);
            appendText("        ", STYLE_GRAY);
            appendText("RED underline", STYLE_RED_UNDERLINE);
            appendText(" = true deadlock (internally stuck and not covered by outgoing PWS transitions)\n", STYLE_GRAY);
            appendText("        NO underline = internally stuck, but at least one enabled outgoing PWS transition can still fire\n", STYLE_GRAY);
            appendText("          (there is still an exit path, so this is not a true deadlock)\n", STYLE_GRAY);
            appendText("    Note: true deadlocks are explicitly labeled below and appear with a red underline in the dashboard.\n\n", STYLE_GRAY);
            
            for (Configuration cfg : ss.getConfigurations()) {
                String cfgStr = cfg.toString();
                
                // Special case: empty configuration "()" means no component machines configured
                boolean isEmptyConfig = cfgStr.equals("()");
                
                boolean satisfiesConstraint = anyConstraint
                        || (cs != null && cfg.implies(cs));
                boolean isCovered = coveredCfgStrs.contains(cfgStr);
                boolean isDeadlock = deadlockStrs.contains(cfgStr);
                boolean canEvolve = !isDeadlock && !isEmptyConfig; // Empty config can't evolve
                boolean isTrueDeadlock = isDeadlock && !isCovered;
                java.util.List<String> componentDeadlocks = findComponentDeadlocks(cfg);
                
                // Determine style matching the dashboard:
                // Text color = constraint satisfaction (green/red).
                // Underline = internal evolution status:
                //   - Underline: can evolve internally
                //   - No underline: internally stuck (covered or true deadlock)
                String style;
                String indicator;
                
                if (isEmptyConfig) {
                    // EMPTY CONFIG: Gray - means no component machines configured
                    style = STYLE_GRAY;
                    indicator = " ○";
                } else if (canEvolve) {
                    // CAN EVOLVE: underline reflects internal evolution
                    style = satisfiesConstraint ? STYLE_GREEN_UNDERLINE : STYLE_RED_UNDERLINE;
                    indicator = " ↻";
                } else {
                    // Internally stuck (covered or true deadlock)
                    style = satisfiesConstraint ? STYLE_GREEN : STYLE_RED;
                    if (isTrueDeadlock) {
                        indicator = " ⛔";
                    } else {
                        indicator = " ✓";
                    }
                }
                
                appendText("    ", STYLE_NORMAL);
                appendText(cfgStr, style);
                appendText(indicator + "\n", STYLE_GRAY);

                java.util.List<PWSTransition> coveringTransitions = coveredByTransition.getOrDefault(cfgStr, Collections.emptyList());
                appendConfigurationDetails(cfg,
                        isEmptyConfig,
                        satisfiesConstraint,
                        canEvolve,
                        isTrueDeadlock,
                        coveringTransitions,
                        componentDeadlocks);
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
        Set<ExitZone> overflowZones = state.getIncomingTransitionOverflowExitZones();
        boolean coverageRequired = !state.isFailState();
        if (!coverageRequired) {
            appendText("  Note: fail state — exit-zone coverage is not required.\n\n", STYLE_ORANGE);
        }

        // Compute covered exit zones
        Set<BasicStateProposition> coveredGuards = new HashSet<>();
        if (coverageRequired && stateMachine != null) {
            for (TransitionInterface ti : stateMachine.getTransitions()) {
                if (ti instanceof PWSTransition pt && pt.isEnabled() && !pt.isTriggerable() 
                        && pt.getSource() == state 
                        && pt.getGuardProposition() instanceof BasicStateProposition) {
                    coveredGuards.add((BasicStateProposition) pt.getGuardProposition());
                }
            }
        }

        Semantics ss = state.getStateSemantics();

        if (reactiveZones == null || reactiveZones.isEmpty()) {
            appendText("  No exit zones detected for this state.\n", STYLE_GRAY);
            appendText("  (No enabled autonomous component transitions from current semantics.)\n\n", STYLE_GRAY);
            return;
        }

        appendText("  Exit zones (autonomous + incoming transition overflow markers):\n", STYLE_GRAY);
        appendText("  Legend: ", STYLE_GRAY);
        appendText("GRAY", STYLE_GRAY);
        appendText(" = internal (target already in semantics), ", STYLE_GRAY);
        if (coverageRequired) {
            appendText("GREEN", STYLE_GREEN);
            appendText(" = covered (handled by PWS transition), ", STYLE_GRAY);
            appendText("RED", STYLE_RED);
            appendText(" = uncovered or orphan (needs attention), ", STYLE_GRAY);
            appendText("overflow|m.s", STYLE_ORANGE);
            appendText(" = incoming transition codomain overflow marker\n\n", STYLE_GRAY);
        } else {
            appendText("ORANGE", STYLE_ORANGE);
            appendText(" = coverage not required (fail state), ", STYLE_GRAY);
            appendText("RED", STYLE_RED);
            appendText(" = orphan (needs attention), ", STYLE_GRAY);
            appendText("overflow|m.s", STYLE_ORANGE);
            appendText(" = incoming transition codomain overflow marker\n\n", STYLE_GRAY);
        }

        int internalCount = 0;
        int coveredCount = 0;
        int uncoveredCount = 0;
        int notRequiredCount = 0;
        int orphanCount = 0;
        int csOnlyCount = 0;
        int ssOnlyCount = 0;
        int overflowCount = 0;
        int bothCount = 0;

        for (ExitZone ez : reactiveZones) {
            boolean isOrphan = ez.isOrphanSource(assembly);
            boolean isInternal = false;
            if (ss != null && assembly != null && ez.getTarget() != null) {
                Semantics targetAndSem = ez.getTarget().toSemantics(assembly).AND(ss);
                isInternal = !targetAndSem.ISEMPTY();
            }
            boolean isCovered = coverageRequired && !isOrphan && !isInternal && coveredGuards.contains(ez.getTarget());
            boolean isCsOnly = csOnlyZones != null && csOnlyZones.contains(ez);
            boolean isSsOnly = ssOnlyZones != null && ssOnlyZones.contains(ez);
            boolean isOverflow = overflowZones != null && overflowZones.contains(ez);
            
            // Determine origin
            String origin;
            if (isCsOnly) {
                origin = "CS-only (from Constraint Semantics)";
                csOnlyCount++;
            } else if (isOverflow) {
                origin = "Incoming transition codomain overflow (outside destination constraints)";
                overflowCount++;
            } else if (isSsOnly) {
                origin = "SS-only (from State Semantics)";
                ssOnlyCount++;
            } else {
                origin = "Both CS and SS";
                bothCount++;
            }

            appendText("    Exit Zone: ", STYLE_BOLD);
            String exitZoneLabel = isOverflow ? formatOverflowLabel(ez) : ez.toString();
            if (isOrphan) {
                appendText(exitZoneLabel + "\n", STYLE_RED);
            } else if (isInternal) {
                appendText(exitZoneLabel + "\n", STYLE_GRAY);
            } else if (!coverageRequired) {
                appendText(exitZoneLabel + "\n", STYLE_ORANGE);
            } else {
                appendText(exitZoneLabel + "\n", isCovered ? STYLE_GREEN : STYLE_RED);
            }
            
            appendText("      Machine:     ", STYLE_GRAY);
            appendText(ez.getStateMachineId() + "\n", STYLE_NORMAL);
            
            appendText("      Transition:  ", STYLE_GRAY);
            if (ez.getTransition() == null && isOverflow) {
                appendText("incoming transition codomain overflow\n", STYLE_NORMAL);
            } else if (ez.getSource() != null && ez.getTarget() != null) {
                appendText(ez.getSource().getStateName() + " → " + ez.getTarget().getStateName() + "\n", STYLE_NORMAL);
            } else {
                appendText("(unknown)\n", STYLE_NORMAL);
            }

            appendText("      Source cfg:  ", STYLE_GRAY);
            if (ez.getSource() != null) {
                appendText("(" + ez.getSource().toString() + ")\n", STYLE_NORMAL);
            } else {
                appendText("(unknown)\n", STYLE_NORMAL);
            }

            appendText("      Target cfg:  ", STYLE_GRAY);
            if (ez.getTarget() != null) {
                appendText("(" + ez.getTarget().toString() + ")\n", STYLE_NORMAL);
            } else {
                appendText("(unknown)\n", STYLE_NORMAL);
            }

            appendText("      Expected guard: ", STYLE_GRAY);
            if (ez.getTarget() != null) {
                appendText("[" + ez.getTarget().toString() + "]\n", STYLE_NORMAL);
            } else {
                appendText("(unknown)\n", STYLE_NORMAL);
            }
            
            appendText("      Origin:      ", STYLE_GRAY);
            if (isCsOnly) {
                appendText(origin + "\n", STYLE_BLUE);
            } else if (isOverflow) {
                appendText(origin + "\n", STYLE_ORANGE);
            } else if (isSsOnly) {
                appendText(origin + "\n", STYLE_ORANGE);
            } else {
                appendText(origin + "\n", STYLE_NORMAL);
            }
            
            appendText("      Status:      ", STYLE_GRAY);
            if (isOrphan) {
                appendText("orphan exit zone — no matching source state\n", STYLE_RED);
            } else if (isOverflow && !coverageRequired) {
                appendText("incoming-transition overflow marker; coverage not required for fail state\n", STYLE_ORANGE);
            } else if (isOverflow && isCovered) {
                appendText("incoming-transition overflow marker covered by autonomous PWS transition\n", STYLE_GREEN);
            } else if (isOverflow) {
                appendText("incoming-transition overflow marker uncovered — needs PWS autonomous guard [" +
                          ez.getTarget().toString() + "]\n", STYLE_RED);
            } else if (isInternal) {
                appendText("internal (target already in semantics)\n", STYLE_GRAY);
            } else if (!coverageRequired) {
                appendText("coverage not required for fail state\n", STYLE_ORANGE);
            } else if (isCovered) {
                appendText("covered by autonomous PWS transition\n", STYLE_GREEN);
            } else {
                appendText("uncovered — needs PWS transition with guard [" +
                          ez.getTarget().toString() + "]\n", STYLE_RED);
            }

            java.util.List<PWSTransition> autonomousCovering = findAutonomousCoveringTransitionsForExitZone(ez);
            appendText("      Covering autonomous transitions: ", STYLE_GRAY);
            if (autonomousCovering.isEmpty()) {
                if (!coverageRequired) {
                    appendText("(not required for fail state)\n", STYLE_ORANGE);
                } else {
                    appendText("(none)\n", STYLE_RED);
                }
            } else {
                appendText(formatPwsTransitions(autonomousCovering) + "\n", STYLE_GREEN);
            }

            if (isOverflow) {
                java.util.List<PWSTransition> producers = findIncomingOverflowProducers(ez);
                appendText("      Incoming producer transitions: ", STYLE_GRAY);
                if (producers.isEmpty()) {
                    appendText("(none detected)\n", STYLE_GRAY);
                } else {
                    appendText(formatPwsTransitions(producers) + "\n", STYLE_NORMAL);
                }
                appendText("      Effect on semantics: ", STYLE_GRAY);
                appendText("excluded from state semantics (outside constraints)\n", STYLE_ORANGE);
            }
            appendText("\n", STYLE_NORMAL);

            if (isInternal) {
                internalCount++;
            } else if (isOrphan) {
                uncoveredCount++;
                orphanCount++;
            } else if (!coverageRequired) {
                notRequiredCount++;
            } else if (isCovered) {
                coveredCount++;
            } else {
                uncoveredCount++;
            }
        }

        // Summary
        appendText("  Summary: ", STYLE_BOLD);
        appendText(internalCount + " internal", STYLE_GRAY);
        appendText(", ", STYLE_NORMAL);
        if (coverageRequired) {
            appendText(coveredCount + " covered", STYLE_GREEN);
            appendText(", ", STYLE_NORMAL);
            appendText(uncoveredCount + " uncovered", uncoveredCount > 0 ? STYLE_RED : STYLE_GREEN);
        } else {
            appendText(notRequiredCount + " coverage not required", STYLE_ORANGE);
            if (uncoveredCount > 0) {
                appendText(", ", STYLE_NORMAL);
                appendText(uncoveredCount + " orphan", STYLE_RED);
            }
        }
        appendText(" (total: " + reactiveZones.size() + ")\n\n", STYLE_GRAY);
        appendText("  Breakdown: ", STYLE_BOLD);
        appendText(csOnlyCount + " CS-only, ", STYLE_BLUE);
        appendText(ssOnlyCount + " SS-only, ", STYLE_ORANGE);
        appendText(overflowCount + " incoming-overflow, ", STYLE_ORANGE);
        appendText(bothCount + " both CS+SS, ", STYLE_GRAY);
        appendText(orphanCount + " orphan\n\n", orphanCount > 0 ? STYLE_RED : STYLE_GREEN);
    }

    private void appendOrphanExitZonesSection() {
        appendText("┌─────────────────────────────────────────────────────────────┐\n", STYLE_SUBHEADING);
        appendText("│ ORPHAN EXIT ZONES                                           │\n", STYLE_SUBHEADING);
        appendText("└─────────────────────────────────────────────────────────────┘\n", STYLE_SUBHEADING);

        Set<ExitZone> reactiveZones = state.getReactiveSemantics();
        if (reactiveZones == null || reactiveZones.isEmpty()) {
            appendText("  No exit zones detected for this state.\n\n", STYLE_GRAY);
            return;
        }

        java.util.List<ExitZone> orphans = new ArrayList<>();
        for (ExitZone ez : reactiveZones) {
            if (ez.isOrphanSource(assembly)) {
                orphans.add(ez);
            }
        }

        if (orphans.isEmpty()) {
            appendText("  No orphan exit zones detected.\n\n", STYLE_GREEN);
            return;
        }

        appendText("  The following exit zones reference a missing source state:\n\n", STYLE_GRAY);
        for (ExitZone ez : orphans) {
            appendText("    ✗ ", STYLE_RED);
            appendText("Orphan exit zone — no matching source state\n", STYLE_RED);
            appendText("      Machine:     ", STYLE_GRAY);
            appendText(ez.getStateMachineId() + "\n", STYLE_NORMAL);
            appendText("      Transition:  ", STYLE_GRAY);
            if (ez.getSource() != null && ez.getTarget() != null) {
                appendText(ez.getSource().getStateName() + " → " + ez.getTarget().getStateName() + "\n", STYLE_NORMAL);
            } else {
                appendText("(unknown)\n", STYLE_NORMAL);
            }
            appendText("      Source cfg:  ", STYLE_GRAY);
            appendText((ez.getSource() != null ? "(" + ez.getSource().toString() + ")\n" : "(unknown)\n"), STYLE_NORMAL);
            appendText("      Target cfg:  ", STYLE_GRAY);
            appendText((ez.getTarget() != null ? "(" + ez.getTarget().toString() + ")\n" : "(unknown)\n"), STYLE_NORMAL);
            appendText("\n", STYLE_NORMAL);
        }
    }

    private void appendDeadlockAnalysisSection() {
        appendText("┌─────────────────────────────────────────────────────────────┐\n", STYLE_SUBHEADING);
        appendText("│ DEADLOCK ANALYSIS                                           │\n", STYLE_SUBHEADING);
        appendText("└─────────────────────────────────────────────────────────────┘\n", STYLE_SUBHEADING);

        Set<Configuration> deadlocks = state.getDeadlockConfigurations();
        
        if (deadlocks == null || deadlocks.isEmpty()) {
            appendText("  No internally stuck configurations detected.\n", STYLE_GREEN);
            appendText("  All configurations can evolve internally.\n\n", STYLE_GRAY);
            return;
        }

        // Get covered configurations
        Set<String> coveredCfgStrs = new HashSet<>();
        Semantics ss = state.getStateSemantics();
        if (stateMachine != null && ss != null) {
            for (Configuration cfg : ss.getConfigurations()) {
                if (!findCoveringTransitions(cfg).isEmpty()) {
                    coveredCfgStrs.add(cfg.toString());
                }
            }
        }

        appendText("  Internally stuck configurations (cannot evolve internally):\n\n", STYLE_GRAY);

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

        // Report status
        java.util.List<String> issues = (state != null && stateMachine != null)
                ? StateSemanticsAnnotation.computeStatusIssues(state, stateMachine)
                : Collections.emptyList();
        if (issues.isEmpty()) {
            appendText("  ✓ STATE IS WELL-FORMED\n\n", STYLE_GREEN);
            appendText("    • All computed configurations satisfy constraints\n", STYLE_GREEN);
            if (state.isFailState()) {
                appendText("    • Fail state: exit-zone coverage not required\n", STYLE_ORANGE);
            } else {
                appendText("    • All exit zones are covered by autonomous transitions\n", STYLE_GREEN);
            }
            appendText("    • No true deadlock configurations\n", STYLE_GREEN);
        } else {
            appendText("  ⚠ STATE HAS ISSUES\n\n", STYLE_RED);
            for (String issue : issues) {
                appendText("    ✗ " + issue + "\n", STYLE_RED);
            }
        }
        appendText("\n", STYLE_NORMAL);
    }

    private java.util.List<PWSTransition> findCoveringTransitions(Configuration cfg) {
        java.util.List<PWSTransition> covering = new ArrayList<>();
        if (cfg == null || stateMachine == null || assembly == null) {
            return covering;
        }
        for (TransitionInterface ti : stateMachine.getTransitions()) {
            if (ti instanceof PWSTransition pt && pt.isEnabled() && pt.getSource() == state) {
                if (stateMachine.transitionCoversConfiguration(pt, cfg)) {
                    covering.add(pt);
                }
            }
        }
        return covering;
    }

    private java.util.List<PWSTransition> findAutonomousCoveringTransitionsForExitZone(ExitZone ez) {
        java.util.List<PWSTransition> covering = new ArrayList<>();
        if (ez == null || ez.getTarget() == null || stateMachine == null) {
            return covering;
        }
        for (TransitionInterface ti : stateMachine.getTransitions()) {
            if (!(ti instanceof PWSTransition pt) || !pt.isEnabled() || pt.getSource() != state || pt.isTriggerable()) {
                continue;
            }
            if (pt.getGuardProposition() instanceof BasicStateProposition bsp && bsp.equals(ez.getTarget())) {
                covering.add(pt);
            }
        }
        return covering;
    }

    private java.util.List<PWSTransition> findIncomingOverflowProducers(ExitZone ez) {
        java.util.List<PWSTransition> producers = new ArrayList<>();
        if (ez == null || ez.getTarget() == null || stateMachine == null || assembly == null || state == null) {
            return producers;
        }
        Semantics cs = state.getConstraintsSemantics();
        if (cs == null) {
            return producers;
        }
        Semantics csComplement;
        Semantics targetSem;
        try {
            csComplement = cs.NOT(assembly);
            targetSem = ez.getTarget().toSemantics(assembly);
        } catch (Exception ex) {
            return producers;
        }
        if (targetSem == null || targetSem.ISEMPTY()) {
            return producers;
        }

        Set<PWSTransition> ordered = new LinkedHashSet<>();
        for (TransitionInterface ti : stateMachine.getTransitions()) {
            if (!(ti instanceof PWSTransition pt) || !pt.isEnabled() || pt.getTarget() != state) {
                continue;
            }
            if (!(pt.getSource() instanceof PWSState srcState)) {
                continue;
            }
            Semantics srcSem = srcState.getStateSemantics();
            if (srcSem == null || srcSem.ISEMPTY()) {
                continue;
            }
            Semantics contribution = stateMachine.computeTransitionContribution(pt, srcSem);
            if (contribution == null || contribution.ISEMPTY()) {
                continue;
            }
            Semantics overflow = contribution.AND(csComplement);
            if (overflow == null || overflow.ISEMPTY()) {
                continue;
            }
            Semantics hitsTarget = overflow.AND(targetSem);
            if (hitsTarget != null && !hitsTarget.ISEMPTY()) {
                ordered.add(pt);
            }
        }
        producers.addAll(ordered);
        return producers;
    }

    private java.util.List<String> findComponentDeadlocks(Configuration cfg) {
        java.util.List<String> results = new ArrayList<>();
        if (cfg == null || assembly == null) {
            return results;
        }
        for (smalgebra.BasicStateProposition bsp : cfg.getBasicStatePropositions()) {
            if (bsp == null) continue;
            String machineId = bsp.getMachineId();
            String stateName = bsp.getStateName();
            if (machineId == null || stateName == null) continue;
            StateMachine machine = assembly.getStateMachines().get(machineId);
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
            for (TransitionInterface ti : machine.getTransitions()) {
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

    private boolean isTransitionEnabled(TransitionInterface t) {
        if (t instanceof machinery.Transition trans) {
            return trans.isEnabled();
        }
        return true;
    }

    private OneStepEvolution findOneStepEvolution(Configuration cfg) {
        if (cfg == null || assembly == null) {
            return null;
        }
        Map<String, StateMachine> machines = assembly.getStateMachines();
        if (machines == null || machines.isEmpty()) {
            return null;
        }
        java.util.List<String> machineIds = new ArrayList<>(machines.keySet());
        Collections.sort(machineIds);
        OneStepEvolution first = null;
        for (String machineId : machineIds) {
            StateMachine machine = machines.get(machineId);
            if (machine == null) continue;
            String currentStateName = cfg.getStateName(machineId);
            if (currentStateName == null) continue;
            for (TransitionInterface ti : machine.getTransitions()) {
                if (!(ti instanceof Transition t)) continue;
                if (!t.isEnabled() || !t.isAutonomous()) continue;
                if (t.getSource() == null || t.getTarget() == null) continue;
                if (!currentStateName.equals(t.getSource().getName())) continue;
                String targetStateName = t.getTarget().getName();
                Configuration nextCfg = cfg.replaceConstraint(machineId, targetStateName);
                if (nextCfg == null || nextCfg.equals(cfg)) continue;
                java.util.List<PWSTransition> covering = findCoveringTransitions(nextCfg);
                String machineName = machine.getName();
                OneStepEvolution candidate = new OneStepEvolution(machineId, machineName, t, nextCfg, covering);
                if (covering != null && !covering.isEmpty()) {
                    return candidate;
                }
                if (first == null) {
                    first = candidate;
                }
            }
        }
        return first;
    }

    private void appendConfigurationDetails(Configuration cfg,
                                            boolean isEmptyConfig,
                                            boolean satisfiesConstraint,
                                            boolean canEvolve,
                                            boolean isTrueDeadlock,
                                            java.util.List<PWSTransition> coveringTransitions,
                                            java.util.List<String> componentDeadlocks) {
        if (cfg == null) return;
        appendText("      1. ", STYLE_GRAY);
        if (isEmptyConfig) {
            appendText("No component machines configured.\n", STYLE_GRAY);
        } else if (satisfiesConstraint) {
            appendText("Satisfies constraints.\n", STYLE_GREEN);
        } else {
            appendText("Violates constraints.\n", STYLE_RED);
        }

        appendText("      2. ", STYLE_GRAY);
        if (isEmptyConfig) {
            appendText("Cannot evolve internally (no component machines configured).\n", STYLE_GRAY);
        } else if (canEvolve) {
            appendText("Can evolve internally by\n", STYLE_GRAY);
            OneStepEvolution evo = findOneStepEvolution(cfg);
            if (evo != null) {
                String exitZone = evo.machineId + "." + evo.transition.getTarget().getName();
                appendText("         one-step internal evolution: ", STYLE_GRAY);
                appendText(formatMachineLabel(evo.machineId, evo.machineName) + ": " + evo.transition.getSource().getName()
                        + " → " + evo.transition.getTarget().getName(), STYLE_NORMAL);
                appendText(" => ", STYLE_GRAY);
                appendText(evo.nextConfig.toString(), STYLE_NORMAL);
                appendText(" into exit zone " + exitZone + "\n", STYLE_GRAY);
            } else {
                appendText("         one-step internal evolution details unavailable.\n", STYLE_GRAY);
            }
        } else {
            appendText("Cannot evolve internally (no enabled autonomous transitions).\n", STYLE_GRAY);
        }

        appendText("      3. ", STYLE_GRAY);
        if (coveringTransitions != null && !coveringTransitions.isEmpty()) {
            appendText("Covered by PWS transition: ", STYLE_GRAY);
            appendText(formatPwsTransitions(coveringTransitions) + "\n", STYLE_GREEN);
        } else if (isTrueDeadlock) {
            appendText("Not covered by any PWS transition (true deadlock).\n", STYLE_RED);
        } else {
            appendText("Not covered by any PWS transition.\n", STYLE_GRAY);
        }

        if (componentDeadlocks != null && !componentDeadlocks.isEmpty()) {
            appendText("      4. ", STYLE_GRAY);
            appendText("Component deadlock: ", STYLE_GRAY);
            appendText(String.join(", ", componentDeadlocks) + "\n", STYLE_ORANGE);
        }
    }

    private String formatPwsTransitions(java.util.List<PWSTransition> transitions) {
        if (transitions == null || transitions.isEmpty()) {
            return "(none)";
        }
        java.util.List<String> parts = new ArrayList<>();
        for (PWSTransition pt : transitions) {
            String src = pt.getSource() != null ? pt.getSource().getName() : "?";
            String tgt = pt.getTarget() != null ? pt.getTarget().getName() : "?";
            String label = src + " → " + tgt;
            if (pt.isTriggerable()) {
                String trigger = pt.getTriggerEvent();
                label += " [" + (trigger == null || trigger.isBlank() ? "trigger" : trigger) + "]";
            } else if (pt.isInitialTransition()) {
                label += " [initial]";
            } else {
                String guard = pt.getGuardProposition() != null ? pt.getGuardProposition().toString() : "null";
                if (guard == null || guard.isBlank()) {
                    guard = "null";
                }
                label += " [autonomous, guard=" + guard + "]";
            }
            parts.add(label);
        }
        return String.join(", ", parts);
    }

    private String formatMachineLabel(String machineId, String machineName) {
        if (machineName == null || machineName.isBlank()) {
            return machineId != null ? machineId : "?";
        }
        if (machineId == null || machineId.isBlank()) {
            return machineName;
        }
        return machineId + " (" + machineName + ")";
    }

    private static String formatOverflowLabel(ExitZone ez) {
        return "overflow|" + formatOverflowTarget(ez != null ? ez.getTarget() : null);
    }

    private static String formatOverflowTarget(BasicStateProposition target) {
        if (target == null) {
            return "?";
        }
        String machineId = target.getMachineId();
        String stateName = target.getStateName();
        if (machineId == null || machineId.isBlank()) {
            return (stateName == null || stateName.isBlank()) ? "?" : stateName;
        }
        if (stateName == null || stateName.isBlank()) {
            return machineId;
        }
        return machineId + "." + stateName;
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
