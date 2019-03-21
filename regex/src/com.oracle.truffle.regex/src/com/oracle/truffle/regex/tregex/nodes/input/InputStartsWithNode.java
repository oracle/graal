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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

public abstract class InputStartsWithNode extends Node {

    public static InputStartsWithNode create() {
        return InputStartsWithNodeGen.create();
    }

    public abstract boolean execute(Object input, String prefix);

    @Specialization
    public boolean startsWith(String input, String prefix) {
        return input.startsWith(prefix);
    }

    @Specialization
    public boolean startsWith(TruffleObject input, String prefix,
                    @Cached("create()") InputLengthNode lengthNode,
                    @Cached("create()") InputCharAtNode charAtNode) {
        if (lengthNode.execute(input) < prefix.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (charAtNode.execute(input, i) != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
