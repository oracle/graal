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

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.llvm.parser.LLVMPhiManager.Phi;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionKind;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.enums.AsmDialect;
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
import com.oracle.truffle.llvm.parser.model.symbols.instructions.UnreachableInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidInvokeInstruction;
import com.oracle.truffle.llvm.parser.model.visitors.InstructionVisitor;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolResolver;
import com.oracle.truffle.llvm.parser.util.LLVMBitcodeTypeHelper;
import com.oracle.truffle.llvm.parser.util.LLVMFrameIDs;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
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

    private final LLVMBitcodeFunctionVisitor method;

    private final InstructionBlock block;

    private final LLVMSymbolResolver symbols;

    private final SulongNodeFactory nodeFactory;

    private final LLVMParserRuntime runtime;

    LLVMBitcodeInstructionVisitor(LLVMBitcodeFunctionVisitor method, InstructionBlock block, SulongNodeFactory nodeFactory) {
        this.method = method;
        this.block = block;
        this.symbols = method.getSymbolResolver();
        this.nodeFactory = nodeFactory;
        this.runtime = method.getRuntime();
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
            final LLVMExpressionNode result = nodeFactory.createArithmeticOperation(runtime, lhs, rhs, opA, type);
            createFrameWrite(result, operation);
            return;
        }

        final LLVMLogicalInstructionKind opL = LLVMBitcodeTypeHelper.toLogicalInstructionType(operation.getOperator());
        if (opL != null) {
            final LLVMExpressionNode result = nodeFactory.createLogicalOperation(runtime, lhs, rhs, opL, type);
            createFrameWrite(result, operation);
            return;
        }

        throw new RuntimeException("Missed a binary operator");
    }

    @Override
    public void visit(BranchInstruction branch) {
        method.addTerminatingInstruction(nodeFactory.createUnconditionalBranch(runtime, method.labels().get(branch.getSuccessor().getName()),
                        getPhiWriteNodes(branch.getSuccessors())[0]), block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(CallInstruction call) {
        final Type targetType = call.getType();
        int argumentCount = getArgumentCount(call, targetType);
        final LLVMExpressionNode[] argNodes = new LLVMExpressionNode[argumentCount];
        final Type[] argTypes = new Type[argumentCount];
        int argIndex = 0;
        if (method.getRuntime().needsStackPointerArgument()) {
            argNodes[argIndex] = nodeFactory.createFrameRead(runtime, new PointerType(null), method.getStackSlot());
            argTypes[argIndex] = new PointerType(null);
            argIndex++;
        }
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
        LLVMExpressionNode result = nodeFactory.createLLVMBuiltin(target, argNodes, method.getStackSlot(), method.getArgCount());
        if (result == null) {
            if (target instanceof InlineAsmConstant) {
                final InlineAsmConstant inlineAsmConstant = (InlineAsmConstant) target;
                result = createInlineAssemblerNode(inlineAsmConstant, argNodes, argTypes, targetType);

            } else {
                LLVMExpressionNode function = symbols.resolve(target);
                result = nodeFactory.createFunctionCall(runtime, function, argNodes, new FunctionType(targetType, argTypes, false));
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
        FrameSlot exceptionSlot = method.getExceptionSlot();
        LLVMExpressionNode[] entries = new LLVMExpressionNode[landingpadInstruction.getClauseSymbols().length];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = symbols.resolve(landingpadInstruction.getClauseSymbols()[i]);
        }
        LLVMExpressionNode landingPad = nodeFactory.createLandingPad(runtime, allocateLandingPadValue, exceptionSlot, landingpadInstruction.isCleanup(), landingpadInstruction.getClauseTypes(),
                        entries);
        createFrameWrite(landingPad, landingpadInstruction);
    }

    @Override
    public void visit(ResumeInstruction resumeInstruction) {
        LLVMControlFlowNode resume = nodeFactory.createResumeInstruction(runtime, method.getExceptionSlot());
        method.addTerminatingInstruction(resume, block.getBlockIndex(), block.getName());
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
        final int argumentCount;
        int explicitArgumentCount = call.getArgumentCount();
        if (method.getRuntime().needsStackPointerArgument()) {
            argumentCount = explicitArgumentCount + 1;
        } else {
            argumentCount = explicitArgumentCount;
        }
        final LLVMExpressionNode[] args = new LLVMExpressionNode[argumentCount];
        final Type[] argsType = new Type[argumentCount];

        int argIndex = 0;
        if (method.getRuntime().needsStackPointerArgument()) {
            args[argIndex] = nodeFactory.createFrameRead(runtime, new PointerType(null), method.getStackSlot());
            argsType[argIndex] = new PointerType(null);
            argIndex++;
        }
        for (int i = 0; i < explicitArgumentCount; i++) {
            args[argIndex] = symbols.resolve(call.getArgument(i));
            argsType[argIndex] = call.getArgument(i).getType();
            argIndex++;
        }

        FunctionType functionType = new FunctionType(call.getType(), argsType, false);
        LLVMExpressionNode node = nodeFactory.createLLVMBuiltin(target, args, method.getStackSlot(), method.getArgCount());
        if (node == null) {
            if (target instanceof InlineAsmConstant) {
                final InlineAsmConstant inlineAsmConstant = (InlineAsmConstant) target;
                node = createInlineAssemblerNode(inlineAsmConstant, args, argsType, call.getType());
            } else {
                LLVMExpressionNode function = symbols.resolve(target);
                node = nodeFactory.createFunctionCall(runtime, function, args, functionType);
            }
        }
        method.addInstruction(node);
    }

    @Override
    public void visit(InvokeInstruction call) {
        final Type targetType = call.getType();
        int argumentCount = getArgumentCount(call, targetType);
        final LLVMExpressionNode[] argNodes = new LLVMExpressionNode[argumentCount];
        final Type[] argTypes = new Type[argumentCount];
        int argIndex = 0;
        if (method.getRuntime().needsStackPointerArgument()) {
            argNodes[argIndex] = nodeFactory.createFrameRead(runtime, new PointerType(null), method.getStackSlot());
            argTypes[argIndex] = new PointerType(null);
            argIndex++;
        }
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
        int regularIndex = method.labels().get(call.normalSuccessor().getName());
        int unwindIndex = method.labels().get(call.unwindSuccessor().getName());

        List<LLVMExpressionNode> normalPhiWriteNodes = new ArrayList<>();
        List<LLVMExpressionNode> unwindPhiWriteNodes = new ArrayList<>();

        List<Phi> phis = method.getPhiManager().get(block);
        if (phis != null) {
            for (Phi phi : phis) {
                FrameSlot slot = method.getSlot(phi.getPhiValue().getName());
                LLVMExpressionNode value = symbols.resolve(phi.getValue());
                LLVMExpressionNode phiWriteNode = nodeFactory.createFrameWrite(runtime, phi.getValue().getType(), value, slot);

                if (call.normalSuccessor() == phi.getBlock()) {
                    normalPhiWriteNodes.add(phiWriteNode);
                } else {
                    unwindPhiWriteNodes.add(phiWriteNode);
                }
            }
        }
        LLVMExpressionNode[] normalPhiWriteNodesArray = normalPhiWriteNodes.toArray(new LLVMExpressionNode[normalPhiWriteNodes.size()]);
        LLVMExpressionNode[] unwindPhiWriteNodesArray = unwindPhiWriteNodes.toArray(new LLVMExpressionNode[unwindPhiWriteNodes.size()]);

        LLVMExpressionNode function = nodeFactory.createLLVMBuiltin(target, argNodes, method.getStackSlot(), method.getArgCount());
        if (function == null) {
            function = symbols.resolve(target);
        }
        LLVMControlFlowNode result = nodeFactory.createFunctionInvoke(runtime, function, argNodes, new FunctionType(targetType, argTypes, false),
                        method.getSlot(call.getName()), method.getExceptionSlot(), regularIndex, unwindIndex, normalPhiWriteNodesArray,
                        unwindPhiWriteNodesArray);

        method.addTerminatingInstruction(result, block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(VoidInvokeInstruction call) {
        final Symbol target = call.getCallTarget();
        final int argumentCount;
        int explicitArgumentCount = call.getArgumentCount();
        if (method.getRuntime().needsStackPointerArgument()) {
            argumentCount = explicitArgumentCount + 1;
        } else {
            argumentCount = explicitArgumentCount;
        }
        final LLVMExpressionNode[] args = new LLVMExpressionNode[argumentCount];
        final Type[] argsType = new Type[argumentCount];

        int argIndex = 0;
        if (method.getRuntime().needsStackPointerArgument()) {
            args[argIndex] = nodeFactory.createFrameRead(runtime, new PointerType(null), method.getStackSlot());
            argsType[argIndex] = new PointerType(null);
            argIndex++;
        }
        for (int i = 0; i < explicitArgumentCount; i++) {
            args[argIndex] = symbols.resolve(call.getArgument(i));
            argsType[argIndex] = call.getArgument(i).getType();
            argIndex++;
        }

        int regularIndex = method.labels().get(call.normalSuccessor().getName());
        int unwindIndex = method.labels().get(call.unwindSuccessor().getName());

        List<LLVMExpressionNode> normalPhiWriteNodes = new ArrayList<>();
        List<LLVMExpressionNode> unwindPhiWriteNodes = new ArrayList<>();

        List<Phi> phis = method.getPhiManager().get(block);
        if (phis != null) {
            for (Phi phi : phis) {
                FrameSlot slot = method.getSlot(phi.getPhiValue().getName());
                LLVMExpressionNode value = symbols.resolve(phi.getValue());
                LLVMExpressionNode phiWriteNode = nodeFactory.createFrameWrite(runtime, phi.getValue().getType(), value, slot);

                if (call.normalSuccessor() == phi.getBlock()) {
                    normalPhiWriteNodes.add(phiWriteNode);
                } else {
                    unwindPhiWriteNodes.add(phiWriteNode);
                }
            }
        }
        LLVMExpressionNode[] normalPhiWriteNodesArray = normalPhiWriteNodes.toArray(new LLVMExpressionNode[normalPhiWriteNodes.size()]);
        LLVMExpressionNode[] unwindPhiWriteNodesArray = unwindPhiWriteNodes.toArray(new LLVMExpressionNode[unwindPhiWriteNodes.size()]);

        LLVMExpressionNode function = nodeFactory.createLLVMBuiltin(target, args, method.getStackSlot(), method.getArgCount());
        if (function == null) {
            function = symbols.resolve(target);
        }
        LLVMControlFlowNode result = nodeFactory.createFunctionInvoke(runtime, function, args, new FunctionType(call.getType(), argsType, false), method.getReturnSlot(), method.getExceptionSlot(),
                        regularIndex,
                        unwindIndex,
                        normalPhiWriteNodesArray, unwindPhiWriteNodesArray);

        method.addTerminatingInstruction(result, block.getBlockIndex(), block.getName());

    }

    private int getArgumentCount(CallInstruction call, final Type targetType) {
        int argumentCount = call.getArgumentCount();
        if (targetType instanceof StructureType) {
            argumentCount++;
        }
        if (method.getRuntime().needsStackPointerArgument()) {
            argumentCount++;
        }
        return argumentCount;
    }

    private int getArgumentCount(InvokeInstruction call, final Type targetType) {
        int argumentCount = call.getArgumentCount();
        if (targetType instanceof StructureType) {
            argumentCount++;
        }
        if (method.getRuntime().needsStackPointerArgument()) {
            argumentCount++;
        }
        return argumentCount;
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
        int trueIndex = method.labels().get(branch.getTrueSuccessor().getName());
        int falseIndex = method.labels().get(branch.getFalseSuccessor().getName());

        LLVMExpressionNode[][] phiWriteNodes = getPhiWriteNodes(branch.getSuccessors());
        LLVMControlFlowNode node = nodeFactory.createConditionalBranch(runtime, trueIndex, falseIndex, conditionNode, phiWriteNodes[0], phiWriteNodes[1]);

        method.addTerminatingInstruction(node, block.getBlockIndex(), block.getName());
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
                labelTargets[i] = method.labels().get(branch.getSuccessor(i).getName());
            }
            LLVMExpressionNode value = symbols.resolve(branch.getAddress());

            LLVMControlFlowNode node = nodeFactory.createIndirectBranch(runtime, value, labelTargets, getPhiWriteNodes(branch.getSuccessors()));
            method.addTerminatingInstruction(node, block.getBlockIndex(), block.getName());
        } else {
            assert branch.getSuccessorCount() == 1;
            LLVMControlFlowNode node = nodeFactory.createUnconditionalBranch(runtime, method.labels().get(branch.getSuccessor(0).getName()), getPhiWriteNodes(branch.getSuccessors())[0]);
            method.addTerminatingInstruction(node, block.getBlockIndex(), block.getName());
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
    public void visit(PhiInstruction pi) {
    }

    @Override
    public void visit(ReturnInstruction ret) {
        LLVMControlFlowNode node;
        if (ret.getValue() == null) {
            node = nodeFactory.createRetVoid(runtime);
        } else {
            final Type type = ret.getValue().getType();
            method.getFrame().findFrameSlot(LLVMFrameIDs.FUNCTION_RETURN_VALUE_FRAME_SLOT_ID).setKind(Type.getFrameSlotKind(type));
            final LLVMExpressionNode value = symbols.resolve(ret.getValue());
            node = nodeFactory.createNonVoidRet(runtime, value, type);
        }
        method.addTerminatingInstruction(node, block.getBlockIndex(), block.getName());
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

        final LLVMExpressionNode node = nodeFactory.createStore(runtime, pointerNode, valueNode, type);

        method.addInstruction(node);
    }

    @Override
    public void visit(SwitchInstruction zwitch) {
        LLVMExpressionNode cond = symbols.resolve(zwitch.getCondition());
        int[] successors = new int[zwitch.getCaseCount() + 1];
        for (int i = 0; i < successors.length - 1; i++) {
            successors[i] = method.labels().get(zwitch.getCaseBlock(i).getName());
        }
        successors[successors.length - 1] = method.labels().get(zwitch.getDefaultBlock().getName());

        Type llvmType = zwitch.getCondition().getType();
        LLVMExpressionNode[] cases = new LLVMExpressionNode[zwitch.getCaseCount()];
        for (int i = 0; i < cases.length; i++) {
            cases[i] = symbols.resolve(zwitch.getCaseValue(i));
        }

        LLVMControlFlowNode node = nodeFactory.createSwitch(runtime, cond, successors, cases, (PrimitiveType) llvmType, getPhiWriteNodes(zwitch.getSuccessors()));
        method.addTerminatingInstruction(node, block.getBlockIndex(), block.getName());
    }

    private LLVMExpressionNode[][] getPhiWriteNodes(List<InstructionBlock> successors) {
        List<Phi> phis = method.getPhiManager().get(block);
        if (phis != null) {
            ArrayList<Phi>[] phisPerSuccessor = collectPhis(successors, phis);
            return convertToPhiWriteNodes(phisPerSuccessor);
        }
        return new LLVMExpressionNode[successors.size()][0];
    }

    private ArrayList<Phi>[] collectPhis(List<InstructionBlock> successors, List<Phi> phis) {
        @SuppressWarnings("unchecked")
        ArrayList<Phi>[] phisPerSuccessor = new ArrayList[successors.size()];
        for (int i = 0; i < phisPerSuccessor.length; i++) {
            phisPerSuccessor[i] = new ArrayList<>();
        }

        for (Phi phi : phis) {
            assignPhiToSuccessor(successors, phi, phisPerSuccessor);
        }
        return phisPerSuccessor;
    }

    private static void assignPhiToSuccessor(List<InstructionBlock> successors, Phi phi, ArrayList<Phi>[] phisPerSuccessor) {
        for (int i = 0; i < successors.size(); i++) {
            if (successors.get(i) == phi.getBlock()) {
                ArrayList<Phi> phis = phisPerSuccessor[i];
                if (!hasMatchingPhi(phis, phi)) {
                    phis.add(phi);
                    return;
                }
            }
        }
        throw new RuntimeException("Could not find a matching successor for a phi.");
    }

    private static boolean hasMatchingPhi(ArrayList<Phi> possiblePhiList, Phi phi) {
        for (int j = 0; j < possiblePhiList.size(); j++) {
            if (possiblePhiList.get(j).getPhiValue() == phi.getPhiValue()) {
                // this successor already has a phi that corresponds to the same phi symbol -> it
                // can't be for that successor. this case happens when we have the same successor
                // block multiple times in the list of the successors.
                return true;
            }
        }
        return false;
    }

    private LLVMExpressionNode[][] convertToPhiWriteNodes(ArrayList<Phi>[] phisPerSuccessor) {
        LLVMExpressionNode[][] result = new LLVMExpressionNode[phisPerSuccessor.length][];
        for (int i = 0; i < result.length; i++) {
            result[i] = new LLVMExpressionNode[phisPerSuccessor[i].size()];
            for (int j = 0; j < phisPerSuccessor[i].size(); j++) {
                Phi phi = phisPerSuccessor[i].get(j);
                FrameSlot slot = method.getSlot(phi.getPhiValue().getName());
                LLVMExpressionNode value = symbols.resolve(phi.getValue());
                Type baseType = phi.getValue().getType();
                LLVMExpressionNode phiWriteNode = nodeFactory.createFrameWrite(runtime, baseType, value, slot);

                result[i][j] = phiWriteNode;
            }
        }
        return result;
    }

    @Override
    public void visit(SwitchOldInstruction zwitch) {
        LLVMExpressionNode cond = symbols.resolve(zwitch.getCondition());

        int[] successors = new int[zwitch.getCaseCount() + 1];
        for (int i = 0; i < successors.length - 1; i++) {
            successors[i] = method.labels().get(zwitch.getCaseBlock(i).getName());
        }
        successors[successors.length - 1] = method.labels().get(zwitch.getDefaultBlock().getName());

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

        LLVMControlFlowNode node = nodeFactory.createSwitch(runtime, cond, successors, cases, llvmType, getPhiWriteNodes(zwitch.getSuccessors()));
        method.addTerminatingInstruction(node, block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(UnreachableInstruction ui) {
        method.addTerminatingInstruction(nodeFactory.createUnreachableNode(runtime), block.getBlockIndex(), block.getName());
    }

    private void createFrameWrite(LLVMExpressionNode result, ValueInstruction source) {
        final LLVMExpressionNode node = nodeFactory.createFrameWrite(runtime, source.getType(), result, method.getSlot(source.getName()));
        method.addInstruction(node);
    }

    private LLVMExpressionNode createInlineAssemblerNode(InlineAsmConstant inlineAsmConstant, LLVMExpressionNode[] argNodes, Type[] argsType, Type retType) {
        if (inlineAsmConstant.hasSideEffects()) {
            LLVMLogger.info("Parsing Inline Assembly Constant with Sideeffects!");
        }
        if (inlineAsmConstant.needsAlignedStack()) {
            throw new UnsupportedOperationException("Assembly Expressions that require an aligned Stack are not supported yet!");
        }
        if (inlineAsmConstant.getDialect() != AsmDialect.AT_T) {
            throw new UnsupportedOperationException("Unsupported Assembly Dialect: " + inlineAsmConstant.getDialect());
        }
        return nodeFactory.createInlineAssemblerExpression(runtime, inlineAsmConstant.getAsmExpression(), inlineAsmConstant.getAsmFlags(), argNodes, argsType, retType);
    }
}
