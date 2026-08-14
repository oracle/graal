/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.sboutlining;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.code.FactoryMethodMarker;
import com.oracle.svm.core.thread.ContinuationSupport;
import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.GuestAnnotationAccess;

import jdk.graal.compiler.core.common.cfg.AbstractControlFlowGraph;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.ValueProxyNode;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.nodes.virtual.MaterializedObjectState;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectState;
import jdk.graal.compiler.phases.graph.MergeableState;
import jdk.graal.compiler.phases.graph.PostOrderNodeIterator;
import jdk.graal.compiler.phases.graph.StatelessPostOrderNodeIterator;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Analyzes {@link StringBuilder} and {@link StringBuffer} operations and produces a materialization
 * plan for {@link SBOutliningPhase}.
 *
 * <p>
 * The analysis keeps supported allocations and appends virtual across the control-flow graph. It
 * places real builder, buffer, or string objects only where program behavior makes them observable.
 * Delaying materialization removes intermediate backing arrays, while sharing compatible
 * materializations limits the number of static calls introduced into the graph. Unsupported uses
 * cause the affected value to escape without changing its semantics.
 *
 * <p>
 * The analysis has three stages:
 *
 * <ol>
 * <li>{@link SBAllocationAssigner} groups aliases by their original allocation and records uses
 * that require an object to escape.</li>
 * <li>{@link SBMaterializer} follows control flow, tracks the virtual append state, and selects the
 * latest required materialization for each use.</li>
 * <li>{@link SBMaterializationPlacer} combines compatible states, raises materializations when that
 * reduces duplication, and creates the metadata consumed by the transformation phase.</li>
 * </ol>
 *
 * <p>
 * The model includes value phis, proxies, exception paths, constructor checks, and the JVM argument
 * slot limit. This class records the decisions. {@link SBOutliningPhase} performs the graph
 * mutations.
 */
@Platforms(Platform.HOSTED_ONLY.class)
final class SBOutliningAnalysis {
    private final StructuredGraph graph;
    private final ControlFlowGraph cfg;
    private final OutlinedSBMethodSupport outlinedSBMethodSupport;

    private final EconomicSet<ResolvedJavaType> twoSlotTypes;
    private final EconomicSet<ResolvedJavaType> handledSBTypes;
    private final EconomicSet<ResolvedJavaMethod> forcedMaterializationCandidates;
    private final EconomicMap<ResolvedJavaMethod, SBOp> sbOpKinds;

    private final EconomicMap<Node, SBAllocation> aliasToSBAllocation = EconomicMap.create(Equivalence.DEFAULT);
    private final EconomicMap<FixedNode, List<SBAllocation>> escapingSBsAtNode = EconomicMap.create(Equivalence.DEFAULT);

    private final EconomicMap<MaterializedSB, List<Node>> materializedSBToUses = EconomicMap.create(Equivalence.DEFAULT);
    private List<SBAllocation> sbAllocations;

    private final boolean inFactoryMethod;

    /**
     * JVM allows parameters passed to an invoke to use at most 255 slots. If the stringifies use
     * more slots than this, then the SB must be materialized. However, since SBs are created though
     * a series of method handle invocations (via SubstrateSBConcatFactory), we cannot use the full
     * 255 slots for stringifies, but are limited to at most 249. These extra six parameters account
     * for the initial buffer capacity and also three parameters (byte[], long, long) used during
     * the SB build-time factory to create the method.
     *
     * As an additional safeguard against future implementation changes, we further limit the number
     * of slots allowed to 240.
     */
    private static final int MAX_STRINGIFY_SLOTS = 240;

    SBOutliningAnalysis(StructuredGraph graph, MetaAccessProvider metaAccess) {
        this.graph = graph;

        MetadataLookup metadataLookup = ImageSingletons.lookup(MetadataLookup.class);
        metadataLookup.initialize(metaAccess);
        twoSlotTypes = metadataLookup.getTwoSlotTypes();
        handledSBTypes = metadataLookup.getHandledSBTypes();
        sbOpKinds = metadataLookup.getSBOpKinds();
        forcedMaterializationCandidates = metadataLookup.getForcedMaterializationCandidates();

        inFactoryMethod = GuestAnnotationAccess.isAnnotationPresent(graph.method().getDeclaringClass(), FactoryMethodMarker.class);

        cfg = ControlFlowGraph.newBuilder(graph).modifiableBlocks(true).connectBlocks(true).computeFrequency(true).computeDominators(true).build();

        outlinedSBMethodSupport = OutlinedSBMethodSupport.singleton();
    }

    List<SBAllocation> performAnalysis() {
        calculateSBAllocations();
        performMaterializationAnalysis();
        List<SBAllocation> sbs = selectVirtualizedSBs();
        updateCounters(sbs);
        return sbs;
    }

    private void updateCounters(List<SBAllocation> sbs) {
        if (!SBOutliningFeature.Options.PrintSBOutliningCounters.getValue()) {
            return;
        }

        for (SBAllocation allocation : sbs) {
            EconomicSet<MaterializedConcreteSB> concrete = allocation.usedConcreteMaterializations;
            long toStrings = 0;
            for (MaterializedConcreteSB sb : concrete) {
                if (sb instanceof MaterializedToStringSB) {
                    toStrings++;
                }
            }

            EconomicSet<InvokeWithExceptionNode> appends = EconomicSet.create(Equivalence.DEFAULT);
            EconomicSet<InvokeWithExceptionNode> inits = EconomicSet.create(Equivalence.DEFAULT, sbs.size());
            concrete.forEach(sb -> {
                inits.add(sb.virtualSB.init);
                List<InvokeWithExceptionNode> stringifies = sb.virtualSB.stringifies;
                if (!stringifies.isEmpty() && stringifies.getFirst() == sb.virtualSB.init) {
                    appends.addAll(stringifies.listIterator(1));
                } else {
                    appends.addAll(stringifies);
                }
            });
            outlinedSBMethodSupport.virtualizedInits.add(inits.size());
            outlinedSBMethodSupport.virtualizedAppends.add(appends.size());
            outlinedSBMethodSupport.virtualizedToStrings.add(toStrings);
            outlinedSBMethodSupport.virtualizeCalls.add(toStrings + inits.size() + appends.size());
        }

        // computing total number of each type of call in method
        for (Invoke node : graph.getInvokes()) {
            switch (getSBOpKind(node.asNode())) {
                case InitZeroArguments:
                case InitWithObject:
                case InitWithCapacity:
                    outlinedSBMethodSupport.sbInits.inc();
                    outlinedSBMethodSupport.sbCalls.inc();
                    break;
                case Append:
                    outlinedSBMethodSupport.sbAppends.inc();
                    outlinedSBMethodSupport.sbCalls.inc();
                    break;
                case Unhandled:
                    outlinedSBMethodSupport.sbUnhandledAppends.inc();
                    outlinedSBMethodSupport.sbCalls.inc();
                    break;
                case ToString:
                    outlinedSBMethodSupport.sbToStrings.inc();
                    outlinedSBMethodSupport.sbCalls.inc();
                    break;
                default:
                    break;
            }
        }
    }

    private void calculateSBAllocations() {
        SBAllocationAssigner assigner = new SBAllocationAssigner(graph);
        assigner.apply();
        assigner.calculateMetadata();
    }

    private void performMaterializationAnalysis() {
        SBMaterializer materializer = new SBMaterializer(graph);
        materializer.apply();
        materializer.calculateMetadata();
    }

    SBAllocationMaterializationInfo calculateMaterializations(SBAllocation sb) {
        SBMaterializationPlacer placer = new SBMaterializationPlacer(sb);
        SBAllocationMaterializationInfo info = placer.placeMaterializations();
        updateCounters(info);
        return info;
    }

    private static <K, V> void computeHelper(EconomicMap<K, V> map, K key, BiFunction<K, V, V> mappingFunction) {
        V value = map.get(key);
        V newValue = mappingFunction.apply(key, value);
        if (newValue != value) {
            map.put(key, newValue);
        }
    }

    public void updateCounters(SBAllocationMaterializationInfo info) {
        if (!SBOutliningFeature.Options.PrintSBOutliningCounters.getValue()) {
            return;
        }

        long toStrings = info.concreteSBs.stream().filter(sb -> sb instanceof MaterializedToStringSB).count();
        List<MaterializedInstanceSB> instances = info.concreteSBs.stream().filter(sb -> sb instanceof MaterializedInstanceSB).map(sb -> (MaterializedInstanceSB) sb).toList();
        long emptyMaterializations = info.concreteSBs.stream().filter(sb -> sb instanceof MaterializedInstanceSB && sb.virtualSB.stringifies.isEmpty()).count();
        boolean allEmptyMaterializations = info.concreteSBs.stream().allMatch(sb -> sb instanceof MaterializedInstanceSB && sb.virtualSB.stringifies.isEmpty());

        outlinedSBMethodSupport.totalMaterializations.add(info.concreteSBs.size());
        outlinedSBMethodSupport.toStringMaterializations.add(toStrings);
        outlinedSBMethodSupport.instanceMaterializations.add(instances.size());
        outlinedSBMethodSupport.emptyMaterializations.add(emptyMaterializations);
        if (allEmptyMaterializations) {
            outlinedSBMethodSupport.allEmptyMaterializations.inc();
        }

        EconomicMap<MaterializedInstanceSB, Integer> matchingMaterializationCount = EconomicMap.create(Equivalence.DEFAULT);
        for (MaterializedConcreteSB sb : info.concreteSBs) {
            MaterializedInstanceSB proxy = new MaterializedInstanceSB(sb.virtualSB, null);
            computeHelper(matchingMaterializationCount, proxy, (_, v) -> v == null ? 1 : v + 1);
        }

        var cursor = matchingMaterializationCount.getEntries();
        while (cursor.advance()) {
            int value = cursor.getValue();
            if (value > 1) {
                outlinedSBMethodSupport.redundantVirtualState.add(value - 1);
            }
        }

        long numDominators = 0;
        for (MaterializedConcreteSB sb : info.concreteSBs) {
            for (MaterializedConcreteSB other : info.concreteSBs) {
                if (sb == other) {
                    continue;
                }
                if (AbstractControlFlowGraph.dominates(other.getMaterializationBlock(cfg), sb.getMaterializationBlock(cfg))) {
                    numDominators++;
                    break;
                }
            }
        }
        outlinedSBMethodSupport.redundantDominators.add(numDominators);
    }

    static List<ValueNode> virtualizedNodesFor(List<SBAllocation> sbs) {
        List<ValueNode> virtualizedNodes = new ArrayList<>();
        for (SBAllocation sb : sbs) {
            virtualizedNodes.addAll(sb.virtualizedNodes);
        }

        return virtualizedNodes;
    }

    private static boolean initHasStringify(InvokeWithExceptionNode node) {
        Signature signature = node.getTargetMethod().getSignature();
        return signature.getParameterCount(false) == 1 && signature.getParameterKind(0).isObject();
    }

    /**
     * Classifies each invocation based on its relevant SB action.
     */
    enum SBOp {
        None(false, false),
        Unhandled(true, false),
        // StringBuffer()
        // StringBuilder()
        InitZeroArguments(true, true),
        // StringBuffer(String)
        // StringBuffer(CharSequence)
        // StringBuilder(String)
        // StringBuilder(CharSequence)
        InitWithObject(true, true),
        // StringBuffer(int)
        // StringBuilder(int)
        InitWithCapacity(true, true),
        Append(true, true),
        ToString(true, false);

        final boolean hasSBReceiver;
        final boolean isSBProducer;

        SBOp(boolean hasSBReceiver, boolean isSBProducer) {
            this.hasSBReceiver = hasSBReceiver;
            this.isSBProducer = isSBProducer;
        }
    }

    public SBOp getSBOpKind(Node node) {
        if (node instanceof InvokeWithExceptionNode) {
            ResolvedJavaMethod callTarget = ((InvokeWithExceptionNode) node).getTargetMethod();
            if (callTarget != null) {
                return sbOpKinds.get(callTarget, SBOp.None);
            }
        }
        return SBOp.None;
    }

    /**
     * Canonical representation for a single SB allocation site. These objects are created during
     * {@link SBAllocationAssigner} and then its fields are populated during {@link SBMaterializer}
     */
    public static class SBAllocation {

        /**
         * Nodes represent this allocation in the graph.
         */
        EconomicSet<ValueNode> aliases = EconomicSet.create(Equivalence.DEFAULT);

        /**
         * Method calls (& allocation) which are virtualized.
         */
        List<ValueNode> virtualizedNodes = null;

        /**
         * Materialized instances of this alloc.
         */
        EconomicSet<MaterializedSB> usedMaterializations = null;

        /**
         * {@link MaterializedConcreteSB materialized concrete instances} of this alloc.
         */
        EconomicSet<MaterializedConcreteSB> usedConcreteMaterializations = null;

        /**
         * Original allocation.
         */
        final ValueNode allocNode;

        /**
         * Original type. Will be either of type StringBuilder or StringBuffer.
         */
        final ResolvedJavaType type;

        /**
         * An id is assigned to all {@link SBAllocation}s which we try to virtualize. We do not
         * initially assign an id because we only assign ids to {@link SBAllocation}s we try to
         * optimize (i.e., are not unhandled {@link SBAllocation}s due to unhandled escapes).
         */
        int id = -1;

        SBAllocation(ValueNode allocNode, ResolvedJavaType type) {
            assert allocNode instanceof NewInstanceNode || allocNode instanceof AllocatedObjectNode;
            this.allocNode = allocNode;
            this.type = type;
        }
    }

    private SBAllocation getSBAllocation(Node node) {
        assert aliasToSBAllocation.containsKey(node);
        return aliasToSBAllocation.get(node);
    }

    void registerMaterializedUse(SBAllocation sb, MaterializedSB materializedSB, ValueNode node) {
        sb.usedMaterializations.add(materializedSB);

        computeHelper(materializedSBToUses, materializedSB, (_, v) -> {
            List<Node> uses = v != null ? v : new ArrayList<>();
            uses.add(node);
            return uses;
        });
    }

    private boolean isHandledAlloc(ResolvedJavaType type) {
        return handledSBTypes.contains(type);
    }

    static boolean allowedEscapingUse(Node underlyingUse) {
        return underlyingUse instanceof IsNullNode ||
                        underlyingUse instanceof FrameState ||
                        underlyingUse instanceof MaterializedObjectState ||
                        underlyingUse instanceof VirtualObjectState;
    }

    /**
     * This pass walks through the graph and tries to map each SB operation to its corresponding
     * {@link SBAllocation} representation.
     *
     * During this pass we also determine all uses of the {@link SBAllocation}s so that we can:
     * <ul>
     * <li>Find escaping uses of each {@link SBAllocation}.</li>
     * <li>Find {@link SBAllocation}s with unhandled escaping uses. These {@link SBAllocation}s are
     * not analyzed or optimized.</li>
     * </ul>
     */
    class SBAllocationAssigner extends StatelessPostOrderNodeIterator {
        EconomicSet<SBAllocation> seenSBAllocations = EconomicSet.create(Equivalence.DEFAULT);
        EconomicSet<SBAllocation> unhandledSBAllocations = EconomicSet.create(Equivalence.DEFAULT);
        EconomicMap<SBAllocation, EconomicSet<FixedNode>> sbAllocationToEscapingUses = EconomicMap.create(Equivalence.DEFAULT);

        SBAllocationAssigner(StructuredGraph graph) {
            super(graph.start());
        }

        private void tryRegisterSBAlloc(ResolvedJavaType type, ValueNode allocationNode) {
            if (isHandledAlloc(type)) {
                assert !aliasToSBAllocation.containsKey(allocationNode) : "two string builders/buffers for same key";

                SBAllocation sb = new SBAllocation(allocationNode, type);
                aliasToSBAllocation.put(allocationNode, sb);

                seenSBAllocations.add(sb);
                sbAllocationToEscapingUses.put(sb, EconomicSet.create(Equivalence.DEFAULT));

                findEscapingUses(sb, allocationNode);
            }
        }

        private SBAllocation findSBAllocation(ValueNode node) {
            ValueNode sbOrigin = node;
            List<ValueNode> unmappedNodes = new ArrayList<>();
            while (!aliasToSBAllocation.containsKey(sbOrigin)) {
                unmappedNodes.add(sbOrigin);
                if (getSBOpKind(sbOrigin).hasSBReceiver) {
                    sbOrigin = ((Invoke) node).getReceiver();
                } else if (sbOrigin instanceof PiNode) {
                    sbOrigin = ((PiNode) sbOrigin).getOriginalNode();
                } else {
                    break;
                }
            }

            SBAllocation sb = aliasToSBAllocation.get(sbOrigin);

            // saving mapping to all walked nodes
            unmappedNodes.forEach(n -> {
                assert !aliasToSBAllocation.containsKey(n);
                aliasToSBAllocation.put(n, sb);
            });

            return sb;
        }

        private boolean nodeUseMightEscape(Node underlyingUse, SBAllocation sb) {
            final NodeInputList<ValueNode> args;
            final ValueNode arg;
            final SBOp op = getSBOpKind(underlyingUse);

            switch (op) {
                case None:
                case Unhandled:
                    return true;

                case ToString:
                    return false; // ToStrings do not cause an escape.

                case InitZeroArguments:
                    assert ((InvokeWithExceptionNode) underlyingUse).callTarget().arguments().count() == 1;
                    return false;

                case InitWithCapacity:
                    args = ((InvokeWithExceptionNode) underlyingUse).callTarget().arguments();
                    assert args.count() == 2;
                    assert !args.get(1).stamp(NodeView.DEFAULT).isPointerStamp();
                    return false;

                case InitWithObject:
                    // Need to make sure this isn't passed in an escaping way to an init.
                    args = ((InvokeWithExceptionNode) underlyingUse).callTarget().arguments();
                    assert args.count() == 2;
                    arg = args.get(1);
                    assert arg.stamp(NodeView.DEFAULT).isPointerStamp();
                    return sb.aliases.contains(arg);

                case Append:
                    // Need to make sure this isn't passed in an escaping way to an append.
                    args = ((InvokeWithExceptionNode) underlyingUse).callTarget().arguments();
                    assert args.count() == 2;
                    arg = args.get(1);
                    return arg.stamp(NodeView.DEFAULT).isPointerStamp() && sb.aliases.contains(arg);

                default:
                    throw VMError.shouldNotReachHereUnexpectedInput(op);
            }
        }

        private void findEscapingUsesHelper(SBAllocation sb, ValueNode sbAlias, EconomicSet<FixedNode> escapingUsages) {
            NodeIterable<Node> usages = sbAlias.usages();
            if (usages.isNotEmpty()) {
                sb.aliases.add(sbAlias);
            }

            for (Node use : usages) {
                // We associated uses of CallTargetNodes with their underlying invoke
                Node underlyingUse = use instanceof CallTargetNode ? ((CallTargetNode) use).invoke().asFixedNode() : use;

                boolean allowedUse = allowedEscapingUse(underlyingUse);

                // use may be problematic
                if (!allowedUse) {
                    if (underlyingUse instanceof FixedNode) {
                        if (underlyingUse instanceof Invoke) {
                            if (!nodeUseMightEscape(underlyingUse, sb)) {
                                continue;
                            }

                            // linking appropriate escaping use
                            CallTargetNode callTarget = ((Invoke) underlyingUse).callTarget();
                            logEvent("found escaping call use: %s (%s), sb: %s", callTarget, callTarget.targetMethod(), sb);
                        } else {
                            logEvent("found escaping FixedNode use: %s, sb: %s", underlyingUse, sb);
                        }
                        escapingUsages.add((FixedNode) underlyingUse);
                    } else {
                        if (underlyingUse instanceof PiNode) {
                            // need to check the uses of the PiNode to make sure they are valid
                            findEscapingUsesHelper(sb, (PiNode) underlyingUse, escapingUsages);
                        } else if (underlyingUse instanceof ValueProxyNode) {
                            // accesses across loops are invalidated
                            unhandledSBAllocations.add(sb);
                            logEvent("unhandledAlloc: found ValueProxyNode %s sb: %s", underlyingUse, sb);
                        } else if (underlyingUse instanceof ValuePhiNode) {
                            /*
                             * ValuePhiNodes are disallowed because their edges may refer to
                             * different sbAllocations.
                             */
                            unhandledSBAllocations.add(sb);
                            logEvent("unhandledAlloc: found ValuePhiNode %s sb: %s", underlyingUse, sb);
                        } else {
                            // can't handle this escaping use type - need to give up
                            unhandledSBAllocations.add(sb);
                            logEvent("unhandledAlloc: found unhandled node %s sb: %s", underlyingUse, sb);
                        }
                    }
                }
            }
        }

        private void findEscapingUses(SBAllocation sb, ValueNode sbAlias) {
            EconomicSet<FixedNode> escapingUsages = EconomicSet.create(Equivalence.DEFAULT);
            findEscapingUsesHelper(sb, sbAlias, escapingUsages);
            sbAllocationToEscapingUses.get(sb).addAll(escapingUsages);
        }

        @Override
        protected void node(FixedNode node) {
            if (node instanceof CommitAllocationNode commitAllocation) {
                for (AllocatedObjectNode allocatedObjectNode : commitAllocation.usages().filter(AllocatedObjectNode.class)) {
                    VirtualObjectNode virtualObject = allocatedObjectNode.getVirtualObject();
                    tryRegisterSBAlloc(virtualObject.type(), allocatedObjectNode);
                }
            } else if (node instanceof NewInstanceNode) {
                tryRegisterSBAlloc(((NewInstanceNode) node).instanceClass(), node);
            } else if (getSBOpKind(node).hasSBReceiver) {
                // each node is only iterated once
                assert !aliasToSBAllocation.containsKey(node);

                SBAllocation sb = findSBAllocation(node);
                if (sb != null) {
                    if (getSBOpKind(node).isSBProducer) {
                        findEscapingUses(sb, node);
                    }
                }
            }
        }

        public void calculateMetadata() {
            outlinedSBMethodSupport.totalSBs.add(seenSBAllocations.size());
            outlinedSBMethodSupport.unhandledSBs.add(unhandledSBAllocations.size());

            // remove metadata for unhandledSBAllocations
            for (SBAllocation sb : unhandledSBAllocations) {
                assert seenSBAllocations.contains(sb);
                seenSBAllocations.remove(sb);
            }

            outlinedSBMethodSupport.candidateSBs.add(seenSBAllocations.size());

            // assign ids for remaining sbAllocations
            sbAllocations = new ArrayList<>(seenSBAllocations.size());
            int id = 0;
            for (SBAllocation sb : seenSBAllocations) {
                sb.id = id++;
                sbAllocations.add(sb);
                sb.virtualizedNodes = new ArrayList<>();
                sb.usedMaterializations = EconomicSet.create(Equivalence.DEFAULT);

                // process escape info for sb
                assert sbAllocationToEscapingUses.containsKey(sb);
                for (FixedNode node : sbAllocationToEscapingUses.get(sb)) {
                    computeHelper(escapingSBsAtNode, node, (_, v) -> {
                        List<SBAllocation> value = v != null ? v : new ArrayList<>();
                        value.add(sb);
                        return value;
                    });
                }
            }
        }
    }

    /**
     * Represents the state of a given {@link SBAllocation} while walking the graph during
     * {@link SBMaterializer}.
     *
     * Note: all implementations are treated as immutable objects/records so that cloning of a
     * {@link SBMaterializerState} does not cause problems.
     */
    interface AbstractSB {

        AbstractSB trySetInit(InvokeWithExceptionNode newInit, boolean forceMaterialization, Function<InvokeWithExceptionNode, Integer> slotSizeCalculator);

        AbstractSB tryAddAppend(InvokeWithExceptionNode append, boolean forceMaterialization, Function<InvokeWithExceptionNode, Integer> slotSizeCalculator);

        MaterializedSB ensureMaterializedAt(InvokeWithExceptionNode materializationPoint);
    }

    /**
     * Represents an {@link SBAllocation} which has not needed to be virtualized yet.
     */
    static class VirtualSB implements AbstractSB {

        final ValueNode alloc;
        final InvokeWithExceptionNode init;

        final List<InvokeWithExceptionNode> stringifies;

        final int slotsUsed;

        final InvokeWithExceptionNode lastAction;

        VirtualSB(ValueNode alloc, InvokeWithExceptionNode init, List<InvokeWithExceptionNode> stringifies, int slotsUsed, InvokeWithExceptionNode lastAction) {
            assert alloc != null;

            this.alloc = alloc;
            this.init = init;
            this.stringifies = stringifies;
            this.lastAction = lastAction;
            this.slotsUsed = slotsUsed;
        }

        static AbstractSB createWithAlloc(ValueNode newAlloc) {
            return new VirtualSB(newAlloc, null, List.of(), 0, null);
        }

        @Override
        public AbstractSB trySetInit(InvokeWithExceptionNode newInit, boolean forceMaterialization, Function<InvokeWithExceptionNode, Integer> slotSizeCalculator) {
            if (init != null) {
                return ensureMaterializedAt(newInit);
            }

            if (forceMaterialization) {
                OutlinedSBMethodSupport.singleton().forcedMaterializations.inc();
                return ensureMaterializedAt(newInit);
            }

            List<InvokeWithExceptionNode> newStringifies = stringifies;
            int newSlotsUsed = slotsUsed;
            if (initHasStringify(newInit)) {
                newStringifies = List.of(newInit);
                newSlotsUsed = slotSizeCalculator.apply(newInit);
            }

            return new VirtualSB(alloc, newInit, newStringifies, newSlotsUsed, newInit);
        }

        @Override
        public AbstractSB tryAddAppend(InvokeWithExceptionNode append, boolean forceMaterialization, Function<InvokeWithExceptionNode, Integer> slotSizeCalculator) {
            if (init == null) {
                return ensureMaterializedAt(append);
            }

            if (forceMaterialization) {
                OutlinedSBMethodSupport.singleton().forcedMaterializations.inc();
                return ensureMaterializedAt(append);
            }

            int newSlotsUsed = slotsUsed + slotSizeCalculator.apply(append);
            if (newSlotsUsed > MAX_STRINGIFY_SLOTS) {
                OutlinedSBMethodSupport.singleton().forcedMaterializations.inc();
                return ensureMaterializedAt(append);
            }

            ArrayList<InvokeWithExceptionNode> newAppends = new ArrayList<>(stringifies);
            newAppends.add(append);
            return new VirtualSB(alloc, init, newAppends, newSlotsUsed, append);
        }

        @Override
        public MaterializedSB ensureMaterializedAt(InvokeWithExceptionNode materializationPoint) {
            assert materializationPoint != null || init == null;
            return new MaterializedInstanceSB(this, materializationPoint);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            VirtualSB virtualSB = (VirtualSB) o;
            return alloc.equals(virtualSB.alloc) && Objects.equals(init, virtualSB.init) && stringifies.equals(virtualSB.stringifies) && Objects.equals(lastAction, virtualSB.lastAction);
        }

        @Override
        public int hashCode() {
            return Objects.hash(alloc, init, stringifies, lastAction);
        }
    }

    /**
     * An SB which represented a materialized state.
     */
    interface MaterializedSB extends AbstractSB {

        @Override
        default AbstractSB trySetInit(InvokeWithExceptionNode newInit, boolean forceMaterialization, Function<InvokeWithExceptionNode, Integer> slotSizeCalculator) {
            return this;
        }

        @Override
        default AbstractSB tryAddAppend(InvokeWithExceptionNode append, boolean forceMaterialization, Function<InvokeWithExceptionNode, Integer> slotSizeCalculator) {
            return this;
        }

        @Override
        default MaterializedSB ensureMaterializedAt(InvokeWithExceptionNode materializationPoint) {
            return this;
        }
    }

    /**
     * A Materialized SB which is needed for control flow purposes.
     */
    interface MaterializedControlFlowSB extends MaterializedSB {

    }

    static class MaterializedPhiSB implements MaterializedControlFlowSB {
        final AbstractMergeNode merge;
        final List<MaterializedSB> materializedEdges;

        MaterializedPhiSB(AbstractMergeNode merge, List<MaterializedSB> materializedEdges) {
            this.merge = merge;
            this.materializedEdges = materializedEdges;
            assert materializedEdges.stream().noneMatch(sb -> sb instanceof MaterializedToStringSB);
        }

        public FixedNode getMaterializationPoint() {
            return merge;
        }
    }

    static class MaterializedValueProxySB implements MaterializedControlFlowSB {
        final LoopExitNode loopExit;
        final MaterializedSB materializedSB;

        MaterializedValueProxySB(LoopExitNode loopExit, MaterializedSB materializedSB) {
            this.loopExit = loopExit;
            this.materializedSB = materializedSB;
        }
    }

    abstract static class MaterializedConcreteSB implements MaterializedSB {
        final VirtualSB virtualSB;

        final InvokeWithExceptionNode materializationInsertionPoint;

        MaterializedConcreteSB(VirtualSB virtualSB, InvokeWithExceptionNode materializationInsertionPoint) {
            this.virtualSB = virtualSB;
            this.materializationInsertionPoint = materializationInsertionPoint;
        }

        public InvokeWithExceptionNode getMaterializationInsertionPoint() {
            return materializationInsertionPoint;
        }

        /**
         * Returns the block of the normal successor of the {@link #materializationInsertionPoint},
         * representing the point after the materialization has been successfully executed without
         * throwing an exception. For example, consider a graph with the following structure:
         *
         * <pre>
         *       [0 Invoke!#toString] <-- materializationInsertionPoint
         *         /            \
         *        / normal       \ exception
         *       /                \
         * [1 next()]        [2 exceptionEdge()]
         *                          |
         *                   [3 Invoke!#toString]
         * </pre>
         *
         * In this graph, the return value of node 0 is only available in the dominated nodes of
         * node 1. Therefore, we cannot merge the materializations of node 0 and node 3.
         */
        public HIRBlock getMaterializationBlock(ControlFlowGraph cfg) {
            return normalSuccessorBlock(materializationInsertionPoint, cfg);
        }

        protected abstract MaterializedConcreteSB duplicateWithHoistedMaterializationPoint(InvokeWithExceptionNode newPoint);

        /**
         * Determines whether this sb would materialize to an equivalent String(Builder|Buffer) as
         * another sb. This does not consider the materialization point, as this does not affect the
         * materialized object.
         */
        public boolean matchingVirtualState(MaterializedConcreteSB other) {
            if (this == other) {
                return true;
            }
            return Objects.equals(virtualSB, other.virtualSB);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MaterializedConcreteSB that = (MaterializedConcreteSB) o;
            return Objects.equals(virtualSB, that.virtualSB) && Objects.equals(materializationInsertionPoint, that.materializationInsertionPoint);
        }

        @Override
        public int hashCode() {
            return Objects.hash(virtualSB, materializationInsertionPoint);
        }
    }

    /**
     * Represents a materialization to a StringBuilder or StringBuffer (not a
     * {@link MaterializedToStringSB}).
     */
    static final class MaterializedInstanceSB extends MaterializedConcreteSB {
        private static final MaterializedInstanceSB EMPTY = new MaterializedInstanceSB(null, null);

        private MaterializedInstanceSB(VirtualSB virtualSB, InvokeWithExceptionNode materializationPoint) {
            super(virtualSB, materializationPoint);
        }

        @Override
        protected MaterializedInstanceSB duplicateWithHoistedMaterializationPoint(InvokeWithExceptionNode newPoint) {
            return new MaterializedInstanceSB(virtualSB, newPoint);
        }
    }

    static final class MaterializedToStringSB extends MaterializedConcreteSB {
        InvokeWithExceptionNode toString;

        private MaterializedToStringSB(VirtualSB virtualSB, InvokeWithExceptionNode materializationPt,
                        InvokeWithExceptionNode toString) {
            super(virtualSB, materializationPt);
            this.toString = toString;
        }

        private static MaterializedToStringSB fromVirtualSB(VirtualSB sb, InvokeWithExceptionNode toString) {
            return new MaterializedToStringSB(sb, toString, toString);
        }

        @Override
        protected MaterializedToStringSB duplicateWithHoistedMaterializationPoint(InvokeWithExceptionNode newPoint) {
            return new MaterializedToStringSB(virtualSB, newPoint, toString);
        }

    }

    /**
     * This class keeps track of the current state of all {@link SBAllocation}s for a given point in
     * the program.
     */
    private class SBMaterializerState extends MergeableState<SBMaterializerState> implements Cloneable {

        /**
         * Tracks the current state associated with each {@link SBAllocation} in
         * {@link #sbAllocations}.
         */
        final AbstractSB[] sbState;

        /**
         * State before last split at an {@link InvokeWithExceptionNode}. This is necessary for
         * going down the exception path where the invoke will not have executed yet. Note that we
         * do not need to keep track of all {@link WithExceptionNode}s since the state will only
         * change at invokes.
         */
        final AbstractSB[] sbStateBeforeInvoke;

        /**
         * Last invoke visited. This is where a materialization will occur.
         */
        InvokeWithExceptionNode[] lastInvoke;
        /**
         * Also keep track of the 2nd to last invoke for going down exception control paths.
         */
        InvokeWithExceptionNode[] lastLastInvoke;

        /**
         * Record for keeping track of the phis present at the loop begin. Note that this is not
         * copied at clones, but instead is shared across all instances of a given pass.
         */
        private final EconomicMap<LoopBeginNode, AbstractSB[]> loopBeginState;

        SBMaterializerState() {
            sbState = new AbstractSB[sbAllocations.size()];
            Arrays.fill(sbState, MaterializedInstanceSB.EMPTY);

            sbStateBeforeInvoke = new AbstractSB[sbAllocations.size()];
            Arrays.fill(sbStateBeforeInvoke, MaterializedInstanceSB.EMPTY);

            lastInvoke = new InvokeWithExceptionNode[sbAllocations.size()];
            lastLastInvoke = new InvokeWithExceptionNode[sbAllocations.size()];

            loopBeginState = EconomicMap.create(Equivalence.DEFAULT);
        }

        SBMaterializerState(AbstractSB[] sbState, AbstractSB[] sbStateBeforeInvoke, InvokeWithExceptionNode[] lastInvoke, InvokeWithExceptionNode[] lastLastInvoke,
                        EconomicMap<LoopBeginNode, AbstractSB[]> loopBeginState) {
            this.sbState = Arrays.copyOf(sbState, sbState.length);
            this.sbStateBeforeInvoke = Arrays.copyOf(sbStateBeforeInvoke, sbState.length);
            this.lastInvoke = Arrays.copyOf(lastInvoke, lastInvoke.length);
            this.lastLastInvoke = Arrays.copyOf(lastLastInvoke, lastLastInvoke.length);
            this.loopBeginState = loopBeginState;
        }

        @Override
        public SBMaterializerState clone() {
            return new SBMaterializerState(sbState, sbStateBeforeInvoke, lastInvoke, lastLastInvoke, loopBeginState);
        }

        /**
         * Wrapper for {@link #doActionAndRecord(SBAllocation, ValueNode, Function)} which
         * determines the {@link SBAllocation}.
         */
        void doActionAndRecord(ValueNode node, Function<AbstractSB, AbstractSB> func) {
            SBAllocation sb = getSBAllocation(node);
            if (sb != null && sb.id != -1) {
                doActionAndRecord(sb, node, func);
            }
        }

        /**
         * @return true if the new state for sbState is a virtual (a {@link VirtualSB}).
         */
        boolean doActionAndRecord(SBAllocation sb, ValueNode node, Function<AbstractSB, AbstractSB> func) {
            AbstractSB oldState = sbState[sb.id];
            AbstractSB newState = func.apply(oldState);
            sbState[sb.id] = newState;

            if (newState instanceof MaterializedSB) {
                registerMaterializedUse(sb, node);
                return false;

            } else {
                sb.virtualizedNodes.add(node);
                return true;
            }
        }

        void registerMaterializedUse(SBAllocation sb, ValueNode node) {
            // need to register this as a use of the materialized value
            MaterializedSB materializedSB = (MaterializedSB) sbState[sb.id];
            SBOutliningAnalysis.this.registerMaterializedUse(sb, materializedSB, node);
        }

        void alloc(ValueNode alloc) {
            doActionAndRecord(alloc, _ -> VirtualSB.createWithAlloc(alloc));
        }

        /**
         * Some of the SB operations are only conditionally virtualizable. This method checks
         * whether a materialization must be forced for this operation.
         */
        boolean forceMaterialization(InvokeWithExceptionNode node) {
            /*
             * Some init & append methods could be virtualized, but currently it is more
             * efficient to perform them on the materialized value; in our observations so far
             * these patterns happen infrequently, so it is not worthwhile to optimize this
             * virtualized shape.
             */
            return forcedMaterializationCandidates.contains(node.getTargetMethod());
        }

        /**
         * Note both appends and inits which are stringified will have the stringify operand in
         * index 1.
         */
        private static ValueNode getStringifyOperand(InvokeWithExceptionNode invoke) {
            return invoke.callTarget().arguments().get(1);
        }

        private int calculateSlotsUsed(InvokeWithExceptionNode originalCall) {
            ValueNode appendOperand = getStringifyOperand(originalCall);
            JavaKind stackKind = appendOperand.getStackKind();
            if (stackKind.isPrimitive()) {
                return stackKind.getSlotCount();
            } else if (appendOperand.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp objectStamp && objectStamp.nonNull()) {
                /*
                 * For non-null stamps we may insert an unbox. Thus, when the unbox type takes two
                 * slots (i.e., long & double), we must treat this append argument as using two
                 * slots.
                 */
                ResolvedJavaType type = objectStamp.type();
                if (type != null && twoSlotTypes.contains(type)) {
                    return 2;
                }
            }
            return 1;
        }

        void init(InvokeWithExceptionNode init) {
            doActionAndRecord(init, sb -> sb.trySetInit(init, forceMaterialization(init), this::calculateSlotsUsed));
        }

        void append(InvokeWithExceptionNode append) {
            doActionAndRecord(append, sb -> sb.tryAddAppend(append, forceMaterialization(append), this::calculateSlotsUsed));
        }

        void processToString(InvokeWithExceptionNode toString) {
            SBAllocation sb = getSBAllocation(toString);
            if (sb != null && sb.id != -1) {
                AbstractSB state = sbState[sb.id];
                if (state instanceof MaterializedSB) {
                    registerMaterializedUse(sb, toString);
                } else {
                    sb.usedMaterializations.add(MaterializedToStringSB.fromVirtualSB((VirtualSB) state, toString));
                    sb.virtualizedNodes.add(toString);
                }
            }
        }

        void materializeAll() {
            for (int i = 0; i < sbState.length; i++) {
                sbState[i] = sbState[i].ensureMaterializedAt(lastInvoke[i]);
            }
        }

        void materializeEscapes(FixedNode position) {
            if (escapingSBsAtNode.containsKey(position)) {
                for (SBAllocation sbAllocation : escapingSBsAtNode.get(position)) {
                    doActionAndRecord(sbAllocation, position, sb -> sb.ensureMaterializedAt(lastInvoke[sbAllocation.id]));
                }
            }
        }

        /**
         * Because we are inserting phi nodes at all LoopBeginNodes, ValueProxyNodes must be
         * inserted at all LoopExits since subsequent materialized uses of the sbState will be from
         * a value defined from within the loop (the phi).
         */
        void createValueProxies(LoopExitNode loopExit) {
            for (int i = 0; i < sbState.length; i++) {
                sbState[i] = new MaterializedValueProxySB(loopExit, sbState[i].ensureMaterializedAt(lastInvoke[i]));
            }
        }

        /**
         * We try to materialize at the last invoke. However, because at the materialization point
         * (an invoke) we are piggybacking on its exception edge, we also need to keep track of the
         * state at the prior invoke as well for when going down an exception path.
         */
        void recordInvokeWithException(InvokeWithExceptionNode invoke) {
            System.arraycopy(sbState, 0, sbStateBeforeInvoke, 0, sbState.length);

            System.arraycopy(lastInvoke, 0, lastLastInvoke, 0, lastInvoke.length);
            Arrays.fill(lastInvoke, invoke);
        }

        boolean isSBVirtual(InvokeWithExceptionNode invoke) {
            SBAllocation sb = getSBAllocation(invoke);
            if (sb != null && sb.id != -1) {
                return sbState[sb.id] instanceof VirtualSB;
            }

            return false;
        }

        /**
         * We insert a phi at all LoopBegins and also at all merges where the state is not
         * identical.
         */
        @Override
        public boolean merge(AbstractMergeNode merge, List<SBMaterializerState> withStates) {
            assert withStates.size() == merge.forwardEndCount() - 1;
            boolean isLoopBegin = merge instanceof LoopBeginNode;

            boolean[] matchingVirtualState;
            boolean[] matchingInvokes;
            if (isLoopBegin) {
                matchingVirtualState = null;
                matchingInvokes = null;
            } else {
                matchingVirtualState = new boolean[sbState.length];
                Arrays.fill(matchingVirtualState, true);
                matchingInvokes = new boolean[sbState.length];
                Arrays.fill(matchingInvokes, true);
                for (SBMaterializerState other : withStates) {
                    /*
                     * This check is necessary to ensure that if a materialization happens, then the
                     * materialization point will be at a place which dominates all merged paths.
                     */
                    for (int i = 0; i < sbState.length; i++) {
                        matchingInvokes[i] = matchingInvokes[i] && lastInvoke[i] == other.lastInvoke[i];
                        AbstractSB otherState = other.sbState[i];
                        matchingVirtualState[i] = matchingVirtualState[i] && otherState instanceof VirtualSB && sbState[i].equals(otherState);
                    }
                }
            }
            for (SBAllocation sb : sbAllocations) {
                int id = sb.id;
                if (isLoopBegin || !matchingVirtualState[id]) {
                    if (SBOutliningFeature.Options.PrintSBOutliningCounters.getValue()) {
                        if (!isLoopBegin) {
                            boolean allVirtual = sbState[id] instanceof VirtualSB;
                            boolean allVirtualSameShape = allVirtual;
                            for (SBMaterializerState other : withStates) {
                                allVirtual = allVirtual && other.sbState[id] instanceof VirtualSB;
                                if (allVirtualSameShape && allVirtual) {
                                    VirtualSB virtualSB = (VirtualSB) sbState[id];
                                    VirtualSB otherVirtualSB = (VirtualSB) other.sbState[id];
                                    allVirtualSameShape = virtualSB.init == otherVirtualSB.init && virtualSB.stringifies.size() == otherVirtualSB.stringifies.size();
                                    if (allVirtualSameShape) {
                                        // compare stringifies
                                        for (int i = 0; i < virtualSB.stringifies.size(); i++) {
                                            allVirtualSameShape = allVirtualSameShape && virtualSB.stringifies.get(i).getTargetMethod().equals(otherVirtualSB.stringifies.get(i).getTargetMethod());
                                        }
                                    }
                                }
                            }
                            if (allVirtual) {
                                outlinedSBMethodSupport.mergeMaterializations.inc();
                            }
                            if (allVirtualSameShape) {
                                outlinedSBMethodSupport.mergeMaterializationsWithSameShape.inc();
                            }
                        }
                    }
                    /*
                     * We have to insert a phi. Ensure there is a materialization at all input
                     * edges.
                     */
                    List<MaterializedSB> forwardEndState = new ArrayList<>(merge.forwardEndCount());
                    forwardEndState.add(sbState[id].ensureMaterializedAt(lastInvoke[id]));
                    for (int i = 1; i < merge.forwardEndCount(); i++) {
                        SBMaterializerState other = withStates.get(i - 1);
                        forwardEndState.add(other.sbState[id].ensureMaterializedAt(other.lastInvoke[id]));
                    }

                    /*
                     * Note: for a phi linked to a LoopBegin, the input edges corresponding to
                     * LoopEnds are added to the phi later in the method loopEnds(LoopBeginNode
                     * loopBegin, List<SBMaterializerState> loopEndStates)
                     */
                    MaterializedPhiSB phi = new MaterializedPhiSB(merge, forwardEndState);
                    sbState[id] = phi;
                } else if (!matchingInvokes[id]) {
                    /*
                     * Although no virtualization is necessary, we have to rewind lastInvoke (and
                     * lastLastInvoke) to the last sb operation since we do not know another invoke
                     * which dominates all edges. It is possible to find another post-dominating
                     * invoke via maintaining a list, but that is overkill.
                     */
                    VirtualSB virtualSB = ((VirtualSB) sbState[id]);
                    InvokeWithExceptionNode newLastInvoke = virtualSB.lastAction;
                    lastInvoke[id] = newLastInvoke;
                    lastLastInvoke[id] = newLastInvoke;
                }
            }

            return true;
        }

        /**
         * At the loopBegins we need to capture the MaterializedPhiSBs so that we can append the
         * {@link MaterializedPhiSB#materializedEdges} corresponding to loopEnds once they have been
         * reached.
         */
        @Override
        public void loopBegin(LoopBeginNode loopBegin) {
            AbstractSB[] snapshot = Arrays.copyOf(sbState, sbState.length);
            boolean allPhis = Arrays.stream(snapshot).allMatch(sb -> {
                if (sb instanceof MaterializedPhiSB) {
                    return ((MaterializedPhiSB) sb).getMaterializationPoint() == loopBegin;
                }
                return false;
            });
            assert allPhis : "loop state should be all MaterializedPhiNodes";

            loopBeginState.put(loopBegin, snapshot);
        }

        /**
         * Adds to phi nodes materialized values for input edges corresponding to
         * {@link LoopEndNode}s.
         */
        @Override
        public void loopEnds(LoopBeginNode loopBegin, List<SBMaterializerState> loopEndStates) {
            AbstractSB[] snapshot = loopBeginState.get(loopBegin);
            for (SBMaterializerState endState : loopEndStates) {
                boolean allMaterialized = Arrays.stream(endState.sbState).allMatch(sb -> sb instanceof MaterializedSB);
                VMError.guarantee(allMaterialized, "everything should be materialized");

                for (int i = 0; i < snapshot.length; i++) {
                    MaterializedPhiSB phi = (MaterializedPhiSB) snapshot[i];
                    phi.materializedEdges.add((MaterializedSB) endState.sbState[i]);
                }
            }

            loopBeginState.removeKey(loopBegin);
        }

        /**
         * Handles state rollback for the exception edges of InvokeWithExceptionNodes.
         */
        @Override
        public void afterSplit(AbstractBeginNode node) {
            Node pred = node.predecessor();
            if (pred instanceof InvokeWithExceptionNode && ((InvokeWithExceptionNode) pred).exceptionEdge() == node) {
                /*
                 * Because this point is not after the last invoke (during the call an exception was
                 * throw) has completed, we need to use the state from before the invoke in our
                 * analysis.
                 *
                 * Further, since we are sharing the exception edge with lastInvoke, we have to
                 * reset the lastInvoke to its predecessor (lastLastInvoke) so that the
                 * materialization itself cannot be used on its own exception path.
                 */
                System.arraycopy(sbStateBeforeInvoke, 0, sbState, 0, sbStateBeforeInvoke.length);
                System.arraycopy(lastLastInvoke, 0, lastInvoke, 0, lastLastInvoke.length);
            }
        }
    }

    /**
     * This class tries to materialize SBs as late as possible and only when they are needed. The
     * goal is to keep the SBs virtual until they must be materialized due to an escaping use or
     * unhandled control flow. Because this is a simple pass, we do not handle loops and therefore
     * force the materialization of all SBs at both loop entrances and exits. Control flow merges
     * also frequently force materializations.
     *
     * After walking through the graph, this class also calculates all of the
     * {@link MaterializedConcreteSB}s needed.
     */
    private class SBMaterializer extends PostOrderNodeIterator<SBMaterializerState> {

        SBMaterializer(StructuredGraph graph) {
            super(graph.start(), new SBMaterializerState());
        }

        private void tryAlloc(ResolvedJavaType type, ValueNode allocationNode) {
            if (isHandledAlloc(type)) {
                state.alloc(allocationNode);
            }
        }

        @Override
        protected void node(FixedNode node) {
            if (getSBOpKind(node) == SBOp.Unhandled && state.isSBVirtual((InvokeWithExceptionNode) node)) {
                outlinedSBMethodSupport.unhandledAppendEscapingSBOps.inc();
            }
            state.materializeEscapes(node);

            if (node instanceof CommitAllocationNode commitAllocation) {
                for (AllocatedObjectNode allocatedObjectNode : commitAllocation.usages().filter(AllocatedObjectNode.class)) {
                    VirtualObjectNode virtualObject = allocatedObjectNode.getVirtualObject();
                    tryAlloc(virtualObject.type(), allocatedObjectNode);
                }
            } else if (node instanceof NewInstanceNode) {
                tryAlloc(((NewInstanceNode) node).instanceClass(), node);
            } else if (node instanceof LoopExitNode) {
                state.createValueProxies((LoopExitNode) node);
            } else {
                switch (getSBOpKind(node)) {
                    case InitZeroArguments:
                    case InitWithObject:
                    case InitWithCapacity:
                        state.init((InvokeWithExceptionNode) node);
                        break;
                    case Append:
                        state.append((InvokeWithExceptionNode) node);
                        break;
                    case ToString:
                        state.processToString((InvokeWithExceptionNode) node);
                        break;
                    case Unhandled:
                    case None:
                    default:
                        break;
                }
            }
        }

        @Override
        protected void end(EndNode endNode) {
            // handling at merges within SBMaterializerState, so don't need to do anything here
        }

        @Override
        protected void merge(AbstractMergeNode merge) {
            // handled directly within SBMaterializerState
        }

        @Override
        protected void loopBegin(LoopBeginNode loopBegin) {
            // handled directly within SBMaterializerState
        }

        @Override
        protected void loopEnd(LoopEndNode loopEnd) {
            // ensure no virtual state escapes loop
            state.materializeAll();
        }

        @Override
        protected Set<Node> controlSplit(ControlSplitNode controlSplit) {
            return super.controlSplit(controlSplit);
        }

        @Override
        protected void invokeWithException(InvokeWithExceptionNode invoke) {
            state.recordInvokeWithException(invoke);
            node(invoke);
        }

        /**
         * Finds all used concrete materializations. This requires searching through control flow
         * nodes (Phis and Proxies) to discover all materializations.
         */
        public void calculateMetadata() {
            Deque<MaterializedControlFlowSB> processList = new ArrayDeque<>();
            EconomicSet<MaterializedControlFlowSB> seenControlFlow = EconomicSet.create(Equivalence.DEFAULT);
            for (SBAllocation sb : sbAllocations) {
                // reset state
                seenControlFlow.clear();

                // store all of the concrete materializations
                EconomicSet<MaterializedConcreteSB> concreteMaterializations = EconomicSet.create(Equivalence.DEFAULT);
                sb.usedConcreteMaterializations = concreteMaterializations;

                for (MaterializedSB materializedSB : sb.usedMaterializations) {
                    if (materializedSB instanceof MaterializedConcreteSB) {
                        concreteMaterializations.add((MaterializedConcreteSB) materializedSB);
                    } else {
                        MaterializedControlFlowSB materializedControlFlow = (MaterializedControlFlowSB) materializedSB;

                        if (seenControlFlow.add(materializedControlFlow)) {
                            processList.push(materializedControlFlow);
                        }

                        while (!processList.isEmpty()) {
                            MaterializedControlFlowSB controlFlow = processList.pop();
                            if (controlFlow instanceof MaterializedPhiSB phi) {
                                for (AbstractSB edge : phi.materializedEdges) {
                                    if (edge instanceof MaterializedConcreteSB) {
                                        concreteMaterializations.add((MaterializedConcreteSB) edge);
                                    } else {
                                        MaterializedControlFlowSB edgeControlFlow = (MaterializedControlFlowSB) edge;
                                        if (!seenControlFlow.contains(edgeControlFlow)) {
                                            processList.push(edgeControlFlow);
                                            seenControlFlow.add(edgeControlFlow);
                                        }
                                    }
                                }
                            } else {
                                MaterializedValueProxySB valueProxy = (MaterializedValueProxySB) controlFlow;
                                MaterializedSB proxyInput = valueProxy.materializedSB;
                                if (proxyInput instanceof MaterializedConcreteSB) {
                                    concreteMaterializations.add((MaterializedConcreteSB) proxyInput);
                                } else {
                                    MaterializedControlFlowSB inputControlFlow = (MaterializedControlFlowSB) proxyInput;
                                    if (seenControlFlow.add(inputControlFlow)) {
                                        processList.push(inputControlFlow);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * @return a list of {@link SBAllocation}s which are valid to be optimized.
     */
    private List<SBAllocation> selectVirtualizedSBs() {
        List<SBAllocation> toOutline = new ArrayList<>();
        outer: for (SBAllocation sbAllocation : sbAllocations) {
            for (MaterializedConcreteSB sb : sbAllocation.usedConcreteMaterializations) {
                if (sb.virtualSB.init == null) {
                    // only want to materialize states with an init
                    logEvent("skipping sb - outlined instance has no init: %s", sbAllocation);
                    continue outer;
                }

                if ((/* GR-49250 */ ContinuationSupport.isSupported() || !SBOutliningFeature.Options.OutlineSBMaterializations.getValue()) && sb instanceof MaterializedInstanceSB) {
                    logEvent("skipping sb - outlined sb materializations are disabled: %s", sbAllocation);
                    continue outer;
                }
            }

            if (inFactoryMethod) {
                /*
                 * Materializing an init within a factory method merely creates an extra level of
                 * indirection.
                 */
                for (MaterializedConcreteSB sb : sbAllocation.usedConcreteMaterializations) {
                    if (sb instanceof MaterializedInstanceSB) {
                        if (sb.virtualSB.lastAction == sb.virtualSB.init) {
                            logEvent("skipping sb - has materialized inits in factory method: %s", sbAllocation);
                            continue outer;
                        }
                    }
                }
            }

            logEvent("accepted sb: %s", sbAllocation);
            toOutline.add(sbAllocation);
        }

        outlinedSBMethodSupport.acceptedSBs.add(toOutline.size());

        return toOutline;
    }

    /**
     * Record containing all information used by {@link SBOutliningPhase}.
     */
    static final class SBAllocationMaterializationInfo {
        private final SBAllocation sbAllocation;
        private final InvokeWithExceptionNode constructorToVerify;
        private final List<InvokeWithExceptionNode> stringifies;
        private final List<MaterializedConcreteSB> concreteSBs;
        private final EconomicMap<MaterializedConcreteSB, MaterializedConcreteSB> concreteSBRemappings;
        private final List<ValueNode> aliases;
        private final List<MaterializedSB> usedMaterializations;
        private final EconomicMap<MaterializedSB, List<Node>> materializedUses;

        private SBAllocationMaterializationInfo(SBAllocation sbAllocation,
                        InvokeWithExceptionNode constructorToVerify,
                        List<InvokeWithExceptionNode> stringifies,
                        List<MaterializedConcreteSB> concreteSBs,
                        EconomicMap<MaterializedConcreteSB, MaterializedConcreteSB> concreteSBRemappings,
                        List<ValueNode> aliases,
                        List<MaterializedSB> usedMaterializations,
                        EconomicMap<MaterializedSB, List<Node>> materializedUses) {
            this.sbAllocation = sbAllocation;
            this.constructorToVerify = constructorToVerify;
            this.stringifies = stringifies;
            this.concreteSBs = concreteSBs;
            this.concreteSBRemappings = concreteSBRemappings;
            this.aliases = aliases;
            this.usedMaterializations = usedMaterializations;
            this.materializedUses = materializedUses;
        }

        public SBAllocation getSBAllocation() {
            return sbAllocation;
        }

        public InvokeWithExceptionNode getConstructorToVerify() {
            return constructorToVerify;
        }

        public List<InvokeWithExceptionNode> getStringifies() {
            return stringifies;
        }

        public List<MaterializedConcreteSB> getConcreteSBs() {
            return concreteSBs;
        }

        MaterializedConcreteSB getRemappedConcreteSB(MaterializedConcreteSB sb) {
            return concreteSBRemappings.get(sb);
        }

        ResolvedJavaType getType() {
            return sbAllocation.type;
        }

        List<MaterializedSB> getUsedMaterializations() {
            return usedMaterializations;
        }

        List<Node> materializedUsesFor(MaterializedSB sb) {
            return materializedUses.get(sb);
        }

        List<ValueNode> getAliases() {
            return aliases;
        }
    }

    /**
     * This class determines the placement of SB materializations. Whereas {@link SBMaterializer}
     * tries to sink materializations as low as possible, here we try to place them at the earliest
     * place possible (without causing unneeded materialization). The benefit of performing the
     * materializations earlier is that it can reduce the number of discrete materializations within
     * the graph, reducing code size.
     */
    private class SBMaterializationPlacer {
        final SBAllocation sbAllocation;

        SBMaterializationPlacer(SBAllocation sbAllocation) {
            this.sbAllocation = sbAllocation;
        }

        private SBAllocationMaterializationInfo createMaterializationInfo(List<MaterializedConcreteSB> concreteSBs,
                        EconomicMap<MaterializedConcreteSB, MaterializedConcreteSB> concreteSBRemappings,
                        InvokeWithExceptionNode constructorToVerify, List<InvokeWithExceptionNode> stringifies) {
            List<ValueNode> aliases = List.of(sbAllocation.aliases.toArray(new ValueNode[sbAllocation.aliases.size()]));
            List<MaterializedSB> usedMaterializations = List.of(sbAllocation.usedMaterializations.toArray(new MaterializedSB[sbAllocation.usedMaterializations.size()]));

            return new SBAllocationMaterializationInfo(
                            sbAllocation, constructorToVerify, stringifies, concreteSBs,
                            concreteSBRemappings, aliases, usedMaterializations,
                            materializedSBToUses);
        }

        /**
         * Determine the new location of all materializations and stores all relevant information
         * within a {@link SBAllocationMaterializationInfo}.
         */
        private SBAllocationMaterializationInfo placeMaterializations() {
            EconomicSet<MaterializedConcreteSB> initialSBs = sbAllocation.usedConcreteMaterializations;

            EconomicMap<MaterializedConcreteSB, MaterializedConcreteSB> dominatorMap = calculateDominators(initialSBs);
            EconomicSet<MaterializedConcreteSB> dominatorSBs = EconomicSet.create(Equivalence.DEFAULT);
            dominatorMap.getValues().forEach(dominatorSBs::add);

            EconomicMap<MaterializedConcreteSB, InvokeWithExceptionNode> raisedPositions = calculateRaisedPositions(dominatorSBs);

            EconomicMap<MaterializedConcreteSB, MaterializedConcreteSB> sbRemappings = EconomicMap.create(Equivalence.DEFAULT, dominatorSBs.size());
            EconomicSet<MaterializedConcreteSB> optimizedSBs = EconomicSet.create(Equivalence.DEFAULT, dominatorSBs.size());

            for (MaterializedConcreteSB sb : dominatorSBs) {
                MaterializedConcreteSB newSB;
                InvokeWithExceptionNode newMaterializationPoint = raisedPositions.get(sb);
                if (newMaterializationPoint == sb.getMaterializationInsertionPoint()) {
                    // the materialization point hasn't changed
                    newSB = sb;
                } else {
                    newSB = sb.duplicateWithHoistedMaterializationPoint(newMaterializationPoint);
                }
                optimizedSBs.add(newSB);
                sbRemappings.put(sb, newSB);
            }

            // adding remappings for original nodes which have a different dominator
            for (MaterializedConcreteSB sb : initialSBs) {
                MaterializedConcreteSB dominator = dominatorMap.get(sb);
                assert dominator != null;
                assert (dominator == sb) == sbRemappings.containsKey(sb);
                if (dominator != sb) {
                    assert !sbRemappings.containsKey(sb);
                    MaterializedConcreteSB domSB = sbRemappings.get(dominator);
                    assert domSB != null;
                    sbRemappings.put(sb, domSB);
                }
            }

            // collect list of new optimized materialized sbs
            List<MaterializedConcreteSB> optimizedSBList = List.of(optimizedSBs.toArray(new MaterializedConcreteSB[optimizedSBs.size()]));

            List<InvokeWithExceptionNode> constructorsToVerify = sbAllocation.virtualizedNodes.stream()
                            .filter(this::isFallibleConstructor)
                            .map(n -> (InvokeWithExceptionNode) n)
                            .toList();
            assert constructorsToVerify.size() <= 1;
            InvokeWithExceptionNode constructorToVerify = constructorsToVerify.isEmpty() ? null : constructorsToVerify.getFirst();

            List<InvokeWithExceptionNode> stringifies = sbAllocation.virtualizedNodes.stream()
                            .filter(this::isStringify)
                            .map(n -> (InvokeWithExceptionNode) n)
                            .toList();

            return createMaterializationInfo(optimizedSBList, sbRemappings, constructorToVerify, stringifies);
        }

        /**
         * Determines if this is a call to a constructor that could fail and throw exceptions
         * depending on inputs.
         */
        private boolean isFallibleConstructor(Node node) {
            final SBOp opKind = getSBOpKind(node);
            return opKind == SBOp.InitWithObject || opKind == SBOp.InitWithCapacity;
        }

        private boolean isStringify(Node node) {
            final SBOp opKind = getSBOpKind(node);
            return opKind == SBOp.InitWithObject || opKind == SBOp.Append;
        }

        /**
         * Calculates the earliest dominator for each MaterializedConcreteSB with a matching virtual
         * state and equivalent exception handling.
         */
        public EconomicMap<MaterializedConcreteSB, MaterializedConcreteSB> calculateDominators(EconomicSet<MaterializedConcreteSB> initialConcreteSBs) {
            EconomicMap<MaterializedConcreteSB, MaterializedConcreteSB> dominators = EconomicMap.create(Equivalence.DEFAULT, initialConcreteSBs.size());

            // first initialize dominator to oneself
            for (MaterializedConcreteSB sb : initialConcreteSBs) {
                dominators.put(sb, sb);
            }

            List<MaterializedConcreteSB> instanceSBs = new ArrayList<>();
            List<MaterializedConcreteSB> toStringSBs = new ArrayList<>();
            for (MaterializedConcreteSB sb : initialConcreteSBs) {
                if (sb instanceof MaterializedInstanceSB) {
                    instanceSBs.add(sb);
                } else {
                    assert sb instanceof MaterializedToStringSB;
                    toStringSBs.add(sb);
                }
            }

            Consumer<List<MaterializedConcreteSB>> calcDominators = sbs -> {
                // find earlier dominators
                if (sbs.size() > 1) {
                    boolean changed;
                    do {
                        changed = false;
                        for (MaterializedConcreteSB a : sbs) {
                            MaterializedConcreteSB aDominator = dominators.get(a);
                            for (MaterializedConcreteSB b : sbs) {
                                MaterializedConcreteSB bDominator = dominators.get(b);
                                if (aDominator != bDominator && a.matchingVirtualState(b) &&
                                                matchingExceptionHandlers(aDominator.getMaterializationInsertionPoint(), bDominator.getMaterializationInsertionPoint())) {
                                    if (AbstractControlFlowGraph.dominates(aDominator.getMaterializationBlock(cfg), bDominator.getMaterializationBlock(cfg))) {
                                        changed = true;
                                        dominators.put(b, aDominator);
                                    }
                                }
                            }
                        }
                    } while (changed);
                }
            };

            calcDominators.accept(instanceSBs);
            calcDominators.accept(toStringSBs);

            return dominators;
        }

        /**
         * Retrieves a single MaterializedInstanceSB from a set of instances. Note that at a given
         * point all values within the set will match via
         * {@link MaterializedConcreteSB#matchingVirtualState(MaterializedConcreteSB)}.
         */
        private MaterializedConcreteSB getRepresentativeSB(EconomicSet<MaterializedConcreteSB> sbs) {
            if (sbs.isEmpty()) {
                return MaterializedInstanceSB.EMPTY;
            }

            MaterializedConcreteSB value = sbs.iterator().next();
            for (MaterializedConcreteSB sb : sbs) {
                assert sb.matchingVirtualState(value);
            }
            return value;
        }

        /**
         * Checks if a MaterializedConcreteSB can be raised to a given block. This can happen when
         * the block is dominated by the last virtualized action within the sb.
         */
        private boolean canRaiseTo(MaterializedConcreteSB sb, HIRBlock pos) {
            if (sb == MaterializedInstanceSB.EMPTY) {
                return false;
            }
            HIRBlock lastAction = normalSuccessorBlock(sb.virtualSB.lastAction, cfg);

            // need to make sure we are not raising the value above its last action.
            return AbstractControlFlowGraph.dominates(lastAction, pos);
        }

        /**
         * Finds the highest position for each materialization which:
         * <ul>
         * <li>comes at or after the original append and also dominates the original materialization
         * point</li>
         * <li>Does not introduce unnecessary materializations.</li>
         * <li>Preserves the original exception handler used by the materialization.</li>
         * </ul>
         *
         * We make sure to not introduce unnecessary materializations by only raising
         * materializations if the materialization is present on all *normal* successors. For
         * {@link WithExceptionNode}s, since it should be an unlikely path, we ignore the exception
         * edge in our calculation if it does not have a materialization.
         *
         * Important Note: since there are control flow splits, it is impossible for multiple
         * InvokeWithExceptionNodes to be within the same block.
         */
        EconomicMap<MaterializedConcreteSB, InvokeWithExceptionNode> calculateRaisedPositions(EconomicSet<MaterializedConcreteSB> dominatorSBs) {
            HIRBlock[] blocks = cfg.reversePostOrder();
            int numBlocks = blocks.length;

            // initialize state
            ArrayList<EconomicSet<MaterializedConcreteSB>> instancesInBlock = new ArrayList<>(numBlocks);
            ArrayList<EconomicSet<MaterializedConcreteSB>> toStringsInBlock = new ArrayList<>(numBlocks);
            for (int i = 0; i < numBlocks; i++) {
                instancesInBlock.add(EconomicSet.create(Equivalence.DEFAULT));
                toStringsInBlock.add(EconomicSet.create(Equivalence.DEFAULT));
            }

            /*
             * Mark the original location of blocks via the dominator map. Because we are using the
             * dominator map, it is possible for the same value to be seen multiple times. However,
             * each value should be at a unique spot.
             */
            for (MaterializedConcreteSB sb : dominatorSBs) {
                HIRBlock originalBlock = sb.getMaterializationBlock(cfg);
                EconomicSet<MaterializedConcreteSB> sbs;
                if (sb instanceof MaterializedInstanceSB) {
                    sbs = instancesInBlock.get(originalBlock.getId());
                } else {
                    sbs = toStringsInBlock.get(originalBlock.getId());
                }

                if (!sbs.isEmpty()) {
                    assert sbs.size() == 1 && sbs.iterator().next() == sb;
                } else {
                    sbs.add(sb);
                }
            }

            /*
             * Walk graph in post order to raise sbs. Because we always materialize at loop entries
             * and exits, it is not necessary for to perform multiple iterations of the walk to
             * account for loops.
             */
            for (int blockID = numBlocks - 1; blockID >= 0; blockID--) {
                HIRBlock block = blocks[blockID];
                if (block.getSuccessorCount() > 0) {
                    processBlock(block, instancesInBlock);
                    processBlock(block, toStringsInBlock);
                }
            }

            /*
             * Record highest point of each sb. Iterate in reserve post order and record for each
             * materialization the first block in which the materialization can be placed.
             */
            EconomicMap<MaterializedConcreteSB, InvokeWithExceptionNode> raisedPositions = EconomicMap.create(Equivalence.DEFAULT, dominatorSBs.size());

            for (int blockID = 0; blockID < numBlocks; blockID++) {
                HIRBlock block = blocks[blockID];
                assert block.getId() == blockID;

                if (block.getEndNode() instanceof InvokeWithExceptionNode invoke) {
                    HIRBlock raisedBlock = normalSuccessorBlock(invoke, cfg);
                    Consumer<MaterializedConcreteSB> updateRaisedPosition = (sb) -> {
                        HIRBlock originalBlock = sb.getMaterializationBlock(cfg);
                        if (!raisedPositions.containsKey(sb) &&
                                        matchingExceptionHandlers(sb.getMaterializationInsertionPoint(), invoke) &&
                                        AbstractControlFlowGraph.dominates(raisedBlock, originalBlock)) {
                            raisedPositions.put(sb, invoke);
                        }
                    };
                    instancesInBlock.get(raisedBlock.getId()).forEach(updateRaisedPosition);
                    toStringsInBlock.get(raisedBlock.getId()).forEach(updateRaisedPosition);
                }
            }

            assert verifyRaisedPositions(raisedPositions);

            return raisedPositions;
        }

        /**
         * Tries to raise instances from the successor blocks into this block.
         */
        private void processBlock(HIRBlock block, ArrayList<EconomicSet<MaterializedConcreteSB>> sbsInBlock) {
            EconomicSet<MaterializedConcreteSB> blockSbs = sbsInBlock.get(block.getId());
            if (blockSbs.isEmpty()) {
                /*
                 * No existing state - try to raise up the successors.
                 */
                if (block.getEndNode() instanceof WithExceptionNode withException) {
                    /*
                     * For blocks with an exception edge, it is okay to assume that exception edge
                     * will not be taken, as this is an unlikely path. Only try to merge the
                     * exception's path state when it is not empty.
                     */
                    EconomicSet<MaterializedConcreteSB> normalSBs = sbsInBlock.get(normalSuccessorBlock(withException, cfg).getId());
                    EconomicSet<MaterializedConcreteSB> exceptionSBs = sbsInBlock.get(exceptionSuccessorBlock(withException, cfg).getId());

                    MaterializedConcreteSB normalSB = getRepresentativeSB(normalSBs);
                    MaterializedConcreteSB exceptionSB = getRepresentativeSB(exceptionSBs);
                    if (canRaiseTo(normalSB, block)) {
                        if (exceptionSB == MaterializedInstanceSB.EMPTY) {
                            blockSbs.addAll(normalSBs);
                        } else if (normalSB.matchingVirtualState(exceptionSB)) {
                            blockSbs.addAll(normalSBs);
                            blockSbs.addAll(exceptionSBs);
                        }
                    }
                } else {
                    /*
                     * If all successors have a matching materialization state, then the
                     * materialization can be merged to this block.
                     */
                    MaterializedConcreteSB newSB = getRepresentativeSB(sbsInBlock.get(block.getSuccessorAt(0).getId()));
                    if (canRaiseTo(newSB, block)) {
                        boolean allMatch = true;
                        for (int i = 1; i < block.getSuccessorCount(); i++) {
                            MaterializedConcreteSB sucSB = getRepresentativeSB(sbsInBlock.get(block.getSuccessorAt(i).getId()));
                            if (!sucSB.matchingVirtualState(newSB)) {
                                allMatch = false;
                                break;
                            }
                        }
                        if (allMatch) {
                            for (int i = 0; i < block.getSuccessorCount(); i++) {
                                HIRBlock succ = block.getSuccessorAt(i);
                                blockSbs.addAll(sbsInBlock.get(succ.getId()));
                            }
                        }
                    }
                }
            } else {
                /*
                 * The block was assigned a value during the initialization step. Any successors
                 * with a matching virtual state can be raised.
                 */
                assert blockSbs.size() == 1;
                MaterializedConcreteSB sb = getRepresentativeSB(blockSbs);

                for (int i = 0; i < block.getSuccessorCount(); i++) {
                    HIRBlock succ = block.getSuccessorAt(i);
                    EconomicSet<MaterializedConcreteSB> sucSBs = sbsInBlock.get(succ.getId());
                    if (sb.matchingVirtualState(getRepresentativeSB(sucSBs))) {
                        blockSbs.addAll(sucSBs);
                    }
                }
            }
        }

        /**
         * Ensures that the raised position dominates the original position. If isn't true, then
         * something very bad has happened.
         */
        private boolean verifyRaisedPositions(EconomicMap<MaterializedConcreteSB, InvokeWithExceptionNode> raisedPositions) {
            var cursor = raisedPositions.getEntries();
            while (cursor.advance()) {
                HIRBlock oBlock = cursor.getKey().getMaterializationBlock(cfg);
                HIRBlock rBlock = normalSuccessorBlock(cursor.getValue(), cfg);
                assert oBlock == rBlock || AbstractControlFlowGraph.dominates(rBlock, oBlock);
            }
            return true;
        }
    }

    /**
     * Returns the normal successor block of the {@link WithExceptionNode}.
     */
    private static HIRBlock normalSuccessorBlock(WithExceptionNode withExceptionNode, ControlFlowGraph cfg) {
        return cfg.blockFor(withExceptionNode.next());
    }

    private static final class ExceptionHandlerDescriptor {
        private final int handlerBci;
        private final int catchTypeCpi;

        ExceptionHandlerDescriptor(ExceptionHandler handler) {
            this.handlerBci = handler.getHandlerBCI();
            this.catchTypeCpi = handler.catchTypeCPI();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ExceptionHandlerDescriptor other)) {
                return false;
            }
            return handlerBci == other.handlerBci && catchTypeCpi == other.catchTypeCpi;
        }

        @Override
        public int hashCode() {
            return Objects.hash(handlerBci, catchTypeCpi);
        }
    }

    private static final class ExceptionHandlerChain {
        private final ResolvedJavaMethod method;
        private final int fallbackBci;
        private final List<ExceptionHandlerDescriptor> handlers;
        private final ExceptionHandlerChain outerChain;

        ExceptionHandlerChain(ResolvedJavaMethod method, int fallbackBci, List<ExceptionHandlerDescriptor> handlers, ExceptionHandlerChain outerChain) {
            this.method = method;
            this.fallbackBci = fallbackBci;
            this.handlers = handlers;
            this.outerChain = outerChain;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ExceptionHandlerChain other)) {
                return false;
            }
            return fallbackBci == other.fallbackBci && Objects.equals(method, other.method) && handlers.equals(other.handlers) && Objects.equals(outerChain, other.outerChain);
        }

        @Override
        public int hashCode() {
            return Objects.hash(method, fallbackBci, handlers, outerChain);
        }
    }

    private static ExceptionHandlerChain exceptionHandlerChain(InvokeWithExceptionNode invoke) {
        FrameState stateAfter = invoke.stateAfter();
        ResolvedJavaMethod method = stateAfter != null ? stateAfter.getMethod() : null;
        FrameState outerState = stateAfter != null ? stateAfter.outerFrameState() : null;
        return exceptionHandlerChain(method, invoke.bci(), outerState);
    }

    private static ExceptionHandlerChain exceptionHandlerChain(FrameState state) {
        if (state == null) {
            return null;
        }
        return exceptionHandlerChain(state.getMethod(), state.bci, state.outerFrameState());
    }

    private static ExceptionHandlerChain exceptionHandlerChain(ResolvedJavaMethod method, int bci, FrameState outerState) {
        ExceptionHandlerChain outerChain = exceptionHandlerChain(outerState);
        if (method == null || bci < 0) {
            /*
             * These invokes do not map cleanly back to a bytecode exception table entry. Keep them
             * conservatively distinct by falling back to the invoke BCI instead of treating them as
             * handler-equivalent.
             */
            return new ExceptionHandlerChain(method, bci, List.of(), outerChain);
        }

        ExceptionHandler[] exceptionHandlers = method.getExceptionHandlers();
        if (exceptionHandlers == null || exceptionHandlers.length == 0) {
            return outerChain;
        }

        ArrayList<ExceptionHandlerDescriptor> handlers = new ArrayList<>();
        /*
         * Match BytecodeParser/BciBlockMapping dispatch order so the signature reflects the actual
         * local exception-dispatch chain, not just the set of covered handlers.
         */
        for (int handlerID = exceptionHandlers.length - 1; handlerID >= 0; handlerID--) {
            ExceptionHandler handler = exceptionHandlers[handlerID];
            if (handler.getStartBCI() <= bci && bci < handler.getEndBCI()) {
                handlers.add(new ExceptionHandlerDescriptor(handler));
            }
        }
        if (handlers.isEmpty()) {
            return outerChain;
        }
        return new ExceptionHandlerChain(method, -1, List.copyOf(handlers), outerChain);
    }

    private static boolean matchingExceptionHandlers(InvokeWithExceptionNode a, InvokeWithExceptionNode b) {
        return Objects.equals(exceptionHandlerChain(a), exceptionHandlerChain(b));
    }

    /**
     * Returns the exception successor block of the {@link WithExceptionNode}.
     */
    private static HIRBlock exceptionSuccessorBlock(WithExceptionNode withExceptionNode, ControlFlowGraph cfg) {
        return cfg.blockFor(withExceptionNode.exceptionEdge());
    }

    private void logEvent(String format, Object... args) {
        DebugContext debug = graph.getDebug();
        if (debug.isLogEnabled(DebugContext.DETAILED_LEVEL)) {
            debug.logv(DebugContext.DETAILED_LEVEL, format, args);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @AutomaticallyRegisteredImageSingleton
    @SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = PartiallyLayerAware.class)
    static class MetadataLookup {
        private static final Constructor<?>[] initZeroArgumentsMethods;
        private static final Constructor<?>[] initWithObjectMethods;
        private static final Constructor<?>[] initWithCapacityMethods;
        private static final Method[] appendMethods;
        private static final Method[] unhandledAppendMethods;
        private static final Method[] toStringMethods;

        /**
         * Method which either may force a materialization or will be virtualized across.
         */
        private static final Executable[] forcedMaterializationMethods;

        private EconomicSet<ResolvedJavaType> twoSlotTypes = null;
        private EconomicSet<ResolvedJavaType> handledSBTypes = null;
        private EconomicSet<ResolvedJavaMethod> forcedMaterializationCandidates = null;
        private EconomicMap<ResolvedJavaMethod, SBOp> sbOpKinds = null;

        private volatile boolean initialized = false;

        static {
            try {
                List<Constructor<?>> initZeroArgumentsList = new ArrayList<>();
                List<Constructor<?>> initWithObjectList = new ArrayList<>();
                List<Constructor<?>> initWithCapacityList = new ArrayList<>();
                List<Method> appendList = new ArrayList<>();
                List<Method> unhandledAppendList = new ArrayList<>();
                List<Method> toStringList = new ArrayList<>();
                List<Executable> forcedMaterializationList = new ArrayList<>();

                if (SBOutliningFeature.Options.OutlineStringBuilderAppends.getValue()) {
                    registerConstructors(StringBuilder.class, initZeroArgumentsList, initWithObjectList, initWithCapacityList, forcedMaterializationList);
                    registerAppendMethods(StringBuilder.class, appendList, unhandledAppendList, forcedMaterializationList);
                    registerToStringMethod(StringBuilder.class, toStringList);
                }

                if (SBOutliningFeature.Options.OutlineStringBufferAppends.getValue()) {
                    registerConstructors(StringBuffer.class, initZeroArgumentsList, initWithObjectList, initWithCapacityList, forcedMaterializationList);
                    registerAppendMethods(StringBuffer.class, appendList, unhandledAppendList, forcedMaterializationList);
                    registerToStringMethod(StringBuffer.class, toStringList);
                }

                Constructor<?>[] emptyConstructor = new Constructor<?>[0];
                Method[] emptyMethod = new Method[0];
                initZeroArgumentsMethods = initZeroArgumentsList.toArray(emptyConstructor);
                initWithObjectMethods = initWithObjectList.toArray(emptyConstructor);
                initWithCapacityMethods = initWithCapacityList.toArray(emptyConstructor);
                appendMethods = appendList.toArray(emptyMethod);
                unhandledAppendMethods = unhandledAppendList.toArray(emptyMethod);
                toStringMethods = toStringList.toArray(emptyMethod);

                forcedMaterializationMethods = forcedMaterializationList.toArray(new Executable[0]);

            } catch (Throwable t) {
                throw VMError.shouldNotReachHere(t);
            }
        }

        @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringBuilder.java#L100-L144")
        @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringBuffer.java#L127-L172")
        private static void registerConstructors(Class<?> clazz, List<Constructor<?>> initZeroArgumentsList, List<Constructor<?>> initWithObjectList,
                        List<Constructor<?>> initWithCapacityList, List<Executable> forcedMaterializationList) {
            initZeroArgumentsList.add(ReflectionUtil.lookupConstructor(clazz));
            initWithCapacityList.add(ReflectionUtil.lookupConstructor(clazz, int.class));
            initWithObjectList.add(ReflectionUtil.lookupConstructor(clazz, String.class));
            initWithObjectList.add(ReflectionUtil.lookupConstructor(clazz, CharSequence.class));
            forcedMaterializationList.add(ReflectionUtil.lookupConstructor(clazz, CharSequence.class));
        }

        @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringBuilder.java#L171-L282")
        @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringBuffer.java#L299-L463")
        private static void registerAppendMethods(Class<?> clazz, List<Method> appendList, List<Method> unhandledAppendList,
                        List<Executable> forcedMaterializationList) throws ClassNotFoundException {
            appendList.add(ReflectionUtil.lookupMethod(clazz, "append", boolean.class));
            appendList.add(ReflectionUtil.lookupMethod(clazz, "append", char.class));
            appendList.add(ReflectionUtil.lookupMethod(clazz, "append", int.class));
            appendList.add(ReflectionUtil.lookupMethod(clazz, "append", long.class));
            appendList.add(ReflectionUtil.lookupMethod(clazz, "append", float.class));
            appendList.add(ReflectionUtil.lookupMethod(clazz, "append", double.class));
            appendList.add(ReflectionUtil.lookupMethod(clazz, "append", String.class));
            appendList.add(ReflectionUtil.lookupMethod(clazz, "append", Object.class));
            appendList.add(ReflectionUtil.lookupMethod(clazz, "append", StringBuffer.class));
            appendList.add(ReflectionUtil.lookupMethod(clazz, "append", CharSequence.class));
            forcedMaterializationList.add(ReflectionUtil.lookupMethod(clazz, "append", StringBuffer.class));
            forcedMaterializationList.add(ReflectionUtil.lookupMethod(clazz, "append", CharSequence.class));

            unhandledAppendList.add(ReflectionUtil.lookupMethod(clazz, "append", CharSequence.class, int.class, int.class));
            unhandledAppendList.add(ReflectionUtil.lookupMethod(clazz, "append", char[].class));
            unhandledAppendList.add(ReflectionUtil.lookupMethod(clazz, "append", char[].class, int.class, int.class));
            unhandledAppendList.add(ReflectionUtil.lookupMethod(clazz, "appendCodePoint", int.class));

            if (clazz == StringBuffer.class) {
                Method appendSB = ReflectionUtil.lookupMethod(StringBuffer.class, "append", Class.forName("java.lang.AbstractStringBuilder"));
                appendList.add(appendSB);
                forcedMaterializationList.add(appendSB);
            }
        }

        @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringBuilder.java#L471-L479")
        @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringBuffer.java#L732-L742")
        private static void registerToStringMethod(Class<?> clazz, List<Method> toStringList) {
            toStringList.add(ReflectionUtil.lookupMethod(clazz, "toString"));
        }

        public void initialize(MetaAccessProvider metaAccess) {
            if (!initialized) {
                synchronized (this) {
                    if (!initialized) {
                        twoSlotTypes = calculateTwoSlotTypes(metaAccess);
                        handledSBTypes = calculateHandledSBTypes(metaAccess);
                        sbOpKinds = calculateSBOpKinds(metaAccess);
                        forcedMaterializationCandidates = EconomicSet.create(Equivalence.DEFAULT);
                        Arrays.stream(forcedMaterializationMethods).forEach(m -> forcedMaterializationCandidates.add(metaAccess.lookupJavaMethod(m)));

                        initialized = true;
                    }
                }
            }
        }

        private static EconomicSet<ResolvedJavaType> calculateTwoSlotTypes(MetaAccessProvider metaAccess) {
            EconomicSet<ResolvedJavaType> twoSlotTypes = EconomicSet.create(Equivalence.DEFAULT, 2);
            twoSlotTypes.add(metaAccess.lookupJavaType(Long.class));
            twoSlotTypes.add(metaAccess.lookupJavaType(Double.class));

            return twoSlotTypes;
        }

        private static EconomicSet<ResolvedJavaType> calculateHandledSBTypes(MetaAccessProvider metaAccess) {
            EconomicSet<ResolvedJavaType> handledTypes = EconomicSet.create(Equivalence.DEFAULT, 2);

            if (SBOutliningFeature.Options.OutlineStringBufferAppends.getValue()) {
                handledTypes.add(metaAccess.lookupJavaType(StringBuffer.class));
            }

            if (SBOutliningFeature.Options.OutlineStringBuilderAppends.getValue()) {
                handledTypes.add(metaAccess.lookupJavaType(StringBuilder.class));
            }

            return handledTypes;
        }

        private static EconomicMap<ResolvedJavaMethod, SBOp> calculateSBOpKinds(MetaAccessProvider metaAccess) {
            int mapSize = initZeroArgumentsMethods.length + initWithObjectMethods.length + initWithCapacityMethods.length + appendMethods.length + unhandledAppendMethods.length +
                            toStringMethods.length;
            EconomicMap<ResolvedJavaMethod, SBOp> map = EconomicMap.create(Equivalence.DEFAULT, mapSize);
            Arrays.stream(initZeroArgumentsMethods).forEach(c -> map.put(metaAccess.lookupJavaMethod(c), SBOp.InitZeroArguments));
            Arrays.stream(initWithObjectMethods).forEach(c -> map.put(metaAccess.lookupJavaMethod(c), SBOp.InitWithObject));
            Arrays.stream(initWithCapacityMethods).forEach(c -> map.put(metaAccess.lookupJavaMethod(c), SBOp.InitWithCapacity));
            Arrays.stream(appendMethods).forEach(m -> map.put(metaAccess.lookupJavaMethod(m), SBOp.Append));
            Arrays.stream(unhandledAppendMethods).forEach(m -> map.put(metaAccess.lookupJavaMethod(m), SBOp.Unhandled));
            Arrays.stream(toStringMethods).forEach(m -> map.put(metaAccess.lookupJavaMethod(m), SBOp.ToString));
            return map;
        }

        EconomicSet<ResolvedJavaType> getTwoSlotTypes() {
            assert initialized && twoSlotTypes != null;
            return twoSlotTypes;
        }

        EconomicSet<ResolvedJavaType> getHandledSBTypes() {
            assert initialized && handledSBTypes != null;
            return handledSBTypes;
        }

        EconomicMap<ResolvedJavaMethod, SBOp> getSBOpKinds() {
            assert initialized && sbOpKinds != null;
            return sbOpKinds;
        }

        EconomicSet<ResolvedJavaMethod> getForcedMaterializationCandidates() {
            assert initialized && forcedMaterializationCandidates != null;
            return forcedMaterializationCandidates;
        }
    }
}
