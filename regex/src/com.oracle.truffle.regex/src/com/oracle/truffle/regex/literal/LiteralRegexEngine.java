/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.literal;

import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.literal.LiteralRegexExecNode.EmptyEndsWith;
import com.oracle.truffle.regex.literal.LiteralRegexExecNode.EmptyEquals;
import com.oracle.truffle.regex.literal.LiteralRegexExecNode.EmptyIndexOf;
import com.oracle.truffle.regex.literal.LiteralRegexExecNode.EmptyStartsWith;
import com.oracle.truffle.regex.literal.LiteralRegexExecNode.EndsWith;
import com.oracle.truffle.regex.literal.LiteralRegexExecNode.Equals;
import com.oracle.truffle.regex.literal.LiteralRegexExecNode.IndexOfString;
import com.oracle.truffle.regex.literal.LiteralRegexExecNode.RegionMatches;
import com.oracle.truffle.regex.literal.LiteralRegexExecNode.StartsWith;
import com.oracle.truffle.regex.tregex.parser.RegexProperties;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.PreCalcResultVisitor;

/**
 * This regex engine is designed for very simple cases, where the regular expression can be directly
 * translated to common string operations. It will map expressions to simple index checks (
 * {@link EmptyStartsWith}, {@link EmptyEndsWith}, {@link EmptyIndexOf}) or to the following methods
 * of {@link String} (or equivalent nodes in {@link com.oracle.truffle.regex.tregex.nodes.input})
 * whenever possible:
 * <ul>
 * <li>{@link String#isEmpty()}: {@link EmptyEquals}</li>
 * <li>{@link String#indexOf(String)}: {@link IndexOfString}</li>
 * <li>{@link String#startsWith(String)}: {@link StartsWith}</li>
 * <li>{@link String#endsWith(String)}: {@link EndsWith}</li>
 * <li>{@link String#equals(Object)}: {@link Equals}</li>
 * <li>{@link String#regionMatches(int, String, int, int)}: {@link RegionMatches}</li>
 * </ul>
 */
public final class LiteralRegexEngine {

    public static LiteralRegexExecNode createNode(RegexLanguage language, RegexAST ast) {
        /*
         * Bail out if the search string would be huge. This can occur with expressions like
         * /a{1000000}/.
         */
        RegexProperties props = ast.getProperties();
        if (ast.isLiteralString() && props.isFixedCodePointWidth() && !props.hasLoneSurrogates() && (!props.hasQuantifiers() || ast.getRoot().getMinPath() <= Short.MAX_VALUE)) {
            return createLiteralNode(language, ast);
        } else {
            return null;
        }
    }

    private static LiteralRegexExecNode createLiteralNode(RegexLanguage language, RegexAST ast) {
        PreCalcResultVisitor preCalcResultVisitor = PreCalcResultVisitor.run(ast, true);
        boolean caret = ast.getRoot().startsWithCaret();
        boolean dollar = ast.getRoot().endsWithDollar();
        if (ast.getRoot().getMinPath() == 0) {
            if (caret) {
                if (dollar) {
                    return LiteralRegexExecNode.create(language, ast, new EmptyEquals(preCalcResultVisitor, ast.getOptions().isMustAdvance()));
                }
                return LiteralRegexExecNode.create(language, ast, new EmptyStartsWith(preCalcResultVisitor, ast.getOptions().isMustAdvance()));
            }
            if (dollar) {
                return LiteralRegexExecNode.create(language, ast, new EmptyEndsWith(preCalcResultVisitor, ast.getFlags().isSticky(), ast.getOptions().isMustAdvance()));
            }
            return LiteralRegexExecNode.create(language, ast, new EmptyIndexOf(preCalcResultVisitor, ast.getOptions().isMustAdvance()));
        }
        if (caret) {
            if (dollar) {
                return LiteralRegexExecNode.create(language, ast, LiteralRegexExecNodeGen.EqualsNodeGen.create(preCalcResultVisitor));
            }
            return LiteralRegexExecNode.create(language, ast, LiteralRegexExecNodeGen.StartsWithNodeGen.create(preCalcResultVisitor));
        }
        if (dollar) {
            return LiteralRegexExecNode.create(language, ast, LiteralRegexExecNodeGen.EndsWithNodeGen.create(preCalcResultVisitor, ast.getFlags().isSticky()));
        }
        if (ast.getFlags().isSticky()) {
            return LiteralRegexExecNode.create(language, ast, LiteralRegexExecNodeGen.RegionMatchesNodeGen.create(preCalcResultVisitor));
        }
        if (preCalcResultVisitor.getLiteral().encodedLength() <= 64) {
            return LiteralRegexExecNode.create(language, ast, LiteralRegexExecNodeGen.IndexOfStringNodeGen.create(preCalcResultVisitor));
        }
        return null;
    }
}
