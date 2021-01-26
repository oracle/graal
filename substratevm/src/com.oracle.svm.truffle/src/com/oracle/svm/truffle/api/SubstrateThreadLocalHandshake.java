/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.api;

import static com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets.TLAB_LOCATIONS;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;

import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.nodes.CodeSynchronizationNode;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.VMThreads.ActionOnTransitionToJavaSupport;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;
import com.oracle.truffle.api.impl.ThreadLocalHandshake;

public final class SubstrateThreadLocalHandshake extends ThreadLocalHandshake {

    // TODO should it be reexectuable?
    public static final SubstrateForeignCallDescriptor FOREIGN_POLL = SnippetRuntime.findForeignCall(SubstrateThreadLocalHandshake.class, "pollStub", false, TLAB_LOCATIONS);

    static final SubstrateThreadLocalHandshake INSTANCE = new SubstrateThreadLocalHandshake();

    static final FastThreadLocalInt PENDING = FastThreadLocalFactory.createInt().setMaxOffset(FastThreadLocal.FIRST_CACHE_LINE);
    static final FastThreadLocalInt DISABLED = FastThreadLocalFactory.createInt().setMaxOffset(FastThreadLocal.FIRST_CACHE_LINE);

    @Override
    public void poll() {
        if (PENDING.get() != 0 && DISABLED.get() == 0) {
            invokeProcessHandshake();
        }
    }

    /** Foreign call: {@link #FOREIGN_POLL}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    @Uninterruptible(reason = "Must not contain safepoint checks")
    private static void pollStub() throws Throwable {
        try {
            invokeProcessHandshake();
        } catch (Throwable t) {
            throw t;
        } finally {
            if (ActionOnTransitionToJavaSupport.isActionPending()) {
                assert ActionOnTransitionToJavaSupport.isSynchronizeCode() : "Unexpected action pending.";
                CodeSynchronizationNode.synchronizeCode();
                ActionOnTransitionToJavaSupport.clearActions();
            }
        }

    }

    @Uninterruptible(reason = "Used both from uninterruptable stub and normal execution.", callerMustBe = false, calleeMustBe = false)
    @RestrictHeapAccess(reason = "Callee may allocate", access = RestrictHeapAccess.Access.UNRESTRICTED, overridesCallers = true)
    @NeverInline("Should not be inlined. Invoked only on the slow-path.")
    private static void invokeProcessHandshake() {
        INSTANCE.processHandshake();
    }

    @Override
    protected void clearPending() {
        setPending(CurrentIsolate.getCurrentThread(), 0);
    }

    @Override
    protected void setPending(Thread t) {
        IsolateThread isolateThread = JavaThreads.fromJavaThread(t);
        setPending(isolateThread, 1);
    }

    private static void setPending(IsolateThread t, int value) {
        int prev;
        do {
            prev = PENDING.getVolatile(t);
        } while (!PENDING.compareAndSet(t, prev, value));
    }

    @Override
    public boolean setDisabled(boolean disabled) {
        int prev = DISABLED.get();
        DISABLED.set(disabled ? 1 : 0);
        return prev != 0;
    }

}
