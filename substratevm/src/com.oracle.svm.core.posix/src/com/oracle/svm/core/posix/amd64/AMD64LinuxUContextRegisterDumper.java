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
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.RegisterDumper;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.graal.amd64.AMD64ReservedRegisters;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.posix.UContextRegisterDumper;
import com.oracle.svm.core.posix.headers.Signal.GregEnumLinuxAMD64;
import com.oracle.svm.core.posix.headers.Signal.GregsPointer;
import com.oracle.svm.core.posix.headers.Signal.ucontext_t;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.amd64.AMD64;

@Platforms({Platform.LINUX_AMD64.class})
@AutomaticFeature
class AMD64LinuxUContextRegisterDumperFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        VMError.guarantee(AMD64.r14.equals(AMD64ReservedRegisters.HEAP_BASE_REGISTER_CANDIDATE));
        VMError.guarantee(AMD64.r15.equals(AMD64ReservedRegisters.THREAD_REGISTER_CANDIDATE));
        ImageSingletons.add(RegisterDumper.class, new AMD64LinuxUContextRegisterDumper());
    }
}

class AMD64LinuxUContextRegisterDumper implements UContextRegisterDumper {
    @Override
    public void dumpRegisters(Log log, ucontext_t uContext, boolean printLocationInfo, boolean allowJavaHeapAccess, boolean allowUnsafeOperations) {
        GregsPointer gregs = uContext.uc_mcontext_linux_amd64_gregs();
        dumpReg(log, "RAX ", gregs.read(GregEnumLinuxAMD64.REG_RAX()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RBX ", gregs.read(GregEnumLinuxAMD64.REG_RBX()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RCX ", gregs.read(GregEnumLinuxAMD64.REG_RCX()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RDX ", gregs.read(GregEnumLinuxAMD64.REG_RDX()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RBP ", gregs.read(GregEnumLinuxAMD64.REG_RBP()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RSI ", gregs.read(GregEnumLinuxAMD64.REG_RSI()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RDI ", gregs.read(GregEnumLinuxAMD64.REG_RDI()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RSP ", gregs.read(GregEnumLinuxAMD64.REG_RSP()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R8  ", gregs.read(GregEnumLinuxAMD64.REG_R8()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R9  ", gregs.read(GregEnumLinuxAMD64.REG_R9()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R10 ", gregs.read(GregEnumLinuxAMD64.REG_R10()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R11 ", gregs.read(GregEnumLinuxAMD64.REG_R11()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R12 ", gregs.read(GregEnumLinuxAMD64.REG_R12()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R13 ", gregs.read(GregEnumLinuxAMD64.REG_R13()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R14 ", gregs.read(GregEnumLinuxAMD64.REG_R14()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R15 ", gregs.read(GregEnumLinuxAMD64.REG_R15()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "EFL ", gregs.read(GregEnumLinuxAMD64.REG_EFL()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RIP ", gregs.read(GregEnumLinuxAMD64.REG_RIP()), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true, calleeMustBe = false)
    public PointerBase getHeapBase(ucontext_t uContext) {
        GregsPointer gregs = uContext.uc_mcontext_linux_amd64_gregs();
        return WordFactory.pointer(gregs.read(GregEnumLinuxAMD64.REG_R14()));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true, calleeMustBe = false)
    public PointerBase getThreadPointer(ucontext_t uContext) {
        GregsPointer gregs = uContext.uc_mcontext_linux_amd64_gregs();
        return WordFactory.pointer(gregs.read(GregEnumLinuxAMD64.REG_R15()));
    }

    @Override
    public PointerBase getSP(ucontext_t uContext) {
        GregsPointer gregs = uContext.uc_mcontext_linux_amd64_gregs();
        return WordFactory.pointer(gregs.read(GregEnumLinuxAMD64.REG_RSP()));
    }

    @Override
    public PointerBase getIP(ucontext_t uContext) {
        GregsPointer gregs = uContext.uc_mcontext_linux_amd64_gregs();
        return WordFactory.pointer(gregs.read(GregEnumLinuxAMD64.REG_RIP()));
    }
}
