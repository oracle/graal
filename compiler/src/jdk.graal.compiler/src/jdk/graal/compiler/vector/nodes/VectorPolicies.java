/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes;

import java.util.List;

import org.graalvm.collections.EconomicSet;

import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.consumer.FoldVectorNode;
import jdk.graal.compiler.vector.nodes.consumer.FoldVectorNode.AccumulatorNode;
import jdk.graal.compiler.vector.nodes.consumer.FoldVectorNode.BinaryMacroNode;
import jdk.graal.compiler.vector.nodes.consumer.LowerableVectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.VectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.VectorGuardNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorLoopNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorReachabilityFenceNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorSafepointNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorWriteNode;
import jdk.graal.compiler.vector.nodes.op.CompareVectorNode;
import jdk.graal.compiler.vector.nodes.op.ConcatVectorNode;
import jdk.graal.compiler.vector.nodes.op.MapVectorNode;
import jdk.graal.compiler.vector.nodes.op.VectorGatherNode;
import jdk.graal.compiler.vector.nodes.op.VectorHashStepNode;
import jdk.graal.compiler.vector.nodes.op.VectorOperation;
import jdk.graal.compiler.vector.nodes.op.VectorTransformation;
import jdk.graal.compiler.vector.nodes.producer.InvariantVectorLogicNode;
import jdk.graal.compiler.vector.nodes.producer.SequenceVectorNode;
import jdk.graal.compiler.vector.nodes.simd.LogicValueStamp;
import jdk.graal.compiler.vector.nodes.subgraph.SubGraphUtil;
import jdk.graal.compiler.vector.nodes.type.VectorStamp;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodes.ArithmeticOperation;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.ConvertNode;
import jdk.graal.compiler.nodes.calc.FloatConvertNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.MaxNode;
import jdk.graal.compiler.nodes.calc.MinMaxNode;
import jdk.graal.compiler.nodes.calc.MinNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.calc.ShiftNode;
import jdk.graal.compiler.nodes.calc.UnsignedMaxNode;
import jdk.graal.compiler.nodes.calc.UnsignedMinNode;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaConstant;

public class VectorPolicies {

    public static class Options {
        // @formatter:off
        @Option(help = "Only generate SIMD loops for vector folds expected to iterate at least this many times.")
        public static final OptionKey<Integer> VectorFoldMinIterations = new OptionKey<>(4);
        // @formatter:on
    }

    private static int getSupportedLength(VectorAccess access, Stamp elementStamp, int upperBound, VectorArchitecture arch) {
        int absElementStride = NumUtil.safeAbs(access.getElementStride());
        int ret = upperBound;
        if (absElementStride == arch.getVectorStride(elementStamp)) {
            ret = arch.getSupportedVectorMoveLength(elementStamp, upperBound);
        } else {
            ret = 1;
        }
        if (upperBound > 1) {
            ((ValueNode) access).getDebug().log(DebugContext.VERY_DETAILED_LEVEL, "vector access %s: upper bound %d, vector length %d", access, upperBound, ret);
        }
        return ret;
    }

    /**
     * Computes the supported vector length inside a subgraph for {@link MapVectorNode} or
     * {@link FoldVectorNode}. The {@code lengthCache} map stores previously computed results for
     * each node to avoid exponential traversals in large graphs with a lot of sharing.
     */
    private static int getSupportedLengthInSubgraph(List<ValueNode> inputs, ValueNode value, int upperBound, VectorArchitecture arch, NodeMap<Integer> lengthCache) {
        if (lengthCache.containsKey(value) && lengthCache.get(value) <= upperBound) {
            return lengthCache.get(value);
        }
        int ret = upperBound;
        if (value instanceof ParameterNode p) {
            if (!SubGraphUtil.isScalarInput(p)) {
                VectorNode vector = (VectorNode) inputs.get(p.index());
                ret = getSupportedLength(vector, upperBound, arch);
            }
        } else if (value instanceof AccumulatorNode) {
            // This is just a placeholder, it does not restrict the vector length.
            ret = upperBound;
        } else if (value instanceof ConvertNode) {
            if (value instanceof IntegerConvertNode<?>) {
                IntegerConvertNode<?> convert = (IntegerConvertNode<?>) value;
                int inputLength = getSupportedLengthInSubgraph(inputs, convert.getValue(), upperBound, arch, lengthCache);
                ret = arch.getSupportedVectorConvertLength(convert.asNode().stamp(NodeView.DEFAULT), convert.getValue().stamp(NodeView.DEFAULT), inputLength, convert.getArithmeticOp());
            } else if (value instanceof FloatConvertNode) {
                FloatConvertNode convert = (FloatConvertNode) value;
                int inputLength = getSupportedLengthInSubgraph(inputs, convert.getValue(), upperBound, arch, lengthCache);
                ret = arch.getSupportedVectorConvertLength(convert.asNode().stamp(NodeView.DEFAULT), convert.getValue().stamp(NodeView.DEFAULT), inputLength, convert.getFloatConvert());
            } else {
                throw GraalError.shouldNotReachHere("Unexpected node type: " + value); // ExcludeFromJacocoGeneratedReport
            }
        } else if (value instanceof ConstantNode) {
            // assume we can at least fall back to load from data patch
            ret = arch.getSupportedVectorMoveLength(value.stamp(NodeView.DEFAULT), upperBound);
        } else if (value instanceof ConditionalNode) {
            ConditionalNode conditionalNode = (ConditionalNode) value;
            for (ValueNode input : conditionalNode.inputs().filter(ValueNode.class)) {
                ret = getSupportedLengthInSubgraph(inputs, input, ret, arch, lengthCache);
            }
            ret = arch.getSupportedVectorConditionalLength(conditionalNode.stamp(NodeView.DEFAULT), ret);
            if (arch.logicVectorsAreBitmasks()) {
                /*
                 * The bitmask representing the condition may have a different size from the values.
                 * In such cases we must convert the bitmask to match the values' size.
                 */
                Stamp compareElementStamp = ((ValueNode) conditionalNode.condition().inputs().first()).stamp(NodeView.DEFAULT);
                Stamp resultStamp = conditionalNode.stamp(NodeView.DEFAULT);
                int compareElementBytes = arch.getVectorStride(compareElementStamp);
                int resultBytes = arch.getVectorStride(resultStamp);
                if (compareElementBytes != resultBytes) {
                    IntegerStamp toStamp = StampFactory.forInteger(resultBytes * Byte.SIZE);
                    IntegerStamp fromStamp = StampFactory.forInteger(compareElementBytes * Byte.SIZE);
                    ArithmeticOpTable.IntegerConvertOp<?> convertOp = compareElementBytes > resultBytes ? fromStamp.getOps().getNarrow() : fromStamp.getOps().getSignExtend();
                    ret = arch.getSupportedVectorConvertLength(toStamp, fromStamp, ret, convertOp);
                }
            }
        } else if (value instanceof LogicNode) {
            LogicNode logicNode = (LogicNode) value;
            for (ValueNode input : logicNode.inputs().filter(ValueNode.class)) {
                ret = getSupportedLengthInSubgraph(inputs, input, ret, arch, lengthCache);
            }
            ret = arch.getSupportedVectorLogicLength(logicNode, ret);
        } else if (value instanceof BinaryMacroNode macroNode) {
            for (ValueNode input : macroNode.inputs().filter(ValueNode.class)) {
                ret = getSupportedLengthInSubgraph(inputs, input, ret, arch, lengthCache);
            }
            Stamp elementStamp = macroNode.stamp(NodeView.DEFAULT);
            ret = arch.getSupportedVectorArithmeticLength(elementStamp, ret, macroNode.getInnerBinaryOp());
            if (macroNode instanceof VectorHashStepNode) {
                // a hash step expands to a combination of multiplication and some secondary
                // operation, therefore we still need to check the multiplication
                ret = getSupportedMultiplicationLength(elementStamp, ret, ArithmeticOpTable.forStamp(elementStamp), arch);
            }
            /* Expanding a macro may currently need a general permute for the init vector. */
            ret = arch.getSupportedVectorPermuteLength(elementStamp, ret);
        } else if (value instanceof ReinterpretNode) {
            ret = getSupportedLengthInSubgraph(inputs, ((ReinterpretNode) value).getValue(), ret, arch, lengthCache);
        } else {
            ArithmeticOperation op = (ArithmeticOperation) value;
            for (ValueNode input : value.inputs().filter(ValueNode.class)) {
                ret = getSupportedLengthInSubgraph(inputs, input, ret, arch, lengthCache);
            }
            Stamp elementStamp = value.stamp(NodeView.DEFAULT);
            int supportedLength;
            if (value instanceof ShiftNode<?> s && SubGraphUtil.isScalarInput(s.getY())) {
                supportedLength = arch.getSupportedVectorShiftWithScalarCount(elementStamp, ret, op.getArithmeticOp());
            } else {
                supportedLength = arch.getSupportedVectorArithmeticLength(elementStamp, ret, op.getArithmeticOp());
            }

            if (supportedLength == 1 && op instanceof MinMaxNode && elementStamp instanceof IntegerStamp) {
                /* Min/max not supported directly, but maybe as a compare-and-blend. */
                CanonicalCondition condition;
                if (op instanceof MinNode || op instanceof MaxNode) {
                    condition = CanonicalCondition.LT;
                } else if (op instanceof UnsignedMinNode || op instanceof UnsignedMaxNode) {
                    condition = CanonicalCondition.BT;
                } else {
                    throw GraalError.shouldNotReachHere("unexpected instance of MinMaxNode: " + op); // ExcludeFromJacocoGeneratedReport
                }
                ret = arch.getSupportedVectorComparisonLength(elementStamp, condition, ret);
                ret = arch.getSupportedVectorBlendLength(elementStamp, ret);
            } else {
                ret = supportedLength;
            }
        }
        if (upperBound > 1) {
            value.getDebug().log(DebugContext.VERY_DETAILED_LEVEL, "inner graph vector node %s (%s): upper bound %d, vector length %d", value, value.stamp(NodeView.DEFAULT), upperBound, ret);
        }
        lengthCache.put(value, ret);
        return ret;
    }

    private static int getSupportedLength(VectorNode vector, int upperBound, VectorArchitecture arch) {
        int ret = upperBound;
        if (vector instanceof MapVectorNode) {
            MapVectorNode map = (MapVectorNode) vector;
            NodeMap<Integer> lengthCache = map.getOp().createNodeMap();
            ret = getSupportedLengthInSubgraph(map.getVectorInputs(), SubGraphUtil.getResult(map), upperBound, arch, lengthCache);
        } else if (vector instanceof VectorTransformation) {
            if (vector instanceof CompareVectorNode) {
                CompareVectorNode compare = (CompareVectorNode) vector;
                Stamp elementStamp = ((VectorNode) compare.getX()).getVectorStamp().getElementStamp();
                ret = arch.getSupportedVectorComparisonLength(elementStamp, ((CompareVectorNode) vector).getCondition(), upperBound);
            } else if (vector instanceof VectorGatherNode) {
                VectorGatherNode gather = (VectorGatherNode) vector;
                Stamp elementStamp = gather.getVectorStamp().getElementStamp();
                Stamp offsetStamp = gather.getOffsets().getVectorStamp().getElementStamp();
                ret = arch.getSupportedVectorGatherLength(elementStamp, offsetStamp, upperBound);
            }
            for (ValueNode input : ((VectorTransformation) vector).getVectorInputs()) {
                ret = getSupportedLength((VectorNode) input, ret, arch);
            }
        } else if (vector instanceof SequenceVectorNode) {
            ret = getSupportedLength((SequenceVectorNode) vector, upperBound, arch);
        } else if (vector instanceof VectorAccess) {
            Stamp elementStamp = vector.getVectorStamp().getElementStamp();
            ret = getSupportedLength((VectorAccess) vector, elementStamp, upperBound, arch);
        } else {
            Stamp elementStamp = vector.getVectorStamp().getElementStamp();
            ret = arch.getSupportedVectorMoveLength(elementStamp, upperBound);
        }
        if (upperBound > 1) {
            vector.asNode().getDebug().log(DebugContext.VERY_DETAILED_LEVEL, "vector %s: upper bound %d, vector length %d", vector, upperBound, ret);
        }
        return ret;
    }

    private static int getSupportedLength(SequenceVectorNode vector, int upperBound, VectorArchitecture arch) {
        // SequenceVectorNode gets lowered to a combination of + and *
        Stamp elementStamp = vector.getVectorStamp().getElementStamp();
        ArithmeticOpTable table = ArithmeticOpTable.forStamp(elementStamp);
        int ret = arch.getSupportedVectorArithmeticLength(elementStamp, upperBound, table.getAdd());

        // check if we know which arithmetic operations will be used for the sequence
        JavaConstant constantStride = vector.getStride().asJavaConstant();
        if (constantStride != null) {
            ValueNode fictiveXNode = new ParameterNode(0, StampPair.createSingle(elementStamp));
            ValueNode canonical = MulNode.canonical(elementStamp, fictiveXNode, constantStride.asLong(), NodeView.DEFAULT);
            if (canonical != null) {
                EconomicSet<ArithmeticOpTable.Op> result = EconomicSet.create();
                collectArithmeticOperations(result, canonical);
                for (ArithmeticOpTable.Op op : result) {
                    if (op instanceof ArithmeticOpTable.ShiftOp.Shl) {
                        // A mul can be converted to a shift with constant shifts
                        ret = arch.getSupportedVectorShiftWithScalarCount(elementStamp, ret, op);
                    } else {
                        ret = arch.getSupportedVectorArithmeticLength(elementStamp, ret, op);
                    }
                }
                return ret;
            }
        }

        ret = getSupportedMultiplicationLength(elementStamp, ret, table, arch);
        if (upperBound > 1) {
            vector.asNode().getDebug().log(DebugContext.VERY_DETAILED_LEVEL, "sequence %s: upper bound %d, vector length %d", vector, upperBound, ret);
        }
        return ret;
    }

    private static int getSupportedMultiplicationLength(Stamp elementStamp, int upperBound, ArithmeticOpTable table, VectorArchitecture arch) {
        int ret = upperBound;
        // we don't know for sure how/if the multiplication will be optimized
        ret = arch.getSupportedVectorArithmeticLength(elementStamp, ret, table.getNeg());
        ret = arch.getSupportedVectorShiftWithScalarCount(elementStamp, ret, table.getShl());
        ret = arch.getSupportedVectorArithmeticLength(elementStamp, ret, table.getSub());
        ret = arch.getSupportedVectorArithmeticLength(elementStamp, ret, table.getMul());
        return ret;
    }

    private static void collectArithmeticOperations(EconomicSet<ArithmeticOpTable.Op> result, Node node) {
        if (node instanceof ArithmeticOperation) {
            result.add(((ArithmeticOperation) node).getArithmeticOp());
        }

        for (Node input : node.inputs()) {
            collectArithmeticOperations(result, input);
        }
    }

    /**
     * Computes the guaranteed alignment (in elements) for a {@link VectorOperation} and all its
     * inputs.
     */
    private static int getCommonAlignmentInElements(VectorOperation operation, VectorArchitecture arch) {
        int alignment = Integer.MAX_VALUE;
        for (ValueNode input : operation.getVectorInputs()) {
            assert !(input instanceof ConcatVectorNode) : "at this point in time, all ConcatVectorNodes must already have been replaced";
            if (input instanceof VectorAccess) {
                VectorAccess vectorAccess = (VectorAccess) input;
                int inputAlignment = getAlignmentInElements(vectorAccess, arch);
                assert CodeUtil.isPowerOf2(inputAlignment) : inputAlignment;
                alignment = Math.min(alignment, inputAlignment);
            } else if (input instanceof VectorOperation) {
                alignment = Math.min(alignment, getCommonAlignmentInElements((VectorOperation) input, arch));
            }
        }

        if (alignment != Integer.MAX_VALUE && operation instanceof VectorWriteNode) {
            // when we have at least one read node that forces some kind of alignment, we need to
            // consider the alignment of the write as well. otherwise, it is possible that the
            // alignment for the reads conflicts with the alignment of the write.
            VectorWriteNode vectorWriteNode = (VectorWriteNode) operation;
            int inputAlignment = getAlignmentInElements(vectorWriteNode, arch);
            assert CodeUtil.isPowerOf2(inputAlignment) : inputAlignment;
            alignment = Math.min(alignment, inputAlignment);
        }

        return alignment;
    }

    /**
     * Computes the guaranteed alignment (in elements) for a single {@link VectorAccess} operation.
     */
    public static int getAlignmentInElements(VectorAccess vectorAccess, VectorArchitecture arch) {
        int absElementStride = NumUtil.safeAbs(vectorAccess.getElementStride());
        assert CodeUtil.isPowerOf2(absElementStride) : absElementStride;
        IntegerStamp offsetStamp = getOffsetRelativeToAccessedObject(vectorAccess.getAddress(), arch);
        if (offsetStamp != null) {
            assert offsetStamp.mayBeSet() == (offsetStamp.mayBeSet() | offsetStamp.mustBeSet()) : offsetStamp;
            long mask = offsetStamp.mayBeSet();
            int inputAlignmentInBytes = 1 << Long.numberOfTrailingZeros(mask);
            int alignmentInBytes = Math.min(inputAlignmentInBytes, arch.getObjectAlignment());
            // it is possible that the stamp is too conservative -> we only use the information if
            // it gives any benefit
            if (alignmentInBytes > absElementStride) {
                return alignmentInBytes / absElementStride;
            }
        }
        return 1;
    }

    private static IntegerStamp getOffsetRelativeToAccessedObject(AddressNode a, VectorArchitecture arch) {
        // addresses can use other addresses as their base. so, we try walking the address hierarchy
        // upwards until we end up at an address base for which we know that it is aligned by
        // arch.getObjectAlignment() bytes.
        IntegerStamp result = null;
        ValueNode currentNode = a;
        while (currentNode instanceof OffsetAddressNode) {
            OffsetAddressNode offsetAddress = (OffsetAddressNode) currentNode;
            Stamp stamp = offsetAddress.getOffset().stamp(NodeView.DEFAULT);
            if (!(stamp instanceof IntegerStamp)) {
                return null;
            }

            if (result == null) {
                result = (IntegerStamp) stamp;
            } else {
                result = (IntegerStamp) IntegerStamp.OPS.getAdd().foldStamp(result, stamp);
            }

            ValueNode base = offsetAddress.getBase();
            if (isAlignedObject(base, arch)) {
                return result;
            }
            currentNode = offsetAddress.getBase();
        }
        return null;
    }

    private static boolean isAlignedObject(ValueNode base, VectorArchitecture arch) {
        switch (base.stamp(NodeView.DEFAULT)) {
            case AbstractObjectStamp ignored -> {
                return true;
            }
            case IntegerStamp stamp -> {
                return Long.numberOfTrailingZeros(stamp.mayBeSet()) >= CodeUtil.log2(arch.getObjectAlignment());
            }
            default -> {
                return false;
            }
        }
    }

    @SuppressWarnings("try")
    public static int getSupportedLengthMask(VectorConsumer consumer, int upperBound, VectorArchitecture arch, TargetDescription target) {
        if (consumer instanceof VectorLoopNode) {
            return getSupportedLengthMask((VectorLoopNode) consumer, upperBound, arch, target);
        }
        int result = 1;
        DebugContext debug = consumer.asNode().getDebug();
        try (Indent indent = debug.logAndIndent(DebugContext.DETAILED_LEVEL, "compute length mask for %s", consumer)) {
            if (upperBound > 0) {
                int supportedLength = upperBound;
                do {
                    supportedLength = getMaxSupportedLength(consumer, arch, target, supportedLength);
                    assert CodeUtil.isPowerOf2(supportedLength) : supportedLength;
                    result = result | supportedLength;
                    supportedLength--;
                } while (supportedLength > 1);
            }
        }
        debug.log(DebugContext.DETAILED_LEVEL, "consumer %s length mask 0x%x", consumer, result);
        return result;
    }

    @SuppressWarnings("try")
    public static int getSupportedLengthMask(VectorLoopNode group, int upperBound, VectorArchitecture arch, TargetDescription target) {
        DebugContext debug = group.asNode().getDebug();
        assert !group.getConsumers().isEmpty() : group + " " + group.getConsumers();
        int mask = Integer.MAX_VALUE;
        try (Indent indent = debug.logAndIndent(DebugContext.DETAILED_LEVEL, "compute length mask for %s", group)) {
            for (ValueNode consumer : group.getConsumers()) {
                mask &= getSupportedLengthMask((VectorConsumer) consumer, upperBound, arch, target);
            }
        }
        debug.log(DebugContext.DETAILED_LEVEL, "consumer group %s length mask 0x%x", group, mask);
        return mask;
    }

    private static int getMaxSupportedLength(VectorConsumer consumer, VectorArchitecture arch, TargetDescription target, int supportedLength) {
        /*
         * the result of the first call might be incorrect because we look at each node
         * individually: nodeA supports length = 8 but does not support length = 4. nodeB supports
         * length = 4 but does not support length = 8.
         *
         * if we visit nodeA before nodeB, the result will be 4, which is incorrect. we work around
         * that, by calling the method multiple times until the result does not change anymore. if
         * this has a significant negative effect on compilation time, this will be fixed in
         * GR-5146,
         */
        int oldSupportedLength;
        int newSupportedLength = supportedLength;
        do {
            oldSupportedLength = newSupportedLength;
            newSupportedLength = getMaxSupportedLength(consumer, oldSupportedLength, arch, target);
            if (oldSupportedLength != newSupportedLength && newSupportedLength > 1) {
                consumer.asNode().getDebug().log(DebugContext.VERY_DETAILED_LEVEL, "%s: old length: %d, new length: %d, iterate until fixed point", consumer, oldSupportedLength, newSupportedLength);
            }
        } while (oldSupportedLength != newSupportedLength && newSupportedLength > 1);

        return newSupportedLength;
    }

    @SuppressWarnings("try")
    private static int getMaxSupportedLength(VectorConsumer consumer, int upperBound, VectorArchitecture arch, TargetDescription target) {
        DebugContext debug = consumer.asNode().getDebug();
        int ret = Math.min(upperBound, consumer.getMaxVectorLength(arch));
        try (Indent indent = debug.logAndIndent(DebugContext.VERY_DETAILED_LEVEL, "compute max supported length for %s, upper bound %d, max length %d", consumer, upperBound,
                        consumer.getMaxVectorLength(arch))) {
            if (consumer instanceof VectorAccess) {
                assert consumer.getVectorInputs().size() == 1 : consumer + " " + consumer.getVectorInputs();
                VectorNode input = (VectorNode) consumer.getVectorInputs().get(0);
                Stamp elementStamp = input.getVectorStamp().getElementStamp();
                ret = getSupportedLength((VectorAccess) consumer, elementStamp, ret, arch);
                ret = getSupportedLength(input, ret, arch);
            } else if (consumer instanceof FoldVectorNode) {
                FoldVectorNode fold = (FoldVectorNode) consumer;
                if (fold.isAssociativeAndCommutative()) {
                    int adjustedUpperBound = limitBoundForFold(upperBound, fold);
                    NodeMap<Integer> lengthCache = fold.getOp().createNodeMap();
                    ret = getSupportedLengthInSubgraph(fold.getVectorInputs(), SubGraphUtil.getResult(fold), adjustedUpperBound, arch, lengthCache);
                } else {
                    debug.log(DebugContext.DETAILED_LEVEL, "fold %s vector length 1 because it is not associative and commutative", fold);
                    ret = 1;
                }
            } else if (consumer instanceof VectorGuardNode || consumer instanceof VectorSafepointNode) {
                for (ValueNode input : consumer.getVectorInputs()) {
                    if (allowOversizedSequenceVector((LowerableVectorConsumer) consumer, input)) {
                        /* No need to restrict the vector length. */
                        continue;
                    } else {
                        ret = getSupportedLength((VectorNode) input, ret, arch);
                    }
                }
                if (consumer instanceof VectorGuardNode guard && !(guard.getCondition() instanceof InvariantVectorLogicNode)) {
                    Stamp elementStamp = guard.getCondition().getElementStamp();
                    if (elementStamp instanceof LogicValueStamp) {
                        ret = Math.min(ret, arch.getMaxLogicVectorLength(elementStamp));
                    } else {
                        ret = arch.getSupportedSimdMaskLogicLength(elementStamp, ret);
                    }
                }
            } else if (consumer instanceof VectorReachabilityFenceNode) {
                for (ValueNode input : consumer.getVectorInputs()) {
                    ret = getSupportedLength((VectorNode) input, ret, arch);
                }
            } else {
                GraalError.shouldNotReachHere("vector consumer " + consumer + " should be handled explicitly in length calculation"); // ExcludeFromJacocoGeneratedReport
            }
        }

        if (ret == upperBound && !CodeUtil.isPowerOf2(upperBound)) {
            // The upper bound can be of the form 2**n - 1, and the consumer might be a vector
            // safepoint that doesn't restrict the supported length. In this case, strip out all but
            // the highest bit to ensure this is a power of 2. Note that this value is one length,
            // not a mask, so this operation doesn't lose length mask bits.
            ret = Integer.highestOneBit(ret);
        }

        if (ret > 1 && !target.arch.supportsUnalignedMemoryAccess()) {
            // the vector snippets only align the top-level vector consumer. on platforms that
            // require naturally aligned accesses, we therefore need to check if aligning the
            // top-level consumer guarantees that all other vector accesses are aligned as well.

            // we are on the safe side if all vector consumers use the same offset in elements
            boolean allInputsUseSameOffset = new SameOffsetAndStrideClosure().allUseSameOffsetAndStride(consumer.asNode(), arch);

            if (!allInputsUseSameOffset) {
                // we need to use the common alignment that is guaranteed for all vector accesses
                ret = Math.min(ret, getCommonAlignmentInElements(consumer, arch));
            }
        }
        return ret;
    }

    /**
     * Vector guards and safepoints have vector sequence values in their frame state. These
     * sequences correspond to loop IVs. When such a consumer is simdified, we are only interested
     * in the first element of the sequence, and we generate scalar code (see
     * VectorGuardNode#firstComponentOfSequence and SimdCutNode#tryToScalarize). Such inputs
     * therefore don't restrict this consumer's vector length.
     * <p/>
     *
     * We can only do this for consumers that iterate upwards; for downwards consumers we want to
     * extract the last instead of the first element. For this we need a valid vector size.
     */
    private static boolean allowOversizedSequenceVector(LowerableVectorConsumer consumer, ValueNode input) {
        return (consumer instanceof VectorGuardNode || consumer instanceof VectorSafepointNode) &&
                        input instanceof SequenceVectorNode && consumer.direction() == InductionVariable.Direction.Up;
    }

    private static int limitBoundForFold(int upperBound, FoldVectorNode fold) {
        // For fold operations we must set up a vector accumulator, then do an expensive horizontal
        // reduction on its elements after the main vector loop. If the vector loop is never
        // executed (or only very few times), this is not worth it. Therefore, if we have reliable
        // information on the expected number of elements to process, we limit the vector length to
        // ensure that we can properly take advantage of a vector computation. For very short
        // computations this can reduce the vector length to 1 (i.e., we only generate scalar code).
        if (fold.trustedBodyIterations() > 0) {
            int adjustedBound = upperBound;
            int minIterations = Options.VectorFoldMinIterations.getValue(fold.graph().getOptions());
            while (adjustedBound > 1 && adjustedBound > fold.trustedBodyIterations() / minIterations) {
                adjustedBound /= 2;
            }
            fold.asNode().getDebug().log(DebugContext.INFO_LEVEL, "%s: trusted body iterations %f, min fold iterations %d, adjusted bound from %d to %d", fold, fold.trustedBodyIterations(),
                            minIterations, upperBound, adjustedBound);
            return adjustedBound;
        } else {
            return upperBound;
        }
    }

    private static final class SameOffsetAndStrideClosure {
        private ValueNode expectedOffset;
        private int expectedStride;

        public boolean allUseSameOffsetAndStride(Node node, VectorArchitecture arch) {
            if (node instanceof VectorAccess) {
                VectorAccess vectorAccess = (VectorAccess) node;
                OffsetAddressNode offsetAddress = (OffsetAddressNode) vectorAccess.getAddress();
                if (expectedOffset == null) {
                    expectedOffset = offsetAddress.getOffset();
                    expectedStride = vectorAccess.getElementStride();
                }

                if (!(offsetAddress.getBase().stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp) || !expectedOffset.equals(offsetAddress.getOffset()) ||
                                vectorAccess.getElementStride() != expectedStride) {
                    return false;
                }
            }

            if (node instanceof VectorOperation) {
                VectorOperation operation = (VectorOperation) node;
                for (ValueNode input : operation.getVectorInputs()) {
                    assert !(input instanceof ConcatVectorNode) : "at this point in time, all ConcatVectorNodes must already have been replaced";
                    boolean result = allUseSameOffsetAndStride(input, arch);
                    if (!result) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    public static int getMinimalElementsForVector(VectorConsumer consumer, VectorArchitecture arch) {
        if (consumer instanceof VectorWriteNode) {
            VectorStamp vectorStamp = ((VectorWriteNode) consumer).getVector().getVectorStamp();
            int maxVectorLength = arch.getMaxVectorLength(vectorStamp.getElementStamp());
            return arch.getMinimalElementsForVectorization(vectorStamp.getElementStamp(), maxVectorLength, consumer.asNode().getOptions());
        } else {
            return 0;
        }
    }
}
