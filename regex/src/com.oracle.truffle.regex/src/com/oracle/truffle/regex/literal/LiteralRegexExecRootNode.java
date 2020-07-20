/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.RegexExecRootNode;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.result.NoMatchResult;
import com.oracle.truffle.regex.result.PreCalculatedResultFactory;
import com.oracle.truffle.regex.result.RegexResult;
import com.oracle.truffle.regex.tregex.nodes.input.InputEndsWithNode;
import com.oracle.truffle.regex.tregex.nodes.input.InputEqualsNode;
import com.oracle.truffle.regex.tregex.nodes.input.InputIndexOfStringNode;
import com.oracle.truffle.regex.tregex.nodes.input.InputRegionMatchesNode;
import com.oracle.truffle.regex.tregex.nodes.input.InputStartsWithNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.PreCalcResultVisitor;
import com.oracle.truffle.regex.tregex.string.AbstractString;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public abstract class LiteralRegexExecRootNode extends RegexExecRootNode implements JsonConvertible {

    protected final PreCalculatedResultFactory resultFactory;

    public LiteralRegexExecRootNode(RegexLanguage language, RegexAST ast, PreCalcResultVisitor preCalcResultVisitor) {
        super(language, ast.getSource(), ast.getFlags().isUnicode());
        this.resultFactory = preCalcResultVisitor.getResultFactory();
    }

    protected String getLiteral() {
        return "";
    }

    @Override
    protected final String getEngineLabel() {
        return "literal:" + getImplName() + "(" + getLiteral() + ")";
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("method", getImplName()),
                        Json.prop("literal", DebugUtil.escapeString(getLiteral())),
                        Json.prop("factory", resultFactory));
    }

    protected abstract String getImplName();

    public static final class EmptyIndexOf extends LiteralRegexExecRootNode {

        public EmptyIndexOf(RegexLanguage language, RegexAST ast, PreCalcResultVisitor preCalcResultVisitor) {
            super(language, ast, preCalcResultVisitor);
        }

        @Override
        protected String getImplName() {
            return "emptyIndexOf";
        }

        @Override
        protected RegexResult execute(Object input, int fromIndex) {
            return resultFactory.createFromStart(fromIndex);
        }
    }

    public static final class EmptyStartsWith extends LiteralRegexExecRootNode {

        public EmptyStartsWith(RegexLanguage language, RegexAST ast, PreCalcResultVisitor preCalcResultVisitor) {
            super(language, ast, preCalcResultVisitor);
        }

        @Override
        protected String getImplName() {
            return "emptyStartsWith";
        }

        @Override
        protected RegexResult execute(Object input, int fromIndex) {
            return fromIndex == 0 ? resultFactory.createFromStart(0) : NoMatchResult.getInstance();
        }
    }

    public static final class EmptyEndsWith extends LiteralRegexExecRootNode {

        public EmptyEndsWith(RegexLanguage language, RegexAST ast, PreCalcResultVisitor preCalcResultVisitor) {
            super(language, ast, preCalcResultVisitor);
        }

        @Override
        protected String getImplName() {
            return "emptyEndsWith";
        }

        @Override
        protected RegexResult execute(Object input, int fromIndex) {
            assert fromIndex <= inputLength(input);
            return resultFactory.createFromEnd(inputLength(input));
        }
    }

    public static final class EmptyEquals extends LiteralRegexExecRootNode {

        public EmptyEquals(RegexLanguage language, RegexAST ast, PreCalcResultVisitor preCalcResultVisitor) {
            super(language, ast, preCalcResultVisitor);
        }

        @Override
        protected String getImplName() {
            return "emptyEquals";
        }

        @Override
        protected RegexResult execute(Object input, int fromIndex) {
            assert fromIndex <= inputLength(input);
            return inputLength(input) == 0 ? resultFactory.createFromStart(0) : NoMatchResult.getInstance();
        }
    }

    abstract static class NonEmptyLiteralRegexExecRootNode extends LiteralRegexExecRootNode {

        protected final AbstractString literal;
        protected final AbstractString mask;

        NonEmptyLiteralRegexExecRootNode(RegexLanguage language, RegexAST ast, PreCalcResultVisitor preCalcResultVisitor) {
            super(language, ast, preCalcResultVisitor);
            literal = preCalcResultVisitor.getLiteral();
            mask = preCalcResultVisitor.getMask();
        }

        @Override
        protected String getLiteral() {
            return literal.toString();
        }

        Object literalContent() {
            return literal.content();
        }

        Object maskContent() {
            return mask == null ? null : mask.content();
        }
    }

    public static final class IndexOfString extends NonEmptyLiteralRegexExecRootNode {

        @Child InputIndexOfStringNode indexOfStringNode = InputIndexOfStringNode.create();

        public IndexOfString(RegexLanguage language, RegexAST ast, PreCalcResultVisitor preCalcResultVisitor) {
            super(language, ast, preCalcResultVisitor);
        }

        @Override
        protected String getImplName() {
            return "indexOfString";
        }

        @Override
        protected RegexResult execute(Object input, int fromIndex) {
            int start = indexOfStringNode.execute(input, fromIndex, inputLength(input), literalContent(), maskContent());
            if (start == -1) {
                return NoMatchResult.getInstance();
            }
            return resultFactory.createFromStart(start);
        }
    }

    public static final class StartsWith extends NonEmptyLiteralRegexExecRootNode {

        @Child InputStartsWithNode startsWithNode = InputStartsWithNode.create();

        public StartsWith(RegexLanguage language, RegexAST ast, PreCalcResultVisitor preCalcResultVisitor) {
            super(language, ast, preCalcResultVisitor);
        }

        @Override
        protected String getImplName() {
            return "startsWith";
        }

        @Override
        protected RegexResult execute(Object input, int fromIndex) {
            if (fromIndex == 0 && startsWithNode.execute(input, literalContent(), maskContent())) {
                return resultFactory.createFromStart(0);
            } else {
                return NoMatchResult.getInstance();
            }
        }
    }

    public static final class EndsWith extends NonEmptyLiteralRegexExecRootNode {

        private final boolean sticky;
        @Child InputEndsWithNode endsWithNode = InputEndsWithNode.create();

        public EndsWith(RegexLanguage language, RegexAST ast, PreCalcResultVisitor preCalcResultVisitor) {
            super(language, ast, preCalcResultVisitor);
            this.sticky = ast.getFlags().isSticky();
        }

        @Override
        protected String getImplName() {
            return "endsWith";
        }

        @Override
        protected RegexResult execute(Object input, int fromIndex) {
            int matchStart = inputLength(input) - literal.encodedLength();
            if ((sticky ? fromIndex == matchStart : fromIndex <= matchStart) && endsWithNode.execute(input, literalContent(), maskContent())) {
                return resultFactory.createFromEnd(inputLength(input));
            } else {
                return NoMatchResult.getInstance();
            }
        }
    }

    public static final class Equals extends NonEmptyLiteralRegexExecRootNode {

        @Child InputEqualsNode equalsNode = InputEqualsNode.create();

        public Equals(RegexLanguage language, RegexAST ast, PreCalcResultVisitor preCalcResultVisitor) {
            super(language, ast, preCalcResultVisitor);
        }

        @Override
        protected String getImplName() {
            return "equals";
        }

        @Override
        protected RegexResult execute(Object input, int fromIndex) {
            if (fromIndex == 0 && equalsNode.execute(input, literalContent(), maskContent())) {
                return resultFactory.createFromStart(0);
            } else {
                return NoMatchResult.getInstance();
            }
        }
    }

    public static final class RegionMatches extends NonEmptyLiteralRegexExecRootNode {

        @Child InputRegionMatchesNode regionMatchesNode = InputRegionMatchesNode.create();

        public RegionMatches(RegexLanguage language, RegexAST ast, PreCalcResultVisitor preCalcResultVisitor) {
            super(language, ast, preCalcResultVisitor);
        }

        @Override
        protected String getImplName() {
            return "regionMatches";
        }

        @Override
        protected RegexResult execute(Object input, int fromIndex) {
            if (regionMatchesNode.execute(input, fromIndex, literalContent(), 0, literal.encodedLength(), maskContent())) {
                return resultFactory.createFromStart(fromIndex);
            } else {
                return NoMatchResult.getInstance();
            }
        }
    }
}
