package com.oracle.truffle.api.operation;

public class BuilderOperationLabel extends OperationLabel {
    BuilderOperationData data;
    boolean hasValue = false;
    int targetBci = 0;

    public BuilderOperationLabel(BuilderOperationData data) {
        this.data = data;
    }
}