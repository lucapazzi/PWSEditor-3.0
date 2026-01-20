package pws.editor;

import pws.PWSStateMachine;
import assembly.MachineLibrary;
import java.io.File;

/**
 * Simple document holder for a PWS workspace: model + library + backing file + dirty flag.
 */
public class PWSDocument {
    private PWSStateMachine model;
    private MachineLibrary library;
    private File file;
    private boolean dirty;

    public PWSDocument(PWSStateMachine model, MachineLibrary library) {
        this.model = model;
        this.library = library;
        this.file = null;
        this.dirty = false;
    }

    public PWSStateMachine getModel() { return model; }
    public MachineLibrary getLibrary() { return library; }
    public File getFile() { return file; }
    public void setFile(File f) { this.file = f; }

    public boolean isDirty() { return dirty; }
    public void setDirty(boolean d) { this.dirty = d; }

    public String getDisplayName() {
        if (file != null) return file.getName();
        String name = (model != null && model.getName() != null) ? model.getName() : "Untitled";
        return name + "*";
    }
}
