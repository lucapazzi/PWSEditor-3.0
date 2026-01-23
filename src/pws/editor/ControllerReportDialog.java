package pws.editor;

import assembly.Assembly;
import machinery.StateInterface;
import machinery.TransitionInterface;
import pws.PWSState;
import pws.PWSStateMachine;
import pws.PWSTransition;
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
public class ControllerReportDialog extends JDialog {
    
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
    private int constraintProblems = 0;
    private int deadlockProblems = 0;
    
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
        Map<PWSState, List<String>> constraintProblemMap = collectConstraintProblems();
        Map<PWSState, Set<Configuration>> deadlockMap = collectDeadlockProblems();
        
        guardProblems = guardProblemsList.size();
        actionProblems = actionProblemsList.size();
        exitZoneProblems = exitZoneProblemMap.values().stream().mapToInt(List::size).sum();
        constraintProblems = constraintProblemMap.values().stream().mapToInt(List::size).sum();
        deadlockProblems = deadlockMap.values().stream().mapToInt(Set::size).sum();
        totalProblems = guardProblems + actionProblems + exitZoneProblems + constraintProblems + deadlockProblems;
        
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
        
        if (constraintProblems > 0) {
            appendConstraintProblemsSection(constraintProblemMap);
        }
        
        if (deadlockProblems > 0) {
            appendDeadlockProblemsSection(deadlockMap);
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
        
        for (StateInterface si : stateMachine.getStates()) {
            if (si instanceof PWSState ps && !ps.isPseudoState()) {
                stateCount++;
            }
        }
        
        for (TransitionInterface ti : stateMachine.getTransitions()) {
            if (ti instanceof PWSTransition pt && pt.isEnabled()) {
                transitionCount++;
                if (pt.isAutonomous()) {
                    autonomousCount++;
                } else {
                    triggeredCount++;
                }
            }
        }
        
        appendText("  Controller Structure:\n", STYLE_BOLD);
        appendText("    • States: " + stateCount + "\n", STYLE_NORMAL);
        appendText("    • Transitions: " + transitionCount + " (", STYLE_NORMAL);
        appendText(autonomousCount + " autonomous", STYLE_CODE);
        appendText(", ", STYLE_NORMAL);
        appendText(triggeredCount + " triggered", STYLE_CODE);
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
            if (constraintProblems > 0) {
                appendText("    ✗ ", STYLE_RED);
                appendText("Constraint violations: " + constraintProblems + "\n", STYLE_NORMAL);
            }
            if (deadlockProblems > 0) {
                appendText("    ⚠ ", STYLE_ORANGE);
                appendText("Deadlock configurations: " + deadlockProblems + "\n", STYLE_NORMAL);
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
            
            // Check TRUE on autonomous (but not initial transitions - they have a hidden startup trigger)
            if (guard instanceof TrueProposition && pt.isAutonomous()) {
                // Initial transitions (from pseudo-state) are treated as triggered by a hidden startup event
                boolean isInitialTransition = (src instanceof PWSState ps && ps.isPseudoState());
                if (!isInitialTransition) {
                    problems.add(new GuardProblem(pt, srcName, tgtName, guardStr,
                            "TRUE on Autonomous",
                            "Fires immediately when entering source state. Add a trigger event or specific guard."));
                    continue;
                }
            }
            
            // Check orphan guard (guard references exit zone that doesn't exist)
            if (pt.isAutonomous() && guard instanceof BasicStateProposition bsp) {
                if (src instanceof PWSState ps && !ps.isPseudoState()) {
                    HashSet<ExitZone> reactive = ps.getReactiveSemantics();
                    if (reactive != null && !reactive.isEmpty()) {
                        boolean found = false;
                        for (ExitZone zone : reactive) {
                            if (zone != null && zone.getTarget() != null 
                                    && zone.getTarget().toString().equals(bsp.toString())) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            problems.add(new GuardProblem(pt, srcName, tgtName, guardStr,
                                    "Orphan Guard",
                                    "References exit zone '" + bsp + "' which no longer exists in state semantics."));
                        }
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
            if (src instanceof PWSState ps && ps.isPseudoState()) continue;
            
            // Get valid actions from source state semantics
            Set<String> validActions = new HashSet<>();
            if (src instanceof PWSState ps) {
                Semantics stateSemantics = ps.getStateSemantics();
                Semantics constraintSemantics = ps.getConstraintsSemantics();
                
                if (stateSemantics != null) {
                    collectValidActionsFromSemantics(stateSemantics, validActions);
                }
                if (constraintSemantics != null) {
                    collectValidActionsFromSemantics(constraintSemantics, validActions);
                }
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
    
    private void appendExitZoneProblemsSection(Map<PWSState, List<ExitZoneProblem>> problemMap) {
        appendText("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", STYLE_GRAY);
        appendText("UNCOVERED EXIT ZONES\n", STYLE_SECTION);
        appendText("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n", STYLE_GRAY);
        
        appendText("  Exit zones represent configurations where component machines can\n", STYLE_NORMAL);
        appendText("  autonomously evolve outside the current state's constraints.\n", STYLE_NORMAL);
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
    
    // ==================== Constraint Problems ====================
    
    private Map<PWSState, List<String>> collectConstraintProblems() {
        Map<PWSState, List<String>> problemMap = new LinkedHashMap<>();
        
        for (StateInterface si : stateMachine.getStates()) {
            if (!(si instanceof PWSState ps) || ps.isPseudoState()) continue;
            
            Semantics ss = ps.getStateSemantics();
            Semantics cs = ps.getConstraintsSemantics();
            
            if (ss == null || cs == null) continue;
            
            // Check if state semantics violates constraints
            Set<String> constraintStrs = new HashSet<>();
            for (Configuration cfg : cs.getConfigurations()) {
                constraintStrs.add(cfg.toString());
            }
            
            List<String> violations = new ArrayList<>();
            for (Configuration cfg : ss.getConfigurations()) {
                if (!constraintStrs.contains(cfg.toString())) {
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
    
    private Map<PWSState, Set<Configuration>> collectDeadlockProblems() {
        Map<PWSState, Set<Configuration>> problemMap = new LinkedHashMap<>();
        
        for (StateInterface si : stateMachine.getStates()) {
            if (!(si instanceof PWSState ps) || ps.isPseudoState()) continue;
            
            Set<Configuration> deadlocks = ps.getDeadlockConfigurations();
            if (deadlocks == null || deadlocks.isEmpty()) continue;
            
            Semantics ss = ps.getStateSemantics();
            if (ss == null) continue;
            
            // Check which deadlocks are covered by outgoing transitions
            Set<String> coveredCfgStrs = new HashSet<>();
            for (TransitionInterface ti : stateMachine.getTransitions()) {
                if (ti instanceof PWSTransition pt && pt.isEnabled() && pt.getSource() == ps) {
                    SMProposition guard = pt.getGuardProposition();
                    if (guard != null) {
                        for (Configuration cfg : ss.getConfigurations()) {
                            if (guard.evaluateConfiguration(cfg, assembly)) {
                                coveredCfgStrs.add(cfg.toString());
                            }
                        }
                    }
                }
            }
            
            // Find true deadlocks (not covered by any transition)
            Set<Configuration> trueDeadlocks = new LinkedHashSet<>();
            for (Configuration cfg : deadlocks) {
                if (!coveredCfgStrs.contains(cfg.toString())) {
                    trueDeadlocks.add(cfg);
                }
            }
            
            if (!trueDeadlocks.isEmpty()) {
                problemMap.put(ps, trueDeadlocks);
            }
        }
        
        return problemMap;
    }
    
    private void appendDeadlockProblemsSection(Map<PWSState, Set<Configuration>> problemMap) {
        appendText("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", STYLE_GRAY);
        appendText("DEADLOCK CONFIGURATIONS\n", STYLE_SECTION);
        appendText("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n", STYLE_GRAY);
        
        appendText("  These configurations have no autonomous evolution path and are not\n", STYLE_NORMAL);
        appendText("  covered by any outgoing transition. The system may get stuck.\n\n", STYLE_NORMAL);
        
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
        appendText("Add transitions that can fire from these configurations, or verify deadlock is intended.\n\n", STYLE_GRAY);
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
            appendText("  ✓ CONTROLLER IS WELL-FORMED\n\n", STYLE_GREEN);
            appendText("    All guards are properly configured.\n", STYLE_GREEN);
            appendText("    All exit zones are covered by transitions.\n", STYLE_GREEN);
            appendText("    All configurations satisfy constraints.\n", STYLE_GREEN);
            appendText("    No unrecoverable deadlock configurations.\n\n", STYLE_GREEN);
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
