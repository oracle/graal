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

import java.util.*;
import java.util.Map.Entry;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CompilationResult.Mark;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.JavaType.Representation;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.util.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.StandardOp.ParametersOp;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.asm.TargetMethodAssembler.CallPositionListener;
import com.oracle.graal.lir.cfg.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.FrameState.InliningIdentifier;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.max.asm.*;
import com.oracle.max.cri.xir.*;
import com.oracle.max.cri.xir.XirAssembler.XirConstant;
import com.oracle.max.cri.xir.XirAssembler.XirInstruction;
import com.oracle.max.cri.xir.XirAssembler.XirMark;
import com.oracle.max.cri.xir.XirAssembler.XirOperand;
import com.oracle.max.cri.xir.XirAssembler.XirParameter;
import com.oracle.max.cri.xir.XirAssembler.XirRegister;
import com.oracle.max.cri.xir.XirAssembler.XirTemp;
import com.oracle.max.criutils.*;

/**
 * This class traverses the HIR instructions and generates LIR instructions from them.
 */
public abstract class LIRGenerator extends LIRGeneratorTool {
    protected final Graph graph;
    protected final CodeCacheProvider runtime;
    protected final TargetDescription target;
    protected final ResolvedJavaMethod method;
    protected final FrameMap frameMap;
    public final NodeMap<Value> nodeOperands;

    protected final LIR lir;
    protected final XirSupport xirSupport;
    protected final XirGenerator xir;
    private final DebugInfoBuilder debugInfoBuilder;

    private Block currentBlock;
    private ValueNode currentInstruction;
    private ValueNode lastInstructionPrinted; // Debugging only
    private FrameState lastState;

    /**
     * Class used to reconstruct the nesting of locks that is required for debug information.
     */
    public static class LockScope {
        /**
         * Linked list of locks. {@link LIRGenerator#curLocks} is the head of the list.
         */
        public final LockScope outer;

        /**
         * The identifier of the actual inlined method instance performing the lock, or null if the outermost method
         * performs the lock. This information is used to compute the {@link BytecodeFrame} that this lock belongs to.
         */
        public final InliningIdentifier inliningIdentifier;

        /**
         * The number of locks already found for this frame state.
         */
        public final int stateDepth;

        /**
         * The object that is locked.
         */
        public final ValueNode object;

        /**
         * Whether or not the lock is eliminated.
         */
        public final boolean eliminated;

        /**
         * Space in the stack frame needed by the VM to perform the locking.
         */
        public final StackSlot lockData;

        public LockScope(LockScope outer, InliningIdentifier inliningIdentifier, ValueNode object, boolean eliminated, StackSlot lockData) {
            this.outer = outer;
            this.inliningIdentifier = inliningIdentifier;
            this.object = object;
            this.eliminated = eliminated;
            this.lockData = lockData;
            if (outer != null && outer.inliningIdentifier == inliningIdentifier) {
                this.stateDepth = outer.stateDepth + 1;
            } else {
                this.stateDepth = 0;
            }
        }

        @Override
        public String toString() {
            InliningIdentifier identifier = inliningIdentifier;
            StringBuilder sb = new StringBuilder().append(identifier).append(": ");
            for (LockScope scope = this; scope != null; scope = scope.outer) {
                if (scope.inliningIdentifier != identifier) {
                    identifier = scope.inliningIdentifier;
                    sb.append('\n').append(identifier).append(": ");
                }
                if (scope.eliminated) {
                    sb.append('!');
                }
                sb.append(scope.object).append(' ');
            }
            return sb.toString();
        }
    }

    /**
     * Mapping from blocks to the lock state at the end of the block, indexed by the id number of the block.
     */
    private BlockMap<LockScope> blockLocks;

    private BlockMap<FrameState> blockLastState;

    /**
     * The list of currently locked monitors.
     */
    private LockScope curLocks;


    public LIRGenerator(Graph graph, CodeCacheProvider runtime, TargetDescription target, FrameMap frameMap, ResolvedJavaMethod method, LIR lir, XirGenerator xir, Assumptions assumptions) {
        this.graph = graph;
        this.runtime = runtime;
        this.target = target;
        this.frameMap = frameMap;
        this.method = method;
        this.nodeOperands = graph.createNodeMap();
        this.lir = lir;
        this.xir = xir;
        this.xirSupport = new XirSupport(assumptions);
        this.debugInfoBuilder = new DebugInfoBuilder(nodeOperands);
        this.blockLocks = new BlockMap<>(lir.cfg);
        this.blockLastState = new BlockMap<>(lir.cfg);
    }

    @Override
    public TargetDescription target() {
        return target;
    }

    /**
     * Returns the operand that has been previously initialized by {@link #setResult(ValueNode, Value)}
     * with the result of an instruction.
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
            if (entry.getValue() == value) {
                return (ValueNode) entry.getKey();
            }
        }
        return null;
    }

    /**
     * Creates a new {@linkplain Variable variable}.
     * @param kind The kind of the new variable.
     * @return a new variable
     */
    @Override
    public Variable newVariable(Kind kind) {
        Kind stackKind = kind.stackKind();
        switch (stackKind) {
            case Jsr:
            case Int:
            case Long:
            case Object:
                return new Variable(stackKind, lir.nextVariable(), Register.RegisterFlag.CPU);
            case Float:
            case Double:
                return new Variable(stackKind, lir.nextVariable(), Register.RegisterFlag.FPU);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public RegisterAttributes attributes(Register register) {
        return frameMap.registerConfig.getAttributesMap()[register.number];
    }

    @Override
    public Value setResult(ValueNode x, Value operand) {
        assert (isVariable(operand) && x.kind() == operand.getKind()) ||
               (isRegister(operand) && !attributes(asRegister(operand)).isAllocatable()) ||
               (isConstant(operand) && x.kind() == operand.getKind().stackKind()) : operand.getKind() + " for node " + x;
        assert operand(x) == null : "operand cannot be set twice";
        assert operand != null && isLegal(operand) : "operand must be legal";
        assert operand.getKind().stackKind() == x.kind();
        assert !(x instanceof VirtualObjectNode);
        nodeOperands.set(x, operand);
        return operand;
    }

    @Override
    public abstract Variable emitMove(Value input);

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

    public Value loadForStore(Value value, Kind storeKind) {
        if (isConstant(value) && canStoreConstant((Constant) value)) {
            return value;
        }
        if (storeKind == Kind.Byte || storeKind == Kind.Boolean) {
            Variable tempVar = new Variable(value.getKind(), lir.nextVariable(), Register.RegisterFlag.Byte);
            emitMove(value, tempVar);
            return tempVar;
        }
        return load(value);
    }

    protected LabelRef getLIRBlock(FixedNode b) {
        Block result = lir.cfg.blockFor(b);
        int suxIndex = currentBlock.getSuccessors().indexOf(result);
        assert suxIndex != -1 : "Block not in successor list of current block";

        return LabelRef.forSuccessor(currentBlock, suxIndex);
    }

    public LIRFrameState state() {
        assert lastState != null : "must have state before instruction";
        return stateFor(lastState, -1);
    }

    public LIRFrameState state(long leafGraphId) {
        assert lastState != null : "must have state before instruction";
        return stateFor(lastState, leafGraphId);
    }

    public LIRFrameState stateFor(FrameState state, long leafGraphId) {
        return stateFor(state, null, null, leafGraphId);
    }

    public LIRFrameState stateFor(FrameState state, List<StackSlot> pointerSlots, LabelRef exceptionEdge, long leafGraphId) {
        return debugInfoBuilder.build(state, curLocks, pointerSlots, exceptionEdge, leafGraphId);
    }

    /**
     * Gets the ABI specific operand used to return a value of a given kind from a method.
     *
     * @param kind the kind of value being returned
     * @return the operand representing the ABI defined location used return a value of kind {@code kind}
     */
    public Value resultOperandFor(Kind kind) {
        if (kind == Kind.Void) {
            return IllegalValue;
        }
        return frameMap.registerConfig.getReturnRegister(kind).asValue(kind);
    }


    public void append(LIRInstruction op) {
        assert LIRVerifier.verify(op);
        if (GraalOptions.PrintIRWithLIR && !TTY.isSuppressed()) {
            if (currentInstruction != null && lastInstructionPrinted != currentInstruction) {
                lastInstructionPrinted = currentInstruction;
                InstructionPrinter ip = new InstructionPrinter(TTY.out());
                ip.printInstructionListing(currentInstruction);
            }
            TTY.println(op.toStringWithIdPrefix());
            TTY.println();
        }
        currentBlock.lir.add(op);
    }

    public void doBlock(Block block) {
        if (GraalOptions.PrintIRWithLIR) {
            TTY.print(block.toString());
        }

        currentBlock = block;
        // set up the list of LIR instructions
        assert block.lir == null : "LIR list already computed for this block";
        block.lir = new ArrayList<>();

        append(new LabelOp(new Label(), block.align));

        if (GraalOptions.TraceLIRGeneratorLevel >= 1) {
            TTY.println("BEGIN Generating LIR for block B" + block.getId());
        }

        curLocks = null;
        for (Block pred : block.getPredecessors()) {
            LockScope predLocks = blockLocks.get(pred);
            if (curLocks == null) {
                curLocks = predLocks;
            } else if (curLocks != predLocks && (!pred.isLoopEnd() || predLocks != null)) {
//                throw new GraalInternalError("cause: %s", predLocks);
                throw new BailoutException("unbalanced monitors: predecessor blocks have different monitor states");
            }
        }

        if (block == lir.cfg.getStartBlock()) {
            assert block.getPredecessors().size() == 0;
            emitPrologue();

        } else {
            assert block.getPredecessors().size() > 0;
            FrameState fs = null;

            for (Block pred : block.getPredecessors()) {
                if (fs == null) {
                    fs = blockLastState.get(pred);
                } else {
                    if (blockLastState.get(pred) == null) {
                        // Only a back edge can have a null state for its enclosing block.
                        assert pred.getEndNode() instanceof LoopEndNode;

                        if (block.getBeginNode().stateAfter() == null) {
                            // We'll assert later that the begin and end of a framestate-less loop
                            // share the frame state that flowed into the loop
                            blockLastState.put(pred, fs);
                        }
                    } else if (fs != blockLastState.get(pred)) {
                        fs = null;
                        break;
                    }
                }
            }
            if (GraalOptions.TraceLIRGeneratorLevel >= 2) {
                if (fs == null) {
                    TTY.println("STATE RESET");
                } else {
                    TTY.println("STATE CHANGE (singlePred)");
                    if (GraalOptions.TraceLIRGeneratorLevel >= 3) {
                        TTY.println(fs.toString(Node.Verbosity.Debugger));
                    }
                }
            }
            lastState = fs;
        }

        List<ScheduledNode> nodes = lir.nodesFor(block);
        for (int i = 0; i < nodes.size(); i++) {
            Node instr = nodes.get(i);

            if (GraalOptions.OptImplicitNullChecks) {
                Node nextInstr = null;
                if (i < nodes.size() - 1) {
                    nextInstr = nodes.get(i + 1);
                }

                if (instr instanceof GuardNode) {
                    GuardNode guardNode = (GuardNode) instr;
                    if (guardNode.condition() instanceof IsNullNode && guardNode.negated()) {
                        IsNullNode isNullNode = (IsNullNode) guardNode.condition();
                        if (nextInstr instanceof Access) {
                            Access access = (Access) nextInstr;
                            if (isNullNode.object() == access.object() && canBeNullCheck(access.location())) {
                                //TTY.println("implicit null check");
                                access.setNullCheck(true);
                                continue;
                            }
                        }
                    }
                }
            }
            if (GraalOptions.TraceLIRGeneratorLevel >= 3) {
                TTY.println("LIRGen for " + instr);
            }
            FrameState stateAfter = null;
            if (instr instanceof StateSplit) {
                stateAfter = ((StateSplit) instr).stateAfter();
            }
            if (instr instanceof ValueNode) {
                try {
                    doRoot((ValueNode) instr);
                } catch (GraalInternalError e) {
                    throw e.addContext(instr);
                } catch (Throwable e) {
                    throw new GraalInternalError(e).addContext(instr);
                }
            }
            if (stateAfter != null) {
                lastState = stateAfter;
                assert checkStateReady(lastState);
                if (GraalOptions.TraceLIRGeneratorLevel >= 2) {
                    TTY.println("STATE CHANGE");
                    if (GraalOptions.TraceLIRGeneratorLevel >= 3) {
                        TTY.println(stateAfter.toString(Node.Verbosity.Debugger));
                    }
                }
            }
        }
        if (block.numberOfSux() >= 1 && !endsWithJump(block)) {
            NodeSuccessorsIterable successors = block.getEndNode().successors();
            assert successors.isNotEmpty() : "should have at least one successor : " + block.getEndNode();

            emitJump(getLIRBlock((FixedNode) successors.first()), null);
        }

        if (GraalOptions.TraceLIRGeneratorLevel >= 1) {
            TTY.println("END Generating LIR for block B" + block.getId());
        }

        // Check that the begin and end of a framestate-less loop
        // share the frame state that flowed into the loop
        assert blockLastState.get(block) == null || blockLastState.get(block) == lastState;

        blockLocks.put(currentBlock, curLocks);
        blockLastState.put(block, lastState);
        currentBlock = null;

        if (GraalOptions.PrintIRWithLIR) {
            TTY.println();
        }
    }

    private boolean checkStateReady(FrameState state) {
        FrameState fs = state;
        while (fs != null) {
            for (ValueNode v : fs.values()) {
                if (v != null && !(v instanceof VirtualObjectNode)) {
                    assert operand(v) != null : "Value " + v + " in " + fs + " is not ready!";
                }
            }
            fs =  fs.outerFrameState();
        }
        return true;
    }

    private static boolean endsWithJump(Block block) {
        if (block.lir.size() == 0) {
            return false;
        }
        LIRInstruction lirInstruction = block.lir.get(block.lir.size() - 1);
        if (lirInstruction instanceof LIRXirInstruction) {
            LIRXirInstruction lirXirInstruction = (LIRXirInstruction) lirInstruction;
            return (lirXirInstruction.falseSuccessor != null) && (lirXirInstruction.trueSuccessor != null);
        }
        return lirInstruction instanceof StandardOp.JumpOp;
    }

    private void doRoot(ValueNode instr) {
        if (GraalOptions.TraceLIRGeneratorLevel >= 2) {
            TTY.println("Emitting LIR for instruction " + instr);
        }
        currentInstruction = instr;

        Debug.log("Visiting %s", instr);
        emitNode(instr);
        Debug.log("Operand for %s = %s", instr, operand(instr));
    }

    protected void emitNode(ValueNode node) {
        ((LIRLowerable) node).generate(this);
    }

    private static boolean canBeNullCheck(LocationNode location) {
        // TODO: Make this part of TargetDescription
        return !(location instanceof IndexedLocationNode) && location.displacement() < 4096;
    }

    protected void emitPrologue() {
        CallingConvention incomingArguments = frameMap.registerConfig.getCallingConvention(JavaCallee, MetaUtil.signatureToKinds(method), target, false);

        Value[] params = new Value[incomingArguments.locations.length];
        for (int i = 0; i < params.length; i++) {
            params[i] = toStackKind(incomingArguments.locations[i]);
            if (ValueUtil.isStackSlot(params[i])) {
                StackSlot slot = ValueUtil.asStackSlot(params[i]);
                if (slot.inCallerFrame() && !lir.hasArgInCallerFrame()) {
                    lir.setHasArgInCallerFrame();
                }
            }
        }

        append(new ParametersOp(params));

        for (LocalNode local : graph.getNodes(LocalNode.class)) {
            Value param = params[local.index()];
            assert param.getKind() == local.kind().stackKind();
            setResult(local, emitMove(param));
        }
    }

    @Override
    public void visitCheckCast(CheckCastNode x) {
        XirSnippet snippet = xir.genCheckCast(site(x, x.object()), toXirArgument(x.object()), toXirArgument(x.targetClassInstruction()), x.targetClass(), x.profile());
        emitXir(snippet, x, state(), true);
        // The result of a checkcast is the unmodified object, so no need to allocate a new variable for it.
        setResult(x, operand(x.object()));
    }

    public void lock(ValueNode object, boolean eliminated, StackSlot lock, InliningIdentifier inliningIdentifier) {
        assert lastState != null : "must have state before instruction";
        curLocks = new LockScope(curLocks, inliningIdentifier, object, eliminated, lock);
    }

    public StackSlot peekLock() {
        assert curLocks.lockData != null;
        return curLocks.lockData;
    }

    public void unlock(ValueNode object, boolean eliminated) {
        if (curLocks == null || curLocks.object != object || curLocks.eliminated != eliminated) {
            throw new BailoutException("unbalanced monitors: attempting to unlock an object that is not on top of the locking stack");
        }
        curLocks = curLocks.outer;
    }

    @Override
    public void visitMonitorEnter(MonitorEnterNode x) {
        StackSlot lockData = frameMap.allocateStackBlock(runtime.sizeOfLockData(), false);
        if (x.eliminated()) {
            // No code is emitted for eliminated locks, but for proper debug information generation we need to
            // register the monitor and its lock data.
            lock(x.object(), true, lockData, x.stateAfter().inliningIdentifier());
            return;
        }

        XirArgument obj = toXirArgument(x.object());
        XirArgument lockAddress = lockData == null ? null : toXirArgument(emitLea(lockData));

        LIRFrameState stateBefore = state();
        // The state before the monitor enter is used for null checks, so it must not contain the newly locked object.
        lock(x.object(), false, lockData, x.stateAfter().inliningIdentifier());
        // The state after the monitor enter is used for deoptimization, after the monitor has blocked, so it must contain the newly locked object.
        LIRFrameState stateAfter = stateFor(x.stateAfter(), -1);

        XirSnippet snippet = xir.genMonitorEnter(site(x, x.object()), obj, lockAddress);
        emitXir(snippet, x, stateBefore, stateAfter, true, null, null);
    }

    @Override
    public void visitMonitorExit(MonitorExitNode x) {
        if (x.eliminated()) {
            unlock(x.object(), true);
            return;
        }

        Value lockData = peekLock();
        XirArgument obj = toXirArgument(x.object());
        XirArgument lockAddress = lockData == null ? null : toXirArgument(emitLea(lockData));

        LIRFrameState stateBefore = state();
        unlock(x.object(), false);

        XirSnippet snippet = xir.genMonitorExit(site(x, x.object()), obj, lockAddress);
        emitXir(snippet, x, stateBefore, true);
    }

    @Override
    public void visitNewInstance(NewInstanceNode x) {
        XirSnippet snippet = xir.genNewInstance(site(x), x.instanceClass());
        emitXir(snippet, x, state(), true);
    }

    @Override
    public void visitNewPrimitiveArray(NewPrimitiveArrayNode x) {
        XirArgument length = toXirArgument(x.length());
        XirSnippet snippet = xir.genNewArray(site(x), length, x.elementType().kind(), null, null);
        emitXir(snippet, x, state(), true);
    }

    @Override
    public void visitNewObjectArray(NewObjectArrayNode x) {
        XirArgument length = toXirArgument(x.length());
        XirSnippet snippet = xir.genNewArray(site(x), length, Kind.Object, x.elementType(), x.elementType().arrayOf());
        emitXir(snippet, x, state(), true);
    }

    @Override
    public void visitNewMultiArray(NewMultiArrayNode x) {
        XirArgument[] dims = new XirArgument[x.dimensionCount()];
        for (int i = 0; i < dims.length; i++) {
            dims[i] = toXirArgument(x.dimension(i));
        }
        XirSnippet snippet = xir.genNewMultiArray(site(x), dims, x.type());
        emitXir(snippet, x, state(), true);
    }

    @Override
    public void visitReturn(ReturnNode x) {
        Value operand = Value.IllegalValue;
        if (!x.kind().isVoid()) {
            operand = resultOperandFor(x.kind());
            emitMove(operand(x.result()), operand);
        }
        emitReturn(operand);
    }

    protected abstract void emitReturn(Value input);

    @Override
    public void visitMerge(MergeNode x) {
    }

    @Override
    public void visitEndNode(EndNode end) {
        moveToPhi(end.merge(), end);
    }

    /**
     * Runtime specific classes can override this to insert a safepoint at the end of a loop.
     */
    @Override
    public void visitLoopEnd(LoopEndNode x) {
    }

    private void moveToPhi(MergeNode merge, EndNode pred) {
        if (GraalOptions.TraceLIRGeneratorLevel >= 1) {
            TTY.println("MOVE TO PHI from " + pred + " to " + merge);
        }
        PhiResolver resolver = new PhiResolver(this);
        for (PhiNode phi : merge.phis()) {
            if (phi.type() == PhiType.Value) {
                ValueNode curVal = phi.valueAt(pred);
                resolver.move(operand(curVal), operandForPhi(phi));
            }
        }
        resolver.dispose();

        append(new JumpOp(getLIRBlock(merge), null));
    }

    private Value operandForPhi(PhiNode phi) {
        assert phi.type() == PhiType.Value : "wrong phi type: " + phi;
        Value result = operand(phi);
        if (result == null) {
            // allocate a variable for this phi
            Variable newOperand = newVariable(phi.kind());
            setResult(phi, newOperand);
            return newOperand;
        } else {
            return result;
        }
    }

    @Override
    public void emitIf(IfNode x) {
        emitBranch(x.compare(), getLIRBlock(x.trueSuccessor()),  getLIRBlock(x.falseSuccessor()), null);
    }

    @Override
    public void emitGuardCheck(BooleanNode comp, DeoptimizationReason deoptReason, DeoptimizationAction action, boolean negated, long leafGraphId) {
        if (comp instanceof IsNullNode && negated) {
            emitNullCheckGuard(((IsNullNode) comp).object(), leafGraphId);
        } else if (comp instanceof ConstantNode && (comp.asConstant().asBoolean() != negated)) {
            // True constant, nothing to emit.
            // False constants are handled within emitBranch.
        } else {
            // Fall back to a normal branch.
            LIRFrameState info = state(leafGraphId);
            LabelRef stubEntry = createDeoptStub(action, deoptReason, info, comp);
            if (negated) {
                emitBranch(comp, stubEntry, null, info);
            } else {
                emitBranch(comp, null, stubEntry, info);
            }
        }
    }

    protected abstract void emitNullCheckGuard(ValueNode object, long leafGraphId);

    public void emitBranch(BooleanNode node, LabelRef trueSuccessor, LabelRef falseSuccessor, LIRFrameState info) {
        if (node instanceof IsNullNode) {
            emitNullCheckBranch((IsNullNode) node, trueSuccessor, falseSuccessor, info);
        } else if (node instanceof CompareNode) {
            emitCompareBranch((CompareNode) node, trueSuccessor, falseSuccessor, info);
        } else if (node instanceof InstanceOfNode) {
            emitInstanceOfBranch((InstanceOfNode) node, trueSuccessor, falseSuccessor, info);
        } else if (node instanceof ConstantNode) {
            emitConstantBranch(((ConstantNode) node).asConstant().asBoolean(), trueSuccessor, falseSuccessor, info);
        } else if (node instanceof IsTypeNode) {
            emitTypeBranch((IsTypeNode) node, trueSuccessor, falseSuccessor, info);
        } else {
            throw GraalInternalError.unimplemented(node.toString());
        }
    }

    private void emitNullCheckBranch(IsNullNode node, LabelRef trueSuccessor, LabelRef falseSuccessor, LIRFrameState info) {
        if (falseSuccessor != null) {
            emitBranch(operand(node.object()), Constant.NULL_OBJECT, Condition.NE, false, falseSuccessor, info);
            if (trueSuccessor != null) {
                emitJump(trueSuccessor, null);
            }
        } else {
            emitBranch(operand(node.object()), Constant.NULL_OBJECT, Condition.EQ, false, trueSuccessor, info);
        }
    }

    public void emitCompareBranch(CompareNode compare, LabelRef trueSuccessorBlock, LabelRef falseSuccessorBlock, LIRFrameState info) {
        if (falseSuccessorBlock != null) {
            emitBranch(operand(compare.x()), operand(compare.y()), compare.condition().negate(), !compare.unorderedIsTrue(), falseSuccessorBlock, info);
            if (trueSuccessorBlock != null) {
                emitJump(trueSuccessorBlock, null);
            }
        } else {
            emitBranch(operand(compare.x()), operand(compare.y()), compare.condition(), compare.unorderedIsTrue(), trueSuccessorBlock, info);
        }
    }

    private void emitInstanceOfBranch(InstanceOfNode x, LabelRef trueSuccessor, LabelRef falseSuccessor, LIRFrameState info) {
        XirArgument obj = toXirArgument(x.object());
        XirSnippet snippet = xir.genInstanceOf(site(x, x.object()), obj, toXirArgument(x.targetClassInstruction()), x.targetClass(), x.profile());
        emitXir(snippet, x, info, null, false, trueSuccessor, falseSuccessor);
    }

    public void emitConstantBranch(boolean value, LabelRef trueSuccessorBlock, LabelRef falseSuccessorBlock, LIRFrameState info) {
        LabelRef block = value ? trueSuccessorBlock : falseSuccessorBlock;
        if (block != null) {
            emitJump(block, info);
        }
    }

    public void emitTypeBranch(IsTypeNode x, LabelRef trueSuccessor, LabelRef falseSuccessor, LIRFrameState info) {
        XirArgument thisClass = toXirArgument(x.objectClass());
        XirArgument otherClass = toXirArgument(x.type().getEncoding(Representation.ObjectHub));
        XirSnippet snippet = xir.genTypeBranch(site(x), thisClass, otherClass, x.type());
        emitXir(snippet, x, info, null, false, trueSuccessor, falseSuccessor);
        if (trueSuccessor != null) {
            emitJump(trueSuccessor, null);
        }
    }

    @Override
    public void emitConditional(ConditionalNode conditional) {
        Value tVal = operand(conditional.trueValue());
        Value fVal = operand(conditional.falseValue());
        setResult(conditional, emitConditional(conditional.condition(), tVal, fVal));
    }

    public Variable emitConditional(BooleanNode node, Value trueValue, Value falseValue) {
        if (node instanceof IsNullNode) {
            return emitNullCheckConditional((IsNullNode) node, trueValue, falseValue);
        } else if (node instanceof CompareNode) {
            return emitCompareConditional((CompareNode) node, trueValue, falseValue);
        } else if (node instanceof InstanceOfNode) {
            return emitInstanceOfConditional((InstanceOfNode) node, trueValue, falseValue);
        } else if (node instanceof ConstantNode) {
            return emitConstantConditional(((ConstantNode) node).asConstant().asBoolean(), trueValue, falseValue);
        } else {
            throw GraalInternalError.unimplemented(node.toString());
        }
    }

    private Variable emitNullCheckConditional(IsNullNode node, Value trueValue, Value falseValue) {
        return emitCMove(operand(node.object()), Constant.NULL_OBJECT, Condition.EQ, false, trueValue, falseValue);
    }

    private Variable emitInstanceOfConditional(InstanceOfNode x, Value trueValue, Value falseValue) {
        XirArgument obj = toXirArgument(x.object());
        XirArgument trueArg = toXirArgument(trueValue);
        XirArgument falseArg = toXirArgument(falseValue);
        XirSnippet snippet = xir.genMaterializeInstanceOf(site(x, x.object()), obj, toXirArgument(x.targetClassInstruction()), trueArg, falseArg, x.targetClass(), x.profile());
        return (Variable) emitXir(snippet, null, null, false);
    }

    private Variable emitConstantConditional(boolean value, Value trueValue, Value falseValue) {
        return emitMove(value ? trueValue : falseValue);
    }

    private Variable emitCompareConditional(CompareNode compare, Value trueValue, Value falseValue) {
        return emitCMove(operand(compare.x()), operand(compare.y()), compare.condition(), compare.unorderedIsTrue(), trueValue, falseValue);
    }


    public abstract void emitLabel(Label label, boolean align);
    public abstract void emitJump(LabelRef label, LIRFrameState info);
    public abstract void emitBranch(Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef label, LIRFrameState info);
    public abstract Variable emitCMove(Value leftVal, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue);

    protected FrameState stateBeforeCallWithArguments(FrameState stateAfter, MethodCallTargetNode call, int bci) {
        return stateAfter.duplicateModified(bci, stateAfter.rethrowException(), call.returnStamp().kind(), toJVMArgumentStack(call.targetMethod().signature(), call.isStatic(), call.arguments()));
    }

    private static ValueNode[] toJVMArgumentStack(Signature signature, boolean isStatic, NodeInputList<ValueNode> arguments) {
        int slotCount = signature.argumentSlots(!isStatic);
        ValueNode[] stack = new ValueNode[slotCount];
        int stackIndex = 0;
        int argumentIndex = 0;
        for (ValueNode arg : arguments) {
            stack[stackIndex] = arg;

            if (stackIndex == 0 && !isStatic) {
                // Current argument is receiver.
                stackIndex += stackSlots(Kind.Object);
            } else {
                stackIndex += stackSlots(signature.argumentKindAt(argumentIndex));
                argumentIndex++;
            }
        }
        return stack;
    }


    public static int stackSlots(Kind kind) {
        return isTwoSlot(kind) ? 2 : 1;
    }

    public static boolean isTwoSlot(Kind kind) {
        assert kind != Kind.Void && kind != Kind.Illegal;
        return kind == Kind.Long || kind == Kind.Double;
    }

    @Override
    public void emitInvoke(Invoke x) {
        if (GraalOptions.XIRLowerInvokes) {
            emitInvokeXIR(x);
            return;
        }

        AbstractCallTargetNode callTarget = (AbstractCallTargetNode) x.callTarget();
        Kind[] signature = callTarget.signature();
        CallingConvention cc = frameMap.registerConfig.getCallingConvention(callTarget.callType(), signature, target(), false);
        frameMap.callsMethod(cc, callTarget.callType());

        List<Value> argList = visitInvokeArguments(cc, callTarget.arguments());
        Value[] parameters = argList.toArray(new Value[argList.size()]);

        LIRFrameState callState = stateFor(x.stateDuring(), null, x instanceof InvokeWithExceptionNode ? getLIRBlock(((InvokeWithExceptionNode) x).exceptionEdge()) : null, x.leafGraphId());
        Value result = resultOperandFor(x.node().kind());

        if (callTarget instanceof DirectCallTargetNode) {
            emitDirectCall((DirectCallTargetNode) callTarget, result, parameters, callState);
        } else if (callTarget instanceof IndirectCallTargetNode) {
            emitIndirectCall((IndirectCallTargetNode) callTarget, result, parameters, callState);
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }

        if (isLegal(result)) {
            setResult(x.node(), emitMove(result));
        }
    }

    protected abstract void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, LIRFrameState callState);

    protected abstract void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, LIRFrameState callState);

    public void emitInvokeXIR(Invoke x) {
        MethodCallTargetNode callTarget = x.methodCallTarget();
        JavaMethod targetMethod = callTarget.targetMethod();

        XirSnippet snippet = null;
        XirArgument receiver;
        switch (callTarget.invokeKind()) {
            case Static:
                snippet = xir.genInvokeStatic(site(x.node()), targetMethod);
                break;
            case Special:
                receiver = toXirArgument(callTarget.receiver());
                snippet = xir.genInvokeSpecial(site(x.node(), callTarget.receiver()), receiver, targetMethod);
                break;
            case Virtual:
                assert callTarget.receiver().kind() == Kind.Object : callTarget + ": " + callTarget.targetMethod().toString();
                receiver = toXirArgument(callTarget.receiver());
                snippet = xir.genInvokeVirtual(site(x.node(), callTarget.receiver()), receiver, targetMethod, x.isMegamorphic());
                break;
            case Interface:
                assert callTarget.receiver().kind() == Kind.Object : callTarget;
                receiver = toXirArgument(callTarget.receiver());
                snippet = xir.genInvokeInterface(site(x.node(), callTarget.receiver()), receiver, targetMethod);
                break;
        }

        Value destinationAddress = null;
        if (!target().invokeSnippetAfterArguments) {
            // This is the version currently necessary for Maxine: since the invokeinterface-snippet uses a division, it
            // destroys rdx, which is also used to pass a parameter.  Therefore, the snippet must be before the parameters are assigned to their locations.
            LIRFrameState addrInfo = stateFor(stateBeforeCallWithArguments(x.stateAfter(), callTarget, x.bci()), x.leafGraphId());
            destinationAddress = emitXir(snippet, x.node(), addrInfo, false);
        }

        Value resultOperand = resultOperandFor(x.node().kind());

        Kind[] signature = MetaUtil.signatureToKinds(callTarget.targetMethod().signature(), callTarget.isStatic() ? null : callTarget.targetMethod().holder().kind());
        CallingConvention cc = frameMap.registerConfig.getCallingConvention(JavaCall, signature, target(), false);
        frameMap.callsMethod(cc, JavaCall);
        List<Value> argList = visitInvokeArguments(cc, callTarget.arguments());

        if (target().invokeSnippetAfterArguments) {
            // This is the version currently active for HotSpot.
            LIRFrameState addrInfo = stateFor(stateBeforeCallWithArguments(x.stateAfter(), callTarget, x.bci()), null, null, x.leafGraphId());
            destinationAddress = emitXir(snippet, x.node(), addrInfo, false);
        }

        final Map<XirMark, Mark> marks = snippet.marks;

        CallPositionListener callPositionListener = new CallPositionListener() {
            public void beforeCall(TargetMethodAssembler tasm) {
            }
            public void atCall(TargetMethodAssembler tasm) {
                if (marks != null) {
                    marks.put(XirMark.CALLSITE, tasm.recordMark(null, new Mark[0]));
                }
            }
        };

        LIRFrameState callInfo = stateFor(x.stateDuring(), null, x instanceof InvokeWithExceptionNode ? getLIRBlock(((InvokeWithExceptionNode) x).exceptionEdge()) : null, x.leafGraphId());
        emitCall(targetMethod, resultOperand, argList, destinationAddress, callInfo, callPositionListener);

        if (isLegal(resultOperand)) {
            setResult(x.node(), emitMove(resultOperand));
        }
    }

    protected abstract void emitCall(Object targetMethod, Value result, List<Value> arguments, Value targetAddress, LIRFrameState info, CallPositionListener ecl);


    private static Value toStackKind(Value value) {
        if (value.getKind().stackKind() != value.getKind()) {
            // We only have stack-kinds in the LIR, so convert the operand kind for values from the calling convention.
            if (isRegister(value)) {
                return asRegister(value).asValue(value.getKind().stackKind());
            } else if (isStackSlot(value)) {
                return StackSlot.get(value.getKind().stackKind(), asStackSlot(value).rawOffset(), asStackSlot(value).rawAddFrameSize());
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        }
        return value;
    }

    public List<Value> visitInvokeArguments(CallingConvention cc, Iterable<ValueNode> arguments) {
        // for each argument, load it into the correct location
        List<Value> argList = new ArrayList<>();
        int j = 0;
        for (ValueNode arg : arguments) {
            if (arg != null) {
                Value operand = toStackKind(cc.locations[j++]);
                emitMove(operand(arg), operand);
                argList.add(operand);

            } else {
                throw GraalInternalError.shouldNotReachHere("I thought we no longer have null entries for two-slot types...");
            }
        }
        return argList;
    }


    protected abstract LabelRef createDeoptStub(DeoptimizationAction action, DeoptimizationReason reason, LIRFrameState info, Object deoptInfo);

    @Override
    public Variable emitCall(@SuppressWarnings("hiding") Object target, Kind result, Kind[] arguments, boolean canTrap, Value... args) {
        LIRFrameState info = canTrap ? state() : null;

        Value physReg = resultOperandFor(result);

        List<Value> argumentList;
        if (arguments.length > 0) {
            // move the arguments into the correct location
            CallingConvention cc = frameMap.registerConfig.getCallingConvention(RuntimeCall, arguments, target(), false);
            frameMap.callsMethod(cc, RuntimeCall);
            assert cc.locations.length == args.length : "argument count mismatch";
            for (int i = 0; i < args.length; i++) {
                Value arg = args[i];
                Value loc = cc.locations[i];
                emitMove(arg, loc);
            }
            argumentList = Arrays.asList(cc.locations);
        } else {
            // no arguments
            assert args == null || args.length == 0;
            argumentList = Collections.emptyList();
        }

        emitCall(target, physReg, argumentList, Constant.forLong(0), info, null);

        if (isLegal(physReg)) {
            return emitMove(physReg);
        } else {
            return null;
        }
    }

    @Override
    public void emitRuntimeCall(RuntimeCallNode x) {
        Value resultOperand = resultOperandFor(x.kind());
        CallingConvention cc = frameMap.registerConfig.getCallingConvention(RuntimeCall, x.call().arguments, target(), false);
        frameMap.callsMethod(cc, RuntimeCall);
        List<Value> argList = visitInvokeArguments(cc, x.arguments());

        LIRFrameState info = null;
        FrameState stateAfter = x.stateAfter();
        if (stateAfter != null) {
            // (cwimmer) I made the code that modifies the operand stack conditional. My scenario: runtime calls to, e.g.,
            // CreateNullPointerException have no equivalent in the bytecodes, so there is no invoke bytecode.
            // Therefore, the result of the runtime call was never pushed to the stack, and we cannot pop it here.
            FrameState stateBeforeReturn = stateAfter;
            if ((stateAfter.stackSize() > 0 && stateAfter.stackAt(stateAfter.stackSize() - 1) == x) ||
                (stateAfter.stackSize() > 1 && stateAfter.stackAt(stateAfter.stackSize() - 2) == x)) {

                stateBeforeReturn = stateAfter.duplicateModified(stateAfter.bci, stateAfter.rethrowException(), x.kind());
            }

            // TODO is it correct here that the pointerSlots are not passed to the oop map generation?
            info = stateFor(stateBeforeReturn, -1);
        } else {
            // Every runtime call needs an info
            // TODO This is conservative. It's not needed for RuntimeCalls that are implemented purely in a stub
            //       that does not trash any registers and does not call into the runtime.
            info = state();
        }

        emitCall(x.call(), resultOperand, argList, Constant.forLong(0), info, null);

        if (isLegal(resultOperand)) {
            setResult(x, emitMove(resultOperand));
        }
    }

    /**
     * This method tries to create a switch implementation that is optimal for the given switch.
     * It will either generate a sequential if/then/else cascade, a set of range tests or a table switch.
     *
     * If the given switch does not contain int keys, it will always create a sequential implementation.
     */
    @Override
    public void emitSwitch(SwitchNode x) {
        int keyCount = x.keyCount();
        if (keyCount == 0) {
            emitJump(getLIRBlock(x.defaultSuccessor()), null);
        } else {
            Variable value = load(operand(x.value()));
            LabelRef defaultTarget = x.defaultSuccessor() == null ? null : getLIRBlock(x.defaultSuccessor());
            if (value.getKind() == Kind.Object || keyCount < GraalOptions.SequentialSwitchLimit) {
                // only a few entries
                emitSequentialSwitch(x, value, defaultTarget);
            } else {
                long valueRange = x.keyAt(keyCount - 1).asLong() - x.keyAt(0).asLong() + 1;
                int switchRangeCount = switchRangeCount(x);
                int rangeDensity = keyCount / switchRangeCount;
                if (rangeDensity >= GraalOptions.RangeTestsSwitchDensity) {
                    emitSwitchRanges(x, switchRangeCount, value, defaultTarget);
                } else if (keyCount / (double) valueRange >= GraalOptions.MinTableSwitchDensity) {
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
        int i = 0;
        while (i < keyCount && x.keySuccessorIndex(i) == x.defaultSuccessorIndex()) {
            i++;
        }
        if (i == keyCount) {
            return 0;
        } else {
            int switchRangeCount = 1;
            i++;
            for (; i < keyCount; i++) {
                if (x.keySuccessorIndex(i) != x.defaultSuccessorIndex()) {
                    if (x.keyAt(i).asInt() != x.keyAt(i - 1).asInt() + 1 || x.keySuccessorIndex(i) != x.keySuccessorIndex(i - 1)) {
                        switchRangeCount++;
                    }
                }
            }
            return switchRangeCount;
        }
    }

    private void emitSwitchRanges(SwitchNode x, int switchRangeCount, Variable keyValue, LabelRef defaultTarget) {
        int[] lowKeys = new int[switchRangeCount];
        int[] highKeys = new int[switchRangeCount];
        LabelRef[] targets = new LabelRef[switchRangeCount];

        int keyCount = x.keyCount();
        int defaultSuccessor = x.defaultSuccessorIndex();

        int current = 0;
        int i = 0;
        while (i < keyCount && x.keySuccessorIndex(i) == x.defaultSuccessorIndex()) {
            i++;
        }
        if (i == keyCount) {
            emitJump(defaultTarget, null);
        } else {
            int key = x.keyAt(i).asInt();
            int successor = x.keySuccessorIndex(i);
            lowKeys[current] = key;
            highKeys[current] = key;
            targets[current] = getLIRBlock(x.blockSuccessor(successor));
            i++;
            for (; i < keyCount; i++) {
                int newSuccessor = x.keySuccessorIndex(i);
                if (newSuccessor != defaultSuccessor) {
                    int newKey = x.keyAt(i).asInt();
                    if (key + 1 == newKey && successor == newSuccessor) {
                        // still in same range
                        highKeys[current] = newKey;
                    } else {
                        current++;
                        lowKeys[current] = newKey;
                        highKeys[current] = newKey;
                        targets[current] = getLIRBlock(x.blockSuccessor(newSuccessor));
                    }
                    key = newKey;
                }
                successor = newSuccessor;
            }
            assert current == switchRangeCount - 1;
            emitSwitchRanges(lowKeys, highKeys, targets, defaultTarget, keyValue);
        }
    }

    protected XirArgument toXirArgument(Value v) {
        if (v == null) {
            return null;
        }
        return XirArgument.forInternalObject(v);
    }

    protected XirArgument toXirArgument(ValueNode i) {
        if (i == null) {
            return null;
        }
        return XirArgument.forInternalObject(loadNonConst(operand(i)));
    }

    private Value allocateOperand(XirSnippet snippet, XirOperand op) {
        if (op instanceof XirParameter)  {
            XirParameter param = (XirParameter) op;
            return allocateOperand(snippet.arguments[param.parameterIndex], op, param.canBeConstant);
        } else if (op instanceof XirRegister) {
            XirRegister reg = (XirRegister) op;
            return reg.register;
        } else if (op instanceof XirTemp) {
            return newVariable(op.kind);
        } else {
            GraalInternalError.shouldNotReachHere();
            return null;
        }
    }

    private Value allocateOperand(XirArgument arg, XirOperand var, boolean canBeConstant) {
        if (arg.constant != null) {
            return arg.constant;
        }

        Value value = (Value) arg.object;
        if (canBeConstant) {
            return value;
        }
        Variable variable = load(value);
        if (var.kind == Kind.Byte || var.kind == Kind.Boolean) {
            Variable tempVar = new Variable(value.getKind(), lir.nextVariable(), Register.RegisterFlag.Byte);
            emitMove(variable, tempVar);
            variable = tempVar;
        }
        return variable;
    }

    protected Value emitXir(XirSnippet snippet, ValueNode x, LIRFrameState info, boolean setInstructionResult) {
        return emitXir(snippet, x, info, null, setInstructionResult, null, null);
    }

    protected Value emitXir(XirSnippet snippet, ValueNode instruction, LIRFrameState info, LIRFrameState infoAfter, boolean setInstructionResult, LabelRef trueSuccessor, LabelRef falseSuccessor) {
        if (GraalOptions.PrintXirTemplates) {
            TTY.println("Emit XIR template " + snippet.template.name);
        }

        final Value[] operandsArray = new Value[snippet.template.variableCount];

        frameMap.reserveOutgoing(snippet.template.outgoingStackSize);

        XirOperand resultOperand = snippet.template.resultOperand;

        if (snippet.template.allocateResultOperand) {
            Value outputOperand = IllegalValue;
            // This snippet has a result that must be separately allocated
            // Otherwise it is assumed that the result is part of the inputs
            if (resultOperand.kind != Kind.Void && resultOperand.kind != Kind.Illegal) {
                if (setInstructionResult) {
                    outputOperand = newVariable(instruction.kind());
                } else {
                    outputOperand = newVariable(resultOperand.kind);
                }
                assert operandsArray[resultOperand.index] == null;
            }
            operandsArray[resultOperand.index] = outputOperand;
            if (GraalOptions.PrintXirTemplates) {
                TTY.println("Output operand: " + outputOperand);
            }
        }

        for (XirTemp t : snippet.template.temps) {
            if (t instanceof XirRegister) {
                XirRegister reg = (XirRegister) t;
                if (!t.reserve) {
                    operandsArray[t.index] = reg.register;
                }
            }
        }

        for (XirConstant c : snippet.template.constants) {
            assert operandsArray[c.index] == null;
            operandsArray[c.index] = c.value;
        }

        XirOperand[] inputOperands = snippet.template.inputOperands;
        XirOperand[] inputTempOperands = snippet.template.inputTempOperands;
        XirOperand[] tempOperands = snippet.template.tempOperands;

        Value[] inputOperandArray = new Value[inputOperands.length + inputTempOperands.length];
        Value[] tempOperandArray = new Value[tempOperands.length];
        int[] inputOperandIndicesArray = new int[inputOperands.length + inputTempOperands.length];
        int[] tempOperandIndicesArray = new int[tempOperands.length];
        for (int i = 0; i < inputOperands.length; i++) {
            XirOperand x = inputOperands[i];
            Value op = allocateOperand(snippet, x);
            operandsArray[x.index] = op;
            inputOperandArray[i] = op;
            inputOperandIndicesArray[i] = x.index;
            if (GraalOptions.PrintXirTemplates) {
                TTY.println("Input operand: " + x);
            }
        }

        assert inputTempOperands.length == 0 : "cwi: I think this code is never used.  If you see this exception being thrown, please tell me...";

        for (int i = 0; i < tempOperands.length; i++) {
            XirOperand x = tempOperands[i];
            Value op = allocateOperand(snippet, x);
            operandsArray[x.index] = op;
            tempOperandArray[i] = op;
            tempOperandIndicesArray[i] = x.index;
            if (GraalOptions.PrintXirTemplates) {
                TTY.println("Temp operand: " + x);
            }
        }

        for (Value operand : operandsArray) {
            assert operand != null;
        }

        Value allocatedResultOperand = operandsArray[resultOperand.index];
        if (!isVariable(allocatedResultOperand) && !isRegister(allocatedResultOperand)) {
            allocatedResultOperand = IllegalValue;
        }

        if (setInstructionResult && isLegal(allocatedResultOperand)) {
            Value operand = operand(instruction);
            if (operand == null) {
                setResult(instruction, allocatedResultOperand);
            } else {
                assert operand == allocatedResultOperand;
            }
        }


        XirInstruction[] slowPath = snippet.template.slowPath;
        if (!isConstant(operandsArray[resultOperand.index]) || snippet.template.fastPath.length != 0 || (slowPath != null && slowPath.length > 0)) {
            // XIR instruction is only needed when the operand is not a constant!
            emitXir(snippet, operandsArray, allocatedResultOperand,
                    inputOperandArray, tempOperandArray, inputOperandIndicesArray, tempOperandIndicesArray,
                    (allocatedResultOperand == IllegalValue) ? -1 : resultOperand.index,
                    info, infoAfter, trueSuccessor, falseSuccessor);
            Debug.metric("LIRXIRInstructions").increment();
        }

        return operandsArray[resultOperand.index];
    }

    protected abstract void emitXir(XirSnippet snippet, Value[] operands, Value outputOperand, Value[] inputs, Value[] temps, int[] inputOperandIndices, int[] tempOperandIndices, int outputOperandIndex,
                    LIRFrameState info, LIRFrameState infoAfter, LabelRef trueSuccessor, LabelRef falseSuccessor);

    protected final Value callRuntime(RuntimeCall runtimeCall, LIRFrameState info, Value... args) {
        // get a result register
        Kind result = runtimeCall.resultKind;
        Kind[] arguments = runtimeCall.arguments;

        Value physReg = result.isVoid() ? IllegalValue : resultOperandFor(result);

        List<Value> argumentList;
        if (arguments.length > 0) {
            // move the arguments into the correct location
            CallingConvention cc = frameMap.registerConfig.getCallingConvention(RuntimeCall, arguments, target(), false);
            frameMap.callsMethod(cc, RuntimeCall);
            assert cc.locations.length == args.length : "argument count mismatch";
            for (int i = 0; i < args.length; i++) {
                Value arg = args[i];
                Value loc = cc.locations[i];
                emitMove(arg, loc);
            }
            argumentList = Arrays.asList(cc.locations);
        } else {
            // no arguments
            assert args == null || args.length == 0;
            argumentList = Util.uncheckedCast(Collections.emptyList());
        }

        emitCall(runtimeCall, physReg, argumentList, Constant.forLong(0), info, null);

        return physReg;
    }

    protected final Variable callRuntimeWithResult(RuntimeCall runtimeCall, LIRFrameState info, Value... args) {
        Value location = callRuntime(runtimeCall, info, args);
        return emitMove(location);
    }

    protected XirSupport site(ValueNode x) {
        return xirSupport.site(x, null);
    }

    protected XirSupport site(ValueNode x, ValueNode receiver) {
        return xirSupport.site(x, receiver);
    }

    /**
     * Implements site-specific information for the XIR interface.
     */
    static class XirSupport implements XirSite {
        final Assumptions assumptions;
        ValueNode current;
        ValueNode receiver;


        public XirSupport(Assumptions assumptions) {
            this.assumptions = assumptions;
        }

        public boolean isNonNull(XirArgument argument) {
            return false;
        }

        public boolean requiresNullCheck() {
            return receiver == null || !(receiver.stamp() instanceof ObjectStamp && ((ObjectStamp) receiver.stamp()).nonNull());
        }

        public boolean requiresBoundsCheck() {
            return true;
        }

        public boolean requiresReadBarrier() {
            return current == null || true;
        }

        public boolean requiresWriteBarrier() {
            return current == null || true;
        }

        public boolean requiresArrayStoreCheck() {
            return true;
        }

        public Assumptions assumptions() {
            return assumptions;
        }

        XirSupport site(ValueNode v, ValueNode r) {
            current = v;
            receiver = r;
            return this;
        }

        @Override
        public String toString() {
            return "XirSupport<" + current + ">";
        }
    }

    public FrameMap frameMap() {
        return frameMap;
    }
}
