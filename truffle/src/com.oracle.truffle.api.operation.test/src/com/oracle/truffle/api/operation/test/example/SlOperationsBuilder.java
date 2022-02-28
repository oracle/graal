package com.oracle.truffle.api.operation.test.example;

import com.oracle.truffle.api.operation.OperationLabel;
import com.oracle.truffle.api.operation.OperationsNode;

// TODO: the abstract / concrete distinction was in prototype, do we need it?
abstract class SlOperationsBuilder {

    public static SlOperationsBuilder createBuilder() {
        return new SlOperationsBuilderImpl();
    }

    public abstract void reset();

    public abstract OperationsNode build();

    // labels

    public abstract OperationLabel createLabel();

    public abstract void markLabel(OperationLabel label);

    // if_then

    public abstract void beginIfThen();

    public abstract void endIfThen();

    // if_then_else

    public abstract void beginIfThenElse();

    public abstract void endIfThenElse();

    // while

    public abstract void beginWhile();

    public abstract void endWhile();

    // block

    public abstract void beginBlock();

    public abstract void endBlock();

    // const_object

    public abstract void emitConstObject(Object value);

    // const_long

    public abstract void emitConstLong(long value);

    // load_local

    public abstract void emitLoadLocal(int index);

    // store_local

    public abstract void beginStoreLocal(int index);

    public abstract void endStoreLocal();

    // load_argument

    public abstract void emitLoadArgument(int index);

    // return

    public abstract void beginReturn();

    public abstract void endReturn();

    // branch

    public abstract void emitBranch(OperationLabel label);

    // AddOperation

    public abstract void beginAddOperation();

    public abstract void endAddOperation();

    // LessThanOperation

    public abstract void beginLessThanOperation();

    public abstract void endLessThanOperation();
}
