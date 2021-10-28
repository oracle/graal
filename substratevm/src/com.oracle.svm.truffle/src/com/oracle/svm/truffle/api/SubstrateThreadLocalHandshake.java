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
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.deopt.DeoptimizationRuntime;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.util.VMError;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.impl.ThreadLocalHandshake;
import com.oracle.truffle.api.nodes.Node;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.SpeculationLog;

public final class SubstrateThreadLocalHandshake extends ThreadLocalHandshake {

    public static final SubstrateForeignCallDescriptor FOREIGN_POLL = SnippetRuntime.findForeignCall(SubstrateThreadLocalHandshake.class, "pollStub", true, TLAB_LOCATIONS);

    static final SubstrateThreadLocalHandshake SINGLETON = new SubstrateThreadLocalHandshake();

    static final FastThreadLocalInt PENDING = FastThreadLocalFactory.createInt("SubstrateThreadLocalHandshake.PENDING").setMaxOffset(FastThreadLocal.FIRST_CACHE_LINE);
    static final FastThreadLocalObject<TruffleSafepointImpl> STATE = FastThreadLocalFactory.createObject(TruffleSafepointImpl.class, "SubstrateThreadLocalHandshake.STATE")
                    .setMaxOffset(FastThreadLocal.FIRST_CACHE_LINE);

    @Platforms(Platform.HOSTED_ONLY.class)//
    static final ThreadLocal<Boolean> HOSTED_PENDING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Platforms(Platform.HOSTED_ONLY.class)//
    private static final ThreadLocal<TruffleSafepointImpl> HOSTED_STATE = ThreadLocal.withInitial(() -> SINGLETON.getThreadState(Thread.currentThread()));

    @Override
    public void poll(Node location) {
        if (SubstrateUtil.HOSTED) {
            if (HOSTED_PENDING.get()) {
                invokeProcessHandshake(location);
            }
        } else {
            if (PENDING.get() != 0) {
                invokeProcessHandshake(location);
            }
        }
    }

    /** Foreign call: {@link #FOREIGN_POLL}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    @Uninterruptible(reason = "Must not contain safepoint checks", calleeMustBe = false)
    @NeverInline("Reads stack pointer")
    private static void pollStub(Object location) throws Throwable {
        try {
            invokeProcessHandshake(location);
        } catch (Throwable t) {

            /*
             * We need to deoptimize the caller here as the caller is likely not prepared for an
             * exception to be thrown.
             */
            StackOverflowCheck.singleton().makeYellowZoneAvailable();
            try {
                Pointer sp = KnownIntrinsics.readCallerStackPointer();
                if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
                    Log.log().string("trace deopt enabled ").newline().flush();
                    CodePointer ip = KnownIntrinsics.readReturnAddress();
                    long reason = Deoptimizer.encodeDeoptActionAndReasonToLong(DeoptimizationAction.None, DeoptimizationReason.TransferToInterpreter, 0);
                    DeoptimizationRuntime.traceDeoptimization(reason, SpeculationLog.NO_SPECULATION.getReason(), DeoptimizationAction.None, sp, ip);
                }
                Deoptimizer.deoptimizeFrame(sp, false, SpeculationLog.NO_SPECULATION.getReason());
                if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
                    Log.log().string("]").newline();
                }
            } finally {
                StackOverflowCheck.singleton().protectYellowZone();
            }
            throw t;
        }
    }

    @Uninterruptible(reason = "Used both from uninterruptable stub.", calleeMustBe = false)
    @RestrictHeapAccess(reason = "Callee may allocate", access = RestrictHeapAccess.Access.UNRESTRICTED)
    private static void invokeProcessHandshake(Object enclosingNode) {
        SINGLETON.processHandshake((Node) enclosingNode);
    }

    @Override
    public void ensureThreadInitialized() {
        if (!SubstrateUtil.HOSTED) {
            STATE.set(getThreadState(Thread.currentThread()));
        }
    }

    @Override
    public TruffleSafepointImpl getCurrent() {
        if (SubstrateUtil.HOSTED) {
            return HOSTED_STATE.get();
        } else {
            TruffleSafepointImpl state = STATE.get();
            assert state != null;
            if (state == null) {
                throw CompilerDirectives.shouldNotReachHere("Thread local handshake is not initialized for this thread. " +
                                "Did you call getCurrent() outside while a polyglot context not entered?");
            }
            return state;
        }
    }

    @Override
    protected void clearFastPending() {
        if (SubstrateUtil.HOSTED) {
            HOSTED_PENDING.set(Boolean.FALSE);
        } else {
            PENDING.setVolatile(CurrentIsolate.getCurrentThread(), 0);
        }
    }

    @Override
    protected void setFastPending(Thread t) {
        if (SubstrateUtil.HOSTED) {
            HOSTED_PENDING.set(Boolean.TRUE);
        } else {
            /*
             * The thread will not go away here because the Truffle implementation ensures that this
             * method is no longer used if the thread is no longer active. It only sets this state
             * for contexts that are currently entered on a thread. Being entered implies that the
             * thread is active.
             */
            assert t.isAlive() : "thread must remain alive while setting fast pending";
            IsolateThread isolateThread = JavaThreads.getIsolateThreadUnsafe(t);
            VMError.guarantee(isolateThread.isNonNull(), "Java thread must remain alive.");
            PENDING.setVolatile(isolateThread, 1);
        }

    }
}
