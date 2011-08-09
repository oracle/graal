/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.ir;

import com.oracle.max.graal.compiler.nodes.base.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;


public abstract class IntegerArithmeticNode extends Arithmetic {

    public IntegerArithmeticNode(CiKind kind, int opcode, ValueNode x, ValueNode y, Graph graph) {
        super(kind, opcode, x, y, false, graph);
        assert kind == CiKind.Int || kind == CiKind.Long;
    }

    public static IntegerArithmeticNode add(ValueNode v1, ValueNode v2) {
        assert v1.kind == v2.kind && v1.graph() == v2.graph();
        Graph graph = v1.graph();
        //TODO (gd) handle conversions here instead of strong assert ?
        switch(v1.kind) {
            case Int:
                return new IntegerAdd(CiKind.Int, v1, v2, graph);
            case Long:
                return new IntegerAdd(CiKind.Long, v1, v2, graph);
            default:
                throw Util.shouldNotReachHere();
        }
    }

    public static IntegerArithmeticNode mul(ValueNode v1, ValueNode v2) {
        assert v1.kind == v2.kind && v1.graph() == v2.graph();
        Graph graph = v1.graph();
        //TODO (gd) handle conversions here instead of strong assert ?
        switch(v1.kind) {
            case Int:
                return new IntegerMul(CiKind.Int, v1, v2, graph);
            case Long:
                return new IntegerMul(CiKind.Long, v1, v2, graph);
            default:
                throw Util.shouldNotReachHere();
        }
    }
}
