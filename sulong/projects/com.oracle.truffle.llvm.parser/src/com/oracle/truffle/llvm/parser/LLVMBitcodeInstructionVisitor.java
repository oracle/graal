/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
import java.util.Objects;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.llvm.parser.LLVMPhiManager.Phi;
import com.oracle.truffle.llvm.parser.metadata.MDExpression;
import com.oracle.truffle.llvm.parser.metadata.debuginfo.SourceVariable;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.attributes.Attribute;
import com.oracle.truffle.llvm.parser.model.attributes.AttributesGroup;
import com.oracle.truffle.llvm.parser.model.enums.AsmDialect;
import com.oracle.truffle.llvm.parser.model.enums.ReadModifyWriteOperator;
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
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DbgDeclareInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DbgValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DebugTrapInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ExtractElementInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ExtractValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.FenceInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.GetElementPointerInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.IndirectBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InsertElementInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InsertValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InvokeInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.LandingpadInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.LoadInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.PhiInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ReadModifyWriteInstruction;
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
import com.oracle.truffle.llvm.parser.model.visitors.SymbolVisitor;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolReadResolver;
import com.oracle.truffle.llvm.parser.util.LLVMBitcodeTypeHelper;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMContext.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.except.LLVMUserException;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.UniquesRegion;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMVoidStatementNodeGen;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;

final class LLVMBitcodeInstructionVisitor implements SymbolVisitor {

    static final FrameSlot[] NO_SLOTS = new FrameSlot[0];

    private final FrameDescriptor frame;
    private final List<Phi> blockPhis;
    private final NodeFactory nodeFactory;
    private final int argCount;
    private final LLVMSymbolReadResolver symbols;
    private final LLVMContext context;
    private final ExternalLibrary library;
    private final ArrayList<LLVMLivenessAnalysis.NullerInformation> nullerInfos;
    private final List<FrameSlot> notNullable;
    private final LLVMRuntimeDebugInformation dbgInfoHandler;
    private final UniquesRegion uniquesRegion;

    private final List<LLVMStatementNode> blockInstructions;
    private int instructionIndex;
    private LLVMControlFlowNode controlFlowNode;

    private LLVMSourceLocation lastLocation;

    LLVMBitcodeInstructionVisitor(FrameDescriptor frame, UniquesRegion uniquesRegion, List<Phi> blockPhis, int argCount, LLVMSymbolReadResolver symbols, LLVMContext context,
                    ExternalLibrary library, ArrayList<LLVMLivenessAnalysis.NullerInformation> nullerInfos, List<FrameSlot> notNullable, LLVMRuntimeDebugInformation dbgInfoHandler) {
        this.frame = frame;
        this.blockPhis = blockPhis;
        this.nodeFactory = context.getLanguage().getNodeFactory();
        this.argCount = argCount;
        this.symbols = symbols;
        this.context = context;
        this.library = library;
        this.nullerInfos = nullerInfos;
        this.notNullable = notNullable;
        this.dbgInfoHandler = dbgInfoHandler;
        this.lastLocation = null;
        this.uniquesRegion = uniquesRegion;

        this.blockInstructions = new ArrayList<>();
    }

    public LLVMStatementNode[] getInstructions() {
        return blockInstructions.toArray(LLVMStatementNode.NO_STATEMENTS);
    }

    LLVMControlFlowNode getControlFlowNode() {
        return controlFlowNode;
    }

    void setInstructionIndex(int instructionIndex) {
        this.instructionIndex = instructionIndex;
    }

    @Override
    public void defaultAction(SymbolImpl symbol) {
        throw new LLVMParserException("Instruction not implemented: " + symbol.getClass().getSimpleName());
    }

    @Override
    public void visit(AllocateInstruction allocate) {
        final Type type = allocate.getPointeeType();
        int alignment;
        if (allocate.getAlign() == 0) {
            alignment = context.getByteAlignment(type);
        } else {
            alignment = 1 << (allocate.getAlign() - 1);
        }
        if (alignment == 0) {
            alignment = LLVMStack.NO_ALIGNMENT_REQUIREMENTS;
        }

        final SymbolImpl count = allocate.getCount();
        final LLVMExpressionNode result;
        if (count instanceof NullConstant) {
            result = nodeFactory.createAlloca(type, alignment);
        } else if (count instanceof IntegerConstant) {
            long numElements = (int) ((IntegerConstant) count).getValue();
            if (numElements == 1) {
                result = nodeFactory.createAlloca(type, alignment);
            } else {
                assert numElements == (int) numElements;
                ArrayType arrayType = new ArrayType(type, (int) numElements);
                result = nodeFactory.createAlloca(arrayType, alignment);
            }
        } else {
            LLVMExpressionNode num = symbols.resolve(count);
            result = nodeFactory.createAllocaArray(type, num, alignment);
        }

        // we never want to step on allocations, only to actual assignments in the source
        LLVMSourceLocation location = null;
        if (context.getEnv().getOptions().get(SulongEngineOption.LL_DEBUG)) {
            location = getSourceLocation(allocate);
        }
        createFrameWrite(result, allocate, location);
    }

    @Override
    public void visit(BinaryOperationInstruction operation) {
        LLVMExpressionNode lhs = symbols.resolve(operation.getLHS());
        LLVMExpressionNode rhs = symbols.resolve(operation.getRHS());

        LLVMExpressionNode result = LLVMBitcodeTypeHelper.createArithmeticInstruction(nodeFactory, lhs, rhs, operation.getOperator(), operation.getType());
        createFrameWrite(result, operation);
    }

    @Override
    public void visit(BranchInstruction branch) {
        LLVMControlFlowNode unconditionalBranchNode = nodeFactory.createUnconditionalBranch(branch.getSuccessor().getBlockIndex(),
                        getPhiWriteNodes(branch)[0], getSourceLocation(branch));
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
        argNodes[argIndex] = nodeFactory.createFrameRead(PointerType.VOID, getStackSlot());
        argTypes[argIndex] = new PointerType(null);
        argIndex++;

        if (targetType instanceof StructureType) {
            argTypes[argIndex] = new PointerType(targetType);
            argNodes[argIndex] = nodeFactory.createGetUniqueStackSpace(targetType, uniquesRegion);
            argIndex++;
        }
        for (int i = 0; argIndex < argumentCount; i++) {
            argNodes[argIndex] = symbols.resolve(call.getArgument(i));
            argTypes[argIndex] = call.getArgument(i).getType();
            final AttributesGroup paramAttr = call.getParameterAttributesGroup(i);
            if (isByValue(paramAttr)) {
                argNodes[argIndex] = capsuleAddressByValue(argNodes[argIndex], argTypes[argIndex], paramAttr);
            }
            argIndex++;
        }

        final LLVMSourceLocation source = getSourceLocation(call, false);
        final SymbolImpl target = call.getCallTarget();
        LLVMExpressionNode result = nodeFactory.createLLVMBuiltin(target, argNodes, argCount, source);
        if (result == null) {
            if (target instanceof InlineAsmConstant) {
                final InlineAsmConstant inlineAsmConstant = (InlineAsmConstant) target;
                result = createInlineAssemblerNode(inlineAsmConstant, argNodes, argTypes, targetType, source);

            } else {
                LLVMExpressionNode function = symbols.resolve(target);
                result = nodeFactory.createFunctionCall(function, argNodes, new FunctionType(targetType, argTypes, false), source);
            }
        }

        // the SourceSection references the call, not the return value assignment
        createFrameWrite(result, call, null);
    }

    @Override
    public void visit(LandingpadInstruction landingpadInstruction) {
        Type type = landingpadInstruction.getType();
        LLVMExpressionNode allocateLandingPadValue = nodeFactory.createGetUniqueStackSpace(type, uniquesRegion);
        LLVMExpressionNode[] entries = new LLVMExpressionNode[landingpadInstruction.getClauseSymbols().length];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = symbols.resolve(landingpadInstruction.getClauseSymbols()[i]);
        }
        LLVMExpressionNode getStack = nodeFactory.createFrameRead(PointerType.VOID, getStackSlot());
        LLVMExpressionNode landingPad = nodeFactory.createLandingPad(allocateLandingPadValue, getExceptionSlot(), landingpadInstruction.isCleanup(), landingpadInstruction.getClauseTypes(),
                        entries, getStack);
        createFrameWrite(landingPad, landingpadInstruction);
    }

    @Override
    public void visit(ResumeInstruction resumeInstruction) {
        LLVMControlFlowNode resume = nodeFactory.createResumeInstruction(getExceptionSlot(), getSourceLocation(resumeInstruction));
        setControlFlowNode(resume);
    }

    @Override
    public void visit(CompareExchangeInstruction cmpxchg) {
        final LLVMExpressionNode ptrNode = symbols.resolve(cmpxchg.getPtr());
        final LLVMExpressionNode cmpNode = symbols.resolve(cmpxchg.getCmp());
        final LLVMExpressionNode newNode = symbols.resolve(cmpxchg.getReplace());
        final Type elementType = cmpxchg.getCmp().getType();

        createFrameWrite(nodeFactory.createCompareExchangeInstruction(cmpxchg.getAggregateType(), elementType, ptrNode, cmpNode, newNode), cmpxchg);
    }

    private void visitDebugIntrinsic(SymbolImpl value, SourceVariable variable, MDExpression expression, long index, boolean isDeclaration) {
        final LLVMStatementNode dbgIntrinsic = dbgInfoHandler.handleDebugIntrinsic(value, variable, expression, index, isDeclaration);
        if (dbgIntrinsic != null) {
            addInstructionUnchecked(dbgIntrinsic);
        }

        handleNullerInfo();
    }

    @Override
    public void visit(DbgDeclareInstruction inst) {
        visitDebugIntrinsic(inst.getValue(), inst.getVariable(), inst.getExpression(), 0L, true);
    }

    @Override
    public void visit(DbgValueInstruction inst) {
        visitDebugIntrinsic(inst.getValue(), inst.getVariable(), inst.getExpression(), inst.getIndex(), false);
    }

    @Override
    public void visit(DebugTrapInstruction inst) {
        addInstruction(nodeFactory.createDebugTrap(inst.getSourceLocation()));
    }

    @Override
    public void visit(VoidCallInstruction call) {
        final int argumentCount = call.getArgumentCount() + 1; // stackpointer
        final LLVMExpressionNode[] args = new LLVMExpressionNode[argumentCount];
        final Type[] argsType = new Type[argumentCount];

        int argIndex = 0;
        args[argIndex] = nodeFactory.createFrameRead(PointerType.VOID, getStackSlot());
        argsType[argIndex] = new PointerType(null);
        argIndex++;

        for (int i = 0; i < call.getArgumentCount(); i++) {
            args[argIndex] = symbols.resolve(call.getArgument(i));
            argsType[argIndex] = call.getArgument(i).getType();
            final AttributesGroup paramAttr = call.getParameterAttributesGroup(i);
            if (isByValue(paramAttr)) {
                args[argIndex] = capsuleAddressByValue(args[argIndex], argsType[argIndex], paramAttr);
            }
            argIndex++;
        }

        final LLVMSourceLocation source = getSourceLocation(call, false);
        final SymbolImpl target = call.getCallTarget();
        LLVMExpressionNode node = nodeFactory.createLLVMBuiltin(target, args, argCount, source);
        if (node == null) {
            if (target instanceof InlineAsmConstant) {
                final InlineAsmConstant inlineAsmConstant = (InlineAsmConstant) target;
                node = createInlineAssemblerNode(inlineAsmConstant, args, argsType, call.getType(), source);
            } else {
                final LLVMExpressionNode function = symbols.resolve(target);
                final FunctionType functionType = new FunctionType(call.getType(), argsType, false);
                node = nodeFactory.createFunctionCall(function, args, functionType, source);
            }
        }
        addInstruction(LLVMVoidStatementNodeGen.create(node));
    }

    @Override
    public void visit(InvokeInstruction call) {
        final Type targetType = call.getType();
        int argumentCount = getArgumentCount(call.getArgumentCount(), targetType);
        final LLVMExpressionNode[] argNodes = new LLVMExpressionNode[argumentCount];
        final Type[] argTypes = new Type[argumentCount];
        int argIndex = 0;
        argNodes[argIndex] = nodeFactory.createFrameRead(PointerType.VOID, getStackSlot());
        argTypes[argIndex] = new PointerType(null);
        argIndex++;
        if (targetType instanceof StructureType) {
            argTypes[argIndex] = new PointerType(targetType);
            argNodes[argIndex] = nodeFactory.createGetUniqueStackSpace(targetType, uniquesRegion);
            argIndex++;
        }
        for (int i = 0; argIndex < argumentCount; i++, argIndex++) {
            argNodes[argIndex] = symbols.resolve(call.getArgument(i));
            argTypes[argIndex] = call.getArgument(i).getType();
            final AttributesGroup paramAttr = call.getParameterAttributesGroup(i);
            if (isByValue(paramAttr)) {
                argNodes[argIndex] = capsuleAddressByValue(argNodes[argIndex], argTypes[argIndex], paramAttr);
            }
        }

        final SymbolImpl target = call.getCallTarget();
        int regularIndex = call.normalSuccessor().getBlockIndex();
        int unwindIndex = call.unwindSuccessor().getBlockIndex();

        List<FrameSlot> normalTo = new ArrayList<>();
        List<FrameSlot> unwindTo = new ArrayList<>();
        List<Type> normalType = new ArrayList<>();
        List<Type> unwindType = new ArrayList<>();
        List<LLVMExpressionNode> normalValue = new ArrayList<>();
        List<LLVMExpressionNode> unwindValue = new ArrayList<>();
        if (blockPhis != null) {
            for (Phi phi : blockPhis) {
                FrameSlot slot = getSlot(phi.getPhiValue());
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
        LLVMStatementNode normalPhi = nodeFactory.createPhi(normalValue.toArray(LLVMExpressionNode.NO_EXPRESSIONS), normalTo.toArray(NO_SLOTS),
                        normalType.toArray(Type.EMPTY_ARRAY));
        LLVMStatementNode unwindPhi = nodeFactory.createPhi(unwindValue.toArray(LLVMExpressionNode.NO_EXPRESSIONS), unwindTo.toArray(NO_SLOTS),
                        unwindType.toArray(Type.EMPTY_ARRAY));

        final LLVMSourceLocation source = getSourceLocation(call, false);
        LLVMExpressionNode function = nodeFactory.createLLVMBuiltin(target, argNodes, argCount, null);
        if (function == null) {
            function = symbols.resolve(target);
        }
        LLVMControlFlowNode result = nodeFactory.createFunctionInvoke(getSlot(call), function, argNodes, new FunctionType(targetType, argTypes, false),
                        regularIndex, unwindIndex, normalPhi,
                        unwindPhi, source);

        setControlFlowNode(result);
    }

    @Override
    public void visit(VoidInvokeInstruction call) {
        final SymbolImpl target = call.getCallTarget();

        final int argumentCount = call.getArgumentCount() + 1; // stackpointer
        final LLVMExpressionNode[] args = new LLVMExpressionNode[argumentCount];
        final Type[] argsType = new Type[argumentCount];

        int argIndex = 0;
        args[argIndex] = nodeFactory.createFrameRead(PointerType.VOID, getStackSlot());
        argsType[argIndex] = new PointerType(null);
        argIndex++;

        for (int i = 0; i < call.getArgumentCount(); i++) {
            args[argIndex] = symbols.resolve(call.getArgument(i));
            argsType[argIndex] = call.getArgument(i).getType();
            final AttributesGroup paramAttr = call.getParameterAttributesGroup(i);
            if (isByValue(paramAttr)) {
                args[argIndex] = capsuleAddressByValue(args[argIndex], argsType[argIndex], paramAttr);
            }
            argIndex++;
        }

        int regularIndex = call.normalSuccessor().getBlockIndex();
        int unwindIndex = call.unwindSuccessor().getBlockIndex();

        List<FrameSlot> normalTo = new ArrayList<>();
        List<FrameSlot> unwindTo = new ArrayList<>();
        List<Type> normalType = new ArrayList<>();
        List<Type> unwindType = new ArrayList<>();
        List<LLVMExpressionNode> normalValue = new ArrayList<>();
        List<LLVMExpressionNode> unwindValue = new ArrayList<>();
        if (blockPhis != null) {
            for (Phi phi : blockPhis) {
                FrameSlot slot = getSlot(phi.getPhiValue());
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
        LLVMStatementNode normalPhi = nodeFactory.createPhi(normalValue.toArray(LLVMExpressionNode.NO_EXPRESSIONS), normalTo.toArray(NO_SLOTS),
                        normalType.toArray(Type.EMPTY_ARRAY));
        LLVMStatementNode unwindPhi = nodeFactory.createPhi(unwindValue.toArray(LLVMExpressionNode.NO_EXPRESSIONS), unwindTo.toArray(NO_SLOTS),
                        unwindType.toArray(Type.EMPTY_ARRAY));

        final LLVMSourceLocation source = getSourceLocation(call, false);
        LLVMExpressionNode function = nodeFactory.createLLVMBuiltin(target, args, argCount, null);
        if (function == null) {
            function = symbols.resolve(target);
        }
        LLVMControlFlowNode result = nodeFactory.createFunctionInvoke(null, function, args, new FunctionType(call.getType(), argsType, false),
                        regularIndex, unwindIndex, normalPhi, unwindPhi, source);

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
        LLVMExpressionNode fromNode = symbols.resolve(cast.getValue());
        Type from = cast.getValue().getType();
        Type to = cast.getType();

        LLVMExpressionNode result = LLVMBitcodeTypeHelper.createCast(nodeFactory, fromNode, to, from, cast.getOperator());
        createFrameWrite(result, cast);
    }

    @Override
    public void visit(CompareInstruction compare) {
        LLVMExpressionNode result = nodeFactory.createComparison(compare.getOperator(),
                        compare.getLHS().getType(),
                        symbols.resolve(compare.getLHS()),
                        symbols.resolve(compare.getRHS()));

        createFrameWrite(result, compare);
    }

    @Override
    public void visit(ConditionalBranchInstruction branch) {
        LLVMExpressionNode conditionNode = symbols.resolve(branch.getCondition());
        int trueIndex = branch.getTrueSuccessor().getBlockIndex();
        int falseIndex = branch.getFalseSuccessor().getBlockIndex();

        LLVMStatementNode[] phiWriteNodes = getPhiWriteNodes(branch);
        LLVMControlFlowNode node = nodeFactory.createConditionalBranch(trueIndex, falseIndex, conditionNode, phiWriteNodes[0], phiWriteNodes[1], getSourceLocation(branch));

        setControlFlowNode(node);
    }

    @Override
    public void visit(ExtractElementInstruction extract) {
        LLVMExpressionNode vector = symbols.resolve(extract.getVector());
        LLVMExpressionNode index = symbols.resolve(extract.getIndex());
        Type resultType = extract.getType();

        LLVMExpressionNode result = nodeFactory.createExtractElement(resultType, vector, index);

        createFrameWrite(result, extract);
    }

    @Override
    public void visit(ExtractValueInstruction extract) {
        if (!(extract.getAggregate().getType() instanceof ArrayType || extract.getAggregate().getType() instanceof StructureType || extract.getAggregate().getType() instanceof PointerType)) {
            throw new LLVMParserException("\'extractvalue\' can only extract elements of arrays and structs!");
        }
        final LLVMExpressionNode baseAddress = symbols.resolve(extract.getAggregate());
        final Type baseType = extract.getAggregate().getType();
        final int targetIndex = extract.getIndex();
        final Type resultType = extract.getType();

        LLVMExpressionNode targetAddress = baseAddress;

        final AggregateType aggregateType = (AggregateType) baseType;

        long offset = context.getIndexOffset(targetIndex, aggregateType);

        final Type targetType = aggregateType.getElementType(targetIndex);
        if (targetType != null && !((targetType instanceof StructureType) && (((StructureType) targetType).isPacked()))) {
            offset += context.getBytePadding(offset, targetType);
        }

        if (offset != 0) {
            final LLVMExpressionNode oneLiteralNode = nodeFactory.createLiteral(1, PrimitiveType.I32);
            targetAddress = nodeFactory.createTypedElementPointer(targetAddress, oneLiteralNode, offset, extract.getType());
        }

        final LLVMExpressionNode result = nodeFactory.createExtractValue(resultType, targetAddress);
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
                labelTargets[i] = branch.getSuccessor(i).getBlockIndex();
            }
            LLVMExpressionNode value = symbols.resolve(branch.getAddress());

            LLVMControlFlowNode node = nodeFactory.createIndirectBranch(value, labelTargets, getPhiWriteNodes(branch), getSourceLocation(branch));
            setControlFlowNode(node);
        } else {
            assert branch.getSuccessorCount() == 1;
            LLVMControlFlowNode node = nodeFactory.createUnconditionalBranch(branch.getSuccessor(0).getBlockIndex(), getPhiWriteNodes(branch)[0],
                            getSourceLocation(branch));
            setControlFlowNode(node);
        }
    }

    @Override
    public void visit(InsertElementInstruction insert) {
        final LLVMExpressionNode vector = symbols.resolve(insert.getVector());
        final LLVMExpressionNode index = symbols.resolve(insert.getIndex());
        final LLVMExpressionNode element = symbols.resolve(insert.getValue());
        final Type type = insert.getType();
        final LLVMExpressionNode result = nodeFactory.createInsertElement(type, vector, element, index);
        createFrameWrite(result, insert);
    }

    @Override
    public void visit(InsertValueInstruction insert) {
        if (!(insert.getAggregate().getType() instanceof StructureType || insert.getAggregate().getType() instanceof ArrayType)) {
            throw new LLVMParserException("\'insertvalue\' can only insert values into arrays and structs!");
        }
        final AggregateType sourceType = (AggregateType) insert.getAggregate().getType();
        final LLVMExpressionNode sourceAggregate = symbols.resolve(insert.getAggregate());
        final LLVMExpressionNode valueToInsert = symbols.resolve(insert.getValue());
        final Type valueType = insert.getValue().getType();
        final int targetIndex = insert.getIndex();

        final LLVMExpressionNode resultAggregate = nodeFactory.createGetUniqueStackSpace(sourceType, uniquesRegion);

        final long offset = context.getIndexOffset(targetIndex, sourceType);
        final LLVMExpressionNode result = nodeFactory.createInsertValue(resultAggregate, sourceAggregate,
                        context.getByteSize(sourceType), offset, valueToInsert, valueType);

        createFrameWrite(result, insert);
    }

    @Override
    public void visit(LoadInstruction load) {
        LLVMExpressionNode source = symbols.resolve(load.getSource());
        LLVMExpressionNode result = nodeFactory.createLoad(load.getType(), source);
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
        final LLVMSourceLocation location = getSourceLocation(ret, false);
        if (ret.getValue() == null) {
            node = nodeFactory.createRetVoid(location);
        } else {
            final Type type = ret.getValue().getType();
            final LLVMExpressionNode value = symbols.resolve(ret.getValue());
            node = nodeFactory.createNonVoidRet(value, type, location);
        }
        setControlFlowNode(node);
    }

    @Override
    public void visit(SelectInstruction select) {
        final LLVMExpressionNode condition = symbols.resolve(select.getCondition());
        final LLVMExpressionNode trueValue = symbols.resolve(select.getTrueValue());
        final LLVMExpressionNode falseValue = symbols.resolve(select.getFalseValue());
        final Type type = select.getType();

        final LLVMExpressionNode result = nodeFactory.createSelect(type, condition, trueValue, falseValue);

        createFrameWrite(result, select);
    }

    @Override
    public void visit(ShuffleVectorInstruction shuffle) {
        final LLVMExpressionNode vector1 = symbols.resolve(shuffle.getVector1());
        final LLVMExpressionNode vector2 = symbols.resolve(shuffle.getVector2());
        final LLVMExpressionNode mask = symbols.resolve(shuffle.getMask());

        final Type type = shuffle.getType();
        final LLVMExpressionNode result = nodeFactory.createShuffleVector(type, vector1, vector2, mask);

        createFrameWrite(result, shuffle);
    }

    @Override
    public void visit(StoreInstruction store) {
        final LLVMExpressionNode pointerNode = symbols.resolve(store.getDestination());
        final LLVMExpressionNode valueNode = symbols.resolve(store.getSource());

        Type type = store.getSource().getType();

        LLVMSourceLocation source = null;
        if (!(store.getSource() instanceof CallInstruction || store.getSource() instanceof InvokeInstruction) || context.getEnv().getOptions().get(SulongEngineOption.LL_DEBUG)) {
            // otherwise the debugger would stop on both the call and the store of the return value
            source = getSourceLocation(store);
        }

        final LLVMStatementNode node = nodeFactory.createStore(pointerNode, valueNode, type, source);
        addInstruction(node);
    }

    @Override
    public void visit(ReadModifyWriteInstruction rmw) {
        final LLVMExpressionNode pointerNode = symbols.resolve(rmw.getPtr());
        final LLVMExpressionNode valueNode = symbols.resolve(rmw.getValue());

        final Type type = rmw.getValue().getType();

        LLVMExpressionNode result = createReadModifyWrite(rmw.getOperator(), pointerNode, valueNode, type);
        createFrameWrite(result, rmw);
    }

    private LLVMExpressionNode createReadModifyWrite(ReadModifyWriteOperator op, LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type) {
        switch (op) {
            case SUB:
                return nodeFactory.createRMWSub(pointerNode, valueNode, type);
            case XCHG:
                return nodeFactory.createRMWXchg(pointerNode, valueNode, type);
            case ADD:
                return nodeFactory.createRMWAdd(pointerNode, valueNode, type);
            case AND:
                return nodeFactory.createRMWAnd(pointerNode, valueNode, type);
            case NAND:
                return nodeFactory.createRMWNand(pointerNode, valueNode, type);
            case OR:
                return nodeFactory.createRMWOr(pointerNode, valueNode, type);
            case XOR:
                return nodeFactory.createRMWXor(pointerNode, valueNode, type);
            case MAX:
            case MIN:
            case UMIN:
            case UMAX:
            default:
                throw new LLVMParserException("Unsupported read-modify-write operation: " + op);
        }
    }

    @Override
    public void visit(FenceInstruction fence) {
        final LLVMStatementNode node = nodeFactory.createFence();
        addInstruction(node);
    }

    @Override
    public void visit(SwitchInstruction zwitch) {
        LLVMExpressionNode cond = symbols.resolve(zwitch.getCondition());
        int[] successors = new int[zwitch.getCaseCount() + 1];
        for (int i = 0; i < successors.length - 1; i++) {
            successors[i] = zwitch.getCaseBlock(i).getBlockIndex();
        }
        successors[successors.length - 1] = zwitch.getDefaultBlock().getBlockIndex();

        Type llvmType = zwitch.getCondition().getType();
        LLVMExpressionNode[] cases = new LLVMExpressionNode[zwitch.getCaseCount()];
        for (int i = 0; i < cases.length; i++) {
            cases[i] = symbols.resolve(zwitch.getCaseValue(i));
        }

        LLVMControlFlowNode node = nodeFactory.createSwitch(cond, successors, cases, llvmType, getPhiWriteNodes(zwitch), getSourceLocation(zwitch));
        setControlFlowNode(node);
    }

    private LLVMStatementNode[] getPhiWriteNodes(TerminatingInstruction terminatingInstruction) {
        if (blockPhis != null) {
            ArrayList<Phi>[] phisPerSuccessor = LLVMPhiManager.getPhisForSuccessors(terminatingInstruction, blockPhis);
            return convertToPhiWriteNodes(phisPerSuccessor);
        }
        return new LLVMStatementNode[terminatingInstruction.getSuccessorCount()];
    }

    private LLVMStatementNode[] convertToPhiWriteNodes(ArrayList<Phi>[] phisPerSuccessor) {
        if (phisPerSuccessor.length == 0) {
            return LLVMStatementNode.NO_STATEMENTS;
        }

        LLVMStatementNode[] result = new LLVMStatementNode[phisPerSuccessor.length];
        for (int i = 0; i < result.length; i++) {
            LLVMExpressionNode[] from = new LLVMExpressionNode[phisPerSuccessor[i].size()];
            FrameSlot[] to = new FrameSlot[phisPerSuccessor[i].size()];
            Type[] types = new Type[phisPerSuccessor[i].size()];
            for (int j = 0; j < phisPerSuccessor[i].size(); j++) {
                Phi phi = phisPerSuccessor[i].get(j);
                to[j] = getSlot(phi.getPhiValue());
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
            successors[i] = zwitch.getCaseBlock(i).getBlockIndex();
        }
        successors[successors.length - 1] = zwitch.getDefaultBlock().getBlockIndex();

        final PrimitiveType llvmType = (PrimitiveType) zwitch.getCondition().getType();
        final LLVMExpressionNode[] cases = new LLVMExpressionNode[zwitch.getCaseCount()];
        for (int i = 0; i < cases.length; i++) {
            // the case value is always a long here regardless of the values actual type, implicit
            // casts to smaller types in the factoryfacade won't work
            switch (llvmType.getPrimitiveKind()) {
                case I8:
                    cases[i] = nodeFactory.createLiteral((byte) zwitch.getCaseValue(i), llvmType);
                    break;
                case I16:
                    cases[i] = nodeFactory.createLiteral((short) zwitch.getCaseValue(i), llvmType);
                    break;
                case I32:
                    cases[i] = nodeFactory.createLiteral((int) zwitch.getCaseValue(i), llvmType);
                    break;
                default:
                    cases[i] = nodeFactory.createLiteral(zwitch.getCaseValue(i), llvmType);
            }
        }

        LLVMControlFlowNode node = nodeFactory.createSwitch(cond, successors, cases, llvmType, getPhiWriteNodes(zwitch), getSourceLocation(zwitch));
        setControlFlowNode(node);
    }

    @Override
    public void visit(UnreachableInstruction ui) {
        setControlFlowNode(nodeFactory.createUnreachableNode());
    }

    private void createFrameWrite(LLVMExpressionNode result, ValueInstruction source) {
        createFrameWrite(result, source, getSourceLocation(source));
    }

    private void createFrameWrite(LLVMExpressionNode result, ValueInstruction source, LLVMSourceLocation sourceLocation) {
        final LLVMStatementNode node = nodeFactory.createFrameWrite(source.getType(), result, getSlot(source), sourceLocation);
        addInstruction(node);
    }

    private LLVMExpressionNode createInlineAssemblerNode(InlineAsmConstant inlineAsmConstant, LLVMExpressionNode[] argNodes, Type[] argsType, Type retType, LLVMSourceLocation sourceLocation) {
        if (inlineAsmConstant.needsAlignedStack()) {
            throw new LLVMParserException("Assembly Expressions that require an aligned Stack are not supported yet!");
        }
        if (inlineAsmConstant.getDialect() != AsmDialect.AT_T) {
            throw new LLVMParserException("Unsupported Assembly Dialect: " + inlineAsmConstant.getDialect());
        }
        return nodeFactory.createInlineAssemblerExpression(library, inlineAsmConstant.getAsmExpression(), inlineAsmConstant.getAsmFlags(), argNodes, argsType, retType, sourceLocation);
    }

    private FrameSlot getSlot(ValueInstruction instruction) {
        return frame.findFrameSlot(instruction.getName());
    }

    private FrameSlot getExceptionSlot() {
        return frame.findFrameSlot(LLVMUserException.FRAME_SLOT_ID);
    }

    private FrameSlot getStackSlot() {
        return frame.findFrameSlot(LLVMStack.FRAME_ID);
    }

    private void addInstruction(LLVMStatementNode node) {
        blockInstructions.add(node);
        handleNullerInfo();
    }

    void addInstructionUnchecked(LLVMStatementNode instruction) {
        blockInstructions.add(instruction);
    }

    private void handleNullerInfo() {
        for (int i = nullerInfos.size() - 1; i >= 0; i--) {
            LLVMLivenessAnalysis.NullerInformation nuller = nullerInfos.get(i);
            if (nuller.getInstructionIndex() > instructionIndex) {
                // the nuller information is sorted descending by instructionIndex
                break;
            } else if (nuller.getInstructionIndex() == instructionIndex) {
                FrameSlot frameSlot = nuller.getFrameSlot();
                if (!notNullable.contains(frameSlot)) {
                    LLVMStatementNode nullerNode = nodeFactory.createFrameNuller(frameSlot);
                    blockInstructions.add(nullerNode);
                }
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

    private LLVMExpressionNode capsuleAddressByValue(LLVMExpressionNode child, Type type, AttributesGroup paramAttr) {
        final Type pointee = ((PointerType) type).getPointeeType();

        final int size = context.getByteSize(pointee);
        int alignment = context.getByteAlignment(pointee);
        for (Attribute attr : paramAttr.getAttributes()) {
            if (attr instanceof Attribute.KnownIntegerValueAttribute && ((Attribute.KnownIntegerValueAttribute) attr).getAttr() == Attribute.Kind.ALIGN) {
                alignment = ((Attribute.KnownIntegerValueAttribute) attr).getValue();
            }
        }

        return nodeFactory.createVarArgCompoundValue(size, alignment, child);
    }

    private LLVMSourceLocation getSourceLocation(Instruction inst) {
        return getSourceLocation(inst, true);
    }

    private LLVMSourceLocation getSourceLocation(Instruction inst, boolean skipRepeatingLocation) {
        final LLVMSourceLocation location = inst.getSourceLocation();
        if (location == null) {
            return null;
        } else if (Objects.equals(lastLocation, location) && skipRepeatingLocation) {
            return null;
        } else {
            lastLocation = location;
            return location;
        }
    }

    private static boolean isByValue(AttributesGroup parameter) {
        if (parameter == null) {
            return false;
        }

        for (Attribute a : parameter.getAttributes()) {
            if (a instanceof Attribute.KnownAttribute && ((Attribute.KnownAttribute) a).getAttr() == Attribute.Kind.BYVAL) {
                return true;
            }
        }

        return false;
    }
}
