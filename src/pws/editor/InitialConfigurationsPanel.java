package pws.editor;

import assembly.Assembly;
import machinery.TransitionInterface;
import pws.PWSStateMachine;
import pws.PWSTransition;
import pws.editor.semantics.Configuration;
import pws.editor.semantics.ExitZone;
import pws.editor.semantics.Semantics;
import smalgebra.BasicStateProposition;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Simple panel that renders initial configurations for the current assembly. */
public class InitialConfigurationsPanel extends JPanel {
    private static final Color PLACEHOLDER_COLOR = new Color(120, 120, 120);
    private static final Color SECTION_COLOR = new Color(150, 150, 150);
    private static final Color TEXT_GREEN = Color.GREEN.darker();
    private static final Color TEXT_RED = new Color(180, 0, 0);
    private static final Color BORDER_COLOR = new Color(180, 180, 180);
    private static final String EXIT_ZONE_ARROW = "→";
    private final ClosureTable closureTable;

    public InitialConfigurationsPanel() {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        setBorder(new RoundedLineBorder(BORDER_COLOR, 8, 1));

        closureTable = new ClosureTable();
        JPanel content = new JPanel(new GridLayout(1, 1));
        content.setOpaque(false);
        content.add(buildRow("closure", closureTable, false));
        add(content, BorderLayout.CENTER);

        setPlaceholder("No controller loaded.");
    }

    private Font getBaseFont() {
        Font f = getFont();
        if (f == null) {
            f = new Font("Dialog", Font.PLAIN, 12);
        }
        return f;
    }

    private Font getNormalFont() {
        return getBaseFont().deriveFont(Font.PLAIN, getBaseFont().getSize2D());
    }

    private Font getSmallFont() {
        float size = Math.max(8f, getBaseFont().getSize2D() - 3f);
        return getBaseFont().deriveFont(Font.ITALIC, size);
    }

    private JPanel buildRow(String labelText, JComponent area, boolean topSeparator) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        if (topSeparator) {
            row.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 200, 200)));
        }
        JLabel label = new JLabel(labelText);
        label.setForeground(SECTION_COLOR);
        label.setBorder(BorderFactory.createEmptyBorder(4, 10, 2, 10));
        label.setFont(getSmallFont());
        row.add(label, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setOpaque(false);
        row.add(scrollPane, BorderLayout.CENTER);
        return row;
    }

    public void setSemantics(Semantics semantics) {
        // Initial configs no longer displayed in this panel.
    }

    public void setExitZones(Set<ExitZone> zones, boolean showMachineIds) {
        // Exit zones no longer displayed in this panel.
    }

    public void setClosure(Semantics semantics, PWSStateMachine machine) {
        if (semantics == null) {
            closureTable.setPlaceholder("No closure configurations.");
            return;
        }
        List<String> machineIds = new ArrayList<>();
        Assembly assembly = (machine != null) ? machine.getAssembly() : null;
        if (assembly != null && assembly.getStateMachines() != null) {
            machineIds.addAll(assembly.getStateMachines().keySet());
        }
        closureTable.setSemantics(semantics, machineIds, machine);
    }

    public void setPlaceholder(String text) {
        closureTable.setPlaceholder(text);
    }

    private String formatExitZoneLabel(ExitZone ez, boolean showMachineIds) {
        if (ez == null) {
            return "?";
        }
        BasicStateProposition source = ez.getSource();
        BasicStateProposition target = ez.getTarget();
        if (source == null && target == null) {
            return "?";
        }
        if (source == null) {
            return formatExitZoneState(target, showMachineIds);
        }
        if (target == null) {
            return formatExitZoneState(source, showMachineIds);
        }
        if (showMachineIds) {
            String srcMachine = source.getMachineId() != null ? source.getMachineId() : "?";
            String srcState = source.getStateName() != null ? source.getStateName() : "?";
            String tgtState = target.getStateName() != null ? target.getStateName() : "?";
            return srcMachine + ":" + srcState + EXIT_ZONE_ARROW + tgtState;
        }
        String srcState = source.getStateName() != null ? source.getStateName() : "?";
        String tgtState = target.getStateName() != null ? target.getStateName() : "?";
        return srcState + EXIT_ZONE_ARROW + tgtState;
    }

    private String formatExitZoneState(BasicStateProposition prop, boolean showMachineIds) {
        if (prop == null) {
            return "?";
        }
        if (showMachineIds) {
            String machineId = prop.getMachineId() != null ? prop.getMachineId() : "?";
            String stateName = prop.getStateName() != null ? prop.getStateName() : "?";
            return machineId + ":" + stateName;
        }
        return prop.getStateName() != null ? prop.getStateName() : "?";
    }

    private final class ClosureTable extends JComponent {
        private static final int TABLE_PADDING_X = 8;
        private static final int TABLE_PADDING_Y = 6;
        private static final int COL_GAP = 10;
        private static final int ROW_GAP = 4;

        private List<Configuration> configs = Collections.emptyList();
        private List<String> machineIds = Collections.emptyList();
        private Map<Configuration, Boolean> coverageMap = Collections.emptyMap();
        private final List<HitArea> hitAreas = new ArrayList<>();
        private String placeholderText = "No closure configurations.";
        private boolean showPlaceholder = true;

        private ClosureTable() {
            setOpaque(true);
            setBackground(Color.WHITE);
            ToolTipManager.sharedInstance().registerComponent(this);
            setToolTipText(" ");
        }

        private void setSemantics(Semantics semantics, List<String> machineIds, PWSStateMachine machine) {
            if (semantics == null || semantics.getConfigurations() == null || semantics.getConfigurations().isEmpty()) {
                setPlaceholder("No closure configurations.");
                return;
            }
            List<Configuration> list = new ArrayList<>();
            for (Object cfg : semantics.getConfigurations()) {
                if (cfg instanceof Configuration c) {
                    list.add(c);
                }
            }
            this.configs = list;
            this.machineIds = (machineIds != null) ? new ArrayList<>(machineIds) : Collections.emptyList();
            this.coverageMap = computeCoverage(list, machine);
            this.showPlaceholder = list.isEmpty();
            revalidate();
            repaint();
        }

        private void setPlaceholder(String text) {
            this.placeholderText = text != null ? text : "";
            this.showPlaceholder = true;
            this.coverageMap = Collections.emptyMap();
            revalidate();
            repaint();
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            if (hitAreas.isEmpty()) return null;
            Point p = event.getPoint();
            for (HitArea area : hitAreas) {
                if (area.bounds.contains(p)) {
                    return area.tooltip;
                }
            }
            return null;
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getNormalFont());
            FontMetrics fmSmall = getFontMetrics(getSmallFont());
            if (showPlaceholder || configs.isEmpty()) {
                int w = fm.stringWidth(placeholderText) + TABLE_PADDING_X * 2;
                int h = fm.getHeight() + TABLE_PADDING_Y * 2;
                return new Dimension(Math.max(80, w), Math.max(30, h));
            }
            int[] colWidths = computeColWidths(fm, fmSmall);
            int tableWidth = computeTableWidth(colWidths);
            int headerHeight = machineIds.isEmpty() ? 0 : fmSmall.getHeight();
            int rowsHeight = configs.size() * (fm.getHeight() + ROW_GAP);
            int height = TABLE_PADDING_Y * 2 + headerHeight + rowsHeight;
            return new Dimension(Math.max(80, tableWidth + TABLE_PADDING_X * 2), Math.max(30, height));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(getBackground());
            g2d.fillRect(0, 0, getWidth(), getHeight());
            hitAreas.clear();

            FontMetrics fm = g2d.getFontMetrics(getNormalFont());
            FontMetrics fmSmall = g2d.getFontMetrics(getSmallFont());
            if (showPlaceholder || configs.isEmpty()) {
                g2d.setFont(getNormalFont());
                g2d.setColor(PLACEHOLDER_COLOR);
                String text = placeholderText != null ? placeholderText : "";
                int sw = fm.stringWidth(text);
                int x = Math.max(TABLE_PADDING_X, (getWidth() - sw) / 2);
                int y = Math.max(TABLE_PADDING_Y + fm.getAscent(), (getHeight() + fm.getAscent()) / 2);
                g2d.drawString(text, x, y);
                return;
            }

            int[] colWidths = computeColWidths(fm, fmSmall);
            int tableWidth = computeTableWidth(colWidths);
            int startX = Math.max(TABLE_PADDING_X, (getWidth() - tableWidth) / 2);
            int y = TABLE_PADDING_Y;

            if (!machineIds.isEmpty()) {
                g2d.setFont(getSmallFont());
                g2d.setColor(SECTION_COLOR);
                int headerX = startX;
                int baseline = y + fmSmall.getAscent();
                for (int i = 0; i < machineIds.size(); i++) {
                    String header = machineIds.get(i);
                    int hw = fmSmall.stringWidth(header);
                    g2d.drawString(header, headerX + (colWidths[i] - hw) / 2, baseline);
                    headerX += colWidths[i] + COL_GAP;
                }
                y += fmSmall.getHeight() + ROW_GAP;
            }

            g2d.setFont(getNormalFont());
            for (Configuration cfg : configs) {
                int cellX = startX;
                int baseline = y + fm.getAscent();
                boolean covered = coverageMap.getOrDefault(cfg, false);
                g2d.setColor(covered ? TEXT_GREEN : TEXT_RED);
                int rowStart = startX;
                int rowWidth = tableWidth;
                if (!machineIds.isEmpty()) {
                    for (int i = 0; i < machineIds.size(); i++) {
                        String cell = cfg.getStateName(machineIds.get(i));
                        if (cell == null || cell.isBlank()) {
                            cell = "-";
                        }
                        int cw = fm.stringWidth(cell);
                        g2d.drawString(cell, cellX + (colWidths[i] - cw) / 2, baseline);
                        cellX += colWidths[i] + COL_GAP;
                    }
                } else {
                    String text = cfg.toString();
                    int sw = fm.stringWidth(text);
                    rowStart = Math.max(TABLE_PADDING_X, (getWidth() - sw) / 2);
                    rowWidth = sw;
                    g2d.drawString(text, rowStart, baseline);
                }
                String tip = covered
                        ? "Covered by an initial transition guard."
                        : "Not covered by any initial transition guard.";
                if (rowWidth > 0) {
                    hitAreas.add(new HitArea(
                        new Rectangle(rowStart, baseline - fm.getAscent(), rowWidth, fm.getHeight()),
                        tip));
                }
                y += fm.getHeight() + ROW_GAP;
            }
        }

        private int[] computeColWidths(FontMetrics fm, FontMetrics fmSmall) {
            if (machineIds.isEmpty()) {
                return new int[0];
            }
            int[] colWidths = new int[machineIds.size()];
            for (int i = 0; i < machineIds.size(); i++) {
                int headerWidth = fmSmall.stringWidth(machineIds.get(i));
                int dashWidth = fm.stringWidth("-");
                colWidths[i] = Math.max(headerWidth, dashWidth);
            }
            for (Configuration cfg : configs) {
                for (int i = 0; i < machineIds.size(); i++) {
                    String cell = cfg.getStateName(machineIds.get(i));
                    if (cell == null || cell.isBlank()) {
                        cell = "-";
                    }
                    colWidths[i] = Math.max(colWidths[i], fm.stringWidth(cell));
                }
            }
            return colWidths;
        }

        private int computeTableWidth(int[] colWidths) {
            if (colWidths.length == 0) return 0;
            int width = 0;
            for (int i = 0; i < colWidths.length; i++) {
                width += colWidths[i];
                if (i < colWidths.length - 1) {
                    width += COL_GAP;
                }
            }
            return width;
        }

        private Map<Configuration, Boolean> computeCoverage(List<Configuration> configs, PWSStateMachine machine) {
            Map<Configuration, Boolean> result = new HashMap<>();
            if (configs == null || configs.isEmpty()) {
                return result;
            }
            if (machine == null || machine.getAssembly() == null) {
                for (Configuration cfg : configs) {
                    result.put(cfg, false);
                }
                return result;
            }
            Assembly asm = machine.getAssembly();
            List<PWSTransition> initialTransitions = new ArrayList<>();
            for (TransitionInterface ti : machine.getTransitions()) {
                if (ti instanceof PWSTransition pt && pt.isEnabled() && pt.isInitialTransition()) {
                    initialTransitions.add(pt);
                }
            }
            for (Configuration cfg : configs) {
                boolean covered = false;
                for (PWSTransition pt : initialTransitions) {
                    if (pt.getGuardProposition() == null) {
                        covered = true;
                        break;
                    }
                    try {
                        if (pt.getGuardProposition().evaluateConfiguration(cfg, asm)) {
                            covered = true;
                            break;
                        }
                    } catch (Exception ignore) {
                    }
                }
                result.put(cfg, covered);
            }
            return result;
        }
    }

    private static final class HitArea {
        private final Rectangle bounds;
        private final String tooltip;

        private HitArea(Rectangle bounds, String tooltip) {
            this.bounds = bounds;
            this.tooltip = tooltip;
        }
    }

    private static final class RoundedLineBorder extends AbstractBorder {
        private final Color color;
        private final int radius;
        private final int thickness;

        private RoundedLineBorder(Color color, int radius, int thickness) {
            this.color = color;
            this.radius = radius;
            this.thickness = thickness;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(color);
                g2.setStroke(new BasicStroke(thickness));
                int pad = thickness / 2;
                g2.drawRoundRect(x + pad, y + pad, width - thickness, height - thickness, radius, radius);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(thickness + 4, thickness + 4, thickness + 4, thickness + 4);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            Insets base = getBorderInsets(c);
            insets.top = base.top;
            insets.left = base.left;
            insets.bottom = base.bottom;
            insets.right = base.right;
            return insets;
        }
    }
}
