package pws.editor.annotation;

import assembly.Assembly;
import assembly.AssemblyInterface;
import smalgebra.AndProposition;
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
import java.util.function.Consumer;
import pws.PWSState;
import pws.editor.semantics.Configuration;
import pws.editor.semantics.Semantics;
import machinery.StateMachine;
import machinery.TransitionInterface;

/** Annotation widget for editing guard propositions. */
public class GuardAnnotation extends Annotation<SMProposition> {
    private Assembly assembly;
    private Consumer<SMProposition> updateCallback;
    private TransitionInterface associatedTransition;
    
    // Problem status for coloring
    private boolean isProblematic = false;
    private String problemReason = null;

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
        // Return the text with square brackets.
        return "[" + (content == null ? "" : content.toString()) + "]";
    }
    
    /**
     * Checks if this guard is problematic and needs red highlighting.
     * Problematic conditions:
     * - FALSE guard (placeholder that needs to be set)
     * - Orphan guard (references exit zone that no longer exists)
     */
    private void checkProblematicStatus() {
        isProblematic = false;
        problemReason = null;
        
        if (content == null) return;
        
        // Check for FALSE guard - placeholder
        if (content instanceof FalseProposition) {
            isProblematic = true;
            problemReason = "FALSE guard - transition will never fire";
            return;
        }
        
        // Check for orphan guard (guard references exit zone that doesn't exist)
        if (associatedTransition != null && associatedTransition.isAutonomous() 
                && content instanceof BasicStateProposition bsp) {
            machinery.StateInterface src = associatedTransition.getSource();
            if (src instanceof PWSState ps && !ps.isPseudoState()) {
                java.util.HashSet<pws.editor.semantics.ExitZone> reactive = ps.getReactiveSemantics();
                if (reactive != null && !reactive.isEmpty()) {
                    // Check if the guard's target exists in any exit zone
                    boolean found = false;
                    for (pws.editor.semantics.ExitZone zone : reactive) {
                        if (zone != null && zone.getTarget() != null 
                                && zone.getTarget().toString().equals(bsp.toString())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        isProblematic = true;
                        problemReason = "Orphan guard - exit zone no longer exists";
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
        g2d.setFont(getFont().deriveFont(Font.PLAIN, 12f));
        
        // Set color based on problematic status
        if (isProblematic) {
            g2d.setColor(new Color(180, 0, 0)); // Red for problematic
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
        if (isProblematic && problemReason != null) {
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
        if (associatedTransition != null) {
            machinery.StateInterface src = associatedTransition.getSource();
            if (src instanceof pws.PWSState ps) {
                srcState = ps;
                isTrueAutonomous = associatedTransition.isAutonomous() && !ps.isPseudoState();
            }
        }
        boolean hasExitZones = srcState != null
                && srcState.getReactiveSemantics() != null
                && !srcState.getReactiveSemantics().isEmpty();

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
            List list = assembly.getAssemblyGuards();
            List<SMProposition> guards = (List<SMProposition>) list;

            // If associatedTransition and source has semantics, filter guards appropriately.
            // For TRUE autonomous transitions (not initial) use reactiveSemantics (ExitZone targets).
            // Initial transitions (from pseudo-state) and triggered transitions use stateSemantics.
            boolean filteredBySemantics = false;
            Set<String> candidateStrings = new LinkedHashSet<>();
            if (associatedTransition != null) {
                machinery.StateInterface src = associatedTransition.getSource();
                if (src instanceof PWSState p) {
                    // Check if this is a TRUE autonomous transition (not from pseudo-state)
                    // Initial transitions from pseudo-state are event-triggered with hidden startup event
                    if (isTrueAutonomous) {
                        // True autonomous transitions -> use reactiveSemantics ExitZone targets
                        java.util.HashSet<pws.editor.semantics.ExitZone> reactive = p.getReactiveSemantics();
                        if (reactive != null && !reactive.isEmpty()) {
                            filteredBySemantics = true;
                            for (pws.editor.semantics.ExitZone zone : reactive) {
                                if (zone != null && zone.getTarget() != null) {
                                    candidateStrings.add(zone.getTarget().toString());
                                }
                            }
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
                for (SMProposition guardOption : guards) {
                    if (!(guardOption instanceof BasicStateProposition)) continue;
                    if (candidateStrings.contains(guardOption.toString())) {
                        JMenuItem item = new JMenuItem(guardOption.toString());
                        item.addActionListener(ev -> {
                            applyGuard(guardOption);
                        });
                        popup.add(item);
                    }
                }
                if (popup.getComponentCount() == 0) {
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
                boolean isInitialTransition = false;
                if (associatedTransition != null) {
                    machinery.StateInterface src = associatedTransition.getSource();
                    if (src instanceof PWSState ps && ps.isPseudoState()) {
                        isInitialTransition = true;
                    }
                }
                
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
            List list = assembly.getAssemblyGuards();
            List<SMProposition> guards = (List<SMProposition>) list;
            
            // Check if this is a TRUE autonomous transition (not initial from pseudo-state)
            // Initial transitions from pseudo-state are event-triggered with hidden startup event
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
                    java.util.HashSet<pws.editor.semantics.ExitZone> reactive = p.getReactiveSemantics();
                    if (reactive != null && !reactive.isEmpty()) {
                        for (pws.editor.semantics.ExitZone zone : reactive) {
                            if (zone != null && zone.getTarget() != null) {
                                candidateStrings.add(zone.getTarget().toString());
                            }
                        }
                    }
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
                
                // Filter guards to show only those not already in the conjunction
                // and that refer to different machines
                boolean hasExtendOptions = false;
                for (SMProposition guardOption : guards) {
                    if (!(guardOption instanceof BasicStateProposition bsp)) continue;
                    
                    // Skip if this machine is already used in the guard
                    if (usedMachineIds.contains(bsp.getMachineId())) continue;
                    
                    JMenuItem item = new JMenuItem("Add: " + guardOption.toString());
                    item.addActionListener(ev -> {
                        // Create AND proposition with existing guard and new proposition
                        SMProposition newGuard = new AndProposition(content, guardOption);
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
        popup.show(this, e.getX(), e.getY());
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
