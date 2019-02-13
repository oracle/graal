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
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.OS;
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
import com.oracle.svm.core.util.VMError;

@Platforms({Platform.LINUX_AMD64.class, Platform.DARWIN_AMD64.class})
@AutomaticFeature
public class SegfaultHandlerFeature implements Feature {

    private static AMD64RegisterReader initializeRegisterReader() {
        if (OS.getCurrent() == OS.LINUX) {
            return new LinuxAMD64RegisterReader();
        } else if (OS.getCurrent() == OS.DARWIN) {
            return new DarwinAMD64RegisterReader();
        } else {
            throw VMError.shouldNotReachHere("Unsupported OS.");
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        ImageSingletons.add(AMD64RegisterReader.class, initializeRegisterReader());
        RuntimeSupport.getRuntimeSupport().addStartupHook(SubstrateSegfaultHandler::install);
    }
}

@Platforms(Platform.AMD64.class)
interface AMD64RegisterReader {

    void setRegistersPointer(ucontext_t ucontext);

    long readRegister(SubstrateSegfaultHandler.AMD64Register register);

}

@Platforms({Platform.LINUX_AMD64.class})
class LinuxAMD64RegisterReader implements AMD64RegisterReader {
    private GregsPointer gregs;

    @Override
    public void setRegistersPointer(ucontext_t ucontext) {
        gregs = ucontext.uc_mcontext_gregs();
    }

    @Override
    public long readRegister(SubstrateSegfaultHandler.AMD64Register register) {
        switch (register) {
            case REG_R8:
                return readRegister(Signal.GregEnum.REG_R8);
            case REG_R9:
                return readRegister(Signal.GregEnum.REG_R9);
            case REG_R10:
                return readRegister(Signal.GregEnum.REG_R10);
            case REG_R11:
                return readRegister(Signal.GregEnum.REG_R11);
            case REG_R12:
                return readRegister(Signal.GregEnum.REG_R12);
            case REG_R13:
                return readRegister(Signal.GregEnum.REG_R13);
            case REG_R14:
                return readRegister(Signal.GregEnum.REG_R14);
            case REG_R15:
                return readRegister(Signal.GregEnum.REG_R15);
            case REG_RDI:
                return readRegister(Signal.GregEnum.REG_RDI);
            case REG_RSI:
                return readRegister(Signal.GregEnum.REG_RSI);
            case REG_RBP:
                return readRegister(Signal.GregEnum.REG_RBP);
            case REG_RBX:
                return readRegister(Signal.GregEnum.REG_RBX);
            case REG_RDX:
                return readRegister(Signal.GregEnum.REG_RDX);
            case REG_RAX:
                return readRegister(Signal.GregEnum.REG_RAX);
            case REG_RCX:
                return readRegister(Signal.GregEnum.REG_RCX);
            case REG_RSP:
                return readRegister(Signal.GregEnum.REG_RSP);
            case REG_RIP:
                return readRegister(Signal.GregEnum.REG_RIP);
            case REG_EFL:
                return readRegister(Signal.GregEnum.REG_EFL);
            default:
                return 0;
        }
    }

    private long readRegister(Signal.GregEnum regR8) {
        return gregs.read(regR8.getCValue());
    }
}

@Platforms({Platform.DARWIN_AMD64.class})
class DarwinAMD64RegisterReader implements AMD64RegisterReader {
    private Signal.MContext64 context;

    @Override
    public void setRegistersPointer(ucontext_t ucontext) {
        context = ucontext.uc_mcontext();
    }

    private long readRegisterAt(int i) {
        return ((CLongPointer) ((CCharPointer) context).addressOf(i)).read();
    }

    @Override
    public long readRegister(SubstrateSegfaultHandler.AMD64Register register) {
        switch (register) {
            case REG_R8:
                return readRegisterAt(context.r9_offset());
            case REG_R9:
                return readRegisterAt(context.r9_offset());
            case REG_R10:
                return readRegisterAt(context.r10_offset());
            case REG_R11:
                return readRegisterAt(context.r11_offset());
            case REG_R12:
                return readRegisterAt(context.r12_offset());
            case REG_R13:
                return readRegisterAt(context.r13_offset());
            case REG_R14:
                return readRegisterAt(context.r14_offset());
            case REG_R15:
                return readRegisterAt(context.r15_offset());
            case REG_RDI:
                return readRegisterAt(context.rdi_offset());
            case REG_RSI:
                return readRegisterAt(context.rsi_offset());
            case REG_RBP:
                return readRegisterAt(context.rbp_offset());
            case REG_RBX:
                return readRegisterAt(context.rbx_offset());
            case REG_RDX:
                return readRegisterAt(context.rdx_offset());
            case REG_RAX:
                return readRegisterAt(context.rax_offset());
            case REG_RCX:
                return readRegisterAt(context.rcx_offset());
            case REG_RSP:
                return readRegisterAt(context.rsp_offset());
            case REG_RIP:
                return readRegisterAt(context.rip_offset());
            case REG_EFL:
                return readRegisterAt(context.efl_offset());
            default:
                return 0;
        }
    }
}

class SubstrateSegfaultHandler {
    public enum AMD64Register {
        REG_R8,
        REG_R9,
        REG_R10,
        REG_R11,
        REG_R12,
        REG_R13,
        REG_R14,
        REG_R15,
        REG_RDI,
        REG_RSI,
        REG_RBP,
        REG_RBX,
        REG_RDX,
        REG_RAX,
        REG_RCX,
        REG_RSP,
        REG_RIP,
        REG_EFL
    }

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
        AMD64RegisterReader regs = ImageSingletons.lookup(AMD64RegisterReader.class);
        regs.setRegistersPointer(uContext);
        Log log = Log.log();
        log.autoflush(true);
        log.string("[ [ SubstrateSegfaultHandler caught signal ").signed(signalNumber).string(" ] ]").newline();

        log.newline().string("General Purpose Register Set Values: ").newline();
        log.indent(true);
        log.string("RAX ").zhex(regs.readRegister(AMD64Register.REG_RAX)).newline();
        log.string("RBX ").zhex(regs.readRegister(AMD64Register.REG_RBX)).newline();
        log.string("RCX ").zhex(regs.readRegister(AMD64Register.REG_RCX)).newline();
        log.string("RDX ").zhex(regs.readRegister(AMD64Register.REG_RDX)).newline();
        log.string("RBP ").zhex(regs.readRegister(AMD64Register.REG_RBP)).newline();
        log.string("RSI ").zhex(regs.readRegister(AMD64Register.REG_RSI)).newline();
        log.string("RDI ").zhex(regs.readRegister(AMD64Register.REG_RDI)).newline();
        log.string("RSP ").zhex(regs.readRegister(AMD64Register.REG_RSP)).newline();
        log.string("R8  ").zhex(regs.readRegister(AMD64Register.REG_R8)).newline();
        log.string("R9  ").zhex(regs.readRegister(AMD64Register.REG_R9)).newline();
        log.string("R10 ").zhex(regs.readRegister(AMD64Register.REG_R10)).newline();
        log.string("R11 ").zhex(regs.readRegister(AMD64Register.REG_R11)).newline();
        log.string("R12 ").zhex(regs.readRegister(AMD64Register.REG_R12)).newline();
        log.string("R13 ").zhex(regs.readRegister(AMD64Register.REG_R13)).newline();
        log.string("R14 ").zhex(regs.readRegister(AMD64Register.REG_R14)).newline();
        log.string("R15 ").zhex(regs.readRegister(AMD64Register.REG_R15)).newline();
        log.string("EFL ").zhex(regs.readRegister(AMD64Register.REG_EFL)).newline();
        log.string("RIP ").zhex(regs.readRegister(AMD64Register.REG_RIP)).newline();
        log.indent(false);

        SubstrateUtil.printDiagnostics(log, WordFactory.pointer(regs.readRegister(AMD64Register.REG_RSP)), WordFactory.pointer(regs.readRegister(AMD64Register.REG_RIP)));
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
