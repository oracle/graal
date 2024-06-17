/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.isolated;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.InstalledCodeObserverSupport;
import com.oracle.svm.core.code.RuntimeCodeInfoAccess;
import com.oracle.svm.core.code.RuntimeCodeInfoMemory;
import com.oracle.svm.core.nmt.NativeMemoryTracking;

final class IsolatedRuntimeMethodInfoAccess {

    public static void startTrackingInCurrentIsolate(CodeInstallInfo installInfo) {
        CodeInfo info = installInfo.getCodeInfo();
        InstalledCodeObserverSupport.attachToCurrentIsolate(RuntimeCodeInfoAccess.getCodeObserverHandles(info));
        RuntimeCodeInfoMemory.singleton().add(info);

        /* NonmovableArray tracking and native memory tracking. */
        RuntimeCodeInfoAccess.forEachArray(info, TRACK_ACTION);

        ForeignIsolateReferenceAdjusterData adjusterData = installInfo.getAdjusterData();
        NonmovableArrays.trackUnmanagedArray(adjusterData.getAddresses());
        NonmovableArrays.trackUnmanagedArray(adjusterData.getHandles());

        if (VMInspectionOptions.hasNativeMemoryTrackingSupport()) {
            NativeMemoryTracking.singleton().track(installInfo);
            NativeMemoryTracking.singleton().track(installInfo.getAdjusterData());
            NativeMemoryTracking.singleton().track(installInfo.getCodeInfo());
            NativeMemoryTracking.singleton().track(adjusterData.getAddresses());
            NativeMemoryTracking.singleton().track(adjusterData.getHandles());
        }
    }

    public static void untrackInCurrentIsolate(CodeInstallInfo installInfo) {
        CodeInfo info = installInfo.getCodeInfo();
        RuntimeCodeInfoMemory.singleton().remove(info);
        InstalledCodeObserverSupport.detachFromCurrentIsolate(RuntimeCodeInfoAccess.getCodeObserverHandles(info));

        /* NonmovableArray tracking and native memory tracking. */
        RuntimeCodeInfoAccess.forEachArray(info, UNTRACK_ACTION);

        ForeignIsolateReferenceAdjusterData adjusterData = installInfo.getAdjusterData();
        NonmovableArrays.untrackUnmanagedArray(adjusterData.getAddresses());
        NonmovableArrays.untrackUnmanagedArray(adjusterData.getHandles());

        if (VMInspectionOptions.hasNativeMemoryTrackingSupport()) {
            NativeMemoryTracking.singleton().untrack(installInfo);
            NativeMemoryTracking.singleton().untrack(installInfo.getAdjusterData());
            NativeMemoryTracking.singleton().untrack(installInfo.getCodeInfo());
            NativeMemoryTracking.singleton().untrack(adjusterData.getAddresses());
            NativeMemoryTracking.singleton().untrack(adjusterData.getHandles());
        }
    }

    private static final RuntimeCodeInfoAccess.NonmovableArrayAction TRACK_ACTION = new RuntimeCodeInfoAccess.NonmovableArrayAction() {
        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void apply(NonmovableArray<?> array) {
            NonmovableArrays.trackUnmanagedArray(array);
            if (VMInspectionOptions.hasNativeMemoryTrackingSupport()) {
                NativeMemoryTracking.singleton().track(array);
            }
        }
    };
    private static final RuntimeCodeInfoAccess.NonmovableArrayAction UNTRACK_ACTION = new RuntimeCodeInfoAccess.NonmovableArrayAction() {
        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void apply(NonmovableArray<?> array) {
            NonmovableArrays.untrackUnmanagedArray(array);
            if (VMInspectionOptions.hasNativeMemoryTrackingSupport()) {
                NativeMemoryTracking.singleton().untrack(array);
            }
        }
    };

    private IsolatedRuntimeMethodInfoAccess() {
    }
}
