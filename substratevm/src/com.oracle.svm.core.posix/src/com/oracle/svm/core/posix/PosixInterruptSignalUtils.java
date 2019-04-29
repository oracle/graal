/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.c.function.CEntryPointOptions.Publish;
import com.oracle.svm.core.posix.headers.Pthread;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.Signal.SignalDispatcher;

/**
 * A null handler for the signal used to interrupt blocking operations.
 *
 * The null signal handler for the interrupt signal is called from a static initialization block of
 * {@link sun.nio.ch.NativeThread}. I need to set up the signal handler at runtime. The null signal
 * handler is also set up, at runtime, when libjvm is loaded, from the <code>init()</code> methods
 * of /jdk8u-dev/jdk/src/solaris/native/java/net/bsd_close.c, and
 * /jdk8u-dev/jdk/src/solaris/native/java/net/linux_close.c. I consolidate all the calls in an
 * {@link #ensureInitialized} method that is called at runtime by the various users.
 *
 * Adapted from /jdk8u-dev/jdk/src/solaris/native/sun/nio/ch/NativeThread.c, but shared by anyone
 * who sends C signals to interrupt blocked operations.
 */
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
public class PosixInterruptSignalUtils {

    /**
     *
     * I am not worried about races, as they will all register the same signal handler.
     */
    static boolean initialized = false;

    /* { Do not re-format commented code: @formatter:off */
    // 035 #ifdef __linux__
    // 036   #include <pthread.h>
    // 037   #include <sys/signal.h>
    // 038   /* Also defined in net/linux_close.c */
    // 039   #define INTERRUPT_SIGNAL (__SIGRTMAX - 2)
    // 040 #elif __solaris__
    // 041   #include <thread.h>
    // 042   #include <signal.h>
    // 043   #define INTERRUPT_SIGNAL (SIGRTMAX - 2)
    // 044 #elif _ALLBSD_SOURCE
    // 045   #include <pthread.h>
    // 046   #include <signal.h>
    // 047   /* Also defined in net/bsd_close.c */
    // 048   #define INTERRUPT_SIGNAL SIGIO
    // 049 #else
    // 050   #error "missing platform-specific definition here"
    // 051 #endif
    private static final Signal.SignalEnum INTERRUPT_SIGNAL = Signal.SignalEnum.SIGIO;
    /* } Do not re-format commented code: @formatter:on */

    @CEntryPoint
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    @Uninterruptible(reason = "Can not check for safepoints because I am running on a borrowed thread.")
    private static void nullHandler(@SuppressWarnings("unused") int signalNumber) {
    }

    /** The address of the null signal handler. */
    private static final CEntryPointLiteral<SignalDispatcher> nullDispatcher = CEntryPointLiteral.create(PosixInterruptSignalUtils.class, "nullHandler", int.class);

    /** Set up a null signal handler for the interrupt signal. */
    public static void ensureInitialized() throws IOException {
        /* I avoid overwriting any previous signal handler by checking for initialization, first. */
        if (!initialized) {
            /* { Do not re-format commented code: @formatter:off */
                // 061     /* Install the null handler for INTERRUPT_SIGNAL.  This might overwrite the
                // 062      * handler previously installed by java/net/linux_close.c, but that's okay
                // 063      * since neither handler actually does anything.  We install our own
                // 064      * handler here simply out of paranoia; ultimately the two mechanisms
                // 065      * should somehow be unified, perhaps within the VM.
                // 066      */
                // 067
                // 068     sigset_t ss;
                // 069     struct sigaction sa, osa;
                Signal.sigaction saPointer = StackValue.get(Signal.sigaction.class);
                Signal.sigaction osaPointer = StackValue.get(Signal.sigaction.class);
                // 070     sa.sa_handler = nullHandler;
                saPointer.sa_handler(PosixInterruptSignalUtils.nullDispatcher.getFunctionPointer());
                // 071     sa.sa_flags = 0;
                saPointer.sa_flags(0);
                // 072     sigemptyset(&sa.sa_mask);
                Signal.sigemptyset(saPointer.sa_mask());
                // 073     if (sigaction(INTERRUPT_SIGNAL, &sa, &osa) < 0)
                if (Signal.sigaction(INTERRUPT_SIGNAL, saPointer, osaPointer) < 0) {
                    // 074         JNU_ThrowIOExceptionWithLastError(env, "sigaction");
                    throw PosixUtils.newIOExceptionWithLastError("sigaction");
                }
                /* } Do not re-format commented code: @formatter:on */
            initialized = true;
        }
    }

    public static int interruptPThread(Pthread.pthread_t pThread) throws IOException {
        ensureInitialized();
        return Pthread.pthread_kill(pThread, INTERRUPT_SIGNAL);
    }
}
