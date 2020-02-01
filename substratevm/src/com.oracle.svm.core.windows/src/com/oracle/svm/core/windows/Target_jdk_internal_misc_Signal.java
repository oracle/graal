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

import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.FromAlias;
import static org.graalvm.nativeimage.c.function.CFunction.Transition.NO_TRANSITION;

import java.util.Hashtable;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.Jvm;
import com.oracle.svm.core.jdk.Package_jdk_internal_misc;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.windows.headers.WinBase;

@TargetClass(classNameProvider = Package_jdk_internal_misc.class, className = "Signal")
final class Target_jdk_internal_misc_Signal {
    // Checkstyle: stop
    @Alias @RecomputeFieldValue(kind = FromAlias)//
    private static Hashtable<?, ?> handlers = new Hashtable<>(4);
    @Alias @RecomputeFieldValue(kind = FromAlias)//
    private static Hashtable<?, ?> signals = new Hashtable<>(4);
    // Checkstyle: resume

    /** Called by the VM to execute Java signal handlers. */
    @Alias
    static native void dispatch(int number);

    @Substitute
    private static long handle0(int sig, long nativeH) {
        if (ImageInfo.isSharedLibrary()) {
            throw new IllegalArgumentException("Installing signal handlers is not allowed for native-image shared libraries.");
        }
        if (!PlatformNativeLibrarySupport.singleton().isFirstIsolate()) {
            throw new IllegalArgumentException("Only the first isolate can install signal handlers, as signal handling is global for the process.");
        }
        SignalDispatcher.ensureInitialized();
        return Jvm.JVM_RegisterSignal(sig, WordFactory.pointer(nativeH)).rawValue();
    }
}

class SignalDispatcher implements Runnable {
    private static final int NEAR_MAX_PRIORITY = Thread.MAX_PRIORITY - 1;

    /** A thread (in the image heap) to dispatch signals as they are raised. */
    private static final Thread signalDispatcherThread;
    static {
        signalDispatcherThread = new Thread(JavaThreads.singleton().systemGroup,
                        new SignalDispatcher(), "Signal Dispatcher");
        signalDispatcherThread.setPriority(NEAR_MAX_PRIORITY);
        signalDispatcherThread.setDaemon(true);
    }

    /**
     * Wait in native for a notification from the C signal handler that the signal has been raised
     * and then dispatch it to the Java signal handler.
     */
    @Override
    public void run() {
        while (true) {
            int sig = osSignalWait();

            if (sig == osSigexitnumPd()) {
                /* Terminate the signal thread. */
                return;
            } else {
                /* Dispatch the signal to java. */
                Target_jdk_internal_misc_Signal.dispatch(sig);
            }
        }
    }

    @CFunction("os__signal_wait")
    private static native int osSignalWait();

    @CFunction(value = "os__sigexitnum_pd", transition = NO_TRANSITION)
    private static native int osSigexitnumPd();

    /** Runtime initialization. */
    static void ensureInitialized() {
        /*
         * Usually an initialization like this would require explicit synchronization. However, in
         * this case it can only be triggered from the `Signal.handle` method that is already
         * synchronized, so we can do without it.
         */
        if (signalDispatcherThread.getState() == Thread.State.NEW) {
            if (!jdkMiscSignalInit()) {
                VMError.shouldNotReachHere("Native state initialization for jdk.internal.misc.Signal failed with error code: 0x" +
                                Integer.toUnsignedString(WinBase.GetLastError(), 16).toUpperCase());
            }
            RuntimeSupport.getRuntimeSupport().addTearDownHook(SignalDispatcher::osTerminateSignalThread);
            signalDispatcherThread.start();
        }
    }

    @CFunction(value = "jdk_misc_signal_init", transition = NO_TRANSITION)
    private static native boolean jdkMiscSignalInit();

    @CFunction(value = "os__terminate_signal_thread", transition = NO_TRANSITION)
    private static native void osTerminateSignalThread();
}
