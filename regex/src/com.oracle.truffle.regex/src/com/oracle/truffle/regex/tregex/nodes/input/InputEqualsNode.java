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

public abstract class InputEqualsNode extends Node {

    public static InputEqualsNode create() {
        return InputEqualsNodeGen.create();
    }

    public abstract boolean execute(Object input, String string, String mask);

    @Specialization(guards = "mask == null")
    public boolean execEquals(String input, String string, @SuppressWarnings("unused") String mask) {
        return input.equals(string);
    }

    @Specialization(guards = "mask != null")
    public boolean execEqualsWithMask(String input, String string, String mask) {
        return input.length() == string.length() && ArrayUtils.regionEqualsWithOrMask(input, 0, string, 0, mask.length(), mask);
    }

    @Specialization(guards = "mask == null")
    public boolean equalsTruffleObjNoMask(TruffleObject input, String string, String mask,
                    @Cached("create()") InputLengthNode lengthNode,
                    @Cached("create()") InputCharAtNode charAtNode) {
        return equalsTruffleObj(input, string, mask, lengthNode, charAtNode);
    }

    @Specialization(guards = "mask != null")
    public boolean equalsTruffleObjWithMask(TruffleObject input, String string, String mask,
                    @Cached("create()") InputLengthNode lengthNode,
                    @Cached("create()") InputCharAtNode charAtNode) {
        return equalsTruffleObj(input, string, mask, lengthNode, charAtNode);
    }

    private static boolean equalsTruffleObj(TruffleObject input, String string, String mask, InputLengthNode lengthNode, InputCharAtNode charAtNode) {
        if (lengthNode.execute(input) != string.length()) {
            return false;
        }
        for (int i = 0; i < string.length(); i++) {
            if (InputCharAtNode.charAtWithMask(input, i, mask, i, charAtNode) != string.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
