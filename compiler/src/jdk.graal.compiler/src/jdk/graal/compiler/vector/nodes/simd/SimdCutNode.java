/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.simd;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.vector.lir.VectorLIRLowerable;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.Value;

/**
 * Node that produces a shorter SIMD value from a longer one.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public final class SimdCutNode extends UnaryNode implements VectorLIRLowerable {

    public static final NodeClass<SimdCutNode> TYPE = NodeClass.create(SimdCutNode.class);
    protected int offset;
    protected int length;

    public SimdCutNode(ValueNode value, int length) {
        this(value, 0, length);
    }

    public SimdCutNode(ValueNode value, int offset, int length) {
        super(TYPE, getStamp((SimdStamp) value.stamp(NodeView.DEFAULT), offset, length), value);
        this.offset = offset;
        this.length = length;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    private static Stamp getStamp(SimdStamp input, int offset, int length) {
        assert length <= input.getVectorLength() : length + "! <=" + input.getVectorLength() + " " + input;
        if (length == 1) {
            return input.getComponent(offset);
        } else {
            Stamp[] ret = new Stamp[length];
            for (int i = 0; i < length; i++) {
                ret[i] = input.getComponent(offset + i);
            }
            return SimdStamp.createWithoutConstantFolding(ret);
        }
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(getStamp((SimdStamp) getValue().stamp(NodeView.DEFAULT), offset, length));
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forValue) {
        SimdStamp inputStamp = (SimdStamp) forValue.stamp(NodeView.from(tool));
        if (offset == 0 && inputStamp.getVectorLength() == length) {
            return forValue;
        }
        // Always look through permutes in the hope the permute becomes redundant
        if (forValue instanceof SimdPermuteNode) {
            SimdPermuteNode permute = (SimdPermuteNode) forValue;
            int elementIndex = permute.destinationMapping[offset];
            if (elementIndex > -1) {
                boolean isContiguous = true;
                for (int i = 1; i < length; ++i) {
                    if (permute.destinationMapping[offset + i] != elementIndex + i) {
                        isContiguous = false;
                        break;
                    }
                }
                if (isContiguous) {
                    return new SimdCutNode(permute.getValue(), elementIndex, length);
                }
            }
        } else if (forValue instanceof SimdConcatNode) {
            SimdConcatNode concat = (SimdConcatNode) forValue;
            int xLength = ((SimdStamp) concat.getX().stamp(NodeView.from(tool))).getVectorLength();
            if (offset < xLength && (offset + length) < xLength) {
                return new SimdCutNode(concat.getX(), offset, length);
            } else if (offset >= xLength) {
                return new SimdCutNode(concat.getY(), offset - xLength, length);
            }
        } else if (forValue instanceof ConstantNode) {
            ConstantNode constantNode = (ConstantNode) forValue;
            SimdStamp constantStamp = (SimdStamp) constantNode.stamp(NodeView.from(tool));
            List<Constant> simdConstantValues = ((SimdConstant) constantNode.asConstant()).getValues();
            if (length == 1) {
                return new ConstantNode(simdConstantValues.get(offset), ((SimdStamp) constantNode.stamp(NodeView.from(tool))).getComponent(offset));
            } else if (offset > 0) {
                // if the offset is zero the cut will become a cast in the code generator and so
                // won't generate any instructions
                Constant[] cutConstants = new Constant[length];
                Stamp[] cutStamps = new Stamp[length];
                for (int i = 0; i < length; ++i) {
                    cutConstants[i] = simdConstantValues.get(i + offset);
                    cutStamps[i] = constantStamp.getComponent(i + offset);
                }
                return new ConstantNode(new SimdConstant(cutConstants), new SimdStamp(cutStamps));
            }
        } else if (forValue instanceof SimdBlendWithConstantMaskNode blend) {
            boolean[] selector = blend.getSelector();
            boolean firstValue = selector[offset];
            boolean allEqual = true;
            for (int i = offset; i < offset + length; i++) {
                if (selector[i] != firstValue) {
                    allEqual = false;
                    break;
                }
            }
            if (allEqual) {
                /*
                 * The blend blends two values x and y with a selector like [true, true, true, true,
                 * false, false, false, false]. This cut takes all of its elements from one of the
                 * blocks that all come from the same input value, i.e., we are picking out elements
                 * 0 to 3 which all come from x, or 4 to 7 which all come from y. We can just cut on
                 * the appropriate input of the blend.
                 */
                ValueNode input = firstValue ? blend.getTrueValues() : blend.getFalseValues();
                return new SimdCutNode(input, offset, length);
            }
        } else if (forValue instanceof SimdInsertNode insert) {
            Stamp insertValStamp = insert.getY().stamp(NodeView.DEFAULT);
            int insertValLen = insertValStamp instanceof SimdStamp s ? s.getVectorLength() : 1;
            if (offset == insert.offset() && length == insertValLen) {
                return insert.getY();
            }
        }

        if (length == 1) {
            if (forValue instanceof SimdBroadcastNode) {
                return ((SimdBroadcastNode) forValue).getValue();
            }
        }

        return this;
    }

    /**
     * If we have {@code offset = 0} and {@code length = 1}, try to transform the computation rooted
     * in this node to a purely scalar computation. This currently transforms phis and add and
     * multiply nodes. Other vector inputs that feed into this computation are themselves cut to a
     * scalar.
     * <p/>
     *
     * If the transformation is successful, the transformed subgraph replaces this node and is
     * returned. Otherwise, returns {@code this}.
     *
     * @return the transformed node, if any; {@code this} otherwise
     */
    public ValueNode tryToScalarize(MetaAccessProvider metaAccess) {
        if (this.hasNoUsages()) {
            return this;
        }

        if (length == 1) {
            EconomicMap<ValuePhiNode, ValuePhiNode> scalarizedPhis = EconomicMap.create(Equivalence.DEFAULT);
            ValueNode input = getValue();
            ValueNode replacement = tryToScalarize(input, offset, stamp(NodeView.DEFAULT), input.graph(), scalarizedPhis, metaAccess);
            if (replacement != this) {
                this.replaceAtUsages(replacement);
                return replacement;
            }
        } else if (getValue().isConstant() && getValue().asConstant() instanceof SimdConstant) {
            return canonicalConstant((SimdConstant) getValue().asConstant(), metaAccess, graph());
        }

        return this;
    }

    private static ValueNode tryToScalarize(ValueNode targetValue, int offset, Stamp stamp, StructuredGraph graph, EconomicMap<ValuePhiNode, ValuePhiNode> scalarizedPhis,
                    MetaAccessProvider metaAccess) {
        GraalError.guarantee(!(stamp instanceof SimdStamp), "must be a scalar %s", stamp);
        ValueNode result;
        if (targetValue instanceof ValuePhiNode phi) {
            result = pushPastPhi(phi, offset, stamp, graph, scalarizedPhis, metaAccess);
        } else if (targetValue instanceof AddNode add) {
            result = pushPastAdd(add, offset, stamp, graph, scalarizedPhis, metaAccess);
        } else if (targetValue instanceof MulNode mul) {
            result = pushPastMul(mul, offset, stamp, graph, scalarizedPhis, metaAccess);
        } else if (targetValue instanceof SimdCutNode cut) {
            result = tryToScalarize(cut.getValue(), offset + cut.getOffset(), stamp, graph, scalarizedPhis, metaAccess);
        } else if (targetValue instanceof SimdBroadcastNode broadcast) {
            result = broadcast.getValue();
        } else if (targetValue.isConstant() && targetValue.asConstant() instanceof SimdConstant con) {
            result = ConstantNode.forConstant(stamp, con.getValue(offset), metaAccess, graph);
        } else {
            result = graph.addOrUnique(new SimdCutNode(targetValue, offset, 1));
        }
        GraalError.guarantee(result.stamp(NodeView.DEFAULT).isCompatible(stamp), "wrong stamp, %s - %s", stamp, result);
        return result;
    }

    private static ValueNode pushPastPhi(ValuePhiNode phi, int offset, Stamp stamp, StructuredGraph graph, EconomicMap<ValuePhiNode, ValuePhiNode> scalarizedPhis, MetaAccessProvider metaAccess) {
        ValuePhiNode replacementPhi;
        if (scalarizedPhis.containsKey(phi)) {
            replacementPhi = scalarizedPhis.get(phi);
        } else {
            AbstractMergeNode merge = phi.merge();
            replacementPhi = graph.addWithoutUnique(new ValuePhiNode(stamp, merge));

            // Before optimizing the phi's input values, put it in the cache to ensure termination.
            scalarizedPhis.put(phi, replacementPhi);
            for (ValueNode phiInput : phi.values()) {
                ValueNode simplifiedInput = tryToScalarize(phiInput, offset, stamp, graph, scalarizedPhis, metaAccess);
                replacementPhi.addInput(simplifiedInput);
            }
        }
        return replacementPhi;
    }

    private static ValueNode pushPastAdd(AddNode add, int offset, Stamp stamp, StructuredGraph graph, EconomicMap<ValuePhiNode, ValuePhiNode> scalarizedPhis, MetaAccessProvider metaAccess) {
        ValueNode x = tryToScalarize(add.getX(), offset, stamp, graph, scalarizedPhis, metaAccess);
        ValueNode y = tryToScalarize(add.getY(), offset, stamp, graph, scalarizedPhis, metaAccess);
        return graph.addOrUniqueWithInputs(BinaryArithmeticNode.add(x, y));
    }

    private static ValueNode pushPastMul(MulNode mul, int offset, Stamp stamp, StructuredGraph graph, EconomicMap<ValuePhiNode, ValuePhiNode> scalarizedPhis, MetaAccessProvider metaAccess) {
        ValueNode x = tryToScalarize(mul.getX(), offset, stamp, graph, scalarizedPhis, metaAccess);
        ValueNode y = tryToScalarize(mul.getY(), offset, stamp, graph, scalarizedPhis, metaAccess);
        return graph.addOrUniqueWithInputs(BinaryArithmeticNode.mul(x, y));
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, VectorLIRGeneratorTool gen) {
        if (length == 1 && getValue().stamp(NodeView.DEFAULT).asConstant() instanceof SimdConstant) {
            Constant constant = ((SimdConstant) getValue().stamp(NodeView.DEFAULT).asConstant()).getValue(offset);
            LIRGeneratorTool lirTool = builder.getLIRGeneratorTool();
            if (lirTool.canInlineConstant(constant)) {
                LIRKind kind = lirTool.getLIRKind(stamp(NodeView.DEFAULT));
                builder.setResult(this, new ConstantValue(lirTool.toRegisterKind(kind), constant));
                return;
            }
        }
        Value result = gen.emitVectorCut(offset, length, builder.operand(getValue()));
        builder.setResult(this, result);
    }

    public ConstantNode canonicalConstant(SimdConstant constant, MetaAccessProvider metaAccess, StructuredGraph graph) {
        Constant cutConstant;
        if (length == 1) {
            cutConstant = constant.getValue(offset);
        } else {
            Constant[] cutValues = new Constant[length];
            for (int i = 0; i < length; i++) {
                cutValues[i] = constant.getValue(i + offset);
            }
            cutConstant = new SimdConstant(cutValues);
        }
        return ConstantNode.forConstant(stamp, cutConstant, metaAccess, graph);
    }
}
