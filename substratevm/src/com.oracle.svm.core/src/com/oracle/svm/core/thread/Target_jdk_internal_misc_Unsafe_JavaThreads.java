/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.thread;

import java.util.concurrent.locks.LockSupport;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.events.ThreadParkEvent;
import com.oracle.svm.core.util.TimeUtils;

@TargetClass(className = "jdk.internal.misc.Unsafe")
@Platforms(InternalPlatform.NATIVE_ONLY.class)
@SuppressWarnings({"static-method"})
final class Target_jdk_internal_misc_Unsafe_JavaThreads {

    /**
     * Block current thread, returning when a balancing <tt>unpark</tt> occurs, or a balancing
     * <tt>unpark</tt> has already occurred, or the thread is interrupted, or, if not absolute and
     * time is not zero, the given time nanoseconds have elapsed, or if absolute, the given deadline
     * in milliseconds since Epoch has passed, or spuriously (i.e., returning for no "reason").
     * Note: This operation is in the Unsafe class only because <tt>unpark</tt> is, so it would be
     * strange to place it elsewhere.
     */
    @Substitute
    void park(boolean isAbsolute, long time) {
        long startTicks = JfrTicks.elapsedTicks();
        Thread t = Thread.currentThread();
        Object parkBlocker = LockSupport.getBlocker(t);

        /* Decide what kind of park I am doing. */
        if (!isAbsolute && time == 0L) {
            /* Park without deadline. */
            PlatformThreads.parkCurrentPlatformOrCarrierThread();
            ThreadParkEvent.emit(startTicks, parkBlocker, Long.MIN_VALUE, Long.MIN_VALUE);
        } else {
            /* Park with deadline. */
            final long delayNanos = TimeUtils.delayNanos(isAbsolute, time);
            PlatformThreads.parkCurrentPlatformOrCarrierThread(delayNanos);
            if (isAbsolute) {
                ThreadParkEvent.emit(startTicks, parkBlocker, Long.MIN_VALUE, time);
            } else {
                ThreadParkEvent.emit(startTicks, parkBlocker, time, Long.MIN_VALUE);
            }
        }
        /*
         * Unsafe.park does not distinguish between timing out, being unparked, and being
         * interrupted, but the thread's interrupt status must be preserved.
         */
    }

    /**
     * Unblock the given thread blocked on <tt>park</tt>, or, if it is not blocked, cause the
     * subsequent call to <tt>park</tt> not to block. Note: this operation is "unsafe" solely
     * because the caller must somehow ensure that the thread has not been destroyed. Nothing
     * special is usually required to ensure this when called from Java (in which there will
     * ordinarily be a live reference to the thread) but this is not nearly-automatically so when
     * calling from native code.
     *
     * @param threadObj the thread to unpark.
     */
    @Substitute
    void unpark(Object threadObj) {
        if (threadObj != null) {
            if (!(threadObj instanceof Thread)) {
                throw new IllegalArgumentException("Unsafe.unpark(!(thread instanceof Thread))");
            }
            Thread thread = (Thread) threadObj;
            if (!VirtualThreads.isSupported() || !VirtualThreads.singleton().isVirtual(thread)) {
                PlatformThreads.unpark(thread);
            }
        }
    }
}
