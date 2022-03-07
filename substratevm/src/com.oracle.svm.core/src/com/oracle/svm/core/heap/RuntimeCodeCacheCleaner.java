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
package com.oracle.svm.core.heap;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.RuntimeCodeCache.CodeInfoVisitor;
import com.oracle.svm.core.graal.meta.SharedRuntimeMethod;

import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

import com.oracle.svm.core.code.RuntimeCodeInfoAccess;
import com.oracle.svm.core.code.RuntimeCodeInfoHistory;
import com.oracle.svm.core.code.RuntimeCodeInfoMemory;

/**
 * Cleans the code cache and frees the unmanaged memory that is used by {@link CodeInfo} objects.
 * Furthermore, it also actively invalidates and frees code that has references to otherwise no
 * longer reachable Java heap objects.
 */
public final class RuntimeCodeCacheCleaner implements CodeInfoVisitor {
    /**
     * To avoid unnecessary deoptimizations, we need to assume that objects of the following classes
     * are reachable, even if they don't have any strong references:
     * <ul>
     * <li>{@link SpeculationReason} objects are embedded in the code and only needed when a
     * deoptimization is triggered.</li>
     * <li>{@link SharedRuntimeMethod} objects are sometimes used as artifical methods (e.g., for
     * adapter code) and are located in the frame info object constants.</li>
     * </ul>
     */
    public static final Class<?>[] CLASSES_ASSUMED_REACHABLE = {SpeculationReason.class, SharedRuntimeMethod.class};

    @Platforms(Platform.HOSTED_ONLY.class)
    public RuntimeCodeCacheCleaner() {
    }

    @Override
    public <T extends CodeInfo> boolean visitCode(T codeInfo) {
        if (RuntimeCodeInfoAccess.areAllObjectsOnImageHeap(codeInfo)) {
            return true;
        }

        int state = CodeInfoAccess.getState(codeInfo);
        if (state == CodeInfo.STATE_UNREACHABLE) {
            freeMemory(codeInfo);
        } else if (state == CodeInfo.STATE_READY_FOR_INVALIDATION) {
            // All objects that are accessed during invalidation must still be reachable.
            CodeInfoTable.invalidateNonStackCodeAtSafepoint(codeInfo);
            assert CodeInfoAccess.getState(codeInfo) == CodeInfo.STATE_PARTIALLY_FREED;
            freeMemory(codeInfo);
        }
        return true;
    }

    private static void freeMemory(CodeInfo codeInfo) {
        boolean removed = RuntimeCodeInfoMemory.singleton().removeDuringGC(codeInfo);
        assert removed : "must have been present";
        RuntimeCodeInfoHistory.singleton().logFree(codeInfo);
        RuntimeCodeInfoAccess.free(codeInfo, false);
    }
}
