/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.VectorPrimitiveStamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_8;

/**
 * This node is a node whose input is a vector value and whose output is a scalar element from
 *  that vector.
 */
@NodeInfo(nameTemplate = "VectorExtract@{p#index/s}", cycles = CYCLES_8, size = SIZE_8)
public final class VectorExtractNode extends FloatingNode implements LIRLowerable {
    public static final NodeClass<VectorExtractNode> TYPE = NodeClass.create(VectorExtractNode.class);

    @Input private ValueNode vectorValue;
    private final int index;

    public VectorExtractNode(Stamp stamp, ValueNode vectorValue, int index) {
        this(TYPE, stamp, vectorValue, index);
    }

    private VectorExtractNode(NodeClass<? extends VectorExtractNode> c, Stamp stamp, ValueNode vectorValue, int index) {
        super(TYPE, stamp);
        this.vectorValue = vectorValue;
        this.index = index;
    }

    public ValueNode value() {
        return vectorValue;
    }

    public int index() {
        return index;
    }

    @Override
    public boolean verify() {
        assertTrue(vectorValue.stamp instanceof VectorPrimitiveStamp, "VectorExtractNode requires a vector ValueNode input");
        return super.verify();
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().emitExtract(gen.getLIRGeneratorTool().getLIRKind(vectorValue.stamp), gen.operand(vectorValue), index));
    }
}
