package com.oracle.truffle.dsl.processor.operations;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.Instruction.ExecuteVariables;
import com.oracle.truffle.dsl.processor.operations.Operation.BuilderVariables;

public abstract class Argument {

    public final int length;

    protected Argument(int length) {
        this.length = length;
    }

    public abstract CodeTree createBuildCode(BuilderVariables vars, CodeTree value);

    public abstract CodeTree createReadCode(ExecuteVariables vars, CodeTree offset);

    public CodeTree createReadCode(ExecuteVariables vars, int offset) {
        return createReadCode(vars, CodeTreeBuilder.createBuilder().variable(vars.bci).string(" + " + offset).build());
    }

    public boolean isImplicit() {
        return false;
    }

    public abstract TypeMirror toBuilderArgumentType();

    public abstract CodeTree getDumpCode(ExecuteVariables vars, CodeTree offset);

    public static class IntegerArgument extends Argument {
        public IntegerArgument(int length) {
            super(length);
            assert length == 1 || length == 2;
        }

        @Override
        public CodeTree createBuildCode(BuilderVariables vars, CodeTree value) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
            b.startStatement();
            if (length == 1) {
                b.variable(vars.bc).string("[").variable(vars.bci).string("] = ");
                b.cast(ProcessorContext.getInstance().getType(byte.class));
                b.tree(value);
            } else {
                b.startCall("LE_BYTES", "putShort");
                b.variable(vars.bc);
                b.variable(vars.bci);
                b.startGroup();
                b.cast(ProcessorContext.getInstance().getType(byte.class));
                b.tree(value);
                b.end(2);
            }
            b.end();
            return b.build();
        }

        @Override
        public TypeMirror toBuilderArgumentType() {
            return ProcessorContext.getInstance().getType(int.class);
        }

        @Override
        public CodeTree createReadCode(ExecuteVariables vars, CodeTree offset) {
            if (length == 1) {
                return CodeTreeBuilder.createBuilder().variable(vars.bc).string("[").tree(offset).string("]").build();
            } else {
                return CodeTreeBuilder.createBuilder().startCall("LE_BYTES", "getShort") //
                                .variable(vars.bc) //
                                .tree(offset) //
                                .end().build();
            }
        }

        @Override
        public CodeTree getDumpCode(ExecuteVariables vars, CodeTree offset) {
            return CodeTreeBuilder.createBuilder().startStatement() //
                            .startCall("sb", "append") //
                            .tree(createReadCode(vars, offset)) //
                            .end(2).build();
        }
    }

    public static class VarArgsCount extends IntegerArgument {

        private final int minCount;

        public VarArgsCount(int minCount) {
            super(1);
            this.minCount = minCount;
        }

        @Override
        public CodeTree createBuildCode(BuilderVariables vars, CodeTree value) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
            b.startStatement();
            b.variable(vars.bc).string("[").variable(vars.bci).string("] = ");
            b.cast(ProcessorContext.getInstance().getType(byte.class));
            b.startParantheses();
            b.variable(vars.numChildren).string(" - " + minCount);
            b.end(2);
            return b.build();
        }

        @Override
        public boolean isImplicit() {
            return true;
        }
    }

    public static class BranchTarget extends Argument {

        public BranchTarget() {
            super(2);
        }

        @Override
        public CodeTree createBuildCode(BuilderVariables vars, CodeTree value) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
            b.startStatement();

            b.startCall(CodeTreeBuilder.createBuilder().string("((BuilderOperationLabel) ").tree(value).string(")").build(), "putValue");

            b.variable(vars.bc);
            b.variable(vars.bci);
            b.end(2);
            return b.build();
        }

        @Override
        public TypeMirror toBuilderArgumentType() {
            return ProcessorContext.getInstance().getTypes().OperationLabel;
        }

        @Override
        public CodeTree createReadCode(ExecuteVariables vars, CodeTree offset) {
            return CodeTreeBuilder.createBuilder().startCall("LE_BYTES", "getShort") //
                            .variable(vars.bc) //
                            .tree(offset) //
                            .end().build();
        }

        @Override
        public CodeTree getDumpCode(ExecuteVariables vars, CodeTree offset) {
            return CodeTreeBuilder.createBuilder().startStatement() //
                            .startCall("sb", "append") //
                            .startCall("String", "format") //
                            .doubleQuote("%04x") //
                            .tree(createReadCode(vars, offset)) //
                            .end(3).build();
        }
    }

    public static class Const extends Argument {
        public Const() {
            super(2);
        }

        @Override
        public CodeTree createBuildCode(BuilderVariables vars, CodeTree value) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
            b.startStatement();
            b.startCall("LE_BYTES", "putShort");
            b.variable(vars.bc);
            b.variable(vars.bci);
            b.startGroup().string("(short) ");
            b.startCall(vars.consts, "add");
            b.tree(value);
            b.end(4);
            return b.build();
        }

        @Override
        public TypeMirror toBuilderArgumentType() {
            return ProcessorContext.getInstance().getType(Object.class);
        }

        @Override
        public CodeTree createReadCode(ExecuteVariables vars, CodeTree offset) {
            return CodeTreeBuilder.createBuilder().variable(vars.consts).string("[")//
                            .startCall("LE_BYTES", "getShort") //
                            .variable(vars.bc) //
                            .tree(offset) //
                            .end().string("]").build();
        }

        @Override
        public CodeTree getDumpCode(ExecuteVariables vars, CodeTree offset) {
            return CodeTreeBuilder.createBuilder().startStatement() //
                            .startCall("sb", "append") //
                            .startCall("String", "format") //
                            .doubleQuote("(%s) %s") //
                            .startCall(createReadCode(vars, offset), "getClass().getSimpleName").end() //
                            .tree(createReadCode(vars, offset)) //
                            .end(3).build();
        }
    }
}
