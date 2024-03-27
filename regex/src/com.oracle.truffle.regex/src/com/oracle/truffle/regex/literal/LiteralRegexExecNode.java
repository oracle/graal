/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.RegexExecNode;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.result.PreCalculatedResultFactory;
import com.oracle.truffle.regex.result.RegexResult;
import com.oracle.truffle.regex.tregex.nodes.input.InputOps;
import com.oracle.truffle.regex.tregex.parser.ast.InnerLiteral;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.PreCalcResultVisitor;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public abstract class LiteralRegexExecNode extends RegexExecNode implements JsonConvertible {

    @Child TruffleString.MaterializeNode materializeNode = TruffleString.MaterializeNode.create();
    @Child LiteralRegexExecImplNode implNode;

    LiteralRegexExecNode(RegexLanguage language, RegexAST ast, LiteralRegexExecImplNode implNode) {
        super(language, ast.getSource(), ast.getFlags().isEitherUnicode());
        this.implNode = insert(implNode);
    }

    @Override
    protected final String getEngineLabel() {
        return "literal:" + implNode.getImplName();
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("method", implNode.getImplName()),
                        Json.prop("literal", DebugUtil.escapeString(implNode.getLiteral())),
                        Json.prop("factory", implNode.resultFactory));
    }

    @Override
    public abstract RegexResult execute(VirtualFrame frame, TruffleString input, int fromIndex);

    @Specialization
    RegexResult doTString(TruffleString input, int fromIndex) {
        materializeNode.execute(input, getEncoding().getTStringEncoding());
        return implNode.execute(input, fromIndex, getEncoding());
    }

    static LiteralRegexExecNode create(RegexLanguage language, RegexAST ast, LiteralRegexExecImplNode implNode) {
        return LiteralRegexExecNodeGen.create(language, ast, implNode);
    }

    abstract static class LiteralRegexExecImplNode extends Node {

        private final PreCalculatedResultFactory resultFactory;

        protected LiteralRegexExecImplNode(PreCalcResultVisitor preCalcResultVisitor) {
            this.resultFactory = preCalcResultVisitor.isBooleanMatch() ? null : preCalcResultVisitor.getResultFactory();
        }

        abstract String getImplName();

        String getLiteral() {
            return "";
        }

        final int inputLength(TruffleString input) {
            return ((RegexExecNode) getParent()).inputLength(input);
        }

        final RegexResult createFromStart(int start) {
            return resultFactory == null ? RegexResult.getBooleanMatchInstance() : resultFactory.createFromStart(start);
        }

        final RegexResult createFromEnd(int end) {
            return resultFactory == null ? RegexResult.getBooleanMatchInstance() : resultFactory.createFromEnd(end);
        }

        abstract RegexResult execute(TruffleString input, int fromIndex, Encodings.Encoding encoding);
    }

    abstract static class EmptyLiteralRegexExecNode extends LiteralRegexExecImplNode {

        protected final boolean mustAdvance;

        EmptyLiteralRegexExecNode(PreCalcResultVisitor preCalcResultVisitor, boolean mustAdvance) {
            super(preCalcResultVisitor);
            this.mustAdvance = mustAdvance;
        }
    }

    public static final class EmptyIndexOf extends EmptyLiteralRegexExecNode {

        public EmptyIndexOf(PreCalcResultVisitor preCalcResultVisitor, boolean mustAdvance) {
            super(preCalcResultVisitor, mustAdvance);
        }

        @Override
        protected String getImplName() {
            return "emptyIndexOf";
        }

        @Override
        protected RegexResult execute(TruffleString input, int fromIndex, Encodings.Encoding encoding) {
            if (mustAdvance) {
                if (fromIndex < inputLength(input)) {
                    return createFromStart(fromIndex + 1);
                } else {
                    return RegexResult.getNoMatchInstance();
                }
            } else {
                return createFromStart(fromIndex);
            }
        }
    }

    public static final class EmptyStartsWith extends EmptyLiteralRegexExecNode {

        public EmptyStartsWith(PreCalcResultVisitor preCalcResultVisitor, boolean mustAdvance) {
            super(preCalcResultVisitor, mustAdvance);
        }

        @Override
        protected String getImplName() {
            return "emptyStartsWith";
        }

        @Override
        protected RegexResult execute(TruffleString input, int fromIndex, Encodings.Encoding encoding) {
            return fromIndex == 0 && !mustAdvance ? createFromStart(0) : RegexResult.getNoMatchInstance();
        }
    }

    public static final class EmptyEndsWith extends EmptyLiteralRegexExecNode {

        private final boolean sticky;

        public EmptyEndsWith(PreCalcResultVisitor preCalcResultVisitor, boolean sticky, boolean mustAdvance) {
            super(preCalcResultVisitor, mustAdvance);
            this.sticky = sticky;
        }

        @Override
        protected String getImplName() {
            return "emptyEndsWith";
        }

        @Override
        protected RegexResult execute(TruffleString input, int fromIndex, Encodings.Encoding encoding) {
            assert fromIndex <= inputLength(input);
            if ((sticky && fromIndex < inputLength(input)) || (mustAdvance && fromIndex == inputLength(input))) {
                return RegexResult.getNoMatchInstance();
            } else {
                return createFromEnd(inputLength(input));
            }
        }
    }

    public static final class EmptyEquals extends EmptyLiteralRegexExecNode {

        public EmptyEquals(PreCalcResultVisitor preCalcResultVisitor, boolean mustAdvance) {
            super(preCalcResultVisitor, mustAdvance);
        }

        @Override
        protected String getImplName() {
            return "emptyEquals";
        }

        @Override
        protected RegexResult execute(TruffleString input, int fromIndex, Encodings.Encoding encoding) {
            assert fromIndex <= inputLength(input);
            return inputLength(input) == 0 && !mustAdvance ? createFromStart(0) : RegexResult.getNoMatchInstance();
        }
    }

    abstract static class NonEmptyLiteralRegexExecNode extends LiteralRegexExecImplNode {

        protected final int literalLength;
        protected final InnerLiteral literal;

        NonEmptyLiteralRegexExecNode(PreCalcResultVisitor preCalcResultVisitor) {
            super(preCalcResultVisitor);
            literalLength = preCalcResultVisitor.getLiteral().encodedLength();
            literal = new InnerLiteral(preCalcResultVisitor.getLiteral(), preCalcResultVisitor.getMask(), 0);
        }

        @Override
        protected String getLiteral() {
            return literal.getLiteral().toString();
        }
    }

    public abstract static class IndexOfString extends NonEmptyLiteralRegexExecNode {

        @Child TruffleString.ByteIndexOfStringNode indexOfStringNode = TruffleString.ByteIndexOfStringNode.create();

        public IndexOfString(PreCalcResultVisitor preCalcResultVisitor) {
            super(preCalcResultVisitor);
        }

        @Override
        protected String getImplName() {
            return "indexOfString";
        }

        @Specialization
        protected RegexResult run(TruffleString input, int fromIndex, Encodings.Encoding encoding) {
            int start = InputOps.indexOf(input, fromIndex, inputLength(input), literal, encoding, indexOfStringNode);
            if (start < 0) {
                return RegexResult.getNoMatchInstance();
            }
            return createFromStart(start);
        }
    }

    public abstract static class LiteralRegexExecWithRegionMatchNode extends NonEmptyLiteralRegexExecNode {

        @Child TruffleString.RegionEqualByteIndexNode regionEqualsNode = TruffleString.RegionEqualByteIndexNode.create();

        public LiteralRegexExecWithRegionMatchNode(PreCalcResultVisitor preCalcResultVisitor) {
            super(preCalcResultVisitor);
        }
    }

    public abstract static class StartsWith extends LiteralRegexExecWithRegionMatchNode {

        public StartsWith(PreCalcResultVisitor preCalcResultVisitor) {
            super(preCalcResultVisitor);
        }

        @Override
        protected String getImplName() {
            return "startsWith";
        }

        @Specialization
        protected RegexResult run(TruffleString input, int fromIndex, Encodings.Encoding encoding) {
            if (fromIndex == 0 && InputOps.startsWith(input, literal, encoding, regionEqualsNode)) {
                return createFromStart(0);
            } else {
                return RegexResult.getNoMatchInstance();
            }
        }
    }

    public abstract static class EndsWith extends LiteralRegexExecWithRegionMatchNode {

        private final boolean sticky;

        public EndsWith(PreCalcResultVisitor preCalcResultVisitor, boolean sticky) {
            super(preCalcResultVisitor);
            this.sticky = sticky;
        }

        @Override
        protected String getImplName() {
            return "endsWith";
        }

        @Specialization
        protected RegexResult run(TruffleString input, int fromIndex, Encodings.Encoding encoding) {
            int matchStart = inputLength(input) - literalLength;
            if ((sticky ? fromIndex == matchStart : fromIndex <= matchStart) && InputOps.endsWith(input, literal, encoding, regionEqualsNode)) {
                return createFromEnd(inputLength(input));
            } else {
                return RegexResult.getNoMatchInstance();
            }
        }
    }

    public abstract static class Equals extends LiteralRegexExecWithRegionMatchNode {

        public Equals(PreCalcResultVisitor preCalcResultVisitor) {
            super(preCalcResultVisitor);
        }

        @Override
        protected String getImplName() {
            return "equals";
        }

        @Specialization
        protected RegexResult run(TruffleString input, int fromIndex, Encodings.Encoding encoding) {
            if (fromIndex == 0 && InputOps.equals(input, literal, encoding, regionEqualsNode)) {
                return createFromStart(0);
            } else {
                return RegexResult.getNoMatchInstance();
            }
        }
    }

    public abstract static class RegionMatches extends LiteralRegexExecWithRegionMatchNode {

        public RegionMatches(PreCalcResultVisitor preCalcResultVisitor) {
            super(preCalcResultVisitor);
        }

        @Override
        protected String getImplName() {
            return "regionMatches";
        }

        @Specialization
        protected RegexResult run(TruffleString input, int fromIndex, Encodings.Encoding encoding) {
            if (InputOps.regionEquals(input, literal, encoding, fromIndex, 0, literalLength, regionEqualsNode)) {
                return createFromStart(fromIndex);
            } else {
                return RegexResult.getNoMatchInstance();
            }
        }
    }
}
