package com.oracle.truffle.api.operation;

public class BuilderOperationData {
    public final BuilderOperationData parent;
    public final int depth;
    public final int stackDepth;
    public final int operationId;
    public final boolean needsLeave;
    public final Object[] aux;
    public final Object[] arguments;
    public OperationLocal[] localReferences;
    public int numChildren = 0;
    public int numLocalReferences = 0;

    public BuilderOperationData(BuilderOperationData parent, int id, int stackDepth, int numAux, boolean needsLeave, int numLocalReferences, Object... arguments) {
        this.parent = parent;
        this.depth = parent == null ? 0 : parent.depth + 1;
        this.operationId = id;
        this.stackDepth = stackDepth;
        this.aux = new Object[numAux];
        this.needsLeave = needsLeave || (parent != null ? parent.needsLeave : false);
        this.arguments = arguments;
        if (numLocalReferences > 0) {
            this.localReferences = new OperationLocal[numLocalReferences];
        }
    }
}
