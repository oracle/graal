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
package com.oracle.truffle.regex;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.result.RegexResult;
import com.oracle.truffle.regex.runtime.nodes.ExpectStringNode;
import com.oracle.truffle.regex.tregex.nodes.input.InputOps;
import com.oracle.truffle.regex.tregex.nodes.input.InputReadNode;
import com.oracle.truffle.regex.tregex.string.Encodings;

public abstract class RegexExecNode extends RegexBodyNode {

    private final boolean mustCheckUTF16Surrogates;
    private @Child ExpectStringNode expectStringNode = ExpectStringNode.create();
    private @Child InputReadNode charAtNode;

    public RegexExecNode(RegexLanguage language, RegexSource source, boolean mustCheckUTF16Surrogates) {
        super(language, source);
        this.mustCheckUTF16Surrogates = getEncoding() == Encodings.UTF_16 && mustCheckUTF16Surrogates;
    }

    @Override
    public final RegexResult execute(VirtualFrame frame) {
        Object[] args = frame.getArguments();
        assert args.length == 2;
        TruffleString.Encoding encoding = getEncoding().getTStringEncoding();
        CompilerAsserts.partialEvaluationConstant(encoding);
        return adjustIndexAndRun(frame, expectStringNode.execute(args[0], encoding), (int) args[1]);
    }

    private int adjustFromIndex(int fromIndex, TruffleString input) {
        if (mustCheckUTF16Surrogates && fromIndex > 0 && fromIndex < inputLength(input)) {
            assert getEncoding() == Encodings.UTF_16;
            if (Character.isLowSurrogate((char) inputRead(input, fromIndex)) && Character.isHighSurrogate((char) inputRead(input, fromIndex - 1))) {
                return fromIndex - 1;
            }
        }
        return fromIndex;
    }

    public final int inputLength(TruffleString input) {
        return InputOps.length(input, getEncoding());
    }

    public final int inputRead(TruffleString input, int i) {
        if (charAtNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            charAtNode = insert(InputReadNode.create());
        }
        return charAtNode.execute(this, input, i, getEncoding());
    }

    private RegexResult adjustIndexAndRun(VirtualFrame frame, TruffleString input, int fromIndex) {
        if (fromIndex < 0 || fromIndex > inputLength(input)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException(String.format("got illegal fromIndex value: %d. fromIndex must be >= 0 and <= input length (%d)", fromIndex, inputLength(input)));
        }
        return execute(frame, input, adjustFromIndex(fromIndex, input));
    }

    public boolean isBacktracking() {
        return false;
    }

    public boolean isNFA() {
        return false;
    }

    protected abstract RegexResult execute(VirtualFrame frame, TruffleString input, int fromIndex);
}
