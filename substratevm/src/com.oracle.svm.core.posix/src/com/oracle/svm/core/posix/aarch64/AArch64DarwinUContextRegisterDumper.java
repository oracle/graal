/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.aarch64;

import static com.oracle.svm.core.RegisterDumper.dumpReg;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.RegisterDumper;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.graal.aarch64.AArch64ReservedRegisters;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.posix.UContextRegisterDumper;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.aarch64.AArch64;

@Platforms({Platform.DARWIN_AARCH64.class, Platform.IOS_AARCH64.class})
@AutomaticFeature
class AArch64DarwinUContextRegisterDumperFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        VMError.guarantee(AArch64.r27.equals(AArch64ReservedRegisters.HEAP_BASE_REGISTER_CANDIDATE));
        VMError.guarantee(AArch64.r28.equals(AArch64ReservedRegisters.THREAD_REGISTER_CANDIDATE));
        ImageSingletons.add(RegisterDumper.class, new AArch64DarwinUContextRegisterDumper());
    }
}

public class AArch64DarwinUContextRegisterDumper implements UContextRegisterDumper {
    @Override
    public void dumpRegisters(Log log, Signal.ucontext_t uContext, boolean printLocationInfo, boolean allowJavaHeapAccess, boolean allowUnsafeOperations) {
        Signal.AArch64DarwinMContext64 sigcontext = uContext.uc_mcontext_darwin_aarch64();
        Signal.GregsPointer regs = sigcontext.regs();
        dumpReg(log, "R0  ", regs.read(0), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R1  ", regs.read(1), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R2  ", regs.read(2), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R3  ", regs.read(3), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R4  ", regs.read(4), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R5  ", regs.read(5), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R6  ", regs.read(6), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R7  ", regs.read(7), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R8  ", regs.read(8), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R9  ", regs.read(9), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R10 ", regs.read(10), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R11 ", regs.read(11), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R12 ", regs.read(12), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R13 ", regs.read(13), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R14 ", regs.read(14), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R15 ", regs.read(15), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R16 ", regs.read(16), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R17 ", regs.read(17), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R18 ", regs.read(18), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R19 ", regs.read(19), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R20 ", regs.read(20), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R21 ", regs.read(21), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R22 ", regs.read(22), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R23 ", regs.read(23), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R24 ", regs.read(24), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R25 ", regs.read(25), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R26 ", regs.read(26), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R27 ", regs.read(27), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R28 ", regs.read(28), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R29 ", sigcontext.fp(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R30 ", sigcontext.lr(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "SP ", sigcontext.sp(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "PC ", sigcontext.pc(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public PointerBase getHeapBase(Signal.ucontext_t uContext) {
        Signal.AArch64DarwinMContext64 sigcontext = uContext.uc_mcontext_darwin_aarch64();
        return WordFactory.pointer(sigcontext.regs().read(27));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public PointerBase getThreadPointer(Signal.ucontext_t uContext) {
        Signal.AArch64DarwinMContext64 sigcontext = uContext.uc_mcontext_darwin_aarch64();
        return WordFactory.pointer(sigcontext.regs().read(28));
    }

    @Override
    public PointerBase getSP(Signal.ucontext_t uContext) {
        Signal.AArch64DarwinMContext64 sigcontext = uContext.uc_mcontext_darwin_aarch64();
        return WordFactory.pointer(sigcontext.sp());
    }

    @Override
    public PointerBase getIP(Signal.ucontext_t uContext) {
        Signal.AArch64DarwinMContext64 sigcontext = uContext.uc_mcontext_darwin_aarch64();
        return WordFactory.pointer(sigcontext.pc());
    }
}
