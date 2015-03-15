/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderPlugin.*;
import com.oracle.graal.java.AbstractBytecodeParser.IntrinsicContext;
import com.oracle.graal.java.BciBlockMapping.BciBlock;
import com.oracle.graal.java.GraphBuilderPhase.Instance.BytecodeParser;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.util.*;

public final class HIRFrameStateBuilder {

    private static final ValueNode[] EMPTY_ARRAY = new ValueNode[0];
    private static final MonitorIdNode[] EMPTY_MONITOR_ARRAY = new MonitorIdNode[0];

    protected final BytecodeParser parser;
    protected final ResolvedJavaMethod method;
    protected int stackSize;
    protected final ValueNode[] locals;
    protected final ValueNode[] stack;
    protected ValueNode[] lockedObjects;

    /**
     * @see BytecodeFrame#rethrowException
     */
    protected boolean rethrowException;

    private MonitorIdNode[] monitorIds;
    private final StructuredGraph graph;
    private FrameState outerFrameState;

    /**
     * Creates a new frame state builder for the given method and the given target graph.
     *
     * @param method the method whose frame is simulated
     * @param graph the target graph of Graal nodes created by the builder
     */
    public HIRFrameStateBuilder(BytecodeParser parser, ResolvedJavaMethod method, StructuredGraph graph) {
        this.parser = parser;
        this.method = method;
        this.locals = allocateArray(method.getMaxLocals());
        this.stack = allocateArray(Math.max(1, method.getMaxStackSize()));
        this.lockedObjects = allocateArray(0);

        assert graph != null;

        this.monitorIds = EMPTY_MONITOR_ARRAY;
        this.graph = graph;
    }

    public void initializeFromArgumentsArray(ValueNode[] arguments) {

        int javaIndex = 0;
        int index = 0;
        if (!method.isStatic()) {
            // set the receiver
            locals[javaIndex] = arguments[index];
            javaIndex = 1;
            index = 1;
        }
        Signature sig = method.getSignature();
        int max = sig.getParameterCount(false);
        for (int i = 0; i < max; i++) {
            Kind kind = sig.getParameterKind(i);
            locals[javaIndex] = arguments[index];
            javaIndex += kind.getSlotCount();
            index++;
        }
    }

    public void initializeForMethodStart(boolean eagerResolve, ParameterPlugin parameterPlugin) {

        int javaIndex = 0;
        int index = 0;
        if (!method.isStatic()) {
            // add the receiver
            FloatingNode receiver = null;
            Stamp receiverStamp = StampFactory.declaredNonNull(method.getDeclaringClass());
            if (parameterPlugin != null) {
                receiver = parameterPlugin.interceptParameter(parser, index, receiverStamp);
            }
            if (receiver == null) {
                receiver = new ParameterNode(javaIndex, receiverStamp);
            }
            locals[javaIndex] = graph.unique(receiver);
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
            FloatingNode param = null;
            if (parameterPlugin != null) {
                param = parameterPlugin.interceptParameter(parser, index, stamp);
            }
            if (param == null) {
                param = new ParameterNode(index, stamp);
            }
            locals[javaIndex] = graph.unique(param);
            javaIndex += kind.getSlotCount();
            index++;
        }
    }

    private HIRFrameStateBuilder(HIRFrameStateBuilder other) {
        this.parser = other.parser;
        this.method = other.method;
        this.stackSize = other.stackSize;
        this.locals = other.locals.clone();
        this.stack = other.stack.clone();
        this.lockedObjects = other.lockedObjects.length == 0 ? other.lockedObjects : other.lockedObjects.clone();
        this.rethrowException = other.rethrowException;

        assert locals.length == method.getMaxLocals();
        assert stack.length == Math.max(1, method.getMaxStackSize());

        assert other.graph != null;
        graph = other.graph;
        monitorIds = other.monitorIds.length == 0 ? other.monitorIds : other.monitorIds.clone();

        assert locals.length == method.getMaxLocals();
        assert stack.length == Math.max(1, method.getMaxStackSize());
        assert lockedObjects.length == monitorIds.length;
    }

    private static ValueNode[] allocateArray(int length) {
        return length == 0 ? EMPTY_ARRAY : new ValueNode[length];
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
        BytecodeParser parent = parser.getParent();
        if (parser.parsingReplacement()) {
            IntrinsicContext intrinsic = parser.replacementContext.asIntrinsic();
            if (intrinsic != null) {
                assert parent != null : "intrinsics can only be processed in context of a caller";

                // We're somewhere in an intrinsic. In this case, we want a frame state
                // that will restart the interpreter just before the intrinsified
                // invocation.
                return intrinsic.getInvokeStateBefore(parent);
            }
        }
        return create(bci, parent, false);
    }

    public FrameState create(int bci, BytecodeParser parent, boolean duringCall) {
        if (outerFrameState == null && parent != null) {
            outerFrameState = parent.getFrameState().create(parent.bci());
            if (parser.parsingReplacement()) {
                IntrinsicContext intrinsic = parser.replacementContext.asIntrinsic();
                if (intrinsic != null) {
                    // A side-effect of creating the frame state in a replacing
                    // parent is that the 'during' frame state is created as well
                    outerFrameState = intrinsic.getInvokeStateDuring();
                }
            }
        }
        if (bci == BytecodeFrame.AFTER_EXCEPTION_BCI && parent != null) {
            FrameState newFrameState = outerFrameState.duplicateModified(outerFrameState.bci, true, Kind.Void, this.peek(0));
            return newFrameState;
        }
        if (bci == BytecodeFrame.INVALID_FRAMESTATE_BCI) {
            throw GraalInternalError.shouldNotReachHere();
            // return graph.add(new FrameState(bci));
        }
        return graph.add(new FrameState(outerFrameState, method, bci, locals, stack, stackSize, lockedObjects, Arrays.asList(monitorIds), rethrowException, duringCall));
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

    public void merge(AbstractMergeNode block, HIRFrameStateBuilder other) {
        assert isCompatibleWith(other);

        for (int i = 0; i < localsSize(); i++) {
            ValueNode curLocal = localAt(i);
            ValueNode mergedLocal = merge(curLocal, other.localAt(i), block);
            if (curLocal != mergedLocal) {
                storeLocal(i, mergedLocal);
            }
        }
        for (int i = 0; i < stackSize(); i++) {
            ValueNode curStack = stackAt(i);
            ValueNode mergedStack = merge(curStack, other.stackAt(i), block);
            if (curStack != mergedStack) {
                storeStack(i, mergedStack);
            }
        }
        for (int i = 0; i < lockedObjects.length; i++) {
            lockedObjects[i] = merge(lockedObjects[i], other.lockedObjects[i], block);
            assert monitorIds[i] == other.monitorIds[i];
        }
    }

    private ValueNode merge(ValueNode currentValue, ValueNode otherValue, AbstractMergeNode block) {
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
            assert !(block instanceof LoopBeginNode) : "Phi functions for loop headers are create eagerly for changed locals and all stack slots";
            if (otherValue == null || otherValue.isDeleted() || currentValue.getKind() != otherValue.getKind()) {
                return null;
            }
            return createValuePhi(currentValue, otherValue, block);
        } else {
            return currentValue;
        }
    }

    private ValuePhiNode createValuePhi(ValueNode currentValue, ValueNode otherValue, AbstractMergeNode block) {
        ValuePhiNode phi = graph.addWithoutUnique(new ValuePhiNode(currentValue.stamp().unrestricted(), block));
        for (int i = 0; i < block.phiPredecessorCount(); i++) {
            phi.addInput(currentValue);
        }
        phi.addInput(otherValue);
        assert phi.valueCount() == block.phiPredecessorCount() + 1;
        return phi;
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

    public void insertLoopPhis(LocalLiveness liveness, int loopId, LoopBeginNode loopBegin) {
        for (int i = 0; i < localsSize(); i++) {
            if (loopBegin.graph().isOSR() || liveness.localIsChangedInLoop(loopId, i)) {
                storeLocal(i, createLoopPhi(loopBegin, localAt(i)));
            }
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

    private ValuePhiNode createLoopPhi(AbstractMergeNode block, ValueNode value) {
        if (value == null) {
            return null;
        }
        assert !block.isPhiAtMerge(value) : "phi function for this block already created";

        ValuePhiNode phi = graph.addWithoutUnique(new ValuePhiNode(value.stamp().unrestricted(), block));
        phi.addInput(value);
        return phi;
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

    public void clearNonLiveLocals(BciBlock block, LocalLiveness liveness, boolean liveIn) {
        /*
         * (lstadler) if somebody is tempted to remove/disable this clearing code: it's possible to
         * remove it for normal compilations, but not for OSR compilations - otherwise dead object
         * slots at the OSR entry aren't cleared. it is also not enough to rely on PiNodes with
         * Kind.Illegal, because the conflicting branch might not have been parsed.
         */
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
    public ValueNode localAt(int i) {
        return locals[i];
    }

    /**
     * Get the value on the stack at the specified stack index.
     *
     * @param i the index into the stack, with {@code 0} being the bottom of the stack
     * @return the instruction at the specified position in the stack
     */
    public ValueNode stackAt(int i) {
        return stack[i];
    }

    /**
     * Gets the value in the lock at the specified index, without any sanity checking.
     *
     * @param i the index into the lock
     * @return the instruction that produced the value for the specified lock
     */
    public ValueNode lockAt(int i) {
        return lockedObjects[i];
    }

    public void storeLock(int i, ValueNode lock) {
        lockedObjects[i] = lock;
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
        assert assertLoadLocal(i, x);
        return x;
    }

    private boolean assertLoadLocal(int i, ValueNode x) {
        assert x != null : i;
        assert parser.parsingReplacement() || (x.getKind().getSlotCount() == 1 || locals[i + 1] == null);
        assert parser.parsingReplacement() || (i == 0 || locals[i - 1] == null || locals[i - 1].getKind().getSlotCount() == 1);
        return true;
    }

    public void storeLocal(int i, ValueNode x) {
        storeLocal(i, x, x == null ? null : x.getKind());
    }

    /**
     * Stores a given local variable at the specified index. If the value occupies two slots, then
     * the next local variable index is also overwritten.
     *
     * @param i the index at which to store
     * @param x the instruction which produces the value for the local
     */
    public void storeLocal(int i, ValueNode x, Kind kind) {
        assert assertStoreLocal(x);
        locals[i] = x;
        if (x != null) {
            if (kind.needsTwoSlots() && !parser.parsingReplacement()) {
                // if this is a double word, then kill i+1
                locals[i + 1] = null;
            }
            if (i > 0 && !parser.parsingReplacement()) {
                ValueNode p = locals[i - 1];
                if (p != null && p.getKind().needsTwoSlots()) {
                    // if there was a double word at i - 1, then kill it
                    locals[i - 1] = null;
                }
            }
        }
    }

    private boolean assertStoreLocal(ValueNode x) {
        assert x == null || parser.parsingReplacement() || (x.getKind() != Kind.Void && x.getKind() != Kind.Illegal) : "unexpected value: " + x;
        return true;
    }

    public void storeStack(int i, ValueNode x) {
        assert assertStoreStack(i, x);
        stack[i] = x;
    }

    private boolean assertStoreStack(int i, ValueNode x) {
        assert x == null || (stack[i] == null || x.getKind() == stack[i].getKind()) : "Method does not handle changes from one-slot to two-slot values or non-alive values";
        return true;
    }

    /**
     * Pushes an instruction onto the stack with the expected type.
     *
     * @param kind the type expected for this instruction
     * @param x the instruction to push onto the stack
     */
    public void push(Kind kind, ValueNode x) {
        assert assertPush(kind, x);
        xpush(x);
        if (kind.needsTwoSlots()) {
            xpush(null);
        }
    }

    private boolean assertPush(Kind kind, ValueNode x) {
        assert parser.parsingReplacement() || (x.getKind() != Kind.Void && x.getKind() != Kind.Illegal);
        assert x != null && (parser.parsingReplacement() || x.getKind() == kind);
        return true;
    }

    /**
     * Pushes a value onto the stack without checking the type.
     *
     * @param x the instruction to push onto the stack
     */
    public void xpush(ValueNode x) {
        assert assertXpush(x);
        stack[stackSize++] = x;
    }

    private boolean assertXpush(ValueNode x) {
        assert parser.parsingReplacement() || (x == null || (x.getKind() != Kind.Void && x.getKind() != Kind.Illegal));
        return true;
    }

    /**
     * Pushes a value onto the stack and checks that it is an int.
     *
     * @param x the instruction to push onto the stack
     */
    public void ipush(ValueNode x) {
        assert assertInt(x);
        xpush(x);
    }

    /**
     * Pushes a value onto the stack and checks that it is a float.
     *
     * @param x the instruction to push onto the stack
     */
    public void fpush(ValueNode x) {
        assert assertFloat(x);
        xpush(x);
    }

    /**
     * Pushes a value onto the stack and checks that it is an object.
     *
     * @param x the instruction to push onto the stack
     */
    public void apush(ValueNode x) {
        assert assertObject(x);
        xpush(x);
    }

    /**
     * Pushes a value onto the stack and checks that it is a long.
     *
     * @param x the instruction to push onto the stack
     */
    public void lpush(ValueNode x) {
        assert assertLong(x);
        xpush(x);
        xpush(null);
    }

    /**
     * Pushes a value onto the stack and checks that it is a double.
     *
     * @param x the instruction to push onto the stack
     */
    public void dpush(ValueNode x) {
        assert assertDouble(x);
        xpush(x);
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
        if (kind.needsTwoSlots()) {
            xpop();
        }
        assert assertPop(kind);
        return xpop();
    }

    private boolean assertPop(Kind kind) {
        assert kind != Kind.Void;
        ValueNode x = xpeek();
        assert x != null && (parser.parsingReplacement() || x.getKind() == kind);
        return true;
    }

    /**
     * Pops a value off of the stack without checking the type.
     *
     * @return x the instruction popped off the stack
     */
    public ValueNode xpop() {
        return stack[--stackSize];
    }

    public ValueNode xpeek() {
        return stack[stackSize - 1];
    }

    /**
     * Pops a value off of the stack and checks that it is an int.
     *
     * @return x the instruction popped off the stack
     */
    public ValueNode ipop() {
        assert assertIntPeek();
        return xpop();
    }

    /**
     * Pops a value off of the stack and checks that it is a float.
     *
     * @return x the instruction popped off the stack
     */
    public ValueNode fpop() {
        assert assertFloatPeek();
        return xpop();
    }

    /**
     * Pops a value off of the stack and checks that it is an object.
     *
     * @return x the instruction popped off the stack
     */
    public ValueNode apop() {
        assert assertObjectPeek();
        return xpop();
    }

    /**
     * Pops a value off of the stack and checks that it is a long.
     *
     * @return x the instruction popped off the stack
     */
    public ValueNode lpop() {
        assert assertHighPeek();
        xpop();
        assert assertLongPeek();
        return xpop();
    }

    /**
     * Pops a value off of the stack and checks that it is a double.
     *
     * @return x the instruction popped off the stack
     */
    public ValueNode dpop() {
        assert assertHighPeek();
        xpop();
        assert assertDoublePeek();
        return xpop();
    }

    /**
     * Pop the specified number of slots off of this stack and return them as an array of
     * instructions.
     *
     * @return an array containing the arguments off of the stack
     */
    public ValueNode[] popArguments(int argSize) {
        ValueNode[] result = allocateArray(argSize);
        int newStackSize = stackSize;
        for (int i = argSize - 1; i >= 0; i--) {
            newStackSize--;
            if (stack[newStackSize] == null) {
                /* Two-slot value. */
                newStackSize--;
                assert stack[newStackSize].getKind().needsTwoSlots();
            } else {
                assert parser.parsingReplacement() || (stack[newStackSize].getKind().getSlotCount() == 1);
            }
            result[i] = stack[newStackSize];
        }
        stackSize = newStackSize;
        return result;
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
                assert stackAt(idx).getKind().needsTwoSlots();
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

    private boolean assertLongPeek() {
        return assertLong(xpeek());
    }

    private static boolean assertLong(ValueNode x) {
        assert x != null && (x.getKind() == Kind.Long);
        return true;
    }

    private boolean assertIntPeek() {
        return assertInt(xpeek());
    }

    private static boolean assertInt(ValueNode x) {
        assert x != null && (x.getKind() == Kind.Int);
        return true;
    }

    private boolean assertFloatPeek() {
        return assertFloat(xpeek());
    }

    private static boolean assertFloat(ValueNode x) {
        assert x != null && (x.getKind() == Kind.Float);
        return true;
    }

    private boolean assertObjectPeek() {
        return assertObject(xpeek());
    }

    private boolean assertObject(ValueNode x) {
        assert x != null && (parser.parsingReplacement() || (x.getKind() == Kind.Object));
        return true;
    }

    private boolean assertDoublePeek() {
        return assertDouble(xpeek());
    }

    private static boolean assertDouble(ValueNode x) {
        assert x != null && (x.getKind() == Kind.Double);
        return true;
    }

    private boolean assertHighPeek() {
        assert xpeek() == null;
        return true;
    }

    @Override
    public int hashCode() {
        int result = hashCode(locals, locals.length);
        result *= 13;
        result += hashCode(stack, this.stackSize);
        return result;
    }

    private static int hashCode(Object[] a, int length) {
        int result = 1;
        for (int i = 0; i < length; ++i) {
            Object element = a[i];
            result = 31 * result + (element == null ? 0 : System.identityHashCode(element));
        }
        return result;
    }

    private static boolean equals(ValueNode[] a, ValueNode[] b, int length) {
        for (int i = 0; i < length; ++i) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object otherObject) {
        if (otherObject instanceof HIRFrameStateBuilder) {
            HIRFrameStateBuilder other = (HIRFrameStateBuilder) otherObject;
            if (!other.method.equals(method)) {
                return false;
            }
            if (other.stackSize != stackSize) {
                return false;
            }
            if (other.parser != parser) {
                return false;
            }
            if (other.rethrowException != rethrowException) {
                return false;
            }
            if (other.graph != graph) {
                return false;
            }
            if (other.locals.length != locals.length) {
                return false;
            }
            return equals(other.locals, locals, locals.length) && equals(other.stack, stack, stackSize) && equals(other.lockedObjects, lockedObjects, lockedObjects.length) &&
                            equals(other.monitorIds, monitorIds, monitorIds.length);
        }
        return false;
    }
}
