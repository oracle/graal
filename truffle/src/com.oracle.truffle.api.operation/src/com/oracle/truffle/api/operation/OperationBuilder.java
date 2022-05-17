package com.oracle.truffle.api.operation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Supplier;

import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;
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

    protected final byte[] bc = new byte[65536];
    protected int bci = 0;
    protected final OperationsConstantPool constPool = new OperationsConstantPool();

    protected static final ByteArraySupport LE_BYTES = ByteArraySupport.littleEndian();

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

        operationData = new BuilderOperationData(null, getBlockOperationIndex(), 0, 0, false);

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
                OperationBytecodeNode instrumentedBytecode = publishBytecode();
                assert instrumentedBytecode instanceof OperationInstrumentedBytecodeNode;
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

    private OperationBytecodeNode publishBytecode() {

        labelPass();

        byte[] bcCopy = Arrays.copyOf(bc, bci);
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

    protected abstract OperationNode createNode(OperationNodes arg0, Object arg1, OperationBytecodeNode arg2);

    protected abstract OperationBytecodeNode createBytecode(int arg0, int arg1, byte[] arg2, Object[] arg3, Node[] arg4, BuilderExceptionHandler[] arg5, ConditionProfile[] arg6);

    protected abstract OperationInstrumentedBytecodeNode createInstrumentedBytecode(int arg0, int arg1, byte[] arg2, Object[] arg3, Node[] arg4, BuilderExceptionHandler[] arg5,
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

    protected int getLocalIndex(Object value) {
        BuilderOperationLocal local = (BuilderOperationLocal) value;
        assert verifyNesting(local.owner, operationData) : "local access not nested properly";
        return local.id;
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

    protected final void createOffset(int locationBci, Object label) {
        BuilderLabelFill fill = new BuilderLabelFill(locationBci, (BuilderOperationLabel) label);
        labelFills.add(fill);
    }

    protected final void labelPass() {
        labelPass(null);
    }

    private final void labelPass(BuilderFinallyTryContext finallyTry) {
        for (BuilderLabelFill fill : labelFills) {
            if (finallyTry != null) {
                if (fill.label.belongsTo(finallyTry)) {
                    assert fill.label.hasValue : "inner label should have been resolved by now";
                    finallyTry.relocationOffsets.add(fill.locationBci);
                } else {
                    finallyTry.handlerLabelFills.add(fill);
                }
            }
            LE_BYTES.putShort(bc, fill.locationBci, (short) fill.label.targetBci);
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

    protected final void calculateLeaves(BuilderOperationData fromData, BuilderOperationLabel toLabel) {
        calculateLeaves(fromData, toLabel.data);
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
        createOffset(bci, label);
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
            short oldOffset = LE_BYTES.getShort(bc, bci + offset);
            LE_BYTES.putShort(bc, bci + offset, (short) (oldOffset + bci));
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
}
