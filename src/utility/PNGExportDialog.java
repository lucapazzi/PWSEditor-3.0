package utility;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.File;

/** Shared PNG export destination chooser with file and clipboard options. */
public final class PNGExportDialog {
    private PNGExportDialog() {
    }

    public enum Destination {
        FILE,
        CLIPBOARD,
        CANCEL
    }

    public static final class Result {
        private final Destination destination;
        private final File file;

        private Result(Destination destination, File file) {
            this.destination = destination;
            this.file = file;
        }

        public Destination destination() {
            return destination;
        }

        public File file() {
            return file;
        }
    }

    public static Result showSaveDialog(Component parent) {
        Destination destination = chooseDestination(parent);
        if (destination == Destination.CLIPBOARD) {
            return new Result(Destination.CLIPBOARD, null);
        }
        if (destination != Destination.FILE) {
            return new Result(Destination.CANCEL, null);
        }

        File selectedFile = chooseFile(parent);
        if (selectedFile == null) {
            return new Result(Destination.CANCEL, null);
        }
        return new Result(Destination.FILE, selectedFile);
    }

    private static Destination chooseDestination(Component parent) {
        Object[] options = {"Save to File", "Save to Clipboard", "Cancel"};
        int choice = JOptionPane.showOptionDialog(
                parent,
                "Choose the export destination.",
                "Export as PNG",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);
        if (choice == 0) return Destination.FILE;
        if (choice == 1) return Destination.CLIPBOARD;
        return Destination.CANCEL;
    }

    private static File chooseFile(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogType(JFileChooser.SAVE_DIALOG);
        chooser.setFileFilter(new FileNameExtensionFilter("PNG Image", "png"));

        while (true) {
            int option = chooser.showSaveDialog(parent);
            if (option != JFileChooser.APPROVE_OPTION) {
                return null;
            }

            File selectedFile = normalizePngExtension(chooser.getSelectedFile());
            if (selectedFile == null) {
                JOptionPane.showMessageDialog(
                        parent,
                        "Please choose a file name.",
                        "No File Selected",
                        JOptionPane.WARNING_MESSAGE);
                continue;
            }
            if (selectedFile.isDirectory()) {
                JOptionPane.showMessageDialog(
                        parent,
                        "Please choose a file, not a folder.",
                        "Invalid Selection",
                        JOptionPane.WARNING_MESSAGE);
                continue;
            }
            if (selectedFile.exists()) {
                int answer = JOptionPane.showConfirmDialog(
                        parent,
                        "The file \"" + selectedFile.getName() + "\" already exists.\nDo you want to replace it?",
                        "Confirm Overwrite",
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (answer == JOptionPane.YES_OPTION) {
                    return selectedFile;
                }
                if (answer == JOptionPane.CANCEL_OPTION || answer == JOptionPane.CLOSED_OPTION) {
                    return null;
                }
                continue;
            }
            return selectedFile;
        }
    }

    private static File normalizePngExtension(File file) {
        if (file == null) return null;
        if (!file.getName().toLowerCase().endsWith(".png")) {
            file = new File(file.getAbsolutePath() + ".png");
        }
        return file;
    }
}
