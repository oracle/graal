/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;

import java.lang.ref.WeakReference;

public interface OldObjectEffects {
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    long elapsedTicks();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    Object getWeakReferent(WeakReference<?> ref);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void emit(Object aliveObject, long timestamp, long allocatedSize, long allocationTime, long threadId, long stackTraceId, long heapUsedAtLastGC, int arrayLength);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean isDead(WeakReference<?> ref);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    default boolean isAlive(WeakReference<?> ref) {
        return !isDead(ref);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    long getStackTraceId();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    long getThreadId(Thread thread);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    long getHeapUsedAtLastGC();
}
