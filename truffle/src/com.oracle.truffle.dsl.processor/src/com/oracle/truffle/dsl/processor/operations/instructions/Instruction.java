package com.oracle.truffle.dsl.processor.operations.instructions;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.Operation;
import com.oracle.truffle.dsl.processor.operations.Operation.BuilderVariables;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData.MethodProperties;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData.ParameterKind;

public abstract class Instruction {

    public static class ExecutionVariables {
        public CodeVariableElement bc;
        public CodeVariableElement bci;
        public CodeVariableElement nextBci;
        public CodeVariableElement frame;
        public CodeVariableElement sp;
        public CodeVariableElement returnValue;
        public CodeVariableElement consts;
        public CodeVariableElement[] inputs;
        public CodeVariableElement[] results;

        public CodeVariableElement probeNodes;
    }

    public static enum InputType {
        STACK_VALUE(0),
        STACK_VALUE_IGNORED(0),
        VARARG_VALUE(2),
        CONST_POOL(2),
        LOCAL(2),
        ARGUMENT(2),
        INSTRUMENT(2),
        BRANCH_TARGET(2);

        final int argumentLength;

        private InputType(int argumentLength) {
            this.argumentLength = argumentLength;
        }

        public final TypeMirror getDefaultExecutionType(ProcessorContext context) {
            switch (this) {
                case STACK_VALUE_IGNORED:
                    return null;
                case STACK_VALUE:
                case CONST_POOL:
                case LOCAL:
                case ARGUMENT:
                    return context.getType(Object.class);
                case VARARG_VALUE:
                    return new ArrayCodeTypeMirror(context.getType(Object.class));
                case BRANCH_TARGET:
                case INSTRUMENT:
                    return context.getType(short.class);
                default:
                    throw new IllegalArgumentException("Unexpected value: " + this);
            }
        }

        boolean needsBuilderArgument() {
            switch (this) {
                case STACK_VALUE:
                case STACK_VALUE_IGNORED:
                case VARARG_VALUE:
                    return false;
                case CONST_POOL:
                case LOCAL:
                case ARGUMENT:
                case BRANCH_TARGET:
                case INSTRUMENT:
                    return true;
                default:
                    throw new IllegalArgumentException("Unexpected value: " + this);
            }
        }

        public final TypeMirror getDefaultBuilderType(ProcessorContext context) {
            switch (this) {
                case STACK_VALUE:
                case STACK_VALUE_IGNORED:
                case VARARG_VALUE:
                case INSTRUMENT:
                    return null;
                case CONST_POOL:
                    return context.getType(Object.class);
                case LOCAL:
                case ARGUMENT:
                    return context.getType(int.class);
                case BRANCH_TARGET:
                    return context.getTypes().OperationLabel;
                default:
                    throw new IllegalArgumentException("Unexpected value: " + this);
            }
        }

        public final CodeTree getImplicitValue(BuilderVariables vars, Instruction instr) {
            switch (this) {
                case STACK_VALUE:
                case STACK_VALUE_IGNORED:
                case CONST_POOL:
                case LOCAL:
                case ARGUMENT:
                case BRANCH_TARGET:
                case INSTRUMENT:
                    return null;
                case VARARG_VALUE:
                    return CodeTreeBuilder.createBuilder().variable(vars.numChildren).string(" - " + instr.numStackValuesExclVarargs()).build();
                default:
                    throw new IllegalArgumentException("Unexpected value: " + this);
            }
        }

        public CodeTree createDumpCode(int n, Instruction op, ExecutionVariables vars) {
            switch (this) {
                case STACK_VALUE:
                    return CodeTreeBuilder.createBuilder().statement("sb.append(\"x\")").build();
                case STACK_VALUE_IGNORED:
                    return CodeTreeBuilder.createBuilder().statement("sb.append(\"_\")").build();
                case CONST_POOL:
                    return CodeTreeBuilder.createBuilder().startBlock()//
                                    .declaration("Object", "o", CodeTreeBuilder.createBuilder().variable(vars.consts).string("[").tree(op.createReadArgumentCode(n, vars)).string("]").build()) //
                                    .startStatement().startCall("sb", "append").startCall("String", "format") //
                                    .doubleQuote("%s %s") //
                                    .string("o.getClass().getSimpleName()") //
                                    .string("o") //
                                    .end(4).build();
                case LOCAL:
                    return CodeTreeBuilder.createBuilder().startStatement().startCall("sb", "append").startCall("String", "format") //
                                    .doubleQuote("loc[%d]") //
                                    .tree(op.createReadArgumentCode(n, vars)) //
                                    .end(3).build();
                case ARGUMENT:
                    return CodeTreeBuilder.createBuilder().startStatement().startCall("sb", "append").startCall("String", "format") //
                                    .doubleQuote("arg[%d]") //
                                    .tree(op.createReadArgumentCode(n, vars)) //
                                    .end(3).build();
                case BRANCH_TARGET:
                    return CodeTreeBuilder.createBuilder().startStatement().startCall("sb", "append").startCall("String", "format") //
                                    .doubleQuote("%04x") //
                                    .tree(op.createReadArgumentCode(n, vars)) //
                                    .end(3).build();
                case INSTRUMENT:
                    return CodeTreeBuilder.createBuilder().startStatement().startCall("sb", "append").startCall("String", "format") //
                                    .doubleQuote("instrument[%d]") //
                                    .tree(op.createReadArgumentCode(n, vars)) //
                                    .end(3).build();
                case VARARG_VALUE:
                    return CodeTreeBuilder.createBuilder().startStatement().startCall("sb", "append").startCall("String", "format") //
                                    .doubleQuote("**%d") //
                                    .tree(op.createReadArgumentCode(n, vars)) //
                                    .end(3).build();
                default:
                    throw new IllegalArgumentException("Unexpected value: " + this);
            }
        }

    }

    public static enum ResultType {
        STACK_VALUE(0),
        SET_LOCAL(2),
        BRANCH(0),
        RETURN(0);

        final int argumentLength;

        private ResultType(int argumentLength) {
            this.argumentLength = argumentLength;
        }

        boolean needsBuilderArgument() {
            switch (this) {
                case STACK_VALUE:
                case BRANCH:
                case RETURN:
                    return false;
                case SET_LOCAL:
                    return true;
                default:
                    throw new IllegalArgumentException("Unexpected value: " + this);
            }
        }

        public final TypeMirror getDefaultBuilderType(ProcessorContext context) {
            switch (this) {
                case STACK_VALUE:
                case BRANCH:
                case RETURN:
                    return null;
                case SET_LOCAL:
                    return context.getType(int.class);
                default:
                    throw new IllegalArgumentException("Unexpected value: " + this);
            }
        }

        public CodeTree createDumpCode(int i, Instruction op, ExecutionVariables vars) {
            switch (this) {
                case STACK_VALUE:
                    return CodeTreeBuilder.createBuilder().statement("sb.append(\"x\")").build();
                case BRANCH:
                    return CodeTreeBuilder.createBuilder().statement("sb.append(\"branch\")").build();
                case RETURN:
                    return CodeTreeBuilder.createBuilder().statement("sb.append(\"return\")").build();
                case SET_LOCAL:
                    return CodeTreeBuilder.createBuilder().startStatement().startCall("sb", "append").startCall("String", "format") //
                                    .doubleQuote("loc[%d]") //
                                    .tree(op.createReadArgumentCode(i + op.inputs.length, vars)) //
                                    .end(3).build();
                default:
                    throw new IllegalArgumentException("Unexpected value: " + this);
            }
        }

    }

    public final String name;
    public final int id;
    public final InputType[] inputs;
    public final ResultType[] results;

    public CodeTree createStackEffect(BuilderVariables vars, CodeTree[] arguments) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        int result = 0;
        int argIndex = 0;

        for (InputType input : inputs) {
            if (input == InputType.STACK_VALUE) {
                result--;
            } else if (input == InputType.VARARG_VALUE) {
                b.string("-").startParantheses().tree(arguments[argIndex]).end().string(" + ");
            }
            argIndex++;
        }

        for (ResultType rt : results) {
            if (rt == ResultType.STACK_VALUE) {
                result++;
            }
        }

        return b.string("" + result).build();
    }

    public CodeVariableElement opcodeIdField;

    public void setOpcodeIdField(CodeVariableElement opcodeIdField) {
        this.opcodeIdField = opcodeIdField;
    }

    Instruction(String name, int id, ResultType result, InputType... inputs) {
        this.name = name;
        this.id = id;
        this.results = new ResultType[]{result};
        this.inputs = inputs;
    }

    Instruction(String name, int id, ResultType[] results, InputType... inputs) {
        this.name = name;
        this.id = id;
        this.results = results;
        this.inputs = inputs;
    }

    public int numStackValuesExclVarargs() {
        int result = 0;
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] == InputType.STACK_VALUE) {
                result++;
            }
        }

        return result;
    }

    public TypeMirror[] expectedInputTypes(ProcessorContext context) {
        TypeMirror[] result = new TypeMirror[inputs.length];

        for (int i = 0; i < inputs.length; i++) {
            result[i] = inputs[i].getDefaultExecutionType(context);
        }
        return result;
    }

    public final CodeTree createReadArgumentCode(int n, ExecutionVariables vars) {
        if (!isArgumentInBytecode(n)) {
            return null;
        }

        return CodeTreeBuilder.createBuilder().startCall("LE_BYTES", "getShort") //
                        .variable(vars.bc) //
                        .startGroup().variable(vars.bci).string(" + " + getArgumentOffset(n)).end() //
                        .end().build();
    }

    public final CodeTree createWriteArgumentCode(int n, BuilderVariables vars, CodeTree val) {
        if (!isArgumentInBytecode(n)) {
            return null;
        }

        CodeTree value = val;

        if (n < inputs.length && inputs[n] == InputType.BRANCH_TARGET) {
            return CodeTreeBuilder.createBuilder().startStatement().startCall("createOffset") //
                            .startGroup().variable(vars.bci).string(" + " + getArgumentOffset(n)).end()//
                            .tree(value) //
                            .end(2).build();
        }

        if (n < inputs.length && inputs[n] == InputType.VARARG_VALUE) {
            value = CodeTreeBuilder.createBuilder().startParantheses().variable(vars.numChildren).string(" - " + numStackValuesExclVarargs()).end().build();
        }

        if (n < inputs.length && inputs[n] == InputType.CONST_POOL) {
            value = CodeTreeBuilder.createBuilder().startCall(vars.consts, "add").tree(value).end().build();
        }

        if (n >= inputs.length && results[n - inputs.length] == ResultType.SET_LOCAL) {
            value = CodeTreeBuilder.createBuilder().startCall("trackLocalsHelper").tree(value).end().build();
        }

        return CodeTreeBuilder.createBuilder().startStatement().startCall("LE_BYTES", "putShort") //
                        .variable(vars.bc) //
                        .startGroup().variable(vars.bci).string(" + " + getArgumentOffset(n)).end()  //
                        .startGroup().cast(new CodeTypeMirror(TypeKind.SHORT)).cast(new CodeTypeMirror(TypeKind.INT)).tree(value).end() //
                        .end(2).build();
    }

    public int opcodeLength() {
        return 1;
    }

    public int getArgumentOffset(int index) {
        int res = opcodeLength();
        for (int i = 0; i < index; i++) {
            if (i < inputs.length) {
                res += inputs[i].argumentLength;
            } else {
                res += results[i - inputs.length].argumentLength;
            }
        }
        return res;
    }

    public boolean isArgumentInBytecode(int index) {
        if (index < inputs.length) {
            return inputs[index].argumentLength > 0;
        } else {
            return results[index - inputs.length].argumentLength > 0;
        }
    }

    public boolean needsBuilderArgument(int index) {
        if (index < inputs.length) {
            return inputs[index].needsBuilderArgument();
        } else {
            return results[index - inputs.length].needsBuilderArgument();
        }
    }

    public int lengthWithoutState() {
        return getArgumentOffset(inputs.length + results.length);
    }

    public int length() {
        return lengthWithoutState() + getAdditionalStateBytes();
    }

    public List<TypeMirror> getBuilderArgumentTypes() {
        ProcessorContext context = ProcessorContext.getInstance();
        List<TypeMirror> result = new ArrayList<>();

        for (int i = 0; i < inputs.length; i++) {
            TypeMirror m = inputs[i].getDefaultBuilderType(context);
            if (m != null) {
                result.add(m);
            }
        }
        for (int i = 0; i < results.length; i++) {
            TypeMirror m = results[i].getDefaultBuilderType(context);
            if (m != null) {
                result.add(m);
            }
        }

        return result;
    }

    public CodeTree createEmitCode(BuilderVariables vars, CodeTree[] arguments) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        // emit opcode
        b.startStatement().variable(vars.bc).string("[").variable(vars.bci).string("] = ").variable(opcodeIdField).end();

        // emit arguments
        int argIndex = 0;
        for (int i = 0; i < inputs.length + results.length; i++) {
            CodeTree argument = needsBuilderArgument(i) ? arguments[argIndex++] : null;
            b.tree(createWriteArgumentCode(i, vars, argument));
        }

        // emit state bytes

        b.tree(createInitializeAdditionalStateBytes(vars, arguments));

        b.startAssign(vars.bci).variable(vars.bci).string(" + " + length()).end();

        // calculate stack offset
        int numPush = numPush();
        CodeTree numPop = numPop(vars);
        b.startAssign(vars.curStack).variable(vars.curStack).string(" + " + numPush + " - ").tree(numPop).end();
        if (numPush > 0) {
            b.startIf().variable(vars.curStack).string(" > ").variable(vars.maxStack).end();
            b.startBlock().startAssign(vars.maxStack).variable(vars.curStack).end(2);
        }

        return b.build();
    }

    public int numPush() {
        int stackPush = 0;
        for (ResultType r : results) {
            if (r == ResultType.STACK_VALUE) {
                stackPush++;
            }
        }
        return stackPush;
    }

    public CodeTree numPop(BuilderVariables vars) {
        int stackPop = 0;
        for (InputType i : inputs) {
            if (i == InputType.STACK_VALUE || i == InputType.STACK_VALUE_IGNORED) {
                stackPop++;
            } else if (i == InputType.VARARG_VALUE) {
                return CodeTreeBuilder.singleVariable(vars.numChildren);
            }
        }
        return CodeTreeBuilder.singleString("" + stackPop);
    }

    public abstract CodeTree createExecuteCode(ExecutionVariables vars);

    public boolean isInstrumentationOnly() {
        return false;
    }

    // state

    public int getAdditionalStateBytes() {
        return 0;
    }

    protected CodeTree createInitializeAdditionalStateBytes(BuilderVariables vars, CodeTree[] arguments) {
        return null;
    }
}
