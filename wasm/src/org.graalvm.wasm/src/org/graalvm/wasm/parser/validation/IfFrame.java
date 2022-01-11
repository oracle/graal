package org.graalvm.wasm.parser.validation;

import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

public class IfFrame extends ControlFrame {
    private int falseJumpLocation;
    private boolean elseBranch;

    IfFrame(byte[] paramTypes, byte[] resultTypes, int initialStackSize, boolean unreachable, int falseJumpLocation) {
        super(paramTypes, resultTypes, initialStackSize, unreachable);
        this.falseJumpLocation = falseJumpLocation;
        this.elseBranch = false;
    }

    @Override
    public byte[] getLabelTypes() {
        return getResultTypes();
    }

    @Override
    public void enterElse(ParserState state, ExtraDataList extraData, int offset) {
        int endJumpLocation = extraData.addElseLocation();
        extraData.setIfTarget(falseJumpLocation, offset, extraData.getLocation());
        falseJumpLocation = endJumpLocation;
        elseBranch = true;
        state.checkStackAfterFrameExit(this, getResultTypes());
    }

    @Override
    public void exit(ExtraDataList extraData, int offset) {
        if (!elseBranch && getLabelTypeLength() > 0) {
            throw WasmException.format(Failure.TYPE_MISMATCH, "Expected else branch. If with result value requires then and else branch.");
        }
        if (elseBranch) {
            extraData.setElseTarget(falseJumpLocation, offset, extraData.getLocation());
        } else {
            extraData.setIfTarget(falseJumpLocation, offset, extraData.getLocation());
        }
        for (int location : conditionalBranches()) {
            extraData.setConditionalBranchTarget(location, offset, extraData.getLocation(), getInitialStackSize(), getLabelTypeLength());
        }
        for (int location : unconditionalBranches()) {
            extraData.setUnconditionalBranchTarget(location, offset, extraData.getLocation(), getInitialStackSize(), getLabelTypeLength());
        }
    }

}
