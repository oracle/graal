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

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;

/**
 * The interface that Java code needs to the C sun.miscSignal handler.
 *
 * With renames to translate C underscores to Java dots.
 */
@CContext(PosixDirectives.class)
public class CSunMiscSignal {

    /* Open the C signal handler mechanism. */
    @CFunction("cSunMiscSignal_open")
    public static native int open();

    /* Close the C signal handler mechanism. */
    @CFunction("cSunMiscSignal_close")
    public static native int close();

    /* Wait for a notification on the semaphore. */
    @CFunction("cSunMiscSignal_await")
    public static native int await();

    /* Notify a thread waiting on the semaphore. */
    @CFunction("cSunMiscSignal_post")
    public static native int post();

    /* Returns 1 if the signal is in the range of the counters, 0 otherwise. */
    @CFunction("cSunMiscSignal_signalRangeCheck")
    public static native int signalRangeCheck(int signal);

    /* Return the count of outstanding signals. Returns -1 if the signal is out of bounds. */
    @CFunction("cSunMiscSignal_getCount")
    public static native long getCount(int signal);

    /*
     * Decrement a counter towards zero, given a signal number. Returns the previous value, or -1 if
     * the signal is out of bounds.
     */
    @CFunction("cSunMiscSignal_decrementCount")
    public static native long decrementCount(int signal);

    /* Return the address of the counting signal handler. */
    @CFunction("cSunMiscSignal_countingHandlerFunctionPointer")
    public static native Signal.SignalDispatcher countingHandlerFunctionPointer();

}
