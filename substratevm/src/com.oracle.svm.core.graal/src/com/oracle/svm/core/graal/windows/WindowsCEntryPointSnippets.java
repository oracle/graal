/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.windows;

import static com.oracle.svm.core.SubstrateOptions.UseHeapBaseRegister;
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
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointCreateIsolateParameters;
import com.oracle.svm.core.c.function.CEntryPointNativeFunctions;
import com.oracle.svm.core.c.function.CEntryPointErrors;
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
/*
import com.oracle.svm.core.windows.WindowsIsolates;
import com.oracle.svm.core.windows.headers.LibC;
import com.oracle.svm.core.windows.headers.Stdio.FILE;
import com.oracle.svm.core.windows.thread.WindowsVMThreads;
*/
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;

/**
 * Snippets for calling from C to Java. See {@link CEntryPointActions} and
 * {@link CEntryPointNativeFunctions} for descriptions of the different ways of entering Java, and
 * later returning to C. This class is the inverse of {@link CFunctionSnippets}.
 *
 * This code transitions thread states, handles when a safepoint is in progress, sets the thread
 * register (if multi-threaded), and sets the heap base register (if enabled).
 */
public final class WindowsCEntryPointSnippets extends SubstrateTemplates implements Snippets {

    public static final SubstrateForeignCallDescriptor CREATE_ISOLATE = SnippetRuntime.findForeignCall(WindowsCEntryPointSnippets.class, "createIsolate", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor ATTACH_THREAD = SnippetRuntime.findForeignCall(WindowsCEntryPointSnippets.class, "attachThread", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor ENTER_ISOLATE_MT = SnippetRuntime.findForeignCall(WindowsCEntryPointSnippets.class, "enterIsolateMT", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor DETACH_THREAD_MT = SnippetRuntime.findForeignCall(WindowsCEntryPointSnippets.class, "detachThreadMT", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor REPORT_EXCEPTION = SnippetRuntime.findForeignCall(WindowsCEntryPointSnippets.class, "reportException", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor TEAR_DOWN_ISOLATE = SnippetRuntime.findForeignCall(WindowsCEntryPointSnippets.class, "tearDownIsolate", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor IS_ATTACHED_MT = SnippetRuntime.findForeignCall(WindowsCEntryPointSnippets.class, "isAttachedMT", false, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor FAIL_FATALLY = SnippetRuntime.findForeignCall(WindowsCEntryPointSnippets.class, "failFatally", false, LocationIdentity.any());

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
        return runtimeCall(CREATE_ISOLATE, parameters, vmThreadSize);
    }

    @SuppressWarnings("unused")
    @Uninterruptible(reason = "Thread state not yet set up.")
    @SubstrateForeignCallTarget
    private static int createIsolate(CEntryPointCreateIsolateParameters parameters, int vmThreadSize) {
        return CEntryPointErrors.NO_ERROR;
    }

    @SuppressWarnings("unused")
    @Snippet
    public static int attachThreadSnippet(Isolate isolate, @ConstantParameter int vmThreadSize) {
        return CEntryPointErrors.NO_ERROR;
    }

    @SuppressWarnings("unused")
    @Uninterruptible(reason = "Thread state not yet set up.")
    @SubstrateForeignCallTarget
    private static int attachThread(Isolate isolate, int vmThreadSize) {
        return CEntryPointErrors.NO_ERROR;
    }

    @Snippet
    public static int detachThreadSnippet() {
        int result = CEntryPointErrors.NO_ERROR;
        return result;
    }

    @SuppressWarnings("unused")
    @SubstrateForeignCallTarget
    @Uninterruptible(reason = "Thread state going away.")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not (thread-local) allocate while detaching a thread.")
    private static int detachThreadMT(IsolateThread thread) {
        int result = CEntryPointErrors.NO_ERROR;
        return result;
    }

    @SuppressWarnings("unused")
    @Uninterruptible(reason = "For calling interruptible code from uninterruptible code.", callerMustBe = true, mayBeInlined = true, calleeMustBe = false)
    private static void detachJavaLangThreadMT(IsolateThread thread) {
    }

    @Snippet
    public static int tearDownIsolateSnippet() {
        return CEntryPointErrors.NO_ERROR;
    }

    @SubstrateForeignCallTarget
    private static int tearDownIsolate() {
        return CEntryPointErrors.NO_ERROR;
    }

    @SuppressWarnings("unused")
    @Snippet
    public static int enterIsolateSnippet(Isolate isolate) {
        return CEntryPointErrors.NO_ERROR;
    }

    @SuppressWarnings("unused")
    @Uninterruptible(reason = "Thread state not set up yet")
    @SubstrateForeignCallTarget
    private static int enterIsolateMT(Isolate isolate) {
        return CEntryPointErrors.NO_ERROR;
    }

    @SuppressWarnings("unused")
    @Snippet
    public static int enterSnippet(IsolateThread thread) {
        return CEntryPointErrors.NO_ERROR;
    }

    @SuppressWarnings("unused")
    @Snippet
    public static int reportExceptionSnippet(Throwable exception) {
        return CEntryPointErrors.NO_ERROR;
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
        return CEntryPointErrors.NO_ERROR;
    }

    @SuppressWarnings("unused")
    @Snippet
    public static boolean isAttachedSnippet(Isolate isolate) {
        return true;
    }

    @SuppressWarnings("unused")
    @Uninterruptible(reason = "Thread state not yet set up.")
    @SubstrateForeignCallTarget
    private static boolean isAttachedMT(Isolate isolate) {
        return true;
    }

    @SuppressWarnings("unused")
    @Snippet
    public static void failFatallySnippet(int code, CCharPointer message) {
    }

    private static final CGlobalData<CCharPointer> FAIL_FATALLY_FDOPEN_MODE = CGlobalDataFactory.createCString("w");
    private static final CGlobalData<CCharPointer> FAIL_FATALLY_MESSAGE_FORMAT = CGlobalDataFactory.createCString("Fatal error: %s (code %d)\n");

    @SuppressWarnings("unused")
    @Uninterruptible(reason = "Unknown thread state.")
    @SubstrateForeignCallTarget
    private static void failFatally(int code, CCharPointer message) {
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

        new WindowsCEntryPointSnippets(options, factories, providers, snippetReflection, vmThreadSize, lowerings);
    }

    private WindowsCEntryPointSnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection, int vmThreadSize,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {

        super(options, factories, providers, snippetReflection);
        lowerings.put(CEntryPointEnterNode.class, new EnterLowering(vmThreadSize));
        lowerings.put(CEntryPointLeaveNode.class, new LeaveLowering());
        lowerings.put(CEntryPointUtilityNode.class, new UtilityLowering());
    }

    protected class EnterLowering implements NodeLoweringProvider<CEntryPointEnterNode> {

        private final SnippetInfo createIsolate = snippet(WindowsCEntryPointSnippets.class, "createIsolateSnippet");
        private final SnippetInfo attachThread = snippet(WindowsCEntryPointSnippets.class, "attachThreadSnippet");
        private final SnippetInfo enter = snippet(WindowsCEntryPointSnippets.class, "enterSnippet");
        private final SnippetInfo enterThreadFromTL = snippet(WindowsCEntryPointSnippets.class, "enterIsolateSnippet");

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
        private final SnippetInfo returnFromJavaToC = snippet(WindowsCEntryPointSnippets.class, "returnFromJavaToCSnippet");
        private final SnippetInfo detachThread = snippet(WindowsCEntryPointSnippets.class, "detachThreadSnippet");
        private final SnippetInfo reportException = snippet(WindowsCEntryPointSnippets.class, "reportExceptionSnippet");
        private final SnippetInfo tearDownIsolate = snippet(WindowsCEntryPointSnippets.class, "tearDownIsolateSnippet");

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
        private final SnippetInfo isAttached = snippet(WindowsCEntryPointSnippets.class, "isAttachedSnippet");
        private final SnippetInfo failFatally = snippet(WindowsCEntryPointSnippets.class, "failFatallySnippet");

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
