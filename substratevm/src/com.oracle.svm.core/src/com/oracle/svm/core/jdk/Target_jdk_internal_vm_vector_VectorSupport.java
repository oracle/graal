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
package com.oracle.svm.core.jdk;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.vm.ci.code.CPUFeatureName;

@TargetClass(className = "jdk.internal.vm.vector.VectorSupport")
final class Target_jdk_internal_vm_vector_VectorSupport {
    @Delete
    private static native int registerNatives();

    @SuppressWarnings("unused")
    @Substitute
    private static int getMaxLaneCount(Class<?> etype) {
        return VectorAPISupport.singleton().getMaxLaneCount(etype);
    }

    /**
     * Substitutes the native method with a constant string defined at build time.
     */
    @Substitute
    public static String getCPUFeatures() {
        return Helper_jdk_internal_vm_vector_VectorSupport.getCPUFeatures();
    }
}

final class Helper_jdk_internal_vm_vector_VectorSupport {
    @Fold
    public static String getCPUFeatures() {
        return ImageSingletons.lookup(SubstrateTargetDescription.class).arch.getFeatures().stream()
                        .map(CPUFeatureName::name)
                        .collect(Collectors.joining(","));
    }
}

@TargetClass(className = "jdk.incubator.vector.LaneType", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_incubator_vector_LaneType {

}

@TargetClass(className = "jdk.incubator.vector.VectorOperators", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_incubator_vector_VectorOperators {
    @TargetClass(className = "jdk.incubator.vector.VectorOperators", innerClass = "ConversionImpl", onlyWith = VectorAPIEnabled.class)
    private static final class Target_jdk_incubator_vector_VectorOperators_ConversionImpl<E, F> {
        /*
         * The following methods are not annotated @ForceInline in the JDK although the Vector API
         * implementation clearly expects them to be inlined. They are on the hot path, and both
         * their hot path callers and their callees are all @ForceInline.
         */

        @AnnotateOriginal
        @AlwaysInline("Vector API performance")
        native char kind();

        @AnnotateOriginal
        @AlwaysInline("Vector API performance")
        native Target_jdk_incubator_vector_LaneType domain();

        @AnnotateOriginal
        @AlwaysInline("Vector API performance")
        native Target_jdk_incubator_vector_LaneType range();

        @AnnotateOriginal
        @AlwaysInline("Vector API performance")
        private static native Target_jdk_incubator_vector_VectorOperators_ConversionImpl<?, ?> ofCopy(Target_jdk_incubator_vector_LaneType dom);

        @AnnotateOriginal
        @AlwaysInline("Vector API performance")
        private static native Target_jdk_incubator_vector_VectorOperators_ConversionImpl<?, ?> ofCast(Target_jdk_incubator_vector_LaneType dom, Target_jdk_incubator_vector_LaneType ran);

        @AnnotateOriginal
        @AlwaysInline("Vector API performance")
        private static native Target_jdk_incubator_vector_VectorOperators_ConversionImpl<?, ?> ofReinterpret(Target_jdk_incubator_vector_LaneType dom, Target_jdk_incubator_vector_LaneType ran);
    }

    @TargetClass(className = "jdk.incubator.vector.VectorOperators", innerClass = "Operator", onlyWith = VectorAPIEnabled.class)
    interface Target_jdk_incubator_vector_VectorOperators_Operator {
    }

    @TargetClass(className = "jdk.incubator.vector.VectorOperators", innerClass = "ImplCache", onlyWith = VectorAPIEnabled.class)
    static final class Target_jdk_incubator_vector_VectorOperators_ImplCache<OP extends Target_jdk_incubator_vector_VectorOperators_Operator, T> {

        @Alias Object[] cache;

        /*
         * We substitute ImplCache#find to remove the call to isNonCapturingLambda. In the process,
         * we simplify the cache lookup by removing lazy cache initialization as we precompute the
         * cache.
         */
        @Substitute
        @AlwaysInline("Vector API fast-path")
        @SuppressWarnings({"unchecked", "unused"})
        public T find(OP op, int opc, IntFunction<T> supplier) {
            T fn = (T) cache[opc];
            return fn;
        }
    }
}

@TargetClass(className = "jdk.incubator.vector.AbstractVector", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_incubator_vector_AbstractVector {
}

@TargetClass(className = "jdk.incubator.vector.AbstractSpecies", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_incubator_vector_AbstractSpecies {

    @Alias private Target_jdk_incubator_vector_AbstractVector dummyVector;

    /*
     * We initialize the `dummyVector` fields during image build-time using VectorAPIFeature. We can
     * have the getter method return the precomputed dummy vector directly.
     */
    @Substitute
    Target_jdk_incubator_vector_AbstractVector dummyVector() {
        return dummyVector;
    }
}

@TargetClass(className = "jdk.incubator.vector.ByteVector", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_incubator_vector_ByteVector {
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.ArrayIndexShift, declClass = byte[].class, isFinal = true) //
    @TargetElement(name = "ARRAY_SHIFT") //
    private static int arrayShift;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.ArrayBaseOffset, declClass = byte[].class, isFinal = true) //
    @TargetElement(name = "ARRAY_BASE") //
    private static long arrayBase;

    @Alias @RecomputeFieldValue(isFinal = true, kind = RecomputeFieldValue.Kind.None) //
    @TargetElement(name = "ELEMENT_LAYOUT") //
    static ValueLayout.OfByte elementLayout;

    @Substitute
    static void memorySegmentSet(MemorySegment ms, long o, int i, byte e) {
        elementLayout.varHandle().set(ms, o + i * 1L, e);
    }

    @Substitute
    static byte memorySegmentGet(MemorySegment ms, long o, int i) {
        return (byte) elementLayout.varHandle().get(ms, o + i * 1L);
    }
}

@TargetClass(className = "jdk.incubator.vector.ShortVector", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_incubator_vector_ShortVector {
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.ArrayIndexShift, declClass = short[].class, isFinal = true) //
    @TargetElement(name = "ARRAY_SHIFT") //
    private static int arrayShift;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.ArrayBaseOffset, declClass = short[].class, isFinal = true) //
    @TargetElement(name = "ARRAY_BASE") //
    private static long arrayBase;

    @Alias @RecomputeFieldValue(isFinal = true, kind = RecomputeFieldValue.Kind.None) //
    @TargetElement(name = "ELEMENT_LAYOUT") //
    static ValueLayout.OfShort elementLayout;

    @Substitute
    static void memorySegmentSet(MemorySegment ms, long o, int i, short e) {
        elementLayout.varHandle().set(ms, o + i * 2L, e);
    }

    @Substitute
    static short memorySegmentGet(MemorySegment ms, long o, int i) {
        return (short) elementLayout.varHandle().get(ms, o + i * 2L);
    }
}

@TargetClass(className = "jdk.incubator.vector.IntVector", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_incubator_vector_IntVector {
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.ArrayIndexShift, declClass = int[].class, isFinal = true) //
    @TargetElement(name = "ARRAY_SHIFT") //
    private static int arrayShift;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.ArrayBaseOffset, declClass = int[].class, isFinal = true) //
    @TargetElement(name = "ARRAY_BASE") //
    private static long arrayBase;

    @Alias @RecomputeFieldValue(isFinal = true, kind = RecomputeFieldValue.Kind.None) //
    @TargetElement(name = "ELEMENT_LAYOUT") //
    static ValueLayout.OfInt elementLayout;

    @Substitute
    static void memorySegmentSet(MemorySegment ms, long o, int i, int e) {
        elementLayout.varHandle().set(ms, o + i * 4L, e);
    }

    @Substitute
    static int memorySegmentGet(MemorySegment ms, long o, int i) {
        return (int) elementLayout.varHandle().get(ms, o + i * 4L);
    }
}

@TargetClass(className = "jdk.incubator.vector.LongVector", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_incubator_vector_LongVector {
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.ArrayIndexShift, declClass = long[].class, isFinal = true) //
    @TargetElement(name = "ARRAY_SHIFT") //
    private static int arrayShift;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.ArrayBaseOffset, declClass = long[].class, isFinal = true) //
    @TargetElement(name = "ARRAY_BASE") //
    private static long arrayBase;

    @Alias @RecomputeFieldValue(isFinal = true, kind = RecomputeFieldValue.Kind.None) //
    @TargetElement(name = "ELEMENT_LAYOUT") //
    static ValueLayout.OfLong elementLayout;

    @Substitute
    static void memorySegmentSet(MemorySegment ms, long o, int i, long e) {
        elementLayout.varHandle().set(ms, o + i * 8L, e);
    }

    @Substitute
    static long memorySegmentGet(MemorySegment ms, long o, int i) {
        return (long) elementLayout.varHandle().get(ms, o + i * 8L);
    }
}

@TargetClass(className = "jdk.incubator.vector.FloatVector", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_incubator_vector_FloatVector {
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.ArrayIndexShift, declClass = float[].class, isFinal = true) //
    @TargetElement(name = "ARRAY_SHIFT") //
    private static int arrayShift;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.ArrayBaseOffset, declClass = float[].class, isFinal = true) //
    @TargetElement(name = "ARRAY_BASE") //
    private static long arrayBase;

    @Alias @RecomputeFieldValue(isFinal = true, kind = RecomputeFieldValue.Kind.None) //
    @TargetElement(name = "ELEMENT_LAYOUT") //
    static ValueLayout.OfFloat elementLayout;

    @Substitute
    static void memorySegmentSet(MemorySegment ms, long o, int i, float e) {
        elementLayout.varHandle().set(ms, o + i * 4L, e);
    }

    @Substitute
    static float memorySegmentGet(MemorySegment ms, long o, int i) {
        return (float) elementLayout.varHandle().get(ms, o + i * 4L);
    }
}

@TargetClass(className = "jdk.incubator.vector.DoubleVector", onlyWith = VectorAPIEnabled.class)
final class Target_jdk_incubator_vector_DoubleVector {
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.ArrayIndexShift, declClass = double[].class, isFinal = true) //
    @TargetElement(name = "ARRAY_SHIFT") //
    private static int arrayShift;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.ArrayBaseOffset, declClass = double[].class, isFinal = true) //
    @TargetElement(name = "ARRAY_BASE") //
    private static long arrayBase;

    @Alias @RecomputeFieldValue(isFinal = true, kind = RecomputeFieldValue.Kind.None) //
    @TargetElement(name = "ELEMENT_LAYOUT") //
    static ValueLayout.OfDouble elementLayout;

    @Substitute
    static void memorySegmentSet(MemorySegment ms, long o, int i, double e) {
        elementLayout.varHandle().set(ms, o + i * 8L, e);
    }

    @Substitute
    static double memorySegmentGet(MemorySegment ms, long o, int i) {
        return (double) elementLayout.varHandle().get(ms, o + i * 8L);
    }
}
