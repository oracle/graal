/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.string.Encodings;

@GenerateWrapper
public abstract class TRegexExecutorBaseNode extends Node implements InstrumentableNode {

    public abstract Object execute(VirtualFrame frame, TRegexExecutorLocals locals, TruffleString.CodeRange codeRange);

    @Override
    public final boolean isInstrumentable() {
        return true;
    }

    @Override
    public final WrapperNode createWrapper(ProbeNode probeNode) {
        return new TRegexExecutorBaseNodeWrapper(this, probeNode);
    }

    public final TRegexExecutorNode unwrap() {
        return (TRegexExecutorNode) (this instanceof TRegexExecutorBaseNodeWrapper ? ((TRegexExecutorBaseNodeWrapper) this).getDelegateNode() : this);
    }

    public abstract TRegexExecutorNode shallowCopy();

    public abstract RegexSource getSource();

    public final Encodings.Encoding getEncoding() {
        return getSource().getEncoding();
    }

    public final boolean isUTF8() {
        return getEncoding() == Encodings.UTF_8;
    }

    public final boolean isUTF16() {
        return getEncoding() == Encodings.UTF_16;
    }

    public final boolean isUTF32() {
        return getEncoding() == Encodings.UTF_32;
    }

    public final boolean isBooleanMatch() {
        boolean booleanMatch = getSource().getOptions().isBooleanMatch();
        CompilerAsserts.partialEvaluationConstant(booleanMatch);
        return booleanMatch;
    }

    public abstract int getNumberOfStates();

    public abstract int getNumberOfTransitions();

    public abstract String getName();

    public abstract boolean isForward();

    public boolean isTrivial() {
        return getNumberOfTransitions() < TRegexOptions.TRegexMaxTransitionsInTrivialExecutor;
    }

    public abstract boolean isSimpleCG();

    /**
     * Returns {@code true} if this executor may write any new capture group boundaries.
     */
    public abstract boolean writesCaptureGroups();

    public abstract TRegexExecutorLocals createLocals(TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, int index);
}
