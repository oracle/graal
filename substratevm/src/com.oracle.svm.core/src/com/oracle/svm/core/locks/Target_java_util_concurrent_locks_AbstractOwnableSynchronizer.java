/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.locks;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.thread.JavaThreads;

import java.util.concurrent.locks.AbstractOwnableSynchronizer;

/**
 * {@link java.util.concurrent.locks.AbstractOwnableSynchronizer} is substituted in order to track
 * the locks per thread when JMX support is enabled.
 */
@SuppressWarnings({"unused"})
@TargetClass(value = java.util.concurrent.locks.AbstractOwnableSynchronizer.class)
public final class Target_java_util_concurrent_locks_AbstractOwnableSynchronizer {

    /**
     * The current owner of exclusive mode synchronization.
     */
    @Alias //
    private transient Thread exclusiveOwnerThread;

    /**
     * Sets the thread that currently owns exclusive access. A {@code null} argument indicates that
     * no thread owns access. This method does not otherwise impose any synchronization or
     * {@code volatile} field accesses.
     * 
     * @param thread the owner thread
     */
    @Substitute()
    protected void setExclusiveOwnerThread(Thread thread) {
        if (thread == null && exclusiveOwnerThread != null) {
            JavaThreads.JMXMonitoring.removeThreadLock(exclusiveOwnerThread,
                            SubstrateUtil.cast(this, AbstractOwnableSynchronizer.class));
        } else {
            JavaThreads.JMXMonitoring.addThreadLock(thread,
                            SubstrateUtil.cast(this, AbstractOwnableSynchronizer.class));
        }
        exclusiveOwnerThread = thread;
    }

    /**
     * Returns the thread last set by {@code setExclusiveOwnerThread}, or {@code null} if never set.
     * This method does not otherwise impose any synchronization or {@code volatile} field accesses.
     * 
     * @return the owner thread
     */
    @Alias
    public Thread getExclusiveOwnerThread() {
        return exclusiveOwnerThread;
    }
}
