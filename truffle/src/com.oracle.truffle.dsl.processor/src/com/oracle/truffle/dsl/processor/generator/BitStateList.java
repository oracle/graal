/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.generator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.generator.BitSet.BitRange;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.MultiStateBitSet;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.model.CacheExpression;
import com.oracle.truffle.dsl.processor.model.GuardExpression;
import com.oracle.truffle.dsl.processor.model.InlineFieldData;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.parser.SpecializationGroup.TypeGuard;

final class BitStateList {

    private final List<BitRangedState> entries;
    private final LinkedHashMap<Object, List<BitRangedState>> byKey = new LinkedHashMap<>();
    private final int bitCount;

    BitStateList(List<State<?>> stateObjects) {
        int bitOffset = 0;
        List<BitRangedState> newStates = new ArrayList<>();
        for (State<?> state : stateObjects) {
            List<BitRangedState> values = byKey.computeIfAbsent(state.key, (v) -> new ArrayList<>());
            for (BitRangedState nodeState : values) {
                if (nodeState.state.getClass() == state.getClass()) {
                    throw new IllegalArgumentException(String.format("Duplicate state for value with key %s and class %s.",
                                    state.key, nodeState.state.getClass()));
                }
            }
            int bits = state.getBits();
            BitRangedState rangedState = new BitRangedState(state, new BitRange(bitOffset, bits));
            newStates.add(rangedState);
            values.add(rangedState);
            bitOffset += bits;
        }
        this.entries = Collections.unmodifiableList(newStates);
        this.bitCount = bitOffset;
    }

    int getBitCount() {
        return bitCount;
    }

    List<BitRangedState> getEntries() {
        return entries;
    }

    @SuppressWarnings("unchecked")
    boolean contains(Class<?> clazz, Object key) {
        return lookup((Class<? extends State<?>>) clazz, key) != null;
    }

    <T extends State<?>> List<T> queryStates(Class<T> statesClass) {
        List<T> newStates = new ArrayList<>();
        for (BitRangedState ranged : entries) {
            if (statesClass.isInstance(ranged.state)) {
                newStates.add(statesClass.cast(ranged.state));
            }
        }
        return newStates;
    }

    @SuppressWarnings({"unchecked", "cast"})
    <T extends State<E>, E> Collection<E> queryKeys(Class<T> statesClass) {
        if (statesClass == null) {
            return (Collection<E>) byKey.keySet();
        }
        Set<E> newKeys = new LinkedHashSet<>();
        for (BitRangedState ranged : entries) {
            if (statesClass.isInstance(ranged.state)) {
                newKeys.add((E) statesClass.cast(ranged.state).key);
            }
        }
        return newKeys;
    }

    List<BitRange> queryRanges(StateQuery query) {
        List<BitRange> range = new ArrayList<>();
        for (Object key : getQueryKeys(query)) {
            List<BitRangedState> values = byKey.get(key);
            if (values != null) {
                for (BitRangedState v : values) {
                    if (query.match(v.state)) {
                        range.add(v.bitRange);
                    }
                }
            }
        }
        return range;
    }

    BitRange queryRange(StateQuery query) {
        if (!query.filtersClass()) {
            throw new IllegalArgumentException("A query for a single range must query the state class.");
        }
        for (Object key : getQueryKeys(query)) {
            BitRangedState state = matchQueryAny(query, byKey.get(key));
            if (state != null) {
                return state.bitRange;
            }
        }
        return null;
    }

    boolean contains(StateQuery query) {
        for (Object key : getQueryKeys(query)) {
            if (matchQueryAny(query, byKey.get(key)) != null) {
                return true;
            }
        }
        return false;
    }

    private Collection<? extends Object> getQueryKeys(StateQuery query) {
        if (query.keys == null) {
            return byKey.keySet();
        }
        return query.keys;
    }

    private static BitRangedState matchQueryAny(StateQuery query, List<BitRangedState> values) {
        if (values != null) {
            for (BitRangedState v : values) {
                if (query.match(v.state)) {
                    return v;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    <T> StateQuery filter(StateQuery query) {
        List<Object> newKeys = new ArrayList<>();
        for (Object key : query.keys) {
            BitRangedState state = matchQueryAny(query, byKey.get(key));
            if (state != null) {
                newKeys.add(state.state.key);
            }
        }
        return StateQuery.create((Class<? extends State<T>>) query.filterClass, (List<T>) newKeys);
    }

    String toString(StateQuery query, String elementSep) {
        StringBuilder b = new StringBuilder();
        String sep = "";
        for (Object key : query.keys) {
            BitRangedState state = matchQueryAny(query, byKey.get(key));
            if (state != null) {
                b.append(sep).append(state.state.toString());
                sep = elementSep;
            }
        }
        return b.toString();
    }

    MultiStateBitSet splitBitSets(String namePrefix, NodeData activeNode, int maxBitWidth) {
        List<StateGroup> groups = groupByDependentSpecializations(this.entries);
        List<BitStateList> stateLists = splitByWidth(groups, maxBitWidth);

        for (BitRangedState state : entries) {
            boolean found = false;
            Class<?> stateClass = state.state.getClass();
            for (BitStateList list : stateLists) {
                if (list.contains(stateClass, state.state.key)) {
                    if (found) {
                        throw new AssertionError("found twice");
                    }
                    found = true;
                }
            }

            if (!found) {
                throw new AssertionError("element not contained in split lists " + state);
            }
        }

        List<BitSet> allBitSets = new ArrayList<>();
        List<BitSet> activeBitSets = new ArrayList<>();
        int index = 0;
        for (BitStateList list : stateLists) {
            BitSet bitSet = new BitSet(namePrefix + "state_" + index, list);
            if (list.isRelevantFor(activeNode)) {
                if (list.getBitCount() == 0) {
                    continue;
                }
                activeBitSets.add(bitSet);
            }
            allBitSets.add(bitSet);
            index++;
        }
        return new MultiStateBitSet(allBitSets, activeBitSets);
    }

    private static int countGroupBits(List<StateGroup> groupedStates) {
        int bits = 0;
        for (StateGroup state : groupedStates) {
            bits += state.countBits();
        }
        return bits;
    }

    private static List<BitStateList> splitByWidth(List<StateGroup> groups, int maxBitWidth) {
        List<List<StateGroup>> split = new ArrayList<>();
        List<StateGroup> currentStates = new ArrayList<>();
        int currentWidth = 0;

        // naively pack them in order (we want to preserve order as much as possible)
        for (StateGroup grouped : groups) {
            int bits = grouped.countBits();
            if (!currentStates.isEmpty()) {
                boolean forceNewGroup = !canBeInSameBitSet(currentStates, grouped);
                if (forceNewGroup || currentWidth + bits > maxBitWidth) {
                    split.add(currentStates);
                    currentStates = new ArrayList<>();
                    currentWidth = 0;
                }
            }

            currentStates.add(grouped);
            currentWidth += bits;
        }
        if (!currentStates.isEmpty()) {
            split.add(currentStates);
        }

        if (split.size() > 1) {
            /*
             * Try to compress.
             */
            for (int i = split.size() - 1; i >= 0; i--) {
                List<StateGroup> pack = split.get(i);
                int bits = countGroupBits(pack);

                for (int y = i - 1; y >= 0; y--) {
                    List<StateGroup> otherPack = split.get(y);
                    int otherBits = countGroupBits(otherPack);
                    int otherBitsLeft = maxBitWidth - otherBits;

                    if (otherBitsLeft <= 0) {
                        // other pack is full
                        continue;
                    }

                    if (bits <= otherBitsLeft) {
                        boolean canFullyMerge = true;
                        for (StateGroup group : pack) {
                            if (!canBeInSameBitSet(otherPack, group)) {
                                canFullyMerge = false;
                                break;
                            }
                        }

                        // we can merge packs fully
                        if (canFullyMerge) {
                            otherPack.addAll(pack);
                            split.remove(i);
                            break;
                        }
                    }

                    if (pack.size() > 1) {
                        ListIterator<StateGroup> packsIterator = pack.listIterator();
                        while (packsIterator.hasNext()) {
                            StateGroup group = packsIterator.next();
                            int groupBits = group.countBits();
                            if (groupBits <= otherBitsLeft && canBeInSameBitSet(otherPack, group)) {
                                packsIterator.remove();
                                otherPack.add(group);
                                otherBitsLeft -= groupBits;

                                if (otherBitsLeft <= 0) {
                                    // no more potential with this pack
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        // create flattened lists
        List<BitStateList> packedLists = new ArrayList<>();
        for (List<StateGroup> pack : split) {
            List<State<?>> flattendGroup = new ArrayList<>();
            for (StateGroup group : pack) {
                flattendGroup.addAll(group.states);
            }
            BitStateList list = new BitStateList(flattendGroup);
            if (maxBitWidth == 32 && list.getBitCount() > maxBitWidth) {
                /*
                 * Note we only check this here for 32 bits, because its possible the processor runs
                 * with fewer or more bits were it may happen that we overpack due to atomic group
                 * size.
                 */
                throw new AssertionError("Max bitwidth exceeded. Probably packing error.");
            }
            packedLists.add(list);
        }

        return packedLists;
    }

    private static boolean canBeInSameBitSet(List<StateGroup> states, StateGroup group) {
        for (StateGroup state : states) {
            if (!state.canBeInSameBitSet(group)) {
                return false;
            }
        }
        return true;
    }

    private static List<StateGroup> groupByDependentSpecializations(List<BitRangedState> states) {
        List<StateGroup> groups = new ArrayList<>();
        Map<SpecializationData, StateGroup> specializationGroups = new LinkedHashMap<>();

        for (BitRangedState state : states) {
            SpecializationData dependentSpecialization = state.state.getDependentSpecialization();

            if (dependentSpecialization != null) {
                StateGroup group = specializationGroups.get(dependentSpecialization);
                if (group == null) {
                    /*
                     * A specialization must share the same group as all specializations it replaces
                     * to ensure that specializations are updated atomically from the same state
                     * bitset. This allows us to avoid locks for any updates.
                     */
                    for (SpecializationData replaces : dependentSpecialization.getReplaces()) {
                        group = specializationGroups.get(replaces);
                        if (group != null) {
                            break;
                        }
                    }

                }
                if (group == null) {
                    group = new StateGroup(new ArrayList<>(), null, dependentSpecialization);
                    specializationGroups.put(dependentSpecialization, group);
                    groups.add(group);
                }

                if (group.excludedSpecialization != state.state.getExcludedSpecialization()) {
                    throw new AssertionError("States with dependent specializations must not use excluded specializations.");
                }
                group.states.add(state.state);
            } else {
                // without a dependent specialization it does not matter where the bit is set
                groups.add(new StateGroup(Arrays.asList(state.state), state.state.getExcludedSpecialization(), null));
            }
        }

        return groups;
    }

    private boolean isRelevantFor(NodeData activeNode) {
        for (BitRangedState ranged : entries) {
            if (ranged.state.node == activeNode) {
                return true;
            }
        }
        return false;
    }

    private BitRangedState lookup(Class<? extends State<?>> clazz, Object key) {
        List<BitRangedState> values = byKey.get(key);
        if (values != null) {
            for (BitRangedState v : values) {
                if (v.state.getClass() == clazz) {
                    return v;
                }
            }
        }
        return null;
    }

    static final class StateGroup {

        final List<State<?>> states;
        final SpecializationData excludedSpecialization;
        final SpecializationData dependentSpecialization;

        StateGroup(List<State<?>> states, SpecializationData excludedSpecialization, SpecializationData dependentSpecialization) {
            this.states = states;
            this.excludedSpecialization = excludedSpecialization;
            this.dependentSpecialization = dependentSpecialization;
        }

        boolean canBeInSameBitSet(StateGroup group) {
            if (this.excludedSpecialization != null && this.excludedSpecialization.equals(group.dependentSpecialization)) {
                return false;
            } else if (group.excludedSpecialization != null && group.excludedSpecialization.equals(this.dependentSpecialization)) {
                return false;
            } else {
                return true;
            }
        }

        int countBits() {
            int bits = 0;
            for (State<?> state : states) {
                bits += state.getBits();
            }
            return bits;
        }

    }

    static final class BitRangedState {

        final State<?> state;
        final BitRange bitRange;

        BitRangedState(State<?> state, BitRange bitRange) {
            this.state = state;
            this.bitRange = bitRange;
        }

    }

    static final class SpecializationActive extends State<SpecializationData> {

        SpecializationActive(SpecializationData key) {
            super(key.getNode(), key);
        }

        @Override
        SpecializationData getDependentSpecialization() {
            return key;
        }

        @Override
        int getBits() {
            return 1;
        }

        @Override
        public String toString() {
            return String.format("SpecializationActive[%s]",
                            ElementUtils.getReadableReference(node.getMessageElement(), key.getMethod()));
        }

        @Override
        void addStateDoc(CodeTreeBuilder docBuilder) {
            docBuilder.string("SpecializationActive ");
            docBuilder.javadocLink(getDependentSpecialization().getMethod(), null);
        }

        @Override
        boolean isInlined() {
            return false;
        }

    }

    static final class SpecializationExcluded extends State<SpecializationData> {

        SpecializationExcluded(SpecializationData key) {
            super(key.getNode(), key);
        }

        @Override
        SpecializationData getDependentSpecialization() {
            return key;
        }

        @Override
        int getBits() {
            return 1;
        }

        @Override
        public String toString() {
            return String.format("SpecializationExcluded ",
                            ElementUtils.getReadableReference(node.getMessageElement(), key.getMethod()));
        }

        @Override
        void addStateDoc(CodeTreeBuilder docBuilder) {
            docBuilder.string("SpecializationExcluded ");
            docBuilder.javadocLink(getDependentSpecialization().getMethod(), null);
        }

        @Override
        boolean isInlined() {
            return false;
        }

    }

    static final class SpecializationCachesInitialized extends State<SpecializationData> {

        private final SpecializationData specialization;

        SpecializationCachesInitialized(SpecializationData specialization) {
            super(specialization.getNode(), specialization);
            this.specialization = specialization;
        }

        @Override
        int getBits() {
            return 1;
        }

        @Override
        public String toString() {
            return String.format("SpecializationCachesInitialized ",
                            ElementUtils.getReadableReference(node.getMessageElement(), key.getMethod()));
        }

        @Override
        void addStateDoc(CodeTreeBuilder docBuilder) {
            docBuilder.string("SpecializationCachesInitialized ");
            docBuilder.javadocLink(specialization.getMethod(), null);
        }

        @Override
        boolean isInlined() {
            return false;
        }
    }

    static final class GuardActive extends State<GuardExpression> {

        private final SpecializationData specialization;

        GuardActive(SpecializationData specialization, GuardExpression key) {
            super(specialization.getNode(), key);
            this.specialization = specialization;
        }

        @Override
        SpecializationData getDependentSpecialization() {
            return specialization;
        }

        @Override
        int getBits() {
            return 1;
        }

        @Override
        public String toString() {
            return String.format("GuardActive[specialization=%s, guardIndex=%s]",
                            ElementUtils.getReadableReference(node.getMessageElement(), specialization.getMethod()),
                            getDependentSpecialization().getGuards().indexOf(key));
        }

        @Override
        void addStateDoc(CodeTreeBuilder docBuilder) {
            docBuilder.string(String.format("GuardActive[guardIndex=%s] ",
                            getDependentSpecialization().getGuards().indexOf(key)));
            docBuilder.javadocLink(getDependentSpecialization().getMethod(), null);
        }

        @Override
        boolean isInlined() {
            return false;
        }
    }

    static final class ImplicitCastState extends State<TypeGuard> {

        ImplicitCastState(NodeData node, TypeGuard key) {
            super(node, key);
        }

        @Override
        int getBits() {
            TypeMirror type = key.getType();
            Collection<TypeMirror> sourceTypes = key.getTypeSystem().lookupSourceTypes(type);
            if (sourceTypes.size() > 1) {
                return sourceTypes.size();
            }
            throw new AssertionError();
        }

        @Override
        public String toString() {
            return String.format("ImplicitCast[type=%s, index=%s]",
                            ElementUtils.getSimpleName(key.getType()),
                            key.getSignatureIndex());
        }

        @Override
        boolean isInlined() {
            return false;
        }

    }

    static final class EncodedEnumState extends State<CacheExpression> {

        private final CacheExpression cache;

        EncodedEnumState(NodeData node, CacheExpression cache) {
            super(node, cache);
            this.cache = cache;
        }

        @Override
        int getBits() {
            List<Element> values = ElementUtils.getEnumValues(ElementUtils.castTypeElement(cache.getParameter().getType()));
            int reservedValues = 0;
            if (cache.isNeverDefault()) {
                reservedValues = 2;
            } else {
                reservedValues = 1;
            }
            int maxValue = values.size() + reservedValues;
            return Integer.SIZE - Integer.numberOfLeadingZeros(maxValue);
        }

        @Override
        public String toString() {
            return String.format("EncodedEnum[cache=%s]",
                            ElementUtils.getReadableReference(node.getMessageElement(), cache.getParameter().getVariableElement()));

        }

        @Override
        boolean isInlined() {
            return false;
        }
    }

    static final class InlinedNodeState extends State<InlineFieldData> {

        private final CacheExpression cache;
        private final SpecializationData excludedSpecialization;

        InlinedNodeState(NodeData node, CacheExpression cache, InlineFieldData key, SpecializationData excludedSpecialization) {
            super(node, key);
            this.excludedSpecialization = excludedSpecialization;
            this.cache = cache;
        }

        @Override
        int getBits() {
            return this.key.getBits();
        }

        @Override
        SpecializationData getExcludedSpecialization() {
            return excludedSpecialization;
        }

        @Override
        public String toString() {
            return String.format("InlinedCache[cache=%s]",
                            ElementUtils.getReadableReference(node.getMessageElement(), cache.getParameter().getVariableElement()));
        }

        @Override
        void addStateDoc(CodeTreeBuilder docBuilder) {
            docBuilder.string("InlinedCache").newLine();
            FlatNodeGenFactory.addCacheInfo(docBuilder, "       ", getDependentSpecialization(), cache, key);
        }

        @Override
        boolean isInlined() {
            return true;
        }

    }

    static final class AOTPreparedState extends State<String> {

        AOTPreparedState(NodeData node) {
            super(node, "aot-prepared");
        }

        @Override
        int getBits() {
            return 1;
        }

        @Override
        public String toString() {
            return "AOTPrepared";
        }

        @Override
        boolean isInlined() {
            return false;
        }
    }

    abstract static class State<T> {

        final NodeData node;
        final T key;

        State(NodeData node, T key) {
            this.node = node;
            this.key = key;
        }

        SpecializationData getDependentSpecialization() {
            return null;
        }

        SpecializationData getExcludedSpecialization() {
            return null;
        }

        abstract int getBits();

        @Override
        public abstract String toString();

        @Override
        public int hashCode() {
            return Objects.hash(getClass(), key);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            State<?> other = (State<?>) obj;
            return Objects.equals(key, other.key);
        }

        void addStateDoc(CodeTreeBuilder docBuilder) {
            docBuilder.string(toString());
        }

        abstract boolean isInlined();
    }

}
