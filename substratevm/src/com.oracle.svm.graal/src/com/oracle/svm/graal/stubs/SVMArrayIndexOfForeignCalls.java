/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.stubs;

import static org.graalvm.compiler.core.common.StrideUtil.NONE;
import static org.graalvm.compiler.core.common.StrideUtil.S1;
import static org.graalvm.compiler.core.common.StrideUtil.S2;
import static org.graalvm.compiler.core.common.StrideUtil.S4;

import org.graalvm.compiler.replacements.nodes.ArrayIndexOfNode;
import org.graalvm.nativeimage.Platform.AARCH64;
import org.graalvm.nativeimage.Platform.AMD64;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.cpufeature.Stubs;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.graal.RuntimeCPUFeatureRegion;

@Platforms({AMD64.class, AARCH64.class})
class SVMArrayIndexOfForeignCalls {

    // GENERATED CODE BEGIN

    // GENERATED FROM:
    // compiler/src/org.graalvm.compiler.hotspot/src/org/graalvm/compiler/hotspot/ArrayIndexOfStub.java
    // BY: "mx svm-sync-graal-stubs"

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveBS1(byte[] array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S1, true, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveBS2(byte[] array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S2, true, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveCS2(char[] array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S2, S2, true, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfB1S1(byte[] array, long offset, int arrayLength, int fromIndex, int v1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S1, false, false, array, offset, arrayLength, fromIndex, v1);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfB2S1(byte[] array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S1, false, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfB3S1(byte[] array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S1, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfB4S1(byte[] array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S1, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfB1S2(byte[] array, long offset, int arrayLength, int fromIndex, int v1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S2, false, false, array, offset, arrayLength, fromIndex, v1);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfB2S2(byte[] array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S2, false, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfB3S2(byte[] array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S2, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfB4S2(byte[] array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S2, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfC1S2(char[] array, long offset, int arrayLength, int fromIndex, int v1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S2, S2, false, false, array, offset, arrayLength, fromIndex, v1);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfC2S2(char[] array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S2, S2, false, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfC3S2(char[] array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S2, S2, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfC4S2(char[] array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S2, S2, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveS1(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S1, true, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveS2(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S2, true, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveS4(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S4, true, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf1S1(Object array, long offset, int arrayLength, int fromIndex, int v1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S1, false, false, array, offset, arrayLength, fromIndex, v1);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf2S1(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S1, false, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf3S1(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S1, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf4S1(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S1, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf1S2(Object array, long offset, int arrayLength, int fromIndex, int v1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S2, false, false, array, offset, arrayLength, fromIndex, v1);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf2S2(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S2, false, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf3S2(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S2, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf4S2(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S2, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf1S4(Object array, long offset, int arrayLength, int fromIndex, int v1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S4, false, false, array, offset, arrayLength, fromIndex, v1);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf2S4(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S4, false, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf3S4(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S4, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf4S4(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S4, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfWithMaskBS1(byte[] array, long offset, int length, int fromIndex, int v1, int mask1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S1, false, true, array, offset, length, fromIndex, v1, mask1);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfWithMaskBS2(byte[] array, long offset, int length, int fromIndex, int v1, int mask1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S2, false, true, array, offset, length, fromIndex, v1, mask1);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveWithMaskBS1(byte[] array, long offset, int length, int fromIndex, int v1, int v2, int mask1, int mask2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S1, true, true, array, offset, length, fromIndex, v1, v2, mask1, mask2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveWithMaskBS2(byte[] array, long offset, int length, int fromIndex, int v1, int v2, int mask1, int mask2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S2, true, true, array, offset, length, fromIndex, v1, v2, mask1, mask2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfWithMaskCS2(char[] array, long offset, int length, int fromIndex, int v1, int mask1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S2, S2, false, true, array, offset, length, fromIndex, v1, mask1);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveWithMaskCS2(char[] array, long offset, int length, int fromIndex, int v1, int v2, int mask1, int mask2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(S2, S2, true, true, array, offset, length, fromIndex, v1, v2, mask1, mask2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfWithMaskS1(Object array, long offset, int length, int fromIndex, int v1, int mask1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S1, false, true, array, offset, length, fromIndex, v1, mask1);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfWithMaskS2(Object array, long offset, int length, int fromIndex, int v1, int mask1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S2, false, true, array, offset, length, fromIndex, v1, mask1);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfWithMaskS4(Object array, long offset, int length, int fromIndex, int v1, int mask1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S4, false, true, array, offset, length, fromIndex, v1, mask1);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveWithMaskS1(Object array, long offset, int length, int fromIndex, int v1, int v2, int mask1, int mask2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S1, true, true, array, offset, length, fromIndex, v1, v2, mask1, mask2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveWithMaskS2(Object array, long offset, int length, int fromIndex, int v1, int v2, int mask1, int mask2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S2, true, true, array, offset, length, fromIndex, v1, v2, mask1, mask2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveWithMaskS4(Object array, long offset, int length, int fromIndex, int v1, int v2, int mask1, int mask2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S4, true, true, array, offset, length, fromIndex, v1, v2, mask1, mask2);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveBS1RTC(byte[] array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S1, true, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveBS2RTC(byte[] array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S2, true, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveCS2RTC(char[] array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(S2, S2, true, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfB1S1RTC(byte[] array, long offset, int arrayLength, int fromIndex, int v1) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S1, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfB2S1RTC(byte[] array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S1, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfB3S1RTC(byte[] array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S1, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2, v3);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfB4S1RTC(byte[] array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S1, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2, v3, v4);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfB1S2RTC(byte[] array, long offset, int arrayLength, int fromIndex, int v1) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S2, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfB2S2RTC(byte[] array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S2, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfB3S2RTC(byte[] array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S2, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2, v3);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfB4S2RTC(byte[] array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S2, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2, v3, v4);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfC1S2RTC(char[] array, long offset, int arrayLength, int fromIndex, int v1) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(S2, S2, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfC2S2RTC(char[] array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(S2, S2, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfC3S2RTC(char[] array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(S2, S2, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2, v3);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfC4S2RTC(char[] array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(S2, S2, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2, v3, v4);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveS1RTC(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S1, true, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveS2RTC(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S2, true, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveS4RTC(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S4, true, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf1S1RTC(Object array, long offset, int arrayLength, int fromIndex, int v1) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S1, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf2S1RTC(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S1, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf3S1RTC(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S1, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2, v3);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf4S1RTC(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S1, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2, v3, v4);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf1S2RTC(Object array, long offset, int arrayLength, int fromIndex, int v1) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S2, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf2S2RTC(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S2, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf3S2RTC(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S2, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2, v3);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf4S2RTC(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S2, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2, v3, v4);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf1S4RTC(Object array, long offset, int arrayLength, int fromIndex, int v1) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S4, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf2S4RTC(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S4, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf3S4RTC(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S4, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2, v3);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOf4S4RTC(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S4, false, false, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, arrayLength, fromIndex, v1, v2, v3, v4);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfWithMaskBS1RTC(byte[] array, long offset, int length, int fromIndex, int v1, int mask1) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S1, false, true, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, length, fromIndex, v1, mask1);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfWithMaskBS2RTC(byte[] array, long offset, int length, int fromIndex, int v1, int mask1) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S2, false, true, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, length, fromIndex, v1, mask1);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveWithMaskBS1RTC(byte[] array, long offset, int length, int fromIndex, int v1, int v2, int mask1, int mask2) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S1, true, true, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, length, fromIndex, v1, v2, mask1, mask2);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveWithMaskBS2RTC(byte[] array, long offset, int length, int fromIndex, int v1, int v2, int mask1, int mask2) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(S1, S2, true, true, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, length, fromIndex, v1, v2, mask1, mask2);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfWithMaskCS2RTC(char[] array, long offset, int length, int fromIndex, int v1, int mask1) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(S2, S2, false, true, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, length, fromIndex, v1, mask1);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveWithMaskCS2RTC(char[] array, long offset, int length, int fromIndex, int v1, int v2, int mask1, int mask2) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(S2, S2, true, true, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, length, fromIndex, v1, v2, mask1, mask2);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfWithMaskS1RTC(Object array, long offset, int length, int fromIndex, int v1, int mask1) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S1, false, true, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, length, fromIndex, v1, mask1);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfWithMaskS2RTC(Object array, long offset, int length, int fromIndex, int v1, int mask1) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S2, false, true, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, length, fromIndex, v1, mask1);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfWithMaskS4RTC(Object array, long offset, int length, int fromIndex, int v1, int mask1) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S4, false, true, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, length, fromIndex, v1, mask1);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveWithMaskS1RTC(Object array, long offset, int length, int fromIndex, int v1, int v2, int mask1, int mask2) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S1, true, true, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, length, fromIndex, v1, v2, mask1, mask2);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveWithMaskS2RTC(Object array, long offset, int length, int fromIndex, int v1, int v2, int mask1, int mask2) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S2, true, true, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, length, fromIndex, v1, v2, mask1, mask2);
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int indexOfTwoConsecutiveWithMaskS4RTC(Object array, long offset, int length, int fromIndex, int v1, int v2, int mask1, int mask2) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return ArrayIndexOfNode.optimizedArrayIndexOf(NONE, S4, true, true, Stubs.getRuntimeCheckedCPUFeatures(), array, offset, length, fromIndex, v1, v2, mask1, mask2);
        } finally {
            region.leave();
        }
    }

    // GENERATED CODE END
}
