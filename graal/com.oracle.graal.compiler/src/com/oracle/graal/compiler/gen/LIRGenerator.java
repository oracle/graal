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

import static com.oracle.graal.api.code.CallingConvention.Type.*;
import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.api.meta.Value.*;
import static com.oracle.graal.lir.LIRValueUtil.*;
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
import com.oracle.graal.phases.util.*;

/**
 * This class traverses the HIR instructions and generates LIR instructions from them.
 */
public abstract class LIRGenerator implements LIRGeneratorTool {

    public final FrameMap frameMap;
    public final NodeMap<Value> nodeOperands;
    public final LIR lir;

    protected final StructuredGraph graph;
    protected final MetaAccessProvider metaAccess;
    protected final CodeCacheProvider codeCache;
    protected final ForeignCallsProvider foreignCalls;
    protected final TargetDescription target;
    protected final CallingConvention cc;

    protected final DebugInfoBuilder debugInfoBuilder;

    protected Block currentBlock;
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
    public abstract boolean canStoreConstant(Constant c);

    public LIRGenerator(StructuredGraph graph, Providers providers, TargetDescription target, FrameMap frameMap, CallingConvention cc, LIR lir) {
        this.graph = graph;
        this.metaAccess = providers.getMetaAccess();
        this.codeCache = providers.getCodeCache();
        this.foreignCalls = providers.getForeignCalls();
        this.target = target;
        this.frameMap = frameMap;
        if (graph.getEntryBCI() == StructuredGraph.INVOCATION_ENTRY_BCI) {
            this.cc = cc;
        } else {
            JavaType[] parameterTypes = new JavaType[]{metaAccess.lookupJavaType(long.class)};
            CallingConvention tmp = frameMap.registerConfig.getCallingConvention(JavaCallee, metaAccess.lookupJavaType(void.class), parameterTypes, target, false);
            this.cc = new CallingConvention(cc.getStackSize(), cc.getReturn(), tmp.getArgument(0));
        }
        this.nodeOperands = graph.createNodeMap();
        this.lir = lir;
        this.debugInfoBuilder = createDebugInfoBuilder(nodeOperands);
    }

    @SuppressWarnings("hiding")
    protected DebugInfoBuilder createDebugInfoBuilder(NodeMap<Value> nodeOperands) {
        return new DebugInfoBuilder(nodeOperands);
    }

    @Override
    public TargetDescription target() {
        return target;
    }

    @Override
    public MetaAccessProvider getMetaAccess() {
        return metaAccess;
    }

    @Override
    public CodeCacheProvider getCodeCache() {
        return codeCache;
    }

    @Override
    public ForeignCallsProvider getForeignCalls() {
        return foreignCalls;
    }

    public StructuredGraph getGraph() {
        return graph;
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
        return nodeOperands.get(node);
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
        PlatformKind stackKind;
        if (platformKind instanceof Kind) {
            stackKind = ((Kind) platformKind).getStackKind();
        } else {
            stackKind = platformKind;
        }
        return new Variable(stackKind, lir.nextVariable());
    }

    @Override
    public RegisterAttributes attributes(Register register) {
        return frameMap.registerConfig.getAttributesMap()[register.number];
    }

    @Override
    public Value setResult(ValueNode x, Value operand) {
        assert (!isRegister(operand) || !attributes(asRegister(operand)).isAllocatable());
        assert operand(x) == null : "operand cannot be set twice";
        assert operand != null && isLegal(operand) : "operand must be legal";
        assert operand.getKind().getStackKind() == x.kind() || x.kind() == Kind.Illegal : operand.getKind().getStackKind() + " must match " + x.kind();
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
        Block result = lir.cfg.blockFor(b);
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
        if (PrintIRWithLIR.getValue() && !TTY.isSuppressed()) {
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

    public void doBlock(Block block) {
        if (PrintIRWithLIR.getValue()) {
            TTY.print(block.toString());
        }

        currentBlock = block;
        // set up the list of LIR instructions
        assert lir.lir(block) == null : "LIR list already computed for this block";
        lir.setLir(block, new ArrayList<LIRInstruction>());

        append(new LabelOp(new Label(block.getId()), block.isAligned()));

        if (TraceLIRGeneratorLevel.getValue() >= 1) {
            TTY.println("BEGIN Generating LIR for block B" + block.getId());
        }

        if (block == lir.cfg.getStartBlock()) {
            assert block.getPredecessorCount() == 0;
            emitPrologue();
        } else {
            assert block.getPredecessorCount() > 0;
        }

        List<ScheduledNode> nodes = lir.nodesFor(block);
        for (int i = 0; i < nodes.size(); i++) {
            Node instr = nodes.get(i);
            if (TraceLIRGeneratorLevel.getValue() >= 3) {
                TTY.println("LIRGen for " + instr);
            }
            if (instr instanceof ValueNode) {
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
        if (block.getSuccessorCount() >= 1 && !endsWithJump(block)) {
            NodeClassIterable successors = block.getEndNode().successors();
            assert successors.isNotEmpty() : "should have at least one successor : " + block.getEndNode();

            emitJump(getLIRBlock((FixedNode) successors.first()));
        }

        if (TraceLIRGeneratorLevel.getValue() >= 1) {
            TTY.println("END Generating LIR for block B" + block.getId());
        }

        currentBlock = null;

        if (PrintIRWithLIR.getValue()) {
            TTY.println();
        }
    }

    protected abstract boolean peephole(ValueNode valueNode);

    private boolean endsWithJump(Block block) {
        List<LIRInstruction> instructions = lir.lir(block);
        if (instructions.size() == 0) {
            return false;
        }
        LIRInstruction lirInstruction = instructions.get(instructions.size() - 1);
        return lirInstruction instanceof StandardOp.JumpOp;
    }

    private void doRoot(ValueNode instr) {
        if (TraceLIRGeneratorLevel.getValue() >= 2) {
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

    protected void emitPrologue() {
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

        for (LocalNode local : graph.getNodes(LocalNode.class)) {
            Value param = params[local.index()];
            assert param.getKind() == local.kind().getStackKind();
            setResult(local, emitMove(param));
        }
    }

    public void emitIncomingValues(Value[] params) {
        ((LabelOp) lir.lir(currentBlock).get(0)).setIncomingValues(params);
    }

    @Override
    public void visitReturn(ReturnNode x) {
        AllocatableValue operand = ILLEGAL;
        if (x.result() != null) {
            operand = resultOperandFor(x.result().kind());
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
        if (TraceLIRGeneratorLevel.getValue() >= 1) {
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
        return phi.kind();
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
        emitBranch(x.condition(), getLIRBlock(x.trueSuccessor()), getLIRBlock(x.falseSuccessor()));
    }

    public void emitBranch(LogicNode node, LabelRef trueSuccessor, LabelRef falseSuccessor) {
        if (node instanceof IsNullNode) {
            emitNullCheckBranch((IsNullNode) node, trueSuccessor, falseSuccessor);
        } else if (node instanceof CompareNode) {
            emitCompareBranch((CompareNode) node, trueSuccessor, falseSuccessor);
        } else if (node instanceof LogicConstantNode) {
            emitConstantBranch(((LogicConstantNode) node).getValue(), trueSuccessor, falseSuccessor);
        } else if (node instanceof IntegerTestNode) {
            emitIntegerTestBranch((IntegerTestNode) node, trueSuccessor, falseSuccessor);
        } else {
            throw GraalInternalError.unimplemented(node.toString());
        }
    }

    private void emitNullCheckBranch(IsNullNode node, LabelRef trueSuccessor, LabelRef falseSuccessor) {
        emitCompareBranch(operand(node.object()), Constant.NULL_OBJECT, Condition.NE, false, falseSuccessor);
        emitJump(trueSuccessor);
    }

    public void emitCompareBranch(CompareNode compare, LabelRef trueSuccessorBlock, LabelRef falseSuccessorBlock) {
        emitCompareBranch(operand(compare.x()), operand(compare.y()), compare.condition().negate(), !compare.unorderedIsTrue(), falseSuccessorBlock);
        emitJump(trueSuccessorBlock);
    }

    public void emitOverflowCheckBranch(LabelRef noOverflowBlock, LabelRef overflowBlock) {
        emitOverflowCheckBranch(overflowBlock, false);
        emitJump(noOverflowBlock);
    }

    public void emitIntegerTestBranch(IntegerTestNode test, LabelRef trueSuccessorBlock, LabelRef falseSuccessorBlock) {
        emitIntegerTestBranch(operand(test.x()), operand(test.y()), true, falseSuccessorBlock);
        emitJump(trueSuccessorBlock);
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

    public abstract void emitCompareBranch(Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef label);

    public abstract void emitOverflowCheckBranch(LabelRef label, boolean negated);

    public abstract void emitIntegerTestBranch(Value left, Value right, boolean negated, LabelRef label);

    public abstract Variable emitConditionalMove(Value leftVal, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue);

    public abstract Variable emitIntegerTestMove(Value leftVal, Value right, Value trueValue, Value falseValue);

    @Override
    public void emitInvoke(Invoke x) {
        LoweredCallTargetNode callTarget = (LoweredCallTargetNode) x.callTarget();
        CallingConvention invokeCc = frameMap.registerConfig.getCallingConvention(callTarget.callType(), x.asNode().stamp().javaType(metaAccess), callTarget.signature(), target(), false);
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
        int keyCount = x.keyCount();
        if (keyCount == 0) {
            emitJump(getLIRBlock(x.defaultSuccessor()));
        } else {
            Variable value = load(operand(x.value()));
            LabelRef defaultTarget = x.defaultSuccessor() == null ? null : getLIRBlock(x.defaultSuccessor());
            if (value.getKind() != Kind.Int) {
                // hopefully only a few entries
                emitSequentialSwitch(x, value, defaultTarget);
            } else {
                assert value.getKind() == Kind.Int;
                long valueRange = x.keyAt(keyCount - 1).asLong() - x.keyAt(0).asLong() + 1;
                int switchRangeCount = switchRangeCount(x);
                if (switchRangeCount == 0) {
                    emitJump(getLIRBlock(x.defaultSuccessor()));
                } else if (switchRangeCount >= MinimumJumpTableSize.getValue() && keyCount / (double) valueRange >= MinTableSwitchDensity.getValue()) {
                    int minValue = x.keyAt(0).asInt();
                    assert valueRange < Integer.MAX_VALUE;
                    LabelRef[] targets = new LabelRef[(int) valueRange];
                    for (int i = 0; i < valueRange; i++) {
                        targets[i] = defaultTarget;
                    }
                    for (int i = 0; i < keyCount; i++) {
                        targets[x.keyAt(i).asInt() - minValue] = getLIRBlock(x.keySuccessor(i));
                    }
                    emitTableSwitch(minValue, defaultTarget, targets, value);
                } else if (keyCount / switchRangeCount >= RangeTestsSwitchDensity.getValue()) {
                    emitSwitchRanges(x, switchRangeCount, value, defaultTarget);
                } else {
                    emitSequentialSwitch(x, value, defaultTarget);
                }
            }
        }
    }

    private void emitSequentialSwitch(final SwitchNode x, Variable key, LabelRef defaultTarget) {
        int keyCount = x.keyCount();
        Integer[] indexes = Util.createSortedPermutation(keyCount, new Comparator<Integer>() {

            @Override
            public int compare(Integer o1, Integer o2) {
                return x.keyProbability(o1) < x.keyProbability(o2) ? 1 : x.keyProbability(o1) > x.keyProbability(o2) ? -1 : 0;
            }
        });
        LabelRef[] keyTargets = new LabelRef[keyCount];
        Constant[] keyConstants = new Constant[keyCount];
        for (int i = 0; i < keyCount; i++) {
            keyTargets[i] = getLIRBlock(x.keySuccessor(indexes[i]));
            keyConstants[i] = x.keyAt(indexes[i]);
        }
        emitSequentialSwitch(keyConstants, keyTargets, defaultTarget, key);
    }

    protected abstract void emitSequentialSwitch(Constant[] keyConstants, LabelRef[] keyTargets, LabelRef defaultTarget, Value key);

    protected abstract void emitSwitchRanges(int[] lowKeys, int[] highKeys, LabelRef[] targets, LabelRef defaultTarget, Value key);

    protected abstract void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, Value key);

    private static int switchRangeCount(SwitchNode x) {
        int keyCount = x.keyCount();
        int switchRangeCount = 0;
        int defaultSux = x.defaultSuccessorIndex();

        int key = x.keyAt(0).asInt();
        int sux = x.keySuccessorIndex(0);
        for (int i = 0; i < keyCount; i++) {
            int newKey = x.keyAt(i).asInt();
            int newSux = x.keySuccessorIndex(i);
            if (newSux != defaultSux && (newKey != key + 1 || sux != newSux)) {
                switchRangeCount++;
            }
            key = newKey;
            sux = newSux;
        }
        return switchRangeCount;
    }

    private void emitSwitchRanges(SwitchNode x, int switchRangeCount, Variable keyValue, LabelRef defaultTarget) {
        assert switchRangeCount >= 1 : "switch ranges should not be used for emitting only the default case";

        int[] lowKeys = new int[switchRangeCount];
        int[] highKeys = new int[switchRangeCount];
        LabelRef[] targets = new LabelRef[switchRangeCount];

        int keyCount = x.keyCount();
        int defaultSuccessor = x.defaultSuccessorIndex();

        int current = -1;
        int key = -1;
        int successor = -1;
        for (int i = 0; i < keyCount; i++) {
            int newSuccessor = x.keySuccessorIndex(i);
            int newKey = x.keyAt(i).asInt();
            if (newSuccessor != defaultSuccessor) {
                if (key + 1 == newKey && successor == newSuccessor) {
                    // still in same range
                    highKeys[current] = newKey;
                } else {
                    current++;
                    lowKeys[current] = newKey;
                    highKeys[current] = newKey;
                    targets[current] = getLIRBlock(x.blockSuccessor(newSuccessor));
                }
            }
            key = newKey;
            successor = newSuccessor;
        }
        assert current == switchRangeCount - 1;
        emitSwitchRanges(lowKeys, highKeys, targets, defaultTarget, keyValue);
    }

    public FrameMap frameMap() {
        return frameMap;
    }

    @Override
    public void beforeRegisterAllocation() {
    }

    /**
     * Gets an garbage vale for a given kind.
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

    public abstract void emitBitCount(Variable result, Value operand);

    public abstract void emitBitScanForward(Variable result, Value operand);

    public abstract void emitBitScanReverse(Variable result, Value operand);

    public abstract void emitByteSwap(Variable result, Value operand);
}
