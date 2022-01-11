package org.graalvm.wasm.parser.validation;

import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

public class BlockFrame extends ControlFrame {
    BlockFrame(byte[] paramTypes, byte[] resultTypes, int initialStackSize, boolean unreachable) {
        super(paramTypes, resultTypes, initialStackSize, unreachable);
    }

    @Override
    public byte[] getLabelTypes() {
        return getResultTypes();
    }

    @Override
    public void enterElse(ParserState state, ExtraDataList extraData, int offset) {
        throw WasmException.create(Failure.TYPE_MISMATCH, "Expected then branch. Else branch requires preceding then branch.");
    }

    @Override
    public void exit(ExtraDataList extraData, int offset) {
        for (int location : conditionalBranches()) {
            extraData.setConditionalBranchTarget(location, offset, extraData.getLocation(), getInitialStackSize(), getLabelTypeLength());
        }
        for (int location : unconditionalBranches()) {
            extraData.setUnconditionalBranchTarget(location, offset, extraData.getLocation(), getInitialStackSize(), getLabelTypeLength());
        }
    }
}
