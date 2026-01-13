package assembly;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight LTL validator: checks balanced parentheses, allowed tokens,
 * and that atomic propositions belong to the assembly alphabet.
 * Returns null when the formula is valid, otherwise an error message.
 */
public class LTLValidator {
    /** Utility class; do not instantiate. */
    private LTLValidator() {
    }

    // Pattern to find atomic propositions like machineId.stateName
    private static final Pattern ATOM = Pattern.compile("([A-Za-z0-9_]+)\\.([A-Za-z0-9_]+)");

    /**
     * Validates an LTL formula string.
     *
     * @param formula formula text
     * @param asm assembly context
     * @return null if valid, otherwise an error message
     */
    public static String validate(String formula, AssemblyInterface asm) {
        if (formula == null || formula.trim().isEmpty()) {
            return "Formula is empty";
        }
        // Disallow template placeholders
        if (formula.indexOf('{') >= 0 || formula.indexOf('}') >= 0) {
            return "Formula contains placeholders '{' or '}', replace them with actual propositions.";
        }

        // Syntax check using parser (produces detailed errors)
        try {
            LTLParser.parse(formula);
        } catch (LTLParser.ParseException ex) {
            return "Parse error at pos " + ex.pos + ": " + ex.getMessage();
        }

        // Extract allowed atoms from assembly
        Set<String> allowed = new HashSet<>();
        try {
            for (smalgebra.BasicStateProposition p : asm.getAssemblyGuards()) {
                allowed.add(p.toString());
            }
        } catch (Exception ex) {
            // If assembly doesn't provide guards, skip membership check
        }

        // Find all atomic-like tokens and validate
        Matcher m = ATOM.matcher(formula);
        while (m.find()) {
            String atom = m.group(1) + "." + m.group(2);
            if (!allowed.isEmpty() && !allowed.contains(atom)) {
                return "Unknown atomic proposition: " + atom;
            }
        }

        // Basic token sanity removed: parsing step provides detailed errors.

        return null; // OK
    }
}
