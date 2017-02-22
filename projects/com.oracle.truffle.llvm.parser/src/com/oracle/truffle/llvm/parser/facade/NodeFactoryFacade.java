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
package com.oracle.truffle.llvm.parser.facade;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.api.LLVMStackFrameNuller;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionKind;
import com.oracle.truffle.llvm.parser.model.enums.CompareOperator;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.model.globals.GlobalVariable;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VectorType;

/**
 * This interface decouples the parser and the concrete implementation of the nodes by only making
 * {@link LLVMExpressionNode} and {@link LLVMExpressionNode} visible. The parser should not directly
 * instantiate a node, but instead use the factory facade.
 */
public interface NodeFactoryFacade {

    LLVMExpressionNode createInsertElement(LLVMParserRuntime runtime, Type resultType, LLVMExpressionNode vector, LLVMExpressionNode element, LLVMExpressionNode index);

    LLVMExpressionNode createExtractElement(LLVMParserRuntime runtime, Type resultType, LLVMExpressionNode vector, LLVMExpressionNode index);

    LLVMExpressionNode createShuffleVector(LLVMParserRuntime runtime, Type llvmType, LLVMExpressionNode vector1, LLVMExpressionNode vector2,
                    LLVMExpressionNode mask);

    LLVMExpressionNode createLoad(LLVMParserRuntime runtime, Type resolvedResultType, LLVMExpressionNode loadTarget);

    LLVMExpressionNode createStore(LLVMParserRuntime runtime, LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type);

    LLVMExpressionNode createLogicalOperation(LLVMParserRuntime runtime, LLVMExpressionNode left, LLVMExpressionNode right, LLVMLogicalInstructionKind opCode, Type llvmType);

    LLVMExpressionNode createLiteral(LLVMParserRuntime runtime, Object value, Type type);

    LLVMExpressionNode createSimpleConstantNoArray(LLVMParserRuntime runtime, Object constant, Type instructionType);

    LLVMExpressionNode createVectorLiteralNode(LLVMParserRuntime runtime, List<LLVMExpressionNode> listValues, Type type);

    LLVMExpressionNode tryCreateFunctionCallSubstitution(LLVMParserRuntime runtime, String name, LLVMExpressionNode[] argNodes, int numberOfExplicitArguments,
                    FrameSlot exceptionValueSlot);

    LLVMControlFlowNode tryCreateFunctionInvokeSubstitution(LLVMParserRuntime runtime, String name, FunctionType type, int numberOfExplicitArguments, LLVMExpressionNode[] argNodes,
                    FrameSlot returnValueSlot, FrameSlot exceptionValueSlot, int normalIndex,
                    int unwindIndex, LLVMExpressionNode[] normalPhiWriteNodes, LLVMExpressionNode[] unwindPhiWriteNodes);

    LLVMControlFlowNode createRetVoid(LLVMParserRuntime runtime);

    LLVMControlFlowNode createNonVoidRet(LLVMParserRuntime runtime, LLVMExpressionNode retValue, Type resolvedType);

    LLVMExpressionNode createFunctionArgNode(int argIndex, Type paramType);

    /**
     * Creates a function argument read node.
     *
     * @param argIndex the index from where to read the argument
     * @param clazz the expected class of the argument
     * @return an argument node
     */
    LLVMExpressionNode createFunctionArgNode(int argIndex, Class<? extends Node> clazz);

    LLVMExpressionNode createFunctionCall(LLVMParserRuntime runtime, LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, FunctionType type);

    LLVMControlFlowNode createFunctionInvoke(LLVMParserRuntime runtime, LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, FunctionType type,
                    FrameSlot returnValueSlot, FrameSlot exceptionValueSlot, int normalIndex,
                    int unwindIndex, LLVMExpressionNode[] normalPhiWriteNodes,
                    LLVMExpressionNode[] unwindPhiWriteNodes);

    LLVMExpressionNode createFrameRead(LLVMParserRuntime runtime, Type llvmType, FrameSlot frameSlot);

    LLVMExpressionNode createFrameWrite(LLVMParserRuntime runtime, Type llvmType, LLVMExpressionNode result, FrameSlot slot);

    LLVMExpressionNode createComparison(LLVMParserRuntime runtime, CompareOperator operator, Type type, LLVMExpressionNode lhs, LLVMExpressionNode rhs);

    LLVMExpressionNode createCast(LLVMParserRuntime runtime, LLVMExpressionNode fromNode, Type targetType, Type fromType, LLVMConversionType type);

    LLVMExpressionNode createArithmeticOperation(LLVMParserRuntime runtime, LLVMExpressionNode left, LLVMExpressionNode right, LLVMArithmeticInstructionType instr, Type llvmType);

    LLVMExpressionNode createExtractValue(LLVMParserRuntime runtime, Type type, LLVMExpressionNode targetAddress);

    LLVMExpressionNode createTypedElementPointer(LLVMParserRuntime runtime, LLVMExpressionNode aggregateAddress, LLVMExpressionNode index, int indexedTypeLength,
                    Type targetType);

    LLVMExpressionNode createSelect(LLVMParserRuntime runtime, Type type, LLVMExpressionNode condition, LLVMExpressionNode trueValue, LLVMExpressionNode falseValue);

    /**
     * Creates a zero vector initializer.
     *
     * @param nrElements the number of elements of the vector
     * @param llvmType the type of the vector
     *
     * @return the zero vector initializer
     */
    LLVMExpressionNode createZeroVectorInitializer(LLVMParserRuntime runtime, int nrElements, VectorType llvmType);

    /**
     * Creates a node representing an <code>unreachable</code> instruction.
     *
     * @return an unreachable node
     * @see <a href="http://llvm.org/docs/LangRef.html#unreachable-instruction">Unreachable in the
     *      LLVM Language Reference Manual</a>
     */
    LLVMControlFlowNode createUnreachableNode(LLVMParserRuntime runtime);

    LLVMControlFlowNode createIndirectBranch(LLVMParserRuntime runtime, LLVMExpressionNode value, int[] labelTargets, LLVMExpressionNode[] phiWrites);

    LLVMControlFlowNode createSwitch(LLVMParserRuntime runtime, LLVMExpressionNode cond, int defaultLabel, int[] otherLabels, LLVMExpressionNode[] cases,
                    PrimitiveType llvmType, LLVMExpressionNode[] phiWriteNodes);

    LLVMControlFlowNode createConditionalBranch(LLVMParserRuntime runtime, int trueIndex, int falseIndex, LLVMExpressionNode conditionNode, LLVMExpressionNode[] truePhiWriteNodes,
                    LLVMExpressionNode[] falsePhiWriteNodes);

    LLVMControlFlowNode createUnconditionalBranch(LLVMParserRuntime runtime, int unconditionalIndex, LLVMExpressionNode[] phiWrites);

    LLVMExpressionNode createArrayLiteral(LLVMParserRuntime runtime, List<LLVMExpressionNode> arrayValues, Type arrayType);

    /**
     * Creates an <code>alloca</code> node with a certain number of elements.
     *
     * @param numElementsType the type of <code>numElements</code>
     * @param byteSize the size of an element
     * @param alignment the alignment requirement
     * @param numElements how many elements to allocate, may be <code>null</code> if only one
     *            element should be allocated
     * @param type the type of an element, may be <code>null</code> if only one element should be
     *            allocated
     * @return a node that allocates the specified number of elements
     */
    LLVMExpressionNode createAlloc(LLVMParserRuntime runtime, Type type, int byteSize, int alignment, Type numElementsType, LLVMExpressionNode numElements);

    LLVMExpressionNode createInsertValue(LLVMParserRuntime runtime, LLVMExpressionNode resultAggregate, LLVMExpressionNode sourceAggregate, int size, int offset, LLVMExpressionNode valueToInsert,
                    Type llvmType);

    LLVMExpressionNode createZeroNode(LLVMParserRuntime runtime, LLVMExpressionNode addressNode, int size);

    /**
     * Creates the global root (e.g., the main function in C).
     *
     * @param mainCallTarget
     * @param args
     * @param mainTypes
     * @param sourceFile
     * @return the global root
     */
    RootNode createGlobalRootNode(LLVMParserRuntime runtime, RootCallTarget mainCallTarget, Object[] args, Source sourceFile, Type[] mainTypes);

    /**
     * Wraps the global root (e.g., the main function in C) to convert its result.
     *
     * @param mainCallTarget
     * @param returnType
     * @return the wrapped global root
     */
    RootNode createGlobalRootNodeWrapping(LLVMParserRuntime runtime, RootCallTarget mainCallTarget, Type returnType);

    /**
     * Creates a structure literal node.
     *
     * @param structureType type of the structure
     * @param packed whether the struct is packed (alignment of the struct is one byte and there is
     *            no padding between the elements)
     * @param types the types of the structure members
     * @param constants the structure members
     * @return the constructed structure literal
     */
    LLVMExpressionNode createStructureConstantNode(LLVMParserRuntime runtime, Type structureType, boolean packed, Type[] types, LLVMExpressionNode[] constants);

    /**
     * Creates a basic block node.
     *
     * @param statementNodes the statement nodes that do not change control flow
     * @param terminatorNode the terminator instruction node that changes control flow
     * @return the basic block node
     */
    LLVMExpressionNode createBasicBlockNode(LLVMParserRuntime runtime, LLVMExpressionNode[] statementNodes, LLVMControlFlowNode terminatorNode, int blockId, String blockName);

    /**
     * Creates a node that groups together several basic blocks in a function and returns the
     * function's result.
     *
     * @param returnSlot the frame slot for the return value
     * @param basicBlockNodes the basic blocks
     * @param indexToSlotNuller nuller node for nulling dead variables
     * @param slotNullerAfterNodes
     * @return the function block node
     */
    LLVMExpressionNode createFunctionBlockNode(LLVMParserRuntime runtime, FrameSlot returnSlot, List<? extends LLVMExpressionNode> basicBlockNodes, LLVMStackFrameNuller[][] indexToSlotNuller,
                    LLVMStackFrameNuller[][] slotNullerAfterNodes);

    /**
     * Creates the entry point for a function.
     *
     * @param functionBodyNode the body of a function that returns the functions result
     * @param beforeFunction function prologue nodes
     * @param afterFunction function epilogue nodes
     * @param frameDescriptor
     * @param functionHeader
     * @return a function root node
     */
    RootNode createFunctionStartNode(LLVMParserRuntime runtime, LLVMExpressionNode functionBodyNode, LLVMExpressionNode[] beforeFunction, LLVMExpressionNode[] afterFunction,
                    SourceSection sourceSection,
                    FrameDescriptor frameDescriptor,
                    FunctionDefinition functionHeader);

    /**
     * Returns the index of the first argument of the formal parameter list.
     *
     * @return the index
     */
    Optional<Integer> getArgStartIndex();

    /**
     * Creates an inline assembler instruction.
     *
     * @param asmExpression
     * @param asmFlags
     * @param args
     * @param retType the type the inline assembler instruction produces
     * @return an inline assembler node
     */
    LLVMExpressionNode createInlineAssemblerExpression(LLVMParserRuntime runtime, String asmExpression, String asmFlags, LLVMExpressionNode[] args, Type[] argTypes, Type retType);

    /**
     * Gets factories that provide substitutions for (standard library) functions. The substitutions
     * are installed when a function is called the first time.
     *
     * @return a map of function names that map to node factories
     */
    Map<String, NodeFactory<? extends LLVMExpressionNode>> getFunctionSubstitutionFactories();

    /**
     * Creates a function substitution root node from an already existing function substitution
     * node.
     *
     * @param intrinsicNode the existing function substitution node
     * @return the root node for the intrinsic
     */
    RootNode createFunctionSubstitutionRootNode(LLVMExpressionNode intrinsicNode);

    Object allocateGlobalVariable(LLVMParserRuntime runtime, GlobalVariable globalVariable);

    Object allocateGlobalConstant(LLVMParserRuntime runtime, GlobalConstant globalConstant);

    RootNode createStaticInitsRootNode(LLVMParserRuntime runtime, LLVMExpressionNode[] staticInits);

    /**
     * Returns whether function calls expect an implicit stack pointer argument.
     *
     * @return true if the parser should pass an implicit stack pointer argument for function calls
     */
    Optional<Boolean> hasStackPointerArgument(LLVMParserRuntime runtime);

    LLVMStackFrameNuller createFrameNuller(LLVMParserRuntime runtime, String identifier, Type type, FrameSlot slot);

    LLVMFunctionDescriptor createFunctionDescriptor(String name, FunctionType type, int functionIndex);

    LLVMFunctionDescriptor createAndRegisterFunctionDescriptor(LLVMParserRuntime runtime, String name, FunctionType functionType);

    LLVMExpressionNode createLandingPad(LLVMParserRuntime runtime, LLVMExpressionNode allocateLandingPadValue, FrameSlot exceptionSlot, boolean cleanup, long[] clauseKinds,
                    LLVMExpressionNode[] entries);

    LLVMControlFlowNode createResumeInstruction(LLVMParserRuntime runtime, FrameSlot exceptionSlot);

}
