package com.oracle.svm.core.foreign;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.internal.foreign.MemorySessionImpl;

@TargetClass(className = "jdk.internal.foreign.abi.UpcallStubs")
public final class Target_jdk_internal_foreign_abi_UpcallStubs {
    @Substitute
    static MemorySegment makeUpcall(long entry, Arena arena) {
        MemorySessionImpl.toMemorySession(arena).addOrCleanupIfFail(new MemorySessionImpl.ResourceList.ResourceCleanup() {
            @Override
            public void cleanup() {
                ForeignFunctionsRuntime.singleton().freeTrampoline(entry);
            }
        });
        return MemorySegment.ofAddress(entry).reinterpret(arena, null);
    }
}