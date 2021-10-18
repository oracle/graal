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
package com.oracle.svm.core.posix.amd64;

import static com.oracle.svm.core.RegisterDumper.dumpReg;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.RegisterDumper;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.graal.amd64.AMD64ReservedRegisters;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.posix.UContextRegisterDumper;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.Signal.ucontext_t;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.amd64.AMD64;

@Platforms({Platform.DARWIN_AMD64.class})
@AutomaticFeature
class AMD64DarwinUContextRegisterDumperFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        VMError.guarantee(AMD64.r14.equals(AMD64ReservedRegisters.HEAP_BASE_REGISTER_CANDIDATE));
        VMError.guarantee(AMD64.r15.equals(AMD64ReservedRegisters.THREAD_REGISTER_CANDIDATE));
        ImageSingletons.add(RegisterDumper.class, new AMD64DarwinUContextRegisterDumper());
    }
}

class AMD64DarwinUContextRegisterDumper implements UContextRegisterDumper {
    @Override
    public void dumpRegisters(Log log, ucontext_t uContext, boolean printLocationInfo, boolean allowJavaHeapAccess, boolean allowUnsafeOperations) {
        Signal.AMD64DarwinMContext64 sigcontext = uContext.uc_mcontext_darwin_amd64();
        dumpReg(log, "RAX ", ((Pointer) sigcontext).readLong(sigcontext.rax_offset()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RBX ", ((Pointer) sigcontext).readLong(sigcontext.rbx_offset()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RCX ", ((Pointer) sigcontext).readLong(sigcontext.rcx_offset()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RDX ", ((Pointer) sigcontext).readLong(sigcontext.rdx_offset()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RBP ", ((Pointer) sigcontext).readLong(sigcontext.rbp_offset()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RSI ", ((Pointer) sigcontext).readLong(sigcontext.rsi_offset()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RDI ", ((Pointer) sigcontext).readLong(sigcontext.rdi_offset()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RSP ", ((Pointer) sigcontext).readLong(sigcontext.rsp_offset()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R8  ", ((Pointer) sigcontext).readLong(sigcontext.r8_offset()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R9  ", ((Pointer) sigcontext).readLong(sigcontext.r9_offset()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R10 ", ((Pointer) sigcontext).readLong(sigcontext.r10_offset()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R11 ", ((Pointer) sigcontext).readLong(sigcontext.r11_offset()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R12 ", ((Pointer) sigcontext).readLong(sigcontext.r12_offset()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R13 ", ((Pointer) sigcontext).readLong(sigcontext.r13_offset()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R14 ", ((Pointer) sigcontext).readLong(sigcontext.r14_offset()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R15 ", ((Pointer) sigcontext).readLong(sigcontext.r15_offset()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "EFL ", ((Pointer) sigcontext).readLong(sigcontext.efl_offset()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RIP ", ((Pointer) sigcontext).readLong(sigcontext.rip_offset()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public PointerBase getHeapBase(ucontext_t uContext) {
        Signal.AMD64DarwinMContext64 sigcontext = uContext.uc_mcontext_darwin_amd64();
        return ((Pointer) sigcontext).readWord(sigcontext.r14_offset());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public PointerBase getThreadPointer(ucontext_t uContext) {
        Signal.AMD64DarwinMContext64 sigcontext = uContext.uc_mcontext_darwin_amd64();
        return ((Pointer) sigcontext).readWord(sigcontext.r15_offset());
    }

    @Override
    public PointerBase getSP(ucontext_t uContext) {
        Signal.AMD64DarwinMContext64 sigcontext = uContext.uc_mcontext_darwin_amd64();
        return ((Pointer) sigcontext).readWord(sigcontext.rsp_offset());
    }

    @Override
    public PointerBase getIP(ucontext_t uContext) {
        Signal.AMD64DarwinMContext64 sigcontext = uContext.uc_mcontext_darwin_amd64();
        return ((Pointer) sigcontext).readWord(sigcontext.rip_offset());
    }
}
