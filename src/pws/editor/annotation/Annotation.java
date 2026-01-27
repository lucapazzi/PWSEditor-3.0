package pws.editor.annotation;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.Serializable;

import editor.StateMachinePanel;

/**
 * Base draggable annotation component bound to a piece of model content.
 *
 * @param <T> type of the annotated content
 */
public class Annotation<T> extends JComponent implements Serializable {
    /** Model content displayed by the annotation. */
    protected T content;
    /** Mouse drag offset used while repositioning the annotation. */
    protected Point dragOffset;

    /**
     * Creates an annotation with the given content.
     *
     * @param content model content to display
     */
    public Annotation(T content) {
        this.content = content;
//        setOpaque(true);
//        setBackground(Color.CYAN);
//        setBorder(BorderFactory.createLineBorder(Color.white, 1));

        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                } else {
                    dragOffset = e.getPoint();
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }

                // Snap annotation to half-grid if parent panel supports it
                // The center of the annotation is snapped to half-grid increments
                java.awt.Container parent = SwingUtilities.getAncestorOfClass(StateMachinePanel.class, Annotation.this);
                if (parent instanceof StateMachinePanel panel && panel.isSnapToGrid()) {
                    int grid = panel.getGridSize();
                    if (grid > 0) {
                        int x = getX();
                        int y = getY();
                        int width = getWidth();
                        int height = getHeight();

                        // Calculate center position
                        int centerX = x + width / 2;
                        int centerY = y + height / 2;

                        // Snap center to half-grid (grid/2) increments
                        int half = Math.max(1, grid / 2);
                        int snappedCenterX = Math.round((float) centerX / half) * half;
                        int snappedCenterY = Math.round((float) centerY / half) * half;

                        // Calculate new top-left position from snapped center
                        int snappedX = snappedCenterX - width / 2;
                        int snappedY = snappedCenterY - height / 2;

                        setLocation(snappedX, snappedY);
                        if (getParent() != null) {
                            getParent().repaint();
                        }
                    }
                }

                dragOffset = null;
                // Mark document dirty after annotation move/interaction
                java.awt.Window w = SwingUtilities.getWindowAncestor(Annotation.this);
                if (w instanceof pws.editor.PWSEditor pe) pe.markDocumentDirty();
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragOffset != null && getParent() != null) {
                    Point parentPoint = SwingUtilities.convertPoint(Annotation.this, e.getPoint(), getParent());
                    setLocation(parentPoint.x - dragOffset.x, parentPoint.y - dragOffset.y);
                    getParent().repaint();
                }
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    /**
     * Returns the current annotation content.
     *
     * @return current annotation content
     */
    public T getContent() {
        return content;
    }

    /**
     * Updates the annotation content and recalculates its size.
     *
     * @param content new model content
     */
    public void setContent(T content) {
        this.content = content;
        // calls setSize(getPreferredSize()) after updating the content
        Dimension d = getPreferredSize();
        setSize(d);
        revalidate();
        repaint();
    }

    /**
     * Method to build the text for display.
     * Subclasses can override this to add extra formatting.
     *
     * @return display text for the annotation
     */
    protected String buildDisplayText() {
        return (content == null ? "" : content.toString());
    }

    @Override
    public Dimension getPreferredSize() {
        // Ottieni il font attualmente impostato
        Font f = getFont();
        if (f == null) {
            // If the font is null, provide a default font.
            f = new Font("Dialog", Font.PLAIN, 12);
            setFont(f);
        }
        Font derived = f.deriveFont(Font.PLAIN, 12f);
        FontMetrics fm = getFontMetrics(derived);
        // Use buildDisplayText() instead of getContentAsString()
        int width = fm.stringWidth(buildDisplayText()) + 10;
        int height = fm.getHeight() + 10;
        return new Dimension(width, height);
    }

    /**
     * Default popup behavior: no menu is shown.
     *
     * @param e triggering mouse event
     */
    protected void showPopup(MouseEvent e) {
        // No popup defined in base class.
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setFont(getFont().deriveFont(Font.PLAIN, 12f));
        g2d.setColor(Color.BLACK);
        String text = buildDisplayText();
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getAscent();
        int x = (getWidth() - textWidth) / 2;
        int y = (getHeight() + textHeight) / 2 - 2;
        g2d.drawString(text, x, y);
    }
}
