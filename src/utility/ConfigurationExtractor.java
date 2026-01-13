package utility;

import smalgebra.AndProposition;
import smalgebra.OrProposition;
import smalgebra.SMProposition;

import java.util.ArrayList;
import java.util.List;

/**
 * La classe ConfigurationExtractor trasforma una SMProposition (formula logica)
 * in un insieme di configurazioni possibili.
 *
 * Ogni configurazione è rappresentata come una mappa ({@code Map<String, String>})
 * che associa a ciascuna macchina il nome dello stato in cui essa si trova.
 *
 * L'algoritmo prevede:
 *   1. Conversione della formula in forma DNF (disgiunzione di congiunzioni) tramite il metodo toDNF().
 *   2. Appiattimento della disgiunzione in una lista di termini.
 *   3. Per ciascun termine (cioè, ciascun prodotto) vengono estratti i literali.
 *      Se per una macchina compaiono due condizioni contrastanti, il termine viene scartato.
 *   4. L'insieme dei termini "validi" viene restituito come insieme di configurazioni.
 */
public class ConfigurationExtractor {
    private ConfigurationExtractor() {
        // Utility class.
    }


    /**
     * Appiattisce una formula OR in una lista di termini.
     * Se l'espressione è una OR, restituisce ricorsivamente tutti i suoi componenti;
     * altrimenti, restituisce una lista contenente l'espressione stessa.
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
     * Appiattisce una formula AND in una lista di literali.
     * Se l'espressione è una AND, restituisce ricorsivamente tutti i suoi componenti;
     * altrimenti, restituisce una lista contenente l'espressione stessa.
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
