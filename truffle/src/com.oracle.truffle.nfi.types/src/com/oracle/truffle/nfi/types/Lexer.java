/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.nfi.types;

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
