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
package com.oracle.max.graal.nodes;

import java.util.*;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.graph.Node.Verbosity;
import com.sun.cri.ci.*;


public class ValueUtil {

    public static ValueNode assertKind(CiKind kind, ValueNode x) {
        assert x != null && ((x.kind() == kind) || (x.kind() == CiKind.Jsr && kind == CiKind.Object)) : "kind=" + kind + ", value=" + x + ((x == null) ? "" : ", value.kind=" + x.kind());
        return x;
    }

    public static RuntimeException shouldNotReachHere(String msg) {
        throw new InternalError("should not reach here: " + msg);
    }

    public static RuntimeException shouldNotReachHere() {
        throw new InternalError("should not reach here");
    }

    public static ValueNode assertLong(ValueNode x) {
        assert x != null && (x.kind() == CiKind.Long);
        return x;
    }

    public static ValueNode assertJsr(ValueNode x) {
        assert x != null && (x.kind() == CiKind.Jsr);
        return x;
    }

    public static ValueNode assertInt(ValueNode x) {
        assert x != null && (x.kind() == CiKind.Int);
        return x;
    }

    public static ValueNode assertFloat(ValueNode x) {
        assert x != null && (x.kind() == CiKind.Float);
        return x;
    }

    public static ValueNode assertObject(ValueNode x) {
        assert x != null && (x.kind() == CiKind.Object);
        return x;
    }

    public static ValueNode assertDouble(ValueNode x) {
        assert x != null && (x.kind() == CiKind.Double);
        return x;
    }

    public static void assertHigh(ValueNode x) {
        assert x == null;
    }

    public static boolean typeMismatch(ValueNode x, ValueNode y) {
        return y == null || x == null || x.kind() != y.kind();
    }


    @SuppressWarnings("unchecked")
    public static <T extends Node> Collection<T> filter(Iterable<Node> nodes, Class<T> clazz) {
        ArrayList<T> phis = new ArrayList<T>();
        for (Node node : nodes) {
            if (clazz.isInstance(node)) {
                phis.add((T) node);
            }
        }
        return phis;
    }

    /**
     * Converts a given instruction to a value string. The representation of an node as
     * a value is formed by concatenating the {@linkplain com.sun.cri.ci.CiKind#typeChar character} denoting its
     * {@linkplain ValueNode#kind kind} and its {@linkplain Node#id()}. For example, {@code "i13"}.
     *
     * @param value the instruction to convert to a value string. If {@code value == null}, then "-" is returned.
     * @return the instruction representation as a string
     */
    public static String valueString(ValueNode value) {
        return (value == null) ? "-" : ("" + value.kind().typeChar + value.toString(Verbosity.Id));
    }
}
