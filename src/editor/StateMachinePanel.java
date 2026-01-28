package editor;

import machinery.*;
import pws.PWSTransition;
import utility.DraggableTriggerLabel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Canvas panel for drawing and editing state machines. */
public class StateMachinePanel extends JPanel implements MouseListener, MouseMotionListener {

    protected StateMachine stateMachine;
    protected StateInterface selectedState = null;
    protected Point dragOffset = null;
    // Canvas drag state (panning)
    protected boolean canvasDragActive = false;
    protected Point canvasDragLast = null;
    protected int canvasDragAccumX = 0;
    protected int canvasDragAccumY = 0;
    private StateMachineEditor owningEditor = null;

    // Link mode fields
    protected boolean linkMode = false;
    protected StateInterface transitionSourceState = null;

    // Fields for control handle (for bending transitions)
    protected TransitionInterface selectedTransitionForControl = null;
    protected Point controlDragOffset = null;

    // Flag to show control handles (for self-loop endpoints in PWS)
    protected boolean showControlHandles = true;

    // Flag to enable/disable edit mode (controls green control point visibility)
    protected boolean editMode = true;

    // Grid and snapping
    protected boolean showGrid = true;
    protected boolean snapToGrid = true;
    protected int gridSize = 20;

    public boolean isShowGrid() {
        return showGrid;
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
        repaint();
    }

    public boolean isSnapToGrid() {
        return snapToGrid;
    }

    public void setSnapToGrid(boolean snapToGrid) {
        this.snapToGrid = snapToGrid;
    }

    public int getGridSize() {
        return gridSize;
    }

    public void setGridSize(int gridSize) {
        this.gridSize = gridSize;
        repaint();
    }
// Initial transition mode flag
    protected boolean initialTransitionMode = false;

    // Graphic constants
    protected final int DIAMETER = 50;
    protected final int RADIUS = DIAMETER / 2;
    // Reduce pseudostate diameter to one third of the normal diameter.
    protected final int PSEUDO_DIAMETER = DIAMETER / 3;
    protected final int PSEUDO_RADIUS = PSEUDO_DIAMETER / 2;

    // Map to hold trigger labels for transitions
    protected Map<TransitionInterface, DraggableTriggerLabel> triggerLabels = new HashMap<>();

    public StateMachinePanel(StateMachine stateMachine) {
        this.stateMachine = stateMachine;
        setBackground(Color.WHITE);
        // Using null layout to allow absolute positioning of draggable labels.
        setLayout(null);
        addMouseListener(this);
        addMouseMotionListener(this);
        // Enable keyboard focus so we can capture arrow keys
        setFocusable(true);
        // --- WASD-key bindings to pan the entire diagram ---
        InputMap im = getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), "moveLeft");   // A = left
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), "moveRight");  // D = right
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0), "moveUp");     // W = up
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0), "moveDown");   // S = down

        am.put("moveLeft",  new AbstractAction() { public void actionPerformed(ActionEvent e){ translateAllStates(-1, 0); }});
        am.put("moveRight", new AbstractAction() { public void actionPerformed(ActionEvent e){ translateAllStates( 1, 0); }});
        am.put("moveUp",    new AbstractAction() { public void actionPerformed(ActionEvent e){ translateAllStates(0,-1); }});
        am.put("moveDown",  new AbstractAction() { public void actionPerformed(ActionEvent e){ translateAllStates(0, 1); }});
    }

    public void setStateMachine(StateMachine sm) {
        this.stateMachine = sm;
    }

    public boolean isShowControlHandles() {
        return showControlHandles;
    }

    public void setShowControlHandles(boolean showControlHandles) {
        this.showControlHandles = showControlHandles;
        repaint();
    }

    public boolean isEditMode() {
        return editMode;
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        repaint();
    }

    /** Optional back-reference to the editor (for menu sync). */
    public void setOwningEditor(StateMachineEditor editor) {
        this.owningEditor = editor;
    }

    public void enableLinkMode() {
        linkMode = true;
        transitionSourceState = null;
        System.out.println("Link mode activated. Select source node, then target node.");
    }

    public void enableInitialTransitionMode() {
        initialTransitionMode = true;
        System.out.println("Initial transition mode activated: click on a target to create an autonomous transition from the pseudo‑state.");
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (showGrid) {
            drawGrid(g);
        }
        // Remove previous trigger labels before redrawing
        // removeAllTriggerLabels();
        drawStates(g);
        drawTransitions(g);
        updateTriggerLabels(); // Add draggable labels for transitions with triggers
        // (Focus glow removed — visual hint disabled during resizing)
    }

    // Remove and clear all trigger labels
    private void removeAllTriggerLabels() {
        for (DraggableTriggerLabel label : triggerLabels.values()) {
            remove(label);
        }
        triggerLabels.clear();
    }

    /**
     * Updates/creates draggable trigger labels for each triggerable transition.
     */
    protected void updateTriggerLabels() {
        // Remove labels for transitions no longer triggerable or removed
        Iterator<Map.Entry<TransitionInterface, DraggableTriggerLabel>> it = triggerLabels.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<TransitionInterface, DraggableTriggerLabel> entry = it.next();
            TransitionInterface t = entry.getKey();
            if (!t.isTriggerable() || !stateMachine.getTransitions().contains(t)) {
                remove(entry.getValue());
                it.remove();
            }
        }
        // Iterate over transitions to update or create trigger labels.
        for (TransitionInterface t : stateMachine.getTransitions()) {
            if (t.isTriggerable()) {
                // Get the label for this transition, if it exists.
                DraggableTriggerLabel label = triggerLabels.get(t);
                if (label == null) {
                    // Create a new label, associating it with the transition.
                    label = new DraggableTriggerLabel(t.getTriggerEvent(), t);
                    // Compute default position.
                    State sourceState = (State) t.getSource();
                    State targetState = (State) t.getTarget();
                    Point sourcePos = sourceState.getPosition();
                    Point targetPos = targetState.getPosition();
                    int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                    int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                    Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
                    Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
                    Point cp = ((Transition) t).getControlPoint();
                    if (cp == null) {
                        cp = computeControlPoint(centerSource, centerTarget);
                        ((Transition) t).setControlPoint(cp);
                    }
                    Point p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
                    Point p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
                    int defaultX = (int) ((p0.x + 2 * cp.x + p2.x) / 4.0) + 5;
                    int defaultY = (int) ((p0.y + 2 * cp.y + p2.y) / 4.0) - 5;
                    // Add to parent first, then revalidate to allow proper size calculation
                    add(label);
                    label.setVisible(true);
                    label.revalidate();
                    Dimension size = label.getPreferredSize();

                    // Snap initial placement: support half-grid snapping when enabled
                    int placedX = defaultX;
                    int placedY = defaultY;
                    if (this.isSnapToGrid()) {
                        int grid = this.getGridSize();
                        if (grid > 0) {
                            int w = size.width;
                            int h = size.height;
                            int centerX = defaultX + w / 2;
                            int centerY = defaultY + h / 2;
                            float half = grid / 2f;
                            int snappedCenterX = Math.round(centerX / half) * Math.round(half);
                            int snappedCenterY = Math.round(centerY / half) * Math.round(half);
                            placedX = snappedCenterX - w / 2;
                            placedY = snappedCenterY - h / 2;
                        }
                    }

                    label.setBounds(placedX, placedY, size.width, size.height);
                    triggerLabels.put(t, label);
                } else {
                    // Update text in case it changed.
                    label.setText("<html><b><u>" + t.getTriggerEvent() + "</u></b></html>");
                    // If the transition already has a stored trigger offset, use it.
                    Point offset = t.getTriggerOffset();
                    if (offset != null) {
                        Dimension size = label.getPreferredSize();
                        label.setBounds(offset.x, offset.y, size.width, size.height);
                    }
                    // Otherwise, do not modify its location; let it remain at the user-defined position.
                }
            }
        }
    }

    protected void drawStates(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        Stroke oldStroke = g2d.getStroke();
        g2d.setStroke(new BasicStroke(1.0f));
        List<StateInterface> states = stateMachine.getStates();
        for (StateInterface state : states) {
            Point pos = ((State) state).getPosition();
            int x = pos.x;
            int y = pos.y;
            if (state.getName().equals("PseudoState")) {
                g2d.setColor(Color.BLACK);
                g2d.fillOval(x, y, PSEUDO_DIAMETER, PSEUDO_DIAMETER);
                g2d.setColor(Color.BLACK);
                g2d.drawOval(x, y, PSEUDO_DIAMETER, PSEUDO_DIAMETER);
            } else {
                g2d.setColor(Color.WHITE);
                g2d.fillOval(x, y, DIAMETER, DIAMETER);
                if (state == selectedState || state == transitionSourceState) {
                    g2d.setColor(Color.RED);
                } else {
                    g2d.setColor(Color.BLACK);
                }
                g2d.drawOval(x, y, DIAMETER, DIAMETER);
                String name = state.getName();
                FontMetrics fm = g2d.getFontMetrics();
                int textWidth = fm.stringWidth(name);
                int textHeight = fm.getHeight();
                int textX = x + (DIAMETER - textWidth) / 2;
                int textY = y + (DIAMETER - textHeight) / 2 + fm.getAscent();
                g2d.drawString(name, textX, textY);
            }
        }
        g2d.setStroke(oldStroke);
    }

    protected void drawTransitions(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        List<TransitionInterface> transitions = stateMachine.getTransitions();
        for (TransitionInterface t : transitions) {
            drawSingleTransition(g2d, t);
        }
    }

    protected void drawSingleTransition(Graphics2D g2d, TransitionInterface t) {
        // Get centers of source and target.
        State sourceState = (State) t.getSource();
        State targetState = (State) t.getTarget();
        Point sourcePos = sourceState.getPosition();
        Point targetPos = targetState.getPosition();
        int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
        int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
        Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
        Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);

        // Compute control point for the curve.
        Point cp = ((Transition) t).getControlPoint();
        if (cp == null) {
            cp = computeControlPoint(centerSource, centerTarget);
            ((Transition) t).setControlPoint(cp);
        }
        Point p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
        Point p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);

        // Draw the transition curve.
        QuadCurve2D.Double curve = new QuadCurve2D.Double();
        curve.setCurve(p0.x, p0.y, cp.x, cp.y, p2.x, p2.y);
        
        // Check if this transition is disabled
        boolean disabled = false;
        if (t instanceof machinery.Transition trans) {
            disabled = !trans.isEnabled();
        }
        
        // Save original stroke and render disabled transitions in gray with thicker stroke
        Stroke oldStroke = g2d.getStroke();
        if (disabled) {
            g2d.setStroke(new BasicStroke(2.0f));
            g2d.setColor(Color.LIGHT_GRAY);
        } else {
            g2d.setColor(Color.BLACK);
        }
        g2d.draw(curve);
        drawArrowHead(g2d, p0, p2, cp);
        g2d.setStroke(oldStroke);

        // For triggerable transitions, the draggable label is now used.
        if (!t.isTriggerable()) {
            int circleRadius = 5;
            // Gray fill only for initial transition from pseudostate, white (hollow) otherwise
            if (sourceState.getName().equals("PseudoState")) {
                g2d.setColor(Color.LIGHT_GRAY);
            } else {
                g2d.setColor(Color.WHITE);
            }
            g2d.fillOval(p0.x - circleRadius, p0.y - circleRadius, circleRadius * 2, circleRadius * 2);
            // Outline: gray for disabled, black otherwise
            g2d.setColor(disabled ? Color.LIGHT_GRAY : Color.BLACK);
            g2d.drawOval(p0.x - circleRadius, p0.y - circleRadius, circleRadius * 2, circleRadius * 2);
        }

        // Draw control handle only if edit mode is enabled
        if (editMode) {
            drawControlHandle(g2d, cp);
        }
    }

    private Point computeControlPoint(Point centerSource, Point centerTarget) {
        int midX = (centerSource.x + centerTarget.x) / 2;
        int midY = (centerSource.y + centerTarget.y) / 2;
        int offset = 20;
        double dx = centerTarget.x - centerSource.x;
        double dy = centerTarget.y - centerSource.y;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance == 0) distance = 1;
        int controlX = (int) (midX - offset * (dy / distance));
        int controlY = (int) (midY + offset * (dx / distance));
        return new Point(controlX, controlY);
    }
    


    private Point computeStartPoint(Point centerSource, Point cp, int offset) {
        double d0x = cp.x - centerSource.x;
        double d0y = cp.y - centerSource.y;
        Point2D.Double norm = normalize(d0x, d0y);
        int x = (int) (centerSource.x + norm.x * offset);
        int y = (int) (centerSource.y + norm.y * offset);
        return new Point(x, y);
    }

    private Point computeEndPoint(Point centerTarget, Point cp, int offset) {
        double d1x = centerTarget.x - cp.x;
        double d1y = centerTarget.y - cp.y;
        Point2D.Double norm = normalize(d1x, d1y);
        int x = (int) (centerTarget.x - norm.x * offset);
        int y = (int) (centerTarget.y - norm.y * offset);
        return new Point(x, y);
    }

    protected Point2D.Double normalize(double dx, double dy) {
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length == 0) return new Point2D.Double(0, 0);
        return new Point2D.Double(dx / length, dy / length);
    }

    private void drawArrowHead(Graphics2D g2d, Point p0, Point p2, Point control) {
        double tangentX = p2.x - control.x;
        double tangentY = p2.y - control.y;
        double theta = Math.atan2(tangentY, tangentX);
        int arrowHeadLength = 10;
        int arrowHeadAngle = 45;
        int x1 = (int) (p2.x - arrowHeadLength * Math.cos(theta - Math.toRadians(arrowHeadAngle)));
        int y1 = (int) (p2.y - arrowHeadLength * Math.sin(theta - Math.toRadians(arrowHeadAngle)));
        int x2 = (int) (p2.x - arrowHeadLength * Math.cos(theta + Math.toRadians(arrowHeadAngle)));
        int y2 = (int) (p2.y - arrowHeadLength * Math.sin(theta + Math.toRadians(arrowHeadAngle)));
        g2d.drawLine(p2.x, p2.y, x1, y1);
        g2d.drawLine(p2.x, p2.y, x2, y2);
    }

    private void drawControlHandle(Graphics2D g2d, Point cp) {
        g2d.setColor(Color.GREEN);
        int handleRadius = 5;
        g2d.fillOval(cp.x - handleRadius, cp.y - handleRadius, handleRadius * 2, handleRadius * 2);
    }

    protected StateInterface getStateAt(Point p) {
        List<StateInterface> states = stateMachine.getStates();
        for (StateInterface state : states) {
            Point pos = ((State) state).getPosition();
            int diam = state.getName().equals("PseudoState") ? PSEUDO_DIAMETER : DIAMETER;
            Rectangle rect = new Rectangle(pos.x, pos.y, diam, diam);
            if (rect.contains(p)) {
                return state;
            }
        }
        return null;
    }

    /**
     * Shift every state position and transition control point by the given pixel delta.
     */
    protected void translateAllStatesByPixels(int px, int py) {
        if (px == 0 && py == 0) return;

        for (StateInterface s : stateMachine.getStates()) {
            State st = (State) s;
            // Move state position
            Point pos = st.getPosition();
            st.setPosition(new Point(pos.x + px, pos.y + py));

            /* ---- Move state‑level annotations if present ---- */
            // Works only if we’re in a PWS environment, but safe to attempt cast
            if (st instanceof pws.PWSState pwsSt) {
                if (pwsSt.getAnnotation() != null) {
                    Rectangle r = pwsSt.getAnnotation().getBounds();
                    pwsSt.getAnnotation().setBounds(r.x + px, r.y + py, r.width, r.height);
                }
            }
        }

        for (TransitionInterface t : stateMachine.getTransitions()) {
            Transition tr = (Transition) t;
            // Move Bézier control point
            Point cp = tr.getControlPoint();
            if (cp != null) cp.translate(px, py);

            // Move trigger label offset (for draggable trigger labels)
            if (t.getTriggerOffset() != null) {
                Point off = t.getTriggerOffset();
                t.setTriggerOffset(new Point(off.x + px, off.y + py));
            }

            /* ---- Move transition‑level annotations if present ---- */
            if (t instanceof PWSTransition pwt) {
                // Guard annotation
                if (pwt.getGuardAnnotation() != null) {
                    Rectangle r = pwt.getGuardAnnotation().getBounds();
                    pwt.getGuardAnnotation().setBounds(r.x + px, r.y + py, r.width, r.height);
                }
                // Action annotation
                if (pwt.getActionAnnotation() != null) {
                    Rectangle r = pwt.getActionAnnotation().getBounds();
                    pwt.getActionAnnotation().setBounds(r.x + px, r.y + py, r.width, r.height);
                }
                // Transition semantics annotation
                if (pwt.getSemanticsAnnotation() != null) {
                    Rectangle r = pwt.getSemanticsAnnotation().getBounds();
                    pwt.getSemanticsAnnotation().setBounds(r.x + px, r.y + py, r.width, r.height);
                }
            }
        }

        for (DraggableTriggerLabel label : triggerLabels.values()) {
            Rectangle r = label.getBounds();
            label.setBounds(r.x + px, r.y + py, r.width, r.height);
        }

        repaint();
    }

    /**
     * Shift every state position and transition control point by the given delta in grid steps.
     */
    private void translateAllStates(int dx, int dy) {
        // Treat dx/dy as grid steps; convert to pixels using current grid size
        translateAllStatesByPixels(dx * gridSize, dy * gridSize);
    }

    protected void panCanvasTo(Point newPoint) {
        if (canvasDragLast == null) return;
        int dx = newPoint.x - canvasDragLast.x;
        int dy = newPoint.y - canvasDragLast.y;

        if (snapToGrid && gridSize > 0) {
            canvasDragAccumX += dx;
            canvasDragAccumY += dy;
            int moveX = (canvasDragAccumX / gridSize) * gridSize;
            int moveY = (canvasDragAccumY / gridSize) * gridSize;
            if (moveX != 0 || moveY != 0) {
                translateAllStatesByPixels(moveX, moveY);
                canvasDragAccumX -= moveX;
                canvasDragAccumY -= moveY;
            }
        } else {
            translateAllStatesByPixels(dx, dy);
        }
        canvasDragLast = newPoint;
    }

    // --------------- MOUSE EVENT HANDLING ---------------

    @Override
    public void mousePressed(MouseEvent e) {
        Point p = e.getPoint();
        // Ensure the panel gains focus so its arrow‑key bindings have priority
        if (!hasFocus()) {
            requestFocusInWindow();
        }
        // Debug: mouse press details — commented out to reduce console noise
        // System.out.println("mousePressed: button=" + e.getButton() + ", point=" + p + ", isPopupTrigger=" + e.isPopupTrigger());
        if (e.getButton() == MouseEvent.BUTTON1) {
            for (TransitionInterface t : stateMachine.getTransitions()) {
                Point cp = ((Transition) t).getControlPoint();
                if (cp != null && p.distance(cp) <= 8) {
                    selectedTransitionForControl = t;
                    controlDragOffset = new Point(e.getX() - cp.x, e.getY() - cp.y);
                    return;
                }
            }
        }
        if (initialTransitionMode) {
            handleInitialTransitionMode(e);
            return;
        }
        if (linkMode) {
            handleLinkMode(e);
            return;
        }
        if (SwingUtilities.isRightMouseButton(e)) {
            handleRightClick(e);
            return;
        }
        StateInterface state = getStateAt(p);
        if (state != null) {
            selectedState = state;
            Point posState = ((State) state).getPosition();
            dragOffset = new Point(p.x - posState.x, p.y - posState.y);
            canvasDragActive = false;
            canvasDragLast = null;
        } else {
            selectedState = null;
            if (SwingUtilities.isLeftMouseButton(e)) {
                canvasDragActive = true;
                canvasDragLast = p;
                canvasDragAccumX = 0;
                canvasDragAccumY = 0;
            }
        }
        repaint();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
            handleRightClick(e);
            return;
        }

        // Snap states and control points to grid on release (snap using state center)
        if (snapToGrid) {
            if (selectedState != null) {
                State st = (State) selectedState;
                Point pos = st.getPosition();
                int d = st.getName().equals("PseudoState") ? PSEUDO_DIAMETER : DIAMETER;
                int r = d / 2;
                Point center = new Point(pos.x + r, pos.y + r);
                Point snappedCenter = snap(center);
                st.setPosition(new Point(snappedCenter.x - r, snappedCenter.y - r));
            }
            if (selectedTransitionForControl != null) {
                Transition tr = (Transition) selectedTransitionForControl;
                Point cp = tr.getControlPoint();
                if (cp != null) {
                    tr.setControlPoint(snap(cp));
                }
            }
        }

        selectedTransitionForControl = null;
        controlDragOffset = null;
        selectedState = null;
        dragOffset = null;
        canvasDragActive = false;
        canvasDragLast = null;
        canvasDragAccumX = 0;
        canvasDragAccumY = 0;
        repaint();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (canvasDragActive && canvasDragLast != null) {
            panCanvasTo(e.getPoint());
            return;
        }
        if (selectedTransitionForControl != null && controlDragOffset != null) {
            Point newPoint = e.getPoint();
            Point newControlPoint = new Point(newPoint.x - controlDragOffset.x, newPoint.y - controlDragOffset.y);
            ((Transition) selectedTransitionForControl).setControlPoint(newControlPoint);
            repaint();
        } else if (selectedState != null && dragOffset != null) {
            Point newPoint = e.getPoint();
            // raw position (top-left corner)
            int rawX = newPoint.x - dragOffset.x;
            int rawY = newPoint.y - dragOffset.y;

            machinery.State st = (machinery.State) selectedState;

            // choose the correct diameter (normal state vs pseudo-state)
            int d = st.getName().equals("PseudoState") ? PSEUDO_DIAMETER : DIAMETER;
            int r = d / 2;

            if (snapToGrid) {
                // current center relative to the new position
                Point center = new Point(rawX + r, rawY + r);
                // snap the center to the grid
                Point snappedCenter = snap(center);
                // recompute the top-left corner from the snapped center
                rawX = snappedCenter.x - r;
                rawY = snappedCenter.y - r;
            }

            st.setPosition(new Point(rawX, rawY));
            repaint();
        }

    }

    @Override public void mouseClicked(MouseEvent e) { 
        // Left-button double-click to rename a state
        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
            StateInterface state = getStateAt(e.getPoint());
            if (state == null) return;
            // Do not rename the pseudostate
            if (state.getName().equals("PseudoState")) {
                return;
            }
            String oldName = state.getName();
            String newName = JOptionPane.showInputDialog(this, "Rename state:", oldName);
            if (newName != null && !newName.trim().isEmpty()) {
                String trimmed = newName.trim();
                ((State) state).setName(trimmed);
                repaint();
                java.awt.Container win = javax.swing.SwingUtilities.getWindowAncestor(this);
                if (win instanceof pws.editor.PWSEditor pe) {
                    pe.renameAssemblyStateName(stateMachine, oldName, trimmed);
                }
            }
        }
    }
    @Override public void mouseEntered(MouseEvent e) { }
    @Override public void mouseExited(MouseEvent e) { }
    @Override public void mouseMoved(MouseEvent e) { }

    private void handleInitialTransitionMode(MouseEvent e) {
        StateInterface clickedState = getStateAt(e.getPoint());
        if (clickedState != null && !clickedState.getName().equals("PseudoState")) {
            StateInterface pseudo = stateMachine.getStates().stream()
                    .filter(s -> s.getName().equals("PseudoState"))
                    .findFirst().orElse(null);
            if (pseudo != null) {
                boolean exists = stateMachine.getTransitions().stream()
                        .anyMatch(t -> t.getSource() == pseudo && t.getTarget() == clickedState && t.isAutonomous());
                if (!exists) {
                    TransitionInterface newTransition = new Transition(pseudo, clickedState, true, "");
                    stateMachine.addTransition(newTransition);
                        // Schedule semantics recalculation if inside PWSEditor
                        java.awt.Container win = javax.swing.SwingUtilities.getWindowAncestor(this);
                        if (win instanceof pws.editor.PWSEditor) {
                            ((pws.editor.PWSEditor) win).scheduleSemanticsRecalculation();
                        }
                    // Debug: initial transition creation (commented out)
                    // System.out.println("Initial transition created: PseudoState -> " + clickedState.getName());
                } else {
                    JOptionPane.showMessageDialog(this, "An initial transition for this state already exists.");
                }
            } else {
                JOptionPane.showMessageDialog(this, "PseudoState not found.");
            }
        } else {
            JOptionPane.showMessageDialog(this, "Select a valid state (not PseudoState).");
        }
        initialTransitionMode = false;
        repaint();
    }

    private void handleLinkMode(MouseEvent e) {
        StateInterface clickedState = getStateAt(e.getPoint());
        if (clickedState != null) {
                if (transitionSourceState == null) {
                transitionSourceState = clickedState;
                // Debug: link mode source selection (commented out)
                // System.out.println("Link mode: Source state selected: " + transitionSourceState.getName());
            } else {
                if (clickedState != transitionSourceState) {
                    // Prevent pseudostate as target
                    if (clickedState.getName().equals("PseudoState")) {
                        JOptionPane.showMessageDialog(this, "Cannot create transition to PseudoState.");
                        linkMode = false;
                        transitionSourceState = null;
                        return;
                    }
                    String trigger = JOptionPane.showInputDialog(this, "Enter trigger event (leave blank for autonomous):");
                    boolean autonomous = transitionSourceState.getName().equals("PseudoState") ||
                            (trigger == null || trigger.trim().isEmpty());
                    TransitionInterface newTransition = new Transition(transitionSourceState, clickedState, autonomous, trigger);
                    stateMachine.addTransition(newTransition);
                    // Schedule semantics recalculation if inside PWSEditor
                    java.awt.Container win = javax.swing.SwingUtilities.getWindowAncestor(this);
                    if (win instanceof pws.editor.PWSEditor) {
                        ((pws.editor.PWSEditor) win).scheduleSemanticsRecalculation();
                    }
                    // Debug: transition creation in link mode (commented out)
                    // System.out.println("Link mode: Transition created from " +
                    //        transitionSourceState.getName() + " to " + clickedState.getName());
                } else {
                    // System.out.println("Link mode: Target same as source. Ignored.");
                }
                linkMode = false;
                transitionSourceState = null;
            }
            repaint();
        } else {
            System.out.println("Link mode: No state found at " + e.getPoint());
        }
    }

    private void handleRightClick(MouseEvent e) {
        Point p = e.getPoint();
        for (TransitionInterface t : stateMachine.getTransitions()) {
            Point cp = ((Transition) t).getControlPoint();
            if (cp != null && p.distance(cp) <= 8) {
                showTransitionPopup(e, t);
                return;
            }
        }
        StateInterface state = getStateAt(p);
        if (state != null) {
            showPopupMenuForState(e, state);
        } else {
            showEmptySpacePopup(e);
        }
    }

    private void showEmptySpacePopup(MouseEvent e) {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem addStateItem = new JMenuItem("Add State");
        addStateItem.addActionListener(ae -> {
            addNewStateAt(e.getPoint());
        });
        popup.add(addStateItem);

        popup.addSeparator();

        JCheckBoxMenuItem editModeItem = new JCheckBoxMenuItem("Edit mode", isEditMode());
        editModeItem.addActionListener(ae -> {
            boolean enabled = editModeItem.isSelected();
            if (owningEditor != null) {
                owningEditor.setEditModeEnabled(enabled);
            } else {
                java.awt.Window w = SwingUtilities.getWindowAncestor(this);
                if (w instanceof StateMachineEditor sme) {
                    sme.setEditModeEnabled(enabled);
                } else {
                    setEditMode(enabled);
                }
            }
        });
        popup.add(editModeItem);
        popup.show(this, e.getX(), e.getY());
    }

    private void addNewStateAt(Point clickPoint) {
        String name = generateDefaultStateName(stateMachine);
        int diameter = DIAMETER;
        int radius = diameter / 2;
        int centerX = clickPoint.x;
        int centerY = clickPoint.y;
        if (snapToGrid) {
            Point snapped = snap(new Point(centerX, centerY));
            centerX = snapped.x;
            centerY = snapped.y;
        }
        Point topLeft = new Point(centerX - radius, centerY - radius);
        stateMachine.addState(new State(name, topLeft));
        // Schedule semantics recalculation if running inside the PWSEditor
        java.awt.Container win = javax.swing.SwingUtilities.getWindowAncestor(this);
        if (win instanceof pws.editor.PWSEditor) {
            ((pws.editor.PWSEditor) win).scheduleSemanticsRecalculation();
        }
        repaint();
    }

    private String generateDefaultStateName(StateMachine machine) {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (StateInterface si : machine.getStates()) {
            names.add(si.getName());
        }
        String base = "S";
        if (!names.contains(base)) {
            return base;
        }
        int idx = 1;
        while (names.contains(base + idx)) {
            idx++;
        }
        return base + idx;
    }

    private void showTransitionPopup(MouseEvent e, TransitionInterface t) {
        JPopupMenu popup = new JPopupMenu();

        // Enable/Disable transition (only for PWSTransition)
        if (t instanceof pws.PWSTransition pt) {
            String toggleText = pt.isEnabled() ? "Disable Transition" : "Enable Transition";
            JMenuItem toggleEnableItem = new JMenuItem(toggleText);
            toggleEnableItem.addActionListener(ae -> {
                pt.setEnabled(!pt.isEnabled());
                // Schedule semantics recalculation
                java.awt.Container win = javax.swing.SwingUtilities.getWindowAncestor(this);
                if (win instanceof pws.editor.PWSEditor pwsEd) {
                    pwsEd.scheduleSemanticsRecalculation();
                    if (pwsEd.getDocument() != null) {
                        pwsEd.getDocument().setDirty(true);
                    }
                }
                if (owningEditor != null) {
                    owningEditor.notifyModelChanged();
                }
                repaint();
            });
            popup.add(toggleEnableItem);
        } else if (t instanceof machinery.Transition trans) {
            // For base Transition in component machines
            String toggleText = trans.isEnabled() ? "Disable Transition" : "Enable Transition";
            JMenuItem toggleEnableItem = new JMenuItem(toggleText);
            toggleEnableItem.addActionListener(ae -> {
                trans.setEnabled(!trans.isEnabled());
                // Schedule semantics recalculation - search up the hierarchy for PWSEditor
                java.awt.Window win = javax.swing.SwingUtilities.getWindowAncestor(this);
                if (win instanceof pws.editor.PWSEditor pwsEd) {
                    pwsEd.scheduleSemanticsRecalculation();
                    if (pwsEd.getDocument() != null) {
                        pwsEd.getDocument().setDirty(true);
                    }
                    // Also repaint the controller panel to reflect changes
                    if (pwsEd.getBaseEditor() != null) {
                        pwsEd.getBaseEditor().getStateMachinePanel().repaint();
                    }
                }
                if (owningEditor != null) {
                    owningEditor.notifyModelChanged();
                }
                repaint();
            });
            popup.add(toggleEnableItem);
        }
        
        popup.addSeparator();

        // Menu item to delete the transition
        JMenuItem deleteItem = new JMenuItem("Delete Transition");
        deleteItem.addActionListener(ae -> {
            // Use the helper method to remove the transition and all associated references
            deleteTransition(t);
            // Schedule semantics recalculation if inside PWSEditor
            java.awt.Container win = javax.swing.SwingUtilities.getWindowAncestor(this);
            if (win instanceof pws.editor.PWSEditor) {
                ((pws.editor.PWSEditor) win).scheduleSemanticsRecalculation();
            }
            revalidate();
            repaint();
        });
        popup.add(deleteItem);

        // If needed, add more entries here to manage annotations (guard, action, semantics),
        // for example "Toggle Guard Annotation", "Toggle Action Annotation", etc.
        // (These entries could be the same for initial and normal transitions if behavior should be uniform.)

        popup.show(this, e.getX(), e.getY());
    }

    private void showPopupMenuForState(MouseEvent e, StateInterface state) {
        // Debug: showPopupMenuForState invoked (commented out)
        // System.out.println("showPopupMenuForState invoked for state: " + state.getName());
        JPopupMenu popup = new JPopupMenu();

        if (state.getName().equals("PseudoState")) {
            // Pseudo-state menu
            JMenuItem addInitialTransItem = new JMenuItem("Add initial transition");
            addInitialTransItem.addActionListener(ae -> {
                enableInitialTransitionMode();
            });
            popup.add(addInitialTransItem);

            JMenuItem infoItem = new JMenuItem("Pseudostate not deletable");
            infoItem.setEnabled(false);
            popup.add(infoItem);
        } else {
            // Normal state menu
            // Create transition item - only if state is not the pseudostate
            JMenuItem createTransItem = new JMenuItem("Create transition: choose arrival state");
            createTransItem.addActionListener(ae -> {
                enableLinkModeWithSource(state);
            });
            popup.add(createTransItem);

            JMenuItem deleteItem = new JMenuItem("Delete");
            deleteItem.addActionListener(ae -> {
                    System.out.println("Delete menu item clicked for state: " + state.getName());
                int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to delete state: " + state.getName() + "?",
                    "Confirm deletion", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    boolean removed = stateMachine.getStates().remove(state);
                    if (removed) {
                        stateMachine.getTransitions().removeIf(t -> t.getSource() == state || t.getTarget() == state);
                        System.out.println("The state and related transitions have been removed from the structure.");
                    } else {
                        System.out.println("Error: state not found in the structure.");
                    }
                        // Schedule semantics recalculation if inside PWSEditor
                        java.awt.Container win = javax.swing.SwingUtilities.getWindowAncestor(this);
                        if (win instanceof pws.editor.PWSEditor) {
                            ((pws.editor.PWSEditor) win).scheduleSemanticsRecalculation();
                        }
                        repaint();
                }
            });
            popup.add(deleteItem);
        }
        // Debug: showing popup menu (commented out)
        // System.out.println("Showing popup menu for state: " + state.getName());
        popup.show(this, e.getX(), e.getY());
    }

    /**
     * Enable link mode with a predefined source state.
     */
    public void enableLinkModeWithSource(StateInterface sourceState) {
        linkMode = true;
        transitionSourceState = sourceState;
    }

    /**
     * Removes transition t from the state machine and clears references to it:
     * - Removes associated annotations (if the transition is a PWSTransition)
     * - Removes t from the global transitions list
     * - Removes t from the source state's outgoing transitions list
     *   and from the target state's incoming transitions list.
     */
    private void deleteTransition(TransitionInterface t) {
//        // If t is a PWSTransition, clear its associated annotations.
//        if (t instanceof PWSTransition) {
//            clearAnnotationsForTransition((PWSTransition) t);
//        }
        // Remove the transition from the global list.
        stateMachine.getTransitions().remove(t);

        // Remove the transition from the source state's outgoing transitions list.
        StateInterface source = t.getSource();
        if (source != null && source.getOutgoingTransitions() != null) {
            source.getOutgoingTransitions().remove(t);
        }

        // Remove the transition from the target state's incoming transitions list.
        StateInterface target = t.getTarget();
        if (target != null && target.getIncomingTransitions() != null) {
            target.getIncomingTransitions().remove(t);
        }
        // Schedule semantics recalculation if inside PWSEditor
        java.awt.Container win = javax.swing.SwingUtilities.getWindowAncestor(this);
        if (win instanceof pws.editor.PWSEditor) {
            ((pws.editor.PWSEditor) win).scheduleSemanticsRecalculation();
        }
    }


    private void drawGrid(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setColor(new Color(230, 230, 230)); // light gray grid
        int w = getWidth();
        int h = getHeight();
        for (int x = 0; x < w; x += gridSize) {
            g2d.drawLine(x, 0, x, h);
        }
        for (int y = 0; y < h; y += gridSize) {
            g2d.drawLine(0, y, w, y);
        }
    }

    protected int snap(int value) {
        return Math.round(value / (float) gridSize) * gridSize;
    }

    protected Point snap(Point p) {
        return new Point(snap(p.x), snap(p.y));
    }

}
