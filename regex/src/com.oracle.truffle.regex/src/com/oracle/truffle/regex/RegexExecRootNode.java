/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.regex.result.RegexResult;
import com.oracle.truffle.regex.tregex.nodes.input.InputCharAtNode;
import com.oracle.truffle.regex.tregex.nodes.input.InputLengthNode;

public abstract class RegexExecRootNode extends RegexBodyNode {

    private final boolean mustCheckUnicodeSurrogates;
    private @Child InputLengthNode lengthNode;
    private @Child InputCharAtNode charAtNode;

    public RegexExecRootNode(RegexLanguage language, RegexSource source, boolean mustCheckUnicodeSurrogates) {
        super(language, source);
        this.mustCheckUnicodeSurrogates = mustCheckUnicodeSurrogates;
    }

    @Override
    public final RegexResult execute(VirtualFrame frame) {
        Object[] args = frame.getArguments();
        assert args.length == 2;
        Object input = args[0];
        int fromIndex = (int) args[1];
        return execute(input, adjustFromIndex(fromIndex, input));
    }

    private int adjustFromIndex(int fromIndex, Object input) {
        if (mustCheckUnicodeSurrogates && fromIndex > 0 && fromIndex < inputLength(input)) {
            if (Character.isLowSurrogate(inputCharAt(input, fromIndex)) && Character.isHighSurrogate(inputCharAt(input, fromIndex - 1))) {
                return fromIndex - 1;
            }
        }
        return fromIndex;
    }

    protected int inputLength(Object input) {
        if (lengthNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            lengthNode = insert(InputLengthNode.create());
        }
        return lengthNode.execute(input);
    }

    protected char inputCharAt(Object input, int i) {
        if (charAtNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            charAtNode = insert(InputCharAtNode.create());
        }
        return charAtNode.execute(input, i);
    }

    protected abstract RegexResult execute(Object input, int fromIndex);
}
