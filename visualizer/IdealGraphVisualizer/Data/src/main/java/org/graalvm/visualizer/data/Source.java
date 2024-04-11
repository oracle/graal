/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.data;

import java.util.*;

import jdk.graal.compiler.graphio.parsing.model.InputNode;

/**
 * Holds a collection of represented {@link InputNodes}. The holder is optimized
 * for 0-1 InputNodes, but can hold any number.
 */
public class Source {
    /**
     * If number of nodes is larger, Set for their IDs is allocated and managed
     * to make duplicate handling in {@link #addSourceNode} faster.
     */
    private static final int SET_THRESHOLD = 20;

    /**
     * Holds either a single {@link InputNode}, or a {@code List&lt;InputNode>}
     * in case there are multiple nodes for this source. Flag {@link #many}
     * determines the value type.
     */
    private Object sources;

    /**
     * If true, {@link #sources} holds a List rather than the node itself.
     */
    private boolean many;

    /**
     * Set of IDs already added. The set is not allocated until the number of
     * source nodes exceeds {@link #SET_THRESHOLD}.
     */
    private Set<Integer> set;

    public Source() {
    }

    private boolean hasMany() {
        return many;
    }

    public boolean isEmpty() {
        return sources == null;
    }

    private List<InputNode> list() {
        assert hasMany();
        return ((List<InputNode>) sources);
    }

    /**
     * Returns the first InputNode or {@code null} if no sources.
     *
     * @return
     */
    public InputNode first() {
        if (hasMany()) {
            return list().get(0);
        } else {
            return (InputNode) sources;
        }
    }

    private Set<Integer> createIdSet() {
        if (set == null) {
            set = new HashSet<>();
            for (InputNode n : list()) {
                set.add(n.getId());
            }
        }
        return set;
    }

    public int firstId() {
        return sources == null ? -1 : first().getId();
    }

    /**
     * Returns source InputNode instances.
     *
     * @return unmodifiable collection.
     */
    public List<InputNode> getSourceNodes() {
        if (hasMany()) {
            return Collections.unmodifiableList((List) sources);
        } else if (isEmpty()) {
            return Collections.emptyList();
        } else {
            return Collections.singletonList((InputNode) sources);
        }
    }

    /**
     * Retrieves source node IDs.
     *
     * @return set of node IDs.
     */
    public final Set<Integer> getSourceNodeIds() {
        return collectIds(new HashSet<>());
    }

    /**
     * Collects source node IDs into the collection {@code col}. Duplicate
     * handling is determined by the passed collection.
     *
     * @param <T> collection type
     * @param col node IDs will be added to this collection
     * @return the value of {@code col}.
     */
    public <T extends Collection<Integer>> T collectIds(T col) {
        assert col != null;
        if (hasMany()) {
            for (InputNode n : list()) {
                col.add(n.getId());
            }
        } else if (!isEmpty()) {
            col.add(((InputNode) sources).getId());
        }
        return col;
    }

    /**
     * Returns IDs of source's nodes.
     *
     * @return node IDs, as a Set.
     * @deprecated this was implemented to be fast and cache the data internally
     * which resulted in large amount of retained memory for large graphs.
     * Depending on the use-case, use other ways how to obtain node IDs, or use
     * {@link #getSourceNodeIds} but be aware that the method always creates a
     * new set.
     */
    @Deprecated
    public Set<Integer> getSourceNodesAsSet() {
        if (hasMany()) {
            return Collections.unmodifiableSet(createIdSet());
        } else if (isEmpty()) {
            return Collections.emptySet();
        } else {
            return Collections.singleton(((InputNode) sources).getId());
        }
    }

    public void addSourceNode(InputNode n) {
        List<InputNode> nodes;
        if (isEmpty()) {
            sources = n;
            return;
        } else if (!hasMany()) {
            if (sources == n) {
                return;
            }
            InputNode existing = (InputNode) sources;
            sources = nodes = new ArrayList<>(2);
            nodes.add(existing);
            many = true;
        } else {
            nodes = list();
            if (nodes.size() < SET_THRESHOLD) {
                if (nodes.contains(n)) {
                    return;
                }
            } else if (createIdSet().contains(n.getId())) {
                return;
            }
        }
        nodes.add(n);
        if (set != null) {
            set.add(n.getId());
        }
    }

    public interface Provider {

        Source getSource();
    }

    public void addSourceNodes(Source s) {
        for (InputNode n : s.getSourceNodes()) {
            addSourceNode(n);
        }
    }

    public void replaceFrom(Source source) {
        if (source.hasMany()) {
            List<InputNode> nodes;
            this.sources = nodes = new ArrayList<>(source.list());
            many = true;
            if (nodes.size() > SET_THRESHOLD) {
                set = new HashSet<>();
                for (InputNode n : nodes) {
                    set.add(n.getId());
                }
            }
        } else {
            sources = source.sources;
            many = false;
        }
    }
}
