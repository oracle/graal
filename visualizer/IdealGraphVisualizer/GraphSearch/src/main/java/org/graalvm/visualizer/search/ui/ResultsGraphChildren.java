/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.search.ui;

import jdk.graal.compiler.graphio.parsing.model.GraphContainer;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import org.graalvm.visualizer.search.GraphItem;
import org.graalvm.visualizer.search.SearchResultsEvent;
import org.graalvm.visualizer.search.SearchResultsListener;
import org.graalvm.visualizer.search.SearchResultsModel;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.WeakListeners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author sdedic
 */
public class ResultsGraphChildren extends Children.Keys<GraphItem> implements SearchResultsListener {
    private final GraphContainer container;
    private final SearchResultsModel model;
    private SearchResultsListener wl;

    public ResultsGraphChildren(GraphContainer container, SearchResultsModel model) {
        this.model = model;
        this.container = container;

        wl = WeakListeners.create(SearchResultsListener.class, this, model);
        model.addSearchResultsListener(wl);
        refreshKeys();
    }

    @Override
    protected void removeNotify() {
        model.removeSearchResultsListener(wl);
        super.removeNotify();
    }

    @Override
    protected void addNotify() {
        super.addNotify();
        if (wl == null) {
            wl = WeakListeners.create(SearchResultsListener.class, this, model);
        }
    }

    @Override
    public void parentsChanged(SearchResultsEvent event) {
        refreshKeys();
    }

    private void refreshKeys() {
        List<InputGraph> parents = new ArrayList<>(container.getGraphs());
        List<GraphItem> items = new ArrayList<>(model.getParents());
        Collections.sort(items, (GraphItem a, GraphItem b) -> {
            int i1 = parents.indexOf(a.getData());
            int i2 = parents.indexOf(b.getData());
            if (i1 != i2) {
                return i1 - i2;
            }
            if (a.getData() == b.getData()) {
                return 0;
            } else {
                return a.hashCode() - b.hashCode();
            }
        });

        setKeys(items);
    }

    @Override
    protected Node[] createNodes(GraphItem t) {
        return new Node[]{
                new GraphNode(model, t)
        };
    }
}
