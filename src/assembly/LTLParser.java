package assembly;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple recursive-descent LTL parser producing an AST and detailed parse errors.
 * Supports atoms of the form `machineId.stateName` and operators: !, X, G, F, U, R, {@literal &}, {@literal |}, {@code ->}
 */
public class LTLParser {

    public static abstract class Node {
        public abstract String toString();
    }

    public static class Atom extends Node {
        public final String name;
        public Atom(String name) { this.name = name; }
        public String toString() { return name; }
    }

    public static class Unary extends Node {
        public final String op;
        public final Node child;
        public Unary(String op, Node child) { this.op = op; this.child = child; }
        public String toString() { return op + "(" + child + ")"; }
    }

    public static class Binary extends Node {
        public final String op;
        public final Node left, right;
        public Binary(String op, Node left, Node right) { this.op = op; this.left = left; this.right = right; }
        public String toString() { return "(" + left + " " + op + " " + right + ")"; }
    }

    public static class ParseException extends Exception {
        public final int pos;
        public ParseException(String msg, int pos) { super(msg); this.pos = pos; }
    }

    private final String input;
    private int p = 0;

    public LTLParser(String input) {
        this.input = input == null ? "" : input.trim();
    }

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
