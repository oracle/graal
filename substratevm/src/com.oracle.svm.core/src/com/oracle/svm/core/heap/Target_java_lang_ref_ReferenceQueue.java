/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;

import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.UnknownClass;
import com.oracle.svm.core.jdk.UninterruptibleUtils;

/**
 * Substitution of {@link ReferenceQueue}. Implementation methods are in
 * {@link ReferenceQueueInternals} so that subclasses cannot interfere with them.
 */
@UnknownClass
@TargetClass(ReferenceQueue.class)
@Substitute
final class Target_java_lang_ref_ReferenceQueue<T> {

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = UninterruptibleUtils.AtomicReference.class) //
    final UninterruptibleUtils.AtomicReference<Reference<? extends T>> queueHead;

    @Substitute
    Target_java_lang_ref_ReferenceQueue() {
        queueHead = new UninterruptibleUtils.AtomicReference<>(null);
    }

    @Substitute
    Reference<? extends T> poll() {
        return ReferenceQueueInternals.doPoll(this);
    }

    @Substitute
    Reference<? extends T> remove() throws InterruptedException {
        return ReferenceQueueInternals.doRemove(this);
    }

    @Substitute
    Reference<? extends T> remove(long timeoutMillis) throws InterruptedException {
        return ReferenceQueueInternals.doRemove(this, timeoutMillis);
    }

    @Substitute
    boolean enqueue(Reference<? extends T> ref) {
        return ReferenceQueueInternals.doEnqueue(this, ref);
    }
}
