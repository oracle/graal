/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.max.graal.alloc.util.ValueUtil.*;
import static com.oracle.max.cri.intrinsics.MemoryBarriers.*;
import static com.sun.cri.ci.CiCallingConvention.Type.*;
import static com.sun.cri.ci.CiValue.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.max.asm.*;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.alloc.*;
import com.oracle.max.graal.compiler.alloc.OperandPool.VariableFlag;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.graphbuilder.*;
import com.oracle.max.graal.compiler.lir.FrameMap.StackBlock;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.compiler.stub.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.DeoptimizeNode.DeoptAction;
import com.oracle.max.graal.nodes.PhiNode.PhiType;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.extended.*;
import com.oracle.max.graal.nodes.java.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;
import com.sun.cri.ri.RiType.Representation;
import com.sun.cri.xir.CiXirAssembler.XirConstant;
import com.sun.cri.xir.CiXirAssembler.XirInstruction;
import com.sun.cri.xir.CiXirAssembler.XirOperand;
import com.sun.cri.xir.CiXirAssembler.XirParameter;
import com.sun.cri.xir.CiXirAssembler.XirRegister;
import com.sun.cri.xir.CiXirAssembler.XirTemp;
import com.sun.cri.xir.*;

/**
 * This class traverses the HIR instructions and generates LIR instructions from them.
 */
public abstract class LIRGenerator extends LIRGeneratorTool {
    public final GraalContext context;
    public final GraalCompilation compilation;
    protected final LIR lir;
    protected final XirSupport xirSupport;
    protected final RiXirGenerator xir;
    public final OperandPool operands;
    private final DebugInfoBuilder debugInfoBuilder;

    private LIRBlock currentBlock;
    private ValueNode currentInstruction;
    private ValueNode lastInstructionPrinted; // Debugging only
    private FrameState lastState;

    public LIRGenerator(GraalCompilation compilation, RiXirGenerator xir) {
        this.context = compilation.compiler.context;
        this.compilation = compilation;
        this.lir = compilation.lir();
        this.xir = xir;
        this.xirSupport = new XirSupport();
        this.operands = new OperandPool(compilation.compiler.target);
        this.debugInfoBuilder = new DebugInfoBuilder(compilation);
    }

    @Override
    public CiTarget target() {
        return compilation.compiler.target;
    }


    /**
     * Returns the operand that has been previously initialized by {@link #setResult()}
     * with the result of an instruction.
     * @param node A node that produces a result value.
     */
    @Override
    public CiValue operand(ValueNode node) {
        return compilation.operand(node);
    }

    /**
     * Creates a new {@linkplain CiVariable variable}.
     * @param kind The kind of the new variable.
     * @return a new variable
     */
    @Override
    public CiVariable newVariable(CiKind kind) {
        return operands.newVariable(kind.stackKind());
    }

    @Override
    public CiValue setResult(ValueNode x, CiValue operand) {
        assert (operand.isVariable() && x.kind() == operand.kind) || (operand.isConstant() && x.kind() == operand.kind.stackKind()) : operand.kind + " for node " + x;

        compilation.setOperand(x, operand);
        if (GraalOptions.DetailedAsserts) {
            if (operand.isVariable()) {
                operands.recordResult((CiVariable) operand, x);
            }
        }
        return operand;
    }


    public CiVariable load(CiValue value) {
        if (!value.isVariable()) {
            return emitMove(value);
        }
        return (CiVariable) value;
    }

    public CiValue loadNonConst(CiValue value) {
        if (value.isConstant() && !canInlineConstant((CiConstant) value)) {
            return emitMove(value);
        }
        return value;
    }

    public CiValue loadForStore(CiValue value, CiKind storeKind) {
        if (value.isConstant() && canStoreConstant((CiConstant) value)) {
            return value;
        }
        if (storeKind == CiKind.Byte || storeKind == CiKind.Boolean) {
            CiVariable tempVar = emitMove(value);
            operands.setFlag(tempVar, VariableFlag.MustBeByteRegister);
            return tempVar;
        }
        return load(value);
    }

    protected LabelRef getLIRBlock(FixedNode b) {
        LIRBlock result = lir.valueToBlock().get(b);
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
        return debugInfoBuilder.build(state, pointerSlots, exceptionEdge);
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
        return compilation.registerConfig.getReturnRegister(kind).asValue(kind);
    }


    public void append(LIRInstruction op) {
        if (GraalOptions.PrintIRWithLIR && !TTY.isSuppressed()) {
            if (currentInstruction != null && lastInstructionPrinted != currentInstruction) {
                lastInstructionPrinted = currentInstruction;
                InstructionPrinter ip = new InstructionPrinter(TTY.out());
                ip.printInstructionListing(currentInstruction);
            }
            TTY.println(op.toStringWithIdPrefix());
            TTY.println();
        }
        currentBlock.lir().add(op);
    }

    public void doBlock(LIRBlock block) {
        if (GraalOptions.PrintIRWithLIR) {
            TTY.print(block.toString());
        }

        currentBlock = block;
        // set up the list of LIR instructions
        assert block.lir() == null : "LIR list already computed for this block";
        block.setLir(new ArrayList<LIRInstruction>());

        emitLabel(block.label(), block.align());

        if (GraalOptions.TraceLIRGeneratorLevel >= 1) {
            TTY.println("BEGIN Generating LIR for block B" + block.blockID());
        }

        if (block == lir.startBlock()) {
            XirSnippet prologue = xir.genPrologue(null, compilation.method);
            if (prologue != null) {
                emitXir(prologue, null, null, null, false);
            }
            setOperandsForParameters();
        } else if (block.getPredecessors().size() > 0) {
            FrameState fs = null;
            for (Block p : block.getPredecessors()) {
                LIRBlock pred = (LIRBlock) p;
                if (fs == null) {
                    fs = pred.lastState();
                } else if (fs != pred.lastState()) {
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

        if (GraalOptions.AllocSSA && block.firstNode() instanceof MergeNode) {
            block.phis = new LIRPhiMapping(block, this);
        }

        for (int i = 0; i < block.getInstructions().size(); ++i) {
            Node instr = block.getInstructions().get(i);

            if (GraalOptions.OptImplicitNullChecks) {
                Node nextInstr = null;
                if (i < block.getInstructions().size() - 1) {
                    nextInstr = block.getInstructions().get(i + 1);
                }

                if (instr instanceof GuardNode) {
                    GuardNode guardNode = (GuardNode) instr;
                    if (guardNode.condition() instanceof NullCheckNode) {
                        NullCheckNode nullCheckNode = (NullCheckNode) guardNode.condition();
                        if (!nullCheckNode.expectedNull && nextInstr instanceof AccessNode) {
                            AccessNode accessNode = (AccessNode) nextInstr;
                            if (nullCheckNode.object() == accessNode.object() && canBeNullCheck(accessNode.location())) {
                                //TTY.println("implicit null check");
                                accessNode.setNullCheck(true);
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
        if (block.numberOfSux() >= 1 && !block.endsWithJump()) {
            NodeSuccessorsIterable successors = block.lastNode().successors();
            assert successors.explicitCount() >= 1 : "should have at least one successor : " + block.lastNode();

            emitJump(getLIRBlock((FixedNode) successors.first()), null);
        }

        if (GraalOptions.TraceLIRGeneratorLevel >= 1) {
            TTY.println("END Generating LIR for block B" + block.blockID());
        }

        block.setLastState(lastState);
        currentBlock = null;

        if (GraalOptions.PrintIRWithLIR) {
            TTY.println();
        }
    }

    private void doRoot(ValueNode instr) {
        if (GraalOptions.TraceLIRGeneratorLevel >= 2) {
            TTY.println("Emitting LIR for instruction " + instr);
        }
        currentInstruction = instr;

        if (GraalOptions.TraceLIRVisit) {
            TTY.println("Visiting    " + instr);
        }

        emitNode(instr);

        if (GraalOptions.TraceLIRVisit) {
            TTY.println("Operand for " + instr + " = " + compilation.operand(instr));
        }
    }

    protected void emitNode(ValueNode node) {
        ((LIRLowerable) node).generate(this);
    }

    private static boolean canBeNullCheck(LocationNode location) {
        // TODO: Make this part of CiTarget
        return !(location instanceof IndexedLocationNode) && location.displacement() < 4096;
    }

    private void setOperandsForParameters() {
        CiCallingConvention args = compilation.frameMap().incomingArguments();
        for (LocalNode local : compilation.graph.getNodes(LocalNode.class)) {
            int i = local.index();
            CiValue src = args.locations[i];
            CiVariable dest = emitMove(src);
            assert src.isLegal() : "check";
            assert src.kind.stackKind() == local.kind().stackKind() : "local type check failed";
            setResult(local, dest);
        }
    }

    private boolean checkStartOperands(Node node, FrameState fs) {
        if (!Modifier.isNative(compilation.method.accessFlags())) {
            if (node == ((StructuredGraph) node.graph()).start()) {
                CiKind[] arguments = CiUtil.signatureToKinds(compilation.method);
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
        emitXir(snippet, x, state(), null, true);
        operand(x);
    }

    @Override
    public void visitCheckCast(CheckCastNode x) {
        XirSnippet snippet = xir.genCheckCast(site(x), toXirArgument(x.object()), toXirArgument(x.targetClassInstruction()), x.targetClass());
        emitXir(snippet, x, state(), null, true);
        // The result of a checkcast is the unmodified object, so no need to allocate a new variable for it.
        setResult(x, operand(x.object()));
    }

    @Override
    public void visitMonitorEnter(MonitorEnterNode x) {
        XirArgument obj = toXirArgument(x.object().owner());
        XirArgument lockAddress = toXirArgument(emitLea(debugInfoBuilder.lockDataFor(x.object())));
        XirSnippet snippet = xir.genMonitorEnter(site(x), obj, lockAddress);
        emitXir(snippet, x, state(), stateFor(x.stateAfter()), null, true);
    }

    @Override
    public void visitMonitorExit(MonitorExitNode x) {
        XirArgument obj = toXirArgument(x.object().owner());
        XirArgument lockAddress = toXirArgument(emitLea(debugInfoBuilder.lockDataFor(x.object())));
        XirSnippet snippet = xir.genMonitorExit(site(x), obj, lockAddress);
        emitXir(snippet, x, state(), null, true);
    }

    protected abstract CiVariable emitLea(StackBlock stackBlock);

    @Override
    public void visitLoadField(LoadFieldNode x) {
        RiField field = x.field();
        LIRDebugInfo info = state();
        if (x.isVolatile()) {
            emitMembar(JMM_PRE_VOLATILE_READ);
        }
        XirArgument receiver = toXirArgument(x.object());
        XirSnippet snippet = x.isStatic() ? xir.genGetStatic(site(x), receiver, field) : xir.genGetField(site(x), receiver, field);
        emitXir(snippet, x, info, null, true);
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
        emitXir(snippet, x, info, null, true);
        if (x.isVolatile()) {
            emitMembar(JMM_POST_VOLATILE_WRITE);
        }
    }

    @Override
    public void visitLoadIndexed(LoadIndexedNode x) {
        XirArgument array = toXirArgument(x.array());
        XirArgument index = toXirArgument(x.index());
        XirSnippet snippet = xir.genArrayLoad(site(x), array, index, x.elementKind(), null);
        emitXir(snippet, x, state(), null, true);
    }

    @Override
    public void visitStoreIndexed(StoreIndexedNode x) {
        XirArgument array = toXirArgument(x.array());
        XirArgument index = toXirArgument(x.index());
        XirArgument value = toXirArgument(x.value());
        XirSnippet snippet = xir.genArrayStore(site(x), array, index, value, x.elementKind(), null);
        emitXir(snippet, x, state(), null, true);
    }

    @Override
    public void visitNewInstance(NewInstanceNode x) {
        XirSnippet snippet = xir.genNewInstance(site(x), x.instanceClass());
        emitXir(snippet, x, state(), null, true);
    }

    @Override
    public void visitNewTypeArray(NewTypeArrayNode x) {
        XirArgument length = toXirArgument(x.length());
        XirSnippet snippet = xir.genNewArray(site(x), length, x.elementType().kind(true), null, null);
        emitXir(snippet, x, state(), null, true);
    }

    @Override
    public void visitNewObjectArray(NewObjectArrayNode x) {
        XirArgument length = toXirArgument(x.length());
        XirSnippet snippet = xir.genNewArray(site(x), length, CiKind.Object, x.elementType(), x.exactType());
        emitXir(snippet, x, state(), null, true);
    }

    @Override
    public void visitNewMultiArray(NewMultiArrayNode x) {
        XirArgument[] dims = new XirArgument[x.dimensionCount()];
        for (int i = 0; i < dims.length; i++) {
            dims[i] = toXirArgument(x.dimension(i));
        }
        XirSnippet snippet = xir.genNewMultiArray(site(x), dims, x.type());
        emitXir(snippet, x, state(), null, true);
    }

    @Override
    public void visitExceptionObject(ExceptionObjectNode x) {
        XirSnippet snippet = xir.genExceptionObject(site(x));
        LIRDebugInfo info = state();
        emitXir(snippet, x, info, null, true);
    }

    @Override
    public void visitReturn(ReturnNode x) {
        CiValue operand = CiValue.IllegalValue;
        if (!x.kind().isVoid()) {
            operand = resultOperandFor(x.kind());
            emitMove(operand(x.result()), operand);
        }
        XirSnippet epilogue = xir.genEpilogue(site(x), compilation.method);
        if (epilogue != null) {
            emitXir(epilogue, x, null, compilation.method, false);
            append(StandardOpcode.RETURN.create(operand));
        }
    }

    @SuppressWarnings("unused")
    protected void postGCWriteBarrier(CiValue addr, CiValue newVal) {
        XirSnippet writeBarrier = xir.genWriteBarrier(toXirArgument(addr));
        if (writeBarrier != null) {
            emitXir(writeBarrier, null, null, null, false);
        }
    }

    @SuppressWarnings("unused")
    protected void preGCWriteBarrier(CiValue addrOpr, boolean patch, LIRDebugInfo info) {
        // TODO(tw): Implement this.
    }



    @Override
    public void visitMerge(MergeNode x) {
        if (x.next() instanceof LoopBeginNode) {
            moveToPhi((LoopBeginNode) x.next(), x);
        }
    }

    @Override
    public void visitEndNode(EndNode end) {
        assert end.merge() != null;
        moveToPhi(end.merge(), end);
        emitJump(getLIRBlock(end.merge()), null);
    }

    @Override
    public void visitLoopEnd(LoopEndNode x) {
        moveToPhi(x.loopBegin(), x);
        if (GraalOptions.GenLoopSafepoints && x.hasSafepointPolling()) {
            emitSafepointPoll(x);
        }
        emitJump(getLIRBlock(x.loopBegin()), null);
    }

    public void emitSafepointPoll(FixedNode x) {
        if (!lastState.method().noSafepointPolls()) {
            XirSnippet snippet = xir.genSafepointPoll(site(x));
            emitXir(snippet, x, state(), null, false);
        }
    }

    @Override
    public void emitIf(IfNode x) {
        assert x.defaultSuccessor() == x.falseSuccessor() : "wrong destination";
        emitBranch(x.compare(), getLIRBlock(x.trueSuccessor()),  getLIRBlock(x.falseSuccessor()), null);
    }

    @Override
    public void emitGuardCheck(BooleanNode comp) {
        if (comp instanceof IsTypeNode) {
            emitTypeGuard((IsTypeNode) comp);
        } else if (comp instanceof NullCheckNode && !((NullCheckNode) comp).expectedNull) {
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

    private void emitNullCheckGuard(NullCheckNode node) {
        assert !node.expectedNull;
        NullCheckNode x = node;
        CiVariable value = load(operand(x.object()));
        LIRDebugInfo info = state();
        append(StandardOpcode.NULL_CHECK.create(value, info));
    }

    private void emitTypeGuard(IsTypeNode node) {
        load(operand(node.object()));
        LIRDebugInfo info = state();
        XirArgument clazz = toXirArgument(node.type().getEncoding(Representation.ObjectHub));
        XirSnippet typeCheck = xir.genTypeCheck(site(node), toXirArgument(node.object()), clazz, node.type());
        emitXir(typeCheck, node, info, compilation.method, false);
    }


    public void emitBranch(BooleanNode node, LabelRef trueSuccessor, LabelRef falseSuccessor, LIRDebugInfo info) {
        if (node instanceof NullCheckNode) {
            emitNullCheckBranch((NullCheckNode) node, trueSuccessor, falseSuccessor, info);
        } else if (node instanceof CompareNode) {
            emitCompareBranch((CompareNode) node, trueSuccessor, falseSuccessor, info);
        } else if (node instanceof InstanceOfNode) {
            emitInstanceOfBranch((InstanceOfNode) node, trueSuccessor, falseSuccessor, info);
        } else if (node instanceof ConstantNode) {
            emitConstantBranch(((ConstantNode) node).asConstant().asBoolean(), trueSuccessor, falseSuccessor, info);
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
        XirSnippet snippet = xir.genInstanceOf(site(x), obj, toXirArgument(x.targetClassInstruction()), x.targetClass());
        emitXir(snippet, x, info, null, false);
        LIRXirInstruction instr = (LIRXirInstruction) currentBlock.lir().get(currentBlock.lir().size() - 1);
        instr.setTrueSuccessor(x.negated ? falseSuccessor : trueSuccessor);
        instr.setFalseSuccessor(x.negated ? trueSuccessor : falseSuccessor);
    }


    public void emitConstantBranch(boolean value, LabelRef trueSuccessorBlock, LabelRef falseSuccessorBlock, LIRDebugInfo info) {
        LabelRef block = value ? trueSuccessorBlock : falseSuccessorBlock;
        if (block != null) {
            emitJump(block, info);
        }
    }

    @Override
    public void emitConditional(ConditionalNode conditional) {
        CiValue tVal = operand(conditional.trueValue());
        CiValue fVal = operand(conditional.falseValue());
        setResult(conditional, emitConditional(conditional.condition(), tVal, fVal));
    }

    public CiVariable emitConditional(BooleanNode node, CiValue trueValue, CiValue falseValue) {
        assert trueValue instanceof CiConstant && trueValue.kind.stackKind() == CiKind.Int;
        assert falseValue instanceof CiConstant && falseValue.kind.stackKind() == CiKind.Int;

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

    private CiVariable emitNullCheckConditional(NullCheckNode node, CiValue trueValue, CiValue falseValue) {
        Condition cond = node.expectedNull ? Condition.EQ : Condition.NE;
        return emitCMove(operand(node.object()), CiConstant.NULL_OBJECT, cond, false, trueValue, falseValue);
    }

    private CiVariable emitInstanceOfConditional(InstanceOfNode x, CiValue trueValue, CiValue falseValue) {
        XirArgument obj = toXirArgument(x.object());
        XirArgument trueArg = toXirArgument(x.negated ? falseValue : trueValue);
        XirArgument falseArg = toXirArgument(x.negated ? trueValue : falseValue);
        XirSnippet snippet = xir.genMaterializeInstanceOf(site(x), obj, toXirArgument(x.targetClassInstruction()), trueArg, falseArg, x.targetClass());
        return (CiVariable) emitXir(snippet, null, null, null, false);
    }

    private CiVariable emitConstantConditional(boolean value, CiValue trueValue, CiValue falseValue) {
        return emitMove(value ? trueValue : falseValue);
    }

    private CiVariable emitCompareConditional(CompareNode compare, CiValue trueValue, CiValue falseValue) {
        return emitCMove(operand(compare.x()), operand(compare.y()), compare.condition(), compare.unorderedIsTrue(), trueValue, falseValue);
    }


    public abstract void emitLabel(Label label, boolean align);
    public abstract void emitJump(LabelRef label, LIRDebugInfo info);
    public abstract void emitBranch(CiValue left, CiValue right, Condition cond, boolean unorderedIsTrue, LabelRef label, LIRDebugInfo info);
    public abstract CiVariable emitCMove(CiValue leftVal, CiValue right, Condition cond, boolean unorderedIsTrue, CiValue trueValue, CiValue falseValue);

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
                stackIndex += FrameStateBuilder.stackSlots(CiKind.Object);
            } else {
                stackIndex += FrameStateBuilder.stackSlots(signature.argumentKindAt(argumentIndex, false));
                argumentIndex++;
            }
        }
        return stack;
    }

    @Override
    public void emitInvoke(Invoke x) {
        MethodCallTargetNode callTarget = x.callTarget();
        RiMethod target = callTarget.targetMethod();

        XirSnippet snippet = null;
        XirArgument receiver;
        switch (callTarget.invokeKind()) {
            case Static:
                snippet = xir.genInvokeStatic(site(x.node()), target);
                break;
            case Special:
                receiver = toXirArgument(callTarget.receiver());
                snippet = xir.genInvokeSpecial(site(x.node()), receiver, target);
                break;
            case Virtual:
                assert callTarget.receiver().kind() == CiKind.Object : callTarget + ": " + callTarget.targetMethod().toString();
                receiver = toXirArgument(callTarget.receiver());
                snippet = xir.genInvokeVirtual(site(x.node()), receiver, target);
                break;
            case Interface:
                assert callTarget.receiver().kind() == CiKind.Object : callTarget;
                receiver = toXirArgument(callTarget.receiver());
                snippet = xir.genInvokeInterface(site(x.node()), receiver, target);
                break;
        }

        CiValue destinationAddress = null;
        if (!target().invokeSnippetAfterArguments) {
            // TODO This is the version currently necessary for Maxine: since the invokeinterface-snippet uses a division, it
            // destroys rdx, which is also used to pass a parameter.  Therefore, the snippet must be before the parameters are assigned to their locations.
            LIRDebugInfo addrInfo = stateFor(stateBeforeCallWithArguments(x.stateAfter(), callTarget, x.bci()));
            destinationAddress = emitXir(snippet, x.node(), addrInfo, null, callTarget.targetMethod(), false);
        }

        CiValue resultOperand = resultOperandFor(x.node().kind());

        CiKind[] signature = CiUtil.signatureToKinds(callTarget.targetMethod().signature(), callTarget.isStatic() ? null : callTarget.targetMethod().holder().kind(true));
        CiCallingConvention cc = compilation.registerConfig.getCallingConvention(JavaCall, signature, target(), false);
        compilation.frameMap().adjustOutgoingStackSize(cc, JavaCall);
        List<CiStackSlot> pointerSlots = new ArrayList<>(2);
        List<CiValue> argList = visitInvokeArguments(cc, callTarget.arguments(), pointerSlots);

        if (target().invokeSnippetAfterArguments) {
            // TODO This is the version currently active for HotSpot.
            LIRDebugInfo addrInfo = stateFor(stateBeforeCallWithArguments(x.stateAfter(), callTarget, x.bci()), pointerSlots, null);
            destinationAddress = emitXir(snippet, x.node(), addrInfo, null, callTarget.targetMethod(), false);
        }

        LIRDebugInfo callInfo = stateFor(x.stateDuring(), pointerSlots, x instanceof InvokeWithExceptionNode ? getLIRBlock(((InvokeWithExceptionNode) x).exceptionEdge()) : null);

        // emit direct or indirect call to the destination address
        if (destinationAddress instanceof CiConstant) {
            // Direct call
            assert ((CiConstant) destinationAddress).isDefaultValue() : "destination address should be zero";
            append(StandardOpcode.DIRECT_CALL.create(target, resultOperand, argList, null, callInfo, snippet.marks));
        } else {
            // Indirect call
            append(StandardOpcode.INDIRECT_CALL.create(target, resultOperand, argList, destinationAddress, callInfo, snippet.marks));
        }

        if (resultOperand.isLegal()) {
            setResult(x.node(), emitMove(resultOperand));
        }
    }

    public List<CiValue> visitInvokeArguments(CiCallingConvention cc, Iterable<ValueNode> arguments, List<CiStackSlot> pointerSlots) {
        // for each argument, load it into the correct location
        List<CiValue> argList = new ArrayList<>();
        int j = 0;
        for (ValueNode arg : arguments) {
            if (arg != null) {
                CiValue operand = cc.locations[j++];
                if (isRegister(operand)) {
                    if (operand.kind.stackKind() != operand.kind) {
                        // We only have stack-kinds in the LIR, so convert the operand kind.
                        operand = asRegister(operand).asValue(operand.kind.stackKind());
                    }

                } else if (isStackSlot(operand)) {
                    assert !asStackSlot(operand).inCallerFrame();
                    if (operand.kind == CiKind.Object && pointerSlots != null) {
                        // This slot must be marked explicitly in the pointer map.
                        pointerSlots.add(asStackSlot(operand));
                    }
                    if (operand.kind.stackKind() != operand.kind) {
                        // We only have stack-kinds in the LIR, so convert the operand kind.
                        operand = CiStackSlot.get(operand.kind.stackKind(), asStackSlot(operand).index());
                    }
                } else {
                    throw Util.shouldNotReachHere();
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
    public CiVariable emitCallToRuntime(CiRuntimeCall runtimeCall, boolean canTrap, CiValue... args) {
        LIRDebugInfo info = canTrap ? state() : null;

        CiKind result = runtimeCall.resultKind;
        CiKind[] arguments = runtimeCall.arguments;
        CiValue physReg = resultOperandFor(result);

        List<CiValue> argumentList;
        if (arguments.length > 0) {
            // move the arguments into the correct location
            CiCallingConvention cc = compilation.registerConfig.getCallingConvention(RuntimeCall, arguments, target(), false);
            compilation.frameMap().adjustOutgoingStackSize(cc, RuntimeCall);
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

        append(StandardOpcode.DIRECT_CALL.create(runtimeCall, physReg, argumentList, null, info, null));

        if (physReg.isLegal()) {
            return emitMove(physReg);
        } else {
            return null;
        }
    }

    @Override
    public void emitRuntimeCall(RuntimeCallNode x) {
        // TODO Merge with emitCallToRuntime() method above.

        CiValue resultOperand = resultOperandFor(x.kind());
        CiCallingConvention cc = compilation.registerConfig.getCallingConvention(RuntimeCall, x.call().arguments, target(), false);
        compilation.frameMap().adjustOutgoingStackSize(cc, RuntimeCall);
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

        append(StandardOpcode.DIRECT_CALL.create(x.call(), resultOperand, argList, null, info, null));

        if (resultOperand.isLegal()) {
            setResult(x, emitMove(resultOperand));
        }
    }

    protected CompilerStub stubFor(CompilerStub.Id id) {
        CompilerStub stub = compilation.compiler.lookupStub(id);
        compilation.frameMap().usesStub(stub);
        return stub;
    }

    protected CompilerStub stubFor(XirTemplate template) {
        CompilerStub stub = compilation.compiler.lookupStub(template);
        compilation.frameMap().usesStub(stub);
        return stub;
    }

    @Override
    public void emitLookupSwitch(LookupSwitchNode x) {
        CiVariable tag = load(operand(x.value()));
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
        CiVariable value = load(operand(x.value()));
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

    private void visitSwitchRanges(SwitchRange[] x, CiVariable value, LabelRef defaultSux) {
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



    private void moveToPhi(MergeNode merge, FixedNode pred) {
        if (GraalOptions.AllocSSA) {
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
    }

    private CiValue operandForPhi(PhiNode phi) {
        assert phi.type() == PhiType.Value : "wrong phi type: " + phi;
        CiValue result = operand(phi);
        if (result == null) {
            // allocate a variable for this phi
            CiVariable newOperand = newVariable(phi.kind());
            setResult(phi, newOperand);
            return newOperand;
        } else {
            return result;
        }
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
        CiVariable variable = load(value);
        if (var.kind == CiKind.Byte || var.kind == CiKind.Boolean) {
            operands.setFlag(variable, VariableFlag.MustBeByteRegister);
        }
        return variable;
    }

    protected CiValue emitXir(XirSnippet snippet, ValueNode x, LIRDebugInfo info, RiMethod method, boolean setInstructionResult) {
        return emitXir(snippet, x, info, null, method, setInstructionResult);
    }

    protected CiValue emitXir(XirSnippet snippet, ValueNode instruction, LIRDebugInfo info, LIRDebugInfo infoAfter, RiMethod method, boolean setInstructionResult) {
        if (GraalOptions.PrintXirTemplates) {
            TTY.println("Emit XIR template " + snippet.template.name);
        }

        final CiValue[] operandsArray = new CiValue[snippet.template.variableCount];

        compilation.frameMap().reserveOutgoing(snippet.template.outgoingStackSize);

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

        for (XirTemplate calleeTemplate : snippet.template.calleeTemplates) {
            // TODO Save these for use in AMD64LIRAssembler
            stubFor(calleeTemplate);
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
        if (!allocatedResultOperand.isVariableOrRegister()) {
            allocatedResultOperand = IllegalValue;
        }

        if (setInstructionResult && allocatedResultOperand.isLegal()) {
            CiValue operand = compilation.operand(instruction);
            if (operand == null) {
                setResult(instruction, allocatedResultOperand);
            } else {
                assert operand == allocatedResultOperand;
            }
        }


        XirInstruction[] slowPath = snippet.template.slowPath;
        if (!operandsArray[resultOperand.index].isConstant() || snippet.template.fastPath.length != 0 || (slowPath != null && slowPath.length > 0)) {
            // XIR instruction is only needed when the operand is not a constant!
            append(StandardOpcode.XIR.create(snippet, operandsArray, allocatedResultOperand,
                    inputOperandArray, tempOperandArray, inputOperandIndicesArray, tempOperandIndicesArray,
                    (operandsArray[resultOperand.index] == IllegalValue) ? -1 : resultOperand.index,
                    info, infoAfter, method));
            if (GraalOptions.Meter) {
                context.metrics.LIRXIRInstructions++;
            }
        }

        return operandsArray[resultOperand.index];
    }

    protected final CiValue callRuntime(CiRuntimeCall runtimeCall, LIRDebugInfo info, CiValue... args) {
        // get a result register
        CiKind result = runtimeCall.resultKind;
        CiKind[] arguments = runtimeCall.arguments;

        CiValue physReg = result.isVoid() ? IllegalValue : resultOperandFor(result);

        List<CiValue> argumentList;
        if (arguments.length > 0) {
            // move the arguments into the correct location
            CiCallingConvention cc = compilation.registerConfig.getCallingConvention(RuntimeCall, arguments, target(), false);
            compilation.frameMap().adjustOutgoingStackSize(cc, RuntimeCall);
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

        append(StandardOpcode.DIRECT_CALL.create(runtimeCall, physReg, argumentList, null, info, null));

        return physReg;
    }

    protected final CiVariable callRuntimeWithResult(CiRuntimeCall runtimeCall, LIRDebugInfo info, CiValue... args) {
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
}
