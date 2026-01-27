package utility;

import smalgebra.AndProposition;
import smalgebra.OrProposition;
import smalgebra.SMProposition;

import java.util.ArrayList;
import java.util.List;

/**
 * The ConfigurationExtractor class transforms an SMProposition (logical formula)
 * into a set of possible configurations.
 *
 * Each configuration is represented as a map ({@code Map<String, String>})
 * that associates each machine with the name of the state it is in.
 *
 * The algorithm:
 *   1. Convert the formula to DNF (disjunction of conjunctions) using toDNF().
 *   2. Flatten the disjunction into a list of terms.
 *   3. For each term (i.e., each product), extract the literals.
 *      If a machine appears with conflicting conditions, the term is discarded.
 *   4. Return the set of "valid" terms as the set of configurations.
 */
public class ConfigurationExtractor {
    private ConfigurationExtractor() {
        // Utility class.
    }


    /**
     * Flattens an OR formula into a list of terms.
     * If the expression is an OR, recursively returns all its components;
     * otherwise, returns a list containing the expression itself.
     */
    private static List<SMProposition> flattenOr(SMProposition expr) {
        List<SMProposition> result = new ArrayList<>();
        if (expr instanceof OrProposition) {
            OrProposition op = (OrProposition) expr;
            result.addAll(flattenOr(op.getLeft()));
            result.addAll(flattenOr(op.getRight()));
        } else {
            result.add(expr);
        }
        return result;
    }

    /**
     * Flattens an AND formula into a list of literals.
     * If the expression is an AND, recursively returns all its components;
     * otherwise, returns a list containing the expression itself.
     */
    private static List<SMProposition> flattenAnd(SMProposition expr) {
        List<SMProposition> result = new ArrayList<>();
        if (expr instanceof AndProposition) {
            AndProposition ap = (AndProposition) expr;
            result.addAll(flattenAnd(ap.getLeft()));
            result.addAll(flattenAnd(ap.getRight()));
        } else {
            result.add(expr);
        }
        return result;
    }
}
