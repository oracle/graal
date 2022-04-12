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

    protected int maxLocals = -1;
    protected int instrumentationId = 0;

    public void reset() {
        labelFills.clear();
        maxLocals = -1;
        instrumentationId = 0;

        instructionIndex = 0;
        curStack = 0;
        maxStack = 0;
    }

    protected final Object trackLocalsHelper(Object value) {
        if (maxLocals < (int) value) {
            maxLocals = (int) value;
        }
        return value;
    }

    // ------------------------ labels ------------------------

    private static class LabelFill {
        int locationBci;
        BuilderOperationLabel label;

        public LabelFill(int locationBci, BuilderOperationLabel label) {
            this.locationBci = locationBci;
            this.label = label;
        }
    }

    private final ArrayList<LabelFill> labelFills = new ArrayList<>();
    private final ArrayList<BuilderOperationLabel> labels = new ArrayList<>();

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
        for (LabelFill fill : labelFills) {
            LE_BYTES.putShort(bc, fill.locationBci, (short) fill.label.targetBci);
        }
    }

    protected BuilderOperationData operationData = null;

    public final OperationLabel createLabel() {
        BuilderOperationLabel label = new BuilderOperationLabel(operationData);
        labels.add(label);
        return label;
    }

    protected abstract void doLeaveOperation(BuilderOperationData data);

    protected final void calculateLeaves(BuilderOperationData fromData, BuilderOperationLabel toLabel) {
        calculateLeaves(fromData, toLabel.data);
    }

    protected final void calculateLeaves(BuilderOperationData fromData, BuilderOperationData toData) {
        if (toData != null && fromData.depth < toData.depth) {
            throw new UnsupportedOperationException("illegal jump to deeper operation");
        }

        BuilderOperationData cur = fromData;
        while ((toData == null && cur != null) || cur.depth > toData.depth) {
            doLeaveOperation(cur);
            cur = cur.parent;
        }

        if (cur != toData) {
            throw new UnsupportedOperationException("illegal jump to non-parent operation");
        }
    }

    /**
     * Emits offset at current bci
     *
     * @param bci
     * @param instr
     * @param label
     * @return length of the offset
     */
    protected final int doBranchInstruction(int bci, int instr, OperationLabel label) {
        createOffset(bci, (BuilderOperationLabel) label);
        return 2;
    }

    protected static class BuilderOperationLabel extends OperationLabel {
        BuilderOperationData data;
        boolean hasValue = false;
        int targetBci = 0;

        public BuilderOperationLabel(BuilderOperationData data) {
            this.data = data;
        }
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

    private int instructionIndex;

    private int[] stackSourceIndices = new int[1024];
    private int curStack;
    private int maxStack;

    private short[] predecessorIndices = new short[65535];

    protected void doBeforeEmitInstruction(int bci, int numPops, boolean pushValue) {
        for (int i = numPops - 1; i >= 0; i--) {
            int predIndex = stackSourceIndices[--curStack];
            predecessorIndices[predIndex] = (short) bci;
            predecessorIndices[predIndex + 1] = (short) i;
        }

        // TODO: we could only record instrs that produce values

        if (pushValue) {
            int index = instructionIndex;
            instructionIndex += 2;

            stackSourceIndices[curStack++] = index;

            if (curStack > maxStack) {
                maxStack = curStack;
            }
        }
    }

    protected short[] createPredecessorIndices() {
        return Arrays.copyOf(predecessorIndices, instructionIndex);
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
}
