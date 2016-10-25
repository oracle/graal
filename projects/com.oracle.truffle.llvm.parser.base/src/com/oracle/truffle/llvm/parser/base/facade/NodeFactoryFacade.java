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
package com.oracle.truffle.llvm.parser.base.facade;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.LLVMType;
import com.oracle.truffle.llvm.parser.base.util.LLVMParserRuntime;

import com.oracle.truffle.llvm.parser.base.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.base.model.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.base.model.types.FunctionType;
import com.oracle.truffle.llvm.parser.base.model.types.Type;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.base.LLVMStackFrameNuller;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMFloatComparisonType;
import com.oracle.truffle.llvm.parser.instructions.LLVMIntegerComparisonType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionType;
import com.oracle.truffle.llvm.types.LLVMFunction;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor.LLVMRuntimeType;

/**
 * This interface decouples the parser and the concrete implementation of the nodes by only making
 * {@link LLVMExpressionNode} and {@link LLVMNode} visible. The parser should not directly
 * instantiate a node, but instead use the factory facade.
 */
public interface NodeFactoryFacade {

    /**
     * Sets up the facade with its {@link LLVMParserRuntime} before its first use.
     *
     * @param runtime
     */
    // also update {@link NodeFactoryFacadeComposite} when changing or removing this name
    void setUpFacade(LLVMParserRuntime runtime);

    /**
     * Get Parser runtime (a temporary required method until we use the new Bitcode Type exclusive).
     *
     * @return runtime
     */
    @Deprecated
    LLVMParserRuntime getRuntime();

    LLVMExpressionNode createInsertElement(LLVMBaseType resultType, LLVMExpressionNode vector, com.intel.llvm.ireditor.lLVM_IR.Type vectorType, LLVMExpressionNode element, LLVMExpressionNode index);

    LLVMExpressionNode createExtractElement(LLVMBaseType resultType, LLVMExpressionNode vector, LLVMExpressionNode index);

    LLVMExpressionNode createShuffleVector(LLVMBaseType llvmType, LLVMExpressionNode target, LLVMExpressionNode vector1, LLVMExpressionNode vector2, LLVMExpressionNode mask);

    LLVMExpressionNode createLoad(Type resolvedResultType, LLVMExpressionNode loadTarget);

    LLVMNode createStore(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type);

    LLVMExpressionNode createLogicalOperation(LLVMExpressionNode left, LLVMExpressionNode right, LLVMLogicalInstructionType opCode, LLVMBaseType llvmType, LLVMExpressionNode target);

    LLVMExpressionNode createUndefinedValue(Type t);

    LLVMExpressionNode createLiteral(Object value, LLVMBaseType type);

    LLVMExpressionNode createSimpleConstantNoArray(String stringValue, LLVMBaseType instructionType, Type type);

    LLVMExpressionNode createVectorLiteralNode(List<LLVMExpressionNode> listValues, LLVMExpressionNode target, LLVMBaseType type);

    /**
     * Creates an intrinsic for a <code>@llvm.*</code> function.
     *
     * @param declaration the function declaration of the function from which the intrinsic is
     *            called
     * @param argNodes the arguments to the intrinsic function
     * @param numberOfExplicitArguments number of explicite arguments passed to function
     * @return the created intrinsic
     */
    LLVMNode createLLVMIntrinsic(FunctionType declaration, Object[] argNodes, int numberOfExplicitArguments);

    LLVMNode createTruffleIntrinsic(String functionName, LLVMExpressionNode[] argNodes);

    LLVMNode createRetVoid();

    LLVMNode createNonVoidRet(LLVMExpressionNode retValue, Type resolvedType);

    LLVMExpressionNode createFunctionArgNode(int argIndex, LLVMBaseType paramType);

    /**
     * Creates a function argument read node.
     *
     * @param argIndex the index from where to read the argument
     * @param clazz the expected class of the argument
     * @return an argument node
     */
    LLVMNode createFunctionArgNode(int argIndex, Class<? extends Node> clazz);

    LLVMNode createFunctionCall(LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, LLVMBaseType llvmType);

    LLVMExpressionNode createFrameRead(LLVMBaseType llvmType, FrameSlot frameSlot);

    LLVMNode createFrameWrite(LLVMBaseType llvmType, LLVMExpressionNode result, FrameSlot slot);

    FrameSlotKind getFrameSlotKind(Type type);

    LLVMExpressionNode createIntegerComparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMBaseType llvmType, LLVMIntegerComparisonType type);

    LLVMExpressionNode createFloatComparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMBaseType llvmType, LLVMFloatComparisonType type);

    LLVMExpressionNode createCast(LLVMExpressionNode fromNode, Type targetType, Type fromType, LLVMConversionType type);

    LLVMExpressionNode createArithmeticOperation(LLVMExpressionNode left, LLVMExpressionNode right, LLVMArithmeticInstructionType instr, LLVMBaseType llvmType, LLVMExpressionNode target);

    LLVMExpressionNode createExtractValue(LLVMBaseType type, LLVMExpressionNode targetAddress);

    /**
     * Creates an getelementptr (GEP) instruction.
     *
     * @param indexType the integer type of the index parameter.
     * @param aggregateAddress the address of the aggregate data structure
     * @param index
     * @param indexedTypeLength
     * @return the getelementptr node
     */
    LLVMExpressionNode createGetElementPtr(LLVMBaseType indexType, LLVMExpressionNode aggregateAddress, LLVMExpressionNode index, int indexedTypeLength);

    Class<?> getJavaClass(LLVMExpressionNode llvmExpressionNode);

    LLVMExpressionNode createSelect(LLVMBaseType llvmType, LLVMExpressionNode condition, LLVMExpressionNode trueValue, LLVMExpressionNode falseValue);

    /**
     * Creates a zero vector initializer.
     *
     * @param nrElements the number of elements of the vector
     * @param target the allocated result storage
     * @param llvmType the type of the vector
     *
     * @return the zero vector initializer
     */
    LLVMExpressionNode createZeroVectorInitializer(int nrElements, LLVMExpressionNode target, LLVMBaseType llvmType);

    /**
     * Creates a node representing an <code>unreachable</code> instruction.
     *
     * @return an unreachable node
     * @see <a href="http://llvm.org/docs/LangRef.html#unreachable-instruction">Unreachable in the
     *      LLVM Language Reference Manual</a>
     */
    LLVMNode createUnreachableNode();

    LLVMNode createIndirectBranch(LLVMExpressionNode value, int[] labelTargets, LLVMNode[] phiWrites);

    LLVMNode createSwitch(LLVMExpressionNode cond, int defaultLabel, int[] otherLabels, LLVMExpressionNode[] cases,
                    LLVMBaseType llvmType, LLVMNode[] phiWriteNodes);

    LLVMNode createConditionalBranch(int trueIndex, int falseIndex, LLVMExpressionNode conditionNode, LLVMNode[] truePhiWriteNodes, LLVMNode[] falsePhiWriteNodes);

    LLVMNode createUnconditionalBranch(int unconditionalIndex, LLVMNode[] phiWrites);

    LLVMExpressionNode createArrayLiteral(List<LLVMExpressionNode> arrayValues, Type arrayType);

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
    LLVMExpressionNode createAlloc(Type type, int byteSize, int alignment, LLVMBaseType numElementsType, LLVMExpressionNode numElements);

    LLVMExpressionNode createInsertValue(LLVMExpressionNode resultAggregate, LLVMExpressionNode sourceAggregate, int size, int offset, LLVMExpressionNode valueToInsert, LLVMBaseType llvmType);

    LLVMExpressionNode createZeroNode(LLVMExpressionNode addressNode, int size);

    LLVMExpressionNode createEmptyStructLiteralNode(LLVMExpressionNode alloca, int byteSize);

    /**
     * Creates the global root (e.g., the main function in C).
     *
     * @param mainCallTarget
     * @param args
     * @param mainTypes
     * @param sourceFile
     * @return the global root
     */
    RootNode createGlobalRootNode(RootCallTarget mainCallTarget, Object[] args, Source sourceFile, LLVMRuntimeType[] mainTypes);

    /**
     * Wraps the global root (e.g., the main function in C) to convert its result.
     *
     * @param mainCallTarget
     * @param returnType
     * @return the wrapped global root
     */
    RootNode createGlobalRootNodeWrapping(RootCallTarget mainCallTarget, LLVMRuntimeType returnType);

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
    LLVMExpressionNode createStructureConstantNode(Type structureType, boolean packed, Type[] types, LLVMExpressionNode[] constants);

    /**
     * Creates a basic block node.
     *
     * @param statementNodes the statement nodes that do not change control flow
     * @param terminatorNode the terminator instruction node that changes control flow
     * @return the basic block node
     */
    LLVMNode createBasicBlockNode(LLVMNode[] statementNodes, LLVMNode terminatorNode, int blockId, String blockName);

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
    LLVMExpressionNode createFunctionBlockNode(FrameSlot returnSlot, List<? extends LLVMNode> basicBlockNodes, LLVMStackFrameNuller[][] indexToSlotNuller,
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
    RootNode createFunctionStartNode(LLVMExpressionNode functionBodyNode, LLVMNode[] beforeFunction, LLVMNode[] afterFunction, SourceSection sourceSection, FrameDescriptor frameDescriptor,
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
    LLVMNode createInlineAssemblerExpression(String asmExpression, String asmFlags, LLVMExpressionNode[] args, LLVMBaseType retType);

    /**
     * Gets factories that provide substitutions for (standard library) functions. The substitutions
     * are installed when a function is called the first time.
     *
     * @return a map of function names that map to node factories
     */
    Map<String, NodeFactory<? extends LLVMNode>> getFunctionSubstitutionFactories();

    /**
     * Creates a function substitution root node from an already existing function substitution
     * node.
     *
     * @param intrinsicNode the existing function substitution node
     * @return the root node for the intrinsic
     */
    RootNode createFunctionSubstitutionRootNode(LLVMNode intrinsicNode);

    Object allocateGlobalVariable(GlobalVariable globalVariable);

    RootNode createStaticInitsRootNode(LLVMNode[] staticInits);

    /**
     * Returns whether function calls expect an implicit stack pointer argument.
     *
     * @return true if the parser should pass an implicit stack pointer argument for function calls
     */
    Optional<Boolean> hasStackPointerArgument();

    LLVMStackFrameNuller createFrameNuller(String identifier, LLVMType type, FrameSlot slot);

    LLVMFunction createFunctionDescriptor(String name, LLVMRuntimeType returnType, boolean varArgs, LLVMRuntimeType[] paramTypes, int functionIndex);

    LLVMFunction createAndRegisterFunctionDescriptor(String name, LLVMRuntimeType convertType, boolean varArgs, LLVMRuntimeType[] convertTypes);

}
