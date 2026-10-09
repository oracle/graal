/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.phases.common.writesinking.data;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteState.CacheData;
import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteState.CacheEntry;
import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteState.CommitOrder;
import jdk.graal.compiler.phases.common.writesinking.data.WritesOrdering.ReverseOrder;

/**
 * Merges movable write states at control-flow merges.
 * <p>
 * Each incoming state records writes that have been seen but not yet committed back into the fixed
 * graph. At a merge, a write can remain movable only when the incoming branches agree on the
 * same write key, write order, value compatibility, and barrier metadata. If compatible branches
 * carry different values, the processor creates or reuses a value phi for the merged movable write.
 * Writes that cannot satisfy these constraints are marked for ordered commit before the merge.
 * <p>
 * Loop-entry merges also support conditional sinking. A write present on loop backedges but missing
 * on the loop-entry branch can be represented as a conditional movable write, which is later
 * committed as a temporary {@code ConditionalWriteNode}.
 */
public abstract class MovableWriteMergeProcessor<State extends MovableWriteMergeProcessor.DefaultProcessableState> {
    /**
     * Compiler providers used when materializing default constants for conditional writes.
     */
    private final CoreProviders context;

    /**
     * Write keys that cannot remain movable after this merge and must be committed in branch order.
     */
    protected final EconomicSet<CacheEntry> toCommit = EconomicSet.create(Equivalence.DEFAULT);

    /**
     * Location identities whose movable writes were invalidated while merging incoming states.
     */
    protected final EconomicSet<LocationIdentity> killedIdentities = EconomicSet.create(Equivalence.DEFAULT);

    /**
     * Write keys that may flow through this merge as conditional writes. The ordering is the reverse
     * of insertion order so commit code can replay writes in the required memory order.
     */
    protected final WritesOrdering conditionalSinks;

    /**
     * Per-predecessor ordered commit plans built while comparing incoming branch state.
     */
    protected final ArrayList<CommitOrder> orders = new ArrayList<>();

    /**
     * Output state populated by {@link #forgeNewState(List)} after merge legality has been checked.
     */
    protected final State newState;

    /**
     * Whether this processor has completed its single merge operation.
     */
    private boolean done = false;

    /**
     * Graph being analyzed or rewritten.
     */
    protected final StructuredGraph graph;

    /**
     * Creates a merge processor that writes the merged result into {@code newState}.
     */
    public MovableWriteMergeProcessor(State newState, CoreProviders context, StructuredGraph graph) {
        this.newState = newState;
        this.context = context;
        this.graph = graph;
        this.conditionalSinks = WritesOrdering.create(graph);
    }

    /**
     * Returns the merged state after {@link #mergeForState(List)} has completed.
     */
    public State state() {
        assert done : "Must be done";
        return newState;
    }

    /**
     * Fills {@link #newState} with the writes that can flow through the merge.
     */
    public abstract void forgeNewState(List<State> states);

    /**
     * Returns {@code true} when partial-path writes for {@code id} may become conditional writes at
     * this merge.
     */
    protected abstract boolean checkConditionalWrite(LocationIdentity id);

    /**
     * Returns {@code true} when this merge processor should insert value phis into the graph.
     */
    protected boolean addPhis() {
        return true;
    }

    /**
     * Merges incoming movable-write states and returns {@link #state()}.
     */
    public State mergeForState(List<State> states) {
        merge(states);
        return newState;
    }

    /**
     * Returns {@code true} when all incoming data for {@code key} can be represented by a single
     * movable write after the merge.
     */
    public boolean canMergeData(List<State> states, CacheEntry key) {
        boolean conditional = conditionalSinks.contains(key);
        int start = conditional ? 1 : 0;
        CacheData cacheData = states.get(start).getCacheData(key);
        if (cacheData == null) {
            return false;
        }
        if (conditional) {
            CacheData entryData = states.get(0).getCacheData(key);
            if (entryData == null || !canMergeWithAll(entryData, states, key, 1)) {
                CacheData defaultData = createDefaultCacheData(cacheData);
                return defaultData != null && canMergeWithAll(defaultData, states, key, 1);
            }
        }
        return canMergeWithAll(cacheData, states, key, start + 1);
    }

    /**
     * Builds the merged write data for {@code key}, adding a value phi when incoming branches write
     * different compatible values.
     */
    public CacheData getMergedData(List<State> states, CacheEntry key, AbstractMergeNode merge) {
        int conditionalMergeCount = 0;
        boolean conditional = conditionalSinks.contains(key);
        CacheData cacheData = states.get(0).getCacheData(key);
        if (cacheData != null && cacheData.conditionalMerge != null) {
            conditionalMergeCount++;
        }
        boolean phi = false;
        if (conditional) {
            // Even if cacheData is not null, discard it to get a fresh conditional.
            CacheData other = states.get(1).getCacheData(key);
            if (cacheData == null || !canMergeWithAll(cacheData, states, key, 1)) {
                cacheData = createDefaultCacheData(other);
            }
            phi = true;
        }

        assert cacheData != null : "invariant";
        ValueNode value = cacheData.value;
        assert value != null : "invariant";
        for (int i = 1; i < states.size(); i++) {
            CacheData otherData = states.get(i).getCacheData(key);
            if (otherData != null) {
                if (otherData.conditionalMerge != null && otherData.conditionalMerge.equals(cacheData.conditionalMerge)) {
                    conditionalMergeCount++;
                }
                ValueNode otherValue = otherData.value;
                assert cacheData.canMergeWith(otherData) : "invariant";
                if (!phi && otherValue != value) {
                    phi = true;
                }
            }
        }
        LoopBeginNode conditionalMerge = null;
        if (conditionalMergeCount == states.size()) {
            conditionalMerge = cacheData.conditionalMerge;
        }
        if (phi) {
            ValueNode[] values = new ValueNode[states.size()];
            values[0] = cacheData.value;
            for (int i = 1; i < states.size(); i++) {
                CacheData data = states.get(i).getCacheData(key);
                assert data != null;
                ValueNode v = data.value;
                values[i] = v;
            }
            ValuePhiNode v = new ValuePhiNode(value.stamp(NodeView.DEFAULT).unrestricted(), merge, values);
            if (addPhis()) {
                v = merge.graph().addOrUnique(v);
            } else {
                ValuePhiNode dup = merge.graph().findDuplicate(v);
                if (dup != null) {
                    v = dup;
                }
            }
            return new CacheData(v, cacheData.barrier, conditional ? (LoopBeginNode) merge : conditionalMerge);
        } else {
            // case that there is the same value on all branches
            return new CacheData(value, cacheData.barrier, conditionalMerge);
        }
    }

    private static boolean canMergeWithAll(CacheData cacheData, List<? extends DefaultProcessableState> states, CacheEntry key, int start) {
        for (int i = start; i < states.size(); i++) {
            CacheData otherData = states.get(i).getCacheData(key);
            if (otherData != null && !cacheData.canMergeWith(otherData)) {
                return false;
            }
        }
        return true;
    }

    private CacheData createDefaultCacheData(CacheData representative) {
        ConstantNode defaultConstant = getDefaultConstant(representative.value);
        if (defaultConstant == null) {
            return null;
        }
        return new CacheData(defaultConstant, representative.barrier);
    }

    private ConstantNode getDefaultConstant(ValueNode value) {
        Stamp stamp = value.stamp(NodeView.DEFAULT);
        if (stamp instanceof SimdStamp simdStamp) {
            // SimdStamp components are compatible, so the first lane is representative.
            Constant defaultComponent = defaultConstantForStamp(simdStamp.getComponent(0));
            SimdConstant defaultConstant = SimdConstant.broadcast(defaultComponent, simdStamp.getVectorLength());
            return ConstantNode.forConstant(stamp.unrestricted(), defaultConstant, context.getMetaAccess(), graph);
        } else if (stamp instanceof AbstractPointerStamp abstractPointerStamp) {
            return ConstantNode.forConstant(stamp, abstractPointerStamp.nullConstant(), context.getMetaAccess(), graph);
        } else if (stamp instanceof IntegerStamp) {
            return ConstantNode.forIntegerStamp(stamp, 0, graph);
        } else {
            return ConstantNode.defaultForKind(value.getStackKind(), graph);
        }
    }

    private static Constant defaultConstantForStamp(Stamp stamp) {
        assert stamp != null : "invariant";
        if (stamp instanceof IntegerStamp integerStamp) {
            return JavaConstant.forPrimitiveInt(integerStamp.getBits(), 0);
        } else {
            return JavaConstant.defaultForKind(stamp.getStackKind());
        }
    }

    private void merge(List<State> states) {
        assert !done : "Must not be done yet " + states;

        // Collect all entries to process.
        getOrdersThrough(states);

        /*
         * At this point, all keys that are not in toCommit can be moved. All other writes have the
         * same location order in all branches. We still have to add them in said order in the state
         * that will sink through.
         */
        forgeNewState(states);
        assert verifyMergedState(states);

        // clean up
        clear();

        done = true;
    }

    /**
     * Clears transient merge bookkeeping so the processor can be discarded after a merge.
     */
    public void clear() {
        toCommit.clear();
        killedIdentities.clear();
    }

    private void getOrdersThrough(List<State> states) {
        assert toCommit.isEmpty() && killedIdentities.isEmpty() && orders.isEmpty() : toCommit + " " + killedIdentities + " " + orders;
        EconomicSet<LocationIdentity> unionSet = EconomicSet.create(Equivalence.DEFAULT);
        for (State state : states) {
            unionSet.addAll(state.getLocations());
            orders.add(state.spawnCommitOrder());
        }
        for (LocationIdentity id : unionSet) {
            if (checkConditionalWrite(id)) {
                sinkConditionally(states, id);
            } else {
                sinkIdentity(states, id);
            }
        }
    }

    private CacheEntry canSinkCurrent(List<State> states, ReverseOrder[] idOrders, int main) {
        ReverseOrder iter = idOrders[main];
        if (!iter.hasNext()) {
            return null;
        }
        CacheEntry key = iter.get();
        CacheData data = states.get(main).getCacheData(key);
        assert key != null && data != null : key + " " + data;
        for (int i = main + 1; i < idOrders.length; i++) {
            iter = idOrders[i];
            if (!iter.hasNext() || !key.equals(iter.get()) || !data.canMergeWith(states.get(i).getCacheData(key))) {
                return null;
            }
        }
        return key;
    }

    private boolean initReverseOrders(List<State> states, LocationIdentity id, ReverseOrder[] idOrders) {
        boolean noSink = false;
        for (int i = 0; i < states.size(); i++) {
            WritesOrdering ordering = states.get(i).getLocationOrder(id);
            if (ordering != null) {
                idOrders[i] = ordering.reverse();
            } else if (checkConditionalWrite(id) && (i == 0)) {
                idOrders[0] = null;
            } else {
                noSink = true;
            }
        }
        return noSink;
    }

    private void sinkIdentity(List<State> states, LocationIdentity id) {
        ReverseOrder[] idOrders = new ReverseOrder[states.size()];
        boolean noSink = initReverseOrders(states, id, idOrders);
        if (noSink) {
            commitRest(states, idOrders);
            return;
        }
        while (canSinkCurrent(states, idOrders, 0) != null) {
            for (ReverseOrder rev : idOrders) {
                rev.next();
            }
        }
        commitRest(states, idOrders);
    }

    private void sinkConditionally(List<State> states, LocationIdentity id) {
        assert checkConditionalWrite(id) : "Conditional write at id " + id + " must verify";
        ReverseOrder[] idOrders = new ReverseOrder[states.size()];
        boolean noSink = initReverseOrders(states, id, idOrders);
        if (noSink) {
            commitRest(states, idOrders);
            return;
        }
        outer: //
        while (true) { // TERMINATION ARGUMENT: bound by the number of sink-able writes
            CompilationAlarm.checkProgress(graph);
            // Try merging all branches except loop entry.
            CacheEntry key = canSinkCurrent(states, idOrders, 1);
            if (key == null) {
                break;
            }

            // Special handling for loop entry branch
            ReverseOrder iter = idOrders[0];
            boolean conditional = false;
            if (iter == null || !iter.hasNext() || !key.equals(iter.get())) {
                // Key is not present in the branch: we conditionally sink.
                conditional = true;
                conditionalSinks.add(key);
            } else {
                // Key is present
                CacheData loopEntryData = states.get(0).getCacheData(key);
                assert loopEntryData != null;
                if (!loopEntryData.canMergeWith(states.get(1).getCacheData(key))) {
                    // Un-mergeable: we abort.
                    break;
                }
                if (loopEntryData.isConditional()) {
                    // Key is present, but is a conditional of a previous loop.
                    for (int i = 1; i < states.size(); i++) {
                        if (!states.get(i).getCacheData(key).equals(loopEntryData)) {
                            /*
                             * This check tests that a previously sunk conditional write is flowing
                             * through the entire loop, ie, that no event touching the key
                             * associated to this conditional write happened between the loop begin
                             * and each loop end.
                             *
                             * Letting a previous conditional enter another loop that modifies this
                             * write state is extremely hard to get correct, and needs to introduce
                             * an uncontrollable amount of control flow (2^n branches, with n being
                             * the number of loops a conditional write goes through).
                             *
                             * It is much simpler to commit the old conditional write, and continue
                             * with a fresh one.
                             */
                            conditionalSinks.add(key);
                            advanceReverseIters(idOrders, iter, false);
                            break outer;
                        }
                    }
                    // the condional of the previous loop flows freely though this later loop. We
                    // let it flow, as it may be overwritten by a non-conditional write.
                }
                assert canSinkCurrent(states, idOrders, 0) != null;
            }

            advanceReverseIters(idOrders, iter, conditional);
        }
        commitRest(states, idOrders);
    }

    private static void advanceReverseIters(ReverseOrder[] idOrders, ReverseOrder iter, boolean conditional) {
        for (ReverseOrder rev : idOrders) {
            if (conditional && rev == iter) {
                continue;
            }
            rev.next();
        }
    }

    private void commitRest(List<State> states, ReverseOrder[] idOrders) {
        for (int i = 0; i < states.size(); i++) {
            ReverseOrder ordering = idOrders[i];
            if (ordering != null && ordering.hasNext()) {
                CacheEntry key = ordering.get();
                recordMergeOrderingConflict(key);
                orders.get(i).register(key, toCommit::add, killedIdentities::add);
            }
        }
    }

    /**
     * Called when a key cannot flow through a merge because incoming write orders do not agree.
     */
    protected void recordMergeOrderingConflict(@SuppressWarnings("unused") CacheEntry key) {
    }

    /**
     * Returns the keys that were allowed to sink conditionally through this merge.
     */
    public WritesOrdering getConditionals() {
        return conditionalSinks;
    }

    /**
     * Verifies that {@link #newState} is a valid subset of each incoming state, accounting for
     * conditional sinks on loop-entry merges.
     */
    public boolean verifyMergedState(List<State> states) {
        State entryState = states.get(0);
        if (conditionalSinks.isEmpty()) {
            assert newState.isSubStateOf(entryState) : "New state " + newState + " must be a substate of " + entryState;
        } else {
            for (CacheEntry key : newState.getEntries()) {
                assert entryState.containsEntry(key) || conditionalSinks.contains(key) : "Must either be a regular or conditional sink " + key;
            }
        }
        for (int i = 1; i < states.size(); i++) {
            assert newState.isSubStateOf(states.get(i)) : "New state " + newState + " must be a substate of " + entryState;
        }
        return true;
    }

    /**
     * Common base state consumed by the merge processor and extended by the analysis and rewrite
     * closures with pass-specific side data.
     */
    public abstract static class DefaultProcessableState {

        protected final MovableWriteState data;

        /**
         * Creates a processable state backed by existing movable-write data.
         */
        public DefaultProcessableState(MovableWriteState movableWrites) {
            this.data = movableWrites;
        }

        /**
         * Creates an empty processable state.
         */
        public DefaultProcessableState() {
            data = new MovableWriteState();
        }

        /**
         * Returns all movable write keys.
         */
        public Iterable<CacheEntry> getEntries() {
            return data.getEntries();
        }

        /**
         * Returns {@code true} when this state contains {@code key}.
         */
        public boolean containsEntry(CacheEntry key) {
            return data.containsEntry(key);
        }

        /**
         * Returns the movable write data for {@code key}.
         */
        public CacheData getCacheData(CacheEntry key) {
            return data.getCacheData(key);
        }

        /**
         * Returns all locations with movable writes.
         */
        public Iterable<LocationIdentity> getLocations() {
            return data.getLocations();
        }

        /**
         * Creates an ordered commit plan for this state.
         */
        public CommitOrder spawnCommitOrder() {
            return data.spawnCommitOrder();
        }

        /**
         * Returns the write order for {@code id}.
         */
        public WritesOrdering getLocationOrder(LocationIdentity id) {
            return data.getOrdering(id);
        }

        /**
         * Returns a compact debug representation of this processable state.
         */
        @Override
        public String toString() {
            return data.toString();
        }

        /**
         * Returns the underlying mutable movable-write data.
         */
        public MovableWriteState getMovableWrites() {
            return data;
        }

        /**
         * Returns {@code true} when every movable write in this state also exists in
         * {@code other}.
         */
        public boolean isSubStateOf(DefaultProcessableState other) {
            for (CacheEntry key : getEntries()) {
                if (!other.containsEntry(key)) {
                    return false;
                }
            }
            return true;
        }
    }
}
