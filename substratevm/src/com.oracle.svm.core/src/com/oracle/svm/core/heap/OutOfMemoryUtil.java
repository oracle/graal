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
package com.oracle.svm.core.heap;

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.heap.dump.HeapDumping;
import com.oracle.svm.core.jdk.JDKUtils;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

/**
 * This class must be used for {@link OutOfMemoryError}s that are thrown because the VM is out of
 * Java heap memory. Other {@link OutOfMemoryError}s (e.g., when we run out of native memory) can be
 * thrown directly.
 */
public class OutOfMemoryUtil {
    private static final OutOfMemoryError OUT_OF_MEMORY_ERROR = new OutOfMemoryError("Garbage-collected heap size exceeded. Consider increasing the maximum Java heap size, for example with '-Xmx'.");

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Can't allocate when out of memory.")
    public static OutOfMemoryError heapSizeExceeded() {
        return reportOutOfMemoryError(OUT_OF_MEMORY_ERROR);
    }

    @Uninterruptible(reason = "Not uninterruptible but it doesn't matter for the callers.", calleeMustBe = false)
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Can't allocate while out of memory.")
    public static OutOfMemoryError reportOutOfMemoryError(OutOfMemoryError error) {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            reportOutOfMemoryError0(error);
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
        throw error;
    }

    @Uninterruptible(reason = "Not uninterruptible but it doesn't matter for the callers.", calleeMustBe = false)
    private static void reportOutOfMemoryError0(OutOfMemoryError error) {
        if (VMOperation.isGCInProgress()) {
            /* If a GC is in progress, then we can't execute the more complex logic below. */
            return;
        }

        if (VMInspectionOptions.hasHeapDumpSupport() && SubstrateOptions.HeapDumpOnOutOfMemoryError.getValue()) {
            HeapDumping.singleton().dumpHeapOnOutOfMemoryError();
        }

        if (SubstrateGCOptions.ExitOnOutOfMemoryError.getValue()) {
            if (LibC.isSupported()) {
                Log.log().string("Terminating due to java.lang.OutOfMemoryError: ").string(JDKUtils.getRawMessage(error)).newline();
                LibC.exit(3);
            } else {
                VMError.shouldNotReachHere("ExitOnOutOfMemoryError can only be used if the LibC support is present.");
            }
        }

        if (SubstrateGCOptions.ReportFatalErrorOnOutOfMemoryError.getValue()) {
            throw VMError.shouldNotReachHere("reporting due to java.lang.OutOfMemoryError");
        }
    }
}
