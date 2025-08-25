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
package com.oracle.svm.core.posix.riscv64;

import static com.oracle.svm.core.RegisterDumper.dumpReg;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.RegisterDumper;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.graal.riscv64.RISCV64ReservedRegisters;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.posix.UContextRegisterDumper;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.Signal.GregsPointer;
import com.oracle.svm.core.posix.headers.Signal.mcontext_linux_riscv64_t;
import com.oracle.svm.core.posix.headers.Signal.ucontext_t;
import com.oracle.svm.core.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Disallowed;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.word.Word;
import jdk.vm.ci.riscv64.RISCV64;

@AutomaticallyRegisteredImageSingleton(RegisterDumper.class)
@Platforms(Platform.LINUX_RISCV64.class)
@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = Disallowed.class)
class RISCV64LinuxUContextRegisterDumper implements UContextRegisterDumper {
    RISCV64LinuxUContextRegisterDumper() {
        VMError.guarantee(RISCV64.x27.equals(RISCV64ReservedRegisters.HEAP_BASE_REGISTER_CANDIDATE));
        VMError.guarantee(RISCV64.x23.equals(RISCV64ReservedRegisters.THREAD_REGISTER));
    }

    @Override
    public void dumpRegisters(Log log, Signal.ucontext_t uContext, boolean printLocationInfo, boolean allowJavaHeapAccess, boolean allowUnsafeOperations) {
        mcontext_linux_riscv64_t sigcontext = uContext.uc_mcontext_linux_riscv64();
        GregsPointer gregs = sigcontext.gregs();
        dumpReg(log, "PC  ", gregs.read(0), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X1  ", gregs.read(1), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X2  ", gregs.read(2), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X3  ", gregs.read(3), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X4  ", gregs.read(4), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X5  ", gregs.read(5), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X6  ", gregs.read(6), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X7  ", gregs.read(7), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X8  ", gregs.read(8), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X9  ", gregs.read(9), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X10 ", gregs.read(10), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X11 ", gregs.read(11), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X12 ", gregs.read(12), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X13 ", gregs.read(13), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X14 ", gregs.read(14), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X15 ", gregs.read(15), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X16 ", gregs.read(16), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X17 ", gregs.read(17), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X18 ", gregs.read(18), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X19 ", gregs.read(19), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X20 ", gregs.read(20), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X21 ", gregs.read(21), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X22 ", gregs.read(22), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X23 ", gregs.read(23), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X24 ", gregs.read(24), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X25 ", gregs.read(25), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X26 ", gregs.read(26), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X27 ", gregs.read(27), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X28 ", gregs.read(28), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X29 ", gregs.read(29), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X30 ", gregs.read(30), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "X31 ", gregs.read(31), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public PointerBase getHeapBase(ucontext_t uContext) {
        GregsPointer regs = uContext.uc_mcontext_linux_riscv64().gregs();
        return Word.pointer(regs.read(27));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public PointerBase getThreadPointer(ucontext_t uContext) {
        GregsPointer regs = uContext.uc_mcontext_linux_riscv64().gregs();
        return Word.pointer(regs.read(23));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public PointerBase getSP(ucontext_t uContext) {
        GregsPointer regs = uContext.uc_mcontext_linux_riscv64().gregs();
        return Word.pointer(regs.read(2));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public PointerBase getIP(ucontext_t uContext) {
        // gregs[0] holds the program counter.
        GregsPointer regs = uContext.uc_mcontext_linux_riscv64().gregs();
        return Word.pointer(regs.read(0));
    }
}
