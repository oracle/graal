package com.oracle.truffle.api.operation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Supplier;

import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.source.Source;

public abstract class OperationsBuilder {

    private ArrayList<OperationsNode> builtNodes = new ArrayList<>();

    protected abstract OperationsNode buildImpl();

    protected static final ByteArraySupport LE_BYTES = ByteArraySupport.littleEndian();

    public final OperationsNode build() {
        OperationsNode result = buildImpl();
        builtNodes.add(result);
        return result;
    }

    public final OperationsNode[] collect() {
        return builtNodes.toArray(new OperationsNode[builtNodes.size()]);
    }

    protected int instrumentationId = 0;

    public void reset() {
        labelFills.clear();
        instrumentationId = 0;

        curStack = 0;
        maxStack = 0;

        numLocals = 0;
    }

    // ------------------------ labels ------------------------

    private static class LabelFill {
        int locationBci;
        BuilderOperationLabel label;

        LabelFill(int locationBci, BuilderOperationLabel label) {
            this.locationBci = locationBci;
            this.label = label;
        }

        LabelFill offset(int offset) {
            return new LabelFill(offset + locationBci, label);
        }
    }

    private ArrayList<LabelFill> labelFills = new ArrayList<>();
    private ArrayList<BuilderOperationLabel> labels = new ArrayList<>();

    protected final void relocateLabels(int bci, int length) {
        for (LabelFill fill : labelFills) {
            if (fill.locationBci >= bci) {
                fill.locationBci += length;
            }
        }

        for (BuilderOperationLabel label : labels) {
            if (label.hasValue && label.targetBci >= bci) {
                label.targetBci += length;
            }
        }
    }

    protected final void createOffset(int locationBci, Object label) {
        LabelFill fill = new LabelFill(locationBci, (BuilderOperationLabel) label);
        labelFills.add(fill);
    }

    protected final void labelPass(byte[] bc) {
        labelPass(bc, null);
    }

    private final void labelPass(byte[] bc, FinallyTryContext finallyTry) {
        for (LabelFill fill : labelFills) {
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

    protected BuilderOperationData operationData = null;

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
    protected final int doBranchInstruction(int bci, int instr, OperationLabel label) {
        createOffset(bci, label);
        return 2;
    }

    protected void doEmitLabel(int bci, OperationLabel label) {
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

    // ------------------------ stack / successor handling ------------------------

    private int[] stackSourceBci = new int[1024];
    private int curStack;
    private int maxStack;

    protected int[] doBeforeEmitInstruction(int bci, int numPops, boolean pushValue) {
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
        BuilderOperationLocal local = new BuilderOperationLocal(operationData, operationData.numLocals++);

        if (numLocals < local.id + 1) {
            numLocals = local.id + 1;
        }

        return local;
    }

    protected final OperationLocal createParentLocal() {
        BuilderOperationData parent = operationData.parent;
        assert operationData.numLocals == parent.numLocals;

        BuilderOperationLocal local = new BuilderOperationLocal(parent, parent.numLocals++);
        operationData.numLocals++;

        if (numLocals < local.id + 1) {
            numLocals = local.id + 1;
        }

        return local;
    }

    protected int getLocalIndex(Object value) {
        BuilderOperationLocal local = (BuilderOperationLocal) value;

        // verify nesting
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

    public abstract void beginSource(Source source);

    public abstract void beginSource(Supplier<Source> supplier);

    public abstract void endSource();

    public abstract void beginSourceSection(int start);

    public abstract void endSourceSection(int length);

    // ------------------------ instrumentation ------------------------

    private final ArrayList<OperationsInstrumentTreeNode> instrumentTrees = new ArrayList<>();

    protected final OperationsInstrumentTreeNode[] getInstrumentTrees() {
        return instrumentTrees.toArray(new OperationsInstrumentTreeNode[instrumentTrees.size()]);
    }

    protected final int doBeginInstrumentation(Class<? extends Tag> tag) {
        instrumentTrees.add(new OperationsInstrumentTreeNode(tag));
        return instrumentTrees.size() - 1;
    }

    // ------------------------ try / finally ------------------------

    private FinallyTryContext currentFinallyTry = null;

    static class FinallyTryContext {
        final FinallyTryContext prev;
        private final byte[] bc;
        private final int bci;
        private final ArrayList<BuilderExceptionHandler> exceptionHandlers;
        private final ArrayList<LabelFill> labelFills;
        private final ArrayList<BuilderOperationLabel> labels;
        private final int curStack;
        private final int maxStack;

        private byte[] handlerBc;
        private ArrayList<BuilderExceptionHandler> handlerHandlers;
        public ArrayList<LabelFill> handlerLabelFills = new ArrayList<>();
        public ArrayList<Integer> relocationOffsets = new ArrayList<>();
        public int handlerMaxStack;

        FinallyTryContext(FinallyTryContext prev, byte[] bc, int bci, ArrayList<BuilderExceptionHandler> exceptionHandlers, ArrayList<LabelFill> labelFills, ArrayList<BuilderOperationLabel> labels,
                        int curStack, int maxStack) {
            this.prev = prev;
            this.bc = bc;
            this.bci = bci;
            this.exceptionHandlers = exceptionHandlers;
            this.labelFills = labelFills;
            this.labels = labels;
            this.curStack = curStack;
            this.maxStack = maxStack;
        }

        private boolean finalized() {
            return handlerBc != null;
        }
    }

    protected final Object doBeginFinallyTry(byte[] bc, int bci, ArrayList<BuilderExceptionHandler> handlers) {
        currentFinallyTry = new FinallyTryContext(currentFinallyTry, bc, bci, handlers, labelFills, labels, curStack, maxStack);
        labelFills = new ArrayList<>();
        labels = new ArrayList<>();
        curStack = 0;
        maxStack = 0;
        return currentFinallyTry;
    }

    protected final void doEndFinallyBlock0(byte[] bc, int bci, ArrayList<BuilderExceptionHandler> handlers) {
        labelPass(bc, currentFinallyTry);
        currentFinallyTry.handlerBc = Arrays.copyOf(bc, bci);
        currentFinallyTry.handlerHandlers = handlers;
        currentFinallyTry.handlerMaxStack = maxStack;
    }

    protected final byte[] doFinallyRestoreBc() {
        return currentFinallyTry.bc;
    }

    protected final int doFinallyRestoreBci() {
        return currentFinallyTry.bci;
    }

    protected final ArrayList<BuilderExceptionHandler> doFinallyRestoreExceptions() {
        return currentFinallyTry.exceptionHandlers;
    }

    protected final void doEndFinallyBlock1() {
        labelFills = currentFinallyTry.labelFills;
        labels = currentFinallyTry.labels;
        curStack = currentFinallyTry.curStack;
        maxStack = currentFinallyTry.maxStack;
        currentFinallyTry = currentFinallyTry.prev;
    }

    protected final int doLeaveFinallyTry(byte[] bc, int bci, BuilderOperationData data, ArrayList<BuilderExceptionHandler> handlers) {
        FinallyTryContext context = (FinallyTryContext) data.aux[0];

        if (!context.finalized()) {
            // still in Finally part, nothing to leave yet
            return bci;
        }

        System.arraycopy(context.handlerBc, 0, bc, bci, context.handlerBc.length);

        for (int offset : context.relocationOffsets) {
            short oldOffset = LE_BYTES.getShort(bc, bci + offset);
            LE_BYTES.putShort(bc, bci + offset, (short) (oldOffset + bci));
        }

        for (BuilderExceptionHandler handler : context.handlerHandlers) {
            handlers.add(handler.offset(bci, curStack));
        }

        for (LabelFill fill : context.handlerLabelFills) {
            labelFills.add(fill.offset(bci));
        }

        if (maxStack < curStack + context.handlerMaxStack) {
            maxStack = curStack + context.handlerMaxStack;
        }

        return bci + context.handlerBc.length;
    }
}
