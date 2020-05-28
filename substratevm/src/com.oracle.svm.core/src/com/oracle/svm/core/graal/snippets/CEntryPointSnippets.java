/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.svm.core.SubstrateOptions.UseDedicatedVMOperationThread;
import static com.oracle.svm.core.graal.nodes.WriteCurrentVMThreadNode.writeCurrentVMThread;
import static com.oracle.svm.core.graal.nodes.WriteHeapBaseNode.writeCurrentVMHeapBase;
import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
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
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.Safepoint;
import com.oracle.svm.core.thread.VMOperationControl;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;

//Checkstyle: stop
import sun.misc.Unsafe;
// Checkstyle: resume

/**
 * Snippets for calling from C to Java. See {@link CEntryPointActions} and
 * {@link CEntryPointNativeFunctions} for descriptions of the different ways of entering Java, and
 * later returning to C. This class is the inverse of {@link CFunctionSnippets}.
 *
 * This code transitions thread states, handles when a safepoint is in progress, sets the thread
 * register (if multi-threaded), and sets the heap base register (if enabled).
 */
public final class CEntryPointSnippets extends SubstrateTemplates implements Snippets {

    public static final SubstrateForeignCallDescriptor CREATE_ISOLATE = SnippetRuntime.findForeignCall(CEntryPointSnippets.class, "createIsolate", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor INITIALIZE_ISOLATE = SnippetRuntime.findForeignCall(CEntryPointSnippets.class, "initializeIsolate", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor ATTACH_THREAD = SnippetRuntime.findForeignCall(CEntryPointSnippets.class, "attachThread", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor ENSURE_JAVA_THREAD = SnippetRuntime.findForeignCall(CEntryPointSnippets.class, "ensureJavaThread", false, LocationIdentity.any());

    public static final SubstrateForeignCallDescriptor ENTER_ISOLATE_MT = SnippetRuntime.findForeignCall(CEntryPointSnippets.class, "enterIsolateMT", false, LocationIdentity.any());

    public static final SubstrateForeignCallDescriptor DETACH_THREAD_MT = SnippetRuntime.findForeignCall(CEntryPointSnippets.class, "detachThreadMT", false, LocationIdentity.any());

    public static final SubstrateForeignCallDescriptor REPORT_EXCEPTION = SnippetRuntime.findForeignCall(CEntryPointSnippets.class, "reportException", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor TEAR_DOWN_ISOLATE = SnippetRuntime.findForeignCall(CEntryPointSnippets.class, "tearDownIsolate", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor IS_ATTACHED_MT = SnippetRuntime.findForeignCall(CEntryPointSnippets.class, "isAttachedMT", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor FAIL_FATALLY = SnippetRuntime.findForeignCall(CEntryPointSnippets.class, "failFatally", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor VERIFY_ISOLATE_THREAD = SnippetRuntime.findForeignCall(CEntryPointSnippets.class, "verifyIsolateThread", false, LocationIdentity.any());

    public static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = {CREATE_ISOLATE, INITIALIZE_ISOLATE, ATTACH_THREAD, ENSURE_JAVA_THREAD, ENTER_ISOLATE_MT,
                    DETACH_THREAD_MT, REPORT_EXCEPTION, TEAR_DOWN_ISOLATE, IS_ATTACHED_MT, FAIL_FATALLY, VERIFY_ISOLATE_THREAD};

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native int runtimeCall(@ConstantNodeParameter ForeignCallDescriptor descriptor, CEntryPointCreateIsolateParameters parameters, int vmThreadSize);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native int runtimeCall(@ConstantNodeParameter ForeignCallDescriptor descriptor, Isolate isolate, int vmThreadSize);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native int runtimeCall(@ConstantNodeParameter ForeignCallDescriptor descriptor, Isolate isolate, boolean inCrashHandler);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native int runtimeCall(@ConstantNodeParameter ForeignCallDescriptor descriptor, IsolateThread thread);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native int runtimeCall(@ConstantNodeParameter ForeignCallDescriptor descriptor, Throwable exception);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native int runtimeCallInitializeIsolate(@ConstantNodeParameter ForeignCallDescriptor descriptor);

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

    @Uninterruptible(reason = "Called by an uninterruptible method.", mayBeInlined = true)
    public static void setHeapBase(PointerBase heapBase) {
        writeCurrentVMHeapBase(hasHeapBase() ? heapBase : WordFactory.nullPointer());
    }

    public interface IsolateCreationWatcher {
        void registerIsolate(Isolate isolate);
    }

    @Snippet
    public static int createIsolateSnippet(CEntryPointCreateIsolateParameters parameters, @ConstantParameter int vmThreadSize) {
        if (MultiThreaded.getValue()) {
            writeCurrentVMThread(WordFactory.nullPointer());
        }
        int result = runtimeCall(CREATE_ISOLATE, parameters, vmThreadSize);

        if (result != CEntryPointErrors.NO_ERROR) {
            return result;
        }
        if (MultiThreaded.getValue()) {
            Safepoint.transitionNativeToJava();
        }

        result = runtimeCallInitializeIsolate(INITIALIZE_ISOLATE);
        return result;
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static int createIsolate(CEntryPointCreateIsolateParameters parameters, int vmThreadSize) {
        WordPointer isolate = StackValue.get(WordPointer.class);
        isolate.write(WordFactory.nullPointer());
        int error = Isolates.create(isolate, parameters);
        if (error != CEntryPointErrors.NO_ERROR) {
            return error;
        }
        if (SpawnIsolates.getValue()) {
            setHeapBase(Isolates.getHeapBase(isolate.read()));
        }

        if (ImageSingletons.contains(IsolateCreationWatcher.class)) {
            ImageSingletons.lookup(IsolateCreationWatcher.class).registerIsolate(isolate.read());
        }

        CodeInfoTable.prepareImageCodeInfo();
        if (MultiThreaded.getValue()) {
            if (!VMThreads.ensureInitialized()) {
                return CEntryPointErrors.THREADING_INITIALIZATION_FAILED;
            }
        }
        error = attachThread(isolate.read(), vmThreadSize);
        if (error != CEntryPointErrors.NO_ERROR) {
            return error;
        }

        JavaThreads.singleton().initializeIsolate();

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

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static int initializeIsolate() {
        boolean firstIsolate = false;

        final long initStateAddr = FIRST_ISOLATE_INIT_STATE.get().rawValue();
        final Unsafe unsafe = GraalUnsafeAccess.getUnsafe();
        int state = unsafe.getInt(initStateAddr);
        if (state != FirstIsolateInitStates.SUCCESSFUL) {
            firstIsolate = unsafe.compareAndSwapInt(null, initStateAddr, FirstIsolateInitStates.UNINITIALIZED, FirstIsolateInitStates.IN_PROGRESS);
            if (firstIsolate) {
                PlatformNativeLibrarySupport.singleton().setIsFirstIsolate();
            } else {
                while (state == FirstIsolateInitStates.IN_PROGRESS) { // spin-wait for first isolate
                    state = unsafe.getIntVolatile(null, initStateAddr);
                }
                if (state == FirstIsolateInitStates.FAILED) {
                    return CEntryPointErrors.ISOLATE_INITIALIZATION_FAILED;
                }
            }
        }

        boolean success = PlatformNativeLibrarySupport.singleton().initializeBuiltinLibraries();
        if (firstIsolate) { // let other isolates (if any) initialize now
            state = success ? FirstIsolateInitStates.SUCCESSFUL : FirstIsolateInitStates.FAILED;
            unsafe.putIntVolatile(null, initStateAddr, state);
        }

        if (!success) {
            return CEntryPointErrors.ISOLATE_INITIALIZATION_FAILED;
        }
        assert !isolateInitialized;
        isolateInitialized = true;

        if (UseDedicatedVMOperationThread.getValue()) {
            VMOperationControl.startVMOperationThread();
        }
        RuntimeSupport.executeInitializationHooks();
        return CEntryPointErrors.NO_ERROR;
    }

    @Snippet
    public static int attachThreadSnippet(Isolate isolate, boolean ensureJavaThread, @ConstantParameter int vmThreadSize) {
        if (MultiThreaded.getValue()) {
            writeCurrentVMThread(WordFactory.nullPointer());
        }

        int error = runtimeCall(ATTACH_THREAD, isolate, vmThreadSize);
        if (error != CEntryPointErrors.NO_ERROR) {
            return error;
        }

        if (MultiThreaded.getValue()) {
            Safepoint.transitionNativeToJava();
        }
        if (ensureJavaThread) {
            runtimeCallEnsureJavaThread(ENSURE_JAVA_THREAD);
        }
        return CEntryPointErrors.NO_ERROR;
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static int attachThread(Isolate isolate, int vmThreadSize) {
        int sanityError = Isolates.checkSanity(isolate);
        if (sanityError != CEntryPointErrors.NO_ERROR) {
            return sanityError;
        }
        if (SpawnIsolates.getValue()) {
            setHeapBase(Isolates.getHeapBase(isolate));
        }
        if (MultiThreaded.getValue()) {
            if (!VMThreads.isInitialized()) {
                return CEntryPointErrors.UNINITIALIZED_ISOLATE;
            }
            IsolateThread thread = VMThreads.singleton().findIsolateThreadForCurrentOSThread(false);
            if (thread.isNull()) { // not attached
                thread = VMThreads.singleton().allocateIsolateThread(vmThreadSize);
                if (thread.isNull()) {
                    return CEntryPointErrors.THREADING_INITIALIZATION_FAILED;
                }
                StackOverflowCheck.singleton().initialize(thread);
                writeCurrentVMThread(thread);
                int error = VMThreads.singleton().attachThread(thread);
                if (error != CEntryPointErrors.NO_ERROR) {
                    VMThreads.singleton().freeIsolateThread(thread);
                    return error;
                }
                // Store thread and isolate in thread-local variables.
                VMThreads.IsolateTL.set(thread, isolate);
            } else {
                writeCurrentVMThread(thread);
            }
        } else {
            StackOverflowCheck.singleton().initialize(WordFactory.nullPointer());
        }
        return CEntryPointErrors.NO_ERROR;
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native void runtimeCallEnsureJavaThread(@ConstantNodeParameter ForeignCallDescriptor descriptor);

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static void ensureJavaThread() {
        JavaThreads.ensureJavaThread();
    }

    @Snippet
    public static int detachThreadSnippet() {
        int result = CEntryPointErrors.NO_ERROR;
        if (MultiThreaded.getValue()) {
            IsolateThread thread = CurrentIsolate.getCurrentThread();
            result = runtimeCall(DETACH_THREAD_MT, thread);
        }
        if (SpawnIsolates.getValue()) {
            writeCurrentVMHeapBase(WordFactory.nullPointer());
        }
        return result;
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    @Uninterruptible(reason = "Thread state going away.")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not (thread-local) allocate while detaching a thread.")
    private static int detachThreadMT(IsolateThread currentThread) {
        try {
            VMThreads.singleton().detachThread(currentThread);
            writeCurrentVMThread(WordFactory.nullPointer());
        } catch (Throwable t) {
            return CEntryPointErrors.UNCAUGHT_EXCEPTION;
        }
        return CEntryPointErrors.NO_ERROR;
    }

    @Snippet
    public static int tearDownIsolateSnippet() {
        return runtimeCallTearDownIsolate(TEAR_DOWN_ISOLATE);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static int tearDownIsolate() {
        try {
            RuntimeSupport.executeTearDownHooks();
            if (!JavaThreads.singleton().tearDown()) {
                return CEntryPointErrors.UNSPECIFIED;
            }

            VMThreads.singleton().tearDown();
            return Isolates.tearDownCurrent();
        } catch (Throwable t) {
            logException(t);
            return CEntryPointErrors.UNCAUGHT_EXCEPTION;
        }
    }

    @Snippet
    public static int enterIsolateSnippet(Isolate isolate, boolean inCrashHandler) {
        int result;
        if (MultiThreaded.getValue()) {
            writeCurrentVMThread(WordFactory.nullPointer());
            result = runtimeCall(ENTER_ISOLATE_MT, isolate, inCrashHandler);
            if (result == CEntryPointErrors.NO_ERROR) {
                if (!inCrashHandler || VMThreads.StatusSupport.isStatusNativeOrSafepoint()) {
                    Safepoint.transitionNativeToJava();
                }
            }
        } else {
            result = Isolates.checkSanity(isolate);
            if (result == CEntryPointErrors.NO_ERROR && SpawnIsolates.getValue()) {
                setHeapBase(Isolates.getHeapBase(isolate));
            }
        }
        return result;
    }

    @Uninterruptible(reason = "Thread state not set up yet")
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static int enterIsolateMT(Isolate isolate, boolean inCrashHandler) {
        int sanityError = Isolates.checkSanity(isolate);
        if (sanityError != CEntryPointErrors.NO_ERROR) {
            return sanityError;
        }
        if (SpawnIsolates.getValue()) {
            setHeapBase(Isolates.getHeapBase(isolate));
        }
        if (!VMThreads.isInitialized()) {
            return CEntryPointErrors.UNINITIALIZED_ISOLATE;
        }
        IsolateThread thread = VMThreads.singleton().findIsolateThreadForCurrentOSThread(inCrashHandler);
        if (thread.isNull()) {
            return CEntryPointErrors.UNATTACHED_THREAD;
        }
        writeCurrentVMThread(thread);
        return CEntryPointErrors.NO_ERROR;
    }

    @Snippet
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
            if (runtimeAssertionsEnabled()) {
                /*
                 * Verification must happen before the thread state transition. It locks the raw
                 * THREAD_MUTEX, so the thread must still be invisible to the safepoint manager.
                 */
                runtimeCall(VERIFY_ISOLATE_THREAD, thread);
            }
            Safepoint.transitionNativeToJava();
        }

        return CEntryPointErrors.NO_ERROR;

    }

    @Fold
    static boolean runtimeAssertionsEnabled() {
        return SubstrateOptions.getRuntimeAssertionsForClass(CEntryPointSnippets.class.getName());
    }

    /**
     * Verify that the {@link IsolateThread} provided by the user is correct, i.e., the same
     * {@link IsolateThread} that would be used by a C entry point providing only the
     * {@link Isolate}. This is a slow check and therefore only done when assertions are enabled.
     */
    @Uninterruptible(reason = "Thread state not set up yet")
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static int verifyIsolateThread(IsolateThread thread) {
        // The verification code below may only be executed if the current thread does not own the
        // THREADS_MUTEX. Otherwise, deadlocks could occur as the thread mutex would get locked a
        // second time.
        VMError.guarantee(CurrentIsolate.getCurrentThread() == thread, "Threads must match for the call below");
        if (!VMThreads.ownsThreadMutex()) {
            IsolateThread threadFromOS = VMThreads.singleton().findIsolateThreadForCurrentOSThread(false);
            if (!thread.equal(threadFromOS)) {
                throw VMError.shouldNotReachHere("A call from native code to Java code provided the wrong JNI environment or the wrong IsolateThread. " +
                                "The JNI environment / IsolateThread is a thread-local data structure and must not be shared between threads.");
            }
        }
        return CEntryPointErrors.NO_ERROR;
    }

    @Snippet
    public static int reportExceptionSnippet(Throwable exception) {
        return runtimeCall(REPORT_EXCEPTION, exception);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static int reportException(Throwable exception) {
        logException(exception);
        ImageSingletons.lookup(LogHandler.class).fatalError();
        return CEntryPointErrors.UNSPECIFIED; // unreachable
    }

    private static void logException(Throwable exception) {
        Log log = Log.log();
        if (log.isEnabled()) {
            if (NoAllocationVerifier.isActive()) {
                log.exception(exception);
            } else {
                StringWriter writer = new StringWriter();
                exception.printStackTrace(new PrintWriter(writer));
                log.string(writer.toString()); // no newline needed
            }
        }
    }

    @Snippet
    public static int returnFromJavaToCSnippet() {
        if (MultiThreaded.getValue()) {
            VMThreads.StatusSupport.setStatusNative();
        }
        return CEntryPointErrors.NO_ERROR;
    }

    @Snippet
    public static boolean isAttachedSnippet(Isolate isolate) {
        return Isolates.checkSanity(isolate) == CEntryPointErrors.NO_ERROR &&
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

    @Snippet
    public static void failFatallySnippet(int code, CCharPointer message) {
        runtimeCallFailFatally(FAIL_FATALLY, code, message);
    }

    @Uninterruptible(reason = "Unknown thread state.")
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static void failFatally(int code, CCharPointer message) {
        VMThreads.singleton().failFatally(code, message);
    }

    public static void registerForeignCalls(Providers providers, SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(providers, FOREIGN_CALLS);
    }

    @SuppressWarnings("unused")
    public static void registerLowerings(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection, int vmThreadSize,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {

        new CEntryPointSnippets(options, factories, providers, snippetReflection, vmThreadSize, lowerings);
    }

    private CEntryPointSnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection, int vmThreadSize,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {

        super(options, factories, providers, snippetReflection);
        lowerings.put(CEntryPointEnterNode.class, new EnterLowering(vmThreadSize));
        lowerings.put(CEntryPointLeaveNode.class, new LeaveLowering());
        lowerings.put(CEntryPointUtilityNode.class, new UtilityLowering());
    }

    protected class EnterLowering implements NodeLoweringProvider<CEntryPointEnterNode> {

        private final SnippetInfo createIsolate = snippet(CEntryPointSnippets.class, "createIsolateSnippet");
        private final SnippetInfo attachThread = snippet(CEntryPointSnippets.class, "attachThreadSnippet");
        private final SnippetInfo enter = snippet(CEntryPointSnippets.class, "enterSnippet");
        private final SnippetInfo enterThreadFromTL = snippet(CEntryPointSnippets.class, "enterIsolateSnippet");

        private final int vmThreadSize;

        EnterLowering(int vmThreadSize) {
            this.vmThreadSize = vmThreadSize;
        }

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
                    args.addConst("vmThreadSize", vmThreadSize);
                    break;
                case AttachThread:
                    args = new Arguments(attachThread, node.graph().getGuardsStage(), tool.getLoweringStage());
                    args.add("isolate", node.getParameter());
                    args.add("ensureJavaThread", node.getEnsureJavaThread());
                    args.addConst("vmThreadSize", vmThreadSize);
                    break;
                case EnterIsolate:
                    args = new Arguments(enterThreadFromTL, node.graph().getGuardsStage(), tool.getLoweringStage());
                    args.add("isolate", node.getParameter());
                    args.add("inCrashHandler", node.isCrashHandler());
                    break;
                case Enter:
                    args = new Arguments(enter, node.graph().getGuardsStage(), tool.getLoweringStage());
                    assert node.getParameter() != null;
                    args.add("thread", node.getParameter());
                    break;
                default:
                    throw shouldNotReachHere();
            }
            SnippetTemplate template = template(node, args);
            template.setMayRemoveLocation(true);
            template.instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    protected class LeaveLowering implements NodeLoweringProvider<CEntryPointLeaveNode> {
        private final SnippetInfo returnFromJavaToC = snippet(CEntryPointSnippets.class, "returnFromJavaToCSnippet");
        private final SnippetInfo detachThread = snippet(CEntryPointSnippets.class, "detachThreadSnippet");
        private final SnippetInfo reportException = snippet(CEntryPointSnippets.class, "reportExceptionSnippet");
        private final SnippetInfo tearDownIsolate = snippet(CEntryPointSnippets.class, "tearDownIsolateSnippet");

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
                    throw shouldNotReachHere();
            }
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }

    }

    protected class UtilityLowering implements NodeLoweringProvider<CEntryPointUtilityNode> {
        private final SnippetInfo isAttached = snippet(CEntryPointSnippets.class, "isAttachedSnippet");
        private final SnippetInfo failFatally = snippet(CEntryPointSnippets.class, "failFatallySnippet");

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
                    throw shouldNotReachHere();
            }
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}
