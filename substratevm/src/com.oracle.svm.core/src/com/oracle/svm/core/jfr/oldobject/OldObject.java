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

final class OldObject {
    static final OldObject EMPTY = new OldObject();

    WeakReference<?> reference;
    long span;
    long objectSize;
    long allocationTime; // nanoseconds
    long threadId;
    long stackTraceId;
    long heapUsedAtLastGC;
    int arrayLength;
    OldObject previous;

    @Uninterruptible(reason = "Accesses allocation profiler.")
    void set(WeakReference<?> ref, long allocatedSize, long allocatedTime, long threadId, long stackTraceId, long heapUsedAtLastGC, int arrayLength) {
        this.reference = ref;
        this.span = allocatedSize;
        this.objectSize = allocatedSize;
        this.allocationTime = allocatedTime;
        this.threadId = threadId;
        this.stackTraceId = stackTraceId;
        this.heapUsedAtLastGC = heapUsedAtLastGC;
        this.arrayLength = arrayLength;
    }

    @Uninterruptible(reason = "Accesses allocation profiler.")
    void clear() {
        this.reference = null;
        this.span = 0L;
        this.objectSize = 0L;
        this.allocationTime = 0L;
        this.threadId = 0L;
        this.stackTraceId = 0L;
        this.heapUsedAtLastGC = 0L;
        this.arrayLength = 0;
        this.previous = null;
    }
}
