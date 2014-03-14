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
import com.oracle.graal.java.BciBlockMapping.Block;
import com.oracle.graal.java.BciBlockMapping.LocalLiveness;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;

public class FrameStateBuilder {

    private static final ValueNode[] EMPTY_ARRAY = new ValueNode[0];
    private static final MonitorIdNode[] EMPTY_MONITOR_ARRAY = new MonitorIdNode[0];

    private final ResolvedJavaMethod method;
    private final StructuredGraph graph;

    private final ValueNode[] locals;
    private final ValueNode[] stack;
    private ValueNode[] lockedObjects;
    private MonitorIdNode[] monitorIds;

    private int stackSize;

    /**
     * @see BytecodeFrame#rethrowException
     */
    private boolean rethrowException;

    public FrameStateBuilder(ResolvedJavaMethod method, StructuredGraph graph, boolean eagerResolve) {
        assert graph != null;
        this.method = method;
        this.graph = graph;
        this.locals = new ValueNode[method.getMaxLocals()];
        // we always need at least one stack slot (for exceptions)
        this.stack = new ValueNode[Math.max(1, method.getMaxStackSize())];
        this.lockedObjects = EMPTY_ARRAY;
        this.monitorIds = EMPTY_MONITOR_ARRAY;

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
            Kind kind = type.getKind().getStackKind();
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

    private FrameStateBuilder(FrameStateBuilder other) {
        method = other.method;
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

    public FrameStateBuilder copy() {
        return new FrameStateBuilder(this);
    }

    public boolean isCompatibleWith(FrameStateBuilder other) {
        assert method.equals(other.method) && graph == other.graph && localsSize() == other.localsSize() : "Can only compare frame states of the same method";
        assert lockedObjects.length == monitorIds.length && other.lockedObjects.length == other.monitorIds.length : "mismatch between lockedObjects and monitorIds";

        if (stackSize() != other.stackSize()) {
            return false;
        }
        for (int i = 0; i < stackSize(); i++) {
            ValueNode x = stackAt(i);
            ValueNode y = other.stackAt(i);
            if (x != y && (x == null || x.isDeleted() || y == null || y.isDeleted() || x.kind() != y.kind())) {
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

    public void merge(MergeNode block, FrameStateBuilder other) {
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
            if (otherValue == null || otherValue.isDeleted() || currentValue.kind() != otherValue.kind()) {
                propagateDelete((PhiNode) currentValue);
                return null;
            }
            ((PhiNode) currentValue).addInput(otherValue);
            return currentValue;

        } else if (currentValue != otherValue) {
            assert !(block instanceof LoopBeginNode) : "Phi functions for loop headers are create eagerly for all locals and stack slots";
            if (otherValue == null || otherValue.isDeleted() || currentValue.kind() != otherValue.kind()) {
                return null;
            }

            PhiNode phi = graph.addWithoutUnique(new PhiNode(currentValue.kind(), block));
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
        assert node instanceof PhiNode || node instanceof ProxyNode;
        if (node.isDeleted()) {
            return;
        }
        // Collect all phi functions that use this phi so that we can delete them recursively (after
        // we delete ourselves to avoid circles).
        List<FloatingNode> propagateUsages = node.usages().filter(FloatingNode.class).filter(isA(PhiNode.class).or(ProxyNode.class)).snapshot();

        // Remove the phi function from all FrameStates where it is used and then delete it.
        assert node.usages().filter(isNotA(FrameState.class).nor(PhiNode.class).nor(ProxyNode.class)).isEmpty() : "phi function that gets deletes must only be used in frame states";
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

    public void insertLoopProxies(LoopExitNode loopExit, FrameStateBuilder loopEntryState) {
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

    private PhiNode createLoopPhi(MergeNode block, ValueNode value) {
        if (value == null) {
            return null;
        }
        assert !block.isPhiAtMerge(value) : "phi function for this block already created";

        PhiNode phi = graph.addWithoutUnique(new PhiNode(value.kind(), block));
        phi.addInput(value);
        return phi;
    }

    public void cleanupDeletedPhis() {
        for (int i = 0; i < localsSize(); i++) {
            if (localAt(i) != null && localAt(i).isDeleted()) {
                assert localAt(i) instanceof PhiNode || localAt(i) instanceof ProxyNode : "Only phi and value proxies can be deleted during parsing: " + localAt(i);
                storeLocal(i, null);
            }
        }
    }

    public void clearNonLiveLocals(Block block, LocalLiveness liveness, boolean liveIn) {
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

    /**
     * Returns the size of the local variables.
     * 
     * @return the size of the local variables
     */
    public int localsSize() {
        return locals.length;
    }

    /**
     * Gets the current size (height) of the stack.
     */
    public int stackSize() {
        return stackSize;
    }

    /**
     * Gets the value in the local variables at the specified index, without any sanity checking.
     * 
     * @param i the index into the locals
     * @return the instruction that produced the value for the specified local
     */
    public final ValueNode localAt(int i) {
        return locals[i];
    }

    /**
     * Get the value on the stack at the specified stack index.
     * 
     * @param i the index into the stack, with {@code 0} being the bottom of the stack
     * @return the instruction at the specified position in the stack
     */
    public final ValueNode stackAt(int i) {
        return stack[i];
    }

    /**
     * Adds a locked monitor to this frame state.
     * 
     * @param object the object whose monitor will be locked.
     */
    public void pushLock(ValueNode object, MonitorIdNode monitorId) {
        assert object.isAlive() && object.kind() == Kind.Object : "unexpected value: " + object;
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

    /**
     * Loads the local variable at the specified index, checking that the returned value is non-null
     * and that two-stack values are properly handled.
     * 
     * @param i the index of the local variable to load
     * @return the instruction that produced the specified local
     */
    public ValueNode loadLocal(int i) {
        ValueNode x = locals[i];
        assert !x.isDeleted();
        assert !isTwoSlot(x.kind()) || locals[i + 1] == null;
        assert i == 0 || locals[i - 1] == null || !isTwoSlot(locals[i - 1].kind());
        return x;
    }

    /**
     * Stores a given local variable at the specified index. If the value occupies
     * {@linkplain FrameStateBuilder#isTwoSlot(Kind) two slots}, then the next local variable index
     * is also overwritten.
     * 
     * @param i the index at which to store
     * @param x the instruction which produces the value for the local
     */
    public void storeLocal(int i, ValueNode x) {
        assert x == null || x.isAlive() && x.kind() != Kind.Void && x.kind() != Kind.Illegal : "unexpected value: " + x;
        locals[i] = x;
        if (x != null && isTwoSlot(x.kind())) {
            // if this is a double word, then kill i+1
            locals[i + 1] = null;
        }
        if (x != null && i > 0) {
            ValueNode p = locals[i - 1];
            if (p != null && isTwoSlot(p.kind())) {
                // if there was a double word at i - 1, then kill it
                locals[i - 1] = null;
            }
        }
    }

    public void storeStack(int i, ValueNode x) {
        assert x == null || x.isAlive() && (stack[i] == null || x.kind() == stack[i].kind()) : "Method does not handle changes from one-slot to two-slot values or non-alive values";
        stack[i] = x;
    }

    /**
     * Pushes an instruction onto the stack with the expected type.
     * 
     * @param kind the type expected for this instruction
     * @param x the instruction to push onto the stack
     */
    public void push(Kind kind, ValueNode x) {
        assert x.isAlive() && x.kind() != Kind.Void && x.kind() != Kind.Illegal;
        xpush(assertKind(kind, x));
        if (isTwoSlot(kind)) {
            xpush(null);
        }
    }

    /**
     * Pushes a value onto the stack without checking the type.
     * 
     * @param x the instruction to push onto the stack
     */
    public void xpush(ValueNode x) {
        assert x == null || (x.isAlive() && x.kind() != Kind.Void && x.kind() != Kind.Illegal);
        stack[stackSize++] = x;
    }

    /**
     * Pushes a value onto the stack and checks that it is an int.
     * 
     * @param x the instruction to push onto the stack
     */
    public void ipush(ValueNode x) {
        xpush(assertInt(x));
    }

    /**
     * Pushes a value onto the stack and checks that it is a float.
     * 
     * @param x the instruction to push onto the stack
     */
    public void fpush(ValueNode x) {
        xpush(assertFloat(x));
    }

    /**
     * Pushes a value onto the stack and checks that it is an object.
     * 
     * @param x the instruction to push onto the stack
     */
    public void apush(ValueNode x) {
        xpush(assertObject(x));
    }

    /**
     * Pushes a value onto the stack and checks that it is a long.
     * 
     * @param x the instruction to push onto the stack
     */
    public void lpush(ValueNode x) {
        xpush(assertLong(x));
        xpush(null);
    }

    /**
     * Pushes a value onto the stack and checks that it is a double.
     * 
     * @param x the instruction to push onto the stack
     */
    public void dpush(ValueNode x) {
        xpush(assertDouble(x));
        xpush(null);
    }

    public void pushReturn(Kind kind, ValueNode x) {
        if (kind != Kind.Void) {
            push(kind.getStackKind(), x);
        }
    }

    /**
     * Pops an instruction off the stack with the expected type.
     * 
     * @param kind the expected type
     * @return the instruction on the top of the stack
     */
    public ValueNode pop(Kind kind) {
        assert kind != Kind.Void;
        if (isTwoSlot(kind)) {
            xpop();
        }
        return assertKind(kind, xpop());
    }

    /**
     * Pops a value off of the stack without checking the type.
     * 
     * @return x the instruction popped off the stack
     */
    public ValueNode xpop() {
        ValueNode result = stack[--stackSize];
        assert result == null || !result.isDeleted();
        return result;
    }

    /**
     * Pops a value off of the stack and checks that it is an int.
     * 
     * @return x the instruction popped off the stack
     */
    public ValueNode ipop() {
        return assertInt(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is a float.
     * 
     * @return x the instruction popped off the stack
     */
    public ValueNode fpop() {
        return assertFloat(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is an object.
     * 
     * @return x the instruction popped off the stack
     */
    public ValueNode apop() {
        return assertObject(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is a long.
     * 
     * @return x the instruction popped off the stack
     */
    public ValueNode lpop() {
        assertHigh(xpop());
        return assertLong(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is a double.
     * 
     * @return x the instruction popped off the stack
     */
    public ValueNode dpop() {
        assertHigh(xpop());
        return assertDouble(xpop());
    }

    /**
     * Pop the specified number of slots off of this stack and return them as an array of
     * instructions.
     * 
     * @return an array containing the arguments off of the stack
     */
    public ValueNode[] popArguments(int slotSize, int argSize) {
        int base = stackSize - slotSize;
        ValueNode[] r = new ValueNode[argSize];
        int argIndex = 0;
        int stackindex = 0;
        while (stackindex < slotSize) {
            ValueNode element = stack[base + stackindex];
            assert element != null;
            r[argIndex++] = element;
            stackindex += stackSlots(element.kind());
        }
        stackSize = base;
        return r;
    }

    /**
     * Peeks an element from the operand stack.
     * 
     * @param argumentNumber The number of the argument, relative from the top of the stack (0 =
     *            top). Long and double arguments only count as one argument, i.e., null-slots are
     *            ignored.
     * @return The peeked argument.
     */
    public ValueNode peek(int argumentNumber) {
        int idx = stackSize() - 1;
        for (int i = 0; i < argumentNumber; i++) {
            if (stackAt(idx) == null) {
                idx--;
                assert isTwoSlot(stackAt(idx).kind());
            }
            idx--;
        }
        return stackAt(idx);
    }

    /**
     * Clears all values on this stack.
     */
    public void clearStack() {
        stackSize = 0;
    }

    public static int stackSlots(Kind kind) {
        return isTwoSlot(kind) ? 2 : 1;
    }

    public static boolean isTwoSlot(Kind kind) {
        assert kind != Kind.Void && kind != Kind.Illegal;
        return kind == Kind.Long || kind == Kind.Double;
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
