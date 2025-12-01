/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.dsl.processor.bytecode.generator.BytecodeRootNodeElement.readImmediate;
import static com.oracle.truffle.dsl.processor.bytecode.generator.BytecodeRootNodeElement.readInstruction;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.bytecode.generator.BytecodeRootNodeElement.InterpreterTier;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLModel;
import com.oracle.truffle.dsl.processor.bytecode.model.ConstantOperandModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionImmediate;
import com.oracle.truffle.dsl.processor.bytecode.parser.BytecodeDSLParser;
import com.oracle.truffle.dsl.processor.bytecode.parser.SpecializationSignatureParser.SpecializationSignature;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Variable;
import com.oracle.truffle.dsl.processor.generator.BitSet;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.ChildExecutionResult;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.FrameState;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.LocalVariable;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.NodeExecutionMode;
import com.oracle.truffle.dsl.processor.generator.NodeGeneratorPlugs;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.ImplicitCastData;
import com.oracle.truffle.dsl.processor.model.NodeChildData;
import com.oracle.truffle.dsl.processor.model.NodeExecutionData;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.model.TemplateMethod;
import com.oracle.truffle.dsl.processor.parser.NodeParser;

public class BytecodeDSLNodeGeneratorPlugs implements NodeGeneratorPlugs {

    private final ProcessorContext context;
    private final TruffleTypes types;
    private final TypeMirror nodeType;
    private final BytecodeDSLModel model;
    private final BytecodeRootNodeElement rootNode;
    private InstructionModel instruction;

    private CodeExecutableElement quickenMethod;

    public BytecodeDSLNodeGeneratorPlugs(BytecodeRootNodeElement rootNode, InstructionModel instr) {
        this.rootNode = rootNode;
        this.model = rootNode.getModel();
        this.context = rootNode.getContext();
        this.types = context.getTypes();
        this.nodeType = rootNode.getAbstractBytecodeNode().asType();
        this.instruction = instr;
    }

    public void setInstruction(InstructionModel instr) {
        this.instruction = instr;
    }

    @Override
    public List<? extends VariableElement> additionalArguments() {
        List<CodeVariableElement> result = new ArrayList<>();
        if (model.hasYieldOperation()) {
            result.add(new CodeVariableElement(context.getTypes().VirtualFrame, "$stackFrame"));
        }
        result.add(new CodeVariableElement(nodeType, "$bytecode"));
        result.add(new CodeVariableElement(context.getType(byte[].class), "$bc"));
        result.add(new CodeVariableElement(context.getType(int.class), "$bci"));
        result.add(new CodeVariableElement(context.getType(int.class), "$sp"));
        return result;
    }

    public void modifyIntrospectionMethod(CodeExecutableElement m) {
        m.addParameter(new CodeVariableElement(types.Node, "$bytecode"));
        m.addParameter(new CodeVariableElement(context.getType(int.class), "$bci"));

        CodeTree body = m.getBodyTree();
        CodeTreeBuilder b = m.createBuilder();

        b.startDeclaration(context.getType(byte[].class), "$bc");
        b.maybeCast(types.Node, nodeType, "$bytecode").string(".bytecodes");
        b.end();

        b.tree(body);

    }

    @Override
    public ChildExecutionResult createExecuteChild(FlatNodeGenFactory factory, CodeTreeBuilder builder, FrameState originalFrameState, FrameState frameState, NodeExecutionData execution,
                    LocalVariable targetValue) {

        CodeTreeBuilder b = builder.create();

        b.string(targetValue.getName(), " = ");

        int index = execution.getIndex();
        boolean throwsUnexpectedResult = buildChildExecution(b, frameState, stackFrame(), index, targetValue.getTypeMirror());

        return new ChildExecutionResult(b.build(), throwsUnexpectedResult);
    }

    public boolean canEliminateTypeGuard(NodeExecutionData currentExecution, TypeMirror type) {
        int operandIndex = currentExecution.getIndex() - instruction.signature.constantOperandsBeforeCount;
        int operandCount = instruction.signature.dynamicOperandCount;
        if (operandIndex < 0) {
            // can always be eliminated as they are always precise
            return true;
        } else if (operandIndex < operandCount) {
            if (!instruction.isQuickening()) {
                return false;
            }

            final TypeMirror instructionType = instruction.signature.getDynamicOperandType(operandIndex);
            if (ElementUtils.typeEquals(instructionType, type) || instruction.nodeData.getTypeSystem().lookupCast(instructionType, type) != null) {
                return model.isBoxingEliminated(type);
            }
            return false;
        } else {
            // can always be eliminated as they are always precise
            return true;
        }
    }

    public void beforeCallSpecialization(FlatNodeGenFactory nodeFactory, CodeTreeBuilder builder, FrameState frameState,
                    SpecializationData specialization) {

        InterpreterTier tier = frameState.getMode() == NodeExecutionMode.UNCACHED ? InterpreterTier.UNCACHED : InterpreterTier.CACHED;
        if (BytecodeRootNodeElement.isStoreBciBeforeSpecialization(model, tier, instruction, specialization)) {
            BytecodeRootNodeElement.storeBciInFrame(builder, "frameValue", "$bci");
        }
    }

    private boolean buildChildExecution(CodeTreeBuilder b, FrameState frameState, String frame, int index, TypeMirror targetType) {
        int operandIndex = index;
        if (operandIndex < instruction.signature.constantOperandsBeforeCount) {
            ConstantOperandModel constantOperand = instruction.operation.constantOperands.before().get(operandIndex);
            InstructionImmediate imm = instruction.constantOperandImmediates.get(constantOperand);
            if (imm == null) {
                throw new AssertionError("Could not find an immediate for constant operand " + constantOperand + " on instruction " + instruction);
            }
            b.tree(rootNode.readConstantImmediate("$bc", "$bci", "$bytecode", imm, constantOperand.type()));
            return false;
        }
        operandIndex -= instruction.signature.constantOperandsBeforeCount;
        int operandCount = instruction.signature.dynamicOperandCount;
        if (operandIndex < operandCount) {
            return buildOperandExecution(b, frameState, frame, targetType, operandIndex, operandCount);
        }
        operandIndex -= instruction.signature.dynamicOperandCount;

        int constantOperandAfterCount = instruction.signature.constantOperandsAfterCount;
        if (operandIndex < constantOperandAfterCount) {
            ConstantOperandModel constantOperand = instruction.operation.constantOperands.after().get(operandIndex);
            InstructionImmediate imm = instruction.constantOperandImmediates.get(constantOperand);
            if (imm == null) {
                throw new AssertionError("Could not find an immediate for constant operand " + constantOperand + " on instruction " + instruction);
            }
            b.tree(rootNode.readConstantImmediate("$bc", "$bci", "$bytecode", imm, constantOperand.type()));
            return false;
        }

        throw new AssertionError("index=" + index + ", signature=" + instruction.signature);
    }

    private boolean buildOperandExecution(CodeTreeBuilder b, FrameState frameState, String frame, TypeMirror targetType, int operandIndex, int operandCount) throws AssertionError {
        final TypeMirror instructionType = instruction.signature.getDynamicOperandType(operandIndex);
        String stackIndex = "$sp - " + (operandCount - operandIndex);

        // slow paths never need boxing elimination (e.g. in the uncached interpreter)
        final boolean fastPath = frameState.getMode().isFastPath();

        /*
         * We only want boxing elimination in the cached interpreter, when the operand type is
         * boxing eliminated and the instruction is a quickening. Without the quickening we cannot
         * boxing eliminate as the operands need to be switched as well.
         */
        final boolean boxingEliminated = fastPath && model.isBoxingEliminated(instructionType) && instruction.isQuickening();

        boolean expectOtherTypes = instruction.getQuickeningRoot().needsChildBciForBoxingElimination(model, operandIndex);
        if (boxingEliminated) {
            if (!expectOtherTypes) {
                // Sanity check for the internal consistency of boxing elimination and
                // needsChildBciForBoxingElimination.
                throw new AssertionError("expectOtherTypes must be true with boxing elimination");
            }

            final ImplicitCastData cast;
            if (model.isBoxingEliminated(instructionType) && !ElementUtils.typeEquals(instructionType, targetType) && !ElementUtils.isObject(targetType)) {
                cast = instruction.nodeData.getTypeSystem().lookupCast(instructionType, targetType);
                if (cast == null) {
                    throw new AssertionError("Instruction type must match the declared type unless for implicit casts: " + instruction + ": " + instructionType + " -> " + targetType);
                }
            } else {
                cast = null;
            }

            if (cast != null) {
                b.startStaticCall(cast.getMethod());
            }

            BytecodeRootNodeElement.startExpectFrameUnsafe(b, frame, instructionType);
            b.string(stackIndex);
            b.end();

            if (cast != null) {
                b.end();
            }

            return true; // unexpected exception thrown
        } else {
            // expected type is always Object without boxing elimination

            /*
             * This currently only happens for variadic arguments where the target type is Object[]
             * but the expected type is Object. If we introduce new types, we might need to expand
             * this check.
             */
            if (ElementUtils.typeEquals(targetType, context.getType(Object[].class))) {
                b.cast(targetType);
            }

            if (expectOtherTypes) {
                if (fastPath) {
                    // frame.expectObject(index)
                    BytecodeRootNodeElement.startExpectFrameUnsafe(b, frame, context.getType(Object.class));
                    b.string(stackIndex);
                    b.end();
                    return true; // unexpected exception thrown
                } else {
                    // frame.getValue(index)
                    BytecodeRootNodeElement.startGetFrameUnsafe(b, frame, null);
                    b.string(stackIndex);
                    b.end();
                    return false;
                }
            } else {
                /*
                 * If boxing elimination is not used in the interpreter version then we can assume
                 * the frame tag is object.
                 */
                // frame.getObject(index)
                b.string(BytecodeRootNodeElement.uncheckedGetFrameObject(frame, stackIndex));
                return false; // no unexpected exception thrown
            }
        }
    }

    public CodeExecutableElement getQuickenMethod() {
        return quickenMethod;
    }

    @Override
    public void notifySpecialize(FlatNodeGenFactory nodeFactory, CodeTreeBuilder builder, FrameState frameState, SpecializationData specialization) {
        if (model.bytecodeDebugListener) {
            rootNode.emitOnSpecialize(builder, "$bytecode", "$bci", BytecodeRootNodeElement.readInstruction("$bc", "$bci"), specialization.getNode().getNodeId() + "$" + specialization.getId());
        }

        if (instruction.getQuickeningRoot().hasSpecializedQuickenings()) {
            if (quickenMethod == null) {
                quickenMethod = createQuickenMethod(nodeFactory, frameState);
            }

            nodeFactory.loadQuickeningStateBitSets(builder, frameState, instruction.nodeData.getReachableSpecializations());

            builder.startStatement();
            builder.startCall("quicken");
            for (VariableElement var : quickenMethod.getParameters()) {
                builder.string(var.getSimpleName().toString());
            }
            builder.end();
            builder.end();
        }
    }

    public CodeTree bindExpressionValue(FrameState frameState, Variable variable) {
        switch (variable.getName()) {
            case NodeParser.SYMBOL_THIS:
            case NodeParser.SYMBOL_NODE:

                if (frameState.getMode().isUncached()) {
                    return CodeTreeBuilder.singleString("$bytecode");
                } else if (instruction.canUseNodeSingleton()) {
                    /*
                     * When node singletons are used we must never bind the singleton node.
                     *
                     * It is safe to do so because the instruction does not bind any node if
                     * canUseNodeSingleton() is true, or the bytecode index is already stored in the
                     * frame. The bytecode index is always stored in the frame for uncached or when
                     * GenerateBytecode.storeBytecodeIndexInFrame() is enabled in the cached
                     * interpreter.
                     */
                    return CodeTreeBuilder.singleString("$bytecode");
                } else {
                    // use default handling (which could resolve to the specialization class)
                    return null;
                }
            case BytecodeDSLParser.SYMBOL_BYTECODE_NODE:
                return CodeTreeBuilder.singleString("$bytecode");
            case BytecodeDSLParser.SYMBOL_ROOT_NODE:
                return CodeTreeBuilder.singleString("$bytecode.getRoot()");
            case BytecodeDSLParser.SYMBOL_BYTECODE_INDEX:
                return CodeTreeBuilder.singleString("$bci");
            case BytecodeDSLParser.SYMBOL_CONTINUATION_ROOT:
                InstructionImmediate continuationIndex = instruction.getImmediates(ImmediateKind.CONSTANT).getLast();
                return CodeTreeBuilder.createBuilder().tree(rootNode.readConstantImmediate("$bc", "$bci", "$bytecode", continuationIndex, rootNode.getContinuationRootNodeImpl().asType())).build();
            default:
                return NodeGeneratorPlugs.super.bindExpressionValue(frameState, variable);

        }
    }

    private CodeExecutableElement createQuickenMethod(FlatNodeGenFactory factory, FrameState frameState) {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(Modifier.PRIVATE, Modifier.STATIC), context.getType(void.class), "quicken");

        factory.addQuickeningStateParametersTo(method, frameState, instruction.nodeData.getReachableSpecializations());
        if (model.bytecodeDebugListener) {
            method.addParameter(new CodeVariableElement(rootNode.getAbstractBytecodeNode().asType(), "$bytecode"));
        }
        method.addParameter(new CodeVariableElement(context.getType(byte[].class), "$bc"));
        method.addParameter(new CodeVariableElement(context.getType(int.class), "$bci"));

        CodeTreeBuilder b = method.createBuilder();
        b.declaration(context.getType(short.class), "newInstruction");
        Set<Integer> boxingEliminated = new TreeSet<>();
        List<InstructionModel> relevantQuickenings = instruction.quickenedInstructions.stream() //
                        .filter(q -> !q.isReturnTypeQuickening() /* selected only by parent */ && q.filteredSpecializations != null) //
                        .toList();

        for (InstructionModel quickening : relevantQuickenings) {
            for (int index = 0; index < quickening.signature.dynamicOperandCount; index++) {
                if (model.isBoxingEliminated(quickening.signature.getDynamicOperandType(index))) {
                    boxingEliminated.add(index);
                }
            }
        }

        for (int valueIndex : boxingEliminated) {
            InstructionImmediate immediate = instruction.findImmediate(ImmediateKind.BYTECODE_INDEX, "child" + valueIndex);

            b.startStatement();
            b.string("int oldOperandIndex" + valueIndex);
            b.string(" = ");
            b.tree(readImmediate("$bc", "$bci", immediate));
            b.end();

            if (instruction.isShortCircuitConverter() || instruction.isEpilogReturn()) {
                b.declaration(context.getType(short.class), "oldOperand" + valueIndex);

                b.startIf().string("oldOperandIndex" + valueIndex).string(" != -1").end().startBlock();
                b.startStatement();
                b.string("oldOperand" + valueIndex);
                b.string(" = ");
                b.tree(readInstruction("$bc", "oldOperandIndex" + valueIndex));
                b.end(); // statement
                b.end().startElseBlock();
                b.startStatement();
                b.string("oldOperand" + valueIndex);
                b.string(" = ");
                b.string("-1");
                b.end(); // statement
                b.end(); // if
            } else {
                b.startStatement();
                b.string("short oldOperand" + valueIndex);
                b.string(" = ");
                b.tree(readInstruction("$bc", "oldOperandIndex" + valueIndex));
                b.end(); // statement
            }

            b.declaration(context.getType(short.class), "newOperand" + valueIndex);
        }

        boolean elseIf = false;
        for (InstructionModel quickening : relevantQuickenings) {
            elseIf = b.startIf(elseIf);
            CodeTree activeCheck = factory.createOnlyActive(frameState, quickening.filteredSpecializations, instruction.nodeData.getReachableSpecializations());
            b.tree(activeCheck);
            String sep = activeCheck.isEmpty() ? "" : " && ";

            SpecializationSignature specializationSignature = quickening.operation.getSpecializationSignature(quickening.filteredSpecializations);
            List<TypeMirror> dynamicOperandTypes = specializationSignature.signature().getDynamicOperandTypes();

            for (int valueIndex : boxingEliminated) {
                TypeMirror specializedType = quickening.signature.getDynamicOperandType(valueIndex);
                TypeMirror specializationTargetType = dynamicOperandTypes.get(valueIndex);
                CodeTree check = factory.createIsImplicitTypeStateCheck(frameState, specializedType, specializationTargetType,
                                valueIndex + specializationSignature.signature().constantOperandsBeforeCount);
                if (check == null) {
                    continue;
                }
                b.newLine().string("  ", sep, "(");
                sep = " && ";
                b.tree(check);
                b.string(")");
            }

            for (int valueIndex : boxingEliminated) {
                TypeMirror specializedType = quickening.signature.getDynamicOperandType(valueIndex);
                if (model.isBoxingEliminated(specializedType)) {
                    b.newLine().string("  ", sep, "(");
                    b.string("newOperand" + valueIndex);
                    b.string(" = ");
                    b.startCall(BytecodeRootNodeElement.createApplyQuickeningName(specializedType)).string("oldOperand" + valueIndex).end();
                    b.string(") != -1");
                    sep = " && ";
                }
            }
            b.end().startBlock();

            for (int valueIndex : boxingEliminated) {
                TypeMirror specializedType = quickening.signature.getDynamicOperandType(valueIndex);
                if (!model.isBoxingEliminated(specializedType)) {
                    b.startStatement();
                    b.string("newOperand" + valueIndex, " = undoQuickening(oldOperand" + valueIndex + ")");
                    b.end();
                }
            }

            List<InstructionModel> returnTypeQuickenings = findReturnTypeQuickenings(quickening);

            if (!returnTypeQuickenings.isEmpty()) {
                elseIf = false;
                for (InstructionModel returnTypeQuickening : returnTypeQuickenings) {
                    elseIf = b.startIf(elseIf);
                    b.startCall(BytecodeRootNodeElement.createIsQuickeningName(returnTypeQuickening.signature.returnType)).tree(BytecodeRootNodeElement.readInstruction("$bc", "$bci")).end();
                    b.end().startBlock();
                    b.startStatement();
                    b.string("newInstruction = ").tree(rootNode.createInstructionConstant(returnTypeQuickening));
                    b.end(); // statement
                    b.end(); // block
                }
                b.startElseBlock();
                b.startStatement();
                b.string("newInstruction = ").tree(rootNode.createInstructionConstant(quickening));
                b.end(); // statement
                b.end();
            } else {
                b.startStatement();
                b.string("newInstruction = ").tree(rootNode.createInstructionConstant(quickening));
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

        List<InstructionModel> returnTypeQuickenings = findReturnTypeQuickenings(instruction);
        if (!returnTypeQuickenings.isEmpty()) {
            elseIf = false;
            for (InstructionModel returnTypeQuickening : returnTypeQuickenings) {
                elseIf = b.startIf(elseIf);
                b.startCall(BytecodeRootNodeElement.createIsQuickeningName(returnTypeQuickening.signature.returnType)).tree(BytecodeRootNodeElement.readInstruction("$bc", "$bci")).end();
                b.end().startBlock();
                b.startStatement();
                b.string("newInstruction = ").tree(rootNode.createInstructionConstant(returnTypeQuickening));
                b.end(); // statement
                b.end(); // block
            }
            b.startElseBlock();
            b.startStatement();
            b.string("newInstruction = ").tree(rootNode.createInstructionConstant(instruction));
            b.end(); // statement
            b.end(); // else block
        } else {
            b.startStatement();
            b.string("newInstruction = ").tree(rootNode.createInstructionConstant(instruction));
            b.end(); // statement
        }

        b.end(); // else block

        for (int valueIndex : boxingEliminated) {
            if (instruction.isShortCircuitConverter()) {
                b.startIf().string("newOperand" + valueIndex).string(" != -1").end().startBlock();
                rootNode.emitQuickeningOperand(b, "$bytecode", "$bc", "$bci", null, valueIndex, "oldOperandIndex" + valueIndex, "oldOperand" + valueIndex, "newOperand" + valueIndex);
                b.end(); // if
            } else {
                rootNode.emitQuickeningOperand(b, "$bytecode", "$bc", "$bci", null, valueIndex, "oldOperandIndex" + valueIndex, "oldOperand" + valueIndex, "newOperand" + valueIndex);
            }
        }

        rootNode.emitQuickening(b, "$bytecode", "$bc", "$bci", null, "newInstruction");

        return method;
    }

    private static List<InstructionModel> findReturnTypeQuickenings(InstructionModel quickening) throws AssertionError {
        List<InstructionModel> returnTypeQuickenings = new ArrayList<>();
        for (InstructionModel returnType : quickening.quickenedInstructions) {
            if (returnType.isReturnTypeQuickening()) {
                returnTypeQuickenings.add(returnType);
            }
        }
        return returnTypeQuickenings;
    }

    @Override
    public String createNodeChildReferenceForException(FlatNodeGenFactory flatNodeGenFactory, FrameState frameState, NodeExecutionData execution, NodeChildData child) {
        return "null";
    }

    public CodeVariableElement createStateField(FlatNodeGenFactory factory, BitSet bitSet) {
        if (instruction.canInlineState()) {
            return null;
        }
        return NodeGeneratorPlugs.super.createStateField(factory, bitSet);
    }

    public CodeTree createStateLoad(FlatNodeGenFactory factory, FrameState frameState, BitSet bitSet) {
        if (instruction.canInlineState()) {
            InstructionImmediate imm = instruction.findImmediate(ImmediateKind.STATE_PROFILE, bitSet.getName());
            if (imm == null) {
                throw new AssertionError("Immediate not found " + bitSet.getName());
            }
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
            b.startStaticCall(context.getType(Short.class), "toUnsignedInt");
            b.tree(BytecodeRootNodeElement.readImmediate("$bc", "$bci", imm));
            b.end();
            return b.build();
        }
        return NodeGeneratorPlugs.super.createStateLoad(factory, frameState, bitSet);
    }

    public CodeTree createStatePersist(FlatNodeGenFactory factory, FrameState frameState, BitSet bitSet, CodeTree valueTree) {
        if (instruction.canInlineState()) {
            InstructionImmediate imm = instruction.findImmediate(ImmediateKind.STATE_PROFILE, bitSet.getName());
            if (imm == null) {
                return CodeTreeBuilder.singleString("/* " + bitSet.getName() + " not found " + instruction.getImmediates() + " */");
            }
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
            b.string("(short) (");
            b.tree(valueTree);
            b.string(" & 0xFFFF)");
            return BytecodeRootNodeElement.writeImmediate("$bc", "$bci", b.build(), imm.encoding());
        }

        return NodeGeneratorPlugs.super.createStatePersist(factory, frameState, bitSet, valueTree);
    }

    public int getMaxStateBitWidth() {
        if (instruction.canInlineState()) {
            return Short.SIZE;
        }
        return NodeGeneratorPlugs.super.getMaxStateBitWidth();
    }

    private String stackFrame() {
        return model.hasYieldOperation() ? "$stackFrame" : TemplateMethod.FRAME_NAME;
    }

}
