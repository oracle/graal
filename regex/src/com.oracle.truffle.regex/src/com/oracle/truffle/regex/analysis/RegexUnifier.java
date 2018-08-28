/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.analysis;

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
        this.lexer = new RegexLexer(source, RegexOptions.DEFAULT);
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
