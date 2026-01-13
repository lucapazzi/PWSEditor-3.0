package assembly;

import java.io.Serializable;

/** Value object for an LTL formula and its classification. */
public class LTLFormula implements Serializable {
    private static final long serialVersionUID = 1L;
    private String id;
    private String formulaText;
    private String kind; // e.g., "safety" or "liveness"

    /**
     * Creates an LTL formula record.
     *
     * @param id formula identifier
     * @param formulaText formula text
     * @param kind classification kind string
     */
    public LTLFormula(String id, String formulaText, String kind) {
        this.id = id;
        this.formulaText = formulaText;
        this.kind = kind;
    }

    /**
     * Returns the formula identifier.
     *
     * @return formula identifier
     */
    public String getId() { return id; }
    /**
     * Returns the formula text.
     *
     * @return formula text
     */
    public String getFormulaText() { return formulaText; }
    /**
     * Returns the kind string.
     *
     * @return kind string
     */
    public String getKind() { return kind; }

    /**
     * Sets the formula text.
     *
     * @param formulaText new formula text
     */
    public void setFormulaText(String formulaText) { this.formulaText = formulaText; }
    /**
     * Sets the kind string.
     *
     * @param kind new kind string
     */
    public void setKind(String kind) { this.kind = kind; }

    @Override
    public String toString() {
        return id + " (" + kind + "): " + formulaText;
    }
}
