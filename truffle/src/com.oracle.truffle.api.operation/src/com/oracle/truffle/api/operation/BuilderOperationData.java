package com.oracle.truffle.api.operation;

public class BuilderOperationData {
    public final BuilderOperationData parent;
    public final int operationId;
    public final Object[] aux;
    public final Object[] arguments;
    public int numChildren = 0;

    public BuilderOperationData(BuilderOperationData parent, int id, int numAux, Object... arguments) {
        this.parent = parent;
        this.operationId = id;
        this.aux = new Object[numAux];
        this.arguments = arguments;
    }
}
