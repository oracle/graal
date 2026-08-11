/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.amd64.AMD64LibCHelper;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.hub.DynamicHubIntrinsics;
import com.oracle.svm.shared.AlwaysInline;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Provides Native Image bindings for the JDK's SIMD implementations of
 * {@code java.util.DualPivotQuicksort.sort} and
 * {@code java.util.DualPivotQuicksort.partition}.
 * <p>
 * The bindings statically link {@code libsimdsort.a} from the JDK and its {@code libm} dependency,
 * which provides {@code log2}. The AVX2 entry points are implemented in
 * {@code src/java.base/linux/native/libsimdsort/avx2-linux-qsort.cpp}, and the AVX-512 entry points
 * are implemented in
 * {@code src/java.base/linux/native/libsimdsort/avx512-linux-qsort.cpp}. The native calls execute in
 * uninterruptible code so that Java arrays cannot move while the library accesses their elements
 * through raw pointers.
 */
@Platforms(Platform.LINUX_AMD64.class)
public final class SimdSortSupport {

    private static final int JVM_T_FLOAT = 6;
    private static final int JVM_T_DOUBLE = 7;
    private static final int JVM_T_INT = 10;
    private static final int JVM_T_LONG = 11;

    /*
     * A/B/A measurements on x86-64-v4 with no native transition showed sharp crossovers at 17
     * elements for int and 21 elements for long. Float and double cross over earlier; 10 elements
     * is the first range size where both have a clear, sustained improvement over the force-inlined
     * Java fallback.
     */
    private static final int MIN_AVX512_FLOAT_SORT_SIZE = 10;
    private static final int MIN_AVX512_DOUBLE_SORT_SIZE = 10;
    private static final int MIN_AVX512_INT_SORT_SIZE = 17;
    private static final int MIN_AVX512_LONG_SORT_SIZE = 21;

    public enum Variant {
        NONE(null, null),
        AVX2("avx2Sort", "avx2Partition"),
        AVX512("avx512Sort", "avx512Partition");

        private final String sortWrapperName;
        private final String partitionWrapperName;

        Variant(String sortWrapperName, String partitionWrapperName) {
            this.sortWrapperName = sortWrapperName;
            this.partitionWrapperName = partitionWrapperName;
        }

        /**
         * Returns the partition wrapper name, or {@code null} for {@link #NONE}.
         */
        public String partitionWrapperName() {
            return partitionWrapperName;
        }

        /**
         * Returns the sort wrapper name, or {@code null} for {@link #NONE}.
         */
        public String sortWrapperName() {
            return sortWrapperName;
        }

        /**
         * Returns whether this variant supports sorting elements of {@code kind}.
         */
        public boolean supports(JavaKind kind) {
            return switch (kind) {
                case Int, Float -> this != NONE;
                case Long, Double -> this == AVX512;
                default -> false;
            };
        }
    }

    private SimdSortSupport() {
    }

    private static boolean supportsAvx512SimdSort() {
        return AMD64LibCHelper.supportsAvx512SimdSort() != 0;
    }

    /**
     * Returns the element kind if {@code variant} supports it, or {@code null} otherwise.
     */
    public static JavaKind getSupportedJavaKind(Variant variant, ResolvedJavaType elementType) {
        if (elementType == null) {
            return null;
        }
        JavaKind kind = elementType.getJavaKind();
        return variant.supports(kind) ? kind : null;
    }

    /**
     * Returns the SIMD sort variant supported by the image target architecture.
     */
    public static Variant getSupportedVariant() {
        if (!Platform.includedIn(Platform.LINUX_AMD64.class) || !(SubstrateTarget.getArchitecture() instanceof AMD64 architecture)) {
            return Variant.NONE;
        }
        if (architecture.getFeatures().contains(AMD64.CPUFeature.AVX512F) && architecture.getFeatures().contains(AMD64.CPUFeature.AVX512DQ)) {
            return Variant.AVX512;
        }
        if (architecture.getFeatures().contains(AMD64.CPUFeature.AVX2)) {
            return Variant.AVX2;
        }
        return Variant.NONE;
    }

    /**
     * Returns the {@code JVM_T_*} element type code expected by libsimdsort.
     */
    public static int toJVMType(JavaKind kind) {
        return switch (kind) {
            case Float -> JVM_T_FLOAT;
            case Double -> JVM_T_DOUBLE;
            case Int -> JVM_T_INT;
            case Long -> JVM_T_LONG;
            default -> throw VMError.shouldNotReachHere("Unsupported SIMD sort element kind: " + kind);
        };
    }

    public static void avx2Sort(Object array, int elementType, int fromIndex, int toIndex, @SuppressWarnings("unused") Object fallback) {
        avx2SortImpl(array, elementType, fromIndex, toIndex);
    }

    public static int[] avx2Partition(Object array, int elementType, int fromIndex, int toIndex, int pivotIndex1, int pivotIndex2, @SuppressWarnings("unused") Object fallback) {
        int[] pivotIndices = new int[2];
        avx2PartitionImpl(array, elementType, fromIndex, toIndex, pivotIndices, pivotIndex1, pivotIndex2);
        return pivotIndices;
    }

    @AlwaysInline("Preserve the force-inlined Java fallback of DualPivotQuicksort.sort")
    public static void avx512Sort(Object array, int elementType, int fromIndex, int toIndex, Object fallback) {
        if (!supportsAvx512SimdSort()) {
            if (isAvx2ElementType(elementType)) {
                avx2Sort(array, elementType, fromIndex, toIndex, fallback);
            } else {
                SubstrateUtil.cast(fallback, Target_java_util_DualPivotQuicksort_SortOperation.class).sort(array, fromIndex, toIndex);
            }
            return;
        }
        if (toIndex - fromIndex < minAvx512SortSize(elementType)) {
            SubstrateUtil.cast(fallback, Target_java_util_DualPivotQuicksort_SortOperation.class).sort(array, fromIndex, toIndex);
            return;
        }
        avx512SortImpl(array, elementType, fromIndex, toIndex);
    }

    @AlwaysInline("Preserve the force-inlined Java fallback of DualPivotQuicksort.partition")
    public static int[] avx512Partition(Object array, int elementType, int fromIndex, int toIndex, int pivotIndex1, int pivotIndex2, Object fallback) {
        if (!supportsAvx512SimdSort()) {
            if (isAvx2ElementType(elementType)) {
                return avx2Partition(array, elementType, fromIndex, toIndex, pivotIndex1, pivotIndex2, fallback);
            } else {
                return SubstrateUtil.cast(fallback, Target_java_util_DualPivotQuicksort_PartitionOperation.class).partition(array, fromIndex, toIndex, pivotIndex1, pivotIndex2);
            }
        }
        int[] pivotIndices = new int[2];
        avx512PartitionImpl(array, elementType, fromIndex, toIndex, pivotIndices, pivotIndex1, pivotIndex2);
        return pivotIndices;
    }

    private static boolean isAvx2ElementType(int elementType) {
        return elementType == JVM_T_FLOAT || elementType == JVM_T_INT;
    }

    private static int minAvx512SortSize(int elementType) {
        return switch (elementType) {
            case JVM_T_FLOAT -> MIN_AVX512_FLOAT_SORT_SIZE;
            case JVM_T_DOUBLE -> MIN_AVX512_DOUBLE_SORT_SIZE;
            case JVM_T_INT -> MIN_AVX512_INT_SORT_SIZE;
            case JVM_T_LONG -> MIN_AVX512_LONG_SORT_SIZE;
            default -> Integer.MAX_VALUE;
        };
    }

    @Uninterruptible(reason = "The array must not move during the native call.")
    private static void avx2SortImpl(Object array, int elementType, int fromIndex, int toIndex) {
        SimdSortLibrary.requireLibM();
        SimdSortLibrary.avx2Sort(addressOfArray(array), elementType, fromIndex, toIndex);
    }

    @Uninterruptible(reason = "The arrays must not move during the native call.")
    private static void avx2PartitionImpl(Object array, int elementType, int fromIndex, int toIndex, int[] pivotIndices, int pivotIndex1, int pivotIndex2) {
        SimdSortLibrary.avx2Partition(addressOfArray(array), elementType, fromIndex, toIndex, addressOfArray(pivotIndices), pivotIndex1, pivotIndex2);
    }

    @Uninterruptible(reason = "The array must not move during the native call.")
    private static void avx512SortImpl(Object array, int elementType, int fromIndex, int toIndex) {
        SimdSortLibrary.requireLibM();
        SimdSortLibrary.avx512Sort(addressOfArray(array), elementType, fromIndex, toIndex);
    }

    @Uninterruptible(reason = "The arrays must not move during the native call.")
    private static void avx512PartitionImpl(Object array, int elementType, int fromIndex, int toIndex, int[] pivotIndices, int pivotIndex1, int pivotIndex2) {
        SimdSortLibrary.avx512Partition(addressOfArray(array), elementType, fromIndex, toIndex, addressOfArray(pivotIndices), pivotIndex1, pivotIndex2);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static Pointer addressOfArray(Object array) {
        DynamicHub hub = DynamicHubIntrinsics.readHub(array);
        return Word.objectToUntrackedPointer(array).add(LayoutEncoding.getArrayBaseOffset(hub.getLayoutEncoding()));
    }
}

@TargetClass(className = "java.util.DualPivotQuicksort", innerClass = "SortOperation")
final class Target_java_util_DualPivotQuicksort_SortOperation {
    @Alias
    native void sort(Object array, int low, int high);
}

@TargetClass(className = "java.util.DualPivotQuicksort", innerClass = "PartitionOperation")
final class Target_java_util_DualPivotQuicksort_PartitionOperation {
    @Alias
    native int[] partition(Object array, int low, int high, int pivotIndex1, int pivotIndex2);
}
