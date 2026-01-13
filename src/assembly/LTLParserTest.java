package assembly;

/**
 * Minimal test runner for LTL parser/analyzer/validator.
 * Run as: java -cp out assembly.LTLParserTest
 */
public class LTLParserTest {
    public static record TestCase(String formula, String expectedKind, boolean shouldParse) {}

    public static void main(String[] args) {
        TestCase[] tests = new TestCase[] {
            new TestCase("G (!m1.s1)", "safety", true),
            new TestCase("F (m1.s1)", "liveness", true),
            new TestCase("G (m1.s1 -> F m2.s2)", "liveness", true),
            new TestCase("m1.s1 U m2.s2", "liveness", true),
            new TestCase("G (m1.s1 & m1.s2)", "safety", true),
            new TestCase("( m1.s1 -> ", "other", false), // should fail parse
            new TestCase("G (unknown.s)", "other", true) // may parse
        };

        int failures = 0;
        for (TestCase t : tests) {
            System.out.println("Test: " + t.formula);
            try {
                var node = LTLParser.parse(t.formula);
                System.out.println("  Parsed OK.");
                if (t.shouldParse == false) {
                    System.out.println("  PARSE: expected to fail but parsed successfully");
                    failures++;
                    continue;
                }
                var kind = LTLAnalyzer.classify(node).name().toLowerCase();
                System.out.println("  Classified: " + kind);
                if (!t.expectedKind.equals("other") && !t.expectedKind.equals(kind)) {
                    System.out.println("  KIND MISMATCH: expected=" + t.expectedKind + " actual=" + kind);
                    failures++;
                }
            } catch (LTLParser.ParseException ex) {
                System.out.println("  Parse failed: " + ex.getMessage());
                if (t.shouldParse) {
                    System.out.println("  PARSE: expected to parse but failed");
                    failures++;
                }
            }
        }
        if (failures > 0) {
            System.out.println("Tests finished: failures=" + failures);
            System.exit(1);
        } else {
            System.out.println("All tests passed.");
            System.exit(0);
        }
    }

    // no external dependencies required for parser tests
}
