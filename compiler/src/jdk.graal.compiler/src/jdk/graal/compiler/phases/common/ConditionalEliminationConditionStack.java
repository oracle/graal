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
import java.util.List;

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
 * Active dominating conditions indexed by their
 * {@link LogicNode#forEachImplicationKey implication keys}. Conditions whose proof rules are not
 * covered by their keys remain in a fallback set considered for every query. A query merges one key
 * bucket with that set in stack order, preserving the first proof selected by a full scan. Searches
 * are never capped.
 * This avoids repeatedly scanning unrelated dominating conditions, which can otherwise make a
 * sequence of independent tests quadratic in the number of conditions.
 *
 * The same map records condition identities and Pi/proxy dependencies. An input change to any key
 * moves its entries to fallback until popped. Memberships stay fixed so that rollback is always in
 * stack order. Sharing the map may select or invalidate extra candidates, which is conservative.
 */
public final class ConditionalEliminationConditionStack implements AutoCloseable {
    private final EconomicMap<Object, ArrayList<Entry>> index = EconomicMap.create();
    private final ArrayList<Entry> entries = new ArrayList<>();
    private final BitSet fallback = new BitSet();
    private final NodeEventScope events;

    public ConditionalEliminationConditionStack(StructuredGraph graph) {
        events = graph.trackNodeEvents(new NodeEventListener() {
            @Override
            public void inputChanged(Node node) {
                List<Entry> affected = index.get(node);
                if (affected != null) {
                    // Keep the original bucket memberships for LIFO removal. Searching fallback
                    // until pop also covers later changes to dependencies introduced by this edit.
                    for (Entry entry : affected) {
                        fallback.set(entry.position);
                    }
                }
            }
        });
    }

    private final class Entry {
        final GuardedCondition condition;
        final int position;
        final List<Object> keys = new ArrayList<>(3);

        Entry(GuardedCondition condition) {
            this.condition = condition;
            position = entries.size();
        }

        void addImplicationKey(ValueNode value) {
            valueKey(value, this);
        }

        void addKey(Object key) {
            ArrayList<Entry> bucket = index.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>(1);
                index.put(key, bucket);
            } else if (bucket.get(bucket.size() - 1) == this) {
                // Entries are registered oldest first. Checking the last member deduplicates even
                // shared Pi/proxy chains in constant time per key.
                return;
            }
            bucket.add(this);
            keys.add(key);
        }

        void removeKeys() {
            for (Object key : keys) {
                ArrayList<Entry> bucket = index.get(key);
                GraalError.guarantee(bucket.remove(bucket.size() - 1) == this, "condition buckets must be popped in stack order");
                if (bucket.isEmpty()) {
                    index.removeKey(key);
                }
            }
        }
    }

    public void push(GuardedCondition condition) {
        Entry entry = new Entry(condition);
        entries.add(entry);
        LogicNode node = condition.getCondition();
        // Track input changes to the condition itself even when its lookup keys are operands.
        entry.addKey(node);
        if (!node.forEachImplicationKey(entry::addImplicationKey)) {
            fallback.set(entry.position);
        }
    }

    /**
     * Normalizes keys identically for registration and queries, recording proxy dependencies during
     * registration. Providers may require stricter matches; extra candidates are harmless.
     */
    private static Object valueKey(ValueNode value, Entry entry) {
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
        Entry entry = entries.remove(entries.size() - 1);
        entry.removeKeys();
        fallback.clear(entry.position);
        return entry.condition;
    }

    /** Copies another stack's conditions in registration order. */
    public void pushAll(ConditionalEliminationConditionStack other) {
        for (Entry entry : other.entries) {
            push(entry.condition);
        }
    }

    public void clear() {
        entries.clear();
        index.clear();
        fallback.clear();
    }

    public int size() {
        return entries.size();
    }

    /**
     * Returns the newest condition guarded by either guard, or {@code null} unless both guards are
     * present. Active conditions are ordered by dominance, so its guard is dominated by both guards.
     */
    public GuardedCondition findDeepestCondition(GuardingNode firstGuard, GuardingNode secondGuard) {
        GuardedCondition deepest = null;
        boolean foundFirst = false;
        boolean foundSecond = false;
        for (int i = entries.size() - 1; i >= 0; i--) {
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

    /** Returns the same first known implication as the full stack, or {@code null}. */
    public Implication findImplication(LogicNode query) {
        Object key = valueKey(query.getImplicationKey(), null);
        List<Entry> bucket = index.get(key);
        int bucketPosition = bucket == null ? -1 : bucket.size() - 1;
        int fallbackPosition = fallback.length() - 1;
        // Merge newest first to select the same dominating proof as the original stack scan.
        while (bucketPosition >= 0 || fallbackPosition >= 0) {
            Entry candidate = bucketPosition >= 0 ? bucket.get(bucketPosition) : null;
            if (candidate == null || candidate.position < fallbackPosition) {
                candidate = entries.get(fallbackPosition);
            } else {
                bucketPosition--;
            }
            Implication result = prove(candidate.condition, query);
            if (result != null) {
                return result;
            }
            if (candidate.position == fallbackPosition) {
                // An invalidated entry can occur in both streams. Visit it once.
                fallbackPosition = fallback.previousSetBit(fallbackPosition - 1);
            }
        }
        return null;
    }

    private static Implication prove(GuardedCondition condition, LogicNode query) {
        // Keys only select candidates; the proof must still check operands, stamps and negation.
        TriState result = condition.getCondition().implies(condition.isNegated(), query);
        return result.isKnown() ? new Implication(condition, result.toBoolean()) : null;
    }

    @Override
    public void close() {
        events.close();
    }
}
