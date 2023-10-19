/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

import jdk.compiler.graal.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.stack.StackFrameVisitor;

/** Operations on virtual threads. */
public interface VirtualThreads {
    @Fold
    static VirtualThreads singleton() {
        ContinuationsFeature.abortIfUnsupported();
        return ImageSingletons.lookup(VirtualThreads.class);
    }

    @Fold
    static boolean isSupported() {
        return ImageSingletons.contains(VirtualThreads.class);
    }

    ThreadFactory createFactory();

    boolean isVirtual(Thread thread);

    boolean getAndClearInterrupt(Thread thread);

    void join(Thread thread, long millis) throws InterruptedException;

    void pinCurrent();

    void unpinCurrent();

    boolean isCurrentPinned();

    Executor getScheduler(Thread thread);

    void blockedOn(Target_sun_nio_ch_Interruptible b);

    /**
     * Virtual thread support must be able to support virtual thread stack frames from its carrier
     * thread, therefore this method is also responsible for taking platform thread stack traces.
     */
    StackTraceElement[] getVirtualOrPlatformThreadStackTrace(boolean filterExceptions, Thread thread, Pointer callerSP);

    void visitCurrentVirtualOrPlatformThreadStackFrames(Pointer callerSP, StackFrameVisitor visitor);

    StackTraceElement[] getVirtualOrPlatformThreadStackTraceAtSafepoint(Thread thread, Pointer callerSP);
}
