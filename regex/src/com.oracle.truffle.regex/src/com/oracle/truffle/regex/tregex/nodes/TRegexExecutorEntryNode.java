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
package com.oracle.truffle.regex.tregex.nodes;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexRootNode;
import com.oracle.truffle.regex.tregex.TRegexOptions;

/**
 * This class wraps {@link TRegexExecutorNode} and specializes on the type of the input strings
 * provided to {@link TRegexExecNode}.
 */
@ImportStatic(TruffleString.CodeRange.class)
public abstract class TRegexExecutorEntryNode extends Node {

    private static final class TRegexExecutorRootNode extends RootNode {

        @Child TRegexExecutorBaseNode executor;
        private final TruffleString.CodeRange codeRange;

        private TRegexExecutorRootNode(RegexLanguage language, TRegexExecutorNode executor, TruffleString.CodeRange codeRange) {
            super(language, RegexRootNode.SHARED_EMPTY_FRAMEDESCRIPTOR);
            this.executor = insert(executor);
            this.codeRange = codeRange;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            TruffleString input = (TruffleString) arguments[0];
            int fromIndex = (int) arguments[1];
            int maxIndex = (int) arguments[2];
            int regionFrom = (int) arguments[3];
            int regionTo = (int) arguments[4];
            int index = (int) arguments[5];
            return executor.execute(frame, executor.createLocals(input, fromIndex, maxIndex, regionFrom, regionTo, index), codeRange);
        }

        @TruffleBoundary
        @Override
        public String toString() {
            String src = executor.getSource().toStringEscaped();
            return "tregex " + executor.getSource().getSource().getName() + " " + executor.getName() + " " + codeRange + ": " + (src.length() > 30 ? src.substring(0, 30) + "..." : src);
        }
    }

    @ImportStatic(TruffleString.CodeRange.class)
    public abstract static class TRegexExecutorEntryInnerNode extends Node {

        private final RegexLanguage language;
        @Child TRegexExecutorBaseNode executor;

        public TRegexExecutorEntryInnerNode(RegexLanguage language, TRegexExecutorBaseNode executor) {
            this.language = language;
            this.executor = executor;
        }

        public static TRegexExecutorEntryInnerNode create(RegexLanguage language, TRegexExecutorBaseNode executor) {
            if (executor == null) {
                return null;
            }
            return TRegexExecutorEntryNodeGen.TRegexExecutorEntryInnerNodeGen.create(language, executor);
        }

        public TRegexExecutorBaseNode getExecutor() {
            return executor;
        }

        public abstract Object execute(VirtualFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, int index, TruffleString.CodeRange codeRange);

        @Specialization(guards = "codeRange == cachedCodeRange", limit = "5")
        Object doTString(VirtualFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, int index, @SuppressWarnings("unused") TruffleString.CodeRange codeRange,
                        @Cached("codeRange") TruffleString.CodeRange cachedCodeRange,
                        @Cached("createCallTarget(cachedCodeRange)") DirectCallNode callNode) {
            return runExecutor(frame, input, fromIndex, maxIndex, regionFrom, regionTo, index, callNode, cachedCodeRange);
        }

        DirectCallNode createCallTarget(TruffleString.CodeRange codeRange) {
            if (getExecutor().isTrivial()) {
                return null;
            } else {
                return DirectCallNode.create(new TRegexExecutorEntryNode.TRegexExecutorRootNode(language, executor.shallowCopy(), codeRange).getCallTarget());
            }
        }

        private Object runExecutor(VirtualFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, int index,
                        DirectCallNode callNode,
                        TruffleString.CodeRange cachedCodeRange) {
            CompilerAsserts.partialEvaluationConstant(cachedCodeRange);
            CompilerAsserts.partialEvaluationConstant(callNode);
            if (callNode == null) {
                return executor.execute(frame, executor.createLocals(input, fromIndex, maxIndex, regionFrom, regionTo, index), cachedCodeRange);
            } else {
                return callNode.call(input, fromIndex, maxIndex, regionFrom, regionTo, index);
            }
        }
    }

    private final TRegexExecutorBaseNode executor;
    @Child TRegexExecutorEntryInnerNode innerNode;

    public TRegexExecutorEntryNode(RegexLanguage language, TRegexExecutorBaseNode executor) {
        this.executor = executor;
        innerNode = insert(TRegexExecutorEntryInnerNode.create(language, executor));
    }

    public static TRegexExecutorEntryNode create(RegexLanguage language, TRegexExecutorBaseNode executor) {
        if (executor == null) {
            return null;
        }
        return TRegexExecutorEntryNodeGen.create(language, executor);
    }

    public TRegexExecutorBaseNode getExecutor() {
        return executor;
    }

    public abstract Object execute(VirtualFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, int index);

    @Specialization
    Object doTString(VirtualFrame frame, TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, int index,
                    @Cached TruffleString.MaterializeNode materializeNode,
                    @Cached TruffleString.GetCodeRangeImpreciseNode codeRangeImpreciseNode,
                    @Cached TruffleString.GetCodeRangeNode codeRangePreciseNode,
                    @Cached InlinedConditionProfile isLatin1Profile) {
        TruffleString.Encoding encoding = executor.getEncoding().getTStringEncoding();
        CompilerAsserts.partialEvaluationConstant(encoding);
        materializeNode.execute(input, encoding);
        TruffleString.CodeRange codeRangeImprecise = codeRangeImpreciseNode.execute(input, encoding);
        final TruffleString.CodeRange codeRange;
        if (isLatin1Profile.profile(this, codeRangeImprecise.isSubsetOf(TruffleString.CodeRange.LATIN_1) || input.byteLength(encoding) > TRegexOptions.CODE_RANGE_EVALUATION_THRESHOLD)) {
            codeRange = codeRangeImprecise;
        } else {
            codeRange = codeRangePreciseNode.execute(input, encoding);
        }
        return innerNode.execute(frame, input, fromIndex, maxIndex, regionFrom, regionTo, index, codeRange);
    }
}
