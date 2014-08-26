/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes;

import static com.oracle.graal.api.meta.LocationIdentity.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

@NodeInfo(allowedUsageTypes = {InputType.Extension})
public class MemoryMapNode extends FloatingNode implements MemoryMap, LIRLowerable {

    private final List<LocationIdentity> locationIdentities;
    @Input(InputType.Memory) NodeInputList<ValueNode> nodes;

    private boolean checkOrder(Map<LocationIdentity, MemoryNode> mmap) {
        for (int i = 0; i < locationIdentities.size(); i++) {
            LocationIdentity locationIdentity = locationIdentities.get(i);
            ValueNode n = nodes.get(i);
            assertTrue(mmap.get(locationIdentity) == n, "iteration order of keys differs from values in input map");
        }
        return true;
    }

    public static MemoryMapNode create(Map<LocationIdentity, MemoryNode> mmap) {
        return USE_GENERATED_NODES ? new MemoryMapNodeGen(mmap) : new MemoryMapNode(mmap);
    }

    protected MemoryMapNode(Map<LocationIdentity, MemoryNode> mmap) {
        super(StampFactory.forVoid());
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
        if (locationIdentity == FINAL_LOCATION) {
            return null;
        } else {
            int index = locationIdentities.indexOf(locationIdentity);
            if (index == -1) {
                index = locationIdentities.indexOf(ANY_LOCATION);
            }
            assert index != -1;
            return (MemoryNode) nodes.get(index);
        }
    }

    public Collection<LocationIdentity> getLocations() {
        return locationIdentities;
    }

    public Map<LocationIdentity, MemoryNode> toMap() {
        HashMap<LocationIdentity, MemoryNode> res = new HashMap<>(locationIdentities.size());
        for (int i = 0; i < nodes.size(); i++) {
            res.put(locationIdentities.get(i), (MemoryNode) nodes.get(i));
        }
        return res;
    }

    public void generate(NodeLIRBuilderTool generator) {
        // nothing to do...
    }
}
