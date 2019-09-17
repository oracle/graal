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
package org.graalvm.compiler.core.llvm;

import static org.graalvm.compiler.core.llvm.LLVMIRBuilder.isVoidType;
import static org.graalvm.compiler.core.llvm.LLVMIRBuilder.typeOf;
import static org.graalvm.compiler.core.llvm.LLVMUtils.dumpValues;
import static org.graalvm.compiler.debug.GraalError.shouldNotReachHere;
import static org.graalvm.compiler.debug.GraalError.unimplemented;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.bytedeco.javacpp.LLVM.LLVMBasicBlockRef;
import org.bytedeco.javacpp.LLVM.LLVMTypeRef;
import org.bytedeco.javacpp.LLVM.LLVMValueRef;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.gen.DebugInfoBuilder;
import org.graalvm.compiler.core.llvm.LLVMUtils.DebugLevel;
import org.graalvm.compiler.core.llvm.LLVMUtils.LLVMAddressValue;
import org.graalvm.compiler.core.llvm.LLVMUtils.LLVMKind;
import org.graalvm.compiler.core.llvm.LLVMUtils.LLVMValueWrapper;
import org.graalvm.compiler.core.llvm.LLVMUtils.LLVMVariable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.GraalGraphError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.NodeSourcePosition;
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
import org.graalvm.compiler.nodes.extended.SwitchNode;
import org.graalvm.compiler.nodes.java.TypeSwitchNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.NodeValueMap;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;

public abstract class NodeLLVMBuilder implements NodeLIRBuilderTool {
    protected final LLVMGenerator gen;
    protected final LLVMIRBuilder builder;
    private final DebugInfoBuilder debugInfoBuilder;

    private Map<Node, LLVMValueWrapper> valueMap = new HashMap<>();

    private Map<ValuePhiNode, LLVMValueRef> backwardsPhi = new HashMap<>();
    private long startPatchpointID = -1L;

    protected NodeLLVMBuilder(StructuredGraph graph, LLVMGenerator gen, BiFunction<NodeValueMap, DebugContext, DebugInfoBuilder> debugInfoProvider) {
        this.gen = gen;
        this.builder = gen.getBuilder();
        this.debugInfoBuilder = debugInfoProvider.apply(this, graph.getDebug());

        gen.getBuilder().addMainFunction(gen.getLLVMFunctionType(graph.method()));

        for (Block block : graph.getLastSchedule().getCFG().getBlocks()) {
            gen.appendBasicBlock(block);
        }
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
        gen.beginBlock(block);
        if (block == graph.getLastSchedule().getCFG().getStartBlock()) {
            assert block.getPredecessorCount() == 0;

            startPatchpointID = LLVMIRBuilder.nextPatchpointId.getAndIncrement();
            gen.getLLVMResult().setStartPatchpointID(startPatchpointID);
            builder.buildStackmap(builder.constantLong(startPatchpointID));

            for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
                LLVMValueRef paramValue = builder.getParam(getParamIndex(param.index()));
                setResult(param, paramValue);
            }

            gen.allocateRegisterSlots();

            if (gen.getDebugLevel() >= DebugLevel.FUNCTION) {
                gen.indent();
                List<JavaKind> printfTypes = new ArrayList<>();
                List<LLVMValueRef> printfArgs = new ArrayList<>();

                for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
                    printfTypes.add(param.getStackKind());
                    printfArgs.add(llvmOperand(param));
                }

                String functionName = builder.getFunctionName();
                gen.emitPrintf("In " + functionName, printfTypes.toArray(new JavaKind[0]), printfArgs.toArray(new LLVMValueRef[0]));
            }
        } else {
            assert block.getPredecessorCount() > 0;
            // create phi-in value array
            AbstractBeginNode begin = block.getBeginNode();
            if (begin instanceof AbstractMergeNode) {
                AbstractMergeNode merge = (AbstractMergeNode) begin;
                for (ValuePhiNode phiNode : merge.valuePhis()) {
                    List<LLVMValueRef> forwardPhis = new ArrayList<>();
                    List<LLVMBasicBlockRef> forwardBlocks = new ArrayList<>();
                    LLVMTypeRef phiType = getLLVMType(phiNode);

                    boolean hasBackwardIncomingEdges = false;
                    for (Block predecessor : block.getPredecessors()) {
                        if (gen.getLLVMResult().isProcessed(predecessor)) {
                            ValueNode phiValue = phiNode.valueAt((AbstractEndNode) predecessor.getEndNode());
                            LLVMValueRef value = llvmOperand(phiValue);
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
            }
        }

        if (gen.getDebugLevel() >= DebugLevel.BLOCK) {
            gen.emitPrintf("In block " + block.toString());
        }

        for (Node node : blockMap.get(block)) {
            if (node instanceof ValueNode) {
                if (!valueMap.containsKey(node)) {
                    ValueNode valueNode = (ValueNode) node;
                    try {
                        if (gen.getDebugLevel() >= DebugLevel.NODE) {
                            gen.emitPrintf(valueNode.toString());
                        }
                        doRoot(valueNode);
                    } catch (GraalError e) {
                        throw GraalGraphError.transformAndAddContext(e, valueNode);
                    } catch (Throwable e) {
                        throw new GraalGraphError(e).addContext(valueNode);
                    }
                } else {
                    // There can be cases in which the result of an instruction is already set
                    // before by other instructions.
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

        gen.getLLVMResult().setProcessed(block);
    }

    protected abstract int getParamIndex(int index);

    @Override
    public void matchBlock(Block b, StructuredGraph graph, StructuredGraph.ScheduleResult blockMap) {

    }

    private void doRoot(ValueNode instr) {
        DebugContext debug = instr.getDebug();
        debug.log("Visiting %s", instr);
        emitNode(instr);
        debug.log("Operand for %s = %s", instr, operand(instr));
    }

    private void emitNode(ValueNode node) {
        if (node.getDebug().isLogEnabled() && node.stamp(NodeView.DEFAULT).isEmpty()) {
            node.getDebug().log("This node has an empty stamp, we are emitting dead code(?): %s", node);
        }
        setSourcePosition(node.getNodeSourcePosition());
        if (node instanceof LIRLowerable) {
            ((LIRLowerable) node).generate(this);
        } else {
            throw shouldNotReachHere("node is not LIRLowerable: " + node);
        }
    }

    void finish() {
        gen.getLLVMResult().setModule(builder.getModule());
    }

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

    private static int expandProbability(double probability) {
        return (int) (probability * Integer.MAX_VALUE);
    }

    protected LLVMValueRef emitCondition(LogicNode condition) {
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
        throw shouldNotReachHere("logic node: " + condition.getClass().getName());
    }

    @Override
    public void emitConditional(ConditionalNode conditional) {
        Value tVal = operand(conditional.trueValue());
        Value fVal = operand(conditional.falseValue());
        setResult(conditional, emitConditional(conditional.condition(), tVal, fVal));
    }

    private Variable emitConditional(LogicNode node, Value trueValue, Value falseValue) {
        if (node instanceof IsNullNode) {
            IsNullNode isNullNode = (IsNullNode) node;
            return gen.emitIsNullMove(operand(isNullNode.getValue()), trueValue, falseValue);
        }
        if (node instanceof CompareNode) {
            CompareNode compare = (CompareNode) node;
            return gen.emitConditionalMove(null, operand(compare.getX()), operand(compare.getY()), compare.condition().asCondition(), compare.unorderedIsTrue(), trueValue, falseValue);
        }
        if (node instanceof LogicConstantNode) {
            return gen.emitMove(((LogicConstantNode) node).getValue() ? trueValue : falseValue);
        }
        if (node instanceof IntegerTestNode) {
            IntegerTestNode test = (IntegerTestNode) node;
            return gen.emitIntegerTestMove(operand(test.getX()), operand(test.getY()), trueValue, falseValue);
        }
        throw unimplemented(node.toString());
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
                LLVMValueRef hub = gen.emitLLVMConstant(builder.objectType(), (JavaConstant) switchNode.keyAt(0));
                LLVMValueRef cond = builder.buildCompare(Condition.EQ, value, hub, false);
                builder.buildIf(cond, gen.getBlock(switchNode.keySuccessor(0)), defaultSuccessor);
                break;
            default:
                throw unimplemented();
        }
    }

    @Override
    public void emitInvoke(Invoke i) {
        LoweredCallTargetNode callTarget = (LoweredCallTargetNode) i.callTarget();
        ResolvedJavaMethod targetMethod = callTarget.targetMethod();
        NodeInputList<ValueNode> arguments = callTarget.arguments();

        LLVMValueRef callee;
        LLVMValueRef[] args;
        args = getCallArguments(arguments, callTarget.callType(), targetMethod);

        LIRFrameState state = state(i);
        state.initDebugInfo(null, false);
        DebugInfo debugInfo = state.debugInfo();

        boolean isVoid;
        long patchpointId = LLVMIRBuilder.nextPatchpointId.getAndIncrement();
        if (callTarget instanceof DirectCallTargetNode) {
            callee = gen.getFunction(targetMethod);
            isVoid = isVoidType(gen.getLLVMFunctionReturnType(targetMethod));
            gen.getLLVMResult().recordDirectCall(targetMethod, patchpointId, debugInfo);
        } else if (callTarget instanceof IndirectCallTargetNode) {
            LLVMValueRef computedAddress = llvmOperand(((IndirectCallTargetNode) callTarget).computedAddress());
            if (LLVMIRBuilder.isObject(typeOf(computedAddress))) {
                computedAddress = builder.buildPtrToInt(computedAddress, builder.longType());
            }
            if (targetMethod != null) {
                callee = builder.buildIntToPtr(computedAddress,
                                builder.pointerType(gen.getLLVMFunctionType(targetMethod), false));
                isVoid = isVoidType(gen.getLLVMFunctionReturnType(targetMethod));
            } else {
                LLVMTypeRef returnType = gen.getLLVMType(callTarget.returnStamp().getTrustedStamp());
                isVoid = isVoidType(returnType);
                LLVMTypeRef[] argTypes = Arrays.stream(callTarget.signature()).map(argType -> builder.getLLVMStackType(gen.getTypeKind(argType.resolve(null)))).toArray(LLVMTypeRef[]::new);
                assert args.length == argTypes.length;

                callee = builder.buildIntToPtr(computedAddress,
                                builder.pointerType(builder.functionType(returnType, argTypes), false));
            }
            gen.getLLVMResult().recordIndirectCall(targetMethod, patchpointId, debugInfo);

            if (gen.getDebugLevel() >= DebugLevel.NODE) {
                gen.emitPrintf("Indirect call to " + ((targetMethod != null) ? targetMethod.getName() : "[unknown]"), new JavaKind[]{JavaKind.Object}, new LLVMValueRef[]{callee});
            }
        } else {
            throw shouldNotReachHere();
        }

        LLVMValueRef call = emitCall(i, callTarget, callee, patchpointId, args);

        if (!isVoid) {
            setResult(i.asNode(), call);
        }
    }

    @SuppressWarnings("unused")
    protected LLVMValueRef emitCall(Invoke invoke, LoweredCallTargetNode callTarget, LLVMValueRef callee, long patchpointId, LLVMValueRef... args) {
        LLVMValueRef call;
        if (invoke instanceof InvokeWithExceptionNode) {
            InvokeWithExceptionNode invokeWithExceptionNode = (InvokeWithExceptionNode) invoke;
            LLVMBasicBlockRef successor = gen.getBlock(invokeWithExceptionNode.next());
            LLVMBasicBlockRef handler = gen.getBlock(invokeWithExceptionNode.exceptionEdge());

            call = builder.buildInvoke(callee, successor, handler, patchpointId, args);
        } else {
            call = builder.buildCall(callee, patchpointId, args);
        }

        return call;
    }

    @SuppressWarnings("unused")
    protected LLVMValueRef[] getCallArguments(NodeInputList<ValueNode> arguments, CallingConvention.Type callType, ResolvedJavaMethod targetMethod) {
        return arguments.stream().map(this::llvmOperand).toArray(LLVMValueRef[]::new);
    }

    @Override
    public void emitReadExceptionObject(ValueNode node) {
        builder.buildLandingPad();

        Register exceptionRegister = gen.getRegisterConfig().getReturnRegister(JavaKind.Object);
        LLVMValueRef exception = builder.buildInlineGetRegister(exceptionRegister.name);
        setResult(node, builder.buildRegisterObject(exception));
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
        for (ValuePhiNode phiNode : i.merge().valuePhis()) {
            LLVMValueRef phi = backwardsPhi.get(phiNode);

            LLVMValueRef value = llvmOperand(phiNode.valueAt(i));
            LLVMValueRef[] values = new LLVMValueRef[]{value};
            builder.addIncoming(phi, values, basicBlocks);
        }
    }

    @Override
    public void visitSafepointNode(SafepointNode i) {
        throw unimplemented();
    }

    @Override
    public void visitBreakpointNode(BreakpointNode i) {
        if (gen.getDebugLevel() >= DebugLevel.FUNCTION) {
            gen.emitPrintf("breakpoint");
        }
        builder.buildDebugtrap();
    }

    @Override
    public void visitFullInfopointNode(FullInfopointNode i) {
        throw unimplemented();
    }

    @Override
    public void setSourcePosition(NodeSourcePosition position) {
    }

    @Override
    public LIRFrameState state(DeoptimizingNode deopt) {
        if (!deopt.canDeoptimize()) {
            return null;
        }

        if (gen.needOnlyOopMaps()) {
            return new LIRFrameState(null, null, null);
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
        return debugInfoBuilder.build(state, null);
    }

    @Override
    public void emitOverflowCheckBranch(AbstractBeginNode overflowSuccessor, AbstractBeginNode next, Stamp compareStamp, double probability) {
        throw unimplemented();
    }

    @Override
    public Value[] visitInvokeArguments(CallingConvention cc, Collection<ValueNode> arguments) {
        throw unimplemented();
    }

    @Override
    public Value operand(Node node) {
        return (Value) valueMap.get(node);
    }

    protected LLVMValueRef llvmOperand(Node node) {
        assert valueMap.containsKey(node);
        return valueMap.get(node).get();
    }

    @Override
    public boolean hasOperand(Node node) {
        return valueMap.containsKey(node);
    }

    protected void setResult(ValueNode node, LLVMValueRef operand) {
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
            LLVMValueRef base = LLVMUtils.getVal(addressValue.getBase());
            Value index = addressValue.getIndex();

            LLVMTypeRef baseType = typeOf(base);
            if (LLVMIRBuilder.isIntegerType(baseType) && LLVMIRBuilder.integerTypeWidth(baseType) == JavaKind.Long.getBitCount()) {
                base = builder.buildIntToPtr(base, builder.rawPointerType());
            } else if (LLVMIRBuilder.isObject(baseType)) {
                typeOverride = true;
            } else {
                throw shouldNotReachHere(dumpValues("unsupported base for address", base));
            }

            LLVMValueRef intermediate;
            if (index == null || index == Value.ILLEGAL) {
                intermediate = base;
            } else {
                intermediate = builder.buildGEP(base, LLVMUtils.getVal(index));
            }

            llvmOperand = new LLVMVariable(intermediate);
        } else if (operand instanceof RegisterValue) {
            RegisterValue registerValue = (RegisterValue) operand;
            llvmOperand = (LLVMVariable) gen.emitReadRegister(registerValue.getRegister(), registerValue.getValueKind());
        } else {
            throw shouldNotReachHere("unknown operand: " + operand.toString());
        }

        assert typeOverride || LLVMIRBuilder.compatibleTypes(getLLVMType(node), typeOf(llvmOperand.get())) : dumpValues(
                        "value type doesn't match node stamp (" + node.stamp(NodeView.DEFAULT).toString() + ")", llvmOperand.get());

        if (gen.getDebugLevel() >= DebugLevel.NODE && node.getStackKind() != JavaKind.Void) {
            builder.setValueName(llvmOperand.get(), node.toString());
        }
        valueMap.put(node, llvmOperand);
        return operand;
    }

    @Override
    public ValueNode valueForOperand(Value value) {
        throw unimplemented();
    }
}
