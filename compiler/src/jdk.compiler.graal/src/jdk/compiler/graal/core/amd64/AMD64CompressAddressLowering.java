/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.compiler.graal.core.amd64;

import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.debug.CounterKey;
import jdk.compiler.graal.debug.DebugContext;
import jdk.vm.ci.code.Register;
import jdk.compiler.graal.core.common.LIRKind;
import jdk.compiler.graal.core.common.Stride;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.CompressionNode;
import jdk.compiler.graal.nodes.NodeView;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.calc.FloatingNode;
import jdk.compiler.graal.nodes.spi.LIRLowerable;
import jdk.compiler.graal.nodes.spi.NodeLIRBuilderTool;

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_0;

public abstract class AMD64CompressAddressLowering extends AMD64AddressLowering {
    private static final CounterKey counterFoldedUncompressDuringAddressLowering = DebugContext.counter("FoldedUncompressDuringAddressLowering");

    @Override
    protected final boolean improve(StructuredGraph graph, DebugContext debug, AMD64AddressNode addr, boolean isBaseNegated, boolean isIndexNegated) {
        if (super.improve(graph, debug, addr, isBaseNegated, isIndexNegated)) {
            return true;
        }

        if (!isBaseNegated && !isIndexNegated && addr.getScale() == Stride.S1) {
            ValueNode base = addr.getBase();
            ValueNode index = addr.getIndex();

            if (tryToImproveUncompression(addr, index, base) || tryToImproveUncompression(addr, base, index)) {
                counterFoldedUncompressDuringAddressLowering.increment(debug);
                return true;
            }
        }

        return false;
    }

    private boolean tryToImproveUncompression(AMD64AddressNode addr, ValueNode value, ValueNode other) {
        if (value instanceof CompressionNode) {
            CompressionNode compression = (CompressionNode) value;
            if (compression.getOp() == CompressionNode.CompressionOp.Uncompress && improveUncompression(addr, compression, other)) {
                return true;
            }
        }

        return false;
    }

    protected abstract boolean improveUncompression(AMD64AddressNode addr, CompressionNode compression, ValueNode other);

    @NodeInfo(cycles = CYCLES_0, size = SIZE_0)
    public static class HeapBaseNode extends FloatingNode implements LIRLowerable {

        public static final NodeClass<HeapBaseNode> TYPE = NodeClass.create(HeapBaseNode.class);

        private final Register heapBaseRegister;

        public HeapBaseNode(Register heapBaseRegister) {
            super(TYPE, StampFactory.pointer());
            this.heapBaseRegister = heapBaseRegister;
        }

        @Override
        public void generate(NodeLIRBuilderTool generator) {
            LIRKind kind = generator.getLIRGeneratorTool().getLIRKind(stamp(NodeView.DEFAULT));
            generator.setResult(this, heapBaseRegister.asValue(kind));
        }
    }
}
