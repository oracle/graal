/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.nfi;

final class Lexer {

    public enum Token {
        OPENPAREN("("),
        CLOSEPAREN(")"),
        OPENBRACKET("["),
        CLOSEBRACKET("]"),
        OPENBRACE("{"),
        CLOSEBRACE("}"),
        SEMICOLON(";"),
        COLON(":"),
        COMMA(","),
        OR("|"),
        ELLIPSIS("..."),
        IDENTIFIER("identifier"),
        STRING("string"),
        INVALID(null),
        EOF("EOF");

        private final String name;

        Token(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private final CharSequence source;
    private int position;

    private Token curToken;
    private int curTokenStart;
    private int curTokenEnd;

    private int mark;

    private Token nextToken;
    private int nextTokenStart;

    Lexer(CharSequence source) {
        this.source = source;
        this.position = 0;
        lex();
    }

    public Token next() {
        lex();
        return curToken;
    }

    public Token peek() {
        return nextToken;
    }

    public String currentValue() {
        if (curTokenEnd > source.length()) {
            return "<EOF>";
        } else {
            int start = curTokenStart;
            int end = curTokenEnd;
            if (curToken == Token.STRING) {
                // cut off string delimiters
                start++;
                end--;
            }
            return source.subSequence(start, end).toString();
        }
    }

    public String peekValue() {
        if (position > source.length()) {
            return "<EOF>";
        } else {
            return source.subSequence(nextTokenStart, position).toString();
        }
    }

    public void mark() {
        mark = nextTokenStart;
    }

    public String markedValue() {
        int to = Math.min(curTokenEnd, source.length());
        return source.subSequence(mark, to).toString();
    }

    private boolean atEnd() {
        return position >= source.length();
    }

    private char ch() {
        if (atEnd()) {
            return 0;
        } else {
            return source.charAt(position);
        }
    }

    private void lex() {
        curToken = nextToken;
        curTokenStart = nextTokenStart;
        curTokenEnd = position;

        while (Character.isWhitespace(ch())) {
            position++;
        }

        nextTokenStart = position;
        nextToken = getNextToken();
    }

    private static boolean isIdentStartChar(char c) {
        return Character.isAlphabetic(c) || c == '/' || c == '_';
    }

    private static boolean isIdentChar(char c) {
        // @formatter:off
        return Character.isAlphabetic(c)
                || Character.isDigit(c)
                || c == '/' || c == '.' // for filenames
                || c == '_';
        // @formatter:on
    }

    private Token getNextToken() {
        if (isIdentStartChar(ch())) {
            do {
                position++;
            } while (isIdentChar(ch()));
            return Token.IDENTIFIER;
        } else {
            char c = ch();
            position++;
            switch (c) {
                case 0:
                    return Token.EOF;
                case '(':
                    return Token.OPENPAREN;
                case ')':
                    return Token.CLOSEPAREN;
                case '[':
                    return Token.OPENBRACKET;
                case ']':
                    return Token.CLOSEBRACKET;
                case '{':
                    return Token.OPENBRACE;
                case '}':
                    return Token.CLOSEBRACE;
                case ':':
                    return Token.COLON;
                case ';':
                    return Token.SEMICOLON;
                case ',':
                    return Token.COMMA;
                case '|':
                    return Token.OR;
                case '.':
                    if (ch() == '.') {
                        position++;
                        if (ch() == '.') {
                            position++;
                            return Token.ELLIPSIS;
                        }
                    }
                    return Token.INVALID;
                case '"':
                case '\'':
                    while (!atEnd()) {
                        if (ch() == c) {
                            position++;
                            return Token.STRING;
                        }
                        position++;
                    }
                    return Token.INVALID;
                default:
                    return Token.INVALID;
            }
        }
    }
}
