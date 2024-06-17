/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.BuildPhaseProvider.ReadyForCompilation;
import com.oracle.svm.core.IsolateListenerSupport.IsolateListener;
import com.oracle.svm.core.SubstrateSegfaultHandler.SingleIsolateSegfaultSetup;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.nodes.WriteCurrentVMThreadNode;
import com.oracle.svm.core.graal.snippets.CEntryPointSnippets;
import com.oracle.svm.core.graal.stackvalue.UnsafeLateStackValue;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;
import com.oracle.svm.core.threadlocal.VMThreadLocalSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;

@AutomaticallyRegisteredFeature
class SubstrateSegfaultHandlerFeature implements InternalFeature {
    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Collections.singletonList(IsolateListenerSupportFeature.class);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (!ImageSingletons.contains(SubstrateSegfaultHandler.class)) {
            return;
        }

        /* Register the marker as accessed so that we have a field with a well-known value. */
        access.registerAsUnsafeAccessed(getStaticFieldWithWellKnownValue());

        SingleIsolateSegfaultSetup singleIsolateSegfaultSetup = new SingleIsolateSegfaultSetup();
        ImageSingletons.add(SingleIsolateSegfaultSetup.class, singleIsolateSegfaultSetup);
        IsolateListenerSupport.singleton().register(singleIsolateSegfaultSetup);

        RuntimeSupport.getRuntimeSupport().addStartupHook(new SubstrateSegfaultHandlerStartupHook());
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        if (!ImageSingletons.contains(SubstrateSegfaultHandler.class)) {
            return;
        }

        SubstrateSegfaultHandler.offsetOfStaticFieldWithWellKnownValue = access.objectFieldOffset(getStaticFieldWithWellKnownValue());
    }

    private static Field getStaticFieldWithWellKnownValue() {
        return ReflectionUtil.lookupField(SubstrateSegfaultHandler.class, "staticFieldWithWellKnownValue");
    }
}

final class SubstrateSegfaultHandlerStartupHook implements RuntimeSupport.Hook {
    @Override
    public void execute(boolean isFirstIsolate) {
        if (isFirstIsolate) {
            Boolean optionValue = SubstrateSegfaultHandler.Options.InstallSegfaultHandler.getValue();
            if (SubstrateOptions.EnableSignalHandling.getValue() && optionValue != Boolean.FALSE) {
                ImageSingletons.lookup(SubstrateSegfaultHandler.class).install();
            }
        }
    }
}

public abstract class SubstrateSegfaultHandler {
    public static class Options {
        @Option(help = "Install segfault handler that prints register contents and full Java stacktrace. Default: enabled for an executable, disabled for a shared library, disabled when EnableSignalHandling is disabled.")//
        static final RuntimeOptionKey<Boolean> InstallSegfaultHandler = new RuntimeOptionKey<>(null);
    }

    private static final long MARKER_VALUE = 0x0123456789ABCDEFL;
    @UnknownPrimitiveField(availability = ReadyForCompilation.class) //
    static long offsetOfStaticFieldWithWellKnownValue;
    @SuppressWarnings("unused") private static long staticFieldWithWellKnownValue = MARKER_VALUE;

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

    /**
     * Called from the platform dependent segfault handler to enter the isolate. Note that this code
     * may trigger a new segfault, which can lead to recursion. In the worst case, the recursion is
     * only stopped once we trigger a native stack overflow.
     */
    @Uninterruptible(reason = "Thread state not set up yet.")
    protected static boolean tryEnterIsolate(RegisterDumper.Context context) {
        /* If there is only a single isolate, we can just enter that isolate. */
        Isolate isolate = SingleIsolateSegfaultSetup.singleton().getIsolate();
        if (isolate.rawValue() != -1) {
            int error = CEntryPointSnippets.enterAttachFromCrashHandler(isolate);
            return error == CEntryPointErrors.NO_ERROR;
        }

        /* The LLVM backend doesn't support the register-based approach. */
        if (SubstrateOptions.useLLVMBackend()) {
            return false;
        }

        /* Try to determine the isolate via the thread register. */
        if (tryEnterIsolateViaThreadRegister(context)) {
            return true;
        }

        /* Try to determine the isolate via the heap base register. */
        return SubstrateOptions.SpawnIsolates.getValue() && tryEnterIsolateViaHeapBaseRegister(context);
    }

    @Uninterruptible(reason = "Thread state not set up yet.")
    @NeverInline("Prevent register writes from floating")
    private static boolean tryEnterIsolateViaThreadRegister(RegisterDumper.Context context) {
        /*
         * Try to determine the isolate via the thread register. Set the thread register to null so
         * that we don't execute this code more than once if we trigger a recursive segfault.
         */
        WriteCurrentVMThreadNode.writeCurrentVMThread(WordFactory.nullPointer());

        IsolateThread isolateThread = (IsolateThread) RegisterDumper.singleton().getThreadPointer(context);
        if (isolateThread.isNonNull()) {
            Isolate isolate = VMThreads.IsolateTL.get(isolateThread);
            if (isValid(isolate)) {
                if (SubstrateOptions.SpawnIsolates.getValue()) {
                    CEntryPointSnippets.setHeapBase(isolate);
                }

                WriteCurrentVMThreadNode.writeCurrentVMThread(isolateThread);
                return true;
            }
        }
        return false;
    }

    @Uninterruptible(reason = "Thread state not set up yet.")
    @NeverInline("Prevent register writes from floating")
    private static boolean tryEnterIsolateViaHeapBaseRegister(RegisterDumper.Context context) {
        /*
         * Set the heap base register to null so that we don't execute this code more than once if
         * we trigger a recursive segfault.
         */
        CEntryPointSnippets.setHeapBase(WordFactory.nullPointer());

        Isolate isolate = (Isolate) RegisterDumper.singleton().getHeapBase(context);
        if (isValid(isolate)) {
            int error = CEntryPointSnippets.enterAttachFromCrashHandler(isolate);
            return error == CEntryPointErrors.NO_ERROR;
        }
        return false;
    }

    @Uninterruptible(reason = "Thread state not set up yet.")
    private static boolean isValid(Isolate isolate) {
        if (Isolates.checkIsolate(isolate) != CEntryPointErrors.NO_ERROR) {
            return false;
        }

        /*
         * Read a static field in the image heap and compare its value with a well-known marker
         * value as an extra sanity check. Note that the heap base register still contains an
         * invalid value when we execute this code, which makes things a bit more complex.
         */
        if (SubstrateOptions.SpawnIsolates.getValue()) {
            UnsignedWord staticFieldsOffsets = ReferenceAccess.singleton().getCompressedRepresentation(StaticFieldsSupport.getStaticPrimitiveFields());
            UnsignedWord wellKnownFieldOffset = staticFieldsOffsets.shiftLeft(ReferenceAccess.singleton().getCompressionShift()).add(WordFactory.unsigned(offsetOfStaticFieldWithWellKnownValue));
            Pointer wellKnownField = ((Pointer) isolate).add(wellKnownFieldOffset);
            return wellKnownField.readLong(0) == MARKER_VALUE;
        }

        return true;
    }

    /**
     * Enter the isolate in an async-signal safe way. Being async-signal-safe significantly limits
     * what we can do (e.g., for unattached threads, we need to allocate the IsolateThread on the
     * stack instead of on the C heap).
     */
    @Uninterruptible(reason = "prologue")
    @SuppressWarnings("unused")
    public static void enterIsolateAsyncSignalSafe(Isolate isolate) {
        int error = CEntryPointSnippets.enterFromCrashHandler(isolate);
        if (error != CEntryPointErrors.NO_ERROR) {
            /*
             * Some error occurred or this is an unattached thread. Only set up a minimal
             * IsolateThread so that we can at least try to dump some information.
             */
            int isolateThreadSize = VMThreadLocalSupport.singleton().vmThreadSize;
            IsolateThread structForUnattachedThread = UnsafeLateStackValue.get(isolateThreadSize);
            UnmanagedMemoryUtil.fill((Pointer) structForUnattachedThread, WordFactory.unsigned(isolateThreadSize), (byte) 0);
            CEntryPointSnippets.initializeIsolateThreadForCrashHandler(isolate, structForUnattachedThread);
        }
    }

    /** Called from the platform dependent segfault handler to print diagnostics. */
    @Uninterruptible(reason = "Must be uninterruptible until we get immune to safepoints.")
    public static void dump(PointerBase signalInfo, RegisterDumper.Context context) {
        Pointer sp = (Pointer) RegisterDumper.singleton().getSP(context);
        CodePointer ip = (CodePointer) RegisterDumper.singleton().getIP(context);
        dump(sp, ip, signalInfo, context);
    }

    @Uninterruptible(reason = "Must be uninterruptible until we get immune to safepoints.", calleeMustBe = false)
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Must not allocate in segfault handler.")
    public static void dump(Pointer sp, CodePointer ip, PointerBase signalInfo, RegisterDumper.Context context) {
        SafepointBehavior.preventSafepoints();
        StackOverflowCheck.singleton().disableStackOverflowChecksForFatalError();
        dumpInterruptibly(sp, ip, signalInfo, context);
    }

    private static void dumpInterruptibly(Pointer sp, CodePointer ip, PointerBase signalInfo, RegisterDumper.Context context) {
        LogHandler logHandler = ImageSingletons.lookup(LogHandler.class);
        Log log = Log.enterFatalContext(logHandler, ip, "[ [ SegfaultHandler caught a segfault. ] ]", null);
        if (log != null) {
            log.newline();
            log.string("[ [ SegfaultHandler caught a segfault in thread ").zhex(CurrentIsolate.getCurrentThread()).string(" ] ]").newline();
            if (signalInfo.isNonNull()) {
                ImageSingletons.lookup(SubstrateSegfaultHandler.class).printSignalInfo(log, signalInfo);
            }

            boolean printedDiagnostics = SubstrateDiagnostics.printFatalError(log, sp, ip, context, false);
            if (SubstrateSegfaultHandler.isInstalled() && printedDiagnostics) {
                log.string("Segfault detected, aborting process. ") //
                                .string("Use '-XX:-InstallSegfaultHandler' to disable the segfault handler at run time and create a core dump instead. ") //
                                .string("Rebuild with '-R:-InstallSegfaultHandler' to disable the handler permanently at build time.") //
                                .newline().newline();
            }
        }
        logHandler.fatalError();
    }

    protected static void printSegfaultAddressInfo(Log log, long addr) {
        log.zhex(addr);
        if (addr != 0) {
            long delta = addr - CurrentIsolate.getIsolate().rawValue();
            String sign = (delta >= 0 ? "+" : "-");
            log.string(" (heapBase ").string(sign).string(" ").signed(Math.abs(delta)).string(")");
        }
    }

    public static class SingleIsolateSegfaultSetup implements IsolateListener {

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
