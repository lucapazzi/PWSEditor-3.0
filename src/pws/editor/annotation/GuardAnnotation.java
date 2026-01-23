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
     * - TRUE guard on autonomous transition (fires immediately - usually unintended)
     *   Exception: Initial transitions (from pseudo-state) are treated as triggered by a hidden startup event
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
        
        // Check for TRUE guard on autonomous transition
        // Exception: Initial transitions (from pseudo-state) are triggered by a hidden startup event,
        // so TRUE is valid for them
        if (content instanceof TrueProposition && associatedTransition != null && associatedTransition.isAutonomous()) {
            machinery.StateInterface src = associatedTransition.getSource();
            boolean isInitialTransition = (src instanceof PWSState ps && ps.isPseudoState());
            if (!isInitialTransition) {
                isProblematic = true;
                problemReason = "TRUE guard on autonomous transition - fires immediately";
                return;
            }
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

        if (content instanceof TrueProposition) {
            List list = assembly.getAssemblyGuards();
            List<SMProposition> guards = (List<SMProposition>) list;

            // If associatedTransition and source has semantics, filter guards appropriately.
            // For autonomous transitions use reactiveSemantics (ExitZone targets).
            // For non-autonomous transitions fall back to stateSemantics as before.
            boolean filteredBySemantics = false;
            Set<String> candidateStrings = new LinkedHashSet<>();
            if (associatedTransition != null) {
                machinery.StateInterface src = associatedTransition.getSource();
                if (src instanceof PWSState p) {
                    // Autonomous transitions -> use reactiveSemantics ExitZone targets
                    if (associatedTransition.isAutonomous()) {
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
                            setContent(guardOption);
                            updateCallback.accept(guardOption);
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
                            setContent(guardOption);
                            updateCallback.accept(guardOption);
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
                        });
                        popup.add(item);
                    }
                }
            }
        } else {
            // Guard is already set - show "Remove guard" and options to extend with AND
            JMenuItem removeItem = new JMenuItem("Remove guard");
            removeItem.addActionListener(ev -> {
                // Reset to FALSE - a placeholder indicating the guard needs to be set
                SMProposition defaultGuard = new FalseProposition();
                setContent(defaultGuard);
                updateCallback.accept(defaultGuard);
                java.awt.Window w = SwingUtilities.getWindowAncestor(GuardAnnotation.this);
                if (w instanceof pws.editor.PWSEditor pe) {
                    pe.markDocumentDirty();
                    pe.scheduleSemanticsRecalculation();
                }
                repaint();
            });
            popup.add(removeItem);
            
            // Add separator before extend options
            popup.addSeparator();
            
            // Get all available guards
            List list = assembly.getAssemblyGuards();
            List<SMProposition> guards = (List<SMProposition>) list;
            
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
                    setContent(newGuard);
                    updateCallback.accept(newGuard);
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
        popup.show(this, e.getX(), e.getY());
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
