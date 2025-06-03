/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle;

import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.VectorAPIEnabled;
import com.oracle.truffle.api.CompilerDirectives;

import java.util.function.BiFunction;
import java.util.function.IntFunction;

@TargetClass(className = "jdk.incubator.vector.Vector", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_incubator_vector_Vector {
}

@TargetClass(className = "jdk.incubator.vector.AbstractVector", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_incubator_vector_AbstractVector {

    // Slow-path method.
    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native ArrayIndexOutOfBoundsException wrongPart(Target_jdk_incubator_vector_AbstractSpecies dsp, Target_jdk_incubator_vector_AbstractSpecies rsp, boolean lanewise, int part);
}

@TargetClass(className = "jdk.internal.vm.vector.Utils", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_internal_vm_vector_Utils {

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native boolean isNonCapturingLambda(Object o);
}

@TargetClass(className = "jdk.internal.vm.vector.VectorSupport", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_internal_vm_vector_VectorSupport {

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "Vector", onlyWith = VectorAPIEnabled.class)
    static final class Target_jdk_internal_vm_vector_VectorSupport_Vector {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "VectorMask", onlyWith = VectorAPIEnabled.class)
    static final class Target_jdk_internal_vm_vector_VectorSupport_VectorMask {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "VectorShuffle", onlyWith = VectorAPIEnabled.class)
    static final class Target_jdk_internal_vm_vector_VectorSupport_VectorShuffle {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "VectorSpecies", onlyWith = VectorAPIEnabled.class)
    static final class Target_jdk_internal_vm_vector_VectorSupport_VectorSpecies {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "VectorPayload", onlyWith = VectorAPIEnabled.class)
    static final class Target_jdk_internal_vm_vector_VectorSupport_VectorPayload {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "FromBitsCoercedOperation", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_FromBitsCoercedOperation {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "IndexPartiallyInUpperRangeOperation", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_IndexPartiallyInUpperRangeOperation {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "ReductionOperation", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_ReductionOperation {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "VecExtractOp", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_VecExtractOp {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "VecInsertOp", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_VecInsertOp {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "UnaryOperation", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_UnaryOperation {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "BinaryOperation", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_BinaryOperation {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "TernaryOperation", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_TernaryOperation {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "LoadOperation", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_LoadOperation {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "LoadVectorMaskedOperation", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_LoadVectorMaskedOperation {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "StoreVectorOperation", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_StoreVectorOperation {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "StoreVectorMaskedOperation", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_StoreVectorMaskedOperation {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "VectorCompareOp", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_VectorCompareOp {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "VectorBlendOp", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_VectorBlendOp {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "VectorBroadcastIntOp", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_VectorBroadcastIntOp {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "VectorConvertOp", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_VectorConvertOp {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "CompressExpandOperation", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_CompressExpandOperation {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "VectorRearrangeOp", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_VectorRearrangeOp {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "VectorMaskOp", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_VectorMaskOp {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "IndexOperation", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_IndexOperation {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "SelectFromTwoVector", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_SelectFromTwoVector {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "LoadVectorOperationWithMap", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_LoadVectorOperationWithMap {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "StoreVectorOperationWithMap", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_StoreVectorOperationWithMap {
    }

    @TargetClass(className = "jdk.internal.vm.vector.VectorSupport", innerClass = "VectorSelectFromOp", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_internal_vm_vector_VectorSupport_VectorSelectFromOp {
    }

    // The methods below have intrinsics in VectorAPIIntrinsics. On fast paths, those should be used
    // instead of the Java fallback implementation. Since we do not rely on these methods on fast
    // paths, we can omit them from PE and reduce the number of methods needed for runtime
    // compilation.

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_internal_vm_vector_VectorSupport_VectorPayload fromBitsCoerced(Class<?> vmClass, Class<?> eClass, int length, long bits, int mode,
                    Target_jdk_internal_vm_vector_VectorSupport_VectorSpecies s, Target_jdk_internal_vm_vector_VectorSupport_FromBitsCoercedOperation defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_internal_vm_vector_VectorSupport_VectorMask indexPartiallyInUpperRange(Class<?> mClass, Class<?> eClass, int length, long offset, long limit,
                    Target_jdk_internal_vm_vector_VectorSupport_IndexPartiallyInUpperRangeOperation defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native long reductionCoerced(int oprId, Class<?> vClass, Class<?> mClass, Class<?> eClass, int length, Target_jdk_internal_vm_vector_VectorSupport_Vector v,
                    Target_jdk_internal_vm_vector_VectorSupport_VectorMask m, Target_jdk_internal_vm_vector_VectorSupport_ReductionOperation defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native long extract(Class<?> vClass, Class<?> eClass, int length, Target_jdk_internal_vm_vector_VectorSupport_VectorPayload vm, int i,
                    Target_jdk_internal_vm_vector_VectorSupport_VecExtractOp defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_internal_vm_vector_VectorSupport_Vector insert(Class<?> vClass, Class<?> eClass, int length, Target_jdk_internal_vm_vector_VectorSupport_Vector v, int i, long val,
                    Target_jdk_internal_vm_vector_VectorSupport_VecInsertOp defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_internal_vm_vector_VectorSupport_Vector unaryOp(int oprId, Class<?> vClass, Class<?> mClass, Class<?> eClass, int length,
                    Target_jdk_internal_vm_vector_VectorSupport_Vector v, Target_jdk_internal_vm_vector_VectorSupport_VectorMask m,
                    Target_jdk_internal_vm_vector_VectorSupport_UnaryOperation defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_internal_vm_vector_VectorSupport_VectorPayload binaryOp(int oprId, Class<?> vmClass, Class<?> mClass, Class<?> eClass, int length,
                    Target_jdk_internal_vm_vector_VectorSupport_VectorPayload v1, Target_jdk_internal_vm_vector_VectorSupport_VectorPayload v2,
                    Target_jdk_internal_vm_vector_VectorSupport_VectorMask m, Target_jdk_internal_vm_vector_VectorSupport_BinaryOperation defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_internal_vm_vector_VectorSupport_Vector ternaryOp(int oprId, Class<?> vClass, Class<?> mClass, Class<?> eClass, int length,
                    Target_jdk_internal_vm_vector_VectorSupport_Vector v1, Target_jdk_internal_vm_vector_VectorSupport_Vector v2, Target_jdk_internal_vm_vector_VectorSupport_Vector v3,
                    Target_jdk_internal_vm_vector_VectorSupport_VectorMask m, Target_jdk_internal_vm_vector_VectorSupport_TernaryOperation defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_internal_vm_vector_VectorSupport_VectorPayload load(Class<?> vmClass, Class<?> eClass, int length, Object base, long offset, boolean fromSegment, Object container,
                    long index, Target_jdk_internal_vm_vector_VectorSupport_VectorSpecies s, Target_jdk_internal_vm_vector_VectorSupport_LoadOperation defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_internal_vm_vector_VectorSupport_Vector loadMasked(Class<?> vClass, Class<?> mClass, Class<?> eClass, int length, Object base, long offset, boolean fromSegment,
                    Target_jdk_internal_vm_vector_VectorSupport_VectorMask m, int offsetInRange, Object container, long index, Target_jdk_internal_vm_vector_VectorSupport_VectorSpecies s,
                    Target_jdk_internal_vm_vector_VectorSupport_LoadVectorMaskedOperation defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native void store(Class<?> vClass, Class<?> eClass, int length, Object base, long offset, boolean fromSegment, Target_jdk_internal_vm_vector_VectorSupport_VectorPayload v, Object container,
                    long index, Target_jdk_internal_vm_vector_VectorSupport_StoreVectorOperation defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native void storeMasked(Class<?> vClass, Class<?> mClass, Class<?> eClass, int length, Object base, long offset, boolean fromSegment, Target_jdk_internal_vm_vector_VectorSupport_Vector v,
                    Target_jdk_internal_vm_vector_VectorSupport_VectorMask m, Object container, long index, Target_jdk_internal_vm_vector_VectorSupport_StoreVectorMaskedOperation defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native boolean test(int cond, Class<?> mClass, Class<?> eClass, int length, Target_jdk_internal_vm_vector_VectorSupport_VectorMask m1,
                    Target_jdk_internal_vm_vector_VectorSupport_VectorMask m2,
                    BiFunction<Target_jdk_internal_vm_vector_VectorSupport_VectorMask, Target_jdk_internal_vm_vector_VectorSupport_VectorMask, Boolean> defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_internal_vm_vector_VectorSupport_VectorMask compare(int cond, Class<?> vectorClass, Class<?> mClass, Class<?> eClass, int length,
                    Target_jdk_internal_vm_vector_VectorSupport_Vector v1, Target_jdk_internal_vm_vector_VectorSupport_Vector v2, Target_jdk_internal_vm_vector_VectorSupport_VectorMask m,
                    Target_jdk_internal_vm_vector_VectorSupport_VectorCompareOp defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_internal_vm_vector_VectorSupport_Vector blend(Class<?> vClass, Class<?> mClass, Class<?> eClass, int length, Target_jdk_internal_vm_vector_VectorSupport_Vector v1,
                    Target_jdk_internal_vm_vector_VectorSupport_Vector v2, Target_jdk_internal_vm_vector_VectorSupport_VectorMask m,
                    Target_jdk_internal_vm_vector_VectorSupport_VectorBlendOp defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_internal_vm_vector_VectorSupport_Vector broadcastInt(int opr, Class<?> vClass, Class<?> mClass, Class<?> eClass, int length,
                    Target_jdk_internal_vm_vector_VectorSupport_Vector v, int n, Target_jdk_internal_vm_vector_VectorSupport_VectorMask m,
                    Target_jdk_internal_vm_vector_VectorSupport_VectorBroadcastIntOp defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_internal_vm_vector_VectorSupport_VectorPayload convert(int oprId, Class<?> fromVectorClass, Class<?> fromeClass, int fromVLen, Class<?> toVectorClass, Class<?> toeClass,
                    int toVLen, Target_jdk_internal_vm_vector_VectorSupport_VectorPayload v, Target_jdk_internal_vm_vector_VectorSupport_VectorSpecies s,
                    Target_jdk_internal_vm_vector_VectorSupport_VectorConvertOp defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_internal_vm_vector_VectorSupport_VectorPayload compressExpandOp(int opr, Class<?> vClass, Class<?> mClass, Class<?> eClass, int length,
                    Target_jdk_internal_vm_vector_VectorSupport_Vector v, Target_jdk_internal_vm_vector_VectorSupport_VectorMask m,
                    Target_jdk_internal_vm_vector_VectorSupport_CompressExpandOperation defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_internal_vm_vector_VectorSupport_Vector rearrangeOp(Class<?> vClass, Class<?> shClass, Class<?> mClass, Class<?> eClass, int length,
                    Target_jdk_internal_vm_vector_VectorSupport_Vector v, Target_jdk_internal_vm_vector_VectorSupport_VectorShuffle sh, Target_jdk_internal_vm_vector_VectorSupport_VectorMask m,
                    Target_jdk_internal_vm_vector_VectorSupport_VectorRearrangeOp defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native long maskReductionCoerced(int oper, Class<?> mClass, Class<?> eClass, int length, Target_jdk_internal_vm_vector_VectorSupport_VectorMask m,
                    Target_jdk_internal_vm_vector_VectorSupport_VectorMaskOp defaultImpl);

    // The following methods are not yet intrinsified, but they pull in a lot of code into the
    // native image nevertheless.

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_internal_vm_vector_VectorSupport_Vector indexVector(Class<?> vClass, Class<?> eClass, int length, Target_jdk_internal_vm_vector_VectorSupport_Vector v, int step,
                    Target_jdk_internal_vm_vector_VectorSupport_VectorSpecies s, Target_jdk_internal_vm_vector_VectorSupport_IndexOperation defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_internal_vm_vector_VectorSupport_Vector libraryUnaryOp(long addr, Class<?> vClass, Class<?> eClass, int length, String debugName,
                    Target_jdk_internal_vm_vector_VectorSupport_Vector v, Target_jdk_internal_vm_vector_VectorSupport_UnaryOperation defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_internal_vm_vector_VectorSupport_VectorPayload libraryBinaryOp(long addr, Class<?> vClass, Class<?> eClass, int length, String debugName,
                    Target_jdk_internal_vm_vector_VectorSupport_VectorPayload v1, Target_jdk_internal_vm_vector_VectorSupport_VectorPayload v2,
                    Target_jdk_internal_vm_vector_VectorSupport_BinaryOperation defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_internal_vm_vector_VectorSupport_Vector selectFromTwoVectorOp(Class<?> vClass, Class<?> eClass, int length, Target_jdk_internal_vm_vector_VectorSupport_Vector v1,
                    Target_jdk_internal_vm_vector_VectorSupport_Vector v2, Target_jdk_internal_vm_vector_VectorSupport_Vector v3,
                    Target_jdk_internal_vm_vector_VectorSupport_SelectFromTwoVector defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_internal_vm_vector_VectorSupport_Vector loadWithMap(Class<?> vClass, Class<?> mClass, Class<?> eClass, int length, Class<?> vectorIndexClass, int indexLength, Object base,
                    long offset, Target_jdk_internal_vm_vector_VectorSupport_Vector indexVector1, Target_jdk_internal_vm_vector_VectorSupport_Vector indexVector2,
                    Target_jdk_internal_vm_vector_VectorSupport_Vector indexVector3, Target_jdk_internal_vm_vector_VectorSupport_Vector indexVector4,
                    Target_jdk_internal_vm_vector_VectorSupport_VectorMask m,
                    Object container, int index, int[] indexMap, int indexM, Target_jdk_internal_vm_vector_VectorSupport_VectorSpecies s,
                    Target_jdk_internal_vm_vector_VectorSupport_LoadVectorOperationWithMap defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native void storeWithMap(Class<?> vClass, Class<?> mClass, Class<?> eClass, int length, Class<?> vectorIndexClass, int indexLength, Object base, long offset,
                    Target_jdk_internal_vm_vector_VectorSupport_Vector indexVector, Target_jdk_internal_vm_vector_VectorSupport_Vector v, Target_jdk_internal_vm_vector_VectorSupport_VectorMask m,
                    Object container, int index, int[] indexMap, int indexM, Target_jdk_internal_vm_vector_VectorSupport_StoreVectorOperationWithMap defaultImpl);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_internal_vm_vector_VectorSupport_Vector selectFromOp(Class<?> vClass, Class<?> mClass, Class<?> eClass, int length, Target_jdk_internal_vm_vector_VectorSupport_Vector v1,
                    Target_jdk_internal_vm_vector_VectorSupport_Vector v2, Target_jdk_internal_vm_vector_VectorSupport_VectorMask m,
                    Target_jdk_internal_vm_vector_VectorSupport_VectorSelectFromOp defaultImpl);
}

@TargetClass(className = "jdk.incubator.vector.VectorMathLibrary", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_incubator_vector_VectorMathLibrary {

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_incubator_vector_Vector unaryMathOp(Target_jdk_incubator_vector_VectorOperators.Target_jdk_incubator_vector_VectorOperators_Unary op, int opc,
                    Target_jdk_incubator_vector_VectorSpecies vspecies,
                    IntFunction<Target_jdk_internal_vm_vector_VectorSupport.Target_jdk_internal_vm_vector_VectorSupport_UnaryOperation> implSupplier, Target_jdk_incubator_vector_Vector v);

    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_incubator_vector_Vector binaryMathOp(Target_jdk_incubator_vector_VectorOperators.Target_jdk_incubator_vector_VectorOperators_Binary op, int opc,
                    Target_jdk_incubator_vector_VectorSpecies vspecies,
                    IntFunction<Target_jdk_internal_vm_vector_VectorSupport.Target_jdk_internal_vm_vector_VectorSupport_BinaryOperation> implSupplier, Target_jdk_incubator_vector_Vector v1,
                    Target_jdk_incubator_vector_Vector v2);
}

@TargetClass(className = "jdk.incubator.vector.AbstractSpecies", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_incubator_vector_AbstractSpecies {

    // Slow-path method.
    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native ClassCastException checkFailed(Object what, Object required);

    // Slow-path method.
    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    native IllegalArgumentException badElementBits(long iv, Object cv);

    // Slow-path method.
    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native IllegalArgumentException badArrayBits(Object iv, boolean isInt, long cv);

    // We pre-compute the vector species lookup table during image build-time using
    // VectorAPIFeature. We do not call `computeSpecies` at runtime.
    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native Target_jdk_incubator_vector_AbstractSpecies computeSpecies(Target_jdk_incubator_vector_LaneType laneType, Target_jdk_incubator_vector_VectorShape shape);
}

@TargetClass(className = "jdk.incubator.vector.VectorSpecies", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_incubator_vector_VectorSpecies {
}

@TargetClass(className = "jdk.incubator.vector.VectorOperators", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_incubator_vector_VectorOperators {

    @TargetClass(className = "jdk.incubator.vector.VectorOperators", innerClass = "Unary", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_incubator_vector_VectorOperators_Unary {
    }

    @TargetClass(className = "jdk.incubator.vector.VectorOperators", innerClass = "Binary", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_incubator_vector_VectorOperators_Binary {
    }

    @TargetClass(className = "jdk.incubator.vector.VectorOperators", innerClass = "OperatorImpl", onlyWith = VectorAPIEnabled.class)
    private static final class Target_jdk_incubator_vector_VectorOperators_OperatorImpl {

        // Slow-path method.
        @AnnotateOriginal
        @CompilerDirectives.TruffleBoundary
        native UnsupportedOperationException illegalOperation(int requireKind, int forbidKind);
    }
}

@TargetClass(className = "jdk.incubator.vector.LaneType", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_incubator_vector_LaneType {

    // Slow-path method.
    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native RuntimeException badElementType(Class<?> elementType, Object expected);
}

@TargetClass(className = "jdk.incubator.vector.VectorShape", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_incubator_vector_VectorShape {
}

@TargetClass(className = "jdk.incubator.vector.AbstractMask", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_incubator_vector_AbstractMask {

    // Slow-path method.
    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    private native IndexOutOfBoundsException checkIndexFailed(long offset, int lane, long length, int esize);
}

@TargetClass(className = "jdk.incubator.vector.VectorIntrinsics", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_incubator_vector_VectorIntrinsics {

    // Slow-path method.
    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    static native IllegalArgumentException requireLengthFailed(int haveLength, int length);
}

@TargetClass(className = "jdk.incubator.vector.FloatVector", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_incubator_vector_FloatVector {

    @TargetClass(className = "jdk.incubator.vector.FloatVector", innerClass = "FTriOp", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_incubator_vector_FloatVector_FTriOp {
    }

    // This is a fast-path method for the (scalar) implementation of a ternary operator. The only
    // supported operator is fused-multiply-add, which uses `java.lang.Math.fma`, which in turn
    // relies on `BigDecimal`. We need to keep `BigDecimal` methods out of PE code.
    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    native Target_jdk_incubator_vector_FloatVector tOpTemplate(Target_jdk_incubator_vector_Vector o1, Target_jdk_incubator_vector_Vector o2, Target_jdk_incubator_vector_FloatVector_FTriOp f);
}

@TargetClass(className = "jdk.incubator.vector.DoubleVector", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_incubator_vector_DoubleVector {

    @TargetClass(className = "jdk.incubator.vector.DoubleVector", innerClass = "FTriOp", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_incubator_vector_DoubleVector_FTriOp {
    }

    // See the comment on Target_jdk_incubator_vector_FloatVector.tOpTemplate.
    @AnnotateOriginal
    @CompilerDirectives.TruffleBoundary
    native Target_jdk_incubator_vector_DoubleVector tOpTemplate(Target_jdk_incubator_vector_Vector o1, Target_jdk_incubator_vector_Vector o2, Target_jdk_incubator_vector_DoubleVector_FTriOp f);
}
