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
package com.oracle.truffle.dsl.processor.bytecode.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionImmediate;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.ChildExecutionResult;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.FrameState;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.LocalVariable;
import com.oracle.truffle.dsl.processor.generator.NodeGeneratorPlugs;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.NodeChildData;
import com.oracle.truffle.dsl.processor.model.NodeExecutionData;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.model.TemplateMethod;

public class BytecodeDSLNodeGeneratorPlugs implements NodeGeneratorPlugs {

    private final ProcessorContext context;
    private final TypeMirror nodeType;
    private final BytecodeDSLModel model;
    private final BytecodeDSLNodeFactory bytecodeFactory;
    private InstructionModel instruction;

    private CodeExecutableElement quickenMethod;

    public BytecodeDSLNodeGeneratorPlugs(ProcessorContext context, BytecodeDSLNodeFactory bytecodeFactory, TypeMirror nodeType, BytecodeDSLModel model, InstructionModel instr) {
        this.bytecodeFactory = bytecodeFactory;
        this.context = context;
        this.nodeType = nodeType;
        this.model = model;
        this.instruction = instr;
    }

    public void setInstruction(InstructionModel instr) {
        this.instruction = instr;
    }

    @Override
    public List<? extends VariableElement> additionalArguments() {
        List<CodeVariableElement> result = new ArrayList<>();
        if (model.enableYield) {
            result.add(new CodeVariableElement(context.getTypes().VirtualFrame, "$stackFrame"));
        }
        result.addAll(List.of(
                        new CodeVariableElement(nodeType, "$root"),
                        new CodeVariableElement(context.getType(short[].class), "$bc"),
                        new CodeVariableElement(context.getType(int.class), "$bci"),
                        new CodeVariableElement(context.getType(int.class), "$sp")));
        return result;
    }

    @Override
    public ChildExecutionResult createExecuteChild(FlatNodeGenFactory factory, CodeTreeBuilder builder, FrameState originalFrameState, FrameState frameState, NodeExecutionData execution,
                    LocalVariable targetValue) {

        CodeTreeBuilder b = builder.create();

        b.string(targetValue.getName(), " = ");

        int index = execution.getIndex();
        boolean throwsUnexpectedResult = buildChildExecution(b, frameState, stackFrame(), index);

        return new ChildExecutionResult(b.build(), throwsUnexpectedResult);
    }

    public boolean canBoxingEliminateType(NodeExecutionData currentExecution, TypeMirror type) {
        return model.isBoxingEliminated(type);
    }

    private boolean buildChildExecution(CodeTreeBuilder b, FrameState frameState, String frame, int idx) {
        int index = idx;

        if (index < instruction.signature.valueCount) {
            TypeMirror targetType = instruction.signature.getSpecializedType(index);
            TypeMirror genericType = instruction.signature.getGenericType(index);
            TypeMirror expectedType = instruction.isQuickening() ? targetType : genericType;
            String stackIndex = "$sp - " + (instruction.signature.valueCount - index);
            if (instruction.getQuickeningRoot().needsBoxingElimination(model, idx)) {
                if (frameState.getMode().isFastPath()) {
                    b.startStatement();
                    if (ElementUtils.needsCastTo(expectedType, targetType)) {
                        b.startStaticCall(bytecodeFactory.lookupExpectMethod(expectedType, targetType));
                    }
                    BytecodeDSLNodeFactory.startExpectFrame(b, expectedType);
                    b.string(frame);
                    b.string(stackIndex);
                    b.end();
                    if (ElementUtils.needsCastTo(expectedType, targetType)) {
                        b.end();
                    }
                    b.end();
                    return true;
                } else {
                    if (!ElementUtils.isObject(genericType)) {
                        b.cast(targetType);
                    }
                    BytecodeDSLNodeFactory.startGetFrame(b, null);
                    b.string(frame).string(stackIndex);
                    b.end();
                    return false;
                }
            } else {
                if (!ElementUtils.isObject(genericType)) {
                    b.cast(expectedType);
                }
                b.string("ACCESS.uncheckedGetObject(" + frame + ", " + stackIndex + ")");
                return false;
            }

        }

        index -= instruction.signature.valueCount;

        if (index < instruction.signature.localSetterCount) {
            List<InstructionImmediate> imms = instruction.getImmediates(ImmediateKind.LOCAL_SETTER);
            InstructionImmediate imm = imms.get(index);
            b.startStaticCall(context.getTypes().LocalSetter, "get");
            b.string("ACCESS.shortArrayRead($bc, $bci + " + imm.offset() + ")");
            b.end();
            return false;
        }

        index -= instruction.signature.localSetterCount;
        if (index < instruction.signature.localSetterRangeCount) {
            List<InstructionImmediate> imms = instruction.getImmediates(ImmediateKind.LOCAL_SETTER_RANGE_START);
            InstructionImmediate imm = imms.get(index);
            b.startStaticCall(context.getTypes().LocalSetterRange, "get");
            b.string("ACCESS.shortArrayRead($bc, $bci + " + imm.offset() + ")"); // start
            b.string("ACCESS.shortArrayRead($bc, $bci + " + (imm.offset() + 1) + ")"); // length
            b.end();
            return false;
        }

        throw new AssertionError("index=" + index + ", signature=" + instruction.signature);
    }

    public CodeExecutableElement getQuickenMethod() {
        return quickenMethod;
    }

    public void notifySpecialize(FlatNodeGenFactory nodeFactory, CodeTreeBuilder builder, FrameState frameState, SpecializationData specialization) {
        if (model.specializationDebugListener) {
            bytecodeFactory.emitOnSpecialize(builder, "$root", "$bci", "$bc[$bci]", specialization.getNode().getNodeId() + "$" + specialization.getId());
        }

        if (instruction.hasQuickenings()) {
            if (quickenMethod == null) {
                quickenMethod = createQuickenMethod(nodeFactory, frameState);
            }
            builder.startStatement();
            builder.startCall("quicken");
            for (VariableElement var : quickenMethod.getParameters()) {
                builder.string(var.getSimpleName().toString());
            }
            builder.end();
            builder.end();
        }
    }

    private CodeExecutableElement createQuickenMethod(FlatNodeGenFactory factory, FrameState frameState) {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(Modifier.PRIVATE, Modifier.STATIC),
                        context.getType(void.class), "quicken");

        factory.addSpecializationStateParametersTo(method, frameState);
        if (model.specializationDebugListener) {
            method.addParameter(new CodeVariableElement(bytecodeFactory.getBytecodeNodeGen().asType(), "$root"));
        }
        method.addParameter(new CodeVariableElement(context.getType(short[].class), "$bc"));
        method.addParameter(new CodeVariableElement(context.getType(int.class), "$bci"));

        CodeTreeBuilder b = method.createBuilder();
        b.declaration(context.getType(short.class), "newInstruction");
        Set<Integer> boxingEliminated = new TreeSet<>();
        for (InstructionModel quickening : instruction.quickenedInstructions) {
            if (quickening.isReturnTypeQuickening()) {
                // not a valid target instruction -> selected only by parent
                continue;
            }
            for (int index = 0; index < quickening.signature.valueCount; index++) {
                if (model.isBoxingEliminated(quickening.signature.getSpecializedType(index))) {
                    boxingEliminated.add(index);
                }
            }
        }

        for (int valueIndex : boxingEliminated) {
            InstructionImmediate immediate = instruction.findImmediate(ImmediateKind.BYTECODE_INDEX, "child" + valueIndex + "_bci");

            b.startStatement();
            b.string("int oldOperandIndex" + valueIndex);
            b.string(" = ");
            b.string("ACCESS.shortArrayRead($bc, $bci + " + immediate.offset() + ")");
            b.end();
            b.startStatement();
            b.string("short oldOperand" + valueIndex);
            b.string(" = ");
            b.string("ACCESS.shortArrayRead($bc, oldOperandIndex" + valueIndex + ")");
            b.end();
            b.declaration(context.getType(short.class), "newOperand" + valueIndex);
        }

        boolean elseIf = false;
        for (InstructionModel quickening : instruction.quickenedInstructions) {
            if (quickening.isReturnTypeQuickening()) {
                // not a valid target instruction -> selected only by parent
                continue;
            }
            elseIf = b.startIf(elseIf);
            CodeTree activeCheck = factory.createOnlyActive(frameState, quickening.filteredSpecializations);
            b.tree(factory.createOnlyActive(frameState, quickening.filteredSpecializations));
            String sep = activeCheck.isEmpty() ? "" : " && ";

            for (int valueIndex : boxingEliminated) {
                TypeMirror specializedType = quickening.signature.getSpecializedType(valueIndex);
                if (model.isBoxingEliminated(specializedType)) {
                    b.newLine().string("  ", sep, "(");
                    b.string("newOperand" + valueIndex);
                    b.string(" = ");
                    b.startCall(BytecodeDSLNodeFactory.createApplyQuickeningName(specializedType)).string("oldOperand" + valueIndex).end();
                    b.string(") != -1");
                    sep = " && ";
                }
            }
            b.end().startBlock();

            for (int valueIndex : boxingEliminated) {
                TypeMirror specializedType = quickening.signature.getSpecializedType(valueIndex);
                if (!model.isBoxingEliminated(specializedType)) {
                    b.startStatement();
                    b.string("newOperand" + valueIndex, " = undoQuickening(oldOperand" + valueIndex + ")");
                    b.end();
                }
            }

            InstructionModel returnTypeQuickening = findReturnTypeQuickening(quickening);

            if (returnTypeQuickening != null) {
                b.startIf();
                b.startCall(BytecodeDSLNodeFactory.createIsQuickeningName(returnTypeQuickening.signature.returnType)).string("$bc[$bci]").end();
                b.end().startBlock();
                b.startStatement();
                b.string("newInstruction = ").tree(bytecodeFactory.createInstructionConstant(returnTypeQuickening));
                b.end(); // statement
                b.end().startElseBlock();
                b.startStatement();
                b.string("newInstruction = ").tree(bytecodeFactory.createInstructionConstant(quickening));
                b.end(); // statement
                b.end();
            } else {
                b.startStatement();
                b.string("newInstruction = ").tree(bytecodeFactory.createInstructionConstant(quickening));
                b.end(); // statement
            }

            b.end(); // if block
        }
        b.startElseBlock(elseIf);

        for (int valueIndex : boxingEliminated) {
            b.startStatement();
            b.string("newOperand" + valueIndex, " = undoQuickening(oldOperand" + valueIndex + ")");
            b.end();
        }

        InstructionModel returnTypeQuickening = findReturnTypeQuickening(instruction);
        if (returnTypeQuickening != null) {
            b.startIf();
            b.startCall(BytecodeDSLNodeFactory.createIsQuickeningName(returnTypeQuickening.signature.returnType)).string("$bc[$bci]").end();
            b.end().startBlock();
            b.startStatement();
            b.string("newInstruction = ").tree(bytecodeFactory.createInstructionConstant(returnTypeQuickening));
            b.end(); // statement
            b.end().startElseBlock();
            b.startStatement();
            b.string("newInstruction = ").tree(bytecodeFactory.createInstructionConstant(instruction));
            b.end(); // statement
            b.end(); // else block
        } else {
            b.startStatement();
            b.string("newInstruction = ").tree(bytecodeFactory.createInstructionConstant(instruction));
            b.end(); // statement
        }

        b.end(); // else block

        for (int valueIndex : boxingEliminated) {
            bytecodeFactory.emitQuickeningOperand(b, "$root", "$bc", "$bci", null, valueIndex, "oldOperandIndex" + valueIndex, "oldOperand" + valueIndex, "newOperand" + valueIndex);
        }
        bytecodeFactory.emitQuickening(b, "$root", "$bc", "$bci", null, "newInstruction");

        return method;
    }

    private static InstructionModel findReturnTypeQuickening(InstructionModel quickening) throws AssertionError {
        InstructionModel returnTypeQuickening = null;
        for (InstructionModel returnType : quickening.quickenedInstructions) {
            if (returnType.isReturnTypeQuickening()) {
                if (returnTypeQuickening != null) {
                    throw new AssertionError("Multiple return type quickenings not supported.");
                }
                returnTypeQuickening = returnType;
            }
        }
        return returnTypeQuickening;
    }

    @Override
    public String createNodeChildReferenceForException(FlatNodeGenFactory flatNodeGenFactory, FrameState frameState, NodeExecutionData execution, NodeChildData child) {
        return "null";
    }

    private String stackFrame() {
        return model.enableYield ? "$stackFrame" : TemplateMethod.FRAME_NAME;
    }

}
