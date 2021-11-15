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
package com.oracle.svm.core.thread;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationImpl;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

public final class Continuation {
    public StoredContinuation stored;

    /**
     * Frame pointer of
     * {@link Target_java_lang_Continuation#enterSpecial(Target_java_lang_Continuation, boolean)} if
     * continuation is running, else frame pointer of
     * {@link LoomSupport#yield(Target_java_lang_Continuation)}.
     */
    Pointer sp;
    CodePointer ip;
    private boolean done;

    int tryPreempt(Thread thread) {
        TryPreemptThunk thunk = new TryPreemptThunk(this, thread);
        JavaVMOperation.enqueueBlockingSafepoint("tryForceYield0", thunk);
        return thunk.preemptStatus;
    }

    @NeverInline("access stack pointer")
    Integer yield() {
        Pointer leafSP = KnownIntrinsics.readCallerStackPointer();
        CodePointer leafIP = KnownIntrinsics.readReturnAddress();

        Pointer rootSP = sp;
        CodePointer rootIP = ip;

        int preemptStatus = StoredContinuationImpl.allocateFromCurrentStack(this, rootSP, leafSP, leafIP);
        if (preemptStatus != 0) {
            return preemptStatus;
        }

        sp = leafSP;
        ip = leafIP;

        KnownIntrinsics.farReturn(0, rootSP, rootIP, false);
        throw VMError.shouldNotReachHere("value should be returned by `farReturn`");
    }

    public boolean isStarted() {
        return sp.isNonNull();
    }

    public boolean isEmpty() {
        return sp.isNull();
    }

    public void finish() {
        done = true;
        sp = WordFactory.nullPointer();
        ip = WordFactory.nullPointer();
        assert isEmpty();
    }

    static class TryPreemptThunk implements SubstrateUtil.Thunk {
        int preemptStatus = JavaContinuations.YIELD_SUCCESS;

        final Continuation cont;
        final Thread thread;

        TryPreemptThunk(Continuation cont, Thread thread) {
            this.cont = cont;
            this.thread = thread;
        }

        @Override
        public void invoke() {
            IsolateThread vmThread = JavaThreads.getIsolateThread(thread);
            Pointer rootSP = cont.sp;
            CodePointer rootIP = cont.ip;
            preemptStatus = StoredContinuationImpl.allocateFromForeignStack(cont, rootSP, vmThread);
            if (preemptStatus == 0) {
                VMThreads.ActionOnExitSafepointSupport.setSwitchStack(vmThread);
                VMThreads.ActionOnExitSafepointSupport.setSwitchStackTarget(vmThread, rootSP, rootIP);
            }
        }
    }
}
