/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.svm.core.graal.aarch64.SubstrateAArch64RegisterConfig;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.posix.UContextRegisterDumper;
import com.oracle.svm.core.posix.headers.Signal.GregsPointer;
import com.oracle.svm.core.posix.headers.Signal.mcontext_t;
import com.oracle.svm.core.posix.headers.Signal.ucontext_t;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.aarch64.AArch64;

@Platforms(Platform.LINUX_AARCH64.class)
@AutomaticFeature
class AArch64UContextRegisterDumperFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        VMError.guarantee(AArch64.r27.equals(SubstrateAArch64RegisterConfig.HEAP_BASE_REGISTER_CANDIDATE));
        VMError.guarantee(AArch64.r28.equals(SubstrateAArch64RegisterConfig.THREAD_REGISTER_CANDIDATE));
        ImageSingletons.add(RegisterDumper.class, new AArch64UContextRegisterDumper());
    }
}

class AArch64UContextRegisterDumper implements UContextRegisterDumper {
    @Override
    public void dumpRegisters(Log log, ucontext_t uContext) {
        mcontext_t sigcontext = uContext.uc_mcontext();
        GregsPointer regs = sigcontext.regs();
        dumpReg(log, "R0  ", regs.read(0));
        dumpReg(log, "R1  ", regs.read(1));
        dumpReg(log, "R2  ", regs.read(2));
        dumpReg(log, "R3  ", regs.read(3));
        dumpReg(log, "R4  ", regs.read(4));
        dumpReg(log, "R5  ", regs.read(5));
        dumpReg(log, "R6  ", regs.read(6));
        dumpReg(log, "R7  ", regs.read(7));
        dumpReg(log, "R8  ", regs.read(8));
        dumpReg(log, "R9  ", regs.read(9));
        dumpReg(log, "R10 ", regs.read(10));
        dumpReg(log, "R11 ", regs.read(11));
        dumpReg(log, "R12 ", regs.read(12));
        dumpReg(log, "R13 ", regs.read(13));
        dumpReg(log, "R14 ", regs.read(14));
        dumpReg(log, "R15 ", regs.read(15));
        dumpReg(log, "R16 ", regs.read(16));
        dumpReg(log, "R17 ", regs.read(17));
        dumpReg(log, "R18 ", regs.read(18));
        dumpReg(log, "R19 ", regs.read(19));
        dumpReg(log, "R20 ", regs.read(20));
        dumpReg(log, "R21 ", regs.read(21));
        dumpReg(log, "R22 ", regs.read(22));
        dumpReg(log, "R23 ", regs.read(23));
        dumpReg(log, "R24 ", regs.read(24));
        dumpReg(log, "R25 ", regs.read(25));
        dumpReg(log, "R26 ", regs.read(26));
        dumpReg(log, "R27 ", regs.read(27));
        dumpReg(log, "R28 ", regs.read(28));
        dumpReg(log, "R29 ", regs.read(29));
        dumpReg(log, "R30 ", regs.read(30));
        dumpReg(log, "R31 ", regs.read(31));
        dumpReg(log, "SP  ", sigcontext.sp());
        dumpReg(log, "PC  ", sigcontext.pc());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public PointerBase getHeapBase(ucontext_t uContext) {
        GregsPointer regs = uContext.uc_mcontext().regs();
        return WordFactory.pointer(regs.read(27));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public PointerBase getThreadPointer(ucontext_t uContext) {
        GregsPointer regs = uContext.uc_mcontext().regs();
        return WordFactory.pointer(regs.read(28));
    }

    @Override
    public PointerBase getSP(ucontext_t uContext) {
        mcontext_t sigcontext = uContext.uc_mcontext();
        return WordFactory.pointer(sigcontext.sp());
    }

    @Override
    public PointerBase getIP(ucontext_t uContext) {
        mcontext_t sigcontext = uContext.uc_mcontext();
        return WordFactory.pointer(sigcontext.pc());
    }
}
