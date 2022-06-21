/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.operation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Supplier;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.FrameWithoutBoxing;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.operation.OperationNode.SourceInfo;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;

public abstract class OperationBuilder {

    private final boolean isReparse;
    private final ArrayList<OperationNode> builtNodes = new ArrayList<>();
    private final OperationNodes nodes;

    private int buildIndex = 0;
    private BuilderSourceInfo sourceBuilder;

    protected final boolean withSource;
    protected final boolean withInstrumentation;

    protected final short[] bc = new short[65536];
    protected int bci = 0;
    protected final OperationsConstantPool constPool = new OperationsConstantPool();

    protected BuilderOperationData operationData;
    protected BuilderFinallyTryContext finallyTryContext;

    protected OperationBuilder(OperationNodes nodes, boolean isReparse, OperationConfig config) {
        this.nodes = nodes;
        this.isReparse = isReparse;

        if (isReparse) {
            builtNodes.addAll(nodes.getNodes());
        }

        this.withSource = config.isWithSource();
        this.withInstrumentation = config.isWithInstrumentation();

        if (withSource) {
            sourceBuilder = new BuilderSourceInfo();
        } else {
            sourceBuilder = null;
        }

        reset();
    }

    private void reset() {
        bci = 0;
        curStack = 0;
        maxStack = 0;
        numLocals = 0;
        constPool.reset();

        operationData = new BuilderOperationData(null, getBlockOperationIndex(), 0, 0, false, 0);

        labelFills.clear();

        numChildNodes = 0;
        numBranchProfiles = 0;

        resetMetadata();
    }

    public final OperationNode publish() {
        if (operationData.depth != 0) {
            throw new IllegalStateException("Not all operations closed");
        }

        OperationNode result;
        if (!isReparse) {
            SourceInfo sourceInfo = withSource ? sourceBuilder.build() : null;
            result = createNode(nodes, sourceInfo, publishBytecode());
            assignMetadata(result);
            assert buildIndex == builtNodes.size();
            builtNodes.add(result);
        } else {
            result = builtNodes.get(buildIndex);

            if (withSource && !result.hasSourceInfo()) {
                result.setSourceInfo(sourceBuilder.build());
            }

            if (withInstrumentation && !result.isBytecodeInstrumented()) {
                BytecodeNode instrumentedBytecode = publishBytecode();
                assert instrumentedBytecode instanceof InstrumentedBytecodeNode;
                result.changeBytecode(instrumentedBytecode);
            }
        }

        reset();

        buildIndex++;

        return result;
    }

    protected final void finish() {
        if (withSource) {
            nodes.setSources(sourceBuilder.buildSource());
        }
        if (!isReparse) {
            nodes.setNodes(builtNodes.toArray(new OperationNode[0]));
        }
    }

    private BytecodeNode publishBytecode() {

        labelPass();

        short[] bcCopy = Arrays.copyOf(bc, bci);
        Object[] consts = constPool.getValues();
        Node[] childrenCopy = new Node[numChildNodes];
        BuilderExceptionHandler[] handlers = exceptionHandlers.toArray(new BuilderExceptionHandler[0]);

        ConditionProfile[] conditionProfiles = new ConditionProfile[numBranchProfiles];
        for (int i = 0; i < conditionProfiles.length; i++) {
            conditionProfiles[i] = ConditionProfile.createCountingProfile();
        }

        if (withInstrumentation) {
            return createInstrumentedBytecode(maxStack, numLocals, bcCopy, consts, childrenCopy, handlers, conditionProfiles);
        } else {
            return createBytecode(maxStack, numLocals, bcCopy, consts, childrenCopy, handlers, conditionProfiles);
        }
    }

    protected abstract OperationNode createNode(OperationNodes arg0, Object arg1, BytecodeNode arg2);

    protected abstract BytecodeNode createBytecode(int arg0, int arg1, short[] arg2, Object[] arg3, Node[] arg4, BuilderExceptionHandler[] arg5, ConditionProfile[] arg6);

    protected abstract InstrumentedBytecodeNode createInstrumentedBytecode(int arg0, int arg1, short[] arg2, Object[] arg3, Node[] arg4, BuilderExceptionHandler[] arg5,
                    ConditionProfile[] arg6);

    protected abstract int getBlockOperationIndex();

    // ------------------------ branch profiles ------------------------

    private short numChildNodes;

    protected final short createChildNodes(int count) {
        short curIndex = numChildNodes;
        numChildNodes += count;
        return curIndex;
    }

    // ------------------------ branch profiles ------------------------

    private short numBranchProfiles;

    protected final short createBranchProfile() {
        return numBranchProfiles++;
    }

    // ------------------------ stack / successor handling ------------------------

    private int[] stackSourceBci = new int[1024];
    private int curStack;
    private int maxStack;

    protected int[] doBeforeEmitInstruction(int numPops, boolean pushValue) {
        int[] result = new int[numPops];

        for (int i = numPops - 1; i >= 0; i--) {
            curStack--;
            int predBci = stackSourceBci[curStack];
            result[i] = predBci;
        }

        if (pushValue) {
            stackSourceBci[curStack] = bci;

            curStack++;

            if (curStack > maxStack) {
                maxStack = curStack;
            }
        }

        return result;
    }

    protected int createMaxStack() {
        return maxStack;
    }

    protected int getCurStack() {
        return curStack;
    }

    protected void setCurStack(int curStack) {
        // this should probably be:
        // assert this.curStack == curStack;
        this.curStack = curStack;
    }

    // ------------------------ locals handling ------------------------

    protected int numLocals;

    public final OperationLocal createLocal() {
        BuilderOperationLocal local = new BuilderOperationLocal(operationData, numLocals++);
        return local;
    }

    protected final OperationLocal createParentLocal() {
        BuilderOperationData parent = operationData.parent;
        BuilderOperationLocal local = new BuilderOperationLocal(parent, numLocals++);
        return local;
    }

    protected short getLocalIndex(Object value) {
        BuilderOperationLocal local = (BuilderOperationLocal) value;
        assert verifyNesting(local.owner, operationData) : "local access not nested properly";
        return (short) local.id;
    }

    protected int[] getLocalIndices(Object[] value) {
        int[] result = new int[value.length];
        for (int i = 0; i < value.length; i++) {
            BuilderOperationLocal local = (BuilderOperationLocal) value[i];
            assert verifyNesting(local.owner, operationData) : "local access not nested properly";
            result[i] = local.id;
        }
        return result;
    }

    protected short getLocalRunStart(Object arg) {
        // todo: validate local run
        OperationLocal[] arr = (OperationLocal[]) arg;
        return (short) ((BuilderOperationLocal) arr[0]).id;
    }

    protected short getLocalRunLength(Object arg) {
        OperationLocal[] arr = (OperationLocal[]) arg;
        return (short) arr.length;
    }

    private static boolean verifyNesting(BuilderOperationData parent, BuilderOperationData child) {
        BuilderOperationData cur = child;
        while (cur.depth > parent.depth) {
            cur = cur.parent;
        }

        return cur == parent;
    }

    // ------------------------ source sections ------------------------

    public final void beginSource(Source source) {
        if (withSource) {
            sourceBuilder.beginSource(bci, source);
        }
    }

    public final void beginSource(Supplier<Source> supplier) {
        if (withSource) {
            sourceBuilder.beginSource(bci, supplier.get());
        }
    }

    public final void endSource() {
        if (withSource) {
            sourceBuilder.endSource(bci);
        }
    }

    public final void beginSourceSection(int start) {
        if (withSource) {
            sourceBuilder.beginSourceSection(bci, start);
        }
    }

    public final void endSourceSection(int length) {
        if (withSource) {
            sourceBuilder.endSourceSection(bci, length);
        }
    }

    // ------------------------------- labels -------------------------------

    private ArrayList<BuilderLabelFill> labelFills = new ArrayList<>();
    private ArrayList<BuilderOperationLabel> labels = new ArrayList<>();

    @SuppressWarnings("unused")
    protected final void putBranchTarget(short[] unusedBc, int locationBci, Object label) {
        BuilderLabelFill fill = new BuilderLabelFill(locationBci, (BuilderOperationLabel) label);
        labelFills.add(fill);
    }

    protected final void labelPass() {
        labelPass(null);
    }

    private void labelPass(BuilderFinallyTryContext finallyTry) {
        for (BuilderLabelFill fill : labelFills) {
            if (finallyTry != null) {
                if (fill.label.belongsTo(finallyTry)) {
                    assert fill.label.hasValue : "inner label should have been resolved by now";
                    finallyTry.relocationOffsets.add(fill.locationBci);
                } else {
                    finallyTry.handlerLabelFills.add(fill);
                }
            }

            bc[fill.locationBci] = (short) fill.label.targetBci;
        }
    }

    public final OperationLabel createLabel() {
        BuilderOperationLabel label = new BuilderOperationLabel(operationData, currentFinallyTry);
        labels.add(label);
        return label;
    }

    protected abstract void doLeaveOperation(BuilderOperationData data);

    protected final void calculateLeaves(BuilderOperationData fromData) {
        calculateLeaves(fromData, (BuilderOperationData) null);
    }

    protected final void calculateLeaves(BuilderOperationData fromData, Object toLabel) {
        calculateLeaves(fromData, ((BuilderOperationLabel) toLabel).data);
    }

    protected final void calculateLeaves(BuilderOperationData fromData, BuilderOperationData toData) {
        if (toData != null && fromData.depth < toData.depth) {
            throw new UnsupportedOperationException("illegal jump to deeper operation");
        }

        if (fromData == toData) {
            return; // nothing to leave
        }

        BuilderOperationData cur = fromData;
        while (true) {
            doLeaveOperation(cur);
            cur = cur.parent;

            if (toData == null && cur == null) {
                break;
            } else if (toData != null && cur.depth <= toData.depth) {
                break;
            }
        }

        if (cur != toData) {
            throw new UnsupportedOperationException("illegal jump to non-parent operation");
        }
    }

    @SuppressWarnings("unused")
    protected final int doBranchInstruction(int instr, OperationLabel label) {
        putBranchTarget(bc, bci, label);
        return 2;
    }

    protected final void doEmitLabel(OperationLabel label) {
        BuilderOperationLabel lbl = (BuilderOperationLabel) label;
        if (lbl.hasValue) {
            throw new UnsupportedOperationException("label already emitted");
        }
        if (operationData != lbl.data) {
            throw new UnsupportedOperationException("label must be created and emitted inside same operation");
        }
        lbl.hasValue = true;
        lbl.targetBci = bci;
    }

    // ------------------------ exceptions ------------------------

    private ArrayList<BuilderExceptionHandler> exceptionHandlers = new ArrayList<>();

    protected final void addExceptionHandler(BuilderExceptionHandler handler) {
        exceptionHandlers.add(handler);
    }

    // ------------------------ try / finally ------------------------

    private BuilderFinallyTryContext currentFinallyTry = null;

    protected final Object doBeginFinallyTry() {

        // save outer code
        currentFinallyTry = new BuilderFinallyTryContext(currentFinallyTry, Arrays.copyOf(bc, bci), exceptionHandlers, labelFills, labels, curStack, maxStack);

        // reset builder for handler
        bci = 0;
        exceptionHandlers = new ArrayList<>();
        labelFills = new ArrayList<>();
        labels = new ArrayList<>();
        curStack = 0;
        maxStack = 0;

        return currentFinallyTry;
    }

    protected final void doEndFinallyBlock() {
        labelPass(currentFinallyTry);

        // save handler code
        currentFinallyTry.handlerBc = Arrays.copyOf(bc, bci);
        currentFinallyTry.handlerHandlers = exceptionHandlers;
        currentFinallyTry.handlerMaxStack = maxStack;

        // restore outer code
        System.arraycopy(currentFinallyTry.bc, 0, bc, 0, currentFinallyTry.bc.length);
        bci = currentFinallyTry.bc.length;
        exceptionHandlers = currentFinallyTry.exceptionHandlers;
        labelFills = currentFinallyTry.labelFills;
        labels = currentFinallyTry.labels;
        curStack = currentFinallyTry.curStack;
        maxStack = currentFinallyTry.maxStack;

        currentFinallyTry = currentFinallyTry.prev;

    }

    protected final void doLeaveFinallyTry(BuilderOperationData opData) {
        BuilderFinallyTryContext context = (BuilderFinallyTryContext) opData.aux[0];

        if (!context.finalized()) {
            // leave out of a finally block
            return;
        }

        System.arraycopy(context.handlerBc, 0, bc, bci, context.handlerBc.length);

        for (int offset : context.relocationOffsets) {
            short oldOffset = bc[bci + offset];

            bc[bci + offset] = (short) (oldOffset + bci);
        }

        for (BuilderExceptionHandler handler : context.handlerHandlers) {
            addExceptionHandler(handler.offset(bci, curStack));
        }

        for (BuilderLabelFill fill : context.handlerLabelFills) {
            labelFills.add(fill.offset(bci));
        }

        if (maxStack < curStack + context.handlerMaxStack) {
            maxStack = curStack + context.handlerMaxStack;
        }

        bci += context.handlerBc.length;
    }

    // ------------------------ instrumentation ------------------------

    @SuppressWarnings({"static-method", "unused"})
    protected final int doBeginInstrumentation(Class<? extends Tag> cls) {
        // TODO
        return 0;
    }

    // ------------------------ metadata ------------------------

    protected abstract void resetMetadata();

    protected abstract void assignMetadata(OperationNode node);

    // ------------------------ nodes ------------------------

    protected abstract static class BytecodeNode extends Node implements BytecodeOSRNode {

        // Thevalues of these must be the same as FrameKind.ordinal && Frame tags
        public static final int FRAME_TYPE_OBJECT = 0;
        public static final int FRAME_TYPE_LONG = 1;
        public static final int FRAME_TYPE_INT = 2;
        public static final int FRAME_TYPE_DOUBLE = 3;
        public static final int FRAME_TYPE_FLOAT = 4;
        public static final int FRAME_TYPE_BOOLEAN = 5;
        public static final int FRAME_TYPE_BYTE = 6;
        public static final int FRAME_TYPE_ILLEGAL = 7;

        static {
            assert FRAME_TYPE_OBJECT == FrameSlotKind.Object.tag;
            assert FRAME_TYPE_LONG == FrameSlotKind.Long.tag;
            assert FRAME_TYPE_INT == FrameSlotKind.Int.tag;
            assert FRAME_TYPE_DOUBLE == FrameSlotKind.Double.tag;
            assert FRAME_TYPE_FLOAT == FrameSlotKind.Float.tag;
            assert FRAME_TYPE_BOOLEAN == FrameSlotKind.Boolean.tag;
            assert FRAME_TYPE_BYTE == FrameSlotKind.Byte.tag;
            assert FRAME_TYPE_ILLEGAL == FrameSlotKind.Illegal.tag;
        }

        protected final int maxStack;
        protected final int maxLocals;

        @CompilationFinal(dimensions = 1) protected final short[] bc;
        @CompilationFinal(dimensions = 1) protected final Object[] consts;
        @Children protected final Node[] children;
        @CompilationFinal(dimensions = 1) protected final BuilderExceptionHandler[] handlers;
        @CompilationFinal(dimensions = 1) protected final ConditionProfile[] conditionProfiles;

        protected static final int VALUES_OFFSET = 0;

        protected BytecodeNode(int maxStack, int maxLocals, short[] bc, Object[] consts, Node[] children, BuilderExceptionHandler[] handlers, ConditionProfile[] conditionProfiles) {
            this.maxStack = maxStack;
            this.maxLocals = maxLocals;
            this.bc = bc;
            this.consts = consts;
            this.children = children;
            this.handlers = handlers;
            this.conditionProfiles = conditionProfiles;
        }

        FrameDescriptor createFrameDescriptor() {
            FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
            builder.addSlots(maxLocals, FrameSlotKind.Illegal);
            builder.addSlots(maxStack, FrameSlotKind.Illegal);
            return builder.build();
        }

        Object execute(VirtualFrame frame) {
            return continueAt(frame, 0, maxLocals + VALUES_OFFSET);
        }

        protected abstract Object continueAt(VirtualFrame frame, int bci, int sp);

        boolean isInstrumented() {
            return false;
        }

        // OSR

        @CompilationFinal private Object osrMetadata;

        public Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState) {
            return continueAt(osrFrame, target, (int) interpreterState);
        }

        public Object getOSRMetadata() {
            return osrMetadata;
        }

        public void setOSRMetadata(Object osrMetadata) {
            this.osrMetadata = osrMetadata;
        }

        // boxing elim

        protected static void setResultBoxedImpl(short[] bc, int bci, int targetType, short[] descriptor) {
            int op = bc[bci] & 0xffff;
            short todo = descriptor[op];

            if (todo > 0) {
                // quicken
                bc[bci] = todo;
            } else {
                // set bit
                int offset = (todo >> 8) & 0x7f;
                int bit = todo & 0xff;
                if (targetType == FRAME_TYPE_OBJECT) {
                    bc[bci + offset] &= ~bit;
                } else {
                    bc[bci + offset] |= bit;
                }
            }
        }

        protected static Object expectObject(VirtualFrame frame, int slot) {
            if (frame.isObject(slot)) {
                return frame.getObject(slot);
            } else {
                // this should only happen in edge cases, when we have specialized to a generic case
                // on one thread, but other threads have already executed the child with primitive
                // return type
                return frame.getValue(slot);
            }
        }

        protected static byte expectByte(VirtualFrame frame, int slot) throws UnexpectedResultException {
            switch (frame.getTag(slot)) {
                case FrameWithoutBoxing.BYTE_TAG:
                    return frame.getByte(slot);
                case FrameWithoutBoxing.OBJECT_TAG:
                    Object value = frame.getObject(slot);
                    if (value instanceof Byte) {
                        return (byte) value;
                    }
                    break;
            }

            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new UnexpectedResultException(frame.getValue(slot));
        }

        protected static boolean expectBoolean(VirtualFrame frame, int slot) throws UnexpectedResultException {
            switch (frame.getTag(slot)) {
                case FrameWithoutBoxing.BOOLEAN_TAG:
                    return frame.getBoolean(slot);
                case FrameWithoutBoxing.OBJECT_TAG:
                    Object value = frame.getObject(slot);
                    if (value instanceof Boolean) {
                        return (boolean) value;
                    }
                    break;
            }

            throw new UnexpectedResultException(frame.getValue(slot));
        }

        protected static int expectInt(VirtualFrame frame, int slot) throws UnexpectedResultException {
            switch (frame.getTag(slot)) {
                case FrameWithoutBoxing.INT_TAG:
                    return frame.getInt(slot);
                case FrameWithoutBoxing.OBJECT_TAG:
                    Object value = frame.getObject(slot);
                    if (value instanceof Integer) {
                        return (int) value;
                    }
                    break;
            }

            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new UnexpectedResultException(frame.getValue(slot));
        }

        protected static float expectFloat(VirtualFrame frame, int slot) throws UnexpectedResultException {
            switch (frame.getTag(slot)) {
                case FrameWithoutBoxing.FLOAT_TAG:
                    return frame.getFloat(slot);
                case FrameWithoutBoxing.OBJECT_TAG:
                    Object value = frame.getObject(slot);
                    if (value instanceof Float) {
                        return (float) value;
                    }
                    break;
            }

            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new UnexpectedResultException(frame.getValue(slot));
        }

        protected static long expectLong(VirtualFrame frame, int slot) throws UnexpectedResultException {
            switch (frame.getTag(slot)) {
                case FrameWithoutBoxing.LONG_TAG:
                    return frame.getLong(slot);
                case FrameWithoutBoxing.OBJECT_TAG:
                    Object value = frame.getObject(slot);
                    if (value instanceof Long) {
                        return (long) value;
                    }
                    break;
            }

            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new UnexpectedResultException(frame.getValue(slot));
        }

        protected static double expectDouble(VirtualFrame frame, int slot) throws UnexpectedResultException {
            switch (frame.getTag(slot)) {
                case FrameWithoutBoxing.DOUBLE_TAG:
                    return frame.getDouble(slot);
                case FrameWithoutBoxing.OBJECT_TAG:
                    Object value = frame.getObject(slot);
                    if (value instanceof Double) {
                        return (double) value;
                    }
                    break;
            }

            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new UnexpectedResultException(frame.getValue(slot));
        }

        protected abstract String dump();
    }

    protected abstract static class InstrumentedBytecodeNode extends BytecodeNode {

        protected InstrumentedBytecodeNode(int maxStack, int maxLocals, short[] bc, Object[] consts, Node[] children, BuilderExceptionHandler[] handlers,
                        ConditionProfile[] conditionProfiles) {
            super(maxStack, maxLocals, bc, consts, children, handlers, conditionProfiles);
        }

        @Override
        boolean isInstrumented() {
            return true;
        }
    }
}
