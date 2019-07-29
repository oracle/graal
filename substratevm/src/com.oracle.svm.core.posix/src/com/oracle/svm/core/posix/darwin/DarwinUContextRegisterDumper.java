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
package com.oracle.svm.core.posix.darwin;

import com.oracle.svm.core.posix.headers.Signal;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.posix.UContextRegisterDumper;
import com.oracle.svm.core.posix.headers.Signal.ucontext_t;

@Platforms(Platform.DARWIN_AMD64.class)
@AutomaticFeature
class DarwinUContextRegisterDumperFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(UContextRegisterDumper.class, new DarwinUContextRegisterDumper());
    }
}

class DarwinUContextRegisterDumper implements UContextRegisterDumper {
    @Override
    public void dumpRegisters(Log log, ucontext_t uContext) {
        Signal.MContext64 sigcontext = uContext.uc_mcontext64();

        log.indent(true);
        long spValue = readRegisterAt(sigcontext, sigcontext.rsp_offset());
        long ipValue = readRegisterAt(sigcontext, sigcontext.rip_offset());

        log.newline().string("General Purpose Register Set Values: ").newline();

        log.indent(true);
        log.string("RAX ").zhex(readRegisterAt(sigcontext, sigcontext.rax_offset())).newline();
        log.string("RBX ").zhex(readRegisterAt(sigcontext, sigcontext.rbx_offset())).newline();
        log.string("RCX ").zhex(readRegisterAt(sigcontext, sigcontext.rcx_offset())).newline();
        log.string("RDX ").zhex(readRegisterAt(sigcontext, sigcontext.rdx_offset())).newline();
        log.string("RBP ").zhex(readRegisterAt(sigcontext, sigcontext.rbx_offset())).newline();
        log.string("RSI ").zhex(readRegisterAt(sigcontext, sigcontext.rsi_offset())).newline();
        log.string("RDI ").zhex(readRegisterAt(sigcontext, sigcontext.rdi_offset())).newline();
        log.string("RSP ").zhex(spValue).newline();
        log.string("R8 ").zhex(readRegisterAt(sigcontext, sigcontext.r8_offset())).newline();
        log.string("R9 ").zhex(readRegisterAt(sigcontext, sigcontext.r9_offset())).newline();
        log.string("R10 ").zhex(readRegisterAt(sigcontext, sigcontext.r10_offset())).newline();
        log.string("R11 ").zhex(readRegisterAt(sigcontext, sigcontext.r11_offset())).newline();
        log.string("R12 ").zhex(readRegisterAt(sigcontext, sigcontext.r12_offset())).newline();
        log.string("R13 ").zhex(readRegisterAt(sigcontext, sigcontext.r13_offset())).newline();
        log.string("R14 ").zhex(readRegisterAt(sigcontext, sigcontext.r14_offset())).newline();
        log.string("R15 ").zhex(readRegisterAt(sigcontext, sigcontext.r15_offset())).newline();
        log.string("EFL ").zhex(readRegisterAt(sigcontext, sigcontext.efl_offset())).newline();
        log.string("RIP ").zhex(ipValue).newline();
        log.indent(false);

        SubstrateUtil.printDiagnostics(log, WordFactory.pointer(spValue), WordFactory.pointer(ipValue));
    }

    private static long readRegisterAt(Signal.MContext64 sigcontext, int i) {
        return ((CLongPointer) ((CCharPointer) sigcontext).addressOf(i)).read();
    }
}
