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

package com.oracle.svm.core.debug.gdb;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.InstalledCodeObserver;
import com.oracle.svm.core.memory.NativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.debug.DebugContext;

/**
 * The accessor for a {@link GdbJitHandle}.
 */
public class GdbJitHandleAccessor implements InstalledCodeObserver.InstalledCodeObserverHandleAccessor {

    @Fold
    public static GdbJitHandleAccessor singleton() {
        return ImageSingletons.lookup(GdbJitHandleAccessor.class);
    }

    /**
     * Creates and allocates a {@link GdbJitHandle} and a {@link GdbJitInterface.JITCodeEntry} in
     * native memory. The handle wraps the {@code GDB JITCodeEntry}, the corresponding runtime debug
     * info, and its state is set as {@link GdbJitHandle#INITIALIZED}.
     *
     * @param debug the {@link DebugContext} used for logging
     * @param debugInfoData the runtime debug info for a runtime compilation
     * @return the {@code GdbJitHandle} for the runtime debug info
     */
    public static InstalledCodeObserver.InstalledCodeObserverHandle createHandle(DebugContext debug, NonmovableArray<Byte> debugInfoData) {
        GdbJitHandle handle = NativeMemory.malloc(SizeOf.get(GdbJitHandle.class), NmtCategory.Code);
        GdbJitInterface.JITCodeEntry entry = NativeMemory.calloc(SizeOf.get(GdbJitInterface.JITCodeEntry.class), NmtCategory.Code);
        handle.setAccessor(singleton());
        handle.setRawHandle(entry);
        handle.setDebugInfoData(debugInfoData);
        handle.setState(GdbJitHandle.INITIALIZED);
        if (debug.isLogEnabled()) {
            try (DebugContext.Scope _ = debug.scope("RuntimeCompilation")) {
                debug.log(toString(handle));
            }
        }
        return handle;
    }

    /**
     * Registers debug info for a runtime compilation in GDB through
     * {@link GdbJitInterface#registerJITCode}.
     *
     * @param installedCodeObserverHandle the handle holding a {@code GDB JITCodeEntry} and the
     *            corresponding debug info
     */
    @Override
    public void activate(InstalledCodeObserver.InstalledCodeObserverHandle installedCodeObserverHandle) {
        GdbJitHandle handle = (GdbJitHandle) installedCodeObserverHandle;
        VMOperation.guaranteeInProgressAtSafepoint("GdbJitHandleAccessor.activate must run in a VMOperation");
        VMError.guarantee(handle.getState() == GdbJitHandle.INITIALIZED);

        NonmovableArray<Byte> debugInfoData = handle.getDebugInfoData();
        CCharPointer address = NonmovableArrays.addressOf(debugInfoData, 0);
        int size = NonmovableArrays.lengthOf(debugInfoData);
        GdbJitInterface.registerJITCode(address, size, handle.getRawHandle());

        handle.setState(GdbJitHandle.REGISTERED);
    }

    /**
     * Unregisters debug info for a runtime compilation in GDB through
     * {@link GdbJitInterface#unregisterJITCode}.
     * <p>
     * After unregistering in GDB all native memory used by the handle, {@code JITCodeEntry} and
     * runtime debug info is cleaned up.
     * <p>
     * If the handle was not registered yet, GDB is not notified and all native memory is
     * immediately cleaned up.
     *
     * @param installedCodeObserverHandle the handle holding a {@code GDB JITCodeEntry} and the
     *            corresponding debug info
     */
    @Override
    @Uninterruptible(reason = "Called during GC or teardown.")
    public void release(InstalledCodeObserver.InstalledCodeObserverHandle installedCodeObserverHandle) {
        GdbJitHandle handle = (GdbJitHandle) installedCodeObserverHandle;
        GdbJitInterface.JITCodeEntry entry = handle.getRawHandle();
        // Handle may still be just initialized here, so it never got registered in GDB.
        if (handle.getState() == GdbJitHandle.REGISTERED) {
            GdbJitInterface.unregisterJITCode(entry);
            handle.setState(GdbJitHandle.UNREGISTERED);
        }
        NativeMemory.free(entry);
        NonmovableArrays.releaseUnmanagedArray(handle.getDebugInfoData());
        NativeMemory.free(handle);
    }

    @Override
    public void detachFromCurrentIsolate(InstalledCodeObserver.InstalledCodeObserverHandle installedCodeObserverHandle) {
        GdbJitHandle handle = (GdbJitHandle) installedCodeObserverHandle;
        NonmovableArrays.untrackUnmanagedArray(handle.getDebugInfoData());
    }

    @Override
    public void attachToCurrentIsolate(InstalledCodeObserver.InstalledCodeObserverHandle installedCodeObserverHandle) {
        GdbJitHandle handle = (GdbJitHandle) installedCodeObserverHandle;
        NonmovableArrays.trackUnmanagedArray(handle.getDebugInfoData());
    }

    private static String toString(GdbJitHandle handle) {
        return "DebugInfoHandle(handle = 0x" + Long.toHexString(handle.getRawHandle().rawValue()) +
                        ", address = 0x" +
                        Long.toHexString(NonmovableArrays.addressOf(handle.getDebugInfoData(), 0).rawValue()) +
                        ", size = " +
                        NonmovableArrays.lengthOf(handle.getDebugInfoData()) +
                        ", handleState = " +
                        handle.getState() +
                        ")";
    }
}
