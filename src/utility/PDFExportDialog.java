package utility;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

/** Shared PDF export destination chooser with file and clipboard options. */
public final class PDFExportDialog {
    private PDFExportDialog() {
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
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("PDF File", "pdf"));
        fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
        fileChooser.setControlButtonsAreShown(false);

        String title = UIManager.getString("FileChooser.saveDialogTitleText");
        if (title == null || title.trim().isEmpty()) title = "Save";
        String saveText = UIManager.getString("FileChooser.saveButtonText");
        if (saveText == null || saveText.trim().isEmpty()) saveText = "Save";
        String cancelText = UIManager.getString("FileChooser.cancelButtonText");
        if (cancelText == null || cancelText.trim().isEmpty()) cancelText = "Cancel";

        Window owner = (parent != null) ? javax.swing.SwingUtilities.getWindowAncestor(parent) : null;
        JDialog dialog = createModalDialog(owner, title);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout());
        dialog.add(fileChooser, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clipboardButton = new JButton("Save to Clipboard");
        JButton cancelButton = new JButton(cancelText);
        JButton saveButton = new JButton(saveText);
        buttonRow.add(clipboardButton);
        buttonRow.add(cancelButton);
        buttonRow.add(saveButton);
        dialog.add(buttonRow, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(saveButton);

        AtomicReference<Result> resultRef = new AtomicReference<>(new Result(Destination.CANCEL, null));

        fileChooser.addActionListener((ActionEvent e) -> {
            String cmd = e.getActionCommand();
            if (JFileChooser.APPROVE_SELECTION.equals(cmd)) {
                File selectedFile = normalizePdfExtension(fileChooser.getSelectedFile());
                if (selectedFile != null) {
                    resultRef.set(new Result(Destination.FILE, selectedFile));
                }
                dialog.dispose();
            } else if (JFileChooser.CANCEL_SELECTION.equals(cmd)) {
                resultRef.set(new Result(Destination.CANCEL, null));
                dialog.dispose();
            }
        });

        saveButton.addActionListener((ActionEvent e) -> fileChooser.approveSelection());
        cancelButton.addActionListener((ActionEvent e) -> fileChooser.cancelSelection());
        clipboardButton.addActionListener((ActionEvent e) -> {
            resultRef.set(new Result(Destination.CLIPBOARD, null));
            dialog.dispose();
        });

        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return resultRef.get();
    }

    private static JDialog createModalDialog(Window owner, String title) {
        if (owner instanceof Frame frameOwner) {
            return new JDialog(frameOwner, title, Dialog.ModalityType.APPLICATION_MODAL);
        }
        if (owner instanceof Dialog dialogOwner) {
            return new JDialog(dialogOwner, title, Dialog.ModalityType.APPLICATION_MODAL);
        }
        return new JDialog((Frame) null, title, true);
    }

    private static File normalizePdfExtension(File file) {
        if (file == null) return null;
        if (!file.getName().toLowerCase().endsWith(".pdf")) {
            file = new File(file.getAbsolutePath() + ".pdf");
        }
        return file;
    }
}
