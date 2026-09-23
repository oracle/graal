/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.compile;

import static jdk.graal.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Required;

import java.util.Optional;

import com.oracle.svm.interpreter.ristretto.RistrettoOSRSupport;
import com.oracle.svm.interpreter.ristretto.RistrettoUtils;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EntryMarkerNode;
import jdk.graal.compiler.nodes.EntryProxyNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchored;
import jdk.graal.compiler.nodes.extended.StateSplitProxyNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.util.OnStackReplacementUtils;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Rewrites a parsed Ristretto OSR graph so execution starts at the bytecode backedge target.
 *
 * HotSpot OSR reads locals and locks from VM-managed OSR buffer nodes. Ristretto enters compiled code
 * from the Java interpreter instead, so the compiled graph must call {@link RistrettoOSRSupport}
 * helpers that read a thread-local transfer state prepared by the interpreter:
 *
 * <ol>
 * <li>Peel containing loops until the entry marker is reachable from the graph start.</li>
 * <li>Replace the old start with an OSR start at the entry marker.</li>
 * <li>Replace local and lock entry proxies with {@link RistrettoOSRSupport} getter calls.</li>
 * <li>Remove the old invocation-entry CFG.</li>
 * </ol>
 *
 * For a source loop such as:
 *
 * <pre>
 * beforeLoop();
 * int sum = 0;
 * int i = start;
 * while (i < limit) {
 *     osrEntryMarker();
 *     sum += i;
 *     i++;
 * }
 * return sum;
 * </pre>
 *
 * parsing from the invocation entry initially leaves the requested OSR BCI inside the loop. Peeling
 * clones one loop iteration so the graph has a legal preheader immediately before the loop body.
 *
 * After the start rewrite, the code before the loop is no longer part of the compiled entry path. The
 * rewritten graph instead starts at a synthetic OSR entry and materializes the live state there:
 *
 * <pre>
 * nativeOSREntryPoint();
 * int i = osrLocal(1);
 * int sum = osrLocal(2);
 * peeledOsrEntryMarker();
 * while (i < limit) {
 *     sum += i;
 *     i++;
 * }
 * return sum;
 * </pre>
 *
 * The loop peeling and start replacement mirror HotSpot's {@code OnStackReplacementPhase}, but the
 * value materialization contract is Ristretto-specific because there is no HotSpot OSR buffer.
 */
public final class RistrettoOnStackReplacementPhase extends BasePhase<HighTierContext> {
    @Override
    public Optional<NotApplicable> notApplicableTo(jdk.graal.compiler.nodes.GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    /**
     * Performs the OSR graph rewrite for graphs whose entry BCI is not the invocation entry.
     */
    @Override
    protected void run(StructuredGraph graph, HighTierContext context) {
        if (!graph.isOSR()) {
            return;
        }

        EntryMarkerNode osr = OnStackReplacementUtils.peelEntryLoops(graph, context.getLoopsDataProvider(), () -> getEntryMarker(graph),
                        RistrettoOnStackReplacementPhase::ensurePeelableRistrettoOSRLoop, (iterations, maxIterations) -> {
                            throw VMError.shouldNotReachHere("Ristretto OSR loop peeling exceeded original loop depth: " + iterations + " > " + maxIterations);
                        }, null);
        stripPlaceholderOuterFrameStates(graph);
        osr = getEntryMarker(graph);
        FrameState osrState = osr.stateAfter();
        if (osrState.stackSize() != 0) {
            throw new PermanentBailoutException("Ristretto OSR currently supports only empty expression stacks.");
        }

        FrameState entryState = duplicateWithDefaultEntryProxies(graph, osrState);
        StartReplacement startReplacement = replaceStartAtOSREntry(graph, osr, entryState);
        FixedWithNextNode insertionPoint = startReplacement.osrStart();
        insertionPoint = appendOSRLocalLoads(graph, insertionPoint, osrState, entryState);
        StateSplitProxyNode lastRestoreState = appendOSRLockLoads(graph, insertionPoint, osrState, entryState);
        if (lastRestoreState != null) {
            lastRestoreState.setStateAfter(osrState.duplicate());
        }
        verifyOSREntryProxiesReplaced(osrState);
        finishOSREntryRewrite(graph, osr, startReplacement);
    }

    /**
     * Replaces the invocation-entry start with a new start immediately before the OSR entry marker.
     *
     * The parsed graph still contains the normal method-entry control flow. Ristretto OSR needs a graph
     * start at the peeled loop header instead, so this method detaches the entry marker, gives the new
     * start the marker's frame state, and leaves the old start available for later CFG deletion.
     */
    private static StartReplacement replaceStartAtOSREntry(StructuredGraph graph, EntryMarkerNode osr, FrameState osrState) {
        StartNode oldStart = graph.start();
        StartNode osrStart = graph.add(new StartNode());
        FixedNode next = osr.next();
        osr.setStateAfter(null);
        osr.setNext(null);
        osrStart.setNext(next);
        osrStart.setStateAfter(osrState);
        graph.setStart(osrStart);
        return new StartReplacement(oldStart, osrStart);
    }

    private static void ensurePeelableRistrettoOSRLoop(Loop loop) {
        for (Node node : loop.inside().nodes()) {
            if (node instanceof ControlFlowAnchored) {
                throw new PermanentBailoutException("Ristretto OSR loop cannot be peeled because it contains a non-duplicable node: %s", node);
            }
        }
    }

    /**
     * Replaces each local entry proxy with a call that loads the matching slot from the transfer state.
     *
     * The helper call state uses a neutral entry state so the helper result does not become part of
     * its own deoptimization metadata. Later frame states use the anchored helper result, which keeps
     * deoptimization from OSR-compiled code faithful to the transferred interpreter frame.
     */
    private static FixedWithNextNode appendOSRLocalLoads(StructuredGraph graph, FixedWithNextNode insertionPoint, FrameState osrState, FrameState invokeState) {
        FixedWithNextNode current = insertionPoint;
        for (int i = 0; i < osrState.localsSize(); i++) {
            ValueNode value = osrState.localAt(i);
            if (value instanceof EntryProxyNode proxy) {
                LocalLoad replacement = appendLocalLoad(graph, current, invokeState, i, proxy.getStackKind(), proxy.stamp(NodeView.DEFAULT), false);
                current = replacement.insertionPoint();
                proxy.replaceAndDelete(replacement.value());
            }
        }
        return current;
    }

    /**
     * Replaces each lock entry proxy with a call that reloads the transferred monitor object.
     *
     * Lock proxies are object-typed but use a separate helper because they are indexed by lock depth
     * rather than by local slot.
     */
    private static StateSplitProxyNode appendOSRLockLoads(StructuredGraph graph, FixedWithNextNode insertionPoint, FrameState osrState, FrameState invokeState) {
        FixedWithNextNode current = insertionPoint;
        StateSplitProxyNode lastRestoreState = current instanceof StateSplitProxyNode stateSplit ? stateSplit : null;
        for (int i = 0; i < osrState.locksSize(); i++) {
            ValueNode value = osrState.lockAt(i);
            if (value instanceof EntryProxyNode proxy) {
                LocalLoad replacement = appendLocalLoad(graph, current, invokeState, i, JavaKind.Object, proxy.stamp(NodeView.DEFAULT), true);
                current = replacement.insertionPoint();
                lastRestoreState = replacement.insertionPoint();
                proxy.replaceAndDelete(replacement.value());
            }
        }
        return lastRestoreState;
    }

    /**
     * Redirects marker usages to the new start and removes the obsolete invocation-entry CFG.
     */
    private static void finishOSREntryRewrite(StructuredGraph graph, EntryMarkerNode osr, StartReplacement startReplacement) {
        osr.replaceAtUsages(startReplacement.osrStart(), InputType.Guard);
        osr.replaceAtUsages(startReplacement.osrStart(), InputType.Anchor);
        GraphUtil.killCFG(startReplacement.oldStart());
        new DeadCodeEliminationPhase(Required).apply(graph);
        verifyNoMethodParametersRemain(graph);
    }

    private static void verifyNoMethodParametersRemain(StructuredGraph graph) {
        if (graph.getNodes(ParameterNode.TYPE).count() != 0) {
            throw new PermanentBailoutException("Ristretto OSR graph still references Java method parameters after entry rewrite.");
        }
    }

    private static void verifyOSREntryProxiesReplaced(FrameState osrState) {
        for (int i = 0; i < osrState.localsSize(); i++) {
            if (osrState.localAt(i) instanceof EntryProxyNode) {
                throw new PermanentBailoutException("Ristretto OSR graph still references entry proxy for local %d after entry rewrite.", i);
            }
        }
        for (int i = 0; i < osrState.locksSize(); i++) {
            if (osrState.lockAt(i) instanceof EntryProxyNode) {
                throw new PermanentBailoutException("Ristretto OSR graph still references entry proxy for lock %d after entry rewrite.", i);
            }
        }
    }

    /**
     * Drops synthetic caller frame states that were only needed while parsing the OSR entry graph.
     *
     * Runtime OSR enters the Ristretto method directly, so preserving a placeholder caller state would
     * expose deoptimization metadata for a caller frame that does not exist on this compiled path.
     */
    private static void stripPlaceholderOuterFrameStates(StructuredGraph graph) {
        for (FrameState state : graph.getNodes(FrameState.TYPE)) {
            if (state.outerFrameState() != null && state.outerFrameState().bci < 0) {
                // Runtime OSR enters this method directly, so placeholder caller states cannot be
                // preserved as deoptimization metadata.
                state.setOuterFrameState(null);
            }
        }
    }

    /**
     * Appends one helper call that materializes an interpreter local or lock object at OSR entry.
     *
     * <pre>
     * slot constant -> RistrettoOSRSupport.get*Local/getLockObject
     * helper invoke -> value proxy preserving the OSR entry frame state
     * </pre>
     */
    private static LocalLoad appendLocalLoad(StructuredGraph graph, FixedWithNextNode insertionPoint, FrameState invokeState, int slot, JavaKind kind, Stamp proxyStamp, boolean lock) {
        JavaKind stackKind = kind.getStackKind();
        InvokeNode invoke = createLocalLoadInvoke(graph, slot, stackKind, proxyStamp, lock);
        invoke.setStateAfter(createHelperStateAfter(graph, invokeState, invoke, stackKind));
        graph.addAfterFixed(insertionPoint, invoke);
        StateSplitProxyNode restoreState = anchorLocalLoadResult(graph, invoke, invokeState);
        return new LocalLoad(restoreState, restoreState);
    }

    private static InvokeNode createLocalLoadInvoke(StructuredGraph graph, int slot, JavaKind stackKind, Stamp proxyStamp, boolean lock) {
        ResolvedJavaMethod targetMethod = lookupGetter(stackKind, lock);
        ConstantNode slotNode = ConstantNode.forInt(slot, graph);
        Stamp returnStamp = localLoadReturnStamp(stackKind, proxyStamp);
        MethodCallTargetNode callTarget = graph.add(new MethodCallTargetNode(InvokeKind.Static, targetMethod, new ValueNode[]{slotNode}, StampPair.createSingle(returnStamp), null));
        return graph.add(new InvokeNode(callTarget, graph.getEntryBCI()));
    }

    private static Stamp localLoadReturnStamp(JavaKind stackKind, Stamp proxyStamp) {
        Stamp returnStamp = stackKind == JavaKind.Object ? StampFactory.object() : StampFactory.forKind(stackKind);
        if (stackKind == JavaKind.Object && proxyStamp != null) {
            returnStamp = proxyStamp.unrestricted();
        }
        return returnStamp;
    }

    private static FrameState createHelperStateAfter(StructuredGraph graph, FrameState invokeState, InvokeNode invoke, JavaKind stackKind) {
        return invokeState.duplicateModified(graph, invoke.bci(), invokeState.getStackState(), JavaKind.Void, new JavaKind[]{stackKind}, new ValueNode[]{invoke}, null, false);
    }

    private static StateSplitProxyNode anchorLocalLoadResult(StructuredGraph graph, InvokeNode invoke, FrameState invokeState) {
        StateSplitProxyNode restoreState = graph.add(new StateSplitProxyNode(invoke));
        restoreState.setStateAfter(invokeState);
        graph.addAfterFixed(invoke, restoreState);
        return restoreState;
    }

    /**
     * Creates the frame state used by OSR entry helper calls.
     *
     * The parsed OSR state contains entry proxies for values that are not materialized until the helper
     * calls below run. Helper-call states therefore use neutral values for those proxies; otherwise the
     * helper result could become part of its own state and form a deoptimization-state cycle.
     */
    private static FrameState duplicateWithDefaultEntryProxies(StructuredGraph graph, FrameState osrState) {
        return graph.add(osrState.duplicate((index, value) -> {
            assert index >= 0;
            return value instanceof EntryProxyNode proxy ? defaultFrameStateValue(graph, proxy.getStackKind()) : value;
        }));
    }

    private static ValueNode defaultFrameStateValue(StructuredGraph graph, JavaKind kind) {
        return ConstantNode.defaultForKind(kind.getStackKind(), graph);
    }

    /**
     * Resolves the preserved Ristretto helper that loads one OSR local or lock slot.
     */
    private static ResolvedJavaMethod lookupGetter(JavaKind kind, boolean lock) {
        return RistrettoUtils.lookupOSRGetter(kind, lock);
    }

    /**
     * Returns the unique OSR entry marker emitted by graph building.
     */
    private static EntryMarkerNode getEntryMarker(StructuredGraph graph) {
        NodeIterable<EntryMarkerNode> osrNodes = graph.getNodes(EntryMarkerNode.TYPE);
        EntryMarkerNode osr = osrNodes.first();
        if (osr == null) {
            throw new PermanentBailoutException("Ristretto OSR entry point was not parsed.");
        }
        if (osrNodes.count() > 1) {
            throw new PermanentBailoutException("Ristretto OSR graph has multiple entry markers.");
        }
        return osr;
    }

    /**
     * Result of appending a local-load helper call and the fixed node that follows it.
     */
    private record LocalLoad(ValueNode value, StateSplitProxyNode insertionPoint) {
    }

    /**
     * The old invocation start plus the replacement start that now owns the OSR entry state.
     */
    private record StartReplacement(StartNode oldStart, StartNode osrStart) {
    }
}
