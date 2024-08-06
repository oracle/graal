/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackFrameVisitor;
import com.oracle.svm.core.thread.VMThreads;

/** Walk the stack and verify all objects that are referenced from stack frames. */
final class StackVerifier {
    private static final StackFrameVerificationVisitor STACK_FRAME_VISITOR = new StackFrameVerificationVisitor();

    @Platforms(Platform.HOSTED_ONLY.class)
    private StackVerifier() {
    }

    @NeverInline("Starts a stack walk in the caller frame")
    public static boolean verifyAllThreads() {
        boolean result = true;

        STACK_FRAME_VISITOR.initialize();
        JavaStackWalker.walkCurrentThread(KnownIntrinsics.readCallerStackPointer(), STACK_FRAME_VISITOR);
        result &= STACK_FRAME_VISITOR.result;

        for (IsolateThread thread = VMThreads.firstThread(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
            if (thread == CurrentIsolate.getCurrentThread()) {
                continue;
            }

            STACK_FRAME_VISITOR.initialize();
            JavaStackWalker.walkThread(thread, STACK_FRAME_VISITOR);
            result &= STACK_FRAME_VISITOR.result;
        }
        return result;
    }

    private static class StackFrameVerificationVisitor extends StackFrameVisitor {
        private final VerifyFrameReferencesVisitor verifyFrameReferencesVisitor;

        private boolean result;

        @Platforms(Platform.HOSTED_ONLY.class)
        StackFrameVerificationVisitor() {
            verifyFrameReferencesVisitor = new VerifyFrameReferencesVisitor();
        }

        public void initialize() {
            this.result = true;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while verifying the stack.")
        public boolean visitRegularFrame(Pointer currentSP, CodePointer currentIP, CodeInfo codeInfo) {
            verifyFrameReferencesVisitor.initialize();
            CodeInfoTable.visitObjectReferences(currentSP, currentIP, codeInfo, verifyFrameReferencesVisitor);
            result &= verifyFrameReferencesVisitor.result;
            return true;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while verifying the stack.")
        protected boolean visitDeoptimizedFrame(Pointer originalSP, CodePointer deoptStubIP, DeoptimizedFrame deoptimizedFrame) {
            /* Nothing to do. */
            return true;
        }
    }

    private static class VerifyFrameReferencesVisitor implements ObjectReferenceVisitor {
        private boolean result;

        @Platforms(Platform.HOSTED_ONLY.class)
        VerifyFrameReferencesVisitor() {
        }

        public void initialize() {
            this.result = true;
        }

        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed, Object holderObject) {
            result &= HeapVerifier.verifyReference(holderObject, objRef, compressed);
            return true;
        }
    }
}
