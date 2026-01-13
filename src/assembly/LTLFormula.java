package assembly;

import java.io.Serializable;

/** Value object for an LTL formula and its classification. */
public class LTLFormula implements Serializable {
    private static final long serialVersionUID = 1L;
    private String id;
    private String formulaText;
    private String kind; // e.g., "safety" or "liveness"

    public LTLFormula(String id, String formulaText, String kind) {
        this.id = id;
        this.formulaText = formulaText;
        this.kind = kind;
    }

    public String getId() { return id; }
    public String getFormulaText() { return formulaText; }
    public String getKind() { return kind; }

    public void setFormulaText(String formulaText) { this.formulaText = formulaText; }
    public void setKind(String kind) { this.kind = kind; }

    @Override
    public String toString() {
        return id + " (" + kind + "): " + formulaText;
    }
}
