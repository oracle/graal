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

import static org.graalvm.nativeimage.c.function.CFunction.Transition.NO_TRANSITION;

import java.util.Locale;

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.jdk.Jvm;
import com.oracle.svm.core.jdk.SignalHandlerSupport;
import com.oracle.svm.core.jdk.Target_jdk_internal_misc_Signal;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.windows.headers.WinBase;

@AutomaticallyRegisteredImageSingleton(SignalHandlerSupport.class)
public class WindowsSignalHandlerSupport implements SignalHandlerSupport {
    private static final int NEAR_MAX_PRIORITY = Thread.MAX_PRIORITY - 1;

    private Thread dispatcherThread;
    private boolean initialized;

    @Platforms(Platform.HOSTED_ONLY.class)
    public WindowsSignalHandlerSupport() {
    }

    @Override
    public long installJavaSignalHandler(int sig, long nativeH) {
        assert MonitorSupport.singleton().isLockedByCurrentThread(Target_jdk_internal_misc_Signal.class);
        ensureInitialized();

        return Jvm.JVM_RegisterSignal(sig, Word.pointer(nativeH)).rawValue();
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }

        if (!Isolates.isCurrentFirst()) {
            /* This is a difference to the posix implementation, see GR-57722. */
            throw new IllegalArgumentException("Only the first isolate can install signal handlers, as signal handling is global for the process.");
        }

        if (!jdkMiscSignalInit()) {
            VMError.shouldNotReachHere("Native state initialization for jdk.internal.misc.Signal failed with error code: 0x" +
                            Integer.toUnsignedString(WinBase.GetLastError(), 16).toUpperCase(Locale.ROOT));
        }

        startDispatcherThread();
        initialized = true;
    }

    private void startDispatcherThread() {
        dispatcherThread = new DispatcherThread();
        dispatcherThread.start();
    }

    @Override
    public void stopDispatcherThread() {
        if (!initialized) {
            return;
        }

        osTerminateSignalThread();
        try {
            dispatcherThread.join();
        } catch (InterruptedException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public void onIsolateTeardown() {
        /* Teardown is not implemented at the moment, see GR-57722. */
    }

    @CFunction("os__signal_wait")
    private static native int osSignalWait();

    @CFunction(value = "os__sigexitnum_pd", transition = NO_TRANSITION)
    private static native int osSigexitnumPd();

    @CFunction(value = "jdk_misc_signal_init", transition = NO_TRANSITION)
    private static native boolean jdkMiscSignalInit();

    @CFunction(value = "os__terminate_signal_thread", transition = NO_TRANSITION)
    private static native void osTerminateSignalThread();

    private static class DispatcherThread extends Thread {
        DispatcherThread() {
            super(PlatformThreads.singleton().systemGroup, "Signal Dispatcher");
            this.setPriority(NEAR_MAX_PRIORITY);
            this.setDaemon(true);
        }

        @Override
        public void run() {
            while (true) {
                int sig = osSignalWait();
                if (sig == osSigexitnumPd()) {
                    /* Terminate the signal thread. */
                    return;
                }

                Target_jdk_internal_misc_Signal.dispatch(sig);
            }
        }
    }
}
