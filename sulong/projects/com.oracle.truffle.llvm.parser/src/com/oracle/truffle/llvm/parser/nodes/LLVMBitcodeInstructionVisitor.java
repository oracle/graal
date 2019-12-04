/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.nodes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.llvm.parser.LLVMLivenessAnalysis;
import com.oracle.truffle.llvm.parser.LLVMPhiManager;
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
import com.oracle.truffle.llvm.parser.util.LLVMBitcodeTypeHelper;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMContext.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.except.LLVMUserException;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.UniquesRegion;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMFrameNuller;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMFrameNullerExpression;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMInstrumentableNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
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
import com.oracle.truffle.llvm.runtime.types.symbols.SSAValue;

public final class LLVMBitcodeInstructionVisitor implements SymbolVisitor {

    public static final FrameSlot[] NO_SLOTS = new FrameSlot[0];

    public static LLVMBitcodeInstructionVisitor create(FrameDescriptor frame, UniquesRegion uniquesRegion, List<Phi> blockPhis, int argCount, LLVMSymbolReadResolver symbols, LLVMContext context,
                    ExternalLibrary library, ArrayList<LLVMLivenessAnalysis.NullerInformation> nullerInfos, HashSet<Integer> notNullable, LLVMRuntimeDebugInformation dbgInfoHandler,
                    DataLayout dataLayout, NodeFactory nodeFactory) {
        return new LLVMBitcodeInstructionVisitor(frame, uniquesRegion, blockPhis, argCount, symbols, context, library, nullerInfos, notNullable, dbgInfoHandler, dataLayout, nodeFactory);
    }

    private final LLVMContext context;
    private final NodeFactory nodeFactory;

    private final FrameDescriptor frame;
    private final List<LLVMPhiManager.Phi> blockPhis;
    private final int argCount;
    private final LLVMSymbolReadResolver symbols;
    private final LLVMContext.ExternalLibrary library;
    private final ArrayList<LLVMLivenessAnalysis.NullerInformation> nullerInfos;
    private final LLVMRuntimeDebugInformation dbgInfoHandler;
    private final LLVMStack.UniquesRegion uniquesRegion;
    private final DataLayout dataLayout;
    private final HashSet<Integer> notNullable;

    private final List<LLVMNode> instructionNodes;
    private final List<SSAValue> instructionTargets;

    // Liveness analysis info for the current instruction (which slots can be cleared afterwards).
    private SSAValue[] nullerInfo;
    private LLVMControlFlowNode controlFlowNode;

    private LLVMSourceLocation lastLocation;

    private boolean optimizeFrameSlots;

    private LLVMBitcodeInstructionVisitor(FrameDescriptor frame, LLVMStack.UniquesRegion uniquesRegion, List<LLVMPhiManager.Phi> blockPhis, int argCount, LLVMSymbolReadResolver symbols,
                    LLVMContext context, LLVMContext.ExternalLibrary library, ArrayList<LLVMLivenessAnalysis.NullerInformation> nullerInfos, HashSet<Integer> notNullable,
                    LLVMRuntimeDebugInformation dbgInfoHandler, DataLayout dataLayout, NodeFactory nodeFactory) {
        this.context = context;
        this.nodeFactory = nodeFactory;
        this.frame = frame;
        this.blockPhis = blockPhis;
        this.argCount = argCount;
        this.symbols = symbols;
        this.library = library;
        this.nullerInfos = nullerInfos;
        this.notNullable = notNullable;
        this.dbgInfoHandler = dbgInfoHandler;
        this.uniquesRegion = uniquesRegion;
        this.dataLayout = dataLayout;

        this.instructionNodes = new ArrayList<>();
        this.instructionTargets = new ArrayList<>();

        this.optimizeFrameSlots = context.getEnv().getOptions().get(SulongEngineOption.OPTIMIZE_FRAME_SLOTS) && !context.getEnv().getOptions().get(SulongEngineOption.LL_DEBUG);
    }

    public LLVMControlFlowNode getControlFlowNode() {
        return controlFlowNode;
    }

    public void setInstructionIndex(int instructionIndex) {
        assert instructionNodes.size() == instructionTargets.size();

        // extract the slots that can be nulled after this instruction
        if (!nullerInfos.isEmpty()) {
            assert nullerInfos.get(nullerInfos.size() - 1).getInstructionIndex() >= instructionIndex : "we either missed an instruction or the nuller information is not sorted correctly";
            int last = nullerInfos.size();
            while (last > 0 && nullerInfos.get(last - 1).getInstructionIndex() == instructionIndex) {
                last--;
            }
            if (last < nullerInfos.size()) {
                SSAValue[] slots = new SSAValue[nullerInfos.size() - last];
                for (int i = slots.length - 1; i >= 0; i--) {
                    slots[i] = nullerInfos.get(last + i).getIdentifier();
                    nullerInfos.remove(last + i);
                }
                nullerInfo = slots;
                return;
            }
        }
        nullerInfo = null;
    }

    public LLVMStatementNode[] finish() {
        for (int i = 0; i < instructionNodes.size(); i++) {
            LLVMNode node = instructionNodes.get(i);
            SSAValue target = instructionTargets.get(i);
            if (target == null) {
                assert node instanceof LLVMStatementNode;
            } else {
                assert node instanceof LLVMExpressionNode;
                instructionNodes.set(i, nodeFactory.createFrameWrite(target.getType(), (LLVMExpressionNode) node, LLVMSymbolReadResolver.findOrAddFrameSlot(frame, target)));
            }
        }
        return instructionNodes.toArray(LLVMStatementNode.NO_STATEMENTS);
    }

    private FrameSlot[] createNullerSlots(SSAValue[] stackValues) {
        if (stackValues != null) {
            int count = 0;
            for (SSAValue value : stackValues) {
                if (value != null) {
                    count++;
                }
            }
            if (count > 0) {
                int pos = 0;
                FrameSlot[] result = new FrameSlot[count];
                for (SSAValue value : stackValues) {
                    if (value != null) {
                        result[pos++] = LLVMSymbolReadResolver.findOrAddFrameSlot(frame, value);
                    }
                }
                return result;
            }
        }
        return null;
    }

    public static FrameSlot findFrameSlot(FrameDescriptor frame, int frameIdentifier) {
        return frame.findFrameSlot(frameIdentifier);
    }

    @Override
    public void defaultAction(SymbolImpl symbol) {
        throw new LLVMParserException("Instruction not implemented: " + symbol.getClass().getSimpleName());
    }

    /**
     * Optimizes the operands of instructions - if this function is used, it needs to be called for
     * all operands in reverse order (last ones first).
     *
     * @param symbol The symbol to resolve.
     * @param other All other inputs of the current instruction.
     * @return The instruction that loads the value from the frame, or the instruction that creates
     *         it (optimized case).
     */
    private LLVMExpressionNode resolveOptimized(SymbolImpl symbol, int excludeOtherIndex, SymbolImpl other, SymbolImpl... others) {
        if (optimizeFrameSlots && nullerInfo != null) {
            if (symbol instanceof SSAValue) {
                if (symbol == other || notNullable.contains(((SSAValue) symbol).getFrameIdentifier())) {
                    return symbols.resolve(symbol);
                }
                for (int i = 0; i < others.length; i++) {
                    SymbolImpl o = others[i];
                    if (i != excludeOtherIndex && symbol == o) {
                        // if it's used multiple times, it needs to be in a frame slot
                        return symbols.resolve(symbol);
                    }
                }
                for (int i = 0; i < nullerInfo.length; i++) {
                    if (nullerInfo[i] == symbol) {
                        // we cannot optimize this value away if it has been read before
                        if (frame.findFrameSlot(nullerInfo[i].getFrameIdentifier()) == null) {
                            // this value is read and cleared afterwards
                            LLVMExpressionNode node = extractNulledValue(nullerInfo[i]);
                            if (node != null) {
                                nullerInfo[i] = null;
                                return node;
                            }
                        }
                        break;
                    }
                }
            }
        }
        return symbols.resolve(symbol);
    }

    private LLVMExpressionNode resolveOptimized(SymbolImpl symbol, SymbolImpl... other) {
        return resolveOptimized(symbol, -1, null, other);
    }

    private LLVMExpressionNode extractNulledValue(SSAValue slot) {
        assert slot != null;
        if (instructionNodes.isEmpty()) {
            return null;
        }
        SSAValue target = instructionTargets.get(instructionTargets.size() - 1);
        if (target == slot) {
            LLVMExpressionNode expression = (LLVMExpressionNode) instructionNodes.get(instructionNodes.size() - 1);
            instructionNodes.remove(instructionNodes.size() - 1);
            instructionTargets.remove(instructionTargets.size() - 1);
            return expression;
        }
        return null;
    }

    @Override
    public void visit(AllocateInstruction allocate) {
        final Type type = allocate.getPointeeType();
        int alignment;
        if (allocate.getAlign() == 0) {
            alignment = type.getAlignment(dataLayout);
        } else {
            alignment = 1 << (allocate.getAlign() - 1);
        }
        if (alignment == 0) {
            alignment = LLVMStack.NO_ALIGNMENT_REQUIREMENTS;
        }

        final SymbolImpl count = allocate.getCount();
        LLVMExpressionNode result;
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
            LLVMExpressionNode num = resolveOptimized(count);
            result = nodeFactory.createAllocaArray(type, num, alignment);
        }

        // we never want to step on allocations, only to actual assignments in the source
        final SourceInstrumentationStrategy intention;
        if (context.getEnv().getOptions().get(SulongEngineOption.LL_DEBUG)) {
            intention = SourceInstrumentationStrategy.FORCED;
        } else {
            intention = SourceInstrumentationStrategy.DISABLED;
        }
        createFrameWrite(result, allocate, intention);
    }

    @Override
    public void visit(BinaryOperationInstruction operation) {
        SymbolImpl rhs = operation.getRHS();
        SymbolImpl lhs = operation.getLHS();
        LLVMExpressionNode rhsNode = resolveOptimized(rhs, lhs);
        LLVMExpressionNode lhsNode = resolveOptimized(lhs, rhs);
        LLVMExpressionNode result = LLVMBitcodeTypeHelper.createArithmeticInstruction(lhsNode, rhsNode, operation.getOperator(), operation.getType(), nodeFactory);
        createFrameWrite(result, operation);
    }

    @Override
    public void visit(BranchInstruction branch) {
        LLVMControlFlowNode unconditionalBranchNode = nodeFactory.createUnconditionalBranch(branch.getSuccessor().getBlockIndex(),
                        getPhiWriteNodes(branch)[0]);
        setControlFlowNode(unconditionalBranchNode, branch);
    }

    @Override
    public void visit(CallInstruction call) {
        final Type targetType = call.getType();
        int argumentCount = getArgumentCount(call.getArgumentCount(), targetType);
        final LLVMExpressionNode[] argNodes = new LLVMExpressionNode[argumentCount];
        final Type[] argTypes = new Type[argumentCount];
        int argIndex = 0;
        // stack pointer
        argNodes[argIndex] = CommonNodeFactory.createFrameRead(PointerType.VOID, getStackSlot());
        argTypes[argIndex] = new PointerType(null);
        argIndex++;

        if (targetType instanceof StructureType) {
            argTypes[argIndex] = new PointerType(targetType);
            argNodes[argIndex] = nodeFactory.createGetUniqueStackSpace(targetType, uniquesRegion);
            argIndex++;
        }
        final SymbolImpl target = call.getCallTarget();
        for (int i = call.getArgumentCount() - 1; i >= 0; i--) {
            argNodes[argIndex + i] = resolveOptimized(call.getArgument(i), i, target, call.getArguments());
            argTypes[argIndex + i] = call.getArgument(i).getType();
            final AttributesGroup paramAttr = call.getParameterAttributesGroup(i);
            if (isByValue(paramAttr)) {
                argNodes[argIndex + i] = capsuleAddressByValue(argNodes[argIndex + i], argTypes[argIndex + i], paramAttr);
            }
        }

        LLVMExpressionNode result = nodeFactory.createLLVMBuiltin(target, argNodes, argTypes, argCount);
        SourceInstrumentationStrategy intent = SourceInstrumentationStrategy.ONLY_FIRST_STATEMENT_ON_LOCATION;
        if (result == null) {
            if (target instanceof InlineAsmConstant) {
                final InlineAsmConstant inlineAsmConstant = (InlineAsmConstant) target;
                result = createInlineAssemblerNode(inlineAsmConstant, argNodes, argTypes, targetType);
            } else {
                LLVMExpressionNode function = symbols.resolve(target);
                result = CommonNodeFactory.createFunctionCall(function, argNodes, new FunctionType(targetType, argTypes, false));

                // the callNode needs to be instrumentable so that the debugger can see the CallTag.
                // If it did not provide a source location, the debugger may not be able to show the
                // node on the call stack or offer stepping into the call.
                intent = SourceInstrumentationStrategy.FORCED;
            }
        }

        // the SourceSection references the call, not the return value assignment
        createFrameWrite(result, call, intent);
    }

    @Override
    public void visit(LandingpadInstruction landingpadInstruction) {
        Type type = landingpadInstruction.getType();
        LLVMExpressionNode allocateLandingPadValue = nodeFactory.createGetUniqueStackSpace(type, uniquesRegion);
        LLVMExpressionNode[] entries = new LLVMExpressionNode[landingpadInstruction.getClauseSymbols().length];
        for (int i = 0; i < entries.length; i++) {
            // cannot optimize reads here - landingpad doesn't evaluate all arguments
            entries[i] = symbols.resolve(landingpadInstruction.getClauseSymbols()[i]);
        }
        LLVMExpressionNode getStack = CommonNodeFactory.createFrameRead(PointerType.VOID, getStackSlot());
        LLVMExpressionNode landingPad = nodeFactory.createLandingPad(allocateLandingPadValue, getExceptionSlot(), landingpadInstruction.isCleanup(), landingpadInstruction.getClauseTypes(),
                        entries, getStack);
        createFrameWrite(landingPad, landingpadInstruction);
    }

    @Override
    public void visit(ResumeInstruction resumeInstruction) {
        LLVMControlFlowNode resume = nodeFactory.createResumeInstruction(getExceptionSlot());
        setControlFlowNode(resume, resumeInstruction);
    }

    @Override
    public void visit(CompareExchangeInstruction cmpxchg) {
        SymbolImpl ptr = cmpxchg.getPtr();
        SymbolImpl cmp = cmpxchg.getCmp();
        SymbolImpl replace = cmpxchg.getReplace();
        LLVMExpressionNode newNode = resolveOptimized(replace, ptr, cmp);
        LLVMExpressionNode cmpNode = resolveOptimized(cmp, ptr, replace);
        LLVMExpressionNode ptrNode = resolveOptimized(ptr, cmp, replace);

        LLVMExpressionNode result = nodeFactory.createCompareExchangeInstruction(cmpxchg.getAggregateType(), cmp.getType(), ptrNode, cmpNode, newNode);
        createFrameWrite(result, cmpxchg);
    }

    private void visitDebugIntrinsic(SymbolImpl value, SourceVariable variable, MDExpression expression, long index, boolean isDeclaration) {
        final LLVMStatementNode dbgIntrinsic = dbgInfoHandler.handleDebugIntrinsic(value, variable, expression, index, isDeclaration);
        if (dbgIntrinsic != null) {
            addStatementWithoutSource(dbgIntrinsic);
        } else {
            handleNullerInfo();
        }
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
        addStatement(CommonNodeFactory.createDebugTrap(), inst, SourceInstrumentationStrategy.FORCED);
    }

    @Override
    public void visit(VoidCallInstruction call) {
        final int argumentCount = call.getArgumentCount() + 1; // stackpointer
        final LLVMExpressionNode[] argNodes = new LLVMExpressionNode[argumentCount];
        final Type[] argTypes = new Type[argumentCount];

        int argIndex = 0;
        argNodes[argIndex] = CommonNodeFactory.createFrameRead(PointerType.VOID, getStackSlot());
        argTypes[argIndex] = new PointerType(null);
        argIndex++;

        SymbolImpl target = call.getCallTarget();

        for (int i = call.getArgumentCount() - 1; i >= 0; i--) {
            argNodes[argIndex + i] = resolveOptimized(call.getArgument(i), i, target, call.getArguments());
            argTypes[argIndex + i] = call.getArgument(i).getType();
            final AttributesGroup paramAttr = call.getParameterAttributesGroup(i);
            if (isByValue(paramAttr)) {
                argNodes[argIndex + i] = capsuleAddressByValue(argNodes[argIndex + i], argTypes[argIndex + i], paramAttr);
            }
        }

        LLVMExpressionNode result = nodeFactory.createLLVMBuiltin(target, argNodes, argTypes, argCount);
        SourceInstrumentationStrategy intent = SourceInstrumentationStrategy.ONLY_FIRST_STATEMENT_ON_LOCATION;
        if (result == null) {
            if (target instanceof InlineAsmConstant) {
                final InlineAsmConstant inlineAsmConstant = (InlineAsmConstant) target;
                result = createInlineAssemblerNode(inlineAsmConstant, argNodes, argTypes, call.getType());
                assignSourceLocation(result, call);
            } else {
                final LLVMExpressionNode function = resolveOptimized(target, call.getArguments());
                final FunctionType functionType = new FunctionType(call.getType(), argTypes, false);
                result = CommonNodeFactory.createFunctionCall(function, argNodes, functionType);

                // the callNode needs to be instrumentable so that the debugger can see the CallTag.
                // If it did not provide a source location, the debugger may not be able to show the
                // node on the call stack or offer stepping into the call.
                intent = SourceInstrumentationStrategy.FORCED;
            }
        }

        addStatement(result, call, intent);
    }

    @Override
    public void visit(InvokeInstruction call) {
        final Type targetType = call.getType();
        int argumentCount = getArgumentCount(call.getArgumentCount(), targetType);
        final LLVMExpressionNode[] argNodes = new LLVMExpressionNode[argumentCount];
        final Type[] argTypes = new Type[argumentCount];
        int argIndex = 0;
        argNodes[argIndex] = CommonNodeFactory.createFrameRead(PointerType.VOID, getStackSlot());
        argTypes[argIndex] = new PointerType(null);
        argIndex++;
        final SymbolImpl target = call.getCallTarget();
        if (targetType instanceof StructureType) {
            argTypes[argIndex] = new PointerType(targetType);
            argNodes[argIndex] = nodeFactory.createGetUniqueStackSpace(targetType, uniquesRegion);
            argIndex++;
        }
        for (int i = call.getArgumentCount() - 1; i >= 0; i--) {
            argNodes[argIndex + i] = resolveOptimized(call.getArgument(i), i, target, call.getArguments());
            argTypes[argIndex + i] = call.getArgument(i).getType();
            final AttributesGroup paramAttr = call.getParameterAttributesGroup(i);
            if (isByValue(paramAttr)) {
                argNodes[argIndex + i] = capsuleAddressByValue(argNodes[argIndex + i], argTypes[argIndex + i], paramAttr);
            }
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
            for (LLVMPhiManager.Phi phi : blockPhis) {
                FrameSlot slot = LLVMSymbolReadResolver.findOrAddFrameSlot(frame, phi.getPhiValue());
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

        // Builtins are not AST-inlined for Invokes, instead a generic LLVMDispatchNode is used.
        LLVMExpressionNode function = symbols.resolve(target);
        LLVMControlFlowNode result = nodeFactory.createFunctionInvoke(LLVMSymbolReadResolver.findOrAddFrameSlot(frame, call), function, argNodes, new FunctionType(targetType, argTypes, false),
                        regularIndex, unwindIndex, normalPhi, unwindPhi);

        setControlFlowNode(result, call, SourceInstrumentationStrategy.FORCED);
    }

    @Override
    public void visit(VoidInvokeInstruction call) {
        final SymbolImpl target = call.getCallTarget();

        final int argumentCount = call.getArgumentCount() + 1; // stackpointer
        final LLVMExpressionNode[] args = new LLVMExpressionNode[argumentCount];
        final Type[] argsType = new Type[argumentCount];

        int argIndex = 0;
        args[argIndex] = CommonNodeFactory.createFrameRead(PointerType.VOID, getStackSlot());
        argsType[argIndex] = new PointerType(null);
        argIndex++;

        for (int i = call.getArgumentCount() - 1; i >= 0; i--) {
            args[argIndex + i] = symbols.resolve(call.getArgument(i));
            argsType[argIndex + i] = call.getArgument(i).getType();
            final AttributesGroup paramAttr = call.getParameterAttributesGroup(i);
            if (isByValue(paramAttr)) {
                args[argIndex + i] = capsuleAddressByValue(args[argIndex + i], argsType[argIndex + i], paramAttr);
            }
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
            for (LLVMPhiManager.Phi phi : blockPhis) {
                FrameSlot slot = LLVMSymbolReadResolver.findOrAddFrameSlot(frame, phi.getPhiValue());
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

        // Builtins are not AST-inlined for Invokes, instead a generic LLVMDispatchNode is used.
        LLVMExpressionNode function = resolveOptimized(target, call.getArguments());
        LLVMControlFlowNode result = nodeFactory.createFunctionInvoke(null, function, args, new FunctionType(call.getType(), argsType, false),
                        regularIndex, unwindIndex, normalPhi, unwindPhi);

        setControlFlowNode(result, call, SourceInstrumentationStrategy.FORCED);
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
        LLVMExpressionNode fromNode = resolveOptimized(cast.getValue());
        Type from = cast.getValue().getType();
        Type to = cast.getType();

        LLVMExpressionNode result = LLVMBitcodeTypeHelper.createCast(fromNode, to, from, cast.getOperator(), nodeFactory);
        createFrameWrite(result, cast);
    }

    @Override
    public void visit(CompareInstruction compare) {
        SymbolImpl rhs = compare.getRHS();
        SymbolImpl lhs = compare.getLHS();
        LLVMExpressionNode rhsNode = resolveOptimized(rhs, lhs);
        LLVMExpressionNode lhsNode = resolveOptimized(lhs, rhs);

        LLVMExpressionNode result = CommonNodeFactory.createComparison(compare.getOperator(), lhs.getType(), lhsNode, rhsNode);

        createFrameWrite(result, compare);
    }

    @Override
    public void visit(ConditionalBranchInstruction branch) {
        LLVMExpressionNode conditionNode = symbols.resolve(branch.getCondition());
        int trueIndex = branch.getTrueSuccessor().getBlockIndex();
        int falseIndex = branch.getFalseSuccessor().getBlockIndex();

        LLVMStatementNode[] phiWriteNodes = getPhiWriteNodes(branch);
        LLVMControlFlowNode node = nodeFactory.createConditionalBranch(trueIndex, falseIndex, conditionNode, phiWriteNodes[0], phiWriteNodes[1]);

        setControlFlowNode(node, branch);
    }

    @Override
    public void visit(ExtractElementInstruction extract) {
        SymbolImpl index = extract.getIndex();
        SymbolImpl vector = extract.getVector();
        LLVMExpressionNode indexNode = resolveOptimized(index, vector);
        LLVMExpressionNode vectorNode = resolveOptimized(vector, index);

        LLVMExpressionNode result = nodeFactory.createExtractElement(extract.getType(), vectorNode, indexNode);

        createFrameWrite(result, extract);
    }

    @Override
    public void visit(ExtractValueInstruction extract) {
        if (!(extract.getAggregate().getType() instanceof ArrayType || extract.getAggregate().getType() instanceof StructureType || extract.getAggregate().getType() instanceof PointerType)) {
            throw new LLVMParserException("\'extractvalue\' can only extract elements of arrays and structs!");
        }
        final LLVMExpressionNode baseAddress = resolveOptimized(extract.getAggregate());
        final Type baseType = extract.getAggregate().getType();
        final int targetIndex = extract.getIndex();
        final Type resultType = extract.getType();

        LLVMExpressionNode targetAddress = baseAddress;

        final AggregateType aggregateType = (AggregateType) baseType;

        long offset = aggregateType.getOffsetOf(targetIndex, dataLayout);

        final Type targetType = aggregateType.getElementType(targetIndex);
        if (targetType != null && !((targetType instanceof StructureType) && (((StructureType) targetType).isPacked()))) {
            offset += Type.getPadding(offset, targetType, dataLayout);
        }

        if (offset != 0) {
            final LLVMExpressionNode oneLiteralNode = nodeFactory.createLiteral(1, PrimitiveType.I32);
            targetAddress = nodeFactory.createTypedElementPointer(offset, extract.getType(), targetAddress, oneLiteralNode);
        }

        final LLVMExpressionNode result = nodeFactory.createExtractValue(resultType, targetAddress);
        createFrameWrite(result, extract);
    }

    @Override
    public void visit(GetElementPointerInstruction gep) {
        LLVMExpressionNode targetAddress = symbols.resolveElementPointer(gep.getBasePointer(), gep.getIndices(), this::resolveOptimized);
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

            LLVMControlFlowNode node = nodeFactory.createIndirectBranch(value, labelTargets, getPhiWriteNodes(branch));
            setControlFlowNode(node, branch);
        } else {
            assert branch.getSuccessorCount() == 1;
            LLVMControlFlowNode node = nodeFactory.createUnconditionalBranch(branch.getSuccessor(0).getBlockIndex(), getPhiWriteNodes(branch)[0]);
            setControlFlowNode(node, branch);
        }
    }

    @Override
    public void visit(InsertElementInstruction insert) {
        SymbolImpl index = insert.getIndex();
        SymbolImpl value = insert.getValue();
        SymbolImpl vector = insert.getVector();
        LLVMExpressionNode indexNode = resolveOptimized(index, index, vector);
        LLVMExpressionNode elementNode = resolveOptimized(value, value, vector);
        LLVMExpressionNode vectorNode = resolveOptimized(vector, value, index);

        LLVMExpressionNode result = nodeFactory.createInsertElement(insert.getType(), vectorNode, elementNode, indexNode);

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

        final long offset = sourceType.getOffsetOf(targetIndex, dataLayout);
        final LLVMExpressionNode result = nodeFactory.createInsertValue(resultAggregate, sourceAggregate,
                        sourceType.getSize(dataLayout), offset, valueToInsert, valueType);

        createFrameWrite(result, insert);
    }

    @Override
    public void visit(LoadInstruction load) {
        LLVMExpressionNode source = resolveOptimized(load.getSource());
        LLVMExpressionNode result = CommonNodeFactory.createLoad(load.getType(), source);

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
            node = nodeFactory.createRetVoid();
        } else {
            final Type type = ret.getValue().getType();
            final LLVMExpressionNode value = symbols.resolve(ret.getValue());
            node = nodeFactory.createNonVoidRet(value, type);
        }
        setControlFlowNode(node, ret);
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
        SymbolImpl value = store.getSource();
        SymbolImpl pointer = store.getDestination();
        LLVMExpressionNode valueNode = resolveOptimized(value, pointer);
        LLVMExpressionNode pointerNode = resolveOptimized(pointer, value);

        SourceInstrumentationStrategy intention = SourceInstrumentationStrategy.DISABLED;
        if (!(store.getSource() instanceof CallInstruction || store.getSource() instanceof InvokeInstruction) || context.getEnv().getOptions().get(SulongEngineOption.LL_DEBUG)) {
            // otherwise the debugger would stop on both the call and the store of the return value
            intention = SourceInstrumentationStrategy.ONLY_FIRST_STATEMENT_ON_LOCATION;
        }

        Type type = value.getType();
        LLVMStatementNode node = nodeFactory.createStore(pointerNode, valueNode, type);
        addStatement(node, store, intention);
    }

    @Override
    public void visit(ReadModifyWriteInstruction rmw) {
        SymbolImpl value = rmw.getValue();
        SymbolImpl pointer = rmw.getPtr();
        LLVMExpressionNode valueNode = resolveOptimized(value, pointer);
        LLVMExpressionNode pointerNode = resolveOptimized(pointer, value);

        Type type = rmw.getValue().getType();

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
            case FADD:
            case FSUB:
            default:
                throw new LLVMParserException("Unsupported read-modify-write operation: " + op);
        }
    }

    @Override
    public void visit(FenceInstruction fence) {
        addStatement(nodeFactory.createFence(), fence);
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

        LLVMControlFlowNode node = nodeFactory.createSwitch(cond, successors, cases, llvmType, getPhiWriteNodes(zwitch));
        setControlFlowNode(node, zwitch);
    }

    private LLVMStatementNode[] getPhiWriteNodes(TerminatingInstruction terminatingInstruction) {
        if (blockPhis != null) {
            ArrayList<LLVMPhiManager.Phi>[] phisPerSuccessor = LLVMPhiManager.getPhisForSuccessors(terminatingInstruction, blockPhis);
            return convertToPhiWriteNodes(phisPerSuccessor);
        }
        return new LLVMStatementNode[terminatingInstruction.getSuccessorCount()];
    }

    private LLVMStatementNode createAggregatePhi(LLVMExpressionNode[] from, FrameSlot[] to, Type[] types) {
        return nodeFactory.createPhi(from, to, types);
    }

    private LLVMStatementNode[] convertToPhiWriteNodes(ArrayList<LLVMPhiManager.Phi>[] phisPerSuccessor) {
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
                to[j] = LLVMSymbolReadResolver.findOrAddFrameSlot(frame, phi.getPhiValue());
                from[j] = symbols.resolve(phi.getValue());
                types[j] = phi.getValue().getType();
            }
            result[i] = createAggregatePhi(from, to, types);
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

        LLVMControlFlowNode node = nodeFactory.createSwitch(cond, successors, cases, llvmType, getPhiWriteNodes(zwitch));
        setControlFlowNode(node, zwitch);
    }

    @Override
    public void visit(UnreachableInstruction ui) {
        final LLVMControlFlowNode node = nodeFactory.createUnreachableNode();
        setControlFlowNode(node, ui);
    }

    public void handleNullerInfo() {
        FrameSlot[] frameSlots = createNullerSlots(nullerInfo);
        nullerInfo = null;
        if (frameSlots != null) {
            if (instructionNodes.isEmpty()) {
                instructionNodes.add(new LLVMFrameNuller(frameSlots, null));
                instructionTargets.add(null);
            } else {
                int index = instructionNodes.size() - 1;
                LLVMNode node = instructionNodes.get(index);
                if (node instanceof LLVMStatementNode) {
                    node = new LLVMFrameNuller(frameSlots, (LLVMStatementNode) node);
                } else {
                    node = new LLVMFrameNullerExpression(frameSlots, (LLVMExpressionNode) node);
                }
                instructionNodes.set(instructionNodes.size() - 1, node);
            }
        }
    }

    private void addNode(LLVMNode node, ValueInstruction target) {
        instructionNodes.add(node);
        instructionTargets.add(target);
    }

    private void createFrameWrite(LLVMExpressionNode result, ValueInstruction source) {
        createFrameWrite(result, source, SourceInstrumentationStrategy.ONLY_FIRST_STATEMENT_ON_LOCATION);
    }

    private void createFrameWrite(LLVMExpressionNode result, ValueInstruction source, SourceInstrumentationStrategy intention) {
        assignSourceLocation(result, source, intention);
        addNode(result, source);
        handleNullerInfo();
    }

    private LLVMExpressionNode createInlineAssemblerNode(InlineAsmConstant inlineAsmConstant, LLVMExpressionNode[] argNodes, Type[] argsType, Type retType) {
        if (inlineAsmConstant.needsAlignedStack()) {
            throw new LLVMParserException("Assembly Expressions that require an aligned Stack are not supported yet!");
        }
        if (inlineAsmConstant.getDialect() != AsmDialect.AT_T) {
            throw new LLVMParserException("Unsupported Assembly Dialect: " + inlineAsmConstant.getDialect());
        }
        return nodeFactory.createInlineAssemblerExpression(library, inlineAsmConstant.getAsmExpression(), inlineAsmConstant.getAsmFlags(), argNodes, argsType, retType);
    }

    private FrameSlot getExceptionSlot() {
        return frame.findFrameSlot(LLVMUserException.FRAME_SLOT_ID);
    }

    private FrameSlot getStackSlot() {
        return frame.findFrameSlot(LLVMStack.FRAME_ID);
    }

    private void addStatement(LLVMStatementNode node, Instruction instruction) {
        addStatement(node, instruction, SourceInstrumentationStrategy.ONLY_FIRST_STATEMENT_ON_LOCATION);
    }

    private void addStatement(LLVMExpressionNode node, Instruction instruction, SourceInstrumentationStrategy intention) {
        assignSourceLocation(node, instruction, intention);
        addNode(LLVMVoidStatementNodeGen.create(node), null);
        handleNullerInfo();
    }

    private void addStatement(LLVMStatementNode node, Instruction instruction, SourceInstrumentationStrategy intention) {
        assignSourceLocation(node, instruction, intention);
        addNode(node, null);
        handleNullerInfo();
    }

    private void addStatementWithoutSource(LLVMStatementNode node) {
        addNode(node, null);
        handleNullerInfo();
    }

    public void addStatementUnchecked(LLVMStatementNode node) {
        addNode(node, null);
    }

    private void setControlFlowNode(LLVMControlFlowNode controlFlowNode, Instruction sourceInstruction) {
        setControlFlowNode(controlFlowNode, sourceInstruction, SourceInstrumentationStrategy.ONLY_FIRST_STATEMENT_ON_LOCATION);
    }

    private void setControlFlowNode(LLVMControlFlowNode controlFlowNode, Instruction sourceInstruction, SourceInstrumentationStrategy intention) {
        assert this.controlFlowNode == null;
        this.controlFlowNode = controlFlowNode;
        assignSourceLocation(controlFlowNode, sourceInstruction, intention);
    }

    private LLVMExpressionNode capsuleAddressByValue(LLVMExpressionNode child, Type type, AttributesGroup paramAttr) {
        final Type pointee = ((PointerType) type).getPointeeType();

        final int size = pointee.getSize(dataLayout);
        int alignment = pointee.getAlignment(dataLayout);
        for (Attribute attr : paramAttr.getAttributes()) {
            if (attr instanceof Attribute.KnownIntegerValueAttribute && ((Attribute.KnownIntegerValueAttribute) attr).getAttr() == Attribute.Kind.ALIGN) {
                alignment = ((Attribute.KnownIntegerValueAttribute) attr).getValue();
            }
        }

        return nodeFactory.createVarArgCompoundValue(size, alignment, child);
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

    private void assignSourceLocation(LLVMInstrumentableNode node, Instruction sourceInstruction) {
        assignSourceLocation(node, sourceInstruction, SourceInstrumentationStrategy.ONLY_FIRST_STATEMENT_ON_LOCATION);
    }

    private void assignSourceLocation(LLVMInstrumentableNode node, Instruction sourceInstruction, SourceInstrumentationStrategy instrumentationStrategy) {
        if (node == null) {
            return;
        }

        final LLVMSourceLocation location = sourceInstruction.getSourceLocation();
        if (location == null) {
            return;
        }

        node.setSourceLocation(location);

        assert instrumentationStrategy != null;
        switch (instrumentationStrategy) {
            case FORCED:
                node.setHasStatementTag(true);
                lastLocation = location;
                break;
            case ONLY_FIRST_STATEMENT_ON_LOCATION:
                if (lastLocation == location) {
                    // shortcut for locations assigned by DEBUG_LOC_AGAIN
                    break;
                } else if (lastLocation != null && Objects.equals(lastLocation.describeFile(), location.describeFile()) && lastLocation.getLine() == location.getLine()) {
                    // only mark the first node occurring at a particular line in a particular file
                    // as statement. this way, Sulong can simulate statement-level debugging even
                    // though llvm debug information does not actually describe statement boundaries
                    break;
                } else {
                    node.setHasStatementTag(true);
                    lastLocation = location;
                }
                break;
            case DISABLED:
                break;
            default:
                throw new LLVMParserException("Unknown instrumentation strategy: " + instrumentationStrategy);
        }
    }

    private enum SourceInstrumentationStrategy {
        // calls and invokes should always be instrumentable so that the debugger can properly
        // display a call-stack and offer step-into / step-over
        FORCED,

        // the avoid the debugger stepping on the same line repeatedly only the first node with that
        // location should be instrumentable
        ONLY_FIRST_STATEMENT_ON_LOCATION,

        // some nodes should not be instrumentable, but still have a source location attached so
        // that a proper stack-trace can be built in case of an exception
        DISABLED
    }
}
