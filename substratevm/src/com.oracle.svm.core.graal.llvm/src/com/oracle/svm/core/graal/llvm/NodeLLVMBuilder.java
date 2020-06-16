/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.graal.llvm;

import static org.graalvm.compiler.debug.GraalError.shouldNotReachHere;
import static org.graalvm.compiler.debug.GraalError.unimplemented;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.gen.DebugInfoBuilder;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.GraalGraphError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BreakpointNode;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.DirectCallTargetNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.FullInfopointNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.IndirectCallTargetNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoweredCallTargetNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.SafepointNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.IntegerTestNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.extended.ForeignCall;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.ForeignCallWithExceptionNode;
import org.graalvm.compiler.nodes.extended.SwitchNode;
import org.graalvm.compiler.nodes.java.TypeSwitchNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.graal.code.CGlobalDataReference;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.code.SubstrateDebugInfoBuilder;
import com.oracle.svm.core.graal.code.SubstrateNodeLIRBuilder;
import com.oracle.svm.core.graal.llvm.LLVMGenerator.SpecialRegister;
import com.oracle.svm.core.graal.llvm.lowering.LLVMAddressLowering.LLVMAddressValue;
import com.oracle.svm.core.graal.llvm.runtime.LLVMExceptionUnwind;
import com.oracle.svm.core.graal.llvm.util.LLVMIRBuilder;
import com.oracle.svm.core.graal.llvm.util.LLVMOptions;
import com.oracle.svm.core.graal.llvm.util.LLVMUtils;
import com.oracle.svm.core.graal.llvm.util.LLVMUtils.LLVMKind;
import com.oracle.svm.core.graal.llvm.util.LLVMUtils.LLVMPendingSpecialRegisterRead;
import com.oracle.svm.core.graal.llvm.util.LLVMUtils.LLVMValueWrapper;
import com.oracle.svm.core.graal.llvm.util.LLVMUtils.LLVMVariable;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.nodes.CGlobalDataLoadAddressNode;
import com.oracle.svm.core.nodes.SafepointCheckNode;
import com.oracle.svm.core.thread.Safepoint;
import com.oracle.svm.core.thread.ThreadingSupportImpl;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMTypeRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMValueRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.global.LLVM;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;

public class NodeLLVMBuilder implements NodeLIRBuilderTool, SubstrateNodeLIRBuilder {
    private final LLVMGenerator gen;
    private final LLVMIRBuilder builder;
    private final RuntimeConfiguration runtimeConfiguration;
    private final DebugInfoBuilder debugInfoBuilder;

    private Map<Node, LLVMValueWrapper> valueMap = new HashMap<>();
    private final Set<AbstractBlockBase<?>> processedBlocks = new HashSet<>();
    private Map<ValuePhiNode, LLVMValueRef> backwardsPhi = new HashMap<>();
    private long nextCGlobalId = 0L;

    protected NodeLLVMBuilder(StructuredGraph graph, LLVMGenerator gen, RuntimeConfiguration runtimeConfiguration) {
        this.gen = gen;
        this.builder = gen.getBuilder();
        this.runtimeConfiguration = runtimeConfiguration;
        this.debugInfoBuilder = new SubstrateDebugInfoBuilder(graph, gen.getProviders().getMetaAccessExtensionProvider(), this);
        setCompilationResultMethod(gen.getCompilationResult(), graph);

        for (Block block : graph.getLastSchedule().getCFG().getBlocks()) {
            gen.appendBasicBlock(block);
        }
    }

    private static void setCompilationResultMethod(CompilationResult result, StructuredGraph graph) {
        Assumptions assumptions = graph.getAssumptions();
        if (assumptions != null && !assumptions.isEmpty()) {
            result.setAssumptions(assumptions.toArray());
        }

        ResolvedJavaMethod rootMethod = graph.method();
        if (rootMethod != null) {
            result.setMethods(rootMethod, graph.getMethods());
            result.setFields(graph.getFields());
        }

        result.setHasUnsafeAccess(graph.hasUnsafeAccess());
    }

    @Override
    public LLVMGenerator getLIRGeneratorTool() {
        return gen;
    }

    private LLVMTypeRef getLLVMType(ValueNode node) {
        return gen.getLLVMType(node.stamp(NodeView.DEFAULT));
    }

    @Override
    public void doBlock(Block block, StructuredGraph graph, BlockMap<List<Node>> blockMap) {
        assert !processedBlocks.contains(block) : "Block already processed " + block;
        assert verifyPredecessors(block);

        gen.beginBlock(block);
        if (block == graph.getLastSchedule().getCFG().getStartBlock()) {
            assert block.getPredecessorCount() == 0;

            long startPatchpointID = LLVMGenerator.nextPatchpointId.getAndIncrement();
            builder.buildStackmap(builder.constantLong(startPatchpointID));
            gen.getCompilationResult().recordInfopoint(NumUtil.safeToInt(startPatchpointID), null, InfopointReason.METHOD_START);

            for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
                int offset = (gen.isEntryPoint() ? 0 : SpecialRegister.count());
                LLVMValueRef paramValue = builder.getFunctionParam(param.index() + offset);
                setResult(param, paramValue);
            }

            if (LLVMOptions.ReturnSpecialRegs.getValue()) {
                for (SpecialRegister reg : SpecialRegister.registers()) {
                    gen.setInitialSpecialRegisterValue(reg, gen.isEntryPoint() ? builder.constantNull(builder.wordType()) : builder.getFunctionParam(reg.getIndex()));
                }
            } else {
                gen.allocateRegisterSlots();
            }

            gen.getDebugInfoPrinter().printFunction(graph, this);
        } else {
            assert block.getPredecessorCount() > 0;
            // create phi-in value array
            AbstractBeginNode begin = block.getBeginNode();
            if (begin instanceof AbstractMergeNode) {
                AbstractMergeNode merge = (AbstractMergeNode) begin;

                if (LLVMOptions.ReturnSpecialRegs.getValue()) {
                    for (SpecialRegister reg : SpecialRegister.registers()) {
                        List<LLVMValueRef> forwardPredValues = new ArrayList<>();
                        List<LLVMBasicBlockRef> forwardBlocks = new ArrayList<>();
                        for (Block predecessor : block.getPredecessors()) {
                            if (processedBlocks.contains(predecessor)) {
                                forwardPredValues.add(block.isExceptionEntry() ? gen.getHandlerSpecialRegisterValue(reg, predecessor) : gen.getSpecialRegisterValue(reg, predecessor));
                                forwardBlocks.add(gen.getBlockEnd(predecessor));
                            }
                        }
                        LLVMValueRef registerPhi = builder.buildPhi(builder.wordType(), forwardPredValues.toArray(new LLVMValueRef[0]), forwardBlocks.toArray(new LLVMBasicBlockRef[0]));
                        gen.setInitialSpecialRegisterValue(reg, registerPhi);
                    }
                }

                for (ValuePhiNode phiNode : merge.valuePhis()) {
                    List<LLVMValueRef> forwardPhis = new ArrayList<>();
                    List<LLVMBasicBlockRef> forwardBlocks = new ArrayList<>();
                    LLVMTypeRef phiType = getLLVMType(phiNode);

                    boolean hasBackwardIncomingEdges = false;
                    for (Block predecessor : block.getPredecessors()) {
                        if (processedBlocks.contains(predecessor)) {
                            ValueNode phiValue = phiNode.valueAt((AbstractEndNode) predecessor.getEndNode());
                            LLVMValueRef value;
                            if (operand(phiValue) instanceof LLVMPendingSpecialRegisterRead) {
                                /*
                                 * The pending read may need to perform instructions to load the
                                 * value, so we put them at the end of the predecessor block
                                 */
                                Block currentBlock = (Block) gen.getCurrentBlock();
                                gen.editBlock(predecessor);
                                value = llvmOperand(phiValue);
                                gen.resumeBlock(currentBlock);
                            } else {
                                value = llvmOperand(phiValue);
                            }
                            LLVMBasicBlockRef parentBlock = gen.getBlockEnd(predecessor);

                            forwardPhis.add(value);
                            forwardBlocks.add(parentBlock);
                        } else {
                            hasBackwardIncomingEdges = true;
                        }
                    }

                    LLVMValueRef[] incomingValues = forwardPhis.toArray(new LLVMValueRef[0]);
                    LLVMBasicBlockRef[] incomingBlocks = forwardBlocks.toArray(new LLVMBasicBlockRef[0]);
                    LLVMValueRef phi = builder.buildPhi(phiType, incomingValues, incomingBlocks);

                    if (hasBackwardIncomingEdges) {
                        backwardsPhi.put(phiNode, phi);
                    }

                    setResult(phiNode, phi);
                }
            } else if (LLVMOptions.ReturnSpecialRegs.getValue()) {
                assert block.getPredecessorCount() == 1;
                Block predecessor = block.getFirstPredecessor();
                for (SpecialRegister reg : SpecialRegister.registers()) {
                    gen.setInitialSpecialRegisterValue(reg, block.isExceptionEntry() ? gen.getHandlerSpecialRegisterValue(reg, predecessor) : gen.getSpecialRegisterValue(reg, predecessor));
                }
            }
        }

        gen.getDebugInfoPrinter().printBlock(block);

        for (Node node : blockMap.get(block)) {
            if (node instanceof ValueNode) {
                /*
                 * There can be cases in which the result of an instruction is already set before by
                 * other instructions.
                 */
                if (!valueMap.containsKey(node)) {
                    ValueNode valueNode = (ValueNode) node;
                    try {
                        gen.getDebugInfoPrinter().printNode(valueNode);
                        emitNode(valueNode);
                    } catch (GraalError e) {
                        throw GraalGraphError.transformAndAddContext(e, valueNode);
                    } catch (Throwable e) {
                        throw new GraalGraphError(e).addContext(valueNode);
                    }
                }
            }
        }

        if (builder.blockTerminator(gen.getBlockEnd(block)) == null) {
            NodeIterable<Node> successors = block.getEndNode().successors();
            assert successors.count() == block.getSuccessorCount();
            if (block.getSuccessorCount() != 1) {
                /*
                 * If we have more than one successor, we cannot just use the first one. Since
                 * successors are unordered, this would be a random choice.
                 */
                throw new GraalError("Block without BlockEndOp: " + block.getEndNode());
            }
            builder.buildBranch(gen.getBlock(block.getFirstSuccessor()));
        }

        processedBlocks.add(block);
    }

    private boolean verifyPredecessors(Block block) {
        for (Block pred : block.getPredecessors()) {
            assert block.isLoopHeader() && pred.isLoopEnd() || processedBlocks.contains(pred) : "Predecessor not yet processed " + pred;
        }
        return true;
    }

    private void emitNode(ValueNode node) {
        DebugContext debug = node.getDebug();
        debug.log("Visiting %s", node);
        if (node.getDebug().isLogEnabled() && node.stamp(NodeView.DEFAULT).isEmpty()) {
            node.getDebug().log("This node has an empty stamp, we are emitting dead code(?): %s", node);
        }
        if (node instanceof LIRLowerable) {
            ((LIRLowerable) node).generate(this);
        } else {
            throw shouldNotReachHere("node is not LIRLowerable: " + node);
        }
        debug.log("Operand for %s = %s", node, operand(node));
    }

    void finish() {
        cGlobals.forEach((symbolName, reference) -> gen.getCompilationResult().recordDataPatchWithNote(0, reference, symbolName));
    }

    /* Control flow nodes */

    @Override
    public void emitIf(IfNode i) {
        LLVMValueRef condition = emitCondition(i.condition());
        LLVMBasicBlockRef thenBlock = gen.getBlock(i.trueSuccessor());
        LLVMBasicBlockRef elseBlock = gen.getBlock(i.falseSuccessor());
        LLVMValueRef instr = builder.buildIf(condition, thenBlock, elseBlock);

        int trueProbability = expandProbability(i.getTrueSuccessorProbability());
        int falseProbability = expandProbability(1 - i.getTrueSuccessorProbability());
        LLVMValueRef branchWeights = builder.branchWeights(builder.constantInt(trueProbability), builder.constantInt(falseProbability));
        builder.setMetadata(instr, "prof", branchWeights);
    }

    private LLVMValueRef emitCondition(LogicNode condition) {
        if (condition instanceof IsNullNode) {
            return builder.buildIsNull(llvmOperand(((IsNullNode) condition).getValue()));
        }
        if (condition instanceof LogicConstantNode) {
            return builder.constantBoolean(((LogicConstantNode) condition).getValue());
        }
        if (condition instanceof CompareNode) {
            CompareNode compareNode = (CompareNode) condition;
            return builder.buildCompare(compareNode.condition().asCondition(), llvmOperand(compareNode.getX()), llvmOperand(compareNode.getY()), compareNode.unorderedIsTrue());
        }
        if (condition instanceof IntegerTestNode) {
            IntegerTestNode integerTestNode = (IntegerTestNode) condition;
            LLVMValueRef and = builder.buildAnd(llvmOperand(integerTestNode.getX()), llvmOperand(integerTestNode.getY()));
            return builder.buildIsNull(and);
        }
        if (condition instanceof SafepointCheckNode) {
            LLVMValueRef threadData = gen.getSpecialRegister(SpecialRegister.ThreadPointer);
            threadData = builder.buildIntToPtr(threadData, builder.rawPointerType());
            LLVMValueRef safepointCounterAddr = builder.buildGEP(threadData, builder.constantInt(Math.toIntExact(Safepoint.getThreadLocalSafepointRequestedOffset())));
            LLVMValueRef safepointCount = builder.buildLoad(safepointCounterAddr, builder.intType());
            if (ThreadingSupportImpl.isRecurringCallbackSupported()) {
                safepointCount = builder.buildSub(safepointCount, builder.constantInt(1));
                builder.buildStore(safepointCount, builder.buildBitcast(safepointCounterAddr, builder.pointerType(builder.intType())));
            }
            return builder.buildICmp(Condition.LE, safepointCount, builder.constantInt(0));
        }
        throw shouldNotReachHere("logic node: " + condition.getClass().getName());
    }

    @Override
    public void emitConditional(ConditionalNode conditional) {
        Value trueValue = operand(conditional.trueValue());
        Value falseValue = operand(conditional.falseValue());
        LogicNode condition = conditional.condition();

        Variable conditionalValue;
        if (condition instanceof IsNullNode) {
            IsNullNode isNullNode = (IsNullNode) condition;
            conditionalValue = gen.emitIsNullMove(operand(isNullNode.getValue()), trueValue, falseValue);
        } else if (condition instanceof CompareNode) {
            CompareNode compare = (CompareNode) condition;
            conditionalValue = gen.emitConditionalMove(null, operand(compare.getX()), operand(compare.getY()), compare.condition().asCondition(), compare.unorderedIsTrue(), trueValue, falseValue);
        } else if (condition instanceof LogicConstantNode) {
            conditionalValue = gen.emitMove(((LogicConstantNode) condition).getValue() ? trueValue : falseValue);
        } else if (condition instanceof IntegerTestNode) {
            IntegerTestNode test = (IntegerTestNode) condition;
            conditionalValue = gen.emitIntegerTestMove(operand(test.getX()), operand(test.getY()), trueValue, falseValue);
        } else {
            throw unimplemented(condition.toString());
        }
        setResult(conditional, conditionalValue);
    }

    @Override
    public void emitSwitch(SwitchNode switchNode) {
        if (switchNode instanceof TypeSwitchNode) {
            emitTypeSwitch((TypeSwitchNode) switchNode);
            return;
        }

        int numCases = switchNode.keyCount();
        LLVMValueRef[] values = new LLVMValueRef[numCases];
        LLVMBasicBlockRef[] blocks = new LLVMBasicBlockRef[numCases];
        LLVMValueRef[] weights = new LLVMValueRef[numCases + 1];
        int defaultProbability = expandProbability(switchNode.probability(switchNode.defaultSuccessor()));
        weights[0] = builder.constantInt(defaultProbability);

        for (int i = 0; i < numCases; ++i) {
            JavaConstant key = (JavaConstant) switchNode.keyAt(i);
            values[i] = builder.constantInt(key.asInt());
            blocks[i] = gen.getBlock(switchNode.keySuccessor(i));
            int keyProbability = expandProbability(switchNode.probability(switchNode.keySuccessor(i)));
            weights[i + 1] = builder.constantInt(keyProbability);
        }

        LLVMValueRef switchInstr = builder.buildSwitch(llvmOperand(switchNode.value()), gen.getBlock(switchNode.defaultSuccessor()), values, blocks);

        LLVMValueRef branchWeights = builder.branchWeights(weights);
        builder.setMetadata(switchInstr, "prof", branchWeights);
    }

    private void emitTypeSwitch(TypeSwitchNode switchNode) {
        int numCases = switchNode.keyCount();
        LLVMValueRef value = llvmOperand(switchNode.value());
        LLVMBasicBlockRef defaultSuccessor = gen.getBlock(switchNode.defaultSuccessor());
        switch (numCases) {
            case 0:
                builder.buildBranch(defaultSuccessor);
                break;
            case 1:
                LLVMValueRef hub = gen.emitLLVMConstant(builder.objectType(false), (JavaConstant) switchNode.keyAt(0));
                LLVMValueRef cond = builder.buildCompare(Condition.EQ, value, hub, false);
                builder.buildIf(cond, gen.getBlock(switchNode.keySuccessor(0)), defaultSuccessor);
                break;
            default:
                throw unimplemented();
        }
    }

    private static int expandProbability(double probability) {
        return (int) (probability * Integer.MAX_VALUE);
    }

    @Override
    public void visitMerge(AbstractMergeNode i) {
        /* Handled in doBlock */
    }

    @Override
    public void visitEndNode(AbstractEndNode i) {
        LLVMBasicBlockRef nextBlock = gen.getBlock(i.merge());
        builder.buildBranch(nextBlock);
    }

    @Override
    public void visitLoopEnd(LoopEndNode i) {
        LLVMBasicBlockRef[] basicBlocks = new LLVMBasicBlockRef[]{gen.getBlockEnd((Block) gen.getCurrentBlock())};

        if (LLVMOptions.ReturnSpecialRegs.getValue()) {
            assert gen.getCurrentBlock().getSuccessorCount() == 1;
            for (SpecialRegister reg : SpecialRegister.registers()) {
                Block successor = ((Block) gen.getCurrentBlock()).getFirstSuccessor();
                LLVMValueRef phi = gen.getInitialSpecialRegisterValue(reg, successor);
                assert LLVM.LLVMGetInstructionOpcode(phi) == LLVM.LLVMPHI;
                builder.addIncoming(phi, new LLVMValueRef[]{gen.getSpecialRegisterValue(reg)}, basicBlocks);
            }
        }

        for (ValuePhiNode phiNode : i.merge().valuePhis()) {
            LLVMValueRef phi = backwardsPhi.get(phiNode);

            LLVMValueRef value = llvmOperand(phiNode.valueAt(i));
            LLVMValueRef[] values = new LLVMValueRef[]{value};
            builder.addIncoming(phi, values, basicBlocks);
        }
    }

    /* Invoke */

    @Override
    public void emitInvoke(Invoke i) {
        LoweredCallTargetNode callTarget = (LoweredCallTargetNode) i.callTarget();
        ResolvedJavaMethod targetMethod = callTarget.targetMethod();
        NodeInputList<ValueNode> arguments = callTarget.arguments();
        LIRFrameState state = state(i);
        state.initDebugInfo(null, false);
        DebugInfo debugInfo = state.debugInfo();

        LLVMValueRef callee;
        boolean isVoid;
        LLVMValueRef[] args = getCallArguments(arguments, callTarget.callType(), targetMethod);
        long patchpointId = LLVMGenerator.nextPatchpointId.getAndIncrement();
        if (callTarget instanceof DirectCallTargetNode) {
            callee = gen.getFunction(targetMethod);
            isVoid = gen.isVoidReturnType(gen.getLLVMFunctionReturnType(targetMethod, false));
            gen.getCompilationResult().recordCall(NumUtil.safeToInt(patchpointId), 0, targetMethod, debugInfo, true);
        } else if (callTarget instanceof IndirectCallTargetNode) {
            LLVMValueRef computedAddress = llvmOperand(((IndirectCallTargetNode) callTarget).computedAddress());
            LLVMTypeRef functionType;
            if (targetMethod != null) {
                functionType = gen.getLLVMFunctionPointerType(targetMethod);
                isVoid = gen.isVoidReturnType(gen.getLLVMFunctionReturnType(targetMethod, false));
            } else {
                LLVMTypeRef returnType = getUnknownCallReturnType(callTarget);
                isVoid = gen.isVoidReturnType(returnType);
                LLVMTypeRef[] argTypes = getUnknownCallArgumentTypes(callTarget);
                assert args.length == argTypes.length;
                functionType = builder.functionPointerType(returnType, argTypes);
            }

            if (LLVMIRBuilder.isObjectType(LLVMIRBuilder.typeOf(computedAddress))) {
                callee = builder.buildBitcast(builder.buildAddrSpaceCast(computedAddress, builder.rawPointerType()), functionType);
            } else {
                callee = builder.buildIntToPtr(computedAddress, functionType);
            }
            gen.getCompilationResult().recordCall(NumUtil.safeToInt(patchpointId), 0, targetMethod, debugInfo, false);

            gen.getDebugInfoPrinter().printIndirectCall(targetMethod, callee);
        } else {
            throw shouldNotReachHere();
        }

        LLVMValueRef call = emitCall(i, callTarget, callee, patchpointId, args);

        if (!isVoid) {
            setResult(i.asNode(), call);
        }
    }

    @Override
    public void emitForeignCall(ForeignCall i) {
        ForeignCallLinkage linkage = gen.getForeignCalls().lookupForeignCall(i.getDescriptor());
        LIRFrameState state = state(i);
        Value[] args = i.operands(this);

        Value result = null;
        if (i instanceof ForeignCallNode) {
            result = gen.emitForeignCall(linkage, state, args);
        } else if (i instanceof ForeignCallWithExceptionNode) {
            ForeignCallWithExceptionNode foreignCallWithExceptionNode = (ForeignCallWithExceptionNode) i;
            LLVMBasicBlockRef successor = gen.getBlock(foreignCallWithExceptionNode.next());
            LLVMBasicBlockRef handler = gen.getBlock(foreignCallWithExceptionNode.exceptionEdge());
            result = gen.emitForeignCall(linkage, state, successor, handler, args);
        } else {
            throw shouldNotReachHere();
        }

        if (result != null) {
            setResult(i.asNode(), result);
        }
    }

    private LLVMValueRef emitCall(Invoke invoke, LoweredCallTargetNode callTarget, LLVMValueRef callee, long patchpointId, LLVMValueRef... args) {
        boolean nativeABI = ((SubstrateCallingConventionType) callTarget.callType()).nativeABI;
        if (!SubstrateBackend.hasJavaFrameAnchor(callTarget)) {
            assert SubstrateBackend.getNewThreadStatus(callTarget) == VMThreads.StatusSupport.STATUS_ILLEGAL;
            return emitCallInstruction(invoke, nativeABI, callee, patchpointId, args);
        }
        assert VMThreads.StatusSupport.isValidStatus(SubstrateBackend.getNewThreadStatus(callTarget));

        LLVMValueRef anchor = llvmOperand(SubstrateBackend.getJavaFrameAnchor(callTarget));
        anchor = builder.buildIntToPtr(anchor, builder.rawPointerType());

        LLVMValueRef lastSPAddr = builder.buildGEP(anchor, builder.constantInt(runtimeConfiguration.getJavaFrameAnchorLastSPOffset()));
        Register stackPointer = gen.getRegisterConfig().getFrameRegister();
        builder.buildStore(builder.buildReadRegister(builder.register(stackPointer.name)), builder.buildBitcast(lastSPAddr, builder.pointerType(builder.wordType())));

        if (SubstrateOptions.MultiThreaded.getValue()) {
            LLVMValueRef threadLocalArea = gen.getSpecialRegister(SpecialRegister.ThreadPointer);
            LLVMValueRef statusIndex = builder.constantInt(runtimeConfiguration.getVMThreadStatusOffset());
            LLVMValueRef statusAddress = builder.buildGEP(builder.buildIntToPtr(threadLocalArea, builder.rawPointerType()), statusIndex);
            LLVMValueRef newThreadStatus = builder.constantInt(SubstrateBackend.getNewThreadStatus(callTarget));
            builder.buildVolatileStore(newThreadStatus, builder.buildBitcast(statusAddress, builder.pointerType(builder.intType())), Integer.BYTES);
        }

        LLVMValueRef wrapper = gen.createJNIWrapper(callee, nativeABI, args.length, runtimeConfiguration.getJavaFrameAnchorLastIPOffset());

        LLVMValueRef[] newArgs = new LLVMValueRef[args.length + 2];
        if (!nativeABI) {
            System.arraycopy(args, 0, newArgs, 0, SpecialRegister.count());
            newArgs[SpecialRegister.count() + 0] = anchor;
            newArgs[SpecialRegister.count() + 1] = callee;
            System.arraycopy(args, SpecialRegister.count(), newArgs, 2 + SpecialRegister.count(), args.length - SpecialRegister.count());
        } else {
            newArgs[0] = anchor;
            newArgs[1] = callee;
            System.arraycopy(args, 0, newArgs, 2, args.length);
        }
        return emitCallInstruction(invoke, nativeABI, wrapper, patchpointId, newArgs);
    }

    private LLVMValueRef emitCallInstruction(Invoke invoke, boolean nativeABI, LLVMValueRef callee, long patchpointId, LLVMValueRef... args) {
        LLVMValueRef call;
        if (invoke instanceof InvokeWithExceptionNode) {
            InvokeWithExceptionNode invokeWithExceptionNode = (InvokeWithExceptionNode) invoke;
            LLVMBasicBlockRef successor = gen.getBlock(invokeWithExceptionNode.next());
            LLVMBasicBlockRef handler = gen.getBlock(invokeWithExceptionNode.exceptionEdge());

            call = gen.buildStatepointInvoke(callee, nativeABI, successor, handler, patchpointId, args);
        } else {
            call = gen.buildStatepointCall(callee, nativeABI, patchpointId, args);
        }

        return call;
    }

    private LLVMValueRef[] getCallArguments(NodeInputList<ValueNode> arguments, CallingConvention.Type callType, ResolvedJavaMethod targetMethod) {
        LLVMValueRef[] args = arguments.stream().map(this::llvmOperand).toArray(LLVMValueRef[]::new);
        return gen.getCallArguments(args, callType, targetMethod);
    }

    private LLVMTypeRef getUnknownCallReturnType(LoweredCallTargetNode callTarget) {
        LLVMTypeRef retType = gen.getLLVMType(callTarget.returnStamp().getTrustedStamp());
        if (!((SubstrateCallingConventionType) callTarget.callType()).nativeABI && LLVMOptions.ReturnSpecialRegs.getValue()) {
            boolean voidReturnType = LLVMIRBuilder.isVoidType(retType);
            LLVMTypeRef[] returnTypes = new LLVMTypeRef[SpecialRegister.count() + (voidReturnType ? 0 : 1)];
            for (SpecialRegister reg : SpecialRegister.registers()) {
                returnTypes[reg.getIndex()] = builder.wordType();
            }
            if (!voidReturnType) {
                returnTypes[SpecialRegister.count()] = retType;
            }
            retType = builder.structType(returnTypes);
        }
        return retType;
    }

    private LLVMTypeRef[] getUnknownCallArgumentTypes(LoweredCallTargetNode callTarget) {
        LLVMTypeRef[] types = Arrays.stream(callTarget.signature()).map(argType -> gen.getLLVMStackType(gen.getTypeKind(argType.resolve(null), false))).toArray(LLVMTypeRef[]::new);
        return gen.getUnknownCallArgumentTypes(types, callTarget.callType());
    }

    /* Other nodes */

    @Override
    public void emitReadExceptionObject(ValueNode node) {
        if (!LLVMOptions.ReturnSpecialRegs.getValue()) {
            builder.buildLandingPad();
        }

        LLVMValueRef retrieveExceptionFunction = gen.getFunction(LLVMExceptionUnwind.getRetrieveExceptionMethod(gen.getMetaAccess()));
        LLVMValueRef[] arguments = gen.getCallArguments(new LLVMValueRef[0], SubstrateCallingConventionType.JavaCall, null);
        LLVMValueRef exception = gen.buildStatepointCall(retrieveExceptionFunction, false, LLVMGenerator.nextPatchpointId.getAndIncrement(), arguments);
        setResult(node, exception);
    }

    @Override
    public void visitBreakpointNode(BreakpointNode i) {
        gen.getDebugInfoPrinter().printBreakpoint();
        builder.buildDebugtrap();
    }

    private final Map<String, CGlobalDataReference> cGlobals = new HashMap<>();

    @Override
    public void emitCGlobalDataLoadAddress(CGlobalDataLoadAddressNode node) {
        CGlobalDataInfo dataInfo = node.getDataInfo();

        String symbolName = (dataInfo.getData().symbolName != null) ? dataInfo.getData().symbolName : "global_" + gen.getFunctionName() + "#" + nextCGlobalId++;
        CGlobalDataReference reference = new CGlobalDataReference(dataInfo);
        if (cGlobals.containsKey(symbolName)) {
            /*
             * This global was defined both as a symbol name and a defined value. We only register
             * the defined value as it contains all the necessary information.
             */
            assert reference.getDataInfo().isSymbolReference() != cGlobals.get(symbolName).getDataInfo().isSymbolReference();
            if (!reference.getDataInfo().isSymbolReference()) {
                cGlobals.put(symbolName, reference);
            }
        } else {
            cGlobals.put(symbolName, reference);
        }

        setResult(node, builder.buildPtrToInt(builder.getExternalSymbol(symbolName)));
    }

    @Override
    public Variable emitReadReturnAddress() {
        LLVMValueRef returnAddress = builder.buildReturnAddress(builder.constantInt(0));
        return new LLVMVariable(returnAddress);
    }

    @Override
    public LIRFrameState state(DeoptimizingNode deopt) {
        if (!deopt.canDeoptimize()) {
            return null;
        }

        FrameState state;
        if (deopt instanceof DeoptimizingNode.DeoptBefore) {
            assert !(deopt instanceof DeoptimizingNode.DeoptDuring || deopt instanceof DeoptimizingNode.DeoptAfter);
            state = ((DeoptimizingNode.DeoptBefore) deopt).stateBefore();
        } else if (deopt instanceof DeoptimizingNode.DeoptDuring) {
            assert !(deopt instanceof DeoptimizingNode.DeoptAfter);
            state = ((DeoptimizingNode.DeoptDuring) deopt).stateDuring();
        } else {
            assert deopt instanceof DeoptimizingNode.DeoptAfter;
            state = ((DeoptimizingNode.DeoptAfter) deopt).stateAfter();
        }
        assert state != null;
        return debugInfoBuilder.build(deopt, state, null);
    }

    /* Unsupported */

    @Override
    public void visitSafepointNode(SafepointNode i) {
        throw unimplemented("the LLVM backend doesn't support deoptimization");
    }

    @Override
    public void visitFullInfopointNode(FullInfopointNode i) {
        throw unimplemented("the LLVM backend doesn't support debug info generation");
    }

    @Override
    public void emitOverflowCheckBranch(AbstractBeginNode overflowSuccessor, AbstractBeginNode next, Stamp compareStamp, double probability) {
        throw unimplemented("the LLVM backend doesn't support deoptimization");
    }

    /* Value map */

    @Override
    public Value operand(Node node) {
        return (Value) valueMap.get(node);
    }

    private LLVMValueRef llvmOperand(Node node) {
        assert hasOperand(node);
        return valueMap.get(node).get();
    }

    @Override
    public boolean hasOperand(Node node) {
        return valueMap.containsKey(node);
    }

    private void setResult(ValueNode node, LLVMValueRef operand) {
        setResult(node, new LLVMVariable(operand));
    }

    @Override
    public Value setResult(ValueNode node, Value operand) {
        LLVMValueWrapper llvmOperand;
        boolean typeOverride = false;
        if (operand instanceof LLVMValueWrapper) {
            llvmOperand = (LLVMValueWrapper) operand;
        } else if (operand instanceof ConstantValue) {
            /* This only happens when emitting null object constants */
            llvmOperand = new LLVMVariable(builder.constantNull(((LLVMKind) operand.getPlatformKind()).get()));
        } else if (operand instanceof LLVMAddressValue) {
            LLVMAddressValue addressValue = (LLVMAddressValue) operand;
            Value wrappedBase = addressValue.getBase();
            Value index = addressValue.getIndex();

            if (wrappedBase instanceof LLVMPendingSpecialRegisterRead) {
                LLVMPendingSpecialRegisterRead pendingRead = (LLVMPendingSpecialRegisterRead) wrappedBase;
                if (index != null && index != Value.ILLEGAL) {
                    pendingRead = new LLVMPendingSpecialRegisterRead(pendingRead, LLVMUtils.getVal(addressValue.getIndex()));
                }
                llvmOperand = pendingRead;
            } else {
                LLVMValueRef base = LLVMUtils.getVal(wrappedBase);
                LLVMTypeRef baseType = LLVMIRBuilder.typeOf(base);
                if (LLVMIRBuilder.isWordType(baseType)) {
                    base = builder.buildIntToPtr(base, builder.rawPointerType());
                } else if (LLVMIRBuilder.isObjectType(baseType)) {
                    typeOverride = true;
                } else {
                    throw shouldNotReachHere(LLVMUtils.dumpValues("unsupported base for address", base));
                }

                LLVMValueRef intermediate;
                if (index == null || index == Value.ILLEGAL) {
                    intermediate = base;
                } else {
                    intermediate = builder.buildGEP(base, LLVMUtils.getVal(index));
                }

                llvmOperand = new LLVMVariable(intermediate);
            }
        } else if (operand instanceof RegisterValue) {
            RegisterValue registerValue = (RegisterValue) operand;
            llvmOperand = (LLVMValueWrapper) gen.emitReadRegister(registerValue.getRegister(), registerValue.getValueKind());
        } else {
            throw shouldNotReachHere("unknown operand: " + operand.toString());
        }

        assert typeOverride || LLVMIRBuilder.compatibleTypes(getLLVMType(node), LLVMIRBuilder.typeOf(llvmOperand.get())) : LLVMUtils.dumpValues(
                        "value type doesn't match node stamp (" + node.stamp(NodeView.DEFAULT).toString() + ")", llvmOperand.get());

        gen.getDebugInfoPrinter().setValueName(llvmOperand, node);
        valueMap.put(node, llvmOperand);
        return operand;
    }

    @Override
    public ValueNode valueForOperand(Value value) {
        throw unimplemented();
    }
}
