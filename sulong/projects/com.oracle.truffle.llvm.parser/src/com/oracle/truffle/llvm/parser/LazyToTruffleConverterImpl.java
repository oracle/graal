/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInterface;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.LLVMLivenessAnalysis.LLVMLivenessAnalysisResult;
import com.oracle.truffle.llvm.parser.LLVMPhiManager.Phi;
import com.oracle.truffle.llvm.parser.metadata.debuginfo.DebugInfoFunctionProcessor;
import com.oracle.truffle.llvm.parser.metadata.debuginfo.SourceVariable;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.attributes.Attribute;
import com.oracle.truffle.llvm.parser.model.attributes.Attribute.Kind;
import com.oracle.truffle.llvm.parser.model.attributes.Attribute.KnownAttribute;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.model.functions.LazyFunctionParser;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DbgDeclareInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DbgValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.nodes.LLVMBitcodeInstructionVisitor;
import com.oracle.truffle.llvm.parser.nodes.LLVMRuntimeDebugInformation;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolReadResolver;
import com.oracle.truffle.llvm.parser.util.LLVMControlFlowGraph;
import com.oracle.truffle.llvm.parser.util.LLVMControlFlowGraph.CFGBlock;
import com.oracle.truffle.llvm.parser.util.LLVMControlFlowGraph.CFGLoop;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.GetStackSpaceFactory;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode.LazyToTruffleConverter;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceFunctionType;
import com.oracle.truffle.llvm.runtime.except.LLVMUserException;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.UniquesRegion;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.base.LLVMBasicBlockNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMUnpackVarargsNodeGen;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.symbols.SSAValue;

public class LazyToTruffleConverterImpl implements LazyToTruffleConverter {

    private static final String LOOP_SUCCESSOR_FRAME_ID = "<loop successor>";

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

        // parse the function block
        parser.parse(diProcessor, source, runtime);

        // prepare the phis
        final Map<InstructionBlock, List<Phi>> phis = LLVMPhiManager.getPhis(method);

        // setup the uniquesRegion
        UniquesRegion uniquesRegion = new UniquesRegion();
        GetStackSpaceFactory getStackSpaceFactory = GetStackSpaceFactory.createGetUniqueStackSpaceFactory(uniquesRegion);

        LLVMLivenessAnalysisResult liveness = LLVMLivenessAnalysis.computeLiveness(phis, method, runtime.getContext().lifetimeAnalysisStream());

        // setup the frameDescriptor
        FrameDescriptor frame = new FrameDescriptor();
        LLVMSymbolReadResolver symbols = new LLVMSymbolReadResolver(runtime, frame, getStackSpaceFactory, dataLayout, options.get(SulongEngineOption.LL_DEBUG));

        frame.addFrameSlot(LLVMUserException.FRAME_SLOT_ID, null, FrameSlotKind.Object);
        frame.addFrameSlot(LLVMStack.FRAME_ID, PointerType.VOID, FrameSlotKind.Object);

        for (FunctionParameter parameter : method.getParameters()) {
            symbols.findOrAddFrameSlot(frame, parameter);
        }

        HashSet<Integer> neededForDebug = getDebugValues();

        // create blocks and translate instructions
        boolean initDebugValues = true;
        LLVMRuntimeDebugInformation info = new LLVMRuntimeDebugInformation(method.getBlocks().size());
        LLVMBasicBlockNode[] blockNodes = new LLVMBasicBlockNode[method.getBlocks().size()];
        for (InstructionBlock block : method.getBlocks()) {
            List<Phi> blockPhis = phis.get(block);
            ArrayList<LLVMLivenessAnalysis.NullerInformation> blockNullerInfos = liveness.getNullableWithinBlock()[block.getBlockIndex()];
            LLVMBitcodeInstructionVisitor visitor = new LLVMBitcodeInstructionVisitor(frame, uniquesRegion, blockPhis, method.getParameters().size(), symbols, context, runtime.getLibrary(),
                            blockNullerInfos, neededForDebug, dataLayout, nodeFactory);

            if (initDebugValues) {
                for (SourceVariable variable : method.getSourceFunction().getVariables()) {
                    if (variable.hasFragments()) {
                        visitor.initializeAggregateLocalVariable(variable);
                    }
                }
                initDebugValues = false;
            }

            for (int i = 0; i < block.getInstructionCount(); i++) {
                visitor.setInstructionIndex(i);
                block.getInstruction(i).accept(visitor);
            }
            LLVMStatementNode[] nodes = visitor.finish();
            info.setBlockDebugInfo(block.getBlockIndex(), visitor.getDebugInfo());
            blockNodes[block.getBlockIndex()] = LLVMBasicBlockNode.createBasicBlockNode(options, nodes, visitor.getControlFlowNode(), block.getBlockIndex(), block.getName());
        }

        for (int j = 0; j < blockNodes.length; j++) {
            FrameSlot[] nullableBeforeBlock = getNullableFrameSlots(frame, liveness.getNullableBeforeBlock()[j]);
            FrameSlot[] nullableAfterBlock = getNullableFrameSlots(frame, liveness.getNullableAfterBlock()[j]);
            blockNodes[j].setNullableFrameSlots(nullableBeforeBlock, nullableAfterBlock);
        }
        info.setBlocks(blockNodes);

        FrameSlot loopSuccessorSlot = null;
        if (context.getEnv().getOptions().get(SulongEngineOption.ENABLE_OSR)) {
            LLVMControlFlowGraph cfg = new LLVMControlFlowGraph(method.getBlocks().toArray(FunctionDefinition.EMPTY));
            cfg.build();

            if (cfg.isReducible() && cfg.getCFGLoops().size() > 0) {
                loopSuccessorSlot = frame.addFrameSlot(LOOP_SUCCESSOR_FRAME_ID, FrameSlotKind.Int);
                resolveLoops(blockNodes, cfg, frame, loopSuccessorSlot, info, options);
            }
        }

        LLVMSourceLocation location = method.getLexicalScope();
        LLVMStatementNode[] copyArgumentsToFrameArray = copyArgumentsToFrame(frame, symbols).toArray(LLVMStatementNode.NO_STATEMENTS);
        LLVMExpressionNode body = nodeFactory.createFunctionBlockNode(frame.findFrameSlot(LLVMUserException.FRAME_SLOT_ID), blockNodes, uniquesRegion.build(), copyArgumentsToFrameArray, location,
                        frame, loopSuccessorSlot, info);

        RootNode rootNode = nodeFactory.createFunctionStartNode(body, frame, method.getName(), method.getSourceName(), method.getParameters().size(), source, location);
        method.onAfterParse();

        if (printAST) {
            printCompactTree(rootNode);
            System.out.println();
        }

        return Truffle.getRuntime().createCallTarget(rootNode);
    }

    private HashSet<Integer> getDebugValues() {
        HashSet<Integer> neededForDebug = new HashSet<>();
        for (InstructionBlock block : method.getBlocks()) {
            for (Instruction instruction : block.getInstructions()) {
                SymbolImpl value;
                if (instruction instanceof DbgValueInstruction) {
                    value = ((DbgValueInstruction) instruction).getValue();
                } else if (instruction instanceof DbgDeclareInstruction) {
                    value = ((DbgDeclareInstruction) instruction).getValue();
                } else {
                    continue;
                }
                if (value instanceof SSAValue) {
                    neededForDebug.add(((SSAValue) value).getFrameIdentifier());
                }
            }
        }
        return neededForDebug;
    }

    private static void printCompactTree(Node node) {
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
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
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

    private void resolveLoops(LLVMBasicBlockNode[] nodes, LLVMControlFlowGraph cfg, FrameDescriptor frame, FrameSlot loopSuccessorSlot, LLVMRuntimeDebugInformation info, OptionValues options) {
        // The original array is needed to access the frame nuller information for outgoing control
        // flow egdes
        LLVMBasicBlockNode[] originalBodyNodes = nodes.clone();
        info.setBlocks(originalBodyNodes);
        for (CFGLoop loop : cfg.getCFGLoops()) {
            int headerId = loop.getHeader().id;
            int[] indexMapping = new int[nodes.length];
            Arrays.fill(indexMapping, -1);
            List<LLVMStatementNode> bodyNodes = new ArrayList<>();
            // add header to body nodes
            LLVMBasicBlockNode header = nodes[headerId];
            bodyNodes.add(header);
            indexMapping[headerId] = 0;
            // add body nodes
            int i = 1;
            for (CFGBlock block : loop.getBody()) {
                bodyNodes.add(nodes[block.id]);
                indexMapping[block.id] = i++;
            }
            int[] loopSuccessors = loop.getSuccessorIDs();
            RepeatingNode loopBody = runtime.getNodeFactory().createLoopDispatchNode(frame.findFrameSlot(LLVMUserException.FRAME_SLOT_ID), Collections.unmodifiableList(bodyNodes),
                            originalBodyNodes, headerId, indexMapping, loopSuccessors, loopSuccessorSlot);
            LLVMControlFlowNode loopNode = runtime.getNodeFactory().createLoop(loopBody, loopSuccessors);
            // replace header block with loop node
            nodes[headerId] = LLVMBasicBlockNode.createBasicBlockNode(options, new LLVMStatementNode[0], loopNode, headerId, "loopAt" + headerId);
            nodes[headerId].setNullableFrameSlots(header.nullableBefore, header.nullableAfter);
            // remove inner loops to reduce number of nodes
            for (CFGLoop innerLoop : loop.getInnerLoops()) {
                nodes[innerLoop.getHeader().id] = null;
            }
        }
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

    /**
     * True when the function parameter has an LLVM byval attribute attached to it. This usually is
     * the case for value parameters (e.g. struct Point p) which the compiler decides to pass
     * through a pointer instead (by creating a copy sometime between the caller and the callee and
     * passing a pointer to that copy). In bitcode the copy's pointer is then tagged with a byval
     * attribute.
     */
    private static boolean functionParameterHasByValueAttribute(FunctionParameter parameter) {
        if (parameter.getParameterAttribute() != null) {
            for (Attribute a : parameter.getParameterAttribute().getAttributes()) {
                if (a instanceof KnownAttribute && ((KnownAttribute) a).getAttr() == Kind.BYVAL) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Create an expression node that, when executed, will provide an address where the respective
     * argument should either be copied from or copied into. This is important when passing struct
     * arguments by value, this node uses getelementptr to infer the location from the source types
     * into the destination frame slot.
     *
     * @param baseAddress Base address from which to calculate the offsets.
     * @param sourceType Type to index into.
     * @param indices List of indices to reach a member or element from the base address.
     */
    private LLVMExpressionNode getTargetAddress(LLVMExpressionNode baseAddress, Type sourceType, ArrayDeque<Long> indices) {
        NodeFactory nf = runtime.getNodeFactory();

        int indicesSize = indices.size();
        Long[] indicesArr = new Long[indicesSize];
        LLVMExpressionNode[] indexNodes = new LLVMExpressionNode[indicesSize];

        int i = indicesSize - 1;
        for (Long idx : indices) {
            indicesArr[i] = idx;
            indexNodes[i] = nf.createLiteral(idx.longValue(), PrimitiveType.I64);
            i--;
        }
        assert i == -1;

        PrimitiveType[] indexTypes = new PrimitiveType[indicesSize];
        Arrays.fill(indexTypes, PrimitiveType.I64);

        LLVMExpressionNode nestedGEPs = CommonNodeFactory.createNestedElementPointerNode(
                        nf,
                        dataLayout,
                        indexNodes,
                        indicesArr,
                        indexTypes,
                        baseAddress,
                        sourceType);

        return nestedGEPs;
    }

    /**
     * This function creates a list of statements that, together, copy a value of type
     * {@code topLevelPointerType} from argument {@code argIndex} to the frame slot {@code
     * slot} by recursing over the structure of {@code topLevelPointerType}.
     *
     * The recursive cases go over the elements (for arrays) and over the members (for structs). The
     * base case is for everything else ("primitives"), where cascades of getelementptrs are created
     * for reading from the source and writing into the target frame slot. The cascades of
     * getelementptr nodes are created by the calls to
     * {@link LazyToTruffleConverterImpl#getTargetAddress} and then used to load or store from the
     * source and into the destination respectively.
     *
     * @param initializers The accumulator where copy statements are going to be inserted.
     * @param slot Target frame slot.
     * @param currentType Current member (for structs) or element (for arrays) type.
     * @param indices List of indices to reach this member or element from the toplevel object.
     */
    private void copyStructArgumentsToFrame(List<LLVMStatementNode> initializers, NodeFactory nodeFactory, FrameSlot slot, int argIndex, PointerType topLevelPointerType, Type currentType,
                    ArrayDeque<Long> indices) {
        if (currentType instanceof StructureType || currentType instanceof ArrayType) {
            AggregateType t = (AggregateType) currentType;

            for (long i = 0; i < t.getNumberOfElements(); i++) {
                indices.push(i);
                copyStructArgumentsToFrame(initializers, nodeFactory, slot, argIndex, topLevelPointerType, t.getElementType(i), indices);
                indices.pop();
            }
        } else {
            LLVMExpressionNode targetAddress = getTargetAddress(CommonNodeFactory.createFrameRead(topLevelPointerType, slot), topLevelPointerType.getPointeeType(), indices);
            /*
             * In case the source is a varargs list (va_list), we need to create a node that would
             * unpack it if it is, and do nothing if it isn't.
             */
            LLVMExpressionNode argMaybeUnpack = LLVMUnpackVarargsNodeGen.create(nodeFactory.createFunctionArgNode(argIndex, topLevelPointerType));
            LLVMExpressionNode sourceAddress = getTargetAddress(argMaybeUnpack, topLevelPointerType.getPointeeType(), indices);
            LLVMExpressionNode sourceLoadNode = CommonNodeFactory.createLoad(currentType, sourceAddress);
            LLVMStatementNode storeNode = nodeFactory.createStore(targetAddress, sourceLoadNode, currentType);
            initializers.add(storeNode);
        }
    }

    /**
     * Copies arguments to the current frame, handling normal "primitives" and byval pointers (e.g.
     * for structs).
     */
    private List<LLVMStatementNode> copyArgumentsToFrame(FrameDescriptor frame, LLVMSymbolReadResolver symbols) {
        NodeFactory nodeFactory = runtime.getNodeFactory();
        List<FunctionParameter> parameters = method.getParameters();
        List<LLVMStatementNode> formalParamInits = new ArrayList<>();
        LLVMExpressionNode stackPointerNode = nodeFactory.createFunctionArgNode(0, PrimitiveType.I64);
        formalParamInits.add(nodeFactory.createFrameWrite(PointerType.VOID, stackPointerNode, frame.findFrameSlot(LLVMStack.FRAME_ID)));

        // There's a struct return type.
        int argIndex = 1;
        if (method.getType().getReturnType() instanceof StructureType) {
            argIndex++;
        }

        for (FunctionParameter parameter : parameters) {
            FrameSlot slot = symbols.findOrAddFrameSlot(frame, parameter);

            if (parameter.getType() instanceof PointerType && functionParameterHasByValueAttribute(parameter)) {
                // It's a struct passed as a pointer but originally passed by value (because LLVM
                // and/or ABI), treat it as such.
                PointerType pointerType = (PointerType) parameter.getType();
                Type pointeeType = pointerType.getPointeeType();
                GetStackSpaceFactory allocaFactory = GetStackSpaceFactory.createAllocaFactory();
                LLVMExpressionNode allocation = allocaFactory.createGetStackSpace(nodeFactory, pointeeType);

                formalParamInits.add(nodeFactory.createFrameWrite(pointerType, allocation, slot));

                ArrayDeque<Long> indices = new ArrayDeque<>();
                copyStructArgumentsToFrame(formalParamInits, nodeFactory, slot, argIndex++, pointerType, pointeeType, indices);
            } else {
                LLVMExpressionNode parameterNode = nodeFactory.createFunctionArgNode(argIndex++, parameter.getType());
                formalParamInits.add(nodeFactory.createFrameWrite(parameter.getType(), parameterNode, slot));
            }
        }

        return formalParamInits;
    }
}
