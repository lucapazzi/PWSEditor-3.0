package assembly;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple recursive-descent LTL parser producing an AST and detailed parse errors.
 * Supports atoms of the form `machineId.stateName` and operators: !, X, G, F, U, R, {@literal &}, {@literal |}, {@code ->}
 */
public class LTLParser {

    /** Base AST node for LTL formulas. */
    public static abstract class Node {
        /** Creates a node. */
        protected Node() {
        }
        public abstract String toString();
    }

    /** Atomic proposition node. */
    public static class Atom extends Node {
        /** Atom name. */
        public final String name;
        /**
         * Creates an atom.
         *
         * @param name atom name
         */
        public Atom(String name) { this.name = name; }
        public String toString() { return name; }
    }

    /** Unary operator node. */
    public static class Unary extends Node {
        /** Operator symbol. */
        public final String op;
        /** Operand node. */
        public final Node child;
        /**
         * Creates a unary node.
         *
         * @param op operator symbol
         * @param child operand node
         */
        public Unary(String op, Node child) { this.op = op; this.child = child; }
        public String toString() { return op + "(" + child + ")"; }
    }

    /** Binary operator node. */
    public static class Binary extends Node {
        /** Operator symbol. */
        public final String op;
        /** Left operand. */
        public final Node left, right;
        /**
         * Creates a binary node.
         *
         * @param op operator symbol
         * @param left left operand
         * @param right right operand
         */
        public Binary(String op, Node left, Node right) { this.op = op; this.left = left; this.right = right; }
        public String toString() { return "(" + left + " " + op + " " + right + ")"; }
    }

    /** Parse error with position information. */
    public static class ParseException extends Exception {
        private static final long serialVersionUID = 1L;
        /** Error position in the input string. */
        public final int pos;
        /**
         * Creates a parse exception.
         *
         * @param msg error message
         * @param pos error position
         */
        public ParseException(String msg, int pos) { super(msg); this.pos = pos; }
    }

    private final String input;
    private int p = 0;

    /**
     * Creates a parser for the given input.
     *
     * @param input input formula text
     */
    public LTLParser(String input) {
        this.input = input == null ? "" : input.trim();
    }

    /**
     * Parses an input string into an AST.
     *
     * @param s input formula
     * @return parsed AST root
     * @throws ParseException if parsing fails
     */
    public static Node parse(String s) throws ParseException {
        LTLParser p = new LTLParser(s);
        Node n = p.parseImpl();
        p.skipWS();
        if (!p.eof()) throw new ParseException("Unexpected text after end of formula", p.p);
        return n;
    }

    // Top-level: implication is lowest precedence
    private Node parseImpl() throws ParseException {
        Node left = parseOr();
        skipWS();
        if (matchString("->")) {
            Node right = parseImpl();
            return new Binary("->", left, right);
        }
        return left;
    }

    private Node parseOr() throws ParseException {
        Node left = parseAnd();
        skipWS();
        while (matchChar('|')) {
            Node right = parseAnd();
            left = new Binary("|", left, right);
            skipWS();
        }
        return left;
    }

    private Node parseAnd() throws ParseException {
        Node left = parseUntil();
        skipWS();
        while (matchChar('&')) {
            Node right = parseUntil();
            left = new Binary("&", left, right);
            skipWS();
        }
        return left;
    }

    private Node parseUntil() throws ParseException {
        Node left = parseUnary();
        skipWS();
        while (true) {
            if (matchChar('U')) {
                Node right = parseUnary();
                left = new Binary("U", left, right);
            } else if (matchChar('R')) {
                Node right = parseUnary();
                left = new Binary("R", left, right);
            } else break;
            skipWS();
        }
        return left;
    }

    private Node parseUnary() throws ParseException {
        skipWS();
        if (matchChar('!')) {
            Node c = parseUnary();
            return new Unary("!", c);
        }
        if (matchChar('X')) {
            Node c = parseUnary();
            return new Unary("X", c);
        }
        if (matchChar('G')) {
            Node c = parseUnary();
            return new Unary("G", c);
        }
        if (matchChar('F')) {
            Node c = parseUnary();
            return new Unary("F", c);
        }
        return parsePrimary();
    }

    private Node parsePrimary() throws ParseException {
        skipWS();
        if (matchChar('(')) {
            Node n = parseImpl();
            skipWS();
            if (!matchChar(')')) throw new ParseException("Expected closing parenthesis", p) ;
            return n;
        }
        // atom: identifier.identifier
        String atom = parseAtom();
        if (atom != null) return new Atom(atom);
        throw new ParseException("Expected atom or '(', found '" + peek() + "'", p);
    }

    private String parseAtom() {
        int start = p;
        // read machine id
        String id = parseIdent();
        if (id == null) { p = start; return null; }
        if (!matchChar('.')) { p = start; return null; }
        String state = parseIdent();
        if (state == null) { p = start; return null; }
        return id + "." + state;
    }

    private String parseIdent() {
        skipWSNoAdvance();
        int start = p;
        StringBuilder sb = new StringBuilder();
        while (!eof() && (Character.isLetterOrDigit(peek()) || peek() == '_')) {
            sb.append(next());
        }
        if (sb.length() == 0) { p = start; return null; }
        return sb.toString();
    }

    private void skipWSNoAdvance() { while (!eof() && Character.isWhitespace(peek())) p++; }
    private void skipWS() { while (!eof() && Character.isWhitespace(peek())) p++; }
    private boolean eof() { return p >= input.length(); }
    private char peek() { return eof() ? '\0' : input.charAt(p); }
    private char next() { return input.charAt(p++); }
    private boolean matchChar(char c) {
        skipWS();
        if (!eof() && input.charAt(p) == c) { p++; return true; }
        return false;
    }
    private boolean matchString(String s) {
        skipWS();
        if (input.startsWith(s, p)) { p += s.length(); return true; }
        return false;
    }
}
