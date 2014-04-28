/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.java;

import static com.oracle.graal.graph.iterators.NodePredicates.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.Node.Verbosity;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.util.*;

public class HIRFrameStateBuilder extends AbstractFrameStateBuilder<ValueNode, HIRFrameStateBuilder> {

    private static final ValueNode[] EMPTY_ARRAY = new ValueNode[0];
    private static final MonitorIdNode[] EMPTY_MONITOR_ARRAY = new MonitorIdNode[0];

    private MonitorIdNode[] monitorIds;
    private final StructuredGraph graph;

    public HIRFrameStateBuilder(ResolvedJavaMethod method, StructuredGraph graph, boolean eagerResolve) {
        super(method, new ValueNode[method.getMaxLocals()], new ValueNode[Math.max(1, method.getMaxStackSize())], EMPTY_ARRAY);

        assert graph != null;

        this.monitorIds = EMPTY_MONITOR_ARRAY;
        this.graph = graph;

        int javaIndex = 0;
        int index = 0;
        if (!method.isStatic()) {
            // add the receiver
            ParameterNode receiver = graph.unique(new ParameterNode(javaIndex, StampFactory.declaredNonNull(method.getDeclaringClass())));
            storeLocal(javaIndex, receiver);
            javaIndex = 1;
            index = 1;
        }
        Signature sig = method.getSignature();
        int max = sig.getParameterCount(false);
        ResolvedJavaType accessingClass = method.getDeclaringClass();
        for (int i = 0; i < max; i++) {
            JavaType type = sig.getParameterType(i, accessingClass);
            if (eagerResolve) {
                type = type.resolve(accessingClass);
            }
            Kind kind = type.getKind();
            Stamp stamp;
            if (kind == Kind.Object && type instanceof ResolvedJavaType) {
                stamp = StampFactory.declared((ResolvedJavaType) type);
            } else {
                stamp = StampFactory.forKind(kind);
            }
            ParameterNode param = graph.unique(new ParameterNode(index, stamp));
            storeLocal(javaIndex, param);
            javaIndex += stackSlots(kind);
            index++;
        }
    }

    private HIRFrameStateBuilder(HIRFrameStateBuilder other) {
        super(other);
        assert other.graph != null;
        graph = other.graph;
        monitorIds = other.monitorIds == EMPTY_MONITOR_ARRAY ? EMPTY_MONITOR_ARRAY : other.monitorIds.clone();

        assert locals.length == method.getMaxLocals();
        assert stack.length == Math.max(1, method.getMaxStackSize());
        assert lockedObjects.length == monitorIds.length;
    }

    @Override
    protected ValueNode[] getEmtpyArray() {
        return EMPTY_ARRAY;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[locals: [");
        for (int i = 0; i < locals.length; i++) {
            sb.append(i == 0 ? "" : ",").append(locals[i] == null ? "_" : locals[i].toString(Verbosity.Id));
        }
        sb.append("] stack: [");
        for (int i = 0; i < stackSize; i++) {
            sb.append(i == 0 ? "" : ",").append(stack[i] == null ? "_" : stack[i].toString(Verbosity.Id));
        }
        sb.append("] locks: [");
        for (int i = 0; i < lockedObjects.length; i++) {
            sb.append(i == 0 ? "" : ",").append(lockedObjects[i].toString(Verbosity.Id)).append(" / ").append(monitorIds[i].toString(Verbosity.Id));
        }
        sb.append("]");
        if (rethrowException) {
            sb.append(" rethrowException");
        }
        sb.append("]");
        return sb.toString();
    }

    public FrameState create(int bci) {
        return graph.add(new FrameState(method, bci, locals, Arrays.asList(stack).subList(0, stackSize), lockedObjects, monitorIds, rethrowException, false));
    }

    @Override
    public HIRFrameStateBuilder copy() {
        return new HIRFrameStateBuilder(this);
    }

    @Override
    public boolean isCompatibleWith(HIRFrameStateBuilder other) {
        assert method.equals(other.method) && graph == other.graph && localsSize() == other.localsSize() : "Can only compare frame states of the same method";
        assert lockedObjects.length == monitorIds.length && other.lockedObjects.length == other.monitorIds.length : "mismatch between lockedObjects and monitorIds";

        if (stackSize() != other.stackSize()) {
            return false;
        }
        for (int i = 0; i < stackSize(); i++) {
            ValueNode x = stackAt(i);
            ValueNode y = other.stackAt(i);
            if (x != y && (x == null || x.isDeleted() || y == null || y.isDeleted() || x.getKind() != y.getKind())) {
                return false;
            }
        }
        if (lockedObjects.length != other.lockedObjects.length) {
            return false;
        }
        for (int i = 0; i < lockedObjects.length; i++) {
            if (GraphUtil.originalValue(lockedObjects[i]) != GraphUtil.originalValue(other.lockedObjects[i]) || monitorIds[i] != other.monitorIds[i]) {
                throw new BailoutException("unbalanced monitors");
            }
        }
        return true;
    }

    public void merge(MergeNode block, HIRFrameStateBuilder other) {
        assert isCompatibleWith(other);

        for (int i = 0; i < localsSize(); i++) {
            storeLocal(i, merge(localAt(i), other.localAt(i), block));
        }
        for (int i = 0; i < stackSize(); i++) {
            storeStack(i, merge(stackAt(i), other.stackAt(i), block));
        }
        for (int i = 0; i < lockedObjects.length; i++) {
            lockedObjects[i] = merge(lockedObjects[i], other.lockedObjects[i], block);
            assert monitorIds[i] == other.monitorIds[i];
        }
    }

    private ValueNode merge(ValueNode currentValue, ValueNode otherValue, MergeNode block) {
        if (currentValue == null || currentValue.isDeleted()) {
            return null;

        } else if (block.isPhiAtMerge(currentValue)) {
            if (otherValue == null || otherValue.isDeleted() || currentValue.getKind() != otherValue.getKind()) {
                propagateDelete((ValuePhiNode) currentValue);
                return null;
            }
            ((PhiNode) currentValue).addInput(otherValue);
            return currentValue;

        } else if (currentValue != otherValue) {
            assert !(block instanceof LoopBeginNode) : "Phi functions for loop headers are create eagerly for all locals and stack slots";
            if (otherValue == null || otherValue.isDeleted() || currentValue.getKind() != otherValue.getKind()) {
                return null;
            }

            ValuePhiNode phi = graph.addWithoutUnique(new ValuePhiNode(currentValue.stamp().unrestricted(), block));
            for (int i = 0; i < block.phiPredecessorCount(); i++) {
                phi.addInput(currentValue);
            }
            phi.addInput(otherValue);
            assert phi.valueCount() == block.phiPredecessorCount() + 1 : "valueCount=" + phi.valueCount() + " predSize= " + block.phiPredecessorCount();
            return phi;

        } else {
            return currentValue;
        }
    }

    private void propagateDelete(FloatingNode node) {
        assert node instanceof ValuePhiNode || node instanceof ProxyNode;
        if (node.isDeleted()) {
            return;
        }
        // Collect all phi functions that use this phi so that we can delete them recursively (after
        // we delete ourselves to avoid circles).
        List<FloatingNode> propagateUsages = node.usages().filter(FloatingNode.class).filter(isA(ValuePhiNode.class).or(ProxyNode.class)).snapshot();

        // Remove the phi function from all FrameStates where it is used and then delete it.
        assert node.usages().filter(isNotA(FrameState.class).nor(ValuePhiNode.class).nor(ProxyNode.class)).isEmpty() : "phi function that gets deletes must only be used in frame states";
        node.replaceAtUsages(null);
        node.safeDelete();

        for (FloatingNode phiUsage : propagateUsages) {
            propagateDelete(phiUsage);
        }
    }

    public void insertLoopPhis(LoopBeginNode loopBegin) {
        for (int i = 0; i < localsSize(); i++) {
            storeLocal(i, createLoopPhi(loopBegin, localAt(i)));
        }
        for (int i = 0; i < stackSize(); i++) {
            storeStack(i, createLoopPhi(loopBegin, stackAt(i)));
        }
        for (int i = 0; i < lockedObjects.length; i++) {
            lockedObjects[i] = createLoopPhi(loopBegin, lockedObjects[i]);
        }
    }

    public void insertLoopProxies(LoopExitNode loopExit, HIRFrameStateBuilder loopEntryState) {
        for (int i = 0; i < localsSize(); i++) {
            ValueNode value = localAt(i);
            if (value != null && (!loopEntryState.contains(value) || loopExit.loopBegin().isPhiAtMerge(value))) {
                Debug.log(" inserting proxy for %s", value);
                storeLocal(i, ProxyNode.forValue(value, loopExit, graph));
            }
        }
        for (int i = 0; i < stackSize(); i++) {
            ValueNode value = stackAt(i);
            if (value != null && (!loopEntryState.contains(value) || loopExit.loopBegin().isPhiAtMerge(value))) {
                Debug.log(" inserting proxy for %s", value);
                storeStack(i, ProxyNode.forValue(value, loopExit, graph));
            }
        }
        for (int i = 0; i < lockedObjects.length; i++) {
            ValueNode value = lockedObjects[i];
            if (value != null && (!loopEntryState.contains(value) || loopExit.loopBegin().isPhiAtMerge(value))) {
                Debug.log(" inserting proxy for %s", value);
                lockedObjects[i] = ProxyNode.forValue(value, loopExit, graph);
            }
        }
    }

    public void insertProxies(BeginNode begin) {
        for (int i = 0; i < localsSize(); i++) {
            ValueNode value = localAt(i);
            if (value != null) {
                Debug.log(" inserting proxy for %s", value);
                storeLocal(i, ProxyNode.forValue(value, begin, graph));
            }
        }
        for (int i = 0; i < stackSize(); i++) {
            ValueNode value = stackAt(i);
            if (value != null) {
                Debug.log(" inserting proxy for %s", value);
                storeStack(i, ProxyNode.forValue(value, begin, graph));
            }
        }
        for (int i = 0; i < lockedObjects.length; i++) {
            ValueNode value = lockedObjects[i];
            if (value != null) {
                Debug.log(" inserting proxy for %s", value);
                lockedObjects[i] = ProxyNode.forValue(value, begin, graph);
            }
        }
    }

    private ValuePhiNode createLoopPhi(MergeNode block, ValueNode value) {
        if (value == null) {
            return null;
        }
        assert !block.isPhiAtMerge(value) : "phi function for this block already created";

        ValuePhiNode phi = graph.addWithoutUnique(new ValuePhiNode(value.stamp().unrestricted(), block));
        phi.addInput(value);
        return phi;
    }

    public void cleanupDeletedPhis() {
        for (int i = 0; i < localsSize(); i++) {
            if (localAt(i) != null && localAt(i).isDeleted()) {
                assert localAt(i) instanceof ValuePhiNode || localAt(i) instanceof ProxyNode : "Only phi and value proxies can be deleted during parsing: " + localAt(i);
                storeLocal(i, null);
            }
        }
    }

    /**
     * Adds a locked monitor to this frame state.
     *
     * @param object the object whose monitor will be locked.
     */
    public void pushLock(ValueNode object, MonitorIdNode monitorId) {
        assert object.isAlive() && object.getKind() == Kind.Object : "unexpected value: " + object;
        lockedObjects = Arrays.copyOf(lockedObjects, lockedObjects.length + 1);
        monitorIds = Arrays.copyOf(monitorIds, monitorIds.length + 1);
        lockedObjects[lockedObjects.length - 1] = object;
        monitorIds[monitorIds.length - 1] = monitorId;
        assert lockedObjects.length == monitorIds.length;
    }

    /**
     * Removes a locked monitor from this frame state.
     *
     * @return the object whose monitor was removed from the locks list.
     */
    public ValueNode popLock() {
        try {
            return lockedObjects[lockedObjects.length - 1];
        } finally {
            lockedObjects = lockedObjects.length == 1 ? EMPTY_ARRAY : Arrays.copyOf(lockedObjects, lockedObjects.length - 1);
            monitorIds = monitorIds.length == 1 ? EMPTY_MONITOR_ARRAY : Arrays.copyOf(monitorIds, monitorIds.length - 1);
            assert lockedObjects.length == monitorIds.length;
        }
    }

    public MonitorIdNode peekMonitorId() {
        return monitorIds[monitorIds.length - 1];
    }

    /**
     * @return the current lock depth
     */
    public int lockDepth() {
        assert lockedObjects.length == monitorIds.length;
        return lockedObjects.length;
    }

    public boolean contains(ValueNode value) {
        for (int i = 0; i < localsSize(); i++) {
            if (localAt(i) == value) {
                return true;
            }
        }
        for (int i = 0; i < stackSize(); i++) {
            if (stackAt(i) == value) {
                return true;
            }
        }
        assert lockedObjects.length == monitorIds.length;
        for (int i = 0; i < lockedObjects.length; i++) {
            if (lockedObjects[i] == value || monitorIds[i] == value) {
                return true;
            }
        }
        return false;
    }
}
