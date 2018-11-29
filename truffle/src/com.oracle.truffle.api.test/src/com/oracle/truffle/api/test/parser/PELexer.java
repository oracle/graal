/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.parser;

import java.util.Arrays;
import java.util.Iterator;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class PELexer {

    public static final byte PRINT = 1;
    public static final byte IF = 2;
    public static final byte THEN = 3;
    public static final byte GOTO = 4;
    public static final byte INPUT = 5;
    public static final byte LET = 6;
    public static final byte GOSUB = 7;
    public static final byte RETURN = 8;
    public static final byte CLEAR = 9;
    public static final byte LIST = 10;
    public static final byte RUN = 11;
    public static final byte END = 12;
    public static final byte PLUS = 13;
    public static final byte MINUS = 14;
    public static final byte MUL = 15;
    public static final byte DIV = 16;
    public static final byte EQUALS = 17;
    public static final byte LESS_THAN = 18;
    public static final byte LARGER_THAN = 19;
    public static final byte COMMA = 20;

    public static final byte NUMBER = 21;
    public static final byte STRING = 22;
    public static final byte NAME = 23;
    public static final byte CR = 24;
    public static final byte EOF = 25;

    public static final String[] tokenNames = {"INVALID", "PRINT", "IF", "THEN", "GOTO", "INPUT", "LET", "GOSUB", "RETURN", "CLEAR", "LIST", "RUN", "END", "PLUS", "MINUS", "MUL", "DIV", "EQUALS",
                    "LESS_THAN", "LARGER_THAN", "COMMA", "NUMBER", "STRING", "NAME", "CR", "EOF"};

    private final byte[] tokens;
    private final int[] tokenStart;
    private final int[] tokenEnd;
    private final int tokenCount;

    private Object[] stack;
    private int stackPointer;
    private int currentToken;

    private final Object[] asArgumentsArray;

    private PELexer(byte[] tokens, int[] tokenStart, int[] tokenEnd, int tokenCount) {
        this.tokens = tokens;
        this.tokenStart = tokenStart;
        this.tokenEnd = tokenEnd;
        this.tokenCount = tokenCount;
        this.stack = new Object[16];
        this.asArgumentsArray = new Object[]{this};
    }

    public static PELexer create(String source) {
        int sourceLength = source.length();

        byte[] tokens = new byte[Math.max(4, sourceLength / 4)];
        int[] tokenStart = new int[tokens.length];
        int[] tokenEnd = new int[tokens.length];

        int tokenCount = 0;

        int idx = 0;
        outer: while (idx < sourceLength) {
            char ch = source.charAt(idx);
            // skip whitespaces
            while (isWhitespace(ch)) {
                idx++;
                if (idx >= sourceLength) {
                    break outer;
                }
                ch = source.charAt(idx);
            }
            // now we know we will create another token
            if (tokenCount == tokens.length - 1) {
                tokens = Arrays.copyOf(tokens, tokens.length * 2);
                tokenStart = Arrays.copyOf(tokenStart, tokens.length);
                tokenEnd = Arrays.copyOf(tokenEnd, tokens.length);
            }
            tokenStart[tokenCount] = idx++;
            switch (ch) {
                case '\r':
                    tokens[tokenCount] = CR;
                    tokenEnd[tokenCount++] = idx;
                    continue outer;
                case '\n':
                    if (idx < sourceLength && source.charAt(idx) == '\r') {
                        idx++;
                    }
                    tokens[tokenCount] = CR;
                    tokenEnd[tokenCount++] = idx;
                    continue outer;
                case '+':
                    tokens[tokenCount] = PLUS;
                    tokenEnd[tokenCount++] = idx;
                    continue outer;
                case '-':
                    tokens[tokenCount] = MINUS;
                    tokenEnd[tokenCount++] = idx;
                    continue outer;
                case '*':
                    tokens[tokenCount] = MUL;
                    tokenEnd[tokenCount++] = idx;
                    continue outer;
                case '/':
                    tokens[tokenCount] = DIV;
                    tokenEnd[tokenCount++] = idx;
                    continue outer;
                case '<':
                    tokens[tokenCount] = LESS_THAN;
                    tokenEnd[tokenCount++] = idx;
                    continue outer;
                case '>':
                    tokens[tokenCount] = LARGER_THAN;
                    tokenEnd[tokenCount++] = idx;
                    continue outer;
                case '=':
                    tokens[tokenCount] = EQUALS;
                    tokenEnd[tokenCount++] = idx;
                    continue outer;
                case ',':
                    tokens[tokenCount] = COMMA;
                    tokenEnd[tokenCount++] = idx;
                    continue outer;
                case '"':
                    // string literal
                    tokens[tokenCount] = STRING;
                    while (idx < sourceLength && source.charAt(idx) != '"') {
                        idx++;
                    }
                    if (idx >= sourceLength) {
                        error("EOF in string literal");
                    }
                    idx++;
                    tokenEnd[tokenCount++] = idx;
                    continue outer;
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    // number
                    tokens[tokenCount] = NUMBER;
                    while (idx < sourceLength && source.charAt(idx) >= '0' && source.charAt(idx) <= '9') {
                        idx++;
                    }
                    if (idx >= sourceLength) {
                        error("EOF in string literal");
                    }
                    tokenEnd[tokenCount++] = idx;
                    continue outer;
                case 'P':
                    if (idx < sourceLength && source.charAt(idx) == 'R') {
                        idx++;
                        if (idx < sourceLength && source.charAt(idx) == 'I') {
                            idx++;
                            if (idx < sourceLength && source.charAt(idx) == 'N') {
                                idx++;
                                if (idx < sourceLength && source.charAt(idx) == 'T') {
                                    idx++;
                                    tokens[tokenCount] = PRINT;
                                    tokenEnd[tokenCount++] = idx;
                                    continue outer;
                                }
                            }
                        }
                    }
                    break;
                case 'I':
                    if (idx < sourceLength) {
                        if (source.charAt(idx) == 'F') {
                            idx++;
                            if (idx >= sourceLength || !isNameChar(source.charAt(idx))) {
                                idx++;
                                tokens[tokenCount] = IF;
                                tokenEnd[tokenCount++] = idx;
                                continue outer;
                            }
                        } else if (source.charAt(idx) == 'N') {
                            idx++;
                            if (idx < sourceLength && source.charAt(idx) == 'P') {
                                idx++;
                                if (idx < sourceLength && source.charAt(idx) == 'U') {
                                    idx++;
                                    if (idx < sourceLength && source.charAt(idx) == 'T') {
                                        idx++;
                                        tokens[tokenCount] = INPUT;
                                        tokenEnd[tokenCount++] = idx;
                                        continue outer;
                                    }
                                }
                            }
                        }
                    }
                    break;
                case 'T':
                    if (idx < sourceLength && source.charAt(idx) == 'H') {
                        idx++;
                        if (idx < sourceLength && source.charAt(idx) == 'E') {
                            idx++;
                            if (idx < sourceLength && source.charAt(idx) == 'N') {
                                idx++;
                                tokens[tokenCount] = THEN;
                                tokenEnd[tokenCount++] = idx;
                                continue outer;
                            }
                        }
                    }
                    break;
                case 'G':
                    if (idx < sourceLength && source.charAt(idx) == 'O') {
                        idx++;
                        if (idx < sourceLength) {
                            if (source.charAt(idx) == 'T') {
                                idx++;
                                if (idx < sourceLength && source.charAt(idx) == 'O') {
                                    idx++;
                                    if (idx >= sourceLength || !isNameChar(source.charAt(idx))) {
                                        idx++;
                                        tokens[tokenCount] = GOTO;
                                        tokenEnd[tokenCount++] = idx;
                                        continue outer;
                                    }
                                }
                            } else if (source.charAt(idx) == 'S') {
                                idx++;
                                if (idx < sourceLength && source.charAt(idx) == 'U') {
                                    idx++;
                                    if (idx < sourceLength && source.charAt(idx) == 'B') {
                                        idx++;
                                        tokens[tokenCount] = GOSUB;
                                        tokenEnd[tokenCount++] = idx;
                                        continue outer;
                                    }
                                }
                            }
                        }
                    }
                    break;
                case 'L':
                    if (idx < sourceLength) {
                        if (source.charAt(idx) == 'E') {
                            idx++;
                            if (idx < sourceLength && source.charAt(idx) == 'T') {
                                idx++;
                                if (idx >= sourceLength || !isNameChar(source.charAt(idx))) {
                                    idx++;
                                    tokens[tokenCount] = LET;
                                    tokenEnd[tokenCount++] = idx;
                                    continue outer;
                                }
                            }
                        } else if (source.charAt(idx) == 'I') {
                            idx++;
                            if (idx < sourceLength && source.charAt(idx) == 'S') {
                                idx++;
                                if (idx < sourceLength && source.charAt(idx) == 'T') {
                                    idx++;
                                    tokens[tokenCount] = LIST;
                                    tokenEnd[tokenCount++] = idx;
                                    continue outer;
                                }
                            }
                        }
                    }
                    break;
                case 'E':
                    if (idx < sourceLength && source.charAt(idx) == 'N') {
                        idx++;
                        if (idx < sourceLength && source.charAt(idx) == 'D') {
                            idx++;
                            tokens[tokenCount] = END;
                            tokenEnd[tokenCount++] = idx;
                            continue outer;
                        }
                    }
                    break;
                case 'R':
                    if (idx < sourceLength) {
                        if (source.charAt(idx) == 'E') {
                            idx++;
                            if (idx < sourceLength && source.charAt(idx) == 'T') {
                                idx++;
                                if (idx < sourceLength && source.charAt(idx) == 'U') {
                                    idx++;
                                    if (idx < sourceLength && source.charAt(idx) == 'R') {
                                        idx++;
                                        if (idx < sourceLength && source.charAt(idx) == 'N') {
                                            idx++;
                                            if (idx >= sourceLength || !isNameChar(source.charAt(idx))) {
                                                idx++;
                                                tokens[tokenCount] = RETURN;
                                                tokenEnd[tokenCount++] = idx;
                                                continue outer;
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (source.charAt(idx) == 'U') {
                            idx++;
                            if (idx < sourceLength && source.charAt(idx) == 'N') {
                                idx++;
                                tokens[tokenCount] = RUN;
                                tokenEnd[tokenCount++] = idx;
                                continue outer;
                            }
                        }
                    }
                    break;
                case 'C':
                    if (idx < sourceLength && source.charAt(idx) == 'L') {
                        idx++;
                        if (idx < sourceLength && source.charAt(idx) == 'E') {
                            idx++;
                            if (idx < sourceLength && source.charAt(idx) == 'A') {
                                idx++;
                                if (idx < sourceLength && source.charAt(idx) == 'R') {
                                    idx++;
                                    tokens[tokenCount] = CLEAR;
                                    tokenEnd[tokenCount++] = idx;
                                    continue outer;
                                }
                            }
                        }
                    }
                    break;
                default:
                    if (!isNameChar(ch)) {
                        error("invalid char: " + ch);
                    }
                    break;
            }
            // fallback case:
            while (idx < sourceLength && isNameChar(source.charAt(idx))) {
                idx++;
            }
            tokens[tokenCount] = NAME;
            tokenEnd[tokenCount++] = idx;
        }
        return new PELexer(tokens, tokenStart, tokenEnd, tokenCount);
    }

    private static boolean isNameChar(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch == '_' || (ch >= '0' && ch <= '9');
    }

    static RuntimeException error(String message) {
        CompilerAsserts.neverPartOfCompilation();
        throw new RuntimeException(message);
    }

    private static boolean isWhitespace(char ch) {
        return ch == ' ' || ch == '\t';
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("[");
        for (int i = 0; i < tokenCount; i++) {
            if (i > 0) {
                str.append(" ");
            }
            str.append(tokenStart[i] + ":" + tokenNames[tokens[i]]);
        }
        return str.append("]").toString();
    }

    public void reset() {
        currentToken = 0;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public int currentTokenId() {
        return currentToken;
    }

    public byte nextToken(ConditionProfile profile) {
        if (profile.profile(currentToken >= tokenCount)) {
            return EOF;
        } else {
            return tokens[currentToken++];
        }
    }

    public byte peek(ConditionProfile profile) {
        if (profile.profile(currentToken >= tokenCount)) {
            return EOF;
        } else {
            return tokens[currentToken];
        }
    }

    public int getTokenStart(int tokenId) {
        return tokenStart[tokenId];
    }

    public int getTokenEnd(int tokenId) {
        return tokenEnd[tokenId];
    }

    public String position() {
        if (currentToken >= tokenCount) {
            return "EOF";
        } else {
            return tokenStart[currentToken] + ":" + tokenNames[tokens[currentToken]];
        }
    }

    public int getStackPointer() {
        return stackPointer;
    }

    public void resetStackPoiner(int pointer) {
        this.stackPointer = pointer;
    }

    public void push(Object value) {
        if (CompilerDirectives.injectBranchProbability(0.01, stackPointer >= stack.length)) {
            enlarge();
        }
        stack[stackPointer++] = value;
    }

    public final class LexerList<T> implements Iterable<T> {

        private final int from;
        private final int to;
        private final Object[] stack;

        private LexerList(int from, int stackPointer, Object[] stack) {
            super();
            this.from = from;
            this.to = stackPointer;
            this.stack = stack;
        }

        @SuppressWarnings("unchecked")
        public T get(int index) {
            assert from + index < to;
            return (T) stack[from + index];
        }

        public int size() {
            return to - from;
        }

        @Override
        public Iterator<T> iterator() {
            return new Iterator<T>() {
                int index = from;

                public boolean hasNext() {
                    return index < to;
                }

                @SuppressWarnings("unchecked")
                public T next() {
                    if (index >= to) {
                        CompilerDirectives.transferToInterpreter();
                        throw new RuntimeException("invalid index in lexer stack iterator");
                    }
                    return (T) stack[index++];
                }

            };
        }
    }

    public <T> LexerList<T> getStackList(int from) {
        return new LexerList<>(from, stackPointer, stack);
    }

    @TruffleBoundary
    private void enlarge() {
        stack = Arrays.copyOf(stack, stack.length * 2);
    }

    public Object[] asArgumentsArray() {
        return asArgumentsArray;
    }
}
