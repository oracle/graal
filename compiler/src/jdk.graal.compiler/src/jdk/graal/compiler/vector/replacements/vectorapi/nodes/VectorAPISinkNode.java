/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.replacements.vectorapi.nodes;

import static jdk.graal.compiler.replacements.nodes.MacroNode.MacroParams;

import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;

/**
 * Common superclass for Vector API nodes that are "value sinks", i.e., that consume vector values
 * but do not produce any. These nodes are the starting points for expansion to SIMD code.
 */
@NodeInfo
public abstract class VectorAPISinkNode extends VectorAPIMacroNode {

    public static final NodeClass<VectorAPISinkNode> TYPE = NodeClass.create(VectorAPISinkNode.class);

    protected VectorAPISinkNode(NodeClass<? extends VectorAPIMacroNode> type, MacroParams macroParams) {
        super(type, macroParams, null /* sink nodes cannot produce constant SIMD values */);
    }

    @Override
    public Stamp vectorStamp() {
        return StampFactory.forVoid();
    }

    public static ValueNode reinterpretAsLong(ValueNode result) {
        ValueNode out = result;
        Stamp s = result.stamp(NodeView.DEFAULT);
        if (!(s instanceof PrimitiveStamp p)) {
            throw GraalError.shouldNotReachHereUnexpectedValue(result);
        }
        if (p instanceof FloatStamp) {
            out = ReinterpretNode.create(StampFactory.forInteger(p.getBits()), out, NodeView.DEFAULT);
        }
        if (p.getBits() < Long.SIZE) {
            out = SignExtendNode.create(out, Long.SIZE, NodeView.DEFAULT);
        }
        return out;
    }
}
