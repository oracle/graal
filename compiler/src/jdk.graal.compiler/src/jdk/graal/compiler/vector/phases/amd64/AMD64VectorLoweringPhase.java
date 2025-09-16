/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.phases.amd64;

import java.util.Arrays;
import java.util.Optional;

import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AbsNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.AndNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.CompressBitsNode;
import jdk.graal.compiler.nodes.calc.FloatConvertNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.calc.IntegerTestNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.NegateNode;
import jdk.graal.compiler.nodes.calc.NotNode;
import jdk.graal.compiler.nodes.calc.OpMaskOrTestNode;
import jdk.graal.compiler.nodes.calc.OpMaskTestNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.calc.ShiftNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.calc.UnaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.XorNode;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.vector.architecture.VectorLoweringProvider;
import jdk.graal.compiler.vector.architecture.amd64.VectorAMD64;
import jdk.graal.compiler.vector.architecture.amd64.VectorAMD64.MaySimulateBT;
import jdk.graal.compiler.vector.lir.amd64.AMD64AVX512ArithmeticLIRGenerator;
import jdk.graal.compiler.vector.nodes.amd64.AMD64SimdSliceNode;
import jdk.graal.compiler.vector.nodes.amd64.AVX512MaskedOpNode;
import jdk.graal.compiler.vector.nodes.amd64.GeneralSimdPermuteNode;
import jdk.graal.compiler.vector.nodes.amd64.IntegerToOpMaskNode;
import jdk.graal.compiler.vector.nodes.amd64.LaneSymmetricSimdPermuteNode;
import jdk.graal.compiler.vector.nodes.simd.LogicValueStamp;
import jdk.graal.compiler.vector.nodes.simd.MaskedOpMetaData;
import jdk.graal.compiler.vector.nodes.simd.SimdBlendWithConstantMaskNode;
import jdk.graal.compiler.vector.nodes.simd.SimdBlendWithLogicMaskNode;
import jdk.graal.compiler.vector.nodes.simd.SimdBroadcastNode;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdCutNode;
import jdk.graal.compiler.vector.nodes.simd.SimdInsertNode;
import jdk.graal.compiler.vector.nodes.simd.SimdMaskLogicNode;
import jdk.graal.compiler.vector.nodes.simd.SimdNarrowingReinterpretNode;
import jdk.graal.compiler.vector.nodes.simd.SimdPermuteNode;
import jdk.graal.compiler.vector.nodes.simd.SimdPermuteWithVectorIndicesNode;
import jdk.graal.compiler.vector.nodes.simd.SimdPrimitiveCompareNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.nodes.simd.SimdToBitMaskNode;
import jdk.graal.compiler.vector.nodes.simd.TargetVectorLoweringUtils;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;

/**
 * This class implements custom vector lowerings for AMD64 as some operations cannot be handled by
 * the backend directly. This also allows emulating instructions that are not available in the SSE*
 * or AVX* instruction sets.
 *
 * When adding further emulated instructions or when changing the used instructions of emulated
 * instructions, it might be necessary to adapt the instruction set tables in
 * {@link jdk.graal.compiler.vector.architecture.amd64.VectorAMD64} as well.
 */
public class AMD64VectorLoweringPhase extends BasePhase<LowTierContext> {

    @Override
    public boolean mustApply(GraphState graphState) {
        return graphState.requiresFutureStage(StageFlag.TARGET_VECTOR_LOWERING) || super.mustApply(graphState);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.unlessRunAfter(this, StageFlag.FINAL_CANONICALIZATION, graphState);
    }

    @Override
    public void run(StructuredGraph graph, LowTierContext context) {
        VectorAMD64 vectorArch = (VectorAMD64) ((VectorLoweringProvider) context.getLowerer()).getVectorArchitecture();
        for (Node node : graph.getNodes()) {
            if (node instanceof SimdCutNode) {
                lowerSimdCut((SimdCutNode) node, context, vectorArch);
            } else if (node instanceof SimdInsertNode s) {
                lowerSimdInsert(s, vectorArch);
            } else if (node instanceof SimdPermuteNode) {
                lowerSimdPermute((SimdPermuteNode) node, context, vectorArch);
            } else if (node instanceof SimdMaskLogicNode) {
                lowerSimdMaskLogic((SimdMaskLogicNode) node, vectorArch);
            } else if (node instanceof SimdBlendWithConstantMaskNode blend) {
                lowerSimdBlendWithConstantMask(blend);
            } else if (node instanceof SimdBlendWithLogicMaskNode blend) {
                lowerSimdBlend(vectorArch, blend);
            } else if (node instanceof AndNode and && and.stamp(NodeView.DEFAULT) instanceof SimdStamp simdStamp && simdStamp.getComponent(0) instanceof LogicValueStamp) {
                lowerSimdMaskAnd(vectorArch, and);
            } else if (shouldLowerSimdUnsignedCompare(node)) {
                lowerSimdUnsignedCompare(node, vectorArch);
            } else if (node instanceof SimdToBitMaskNode s) {
                lowerSimdToBitMask(s, vectorArch);
            } else if (node instanceof ValueNode) {
                ValueNode valueNode = (ValueNode) node;
                if (valueNode.stamp(NodeView.DEFAULT) instanceof SimdStamp s) {
                    lowerSimdNode(valueNode, s, context, vectorArch);
                }
            }
        }
    }

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        graphState.removeRequirementToStage(StageFlag.TARGET_VECTOR_LOWERING);
    }

    /**
     * Check if the specified permutation is suitable for encoding using an immediate permute mask
     * <p>
     * PSHUFD / PSHUFLW / PUSHFHW / QPERM use an 8bit immediate to encode the permutation to be
     * performed. The immediate is sufficient to specify the permutation for 128bits of register
     * data. To extend these operations to 256bit and 512bit vector registers the operation is
     * applied repeatedly across each 128bit register segment. These segments are referred to as
     * vector lanes in Intel documentation. This fucntion checks if the specified permutation can be
     * contained by the range addressable by an immediate and is symmetric across lanes to allow for
     * a higher performance instruction to be used rather than PSHUFB which requires expensive
     * constant loading in the general case.
     *
     * @param vectorSize The size of the vector register
     * @param elementSize The kind of element stored in the vector
     * @param destinationMapping The mapping of source vector elements to destination elements
     * @return true iff the {@code destinationMapping} can be encoded using a constant form of
     *         permute/shuffle
     */
    private static boolean isLaneSymmetric(Architecture arch, int vectorSize, int elementSize, int[] destinationMapping) {
        // QPERM can handle up to 256bit vectors without lanes so reflect that fact here

        int laneSize = (elementSize == 8 ? 32 : 16) / elementSize;
        int numberOfLanes = vectorSize / (elementSize == 8 ? 32 : 16) + (vectorSize % (elementSize == 8 ? 32 : 16) != 0 ? 1 : 0);

        // handle cases of SIMD values which are not an exact multiple of the vector lane sizes
        // when more than one lane is required
        if (numberOfLanes > 1 && destinationMapping.length % laneSize != 0) {
            return false;
        }

        // Highspeed word shuffles are handled in two self-contained parts (low-word and high-word)
        // so we need to check first that we satify this condition for the first lane of match
        // indices
        if (elementSize == 2) {
            for (int i = 0; i < destinationMapping.length && i < 4; ++i) {
                if (destinationMapping[i] > 3) {
                    return false;
                }
            }
            for (int i = 4; i < destinationMapping.length && i < 8; ++i) {
                if (destinationMapping[i] < 4 || destinationMapping[i] > 7) {
                    return false;
                }
            }
        }

        for (int i = 0; i < laneSize && i < destinationMapping.length; ++i) {
            int target = destinationMapping[i];
            if (target >= laneSize) {
                return false;
            }
            for (int lane = 1; lane < numberOfLanes; ++lane) {
                int laneTarget = destinationMapping[lane * laneSize + i];
                if (laneTarget == -1) {
                    continue;
                }
                if (target == -1) {
                    return false;
                }
                if (laneTarget - (lane * laneSize) != target) {
                    return false;
                }
            }
        }

        // zeroing is currently only supported on VPERMQ which is used for vectors of longs of
        // length > 2
        if (!((AMD64) arch).getFeatures().contains(AMD64.CPUFeature.AVX512F) || elementSize < 8 || vectorSize < 32) {
            // zeroing is not supported in general by lane symmetric operations
            for (int i = 0; i < destinationMapping.length; ++i) {
                if (destinationMapping[i] == -1) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void lowerSimdPermute(SimdPermuteNode permute, LowTierContext context, VectorAMD64 vectorArch) {
        StructuredGraph graph = permute.graph();
        Stamp pStamp = permute.stamp(NodeView.DEFAULT);
        assert pStamp instanceof SimdStamp : pStamp;

        SimdStamp vectorStamp = (SimdStamp) permute.stamp(NodeView.DEFAULT);
        Stamp elementStamp = vectorStamp.getComponent(0);
        int elementBytes = vectorArch.getVectorStride(elementStamp);
        int vectorBytes = elementBytes * vectorStamp.getVectorLength();

        int rotateLeftAmount = vectorRotateLeftAmount(permute);
        if (rotateLeftAmount != -1) {
            permute.replaceAtUsagesAndDelete(graph.addOrUnique(AMD64SimdSliceNode.create(permute.getValue(), permute.getValue(), rotateLeftAmount)));
        } else if (elementBytes > 1 && isLaneSymmetric(context.getTarget().arch, vectorBytes, elementBytes, permute.getDestinationMapping())) {
            permute.replaceAtUsagesAndDelete(
                            graph.addOrUnique(new LaneSymmetricSimdPermuteNode(permute.stamp(NodeView.DEFAULT), permute.getValue(), permute.getDestinationMapping())));
        } else {
            ValueNode result = graph.unique(GeneralSimdPermuteNode.create(vectorArch, permute.getValue(), permute.getDestinationMapping()));
            permute.replaceAtUsagesAndDelete(result);
        }
    }

    /**
     * If a permute is recognized to be a left rotation, we can replace an {@link SimdPermuteNode}
     * with a more optimal {@link AMD64SimdSliceNode}.
     */
    private static int vectorRotateLeftAmount(SimdPermuteNode permute) {
        int[] indices = permute.getDestinationMapping();
        int origin = indices[0];
        if (origin == -1) {
            return -1;
        }

        GraalError.guarantee(origin >= 0 && origin < indices.length, "unexpected index %d", origin);
        for (int i = 1; i < indices.length; i++) {
            int expected = origin + i;
            expected = expected >= indices.length ? expected - indices.length : expected;
            if (indices[i] != expected) {
                return -1;
            }
        }

        return origin;
    }

    private static void lowerSimdCut(SimdCutNode cut, LowTierContext context, VectorAMD64 vectorArch) {
        ValueNode l = TargetVectorLoweringUtils.legalizeSimdCutLength(cut, context);
        if (l != cut) {
            cut.replaceAtUsagesAndDelete(l);
            GraalError.guarantee(!(l instanceof SimdCutNode), "should not be another cut");
            return;
        }

        StructuredGraph graph = cut.graph();
        Stamp cutStamp = cut.stamp(NodeView.DEFAULT);
        Stamp elementStamp = cutStamp instanceof SimdStamp
                        ? ((SimdStamp) cutStamp).getComponent(0)
                        : cutStamp;

        int length = cut.getLength();
        GraalError.guarantee(NumUtil.isUnsignedPowerOf2(length), "unexpected length %d", length);
        int elementBits = vectorArch.getVectorStride(elementStamp) * Byte.SIZE;

        if (cut.getOffset() % length != 0) {
            // unaligned cut, lower to permute
            int[] destinationMapping = new int[length];
            for (int i = 0; i < destinationMapping.length; i++) {
                destinationMapping[i] = cut.getOffset() + i;
            }
            SimdPermuteNode permute = cut.graph().addOrUnique(new SimdPermuteNode(cut.getValue(), destinationMapping));
            cut.replaceAtUsagesAndDelete(permute);
            return;
        }

        int bitOffset = cut.getOffset() * elementBits;
        if (bitOffset < 128) {
            // We can always cut a vector in the lowest XMM lane
            return;
        }

        if (cutStamp instanceof IntegerStamp) {
            // This cut moves the element to a GPR
            GraalError.guarantee(length == 1, "%s", cutStamp);
            // Cut the XMM lane then extract the element from there
            int xmmLength = 128 / elementBits;
            SimdCutNode xmm = graph.unique(new SimdCutNode(cut.getValue(), (bitOffset / 128) * xmmLength, xmmLength));
            SimdCutNode result = graph.unique(new SimdCutNode(xmm, (bitOffset % 128) / elementBits, 1));
            cut.replaceAtUsagesAndDelete(result);
            return;
        }

        if (bitOffset % 128 == 0) {
            // It is possible to cut this simply using vextract[if]
            return;
        }
        if (bitOffset == 192 && vectorArch.arch.getFeatures().contains(AMD64.CPUFeature.AVX2)) {
            // This can be done with a vpermpd
            return;
        }

        // Now the only thing we can do is to cut the XMM lane then cut again from there
        int xmmLength = 128 / elementBits;
        SimdCutNode xmm = graph.unique(new SimdCutNode(cut.getValue(), (bitOffset / 128) * xmmLength, xmmLength));
        SimdCutNode result = graph.unique(new SimdCutNode(xmm, (bitOffset % 128) / elementBits, length));
        cut.replaceAtUsagesAndDelete(result);
    }

    private static void lowerSimdInsert(SimdInsertNode node, VectorAMD64 vectorArch) {
        StructuredGraph graph = node.graph();
        int offset = node.offset();
        SimdStamp vecStamp = (SimdStamp) node.getX().stamp(NodeView.DEFAULT);
        Stamp valStamp = node.getY().stamp(NodeView.DEFAULT);
        PrimitiveStamp elementStamp = (PrimitiveStamp) vecStamp.getComponent(0);
        int elementBits = vectorArch.getVectorStride(elementStamp) * Byte.SIZE;
        int vecBits = elementBits * vecStamp.getVectorLength();
        int valBits = elementBits * (valStamp instanceof SimdStamp s ? s.getVectorLength() : 1);
        if (vecBits <= 128 || valBits >= 128) {
            return;
        }

        // Need to cut out the 128-bit lane to insert on that
        int elementsPerLane = 128 / elementBits;
        int laneOffset = offset / elementsPerLane * elementsPerLane;
        SimdCutNode lane = graph.unique(new SimdCutNode(node.getX(), laneOffset, elementsPerLane));
        ValueNode insertedLane = graph.addOrUnique(SimdInsertNode.create(lane, node.getY(), offset % elementsPerLane));
        ValueNode result = graph.addOrUnique(SimdInsertNode.create(node.getX(), insertedLane, laneOffset));
        node.replaceAtUsagesAndDelete(result);
    }

    private static void lowerSimdToBitMask(SimdToBitMaskNode node, VectorAMD64 vectorArch) {
        SimdStamp inputStamp = (SimdStamp) node.getValue().stamp(NodeView.DEFAULT);
        if (!(inputStamp.getComponent(0) instanceof IntegerStamp i) || i.getBits() != Short.SIZE) {
            return;
        }

        // word-element bit mask cannot be converted directly, reinterpret it as a byte mask and
        // pext the result
        if (!vectorArch.arch.getFeatures().contains(AMD64.CPUFeature.BMI2)) {
            return;
        }

        StructuredGraph graph = node.graph();
        SimdStamp byteStamp = SimdStamp.broadcast(IntegerStamp.create(Byte.SIZE), inputStamp.getVectorLength() * 2);
        ValueNode input = graph.addOrUnique(ReinterpretNode.create(byteStamp, node.getValue(), NodeView.DEFAULT));
        SimdToBitMaskNode bitmask = graph.unique(new SimdToBitMaskNode(input));
        ConstantNode compressMask = ConstantNode.forLong(0x5555555555555555L, graph);
        ValueNode result = graph.unique(new CompressBitsNode(bitmask, compressMask));
        node.replaceAtUsagesAndDelete(result);
    }

    private static void lowerSimdNode(ValueNode node, SimdStamp stamp, LowTierContext context, VectorAMD64 vectorArch) {
        if (node instanceof NegateNode) {
            lowerVectorNegate((NegateNode) node, stamp);
        } else if (node instanceof NotNode) {
            lowerVectorNot((NotNode) node, stamp, vectorArch);
        } else if (node instanceof AbsNode) {
            lowerVectorAbs((AbsNode) node, stamp);
        } else if (node instanceof ShiftNode) {
            lowerVectorShift((ShiftNode<?>) node, vectorArch);
        } else if (node instanceof FloatConvertNode) {
            FloatConvertNode convert = (FloatConvertNode) node;
            if (convert.getFloatConvert() == FloatConvert.L2D) {
                if (!vectorArch.supportsLongToDoubleFloatConvert()) {
                    // emulate vectorized long to double on CPUs that don't support it natively
                    lowerVectorLongToDouble(convert, stamp);
                }
            }
        } else if (node instanceof ConstantNode) {
            TargetVectorLoweringUtils.uniqueSimdConstant((ConstantNode) node, stamp, context, vectorArch);
        }
    }

    private static void lowerVectorNegate(NegateNode negate, SimdStamp stamp) {
        StructuredGraph graph = negate.graph();
        Stamp elementStamp = stamp.getComponent(0);
        if (elementStamp instanceof IntegerStamp) {
            Constant scalarZero = JavaConstant.forPrimitiveInt(((IntegerStamp) elementStamp).getBits(), 0);
            Constant vectorZero = SimdConstant.broadcast(scalarZero, stamp.getVectorLength());
            ConstantNode zeroNode = graph.unique(ConstantNode.forConstant(stamp, vectorZero, null));
            // We need to create the node without canonicalization because
            // on AMD64 there is no vectorized negate
            ValueNode minus = graph.addOrUniqueWithInputs(new SubNode(zeroNode, negate.getValue()));
            negate.replaceAtUsagesAndDelete(minus);
        } else if (elementStamp instanceof FloatStamp) {
            ValueNode scalarMask;
            if (((FloatStamp) elementStamp).getBits() == 32) {
                scalarMask = ConstantNode.forFloat(Float.intBitsToFloat(0x80000000), graph);
            } else {
                scalarMask = ConstantNode.forDouble(Double.longBitsToDouble(0x8000000000000000L), graph);
            }
            ValueNode vectorMask = graph.unique(new SimdBroadcastNode(scalarMask, stamp.getVectorLength()));
            ValueNode xor = graph.unique(new XorNode(negate.getValue(), vectorMask));
            negate.replaceAtUsagesAndDelete(xor);
        } else {
            throw GraalError.shouldNotReachHereUnexpectedValue(elementStamp); // ExcludeFromJacocoGeneratedReport
        }
    }

    private static void lowerVectorNot(NotNode not, SimdStamp stamp, VectorAMD64 vectorArch) {
        if (SimdStamp.isOpmask(stamp)) {
            // this is an op mask not, this does not need to be transformed into an xor
            GraalError.guarantee(!vectorArch.logicVectorsAreBitmasks(), "op mask NOT is only allowed on op masks!");
            return;
        }
        StructuredGraph graph = not.graph();
        PrimitiveStamp elementStamp = (PrimitiveStamp) stamp.getComponent(0);
        int bits = elementStamp.getBits();
        Constant scalarMask = JavaConstant.forPrimitiveInt(bits, CodeUtil.mask(bits));
        Constant vectorMask = SimdConstant.broadcast(scalarMask, stamp.getVectorLength());
        ConstantNode maskNode = graph.unique(ConstantNode.forConstant(stamp, vectorMask, null));
        ValueNode xor = graph.unique(new XorNode(not.getValue(), maskNode));
        not.replaceAtUsagesAndDelete(xor);
    }

    private static void lowerVectorAbs(AbsNode node, SimdStamp stamp) {
        if (stamp.getComponent(0) instanceof FloatStamp) {
            StructuredGraph graph = node.graph();
            ValueNode scalarMask;
            if (((FloatStamp) stamp.getComponent(0)).getBits() == 32) {
                scalarMask = ConstantNode.forFloat(Float.intBitsToFloat(0x7FFFFFFF), graph);
            } else {
                scalarMask = ConstantNode.forDouble(Double.longBitsToDouble(0x7FFFFFFFFFFFFFFFL), graph);
            }
            ValueNode vectorMask = graph.unique(new SimdBroadcastNode(scalarMask, stamp.getVectorLength()));
            ValueNode and = graph.unique(new AndNode(node.getValue(), vectorMask));
            node.replaceAtUsagesAndDelete(and);
        }
    }

    private static void lowerVectorShift(ShiftNode<?> node, VectorAMD64 vectorArch) {
        StructuredGraph graph = node.graph();
        int mask = node.getShiftAmountMask();
        Stamp yStamp = node.getY().stamp(NodeView.DEFAULT);
        if (yStamp instanceof SimdStamp shift) {
            for (int i = 0; i < shift.getVectorLength(); i++) {
                IntegerStamp e = (IntegerStamp) shift.getComponent(i);
                if ((e.mayBeSet() & mask) != e.mayBeSet()) {
                    /*
                     * AVX vector shift instructions don't mask their argument, so we have to add a
                     * manual mask operation.
                     */
                    SimdBroadcastNode maskVector = graph.unique(new SimdBroadcastNode(ConstantNode.forIntegerBits(e.getBits(), mask, graph), shift.getVectorLength()));
                    AndNode newY = graph.unique(new AndNode(node.getY(), maskVector));
                    node.setY(newY);
                    break;
                }
            }

            return;
        }

        IntegerStamp shift = (IntegerStamp) yStamp;
        if ((shift.mayBeSet() & mask) != shift.mayBeSet()) {
            /*
             * AVX vector shift instructions don't mask their argument, so we have to add a manual
             * mask operation.
             */
            ConstantNode maskNode = ConstantNode.forInt(mask, graph);
            AndNode masked = graph.unique(new AndNode(node.getY(), maskNode));
            node.setY(masked);
        }
        if (node instanceof LeftShiftNode && node.getY().isJavaConstant()) {
            SimdStamp simdStamp = (SimdStamp) node.stamp(NodeView.DEFAULT);
            int length = simdStamp.getVectorLength();
            IntegerStamp elementStamp = (IntegerStamp) simdStamp.getComponent(0);
            if (vectorArch.getSupportedVectorShiftWithScalarCount(elementStamp, length, elementStamp.getOps().getShl()) != length) {
                /*
                 * No AVX version supports left shifts on bytes, and some other variants may also be
                 * unsupported on some targets. We can get here for code like (x + x) which after
                 * vector simplifications and canonicalization turned into (x << 1). Legalize it. We
                 * must use repeated addition because multiply on bytes isn't supported either.
                 */
                int remainingShift = node.getY().asJavaConstant().asInt();
                ValueNode replacement = node.getX();
                while (remainingShift-- > 0) {
                    replacement = graph.unique(new AddNode(replacement, replacement));
                }
                node.replaceAndDelete(replacement);
            }
        }
    }

    private static void lowerVectorLongToDouble(FloatConvertNode convert, SimdStamp resultStamp) {
        /*
         * @formatter:off
         *
         * While there exists no vectorized long to double instruction on x86, if `x` fits in the
         * mantissa field of the double (52 bits), then this easily vectorizable formula gives an
         * alternative way to convert to double:
         *
         * (x + magic) -d magic
         *
         * where + is 64bit integer addition, and -d is double subtraction.
         *
         * For unsigned inputs (between 0 and 2^52-1), magic = 2^52 (encoded as double). For signed
         * inputs (between -2^51 and 2^51), magic = 2^52 + 2^51 (encoded as double).
         *
         * (source: Hacker's delight, Table 17-2. Floating-Point Conversions)
         *
         * To see why this works, it's useful to look at the encoding of 2^52 as double:
         *
         * 0x4330_0000_0000_0000 = sign 0, exponent 52, mantissa 1000... (implicit 1 + 52 zeroes)
         *
         * The mantissa is in the lower 52 bits of the encoding, so adding an unsigned 52-bit
         * integer `x` (bits 52..63 are zero) adds that number to the mantissa:
         *
         *   s eeeeeeeeeee 0000... (52 zeroes)
         * + 0 00000000000 xxxx... (52 bits of x)
         * -----------------------
         *   s eeeeeeeeeee xxxx...
         *
         * The resulting mantissa is 1000... (52 zeroes) + x, i.e. 2^52 + `x`. Since bits 52..63 of
         * `x` are zero, the high bits are unchanged (the exponent is still 52 and the sign is still
         * 0), so we now have the value 2^52 + `x` encoded as double. Now we just need to subtract
         * (double) 2^52 to get `x`.
         *
         * The signed case is the same, with the additional trick of adding and subtracting the
         * 52-bit MININT to convert signed to unsigned arithmetic, i.e. we start with the magic
         * number 2^52 + 2^51 encoded as double:
         *
         * 0x4338_0000_0000_0000 = sign 0, exponent 52, mantissa 11000... (51 zeroes)
         *
         * The value `x` is a 52-bit signed integer, that is, the bits 51..63 are either all 0 or
         * all 1. Adding that to the magic number:
         *
         *   (positive case)                            (negative case)
         *
         *   s eeeeeeeeeee 1000... (51 zeroes)          s eeeeeeeeeee 1000... (51 zeroes)
         * + 0 00000000000 0xxx... (51 bits of x)     + 1 11111111111 1xxx... (51 bits of x)
         * -----------------------                    -----------------------
         *   s eeeeeeeeeee 1xxx...                      s eeeeeeeeeee 0xxx...
         *
         * In both cases, the sign and exponent are left alone, so they are still 0 and 52,
         * respectively. The mantissa is now 110000... (51 zeroes) + `x` (signed integer addition),
         * that is, we now have 2^52 + 2^51 + `x` encoded as double, so we just subtract our magic
         * number (double) (2^52 + 2^51) to get `x`.
         *
         * @formatter:on
         */

        ValueNode x = convert.getValue();
        int length = resultStamp.getVectorLength();

        JavaConstant[] magic = new JavaConstant[length];
        SimdStamp xStamp = (SimdStamp) x.stamp(NodeView.DEFAULT);
        assert xStamp.getVectorLength() == length : xStamp;

        for (int i = 0; i < length; i++) {
            IntegerStamp element = (IntegerStamp) xStamp.getComponent(i);

            if (VectorAMD64.supportUnsignedLongToDouble(element)) {
                magic[i] = JavaConstant.forLong(0x4330_0000_0000_0000L); // (double) 2^52
            } else if (VectorAMD64.supportSignedLongToDouble(element)) {
                magic[i] = JavaConstant.forLong(0x4338_0000_0000_0000L); // (double) (2^52 + 2^51)
            } else {
                throw GraalError.shouldNotReachHere("invalid vectorized L2D: " + element); // ExcludeFromJacocoGeneratedReport
            }
        }
        Constant vectorMagic = new SimdConstant(magic);

        StructuredGraph graph = convert.graph();
        ValueNode magicInt = graph.unique(ConstantNode.forConstant(xStamp, vectorMagic, null));
        ValueNode xPlusMagicInt = graph.unique(new AddNode(x, magicInt));

        ValueNode xPlusMagicDouble = graph.addOrUnique(ReinterpretNode.create(resultStamp, xPlusMagicInt, NodeView.DEFAULT));

        ValueNode magicDouble = graph.addOrUnique(ReinterpretNode.create(resultStamp, magicInt, NodeView.DEFAULT));
        ValueNode xDouble = graph.unique(new SubNode(xPlusMagicDouble, magicDouble));

        convert.replaceAndDelete(xDouble);
    }

    private static void lowerSimdMaskLogic(SimdMaskLogicNode simdMaskLogic, VectorAMD64 vectorArch) {
        StructuredGraph graph = simdMaskLogic.graph();
        ValueNode vectorLogic = simdMaskLogic.getValue();
        SimdStamp simdStamp = (SimdStamp) vectorLogic.stamp(NodeView.DEFAULT);
        Stamp logicStamp = simdStamp.getComponent(0);

        if (AMD64BaseAssembler.supportsFullAVX512(vectorArch.arch.getFeatures())) {
            ValueNode testResult;
            if (simdStamp.getVectorLength() >= Byte.SIZE) {
                // if an entire instruction size can be used, we use KORTEST to test for
                // all_zeros/ones
                testResult = graph.addOrUniqueWithInputs(new OpMaskOrTestNode(vectorLogic, vectorLogic,
                                simdMaskLogic.getCondition() == SimdMaskLogicNode.Condition.ALL_ZEROS));
            } else {
                // if we can not use an entire instruction size, we need to build a mask to test
                // against
                ValueNode curMask = ConstantNode.forInt((int) CodeUtil.mask(simdStamp.getVectorLength()), graph);
                curMask = graph.addOrUniqueWithInputs(new IntegerToOpMaskNode(curMask, simdStamp));
                testResult = graph.addOrUniqueWithInputs(new OpMaskTestNode(vectorLogic, curMask,
                                simdMaskLogic.getCondition() == SimdMaskLogicNode.Condition.ALL_ONES));
            }

            simdMaskLogic.replaceAndDelete(testResult);
            return;
        }

        assert vectorArch.logicVectorsAreBitmasks() : "representing logic values as vectors of integer bitmasks";
        int vectorBits = simdStamp.getVectorLength() * vectorArch.getVectorStride(logicStamp) * Byte.SIZE;
        // If the condition mask fills out an entire XMM or YMM register, we can use VPTEST to check
        // it for all-zeros or all-ones.
        if (vectorBits == AVXSize.XMM.getBytes() * Byte.SIZE || vectorBits == AVXSize.YMM.getBytes() * Byte.SIZE) {
            ValueNode conditionMask = vectorLogic;
            if (simdMaskLogic.getCondition() == SimdMaskLogicNode.Condition.ALL_ONES) {
                conditionMask = NotNode.create(conditionMask);
            }
            LogicNode vectorTest = simdMaskLogic.graph().addOrUniqueWithInputs(IntegerTestNode.create(conditionMask, conditionMask, NodeView.DEFAULT));
            simdMaskLogic.replaceAndDelete(vectorTest);
            return;
        }

        // Otherwise, use VPMOVMSKB or VMOVMSKP[DS] to extract a bit mask and compare it to a
        // scalar constant.
        int compareBits = simdStamp.getVectorLength();
        if (vectorArch.getVectorStride(logicStamp) == Short.BYTES) {
            compareBits *= 2;
            vectorLogic = graph.addOrUnique(ReinterpretNode.create(SimdStamp.broadcast(IntegerStamp.create(Byte.SIZE), compareBits), vectorLogic, NodeView.DEFAULT));
        }
        ValueNode compareBitMask = ConstantNode.forLong(simdMaskLogic.getCondition() == SimdMaskLogicNode.Condition.ALL_ZEROS ? 0 : CodeUtil.mask(compareBits), graph);
        ValueNode conditionMask = graph.unique(new SimdToBitMaskNode(vectorLogic));
        LogicNode replacementCondition = graph.unique(IntegerEqualsNode.create(conditionMask, compareBitMask, NodeView.DEFAULT));
        simdMaskLogic.replaceAndDelete(replacementCondition);
    }

    private static AMD64Kind stampToElementPlatformKind(Stamp stamp) {
        Stamp eStamp = ((SimdStamp) stamp).getComponent(0);
        if (eStamp instanceof IntegerStamp i) {
            return switch (i.getBits()) {
                case Byte.SIZE -> AMD64Kind.BYTE;
                case Short.SIZE -> AMD64Kind.WORD;
                case Integer.SIZE -> AMD64Kind.DWORD;
                case Long.SIZE -> AMD64Kind.QWORD;
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(i);
            };
        } else {
            FloatStamp f = (FloatStamp) eStamp;
            return switch (f.getBits()) {
                case Float.SIZE -> AMD64Kind.SINGLE;
                case Double.SIZE -> AMD64Kind.DOUBLE;
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(f);
            };
        }
    }

    /**
     * If a blend is recognized to do a slice operation (see {@link AMD64SimdSliceNode} and
     * {@code jdk.incubator.vector.Vector::slice}), we can replace it with the more optimal
     * {@link AMD64SimdSliceNode}.
     */
    private static void lowerSimdBlendWithConstantMask(SimdBlendWithConstantMaskNode blend) {
        boolean[] mask = blend.getSelector();
        boolean trueValueIsSrc1 = mask[0];

        // Search for an index origin such that all values before origin are the same, and all
        // values from origin are the same and different from the values before origin
        int origin = 0;
        for (int i = 1; i < mask.length; i++) {
            if (mask[i] != trueValueIsSrc1) {
                origin = i;
                break;
            }
        }
        for (int i = origin; i < mask.length; i++) {
            if (mask[i] == trueValueIsSrc1) {
                return;
            }
        }

        // The actual origin of a slice is mask.length - origin
        origin = mask.length - origin;
        GraalError.guarantee(origin > 0 && origin < mask.length, "unexpected origin value for %s", Arrays.toString(mask));

        StructuredGraph graph = blend.graph();
        ValueNode src1;
        if (blend.getFalseValues() instanceof AMD64SimdSliceNode slice &&
                        slice.getSrc1() == slice.getSrc2() && slice.getOrigin() == origin) {
            src1 = slice.getSrc1();
        } else if (blend.getFalseValues() instanceof SimdNarrowingReinterpretNode in && in.getValue().isDefaultConstant()) {
            src1 = in;
        } else if (blend.getFalseValues().isConstant()) {
            // We want to ensure we can actually create a slice node before rotating the constant
            // below
            if (blend.getTrueValues() instanceof AMD64SimdSliceNode slice &&
                            slice.getSrc1() == slice.getSrc2() && slice.getOrigin() == origin) {
                src1 = rotateRightConstantVector(graph, (SimdConstant) blend.getFalseValues().asConstant(), origin);
            } else {
                return;
            }
        } else {
            return;
        }

        // At this point we know that blend.getFalseValues() is of a suitable form
        ValueNode src2;
        if (blend.getTrueValues() instanceof AMD64SimdSliceNode slice &&
                        slice.getSrc1() == slice.getSrc2() && slice.getOrigin() == origin) {
            src2 = slice.getSrc1();
        } else if (blend.getTrueValues() instanceof SimdNarrowingReinterpretNode in && in.getValue().isDefaultConstant()) {
            src2 = in;
        } else if (blend.getTrueValues().isConstant()) {
            src2 = rotateRightConstantVector(graph, (SimdConstant) blend.getTrueValues().asConstant(), origin);
        } else {
            return;
        }

        if (trueValueIsSrc1) {
            ValueNode tmp = src1;
            src1 = src2;
            src2 = tmp;
        }
        blend.replaceAndDelete(graph.addOrUniqueWithInputs(AMD64SimdSliceNode.create(src1, src2, origin)));
    }

    private static ValueNode rotateRightConstantVector(StructuredGraph graph, SimdConstant vector, int origin) {
        JavaConstant[] data = new JavaConstant[vector.getVectorLength()];
        for (int i = 0; i < data.length; i++) {
            int srcIdx = i - origin;
            if (srcIdx < 0) {
                srcIdx += data.length;
            }
            data[i] = (JavaConstant) vector.getValue(srcIdx);
        }
        return graph.addOrUnique(SimdConstant.constantNodeForConstants(data));
    }

    /**
     * This tries to convert a blend of an arithmetic op into a masked arithmetic op
     * <p>
     * E.g: {@code vpaddd zmm3, zmm1, zmm2; vpblendmd zmm0{k1}, zmm0, zmm3} can be converted to:
     * {@code vpaddd zmm0{k1}, zmm1, zmm2}.
     */
    private static void lowerSimdBlend(VectorAMD64 vectorArch, SimdBlendWithLogicMaskNode blend) {
        AMD64Kind eKind = stampToElementPlatformKind(blend.stamp(NodeView.DEFAULT));
        ValueNode op = blend.getTrueValues();
        ValueNode other = blend.getFalseValues();
        ValueNode selector = blend.getSelector();

        // Try both permutations
        if (!op.hasExactlyOneUsage() || AMD64AVX512ArithmeticLIRGenerator.getMaskedOpcode(vectorArch.arch, new MaskedOpMetaData(op), eKind, null) == null) {
            op = blend.getFalseValues();
            other = blend.getTrueValues();
            if (!op.hasExactlyOneUsage() || AMD64AVX512ArithmeticLIRGenerator.getMaskedOpcode(vectorArch.arch, new MaskedOpMetaData(op), eKind, null) == null) {
                return;
            }

            // For the commuted case we need to negate the selector
            selector = blend.graph().addOrUniqueWithInputs(NotNode.create(selector));
        }

        if (other.isConstant() && other.asConstant().isDefaultForKind()) {
            // Set other to null to signify zero-masking behavior
            other = null;
        }
        AVX512MaskedOpNode newNode = switch (op) {
            case SimdPermuteWithVectorIndicesNode permute -> AVX512MaskedOpNode.createPermute(permute, other, selector, permute.getX(), permute.getY());
            case UnaryArithmeticNode<?> u -> AVX512MaskedOpNode.createUnaryArithmetic(u, other, selector, u.getValue());
            case BinaryArithmeticNode<?> b -> AVX512MaskedOpNode.createBinaryArithmetic(b, other, selector, b.getX(), b.getY());
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(op);
        };
        newNode = blend.graph().unique(newNode);
        blend.replaceAtUsagesAndDelete(newNode);
    }

    private static void lowerSimdMaskAnd(VectorAMD64 vectorArch, AndNode and) {
        SimdPrimitiveCompareNode op = null;
        ValueNode other = null;
        // kand is expensive so we do the transformation regardless of whether op is used by other
        // nodes
        if (and.getX() instanceof SimdPrimitiveCompareNode compare) {
            op = compare;
            other = and.getY();
        } else if (and.getY() instanceof SimdPrimitiveCompareNode compare) {
            op = compare;
            other = and.getX();
        }
        if (op == null) {
            return;
        }
        AMD64Kind srcEKind = stampToElementPlatformKind(op.getX().stamp(NodeView.DEFAULT));
        if (AMD64AVX512ArithmeticLIRGenerator.getMaskedOpcode(vectorArch.arch, new MaskedOpMetaData(op), null, srcEKind) == null) {
            return;
        }

        AVX512MaskedOpNode newNode = AVX512MaskedOpNode.createBinaryComparison(op, other, op.getX(), op.getY());
        newNode = and.graph().unique(newNode);
        and.replaceAtUsagesAndDelete(newNode);
    }

    private static boolean shouldLowerSimdUnsignedCompare(Node node) {
        if (node instanceof SimdPrimitiveCompareNode && ((SimdPrimitiveCompareNode) node).getCondition() == CanonicalCondition.BT) {
            return true;
        } else if (node instanceof CompareNode) {
            CompareNode compare = (CompareNode) node;
            return compare.condition() == CanonicalCondition.BT && compare.getX().stamp(NodeView.DEFAULT) instanceof SimdStamp;
        }
        return false;
    }

    /**
     * Try to lower an unsigned SIMD comparison to a signed one, with modified inputs if needed. Due
     * to the intricacies of how different kinds of conditional vector code are created, the
     * comparison may be a {@link SimdPrimitiveCompareNode} (for vectorized guards) or a plan
     * {@link CompareNode} (for conditional expressions).
     */
    private static void lowerSimdUnsignedCompare(Node comparison, VectorAMD64 vectorArch) {
        ValueNode x;
        ValueNode y;
        StructuredGraph graph;
        if (comparison instanceof SimdPrimitiveCompareNode) {
            SimdPrimitiveCompareNode compareVector = (SimdPrimitiveCompareNode) comparison;
            assert compareVector.getCondition() == CanonicalCondition.BT : compareVector + " " + compareVector.getCondition();
            x = compareVector.getX();
            y = compareVector.getY();
            graph = compareVector.graph();
        } else {
            CompareNode compare = (CompareNode) comparison;
            assert compare.condition() == CanonicalCondition.BT : compare + " " + compare.condition();
            x = compare.getX();
            y = compare.getY();
            graph = compare.graph();
        }

        SimdStamp xSimdStamp = (SimdStamp) x.stamp(NodeView.DEFAULT);
        IntegerStamp elementStamp = (IntegerStamp) xSimdStamp.getComponent(0);
        if (vectorArch.getSupportedVectorComparisonLength(elementStamp, CanonicalCondition.BT, xSimdStamp.getVectorLength(), MaySimulateBT.NO) == 1) {
            // We should only get here if we are sure that we can simulate this unsigned compare
            // using xor and a signed compare.
            assert vectorArch.getSupportedVectorComparisonLength(elementStamp, CanonicalCondition.BT, xSimdStamp.getVectorLength(), MaySimulateBT.YES) == xSimdStamp.getVectorLength() : "VectorArch " +
                            vectorArch + " must have supported length for " + xSimdStamp;

            long signBit = 1L << (elementStamp.getBits() - 1);
            ValueNode signedX;
            ValueNode signedY;

            SimdStamp ySimdStamp = (SimdStamp) y.stamp(NodeView.DEFAULT);
            assert xSimdStamp.getVectorLength() == ySimdStamp.getVectorLength() : xSimdStamp + " " + ySimdStamp;
            boolean allSignsAgree = true;
            for (int i = 0; i < xSimdStamp.getVectorLength(); i++) {
                IntegerStamp xElement = (IntegerStamp) xSimdStamp.getComponent(i);
                IntegerStamp yElement = (IntegerStamp) ySimdStamp.getComponent(i);

                if ((xElement.mustBeSet() & signBit) != (xElement.mayBeSet() & signBit) ||
                                (yElement.mustBeSet() & signBit) != (yElement.mayBeSet() & signBit) ||
                                (xElement.mayBeSet() & signBit) != (yElement.mayBeSet() & signBit)) {
                    // We are not sure about one of the values' sign bits, or we are sure that the
                    // sign bits differ.
                    allSignsAgree = false;
                    break;
                }
            }

            if (allSignsAgree) {
                /*
                 * Corresponding elements of x and y have matching signs. In this case, unsigned and
                 * signed comparisons are equivalent, so we can just replace the unsigned comparison
                 * by a signed one.
                 */
                signedX = x;
                signedY = y;
            } else {
                /*
                 * Transform x |<| y to (x ^ signBit) < (y ^ signBit), i.e., compare the values as
                 * signed with both of their signs flipped. This works because (for each pair of
                 * elements):
                 *
                 * - If x and y have identical signs before the flip, they will have identical signs
                 * after the flip, and in this case |<| and < agree.
                 *
                 * - If x has sign bit 0 and y has sign bit 1, x |<| y holds. Flipping the signs
                 * makes the flipped x negative and the flipped y positive, so the flipped x is
                 * signed less than the flipped y.
                 *
                 * - If x has sign bit 1 and y has sign bit 0, x |<| y does not hold. Flipping the
                 * signs makes the flipped x positive and the flipped y negative, so the flipped x
                 * is not signed less than the flipped y.
                 */
                JavaConstant signBitConstant = JavaConstant.forPrimitiveInt(elementStamp.getBits(), signBit);
                ConstantNode simdSignBits = graph.addOrUniqueWithInputs(SimdConstant.constantNodeForBroadcast(signBitConstant, xSimdStamp.getVectorLength()));
                signedX = graph.addOrUniqueWithInputs(XorNode.create(x, simdSignBits, NodeView.DEFAULT));
                signedY = graph.addOrUniqueWithInputs(XorNode.create(y, simdSignBits, NodeView.DEFAULT));
            }

            ValueNode signedCompare;
            if (comparison instanceof SimdPrimitiveCompareNode) {
                signedCompare = graph.addOrUnique(SimdPrimitiveCompareNode.simdCompare(CanonicalCondition.LT, signedX, signedY, false, vectorArch));
            } else if (comparison instanceof CompareNode) {
                signedCompare = graph.addOrUniqueWithInputs(IntegerLessThanNode.create(signedX, signedY, NodeView.DEFAULT));
            } else {
                throw GraalError.shouldNotReachHereUnexpectedValue(comparison); // ExcludeFromJacocoGeneratedReport
            }
            comparison.replaceAtUsagesAndDelete(signedCompare);
        }
    }
}
