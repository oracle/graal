/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.compiler.gen;

import static com.oracle.graal.compiler.common.BackendOptions.ConstructionSSAlirDuringLirBuilding;
import static com.oracle.graal.compiler.common.GraalOptions.MatchExpressions;
import static com.oracle.graal.debug.GraalDebugConfig.Options.LogVerbose;
import static com.oracle.graal.lir.LIR.verifyBlock;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isLegal;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

import com.oracle.graal.compiler.common.BackendOptions;
import com.oracle.graal.compiler.common.calc.Condition;
import com.oracle.graal.compiler.common.cfg.BlockMap;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.match.ComplexMatchValue;
import com.oracle.graal.compiler.match.MatchRuleRegistry;
import com.oracle.graal.compiler.match.MatchStatement;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.TTY;
import com.oracle.graal.graph.GraalGraphJVMCIError;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClassIterable;
import com.oracle.graal.graph.NodeMap;
import com.oracle.graal.lir.FullInfopointOp;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LabelRef;
import com.oracle.graal.lir.SimpleInfopointOp;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.SwitchStrategy;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.debug.LIRGenerationDebugContext;
import com.oracle.graal.lir.gen.LIRGenerator.Options;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.lir.gen.LIRGeneratorTool.BlockScope;
import com.oracle.graal.lir.gen.PhiResolver;
import com.oracle.graal.nodes.AbstractBeginNode;
import com.oracle.graal.nodes.AbstractEndNode;
import com.oracle.graal.nodes.AbstractMergeNode;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.DeoptimizingNode;
import com.oracle.graal.nodes.DirectCallTargetNode;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.FullInfopointNode;
import com.oracle.graal.nodes.IfNode;
import com.oracle.graal.nodes.IndirectCallTargetNode;
import com.oracle.graal.nodes.Invoke;
import com.oracle.graal.nodes.InvokeWithExceptionNode;
import com.oracle.graal.nodes.LogicConstantNode;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.LoopEndNode;
import com.oracle.graal.nodes.LoweredCallTargetNode;
import com.oracle.graal.nodes.ParameterNode;
import com.oracle.graal.nodes.PhiNode;
import com.oracle.graal.nodes.SimpleInfopointNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.ValuePhiNode;
import com.oracle.graal.nodes.calc.CompareNode;
import com.oracle.graal.nodes.calc.ConditionalNode;
import com.oracle.graal.nodes.calc.IntegerTestNode;
import com.oracle.graal.nodes.calc.IsNullNode;
import com.oracle.graal.nodes.cfg.Block;
import com.oracle.graal.nodes.cfg.ControlFlowGraph;
import com.oracle.graal.nodes.extended.IntegerSwitchNode;
import com.oracle.graal.nodes.extended.SwitchNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import com.oracle.graal.nodes.spi.NodeValueMap;
import com.oracle.graal.nodes.virtual.VirtualObjectNode;

/**
 * This class traverses the HIR instructions and generates LIR instructions from them.
 */
public abstract class NodeLIRBuilder implements NodeLIRBuilderTool, LIRGenerationDebugContext {

    private final boolean allowObjectConstantToStackMove;
    private final NodeMap<Value> nodeOperands;
    private final DebugInfoBuilder debugInfoBuilder;

    protected final LIRGeneratorTool gen;

    private ValueNode currentInstruction;
    private ValueNode lastInstructionPrinted; // Debugging only

    private final NodeMatchRules nodeMatchRules;
    private Map<Class<? extends Node>, List<MatchStatement>> matchRules;

    public NodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool gen, NodeMatchRules nodeMatchRules) {
        this.gen = gen;
        this.nodeMatchRules = nodeMatchRules;
        this.nodeOperands = graph.createNodeMap();
        this.debugInfoBuilder = createDebugInfoBuilder(graph, this);
        if (MatchExpressions.getValue()) {
            matchRules = MatchRuleRegistry.lookup(nodeMatchRules.getClass());
        }

        assert nodeMatchRules.lirBuilder == null;
        nodeMatchRules.lirBuilder = this;
        allowObjectConstantToStackMove = BackendOptions.UserOptions.AllowObjectConstantToStackMove.getValue();
    }

    public NodeMatchRules getNodeMatchRules() {
        return nodeMatchRules;
    }

    @SuppressWarnings({"unused"})
    protected DebugInfoBuilder createDebugInfoBuilder(StructuredGraph graph, NodeValueMap nodeValueMap) {
        return new DebugInfoBuilder(nodeValueMap);
    }

    /**
     * Returns the operand that has been previously initialized by
     * {@link #setResult(ValueNode, Value)} with the result of an instruction. It's a code
     * generation error to ask for the operand of ValueNode that doesn't have one yet.
     *
     * @param node A node that produces a result value.
     */
    @Override
    public Value operand(Node node) {
        Value operand = getOperand(node);
        assert operand != null : String.format("missing operand for %1s", node);
        return operand;
    }

    @Override
    public boolean hasOperand(Node node) {
        return getOperand(node) != null;
    }

    private Value getOperand(Node node) {
        if (nodeOperands == null) {
            return null;
        }
        return nodeOperands.get(node);
    }

    public ValueNode valueForOperand(Value value) {
        assert nodeOperands != null;
        for (Entry<Node, Value> entry : nodeOperands.entries()) {
            if (entry.getValue().equals(value)) {
                return (ValueNode) entry.getKey();
            }
        }
        return null;
    }

    @Override
    public Object getSourceForOperand(Value value) {
        return valueForOperand(value);
    }

    @Override
    public Value setResult(ValueNode x, Value operand) {
        assert (!isRegister(operand) || !gen.attributes(asRegister(operand)).isAllocatable());
        assert nodeOperands != null && (nodeOperands.get(x) == null || nodeOperands.get(x) instanceof ComplexMatchValue) : "operand cannot be set twice";
        assert operand != null && isLegal(operand) : "operand must be legal";
        assert !(x instanceof VirtualObjectNode);
        nodeOperands.set(x, operand);
        return operand;
    }

    /**
     * Used by the {@link MatchStatement} machinery to override the generation LIR for some
     * ValueNodes.
     */
    public void setMatchResult(Node x, Value operand) {
        assert operand.equals(ComplexMatchValue.INTERIOR_MATCH) || operand instanceof ComplexMatchValue;
        assert operand instanceof ComplexMatchValue || x.getUsageCount() == 1 : "interior matches must be single user";
        assert nodeOperands != null && nodeOperands.get(x) == null : "operand cannot be set twice";
        assert !(x instanceof VirtualObjectNode);
        nodeOperands.set(x, operand);
    }

    public LabelRef getLIRBlock(FixedNode b) {
        assert gen.getResult().getLIR().getControlFlowGraph() instanceof ControlFlowGraph;
        Block result = ((ControlFlowGraph) gen.getResult().getLIR().getControlFlowGraph()).blockFor(b);
        int suxIndex = gen.getCurrentBlock().getSuccessors().indexOf(result);
        assert suxIndex != -1 : "Block not in successor list of current block";

        assert gen.getCurrentBlock() instanceof Block;
        return LabelRef.forSuccessor(gen.getResult().getLIR(), gen.getCurrentBlock(), suxIndex);
    }

    public final void append(LIRInstruction op) {
        if (Options.PrintIRWithLIR.getValue() && !TTY.isSuppressed()) {
            if (currentInstruction != null && lastInstructionPrinted != currentInstruction) {
                lastInstructionPrinted = currentInstruction;
                InstructionPrinter ip = new InstructionPrinter(TTY.out());
                ip.printInstructionListing(currentInstruction);
            }
        }
        gen.append(op);
    }

    protected LIRKind getExactPhiKind(PhiNode phi) {
        // TODO (je): maybe turn this into generator-style instead of allocating an ArrayList.
        ArrayList<LIRKind> values = new ArrayList<>(phi.valueCount());
        for (int i = 0; i < phi.valueCount(); i++) {
            ValueNode node = phi.valueAt(i);
            Value value = getOperand(node);
            if (value != null) {
                values.add(value.getLIRKind());
            } else {
                assert isPhiInputFromBackedge(phi, i) : String.format("Input %s to phi node %s is not yet available although it is not coming from a loop back edge", node, phi);
                // non-java constant -> get LIRKind from stamp.
                LIRKind kind = gen.getLIRKind(node.stamp());
                values.add(gen.toRegisterKind(kind));
            }
        }
        LIRKind derivedKind = LIRKind.merge(values);
        assert verifyPHIKind(derivedKind, gen.getLIRKind(phi.stamp()));
        return derivedKind;
    }

    private boolean verifyPHIKind(LIRKind derivedKind, LIRKind phiKind) {
        PlatformKind derivedPlatformKind = derivedKind.getPlatformKind();
        PlatformKind phiPlatformKind = gen.toRegisterKind(phiKind).getPlatformKind();
        assert derivedPlatformKind.equals(phiPlatformKind) : "kinds don't match: " + derivedPlatformKind + " vs " + phiPlatformKind;
        return true;
    }

    private static boolean isPhiInputFromBackedge(PhiNode phi, int index) {
        AbstractMergeNode merge = phi.merge();
        AbstractEndNode end = merge.phiPredecessorAt(index);
        return end instanceof LoopEndNode && ((LoopEndNode) end).loopBegin().equals(merge);
    }

    private Value[] createPhiIn(AbstractMergeNode merge) {
        List<Value> values = new ArrayList<>();
        for (ValuePhiNode phi : merge.valuePhis()) {
            assert getOperand(phi) == null;
            Variable value = gen.newVariable(getExactPhiKind(phi));
            values.add(value);
            setResult(phi, value);
        }
        return values.toArray(new Value[values.size()]);
    }

    private Value[] createPhiOut(AbstractMergeNode merge, AbstractEndNode pred) {
        List<Value> values = new ArrayList<>();
        for (PhiNode phi : merge.valuePhis()) {
            ValueNode node = phi.valueAt(pred);
            Value value = operand(node);
            assert value != null;
            if (isRegister(value)) {
                /*
                 * Fixed register intervals are not allowed at block boundaries so we introduce a
                 * new Variable.
                 */
                value = gen.emitMove(value);
            } else if (!allowObjectConstantToStackMove && node instanceof ConstantNode && !value.getLIRKind().isValue()) {
                /*
                 * Object constants are not allowed as inputs for PHIs. Explicitly create a copy of
                 * this value to force it into a register. The new variable is only used in the PHI.
                 */
                Variable result = gen.newVariable(value.getLIRKind());
                gen.emitMove(result, value);
                value = result;
            }
            values.add(value);
        }
        return values.toArray(new Value[values.size()]);
    }

    @SuppressWarnings("try")
    public void doBlock(Block block, StructuredGraph graph, BlockMap<List<Node>> blockMap) {
        try (BlockScope blockScope = gen.getBlockScope(block)) {

            if (block == gen.getResult().getLIR().getControlFlowGraph().getStartBlock()) {
                assert block.getPredecessorCount() == 0;
                emitPrologue(graph);
            } else {
                assert block.getPredecessorCount() > 0;
                if (ConstructionSSAlirDuringLirBuilding.getValue()) {
                    // create phi-in value array
                    AbstractBeginNode begin = block.getBeginNode();
                    if (begin instanceof AbstractMergeNode) {
                        AbstractMergeNode merge = (AbstractMergeNode) begin;
                        LabelOp label = (LabelOp) gen.getResult().getLIR().getLIRforBlock(block).get(0);
                        label.setIncomingValues(createPhiIn(merge));
                        if (Options.PrintIRWithLIR.getValue() && !TTY.isSuppressed()) {
                            TTY.println("Created PhiIn: " + label);

                        }
                    }
                }
            }

            List<Node> nodes = blockMap.get(block);

            // Allow NodeLIRBuilder subclass to specialize code generation of any interesting groups
            // of instructions
            matchComplexExpressions(nodes);

            for (int i = 0; i < nodes.size(); i++) {
                Node node = nodes.get(i);
                if (node instanceof ValueNode) {
                    ValueNode valueNode = (ValueNode) node;
                    if (Options.TraceLIRGeneratorLevel.getValue() >= 3) {
                        TTY.println("LIRGen for " + valueNode);
                    }
                    Value operand = getOperand(valueNode);
                    if (operand == null) {
                        if (!peephole(valueNode)) {
                            try {
                                doRoot(valueNode);
                            } catch (JVMCIError e) {
                                throw GraalGraphJVMCIError.transformAndAddContext(e, valueNode);
                            } catch (Throwable e) {
                                throw new GraalGraphJVMCIError(e).addContext(valueNode);
                            }
                        }
                    } else if (ComplexMatchValue.INTERIOR_MATCH.equals(operand)) {
                        // Doesn't need to be evaluated
                        Debug.log("interior match for %s", valueNode);
                    } else if (operand instanceof ComplexMatchValue) {
                        Debug.log("complex match for %s", valueNode);
                        ComplexMatchValue match = (ComplexMatchValue) operand;
                        operand = match.evaluate(this);
                        if (operand != null) {
                            setResult(valueNode, operand);
                        }
                    } else {
                        // There can be cases in which the result of an instruction is already set
                        // before by other instructions.
                    }
                }
            }

            if (!gen.hasBlockEnd(block)) {
                NodeClassIterable successors = block.getEndNode().successors();
                assert successors.count() == block.getSuccessorCount();
                if (block.getSuccessorCount() != 1) {
                    /*
                     * If we have more than one successor, we cannot just use the first one. Since
                     * successors are unordered, this would be a random choice.
                     */
                    throw new JVMCIError("Block without BlockEndOp: " + block.getEndNode());
                }
                gen.emitJump(getLIRBlock((FixedNode) successors.first()));
            }

            assert verifyBlock(gen.getResult().getLIR(), block);
        }
    }

    @SuppressWarnings("try")
    protected void matchComplexExpressions(List<Node> nodes) {
        if (matchRules != null) {
            try (Scope s = Debug.scope("MatchComplexExpressions")) {
                if (LogVerbose.getValue()) {
                    int i = 0;
                    for (Node node : nodes) {
                        Debug.log("%d: (%s) %1S", i++, node.getUsageCount(), node);
                    }
                }

                // Match the nodes in backwards order to encourage longer matches.
                for (int index = nodes.size() - 1; index >= 0; index--) {
                    Node node = nodes.get(index);
                    if (getOperand(node) != null) {
                        continue;
                    }
                    // See if this node is the root of any MatchStatements
                    List<MatchStatement> statements = matchRules.get(node.getClass());
                    if (statements != null) {
                        for (MatchStatement statement : statements) {
                            if (statement.generate(this, index, node, nodes)) {
                                // Found a match so skip to the next
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    protected abstract boolean peephole(ValueNode valueNode);

    private void doRoot(ValueNode instr) {
        if (Options.TraceLIRGeneratorLevel.getValue() >= 2) {
            TTY.println("Emitting LIR for instruction " + instr);
        }
        currentInstruction = instr;

        Debug.log("Visiting %s", instr);
        emitNode(instr);
        Debug.log("Operand for %s = %s", instr, getOperand(instr));
    }

    protected void emitNode(ValueNode node) {
        if (Debug.isLogEnabled() && node.stamp().isEmpty()) {
            Debug.log("This node has an empty stamp, we are emitting dead code(?): %s", node);
        }
        if (node instanceof LIRLowerable) {
            ((LIRLowerable) node).generate(this);
        } else {
            throw JVMCIError.shouldNotReachHere("node is not LIRLowerable: " + node);
        }
    }

    protected void emitPrologue(StructuredGraph graph) {
        CallingConvention incomingArguments = gen.getCallingConvention();

        Value[] params = new Value[incomingArguments.getArgumentCount()];
        for (int i = 0; i < params.length; i++) {
            params[i] = incomingArguments.getArgument(i);
            if (ValueUtil.isStackSlot(params[i])) {
                StackSlot slot = ValueUtil.asStackSlot(params[i]);
                if (slot.isInCallerFrame() && !gen.getResult().getLIR().hasArgInCallerFrame()) {
                    gen.getResult().getLIR().setHasArgInCallerFrame();
                }
            }
        }

        gen.emitIncomingValues(params);

        for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
            Value paramValue = params[param.index()];
            assert paramValue.getLIRKind().equals(getLIRGeneratorTool().getLIRKind(param.stamp())) : paramValue + " " + getLIRGeneratorTool().getLIRKind(param.stamp());
            setResult(param, gen.emitMove(paramValue));
        }
    }

    @Override
    public void visitMerge(AbstractMergeNode x) {
    }

    @Override
    public void visitEndNode(AbstractEndNode end) {
        AbstractMergeNode merge = end.merge();
        JumpOp jump = newJumpOp(getLIRBlock(merge));
        if (ConstructionSSAlirDuringLirBuilding.getValue()) {
            jump.setOutgoingValues(createPhiOut(merge, end));
        } else {
            moveToPhi(merge, end);
        }
        append(jump);
    }

    /**
     * Runtime specific classes can override this to insert a safepoint at the end of a loop.
     */
    @Override
    public void visitLoopEnd(LoopEndNode x) {
    }

    private void moveToPhi(AbstractMergeNode merge, AbstractEndNode pred) {
        if (Options.TraceLIRGeneratorLevel.getValue() >= 1) {
            TTY.println("MOVE TO PHI from " + pred + " to " + merge);
        }
        PhiResolver resolver = PhiResolver.create(gen);
        for (PhiNode phi : merge.phis()) {
            if (phi instanceof ValuePhiNode) {
                ValueNode curVal = phi.valueAt(pred);
                resolver.move(operandForPhi((ValuePhiNode) phi), operand(curVal));
            }
        }
        resolver.dispose();
    }

    protected JumpOp newJumpOp(LabelRef ref) {
        return new JumpOp(ref);
    }

    protected LIRKind getPhiKind(PhiNode phi) {
        return gen.getLIRKind(phi.stamp());
    }

    private Value operandForPhi(ValuePhiNode phi) {
        Value result = getOperand(phi);
        if (result == null) {
            // allocate a variable for this phi
            Variable newOperand = gen.newVariable(getPhiKind(phi));
            setResult(phi, newOperand);
            return newOperand;
        } else {
            return result;
        }
    }

    @Override
    public void emitIf(IfNode x) {
        emitBranch(x.condition(), getLIRBlock(x.trueSuccessor()), getLIRBlock(x.falseSuccessor()), x.probability(x.trueSuccessor()));
    }

    public void emitBranch(LogicNode node, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability) {
        if (node instanceof IsNullNode) {
            emitNullCheckBranch((IsNullNode) node, trueSuccessor, falseSuccessor, trueSuccessorProbability);
        } else if (node instanceof CompareNode) {
            emitCompareBranch((CompareNode) node, trueSuccessor, falseSuccessor, trueSuccessorProbability);
        } else if (node instanceof LogicConstantNode) {
            emitConstantBranch(((LogicConstantNode) node).getValue(), trueSuccessor, falseSuccessor);
        } else if (node instanceof IntegerTestNode) {
            emitIntegerTestBranch((IntegerTestNode) node, trueSuccessor, falseSuccessor, trueSuccessorProbability);
        } else {
            throw JVMCIError.unimplemented(node.toString());
        }
    }

    private void emitNullCheckBranch(IsNullNode node, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability) {
        LIRKind kind = gen.getLIRKind(node.getValue().stamp());
        Value nullValue = gen.emitConstant(kind, JavaConstant.NULL_POINTER);
        gen.emitCompareBranch(kind.getPlatformKind(), operand(node.getValue()), nullValue, Condition.EQ, false, trueSuccessor, falseSuccessor, trueSuccessorProbability);
    }

    public void emitCompareBranch(CompareNode compare, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability) {
        PlatformKind kind = gen.getLIRKind(compare.getX().stamp()).getPlatformKind();
        gen.emitCompareBranch(kind, operand(compare.getX()), operand(compare.getY()), compare.condition(), compare.unorderedIsTrue(), trueSuccessor, falseSuccessor, trueSuccessorProbability);
    }

    public void emitIntegerTestBranch(IntegerTestNode test, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability) {
        gen.emitIntegerTestBranch(operand(test.getX()), operand(test.getY()), trueSuccessor, falseSuccessor, trueSuccessorProbability);
    }

    public void emitConstantBranch(boolean value, LabelRef trueSuccessorBlock, LabelRef falseSuccessorBlock) {
        LabelRef block = value ? trueSuccessorBlock : falseSuccessorBlock;
        gen.emitJump(block);
    }

    @Override
    public void emitConditional(ConditionalNode conditional) {
        Value tVal = operand(conditional.trueValue());
        Value fVal = operand(conditional.falseValue());
        setResult(conditional, emitConditional(conditional.condition(), tVal, fVal));
    }

    public Variable emitConditional(LogicNode node, Value trueValue, Value falseValue) {
        if (node instanceof IsNullNode) {
            IsNullNode isNullNode = (IsNullNode) node;
            LIRKind kind = gen.getLIRKind(isNullNode.getValue().stamp());
            Value nullValue = gen.emitConstant(kind, JavaConstant.NULL_POINTER);
            return gen.emitConditionalMove(kind.getPlatformKind(), operand(isNullNode.getValue()), nullValue, Condition.EQ, false, trueValue, falseValue);
        } else if (node instanceof CompareNode) {
            CompareNode compare = (CompareNode) node;
            PlatformKind kind = gen.getLIRKind(compare.getX().stamp()).getPlatformKind();
            return gen.emitConditionalMove(kind, operand(compare.getX()), operand(compare.getY()), compare.condition(), compare.unorderedIsTrue(), trueValue, falseValue);
        } else if (node instanceof LogicConstantNode) {
            return gen.emitMove(((LogicConstantNode) node).getValue() ? trueValue : falseValue);
        } else if (node instanceof IntegerTestNode) {
            IntegerTestNode test = (IntegerTestNode) node;
            return gen.emitIntegerTestMove(operand(test.getX()), operand(test.getY()), trueValue, falseValue);
        } else {
            throw JVMCIError.unimplemented(node.toString());
        }
    }

    @Override
    public void emitInvoke(Invoke x) {
        LoweredCallTargetNode callTarget = (LoweredCallTargetNode) x.callTarget();
        CallingConvention invokeCc = gen.getResult().getFrameMapBuilder().getRegisterConfig().getCallingConvention(callTarget.callType(), x.asNode().stamp().javaType(gen.getMetaAccess()),
                        callTarget.signature(), gen.target(), false);
        gen.getResult().getFrameMapBuilder().callsMethod(invokeCc);

        Value[] parameters = visitInvokeArguments(invokeCc, callTarget.arguments());

        LabelRef exceptionEdge = null;
        if (x instanceof InvokeWithExceptionNode) {
            exceptionEdge = getLIRBlock(((InvokeWithExceptionNode) x).exceptionEdge());
        }
        LIRFrameState callState = stateWithExceptionEdge(x, exceptionEdge);

        Value result = invokeCc.getReturn();
        if (callTarget instanceof DirectCallTargetNode) {
            emitDirectCall((DirectCallTargetNode) callTarget, result, parameters, AllocatableValue.NONE, callState);
        } else if (callTarget instanceof IndirectCallTargetNode) {
            emitIndirectCall((IndirectCallTargetNode) callTarget, result, parameters, AllocatableValue.NONE, callState);
        } else {
            throw JVMCIError.shouldNotReachHere();
        }

        if (isLegal(result)) {
            setResult(x.asNode(), gen.emitMove(result));
        }

        if (x instanceof InvokeWithExceptionNode) {
            gen.emitJump(getLIRBlock(((InvokeWithExceptionNode) x).next()));
        }
    }

    protected abstract void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState);

    protected abstract void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState);

    @Override
    public Value[] visitInvokeArguments(CallingConvention invokeCc, Collection<ValueNode> arguments) {
        // for each argument, load it into the correct location
        Value[] result = new Value[arguments.size()];
        int j = 0;
        for (ValueNode arg : arguments) {
            if (arg != null) {
                AllocatableValue operand = invokeCc.getArgument(j);
                gen.emitMove(operand, operand(arg));
                result[j] = operand;
                j++;
            } else {
                throw JVMCIError.shouldNotReachHere("I thought we no longer have null entries for two-slot types...");
            }
        }
        return result;
    }

    /**
     * This method tries to create a switch implementation that is optimal for the given switch. It
     * will either generate a sequential if/then/else cascade, a set of range tests or a table
     * switch.
     *
     * If the given switch does not contain int keys, it will always create a sequential
     * implementation.
     */
    @Override
    public void emitSwitch(SwitchNode x) {
        assert x.defaultSuccessor() != null;
        LabelRef defaultTarget = getLIRBlock(x.defaultSuccessor());
        int keyCount = x.keyCount();
        if (keyCount == 0) {
            gen.emitJump(defaultTarget);
        } else {
            Variable value = gen.load(operand(x.value()));
            if (keyCount == 1) {
                assert defaultTarget != null;
                double probability = x.probability(x.keySuccessor(0));
                LIRKind kind = gen.getLIRKind(x.value().stamp());
                Value key = gen.emitConstant(kind, x.keyAt(0));
                gen.emitCompareBranch(kind.getPlatformKind(), gen.load(operand(x.value())), key, Condition.EQ, false, getLIRBlock(x.keySuccessor(0)), defaultTarget, probability);
            } else if (x instanceof IntegerSwitchNode && x.isSorted()) {
                IntegerSwitchNode intSwitch = (IntegerSwitchNode) x;
                LabelRef[] keyTargets = new LabelRef[keyCount];
                JavaConstant[] keyConstants = new JavaConstant[keyCount];
                double[] keyProbabilities = new double[keyCount];
                JavaKind keyKind = intSwitch.keyAt(0).getJavaKind();
                for (int i = 0; i < keyCount; i++) {
                    keyTargets[i] = getLIRBlock(intSwitch.keySuccessor(i));
                    keyConstants[i] = intSwitch.keyAt(i);
                    keyProbabilities[i] = intSwitch.keyProbability(i);
                    assert keyConstants[i].getJavaKind() == keyKind;
                }
                gen.emitStrategySwitch(keyConstants, keyProbabilities, keyTargets, defaultTarget, value);
            } else {
                // keyKind != JavaKind.Int || !x.isSorted()
                LabelRef[] keyTargets = new LabelRef[keyCount];
                Constant[] keyConstants = new Constant[keyCount];
                double[] keyProbabilities = new double[keyCount];
                for (int i = 0; i < keyCount; i++) {
                    keyTargets[i] = getLIRBlock(x.keySuccessor(i));
                    keyConstants[i] = x.keyAt(i);
                    keyProbabilities[i] = x.keyProbability(i);
                }

                // hopefully only a few entries
                gen.emitStrategySwitch(new SwitchStrategy.SequentialStrategy(keyProbabilities, keyConstants), value, keyTargets, defaultTarget);
            }
        }
    }

    public DebugInfoBuilder getDebugInfoBuilder() {
        assert debugInfoBuilder != null;
        return debugInfoBuilder;
    }

    private static FrameState getFrameState(DeoptimizingNode deopt) {
        if (deopt instanceof DeoptimizingNode.DeoptBefore) {
            assert !(deopt instanceof DeoptimizingNode.DeoptDuring || deopt instanceof DeoptimizingNode.DeoptAfter);
            return ((DeoptimizingNode.DeoptBefore) deopt).stateBefore();
        } else if (deopt instanceof DeoptimizingNode.DeoptDuring) {
            assert !(deopt instanceof DeoptimizingNode.DeoptAfter);
            return ((DeoptimizingNode.DeoptDuring) deopt).stateDuring();
        } else {
            assert deopt instanceof DeoptimizingNode.DeoptAfter;
            return ((DeoptimizingNode.DeoptAfter) deopt).stateAfter();
        }
    }

    public LIRFrameState state(DeoptimizingNode deopt) {
        if (!deopt.canDeoptimize()) {
            return null;
        }
        return stateFor(getFrameState(deopt));
    }

    public LIRFrameState stateWithExceptionEdge(DeoptimizingNode deopt, LabelRef exceptionEdge) {
        if (!deopt.canDeoptimize()) {
            return null;
        }
        return stateForWithExceptionEdge(getFrameState(deopt), exceptionEdge);
    }

    public LIRFrameState stateFor(FrameState state) {
        return stateForWithExceptionEdge(state, null);
    }

    public LIRFrameState stateForWithExceptionEdge(FrameState state, LabelRef exceptionEdge) {
        if (gen.needOnlyOopMaps()) {
            return new LIRFrameState(null, null, null);
        }
        assert state != null;
        return getDebugInfoBuilder().build(state, exceptionEdge);
    }

    @Override
    public void emitOverflowCheckBranch(AbstractBeginNode overflowSuccessor, AbstractBeginNode next, Stamp stamp, double probability) {
        LIRKind cmpKind = getLIRGeneratorTool().getLIRKind(stamp);
        gen.emitOverflowCheckBranch(getLIRBlock(overflowSuccessor), getLIRBlock(next), cmpKind, probability);
    }

    @Override
    public void visitFullInfopointNode(FullInfopointNode i) {
        append(new FullInfopointOp(stateFor(i.getState()), i.getReason()));
    }

    @Override
    public void visitSimpleInfopointNode(SimpleInfopointNode i) {
        append(new SimpleInfopointOp(i.getReason(), i.getPosition()));
    }

    @Override
    public LIRGeneratorTool getLIRGeneratorTool() {
        return gen;
    }
}
