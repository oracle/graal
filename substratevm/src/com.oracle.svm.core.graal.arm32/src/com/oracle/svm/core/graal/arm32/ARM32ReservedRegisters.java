/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * ...
 */

// ファイルパス: substratevm/src/com.oracle.svm.core.graal.arm32/src/com/oracle/svm/core/graal/arm32/ARM32ReservedRegisters.java

package com.oracle.svm.core.graal.arm32;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.DisallowLayered;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.arm.ARM;

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = DisallowLayered.class)
public final class ARM32ReservedRegisters extends ReservedRegisters {

    // r9 is the Thread Pointer register in Linux ARM EABI (TP register)
    public static final Register THREAD_REGISTER = ARM.r9;

    // r8 is the Heap Base register candidate
    public static final Register HEAP_BASE_REGISTER_CANDIDATE = ARM.r8;

    @Platforms(Platform.HOSTED_ONLY.class)
    ARM32ReservedRegisters() {
        // super(stackPointer, threadRegister, heapBaseRegister, codeBaseRegister)
        // r13 = SP, r9 = thread pointer, r8 = heap base, null = no code base
        super(ARM.r13, THREAD_REGISTER, HEAP_BASE_REGISTER_CANDIDATE, null);
    }
}
