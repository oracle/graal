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
package org.graalvm.visualizer.search;

import java.util.Collection;
import java.util.EventObject;

/**
 * @author sdedic
 */
public class SearchResultsEvent extends EventObject {
    private final Collection<GraphItem> graphs;
    private final Collection<NodeResultItem> items;
    private final Collection<String> names;

    public SearchResultsEvent(SearchResultsModel source, Collection<String> names) {
        super(source);
        this.names = names;
        this.graphs = null;
        this.items = null;
    }

    public SearchResultsEvent(Collection<GraphItem> graphs,
                              Collection<NodeResultItem> items, SearchResultsModel source) {
        super(source);
        this.graphs = graphs;
        this.items = items;
        this.names = null;
    }

    public SearchResultsEvent(Collection<GraphItem> graphs, SearchResultsModel source) {
        super(source);
        this.graphs = graphs;
        this.items = null;
        this.names = null;
    }

    public Collection<GraphItem> getGraphs() {
        return graphs;
    }

    public Collection<NodeResultItem> getItems() {
        return items;
    }

    public Collection<String> getNames() {
        return names;
    }

    @Override
    public SearchResultsModel getSource() {
        return (SearchResultsModel) super.getSource();
    }
}
