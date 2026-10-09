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

import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.IndexAddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.phases.common.writesinking.WriteSinkingUtil;

/**
 * Writes collected while they are movable between their original loop-body position and the point
 * where write sinking either commits them back into the graph or proves that they can flow through
 * a merge or outer loop.
 * <p>
 * A write is movable here because it has been observed by the traversal, but its final graph
 * placement is not known yet. It may later be deleted from the loop body and committed at an exit,
 * merged with equivalent movable writes from other branches, or forced to commit before a read or
 * memory kill that could observe it.
 * <p>
 * The state keeps two linked views of the same movable writes:
 * <ul>
 * <li>a mapping from exact write key to the value and barrier data needed to recreate the write;</li>
 * <li>a per-{@link LocationIdentity} order that preserves memory ordering among potentially
 * aliasing writes.</li>
 * </ul>
 */
public final class MovableWriteState {
    @FunctionalInterface
    public interface DataAction {
        /**
         * No-op action used when callers only need to discard movable writes.
         */
        DataAction unit = (key, data) -> {
        };

        /**
         * Processes the write identified by {@code key} and its movable write data.
         */
        void apply(CacheEntry key, CacheData data);
    }

    /**
     * Movable write data keyed by exact base, offset, and location identity.
     */
    private final EconomicMap<CacheEntry, CacheData> movableWrites;

    /**
     * Per-location write order used to commit or merge movable writes without reordering aliasing
     * memory effects.
     */
    private final EconomicMap<LocationIdentity, WritesOrdering> orderings;

    /**
     * Creates an empty movable-write state.
     */
    public MovableWriteState() {
        movableWrites = EconomicMap.create();
        orderings = EconomicMap.create();
    }

    private MovableWriteState(MovableWriteState other) {
        this.movableWrites = EconomicMap.create();
        MapCursor<CacheEntry, CacheData> writesCursor = other.movableWrites.getEntries();
        while (writesCursor.advance()) {
            this.movableWrites.put(writesCursor.getKey(), writesCursor.getValue());
        }
        this.orderings = EconomicMap.create();
        MapCursor<LocationIdentity, WritesOrdering> orderCursor = other.orderings.getEntries();
        while (orderCursor.advance()) {
            this.orderings.put(orderCursor.getKey(), orderCursor.getValue().copy());
        }
    }

    /**
     * Creates an independent copy of this state.
     */
    public MovableWriteState cloneState() {
        return new MovableWriteState(this);
    }

    /**
     * Returns the movable write data for {@code key}, or {@code null}.
     */
    public CacheData getCacheData(CacheEntry key) {
        return movableWrites.get(key);
    }

    /**
     * Returns the per-location write order for {@code id}, or {@code null}.
     */
    public WritesOrdering getOrdering(LocationIdentity id) {
        return orderings.get(id);
    }

    /**
     * Returns a cursor over all movable writes.
     */
    public MapCursor<CacheEntry, CacheData> getCursor() {
        return movableWrites.getEntries();
    }

    /**
     * Returns all movable write keys.
     */
    public Iterable<CacheEntry> getEntries() {
        return movableWrites.getKeys();
    }

    /**
     * Returns all locations with movable writes.
     */
    public Iterable<LocationIdentity> getLocations() {
        return orderings.getKeys();
    }

    /**
     * Returns the number of locations with movable writes.
     */
    public int getLocationCount() {
        return orderings.size();
    }

    /**
     * Returns {@code true} when this state contains {@code entry}.
     */
    public boolean containsEntry(CacheEntry entry) {
        return movableWrites.containsKey(entry);
    }

    /**
     * Returns {@code true} when this state contains movable writes for {@code locationIdentity}.
     */
    public boolean containsLocation(LocationIdentity locationIdentity) {
        return orderings.containsKey(locationIdentity);
    }

    /**
     * Returns {@code true} when there are no movable writes.
     */
    public boolean isEmpty() {
        return movableWrites.isEmpty();
    }

    /**
     * Applies {@code action} to movable writes in per-location order.
     */
    public void forAllData(DataAction action) {
        MapCursor<LocationIdentity, WritesOrdering> cursor = orderings.getEntries();
        while (cursor.advance()) {
            for (CacheEntry key : cursor.getValue()) {
                action.apply(key, getCacheData(key));
            }
        }
    }

    /**
     * Adds or replaces the movable write identified by {@code identifier}.
     */
    public void addEntry(Graph graph, CacheEntry identifier, CacheData data) {
        WritesOrdering ordering = orderings.get(identifier.identity);
        if (ordering == null) {
            ordering = WritesOrdering.create(graph);
            ordering.add(identifier);
            orderings.put(identifier.identity, ordering);
        } else {
            ordering.remove(identifier);
            ordering.add(identifier);
        }
        movableWrites.put(identifier, data);
        assert verify();
    }

    /**
     * Adds a movable write using metadata from the original {@link WriteNode}.
     */
    public void addEntry(CacheEntry identifier, ValueNode value, WriteNode writeNode) {
        addEntry(value.graph(), identifier, new CacheData(value, writeNode));
    }

    /**
     * Applies {@code action} to movable write keys in per-location order.
     */
    public void forAllInOrder(Consumer<CacheEntry> action) {
        MapCursor<LocationIdentity, WritesOrdering> cursor = orderings.getEntries();
        while (cursor.advance()) {
            for (CacheEntry key : cursor.getValue()) {
                action.accept(key);
            }
        }
    }

    /**
     * Forces commit of all writes being currently sunk, to be inserted before a given commit point.
     */
    public int killWrites(DataAction dataAction) {
        int killedWrites = 0;
        // Commits all sunk writes
        MapCursor<LocationIdentity, WritesOrdering> order = orderings.getEntries();
        while (order.advance()) {
            for (CacheEntry key : order.getValue()) {
                CacheData data = getCacheData(key);
                assert data != null;
                dataAction.apply(key, data);
                movableWrites.removeKey(key);
                killedWrites++;
            }
        }
        assert movableWrites.isEmpty() : "Must be empty " + movableWrites;
        orderings.clear();
        movableWrites.clear();
        assert verify();
        return killedWrites;
    }

    /**
     * Forces commit of all writes corresponding to a given LocationIdentity, up until a given
     * address (For example, when registering a write to a same address, but with an incompatible
     * value or barrier type.).
     */
    @SuppressWarnings("try")
    public int killWritesUntil(LocationIdentity identity, CacheEntry until, DataAction dataAction) {
        // Commits all sunk writes corresponding to this identity.
        WritesOrdering ordering = orderings.get(identity);
        int index = 0;
        if (ordering != null) {
            DebugContext debug = ordering.iterator().next().address.graph().getDebug();
            try (DebugContext.Scope s = debug.scope("WriteSinking")) {
                debug.log(DebugContext.DETAILED_LEVEL, "Killed location identity \"%s\" has associated writes, removing cache entries for (and committing) %s write(s)", identity, ordering.size());

                for (CacheEntry key : ordering) {
                    CacheData data = getCacheData(key);
                    assert data != null : "Unable to determine write metadata for ordering " + ordering;
                    debug.log(DebugContext.VERY_DETAILED_LEVEL, "Killed location identity \"%s\" is going to remove cache entry (and commit) \"%s\" for write", identity, key);
                    dataAction.apply(key, data);
                    movableWrites.removeKey(key);
                    if (index == ordering.size() - 1) {
                        orderings.removeKey(identity);
                    } else {
                        if (key.equals(until)) {
                            ordering.chop(index);
                            break;
                        }
                    }
                    index++;
                }
            }
        }
        assert verify();

        return index;
    }

    public static class CacheData {
        /**
         * Value that should be written when this movable write is committed.
         */
        public final ValueNode value;

        /**
         * Barrier metadata copied from the original write and preserved until commit.
         */
        public final BarrierType barrier;

        /**
         * Merge where this movable write became path-conditional, or {@code null} for an
         * unconditional movable write. For the common zero-iteration loop case this is the
         * {@link LoopBeginNode}: the write is only committed if execution arrived from a backedge
         * that saw the original loop-body write.
         */
        public final LoopBeginNode conditionalMerge;

        /**
         * Records the value and barrier from an original write.
         */
        public CacheData(ValueNode value, WriteNode writeNode) {
            this(value, writeNode.getBarrierType(), null);
        }

        /**
         * Records an unconditional movable write.
         */
        public CacheData(ValueNode value, BarrierType barrierType) {
            this(value, barrierType, null);
        }

        /**
         * Records a movable write, optionally conditional on reaching {@code conditionalMerge}
         * through a predecessor that observed the original write.
         */
        public CacheData(ValueNode value, BarrierType barrierType, LoopBeginNode conditionalMerge) {
            this.value = value;
            this.barrier = barrierType;
            this.conditionalMerge = conditionalMerge;
        }

        /**
         * Returns a hash code matching the value and barrier identity.
         */
        @Override
        public int hashCode() {
            return barrier.hashCode() + 31 * value.hashCode();
        }

        /**
         * Compares movable write data by value, barrier, and conditional merge.
         */
        @Override
        public boolean equals(Object obj) {
            return obj instanceof CacheData other && value.equals(other.value) && barrier.equals(other.barrier) && conditionalMerge == other.conditionalMerge;
        }

        /**
         * Returns {@code true} when this data can be merged with {@code other}.
         */
        public boolean canMergeWith(CacheData other) {
            return WriteSinkingUtil.canMergeValues(this.value, other.value) && this.barrier == other.barrier;
        }

        /**
         * Returns {@code true} when this movable write must be guarded when committed.
         */
        public boolean isConditional() {
            return conditionalMerge != null;
        }
    }

    public static class CacheEntry {

        /**
         * Base object of the movable write address. Equality intentionally uses node identity.
         */
        public final ValueNode base;

        /**
         * Location identity killed by the original write.
         */
        public final LocationIdentity identity;

        /**
         * Constant offset or index node of the movable write address. Equality intentionally uses
         * node identity.
         */
        public final ValueNode offset;

        /**
         * Original address node used when recreating or committing the movable write.
         */
        public final AddressNode address;

        /**
         * Creates a stable key for an address and location identity.
         */
        public CacheEntry(AddressNode address, LocationIdentity identity) {
            this.address = address;
            this.base = address.getBase();
            this.offset = extractOffset(address);
            this.identity = identity;
        }

        private static ValueNode extractOffset(AddressNode address) {
            if (address instanceof OffsetAddressNode offsetAddressNode) {
                return offsetAddressNode.getOffset();
            } else {
                assert address instanceof IndexAddressNode : address;
                return address.getIndex();
            }
        }

        /**
         * Returns a hash code matching the tracked write address and location identity.
         */
        @Override
        public int hashCode() {
            int result = 31 + ((identity == null) ? 0 : identity.hashCode());
            result = 31 * result + ((base == null) ? 0 : base.hashCode());
            return 23 * result + ((offset == null) ? 0 : offset.hashCode());
        }

        /**
         * Compares write keys by exact location identity, base, and offset nodes.
         */
        @Override
        public boolean equals(Object obj) {
            return obj instanceof CacheEntry other && identity.equals(other.identity) && base == other.base && offset == other.offset;
        }

        /**
         * Returns a compact debug representation of the write key.
         */
        @Override
        public String toString() {
            return base + ":" + identity;
        }
    }

    /**
     * Creates an initially empty ordered commit plan for this state.
     */
    public CommitOrder spawnCommitOrder() {
        return new CommitOrder();
    }

    /**
     * A data structure to perform bulk ordered killing in the context of iterating over the
     * entries. This records all the information needed to kill writes a-posteriori, to avoid
     * concurrent modification exceptions and/or unnecessarily complex ordering handling in the
     * loop.
     */
    public final class CommitOrder {

        /**
         * Returns the state that owns this commit order.
         */
        public MovableWriteState getData() {
            return MovableWriteState.this;
        }

        /**
         * Highest movable-write index selected for commit for each location identity.
         */
        final EconomicMap<LocationIdentity, Integer> order = EconomicMap.create(Equivalence.DEFAULT);

        /**
         * Registers {@code key} and all earlier movable writes for the same location for commit.
         */
        public void register(CacheEntry key) {
            register(key, null, null);
        }

        /**
         * Registers {@code key} and all earlier movable writes for the same location for commit.
         *
         * @return {@code true} if this call extended the commit plan.
         */
        public boolean register(CacheEntry key, Consumer<CacheEntry> newCommitAction, Consumer<LocationIdentity> locationKillAction) {
            if (containsEntry(key)) {
                WritesOrdering ordering = getOrdering(key.identity);
                assert ordering != null;
                Integer previousIndex = order.get(key.identity);
                int startIndex = previousIndex == null ? 0 : previousIndex;
                for (int i = startIndex; i < ordering.size(); i++) {
                    if (ordering.get(i).equals(key)) {
                        if (previousIndex != null && i == startIndex) {
                            return false;
                        }
                        if (newCommitAction != null) {
                            for (int k = startIndex; k <= i; k++) {
                                newCommitAction.accept(ordering.get(k));
                            }
                        }
                        if (locationKillAction != null) {
                            locationKillAction.accept(key.identity);
                        }
                        order.put(key.identity, i);
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Applies {@code action} to every write key currently selected for commit.
         */
        public void forAll(Consumer<CacheEntry> action) {
            MapCursor<LocationIdentity, Integer> cursor = order.getEntries();
            while (cursor.advance()) {
                WritesOrdering ordering = getOrdering(cursor.getKey());
                assert ordering != null;
                for (int i = 0; i <= cursor.getValue(); i++) {
                    CacheEntry key = ordering.get(i);
                    action.accept(key);
                }
            }
        }

        /**
         * Commits all selected writes by applying {@code action} in order.
         */
        public void commit(DataAction action) {
            MapCursor<LocationIdentity, Integer> cursor = order.getEntries();
            while (cursor.advance()) {
                CacheEntry until = getOrdering(cursor.getKey()).get(cursor.getValue());
                killWritesUntil(until.identity, until, action);
            }
        }

        /**
         * Returns the number of writes selected for commit.
         */
        public int size() {
            int writeCount = 0;
            MapCursor<LocationIdentity, Integer> cursor = order.getEntries();
            while (cursor.advance()) {
                writeCount += cursor.getValue() + 1;
            }
            return writeCount;
        }

        /**
         * Returns {@code true} when no writes are selected for commit.
         */
        public boolean isEmpty() {
            return order.isEmpty();
        }

        /**
         * Returns a compact debug representation of this commit order.
         */
        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            MapCursor<LocationIdentity, Integer> cursor = order.getEntries();
            while (cursor.advance()) {
                WritesOrdering ordering = getOrdering((cursor.getKey()));
                boolean first = true;
                for (int i = 0; i <= cursor.getValue(); i++) {
                    if (first) {
                        first = false;
                    } else {
                        str.append(", ");
                    }
                    str.append(ordering.get(i));
                }
            }
            return str.toString();
        }
    }

    /**
     * Ensure consistency of the state. Asserted after every public operation.
     */
    public boolean verify() {
        MapCursor<CacheEntry, CacheData> cursor = movableWrites.getEntries();
        while (cursor.advance()) {
            CacheEntry key = cursor.getKey();
            assert containsInfoInOrderings(key) : "Must have proper ordering info in " + key;
        }
        for (WritesOrdering order : orderings.getValues()) {
            assert !order.isEmpty() : order;
            for (CacheEntry entry : order) {
                assert containsEntry(entry) : movableWrites + " must contain " + entry;
            }
        }
        return true;
    }

    private boolean containsInfoInOrderings(CacheEntry key) {
        assert orderings.containsKey(key.identity) : "Orderings " + orderings + " must contain key " + key.identity;
        WritesOrdering orders = orderings.get(key.identity);
        for (CacheEntry orderInfo : orders) {
            if (orderInfo.address.equals(key.address)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a compact debug representation of all movable writes.
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        boolean first = true;
        MapCursor<CacheEntry, CacheData> cursor = getCursor();
        while (cursor.advance()) {
            if (!first) {
                str.append(", ");
            }
            first = false;
            CacheEntry key = cursor.getKey();
            CacheData value = cursor.getValue();
            str.append(value.isConditional() ? "cond " : "").append(key.base).append(" at ").append(key.offset.asJavaConstant().asLong()).append(": ").append(value.value);
        }
        return str.toString();
    }
}
