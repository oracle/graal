package com.oracle.truffle.dsl.processor.operations;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

abstract class Instruction {

    static class ExecutorVariables {
        CodeVariableElement bci;
        CodeVariableElement nextBci;
        CodeVariableElement bc;
        CodeVariableElement consts;
        CodeVariableElement sp;
        CodeVariableElement funArgs;
        CodeVariableElement frame;
        CodeVariableElement returnValue;

        CodeVariableElement[] arguments;
        CodeVariableElement[] children;
        CodeVariableElement result;

        public ExecutorVariables(CodeVariableElement bci, CodeVariableElement nextBci, CodeVariableElement bc, CodeVariableElement consts, CodeVariableElement sp, CodeVariableElement funArgs,
                        CodeVariableElement frame, CodeVariableElement returnValue) {
            this.bci = bci;
            this.nextBci = nextBci;
            this.bc = bc;
            this.consts = consts;
            this.sp = sp;
            this.funArgs = funArgs;
            this.frame = frame;
            this.returnValue = returnValue;
            this.arguments = null;
            this.children = null;
            this.result = null;
        }

    }

    enum ArgumentType {
        BYTE(1),
        SHORT(2),
        JUMP_TARGET(2),
        LOCAL(2),
        FUN_ARG(2),
        LOCAL_INDEX(2),
        CONSTANT_POOL(2);

        public final int length;

        public TypeMirror toType(ProcessorContext context, TruffleTypes types) {
            switch (this) {
                case BYTE:
                    return context.getType(byte.class);
                case LOCAL:
                case FUN_ARG:
                case LOCAL_INDEX:
                case SHORT:
                    return context.getType(short.class);
                case JUMP_TARGET:
                    return types.OperationLabel;
                case CONSTANT_POOL:
                    return context.getType(Object.class);
                default:
                    throw new IllegalArgumentException(this.toString());
            }
        }

        private ArgumentType(int length) {
            this.length = length;
        }

        public TypeMirror toExecType(ProcessorContext context, TruffleTypes types) {
            switch (this) {
                case BYTE:
                    return context.getType(byte.class);
                case SHORT:
                case JUMP_TARGET:
                case LOCAL_INDEX:
                    return context.getType(short.class);
                case LOCAL:
                case FUN_ARG:
                case CONSTANT_POOL:
                    return context.getType(Object.class);
                default:
                    throw new IllegalArgumentException(this.toString());
            }
        }

        CodeTree createReaderCode(ExecutorVariables vars, CodeTree offset) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
            switch (this) {
                case BYTE:
                    b.variable(vars.bc).string("[").tree(offset).string("]");
                    break;
                case SHORT:
                case JUMP_TARGET:
                case LOCAL_INDEX:
                    b.startCall("LE_BYTES", "getShort");
                    b.variable(vars.bc);
                    b.tree(offset);
                    b.end();
                    break;
                case CONSTANT_POOL:
                    b.variable(vars.consts);
                    b.string("[");
                    b.startCall("LE_BYTES", "getShort");
                    b.variable(vars.bc);
                    b.tree(offset);
                    b.end();
                    b.string("]");
                    break;
                case FUN_ARG:
                    b.variable(vars.funArgs);
                    b.string("[");
                    b.startCall("LE_BYTES", "getShort");
                    b.variable(vars.bc);
                    b.tree(offset);
                    b.end();
                    b.string("]");
                    break;
                case LOCAL:
                    b.startCall(CodeTreeBuilder.singleVariable(vars.frame), "getValue");
                    b.startGroup();
                    b.string("maxStack + ");
                    b.startCall("LE_BYTES", "getShort");
                    b.variable(vars.bc);
                    b.tree(offset);
                    b.end(3);
                    break;
                default:
                    throw new IllegalArgumentException(this.toString());
            }
            return b.build();
        }

        CodeTree createDumperReaderCode(CodeVariableElement varBc, CodeTree offset) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
            switch (this) {
                case BYTE:
                    b.variable(varBc).string("[").tree(offset).string("]");
                    break;
                case SHORT:
                case JUMP_TARGET:
                case LOCAL_INDEX:
                case FUN_ARG:
                case CONSTANT_POOL:
                case LOCAL:
                    b.startCall("LE_BYTES", "getShort");
                    b.variable(varBc);
                    b.tree(offset);
                    b.end();
                    break;
                default:
                    throw new IllegalArgumentException(this.toString());
            }
            return b.build();
        }

        CodeTree createDumperCode(CodeVariableElement varBc, CodeTree offset, CodeVariableElement sb) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.startStatement();
            b.startCall(sb, "append");
            b.startCall("String", "format");
            switch (this) {
                case BYTE:
                case SHORT:
                case LOCAL:
                case LOCAL_INDEX:
                case FUN_ARG:
                case CONSTANT_POOL:
                    b.doubleQuote("%d ");
                    break;
                case JUMP_TARGET:
                    b.doubleQuote("%04x ");
                    break;
                default:
                    throw new IllegalArgumentException(this.toString());
            }
            b.tree(createDumperReaderCode(varBc, offset));
            b.end(3);

            return b.build();
        }
    }

    final int opcodeNumber;
    final ArgumentType[] arguments;
    final int stackPops;
    final int stackPushes;

    protected Instruction(int opcodeNumber, int stackPops, int stackPushes, ArgumentType... arguments) {
        this.opcodeNumber = opcodeNumber;
        this.stackPops = stackPops;
        this.stackPushes = stackPushes;
        this.arguments = arguments;
    }

    public int length() {
        int l = 1;
        for (ArgumentType arg : arguments) {
            l += arg.length;
        }
        return l;
    }

    public boolean isDivergent() {
        return false;
    }

    CodeTree createEmitterCode(
                    TruffleTypes types,
                    Operation.EmitterVariables vars,
                    String... varArguments) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.lineComment("opcode");

        // TODO: support multibyte ops
        OperationGeneratorUtils.buildWriteByte(b, Integer.toString(opcodeNumber), vars.bc, vars.bci);

        for (int i = 0; i < arguments.length; i++) {
            b.lineComment("argument" + i);
            String argv = varArguments[i];
            switch (arguments[i]) {
                case BYTE:
                    OperationGeneratorUtils.buildWriteByte(b, argv, vars.bc, vars.bci);
                    break;
                case LOCAL:
                case LOCAL_INDEX:
                case FUN_ARG:
                case SHORT:
                    OperationGeneratorUtils.buildWriteShort(b, "(short) " + argv, vars.bc, vars.bci);
                    break;
                case CONSTANT_POOL:
                    b.declaration("int", "argidx_" + i, CodeTreeBuilder.createBuilder().startCall(CodeTreeBuilder.singleVariable(vars.consts), "add").string(argv).end().build());
                    OperationGeneratorUtils.buildWriteShort(b, "(short) argidx_" + i, vars.bc, vars.bci);
                    break;
                case JUMP_TARGET:
                    b.declaration(types.BuilderOperationLabel, "lbl_" + i, "(" + types.BuilderOperationLabel.asElement().getSimpleName() + ") " + argv);
                    b.startStatement().startCall("lbl_" + i, "putValue").variable(vars.bc).variable(vars.bci).end(2);
                    b.startAssign(vars.bci).variable(vars.bci).string("+ 2").end();
                    break;
                default:
                    throw new IllegalArgumentException("unknown argument type " + arguments[i]);
            }
        }

        return b.build();
    }

    abstract CodeTree createExecutorCode(TruffleTypes types, ExecutorVariables vars);

    CodeTree createNextBciCode(TruffleTypes types, ExecutorVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startAssign(vars.nextBci).variable(vars.bci).string(" + " + length()).end();
        return b.build();
    }

    static class StoreLocal extends Instruction {
        protected StoreLocal(int opcodeNumber) {
            super(opcodeNumber, 1, 0, ArgumentType.LOCAL_INDEX);
        }

        @Override
        CodeTree createExecutorCode(TruffleTypes types, ExecutorVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.startStatement();
            b.startCall(CodeTreeBuilder.singleVariable(vars.frame), "setObject");
            b.startGroup().string("maxStack + ").variable(vars.arguments[0]).end();
            b.variable(vars.children[0]);
            b.end(2);

            return b.build();
        }
    }

    static class JumpUncond extends Instruction {
        protected JumpUncond(int opcodeNumber) {
            super(opcodeNumber, 0, 0, ArgumentType.JUMP_TARGET);
        }

        @Override
        CodeTree createExecutorCode(TruffleTypes types, ExecutorVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
            b.startAssign(vars.nextBci).tree(OperationGeneratorUtils.buildReadShort(vars.bc, vars.bci.getName() + " + 1")).end();
            return b.build();
        }

        @Override
        CodeTree createNextBciCode(TruffleTypes types, ExecutorVariables vars) {
            return null;
        }
    }

    static class JumpFalse extends Instruction {
        protected JumpFalse(int opcodeNumber) {
            super(opcodeNumber, 1, 0, ArgumentType.JUMP_TARGET);
        }

        @Override
        CodeTree createExecutorCode(TruffleTypes types, ExecutorVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.startIf().string("(boolean) ").variable(vars.children[0]).end();
            b.startBlock();
            b.startAssign(vars.nextBci).variable(vars.bci).string(" + " + length()).end();
            b.end();
            b.startElseBlock();
            b.startAssign(vars.nextBci).variable(vars.arguments[0]).end();
            b.end();

            return b.build();
        }

        @Override
        CodeTree createNextBciCode(TruffleTypes types, ExecutorVariables vars) {
            return null;
        }
    }

    static class Pop extends Instruction {
        protected Pop(int opcodeNumber) {
            super(opcodeNumber, 1, 0);
        }

        @Override
        CodeTree createExecutorCode(TruffleTypes types, ExecutorVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
            b.startStatement().variable(vars.sp).string(" -= 1").end();
            return b.build();
        }

    }

    static class Return extends Instruction {
        protected Return(int opcodeNumber) {
            super(opcodeNumber, 1, 0);
        }

        @Override
        CodeTree createExecutorCode(TruffleTypes types, ExecutorVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
            b.startAssign(vars.returnValue).variable(vars.children[0]).end();
            b.statement("break loop");
            return b.build();
        }

        @Override
        public boolean isDivergent() {
            return true;
        }
    }

    static abstract class SimplePushInstruction extends Instruction {
        protected SimplePushInstruction(int opcodeNumber, ArgumentType... arguments) {
            super(opcodeNumber, 0, 1, arguments);
        }

        @Override
        CodeTree createExecutorCode(TruffleTypes types, ExecutorVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
            b.startAssign(vars.result).variable(vars.arguments[0]).end();
            return b.build();
        }
    }

    static class ConstObject extends SimplePushInstruction {
        protected ConstObject(int opcodeNumber) {
            super(opcodeNumber, ArgumentType.CONSTANT_POOL);
        }
    }

    static class LoadLocal extends SimplePushInstruction {
        protected LoadLocal(int opcodeNumber) {
            super(opcodeNumber, ArgumentType.LOCAL);
        }
    }

    static class LoadArgument extends SimplePushInstruction {
        protected LoadArgument(int opcodeNumber) {
            super(opcodeNumber, ArgumentType.FUN_ARG);
        }
    }

    static class Custom extends Instruction {
        private final TypeElement type;

        private VariableElement uncachedInstance;

        public TypeElement getType() {
            return type;
        }

        protected Custom(int opcodeNumber, TypeElement type, ExecutableElement mainMethod) {
            super(opcodeNumber, mainMethod.getParameters().size(), 1);
            this.type = type;
        }

        public void setUncachedInstance(VariableElement uncachedInstance) {
            this.uncachedInstance = uncachedInstance;
        }

        @Override
        CodeTree createExecutorCode(TruffleTypes types, ExecutorVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
            b.startAssign(vars.result);
            b.startCall(CodeTreeBuilder.createBuilder().staticReference(uncachedInstance).build(), "execute");
            for (CodeVariableElement child : vars.children) {
                b.variable(child);
            }
            b.end();
            b.end();
            return b.build();
        }
    }
}
