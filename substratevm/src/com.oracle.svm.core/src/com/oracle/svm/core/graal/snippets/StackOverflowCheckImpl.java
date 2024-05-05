/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.EXTREMELY_SLOW_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

import java.util.Map;
import java.util.function.Predicate;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnreachableNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.tiers.MidTierContext;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.RestrictHeapAccess.Access;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.ThreadingSupportImpl;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class StackOverflowCheckImpl implements StackOverflowCheck {
    // The stack boundary for the stack overflow check
    public static final FastThreadLocalWord<UnsignedWord> stackBoundaryTL = FastThreadLocalFactory.createWord("StackOverflowCheckImpl.stackBoundaryTL").setMaxOffset(FastThreadLocal.FIRST_CACHE_LINE);

    /**
     * Stores a counter how often the yellow zone has been made available, so that the yellow zone
     * is only protected after a matching number of calls. Note that the counter doesn't start at 0:
     * 0 is the default value of thread local variables, so disallowing 0 as a valid value allows us
     * to to detect error in the state transitions.
     */
    static final FastThreadLocalInt yellowZoneStateTL = FastThreadLocalFactory.createInt("StackOverflowCheckImpl.yellowZoneStateTL");
    static final int STATE_UNINITIALIZED = 0;
    static final int STATE_YELLOW_ENABLED = 1;

    public static final SubstrateForeignCallDescriptor THROW_CACHED_STACK_OVERFLOW_ERROR = SnippetRuntime.findForeignCall(StackOverflowCheckImpl.class, "throwCachedStackOverflowError", true);
    public static final SubstrateForeignCallDescriptor THROW_NEW_STACK_OVERFLOW_ERROR = SnippetRuntime.findForeignCall(StackOverflowCheckImpl.class, "throwNewStackOverflowError", true);

    static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{THROW_CACHED_STACK_OVERFLOW_ERROR, THROW_NEW_STACK_OVERFLOW_ERROR};

    @Platforms(Platform.HOSTED_ONLY.class)
    public StackOverflowCheckImpl() {
    }

    @Uninterruptible(reason = "Called while thread is being attached to the VM, i.e., when the thread state is not yet set up.")
    @Override
    public boolean initialize() {
        /* Get the physical end of the stack. Everything past this point is memory-protected. */
        WordPointer stackBasePtr = StackValue.get(WordPointer.class);
        WordPointer stackEndPtr = StackValue.get(WordPointer.class);
        if (!PlatformSupport.singleton().lookupStack(stackBasePtr, stackEndPtr)) {
            return false;
        }

        UnsignedWord stackBase = stackBasePtr.read();
        UnsignedWord stackEnd = stackEndPtr.read();
        VMError.guarantee(stackEnd.notEqual(0), "Stack end must not be 0");

        /* Cache the stack boundaries in thread locals and setup yellow/red zone. */
        VMThreads.StackBase.set(stackBase);
        VMThreads.StackEnd.set(stackEnd);
        setupYellowAndRedZone(stackEnd);
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void setupYellowAndRedZone(UnsignedWord stackEnd) {
        /*
         * The memory of the yellow and red zone is not protected, it is just a soft limit that we
         * can change.
         */
        stackBoundaryTL.set(stackEnd.add(Options.StackYellowZoneSize.getValue() + Options.StackRedZoneSize.getValue()));
        yellowZoneStateTL.set(STATE_YELLOW_ENABLED);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isWithinBounds(UnsignedWord address) {
        return stackBoundaryTL.get().belowOrEqual(address) && VMThreads.StackBase.get().aboveOrEqual(address);
    }

    @Override
    public int getState() {
        return yellowZoneStateTL.get();
    }

    @Override
    @Uninterruptible(reason = "Atomically manipulating state of multiple thread local variables.")
    public void setState(int newState) {
        int oldState = yellowZoneStateTL.get();
        yellowZoneStateTL.set(newState);

        if (newState > oldState) {
            onYellowZoneMadeAvailable(oldState, newState);
        } else if (newState < oldState) {
            onYellowZoneProtected(oldState, newState);
        }
    }

    @Uninterruptible(reason = "Atomically manipulating state of multiple thread local variables.")
    @Override
    public void makeYellowZoneAvailable() {
        // Performance-sensitive: intentionally not calling setState(yellowZoneStateTL.get() + 1)
        int oldState = yellowZoneStateTL.get();
        int newState = oldState + 1;
        yellowZoneStateTL.set(newState);
        onYellowZoneMadeAvailable(oldState, newState);
    }

    @Uninterruptible(reason = "Atomically manipulating state of multiple thread local variables.")
    private static void onYellowZoneMadeAvailable(int oldState, int newState) {
        VMError.guarantee(newState > oldState && newState > STATE_YELLOW_ENABLED, "StackOverflowCheckImpl.onYellowZoneMadeAvailable: Illegal state");

        if (oldState == STATE_YELLOW_ENABLED) {
            /*
             * Even though "yellow zones" and "recurring callbacks" are orthogonal features, running
             * a recurring callback in the yellow zone is dangerous because a stack overflow in the
             * recurring callback would then lead to a fatal error.
             */
            ThreadingSupportImpl.pauseRecurringCallback("Recurring callbacks are considered user code and must not run in yellow zone");

            stackBoundaryTL.set(stackBoundaryTL.get().subtract(Options.StackYellowZoneSize.getValue()));
        }

        /*
         * Check that after enabling the yellow zone there is actually stack space available again.
         * Otherwise we would immediately throw a StackOverflowError when reaching the first
         * non-Uninterruptible callee, and then we would recursively end up here again.
         */
        UnsignedWord stackBoundary = StackOverflowCheckImpl.stackBoundaryTL.get();
        if (KnownIntrinsics.readStackPointer().belowOrEqual(stackBoundary)) {
            throw VMError.shouldNotReachHere("StackOverflowError: Enabling the yellow zone of the stack did not make any stack space available. Possible reasons for that: " +
                            "1) A call from native code to Java code provided the wrong JNI environment or the wrong IsolateThread; " +
                            "2) Frames of native code filled the stack, and now there is not even enough stack space left to throw a regular StackOverflowError; " +
                            "3) An internal VM error occurred.");
        }
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isYellowZoneAvailable() {
        return yellowZoneStateTL.get() > STATE_YELLOW_ENABLED;
    }

    @Uninterruptible(reason = "Atomically manipulating state of multiple thread local variables.")
    @Override
    public void protectYellowZone() {
        // Performance-sensitive: intentionally not calling setState(yellowZoneStateTL.get() - 1)
        int oldState = yellowZoneStateTL.get();
        int newState = oldState - 1;
        yellowZoneStateTL.set(newState);
        onYellowZoneProtected(oldState, newState);
    }

    @Uninterruptible(reason = "Atomically manipulating state of multiple thread local variables.")
    private static void onYellowZoneProtected(int oldState, int newState) {
        VMError.guarantee(newState < oldState && newState >= STATE_YELLOW_ENABLED, "StackOverflowCheckImpl.onYellowZoneProtected: Illegal state");

        if (newState == STATE_YELLOW_ENABLED) {
            ThreadingSupportImpl.resumeRecurringCallbackAtNextSafepoint();

            stackBoundaryTL.set(stackBoundaryTL.get().add(Options.StackYellowZoneSize.getValue()));
        }
    }

    @Override
    public int yellowAndRedZoneSize() {
        return Options.StackYellowZoneSize.getValue() + Options.StackRedZoneSize.getValue();
    }

    @Uninterruptible(reason = "Called by fatal error handling that is uninterruptible.")
    @Override
    public void disableStackOverflowChecksForFatalError() {
        /*
         * Setting the boundary to a low value effectively disables the check. We are not using 0 so
         * that we can distinguish the value set here from an uninitialized value.
         */
        stackBoundaryTL.set(WordFactory.unsigned(1));
        /*
         * A random marker value. The actual value does not matter, but having a high value also
         * ensures that any future calls to protectYellowZone() do not modify the stack boundary
         * again.
         */
        yellowZoneStateTL.set(0x7EFEFEFE);
    }

    /**
     * If the stack size is larger than the requested stack size, then artificially limit the stack
     * to match the requested stack size.
     */
    @Uninterruptible(reason = "Atomically manipulating state of multiple thread local variables.")
    private static void updateStackOverflowBoundary(UnsignedWord requestedStackSize) {
        UnsignedWord stackBase = VMThreads.StackBase.get();
        UnsignedWord stackEnd = VMThreads.StackEnd.get();
        if (stackBase.equal(0) || stackEnd.equal(0)) {
            /* The stack size is unknown. */
            return;
        }

        UnsignedWord stackSize = stackBase.subtract(stackEnd);
        if (stackSize.aboveThan(requestedStackSize)) {
            UnsignedWord newStackEnd = stackBase.subtract(requestedStackSize);
            VMThreads.StackEnd.set(newStackEnd);
            setupYellowAndRedZone(newStackEnd);
        }
    }

    @Override
    public void updateStackOverflowBoundary() {
        long threadSize = PlatformThreads.getRequestedStackSize(Thread.currentThread());
        if (threadSize != 0) {
            updateStackOverflowBoundary(WordFactory.unsigned(threadSize));
        }
    }

    @Override
    public UnsignedWord getStackOverflowBoundary() {
        return stackBoundaryTL.get();
    }

    /**
     * Throw a cached {@link StackOverflowError} (without a stack trace) when we know statically
     * that the method with the stack overflow check must never allocate.
     */
    @Uninterruptible(reason = "Must not have a stack overflow check: we are here because the stack overflow check failed.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwCachedStackOverflowError() {
        VMError.guarantee(StackOverflowCheckImpl.yellowZoneStateTL.get() != StackOverflowCheckImpl.STATE_UNINITIALIZED,
                        "Stack boundary for the current thread not yet initialized. Only uninterruptible code with no stack overflow checks can run at this point.");

        throw ImplicitExceptions.CACHED_STACK_OVERFLOW_ERROR;
    }

    /**
     * Throw a new {@link StackOverflowError} (with a stack trace). In cases where we dynamically
     * find out that allocation is not possible at the current time, the cached
     * {@link StackOverflowError} is thrown.
     */
    @Uninterruptible(reason = "Must not have a stack overflow check: we are here because the stack overflow check failed.")
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewStackOverflowError() {
        int state = StackOverflowCheckImpl.yellowZoneStateTL.get();
        VMError.guarantee(state != StackOverflowCheckImpl.STATE_UNINITIALIZED,
                        "Stack boundary for the current thread not yet initialized. Only uninterruptible code with no stack overflow checks can run at this point.");

        StackOverflowError error;
        if (state > StackOverflowCheckImpl.STATE_YELLOW_ENABLED || Heap.getHeap().isAllocationDisallowed()) {
            error = ImplicitExceptions.CACHED_STACK_OVERFLOW_ERROR;
        } else {
            try {
                StackOverflowCheck.singleton().makeYellowZoneAvailable();
                error = newStackOverflowError();
            } finally {
                StackOverflowCheck.singleton().protectYellowZone();
            }
        }

        throw error;
    }

    @Uninterruptible(reason = "Allow allocation now that yellow zone is available for new stack frames", calleeMustBe = false)
    @RestrictHeapAccess(reason = "Allow allocation now that yellow zone is available for new stack frames", access = Access.UNRESTRICTED)
    private static StackOverflowError newStackOverflowError() {
        /*
         * Now that the yellow zone is enabled, we can allocate the error and collect the stack
         * trace. Note that this might even involve a GC run - the yellow zone is large enough to
         * accommodate that.
         *
         * We need another method call indirection to make our uninterruptible and no-allocation
         * verifiers happy.
         */
        return newStackOverflowError0();
    }

    private static StackOverflowError newStackOverflowError0() {
        return new StackOverflowError();
    }

    public static boolean needStackOverflowCheck(SharedMethod method) {
        if (method.isUninterruptible()) {
            /*
             * Uninterruptible methods are allowed to use the yellow and red zones of the stack.
             * Also, the thread register and stack boundary might not be set up. We cannot do a
             * stack overflow check.
             */
            return false;
        }
        return true;
    }

    public static long computeDeoptFrameSize(StructuredGraph graph) {
        long deoptFrameSize = 0;
        if (ImageInfo.inImageRuntimeCode()) {
            /*
             * Deoptimization must not lead to stack overflow errors, i.e., the deoptimization
             * source must check for a stack frame size large enough to cover all possible
             * deoptimization points (with all the methods inlined at that point). We do not know
             * which frame states are used for deoptimization, so we simply look at all frame states
             * and use the largest.
             *
             * Many frame states can share the same outer frame states. To avoid recomputing the
             * same information multiple times, we cache all values that we already computed.
             */
            NodeMap<Long> deoptFrameSizeCache = new NodeMap<>(graph);
            for (FrameState state : graph.getNodes(FrameState.TYPE)) {
                deoptFrameSize = Math.max(deoptFrameSize, computeDeoptFrameSize(state, deoptFrameSizeCache));
            }
        }
        return deoptFrameSize;
    }

    private static long computeDeoptFrameSize(FrameState state, NodeMap<Long> deoptFrameSizeCache) {
        Long existing = deoptFrameSizeCache.get(state);
        if (existing != null) {
            return existing;
        }

        long outerFrameSize = state.outerFrameState() == null ? 0 : computeDeoptFrameSize(state.outerFrameState(), deoptFrameSizeCache);
        long myFrameSize = CodeInfoAccess.lookupTotalFrameSize(CodeInfoTable.getImageCodeInfo(), ((SharedMethod) state.getMethod()).getDeoptOffsetInImage());

        long result = outerFrameSize + myFrameSize;
        deoptFrameSizeCache.put(state, result);
        return result;
    }
}

@NodeInfo(cycles = NodeCycles.CYCLES_4, size = NodeSize.SIZE_8)
final class StackOverflowCheckNode extends FixedWithNextNode implements Lowerable {
    public static final NodeClass<StackOverflowCheckNode> TYPE = NodeClass.create(StackOverflowCheckNode.class);

    protected StackOverflowCheckNode() {
        super(TYPE, StampFactory.forVoid());
    }
}

final class InsertStackOverflowCheckPhase extends BasePhase<MidTierContext> {

    @Override
    public boolean checkContract() {
        /*
         * This phase is necessary for correctness. The impact depends highly on the size of the
         * original graph.
         */
        return false;
    }

    @Override
    protected void run(StructuredGraph graph, MidTierContext context) {
        SharedMethod method = (SharedMethod) graph.method();
        if (((SubstrateBackend) context.getTargetProvider()).stackOverflowCheckedInPrologue(method) || !StackOverflowCheckImpl.needStackOverflowCheck(method)) {
            return;
        }
        /*
         * Insert the stack overflow node at the beginning of the graph. Note that it is not
         * strictly necessary that the stack overflow check is really the first piece of machine
         * code of the method, i.e., we do not require that the scheduler places no floating nodes
         * before the StackOverflowCheckNode. The red zone of the stack gives us enough room to
         * actually access the stack frame before the overflow check.
         */
        StackOverflowCheckNode stackOverflowCheckNode = graph.add(new StackOverflowCheckNode());
        graph.addAfterFixed(graph.start(), stackOverflowCheckNode);
    }
}

final class StackOverflowCheckSnippets extends SubstrateTemplates implements Snippets {

    /**
     * The snippet for the lowering of {@link StackOverflowCheckNode}, i.e., the fast-path stack
     * overflow check.
     *
     * Note that this code runs when the stack frame has already been set up completely, i.e., the
     * stack pointer has already been changed to the new value for the frame.
     */
    @Snippet
    private static void stackOverflowCheckSnippet(@ConstantParameter boolean mustNotAllocate, @ConstantParameter boolean hasDeoptFrameSize, long deoptFrameSize) {
        UnsignedWord stackBoundary = StackOverflowCheckImpl.stackBoundaryTL.get();
        if (hasDeoptFrameSize) {
            /*
             * Methods that can deoptimize must have enough space on the stack for all frames after
             * deoptimization.
             */
            stackBoundary = stackBoundary.add(WordFactory.unsigned(deoptFrameSize));
        }
        if (probability(EXTREMELY_SLOW_PATH_PROBABILITY, KnownIntrinsics.readStackPointer().belowOrEqual(stackBoundary))) {

            /*
             * This check is constant folded during snippet lowering, to avoid setting up a boolean
             * argument. This keeps the code (which is included in nearly every method) as compact
             * as possible.
             */
            if (mustNotAllocate) {
                callSlowPath(StackOverflowCheckImpl.THROW_CACHED_STACK_OVERFLOW_ERROR);
            } else {
                callSlowPath(StackOverflowCheckImpl.THROW_NEW_STACK_OVERFLOW_ERROR);
            }

            /* No control flow merge is necessary, the slow path never returns. */
            throw UnreachableNode.unreachable();
        }
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native void callSlowPath(@ConstantNodeParameter ForeignCallDescriptor descriptor);

    /*
     * Boilerplate code to register and perform the lowering.
     */

    private final Predicate<ResolvedJavaMethod> mustNotAllocatePredicate;

    private final SnippetInfo stackOverflowCheck;

    StackOverflowCheckSnippets(OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings,
                    Predicate<ResolvedJavaMethod> mustNotAllocatePredicate) {
        super(options, providers);

        this.stackOverflowCheck = snippet(providers, StackOverflowCheckSnippets.class, "stackOverflowCheckSnippet", StackOverflowCheckImpl.stackBoundaryTL.getLocationIdentity());
        this.mustNotAllocatePredicate = mustNotAllocatePredicate;

        lowerings.put(StackOverflowCheckNode.class, new StackOverflowCheckLowering());
    }

    final class StackOverflowCheckLowering implements NodeLoweringProvider<StackOverflowCheckNode> {
        @Override
        public void lower(StackOverflowCheckNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();

            long deoptFrameSize = StackOverflowCheckImpl.computeDeoptFrameSize(graph);

            Arguments args = new Arguments(stackOverflowCheck, graph.getGuardsStage(), tool.getLoweringStage());
            args.addConst("mustNotAllocate", mustNotAllocatePredicate != null && mustNotAllocatePredicate.test(graph.method()));
            args.addConst("hasDeoptFrameSize", deoptFrameSize > 0);
            args.add("deoptFrameSize", deoptFrameSize);
            template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}
