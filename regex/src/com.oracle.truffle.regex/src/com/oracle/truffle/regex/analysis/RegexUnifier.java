/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.analysis;

import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexOptions;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.tregex.parser.RegexLexer;
import com.oracle.truffle.regex.tregex.parser.Token;

/**
 * Generates a "unified" regular expression representation where all single characters are replaced
 * by "x" and all character classes are replaced by "[c]". The result is supposed to represent the
 * expression's general structure and complexity, and enable the user to find structurally
 * equivalent expressions. Example: /(.*yui[a-xU-Y](,|\w))/ -> /([c]*xxx[c](x|[c]))/
 */
public final class RegexUnifier {

    private final RegexSource source;
    private final RegexLexer lexer;

    private final StringBuilder dump;

    public RegexUnifier(RegexSource source) {
        this.source = source;
        this.lexer = new RegexLexer(source, RegexFlags.parseFlags(source.getFlags()), RegexOptions.DEFAULT);
        this.dump = new StringBuilder(source.getPattern().length());
    }

    public String getUnifiedPattern() throws RegexSyntaxException {
        dump.append("/");
        while (lexer.hasNext()) {
            Token token = lexer.next();
            switch (token.kind) {
                case caret:
                    dump.append("^");
                    break;
                case dollar:
                    dump.append("$");
                    break;
                case wordBoundary:
                    dump.append("\\b");
                    break;
                case nonWordBoundary:
                    dump.append("\\B");
                    break;
                case backReference:
                    dump.append("\\").append(((Token.BackReference) token).getGroupNr());
                    break;
                case quantifier:
                    final Token.Quantifier quantifier = (Token.Quantifier) token;
                    if (quantifier.getMin() == 0 && quantifier.getMax() == 1) {
                        dump.append("?");
                    } else if (quantifier.getMin() == 0 && quantifier.isInfiniteLoop()) {
                        dump.append("*");
                    } else if (quantifier.getMin() == 1 && quantifier.isInfiniteLoop()) {
                        dump.append("+");
                    } else {
                        String lowerBound = quantifier.getMin() == -1 ? "Inf" : Integer.toString(quantifier.getMin());
                        dump.append("{").append(lowerBound);
                        if (quantifier.getMax() != quantifier.getMin()) {
                            dump.append(",");
                            if (!quantifier.isInfiniteLoop()) {
                                dump.append(quantifier.getMax());
                            }
                        }
                        dump.append("}");
                    }
                    if (!quantifier.isGreedy()) {
                        dump.append("?");
                    }
                    break;
                case alternation:
                    dump.append("|");
                    break;
                case captureGroupBegin:
                    dump.append("(");
                    break;
                case nonCaptureGroupBegin:
                    dump.append("(?:");
                    break;
                case lookAheadAssertionBegin:
                    dump.append(((Token.LookAheadAssertionBegin) token).isNegated() ? "(?!" : "(?=");
                    break;
                case lookBehindAssertionBegin:
                    dump.append(((Token.LookBehindAssertionBegin) token).isNegated() ? "(?<!" : "(?<=");
                    break;
                case groupEnd:
                    dump.append(")");
                    break;
                case charClass:
                    if (((Token.CharacterClass) token).getCodePointSet().matchesSingleChar()) {
                        dump.append("x");
                    } else {
                        dump.append("[c]");
                    }
                    break;
            }
        }
        dump.append("/");
        dump.append(source.getFlags());
        return dump.toString();
    }
}
