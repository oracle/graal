/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Uninterruptible;

/**
 * An immutable VM operation that lives in the image heap. All mutable state is kept in native
 * memory (see {@linkplain NativeVMOperationData}). This construct is used in places where we can't
 * allocate any Java objects (e.g., when we need to do a GC).
 */
public abstract class NativeVMOperation extends VMOperation {
    @Platforms(value = Platform.HOSTED_ONLY.class)
    protected NativeVMOperation(String name, SystemEffect systemEffect) {
        super(name, systemEffect);
    }

    public void enqueue(NativeVMOperationData data) {
        assert data.getNativeVMOperation() == this;
        VMOperationControl.get().enqueue(data);
    }

    @Uninterruptible(reason = "Called from a non-Java thread.")
    public void enqueueFromNonJavaThread(NativeVMOperationData data) {
        assert data.getNativeVMOperation() == this;
        VMOperationControl.get().enqueueFromNonJavaThread(data);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    protected boolean isFinished(NativeVMOperationData data) {
        return data.getFinished();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void setFinished(NativeVMOperationData data, boolean value) {
        data.setFinished(value);
    }

    @Override
    protected IsolateThread getQueuingThread(NativeVMOperationData data) {
        return data.getQueuingThread();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void setQueuingThread(NativeVMOperationData data, IsolateThread value) {
        data.setQueuingThread(value);
    }
}
