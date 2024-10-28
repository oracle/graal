package com.oracle.svm.core.debug;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.InstalledCodeObserver;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.debug.DebugContext;
import jdk.vm.ci.meta.MetaAccessProvider;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

public class SubstrateDebugInfoInstaller implements InstalledCodeObserver {

    private final SubstrateDebugInfoProvider substrateDebugInfoProvider;

    static final class Factory implements InstalledCodeObserver.Factory {

        private final MetaAccessProvider metaAccess;
        private final RuntimeConfiguration runtimeConfiguration;

        Factory(MetaAccessProvider metaAccess, RuntimeConfiguration runtimeConfiguration) {
            this.metaAccess = metaAccess;
            this.runtimeConfiguration = runtimeConfiguration;
        }

        @Override
        public InstalledCodeObserver create(DebugContext debugContext, SharedMethod method, CompilationResult compilation, Pointer code, int codeSize) {
            try {
                return new SubstrateDebugInfoInstaller(debugContext, method, compilation, metaAccess, runtimeConfiguration, code, codeSize);
            } catch (Throwable t) {
                throw VMError.shouldNotReachHere(t);
            }
        }
    }

    private SubstrateDebugInfoInstaller(DebugContext debugContext, SharedMethod method, CompilationResult compilation, MetaAccessProvider metaAccess, RuntimeConfiguration runtimeConfiguration, Pointer code, int codeSize) {
        substrateDebugInfoProvider = new SubstrateDebugInfoProvider(debugContext, method, compilation, runtimeConfiguration, code.rawValue(), codeSize);
    }

    @RawStructure
    private interface Handle extends InstalledCodeObserverHandle {
        int INITIALIZED = 0;
        int ACTIVATED = 1;
        int RELEASED = 2;

        @RawField
        GDBJITInterfaceSystemJava.JITCodeEntry getRawHandle();

        @RawField
        void setRawHandle(GDBJITInterfaceSystemJava.JITCodeEntry value);

        @RawField
        NonmovableArray<Byte> getDebugInfoData();

        @RawField
        void setDebugInfoData(NonmovableArray<Byte> data);

        @RawField
        int getState();

        @RawField
        void setState(int value);
    }

    static final class Accessor implements InstalledCodeObserverHandleAccessor {

        static Handle createHandle(NonmovableArray<Byte> debugInfoData) {
            Handle handle = UnmanagedMemory.malloc(SizeOf.get(Handle.class));
            GDBJITInterfaceSystemJava.JITCodeEntry entry = UnmanagedMemory.calloc(SizeOf.get(GDBJITInterfaceSystemJava.JITCodeEntry.class));
            handle.setAccessor(ImageSingletons.lookup(Accessor.class));
            handle.setRawHandle(entry);
            handle.setDebugInfoData(debugInfoData);
            handle.setState(Handle.INITIALIZED);
            return handle;
        }

        @Override
        public void activate(InstalledCodeObserverHandle installedCodeObserverHandle) {
            Handle handle = (Handle) installedCodeObserverHandle;
            VMOperation.guaranteeInProgress("SubstrateDebugInfoInstaller.Accessor.activate must run in a VMOperation");
            VMError.guarantee(handle.getState() == Handle.INITIALIZED);

            NonmovableArray<Byte> debugInfoData = handle.getDebugInfoData();
            CCharPointer address = NonmovableArrays.addressOf(debugInfoData, 0);
            int size = NonmovableArrays.lengthOf(debugInfoData);
            GDBJITInterfaceSystemJava.registerJITCode(address, size, handle.getRawHandle());

            handle.setState(Handle.ACTIVATED);
        }

        @Override
        public void release(InstalledCodeObserverHandle installedCodeObserverHandle) {
            Handle handle = (Handle) installedCodeObserverHandle;
            VMOperation.guaranteeInProgress("SubstrateDebugInfoInstaller.Accessor.release must run in a VMOperation");
            VMError.guarantee(handle.getState() == Handle.ACTIVATED);

            GDBJITInterfaceSystemJava.JITCodeEntry entry = handle.getRawHandle();
            GDBJITInterfaceSystemJava.unregisterJITCode(entry);

            handle.setState(Handle.RELEASED);
            NonmovableArrays.releaseUnmanagedArray(handle.getDebugInfoData());
            UnmanagedMemory.free(handle);
        }

        @Override
        public void detachFromCurrentIsolate(InstalledCodeObserverHandle installedCodeObserverHandle) {
            Handle handle = (Handle) installedCodeObserverHandle;
            NonmovableArrays.untrackUnmanagedArray(handle.getDebugInfoData());
        }

        @Override
        public void attachToCurrentIsolate(InstalledCodeObserverHandle installedCodeObserverHandle) {
            Handle handle = (Handle) installedCodeObserverHandle;
            NonmovableArrays.trackUnmanagedArray(handle.getDebugInfoData());
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void releaseOnTearDown(InstalledCodeObserverHandle installedCodeObserverHandle) {
            Handle handle = (Handle) installedCodeObserverHandle;
            if (handle.getState() == Handle.ACTIVATED) {
                GDBJITInterfaceSystemJava.JITCodeEntry entry = handle.getRawHandle();
                GDBJITInterfaceSystemJava.unregisterJITCode(entry);
                handle.setState(Handle.RELEASED);
            }
            NonmovableArrays.releaseUnmanagedArray(handle.getDebugInfoData());
            // UnmanagedMemory.free(handle); -> change because of Uninterruptible annotation
            ImageSingletons.lookup(UnmanagedMemorySupport.class).free(handle);
        }

        static String toString(Handle handle) {
            StringBuilder sb = new StringBuilder("DebugInfoHandle(handle = 0x");
            sb.append(Long.toHexString(handle.getRawHandle().rawValue()));
            sb.append(", address = 0x");
            sb.append(Long.toHexString(NonmovableArrays.addressOf(handle.getDebugInfoData(), 0).rawValue()));
            sb.append(", size = ");
            sb.append(NonmovableArrays.lengthOf(handle.getDebugInfoData()));
            sb.append(", handleState = ");
            sb.append(handle.getState());
            sb.append(")");
            return sb.toString();
        }
    }

    @Override
    public InstalledCodeObserverHandle install() {
        NonmovableArray<Byte> debugInfoData = substrateDebugInfoProvider.writeDebugInfoData();
        Handle handle = Accessor.createHandle(debugInfoData);
        System.out.println(Accessor.toString(handle));
        return handle;
    }
}
