/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.windows;

import static com.oracle.svm.core.RegisterDumper.dumpReg;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.RegisterDumper;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.graal.amd64.AMD64ReservedRegisters;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.windows.headers.ErrHandlingAPI.CONTEXT;

import jdk.vm.ci.amd64.AMD64;

@AutomaticFeature
class WindowsRegisterDumperFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        VMError.guarantee(AMD64.r14.equals(AMD64ReservedRegisters.HEAP_BASE_REGISTER_CANDIDATE));
        VMError.guarantee(AMD64.r15.equals(AMD64ReservedRegisters.THREAD_REGISTER_CANDIDATE));
        ImageSingletons.add(RegisterDumper.class, new WindowsRegisterDumper());
    }
}

public class WindowsRegisterDumper implements RegisterDumper {
    @Override
    public void dumpRegisters(Log log, Context context, boolean printLocationInfo, boolean allowJavaHeapAccess, boolean allowUnsafeOperations) {
        dumpRegisters(log, (CONTEXT) context, printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
    }

    private static void dumpRegisters(Log log, CONTEXT context, boolean printLocationInfo, boolean allowJavaHeapAccess, boolean allowUnsafeOperations) {
        dumpReg(log, "RAX ", context.Rax(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RBX ", context.Rbx(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RCX ", context.Rcx(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RDX ", context.Rdx(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RBP ", context.Rbp(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RSI ", context.Rsi(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RDI ", context.Rdi(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RSP ", context.Rsp(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R8  ", context.R8(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R9  ", context.R9(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R10 ", context.R10(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R11 ", context.R11(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R12 ", context.R12(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R13 ", context.R13(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R14 ", context.R14(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R15 ", context.R15(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "EFL ", context.EFlags(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RIP ", context.Rip(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public PointerBase getHeapBase(Context context) {
        return WordFactory.pointer(((CONTEXT) context).R14());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public PointerBase getThreadPointer(Context context) {
        return WordFactory.pointer(((CONTEXT) context).R15());
    }

    @Override
    public PointerBase getSP(Context context) {
        return WordFactory.pointer(((CONTEXT) context).Rsp());
    }

    @Override
    public PointerBase getIP(Context context) {
        return WordFactory.pointer(((CONTEXT) context).Rip());
    }
}
