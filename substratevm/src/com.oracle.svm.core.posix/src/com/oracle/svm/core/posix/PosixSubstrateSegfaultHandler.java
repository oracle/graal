/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateSegfaultHandler;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.c.function.CEntryPointOptions.Publish;
import com.oracle.svm.core.graal.snippets.CEntryPointSnippets.IsolateCreationWatcher;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.Signal.AdvancedSignalDispatcher;
import com.oracle.svm.core.posix.headers.Signal.sigaction;
import com.oracle.svm.core.posix.headers.Signal.siginfo_t;
import com.oracle.svm.core.posix.headers.Signal.ucontext_t;

@AutomaticFeature
class PosixSubstrateSegfaultHandlerFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(SubstrateSegfaultHandler.class, new PosixSubstrateSegfaultHandler());
        if (SubstrateOptions.useLLVMBackend()) {
            ImageSingletons.add(IsolateCreationWatcher.class, new PosixSubstrateSegfaultHandler.SingleIsolateSegfaultIsolateSetup());
        }
    }
}

class PosixSubstrateSegfaultHandler extends SubstrateSegfaultHandler {
    @CEntryPoint
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in segfault signal handler.")
    @Uninterruptible(reason = "Must be uninterruptible until it gets immune to safepoints")
    private static void dispatch(@SuppressWarnings("unused") int signalNumber, @SuppressWarnings("unused") siginfo_t sigInfo, ucontext_t uContext) {
        if (SubstrateOptions.useLLVMBackend()) {
            Isolate isolate = ((SingleIsolateSegfaultIsolateSetup) ImageSingletons.lookup(IsolateCreationWatcher.class)).getIsolate();
            if (isolate.rawValue() == -1) {
                /* Multiple isolates registered, we can't know which one caused the segfault. */
                return;
            }
            CEntryPointActions.enterIsolateFromCrashHandler(isolate);
        } else if (!tryEnterIsolate(uContext)) {
            /* This means the segfault happened in native code, so we don't even try to dump. */
            return;
        }
        dump(uContext);
    }

    /** The address of the signal handler for signals handled by Java code, above. */
    private static final CEntryPointLiteral<AdvancedSignalDispatcher> advancedSignalDispatcher = CEntryPointLiteral.create(PosixSubstrateSegfaultHandler.class,
                    "dispatch", int.class, siginfo_t.class, ucontext_t.class);

    @Override
    protected void install() {
        int structSigActionSize = SizeOf.get(sigaction.class);
        sigaction structSigAction = StackValue.get(structSigActionSize);
        LibC.memset(structSigAction, WordFactory.signed(0), WordFactory.unsigned(structSigActionSize));
        /* Register sa_sigaction signal handler */
        structSigAction.sa_flags(Signal.SA_SIGINFO());
        structSigAction.sa_sigaction(advancedSignalDispatcher.getFunctionPointer());
        Signal.sigaction(Signal.SignalEnum.SIGSEGV, structSigAction, WordFactory.nullPointer());
    }

    static class SingleIsolateSegfaultIsolateSetup implements IsolateCreationWatcher {

        /**
         * Stores the address of the first isolate created. This is meant for the LLVM backend to
         * attempt to detect the current isolate when entering the SVM segfault handler. The value
         * is set to -1 when an additional isolate is created, as there is then no way of knowing in
         * which isolate a subsequent segfault occurs.
         */
        private static final CGlobalData<Pointer> baseIsolate = CGlobalDataFactory.createWord();

        @Override
        @Uninterruptible(reason = "Called from uninterruptible method")
        public void registerIsolate(Isolate isolate) {
            PointerBase value = baseIsolate.get().compareAndSwapWord(0, WordFactory.zero(), isolate, LocationIdentity.ANY_LOCATION);
            if (!value.isNull()) {
                baseIsolate.get().writeWord(0, WordFactory.signed(-1));
            }
        }

        @Uninterruptible(reason = "Called from uninterruptible method")
        public Isolate getIsolate() {
            return baseIsolate.get().readWord(0);
        }
    }
}
