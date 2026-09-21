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
import java.util.Iterator;

import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.graph.Graph;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteState.CacheEntry;

/**
 * An ordered list of writes to a specific {@link LocationIdentity}. The writes must be scheduled in
 * this order.
 */
public final class WritesOrdering extends ArrayList<CacheEntry> {
    private static final long serialVersionUID = 5607680034990650382L;

    private static final int DEFAULT_SIZE = 2;

    private final transient Graph graph;

    private WritesOrdering(Graph graph) {
        super(DEFAULT_SIZE);
        this.graph = graph;
    }

    /**
     * Creates an empty per-location ordering associated with {@code graph}.
     */
    public static WritesOrdering create(Graph graph) {
        return new WritesOrdering(graph);
    }

    private WritesOrdering(WritesOrdering other) {
        super(other);
        this.graph = other.graph;
    }

    protected WritesOrdering copy() {
        return new WritesOrdering(this);
    }

    /**
     * Checks that all the writes in this ordering appear in {@code other} in the same order. Note
     * that {@code other} can have other writes in-between.
     *
     * Examples:
     *
     * <pre>
     * [1, 4, 8].agreesWith([1, 2, 3, 4, 5, 6, 7, 8, 9]) -> true
     * [1, 5, 2].agreesWith([1, 2, 3, 4, 5, 6, 7, 8, 9]) -> false
     * [].agreesWith([1, 2, 3, 4, 5, 6, 7, 8, 9]) -> true
     * order.agreesWith(order) -> true
     * </pre>
     */
    public boolean agreesWith(WritesOrdering other) {
        Iterator<CacheEntry> otherItr = other.iterator();
        for (CacheEntry key : this) {
            while (true) { // TERMINATION ARGUMENT: processing fixed data structure
                CompilationAlarm.checkProgress(graph);
                if (otherItr.hasNext()) {
                    if (otherItr.next().equals(key)) {
                        break;
                    }
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Returns an iterator that starts from the end of this ordering.
     */
    public ReverseOrder reverse() {
        return new ReverseOrder();
    }

    /**
     * Cuts off entries from this ordering, until index n (included).
     */
    void chop(int n) {
        removeRange(0, n + 1);
    }

    public final class ReverseOrder implements Iterator<CacheEntry>, Iterable<CacheEntry> {
        private int pos;

        private ReverseOrder() {
            this.pos = size() - 1;
        }

        /**
         * Returns the current entry without advancing the reverse iterator.
         */
        public CacheEntry get() {
            return WritesOrdering.this.get(pos);
        }

        /**
         * Returns the current entry and advances toward the beginning of the ordering.
         */
        @Override
        public CacheEntry next() {
            return WritesOrdering.this.get(pos--);
        }

        /**
         * Returns {@code true} when another entry exists in reverse order.
         */
        @Override
        public boolean hasNext() {
            return pos >= 0;
        }

        /**
         * Returns this iterator as its own iterable.
         */
        @Override
        public Iterator<CacheEntry> iterator() {
            return this;
        }
    }
}
