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

import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.thread.VMOperation;

@TargetClass(java.lang.ref.ReferenceQueue.class)
@Substitute
final class Target_java_lang_ref_ReferenceQueue {

    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = FeebleReferenceList.class)//
    protected final FeebleReferenceList<Object> feeble;

    @Substitute
    protected Target_java_lang_ref_ReferenceQueue() {
        this.feeble = FeebleReferenceList.factory();
    }

    @Substitute
    public Object poll() {
        return ReferenceWrapper.unwrap(feeble.pop());
    }

    @Substitute
    public Object remove() throws InterruptedException {
        VMOperation.guaranteeNotInProgress("Calling ReferenceQueue.remove() inside a VMOperation could block the VM operation thread and cause a deadlock.");
        return ReferenceWrapper.unwrap(feeble.remove());
    }

    @Substitute
    public Object remove(long timeoutMillis) throws InterruptedException {
        VMOperation.guaranteeNotInProgress("Calling ReferenceQueue.remove(long) inside a VMOperation could block the VM operation thread and cause a deadlock.");
        return ReferenceWrapper.unwrap(feeble.remove(timeoutMillis));
    }

    @KeepOriginal
    native boolean enqueue(Reference<?> r);

    public boolean isEmpty() {
        return feeble.isEmpty();
    }
}
