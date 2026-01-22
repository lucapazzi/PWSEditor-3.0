package pws.editor;

import java.util.Collections;
import java.util.List;

/** Result of running a single LTL check. */
public class LTLCheckResult {
    public enum Status {
        PASS,
        FAIL,
        SKIPPED,
        ERROR
    }

    private final String formulaId;
    private final String formulaText;
    private final Status status;
    private final String message;
    private final List<String> violatingStates;

    public LTLCheckResult(String formulaId, String formulaText, Status status, String message, List<String> violatingStates) {
        this.formulaId = formulaId;
        this.formulaText = formulaText;
        this.status = status;
        this.message = message;
        this.violatingStates = violatingStates == null ? Collections.emptyList() : violatingStates;
    }

    public String getFormulaId() {
        return formulaId;
    }

    public String getFormulaText() {
        return formulaText;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getViolatingStates() {
        return violatingStates;
    }

    @Override
    public String toString() {
        String id = (formulaId == null || formulaId.isBlank()) ? "formula" : formulaId;
        String base = "[" + status + "] " + id + ": " + formulaText;
        if (message == null || message.isBlank()) return base;
        return base + " - " + message;
    }
}
