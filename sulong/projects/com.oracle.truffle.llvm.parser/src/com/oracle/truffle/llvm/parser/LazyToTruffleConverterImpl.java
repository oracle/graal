/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInterface;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.LLVMLivenessAnalysis.LLVMLivenessAnalysisResult;
import com.oracle.truffle.llvm.parser.LLVMPhiManager.Phi;
import com.oracle.truffle.llvm.parser.metadata.debuginfo.DebugInfoFunctionProcessor;
import com.oracle.truffle.llvm.parser.metadata.debuginfo.SourceVariable;
import com.oracle.truffle.llvm.parser.model.attributes.Attribute;
import com.oracle.truffle.llvm.parser.model.attributes.Attribute.Kind;
import com.oracle.truffle.llvm.parser.model.attributes.Attribute.KnownAttribute;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.model.functions.LazyFunctionParser;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.nodes.LLVMBitcodeInstructionVisitor;
import com.oracle.truffle.llvm.parser.nodes.LLVMRuntimeDebugInformation;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolReadResolver;
import com.oracle.truffle.llvm.runtime.GetStackSpaceFactory;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor.LazyToTruffleConverter;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceFunctionType;
import com.oracle.truffle.llvm.runtime.except.LLVMUserException;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.UniquesRegion;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.base.LLVMBasicBlockNode;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;

public class LazyToTruffleConverterImpl implements LazyToTruffleConverter {
    private final LLVMParserRuntime runtime;
    private final FunctionDefinition method;
    private final Source source;
    private final LazyFunctionParser parser;
    private final DebugInfoFunctionProcessor diProcessor;
    private final DataLayout dataLayout;

    private RootCallTarget resolved;

    LazyToTruffleConverterImpl(LLVMParserRuntime runtime, FunctionDefinition method, Source source, LazyFunctionParser parser,
                    DebugInfoFunctionProcessor diProcessor, DataLayout dataLayout) {
        this.runtime = runtime;
        this.method = method;
        this.source = source;
        this.parser = parser;
        this.diProcessor = diProcessor;
        this.resolved = null;
        this.dataLayout = dataLayout;
    }

    @Override
    public RootCallTarget convert() {
        CompilerAsserts.neverPartOfCompilation();

        synchronized (this) {
            if (resolved == null) {
                resolved = generateCallTarget();
            }
            return resolved;
        }
    }

    private RootCallTarget generateCallTarget() {
        LLVMContext context = runtime.getContext();
        NodeFactory nodeFactory = runtime.getNodeFactory();
        OptionValues options = context.getEnv().getOptions();

        // parse the function block
        parser.parse(diProcessor, source, runtime);

        String printASTOption = options.get(SulongEngineOption.PRINT_AST);
        boolean printAST = false;
        if (!printASTOption.isEmpty()) {
            String[] regexes = printASTOption.split(",");
            for (String regex : regexes) {
                if (method.getName().matches(regex)) {
                    printAST = true;
                    System.out.println("\n========== " + method.getName() + "\n");
                    break;
                }
            }
        }

        // prepare the phis
        final Map<InstructionBlock, List<Phi>> phis = LLVMPhiManager.getPhis(method);

        // setup the uniquesRegion
        UniquesRegion uniquesRegion = new UniquesRegion();
        GetStackSpaceFactory getStackSpaceFactory = GetStackSpaceFactory.createGetUniqueStackSpaceFactory(uniquesRegion);

        LLVMLivenessAnalysisResult liveness = LLVMLivenessAnalysis.computeLiveness(phis, method, runtime.getContext().lifetimeAnalysisStream());

        // setup the frameDescriptor
        final FrameDescriptor frame = StackManager.createFrame(method);

        LLVMSymbolReadResolver symbols = new LLVMSymbolReadResolver(runtime, frame, getStackSpaceFactory, dataLayout);

        HashSet<Integer> notNullable = new HashSet<>();
        LLVMRuntimeDebugInformation dbgInfoHandler = new LLVMRuntimeDebugInformation(frame, context, notNullable, symbols);
        dbgInfoHandler.registerStaticDebugSymbols(method);

        // create blocks and translate instructions
        boolean initDebugValues = dbgInfoHandler.isEnabled();
        List<LLVMBasicBlockNode> blockNodes = new ArrayList<>();
        for (InstructionBlock block : method.getBlocks()) {
            List<Phi> blockPhis = phis.get(block);
            ArrayList<LLVMLivenessAnalysis.NullerInformation> blockNullerInfos = liveness.getNullableWithinBlock()[block.getBlockIndex()];
            LLVMBitcodeInstructionVisitor visitor = LLVMBitcodeInstructionVisitor.create(frame, uniquesRegion, blockPhis, method.getParameters().size(), symbols, context, runtime.getLibrary(),
                            blockNullerInfos, notNullable, dbgInfoHandler, dataLayout, nodeFactory);

            if (initDebugValues) {
                for (SourceVariable variable : method.getSourceFunction().getVariables()) {
                    final LLVMStatementNode initNode = dbgInfoHandler.createInitializer(variable);
                    if (initNode != null) {
                        visitor.addStatementUnchecked(initNode);
                    }
                }
                initDebugValues = false;
            }

            for (int i = 0; i < block.getInstructionCount(); i++) {
                Instruction instruction = block.getInstruction(i);
                visitor.setInstructionIndex(i);
                instruction.accept(visitor);
            }
            LLVMStatementNode[] nodes = visitor.finish();
            blockNodes.add(LLVMBasicBlockNode.createBasicBlockNode(options, nodes, visitor.getControlFlowNode(), block.getBlockIndex(), block.getName()));
        }

        for (int j = 0; j < blockNodes.size(); j++) {
            FrameSlot[] nullableBeforeBlock = getNullableFrameSlots(frame, liveness.getNullableBeforeBlock()[j]);
            FrameSlot[] nullableAfterBlock = getNullableFrameSlots(frame, liveness.getNullableAfterBlock()[j]);
            blockNodes.get(j).setNullableFrameSlots(nullableBeforeBlock, nullableAfterBlock);
        }

        LLVMSourceLocation location = method.getLexicalScope();

        LLVMStatementNode[] copyArgumentsToFrameArray = copyArgumentsToFrame(frame).toArray(LLVMStatementNode.NO_STATEMENTS);
        LLVMExpressionNode body = nodeFactory.createFunctionBlockNode(frame.findFrameSlot(LLVMUserException.FRAME_SLOT_ID), blockNodes, uniquesRegion.build(), copyArgumentsToFrameArray, location,
                        frame);

        RootNode rootNode = nodeFactory.createFunctionStartNode(body, frame, method.getName(), method.getSourceName(), method.getParameters().size(), source, location);
        method.onAfterParse();

        if (printAST) {
            printCompactTree(rootNode);
            System.out.println();
        }

        return Truffle.getRuntime().createCallTarget(rootNode);
    }

    public static void printCompactTree(Node node) {
        printCompactTree(new PrintWriter(System.out), null, node, null, 1);
    }

    private static void printCompactTree(PrintWriter p, NodeInterface parent, NodeInterface node, String fieldName, int level) {
        if (node == null) {
            return;
        }
        for (int i = 0; i < level; i++) {
            p.print("  ");
        }
        if (parent == null) {
            p.println(node);
        } else {
            p.print(fieldName);
            p.print(" = ");
            p.println(node);
        }

        for (Class<?> c = node.getClass(); c != Object.class; c = c.getSuperclass()) {
            Field[] fields = c.getDeclaredFields();
            for (Field field : fields) {
                if (NodeInterface.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        NodeInterface value = (NodeInterface) field.get(node);
                        if (value != null) {
                            printCompactTree(p, node, value, field.getName(), level + 1);
                        }
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        e.printStackTrace();
                    }
                } else if (NodeInterface[].class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        NodeInterface[] value = (NodeInterface[]) field.get(node);
                        if (value != null) {
                            for (int i = 0; i < value.length; i++) {
                                printCompactTree(p, node, value[i], field.getName() + "[" + i + "]", level + 1);
                            }
                        }
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        p.flush();
    }

    @Override
    public LLVMSourceFunctionType getSourceType() {
        convert();
        return method.getSourceFunction().getSourceType();
    }

    private static FrameSlot[] getNullableFrameSlots(FrameDescriptor frame, BitSet nullable) {
        int bitIndex = -1;

        ArrayList<FrameSlot> nullableSlots = new ArrayList<>();
        while ((bitIndex = nullable.nextSetBit(bitIndex + 1)) >= 0) {
            int frameIdentifier = bitIndex;
            nullableSlots.add(LLVMBitcodeInstructionVisitor.findFrameSlot(frame, frameIdentifier));
        }
        if (nullableSlots.size() > 0) {
            return nullableSlots.toArray(new FrameSlot[nullableSlots.size()]);
        } else {
            return null;
        }
    }

    private List<LLVMStatementNode> copyArgumentsToFrame(FrameDescriptor frame) {
        List<FunctionParameter> parameters = method.getParameters();
        List<LLVMStatementNode> formalParamInits = new ArrayList<>();
        LLVMExpressionNode stackPointerNode = runtime.getNodeFactory().createFunctionArgNode(0, PrimitiveType.I64);
        formalParamInits.add(runtime.getNodeFactory().createFrameWrite(PointerType.VOID, stackPointerNode, frame.findFrameSlot(LLVMStack.FRAME_ID)));

        int argIndex = 1;
        if (method.getType().getReturnType() instanceof StructureType) {
            argIndex++;
        }
        for (FunctionParameter parameter : parameters) {
            LLVMExpressionNode parameterNode = runtime.getNodeFactory().createFunctionArgNode(argIndex++, parameter.getType());
            FrameSlot slot = LLVMSymbolReadResolver.findOrAddFrameSlot(frame, parameter);
            if (isStructByValue(parameter)) {
                Type type = ((PointerType) parameter.getType()).getPointeeType();
                formalParamInits.add(
                                runtime.getNodeFactory().createFrameWrite(parameter.getType(),
                                                runtime.getNodeFactory().createCopyStructByValue(type, GetStackSpaceFactory.createAllocaFactory(), parameterNode), slot));
            } else {
                formalParamInits.add(runtime.getNodeFactory().createFrameWrite(parameter.getType(), parameterNode, slot));
            }
        }
        return formalParamInits;
    }

    private static boolean isStructByValue(FunctionParameter parameter) {
        if (parameter.getType() instanceof PointerType && parameter.getParameterAttribute() != null) {
            for (Attribute a : parameter.getParameterAttribute().getAttributes()) {
                if (a instanceof KnownAttribute && ((KnownAttribute) a).getAttr() == Kind.BYVAL) {
                    return true;
                }
            }
        }
        return false;
    }
}
