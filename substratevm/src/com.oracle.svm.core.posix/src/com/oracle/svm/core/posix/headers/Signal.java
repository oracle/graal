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
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;

/**
 * Contains definitions from signal.h that we actually need.
 */
@CContext(PosixDirectives.class)
public class Signal {
    /* Allow lower-case type names, underscores in names, etc.: Checkstyle: stop. */

    @CFunction
    public static native int kill(int pid, int sig);

    @CFunction
    public static native int sigprocmask(int how, sigset_tPointer set, sigset_tPointer oldset);

    /** A pointer to a signal set. The implementation of a signal set is platform-specific. */
    @CPointerTo(nameOfCType = "sigset_t")
    public interface sigset_tPointer extends PointerBase {
    }

    /** The interface to a C signal handler. */
    public interface SignalDispatcher extends CFunctionPointer {

        /** From signal(2): typedef void (*sig_t) (int). */
        @InvokeCFunctionPointer
        void dispatch(int sig);
    }

    /** Register a signal handler. */
    @CFunction
    public static native SignalDispatcher signal(int signum, SignalDispatcher handler);

    /** The signal handler that does the default action for a signal. */
    @CConstant
    public static native SignalDispatcher SIG_DFL();

    /** The signal handler that ignores a signal. */
    @CConstant
    public static native SignalDispatcher SIG_IGN();

    /** The signal handler that represents an error result. */
    @CConstant
    public static native SignalDispatcher SIG_ERR();

    /** Send a signal to the current thread. */
    @CFunction
    public static native int raise(int signum);

    @CStruct
    public interface siginfo_t extends PointerBase {
        /* Fields unused */
    }

    @Platforms(Platform.LINUX.class)
    @CPointerTo(nameOfCType = "long long int")
    public interface GregsPointer extends PointerBase {
        long read(int index);
    }

    @Platforms(Platform.LINUX.class)
    @CEnum
    @CContext(PosixDirectives.class)
    public enum GregEnum {
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
        REG_EFL,
        REG_CSGSFS,
        REG_ERR,
        REG_TRAPNO,
        REG_OLDMASK,
        REG_CR2;

        @CEnumValue
        public native int getCValue();
    }

    @Platforms(Platform.LINUX.class)
    @CStruct
    public interface ucontext_t extends PointerBase {
        /*-
            // Userlevel context.
            typedef struct ucontext
              {
                unsigned long int uc_flags;
                struct ucontext *uc_link;
                stack_t uc_stack;
                mcontext_t uc_mcontext;
                __sigset_t uc_sigmask;
                struct _libc_fpstate __fpregs_mem;
              } ucontext_t;
        
            // Context to describe whole processor state.
            typedef struct
              {
                gregset_t gregs;
                // Note that fpregs is a pointer.
                fpregset_t fpregs;
                __extension__ unsigned long long __reserved1 [8];
            } mcontext_t;
         */
        @CFieldAddress("uc_mcontext.gregs")
        GregsPointer uc_mcontext_gregs();
    }

    /** Advanced interface to a C signal handler. */
    public interface AdvancedSignalDispatcher extends CFunctionPointer {

        /** From SIGACTION(2): void (*sa_sigaction)(int, siginfo_t *, void *). */
        @InvokeCFunctionPointer
        void dispatch(int signum, siginfo_t siginfo, WordPointer opaque);
    }

    @Platforms(Platform.LINUX.class)
    @CConstant
    public static native int SA_SIGINFO();

    @CStruct(addStructKeyword = true)
    public interface sigaction extends PointerBase {
        /*-
           struct sigaction {
               void     (*sa_handler)(int);
               void     (*sa_sigaction)(int, siginfo_t *, void *);
               sigset_t   sa_mask;
               int        sa_flags;
               void     (*sa_restorer)(void);
           };
         */

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

    /** Advanced signal handler register function. */
    @CFunction
    public static native int sigaction(SignalEnum signum, sigaction act, sigaction oldact);

    /**
     * An alphabetical list of the signals on POSIX platforms. The signal numbers come from
     * {@link #getCValue()}.
     */
    @CEnum
    @CContext(PosixDirectives.class)
    public enum SignalEnum {
        /* create core image: abort program (formerly SIGIOT) */
        SIGABRT,
        /* terminate process: real-time timer expired */
        SIGALRM,
        /* create core image: bus error */
        SIGBUS,
        /* discard signal: child status has changed */
        SIGCHLD,
        /* discard signal: continue after stop */
        SIGCONT,
        /* create core image: floating-point exception */
        SIGFPE,
        /* terminate process: terminal line hangup */
        SIGHUP,
        /* create core image: illegal instruction */
        SIGILL,
        /* terminate process: interrupt program */
        SIGINT,
        /* discard signal: I/O is possible on a descriptor (see fcntl(2)) */
        SIGIO,
        /* create core image: abort program (replaced by SIGABRT) */
        SIGIOT,
        /* terminate process: kill program */
        SIGKILL,
        /* terminate process: write on a pipe with no reader */
        SIGPIPE,
        /* terminate process: profiling timer alarm (see setitimer(2)) */
        SIGPROF,
        /* create core image: quit program */
        SIGQUIT,
        /* create core image: segmentation violation */
        SIGSEGV,
        /* stop process: stop (cannot be caught or ignored) */
        SIGSTOP,
        /* create core image: non-existent system call invoked */
        SIGSYS,
        /* terminate process: software termination signal */
        SIGTERM,
        /* create core image: trace trap */
        SIGTRAP,
        /* stop process: stop signal generated from keyboard */
        SIGTSTP,
        /* stop process: background read attempted from control terminal */
        SIGTTIN,
        /* stop process: background write attempted to control terminal */
        SIGTTOU,
        /* discard signal: urgent condition present on socket */
        SIGURG,
        /* terminate process: User defined signal 1 */
        SIGUSR1,
        /* terminate process: User defined signal 2 */
        SIGUSR2,
        /* terminate process: virtual time alarm (see setitimer(2)) */
        SIGVTALRM,
        /* discard signal: Window size change */
        SIGWINCH,
        /* terminate process: cpu time limit exceeded (see setrlimit(2)) */
        SIGXCPU,
        /* terminate process: file size limit exceeded (see setrlimit(2)) */
        SIGXFSZ;

        @CEnumValue
        public native int getCValue();
    }

    /** An alphabetical list of Linux-specific signals. */
    /* Workaround for GR-7858: @Platform @CEnum members. */
    @Platforms(Platform.LINUX.class)
    @CEnum
    @CContext(PosixDirectives.class)
    public enum LinuxSignalEnum {
        /* Pollable event (Sys V). Synonym for SIGIO */
        SIGPOLL,
        /* Power failure restart (System V). */
        SIGPWR;

        @CEnumValue
        public native int getCValue();
    }

    /** An alphabetical list of Darwin-specific signals. */
    /* Workaround for GR-7858: @Platform @CEnum members. */
    @Platforms(Platform.DARWIN.class)
    @CEnum
    @CContext(PosixDirectives.class)
    public enum DarwinSignalEnum {
        /* status request from keyboard */
        SIGINFO,
        /* EMT instruction */
        SIGEMT;

        @CEnumValue
        public native int getCValue();
    }

    @CFunction
    public static native int sigemptyset(sigset_tPointer set);

    /* Allow lower-case type names, underscores in names, etc.: Checkstyle: resume. */
}
