/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodes.input;

import com.oracle.truffle.api.ArrayUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

public abstract class InputEndsWithNode extends Node {

    public static InputEndsWithNode create() {
        return InputEndsWithNodeGen.create();
    }

    public abstract boolean execute(Object input, String suffix, String mask);

    @Specialization(guards = "mask == null")
    public boolean endsWith(String input, String suffix, @SuppressWarnings("unused") String mask) {
        return input.endsWith(suffix);
    }

    @Specialization(guards = "mask != null")
    public boolean endsWithWithMask(String input, String suffix, String mask) {
        return ArrayUtils.regionEqualsWithOrMask(input, input.length() - suffix.length(), suffix, 0, mask.length(), mask);
    }

    @Specialization(guards = "mask == null")
    public boolean endsWithTruffleObjNoMask(TruffleObject input, String suffix, String mask,
                    @Cached("create()") InputLengthNode lengthNode,
                    @Cached("create()") InputCharAtNode charAtNode) {
        return endsWithTruffleObj(input, suffix, mask, lengthNode, charAtNode);
    }

    @Specialization(guards = "mask != null")
    public boolean endsWithTruffleObjWithMask(TruffleObject input, String suffix, String mask,
                    @Cached("create()") InputLengthNode lengthNode,
                    @Cached("create()") InputCharAtNode charAtNode) {
        assert mask.length() == suffix.length();
        return endsWithTruffleObj(input, suffix, mask, lengthNode, charAtNode);
    }

    private static boolean endsWithTruffleObj(TruffleObject input, String suffix, String mask, InputLengthNode lengthNode, InputCharAtNode charAtNode) {
        final int inputLength = lengthNode.execute(input);
        if (inputLength < suffix.length()) {
            return false;
        }
        final int offset = inputLength - suffix.length();
        for (int i = 0; i < suffix.length(); i++) {
            if (InputCharAtNode.charAtWithMask(input, offset + i, mask, i, charAtNode) != suffix.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
