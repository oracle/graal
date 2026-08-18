/*
 * Copyright (c) 2011, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.virtual.phases.ea;

import static jdk.graal.compiler.core.common.GraalOptions.EscapeAnalyzeOnly;

import java.util.Optional;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.virtual.phases.ea.ReadEliminationBlockState.ArrayCloneCacheEntry;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationBlockState.IndexedCacheEntry;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationBlockState.NewInitializedArrayCacheEntry;

import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.ScheduleResult;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.util.LoopUtility;
import jdk.graal.compiler.phases.graph.ReentrantBlockIterator;
import jdk.graal.compiler.replacements.nodes.ObjectClone;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationBlockState.CacheEntry;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * This phase performs read and (simple) write elimination on a graph. It operates on multiple
 * granularities, i.e., before and after high-tier lowering. The phase iterates the graph in a
 * reverse-post-order fashion {@linkplain ReentrantBlockIterator} and tracks the currently active
 * value for a specific {@linkplain LocationIdentity}, which allows the removal of subsequent reads
 * if no writes happen in between, etc. if the value read from memory is in a virtual register
 * (node).
 *
 * A trivial example for read elimination can be seen below:
 *
 * <pre>
 * int i = object.fieldValue;
 * // code not changing object.fieldValue but using i
 * consume(object.fieldValue);
 * </pre>
 *
 * Read elimination will transform this piece of code to the code below and remove the second,
 * unnecessary, memory read of the field:
 *
 * <pre>
 * int i = object.fieldValue;
 * // code not changing object.fieldValue but using i
 * consume(i);
 * </pre>
 *
 * In addition to field accesses, this phase also considers indexed accesses (array accesses with
 * potentially non-constant indices) and array clone operations.
 *
 * Without analysis of indexed accesses, read elimination will not be able to remove the subsequent
 * array access below:
 *
 * <pre>
 * int tmp = a[i] // read of array element
 * a[x] = 0; // write of array element where it is known that x != i
 * return a[i]
 * </pre>
 *
 * The analysis of indexed accesses will allow removal of the second read and produce the code below:
 *
 * <pre>
 * int tmp = a[i] // read of array element
 * a[x] = 0; // write of array element where it is known that x != i
 * return tmp
 * </pre>
 *
 * Additionally, for clone operations on arrays, this phase re-routes as many operations on the
 * clone to the clonee as long as it is safe with respect to the memory semantics of the input code.
 * In an ideal situation the number of usages of a clone drops to zero which allows subsequent
 * removal of the clone operation.
 *
 * The usages of clone operations are removed, if possible, by the
 * {@linkplain ReadEliminationPhase}.
 *
 * {@link ObjectCloneRemovalPhase} performs the removal of (guaranteed null checked) array clone
 * nodes without usages. Clone operations, although they allocate memory, do not have a visible
 * side-effect in the semantics of the VM. Thus, if a clone operation has no usages and is not
 * escaping the current compilation unit it can be removed and replaced by the clonee. For this to
 * be true we must ensure the clone is a result of a well defined clone implementation, which is the
 * case for Java arrays.
 *
 * Consider the following example code:
 *
 * <pre>
 * public int foo(int[] bar) {
 *     return bar.clone().length;
 * }
 * </pre>
 *
 * which will be optimized to
 *
 * <pre>
 * public int foo(int[] bar) {
 *     bar.clone();
 *     return bar.length;
 * }
 * </pre>
 *
 * and finally, by the application of {@link ObjectCloneRemovalPhase}, optimized to:
 *
 * <pre>
 * public int foo(int[] bar) {
 *     return bar.length;
 * }
 * </pre>
 *
 */
public class ReadEliminationPhase extends EffectsPhase<CoreProviders> {

    public static class Options {
        // @formatter:off
        @Option(help = "Tries to eliminate array clone operations by handling clone operations in early read elimination.", type = OptionType.Debug)
        public static final OptionKey<Boolean> CloneReadElimination = new OptionKey<>(true);
        // @formatter:on
    }

    protected final boolean considerGuards;

    public ReadEliminationPhase(CanonicalizerPhase canonicalizer) {
        this(canonicalizer, true);
    }

    public ReadEliminationPhase(CanonicalizerPhase canonicalizer, boolean considerGuards) {
        super(1, canonicalizer, true);
        this.considerGuards = considerGuards;
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        if (VirtualUtil.matches(graph, EscapeAnalyzeOnly.getValue(graph.getOptions()))) {
            runAnalysis(graph, context);
        }
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        NotApplicable.when(graphState.allowsFloatingReads(),
                                        "This phase must not be applied while reads are floating"));
    }

    @Override
    public float codeSizeIncrease() {
        return 2f;
    }

    private class MergedReadEliminationClosure extends ReadEliminationClosure {

        private final CoreProviders context;
        private final boolean optimizeArrayClone;

        MergedReadEliminationClosure(ControlFlowGraph cfg, boolean considerGuards, CoreProviders context, boolean optimizeArrayClone) {
            super(cfg, considerGuards);
            this.context = context;
            this.optimizeArrayClone = optimizeArrayClone;
        }

        @Override
        protected ReadEliminationBlockState getInitialState() {
            return new ReadEliminationBlockState();
        }

        private static void registerCloneCacheEntry(ReadEliminationBlockState state, ObjectClone boc) {
            ResolvedJavaType type = StampTool.typeOrNull(boc.asNode().stamp(NodeView.DEFAULT));
            if (type != null && type.isArray() && ((AbstractObjectStamp) boc.asNode().stamp(NodeView.DEFAULT)).nonNull()) {
                ArrayCloneCacheEntry arrayCacheEntry = new ArrayCloneCacheEntry(boc.getObject(),
                                NamedLocationIdentity.getArrayLocation(type.getComponentType().getJavaKind()));
                ValueNode cachedArray = state.getCacheEntry(arrayCacheEntry);
                if (cachedArray != null) {
                    // Transitive caching for clones on the same clonee
                    state.addCacheEntry(arrayCacheEntry, cachedArray);
                } else {
                    state.addCacheEntry(arrayCacheEntry, boc.asNode());
                }
            }
        }

        private ValueNode tryRerouteToClonee(GraphEffectList effects, ReadEliminationBlockState state, ValueNode cloneInput, ValueNode usage) {
            if (cloneInput instanceof ObjectClone) {
                ResolvedJavaType type = StampTool.typeOrNull(cloneInput.stamp(NodeView.DEFAULT));
                if (type != null && type.isArray()) {
                    ValueNode cachedArray = cloneInput;
                    while (cachedArray instanceof ObjectClone) {
                        ArrayCloneCacheEntry arrayCacheEntry = new ArrayCloneCacheEntry(((ObjectClone) cachedArray).getObject(),
                                        NamedLocationIdentity.getArrayLocation(type.getComponentType().getJavaKind()));
                        cachedArray = state.getCacheEntry(arrayCacheEntry);
                        // For chained clone operations we iterate until we found the original clone
                        if (cachedArray instanceof ObjectClone) {
                            ValueNode obj = ((ObjectClone) cachedArray).getObject();
                            if (obj instanceof ObjectClone) {
                                cachedArray = obj;
                                continue;
                            }
                            break;
                        }
                    }
                    if (cachedArray instanceof ObjectClone clonedArray) {
                        var clonee = getScalarAlias(clonedArray.getObject());
                        if (areValuesReplaceable(cloneInput, clonee, considerGuards)) {
                            effects.replaceFirstInput(usage, cloneInput, clonee);
                            changed = true;
                            return cachedArray;
                        }
                    }
                }
            }
            return cloneInput;
        }

        @Override
        protected boolean processNode(Node node, ReadEliminationBlockState state, GraphEffectList effects, FixedWithNextNode lastFixedNode) {
            assert node.isAlive();
            boolean deleted = false;
            if (node instanceof LoadIndexedNode) {
                LoadIndexedNode load = (LoadIndexedNode) node;
                ValueNode unproxifiedArray = getScalarAlias(GraphUtil.unproxify(load.array()));
                unproxifiedArray = tryRerouteToClonee(effects, state, unproxifiedArray, load);
                ValueNode unproxifiedIndex = GraphUtil.unproxify(load.index());
                CacheEntry<?> identifier = new IndexedCacheEntry(unproxifiedArray,
                                NamedLocationIdentity.getArrayLocation(load.elementKind()), unproxifiedIndex, load.elementKind());
                ValueNode cachedValue = state.getCacheEntry(identifier);
                if (cachedValue != null && areValuesReplaceable(load, cachedValue, considerGuards)) {
                    effects.replaceAtUsages(load, cachedValue, load);
                    addScalarAlias(load, cachedValue);
                    deleted = true;
                    effects.addLog(load.graph().getOptimizationLog(),
                                    optimizationLog -> optimizationLog.report(ReadEliminationPhase.class, "IndexedLoadElimination", load));
                } else {
                    CacheEntry<?> newArrayIdentifier = new NewInitializedArrayCacheEntry(unproxifiedArray, NamedLocationIdentity.getArrayLocation(load.elementKind()));
                    cachedValue = state.getCacheEntry(newArrayIdentifier);
                    /*
                     * We cannot just use the unproxified array - it may be missing a proxy or it
                     * may be an optimized node that would need a new proxy. Since we are inserting
                     * fixed nodes we have to adhere to loop closed SSA form.
                     */
                    if (cachedValue != null && doesNotExitLoop(unproxifiedArray, load) && boundsCheckNewArrayLoad(unproxifiedArray, load, effects)) {
                        effects.replaceAtUsages(load, cachedValue, load);
                        addScalarAlias(load, cachedValue);
                        deleted = true;
                        effects.addLog(load.graph().getOptimizationLog(),
                                        optimizationLog -> optimizationLog.report(ReadEliminationPhase.class, "IndexedLoadElimination", load));
                    } else {
                        state.addCacheEntry(identifier, load);
                    }
                }
            } else if (node instanceof StoreIndexedNode) {
                StoreIndexedNode store = (StoreIndexedNode) node;
                ValueNode unproxifiedArray = getScalarAlias(GraphUtil.unproxify(store.array()));
                ValueNode unproxifiedIndex = GraphUtil.unproxify(store.index());
                CacheEntry<?> identifier = new IndexedCacheEntry(unproxifiedArray,
                                NamedLocationIdentity.getArrayLocation(store.elementKind()), unproxifiedIndex, store.elementKind());
                ValueNode cachedValue = state.getCacheEntry(identifier);
                ValueNode finalValue = getScalarAlias(store.value());
                if (GraphUtil.unproxify(finalValue) == GraphUtil.unproxify(cachedValue)) {
                    effects.deleteNode(store);
                    deleted = true;
                    effects.addLog(store.graph().getOptimizationLog(),
                                    optimizationLog -> optimizationLog.report(ReadEliminationPhase.class, "IndexedStoreElimination", store));
                }
                state.killReadCache(node, identifier.getIdentity(), unproxifiedIndex, unproxifiedArray);
                state.addCacheEntry(identifier, finalValue);
            } else if (optimizeArrayClone && node instanceof ObjectClone) {
                ObjectClone cloneNode = (ObjectClone) node;
                tryRerouteToClonee(effects, state, cloneNode.getObject(), cloneNode.asNode());
                registerCloneCacheEntry(state, cloneNode);
            } else if (optimizeArrayClone && node instanceof ArrayLengthNode) {
                ArrayLengthNode al = (ArrayLengthNode) node;
                ValueNode array = al.array();
                tryRerouteToClonee(effects, state, array, al);
            } else if (node instanceof NewArrayNode newArray && newArray.fillContents()) {
                CacheEntry<?> identifier = new NewInitializedArrayCacheEntry(newArray);
                /*
                 * The constant built here has the same semantics as LoadIndexed's load conversions,
                 * i.e., subword types are extended to i32, null constants are uncompressed.
                 */
                ConstantNode defaultConstant = ConstantNode.defaultForKind(newArray.elementType().getJavaKind());
                state.addCacheEntry(identifier, defaultConstant);
            } else {
                deleted = super.processNode(node, state, effects, lastFixedNode);
            }
            return deleted;
        }

        /**
         * Ensure that we do not need to create {@link ProxyNode} nodes between
         * {@code unproxifiedArray} and {@code load} to add a {@link InputType#Value}l edge between
         * them. If {@code unproxifiedArray} is not a fixed node it is not part of the control flow
         * graph and we give up.
         */
        private boolean doesNotExitLoop(ValueNode unproxifiedArray, LoadIndexedNode load) {
            ValueNode array = unproxifiedArray;
            // for floating nodes we cannot really answer the question if they are inside or outside
            // a loop but we can use the merge point for floating nodes
            if (array instanceof PhiNode phi) {
                array = phi.merge();
            }
            if (array instanceof ProxyNode proxy) {
                array = proxy.proxyPoint();
            }
            return LoopUtility.canUseWithoutProxy(cfg, array, load);
        }

        /**
         * When {@code load} is a direct load from the newly allocated, default initialized
         * {@code unproxifiedArray}, the load can be replaced by a default constant if it is in
         * bounds. This method attempts to build the bounds check with a guard.
         *
         * @return {@code true} if the load is proved in-bounds or a guard could be placed;
         *         {@code false} if the load is proved out of bounds or no guard could be placed
         */
        private boolean boundsCheckNewArrayLoad(ValueNode unproxifiedArray, LoadIndexedNode load, GraphEffectList effects) {
            ValueNode arrayLength = getScalarAlias(ArrayLengthNode.create(unproxifiedArray, context.getConstantReflection()));
            ValueNode index = getScalarAlias(load.index());
            LogicNode indexInBounds = IntegerBelowNode.create(index, arrayLength, NodeView.DEFAULT);
            if (indexInBounds instanceof LogicConstantNode) {
                return indexInBounds.isTautology();
            }
            if (load.graph().getGraphState().isExplicitExceptionsNoDeopt()) {
                return false;
            }
            if (!arrayLength.isAlive() && arrayLength instanceof FixedWithNextNode fixedArrayLength) {
                effects.addFixedNodeBefore(fixedArrayLength, load);
            }
            effects.addFloatingNode(indexInBounds, "bounds check condition");
            /*
             * As in the default lowering of LoadIndexedNode, the bounds check guard uses no
             * speculation and InvalidateReprofile as the deoptimization action. The next time the
             * method is parsed, we will have seen an exception at the LoadIndexed's BCI, so we will
             * recompile with explicit exceptions. This should avoid deopt loops even without
             * speculation.
             */
            FixedGuardNode fixedGuard = new FixedGuardNode(indexInBounds, DeoptimizationReason.BoundsCheckException, DeoptimizationAction.InvalidateReprofile, SpeculationLog.NO_SPECULATION, false,
                            load.next().getNodeSourcePosition());
            effects.addFixedNodeBefore(fixedGuard, load);
            return true;
        }
    }

    @Override
    protected Closure<?> createEffectsClosure(CoreProviders context, ScheduleResult schedule, ControlFlowGraph cfg, OptionValues options) {
        return new MergedReadEliminationClosure(cfg, considerGuards, context, Options.CloneReadElimination.getValue(options));
    }
}
