/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.posix;

import static com.oracle.svm.core.SubstrateOptions.MultiThreaded;
import static com.oracle.svm.core.SubstrateOptions.SpawnIsolates;
import static com.oracle.svm.core.SubstrateOptions.UseHeapBaseRegister;
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
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CEntryPointContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointCreateIsolateParameters;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.c.function.CEntryPointNativeFunctions;
import com.oracle.svm.core.c.function.CEntryPointSetup;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallLinkage;
import com.oracle.svm.core.graal.nodes.CEntryPointEnterNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode;
import com.oracle.svm.core.graal.nodes.CEntryPointUtilityNode;
import com.oracle.svm.core.graal.snippets.CFunctionSnippets;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.Stdio.FILE;
import com.oracle.svm.core.posix.thread.PosixVMThreads;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.Safepoint;
import com.oracle.svm.core.thread.VMThreads;

/**
 * Snippets for calling from C to Java. See {@link CEntryPointActions} and
 * {@link CEntryPointNativeFunctions} for descriptions of the different ways of entering Java, and
 * later returning to C. This class is the inverse of {@link CFunctionSnippets}.
 *
 * This code transitions thread states, handles when a safepoint is in progress, sets the thread
 * register (if multi-threaded), and sets the heap base register (if enabled).
 */
public final class PosixCEntryPointSnippets extends SubstrateTemplates implements Snippets {

    public static final SubstrateForeignCallDescriptor CREATE_ISOLATE = SnippetRuntime.findForeignCall(PosixCEntryPointSnippets.class, "createIsolate", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor ATTACH_THREAD = SnippetRuntime.findForeignCall(PosixCEntryPointSnippets.class, "attachThread", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor ENTER_ISOLATE_MT = SnippetRuntime.findForeignCall(PosixCEntryPointSnippets.class, "enterIsolateMT", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor DETACH_THREAD_MT = SnippetRuntime.findForeignCall(PosixCEntryPointSnippets.class, "detachThreadMT", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor REPORT_EXCEPTION = SnippetRuntime.findForeignCall(PosixCEntryPointSnippets.class, "reportException", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor TEAR_DOWN_ISOLATE = SnippetRuntime.findForeignCall(PosixCEntryPointSnippets.class, "tearDownIsolate", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor IS_ATTACHED_MT = SnippetRuntime.findForeignCall(PosixCEntryPointSnippets.class, "isAttachedMT", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor FAIL_FATALLY = SnippetRuntime.findForeignCall(PosixCEntryPointSnippets.class, "failFatally", false, LocationIdentity.any());

    public static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = {CREATE_ISOLATE, ATTACH_THREAD, ENTER_ISOLATE_MT,
                    DETACH_THREAD_MT, REPORT_EXCEPTION, TEAR_DOWN_ISOLATE, IS_ATTACHED_MT, FAIL_FATALLY};

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native int runtimeCall(@ConstantNodeParameter ForeignCallDescriptor descriptor, CEntryPointCreateIsolateParameters parameters, int vmThreadSize);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native int runtimeCall(@ConstantNodeParameter ForeignCallDescriptor descriptor, Isolate isolate, int vmThreadSize);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native int runtimeCall(@ConstantNodeParameter ForeignCallDescriptor descriptor, Isolate isolate);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native int runtimeCall(@ConstantNodeParameter ForeignCallDescriptor descriptor, IsolateThread thread);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native int runtimeCall(@ConstantNodeParameter ForeignCallDescriptor descriptor, Throwable exception);

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

    @Uninterruptible(reason = "Called by an uninterruptible method.")
    public static void setHeapBase(PointerBase heapBase) {
        assert UseHeapBaseRegister.getValue();
        writeCurrentVMHeapBase(hasHeapBase() ? heapBase : WordFactory.nullPointer());
    }

    @Snippet
    public static int createIsolateSnippet(CEntryPointCreateIsolateParameters parameters, @ConstantParameter int vmThreadSize) {
        if (MultiThreaded.getValue()) {
            writeCurrentVMThread(VMThreads.nullThread());
        }
        return runtimeCall(CREATE_ISOLATE, parameters, vmThreadSize);
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    @SubstrateForeignCallTarget
    private static int createIsolate(CEntryPointCreateIsolateParameters parameters, int vmThreadSize) {
        WordPointer isolate = StackValue.get(WordPointer.class);
        isolate.write(WordFactory.nullPointer());
        int error = Isolates.create(isolate, parameters);
        if (error != CEntryPointErrors.NO_ERROR) {
            return error;
        }
        if (UseHeapBaseRegister.getValue()) {
            setHeapBase(Isolates.getHeapBase(isolate.read()));
        }
        if (MultiThreaded.getValue()) {
            PosixVMThreads.ensureInitialized();
        }
        return attachThread(isolate.read(), vmThreadSize);
    }

    @Snippet
    public static int attachThreadSnippet(Isolate isolate, @ConstantParameter int vmThreadSize) {
        if (MultiThreaded.getValue()) {
            writeCurrentVMThread(VMThreads.nullThread());
        }
        int result = runtimeCall(ATTACH_THREAD, isolate, vmThreadSize);
        if (MultiThreaded.getValue() && result == CEntryPointErrors.NO_ERROR) {
            Safepoint.transitionNativeToJava();
        }
        return result;
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    @SubstrateForeignCallTarget
    private static int attachThread(Isolate isolate, int vmThreadSize) {
        int sanityError = Isolates.checkSanity(isolate);
        if (sanityError != CEntryPointErrors.NO_ERROR) {
            return sanityError;
        }
        if (UseHeapBaseRegister.getValue()) {
            setHeapBase(Isolates.getHeapBase(isolate));
        }
        if (MultiThreaded.getValue()) {
            if (!PosixVMThreads.isInitialized()) {
                return CEntryPointErrors.UNINITIALIZED_ISOLATE;
            }
            IsolateThread thread = PosixVMThreads.VMThreadTL.get();
            if (VMThreads.isNullThread(thread)) { // not attached
                thread = LibC.calloc(WordFactory.unsigned(1), WordFactory.unsigned(vmThreadSize));
                VMThreads.attachThread(thread);
                // Store thread and isolate in thread-local variables.
                PosixVMThreads.VMThreadTL.set(thread);
                PosixVMThreads.IsolateTL.set(thread, isolate);
            }
            writeCurrentVMThread(thread);
        }
        return CEntryPointErrors.NO_ERROR;
    }

    @Snippet
    public static int detachThreadSnippet() {
        int result = CEntryPointErrors.NO_ERROR;
        if (MultiThreaded.getValue()) {
            IsolateThread thread = CEntryPointContext.getCurrentIsolateThread();
            result = runtimeCall(DETACH_THREAD_MT, thread);
        }
        if (UseHeapBaseRegister.getValue()) {
            writeCurrentVMHeapBase(WordFactory.nullPointer());
        }
        return result;
    }

    @SubstrateForeignCallTarget
    @Uninterruptible(reason = "Thread state going away.")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not (thread-local) allocate while detaching a thread.")
    private static int detachThreadMT(IsolateThread thread) {
        int result = CEntryPointErrors.NO_ERROR;
        /*
         * Make me immune to safepoints (the safepoint mechanism ignores me). We are calling
         * functions that are not marked as @Uninterruptible during the detach process. We hold the
         * THREAD_MUTEX, so we know that we are not going to be interrupted by a safepoint. But a
         * safepoint can already be requested, or our safepoint counter can reach 0 - so it is still
         * possible that we enter the safepoint slow path.
         */
        VMThreads.StatusSupport.setStatusIgnoreSafepoints();

        // try-finally because try-with-resources can call interruptible code
        VMThreads.THREAD_MUTEX.lockNoTransition();
        try {
            detachJavaLangThreadMT(thread);

            // clear references to thread to avoid unintended use
            writeCurrentVMThread(VMThreads.nullThread());
            PosixVMThreads.VMThreadTL.set(VMThreads.nullThread());

            VMThreads.detachThread(thread);
        } catch (Throwable t) {
            result = CEntryPointErrors.UNSPECIFIED;
        } finally {
            VMThreads.THREAD_MUTEX.unlock();
            LibC.free(thread);
        }
        return result;
    }

    @Uninterruptible(reason = "For calling interruptible code from uninterruptible code.", callerMustBe = true, mayBeInlined = true, calleeMustBe = false)
    private static void detachJavaLangThreadMT(IsolateThread thread) {
        JavaThreads.detachThread(thread);
    }

    @Snippet
    public static int tearDownIsolateSnippet() {
        return runtimeCallTearDownIsolate(TEAR_DOWN_ISOLATE);
    }

    @SubstrateForeignCallTarget
    private static int tearDownIsolate() {
        RuntimeSupport.executeTearDownHooks();
        boolean success = JavaThreads.singleton().tearDownVM();
        if (!success) {
            return CEntryPointErrors.UNSPECIFIED;
        }
        PosixVMThreads.finishTearDown();
        return Isolates.tearDownCurrent();
    }

    @Snippet
    public static int enterIsolateSnippet(Isolate isolate) {
        int result;
        if (MultiThreaded.getValue()) {
            writeCurrentVMThread(VMThreads.nullThread());
            result = runtimeCall(ENTER_ISOLATE_MT, isolate);
            if (result == CEntryPointErrors.NO_ERROR) {
                Safepoint.transitionNativeToJava();
            }
        } else {
            result = Isolates.checkSanity(isolate);
            if (result == CEntryPointErrors.NO_ERROR && UseHeapBaseRegister.getValue()) {
                setHeapBase(Isolates.getHeapBase(isolate));
            }
        }
        return result;
    }

    @Uninterruptible(reason = "Thread state not set up yet")
    @SubstrateForeignCallTarget
    private static int enterIsolateMT(Isolate isolate) {
        int sanityError = Isolates.checkSanity(isolate);
        if (sanityError != CEntryPointErrors.NO_ERROR) {
            return sanityError;
        }
        if (UseHeapBaseRegister.getValue()) {
            setHeapBase(Isolates.getHeapBase(isolate));
        }
        if (!PosixVMThreads.isInitialized()) {
            return CEntryPointErrors.UNINITIALIZED_ISOLATE;
        }
        IsolateThread thread = PosixVMThreads.VMThreadTL.get();
        if (VMThreads.isNullThread(thread)) {
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
            isolate = PosixVMThreads.IsolateTL.get(thread);
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
        if (UseHeapBaseRegister.getValue()) {
            setHeapBase(Isolates.getHeapBase(isolate));
        }
        if (MultiThreaded.getValue()) {
            Safepoint.transitionNativeToJava();
        }
        return CEntryPointErrors.NO_ERROR;
    }

    @Snippet
    public static int reportExceptionSnippet(Throwable exception) {
        return runtimeCall(REPORT_EXCEPTION, exception);
    }

    @SubstrateForeignCallTarget
    private static int reportException(Throwable exception) {
        Log.log().string(exception.getClass().getName());
        if (!NoAllocationVerifier.isActive()) {
            String detail = exception.getMessage();
            if (detail != null) {
                Log.log().string(": ").string(detail);
            }
        }
        Log.log().newline();
        ImageSingletons.lookup(LogHandler.class).fatalError();
        return CEntryPointErrors.UNSPECIFIED; // unreachable
    }

    @Snippet
    public static int returnFromJavaToCSnippet() {
        if (MultiThreaded.getValue()) {
            assert VMThreads.StatusSupport.isStatusJava() : "Should be coming from Java to native.";
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
    @SubstrateForeignCallTarget
    private static boolean isAttachedMT(Isolate isolate) {
        if (UseHeapBaseRegister.getValue()) {
            setHeapBase(Isolates.getHeapBase(isolate));
        }
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

    @SuppressWarnings("unused")
    public static void registerForeignCalls(RuntimeConfiguration runtimeConfig, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<SubstrateForeignCallDescriptor, SubstrateForeignCallLinkage> foreignCalls, boolean hosted) {

        for (SubstrateForeignCallDescriptor descriptor : FOREIGN_CALLS) {
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
        lowerings.put(CEntryPointEnterNode.class, new EnterLowering(vmThreadSize));
        lowerings.put(CEntryPointLeaveNode.class, new LeaveLowering());
        lowerings.put(CEntryPointUtilityNode.class, new UtilityLowering());
    }

    protected class EnterLowering implements NodeLoweringProvider<CEntryPointEnterNode> {

        private final SnippetInfo createIsolate = snippet(PosixCEntryPointSnippets.class, "createIsolateSnippet");
        private final SnippetInfo attachThread = snippet(PosixCEntryPointSnippets.class, "attachThreadSnippet");
        private final SnippetInfo enter = snippet(PosixCEntryPointSnippets.class, "enterSnippet");
        private final SnippetInfo enterThreadFromTL = snippet(PosixCEntryPointSnippets.class, "enterIsolateSnippet");

        private final int vmThreadSize;

        EnterLowering(int vmThreadSize) {
            this.vmThreadSize = vmThreadSize;
        }

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

    protected class LeaveLowering implements NodeLoweringProvider<CEntryPointLeaveNode> {
        private final SnippetInfo returnFromJavaToC = snippet(PosixCEntryPointSnippets.class, "returnFromJavaToCSnippet");
        private final SnippetInfo detachThread = snippet(PosixCEntryPointSnippets.class, "detachThreadSnippet");
        private final SnippetInfo reportException = snippet(PosixCEntryPointSnippets.class, "reportExceptionSnippet");
        private final SnippetInfo tearDownIsolate = snippet(PosixCEntryPointSnippets.class, "tearDownIsolateSnippet");

        @Override
        public void lower(CEntryPointLeaveNode node, LoweringTool tool) {
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
}
