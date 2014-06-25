package com.oracle.graal.nodes;

import static com.oracle.graal.api.meta.LocationIdentity.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

@NodeInfo(allowedUsageTypes = {InputType.Extension})
public class MemoryMapNode extends FloatingNode implements MemoryMap, LIRLowerable {

    private final List<LocationIdentity> locationIdentities;
    @Input(InputType.Memory) private final NodeInputList<ValueNode> nodes;

    private boolean checkOrder(Map<LocationIdentity, MemoryNode> mmap) {
        for (int i = 0; i < locationIdentities.size(); i++) {
            LocationIdentity locationIdentity = locationIdentities.get(i);
            ValueNode n = nodes.get(i);
            assertTrue(mmap.get(locationIdentity) == n, "iteration order of keys differs from values in input map");
        }
        return true;
    }

    public MemoryMapNode(Map<LocationIdentity, MemoryNode> mmap) {
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
