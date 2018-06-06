/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix;

import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.c.function.CEntryPointOptions.Publish;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.Signal.AdvancedSignalDispatcher;
import com.oracle.svm.core.posix.headers.Signal.GregsPointer;
import com.oracle.svm.core.posix.headers.Signal.sigaction;
import com.oracle.svm.core.posix.headers.Signal.siginfo_t;
import com.oracle.svm.core.posix.headers.Signal.ucontext_t;
import com.oracle.svm.core.thread.VMThreads;

@Platforms(Platform.LINUX.class)
@AutomaticFeature
public class SegfaultHandlerFeature implements Feature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        RuntimeSupport.getRuntimeSupport().addStartupHook(SubstrateSegfaultHandler::install);
    }
}

class SubstrateSegfaultHandler {

    public static class Options {
        @Option(help = "Install segfault handler that prints register contents and full Java stacktrace")//
        static final RuntimeOptionKey<Boolean> InstallSegfaultHandler = new RuntimeOptionKey<>(true);
    }

    private static volatile boolean dispatchInProgress = false;

    @CEntryPoint
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in segfault signal handler.")
    @Uninterruptible(reason = "Must be uninterruptible until it gets immune to safepoints", calleeMustBe = false)
    private static void dispatch(int signalNumber, @SuppressWarnings("unused") siginfo_t sigInfo, ucontext_t uContext) {
        if (dispatchInProgress) {
            Log.log().newline().string("[ [ SubstrateSegfaultHandler already handling signal ").signed(signalNumber).string(" ] ]").newline();
            return;
        }
        dispatchInProgress = true;

        VMThreads.StatusSupport.setStatusIgnoreSafepoints();

        Log log = Log.log();
        log.autoflush(true);
        log.string("[ [ SubstrateSegfaultHandler caught signal ").signed(signalNumber).string(" ] ]").newline();

        GregsPointer gregs = uContext.uc_mcontext_gregs();
        long spValue = gregs.read(Signal.GregEnum.REG_RSP.getCValue());
        long ipValue = gregs.read(Signal.GregEnum.REG_RIP.getCValue());

        log.newline().string("General Purpose Register Set Values: ").newline();

        log.indent(true);
        log.string("RAX ").zhex(gregs.read(Signal.GregEnum.REG_RAX.getCValue())).newline();
        log.string("RBX ").zhex(gregs.read(Signal.GregEnum.REG_RBX.getCValue())).newline();
        log.string("RCX ").zhex(gregs.read(Signal.GregEnum.REG_RCX.getCValue())).newline();
        log.string("RDX ").zhex(gregs.read(Signal.GregEnum.REG_RDX.getCValue())).newline();
        log.string("RBP ").zhex(gregs.read(Signal.GregEnum.REG_RBP.getCValue())).newline();
        log.string("RSI ").zhex(gregs.read(Signal.GregEnum.REG_RSI.getCValue())).newline();
        log.string("RDI ").zhex(gregs.read(Signal.GregEnum.REG_RDI.getCValue())).newline();
        log.string("RSP ").zhex(spValue).newline();
        log.string("R8  ").zhex(gregs.read(Signal.GregEnum.REG_R8.getCValue())).newline();
        log.string("R9  ").zhex(gregs.read(Signal.GregEnum.REG_R9.getCValue())).newline();
        log.string("R10 ").zhex(gregs.read(Signal.GregEnum.REG_R10.getCValue())).newline();
        log.string("R11 ").zhex(gregs.read(Signal.GregEnum.REG_R11.getCValue())).newline();
        log.string("R12 ").zhex(gregs.read(Signal.GregEnum.REG_R12.getCValue())).newline();
        log.string("R13 ").zhex(gregs.read(Signal.GregEnum.REG_R13.getCValue())).newline();
        log.string("R14 ").zhex(gregs.read(Signal.GregEnum.REG_R14.getCValue())).newline();
        log.string("R15 ").zhex(gregs.read(Signal.GregEnum.REG_R15.getCValue())).newline();
        log.string("EFL ").zhex(gregs.read(Signal.GregEnum.REG_EFL.getCValue())).newline();
        log.string("RIP ").zhex(ipValue).newline();
        log.indent(false);

        SubstrateUtil.printDiagnostics(log, WordFactory.pointer(spValue), WordFactory.pointer(ipValue));

        log.string("Use runtime option -R:-InstallSegfaultHandler if you don't want to use SubstrateSegfaultHandler.").newline();

        log.newline().string("Bye bye ...").newline().newline();
        ImageSingletons.lookup(LogHandler.class).fatalError();
    }

    /** The address of the signal handler for signals handled by Java code, above. */
    private static final CEntryPointLiteral<AdvancedSignalDispatcher> advancedSignalDispatcher = CEntryPointLiteral.create(SubstrateSegfaultHandler.class, "dispatch", int.class, siginfo_t.class,
                    ucontext_t.class);

    static void install() {
        if (Options.InstallSegfaultHandler.getValue()) {
            int structSigActionSize = SizeOf.get(sigaction.class);
            sigaction structSigAction = StackValue.get(structSigActionSize);
            LibC.memset(structSigAction, WordFactory.signed(0), WordFactory.unsigned(structSigActionSize));
            /* Register sa_sigaction signal handler */
            structSigAction.sa_flags(Signal.SA_SIGINFO());
            structSigAction.sa_sigaction(advancedSignalDispatcher.getFunctionPointer());
            Signal.sigaction(Signal.SignalEnum.SIGSEGV, structSigAction, WordFactory.nullPointer());
        }
    }
}
