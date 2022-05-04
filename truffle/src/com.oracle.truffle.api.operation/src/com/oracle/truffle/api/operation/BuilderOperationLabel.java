package com.oracle.truffle.api.operation;

import com.oracle.truffle.api.operation.OperationsBuilder.FinallyTryContext;

public class BuilderOperationLabel extends OperationLabel {
    BuilderOperationData data;
    boolean hasValue = false;
    int targetBci = 0;
    FinallyTryContext finallyTry;

    public BuilderOperationLabel(BuilderOperationData data, FinallyTryContext finallyTry) {
        this.data = data;
        this.finallyTry = finallyTry;
    }

    boolean belongsTo(FinallyTryContext context) {
        FinallyTryContext cur = finallyTry;
        while (cur != null) {
            if (cur == context) {
                return true;
            }
            cur = cur.prev;
        }

        return false;
    }

    @Override
    public String toString() {
        if (!hasValue) {
            return "BuilderOperationLabel [unresolved]";
        } else {
            return String.format("BuilderOperationLabel [target=%04x]", targetBci);
        }
    }
}