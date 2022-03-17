package com.oracle.truffle.api.operation.test.example;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.operation.test.example.TestLanguageAst.ListNode;
import com.oracle.truffle.api.operation.test.example.TestLanguageAst.NumberNode;
import com.oracle.truffle.api.operation.test.example.TestLanguageAst.SymbolNode;
import com.oracle.truffle.api.source.Source;

class TestLanguageParser {
    int pos = 0;
    final String src;

    public TestLanguageParser(Source s) {
        this.src = s.getCharacters().toString();
    }

    private char readChar() {
        char c = peekChar();
        pos++;
        return c;
    }

    private char peekChar(boolean skipWs) {
        while (skipWs && pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }

        if (pos >= src.length()) {
            return '\0';
        }

        return src.charAt(pos);
    }

    private char peekChar() {
        return peekChar(true);
    }

    public TestLanguageAst parse() {
        char c = peekChar();
        if (c == '(')
            return parseList();
        else if (Character.isDigit(c))
            return parseLong();
        else if (Character.isAlphabetic(c))
            return parseSymbol();
        else
            throw new IllegalArgumentException("Parse error at: " + (pos - 1));
    }

    private ListNode parseList() {
        List<TestLanguageAst> result = new ArrayList<>();

        int start = pos;

        char openParen = readChar();
        assert openParen == '(';

        while (peekChar() != ')') {
            if (peekChar() == '\0') {
                throw new IllegalArgumentException("Expected ')', found EOF");
            }
            result.add(parse());
        }

        readChar(); // read the ')'

        return new ListNode(start, pos - start, result.toArray(new TestLanguageAst[result.size()]));
    }

    private NumberNode parseLong() {
        long result = 0;
        int start = pos;

        while (Character.isDigit(peekChar(false))) {
            result = result * 10 + (readChar() - '0');
        }

        return new NumberNode(start, pos - start, result);
    }

    private SymbolNode parseSymbol() {
        StringBuilder sb = new StringBuilder();
        int start = pos;
        while (Character.isAlphabetic(peekChar(false))) {
            sb.append(readChar());
        }

        return new SymbolNode(start, pos - start, sb.toString());
    }
}