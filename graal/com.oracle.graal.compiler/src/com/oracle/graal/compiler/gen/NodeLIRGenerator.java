/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.api.meta.Value.*;
import static com.oracle.graal.lir.LIR.*;
import static com.oracle.graal.nodes.ConstantNode.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.Map.Entry;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.compiler.gen.LIRGenerator.LoadConstant;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.BlockEndOp;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * This class traverses the HIR instructions and generates LIR instructions from them.
 */
public abstract class NodeLIRGenerator implements NodeLIRGeneratorTool {

    public static class Options {
        // @formatter:off
//        @Option(help = "Print HIR along side LIR as the latter is generated")
//        public static final OptionValue<Boolean> PrintIRWithLIR = new OptionValue<>(false);
//        @Option(help = "The trace level for the LIR generator")
//        public static final OptionValue<Integer> TraceLIRGeneratorLevel = new OptionValue<>(0);
        // @formatter:on
    }

    private final NodeMap<Value> nodeOperands;
    private final DebugInfoBuilder debugInfoBuilder;

    private final int traceLevel;
    private final boolean printIRWithLIR;

    protected final LIRGenerator gen;

    private ValueNode currentInstruction;
    private ValueNode lastInstructionPrinted; // Debugging only

    protected LIRGenerationResult res;

    public NodeLIRGenerator(StructuredGraph graph, LIRGenerationResult res, LIRGenerator gen) {
        this.res = res;
        this.nodeOperands = graph.createNodeMap();
        this.debugInfoBuilder = createDebugInfoBuilder(nodeOperands);
        this.gen = gen;
        this.traceLevel = LIRGenerator.Options.TraceLIRGeneratorLevel.getValue();
        this.printIRWithLIR = LIRGenerator.Options.PrintIRWithLIR.getValue();
        gen.setDebugInfoBuilder(debugInfoBuilder);
    }

    @SuppressWarnings("hiding")
    protected DebugInfoBuilder createDebugInfoBuilder(NodeMap<Value> nodeOperands) {
        return new DebugInfoBuilder(nodeOperands);
    }

    /**
     * Returns the operand that has been previously initialized by
     * {@link #setResult(ValueNode, Value)} with the result of an instruction.
     * 
     * @param node A node that produces a result value.
     */
    @Override
    public Value operand(ValueNode node) {
        if (nodeOperands == null) {
            return null;
        }
        Value operand = nodeOperands.get(node);
        if (operand == null) {
            return getConstantOperand(node);
        }
        return operand;
    }

    private Value getConstantOperand(ValueNode node) {
        if (!ConstantNodeRecordsUsages) {
            Constant value = node.asConstant();
            if (value != null) {
                if (gen.canInlineConstant(value)) {
                    return setResult(node, value);
                } else {
                    Variable loadedValue;
                    if (gen.constantLoads == null) {
                        gen.constantLoads = new HashMap<>();
                    }
                    LoadConstant load = gen.constantLoads.get(value);
                    assert gen.getCurrentBlock() instanceof Block;
                    if (load == null) {
                        int index = res.getLIR().getLIRforBlock(gen.getCurrentBlock()).size();
                        loadedValue = gen.emitMove(value);
                        LIRInstruction op = res.getLIR().getLIRforBlock(gen.getCurrentBlock()).get(index);
                        gen.constantLoads.put(value, new LoadConstant(loadedValue, (Block) gen.getCurrentBlock(), index, op));
                    } else {
                        Block dominator = ControlFlowGraph.commonDominator(load.block, (Block) gen.getCurrentBlock());
                        loadedValue = load.variable;
                        if (dominator != load.block) {
                            load.unpin(res.getLIR());
                        } else {
                            assert load.block != gen.getCurrentBlock() || load.index < res.getLIR().getLIRforBlock(gen.getCurrentBlock()).size();
                        }
                        load.block = dominator;
                    }
                    return loadedValue;
                }
            }
        } else {
            // Constant is loaded by ConstantNode.generate()
        }
        return null;
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
        assert nodeOperands == null || nodeOperands.get(x) == null : "operand cannot be set twice";
        assert operand != null && isLegal(operand) : "operand must be legal";
        assert operand.getKind().getStackKind() == x.getKind() || x.getKind() == Kind.Illegal : operand.getKind().getStackKind() + " must match " + x.getKind();
        assert !(x instanceof VirtualObjectNode);
        nodeOperands.set(x, operand);
        return operand;
    }

    public LabelRef getLIRBlock(FixedNode b) {
        assert res.getLIR().getControlFlowGraph() instanceof ControlFlowGraph;
        Block result = ((ControlFlowGraph) res.getLIR().getControlFlowGraph()).blockFor(b);
        int suxIndex = gen.getCurrentBlock().getSuccessors().indexOf(result);
        assert suxIndex != -1 : "Block not in successor list of current block";

        assert gen.getCurrentBlock() instanceof Block;
        return LabelRef.forSuccessor(res.getLIR(), (Block) gen.getCurrentBlock(), suxIndex);
    }

    public LIRFrameState state(DeoptimizingNode deopt) {
        if (!deopt.canDeoptimize()) {
            return null;
        }
        return stateFor(deopt.getDeoptimizationState());
    }

    public LIRFrameState stateWithExceptionEdge(DeoptimizingNode deopt, LabelRef exceptionEdge) {
        if (!deopt.canDeoptimize()) {
            return null;
        }
        return stateForWithExceptionEdge(deopt.getDeoptimizationState(), exceptionEdge);
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

    /**
     * Gets the ABI specific operand used to return a value of a given kind from a method.
     * 
     * @param kind the kind of value being returned
     * @return the operand representing the ABI defined location used return a value of kind
     *         {@code kind}
     */
    public AllocatableValue resultOperandFor(Kind kind) {
        if (kind == Kind.Void) {
            return ILLEGAL;
        }
        return res.getFrameMap().registerConfig.getReturnRegister(kind).asValue(kind);
    }

    final protected void append(LIRInstruction op) {
        if (printIRWithLIR && !TTY.isSuppressed()) {
            if (currentInstruction != null && lastInstructionPrinted != currentInstruction) {
                lastInstructionPrinted = currentInstruction;
                InstructionPrinter ip = new InstructionPrinter(TTY.out());
                ip.printInstructionListing(currentInstruction);
            }
        }
        gen.append(op);
    }

    public final void doBlockStart(AbstractBlock<?> block) {
        if (printIRWithLIR) {
            TTY.print(block.toString());
        }

        gen.setCurrentBlock(block);

        // set up the list of LIR instructions
        assert res.getLIR().getLIRforBlock(block) == null : "LIR list already computed for this block";
        res.getLIR().setLIRforBlock(block, new ArrayList<LIRInstruction>());

        append(new LabelOp(new Label(block.getId()), block.isAligned()));

        if (traceLevel >= 1) {
            TTY.println("BEGIN Generating LIR for block B" + block.getId());
        }
    }

    public final void doBlockEnd(AbstractBlock<?> block) {

        if (traceLevel >= 1) {
            TTY.println("END Generating LIR for block B" + block.getId());
        }

        gen.setCurrentBlock(null);

        if (printIRWithLIR) {
            TTY.println();
        }
    }

    /**
     * For Baseline compilation
     */

    public <T extends AbstractBlock<T>> void doBlock(T block, ResolvedJavaMethod method, BytecodeParser<T> parser) {
        doBlockStart(block);

        if (block == res.getLIR().getControlFlowGraph().getStartBlock()) {
            assert block.getPredecessorCount() == 0;
            emitPrologue(method, parser);
        } else {
            assert block.getPredecessorCount() > 0;
        }
        parser.processBlock(block);

        doBlockEnd(block);
    }

    public void doBlock(Block block, StructuredGraph graph, BlockMap<List<ScheduledNode>> blockMap) {
        doBlockStart(block);

        if (block == res.getLIR().getControlFlowGraph().getStartBlock()) {
            assert block.getPredecessorCount() == 0;
            emitPrologue(graph);
        } else {
            assert block.getPredecessorCount() > 0;
        }

        List<ScheduledNode> nodes = blockMap.get(block);
        for (int i = 0; i < nodes.size(); i++) {
            Node instr = nodes.get(i);
            if (traceLevel >= 3) {
                TTY.println("LIRGen for " + instr);
            }
            if (!ConstantNodeRecordsUsages && instr instanceof ConstantNode) {
                // Loading of constants is done lazily by operand()
            } else if (instr instanceof ValueNode) {
                ValueNode valueNode = (ValueNode) instr;
                if (operand(valueNode) == null) {
                    if (!peephole(valueNode)) {
                        try {
                            doRoot((ValueNode) instr);
                        } catch (GraalInternalError e) {
                            throw e.addContext(instr);
                        } catch (Throwable e) {
                            throw new GraalInternalError(e).addContext(instr);
                        }
                    }
                } else {
                    // There can be cases in which the result of an instruction is already set
                    // before by other instructions.
                }
            }
        }

        if (!hasBlockEnd(block)) {
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

        assert verifyBlock(res.getLIR(), block);
        doBlockEnd(block);
    }

    protected abstract boolean peephole(ValueNode valueNode);

    private boolean hasBlockEnd(Block block) {
        List<LIRInstruction> ops = res.getLIR().getLIRforBlock(block);
        if (ops.size() == 0) {
            return false;
        }
        return ops.get(ops.size() - 1) instanceof BlockEndOp;
    }

    private void doRoot(ValueNode instr) {
        if (traceLevel >= 2) {
            TTY.println("Emitting LIR for instruction " + instr);
        }
        currentInstruction = instr;

        Debug.log("Visiting %s", instr);
        emitNode(instr);
        Debug.log("Operand for %s = %s", instr, operand(instr));
    }

    protected void emitNode(ValueNode node) {
        if (Debug.isLogEnabled() && node.stamp() instanceof IllegalStamp) {
            Debug.log("This node has invalid type, we are emitting dead code(?): %s", node);
        }
        if (node instanceof LIRGenLowerable) {
            ((LIRGenLowerable) node).generate(this);
        } else if (node instanceof LIRGenResLowerable) {
            ((LIRGenResLowerable) node).generate(this, res);
        } else if (node instanceof LIRLowerable) {
            ((LIRLowerable) node).generate(this);
        } else if (node instanceof ArithmeticLIRLowerable) {
            ((ArithmeticLIRLowerable) node).generate(this);
        } else {
            throw GraalInternalError.shouldNotReachHere("node is not LIRLowerable: " + node);
        }
    }

    protected void emitPrologue(StructuredGraph graph) {
        CallingConvention incomingArguments = gen.getCallingConvention();

        Value[] params = new Value[incomingArguments.getArgumentCount()];
        for (int i = 0; i < params.length; i++) {
            params[i] = toStackKind(incomingArguments.getArgument(i));
            if (ValueUtil.isStackSlot(params[i])) {
                StackSlot slot = ValueUtil.asStackSlot(params[i]);
                if (slot.isInCallerFrame() && !res.getLIR().hasArgInCallerFrame()) {
                    res.getLIR().setHasArgInCallerFrame();
                }
            }
        }

        emitIncomingValues(params);

        for (ParameterNode param : graph.getNodes(ParameterNode.class)) {
            Value paramValue = params[param.index()];
            assert paramValue.getKind() == param.getKind().getStackKind();
            setResult(param, gen.emitMove(paramValue));
        }
    }

    protected <T extends AbstractBlock<T>> void emitPrologue(ResolvedJavaMethod method, BytecodeParser<T> parser) {
        CallingConvention incomingArguments = gen.getCallingConvention();

        Value[] params = new Value[incomingArguments.getArgumentCount()];
        for (int i = 0; i < params.length; i++) {
            params[i] = toStackKind(incomingArguments.getArgument(i));
            if (ValueUtil.isStackSlot(params[i])) {
                StackSlot slot = ValueUtil.asStackSlot(params[i]);
                if (slot.isInCallerFrame() && !res.getLIR().hasArgInCallerFrame()) {
                    res.getLIR().setHasArgInCallerFrame();
                }
            }
        }

        emitIncomingValues(params);

        Signature sig = method.getSignature();
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        for (int i = 0; i < sig.getParameterCount(!isStatic); i++) {
            Value paramValue = params[i];
            assert paramValue.getKind() == sig.getParameterKind(i).getStackKind();
            // TODO setResult(param, emitMove(paramValue));
            parser.setParameter(i, gen.emitMove(paramValue));
        }

        // return arguments;
    }

    public void emitIncomingValues(Value[] params) {
        ((LabelOp) res.getLIR().getLIRforBlock(gen.getCurrentBlock()).get(0)).setIncomingValues(params);
    }

    @Override
    public void visitReturn(ReturnNode x) {
        AllocatableValue operand = ILLEGAL;
        if (x.result() != null) {
            operand = resultOperandFor(x.result().getKind());
            gen.emitMove(operand, operand(x.result()));
        }
        gen.emitReturn(operand);
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
        if (traceLevel >= 1) {
            TTY.println("MOVE TO PHI from " + pred + " to " + merge);
        }
        PhiResolver resolver = new PhiResolver(gen);
        for (PhiNode phi : merge.phis()) {
            if (phi.type() == PhiType.Value) {
                ValueNode curVal = phi.valueAt(pred);
                resolver.move(operandForPhi(phi), operand(curVal));
            }
        }
        resolver.dispose();

        append(new JumpOp(getLIRBlock(merge)));
    }

    protected PlatformKind getPhiKind(PhiNode phi) {
        return phi.getKind();
    }

    private Value operandForPhi(PhiNode phi) {
        assert phi.type() == PhiType.Value : "wrong phi type: " + phi;
        Value result = operand(phi);
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
        gen.emitCompareBranch(operand(node.object()), Constant.NULL_OBJECT, Condition.EQ, false, trueSuccessor, falseSuccessor, trueSuccessorProbability);
    }

    public void emitCompareBranch(CompareNode compare, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability) {
        gen.emitCompareBranch(operand(compare.x()), operand(compare.y()), compare.condition(), compare.unorderedIsTrue(), trueSuccessor, falseSuccessor, trueSuccessorProbability);
    }

    public void emitIntegerTestBranch(IntegerTestNode test, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability) {
        gen.emitIntegerTestBranch(operand(test.x()), operand(test.y()), trueSuccessor, falseSuccessor, trueSuccessorProbability);
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
            return gen.emitConditionalMove(operand(isNullNode.object()), Constant.NULL_OBJECT, Condition.EQ, false, trueValue, falseValue);
        } else if (node instanceof CompareNode) {
            CompareNode compare = (CompareNode) node;
            return gen.emitConditionalMove(operand(compare.x()), operand(compare.y()), compare.condition(), compare.unorderedIsTrue(), trueValue, falseValue);
        } else if (node instanceof LogicConstantNode) {
            return gen.emitMove(((LogicConstantNode) node).getValue() ? trueValue : falseValue);
        } else if (node instanceof IntegerTestNode) {
            IntegerTestNode test = (IntegerTestNode) node;
            return gen.emitIntegerTestMove(operand(test.x()), operand(test.y()), trueValue, falseValue);
        } else {
            throw GraalInternalError.unimplemented(node.toString());
        }
    }

// public abstract void emitJump(LabelRef label);
//
// public abstract void emitCompareBranch(Value left, Value right, Condition cond, boolean
// unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination, double
// trueDestinationProbability);
//
// public abstract void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, double
// overflowProbability);
//
// public abstract void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination,
// LabelRef falseDestination, double trueSuccessorProbability);
//
// public abstract Variable emitConditionalMove(Value leftVal, Value right, Condition cond, boolean
// unorderedIsTrue, Value trueValue, Value falseValue);
//
// public abstract Variable emitIntegerTestMove(Value leftVal, Value right, Value trueValue, Value
// falseValue);

    @Override
    public void emitInvoke(Invoke x) {
        LoweredCallTargetNode callTarget = (LoweredCallTargetNode) x.callTarget();
        CallingConvention invokeCc = res.getFrameMap().registerConfig.getCallingConvention(callTarget.callType(), x.asNode().stamp().javaType(gen.getMetaAccess()), callTarget.signature(),
                        gen.target(), false);
        res.getFrameMap().callsMethod(invokeCc);

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

    protected static AllocatableValue toStackKind(AllocatableValue value) {
        if (value.getKind().getStackKind() != value.getKind()) {
            // We only have stack-kinds in the LIR, so convert the operand kind for values from the
            // calling convention.
            if (isRegister(value)) {
                return asRegister(value).asValue(value.getKind().getStackKind());
            } else if (isStackSlot(value)) {
                return StackSlot.get(value.getKind().getStackKind(), asStackSlot(value).getRawOffset(), asStackSlot(value).getRawAddFrameSize());
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        }
        return value;
    }

    @Override
    public Value[] visitInvokeArguments(CallingConvention invokeCc, Collection<ValueNode> arguments) {
        // for each argument, load it into the correct location
        Value[] result = new Value[arguments.size()];
        int j = 0;
        for (ValueNode arg : arguments) {
            if (arg != null) {
                AllocatableValue operand = toStackKind(invokeCc.getArgument(j));
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
                gen.emitCompareBranch(gen.load(operand(x.value())), x.keyAt(0), Condition.EQ, false, getLIRBlock(x.keySuccessor(0)), defaultTarget, probability);
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

    public void emitOverflowCheckBranch(AbstractBeginNode overflowSuccessor, AbstractBeginNode next, double probability) {
        gen.emitOverflowCheckBranch(getLIRBlock(overflowSuccessor), getLIRBlock(next), probability);
    }

    public void emitArrayEquals(Kind kind, Variable result, Value array1, Value array2, Value length) {
        gen.emitArrayEquals(kind, result, array1, array2, length);
    }

    public Variable newVariable(Kind i) {
        return gen.newVariable(i);
    }

    public void emitBitCount(Variable result, Value operand) {
        gen.emitBitCount(result, operand);
    }

    public void emitBitScanForward(Variable result, Value operand) {
        gen.emitBitScanForward(result, operand);
    }

    public void emitBitScanReverse(Variable result, Value operand) {
        gen.emitBitScanReverse(result, operand);
    }

    @Override
    public LIRGenerator getLIRGeneratorTool() {
        return gen;
    }

    public LIRGenerator getLIRGenerator() {
        return gen;
    }
}
