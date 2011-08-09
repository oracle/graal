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
package com.oracle.max.graal.compiler.ir;

import com.oracle.max.graal.compiler.nodes.base.*;
import com.oracle.max.graal.compiler.nodes.spi.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;

@NodeInfo(shortName = "&")
public final class And extends Logic implements Canonicalizable {

    public And(CiKind kind, ValueNode x, ValueNode y, Graph graph) {
        super(kind, kind == CiKind.Int ? Bytecodes.IAND : Bytecodes.LAND, x, y, graph);
    }

    @Override
    public Node canonical(NotifyReProcess reProcess) {
        if (x() == y()) {
            return x();
        }
        if (x().isConstant() && !y().isConstant()) {
            swapOperands();
        }
        if (x().isConstant()) {
            if (kind == CiKind.Int) {
                return Constant.forInt(x().asConstant().asInt() & y().asConstant().asInt(), graph());
            } else {
                assert kind == CiKind.Long;
                return Constant.forLong(x().asConstant().asLong() & y().asConstant().asLong(), graph());
            }
        } else if (y().isConstant()) {
            if (kind == CiKind.Int) {
                int c = y().asConstant().asInt();
                if (c == -1) {
                    return x();
                }
                if (c == 0) {
                    return Constant.forInt(0, graph());
                }
            } else {
                assert kind == CiKind.Long;
                long c = y().asConstant().asLong();
                if (c == -1) {
                    return x();
                }
                if (c == 0) {
                    return Constant.forLong(0, graph());
                }
            }
        }
        return this;
    }
}
