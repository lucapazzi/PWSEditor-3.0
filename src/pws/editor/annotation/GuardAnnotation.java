package pws.editor.annotation;

import assembly.Assembly;
import assembly.AssemblyInterface;
import smalgebra.SMProposition;
import smalgebra.TrueProposition;
import smalgebra.BasicStateProposition;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import pws.PWSState;
import pws.editor.semantics.Configuration;
import pws.editor.semantics.Semantics;
import machinery.StateMachine;
import machinery.TransitionInterface;

public class GuardAnnotation extends Annotation<SMProposition> {
    private Assembly assembly;
    private Consumer<SMProposition> updateCallback;
    private TransitionInterface associatedTransition;

    public GuardAnnotation(SMProposition content, Assembly assembly, Consumer<SMProposition> updateCallback) {
        super(content);
        this.assembly = assembly;
        this.updateCallback = updateCallback;
        this.associatedTransition = null;
    }

    public GuardAnnotation(SMProposition content, Assembly assembly, Consumer<SMProposition> updateCallback, TransitionInterface associatedTransition) {
        super(content);
        this.assembly = assembly;
        this.updateCallback = updateCallback;
        this.associatedTransition = associatedTransition;
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
            JMenuItem removeItem = new JMenuItem("Remove guard");
            removeItem.addActionListener(ev -> {
                SMProposition defaultGuard = new TrueProposition();
                setContent(defaultGuard);
                updateCallback.accept(defaultGuard);
                repaint();
            });
            popup.add(removeItem);
        }
        popup.show(this, e.getX(), e.getY());
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