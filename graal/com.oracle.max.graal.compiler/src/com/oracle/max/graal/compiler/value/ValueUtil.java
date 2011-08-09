/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.max.graal.compiler.value;

import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.nodes.base.*;
import com.sun.cri.ci.*;


public class ValueUtil {

    public static ValueNode assertKind(CiKind kind, ValueNode x) {
        assert x != null && (x.kind == kind) : "kind=" + kind + ", value=" + x + ((x == null) ? "" : ", value.kind=" + x.kind);
        return x;
    }

    public static ValueNode assertLong(ValueNode x) {
        assert x != null && (x.kind == CiKind.Long);
        return x;
    }

    public static ValueNode assertJsr(ValueNode x) {
        assert x != null && (x.kind == CiKind.Jsr);
        return x;
    }

    public static ValueNode assertInt(ValueNode x) {
        assert x != null && (x.kind == CiKind.Int);
        return x;
    }

    public static ValueNode assertFloat(ValueNode x) {
        assert x != null && (x.kind == CiKind.Float);
        return x;
    }

    public static ValueNode assertObject(ValueNode x) {
        assert x != null && (x.kind == CiKind.Object);
        return x;
    }

    public static ValueNode assertWord(ValueNode x) {
        assert x != null && (x.kind == CiKind.Word);
        return x;
    }

    public static ValueNode assertDouble(ValueNode x) {
        assert x != null && (x.kind == CiKind.Double);
        return x;
    }

    public static void assertHigh(ValueNode x) {
        assert x == null;
    }

    public static boolean typeMismatch(ValueNode x, ValueNode y) {
        return y == null || !Util.archKindsEqual(x, y);
    }

    public static boolean isDoubleWord(ValueNode x) {
        return x != null && x.kind.isDoubleWord();
    }

}
