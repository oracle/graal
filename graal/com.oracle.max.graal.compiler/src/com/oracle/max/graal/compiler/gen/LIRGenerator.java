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
package com.oracle.max.graal.compiler.gen;

import static com.oracle.max.cri.ci.CiCallingConvention.Type.*;
import static com.oracle.max.cri.ci.CiValue.*;
import static com.oracle.max.cri.ci.CiValueUtil.*;
import static com.oracle.max.cri.util.MemoryBarriers.*;
import static com.oracle.max.graal.alloc.util.ValueUtil.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.max.asm.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ci.CiTargetMethod.Mark;
import com.oracle.max.cri.ri.*;
import com.oracle.max.cri.ri.RiType.Representation;
import com.oracle.max.cri.xir.CiXirAssembler.XirConstant;
import com.oracle.max.cri.xir.CiXirAssembler.XirInstruction;
import com.oracle.max.cri.xir.CiXirAssembler.XirMark;
import com.oracle.max.cri.xir.CiXirAssembler.XirOperand;
import com.oracle.max.cri.xir.CiXirAssembler.XirParameter;
import com.oracle.max.cri.xir.CiXirAssembler.XirRegister;
import com.oracle.max.cri.xir.CiXirAssembler.XirTemp;
import com.oracle.max.cri.xir.*;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.cfg.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.lir.StandardOp.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.debug.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.DeoptimizeNode.DeoptAction;
import com.oracle.max.graal.nodes.PhiNode.PhiType;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.extended.*;
import com.oracle.max.graal.nodes.java.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.virtual.*;

/**
 * This class traverses the HIR instructions and generates LIR instructions from them.
 */
public abstract class LIRGenerator extends LIRGeneratorTool {
    protected final Graph graph;
    protected final RiRuntime runtime;
    protected final CiTarget target;
    protected final RiResolvedMethod method;
    protected final FrameMap frameMap;
    public final NodeMap<CiValue> nodeOperands;

    protected final LIR lir;
    protected final XirSupport xirSupport;
    protected final RiXirGenerator xir;
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
         * The frame state of the caller of the method performing the lock, or null if the outermost method
         * performs the lock. This information is used to compute the {@link CiFrame} that this lock belongs to.
         * We cannot use the actual frame state of the locking method, because it is now unique for a method. The
         * caller frame states are unique, i.e., all frame states of an inlined methods refer to the same caller frame state.
         */
        public final FrameState callerState;

        /**
         * The number of locks already found for this frame state.
         */
        public final int stateDepth;

        /**
         * The monitor enter node, with information about the object that is locked and the elimination status.
         */
        public final MonitorEnterNode monitor;

        /**
         * Space in the stack frame needed by the VM to perform the locking.
         */
        public final CiStackSlot lockData;

        public LockScope(LockScope outer, FrameState callerState, MonitorEnterNode monitor, CiStackSlot lockData) {
            this.outer = outer;
            this.callerState = callerState;
            this.monitor = monitor;
            this.lockData = lockData;
            if (outer != null && outer.callerState == callerState) {
                this.stateDepth = outer.stateDepth + 1;
            } else {
                this.stateDepth = 0;
            }
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


    public LIRGenerator(Graph graph, RiRuntime runtime, CiTarget target, FrameMap frameMap, RiResolvedMethod method, LIR lir, RiXirGenerator xir) {
        this.graph = graph;
        this.runtime = runtime;
        this.target = target;
        this.frameMap = frameMap;
        this.method = method;
        this.nodeOperands = graph.createNodeMap();
        this.lir = lir;
        this.xir = xir;
        this.xirSupport = new XirSupport();
        this.debugInfoBuilder = new DebugInfoBuilder(nodeOperands);
        this.blockLocks = new BlockMap<>(lir.cfg);
        this.blockLastState = new BlockMap<>(lir.cfg);
    }

    @Override
    public CiTarget target() {
        return target;
    }

    /**
     * Returns the operand that has been previously initialized by {@link #setResult()}
     * with the result of an instruction.
     * @param node A node that produces a result value.
     */
    @Override
    public CiValue operand(ValueNode node) {
        if (nodeOperands == null) {
            return null;
        }
        return nodeOperands.get(node);
    }

    /**
     * Creates a new {@linkplain Variable variable}.
     * @param kind The kind of the new variable.
     * @return a new variable
     */
    @Override
    public Variable newVariable(CiKind kind) {
        CiKind stackKind = kind.stackKind();
        switch (stackKind) {
            case Jsr:
            case Int:
            case Long:
            case Object:
                return new Variable(stackKind, lir.nextVariable(), CiRegister.RegisterFlag.CPU);
            case Float:
            case Double:
                return new Variable(stackKind, lir.nextVariable(), CiRegister.RegisterFlag.FPU);
            default:
                throw Util.shouldNotReachHere();
        }
    }

    @Override
    public CiValue setResult(ValueNode x, CiValue operand) {
        assert (isVariable(operand) && x.kind() == operand.kind) || (isConstant(operand) && x.kind() == operand.kind.stackKind()) : operand.kind + " for node " + x;

        assert operand(x) == null : "operand cannot be set twice";
        assert operand != null && isLegal(operand) : "operand must be legal";
        assert operand.kind.stackKind() == x.kind();
        assert !(x instanceof VirtualObjectNode);
        nodeOperands.set(x, operand);
        return operand;
    }

    @Override
    public abstract Variable emitMove(CiValue input);

    public Variable load(CiValue value) {
        if (!isVariable(value)) {
            return emitMove(value);
        }
        return (Variable) value;
    }

    public CiValue loadNonConst(CiValue value) {
        if (isConstant(value) && !canInlineConstant((CiConstant) value)) {
            return emitMove(value);
        }
        return value;
    }

    public CiValue loadForStore(CiValue value, CiKind storeKind) {
        if (isConstant(value) && canStoreConstant((CiConstant) value)) {
            return value;
        }
        if (storeKind == CiKind.Byte || storeKind == CiKind.Boolean) {
            Variable tempVar = new Variable(value.kind, lir.nextVariable(), CiRegister.RegisterFlag.Byte);
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

    public LIRDebugInfo state() {
        assert lastState != null : "must have state before instruction";
        return stateFor(lastState);
    }

    public LIRDebugInfo stateFor(FrameState state) {
        return stateFor(state, null, null);
    }

    public LIRDebugInfo stateFor(FrameState state, List<CiStackSlot> pointerSlots, LabelRef exceptionEdge) {
        return debugInfoBuilder.build(state, curLocks, pointerSlots, exceptionEdge);
    }

    /**
     * Gets the ABI specific operand used to return a value of a given kind from a method.
     *
     * @param kind the kind of value being returned
     * @return the operand representing the ABI defined location used return a value of kind {@code kind}
     */
    public CiValue resultOperandFor(CiKind kind) {
        if (kind == CiKind.Void) {
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

        if (GraalOptions.AllocSSA && block.getBeginNode() instanceof MergeNode) {
            assert phiValues.isEmpty();
            MergeNode merge = (MergeNode) block.getBeginNode();
            for (PhiNode phi : merge.phis()) {
                if (phi.type() == PhiType.Value) {
                    CiValue phiValue = newVariable(phi.kind());
                    setResult(phi, phiValue);
                    phiValues.add(phiValue);
                }
            }
            append(new PhiLabelOp(new Label(), block.align, phiValues.toArray(new CiValue[phiValues.size()])));
            phiValues.clear();
        } else {
            append(new LabelOp(new Label(), block.align));
        }

        if (GraalOptions.TraceLIRGeneratorLevel >= 1) {
            TTY.println("BEGIN Generating LIR for block B" + block.getId());
        }

        curLocks = null;
        for (Block pred : block.getPredecessors()) {
            LockScope predLocks = blockLocks.get(pred);
            if (curLocks == null) {
                curLocks = predLocks;
            } else if (curLocks != predLocks && (!pred.isLoopEnd() || predLocks != null)) {
                throw new CiBailout("unbalanced monitors: predecessor blocks have different monitor states");
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
                } else if (fs != blockLastState.get(pred)) {
                    fs = null;
                    break;
                }
            }
            if (GraalOptions.TraceLIRGeneratorLevel >= 2) {
                if (fs == null) {
                    TTY.println("STATE RESET");
                } else {
                    TTY.println("STATE CHANGE (singlePred)");
                    if (GraalOptions.TraceLIRGeneratorLevel >= 3) {
                        TTY.println(fs.toDetailedString());
                    }
                }
            }
            lastState = fs;
        }

        List<Node> nodes = lir.nodesFor(block);
        for (int i = 0; i < nodes.size(); i++) {
            Node instr = nodes.get(i);

            if (GraalOptions.OptImplicitNullChecks) {
                Node nextInstr = null;
                if (i < nodes.size() - 1) {
                    nextInstr = nodes.get(i + 1);
                }

                if (instr instanceof GuardNode) {
                    GuardNode guardNode = (GuardNode) instr;
                    if (guardNode.condition() instanceof NullCheckNode) {
                        NullCheckNode nullCheckNode = (NullCheckNode) guardNode.condition();
                        if (!nullCheckNode.expectedNull && nextInstr instanceof Access) {
                            Access access = (Access) nextInstr;
                            if (nullCheckNode.object() == access.object() && canBeNullCheck(access.location())) {
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
                doRoot((ValueNode) instr);
            }
            if (stateAfter != null) {
                lastState = stateAfter;
                assert checkStartOperands(instr, lastState);
                if (GraalOptions.TraceLIRGeneratorLevel >= 2) {
                    TTY.println("STATE CHANGE");
                    if (GraalOptions.TraceLIRGeneratorLevel >= 3) {
                        TTY.println(stateAfter.toDetailedString());
                    }
                }
            }
        }
        if (block.numberOfSux() >= 1 && !endsWithJump(block)) {
            NodeSuccessorsIterable successors = block.getEndNode().successors();
            assert successors.count() >= 1 : "should have at least one successor : " + block.getEndNode();

            emitJump(getLIRBlock((FixedNode) successors.first()), null);
        }

        if (GraalOptions.TraceLIRGeneratorLevel >= 1) {
            TTY.println("END Generating LIR for block B" + block.getId());
        }

        blockLocks.put(currentBlock, curLocks);
        blockLastState.put(block, lastState);
        currentBlock = null;

        if (GraalOptions.PrintIRWithLIR) {
            TTY.println();
        }
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
        // TODO: Make this part of CiTarget
        return !(location instanceof IndexedLocationNode) && location.displacement() < 4096;
    }

    private void emitPrologue() {
        CiCallingConvention incomingArguments = frameMap.registerConfig.getCallingConvention(JavaCallee, CiUtil.signatureToKinds(method), target, false);

        CiValue[] params = new CiValue[incomingArguments.locations.length];
        for (int i = 0; i < params.length; i++) {
            params[i] = toStackKind(incomingArguments.locations[i]);
        }
        append(new ParametersOp(params));

        XirSnippet prologue = xir.genPrologue(null, method);
        if (prologue != null) {
            emitXir(prologue, null, null, false);
        }

        for (LocalNode local : graph.getNodes(LocalNode.class)) {
            CiValue param = params[local.index()];
            assert param.kind == local.kind().stackKind();
            setResult(local, emitMove(param));
        }
    }

    private boolean checkStartOperands(Node node, FrameState fs) {
        if (!Modifier.isNative(method.accessFlags())) {
            if (node == ((StructuredGraph) node.graph()).start()) {
                CiKind[] arguments = CiUtil.signatureToKinds(method);
                int slot = 0;
                for (CiKind kind : arguments) {
                    ValueNode arg = fs.localAt(slot);
                    assert arg != null && arg.kind() == kind.stackKind() : "No valid local in framestate for slot #" + slot + " (" + arg + ")";
                    slot++;
                    if (slot < fs.localsSize() && fs.localAt(slot) == null) {
                        slot++;
                    }
                }
            }
        }
        return true;
    }


    @Override
    public void visitArrayLength(ArrayLengthNode x) {
        XirArgument array = toXirArgument(x.array());
        XirSnippet snippet = xir.genArrayLength(site(x), array);
        emitXir(snippet, x, state(), true);
        operand(x);
    }

    @Override
    public void visitCheckCast(CheckCastNode x) {
        XirSnippet snippet = xir.genCheckCast(site(x), toXirArgument(x.object()), toXirArgument(x.targetClassInstruction()), x.targetClass(), x.hints(), x.hintsExact());
        emitXir(snippet, x, state(), true);
        // The result of a checkcast is the unmodified object, so no need to allocate a new variable for it.
        setResult(x, operand(x.object()));
    }

    @Override
    public void visitMonitorEnter(MonitorEnterNode x) {
        CiStackSlot lockData = frameMap.allocateStackBlock(runtime.sizeOfLockData(), false);
        if (x.eliminated()) {
            // No code is emitted for eliminated locks, but for proper debug information generation we need to
            // register the monitor and its lock data.
            curLocks = new LockScope(curLocks, x.stateAfter().outerFrameState(), x, lockData);
            return;
        }

        XirArgument obj = toXirArgument(x.object());
        XirArgument lockAddress = lockData == null ? null : toXirArgument(emitLea(lockData));

        LIRDebugInfo stateBefore = state();
        // The state before the monitor enter is used for null checks, so it must not contain the newly locked object.
        curLocks = new LockScope(curLocks, x.stateAfter().outerFrameState(), x, lockData);
        // The state after the monitor enter is used for deoptimization, after the monitor has blocked, so it must contain the newly locked object.
        LIRDebugInfo stateAfter = stateFor(x.stateAfter());

        XirSnippet snippet = xir.genMonitorEnter(site(x), obj, lockAddress);
        emitXir(snippet, x, stateBefore, stateAfter, true, null, null);
    }

    @Override
    public void visitMonitorExit(MonitorExitNode x) {
        if (curLocks == null || curLocks.monitor.object() != x.object() || curLocks.monitor.eliminated() != x.eliminated()) {
            throw new CiBailout("unbalanced monitors: attempting to unlock an object that is not on top of the locking stack");
        }
        if (x.eliminated()) {
            curLocks = curLocks.outer;
            return;
        }

        CiStackSlot lockData = curLocks.lockData;
        XirArgument obj = toXirArgument(x.object());
        XirArgument lockAddress = lockData == null ? null : toXirArgument(emitLea(lockData));

        LIRDebugInfo stateBefore = state();
        curLocks = curLocks.outer;

        XirSnippet snippet = xir.genMonitorExit(site(x), obj, lockAddress);
        emitXir(snippet, x, stateBefore, true);
    }

    @Override
    public void visitLoadField(LoadFieldNode x) {
        RiField field = x.field();
        LIRDebugInfo info = state();
        if (x.isVolatile()) {
            emitMembar(JMM_PRE_VOLATILE_READ);
        }
        XirArgument receiver = toXirArgument(x.object());
        XirSnippet snippet = x.isStatic() ? xir.genGetStatic(site(x), receiver, field) : xir.genGetField(site(x), receiver, field);
        emitXir(snippet, x, info, true);
        if (x.isVolatile()) {
            emitMembar(JMM_POST_VOLATILE_READ);
        }
    }

    @Override
    public void visitStoreField(StoreFieldNode x) {
        RiField field = x.field();
        LIRDebugInfo info = state();
        if (x.isVolatile()) {
            emitMembar(JMM_PRE_VOLATILE_WRITE);
        }
        XirArgument receiver = toXirArgument(x.object());
        XirArgument value = toXirArgument(x.value());
        XirSnippet snippet = x.isStatic() ? xir.genPutStatic(site(x), receiver, field, value) : xir.genPutField(site(x), receiver, field, value);
        emitXir(snippet, x, info, true);
        if (x.isVolatile()) {
            emitMembar(JMM_POST_VOLATILE_WRITE);
        }
    }

    @Override
    public void visitLoadIndexed(LoadIndexedNode x) {
        XirArgument array = toXirArgument(x.array());
        XirArgument index = toXirArgument(x.index());
        XirSnippet snippet = xir.genArrayLoad(site(x), array, index, x.elementKind(), null);
        emitXir(snippet, x, state(), true);
    }

    @Override
    public void visitStoreIndexed(StoreIndexedNode x) {
        XirArgument array = toXirArgument(x.array());
        XirArgument index = toXirArgument(x.index());
        XirArgument value = toXirArgument(x.value());
        XirSnippet snippet = xir.genArrayStore(site(x), array, index, value, x.elementKind(), null);
        emitXir(snippet, x, state(), true);
    }

    @Override
    public void visitNewInstance(NewInstanceNode x) {
        XirSnippet snippet = xir.genNewInstance(site(x), x.instanceClass());
        emitXir(snippet, x, state(), true);
    }

    @Override
    public void visitNewTypeArray(NewTypeArrayNode x) {
        XirArgument length = toXirArgument(x.length());
        XirSnippet snippet = xir.genNewArray(site(x), length, x.elementType().kind(true), null, null);
        emitXir(snippet, x, state(), true);
    }

    @Override
    public void visitNewObjectArray(NewObjectArrayNode x) {
        XirArgument length = toXirArgument(x.length());
        XirSnippet snippet = xir.genNewArray(site(x), length, CiKind.Object, x.elementType(), x.exactType());
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
    public void visitExceptionObject(ExceptionObjectNode x) {
        XirSnippet snippet = xir.genExceptionObject(site(x));
        LIRDebugInfo info = state();
        emitXir(snippet, x, info, true);
    }

    @Override
    public void visitReturn(ReturnNode x) {
        CiValue operand = CiValue.IllegalValue;
        if (!x.kind().isVoid()) {
            operand = resultOperandFor(x.kind());
            emitMove(operand(x.result()), operand);
        }
        XirSnippet epilogue = xir.genEpilogue(site(x), method);
        if (epilogue != null) {
            emitXir(epilogue, x, null, false);
            emitReturn(operand);
        }
    }

    protected abstract void emitReturn(CiValue input);

    @SuppressWarnings("unused")
    protected void postGCWriteBarrier(CiValue addr, CiValue newVal) {
        XirSnippet writeBarrier = xir.genWriteBarrier(toXirArgument(addr));
        if (writeBarrier != null) {
            emitXir(writeBarrier, null, null, false);
        }
     }

    @SuppressWarnings("unused")
    protected void preGCWriteBarrier(CiValue addrOpr, boolean patch, LIRDebugInfo info) {
        // TODO(tw): Implement this.
    }



    @Override
    public void visitMerge(MergeNode x) {
    }

    @Override
    public void visitEndNode(EndNode end) {
        moveToPhi(end.merge(), end);
    }

    @Override
    public void visitLoopEnd(LoopEndNode x) {
        if (GraalOptions.GenLoopSafepoints && x.hasSafepointPolling()) {
            emitSafepointPoll(x);
        }
        moveToPhi(x.loopBegin(), x);
    }

    private ArrayList<CiValue> phiValues = new ArrayList<>();

    private void moveToPhi(MergeNode merge, FixedNode pred) {
        if (GraalOptions.AllocSSA) {
            assert phiValues.isEmpty();
            for (PhiNode phi : merge.phis()) {
                if (phi.type() == PhiType.Value) {
                    phiValues.add(operand(phi.valueAt(pred)));
                }
            }
            append(new PhiJumpOp(getLIRBlock(merge), phiValues.toArray(new CiValue[phiValues.size()])));
            phiValues.clear();
            return;
        }

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

    private CiValue operandForPhi(PhiNode phi) {
        assert phi.type() == PhiType.Value : "wrong phi type: " + phi;
        CiValue result = operand(phi);
        if (result == null) {
            // allocate a variable for this phi
            Variable newOperand = newVariable(phi.kind());
            setResult(phi, newOperand);
            return newOperand;
        } else {
            return result;
        }
    }


    public void emitSafepointPoll(FixedNode x) {
        if (!lastState.method().noSafepointPolls()) {
            XirSnippet snippet = xir.genSafepointPoll(site(x));
            emitXir(snippet, x, state(), false);
        }
    }

    @Override
    public void emitIf(IfNode x) {
        assert x.defaultSuccessor() == x.falseSuccessor() : "wrong destination";
        emitBranch(x.compare(), getLIRBlock(x.trueSuccessor()),  getLIRBlock(x.falseSuccessor()), null);
    }

    @Override
    public void emitGuardCheck(BooleanNode comp) {
        if (comp instanceof NullCheckNode && !((NullCheckNode) comp).expectedNull) {
            emitNullCheckGuard((NullCheckNode) comp);
        } else if (comp instanceof ConstantNode && comp.asConstant().asBoolean()) {
            // True constant, nothing to emit.
        } else {
            // Fall back to a normal branch.
            LIRDebugInfo info = state();
            LabelRef stubEntry = createDeoptStub(DeoptAction.InvalidateReprofile, info, comp);
            emitBranch(comp, null, stubEntry, info);
        }
    }

    protected abstract void emitNullCheckGuard(NullCheckNode node);

    public void emitBranch(BooleanNode node, LabelRef trueSuccessor, LabelRef falseSuccessor, LIRDebugInfo info) {
        if (node instanceof NullCheckNode) {
            emitNullCheckBranch((NullCheckNode) node, trueSuccessor, falseSuccessor, info);
        } else if (node instanceof CompareNode) {
            emitCompareBranch((CompareNode) node, trueSuccessor, falseSuccessor, info);
        } else if (node instanceof InstanceOfNode) {
            emitInstanceOfBranch((InstanceOfNode) node, trueSuccessor, falseSuccessor, info);
        } else if (node instanceof ConstantNode) {
            emitConstantBranch(((ConstantNode) node).asConstant().asBoolean(), trueSuccessor, falseSuccessor, info);
        } else if (node instanceof IsTypeNode) {
            emitTypeBranch((IsTypeNode) node, trueSuccessor, falseSuccessor, info);
        } else {
            throw Util.unimplemented(node.toString());
        }
    }

    private void emitNullCheckBranch(NullCheckNode node, LabelRef trueSuccessor, LabelRef falseSuccessor, LIRDebugInfo info) {
        Condition cond = node.expectedNull ? Condition.NE : Condition.EQ;
        emitBranch(operand(node.object()), CiConstant.NULL_OBJECT, cond, false, falseSuccessor, info);
        if (trueSuccessor != null) {
            emitJump(trueSuccessor, null);
        }
    }

    public void emitCompareBranch(CompareNode compare, LabelRef trueSuccessorBlock, LabelRef falseSuccessorBlock, LIRDebugInfo info) {
        emitBranch(operand(compare.x()), operand(compare.y()), compare.condition().negate(), !compare.unorderedIsTrue(), falseSuccessorBlock, info);
        if (trueSuccessorBlock != null) {
            emitJump(trueSuccessorBlock, null);
        }
    }

    private void emitInstanceOfBranch(InstanceOfNode x, LabelRef trueSuccessor, LabelRef falseSuccessor, LIRDebugInfo info) {
        XirArgument obj = toXirArgument(x.object());
        XirSnippet snippet = xir.genInstanceOf(site(x), obj, toXirArgument(x.targetClassInstruction()), x.targetClass(), x.hints(), x.hintsExact());
        emitXir(snippet, x, info, null, false, x.negated() ? falseSuccessor : trueSuccessor, x.negated() ? trueSuccessor : falseSuccessor);
    }

    public void emitConstantBranch(boolean value, LabelRef trueSuccessorBlock, LabelRef falseSuccessorBlock, LIRDebugInfo info) {
        LabelRef block = value ? trueSuccessorBlock : falseSuccessorBlock;
        if (block != null) {
            emitJump(block, info);
        }
    }

    public void emitTypeBranch(IsTypeNode x, LabelRef trueSuccessor, LabelRef falseSuccessor, LIRDebugInfo info) {
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
        CiValue tVal = operand(conditional.trueValue());
        CiValue fVal = operand(conditional.falseValue());
        setResult(conditional, emitConditional(conditional.condition(), tVal, fVal));
    }

    public Variable emitConditional(BooleanNode node, CiValue trueValue, CiValue falseValue) {
        assert trueValue instanceof CiConstant && (trueValue.kind.stackKind() == CiKind.Int || trueValue.kind == CiKind.Long);
        assert falseValue instanceof CiConstant && (falseValue.kind.stackKind() == CiKind.Int || trueValue.kind == CiKind.Long);

        if (node instanceof NullCheckNode) {
            return emitNullCheckConditional((NullCheckNode) node, trueValue, falseValue);
        } else if (node instanceof CompareNode) {
            return emitCompareConditional((CompareNode) node, trueValue, falseValue);
        } else if (node instanceof InstanceOfNode) {
            return emitInstanceOfConditional((InstanceOfNode) node, trueValue, falseValue);
        } else if (node instanceof ConstantNode) {
            return emitConstantConditional(((ConstantNode) node).asConstant().asBoolean(), trueValue, falseValue);
        } else {
            throw Util.unimplemented(node.toString());
        }
    }

    private Variable emitNullCheckConditional(NullCheckNode node, CiValue trueValue, CiValue falseValue) {
        Condition cond = node.expectedNull ? Condition.EQ : Condition.NE;
        return emitCMove(operand(node.object()), CiConstant.NULL_OBJECT, cond, false, trueValue, falseValue);
    }

    private Variable emitInstanceOfConditional(InstanceOfNode x, CiValue trueValue, CiValue falseValue) {
        XirArgument obj = toXirArgument(x.object());
        XirArgument trueArg = toXirArgument(x.negated() ? falseValue : trueValue);
        XirArgument falseArg = toXirArgument(x.negated() ? trueValue : falseValue);
        XirSnippet snippet = xir.genMaterializeInstanceOf(site(x), obj, toXirArgument(x.targetClassInstruction()), trueArg, falseArg, x.targetClass(), x.hints(), x.hintsExact());
        return (Variable) emitXir(snippet, null, null, false);
    }

    private Variable emitConstantConditional(boolean value, CiValue trueValue, CiValue falseValue) {
        return emitMove(value ? trueValue : falseValue);
    }

    private Variable emitCompareConditional(CompareNode compare, CiValue trueValue, CiValue falseValue) {
        return emitCMove(operand(compare.x()), operand(compare.y()), compare.condition(), compare.unorderedIsTrue(), trueValue, falseValue);
    }


    public abstract void emitLabel(Label label, boolean align);
    public abstract void emitJump(LabelRef label, LIRDebugInfo info);
    public abstract void emitBranch(CiValue left, CiValue right, Condition cond, boolean unorderedIsTrue, LabelRef label, LIRDebugInfo info);
    public abstract Variable emitCMove(CiValue leftVal, CiValue right, Condition cond, boolean unorderedIsTrue, CiValue trueValue, CiValue falseValue);

    protected FrameState stateBeforeCallWithArguments(FrameState stateAfter, MethodCallTargetNode call, int bci) {
        return stateAfter.duplicateModified(bci, stateAfter.rethrowException(), call.returnKind(), toJVMArgumentStack(call.targetMethod().signature(), call.isStatic(), call.arguments()));
    }

    private static ValueNode[] toJVMArgumentStack(RiSignature signature, boolean isStatic, NodeInputList<ValueNode> arguments) {
        int slotCount = signature.argumentSlots(!isStatic);
        ValueNode[] stack = new ValueNode[slotCount];
        int stackIndex = 0;
        int argumentIndex = 0;
        for (ValueNode arg : arguments) {
            stack[stackIndex] = arg;

            if (stackIndex == 0 && !isStatic) {
                // Current argument is receiver.
                stackIndex += stackSlots(CiKind.Object);
            } else {
                stackIndex += stackSlots(signature.argumentKindAt(argumentIndex, false));
                argumentIndex++;
            }
        }
        return stack;
    }


    public static int stackSlots(CiKind kind) {
        return isTwoSlot(kind) ? 2 : 1;
    }

    public static boolean isTwoSlot(CiKind kind) {
        assert kind != CiKind.Void && kind != CiKind.Illegal;
        return kind == CiKind.Long || kind == CiKind.Double;
    }

    @Override
    public void emitInvoke(Invoke x) {
        MethodCallTargetNode callTarget = x.callTarget();
        RiMethod targetMethod = callTarget.targetMethod();

        XirSnippet snippet = null;
        XirArgument receiver;
        switch (callTarget.invokeKind()) {
            case Static:
                snippet = xir.genInvokeStatic(site(x.node()), targetMethod);
                break;
            case Special:
                receiver = toXirArgument(callTarget.receiver());
                snippet = xir.genInvokeSpecial(site(x.node()), receiver, targetMethod);
                break;
            case Virtual:
                assert callTarget.receiver().kind() == CiKind.Object : callTarget + ": " + callTarget.targetMethod().toString();
                receiver = toXirArgument(callTarget.receiver());
                snippet = xir.genInvokeVirtual(site(x.node()), receiver, targetMethod);
                break;
            case Interface:
                assert callTarget.receiver().kind() == CiKind.Object : callTarget;
                receiver = toXirArgument(callTarget.receiver());
                snippet = xir.genInvokeInterface(site(x.node()), receiver, targetMethod);
                break;
        }

        CiValue destinationAddress = null;
        if (!target().invokeSnippetAfterArguments) {
            // TODO This is the version currently necessary for Maxine: since the invokeinterface-snippet uses a division, it
            // destroys rdx, which is also used to pass a parameter.  Therefore, the snippet must be before the parameters are assigned to their locations.
            LIRDebugInfo addrInfo = stateFor(stateBeforeCallWithArguments(x.stateAfter(), callTarget, x.bci()));
            destinationAddress = emitXir(snippet, x.node(), addrInfo, false);
        }

        CiValue resultOperand = resultOperandFor(x.node().kind());

        CiKind[] signature = CiUtil.signatureToKinds(callTarget.targetMethod().signature(), callTarget.isStatic() ? null : callTarget.targetMethod().holder().kind(true));
        CiCallingConvention cc = frameMap.registerConfig.getCallingConvention(JavaCall, signature, target(), false);
        frameMap.callsMethod(cc, JavaCall);
        List<CiStackSlot> pointerSlots = new ArrayList<>(2);
        List<CiValue> argList = visitInvokeArguments(cc, callTarget.arguments(), pointerSlots);

        if (target().invokeSnippetAfterArguments) {
            // TODO This is the version currently active for HotSpot.
            LIRDebugInfo addrInfo = stateFor(stateBeforeCallWithArguments(x.stateAfter(), callTarget, x.bci()), pointerSlots, null);
            destinationAddress = emitXir(snippet, x.node(), addrInfo, false);
        }

        LIRDebugInfo callInfo = stateFor(x.stateDuring(), pointerSlots, x instanceof InvokeWithExceptionNode ? getLIRBlock(((InvokeWithExceptionNode) x).exceptionEdge()) : null);
        emitCall(targetMethod, resultOperand, argList, destinationAddress, callInfo, snippet.marks);

        if (isLegal(resultOperand)) {
            setResult(x.node(), emitMove(resultOperand));
        }
    }

    protected abstract void emitCall(Object targetMethod, CiValue result, List<CiValue> arguments, CiValue targetAddress, LIRDebugInfo info, Map<XirMark, Mark> marks);


    private static CiValue toStackKind(CiValue value) {
        if (value.kind.stackKind() != value.kind) {
            // We only have stack-kinds in the LIR, so convert the operand kind for values from the calling convention.
            if (isRegister(value)) {
                return asRegister(value).asValue(value.kind.stackKind());
            } else if (isStackSlot(value)) {
                return CiStackSlot.get(value.kind.stackKind(), asStackSlot(value).rawOffset(), asStackSlot(value).rawAddFrameSize());
            } else {
                throw Util.shouldNotReachHere();
            }
        }
        return value;
    }

    public List<CiValue> visitInvokeArguments(CiCallingConvention cc, Iterable<ValueNode> arguments, List<CiStackSlot> pointerSlots) {
        // for each argument, load it into the correct location
        List<CiValue> argList = new ArrayList<>();
        int j = 0;
        for (ValueNode arg : arguments) {
            if (arg != null) {
                CiValue operand = toStackKind(cc.locations[j++]);

                if (isStackSlot(operand) && operand.kind == CiKind.Object && pointerSlots != null) {
                    assert !asStackSlot(operand).inCallerFrame();
                    // This slot must be marked explicitly in the pointer map.
                    pointerSlots.add(asStackSlot(operand));
                }

                emitMove(operand(arg), operand);
                argList.add(operand);

            } else {
                throw Util.shouldNotReachHere("I thought we no longer have null entries for two-slot types...");
            }
        }
        return argList;
    }


    protected abstract LabelRef createDeoptStub(DeoptAction action, LIRDebugInfo info, Object deoptInfo);

    @Override
    public Variable emitCallToRuntime(CiRuntimeCall runtimeCall, boolean canTrap, CiValue... args) {
        LIRDebugInfo info = canTrap ? state() : null;

        CiKind result = runtimeCall.resultKind;
        CiKind[] arguments = runtimeCall.arguments;
        CiValue physReg = resultOperandFor(result);

        List<CiValue> argumentList;
        if (arguments.length > 0) {
            // move the arguments into the correct location
            CiCallingConvention cc = frameMap.registerConfig.getCallingConvention(RuntimeCall, arguments, target(), false);
            frameMap.callsMethod(cc, RuntimeCall);
            assert cc.locations.length == args.length : "argument count mismatch";
            for (int i = 0; i < args.length; i++) {
                CiValue arg = args[i];
                CiValue loc = cc.locations[i];
                emitMove(arg, loc);
            }
            argumentList = Arrays.asList(cc.locations);
        } else {
            // no arguments
            assert args == null || args.length == 0;
            argumentList = Collections.emptyList();
        }

        emitCall(runtimeCall, physReg, argumentList, CiConstant.forLong(0), info, null);

        if (isLegal(physReg)) {
            return emitMove(physReg);
        } else {
            return null;
        }
    }

    @Override
    public void emitRuntimeCall(RuntimeCallNode x) {
        // TODO Merge with emitCallToRuntime() method above.

        CiValue resultOperand = resultOperandFor(x.kind());
        CiCallingConvention cc = frameMap.registerConfig.getCallingConvention(RuntimeCall, x.call().arguments, target(), false);
        frameMap.callsMethod(cc, RuntimeCall);
        List<CiStackSlot> pointerSlots = new ArrayList<>(2);
        List<CiValue> argList = visitInvokeArguments(cc, x.arguments(), pointerSlots);

        LIRDebugInfo info = null;
        FrameState stateAfter = x.stateAfter();
        if (stateAfter != null) {
            // TODO change back to stateBeforeReturn() when RuntimeCallNode uses a CallTargetNode
            FrameState stateBeforeReturn = stateAfter.duplicateModified(stateAfter.bci, stateAfter.rethrowException(), x.kind());

            // TODO is it correct here that the pointerSlots are not passed to the oop map generation?
            info = stateFor(stateBeforeReturn);
        }

        emitCall(x.call(), resultOperand, argList, CiConstant.forLong(0), info, null);

        if (isLegal(resultOperand)) {
            setResult(x, emitMove(resultOperand));
        }
    }

    @Override
    public void emitLookupSwitch(LookupSwitchNode x) {
        Variable tag = load(operand(x.value()));
        if (x.numberOfCases() == 0 || x.numberOfCases() < GraalOptions.SequentialSwitchLimit) {
            int len = x.numberOfCases();
            for (int i = 0; i < len; i++) {
                emitBranch(tag, CiConstant.forInt(x.keyAt(i)), Condition.EQ, false, getLIRBlock(x.blockSuccessor(i)), null);
            }
            emitJump(getLIRBlock(x.defaultSuccessor()), null);
        } else {
            visitSwitchRanges(createSwitchRanges(x, null), tag, getLIRBlock(x.defaultSuccessor()));
        }
    }

    @Override
    public void emitTableSwitch(TableSwitchNode x) {
        Variable value = load(operand(x.value()));
        // TODO: tune the defaults for the controls used to determine what kind of translation to use
        if (x.numberOfCases() == 0 || x.numberOfCases() <= GraalOptions.SequentialSwitchLimit) {
            int loKey = x.lowKey();
            int len = x.numberOfCases();
            for (int i = 0; i < len; i++) {
                emitBranch(value, CiConstant.forInt(i + loKey), Condition.EQ, false, getLIRBlock(x.blockSuccessor(i)), null);
            }
            emitJump(getLIRBlock(x.defaultSuccessor()), null);
        } else {
            SwitchRange[] switchRanges = createSwitchRanges(null, x);
            int rangeDensity = x.numberOfCases() / switchRanges.length;
            if (rangeDensity >= GraalOptions.RangeTestsSwitchDensity) {
                visitSwitchRanges(switchRanges, value, getLIRBlock(x.defaultSuccessor()));
            } else {
                LabelRef[] targets = new LabelRef[x.numberOfCases()];
                for (int i = 0; i < x.numberOfCases(); ++i) {
                    targets[i] = getLIRBlock(x.blockSuccessor(i));
                }
                emitTableSwitch(x.lowKey(), getLIRBlock(x.defaultSuccessor()), targets, value);
            }
        }
    }

    protected abstract void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, CiValue index);

    // the range of values in a lookupswitch or tableswitch statement
    private static final class SwitchRange {
        protected final int lowKey;
        protected int highKey;
        protected final LabelRef sux;

        SwitchRange(int lowKey, LabelRef sux) {
            this.lowKey = lowKey;
            this.highKey = lowKey;
            this.sux = sux;
        }
    }

    private SwitchRange[] createSwitchRanges(LookupSwitchNode ls, TableSwitchNode ts) {
        // Only one of the parameters is used, but code is shared because it is mostly the same.
        SwitchNode x = ls != null ? ls : ts;
        // we expect the keys to be sorted by increasing value
        List<SwitchRange> res = new ArrayList<>(x.numberOfCases());
        int len = x.numberOfCases();
        if (len > 0) {
            LabelRef defaultSux = getLIRBlock(x.defaultSuccessor());
            int key = ls != null ? ls.keyAt(0) : ts.lowKey();
            LabelRef sux = getLIRBlock(x.blockSuccessor(0));
            SwitchRange range = new SwitchRange(key, sux);
            for (int i = 1; i < len; i++) {
                int newKey = ls != null ? ls.keyAt(i) : key + 1;
                LabelRef newSux = getLIRBlock(x.blockSuccessor(i));
                if (key + 1 == newKey && sux == newSux) {
                    // still in same range
                    range.highKey = newKey;
                } else {
                    // skip tests which explicitly dispatch to the default
                    if (range.sux != defaultSux) {
                        res.add(range);
                    }
                    range = new SwitchRange(newKey, newSux);
                }
                key = newKey;
                sux = newSux;
            }
            if (res.size() == 0 || res.get(res.size() - 1) != range) {
                res.add(range);
            }
        }
        return res.toArray(new SwitchRange[res.size()]);
    }

    private void visitSwitchRanges(SwitchRange[] x, Variable value, LabelRef defaultSux) {
        for (int i = 0; i < x.length; i++) {
            SwitchRange oneRange = x[i];
            int lowKey = oneRange.lowKey;
            int highKey = oneRange.highKey;
            LabelRef dest = oneRange.sux;
            if (lowKey == highKey) {
                emitBranch(value, CiConstant.forInt(lowKey), Condition.EQ, false, dest, null);
            } else if (highKey - lowKey == 1) {
                emitBranch(value, CiConstant.forInt(lowKey), Condition.EQ, false, dest, null);
                emitBranch(value, CiConstant.forInt(highKey), Condition.EQ, false, dest, null);
            } else {
                Label l = new Label();
                emitBranch(value, CiConstant.forInt(lowKey), Condition.LT, false, LabelRef.forLabel(l), null);
                emitBranch(value, CiConstant.forInt(highKey), Condition.LE, false, dest, null);
                emitLabel(l, false);
            }
        }
        emitJump(defaultSux, null);
    }


    protected XirArgument toXirArgument(CiValue v) {
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

    private CiValue allocateOperand(XirSnippet snippet, XirOperand op) {
        if (op instanceof XirParameter)  {
            XirParameter param = (XirParameter) op;
            return allocateOperand(snippet.arguments[param.parameterIndex], op, param.canBeConstant);
        } else if (op instanceof XirRegister) {
            XirRegister reg = (XirRegister) op;
            return reg.register;
        } else if (op instanceof XirTemp) {
            return newVariable(op.kind);
        } else {
            Util.shouldNotReachHere();
            return null;
        }
    }

    private CiValue allocateOperand(XirArgument arg, XirOperand var, boolean canBeConstant) {
        if (arg.constant != null) {
            return arg.constant;
        }

        CiValue value = (CiValue) arg.object;
        if (canBeConstant) {
            return value;
        }
        Variable variable = load(value);
        if (var.kind == CiKind.Byte || var.kind == CiKind.Boolean) {
            Variable tempVar = new Variable(value.kind, lir.nextVariable(), CiRegister.RegisterFlag.Byte);
            emitMove(variable, tempVar);
            variable = tempVar;
        }
        return variable;
    }

    protected CiValue emitXir(XirSnippet snippet, ValueNode x, LIRDebugInfo info, boolean setInstructionResult) {
        return emitXir(snippet, x, info, null, setInstructionResult, null, null);
    }

    protected CiValue emitXir(XirSnippet snippet, ValueNode instruction, LIRDebugInfo info, LIRDebugInfo infoAfter, boolean setInstructionResult, LabelRef trueSuccessor, LabelRef falseSuccessor) {
        if (GraalOptions.PrintXirTemplates) {
            TTY.println("Emit XIR template " + snippet.template.name);
        }

        final CiValue[] operandsArray = new CiValue[snippet.template.variableCount];

        frameMap.reserveOutgoing(snippet.template.outgoingStackSize);

        XirOperand resultOperand = snippet.template.resultOperand;

        if (snippet.template.allocateResultOperand) {
            CiValue outputOperand = IllegalValue;
            // This snippet has a result that must be separately allocated
            // Otherwise it is assumed that the result is part of the inputs
            if (resultOperand.kind != CiKind.Void && resultOperand.kind != CiKind.Illegal) {
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

        CiValue[] inputOperandArray = new CiValue[inputOperands.length + inputTempOperands.length];
        CiValue[] tempOperandArray = new CiValue[tempOperands.length];
        int[] inputOperandIndicesArray = new int[inputOperands.length + inputTempOperands.length];
        int[] tempOperandIndicesArray = new int[tempOperands.length];
        for (int i = 0; i < inputOperands.length; i++) {
            XirOperand x = inputOperands[i];
            CiValue op = allocateOperand(snippet, x);
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
            CiValue op = allocateOperand(snippet, x);
            operandsArray[x.index] = op;
            tempOperandArray[i] = op;
            tempOperandIndicesArray[i] = x.index;
            if (GraalOptions.PrintXirTemplates) {
                TTY.println("Temp operand: " + x);
            }
        }

        for (CiValue operand : operandsArray) {
            assert operand != null;
        }

        CiValue allocatedResultOperand = operandsArray[resultOperand.index];
        if (!isVariable(allocatedResultOperand) && !isRegister(allocatedResultOperand)) {
            allocatedResultOperand = IllegalValue;
        }

        if (setInstructionResult && isLegal(allocatedResultOperand)) {
            CiValue operand = operand(instruction);
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

    protected abstract void emitXir(XirSnippet snippet, CiValue[] operands, CiValue outputOperand, CiValue[] inputs, CiValue[] temps, int[] inputOperandIndices, int[] tempOperandIndices, int outputOperandIndex,
                    LIRDebugInfo info, LIRDebugInfo infoAfter, LabelRef trueSuccessor, LabelRef falseSuccessor);

    protected final CiValue callRuntime(CiRuntimeCall runtimeCall, LIRDebugInfo info, CiValue... args) {
        // get a result register
        CiKind result = runtimeCall.resultKind;
        CiKind[] arguments = runtimeCall.arguments;

        CiValue physReg = result.isVoid() ? IllegalValue : resultOperandFor(result);

        List<CiValue> argumentList;
        if (arguments.length > 0) {
            // move the arguments into the correct location
            CiCallingConvention cc = frameMap.registerConfig.getCallingConvention(RuntimeCall, arguments, target(), false);
            frameMap.callsMethod(cc, RuntimeCall);
            assert cc.locations.length == args.length : "argument count mismatch";
            for (int i = 0; i < args.length; i++) {
                CiValue arg = args[i];
                CiValue loc = cc.locations[i];
                emitMove(arg, loc);
            }
            argumentList = Arrays.asList(cc.locations);
        } else {
            // no arguments
            assert args == null || args.length == 0;
            argumentList = Util.uncheckedCast(Collections.emptyList());
        }

        emitCall(runtimeCall, physReg, argumentList, CiConstant.forLong(0), info, null);

        return physReg;
    }

    protected final Variable callRuntimeWithResult(CiRuntimeCall runtimeCall, LIRDebugInfo info, CiValue... args) {
        CiValue location = callRuntime(runtimeCall, info, args);
        return emitMove(location);
    }

    SwitchRange[] createLookupRanges(LookupSwitchNode x) {
        // we expect the keys to be sorted by increasing value
        List<SwitchRange> res = new ArrayList<>(x.numberOfCases());
        int len = x.numberOfCases();
        if (len > 0) {
            LabelRef defaultSux = getLIRBlock(x.defaultSuccessor());
            int key = x.keyAt(0);
            LabelRef sux = getLIRBlock(x.blockSuccessor(0));
            SwitchRange range = new SwitchRange(key, sux);
            for (int i = 1; i < len; i++) {
                int newKey = x.keyAt(i);
                LabelRef newSux = getLIRBlock(x.blockSuccessor(i));
                if (key + 1 == newKey && sux == newSux) {
                    // still in same range
                    range.highKey = newKey;
                } else {
                    // skip tests which explicitly dispatch to the default
                    if (range.sux != defaultSux) {
                        res.add(range);
                    }
                    range = new SwitchRange(newKey, newSux);
                }
                key = newKey;
                sux = newSux;
            }
            if (res.size() == 0 || res.get(res.size() - 1) != range) {
                res.add(range);
            }
        }
        return res.toArray(new SwitchRange[res.size()]);
    }

    SwitchRange[] createLookupRanges(TableSwitchNode x) {
        // TODO: try to merge this with the code for LookupSwitch
        List<SwitchRange> res = new ArrayList<>(x.numberOfCases());
        int len = x.numberOfCases();
        if (len > 0) {
            LabelRef sux = getLIRBlock(x.blockSuccessor(0));
            int key = x.lowKey();
            LabelRef defaultSux = getLIRBlock(x.defaultSuccessor());
            SwitchRange range = new SwitchRange(key, sux);
            for (int i = 0; i < len; i++, key++) {
                LabelRef newSux = getLIRBlock(x.blockSuccessor(i));
                if (sux == newSux) {
                    // still in same range
                    range.highKey = key;
                } else {
                    // skip tests which explicitly dispatch to the default
                    if (sux != defaultSux) {
                        res.add(range);
                    }
                    range = new SwitchRange(key, newSux);
                }
                sux = newSux;
            }
            if (res.size() == 0 || res.get(res.size() - 1) != range) {
                res.add(range);
            }
        }
        return res.toArray(new SwitchRange[res.size()]);
    }

    protected XirSupport site(ValueNode x) {
        return xirSupport.site(x);
    }

    /**
     * Implements site-specific information for the XIR interface.
     */
    static class XirSupport implements XirSite {
        ValueNode current;

        XirSupport() {
        }

        public CiCodePos getCodePos() {
            if (current instanceof StateSplit) {
                FrameState stateAfter = ((StateSplit) current).stateAfter();
                if (stateAfter != null) {
                    return stateAfter.toCodePos();
                }
            }
            return null;
        }

        public boolean isNonNull(XirArgument argument) {
            return false;
        }

        public boolean requiresNullCheck() {
            return current == null || true;
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

        public RiType getApproximateType(XirArgument argument) {
            return current == null ? null : current.declaredType();
        }

        public RiType getExactType(XirArgument argument) {
            return current == null ? null : current.exactType();
        }

        XirSupport site(ValueNode v) {
            current = v;
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
