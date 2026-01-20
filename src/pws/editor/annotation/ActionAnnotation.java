package pws.editor.annotation;

import assembly.Action;
import assembly.ActionList;
import assembly.AssemblyInterface;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import pws.PWSState;
import pws.editor.semantics.Configuration;
import pws.editor.semantics.Semantics;
import smalgebra.BasicStateProposition;
import machinery.StateMachine;

/** Annotation widget for editing a list of actions. */
public class ActionAnnotation extends Annotation<ActionList> {
    private AssemblyInterface assembly;
    private Consumer<ActionList> updateCallback; // Callback per aggiornare il modello
    // Optional associated transition (when this annotation is attached to a transition)
    private machinery.TransitionInterface associatedTransition;

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
        // restrict insertable actions to events reachable from states in the source state's semantics.
        boolean filteredBySemantics = false;
        if (associatedTransition != null) {
            machinery.StateInterface src = associatedTransition.getSource();
            if (src instanceof PWSState) {
                Semantics sem = ((PWSState) src).getStateSemantics();
                if (sem != null && !sem.getConfigurations().isEmpty()) {
                    filteredBySemantics = true;
                    Set<String> candidateStrings = new LinkedHashSet<>();
                    for (Configuration conf : sem.getConfigurations()) {
                        for (BasicStateProposition bsp : conf.getBasicStatePropositions()) {
                            String machineId = bsp.getMachineId();
                            String stateName = bsp.getStateName();
                            StateMachine machine = assembly.getStateMachines().get(machineId);
                            if (machine == null) continue;
                            for (machinery.TransitionInterface t : machine.getTransitions()) {
                                if (t.isTriggerable() && t.getSource() != null && stateName.equals(t.getSource().getName())) {
                                    candidateStrings.add(machineId + "." + t.getTriggerEvent());
                                }
                            }
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
                    if (w instanceof pws.editor.PWSEditor pe) pe.markDocumentDirty();
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
                    if (w instanceof pws.editor.PWSEditor pe) pe.markDocumentDirty();
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
