package pws.editor.annotation;

import assembly.Assembly;
import assembly.AssemblyInterface;
import smalgebra.AndProposition;
import smalgebra.OrProposition;
import smalgebra.SMProposition;
import smalgebra.TrueProposition;
import smalgebra.FalseProposition;
import smalgebra.BasicStateProposition;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.StringJoiner;
import java.util.function.Consumer;
import pws.PWSTransition;
import pws.PWSState;
import pws.editor.GuardEditorDialog;
import pws.editor.semantics.Configuration;
import pws.editor.semantics.ExitZone;
import pws.editor.semantics.Semantics;
import machinery.StateMachine;
import machinery.TransitionInterface;

/** Annotation widget for editing guard propositions. */
@SuppressWarnings("this-escape")
public class GuardAnnotation extends Annotation<SMProposition> {
    private static final long serialVersionUID = 1L;
    private Assembly assembly;
    private transient Consumer<SMProposition> updateCallback;
    private TransitionInterface associatedTransition;
    
    // Problem status for coloring
    private enum GuardIssueLevel { NONE, ORANGE, RED }
    private GuardIssueLevel issueLevel = GuardIssueLevel.NONE;
    private String problemReason = null;
    private static final Color COLOR_RED = new Color(180, 0, 0);
    private static final Color COLOR_ORANGE = new Color(204, 102, 0);

    private static final class GuardSegment {
        private final String text;
        private final boolean highlight;

        private GuardSegment(String text, boolean highlight) {
            this.text = text;
            this.highlight = highlight;
        }
    }

    /**
     * Creates a guard annotation.
     *
     * @param content initial guard proposition
     * @param assembly assembly context
     * @param updateCallback callback to update the model
     */
    public GuardAnnotation(SMProposition content, Assembly assembly, Consumer<SMProposition> updateCallback) {
        super(content);
        this.assembly = assembly;
        this.updateCallback = updateCallback;
        this.associatedTransition = null;
        setToolTipText(""); // Enable tooltips
    }

    /**
     * Creates a guard annotation attached to a specific transition.
     *
     * @param content initial guard proposition
     * @param assembly assembly context
     * @param updateCallback callback to update the model
     * @param associatedTransition transition associated with this annotation
     */
    public GuardAnnotation(SMProposition content, Assembly assembly, Consumer<SMProposition> updateCallback, TransitionInterface associatedTransition) {
        super(content);
        this.assembly = assembly;
        this.updateCallback = updateCallback;
        this.associatedTransition = associatedTransition;
        setToolTipText(""); // Enable tooltips
    }

    // Add an extra listener to adjust snapping on mouse release to half-grid.
    {
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                java.awt.Container parent = javax.swing.SwingUtilities.getAncestorOfClass(editor.StateMachinePanel.class, GuardAnnotation.this);
                if (parent instanceof editor.StateMachinePanel panel && panel.isSnapToGrid()) {
                    int grid = panel.getGridSize();
                    if (grid <= 0) return;

                    int x = getX();
                    int y = getY();
                    int w = getWidth();
                    int h = getHeight();
                    int centerX = x + w / 2;
                    int centerY = y + h / 2;

                    float half = grid / 2f;
                    int snappedCenterX = Math.round(centerX / half) * Math.round(half);
                    int snappedCenterY = Math.round(centerY / half) * Math.round(half);

                    int snappedX = snappedCenterX - w / 2;
                    int snappedY = snappedCenterY - h / 2;
                    setLocation(snappedX, snappedY);
                    if (getParent() != null) getParent().repaint();
                }
            }
        });
    }

    @Override
    protected String buildDisplayText() {
        return "[" + toConfigurationDisplay(content) + "]";
    }

    private boolean shouldHighlightAutonomousGuard() {
        if (associatedTransition == null || !associatedTransition.isAutonomous()) {
            return false;
        }
        return !(content instanceof TrueProposition) && !(content instanceof FalseProposition);
    }

    private List<GuardSegment> buildGuardSegments(SMProposition prop, boolean highlightAutonomous) {
        List<GuardSegment> segments = new ArrayList<>();
        segments.add(new GuardSegment("[" + toConfigurationDisplay(prop) + "]", highlightAutonomous));
        return segments;
    }

    private void appendPropositionSegments(SMProposition prop, List<GuardSegment> segments) {
        if (prop == null) {
            return;
        }
        if (prop instanceof BasicStateProposition) {
            segments.add(new GuardSegment(prop.toString(), true));
            return;
        }
        if (prop instanceof TrueProposition || prop instanceof FalseProposition) {
            segments.add(new GuardSegment(prop.toString(), false));
            return;
        }
        if (prop instanceof AndProposition and) {
            segments.add(new GuardSegment("(", false));
            appendPropositionSegments(and.getLeft(), segments);
            segments.add(new GuardSegment(" AND ", false));
            appendPropositionSegments(and.getRight(), segments);
            segments.add(new GuardSegment(")", false));
            return;
        }
        segments.add(new GuardSegment(prop.toString(), false));
    }

    private String toConfigurationDisplay(SMProposition prop) {
        if (prop == null) return "";
        java.util.List<java.util.List<BasicStateProposition>> products = expandToProducts(prop);
        if (products.isEmpty()) return prop.toString();
        if (products.size() == 1 && products.get(0).isEmpty()) {
            return "TRUE";
        }
        StringJoiner orJoin = new StringJoiner(", ");
        for (java.util.List<BasicStateProposition> product : products) {
            StringJoiner andJoin = new StringJoiner(", ");
            for (BasicStateProposition bsp : product) {
                andJoin.add(bsp.toString());
            }
            orJoin.add("(" + andJoin + ")");
        }
        return orJoin.toString();
    }

    private java.util.List<java.util.List<BasicStateProposition>> expandToProducts(SMProposition prop) {
        java.util.List<java.util.List<BasicStateProposition>> result = new ArrayList<>();
        if (prop == null || prop instanceof FalseProposition) {
            return result; // empty
        }
        if (prop instanceof TrueProposition) {
            result.add(new ArrayList<>()); // empty product
            return result;
        }
        if (prop instanceof BasicStateProposition bsp) {
            java.util.List<BasicStateProposition> single = new ArrayList<>();
            single.add(bsp);
            result.add(single);
            return result;
        }
        if (prop instanceof AndProposition and) {
            var left = expandToProducts(and.getLeft());
            var right = expandToProducts(and.getRight());
            for (var l : left) {
                for (var r : right) {
                    java.util.List<BasicStateProposition> combo = new ArrayList<>(l.size() + r.size());
                    combo.addAll(l);
                    combo.addAll(r);
                    result.add(combo);
                }
            }
            return result;
        }
        if (prop instanceof OrProposition or) {
            result.addAll(expandToProducts(or.getLeft()));
            result.addAll(expandToProducts(or.getRight()));
            return result;
        }
        // fallback
        return result;
    }
    
    /**
     * Checks if this guard is problematic and needs red highlighting.
     * Problematic conditions:
     * - FALSE guard (placeholder that needs to be set)
     * - Orphan guard (references exit zone that no longer exists)
     */
    private void checkProblematicStatus() {
        issueLevel = GuardIssueLevel.NONE;
        problemReason = null;
        
        if (content == null) return;
        
        // Check for FALSE guard - placeholder
        if (content instanceof FalseProposition) {
            issueLevel = GuardIssueLevel.RED;
            problemReason = "FALSE guard - transition will never fire";
            return;
        }
        
        // Check for orphan guard (guard references an unavailable autonomous target)
        if (associatedTransition != null && associatedTransition.isAutonomous() 
                && content instanceof BasicStateProposition bsp) {
            machinery.StateInterface src = associatedTransition.getSource();
            if (src instanceof PWSState ps && !ps.isPseudoState()) {
                Set<String> availableTargets = collectAutonomousGuardValidationTargets(ps);
                if (!availableTargets.contains(bsp.toString())) {
                    issueLevel = GuardIssueLevel.RED;
                    problemReason = "Orphan guard - exit zone no longer exists or is internal";
                    return;
                }
            }
        }

        // Triggered transition guard partitioning checks (source + trigger event, including _init)
        if (associatedTransition instanceof PWSTransition pt) {
            machinery.StateInterface src = pt.getSource();
            if (src instanceof PWSState ps && assembly != null) {
                String triggerKey = getTriggeredPartitionKey(pt, ps);
                if (triggerKey != null) {
                    Semantics stateSem = ps.getStateSemantics();
                    Semantics guardSem = content.toSemantics(assembly);
                    if (stateSem != null) {
                        guardSem = guardSem.AND(stateSem);
                    }
                    if (guardSem.ISEMPTY()) {
                        issueLevel = GuardIssueLevel.ORANGE;
                        problemReason = "Orphan guard - no matching source semantics";
                        return;
                    }
                    for (TransitionInterface ti : ps.getOutgoingTransitions()) {
                        if (ti == pt) continue;
                        if (!(ti instanceof PWSTransition other)) continue;
                        String otherKey = getTriggeredPartitionKey(other, ps);
                        if (otherKey == null || !triggerKey.equals(otherKey)) continue;
                        SMProposition otherGuard = other.getGuardProposition();
                        if (otherGuard == null) continue;
                        Semantics otherSem = otherGuard.toSemantics(assembly);
                        if (stateSem != null) {
                            otherSem = otherSem.AND(stateSem);
                        }
                        if (!guardSem.AND(otherSem).ISEMPTY()) {
                            issueLevel = GuardIssueLevel.RED;
                            problemReason = "Overlapping guard - same trigger from source state";
                            return;
                        }
                    }
                    if (stateSem != null && !stateSem.ISEMPTY()) {
                        Semantics otherCoverage = collectTriggeredCoverage(ps, pt, triggerKey);
                        Semantics union = new Semantics(assembly.getAssemblyId());
                        if (otherCoverage != null && !otherCoverage.ISEMPTY()) {
                            union = union.OR(otherCoverage);
                        }
                        if (!guardSem.ISEMPTY()) {
                            union = union.OR(guardSem);
                        }
                        if (!stateSem.implies(union)) {
                            issueLevel = GuardIssueLevel.ORANGE;
                            problemReason = "Incomplete guard partition - some source semantics not covered for trigger '"
                                    + triggerKey + "'";
                            return;
                        }
                    }
                }
            }
        }
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        // Update problematic status before painting
        checkProblematicStatus();
        
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Font baseFont = getFont();
        if (baseFont == null) {
            baseFont = new Font("Dialog", Font.PLAIN, 12);
            setFont(baseFont);
        }
        float size = baseFont.getSize2D();
        Font plainFont = baseFont.deriveFont(Font.PLAIN, size);
        Font boldFont = plainFont.deriveFont(Font.BOLD, size);
        
        // Set color based on problematic status
        if (issueLevel == GuardIssueLevel.RED) {
            g2d.setColor(COLOR_RED);
        } else if (issueLevel == GuardIssueLevel.ORANGE) {
            g2d.setColor(COLOR_ORANGE);
        } else {
            g2d.setColor(Color.BLACK);
        }
        
        boolean highlightAutonomous = shouldHighlightAutonomousGuard();
        List<GuardSegment> segments = buildGuardSegments(content, highlightAutonomous);
        FontMetrics fmPlain = g2d.getFontMetrics(plainFont);
        FontMetrics fmBold = g2d.getFontMetrics(boldFont);
        int textHeight = fmPlain.getAscent();
        int totalWidth = 0;
        for (GuardSegment seg : segments) {
            FontMetrics fm = seg.highlight ? fmBold : fmPlain;
            totalWidth += fm.stringWidth(seg.text);
        }
        int x = (getWidth() - totalWidth) / 2;
        int y = (getHeight() + textHeight) / 2 - 2;
        for (GuardSegment seg : segments) {
            FontMetrics fm = seg.highlight ? fmBold : fmPlain;
            g2d.setFont(seg.highlight ? boldFont : plainFont);
            g2d.drawString(seg.text, x, y);
            int segWidth = fm.stringWidth(seg.text);
            if (seg.highlight && segWidth > 0) {
                int underlineY = y + 1;
                g2d.drawLine(x, underlineY, x + segWidth, underlineY);
            }
            x += segWidth;
        }
    }

    @Override
    public Dimension getPreferredSize() {
        Font f = getFont();
        if (f == null) {
            f = new Font("Dialog", Font.PLAIN, 12);
            setFont(f);
        }
        float size = f.getSize2D();
        Font plainFont = f.deriveFont(Font.PLAIN, size);
        Font boldFont = plainFont.deriveFont(Font.BOLD, size);
        FontMetrics fmPlain = getFontMetrics(plainFont);
        FontMetrics fmBold = getFontMetrics(boldFont);

        boolean highlightAutonomous = shouldHighlightAutonomousGuard();
        List<GuardSegment> segments = buildGuardSegments(content, highlightAutonomous);
        int width = 0;
        for (GuardSegment seg : segments) {
            FontMetrics fm = seg.highlight ? fmBold : fmPlain;
            width += fm.stringWidth(seg.text);
        }
        int height = Math.max(fmPlain.getHeight(), fmBold.getHeight());
        return new Dimension(width + 10, height + 10);
    }
    
    @Override
    public String getToolTipText() {
        if (issueLevel != GuardIssueLevel.NONE && problemReason != null) {
            return problemReason;
        }
        return super.getToolTipText();
    }

    @Override
    protected void showPopup(MouseEvent e) {
        JPopupMenu popup = new JPopupMenu();
        boolean isTrue = content instanceof TrueProposition;
        boolean isFalse = content instanceof FalseProposition;
        boolean isTrueAutonomous = false;
        pws.PWSState srcState = null;
        Semantics otherTriggeredCoverage = null;
        if (associatedTransition != null) {
            machinery.StateInterface src = associatedTransition.getSource();
            if (src instanceof pws.PWSState ps) {
                srcState = ps;
                isTrueAutonomous = associatedTransition.isAutonomous() && !ps.isPseudoState();
            }
        }
        boolean hasExitZones = hasAnyExitZones(srcState);

        // Treat both TRUE and FALSE as "no guard set" - need to select a guard
        // FALSE is a placeholder indicating the guard was removed and needs to be set
        if (isTrue || isFalse) {
            if (isTrueAutonomous && !hasExitZones) {
                if (isTrue) {
                    JMenuItem removeItem = new JMenuItem("Remove guard");
                    removeItem.addActionListener(ev -> applyGuard(new FalseProposition()));
                    popup.add(removeItem);
                } else {
                    JMenuItem trueItem = new JMenuItem("TRUE");
                    trueItem.addActionListener(ev -> applyGuard(new TrueProposition()));
                    popup.add(trueItem);
                }
                popup.show(this, e.getX(), e.getY());
                return;
            }

            if (isTrue) {
                JMenuItem removeItem = new JMenuItem("Remove guard");
                removeItem.addActionListener(ev -> applyGuard(new FalseProposition()));
                popup.add(removeItem);
                popup.addSeparator();
            }
            List<? extends SMProposition> guards = assembly.getAssemblyGuards();

            // If associatedTransition and source has semantics, filter guards appropriately.
            // For TRUE autonomous transitions (not initial) use reactiveSemantics (ExitZone targets).
            // Initial transitions (from pseudo-state) and triggered transitions use stateSemantics.
            boolean filteredBySemantics = false;
            Set<String> candidateStrings = new LinkedHashSet<>();
            if (associatedTransition != null) {
                machinery.StateInterface src = associatedTransition.getSource();
                if (src instanceof PWSState p) {
                    String triggerKey = getTriggeredPartitionKey(associatedTransition, p);
                    if (triggerKey != null) {
                        otherTriggeredCoverage = collectTriggeredCoverage(p, associatedTransition, triggerKey);
                    }
                    // Check if this is a TRUE autonomous transition (not from pseudo-state)
                    // Initial transitions from pseudo-state are event-triggered with "_init"
                    if (isTrueAutonomous) {
                        // True autonomous transitions -> use eligible reactive exit zones
                        Set<String> eligibleTargets = collectEligibleAutonomousExitTargets(p, associatedTransition);
                        if (!eligibleTargets.isEmpty()) {
                            filteredBySemantics = true;
                            candidateStrings.addAll(eligibleTargets);
                        } else if (hasAnyExitZones(p)) {
                            // Reactive semantics exist but none are eligible (internal/covered)
                            filteredBySemantics = true;
                        }
                    } else {
                        // Non-autonomous: preserve previous behavior using stateSemantics configurations
                        Semantics sem = p.getStateSemantics();
                        if (sem != null && !sem.getConfigurations().isEmpty()) {
                            filteredBySemantics = true;
                            for (Configuration conf : sem.getConfigurations()) {
                                for (BasicStateProposition bsp : conf.getBasicStatePropositions()) {
                                    candidateStrings.add(bsp.toString());
                                }
                            }
                        }
                    }
                }
            }

            if (filteredBySemantics) {
                boolean added = false;
                for (SMProposition guardOption : guards) {
                    if (!(guardOption instanceof BasicStateProposition)) continue;
                    if (candidateStrings.contains(guardOption.toString())) {
                        if (shouldFilterTriggeredGuardOption(guardOption, srcState, associatedTransition, otherTriggeredCoverage)) {
                            continue;
                        }
                        JMenuItem item = new JMenuItem(guardOption.toString());
                        item.addActionListener(ev -> {
                            applyGuard(guardOption);
                        });
                        popup.add(item);
                        added = true;
                    }
                }
                if (!added) {
                    JMenuItem none = new JMenuItem("No guards available");
                    none.setEnabled(false);
                    popup.add(none);
                }
            } else {
                if (guards.isEmpty()) {
                    JMenuItem none = new JMenuItem("No guards available");
                    none.setEnabled(false);
                    popup.add(none);
                } else {
                    for (SMProposition guardOption : guards) {
                        if (guardOption instanceof BasicStateProposition
                                && shouldFilterTriggeredGuardOption(guardOption, srcState, associatedTransition, otherTriggeredCoverage)) {
                            continue;
                        }
                        JMenuItem item = new JMenuItem(guardOption.toString());
                        item.addActionListener(ev -> {
                            applyGuard(guardOption);
                        });
                        popup.add(item);
                    }
                }
            }
        } else {
            // Guard is already set - show "Remove guard" and options
            JMenuItem removeItem = new JMenuItem("Remove guard");
            removeItem.addActionListener(ev -> {
                // Determine the appropriate default guard based on transition type
                // Initial transitions (from pseudo-state) should reset to TRUE (fire at startup)
                // Autonomous transitions should reset to FALSE (placeholder)
                SMProposition defaultGuard;
                boolean isInitialTransition = associatedTransition instanceof PWSTransition pt && pt.isInitialTransition();
                
                if (isInitialTransition) {
                    // Initial transitions reset to TRUE - "fire at startup"
                    defaultGuard = new TrueProposition();
                } else {
                    // Other transitions reset to FALSE - placeholder indicating guard needs to be set
                    defaultGuard = new FalseProposition();
                }
                
                applyGuard(defaultGuard);
            });
            popup.add(removeItem);
            
            // Add separator before extend options
            popup.addSeparator();
            
            // Get all available guards
            List<? extends SMProposition> guards = assembly.getAssemblyGuards();
            
            // Check if this is a TRUE autonomous transition (not initial from pseudo-state)
            // Initial transitions from pseudo-state are event-triggered with "_init"
            boolean isTrueAutonomousTransition = false;
            if (associatedTransition != null && associatedTransition.isAutonomous()) {
                machinery.StateInterface src = associatedTransition.getSource();
                if (src instanceof PWSState p && !p.isPseudoState()) {
                    isTrueAutonomousTransition = true;
                }
            }
            
            if (isTrueAutonomousTransition) {
                // For true autonomous transitions: show only available exit zones for replacement (no AND)
                Set<String> candidateStrings = new LinkedHashSet<>();
                machinery.StateInterface src = associatedTransition.getSource();
                if (src instanceof PWSState p) {
                    candidateStrings.addAll(collectEligibleAutonomousExitTargets(p, associatedTransition));
                }
                
                // Show available exit zones (excluding the current guard)
                String currentGuardStr = content.toString();
                boolean hasOptions = false;
                for (SMProposition guardOption : guards) {
                    if (!(guardOption instanceof BasicStateProposition)) continue;
                    String guardStr = guardOption.toString();
                    // Skip if not in available exit zones or if it's the current guard
                    if (!candidateStrings.contains(guardStr) || guardStr.equals(currentGuardStr)) continue;
                    
                    JMenuItem item = new JMenuItem("Change to: " + guardStr);
                    item.addActionListener(ev -> {
                        applyGuard(guardOption);
                    });
                    popup.add(item);
                    hasOptions = true;
                }
                
                if (!hasOptions) {
                    JMenuItem none = new JMenuItem("No other exit zones available");
                    none.setEnabled(false);
                    popup.add(none);
                }
            } else {
                // Non-autonomous transitions: allow AND conjunction to extend the guard
                // Extract already used machine IDs from current guard
                Set<String> usedMachineIds = extractMachineIds(content);
                String triggerKey = getTriggeredPartitionKey(associatedTransition, srcState);
                if (triggerKey != null) {
                    otherTriggeredCoverage = collectTriggeredCoverage(srcState, associatedTransition, triggerKey);
                }
                
                // Filter guards to show only those not already in the conjunction
                // and that refer to different machines
                boolean hasExtendOptions = false;
                for (SMProposition guardOption : guards) {
                    if (!(guardOption instanceof BasicStateProposition bsp)) continue;
                    
                    // Skip if this machine is already used in the guard
                    if (usedMachineIds.contains(bsp.getMachineId())) continue;
                    
                    SMProposition newGuard = new AndProposition(content, guardOption);
                    if (shouldFilterTriggeredGuardOption(newGuard, srcState, associatedTransition, otherTriggeredCoverage)) {
                        continue;
                    }
                    JMenuItem item = new JMenuItem("Add: " + guardOption.toString());
                    item.addActionListener(ev -> {
                        applyGuard(newGuard);
                    });
                    popup.add(item);
                    hasExtendOptions = true;
                }
                
                if (!hasExtendOptions) {
                    JMenuItem none = new JMenuItem("No additional guards available");
                    none.setEnabled(false);
                    popup.add(none);
                }
            }
        }
        maybeAddAdvancedGuardEditor(popup);
        popup.show(this, e.getX(), e.getY());
    }

    private void maybeAddAdvancedGuardEditor(JPopupMenu popup) {
        if (associatedTransition instanceof PWSTransition pt && pt.isTriggerable()) {
            popup.addSeparator();
            JMenuItem advancedItem = new JMenuItem("Advanced guard editor...");
            advancedItem.addActionListener(ae -> {
                Window owner = SwingUtilities.getWindowAncestor(GuardAnnotation.this);
                GuardEditorDialog dialog = new GuardEditorDialog(owner, pt, assembly, this::applyGuard);
                dialog.setVisible(true);
            });
            popup.add(advancedItem);
        }
    }

    private void applyGuard(SMProposition guard) {
        setContent(guard);
        updateCallback.accept(guard);
        java.awt.Window w = SwingUtilities.getWindowAncestor(GuardAnnotation.this);
        if (w instanceof pws.editor.PWSEditor pe) {
            pe.markDocumentDirty();
            pe.scheduleSemanticsRecalculation();
        }
        revalidate();
        repaint();
        if (getParent() != null) {
            getParent().revalidate();
            getParent().repaint();
        }
    }

    private boolean isTriggeredPartitionContext(TransitionInterface transition, PWSState srcState) {
        return getTriggeredPartitionKey(transition, srcState) != null;
    }

    private String getTriggeredPartitionKey(TransitionInterface transition, PWSState srcState) {
        if (transition == null || srcState == null) return null;
        if (!(transition instanceof PWSTransition pt)) return null;
        if (!pt.isEnabled()) return null;
        if (pt.isTriggerable() && pt.getTriggerEvent() != null && !pt.getTriggerEvent().isEmpty()) {
            return pt.getTriggerEvent();
        }
        if (pt.isInitialTransition()) {
            return PWSTransition.INIT_TRIGGER_EVENT;
        }
        return null;
    }

    private Semantics collectTriggeredCoverage(PWSState srcState, TransitionInterface self, String triggerKey) {
        if (assembly == null || srcState == null || triggerKey == null || triggerKey.isEmpty()) {
            return null;
        }
        Semantics union = new Semantics(assembly.getAssemblyId());
        for (TransitionInterface ti : srcState.getOutgoingTransitions()) {
            if (ti == self) continue;
            if (!(ti instanceof PWSTransition pt)) continue;
            String otherKey = getTriggeredPartitionKey(pt, srcState);
            if (otherKey == null || !triggerKey.equals(otherKey)) continue;
            SMProposition guard = pt.getGuardProposition();
            if (guard == null) continue;
            Semantics guardSem = guard.toSemantics(assembly);
            Semantics stateSem = srcState.getStateSemantics();
            if (stateSem != null) {
                guardSem = guardSem.AND(stateSem);
            }
            if (!guardSem.ISEMPTY()) {
                union = union.OR(guardSem);
            }
        }
        return union;
    }

    private boolean shouldFilterTriggeredGuardOption(SMProposition guardOption,
                                                     PWSState srcState,
                                                     TransitionInterface self,
                                                     Semantics otherCoverage) {
        if (guardOption == null || assembly == null || srcState == null) return false;
        if (!isTriggeredPartitionContext(self, srcState)) return false;
        Semantics candidateSem = guardOption.toSemantics(assembly);
        Semantics stateSem = srcState.getStateSemantics();
        if (stateSem != null) {
            candidateSem = candidateSem.AND(stateSem);
        }
        if (candidateSem.ISEMPTY()) {
            return true;
        }
        if (otherCoverage == null || otherCoverage.ISEMPTY()) {
            return false;
        }
        return !candidateSem.AND(otherCoverage).ISEMPTY();
    }

    /**
     * Collects eligible exit-zone targets for autonomous transitions.
     * Includes CS-only exit zones (provisional) and excludes those already covered by other
     * enabled autonomous PWS transitions from the same source state.
     * Internal (gray) exit zones are excluded for SS-based zones; CS-only zones are kept.
     */
    private Set<String> collectEligibleAutonomousExitTargets(PWSState srcState, TransitionInterface self) {
        Set<String> eligible = new LinkedHashSet<>();
        if (srcState == null) {
            return eligible;
        }
        Set<String> coveredByOthers = new HashSet<>();
        for (TransitionInterface ti : srcState.getOutgoingTransitions()) {
            if (ti == null || ti == self) continue;
            if (!(ti instanceof PWSTransition pt)) continue;
            if (!pt.isEnabled() || pt.isTriggerable()) continue;
            if (pt.getGuardProposition() instanceof BasicStateProposition bsp) {
                coveredByOthers.add(bsp.toString());
            }
        }
        Semantics stateSem = srcState.getStateSemantics();
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
        if (zones.isEmpty()) {
            return eligible;
        }
        for (ExitZone zone : zones) {
            if (zone == null || zone.getTarget() == null) continue;
            BasicStateProposition target = zone.getTarget();
            String targetStr = target.toString();
            if (coveredByOthers.contains(targetStr)) continue;
            boolean isCsOnly = csOnly != null && csOnly.contains(zone);
            if (!isCsOnly && assembly != null && stateSem != null) {
                try {
                    Semantics targetAndSem = target.toSemantics(assembly).AND(stateSem);
                    if (!targetAndSem.ISEMPTY()) {
                        continue; // internal (gray) exit zone
                    }
                } catch (Exception ignored) {
                    // If semantics calculation fails, don't classify as internal.
                }
            }
            eligible.add(targetStr);
        }
        return eligible;
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

        Semantics stateSem = srcState.getStateSemantics();
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
            if (!isCsOnly && assembly != null && stateSem != null) {
                try {
                    Semantics targetAndSem = target.toSemantics(assembly).AND(stateSem);
                    if (!targetAndSem.ISEMPTY()) {
                        continue; // internal (gray) exit zone
                    }
                } catch (Exception ignored) {
                    // If semantics calculation fails, don't classify as internal.
                }
            }
            targets.add(target.toString());
        }
        return targets;
    }

    private boolean hasAnyExitZones(PWSState state) {
        if (state == null) return false;
        Set<ExitZone> reactive = state.getReactiveSemantics();
        if (reactive != null && !reactive.isEmpty()) {
            return true;
        }
        Set<ExitZone> csOnly = state.getCsOnlyExitZones();
        return csOnly != null && !csOnly.isEmpty();
    }
    
    /**
     * Extracts all machine IDs used in the given proposition.
     * Recursively traverses AND propositions to collect all BasicStateProposition machine IDs.
     *
     * @param prop the proposition to analyze
     * @return set of machine IDs found in the proposition
     */
    private Set<String> extractMachineIds(SMProposition prop) {
        Set<String> machineIds = new HashSet<>();
        extractMachineIdsRecursive(prop, machineIds);
        return machineIds;
    }
    
    /**
     * Recursive helper to extract machine IDs from a proposition.
     */
    private void extractMachineIdsRecursive(SMProposition prop, Set<String> machineIds) {
        if (prop instanceof BasicStateProposition bsp) {
            machineIds.add(bsp.getMachineId());
        } else if (prop instanceof AndProposition and) {
            extractMachineIdsRecursive(and.getLeft(), machineIds);
            extractMachineIdsRecursive(and.getRight(), machineIds);
        }
        // TrueProposition and other types don't contribute machine IDs
    }

//    @Override
//    protected void paintComponent(Graphics g) {
//        super.paintComponent(g);
//        Graphics2D g2d = (Graphics2D) g;
//        g2d.setFont(getFont().deriveFont(Font.PLAIN, 12f));
//        g2d.setColor(Color.BLACK);
//        String text = buildDisplayText();
//        FontMetrics fm = g2d.getFontMetrics();
//        int textWidth = fm.stringWidth(text);
//        int textHeight = fm.getAscent();
//        int x = (getWidth() - textWidth) / 2;
//        int y = (getHeight() + textHeight) / 2 - 2;
//        g2d.drawString(text, x, y);
//    }
}
