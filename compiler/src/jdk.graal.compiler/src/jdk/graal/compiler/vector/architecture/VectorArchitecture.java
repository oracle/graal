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
package jdk.graal.compiler.vector.architecture;

import java.util.Objects;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ShortCircuitOrNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.IntegerTestNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.NarrowableArithmeticNode;
import jdk.graal.compiler.nodes.calc.ShiftNode;
import jdk.graal.compiler.nodes.type.NarrowOopStamp;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.vector.nodes.simd.LogicValueStamp;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIType;
import jdk.vm.ci.meta.JavaKind;

/**
 * Represents a CPU architecture that supports vectorization.
 */
public abstract class VectorArchitecture {

    /**
     * The stride (in bytes) for vectors of ordinary object pointers in memory. That is, this is the
     * compressed reference size if compressed references are enabled.
     */
    protected final int oopVectorStride;
    protected final boolean useCompressedOops;
    /**
     * A mask stamp corresponding to the size of a vectorizable oop.
     */
    protected final Stamp oopMaskStamp;
    protected int cachedMaxVectorLength;

    /** The table of Vector API types associated with this vector architecture. */
    protected VectorAPIType.Table vectorAPITypeTable;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        VectorArchitecture that = (VectorArchitecture) o;
        return oopVectorStride == that.oopVectorStride && useCompressedOops == that.useCompressedOops && Objects.equals(oopMaskStamp, that.oopMaskStamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(oopVectorStride, useCompressedOops, oopMaskStamp);
    }

    protected VectorArchitecture(int oopVectorStride, boolean useCompressedOops) {
        this.oopVectorStride = oopVectorStride;
        this.useCompressedOops = useCompressedOops;
        this.oopMaskStamp = IntegerStamp.create(oopVectorStride * Byte.SIZE, -1, 0);
        this.cachedMaxVectorLength = 0;
    }

    /**
     * Determine the maximum vector length supported for vector operations on values of a given
     * {@link Stamp}.
     *
     * @param stamp the type of the individual vector elements
     * @return the maximum supported number of elements in a vector
     */
    public abstract int getMaxVectorLength(Stamp stamp);

    /**
     * Determine the maximum number of logic values of the given {@code stamp} that can be
     * represented in a logic vector.
     *
     * @param stamp the type of the individual vector elements
     * @return the maximum supported number of elements in a logic vector
     */
    public abstract int getMaxLogicVectorLength(Stamp stamp);

    /**
     * Returns the length of the smallest supported platform kind able to accommodate {@code length}
     * elements of {@code stamp} size.
     *
     * @param stamp The stamp of the vector elements
     * @param length The length of the vector
     * @return the length, in elements, of the smallest platform kind that can hold the specified
     *         vector or throws an error if no kind is available
     */
    public abstract int getShortestCoveringVectorLength(Stamp stamp, int length);

    /**
     * Determine the maximum vector length supported for vector operations. This is useful for
     * vector operations that do not restrict simdification themselves and can inherit a vector
     * length computed from their inputs.
     *
     * @return the maximum supported vector size
     */
    public int getMaxVectorLength() {
        if (cachedMaxVectorLength > 0) {
            return cachedMaxVectorLength;
        }
        int maxLength = -1;
        for (JavaKind kind : JavaKind.values()) {
            Stamp kindStamp = (kind.isNumericFloat() ? StampFactory.forKind(kind)
                            : kind.isPrimitive() && kind != JavaKind.Void ? IntegerStamp.create(kind.getBitCount()) : kind == JavaKind.Object ? StampFactory.object() : null);
            if (kindStamp == null) {
                assert kind == JavaKind.Void || kind == JavaKind.Illegal : kind;
                continue;
            }
            int kindLength = getMaxVectorLength(kindStamp);
            maxLength = Integer.max(maxLength, kindLength);
        }
        cachedMaxVectorLength = maxLength;
        return cachedMaxVectorLength;
    }

    /**
     * Determine the element stride for vectors of type {@code stamp} in memory.
     *
     * @param stamp the type of the individual vector elements
     * @return the offset in bytes between two adjacent vector elements
     */
    public int getVectorStride(Stamp stamp) {
        assert isVectorizable(stamp) : "expected a vectorizable stamp (either PrimitiveStamp or AbstractObjectStamp), got " + stamp.getClass().getSimpleName() + ": " + stamp;
        if (stamp instanceof PrimitiveStamp) {
            int bits = PrimitiveStamp.getBits(stamp);
            if (bits < Byte.SIZE) {
                // booleans still need a full byte
                return 1;
            } else {
                return bits / Byte.SIZE;
            }
        } else {
            assert isVectorizableObjectStamp(stamp) : stamp;
            return oopVectorStride;
        }
    }

    /**
     * Determine if a {@code stamp} can be the element type for a vector operation.
     *
     * @param stamp the type of the individual vector elements
     * @return true iff we can compute the stride of the access and it is valid to vectorize
     *         operations on vector of type {@code stamp} in memory
     */
    public boolean isVectorizable(Stamp stamp) {
        return stamp instanceof PrimitiveStamp || isVectorizableObjectStamp(stamp);
    }

    /**
     * Determine if a {@code stamp} is an {@linkplain AbstractObjectStamp object stamp} that can be
     * the element type for a vector operation. If the current VM configuration supports both
     * compressed and uncompressed object references, only compressed references will be
     * vectorizable.
     *
     * @param stamp the type of the individual vector elements
     * @return true iff we can compute the stride of the access and it is valid to vectorize
     *         operations on vector of type {@code stamp} in memory
     */
    public boolean isVectorizableObjectStamp(Stamp stamp) {
        return (useCompressedOops ? stamp instanceof NarrowOopStamp : stamp instanceof ObjectStamp);
    }

    /**
     * Determine whether a scalar shift count of a vector shift should be broadcasted so that the
     * operation becomes a lanewise shift.
     *
     * @param xStamp the type of the shifted node
     * @param yStamp the type of the shift count
     */
    public abstract boolean shouldBroadcastVectorShiftCount(SimdStamp xStamp, IntegerStamp yStamp);

    /**
     * Get a natively supported vector length for a vectorized memory access.
     *
     * @param stamp the stamp of the individual vector elements
     * @param maxLength the maximum length that should be returned
     * @return a supported vector size, but at most {@code maxLength}
     */
    public abstract int getSupportedVectorMoveLength(Stamp stamp, int maxLength);

    /**
     * Get a natively supported vector length for a vectorized masked memory access.
     *
     * @param stamp the stamp of the individual vector elements
     * @param maxLength the maximum length that should be returned
     * @return a supported vector size, but at most {@code maxLength}
     */
    public abstract int getSupportedVectorMaskedMoveLength(Stamp stamp, int maxLength);

    /**
     * Get a natively supported vector length for a vector arithmetic operation.
     *
     * @param stamp the stamp of the individual vector elements
     * @param maxLength the maximum length that should be returned
     * @param op the arithmetic operation for which the vector size should be determined
     * @return a supported vector size, but at most {@code maxLength}
     */
    public abstract int getSupportedVectorArithmeticLength(Stamp stamp, int maxLength, ArithmeticOpTable.Op op);

    /**
     * Get a natively supported vector length for a shift with scalar count.
     *
     * @param stamp the stamp of the individual vector elements
     * @param maxLength the maximum length that should be returned
     * @param op the arithmetic operation for which the vector size should be determined
     * @return a supported vector size, but at most {@code maxLength}
     */
    public abstract int getSupportedVectorShiftWithScalarCount(Stamp stamp, int maxLength, ArithmeticOpTable.Op op);

    /**
     * Returns whether the given vectorized operation may be rewritten to a narrower one.
     *
     * @param operation the existing vectorized arithmetic operation
     * @param narrowedStamp the stamp the operation should be narrowed to
     * @return true if the operation can be replaced with a narrowed one
     */
    public abstract boolean narrowedVectorInstructionAvailable(NarrowableArithmeticNode operation, IntegerStamp narrowedStamp);

    /**
     * Return whether the given vector shift can be narrowed. This is special because we can have
     * vector shifts with scalar counts.
     */
    public abstract boolean narrowedVectorShiftAvailable(ShiftNode<?> op, IntegerStamp narrowedStamp);

    /**
     * Get a natively supported vector length for a vectorized convert operation.
     *
     * @param result the stamp of the result of the convert operation
     * @param input the stamp of the input of the convert operation
     * @param maxLength the maximum length that should be returned
     * @param op the convert operation
     * @return a supported vector size, but at most {@code maxLength}
     */
    public abstract int getSupportedVectorConvertLength(Stamp result, Stamp input, int maxLength, IntegerConvertOp<?> op);

    /**
     * Get a natively supported vector length for a vectorized convert operation.
     *
     * @param result the stamp of the result of the convert operation
     * @param input the stamp of the input of the convert operation
     * @param maxLength the maximum length that should be returned
     * @param op the convert operation
     * @return a supported vector size, but at most {@code maxLength}
     */
    public abstract int getSupportedVectorConvertLength(Stamp result, Stamp input, int maxLength, FloatConvert op);

    /**
     * Get a natively supported vector length for a vectorized conditional operation.
     *
     * @param stamp the stamp of the result of the conditional operation
     * @param maxLength the maximum length that should be returned
     * @return a supported vector size, but at most {@code maxLength}
     */
    public abstract int getSupportedVectorConditionalLength(Stamp stamp, int maxLength);

    /**
     * Get a natively supported vector length for a vectorized logic operation.
     *
     * @param logicNode the logic node that encapsulates the logic operation
     * @param maxLength maxLength the maximum length that should be returned
     * @return a supported vector size, but at most {@code maxLength}
     */
    public abstract int getSupportedVectorLogicLength(LogicNode logicNode, int maxLength);

    /**
     * Utility method for {@link #getSupportedVectorLogicLength}, factoring out common platform
     * independent code.
     */
    protected int getSupportedVectorLogicLengthHelper(LogicNode logicNode, int maxLength) {
        if (logicNode instanceof CompareNode) {
            CompareNode compareNode = (CompareNode) logicNode;
            assert compareNode.getX().stamp(NodeView.DEFAULT).isCompatible(compareNode.getY().stamp(NodeView.DEFAULT)) : compareNode.getX() + " not compatible with " + compareNode.getY();
            return getSupportedVectorComparisonLength(compareNode.getX().stamp(NodeView.DEFAULT), compareNode.condition(), maxLength);
        } else if (logicNode instanceof IsNullNode) {
            return getSupportedVectorComparisonLength(oopMaskStamp, CanonicalCondition.EQ, maxLength);
        } else if (logicNode instanceof ShortCircuitOrNode) {
            // ShortCircuitOrNodes are lowered to if nodes in the low tier. So, we are not be able
            // to vectorize them at the moment.
            return 1;
        } else if (logicNode instanceof LogicConstantNode) {
            return getSupportedVectorMoveLength(logicNode.stamp(NodeView.DEFAULT), maxLength);
        } else if (logicNode instanceof IntegerTestNode) {
            IntegerTestNode testNode = (IntegerTestNode) logicNode;
            int result = getSupportedVectorArithmeticLength(testNode.getX().stamp(NodeView.DEFAULT), maxLength, IntegerStamp.OPS.getAnd());
            return getSupportedVectorComparisonLength(testNode.getX().stamp(NodeView.DEFAULT), CanonicalCondition.EQ, result);
        } else {
            throw GraalError.shouldNotReachHere("Unknown class: " + logicNode.getClass()); // ExcludeFromJacocoGeneratedReport
        }
    }

    /**
     * Get a natively supported vector length for a vectorized comparison.
     *
     * @param stamp the stamp of the vector elements to be compared
     * @param condition the condition of the comparison
     * @param maxLength the maximum length that should be returned
     * @return a supported vector size, but at most {@code maxLength}
     */
    public abstract int getSupportedVectorComparisonLength(Stamp stamp, CanonicalCondition condition, int maxLength);

    /**
     * Get a natively supported vector length for a vector gather operation.
     *
     * @param elementStamp the stamp of the elements to be gathered
     * @param offsetStamp the stamp of the offsets from the base address
     * @param maxLength the maximum length that should be returned
     * @return a supported vector size, but at most {@code maxLength}
     */
    public abstract int getSupportedVectorGatherLength(Stamp elementStamp, Stamp offsetStamp, int maxLength);

    /**
     * Get the maximum supported vector length for a vector permute instruction up to maxLength.
     *
     * @param elementStamp the stamp of the elements to be permuted
     * @param maxLength the maximum length to return
     * @return the number of elements that can be permuted by a single instruction
     */
    public abstract int getSupportedVectorPermuteLength(Stamp elementStamp, int maxLength);

    /**
     * Get the maximum supported vector length for a vector blend instruction up to maxLength.
     *
     * @param elementStamp the stamp of the elements to be blended
     * @param maxLength the maximum length to return
     * @return the number of elements that can be blended by a single instruction
     */
    public abstract int getSupportedVectorBlendLength(Stamp elementStamp, int maxLength);

    /**
     * Get the maximum supported vector length for a vector compress/expand based on a mask.
     *
     * @param elementStamp the stamp of the elements to be blended
     * @param maxLength the maximum length to return
     * @return the number of elements that can be compressed/expanded by a single instruction
     */
    public abstract int getSupportedVectorCompressExpandLength(Stamp elementStamp, int maxLength);

    /**
     * Determine the minimum alignment in bytes that is guaranteed for objects.
     *
     * @return the alignment in bytes that is guaranteed for objects.
     */
    public abstract int getObjectAlignment();

    /**
     * Returns the minimal required elements of stamp have to be available in order to use this
     * vector length for vectorization.
     *
     * @param stamp of the elements
     * @param vectorLength vector size (must be valid size of {@link #getMaxVectorLength(Stamp)}
     * @param optionValues
     */
    public int getMinimalElementsForVectorization(Stamp stamp, int vectorLength, OptionValues optionValues) {
        return 0;
    }

    /**
     * Returns whether the architecture supports floating point conditional operations on all
     * floating point vector lengths, including length 1 (i.e., scalars).
     */
    public boolean supportsFloatingPointConditionalMoves() {
        return true;
    }

    /**
     * Returns whether the architecture supports vectorized float conversion from long to double
     * natively.
     *
     * @return {@code true} iff float conversion of the target kind is supported
     */
    public boolean supportsLongToDoubleFloatConvert() {
        return false;
    }

    /**
     * Returns true if the code generator can concatenate two SIMD inputs of size
     * {@code inputSizeInBytes} into a single wide SIMD value.
     *
     * @param inputSizeInBytes the size of SIMD input to be concatenated in bytes
     * @return {@code true} iff the concatenation is supported
     */
    public boolean supportsVectorConcat(int inputSizeInBytes) {
        return false;
    }

    /**
     * Returns true if the architecture supports AES vector instructions.
     *
     * @return {@code true} if AES instructions are supported.
     */
    public boolean supportsAES() {
        return false;
    }

    /**
     * Determines whether this target represents vectors of logic values as vectors of bitmasks,
     * each with a value of 0 or -1 (all-zeros or all-ones), with each of these bitmasks having the
     * same bit width as some underlying primitive type, and such logic vectors being stored in
     * normal vector registers as regular SIMD vector values. The opposite is a target where vectors
     * of logic values are represented by single 0 or 1 bits in dedicated vector mask registers.
     *
     * @return {@code true} if the target represents logic vectors as vectors of 0 or -1 bitmasks;
     *         {@code false} if the target uses dedicated mask registers
     */
    public abstract boolean logicVectorsAreBitmasks();

    /**
     * Predict the length in bytes of the maximum vector as used by the VM's implementation of the
     * Vector API. This must take into account the overall maximum vector length on the architecture
     * but might also depend on CPU features.
     *
     * @return a vector length in bytes
     */
    public int guessMaxVectorAPIVectorLength(Stamp elementStamp) {
        int elementBytes = PrimitiveStamp.getBits(elementStamp) / 8;
        return getMaxVectorLength(elementStamp) * elementBytes;
    }

    /**
     * Get a natively supported vector length (number of elements) for {@code SimdMaskLogicNode}.
     *
     * @param elementStamp the stamp of the elements used in the condition computing the input mask
     * @param maxLength the maximum length that should be returned
     * @return a supported vector size, but at most {@code maxLength}
     */
    public int getSupportedSimdMaskLogicLength(Stamp elementStamp, int maxLength) {
        return getSupportedVectorMoveLength(elementStamp, maxLength);
    }

    /**
     * Return a stamp representing an element of the result of a vectorized comparison of vectors of
     * {@code elementStamp}. Depending on {@link #logicVectorsAreBitmasks()}, this is either an
     * appropriate {@link IntegerStamp} or a {@link LogicValueStamp}.
     */
    public Stamp maskStamp(Stamp elementStamp) {
        if (logicVectorsAreBitmasks()) {
            int elementBits = getVectorStride(elementStamp) * Byte.SIZE;
            return IntegerStamp.create(elementBits, -1L, 0L);
        } else {
            return LogicValueStamp.UNRESTRICTED;
        }
    }

    public VectorAPIType.Table getVectorAPITypeTable() {
        return vectorAPITypeTable;
    }

    public void setVectorAPITypeTable(VectorAPIType.Table table) {
        GraalError.guarantee(this.vectorAPITypeTable == null, "Vector API type table must only be set once per vector architecture");
        this.vectorAPITypeTable = table;
    }
}
