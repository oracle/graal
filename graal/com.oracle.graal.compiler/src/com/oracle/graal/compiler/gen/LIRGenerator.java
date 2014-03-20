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
import static com.oracle.graal.lir.LIRValueUtil.*;
import static com.oracle.graal.nodes.ConstantNode.*;
import static com.oracle.graal.phases.GraalOptions.*;

import java.util.*;
import java.util.Map.Entry;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.BlockEndOp;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.StandardOp.NoOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.options.*;
import com.oracle.graal.phases.util.*;

/**
 * This class traverses the HIR instructions and generates LIR instructions from them.
 */
public abstract class LIRGenerator implements LIRGeneratorTool, LIRTypeTool {

    public static class Options {
        // @formatter:off
        @Option(help = "Print HIR along side LIR as the latter is generated")
        public static final OptionValue<Boolean> PrintIRWithLIR = new OptionValue<>(false);
        @Option(help = "The trace level for the LIR generator")
        public static final OptionValue<Integer> TraceLIRGeneratorLevel = new OptionValue<>(0);
        // @formatter:on
    }

    public final FrameMap frameMap;
    public final NodeMap<Value> nodeOperands;
    public final LIR lir;

    private final Providers providers;
    protected final CallingConvention cc;

    protected final DebugInfoBuilder debugInfoBuilder;

    protected Block currentBlock;
    private final int traceLevel;
    private final boolean printIRWithLIR;

    /**
     * Handle for an operation that loads a constant into a variable. The operation starts in the
     * first block where the constant is used but will eventually be
     * {@linkplain LIRGenerator#insertConstantLoads() moved} to a block dominating all usages of the
     * constant.
     */
    public static class LoadConstant implements Comparable<LoadConstant> {
        /**
         * The index of {@link #op} within {@link #block}'s instruction list or -1 if {@code op} is
         * to be moved to a dominator block.
         */
        int index;

        /**
         * The operation that loads the constant.
         */
        private final LIRInstruction op;

        /**
         * The block that does or will contain {@link #op}. This is initially the block where the
         * first usage of the constant is seen during LIR generation.
         */
        private Block block;

        /**
         * The variable into which the constant is loaded.
         */
        private final Variable variable;

        public LoadConstant(Variable variable, Block block, int index, LIRInstruction op) {
            this.variable = variable;
            this.block = block;
            this.index = index;
            this.op = op;
        }

        /**
         * Sorts {@link LoadConstant} objects according to their enclosing blocks. This is used to
         * group loads per block in {@link LIRGenerator#insertConstantLoads()}.
         */
        public int compareTo(LoadConstant o) {
            if (block.getId() < o.block.getId()) {
                return -1;
            }
            if (block.getId() > o.block.getId()) {
                return 1;
            }
            return 0;
        }

        @Override
        public String toString() {
            return block + "#" + op;
        }

        /**
         * Removes the {@link #op} from its original location if it is still at that location.
         */
        public void unpin(LIR lir) {
            if (index >= 0) {
                // Replace the move with a filler op so that the operation
                // list does not need to be adjusted.
                List<LIRInstruction> instructions = lir.lir(block);
                instructions.set(index, new NoOp(null, -1));
                index = -1;
            }
        }
    }

    private Map<Constant, LoadConstant> constantLoads;

    private ValueNode currentInstruction;
    private ValueNode lastInstructionPrinted; // Debugging only

    /**
     * Records whether the code being generated makes at least one foreign call.
     */
    private boolean hasForeignCall;

    /**
     * Checks whether the supplied constant can be used without loading it into a register for store
     * operations, i.e., on the right hand side of a memory access.
     * 
     * @param c The constant to check.
     * @return True if the constant can be used directly, false if the constant needs to be in a
     *         register.
     */
    public abstract boolean canStoreConstant(Constant c, boolean isCompressed);

    public LIRGenerator(StructuredGraph graph, Providers providers, FrameMap frameMap, CallingConvention cc, LIR lir) {
        this.providers = providers;
        this.frameMap = frameMap;
        this.cc = cc;
        this.nodeOperands = graph.createNodeMap();
        this.lir = lir;
        this.debugInfoBuilder = createDebugInfoBuilder(nodeOperands);
        this.traceLevel = Options.TraceLIRGeneratorLevel.getValue();
        this.printIRWithLIR = Options.PrintIRWithLIR.getValue();
    }

    /**
     * Returns true if the redundant move elimination optimization should be done after register
     * allocation.
     */
    public boolean canEliminateRedundantMoves() {
        return true;
    }

    @SuppressWarnings("hiding")
    protected DebugInfoBuilder createDebugInfoBuilder(NodeMap<Value> nodeOperands) {
        return new DebugInfoBuilder(nodeOperands);
    }

    @Override
    public TargetDescription target() {
        return getCodeCache().getTarget();
    }

    protected Providers getProviders() {
        return providers;
    }

    @Override
    public MetaAccessProvider getMetaAccess() {
        return providers.getMetaAccess();
    }

    @Override
    public CodeCacheProvider getCodeCache() {
        return providers.getCodeCache();
    }

    @Override
    public ForeignCallsProvider getForeignCalls() {
        return providers.getForeignCalls();
    }

    /**
     * Determines whether the code being generated makes at least one foreign call.
     */
    public boolean hasForeignCall() {
        return hasForeignCall;
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
                if (canInlineConstant(value)) {
                    return setResult(node, value);
                } else {
                    Variable loadedValue;
                    if (constantLoads == null) {
                        constantLoads = new HashMap<>();
                    }
                    LoadConstant load = constantLoads.get(value);
                    if (load == null) {
                        int index = lir.lir(currentBlock).size();
                        loadedValue = emitMove(value);
                        LIRInstruction op = lir.lir(currentBlock).get(index);
                        constantLoads.put(value, new LoadConstant(loadedValue, currentBlock, index, op));
                    } else {
                        Block dominator = ControlFlowGraph.commonDominator(load.block, currentBlock);
                        loadedValue = load.variable;
                        if (dominator != load.block) {
                            load.unpin(lir);
                        } else {
                            assert load.block != currentBlock || load.index < lir.lir(currentBlock).size();
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
        for (Entry<Node, Value> entry : nodeOperands.entries()) {
            if (entry.getValue().equals(value)) {
                return (ValueNode) entry.getKey();
            }
        }
        return null;
    }

    /**
     * Creates a new {@linkplain Variable variable}.
     * 
     * @param platformKind The kind of the new variable.
     * @return a new variable
     */
    @Override
    public Variable newVariable(PlatformKind platformKind) {
        return new Variable(platformKind, lir.nextVariable());
    }

    @Override
    public RegisterAttributes attributes(Register register) {
        return frameMap.registerConfig.getAttributesMap()[register.number];
    }

    @Override
    public Value setResult(ValueNode x, Value operand) {
        assert (!isRegister(operand) || !attributes(asRegister(operand)).isAllocatable());
        assert nodeOperands == null || nodeOperands.get(x) == null : "operand cannot be set twice";
        assert operand != null && isLegal(operand) : "operand must be legal";
        assert operand.getKind().getStackKind() == x.getKind() || x.getKind() == Kind.Illegal : operand.getKind().getStackKind() + " must match " + x.getKind();
        assert !(x instanceof VirtualObjectNode);
        nodeOperands.set(x, operand);
        return operand;
    }

    @Override
    public abstract Variable emitMove(Value input);

    public AllocatableValue asAllocatable(Value value) {
        if (isAllocatableValue(value)) {
            return asAllocatableValue(value);
        } else {
            return emitMove(value);
        }
    }

    public Variable load(Value value) {
        if (!isVariable(value)) {
            return emitMove(value);
        }
        return (Variable) value;
    }

    public Value loadNonConst(Value value) {
        if (isConstant(value) && !canInlineConstant((Constant) value)) {
            return emitMove(value);
        }
        return value;
    }

    public LabelRef getLIRBlock(FixedNode b) {
        Block result = lir.getControlFlowGraph().blockFor(b);
        int suxIndex = currentBlock.getSuccessors().indexOf(result);
        assert suxIndex != -1 : "Block not in successor list of current block";

        return LabelRef.forSuccessor(lir, currentBlock, suxIndex);
    }

    /**
     * Determines if only oop maps are required for the code generated from the LIR.
     */
    protected boolean needOnlyOopMaps() {
        return false;
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
        if (needOnlyOopMaps()) {
            return new LIRFrameState(null, null, null);
        }
        assert state != null;
        return debugInfoBuilder.build(state, exceptionEdge);
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
        return frameMap.registerConfig.getReturnRegister(kind).asValue(kind);
    }

    public void append(LIRInstruction op) {
        if (printIRWithLIR && !TTY.isSuppressed()) {
            if (currentInstruction != null && lastInstructionPrinted != currentInstruction) {
                lastInstructionPrinted = currentInstruction;
                InstructionPrinter ip = new InstructionPrinter(TTY.out());
                ip.printInstructionListing(currentInstruction);
            }
            TTY.println(op.toStringWithIdPrefix());
            TTY.println();
        }
        assert LIRVerifier.verify(op);
        lir.lir(currentBlock).add(op);
    }

    public void doBlock(Block block, StructuredGraph graph, BlockMap<List<ScheduledNode>> blockMap) {
        if (printIRWithLIR) {
            TTY.print(block.toString());
        }

        currentBlock = block;

        // set up the list of LIR instructions
        assert lir.lir(block) == null : "LIR list already computed for this block";
        lir.setLir(block, new ArrayList<LIRInstruction>());

        append(new LabelOp(new Label(block.getId()), block.isAligned()));

        if (traceLevel >= 1) {
            TTY.println("BEGIN Generating LIR for block B" + block.getId());
        }

        if (block == lir.getControlFlowGraph().getStartBlock()) {
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
            emitJump(getLIRBlock((FixedNode) successors.first()));
        }

        assert verifyBlock(lir, block);

        if (traceLevel >= 1) {
            TTY.println("END Generating LIR for block B" + block.getId());
        }

        currentBlock = null;

        if (printIRWithLIR) {
            TTY.println();
        }
    }

    protected abstract boolean peephole(ValueNode valueNode);

    private boolean hasBlockEnd(Block block) {
        List<LIRInstruction> ops = lir.lir(block);
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
        } else if (node instanceof LIRLowerable) {
            ((LIRLowerable) node).generate(this);
        } else if (node instanceof ArithmeticLIRLowerable) {
            ((ArithmeticLIRLowerable) node).generate(this);
        } else {
            throw GraalInternalError.shouldNotReachHere("node is not LIRLowerable: " + node);
        }
    }

    protected void emitPrologue(StructuredGraph graph) {
        CallingConvention incomingArguments = cc;

        Value[] params = new Value[incomingArguments.getArgumentCount()];
        for (int i = 0; i < params.length; i++) {
            params[i] = toStackKind(incomingArguments.getArgument(i));
            if (ValueUtil.isStackSlot(params[i])) {
                StackSlot slot = ValueUtil.asStackSlot(params[i]);
                if (slot.isInCallerFrame() && !lir.hasArgInCallerFrame()) {
                    lir.setHasArgInCallerFrame();
                }
            }
        }

        emitIncomingValues(params);

        for (ParameterNode param : graph.getNodes(ParameterNode.class)) {
            Value paramValue = params[param.index()];
            assert paramValue.getKind() == param.getKind().getStackKind();
            setResult(param, emitMove(paramValue));
        }
    }

    public void emitIncomingValues(Value[] params) {
        ((LabelOp) lir.lir(currentBlock).get(0)).setIncomingValues(params);
    }

    @Override
    public void visitReturn(ReturnNode x) {
        AllocatableValue operand = ILLEGAL;
        if (x.result() != null) {
            operand = resultOperandFor(x.result().getKind());
            emitMove(operand, operand(x.result()));
        }
        emitReturn(operand);
    }

    protected abstract void emitReturn(Value input);

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
        PhiResolver resolver = new PhiResolver(this);
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
            Variable newOperand = newVariable(getPhiKind(phi));
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
        emitCompareBranch(operand(node.object()), Constant.NULL_OBJECT, Condition.EQ, false, trueSuccessor, falseSuccessor, trueSuccessorProbability);
    }

    public void emitCompareBranch(CompareNode compare, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability) {
        emitCompareBranch(operand(compare.x()), operand(compare.y()), compare.condition(), compare.unorderedIsTrue(), trueSuccessor, falseSuccessor, trueSuccessorProbability);
    }

    public void emitIntegerTestBranch(IntegerTestNode test, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability) {
        emitIntegerTestBranch(operand(test.x()), operand(test.y()), trueSuccessor, falseSuccessor, trueSuccessorProbability);
    }

    public void emitConstantBranch(boolean value, LabelRef trueSuccessorBlock, LabelRef falseSuccessorBlock) {
        LabelRef block = value ? trueSuccessorBlock : falseSuccessorBlock;
        emitJump(block);
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
            return emitConditionalMove(operand(isNullNode.object()), Constant.NULL_OBJECT, Condition.EQ, false, trueValue, falseValue);
        } else if (node instanceof CompareNode) {
            CompareNode compare = (CompareNode) node;
            return emitConditionalMove(operand(compare.x()), operand(compare.y()), compare.condition(), compare.unorderedIsTrue(), trueValue, falseValue);
        } else if (node instanceof LogicConstantNode) {
            return emitMove(((LogicConstantNode) node).getValue() ? trueValue : falseValue);
        } else if (node instanceof IntegerTestNode) {
            IntegerTestNode test = (IntegerTestNode) node;
            return emitIntegerTestMove(operand(test.x()), operand(test.y()), trueValue, falseValue);
        } else {
            throw GraalInternalError.unimplemented(node.toString());
        }
    }

    public abstract void emitJump(LabelRef label);

    public abstract void emitCompareBranch(Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability);

    public abstract void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, double overflowProbability);

    public abstract void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueSuccessorProbability);

    public abstract Variable emitConditionalMove(Value leftVal, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue);

    public abstract Variable emitIntegerTestMove(Value leftVal, Value right, Value trueValue, Value falseValue);

    @Override
    public void emitInvoke(Invoke x) {
        LoweredCallTargetNode callTarget = (LoweredCallTargetNode) x.callTarget();
        CallingConvention invokeCc = frameMap.registerConfig.getCallingConvention(callTarget.callType(), x.asNode().stamp().javaType(getMetaAccess()), callTarget.signature(), target(), false);
        frameMap.callsMethod(invokeCc);

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
            setResult(x.asNode(), emitMove(result));
        }

        if (x instanceof InvokeWithExceptionNode) {
            emitJump(getLIRBlock(((InvokeWithExceptionNode) x).next()));
        }
    }

    protected abstract void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState);

    protected abstract void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState);

    protected abstract void emitForeignCall(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info);

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

    public Value[] visitInvokeArguments(CallingConvention invokeCc, Collection<ValueNode> arguments) {
        // for each argument, load it into the correct location
        Value[] result = new Value[arguments.size()];
        int j = 0;
        for (ValueNode arg : arguments) {
            if (arg != null) {
                AllocatableValue operand = toStackKind(invokeCc.getArgument(j));
                emitMove(operand, operand(arg));
                result[j] = operand;
                j++;
            } else {
                throw GraalInternalError.shouldNotReachHere("I thought we no longer have null entries for two-slot types...");
            }
        }
        return result;
    }

    @Override
    public Variable emitForeignCall(ForeignCallLinkage linkage, DeoptimizingNode info, Value... args) {
        LIRFrameState state = null;
        if (linkage.canDeoptimize()) {
            if (info != null) {
                state = stateFor(info.getDeoptimizationState());
            } else {
                assert needOnlyOopMaps();
                state = new LIRFrameState(null, null, null);
            }
        }

        // move the arguments into the correct location
        CallingConvention linkageCc = linkage.getOutgoingCallingConvention();
        frameMap.callsMethod(linkageCc);
        assert linkageCc.getArgumentCount() == args.length : "argument count mismatch";
        Value[] argLocations = new Value[args.length];
        for (int i = 0; i < args.length; i++) {
            Value arg = args[i];
            AllocatableValue loc = linkageCc.getArgument(i);
            emitMove(loc, arg);
            argLocations[i] = loc;
        }
        this.hasForeignCall = true;
        emitForeignCall(linkage, linkageCc.getReturn(), argLocations, linkage.getTemporaries(), state);

        if (isLegal(linkageCc.getReturn())) {
            return emitMove(linkageCc.getReturn());
        } else {
            return null;
        }
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
            emitJump(defaultTarget);
        } else {
            Variable value = load(operand(x.value()));
            if (keyCount == 1) {
                assert defaultTarget != null;
                double probability = x.probability(x.keySuccessor(0));
                emitCompareBranch(load(operand(x.value())), x.keyAt(0), Condition.EQ, false, getLIRBlock(x.keySuccessor(0)), defaultTarget, probability);
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
                    emitStrategySwitch(new SwitchStrategy.SequentialStrategy(keyProbabilities, keyConstants), value, keyTargets, defaultTarget);
                } else {
                    emitStrategySwitch(keyConstants, keyProbabilities, keyTargets, defaultTarget, value);
                }
            }
        }
    }

    protected void emitStrategySwitch(Constant[] keyConstants, double[] keyProbabilities, LabelRef[] keyTargets, LabelRef defaultTarget, Variable value) {
        int keyCount = keyConstants.length;
        SwitchStrategy strategy = SwitchStrategy.getBestStrategy(keyProbabilities, keyConstants, keyTargets);
        long valueRange = keyConstants[keyCount - 1].asLong() - keyConstants[0].asLong() + 1;
        double tableSwitchDensity = keyCount / (double) valueRange;
        /*
         * This heuristic tries to find a compromise between the effort for the best switch strategy
         * and the density of a tableswitch. If the effort for the strategy is at least 4, then a
         * tableswitch is preferred if better than a certain value that starts at 0.5 and lowers
         * gradually with additional effort.
         */
        if (strategy.getAverageEffort() < 4 || tableSwitchDensity < (1 / Math.sqrt(strategy.getAverageEffort()))) {
            emitStrategySwitch(strategy, value, keyTargets, defaultTarget);
        } else {
            int minValue = keyConstants[0].asInt();
            assert valueRange < Integer.MAX_VALUE;
            LabelRef[] targets = new LabelRef[(int) valueRange];
            for (int i = 0; i < valueRange; i++) {
                targets[i] = defaultTarget;
            }
            for (int i = 0; i < keyCount; i++) {
                targets[keyConstants[i].asInt() - minValue] = keyTargets[i];
            }
            emitTableSwitch(minValue, defaultTarget, targets, value);
        }
    }

    protected abstract void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget);

    protected abstract void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, Value key);

    public FrameMap frameMap() {
        return frameMap;
    }

    @Override
    public void beforeRegisterAllocation() {
        insertConstantLoads();
    }

    /**
     * Moves deferred {@linkplain LoadConstant loads} of constants into blocks dominating all usages
     * of the constant. Any operations inserted into a block are guaranteed to be immediately prior
     * to the first control flow instruction near the end of the block.
     */
    private void insertConstantLoads() {
        if (constantLoads != null) {
            // Remove loads where all usages are in the same block.
            for (Iterator<Map.Entry<Constant, LoadConstant>> iter = constantLoads.entrySet().iterator(); iter.hasNext();) {
                LoadConstant lc = iter.next().getValue();

                // Move loads of constant outside of loops
                if (OptScheduleOutOfLoops.getValue()) {
                    Block outOfLoopDominator = lc.block;
                    while (outOfLoopDominator.getLoop() != null) {
                        outOfLoopDominator = outOfLoopDominator.getDominator();
                    }
                    if (outOfLoopDominator != lc.block) {
                        lc.unpin(lir);
                        lc.block = outOfLoopDominator;
                    }
                }

                if (lc.index != -1) {
                    assert lir.lir(lc.block).get(lc.index) == lc.op;
                    iter.remove();
                }
            }
            if (constantLoads.isEmpty()) {
                return;
            }

            // Sorting groups the loads per block.
            LoadConstant[] groupedByBlock = constantLoads.values().toArray(new LoadConstant[constantLoads.size()]);
            Arrays.sort(groupedByBlock);

            int groupBegin = 0;
            while (true) {
                int groupEnd = groupBegin + 1;
                Block block = groupedByBlock[groupBegin].block;
                while (groupEnd < groupedByBlock.length && groupedByBlock[groupEnd].block == block) {
                    groupEnd++;
                }
                int groupSize = groupEnd - groupBegin;

                List<LIRInstruction> ops = lir.lir(block);
                int lastIndex = ops.size() - 1;
                assert ops.get(lastIndex) instanceof BlockEndOp;
                int insertionIndex = lastIndex;
                for (int i = Math.max(0, lastIndex - MAX_EXCEPTION_EDGE_OP_DISTANCE_FROM_END); i < lastIndex; i++) {
                    if (getExceptionEdge(ops.get(i)) != null) {
                        insertionIndex = i;
                        break;
                    }
                }

                if (groupSize == 1) {
                    ops.add(insertionIndex, groupedByBlock[groupBegin].op);
                } else {
                    assert groupSize > 1;
                    List<LIRInstruction> moves = new ArrayList<>(groupSize);
                    for (int i = groupBegin; i < groupEnd; i++) {
                        moves.add(groupedByBlock[i].op);
                    }
                    ops.addAll(insertionIndex, moves);
                }

                if (groupEnd == groupedByBlock.length) {
                    break;
                }
                groupBegin = groupEnd;
            }
            constantLoads = null;
        }
    }

    /**
     * Gets a garbage value for a given kind.
     */
    protected Constant zapValueForKind(PlatformKind kind) {
        long dead = 0xDEADDEADDEADDEADL;
        switch ((Kind) kind) {
            case Boolean:
                return Constant.FALSE;
            case Byte:
                return Constant.forByte((byte) dead);
            case Char:
                return Constant.forChar((char) dead);
            case Short:
                return Constant.forShort((short) dead);
            case Int:
                return Constant.forInt((int) dead);
            case Double:
                return Constant.forDouble(Double.longBitsToDouble(dead));
            case Float:
                return Constant.forFloat(Float.intBitsToFloat((int) dead));
            case Long:
                return Constant.forLong(dead);
            case Object:
                return Constant.NULL_OBJECT;
            default:
                throw new IllegalArgumentException(kind.toString());
        }
    }

    /**
     * Default implementation: Return the Java stack kind for each stamp.
     */
    public PlatformKind getPlatformKind(Stamp stamp) {
        return stamp.getPlatformKind(this);
    }

    public PlatformKind getIntegerKind(int bits, boolean unsigned) {
        if (bits > 32) {
            return Kind.Long;
        } else {
            return Kind.Int;
        }
    }

    public PlatformKind getFloatingKind(int bits) {
        switch (bits) {
            case 32:
                return Kind.Float;
            case 64:
                return Kind.Double;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    public PlatformKind getObjectKind() {
        return Kind.Object;
    }

    public abstract void emitBitCount(Variable result, Value operand);

    public abstract void emitBitScanForward(Variable result, Value operand);

    public abstract void emitBitScanReverse(Variable result, Value operand);

    public abstract void emitByteSwap(Variable result, Value operand);

    public abstract void emitArrayEquals(Kind kind, Variable result, Value array1, Value array2, Value length);
}
