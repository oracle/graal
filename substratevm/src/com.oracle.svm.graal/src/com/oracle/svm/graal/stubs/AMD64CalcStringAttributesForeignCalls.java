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

import org.graalvm.compiler.lir.amd64.AMD64CalcStringAttributesOp;
import org.graalvm.compiler.replacements.amd64.AMD64CalcStringAttributesNode;
import org.graalvm.nativeimage.Platform.AMD64;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.cpufeature.Stubs;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.graal.RuntimeCPUFeatureRegion;

@Platforms(AMD64.class)
class AMD64CalcStringAttributesForeignCalls {

    // GENERATED CODE BEGIN

    // GENERATED FROM:
    // compiler/src/org.graalvm.compiler.hotspot.amd64/src/org/graalvm/compiler/hotspot/amd64/AMD64CalcStringAttributesStub.java
    // BY: "mx svm-sync-graal-stubs"

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int calcStringAttributesLatin1(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.intReturnValue(array, offset, length, AMD64CalcStringAttributesOp.Op.LATIN1, false);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int calcStringAttributesBMP(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.intReturnValue(array, offset, length, AMD64CalcStringAttributesOp.Op.BMP, false);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static long calcStringAttributesUTF8Valid(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.longReturnValue(array, offset, length, AMD64CalcStringAttributesOp.Op.UTF_8, true);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static long calcStringAttributesUTF8Unknown(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.longReturnValue(array, offset, length, AMD64CalcStringAttributesOp.Op.UTF_8, false);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static long calcStringAttributesUTF16Valid(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.longReturnValue(array, offset, length, AMD64CalcStringAttributesOp.Op.UTF_16, true);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static long calcStringAttributesUTF16Unknown(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.longReturnValue(array, offset, length, AMD64CalcStringAttributesOp.Op.UTF_16, false);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int calcStringAttributesUTF32(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.intReturnValue(array, offset, length, AMD64CalcStringAttributesOp.Op.UTF_32, false);
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int calcStringAttributesLatin1RTC(Object array, long offset, int length) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return AMD64CalcStringAttributesNode.intReturnValue(array, offset, length, AMD64CalcStringAttributesOp.Op.LATIN1, false, Stubs.getRuntimeCheckedCPUFeatures());
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int calcStringAttributesBMPRTC(Object array, long offset, int length) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return AMD64CalcStringAttributesNode.intReturnValue(array, offset, length, AMD64CalcStringAttributesOp.Op.BMP, false, Stubs.getRuntimeCheckedCPUFeatures());
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static long calcStringAttributesUTF8ValidRTC(Object array, long offset, int length) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return AMD64CalcStringAttributesNode.longReturnValue(array, offset, length, AMD64CalcStringAttributesOp.Op.UTF_8, true, Stubs.getRuntimeCheckedCPUFeatures());
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static long calcStringAttributesUTF8UnknownRTC(Object array, long offset, int length) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return AMD64CalcStringAttributesNode.longReturnValue(array, offset, length, AMD64CalcStringAttributesOp.Op.UTF_8, false, Stubs.getRuntimeCheckedCPUFeatures());
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static long calcStringAttributesUTF16ValidRTC(Object array, long offset, int length) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return AMD64CalcStringAttributesNode.longReturnValue(array, offset, length, AMD64CalcStringAttributesOp.Op.UTF_16, true, Stubs.getRuntimeCheckedCPUFeatures());
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static long calcStringAttributesUTF16UnknownRTC(Object array, long offset, int length) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return AMD64CalcStringAttributesNode.longReturnValue(array, offset, length, AMD64CalcStringAttributesOp.Op.UTF_16, false, Stubs.getRuntimeCheckedCPUFeatures());
        } finally {
            region.leave();
        }
    }

    @Uninterruptible(reason = "Must not do a safepoint check.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    private static int calcStringAttributesUTF32RTC(Object array, long offset, int length) {
        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(Stubs.getRuntimeCheckedCPUFeatures());
        try {
            return AMD64CalcStringAttributesNode.intReturnValue(array, offset, length, AMD64CalcStringAttributesOp.Op.UTF_32, false, Stubs.getRuntimeCheckedCPUFeatures());
        } finally {
            region.leave();
        }
    }

    // GENERATED CODE END
}
