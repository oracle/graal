/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.ContinuableMethodWithBytecode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoThreadLocalState;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.vm.continuation.HostFrameRecord;
import com.oracle.truffle.espresso.vm.continuation.UnwindContinuationException;

/**
 * VM entry point from the Continuation class, responsible for unwinding and rewinding the stack.
 */
@EspressoSubstitutions
public final class Target_org_graalvm_continuations_ContinuationImpl {
    @Substitution(hasReceiver = true)
    static void suspend0(StaticObject self, @Inject EspressoLanguage language, @Inject Meta meta) {
        EspressoThreadLocalState tls = language.getThreadLocalState();
        if (tls.isContinuationSuspensionBlocked()) {
            throw meta.throwExceptionWithMessage(meta.continuum.org_graalvm_continuations_IllegalContinuationStateException,
                            "Suspension is currently blocked by the presence of unsupported frames on the stack. " +
                                            "Check for synchronized blocks, native calls and VM intrinsics in the stack trace of this exception.");
        }

        // We don't want to let the user see our upcalls during the unwind process e.g. to do
        // reflection or create the FrameRecords in the guest.
        // Re-enabled in the catch clause of Continuation.resume0().
        // TODO(GR-54326): Let Truffle Instrumentation know.
        tls.disableSingleStepping();

        // This internal exception will be caught in BytecodeNode's interpreter loop. Frame records
        // will be added to the exception object in a linked list until it's caught in resume below.
        throw new UnwindContinuationException(self);
    }

    @Substitution(hasReceiver = true)
    abstract static class Resume0 extends SubstitutionNode {
        abstract boolean execute(StaticObject self);

        @SuppressWarnings("try")
        @Specialization
        static boolean resume0(StaticObject self,
                        @Bind("getLanguage()") EspressoLanguage lang, @Bind("getMeta()") Meta meta,
                        @Cached ContinuableMethodWithBytecode.ResumeNextContinuationNode rewind) {
            // This method is an intrinsic and the act of invoking one of those blocks the ability
            // to call suspend, so we have to undo that first.
            EspressoThreadLocalState tls = lang.getThreadLocalState();
            if (tls.isInContinuation()) {
                throw meta.throwExceptionWithMessage(meta.continuum.org_graalvm_continuations_IllegalContinuationStateException,
                                "Cannot resume a continuation while already running in a continuation.");
            }
            HostFrameRecord stack = getHFR(self, meta);
            if (stack == null) {
                throw meta.throwExceptionWithMessage(meta.continuum.org_graalvm_continuations_IllegalContinuationStateException, "Continuation was not properly dematerialized.");
            }
            assert stack.verify(meta, false);
            // Consume the stack.
            meta.continuum.HIDDEN_CONTINUATION_FRAME_RECORD.setHiddenObject(self, null, true);

            // Try-with-resources generates a call to 'Throwable.addSuppressed()', which is
            // blocklisted by SVM.
            EspressoThreadLocalState.ContinuationScope scope = tls.continuationScope();
            try {
                // Disable stepping until we have fully re-winded.
                // Re-enabled in ResumeNextContinuationNode.dolast()
                // TODO(GR-54326): Let Truffle Instrumentation know.
                tls.disableSingleStepping();
                rewind.execute(stack);
                // Normal completion.
                return false;
            } catch (UnwindContinuationException unwind) {
                assert unwind.getContinuation() == self;
                meta.continuum.HIDDEN_CONTINUATION_FRAME_RECORD.setHiddenObject(self, unwind.head);
                // Allow reporting of stepping in this thread again. It was blocked by the call to
                // suspend0()
                tls.enableSingleStepping();
                // Suspended
                return true;
            } finally {
                scope.close();
            }

        }

        private static HostFrameRecord getHFR(StaticObject self, Meta meta) {
            return (HostFrameRecord) meta.continuum.HIDDEN_CONTINUATION_FRAME_RECORD.getHiddenObject(self, true);
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class Start0 extends SubstitutionNode {
        abstract boolean execute(StaticObject self);

        // Try-with-resources generates a call to 'Throwable.addSuppressed()', which is blocklisted
        // by SVM.
        @SuppressWarnings("try")
        @Specialization
        boolean start0(StaticObject self,
                        @Bind("getMeta()") Meta meta, @Bind("getLanguage()") EspressoLanguage lang,
                        @Cached("create(meta.continuum.org_graalvm_continuations_ContinuationImpl_run.getCallTarget())") DirectCallNode runCall) {
            // This method is an intrinsic and the act of invoking one of those blocks the ability
            // to call suspend, so we have to undo that first.
            EspressoThreadLocalState tls = lang.getThreadLocalState();
            if (tls.isInContinuation()) {
                throw meta.throwExceptionWithMessage(meta.continuum.org_graalvm_continuations_IllegalContinuationStateException,
                                "Cannot resume a continuation while already running in a continuation.");
            }

            EspressoThreadLocalState.ContinuationScope scope = tls.continuationScope();
            try {
                // The run method is private in Continuation and is the continuation delimiter.
                // Frames from run onwards will be unwound on suspend, and rewound on resume.
                runCall.call(self);
                // Normal completion
                return false;
            } catch (UnwindContinuationException unwind) {
                assert unwind.getContinuation() == self;
                // Guest called suspend(). By the time we get here the frame info has been gathered
                // up into host-side objects so we just need to copy the data into the guest world.
                meta.continuum.HIDDEN_CONTINUATION_FRAME_RECORD.setHiddenObject(self, unwind.head);
                // Allow reporting of stepping in this thread again. It was blocked by the call to
                // suspend0()
                tls.enableSingleStepping();
                // Suspended
                return true;
            } finally {
                scope.close();
            }
        }
    }

    @Substitution(hasReceiver = true)
    static void materialize0(StaticObject self, @Inject Meta meta) {
        HostFrameRecord hfr = (HostFrameRecord) meta.continuum.HIDDEN_CONTINUATION_FRAME_RECORD.getHiddenObject(self, true);
        if (hfr == null) {
            // no host frame to materialize
            return;
        }
        if (!StaticObject.isNull(meta.continuum.org_graalvm_continuations_ContinuationImpl_stackFrameHead.getObject(self))) {
            throw meta.throwExceptionWithMessage(meta.continuum.org_graalvm_continuations_IllegalMaterializedRecordException,
                            "Somehow, both guest and host records are alive at the same time.");
        }
        StaticObject guestRecord = hfr.copyToGuest(meta);
        // If successful, we can clear the host record
        meta.continuum.HIDDEN_CONTINUATION_FRAME_RECORD.setHiddenObject(self, null, true);
        meta.continuum.org_graalvm_continuations_ContinuationImpl_stackFrameHead.setObject(self, guestRecord);
    }

    @Substitution(hasReceiver = true)
    static void dematerialize0(StaticObject self, @Inject Meta meta, @Inject EspressoContext context) {
        StaticObject guestRecord = meta.continuum.org_graalvm_continuations_ContinuationImpl_stackFrameHead.getObject(self);
        if (StaticObject.isNull(guestRecord)) {
            // no guest frame to dematerialize
            return;
        }
        if (meta.continuum.HIDDEN_CONTINUATION_FRAME_RECORD.getHiddenObject(self, true) != null) {
            throw meta.throwExceptionWithMessage(meta.continuum.org_graalvm_continuations_IllegalMaterializedRecordException,
                            "Somehow, both guest and host records are alive at the same time.");
        }
        HostFrameRecord hfr = HostFrameRecord.copyFromGuest(self, meta, context);
        // if successful, we can clear the guest side record
        meta.continuum.org_graalvm_continuations_ContinuationImpl_stackFrameHead.setObject(self, StaticObject.NULL);
        meta.continuum.HIDDEN_CONTINUATION_FRAME_RECORD.setHiddenObject(self, hfr, true);
    }
}
