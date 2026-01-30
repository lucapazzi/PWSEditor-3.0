package pws.editor;

import javax.swing.*;
import java.io.*;
import java.util.Map;
import assembly.MachineLibrary;
import machinery.StateMachine;
import pws.PWSStateMachine;
import serializer.JsonModelSerializer;

/**
 * Minimal file manager for PWS: New, Open, Save, Save As using JSON serialization.
 */
public class PWSFileManager {
    private final PWSEditor editor;

    public PWSFileManager(PWSEditor editor) {
        this.editor = editor;
    }

    public void newDocument() {
        MachineLibrary preservedLibrary = editor.getCurrentLibrary();
        PWSStateMachine model = new PWSStateMachine("Untitled");
        applyPreservedLibrary(model, preservedLibrary);
        PWSDocument doc = new PWSDocument(model, model.getAssembly().getMachineLibrary());
        // New documents should start clean (no unsaved changes)
        doc.setDirty(false);
        editor.setDocument(doc);
        // Ensure the controller editor is shown for the newly created model
        editor.setControllerEditorVisible(true);
        editor.rebuildUIForNewModel(model);
        editor.updateWindowTitle();
    }

    public void open() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PWS Workspace (.pws)", "pws"));
        if (fc.showOpenDialog(editor) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            try {
                MachineLibrary preservedLibrary = editor.getCurrentLibrary();
                JsonModelSerializer.LoadedWorkspace loaded = JsonModelSerializer.loadPwsWorkspace(file);
                PWSStateMachine model = loaded.getModel();
                if (model != null) {
                    boolean keepCurrentLibrary = false;
                    if (preservedLibrary != null && !preservedLibrary.getMachines().isEmpty()) {
                        MachineLibrary loadedLibrary = model.getAssembly().getMachineLibrary();
                        boolean loadedNotEmpty = loadedLibrary != null && !loadedLibrary.getMachines().isEmpty();
                        if (loadedNotEmpty) {
                            Object[] options = new Object[] {
                                "Keep current library",
                                "Use file library",
                                "Cancel"
                            };
                            int opt = JOptionPane.showOptionDialog(
                                editor,
                                "Current machine library is not empty.\nChoose which library to use for the opened file.",
                                "Library conflict",
                                JOptionPane.YES_NO_CANCEL_OPTION,
                                JOptionPane.QUESTION_MESSAGE,
                                null,
                                options,
                                options[1]);
                            if (opt == 2 || opt == JOptionPane.CLOSED_OPTION) {
                                return;
                            }
                            keepCurrentLibrary = (opt == 0);
                        } else {
                            keepCurrentLibrary = true;
                        }
                    }
                    if (keepCurrentLibrary) {
                        applyPreservedLibrary(model, preservedLibrary);
                    }
                    // Normalize model name to the file name (without extension) so logs
                    // and UI reflect the loaded workspace identity.
                    try {
                        String fname = file.getName();
                        if (fname != null && !fname.trim().isEmpty()) {
                            int dot = fname.lastIndexOf('.');
                            String base = (dot > 0) ? fname.substring(0, dot) : fname;
                            model.setName(base);
                        }
                    } catch (Exception ignored) {}
                    PWSDocument doc = new PWSDocument(model, model.getAssembly().getMachineLibrary());
                    doc.setFile(file);
                    doc.setDirty(false);
                    editor.setDocument(doc);
                    // Show controller editor when loading a model
                    editor.setControllerEditorVisible(true);
                    editor.rebuildUIForNewModel(model);

                    try {
                        PWSStateMachinePanel panel = (PWSStateMachinePanel) editor.getBaseEditor().getStateMachinePanel();
                        if (loaded.getAnnotations() != null) {
                            panel.importAnnotations(loaded.getAnnotations());
                        }
                        editor.syncViewMenuSelections();

                        // Ensure dashboards are visible and restored
                        try {
                            panel.setShowStateAnnotations(true);
                            panel.restoreVisibleStateAnnotations();
                            panel.repaint();
                        } catch (Exception ignore) {}

                        // Recalculate semantics after loading to ensure all computed fields are up-to-date
                        editor.scheduleSemanticsRecalculation();
                    } catch (Exception ex) {
                        // Non-fatal: show a warning but continue
                        JOptionPane.showMessageDialog(editor, "Warning: annotations could not be restored: " + ex.getMessage(), "Warning", JOptionPane.WARNING_MESSAGE);
                    }

                    // Loaded from disk, treat as not dirty
                    doc.setDirty(false);
                    editor.updateWindowTitle();
                } else {
                    JOptionPane.showMessageDialog(editor, "The selected file does not contain a valid model.");
                }
            } catch (IOException | IllegalArgumentException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(editor, "Error opening file: " + ex.getMessage());
            }
        }
    }

    private void applyPreservedLibrary(PWSStateMachine model, MachineLibrary preservedLibrary) {
        if (model == null || preservedLibrary == null) return;
        MachineLibrary target = model.getAssembly().getMachineLibrary();
        if (target == preservedLibrary) return;
        target.clear();
        for (Map.Entry<String, StateMachine> entry : preservedLibrary.getMachines().entrySet()) {
            target.addMachine(entry.getKey(), entry.getValue());
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
            PWSStateMachinePanel panel = (PWSStateMachinePanel) editor.getBaseEditor().getStateMachinePanel();
            JsonModelSerializer.savePwsWorkspace(model, panel.exportAnnotations(), file);

            doc.setFile(file);
            doc.setDirty(false);
            editor.updateWindowTitle();
            return true;
        } catch (IOException | IllegalArgumentException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(editor, "Error saving: " + ex.getMessage());
            return false;
        }
    }
}
