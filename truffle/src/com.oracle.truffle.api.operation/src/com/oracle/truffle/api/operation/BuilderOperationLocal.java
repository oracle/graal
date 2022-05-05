package com.oracle.truffle.api.operation;

class BuilderOperationLocal extends OperationLocal {
    final BuilderOperationData owner;
    final int id;

    BuilderOperationLocal(BuilderOperationData owner, int id) {
        this.owner = owner;
        this.id = id;
    }

}
