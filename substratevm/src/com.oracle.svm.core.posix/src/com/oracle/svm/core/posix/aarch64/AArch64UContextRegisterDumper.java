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

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.posix.UContextRegisterDumper;
import com.oracle.svm.core.posix.headers.Signal.GregsPointer;
import com.oracle.svm.core.posix.headers.Signal.mcontext_t;
import com.oracle.svm.core.posix.headers.Signal.ucontext_t;

@Platforms(Platform.LINUX_AArch64.class)
@AutomaticFeature
class AArch64UContextRegisterDumperFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(UContextRegisterDumper.class, new AArch64UContextRegisterDumper());
    }
}

class AArch64UContextRegisterDumper implements UContextRegisterDumper {
    @Override
    public void dumpRegisters(Log log, ucontext_t uContext) {
        mcontext_t sigcontext = uContext.uc_mcontext();
        GregsPointer regs = sigcontext.regs();
        long spValue = sigcontext.sp();
        long pcValue = sigcontext.pc();

        log.newline().string("General Purpose Register Set Values: ").newline();

        log.indent(true);
        log.string("R0 ").zhex(regs.read(0)).newline();
        log.string("R1 ").zhex(regs.read(1)).newline();
        log.string("R2 ").zhex(regs.read(2)).newline();
        log.string("R3 ").zhex(regs.read(3)).newline();
        log.string("R4 ").zhex(regs.read(4)).newline();
        log.string("R5 ").zhex(regs.read(5)).newline();
        log.string("R6 ").zhex(regs.read(6)).newline();
        log.string("R7 ").zhex(regs.read(7)).newline();
        log.string("R8 ").zhex(regs.read(8)).newline();
        log.string("R9 ").zhex(regs.read(9)).newline();
        log.string("R10 ").zhex(regs.read(10)).newline();
        log.string("R11 ").zhex(regs.read(11)).newline();
        log.string("R12 ").zhex(regs.read(12)).newline();
        log.string("R13 ").zhex(regs.read(13)).newline();
        log.string("R14 ").zhex(regs.read(14)).newline();
        log.string("R15 ").zhex(regs.read(15)).newline();
        log.string("R16 ").zhex(regs.read(16)).newline();
        log.string("R17 ").zhex(regs.read(17)).newline();
        log.string("R18 ").zhex(regs.read(18)).newline();
        log.string("R19 ").zhex(regs.read(19)).newline();
        log.string("R20 ").zhex(regs.read(20)).newline();
        log.string("R21 ").zhex(regs.read(21)).newline();
        log.string("R22 ").zhex(regs.read(22)).newline();
        log.string("R23 ").zhex(regs.read(23)).newline();
        log.string("R24 ").zhex(regs.read(24)).newline();
        log.string("R25 ").zhex(regs.read(25)).newline();
        log.string("R26 ").zhex(regs.read(26)).newline();
        log.string("R27 ").zhex(regs.read(27)).newline();
        log.string("R28 ").zhex(regs.read(28)).newline();
        log.string("R29 ").zhex(regs.read(29)).newline();
        log.string("R30 ").zhex(regs.read(30)).newline();
        log.string("R31 ").zhex(regs.read(31)).newline();
        log.string("SP ").zhex(spValue).newline();
        log.string("PC ").zhex(pcValue).newline();
        log.indent(false);

        SubstrateUtil.printDiagnostics(log, WordFactory.pointer(spValue), WordFactory.pointer(pcValue));
    }
}
