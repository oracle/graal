/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.architecture.aarch64;

import static jdk.vm.ci.aarch64.AArch64Kind.BYTE;
import static jdk.vm.ci.aarch64.AArch64Kind.DOUBLE;
import static jdk.vm.ci.aarch64.AArch64Kind.DWORD;
import static jdk.vm.ci.aarch64.AArch64Kind.QWORD;
import static jdk.vm.ci.aarch64.AArch64Kind.SINGLE;
import static jdk.vm.ci.aarch64.AArch64Kind.WORD;

import java.util.Objects;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.asm.aarch64.ASIMDKind;
import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.Op;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.calc.NarrowableArithmeticNode;
import jdk.graal.compiler.nodes.calc.ShiftNode;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64.CPUFeature;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.CodeUtil;

public final class VectorAArch64 extends VectorArchitecture {

    /* Temporary hooks to gradually enable pieces of AArch64 vectorization. */
    public static boolean enableVectorization = true;
    public static boolean enableMoveOps = true;
    public static boolean enableArithmeticOps = true;
    public static boolean enableConvertOps = true;
    public static boolean enableComparisonOps = true;
    public static boolean enableLogicOps = true;
    public static boolean enableConditionalOps = true;
    public static boolean enablePermuteOps = true;
    public static boolean enableBlendOps = true;
    public static boolean enableSIMDOps = true;
    public static boolean enableObjectVectorization = false;

    /* (Byte) size of NEON registers. */
    private static final int NEON_BYTE_WIDTH = 16;

    private final AArch64 arch;
    private final boolean enabled;
    private final int objectAlignment;

    /* Maximum vector size in bytes. */
    private final int maxVectorByteSize;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        VectorAArch64 that = (VectorAArch64) o;
        return enabled == that.enabled && objectAlignment == that.objectAlignment && maxVectorByteSize == that.maxVectorByteSize && Objects.equals(arch, that.arch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), arch, enabled, objectAlignment, maxVectorByteSize);
    }

    public VectorAArch64(AArch64 arch, boolean enabled, int oopVectorStride, boolean useCompressedOops, int objectAlignment) {
        this(arch, enabled, oopVectorStride, useCompressedOops, objectAlignment, NEON_BYTE_WIDTH);
    }

    public VectorAArch64(AArch64 arch, int oopVectorStride, boolean useCompressedOops, int objectAlignment, int maxVectorByteSize) {
        this(arch, true, oopVectorStride, useCompressedOops, objectAlignment, maxVectorByteSize);
    }

    private VectorAArch64(AArch64 arch, boolean enabled, int oopVectorStride, boolean useCompressedOops, int objectAlignment, int maxVectorByteSize) {
        super(oopVectorStride, useCompressedOops);
        this.arch = arch;
        this.enabled = enabled;
        this.objectAlignment = objectAlignment;
        this.maxVectorByteSize = Math.min(NEON_BYTE_WIDTH, maxVectorByteSize);
        assert CodeUtil.isPowerOf2(this.maxVectorByteSize) : maxVectorByteSize;
    }

    @Override
    public int getMaxVectorLength(Stamp stamp) {
        return getSupportedVectorMoveLength(stamp, Integer.MAX_VALUE);
    }

    @Override
    public int getMaxLogicVectorLength(Stamp logicStamp) {
        return getMaxVectorLength(logicStamp);
    }

    @Override
    public int getShortestCoveringVectorLength(Stamp stamp, int length) {
        assert isVectorizable(stamp) : stamp;

        // determining the base kind
        int elementByteSize = getVectorStride(stamp);
        AArch64Kind baseKind;
        switch (elementByteSize) {
            case 1:
                baseKind = BYTE;
                break;
            case 2:
                baseKind = WORD;
                break;
            case 4:
                baseKind = stamp instanceof FloatStamp ? SINGLE : DWORD;
                break;
            case 8:
                baseKind = stamp instanceof FloatStamp ? DOUBLE : QWORD;
                break;
            default:
                throw GraalError.shouldNotReachHere("Unexpected element size."); // ExcludeFromJacocoGeneratedReport
        }

        return ASIMDKind.getASIMDKind(baseKind, length).getVectorLength();
    }

    @Override
    public boolean shouldBroadcastVectorShiftCount(SimdStamp xStamp, IntegerStamp yStamp) {
        return false;
    }

    @Override
    public int getSupportedVectorMoveLength(Stamp stamp, int maxLength) {
        if (!enableMoveOps || !hasMinimumVectorizationRequirements(maxLength)) {
            return 1;
        }

        return getSupportedVectorLength(stamp, maxLength);
    }

    @Override
    public int getSupportedVectorMaskedMoveLength(Stamp stamp, int maxLength) {
        return 1;
    }

    @Override
    public int getSupportedVectorArithmeticLength(Stamp stamp, int maxLength, Op op) {
        if (!enableArithmeticOps || !hasMinimumVectorizationRequirements(maxLength)) {
            return 1;
        }

        if (!AArch64SupportedArithmeticVectorInstructionsTable.isSupported(op, stamp)) {
            return 1;
        }

        return getSupportedVectorLength(stamp, maxLength);
    }

    @Override
    public int getSupportedVectorShiftWithScalarCount(Stamp stamp, int maxLength, Op op) {
        return getSupportedVectorArithmeticLength(stamp, maxLength, op);
    }

    @Override
    public boolean narrowedVectorInstructionAvailable(NarrowableArithmeticNode operation, IntegerStamp narrowedStamp) {
        Op op = operation.getArithmeticOp();
        if (op instanceof IntegerConvertOp && narrowedStamp.getBits() >= Byte.SIZE) {
            /*
             * Neon can support arbitrary integer conversions (via possibly multiple instructions).
             */
            return true;
        } else {
            return AArch64SupportedArithmeticVectorInstructionsTable.isSupported(op, narrowedStamp);
        }
    }

    @Override
    public boolean narrowedVectorShiftAvailable(ShiftNode<?> op, IntegerStamp narrowedStamp) {
        return narrowedVectorInstructionAvailable(op, narrowedStamp);
    }

    @Override
    public int getSupportedVectorConvertLength(Stamp result, Stamp input, int maxLength, IntegerConvertOp<?> op) {
        if (!enableConvertOps || !hasMinimumVectorizationRequirements(maxLength)) {
            return 1;
        }

        if (!AArch64VectorConvertInstructionsTable.isSupportedConversion(input, result)) {
            return 1;
        }

        return Integer.min(getSupportedVectorLength(input, maxLength), getSupportedVectorLength(result, maxLength));
    }

    @Override
    public int getSupportedVectorConvertLength(Stamp result, Stamp input, int maxLength, FloatConvert op) {
        if (!enableConvertOps || !hasMinimumVectorizationRequirements(maxLength)) {
            return 1;
        }

        if (!AArch64VectorConvertInstructionsTable.isSupportedConversion(op)) {
            return 1;
        }

        return Integer.min(getSupportedVectorLength(input, maxLength), getSupportedVectorLength(result, maxLength));
    }

    @Override
    public int getSupportedVectorConditionalLength(Stamp stamp, int maxLength) {
        if (!enableConditionalOps || !hasMinimumVectorizationRequirements(maxLength)) {
            return 1;
        }

        return getSupportedVectorLength(stamp, maxLength);
    }

    @Override
    public int getSupportedVectorLogicLength(LogicNode logicNode, int maxLength) {
        if (!enableLogicOps || !hasMinimumVectorizationRequirements(maxLength)) {
            return 1;
        }

        return super.getSupportedVectorLogicLengthHelper(logicNode, maxLength);
    }

    @Override
    public int getSupportedVectorComparisonLength(Stamp stamp, CanonicalCondition condition, int maxLength) {
        if (!enableComparisonOps || !hasMinimumVectorizationRequirements(maxLength)) {
            return 1;
        }

        return getSupportedVectorLength(stamp, maxLength);
    }

    @Override
    public int getSupportedVectorGatherLength(Stamp elementStamp, Stamp offsetStamp, int maxLength) {
        return 1;
    }

    @Override
    public int getSupportedVectorPermuteLength(Stamp elementStamp, int maxLength) {
        if (!enablePermuteOps || !hasMinimumVectorizationRequirements(maxLength)) {
            return 1;
        }

        return getSupportedVectorLength(elementStamp, maxLength);
    }

    @Override
    public int getSupportedVectorBlendLength(Stamp elementStamp, int maxLength) {
        if (!enableBlendOps || !hasMinimumVectorizationRequirements(maxLength)) {
            return 1;
        }

        return getSupportedVectorLength(elementStamp, maxLength);
    }

    @Override
    public int getSupportedVectorCompressExpandLength(Stamp elementStamp, int maxLength) {
        return 1;
    }

    @Override
    public int getObjectAlignment() {
        return objectAlignment;
    }

    @Override
    public boolean supportsAES() {
        return arch.getFeatures().contains(CPUFeature.AES) && arch.getFeatures().contains(CPUFeature.ASIMD);
    }

    @Override
    public boolean logicVectorsAreBitmasks() {
        return true;
    }

    private boolean hasMinimumVectorizationRequirements(int maxLength) {
        /* Only performing vectorization when NEON instructions are available. */
        return enableVectorization && enabled && maxLength > 1 && arch.getFeatures().contains(AArch64.CPUFeature.ASIMD);
    }

    /**
     * For computing the maximum number of vector elements that can be handled in one machine code
     * instruction, we need to take the following constraints into account:
     * <ul>
     * <li>what is the maximum number of vector elements that we want to vectorize?</li>
     * <li>For the requested type, what is the maximum number of vector elements that can fit within
     * into the {@link #maxVectorByteSize}?</li>
     * <li>For the requested type, what is the minimum number of vector elements which can be
     * represented by a {@link AArch64Kind}?</li>
     * </ul>
     */
    private int getSupportedVectorLength(Stamp stamp, int maxLength) {
        if (!isVectorizable(stamp)) {
            return 1;
        } else if (stamp instanceof AbstractObjectStamp && !enableObjectVectorization) {
            /* Only handling primitive values. */
            return 1;
        }
        int elementByteSize = getVectorStride(stamp);
        int maxSupportedVectorLength = maxVectorByteSize / elementByteSize;
        assert CodeUtil.isPowerOf2(maxSupportedVectorLength) : maxSupportedVectorLength;

        /* Currently, vector lengths must be a power of two. */
        int vectorLength = maxLength <= maxSupportedVectorLength ? Integer.highestOneBit(maxLength) : maxSupportedVectorLength;

        /*
         * Currently, not all possible AArch64Kind vector lengths exist. Therefore, one must
         * guarantee the length requested is larger than the minimum supported.
         */
        int minSupportedVectorLength = 0;
        switch (elementByteSize) {
            case 1:
                /* V32_BYTE */
                minSupportedVectorLength = 4;
                break;
            case 2:
                /* V32_WORD */
                minSupportedVectorLength = 2;
                break;
            case 4:
                /* Float: V128_SINGLE, Int/Objects: V64_DWORD */
                minSupportedVectorLength = stamp instanceof FloatStamp ? 4 : 2;
                break;
            case 8:
                /* Float: V128_DOUBLE, Int/Objects: V128_QWORD */
                minSupportedVectorLength = 2;
                break;
            default:
                throw GraalError.shouldNotReachHere("Unexpected element size."); // ExcludeFromJacocoGeneratedReport
        }

        if (vectorLength < minSupportedVectorLength) {
            /*
             * If vector length requested is too small, then vectorization is not possible.
             */
            return 1;
        }

        return vectorLength;
    }

    private static final class AArch64SupportedArithmeticVectorInstructionsTable {
        private static final AArch64Kind[] FLOAT_KINDS = {SINGLE, DOUBLE};
        private static final AArch64Kind[] INTEGER_KINDS = {BYTE, WORD, DWORD, QWORD};
        /*
         * Some NEON integer arithmetic instructions, such as multiply, do not support long types.
         */
        private static final AArch64Kind[] NON_LONG_INTEGER_KINDS = {BYTE, WORD, DWORD};
        private static final AArch64Kind[] NONE = new AArch64Kind[0];

        /* Map of the supported AArch64 kinds for each arithmetic op type. */
        protected static final EconomicMap<Op, AArch64Kind[]> supportedOps = EconomicMap.create();

        static {
            register(ArithmeticOpTable::getNeg, INTEGER_KINDS, FLOAT_KINDS);
            register(ArithmeticOpTable::getAdd, INTEGER_KINDS, FLOAT_KINDS);
            register(ArithmeticOpTable::getSub, INTEGER_KINDS, FLOAT_KINDS);
            register(ArithmeticOpTable::getMul, NON_LONG_INTEGER_KINDS, FLOAT_KINDS);
            register(ArithmeticOpTable::getDiv, NONE, FLOAT_KINDS);
            register(ArithmeticOpTable::getNot, INTEGER_KINDS, NONE);
            register(ArithmeticOpTable::getAnd, INTEGER_KINDS, FLOAT_KINDS);
            register(ArithmeticOpTable::getOr, INTEGER_KINDS, FLOAT_KINDS);
            register(ArithmeticOpTable::getXor, INTEGER_KINDS, NONE);
            register(ArithmeticOpTable::getShl, INTEGER_KINDS, NONE);
            register(ArithmeticOpTable::getShr, INTEGER_KINDS, NONE);
            register(ArithmeticOpTable::getUShr, INTEGER_KINDS, NONE);
            register(ArithmeticOpTable::getAbs, INTEGER_KINDS, FLOAT_KINDS);
            register(ArithmeticOpTable::getSqrt, NONE, FLOAT_KINDS);
            register(ArithmeticOpTable::getMin, NON_LONG_INTEGER_KINDS, FLOAT_KINDS);
            register(ArithmeticOpTable::getMax, NON_LONG_INTEGER_KINDS, FLOAT_KINDS);
            register(ArithmeticOpTable::getUMin, NON_LONG_INTEGER_KINDS, NONE);
            register(ArithmeticOpTable::getUMax, NON_LONG_INTEGER_KINDS, NONE);
            register(ArithmeticOpTable::getFMA, NONE, FLOAT_KINDS);
            register(ArithmeticOpTable::getCompress, NONE, NONE);
            register(ArithmeticOpTable::getExpand, NONE, NONE);

            /*
             * Not sure if there is a nice way to vectorize missing ops. For MulHigh and UMulHigh,
             * can used MULL/UMULL and move values into bottom reg - but pretty convoluted. This
             * strategy would work for 32/64 bit lengths up to word (not doubleword...).
             *
             * Missing ops: getUMulHigh, getMulHigh, getRem
             */

        }

        private static void register(Function<ArithmeticOpTable, Op> op, AArch64Kind[] integerKinds, AArch64Kind[] floatKinds) {
            if (integerKinds != NONE) {
                supportedOps.put(op.apply(IntegerStamp.OPS), integerKinds);
            }

            if (floatKinds != NONE) {
                supportedOps.put(op.apply(FloatStamp.OPS), floatKinds);
            }
        }

        private static boolean stampMatches(Stamp stamp, AArch64Kind kind) {
            if (!(stamp instanceof PrimitiveStamp)) {
                return false;
            }

            PrimitiveStamp pStamp = (PrimitiveStamp) stamp;
            int byteSize = pStamp.getBits() / Byte.SIZE;
            switch (kind) {
                case BYTE:
                    return pStamp instanceof IntegerStamp && byteSize == BYTE.getSizeInBytes();
                case WORD:
                    return pStamp instanceof IntegerStamp && byteSize == WORD.getSizeInBytes();
                case DWORD:
                    return pStamp instanceof IntegerStamp && byteSize == DWORD.getSizeInBytes();
                case QWORD:
                    return pStamp instanceof IntegerStamp && byteSize == QWORD.getSizeInBytes();
                case SINGLE:
                    return pStamp instanceof FloatStamp && byteSize == SINGLE.getSizeInBytes();
                case DOUBLE:
                    return pStamp instanceof FloatStamp && byteSize == DOUBLE.getSizeInBytes();
            }
            return false;
        }

        public static boolean isSupported(Op op, Stamp stamp) {
            AArch64Kind[] supportedTypes = supportedOps.get(op, NONE);
            for (AArch64Kind type : supportedTypes) {
                if (stampMatches(stamp, type)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class AArch64VectorConvertInstructionsTable {
        public static boolean isSupportedConversion(Stamp input, Stamp output) {
            return input instanceof PrimitiveStamp && output instanceof PrimitiveStamp && PrimitiveStamp.getBits(input) >= Byte.SIZE && PrimitiveStamp.getBits(output) >= Byte.SIZE;
        }

        public static boolean isSupportedConversion(FloatConvert op) {
            return switch (op) {
                /* floating-point convert narrow or long */
                case D2F, F2D -> true;
                /* same element size */
                case D2L, L2D, F2I, I2F -> true;
                /* unsigned conversions need testing */
                case D2UL, UL2D, F2UI, UI2F -> false;
                /* I think I have support for these. TODO enable them */
                case D2I, I2D, D2UI, UI2D -> false;
                /* Don't see a good strategy for implementing these. */
                case F2L, L2F, F2UL, UL2F -> false;
                default -> false;
            };
        }
    }
}
