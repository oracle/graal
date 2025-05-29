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

import org.graalvm.visualizer.search.GraphItem;
import org.graalvm.visualizer.search.SearchResultsModel;
import org.graalvm.visualizer.util.GraphTypes;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

import java.awt.Image;

/**
 * @author sdedic
 */
public class GraphNode extends AbstractNode {
    private final GraphItem item;
    private final SearchResultsModel model;
    private final InstanceContent content;

    private Image icon;
    private Image openedIcon;

    private GraphNode(SearchResultsModel model, GraphItem item, InstanceContent content) {
        super(new GraphNodeChildren(model, item), new AbstractLookup(content));
        this.item = item;
        this.model = model;

        setName(item.getDisplayName());
        this.content = content;

        content.add(item);
    }

    public GraphNode(SearchResultsModel model, GraphItem item) {
        this(model, item, new InstanceContent());
    }

    @Override
    public Image getIcon(int type) {
        if (icon == null) {
            GraphTypes tt = Lookup.getDefault().lookup(GraphTypes.class);
            Node n = tt.getTypeNode(item.getType());
            icon = n.getIcon(type);
        }
        return icon;
    }

    @Override
    public Image getOpenedIcon(int type) {
        if (openedIcon == null) {
            GraphTypes tt = Lookup.getDefault().lookup(GraphTypes.class);
            Node n = tt.getTypeNode(item.getType());
            openedIcon = n.getOpenedIcon(type);
        }
        return openedIcon;
    }
}
