package editor;

import machinery.*;
import pws.PWSTransition;
import utility.DraggableTriggerLabel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.Stroke;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    protected boolean dragMoved = false;

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
    public int getStateDiameter() {
        return DIAMETER;
    }

    public void setStateDiameter(int diameter) {
        if (diameter < 20) return;
        DIAMETER = diameter;
        RADIUS = DIAMETER / 2;
        PSEUDO_DIAMETER = Math.max(6, DIAMETER / 3);
        PSEUDO_RADIUS = PSEUDO_DIAMETER / 2;
        repaint();
    }

    public float getStateBorderThickness() {
        return stateBorderThickness;
    }

    public void setStateBorderThickness(float thickness) {
        if (thickness <= 0f) return;
        stateBorderThickness = thickness;
        repaint();
    }

    public float getStateFontSize() {
        return stateFontSize;
    }

    public void setStateFontSize(float size) {
        if (size <= 0f) return;
        stateFontSize = size;
        Font base = getFont();
        if (base == null) {
            base = new Font("Dialog", Font.PLAIN, 12);
        }
        Font derived = base.deriveFont(stateFontSize);
        setFont(derived);
        for (DraggableTriggerLabel label : triggerLabels.values()) {
            if (label != null) {
                label.setFont(derived);
                label.setSize(label.getPreferredSize());
            }
        }
        repaint();
    }
// Initial transition mode flag
    protected boolean initialTransitionMode = false;

    // Graphic constants (configurable)
    protected int DIAMETER = 50;
    protected int RADIUS = DIAMETER / 2;
    // Reduce pseudostate diameter to one third of the normal diameter.
    protected int PSEUDO_DIAMETER = DIAMETER / 3;
    protected int PSEUDO_RADIUS = PSEUDO_DIAMETER / 2;
    protected float stateBorderThickness = 1.0f;
    protected float stateFontSize = 12f;
    private static final Color COMPONENT_FAIL_STATE_BORDER_COLOR = new Color(204, 170, 0);
    private static final Color COMPONENT_DEADLOCK_BORDER_COLOR = new Color(180, 0, 0);
    private static final Color COMPONENT_UNREACHABLE_BORDER_COLOR = new Color(204, 170, 0);
    private static final float[] COMPONENT_FAIL_STATE_DASH = new float[] {6f, 4f};

    // Map to hold trigger labels for transitions
    protected Map<TransitionInterface, DraggableTriggerLabel> triggerLabels = new HashMap<>();

    // Pseudostate alias support (UI-only)
    protected final List<Point> pseudoStateAliases = new ArrayList<>();
    protected final Map<TransitionInterface, Integer> pseudoAliasByTransition = new HashMap<>();
    protected int hitPseudoAliasIndex = -1;
    protected int selectedPseudoAliasIndex = -1;
    protected int menuPseudoAliasIndex = -1;
    protected int initialTransitionAliasIndex = -1;
    protected int transitionSourcePseudoAliasIndex = -1;

    protected enum DragTransitionKind {
        GUARD_TRIGGERED,
        EVENT_TRIGGERED,
        INITIAL_TRIGGERED
    }

    protected boolean dragTransitionArmed = false;
    protected boolean dragTransitionActive = false;
    protected DragTransitionKind dragTransitionKind = null;
    protected StateInterface dragTransitionSourceState = null;
    protected int dragTransitionSourcePseudoAliasIndex = -1;
    protected Point dragTransitionCurrentPoint = null;

    private static final Color SELECTION_RECT_STROKE = new Color(27, 88, 166);
    private static final Color SELECTION_RECT_FILL = new Color(27, 88, 166, 48);
    private static final int EXPORT_SELECTION_MARGIN = 16;
    private final Set<StateInterface> selectedStates = new LinkedHashSet<>();
    private final Set<Integer> selectedPseudoAliases = new LinkedHashSet<>();
    private final Set<Component> selectedComponents = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<TransitionInterface> selectedTransitions = new LinkedHashSet<>();
    private boolean selectionBoxActive = false;
    private Point selectionBoxAnchor = null;
    private Rectangle selectionBoxRect = null;
    private final Set<StateInterface> selectionBoxBaseStates = new LinkedHashSet<>();
    private final Set<Integer> selectionBoxBaseAliases = new LinkedHashSet<>();
    private final Set<Component> selectionBoxBaseComponents = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<TransitionInterface> selectionBoxBaseTransitions = new LinkedHashSet<>();
    private boolean selectionDragActive = false;
    private Point selectionDragAnchor = null;
    private final Map<StateInterface, Point> selectionDragStateOrigins = new HashMap<>();
    private final Map<Integer, Point> selectionDragAliasOrigins = new HashMap<>();
    private final Map<Component, Rectangle> selectionDragComponentOrigins = new IdentityHashMap<>();
    private final Map<TransitionInterface, Point> selectionDragTransitionControlOrigins = new HashMap<>();
    private boolean renderSelectionHighlights = true;
    private boolean exportSelectionOnlyActive = false;
    private final Map<Component, Boolean> exportSelectionComponentVisibility = new IdentityHashMap<>();

    public static class AliasData {
        public final List<Point> pseudoAliases = new ArrayList<>();
        public final Map<String, Integer> pseudoAliasByTransition = new LinkedHashMap<>();
    }

    public StateMachinePanel(StateMachine stateMachine) {
        this.stateMachine = stateMachine;
        setBackground(Color.WHITE);
        // Using null layout to allow absolute positioning of draggable labels.
        setLayout(null);
        addMouseListener(this);
        addMouseMotionListener(this);
        javax.swing.ToolTipManager.sharedInstance().registerComponent(this);
        // Enable keyboard focus so we can capture arrow keys
        setFocusable(true);
        // --- WASD-key bindings to pan the entire diagram ---
        InputMap im = getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), "moveLeft");   // A = left
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), "moveRight");  // D = right
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0), "moveUp");     // W = up
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0), "moveDown");   // S = down

        am.put("moveLeft",  new AbstractAction() { public void actionPerformed(ActionEvent e){ translateAllStates(-1, 0); markOwningEditorDirty(); }});
        am.put("moveRight", new AbstractAction() { public void actionPerformed(ActionEvent e){ translateAllStates( 1, 0); markOwningEditorDirty(); }});
        am.put("moveUp",    new AbstractAction() { public void actionPerformed(ActionEvent e){ translateAllStates(0,-1); markOwningEditorDirty(); }});
        am.put("moveDown",  new AbstractAction() { public void actionPerformed(ActionEvent e){ translateAllStates(0, 1); markOwningEditorDirty(); }});
    }

    public void setStateMachine(StateMachine sm) {
        this.stateMachine = sm;
        pseudoStateAliases.clear();
        pseudoAliasByTransition.clear();
        hitPseudoAliasIndex = -1;
        selectedPseudoAliasIndex = -1;
        menuPseudoAliasIndex = -1;
        initialTransitionAliasIndex = -1;
        transitionSourcePseudoAliasIndex = -1;
        clearBaseDragTransitionState();
        clearObjectSelection();
        clearSelectionInteractionState();
    }

    public void setRenderSelectionHighlights(boolean renderSelectionHighlights) {
        this.renderSelectionHighlights = renderSelectionHighlights;
        repaint();
    }

    public boolean beginSelectionOnlyExport() {
        if (!hasObjectSelection()) {
            return false;
        }
        exportSelectionOnlyActive = true;
        exportSelectionComponentVisibility.clear();
        for (Component c : getComponents()) {
            if (!isSelectableComponent(c)) continue;
            if (isComponentSelectedForObjectSelection(c)) continue;
            if (!c.isVisible()) continue;
            exportSelectionComponentVisibility.put(c, Boolean.TRUE);
            c.setVisible(false);
        }
        return true;
    }

    public void endSelectionOnlyExport() {
        if (!exportSelectionOnlyActive) {
            return;
        }
        for (Map.Entry<Component, Boolean> entry : exportSelectionComponentVisibility.entrySet()) {
            Component c = entry.getKey();
            Boolean wasVisible = entry.getValue();
            if (c != null && wasVisible != null) {
                c.setVisible(wasVisible);
            }
        }
        exportSelectionComponentVisibility.clear();
        exportSelectionOnlyActive = false;
    }

    protected boolean isSelectionOnlyExportActive() {
        return exportSelectionOnlyActive;
    }

    protected boolean isStateSelectedForObjectSelection(StateInterface state) {
        return selectedStates.contains(state);
    }

    protected boolean isPseudoAliasSelectedForObjectSelection(int aliasIndex) {
        return selectedPseudoAliases.contains(aliasIndex);
    }

    protected boolean isTransitionSelectedForObjectSelection(TransitionInterface transition) {
        return selectedTransitions.contains(transition);
    }

    protected boolean isComponentSelectedForObjectSelection(Component component) {
        return selectedComponents.contains(component);
    }

    public boolean hasObjectSelection() {
        pruneSelection();
        return !selectedStates.isEmpty()
                || !selectedPseudoAliases.isEmpty()
                || !selectedComponents.isEmpty()
                || !selectedTransitions.isEmpty();
    }

    public Rectangle getSelectionBoundsForExport() {
        pruneSelection();
        Rectangle bounds = null;
        for (StateInterface state : selectedStates) {
            if (!(state instanceof State st)) continue;
            Point pos = st.getPosition();
            if (pos == null) continue;
            int diameter = isPseudoState(state) ? PSEUDO_DIAMETER : DIAMETER;
            Rectangle r = new Rectangle(pos.x, pos.y, diameter, diameter);
            bounds = (bounds == null) ? new Rectangle(r) : bounds.union(r);
        }

        for (Integer aliasIndex : selectedPseudoAliases) {
            if (aliasIndex == null || aliasIndex < 0 || aliasIndex >= pseudoStateAliases.size()) continue;
            Point pos = pseudoStateAliases.get(aliasIndex);
            Rectangle r = new Rectangle(pos.x, pos.y, PSEUDO_DIAMETER, PSEUDO_DIAMETER);
            bounds = (bounds == null) ? new Rectangle(r) : bounds.union(r);
        }

        for (TransitionInterface t : selectedTransitions) {
            Rectangle r = getTransitionVisualBounds(t);
            if (r == null) continue;
            bounds = (bounds == null) ? new Rectangle(r) : bounds.union(r);
        }

        for (Component c : selectedComponents) {
            if (c == null || c.getParent() != this || !c.isVisible()) continue;
            Rectangle r = c.getBounds();
            bounds = (bounds == null) ? new Rectangle(r) : bounds.union(r);
        }

        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return null;
        }

        int x = Math.max(0, bounds.x - EXPORT_SELECTION_MARGIN);
        int y = Math.max(0, bounds.y - EXPORT_SELECTION_MARGIN);
        int maxX = Math.min(getWidth(), bounds.x + bounds.width + EXPORT_SELECTION_MARGIN);
        int maxY = Math.min(getHeight(), bounds.y + bounds.height + EXPORT_SELECTION_MARGIN);
        int w = Math.max(1, maxX - x);
        int h = Math.max(1, maxY - y);
        return new Rectangle(x, y, w, h);
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

    protected void markOwningEditorDirty() {
        java.awt.Window w = SwingUtilities.getWindowAncestor(this);
        if (w instanceof pws.editor.PWSEditor pe) {
            pe.markDocumentDirty();
            return;
        }
        if (owningEditor != null) {
            owningEditor.markDocumentDirty();
        }
    }

    public void notifyTriggerEventChanged() {
        markOwningEditorDirty();
        java.awt.Window w = SwingUtilities.getWindowAncestor(this);
        if (w instanceof pws.editor.PWSEditor pe) {
            pe.scheduleSemanticsRecalculation();
        }
        repaint();
    }

    /**
     * Optional hook for child components (annotations, trigger labels) that want
     * to delegate mouse handling to a panel-level selection controller.
     */
    public boolean handleSelectableComponentMousePressed(Component component, MouseEvent e) {
        if (!isSelectableComponent(component) || e == null) {
            return false;
        }
        if (initialTransitionMode || linkMode) {
            return false;
        }
        if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
            return false;
        }
        if (!SwingUtilities.isLeftMouseButton(e)) {
            return false;
        }
        if (isBaseDragCommandModifierDown(e)) {
            return false;
        }

        Point panelPoint = SwingUtilities.convertPoint(component, e.getPoint(), this);
        dragMoved = false;
        if (!hasFocus()) {
            requestFocusInWindow();
        }
        clearSelectionInteractionState();

        if (e.isShiftDown()) {
            toggleComponentSelection(component);
            selectedState = null;
            selectedPseudoAliasIndex = -1;
            dragOffset = null;
            canvasDragActive = false;
            canvasDragLast = null;
            canvasDragAccumX = 0;
            canvasDragAccumY = 0;
            repaint();
            return true;
        }

        pruneSelection();
        if (!selectedComponents.contains(component)) {
            return false;
        }
        selectedState = null;
        selectedPseudoAliasIndex = -1;
        dragOffset = null;
        canvasDragActive = false;
        canvasDragLast = null;
        canvasDragAccumX = 0;
        canvasDragAccumY = 0;
        beginSelectionDrag(panelPoint);
        repaint();
        return true;
    }

    /**
     * Optional hook for child components (annotations, trigger labels) that want
     * to delegate mouse handling to a panel-level selection controller.
     */
    public boolean handleSelectableComponentMouseDragged(Component component, MouseEvent e) {
        if (!isSelectableComponent(component) || e == null) {
            return false;
        }
        if (!selectionDragActive) {
            return e.isShiftDown() && SwingUtilities.isLeftMouseButton(e);
        }
        Point panelPoint = SwingUtilities.convertPoint(component, e.getPoint(), this);
        updateSelectionDrag(panelPoint);
        repaint();
        return true;
    }

    /**
     * Optional hook for child components (annotations, trigger labels) that want
     * to delegate mouse handling to a panel-level selection controller.
     */
    public boolean handleSelectableComponentMouseReleased(Component component, MouseEvent e) {
        if (!isSelectableComponent(component) || e == null) {
            return false;
        }
        if (!selectionDragActive) {
            return SwingUtilities.isLeftMouseButton(e);
        }
        finishSelectionDrag();
        selectedState = null;
        selectedPseudoAliasIndex = -1;
        dragOffset = null;
        canvasDragActive = false;
        canvasDragLast = null;
        canvasDragAccumX = 0;
        canvasDragAccumY = 0;
        repaint();
        if (dragMoved) {
            markOwningEditorDirty();
        }
        dragMoved = false;
        return true;
    }

    public void enableLinkMode() {
        linkMode = true;
        transitionSourceState = null;
        System.out.println("Link mode activated. Select source node, then target node.");
    }

    public void enableInitialTransitionMode() {
        initialTransitionMode = true;
        System.out.println("Initial transition mode activated: click on a target to create a triggerable '_init' transition from the pseudo‑state.");
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
        drawBaseDragTransitionPreview(g);
        updateTriggerLabels(); // Add draggable labels for transitions with triggers
        // (Focus glow removed — visual hint disabled during resizing)
        if (isComponentMachine() && hasComponentDeadlocks()) {
            Graphics2D g2d = (Graphics2D) g;
            Stroke old = g2d.getStroke();
            g2d.setColor(new Color(180, 0, 0));
            g2d.setStroke(new BasicStroke(2.0f));
            g2d.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
            g2d.setStroke(old);
        }
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        drawSelectionOverlay(g);
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
        boolean selectionOnlyExport = isSelectionOnlyExportActive();
        // Remove alias mappings for transitions that no longer exist
        Iterator<Map.Entry<TransitionInterface, Integer>> aliasIt = pseudoAliasByTransition.entrySet().iterator();
        while (aliasIt.hasNext()) {
            Map.Entry<TransitionInterface, Integer> entry = aliasIt.next();
            if (!stateMachine.getTransitions().contains(entry.getKey())) {
                aliasIt.remove();
            }
        }
        // Remove labels for transitions no longer triggerable, removed, or hidden (_init)
        Iterator<Map.Entry<TransitionInterface, DraggableTriggerLabel>> it = triggerLabels.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<TransitionInterface, DraggableTriggerLabel> entry = it.next();
            TransitionInterface t = entry.getKey();
            if (!t.isTriggerable() || isInitTrigger(t) || !stateMachine.getTransitions().contains(t)) {
                remove(entry.getValue());
                it.remove();
            }
        }
        // Iterate over transitions to update or create trigger labels.
        for (TransitionInterface t : stateMachine.getTransitions()) {
            if (t.isTriggerable() && !isInitTrigger(t)) {
                // Get the label for this transition, if it exists.
                DraggableTriggerLabel label = triggerLabels.get(t);
                if (selectionOnlyExport) {
                    boolean keep = isTransitionSelectedForObjectSelection(t)
                            || (label != null && isComponentSelectedForObjectSelection(label));
                    if (!keep) {
                        if (label != null) {
                            label.setVisible(false);
                        }
                        continue;
                    }
                }
                if (label == null) {
                    // Create a new label, associating it with the transition.
                    label = new DraggableTriggerLabel(t.getTriggerEvent(), t);
                    if (getFont() != null) {
                        label.setFont(getFont());
                    }
                    // Compute default position.
                    State sourceState = (State) t.getSource();
                    State targetState = (State) t.getTarget();
                    Point sourcePos = getStatePositionForTransition(sourceState, t, true);
                    Point targetPos = getStatePositionForTransition(targetState, t, false);
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
                    label.setVisible(true);
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

    private boolean isInitTrigger(TransitionInterface t) {
        if (t == null || t.getTriggerEvent() == null) {
            return false;
        }
        if (!"_init".equals(t.getTriggerEvent())) {
            return false;
        }
        StateInterface src = t.getSource();
        return src != null && "PseudoState".equals(src.getName());
    }

    protected void drawStates(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        Stroke oldStroke = g2d.getStroke();
        Stroke normalStroke = new BasicStroke(stateBorderThickness);
        boolean selectionOnlyExport = isSelectionOnlyExportActive();
        float failThickness = Math.max(2.0f, stateBorderThickness + 1.5f);
        float deadlockThickness = Math.max(2.0f, stateBorderThickness + 1.0f);
        Stroke failStroke = new BasicStroke(
            failThickness,
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND,
            10.0f,
            COMPONENT_FAIL_STATE_DASH,
            0.0f
        );
        Stroke deadlockStroke = new BasicStroke(deadlockThickness);
        g2d.setStroke(normalStroke);
        java.util.Set<StateInterface> reachable = isComponentMachine()
                ? getComponentReachableStates()
                : java.util.Collections.emptySet();
        List<StateInterface> states = stateMachine.getStates();
        for (StateInterface state : states) {
            if (selectionOnlyExport && !isStateSelectedForObjectSelection(state)) {
                continue;
            }
            Point pos = ((State) state).getPosition();
            int x = pos.x;
            int y = pos.y;
            if (state.getName().equals("PseudoState")) {
                boolean isSelected = (renderSelectionHighlights
                        && (selectedStates.contains(state) || state == transitionSourceState));
                g2d.setColor(Color.BLACK);
                g2d.fillOval(x, y, PSEUDO_DIAMETER, PSEUDO_DIAMETER);
                g2d.setColor(Color.BLACK);
                g2d.setStroke(normalStroke);
                g2d.drawOval(x, y, PSEUDO_DIAMETER, PSEUDO_DIAMETER);
                if (isSelected) {
                    g2d.setColor(Color.RED);
                    g2d.drawOval(x - 2, y - 2, PSEUDO_DIAMETER + 4, PSEUDO_DIAMETER + 4);
                }
            } else {
                g2d.setColor(Color.WHITE);
                g2d.fillOval(x, y, DIAMETER, DIAMETER);
                boolean isSelected = (renderSelectionHighlights
                        && (selectedStates.contains(state) || state == transitionSourceState));
                boolean isUnreachable = isComponentMachine() && !reachable.contains(state);
                boolean isDeadlock = false;
                if (!isUnreachable && isComponentMachine()) {
                    boolean hasEnabledOutgoing = false;
                    for (TransitionInterface t : stateMachine.getTransitions()) {
                        if (t != null && t.getSource() == state && isTransitionEnabled(t)) {
                            hasEnabledOutgoing = true;
                            break;
                        }
                    }
                    isDeadlock = !hasEnabledOutgoing;
                }
                boolean isManualFail = isComponentFailState(state) && !isUnreachable;
                if (isManualFail) {
                    g2d.setStroke(failStroke);
                    g2d.setColor(COMPONENT_FAIL_STATE_BORDER_COLOR);
                    g2d.drawOval(x, y, DIAMETER, DIAMETER);
                    if (isSelected) {
                        g2d.setStroke(normalStroke);
                        g2d.setColor(Color.RED);
                        g2d.drawOval(x - 2, y - 2, DIAMETER + 4, DIAMETER + 4);
                    }
                } else if (isDeadlock) {
                    g2d.setStroke(deadlockStroke);
                    g2d.setColor(COMPONENT_DEADLOCK_BORDER_COLOR);
                    g2d.drawOval(x, y, DIAMETER, DIAMETER);
                    if (isSelected) {
                        g2d.setStroke(normalStroke);
                        g2d.setColor(Color.RED);
                        g2d.drawOval(x - 2, y - 2, DIAMETER + 4, DIAMETER + 4);
                    }
                } else {
                    g2d.setStroke(normalStroke);
                    g2d.setColor(isSelected ? Color.RED : Color.BLACK);
                    g2d.drawOval(x, y, DIAMETER, DIAMETER);
                }
                if (isUnreachable) {
                    Stroke prev = g2d.getStroke();
                    g2d.setStroke(new BasicStroke(2.0f));
                    g2d.setColor(COMPONENT_UNREACHABLE_BORDER_COLOR);
                    g2d.drawOval(x - 2, y - 2, DIAMETER + 4, DIAMETER + 4);
                    g2d.setStroke(prev);
                }
                String name = state.getName();
                FontMetrics fm = g2d.getFontMetrics();
                int textWidth = fm.stringWidth(name);
                int textHeight = fm.getHeight();
                int textX = x + (DIAMETER - textWidth) / 2;
                int textY = y + (DIAMETER - textHeight) / 2 + fm.getAscent();
                g2d.setColor(isSelected ? Color.RED : Color.BLACK);
                g2d.drawString(name, textX, textY);
            }
        }
        for (int i = 0; i < pseudoStateAliases.size(); i++) {
            if (selectionOnlyExport && !isPseudoAliasSelectedForObjectSelection(i)) {
                continue;
            }
            Point aliasPos = pseudoStateAliases.get(i);
            g2d.setColor(Color.BLACK);
            g2d.fillOval(aliasPos.x, aliasPos.y, PSEUDO_DIAMETER, PSEUDO_DIAMETER);
            g2d.setColor(Color.BLACK);
            g2d.setStroke(normalStroke);
            g2d.drawOval(aliasPos.x, aliasPos.y, PSEUDO_DIAMETER, PSEUDO_DIAMETER);
            if (renderSelectionHighlights && selectedPseudoAliases.contains(i)) {
                g2d.setColor(Color.RED);
                g2d.drawOval(aliasPos.x - 2, aliasPos.y - 2, PSEUDO_DIAMETER + 4, PSEUDO_DIAMETER + 4);
            }
        }
        g2d.setStroke(oldStroke);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (!isComponentMachine()) return null;
        StateInterface state = getStateAt(e.getPoint());
        if (state == null) return null;
        if (isComponentUnreachableState(state)) {
            return "Unreachable state (no path from the initial pseudostate). This should be fixed.";
        }
        if (isComponentDeadlockState(state)) {
            return "Deadlock state (no enabled outgoing transitions).";
        }
        if (isComponentFailState(state)) {
            return "Fail state (marked manually).";
        }
        return null;
    }

    protected void drawTransitions(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        boolean selectionOnlyExport = isSelectionOnlyExportActive();
        List<TransitionInterface> transitions = stateMachine.getTransitions();
        for (TransitionInterface t : transitions) {
            if (selectionOnlyExport && !isTransitionSelectedForObjectSelection(t)) {
                continue;
            }
            drawSingleTransition(g2d, t);
        }
    }

    protected void drawSingleTransition(Graphics2D g2d, TransitionInterface t) {
        // Get centers of source and target.
        State sourceState = (State) t.getSource();
        State targetState = (State) t.getTarget();
        Point sourcePos = getStatePositionForTransition(sourceState, t, true);
        Point targetPos = getStatePositionForTransition(targetState, t, false);
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
        if (renderSelectionHighlights && selectedTransitions.contains(t)) {
            g2d.setStroke(new BasicStroke(disabled ? 3.0f : 2.0f));
            g2d.setColor(Color.RED);
            g2d.draw(curve);
            drawArrowHead(g2d, p0, p2, cp);
        }
        g2d.setStroke(oldStroke);

        // For triggerable transitions, the draggable label is now used.
        if (!t.isTriggerable() && !"PseudoState".equals(sourceState.getName())) {
            int circleRadius = 5;
            g2d.setColor(Color.WHITE);
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

    private Point getTransitionControlPointForRendering(TransitionInterface t) {
        if (!(t instanceof Transition transition)) {
            return null;
        }
        Point cp = transition.getControlPoint();
        if (cp != null) {
            return cp;
        }
        StateInterface source = t.getSource();
        StateInterface target = t.getTarget();
        if (!(source instanceof State) || !(target instanceof State)) {
            return null;
        }
        Point sourcePos = getStatePositionForTransition(source, t, true);
        Point targetPos = getStatePositionForTransition(target, t, false);
        if (sourcePos == null || targetPos == null) {
            return null;
        }
        int sourceCenterOffset = isPseudoState(source) ? PSEUDO_RADIUS : RADIUS;
        int targetCenterOffset = isPseudoState(target) ? PSEUDO_RADIUS : RADIUS;
        Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
        Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
        cp = computeControlPoint(centerSource, centerTarget);
        transition.setControlPoint(cp);
        return cp;
    }

    private Point[] computeTransitionEndpoints(TransitionInterface t) {
        if (t == null || t.getSource() == null || t.getTarget() == null) {
            return null;
        }
        Point cp = getTransitionControlPointForRendering(t);
        if (cp == null) {
            return null;
        }
        Point sourcePos = getStatePositionForTransition(t.getSource(), t, true);
        Point targetPos = getStatePositionForTransition(t.getTarget(), t, false);
        if (sourcePos == null || targetPos == null) {
            return null;
        }
        int sourceCenterOffset = isPseudoState(t.getSource()) ? PSEUDO_RADIUS : RADIUS;
        int targetCenterOffset = isPseudoState(t.getTarget()) ? PSEUDO_RADIUS : RADIUS;
        Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
        Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
        Point p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
        Point p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
        return new Point[] {p0, p2};
    }

    private QuadCurve2D.Double buildTransitionCurve(TransitionInterface t) {
        Point cp = getTransitionControlPointForRendering(t);
        Point[] endpoints = computeTransitionEndpoints(t);
        if (cp == null || endpoints == null || endpoints[0] == null || endpoints[1] == null) {
            return null;
        }
        QuadCurve2D.Double curve = new QuadCurve2D.Double();
        curve.setCurve(endpoints[0].x, endpoints[0].y, cp.x, cp.y, endpoints[1].x, endpoints[1].y);
        return curve;
    }

    private boolean isPointNearCurve(QuadCurve2D.Double curve, Point point, double tolerance) {
        if (curve == null || point == null) return false;
        PathIterator iterator = new FlatteningPathIterator(curve.getPathIterator(null), 1.5);
        double[] coords = new double[6];
        double lastX = 0;
        double lastY = 0;
        while (!iterator.isDone()) {
            int segment = iterator.currentSegment(coords);
            if (segment == PathIterator.SEG_MOVETO) {
                lastX = coords[0];
                lastY = coords[1];
            } else if (segment == PathIterator.SEG_LINETO) {
                double dist = Line2D.ptSegDist(lastX, lastY, coords[0], coords[1], point.x, point.y);
                if (dist <= tolerance) {
                    return true;
                }
                lastX = coords[0];
                lastY = coords[1];
            }
            iterator.next();
        }
        return false;
    }

    private TransitionInterface getTransitionAt(Point p) {
        if (p == null) return null;
        List<TransitionInterface> transitions = stateMachine.getTransitions();
        for (int i = transitions.size() - 1; i >= 0; i--) {
            TransitionInterface t = transitions.get(i);
            QuadCurve2D.Double curve = buildTransitionCurve(t);
            if (isPointNearCurve(curve, p, 7.0)) {
                return t;
            }
        }
        return null;
    }

    private boolean transitionIntersectsRect(TransitionInterface t, Rectangle rect) {
        if (t == null || rect == null) return false;
        QuadCurve2D.Double curve = buildTransitionCurve(t);
        if (curve == null) return false;
        if (curve.intersects(rect.x, rect.y, rect.width, rect.height)) {
            return true;
        }
        if (rect.contains(curve.getP1()) || rect.contains(curve.getP2()) || rect.contains(curve.getCtrlPt())) {
            return true;
        }
        return false;
    }

    private Rectangle getTransitionVisualBounds(TransitionInterface t) {
        QuadCurve2D.Double curve = buildTransitionCurve(t);
        if (curve == null) return null;
        Rectangle bounds = curve.getBounds();
        bounds.grow(8, 8);
        DraggableTriggerLabel label = triggerLabels.get(t);
        if (label != null && label.getParent() == this && label.isVisible()) {
            bounds = bounds.union(label.getBounds());
        }
        return bounds;
    }

    private boolean isComponentMachine() {
        return !(stateMachine instanceof pws.PWSStateMachine);
    }

    private boolean hasComponentDeadlocks() {
        if (!isComponentMachine()) return false;
        for (StateInterface state : stateMachine.getStates()) {
            if (isComponentDeadlockState(state)) {
                return true;
            }
        }
        return false;
    }

    private boolean isComponentDeadlockState(StateInterface state) {
        if (!isComponentMachine() || state == null) return false;
        if ("PseudoState".equals(state.getName())) return false;
        if (isComponentUnreachableState(state)) return false;
        boolean hasEnabledOutgoing = false;
        for (TransitionInterface t : stateMachine.getTransitions()) {
            if (t != null && t.getSource() == state && isTransitionEnabled(t)) {
                hasEnabledOutgoing = true;
                break;
            }
        }
        return !hasEnabledOutgoing;
    }

    private boolean isComponentFailState(StateInterface state) {
        if (!isComponentMachine() || state == null) return false;
        if ("PseudoState".equals(state.getName())) return false;
        if (state instanceof State s) {
            return s.isFailState();
        }
        return false;
    }

    private boolean isComponentUnreachableState(StateInterface state) {
        if (!isComponentMachine() || state == null) return false;
        if ("PseudoState".equals(state.getName())) return false;
        return !getComponentReachableStates().contains(state);
    }

    private java.util.Set<StateInterface> getComponentReachableStates() {
        java.util.Set<StateInterface> reachable = new java.util.HashSet<>();
        if (!isComponentMachine() || stateMachine == null) return reachable;
        java.util.ArrayDeque<StateInterface> queue = new java.util.ArrayDeque<>();
        for (StateInterface s : stateMachine.getInitialStates()) {
            if (s != null && !"PseudoState".equals(s.getName()) && reachable.add(s)) {
                queue.add(s);
            }
        }
        while (!queue.isEmpty()) {
            StateInterface current = queue.poll();
            for (TransitionInterface t : stateMachine.getTransitions()) {
                if (t == null || t.getSource() != current || !isTransitionEnabled(t)) {
                    continue;
                }
                StateInterface target = t.getTarget();
                if (target == null || "PseudoState".equals(target.getName())) {
                    continue;
                }
                if (reachable.add(target)) {
                    queue.add(target);
                }
            }
        }
        return reachable;
    }

    private boolean isTransitionEnabled(TransitionInterface t) {
        if (t instanceof machinery.Transition trans) {
            return trans.isEnabled();
        }
        return true;
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

    protected boolean isPseudoState(StateInterface state) {
        return state != null && "PseudoState".equals(state.getName());
    }

    protected StateInterface getPseudoStateOrNull() {
        if (stateMachine == null) return null;
        StateInterface pseudo = stateMachine.getPseudoState();
        if (pseudo != null) return pseudo;
        for (StateInterface s : stateMachine.getStates()) {
            if (isPseudoState(s)) return s;
        }
        return null;
    }

    protected int getTotalPseudoAliasCount() {
        return (getPseudoStateOrNull() == null ? 0 : 1) + pseudoStateAliases.size();
    }

    protected int getPseudoAliasIndexAt(Point p) {
        for (int i = 0; i < pseudoStateAliases.size(); i++) {
            Point pos = pseudoStateAliases.get(i);
            Rectangle rect = new Rectangle(pos.x, pos.y, PSEUDO_DIAMETER, PSEUDO_DIAMETER);
            if (rect.contains(p)) {
                return i;
            }
        }
        return -1;
    }

    protected void createPseudoAliasAt(Point clickPoint) {
        int radius = PSEUDO_DIAMETER / 2;
        int centerX = clickPoint.x;
        int centerY = clickPoint.y;
        if (snapToGrid) {
            Point snapped = snap(new Point(centerX, centerY));
            centerX = snapped.x;
            centerY = snapped.y;
        }
        Point topLeft = new Point(centerX - radius, centerY - radius);
        StateInterface pseudo = getPseudoStateOrNull();
        if (pseudo != null) {
            Point pseudoPos = ((State) pseudo).getPosition();
            if (pseudoPos != null && pseudoPos.equals(topLeft)) {
                return;
            }
        }
        for (Point alias : pseudoStateAliases) {
            if (alias.equals(topLeft)) {
                return;
            }
        }
        pseudoStateAliases.add(topLeft);
        repaint();
    }

    protected void rememberPseudoAliasForTransition(TransitionInterface t, int aliasIndex) {
        if (t == null) return;
        if (aliasIndex >= 0 && aliasIndex < pseudoStateAliases.size()) {
            pseudoAliasByTransition.put(t, aliasIndex);
        } else {
            pseudoAliasByTransition.remove(t);
        }
    }

    protected void clearPseudoAliasForTransition(TransitionInterface t) {
        if (t == null) return;
        pseudoAliasByTransition.remove(t);
    }

    protected void removePseudoAliasAt(int aliasIndex) {
        if (aliasIndex < 0 || aliasIndex >= pseudoStateAliases.size()) return;
        pseudoStateAliases.remove(aliasIndex);
        Iterator<Map.Entry<TransitionInterface, Integer>> it = pseudoAliasByTransition.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<TransitionInterface, Integer> entry = it.next();
            Integer idx = entry.getValue();
            if (idx == null) {
                it.remove();
            } else if (idx == aliasIndex) {
                it.remove();
            } else if (idx > aliasIndex) {
                entry.setValue(idx - 1);
            }
        }
        if (selectedPseudoAliases.isEmpty()) {
            return;
        }
        Set<Integer> updated = new LinkedHashSet<>();
        for (Integer idx : selectedPseudoAliases) {
            if (idx == null) continue;
            if (idx == aliasIndex) continue;
            updated.add(idx > aliasIndex ? idx - 1 : idx);
        }
        selectedPseudoAliases.clear();
        selectedPseudoAliases.addAll(updated);
    }

    protected void deletePrimaryPseudoAlias() {
        if (pseudoStateAliases.isEmpty()) return;
        StateInterface pseudo = getPseudoStateOrNull();
        if (pseudo == null) return;
        Point promoted = new Point(pseudoStateAliases.get(0));
        removePseudoAliasAt(0);
        ((State) pseudo).setPosition(promoted);
    }

    public AliasData exportAliasData() {
        AliasData data = new AliasData();
        for (Point pos : pseudoStateAliases) {
            if (pos != null) {
                data.pseudoAliases.add(new Point(pos));
            }
        }
        for (Map.Entry<TransitionInterface, Integer> entry : pseudoAliasByTransition.entrySet()) {
            TransitionInterface t = entry.getKey();
            Integer aliasIndex = entry.getValue();
            if (!(t instanceof Transition) || aliasIndex == null) continue;
            String id = ((Transition) t).getId();
            if (id == null || id.isBlank()) continue;
            if (aliasIndex < 0 || aliasIndex >= pseudoStateAliases.size()) continue;
            data.pseudoAliasByTransition.put(id, aliasIndex);
        }
        return data;
    }

    public void importAliasData(AliasData data) {
        pseudoStateAliases.clear();
        pseudoAliasByTransition.clear();
        if (data == null) {
            repaint();
            return;
        }
        for (Point pos : data.pseudoAliases) {
            if (pos != null) {
                pseudoStateAliases.add(new Point(pos));
            }
        }
        if (!data.pseudoAliasByTransition.isEmpty()) {
            Map<String, TransitionInterface> transitionById = new HashMap<>();
            for (TransitionInterface t : stateMachine.getTransitions()) {
                if (t instanceof Transition) {
                    String id = ((Transition) t).getId();
                    if (id != null && !id.isBlank()) {
                        transitionById.put(id, t);
                    }
                }
            }
            for (Map.Entry<String, Integer> entry : data.pseudoAliasByTransition.entrySet()) {
                String id = entry.getKey();
                Integer aliasIndex = entry.getValue();
                if (id == null || aliasIndex == null) continue;
                if (aliasIndex < 0 || aliasIndex >= pseudoStateAliases.size()) continue;
                TransitionInterface t = transitionById.get(id);
                if (t != null) {
                    rememberPseudoAliasForTransition(t, aliasIndex);
                }
            }
        }
        repaint();
    }

    private void pruneSelection() {
        selectedStates.removeIf(s -> s == null || !stateMachine.getStates().contains(s));
        selectedPseudoAliases.removeIf(i -> i == null || i < 0 || i >= pseudoStateAliases.size());
        selectedComponents.removeIf(c -> c == null || c.getParent() != this || !isSelectableComponent(c));
        selectedTransitions.removeIf(t -> t == null || !stateMachine.getTransitions().contains(t));
    }

    protected boolean isSelectableComponent(Component component) {
        return component instanceof DraggableTriggerLabel;
    }

    private boolean isStateOrAliasSelected(StateInterface state, int aliasIndex) {
        if (state == null) return false;
        if (isPseudoState(state) && aliasIndex >= 0) {
            return selectedPseudoAliases.contains(aliasIndex);
        }
        return selectedStates.contains(state);
    }

    private void clearObjectSelection() {
        selectedStates.clear();
        selectedPseudoAliases.clear();
        selectedComponents.clear();
        selectedTransitions.clear();
    }

    private void toggleStateOrAlias(StateInterface state, int aliasIndex) {
        if (state == null) return;
        if (isPseudoState(state) && aliasIndex >= 0) {
            if (!selectedPseudoAliases.remove(aliasIndex)) {
                selectedPseudoAliases.add(aliasIndex);
            }
            return;
        }
        if (!selectedStates.remove(state)) {
            selectedStates.add(state);
        }
    }

    private void toggleComponentSelection(Component component) {
        if (component == null || !isSelectableComponent(component)) return;
        if (!selectedComponents.remove(component)) {
            selectedComponents.add(component);
        }
    }

    private void toggleTransitionSelection(TransitionInterface transition) {
        if (transition == null) return;
        if (!selectedTransitions.remove(transition)) {
            selectedTransitions.add(transition);
        }
    }

    private void clearSelectionInteractionState() {
        selectionBoxActive = false;
        selectionBoxAnchor = null;
        selectionBoxRect = null;
        selectionBoxBaseStates.clear();
        selectionBoxBaseAliases.clear();
        selectionBoxBaseComponents.clear();
        selectionBoxBaseTransitions.clear();

        selectionDragActive = false;
        selectionDragAnchor = null;
        selectionDragStateOrigins.clear();
        selectionDragAliasOrigins.clear();
        selectionDragComponentOrigins.clear();
        selectionDragTransitionControlOrigins.clear();
    }

    private void beginSelectionDrag(Point anchor) {
        pruneSelection();
        if (anchor == null || (!hasObjectSelection())) {
            selectionDragActive = false;
            selectionDragAnchor = null;
            return;
        }
        selectionDragActive = true;
        selectionDragAnchor = new Point(anchor);
        selectionDragStateOrigins.clear();
        selectionDragAliasOrigins.clear();
        selectionDragComponentOrigins.clear();
        selectionDragTransitionControlOrigins.clear();

        for (StateInterface state : selectedStates) {
            if (state instanceof State st && st.getPosition() != null) {
                selectionDragStateOrigins.put(state, new Point(st.getPosition()));
            }
        }
        for (Integer aliasIndex : selectedPseudoAliases) {
            if (aliasIndex != null && aliasIndex >= 0 && aliasIndex < pseudoStateAliases.size()) {
                selectionDragAliasOrigins.put(aliasIndex, new Point(pseudoStateAliases.get(aliasIndex)));
            }
        }
        for (Component c : selectedComponents) {
            if (c != null && c.getParent() == this) {
                selectionDragComponentOrigins.put(c, new Rectangle(c.getBounds()));
            }
        }
        for (TransitionInterface t : selectedTransitions) {
            Point cp = getTransitionControlPointForRendering(t);
            if (cp != null) {
                selectionDragTransitionControlOrigins.put(t, new Point(cp));
            }
        }
        if (selectionDragStateOrigins.isEmpty()
                && selectionDragAliasOrigins.isEmpty()
                && selectionDragComponentOrigins.isEmpty()
                && selectionDragTransitionControlOrigins.isEmpty()) {
            selectionDragActive = false;
            selectionDragAnchor = null;
        }
    }

    private void updateSelectionDrag(Point current) {
        if (!selectionDragActive || selectionDragAnchor == null || current == null) {
            return;
        }
        int dx = current.x - selectionDragAnchor.x;
        int dy = current.y - selectionDragAnchor.y;
        if (dx == 0 && dy == 0) {
            return;
        }

        for (Map.Entry<StateInterface, Point> entry : selectionDragStateOrigins.entrySet()) {
            if (!(entry.getKey() instanceof State st)) continue;
            Point base = entry.getValue();
            st.setPosition(new Point(base.x + dx, base.y + dy));
        }

        for (Map.Entry<Integer, Point> entry : selectionDragAliasOrigins.entrySet()) {
            Integer aliasIndex = entry.getKey();
            if (aliasIndex == null || aliasIndex < 0 || aliasIndex >= pseudoStateAliases.size()) continue;
            Point base = entry.getValue();
            pseudoStateAliases.get(aliasIndex).setLocation(base.x + dx, base.y + dy);
        }

        for (Map.Entry<Component, Rectangle> entry : selectionDragComponentOrigins.entrySet()) {
            Component c = entry.getKey();
            Rectangle base = entry.getValue();
            if (c == null || c.getParent() != this) continue;
            int x = base.x + dx;
            int y = base.y + dy;
            c.setBounds(x, y, base.width, base.height);
            if (c instanceof DraggableTriggerLabel label && label.getAssociatedTransition() != null) {
                label.getAssociatedTransition().setTriggerOffset(new Point(x, y));
            }
        }

        for (Map.Entry<TransitionInterface, Point> entry : selectionDragTransitionControlOrigins.entrySet()) {
            TransitionInterface t = entry.getKey();
            Point base = entry.getValue();
            if (!(t instanceof Transition tr) || !stateMachine.getTransitions().contains(t)) continue;
            tr.setControlPoint(new Point(base.x + dx, base.y + dy));
        }
        dragMoved = true;
    }

    private boolean snapSelectedObjectsToGrid() {
        if (!snapToGrid || getGridSize() <= 0) {
            return false;
        }
        boolean moved = false;
        int grid = getGridSize();
        int half = Math.max(1, grid / 2);

        for (StateInterface state : selectedStates) {
            if (!(state instanceof State st)) continue;
            Point pos = st.getPosition();
            if (pos == null) continue;
            int diameter = isPseudoState(state) ? PSEUDO_DIAMETER : DIAMETER;
            int radius = diameter / 2;
            Point center = new Point(pos.x + radius, pos.y + radius);
            Point snappedCenter = snap(center);
            Point snappedPos = new Point(snappedCenter.x - radius, snappedCenter.y - radius);
            if (!snappedPos.equals(pos)) {
                st.setPosition(snappedPos);
                moved = true;
            }
        }

        for (Integer aliasIndex : selectedPseudoAliases) {
            if (aliasIndex == null || aliasIndex < 0 || aliasIndex >= pseudoStateAliases.size()) continue;
            Point pos = pseudoStateAliases.get(aliasIndex);
            int radius = PSEUDO_DIAMETER / 2;
            Point center = new Point(pos.x + radius, pos.y + radius);
            Point snappedCenter = snap(center);
            Point snappedPos = new Point(snappedCenter.x - radius, snappedCenter.y - radius);
            if (!snappedPos.equals(pos)) {
                pos.setLocation(snappedPos);
                moved = true;
            }
        }

        for (Component c : selectedComponents) {
            if (c == null || c.getParent() != this || !c.isVisible()) continue;
            Rectangle b = c.getBounds();
            int centerX = b.x + b.width / 2;
            int centerY = b.y + b.height / 2;
            int snappedCenterX = Math.round((float) centerX / half) * half;
            int snappedCenterY = Math.round((float) centerY / half) * half;
            int snappedX = snappedCenterX - b.width / 2;
            int snappedY = snappedCenterY - b.height / 2;
            if (snappedX != b.x || snappedY != b.y) {
                c.setBounds(snappedX, snappedY, b.width, b.height);
                if (c instanceof DraggableTriggerLabel label && label.getAssociatedTransition() != null) {
                    label.getAssociatedTransition().setTriggerOffset(new Point(snappedX, snappedY));
                }
                moved = true;
            }
        }

        for (TransitionInterface t : selectedTransitions) {
            if (!(t instanceof Transition tr) || !stateMachine.getTransitions().contains(t)) continue;
            Point cp = tr.getControlPoint();
            if (cp == null) {
                cp = getTransitionControlPointForRendering(t);
            }
            if (cp != null) {
                Point snapped = snap(cp);
                if (!snapped.equals(cp)) {
                    tr.setControlPoint(snapped);
                    moved = true;
                }
            }
        }

        return moved;
    }

    private void finishSelectionDrag() {
        if (selectionDragActive && snapSelectedObjectsToGrid()) {
            dragMoved = true;
        }
        selectionDragActive = false;
        selectionDragAnchor = null;
        selectionDragStateOrigins.clear();
        selectionDragAliasOrigins.clear();
        selectionDragComponentOrigins.clear();
        selectionDragTransitionControlOrigins.clear();
    }

    private void startSelectionBox(Point anchor) {
        pruneSelection();
        selectionBoxActive = true;
        selectionBoxAnchor = (anchor != null) ? new Point(anchor) : null;
        selectionBoxRect = (anchor != null) ? new Rectangle(anchor.x, anchor.y, 1, 1) : null;
        selectionBoxBaseStates.clear();
        selectionBoxBaseStates.addAll(selectedStates);
        selectionBoxBaseAliases.clear();
        selectionBoxBaseAliases.addAll(selectedPseudoAliases);
        selectionBoxBaseComponents.clear();
        selectionBoxBaseComponents.addAll(selectedComponents);
        selectionBoxBaseTransitions.clear();
        selectionBoxBaseTransitions.addAll(selectedTransitions);
    }

    private void updateSelectionBox(Point current) {
        if (!selectionBoxActive || selectionBoxAnchor == null || current == null) {
            return;
        }
        selectionBoxRect = normalizeRect(selectionBoxAnchor, current);
        Set<StateInterface> states = new LinkedHashSet<>(selectionBoxBaseStates);
        Set<Integer> aliases = new LinkedHashSet<>(selectionBoxBaseAliases);
        Set<Component> components = Collections.newSetFromMap(new IdentityHashMap<>());
        components.addAll(selectionBoxBaseComponents);
        Set<TransitionInterface> transitions = new LinkedHashSet<>(selectionBoxBaseTransitions);
        collectIntersectingObjects(selectionBoxRect, states, aliases, components, transitions);

        selectedStates.clear();
        selectedStates.addAll(states);
        selectedPseudoAliases.clear();
        selectedPseudoAliases.addAll(aliases);
        selectedComponents.clear();
        selectedComponents.addAll(components);
        selectedTransitions.clear();
        selectedTransitions.addAll(transitions);
    }

    private void finishSelectionBox() {
        selectionBoxActive = false;
        selectionBoxAnchor = null;
        selectionBoxBaseStates.clear();
        selectionBoxBaseAliases.clear();
        selectionBoxBaseComponents.clear();
        selectionBoxBaseTransitions.clear();
    }

    private void collectIntersectingObjects(
            Rectangle rect,
            Set<StateInterface> states,
            Set<Integer> aliases,
            Set<Component> components,
            Set<TransitionInterface> transitions) {
        if (rect == null) return;

        for (StateInterface state : stateMachine.getStates()) {
            if (!(state instanceof State st)) continue;
            Point pos = st.getPosition();
            if (pos == null) continue;
            int d = isPseudoState(state) ? PSEUDO_DIAMETER : DIAMETER;
            Rectangle stateRect = new Rectangle(pos.x, pos.y, d, d);
            if (rect.intersects(stateRect) || rect.contains(stateRect)) {
                states.add(state);
            }
        }

        for (int i = 0; i < pseudoStateAliases.size(); i++) {
            Point pos = pseudoStateAliases.get(i);
            Rectangle aliasRect = new Rectangle(pos.x, pos.y, PSEUDO_DIAMETER, PSEUDO_DIAMETER);
            if (rect.intersects(aliasRect) || rect.contains(aliasRect)) {
                aliases.add(i);
            }
        }

        for (Component c : getComponents()) {
            if (!isSelectableComponent(c) || !c.isVisible()) continue;
            Rectangle bounds = c.getBounds();
            if (rect.intersects(bounds) || rect.contains(bounds)) {
                components.add(c);
            }
        }

        for (TransitionInterface t : stateMachine.getTransitions()) {
            if (transitionIntersectsRect(t, rect)) {
                transitions.add(t);
            }
        }
    }

    private Rectangle normalizeRect(Point a, Point b) {
        int x = Math.min(a.x, b.x);
        int y = Math.min(a.y, b.y);
        int w = Math.abs(a.x - b.x);
        int h = Math.abs(a.y - b.y);
        return new Rectangle(x, y, Math.max(1, w), Math.max(1, h));
    }

    private void drawSelectionOverlay(Graphics g) {
        if (!renderSelectionHighlights) {
            return;
        }
        pruneSelection();
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            g2d.setColor(Color.RED);
            g2d.setStroke(new BasicStroke(1.5f));
            for (Component c : selectedComponents) {
                if (c == null || c.getParent() != this || !c.isVisible()) continue;
                Rectangle b = c.getBounds();
                g2d.drawRect(b.x - 2, b.y - 2, b.width + 4, b.height + 4);
            }
            if (selectionBoxActive && selectionBoxRect != null) {
                g2d.setColor(SELECTION_RECT_FILL);
                g2d.fill(selectionBoxRect);
                g2d.setColor(SELECTION_RECT_STROKE);
                g2d.setStroke(new BasicStroke(1.2f));
                g2d.draw(selectionBoxRect);
            }
        } finally {
            g2d.dispose();
        }
    }

    protected Point getStatePositionForTransition(StateInterface state, TransitionInterface t, boolean source) {
        if (state == null) return null;
        if (isPseudoState(state) && (source || (t != null && t.getSource() == t.getTarget()))) {
            Integer idx = pseudoAliasByTransition.get(t);
            if (idx != null) {
                if (idx >= 0 && idx < pseudoStateAliases.size()) {
                    return pseudoStateAliases.get(idx);
                }
                pseudoAliasByTransition.remove(t);
            }
        }
        return ((State) state).getPosition();
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
        hitPseudoAliasIndex = -1;
        int aliasIndex = getPseudoAliasIndexAt(p);
        if (aliasIndex >= 0) {
            StateInterface pseudo = getPseudoStateOrNull();
            if (pseudo != null) {
                hitPseudoAliasIndex = aliasIndex;
                return pseudo;
            }
        }
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

        for (Point aliasPos : pseudoStateAliases) {
            aliasPos.translate(px, py);
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

    protected boolean isBaseDragCommandModifierDown(MouseEvent e) {
        return e != null && (e.isMetaDown() || e.isControlDown());
    }

    protected DragTransitionKind determineBaseDragTransitionKind(MouseEvent e, StateInterface sourceState) {
        if (isPseudoState(sourceState)) {
            return DragTransitionKind.INITIAL_TRIGGERED;
        }
        if (e != null && e.isShiftDown()) {
            return DragTransitionKind.EVENT_TRIGGERED;
        }
        return DragTransitionKind.GUARD_TRIGGERED;
    }

    protected void armBaseDragTransition(StateInterface sourceState, int sourceAliasIndex, DragTransitionKind kind, Point startPoint) {
        dragTransitionArmed = true;
        dragTransitionActive = false;
        dragTransitionKind = kind;
        dragTransitionSourceState = sourceState;
        dragTransitionSourcePseudoAliasIndex = sourceAliasIndex;
        dragTransitionCurrentPoint = (startPoint != null) ? new Point(startPoint) : null;
    }

    protected void clearBaseDragTransitionState() {
        dragTransitionArmed = false;
        dragTransitionActive = false;
        dragTransitionKind = null;
        dragTransitionSourceState = null;
        dragTransitionSourcePseudoAliasIndex = -1;
        dragTransitionCurrentPoint = null;
    }

    protected Rectangle getBaseDragTransitionSourceBounds() {
        if (!(dragTransitionSourceState instanceof State sourceState)) {
            return null;
        }
        Point pos;
        int diameter;
        if (isPseudoState(dragTransitionSourceState)
                && dragTransitionSourcePseudoAliasIndex >= 0
                && dragTransitionSourcePseudoAliasIndex < pseudoStateAliases.size()) {
            pos = pseudoStateAliases.get(dragTransitionSourcePseudoAliasIndex);
            diameter = PSEUDO_DIAMETER;
        } else {
            pos = sourceState.getPosition();
            diameter = isPseudoState(dragTransitionSourceState) ? PSEUDO_DIAMETER : DIAMETER;
        }
        if (pos == null) {
            return null;
        }
        return new Rectangle(pos.x, pos.y, diameter, diameter);
    }

    protected Point getBaseDragTransitionSourceCenter() {
        if (!(dragTransitionSourceState instanceof State sourceState)) {
            return null;
        }
        Point pos;
        int radius;
        if (isPseudoState(dragTransitionSourceState)
                && dragTransitionSourcePseudoAliasIndex >= 0
                && dragTransitionSourcePseudoAliasIndex < pseudoStateAliases.size()) {
            pos = pseudoStateAliases.get(dragTransitionSourcePseudoAliasIndex);
            radius = PSEUDO_RADIUS;
        } else {
            pos = sourceState.getPosition();
            radius = isPseudoState(dragTransitionSourceState) ? PSEUDO_RADIUS : RADIUS;
        }
        if (pos == null) {
            return null;
        }
        return new Point(pos.x + radius, pos.y + radius);
    }

    protected boolean updateBaseDragTransitionActivation(Point currentPoint) {
        if (!dragTransitionArmed || currentPoint == null) {
            return false;
        }
        dragTransitionCurrentPoint = new Point(currentPoint);
        if (dragTransitionActive) {
            return true;
        }
        Rectangle sourceBounds = getBaseDragTransitionSourceBounds();
        if (sourceBounds == null || !sourceBounds.contains(currentPoint)) {
            dragTransitionActive = true;
        }
        return dragTransitionActive;
    }

    protected void drawBaseDragTransitionPreview(Graphics g) {
        if (!dragTransitionArmed || !dragTransitionActive || dragTransitionCurrentPoint == null) {
            return;
        }
        Point sourceCenter = getBaseDragTransitionSourceCenter();
        if (sourceCenter == null) {
            return;
        }
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            Stroke oldStroke = g2d.getStroke();
            g2d.setColor(new Color(70, 70, 70));
            g2d.setStroke(new BasicStroke(
                    1.5f,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND,
                    1.0f,
                    new float[] {6f, 4f},
                    0.0f));
            g2d.drawLine(sourceCenter.x, sourceCenter.y, dragTransitionCurrentPoint.x, dragTransitionCurrentPoint.y);
            g2d.setStroke(oldStroke);
        } finally {
            g2d.dispose();
        }
    }

    protected void scheduleSemanticsRecalculationIfNeeded() {
        java.awt.Container win = javax.swing.SwingUtilities.getWindowAncestor(this);
        if (win instanceof pws.editor.PWSEditor) {
            ((pws.editor.PWSEditor) win).scheduleSemanticsRecalculation();
        }
    }

    protected boolean isInitialPseudoTransition(TransitionInterface t) {
        if (t == null || !isPseudoState(t.getSource())) {
            return false;
        }
        String trigger = t.getTriggerEvent();
        if (trigger != null && "_init".equals(trigger.trim())) {
            return true;
        }
        return t.isAutonomous();
    }

    protected boolean createBaseDragTransitionToTarget(StateInterface targetState) {
        if (!dragTransitionActive || dragTransitionSourceState == null || targetState == null) {
            return false;
        }
        if (targetState == dragTransitionSourceState) {
            return false;
        }
        if (isPseudoState(targetState)) {
            JOptionPane.showMessageDialog(this, "Cannot create transition to PseudoState.");
            return false;
        }

        TransitionInterface newTransition;
        if (dragTransitionKind == DragTransitionKind.INITIAL_TRIGGERED) {
            if (!isPseudoState(dragTransitionSourceState)) {
                return false;
            }
            boolean exists = stateMachine.getTransitions().stream()
                    .anyMatch(t -> t.getSource() == dragTransitionSourceState
                            && t.getTarget() == targetState
                            && isInitialPseudoTransition(t));
            if (exists) {
                JOptionPane.showMessageDialog(this, "An initial transition for this state already exists.");
                return false;
            }
            newTransition = new Transition(dragTransitionSourceState, targetState, false, "_init");
            rememberPseudoAliasForTransition(newTransition, dragTransitionSourcePseudoAliasIndex);
        } else {
            boolean autonomous = (dragTransitionKind == DragTransitionKind.GUARD_TRIGGERED);
            String trigger = autonomous ? "" : "ev";
            newTransition = new Transition(dragTransitionSourceState, targetState, autonomous, trigger);
            if (isPseudoState(dragTransitionSourceState)) {
                rememberPseudoAliasForTransition(newTransition, dragTransitionSourcePseudoAliasIndex);
            }
        }

        stateMachine.addTransition(newTransition);
        scheduleSemanticsRecalculationIfNeeded();
        markOwningEditorDirty();
        return true;
    }

    // --------------- MOUSE EVENT HANDLING ---------------

    @Override
    public void mousePressed(MouseEvent e) {
        Point p = e.getPoint();
        dragMoved = false;
        // Ensure the panel gains focus so its arrow‑key bindings have priority
        if (!hasFocus()) {
            requestFocusInWindow();
        }
        if (!SwingUtilities.isLeftMouseButton(e)) {
            clearBaseDragTransitionState();
            clearSelectionInteractionState();
        }
        // Debug: mouse press details — commented out to reduce console noise
        // System.out.println("mousePressed: button=" + e.getButton() + ", point=" + p + ", isPopupTrigger=" + e.isPopupTrigger());
        if (e.getButton() == MouseEvent.BUTTON1) {
            for (TransitionInterface t : stateMachine.getTransitions()) {
                Point cp = getTransitionControlPointForRendering(t);
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
        int aliasIndex = (isPseudoState(state) && hitPseudoAliasIndex >= 0) ? hitPseudoAliasIndex : -1;
        if (state != null && SwingUtilities.isLeftMouseButton(e) && isBaseDragCommandModifierDown(e)) {
            clearSelectionInteractionState();
            int sourceAliasIndex = aliasIndex;
            DragTransitionKind kind = determineBaseDragTransitionKind(e, state);
            armBaseDragTransition(state, sourceAliasIndex, kind, p);
            selectedState = null;
            selectedPseudoAliasIndex = -1;
            dragOffset = null;
            canvasDragActive = false;
            canvasDragLast = null;
            canvasDragAccumX = 0;
            canvasDragAccumY = 0;
            repaint();
            return;
        }
        TransitionInterface transitionHit = (state == null) ? getTransitionAt(p) : null;
        boolean additiveSelectionGesture = SwingUtilities.isLeftMouseButton(e)
                && e.isShiftDown()
                && !isBaseDragCommandModifierDown(e);
        if (additiveSelectionGesture) {
            clearSelectionInteractionState();
            if (state != null) {
                toggleStateOrAlias(state, aliasIndex);
                selectedState = null;
                selectedPseudoAliasIndex = -1;
                dragOffset = null;
                canvasDragActive = false;
                canvasDragLast = null;
                canvasDragAccumX = 0;
                canvasDragAccumY = 0;
            } else if (transitionHit != null) {
                toggleTransitionSelection(transitionHit);
                selectedState = null;
                selectedPseudoAliasIndex = -1;
                dragOffset = null;
                canvasDragActive = false;
                canvasDragLast = null;
                canvasDragAccumX = 0;
                canvasDragAccumY = 0;
            } else {
                startSelectionBox(p);
                canvasDragActive = false;
                canvasDragLast = null;
                canvasDragAccumX = 0;
                canvasDragAccumY = 0;
            }
            repaint();
            return;
        }

        clearSelectionInteractionState();
        if (state != null) {
            if (isStateOrAliasSelected(state, aliasIndex)) {
                beginSelectionDrag(p);
                selectedState = null;
                selectedPseudoAliasIndex = -1;
                dragOffset = null;
            } else {
                selectedState = state;
                selectedPseudoAliasIndex = aliasIndex;
                if (aliasIndex >= 0 && aliasIndex < pseudoStateAliases.size()) {
                    Point aliasPos = pseudoStateAliases.get(selectedPseudoAliasIndex);
                    dragOffset = new Point(p.x - aliasPos.x, p.y - aliasPos.y);
                } else {
                    Point posState = ((State) state).getPosition();
                    dragOffset = new Point(p.x - posState.x, p.y - posState.y);
                }
            }
            canvasDragActive = false;
            canvasDragLast = null;
            canvasDragAccumX = 0;
            canvasDragAccumY = 0;
        } else if (transitionHit != null) {
            pruneSelection();
            if (selectedTransitions.contains(transitionHit)) {
                beginSelectionDrag(p);
            }
            selectedState = null;
            selectedPseudoAliasIndex = -1;
            dragOffset = null;
            canvasDragActive = false;
            canvasDragLast = null;
            canvasDragAccumX = 0;
            canvasDragAccumY = 0;
        } else {
            clearObjectSelection();
            selectedState = null;
            selectedPseudoAliasIndex = -1;
            dragOffset = null;
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

        if (selectionBoxActive) {
            finishSelectionBox();
            selectionBoxRect = null;
            selectedState = null;
            selectedPseudoAliasIndex = -1;
            dragOffset = null;
            repaint();
            return;
        }

        if (dragTransitionArmed) {
            if (dragTransitionActive) {
                StateInterface targetState = getStateAt(e.getPoint());
                createBaseDragTransitionToTarget(targetState);
            }
            clearBaseDragTransitionState();
            selectedState = null;
            selectedPseudoAliasIndex = -1;
            dragOffset = null;
            canvasDragActive = false;
            canvasDragLast = null;
            canvasDragAccumX = 0;
            canvasDragAccumY = 0;
            clearSelectionInteractionState();
            revalidate();
            repaint();
            return;
        }

        if (selectionDragActive) {
            finishSelectionDrag();
            selectedTransitionForControl = null;
            controlDragOffset = null;
            selectedState = null;
            selectedPseudoAliasIndex = -1;
            dragOffset = null;
            canvasDragActive = false;
            canvasDragLast = null;
            canvasDragAccumX = 0;
            canvasDragAccumY = 0;
            repaint();
            if (dragMoved) {
                markOwningEditorDirty();
            }
            dragMoved = false;
            return;
        }

        // Snap states and control points to grid on release (snap using state center)
        if (snapToGrid) {
            if (selectedPseudoAliasIndex >= 0 && selectedPseudoAliasIndex < pseudoStateAliases.size()) {
                Point pos = pseudoStateAliases.get(selectedPseudoAliasIndex);
                int r = PSEUDO_DIAMETER / 2;
                Point center = new Point(pos.x + r, pos.y + r);
                Point snappedCenter = snap(center);
                Point newPos = new Point(snappedCenter.x - r, snappedCenter.y - r);
                if (!newPos.equals(pos)) {
                    pos.setLocation(newPos);
                    dragMoved = true;
                }
            } else if (selectedState != null) {
                State st = (State) selectedState;
                Point pos = st.getPosition();
                int d = st.getName().equals("PseudoState") ? PSEUDO_DIAMETER : DIAMETER;
                int r = d / 2;
                Point center = new Point(pos.x + r, pos.y + r);
                Point snappedCenter = snap(center);
                Point newPos = new Point(snappedCenter.x - r, snappedCenter.y - r);
                if (!newPos.equals(pos)) {
                    st.setPosition(newPos);
                    dragMoved = true;
                }
            }
            if (selectedTransitionForControl != null) {
                Transition tr = (Transition) selectedTransitionForControl;
                Point cp = tr.getControlPoint();
                if (cp != null) {
                    Point snapped = snap(cp);
                    if (!snapped.equals(cp)) {
                        tr.setControlPoint(snapped);
                        dragMoved = true;
                    }
                }
            }
        }

        selectedTransitionForControl = null;
        controlDragOffset = null;
        selectedState = null;
        selectedPseudoAliasIndex = -1;
        dragOffset = null;
        canvasDragActive = false;
        canvasDragLast = null;
        canvasDragAccumX = 0;
        canvasDragAccumY = 0;
        repaint();
        if (dragMoved) {
            markOwningEditorDirty();
        }
        dragMoved = false;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (dragTransitionArmed) {
            updateBaseDragTransitionActivation(e.getPoint());
            repaint();
            return;
        }
        if (selectionBoxActive) {
            updateSelectionBox(e.getPoint());
            repaint();
            return;
        }
        if (canvasDragActive && canvasDragLast != null) {
            dragMoved = true;
            panCanvasTo(e.getPoint());
            return;
        }
        if (selectedTransitionForControl != null && controlDragOffset != null) {
            Point newPoint = e.getPoint();
            Point newControlPoint = new Point(newPoint.x - controlDragOffset.x, newPoint.y - controlDragOffset.y);
            ((Transition) selectedTransitionForControl).setControlPoint(newControlPoint);
            dragMoved = true;
            repaint();
        } else if (selectionDragActive) {
            updateSelectionDrag(e.getPoint());
            repaint();
        } else if (selectedPseudoAliasIndex >= 0 && dragOffset != null) {
            Point newPoint = e.getPoint();
            int rawX = newPoint.x - dragOffset.x;
            int rawY = newPoint.y - dragOffset.y;
            int r = PSEUDO_DIAMETER / 2;
            if (snapToGrid) {
                Point center = new Point(rawX + r, rawY + r);
                Point snappedCenter = snap(center);
                rawX = snappedCenter.x - r;
                rawY = snappedCenter.y - r;
            }
            Point aliasPos = pseudoStateAliases.get(selectedPseudoAliasIndex);
            aliasPos.setLocation(rawX, rawY);
            dragMoved = true;
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
            dragMoved = true;
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
                } else {
                    markOwningEditorDirty();
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
                        .anyMatch(t -> t.getSource() == pseudo
                                && t.getTarget() == clickedState
                                && isInitialPseudoTransition(t));
                if (!exists) {
                    TransitionInterface newTransition = new Transition(pseudo, clickedState, false, "_init");
                    stateMachine.addTransition(newTransition);
                    rememberPseudoAliasForTransition(newTransition, initialTransitionAliasIndex);
                        // Schedule semantics recalculation if inside PWSEditor
                        java.awt.Container win = javax.swing.SwingUtilities.getWindowAncestor(this);
                        if (win instanceof pws.editor.PWSEditor) {
                            ((pws.editor.PWSEditor) win).scheduleSemanticsRecalculation();
                        }
                        markOwningEditorDirty();
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
        initialTransitionAliasIndex = -1;
        repaint();
    }

    private void handleLinkMode(MouseEvent e) {
        StateInterface clickedState = getStateAt(e.getPoint());
        if (clickedState != null) {
                if (transitionSourceState == null) {
                transitionSourceState = clickedState;
                if (isPseudoState(clickedState) && hitPseudoAliasIndex >= 0) {
                    transitionSourcePseudoAliasIndex = hitPseudoAliasIndex;
                } else {
                    transitionSourcePseudoAliasIndex = -1;
                }
                // Debug: link mode source selection (commented out)
                // System.out.println("Link mode: Source state selected: " + transitionSourceState.getName());
            } else {
                if (clickedState != transitionSourceState) {
                    // Prevent pseudostate as target
                    if (clickedState.getName().equals("PseudoState")) {
                        JOptionPane.showMessageDialog(this, "Cannot create transition to PseudoState.");
                        linkMode = false;
                        transitionSourceState = null;
                        transitionSourcePseudoAliasIndex = -1;
                        return;
                    }
                    String trigger = JOptionPane.showInputDialog(this, "Enter trigger event (leave blank for autonomous):");
                    if (trigger != null && "_init".equals(trigger.trim())
                            && (transitionSourceState == null || !isPseudoState(transitionSourceState))) {
                        JOptionPane.showMessageDialog(this, "\"_init\" is reserved for initial transitions.");
                        linkMode = false;
                        transitionSourceState = null;
                        transitionSourcePseudoAliasIndex = -1;
                        return;
                    }
                    boolean autonomous = transitionSourceState.getName().equals("PseudoState") ||
                            (trigger == null || trigger.trim().isEmpty());
                    TransitionInterface newTransition = new Transition(transitionSourceState, clickedState, autonomous, trigger);
                    stateMachine.addTransition(newTransition);
                    if (isPseudoState(transitionSourceState)) {
                        rememberPseudoAliasForTransition(newTransition, transitionSourcePseudoAliasIndex);
                    }
                    // Schedule semantics recalculation if inside PWSEditor
                    java.awt.Container win = javax.swing.SwingUtilities.getWindowAncestor(this);
                    if (win instanceof pws.editor.PWSEditor) {
                        ((pws.editor.PWSEditor) win).scheduleSemanticsRecalculation();
                    }
                    markOwningEditorDirty();
                    // Debug: transition creation in link mode (commented out)
                    // System.out.println("Link mode: Transition created from " +
                    //        transitionSourceState.getName() + " to " + clickedState.getName());
                } else {
                    // System.out.println("Link mode: Target same as source. Ignored.");
                }
                linkMode = false;
                transitionSourceState = null;
                transitionSourcePseudoAliasIndex = -1;
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
            menuPseudoAliasIndex = isPseudoState(state) ? hitPseudoAliasIndex : -1;
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

        JMenuItem addPseudoAliasItem = new JMenuItem("Create pseudostate alias");
        addPseudoAliasItem.addActionListener(ae -> {
            createPseudoAliasAt(e.getPoint());
            markOwningEditorDirty();
        });
        popup.add(addPseudoAliasItem);

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
        markOwningEditorDirty();
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
                pws.editor.PWSEditor pwsEd = findOwningPWSEditor();
                if (pwsEd != null) {
                    pwsEd.scheduleSemanticsRecalculation();
                }
                if (owningEditor != null) {
                    owningEditor.notifyModelChanged();
                }
                markOwningEditorDirty();
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
                pws.editor.PWSEditor pwsEd = findOwningPWSEditor();
                if (pwsEd != null) {
                    pwsEd.scheduleSemanticsRecalculation();
                    // Also repaint the controller panel to reflect changes
                    if (pwsEd.getBaseEditor() != null) {
                        pwsEd.getBaseEditor().getStateMachinePanel().repaint();
                    }
                }
                if (owningEditor != null) {
                    owningEditor.notifyModelChanged();
                }
                markOwningEditorDirty();
                repaint();
            });
            popup.add(toggleEnableItem);
        }
        
        popup.addSeparator();

        // Menu item to delete the transition
        JMenuItem deleteItem = new JMenuItem("Delete Transition");
        deleteItem.addActionListener(ae -> {
            Object[] options = new Object[] {"Yes", "No"};
            int confirm = JOptionPane.showOptionDialog(
                    this,
                    "Are you sure you want to delete the transition?",
                    "Confirm deletion",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    options,
                    options[0]);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
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

    private pws.editor.PWSEditor findOwningPWSEditor() {
        java.awt.Window win = javax.swing.SwingUtilities.getWindowAncestor(this);
        while (win != null) {
            if (win instanceof pws.editor.PWSEditor pwsEd) {
                return pwsEd;
            }
            if (win instanceof java.awt.Dialog dialog) {
                win = dialog.getOwner();
                continue;
            }
            break;
        }
        return null;
    }

    private void showPopupMenuForState(MouseEvent e, StateInterface state) {
        // Debug: showPopupMenuForState invoked (commented out)
        // System.out.println("showPopupMenuForState invoked for state: " + state.getName());
        JPopupMenu popup = new JPopupMenu();

        if (isPseudoState(state)) {
            // Pseudo-state menu
            JMenuItem addInitialTransItem = new JMenuItem("Add initial transition");
            addInitialTransItem.addActionListener(ae -> {
                initialTransitionAliasIndex = menuPseudoAliasIndex;
                enableInitialTransitionMode();
            });
            popup.add(addInitialTransItem);

            if (getTotalPseudoAliasCount() > 1) {
                JMenuItem deleteAliasItem = new JMenuItem("Delete pseudostate alias");
                deleteAliasItem.addActionListener(ae -> {
                    if (menuPseudoAliasIndex >= 0) {
                        removePseudoAliasAt(menuPseudoAliasIndex);
                    } else {
                        deletePrimaryPseudoAlias();
                    }
                    menuPseudoAliasIndex = -1;
                    markOwningEditorDirty();
                    repaint();
                });
                popup.add(deleteAliasItem);
            } else {
                JMenuItem infoItem = new JMenuItem("Pseudostate alias cannot be deleted");
                infoItem.setEnabled(false);
                popup.add(infoItem);
            }
        } else {
            // Normal state menu
            // Create transition item - only if state is not the pseudostate
            JMenuItem createTransItem = new JMenuItem("Create transition: choose arrival state");
            createTransItem.addActionListener(ae -> {
                enableLinkModeWithSource(state);
            });
            popup.add(createTransItem);

            if (state instanceof State st) {
                JCheckBoxMenuItem failItem = new JCheckBoxMenuItem("Fail state", st.isFailState());
                if (isComponentUnreachableState(state)) {
                    failItem.setEnabled(false);
                }
                failItem.addActionListener(ae -> {
                    st.setFailState(failItem.isSelected());
                    markOwningEditorDirty();
                    repaint();
                });
                popup.add(failItem);
            }

            JMenuItem deleteItem = new JMenuItem("Delete State");
            deleteItem.addActionListener(ae -> {
                    System.out.println("Delete menu item clicked for state: " + state.getName());
                Object[] options = new Object[] {"Yes", "No"};
                int confirm = JOptionPane.showOptionDialog(this,
                    "Are you sure you want to delete state: " + state.getName() + "?",
                    "Confirm deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options[0]);
                if (confirm == JOptionPane.YES_OPTION) {
                    boolean removed = stateMachine.getStates().remove(state);
                    if (removed) {
                        Iterator<TransitionInterface> transitionIterator = stateMachine.getTransitions().iterator();
                        while (transitionIterator.hasNext()) {
                            TransitionInterface t = transitionIterator.next();
                            if (t.getSource() == state || t.getTarget() == state) {
                                clearPseudoAliasForTransition(t);
                                transitionIterator.remove();
                                selectedTransitions.remove(t);
                                DraggableTriggerLabel label = triggerLabels.remove(t);
                                if (label != null) {
                                    remove(label);
                                }
                            }
                        }
                        System.out.println("The state and related transitions have been removed from the structure.");
                    } else {
                        System.out.println("Error: state not found in the structure.");
                    }
                        clearObjectSelection();
                        clearSelectionInteractionState();
                        // Schedule semantics recalculation if inside PWSEditor
                        java.awt.Container win = javax.swing.SwingUtilities.getWindowAncestor(this);
                        if (win instanceof pws.editor.PWSEditor) {
                            ((pws.editor.PWSEditor) win).scheduleSemanticsRecalculation();
                        }
                        markOwningEditorDirty();
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
        transitionSourcePseudoAliasIndex = -1;
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
        clearPseudoAliasForTransition(t);
        selectedTransitions.remove(t);
        DraggableTriggerLabel label = triggerLabels.remove(t);
        if (label != null) {
            remove(label);
        }
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
        markOwningEditorDirty();
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
