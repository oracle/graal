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
package com.oracle.svm.core.posix.cosmo.headers;

import com.oracle.svm.core.RegisterDumper;
import com.oracle.svm.core.SubstrateSegfaultHandler;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.posix.cosmo.CosmoSignalHandlerSupport;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.struct.AllowNarrowingCast;
import org.graalvm.nativeimage.c.struct.AllowWideningCast;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static org.graalvm.nativeimage.c.function.CFunction.Transition.NO_TRANSITION;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file signal.h.
 */
@CContext(CosmoDirectives.class)
public class Signal {

    @CFunction
    public static native int kill(int pid, int sig);

    @CFunction(value = "stubSIG_BLOCK", transition = CFunction.Transition.NO_TRANSITION)
    public static native int SIG_BLOCK();

    @CFunction(value = "stubSIG_UNBLOCK", transition = CFunction.Transition.NO_TRANSITION)
    public static native int SIG_UNBLOCK();

    @CFunction(value = "stubSIG_SETMASK", transition = CFunction.Transition.NO_TRANSITION)
    public static native int SIG_SETMASK();

    @CConstant
    public static native int SIGEV_SIGNAL();

    @CPointerTo(nameOfCType = "sigset_t")
    public interface sigset_tPointer extends PointerBase {
    }

    public interface VoidFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        void dispatch();
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

    @Platforms({Platform.LINUX.class})
    @CPointerTo(nameOfCType = "long long int")
    public interface GregsPointer extends PointerBase {
        long read(int index);
    }

    @CStruct
    public interface ucontext_t extends RegisterDumper.Context {
        @CFieldAddress("uc_mcontext")
        @Platforms({Platform.LINUX_AMD64_BASE.class})
        mcontext_linux_x86_64_t uc_mcontext_linux_x86_64();

        @CFieldAddress("uc_mcontext")
        @Platforms({Platform.LINUX_AARCH64_BASE.class})
        mcontext_linux_aarch64_t uc_mcontext_linux_aarch64();
    }

    public interface AdvancedSignalDispatcher extends CFunctionPointer {
        @InvokeCFunctionPointer
        void dispatch(int signum, siginfo_t siginfo, WordPointer opaque);
    }

    @CFunction(value = "stubSA_RESTART", transition = CFunction.Transition.NO_TRANSITION)
    public static native int SA_RESTART();

    @CFunction(value = "stubSA_SIGINFO", transition = CFunction.Transition.NO_TRANSITION)
    public static native int SA_SIGINFO();

    @CFunction(value = "stubSA_NODEFER", transition = CFunction.Transition.NO_TRANSITION)
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
        @AllowWideningCast
        long sa_flags();

        @CField
        @AllowNarrowingCast
        void sa_flags(long value);

        @CField
        VoidFunctionPointer sa_restorer();

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

    /** Don't call this function directly, use {@link CosmoSignalHandlerSupport} instead. */
    @CFunction(transition = NO_TRANSITION)
    public static native int sigaction(int signum, sigaction act, sigaction oldact);

    @CEnum
    @CContext(CosmoDirectives.class)
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
    @CContext(CosmoDirectives.class)
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

    @CStruct(value = "mcontext_t")
    @Platforms({Platform.LINUX_AMD64_BASE.class})
    public interface mcontext_linux_x86_64_t extends PointerBase {
        @CField
        long r8();

        @CField
        long r9();

        @CField
        long r10();

        @CField
        long r11();

        @CField
        long r12();

        @CField
        long r13();

        @CField
        long r14();

        @CField
        long r15();

        @CField
        long rdi();

        @CField
        long rsi();

        @CField
        long rbp();

        @CField
        long rbx();

        @CField
        long rdx();

        @CField
        long rax();

        @CField
        long rcx();

        @CField
        long rsp();

        @CField
        long rip();

        @CField
        long eflags();
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
