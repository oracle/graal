/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.compiler.GraalDebugConfig.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.lir.LIR.*;

import java.util.*;
import java.util.Map.Entry;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.compiler.match.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.gen.LIRGenerator.Options;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * This class traverses the HIR instructions and generates LIR instructions from them.
 */
@MatchableNode(nodeClass = ConstantNode.class, shareable = true)
@MatchableNode(nodeClass = FloatConvertNode.class, inputs = {"value"})
@MatchableNode(nodeClass = FloatSubNode.class, inputs = {"x", "y"})
@MatchableNode(nodeClass = FloatingReadNode.class, inputs = {"object", "location"})
@MatchableNode(nodeClass = IfNode.class, inputs = {"condition"})
@MatchableNode(nodeClass = IntegerSubNode.class, inputs = {"x", "y"})
@MatchableNode(nodeClass = LeftShiftNode.class, inputs = {"x", "y"})
@MatchableNode(nodeClass = NarrowNode.class, inputs = {"value"})
@MatchableNode(nodeClass = ReadNode.class, inputs = {"object", "location"})
@MatchableNode(nodeClass = ReinterpretNode.class, inputs = {"value"})
@MatchableNode(nodeClass = SignExtendNode.class, inputs = {"value"})
@MatchableNode(nodeClass = UnsignedRightShiftNode.class, inputs = {"x", "y"})
@MatchableNode(nodeClass = WriteNode.class, inputs = {"object", "location", "value"})
@MatchableNode(nodeClass = ZeroExtendNode.class, inputs = {"value"})
@MatchableNode(nodeClass = AndNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = FloatAddNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = FloatEqualsNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = FloatLessThanNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = FloatMulNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = IntegerAddNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = IntegerBelowNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = IntegerEqualsNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = IntegerLessThanNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = IntegerMulNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = IntegerTestNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = ObjectEqualsNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = OrNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = XorNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = PiNode.class, inputs = {"object"})
@MatchableNode(nodeClass = ConstantLocationNode.class, shareable = true)
public abstract class NodeLIRBuilder implements NodeLIRBuilderTool {

    private final NodeMap<Value> nodeOperands;
    private final DebugInfoBuilder debugInfoBuilder;

    protected final LIRGeneratorTool gen;

    private ValueNode currentInstruction;
    private ValueNode lastInstructionPrinted; // Debugging only

    private Map<Class<? extends ValueNode>, List<MatchStatement>> matchRules;

    public NodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool gen) {
        this.gen = gen;
        this.nodeOperands = graph.createNodeMap();
        this.debugInfoBuilder = createDebugInfoBuilder(graph, nodeOperands);
        if (MatchExpressions.getValue()) {
            matchRules = MatchRuleRegistry.lookup(getClass());
        }
    }

    @SuppressWarnings({"unused", "hiding"})
    protected DebugInfoBuilder createDebugInfoBuilder(StructuredGraph graph, NodeMap<Value> nodeOperands) {
        return new DebugInfoBuilder(nodeOperands);
    }

    /**
     * Returns the operand that has been previously initialized by
     * {@link #setResult(ValueNode, Value)} with the result of an instruction. It's a code
     * generation error to ask for the operand of ValueNode that doesn't have one yet.
     *
     * @param node A node that produces a result value.
     */
    @Override
    public Value operand(ValueNode node) {
        Value operand = getOperand(node);
        assert operand != null : String.format("missing operand for %1s", node);
        return operand;
    }

    @Override
    public boolean hasOperand(ValueNode node) {
        return getOperand(node) != null;
    }

    private Value getOperand(ValueNode node) {
        if (nodeOperands == null) {
            return null;
        }
        return nodeOperands.get(node);
    }

    public ValueNode valueForOperand(Value value) {
        for (Entry<Node, Value> entry : getNodeOperands().entries()) {
            if (entry.getValue().equals(value)) {
                return (ValueNode) entry.getKey();
            }
        }
        return null;
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
    public void setMatchResult(ValueNode x, Value operand) {
        assert operand.equals(ComplexMatchValue.INTERIOR_MATCH) || operand instanceof ComplexMatchValue;
        assert operand instanceof ComplexMatchValue || x.usages().count() == 1 : "interior matches must be single user";
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

    public void doBlock(Block block, StructuredGraph graph, BlockMap<List<ScheduledNode>> blockMap) {
        gen.doBlockStart(block);

        if (block == gen.getResult().getLIR().getControlFlowGraph().getStartBlock()) {
            assert block.getPredecessorCount() == 0;
            emitPrologue(graph);
        } else {
            assert block.getPredecessorCount() > 0;
        }

        List<ScheduledNode> nodes = blockMap.get(block);

        // Allow NodeLIRBuilder subclass to specialize code generation of any interesting groups
        // of instructions
        matchComplexExpressions(nodes);

        for (int i = 0; i < nodes.size(); i++) {
            Node instr = nodes.get(i);
            if (Options.TraceLIRGeneratorLevel.getValue() >= 3) {
                TTY.println("LIRGen for " + instr);
            }
            if (instr instanceof ValueNode) {
                ValueNode valueNode = (ValueNode) instr;
                Value operand = getOperand(valueNode);
                if (operand == null) {
                    if (!peephole(valueNode)) {
                        try {
                            doRoot((ValueNode) instr);
                        } catch (GraalInternalError e) {
                            throw GraalGraphInternalError.transformAndAddContext(e, instr);
                        } catch (Throwable e) {
                            throw new GraalGraphInternalError(e).addContext(instr);
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
                throw new GraalInternalError("Block without BlockEndOp: " + block.getEndNode());
            }
            gen.emitJump(getLIRBlock((FixedNode) successors.first()));
        }

        assert verifyBlock(gen.getResult().getLIR(), block);
        gen.doBlockEnd(block);
    }

    protected void matchComplexExpressions(List<ScheduledNode> nodes) {
        if (matchRules != null) {
            try (Scope s = Debug.scope("MatchComplexExpressions")) {
                if (LogVerbose.getValue()) {
                    int i = 0;
                    for (ScheduledNode node : nodes) {
                        Debug.log("%d: (%s) %1S", i++, node.usages().count(), node);
                    }
                }

                // Match the nodes in backwards order to encourage longer matches.
                for (int index = nodes.size() - 1; index >= 0; index--) {
                    ScheduledNode snode = nodes.get(index);
                    if (!(snode instanceof ValueNode)) {
                        continue;
                    }
                    ValueNode node = (ValueNode) snode;
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
        if (Debug.isLogEnabled() && node.stamp() instanceof IllegalStamp) {
            Debug.log("This node has invalid type, we are emitting dead code(?): %s", node);
        }
        if (node instanceof LIRLowerable) {
            ((LIRLowerable) node).generate(this);
        } else if (node instanceof ArithmeticLIRLowerable) {
            ((ArithmeticLIRLowerable) node).generate(this, gen);
        } else {
            throw GraalInternalError.shouldNotReachHere("node is not LIRLowerable: " + node);
        }
    }

    protected void emitPrologue(StructuredGraph graph) {
        CallingConvention incomingArguments = gen.getCallingConvention();

        Value[] params = new Value[incomingArguments.getArgumentCount()];
        for (int i = 0; i < params.length; i++) {
            params[i] = LIRGenerator.toStackKind(incomingArguments.getArgument(i));
            if (ValueUtil.isStackSlot(params[i])) {
                StackSlot slot = ValueUtil.asStackSlot(params[i]);
                if (slot.isInCallerFrame() && !gen.getResult().getLIR().hasArgInCallerFrame()) {
                    gen.getResult().getLIR().setHasArgInCallerFrame();
                }
            }
        }

        gen.emitIncomingValues(params);

        for (ParameterNode param : graph.getNodes(ParameterNode.class)) {
            Value paramValue = params[param.index()];
            assert paramValue.getKind() == param.getKind().getStackKind();
            setResult(param, gen.emitMove(paramValue));
        }
    }

    @Override
    public void visitMerge(MergeNode x) {
    }

    @Override
    public void visitEndNode(AbstractEndNode end) {
        moveToPhi(end.merge(), end);
    }

    /**
     * Runtime specific classes can override this to insert a safepoint at the end of a loop.
     */
    @Override
    public void visitLoopEnd(LoopEndNode x) {
    }

    private void moveToPhi(MergeNode merge, AbstractEndNode pred) {
        if (Options.TraceLIRGeneratorLevel.getValue() >= 1) {
            TTY.println("MOVE TO PHI from " + pred + " to " + merge);
        }
        PhiResolver resolver = new PhiResolver(gen);
        for (PhiNode phi : merge.phis()) {
            if (phi instanceof ValuePhiNode) {
                ValueNode curVal = phi.valueAt(pred);
                resolver.move(operandForPhi((ValuePhiNode) phi), operand(curVal));
            }
        }
        resolver.dispose();

        append(newJumpOp(getLIRBlock(merge)));
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
            throw GraalInternalError.unimplemented(node.toString());
        }
    }

    private void emitNullCheckBranch(IsNullNode node, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability) {
        PlatformKind kind = gen.getLIRKind(node.getValue().stamp()).getPlatformKind();
        gen.emitCompareBranch(kind, operand(node.getValue()), kind.getDefaultValue(), Condition.EQ, false, trueSuccessor, falseSuccessor, trueSuccessorProbability);
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
            PlatformKind kind = gen.getLIRKind(isNullNode.getValue().stamp()).getPlatformKind();
            return gen.emitConditionalMove(kind, operand(isNullNode.getValue()), kind.getDefaultValue(), Condition.EQ, false, trueValue, falseValue);
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
            throw GraalInternalError.unimplemented(node.toString());
        }
    }

    @Override
    public void emitInvoke(Invoke x) {
        LoweredCallTargetNode callTarget = (LoweredCallTargetNode) x.callTarget();
        CallingConvention invokeCc = gen.getResult().getFrameMap().registerConfig.getCallingConvention(callTarget.callType(), x.asNode().stamp().javaType(gen.getMetaAccess()), callTarget.signature(),
                        gen.target(), false);
        gen.getResult().getFrameMap().callsMethod(invokeCc);

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
            throw GraalInternalError.shouldNotReachHere();
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
                AllocatableValue operand = LIRGenerator.toStackKind(invokeCc.getArgument(j));
                gen.emitMove(operand, operand(arg));
                result[j] = operand;
                j++;
            } else {
                throw GraalInternalError.shouldNotReachHere("I thought we no longer have null entries for two-slot types...");
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
                PlatformKind kind = gen.getLIRKind(x.value().stamp()).getPlatformKind();
                gen.emitCompareBranch(kind, gen.load(operand(x.value())), x.keyAt(0), Condition.EQ, false, getLIRBlock(x.keySuccessor(0)), defaultTarget, probability);
            } else {
                LabelRef[] keyTargets = new LabelRef[keyCount];
                Constant[] keyConstants = new Constant[keyCount];
                double[] keyProbabilities = new double[keyCount];
                for (int i = 0; i < keyCount; i++) {
                    keyTargets[i] = getLIRBlock(x.keySuccessor(i));
                    keyConstants[i] = x.keyAt(i);
                    keyProbabilities[i] = x.keyProbability(i);
                }
                if (value.getKind() != Kind.Int || !x.isSorted()) {
                    // hopefully only a few entries
                    gen.emitStrategySwitch(new SwitchStrategy.SequentialStrategy(keyProbabilities, keyConstants), value, keyTargets, defaultTarget);
                } else {
                    gen.emitStrategySwitch(keyConstants, keyProbabilities, keyTargets, defaultTarget, value);
                }
            }
        }
    }

    public final NodeMap<Value> getNodeOperands() {
        assert nodeOperands != null;
        return nodeOperands;
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

    public void emitOverflowCheckBranch(BeginNode overflowSuccessor, BeginNode next, double probability) {
        gen.emitOverflowCheckBranch(getLIRBlock(overflowSuccessor), getLIRBlock(next), probability);
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
