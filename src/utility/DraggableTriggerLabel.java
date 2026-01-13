package utility;

import machinery.TransitionInterface;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Draggable label used to reposition transition trigger text. */
public class DraggableTriggerLabel extends JLabel {
    private Point initialClick;
    private TransitionInterface associatedTransition;

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

    private void initDrag() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                initialClick = e.getPoint();
                Component smPanel = SwingUtilities.getAncestorOfClass(editor.StateMachinePanel.class, DraggableTriggerLabel.this);
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
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                Component comp = SwingUtilities.getAncestorOfClass(editor.StateMachinePanel.class, DraggableTriggerLabel.this);
                if (comp instanceof editor.StateMachinePanel) {
                    editor.StateMachinePanel panel = (editor.StateMachinePanel) comp;
                    if (panel.isSnapToGrid()) {
                        int grid = panel.getGridSize();
                        if (grid > 0) {
                            int x = getX();
                            int y = getY();
                            int w = getWidth();
                            int h = getHeight();
                            int centerX = x + w / 2;
                            int centerY = y + h / 2;
                            int half = Math.max(1, grid / 2);
                            int snappedCenterX = Math.round((float) centerX / half) * half;
                            int snappedCenterY = Math.round((float) centerY / half) * half;
                            int snappedX = snappedCenterX - w / 2;
                            int snappedY = snappedCenterY - h / 2;
                            setLocation(snappedX, snappedY);
                            if (associatedTransition != null) {
                                associatedTransition.setTriggerOffset(new Point(snappedX, snappedY));
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
                int thisX = getX();
                int thisY = getY();
                int xMoved = e.getX() - initialClick.x;
                int yMoved = e.getY() - initialClick.y;
                int newX = thisX + xMoved;
                int newY = thisY + yMoved;

                Component comp = SwingUtilities.getAncestorOfClass(editor.StateMachinePanel.class, DraggableTriggerLabel.this);
                if (comp instanceof editor.StateMachinePanel) {
                    editor.StateMachinePanel panel = (editor.StateMachinePanel) comp;
                    if (panel.isSnapToGrid()) {
                        int grid = panel.getGridSize();
                        if (grid > 0) {
                            int w = getWidth();
                            int h = getHeight();
                            int centerX = newX + w / 2;
                            int centerY = newY + h / 2;
                            int half = Math.max(1, grid / 2);
                            int snappedCenterX = Math.round((float) centerX / half) * half;
                            int snappedCenterY = Math.round((float) centerY / half) * half;
                            int snappedX = snappedCenterX - w / 2;
                            int snappedY = snappedCenterY - h / 2;
                            setLocation(snappedX, snappedY);
                            newX = snappedX;
                            newY = snappedY;
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
