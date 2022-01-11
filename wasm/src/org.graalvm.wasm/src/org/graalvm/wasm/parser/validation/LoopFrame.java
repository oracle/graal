package org.graalvm.wasm.parser.validation;

import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

public class LoopFrame extends ControlFrame {
    private final int target;
    private final int extraTarget;

    LoopFrame(byte[] paramTypes, byte[] resultTypes, int initialStackSize, boolean unreachable, int target, int extraTarget) {
        super(paramTypes, resultTypes, initialStackSize, unreachable);
        this.target = target;
        this.extraTarget = extraTarget;
    }

    @Override
    public byte[] getLabelTypes() {
        return getParamTypes();
    }

    @Override
    public void enterElse(ParserState state, ExtraDataList extraData, int offset) {
        throw WasmException.create(Failure.TYPE_MISMATCH, "Expected then branch. Else branch requires preceding then branch.");
    }

    @Override
    public void exit(ExtraDataList extraData, int offset) {
        for (int location : conditionalBranches()) {
            extraData.setConditionalBranchTarget(location, target, extraTarget, getInitialStackSize(), getLabelTypeLength());
        }
        for (int location : unconditionalBranches()) {
            extraData.setUnconditionalBranchTarget(location, target, extraTarget, getInitialStackSize(), getLabelTypeLength());
        }
    }
}
