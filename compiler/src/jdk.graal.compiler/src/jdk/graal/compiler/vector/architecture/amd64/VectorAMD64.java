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
package jdk.graal.compiler.vector.architecture.amd64;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jdk.graal.compiler.asm.amd64.AMD64Assembler.VectorFeatureAssertion;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.core.common.calc.FloatConvertCategory;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.Op;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ShortCircuitOrNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.IntegerTestNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.NarrowableArithmeticNode;
import jdk.graal.compiler.nodes.calc.ShiftNode;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.LogicValueStamp;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CodeUtil;

public final class VectorAMD64 extends VectorArchitecture {

    private static final int BYTE_BITS = AMD64Kind.BYTE.getSizeInBytes() * Byte.SIZE;
    private static final int WORD_BITS = AMD64Kind.WORD.getSizeInBytes() * Byte.SIZE;
    private static final int DWORD_BITS = AMD64Kind.DWORD.getSizeInBytes() * Byte.SIZE;
    private static final int QWORD_BITS = AMD64Kind.QWORD.getSizeInBytes() * Byte.SIZE;
    private static final int SINGLE_BITS = AMD64Kind.SINGLE.getSizeInBytes() * Byte.SIZE;
    private static final int DOUBLE_BITS = AMD64Kind.DOUBLE.getSizeInBytes() * Byte.SIZE;

    public final AMD64 arch;
    private final boolean enabled;
    private final int objectAlignment;
    private final boolean enableObjectVectorization;

    private int maxVectorSize;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        VectorAMD64 that = (VectorAMD64) o;
        return enabled == that.enabled && objectAlignment == that.objectAlignment && enableObjectVectorization == that.enableObjectVectorization && maxVectorSize == that.maxVectorSize &&
                        Objects.equals(arch, that.arch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), arch, enabled, objectAlignment, enableObjectVectorization, maxVectorSize);
    }

    public VectorAMD64(AMD64 arch, boolean enabled, int oopVectorStride, boolean useCompressedOops, int objectAlignment) {
        this(arch, enabled, oopVectorStride, useCompressedOops, objectAlignment, arch.getLargestStorableKind(AMD64.XMM).getSizeInBytes(), true);
    }

    public VectorAMD64(AMD64 arch, int oopVectorStride, boolean useCompressedOops, int objectAlignment, int maxVectorSize, boolean enableObjectVectorization) {
        this(arch, true, oopVectorStride, useCompressedOops, objectAlignment, maxVectorSize, enableObjectVectorization);
    }

    private VectorAMD64(AMD64 arch, boolean enabled, int oopVectorStride, boolean useCompressedOops, int objectAlignment, int maxVectorSize, boolean enableObjectVectorization) {
        super(oopVectorStride, useCompressedOops);
        this.arch = arch;
        this.enabled = enabled;
        this.objectAlignment = objectAlignment;
        this.maxVectorSize = Math.min(maxVectorSize, maxVectorSizeForArchitecture(arch));
        this.enableObjectVectorization = enableObjectVectorization;
    }

    @Override
    public int getMaxVectorLength(Stamp stamp) {
        return getSupportedVectorMoveLength(stamp, Integer.MAX_VALUE);
    }

    @Override
    public int getMaxLogicVectorLength(Stamp logicStamp) {
        if (!logicVectorsAreBitmasks()) {
            return arch.getLargestStorableKind(AMD64.MASK).getSizeInBytes() * Byte.SIZE;
        } else {
            return getMaxVectorLength(logicStamp);
        }
    }

    @Override
    public int getShortestCoveringVectorLength(Stamp stamp, int length) {
        int stride = getVectorStride(stamp);
        int sizeInBytes = stride * length;
        int maxSupportedAVXBytes = getMaxSupportedAVXSize(arch.getFeatures()).getBytes();
        GraalError.guarantee(sizeInBytes <= maxSupportedAVXBytes, "Unable to cover requested vector length.");
        for (AVXSize size : AVXSize.values()) {
            if (size.getBytes() >= sizeInBytes) {
                return size.getBytes() / stride;
            }
        }
        throw GraalError.shouldNotReachHereUnexpectedValue(sizeInBytes); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public boolean shouldBroadcastVectorShiftCount(SimdStamp xStamp, IntegerStamp yStamp) {
        if (yStamp.asConstant() != null) {
            return false;
        }

        IntegerStamp eStamp = (IntegerStamp) xStamp.getComponent(0);
        if (eStamp.getBits() == Short.SIZE) {
            if (xStamp.getVectorLength() == AVXSize.ZMM.getBytes() / Short.BYTES) {
                GraalError.guarantee(arch.getFeatures().contains(CPUFeature.AVX512BW), "Must support");
                return true;
            }

            return arch.getFeatures().contains(CPUFeature.AVX512BW) && arch.getFeatures().contains(CPUFeature.AVX512VL);
        }

        GraalError.guarantee(eStamp.getBits() == Integer.SIZE || eStamp.getBits() == Long.SIZE, "Must be int or long");
        return arch.getFeatures().contains(CPUFeature.AVX2);
    }

    @Override
    public int getSupportedVectorMoveLength(Stamp stamp, int maxLength) {
        if (!hasMinimumVectorizationRequirements(maxLength)) {
            return 1;
        }

        AVXSize avxSize = getMaxSupportedAVXSize(arch.getFeatures());
        if (stamp.isObjectStamp() && avxSize.getBytes() > AVXSize.YMM.getBytes()) {
            /*
             * Temporarily limit oop vectors to size YMM. The ZGC barrier does not support ZMM oop
             * vectors yet (GR-47783), and the fix for that is blocked by GR-47596.
             */
            avxSize = AVXSize.YMM;
        }
        return getSupportedVectorLength(stamp, maxLength, avxSize);
    }

    @Override
    public int getSupportedVectorMaskedMoveLength(Stamp stamp, int maxLength) {
        int length = getSupportedVectorMoveLength(stamp, maxLength);
        if (length == 1) {
            return 1;
        }

        int elementBits = ((PrimitiveStamp) stamp).getBits();
        if (elementBits * length < AVXSize.XMM.getBytes() * Byte.SIZE) {
            return 1;
        }

        EnumSet<CPUFeature> features = arch.getFeatures();
        if (elementBits < Integer.SIZE && !(features.contains(CPUFeature.AVX512BW) && features.contains(CPUFeature.AVX512VL))) {
            // Subwords need avx512bwvl
            return 1;
        }
        return length;
    }

    @Override
    public int getSupportedVectorConvertLength(Stamp result, Stamp input, int maxLength, IntegerConvertOp<?> op) {
        if (!hasMinimumVectorizationRequirements(maxLength)) {
            return 1;
        }

        AVXSize avxSize = convertOps.getSupportedAVXSizeOrNull(op, ((PrimitiveStamp) input).getBits(), ((PrimitiveStamp) result).getBits(), maxLength);
        if (avxSize == null) {
            return 1;
        }
        return Math.min(getSupportedVectorLength(result, maxLength, avxSize), getSupportedVectorLength(input, maxLength, avxSize));
    }

    @Override
    public int getSupportedVectorConvertLength(Stamp result, Stamp input, int maxLength, FloatConvert op) {
        if (!hasMinimumVectorizationRequirements(maxLength)) {
            return 1;
        }

        // for long to double conversion, the conversion table is a bit too optimistic, so we need
        // to additionally check if the long value fits into the mantissa of a double.
        if (isImpossibleLongToDoubleConversion(result, input)) {
            return 1;
        }

        if (op.getCategory().equals(FloatConvertCategory.FloatingPointToInteger)) {
            ArithmeticOpTable.FloatConvertOp stampChecks = ArithmeticOpTable.forStamp(input).getFloatConvert(op);
            if (stampChecks.inputCanBeNaN(input) || stampChecks.canOverflowInteger(input)) {
                /*
                 * This instruction is not supported yet because we would need fixup code to map
                 * AMD64 semantics to Java semantics (GR-51421).
                 */
                return 1;
            }
        }

        AVXSize avxSize = convertOps.getSupportedAVXSize(op.getCategory(), ((PrimitiveStamp) input).getBits(), ((PrimitiveStamp) result).getBits(), maxLength);
        if (avxSize == null) {
            /* No vectorized conversion found. */
            return 1;
        }
        return Math.min(getSupportedVectorLength(result, maxLength, avxSize), getSupportedVectorLength(input, maxLength, avxSize));
    }

    @Override
    public int getSupportedVectorArithmeticLength(Stamp stamp, int maxLength, ArithmeticOpTable.Op op) {
        if (!hasMinimumVectorizationRequirements(maxLength)) {
            return 1;
        }

        if (stamp instanceof LogicValueStamp) {
            int res = arch.getLargestStorableKind(AMD64.MASK).getSizeInBytes() * Byte.SIZE;
            return Math.min(res, maxLength);
        }

        AVXSize avxSize = arithOps.getSupportedAVXSize(op, ((PrimitiveStamp) stamp).getBits(), maxLength);
        return getSupportedVectorLength(stamp, maxLength, avxSize);
    }

    @Override
    public int getSupportedVectorShiftWithScalarCount(Stamp stamp, int maxLength, ArithmeticOpTable.Op op) {
        if (!hasMinimumVectorizationRequirements(maxLength)) {
            return 1;
        }

        IntegerStamp iStamp = (IntegerStamp) stamp;
        if (iStamp.getBits() == Byte.SIZE) {
            return 1;
        }

        AVXSize result;
        int bits = iStamp.getBits();
        int maxBitLength = maxLength * bits;
        if (op instanceof ArithmeticOpTable.ShiftOp.Shr) {
            result = switch (bits) {
                case Short.SIZE -> arithOps.getSupportedAVXSize(VectorFeatureAssertion.AVX1_AVX2_AVX512BW_VL, maxBitLength);
                case Integer.SIZE -> arithOps.getSupportedAVXSize(VectorFeatureAssertion.AVX1_AVX2_AVX512F_VL, maxBitLength);
                case Long.SIZE -> arithOps.getSupportedAVXSize(VectorFeatureAssertion.AVX512F_VL, maxBitLength);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(stamp);
            };
        } else {
            result = switch (bits) {
                case Short.SIZE -> arithOps.getSupportedAVXSize(VectorFeatureAssertion.AVX1_AVX2_AVX512BW_VL, maxBitLength);
                case Integer.SIZE, Long.SIZE -> arithOps.getSupportedAVXSize(VectorFeatureAssertion.AVX1_AVX2_AVX512F_VL, maxBitLength);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(stamp);
            };
        }
        return getSupportedVectorLength(stamp, maxLength, result);
    }

    @Override
    public boolean narrowedVectorInstructionAvailable(NarrowableArithmeticNode operation, IntegerStamp narrowedStamp) {
        Op op = operation.getArithmeticOp();
        AVXSize avxSize;
        /*
         * Ignore the exact vector length in this high-level check. We only want to know if any
         * narrowed version of the instruction is available, not one for a specific vector size.
         * That will be checked during vector lowering.
         */
        int maxLength = Integer.MAX_VALUE;
        if (op instanceof IntegerConvertOp) {
            avxSize = convertOps.getSupportedAVXSizeOrNull(op, PrimitiveStamp.getBits(((ValueNode) operation).stamp(NodeView.DEFAULT)), PrimitiveStamp.getBits(narrowedStamp), maxLength);
        } else {
            avxSize = arithOps.getSupportedAVXSize(op, PrimitiveStamp.getBits(narrowedStamp), maxLength);
        }
        return avxSize != null;
    }

    @Override
    public boolean narrowedVectorShiftAvailable(ShiftNode<?> op, IntegerStamp narrowedStamp) {
        // byte shifts are unavailable
        // short and int shifts are available on all machine with wider operations
        return narrowedStamp.getBits() != Byte.SIZE;
    }

    @Override
    public int getSupportedVectorConditionalLength(Stamp stamp, int maxLength) {
        if (!hasMinimumVectorizationRequirements(maxLength)) {
            return 1;
        }

        AVXSize avxSize = blendOps.getSupportedAVXSize(stamp, maxLength);
        int stampBits = getVectorStride(stamp) * 8;

        // check for widening conversion support for the logic node encoding the condition
        if (avxSize == AVXSize.YMM || avxSize == AVXSize.ZMM) {
            int targetBits = stampBits;
            int sourceBits = targetBits >> 1;
            while (sourceBits >= 8) {
                AVXSize extendSize = convertOps.getSupportedAVXSize(IntegerStamp.OPS.getSignExtend(), sourceBits, targetBits, maxLength);
                if (avxSize.getBytes() > extendSize.getBytes()) {
                    avxSize = extendSize;
                }
                sourceBits >>= 1;
            }
        }

        // check for widening conversion support for the logic node encoding the condition
        if (avxSize == AVXSize.YMM || avxSize == AVXSize.ZMM) {
            int targetBits = stampBits;
            int sourceBits = targetBits << 1;
            while (sourceBits <= 64) {
                AVXSize narrowSize = convertOps.getSupportedAVXSize(IntegerStamp.OPS.getNarrow(), sourceBits, targetBits, maxLength);
                if (avxSize.getBytes() > narrowSize.getBytes()) {
                    avxSize = narrowSize;
                }
                sourceBits <<= 1;
            }
        }

        avxSize = legalizeBlendSize(avxSize);
        return getSupportedVectorLength(stamp, maxLength, avxSize);
    }

    private AVXSize legalizeBlendSize(AVXSize avxSize) {
        if (avxSize == AVXSize.ZMM) {
            /*
             * Computing the blend mask may involve mask move operations that are only available
             * with BW/DQ/VL. We can't check the condition here, so be conservative.
             */
            EnumSet<CPUFeature> features = arch.getFeatures();
            if (!(features.contains(CPUFeature.AVX512BW) && features.contains(CPUFeature.AVX512DQ) && features.contains(CPUFeature.AVX512VL))) {
                return AVXSize.YMM;
            }
        }
        return avxSize;
    }

    @Override
    public int getSupportedVectorLogicLength(LogicNode logicNode, int maxLength) {
        if (!hasMinimumVectorizationRequirements(maxLength)) {
            return 1;
        }

        int upperBound = maxLength;
        EnumSet<CPUFeature> features = arch.getFeatures();
        if (features.contains(CPUFeature.AVX512F)) {
            if (!(features.contains(CPUFeature.AVX512BW) && features.contains(CPUFeature.AVX512DQ) && features.contains(CPUFeature.AVX512VL))) {
                /*
                 * We don't know how the result of this logic operation will be used. Without a
                 * reasonable AVX512 subset, we might be able to produce this operation's result in
                 * a mask register, but some usage might not be able to use that mask (see also
                 * #legalizeBlendSize). Limit this operation's size to at most YMM so we use
                 * AVX/AVX2 instructions instead.
                 */
                Stamp logicOperationStamp = logicOperationStamp(logicNode);
                if (logicOperationStamp == null) {
                    return 1;
                }
                int elementBytes = getVectorStride(logicOperationStamp);
                upperBound = Math.min(AVXSize.YMM.getBytes() / elementBytes, upperBound);
            }
        }

        return super.getSupportedVectorLogicLengthHelper(logicNode, upperBound);
    }

    /**
     * Returns a stamp describing a representative input of the logic node. The bit width of this
     * stamp describes the width of the logic bitmasks produced by the vectorized version of the
     * logic operation. Returns {@code null} short-circuiting operations that don't produce a simple
     * result.
     */
    private Stamp logicOperationStamp(LogicNode logicNode) {
        /* These cases should remain in sync with getSupportedVectorLogicLengthHelper. */
        if (logicNode instanceof CompareNode compareNode) {
            return compareNode.getX().stamp(NodeView.DEFAULT);
        } else if (logicNode instanceof IsNullNode) {
            return oopMaskStamp;
        } else if (logicNode instanceof ShortCircuitOrNode) {
            return null;
        } else if (logicNode instanceof LogicConstantNode) {
            return logicNode.stamp(NodeView.DEFAULT);
        } else if (logicNode instanceof IntegerTestNode testNode) {
            return testNode.getX().stamp(NodeView.DEFAULT);
        } else {
            throw GraalError.shouldNotReachHere("Unknown class: " + logicNode.getClass()); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public int getSupportedVectorComparisonLength(Stamp stamp, CanonicalCondition condition, int maxLength) {
        return getSupportedVectorComparisonLength(stamp, condition, maxLength, MaySimulateBT.YES);
    }

    private static int maxVectorSizeForArchitecture(AMD64 arch) {
        int maxPhysicalSize = arch.getLargestStorableKind(AMD64.XMM).getSizeInBytes();
        // We only want to use ZMM sized registers if full AVX512 is supported.
        int maxDesiredSize = AMD64BaseAssembler.supportsFullAVX512(arch.getFeatures()) ? AVXSize.ZMM.getBytes() : AVXSize.YMM.getBytes();
        return Math.min(maxPhysicalSize, maxDesiredSize);
    }

    /**
     * To be called only when (re-)configuring the compiler for an SVM runtime compilation. Resets
     * precomputed values stored in this vector architecture instance for the now known runtime
     * target architecture.
     */
    public void updateForRuntimeArchitecture(AMD64 newArch) {
        this.cachedMaxVectorLength = 0;  // force recomputation
        this.maxVectorSize = maxVectorSizeForArchitecture(newArch);
        this.vectorAPITypeTable = null;
    }

    /**
     * AVX 1 and 2 don't have unsigned compares, but we can try to simulate them using other
     * instructions. This enum is used to specify whether to allow this simulation when querying
     * {@link VectorAMD64#getSupportedVectorComparisonLength(Stamp, CanonicalCondition, int, MaySimulateBT)}.
     */
    public enum MaySimulateBT {
        YES,
        NO
    }

    public int getSupportedVectorComparisonLength(Stamp stamp, CanonicalCondition condition, int maxLength, MaySimulateBT maySimulateBT) {
        AVXSize avxSize = compOps.getSupportedAVXSize(stamp, condition, maxLength);
        int supportedLength = getSupportedVectorLength(stamp, maxLength, avxSize);
        if (supportedLength == 1 && condition == CanonicalCondition.BT && maySimulateBT == MaySimulateBT.YES) {
            assert stamp instanceof IntegerStamp : stamp;
            // AVX 1 and 2 don't have unsigned compares, but x |<| y can be simulated using a signed
            // compare as (x ^ signBit) < (y ^ signBit). See if we have the appropriate instructions
            // for that. Vector lowering will have to legalize unsigned compares.
            int signedCompareLength = getSupportedVectorComparisonLength(stamp, CanonicalCondition.LT, maxLength, MaySimulateBT.NO);
            supportedLength = getSupportedVectorArithmeticLength(stamp, signedCompareLength, ArithmeticOpTable.forStamp(stamp).getXor());
        }
        return supportedLength;
    }

    @Override
    public int getSupportedVectorGatherLength(Stamp elementStamp, Stamp offsetStamp, int maxLength) {
        if (!hasMinimumVectorizationRequirements(maxLength)) {
            return 1;
        }

        AVXSize elementSize = gatherOps.getSupportedAVXElementSize(elementStamp, maxLength);
        AVXSize offsetSize = gatherOps.getSupportedAVXOffsetSize(offsetStamp, maxLength);
        return Math.min(getSupportedVectorLength(elementStamp, maxLength, elementSize), getSupportedVectorLength(offsetStamp, maxLength, offsetSize));
    }

    @Override
    public int getSupportedVectorPermuteLength(Stamp elementStamp, int maxLength) {
        if (!hasMinimumVectorizationRequirements(maxLength)) {
            return 1;
        }

        AVXSize avxSize = getMaxSupportedAVXSize(arch.getFeatures());
        if (avxSize == AVXSize.YMM) {
            if (!arch.getFeatures().contains(CPUFeature.AVX2)) {
                avxSize = AVXSize.XMM;
            }
        } else if (avxSize == AVXSize.ZMM) {
            // While using AVX512_VBMI is the preferred option, GeneralSIMDPermute offers a
            // workaround using AVX512_BW
            if (!arch.getFeatures().contains(CPUFeature.AVX512_VBMI) && !arch.getFeatures().contains(CPUFeature.AVX512BW)) {
                avxSize = AVXSize.YMM;
            }
        }
        int lengthToCheck = Integer.highestOneBit(maxLength);
        if (lengthToCheck < maxLength) {
            lengthToCheck <<= 1;
        }

        int supportedLength = getSupportedVectorLength(elementStamp, lengthToCheck, avxSize);
        return Math.min(supportedLength, maxLength);
    }

    @Override
    public int getSupportedVectorBlendLength(Stamp elementStamp, int maxLength) {
        if (!hasMinimumVectorizationRequirements(maxLength)) {
            return 1;
        }

        AVXSize avxSize = blendOps.getSupportedAVXSize(elementStamp, maxLength);
        int lengthToCheck = Integer.highestOneBit(maxLength);
        if (lengthToCheck < maxLength) {
            lengthToCheck <<= 1;
        }

        avxSize = legalizeBlendSize(avxSize);
        int supportedLength = getSupportedVectorLength(elementStamp, lengthToCheck, avxSize);
        return Math.min(supportedLength, maxLength);
    }

    private boolean hasMinimumVectorizationRequirements(int maxLength) {
        // for now, we are not vectorizing anything when no AVX instructions are available
        return enabled && maxLength > 1 && arch.getFeatures().contains(CPUFeature.AVX) && maxVectorSize >= AVXSize.XMM.getBytes();
    }

    @Override
    public boolean supportsFloatingPointConditionalMoves() {
        // AMD64 has no floating-point conditional move instruction, which we need for the tail
        // consumer of vectorized floating-point conditionals. With AVX we can simulate the
        // conditional move with a blend, but without AVX we must avoid trying to vectorize such
        // loops.
        return arch.getFeatures().contains(CPUFeature.AVX);
    }

    /**
     * For computing the maximum number of vector elements that can be handled in one machine code
     * instruction, we need to take the following constraints into account:
     * <ul>
     * <li>which SIMD instruction set is available (SSE* or AVX*)?</li>
     * <li>for each operations that we want to vectorize, what is the broadest SIMD machine code
     * instruction available?</li> *
     * <li>do we have a hardware register for storing a vector of the desired data type and length?
     * </li>
     * <li>what is the maximum number of vector elements that we want to vectorize?</li>
     * </ul>
     */
    private int getSupportedVectorLength(Stamp stamp, int maxLength, AVXSize avxSize) {
        if (stamp instanceof AbstractObjectStamp && !enableObjectVectorization) {
            /* Only handling primitive values. */
            return 1;
        }

        if (avxSize != null && isVectorizable(stamp)) {
            int supportedElements = avxSize.getBytes() / getVectorStride(stamp);
            assert CodeUtil.isPowerOf2(supportedElements) : supportedElements;

            int result = maxLength <= supportedElements ? Integer.highestOneBit(maxLength) : supportedElements;
            while (!hasVectorSupport(stamp, result)) {
                result = result >> 1;
            }

            assert result > 0 : "at least a step length of 1 must always be possible";
            return result;
        }
        return 1;
    }

    private AVXSize getMaxSupportedAVXSize(EnumSet<CPUFeature> features) {
        if (features.contains(CPUFeature.AVX512F) && maxVectorSize >= 64) {
            return AVXSize.ZMM;
        } else if (features.contains(CPUFeature.AVX) && maxVectorSize >= 32) {
            return AVXSize.YMM;
        } else {
            assert features.contains(CPUFeature.SSE2) : features;
            assert maxVectorSize >= 16 : maxVectorSize;
            return AVXSize.XMM;
        }
    }

    private static boolean hasVectorSupport(Stamp stamp, int length) {
        if (length == 2) {
            if (stamp instanceof IntegerStamp && ((IntegerStamp) stamp).getBits() == BYTE_BITS) {
                // no moves to/from XMM registers are available for 2x BYTE
                return false;
            } else if (stamp instanceof FloatStamp && ((FloatStamp) stamp).getBits() == SINGLE_BITS) {
                // no AMD64Kind is available for 2x SINGLE/FLOAT
                return false;
            }
        }
        return true;
    }

    /**
     * Check whether the input fits in an unsigned 52-bit integer.
     */
    public static boolean supportUnsignedLongToDouble(IntegerStamp input) {
        long unsignedMask = (1L << 52) - 1;
        return (input.mayBeSet() & ~unsignedMask) == 0L;
    }

    /**
     * Check whether the input fits in a signed 52-bit integer.
     */
    public static boolean supportSignedLongToDouble(IntegerStamp input) {
        long signedBound = 1L << 51;
        return -signedBound <= input.lowerBound() && input.upperBound() < signedBound;
    }

    private boolean isImpossibleLongToDoubleConversion(Stamp result, Stamp input) {
        boolean isLongToDouble = result instanceof FloatStamp && input instanceof IntegerStamp && PrimitiveStamp.getBits(result) == 64 && PrimitiveStamp.getBits(input) == 64;
        if (!isLongToDouble) {
            return false;
        }
        if (supportsLongToDoubleFloatConvert()) {
            return false;
        }
        return !supportUnsignedLongToDouble((IntegerStamp) input) && !supportSignedLongToDouble((IntegerStamp) input);
    }

    @Override
    public int getSupportedVectorCompressExpandLength(Stamp elementStamp, int maxLength) {
        if (!hasMinimumVectorizationRequirements(maxLength)) {
            return 1;
        }

        AVXSize avxSize = compressExpandOps.getSupportedAVXSize(elementStamp, maxLength);
        int supportedLength = getSupportedVectorLength(elementStamp, maxLength, avxSize);
        return Math.min(supportedLength, maxLength);
    }

    @Override
    public int getObjectAlignment() {
        return objectAlignment;
    }

    @Override
    public boolean supportsLongToDoubleFloatConvert() {
        EnumSet<AMD64.CPUFeature> features = arch.getFeatures();
        return features.contains(AMD64.CPUFeature.AVX512DQ) && features.contains(AMD64.CPUFeature.AVX512VL);
    }

    @Override
    public boolean supportsVectorConcat(int inputSizeInBytes) {
        int maxSupportedAVXBytes = getMaxSupportedAVXSize(arch.getFeatures()).getBytes();
        // XMM into YMM concat or YMM into ZMM concat is supported
        return inputSizeInBytes == 16 && maxSupportedAVXBytes >= 32 || inputSizeInBytes == 32 && maxSupportedAVXBytes >= 64;
    }

    @Override
    public boolean supportsAES() {
        return arch.getFeatures().contains(CPUFeature.AES) && arch.getFeatures().contains(CPUFeature.AVX);
    }

    @Override
    public boolean logicVectorsAreBitmasks() {
        // AVX1 and AVX2 use bitmasks, AVX-512 should use the dedicated mask registers.
        return !AMD64BaseAssembler.supportsFullAVX512(arch.getFeatures());
    }

    private final AMD64VectorConvertInstructionsTable convertOps = new AMD64VectorConvertInstructionsTable(this);

    /*
     * The instruction tables specify on a high level which SIMD instruction set is required for
     * emitting certain SIMD operations on various vector register sizes (XMM/YMM/ZMM).
     *
     * At this level, we don't really care about the particular machine code instruction that is
     * going to be emitted, we just care about (a possible conservative) estimation which feature
     * set(s) is going to be used by which operation.
     */

    private static final class AMD64VectorConvertInstructionsMap extends AMD64VectorInstructionsMap<VectorConvertOperation> {

        private static final VectorConvertOperation[] INTEGER_EXTENDS = {
                        op(BYTE_BITS, WORD_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512BW_VL),
                        op(BYTE_BITS, DWORD_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512F_VL),
                        op(BYTE_BITS, QWORD_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512F_VL),
                        op(WORD_BITS, DWORD_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512F_VL),
                        op(WORD_BITS, QWORD_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512F_VL),
                        op(DWORD_BITS, QWORD_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512F_VL),
        };

        @SuppressWarnings("unchecked")
        AMD64VectorConvertInstructionsMap() {
            super(
                            // integer conversions
                            entry(IntegerStamp.OPS.getZeroExtend(),
                                            INTEGER_EXTENDS),

                            entry(IntegerStamp.OPS.getSignExtend(),
                                            INTEGER_EXTENDS),

                            entry(IntegerStamp.OPS.getNarrow(),
                                            op(WORD_BITS, BYTE_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512BW_VL),
                                            op(DWORD_BITS, BYTE_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512BW_VL),
                                            op(DWORD_BITS, WORD_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512BW_VL),
                                            op(QWORD_BITS, BYTE_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512BW_VL),
                                            op(QWORD_BITS, WORD_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512BW_VL),
                                            op(QWORD_BITS, DWORD_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512F_VL)),

                            // floating point conversions

                            entry(FloatConvertCategory.IntegerToFloatingPoint,
                                            op(DWORD_BITS, SINGLE_BITS, VectorFeatureAssertion.AVX1_AVX512F_VL),
                                            op(QWORD_BITS, SINGLE_BITS, VectorFeatureAssertion.AVX512DQ_VL),
                                            op(DWORD_BITS, DOUBLE_BITS, VectorFeatureAssertion.AVX1_AVX512F_VL),
                                            // QWORD to DOUBLE conversion is not supported in
                                            // AVX/AVX2/SSE2 but it is emulated during lowering (see
                                            // AMD64VectorLoweringPhase)
                                            op(QWORD_BITS, DOUBLE_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512DQ_VL)),

                            /*
                             * The instructions in this category don't match Java semantics. At the
                             * moment they can only be used when we know that input is not NaN and
                             * will not overflow the result. As in the scalar case, we will want to
                             * emit the required fixup code for these special cases (GR-51421).
                             */
                            entry(FloatConvertCategory.FloatingPointToInteger,
                                            op(SINGLE_BITS, DWORD_BITS, VectorFeatureAssertion.AVX1_AVX512F_VL),
                                            op(SINGLE_BITS, QWORD_BITS, VectorFeatureAssertion.AVX512DQ_VL),
                                            op(DOUBLE_BITS, DWORD_BITS, VectorFeatureAssertion.AVX1_AVX512F_VL),
                                            op(DOUBLE_BITS, QWORD_BITS, VectorFeatureAssertion.AVX512DQ_VL)),

                            entry(FloatConvertCategory.FloatingPointToFloatingPoint,
                                            op(SINGLE_BITS, DOUBLE_BITS, VectorFeatureAssertion.AVX1_AVX512F_VL),
                                            op(DOUBLE_BITS, SINGLE_BITS, VectorFeatureAssertion.AVX1_AVX512F_VL)));
        }

        protected static VectorOpEntry<VectorConvertOperation> entry(Object op, VectorConvertOperation... operations) {
            return new VectorOpEntry<>(op, operations);
        }

        private static VectorConvertOperation op(int fromBits, int toBits, VectorFeatureAssertion requiredFeatures) {
            return new VectorConvertOperation(fromBits, toBits, requiredFeatures);
        }
    }

    private static final class AMD64VectorConvertInstructionsTable extends AMD64VectorInstructionsTable<VectorConvertOperation> {

        private static final AMD64VectorConvertInstructionsMap CONVERT_INSTRUCTIONS_MAP = new AMD64VectorConvertInstructionsMap();

        private AMD64VectorConvertInstructionsTable(VectorAMD64 vectorAMD64) {
            super(vectorAMD64, CONVERT_INSTRUCTIONS_MAP);
            assert validateEntries();
        }

        public AVXSize getSupportedAVXSize(FloatConvertCategory op, int fromBits, int toBits, int maxLength) {
            return getInternalSupportedAVXSize(op, fromBits, toBits, maxLength, true);
        }

        public AVXSize getSupportedAVXSize(Op op, int fromBits, int toBits, int maxLength) {
            return getInternalSupportedAVXSize(op, fromBits, toBits, maxLength, false);
        }

        public AVXSize getSupportedAVXSizeOrNull(Op op, int fromBits, int toBits, int maxLength) {
            return getInternalSupportedAVXSize(op, fromBits, toBits, maxLength, true);
        }

        private AVXSize getInternalSupportedAVXSize(Object op, int fromBits, int toBits, int maxLength, boolean nullIfNotFound) {
            assert op != null;

            VectorConvertOperation[] vectorOps = map.table.get(op);
            if (vectorOps == null) {
                if (nullIfNotFound) {
                    return null;
                } else {
                    throw GraalError.shouldNotReachHere("missing entry: " + op); // ExcludeFromJacocoGeneratedReport
                }
            }

            int maxVectorBytes = maxLength * (Math.max(fromBits, toBits) / 8);
            for (VectorConvertOperation vectorOp : vectorOps) {
                if (fromBits == vectorOp.fromBits && toBits == vectorOp.toBits) {
                    return getSupportedAVXSize(vectorOp.requiredFeatures, maxVectorBytes);
                }
            }
            if (nullIfNotFound) {
                return null;
            } else {
                throw GraalError.shouldNotReachHere("table does not specify conversion from " + fromBits + " to " + toBits + " bits for operation " + op); // ExcludeFromJacocoGeneratedReport
            }
        }

        private boolean validateEntries() {
            Set<Object> cursor = map.table.keySet();
            for (Object key : cursor) {
                VectorConvertOperation[] operations = map.table.get(key);
                for (int i = 0; i < operations.length; i++) {
                    // check for duplicated entries
                    int fromBits = operations[i].fromBits;
                    int toBits = operations[i].toBits;
                    assert CodeUtil.isPowerOf2(fromBits) : fromBits;
                    assert CodeUtil.isPowerOf2(toBits) : toBits;
                    for (int j = i + 1; j < operations.length; j++) {
                        int otherFromBits = operations[j].fromBits;
                        int otherToBits = operations[j].toBits;
                        assert fromBits != otherFromBits || toBits != otherToBits : "found multiple entries that convert from " + fromBits + " to " + toBits + " bits: " + key;
                    }
                }
            }
            return true;
        }
    }

    private static class VectorConvertOperation {
        private final int fromBits;
        private final int toBits;

        private final VectorFeatureAssertion requiredFeatures;

        VectorConvertOperation(int fromBits, int toBits, VectorFeatureAssertion requiredFeatures) {
            this.fromBits = fromBits;
            this.toBits = toBits;
            this.requiredFeatures = requiredFeatures;
        }
    }

    private final AMD64SupportedArithmeticVectorInstructionsTable arithOps = new AMD64SupportedArithmeticVectorInstructionsTable(this);

    private static final class AMD64VectorArithmeticInstructionsMap extends AMD64SimpleVectorInstructionsTable.AMD64SimpleVectorInstructionsMap {

        private static final VectorSimpleOperation[] REGULAR_INTEGER_ARITHMETIC = {
                        op(BYTE_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512BW_VL),
                        op(WORD_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512BW_VL),
                        op(DWORD_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512F_VL),
                        op(QWORD_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512F_VL)
        };

        private static final VectorSimpleOperation[] REGULAR_INTEGER_BITWISE = {
                        op(BYTE_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512F_VL),
                        op(WORD_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512F_VL),
                        op(DWORD_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512F_VL),
                        op(QWORD_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512F_VL)
        };

        private static final VectorSimpleOperation[] REGULAR_INTEGER_MINMAX = {
                        op(BYTE_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512BW_VL),
                        op(WORD_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512BW_VL),
                        op(DWORD_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512F_VL),
                        op(QWORD_BITS, VectorFeatureAssertion.AVX512F_VL),
        };

        private static final VectorSimpleOperation[] REGULAR_FLOAT_ARITHMETIC = {
                        op(SINGLE_BITS, VectorFeatureAssertion.AVX1_AVX512F_VL),
                        op(DOUBLE_BITS, VectorFeatureAssertion.AVX1_AVX512F_VL)
        };

        private static final VectorSimpleOperation[] REGULAR_FLOAT_BITWISE_MINMAX = {
                        op(SINGLE_BITS, VectorFeatureAssertion.AVX1_AVX512DQ_VL),
                        op(DOUBLE_BITS, VectorFeatureAssertion.AVX1_AVX512DQ_VL)
        };

        private static final VectorSimpleOperation[] REGULAR_MASK = {
                        op(BYTE_BITS, VectorFeatureAssertion.AVX512DQ_VL),
                        op(WORD_BITS, VectorFeatureAssertion.AVX512F_VL),
                        op(DWORD_BITS, VectorFeatureAssertion.AVX512BW_VL),
                        op(QWORD_BITS, VectorFeatureAssertion.AVX512BW_VL)
        };

        @SuppressWarnings("unchecked")
        AMD64VectorArithmeticInstructionsMap() {
            super(
                            // integer arithmetics
                            entry(IntegerStamp.OPS.getNeg(),
                                            /*
                                             * On AMD64 we always lower Neg(x) to 0 - x, so match
                                             * the table for Sub.
                                             */
                                            REGULAR_INTEGER_ARITHMETIC),

                            entry(IntegerStamp.OPS.getAdd(),
                                            REGULAR_INTEGER_ARITHMETIC),

                            entry(IntegerStamp.OPS.getSub(),
                                            REGULAR_INTEGER_ARITHMETIC),

                            entry(IntegerStamp.OPS.getMul(),
                                            op(BYTE_BITS, null),
                                            op(WORD_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512BW_VL),
                                            op(DWORD_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512F_VL),
                                            op(QWORD_BITS, VectorFeatureAssertion.AVX512DQ_VL)),

                            entry(IntegerStamp.OPS.getMulHigh(),
                                            op(BYTE_BITS, null),
                                            op(WORD_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512BW_VL),
                                            op(DWORD_BITS, null),
                                            op(QWORD_BITS, null)),

                            entry(IntegerStamp.OPS.getUMulHigh(),
                                            op(BYTE_BITS, null),
                                            op(WORD_BITS, VectorFeatureAssertion.AVX1_AVX2_AVX512BW_VL),
                                            op(DWORD_BITS, null),
                                            op(QWORD_BITS, null)),

                            entry(IntegerStamp.OPS.getDiv(),
                                            op(BYTE_BITS, null),
                                            op(WORD_BITS, null),
                                            op(DWORD_BITS, null),
                                            op(QWORD_BITS, null)),

                            entry(IntegerStamp.OPS.getRem(),
                                            op(BYTE_BITS, null),
                                            op(WORD_BITS, null),
                                            op(DWORD_BITS, null),
                                            op(QWORD_BITS, null)),

                            entry(IntegerStamp.OPS.getNot(),
                                            REGULAR_INTEGER_BITWISE),

                            entry(IntegerStamp.OPS.getAnd(),
                                            REGULAR_INTEGER_BITWISE),

                            entry(IntegerStamp.OPS.getOr(),
                                            REGULAR_INTEGER_BITWISE),

                            entry(IntegerStamp.OPS.getXor(),
                                            REGULAR_INTEGER_BITWISE),

                            // These are general shifts of 2 vectors (e,g, vpsllvd), shifts where
                            // the shift amount is a scalar (e.g. vpslld) is handled separately
                            entry(IntegerStamp.OPS.getShl(),
                                            op(BYTE_BITS, null),
                                            op(WORD_BITS, VectorFeatureAssertion.AVX512BW_VL),
                                            op(DWORD_BITS, VectorFeatureAssertion.AVX2_AVX512F_VL),
                                            op(QWORD_BITS, VectorFeatureAssertion.AVX2_AVX512F_VL)),

                            entry(IntegerStamp.OPS.getShr(),
                                            op(BYTE_BITS, null),
                                            op(WORD_BITS, VectorFeatureAssertion.AVX512BW_VL),
                                            op(DWORD_BITS, VectorFeatureAssertion.AVX2_AVX512F_VL),
                                            op(QWORD_BITS, VectorFeatureAssertion.AVX512F_VL)),

                            entry(IntegerStamp.OPS.getUShr(),
                                            op(BYTE_BITS, null),
                                            op(WORD_BITS, VectorFeatureAssertion.AVX512BW_VL),
                                            op(DWORD_BITS, VectorFeatureAssertion.AVX2_AVX512F_VL),
                                            op(QWORD_BITS, VectorFeatureAssertion.AVX2_AVX512F_VL)),

                            entry(IntegerStamp.OPS.getAbs(),
                                            REGULAR_INTEGER_MINMAX),

                            entry(IntegerStamp.OPS.getMax(),
                                            REGULAR_INTEGER_MINMAX),

                            entry(IntegerStamp.OPS.getMin(),
                                            REGULAR_INTEGER_MINMAX),

                            entry(IntegerStamp.OPS.getUMax(),
                                            REGULAR_INTEGER_MINMAX),

                            entry(IntegerStamp.OPS.getUMin(),
                                            REGULAR_INTEGER_MINMAX),

                            entry(IntegerStamp.OPS.getCompress(),
                                            op(BYTE_BITS, null),
                                            op(WORD_BITS, null),
                                            op(DWORD_BITS, null),
                                            op(QWORD_BITS, null)),

                            entry(IntegerStamp.OPS.getExpand(),
                                            op(BYTE_BITS, null),
                                            op(WORD_BITS, null),
                                            op(DWORD_BITS, null),
                                            op(QWORD_BITS, null)),

                            // floating point arithmetics
                            entry(FloatStamp.OPS.getNeg(),
                                            REGULAR_FLOAT_BITWISE_MINMAX),

                            entry(FloatStamp.OPS.getAdd(),
                                            REGULAR_FLOAT_ARITHMETIC),

                            entry(FloatStamp.OPS.getSub(),
                                            REGULAR_FLOAT_ARITHMETIC),

                            entry(FloatStamp.OPS.getMul(),
                                            REGULAR_FLOAT_ARITHMETIC),

                            entry(FloatStamp.OPS.getDiv(),
                                            REGULAR_FLOAT_ARITHMETIC),

                            entry(FloatStamp.OPS.getRem(),
                                            op(SINGLE_BITS, null),
                                            op(DOUBLE_BITS, null)),

                            entry(FloatStamp.OPS.getNot(),
                                            REGULAR_FLOAT_ARITHMETIC),

                            entry(FloatStamp.OPS.getAnd(),
                                            REGULAR_FLOAT_BITWISE_MINMAX),

                            entry(FloatStamp.OPS.getOr(),
                                            REGULAR_FLOAT_BITWISE_MINMAX),

                            entry(FloatStamp.OPS.getXor(),
                                            REGULAR_FLOAT_BITWISE_MINMAX),

                            entry(FloatStamp.OPS.getAbs(),
                                            REGULAR_FLOAT_BITWISE_MINMAX),

                            entry(FloatStamp.OPS.getSqrt(),
                                            REGULAR_FLOAT_ARITHMETIC),

                            entry(FloatStamp.OPS.getMax(),
                                            REGULAR_FLOAT_BITWISE_MINMAX),

                            entry(FloatStamp.OPS.getMin(),
                                            REGULAR_FLOAT_BITWISE_MINMAX),

                            entry(FloatStamp.OPS.getFMA(),
                                            op(SINGLE_BITS, VectorFeatureAssertion.FMA),
                                            op(DOUBLE_BITS, VectorFeatureAssertion.FMA)),

                            entry(SimdStamp.OPMASK_OPS.getNot(),
                                            REGULAR_MASK),

                            entry(SimdStamp.OPMASK_OPS.getAnd(),
                                            REGULAR_MASK),

                            entry(SimdStamp.OPMASK_OPS.getOr(),
                                            REGULAR_MASK),

                            entry(SimdStamp.OPMASK_OPS.getXor(),
                                            REGULAR_MASK),

                            entry(SimdStamp.OPMASK_OPS.getReinterpret(),
                                            REGULAR_MASK));
        }
    }

    private static final class AMD64SupportedArithmeticVectorInstructionsTable extends AMD64SimpleVectorInstructionsTable {

        private static final AMD64VectorArithmeticInstructionsMap ARITHMETIC_INSTRUCTIONS_MAP = new AMD64VectorArithmeticInstructionsMap();

        private AMD64SupportedArithmeticVectorInstructionsTable(VectorAMD64 vectorAMD64) {
            super(vectorAMD64, ARITHMETIC_INSTRUCTIONS_MAP);
        }

        public AVXSize getSupportedAVXSize(Op op, int bits, int maxLength) {
            return getEntry(op, bits, maxLength);
        }
    }

    private final AMD64SupportedVectorComparisonInstructionsTable compOps = new AMD64SupportedVectorComparisonInstructionsTable(this);

    private static final class AMD64VectorComparisonInstructionsMap extends AMD64SimpleVectorInstructionsTable.AMD64SimpleVectorInstructionsMap {
        @SuppressWarnings("unchecked")
        AMD64VectorComparisonInstructionsMap() {
            super(
                            // integer comparisons
                            entry(new AMD64SupportedVectorComparisonInstructionsTable.VectorComparisonInstructionTableKey(IntegerStamp.class, CanonicalCondition.EQ),
                                            AMD64VectorArithmeticInstructionsMap.REGULAR_INTEGER_ARITHMETIC),

                            entry(new AMD64SupportedVectorComparisonInstructionsTable.VectorComparisonInstructionTableKey(IntegerStamp.class, CanonicalCondition.LT),
                                            AMD64VectorArithmeticInstructionsMap.REGULAR_INTEGER_ARITHMETIC),

                            entry(new AMD64SupportedVectorComparisonInstructionsTable.VectorComparisonInstructionTableKey(IntegerStamp.class, CanonicalCondition.BT),
                                            op(BYTE_BITS, VectorFeatureAssertion.AVX512BW_VL),
                                            op(WORD_BITS, VectorFeatureAssertion.AVX512BW_VL),
                                            op(DWORD_BITS, VectorFeatureAssertion.AVX512F_VL),
                                            op(QWORD_BITS, VectorFeatureAssertion.AVX512F_VL)),

                            // floating point comparisons
                            entry(new AMD64SupportedVectorComparisonInstructionsTable.VectorComparisonInstructionTableKey(FloatStamp.class, CanonicalCondition.EQ),
                                            AMD64VectorArithmeticInstructionsMap.REGULAR_FLOAT_ARITHMETIC),

                            entry(new AMD64SupportedVectorComparisonInstructionsTable.VectorComparisonInstructionTableKey(FloatStamp.class, CanonicalCondition.LT),
                                            AMD64VectorArithmeticInstructionsMap.REGULAR_FLOAT_ARITHMETIC));
        }
    }

    private static final class AMD64SupportedVectorComparisonInstructionsTable extends AMD64SimpleVectorInstructionsTable {

        private static final AMD64VectorComparisonInstructionsMap COMPARISON_INSTRUCTIONS_MAP = new AMD64VectorComparisonInstructionsMap();

        private AMD64SupportedVectorComparisonInstructionsTable(VectorAMD64 vectorAMD64) {
            super(vectorAMD64, COMPARISON_INSTRUCTIONS_MAP);
        }

        public AVXSize getSupportedAVXSize(Stamp stamp, CanonicalCondition condition, int maxLength) {
            if (stamp instanceof AbstractObjectStamp) {
                assert condition == CanonicalCondition.EQ : condition;
                // For purposes of pointer comparisons (equality checks for null or other pointers),
                // compare as integers of the appropriate size.
                return getEntry(new VectorComparisonInstructionTableKey(IntegerStamp.class, condition), oopBits((AbstractObjectStamp) stamp), maxLength);
            }
            return getEntry(new VectorComparisonInstructionTableKey(stamp.getClass(), condition), PrimitiveStamp.getBits(stamp), maxLength);
        }

        private static class VectorComparisonInstructionTableKey {
            private Class<?> clazz;
            private CanonicalCondition condition;

            VectorComparisonInstructionTableKey(Class<?> clazz, CanonicalCondition condition) {
                this.clazz = clazz;
                this.condition = condition;
            }

            @Override
            public int hashCode() {
                final int prime = 31;
                int result = clazz.hashCode();
                result = prime * result + condition.hashCode();
                return result;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                } else if (obj instanceof VectorComparisonInstructionTableKey) {
                    VectorComparisonInstructionTableKey other = (VectorComparisonInstructionTableKey) obj;
                    return this.clazz.equals(other.clazz) && this.condition.equals(other.condition);
                }
                return false;
            }
        }
    }

    private final AMD64SupportedGatherInstructionsTable gatherOps = new AMD64SupportedGatherInstructionsTable(this);

    private static final class AMD64VectorGatherInstructionsMap extends AMD64SimpleVectorInstructionsTable.AMD64SimpleVectorInstructionsMap {
        @SuppressWarnings("unchecked")
        AMD64VectorGatherInstructionsMap() {
            super(
                            // integer elements or offsets
                            entry(IntegerStamp.class,
                                            op(BYTE_BITS, null),
                                            op(WORD_BITS, null),
                                            op(DWORD_BITS, VectorFeatureAssertion.AVX2_AVX512F_VL),
                                            op(QWORD_BITS, VectorFeatureAssertion.AVX2_AVX512F_VL)),

                            // floating point elements
                            entry(FloatStamp.class,
                                            op(SINGLE_BITS, VectorFeatureAssertion.AVX2_AVX512F_VL),
                                            op(DOUBLE_BITS, VectorFeatureAssertion.AVX2_AVX512F_VL)));
        }
    }

    private static final class AMD64SupportedGatherInstructionsTable extends AMD64SimpleVectorInstructionsTable {

        private static final AMD64VectorGatherInstructionsMap GATHER_INSTRUCTIONS_MAP = new AMD64VectorGatherInstructionsMap();

        private AMD64SupportedGatherInstructionsTable(VectorAMD64 vectorAMD64) {
            super(vectorAMD64, GATHER_INSTRUCTIONS_MAP);
        }

        public AVXSize getSupportedAVXElementSize(Stamp stamp, int maxLength) {
            if (stamp instanceof AbstractObjectStamp) {
                // For copying from memory, treat pointers like integers of the corresponding size.
                return getEntry(IntegerStamp.class, oopBits((AbstractObjectStamp) stamp), maxLength);
            }
            return getEntry(stamp.getClass(), PrimitiveStamp.getBits(stamp), maxLength);
        }

        public AVXSize getSupportedAVXOffsetSize(Stamp stamp, int maxLength) {
            if (!(stamp instanceof IntegerStamp)) {
                GraalError.shouldNotReachHere("gather offset stamp must be an integer stamp, got: " + stamp); // ExcludeFromJacocoGeneratedReport
            }
            return getEntry(stamp.getClass(), PrimitiveStamp.getBits(stamp), maxLength);
        }
    }

    private final AMD64SupportedBlendVectorInstructionsTable blendOps = new AMD64SupportedBlendVectorInstructionsTable(this);

    private static final class AMD64VectorBlendInstructionsMap extends AMD64SimpleVectorInstructionsTable.AMD64SimpleVectorInstructionsMap {
        @SuppressWarnings("unchecked")
        AMD64VectorBlendInstructionsMap() {
            super(
                            // integer blends
                            entry(IntegerStamp.class,
                                            AMD64VectorArithmeticInstructionsMap.REGULAR_INTEGER_ARITHMETIC),

                            // floating point blends
                            entry(FloatStamp.class,
                                            AMD64VectorArithmeticInstructionsMap.REGULAR_FLOAT_ARITHMETIC));
        }
    }

    private static final class AMD64SupportedBlendVectorInstructionsTable extends AMD64SimpleVectorInstructionsTable {

        private static final AMD64VectorBlendInstructionsMap BLEND_INSTRUCTIONS_MAP = new AMD64VectorBlendInstructionsMap();

        private AMD64SupportedBlendVectorInstructionsTable(VectorAMD64 vectorAMD64) {
            super(vectorAMD64, BLEND_INSTRUCTIONS_MAP);
        }

        public AVXSize getSupportedAVXSize(Stamp stamp, int maxLength) {
            if (stamp instanceof AbstractObjectStamp) {
                // For blends, treat pointers like integers of the appropriate size.
                return getEntry(IntegerStamp.class, oopBits((AbstractObjectStamp) stamp), maxLength);
            }
            return getEntry(stamp.getClass(), PrimitiveStamp.getBits(stamp), maxLength);
        }
    }

    private final AMD64SupportedCompressExpandVectorInstructionsTable compressExpandOps = new AMD64SupportedCompressExpandVectorInstructionsTable(this);

    private static final class AMD64VectorCompressExpandInstructionsMap extends AMD64SimpleVectorInstructionsTable.AMD64SimpleVectorInstructionsMap {
        @SuppressWarnings("unchecked")
        AMD64VectorCompressExpandInstructionsMap() {
            super(
                            entry(IntegerStamp.class,
                                            op(BYTE_BITS, VectorFeatureAssertion.AVX512_VBMI2_VL),
                                            op(WORD_BITS, VectorFeatureAssertion.AVX512_VBMI2_VL),
                                            op(DWORD_BITS, VectorFeatureAssertion.AVX512F_VL),
                                            op(QWORD_BITS, VectorFeatureAssertion.AVX512F_VL)),

                            entry(FloatStamp.class,
                                            op(SINGLE_BITS, VectorFeatureAssertion.AVX512F_VL),
                                            op(DOUBLE_BITS, VectorFeatureAssertion.AVX512F_VL)));
        }
    }

    private static final class AMD64SupportedCompressExpandVectorInstructionsTable extends AMD64SimpleVectorInstructionsTable {

        private static final AMD64VectorCompressExpandInstructionsMap COMPRESS_EXPAND_INSTRUCTIONS_MAP = new AMD64VectorCompressExpandInstructionsMap();

        private AMD64SupportedCompressExpandVectorInstructionsTable(VectorAMD64 vectorAMD64) {
            super(vectorAMD64, COMPRESS_EXPAND_INSTRUCTIONS_MAP);
        }

        public AVXSize getSupportedAVXSize(Stamp stamp, int maxLength) {
            if (stamp instanceof AbstractObjectStamp) {
                // For compress/expand, treat pointers like integers of the appropriate size.
                return getEntry(IntegerStamp.class, oopBits((AbstractObjectStamp) stamp), maxLength);
            }
            return getEntry(stamp.getClass(), PrimitiveStamp.getBits(stamp), maxLength);
        }
    }

    private static class VectorSimpleOperation {
        private final int bits;
        protected final VectorFeatureAssertion requiredFeatures;

        VectorSimpleOperation(int bits, VectorFeatureAssertion requiredFeatures) {
            this.bits = bits;
            this.requiredFeatures = requiredFeatures;
        }
    }

    private abstract static class AMD64SimpleVectorInstructionsTable extends AMD64VectorInstructionsTable<VectorSimpleOperation> {

        protected abstract static class AMD64SimpleVectorInstructionsMap extends AMD64VectorInstructionsMap<VectorSimpleOperation> {
            @SuppressWarnings("unchecked")
            AMD64SimpleVectorInstructionsMap(VectorOpEntry<VectorSimpleOperation>... entries) {
                super(entries);
            }

            protected static VectorOpEntry<VectorSimpleOperation> entry(Object op, VectorSimpleOperation... operations) {
                return new VectorOpEntry<>(op, operations);
            }

            protected static VectorSimpleOperation op(int bits, VectorFeatureAssertion requiredFeatures) {
                return new VectorSimpleOperation(bits, requiredFeatures);
            }
        }

        private AMD64SimpleVectorInstructionsTable(VectorAMD64 vectorAMD64, AMD64SimpleVectorInstructionsMap map) {
            super(vectorAMD64, map);
            assert validateEntries();
        }

        protected AVXSize getEntry(Object key, int bits, int maxVectorLength) {
            assert key != null;

            VectorSimpleOperation[] vectorOps = map.table.get(key);
            if (vectorOps == null) {
                throw GraalError.shouldNotReachHere("missing entry: " + key); // ExcludeFromJacocoGeneratedReport
            }

            int elementBytes = bits / 8;
            // Avoid overflow in the multiplication below.
            int maxLength = Math.min(maxVectorLength, Integer.MAX_VALUE / elementBytes);
            int maxVectorBytes = maxLength * elementBytes;
            for (VectorSimpleOperation vectorOp : vectorOps) {
                if (bits == vectorOp.bits) {
                    return getSupportedAVXSize(vectorOp.requiredFeatures, maxVectorBytes);
                }
            }
            throw GraalError.shouldNotReachHere("table does not specify bit width " + bits + " for " + key); // ExcludeFromJacocoGeneratedReport
        }

        private boolean validateEntries() {
            Set<Object> cursor = map.table.keySet();
            for (Object key : cursor) {
                VectorSimpleOperation[] operations = map.table.get(key);
                for (int i = 0; i < operations.length; i++) {
                    int bits = operations[i].bits;
                    assert CodeUtil.isPowerOf2(bits) : bits;
                    for (int j = i + 1; j < operations.length; j++) {
                        int otherBits = operations[j].bits;
                        assert bits != otherBits : "found multiple entries for " + bits + "bits: " + key;
                    }
                }
            }
            return true;
        }
    }

    private abstract static class AMD64VectorInstructionsTable<T> {

        private final VectorAMD64 vectorAMD64;
        protected final AMD64VectorInstructionsMap<T> map;

        private AMD64VectorInstructionsTable(VectorAMD64 vectorAMD64, AMD64VectorInstructionsMap<T> map) {
            this.vectorAMD64 = vectorAMD64;
            this.map = map;
        }

        /**
         * Returns the largest supported AVX vector size given the CPU feature and vector length
         * constraints. The returned vector size is bounded by the architecture's max vector length,
         * expressed in bytes. If the returned vector size is larger than {@link AVXSize#XMM}, it is
         * also bounded by the {@code maxVectorBytes} parameter. That is, this method can return
         * {@link AVXSize#XMM} for operations that only want to use part of an XMM register.
         */
        protected AVXSize getSupportedAVXSize(VectorFeatureAssertion requiredFeatures, int maxVectorBytes) {
            if (requiredFeatures == null) {
                /* An explicit null entry in a table means "no such instruction is available". */
                return null;
            }
            EnumSet<CPUFeature> availableFeatures = vectorAMD64.arch.getFeatures();
            if (Math.min(vectorAMD64.maxVectorSize, maxVectorBytes) >= 64 && requiredFeatures.supports(availableFeatures, AVXSize.ZMM)) {
                return AVXSize.ZMM;
            } else if (Math.min(vectorAMD64.maxVectorSize, maxVectorBytes) >= 32 && requiredFeatures.supports(availableFeatures, AVXSize.YMM)) {
                return AVXSize.YMM;
            } else if (vectorAMD64.maxVectorSize >= 16 && requiredFeatures.supports(availableFeatures, AVXSize.XMM)) {
                return AVXSize.XMM;
            } else {
                return null;
            }
        }

        protected int oopBits(AbstractObjectStamp objectStamp) {
            return vectorAMD64.getVectorStride(objectStamp) * Byte.SIZE;
        }
    }

    private abstract static class AMD64VectorInstructionsMap<T> {

        protected final Map<Object, T[]> table;

        @SuppressWarnings("unchecked")
        private AMD64VectorInstructionsMap(VectorOpEntry<T>... entries) {
            table = CollectionsUtil.mapOfEntries(entries);
        }

        static class VectorOpEntry<T> implements Map.Entry<Object, T[]> {
            private final Object op;
            private final T[] operations;

            VectorOpEntry(Object key, T[] operations) {
                this.op = key;
                this.operations = operations;

                assert op != null;
                assert NumUtil.assertNonNegativeInt(operations.length);
            }

            @Override
            public Object getKey() {
                return op;
            }

            @Override
            public T[] getValue() {
                return operations;
            }

            @Override
            public T[] setValue(T[] value) {
                throw new UnsupportedOperationException("vector instruction tables are immutable");
            }
        }
    }

    @Override
    public int guessMaxVectorAPIVectorLength(Stamp elementStamp) {
        int elementBytes = PrimitiveStamp.getBits(elementStamp) / 8;
        int maxVectorBytes = getMaxVectorLength(elementStamp) * elementBytes;
        /*
         * The Vector API restricts vector sizes according to the AVX version in use. For example,
         * even with AVX available and MaxVectorSize >= 32 it will only use XMM-sized registers
         * because AVX provides some, but not all of its operations on YMM sizes.
         */
        int maxBytesFromFeatures;
        if (arch.getFeatures().contains(CPUFeature.AVX512F)) {
            maxBytesFromFeatures = AVXSize.ZMM.getBytes();
        } else if (arch.getFeatures().contains(CPUFeature.AVX2)) {
            maxBytesFromFeatures = AVXSize.YMM.getBytes();
        } else if (arch.getFeatures().contains(CPUFeature.AVX)) {
            maxBytesFromFeatures = AVXSize.XMM.getBytes();
        } else {
            maxBytesFromFeatures = elementBytes;
        }
        return Math.min(maxVectorBytes, maxBytesFromFeatures);
    }

    @Override
    public int getSupportedSimdMaskLogicLength(Stamp elementStamp, int maxLength) {
        int elementBytes = getVectorStride(elementStamp);
        /*
         * With AVX512BW we can use VPMOVB2M on ZMM registers. Otherwise, we must use VPMOVMSKB and
         * are limited to YMM or XMM.
         */
        if (arch.getFeatures().contains(CPUFeature.AVX512BW)) {
            return Math.min(AVXSize.ZMM.getBytes() / elementBytes, maxLength);
        } else if (arch.getFeatures().contains(CPUFeature.AVX2)) {
            return Math.min(AVXSize.YMM.getBytes() / elementBytes, maxLength);
        } else if (arch.getFeatures().contains(CPUFeature.AVX)) {
            return Math.min(AVXSize.XMM.getBytes() / elementBytes, maxLength);
        } else {
            return 1;
        }
    }
}
