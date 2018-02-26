/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.posix;

import static com.oracle.svm.core.graal.nodes.WriteCurrentVMThreadNode.writeCurrentVMThread;
import static com.oracle.svm.core.graal.nodes.WriteHeapBaseNode.writeCurrentVMHeapBase;
import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

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
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointCreateIsolateParameters;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallLinkage;
import com.oracle.svm.core.graal.nodes.CEntryPointEnterNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode;
import com.oracle.svm.core.graal.nodes.CEntryPointUtilityNode;
import com.oracle.svm.core.graal.snippets.CFunctionSnippets;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.Stdio.FILE;
import com.oracle.svm.core.posix.thread.PosixVMThreads;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.Safepoint;
import com.oracle.svm.core.thread.VMThreads;

/**
 * Snippets for calling from C to Java. This is the inverse of {@link CFunctionSnippets}.
 *
 * Among the things that has to be done is to transition the thread state from being in native code
 * to being in Java code on the way in, and to transition the thread state from being in Java code
 * to being in native code on the way out.
 *
 * <ol>
 * There are three cases for calling from C to Java (via {@link CEntryPointEnterNode}):
 * <li>createIsolate: When the isolate must be set up.</li>
 * <li>attachThread: When the thread is already set up and I need to make it ready to run Java code
 * for the first time by setting up the IsolateThread (thread-local storage>.</li>
 * <li>enterIsolate: When the IsolateThread is set up and I can access the thread status through
 * that.</li>
 * <li>enter: When the IsolateThread is set up and is passed as a parameter to the call.</li>
 * </ol>
 *
 * <ol>
 * There are three cases for returning from Java to C (via {@link CEntryPointLeaveNode}):
 * <li>leave: When the Java code is returning normally.</li>
 * <li>detachJavaThread: When the thread is going away and I need to clean up the IsolateThread data
 * structure.</li>
 * <li>tearDownIsolate: when the thread is going away along with the rest of the isolate.</li>
 * <li>reportException: When the Java code is aborting with a Java exception.</li>
 * </ol>
 *
 * A complication is that a safepoint may be in progress when C tries to call Java. It should not be
 * allowed into Java code until the safepoint has finished.
 */
public final class PosixCEntryPointSnippets extends SubstrateTemplates implements Snippets {

    public static final SubstrateForeignCallDescriptor CREATE_ISOLATE = SnippetRuntime.findForeignCall(PosixCEntryPointSnippets.class, "createIsolate", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor ATTACH_THREAD = SnippetRuntime.findForeignCall(PosixCEntryPointSnippets.class, "attachThread", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor ENTER_ISOLATE = SnippetRuntime.findForeignCall(PosixCEntryPointSnippets.class, "enterIsolate", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor ENTER = SnippetRuntime.findForeignCall(PosixCEntryPointSnippets.class, "enter", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor DETACH_THREAD = SnippetRuntime.findForeignCall(PosixCEntryPointSnippets.class, "detachThread", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor REPORT_EXCEPTION = SnippetRuntime.findForeignCall(PosixCEntryPointSnippets.class, "reportException", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor TEAR_DOWN_ISOLATE = SnippetRuntime.findForeignCall(PosixCEntryPointSnippets.class, "tearDownIsolate", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor IS_ATTACHED = SnippetRuntime.findForeignCall(PosixCEntryPointSnippets.class, "isAttached", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor FAIL_FATALLY = SnippetRuntime.findForeignCall(PosixCEntryPointSnippets.class, "failFatally", false, LocationIdentity.any());

    public static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS_ST = {REPORT_EXCEPTION, FAIL_FATALLY};
    public static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS_MT = {CREATE_ISOLATE, ATTACH_THREAD, ENTER_ISOLATE,
                    ENTER, DETACH_THREAD, REPORT_EXCEPTION, TEAR_DOWN_ISOLATE, IS_ATTACHED, FAIL_FATALLY};

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native int runtimeCall(@ConstantNodeParameter ForeignCallDescriptor descriptor, CEntryPointCreateIsolateParameters parameters, int vmThreadSize);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native int runtimeCall(@ConstantNodeParameter ForeignCallDescriptor descriptor, Isolate isolate, int vmThreadSize);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native int runtimeCall(@ConstantNodeParameter ForeignCallDescriptor descriptor, Isolate isolate);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native void runtimeCall(@ConstantNodeParameter ForeignCallDescriptor descriptor, IsolateThread thread);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native void runtimeCall(@ConstantNodeParameter ForeignCallDescriptor descriptor, Throwable exception);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native int runtimeCallTearDownIsolate(@ConstantNodeParameter ForeignCallDescriptor descriptor);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native boolean runtimeCallIsAttached(@ConstantNodeParameter ForeignCallDescriptor descriptor, Isolate isolate);

    public static final String HEAP_BASE = "__svm_heap_base";
    private static final CGlobalData<PointerBase> heapBaseAccess = CGlobalDataFactory.forSymbol(HEAP_BASE);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native void runtimeCallFailFatally(@ConstantNodeParameter ForeignCallDescriptor descriptor, int code, CCharPointer message);

    @Uninterruptible(reason = "Called by an uninterruptible method.")
    static PointerBase heapBase() {
        return heapBaseAccess.get();
    }

    @Fold
    static boolean hasHeapBase() {
        return ImageSingletons.lookup(CompressEncoding.class).hasBase();
    }

    @Uninterruptible(reason = "Called by an uninterruptible method.")
    private static void clearHeapBase() {
        if (SubstrateOptions.UseHeapBaseRegister.getValue()) {
            writeCurrentVMHeapBase(WordFactory.nullPointer());
        }
    }

    @Uninterruptible(reason = "Called by an uninterruptible method.")
    private static void setHeapBase() {
        if (SubstrateOptions.UseHeapBaseRegister.getValue()) {
            writeCurrentVMHeapBase(hasHeapBase() ? heapBase() : WordFactory.nullPointer());
        }
    }

    /*
     * There is some asymmetry between attachThread and detachThread because of lazy initialization.
     *
     * In theory, all attachThread should do is make sure the Posix thread-local that points to the
     * VM thread-locals (the object containing all the VM thread-local variables); and all
     * detachThread should do is break the association from the Posix thread-local to the VM
     * thread-locals.
     *
     * In practice, the call to attachThread is made with nothing in place and if necessary it
     * allocates an instance of the VM thread-locals object to store in the Posix thread-local.
     * attachThread also has a hook (postAttachThread(IsolateThread) for any work that needs either
     * of the levels of thread-locals set up.
     *
     * However attachThread can not initialize the java.lang.Thread reference to the VM
     * thread-locals, because the java.lang.Thread object is constructed lazily the first time
     * Thread.currentThread() is called by the thread (See Target_java_lang_Thread.currentThread()).
     *
     * In contrast detachThread is called with the java.lang.Thread reference to the VM thread-local
     * (if one was allocated) set up. So it has a hook (preDetachThread(IsolateThread) to clean up
     * the java.lang.Thread's reference to the VM thread-locals, clean up the VM thread-locals, and
     * finally drop the reference from the Posix thread-local to the VM thread-locals.
     */

    /** Isolate initialization. */
    @Snippet
    public static int createIsolateSnippet(CEntryPointCreateIsolateParameters parameters, @ConstantParameter int vmThreadSize) {
        writeCurrentVMThread(VMThreads.nullThread());
        return runtimeCall(CREATE_ISOLATE, parameters, vmThreadSize);
    }

    /**
     * Foreign call: {@link #CREATE_ISOLATE}.
     */
    @Uninterruptible(reason = "Thread state not yet set up.")
    @SubstrateForeignCallTarget
    private static int createIsolate(@SuppressWarnings("unused") CEntryPointCreateIsolateParameters parameters, int vmThreadSize) {
        /*
         * TODO: Create a heap for the isolate, write it to the heap base register, pass it as an
         * Isolate to attachThread
         */
        setHeapBase();

        // Lenient: in case an isolate has already been created, attach to it.
        // In the future, we will create a separate isolate.
        PosixVMThreads.ensureInitialized();
        return attachThread(Word.zero(), vmThreadSize);
    }

    /** Call from C to Java on a new thread. */
    @Snippet
    public static int attachThreadSnippet(Isolate isolate, @ConstantParameter int vmThreadSize) {
        writeCurrentVMThread(VMThreads.nullThread());
        return runtimeCall(ATTACH_THREAD, isolate, vmThreadSize);
    }

    /**
     * Foreign call: {@link #ATTACH_THREAD}.
     * <p>
     * This method is annotated with {@link Uninterruptible} because not enough of the thread state
     * is set up to check for safepoints, e.g., on method returns.
     */
    @Uninterruptible(reason = "Thread state not yet set up.")
    @SubstrateForeignCallTarget
    private static int attachThread(@SuppressWarnings("unused") Isolate isolate, int vmThreadSize) {
        /* TODO: Use isolate as a heap base here, write it to the created IsolateThread */
        setHeapBase();

        if (!PosixVMThreads.isInitialized()) {
            return -1;
        }

        /* Check if the thread is already attached. */
        IsolateThread thread = PosixVMThreads.VMThreadTL.get();
        if (VMThreads.isNullThread(thread)) {
            /* Allocate the new thread in native memory and add it to thread list. */
            thread = LibC.calloc(WordFactory.unsigned(1), WordFactory.unsigned(vmThreadSize));
            VMThreads.attachThread(thread);
            /* Store thread in pthread thread-local variable. */
            PosixVMThreads.VMThreadTL.set(thread);
        }
        VMThreads.StatusSupport.setStatusJavaUnguarded(thread);
        postAttachThread(thread);
        writeCurrentVMThread(thread);
        return 0;
    }

    /** Things to be done to the current thread <em>after</em> attachThread. */
    @Uninterruptible(reason = "Thread state not yet set up.")
    private static void postAttachThread(@SuppressWarnings({"unused"}) IsolateThread thread) {
        // This would be a place to let VMThreads set things up,
        // but I think things are too fragile at this point to be useful.
        // For example, Log.log() does not work yet.
    }

    @Snippet
    public static int detachThreadSnippet() {
        IsolateThread thread = KnownIntrinsics.currentVMThread();
        runtimeCall(DETACH_THREAD, thread);
        return 0;
    }

    /**
     * Foreign call: {@link #DETACH_THREAD}.
     * <p>
     * This method is annotated with {@link Uninterruptible} because once I have discarded the
     * thread data structure, the thread can no longer even check for safepoint requests, e.g., on
     * method returns.
     * <p>
     * Using try-finally rather than a try-with-resources to avoid implicitly calling
     * {@link Throwable#addSuppressed(Throwable)}, which I can not assert is uninterruptible.
     */
    @SubstrateForeignCallTarget
    @Uninterruptible(reason = "Thread state going away.")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not (thread-local) allocate while detaching a thread.")
    private static void detachThread(IsolateThread thread) {
        /*
         * Set thread status to exited. This makes me immune to safepoints (the safepoint mechanism
         * ignores me). Also clear any pending safepoint requests, since I will not honor them.
         */
        VMThreads.StatusSupport.setStatusExited();
        Safepoint.setSafepointRequested(Safepoint.SafepointRequestValues.RESET);

        /* Hold the mutex around manipulations of thread state. */
        try {
            VMThreads.THREAD_MUTEX.lockNoTransition();

            /* Cleanup of the java.lang.Thread object. */
            detachJavaThread(thread);

            /*
             * We are about to free the thread. make sure no no can get a reference to it from the
             * thread register or the pthread thread-local variable.
             */
            writeCurrentVMThread(VMThreads.nullThread());
            PosixVMThreads.VMThreadTL.set(VMThreads.nullThread());

            /* Remove the thread from the list of VM threads, and then free the memory. */
            VMThreads.detachThread(thread);
        } finally {
            VMThreads.THREAD_MUTEX.unlock();
        }

        LibC.free(thread);
        clearHeapBase();
    }

    @Uninterruptible(reason = "Calling out from uninterruptible code with lock held.", calleeMustBe = false)
    private static int detachJavaThread(IsolateThread thread) {
        JavaThreads.detachThread(thread);
        return 0;
    }

    @Snippet
    public static int tearDownIsolateSnippet() {
        return runtimeCallTearDownIsolate(TEAR_DOWN_ISOLATE);
    }

    /**
     * Foreign call: {@link #TEAR_DOWN_ISOLATE}.
     * <p>
     * Exit action that tears down the isolate.
     * <p>
     * Returns 0 if the isolate was torn down, 1 otherwise.
     */
    @SubstrateForeignCallTarget
    private static int tearDownIsolate() {
        /* Let someone who can do all the work do all the work. */
        return JavaThreads.singleton().tearDownVM() ? 0 : -1;
    }

    /** Call from C to Java with IsolateThread coming from Posix thread-local storage. */
    @Snippet
    public static int enterIsolateSnippet(Isolate isolate) {
        // Get IsolateThread from Posix thread-local storage.
        writeCurrentVMThread(VMThreads.nullThread());
        int result = runtimeCall(ENTER_ISOLATE, isolate);
        if (result == 0) {
            transitionCtoJava();
        }
        return result;
    }

    /** Foreign call: {@link #ENTER_ISOLATE}. */
    @Uninterruptible(reason = "Thread register not set up yet")
    @SubstrateForeignCallTarget
    private static int enterIsolate(@SuppressWarnings("unused") Isolate isolate) {
        if (!PosixVMThreads.isInitialized()) {
            return -2;
        }
        /* Get thread from pthread thread-local variable. */
        IsolateThread thread = PosixVMThreads.VMThreadTL.get();
        if (VMThreads.isNullThread(thread)) {
            return -1;
        }
        // TODO: Use isolate as a heap base
        setHeapBase();
        writeCurrentVMThread(thread);
        return 0;
    }

    /** Call from C to Java with IsolateThread coming from the parameter. */
    @Snippet
    public static int enterSnippet(IsolateThread thread) {
        // Get IsolateThread from parameter.
        if (thread.isNull()) {
            return -1;
        }
        writeCurrentVMThread(thread);
        if (SubstrateOptions.UseHeapBaseRegister.getValue()) {
            // TODO: read heap base from the thread
            runtimeCall(ENTER, thread);
        }
        transitionCtoJava();
        return 0;
    }

    @SubstrateForeignCallTarget
    private static void enter(@SuppressWarnings("unused") IsolateThread thread) {
        setHeapBase();
    }

    private static void transitionCtoJava() {
        // Transition from C to Java, checking for safepoint.
        Safepoint.transitionNativeToJava();
    }

    /** Return from Java back to C with a pending Java exception. */
    @Snippet
    public static int reportExceptionSnippet(Throwable exception) {
        runtimeCall(REPORT_EXCEPTION, exception);
        return -1; // unreachable
    }

    /** Foreign call: {@link #REPORT_EXCEPTION}. */
    @SubstrateForeignCallTarget
    private static void reportException(Throwable exception) {
        Log.log().string(exception.getClass().getName());
        if (!NoAllocationVerifier.isActive()) {
            String detail = exception.getMessage();
            if (detail != null) {
                Log.log().string(": ").string(detail);
            }
        }
        Log.log().newline();
        LibC.abort();
    }

    @Snippet
    public static int returnFromJavaToCSnippet() {
        assert VMThreads.StatusSupport.isStatusJava() : "Should be coming from Java to native.";
        VMThreads.StatusSupport.setStatusNative();
        return 0;
    }

    @Snippet
    public static boolean isAttachedSnippet(Isolate isolate) {
        return runtimeCallIsAttached(IS_ATTACHED, isolate);
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    @SubstrateForeignCallTarget
    private static boolean isAttached(@SuppressWarnings("unused") Isolate isolate) {
        return PosixVMThreads.isInitialized() && PosixVMThreads.VMThreadTL.get().isNonNull();
    }

    @Snippet
    public static void failFatallySnippet(int code, CCharPointer message) {
        runtimeCallFailFatally(FAIL_FATALLY, code, message);
    }

    private static final CGlobalData<CCharPointer> FAIL_FATALLY_FDOPEN_MODE = CGlobalDataFactory.createCString("w");
    private static final CGlobalData<CCharPointer> FAIL_FATALLY_MESSAGE_FORMAT = CGlobalDataFactory.createCString("Fatal error: %s (code %d)\n");

    @CFunction(value = "fdopen", transition = Transition.NO_TRANSITION)
    public static native FILE fdopen(int fd, CCharPointer mode);

    @CFunction(value = "fprintf", transition = Transition.NO_TRANSITION)
    public static native int fprintfSD(FILE stream, CCharPointer format, CCharPointer arg0, int arg1);

    @Uninterruptible(reason = "Unknown thread state.")
    @SubstrateForeignCallTarget
    private static void failFatally(int code, CCharPointer message) {
        FILE stderr = fdopen(2, FAIL_FATALLY_FDOPEN_MODE.get());
        fprintfSD(stderr, FAIL_FATALLY_MESSAGE_FORMAT.get(), message, code);
        LibC.exit(code);
    }

    @Snippet
    public static int enterSingleThreadedSnippet() {
        return 0;
    }

    @Snippet
    public static int leaveSingleThreadedSnippet() {
        return 0;
    }

    @Snippet
    public static boolean isAttachedSingleThreadedSnippet(@SuppressWarnings("unused") Isolate isolate) {
        return true;
    }

    private final int vmThreadSize;

    @SuppressWarnings("unused")
    public static void registerForeignCalls(RuntimeConfiguration runtimeConfig, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<SubstrateForeignCallDescriptor, SubstrateForeignCallLinkage> foreignCalls, boolean hosted) {

        SubstrateForeignCallDescriptor[] descriptors = FOREIGN_CALLS_ST;
        if (SubstrateOptions.MultiThreaded.getValue()) {
            descriptors = FOREIGN_CALLS_MT;
        }
        for (SubstrateForeignCallDescriptor descriptor : descriptors) {
            foreignCalls.put(descriptor, new SubstrateForeignCallLinkage(providers, descriptor));
        }
    }

    @SuppressWarnings("unused")
    public static void registerLowerings(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection, int vmThreadSize,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {

        new PosixCEntryPointSnippets(options, factories, providers, snippetReflection, vmThreadSize, lowerings);
    }

    private PosixCEntryPointSnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection, int vmThreadSize,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {

        super(options, factories, providers, snippetReflection);
        this.vmThreadSize = vmThreadSize;
        if (SubstrateOptions.MultiThreaded.getValue()) {
            lowerings.put(CEntryPointEnterNode.class, new EnterMTLowering());
            lowerings.put(CEntryPointLeaveNode.class, new LeaveMTLowering());
            lowerings.put(CEntryPointUtilityNode.class, new UtilityMTLowering());
        } else {
            lowerings.put(CEntryPointEnterNode.class, new EnterSTLowering());
            lowerings.put(CEntryPointLeaveNode.class, new LeaveSTLowering());
            lowerings.put(CEntryPointUtilityNode.class, new UtilitySTLowering());
        }
    }

    protected class EnterMTLowering implements NodeLoweringProvider<CEntryPointEnterNode> {

        private final SnippetInfo createIsolate = snippet(PosixCEntryPointSnippets.class, "createIsolateSnippet");
        private final SnippetInfo attachThread = snippet(PosixCEntryPointSnippets.class, "attachThreadSnippet");
        private final SnippetInfo enter = snippet(PosixCEntryPointSnippets.class, "enterSnippet");
        private final SnippetInfo enterThreadFromTL = snippet(PosixCEntryPointSnippets.class, "enterIsolateSnippet");

        @Override
        public void lower(CEntryPointEnterNode node, LoweringTool tool) {
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
                    args.addConst("vmThreadSize", vmThreadSize);
                    break;
                case EnterIsolate:
                    args = new Arguments(enterThreadFromTL, node.graph().getGuardsStage(), tool.getLoweringStage());
                    args.add("isolate", node.getParameter());
                    break;
                case Enter:
                    args = new Arguments(enter, node.graph().getGuardsStage(), tool.getLoweringStage());
                    assert node.getParameter() != null;
                    args.add("thread", node.getParameter());
                    break;
                default:
                    throw shouldNotReachHere();
            }
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    protected class EnterSTLowering implements NodeLoweringProvider<CEntryPointEnterNode> {
        private final SnippetInfo enterSingleThreaded = snippet(PosixCEntryPointSnippets.class, "enterSingleThreadedSnippet");

        @Override
        public void lower(CEntryPointEnterNode node, LoweringTool tool) {
            Arguments args;
            switch (node.getEnterAction()) {
                case CreateIsolate:
                case AttachThread:
                case EnterIsolate:
                case Enter:
                    args = new Arguments(enterSingleThreaded, node.graph().getGuardsStage(), tool.getLoweringStage());
                    break;
                default:
                    throw shouldNotReachHere();
            }
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    protected class LeaveMTLowering implements NodeLoweringProvider<CEntryPointLeaveNode> {
        private final SnippetInfo returnFromJavaToC = snippet(PosixCEntryPointSnippets.class, "returnFromJavaToCSnippet");
        private final SnippetInfo detachThread = snippet(PosixCEntryPointSnippets.class, "detachThreadSnippet");
        private final SnippetInfo reportException = snippet(PosixCEntryPointSnippets.class, "reportExceptionSnippet");
        private final SnippetInfo tearDownIsolate = snippet(PosixCEntryPointSnippets.class, "tearDownIsolateSnippet");

        @Override
        public void lower(CEntryPointLeaveNode node, LoweringTool tool) {
            Arguments args;
            switch (node.getLeaveAction()) {
                case Leave:
                    /* Return the value of the method. */
                    args = new Arguments(returnFromJavaToC, node.graph().getGuardsStage(), tool.getLoweringStage());
                    break;
                case DetachThread:
                    /* Return the value of the method. */
                    args = new Arguments(detachThread, node.graph().getGuardsStage(), tool.getLoweringStage());
                    break;
                case TearDownIsolate:
                    /* Return the value of the exit action. */
                    args = new Arguments(tearDownIsolate, node.graph().getGuardsStage(), tool.getLoweringStage());
                    break;
                case ExceptionAbort:
                    /* We have an unhandled Java exception to report. */
                    args = new Arguments(reportException, node.graph().getGuardsStage(), tool.getLoweringStage());
                    args.add("exception", node.getException());
                    break;
                default:
                    throw shouldNotReachHere();
            }
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }

    }

    protected class LeaveSTLowering implements NodeLoweringProvider<CEntryPointLeaveNode> {

        private final SnippetInfo reportException = snippet(PosixCEntryPointSnippets.class, "reportExceptionSnippet");
        private final SnippetInfo leaveSingleThreaded = snippet(PosixCEntryPointSnippets.class, "leaveSingleThreadedSnippet");

        @Override
        public void lower(CEntryPointLeaveNode node, LoweringTool tool) {
            Arguments args;
            switch (node.getLeaveAction()) {
                case ExceptionAbort:
                    args = new Arguments(reportException, node.graph().getGuardsStage(), tool.getLoweringStage());
                    args.add("exception", node.getException());
                    break;
                case Leave:
                case DetachThread:
                case TearDownIsolate:
                    args = new Arguments(leaveSingleThreaded, node.graph().getGuardsStage(), tool.getLoweringStage());
                    break;
                default:
                    throw shouldNotReachHere();
            }
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    protected class UtilityMTLowering implements NodeLoweringProvider<CEntryPointUtilityNode> {
        private final SnippetInfo isAttached = snippet(PosixCEntryPointSnippets.class, "isAttachedSnippet");
        private final SnippetInfo failFatally = snippet(PosixCEntryPointSnippets.class, "failFatallySnippet");

        @Override
        public void lower(CEntryPointUtilityNode node, LoweringTool tool) {
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

    protected class UtilitySTLowering implements NodeLoweringProvider<CEntryPointUtilityNode> {
        private final SnippetInfo isAttachedSingleThreaded = snippet(PosixCEntryPointSnippets.class, "isAttachedSingleThreadedSnippet");
        private final SnippetInfo failFatally = snippet(PosixCEntryPointSnippets.class, "failFatallySnippet");

        @Override
        public void lower(CEntryPointUtilityNode node, LoweringTool tool) {
            Arguments args;
            switch (node.getUtilityAction()) {
                case IsAttached:
                    args = new Arguments(isAttachedSingleThreaded, node.graph().getGuardsStage(), tool.getLoweringStage());
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
