/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.parser.LLVMPhiManager.Phi;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionKind;
import com.oracle.truffle.llvm.parser.model.enums.AsmDialect;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.symbols.constants.InlineAsmConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.NullConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.AllocateInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.BinaryOperationInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.BranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CallInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CastInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CompareExchangeInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CompareInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ConditionalBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ExtractElementInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ExtractValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.GetElementPointerInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.IndirectBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InsertElementInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InsertValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InvokeInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.LandingpadInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.LoadInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.PhiInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ResumeInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ReturnInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SelectInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ShuffleVectorInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.StoreInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SwitchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SwitchOldInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.TerminatingInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.UnreachableInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidInvokeInstruction;
import com.oracle.truffle.llvm.parser.model.visitors.InstructionVisitor;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolResolver;
import com.oracle.truffle.llvm.parser.util.LLVMBitcodeTypeHelper;
import com.oracle.truffle.llvm.runtime.LLVMException;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

final class LLVMBitcodeInstructionVisitor implements InstructionVisitor {

    private final FrameDescriptor frame;
    private final Map<String, Integer> labels;
    private final List<Phi> blockPhis;
    private final SulongNodeFactory nodeFactory;
    private final int argCount;
    private final LLVMSymbolResolver symbols;
    private final LLVMParserRuntime runtime;
    private final ArrayList<LLVMLivenessAnalysis.NullerInformation> nullerInfos;
    private final List<? extends FrameSlot> frameSlots;

    private final List<LLVMExpressionNode> blockInstructions;
    private int instructionIndex;
    private LLVMControlFlowNode controlFlowNode;

    LLVMBitcodeInstructionVisitor(FrameDescriptor frame, Map<String, Integer> labels,
                    List<Phi> blockPhis, SulongNodeFactory nodeFactory, int argCount, LLVMSymbolResolver symbols, LLVMParserRuntime runtime,
                    ArrayList<LLVMLivenessAnalysis.NullerInformation> nullerInfos) {
        this.frame = frame;
        this.labels = labels;
        this.blockPhis = blockPhis;
        this.nodeFactory = nodeFactory;
        this.argCount = argCount;
        this.symbols = symbols;
        this.runtime = runtime;
        this.nullerInfos = nullerInfos;
        this.frameSlots = frame.getSlots();

        this.blockInstructions = new ArrayList<>();
    }

    public LLVMExpressionNode[] getInstructions() {
        return blockInstructions.toArray(new LLVMExpressionNode[0]);
    }

    public LLVMControlFlowNode getControlFlowNode() {
        return controlFlowNode;
    }

    public void setInstructionIndex(int instructionIndex) {
        this.instructionIndex = instructionIndex;
    }

    @Override
    public void visit(AllocateInstruction allocate) {
        final Type type = allocate.getPointeeType();
        int alignment;
        if (allocate.getAlign() == 0) {
            alignment = runtime.getByteAlignment(type);
        } else {
            alignment = 1 << (allocate.getAlign() - 1);
        }
        if (alignment == 0) {
            alignment = LLVMStack.NO_ALIGNMENT_REQUIREMENTS;
        }

        final int size = runtime.getByteSize(type);
        final Symbol count = allocate.getCount();
        final LLVMExpressionNode result;
        if (count instanceof NullConstant) {
            result = nodeFactory.createAlloc(runtime, type, size, alignment, null, null);
        } else if (count instanceof IntegerConstant) {
            if (type instanceof VariableBitWidthType) {
                result = nodeFactory.createAlloc(runtime, type, size * (int) ((IntegerConstant) count).getValue(), alignment, null, null);
            } else {
                result = nodeFactory.createAlloc(runtime, type, size * (int) ((IntegerConstant) count).getValue(), alignment, null, null);
            }
        } else {
            LLVMExpressionNode num = symbols.resolve(count);
            result = nodeFactory.createAlloc(runtime, type, size, alignment, count.getType(), num);
        }

        createFrameWrite(result, allocate);
    }

    @Override
    public void visit(BinaryOperationInstruction operation) {
        LLVMExpressionNode lhs = symbols.resolve(operation.getLHS());
        LLVMExpressionNode rhs = symbols.resolve(operation.getRHS());

        final Type type = operation.getType();
        final LLVMArithmeticInstructionType opA = LLVMBitcodeTypeHelper.toArithmeticInstructionType(operation.getOperator());
        if (opA != null) {
            final LLVMExpressionNode result = nodeFactory.createArithmeticOperation(runtime, lhs, rhs, opA, type, operation.getFlags());
            createFrameWrite(result, operation);
            return;
        }

        final LLVMLogicalInstructionKind opL = LLVMBitcodeTypeHelper.toLogicalInstructionType(operation.getOperator());
        if (opL != null) {
            final LLVMExpressionNode result = nodeFactory.createLogicalOperation(runtime, lhs, rhs, opL, type, operation.getFlags());
            createFrameWrite(result, operation);
            return;
        }

        throw new RuntimeException("Missed a binary operator");
    }

    @Override
    public void visit(BranchInstruction branch) {
        LLVMControlFlowNode unconditionalBranchNode = nodeFactory.createUnconditionalBranch(runtime, labels.get(branch.getSuccessor().getName()),
                        getPhiWriteNodes(branch)[0], runtime.getSourceSection(branch));
        setControlFlowNode(unconditionalBranchNode);
    }

    @Override
    public void visit(CallInstruction call) {
        final Type targetType = call.getType();
        int argumentCount = getArgumentCount(call.getArgumentCount(), targetType);
        final LLVMExpressionNode[] argNodes = new LLVMExpressionNode[argumentCount];
        final Type[] argTypes = new Type[argumentCount];
        int argIndex = 0;
        // stack pointer
        argNodes[argIndex] = nodeFactory.createFrameRead(runtime, PrimitiveType.I64, getStackSlot());
        argTypes[argIndex] = new PointerType(null);
        argIndex++;

        if (targetType instanceof StructureType) {
            final int size = runtime.getByteSize(targetType);
            final int align = runtime.getByteAlignment(targetType);
            argTypes[argIndex] = new PointerType(targetType);
            argNodes[argIndex] = nodeFactory.createAlloc(runtime, targetType, size, align, null, null);
            argIndex++;
        }
        for (int i = 0; argIndex < argumentCount; i++) {
            argNodes[argIndex] = symbols.resolve(call.getArgument(i));
            argTypes[argIndex] = call.getArgument(i).getType();
            argIndex++;
        }

        final SourceSection sourceSection = runtime.getSourceSection(call);
        final Symbol target = call.getCallTarget();
        LLVMExpressionNode result = nodeFactory.createLLVMBuiltin(target, argNodes, argCount, sourceSection);
        if (result == null) {
            if (target instanceof InlineAsmConstant) {
                final InlineAsmConstant inlineAsmConstant = (InlineAsmConstant) target;
                result = createInlineAssemblerNode(inlineAsmConstant, argNodes, argTypes, targetType, sourceSection);

            } else {
                LLVMExpressionNode function = symbols.resolve(target);
                result = nodeFactory.createFunctionCall(runtime, function, argNodes, new FunctionType(targetType, argTypes, false), sourceSection);
            }
        }
        createFrameWrite(result, call);
    }

    @Override
    public void visit(LandingpadInstruction landingpadInstruction) {
        Type type = landingpadInstruction.getType();
        final int size = runtime.getByteSize(type);
        final int align = runtime.getByteAlignment(type);
        LLVMExpressionNode allocateLandingPadValue = nodeFactory.createAlloc(runtime, type, size, align, null, null);
        LLVMExpressionNode[] entries = new LLVMExpressionNode[landingpadInstruction.getClauseSymbols().length];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = symbols.resolve(landingpadInstruction.getClauseSymbols()[i]);
        }
        LLVMExpressionNode landingPad = nodeFactory.createLandingPad(runtime, allocateLandingPadValue, getExceptionSlot(), landingpadInstruction.isCleanup(), landingpadInstruction.getClauseTypes(),
                        entries);
        createFrameWrite(landingPad, landingpadInstruction);
    }

    @Override
    public void visit(ResumeInstruction resumeInstruction) {
        LLVMControlFlowNode resume = nodeFactory.createResumeInstruction(runtime, getExceptionSlot(), runtime.getSourceSection(resumeInstruction));
        setControlFlowNode(resume);
    }

    @Override
    public void visit(CompareExchangeInstruction cmpxchg) {
        final LLVMExpressionNode ptrNode = symbols.resolve(cmpxchg.getPtr());
        final LLVMExpressionNode cmpNode = symbols.resolve(cmpxchg.getCmp());
        final LLVMExpressionNode newNode = symbols.resolve(cmpxchg.getReplace());
        final Type elementType = cmpxchg.getCmp().getType();

        createFrameWrite(nodeFactory.createCompareExchangeInstruction(runtime, cmpxchg.getType(), elementType, ptrNode, cmpNode, newNode), cmpxchg);
    }

    @Override
    public void visit(VoidCallInstruction call) {
        final Symbol target = call.getCallTarget();

        if (target instanceof FunctionDeclaration) {
            final String name = ((FunctionDeclaration) target).getName();
            if ("@llvm.dbg.declare".equals(name) || "@llvm.dbg.value".equals(name)) {
                // these intrinsics are debug information and should be resolved during parsing, not
                // at runtime
                return;
            }
        }

        final int argumentCount = call.getArgumentCount() + 1; // stackpointer
        final LLVMExpressionNode[] args = new LLVMExpressionNode[argumentCount];
        final Type[] argsType = new Type[argumentCount];

        int argIndex = 0;
        args[argIndex] = nodeFactory.createFrameRead(runtime, PrimitiveType.I64, getStackSlot());
        argsType[argIndex] = new PointerType(null);
        argIndex++;

        for (int i = 0; i < call.getArgumentCount(); i++) {
            args[argIndex] = symbols.resolve(call.getArgument(i));
            argsType[argIndex] = call.getArgument(i).getType();
            argIndex++;
        }

        final SourceSection sourceSection = runtime.getSourceSection(call);
        LLVMExpressionNode node = nodeFactory.createLLVMBuiltin(target, args, argCount, sourceSection);
        if (node == null) {
            if (target instanceof InlineAsmConstant) {
                final InlineAsmConstant inlineAsmConstant = (InlineAsmConstant) target;
                node = createInlineAssemblerNode(inlineAsmConstant, args, argsType, call.getType(), sourceSection);
            } else {
                final LLVMExpressionNode function = symbols.resolve(target);
                final FunctionType functionType = new FunctionType(call.getType(), argsType, false);
                node = nodeFactory.createFunctionCall(runtime, function, args, functionType, sourceSection);
            }
        }
        addInstruction(node);
    }

    @Override
    public void visit(InvokeInstruction call) {
        final Type targetType = call.getType();
        int argumentCount = getArgumentCount(call.getArgumentCount(), targetType);
        final LLVMExpressionNode[] argNodes = new LLVMExpressionNode[argumentCount];
        final Type[] argTypes = new Type[argumentCount];
        int argIndex = 0;
        argNodes[argIndex] = nodeFactory.createFrameRead(runtime, PrimitiveType.I64, getStackSlot());
        argTypes[argIndex] = new PointerType(null);
        argIndex++;
        if (targetType instanceof StructureType) {
            final int size = runtime.getByteSize(targetType);
            final int align = runtime.getByteAlignment(targetType);
            argTypes[argIndex] = new PointerType(targetType);
            argNodes[argIndex] = nodeFactory.createAlloc(runtime, targetType, size, align, null, null);
            argIndex++;
        }
        for (int i = 0; argIndex < argumentCount; i++, argIndex++) {
            argNodes[argIndex] = symbols.resolve(call.getArgument(i));
            argTypes[argIndex] = call.getArgument(i).getType();
        }

        final Symbol target = call.getCallTarget();
        int regularIndex = labels.get(call.normalSuccessor().getName());
        int unwindIndex = labels.get(call.unwindSuccessor().getName());

        List<FrameSlot> normalTo = new ArrayList<>();
        List<FrameSlot> unwindTo = new ArrayList<>();
        List<Type> normalType = new ArrayList<>();
        List<Type> unwindType = new ArrayList<>();
        List<LLVMExpressionNode> normalValue = new ArrayList<>();
        List<LLVMExpressionNode> unwindValue = new ArrayList<>();
        if (blockPhis != null) {
            for (Phi phi : blockPhis) {
                FrameSlot slot = getSlot(phi.getPhiValue().getName());
                LLVMExpressionNode value = symbols.resolve(phi.getValue());
                if (call.normalSuccessor() == phi.getBlock()) {
                    normalTo.add(slot);
                    normalType.add(phi.getValue().getType());
                    normalValue.add(value);
                } else {
                    unwindTo.add(slot);
                    unwindType.add(phi.getValue().getType());
                    unwindValue.add(value);

                }
            }
        }
        LLVMExpressionNode normalPhi = nodeFactory.createPhi(normalValue.toArray(new LLVMExpressionNode[normalValue.size()]), normalTo.toArray(new FrameSlot[normalTo.size()]),
                        normalType.toArray(new Type[normalType.size()]));
        LLVMExpressionNode unwindPhi = nodeFactory.createPhi(unwindValue.toArray(new LLVMExpressionNode[unwindValue.size()]), unwindTo.toArray(new FrameSlot[unwindTo.size()]),
                        unwindType.toArray(new Type[unwindType.size()]));

        final SourceSection sourceSection = runtime.getSourceSection(call);
        LLVMExpressionNode function = nodeFactory.createLLVMBuiltin(target, argNodes, argCount, null);
        if (function == null) {
            function = symbols.resolve(target);
        }
        LLVMControlFlowNode result = nodeFactory.createFunctionInvoke(runtime, getSlot(call.getName()), function, argNodes, new FunctionType(targetType, argTypes, false),
                        regularIndex, unwindIndex, normalPhi,
                        unwindPhi, sourceSection);

        setControlFlowNode(result);
    }

    @Override
    public void visit(VoidInvokeInstruction call) {
        final Symbol target = call.getCallTarget();

        final int argumentCount = call.getArgumentCount() + 1; // stackpointer
        final LLVMExpressionNode[] args = new LLVMExpressionNode[argumentCount];
        final Type[] argsType = new Type[argumentCount];

        int argIndex = 0;
        args[argIndex] = nodeFactory.createFrameRead(runtime, PrimitiveType.I64, getStackSlot());
        argsType[argIndex] = new PointerType(null);
        argIndex++;

        for (int i = 0; i < call.getArgumentCount(); i++) {
            args[argIndex] = symbols.resolve(call.getArgument(i));
            argsType[argIndex] = call.getArgument(i).getType();
            argIndex++;
        }

        int regularIndex = labels.get(call.normalSuccessor().getName());
        int unwindIndex = labels.get(call.unwindSuccessor().getName());

        List<FrameSlot> normalTo = new ArrayList<>();
        List<FrameSlot> unwindTo = new ArrayList<>();
        List<Type> normalType = new ArrayList<>();
        List<Type> unwindType = new ArrayList<>();
        List<LLVMExpressionNode> normalValue = new ArrayList<>();
        List<LLVMExpressionNode> unwindValue = new ArrayList<>();
        if (blockPhis != null) {
            for (Phi phi : blockPhis) {
                FrameSlot slot = getSlot(phi.getPhiValue().getName());
                LLVMExpressionNode value = symbols.resolve(phi.getValue());
                if (call.normalSuccessor() == phi.getBlock()) {
                    normalTo.add(slot);
                    normalType.add(phi.getValue().getType());
                    normalValue.add(value);
                } else {
                    unwindTo.add(slot);
                    unwindType.add(phi.getValue().getType());
                    unwindValue.add(value);

                }
            }
        }
        LLVMExpressionNode normalPhi = nodeFactory.createPhi(normalValue.toArray(new LLVMExpressionNode[normalValue.size()]), normalTo.toArray(new FrameSlot[normalTo.size()]),
                        normalType.toArray(new Type[normalType.size()]));
        LLVMExpressionNode unwindPhi = nodeFactory.createPhi(unwindValue.toArray(new LLVMExpressionNode[unwindValue.size()]), unwindTo.toArray(new FrameSlot[unwindTo.size()]),
                        unwindType.toArray(new Type[unwindType.size()]));

        final SourceSection sourceSection = runtime.getSourceSection(call);
        LLVMExpressionNode function = nodeFactory.createLLVMBuiltin(target, args, argCount, null);
        if (function == null) {
            function = symbols.resolve(target);
        }
        LLVMControlFlowNode result = nodeFactory.createFunctionInvoke(runtime, null, function, args, new FunctionType(call.getType(), argsType, false),
                        regularIndex, unwindIndex, normalPhi, unwindPhi, sourceSection);

        setControlFlowNode(result);

    }

    private static int getArgumentCount(int argumentCount, final Type targetType) {
        int count = argumentCount;
        if (targetType instanceof StructureType) {
            count++;
        }
        count++; // stackpointer
        return count;
    }

    @Override
    public void visit(CastInstruction cast) {
        LLVMConversionType type = LLVMBitcodeTypeHelper.toConversionType(cast.getOperator());
        LLVMExpressionNode fromNode = symbols.resolve(cast.getValue());
        Type from = cast.getValue().getType();
        Type to = cast.getType();

        LLVMExpressionNode result = nodeFactory.createCast(runtime, fromNode, to, from, type);
        createFrameWrite(result, cast);
    }

    @Override
    public void visit(CompareInstruction compare) {
        LLVMExpressionNode result = nodeFactory.createComparison(runtime,
                        compare.getOperator(),
                        compare.getLHS().getType(),
                        symbols.resolve(compare.getLHS()),
                        symbols.resolve(compare.getRHS()));

        createFrameWrite(result, compare);
    }

    @Override
    public void visit(ConditionalBranchInstruction branch) {
        LLVMExpressionNode conditionNode = symbols.resolve(branch.getCondition());
        int trueIndex = labels.get(branch.getTrueSuccessor().getName());
        int falseIndex = labels.get(branch.getFalseSuccessor().getName());

        LLVMExpressionNode[] phiWriteNodes = getPhiWriteNodes(branch);
        LLVMControlFlowNode node = nodeFactory.createConditionalBranch(runtime, trueIndex, falseIndex, conditionNode, phiWriteNodes[0], phiWriteNodes[1], runtime.getSourceSection(branch));

        setControlFlowNode(node);
    }

    @Override
    public void visit(ExtractElementInstruction extract) {
        LLVMExpressionNode vector = symbols.resolve(extract.getVector());
        LLVMExpressionNode index = symbols.resolve(extract.getIndex());
        Type resultType = extract.getType();

        LLVMExpressionNode result = nodeFactory.createExtractElement(runtime, resultType, vector, index);

        createFrameWrite(result, extract);
    }

    @Override
    public void visit(ExtractValueInstruction extract) {
        if (!(extract.getAggregate().getType() instanceof ArrayType || extract.getAggregate().getType() instanceof StructureType || extract.getAggregate().getType() instanceof PointerType)) {
            throw new IllegalStateException("\'extractvalue\' can only extract elements of arrays and structs!");
        }
        final LLVMExpressionNode baseAddress = symbols.resolve(extract.getAggregate());
        final Type baseType = extract.getAggregate().getType();
        final int targetIndex = extract.getIndex();
        final Type resultType = extract.getType();

        LLVMExpressionNode targetAddress = baseAddress;

        final AggregateType aggregateType = (AggregateType) baseType;

        int offset = runtime.getIndexOffset(targetIndex, aggregateType);

        final Type targetType = aggregateType.getElementType(targetIndex);
        if (targetType != null && !((targetType instanceof StructureType) && (((StructureType) targetType).isPacked()))) {
            offset += runtime.getBytePadding(offset, targetType);
        }

        if (offset != 0) {
            final LLVMExpressionNode oneLiteralNode = nodeFactory.createLiteral(runtime, 1, PrimitiveType.I32);
            targetAddress = nodeFactory.createTypedElementPointer(runtime, targetAddress, oneLiteralNode, offset, extract.getType());
        }

        final LLVMExpressionNode result = nodeFactory.createExtractValue(runtime, resultType, targetAddress);
        createFrameWrite(result, extract);
    }

    @Override
    public void visit(GetElementPointerInstruction gep) {
        final LLVMExpressionNode targetAddress = symbols.resolveElementPointer(gep.getBasePointer(), gep.getIndices());
        createFrameWrite(targetAddress, gep);
    }

    @Override
    public void visit(IndirectBranchInstruction branch) {
        if (branch.getSuccessorCount() > 1) {
            int[] labelTargets = new int[branch.getSuccessorCount()];
            for (int i = 0; i < labelTargets.length; i++) {
                labelTargets[i] = labels.get(branch.getSuccessor(i).getName());
            }
            LLVMExpressionNode value = symbols.resolve(branch.getAddress());

            LLVMControlFlowNode node = nodeFactory.createIndirectBranch(runtime, value, labelTargets, getPhiWriteNodes(branch), runtime.getSourceSection(branch));
            setControlFlowNode(node);
        } else {
            assert branch.getSuccessorCount() == 1;
            LLVMControlFlowNode node = nodeFactory.createUnconditionalBranch(runtime, labels.get(branch.getSuccessor(0).getName()), getPhiWriteNodes(branch)[0],
                            runtime.getSourceSection(branch));
            setControlFlowNode(node);
        }
    }

    @Override
    public void visit(InsertElementInstruction insert) {
        final LLVMExpressionNode vector = symbols.resolve(insert.getVector());
        final LLVMExpressionNode index = symbols.resolve(insert.getIndex());
        final LLVMExpressionNode element = symbols.resolve(insert.getValue());
        final Type type = insert.getType();
        final LLVMExpressionNode result = nodeFactory.createInsertElement(runtime, type, vector, element, index);
        createFrameWrite(result, insert);
    }

    @Override
    public void visit(InsertValueInstruction insert) {
        if (!(insert.getAggregate().getType() instanceof StructureType || insert.getAggregate().getType() instanceof ArrayType)) {
            throw new IllegalStateException("\'insertvalue\' can only insert values into arrays and structs!");
        }
        final AggregateType sourceType = (AggregateType) insert.getAggregate().getType();
        final LLVMExpressionNode sourceAggregate = symbols.resolve(insert.getAggregate());
        final LLVMExpressionNode valueToInsert = symbols.resolve(insert.getValue());
        final Type valueType = insert.getValue().getType();
        final int targetIndex = insert.getIndex();
        final int size = runtime.getByteSize(sourceType);
        final int alignment = runtime.getByteAlignment(sourceType);

        final LLVMExpressionNode resultAggregate = nodeFactory.createAlloc(runtime, sourceType, size, alignment, null, null);

        final int offset = runtime.getIndexOffset(targetIndex, sourceType);
        final LLVMExpressionNode result = nodeFactory.createInsertValue(runtime, resultAggregate, sourceAggregate,
                        runtime.getByteSize(sourceType), offset, valueToInsert, valueType);

        createFrameWrite(result, insert);
    }

    @Override
    public void visit(LoadInstruction load) {
        LLVMExpressionNode source = symbols.resolve(load.getSource());
        LLVMExpressionNode result = nodeFactory.createLoad(runtime, load.getType(), source);
        createFrameWrite(result, load);
    }

    @Override
    public void visit(PhiInstruction phi) {
        // we don't do any processing for phi nodes but there still might be some nuller information
        // associated with the phi (e.g., when the phi value is never used)
        handleNullerInfo();
        // TODO add sourcesection to this phi
    }

    @Override
    public void visit(ReturnInstruction ret) {
        LLVMControlFlowNode node;
        if (ret.getValue() == null) {
            node = nodeFactory.createRetVoid(runtime, runtime.getSourceSection(ret));
        } else {
            final Type type = ret.getValue().getType();
            final LLVMExpressionNode value = symbols.resolve(ret.getValue());
            node = nodeFactory.createNonVoidRet(runtime, value, type, runtime.getSourceSection(ret));
        }
        setControlFlowNode(node);
    }

    @Override
    public void visit(SelectInstruction select) {
        final LLVMExpressionNode condition = symbols.resolve(select.getCondition());
        final LLVMExpressionNode trueValue = symbols.resolve(select.getTrueValue());
        final LLVMExpressionNode falseValue = symbols.resolve(select.getFalseValue());
        final Type type = select.getType();

        final LLVMExpressionNode result = nodeFactory.createSelect(runtime, type, condition, trueValue, falseValue);

        createFrameWrite(result, select);
    }

    @Override
    public void visit(ShuffleVectorInstruction shuffle) {
        final LLVMExpressionNode vector1 = symbols.resolve(shuffle.getVector1());
        final LLVMExpressionNode vector2 = symbols.resolve(shuffle.getVector2());
        final LLVMExpressionNode mask = symbols.resolve(shuffle.getMask());

        final Type type = shuffle.getType();
        final LLVMExpressionNode result = nodeFactory.createShuffleVector(runtime, type, vector1, vector2, mask);

        createFrameWrite(result, shuffle);
    }

    @Override
    public void visit(StoreInstruction store) {
        final LLVMExpressionNode pointerNode = symbols.resolve(store.getDestination());
        final LLVMExpressionNode valueNode = symbols.resolve(store.getSource());

        Type type = store.getSource().getType();

        final LLVMExpressionNode node = nodeFactory.createStore(runtime, pointerNode, valueNode, type, runtime.getSourceSection(store));

        addInstruction(node);
    }

    @Override
    public void visit(SwitchInstruction zwitch) {
        LLVMExpressionNode cond = symbols.resolve(zwitch.getCondition());
        int[] successors = new int[zwitch.getCaseCount() + 1];
        for (int i = 0; i < successors.length - 1; i++) {
            successors[i] = labels.get(zwitch.getCaseBlock(i).getName());
        }
        successors[successors.length - 1] = labels.get(zwitch.getDefaultBlock().getName());

        Type llvmType = zwitch.getCondition().getType();
        LLVMExpressionNode[] cases = new LLVMExpressionNode[zwitch.getCaseCount()];
        for (int i = 0; i < cases.length; i++) {
            cases[i] = symbols.resolve(zwitch.getCaseValue(i));
        }

        LLVMControlFlowNode node = nodeFactory.createSwitch(runtime, cond, successors, cases, (PrimitiveType) llvmType, getPhiWriteNodes(zwitch), runtime.getSourceSection(zwitch));
        setControlFlowNode(node);
    }

    private LLVMExpressionNode[] getPhiWriteNodes(TerminatingInstruction terminatingInstruction) {
        if (blockPhis != null) {
            ArrayList<Phi>[] phisPerSuccessor = LLVMPhiManager.getPhisForSuccessors(terminatingInstruction, blockPhis);
            return convertToPhiWriteNodes(phisPerSuccessor);
        }
        return new LLVMExpressionNode[terminatingInstruction.getSuccessorCount()];
    }

    private LLVMExpressionNode[] convertToPhiWriteNodes(ArrayList<Phi>[] phisPerSuccessor) {
        LLVMExpressionNode[] result = new LLVMExpressionNode[phisPerSuccessor.length];
        for (int i = 0; i < result.length; i++) {
            LLVMExpressionNode[] from = new LLVMExpressionNode[phisPerSuccessor[i].size()];
            FrameSlot[] to = new FrameSlot[phisPerSuccessor[i].size()];
            Type[] types = new Type[phisPerSuccessor[i].size()];
            for (int j = 0; j < phisPerSuccessor[i].size(); j++) {
                Phi phi = phisPerSuccessor[i].get(j);
                to[j] = getSlot(phi.getPhiValue().getName());
                from[j] = symbols.resolve(phi.getValue());
                types[j] = phi.getValue().getType();
            }
            result[i] = nodeFactory.createPhi(from, to, types);
        }
        return result;
    }

    @Override
    public void visit(SwitchOldInstruction zwitch) {
        LLVMExpressionNode cond = symbols.resolve(zwitch.getCondition());

        int[] successors = new int[zwitch.getCaseCount() + 1];
        for (int i = 0; i < successors.length - 1; i++) {
            successors[i] = labels.get(zwitch.getCaseBlock(i).getName());
        }
        successors[successors.length - 1] = labels.get(zwitch.getDefaultBlock().getName());

        final PrimitiveType llvmType = (PrimitiveType) zwitch.getCondition().getType();
        final LLVMExpressionNode[] cases = new LLVMExpressionNode[zwitch.getCaseCount()];
        for (int i = 0; i < cases.length; i++) {
            // the case value is always a long here regardless of the values actual type, implicit
            // casts to smaller types in the factoryfacade won't work
            switch (llvmType.getPrimitiveKind()) {
                case I8:
                    cases[i] = nodeFactory.createLiteral(runtime, (byte) zwitch.getCaseValue(i), llvmType);
                    break;
                case I16:
                    cases[i] = nodeFactory.createLiteral(runtime, (short) zwitch.getCaseValue(i), llvmType);
                    break;
                case I32:
                    cases[i] = nodeFactory.createLiteral(runtime, (int) zwitch.getCaseValue(i), llvmType);
                    break;
                default:
                    cases[i] = nodeFactory.createLiteral(runtime, zwitch.getCaseValue(i), llvmType);
            }
        }

        LLVMControlFlowNode node = nodeFactory.createSwitch(runtime, cond, successors, cases, llvmType, getPhiWriteNodes(zwitch), runtime.getSourceSection(zwitch));
        setControlFlowNode(node);
    }

    @Override
    public void visit(UnreachableInstruction ui) {
        setControlFlowNode(nodeFactory.createUnreachableNode(runtime));
    }

    private void createFrameWrite(LLVMExpressionNode result, ValueInstruction source) {
        final LLVMExpressionNode node = nodeFactory.createFrameWrite(runtime, source.getType(), result, getSlot(source.getName()), runtime.getSourceSection(source));
        addInstruction(node);
    }

    private LLVMExpressionNode createInlineAssemblerNode(InlineAsmConstant inlineAsmConstant, LLVMExpressionNode[] argNodes, Type[] argsType, Type retType, SourceSection sourceSection) {
        if (inlineAsmConstant.needsAlignedStack()) {
            throw new UnsupportedOperationException("Assembly Expressions that require an aligned Stack are not supported yet!");
        }
        if (inlineAsmConstant.getDialect() != AsmDialect.AT_T) {
            throw new UnsupportedOperationException("Unsupported Assembly Dialect: " + inlineAsmConstant.getDialect());
        }
        return nodeFactory.createInlineAssemblerExpression(runtime, inlineAsmConstant.getAsmExpression(), inlineAsmConstant.getAsmFlags(), argNodes, argsType, retType, sourceSection);
    }

    private FrameSlot getSlot(String name) {
        return frame.findFrameSlot(name);
    }

    private FrameSlot getExceptionSlot() {
        return getSlot(LLVMException.FRAME_SLOT_ID);
    }

    private FrameSlot getStackSlot() {
        return getSlot(LLVMStack.FRAME_ID);
    }

    private void addInstruction(LLVMExpressionNode node) {
        blockInstructions.add(node);
        handleNullerInfo();
    }

    private void handleNullerInfo() {
        for (int i = nullerInfos.size() - 1; i >= 0; i--) {
            LLVMLivenessAnalysis.NullerInformation nuller = nullerInfos.get(i);
            if (nuller.getInstructionIndex() > instructionIndex) {
                // the nuller information is sorted descending by instructionIndex
                break;
            } else if (nuller.getInstructionIndex() == instructionIndex) {
                FrameSlot frameSlot = frameSlots.get(nuller.getFrameSlotIndex());
                LLVMExpressionNode nullerNode = nodeFactory.createFrameNuller(frameSlot);
                blockInstructions.add(nullerNode);
                nullerInfos.remove(i);
            } else {
                assert false : "we either missed an instruction or the nuller information is not sorted correctly";
            }
        }
    }

    private void setControlFlowNode(LLVMControlFlowNode controlFlowNode) {
        assert this.controlFlowNode == null;
        this.controlFlowNode = controlFlowNode;
    }
}
