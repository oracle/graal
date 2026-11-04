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
package jdk.graal.compiler.phases.common;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph.NodeEventListener;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.spi.ValueProxy;
import jdk.graal.compiler.phases.common.ConditionalEliminationUtil.GuardedCondition;
import jdk.graal.compiler.phases.common.ConditionalEliminationUtil.Implication;
import jdk.vm.ci.meta.TriState;

/**
 * Stack of dominating conditions indexed by their
 * {@link LogicNode#forEachImplicationKey implication keys}. Each key maps to a bucket of conditions
 * ordered newest first. Conditions whose proof rules are not covered by their keys are also recorded
 * in a fallback set searched for every query. Merging the matching bucket with the fallback set in
 * stack order selects the same first proof as a full scan, while skipping unrelated conditions.
 * This avoids quadratic searches for sequences of independent tests without capping the search.
 *
 * The index also records condition nodes and Pi/proxy dependencies to track input changes. A change
 * adds affected conditions to fallback until they are popped, leaving their bucket memberships
 * intact for rollback. Using one index for both purposes can select extra candidates; implication
 * checks still validate every proof.
 */
public final class ConditionalEliminationConditionStack implements AutoCloseable {
    /** Newest membership for each normalized implication key or input-change dependency. */
    private final EconomicMap<Object, Membership> index = EconomicMap.create();

    /** Number of active conditions in the prefix of {@link #entries}. */
    private int size;

    /**
     * Reusable condition slots, with the first {@link #size} slots active. Storage grows with peak
     * stack depth; inactive slots have their condition references cleared.
     */
    private final ArrayList<ConditionEntry> entries = new ArrayList<>();

    /** Number of active registrations in the prefix of {@link #memberships}. */
    private int membershipCount;

    /**
     * Reusable membership slots in registration order. Only the first {@link #membershipCount}
     * slots are active; the remaining slots are cleared and retained for reuse.
     */
    private final ArrayList<Membership> memberships = new ArrayList<>();

    /** Positions of conditions that must be considered for every query until popped. */
    private final BitSet fallbackPositions = new BitSet();

    /** Graph-event scope tracking input changes to indexed conditions and dependencies. */
    private final NodeEventScope events;

    public ConditionalEliminationConditionStack(StructuredGraph graph) {
        events = graph.trackNodeEvents(new NodeEventListener() {
            /**
             * Keeps affected conditions in fallback even if edits introduce untracked dependencies.
             * Bucket memberships remain unchanged until pop.
             */
            @Override
            public void inputChanged(Node node) {
                for (Membership membership = index.get(node); membership != null; membership = membership.previousInBucket) {
                    fallbackPositions.set(membership.entry.stackPosition);
                }
            }
        });
    }

    /** One condition's registration under a lookup key or an input-change dependency. */
    private static final class Membership {
        /** Lookup key for this registration, or {@code null} while the slot is inactive. */
        Object key;

        /** Owning condition entry, or {@code null} while the slot is inactive. */
        ConditionEntry entry;

        /** Older membership in the same bucket, restored as its head when this one is popped. */
        Membership previousInBucket;
    }

    /**
     * Reusable condition slot that also consumes implication keys, avoiding a callback allocation on
     * each push.
     */
    private final class ConditionEntry implements Consumer<ValueNode> {
        /** Dominating condition for this slot, or {@code null} while the slot is inactive. */
        GuardedCondition condition;

        /** Fixed position of this slot in {@link ConditionalEliminationConditionStack#entries}. */
        final int stackPosition;

        /** First membership registered for the current condition; rollback stops at this mark. */
        int membershipStart;

        ConditionEntry(int stackPosition) {
            this.stackPosition = stackPosition;
        }

        @Override
        public void accept(ValueNode value) {
            normalizeKey(value, this);
        }

        /**
         * Registers a key once for this entry. All its keys are registered before the next push, so
         * a repeated key already has this entry at the bucket head.
         */
        void addKey(Object key) {
            Membership previous = index.get(key);
            if (previous != null && previous.entry == this) {
                return;
            }
            Membership membership;
            if (membershipCount == memberships.size()) {
                membership = new Membership();
                memberships.add(membership);
            } else {
                membership = memberships.get(membershipCount);
            }
            membershipCount++;
            membership.key = key;
            membership.entry = this;
            membership.previousInBucket = previous;
            index.put(key, membership);
        }

        /**
         * Removes this entry's tail of the active membership range in reverse registration order,
         * restoring each bucket's previous head.
         */
        void removeKeys() {
            while (membershipCount > membershipStart) {
                Membership membership = memberships.get(--membershipCount);
                GraalError.guarantee(index.get(membership.key) == membership, "condition buckets must be popped in stack order");
                if (membership.previousInBucket == null) {
                    index.removeKey(membership.key);
                } else {
                    index.put(membership.key, membership.previousInBucket);
                }
                // Retain the slot for reuse without keeping graph dependencies alive.
                membership.key = null;
                membership.entry = null;
                membership.previousInBucket = null;
            }
        }
    }

    /**
     * Registers a condition and its keys, including the condition node itself so changes to its
     * inputs are tracked even when its lookup keys are operands.
     */
    public void push(GuardedCondition condition) {
        ConditionEntry entry;
        if (size == entries.size()) {
            entry = new ConditionEntry(size);
            entries.add(entry);
        } else {
            entry = entries.get(size);
        }
        entry.condition = condition;
        entry.membershipStart = membershipCount;
        size++;
        LogicNode node = condition.getCondition();
        entry.addKey(node);
        if (!node.forEachImplicationKey(entry)) {
            fallbackPositions.set(entry.stackPosition);
        }
    }

    /**
     * Unwraps Pi/proxy nodes and uses constant values as keys. If {@code entry} is non-null, also
     * registers the key and each unwrapped node so input changes mark the entry for fallback.
     * Registration and queries use the same normalization; implication checks may require stricter
     * matches.
     */
    private static Object normalizeKey(ValueNode value, ConditionEntry entry) {
        ValueNode node = value;
        while (node instanceof ValueProxy proxy) {
            if (entry != null) {
                entry.addKey(node);
            }
            node = proxy.getOriginalNode();
        }
        // Distinct ConstantNodes can represent the same value and participate in the same proof.
        Object key = node.isConstant() ? node.asConstant() : node;
        if (entry != null) {
            entry.addKey(key);
        }
        return key;
    }

    public GuardedCondition pop() {
        ConditionEntry entry = entries.get(size - 1);
        size--;
        GuardedCondition condition = entry.condition;
        entry.removeKeys();
        entry.condition = null;
        fallbackPositions.clear(entry.stackPosition);
        return condition;
    }

    /** Copies another stack's conditions in registration order. */
    public void pushAll(ConditionalEliminationConditionStack other) {
        for (int i = 0; i < other.size; i++) {
            push(other.entries.get(i).condition);
        }
    }

    public void clear() {
        while (size > 0) {
            pop();
        }
    }

    public int size() {
        return size;
    }

    /**
     * Returns the newest condition guarded by either guard, or {@code null} unless both guards are
     * present. Active conditions are ordered by dominance, so its guard is dominated by both guards.
     */
    public GuardedCondition findDeepestCondition(GuardingNode firstGuard, GuardingNode secondGuard) {
        GuardedCondition deepest = null;
        boolean foundFirst = false;
        boolean foundSecond = false;
        for (int i = size - 1; i >= 0; i--) {
            GuardedCondition condition = entries.get(i).condition;
            GuardingNode guard = condition.getGuard();
            if (guard == firstGuard || guard == secondGuard) {
                if (deepest == null) {
                    deepest = condition;
                }
                foundFirst |= guard == firstGuard;
                foundSecond |= guard == secondGuard;
                if (foundFirst && foundSecond) {
                    return deepest;
                }
            }
        }
        return null;
    }

    /**
     * Returns the same first known implication as the full stack, or {@code null}. The matching key
     * bucket and fallback set are merged in descending stack position, visiting entries present in
     * both streams once.
     */
    public Implication findImplication(LogicNode query) {
        Object key = normalizeKey(query.getImplicationKey(), null);
        Membership bucket = index.get(key);
        int fallbackPosition = fallbackPositions.length() - 1;
        while (bucket != null || fallbackPosition >= 0) {
            ConditionEntry candidate = bucket == null ? null : bucket.entry;
            if (candidate == null || candidate.stackPosition < fallbackPosition) {
                candidate = entries.get(fallbackPosition);
            } else {
                bucket = bucket.previousInBucket;
            }
            Implication result = prove(candidate.condition, query);
            if (result != null) {
                return result;
            }
            if (candidate.stackPosition == fallbackPosition) {
                // If the entry was also in the bucket, both streams advance past it together.
                fallbackPosition = fallbackPositions.previousSetBit(fallbackPosition - 1);
            }
        }
        return null;
    }

    /** Validates a candidate's operands, stamps and negation using its implication rules. */
    private static Implication prove(GuardedCondition condition, LogicNode query) {
        TriState result = condition.getCondition().implies(condition.isNegated(), query);
        return result.isKnown() ? new Implication(condition, result.toBoolean()) : null;
    }

    @Override
    public void close() {
        events.close();
    }
}
