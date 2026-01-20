package pws.editor;

import javax.swing.*;
import java.io.*;
import pws.PWSStateMachine;
import serializer.BinaryModelSerializer;

/**
 * Minimal file manager for PWS: New, Open, Save, Save As using existing serializer and
 * the panel annotation stream methods.
 */
public class PWSFileManager {
    private final PWSEditor editor;

    public PWSFileManager(PWSEditor editor) {
        this.editor = editor;
    }

    public void newDocument() {
        PWSStateMachine model = new PWSStateMachine("Untitled");
        PWSDocument doc = new PWSDocument(model, model.getAssembly().getMachineLibrary());
        // New documents should start clean (no unsaved changes)
        doc.setDirty(false);
        editor.setDocument(doc);
        editor.rebuildUIForNewModel(model);
        editor.updateWindowTitle();
    }

    public void open() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PWS Workspace (.pws)", "pws"));
        if (fc.showOpenDialog(editor) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            try {
                Object[] pair = BinaryModelSerializer.loadModelAndLibrary(file.getAbsolutePath());
                Object loadedModel = pair[0];
                Object libOrAnn = pair[1];
                if (loadedModel instanceof PWSStateMachine) {
                    PWSStateMachine model = (PWSStateMachine) loadedModel;
                    PWSDocument doc = new PWSDocument(model, model.getAssembly().getMachineLibrary());
                    doc.setFile(file);
                    doc.setDirty(false);
                    editor.setDocument(doc);
                    editor.rebuildUIForNewModel(model);

                    // Attempt to restore annotations. The file format may contain the annotations
                    // as the second object (older files) or as a third appended object. Try both.
                    try {
                        // Case 1: BinaryModelSerializer returned a byte[] as second object
                        if (libOrAnn instanceof byte[]) {
                            byte[] annotationsBytes = (byte[]) libOrAnn;
                            try (ObjectInputStream annIn = new ObjectInputStream(new ByteArrayInputStream(annotationsBytes))) {
                                ((PWSStateMachinePanel) editor.getBaseEditor().getStateMachinePanel()).loadAnnotationsFromStream(annIn);
                            }
                        } else {
                            // Case 2: Try to read a third object from the file (annotations appended after model+lib)
                            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                                // skip model
                                try { ois.readObject(); } catch (Exception ignore) {}
                                // skip library or annotations placeholder
                                try { ois.readObject(); } catch (Exception ignore) {}
                                try {
                                    Object maybeAnn = ois.readObject();
                                    if (maybeAnn instanceof byte[]) {
                                        byte[] annotationsBytes = (byte[]) maybeAnn;
                                        try (ObjectInputStream annIn = new ObjectInputStream(new ByteArrayInputStream(annotationsBytes))) {
                                            ((PWSStateMachinePanel) editor.getBaseEditor().getStateMachinePanel()).loadAnnotationsFromStream(annIn);
                                        }
                                    }
                                } catch (EOFException eof) {
                                    // no annotations present
                                }
                            } catch (IOException | ClassNotFoundException ex) {
                                // non-fatal: annotations may not be present or in older format
                            }
                        }

                        // Ensure dashboards are visible and restored
                        try {
                            PWSStateMachinePanel panel = (PWSStateMachinePanel) editor.getBaseEditor().getStateMachinePanel();
                            panel.setShowStateAnnotations(true);
                            panel.restoreVisibleStateAnnotations();
                            panel.repaint();
                        } catch (Exception ignore) {}
                    } catch (IOException | ClassNotFoundException ex) {
                        // Non-fatal: show a warning but continue
                        JOptionPane.showMessageDialog(editor, "Warning: annotations could not be restored: " + ex.getMessage(), "Warning", JOptionPane.WARNING_MESSAGE);
                    }

                    // Loaded from disk, treat as not dirty
                    doc.setDirty(false);
                    editor.updateWindowTitle();
                } else {
                    JOptionPane.showMessageDialog(editor, "The selected file does not contain a valid model.");
                }
            } catch (IOException | ClassNotFoundException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(editor, "Error opening file: " + ex.getMessage());
            }
        }
    }

    public boolean saveAs() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PWS Workspace (.pws)", "pws"));
        if (fc.showSaveDialog(editor) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".pws")) {
                file = new File(file.getAbsolutePath() + ".pws");
            }
            return saveToFile(file);
        }
        return false;
    }

    public boolean save() {
        PWSDocument doc = editor.getDocument();
        if (doc == null) return false;
        File f = doc.getFile();
        if (f == null) return saveAs();
        return saveToFile(f);
    }

    private boolean saveToFile(File file) {
        try {
            PWSDocument doc = editor.getDocument();
            if (doc == null) return false;
            PWSStateMachine model = doc.getModel();

            // Serialize annotations into bytes first
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                ((PWSStateMachinePanel) editor.getBaseEditor().getStateMachinePanel()).saveAnnotationsToStream(oos);
            }
            byte[] ann = baos.toByteArray();

            // Save model + library using serializer
            BinaryModelSerializer.saveModelAndLibrary(model, model.getAssembly().getMachineLibrary(), file.getAbsolutePath());

            // Append annotations bytes
            try (FileOutputStream fos = new FileOutputStream(file, true);
                 AppendingObjectOutputStream aout = new AppendingObjectOutputStream(fos)) {
                aout.writeObject(ann);
                aout.flush();
            }

            doc.setFile(file);
            doc.setDirty(false);
            editor.updateWindowTitle();
            return true;
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(editor, "Error saving: " + ex.getMessage());
            return false;
        }
    }

    // Helper to append objects without writing a new stream header
    private static class AppendingObjectOutputStream extends ObjectOutputStream {
        public AppendingObjectOutputStream(OutputStream out) throws IOException {
            super(out);
        }

        @Override
        protected void writeStreamHeader() throws IOException {
            // Do not write a header when appending
        }
    }
}
