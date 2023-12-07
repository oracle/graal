/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.snippets;

import static com.oracle.svm.core.SubstrateOptions.MultiThreaded;
import static com.oracle.svm.core.SubstrateOptions.SpawnIsolates;
import static com.oracle.svm.core.graal.nodes.WriteCurrentVMThreadNode.writeCurrentVMThread;
import static com.oracle.svm.core.graal.nodes.WriteHeapBaseNode.writeCurrentVMHeapBase;
import static com.oracle.svm.core.heap.RestrictHeapAccess.Access.NO_ALLOCATION;
import static com.oracle.svm.core.util.VMError.shouldNotReachHereUnexpectedInput;
import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;

import java.util.Map;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.CPUFeatureAccess;
import com.oracle.svm.core.IsolateArgumentParser;
import com.oracle.svm.core.IsolateListenerSupport;
import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.JavaMainWrapper.JavaMainSupport;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.RuntimeAssertionsSupport;
import com.oracle.svm.core.SubstrateDiagnostics;
import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointCreateIsolateParameters;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.c.function.CEntryPointNativeFunctions;
import com.oracle.svm.core.c.function.CEntryPointSetup;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.nodes.CEntryPointEnterNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode;
import com.oracle.svm.core.graal.nodes.CEntryPointUtilityNode;
import com.oracle.svm.core.graal.nodes.WriteCurrentVMThreadNode;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.heap.ReferenceHandler;
import com.oracle.svm.core.heap.ReferenceHandlerThread;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.RuntimeOptionParser;
import com.oracle.svm.core.os.MemoryProtectionProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.Safepoint;
import com.oracle.svm.core.thread.VMOperationControl;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;
import com.oracle.svm.core.threadlocal.VMThreadLocalMTSupport;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.nodes.PauseNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;
import jdk.graal.compiler.word.Word;
import jdk.internal.misc.Unsafe;

/**
 * Snippets for calling from C to Java. See {@link CEntryPointActions} and
 * {@link CEntryPointNativeFunctions} for descriptions of the different ways of entering Java, and
 * later returning to C. This class is the inverse of {@link CFunctionSnippets}.
 *
 * This code transitions thread states, handles when a safepoint is in progress, sets the thread
 * register (if multi-threaded), and sets the heap base register (if enabled).
 */
public final class CEntryPointSnippets extends SubstrateTemplates implements Snippets {

    public static final SubstrateForeignCallDescriptor CREATE_ISOLATE = SnippetRuntime.findForeignCall(CEntryPointSnippets.class, "createIsolate",
                    HAS_SIDE_EFFECT, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor INITIALIZE_ISOLATE = SnippetRuntime.findForeignCall(CEntryPointSnippets.class, "initializeIsolate",
                    HAS_SIDE_EFFECT, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor ATTACH_THREAD = SnippetRuntime.findForeignCall(CEntryPointSnippets.class, "attachThread", HAS_SIDE_EFFECT,
                    LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor ENSURE_JAVA_THREAD = SnippetRuntime.findForeignCall(CEntryPointSnippets.class, "ensureJavaThread",
                    HAS_SIDE_EFFECT, LocationIdentity.any());

    public static final SubstrateForeignCallDescriptor ENTER_BY_ISOLATE_MT = SnippetRuntime.findForeignCall(CEntryPointSnippets.class, "enterByIsolateMT",
                    HAS_SIDE_EFFECT, LocationIdentity.any());

    public static final SubstrateForeignCallDescriptor DETACH_THREAD_MT = SnippetRuntime.findForeignCall(CEntryPointSnippets.class, "detachThreadMT",
                    HAS_SIDE_EFFECT, LocationIdentity.any());

    public static final SubstrateForeignCallDescriptor REPORT_EXCEPTION = SnippetRuntime.findForeignCall(CEntryPointSnippets.class, "reportException",
                    HAS_SIDE_EFFECT, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor TEAR_DOWN_ISOLATE = SnippetRuntime.findForeignCall(CEntryPointSnippets.class, "tearDownIsolate",
                    HAS_SIDE_EFFECT, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor IS_ATTACHED_MT = SnippetRuntime.findForeignCall(CEntryPointSnippets.class, "isAttachedMT", HAS_SIDE_EFFECT, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor FAIL_FATALLY = SnippetRuntime.findForeignCall(CEntryPointSnippets.class, "failFatally", HAS_SIDE_EFFECT, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor VERIFY_ISOLATE_THREAD = SnippetRuntime.findForeignCall(CEntryPointSnippets.class, "verifyIsolateThread", HAS_SIDE_EFFECT,
                    LocationIdentity.any());

    public static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = {CREATE_ISOLATE, INITIALIZE_ISOLATE, ATTACH_THREAD, ENSURE_JAVA_THREAD, ENTER_BY_ISOLATE_MT,
                    DETACH_THREAD_MT, REPORT_EXCEPTION, TEAR_DOWN_ISOLATE, IS_ATTACHED_MT, FAIL_FATALLY, VERIFY_ISOLATE_THREAD};

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native int runtimeCall(@ConstantNodeParameter ForeignCallDescriptor descriptor, CEntryPointCreateIsolateParameters parameters);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native int runtimeCall(@ConstantNodeParameter ForeignCallDescriptor descriptor, Isolate isolate,
                    boolean startedByIsolate, boolean ensuringJavaThread);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native int runtimeCall(@ConstantNodeParameter ForeignCallDescriptor descriptor, Isolate isolate);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native int runtimeCall(@ConstantNodeParameter ForeignCallDescriptor descriptor, IsolateThread thread);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native int runtimeCall(@ConstantNodeParameter ForeignCallDescriptor descriptor, Throwable exception);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native int runtimeCallInitializeIsolate(@ConstantNodeParameter ForeignCallDescriptor descriptor, CEntryPointCreateIsolateParameters parameters);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native int runtimeCallTearDownIsolate(@ConstantNodeParameter ForeignCallDescriptor descriptor);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native boolean runtimeCallIsAttached(@ConstantNodeParameter ForeignCallDescriptor descriptor, Isolate isolate);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native void runtimeCallFailFatally(@ConstantNodeParameter ForeignCallDescriptor descriptor, int code, CCharPointer message);

    @Fold
    static boolean hasHeapBase() {
        return ImageSingletons.lookup(CompressEncoding.class).hasBase();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setHeapBase(PointerBase heapBase) {
        if (hasHeapBase()) {
            writeCurrentVMHeapBase(heapBase);
            if (MemoryProtectionProvider.isAvailable()) {
                MemoryProtectionProvider.singleton().unlockCurrentIsolate();
            }
        } else {
            writeCurrentVMHeapBase(WordFactory.nullPointer());
        }
    }

    @Snippet(allowMissingProbabilities = true)
    public static int createIsolateSnippet(CEntryPointCreateIsolateParameters parameters) {
        if (MultiThreaded.getValue()) {
            writeCurrentVMThread(WordFactory.nullPointer());
        }
        int result = runtimeCall(CREATE_ISOLATE, parameters);

        if (result != CEntryPointErrors.NO_ERROR) {
            return result;
        }
        if (MultiThreaded.getValue()) {
            Safepoint.transitionNativeToJava(false);
        }

        return runtimeCallInitializeIsolate(INITIALIZE_ISOLATE, parameters);
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static int createIsolate(CEntryPointCreateIsolateParameters providedParameters) {
        CPUFeatureAccess cpuFeatureAccess = ImageSingletons.lookup(CPUFeatureAccess.class);
        if (cpuFeatureAccess.verifyHostSupportsArchitectureEarly() != 0) {
            return CEntryPointErrors.CPU_FEATURE_CHECK_FAILED;
        }

        UnsignedWord runtimePageSize = VirtualMemoryProvider.get().getGranularity();
        UnsignedWord imagePageSize = WordFactory.unsigned(SubstrateOptions.getPageSize());
        boolean validPageSize = runtimePageSize.equal(imagePageSize) ||
                        (Heap.getHeap().allowPageSizeMismatch() && UnsignedUtils.isAMultiple(imagePageSize, runtimePageSize));
        if (!validPageSize) {
            return CEntryPointErrors.PAGE_SIZE_CHECK_FAILED;
        }
        CLongPointer parsedArgs = StackValue.get(IsolateArgumentParser.getStructSize());
        CEntryPointCreateIsolateParameters parameters = providedParameters;
        if (parameters.isNull() || parameters.version() < 1) {
            parameters = StackValue.get(CEntryPointCreateIsolateParameters.class);
            parameters.setReservedSpaceSize(WordFactory.zero());
            parameters.setVersion(1);
        }
        IsolateArgumentParser.parse(parameters, parsedArgs);
        if (parameters.reservedSpaceSize().equal(0)) {
            parameters.setReservedSpaceSize(WordFactory.unsigned(parsedArgs.read(IsolateArgumentParser.getOptionIndex(SubstrateGCOptions.ReservedAddressSpaceSize))));
        }

        WordPointer isolatePtr = StackValue.get(WordPointer.class);
        int error = Isolates.create(isolatePtr, parameters);
        if (error != CEntryPointErrors.NO_ERROR) {
            return error;
        }
        Isolate isolate = isolatePtr.read();
        if (SpawnIsolates.getValue()) {
            setHeapBase(Isolates.getHeapBase(isolate));
        }

        return createIsolate0(parsedArgs, isolate);
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    @NeverInline(value = "Ensure this code cannot rise above where heap base is set.")
    private static int createIsolate0(CLongPointer parsedArgs, Isolate isolate) {

        IsolateArgumentParser.singleton().persistOptions(parsedArgs);
        IsolateListenerSupport.singleton().afterCreateIsolate(isolate);

        CodeInfoTable.prepareImageCodeInfo();
        if (MultiThreaded.getValue()) {
            if (!VMThreads.ensureInitialized()) {
                return CEntryPointErrors.THREADING_INITIALIZATION_FAILED;
            }
        }
        int error = enterAttachThread0(isolate, false, true);
        if (error != CEntryPointErrors.NO_ERROR) {
            return error;
        }

        PlatformThreads.singleton().initializeIsolate();
        return CEntryPointErrors.NO_ERROR;
    }

    /** States for {@link #FIRST_ISOLATE_INIT_STATE}. */
    private static final class FirstIsolateInitStates {
        static final int UNINITIALIZED = 0;
        static final int IN_PROGRESS = 1;
        static final int SUCCESSFUL = 2;

        static final int FAILED = -1;
    }

    /**
     * Certain initialization tasks must be done exactly once per process, by the first launched
     * isolate, which is coordinated via this variable.
     */
    private static final CGlobalData<PointerBase> FIRST_ISOLATE_INIT_STATE = CGlobalDataFactory.createWord();
    private static boolean isolateInitialized;

    public static boolean isIsolateInitialized() {
        return isolateInitialized;
    }

    @Uninterruptible(reason = "Must be uninterruptible because thread state is not set up after leaveTearDownIsolate().")
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static int initializeIsolate(CEntryPointCreateIsolateParameters parameters) {
        int result = initializeIsolateInterruptibly(parameters);
        if (result != CEntryPointErrors.NO_ERROR) {
            CEntryPointActions.leaveTearDownIsolate();
            /* Extra return is needed because of validation. */
            return result;
        }
        return result;
    }

    @Uninterruptible(reason = "Used as a transition between uninterruptible and interruptible code", calleeMustBe = false)
    private static int initializeIsolateInterruptibly(CEntryPointCreateIsolateParameters parameters) {
        return initializeIsolateInterruptibly0(parameters);
    }

    private static int initializeIsolateInterruptibly0(CEntryPointCreateIsolateParameters parameters) {
        /*
         * The VM operation thread must be started early as no VM operations can be scheduled before
         * this thread is fully started. The isolate teardown may also use VM operations.
         */
        if (VMOperationControl.useDedicatedVMOperationThread()) {
            VMOperationControl.startVMOperationThread();
        }

        long initStateAddr = FIRST_ISOLATE_INIT_STATE.get().rawValue();
        boolean firstIsolate = Unsafe.getUnsafe().compareAndSetInt(null, initStateAddr, FirstIsolateInitStates.UNINITIALIZED, FirstIsolateInitStates.IN_PROGRESS);

        Isolates.assignIsolateId(firstIsolate);
        Isolates.assignCurrentStartTime();

        if (!firstIsolate) {
            int state = Unsafe.getUnsafe().getInt(initStateAddr);
            if (state != FirstIsolateInitStates.SUCCESSFUL) {
                while (state == FirstIsolateInitStates.IN_PROGRESS) { // spin-wait for first isolate
                    PauseNode.pause();
                    state = Unsafe.getUnsafe().getIntVolatile(null, initStateAddr);
                }
                if (state == FirstIsolateInitStates.FAILED) {
                    return CEntryPointErrors.ISOLATE_INITIALIZATION_FAILED;
                }
            }
        }

        /*
         * The reference handler thread must also be started early. Otherwise, it could happen that
         * the GC publishes pending references but there is no thread to process them. This could
         * result in deadlocks if ReferenceInternals.waitForReferenceProcessing() is called.
         */
        if (ReferenceHandler.useDedicatedThread()) {
            ReferenceHandlerThread.start();
        }

        /*
         * After starting all the necessary threads, we can finally execute complex JDK code or code
         * that allocates a significant amount of memory.
         */

        if (parameters.isNonNull() && parameters.version() >= 3 && parameters.getArgv().isNonNull()) {
            boolean exitWhenArgumentParsingFails = true;
            boolean ignoreUnrecognized = false;
            if (parameters.version() >= 4) {
                ignoreUnrecognized = parameters.getIgnoreUnrecognizedArguments();
                exitWhenArgumentParsingFails = parameters.getExitWhenArgumentParsingFails();
            }

            String[] args = SubstrateUtil.convertCToJavaArgs(parameters.getArgc(), parameters.getArgv());
            try {
                args = RuntimeOptionParser.parseAndConsumeAllOptions(args, ignoreUnrecognized);
            } catch (IllegalArgumentException e) {
                if (exitWhenArgumentParsingFails) {
                    Log.logStream().println("error: " + e.getMessage());
                    System.exit(1);
                } else {
                    return CEntryPointErrors.ARGUMENT_PARSING_FAILED;
                }
            }

            if (ImageSingletons.contains(JavaMainSupport.class)) {
                ImageSingletons.lookup(JavaMainSupport.class).mainArgs = args;
            }
        }

        boolean success = PlatformNativeLibrarySupport.singleton().initializeBuiltinLibraries();
        if (firstIsolate) { // let other isolates (if any) initialize now
            int state = success ? FirstIsolateInitStates.SUCCESSFUL : FirstIsolateInitStates.FAILED;
            Unsafe.getUnsafe().putIntVolatile(null, initStateAddr, state);
        }

        if (!success) {
            return CEntryPointErrors.ISOLATE_INITIALIZATION_FAILED;
        }

        /* Adjust stack overflow boundary of main thread. */
        StackOverflowCheck.singleton().updateStackOverflowBoundary();

        /* Initialize the physical memory size. */
        PhysicalMemory.size();

        assert !isolateInitialized;
        isolateInitialized = true;

        try {
            RuntimeSupport.executeInitializationHooks();
        } catch (Throwable t) {
            System.err.println("Uncaught exception while running initialization hooks:");
            t.printStackTrace();
            return CEntryPointErrors.ISOLATE_INITIALIZATION_FAILED;
        }

        return CEntryPointErrors.NO_ERROR;
    }

    @Snippet(allowMissingProbabilities = true)
    public static int attachThreadSnippet(Isolate isolate, boolean startedByIsolate, boolean ensureJavaThread) {
        if (MultiThreaded.getValue()) {
            writeCurrentVMThread(WordFactory.nullPointer());
        }
        int error = runtimeCall(ATTACH_THREAD, isolate, startedByIsolate, ensureJavaThread);
        if (error != CEntryPointErrors.NO_ERROR) {
            return error;
        }

        if (MultiThreaded.getValue()) {
            Safepoint.transitionNativeToJava(false);
        }
        if (ensureJavaThread) {
            runtimeCallEnsureJavaThread(ENSURE_JAVA_THREAD);
        }
        return CEntryPointErrors.NO_ERROR;
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static int attachThread(Isolate isolate, boolean startedByIsolate, boolean ensureJavaThread) {
        return enterAttachThread0(isolate, startedByIsolate, ensureJavaThread);
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    private static int enterAttachThread0(Isolate isolate, boolean startedByIsolate, boolean ensureJavaThread) {
        return enterAttachThread0(isolate, startedByIsolate, ensureJavaThread, true, false);
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    private static int enterAttachThread0(Isolate isolate, boolean startedByIsolate, boolean ensureJavaThread, boolean allowAttach, boolean inCrashHandler) {
        int error = Isolates.checkIsolate(isolate);
        if (error != CEntryPointErrors.NO_ERROR) {
            return error;
        }
        if (SpawnIsolates.getValue()) {
            setHeapBase(Isolates.getHeapBase(isolate));
        }
        if (MultiThreaded.getValue()) {
            if (!VMThreads.isInitialized()) {
                return CEntryPointErrors.UNINITIALIZED_ISOLATE;
            }

            IsolateThread thread = WordFactory.nullPointer();
            if (startedByIsolate) {
                assert VMThreads.singleton().findIsolateThreadForCurrentOSThread(inCrashHandler).isNull();
            } else {
                thread = VMThreads.singleton().findIsolateThreadForCurrentOSThread(inCrashHandler);
            }

            if (thread.isNull()) { // not attached
                if (!allowAttach) {
                    return CEntryPointErrors.UNATTACHED_THREAD;
                }
                return attachUnattachedThread(isolate, startedByIsolate, inCrashHandler);
            } else {
                writeCurrentVMThread(thread);
                if (ensureJavaThread && !PlatformThreads.isCurrentAssigned()) {
                    throw VMError.shouldNotReachHere("thread was already attached but does not have a Thread object and we would assign one");
                }
            }
        } else {
            StackOverflowCheck.singleton().initialize();
        }
        return CEntryPointErrors.NO_ERROR;
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    private static int attachUnattachedThread(Isolate isolate, boolean startedByIsolate, boolean inCrashHandler) {
        int isolateThreadSize = VMThreadLocalMTSupport.singleton().vmThreadSize;
        IsolateThread thread = VMThreads.singleton().allocateIsolateThread(isolateThreadSize);
        if (thread.isNull()) {
            return CEntryPointErrors.ALLOCATION_FAILED;
        }
        writeCurrentVMThread(thread);
        if (!StackOverflowCheck.singleton().initialize()) {
            return CEntryPointErrors.UNKNOWN_STACK_BOUNDARIES;
        }

        if (inCrashHandler) {
            // If we are in the crash handler then we only want to make sure that this thread can
            // print diagnostics. A full attach operation would be too dangerous.
            SubstrateDiagnostics.setOnlyAttachedForCrashHandler(thread);
        } else {
            int error = VMThreads.singleton().attachThread(thread, startedByIsolate);
            if (error != CEntryPointErrors.NO_ERROR) {
                VMThreads.singleton().freeIsolateThread(thread);
                return error;
            }
        }

        VMThreads.IsolateTL.set(thread, isolate);
        return CEntryPointErrors.NO_ERROR;
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    public static int enterAttachFromCrashHandler(Isolate isolate) {
        return enterAttachThread0(isolate, false, false, true, true);
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    public static int enterFromCrashHandler(Isolate isolate) {
        return enterAttachThread0(isolate, false, false, false, true);
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    public static void initializeIsolateThreadForCrashHandler(Isolate isolate, IsolateThread thread) {
        if (SpawnIsolates.getValue()) {
            setHeapBase(Isolates.getHeapBase(isolate));
        }

        if (MultiThreaded.getValue()) {
            writeCurrentVMThread(thread);
            VMThreads.StatusSupport.setStatusNative();
            VMThreads.IsolateTL.set(thread, isolate);
        }
        SubstrateDiagnostics.setOnlyAttachedForCrashHandler(thread);
        StackOverflowCheck.singleton().disableStackOverflowChecksForFatalError();
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native void runtimeCallEnsureJavaThread(@ConstantNodeParameter ForeignCallDescriptor descriptor);

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static void ensureJavaThread() {
        PlatformThreads.ensureCurrentAssigned();
    }

    @Snippet(allowMissingProbabilities = true)
    public static int detachThreadSnippet() {
        int result = CEntryPointErrors.NO_ERROR;
        if (MultiThreaded.getValue()) {
            IsolateThread thread = CurrentIsolate.getCurrentThread();
            result = runtimeCall(DETACH_THREAD_MT, thread);
        }

        /*
         * Note that we do not reset the fixed registers used for the thread and isolate to null:
         * Since these values are not copied to different registers when they are used, we need to
         * keep the registers intact until the last possible point where we are in Java code.
         */
        return result;
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    @Uninterruptible(reason = "Thread state going away.")
    private static int detachThreadMT(IsolateThread currentThread) {
        try {
            VMThreads.singleton().detachThread(currentThread);
        } catch (Throwable t) {
            return CEntryPointErrors.UNCAUGHT_EXCEPTION;
        }
        return CEntryPointErrors.NO_ERROR;
    }

    @Snippet(allowMissingProbabilities = true)
    public static int tearDownIsolateSnippet() {
        return runtimeCallTearDownIsolate(TEAR_DOWN_ISOLATE);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    @Uninterruptible(reason = "All code executed after VMThreads#tearDown must be uninterruptible")
    private static int tearDownIsolate() {
        try {
            if (!initiateTearDownIsolateInterruptibly()) {
                return CEntryPointErrors.UNSPECIFIED;
            }

            VMThreads.singleton().tearDown();
            IsolateThread finalThread = CurrentIsolate.getCurrentThread();
            int result = Isolates.tearDownCurrent();
            // release the heap memory associated with final isolate thread
            VMThreads.singleton().freeIsolateThread(finalThread);
            WriteCurrentVMThreadNode.writeCurrentVMThread(WordFactory.nullPointer());
            return result;
        } catch (Throwable t) {
            return reportException(t);
        }
    }

    @Uninterruptible(reason = "Used as a transition between uninterruptible and interruptible code", calleeMustBe = false)
    private static boolean initiateTearDownIsolateInterruptibly() {
        RuntimeSupport.executeTearDownHooks();
        return PlatformThreads.singleton().tearDown();
    }

    @Snippet(allowMissingProbabilities = true)
    public static int enterByIsolateSnippet(Isolate isolate) {
        int result;
        if (MultiThreaded.getValue()) {
            writeCurrentVMThread(WordFactory.nullPointer());
            result = runtimeCall(ENTER_BY_ISOLATE_MT, isolate);
            if (result == CEntryPointErrors.NO_ERROR) {
                if (VMThreads.StatusSupport.isStatusNativeOrSafepoint()) {
                    Safepoint.transitionNativeToJava(false);
                }
            }
        } else {
            result = Isolates.checkIsolate(isolate);
            if (result == CEntryPointErrors.NO_ERROR && SpawnIsolates.getValue()) {
                setHeapBase(Isolates.getHeapBase(isolate));
            }
        }
        return result;
    }

    @Uninterruptible(reason = "Thread state not set up yet")
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static int enterByIsolateMT(Isolate isolate) {
        int error = Isolates.checkIsolate(isolate);
        if (error != CEntryPointErrors.NO_ERROR) {
            return error;
        }
        if (SpawnIsolates.getValue()) {
            setHeapBase(Isolates.getHeapBase(isolate));
        }
        if (!VMThreads.isInitialized()) {
            return CEntryPointErrors.UNINITIALIZED_ISOLATE;
        }
        IsolateThread thread = VMThreads.singleton().findIsolateThreadForCurrentOSThread(false);
        if (thread.isNull()) {
            return CEntryPointErrors.UNATTACHED_THREAD;
        }
        writeCurrentVMThread(thread);
        return CEntryPointErrors.NO_ERROR;
    }

    @Snippet(allowMissingProbabilities = true)
    public static int enterSnippet(IsolateThread thread) {
        Isolate isolate;
        if (MultiThreaded.getValue()) {
            if (thread.isNull()) {
                return CEntryPointErrors.NULL_ARGUMENT;
            }
            writeCurrentVMThread(thread);
            isolate = VMThreads.IsolateTL.get(thread);
        } else { // single-threaded
            if (SpawnIsolates.getValue()) {
                if (thread.isNull()) {
                    return CEntryPointErrors.NULL_ARGUMENT;
                }
            } else if (!thread.equal(CEntryPointSetup.SINGLE_THREAD_SENTINEL)) {
                return CEntryPointErrors.UNATTACHED_THREAD;
            }
            isolate = (Isolate) ((Word) thread).subtract(CEntryPointSetup.SINGLE_ISOLATE_TO_SINGLE_THREAD_ADDEND);
        }
        if (SpawnIsolates.getValue()) {
            setHeapBase(Isolates.getHeapBase(isolate));
        }
        if (MultiThreaded.getValue()) {
            if (runtimeAssertionsEnabled() || SubstrateOptions.CheckIsolateThreadAtEntry.getValue()) {
                /*
                 * Verification must happen before the thread state transition. It locks the raw
                 * THREAD_MUTEX, so the thread must still be invisible to the safepoint manager.
                 */
                runtimeCall(VERIFY_ISOLATE_THREAD, thread);
            }
            Safepoint.transitionNativeToJava(false);
        }

        return CEntryPointErrors.NO_ERROR;

    }

    @Fold
    static boolean runtimeAssertionsEnabled() {
        return RuntimeAssertionsSupport.singleton().desiredAssertionStatus(CEntryPointSnippets.class);
    }

    /**
     * Verify that the {@link IsolateThread} provided by the user is correct, i.e., the same
     * {@link IsolateThread} that would be used by a C entry point providing only the
     * {@link Isolate}. This is a slow check and therefore only done when assertions are enabled.
     */
    @Uninterruptible(reason = "Thread state not set up yet")
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static int verifyIsolateThread(IsolateThread thread) {
        VMError.guarantee(CurrentIsolate.getCurrentThread() == thread, "Threads must match for the call below");
        if (!VMThreads.singleton().verifyIsCurrentThread(thread) || !VMThreads.singleton().verifyThreadIsAttached(thread)) {
            throw VMError.shouldNotReachHere("A call from native code to Java code provided the wrong JNI environment or the wrong IsolateThread. " +
                            "The JNI environment / IsolateThread is a thread-local data structure and must not be shared between threads.");

        }
        return CEntryPointErrors.NO_ERROR;
    }

    @Snippet(allowMissingProbabilities = true)
    public static int reportExceptionSnippet(Throwable exception) {
        return runtimeCall(REPORT_EXCEPTION, exception);
    }

    @Uninterruptible(reason = "Avoid StackOverflowError and safepoints until they are disabled permanently", calleeMustBe = false)
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Must not allocate in fatal error handling.")
    private static int reportException(Throwable exception) {
        SafepointBehavior.preventSafepoints();
        StackOverflowCheck.singleton().disableStackOverflowChecksForFatalError();

        reportExceptionInterruptibly(exception);
        return CEntryPointErrors.UNSPECIFIED; // unreachable
    }

    private static void reportExceptionInterruptibly(Throwable exception) {
        logException(exception);
        ImageSingletons.lookup(LogHandler.class).fatalError();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Must not allocate in fatal error handling.")
    private static void logException(Throwable exception) {
        try {
            Log.log().exception(exception).newline();
        } catch (Throwable ex) {
            /* Logging failed, so there is nothing we can do anymore to log. */
        }
    }

    @Snippet(allowMissingProbabilities = true)
    public static int returnFromJavaToCSnippet() {
        if (MultiThreaded.getValue()) {
            VMThreads.StatusSupport.setStatusNative();
        }
        return CEntryPointErrors.NO_ERROR;
    }

    @Snippet(allowMissingProbabilities = true)
    public static boolean isAttachedSnippet(Isolate isolate) {
        return Isolates.checkIsolate(isolate) == CEntryPointErrors.NO_ERROR &&
                        (!MultiThreaded.getValue() || runtimeCallIsAttached(IS_ATTACHED_MT, isolate));
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static boolean isAttachedMT(Isolate isolate) {
        if (SpawnIsolates.getValue()) {
            setHeapBase(Isolates.getHeapBase(isolate));
        }
        return VMThreads.isInitialized() && VMThreads.singleton().findIsolateThreadForCurrentOSThread(false).isNonNull();
    }

    @Snippet(allowMissingProbabilities = true)
    public static void failFatallySnippet(int code, CCharPointer message) {
        runtimeCallFailFatally(FAIL_FATALLY, code, message);
    }

    @Uninterruptible(reason = "Unknown thread state.")
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static void failFatally(int code, CCharPointer message) {
        VMThreads.singleton().failFatally(code, message);
    }

    public static void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(FOREIGN_CALLS);
    }

    @SuppressWarnings("unused")
    public static void registerLowerings(OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        new CEntryPointSnippets(options, providers, lowerings);
    }

    private final SnippetInfo createIsolate;
    private final SnippetInfo attachThread;
    private final SnippetInfo enter;
    private final SnippetInfo enterByIsolate;

    private final SnippetInfo returnFromJavaToC;
    private final SnippetInfo detachThread;
    private final SnippetInfo reportException;
    private final SnippetInfo tearDownIsolate;

    private final SnippetInfo isAttached;
    private final SnippetInfo failFatally;

    private CEntryPointSnippets(OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, providers);

        this.createIsolate = snippet(providers, CEntryPointSnippets.class, "createIsolateSnippet");
        this.attachThread = snippet(providers, CEntryPointSnippets.class, "attachThreadSnippet");
        this.enter = snippet(providers, CEntryPointSnippets.class, "enterSnippet");
        this.enterByIsolate = snippet(providers, CEntryPointSnippets.class, "enterByIsolateSnippet");

        this.returnFromJavaToC = snippet(providers, CEntryPointSnippets.class, "returnFromJavaToCSnippet");
        this.detachThread = snippet(providers, CEntryPointSnippets.class, "detachThreadSnippet");
        this.reportException = snippet(providers, CEntryPointSnippets.class, "reportExceptionSnippet");
        this.tearDownIsolate = snippet(providers, CEntryPointSnippets.class, "tearDownIsolateSnippet");

        this.isAttached = snippet(providers, CEntryPointSnippets.class, "isAttachedSnippet");
        this.failFatally = snippet(providers, CEntryPointSnippets.class, "failFatallySnippet");

        lowerings.put(CEntryPointEnterNode.class, new EnterLowering());
        lowerings.put(CEntryPointLeaveNode.class, new LeaveLowering());
        lowerings.put(CEntryPointUtilityNode.class, new UtilityLowering());
    }

    protected class EnterLowering implements NodeLoweringProvider<CEntryPointEnterNode> {
        @Override
        public void lower(CEntryPointEnterNode node, LoweringTool tool) {
            if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
                return;
            }
            Arguments args;
            switch (node.getEnterAction()) {
                case CreateIsolate:
                    args = new Arguments(createIsolate, node.graph().getGuardsStage(), tool.getLoweringStage());
                    args.add("parameters", node.getParameter());
                    break;
                case AttachThread:
                    args = new Arguments(attachThread, node.graph().getGuardsStage(), tool.getLoweringStage());
                    args.add("isolate", node.getParameter());
                    args.add("startedByIsolate", node.getStartedByIsolate());
                    args.add("ensureJavaThread", node.getEnsureJavaThread());
                    break;
                case EnterByIsolate:
                    args = new Arguments(enterByIsolate, node.graph().getGuardsStage(), tool.getLoweringStage());
                    args.add("isolate", node.getParameter());
                    break;
                case Enter:
                    args = new Arguments(enter, node.graph().getGuardsStage(), tool.getLoweringStage());
                    assert node.getParameter() != null;
                    args.add("thread", node.getParameter());
                    break;
                default:
                    throw shouldNotReachHereUnexpectedInput(node.getEnterAction()); // ExcludeFromJacocoGeneratedReport
            }
            SnippetTemplate template = template(tool, node, args);
            template.setMayRemoveLocation(true);
            template.instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    protected class LeaveLowering implements NodeLoweringProvider<CEntryPointLeaveNode> {
        @Override
        public void lower(CEntryPointLeaveNode node, LoweringTool tool) {
            if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
                return;
            }
            Arguments args;
            switch (node.getLeaveAction()) {
                case Leave:
                    args = new Arguments(returnFromJavaToC, node.graph().getGuardsStage(), tool.getLoweringStage());
                    break;
                case DetachThread:
                    args = new Arguments(detachThread, node.graph().getGuardsStage(), tool.getLoweringStage());
                    break;
                case TearDownIsolate:
                    args = new Arguments(tearDownIsolate, node.graph().getGuardsStage(), tool.getLoweringStage());
                    break;
                case ExceptionAbort:
                    args = new Arguments(reportException, node.graph().getGuardsStage(), tool.getLoweringStage());
                    args.add("exception", node.getException());
                    break;
                default:
                    throw shouldNotReachHereUnexpectedInput(node.getLeaveAction()); // ExcludeFromJacocoGeneratedReport
            }
            template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }

    }

    protected class UtilityLowering implements NodeLoweringProvider<CEntryPointUtilityNode> {
        @Override
        public void lower(CEntryPointUtilityNode node, LoweringTool tool) {
            if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
                return;
            }
            Arguments args;
            switch (node.getUtilityAction()) {
                case IsAttached:
                    args = new Arguments(isAttached, node.graph().getGuardsStage(), tool.getLoweringStage());
                    args.add("isolate", node.getParameter0());
                    break;
                case FailFatally:
                    args = new Arguments(failFatally, node.graph().getGuardsStage(), tool.getLoweringStage());
                    args.add("code", node.getParameter0());
                    args.add("message", node.getParameter1());
                    break;
                default:
                    throw shouldNotReachHereUnexpectedInput(node.getUtilityAction()); // ExcludeFromJacocoGeneratedReport
            }
            template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}
