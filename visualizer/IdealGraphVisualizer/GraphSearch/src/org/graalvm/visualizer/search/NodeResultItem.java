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

import org.graalvm.visualizer.data.InputNode;
import org.graalvm.visualizer.data.Properties;

/**
 *
 * @author sdedic
 */
public class NodeResultItem implements ResultItem {
    private final GraphItem owner;
    private final int   nodeId;
    private Properties  itemProperties;
    private InputNode   node;

    public NodeResultItem(GraphItem ownerHandle, int nodeId, Properties nodeProperties) {
        this.owner = ownerHandle;
        this.nodeId = nodeId;
        this.itemProperties = nodeProperties;
    }

    public NodeResultItem(GraphItem ownerHandle, InputNode node) {
        this.owner = ownerHandle;
        this.node = node;
        this.nodeId = node.getId();
        this.itemProperties = Properties.newProperties(node.getProperties());
    }

    public NodeResultItem(GraphItem ownerHandle, InputNode node, Properties itemProperties) {
        this.owner = ownerHandle;
        this.itemProperties = itemProperties;
        this.node = node;
        this.nodeId = node.getId();
    }

    public InputNode getNode() {
        return node;
    }

    public int getNodeId() {
        return nodeId;
    }

    public GraphItem getOwner() {
        return owner;
    }

    public Properties getItemProperties() {
        return itemProperties;
    }

    @Override
    public String getDisplayName() {
        return itemProperties.getString("name", null); // NOI18N
    }

    @Override
    public Properties getProperties() {
        return itemProperties;
    }
}
