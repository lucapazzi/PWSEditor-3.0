package pws.editor;

import pws.editor.semantics.ExitZone;
import pws.editor.semantics.Semantics;
import smalgebra.BasicStateProposition;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;
import java.util.Set;

/** Simple panel that renders initial configurations for the current assembly. */
public class InitialConfigurationsPanel extends JPanel {
    private static final Color PLACEHOLDER_COLOR = new Color(120, 120, 120);
    private static final Color SECTION_COLOR = new Color(150, 150, 150);
    private static final Color TEXT_GREEN = Color.GREEN.darker();
    private static final Color BORDER_COLOR = new Color(180, 180, 180);
    private static final String EXIT_ZONE_ARROW = "→";
    private final JTextPane configsArea;
    private final JTextPane exitZonesArea;
    private final JTextPane closureArea;

    public InitialConfigurationsPanel() {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        setBorder(new RoundedLineBorder(BORDER_COLOR, 8, 1));

        configsArea = createTextPane();
        exitZonesArea = createTextPane();
        closureArea = createTextPane();
        JPanel content = new JPanel(new GridLayout(3, 1));
        content.setOpaque(false);
        content.add(buildRow("initial configs", configsArea, false));
        content.add(buildRow("exit zones", exitZonesArea, true));
        content.add(buildRow("closure", closureArea, true));
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

    private JTextPane createTextPane() {
        JTextPane pane = new JTextPane();
        pane.setEditable(false);
        pane.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        pane.setBackground(Color.WHITE);
        pane.setFont(getNormalFont());
        return pane;
    }

    private JPanel buildRow(String labelText, JTextPane area, boolean topSeparator) {
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
        if (semantics == null) {
            setConfigsPlaceholder("No initial configurations.");
            return;
        }
        Collection<?> configs = semantics.getConfigurations();
        if (configs == null || configs.isEmpty()) {
            setConfigsPlaceholder("No initial configurations.");
            return;
        }
        StringJoiner joiner = new StringJoiner(" ");
        for (Object cfg : configs) {
            if (cfg != null) {
                joiner.add(cfg.toString());
            }
        }
        String text = joiner.toString().trim();
        if (text.isEmpty()) {
            setConfigsPlaceholder("No initial configurations.");
            return;
        }
        setText(configsArea, text, false);
    }

    public void setExitZones(Set<ExitZone> zones, boolean showMachineIds) {
        if (zones == null || zones.isEmpty()) {
            setExitZonesPlaceholder("No exit zones.");
            return;
        }
        List<String> labels = new ArrayList<>(zones.size());
        for (ExitZone ez : zones) {
            labels.add(formatExitZoneLabel(ez, showMachineIds));
        }
        Collections.sort(labels);
        StringJoiner joiner = new StringJoiner(", ");
        for (String label : labels) {
            if (label != null && !label.isBlank()) {
                joiner.add(label);
            }
        }
        String text = joiner.toString().trim();
        if (text.isEmpty()) {
            setExitZonesPlaceholder("No exit zones.");
            return;
        }
        setText(exitZonesArea, text, false);
    }

    public void setClosure(Semantics semantics) {
        if (semantics == null) {
            setClosurePlaceholder("No closure configurations.");
            return;
        }
        Collection<?> configs = semantics.getConfigurations();
        if (configs == null || configs.isEmpty()) {
            setClosurePlaceholder("No closure configurations.");
            return;
        }
        StringJoiner joiner = new StringJoiner(" ");
        for (Object cfg : configs) {
            if (cfg != null) {
                joiner.add(cfg.toString());
            }
        }
        String text = joiner.toString().trim();
        if (text.isEmpty()) {
            setClosurePlaceholder("No closure configurations.");
            return;
        }
        setText(closureArea, text, false);
    }

    public void setPlaceholder(String text) {
        setText(configsArea, text, true);
        setText(exitZonesArea, text, true);
        setText(closureArea, text, true);
    }

    private void setConfigsPlaceholder(String text) {
        setText(configsArea, text, true);
    }

    private void setExitZonesPlaceholder(String text) {
        setText(exitZonesArea, text, true);
    }

    private void setClosurePlaceholder(String text) {
        setText(closureArea, text, true);
    }

    private void setText(JTextPane area, String text, boolean placeholder) {
        Color color = placeholder ? PLACEHOLDER_COLOR : TEXT_GREEN;
        String value = text != null ? text : "";
        area.setText(value);
        StyledDocument doc = area.getStyledDocument();
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setAlignment(attrs, StyleConstants.ALIGN_CENTER);
        StyleConstants.setForeground(attrs, color);
        doc.setParagraphAttributes(0, doc.getLength(), attrs, true);
        area.setCaretPosition(0);
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
