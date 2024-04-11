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

package org.graalvm.visualizer.view.impl;

import jdk.graal.compiler.graphio.parsing.model.ChangedEvent;
import jdk.graal.compiler.graphio.parsing.model.ChangedListener;
import jdk.graal.compiler.graphio.parsing.model.GraphContainer;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.graalvm.visualizer.util.ListenerSupport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Collects a single type of graph from another container. The graphs are picked
 * up by a {@link Predicate} passed to the constructor. The object refires any
 * changed notifications of the original.
 *
 * @author sdedic
 */
public class GraphTypeContainer implements GraphContainer {
    private final GraphContainer delegate;
    private final Predicate<InputGraph> acceptor;
    private final ChangedEvent<GraphContainer> changeEvent = new ChangedEvent<>(this);
    private final String type;
    private final ChangedListener<GraphContainer> refireListener = (e) -> {
        filteredGraphs = null;
        changeEvent.fire();
    };

    // @GuardedBy(this)
    private volatile List<InputGraph> filteredGraphs;

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + Objects.hashCode(this.delegate);
        hash = 97 * hash + Objects.hashCode(this.acceptor);
        hash = 97 * hash + Objects.hashCode(this.type);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final GraphTypeContainer other = (GraphTypeContainer) obj;
        if (!Objects.equals(this.type, other.type)) {
            return false;
        }
        if (!Objects.equals(this.delegate, other.delegate)) {
            return false;
        }
        if (!Objects.equals(this.acceptor, other.acceptor)) {
            return false;
        }
        return true;
    }

    /**
     * Creates a type container for a specific graph type.
     * The container may optionally use an "accptor" to filter contained graphs. If "acceptor" is null,
     * the default filter based on {@link InputGraph#getGraphType()} will be used. {@code null} graph
     * type will accept all graph types. When acceptor is provided, it must ensure its results
     * comply with the type.
     * <p/>
     * If the acceptor is provided, it is compared using {@link Objects#equals(java.lang.Object, java.lang.Object)} -
     * <b>beware of using method references</b> or lambdas as their identity are subject to caching and
     * equals semantics is not defined.
     *
     * @param delegate  the underlying container
     * @param graphType type of graphs, use {@code null} to accept all of them
     * @param acceptor
     */
    public GraphTypeContainer(GraphContainer delegate, String graphType, Predicate<InputGraph> acceptor) {
        this.delegate = delegate;
        this.type = graphType;
        this.acceptor = acceptor;
        ListenerSupport.addWeakListener(refireListener, this.delegate.getChangedEvent());
    }

    @Override
    public Group getContentOwner() {
        return delegate.getContentOwner();
    }

    @Override
    public ChangedEvent<GraphContainer> getChangedEvent() {
        return changeEvent;
    }

    @Override
    public boolean accept(InputGraph g) {
        if (acceptor != null) {
            return acceptor.test(g);
        }
        return type == null || type.equals(g.getGraphType());
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getName() {
        return getContentOwner().getName();
    }

    private List<InputGraph> findGraphs() {
        List<InputGraph> res = filteredGraphs;
        if (res != null) {
            return res;
        }
        List<InputGraph> graphs = new ArrayList<>(delegate.getGraphs());
        for (Iterator<InputGraph> itg = graphs.iterator(); itg.hasNext(); ) {
            InputGraph g = itg.next();
            if (!accept(g)) {
                itg.remove();
            }
        }
        synchronized (this) {
            if (filteredGraphs == null) {
                filteredGraphs = graphs;
            }
            return graphs;
        }
    }

    @Override
    public int getGraphsCount() {
        return findGraphs().size();
    }

    @Override
    public Set<Integer> getChildNodeIds() {
        return getGraphs().parallelStream().flatMap((e) -> e.getNodeIds().stream()).collect(Collectors.toSet());
    }

    @Override
    public Set<InputNode> getChildNodes() {
        return getGraphs().parallelStream().flatMap((e) -> e.getNodes().stream()).collect(Collectors.toSet());
    }

    @Override
    public List<InputGraph> getGraphs() {
        return Collections.unmodifiableList(findGraphs());
    }

    @Override
    public InputGraph getLastGraph() {
        List<InputGraph> gs = findGraphs();
        return gs.isEmpty() ? null : gs.get(gs.size() - 1);
    }

    @Override
    public boolean isNodeChanged(InputGraph base, InputGraph to, int nodeId) {
        List<InputGraph> graphs = getGraphs();
        int fromIndex = graphs.indexOf(base);
        int toIndex = graphs.indexOf(to);
        assert fromIndex != -1 && toIndex >= fromIndex;
        if (fromIndex == toIndex) {
            return false;
        }
        for (int i = fromIndex + 1; i <= toIndex; i++) {
            InputGraph g = graphs.get(i);
            if (g.isDuplicate()) {
                continue;
            }
            if (g.isNodeChanged(nodeId)) {
                return true;
            }
        }
        return false;
    }
}
