/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.headers;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static org.graalvm.nativeimage.c.function.CFunction.Transition.NO_TRANSITION;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CFieldOffset;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.RegisterDumper;
import com.oracle.svm.core.SubstrateSegfaultHandler;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.posix.PosixSignalHandlerSupport;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file signal.h.
 */
@CContext(PosixDirectives.class)
public class Signal {

    @CFunction
    public static native int kill(int pid, int sig);

    @CConstant
    public static native int SIG_BLOCK();

    @CConstant
    public static native int SIG_UNBLOCK();

    @CConstant
    public static native int SIG_SETMASK();

    @CConstant
    public static native int SIGEV_SIGNAL();

    @CPointerTo(nameOfCType = "sigset_t")
    public interface sigset_tPointer extends PointerBase {
    }

    /**
     * WARNING: do NOT introduce direct calls to {@code signal} or {@code sigset} as they are not
     * portable. Besides that, signal chaining (libjsig) in HotSpot would print warnings.
     */
    public interface SignalDispatcher extends CFunctionPointer {
        @InvokeCFunctionPointer
        void dispatch(int sig);
    }

    @CConstant
    public static native SignalDispatcher SIG_DFL();

    @CConstant
    public static native SignalDispatcher SIG_IGN();

    @CConstant
    public static native SignalDispatcher SIG_ERR();

    @CFunction
    public static native int raise(int signum);

    @CStruct(isIncomplete = true)
    public interface siginfo_t extends PointerBase {
        @CField
        int si_signo();

        @CField
        int si_errno();

        @CField
        int si_code();

        @CField
        VoidPointer si_addr();
    }

    @Platforms({Platform.LINUX.class, Platform.DARWIN_AARCH64.class})
    @CPointerTo(nameOfCType = "long long int")
    public interface GregsPointer extends PointerBase {
        long read(int index);
    }

    @CStruct
    public interface ucontext_t extends RegisterDumper.Context {
        @CFieldAddress("uc_mcontext.gregs")
        @Platforms({Platform.LINUX_AMD64.class})
        GregsPointer uc_mcontext_linux_amd64_gregs();

        @CFieldAddress("uc_mcontext")
        @Platforms({Platform.LINUX_AARCH64_BASE.class})
        mcontext_linux_aarch64_t uc_mcontext_linux_aarch64();

        @CFieldAddress("uc_mcontext")
        @Platforms({Platform.LINUX_RISCV64.class})
        mcontext_linux_riscv64_t uc_mcontext_linux_riscv64();

        @CField("uc_mcontext")
        @Platforms({Platform.DARWIN_AMD64.class})
        AMD64DarwinMContext64 uc_mcontext_darwin_amd64();

        @CField("uc_mcontext")
        @Platforms({Platform.DARWIN_AARCH64.class})
        AArch64DarwinMContext64 uc_mcontext_darwin_aarch64();
    }

    public interface AdvancedSignalDispatcher extends CFunctionPointer {
        @InvokeCFunctionPointer
        void dispatch(int signum, siginfo_t siginfo, WordPointer opaque);
    }

    @CConstant
    public static native int SA_RESTART();

    @CConstant
    public static native int SA_SIGINFO();

    @CConstant
    public static native int SA_NODEFER();

    @CStruct(addStructKeyword = true)
    public interface sigaction extends PointerBase {
        @CField
        SignalDispatcher sa_handler();

        @CField
        void sa_handler(SignalDispatcher value);

        @CField
        AdvancedSignalDispatcher sa_sigaction();

        @CField
        void sa_sigaction(AdvancedSignalDispatcher value);

        @CField
        int sa_flags();

        @CField
        void sa_flags(int value);

        @CFieldAddress
        sigset_tPointer sa_mask();
    }

    @CStruct(addStructKeyword = true)
    public interface sigevent extends PointerBase {
        @CField
        void sigev_notify(int value);

        @CField
        void sigev_signo(int value);
    }

    /** Don't call this function directly, use {@link PosixSignalHandlerSupport} instead. */
    @CFunction(transition = NO_TRANSITION)
    public static native int sigaction(int signum, sigaction act, sigaction oldact);

    @CEnum
    @CContext(PosixDirectives.class)
    public enum SignalEnum {
        SIGABRT,
        SIGALRM,
        SIGBUS,
        SIGCHLD,
        SIGCONT,
        SIGFPE,
        SIGHUP,
        SIGILL,
        SIGINT,
        SIGIO,
        SIGIOT,
        SIGKILL,
        SIGPIPE,
        SIGPROF,
        SIGQUIT,
        SIGSEGV,
        SIGSTOP,
        SIGSYS,
        SIGTERM,
        SIGTRAP,
        SIGTSTP,
        SIGTTIN,
        SIGTTOU,
        SIGURG,
        SIGUSR1,
        SIGUSR2,
        SIGVTALRM,
        SIGWINCH,
        SIGXCPU,
        SIGXFSZ;

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public int getCValue() {
            if (SubstrateUtil.HOSTED) {
                return CConstant.ValueAccess.get(this, "getCValue0");
            }
            return getCValue0();
        }

        @CEnumValue
        private native int getCValue0();
    }

    @Platforms(Platform.LINUX.class)
    @CEnum
    @CContext(PosixDirectives.class)
    public enum LinuxSignalEnum {
        SIGPOLL,
        SIGPWR;

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public int getCValue() {
            if (SubstrateUtil.HOSTED) {
                return CConstant.ValueAccess.get(this, "getCValue0");
            }
            return getCValue0();
        }

        @CEnumValue
        private native int getCValue0();
    }

    @Platforms(Platform.DARWIN.class)
    @CEnum
    @CContext(PosixDirectives.class)
    public enum DarwinSignalEnum {
        SIGINFO,
        SIGEMT;

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public int getCValue() {
            if (SubstrateUtil.HOSTED) {
                return CConstant.ValueAccess.get(this, "getCValue0");
            }
            return getCValue0();
        }

        @CEnumValue
        private native int getCValue0();
    }

    /**
     * Used in {@link SubstrateSegfaultHandler}. So, this must not be a {@link CEnum} as this would
     * result in machine code that needs a proper a heap base.
     *
     * Information about Linux's AMD64 struct sigcontext_64 uc_mcontext can be found at
     * https://github.com/torvalds/linux/blob/9e1ff307c779ce1f0f810c7ecce3d95bbae40896/arch/x86/include/uapi/asm/sigcontext.h#L238
     */
    @Platforms({Platform.LINUX_AMD64.class})
    @CContext(PosixDirectives.class)
    public static final class GregEnumLinuxAMD64 {
        @CConstant
        public static native int REG_R8();

        @CConstant
        public static native int REG_R9();

        @CConstant
        public static native int REG_R10();

        @CConstant
        public static native int REG_R11();

        @CConstant
        public static native int REG_R12();

        @CConstant
        public static native int REG_R13();

        @CConstant
        public static native int REG_R14();

        @CConstant
        public static native int REG_R15();

        @CConstant
        public static native int REG_RDI();

        @CConstant
        public static native int REG_RSI();

        @CConstant
        public static native int REG_RBP();

        @CConstant
        public static native int REG_RBX();

        @CConstant
        public static native int REG_RDX();

        @CConstant
        public static native int REG_RAX();

        @CConstant
        public static native int REG_RCX();

        @CConstant
        public static native int REG_RSP();

        @CConstant
        public static native int REG_RIP();

        @CConstant
        public static native int REG_EFL();

        @CConstant
        public static native int REG_CSGSFS();

        @CConstant
        public static native int REG_ERR();

        @CConstant
        public static native int REG_TRAPNO();

        @CConstant
        public static native int REG_OLDMASK();

        @CConstant
        public static native int REG_CR2();
    }

    /**
     * Information about Linux's AArch64 struct sigcontext uc_mcontext can be found at
     * https://github.com/torvalds/linux/blob/9e1ff307c779ce1f0f810c7ecce3d95bbae40896/arch/arm64/include/uapi/asm/sigcontext.h#L28
     */
    @CStruct(value = "mcontext_t")
    @Platforms({Platform.LINUX_AARCH64_BASE.class})
    public interface mcontext_linux_aarch64_t extends PointerBase {
        @CField
        long fault_address();

        @CFieldAddress
        GregsPointer regs();

        @CField
        long sp();

        @CField
        long pc();

        @CField
        long pstate();
    }

    /**
     * Information about Linux's RISCV64 struct sigcontext uc_mcontext can be found at
     * https://github.com/torvalds/linux/blob/9e1ff307c779ce1f0f810c7ecce3d95bbae40896/arch/riscv/include/uapi/asm/sigcontext.h#L17
     */
    @CStruct(value = "mcontext_t")
    @Platforms({Platform.LINUX_RISCV64.class})
    public interface mcontext_linux_riscv64_t extends PointerBase {
        @CFieldAddress(value = "__gregs")
        GregsPointer gregs();
    }

    /**
     * Information about Darwin's AMD64 mcontext64 can be found at
     * https://github.com/apple/darwin-xnu/blob/2ff845c2e033bd0ff64b5b6aa6063a1f8f65aa32/bsd/i386/_mcontext.h#L147
     *
     * Information about _STRUCT_X86_THREAD_STATE64 can be found at
     * https://github.com/apple/darwin-xnu/blob/2ff845c2e033bd0ff64b5b6aa6063a1f8f65aa32/osfmk/mach/i386/_structs.h#L739
     */
    @Platforms({Platform.DARWIN_AMD64.class})
    @CStruct(value = "__darwin_mcontext64", addStructKeyword = true)
    public interface AMD64DarwinMContext64 extends PointerBase {
        @CFieldOffset("__ss.__rax")
        int rax_offset();

        @CFieldOffset("__ss.__rbx")
        int rbx_offset();

        @CFieldOffset("__ss.__rip")
        int rip_offset();

        @CFieldOffset("__ss.__rsp")
        int rsp_offset();

        @CFieldOffset("__ss.__rcx")
        int rcx_offset();

        @CFieldOffset("__ss.__rdx")
        int rdx_offset();

        @CFieldOffset("__ss.__rbp")
        int rbp_offset();

        @CFieldOffset("__ss.__rsi")
        int rsi_offset();

        @CFieldOffset("__ss.__rdi")
        int rdi_offset();

        @CFieldOffset("__ss.__r8")
        int r8_offset();

        @CFieldOffset("__ss.__r9")
        int r9_offset();

        @CFieldOffset("__ss.__r10")
        int r10_offset();

        @CFieldOffset("__ss.__r11")
        int r11_offset();

        @CFieldOffset("__ss.__r12")
        int r12_offset();

        @CFieldOffset("__ss.__r13")
        int r13_offset();

        @CFieldOffset("__ss.__r14")
        int r14_offset();

        @CFieldOffset("__ss.__r15")
        int r15_offset();

        @CFieldOffset("__ss.__rflags")
        int efl_offset();
    }

    /**
     * Information about Darwin's AArch64 mcontext64 can be found at
     * https://github.com/apple/darwin-xnu/blob/2ff845c2e033bd0ff64b5b6aa6063a1f8f65aa32/bsd/arm/_mcontext.h#L70
     *
     * Information about _STRUCT_ARM_THREAD_STATE64 can be found at
     * https://github.com/apple/darwin-xnu/blob/2ff845c2e033bd0ff64b5b6aa6063a1f8f65aa32/osfmk/mach/arm/_structs.h#L102
     */
    @Platforms({Platform.DARWIN_AARCH64.class})
    @CStruct(value = "__darwin_mcontext64", addStructKeyword = true)
    public interface AArch64DarwinMContext64 extends PointerBase {
        @CFieldAddress("__ss.__x")
        GregsPointer regs();

        @CField("__ss.__fp")
        long fp();

        @CField("__ss.__lr")
        long lr();

        @CField("__ss.__sp")
        long sp();

        @CField("__ss.__pc")
        long pc();
    }

    public static class NoTransitions {
        @CFunction(transition = NO_TRANSITION)
        public static native int kill(int pid, int sig);

        @CFunction(transition = NO_TRANSITION)
        public static native int sigprocmask(int how, sigset_tPointer set, sigset_tPointer oldset);

        @CFunction(transition = NO_TRANSITION)
        public static native int sigemptyset(sigset_tPointer set);

        @CFunction(transition = NO_TRANSITION)
        public static native int sigaddset(sigset_tPointer set, int signum);

    }
}
