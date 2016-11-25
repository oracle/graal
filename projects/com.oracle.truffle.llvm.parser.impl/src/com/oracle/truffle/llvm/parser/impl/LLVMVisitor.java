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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.truffle.llvm.parser.base.model.globals.GlobalConstant;
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
import com.intel.llvm.ireditor.lLVM_IR.Instruction_and;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_ashr;
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
import com.intel.llvm.ireditor.lLVM_IR.Instruction_load;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_lshr;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_or;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_ret;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_select;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_shl;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_shufflevector;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_store;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_switch;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_unreachable;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_xor;
import com.intel.llvm.ireditor.lLVM_IR.LocalValue;
import com.intel.llvm.ireditor.lLVM_IR.LocalValueRef;
import com.intel.llvm.ireditor.lLVM_IR.MiddleInstruction;
import com.intel.llvm.ireditor.lLVM_IR.Model;
import com.intel.llvm.ireditor.lLVM_IR.NamedMetadata;
import com.intel.llvm.ireditor.lLVM_IR.NamedMiddleInstruction;
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
import com.intel.llvm.ireditor.types.ResolvedArrayType;
import com.intel.llvm.ireditor.types.ResolvedType;
import com.intel.llvm.ireditor.types.ResolvedVectorType;
import com.intel.llvm.ireditor.types.TypeResolver;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.context.nativeint.NativeLookup;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.base.LLVMStackFrameNuller;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.LLVMType;
import com.oracle.truffle.llvm.parser.base.datalayout.DataLayoutConverter;
import com.oracle.truffle.llvm.parser.base.facade.NodeFactoryFacade;
import com.oracle.truffle.llvm.parser.base.util.LLVMParserAsserts;
import com.oracle.truffle.llvm.parser.base.util.LLVMParserResultImpl;
import com.oracle.truffle.llvm.parser.impl.LLVMPhiVisitor.Phi;
import com.oracle.truffle.llvm.parser.impl.lifetime.LLVMLifeTimeAnalysisResult;
import com.oracle.truffle.llvm.parser.impl.lifetime.LLVMLifeTimeAnalysisVisitor;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMFloatComparisonType;
import com.oracle.truffle.llvm.parser.instructions.LLVMIntegerComparisonType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionType;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.LLVMParserException;
import com.oracle.truffle.llvm.runtime.LLVMParserException.ParserErrorCause;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.runtime.options.LLVMOptions;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunction;
import com.oracle.truffle.llvm.types.memory.LLVMStack;

/**
 * This class traverses the LLVM IR AST as provided by the <code>com.intel.llvm.ireditor</code>
 * project and returns an executable AST.
 */
public final class LLVMVisitor implements LLVMParserRuntimeTextual {

    private static final int HEX_BASE = 16;

    private static final String FUNCTION_RETURN_VALUE_FRAME_SLOT_ID = "<function return value>";
    private static final String STACK_ADDRESS_FRAME_SLOT_ID = "<stack pointer>";

    private static final TypeResolver typeResolver = new TypeResolver();
    private FrameDescriptor frameDescriptor;
    private FrameDescriptor globalFrameDescriptor;
    private List<LLVMNode> functionEpilogue;
    private Map<FunctionHeader, Map<String, Integer>> functionToLabelMapping;
    private final Map<LLVMFunction, RootCallTarget> functionCallTargets = new HashMap<>();
    private Map<String, Object> globalVariableScope = new HashMap<>();
    private final Map<String, com.oracle.truffle.llvm.parser.base.model.types.Type> variableTypes = new HashMap<>();
    private Map<String, Integer> labelList;
    private FrameSlot retSlot;
    private FrameSlot stackPointerSlot;
    private FunctionDef containingFunctionDef;
    private NodeFactoryFacade factoryFacade;

    private Map<BasicBlock, List<Phi>> phiRefs;

    private NativeLookup nativeLookup;

    private Object[] mainArgs;

    private Source sourceFile;
    private Source mainSourceFile;

    private LLVMTypeHelper typeHelper;

    public LLVMVisitor(Object[] mainArgs, Source sourceFile, Source mainSourceFile) {
        this.mainArgs = mainArgs;
        this.sourceFile = sourceFile;
        this.mainSourceFile = mainSourceFile;
        typeHelper = new LLVMTypeHelper(this);
    }

    private static LLVMFunction searchFunction(Map<LLVMFunction, RootCallTarget> parsedFunctions, String toSearch) {
        for (LLVMFunction func : parsedFunctions.keySet()) {
            if (func.getName().equals(toSearch)) {
                return func;
            }
        }
        return null;
    }

    public LLVMParserResult getMain(Model model, NodeFactoryFacade facade) {
        Map<LLVMFunction, RootCallTarget> parsedFunctions = visit(model, facade);
        LLVMFunction mainFunction = searchFunction(parsedFunctions, "@main");
        LLVMNode[] globalVarInits = globalNodes.toArray(new LLVMNode[globalNodes.size()]);
        RootCallTarget globalVarInitsTarget = Truffle.getRuntime().createCallTarget(factoryFacade.createStaticInitsRootNode(globalVarInits));
        deallocations = globalDeallocations.toArray(new LLVMNode[globalDeallocations.size()]);
        RootCallTarget globalVarDeallocsTarget = Truffle.getRuntime().createCallTarget(factoryFacade.createStaticInitsRootNode(deallocations));
        LLVMNode[] destructorFunctionsArray = destructorFunctions.toArray(new LLVMNode[destructorFunctions.size()]);
        RootCallTarget destructorFunctionsTarget = Truffle.getRuntime().createCallTarget(
                        factoryFacade.createStaticInitsRootNode(destructorFunctionsArray));
        List<RootCallTarget> destructorFunctionCallTargets = new ArrayList<>(1);
        destructorFunctionCallTargets.add(destructorFunctionsTarget);
        LLVMNode[] constructorFunctionsArray = constructorFunctions.toArray(new LLVMNode[constructorFunctions.size()]);
        RootCallTarget constructorFunctionsTarget = Truffle.getRuntime().createCallTarget(
                        factoryFacade.createStaticInitsRootNode(constructorFunctionsArray));
        List<RootCallTarget> constructorFunctionCallTargets = new ArrayList<>(1);
        constructorFunctionCallTargets.add(constructorFunctionsTarget);
        if (mainFunction == null) {
            return new LLVMParserResultImpl(null, globalVarInitsTarget, globalVarDeallocsTarget, constructorFunctionCallTargets, destructorFunctionCallTargets, parsedFunctions);
        }
        RootCallTarget mainCallTarget = parsedFunctions.get(mainFunction);
        RootNode globalFunction = factoryFacade.createGlobalRootNode(mainCallTarget, mainArgs, mainSourceFile, mainFunction.getParameterTypes());
        RootCallTarget globalFunctionRoot = Truffle.getRuntime().createCallTarget(globalFunction);
        RootNode globalRootNode = factoryFacade.createGlobalRootNodeWrapping(globalFunctionRoot, mainFunction.getReturnType());
        RootCallTarget wrappedCallTarget = Truffle.getRuntime().createCallTarget(globalRootNode);
        return new LLVMParserResultImpl(wrappedCallTarget, globalVarInitsTarget, globalVarDeallocsTarget, constructorFunctionCallTargets, destructorFunctionCallTargets, parsedFunctions);
    }

    public Map<LLVMFunction, RootCallTarget> visit(Model model, NodeFactoryFacade facade) {
        this.factoryFacade = facade;
        this.nativeLookup = new NativeLookup(facade);
        List<EObject> objects = model.eContents();
        List<LLVMFunction> functions = new ArrayList<>();
        globalNodes = new ArrayList<>();
        List<GlobalVariable> staticVars = new ArrayList<>();
        functionToLabelMapping = new HashMap<>();
        setTargetInfo(objects);
        allocateGlobals(objects);
        allocateAliases(objects);
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
                LLVMLogger.info(object + " not supported!");
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
                // do nothing
            } else if (object instanceof InlineAsm) {
                LLVMLogger.info("ignoring module level inline assembler!");
            } else {
                throw new AssertionError(object);
            }
        }
        List<LLVMNode> globalVarNodes = addGlobalVars(this, staticVars);
        globalNodes.addAll(globalVarNodes);
        return functionCallTargets;
    }

    private void allocateAliases(List<EObject> objects) {
        boolean resolvedAllAliases;
        boolean madeProgress;
        Object unresolvedAlias = null;
        do {
            resolvedAllAliases = true;
            madeProgress = false;
            for (EObject object : objects) {
                if (object instanceof Alias) {
                    Alias alias = (Alias) object;
                    if (aliases.get(alias) == null) {
                        GlobalValueDef aliasee = alias.getAliasee().getRef();
                        Object globalVar;
                        if (aliasee instanceof GlobalVariable) {
                            globalVar = globalVariableScope.get(((GlobalVariable) aliasee).getName());
                        } else if (aliasee instanceof Alias) {
                            globalVar = aliases.get(aliasee);
                        } else {
                            continue;
                        }
                        if (globalVar == null) {
                            resolvedAllAliases = false;
                            unresolvedAlias = alias.getName();
                        } else {
                            aliases.put(alias, globalVar);
                            madeProgress = true;
                        }
                    }
                }
            }
            if (!resolvedAllAliases && !madeProgress) {
                LLVMLogger.info("Could not resolve all aliases (e.g., " + unresolvedAlias + ")");
                break;
            }
        } while (!resolvedAllAliases);
    }

    private static void setTargetInfo(List<EObject> objects) {
        for (EObject object : objects) {
            if (object instanceof TargetInfo) {
                LLVMVisitor.visitTargetInfo((TargetInfo) object);
            }
        }
    }

    private void allocateGlobals(List<EObject> objects) {
        for (EObject object : objects) {
            if (object instanceof GlobalVariable) {
                GlobalVariable globalVar = (GlobalVariable) object;
                com.oracle.truffle.llvm.parser.base.model.globals.GlobalVariable resolveGlobalVariable = LLVMToBitcodeAdapter.resolveGlobalVariable(
                                (LLVMParserRuntimeTextual) factoryFacade.getRuntime(), globalVar);
                globalVariableScope.put(globalVar.getName(), findOrAllocateGlobal(resolveGlobalVariable));
            }
        }
    }

    private void addFunctionAttribute(GlobalVariable globalVar, List<LLVMNode> targetList) {
        ResolvedArrayType type = (ResolvedArrayType) typeResolver.resolve(globalVar.getType());
        int size = type.getSize();
        Object allocGlobalVariable = globalVariableScope.get(globalVar.getName());
        ResolvedType structType = type.getContainedType(0);
        int structSize = typeHelper.getByteSize(structType);
        for (int i = 0; i < size; i++) {
            LLVMExpressionNode globalVarAddress = factoryFacade.createLiteral(allocGlobalVariable, LLVMBaseType.ADDRESS);
            LLVMExpressionNode iNode = factoryFacade.createLiteral(i, LLVMBaseType.I32);
            LLVMExpressionNode structPointer = factoryFacade.createGetElementPtr(LLVMBaseType.I32, globalVarAddress, iNode, structSize);
            LLVMExpressionNode loadedStruct = factoryFacade.createLoad(TextToBCConverter.convert(structType), structPointer);
            ResolvedType functionType = structType.getContainedType(1);
            int indexedTypeLength = typeHelper.getAlignmentByte(functionType);
            LLVMExpressionNode oneLiteralNode = factoryFacade.createLiteral(1, LLVMBaseType.I32);
            LLVMExpressionNode functionLoadTarget = factoryFacade.createGetElementPtr(LLVMBaseType.I32, loadedStruct, oneLiteralNode, indexedTypeLength);
            LLVMExpressionNode loadedFunction = factoryFacade.createLoad(TextToBCConverter.convert(functionType), functionLoadTarget);
            LLVMExpressionNode[] argNodes = new LLVMExpressionNode[]{factoryFacade.createFrameRead(LLVMBaseType.ADDRESS, getStackPointerSlot())};
            assert argNodes.length == factoryFacade.getArgStartIndex().get();
            LLVMNode functionCall = factoryFacade.createFunctionCall(loadedFunction, argNodes, LLVMBaseType.VOID);
            targetList.add(functionCall);
        }
    }

    private final List<LLVMNode> destructorFunctions = new ArrayList<>();
    private final List<LLVMNode> constructorFunctions = new ArrayList<>();

    private List<LLVMNode> addGlobalVars(LLVMVisitor visitor, List<GlobalVariable> globalVariables) {
        frameDescriptor = globalFrameDescriptor = new FrameDescriptor();
        stackPointerSlot = frameDescriptor.addFrameSlot(STACK_ADDRESS_FRAME_SLOT_ID, FrameSlotKind.Object);
        List<LLVMNode> globalVarNodes = new ArrayList<>();
        for (GlobalVariable globalVar : globalVariables) {
            LLVMNode globalVarWrite = visitor.visitGlobalVariable(globalVar);
            if (globalVarWrite != null) {
                globalVarNodes.add(globalVarWrite);
            }
            if (globalVar.getName().equals("@llvm.global_ctors")) {
                addFunctionAttribute(globalVar, constructorFunctions);
            } else if (globalVar.getName().equals("@llvm.global_dtors")) {
                addFunctionAttribute(globalVar, destructorFunctions);
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
                String layout = object.getLayout();
                if (layout != null) {
                    // remove enclosing quotes
                    layout = layout.substring(1, layout.length() - 2);
                    layoutConverter = DataLayoutConverter.getConverter(layout);
                } else {
                    throw new AssertionError("Invalid Layout!");
                }
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
                ResolvedType resolvedType = resolve(globalVariable.getType());
                int byteSize = typeHelper.getByteSize(resolvedType);
                Object allocGlobalVariable = globalVariableScope.get(globalVariable.getName());
                if (byteSize == 0) {
                    return null;
                } else {
                    LLVMExpressionNode globalVarAddress = factoryFacade.createLiteral(allocGlobalVariable, LLVMBaseType.ADDRESS);
                    LLVMNode storeNode = getStoreNode(globalVarAddress, constant, globalVariable.getType());
                    return storeNode;
                }
            } else {
                // can be null, e.g., for zero-size array initializer
                return null;
            }
        }
    }

    private final Map<Alias, Object> aliases = new HashMap<>();
    private final List<LLVMNode> globalDeallocations = new ArrayList<>();
    private boolean isGlobalScope;

    private Object findOrAllocateGlobal(com.oracle.truffle.llvm.parser.base.model.globals.GlobalValueSymbol globalVariable) {
        if (globalVariable instanceof com.oracle.truffle.llvm.parser.base.model.globals.GlobalVariable) {
            return factoryFacade.allocateGlobalVariable((com.oracle.truffle.llvm.parser.base.model.globals.GlobalVariable) globalVariable);
        } else if (globalVariable instanceof GlobalConstant) {
            return factoryFacade.allocateGlobalConstant((GlobalConstant) globalVariable);
        } else {
            throw new AssertionError();
        }
    }

    private LLVMExpressionNode visitArrayConstantStore(ArrayConstant constant) {
        ConstantList constList = constant.getList();
        List<LLVMExpressionNode> arrayValues = visitConstantList(constList);
        return getArrayLiteral(arrayValues, resolve(constant));
    }

    private LLVMExpressionNode getArrayLiteral(List<LLVMExpressionNode> arrayValues, ResolvedType arrayType) {
        return factoryFacade.createArrayLiteral(arrayValues, TextToBCConverter.convert(arrayType));
    }

    private LLVMFunction visitFunction(FunctionDef def) {
        this.containingFunctionDef = def;
        isGlobalScope = false;
        frameDescriptor = new FrameDescriptor();
        isGlobalScope = false;
        if (!resolve(def.getHeader().getRettype()).isVoid()) {
            retSlot = frameDescriptor.addFrameSlot(FUNCTION_RETURN_VALUE_FRAME_SLOT_ID);
        }
        stackPointerSlot = frameDescriptor.addFrameSlot(STACK_ADDRESS_FRAME_SLOT_ID, FrameSlotKind.Object);
        functionEpilogue = new ArrayList<>();
        LLVMAttributeVisitor.visitFunctionHeader(def.getHeader());
        labelList = getBlockLabelIndexMapping(def);
        List<LLVMNode> formalParameters = getFormalParametersInit(def);
        LLVMExpressionNode block = getFunctionBlockStatements(def);
        LLVMNode[] beforeFunction = formalParameters.toArray(new LLVMNode[formalParameters.size()]);
        LLVMNode[] afterFunction = functionEpilogue.toArray(new LLVMNode[functionEpilogue.size()]);
        RootNode rootNode = factoryFacade.createFunctionStartNode(block, beforeFunction, afterFunction, sourceFile.createSection(1), frameDescriptor,
                        LLVMToBitcodeAdapter.resolveFunctionDef(this, def));
        if (LLVMOptions.DEBUG.printFunctionASTs()) {
            NodeUtil.printTree(System.out, rootNode);
        }
        LLVMFunction function = createLLVMFunctionFromHeader(def.getHeader());
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        functionCallTargets.put(function, callTarget);
        return function;
    }

    private LLVMExpressionNode getFunctionBlockStatements(FunctionDef def) {
        List<LLVMNode> allFunctionNodes = new ArrayList<>();
        int currentIndex = 0;
        int[] basicBlockIndices = new int[def.getBasicBlocks().size()];
        int i = 0;
        for (BasicBlock basicBlock : def.getBasicBlocks()) {
            LLVMNode statementNodes = visitBasicBlock(basicBlock);
            basicBlockIndices[i++] = currentIndex;
            currentIndex++;
            allFunctionNodes.add(statementNodes);
        }

        Map<BasicBlock, FrameSlot[]> deadSlotsAtBeginBlock;
        Map<BasicBlock, FrameSlot[]> deadSlotsAfterBlock;
        LLVMLifeTimeAnalysisResult analysisResult = LLVMLifeTimeAnalysisVisitor.visit(def, frameDescriptor);
        deadSlotsAtBeginBlock = analysisResult.getBeginDead();
        deadSlotsAfterBlock = analysisResult.getEndDead();
        LLVMStackFrameNuller[][] slotNullerBeginNodes = getSlotNuller(def, currentIndex, basicBlockIndices, deadSlotsAtBeginBlock);
        LLVMStackFrameNuller[][] slotNullerAfterNodes = getSlotNuller(def, currentIndex, basicBlockIndices, deadSlotsAfterBlock);
        return factoryFacade.createFunctionBlockNode(retSlot, allFunctionNodes, slotNullerBeginNodes, slotNullerAfterNodes);
    }

    private LLVMStackFrameNuller[][] getSlotNuller(FunctionDef def, int size, int[] basicBlockIndices, Map<BasicBlock, FrameSlot[]> deadSlotsAfterBlock) {
        LLVMStackFrameNuller[][] indexToSlotNuller = new LLVMStackFrameNuller[size][];
        int i = 0;
        for (BasicBlock basicBlock : def.getBasicBlocks()) {
            FrameSlot[] deadSlots = deadSlotsAfterBlock.get(basicBlock);
            if (deadSlots != null) {
                LLVMParserAsserts.assertNoNullElement(deadSlots);
                indexToSlotNuller[basicBlockIndices[i++]] = getSlotNullerNode(deadSlots);
            }
        }
        return indexToSlotNuller;
    }

    private LLVMStackFrameNuller[] getSlotNullerNode(FrameSlot[] deadSlots) {
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

    private LLVMStackFrameNuller getNullerNode(FrameSlot slot) {
        String identifier = (String) slot.getIdentifier();
        LLVMType type = LLVMTypeHelper.getLLVMType(variableTypes.get(identifier));
        return factoryFacade.createFrameNuller(identifier, type, slot);
    }

    private boolean needsStackPointerArgument() {
        Optional<Boolean> hasStackPointerArgument = factoryFacade.hasStackPointerArgument();
        return hasStackPointerArgument.isPresent() && hasStackPointerArgument.get();
    }

    private List<LLVMNode> getFormalParametersInit(FunctionDef def) {
        // add formal parameters
        List<LLVMNode> formalParamInits = new ArrayList<>();
        FunctionHeader functionHeader = def.getHeader();
        EList<Parameter> pars = functionHeader.getParameters().getParameters();
        if (needsStackPointerArgument()) {
            LLVMExpressionNode stackPointerNode = factoryFacade.createFunctionArgNode(0, LLVMBaseType.ADDRESS);
            formalParamInits.add(factoryFacade.createFrameWrite(LLVMBaseType.ADDRESS, stackPointerNode, getStackPointerSlot()));
        }
        int argIndex = factoryFacade.getArgStartIndex().get();
        if (resolve(functionHeader.getRettype()).isStruct()) {
            LLVMExpressionNode functionRetParNode = factoryFacade.createFunctionArgNode(argIndex++, LLVMBaseType.STRUCT);
            LLVMNode retValue = createAssignment((String) retSlot.getIdentifier(), functionRetParNode, functionHeader.getRettype());
            formalParamInits.add(retValue);
        }
        for (Parameter par : pars) {
            LLVMExpressionNode parNode;
            LLVMBaseType paramType = getLLVMType(par.getType()).getType();
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

    private static Map<String, Integer> getBlockLabelIndexMapping(FunctionDef functionDef) {
        int labelIndex = 0;
        HashMap<String, Integer> labels = new HashMap<>();
        for (BasicBlock basicBlock : functionDef.getBasicBlocks()) {
            labels.put(basicBlock.getName(), labelIndex);
            labelIndex++;
        }
        return labels;
    }

    public static DataLayoutConverter.DataSpecConverter layoutConverter;

    private LLVMNode[] deallocations;

    private List<LLVMNode> globalNodes;

    private BasicBlock currentBasicBlock;

    private LLVMNode visitBasicBlock(BasicBlock basicBlock) {
        currentBasicBlock = basicBlock;
        List<LLVMNode> statements = new ArrayList<>(basicBlock.getInstructions().size());
        for (Instruction instr : basicBlock.getInstructions()) {
            List<LLVMNode> instrInstructions = visitInstruction(instr);
            for (LLVMNode instruction : instrInstructions) {
                statements.add(instruction);
            }
        }
        LLVMNode[] statementNodes = new LLVMNode[statements.size() - 1];
        System.arraycopy(statements.toArray(new LLVMNode[statementNodes.length]), 0, statementNodes, 0, statementNodes.length);
        LLVMParserAsserts.assertNoNullElement(statementNodes);
        LLVMNode terminatorNode = statements.get(statements.size() - 1);
        int basicBlockIndex = getIndexFromBasicBlock(basicBlock);
        LLVMNode basicBlockNode = factoryFacade.createBasicBlockNode(statementNodes, terminatorNode, basicBlockIndex, basicBlock.getName());
        return basicBlockNode;
    }

    private List<LLVMNode> visitInstruction(Instruction instr) {
        if (instr instanceof TerminatorInstruction) {
            List<LLVMNode> statements = new ArrayList<>();
            LLVMNode visitTerminatorInstruction = visitTerminatorInstruction((TerminatorInstruction) instr);
            statements.add(visitTerminatorInstruction);
            return statements;
        } else if (instr instanceof MiddleInstruction) {
            return visitMiddleInstruction((MiddleInstruction) instr);
        } else if (instr instanceof StartingInstruction) {
            return Collections.emptyList();
        } else {
            throw new AssertionError(instr);
        }
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
        List<LLVMExpressionNode> argNodes = new ArrayList<>(args.size());
        if (needsStackPointerArgument()) {
            LLVMExpressionNode stackPointerRead = factoryFacade.createFrameRead(LLVMBaseType.ADDRESS, getStackPointerSlot());
            argNodes.add(stackPointerRead);
        }
        if (retType.isStruct()) {
            argNodes.add(allocateFunctionLifetime(retType));
        }
        for (Argument arg : args) {
            argNodes.add(visitValueRef(arg.getRef(), arg.getType().getType()));
        }
        LLVMExpressionNode[] finalArgs = argNodes.toArray(new LLVMExpressionNode[argNodes.size()]);
        if (callee instanceof GlobalValueRef && ((GlobalValueRef) callee).getConstant().getRef() instanceof FunctionHeader) {
            FunctionHeader functionHeader = (FunctionHeader) ((GlobalValueRef) callee).getConstant().getRef();
            LLVMNode optionalSubstitutionNode = factoryFacade.tryCreateFunctionSubstitution(LLVMToBitcodeAdapter.resolveFunctionHeader(this, functionHeader), finalArgs,
                            LLVMToBitcodeAdapter.resolveFunctionDef(this, containingFunctionDef).getArgumentTypes().length);
            if (optionalSubstitutionNode != null) {
                return optionalSubstitutionNode;
            }
        } else if (callee instanceof InlineAssembler) {
            String asmSnippet = ((InlineAssembler) callee).getAssembler();
            String asmFlags = ((InlineAssembler) callee).getFlags();
            return factoryFacade.createInlineAssemblerExpression(asmSnippet, asmFlags, finalArgs, LLVMTypeHelper.getLLVMType(retType).getType());
        }
        LLVMExpressionNode func = visitValueRef((ValueRef) callee, null);
        return factoryFacade.createFunctionCall(func, finalArgs, LLVMTypeHelper.getLLVMType(retType).getType());
    }

    private LLVMNode visitStoreInstruction(Instruction_store instr) {
        TypedValue pointer = instr.getPointer();
        TypedValue index = instr.getValue();
        LLVMExpressionNode pointerNode = visitValueRef(pointer.getRef(), pointer.getType());
        LLVMExpressionNode valueNode = visitValueRef(index.getRef(), index.getType());
        return getStoreNode(pointerNode, valueNode, index.getType());
    }

    private LLVMNode getStoreNode(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type) {
        return factoryFacade.createStore(pointerNode, valueNode, TextToBCConverter.convert(resolve(type)));
    }

    private LLVMType getLLVMType(EObject object) {
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
        FrameSlot frameSlot = findOrAddFrameSlot(name, namedMiddleInstr);
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
        LLVMExpressionNode mask = visitValueRef(instr.getMask().getRef(), instr.getMask().getType());
        ResolvedType resultType = resolve(instr.getVector1().getType());
        ResolvedVectorType resultVectorType = resultType.asVector();
        LLVMExpressionNode target = allocateFunctionLifetime(resultVectorType);
        LLVMBaseType llvmType = getLLVMType(instr.getVector1().getType()).getType();
        return factoryFacade.createShuffleVector(llvmType, target, vector1, vector2, mask);
    }

    private LLVMExpressionNode visitExtractValue(Instruction_extractvalue instr) {
        LLVMExpressionNode aggregate = visitValueRef(instr.getAggregate().getRef(), instr.getAggregate().getType());
        EList<Constant> indices = instr.getIndices();
        if (indices.size() != 1) {
            throw new AssertionError("not yet supported");
        }
        LLVMExpressionNode targetAddress = getConstantElementPtr(aggregate, instr.getAggregate().getType(), indices);
        LLVMBaseType type = getLLVMType(instr).getType();
        return factoryFacade.createExtractValue(type, targetAddress);
    }

    private LLVMExpressionNode visitExtractElement(Instruction_extractelement instr) {
        LLVMExpressionNode vector = visitValueRef(instr.getVector().getRef(), instr.getVector().getType());
        LLVMExpressionNode index = visitValueRef(instr.getIndex().getRef(), instr.getIndex().getType());
        LLVMBaseType resultType = LLVMTypeHelper.getLLVMType(resolve(instr)).getType();
        return factoryFacade.createExtractElement(resultType, vector, index);
    }

    private LLVMExpressionNode visitInsertElement(Instruction_insertelement instr) {
        LLVMExpressionNode vector = visitValueRef(instr.getVector().getRef(), instr.getVector().getType());
        LLVMExpressionNode index = visitValueRef(instr.getIndex().getRef(), instr.getIndex().getType());
        LLVMExpressionNode element = visitValueRef(instr.getElement().getRef(), instr.getElement().getType());
        LLVMBaseType resultType = LLVMTypeHelper.getLLVMType(resolve(instr)).getType();
        return factoryFacade.createInsertElement(resultType, vector, instr.getVector().getType(), element, index);
    }

    private LLVMExpressionNode visitInsertValueInstr(Instruction_insertvalue insertValue) {
        EList<Constant> indices = insertValue.getIndices();
        TypedValue aggregate = insertValue.getAggregate();
        TypedValue element = insertValue.getElement();
        int size = typeHelper.getByteSize(resolve(aggregate.getType()));
        LLVMExpressionNode resultAggregate = allocateFunctionLifetime(resolve(aggregate.getType()));
        LLVMExpressionNode sourceAggregate = visitValueRef(aggregate.getRef(), aggregate.getType());
        LLVMExpressionNode valueToInsert = visitValueRef(element.getRef(), element.getType());
        List<Integer> constants = new ArrayList<>();
        for (Constant c : indices) {
            constants.add((Integer) LLVMConstantEvaluator.evaluateConstant(this, c));
        }
        if (constants.size() != 1) {
            throw new AssertionError(constants);
        }
        int index = constants.get(0);
        int offset = typeHelper.goIntoTypeGetLengthByte(resolve(aggregate.getType()), index);
        LLVMBaseType llvmType = getLLVMType(element).getType();
        return factoryFacade.createInsertValue(resultAggregate, sourceAggregate, size, offset, valueToInsert, llvmType);
    }

    @SuppressWarnings("deprecation")
    private LLVMExpressionNode visitFcmpInstruction(Instruction_fcmp instr) {
        String condition = instr.getCondition();
        LLVMExpressionNode left = visitValueRef(instr.getOp1(), instr.getType());
        LLVMExpressionNode right = visitValueRef(instr.getOp2(), instr.getType());
        LLVMBaseType llvmType = getLLVMType(instr.getType()).getType();
        return factoryFacade.createFloatComparison(left, right, llvmType, LLVMFloatComparisonType.fromString(condition));
    }

    private LLVMExpressionNode visitBinaryLogicalInstruction(BitwiseBinaryInstruction instr) {
        ValueRef op1 = instr.getOp1();
        ValueRef op2 = instr.getOp2();
        LLVMExpressionNode left = visitValueRef(op1, instr.getType());
        LLVMExpressionNode right = visitValueRef(op2, instr.getType());
        LLVMBaseType llvmType = getLLVMType(instr.getType()).getType();
        LLVMExpressionNode target = allocateVectorResultIfVector(instr);
        return factoryFacade.createLogicalOperation(left, right, getLogicalInstructionType(instr), llvmType, target);
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

    private static LLVMLogicalInstructionType getLogicalInstructionType(BitwiseBinaryInstruction instr) {
        if (instr instanceof Instruction_and) {
            return LLVMLogicalInstructionType.AND;
        } else if (instr instanceof Instruction_or) {
            return LLVMLogicalInstructionType.OR;
        } else if (instr instanceof Instruction_shl) {
            return LLVMLogicalInstructionType.SHIFT_LEFT;
        } else if (instr instanceof Instruction_lshr) {
            return LLVMLogicalInstructionType.LOGICAL_SHIFT_RIGHT;
        } else if (instr instanceof Instruction_ashr) {
            return LLVMLogicalInstructionType.ARITHMETIC_SHIFT_RIGHT;
        } else if (instr instanceof Instruction_xor) {
            return LLVMLogicalInstructionType.XOR;
        } else {
            throw new AssertionError(instr);
        }
    }

    private LLVMExpressionNode visitSelectInstr(Instruction_select instr) {
        LLVMExpressionNode condition = visitValueRef(instr.getCondition().getRef(), instr.getCondition().getType());
        LLVMExpressionNode trueValue = visitValueRef(instr.getValue1().getRef(), instr.getValue1().getType());
        LLVMExpressionNode falseValue = visitValueRef(instr.getValue2().getRef(), instr.getValue2().getType());
        return factoryFacade.createSelect(TextToBCConverter.convert(resolve(instr.getValue1().getType())), condition, trueValue, falseValue);
    }

    private LLVMExpressionNode visitGetElementPtr(Instruction_getelementptr getElementPtr) {
        TypedValue base = getElementPtr.getBase();
        Type baseType = base.getType();
        LLVMExpressionNode baseNode = visitValueRef(base.getRef(), baseType);
        LLVMExpressionNode currentAddress = baseNode;
        List<ValueRef> refs = new ArrayList<>();
        List<Type> types = new ArrayList<>();
        EList<TypedValue> indices = getElementPtr.getIndices();
        for (TypedValue val : indices) {
            refs.add(val.getRef());
            types.add(val.getType());
        }
        return getElementPtr(currentAddress, baseType, refs, types);
    }

    private LLVMExpressionNode getElementPtr(LLVMExpressionNode baseAddress, Type baseType, List<ValueRef> refs, List<Type> types) {
        LLVMExpressionNode currentAddress = baseAddress;
        ResolvedType currentType = resolve(baseType);
        for (int i = 0; i < refs.size(); i++) {
            ValueRef currentRef = refs.get(i);
            Type type = types.get(i);
            Integer constantIndex = evaluateIndexAsConstant(currentRef);
            if (constantIndex == null) {
                int indexedTypeLength = typeHelper.goIntoTypeGetLengthByte(currentType, 1);
                currentType = LLVMTypeHelper.goIntoType(currentType, 1);
                LLVMExpressionNode valueRef = visitValueRef(currentRef, type);
                currentAddress = factoryFacade.createGetElementPtr(getLLVMType(type).getType(), currentAddress, valueRef, indexedTypeLength);
            } else {
                int indexedTypeLength = typeHelper.goIntoTypeGetLengthByte(currentType, constantIndex);
                currentType = LLVMTypeHelper.goIntoType(currentType, constantIndex);
                if (indexedTypeLength != 0) {
                    LLVMExpressionNode constantNode;
                    switch (getLLVMType(type).getType()) {
                        case I32:
                            constantNode = factoryFacade.createLiteral(1, LLVMBaseType.I32);
                            break;
                        case I64:
                            constantNode = factoryFacade.createLiteral(1L, LLVMBaseType.I64);
                            break;
                        default:
                            throw new AssertionError();
                    }
                    currentAddress = factoryFacade.createGetElementPtr(getLLVMType(type).getType(), currentAddress, constantNode, indexedTypeLength);
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
                try {
                    return Integer.parseInt(simpleConstant.getValue());
                } catch (NumberFormatException e) {
                    LLVMLogger.info("GEP index overflow (still parse as int");
                    return (int) Long.parseLong(simpleConstant.getValue());
                }
            }
        }
        return null;
    }

    private LLVMNode getWriteNode(LLVMExpressionNode result, FrameSlot slot, EObject type) {
        LLVMBaseType baseType = getLLVMType(type).getType();
        FrameSlotKind frameSlotKind = factoryFacade.getFrameSlotKind(TextToBCConverter.convert(resolve(type)));
        slot.setKind(frameSlotKind);
        return factoryFacade.createFrameWrite(baseType, result, slot);
    }

    @SuppressWarnings("deprecation")
    private LLVMExpressionNode visitIcmpInstruction(Instruction_icmp instr) {
        String condition = instr.getCondition();
        LLVMExpressionNode left = visitValueRef(instr.getOp1(), instr.getType());
        LLVMExpressionNode right = visitValueRef(instr.getOp2(), instr.getType());
        LLVMBaseType llvmType = getLLVMType(instr.getType()).getType();
        return factoryFacade.createIntegerComparison(left, right, llvmType, LLVMIntegerComparisonType.fromString(condition));
    }

    private LLVMExpressionNode visitLoadInstruction(Instruction_load instr) {
        TypedValue pointer = instr.getPointer();
        LLVMExpressionNode pointerNode = visitValueRef(pointer.getRef(), pointer.getType());
        ResolvedType resolvedResultType = resolve(instr);
        LLVMExpressionNode loadTarget = pointerNode;
        return factoryFacade.createLoad(TextToBCConverter.convert(resolvedResultType), loadTarget);
    }

    private LLVMExpressionNode visitAllocaInstruction(Instruction_alloca instr) {
        TypedValue numElementsVal = instr.getNumElements();
        Type type = instr.getType();
        ResolvedType resolvedInstructionType = resolve(type);
        String alignmentString = instr.getAlignment();
        int alignment = 0;
        if (alignmentString == null) {
            if (layoutConverter != null) {
                alignment = typeHelper.getAlignmentByte(resolvedInstructionType);
            }
        } else {
            alignment = Integer.parseInt(alignmentString.substring("align ".length()));
        }
        if (alignment == 0) {
            alignment = LLVMStack.NO_ALIGNMENT_REQUIREMENTS;
        }
        int byteSize = typeHelper.getByteSize(resolvedInstructionType);
        LLVMExpressionNode alloc;
        if (numElementsVal == null) {
            alloc = factoryFacade.createAlloc(TextToBCConverter.convert(resolvedInstructionType), byteSize, alignment, null, null);
        } else {
            Type numElementsType = instr.getNumElements().getType();
            LLVMBaseType llvmType = getLLVMType(numElementsType).getType();
            LLVMExpressionNode numElements = visitValueRef(numElementsVal.getRef(), numElementsType);
            alloc = factoryFacade.createAlloc(TextToBCConverter.convert(resolvedInstructionType), byteSize, alignment, llvmType, numElements);
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
        return factoryFacade.createArithmeticOperation(left, right, instructionType, getLLVMType(instr.getType()).getType(), target);
    }

    private LLVMExpressionNode visitConversionConstruction(ConversionInstruction instr) {
        LLVMConversionType type = LLVMConversionType.fromString(instr.getOpcode());
        LLVMExpressionNode fromNode = visitValueRef(instr.getValue(), instr.getFromType());
        ResolvedType targetType = resolve(instr.getTargetType());
        ResolvedType fromType = resolve(instr.getFromType());
        return factoryFacade.createCast(fromNode, TextToBCConverter.convert(targetType), TextToBCConverter.convert(fromType), type);
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
            if (constant.getRef() instanceof FunctionHeader) {
                FunctionHeader header = (FunctionHeader) constant.getRef();
                LLVMFunction function = createLLVMFunctionFromHeader(header);
                return factoryFacade.createLiteral(function, LLVMBaseType.FUNCTION_ADDRESS);
            } else if (constant.getRef() instanceof GlobalVariable) {
                GlobalVariable globalVariable = (GlobalVariable) constant.getRef();
                Object findOrAllocateGlobal = globalVariableScope.get(globalVariable.getName());
                assert findOrAllocateGlobal != null;
                return factoryFacade.createLiteral(findOrAllocateGlobal, LLVMBaseType.ADDRESS);
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
            } else {
                throw new AssertionError(constant);
            }
        }
    }

    private LLVMExpressionNode visitAliasConstant(Alias ref) {
        Aliasee aliasee = ref.getAliasee();
        GlobalValueDef aliaseeRef = aliasee.getRef();
        if (aliaseeRef instanceof FunctionHeader) {
            LLVMFunction function = createLLVMFunctionFromHeader((FunctionHeader) aliaseeRef);
            return factoryFacade.createLiteral(function, LLVMBaseType.FUNCTION_ADDRESS);
        } else if (aliaseeRef instanceof GlobalVariable) {
            GlobalVariable originalVar = (GlobalVariable) aliaseeRef;
            Object globalVar = globalVariableScope.get(originalVar.getName());
            LLVMParserAsserts.assertNotNull(globalVar);
            return factoryFacade.createLiteral(globalVar, LLVMBaseType.ADDRESS);
        } else if (aliaseeRef instanceof Alias) {
            Object globalVar = aliases.get(ref);
            LLVMParserAsserts.assertNotNull(globalVar);
            return factoryFacade.createLiteral(globalVar, LLVMBaseType.ADDRESS);
        } else if (aliasee.getBitcast() != null) {
            GlobalValueRef constant = aliasee.getBitcast().getConstant();
            return visitValueRef(constant, aliasee.getBitcast().getTargetType());
        } else {
            throw new AssertionError(aliaseeRef);
        }
    }

    private LLVMExpressionNode visitConstantExpressionConvert(Constant constant) {
        ConstantExpression_convert conv = (ConstantExpression_convert) constant;
        ResolvedType targetType = resolve(conv.getTargetType());
        ResolvedType fromType = resolve(conv.getFromType());
        LLVMConversionType type = LLVMConversionType.fromString(conv.getOpcode());
        return factoryFacade.createCast(visitValueRef(conv.getConstant(), conv.getFromType()), TextToBCConverter.convert(targetType), TextToBCConverter.convert(fromType), type);
    }

    private LLVMExpressionNode visitVectorConstant(VectorConstant constant) {
        ConstantList list = constant.getList();
        ResolvedVectorType type = (ResolvedVectorType) resolve(constant);
        List<LLVMExpressionNode> listValues = visitConstantList(list);
        LLVMExpressionNode target = allocateVectorResult(constant);
        return factoryFacade.createVectorLiteralNode(listValues, target, LLVMTypeHelper.getLLVMType(type).getType());
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
        LLVMBaseType llvmType = getLLVMType(constant).getType();
        if (LLVMLogicalInstructionType.isLogicalInstruction(opCode)) {
            LLVMLogicalInstructionType opType = LLVMLogicalInstructionType.fromString(opCode);
            return factoryFacade.createLogicalOperation(left, right, opType, llvmType, target);
        } else if (LLVMArithmeticInstructionType.isArithmeticInstruction(opCode)) {
            LLVMArithmeticInstructionType opType = LLVMArithmeticInstructionType.fromString(opCode);
            return factoryFacade.createArithmeticOperation(left, right, opType, llvmType, target);
        }
        throw new AssertionError(opCode);
    }

    @SuppressWarnings("deprecation")
    private LLVMExpressionNode visitConstantExpressionCompare(ConstantExpression_compare constant) {
        String condition = constant.getCondition();
        LLVMExpressionNode left = visitValueRef(constant.getOp1().getRef(), constant.getOp1().getType());
        LLVMExpressionNode right = visitValueRef(constant.getOp2().getRef(), constant.getOp2().getType());
        LLVMBaseType llvmType = getLLVMType(constant.getOp1().getType()).getType();
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
        LLVMExpressionNode baseNode = visitValueRef(constant.getConstant(), constant.getConstantType());
        return getConstantElementPtr(baseNode, constant.getConstantType(), constant.getIndices());
    }

    private LLVMExpressionNode getConstantElementPtr(LLVMExpressionNode baseAddress, Type baseType, EList<Constant> indices) {
        LLVMExpressionNode currentAddress = baseAddress;
        ResolvedType currentType = resolve(baseType);
        ResolvedType parentType = null;
        int currentOffset = 0;
        for (Constant index : indices) {
            Number evaluateConstant = (Number) LLVMConstantEvaluator.evaluateConstant(this, index);
            assert evaluateConstant.longValue() == evaluateConstant.intValue();
            int val = evaluateConstant.intValue();
            int indexedTypeLength = typeHelper.goIntoTypeGetLengthByte(currentType, val);
            currentOffset += indexedTypeLength;
            parentType = currentType;
            currentType = LLVMTypeHelper.goIntoType(currentType, val);
        }
        if (currentType != null && !LLVMTypeHelper.isPackedStructType(parentType)) {
            currentOffset += typeHelper.computePaddingByte(currentOffset, currentType);
        }
        if (currentOffset != 0) {
            LLVMExpressionNode oneValueNode = factoryFacade.createLiteral(1, LLVMBaseType.I32);
            currentAddress = factoryFacade.createGetElementPtr(LLVMBaseType.I32, currentAddress, oneValueNode, currentOffset);
        }
        return currentAddress;
    }

    private LLVMExpressionNode visitZeroInitializer(EObject type) {
        LLVMBaseType llvmType = getLLVMType(type).getType();
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
        LLVMBaseType llvmType = LLVMTypeHelper.getLLVMType(vectorType).getType();
        return factoryFacade.createZeroVectorInitializer(nrElements, target, llvmType);
    }

    private LLVMExpressionNode visitZeroStructInitializer(EObject type) {
        ResolvedType resolvedType = resolve(type);
        int size = typeHelper.getByteSize(resolvedType);
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
        int size = typeHelper.getByteSize(resolvedType);
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
        boolean packed = structure.getPacked() != null;
        ResolvedType[] types = new ResolvedType[typedConstants.size()];
        LLVMExpressionNode[] constants = new LLVMExpressionNode[(typedConstants.size())];
        for (int i = 0; i < types.length; i++) {
            TypedConstant constant = typedConstants.get(i);
            types[i] = resolve(constant.getType());
            LLVMExpressionNode parsedConstant = visitConstant(constant.getType(), constant.getValue());
            constants[i] = parsedConstant;
        }
        GlobalValueDef ref = structure.getRef();
        if (ref != null) {
            throw new AssertionError();
        }
        ResolvedType structureType = resolve(structure);
        return factoryFacade.createStructureConstantNode(TextToBCConverter.convert(structureType), packed, TextToBCConverter.convertTypes(types), constants);
    }

    private LLVMExpressionNode getUndefinedValueNode(EObject type) {
        LLVMBaseType llvmType = getLLVMType(type).getType();
        if (llvmType != LLVMBaseType.ARRAY && llvmType != LLVMBaseType.STRUCT) {
            return factoryFacade.createUndefinedValue(TextToBCConverter.convert(resolve(type)));
        } else {
            ResolvedType resolvedType = resolve(type);
            int byteSize = typeHelper.getByteSize(resolvedType);
            LLVMExpressionNode alloca = allocateFunctionLifetime(resolvedType);
            return factoryFacade.createEmptyStructLiteralNode(alloca, byteSize);
        }
    }

    private LLVMFunction createLLVMFunctionFromHeader(FunctionHeader header) {
        Type returnType = header.getRettype();
        Parameters parameters = header.getParameters();
        EList<Parameter> params = parameters.getParameters();
        LLVMType llvmReturnType = getLLVMType(returnType);
        boolean varArgs = parameters.getVararg() != null;
        LLVMType[] llvmParamTypes = new LLVMType[params.size()];
        for (int i = 0; i < params.size(); i++) {
            llvmParamTypes[i] = getLLVMType(params.get(i).getType().getType());
        }
        return factoryFacade.createAndRegisterFunctionDescriptor(header.getName(), LLVMTypeHelper.convertType(llvmReturnType), varArgs, LLVMTypeHelper.convertTypes(llvmParamTypes));
    }

    private LLVMExpressionNode parseSimpleConstant(EObject type, SimpleConstant simpleConst) {
        LLVMBaseType instructionType = getLLVMType(type).getType();
        String stringValue = simpleConst.getValue();
        if (instructionType == LLVMBaseType.ARRAY) {
            ResolvedType resolvedType = resolve(type);
            ResolvedType containedType = resolvedType.getContainedType(0);
            LLVMBaseType arrayElementType = LLVMTypeHelper.getLLVMType(containedType).getType();
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
            return factoryFacade.createSimpleConstantNoArray(stringValue, instructionType, TextToBCConverter.convert(resolve(type)));
        }
    }

    private LLVMExpressionNode getReadNode(String name, EObject type) {
        FrameSlot frameSlot = findOrAddFrameSlot(name, type);
        return getReadNodeForSlot(frameSlot, type);
    }

    private FrameSlot findOrAddFrameSlot(String name, EObject obj) {
        ResolvedType type = resolve(obj);
        FrameSlot frameSlot = frameDescriptor.findOrAddFrameSlot(name);
        variableTypes.put(name, TextToBCConverter.convert(type));
        if (frameSlot == null) {
            throw new AssertionError("frame slot is null!");
        }
        if (frameSlot.getKind() == FrameSlotKind.Illegal) {
            frameSlot.setKind(factoryFacade.getFrameSlotKind(TextToBCConverter.convert(type)));
        }
        assert frameSlot.getKind() == factoryFacade.getFrameSlotKind(TextToBCConverter.convert(type));
        return frameSlot;
    }

    private LLVMExpressionNode getReadNodeForSlot(FrameSlot frameSlot, EObject type) {
        LLVMBaseType llvmType = getLLVMType(type).getType();
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
        } else {
            throw new AssertionError(termInstruction);
        }
    }

    private LLVMNode visitIndirectBranch(Instruction_indirectbr instr) {
        EList<BasicBlockRef> destinations = instr.getDestinations();
        int[] labelTargets = new int[destinations.size()];
        for (int i = 0; i < labelTargets.length; i++) {
            labelTargets[i] = getIndexFromBasicBlock(destinations.get(i).getRef());
        }
        LLVMExpressionNode value = visitValueRef(instr.getAddress().getRef(), instr.getAddress().getType());
        return factoryFacade.createIndirectBranch(value, labelTargets, getUnconditionalPhiWriteNodes());
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
        LLVMNode[] phiWriteNodes = getUnconditionalPhiWriteNodes();
        LLVMBaseType llvmType = getLLVMType(switchInstr.getComparisonValue().getType()).getType();
        return factoryFacade.createSwitch(cond, defaultLabel, otherLabels, cases, llvmType, phiWriteNodes);
    }

    private LLVMNode visitBr(Instruction_br brInstruction) {
        TypedValue typedValue = brInstruction.getCondition();
        if (typedValue != null) {
            LLVMExpressionNode conditionNode = visitValueRef(typedValue.getRef(), typedValue.getType());
            BasicBlock trueBasicBlock = brInstruction.getTrue().getRef();
            int trueIndex = getIndexFromBasicBlock(trueBasicBlock);
            BasicBlock falseBasicBlock = brInstruction.getFalse().getRef();
            int falseIndex = getIndexFromBasicBlock(falseBasicBlock);
            List<LLVMNode> trueConditionPhiWriteNodes = new ArrayList<>();
            List<LLVMNode> falseConditionPhiWriteNodes = new ArrayList<>();
            if (!phiRefs.get(currentBasicBlock).isEmpty()) {
                List<Phi> phiValues = phiRefs.get(currentBasicBlock);
                for (Phi phi : phiValues) {
                    FrameSlot phiSlot = findOrAddFrameSlot(phi.getAssignTo(), phi.getType());
                    LLVMExpressionNode phiValueNode = visitValueRef(phi.getValueRef(), phi.getType());
                    boolean isTrueCondition = brInstruction.getTrue().getRef() == phi.getStartingInstr().eContainer();
                    LLVMNode phiWriteNode = getWriteNode(phiValueNode, phiSlot, phi.getType());
                    if (isTrueCondition) {
                        trueConditionPhiWriteNodes.add(phiWriteNode);
                    } else {
                        falseConditionPhiWriteNodes.add(phiWriteNode);
                    }
                }
            }
            LLVMNode[] truePhiWriteNodesArr = trueConditionPhiWriteNodes.toArray(new LLVMNode[trueConditionPhiWriteNodes.size()]);
            LLVMNode[] falsePhiWriteNodesArr = falseConditionPhiWriteNodes.toArray(new LLVMNode[falseConditionPhiWriteNodes.size()]);
            return factoryFacade.createConditionalBranch(trueIndex, falseIndex, conditionNode, truePhiWriteNodesArr, falsePhiWriteNodesArr);
        } else {
            LLVMNode[] unconditionalPhiWriteNodeArr = getUnconditionalPhiWriteNodes();
            BasicBlock unconditional = brInstruction.getUnconditional().getRef();
            int unconditionalIndex = getIndexFromBasicBlock(unconditional);
            return factoryFacade.createUnconditionalBranch(unconditionalIndex, unconditionalPhiWriteNodeArr);
        }
    }

    private LLVMNode[] getUnconditionalPhiWriteNodes() {
        List<LLVMNode> unconditionalPhiWriteNode = new ArrayList<>();
        if (!phiRefs.get(currentBasicBlock).isEmpty()) {
            List<Phi> phiValues = phiRefs.get(currentBasicBlock);
            for (Phi phi : phiValues) {
                FrameSlot phiSlot = findOrAddFrameSlot(phi.getAssignTo(), phi.getType());
                LLVMExpressionNode phiValueNode = visitValueRef(phi.getValueRef(), phi.getType());
                LLVMNode phiWriteNode = getWriteNode(phiValueNode, phiSlot, phi.getType());
                unconditionalPhiWriteNode.add(phiWriteNode);
            }
        }
        LLVMNode[] unconditionalPhiWriteNodeArr = unconditionalPhiWriteNode.toArray(new LLVMNode[unconditionalPhiWriteNode.size()]);
        return unconditionalPhiWriteNodeArr;
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
            retSlot.setKind(factoryFacade.getFrameSlotKind(TextToBCConverter.convert(resolvedType)));
            return factoryFacade.createNonVoidRet(retValue, TextToBCConverter.convert(resolvedType));
        }
    }

    @Override
    public LLVMExpressionNode allocateVectorResult(Object type) {
        ResolvedVectorType vector = (ResolvedVectorType) resolve((EObject) type);
        return allocateFunctionLifetime(vector);
    }

    @Override
    public ResolvedType resolve(EObject e) {
        return typeResolver.resolve(e);
    }

    public LLVMExpressionNode allocateFunctionLifetime(ResolvedType resolvedType) {
        int alignment = typeHelper.getAlignmentByte(resolvedType);
        int size = typeHelper.getByteSize(resolvedType);
        return allocateFunctionLifetime(TextToBCConverter.convert(resolvedType), size, alignment);
    }

    public LLVMExpressionNode allocateFunctionLifetime(int size, ResolvedType resolvedType) {
        int alignment = typeHelper.getAlignmentByte(resolvedType);
        return allocateFunctionLifetime(TextToBCConverter.convert(resolvedType), size, alignment);
    }

    @Override
    public LLVMExpressionNode allocateFunctionLifetime(com.oracle.truffle.llvm.parser.base.model.types.Type type, int size, int alignment) {
        return factoryFacade.createAlloc(type, size, alignment, null, null);
    }

    @Override
    public FrameSlot getReturnSlot() {
        return retSlot;
    }

    @Override
    public Object getGlobalAddress(com.oracle.truffle.llvm.parser.base.model.globals.GlobalValueSymbol var) {
        return findOrAllocateGlobal(var);
    }

    @Override
    public FrameSlot getStackPointerSlot() {
        return stackPointerSlot;
    }

    @Override
    public int getByteAlignment(com.oracle.truffle.llvm.parser.base.model.types.Type type) {
        return type.getAlignment(layoutConverter);
    }

    @Override
    public int getByteSize(com.oracle.truffle.llvm.parser.base.model.types.Type type) {
        return type.getSize(layoutConverter);
    }

    @Override
    public int getBytePadding(int offset, com.oracle.truffle.llvm.parser.base.model.types.Type type) {
        return com.oracle.truffle.llvm.parser.base.model.types.Type.getPadding(offset, type, layoutConverter);
    }

    @Override
    public int getIndexOffset(int index, com.oracle.truffle.llvm.parser.base.model.types.Type type) {
        return type.getIndexOffset(index, layoutConverter);
    }

    @Override
    public FrameDescriptor getGlobalFrameDescriptor() {
        return globalFrameDescriptor;
    }

    @Override
    public FrameDescriptor getMethodFrameDescriptor() {
        return frameDescriptor;
    }

    @Override
    public void addDestructor(LLVMNode destructorNode) {
        globalDeallocations.add(destructorNode);
    }

    @Override
    public long getNativeHandle(String name) {
        return nativeLookup.getNativeHandle(name);
    }

    @Override
    public Map<String, com.oracle.truffle.llvm.parser.base.model.types.Type> getVariableNameTypesMapping() {
        return variableTypes;
    }

    @Override
    public NodeFactoryFacade getNodeFactoryFacade() {
        return factoryFacade;
    }

}
