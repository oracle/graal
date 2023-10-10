package com.oracle.truffle.dsl.processor.operations.model;

public class ShortCircuitInstructionModel extends InstructionModel {
    public final boolean continueWhen;
    public final boolean returnConvertedValue;
    public final InstructionModel booleanConverterInstruction;

    public ShortCircuitInstructionModel(int id, String name, boolean continueWhen, boolean returnConvertedValue, InstructionModel booleanConverterInstruction) {
        super(id, InstructionKind.CUSTOM_SHORT_CIRCUIT, name);
        this.continueWhen = continueWhen;
        this.returnConvertedValue = returnConvertedValue;
        this.booleanConverterInstruction = booleanConverterInstruction;
    }

}
