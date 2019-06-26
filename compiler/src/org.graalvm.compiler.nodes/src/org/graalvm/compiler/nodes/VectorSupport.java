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

import jdk.vm.ci.meta.PrimitiveConstant;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.VectorPrimitiveStamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class VectorSupport {
    private VectorSupport() { }

    /**
     * This node is a node whose input is a vector value and whose output is a scalar element from
     *  that vector.
     */
    @NodeInfo(nameTemplate = "VectorExtract@{p#index/s}")
    public static final class VectorExtractNode extends FloatingNode implements LIRLowerable {
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

    @NodeInfo(nameTemplate = "VectorPack")
    public static final class VectorPackNode extends FloatingNode implements Canonicalizable, LIRLowerable {

        public static final NodeClass<VectorPackNode> TYPE = NodeClass.create(VectorPackNode.class);

        @Input private NodeInputList<ValueNode> values;

        public VectorPackNode(VectorPrimitiveStamp stamp, List<ValueNode> values) {
            this(TYPE, stamp, values);
        }

        private VectorPackNode(NodeClass<? extends VectorPackNode> c, VectorPrimitiveStamp stamp, List<ValueNode> values) {
            super(c, stamp);
            this.values = new NodeInputList<>(this, values);
        }

        public NodeInputList<ValueNode> values() {
            return values;
        }

        @Override
        public boolean verify() {
            assertTrue(values.stream().noneMatch(x -> x.stamp instanceof VectorPrimitiveStamp), "VectorPackNode requires scalar inputs");
            return super.verify();
        }

        @Override
        public Node canonical(CanonicalizerTool tool) {
            // Do nothing if no inputs
            if (inputs().isEmpty()) {
                return this;
            }

            // All inputs need to be VectorExtractNode
            // All VectorExtractNode have exactly one usage to be deleted
            // All inputs need to refer to the same vector
            // All inputs need to be in increasing order, starting at 0
            // TODO: Input count needs to be the same as the type of the vector.
            //       there isn't yet a way to express this.

            // Obtain the vector value
            if (!(inputs().first() instanceof VectorExtractNode)) {
                return this;
            }
            final ValueNode vectorValue = ((VectorExtractNode) inputs().first()).value();

            // Ensure that all inputs refer to the same vector
            final boolean allEqualVector = StreamSupport.stream(inputs().spliterator(), false).
                    allMatch(x -> x instanceof VectorExtractNode && ((VectorExtractNode) x).value() == vectorValue);
            if (!allEqualVector) {
                return this;
            }

            final List<VectorExtractNode> nodes = inputs().snapshot().stream().
                    map(x -> (VectorExtractNode) x).
                    sorted(Comparator.comparingInt(VectorExtractNode::index)).
                    collect(Collectors.toList());

            // Ensure presence of zero
            if (nodes.get(0).index() != 0) {
                return this;
            }

            // Ensure there are no gaps
            int lastIndex = 0;
            for (int i = 1; i < nodes.size(); i++) {
                if (nodes.get(i).index() - lastIndex > 1) {
                    return this;
                }
                lastIndex = i;
            }

            replaceAtUsages(vectorValue);
            for (VectorExtractNode node : nodes) {
                if (node.getUsageCount() == 1 && node.getUsageAt(0) == this) {
                    node.removeUsage(this);
                    node.safeDelete();
                }
            }

            return null; // to delete the current node
        }

        @Override
        public void generate(NodeLIRBuilderTool gen) {
            // if all the values are constants
            final boolean allPrimitiveConstant =
                    values.stream().allMatch(x -> x.getStackKind().isPrimitive() && x.isConstant());

            if (!allPrimitiveConstant) {
                generateForNotPrimitive(gen);
            } else {
                generateForPrimitive(gen);
            }
        }

        private void generateForPrimitive(NodeLIRBuilderTool gen) {
            final LIRKind kind = gen.getLIRGeneratorTool().getLIRKind(stamp);

            final ByteBuffer byteBuffer = ByteBuffer.allocate(kind.getPlatformKind().getSizeInBytes());
            // TODO: don't hardcode for Intel
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

            for (ValueNode node : values) {
                final PrimitiveConstant value = (PrimitiveConstant) ((ConstantNode) node).getValue();
                value.serialize(byteBuffer);
            }

            // TODO: don't hardcode for vector size
            gen.setResult(this, gen.getLIRGeneratorTool().emitPackConst(kind, byteBuffer));
        }

        private void generateForNotPrimitive(NodeLIRBuilderTool gen) {
            final LIRKind kind = gen.getLIRGeneratorTool().getLIRKind(stamp);
            gen.setResult(this, gen.getLIRGeneratorTool().emitPack(kind, values.stream().map(gen::operand).collect(Collectors.toList())));
        }
    }
}
