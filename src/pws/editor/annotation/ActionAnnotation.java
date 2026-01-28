package pws.editor.annotation;

import assembly.Action;
import assembly.ActionList;
import assembly.AssemblyInterface;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import assembly.Assembly;
import pws.PWSState;
import pws.PWSTransition;
import pws.editor.semantics.ExitZone;
import pws.editor.semantics.Configuration;
import pws.editor.semantics.Semantics;
import smalgebra.BasicStateProposition;
import smalgebra.SMProposition;
import machinery.StateMachine;

/** Annotation widget for editing a list of actions. */
public class ActionAnnotation extends Annotation<ActionList> {
    private AssemblyInterface assembly;
    private Consumer<ActionList> updateCallback; // Callback per aggiornare il modello
    // Optional associated transition (when this annotation is attached to a transition)
    private machinery.TransitionInterface associatedTransition;
    
    // Problem status for coloring
    private boolean hasOrphanActions = false;
    private List<String> orphanActionReasons = new ArrayList<>();

    /**
     * Creates an action annotation.
     *
     * @param content initial action list
     * @param assembly assembly context
     * @param updateCallback callback to update the model
     */
    public ActionAnnotation(ActionList content, AssemblyInterface assembly, Consumer<ActionList> updateCallback) {
        super(content);
        this.assembly = assembly;
        this.updateCallback = updateCallback;
        this.associatedTransition = null;
        setToolTipText(""); // Enable tooltips
    }

    /**
     * Creates an action annotation attached to a specific transition.
     *
     * @param content initial action list
     * @param assembly assembly context
     * @param updateCallback callback to update the model
     * @param associatedTransition transition associated with this annotation
     */
    public ActionAnnotation(ActionList content, AssemblyInterface assembly, Consumer<ActionList> updateCallback, machinery.TransitionInterface associatedTransition) {
        super(content);
        this.assembly = assembly;
        this.updateCallback = updateCallback;
        this.associatedTransition = associatedTransition;
        setToolTipText(""); // Enable tooltips
    }
    
    /**
     * Checks if any action is orphan (references machines/events not reachable from source state semantics).
     * For autonomous transitions, actions are checked against the source semantics after
     * applying the matching exit-zone internal transition.
     * An action is orphan when:
     * - The machine it references is not in the source state's semantics or constraints
     * - The event it references is not triggerable from any state in the source semantics
     */
    private void checkOrphanActions() {
        hasOrphanActions = false;
        orphanActionReasons.clear();
        
        if (content == null || content.isEmpty()) return;
        if (associatedTransition == null) return;
        
        machinery.StateInterface src = associatedTransition.getSource();
        if (!(src instanceof PWSState ps) || ps.isPseudoState()) return;
        
        // Get valid machine.event combinations from source state semantics
        Set<String> validActions = new HashSet<>();
        Semantics stateSemantics = ps.getStateSemantics();
        Semantics constraintSemantics = ps.getConstraintsSemantics();
        if (associatedTransition instanceof PWSTransition pt) {
            collectValidActionsForTransition(ps, pt, validActions);
        } else {
            if (stateSemantics != null) {
                collectValidActionsFromSemantics(stateSemantics, validActions);
            }
            if (constraintSemantics != null) {
                collectValidActionsFromSemantics(constraintSemantics, validActions);
            }
        }
        
        // If no semantics available, don't flag as orphan
        if (validActions.isEmpty() && (stateSemantics == null || stateSemantics.getConfigurations().isEmpty()) 
                && (constraintSemantics == null || constraintSemantics.getConfigurations().isEmpty())) {
            return;
        }
        
        // Check each action
        for (Action a : content) {
            String actionStr = a.toString();
            if (!validActions.contains(actionStr)) {
                hasOrphanActions = true;
                orphanActionReasons.add("'" + actionStr + "' is not reachable from source state semantics");
            }
        }
    }

    private void collectValidActionsForTransition(PWSState ps, PWSTransition pt, Set<String> validActions) {
        Semantics stateSemantics = ps.getStateSemantics();
        Semantics constraintSemantics = ps.getConstraintsSemantics();
        SMProposition guard = pt.getGuardProposition();
        if (pt.isAutonomous() && guard instanceof BasicStateProposition) {
            Assembly asm = (assembly instanceof Assembly) ? (Assembly) assembly : null;
            HashSet<ExitZone> reactiveZones = ps.getReactiveSemantics();
            boolean matchedZone = false;
            if (asm != null && reactiveZones != null) {
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
                                ez.getStateMachineId(), ez.getTransition(), asm);
                        collectValidActionsFromSemantics(transformed, validActions);
                    }
                    if (constraintSemantics != null) {
                        Semantics transformed = constraintSemantics.transformByMachineTransition(
                                ez.getStateMachineId(), ez.getTransition(), asm);
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
    
    /**
     * Collects valid machine.event action strings from a semantics object.
     */
    private void collectValidActionsFromSemantics(Semantics sem, Set<String> validActions) {
        if (sem == null || sem.getConfigurations().isEmpty()) return;
        
        for (Configuration conf : sem.getConfigurations()) {
            for (BasicStateProposition bsp : conf.getBasicStatePropositions()) {
                String machineId = bsp.getMachineId();
                String stateName = bsp.getStateName();
                StateMachine machine = assembly.getStateMachines().get(machineId);
                if (machine == null) continue;
                
                // Find triggerable transitions from this state
                for (machinery.TransitionInterface t : machine.getTransitions()) {
                    if (t.isTriggerable() && t.getSource() != null && stateName.equals(t.getSource().getName())) {
                        validActions.add(machineId + "." + t.getTriggerEvent());
                    }
                }
            }
        }
    }
    
    /**
     * Returns whether this annotation has orphan actions.
     */
    public boolean hasOrphanActions() {
        checkOrphanActions();
        return hasOrphanActions;
    }
    
    /**
     * Returns the list of orphan action reasons.
     */
    public List<String> getOrphanActionReasons() {
        checkOrphanActions();
        return orphanActionReasons;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        // Update orphan status before painting
        checkOrphanActions();
        
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setFont(getFont().deriveFont(Font.PLAIN, 12f));
        
        // Set color based on orphan status
        if (hasOrphanActions) {
            g2d.setColor(new Color(180, 0, 0)); // Red for orphan actions
        } else {
            g2d.setColor(Color.BLACK);
        }
        
        String text = buildDisplayText();
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getAscent();
        int x = (getWidth() - textWidth) / 2;
        int y = (getHeight() + textHeight) / 2 - 2;
        g2d.drawString(text, x, y);
    }
    
    @Override
    public String getToolTipText() {
        if (hasOrphanActions && !orphanActionReasons.isEmpty()) {
            return "Orphan actions: " + String.join(", ", orphanActionReasons);
        }
        return super.getToolTipText();
    }

    @Override
    protected void showPopup(MouseEvent e) {
        JPopupMenu popup = new JPopupMenu();

        // Insert section
        JMenuItem insertLabel = new JMenuItem("Insert");
        insertLabel.setEnabled(false);
        popup.add(insertLabel);

        List<Action> allActions = assembly.getAssemblyActions();
        ActionList current = getContent();
        List<Action> actionsToInsert = new ArrayList<>();

        // If this annotation is attached to a transition with a PWS source state,
        // restrict insertable actions to events reachable from states in the source semantics.
        boolean filteredBySemantics = false;
        if (associatedTransition != null) {
            machinery.StateInterface src = associatedTransition.getSource();
            if (src instanceof PWSState) {
                PWSState ps = (PWSState) src;
                Semantics sem = ps.getStateSemantics();
                Semantics cs = ps.getConstraintsSemantics();
                if ((sem != null && !sem.getConfigurations().isEmpty())
                        || (cs != null && !cs.getConfigurations().isEmpty())) {
                    filteredBySemantics = true;
                    Set<String> candidateStrings = new LinkedHashSet<>();
                    if (associatedTransition instanceof PWSTransition pt) {
                        collectValidActionsForTransition(ps, pt, candidateStrings);
                    } else {
                        if (sem != null) {
                            collectValidActionsFromSemantics(sem, candidateStrings);
                        }
                        if (cs != null) {
                            collectValidActionsFromSemantics(cs, candidateStrings);
                        }
                    }
                    // Map assembly actions to the candidate strings, avoiding actions from machines already present in the list.
                    for (Action a : allActions) {
                        boolean alreadyPresent = false;
                        for (Action act : current) {
                            if (act.getMachineId().equals(a.getMachineId())) {
                                alreadyPresent = true;
                                break;
                            }
                        }
                        if (!alreadyPresent && candidateStrings.contains(a.toString())) {
                            actionsToInsert.add(a);
                        }
                    }
                }
            }
        }

        // Fallback: if semantics-based filtering produced no candidates, use the previous behavior.
        if (!filteredBySemantics || actionsToInsert.isEmpty()) {
            for (Action a : allActions) {
                boolean alreadyPresent = false;
                for (Action act : current) {
                    if (act.getMachineId().equals(a.getMachineId())) {
                        alreadyPresent = true;
                        break;
                    }
                }
                if (!alreadyPresent) {
                    actionsToInsert.add(a);
                }
            }
        }
        if (actionsToInsert.isEmpty()) {
            JMenuItem noInsert = new JMenuItem("No available actions");
            noInsert.setEnabled(false);
            popup.add(noInsert);
        } else {
            for (Action a : actionsToInsert) {
                JMenuItem item = new JMenuItem(a.toString());
                item.addActionListener(ev -> {
                    current.add(a);
                    setContent(current);
                    updateCallback.accept(current);
                    java.awt.Window w = SwingUtilities.getWindowAncestor(ActionAnnotation.this);
                    if (w instanceof pws.editor.PWSEditor pe) {
                        pe.markDocumentDirty();
                        pe.scheduleSemanticsRecalculation();
                    }
                    revalidate();
                    repaint();
                });
                popup.add(item);
            }
        }

        popup.addSeparator();

        // Remove section
        JMenuItem removeLabel = new JMenuItem("Remove");
        removeLabel.setEnabled(false);
        popup.add(removeLabel);
        if (current.isEmpty()) {
            JMenuItem noRemove = new JMenuItem("No actions added");
            noRemove.setEnabled(false);
            popup.add(noRemove);
        } else {
            for (Action a : current) {
                JMenuItem item = new JMenuItem(a.toString());
                item.addActionListener(ev -> {
                    current.remove(a);
                    setContent(current);
                    updateCallback.accept(current);
                    java.awt.Window w = SwingUtilities.getWindowAncestor(ActionAnnotation.this);
                    if (w instanceof pws.editor.PWSEditor pe) {
                        pe.markDocumentDirty();
                        pe.scheduleSemanticsRecalculation();
                    }
                    revalidate();
                    repaint();
                });
                popup.add(item);
            }
        }

        popup.show(this, e.getX(), e.getY());
    }

    protected String buildDisplayText() {
        return (content == null ? "" : content.toString());
    }

//    @Override
//    protected void paintComponent(Graphics g) {
//        super.paintComponent(g);
//        // Draw the string representation of the content centered in the component.
//        Graphics2D g2d = (Graphics2D) g;
//        g2d.setFont(getFont().deriveFont(Font.PLAIN, 12f));
//        g2d.setColor(Color.BLACK);
//        String text = (content == null ? "" : content.toString());
//        FontMetrics fm = g2d.getFontMetrics();
//        int textWidth = fm.stringWidth(text);
//        int textHeight = fm.getAscent();
//        int x = (getWidth() - textWidth) / 2;
//        int y = (getHeight() + textHeight) / 2 - 2;
//        g2d.drawString(text, x, y);
//    }
}
