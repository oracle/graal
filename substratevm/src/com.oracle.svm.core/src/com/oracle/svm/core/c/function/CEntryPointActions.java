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
package com.oracle.svm.core.c.function;

import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.thread.JavaThreads;

/**
 * Advanced entry and leave actions for entry point methods annotated with {@link CEntryPoint}.
 * These methods are an alternative to automatically entering and leaving a context that is passed
 * as a parameter, and they also enable creating an isolate or attaching a method on demand. The
 * methods of this class must be called from the {@link CEntryPointOptions#prologue() prologue} or
 * {@link CEntryPointOptions#epilogue() epilogue} code of the entry point, or, if the entry point
 * method is annotated with {@link Uninterruptible}, from that method itself.
 *
 * @see CEntryPointSetup
 */
public final class CEntryPointActions {
    private CEntryPointActions() {
    }

    /**
     * Creates a new isolate, then {@linkplain #enterAttachThread attaches} the current thread to
     * the created isolate, creating a context for the thread in the isolate, and then enters that
     * context before returning.
     *
     * @param params initialization parameters.
     * @return 0 on success, otherwise non-zero.
     */
    public static native int enterCreateIsolate(CEntryPointCreateIsolateParameters params);

    /**
     * Creates a context for the current thread in the specified existing isolate, then enters that
     * context. If the thread has already been attached, this does not cause the operation to fail.
     *
     * @param isolate an existing isolate.
     * @param ensureJavaThread when set to true, the method ensures that the
     *            {@link java.lang.Thread} object for the newly attached thread is created. If the
     *            parameter is set to false, a later call to one of the
     *            {@link JavaThreads#ensureJavaThread} methods early after the prologue must be used
     *            to do the initialization manually.
     * @return 0 on success, otherwise non-zero.
     */
    public static native int enterAttachThread(Isolate isolate, boolean ensureJavaThread);

    /**
     * Enters an existing context for the current thread (for example, one created with
     * {@link #enterAttachThread}).
     *
     * @param thread existing context for the current thread.
     * @return 0 on success, otherwise non-zero.
     */
    public static native int enter(IsolateThread thread);

    /**
     * Enters an existing context for the current thread that has already been created in the given
     * isolate.
     *
     * @param isolate isolate in which a context for the current thread exists.
     * @return 0 on success, otherwise non-zero.
     */
    public static native int enterIsolate(Isolate isolate);

    /**
     * May only be used during the prologue of a segfault handler. If the thread is already
     * attached, it enters the existing context of that thread. If the thread is unattached, it
     * creates a context that is sufficient for executing the segfault handler. After executing the
     * segfault handler, execution must not resume normally.
     *
     * @param isolate isolate in which a context for the current thread exists.
     * @return 0 on success, otherwise non-zero.
     */
    public static native int enterAttachThreadFromCrashHandler(Isolate isolate);

    /**
     * Leaves the current thread's current context.
     *
     * @return 0 on success, otherwise non-zero.
     */
    public static native int leave();

    /**
     * Leaves the current thread's current context, then discards that context.
     *
     * @return 0 on success, otherwise non-zero.
     */
    public static native int leaveDetachThread();

    /**
     * Leaves the current thread's current context, then waits for all attached threads in the
     * context's isolate to detach and discards that isolate entirely.
     *
     * @return 0 on success, otherwise non-zero.
     */
    public static native int leaveTearDownIsolate();

    /**
     * Fail in a fatal manner, such as by terminating the executing process. This method is intended
     * for situations in which recovery is not possible, or in which reporting a severe error in any
     * other way is not possible. This method does not return.
     *
     * @param code An integer representing the cause (should be non-zero by convention).
     * @param message A message describing the cause (may be omitted by passing
     *            {@link WordFactory#nullPointer() null}).
     */
    public static native void failFatally(int code, CCharPointer message);

    /**
     * @return whether the current thread is attached to the specified isolate.
     */
    public static native boolean isCurrentThreadAttachedTo(Isolate isolate);
}
