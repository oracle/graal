package com.oracle.svm.core.foreign;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "jdk.internal.foreign.abi.UpcallStubs")
public final class Target_jdk_internal_foreign_abi_UpcallStubs {
    /**
     * HotSpot has a mechanism which frees upcall stubs when the arena expires. We might leverage
     * this to free trampolines. Ignore this cleaning mechanism for now.
     */
    @Substitute
    static MemorySegment makeUpcall(long entry, Arena arena) {
        return MemorySegment.ofAddress(entry).reinterpret(arena, null);
    }
}