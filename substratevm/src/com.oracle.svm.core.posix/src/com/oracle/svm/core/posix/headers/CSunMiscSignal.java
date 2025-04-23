/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.nativeimage.c.function.CFunction.Transition.NO_TRANSITION;

import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CLibrary;

/** See cSunMiscSignal.c for the C implementation. */
@CLibrary(value = "libchelper", requireStatic = true)
public class CSunMiscSignal {
    /**
     * Open the Java signal handler mechanism. Multiple isolates may execute this method in parallel
     * but only a single isolate may claim ownership.
     *
     * @return 0 on success, 1 if the signal handler mechanism was already claimed by another
     *         isolate, or some other value if an error occurred during initialization.
     */
    @CFunction("cSunMiscSignal_open")
    public static native int open();

    /**
     * Close the Java signal handler mechanism.
     *
     * @return 0 on success, or some non-zero value if an error occurred.
     */
    @CFunction(value = "cSunMiscSignal_close", transition = NO_TRANSITION)
    public static native int close();

    /** Wait for a notification on the semaphore. Prone to spurious wake-ups. */
    @CFunction("cSunMiscSignal_awaitSemaphore")
    public static native int awaitSemaphore();

    /** Notify a thread waiting on the semaphore. */
    @CFunction(value = "cSunMiscSignal_signalSemaphore", transition = NO_TRANSITION)
    public static native int signalSemaphore();

    /** Returns true if the signal is in the range of the counters. */
    @CFunction(value = "cSunMiscSignal_signalRangeCheck", transition = NO_TRANSITION)
    public static native boolean signalRangeCheck(int signal);

    /**
     * Returns the number of the first pending signal, or -1 if no signal is pending. May only be
     * called by a single thread (i.e., the signal dispatcher thread).
     */
    @CFunction(value = "cSunMiscSignal_checkPendingSignal", transition = NO_TRANSITION)
    public static native int checkPendingSignal();

    /** Returns a function pointer to the C signal handler. */
    @CFunction(value = "cSunMiscSignal_signalHandlerFunctionPointer", transition = NO_TRANSITION)
    public static native Signal.SignalDispatcher signalHandlerFunctionPointer();
}
