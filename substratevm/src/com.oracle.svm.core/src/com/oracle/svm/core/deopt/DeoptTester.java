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
package com.oracle.svm.core.deopt;

import java.util.HashSet;
import java.util.Set;

import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackFrameVisitor;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMOperationControl;

/**
 * Utility class for deoptimization stress test. Used if the DeoptimizeAll option is set.
 */
public class DeoptTester {

    private static final Set<Long> handledPCs = new HashSet<>();

    private static int inDeoptTest;

    private static final StackFrameVisitor collectPcVisitor = new StackFrameVisitor() {

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, overridesCallers = true, reason = "Only deals with IPs, not Objects.")
        public boolean visitFrame(Pointer sp, CodePointer ip, DeoptimizedFrame deoptimizedFrame) {
            handledPCs.add(ip.rawValue());
            return true;
        }
    };

    /**
     * Scans the stack frames and if there are some new (= so far not seen) PCs inside deoptimizable
     * methods, a deopt is done.
     *
     * Foreign call: {@link SnippetRuntime#DEOPTTEST}.
     */
    @NeverInline("deoptTest must have a separate stack frame")
    @SubstrateForeignCallTarget
    public static void deoptTest() {
        if (inDeoptTest > 0) {
            return;
        }
        inDeoptTest++;
        try {

            if (Heap.getHeap().isAllocationDisallowed()) {
                return;
            }
            if (VMOperationControl.TestingBackdoor.isLocked()) {
                return;
            }
            if (VMOperation.isInProgress()) {
                return;
            }

            Pointer startSp = KnownIntrinsics.readCallerStackPointer();
            CodePointer startIp = KnownIntrinsics.readReturnAddress();

            int numHandledPCs = handledPCs.size();

            JavaStackWalker.walkCurrentThread(startSp, startIp, collectPcVisitor);

            if (handledPCs.size() > numHandledPCs) {
                Deoptimizer.deoptimizeAll();
            }

        } finally {
            inDeoptTest--;
        }
    }

    /*
     * These methods are a temporary workaround until we have verification in place that ensures
     * uninterruptible (= safepoint free) methods only call other uninterruptible methods.
     */

    public static void disableDeoptTesting() {
        inDeoptTest++;
    }

    public static void enableDeoptTesting() {
        inDeoptTest--;
    }
}
