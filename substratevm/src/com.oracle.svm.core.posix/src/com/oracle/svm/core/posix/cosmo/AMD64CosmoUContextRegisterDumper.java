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
package com.oracle.svm.core.posix.cosmo;

import com.oracle.svm.core.RegisterDumper;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.graal.amd64.AMD64ReservedRegisters;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.posix.cosmo.headers.Signal;
import com.oracle.svm.core.posix.cosmo.headers.Signal.ucontext_t;
import com.oracle.svm.core.util.VMError;
import org.graalvm.word.impl.Word;
import jdk.vm.ci.amd64.AMD64;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.PointerBase;

import static com.oracle.svm.core.RegisterDumper.dumpReg;

@AutomaticallyRegisteredImageSingleton(value = RegisterDumper.class, onlyWith = CosmoLibCSupplier.class)
@Platforms(Platform.LINUX_AMD64.class)
class AMD64CosmoUContextRegisterDumper implements UContextRegisterDumper {
    AMD64CosmoUContextRegisterDumper() {
        VMError.guarantee(AMD64.r14.equals(AMD64ReservedRegisters.HEAP_BASE_REGISTER));
        VMError.guarantee(AMD64.r15.equals(AMD64ReservedRegisters.THREAD_REGISTER));
    }

    @Override
    public void dumpRegisters(Log log, ucontext_t uContext, boolean printLocationInfo, boolean allowJavaHeapAccess, boolean allowUnsafeOperations) {
        Signal.mcontext_linux_x86_64_t mContext = uContext.uc_mcontext_linux_x86_64();
        dumpReg(log, "RAX ", mContext.rax(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RBX ", mContext.rbx(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RCX ", mContext.rcx(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RDX ", mContext.rdx(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RBP ", mContext.rbp(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RSI ", mContext.rsi(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RDI ", mContext.rdi(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RSP ", mContext.rsp(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R8  ", mContext.r8(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R9  ", mContext.r9(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R10 ", mContext.r10(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R11 ", mContext.r11(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R12 ", mContext.r12(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R13 ", mContext.r13(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R14 ", mContext.r14(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R15 ", mContext.r15(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "EFL ", mContext.eflags(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RIP ", mContext.rip(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public PointerBase getHeapBase(ucontext_t uContext) {
        return Word.pointer(uContext.uc_mcontext_linux_x86_64().r14());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public PointerBase getThreadPointer(ucontext_t uContext) {
        return Word.pointer(uContext.uc_mcontext_linux_x86_64().r15());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public PointerBase getSP(ucontext_t uContext) {
        return Word.pointer(uContext.uc_mcontext_linux_x86_64().rsp());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public PointerBase getIP(ucontext_t uContext) {
        return Word.pointer(uContext.uc_mcontext_linux_x86_64().rip());
    }
}
