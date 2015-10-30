/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.memory;

import static jdk.vm.ci.meta.LocationIdentity.any;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jdk.vm.ci.meta.LocationIdentity;

import com.oracle.graal.compiler.common.CollectionsFactory;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.NodeInputList;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.StartNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

@NodeInfo(allowedUsageTypes = {InputType.Extension, InputType.Memory})
public final class MemoryMapNode extends FloatingNode implements MemoryMap, MemoryNode, LIRLowerable {

    public static final NodeClass<MemoryMapNode> TYPE = NodeClass.create(MemoryMapNode.class);
    protected final List<LocationIdentity> locationIdentities;
    @Input(InputType.Memory) NodeInputList<ValueNode> nodes;

    private boolean checkOrder(Map<LocationIdentity, MemoryNode> mmap) {
        for (int i = 0; i < locationIdentities.size(); i++) {
            LocationIdentity locationIdentity = locationIdentities.get(i);
            ValueNode n = nodes.get(i);
            assertTrue(mmap.get(locationIdentity) == n, "iteration order of keys differs from values in input map");
        }
        return true;
    }

    public MemoryMapNode(Map<LocationIdentity, MemoryNode> mmap) {
        super(TYPE, StampFactory.forVoid());
        locationIdentities = new ArrayList<>(mmap.keySet());
        nodes = new NodeInputList<>(this, mmap.values());
        assert checkOrder(mmap);
    }

    public boolean isEmpty() {
        if (locationIdentities.isEmpty()) {
            return true;
        }
        if (locationIdentities.size() == 1) {
            if (nodes.get(0) instanceof StartNode) {
                return true;
            }
        }
        return false;
    }

    public MemoryNode getLastLocationAccess(LocationIdentity locationIdentity) {
        if (locationIdentity.isImmutable()) {
            return null;
        } else {
            int index = locationIdentities.indexOf(locationIdentity);
            if (index == -1) {
                index = locationIdentities.indexOf(any());
            }
            assert index != -1;
            return (MemoryNode) nodes.get(index);
        }
    }

    public Collection<LocationIdentity> getLocations() {
        return locationIdentities;
    }

    public Map<LocationIdentity, MemoryNode> toMap() {
        HashMap<LocationIdentity, MemoryNode> res = CollectionsFactory.newMap(locationIdentities.size());
        for (int i = 0; i < nodes.size(); i++) {
            res.put(locationIdentities.get(i), (MemoryNode) nodes.get(i));
        }
        return res;
    }

    public void generate(NodeLIRBuilderTool generator) {
        // nothing to do...
    }
}
