/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.resident.impl;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackFrameVisitor;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.PlatformThreads;
import org.graalvm.nativeimage.IsolateThread;

public final class SafeStackWalker {
    @NeverInline("Starting a stack walk in the caller frame")
    public static void safeStackWalk(Thread targetThread, StackFrameVisitor visitor) {
        assert targetThread != null;
        if (targetThread == Thread.currentThread()) {
            JavaStackWalker.walkCurrentThread(KnownIntrinsics.readCallerStackPointer(), visitor);
        } else {
            // Stack-walking another (suspended) thread requires a safepoint.
            StackWalkOperation stackWalkOp = new StackWalkOperation(targetThread, visitor);
            stackWalkOp.enqueue();
        }
    }

    @InternalVMMethod
    private static final class StackWalkOperation extends JavaVMOperation {
        private final Thread thread;
        private final StackFrameVisitor visitor;

        StackWalkOperation(Thread thread, StackFrameVisitor visitor) {
            super(VMOperationInfos.get(StackWalkOperation.class, "Stack walking", SystemEffect.SAFEPOINT));
            this.thread = thread;
            this.visitor = visitor;
        }

        @Override
        @NeverInline("Starting a stack walk.")
        protected void operate() {
            assert thread.isAlive();
            IsolateThread isolateThread = PlatformThreads.getIsolateThreadUnsafe(thread);
            JavaStackWalker.walkThread(isolateThread, visitor);
        }
    }
}
