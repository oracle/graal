/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import static com.oracle.svm.core.heap.RestrictHeapAccess.Access.NO_ALLOCATION;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.IsolateListenerSupport.IsolateListener;
import com.oracle.svm.core.SubstrateSegfaultHandler.SingleIsolateSegfaultSetup;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.nodes.WriteCurrentVMThreadNode;
import com.oracle.svm.core.graal.snippets.CEntryPointSnippets;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;
import com.oracle.svm.core.util.VMError;

@AutomaticallyRegisteredFeature
class SubstrateSegfaultHandlerFeature implements InternalFeature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (!ImageSingletons.contains(SubstrateSegfaultHandler.class)) {
            return; /* No segfault handler. */
        }

        SingleIsolateSegfaultSetup singleIsolateSegfaultSetup = new SingleIsolateSegfaultSetup();
        ImageSingletons.add(SingleIsolateSegfaultSetup.class, singleIsolateSegfaultSetup);
        IsolateListenerSupport.singleton().register(singleIsolateSegfaultSetup);

        VMError.guarantee(ImageSingletons.contains(RegisterDumper.class));
        RuntimeSupport.getRuntimeSupport().addStartupHook(new SubstrateSegfaultHandlerStartupHook());
    }
}

final class SubstrateSegfaultHandlerStartupHook implements RuntimeSupport.Hook {
    @Override
    public void execute(boolean isFirstIsolate) {
        if (isFirstIsolate) {
            Boolean optionValue = SubstrateSegfaultHandler.Options.InstallSegfaultHandler.getValue();
            if (optionValue == Boolean.TRUE || (optionValue == null && ImageInfo.isExecutable())) {
                ImageSingletons.lookup(SubstrateSegfaultHandler.class).install();
            }
        }
    }
}

public abstract class SubstrateSegfaultHandler {
    public static class Options {
        @Option(help = "Install segfault handler that prints register contents and full Java stacktrace. Default: enabled for an executable, disabled for a shared library.")//
        static final RuntimeOptionKey<Boolean> InstallSegfaultHandler = new RuntimeOptionKey<>(null);
    }

    private boolean installed;

    @Fold
    public static SubstrateSegfaultHandler singleton() {
        return ImageSingletons.lookup(SubstrateSegfaultHandler.class);
    }

    public static boolean isInstalled() {
        return singleton().installed;
    }

    /** Installs the platform dependent segfault handler. */
    public void install() {
        installInternal();
        installed = true;
    }

    protected abstract void installInternal();

    protected abstract void printSignalInfo(Log log, PointerBase signalInfo);

    /** Called from the platform dependent segfault handler to enter the isolate. */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Must not allocate in segfault handler.")
    protected static boolean tryEnterIsolate(RegisterDumper.Context context) {
        // Check if we have sufficient information to enter the correct isolate.
        Isolate isolate = SingleIsolateSegfaultSetup.singleton().getIsolate();
        if (isolate.rawValue() != -1) {
            // There is only a single isolate, so lets attach to that isolate.
            int error = CEntryPointActions.enterAttachThreadFromCrashHandler(isolate);
            return error == CEntryPointErrors.NO_ERROR;
        } else if (!SubstrateOptions.useLLVMBackend()) {
            // Try to determine the isolate via the register information. This very likely fails if
            // the crash happened in native code that was linked into Native Image.
            if (SubstrateOptions.SpawnIsolates.getValue()) {
                PointerBase heapBase = RegisterDumper.singleton().getHeapBase(context);
                CEntryPointSnippets.setHeapBase(heapBase);
            }
            if (SubstrateOptions.MultiThreaded.getValue()) {
                PointerBase threadPointer = RegisterDumper.singleton().getThreadPointer(context);
                WriteCurrentVMThreadNode.writeCurrentVMThread((IsolateThread) threadPointer);
            }

            /*
             * The following probing is subject to implicit recursion as it may trigger a new
             * segfault. However, this is fine, as it will eventually result in native stack
             * overflow.
             */
            isolate = VMThreads.IsolateTL.get();
            return Isolates.checkIsolate(isolate) == CEntryPointErrors.NO_ERROR && (!SubstrateOptions.SpawnIsolates.getValue() || isolate.equal(KnownIntrinsics.heapBase()));
        }
        return false;
    }

    /** Called from the platform dependent segfault handler to print diagnostics. */
    @Uninterruptible(reason = "Must be uninterruptible until we get immune to safepoints.", calleeMustBe = false)
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Must not allocate in segfault handler.")
    protected static void dump(PointerBase signalInfo, RegisterDumper.Context context) {
        SafepointBehavior.preventSafepoints();
        StackOverflowCheck.singleton().disableStackOverflowChecksForFatalError();

        dumpInterruptibly(signalInfo, context);
    }

    private static void dumpInterruptibly(PointerBase signalInfo, RegisterDumper.Context context) {
        PointerBase callerIP = RegisterDumper.singleton().getIP(context);
        LogHandler logHandler = ImageSingletons.lookup(LogHandler.class);
        Log log = Log.enterFatalContext(logHandler, (CodePointer) callerIP, "[ [ SubstrateSegfaultHandler caught a segfault. ] ]", null);
        if (log != null) {
            log.newline();
            log.string("[ [ SubstrateSegfaultHandler caught a segfault in thread ").zhex(CurrentIsolate.getCurrentThread()).string(" ] ]").newline();
            ImageSingletons.lookup(SubstrateSegfaultHandler.class).printSignalInfo(log, signalInfo);

            PointerBase sp = RegisterDumper.singleton().getSP(context);
            PointerBase ip = RegisterDumper.singleton().getIP(context);
            boolean printedDiagnostics = SubstrateDiagnostics.printFatalError(log, (Pointer) sp, (CodePointer) ip, context, false);
            if (printedDiagnostics) {
                log.string("Segfault detected, aborting process. Use runtime option -R:-InstallSegfaultHandler if you don't want to use SubstrateSegfaultHandler.").newline();
                log.newline();
            }
        }
        logHandler.fatalError();
    }

    static class SingleIsolateSegfaultSetup implements IsolateListener {

        /**
         * Stores the address of the first isolate created. This is meant to attempt to detect the
         * current isolate when entering the SVM segfault handler. The value is set to -1 when an
         * additional isolate is created, as there is then no way of knowing in which isolate a
         * subsequent segfault occurs.
         */
        private static final CGlobalData<Pointer> baseIsolate = CGlobalDataFactory.createWord();

        @Fold
        public static SingleIsolateSegfaultSetup singleton() {
            return ImageSingletons.lookup(SingleIsolateSegfaultSetup.class);
        }

        @Override
        @Uninterruptible(reason = "Thread state not yet set up.")
        public void afterCreateIsolate(Isolate isolate) {
            PointerBase value = baseIsolate.get().compareAndSwapWord(0, WordFactory.zero(), isolate, LocationIdentity.ANY_LOCATION);
            if (!value.isNull()) {
                baseIsolate.get().writeWord(0, WordFactory.signed(-1));
            }
        }

        @Uninterruptible(reason = "Thread state not yet set up.")
        public Isolate getIsolate() {
            return baseIsolate.get().readWord(0);
        }
    }
}
