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

import org.graalvm.visualizer.search.NodeResultItem;
import org.graalvm.visualizer.search.SearchResultsEvent;
import org.graalvm.visualizer.search.SearchResultsListener;
import org.graalvm.visualizer.search.SearchResultsModel;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.WeakListeners;

/**
 *
 * @author sdedic
 */
public class ResultsFlatChildren extends Children.Keys<NodeResultItem> implements SearchResultsListener {
    private final SearchResultsModel  model;
    private SearchResultsListener wl;

    public ResultsFlatChildren(SearchResultsModel model) {
        this.model = model;
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
    public void itemsAdded(SearchResultsEvent event) {
        refreshKeys();
    }

    @Override
    public void itemsRemoved(SearchResultsEvent event) {
        refreshKeys();
    }
    
    private void refreshKeys() {
        setKeys(model.getItems());
    }
    
    @Override
    protected Node[] createNodes(NodeResultItem t) {
        return new Node[] { ItemNode.createNode(model, t, Lookup.EMPTY) };
    }
}
