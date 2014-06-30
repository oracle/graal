/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.hotspot.nodes.BeginLockScopeNode.*;
import static com.oracle.graal.hotspot.nodes.DirectCompareAndSwapNode.*;
import static com.oracle.graal.hotspot.nodes.EndLockScopeNode.*;
import static com.oracle.graal.hotspot.nodes.VMErrorNode.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.*;
import static com.oracle.graal.replacements.SnippetTemplate.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.debug.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.options.*;
import com.oracle.graal.phases.common.inlining.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.replacements.Snippet.Fold;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.word.*;

/**
 * Snippets used for implementing the monitorenter and monitorexit instructions.
 *
 * The locking algorithm used is described in the paper <a
 * href="http://dl.acm.org/citation.cfm?id=1167515.1167496"> Eliminating synchronization-related
 * atomic operations with biased locking and bulk rebiasing</a> by Kenneth Russell and David
 * Detlefs.
 */
public class MonitorSnippets implements Snippets {

    public static class Options {

        //@formatter:off
        @Option(help = "")
        private static final OptionValue<Boolean> ProfileMonitors = new OptionValue<>(false);
        //@formatter:on
    }

    private static final boolean PROFILE_CONTEXT = false;

    @Fold
    private static boolean doProfile() {
        return Options.ProfileMonitors.getValue();
    }

    /**
     * Monitor operations on objects whose type contains this substring will be traced.
     */
    private static final String TRACE_TYPE_FILTER = System.getProperty("graal.monitors.trace.typeFilter");

    /**
     * Monitor operations in methods whose fully qualified name contains this substring will be
     * traced.
     */
    private static final String TRACE_METHOD_FILTER = System.getProperty("graal.monitors.trace.methodFilter");

    public static final boolean CHECK_BALANCED_MONITORS = Boolean.getBoolean("graal.monitors.checkBalanced");

    @Snippet
    public static void monitorenter(Object object, @ConstantParameter int lockDepth, @ConstantParameter Register threadRegister, @ConstantParameter Register stackPointerRegister,
                    @ConstantParameter boolean trace) {
        verifyOop(object);

        if (object == null) {
            DeoptimizeNode.deopt(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.NullCheckException);
        }
        GuardingNode anchorNode = SnippetAnchorNode.anchor();

        // Load the mark word - this includes a null-check on object
        final Word mark = loadWordFromObject(object, markOffset());

        final Word lock = beginLockScope(lockDepth);

        trace(trace, "           object: 0x%016lx\n", Word.fromObject(object));
        trace(trace, "             lock: 0x%016lx\n", lock);
        trace(trace, "             mark: 0x%016lx\n", mark);

        incCounter();

        if (useBiasedLocking()) {
            // See whether the lock is currently biased toward our thread and
            // whether the epoch is still valid.
            // Note that the runtime guarantees sufficient alignment of JavaThread
            // pointers to allow age to be placed into low bits.
            final Word biasableLockBits = mark.and(biasedLockMaskInPlace());

            // First check to see whether biasing is enabled for this object
            if (probability(NOT_FREQUENT_PROBABILITY, biasableLockBits.notEqual(Word.unsigned(biasedLockPattern())))) {
                // Biasing not enabled -> fall through to lightweight locking
            } else {
                // The bias pattern is present in the object's mark word. Need to check
                // whether the bias owner and the epoch are both still current.
                Word hub = loadHubIntrinsic(object, getWordKind(), anchorNode);
                final Word prototypeMarkWord = hub.readWord(prototypeMarkWordOffset(), PROTOTYPE_MARK_WORD_LOCATION);
                final Word thread = registerAsWord(threadRegister);
                final Word tmp = prototypeMarkWord.or(thread).xor(mark).and(~ageMaskInPlace());
                trace(trace, "prototypeMarkWord: 0x%016lx\n", prototypeMarkWord);
                trace(trace, "           thread: 0x%016lx\n", thread);
                trace(trace, "              tmp: 0x%016lx\n", tmp);
                if (probability(FREQUENT_PROBABILITY, tmp.equal(0))) {
                    // Object is already biased to current thread -> done
                    traceObject(trace, "+lock{bias:existing}", object, true);
                    return;
                }

                // At this point we know that the mark word has the bias pattern and
                // that we are not the bias owner in the current epoch. We need to
                // figure out more details about the state of the mark word in order to
                // know what operations can be legally performed on the object's
                // mark word.

                // If the low three bits in the xor result aren't clear, that means
                // the prototype header is no longer biasable and we have to revoke
                // the bias on this object.
                if (probability(FREQUENT_PROBABILITY, tmp.and(biasedLockMaskInPlace()).equal(0))) {
                    // Biasing is still enabled for object's type. See whether the
                    // epoch of the current bias is still valid, meaning that the epoch
                    // bits of the mark word are equal to the epoch bits of the
                    // prototype mark word. (Note that the prototype mark word's epoch bits
                    // only change at a safepoint.) If not, attempt to rebias the object
                    // toward the current thread. Note that we must be absolutely sure
                    // that the current epoch is invalid in order to do this because
                    // otherwise the manipulations it performs on the mark word are
                    // illegal.
                    if (probability(FREQUENT_PROBABILITY, tmp.and(epochMaskInPlace()).equal(0))) {
                        // The epoch of the current bias is still valid but we know nothing
                        // about the owner; it might be set or it might be clear. Try to
                        // acquire the bias of the object using an atomic operation. If this
                        // fails we will go in to the runtime to revoke the object's bias.
                        // Note that we first construct the presumed unbiased header so we
                        // don't accidentally blow away another thread's valid bias.
                        Word unbiasedMark = mark.and(biasedLockMaskInPlace() | ageMaskInPlace() | epochMaskInPlace());
                        Word biasedMark = unbiasedMark.or(thread);
                        trace(trace, "     unbiasedMark: 0x%016lx\n", unbiasedMark);
                        trace(trace, "       biasedMark: 0x%016lx\n", biasedMark);
                        if (probability(VERY_FAST_PATH_PROBABILITY, compareAndSwap(object, markOffset(), unbiasedMark, biasedMark, MARK_WORD_LOCATION).equal(unbiasedMark))) {
                            // Object is now biased to current thread -> done
                            traceObject(trace, "+lock{bias:acquired}", object, true);
                            return;
                        }
                        // If the biasing toward our thread failed, this means that another thread
                        // owns the bias and we need to revoke that bias. The revocation will occur
                        // in the interpreter runtime.
                        traceObject(trace, "+lock{stub:revoke}", object, true);
                        monitorenterStubC(MONITORENTER, object, lock);
                        return;
                    } else {
                        // At this point we know the epoch has expired, meaning that the
                        // current bias owner, if any, is actually invalid. Under these
                        // circumstances _only_, are we allowed to use the current mark word
                        // value as the comparison value when doing the CAS to acquire the
                        // bias in the current epoch. In other words, we allow transfer of
                        // the bias from one thread to another directly in this situation.
                        Word biasedMark = prototypeMarkWord.or(thread);
                        trace(trace, "       biasedMark: 0x%016lx\n", biasedMark);
                        if (probability(VERY_FAST_PATH_PROBABILITY, compareAndSwap(object, markOffset(), mark, biasedMark, MARK_WORD_LOCATION).equal(mark))) {
                            // Object is now biased to current thread -> done
                            traceObject(trace, "+lock{bias:transfer}", object, true);
                            return;
                        }
                        // If the biasing toward our thread failed, then another thread
                        // succeeded in biasing it toward itself and we need to revoke that
                        // bias. The revocation will occur in the runtime in the slow case.
                        traceObject(trace, "+lock{stub:epoch-expired}", object, true);
                        monitorenterStubC(MONITORENTER, object, lock);
                        return;
                    }
                } else {
                    // The prototype mark word doesn't have the bias bit set any
                    // more, indicating that objects of this data type are not supposed
                    // to be biased any more. We are going to try to reset the mark of
                    // this object to the prototype value and fall through to the
                    // CAS-based locking scheme. Note that if our CAS fails, it means
                    // that another thread raced us for the privilege of revoking the
                    // bias of this particular object, so it's okay to continue in the
                    // normal locking code.
                    Word result = compareAndSwap(object, markOffset(), mark, prototypeMarkWord, MARK_WORD_LOCATION);

                    // Fall through to the normal CAS-based lock, because no matter what
                    // the result of the above CAS, some thread must have succeeded in
                    // removing the bias bit from the object's header.

                    if (ENABLE_BREAKPOINT) {
                        bkpt(object, mark, tmp, result);
                    }
                }
            }
        }

        // Create the unlocked mark word pattern
        Word unlockedMark = mark.or(unlockedMask());
        trace(trace, "     unlockedMark: 0x%016lx\n", unlockedMark);

        // Copy this unlocked mark word into the lock slot on the stack
        lock.writeWord(lockDisplacedMarkOffset(), unlockedMark, DISPLACED_MARK_WORD_LOCATION);

        // Test if the object's mark word is unlocked, and if so, store the
        // (address of) the lock slot into the object's mark word.
        Word currentMark = compareAndSwap(object, markOffset(), unlockedMark, lock, MARK_WORD_LOCATION);
        if (currentMark.notEqual(unlockedMark)) {
            trace(trace, "      currentMark: 0x%016lx\n", currentMark);
            // The mark word in the object header was not the same.
            // Either the object is locked by another thread or is already locked
            // by the current thread. The latter is true if the mark word
            // is a stack pointer into the current thread's stack, i.e.:
            //
            // 1) (currentMark & aligned_mask) == 0
            // 2) rsp <= currentMark
            // 3) currentMark <= rsp + page_size
            //
            // These 3 tests can be done by evaluating the following expression:
            //
            // (currentMark - rsp) & (aligned_mask - page_size)
            //
            // assuming both the stack pointer and page_size have their least
            // significant 2 bits cleared and page_size is a power of 2
            final Word alignedMask = Word.unsigned(wordSize() - 1);
            final Word stackPointer = registerAsWord(stackPointerRegister);
            if (probability(VERY_SLOW_PATH_PROBABILITY, currentMark.subtract(stackPointer).and(alignedMask.subtract(pageSize())).notEqual(0))) {
                // Most likely not a recursive lock, go into a slow runtime call
                traceObject(trace, "+lock{stub:failed-cas}", object, true);
                monitorenterStubC(MONITORENTER, object, lock);
                return;
            } else {
                // Recursively locked => write 0 to the lock slot
                lock.writeWord(lockDisplacedMarkOffset(), Word.zero(), DISPLACED_MARK_WORD_LOCATION);
                traceObject(trace, "+lock{recursive}", object, true);
            }
        } else {
            traceObject(trace, "+lock{cas}", object, true);
        }
    }

    /**
     * Calls straight out to the monitorenter stub.
     */
    @Snippet
    public static void monitorenterStub(Object object, @ConstantParameter int lockDepth, @ConstantParameter boolean trace) {
        verifyOop(object);
        incCounter();
        if (object == null) {
            DeoptimizeNode.deopt(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.NullCheckException);
        }
        // BeginLockScope nodes do not read from object so a use of object
        // cannot float about the null check above
        final Word lock = beginLockScope(lockDepth);
        traceObject(trace, "+lock{stub}", object, true);
        monitorenterStubC(MONITORENTER, object, lock);
    }

    @Snippet
    public static void monitorexit(Object object, @ConstantParameter int lockDepth, @ConstantParameter boolean trace) {
        trace(trace, "           object: 0x%016lx\n", Word.fromObject(object));
        if (useBiasedLocking()) {
            // Check for biased locking unlock case, which is a no-op
            // Note: we do not have to check the thread ID for two reasons.
            // First, the interpreter checks for IllegalMonitorStateException at
            // a higher level. Second, if the bias was revoked while we held the
            // lock, the object could not be rebiased toward another thread, so
            // the bias bit would be clear.
            final Word mark = loadWordFromObject(object, markOffset());
            trace(trace, "             mark: 0x%016lx\n", mark);
            if (probability(FREQUENT_PROBABILITY, mark.and(biasedLockMaskInPlace()).equal(Word.unsigned(biasedLockPattern())))) {
                endLockScope();
                decCounter();
                traceObject(trace, "-lock{bias}", object, false);
                return;
            }
        }

        final Word lock = CurrentLockNode.currentLock(lockDepth);

        // Load displaced mark
        final Word displacedMark = lock.readWord(lockDisplacedMarkOffset(), DISPLACED_MARK_WORD_LOCATION);
        trace(trace, "    displacedMark: 0x%016lx\n", displacedMark);

        if (displacedMark.equal(0)) {
            // Recursive locking => done
            traceObject(trace, "-lock{recursive}", object, false);
        } else {
            verifyOop(object);
            // Test if object's mark word is pointing to the displaced mark word, and if so, restore
            // the displaced mark in the object - if the object's mark word is not pointing to
            // the displaced mark word, do unlocking via runtime call.
            if (probability(VERY_SLOW_PATH_PROBABILITY, DirectCompareAndSwapNode.compareAndSwap(object, markOffset(), lock, displacedMark, MARK_WORD_LOCATION).notEqual(lock))) {
                // The object's mark word was not pointing to the displaced header,
                // we do unlocking via runtime call.
                traceObject(trace, "-lock{stub}", object, false);
                monitorexitStubC(MONITOREXIT, object, lock);
            } else {
                traceObject(trace, "-lock{cas}", object, false);
            }
        }
        endLockScope();
        decCounter();
    }

    /**
     * Calls straight out to the monitorexit stub.
     */
    @Snippet
    public static void monitorexitStub(Object object, @ConstantParameter int lockDepth, @ConstantParameter boolean trace) {
        verifyOop(object);
        traceObject(trace, "-lock{stub}", object, false);
        final Word lock = CurrentLockNode.currentLock(lockDepth);
        monitorexitStubC(MONITOREXIT, object, lock);
        endLockScope();
        decCounter();
    }

    private static void traceObject(boolean enabled, String action, Object object, boolean enter) {
        if (doProfile()) {
            DynamicCounterNode.counter(action, enter ? "number of monitor enters" : "number of monitor exits", 1, PROFILE_CONTEXT);
        }
        if (enabled) {
            Log.print(action);
            Log.print(' ');
            Log.printlnObject(object);
        }
    }

    private static void trace(boolean enabled, String format, WordBase value) {
        if (enabled) {
            Log.printf(format, value.rawValue());
        }
    }

    /**
     * Leaving the breakpoint code in to provide an example of how to use the {@link BreakpointNode}
     * intrinsic.
     */
    private static final boolean ENABLE_BREAKPOINT = false;

    private static final LocationIdentity MONITOR_COUNTER_LOCATION = new NamedLocationIdentity("MonitorCounter");

    @NodeIntrinsic(BreakpointNode.class)
    static native void bkpt(Object object, Word mark, Word tmp, Word value);

    private static void incCounter() {
        if (CHECK_BALANCED_MONITORS) {
            final Word counter = MonitorCounterNode.counter();
            final int count = counter.readInt(0, MONITOR_COUNTER_LOCATION);
            counter.writeInt(0, count + 1, MONITOR_COUNTER_LOCATION);
        }
    }

    private static void decCounter() {
        if (CHECK_BALANCED_MONITORS) {
            final Word counter = MonitorCounterNode.counter();
            final int count = counter.readInt(0, MONITOR_COUNTER_LOCATION);
            counter.writeInt(0, count - 1, MONITOR_COUNTER_LOCATION);
        }
    }

    @Snippet
    private static void initCounter() {
        final Word counter = MonitorCounterNode.counter();
        counter.writeInt(0, 0, MONITOR_COUNTER_LOCATION);
    }

    @Snippet
    private static void checkCounter(@ConstantParameter String errMsg) {
        final Word counter = MonitorCounterNode.counter();
        final int count = counter.readInt(0, MONITOR_COUNTER_LOCATION);
        if (count != 0) {
            vmError(errMsg, count);
        }
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo monitorenter = snippet(MonitorSnippets.class, "monitorenter");
        private final SnippetInfo monitorexit = snippet(MonitorSnippets.class, "monitorexit");
        private final SnippetInfo monitorenterStub = snippet(MonitorSnippets.class, "monitorenterStub");
        private final SnippetInfo monitorexitStub = snippet(MonitorSnippets.class, "monitorexitStub");
        private final SnippetInfo initCounter = snippet(MonitorSnippets.class, "initCounter");
        private final SnippetInfo checkCounter = snippet(MonitorSnippets.class, "checkCounter");

        private final boolean useFastLocking;

        public Templates(HotSpotProviders providers, TargetDescription target, boolean useFastLocking) {
            super(providers, providers.getSnippetReflection(), target);
            this.useFastLocking = useFastLocking;
        }

        public void lower(MonitorEnterNode monitorenterNode, HotSpotRegistersProvider registers, LoweringTool tool) {
            StructuredGraph graph = monitorenterNode.graph();
            checkBalancedMonitors(graph, tool);

            Arguments args;
            if (useFastLocking) {
                args = new Arguments(monitorenter, graph.getGuardsStage(), tool.getLoweringStage());
            } else {
                args = new Arguments(monitorenterStub, graph.getGuardsStage(), tool.getLoweringStage());
            }
            args.add("object", monitorenterNode.object());
            args.addConst("lockDepth", monitorenterNode.getMonitorId().getLockDepth());
            args.addConst("threadRegister", registers.getThreadRegister());
            args.addConst("stackPointerRegister", registers.getStackPointerRegister());
            args.addConst("trace", isTracingEnabledForType(monitorenterNode.object()) || isTracingEnabledForMethod(graph.method()));

            template(args).instantiate(providers.getMetaAccess(), monitorenterNode, DEFAULT_REPLACER, args);
        }

        public void lower(MonitorExitNode monitorexitNode, LoweringTool tool) {
            StructuredGraph graph = monitorexitNode.graph();

            Arguments args;
            if (useFastLocking) {
                args = new Arguments(monitorexit, graph.getGuardsStage(), tool.getLoweringStage());
            } else {
                args = new Arguments(monitorexitStub, graph.getGuardsStage(), tool.getLoweringStage());
            }
            args.add("object", monitorexitNode.object());
            args.addConst("lockDepth", monitorexitNode.getMonitorId().getLockDepth());
            args.addConst("trace", isTracingEnabledForType(monitorexitNode.object()) || isTracingEnabledForMethod(graph.method()));

            template(args).instantiate(providers.getMetaAccess(), monitorexitNode, DEFAULT_REPLACER, args);
        }

        static boolean isTracingEnabledForType(ValueNode object) {
            ResolvedJavaType type = StampTool.typeOrNull(object.stamp());
            if (TRACE_TYPE_FILTER == null) {
                return false;
            } else {
                if (TRACE_TYPE_FILTER.length() == 0) {
                    return true;
                }
                if (type == null) {
                    return false;
                }
                return (type.getName().contains(TRACE_TYPE_FILTER));
            }
        }

        static boolean isTracingEnabledForMethod(ResolvedJavaMethod method) {
            if (TRACE_METHOD_FILTER == null) {
                return false;
            } else {
                if (TRACE_METHOD_FILTER.length() == 0) {
                    return true;
                }
                if (method == null) {
                    return false;
                }
                return (MetaUtil.format("%H.%n", method).contains(TRACE_METHOD_FILTER));
            }
        }

        /**
         * If balanced monitor checking is enabled then nodes are inserted at the start and all
         * return points of the graph to initialize and check the monitor counter respectively.
         */
        private void checkBalancedMonitors(StructuredGraph graph, LoweringTool tool) {
            if (CHECK_BALANCED_MONITORS) {
                NodeIterable<MonitorCounterNode> nodes = graph.getNodes().filter(MonitorCounterNode.class);
                if (nodes.isEmpty()) {
                    // Only insert the nodes if this is the first monitorenter being lowered.
                    JavaType returnType = initCounter.getMethod().getSignature().getReturnType(initCounter.getMethod().getDeclaringClass());
                    MethodCallTargetNode callTarget = graph.add(new MethodCallTargetNode(InvokeKind.Static, initCounter.getMethod(), new ValueNode[0], returnType));
                    InvokeNode invoke = graph.add(new InvokeNode(callTarget, 0));
                    invoke.setStateAfter(graph.start().stateAfter());
                    graph.addAfterFixed(graph.start(), invoke);

                    StructuredGraph inlineeGraph = providers.getReplacements().getSnippet(initCounter.getMethod());
                    InliningUtil.inline(invoke, inlineeGraph, false, null);

                    List<ReturnNode> rets = graph.getNodes(ReturnNode.class).snapshot();
                    for (ReturnNode ret : rets) {
                        returnType = checkCounter.getMethod().getSignature().getReturnType(checkCounter.getMethod().getDeclaringClass());
                        String msg = "unbalanced monitors in " + MetaUtil.format("%H.%n(%p)", graph.method()) + ", count = %d";
                        ConstantNode errMsg = ConstantNode.forConstant(HotSpotObjectConstant.forObject(msg), providers.getMetaAccess(), graph);
                        callTarget = graph.add(new MethodCallTargetNode(InvokeKind.Static, checkCounter.getMethod(), new ValueNode[]{errMsg}, returnType));
                        invoke = graph.add(new InvokeNode(callTarget, 0));
                        List<ValueNode> stack = Collections.emptyList();
                        FrameState stateAfter = new FrameState(graph.method(), BytecodeFrame.AFTER_BCI, new ValueNode[0], stack, new ValueNode[0], new MonitorIdNode[0], false, false);
                        invoke.setStateAfter(graph.add(stateAfter));
                        graph.addBeforeFixed(ret, invoke);

                        Arguments args = new Arguments(checkCounter, graph.getGuardsStage(), tool.getLoweringStage());
                        args.addConst("errMsg", msg);
                        inlineeGraph = template(args).copySpecializedGraph();

                        // inlineeGraph = replacements.getSnippet(checkCounter.getMethod());
                        InliningUtil.inline(invoke, inlineeGraph, false, null);
                    }
                }
            }
        }
    }

    public static final ForeignCallDescriptor MONITORENTER = new ForeignCallDescriptor("monitorenter", void.class, Object.class, Word.class);
    public static final ForeignCallDescriptor MONITOREXIT = new ForeignCallDescriptor("monitorexit", void.class, Object.class, Word.class);

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void monitorenterStubC(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object object, Word lock);

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void monitorexitStubC(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object object, Word lock);

}
