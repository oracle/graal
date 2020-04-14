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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.RegisterDumper;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.graal.amd64.SubstrateAMD64RegisterConfig;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.windows.headers.ErrHandlingAPI.CONTEXT;

import jdk.vm.ci.amd64.AMD64;

@AutomaticFeature
class WindowsRegisterDumperFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        VMError.guarantee(AMD64.r14.equals(SubstrateAMD64RegisterConfig.HEAP_BASE_REGISTER_CANDIDATE));
        VMError.guarantee(AMD64.r15.equals(SubstrateAMD64RegisterConfig.THREAD_REGISTER_CANDIDATE));
        ImageSingletons.add(RegisterDumper.class, new WindowsRegisterDumper());
    }
}

public class WindowsRegisterDumper implements RegisterDumper {
    @Override
    public void dumpRegisters(Log log, Context context) {
        dumpRegisters(log, (CONTEXT) context);
    }

    private static void dumpRegisters(Log log, CONTEXT context) {
        log.string("RAX ").zhex(context.Rax()).newline();
        log.string("RBX ").zhex(context.Rbx()).newline();
        log.string("RCX ").zhex(context.Rcx()).newline();
        log.string("RDX ").zhex(context.Rdx()).newline();
        log.string("RBP ").zhex(context.Rbp()).newline();
        log.string("RSI ").zhex(context.Rsi()).newline();
        log.string("RDI ").zhex(context.Rdi()).newline();
        log.string("RSP ").zhex(context.Rsp()).newline();
        log.string("R8  ").zhex(context.R8()).newline();
        log.string("R9  ").zhex(context.R9()).newline();
        log.string("R10 ").zhex(context.R10()).newline();
        log.string("R11 ").zhex(context.R11()).newline();
        log.string("R12 ").zhex(context.R12()).newline();
        log.string("R13 ").zhex(context.R13()).newline();
        log.string("R14 ").zhex(context.R14()).newline();
        log.string("R15 ").zhex(context.R15()).newline();
        log.string("EFL ").zhex(context.EFlags()).newline();
        log.string("RIP ").zhex(context.Rip()).newline();
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
