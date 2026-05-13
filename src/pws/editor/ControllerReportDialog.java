package pws.editor;

import assembly.Assembly;
import machinery.StateInterface;
import machinery.TransitionInterface;
import pws.PWSState;
import pws.PWSStateMachine;
import pws.PWSTransition;
import pws.editor.annotation.StateSemanticsAnnotation;
import pws.editor.semantics.Configuration;
import pws.editor.semantics.ExitZone;
import pws.editor.semantics.Semantics;
import smalgebra.BasicStateProposition;
import smalgebra.FalseProposition;
import smalgebra.SMProposition;
import smalgebra.TrueProposition;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Dialog that displays a comprehensive report of all issues in the controller.
 * Enumerates problems like uncovered exit zones, orphan guards, problematic guards,
 * constraint violations, and deadlock configurations.
 * 
 * Each issue category corresponds to red visual indicators on the diagram.
 * Future: Will also report LTL formula satisfaction.
 */
@SuppressWarnings("this-escape")
public class ControllerReportDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    
    // Text styles
    private static final String STYLE_TITLE = "title";
    private static final String STYLE_SECTION = "section";
    private static final String STYLE_SUBSECTION = "subsection";
    private static final String STYLE_NORMAL = "normal";
    private static final String STYLE_BOLD = "bold";
    private static final String STYLE_GREEN = "green";
    private static final String STYLE_RED = "red";
    private static final String STYLE_ORANGE = "orange";
    private static final String STYLE_GRAY = "gray";
    private static final String STYLE_CODE = "code";
    
    private final PWSStateMachine stateMachine;
    private final Assembly assembly;
    private final JTextPane textPane;
    
    // Problem counts
    private int totalProblems = 0;
    private int guardProblems = 0;
    private int actionProblems = 0;
    private int exitZoneProblems = 0;
    private int orphanExitZoneProblems = 0;
    private int constraintProblems = 0;
    private int primaryDeadlockProblems = 0;
    private int secondaryDeadlockProblems = 0;
    private int unreachableProblems = 0;
    
    public ControllerReportDialog(Frame owner, PWSStateMachine stateMachine) {
        super(owner, "Controller Report", true);
        this.stateMachine = stateMachine;
        this.assembly = stateMachine.getAssembly();
        
        setSize(750, 650);
        setLocationRelativeTo(owner);
        
        // Create text pane with styles
        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setBackground(new Color(252, 252, 252));
        setupStyles();
        
        // Build the report
        buildReport();
        
        // Scroll to top
        textPane.setCaretPosition(0);
        
        // Layout
        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(closeButton);
        
        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    private void setupStyles() {
        StyledDocument doc = textPane.getStyledDocument();
        
        // Title style
        Style style = doc.addStyle(STYLE_TITLE, null);
        StyleConstants.setFontSize(style, 18);
        StyleConstants.setBold(style, true);
        StyleConstants.setForeground(style, new Color(40, 40, 40));
        
        // Section style
        style = doc.addStyle(STYLE_SECTION, null);
        StyleConstants.setFontSize(style, 14);
        StyleConstants.setBold(style, true);
        StyleConstants.setForeground(style, new Color(0, 80, 150));
        
        // Subsection style
        style = doc.addStyle(STYLE_SUBSECTION, null);
        StyleConstants.setFontSize(style, 12);
        StyleConstants.setBold(style, true);
        StyleConstants.setForeground(style, new Color(60, 60, 60));
        
        // Normal style
        style = doc.addStyle(STYLE_NORMAL, null);
        StyleConstants.setFontSize(style, 12);
        StyleConstants.setForeground(style, Color.BLACK);
        
        // Bold style
        style = doc.addStyle(STYLE_BOLD, null);
        StyleConstants.setFontSize(style, 12);
        StyleConstants.setBold(style, true);
        StyleConstants.setForeground(style, Color.BLACK);
        
        // Green style
        style = doc.addStyle(STYLE_GREEN, null);
        StyleConstants.setFontSize(style, 12);
        StyleConstants.setForeground(style, new Color(0, 128, 0));
        
        // Red style
        style = doc.addStyle(STYLE_RED, null);
        StyleConstants.setFontSize(style, 12);
        StyleConstants.setForeground(style, new Color(180, 0, 0));
        
        // Orange style
        style = doc.addStyle(STYLE_ORANGE, null);
        StyleConstants.setFontSize(style, 12);
        StyleConstants.setForeground(style, new Color(200, 100, 0));
        
        // Gray style
        style = doc.addStyle(STYLE_GRAY, null);
        StyleConstants.setFontSize(style, 12);
        StyleConstants.setForeground(style, Color.GRAY);
        
        // Code style (monospace)
        style = doc.addStyle(STYLE_CODE, null);
        StyleConstants.setFontFamily(style, Font.MONOSPACED);
        StyleConstants.setFontSize(style, 11);
        StyleConstants.setForeground(style, new Color(80, 80, 80));
    }
    
    private void buildReport() {
        // Title
        appendText("CONTROLLER REPORT\n", STYLE_TITLE);
        appendText("Generated: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n\n", STYLE_GRAY);
        
        // Collect all problems first to count them
        List<GuardProblem> guardProblemsList = collectGuardProblems();
        List<ActionProblem> actionProblemsList = collectActionProblems();
        Map<PWSState, List<ExitZoneProblem>> exitZoneProblemMap = collectExitZoneProblems();
        Map<PWSState, List<ExitZoneProblem>> orphanExitZoneProblemMap = collectOrphanExitZoneProblems();
        Map<PWSState, List<String>> constraintProblemMap = collectConstraintProblems();
        Map<PWSState, Set<Configuration>> primaryDeadlockMap = collectPrimaryDeadlockProblems();
        Map<PWSState, Set<Configuration>> secondaryDeadlockMap = collectSecondaryDeadlockProblems();
        List<PWSState> unreachableStates = collectUnreachableStates();
        
        guardProblems = guardProblemsList.size();
        actionProblems = actionProblemsList.size();
        exitZoneProblems = exitZoneProblemMap.values().stream().mapToInt(List::size).sum();
        orphanExitZoneProblems = orphanExitZoneProblemMap.values().stream().mapToInt(List::size).sum();
        constraintProblems = constraintProblemMap.values().stream().mapToInt(List::size).sum();
        primaryDeadlockProblems = primaryDeadlockMap.values().stream().mapToInt(Set::size).sum();
        secondaryDeadlockProblems = secondaryDeadlockMap.values().stream().mapToInt(Set::size).sum();
        unreachableProblems = unreachableStates.size();
        totalProblems = guardProblems + actionProblems + exitZoneProblems + orphanExitZoneProblems
                + constraintProblems + primaryDeadlockProblems + secondaryDeadlockProblems + unreachableProblems;
        
        // Summary section
        appendSummarySection();
        
        // Detailed sections
        if (guardProblems > 0) {
            appendGuardProblemsSection(guardProblemsList);
        }
        
        if (actionProblems > 0) {
            appendActionProblemsSection(actionProblemsList);
        }
        
        if (exitZoneProblems > 0) {
            appendExitZoneProblemsSection(exitZoneProblemMap);
        }

        if (orphanExitZoneProblems > 0) {
            appendOrphanExitZoneProblemsSection(orphanExitZoneProblemMap);
        }
        
        if (constraintProblems > 0) {
            appendConstraintProblemsSection(constraintProblemMap);
        }
        
        if (primaryDeadlockProblems > 0) {
            appendPrimaryDeadlockProblemsSection(primaryDeadlockMap);
        }
        
        if (secondaryDeadlockProblems > 0) {
            appendSecondaryDeadlockProblemsSection(secondaryDeadlockMap);
        }
        
        if (unreachableProblems > 0) {
            appendUnreachableStatesSection(unreachableStates);
        }
        
        // LTL section placeholder
        appendLTLPlaceholderSection();
        
        // Final status
        appendFinalStatus();
    }
    
    private void appendSummarySection() {
        appendText("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", STYLE_GRAY);
        appendText("SUMMARY\n", STYLE_SECTION);
        appendText("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n", STYLE_GRAY);
        
        // Controller stats
        int stateCount = 0;
        int transitionCount = 0;
        int autonomousCount = 0;
        int triggeredCount = 0;
        int initialCount = 0;
        int timeoutCount = 0;
        
        for (StateInterface si : stateMachine.getStates()) {
            if (si instanceof PWSState ps && !ps.isPseudoState()) {
                stateCount++;
            }
        }
        
        for (TransitionInterface ti : stateMachine.getTransitions()) {
            if (ti instanceof PWSTransition pt && pt.isEnabled()) {
                transitionCount++;
                // Check if it's an initial transition (pseudo-state + _init)
                boolean isInitial = pt.isInitialTransition();
                
                if (isInitial) {
                    initialCount++;
                } else if (pt.isTimeoutTransition()) {
                    timeoutCount++;
                } else if (pt.isAutonomous()) {
                    // True autonomous transitions (guard-driven)
                    autonomousCount++;
                } else {
                    // Regular triggered transitions
                    triggeredCount++;
                }
            }
        }
        
        appendText("  Controller Structure:\n", STYLE_BOLD);
        appendText("    • States: " + stateCount + "\n", STYLE_NORMAL);
        appendText("    • Transitions: " + transitionCount + " (", STYLE_NORMAL);
        if (initialCount > 0) {
            appendText(initialCount + " initial", STYLE_CODE);
            if (autonomousCount > 0 || timeoutCount > 0 || triggeredCount > 0) appendText(", ", STYLE_NORMAL);
        }
        if (autonomousCount > 0) {
            appendText(autonomousCount + " autonomous", STYLE_CODE);
            if (timeoutCount > 0 || triggeredCount > 0) appendText(", ", STYLE_NORMAL);
        }
        if (timeoutCount > 0) {
            appendText(timeoutCount + " timeout", STYLE_CODE);
            if (triggeredCount > 0) appendText(", ", STYLE_NORMAL);
        }
        if (triggeredCount > 0) {
            appendText(triggeredCount + " triggered", STYLE_CODE);
        }
        if (initialCount == 0 && autonomousCount == 0 && timeoutCount == 0 && triggeredCount == 0) {
            appendText("none", STYLE_CODE);
        }
        appendText(")\n", STYLE_NORMAL);
        appendText("    • Assembly machines: " + assembly.getStateMachines().size() + "\n\n", STYLE_NORMAL);
        
        // Problem summary
        appendText("  Issues Found:\n", STYLE_BOLD);
        
        if (totalProblems == 0) {
            appendText("    ✓ No issues detected\n\n", STYLE_GREEN);
        } else {
            if (guardProblems > 0) {
                appendText("    ✗ ", STYLE_RED);
                appendText("Guard problems: " + guardProblems + "\n", STYLE_NORMAL);
            }
            if (actionProblems > 0) {
                appendText("    ✗ ", STYLE_RED);
                appendText("Action problems: " + actionProblems + "\n", STYLE_NORMAL);
            }
            if (exitZoneProblems > 0) {
                appendText("    ✗ ", STYLE_RED);
                appendText("Uncovered exit zones: " + exitZoneProblems + "\n", STYLE_NORMAL);
            }
            if (orphanExitZoneProblems > 0) {
                appendText("    ✗ ", STYLE_RED);
                appendText("Orphan exit zones: " + orphanExitZoneProblems + "\n", STYLE_NORMAL);
            }
            if (constraintProblems > 0) {
                appendText("    ✗ ", STYLE_RED);
                appendText("Constraint violations: " + constraintProblems + "\n", STYLE_NORMAL);
            }
            if (primaryDeadlockProblems > 0) {
                appendText("    ⚠ ", STYLE_ORANGE);
                appendText("Primary deadlock configurations: " + primaryDeadlockProblems + "\n", STYLE_NORMAL);
            }
            if (secondaryDeadlockProblems > 0) {
                appendText("    ⚠ ", STYLE_ORANGE);
                appendText("Secondary (internal) deadlock configurations: " + secondaryDeadlockProblems + "\n", STYLE_NORMAL);
            }
            if (unreachableProblems > 0) {
                appendText("    ✗ ", STYLE_RED);
                appendText("Unreachable states: " + unreachableProblems + "\n", STYLE_NORMAL);
            }
            appendText("\n", STYLE_NORMAL);
        }
    }
    
    // ==================== Guard Problems ====================
    
    private static class GuardProblem {
        final PWSTransition transition;
        final String sourceState;
        final String targetState;
        final String guardText;
        final String problemType;
        final String explanation;
        
        GuardProblem(PWSTransition t, String src, String tgt, String guard, String type, String explanation) {
            this.transition = t;
            this.sourceState = src;
            this.targetState = tgt;
            this.guardText = guard;
            this.problemType = type;
            this.explanation = explanation;
        }
    }
    
    private List<GuardProblem> collectGuardProblems() {
        List<GuardProblem> problems = new ArrayList<>();
        
        for (TransitionInterface ti : stateMachine.getTransitions()) {
            if (!(ti instanceof PWSTransition pt) || !pt.isEnabled()) continue;
            
            SMProposition guard = pt.getGuardProposition();
            StateInterface src = pt.getSource();
            StateInterface tgt = pt.getTarget();
            String srcName = src != null ? src.getName() : "?";
            String tgtName = tgt != null ? tgt.getName() : "?";
            String guardStr = guard != null ? guard.toString() : "null";
            
            // Check FALSE guard
            if (guard instanceof FalseProposition) {
                problems.add(new GuardProblem(pt, srcName, tgtName, guardStr,
                        "FALSE Guard (Placeholder)",
                        "This transition will never fire. Set a meaningful guard condition."));
                continue;
            }
            
            // Check orphan guard (guard references an unavailable autonomous target)
            if (pt.isAutonomous() && guard instanceof BasicStateProposition bsp) {
                if (src instanceof PWSState ps && !ps.isPseudoState()) {
                    Set<String> availableTargets = collectAutonomousGuardValidationTargets(ps);
                    if (!availableTargets.contains(bsp.toString())) {
                        problems.add(new GuardProblem(pt, srcName, tgtName, guardStr,
                                "Orphan Guard",
                                "References exit zone '" + bsp + "' which no longer exists or became internal."));
                    }
                }
            }
        }
        
        return problems;
    }
    
    private void appendGuardProblemsSection(List<GuardProblem> problems) {
        appendText("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", STYLE_GRAY);
        appendText("GUARD PROBLEMS\n", STYLE_SECTION);
        appendText("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n", STYLE_GRAY);
        
        appendText("  Guards shown in ", STYLE_NORMAL);
        appendText("red", STYLE_RED);
        appendText(" on the diagram indicate problems.\n\n", STYLE_NORMAL);
        
        for (GuardProblem gp : problems) {
            appendText("  • Transition: ", STYLE_BOLD);
            appendText(gp.sourceState + " → " + gp.targetState + "\n", STYLE_CODE);
            appendText("    Guard: ", STYLE_NORMAL);
            appendText("[" + gp.guardText + "]\n", STYLE_RED);
            appendText("    Problem: ", STYLE_NORMAL);
            appendText(gp.problemType + "\n", STYLE_ORANGE);
            appendText("    ", STYLE_NORMAL);
            appendText(gp.explanation + "\n\n", STYLE_GRAY);
        }
    }
    
    // ==================== Action Problems ====================
    
    private static class ActionProblem {
        final PWSTransition transition;
        final String sourceState;
        final String targetState;
        final String actionText;
        final String problemType;
        final String explanation;
        
        ActionProblem(PWSTransition t, String src, String tgt, String action, String type, String explanation) {
            this.transition = t;
            this.sourceState = src;
            this.targetState = tgt;
            this.actionText = action;
            this.problemType = type;
            this.explanation = explanation;
        }
    }
    
    private List<ActionProblem> collectActionProblems() {
        List<ActionProblem> problems = new ArrayList<>();
        
        for (TransitionInterface ti : stateMachine.getTransitions()) {
            if (!(ti instanceof PWSTransition pt) || !pt.isEnabled()) continue;
            
            assembly.ActionList actions = pt.getActionList();
            if (actions == null || actions.isEmpty()) continue;
            
            StateInterface src = pt.getSource();
            StateInterface tgt = pt.getTarget();
            String srcName = src != null ? src.getName() : "?";
            String tgtName = tgt != null ? tgt.getName() : "?";
            
            // Skip initial transitions
            if (pt.isInitialTransition()) continue;
            
            // Get valid actions from source state semantics
            Set<String> validActions = new HashSet<>();
            if (src instanceof PWSState ps) {
                collectValidActionsForTransition(ps, pt, validActions);
            }
            
            // If no semantics available, don't flag as orphan
            if (validActions.isEmpty()) continue;
            
            // Check each action
            for (assembly.Action a : actions) {
                String actionStr = a.toString();
                if (!validActions.contains(actionStr)) {
                    problems.add(new ActionProblem(pt, srcName, tgtName, actionStr,
                            "Orphan Action",
                            "Action '" + actionStr + "' is not reachable from source state semantics."));
                }
            }
        }
        
        return problems;
    }

    private void collectValidActionsForTransition(PWSState ps, PWSTransition pt, Set<String> validActions) {
        Semantics stateSemantics = ps.getStateSemantics();
        Semantics constraintSemantics = ps.getConstraintsSemantics();
        SMProposition guard = pt.getGuardProposition();
        if (pt.isTriggerable()) {
            if (stateSemantics != null && !stateSemantics.getConfigurations().isEmpty()) {
                Semantics guardedSource = stateSemantics;
                if (guard != null) {
                    guardedSource = guardedSource.AND(guard.toSemantics(assembly));
                }
                collectValidActionsFromSemantics(guardedSource, validActions);
                return;
            }
            // Fallback when state semantics is not available yet.
            if (constraintSemantics != null) {
                collectValidActionsFromSemantics(constraintSemantics, validActions);
            }
            return;
        }
        if (pt.isAutonomous() && guard instanceof BasicStateProposition) {
            HashSet<ExitZone> reactiveZones = ps.getReactiveSemantics();
            boolean matchedZone = false;
            if (reactiveZones != null) {
                for (ExitZone ez : reactiveZones) {
                    if (ez == null || ez.getTarget() == null || ez.getTransition() == null) {
                        continue;
                    }
                    if (!guard.equals(ez.getTarget())) {
                        continue;
                    }
                    matchedZone = true;
                    if (stateSemantics != null) {
                        Semantics transformed = stateSemantics.transformByMachineTransition(
                                ez.getStateMachineId(), ez.getTransition(), assembly);
                        collectValidActionsFromSemantics(transformed, validActions);
                    }
                    if (constraintSemantics != null) {
                        Semantics transformed = constraintSemantics.transformByMachineTransition(
                                ez.getStateMachineId(), ez.getTransition(), assembly);
                        collectValidActionsFromSemantics(transformed, validActions);
                    }
                }
            }
            if (matchedZone) {
                return;
            }
        }
        if (stateSemantics != null) {
            collectValidActionsFromSemantics(stateSemantics, validActions);
        }
        if (constraintSemantics != null) {
            collectValidActionsFromSemantics(constraintSemantics, validActions);
        }
    }
    
    private void collectValidActionsFromSemantics(Semantics sem, Set<String> validActions) {
        if (sem == null || sem.getConfigurations().isEmpty()) return;
        
        for (Configuration conf : sem.getConfigurations()) {
            for (BasicStateProposition bsp : conf.getBasicStatePropositions()) {
                String machineId = bsp.getMachineId();
                String stateName = bsp.getStateName();
                machinery.StateMachine machine = assembly.getStateMachines().get(machineId);
                if (machine == null) continue;
                
                // Find triggerable transitions from this state
                for (TransitionInterface t : machine.getTransitions()) {
                    if (t.isTriggerable() && t.getSource() != null && stateName.equals(t.getSource().getName())) {
                        validActions.add(machineId + "." + t.getTriggerEvent());
                    }
                }
            }
        }
    }

    /**
     * Collects autonomous guard targets that are still meaningful for validation:
     * includes reactive and CS-only zones, excluding SS zones that became internal.
     */
    private Set<String> collectAutonomousGuardValidationTargets(PWSState srcState) {
        Set<String> targets = new LinkedHashSet<>();
        if (srcState == null) {
            return targets;
        }

        Set<ExitZone> reactive = srcState.getReactiveSemantics();
        Set<ExitZone> csOnly = srcState.getCsOnlyExitZones();
        List<ExitZone> zones = new ArrayList<>();
        if (reactive != null) {
            zones.addAll(reactive);
        }
        if (csOnly != null && !csOnly.isEmpty()) {
            for (ExitZone ez : csOnly) {
                if (!zones.contains(ez)) {
                    zones.add(ez);
                }
            }
        }

        for (ExitZone zone : zones) {
            if (zone == null || zone.getTarget() == null) continue;
            BasicStateProposition target = zone.getTarget();
            boolean isCsOnly = csOnly != null && csOnly.contains(zone);
            if (!isCsOnly && PWSStateMachine.isExitZoneInternal(srcState, zone, assembly)) {
                continue; // internal (gray) exit zone
            }
            targets.add(target.toString());
        }
        return targets;
    }
    
    private void appendActionProblemsSection(List<ActionProblem> problems) {
        appendText("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", STYLE_GRAY);
        appendText("ACTION PROBLEMS\n", STYLE_SECTION);
        appendText("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n", STYLE_GRAY);
        
        appendText("  Actions shown in ", STYLE_NORMAL);
        appendText("red", STYLE_RED);
        appendText(" on the diagram indicate orphan actions.\n", STYLE_NORMAL);
        appendText("  An action is orphan when it references events not reachable from the\n", STYLE_NORMAL);
        appendText("  source state's semantics or constraints.\n\n", STYLE_NORMAL);
        
        for (ActionProblem ap : problems) {
            appendText("  • Transition: ", STYLE_BOLD);
            appendText(ap.sourceState + " → " + ap.targetState + "\n", STYLE_CODE);
            appendText("    Action: ", STYLE_NORMAL);
            appendText(ap.actionText + "\n", STYLE_RED);
            appendText("    Problem: ", STYLE_NORMAL);
            appendText(ap.problemType + "\n", STYLE_ORANGE);
            appendText("    ", STYLE_NORMAL);
            appendText(ap.explanation + "\n\n", STYLE_GRAY);
        }
        
        appendText("  ", STYLE_NORMAL);
        appendText("Fix: ", STYLE_BOLD);
        appendText("Remove orphan actions or update the source state constraints to include\n", STYLE_GRAY);
        appendText("        the machine states that enable these events.\n\n", STYLE_GRAY);
    }
    
    // ==================== Exit Zone Problems ====================
    
    private static class ExitZoneProblem {
        final ExitZone exitZone;
        final String description;
        
        ExitZoneProblem(ExitZone ez, String desc) {
            this.exitZone = ez;
            this.description = desc;
        }
    }
    
    private Map<PWSState, List<ExitZoneProblem>> collectExitZoneProblems() {
        Map<PWSState, List<ExitZoneProblem>> problemMap = new LinkedHashMap<>();
        
        for (StateInterface si : stateMachine.getStates()) {
            if (!(si instanceof PWSState ps) || ps.isPseudoState()) continue;
            if (ps.isFailState()) continue;
            
            HashSet<ExitZone> reactive = ps.getReactiveSemantics();
            if (reactive == null || reactive.isEmpty()) continue;
            // Collect guards from autonomous transitions leaving this state
            Set<String> coveredGuards = new HashSet<>();
            for (TransitionInterface ti : stateMachine.getTransitions()) {
                if (ti instanceof PWSTransition pt && pt.isEnabled() && !pt.isTriggerable() 
                        && pt.getSource() == ps 
                        && pt.getGuardProposition() instanceof BasicStateProposition bsp) {
                    coveredGuards.add(bsp.toString());
                }
            }
            
            // Check for uncovered exit zones
            List<ExitZoneProblem> stateProblems = new ArrayList<>();
            for (ExitZone ez : reactive) {
                if (ez.isOrphanSource(assembly)) continue;
                boolean isInternal = PWSStateMachine.isExitZoneInternal(ps, ez, assembly);
                if (isInternal) {
                    continue;
                }
                if (ez.getTarget() != null && !coveredGuards.contains(ez.getTarget().toString())) {
                    String desc = "Exit zone '" + ez.getTarget() + "' has no covering autonomous transition.";
                    stateProblems.add(new ExitZoneProblem(ez, desc));
                }
            }
            
            if (!stateProblems.isEmpty()) {
                problemMap.put(ps, stateProblems);
            }
        }
        
        return problemMap;
    }

    private Map<PWSState, List<ExitZoneProblem>> collectOrphanExitZoneProblems() {
        Map<PWSState, List<ExitZoneProblem>> problemMap = new LinkedHashMap<>();

        for (StateInterface si : stateMachine.getStates()) {
            if (!(si instanceof PWSState ps) || ps.isPseudoState()) continue;

            HashSet<ExitZone> reactive = ps.getReactiveSemantics();
            if (reactive == null || reactive.isEmpty()) continue;

            List<ExitZoneProblem> stateProblems = new ArrayList<>();
            for (ExitZone ez : reactive) {
                if (ez.isOrphanSource(assembly)) {
                    String desc = "Orphan exit zone — no matching source state.";
                    stateProblems.add(new ExitZoneProblem(ez, desc));
                }
            }

            if (!stateProblems.isEmpty()) {
                problemMap.put(ps, stateProblems);
            }
        }

        return problemMap;
    }
    
    private void appendExitZoneProblemsSection(Map<PWSState, List<ExitZoneProblem>> problemMap) {
        appendText("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", STYLE_GRAY);
        appendText("UNCOVERED EXIT ZONES\n", STYLE_SECTION);
        appendText("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n", STYLE_GRAY);
        
        appendText("  Exit zones represent autonomous component evolution outside\n", STYLE_NORMAL);
        appendText("  the current state's allowed semantics.\n", STYLE_NORMAL);
        appendText("  Each exit zone should have a covering autonomous transition.\n\n", STYLE_NORMAL);
        
        for (Map.Entry<PWSState, List<ExitZoneProblem>> entry : problemMap.entrySet()) {
            PWSState state = entry.getKey();
            List<ExitZoneProblem> problems = entry.getValue();
            
            appendText("  State: ", STYLE_BOLD);
            appendText(state.getName() + "\n", STYLE_CODE);
            
            for (ExitZoneProblem ezp : problems) {
                appendText("    ✗ ", STYLE_RED);
                appendText(ezp.description + "\n", STYLE_NORMAL);
                
                // Show source information (the configuration that leads to this exit)
                if (ezp.exitZone.getSource() != null) {
                    appendText("      From: ", STYLE_GRAY);
                    appendText(ezp.exitZone.getSource().toString() + " → " + ezp.exitZone.getTarget().toString() + "\n", STYLE_CODE);
                }
            }
            appendText("\n", STYLE_NORMAL);
        }
        
        appendText("  ", STYLE_NORMAL);
        appendText("Fix: ", STYLE_BOLD);
        appendText("Add autonomous transitions with guards matching these exit zones.\n\n", STYLE_GRAY);
    }

    private void appendOrphanExitZoneProblemsSection(Map<PWSState, List<ExitZoneProblem>> problemMap) {
        appendText("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", STYLE_GRAY);
        appendText("ORPHAN EXIT ZONES\n", STYLE_SECTION);
        appendText("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n", STYLE_GRAY);

        appendText("  Exit zones whose source state no longer exists in the assembly.\n", STYLE_NORMAL);
        appendText("  These typically indicate stale data or inconsistent models.\n\n", STYLE_NORMAL);

        for (Map.Entry<PWSState, List<ExitZoneProblem>> entry : problemMap.entrySet()) {
            PWSState state = entry.getKey();
            List<ExitZoneProblem> problems = entry.getValue();

            appendText("  State: ", STYLE_BOLD);
            appendText(state.getName() + "\n", STYLE_CODE);

            for (ExitZoneProblem ezp : problems) {
                appendText("    ✗ ", STYLE_RED);
                appendText(ezp.description + "\n", STYLE_NORMAL);

                if (ezp.exitZone.getSource() != null || ezp.exitZone.getTarget() != null) {
                    appendText("      From: ", STYLE_GRAY);
                    if (ezp.exitZone.getSource() != null && ezp.exitZone.getTarget() != null) {
                        appendText(ezp.exitZone.getSource().toString() + " → " + ezp.exitZone.getTarget().toString() + "\n", STYLE_CODE);
                    } else if (ezp.exitZone.getSource() != null) {
                        appendText(ezp.exitZone.getSource().toString() + " → (unknown)\n", STYLE_CODE);
                    } else {
                        appendText("(unknown) → " + ezp.exitZone.getTarget().toString() + "\n", STYLE_CODE);
                    }
                }
            }
            appendText("\n", STYLE_NORMAL);
        }

        appendText("  ", STYLE_NORMAL);
        appendText("Fix: ", STYLE_BOLD);
        appendText("Restore the missing source state/transition or recompute semantics to remove stale exit zones.\n\n", STYLE_GRAY);
    }
    
    // ==================== Constraint Problems ====================
    
    private Map<PWSState, List<String>> collectConstraintProblems() {
        Map<PWSState, List<String>> problemMap = new LinkedHashMap<>();
        
        for (StateInterface si : stateMachine.getStates()) {
            if (!(si instanceof PWSState ps) || ps.isPseudoState()) continue;
            
            Semantics ss = ps.getStateSemantics();
            Semantics cs = ps.getConstraintsSemantics();
            String rawConstraint = ps.getRawConstraintText();
            
            if (ss == null || cs == null) continue;
            boolean hasRaw = rawConstraint != null && !rawConstraint.isBlank();
            boolean rawAny = hasRaw && "ANY".equalsIgnoreCase(rawConstraint.trim());
            boolean hasCs = cs != null && !cs.getConfigurations().isEmpty();
            boolean anyConstraint = ps.isPseudoState() || rawAny || (!hasRaw && !hasCs);
            if (anyConstraint) continue;
            
            // Check if state semantics violates constraints
            List<String> violations = new ArrayList<>();
            for (Configuration cfg : ss.getConfigurations()) {
                if (!cfg.implies(cs)) {
                    violations.add(cfg.toString());
                }
            }
            
            if (!violations.isEmpty()) {
                problemMap.put(ps, violations);
            }
        }
        
        return problemMap;
    }
    
    private void appendConstraintProblemsSection(Map<PWSState, List<String>> problemMap) {
        appendText("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", STYLE_GRAY);
        appendText("CONSTRAINT VIOLATIONS\n", STYLE_SECTION);
        appendText("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n", STYLE_GRAY);
        
        appendText("  These configurations appear in state semantics but violate\n", STYLE_NORMAL);
        appendText("  the user-defined constraints. They are shown in ", STYLE_NORMAL);
        appendText("red", STYLE_RED);
        appendText(" on dashboards.\n\n", STYLE_NORMAL);
        
        for (Map.Entry<PWSState, List<String>> entry : problemMap.entrySet()) {
            PWSState state = entry.getKey();
            List<String> violations = entry.getValue();
            
            appendText("  State: ", STYLE_BOLD);
            appendText(state.getName() + "\n", STYLE_CODE);
            
            for (String cfg : violations) {
                appendText("    ✗ ", STYLE_RED);
                appendText(cfg + "\n", STYLE_CODE);
            }
            appendText("\n", STYLE_NORMAL);
        }
        
        appendText("  ", STYLE_NORMAL);
        appendText("Fix: ", STYLE_BOLD);
        appendText("Review constraints or transition structure to eliminate these configurations.\n\n", STYLE_GRAY);
    }
    
    // ==================== Deadlock Problems ====================

    private Set<String> collectEscapeCoveredCfgStrs(PWSState state) {
        return StateSemanticsAnnotation.computeCoveredCfgStrs(state, stateMachine);
    }
    
    private Map<PWSState, Set<Configuration>> collectPrimaryDeadlockProblems() {
        Map<PWSState, Set<Configuration>> problemMap = new LinkedHashMap<>();

        for (StateInterface si : stateMachine.getStates()) {
            if (!(si instanceof PWSState ps) || ps.isPseudoState() || ps.isFailState()) continue;

            Semantics ss = ps.getStateSemantics();
            if (ss == null || ss.getConfigurations().isEmpty()) continue;

            Set<String> coveredCfgStrs = collectEscapeCoveredCfgStrs(ps);
            Set<Configuration> primaryDeadlocks = new LinkedHashSet<>();
            for (Configuration cfg : ss.getConfigurations()) {
                if (!coveredCfgStrs.contains(cfg.toString())) {
                    primaryDeadlocks.add(cfg);
                }
            }

            if (!primaryDeadlocks.isEmpty()) {
                problemMap.put(ps, primaryDeadlocks);
            }
        }

        return problemMap;
    }

    private Map<PWSState, Set<Configuration>> collectSecondaryDeadlockProblems() {
        Map<PWSState, Set<Configuration>> problemMap = new LinkedHashMap<>();
        
        for (StateInterface si : stateMachine.getStates()) {
            if (!(si instanceof PWSState ps) || ps.isPseudoState() || ps.isFailState()) continue;
            
            Set<Configuration> deadlocks = ps.getDeadlockConfigurations();
            if (deadlocks == null || deadlocks.isEmpty()) continue;
            
            Semantics ss = ps.getStateSemantics();
            if (ss == null) continue;
            
            Set<String> coveredCfgStrs = collectEscapeCoveredCfgStrs(ps);
            
            // Secondary deadlock = internally stuck and primary deadlock.
            Set<Configuration> secondaryDeadlocks = new LinkedHashSet<>();
            for (Configuration cfg : deadlocks) {
                if (!coveredCfgStrs.contains(cfg.toString())) {
                    secondaryDeadlocks.add(cfg);
                }
            }
            
            if (!secondaryDeadlocks.isEmpty()) {
                problemMap.put(ps, secondaryDeadlocks);
            }
        }
        
        return problemMap;
    }
    
    private void appendPrimaryDeadlockProblemsSection(Map<PWSState, Set<Configuration>> problemMap) {
        appendText("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", STYLE_GRAY);
        appendText("PRIMARY DEADLOCK CONFIGURATIONS\n", STYLE_SECTION);
        appendText("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n", STYLE_GRAY);
        
        appendText("  These configurations have no escape path to an enabled outgoing\n", STYLE_NORMAL);
        appendText("  controller transition (directly or after internal evolution).\n\n", STYLE_NORMAL);
        
        for (Map.Entry<PWSState, Set<Configuration>> entry : problemMap.entrySet()) {
            PWSState state = entry.getKey();
            Set<Configuration> deadlocks = entry.getValue();
            
            appendText("  State: ", STYLE_BOLD);
            appendText(state.getName() + "\n", STYLE_CODE);
            
            for (Configuration cfg : deadlocks) {
                appendText("    ⚠ ", STYLE_ORANGE);
                appendText(cfg.toString() + "\n", STYLE_CODE);
            }
            appendText("\n", STYLE_NORMAL);
        }
        
        appendText("  ", STYLE_NORMAL);
        appendText("Fix: ", STYLE_BOLD);
        appendText("Add enabled outgoing transitions (excluding self-loops) that can fire either directly or after internal evolution.\n\n", STYLE_GRAY);
    }
    
    private void appendSecondaryDeadlockProblemsSection(Map<PWSState, Set<Configuration>> problemMap) {
        appendText("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", STYLE_GRAY);
        appendText("SECONDARY (INTERNAL) DEADLOCK CONFIGURATIONS\n", STYLE_SECTION);
        appendText("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n", STYLE_GRAY);
        
        appendText("  These configurations cannot evolve internally and have no escape\n", STYLE_NORMAL);
        appendText("  path to any outgoing transition. The system may get stuck.\n\n", STYLE_NORMAL);
        
        for (Map.Entry<PWSState, Set<Configuration>> entry : problemMap.entrySet()) {
            PWSState state = entry.getKey();
            Set<Configuration> deadlocks = entry.getValue();
            
            appendText("  State: ", STYLE_BOLD);
            appendText(state.getName() + "\n", STYLE_CODE);
            
            for (Configuration cfg : deadlocks) {
                appendText("    ⚠ ", STYLE_ORANGE);
                appendText(cfg.toString() + "\n", STYLE_CODE);
            }
            appendText("\n", STYLE_NORMAL);
        }
        
        appendText("  ", STYLE_NORMAL);
        appendText("Fix: ", STYLE_BOLD);
        appendText("Add transitions that can fire from these configurations, or verify the sink behavior is intended.\n\n", STYLE_GRAY);
    }
    
    // ==================== Unreachable States ====================
    
    private List<PWSState> collectUnreachableStates() {
        List<PWSState> unreachable = new ArrayList<>();
        for (StateInterface si : stateMachine.getStates()) {
            if (!(si instanceof PWSState ps) || ps.isPseudoState()) continue;
            Semantics ss = ps.getStateSemantics();
            if (ss == null || ss.getConfigurations().isEmpty()) {
                unreachable.add(ps);
            }
        }
        return unreachable;
    }
    
    private void appendUnreachableStatesSection(List<PWSState> states) {
        appendText("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", STYLE_GRAY);
        appendText("UNREACHABLE STATES\n", STYLE_SECTION);
        appendText("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n", STYLE_GRAY);
        
        appendText("  These states have no computed configurations and cannot be reached.\n\n", STYLE_NORMAL);
        
        for (PWSState ps : states) {
            appendText("  State: ", STYLE_BOLD);
            appendText(ps.getName() + "\n", STYLE_CODE);
        }
        appendText("\n", STYLE_NORMAL);
        
        appendText("  ", STYLE_NORMAL);
        appendText("Fix: ", STYLE_BOLD);
        appendText("Check incoming transitions and guards, or remove the unreachable state.\n\n", STYLE_GRAY);
    }
    
    // ==================== LTL Placeholder ====================
    
    private void appendLTLPlaceholderSection() {
        appendText("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", STYLE_GRAY);
        appendText("LTL FORMULA VERIFICATION\n", STYLE_SECTION);
        appendText("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n", STYLE_GRAY);
        
        appendText("  ", STYLE_NORMAL);
        appendText("(Coming soon)", STYLE_GRAY);
        appendText("\n\n  LTL formula satisfaction results will appear here once\n", STYLE_NORMAL);
        appendText("  the LTL verification functionality is implemented.\n\n", STYLE_NORMAL);
    }
    
    // ==================== Final Status ====================
    
    private void appendFinalStatus() {
        appendText("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", STYLE_GRAY);
        appendText("OVERALL STATUS\n", STYLE_SECTION);
        appendText("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n", STYLE_GRAY);

        if (totalProblems == 0) {
            boolean hasFailStates = false;
            for (StateInterface si : stateMachine.getStates()) {
                if (si instanceof PWSState ps && !ps.isPseudoState() && ps.isFailState()) {
                    hasFailStates = true;
                    break;
                }
            }
            appendText("  ✓ CONTROLLER IS WELL-FORMED\n\n", STYLE_GREEN);
            appendText("    All guards are properly configured.\n", STYLE_GREEN);
            if (hasFailStates) {
                appendText("    Exit-zone coverage is satisfied for non-fail states.\n", STYLE_GREEN);
                appendText("    Fail states mask primary/secondary deadlock checks.\n", STYLE_GREEN);
            } else {
                appendText("    All exit zones are covered and none are orphan.\n", STYLE_GREEN);
            }
            appendText("    All configurations satisfy constraints.\n", STYLE_GREEN);
            appendText("    No primary deadlock configurations.\n", STYLE_GREEN);
            appendText("    No secondary (internal) deadlock configurations.\n", STYLE_GREEN);
            appendText("    No unreachable states.\n\n", STYLE_GREEN);
        } else {
            appendText("  ⚠ CONTROLLER HAS " + totalProblems + " ISSUE" + (totalProblems > 1 ? "S" : "") + "\n\n", STYLE_RED);
            appendText("    Review the sections above and address each issue.\n", STYLE_NORMAL);
            appendText("    Problems are also highlighted in ", STYLE_NORMAL);
            appendText("red", STYLE_RED);
            appendText(" on the diagram.\n\n", STYLE_NORMAL);
        }
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
