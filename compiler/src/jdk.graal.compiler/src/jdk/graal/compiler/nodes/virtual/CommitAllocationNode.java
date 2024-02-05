/*
 * Copyright (c) 2009, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.nodes.virtual;

import static jdk.graal.compiler.nodeinfo.InputType.Association;
import static jdk.graal.compiler.nodeinfo.InputType.Extension;
import static jdk.graal.compiler.nodeinfo.InputType.Memory;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.nodes.java.MonitorIdNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.spi.VirtualizableAllocation;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;

// @formatter:off
@NodeInfo(nameTemplate = "Alloc {i#virtualObjects}",
          allowedUsageTypes = {Extension, Memory},
          cycles = CYCLES_UNKNOWN,
          cyclesRationale = "We don't know statically how many, and which, allocations are done.",
          size = SIZE_UNKNOWN,
          sizeRationale = "We don't know statically how much code for which allocations has to be generated."
)
// @formatter:on
public final class CommitAllocationNode extends FixedWithNextNode implements VirtualizableAllocation, Lowerable, Simplifiable, SingleMemoryKill, MemoryAccess {

    public static final NodeClass<CommitAllocationNode> TYPE = NodeClass.create(CommitAllocationNode.class);

    @Input NodeInputList<VirtualObjectNode> virtualObjects = new NodeInputList<>(this);
    @Input NodeInputList<ValueNode> values = new NodeInputList<>(this);
    @Input(Association) NodeInputList<MonitorIdNode> locks = new NodeInputList<>(this);
    protected ArrayList<Integer> lockIndexes = new ArrayList<>(Arrays.asList(0));
    protected ArrayList<Boolean> ensureVirtual = new ArrayList<>();

    public CommitAllocationNode() {
        super(TYPE, StampFactory.forVoid());
    }

    public List<VirtualObjectNode> getVirtualObjects() {
        return virtualObjects;
    }

    public List<ValueNode> getValues() {
        return values;
    }

    public List<MonitorIdNode> getLocks() {
        return locks.snapshot();
    }

    public List<MonitorIdNode> getLocks(int objIndex) {
        return locks.subList(lockIndexes.get(objIndex), lockIndexes.get(objIndex + 1));
    }

    public int getObjectIndex(MonitorIdNode monitorId) {
        int monitorIndex = locks.indexOf(monitorId);
        if (monitorIndex == -1) {
            return -1;
        }

        for (int objIndex = 0; objIndex < virtualObjects.size(); objIndex++) {
            if (lockIndexes.get(objIndex) <= monitorIndex && monitorIndex < lockIndexes.get(objIndex + 1)) {
                return objIndex;
            }
        }

        return -1;
    }

    public List<Boolean> getEnsureVirtual() {
        return ensureVirtual;
    }

    @Override
    public boolean verifyNode() {
        assertTrue(virtualObjects.size() + 1 == lockIndexes.size(), "lockIndexes size doesn't match %s, %s", virtualObjects, lockIndexes);
        assertTrue(lockIndexes.get(lockIndexes.size() - 1) == locks.size(), "locks size doesn't match %s,%s", lockIndexes, locks);
        int valueCount = 0;
        for (VirtualObjectNode virtual : virtualObjects) {
            valueCount += virtual.entryCount();
        }
        assertTrue(values.size() == valueCount, "values size doesn't match");
        assertTrue(virtualObjects.size() == ensureVirtual.size(), "ensureVirtual size doesn't match");
        return super.verifyNode();
    }

    @Override
    public void lower(LoweringTool tool) {
        for (int i = 0; i < virtualObjects.size(); i++) {
            if (ensureVirtual.get(i)) {
                EnsureVirtualizedNode.ensureVirtualFailure(this, virtualObjects.get(i).stamp(NodeView.DEFAULT));
            }
        }
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        if (locks == null) { // possible if inspected during initialization in GraphDecoder
            return null;
        }
        if (locks.isEmpty()) {
            return LocationIdentity.init();
        }
        return LocationIdentity.any();
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return getKilledLocationIdentity();
    }

    @Override
    public void afterClone(Node other) {
        lockIndexes = new ArrayList<>(lockIndexes);
    }

    public void addLocks(List<MonitorIdNode> monitorIds) {
        locks.addAll(monitorIds);
        lockIndexes.add(locks.size());
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        int pos = 0;
        for (int i = 0; i < virtualObjects.size(); i++) {
            VirtualObjectNode virtualObject = virtualObjects.get(i);
            int entryCount = virtualObject.entryCount();
            /*
             * n.b. the node source position of virtualObject will have been set when it was
             * created.
             */
            tool.createVirtualObject(virtualObject, values.subList(pos, pos + entryCount).toArray(new ValueNode[entryCount]), getLocks(i), virtualObject.getNodeSourcePosition(), ensureVirtual.get(i));
            pos += entryCount;
        }
        tool.delete();
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        Map<Object, Object> properties = super.getDebugProperties(map);
        if (virtualObjects == null) { // possible if inspected during initialization in GraphDecoder
            return properties;
        }
        int valuePos = 0;
        for (int objIndex = 0; objIndex < virtualObjects.size(); objIndex++) {
            VirtualObjectNode virtual = virtualObjects.get(objIndex);
            if (virtual == null) {
                // Could occur in invalid graphs
                properties.put("object(" + objIndex + ")", "null");
                continue;
            }
            StringBuilder s = new StringBuilder();
            s.append(virtual.type().toJavaName(false)).append("[");
            for (int i = 0; i < virtual.entryCount(); i++) {
                ValueNode value = values.get(valuePos++);
                s.append(i == 0 ? "" : ",").append(value == null ? "_" : value.toString(Verbosity.Id));
            }
            s.append("]");
            if (!getLocks(objIndex).isEmpty()) {
                s.append(" locked(").append(getLocks(objIndex)).append(")");
            }
            properties.put("object(" + virtual.toString(Verbosity.Id) + ")", s.toString());
        }
        return properties;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        boolean[] used = new boolean[virtualObjects.size()];
        int usedCount = 0;
        for (AllocatedObjectNode addObject : usages().filter(AllocatedObjectNode.class)) {
            int index = virtualObjects.indexOf(addObject.getVirtualObject());
            assert !used[index];
            used[index] = true;
            usedCount++;
        }
        if (usedCount == 0) {
            List<Node> inputSnapshot = inputs().snapshot();
            graph().removeFixed(this);
            for (Node input : inputSnapshot) {
                tool.removeIfUnused(input);
            }
            return;
        }
        boolean progress;
        do {
            progress = false;
            int valuePos = 0;
            for (int objIndex = 0; objIndex < virtualObjects.size(); objIndex++) {
                VirtualObjectNode virtualObject = virtualObjects.get(objIndex);
                if (used[objIndex]) {
                    for (int i = 0; i < virtualObject.entryCount(); i++) {
                        int index = virtualObjects.indexOf(values.get(valuePos + i));
                        if (index != -1 && !used[index]) {
                            progress = true;
                            used[index] = true;
                            usedCount++;
                        }
                    }
                }
                valuePos += virtualObject.entryCount();
            }

        } while (progress);

        if (usedCount < virtualObjects.size()) {
            List<VirtualObjectNode> newVirtualObjects = new ArrayList<>(usedCount);
            List<MonitorIdNode> newLocks = new ArrayList<>(usedCount);
            ArrayList<Integer> newLockIndexes = new ArrayList<>(usedCount + 1);
            ArrayList<Boolean> newEnsureVirtual = new ArrayList<>(usedCount);
            newLockIndexes.add(0);
            List<ValueNode> newValues = new ArrayList<>();
            int valuePos = 0;
            for (int objIndex = 0; objIndex < virtualObjects.size(); objIndex++) {
                VirtualObjectNode virtualObject = virtualObjects.get(objIndex);
                if (used[objIndex]) {
                    newVirtualObjects.add(virtualObject);
                    newLocks.addAll(getLocks(objIndex));
                    newLockIndexes.add(newLocks.size());
                    newValues.addAll(values.subList(valuePos, valuePos + virtualObject.entryCount()));
                    newEnsureVirtual.add(ensureVirtual.get(objIndex));
                }
                valuePos += virtualObject.entryCount();
            }
            virtualObjects.clear();
            virtualObjects.addAll(newVirtualObjects);
            locks.clear();
            locks.addAll(newLocks);
            values.clear();
            values.addAll(newValues);
            lockIndexes = newLockIndexes;
            ensureVirtual = newEnsureVirtual;
        }
    }

    @Override
    public NodeCycles estimatedNodeCycles() {
        int fieldWriteCount = getFieldWriteCount();
        if (fieldWriteCount == -1) {
            return CYCLES_UNKNOWN;
        }
        int rawValueWrites = NodeCycles.compute(WriteNode.TYPE.cycles(), fieldWriteCount).value;
        int rawValuesTlabBumps = AbstractNewObjectNode.TYPE.cycles().value;
        return NodeCycles.compute(rawValueWrites + rawValuesTlabBumps);
    }

    @Override
    protected NodeSize dynamicNodeSizeEstimate() {
        int fieldWriteCount = getFieldWriteCount();
        if (fieldWriteCount == -1) {
            return SIZE_UNKNOWN;
        }
        int rawValueWrites = NodeSize.compute(WriteNode.TYPE.size(), fieldWriteCount).value;
        int rawValuesTlabBumps = AbstractNewObjectNode.TYPE.size().value;
        return NodeSize.compute(rawValueWrites + rawValuesTlabBumps);
    }

    private int getFieldWriteCount() {
        List<VirtualObjectNode> v = getVirtualObjects();
        if (v == null) {
            return -1;
        }
        int fieldWriteCount = 0;
        for (int i = 0; i < v.size(); i++) {
            VirtualObjectNode node = v.get(i);
            if (node == null) {
                return -1;
            }
            fieldWriteCount += node.entryCount();
        }
        return fieldWriteCount;
    }
}
