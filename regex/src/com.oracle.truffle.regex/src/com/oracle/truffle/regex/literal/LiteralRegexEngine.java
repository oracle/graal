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
package com.oracle.truffle.regex.literal;

import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.tregex.parser.RegexProperties;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.PreCalcResultVisitor;

public final class LiteralRegexEngine {

    public static LiteralRegexExecRootNode createNode(RegexLanguage language, RegexAST ast) {
        RegexProperties p = ast.getProperties();
        if (p.hasAlternations() || p.hasCharClasses() || p.hasLookAroundAssertions() || p.hasLoops()) {
            return null;
        }
        PreCalcResultVisitor preCalcResultVisitor = PreCalcResultVisitor.run(ast, true);
        final boolean caret = ast.getRoot().startsWithCaret();
        final boolean dollar = ast.getRoot().endsWithDollar();
        if ((caret || dollar) && ast.getSource().getFlags().isMultiline()) {
            return null;
        }
        return createLiteralNode(language, ast, caret, dollar, preCalcResultVisitor);
    }

    private static LiteralRegexExecRootNode createLiteralNode(RegexLanguage language, RegexAST ast, boolean caret, boolean dollar, PreCalcResultVisitor preCalcResultVisitor) {
        RegexSource source = ast.getSource();
        if (preCalcResultVisitor.getLiteral().length() == 0) {
            if (caret) {
                if (dollar) {
                    return new LiteralRegexExecRootNode.EmptyEquals(language, source, preCalcResultVisitor);
                }
                return new LiteralRegexExecRootNode.EmptyStartsWith(language, source, preCalcResultVisitor);
            }
            if (dollar) {
                return new LiteralRegexExecRootNode.EmptyEndsWith(language, source, preCalcResultVisitor);
            }
            return new LiteralRegexExecRootNode.EmptyIndexOf(language, source, preCalcResultVisitor);
        }
        if (caret) {
            if (dollar) {
                return new LiteralRegexExecRootNode.Equals(language, source, preCalcResultVisitor);
            }
            return new LiteralRegexExecRootNode.StartsWith(language, source, preCalcResultVisitor);
        }
        if (dollar) {
            return new LiteralRegexExecRootNode.EndsWith(language, source, preCalcResultVisitor);
        }
        if (source.getFlags().isSticky()) {
            return new LiteralRegexExecRootNode.RegionMatches(language, source, preCalcResultVisitor);
        }
        if (preCalcResultVisitor.getLiteral().length() == 1) {
            return new LiteralRegexExecRootNode.IndexOfChar(language, source, preCalcResultVisitor);
        }
        return null;
    }
}
