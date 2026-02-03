package pws.editor;

import assembly.ActionList;
import assembly.Assembly;
import assembly.AssemblyInterface;
import editor.StateMachinePanel;
import machinery.StateInterface;
import machinery.Transition;
import machinery.TransitionInterface;
import pws.PWSState;
import pws.PWSStateMachine;
import pws.PWSTransition;
import pws.editor.annotation.ActionAnnotation;
import pws.editor.annotation.Annotation;
import pws.editor.annotation.GuardAnnotation;
import pws.editor.annotation.StateSemanticsAnnotation;
import pws.editor.annotation.TransitionSemanticsAnnotation;
import pws.editor.semantics.Semantics;
import smalgebra.SMProposition;
import smalgebra.TrueProposition;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.HierarchyEvent;
import java.awt.font.TextAttribute;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.text.AttributedString;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.awt.BasicStroke;
import java.awt.Stroke;

/** PWS-specific canvas with guard, action, and semantics annotations. */
public class PWSStateMachinePanel extends StateMachinePanel {
    /** Whether to render state‐semantics annotations at all */
    private boolean showStateAnnotations = false;
    private static final Color FAIL_STATE_BORDER_COLOR = new Color(204, 170, 0);
    private static final float[] FAIL_STATE_DASH = new float[] {6f, 4f};
    
    // Fields for self-loop endpoint dragging
    private TransitionInterface selectedSelfLoopTransition = null;
    private boolean draggingSelfLoopStart = false;
    private boolean draggingSelfLoopEnd = false;
    // Fields for non-self transition endpoint dragging (no visible handles)
    private TransitionInterface draggingEndpointTransition = null;
    private boolean draggingEndpointStart = false;
    private boolean draggingEndpointEnd = false;
    private Point draggingEndpointPoint = null;

    public PWSStateMachinePanel(PWSStateMachine stateMachine) {
        super(stateMachine);
        setLayout(null);
        // Enable keyboard focus so arrow keys translate the whole diagram
        setFocusable(true);
        // Mouse listeners are inherited from StateMachinePanel.
        restoreVisibleStateAnnotations();
    }

    @Override
    protected void addImpl(Component comp, Object constraints, int index) {
        applyFontToAnnotation(comp);
        super.addImpl(comp, constraints, index);
    }

    private void applyFontToAnnotation(Component comp) {
        if (!(comp instanceof Annotation)) return;
        Font base = getFont();
        if (base == null) {
            base = new Font("Dialog", Font.PLAIN, Math.round(getStateFontSize()));
        }
        float size = getStateFontSize();
        comp.setFont(base.deriveFont(base.getStyle(), size));
        if (comp instanceof JComponent) {
            resizeAnnotationCentered((JComponent) comp);
        }
    }

    /**
     * Enable or disable rendering of state annotations/dashboards globally.
     * This does NOT change individual state visibility - it only controls whether
     * visible dashboards are rendered on the canvas.
     * @param show true = render dashboards for states with annotationVisible=true; false = hide all
     */
    public void setShowStateAnnotations(boolean show) {
        this.showStateAnnotations = show;
        for (StateInterface si : stateMachine.getStates()) {
            if (!(si instanceof PWSState pwsState)) continue;
            StateSemanticsAnnotation annot = pwsState.getAnnotation();
            if (annot == null) continue;
            boolean shouldShow = show && pwsState.isAnnotationVisible() && !pwsState.isPseudoState();
            annot.setVisible(shouldShow);
        }
        revalidate();
        repaint();
    }

    /**
     * Force all state dashboards to be visible or hidden.
     * This explicitly sets each state's annotationVisible property.
     * @param visible true = make all dashboards visible; false = hide all
     */
    public void setAllStateDashboardsVisible(boolean visible) {
        for (StateInterface si : stateMachine.getStates()) {
            if (si instanceof PWSState p) {
                if (p.isPseudoState()) {
                    p.setAnnotationVisible(false);
                    if (p.getAnnotation() != null) {
                        p.getAnnotation().setVisible(false);
                    }
                } else {
                    p.setAnnotationVisible(visible);
                }
            }
        }
        repaint();
    }

    /** Allow annotations to find the underlying state machine. */
    public PWSStateMachine getStateMachine() {
        return (PWSStateMachine) stateMachine;
    }

    /**
     * Recreate and attach dashboards for states that were marked visible but
     * whose transient annotation component was not restored (e.g., after load).
     *
     * Made public so callers (e.g. the outer editor) can force a restore after
     * annotations are loaded from disk or after the global "show dashboards"
     * flag is toggled.
     */
    public void restoreVisibleStateAnnotations() {
        Assembly assembly = ((PWSStateMachine) stateMachine).getAssembly();
        for (StateInterface si : stateMachine.getStates()) {
            if (si instanceof PWSState pwsState) {
                if (pwsState.isPseudoState()) {
                    pwsState.setAnnotationVisible(false);
                    if (pwsState.getAnnotation() != null) {
                        pwsState.getAnnotation().setVisible(false);
                    }
                    continue;
                }
                if (!pwsState.isAnnotationVisible()) {
                    continue;
                }

                StateSemanticsAnnotation annot = pwsState.getAnnotation();
                if (annot == null) {
                    annot = new StateSemanticsAnnotation(pwsState, assembly, this);

                    // Default positioning: center aligned above the state
                    Point pos = ((machinery.State) pwsState).getPosition();
                    int d = pwsState.getName().equals("PseudoState") ? PSEUDO_DIAMETER : DIAMETER;
                    int stateCenter = pos.x + d / 2;
                    int annotCenterX = stateCenter;
                    int annotCenterY = pos.y - 60;
                    annot.setBounds(annotCenterX - 60, annotCenterY - 15, 120, 30);

                    pwsState.setAnnotation(annot);
                    add(annot);
                    
                    // Restore minimized state if previously minimized
                    if (pwsState.isAnnotationMinimized()) {
                        annot.setMinimized(true);
                    }
                } else {
                    // Annotation exists - ensure it's attached and visible
                    if (annot.getParent() != this) {
                        add(annot);
                    }
                    // Ensure the JComponent is visible (loadAnnotationsFromStream sets it to false)
                    annot.setVisible(true);
                    // Restore minimized state
                    if (pwsState.isAnnotationMinimized()) {
                        annot.setMinimized(true);
                    }
                }
            }
        }
    }

    /**
     * Recompute dashboard sizes after display settings change (e.g., exit-zone labels).
     */
    public void refreshStateAnnotationSizes() {
        for (StateInterface si : stateMachine.getStates()) {
            if (si instanceof PWSState pwsState) {
                StateSemanticsAnnotation annot = pwsState.getAnnotation();
                if (annot == null) continue;
                Dimension pref = annot.getPreferredSize();
                Rectangle bounds = annot.getBounds();
                if (pref.width != bounds.width || pref.height != bounds.height) {
                    annot.setBounds(bounds.x, bounds.y, pref.width, pref.height);
                }
                annot.revalidate();
                annot.repaint();
            }
        }
        resizeCanvasToContent();
    }

    @Override
    public void setStateFontSize(float size) {
        super.setStateFontSize(size);
        applyFontSizeToAnnotations(size);
    }

    private void applyFontSizeToAnnotations(float size) {
        Font base = getFont();
        if (base == null) {
            base = new Font("Dialog", Font.PLAIN, Math.round(size));
        }
        Font derived = base.deriveFont(size);
        for (StateInterface si : stateMachine.getStates()) {
            if (si instanceof PWSState ps) {
                StateSemanticsAnnotation ann = ps.getAnnotation();
                if (ann != null) {
                    ann.setFont(derived);
                    resizeAnnotationCentered(ann);
                }
            }
        }
        for (TransitionInterface ti : stateMachine.getTransitions()) {
            if (ti instanceof PWSTransition pt) {
                if (pt.getGuardAnnotation() != null) {
                    pt.getGuardAnnotation().setFont(derived);
                    resizeAnnotationCentered(pt.getGuardAnnotation());
                }
                if (pt.getActionAnnotation() != null) {
                    pt.getActionAnnotation().setFont(derived);
                    resizeAnnotationCentered(pt.getActionAnnotation());
                }
                if (pt.getSemanticsAnnotation() != null) {
                    pt.getSemanticsAnnotation().setFont(derived);
                    resizeAnnotationCentered(pt.getSemanticsAnnotation());
                }
            }
        }
        revalidate();
        repaint();
    }

    private void resizeAnnotationCentered(JComponent comp) {
        if (comp == null) return;
        Rectangle bounds = comp.getBounds();
        int centerX = bounds.x + bounds.width / 2;
        int centerY = bounds.y + bounds.height / 2;
        Dimension pref = comp.getPreferredSize();
        int w = Math.max(1, pref.width);
        int h = Math.max(1, pref.height);
        comp.setBounds(centerX - w / 2, centerY - h / 2, w, h);
        comp.revalidate();
        comp.repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        drawStateAnnotations(g);
        drawTransitions(g);
    }

    // -------------------- DRAWING METHODS --------------------

    /**
     * Draw dotted lines connecting each state to its annotation (if visible).
     */
    private void drawStateAnnotations(Graphics g) {
        List<StateInterface> states = stateMachine.getStates();
        for (StateInterface s : states) {
            if (s instanceof PWSState) {
                PWSState pwsState = (PWSState) s;
                if (showStateAnnotations && pwsState.isAnnotationVisible() && pwsState.getAnnotation() != null) {
                    Point statePos = ((machinery.State) pwsState).getPosition();
                    int stateDiam = pwsState.getName().equals("PseudoState") ? PSEUDO_DIAMETER : DIAMETER;
                    int centerX = statePos.x + stateDiam / 2;
                    int centerY = statePos.y + stateDiam / 2;
                    Rectangle annotBounds = pwsState.getAnnotation().getBounds();
                    int annotCenterX = annotBounds.x + annotBounds.width / 2;
                    int annotCenterY = annotBounds.y + annotBounds.height / 2;
                    Graphics2D g2d = (Graphics2D) g;
                    Stroke oldStroke = g2d.getStroke();
                    float[] dashPattern = {2f, 4f};
                    g2d.setStroke(new BasicStroke(
                        1.0f,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND,
                        1.0f,
                        dashPattern,
                        0.0f
                    ));
                    g2d.setColor(new Color(150, 150, 150));  // darker grey for better visibility
                    g2d.drawLine(centerX, centerY, annotCenterX, annotCenterY);
                    g2d.setStroke(oldStroke);
                }
            }
        }
    }

    @Override
    protected void drawStates(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        Stroke oldStroke = g2d.getStroke();
        Stroke normalStroke = new BasicStroke(stateBorderThickness);
        float failThickness = Math.max(2.0f, stateBorderThickness + 1.5f);
        Stroke failStroke = new BasicStroke(
            failThickness,
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND,
            10.0f,
            FAIL_STATE_DASH,
            0.0f
        );
        List<StateInterface> states = stateMachine.getStates();
        for (StateInterface state : states) {
            Point pos = ((machinery.State) state).getPosition();
            int x = pos.x;
            int y = pos.y;
            if (state.getName().equals("PseudoState")) {
                g2d.setColor(Color.BLACK);
                g2d.fillOval(x, y, PSEUDO_DIAMETER, PSEUDO_DIAMETER);
                g2d.setColor(Color.BLACK);
                g2d.setStroke(normalStroke);
                g2d.drawOval(x, y, PSEUDO_DIAMETER, PSEUDO_DIAMETER);
            } else {
                g2d.setColor(Color.WHITE);
                g2d.fillOval(x, y, DIAMETER, DIAMETER);
                boolean isSelected = (state == selectedState || state == transitionSourceState);
                boolean isFailState = (state instanceof PWSState ps) && ps.isFailState();
                if (isFailState) {
                    g2d.setStroke(failStroke);
                    g2d.setColor(FAIL_STATE_BORDER_COLOR);
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
        for (Point aliasPos : pseudoStateAliases) {
            g2d.setColor(Color.BLACK);
            g2d.fillOval(aliasPos.x, aliasPos.y, PSEUDO_DIAMETER, PSEUDO_DIAMETER);
            g2d.setColor(Color.BLACK);
            g2d.setStroke(normalStroke);
            g2d.drawOval(aliasPos.x, aliasPos.y, PSEUDO_DIAMETER, PSEUDO_DIAMETER);
        }
        g2d.setStroke(oldStroke);
    }

    /**
     * Draws all transitions.
     */
    protected void drawTransitions(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        List<TransitionInterface> transitions = stateMachine.getTransitions();
        for (TransitionInterface t : transitions) {
            drawSingleTransition(g2d, t);
        }
    }

    /**
     * Draw a single transition, including its curve, arrowhead and annotations.
     */
    protected void drawSingleTransition(Graphics2D g2d, TransitionInterface t) {
        // Compute centers of source and target nodes.
        machinery.State sourceState = (machinery.State) t.getSource();
        machinery.State targetState = (machinery.State) t.getTarget();
        Point sourcePos = getStatePositionForTransition(sourceState, t, true);
        Point targetPos = getStatePositionForTransition(targetState, t, false);
        int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
        int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
        Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
        Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);

        // Check for self-loop (source == target)
        boolean isSelfLoop = (sourceState == targetState);
        
        // Retrieve or compute the control point.
        Point cp = ((Transition) t).getControlPoint();
        if (cp == null) {
            if (isSelfLoop) {
                cp = computeSelfLoopControlPoint(centerSource, sourceCenterOffset);
            } else {
                cp = computeControlPoint(centerSource, centerTarget);
            }
            ((Transition) t).setControlPoint(cp);
        }

        Point p0, p2;
        if (isSelfLoop) {
            // For self-loops, compute start and end points on the circle perimeter
            PWSTransition trans = (PWSTransition) t;
            Double startAngle = trans.getSelfLoopStartAngle();
            Double endAngle = trans.getSelfLoopEndAngle();
            if (startAngle != null) {
                p0 = computeSelfLoopStartPoint(centerSource, sourceCenterOffset, startAngle);
            } else {
                p0 = computeSelfLoopStartPoint(centerSource, sourceCenterOffset);
            }
            if (endAngle != null) {
                p2 = computeSelfLoopEndPoint(centerSource, sourceCenterOffset, endAngle);
            } else {
                p2 = computeSelfLoopEndPoint(centerSource, sourceCenterOffset);
            }
        } else {
            p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
            p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
        }
        if (draggingEndpointTransition == t && draggingEndpointPoint != null) {
            if (draggingEndpointStart) {
                p0 = new Point(draggingEndpointPoint);
            } else if (draggingEndpointEnd) {
                p2 = new Point(draggingEndpointPoint);
            }
        }

        // Draw the transition curve.
        QuadCurve2D.Double curve = new QuadCurve2D.Double();
        curve.setCurve(p0.x, p0.y, cp.x, cp.y, p2.x, p2.y);
        // Render disabled transitions in gray and slightly thicker
        boolean disabled = (t instanceof PWSTransition pt2 && !pt2.isEnabled());
        // Save original stroke
        Stroke oldStroke = g2d.getStroke();
        // Use a thicker stroke when disabled (e.g., 2px instead of 1px)
        g2d.setStroke(new BasicStroke(disabled ? 2.0f : 1.0f));
        g2d.setColor(disabled ? Color.LIGHT_GRAY : Color.BLACK);
        g2d.draw(curve);
        drawArrowHead(g2d, p0, p2, cp);
        // Restore original stroke
        g2d.setStroke(oldStroke);

        // Draw the trigger annotation or, if empty (autonomous transition), a white dot.
        String trigger = t.getTriggerEvent();
        if (trigger != null && !trigger.trim().isEmpty() && t.isTriggerable()) {
            // Fixed trigger labels are no longer drawn here because triggers are shown as draggable labels.
            // (The draggable labels are handled separately in updateTriggerLabels().)
            // drawTriggerAnnotation(g2d, t, p0, cp, p2);
        } else {
            int circleRadius = 5;
            g2d.setColor(Color.WHITE);
            g2d.fillOval(p0.x - circleRadius, p0.y - circleRadius, circleRadius * 2, circleRadius * 2);
            // Outline: gray for disabled, black otherwise
            g2d.setColor(disabled ? Color.LIGHT_GRAY : Color.BLACK);
            g2d.drawOval(p0.x - circleRadius, p0.y - circleRadius, circleRadius * 2, circleRadius * 2);
        }

        // If the transition is a PWSTransition, update/draw annotations.
        PWSTransition pt = null;
        if (t instanceof PWSTransition) {
            pt = (PWSTransition) t;
            drawPWSTransitionAnnotations(g2d, pt, p0, cp, p2);
        }

        // Draw control handle only when edit mode is enabled
        if (isEditMode()) {
            drawControlHandle(g2d, cp);
        }
        
        // For self-loops, draw endpoint handles if enabled
        if (showControlHandles && isSelfLoop) {
            drawSelfLoopEndpointHandle(g2d, p0);  // start point
            drawSelfLoopEndpointHandle(g2d, p2);  // end point
        }

        // Draw connector lines between annotations and the arc
        // when the transition is a PWSTransition (pt is not null).
        if (pt != null) {
            Stroke savedStroke = g2d.getStroke();
            float[] dashPattern = {2f, 4f};
            Stroke dashed = new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1.0f, dashPattern, 0.0f);
            g2d.setStroke(dashed);
            g2d.setColor(new Color(180, 180, 180)); // light color for the lines

            // GuardAnnotation: line from the t = 0.2 point (toward the target) to the annotation center.
            if (pt.getGuardAnnotation() != null && pt.getGuardAnnotation().isVisible()) {
                Point guardPoint = computePointOnCurve(p0, cp, p2, 0.2);
                Rectangle guardBounds = pt.getGuardAnnotation().getBounds();
                Point guardCenter = new Point(guardBounds.x + guardBounds.width / 2, guardBounds.y + guardBounds.height / 2);
                g2d.drawLine(guardPoint.x, guardPoint.y, guardCenter.x, guardCenter.y);
            }
            // ActionAnnotation: line from the t = 0.5 point (midpoint) to the annotation center.
            if (pt.getActionAnnotation() != null && pt.getActionAnnotation().isVisible()) {
                Point actionPoint = computePointOnCurve(p0, cp, p2, 0.5);
                Rectangle actionBounds = pt.getActionAnnotation().getBounds();
                Point actionCenter = new Point(actionBounds.x + actionBounds.width / 2, actionBounds.y + actionBounds.height / 2);
                g2d.drawLine(actionPoint.x, actionPoint.y, actionCenter.x, actionCenter.y);
            }
            // TransitionSemanticsAnnotation: line from the t = 0.8 point (toward the source) to the annotation center.
            if (pt.getSemanticsAnnotation() != null && pt.getSemanticsAnnotation().isVisible()) {
                Point semPoint = computePointOnCurve(p0, cp, p2, 0.8);
                Rectangle semBounds = pt.getSemanticsAnnotation().getBounds();
                Point semCenter = new Point(semBounds.x + semBounds.width / 2, semBounds.y + semBounds.height / 2);
                g2d.drawLine(semPoint.x, semPoint.y, semCenter.x, semCenter.y);
            }
            g2d.setStroke(savedStroke);
        }
    }

    // Helper method to compute a point on the quadratic Bézier curve for a given t.
    private Point computePointOnCurve(Point p0, Point cp, Point p2, double t) {
        double oneMinusT = 1.0 - t;
        int x = (int) (oneMinusT * oneMinusT * p0.x + 2 * oneMinusT * t * cp.x + t * t * p2.x);
        int y = (int) (oneMinusT * oneMinusT * p0.y + 2 * oneMinusT * t * cp.y + t * t * p2.y);
        return new Point(x, y);
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
    
    private Point computeSelfLoopControlPoint(Point center, int radius) {
        // Control point above the state for the self-loop arc
        int loopHeight = (int)(radius * 2.0);
        return new Point(center.x, center.y - loopHeight);
    }
    
    private Point computeSelfLoopStartPoint(Point center, int radius) {
        // Start point at ~135 degrees (top-left of circle)
        double angle = Math.toRadians(135);
        int x = (int)(center.x + radius * Math.cos(angle));
        int y = (int)(center.y + radius * Math.sin(angle));
        return new Point(x, y);
    }
    
    private Point computeSelfLoopStartPoint(Point center, int radius, double angleDegrees) {
        double angle = Math.toRadians(angleDegrees);
        int x = (int)(center.x + radius * Math.cos(angle));
        int y = (int)(center.y + radius * Math.sin(angle));
        return new Point(x, y);
    }
    
    private Point computeSelfLoopEndPoint(Point center, int radius) {
        // End point at ~45 degrees (top-right of circle)
        double angle = Math.toRadians(45);
        int x = (int)(center.x + radius * Math.cos(angle));
        int y = (int)(center.y + radius * Math.sin(angle));
        return new Point(x, y);
    }
    
    private Point computeSelfLoopEndPoint(Point center, int radius, double angleDegrees) {
        double angle = Math.toRadians(angleDegrees);
        int x = (int)(center.x + radius * Math.cos(angle));
        int y = (int)(center.y + radius * Math.sin(angle));
        return new Point(x, y);
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

    protected void drawArrowHead(Graphics2D g2d, Point p0, Point p2, Point control) {
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

    /**
     * Draws the trigger annotation for a transition.
     */
    protected void drawTriggerAnnotation(Graphics2D g2d, TransitionInterface t, Point p0, Point cp, Point p2) {
        String trigger = t.getTriggerEvent();
        if (trigger == null || trigger.length() == 0) {
            return; // Skip drawing if trigger is empty.
        }
        int textX = (int) ((p0.x + 2 * cp.x + p2.x) / 4.0);
        int textY = (int) ((p0.y + 2 * cp.y + p2.y) / 4.0) - 5;
        AttributedString attrTrigger = new AttributedString(trigger);
        attrTrigger.addAttribute(TextAttribute.FONT, g2d.getFont().deriveFont(Font.BOLD));
        attrTrigger.addAttribute(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
        g2d.drawString(attrTrigger.getIterator(), textX, textY);
    }

    /**
     * Draws separate annotations for a PWSTransition: guard, actions, and transition semantics.
     */
    private void drawPWSTransitionAnnotations(Graphics2D g2d, PWSTransition pt, Point p0, Point cp, Point p2) {
        // Recupera l'assembly dal stateMachine
        Assembly assembly = ((PWSStateMachine) stateMachine).getAssembly();

        // ---- Guard Annotation ----
        SMProposition guardProp = pt.getGuardProposition();
        if (pt.getGuardAnnotation() == null) {
            // Compute the point on the curve for the GuardAnnotation (using t = 0.2)
            Point guardPoint = computePointOnCurve(p0, cp, p2, 0.2);
            // Center the annotation on the grid point by subtracting half the width/height
            int guardX = guardPoint.x - 60; // center horizontal (120 / 2 = 60)
            int guardY = guardPoint.y - 10; // center vertical (20 / 2 = 10)
            // Snap guard annotation center to half-grid if snapping is enabled
            java.awt.Container parent = SwingUtilities.getAncestorOfClass(editor.StateMachinePanel.class, this);
            if (parent instanceof editor.StateMachinePanel panel && panel.isSnapToGrid()) {
                int grid = panel.getGridSize();
                if (grid > 0) {
                    int w = 120, h = 20;
                    int centerX = guardX + w / 2;
                    int centerY = guardY + h / 2;
                    float half = grid / 2f;
                    int snappedCenterX = Math.round(centerX / half) * Math.round(half);
                    int snappedCenterY = Math.round(centerY / half) * Math.round(half);
                    guardX = snappedCenterX - w / 2;
                    guardY = snappedCenterY - h / 2;
                }
            }
            GuardAnnotation guardAnnot = new GuardAnnotation(guardProp, assembly, newGuard -> {
                pt.setGuardProposition(newGuard);
                java.awt.Window w = SwingUtilities.getWindowAncestor(PWSStateMachinePanel.this);
                if (w instanceof PWSEditor pe) {
                    pe.markDocumentDirty();
                    pe.scheduleSemanticsRecalculation();
                }
            }, pt);
            guardAnnot.setBounds(guardX, guardY, 120, 20);
            // For both reactive and triggerable transitions, pass guardProp directly.
            guardAnnot.setContent(guardProp);
            pt.setGuardAnnotation(guardAnnot);
            add(guardAnnot);
        } else {
            pt.getGuardAnnotation().setContent(guardProp);
        }

        // ---- Action Annotation ----
        ActionList actions = pt.getActionList();
        if (pt.getActionAnnotation() == null) {
            Point actionPoint = computePointOnCurve(p0, cp, p2, 0.5);
            // Center the annotation on the grid point by subtracting half the width/height
            int actionX = actionPoint.x - 75; // center horizontal (150 / 2 = 75)
            int actionY = actionPoint.y - 10; // center vertical (20 / 2 = 10)
            ActionAnnotation actionAnnot = new ActionAnnotation(actions, assembly, newActions -> {
                pt.setActionList(newActions);
            }, pt);
            actionAnnot.setBounds(actionX, actionY, 150, 20);
            pt.setActionAnnotation(actionAnnot);
            add(actionAnnot);
        } else {
            pt.getActionAnnotation().setContent(actions);
        }

        // ---- Transition Semantics Annotation ----
        Semantics semProp = pt.getTransitionSemantics();
        if (pt.getSemanticsAnnotation() == null) {
            Point semPoint = computePointOnCurve(p0, cp, p2, 0.8);
            // Center the annotation on the grid point by subtracting half the width/height
            int semX = semPoint.x - 75; // center horizontal (150 / 2 = 75)
            int semY = semPoint.y - 10; // center vertical (20 / 2 = 10)
            TransitionSemanticsAnnotation semAnnot = new TransitionSemanticsAnnotation(semProp);
            semAnnot.setBounds(semX, semY, 150, 20);
            semAnnot.setVisible(false);
            pt.setSemanticsAnnotation(semAnnot);
            add(semAnnot);
        } else {
            pt.getSemanticsAnnotation().setContent(semProp);
        }
    }

    // -------------------- MOUSE EVENT HANDLING --------------------

    @Override
    public void mousePressed(MouseEvent e) {
        Point p = e.getPoint();

        // Check if left-click is near a transition control handle for bending.
        if (e.getButton() == MouseEvent.BUTTON1) {
            // Check for self-loop endpoint dragging first
            for (TransitionInterface t : stateMachine.getTransitions()) {
                if (t.getSource() == t.getTarget()) {
                    // This is a self-loop
                    PWSTransition trans = (PWSTransition) t;
                    machinery.State state = (machinery.State) t.getSource();
                    int radius = state.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                    Point pos = getStatePositionForTransition(state, t, true);
                    Point center = new Point(pos.x + radius, pos.y + radius);
                    
                    // Get current angles
                    Double startAngle = trans.getSelfLoopStartAngle();
                    Double endAngle = trans.getSelfLoopEndAngle();
                    Point p0 = startAngle != null ? 
                        computeSelfLoopStartPoint(center, radius, startAngle) :
                        computeSelfLoopStartPoint(center, radius);
                    Point p2 = endAngle != null ?
                        computeSelfLoopEndPoint(center, radius, endAngle) :
                        computeSelfLoopEndPoint(center, radius);
                    
                    // Check if clicking near start or end point
                    if (p.distance(p0) <= 8) {
                        selectedSelfLoopTransition = t;
                        draggingSelfLoopStart = true;
                        return;
                    }
                    if (p.distance(p2) <= 8) {
                        selectedSelfLoopTransition = t;
                        draggingSelfLoopEnd = true;
                        return;
                    }
                }
            }
            // Check for endpoint dragging on non-self transitions (no visible handles)
            for (TransitionInterface t : stateMachine.getTransitions()) {
                if (t.getSource() == t.getTarget()) continue;
                Point[] endpoints = computeTransitionEndpoints(t);
                if (endpoints == null) continue;
                Point p0 = endpoints[0];
                Point p2 = endpoints[1];
                if (p0 != null && p.distance(p0) <= 8) {
                    draggingEndpointTransition = t;
                    draggingEndpointStart = true;
                    draggingEndpointEnd = false;
                    draggingEndpointPoint = p;
                    return;
                }
                if (p2 != null && p.distance(p2) <= 8) {
                    draggingEndpointTransition = t;
                    draggingEndpointStart = false;
                    draggingEndpointEnd = true;
                    draggingEndpointPoint = p;
                    return;
                }
            }
            // Check for control point dragging
            for (TransitionInterface t : stateMachine.getTransitions()) {
                Point cp = ((Transition) t).getControlPoint();
                if (cp != null && p.distance(cp) <= 8) {
                    selectedTransitionForControl = t;
                    controlDragOffset = new Point(e.getX() - cp.x, e.getY() - cp.y);
                    return;
                }
            }
        }

        // Handle initial transition mode.
        if (initialTransitionMode) {
            handleInitialTransitionMode(e);
            return;
        }

        // Handle link mode.
        if (linkMode) {
            handleLinkMode(e);
            return;
        }

        // Handle right-click events.
        if (SwingUtilities.isRightMouseButton(e)) {
            handleRightClick(e);
            return;
        }

        // Otherwise, select a state for dragging.
        StateInterface state = getStateAt(p);
        if (state != null) {
            selectedState = state;
            selectedPseudoAliasIndex = -1;
            if (isPseudoState(state) && hitPseudoAliasIndex >= 0 && hitPseudoAliasIndex < pseudoStateAliases.size()) {
                selectedPseudoAliasIndex = hitPseudoAliasIndex;
                Point aliasPos = pseudoStateAliases.get(selectedPseudoAliasIndex);
                dragOffset = new Point(p.x - aliasPos.x, p.y - aliasPos.y);
            } else {
                Point pos = ((machinery.State) state).getPosition();
                dragOffset = new Point(p.x - pos.x, p.y - pos.y);
            }
            canvasDragActive = false;
            canvasDragLast = null;
        } else {
            selectedState = null;
            selectedPseudoAliasIndex = -1;
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
    public void mouseDragged(MouseEvent e) {
        if (draggingEndpointTransition != null) {
            draggingEndpointPoint = e.getPoint();
            repaint();
            return;
        }
        if (canvasDragActive && canvasDragLast != null) {
            panCanvasTo(e.getPoint());
            return;
        }
        if (selectedSelfLoopTransition != null && (draggingSelfLoopStart || draggingSelfLoopEnd)) {
            // Dragging self-loop endpoint
            PWSTransition trans = (PWSTransition) selectedSelfLoopTransition;
            machinery.State state = (machinery.State) selectedSelfLoopTransition.getSource();
            int radius = state.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
            Point pos = getStatePositionForTransition(state, selectedSelfLoopTransition, true);
            Point center = new Point(pos.x + radius, pos.y + radius);
            
            // Calculate angle from center to mouse position
            double dx = e.getX() - center.x;
            double dy = e.getY() - center.y;
            double angleRadians = Math.atan2(dy, dx);
            double angleDegrees = Math.toDegrees(angleRadians);
            
            // Update the appropriate angle
            if (draggingSelfLoopStart) {
                trans.setSelfLoopStartAngle(angleDegrees);
            } else if (draggingSelfLoopEnd) {
                trans.setSelfLoopEndAngle(angleDegrees);
            }
            repaint();
        } else if (selectedTransitionForControl != null && controlDragOffset != null) {
            Point newPoint = e.getPoint();
            Point newControlPoint = new Point(newPoint.x - controlDragOffset.x, newPoint.y - controlDragOffset.y);
            if (snapToGrid) {
                newControlPoint = snap(newControlPoint);
            }
            ((Transition) selectedTransitionForControl).setControlPoint(newControlPoint);
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

    @Override
    public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
            handleRightClick(e);
            return;
        }

        if (draggingEndpointTransition != null) {
            StateInterface hit = getStateAt(e.getPoint());
            boolean changed = false;
            if (hit != null) {
                if (draggingEndpointEnd && hit instanceof PWSState ps && ps.isPseudoState()) {
                    // Do not allow transitions to pseudo-state
                } else {
                    Transition tr = (Transition) draggingEndpointTransition;
                    if (draggingEndpointStart && tr.getSource() != hit) {
                        tr.setSource(hit);
                        changed = true;
                    } else if (draggingEndpointEnd && tr.getTarget() != hit) {
                        tr.setTarget(hit);
                        changed = true;
                    }
                    if (changed) {
                        tr.setControlPoint(null);
                        if (tr instanceof PWSTransition pt) {
                            pt.setSelfLoopStartAngle(null);
                            pt.setSelfLoopEndAngle(null);
                        }
                        if (draggingEndpointStart) {
                            if (isPseudoState(hit)) {
                                rememberPseudoAliasForTransition(tr, hitPseudoAliasIndex);
                            } else {
                                clearPseudoAliasForTransition(tr);
                            }
                        }
                    }
                }
            }
            draggingEndpointTransition = null;
            draggingEndpointStart = false;
            draggingEndpointEnd = false;
            draggingEndpointPoint = null;
            repaint();
            if (changed) {
                java.awt.Window w = SwingUtilities.getWindowAncestor(this);
                if (w instanceof PWSEditor pe) {
                    pe.markDocumentDirty();
                    pe.scheduleSemanticsRecalculation();
                }
            }
            return;
        }

        // Final snap on release, in case of small offsets
        if (snapToGrid) {
            if (selectedPseudoAliasIndex >= 0 && selectedPseudoAliasIndex < pseudoStateAliases.size()) {
                Point pos = pseudoStateAliases.get(selectedPseudoAliasIndex);
                int r = PSEUDO_DIAMETER / 2;
                Point center = new Point(pos.x + r, pos.y + r);
                Point snappedCenter = snap(center);
                pos.setLocation(snappedCenter.x - r, snappedCenter.y - r);
            } else if (selectedState != null) {
                machinery.State st = (machinery.State) selectedState;
                java.awt.Point pos = st.getPosition();

                int d = st.getName().equals("PseudoState") ? PSEUDO_DIAMETER : DIAMETER;
                int r = d / 2;

                // current center
                Point center = new Point(pos.x + r, pos.y + r);
                // snap the center
                Point snappedCenter = snap(center);
                // new top-left position
                Point newPos = new Point(snappedCenter.x - r, snappedCenter.y - r);
                st.setPosition(newPos);
                java.awt.Window w = SwingUtilities.getWindowAncestor(this);
                if (w instanceof PWSEditor pe) pe.markDocumentDirty();
            }

            // qui lasci invariato lo snap del control point:
            // if (selectedTransitionForControl != null) { ... }
        }

        selectedTransitionForControl = null;
        controlDragOffset = null;
        selectedState = null;
        selectedPseudoAliasIndex = -1;
        dragOffset = null;
        selectedSelfLoopTransition = null;
        draggingSelfLoopStart = false;
        draggingSelfLoopEnd = false;
        canvasDragActive = false;
        canvasDragLast = null;
        canvasDragAccumX = 0;
        canvasDragAccumY = 0;
        repaint();
        java.awt.Window w = SwingUtilities.getWindowAncestor(this);
        if (w instanceof PWSEditor pe) pe.markDocumentDirty();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        // Left-button double-click to rename a state
        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
            StateInterface state = getStateAt(e.getPoint());
            if (state == null) {
                resizeCanvasToContent();
                return;
            }
            // Do not rename the pseudostate
            if (state instanceof PWSState p && p.isPseudoState()) {
                return;
            }
            String newName = JOptionPane.showInputDialog(this, "Rename state:", state.getName());
            if (newName != null && !newName.trim().isEmpty()) {
                ((machinery.State) state).setName(newName.trim());
                repaint();
                java.awt.Container win = javax.swing.SwingUtilities.getWindowAncestor(this);
                if (win instanceof pws.editor.PWSEditor) {
                    ((pws.editor.PWSEditor) win).scheduleSemanticsRecalculation();
                }
            }
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }

    private void resizeCanvasToContent() {
        Rectangle bounds = computeContentBounds();
        if (bounds == null) return;

        int margin = Math.max(40, getGridSize());
        int targetWidth = Math.max(200, bounds.width + margin * 2);
        int targetHeight = Math.max(200, bounds.height + margin * 2);

        java.awt.Container parent = getParent();
        if (parent instanceof JViewport viewport) {
            Dimension viewSize = viewport.getExtentSize();
            targetWidth = Math.max(targetWidth, viewSize.width);
            targetHeight = Math.max(targetHeight, viewSize.height);
        }

        Dimension target = new Dimension(targetWidth, targetHeight);
        setPreferredSize(target);
        revalidate();
        repaint();
    }

    private Rectangle computeContentBounds() {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (StateInterface si : stateMachine.getStates()) {
            if (!(si instanceof machinery.State st)) continue;
            Point pos = st.getPosition();
            if (pos == null) continue;
            int radius = "PseudoState".equals(st.getName()) ? PSEUDO_RADIUS : RADIUS;
            int x1 = pos.x;
            int y1 = pos.y;
            int x2 = pos.x + radius * 2;
            int y2 = pos.y + radius * 2;
            minX = Math.min(minX, x1);
            minY = Math.min(minY, y1);
            maxX = Math.max(maxX, x2);
            maxY = Math.max(maxY, y2);
        }

        for (Point pos : pseudoStateAliases) {
            int x1 = pos.x;
            int y1 = pos.y;
            int x2 = pos.x + PSEUDO_DIAMETER;
            int y2 = pos.y + PSEUDO_DIAMETER;
            minX = Math.min(minX, x1);
            minY = Math.min(minY, y1);
            maxX = Math.max(maxX, x2);
            maxY = Math.max(maxY, y2);
        }

        for (Component comp : getComponents()) {
            if (comp instanceof StateSemanticsAnnotation && comp.isVisible()) {
                Rectangle r = comp.getBounds();
                minX = Math.min(minX, r.x);
                minY = Math.min(minY, r.y);
                maxX = Math.max(maxX, r.x + r.width);
                maxY = Math.max(maxY, r.y + r.height);
            }
        }

        if (minX == Integer.MAX_VALUE) {
            return null;
        }
        return new Rectangle(minX, minY, Math.max(0, maxX - minX), Math.max(0, maxY - minY));
    }

    // -------------------- HELPER METHODS FOR MOUSE --------------------

    /**
     * Handles initial transition mode: creates a triggerable "_init" transition from the pseudo‑state.
     */
    private void handleInitialTransitionMode(MouseEvent e) {
        StateInterface clickedState = getStateAt(e.getPoint());
        if (clickedState != null && !clickedState.getName().equals("PseudoState")) {
            // Trova il Pseudostato (assumendo che esista sempre e abbia nome "PseudoState")
            StateInterface pseudo = stateMachine.getStates().stream()
                    .filter(s -> s.getName().equals("PseudoState"))
                    .findFirst().orElse(null);
            if (pseudo != null) {
                // Check if an initial transition from the PseudoState to the target already exists
                boolean exists = stateMachine.getTransitions().stream()
                        .anyMatch(t -> t instanceof PWSTransition pt
                                && t.getSource() == pseudo
                                && t.getTarget() == clickedState
                                && pt.isInitialTransition());
                if (!exists) {
                    // Create the initial transition as a triggerable "_init" event.
                    PWSTransition newTransition = new PWSTransition(
                            pseudo,
                            clickedState,
                            false,
                            PWSTransition.INIT_TRIGGER_EVENT,
                            ((PWSStateMachine)stateMachine).getAssembly()
                    );
                    // Transition fields (guardProposition, actionList, transitionSemantics) are
                    // inizializzati ai valori di default (TrueProposition, lista vuota, TrueProposition).
                    boolean hasOtherInitial = stateMachine.getTransitions().stream()
                            .anyMatch(t -> t instanceof PWSTransition pt && pt.isInitialTransition());
                    if (hasOtherInitial) {
                        newTransition.setGuardProposition(new smalgebra.FalseProposition());
                    }
                    stateMachine.addTransition(newTransition);
                    rememberPseudoAliasForTransition(newTransition, initialTransitionAliasIndex);
                    // Create a (transient) ActionAnnotation but keep it hidden for autonomous transitions.
                    if (newTransition.isAutonomous()) {
                        try {
                            ActionAnnotation actionAnnot = new ActionAnnotation(newTransition.getActionList(), ((PWSStateMachine)stateMachine).getAssembly(), newActions -> newTransition.setActionList(newActions), newTransition);
                            // place roughly on the curve (0.5)
                            machinery.State sourceState = (machinery.State) newTransition.getSource();
                            machinery.State targetState = (machinery.State) newTransition.getTarget();
                            Point sourcePos = getStatePositionForTransition(sourceState, newTransition, true);
                            Point targetPos = getStatePositionForTransition(targetState, newTransition, false);
                            int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                            int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                            Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
                            Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
                            Point cp = ((Transition) newTransition).getControlPoint();
                            if (cp == null) cp = computeControlPoint(centerSource, centerTarget);
                            Point p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
                            Point p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
                            Point actionPoint = computePointOnCurve(p0, cp, p2, 0.5);
                            actionAnnot.setBounds(actionPoint.x - 75, actionPoint.y - 10, 150, 20);
                            newTransition.setActionAnnotation(actionAnnot);
                            add(actionAnnot);
                            actionAnnot.setVisible(false);
                        } catch (Exception ignored) {}
                    }
                    System.out.println("Initial transition created: PseudoState -_init-> " + clickedState.getName());
                    java.awt.Window w = SwingUtilities.getWindowAncestor(this);
                    if (w instanceof PWSEditor pe) {
                        pe.markDocumentDirty();
                        pe.scheduleSemanticsRecalculation();
                    }
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
        revalidate();
        repaint();
    }

    /**
     * Handles link mode: first click selects source state; second click creates a new PWSTransition.
     */
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
                // Prevent pseudostate as target
                if (clickedState instanceof PWSState p && p.isPseudoState()) {
                    JOptionPane.showMessageDialog(this, "Cannot create transition to PseudoState.");
                    linkMode = false;
                    transitionSourceState = null;
                    transitionSourcePseudoAliasIndex = -1;
                    return;
                }
                String trigger = JOptionPane.showInputDialog(this, "Enter trigger event (leave blank for autonomous):");
                if (trigger != null && "_init".equals(trigger.trim())
                        && !(transitionSourceState instanceof PWSState ps && ps.isPseudoState())) {
                    JOptionPane.showMessageDialog(this, "\"_init\" is reserved for initial transitions.");
                    linkMode = false;
                    transitionSourceState = null;
                    transitionSourcePseudoAliasIndex = -1;
                    return;
                }
                boolean autonomous = (trigger == null || trigger.trim().isEmpty());
                PWSTransition newTransition = new PWSTransition(transitionSourceState, clickedState, autonomous, trigger,((PWSStateMachine)stateMachine).getAssembly());
                
                // For autonomous transitions (except from pseudostate), set FALSE as default guard
                // This makes it clear that the guard needs to be set by the designer
                if (autonomous && !transitionSourceState.getName().equals("PseudoState")) {
                    newTransition.setGuardProposition(new smalgebra.FalseProposition());
                }
                if (transitionSourceState instanceof PWSState ps && ps.isPseudoState()) {
                    if (newTransition.isInitialTransition()) {
                        boolean hasOtherInitial = stateMachine.getTransitions().stream()
                                .anyMatch(t -> t instanceof PWSTransition pt && pt.isInitialTransition());
                        if (hasOtherInitial) {
                            newTransition.setGuardProposition(new smalgebra.FalseProposition());
                        }
                    }
                }
                // For triggered transitions sharing the same source+trigger, use FALSE to force partitioning
                if (!autonomous && transitionSourceState instanceof PWSState) {
                    String trig = (trigger != null) ? trigger.trim() : "";
                    if (!trig.isEmpty()) {
                        for (TransitionInterface ti : stateMachine.getTransitions()) {
                            if (ti instanceof PWSTransition pt && pt.isEnabled() && pt.isTriggerable()
                                    && pt.getSource() == transitionSourceState
                                    && trig.equals(pt.getTriggerEvent())) {
                                newTransition.setGuardProposition(new smalgebra.FalseProposition());
                                break;
                            }
                        }
                    }
                }

                // Here, we no longer use a single dialog; the guard remains default (TRUE for triggered, FALSE for autonomous)
                // and the transition semantics default as well.
                // The user can later modify them by clicking on the corresponding annotations.

                stateMachine.addTransition(newTransition);
                if (isPseudoState(transitionSourceState)) {
                    rememberPseudoAliasForTransition(newTransition, transitionSourcePseudoAliasIndex);
                }
                // If autonomous (no trigger) create hidden action annotation by default
                if (newTransition.isAutonomous()) {
                    try {
                        ActionAnnotation actionAnnot = new ActionAnnotation(newTransition.getActionList(), ((PWSStateMachine)stateMachine).getAssembly(), newActions -> newTransition.setActionList(newActions), newTransition);
                        machinery.State sourceState = (machinery.State) newTransition.getSource();
                        machinery.State targetState = (machinery.State) newTransition.getTarget();
                        Point sourcePos = getStatePositionForTransition(sourceState, newTransition, true);
                        Point targetPos = getStatePositionForTransition(targetState, newTransition, false);
                        int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                        int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                        Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
                        Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
                        Point cp = ((Transition) newTransition).getControlPoint();
                        if (cp == null) cp = computeControlPoint(centerSource, centerTarget);
                        Point p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
                        Point p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
                        Point actionPoint = computePointOnCurve(p0, cp, p2, 0.5);
                        actionAnnot.setBounds(actionPoint.x - 75, actionPoint.y - 10, 150, 20);
                        newTransition.setActionAnnotation(actionAnnot);
                        add(actionAnnot);
                        actionAnnot.setVisible(false);
                    } catch (Exception ignored) {}
                }
                // Debug: transition creation in link mode (commented out)
                // System.out.println("Link mode: Transition created from " +
                //    transitionSourceState.getName() + " to " + clickedState.getName() +
                //    (clickedState == transitionSourceState ? " (self-loop)" : ""));
                linkMode = false;
                transitionSourceState = null;
                transitionSourcePseudoAliasIndex = -1;
                java.awt.Window w = SwingUtilities.getWindowAncestor(this);
                if (w instanceof PWSEditor pe) {
                    pe.markDocumentDirty();
                    pe.scheduleSemanticsRecalculation();
                }
            }
            repaint();
        } else {
            // System.out.println("Link mode: No state found at " + e.getPoint());
        }
    }

    /**
     * Handles right-click events: if near a transition control handle, shows transition popup;
     * otherwise, shows a popup for the state.
     */
    private void handleRightClick(MouseEvent e) {
        Point p = e.getPoint();
        // Check transition control handle first.
        for (TransitionInterface t : stateMachine.getTransitions()) {
            Point cp = ((Transition) t).getControlPoint();
            if (cp != null && p.distance(cp) <= 8) {
                showTransitionPopup(e, t);
                return;
            }
        }
        // Otherwise, show state popup or empty‑space popup.
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
        });
        popup.add(addPseudoAliasItem);
        
        popup.addSeparator();

        JCheckBoxMenuItem editModeItem = new JCheckBoxMenuItem("Edit mode", isEditMode());
        editModeItem.addActionListener(ae -> {
            boolean enabled = editModeItem.isSelected();
            java.awt.Window w = SwingUtilities.getWindowAncestor(this);
            if (w instanceof PWSEditor pe) {
                pe.setEditModeEnabled(enabled);
            } else {
                setEditMode(enabled);
            }
        });
        popup.add(editModeItem);

        JMenuItem leastFixpointItem = new JMenuItem("Least Fixpoint");
        leastFixpointItem.addActionListener(ae -> {
            java.awt.Window w = SwingUtilities.getWindowAncestor(this);
            if (w instanceof PWSEditor pe) {
                pe.scheduleSemanticsRecalculation();
            } else if (stateMachine instanceof PWSStateMachine pwsm) {
                pwsm.recalculateSemantics();
                revalidate();
                repaint();
            }
        });
        popup.add(leastFixpointItem);

        popup.addSeparator();

        // Controller Report menu item
        JMenuItem reportItem = new JMenuItem("Controller Report...");
        reportItem.addActionListener(ae -> {
            if (stateMachine instanceof pws.PWSStateMachine pwsm) {
                java.awt.Window w = SwingUtilities.getWindowAncestor(this);
                java.awt.Frame frame = (w instanceof java.awt.Frame) ? (java.awt.Frame) w : null;
                ControllerReportDialog dialog = new ControllerReportDialog(frame, pwsm);
                dialog.setVisible(true);
            }
        });
        popup.add(reportItem);
        
        popup.show(this, e.getX(), e.getY());
    }

    private void addNewStateAt(Point clickPoint) {
        PWSStateMachine pwsMachine = (PWSStateMachine) stateMachine;
        String name = generateDefaultStateName(pwsMachine);

        int diameter = DIAMETER; // normal state size
        int radius = diameter / 2;

        int centerX = clickPoint.x;
        int centerY = clickPoint.y;
        if (snapToGrid) {
            Point snapped = snap(new Point(centerX, centerY));
            centerX = snapped.x;
            centerY = snapped.y;
        }
        Point topLeft = new Point(centerX - radius, centerY - radius);

        PWSState newState = new PWSState(name, topLeft, pwsMachine.getAssembly());
        pwsMachine.addState(newState);

        while (isAnyConstraints(newState)) {
            ConstraintsEditorDialog dialog = new ConstraintsEditorDialog(newState, pwsMachine.getAssembly());
            dialog.setVisible(true);
            if (!isAnyConstraints(newState)) {
                break;
            }
            Object[] options = { "Keep ANY", "Edit Constraints", "Remove State" };
            int choice = JOptionPane.showOptionDialog(
                    this,
                    "Constraints are ANY (no exit zones will be derived).",
                    "Constraints = ANY",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    options,
                    options[0]
            );
            if (choice == 0) {
                break; // Keep ANY
            }
            if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
                pwsMachine.getStates().remove(newState);
                repaint();
                return;
            }
            // choice == 1 -> edit again
        }
        repaint();
        // mark document dirty and trigger semantics recalculation
        java.awt.Window w = SwingUtilities.getWindowAncestor(this);
        if (w instanceof PWSEditor pe) {
            pe.markDocumentDirty();
            pe.scheduleSemanticsRecalculation();
        }
    }

    private String generateDefaultStateName(PWSStateMachine machine) {
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

    private boolean isAnyConstraints(PWSState state) {
        if (state == null || state.isPseudoState()) {
            return false;
        }
        String raw = state.getRawConstraintText();
        if (raw != null && !raw.isBlank()) {
            return "ANY".equalsIgnoreCase(raw.trim());
        }
        Semantics cs = state.getConstraintsSemantics();
        return cs == null || cs.getConfigurations().isEmpty();
    }

    /**
     * Enable link mode with a predefined source state.
     */
    public void enableLinkModeWithSource(StateInterface sourceState) {
        linkMode = true;
        transitionSourceState = sourceState;
        transitionSourcePseudoAliasIndex = -1;
    }

    private void showTransitionPopup(MouseEvent e, TransitionInterface t) {
        JPopupMenu popup = new JPopupMenu();

        if (t instanceof PWSTransition) {
            PWSTransition pt = (PWSTransition) t;

            // Toggle enable/disable transition
            String toggleText = pt.isEnabled() ? "Disable Transition" : "Enable Transition";
            JMenuItem toggleEnableItem = new JMenuItem(toggleText);
            toggleEnableItem.addActionListener(ae -> {
                pt.setEnabled(!pt.isEnabled());
                java.awt.Window w = SwingUtilities.getWindowAncestor(this);
                if (w instanceof PWSEditor pe) {
                    pe.markDocumentDirty();
                    pe.scheduleSemanticsRecalculation();
                }
                revalidate();
                repaint();
            });
            popup.add(toggleEnableItem);
            popup.addSeparator();

            // Add other menu entries here for annotations, etc., if needed.
            if (!pt.isAutonomous()) {
                // Show/Hide Guard (only for non-autonomous transitions)
                String guardText = (pt.getGuardAnnotation() != null && pt.getGuardAnnotation().isVisible()) ? "Hide Guard" : "Show Guard";
                JMenuItem guardItem = new JMenuItem(guardText);
                guardItem.addActionListener(ae -> {
                    if (pt.getGuardAnnotation() == null) {
                        // create guard annotation and attach
                        GuardAnnotation guardAnnot = new GuardAnnotation(pt.getGuardProposition(), ((PWSStateMachine)stateMachine).getAssembly(), newGuard -> {
                            pt.setGuardProposition(newGuard);
                            java.awt.Window w = SwingUtilities.getWindowAncestor(PWSStateMachinePanel.this);
                            if (w instanceof PWSEditor pe) {
                                pe.markDocumentDirty();
                                pe.scheduleSemanticsRecalculation();
                            }
                        }, pt);
                        // default placement along the curve (0.2)
                        try {
                            machinery.State sourceState = (machinery.State) pt.getSource();
                            machinery.State targetState = (machinery.State) pt.getTarget();
                            Point sourcePos = getStatePositionForTransition(sourceState, pt, true);
                            Point targetPos = getStatePositionForTransition(targetState, pt, false);
                            int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                            int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                            Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
                            Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
                            Point cp = ((Transition) pt).getControlPoint();
                            if (cp == null) cp = computeControlPoint(centerSource, centerTarget);
                            Point p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
                            Point p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
                            Point guardPoint = computePointOnCurve(p0, cp, p2, 0.2);
                            guardAnnot.setBounds(guardPoint.x - 60, guardPoint.y - 10, 120, 20);
                        } catch (Exception ignored) {
                            guardAnnot.setBounds(10, 10, 120, 20);
                        }
                        pt.setGuardAnnotation(guardAnnot);
                        add(guardAnnot);
                        guardAnnot.setVisible(true);
                        revalidate();
                        repaint();
                        java.awt.Window w = SwingUtilities.getWindowAncestor(this);
                        if (w instanceof PWSEditor pe) pe.markDocumentDirty();
                    } else {
                        // toggle visibility explicitly
                        boolean newVis = !pt.getGuardAnnotation().isVisible();
                        pt.getGuardAnnotation().setVisible(newVis);
                        revalidate();
                        repaint();
                        java.awt.Window w = SwingUtilities.getWindowAncestor(this);
                        if (w instanceof PWSEditor pe) pe.markDocumentDirty();
                    }
                });
                popup.add(guardItem);
            }

            // Show/Hide Action
            String actionText = (pt.getActionAnnotation() != null && pt.getActionAnnotation().isVisible()) ? "Hide Action" : "Show Action";
            JMenuItem actionItem = new JMenuItem(actionText);
            actionItem.addActionListener(ae -> {
                if (pt.getActionAnnotation() == null) {
                    ActionAnnotation actionAnnot = new ActionAnnotation(pt.getActionList(), ((PWSStateMachine)stateMachine).getAssembly(), newActions -> pt.setActionList(newActions), pt);
                    try {
                        machinery.State sourceState = (machinery.State) pt.getSource();
                        machinery.State targetState = (machinery.State) pt.getTarget();
                        Point sourcePos = getStatePositionForTransition(sourceState, pt, true);
                        Point targetPos = getStatePositionForTransition(targetState, pt, false);
                        int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                        int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                        Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
                        Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
                        Point cp = ((Transition) pt).getControlPoint();
                        if (cp == null) cp = computeControlPoint(centerSource, centerTarget);
                        Point p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
                        Point p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
                        Point actionPoint = computePointOnCurve(p0, cp, p2, 0.5);
                        actionAnnot.setBounds(actionPoint.x - 75, actionPoint.y - 10, 150, 20);
                    } catch (Exception ignored) {
                        actionAnnot.setBounds(10, 10, 150, 20);
                    }
                    pt.setActionAnnotation(actionAnnot);
                    add(actionAnnot);
                    actionAnnot.setVisible(true);
                    revalidate();
                    repaint();
                } else {
                    boolean newVis = !pt.getActionAnnotation().isVisible();
                    pt.getActionAnnotation().setVisible(newVis);
                    revalidate();
                    repaint();
                }
            });
            popup.add(actionItem);

            // Show/Hide Transition Semantics
            String semText = (pt.getSemanticsAnnotation() != null && pt.getSemanticsAnnotation().isVisible()) ? "Hide Semantics" : "Show Semantics";
            JMenuItem semItem = new JMenuItem(semText);
            semItem.addActionListener(ae -> {
                if (pt.getSemanticsAnnotation() == null) {
                    TransitionSemanticsAnnotation semAnnot = new TransitionSemanticsAnnotation(pt.getTransitionSemantics());
                    try {
                        machinery.State sourceState = (machinery.State) pt.getSource();
                        machinery.State targetState = (machinery.State) pt.getTarget();
                        Point sourcePos = getStatePositionForTransition(sourceState, pt, true);
                        Point targetPos = getStatePositionForTransition(targetState, pt, false);
                        int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                        int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                        Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
                        Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
                        Point cp = ((Transition) pt).getControlPoint();
                        if (cp == null) cp = computeControlPoint(centerSource, centerTarget);
                        Point p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
                        Point p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
                        Point semPoint = computePointOnCurve(p0, cp, p2, 0.8);
                        semAnnot.setBounds(semPoint.x - 75, semPoint.y - 10, 150, 20);
                    } catch (Exception ignored) {
                        semAnnot.setBounds(10, 10, 150, 20);
                    }
                    pt.setSemanticsAnnotation(semAnnot);
                    add(semAnnot);
                    semAnnot.setVisible(true);
                    revalidate();
                    repaint();
                } else {
                    boolean newVis = !pt.getSemanticsAnnotation().isVisible();
                    pt.getSemanticsAnnotation().setVisible(newVis);
                    revalidate();
                    repaint();
                }
            });
            popup.add(semItem);
        }

        // Menu item to show/hide control handles
        JMenuItem toggleHandlesItem = new JMenuItem(showControlHandles ? "Hide Self-Loop Handles" : "Show Self-Loop Handles");
        toggleHandlesItem.addActionListener(ae -> {
            showControlHandles = !showControlHandles;
            repaint();
        });
        popup.add(toggleHandlesItem);

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
            if (confirm == JOptionPane.YES_OPTION) {
                deleteTransition(t); // Helper method that removes the transition and its references.
                revalidate();
                repaint();
            }
        });
        popup.add(deleteItem);

        popup.show(this, e.getX(), e.getY());
    }

    protected  void showPopupMenuForState(MouseEvent e, StateInterface state) {
                System.out.println("showPopupMenuForState: Detected state: " + state.getName()
                                + " - Type: " + state.getClass().getName());

        JPopupMenu popup = new JPopupMenu();

        if (state instanceof PWSState && ((PWSState) state).isPseudoState()) {
            // Add Initial Transition item
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
                    revalidate();
                    repaint();
                });
                popup.add(deleteAliasItem);
            } else {
                JMenuItem infoItem = new JMenuItem("Pseudostate alias cannot be deleted");
                infoItem.setEnabled(false);
                popup.add(infoItem);
            }
        } else {
            // Normal state case
            // Create transition item - only if state is not the pseudostate
            if (!(state instanceof PWSState p && p.isPseudoState())) {
                JMenuItem createTransItem = new JMenuItem("Create transition: choose arrival state");
                createTransItem.addActionListener(ae -> {
                    enableLinkModeWithSource(state);
                });
                popup.add(createTransItem);
            }

            if (state instanceof PWSState) {
                PWSState pwsState = (PWSState) state;
                JMenuItem toggleAnnot;
                if (pwsState.isAnnotationVisible()) {
                    toggleAnnot = new JMenuItem("Hide Dashboard");
                } else {
                    toggleAnnot = new JMenuItem("Show Dashboard");
                }
                toggleAnnot.addActionListener(ae -> {
                    boolean newVisible = !pwsState.isAnnotationVisible();
                    pwsState.setAnnotationVisible(newVisible);
                    if (newVisible) {
                        if (pwsState.getAnnotation() == null) {
                            StateSemanticsAnnotation annot = new StateSemanticsAnnotation(pwsState, ((PWSStateMachine) stateMachine).getAssembly(), this);
                            Point pos = ((machinery.State) pwsState).getPosition();
                            int d = pwsState.getName().equals("PseudoState") ? PSEUDO_DIAMETER : DIAMETER;
                            int stateCenter = pos.x + d / 2;
                            // Center the annotation above the state
                            int annotCenterX = stateCenter;
                            int annotCenterY = pos.y - 60;
                            annot.setBounds(annotCenterX - 60, annotCenterY - 15, 120, 30);
                            pwsState.setAnnotation(annot);
                            add(annot);
                            annot.setVisible(showStateAnnotations);
                            java.awt.Container win = javax.swing.SwingUtilities.getWindowAncestor(this);
                            if (win instanceof pws.editor.PWSEditor) {
                                ((pws.editor.PWSEditor) win).scheduleSemanticsRecalculation();
                            }
                            System.out.println("Created new Annotation for " + pwsState.getName());
                        } else {
                            pwsState.getAnnotation().setVisible(showStateAnnotations);
                            java.awt.Container win2 = javax.swing.SwingUtilities.getWindowAncestor(this);
                            if (win2 instanceof pws.editor.PWSEditor) {
                                ((pws.editor.PWSEditor) win2).scheduleSemanticsRecalculation();
                            }
                        }
                    } else {
                        if (pwsState.getAnnotation() != null) {
                            pwsState.getAnnotation().setVisible(false);
                        }
                    }
                    revalidate();
                    repaint();
                });
                popup.add(toggleAnnot);

                if (!pwsState.isPseudoState()) {
                    JCheckBoxMenuItem failItem = new JCheckBoxMenuItem("Fail state", pwsState.isFailState());
                    failItem.addActionListener(ae -> {
                        pwsState.setFailState(failItem.isSelected());
                        if (pwsState.getAnnotation() != null) {
                            pwsState.getAnnotation().repaint();
                        }
                        java.awt.Window w = SwingUtilities.getWindowAncestor(this);
                        if (w instanceof PWSEditor pe) {
                            pe.markDocumentDirty();
                        }
                        repaint();
                    });
                    popup.add(failItem);
                }
            }

            JMenuItem deleteItem = new JMenuItem("Delete State");
            deleteItem.addActionListener(ae -> {
                Object[] options = new Object[] {"Yes", "No"};
            int confirm = JOptionPane.showOptionDialog(this,
                    "Are you sure you want to delete state \"" + state.getName() + "\"?",
                    "Confirm deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options[0]);
                if (confirm == JOptionPane.YES_OPTION) {
                    // In the PWS case, remove the state's annotation if it exists.
                    if (state instanceof PWSState) {
                        PWSState pwsState = (PWSState) state;
                        if (pwsState.getAnnotation() != null) {
                            remove(pwsState.getAnnotation());
                        }
                    }
                    // Remove the state from the data structure.
                    stateMachine.getStates().remove(state);
                    // Remove any transitions related to the state.
                    Iterator<TransitionInterface> it = stateMachine.getTransitions().iterator();
                    while (it.hasNext()) {
                        TransitionInterface t = it.next();
                        if (t.getSource() == state || t.getTarget() == state) {
                            // For PWS, clear annotations if necessary
                            if (state instanceof PWSState && t instanceof PWSTransition) {
                                clearAnnotationsForTransition((PWSTransition) t);
                            }
                            // Remove t from the global transitions list
                            it.remove();

                            // Remove t from the source state's outgoing transitions
                            StateInterface source = t.getSource();
                            if (source != null && source.getOutgoingTransitions() != null) {
                                source.getOutgoingTransitions().remove(t);
                            }

                            // Remove t from the target state's incoming transitions
                            StateInterface target = t.getTarget();
                            if (target != null && target.getIncomingTransitions() != null) {
                                target.getIncomingTransitions().remove(t);
                            }

                            // Also remove t from the 'state' itself (both incoming and outgoing)
                            if (state.getIncomingTransitions() != null) {
                                state.getIncomingTransitions().remove(t);
                            }
                            if (state.getOutgoingTransitions() != null) {
                                state.getOutgoingTransitions().remove(t);
                            }

//                            // Optional debugging: print if t is still associated with state
//                            if (state.getIncomingTransitions() != null && state.getIncomingTransitions().contains(t)) {
//                                System.out.println(t.toString() + " is still associated to state " + state.getName() + " in incoming transitions");
//                            }
//                            if (state.getOutgoingTransitions() != null && state.getOutgoingTransitions().contains(t)) {
//                                System.out.println(t.toString() + " is still associated to state " + state.getName() + " in outgoing transitions");
//                            }
                        }
                    }
                    // Mark document dirty and trigger semantics recalculation
                    java.awt.Window w = SwingUtilities.getWindowAncestor(PWSStateMachinePanel.this);
                    if (w instanceof PWSEditor pe) {
                        pe.markDocumentDirty();
                        pe.scheduleSemanticsRecalculation();
                    }
                    repaint();
                }
            });
            popup.add(deleteItem);
        }
        popup.show(this, e.getX(), e.getY());
    }

    // Private helper in PWSStateMachine to clear a transition's annotations
    private void clearAnnotationsForTransition(PWSTransition pt) {
        if (pt.getGuardAnnotation() != null) {
            remove(pt.getGuardAnnotation());
            pt.setGuardAnnotation(null);
        }
        if (pt.getActionAnnotation() != null) {
            remove(pt.getActionAnnotation());
            pt.setActionAnnotation(null);
        }
        if (pt.getSemanticsAnnotation() != null) {
            remove(pt.getSemanticsAnnotation());
            pt.setSemanticsAnnotation(null);
        }
    }

    // -------------------- SERIALIZATION METHODS --------------------

    public static class AnnotationData {
        public final java.util.List<StateAnnotationData> stateAnnotations = new java.util.ArrayList<>();
        public final java.util.List<TransitionAnnotationData> transitionAnnotations = new java.util.ArrayList<>();
        public final java.util.List<Point> pseudoAliases = new java.util.ArrayList<>();
        public final java.util.Map<String, Integer> pseudoAliasByTransition = new java.util.LinkedHashMap<>();
        public Boolean showExitZoneMachineIds;
        public Integer stateDiameter;
        public Float stateBorderThickness;
        public Float stateFontSize;
    }

    public static class StateAnnotationData {
        public String stateName;
        public Rectangle bounds;
        public boolean visible;
        public Integer offsetX;
        public Integer offsetY;
    }

    public static class TransitionAnnotationData {
        public String transitionId;
        public Rectangle guardBounds;
        public Rectangle actionBounds;
        public Rectangle semanticsBounds;
        public boolean guardVisible;
        public boolean actionVisible;
        public boolean semanticsVisible;
        public Integer guardOffsetX;
        public Integer guardOffsetY;
        public Integer actionOffsetX;
        public Integer actionOffsetY;
        public Integer semanticsOffsetX;
        public Integer semanticsOffsetY;
    }

    public AnnotationData exportAnnotations() {
        AnnotationData data = new AnnotationData();
        data.showExitZoneMachineIds = StateSemanticsAnnotation.isShowExitZoneMachineIds();
        data.stateDiameter = getStateDiameter();
        data.stateBorderThickness = getStateBorderThickness();
        data.stateFontSize = getStateFontSize();

        for (Point pos : pseudoStateAliases) {
            if (pos != null) {
                data.pseudoAliases.add(new Point(pos));
            }
        }
        for (Map.Entry<TransitionInterface, Integer> entry : pseudoAliasByTransition.entrySet()) {
            TransitionInterface t = entry.getKey();
            Integer aliasIndex = entry.getValue();
            if (!(t instanceof PWSTransition) || aliasIndex == null) continue;
            String id = ((PWSTransition) t).getId();
            if (id == null) continue;
            if (aliasIndex < 0 || aliasIndex >= pseudoStateAliases.size()) continue;
            data.pseudoAliasByTransition.put(id, aliasIndex);
        }

        // State annotations.
        for (StateInterface s : stateMachine.getStates()) {
            if (s instanceof PWSState) {
                PWSState pState = (PWSState) s;
                if (pState.isPseudoState()) {
                    continue;
                }
                StateAnnotationData rec = new StateAnnotationData();
                rec.stateName = pState.getName();
                Rectangle annotBounds = (pState.getAnnotation() != null) ? pState.getAnnotation().getBounds() : null;
                rec.bounds = annotBounds;
                rec.visible = pState.isAnnotationVisible();
                if (annotBounds != null) {
                    Point pos = ((machinery.State) pState).getPosition();
                    int d = pState.getName().equals("PseudoState") ? PSEUDO_DIAMETER : DIAMETER;
                    int stateCenterX = pos.x + d / 2;
                    int stateCenterY = pos.y + d / 2;
                    rec.offsetX = annotBounds.x - stateCenterX;
                    rec.offsetY = annotBounds.y - stateCenterY;
                }
                data.stateAnnotations.add(rec);
            }
        }

        // Transition annotations.
        for (TransitionInterface t : stateMachine.getTransitions()) {
            if (t instanceof PWSTransition) {
                PWSTransition pt = (PWSTransition) t;
                TransitionAnnotationData rec = new TransitionAnnotationData();
                rec.transitionId = pt.getId();
                rec.guardBounds = (pt.getGuardAnnotation() != null) ? pt.getGuardAnnotation().getBounds() : null;
                rec.actionBounds = (pt.getActionAnnotation() != null) ? pt.getActionAnnotation().getBounds() : null;
                rec.semanticsBounds = (pt.getSemanticsAnnotation() != null) ? pt.getSemanticsAnnotation().getBounds() : null;
                rec.guardVisible = (pt.getGuardAnnotation() != null) && pt.getGuardAnnotation().isVisible();
                rec.actionVisible = (pt.getActionAnnotation() != null) && pt.getActionAnnotation().isVisible();
                rec.semanticsVisible = (pt.getSemanticsAnnotation() != null) && pt.getSemanticsAnnotation().isVisible();
                try {
                    machinery.State sourceState = (machinery.State) pt.getSource();
                    machinery.State targetState = (machinery.State) pt.getTarget();
                    Point sourcePos = getStatePositionForTransition(sourceState, pt, true);
                    Point targetPos = getStatePositionForTransition(targetState, pt, false);
                    int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                    int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                    Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
                    Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
                    Point cp = ((Transition) pt).getControlPoint();
                    if (cp == null) cp = computeControlPoint(centerSource, centerTarget);
                    Point p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
                    Point p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
                    Point guardPoint = computePointOnCurve(p0, cp, p2, 0.2);
                    Point actionPoint = computePointOnCurve(p0, cp, p2, 0.5);
                    Point semPoint = computePointOnCurve(p0, cp, p2, 0.8);

                    if (rec.guardBounds != null) {
                        rec.guardOffsetX = rec.guardBounds.x - guardPoint.x;
                        rec.guardOffsetY = rec.guardBounds.y - guardPoint.y;
                    }
                    if (rec.actionBounds != null) {
                        rec.actionOffsetX = rec.actionBounds.x - actionPoint.x;
                        rec.actionOffsetY = rec.actionBounds.y - actionPoint.y;
                    }
                    if (rec.semanticsBounds != null) {
                        rec.semanticsOffsetX = rec.semanticsBounds.x - semPoint.x;
                        rec.semanticsOffsetY = rec.semanticsBounds.y - semPoint.y;
                    }
                } catch (Exception ignore) {
                }
                data.transitionAnnotations.add(rec);
            }
        }

        return data;
    }

    public void importAnnotations(AnnotationData data) {
        if (data == null) return;
        if (data.showExitZoneMachineIds != null) {
            StateSemanticsAnnotation.setShowExitZoneMachineIds(data.showExitZoneMachineIds);
        }
        if (data.stateDiameter != null) {
            setStateDiameter(data.stateDiameter);
        }
        if (data.stateBorderThickness != null) {
            setStateBorderThickness(data.stateBorderThickness);
        }
        if (data.stateFontSize != null) {
            setStateFontSize(data.stateFontSize);
        }

        pseudoStateAliases.clear();
        pseudoAliasByTransition.clear();
        if (data.pseudoAliases != null) {
            for (Point pos : data.pseudoAliases) {
                if (pos != null) {
                    pseudoStateAliases.add(new Point(pos));
                }
            }
        }
        if (data.pseudoAliasByTransition != null && !data.pseudoAliasByTransition.isEmpty()) {
            Map<String, TransitionInterface> transitionById = new HashMap<>();
            for (TransitionInterface t : stateMachine.getTransitions()) {
                if (t instanceof PWSTransition pt) {
                    String id = pt.getId();
                    if (id != null) transitionById.put(id, t);
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

        // Restore state annotations.
        for (StateAnnotationData rec : data.stateAnnotations) {
            String stateName = rec.stateName;
            if (stateName == null) continue;
            Rectangle annotBounds = rec.bounds;
            boolean stateVisible = rec.visible;
            int relOffsetX = (rec.offsetX != null) ? rec.offsetX : Integer.MIN_VALUE;
            int relOffsetY = (rec.offsetY != null) ? rec.offsetY : Integer.MIN_VALUE;
            for (StateInterface s : stateMachine.getStates()) {
                if (s instanceof PWSState && s.getName().equals(stateName)) {
                    PWSState pState = (PWSState) s;
                    if (pState.isPseudoState()) {
                        pState.setAnnotationVisible(false);
                        if (pState.getAnnotation() != null) {
                            pState.getAnnotation().setVisible(false);
                        }
                        break;
                    }
                    if (annotBounds != null || relOffsetX != Integer.MIN_VALUE) {
                        if (pState.getAnnotation() == null) {
                            StateSemanticsAnnotation annot = new StateSemanticsAnnotation(
                                pState,
                                ((PWSStateMachine) stateMachine).getAssembly(),
                                this);
                            if (relOffsetX != Integer.MIN_VALUE) {
                                Point pos = ((machinery.State) pState).getPosition();
                                int d = pState.getName().equals("PseudoState") ? PSEUDO_DIAMETER : DIAMETER;
                                int stateCenterX = pos.x + d / 2;
                                int stateCenterY = pos.y + d / 2;
                                int w = (annotBounds != null) ? annotBounds.width : 120;
                                int h = (annotBounds != null) ? annotBounds.height : 30;
                                annot.setBounds(stateCenterX + relOffsetX, stateCenterY + relOffsetY, w, h);
                            } else {
                                annot.setBounds(annotBounds);
                            }
                            annot.setVisible(stateVisible);
                            pState.setAnnotationVisible(stateVisible);
                            pState.setAnnotation(annot);
                            add(annot);
                        } else {
                            if (relOffsetX != Integer.MIN_VALUE) {
                                Point pos = ((machinery.State) pState).getPosition();
                                int d = pState.getName().equals("PseudoState") ? PSEUDO_DIAMETER : DIAMETER;
                                int stateCenterX = pos.x + d / 2;
                                int stateCenterY = pos.y + d / 2;
                                int w = (annotBounds != null) ? annotBounds.width : pState.getAnnotation().getBounds().width;
                                int h = (annotBounds != null) ? annotBounds.height : pState.getAnnotation().getBounds().height;
                                pState.getAnnotation().setBounds(stateCenterX + relOffsetX, stateCenterY + relOffsetY, w, h);
                            } else if (annotBounds != null) {
                                pState.getAnnotation().setBounds(annotBounds);
                            }
                            pState.getAnnotation().setVisible(stateVisible);
                            pState.setAnnotationVisible(stateVisible);
                        }
                    }
                    break;
                }
            }
        }

        // Restore transition annotations.
        for (TransitionAnnotationData rec : data.transitionAnnotations) {
            String transitionId = rec.transitionId;
            if (transitionId == null) continue;
            Rectangle guardBounds = rec.guardBounds;
            Rectangle actionBounds = rec.actionBounds;
            Rectangle semanticsBounds = rec.semanticsBounds;
            boolean guardVisible = rec.guardVisible;
            boolean actionVisible = rec.actionVisible;
            boolean semVisible = rec.semanticsVisible;
            int guardOffsetX = (rec.guardOffsetX != null) ? rec.guardOffsetX : Integer.MIN_VALUE;
            int guardOffsetY = (rec.guardOffsetY != null) ? rec.guardOffsetY : Integer.MIN_VALUE;
            int actionOffsetX = (rec.actionOffsetX != null) ? rec.actionOffsetX : Integer.MIN_VALUE;
            int actionOffsetY = (rec.actionOffsetY != null) ? rec.actionOffsetY : Integer.MIN_VALUE;
            int semOffsetX = (rec.semanticsOffsetX != null) ? rec.semanticsOffsetX : Integer.MIN_VALUE;
            int semOffsetY = (rec.semanticsOffsetY != null) ? rec.semanticsOffsetY : Integer.MIN_VALUE;

            for (TransitionInterface t : stateMachine.getTransitions()) {
                if (t instanceof PWSTransition && ((PWSTransition) t).getId().equals(transitionId)) {
                    PWSTransition pt = (PWSTransition) t;

                    if (guardBounds != null) {
                        if (pt.getGuardAnnotation() == null) {
                            SMProposition guardProp = pt.getGuardProposition();
                            GuardAnnotation guardAnnot = new GuardAnnotation(guardProp, ((PWSStateMachine)stateMachine).getAssembly(), newGuard -> {
                                pt.setGuardProposition(newGuard);
                                java.awt.Window w = SwingUtilities.getWindowAncestor(PWSStateMachinePanel.this);
                                if (w instanceof PWSEditor pe) {
                                    pe.markDocumentDirty();
                                    pe.scheduleSemanticsRecalculation();
                                }
                            }, pt);
                            if (guardOffsetX != Integer.MIN_VALUE) {
                                machinery.State sourceState = (machinery.State) pt.getSource();
                                machinery.State targetState = (machinery.State) pt.getTarget();
                                Point sourcePos = getStatePositionForTransition(sourceState, pt, true);
                                Point targetPos = getStatePositionForTransition(targetState, pt, false);
                                int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
                                Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
                                Point cp = ((Transition) pt).getControlPoint();
                                if (cp == null) cp = computeControlPoint(centerSource, centerTarget);
                                Point p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
                                Point p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
                                Point guardPoint = computePointOnCurve(p0, cp, p2, 0.2);
                                int w = (guardBounds != null) ? guardBounds.width : 120;
                                int h = (guardBounds != null) ? guardBounds.height : 20;
                                guardAnnot.setBounds(guardPoint.x + guardOffsetX, guardPoint.y + guardOffsetY, w, h);
                            } else {
                                guardAnnot.setBounds(guardBounds);
                            }
                            pt.setGuardAnnotation(guardAnnot);
                            add(guardAnnot);
                            guardAnnot.setVisible(guardVisible);
                        } else {
                            if (guardOffsetX != Integer.MIN_VALUE) {
                                machinery.State sourceState = (machinery.State) pt.getSource();
                                machinery.State targetState = (machinery.State) pt.getTarget();
                                Point sourcePos = getStatePositionForTransition(sourceState, pt, true);
                                Point targetPos = getStatePositionForTransition(targetState, pt, false);
                                int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
                                Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
                                Point cp = ((Transition) pt).getControlPoint();
                                if (cp == null) cp = computeControlPoint(centerSource, centerTarget);
                                Point p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
                                Point p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
                                Point guardPoint = computePointOnCurve(p0, cp, p2, 0.2);
                                int w = (guardBounds != null) ? guardBounds.width : pt.getGuardAnnotation().getBounds().width;
                                int h = (guardBounds != null) ? guardBounds.height : pt.getGuardAnnotation().getBounds().height;
                                pt.getGuardAnnotation().setBounds(guardPoint.x + guardOffsetX, guardPoint.y + guardOffsetY, w, h);
                            } else {
                                pt.getGuardAnnotation().setBounds(guardBounds);
                            }
                            pt.getGuardAnnotation().setVisible(guardVisible);
                        }
                    }

                    if (actionBounds != null) {
                        if (pt.getActionAnnotation() == null) {
                            ActionAnnotation actionAnnot = new ActionAnnotation(pt.getActionList(), ((PWSStateMachine)stateMachine).getAssembly(), newActions -> pt.setActionList(newActions), pt);
                            if (actionOffsetX != Integer.MIN_VALUE) {
                                machinery.State sourceState = (machinery.State) pt.getSource();
                                machinery.State targetState = (machinery.State) pt.getTarget();
                                Point sourcePos = getStatePositionForTransition(sourceState, pt, true);
                                Point targetPos = getStatePositionForTransition(targetState, pt, false);
                                int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
                                Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
                                Point cp = ((Transition) pt).getControlPoint();
                                if (cp == null) cp = computeControlPoint(centerSource, centerTarget);
                                Point p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
                                Point p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
                                Point actionPoint = computePointOnCurve(p0, cp, p2, 0.5);
                                int w = (actionBounds != null) ? actionBounds.width : 150;
                                int h = (actionBounds != null) ? actionBounds.height : 20;
                                actionAnnot.setBounds(actionPoint.x + actionOffsetX, actionPoint.y + actionOffsetY, w, h);
                            } else {
                                actionAnnot.setBounds(actionBounds);
                            }
                            pt.setActionAnnotation(actionAnnot);
                            add(actionAnnot);
                            actionAnnot.setVisible(actionVisible);
                        } else {
                            if (actionOffsetX != Integer.MIN_VALUE) {
                                machinery.State sourceState = (machinery.State) pt.getSource();
                                machinery.State targetState = (machinery.State) pt.getTarget();
                                Point sourcePos = getStatePositionForTransition(sourceState, pt, true);
                                Point targetPos = getStatePositionForTransition(targetState, pt, false);
                                int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
                                Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
                                Point cp = ((Transition) pt).getControlPoint();
                                if (cp == null) cp = computeControlPoint(centerSource, centerTarget);
                                Point p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
                                Point p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
                                Point actionPoint = computePointOnCurve(p0, cp, p2, 0.5);
                                int w = (actionBounds != null) ? actionBounds.width : pt.getActionAnnotation().getBounds().width;
                                int h = (actionBounds != null) ? actionBounds.height : pt.getActionAnnotation().getBounds().height;
                                pt.getActionAnnotation().setBounds(actionPoint.x + actionOffsetX, actionPoint.y + actionOffsetY, w, h);
                            } else {
                                pt.getActionAnnotation().setBounds(actionBounds);
                            }
                            pt.getActionAnnotation().setVisible(actionVisible);
                        }
                    }

                    if (semanticsBounds != null) {
                        if (pt.getSemanticsAnnotation() == null) {
                            Semantics semProp = pt.getTransitionSemantics();
                            TransitionSemanticsAnnotation semAnnot = new TransitionSemanticsAnnotation(semProp);
                            if (semOffsetX != Integer.MIN_VALUE) {
                                machinery.State sourceState = (machinery.State) pt.getSource();
                                machinery.State targetState = (machinery.State) pt.getTarget();
                                Point sourcePos = getStatePositionForTransition(sourceState, pt, true);
                                Point targetPos = getStatePositionForTransition(targetState, pt, false);
                                int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
                                Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
                                Point cp = ((Transition) pt).getControlPoint();
                                if (cp == null) cp = computeControlPoint(centerSource, centerTarget);
                                Point p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
                                Point p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
                                Point semPoint = computePointOnCurve(p0, cp, p2, 0.8);
                                int w = (semanticsBounds != null) ? semanticsBounds.width : 150;
                                int h = (semanticsBounds != null) ? semanticsBounds.height : 20;
                                semAnnot.setBounds(semPoint.x + semOffsetX, semPoint.y + semOffsetY, w, h);
                            } else {
                                semAnnot.setBounds(semanticsBounds);
                            }
                            semAnnot.setVisible(semVisible);
                            pt.setSemanticsAnnotation(semAnnot);
                            add(semAnnot);
                        } else {
                            if (semOffsetX != Integer.MIN_VALUE) {
                                machinery.State sourceState = (machinery.State) pt.getSource();
                                machinery.State targetState = (machinery.State) pt.getTarget();
                                Point sourcePos = getStatePositionForTransition(sourceState, pt, true);
                                Point targetPos = getStatePositionForTransition(targetState, pt, false);
                                int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
                                Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
                                Point cp = ((Transition) pt).getControlPoint();
                                if (cp == null) cp = computeControlPoint(centerSource, centerTarget);
                                Point p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
                                Point p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
                                Point semPoint = computePointOnCurve(p0, cp, p2, 0.8);
                                int w = (semanticsBounds != null) ? semanticsBounds.width : pt.getSemanticsAnnotation().getBounds().width;
                                int h = (semanticsBounds != null) ? semanticsBounds.height : pt.getSemanticsAnnotation().getBounds().height;
                                pt.getSemanticsAnnotation().setBounds(semPoint.x + semOffsetX, semPoint.y + semOffsetY, w, h);
                            } else {
                                pt.getSemanticsAnnotation().setBounds(semanticsBounds);
                            }
                            pt.getSemanticsAnnotation().setVisible(semVisible);
                        }
                    }
                    break;
                }
            }
        }

        revalidate();
        repaint();
    }



    public void saveAnnotationsToStream(ObjectOutputStream oos) throws IOException {
        // Save state annotations.
        for (StateInterface s : stateMachine.getStates()) {
            if (s instanceof PWSState) {
                PWSState pState = (PWSState) s;
                if (pState.isPseudoState()) {
                    continue;
                }
                String stateName = pState.getName();
                Rectangle annotBounds = (pState.getAnnotation() != null) ? pState.getAnnotation().getBounds() : null;
                oos.writeUTF(stateName);
                oos.writeObject(annotBounds);
                // persist whether the state dashboard was visible
                boolean stateVisible = pState.isAnnotationVisible();
                oos.writeBoolean(stateVisible);
                // Also write a position offset relative to the state center for more robust restore
                if (annotBounds != null) {
                    Point pos = ((machinery.State) pState).getPosition();
                    int d = pState.getName().equals("PseudoState") ? PSEUDO_DIAMETER : DIAMETER;
                    int stateCenterX = pos.x + d / 2;
                    int stateCenterY = pos.y + d / 2;
                    int offsetX = annotBounds.x - stateCenterX;
                    int offsetY = annotBounds.y - stateCenterY;
                    oos.writeInt(offsetX);
                    oos.writeInt(offsetY);
                } else {
                    oos.writeInt(Integer.MIN_VALUE);
                    oos.writeInt(Integer.MIN_VALUE);
                }
            }
        }
        oos.writeUTF("END_STATES");

        // Save transition annotations.
        for (TransitionInterface t : stateMachine.getTransitions()) {
            if (t instanceof PWSTransition) {
                PWSTransition pt = (PWSTransition) t;
                String transitionId = pt.getId();
                Rectangle guardBounds = (pt.getGuardAnnotation() != null) ? pt.getGuardAnnotation().getBounds() : null;
                Rectangle actionBounds = (pt.getActionAnnotation() != null) ? pt.getActionAnnotation().getBounds() : null;
                Rectangle semanticsBounds = (pt.getSemanticsAnnotation() != null) ? pt.getSemanticsAnnotation().getBounds() : null;
                oos.writeUTF(transitionId);
                oos.writeObject(guardBounds);
                oos.writeObject(actionBounds);
                oos.writeObject(semanticsBounds);
                // persist visibility of the annotations (if present)
                boolean guardVisible = (pt.getGuardAnnotation() != null) && pt.getGuardAnnotation().isVisible();
                boolean actionVisible = (pt.getActionAnnotation() != null) && pt.getActionAnnotation().isVisible();
                boolean semVisible = (pt.getSemanticsAnnotation() != null) && pt.getSemanticsAnnotation().isVisible();
                oos.writeBoolean(guardVisible);
                oos.writeBoolean(actionVisible);
                oos.writeBoolean(semVisible);
                // Persist relative offsets of annotations to the curve points (guard@0.2, action@0.5, sem@0.8)
                try {
                    // Compute curve reference points
                    machinery.State sourceState = (machinery.State) pt.getSource();
                    machinery.State targetState = (machinery.State) pt.getTarget();
                    Point sourcePos = getStatePositionForTransition(sourceState, pt, true);
                    Point targetPos = getStatePositionForTransition(targetState, pt, false);
                    int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                    int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                    Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
                    Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
                    Point cp = ((Transition) pt).getControlPoint();
                    if (cp == null) cp = computeControlPoint(centerSource, centerTarget);
                    Point p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
                    Point p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
                    Point guardPoint = computePointOnCurve(p0, cp, p2, 0.2);
                    Point actionPoint = computePointOnCurve(p0, cp, p2, 0.5);
                    Point semPoint = computePointOnCurve(p0, cp, p2, 0.8);
                    // write offsets (or sentinel)
                    if (guardBounds != null) {
                        oos.writeInt(guardBounds.x - guardPoint.x);
                        oos.writeInt(guardBounds.y - guardPoint.y);
                    } else {
                        oos.writeInt(Integer.MIN_VALUE);
                        oos.writeInt(Integer.MIN_VALUE);
                    }
                    if (actionBounds != null) {
                        oos.writeInt(actionBounds.x - actionPoint.x);
                        oos.writeInt(actionBounds.y - actionPoint.y);
                    } else {
                        oos.writeInt(Integer.MIN_VALUE);
                        oos.writeInt(Integer.MIN_VALUE);
                    }
                    if (semanticsBounds != null) {
                        oos.writeInt(semanticsBounds.x - semPoint.x);
                        oos.writeInt(semanticsBounds.y - semPoint.y);
                    } else {
                        oos.writeInt(Integer.MIN_VALUE);
                        oos.writeInt(Integer.MIN_VALUE);
                    }
                } catch (Exception ignore) {
                    // If anything fails, write sentinels to remain backwards-compatible
                    oos.writeInt(Integer.MIN_VALUE);
                    oos.writeInt(Integer.MIN_VALUE);
                    oos.writeInt(Integer.MIN_VALUE);
                    oos.writeInt(Integer.MIN_VALUE);
                    oos.writeInt(Integer.MIN_VALUE);
                    oos.writeInt(Integer.MIN_VALUE);
                }
            }
        }
    }

    public void loadAnnotationsFromStream(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        // Restore state annotations.
        String stateName = ois.readUTF();
        while (!"END_STATES".equals(stateName)) {
            Rectangle annotBounds = (Rectangle) ois.readObject();
            boolean stateVisible = false;
            try {
                stateVisible = ois.readBoolean();
            } catch (IOException ignored) {
                // backward compatibility: if no boolean present, default to false
                stateVisible = false;
            }
            // Attempt to read optional relative offsets (backwards-compatible)
            int relOffsetX = Integer.MIN_VALUE;
            int relOffsetY = Integer.MIN_VALUE;
            try {
                relOffsetX = ois.readInt();
                relOffsetY = ois.readInt();
            } catch (IOException ignored) {
                // older files don't have offsets — leave as MIN_VALUE
            }
            for (StateInterface s : stateMachine.getStates()) {
                if (s instanceof PWSState && s.getName().equals(stateName)) {
                    PWSState pState = (PWSState) s;
                    if (pState.isPseudoState()) {
                        pState.setAnnotationVisible(false);
                        if (pState.getAnnotation() != null) {
                            pState.getAnnotation().setVisible(false);
                        }
                        break;
                    }
                    if (annotBounds != null || relOffsetX != Integer.MIN_VALUE) {
                        if (pState.getAnnotation() == null) {
                            StateSemanticsAnnotation annot = new StateSemanticsAnnotation(
                                pState,
                                ((PWSStateMachine) stateMachine).getAssembly(),
                                this);
                            if (relOffsetX != Integer.MIN_VALUE) {
                                Point pos = ((machinery.State) pState).getPosition();
                                int d = pState.getName().equals("PseudoState") ? PSEUDO_DIAMETER : DIAMETER;
                                int stateCenterX = pos.x + d / 2;
                                int stateCenterY = pos.y + d / 2;
                                int w = (annotBounds != null) ? annotBounds.width : 120;
                                int h = (annotBounds != null) ? annotBounds.height : 30;
                                annot.setBounds(stateCenterX + relOffsetX, stateCenterY + relOffsetY, w, h);
                            } else {
                                annot.setBounds(annotBounds);
                            }
                            annot.setVisible(stateVisible);
                            pState.setAnnotationVisible(stateVisible);
                            pState.setAnnotation(annot);
                            add(annot);
                        } else {
                            if (relOffsetX != Integer.MIN_VALUE) {
                                Point pos = ((machinery.State) pState).getPosition();
                                int d = pState.getName().equals("PseudoState") ? PSEUDO_DIAMETER : DIAMETER;
                                int stateCenterX = pos.x + d / 2;
                                int stateCenterY = pos.y + d / 2;
                                int w = (annotBounds != null) ? annotBounds.width : pState.getAnnotation().getBounds().width;
                                int h = (annotBounds != null) ? annotBounds.height : pState.getAnnotation().getBounds().height;
                                pState.getAnnotation().setBounds(stateCenterX + relOffsetX, stateCenterY + relOffsetY, w, h);
                            } else if (annotBounds != null) {
                                pState.getAnnotation().setBounds(annotBounds);
                            }
                            pState.getAnnotation().setVisible(stateVisible);
                            pState.setAnnotationVisible(stateVisible);
                        }
                    }
                    break;
                }
            }
            stateName = ois.readUTF();
        }
        // Restore transition annotations.
        while (true) {
            try {
                String transitionId = ois.readUTF();
                Rectangle guardBounds = (Rectangle) ois.readObject();
                Rectangle actionBounds = (Rectangle) ois.readObject();
                Rectangle semanticsBounds = (Rectangle) ois.readObject();
                for (TransitionInterface t : stateMachine.getTransitions()) {
                    if (t instanceof PWSTransition && ((PWSTransition) t).getId().equals(transitionId)) {
                        PWSTransition pt = (PWSTransition) t;
                        boolean guardVisible = false;
                        boolean actionVisible = false;
                        boolean semVisible = false;
                        try {
                            guardVisible = ois.readBoolean();
                            actionVisible = ois.readBoolean();
                            semVisible = ois.readBoolean();
                        } catch (IOException ignored) {
                            // backward compatibility: leave defaults false
                        }

                        // Read optional relative offsets for transition annotations (backwards compatible)
                        int guardOffsetX = Integer.MIN_VALUE;
                        int guardOffsetY = Integer.MIN_VALUE;
                        int actionOffsetX = Integer.MIN_VALUE;
                        int actionOffsetY = Integer.MIN_VALUE;
                        int semOffsetX = Integer.MIN_VALUE;
                        int semOffsetY = Integer.MIN_VALUE;
                        try {
                            guardOffsetX = ois.readInt();
                            guardOffsetY = ois.readInt();
                            actionOffsetX = ois.readInt();
                            actionOffsetY = ois.readInt();
                            semOffsetX = ois.readInt();
                            semOffsetY = ois.readInt();
                        } catch (IOException ignored) {
                            // older files don't have offsets; we'll use absolute bounds
                        }

                        // Guard Annotation
                        if (guardBounds != null) {
                            if (pt.getGuardAnnotation() == null) {
                                SMProposition guardProp = pt.getGuardProposition();
                                GuardAnnotation guardAnnot = new GuardAnnotation(guardProp, ((PWSStateMachine)stateMachine).getAssembly(), newGuard -> {
                                    pt.setGuardProposition(newGuard);
                                    java.awt.Window w = SwingUtilities.getWindowAncestor(PWSStateMachinePanel.this);
                                    if (w instanceof PWSEditor pe) {
                                        pe.markDocumentDirty();
                                        pe.scheduleSemanticsRecalculation();
                                    }
                                }, pt);
                                if (guardOffsetX != Integer.MIN_VALUE) {
                                    machinery.State sourceState = (machinery.State) pt.getSource();
                                    machinery.State targetState = (machinery.State) pt.getTarget();
                                    Point sourcePos = getStatePositionForTransition(sourceState, pt, true);
                                    Point targetPos = getStatePositionForTransition(targetState, pt, false);
                                    int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                    int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                    Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
                                    Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
                                    Point cp = ((Transition) pt).getControlPoint();
                                    if (cp == null) cp = computeControlPoint(centerSource, centerTarget);
                                    Point p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
                                    Point p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
                                    Point guardPoint = computePointOnCurve(p0, cp, p2, 0.2);
                                    int w = (guardBounds != null) ? guardBounds.width : 120;
                                    int h = (guardBounds != null) ? guardBounds.height : 20;
                                    guardAnnot.setBounds(guardPoint.x + guardOffsetX, guardPoint.y + guardOffsetY, w, h);
                                } else {
                                    guardAnnot.setBounds(guardBounds);
                                }
                                pt.setGuardAnnotation(guardAnnot);
                                add(guardAnnot);
                                guardAnnot.setVisible(guardVisible);
                            } else {
                                if (guardOffsetX != Integer.MIN_VALUE) {
                                    machinery.State sourceState = (machinery.State) pt.getSource();
                                    machinery.State targetState = (machinery.State) pt.getTarget();
                                    Point sourcePos = getStatePositionForTransition(sourceState, pt, true);
                                    Point targetPos = getStatePositionForTransition(targetState, pt, false);
                                    int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                    int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                    Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
                                    Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
                                    Point cp = ((Transition) pt).getControlPoint();
                                    if (cp == null) cp = computeControlPoint(centerSource, centerTarget);
                                    Point p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
                                    Point p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
                                    Point guardPoint = computePointOnCurve(p0, cp, p2, 0.2);
                                    int w = (guardBounds != null) ? guardBounds.width : pt.getGuardAnnotation().getBounds().width;
                                    int h = (guardBounds != null) ? guardBounds.height : pt.getGuardAnnotation().getBounds().height;
                                    pt.getGuardAnnotation().setBounds(guardPoint.x + guardOffsetX, guardPoint.y + guardOffsetY, w, h);
                                } else {
                                    pt.getGuardAnnotation().setBounds(guardBounds);
                                }
                                pt.getGuardAnnotation().setVisible(guardVisible);
                            }
                        }

                        // Action Annotation
                        if (actionBounds != null) {
                            if (pt.getActionAnnotation() == null) {
                                ActionAnnotation actionAnnot = new ActionAnnotation(pt.getActionList(), ((PWSStateMachine)stateMachine).getAssembly(), newActions -> pt.setActionList(newActions), pt);
                                if (actionOffsetX != Integer.MIN_VALUE) {
                                    machinery.State sourceState = (machinery.State) pt.getSource();
                                    machinery.State targetState = (machinery.State) pt.getTarget();
                                    Point sourcePos = getStatePositionForTransition(sourceState, pt, true);
                                    Point targetPos = getStatePositionForTransition(targetState, pt, false);
                                    int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                    int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                    Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
                                    Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
                                    Point cp = ((Transition) pt).getControlPoint();
                                    if (cp == null) cp = computeControlPoint(centerSource, centerTarget);
                                    Point p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
                                    Point p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
                                    Point actionPoint = computePointOnCurve(p0, cp, p2, 0.5);
                                    int w = (actionBounds != null) ? actionBounds.width : 150;
                                    int h = (actionBounds != null) ? actionBounds.height : 20;
                                    actionAnnot.setBounds(actionPoint.x + actionOffsetX, actionPoint.y + actionOffsetY, w, h);
                                } else {
                                    actionAnnot.setBounds(actionBounds);
                                }
                                pt.setActionAnnotation(actionAnnot);
                                add(actionAnnot);
                                actionAnnot.setVisible(actionVisible);
                            } else {
                                if (actionOffsetX != Integer.MIN_VALUE) {
                                    machinery.State sourceState = (machinery.State) pt.getSource();
                                    machinery.State targetState = (machinery.State) pt.getTarget();
                                    Point sourcePos = getStatePositionForTransition(sourceState, pt, true);
                                    Point targetPos = getStatePositionForTransition(targetState, pt, false);
                                    int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                    int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                    Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
                                    Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
                                    Point cp = ((Transition) pt).getControlPoint();
                                    if (cp == null) cp = computeControlPoint(centerSource, centerTarget);
                                    Point p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
                                    Point p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
                                    Point actionPoint = computePointOnCurve(p0, cp, p2, 0.5);
                                    int w = (actionBounds != null) ? actionBounds.width : pt.getActionAnnotation().getBounds().width;
                                    int h = (actionBounds != null) ? actionBounds.height : pt.getActionAnnotation().getBounds().height;
                                    pt.getActionAnnotation().setBounds(actionPoint.x + actionOffsetX, actionPoint.y + actionOffsetY, w, h);
                                } else {
                                    pt.getActionAnnotation().setBounds(actionBounds);
                                }
                                pt.getActionAnnotation().setVisible(actionVisible);
                            }
                        }

                        // Transition Semantics Annotation
                        if (semanticsBounds != null) {
                            if (pt.getSemanticsAnnotation() == null) {
                                Semantics semProp = pt.getTransitionSemantics();
                                TransitionSemanticsAnnotation semAnnot = new TransitionSemanticsAnnotation(semProp);
                                if (semOffsetX != Integer.MIN_VALUE) {
                                    machinery.State sourceState = (machinery.State) pt.getSource();
                                    machinery.State targetState = (machinery.State) pt.getTarget();
                                    Point sourcePos = getStatePositionForTransition(sourceState, pt, true);
                                    Point targetPos = getStatePositionForTransition(targetState, pt, false);
                                    int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                    int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                    Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
                                    Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
                                    Point cp = ((Transition) pt).getControlPoint();
                                    if (cp == null) cp = computeControlPoint(centerSource, centerTarget);
                                    Point p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
                                    Point p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
                                    Point semPoint = computePointOnCurve(p0, cp, p2, 0.8);
                                    int w = (semanticsBounds != null) ? semanticsBounds.width : 150;
                                    int h = (semanticsBounds != null) ? semanticsBounds.height : 20;
                                    semAnnot.setBounds(semPoint.x + semOffsetX, semPoint.y + semOffsetY, w, h);
                                } else {
                                    semAnnot.setBounds(semanticsBounds);
                                }
                                semAnnot.setVisible(semVisible);
                                pt.setSemanticsAnnotation(semAnnot);
                                add(semAnnot);
                            } else {
                                if (semOffsetX != Integer.MIN_VALUE) {
                                    machinery.State sourceState = (machinery.State) pt.getSource();
                                    machinery.State targetState = (machinery.State) pt.getTarget();
                                    Point sourcePos = getStatePositionForTransition(sourceState, pt, true);
                                    Point targetPos = getStatePositionForTransition(targetState, pt, false);
                                    int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                    int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
                                    Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
                                    Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
                                    Point cp = ((Transition) pt).getControlPoint();
                                    if (cp == null) cp = computeControlPoint(centerSource, centerTarget);
                                    Point p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
                                    Point p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
                                    Point semPoint = computePointOnCurve(p0, cp, p2, 0.8);
                                    int w = (semanticsBounds != null) ? semanticsBounds.width : pt.getSemanticsAnnotation().getBounds().width;
                                    int h = (semanticsBounds != null) ? semanticsBounds.height : pt.getSemanticsAnnotation().getBounds().height;
                                    pt.getSemanticsAnnotation().setBounds(semPoint.x + semOffsetX, semPoint.y + semOffsetY, w, h);
                                } else {
                                    pt.getSemanticsAnnotation().setBounds(semanticsBounds);
                                }
                                pt.getSemanticsAnnotation().setVisible(semVisible);
                            }
                        }
                        break;
                    }
                }
            } catch (EOFException eof) {
                break;
            }
        }
        revalidate();
        repaint();
    }

    /**
            * Removes transition t from the state machine and clears references to it:
            * - Removes associated annotations (if the transition is a PWSTransition)
            * - Removes t from the global transitions list
            * - Removes t from the source state's outgoing transitions list
            *   and from the target state's incoming transitions list.
            */
    public void deleteTransition(TransitionInterface t) {
        // If t is a PWSTransition, clear its associated annotations.
        if (t instanceof PWSTransition) {
            clearAnnotationsForTransition((PWSTransition) t);
        }
        clearPseudoAliasForTransition(t);
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

        // After removing the transition, update and remove its trigger label
        updateTriggerLabels();
        revalidate();
        repaint();
        java.awt.Window w = SwingUtilities.getWindowAncestor(this);
        if (w instanceof PWSEditor pe) {
            pe.markDocumentDirty();
            pe.scheduleSemanticsRecalculation();
        }
    }
    
    /**
     * Draws a draggable endpoint handle for self-loops.
     */
    private void drawSelfLoopEndpointHandle(Graphics2D g2d, Point p) {
        g2d.setColor(Color.BLUE);
        int handleRadius = 4;
        g2d.fillOval(p.x - handleRadius, p.y - handleRadius, handleRadius * 2, handleRadius * 2);
        g2d.setColor(Color.BLACK);
        g2d.drawOval(p.x - handleRadius, p.y - handleRadius, handleRadius * 2, handleRadius * 2);
    }

    private Point[] computeTransitionEndpoints(TransitionInterface t) {
        if (t == null || t.getSource() == null || t.getTarget() == null) return null;
        machinery.State sourceState = (machinery.State) t.getSource();
        machinery.State targetState = (machinery.State) t.getTarget();
        Point sourcePos = getStatePositionForTransition(sourceState, t, true);
        Point targetPos = getStatePositionForTransition(targetState, t, false);
        int sourceCenterOffset = sourceState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
        int targetCenterOffset = targetState.getName().equals("PseudoState") ? PSEUDO_RADIUS : RADIUS;
        Point centerSource = new Point(sourcePos.x + sourceCenterOffset, sourcePos.y + sourceCenterOffset);
        Point centerTarget = new Point(targetPos.x + targetCenterOffset, targetPos.y + targetCenterOffset);
        boolean isSelfLoop = (sourceState == targetState);
        Point cp = ((Transition) t).getControlPoint();
        if (cp == null) {
            cp = isSelfLoop ? computeSelfLoopControlPoint(centerSource, sourceCenterOffset)
                    : computeControlPoint(centerSource, centerTarget);
        }
        Point p0;
        Point p2;
        if (isSelfLoop) {
            PWSTransition trans = (PWSTransition) t;
            Double startAngle = trans.getSelfLoopStartAngle();
            Double endAngle = trans.getSelfLoopEndAngle();
            p0 = startAngle != null
                    ? computeSelfLoopStartPoint(centerSource, sourceCenterOffset, startAngle)
                    : computeSelfLoopStartPoint(centerSource, sourceCenterOffset);
            p2 = endAngle != null
                    ? computeSelfLoopEndPoint(centerSource, sourceCenterOffset, endAngle)
                    : computeSelfLoopEndPoint(centerSource, sourceCenterOffset);
        } else {
            p0 = computeStartPoint(centerSource, cp, sourceCenterOffset);
            p2 = computeEndPoint(centerTarget, cp, targetCenterOffset);
        }
        return new Point[] { p0, p2 };
    }
}
