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

package jdk.graal.compiler.vector.replacements.vectorapi;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.NotNode;
import jdk.graal.compiler.nodes.extended.GetClassNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.architecture.VectorLoweringProvider;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdPrimitiveCompareNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public class VectorAPIUtils {

    /**
     * If {@code maybeClassValue} is a node producing a Java {@link Class} value (i.e., a class
     * constant node or a {@link GetClassNode}), return a non-null object stamp for that class.
     * Return {@code null} otherwise.
     */
    public static ObjectStamp nonNullStampForClassValue(CoreProviders providers, ValueNode maybeClassValue) {
        if (maybeClassValue instanceof GetClassNode getClass) {
            ObjectStamp inputStamp = (ObjectStamp) getClass.getObject().stamp(NodeView.DEFAULT);
            return (ObjectStamp) inputStamp.asNonNull();
        }
        return nonNullStampForClassConstant(providers, maybeClassValue);
    }

    /**
     * If {@code maybeClassConstant} is a constant node referring to a Java {@link Class}, return a
     * non-null object stamp for that class. Return {@code null} otherwise.
     */
    public static ObjectStamp nonNullStampForClassConstant(CoreProviders providers, ValueNode maybeClassConstant) {
        if (maybeClassConstant.isConstant()) {
            ResolvedJavaType javaType = providers.getConstantReflection().asJavaType(maybeClassConstant.asConstant());
            if (javaType != null) {
                return StampFactory.objectNonNull(TypeReference.createExactTrusted(javaType));
            }
        }
        maybeClassConstant.getDebug().log(DebugContext.DETAILED_LEVEL, "Can't derive a Vector API object stamp from %s / %s", maybeClassConstant, maybeClassConstant.stamp(NodeView.DEFAULT));
        return null;
    }

    /**
     * Checks that all the given values are constants, and that {@code vmClass} is a vector or mask
     * class representing vectors with elements of type {@code eClass} and vector length
     * {@code length}. Returns the {@link SimdStamp} corresponding to the vector class, or
     * {@code null} if the preconditions are not satisfied.
     */
    public static SimdStamp stampForVectorClass(ValueNode vmClass, ValueNode eClass, ValueNode length, CoreProviders providers) {
        if (!(vmClass.isJavaConstant() && eClass.isJavaConstant() && length.isJavaConstant() && length.asJavaConstant().getJavaKind() == JavaKind.Int)) {
            return null;
        }
        ConstantReflectionProvider constantReflection = providers.getConstantReflection();
        ResolvedJavaType vmType = constantReflection.asJavaType(vmClass.asJavaConstant());
        ResolvedJavaType eType = constantReflection.asJavaType(eClass.asJavaConstant());
        return stampForVectorClass(vmType, eType, length.asJavaConstant().asInt(), providers);
    }

    public static SimdStamp stampForVectorClass(ResolvedJavaType vmType, ResolvedJavaType eType, int length, CoreProviders providers) {
        if (vmType == null || eType == null) {
            return null;
        }
        VectorAPIType vectorType = VectorAPIType.ofType(vmType, providers);
        if (vectorType == null) {
            return null;
        }
        if (vectorType.stamp.getVectorLength() == 1) {
            /*
             * We cannot support vectors of length 1 because the JVMCI ValueKinds don't allow us to
             * distinguish between scalar values and SIMD values with vector length 1. This means
             * that we would not be able to generate the correct frame info for deopts.
             *
             * The only vectors in the Vector API with length 1 are Long64 and Double64, which are
             * unlikely to have practical significance.
             */
            return null;
        }
        if (!vectorType.isMask && vectorArchitecture(providers).getSupportedVectorMoveLength(vectorType.payloadStamp.getComponent(0), vectorType.vectorLength) != vectorType.vectorLength) {
            /*
             * This vector type is not natively supported by the target. Don't try to intrinsify
             * operations on vectors of this type.
             */
            return null;
        }
        if (vectorType.isMask) {
            return checkMaskElementType(vectorType, eType, length);
        } else if (vectorType.elementKind.equals(eType.getJavaKind()) && vectorType.vectorLength == length) {
            return vectorType.stamp;
        }
        return null;
    }

    static SimdStamp stampForMaskClass(ValueNode mClass, ValueNode eClass, ValueNode length, CoreProviders providers) {
        if (mClass.isJavaConstant() && eClass.isJavaConstant() && length.isJavaConstant() && length.asJavaConstant().getJavaKind() == JavaKind.Int) {
            ConstantReflectionProvider constantReflection = providers.getConstantReflection();
            ResolvedJavaType mType = constantReflection.asJavaType(mClass.asJavaConstant());
            ResolvedJavaType eType = constantReflection.asJavaType(eClass.asJavaConstant());
            if (mType != null && eType != null) {
                VectorAPIType maskType = VectorAPIType.ofType(mType, providers);
                if (maskType != null) {
                    SimdStamp stamp = checkMaskElementType(maskType, eType, length.asJavaConstant().asInt());
                    if (stamp != null) {
                        return stamp;
                    }
                }
            }
        }
        mClass.getDebug().log(DebugContext.DETAILED_LEVEL, "Can't derive a Vector API mask stamp from %s / %s / %s", mClass, eClass, length);
        return null;
    }

    public static SimdStamp checkMaskElementType(VectorAPIType maskType, ResolvedJavaType eType, int length) {
        GraalError.guarantee(maskType.isMask, "expected mask type: %s", maskType);
        /*
         * A floating point mask type has a floating point element type, but the element type passed
         * in to mask operations is an integer type of the same size.
         */
        if (maskType.elementKind.isPrimitive() && eType.isPrimitive()) {
            if (maskType.elementKind.getBitCount() == eType.getJavaKind().getBitCount() && maskType.vectorLength == length) {
                return maskType.stamp;
            }
        }
        return null;
    }

    @SuppressWarnings("unused")
    public static AddressNode buildAddress(ValueNode base, ValueNode offset, ValueNode container, ValueNode index) {
        /*
         * Morally speaking, if the container is an array, then offset = arrayBaseOffset + index *
         * arrayIndexScale should hold. We probably don't gain anything from trying to verify this.
         */
        return new OffsetAddressNode(base, offset);
    }

    /**
     * Computes a location identity from the given {@code container}. If the container is a
     * primitive array, returns a location identity representing that array. Otherwise, returns
     * {@link LocationIdentity#ANY_LOCATION}.
     */
    public static LocationIdentity containerLocationIdentity(ValueNode container) {
        ResolvedJavaType containerType = StampTool.typeOrNull(container);
        if (containerType != null && containerType.isArray() && containerType.getComponentType().isPrimitive()) {
            return NamedLocationIdentity.getArrayLocation(containerType.getComponentType().getJavaKind());
        }
        return LocationIdentity.ANY_LOCATION;
    }

    public static SimdPrimitiveCompareNode isZero(ValueNode vector, VectorArchitecture vectorArch) {
        SimdStamp simdStamp = (SimdStamp) vector.stamp(NodeView.DEFAULT);
        Stamp elementStamp = simdStamp.getComponent(0);
        JavaConstant zero = elementStamp instanceof IntegerStamp
                        ? JavaConstant.forPrimitiveInt(PrimitiveStamp.getBits(elementStamp), 0)
                        : JavaConstant.defaultForKind(elementStamp.getStackKind());
        ConstantNode zeroVector = SimdConstant.constantNodeForBroadcast(zero, simdStamp.getVectorLength());
        return SimdPrimitiveCompareNode.simdCompare(CanonicalCondition.EQ, vector, zeroVector, false, vectorArch);
    }

    public static ValueNode isNonzero(ValueNode vector, VectorArchitecture vectorArch) {
        return NotNode.create(isZero(vector, vectorArch));
    }

    /**
     * Return a stamp for the given primitive kind. Unlike {@link StampFactory#forKind}, this
     * returns an {@code i8} stamp for {@link JavaKind#Boolean}.
     */
    public static PrimitiveStamp primitiveStampForKind(JavaKind kind) {
        GraalError.guarantee(kind.isPrimitive(), "expected primitive kind, got: %s", kind);
        if (kind.isNumericInteger()) {
            return IntegerStamp.create(Math.max(kind.getBitCount(), 8));
        } else {
            return (PrimitiveStamp) StampFactory.forKind(kind);
        }
    }

    public static VectorArchitecture vectorArchitecture(CoreProviders providers) {
        return ((VectorLoweringProvider) providers.getLowerer()).getVectorArchitecture();
    }

    /**
     * Determine whether the given {@code vmClass} input to a Vector API macro represents a
     * {@link VectorAPIType} with the {@link VectorAPIType#isMask} flag set. Returns {@code false}
     * if the {@code vmClass} is not a constant representing a Vector API class.
     */
    public static boolean isMask(ValueNode vmClass, CoreProviders providers) {
        if (vmClass.isJavaConstant()) {
            ConstantReflectionProvider constantReflection = providers.getConstantReflection();
            ResolvedJavaType vType = constantReflection.asJavaType(vmClass.asJavaConstant());
            VectorAPIType vectorType = VectorAPIType.ofType(vType, providers);
            if (vectorType == null) {
                return false;
            }
            return vectorType.isMask;
        }
        return false;
    }

    /**
     * Create a iota vector (a vector with elements being 0, 1, 2, etc) with the given element width
     * and vector length.
     */
    public static ConstantNode iotaVector(int elementBits, int vectorLength) {
        JavaConstant[] iotaValues = new JavaConstant[vectorLength];
        for (int i = 0; i < iotaValues.length; i++) {
            iotaValues[i] = JavaConstant.forPrimitiveInt(elementBits, i);
        }
        return SimdConstant.constantNodeForConstants(iotaValues);
    }
}
