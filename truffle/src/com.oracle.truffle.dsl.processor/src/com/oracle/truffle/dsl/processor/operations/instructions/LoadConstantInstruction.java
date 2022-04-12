package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils;
import com.oracle.truffle.dsl.processor.operations.Operation.BuilderVariables;

public class LoadConstantInstruction extends Instruction {
    public enum ConstantKind {
        BOOLEAN("boolean", "Boolean"),
        BYTE("byte", "Byte"),
        INT("int", "Int", "Integer"),
        FLOAT("float", "Float"),
        LONG("long", "Long"),
        DOUBLE("double", "Double"),
        OBJECT("Object", "Object");

        private final String typeName;
        private final String frameName;
        private final String typeNameBoxed;

        private ConstantKind(String typeName, String frameName) {
            this(typeName, frameName, frameName);
        }

        private ConstantKind(String typeName, String frameName, String typeNameBoxed) {
            this.typeName = typeName;
            this.frameName = frameName;
            this.typeNameBoxed = typeNameBoxed;
        }

        public boolean isSingleByte() {
            return this == BOOLEAN || this == BYTE;
        }

        public boolean isBoxed() {
            return this == OBJECT;
        }

        public String getFrameName() {
            return frameName;
        }

        public String getTypeName() {
            return typeName;
        }

        public String getTypeNameBoxed() {
            return typeNameBoxed;
        }
    }

    private ConstantKind kind;
    private LoadConstantInstruction boxedVariant;

    public LoadConstantInstruction(int id, boolean boxed, ConstantKind kind, LoadConstantInstruction boxedVariant) {
        super("load.constant." + kind.toString().toLowerCase() + (boxed ? ".boxed" : ""), id, ResultType.STACK_VALUE, new InputType[0]);
        this.kind = kind;
        this.boxedVariant = boxedVariant == null ? this : boxedVariant;
    }

    @Override
    public int getAdditionalStateBytes() {
        return kind.isSingleByte() ? 1 : 2;
    }

    @Override
    public boolean standardPrologue() {
        return false;
    }

    @Override
    protected CodeTree createInitializeAdditionalStateBytes(BuilderVariables vars, CodeTree[] arguments) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        if (kind.isSingleByte()) {
            b.startStatement();
            b.variable(vars.bc).string("[").variable(vars.bci).string(" + " + lengthWithoutState(), "] = ");
            if (kind == ConstantKind.BOOLEAN) {
                b.string("((boolean) ");
                b.tree(arguments[0]);
                b.string(") ? (byte) 1 : (byte) 0");
            } else {
                b.string("(", kind.getTypeName(), ") ").tree(arguments[0]);
            }
            b.end();
        } else {
            b.startStatement().startCall("LE_BYTES", "putShort");
            b.variable(vars.bc);
            b.startGroup().variable(vars.bci).string(" + " + lengthWithoutState()).end();
            b.startGroup().string("(short) ");
            b.startCall(vars.consts, "add");
            b.tree(arguments[0]);
            b.end(4);
        }

        return b.build();
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startStatement().startCall(vars.frame, "set" + kind.getFrameName());
        b.variable(vars.sp);
        b.tree(createGetArgument(vars));
        b.end(2);

        b.startAssign(vars.sp).variable(vars.sp).string(" + 1").end();

        return b.build();

    }

    private CodeTree createGetArgument(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        if (kind.isSingleByte()) {
            b.variable(vars.bc).string("[").variable(vars.bci).string(" + " + lengthWithoutState() + "]");
            if (kind == ConstantKind.BOOLEAN) {
                b.string(" != 0");
            }
        } else {
            if (kind != ConstantKind.OBJECT) {
                b.string("(", kind.getTypeName(), ") ");
            }
            b.variable(vars.consts).string("[");
            b.startCall("LE_BYTES", "getShort");
            b.variable(vars.bc);
            b.startGroup().variable(vars.bci).string(" + " + lengthWithoutState()).end();
            b.end().string("]");
        }
        return b.build();
    }

    @Override
    public CodeTree createSetResultBoxed(ExecutionVariables vars) {
        if (this == boxedVariant) {
            return null;
        }

        return OperationGeneratorUtils.createWriteOpcode(vars.bc, vars.bci, boxedVariant.opcodeIdField);
    }

    @Override
    public CodeTree createSetInputBoxed(ExecutionVariables vars, int index) {
        return null;
    }
}
