/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.operations.instructions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.Operation.BuilderVariables;
import com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils;

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
        public CodeVariableElement tracer;
    }

    public enum InputType {
        STACK_VALUE(1),
        STACK_VALUE_IGNORED(0),
        VARARG_VALUE(1),
        CONST_POOL(1),
        LOCAL(1),
        ARGUMENT(1),
        INSTRUMENT(1),
        BRANCH_PROFILE(1),
        BRANCH_TARGET(1);

        final int argumentLength;

        InputType(int argumentLength) {
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
                case BRANCH_PROFILE:
                    return context.getDeclaredType("com.oracle.truffle.api.profiles.ConditionProfile");
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
                case BRANCH_PROFILE:
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
                case BRANCH_PROFILE:
                    return null;
                case CONST_POOL:
                    return context.getType(Object.class);
                case LOCAL:
                    return context.getTypes().OperationLocal;
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
                case BRANCH_PROFILE:
                    return CodeTreeBuilder.singleString("ConditionProfile.createCountingProfile()");
                case VARARG_VALUE:
                    return CodeTreeBuilder.createBuilder().variable(vars.numChildren).string(" - " + instr.numStackValuesExclVarargs()).build();
                default:
                    throw new IllegalArgumentException("Unexpected value: " + this);
            }
        }

        public CodeTree createDumpCode(int n, Instruction op, ExecutionVariables vars) {
            switch (this) {
                case STACK_VALUE:
                    return CodeTreeBuilder.createBuilder().startStatement().startCall("sb", "append").startCall("String", "format").doubleQuote("pop[-%d]").startGroup().variable(vars.bc).string(
                                    "[").variable(vars.bci).string(" + " + op.getArgumentOffset(n) + "]").end().end(3).build();
                case STACK_VALUE_IGNORED:
                    return CodeTreeBuilder.createBuilder().statement("sb.append(\"_\")").build();
                case CONST_POOL:
                    return CodeTreeBuilder.createBuilder().startBlock().declaration("Object", "o",
                                    CodeTreeBuilder.createBuilder().variable(vars.consts).string("[").tree(op.createReadArgumentCode(n, vars)).string("]").build()).startStatement().startCall("sb",
                                                    "append").startCall("String", "format").doubleQuote("%s %s").string("o.getClass().getSimpleName()").string("o").end(4).build();
                case LOCAL:
                    return CodeTreeBuilder.createBuilder().startStatement().startCall("sb", "append").startCall("String", "format").doubleQuote("loc[%d]").tree(op.createReadArgumentCode(n, vars)).end(
                                    3).build();
                case ARGUMENT:
                    return CodeTreeBuilder.createBuilder().startStatement().startCall("sb", "append").startCall("String", "format").doubleQuote("arg[%d]").tree(op.createReadArgumentCode(n, vars)).end(
                                    3).build();
                case BRANCH_TARGET:
                    return CodeTreeBuilder.createBuilder().startStatement().startCall("sb", "append").startCall("String", "format").doubleQuote("%04x").tree(op.createReadArgumentCode(n, vars)).end(
                                    3).build();
                case INSTRUMENT:
                    return CodeTreeBuilder.createBuilder().startStatement().startCall("sb", "append").startCall("String", "format").doubleQuote("instrument[%d]").tree(
                                    op.createReadArgumentCode(n, vars)).end(3).build();
                case VARARG_VALUE:
                    return CodeTreeBuilder.createBuilder().startStatement().startCall("sb", "append").startCall("String", "format").doubleQuote("**%d").tree(op.createReadArgumentCode(n, vars)).end(
                                    3).build();
                case BRANCH_PROFILE:
                    return null;
                default:
                    throw new IllegalArgumentException("Unexpected value: " + this);
            }
        }

        boolean isStackValue() {
            return this == STACK_VALUE || this == STACK_VALUE_IGNORED;
        }

    }

    public enum ResultType {
        STACK_VALUE(0),
        SET_LOCAL(1),
        BRANCH(0),
        RETURN(0);

        final int argumentLength;

        ResultType(int argumentLength) {
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
                    return context.getTypes().OperationLocal;
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
                    return CodeTreeBuilder.createBuilder().startStatement().startCall("sb", "append").startCall("String", "format").doubleQuote("loc[%d]").tree(
                                    op.createReadArgumentCode(i + op.inputs.length, vars)).end(3).build();
                default:
                    throw new IllegalArgumentException("Unexpected value: " + this);
            }
        }

    }

    public enum BoxingEliminationBehaviour {
        DO_NOTHING,
        SET_BIT,
        REPLACE
    }

    public final String name;
    public final int id;
    public final InputType[] inputs;
    public final ResultType[] results;

    @SuppressWarnings("unused")
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

        return CodeTreeBuilder.createBuilder().variable(vars.bc).string("[").variable(vars.bci).string(" + " + getArgumentOffset(n)).string("]").build();
    }

    public final CodeTree createWriteArgumentCode(int n, BuilderVariables vars, CodeTree val) {
        if (!isArgumentInBytecode(n)) {
            return null;
        }

        CodeTree value = val;

        if (n < inputs.length && inputs[n] == InputType.BRANCH_TARGET) {
            return CodeTreeBuilder.createBuilder().startStatement().startCall("createOffset").startGroup().variable(vars.bci).string(" + " + getArgumentOffset(n)).end().tree(value).end(2).build();
        }

        if (n < inputs.length && inputs[n] == InputType.STACK_VALUE) {
            int svIndex = 0;
            for (int i = 0; i < n; i++) {
                if (inputs[i].isStackValue()) {
                    svIndex++;
                }
            }

            return CodeTreeBuilder.createBuilder().startStatement().variable(vars.bc).string("[").variable(vars.bci).string(" + " + getArgumentOffset(n)).string("] = ").string(
                            "predecessorBcis[" + svIndex + "] < ").variable(vars.bci).string(" - 255").string(" ? 0").string(" : (byte)(").variable(vars.bci).string(
                                            " - predecessorBcis[" + svIndex + "])").end().build();
        }

        if (n < inputs.length && inputs[n] == InputType.VARARG_VALUE) {
            value = CodeTreeBuilder.createBuilder().startParantheses().variable(vars.numChildren).string(" - " + numStackValuesExclVarargs()).end().build();
        }

        if (n < inputs.length && inputs[n] == InputType.BRANCH_PROFILE) {
            value = CodeTreeBuilder.singleString("createBranchProfile()");
        }

        if (n < inputs.length && inputs[n] == InputType.CONST_POOL) {
            value = CodeTreeBuilder.createBuilder().startCall(vars.consts, "add").tree(value).end().build();
        }

        if ((n < inputs.length && inputs[n] == InputType.LOCAL) || (n >= inputs.length && results[n - inputs.length] == ResultType.SET_LOCAL)) {
            value = CodeTreeBuilder.createBuilder().startCall("getLocalIndex").tree(value).end().build();
        }

        return CodeTreeBuilder.createBuilder().startStatement().variable(vars.bc).string("[").variable(vars.bci).string(" + " + getArgumentOffset(n)).string("] = ").cast(
                        new CodeTypeMirror(TypeKind.SHORT)).cast(new CodeTypeMirror(TypeKind.INT)).tree(value).end().build();
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

    public int getStackValueArgumentOffset(int index) {
        int svIndex = 0;
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i].isStackValue()) {
                if (svIndex == index) {
                    return getArgumentOffset(i);
                } else {
                    svIndex++;
                }
            }
        }

        throw new AssertionError("should not reach here");
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

    @SuppressWarnings("unused")
    protected CodeTree createCustomEmitCode(BuilderVariables vars, CodeTree[] arguments) {
        return null;
    }

    @SuppressWarnings("unused")
    protected CodeTree createCustomEmitCodeAfter(BuilderVariables vars, CodeTree[] arguments) {
        return null;
    }

    public final CodeTree createEmitCode(BuilderVariables vars, CodeTree[] arguments) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.tree(createCustomEmitCode(vars, arguments));

        // calculate stack offset
        int numPush = numPush();
        CodeTree numPop = numPop(vars);

        assert numPush == 1 || numPush == 0;

        b.startAssign("int[] predecessorBcis");
        b.startCall("doBeforeEmitInstruction");
        b.tree(numPop);
        b.string(numPush == 0 ? "false" : "true");
        b.end(2);

        // emit opcode
        b.tree(OperationGeneratorUtils.createWriteOpcode(vars.bc, vars.bci, opcodeIdField));

        // emit arguments
        int argIndex = 0;
        for (int i = 0; i < inputs.length + results.length; i++) {
            CodeTree argument = needsBuilderArgument(i) ? arguments[argIndex++] : null;
            b.tree(createWriteArgumentCode(i, vars, argument));
        }

        // emit state bytes

        b.tree(createInitializeAdditionalStateBytes(vars, arguments));

        b.startAssign(vars.bci).variable(vars.bci).string(" + " + length()).end();

        b.tree(createCustomEmitCodeAfter(vars, arguments));

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

    public boolean isVariadic() {
        for (InputType i : inputs) {
            if (i == InputType.VARARG_VALUE) {
                return true;
            }
        }

        return false;
    }

    public int numPopStatic() {
        int stackPop = 0;
        for (InputType i : inputs) {
            if (i == InputType.STACK_VALUE || i == InputType.STACK_VALUE_IGNORED) {
                stackPop++;
            } else if (i == InputType.VARARG_VALUE) {
                throw new UnsupportedOperationException("number of pops not static");
            }
        }
        return stackPop;
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

    @SuppressWarnings("unused")
    protected CodeTree createInitializeAdditionalStateBytes(BuilderVariables vars, CodeTree[] arguments) {
        return null;
    }

    public String dumpInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("\n");

        sb.append("  Inputs:\n");
        for (InputType type : inputs) {
            sb.append("    ").append(type).append("\n");
        }
        sb.append("  Results:\n");
        for (ResultType type : results) {
            sb.append("    ").append(type).append("\n");
        }

        return sb.toString();
    }

    public boolean standardPrologue() {
        return true;
    }

    public boolean isBranchInstruction() {
        return Arrays.stream(results).anyMatch(x -> x == ResultType.BRANCH);
    }

    public boolean isReturnInstruction() {
        return Arrays.stream(results).anyMatch(x -> x == ResultType.RETURN);
    }

    public abstract BoxingEliminationBehaviour boxingEliminationBehaviour();

    @SuppressWarnings("unused")
    public CodeVariableElement boxingEliminationReplacement(FrameKind kind) {
        throw new AssertionError();
    }

    public int boxingEliminationBitOffset() {
        throw new AssertionError();
    }

    public int boxingEliminationBitMask() {
        throw new AssertionError();
    }

    public abstract CodeTree createPrepareAOT(ExecutionVariables vars, CodeTree language, CodeTree root);

    public CodeTree getPredecessorOffset(ExecutionVariables vars, int index) {
        int curIndex = index;
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] == InputType.STACK_VALUE || inputs[i] == InputType.STACK_VALUE_IGNORED) {
                if (curIndex-- == 0) {
                    return CodeTreeBuilder.createBuilder().startParantheses().variable(vars.bc).string("[").variable(vars.bci).string(" + " + getArgumentOffset(i)).string("] & 0xff").end().build();
                }
            }
        }

        throw new AssertionError("should not reach here");
    }

    @SuppressWarnings("unused")
    public CodeTree[] createTracingArguments(ExecutionVariables vars) {
        return new CodeTree[0];
    }

    public int numLocalReferences() {
        return 0;
    }
}
