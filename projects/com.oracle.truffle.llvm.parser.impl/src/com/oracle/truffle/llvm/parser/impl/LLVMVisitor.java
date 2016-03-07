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
package com.oracle.truffle.llvm.parser.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;

import com.intel.llvm.ireditor.lLVM_IR.Alias;
import com.intel.llvm.ireditor.lLVM_IR.Aliasee;
import com.intel.llvm.ireditor.lLVM_IR.Argument;
import com.intel.llvm.ireditor.lLVM_IR.ArrayConstant;
import com.intel.llvm.ireditor.lLVM_IR.AttributeGroup;
import com.intel.llvm.ireditor.lLVM_IR.BasicBlock;
import com.intel.llvm.ireditor.lLVM_IR.BasicBlockRef;
import com.intel.llvm.ireditor.lLVM_IR.BinaryInstruction;
import com.intel.llvm.ireditor.lLVM_IR.BitwiseBinaryInstruction;
import com.intel.llvm.ireditor.lLVM_IR.BlockAddress;
import com.intel.llvm.ireditor.lLVM_IR.Callee;
import com.intel.llvm.ireditor.lLVM_IR.Constant;
import com.intel.llvm.ireditor.lLVM_IR.ConstantExpression_binary;
import com.intel.llvm.ireditor.lLVM_IR.ConstantExpression_compare;
import com.intel.llvm.ireditor.lLVM_IR.ConstantExpression_convert;
import com.intel.llvm.ireditor.lLVM_IR.ConstantExpression_getelementptr;
import com.intel.llvm.ireditor.lLVM_IR.ConstantList;
import com.intel.llvm.ireditor.lLVM_IR.ConversionInstruction;
import com.intel.llvm.ireditor.lLVM_IR.FunctionDecl;
import com.intel.llvm.ireditor.lLVM_IR.FunctionDef;
import com.intel.llvm.ireditor.lLVM_IR.FunctionHeader;
import com.intel.llvm.ireditor.lLVM_IR.GlobalValueDef;
import com.intel.llvm.ireditor.lLVM_IR.GlobalValueRef;
import com.intel.llvm.ireditor.lLVM_IR.GlobalVariable;
import com.intel.llvm.ireditor.lLVM_IR.InlineAsm;
import com.intel.llvm.ireditor.lLVM_IR.InlineAssembler;
import com.intel.llvm.ireditor.lLVM_IR.Instruction;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_alloca;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_atomicrmw;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_br;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_call_nonVoid;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_cmpxchg;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_extractelement;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_extractvalue;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_fcmp;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_fence;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_getelementptr;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_icmp;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_indirectbr;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_insertelement;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_insertvalue;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_invoke_nonVoid;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_landingpad;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_load;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_ret;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_select;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_shufflevector;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_store;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_switch;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_unreachable;
import com.intel.llvm.ireditor.lLVM_IR.LocalValue;
import com.intel.llvm.ireditor.lLVM_IR.LocalValueRef;
import com.intel.llvm.ireditor.lLVM_IR.MetadataNode;
import com.intel.llvm.ireditor.lLVM_IR.MetadataNodeElement;
import com.intel.llvm.ireditor.lLVM_IR.MiddleInstruction;
import com.intel.llvm.ireditor.lLVM_IR.Model;
import com.intel.llvm.ireditor.lLVM_IR.NamedMetadata;
import com.intel.llvm.ireditor.lLVM_IR.NamedMiddleInstruction;
import com.intel.llvm.ireditor.lLVM_IR.NamedTerminatorInstruction;
import com.intel.llvm.ireditor.lLVM_IR.Parameter;
import com.intel.llvm.ireditor.lLVM_IR.Parameters;
import com.intel.llvm.ireditor.lLVM_IR.SimpleConstant;
import com.intel.llvm.ireditor.lLVM_IR.StartingInstruction;
import com.intel.llvm.ireditor.lLVM_IR.StructureConstant;
import com.intel.llvm.ireditor.lLVM_IR.TargetInfo;
import com.intel.llvm.ireditor.lLVM_IR.TerminatorInstruction;
import com.intel.llvm.ireditor.lLVM_IR.Type;
import com.intel.llvm.ireditor.lLVM_IR.TypeDef;
import com.intel.llvm.ireditor.lLVM_IR.TypedConstant;
import com.intel.llvm.ireditor.lLVM_IR.TypedValue;
import com.intel.llvm.ireditor.lLVM_IR.Undef;
import com.intel.llvm.ireditor.lLVM_IR.ValueRef;
import com.intel.llvm.ireditor.lLVM_IR.VectorConstant;
import com.intel.llvm.ireditor.lLVM_IR.ZeroInitializer;
import com.intel.llvm.ireditor.lLVM_IR.impl.Instruction_brImpl;
import com.intel.llvm.ireditor.types.ResolvedArrayType;
import com.intel.llvm.ireditor.types.ResolvedType;
import com.intel.llvm.ireditor.types.ResolvedVectorType;
import com.intel.llvm.ireditor.types.TypeResolver;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.nativeint.NativeLookup;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMContext;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMLanguage;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMMetadataNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMStatementNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMStructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI32VectorNode;
import com.oracle.truffle.llvm.nodes.impl.exception.LLVMInvokeNode;
import com.oracle.truffle.llvm.nodes.impl.exception.LLVMLandingPadNode.LLVMAddressLandingPadNode;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallNode;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMFunctionBodyNode;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMFunctionStartNode;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMMemCopyFactory.LLVMMemI32CopyFactory;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMBlockNode;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMBlockNode.LLVMBlockControlFlowNode;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMBlockNode.LLVMBlockNoControlFlowNode;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMPhiNode;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMStackFrameNuller;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMStackFrameNuller.LLVMBooleanNuller;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMStackFrameNuller.LLVMByteNuller;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMStackFrameNuller.LLVMDoubleNull;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMStackFrameNuller.LLVMFloatNuller;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMStackFrameNuller.LLVMIntNuller;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMStackFrameNuller.LLVMLongNuller;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMStackFrameNuller.LLVMObjectNuller;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMWrappedStatementNode;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.NodeFactoryFacade;
import com.oracle.truffle.llvm.parser.impl.LLVMPhiVisitor.Phi;
import com.oracle.truffle.llvm.parser.impl.layout.DataLayoutConverter;
import com.oracle.truffle.llvm.parser.impl.layout.DataLayoutConverter.DataSpecConverter;
import com.oracle.truffle.llvm.parser.impl.layout.DataLayoutParser;
import com.oracle.truffle.llvm.parser.impl.layout.DataLayoutParser.DataTypeSpecification;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMFloatComparisonType;
import com.oracle.truffle.llvm.parser.instructions.LLVMIntegerComparisonType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionType;
import com.oracle.truffle.llvm.parser.util.LLVMTypeHelper;
import com.oracle.truffle.llvm.runtime.LLVMOptimizationConfiguration;
import com.oracle.truffle.llvm.runtime.LLVMOptions;
import com.oracle.truffle.llvm.runtime.LLVMParserException;
import com.oracle.truffle.llvm.runtime.LLVMParserException.ParserErrorCause;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunction;
import com.oracle.truffle.llvm.types.LLVMFunction.LLVMRuntimeType;
import com.oracle.truffle.llvm.types.LLVMMetadata;
import com.oracle.truffle.llvm.types.memory.LLVMHeap;
import com.oracle.truffle.llvm.types.memory.LLVMMemory;
import com.oracle.truffle.llvm.types.memory.LLVMStack;

/**
 * This class traverses the LLVM IR AST as provided by the <code>com.intel.llvm.ireditor</code>
 * project and returns an executable AST.
 */
public class LLVMVisitor implements LLVMParserRuntime {

    private static final int HEX_BASE = 16;

    private static final String FUNCTION_RETURN_VALUE_FRAME_SLOT_ID = "<function return value>";
    private static final String STACK_ADDRESS_FRAME_SLOT_ID = "<stack pointer>";

    private static final TypeResolver typeResolver = new TypeResolver();
    private FrameDescriptor frameDescriptor;
    private LLVMContext currentContext;
    private List<LLVMNode> functionEpilogue;
    private Map<FunctionHeader, Map<String, Integer>> functionToLabelMapping;
    private final Map<LLVMFunction, RootCallTarget> functionCallTargets = new HashMap<>();
    private Map<String, Integer> labelList;
    private FrameSlot retSlot;
    private FrameSlot stackPointerSlot;
    private FunctionDef containingFunctionDef;
    private NodeFactoryFacade factoryFacade;
    private final LLVMOptimizationConfiguration optimizationConfiguration;

    private Map<BasicBlock, List<Phi>> phiRefs;

    private NativeLookup nativeLookup;

    public LLVMVisitor(LLVMContext context, LLVMOptimizationConfiguration optimizationConfiguration) {
        currentContext = context;
        this.optimizationConfiguration = optimizationConfiguration;
        LLVMTypeHelper.setParserRuntime(this);
    }

    public RootCallTarget getMain(Model model, NodeFactoryFacade facade) {
        visit(model, facade);
        currentContext.getFunctionRegistry();
        LLVMFunction mainFunction = LLVMFunction.createFromName("@main");
        RootCallTarget mainCallTarget = currentContext.getFunctionRegistry().lookup(mainFunction);
        int argParamCount = mainFunction.getLlvmParamTypes().length;
        RootNode globalFunction;
        LLVMNode[] staticInits = globalNodes.toArray(new LLVMNode[globalNodes.size()]);
        int argsCount = currentContext.getMainArguments().length + 1;
        if (argParamCount == 0) {
            globalFunction = factoryFacade.createGlobalRootNode(staticInits, mainCallTarget, deallocations);
        } else {
            if (argParamCount == 1) {
                globalFunction = factoryFacade.createGlobalRootNode(staticInits, mainCallTarget, deallocations, argsCount);
            } else {
                Object[] args = new Object[argsCount];
                args[0] = currentContext.getSourceFile();
                System.arraycopy(currentContext.getMainArguments(), 0, args, 1, currentContext.getMainArguments().length);
                LLVMParserAsserts.assertNoNullElement(args);
                LLVMAddress allocatedArgsStartAddress = getArgsAsStringArray(args);
                // Checkstyle: stop magic number check
                if (argParamCount == 2) {
                    globalFunction = factoryFacade.createGlobalRootNode(staticInits, mainCallTarget, deallocations, argsCount, allocatedArgsStartAddress);
                } else if (argParamCount == 3) {
                    LLVMAddress posixEnvPointer = LLVMAddress.NULL_POINTER;
                    globalFunction = factoryFacade.createGlobalRootNode(staticInits, mainCallTarget, deallocations, argsCount, allocatedArgsStartAddress, posixEnvPointer);
                } else {
                    throw new AssertionError(argParamCount);
                }
                // Checkstyle: resume magic number check
            }
        }
        RootCallTarget wrappedCallTarget = Truffle.getRuntime().createCallTarget(wrapMainFunction(Truffle.getRuntime().createCallTarget(globalFunction)));
        return wrappedCallTarget;
    }

    private RootNode wrapMainFunction(RootCallTarget mainCallTarget) {
        LLVMFunction mainSignature = LLVMFunction.createFromName("@main");
        LLVMRuntimeType returnType = mainSignature.getLlvmReturnType();
        return factoryFacade.createGlobalRootNodeWrapping(mainCallTarget, returnType);
    }

    private static LLVMAddress getArgsAsStringArray(Object... args) {
        LLVMParserAsserts.assertNoNullElement(args);
        String[] stringArgs = getStringArgs(args);
        int argsMemory = stringArgs.length * LLVMAddress.WORD_LENGTH_BIT / Byte.SIZE;
        LLVMAddress allocatedArgsStartAddress = LLVMHeap.allocateMemory(argsMemory);
        LLVMAddress allocatedArgs = allocatedArgsStartAddress;
        for (int i = 0; i < stringArgs.length; i++) {
            String string = stringArgs[i];
            LLVMAddress allocatedCString = LLVMHeap.allocateCString(string);
            LLVMMemory.putAddress(allocatedArgs, allocatedCString);
            allocatedArgs = allocatedArgs.increment(LLVMAddress.WORD_LENGTH_BIT / Byte.SIZE);
        }
        return allocatedArgsStartAddress;
    }

    private static String[] getStringArgs(Object... args) {
        LLVMParserAsserts.assertNoNullElement(args);
        String[] stringArgs = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            stringArgs[i] = args[i].toString();
        }
        LLVMParserAsserts.assertNoNullElement(stringArgs);
        return stringArgs;
    }

    public List<LLVMFunction> visit(Model model, NodeFactoryFacade facade) {
        this.factoryFacade = facade;
        List<EObject> objects = model.eContents();
        List<LLVMFunction> functions = new ArrayList<>();
        globalNodes = new ArrayList<>();
        List<GlobalVariable> staticVars = new ArrayList<>();
        functionToLabelMapping = new HashMap<>();
        setTargetInfo(objects);
        allocateGlobals(this, objects);
        this.nativeLookup = new NativeLookup(facade);
        for (EObject object : objects) {
            if (object instanceof FunctionDef) {
                phiRefs = LLVMPhiVisitor.visit((FunctionDef) object);
                LLVMFunction function = visitFunction((FunctionDef) object);
                Map<String, Integer> functionLabels = labelList;
                functionToLabelMapping.put(((FunctionDef) object).getHeader(), functionLabels);
                functions.add(function);
            } else if (object instanceof TargetInfo) {
                // already parsed
            } else if (object instanceof NamedMetadata) {
                if (LLVMOptions.debugEnabled()) {
                    System.err.println(object + " not supported!");
                }
            } else if (object instanceof FunctionDecl) {
                // not needed for the moment
            } else if (object instanceof AttributeGroup) {
                // do nothing, visit later when the attribute group is referenced
            } else if (object instanceof GlobalVariable) {
                // global vars can depend on block addresses in functions, so handle them after
                // everything is initialized
                staticVars.add((GlobalVariable) object);
            } else if (object instanceof TypeDef) {
                // do nothing
            } else if (object instanceof Alias) {
                // do nothing, visit later when alias is referenced
            } else if (object instanceof InlineAsm) {
                if (LLVMOptions.debugEnabled()) {
                    System.err.println("ignoring module level inline assembler!");
                }
            } else {
                throw new AssertionError(object);
            }
        }
        currentContext.getFunctionRegistry().register(functionCallTargets);
        List<LLVMNode> globalVarNodes = addGlobalVars(this, staticVars);
        globalNodes.addAll(globalVarNodes);
        deallocations = globalDeallocations.toArray(new LLVMAddress[globalDeallocations.size()]);
        return functions;
    }

    private static void setTargetInfo(List<EObject> objects) {
        for (EObject object : objects) {
            if (object instanceof TargetInfo) {
                LLVMVisitor.visitTargetInfo((TargetInfo) object);
            }
        }
    }

    private static void allocateGlobals(LLVMVisitor visitor, List<EObject> objects) {
        for (EObject object : objects) {
            if (object instanceof GlobalVariable) {
                visitor.findOrAllocateGlobal((GlobalVariable) object);
            }
        }
    }

    private List<LLVMNode> addGlobalVars(LLVMVisitor visitor, List<GlobalVariable> globalVariables) {
        List<LLVMNode> globalVarNodes = new ArrayList<>();
        for (GlobalVariable globalVar : globalVariables) {
            LLVMNode globalVarWrite = visitor.visitGlobalVariable(globalVar);
            if (globalVarWrite != null) {
                globalVarNodes.add(globalVarWrite);
            }
            if (globalVar.getName().equals("@llvm.global_ctors")) {
                ResolvedArrayType type = (ResolvedArrayType) typeResolver.resolve(globalVar.getType());
                int size = type.getSize();
                LLVMAddress allocGlobalVariable = findOrAllocateGlobal(globalVar);
                ResolvedType structType = type.getContainedType(0);
                int structSize = LLVMTypeHelper.getByteSize(structType);
                for (int i = 0; i < size; i++) {
                    LLVMExpressionNode globalVarAddress = factoryFacade.createLiteral(allocGlobalVariable, LLVMBaseType.ADDRESS);
                    LLVMExpressionNode iNode = factoryFacade.createLiteral(i, LLVMBaseType.I32);
                    LLVMAddressNode structPointer = (LLVMAddressNode) factoryFacade.createGetElementPtr(LLVMBaseType.I32, globalVarAddress, iNode, structSize);
                    LLVMAddressNode loadedStruct = (LLVMAddressNode) factoryFacade.createLoad(structType, structPointer);
                    ResolvedType functionType = structType.getContainedType(1);
                    int indexedTypeLength = LLVMTypeHelper.getAlignmentByte(functionType);
                    LLVMExpressionNode oneLiteralNode = factoryFacade.createLiteral(1, LLVMBaseType.I32);
                    LLVMAddressNode functionLoadTarget = (LLVMAddressNode) factoryFacade.createGetElementPtr(LLVMBaseType.I32, loadedStruct, oneLiteralNode, indexedTypeLength);
                    LLVMFunctionNode loadedFunction = (LLVMFunctionNode) factoryFacade.createLoad(functionType, functionLoadTarget);
                    LLVMNode functionCall = factoryFacade.createFunctionCall(loadedFunction, new LLVMExpressionNode[0], LLVMBaseType.VOID);
                    globalVarNodes.add(functionCall);
                }
            } else if (globalVar.getName().equals("llvm.global_dtors")) {
                throw new AssertionError("destructors not yet supported!");
            }
        }
        return globalVarNodes;
    }

    private static void visitTargetInfo(TargetInfo object) {
        String infoType = object.getInfoType();
        switch (infoType) {
            case "triple":
                // ignore
                break;
            case "datalayout":
                List<DataTypeSpecification> dataLayout = DataLayoutParser.parseDataLayout(object.getLayout());
                layoutConverter = DataLayoutConverter.getConverter(dataLayout);
                break;
            default:
                throw new AssertionError(infoType + " not supported!");
        }
    }

    class GlobalVarResult {
        GlobalVarResult(LLVMNode alloc, LLVMNode initValue) {
            this.alloc = alloc;
            this.initValue = initValue;
        }

        LLVMNode alloc;
        LLVMNode initValue;
    }

    private LLVMNode visitGlobalVariable(GlobalVariable globalVariable) {
        isGlobalScope = true;
        Constant initialValue = globalVariable.getInitialValue();
        if (initialValue == null) {
            return null;
        } else {
            LLVMExpressionNode constant = visitConstant(globalVariable.getType(), initialValue);
            if (constant != null) {
                LLVMBaseType llvmType = getLLVMType(globalVariable.getType());
                ResolvedType resolvedType = resolve(globalVariable.getType());
                int byteSize = LLVMTypeHelper.getByteSize(resolvedType);
                LLVMAddress allocGlobalVariable = findOrAllocateGlobal(globalVariable);
                if (byteSize == 0) {
                    return null;
                } else {
                    LLVMExpressionNode globalVarAddress = factoryFacade.createLiteral(allocGlobalVariable, LLVMBaseType.ADDRESS);
                    LLVMNode storeNode;
                    if (llvmType == LLVMBaseType.ARRAY || llvmType == LLVMBaseType.STRUCT) {
                        LLVMExpressionNode isVolatileNode = factoryFacade.createLiteral(false, LLVMBaseType.I1);
                        LLVMExpressionNode alignNode = factoryFacade.createLiteral(0, LLVMBaseType.I32);
                        LLVMExpressionNode lengthNode = factoryFacade.createLiteral(byteSize, LLVMBaseType.I32);
                        storeNode = LLVMMemI32CopyFactory.create((LLVMAddressNode) globalVarAddress, (LLVMAddressNode) constant, (LLVMI32Node) lengthNode, (LLVMI32Node) alignNode,
                                        (LLVMI1Node) isVolatileNode);
                    } else {
                        storeNode = getStoreNode((LLVMAddressNode) globalVarAddress, constant, globalVariable.getType());
                    }
                    return storeNode;
                }
            } else {
                // can be null, e.g., for zero-size array initializer
                return null;
            }
        }
    }

    private final Map<GlobalVariable, LLVMAddress> globalVars = new HashMap<>();
    private final List<LLVMAddress> globalDeallocations = new ArrayList<>();
    private boolean isGlobalScope;

    private LLVMAddress findOrAllocateGlobal(GlobalVariable globalVariable) {
        if (globalVars.containsKey(globalVariable)) {
            return globalVars.get(globalVariable);
        } else {
            ResolvedType resolvedType = resolve(globalVariable.getType());
            int byteSize = LLVMTypeHelper.getByteSize(resolvedType);
            LLVMAddress allocation = LLVMHeap.allocateMemory(byteSize);
            globalVars.put(globalVariable, allocation);
            globalDeallocations.add(allocation);
            return allocation;
        }
    }

    private LLVMExpressionNode visitArrayConstantStore(ArrayConstant constant) {
        ConstantList constList = constant.getList();
        List<LLVMExpressionNode> arrayValues = visitConstantList(constList);
        return getArrayLiteral(arrayValues, resolve(constant));
    }

    private LLVMExpressionNode getArrayLiteral(List<LLVMExpressionNode> arrayValues, ResolvedType arrayType) {
        return factoryFacade.createArrayLiteral(arrayValues, arrayType);
    }

    private LLVMFunction visitFunction(FunctionDef def) {
        this.containingFunctionDef = def;
        isGlobalScope = false;
        frameDescriptor = new FrameDescriptor();
        isGlobalScope = false;
        retSlot = frameDescriptor.addFrameSlot(FUNCTION_RETURN_VALUE_FRAME_SLOT_ID);
        stackPointerSlot = frameDescriptor.addFrameSlot(STACK_ADDRESS_FRAME_SLOT_ID, FrameSlotKind.Object);
        functionEpilogue = new ArrayList<>();
        LLVMAttributeVisitor.visitFunctionHeader(def.getHeader());
        labelList = getBlockLabelIndexMapping(def);
        List<LLVMNode> formalParameters = getFormalParametersInit(def);
        LLVMBlockNode block = getFunctionBlockStatements(def);
        String functionName = def.getHeader().getName();
        LLVMFunctionBodyNode functionBodyNode = new LLVMFunctionBodyNode(block, retSlot);
        LLVMNode[] beforeFunction = formalParameters.toArray(new LLVMNode[formalParameters.size()]);
        LLVMNode[] afterFunction = functionEpilogue.toArray(new LLVMNode[functionEpilogue.size()]);
        LLVMContext context = LLVMLanguage.INSTANCE.findContext0(LLVMLanguage.INSTANCE.createFindContextNode0());
        LLVMFunctionStartNode rootNode = new LLVMFunctionStartNode(functionBodyNode, stackPointerSlot, beforeFunction, afterFunction, frameDescriptor, functionName, context);
        if (LLVMOptions.printFunctionASTs()) {
            NodeUtil.printTree(System.out, rootNode);
        }
        LLVMFunction function = createLLVMFunctionFromHeader(def.getHeader());
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        functionCallTargets.put(function, callTarget);
        return function;
    }

    private LLVMBlockNode getFunctionBlockStatements(FunctionDef def) {
        List<LLVMNode> allFunctionNodes = new ArrayList<>();
        int currentIndex = 0;
        int[] basicBlockIndices = new int[def.getBasicBlocks().size()];
        int i = 0;
        for (BasicBlock basicBlock : def.getBasicBlocks()) {
            List<LLVMNode> statementNodes = visitBasicBlock(basicBlock);
            currentIndex += statementNodes.size();
            basicBlockIndices[i++] = currentIndex - 1;
            allFunctionNodes.addAll(statementNodes);
        }
        LLVMStackFrameNuller[][] indexToSlotNuller = new LLVMStackFrameNuller[currentIndex][];
        i = 0;
        Map<BasicBlock, FrameSlot[]> deadSlotsAfterBlock;
        if (LLVMOptions.lifeTimeAnalysisEnabled()) {
            deadSlotsAfterBlock = LLVMLifeTimeAnalysisVisitor.visit(def, frameDescriptor);
        } else {
            deadSlotsAfterBlock = new HashMap<>();
        }
        for (BasicBlock basicBlock : def.getBasicBlocks()) {
            FrameSlot[] deadSlots = deadSlotsAfterBlock.get(basicBlock);
            LLVMParserAsserts.assertNoNullElement(deadSlots);
            indexToSlotNuller[basicBlockIndices[i++]] = getSlotNullerNode(deadSlots);
        }
        int size = allFunctionNodes.size();
        boolean lastStatementIsControl = allFunctionNodes.get(size - 1) instanceof LLVMStatementNode;
        if (!lastStatementIsControl) {
            throw new IllegalStateException("last statement in basic block should be control statement");
        }
        boolean containsControlFlow = size != 1 && allFunctionNodes.stream().limit(allFunctionNodes.size() - 1).anyMatch(node -> node instanceof LLVMStatementNode);
        if (containsControlFlow) {
            LLVMStatementNode[] statements = new LLVMStatementNode[allFunctionNodes.size()];
            for (i = 0; i < allFunctionNodes.size(); i++) {
                LLVMNode currentNode = allFunctionNodes.get(i);
                if (currentNode instanceof LLVMStatementNode) {
                    statements[i] = (LLVMStatementNode) currentNode;
                } else {
                    final int successorIndex = i + 1;
                    statements[i] = new LLVMWrappedStatementNode(currentNode, successorIndex);
                }
            }
            LLVMParserAsserts.assertNoNullElement(statements);
            return new LLVMBlockControlFlowNode(statements, indexToSlotNuller);
        } else {
            return new LLVMBlockNoControlFlowNode(allFunctionNodes.toArray(new LLVMNode[allFunctionNodes.size()]));
        }
    }

    private static LLVMStackFrameNuller[] getSlotNullerNode(FrameSlot[] deadSlots) {
        if (deadSlots == null) {
            return new LLVMStackFrameNuller[0];
        }
        LLVMStackFrameNuller[] nullers = new LLVMStackFrameNuller[deadSlots.length];
        int i = 0;
        for (FrameSlot slot : deadSlots) {
            nullers[i++] = getNullerNode(slot);
        }
        LLVMParserAsserts.assertNoNullElement(nullers);
        return nullers;
    }

    private static LLVMStackFrameNuller getNullerNode(FrameSlot slot) {
        switch (slot.getKind()) {
            case Boolean:
                return new LLVMBooleanNuller(slot);
            case Byte:
                return new LLVMByteNuller(slot);
            case Int:
                return new LLVMIntNuller(slot);
            case Long:
                return new LLVMLongNuller(slot);
            case Float:
                return new LLVMFloatNuller(slot);
            case Double:
                return new LLVMDoubleNull(slot);
            case Object:
                return new LLVMObjectNuller(slot);
            case Illegal:
                throw new AssertionError("illegal");
            default:
                throw new AssertionError();
        }
    }

    private List<LLVMNode> getFormalParametersInit(FunctionDef def) {
        // add formal parameters
        List<LLVMNode> formalParamInits = new ArrayList<>();
        FunctionHeader functionHeader = def.getHeader();
        EList<Parameter> pars = functionHeader.getParameters().getParameters();
        int argIndex = LLVMCallNode.ARG_START_INDEX;
        if (resolve(functionHeader.getRettype()).isStruct()) {
            LLVMExpressionNode functionRetParNode = factoryFacade.createFunctionArgNode(argIndex++, LLVMBaseType.STRUCT);
            LLVMNode retValue = createAssignment((String) retSlot.getIdentifier(), functionRetParNode, functionHeader.getRettype());
            formalParamInits.add(retValue);
        }
        for (Parameter par : pars) {
            LLVMExpressionNode parNode;
            LLVMBaseType paramType = getLLVMType(par.getType());
            parNode = factoryFacade.createFunctionArgNode(argIndex, paramType);
            argIndex++;
            formalParamInits.add(createAssignment(par.getName(), parNode, par.getType().getType()));
        }
        return formalParamInits;
    }

    private LLVMNode createAssignment(String name, LLVMExpressionNode writeValue, Type type) {
        FrameSlot frameSlot = findOrAddFrameSlot(name, type);
        return getWriteNode(writeValue, frameSlot, type);
    }

    private Map<String, Integer> getBlockLabelIndexMapping(FunctionDef functionDef) {
        int labelIndex = 0;
        HashMap<String, Integer> labels = new HashMap<>();
        for (BasicBlock basicBlock : functionDef.getBasicBlocks()) {
            labels.put(basicBlock.getName(), labelIndex);
            int nrInstructions = basicBlock.getInstructions().size();
            int nrAdditionalPhiAssignments = phiRefs.get(basicBlock).size();
            labelIndex += nrInstructions + nrAdditionalPhiAssignments;
        }
        return labels;
    }

    public static DataSpecConverter layoutConverter;

    private LLVMAddress[] deallocations;

    private List<LLVMNode> globalNodes;

    private List<LLVMNode> visitBasicBlock(BasicBlock basicBlock) {
        List<LLVMNode> statements = new ArrayList<>(basicBlock.getInstructions().size());
        for (Instruction instr : basicBlock.getInstructions()) {
            List<LLVMNode> instrInstructions = visitInstruction(basicBlock, instr);
            for (LLVMNode instruction : instrInstructions) {
                statements.add(instruction);
            }
        }
        return statements;
    }

    private List<LLVMNode> visitInstruction(BasicBlock basicBlock, Instruction instr) {
        if (instr instanceof TerminatorInstruction) {
            List<LLVMNode> statements = new ArrayList<>();
            if (!phiRefs.get(basicBlock).isEmpty()) {
                List<Phi> phiValues = phiRefs.get(basicBlock);
                if (isConditionalBranch(instr)) {
                    Instruction_brImpl conditionalBranch = (Instruction_brImpl) ((TerminatorInstruction) instr).getInstruction();
                    for (Phi valueRef : phiValues) {
                        FrameSlot phiSlot = frameDescriptor.findOrAddFrameSlot(valueRef.getAssignTo());
                        LLVMExpressionNode visitValueRef = visitValueRef(valueRef.getValueRef(), valueRef.getType());
                        assert conditionalBranch.getTrue().getRef() == valueRef.getStartingInstr().eContainer() || conditionalBranch.getFalse().getRef() == valueRef.getStartingInstr().eContainer();
                        boolean isTrueCondition = conditionalBranch.getTrue().getRef() == valueRef.getStartingInstr().eContainer();
                        LLVMI1Node conditionNode = (LLVMI1Node) visitValueRef(conditionalBranch.getCondition().getRef(), conditionalBranch.getCondition().getType());
                        LLVMNode phiWriteNode = getWriteNode(visitValueRef, phiSlot, valueRef.getType());
                        LLVMNode conditionalPhiWriteNode;
                        if (isTrueCondition) {
                            conditionalPhiWriteNode = factoryFacade.createConditionalPhiWriteNode(conditionNode, phiWriteNode);
                        } else {
                            LLVMExpressionNode rightNode = factoryFacade.createLiteral(true, LLVMBaseType.I1);
                            LLVMExpressionNode create = factoryFacade.createLogicalOperation(conditionNode, rightNode, LLVMLogicalInstructionType.XOR, LLVMBaseType.I1, null);
                            conditionalPhiWriteNode = factoryFacade.createConditionalPhiWriteNode(create, phiWriteNode);
                        }
                        statements.add(conditionalPhiWriteNode);
                    }
                } else {
                    for (Phi valueRef : phiValues) {
                        FrameSlot phiSlot = frameDescriptor.findOrAddFrameSlot(valueRef.getAssignTo());
                        LLVMExpressionNode visitValueRef = visitValueRef(valueRef.getValueRef(), valueRef.getType());
                        LLVMNode phiWriteNode = getWriteNode(visitValueRef, phiSlot, valueRef.getType());
                        statements.add(phiWriteNode);
                    }
                }
            }
            LLVMNode visitTerminatorInstruction = visitTerminatorInstruction((TerminatorInstruction) instr);
            statements.add(visitTerminatorInstruction);
            return statements;
        } else if (instr instanceof MiddleInstruction) {
            return visitMiddleInstruction((MiddleInstruction) instr);
        } else if (instr instanceof StartingInstruction) {
            return Arrays.asList(visitStartingInstruction((StartingInstruction) instr));
        } else {
            throw new AssertionError(instr);
        }
    }

    private static boolean isConditionalBranch(Instruction instr) {
        return ((TerminatorInstruction) instr).getInstruction() instanceof Instruction_brImpl && ((Instruction_brImpl) ((TerminatorInstruction) instr).getInstruction()).getUnconditional() == null;
    }

    private static LLVMNode visitStartingInstruction(@SuppressWarnings("unused") StartingInstruction instr) {
        return new LLVMPhiNode();
    }

    private List<LLVMNode> visitMiddleInstruction(MiddleInstruction middleInstr) {
        EObject instr = middleInstr.getInstruction();
        LLVMNode middleInstruction;
        if (instr instanceof NamedMiddleInstruction) {
            return visitNamedMiddleInstruction((NamedMiddleInstruction) instr);
        } else if (instr instanceof Instruction_store) {
            middleInstruction = visitStoreInstruction((Instruction_store) instr);
        } else if (instr instanceof Instruction_call_nonVoid) {
            middleInstruction = visitFunctionCall((Instruction_call_nonVoid) instr);
        } else {
            throw new AssertionError(instr);
        }
        return Arrays.asList(middleInstruction);
    }

    private LLVMNode visitFunctionCall(Callee callee, EList<Argument> args, ResolvedType retType) throws AssertionError {
        if (callee instanceof InlineAssembler) {
            throw new LLVMUnsupportedException(UnsupportedReason.INLINE_ASSEMBLER);
        }
        List<LLVMExpressionNode> argNodes = new ArrayList<>(args.size());
        if (retType.isStruct()) {
            argNodes.add(allocateFunctionLifetime(retType));
        }
        for (Argument arg : args) {
            argNodes.add(visitValueRef(arg.getRef(), arg.getType().getType()));
        }
        LLVMExpressionNode[] finalArgs = argNodes.toArray(new LLVMExpressionNode[argNodes.size()]);
        if (callee instanceof GlobalValueRef && ((GlobalValueRef) callee).getConstant().getRef() instanceof FunctionHeader) {
            FunctionHeader functionHeader = (FunctionHeader) ((GlobalValueRef) callee).getConstant().getRef();
            String functionName = functionHeader.getName();
            if (functionName.startsWith("@llvm.")) {
                return factoryFacade.createLLVMIntrinsic(functionName, finalArgs, containingFunctionDef);
            }
        }
        LLVMExpressionNode func = visitValueRef((ValueRef) callee, null);
        LLVMFunctionNode functionNode = (LLVMFunctionNode) func;
        return factoryFacade.createFunctionCall(functionNode, finalArgs, LLVMTypeHelper.getLLVMType(retType));
    }

    private LLVMNode visitStoreInstruction(Instruction_store instr) {
        TypedValue pointer = instr.getPointer();
        TypedValue index = instr.getValue();
        LLVMExpressionNode pointerNode = visitValueRef(pointer.getRef(), pointer.getType());
        LLVMExpressionNode valueNode = visitValueRef(index.getRef(), index.getType());
        return getStoreNode((LLVMAddressNode) pointerNode, valueNode, index.getType());
    }

    private LLVMNode getStoreNode(LLVMAddressNode pointerNode, LLVMExpressionNode valueNode, Type type) {
        return factoryFacade.createStore(pointerNode, valueNode, resolve(type));
    }

    private LLVMBaseType getLLVMType(EObject object) {
        if (object == null) {
            throw new AssertionError();
        }
        ResolvedType llvmType = resolve(object);
        return LLVMTypeHelper.getLLVMType(llvmType);
    }

    private List<LLVMNode> visitNamedMiddleInstruction(NamedMiddleInstruction namedMiddleInstr) {
        EObject instr = namedMiddleInstr.getInstruction();
        String name = namedMiddleInstr.getName();
        LLVMExpressionNode result;
        FrameSlot frameSlot = frameDescriptor.findOrAddFrameSlot(name);
        if (instr instanceof BinaryInstruction) {
            BinaryInstruction binaryInstruction = (BinaryInstruction) instr;
            result = visitBinaryArithmeticInstruction(binaryInstruction);
        } else if (instr instanceof BitwiseBinaryInstruction) {
            BitwiseBinaryInstruction bitwiseInstruction = (BitwiseBinaryInstruction) instr;
            result = visitBinaryLogicalInstruction(bitwiseInstruction);
        } else if (instr instanceof ConversionInstruction) {
            result = visitConversionConstruction((ConversionInstruction) instr);
        } else if (instr instanceof Instruction_call_nonVoid) {
            result = (LLVMExpressionNode) visitFunctionCall((Instruction_call_nonVoid) instr);
        } else if (instr instanceof Instruction_alloca) {
            result = visitAllocaInstruction((Instruction_alloca) instr);
        } else if (instr instanceof Instruction_load) {
            result = visitLoadInstruction((Instruction_load) instr);
        } else if (instr instanceof Instruction_icmp) {
            result = visitIcmpInstruction((Instruction_icmp) instr);
        } else if (instr instanceof Instruction_fcmp) {
            result = visitFcmpInstruction((Instruction_fcmp) instr);
        } else if (instr instanceof Instruction_getelementptr) {
            result = visitGetElementPtr((Instruction_getelementptr) instr);
        } else if (instr instanceof Instruction_select) {
            result = visitSelectInstr((Instruction_select) instr);
        } else if (instr instanceof Instruction_insertvalue) {
            result = visitInsertValueInstr((Instruction_insertvalue) instr);
        } else if (instr instanceof Instruction_extractelement) {
            result = visitExtractElement((Instruction_extractelement) instr);
        } else if (instr instanceof Instruction_insertelement) {
            result = visitInsertElement((Instruction_insertelement) instr);
        } else if (instr instanceof Instruction_landingpad) {
            result = visitLandingPad((Instruction_landingpad) instr);
        } else if (instr instanceof Instruction_extractvalue) {
            result = visitExtractValue((Instruction_extractvalue) instr);
        } else if (instr instanceof Instruction_shufflevector) {
            result = visitShuffleVector((Instruction_shufflevector) instr);
        } else if (isMultithreadingInstruction(instr)) {
            throw new LLVMUnsupportedException(UnsupportedReason.MULTITHREADING);
        } else {
            throw new AssertionError(instr);
        }
        List<LLVMNode> resultNodes = new ArrayList<>();
        LLVMNode writeNode = getWriteNode(result, frameSlot, instr);
        resultNodes.add(writeNode);
        return resultNodes;
    }

    private static boolean isMultithreadingInstruction(EObject instr) {
        return instr instanceof Instruction_atomicrmw || instr instanceof Instruction_cmpxchg || instr instanceof Instruction_fence;
    }

    private LLVMExpressionNode visitShuffleVector(Instruction_shufflevector instr) {
        LLVMExpressionNode vector1 = visitValueRef(instr.getVector1().getRef(), instr.getVector1().getType());
        LLVMExpressionNode vector2 = visitValueRef(instr.getVector2().getRef(), instr.getVector2().getType());
        LLVMI32VectorNode mask = (LLVMI32VectorNode) visitValueRef(instr.getMask().getRef(), instr.getMask().getType());
        ResolvedType resultType = resolve(instr.getVector1().getType());
        ResolvedVectorType resultVectorType = resultType.asVector();
        LLVMExpressionNode target = allocateFunctionLifetime(resultVectorType);
        LLVMBaseType llvmType = getLLVMType(instr.getVector1().getType());
        return factoryFacade.createShuffleVector(llvmType, target, vector1, vector2, mask);
    }

    private LLVMExpressionNode visitExtractValue(Instruction_extractvalue instr) {
        LLVMAddressNode aggregate = (LLVMAddressNode) visitValueRef(instr.getAggregate().getRef(), instr.getAggregate().getType());
        EList<Constant> indices = instr.getIndices();
        if (indices.size() != 1) {
            throw new AssertionError("not yet supported");
        }
        LLVMAddressNode targetAddress = (LLVMAddressNode) getConstantElementPtr(aggregate, instr.getAggregate().getType(), indices);
        LLVMBaseType type = getLLVMType(instr);
        return factoryFacade.createExtractValue(type, targetAddress);
    }

    private LLVMExpressionNode visitLandingPad(Instruction_landingpad instr) {
        Type resultType = instr.getResultType();
        LLVMBaseType llvmType = getLLVMType(resultType);
        switch (llvmType) {
            case STRUCT:
                return new LLVMAddressLandingPadNode();
            default:
                throw new AssertionError(llvmType);
        }
    }

    private LLVMExpressionNode visitExtractElement(Instruction_extractelement instr) {
        LLVMExpressionNode vector = visitValueRef(instr.getVector().getRef(), instr.getVector().getType());
        LLVMExpressionNode index = visitValueRef(instr.getIndex().getRef(), instr.getIndex().getType());
        LLVMBaseType resultType = LLVMTypeHelper.getLLVMType(resolve(instr));
        return factoryFacade.createExtractElement(resultType, vector, index);
    }

    private LLVMExpressionNode visitInsertElement(Instruction_insertelement instr) {
        LLVMExpressionNode vector = visitValueRef(instr.getVector().getRef(), instr.getVector().getType());
        LLVMI32Node index = (LLVMI32Node) visitValueRef(instr.getIndex().getRef(), instr.getIndex().getType());
        LLVMExpressionNode element = visitValueRef(instr.getElement().getRef(), instr.getElement().getType());
        LLVMBaseType resultType = LLVMTypeHelper.getLLVMType(resolve(instr));
        return factoryFacade.createInsertElement(resultType, vector, instr.getVector().getType(), element, index);
    }

    private LLVMExpressionNode visitInsertValueInstr(Instruction_insertvalue insertValue) {
        EList<Constant> indices = insertValue.getIndices();
        TypedValue aggregate = insertValue.getAggregate();
        TypedValue element = insertValue.getElement();
        int size = LLVMTypeHelper.getByteSize(resolve(aggregate.getType()));
        LLVMExpressionNode resultAggregate = allocateFunctionLifetime(resolve(aggregate.getType()));
        LLVMAddressNode sourceAggregate = (LLVMAddressNode) visitValueRef(aggregate.getRef(), aggregate.getType());
        LLVMExpressionNode valueToInsert = visitValueRef(element.getRef(), element.getType());
        List<LLVMI32Node> indexNodes = new ArrayList<>();
        for (Constant c : indices) {
            LLVMExpressionNode constantNode = visitConstant(c, c);
            indexNodes.add((LLVMI32Node) constantNode);
        }
        int index = indexNodes.get(0).executeI32(null);
        int offset = LLVMTypeHelper.goIntoTypeGetLengthByte(resolve(aggregate.getType()), index);
        assert indexNodes.size() == 1;
        LLVMBaseType llvmType = getLLVMType(element);
        return factoryFacade.createInsertValue(resultAggregate, sourceAggregate, size, offset, valueToInsert, llvmType);
    }

    private LLVMExpressionNode visitFcmpInstruction(Instruction_fcmp instr) {
        String condition = instr.getCondition();
        LLVMExpressionNode left = visitValueRef(instr.getOp1(), instr.getType());
        LLVMExpressionNode right = visitValueRef(instr.getOp2(), instr.getType());
        LLVMBaseType llvmType = getLLVMType(instr.getType());
        return factoryFacade.createFloatComparison(left, right, llvmType, LLVMFloatComparisonType.fromString(condition));
    }

    private LLVMExpressionNode visitBinaryLogicalInstruction(BitwiseBinaryInstruction instr) {
        ValueRef op1 = instr.getOp1();
        ValueRef op2 = instr.getOp2();
        LLVMExpressionNode left = visitValueRef(op1, instr.getType());
        LLVMExpressionNode right = visitValueRef(op2, instr.getType());
        LLVMBaseType llvmType = getLLVMType(instr.getType());
        LLVMExpressionNode target = allocateVectorResultIfVector(instr);
        return factoryFacade.createLogicalOperation(left, right, instr, llvmType, target);
    }

    private LLVMExpressionNode allocateVectorResultIfVector(EObject type) {
        boolean isVector = resolve(type) instanceof ResolvedVectorType;
        LLVMExpressionNode target;
        if (isVector) {
            target = allocateVectorResult(type);
        } else {
            target = null;
        }
        return target;
    }

    private LLVMExpressionNode visitSelectInstr(Instruction_select instr) {
        LLVMI1Node condition = (LLVMI1Node) visitValueRef(instr.getCondition().getRef(), instr.getCondition().getType());
        LLVMExpressionNode trueValue = visitValueRef(instr.getValue1().getRef(), instr.getValue1().getType());
        LLVMExpressionNode falseValue = visitValueRef(instr.getValue2().getRef(), instr.getValue2().getType());
        LLVMBaseType llvmType = getLLVMType(instr.getValue1().getType());
        return factoryFacade.createSelect(llvmType, condition, trueValue, falseValue);
    }

    private LLVMExpressionNode visitGetElementPtr(Instruction_getelementptr getElementPtr) {
        TypedValue base = getElementPtr.getBase();
        Type baseType = base.getType();
        LLVMAddressNode baseNode = (LLVMAddressNode) visitValueRef(base.getRef(), baseType);
        LLVMAddressNode currentAddress = baseNode;
        List<ValueRef> refs = new ArrayList<>();
        List<Type> types = new ArrayList<>();
        EList<TypedValue> indices = getElementPtr.getIndices();
        for (TypedValue val : indices) {
            refs.add(val.getRef());
            types.add(val.getType());
        }
        return getElementPtr(currentAddress, baseType, refs, types);
    }

    private LLVMExpressionNode getElementPtr(LLVMAddressNode baseAddress, Type baseType, List<ValueRef> refs, List<Type> types) {
        LLVMAddressNode currentAddress = baseAddress;
        ResolvedType currentType = resolve(baseType);
        for (int i = 0; i < refs.size(); i++) {
            ValueRef currentRef = refs.get(i);
            Type type = types.get(i);
            Integer constantIndex = evaluateIndexAsConstant(currentRef);
            if (constantIndex == null) {
                int indexedTypeLength = LLVMTypeHelper.goIntoTypeGetLengthByte(currentType, 1);
                currentType = LLVMTypeHelper.goIntoType(currentType, 1);
                LLVMExpressionNode valueRef = visitValueRef(currentRef, type);
                currentAddress = (LLVMAddressNode) factoryFacade.createGetElementPtr(getLLVMType(type), currentAddress, valueRef, indexedTypeLength);
            } else {
                int indexedTypeLength = LLVMTypeHelper.goIntoTypeGetLengthByte(currentType, constantIndex);
                currentType = LLVMTypeHelper.goIntoType(currentType, constantIndex);
                if (indexedTypeLength != 0) {
                    LLVMExpressionNode constantNode;
                    switch (getLLVMType(type)) {
                        case I32:
                            constantNode = factoryFacade.createLiteral(1, LLVMBaseType.I32);
                            break;
                        case I64:
                            constantNode = factoryFacade.createLiteral(1L, LLVMBaseType.I64);
                            break;
                        default:
                            throw new AssertionError();
                    }
                    currentAddress = (LLVMAddressNode) factoryFacade.createGetElementPtr(getLLVMType(type), currentAddress, constantNode, indexedTypeLength);
                }
            }
        }
        return currentAddress;
    }

    private static Integer evaluateIndexAsConstant(ValueRef currentRef) {
        if (currentRef instanceof GlobalValueRef) {
            GlobalValueRef ref = (GlobalValueRef) currentRef;
            if (ref.getConstant() instanceof SimpleConstant) {
                SimpleConstant simpleConstant = (SimpleConstant) ref.getConstant();
                return Integer.parseInt(simpleConstant.getValue());
            }
        }
        return null;
    }

    private LLVMNode getWriteNode(LLVMExpressionNode result, FrameSlot slot, EObject type) {
        LLVMBaseType baseType = getLLVMType(type);
        FrameSlotKind frameSlotKind = factoryFacade.getFrameSlotKind(baseType);
        slot.setKind(frameSlotKind);
        return factoryFacade.createFrameWrite(baseType, result, slot);
    }

    private LLVMExpressionNode visitIcmpInstruction(Instruction_icmp instr) {
        String condition = instr.getCondition();
        LLVMExpressionNode left = visitValueRef(instr.getOp1(), instr.getType());
        LLVMExpressionNode right = visitValueRef(instr.getOp2(), instr.getType());
        LLVMBaseType llvmType = getLLVMType(instr.getType());
        return factoryFacade.createIntegerComparison(left, right, llvmType, LLVMIntegerComparisonType.fromString(condition));
    }

    private LLVMExpressionNode visitLoadInstruction(Instruction_load instr) {
        TypedValue pointer = instr.getPointer();
        LLVMExpressionNode pointerNode = visitValueRef(pointer.getRef(), pointer.getType());
        ResolvedType resolvedResultType = resolve(instr);
        LLVMAddressNode loadTarget = (LLVMAddressNode) pointerNode;
        return factoryFacade.createLoad(resolvedResultType, loadTarget);
    }

    private LLVMExpressionNode visitAllocaInstruction(Instruction_alloca instr) {
        TypedValue numElementsVal = instr.getNumElements();
        Type type = instr.getType();
        String alignmentString = instr.getAlignment();
        int alignment = 0;
        if (alignmentString == null) {
            if (layoutConverter != null) {
                alignment = LLVMTypeHelper.getAlignmentByte(resolve(instr.getType()));
            }
        } else {
            alignment = Integer.parseInt(alignmentString.substring("align ".length()));
        }
        if (alignment == 0) {
            alignment = LLVMStack.NO_ALIGNMENT_REQUIREMENTS;
        }
        int byteSize = LLVMTypeHelper.getByteSize(resolve(type));
        LLVMExpressionNode alloc;
        if (numElementsVal == null) {
            alloc = factoryFacade.createAlloc(byteSize, alignment);
        } else {
            Type numElementsType = instr.getNumElements().getType();
            LLVMBaseType llvmType = getLLVMType(numElementsType);
            LLVMExpressionNode numElements = visitValueRef(numElementsVal.getRef(), numElementsType);
            alloc = factoryFacade.createAlloc(llvmType, numElements, byteSize, alignment);
        }
        return alloc;
    }

    private LLVMNode visitFunctionCall(Instruction_call_nonVoid instr) {
        Callee callee = instr.getCallee();
        EList<Argument> args = instr.getArgs().getArguments();
        ResolvedType resolve = resolve(instr);
        return visitFunctionCall(callee, args, resolve);
    }

    private LLVMExpressionNode visitBinaryArithmeticInstruction(BinaryInstruction instr) {
        ValueRef op1 = instr.getOp1();
        ValueRef op2 = instr.getOp2();
        LLVMExpressionNode left = visitValueRef(op1, instr.getType());
        LLVMExpressionNode right = visitValueRef(op2, instr.getType());
        LLVMExpressionNode target = allocateVectorResultIfVector(instr);
        LLVMArithmeticInstructionType instructionType = LLVMArithmeticInstructionType.fromString(instr.getOpcode());
        return factoryFacade.createArithmeticOperation(left, right, instructionType, getLLVMType(instr.getType()), target);
    }

    private LLVMExpressionNode visitConversionConstruction(ConversionInstruction instr) {
        LLVMConversionType type = LLVMConversionType.fromString(instr.getOpcode());
        LLVMExpressionNode fromNode = visitValueRef(instr.getValue(), instr.getFromType());
        ResolvedType targetType = resolve(instr.getTargetType());
        ResolvedType fromType = resolve(instr.getFromType());
        return factoryFacade.createCast(fromNode, targetType, fromType, type);
    }

    private LLVMExpressionNode visitValueRef(ValueRef valueRef, Type type) {
        if (valueRef instanceof GlobalValueRef) {
            GlobalValueRef globalValueRef = (GlobalValueRef) valueRef;
            Constant constant = globalValueRef.getConstant();
            return visitConstant(type, constant);
        } else if (valueRef instanceof LocalValueRef) {
            LocalValueRef localValueRef = (LocalValueRef) valueRef;
            LocalValue localValue = localValueRef.getRef();
            String name = localValue.getName();
            return getReadNode(name, localValueRef);
        } else {
            throw new AssertionError(valueRef);
        }
    }

    private LLVMExpressionNode visitConstant(EObject type, Constant constant) throws AssertionError {
        if (constant instanceof SimpleConstant) {
            SimpleConstant simpleConst = (SimpleConstant) constant;
            return parseSimpleConstant(type, simpleConst);
        } else if (constant instanceof ArrayConstant) {
            return visitArrayConstantStore((ArrayConstant) constant);
        } else {
            if (resolve(type).isMetadata()) {
                return new LLVMMetadataNode(null);
            }
            if (constant.getRef() instanceof FunctionHeader) {
                FunctionHeader header = (FunctionHeader) constant.getRef();
                LLVMFunction function = createLLVMFunctionFromHeader(header);
                return factoryFacade.createLiteral(function, LLVMBaseType.FUNCTION_ADDRESS);
            } else if (constant.getRef() instanceof GlobalVariable) {
                GlobalVariable globalVariable = (GlobalVariable) constant.getRef();
                String globalVarName = globalVariable.getName();
                String linkage = globalVariable.getLinkage();
                if ("external".equals(linkage)) {
                    long getNativeSymbol = nativeLookup.getNativeHandle(globalVarName);
                    LLVMAddress nativeSymbolAddress = LLVMAddress.fromLong(getNativeSymbol);
                    return factoryFacade.createLiteral(nativeSymbolAddress, LLVMBaseType.ADDRESS);
                } else {
                    LLVMAddress findOrAllocateGlobal = findOrAllocateGlobal(globalVariable);
                    assert findOrAllocateGlobal != null;
                    return factoryFacade.createLiteral(findOrAllocateGlobal, LLVMBaseType.ADDRESS);
                }
            } else if (constant instanceof ZeroInitializer) {
                return visitZeroInitializer(type);
            } else if (constant instanceof ConstantExpression_convert) {
                return visitConstantExpressionConvert(constant);
            } else if (constant instanceof Undef) {
                return getUndefinedValueNode(type);
            } else if (constant instanceof StructureConstant) {
                return visitStructureConstant((StructureConstant) constant);
            } else if (constant instanceof ConstantExpression_getelementptr) {
                return visitConstantGetElementPtr((ConstantExpression_getelementptr) constant);
            } else if (constant instanceof BlockAddress) {
                return visitBlockAddress((BlockAddress) constant);
            } else if (constant instanceof ConstantExpression_compare) {
                return visitConstantExpressionCompare((ConstantExpression_compare) constant);
            } else if (constant instanceof ConstantExpression_binary) {
                return visitConstantExpressionBinary((ConstantExpression_binary) constant);
            } else if (constant instanceof VectorConstant) {
                return visitVectorConstant((VectorConstant) constant);
            } else if (constant.getRef() instanceof Alias) {
                return visitAliasConstant((Alias) constant.getRef());
            } else if (constant instanceof MetadataNode) {
                return visitMetadataNode((MetadataNode) constant);
            } else {
                throw new AssertionError(constant);
            }
        }
    }

    private static LLVMExpressionNode visitMetadataNode(MetadataNode constant) {
        EList<MetadataNodeElement> metaDataElements = constant.getElements();
        List<LLVMMetadata> metaDatas = new ArrayList<>();
        for (MetadataNodeElement metaData : metaDataElements) {
            metaDatas.add(visitMetaData(metaData));
        }
        assert metaDataElements.size() == 1;
        return new LLVMMetadataNode(metaDatas.get(0));
    }

    private static LLVMMetadata visitMetaData(@SuppressWarnings("unused") MetadataNodeElement metaData) {
        return new LLVMMetadata();
    }

    private LLVMExpressionNode visitAliasConstant(Alias ref) {
        Aliasee aliasee = ref.getAliasee();
        GlobalValueDef aliaseeRef = aliasee.getRef();
        if (aliaseeRef instanceof FunctionHeader) {
            LLVMFunction function = createLLVMFunctionFromHeader((FunctionHeader) aliaseeRef);
            return factoryFacade.createLiteral(function, LLVMBaseType.FUNCTION_ADDRESS);
        } else {
            throw new AssertionError(aliaseeRef);
        }
    }

    private LLVMExpressionNode visitConstantExpressionConvert(Constant constant) {
        ConstantExpression_convert conv = (ConstantExpression_convert) constant;
        ResolvedType targetType = resolve(conv.getTargetType());
        ResolvedType fromType = resolve(conv.getFromType());
        LLVMConversionType type = LLVMConversionType.fromString(conv.getOpcode());
        return factoryFacade.createCast(visitValueRef(conv.getConstant(), conv.getFromType()), targetType, fromType, type);
    }

    private LLVMExpressionNode visitVectorConstant(VectorConstant constant) {
        ConstantList list = constant.getList();
        ResolvedVectorType type = (ResolvedVectorType) resolve(constant);
        List<LLVMExpressionNode> listValues = visitConstantList(list);
        LLVMExpressionNode target = allocateVectorResult(constant);
        return factoryFacade.createVectorLiteralNode(listValues, target, type);
    }

    private List<LLVMExpressionNode> visitConstantList(ConstantList list) throws AssertionError {
        List<LLVMExpressionNode> arrayValues = new ArrayList<>();
        for (TypedConstant typedConst : list.getTypedConstants()) {
            LLVMExpressionNode constValue = visitConstant(typedConst.getType(), typedConst.getValue());
            arrayValues.add(constValue);
        }
        return arrayValues;
    }

    private LLVMExpressionNode visitConstantExpressionBinary(ConstantExpression_binary constant) {
        LLVMExpressionNode left = visitValueRef(constant.getOp1().getRef(), constant.getOp1().getType());
        LLVMExpressionNode right = visitValueRef(constant.getOp2().getRef(), constant.getOp2().getType());
        String opCode = constant.getOpcode();
        LLVMExpressionNode target = allocateVectorResultIfVector(constant);
        LLVMBaseType llvmType = getLLVMType(constant);
        if (LLVMLogicalInstructionType.isLogicalInstruction(opCode)) {
            LLVMLogicalInstructionType opType = LLVMLogicalInstructionType.fromString(opCode);
            return factoryFacade.createLogicalOperation(left, right, opType, llvmType, target);
        } else if (LLVMArithmeticInstructionType.isArithmeticInstruction(opCode)) {
            LLVMArithmeticInstructionType opType = LLVMArithmeticInstructionType.fromString(opCode);
            return factoryFacade.createArithmeticOperation(left, right, opType, llvmType, target);
        }
        throw new AssertionError(opCode);
    }

    private LLVMExpressionNode visitConstantExpressionCompare(ConstantExpression_compare constant) {
        String condition = constant.getCondition();
        LLVMExpressionNode left = visitValueRef(constant.getOp1().getRef(), constant.getOp1().getType());
        LLVMExpressionNode right = visitValueRef(constant.getOp2().getRef(), constant.getOp2().getType());
        LLVMBaseType llvmType = getLLVMType(constant.getOp1().getType());
        if (LLVMIntegerComparisonType.isIntegerCondition(condition)) {
            return factoryFacade.createIntegerComparison(left, right, llvmType, LLVMIntegerComparisonType.fromString(condition));
        } else if (LLVMFloatComparisonType.isFloatCondition(condition)) {
            return factoryFacade.createFloatComparison(left, right, llvmType, LLVMFloatComparisonType.fromString(condition));
        } else {
            throw new AssertionError(condition);
        }
    }

    private LLVMExpressionNode visitBlockAddress(BlockAddress blockAddress) {
        FunctionHeader function = (FunctionHeader) blockAddress.getFunction().getConstant().getRef();
        BasicBlock basicBlock = blockAddress.getBasicBlock().getRef();
        int val;
        if (isGlobalScope) {
            Map<String, Integer> functionBlocks = functionToLabelMapping.get(function);
            val = functionBlocks.get(basicBlock.getName());
        } else {
            val = getIndexFromBasicBlock(basicBlock);
        }
        LLVMAddress fromLong = LLVMAddress.fromLong(val);
        return factoryFacade.createLiteral(fromLong, LLVMBaseType.ADDRESS);
    }

    private LLVMExpressionNode visitConstantGetElementPtr(ConstantExpression_getelementptr constant) {
        LLVMAddressNode baseNode = (LLVMAddressNode) visitValueRef(constant.getConstant(), constant.getConstantType());
        return getConstantElementPtr(baseNode, constant.getConstantType(), constant.getIndices());
    }

    private LLVMExpressionNode getConstantElementPtr(LLVMAddressNode baseAddress, Type baseType, EList<Constant> indices) {
        LLVMExpressionNode currentAddress = baseAddress;
        ResolvedType currentType = resolve(baseType);
        ResolvedType parentType = null;
        int currentOffset = 0;
        for (Constant index : indices) {
            Number evaluateConstant = (Number) LLVMConstantEvaluator.evaluateConstant(this, index);
            assert evaluateConstant.longValue() == evaluateConstant.intValue();
            int val = evaluateConstant.intValue();
            int indexedTypeLength = LLVMTypeHelper.goIntoTypeGetLengthByte(currentType, val);
            currentOffset += indexedTypeLength;
            parentType = currentType;
            currentType = LLVMTypeHelper.goIntoType(currentType, val);
        }
        if (currentType != null && !LLVMTypeHelper.isPackedStructType(parentType)) {
            currentOffset += LLVMTypeHelper.computePaddingByte(currentOffset, currentType);
        }
        if (currentOffset != 0) {
            LLVMExpressionNode oneValueNode = factoryFacade.createLiteral(1, LLVMBaseType.I32);
            currentAddress = factoryFacade.createGetElementPtr(currentAddress, oneValueNode, currentOffset);
        }
        return currentAddress;
    }

    private LLVMExpressionNode visitZeroInitializer(EObject type) {
        LLVMBaseType llvmType = getLLVMType(type);
        if (resolve(type).isVector()) {
            return visitZeroVectorInitializer(type);
        }
        switch (llvmType) {
            case ARRAY:
                return visitZeroArrayInitializer(type);
            case STRUCT:
                return visitZeroStructInitializer(type);
            default:
                throw new AssertionError(llvmType);
        }
    }

    private LLVMExpressionNode visitZeroVectorInitializer(EObject type) {
        ResolvedVectorType vectorType = (ResolvedVectorType) resolve(type);
        int nrElements = vectorType.getSize();
        LLVMExpressionNode target = allocateVectorResult(type);
        LLVMBaseType llvmType = LLVMTypeHelper.getLLVMType(vectorType);
        return factoryFacade.createZeroVectorInitializer(nrElements, target, llvmType);
    }

    private LLVMExpressionNode visitZeroStructInitializer(EObject type) {
        ResolvedType resolvedType = resolve(type);
        int size = LLVMTypeHelper.getByteSize(resolvedType);
        if (size == 0) {
            LLVMAddress minusOneNode = LLVMAddress.fromLong(-1);
            return factoryFacade.createLiteral(minusOneNode, LLVMBaseType.ADDRESS);
        } else {
            LLVMExpressionNode addressNode = allocateFunctionLifetime(size, resolvedType);
            return factoryFacade.createZeroNode(addressNode, size);

        }
    }

    private LLVMExpressionNode visitZeroArrayInitializer(EObject type) {
        ResolvedArrayType resolvedType = (ResolvedArrayType) resolve(type);
        int size = LLVMTypeHelper.getByteSize(resolvedType);
        if (size == 0) {
            // zero-size array, not allowed per C and C++ standard
            return null;
        } else {
            LLVMExpressionNode addressNode = allocateFunctionLifetime(resolvedType);
            return factoryFacade.createZeroNode(addressNode, size);
        }
    }

    private LLVMExpressionNode visitStructureConstant(StructureConstant structure) {
        ConstantList list = structure.getList();
        EList<TypedConstant> typedConstants = list.getTypedConstants();
        int[] offsets = new int[typedConstants.size()];
        Node[] nodes = new LLVMStructWriteNode[typedConstants.size()];
        int i = 0;
        int currentOffset = 0;
        boolean packed = structure.getPacked() != null;
        int structSize = LLVMTypeHelper.getStructureSizeByte(structure, typeResolver);
        // FIXME alignment
        LLVMExpressionNode alloc = allocateFunctionLifetime(structSize, LLVMStack.NO_ALIGNMENT_REQUIREMENTS);
        for (TypedConstant constant : typedConstants) {
            ResolvedType resolvedType = resolve(constant.getType());
            if (!packed) {
                currentOffset += LLVMTypeHelper.computePaddingByte(currentOffset, resolvedType);
            }
            offsets[i] = currentOffset;
            LLVMExpressionNode parsedConstant = visitConstant(constant.getType(), constant.getValue());
            int byteSize = LLVMTypeHelper.getByteSize(resolvedType);
            nodes[i] = factoryFacade.createStructWriteNode(parsedConstant, resolvedType);
            currentOffset += byteSize;
            i++;
        }
        GlobalValueDef ref = structure.getRef();
        if (ref != null) {
            throw new AssertionError();
        }
        return factoryFacade.createStructLiteralNode(offsets, nodes, alloc);
    }

    private LLVMExpressionNode getUndefinedValueNode(EObject type) {
        LLVMBaseType llvmType = getLLVMType(type);
        if (llvmType != LLVMBaseType.ARRAY && llvmType != LLVMBaseType.STRUCT) {
            return factoryFacade.createUndefinedValue(type);
        } else {
            ResolvedType resolvedType = resolve(type);
            int byteSize = LLVMTypeHelper.getByteSize(resolvedType);
            LLVMExpressionNode alloca = allocateFunctionLifetime(resolvedType);
            return factoryFacade.createEmptyStructLiteralNode(alloca, byteSize);
        }
    }

    private LLVMFunction createLLVMFunctionFromHeader(FunctionHeader header) {
        Type returnType = header.getRettype();
        Parameters parameters = header.getParameters();
        EList<Parameter> params = parameters.getParameters();
        LLVMBaseType llvmReturnType = getLLVMType(returnType);
        boolean varArgs = parameters.getVararg() != null;
        LLVMBaseType[] llvmParamTypes = new LLVMBaseType[params.size()];
        for (int i = 0; i < params.size(); i++) {
            llvmParamTypes[i] = getLLVMType(params.get(i).getType().getType());
        }
        return LLVMFunction.create(header.getName(), LLVMTypeHelper.convertType(llvmReturnType), LLVMTypeHelper.convertTypes(llvmParamTypes), varArgs);
    }

    private LLVMExpressionNode parseSimpleConstant(EObject type, SimpleConstant simpleConst) {
        LLVMBaseType instructionType = getLLVMType(type);
        String stringValue = simpleConst.getValue();
        if (instructionType == LLVMBaseType.ARRAY) {
            ResolvedType resolvedType = resolve(type);
            ResolvedType containedType = resolvedType.getContainedType(0);
            LLVMBaseType arrayElementType = LLVMTypeHelper.getLLVMType(containedType);
            switch (arrayElementType) {
                case I8:
                    final Pattern pattern = Pattern.compile("c\"(.+?)\"");
                    final Matcher matcher = pattern.matcher(stringValue);
                    if (matcher.matches()) {
                        String subString = matcher.group(1);
                        List<LLVMExpressionNode> values = new ArrayList<>();
                        int i = 0;
                        while (i < subString.length()) {
                            byte c = (byte) subString.charAt(i);
                            if (c == '\\') {
                                // Checkstyle: stop magic number check
                                String hexValue = subString.substring(i + 1, i + 3);
                                int value = Integer.parseInt(hexValue, HEX_BASE);
                                byte byteValue = (byte) value;
                                LLVMExpressionNode byteValueNode = factoryFacade.createLiteral(byteValue, LLVMBaseType.I8);
                                values.add(byteValueNode);
                                i += 3;
                                // Checkstyle: resume magic number check
                            } else {
                                LLVMExpressionNode byteValueNode = factoryFacade.createLiteral(c, LLVMBaseType.I8);
                                values.add(byteValueNode);
                                i++;
                            }
                        }
                        return getArrayLiteral(values, resolvedType);
                    } else {
                        throw new AssertionError("just C style strings supported for now");
                    }
                default:
                    throw new AssertionError(arrayElementType);
            }
        } else if (instructionType == LLVMBaseType.STRUCT) {
            throw new AssertionError(simpleConst);
        } else {
            return factoryFacade.createSimpleConstantNoArray(stringValue, instructionType, resolve(type));
        }
    }

    private LLVMExpressionNode getReadNode(String name, EObject type) {
        FrameSlot frameSlot = findOrAddFrameSlot(name, type);
        return getReadNodeForSlot(frameSlot, type);
    }

    private FrameSlot findOrAddFrameSlot(String name, EObject obj) {
        ResolvedType type = resolve(obj);
        FrameSlot frameSlot = frameDescriptor.findOrAddFrameSlot(name);
        if (frameSlot == null) {
            throw new AssertionError("frame slot is null!");
        }
        if (frameSlot.getKind() == FrameSlotKind.Illegal) {
            frameSlot.setKind(factoryFacade.getFrameSlotKind(type));
        }
        assert frameSlot.getKind() == factoryFacade.getFrameSlotKind(type);
        return frameSlot;
    }

    private LLVMExpressionNode getReadNodeForSlot(FrameSlot frameSlot, EObject type) {
        LLVMBaseType llvmType = getLLVMType(type);
        return factoryFacade.createFrameRead(llvmType, frameSlot);
    }

    private LLVMNode visitTerminatorInstruction(TerminatorInstruction instr) {
        EObject termInstruction = instr.getInstruction();
        if (termInstruction instanceof Instruction_ret) {
            return visitRet((Instruction_ret) termInstruction);
        } else if (termInstruction instanceof Instruction_unreachable) {
            return factoryFacade.createUnreachableNode();
        } else if (termInstruction instanceof Instruction_br) {
            return visitBr((Instruction_br) termInstruction);
        } else if (termInstruction instanceof Instruction_switch) {
            return visitSwitch((Instruction_switch) termInstruction);
        } else if (termInstruction instanceof Instruction_indirectbr) {
            return visitIndirectBranch((Instruction_indirectbr) termInstruction);
        } else if (termInstruction instanceof NamedTerminatorInstruction) {
            return visitNamedTerminator((NamedTerminatorInstruction) termInstruction);
        } else {
            throw new AssertionError(termInstruction);
        }
    }

    private LLVMNode visitNamedTerminator(NamedTerminatorInstruction termInstruction) {
        Instruction_invoke_nonVoid invoke = termInstruction.getInstruction();
        int normalContinueIndex = getIndexFromBasicBlock(invoke.getToLabel().getRef());
        int exceptionIndex = getIndexFromBasicBlock(invoke.getExceptionLabel().getRef());
        Callee callee = invoke.getCallee();
        EList<Argument> args = invoke.getArgs().getArguments();
        LLVMNode callInstruction = visitFunctionCall(callee, args, resolve(invoke));
        return new LLVMInvokeNode(normalContinueIndex, exceptionIndex, callInstruction);
    }

    private LLVMNode visitIndirectBranch(Instruction_indirectbr instr) {
        EList<BasicBlockRef> destinations = instr.getDestinations();
        int[] labelTargets = new int[destinations.size()];
        for (int i = 0; i < labelTargets.length; i++) {
            labelTargets[i] = getIndexFromBasicBlock(destinations.get(i).getRef());
        }
        LLVMExpressionNode value = visitValueRef(instr.getAddress().getRef(), instr.getAddress().getType());
        return factoryFacade.createIndirectBranch(value, labelTargets);
    }

    private LLVMNode visitSwitch(Instruction_switch switchInstr) {
        LLVMExpressionNode cond = visitValueRef(switchInstr.getComparisonValue().getRef(), switchInstr.getComparisonValue().getType());
        int defaultLabel = getIndexFromBasicBlock(switchInstr.getDefaultDest().getRef());
        int[] otherLabels = new int[switchInstr.getDestinations().size()];
        for (int i = 0; i < otherLabels.length; i++) {
            otherLabels[i] = getIndexFromBasicBlock(switchInstr.getDestinations().get(i).getRef());
        }
        EList<TypedValue> caseConditions = switchInstr.getCaseConditions();
        LLVMExpressionNode[] cases = new LLVMExpressionNode[caseConditions.size()];
        for (int i = 0; i < caseConditions.size(); i++) {
            cases[i] = visitValueRef(caseConditions.get(i).getRef(), caseConditions.get(i).getType());
        }
        LLVMParserAsserts.assertNoNullElement(cases);
        LLVMBaseType llvmType = getLLVMType(switchInstr.getComparisonValue().getType());
        return factoryFacade.createSwitch(cond, defaultLabel, otherLabels, cases, llvmType);
    }

    private LLVMNode visitBr(Instruction_br brInstruction) {
        TypedValue typedValue = brInstruction.getCondition();
        if (typedValue != null) {
            LLVMExpressionNode conditionNode = visitValueRef(typedValue.getRef(), typedValue.getType());
            BasicBlock trueBasicBlock = brInstruction.getTrue().getRef();
            int trueIndex = getIndexFromBasicBlock(trueBasicBlock);
            BasicBlock falseBasicBlock = brInstruction.getFalse().getRef();
            int falseIndex = getIndexFromBasicBlock(falseBasicBlock);
            return factoryFacade.createConditionalBranch(trueIndex, falseIndex, conditionNode);
        } else {
            BasicBlock unconditional = brInstruction.getUnconditional().getRef();
            int unconditionalIndex = getIndexFromBasicBlock(unconditional);
            return factoryFacade.createUnconditionalBranch(unconditionalIndex);
        }
    }

    private int getIndexFromBasicBlock(BasicBlock trueBasicBlock) {
        if (trueBasicBlock.getName() == null) {
            throw new LLVMParserException(ParserErrorCause.MISSING_BASIC_BLOCK_REF);
        } else {
            return labelList.get(trueBasicBlock.getName());
        }
    }

    private LLVMNode visitRet(Instruction_ret ret) {
        TypedValue val = ret.getVal();
        if (val == null) {
            return factoryFacade.createRetVoid();
        } else {
            LLVMExpressionNode retValue = visitValueRef(val.getRef(), val.getType());
            ResolvedType resolvedType = resolve(val.getType());
            retSlot.setKind(factoryFacade.getFrameSlotKind(resolvedType));
            return factoryFacade.createNonVoidRet(retValue, resolvedType);
        }
    }

    @Override
    public LLVMExpressionNode allocateVectorResult(EObject type) {
        ResolvedVectorType vector = (ResolvedVectorType) resolve(type);
        return allocateFunctionLifetime(vector);
    }

    @Override
    public ResolvedType resolve(EObject e) {
        return typeResolver.resolve(e);
    }

    public LLVMExpressionNode allocateFunctionLifetime(ResolvedType resolvedType) {
        int alignment = LLVMTypeHelper.getAlignmentByte(resolvedType);
        int size = LLVMTypeHelper.getByteSize(resolvedType);
        return allocateFunctionLifetime(size, alignment);
    }

    public LLVMExpressionNode allocateFunctionLifetime(int size, ResolvedType resolvedType) {
        int alignment = LLVMTypeHelper.getAlignmentByte(resolvedType);
        return allocateFunctionLifetime(size, alignment);
    }

    @Override
    public LLVMExpressionNode allocateFunctionLifetime(int size, int alignment) {
        return factoryFacade.createAlloc(size, alignment);
    }

    @Override
    public FrameSlot getReturnSlot() {
        return retSlot;
    }

    @Override
    public LLVMAddress getGlobalAddress(GlobalVariable var) {
        return globalVars.get(var);
    }

    public FrameSlot getStackPointerSlot() {
        return stackPointerSlot;
    }

    public LLVMOptimizationConfiguration getOptimizationConfiguration() {
        return optimizationConfiguration;
    }

    public int getBitAlignment(LLVMBaseType type) {
        return layoutConverter.getBitAlignment(type);
    }
}
