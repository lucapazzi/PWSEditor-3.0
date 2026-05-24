package utility;

import machinery.TransitionInterface;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Draggable label used to reposition transition trigger text. */
@SuppressWarnings("this-escape")
public class DraggableTriggerLabel extends JLabel {
    private static final long serialVersionUID = 1L;
    private Point initialClick;
    private TransitionInterface associatedTransition;
    private transient boolean delegatedToPanelSelection = false;

    /**
     * Creates a draggable label for the given transition.
     *
     * @param text label text
     * @param associatedTransition transition whose trigger offset is updated
     */
    public DraggableTriggerLabel(String text, TransitionInterface associatedTransition) {
        super("<html><b><u>" + text + "</u></b></html>");
        setOpaque(false);
        this.associatedTransition = associatedTransition;
        initDrag();
    }

    /**
     * Creates a draggable label without an associated transition.
     *
     * @param text label text
     */
    public DraggableTriggerLabel(String text) {
        this(text, null);
    }

    public TransitionInterface getAssociatedTransition() {
        return associatedTransition;
    }

    private void initDrag() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Component smPanel = SwingUtilities.getAncestorOfClass(editor.StateMachinePanel.class, DraggableTriggerLabel.this);
                if (smPanel instanceof editor.StateMachinePanel panel
                        && panel.handleSelectableComponentMousePressed(DraggableTriggerLabel.this, e)) {
                    delegatedToPanelSelection = true;
                    return;
                }
                delegatedToPanelSelection = false;
                initialClick = e.getPoint();
                if (smPanel != null) {
                    smPanel.requestFocusInWindow();
                } else if (getParent() != null) {
                    getParent().requestFocusInWindow();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2 && associatedTransition != null) {
                    String current = associatedTransition.getTriggerEvent();
                    String input = JOptionPane.showInputDialog(DraggableTriggerLabel.this, "Edit trigger event:", current);
                    if (input != null) {
                        associatedTransition.setTriggerEvent(input);
                        setText("<html><b><u>" + input + "</u></b></html>");
                        revalidate();
                        repaint();
                        Component comp = SwingUtilities.getAncestorOfClass(editor.StateMachinePanel.class, DraggableTriggerLabel.this);
                        if (comp instanceof editor.StateMachinePanel panel) {
                            panel.notifyTriggerEventChanged();
                        }
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                Component comp = SwingUtilities.getAncestorOfClass(editor.StateMachinePanel.class, DraggableTriggerLabel.this);
                if (delegatedToPanelSelection) {
                    delegatedToPanelSelection = false;
                    if (comp instanceof editor.StateMachinePanel panel
                            && panel.handleSelectableComponentMouseReleased(DraggableTriggerLabel.this, e)) {
                        return;
                    }
                }
                if (comp instanceof editor.StateMachinePanel) {
                    editor.StateMachinePanel panel = (editor.StateMachinePanel) comp;
                    if (panel.isSnapToGrid()) {
                        int grid = panel.getGridSize();
                        if (grid > 0) {
                            Point snapped = SnapUtils.snapComponentTopLeftToHalfGrid(
                                    getX(), getY(), getWidth(), getHeight(), grid);
                            setLocation(snapped);
                            if (associatedTransition != null) {
                                associatedTransition.setTriggerOffset(new Point(snapped));
                            }
                            if (getParent() != null) getParent().repaint();
                        }
                    }
                }
            }
        });

        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                Component comp = SwingUtilities.getAncestorOfClass(editor.StateMachinePanel.class, DraggableTriggerLabel.this);
                if (delegatedToPanelSelection
                        && comp instanceof editor.StateMachinePanel panel
                        && panel.handleSelectableComponentMouseDragged(DraggableTriggerLabel.this, e)) {
                    return;
                }
                int thisX = getX();
                int thisY = getY();
                int xMoved = e.getX() - initialClick.x;
                int yMoved = e.getY() - initialClick.y;
                int newX = thisX + xMoved;
                int newY = thisY + yMoved;
                if (comp instanceof editor.StateMachinePanel) {
                    editor.StateMachinePanel panel = (editor.StateMachinePanel) comp;
                    if (panel.isSnapToGrid()) {
                        int grid = panel.getGridSize();
                        if (grid > 0) {
                            Point snapped = SnapUtils.snapComponentTopLeftToHalfGrid(
                                    newX, newY, getWidth(), getHeight(), grid);
                            setLocation(snapped);
                            newX = snapped.x;
                            newY = snapped.y;
                        } else {
                            setLocation(newX, newY);
                        }
                    } else {
                        setLocation(newX, newY);
                    }
                } else {
                    setLocation(newX, newY);
                }

                if (associatedTransition != null) {
                    associatedTransition.setTriggerOffset(new Point(newX, newY));
                }
            }
        });
    }
}
