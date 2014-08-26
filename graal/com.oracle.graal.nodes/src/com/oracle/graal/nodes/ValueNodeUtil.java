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
package com.oracle.graal.nodes;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.extended.*;

public class ValueNodeUtil {

    public static ValueNode assertKind(Kind kind, ValueNode x) {
        assert x != null && x.getKind() == kind : "kind=" + kind + ", value=" + x + ((x == null) ? "" : ", value.kind=" + x.getKind());
        return x;
    }

    public static RuntimeException shouldNotReachHere(String msg) {
        throw new InternalError("should not reach here: " + msg);
    }

    public static RuntimeException shouldNotReachHere() {
        throw new InternalError("should not reach here");
    }

    public static ValueNode assertLong(ValueNode x) {
        assert x != null && (x.getKind() == Kind.Long);
        return x;
    }

    public static ValueNode assertInt(ValueNode x) {
        assert x != null && (x.getKind() == Kind.Int);
        return x;
    }

    public static ValueNode assertFloat(ValueNode x) {
        assert x != null && (x.getKind() == Kind.Float);
        return x;
    }

    public static ValueNode assertObject(ValueNode x) {
        assert x != null && (x.getKind() == Kind.Object);
        return x;
    }

    public static ValueNode assertDouble(ValueNode x) {
        assert x != null && (x.getKind() == Kind.Double);
        return x;
    }

    public static void assertHigh(ValueNode x) {
        assert x == null;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Node> Collection<T> filter(Iterable<Node> nodes, Class<T> clazz) {
        ArrayList<T> phis = new ArrayList<>();
        for (Node node : nodes) {
            if (clazz.isInstance(node)) {
                phis.add((T) node);
            }
        }
        return phis;
    }

    /**
     * Converts a given instruction to a value string. The representation of an node as a value is
     * formed by concatenating the {@linkplain com.oracle.graal.api.meta.Kind#getTypeChar character}
     * denoting its {@linkplain ValueNode#getKind kind} and its id. For example, {@code "i13"}.
     * 
     * @param value the instruction to convert to a value string. If {@code value == null}, then "-"
     *            is returned.
     * @return the instruction representation as a string
     */
    public static String valueString(ValueNode value) {
        return (value == null) ? "-" : ("" + value.getKind().getTypeChar() + value.toString(Verbosity.Id));
    }

    public static ValueNode asNode(MemoryNode node) {
        if (node == null) {
            return null;
        } else {
            return node.asNode();
        }
    }
}
