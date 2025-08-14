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

public class GdbJitAccessor implements InstalledCodeObserver.InstalledCodeObserverHandleAccessor {

    @SuppressWarnings("try")
    public static GdbJitHandle createHandle(DebugContext debug, NonmovableArray<Byte> debugInfoData) {
        GdbJitHandle handle = NativeMemory.malloc(SizeOf.get(GdbJitHandle.class), NmtCategory.Code);
        GdbJitInterface.JITCodeEntry entry = NativeMemory.calloc(SizeOf.get(GdbJitInterface.JITCodeEntry.class), NmtCategory.Code);
        handle.setAccessor(singleton());
        handle.setRawHandle(entry);
        handle.setDebugInfoData(debugInfoData);
        handle.setState(GdbJitHandle.INITIALIZED);
        if (debug.isLogEnabled()) {
            try (DebugContext.Scope s = debug.scope("RuntimeCompilation")) {
                debug.log(toString(handle));
            }
        }
        return handle;
    }

    @Fold
    public static GdbJitAccessor singleton() {
        return ImageSingletons.lookup(GdbJitAccessor.class);
    }

    @Override
    public void activate(InstalledCodeObserver.InstalledCodeObserverHandle installedCodeObserverHandle) {
        GdbJitHandle handle = (GdbJitHandle) installedCodeObserverHandle;
        VMOperation.guaranteeInProgressAtSafepoint("SubstrateDebugInfoInstaller.Accessor.activate must run in a VMOperation");
        VMError.guarantee(handle.getState() == GdbJitHandle.INITIALIZED);

        NonmovableArray<Byte> debugInfoData = handle.getDebugInfoData();
        CCharPointer address = NonmovableArrays.addressOf(debugInfoData, 0);
        int size = NonmovableArrays.lengthOf(debugInfoData);
        GdbJitInterface.registerJITCode(address, size, handle.getRawHandle());

        handle.setState(GdbJitHandle.ACTIVATED);
    }

    @Override
    @Uninterruptible(reason = "Called during GC or teardown.")
    public void release(InstalledCodeObserver.InstalledCodeObserverHandle installedCodeObserverHandle) {
        GdbJitHandle handle = (GdbJitHandle) installedCodeObserverHandle;
        GdbJitInterface.JITCodeEntry entry = handle.getRawHandle();
        // Handle may still be just initialized here, so it never got registered in GDB.
        if (handle.getState() == GdbJitHandle.ACTIVATED) {
            GdbJitInterface.unregisterJITCode(entry);
            handle.setState(GdbJitHandle.RELEASED);
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
