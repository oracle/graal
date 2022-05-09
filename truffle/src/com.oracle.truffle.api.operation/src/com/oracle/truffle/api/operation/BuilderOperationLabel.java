package com.oracle.truffle.api.operation;

public class BuilderOperationLabel extends OperationLabel {
    BuilderOperationData data;
    boolean hasValue = false;
    int targetBci = 0;
    BuilderFinallyTryContext finallyTry;

    public BuilderOperationLabel(BuilderOperationData data, BuilderFinallyTryContext finallyTry) {
        this.data = data;
        this.finallyTry = finallyTry;
    }

    boolean belongsTo(BuilderFinallyTryContext context) {
        BuilderFinallyTryContext cur = finallyTry;
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