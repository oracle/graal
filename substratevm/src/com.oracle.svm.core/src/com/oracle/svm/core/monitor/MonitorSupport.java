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
package com.oracle.svm.core.monitor;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Uninterruptible;

/**
 * This interface provides functions related to monitor operations (the Java "synchronized" keyword
 * and related functions in the JDK) that are invoked by other parts of the VM.
 */
public abstract class MonitorSupport {

    @Fold
    public static MonitorSupport singleton() {
        return ImageSingletons.lookup(MonitorSupport.class);
    }

    /**
     * Implements the semantics of the monitorenter bytecode.
     */
    public abstract void monitorEnter(Object obj);

    /**
     * Implements the semantics of the monitorexit bytecode.
     */
    public abstract void monitorExit(Object obj);

    /*
     * Support for objects that are re-locked during deoptimization. This method is called when
     * preparing the {@link DeoptimizedFrame}. This is done in a VM operation and can therefore run
     * in any thread. The actual locking therefore should be done in {@link #doRelockObject}.
     * However, {@link #doRelockObject} is {@link Uninterruptible} and therefore must not allocate.
     * Any allocation of a lock object must therefore be done early in this method.
     * 
     * The object returned by this method is passed as a parameter to {@link #doRelockObject}.
     */
    public abstract Object prepareRelockObject(Object obj);

    /*
     * Support for objects that are re-locked during deoptimization. This method is called just
     * before returning to the deoptimized frame, i.e., it always runs in the thread that either
     * already is the owner of the monitor, or must be the owner of the monitor after this method.
     * 
     * @param lockData The value returned by a prior call to {@link #prepareRelockObject} for the
     * object.
     */
    @Uninterruptible(reason = "called during deoptimization")
    public abstract void doRelockObject(Object obj, Object lockData);

    /** Implements the semantics of {@link Thread#holdsLock}. */
    public abstract boolean isLockedByCurrentThread(Object obj);

    /** Determines whether the object's monitor is locked by the current or any other thread. */
    public abstract boolean isLockedByAnyThread(Object obj);

    /**
     * Implements the semantics of {@link Object#wait}.
     */
    public final void wait(Object obj, long timeoutMillis) throws InterruptedException {
        /* Required checks on the arguments. */
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("Timeout is negative.");
        }

        doWait(obj, timeoutMillis);
    }

    protected abstract void doWait(Object obj, long timeoutMillis) throws InterruptedException;

    /**
     * Implements the semantics of {@link Object#notify} and {@link Object#notifyAll}.
     */
    public abstract void notify(Object obj, boolean notifyAll);

    /**
     * Called from {@code Unsafe.park} when changing the current thread's state before parking the
     * thread. When the thread is parked due to a monitor operation, we need to alter the new thread
     * state so {@link Thread#getState()} gives the expected result.
     */
    public abstract int maybeAdjustNewParkStatus(int status);
}
