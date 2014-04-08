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
import static com.oracle.graal.nodes.ValueNodeUtil.*;
import static java.lang.reflect.Modifier.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.Node.Verbosity;
import com.oracle.graal.java.BciBlockMapping.BciBlock;
import com.oracle.graal.java.BciBlockMapping.LocalLiveness;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;

public class HIRFrameStateBuilder extends AbstractFrameStateBuilder<ValueNode> {

    private static final ValueNode[] EMPTY_ARRAY = new ValueNode[0];
    private static final MonitorIdNode[] EMPTY_MONITOR_ARRAY = new MonitorIdNode[0];

    private final ValueNode[] locals;
    private final ValueNode[] stack;
    private ValueNode[] lockedObjects;
    private MonitorIdNode[] monitorIds;
    private final StructuredGraph graph;

    /**
     * @see BytecodeFrame#rethrowException
     */
    private boolean rethrowException;

    public HIRFrameStateBuilder(ResolvedJavaMethod method, StructuredGraph graph, boolean eagerResolve) {
        super(method);

        assert graph != null;

        this.locals = new ValueNode[method.getMaxLocals()];
        // we always need at least one stack slot (for exceptions)
        this.stack = new ValueNode[Math.max(1, method.getMaxStackSize())];
        this.lockedObjects = EMPTY_ARRAY;
        this.monitorIds = EMPTY_MONITOR_ARRAY;
        this.graph = graph;

        int javaIndex = 0;
        int index = 0;
        if (!isStatic(method.getModifiers())) {
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
        locals = other.locals.clone();
        stack = other.stack.clone();
        lockedObjects = other.lockedObjects == EMPTY_ARRAY ? EMPTY_ARRAY : other.lockedObjects.clone();
        monitorIds = other.monitorIds == EMPTY_MONITOR_ARRAY ? EMPTY_MONITOR_ARRAY : other.monitorIds.clone();
        stackSize = other.stackSize;
        rethrowException = other.rethrowException;

        assert locals.length == method.getMaxLocals();
        assert stack.length == Math.max(1, method.getMaxStackSize());
        assert lockedObjects.length == monitorIds.length;
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

    public HIRFrameStateBuilder copy() {
        return new HIRFrameStateBuilder(this);
    }

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

    public void insertProxies(AbstractBeginNode begin) {
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

    public void clearNonLiveLocals(BciBlock block, LocalLiveness liveness, boolean liveIn) {
        if (liveness == null) {
            return;
        }
        if (liveIn) {
            for (int i = 0; i < locals.length; i++) {
                if (!liveness.localIsLiveIn(block, i)) {
                    locals[i] = null;
                }
            }
        } else {
            for (int i = 0; i < locals.length; i++) {
                if (!liveness.localIsLiveOut(block, i)) {
                    locals[i] = null;
                }
            }
        }
    }

    /**
     * @see BytecodeFrame#rethrowException
     */
    public boolean rethrowException() {
        return rethrowException;
    }

    /**
     * @see BytecodeFrame#rethrowException
     */
    public void setRethrowException(boolean b) {
        rethrowException = b;
    }

    @Override
    public int localsSize() {
        return locals.length;
    }

    @Override
    public ValueNode localAt(int i) {
        return locals[i];
    }

    @Override
    public ValueNode stackAt(int i) {
        return stack[i];
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

    @Override
    public ValueNode loadLocal(int i) {
        ValueNode x = locals[i];
        assert !x.isDeleted();
        assert !isTwoSlot(x.getKind()) || locals[i + 1] == null;
        assert i == 0 || locals[i - 1] == null || !isTwoSlot(locals[i - 1].getKind());
        return x;
    }

    @Override
    public void storeLocal(int i, ValueNode x) {
        assert x == null || x.isAlive() && x.getKind() != Kind.Void && x.getKind() != Kind.Illegal : "unexpected value: " + x;
        locals[i] = x;
        if (x != null && isTwoSlot(x.getKind())) {
            // if this is a double word, then kill i+1
            locals[i + 1] = null;
        }
        if (x != null && i > 0) {
            ValueNode p = locals[i - 1];
            if (p != null && isTwoSlot(p.getKind())) {
                // if there was a double word at i - 1, then kill it
                locals[i - 1] = null;
            }
        }
    }

    @Override
    public void storeStack(int i, ValueNode x) {
        assert x == null || x.isAlive() && (stack[i] == null || x.getKind() == stack[i].getKind()) : "Method does not handle changes from one-slot to two-slot values or non-alive values";
        stack[i] = x;
    }

    @Override
    public void push(Kind kind, ValueNode x) {
        assert x.isAlive() && x.getKind() != Kind.Void && x.getKind() != Kind.Illegal;
        xpush(assertKind(kind, x));
        if (isTwoSlot(kind)) {
            xpush(null);
        }
    }

    @Override
    public void xpush(ValueNode x) {
        assert x == null || (x.isAlive() && x.getKind() != Kind.Void && x.getKind() != Kind.Illegal);
        stack[stackSize++] = x;
    }

    @Override
    public void ipush(ValueNode x) {
        xpush(assertInt(x));
    }

    @Override
    public void fpush(ValueNode x) {
        xpush(assertFloat(x));
    }

    @Override
    public void apush(ValueNode x) {
        xpush(assertObject(x));
    }

    @Override
    public void lpush(ValueNode x) {
        xpush(assertLong(x));
        xpush(null);
    }

    @Override
    public void dpush(ValueNode x) {
        xpush(assertDouble(x));
        xpush(null);
    }

    @Override
    public void pushReturn(Kind kind, ValueNode x) {
        if (kind != Kind.Void) {
            push(kind.getStackKind(), x);
        }
    }

    @Override
    public ValueNode pop(Kind kind) {
        assert kind != Kind.Void;
        if (isTwoSlot(kind)) {
            xpop();
        }
        return assertKind(kind, xpop());
    }

    @Override
    public ValueNode xpop() {
        ValueNode result = stack[--stackSize];
        assert result == null || !result.isDeleted();
        return result;
    }

    @Override
    public ValueNode ipop() {
        return assertInt(xpop());
    }

    @Override
    public ValueNode fpop() {
        return assertFloat(xpop());
    }

    @Override
    public ValueNode apop() {
        return assertObject(xpop());
    }

    @Override
    public ValueNode lpop() {
        assertHigh(xpop());
        return assertLong(xpop());
    }

    @Override
    public ValueNode dpop() {
        assertHigh(xpop());
        return assertDouble(xpop());
    }

    @Override
    public ValueNode[] popArguments(int slotSize, int argSize) {
        int base = stackSize - slotSize;
        ValueNode[] r = new ValueNode[argSize];
        int argIndex = 0;
        int stackindex = 0;
        while (stackindex < slotSize) {
            ValueNode element = stack[base + stackindex];
            assert element != null;
            r[argIndex++] = element;
            stackindex += stackSlots(element.getKind());
        }
        stackSize = base;
        return r;
    }

    @Override
    public ValueNode peek(int argumentNumber) {
        int idx = stackSize() - 1;
        for (int i = 0; i < argumentNumber; i++) {
            if (stackAt(idx) == null) {
                idx--;
                assert isTwoSlot(stackAt(idx).getKind());
            }
            idx--;
        }
        return stackAt(idx);
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
