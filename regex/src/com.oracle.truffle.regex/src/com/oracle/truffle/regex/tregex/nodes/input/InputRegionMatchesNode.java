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

public abstract class InputRegionMatchesNode extends Node {

    public static InputRegionMatchesNode create() {
        return InputRegionMatchesNodeGen.create();
    }

    public abstract boolean execute(Object input, int fromIndex1, Object match, int fromIndex2, int length, String mask);

    @Specialization(guards = "mask == null")
    public boolean regionMatches(String input, int fromIndex1, String match, int fromIndex2, int length, @SuppressWarnings("unused") String mask) {
        return input.regionMatches(fromIndex1, match, fromIndex2, length);
    }

    @Specialization(guards = "mask != null")
    public boolean regionMatchesWithMask(String input, int fromIndex1, String match, int fromIndex2, int length, String mask) {
        return ArrayUtils.regionEqualsWithOrMask(input, fromIndex1, match, fromIndex2, length, mask);
    }

    @Specialization(guards = "mask == null")
    public boolean regionMatchesTruffleObjNoMask(TruffleObject input, int fromIndex1, String match, int fromIndex2, int length, String mask,
                    @Cached("create()") InputLengthNode lengthNode,
                    @Cached("create()") InputCharAtNode charAtNode) {
        return regionMatchesTruffleObj(input, fromIndex1, match, fromIndex2, length, mask, lengthNode, charAtNode);
    }

    @Specialization(guards = "mask != null")
    public boolean regionMatchesTruffleObjWithMask(TruffleObject input, int fromIndex1, String match, int fromIndex2, int length, String mask,
                    @Cached("create()") InputLengthNode lengthNode,
                    @Cached("create()") InputCharAtNode charAtNode) {
        assert match.length() == mask.length();
        return regionMatchesTruffleObj(input, fromIndex1, match, fromIndex2, length, mask, lengthNode, charAtNode);
    }

    @Specialization(guards = "mask == null")
    public boolean regionMatchesTruffleObjTruffleObjNoMask(TruffleObject input, int fromIndex1, TruffleObject match, int fromIndex2, int length, @SuppressWarnings("unused") String mask,
                    @Cached("create()") InputLengthNode lengthNode1,
                    @Cached("create()") InputCharAtNode charAtNode1,
                    @Cached("create()") InputLengthNode lengthNode2,
                    @Cached("create()") InputCharAtNode charAtNode2) {
        if (fromIndex1 + length > lengthNode1.execute(input) || fromIndex2 + length > lengthNode2.execute(match)) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (charAtNode1.execute(input, fromIndex1 + i) != charAtNode2.execute(match, fromIndex2 + i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean regionMatchesTruffleObj(TruffleObject input, int fromIndex1, String match, int fromIndex2, int length, String mask, InputLengthNode lengthNode, InputCharAtNode charAtNode) {
        if (fromIndex1 + length > lengthNode.execute(input) || fromIndex2 + length > match.length()) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (InputCharAtNode.charAtWithMask(input, fromIndex1 + i, mask, i, charAtNode) != match.charAt(fromIndex2 + i)) {
                return false;
            }
        }
        return true;
    }
}
