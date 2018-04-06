/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.regex.result.RegexResult;
import com.oracle.truffle.regex.tregex.nodes.input.InputCharAtNode;
import com.oracle.truffle.regex.tregex.nodes.input.InputLengthNode;
import com.oracle.truffle.regex.util.NumberConversion;

public abstract class RegexExecRootNode extends RegexBodyNode {

    @Child private InputLengthNode inputLengthNode = InputLengthNode.create();
    @Child private InputCharAtNode inputCharAtNode = InputCharAtNode.create();

    public RegexExecRootNode(RegexLanguage language, RegexSource source) {
        super(language, source);
    }

    @Override
    public final RegexResult execute(VirtualFrame frame) {
        Object[] args = frame.getArguments();
        assert args.length == 3;

        RegexObject regex = (RegexObject) args[0];
        Object input = args[1];
        int fromIndex = NumberConversion.intValue((Number) args[2]);
        if (sourceIsUnicode(regex) && fromIndex > 0 && fromIndex < inputLengthNode.execute(input)) {
            if (Character.isLowSurrogate(inputCharAtNode.execute(input, fromIndex)) &&
                            Character.isHighSurrogate(inputCharAtNode.execute(input, fromIndex - 1))) {
                fromIndex = fromIndex - 1;
            }
        }

        return execute(frame, regex, input, fromIndex);
    }

    protected abstract RegexResult execute(VirtualFrame frame, RegexObject regex, Object input, int fromIndex);

    @SuppressWarnings("unused")
    protected boolean sourceIsUnicode(RegexObject regex) {
        return getSource().getFlags().isUnicode();
    }
}
