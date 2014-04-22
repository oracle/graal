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
import static com.oracle.graal.lir.LIR.*;
import static com.oracle.graal.nodes.ConstantNode.*;

import java.util.*;
import java.util.Map.Entry;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.compiler.gen.LIRGenerator.LoadConstant;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * This class traverses the HIR instructions and generates LIR instructions from them.
 */
public abstract class NodeLIRBuilder implements NodeLIRBuilderTool {

    private final NodeMap<Value> nodeOperands;
    private final DebugInfoBuilder debugInfoBuilder;

    protected final LIRGenerator gen;

    private ValueNode currentInstruction;
    private ValueNode lastInstructionPrinted; // Debugging only

    public NodeLIRBuilder(StructuredGraph graph, LIRGenerator gen) {
        this.gen = gen;
        this.nodeOperands = graph.createNodeMap();
        this.debugInfoBuilder = createDebugInfoBuilder(nodeOperands);
        gen.setDebugInfoBuilder(debugInfoBuilder);
    }

    @SuppressWarnings("hiding")
    protected DebugInfoBuilder createDebugInfoBuilder(NodeMap<Value> nodeOperands) {
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
        Value operand = nodeOperands.get(node);
        if (operand == null) {
            operand = getConstantOperand(node);
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
                        int index = gen.getResult().getLIR().getLIRforBlock(gen.getCurrentBlock()).size();
                        loadedValue = gen.emitMove(value);
                        LIRInstruction op = gen.getResult().getLIR().getLIRforBlock(gen.getCurrentBlock()).get(index);
                        gen.constantLoads.put(value, new LoadConstant(loadedValue, gen.getCurrentBlock(), index, op));
                    } else {
                        AbstractBlock<?> dominator = ControlFlowGraph.commonDominator((Block) load.block, (Block) gen.getCurrentBlock());
                        loadedValue = load.variable;
                        if (dominator != load.block) {
                            load.unpin(gen.getResult().getLIR());
                        } else {
                            assert load.block != gen.getCurrentBlock() || load.index < gen.getResult().getLIR().getLIRforBlock(gen.getCurrentBlock()).size();
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
        assert !(x instanceof VirtualObjectNode);
        nodeOperands.set(x, operand);
        return operand;
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
        if (gen.printIRWithLIR && !TTY.isSuppressed()) {
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
        int instructionsFolded = 0;
        for (int i = 0; i < nodes.size(); i++) {
            Node instr = nodes.get(i);
            if (gen.traceLevel >= 3) {
                TTY.println("LIRGen for " + instr);
            }
            if (instructionsFolded > 0) {
                instructionsFolded--;
                continue;
            }
            if (!ConstantNodeRecordsUsages && instr instanceof ConstantNode) {
                // Loading of constants is done lazily by operand()

            } else if (instr instanceof ValueNode) {
                ValueNode valueNode = (ValueNode) instr;
                if (!hasOperand(valueNode)) {
                    if (!peephole(valueNode)) {
                        instructionsFolded = maybeFoldMemory(nodes, i, valueNode);
                        if (instructionsFolded == 0) {
                            try {
                                doRoot((ValueNode) instr);
                            } catch (GraalInternalError e) {
                                throw GraalGraphInternalError.transformAndAddContext(e, instr);
                            } catch (Throwable e) {
                                throw new GraalGraphInternalError(e).addContext(instr);
                            }
                        }
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

    private static final DebugMetric MemoryFoldSuccess = Debug.metric("MemoryFoldSuccess");
    private static final DebugMetric MemoryFoldFailed = Debug.metric("MemoryFoldFailed");
    private static final DebugMetric MemoryFoldFailedNonAdjacent = Debug.metric("MemoryFoldedFailedNonAdjacent");
    private static final DebugMetric MemoryFoldFailedDifferentBlock = Debug.metric("MemoryFoldedFailedDifferentBlock");

    /**
     * Subclass can provide helper to fold memory operations into other operations.
     */
    public MemoryArithmeticLIRLowerer getMemoryLowerer() {
        return null;
    }

    private static final Object LOG_OUTPUT_LOCK = new Object();

    /**
     * Try to find a sequence of Nodes which can be passed to the backend to look for optimized
     * instruction sequences using memory. Currently this basically is a read with a single
     * arithmetic user followed by an possible if use. This should generalized to more generic
     * pattern matching so that it can be more flexibly used.
     */
    private int maybeFoldMemory(List<ScheduledNode> nodes, int i, ValueNode access) {
        MemoryArithmeticLIRLowerer lowerer = getMemoryLowerer();
        if (lowerer != null && GraalOptions.OptFoldMemory.getValue() && (access instanceof ReadNode || access instanceof FloatingReadNode) && access.usages().count() == 1 && i + 1 < nodes.size()) {
            try (Scope s = Debug.scope("MaybeFoldMemory", access)) {
                // This is all bit hacky since it's happening on the linearized schedule. This needs
                // to be revisited at some point.

                // Uncast the memory operation.
                Node use = access.usages().first();
                if (use instanceof UnsafeCastNode && use.usages().count() == 1) {
                    use = use.usages().first();
                }

                // Find a memory lowerable usage of this operation
                if (use instanceof MemoryArithmeticLIRLowerable) {
                    ValueNode operation = (ValueNode) use;
                    if (!nodes.contains(operation)) {
                        Debug.log("node %1s in different block from %1s", access, operation);
                        MemoryFoldFailedDifferentBlock.increment();
                        return 0;
                    }
                    ValueNode firstOperation = operation;
                    if (operation instanceof LogicNode) {
                        if (operation.usages().count() == 1 && operation.usages().first() instanceof IfNode) {
                            ValueNode ifNode = (ValueNode) operation.usages().first();
                            if (!nodes.contains(ifNode)) {
                                MemoryFoldFailedDifferentBlock.increment();
                                Debug.log("if node %1s in different block from %1s", ifNode, operation);
                                try (Indent indent = Debug.logAndIndent("checking operations")) {
                                    int start = nodes.indexOf(access);
                                    int end = nodes.indexOf(operation);
                                    for (int i1 = Math.min(start, end); i1 <= Math.max(start, end); i1++) {
                                        Debug.log("%d: (%d) %1s", i1, nodes.get(i1).usages().count(), nodes.get(i1));
                                    }
                                }
                                return 0;
                            } else {
                                operation = ifNode;
                            }
                        }
                    }
                    if (Debug.isLogEnabled()) {
                        synchronized (LOG_OUTPUT_LOCK) {  // Hack to ensure the output is grouped.
                            try (Indent indent = Debug.logAndIndent("checking operations")) {
                                int start = nodes.indexOf(access);
                                int end = nodes.indexOf(operation);
                                for (int i1 = Math.min(start, end); i1 <= Math.max(start, end); i1++) {
                                    Debug.log("%d: (%d) %1s", i1, nodes.get(i1).usages().count(), nodes.get(i1));
                                }
                            }
                        }
                    }
                    // Possible lowerable operation in the same block. Check out the dependencies.
                    int opIndex = nodes.indexOf(operation);
                    int current = i + 1;
                    ArrayList<ValueNode> deferred = null;
                    for (; current < opIndex; current++) {
                        ScheduledNode node = nodes.get(current);
                        if (node != firstOperation) {
                            if (node instanceof LocationNode || node instanceof VirtualObjectNode) {
                                // nothing to do
                                continue;
                            } else if (node instanceof ConstantNode) {
                                if (deferred == null) {
                                    deferred = new ArrayList<>(2);
                                }
                                // These nodes are collected and the backend is expended to
                                // evaluate them before generating the lowered form. This
                                // basically works around unfriendly scheduling of values which
                                // are defined in a block but not used there.
                                deferred.add((ValueNode) node);
                                continue;
                            } else if (node instanceof UnsafeCastNode) {
                                UnsafeCastNode cast = (UnsafeCastNode) node;
                                if (cast.getOriginalNode() == access) {
                                    continue;
                                }
                            }

                            // Unexpected inline node
                            // Debug.log("unexpected node %1s", node);
                            break;
                        }
                    }

                    if (current == opIndex) {
                        if (lowerer.memoryPeephole((Access) access, (MemoryArithmeticLIRLowerable) operation, deferred)) {
                            MemoryFoldSuccess.increment();
                            // if this operation had multiple access inputs, then previous attempts
                            // would be marked as failures which is wrong. Try to adjust the
                            // counters to account for this.
                            for (Node input : operation.inputs()) {
                                if (input == access) {
                                    continue;
                                }
                                if (input instanceof Access && nodes.contains(input)) {
                                    MemoryFoldFailedNonAdjacent.add(-1);
                                }
                            }
                            if (deferred != null) {
                                // Ensure deferred nodes were evaluated
                                for (ValueNode node : deferred) {
                                    assert hasOperand(node);
                                }
                            }
                            return opIndex - i;
                        } else {
                            // This isn't true failure, it just means there wasn't match for the
                            // pattern. Usually that means it's just not supported by the backend.
                            MemoryFoldFailed.increment();
                            return 0;
                        }
                    } else {
                        MemoryFoldFailedNonAdjacent.increment();
                    }
                } else if (!(use instanceof Access) && !(use instanceof PhiNode) && use.usages().count() == 1) {
                    // memory usage which isn't considered lowerable. Mostly these are
                    // uninteresting but it might be worth looking at to ensure that interesting
                    // nodes are being properly handled.
                    // Debug.log("usage isn't lowerable %1s", access.usages().first());
                }
            }
        }
        return 0;
    }

    protected abstract boolean peephole(ValueNode valueNode);

    private void doRoot(ValueNode instr) {
        if (gen.traceLevel >= 2) {
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
        if (node instanceof LIRGenLowerable) {
            ((LIRGenLowerable) node).generate(this);
        } else if (node instanceof LIRGenResLowerable) {
            ((LIRGenResLowerable) node).generate(this, gen.getResult());
        } else if (node instanceof LIRLowerable) {
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
        if (gen.traceLevel >= 1) {
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

        append(new JumpOp(getLIRBlock(merge)));
    }

    protected PlatformKind getPhiKind(PhiNode phi) {
        return gen.getPlatformKind(phi.stamp());
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
        PlatformKind kind = gen.getPlatformKind(node.object().stamp());
        gen.emitCompareBranch(kind, operand(node.object()), Constant.NULL_OBJECT, Condition.EQ, false, trueSuccessor, falseSuccessor, trueSuccessorProbability);
    }

    public void emitCompareBranch(CompareNode compare, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability) {
        PlatformKind kind = gen.getPlatformKind(compare.x().stamp());
        gen.emitCompareBranch(kind, operand(compare.x()), operand(compare.y()), compare.condition(), compare.unorderedIsTrue(), trueSuccessor, falseSuccessor, trueSuccessorProbability);
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
            PlatformKind kind = gen.getPlatformKind(isNullNode.object().stamp());
            return gen.emitConditionalMove(kind, operand(isNullNode.object()), Constant.NULL_OBJECT, Condition.EQ, false, trueValue, falseValue);
        } else if (node instanceof CompareNode) {
            CompareNode compare = (CompareNode) node;
            PlatformKind kind = gen.getPlatformKind(compare.x().stamp());
            return gen.emitConditionalMove(kind, operand(compare.x()), operand(compare.y()), compare.condition(), compare.unorderedIsTrue(), trueValue, falseValue);
        } else if (node instanceof LogicConstantNode) {
            return gen.emitMove(((LogicConstantNode) node).getValue() ? trueValue : falseValue);
        } else if (node instanceof IntegerTestNode) {
            IntegerTestNode test = (IntegerTestNode) node;
            return gen.emitIntegerTestMove(operand(test.x()), operand(test.y()), trueValue, falseValue);
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
        LIRFrameState callState = gen.stateWithExceptionEdge(x, exceptionEdge);

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
                PlatformKind kind = gen.getPlatformKind(x.value().stamp());
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

    public void emitOverflowCheckBranch(BeginNode overflowSuccessor, BeginNode next, double probability) {
        gen.emitOverflowCheckBranch(getLIRBlock(overflowSuccessor), getLIRBlock(next), probability);
    }

    public final void emitArrayEquals(Kind kind, Variable result, Value array1, Value array2, Value length) {
        gen.emitArrayEquals(kind, result, array1, array2, length);
    }

    public final Variable newVariable(Kind i) {
        return gen.newVariable(i);
    }

    public final void emitBitCount(Variable result, Value operand) {
        gen.emitBitCount(result, operand);
    }

    public final void emitBitScanForward(Variable result, Value operand) {
        gen.emitBitScanForward(result, operand);
    }

    final void emitBitScanReverse(Variable result, Value operand) {
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
