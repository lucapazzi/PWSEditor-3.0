package utility;

import editor.StateMachinePanel;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * VectorSVGExporter: Exports a state machine panel to SVG format.
 * Embeds the rendered panel as a high-quality PNG image in SVG wrapper.
 */
public class VectorSVGExporter {

    /**
     * Export a state machine panel to an SVG file.
     * @param panel The panel containing the state machine
     * @param file The destination SVG file
     * @throws IOException if writing fails
     */
    public static void exportPanelToVectorSVG(StateMachinePanel panel, File file) throws IOException {
        // Calculate bounds
        int width = panel.getWidth() > 0 ? panel.getWidth() : 800;
        int height = panel.getHeight() > 0 ? panel.getHeight() : 600;

        // Render panel to BufferedImage at high quality
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                Math.max(1, width), Math.max(1, height), java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(java.awt.Color.WHITE);
        g2.fillRect(0, 0, width, height);
        panel.printAll(g2);
        g2.dispose();

        // Convert to base64 PNG
        String b64;
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(img, "png", baos);
            b64 = java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
        }

        // Generate SVG with embedded PNG image
        StringBuilder svg = new StringBuilder();
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width).append("\" height=\"").append(height).append("\" viewBox=\"0 0 ").append(width).append(" ").append(height).append("\">\n");
        svg.append("  <image href=\"data:image/png;base64,").append(b64).append("\" width=\"").append(width).append("\" height=\"").append(height).append("\"/>\n");
        svg.append("</svg>\n");

        // Write to file
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(svg.toString());
        }
    }
}
