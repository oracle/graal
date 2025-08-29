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
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;
import com.oracle.svm.core.threadlocal.VMThreadLocalSupport;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.word.Word;

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Independent.class)
@AutomaticallyRegisteredFeature
class SubstrateSegfaultHandlerFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.firstImageBuild();
    }

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
    private static final CGlobalData<Pointer> SEGFAULT_HANDLER_INSTALLED = CGlobalDataFactory.createWord();

    @Override
    public void execute(boolean isFirstIsolate) {
        Boolean optionValue = SubstrateSegfaultHandler.Options.InstallSegfaultHandler.getValue();
        if (SubstrateOptions.EnableSignalHandling.getValue() && optionValue != Boolean.FALSE && isFirst()) {
            ImageSingletons.lookup(SubstrateSegfaultHandler.class).install();
        }
    }

    private static boolean isFirst() {
        Word expected = Word.zero();
        Word actual = SEGFAULT_HANDLER_INSTALLED.get().compareAndSwapWord(0, expected, Word.unsigned(1), LocationIdentity.ANY_LOCATION);
        return expected == actual;
    }
}

public abstract class SubstrateSegfaultHandler {
    public static class Options {
        @Option(help = "Install segfault handler that prints register contents and full Java stacktrace. Default: enabled for an executable, disabled for a shared library, disabled when EnableSignalHandling is disabled.")//
        public static final RuntimeOptionKey<Boolean> InstallSegfaultHandler = new RuntimeOptionKey<>(null);
    }

    private static final long MARKER_VALUE = 0x0123456789ABCDEFL;
    @UnknownPrimitiveField(availability = ReadyForCompilation.class) //
    static long offsetOfStaticFieldWithWellKnownValue;
    @SuppressWarnings("unused") private static long staticFieldWithWellKnownValue = MARKER_VALUE;

    @Fold
    public static SubstrateSegfaultHandler singleton() {
        return ImageSingletons.lookup(SubstrateSegfaultHandler.class);
    }

    /** Installs the platform dependent segfault handler. */
    public abstract void install();

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
        return tryEnterIsolateViaHeapBaseRegister(context);
    }

    @Uninterruptible(reason = "Thread state not set up yet.")
    @NeverInline("Prevent register writes from floating")
    private static boolean tryEnterIsolateViaThreadRegister(RegisterDumper.Context context) {
        /*
         * Try to determine the isolate via the thread register. Set the thread register to null so
         * that we don't execute this code more than once if we trigger a recursive segfault.
         */
        WriteCurrentVMThreadNode.writeCurrentVMThread(Word.nullPointer());

        IsolateThread isolateThread = (IsolateThread) RegisterDumper.singleton().getThreadPointer(context);
        if (isolateThread.isNonNull()) {
            Isolate isolate = VMThreads.IsolateTL.get(isolateThread);
            if (isValid(isolate)) {
                CEntryPointSnippets.initBaseRegisters(isolate);
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
         * Set the base registers to null so that we don't execute this code more than once if we
         * trigger a recursive segfault.
         */
        CEntryPointSnippets.initBaseRegisters(Word.nullPointer(), Word.nullPointer());

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
        UnsignedWord staticFieldsOffsets = ReferenceAccess.singleton()
                        .getCompressedRepresentation(StaticFieldsSupport.getStaticPrimitiveFieldsAtRuntime(MultiLayeredImageSingleton.UNKNOWN_LAYER_NUMBER));
        UnsignedWord wellKnownFieldOffset = staticFieldsOffsets.shiftLeft(ReferenceAccess.singleton().getCompressionShift()).add(Word.unsigned(offsetOfStaticFieldWithWellKnownValue));
        Pointer wellKnownField = ((Pointer) isolate).add(wellKnownFieldOffset);
        return wellKnownField.readLong(0) == MARKER_VALUE;
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
            UnmanagedMemoryUtil.fill((Pointer) structForUnattachedThread, Word.unsigned(isolateThreadSize), (byte) 0);
            CEntryPointSnippets.initializeIsolateThreadForCrashHandler(isolate, structForUnattachedThread);
        }
    }

    /** Called in certain embedding use-cases. */
    @Uninterruptible(reason = "Must be uninterruptible until we get immune to safepoints.")
    public static void dump(PointerBase signalInfo, RegisterDumper.Context context) {
        dump(signalInfo, context, false);
    }

    @Uninterruptible(reason = "Must be uninterruptible until we get immune to safepoints.")
    @NeverInline("Base registers are set in caller, prevent reads from floating before that.")
    public static void dump(PointerBase signalInfo, RegisterDumper.Context context, boolean inSVMSegfaultHandler) {
        Pointer sp = (Pointer) RegisterDumper.singleton().getSP(context);
        CodePointer ip = (CodePointer) RegisterDumper.singleton().getIP(context);
        dump(sp, ip, signalInfo, context, inSVMSegfaultHandler);
    }

    @Uninterruptible(reason = "Must be uninterruptible until we get immune to safepoints.", calleeMustBe = false)
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Must not allocate in segfault handler.")
    private static void dump(Pointer sp, CodePointer ip, PointerBase signalInfo, RegisterDumper.Context context, boolean inSVMSegfaultHandler) {
        SafepointBehavior.preventSafepoints();
        StackOverflowCheck.singleton().disableStackOverflowChecksForFatalError();
        dumpInterruptibly(sp, ip, signalInfo, context, inSVMSegfaultHandler);
    }

    private static void dumpInterruptibly(Pointer sp, CodePointer ip, PointerBase signalInfo, RegisterDumper.Context context, boolean inSVMSegfaultHandler) {
        LogHandler logHandler = ImageSingletons.lookup(LogHandler.class);
        Log log = Log.enterFatalContext(logHandler, ip, "[ [ SegfaultHandler caught a segfault. ] ]", null);
        if (log != null) {
            log.newline();
            log.string("[ [ SegfaultHandler caught a segfault in thread ").zhex(CurrentIsolate.getCurrentThread()).string(" ] ]").newline();
            if (signalInfo.isNonNull()) {
                ImageSingletons.lookup(SubstrateSegfaultHandler.class).printSignalInfo(log, signalInfo);
            }

            boolean printedDiagnostics = SubstrateDiagnostics.printFatalError(log, sp, ip, context, false);
            if (printedDiagnostics && inSVMSegfaultHandler) {
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
            // we don't care if overflow happens in this abs
            log.string(" (heapBase ").string(sign).string(" ").signed(NumUtil.unsafeAbs(delta)).string(")");
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
            PointerBase value = baseIsolate.get().compareAndSwapWord(0, Word.zero(), isolate, LocationIdentity.ANY_LOCATION);
            if (!value.isNull()) {
                baseIsolate.get().writeWord(0, Word.signed(-1));
            }
        }

        @Uninterruptible(reason = "Thread state not yet set up.")
        public Isolate getIsolate() {
            return baseIsolate.get().readWord(0);
        }
    }
}
