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
package com.sun.c1x.gen;

import static com.sun.cri.bytecode.Bytecodes.*;
import static com.sun.cri.bytecode.Bytecodes.MemoryBarriers.*;
import static com.sun.cri.ci.CiCallingConvention.Type.*;
import static com.sun.cri.ci.CiValue.*;

import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.alloc.*;
import com.sun.c1x.alloc.OperandPool.VariableFlag;
import com.sun.c1x.asm.*;
import com.sun.c1x.debug.*;
import com.sun.c1x.globalstub.*;
import com.sun.c1x.graph.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.lir.*;
import com.sun.c1x.opt.*;
import com.sun.c1x.util.*;
import com.sun.c1x.value.*;
import com.sun.c1x.value.FrameState.PhiProcedure;
import com.sun.cri.bytecode.Bytecodes.MemoryBarriers;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;
import com.sun.cri.xir.CiXirAssembler.XirConstant;
import com.sun.cri.xir.CiXirAssembler.XirInstruction;
import com.sun.cri.xir.CiXirAssembler.XirOperand;
import com.sun.cri.xir.CiXirAssembler.XirParameter;
import com.sun.cri.xir.CiXirAssembler.XirRegister;
import com.sun.cri.xir.CiXirAssembler.XirTemp;
import com.sun.cri.xir.*;

/**
 * This class traverses the HIR instructions and generates LIR instructions from them.
 *
 * @author Thomas Wuerthinger
 * @author Ben L. Titzer
 * @author Marcelo Cintra
 * @author Doug Simon
 */
public abstract class LIRGenerator extends ValueVisitor {

    /**
     * Helper class for inserting memory barriers as necessary to implement the Java Memory Model
     * with respect to volatile field accesses.
     *
     * @see MemoryBarriers
     */
    class VolatileMemoryAccess {
        /**
         * Inserts any necessary memory barriers before a volatile write as required by the JMM.
         */
        void preVolatileWrite() {
            int barriers = compilation.target.arch.requiredBarriers(JMM_PRE_VOLATILE_WRITE);
            if (compilation.target.isMP && barriers != 0) {
                lir.membar(barriers);
            }
        }

        /**
         * Inserts any necessary memory barriers after a volatile write as required by the JMM.
         */
        void postVolatileWrite() {
            int barriers = compilation.target.arch.requiredBarriers(JMM_POST_VOLATILE_WRITE);
            if (compilation.target.isMP && barriers != 0) {
                lir.membar(barriers);
            }
        }

        /**
         * Inserts any necessary memory barriers before a volatile read as required by the JMM.
         */
        void preVolatileRead() {
            int barriers = compilation.target.arch.requiredBarriers(JMM_PRE_VOLATILE_READ);
            if (compilation.target.isMP && barriers != 0) {
                lir.membar(barriers);
            }
        }

        /**
         * Inserts any necessary memory barriers after a volatile read as required by the JMM.
         */
        void postVolatileRead() {
            // Ensure field's data is loaded before any subsequent loads or stores.
            int barriers = compilation.target.arch.requiredBarriers(LOAD_LOAD | LOAD_STORE);
            if (compilation.target.isMP && barriers != 0) {
                lir.membar(barriers);
            }
        }
    }

    /**
     * Forces the result of a given instruction to be available in a given register,
     * inserting move instructions if necessary.
     *
     * @param instruction an instruction that produces a {@linkplain Value#operand() result}
     * @param register the {@linkplain CiRegister} in which the result of {@code instruction} must be available
     * @return {@code register} as an operand
     */
    protected CiValue force(Value instruction, CiRegister register) {
        return force(instruction, register.asValue(instruction.kind));
    }

    /**
     * Forces the result of a given instruction to be available in a given operand,
     * inserting move instructions if necessary.
     *
     * @param instruction an instruction that produces a {@linkplain Value#operand() result}
     * @param operand the operand in which the result of {@code instruction} must be available
     * @return {@code operand}
     */
    protected CiValue force(Value instruction, CiValue operand) {
        CiValue result = makeOperand(instruction);
        if (result != operand) {
            assert result.kind != CiKind.Illegal;
            if (!compilation.archKindsEqual(result.kind, operand.kind)) {
                // moves between different types need an intervening spill slot
                CiValue tmp = forceToSpill(result, operand.kind, false);
                lir.move(tmp, operand);
            } else {
                lir.move(result, operand);
            }
        }
        return operand;
    }

    protected CiValue load(Value val) {
        CiValue result = makeOperand(val);
        if (!result.isVariableOrRegister()) {
            CiVariable operand = newVariable(val.kind);
            lir.move(result, operand);
            return operand;
        }
        return result;
    }

    // the range of values in a lookupswitch or tableswitch statement
    private static final class SwitchRange {
        final int lowKey;
        int highKey;
        final BlockBegin sux;

        SwitchRange(int lowKey, BlockBegin sux) {
            this.lowKey = lowKey;
            this.highKey = lowKey;
            this.sux = sux;
        }
    }

    protected final C1XCompilation compilation;
    protected final IR ir;
    protected final XirSupport xirSupport;
    protected final RiXirGenerator xir;
    protected final boolean isTwoOperand;

    private BlockBegin currentBlock;

    public final OperandPool operands;

    private Value currentInstruction;
    private Value lastInstructionPrinted; // Debugging only

    private List<CiConstant> constants;
    private List<CiVariable> variablesForConstants;
    protected LIRList lir;
    final VolatileMemoryAccess vma;
    private ArrayList<DeoptimizationStub> deoptimizationStubs;
    private FrameState lastState;

    public LIRGenerator(C1XCompilation compilation) {
        this.compilation = compilation;
        this.ir = compilation.hir();
        this.xir = compilation.compiler.xir;
        this.xirSupport = new XirSupport();
        this.isTwoOperand = compilation.target.arch.twoOperandMode();
        this.vma = new VolatileMemoryAccess();

        constants = new ArrayList<CiConstant>();
        variablesForConstants = new ArrayList<CiVariable>();

        this.operands = new OperandPool(compilation.target);

        new PhiSimplifier(ir);
    }

    public ArrayList<DeoptimizationStub> deoptimizationStubs() {
        return deoptimizationStubs;
    }

    public static class DeoptimizationStub {
        public final Label label = new Label();
        public final LIRDebugInfo info;

        public DeoptimizationStub(FrameState state) {
            info = new LIRDebugInfo(state, null);
        }
    }

    public void doBlock(BlockBegin block) {
        blockDoProlog(block);
        this.currentBlock = block;

        if (C1XOptions.TraceLIRGeneratorLevel >= 1) {
            TTY.println("BEGIN Generating LIR for block B" + block.blockID);
        }

        for (Instruction instr = block; instr != null; instr = instr.next()) {
            FrameState stateAfter = instr.stateAfter();
            FrameState stateBefore = null;
            if (instr instanceof StateSplit && ((StateSplit) instr).stateBefore() != null) {
                stateBefore = ((StateSplit) instr).stateBefore();
            }
            if (stateBefore != null) {
                lastState = stateBefore;
                if (C1XOptions.TraceLIRGeneratorLevel >= 2) {
                    TTY.println("STATE CHANGE (stateBefore)");
                    if (C1XOptions.TraceLIRGeneratorLevel >= 3) {
                        TTY.println(stateBefore.toString());
                    }
                }
            }
            if (!(instr instanceof BlockBegin)) {
                walkState(instr, stateAfter);
                doRoot(instr);
            }
            if (stateAfter != null) {
                lastState = stateAfter;
                if (C1XOptions.TraceLIRGeneratorLevel >= 2) {
                    TTY.println("STATE CHANGE");
                    if (C1XOptions.TraceLIRGeneratorLevel >= 3) {
                        TTY.println(stateAfter.toString());
                    }
                }
            }
        }

        if (C1XOptions.TraceLIRGeneratorLevel >= 1) {
            TTY.println("END Generating LIR for block B" + block.blockID);
        }

        this.currentBlock = null;
        blockDoEpilog(block);
    }

    @Override
    public void visitArrayLength(ArrayLength x) {
        emitArrayLength(x);
    }

    public CiValue emitArrayLength(ArrayLength x) {
        XirArgument array = toXirArgument(x.array());
        XirSnippet snippet = xir.genArrayLength(site(x), array);
        emitXir(snippet, x, stateFor(x), null, true);
        return x.operand();
    }

    private void setOperandsForLocals(FrameState state) {
        CiCallingConvention args = compilation.frameMap().incomingArguments();
        int javaIndex = 0;
        for (int i = 0; i < args.locations.length; i++) {
            CiValue src = args.locations[i];
            assert src.isLegal() : "check";

            CiVariable dest = newVariable(src.kind.stackKind());
            lir.move(src, dest, src.kind);

            // Assign new location to Local instruction for this local
            Value instr = state.localAt(javaIndex);
            Local local = ((Local) instr);
            CiKind kind = src.kind.stackKind();
            assert kind == local.kind.stackKind() : "local type check failed";
            setResult(local, dest);
            javaIndex += kind.jvmSlots;
        }
    }

    @Override
    public void visitResolveClass(ResolveClass i) {
        LIRDebugInfo info = stateFor(i);
        XirSnippet snippet = xir.genResolveClass(site(i), i.type, i.portion);
        emitXir(snippet, i, info, null, true);
    }

    @Override
    public void visitCheckCast(CheckCast x) {
        XirArgument obj = toXirArgument(x.object());
        XirSnippet snippet = xir.genCheckCast(site(x), obj, toXirArgument(x.targetClassInstruction()), x.targetClass());
        emitXir(snippet, x, stateFor(x), null, true);
    }

    @Override
    public void visitInstanceOf(InstanceOf x) {
        XirArgument obj = toXirArgument(x.object());
        XirSnippet snippet = xir.genInstanceOf(site(x), obj, toXirArgument(x.targetClassInstruction()), x.targetClass());
        emitXir(snippet, x, stateFor(x), null, true);
    }

    @Override
    public void visitMonitorEnter(MonitorEnter x) {
        XirArgument obj = toXirArgument(x.object());
        XirArgument lockAddress = toXirArgument(x.lockAddress());
        XirSnippet snippet = xir.genMonitorEnter(site(x), obj, lockAddress);
        emitXir(snippet, x, stateFor(x), stateFor(x, x.stateAfter()), null, true, null);
    }

    @Override
    public void visitMonitorExit(MonitorExit x) {
        XirArgument obj = toXirArgument(x.object());
        XirArgument lockAddress = toXirArgument(x.lockAddress());
        XirSnippet snippet = xir.genMonitorExit(site(x), obj, lockAddress);
        emitXir(snippet, x, stateFor(x), null, true);
    }

    @Override
    public void visitStoreIndexed(StoreIndexed x) {
        XirArgument array = toXirArgument(x.array());
        XirArgument length = x.length() == null ? null : toXirArgument(x.length());
        XirArgument index = toXirArgument(x.index());
        XirArgument value = toXirArgument(x.value());
        XirSnippet snippet = xir.genArrayStore(site(x), array, index, length, value, x.elementKind(), null);
        emitXir(snippet, x, stateFor(x), null, true);
    }

    @Override
    public void visitNewInstance(NewInstance x) {
        XirSnippet snippet = xir.genNewInstance(site(x), x.instanceClass());
        emitXir(snippet, x, stateFor(x), null, true);
    }

    @Override
    public void visitNewTypeArray(NewTypeArray x) {
        XirArgument length = toXirArgument(x.length());
        XirSnippet snippet = xir.genNewArray(site(x), length, x.elementKind(), null, null);
        emitXir(snippet, x, stateFor(x), null, true);
    }

    @Override
    public void visitNewObjectArray(NewObjectArray x) {
        XirArgument length = toXirArgument(x.length());
        XirSnippet snippet = xir.genNewArray(site(x), length, CiKind.Object, x.elementClass(), x.exactType());
        emitXir(snippet, x, stateFor(x), null, true);
    }

    @Override
    public void visitNewMultiArray(NewMultiArray x) {
        XirArgument[] dims = new XirArgument[x.dimensionCount()];

        for (int i = 0; i < dims.length; i++) {
            dims[i] = toXirArgument(x.dimension(i));
        }

        XirSnippet snippet = xir.genNewMultiArray(site(x), dims, x.elementKind);
        emitXir(snippet, x, stateFor(x), null, true);
    }

    @Override
    public void visitConstant(Constant x) {
        if (!canInlineAsConstant(x)) {
            CiValue res = x.operand();
            if (!(res.isLegal())) {
                res = x.asConstant();
            }
            if (res.isConstant()) {
                CiVariable reg = createResultVariable(x);
                lir.move(res, reg);
            } else {
                setResult(x, (CiVariable) res);
            }
        }
    }

    @Override
    public void visitExceptionObject(ExceptionObject x) {
        assert currentBlock.isExceptionEntry() : "ExceptionObject only allowed in exception handler block";
        assert currentBlock.next() == x : "ExceptionObject must be first instruction of block";

        // no moves are created for phi functions at the begin of exception
        // handlers, so assign operands manually here
        currentBlock.stateBefore().forEachLivePhi(currentBlock, new PhiProcedure() {
            public boolean doPhi(Phi phi) {
                operandForPhi(phi);
                return true;
            }
        });

        XirSnippet snippet = xir.genExceptionObject(site(x));
        emitXir(snippet, x, stateFor(x), null, true);
    }

    @Override
    public void visitGoto(Goto x) {
        setNoResult(x);

        // emit phi-instruction moves after safepoint since this simplifies
        // describing the state at the safepoint.
        moveToPhi(x.stateAfter());

        lir.jump(x.defaultSuccessor());
    }

    @Override
    public void visitIfOp(IfOp i) {
        Value x = i.x();
        Value y = i.y();
        CiKind xtype = x.kind;
        CiKind ttype = i.trueValue().kind;
        assert xtype.isInt() || xtype.isObject() : "cannot handle others";
        assert ttype.isInt() || ttype.isObject() || ttype.isLong() || ttype.isWord() : "cannot handle others";
        assert ttype.equals(i.falseValue().kind) : "cannot handle others";

        CiValue left = load(x);
        CiValue right = null;
        if (!canInlineAsConstant(y)) {
            right = load(y);
        } else {
            right = makeOperand(y);
        }

        CiValue tVal = makeOperand(i.trueValue());
        CiValue fVal = makeOperand(i.falseValue());
        CiValue reg = createResultVariable(i);

        lir.cmp(i.condition(), left, right);
        lir.cmove(i.condition(), tVal, fVal, reg);
    }

    protected FrameState stateBeforeInvoke(Invoke invoke) {
        Value[] args = new Value[invoke.argumentCount()];
        for (int i = 0; i < invoke.argumentCount(); i++) {
            args[i] = invoke.argument(i);
        }
        return invoke.stateAfter().duplicateModified(invoke.bci(), invoke.kind/*, args*/);
    }

    protected FrameState stateBeforeInvokeWithArguments(Invoke invoke) {
        Value[] args = new Value[invoke.argumentCount()];
        for (int i = 0; i < invoke.argumentCount(); i++) {
            args[i] = invoke.argument(i);
        }
        return invoke.stateAfter().duplicateModified(invoke.bci(), invoke.kind, args);
    }

    @Override
    public void visitInvoke(Invoke x) {
        RiMethod target = x.target();
        LIRDebugInfo info = stateFor(x, stateBeforeInvokeWithArguments(x));
        LIRDebugInfo info2 = stateFor(x, stateBeforeInvoke(x));

        XirSnippet snippet = null;

        int opcode = x.opcode();
        XirArgument receiver;
        switch (opcode) {
            case INVOKESTATIC:
                snippet = xir.genInvokeStatic(site(x), target);
                break;
            case INVOKESPECIAL:
                receiver = toXirArgument(x.receiver());
                snippet = xir.genInvokeSpecial(site(x), receiver, target);
                break;
            case INVOKEVIRTUAL:
                receiver = toXirArgument(x.receiver());
                snippet = xir.genInvokeVirtual(site(x), receiver, target);
                break;
            case INVOKEINTERFACE:
                receiver = toXirArgument(x.receiver());
                snippet = xir.genInvokeInterface(site(x), receiver, target);
                break;
        }

        CiValue destinationAddress = null;
        // emitting the template earlier can ease pressure on register allocation, but the argument loading can destroy an
        // implicit calling convention between the XirSnippet and the call.
        if (!C1XOptions.InvokeSnippetAfterArguments) {
            destinationAddress = emitXir(snippet, x, info.copy(), x.target(), false);
        }

        CiValue resultOperand = resultOperandFor(x.kind);
        CiCallingConvention cc = compilation.frameMap().getCallingConvention(x.signature(), JavaCall);
        List<CiValue> pointerSlots = new ArrayList<CiValue>(2);
        List<CiValue> argList = visitInvokeArguments(cc, x, pointerSlots);

        if (C1XOptions.InvokeSnippetAfterArguments) {
            destinationAddress = emitXir(snippet, x, info.copy(), null, x.target(), false, pointerSlots);
        }

        // emit direct or indirect call to the destination address
        if (destinationAddress instanceof CiConstant) {
            // Direct call
            assert ((CiConstant) destinationAddress).isDefaultValue() : "destination address should be zero";
            lir.callDirect(target, resultOperand, argList, info2, snippet.marks, pointerSlots);
        } else {
            // Indirect call
            argList.add(destinationAddress);
            lir.callIndirect(target, resultOperand, argList, info2, snippet.marks, pointerSlots);
        }

        if (resultOperand.isLegal()) {
            CiValue result = createResultVariable(x);
            lir.move(resultOperand, result);
        }
    }

    @Override
    public void visitMonitorAddress(MonitorAddress x) {
        CiValue result = createResultVariable(x);
        lir.monitorAddress(x.monitor(), result);
    }

    /**
     * For note on volatile fields, see {@link #visitStoreField(StoreField)}.
     */
    @Override
    public void visitLoadField(LoadField x) {
        RiField field = x.field();
        LIRDebugInfo info = stateFor(x);
        XirArgument receiver = toXirArgument(x.object());
        XirSnippet snippet = x.isStatic() ? xir.genGetStatic(site(x), receiver, field) : xir.genGetField(site(x), receiver, field);
        emitXir(snippet, x, info, null, true);

        if (x.isVolatile()) {
            vma.postVolatileRead();
        }
    }

    @Override
    public void visitLoadIndexed(LoadIndexed x) {
        XirArgument array = toXirArgument(x.array());
        XirArgument index = toXirArgument(x.index());
        XirArgument length = toXirArgument(x.length());
        XirSnippet snippet = xir.genArrayLoad(site(x), array, index, length, x.elementKind(), null);
        emitXir(snippet, x, stateFor(x), null, true);
    }

    protected GlobalStub stubFor(CiRuntimeCall runtimeCall) {
        GlobalStub stub = compilation.compiler.lookupGlobalStub(runtimeCall);
        compilation.frameMap().usesGlobalStub(stub);
        return stub;
    }

    protected GlobalStub stubFor(GlobalStub.Id globalStub) {
        GlobalStub stub = compilation.compiler.lookupGlobalStub(globalStub);
        compilation.frameMap().usesGlobalStub(stub);
        return stub;
    }

    protected GlobalStub stubFor(XirTemplate template) {
        GlobalStub stub = compilation.compiler.lookupGlobalStub(template);
        compilation.frameMap().usesGlobalStub(stub);
        return stub;
    }

    @Override
    public void visitLocal(Local x) {
        if (x.operand().isIllegal()) {
            createResultVariable(x);
        }
    }

    @Override
    public void visitLookupSwitch(LookupSwitch x) {
        CiValue tag = load(x.value());
        setNoResult(x);

        if (x.isSafepoint()) {
            emitXir(xir.genSafepoint(site(x)), x, stateFor(x), null, false);
        }

        // move values into phi locations
        moveToPhi(x.stateAfter());

        if (x.numberOfCases() == 0 || x.numberOfCases() < C1XOptions.SequentialSwitchLimit) {
            int len = x.numberOfCases();
            for (int i = 0; i < len; i++) {
                lir.cmp(Condition.EQ, tag, x.keyAt(i));
                lir.branch(Condition.EQ, CiKind.Int, x.blockSuccessor(i));
            }
            lir.jump(x.defaultSuccessor());
        } else {
            visitSwitchRanges(createLookupRanges(x), tag, x.defaultSuccessor());
        }
    }

    @Override
    public void visitNullCheck(NullCheck x) {
        CiValue value = load(x.object());
        LIRDebugInfo info = stateFor(x);
        lir.nullCheck(value, info);
    }

    @Override
    public void visitPhi(Phi i) {
        Util.shouldNotReachHere();
    }

    @Override
    public void visitReturn(Return x) {
        if (x.kind.isVoid()) {
            XirSnippet epilogue = xir.genEpilogue(site(x), compilation.method);
            if (epilogue != null) {
                emitXir(epilogue, x, stateFor(x), compilation.method, false);
                lir.returnOp(IllegalValue);
            }
        } else {
            CiValue operand = resultOperandFor(x.kind);
            CiValue result = force(x.result(), operand);
            XirSnippet epilogue = xir.genEpilogue(site(x), compilation.method);
            if (epilogue != null) {
                emitXir(epilogue, x, stateFor(x, x.stateAfter()), compilation.method, false);
                lir.returnOp(result);
            }
        }
        setNoResult(x);
    }

    protected XirArgument toXirArgument(CiValue v) {
        if (v == null) {
            return null;
        }

        return XirArgument.forInternalObject(v);
    }

    protected XirArgument toXirArgument(Value i) {
        if (i == null) {
            return null;
        }

        return XirArgument.forInternalObject(new LIRItem(i, this));
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
        } else {
            assert arg.object != null;
            if (arg.object instanceof CiValue) {
                return (CiValue) arg.object;
            }
            assert arg.object instanceof LIRItem;
            LIRItem item = (LIRItem) arg.object;
            if (canBeConstant) {
                return item.instruction.operand();
            } else {
                CiKind kind = var.kind;
                if (kind == CiKind.Byte || kind == CiKind.Boolean) {
                    item.loadByteItem();
                } else {
                    item.loadItem();
                }
                return item.result();
            }
        }
    }

    protected CiValue emitXir(XirSnippet snippet, Instruction x, LIRDebugInfo info, RiMethod method, boolean setInstructionResult) {
        return emitXir(snippet, x, info, null, method, setInstructionResult, null);
    }

    protected CiValue emitXir(XirSnippet snippet, Instruction instruction, LIRDebugInfo info, LIRDebugInfo infoAfter, RiMethod method, boolean setInstructionResult, List<CiValue> pointerSlots) {
        if (C1XOptions.PrintXirTemplates) {
            TTY.println("Emit XIR template " + snippet.template.name);
        }

        final CiValue[] operands = new CiValue[snippet.template.variableCount];

        compilation.frameMap().reserveOutgoing(snippet.template.outgoingStackSize);

        XirOperand resultOperand = snippet.template.resultOperand;

        if (snippet.template.allocateResultOperand) {
            CiValue outputOperand = IllegalValue;
            // This snippet has a result that must be separately allocated
            // Otherwise it is assumed that the result is part of the inputs
            if (resultOperand.kind != CiKind.Void && resultOperand.kind != CiKind.Illegal) {
                if (setInstructionResult) {
                    outputOperand = newVariable(instruction.kind);
                } else {
                    outputOperand = newVariable(resultOperand.kind);
                }
                assert operands[resultOperand.index] == null;
            }
            operands[resultOperand.index] = outputOperand;
            if (C1XOptions.PrintXirTemplates) {
                TTY.println("Output operand: " + outputOperand);
            }
        }

        for (XirTemp t : snippet.template.temps) {
            if (t instanceof XirRegister) {
                XirRegister reg = (XirRegister) t;
                if (!t.reserve) {
                    operands[t.index] = reg.register;
                }
            }
        }

        for (XirTemplate calleeTemplate : snippet.template.calleeTemplates) {
            // TODO Save these for use in X86LIRAssembler
            stubFor(calleeTemplate);
        }

        for (XirConstant c : snippet.template.constants) {
            assert operands[c.index] == null;
            operands[c.index] = c.value;
        }

        XirOperand[] inputOperands = snippet.template.inputOperands;
        XirOperand[] inputTempOperands = snippet.template.inputTempOperands;
        XirOperand[] tempOperands = snippet.template.tempOperands;

        CiValue[] operandArray = new CiValue[inputOperands.length + inputTempOperands.length + tempOperands.length];
        int[] operandIndicesArray = new int[inputOperands.length + inputTempOperands.length + tempOperands.length];
        for (int i = 0; i < inputOperands.length; i++) {
            XirOperand x = inputOperands[i];
            CiValue op = allocateOperand(snippet, x);
            operands[x.index] = op;
            operandArray[i] = op;
            operandIndicesArray[i] = x.index;
            if (C1XOptions.PrintXirTemplates) {
                TTY.println("Input operand: " + x);
            }
        }

        for (int i = 0; i < inputTempOperands.length; i++) {
            XirOperand x = inputTempOperands[i];
            CiValue op = allocateOperand(snippet, x);
            CiValue newOp = newVariable(op.kind);
            lir.move(op, newOp);
            operands[x.index] = newOp;
            operandArray[i + inputOperands.length] = newOp;
            operandIndicesArray[i + inputOperands.length] = x.index;
            if (C1XOptions.PrintXirTemplates) {
                TTY.println("InputTemp operand: " + x);
            }
        }

        for (int i = 0; i < tempOperands.length; i++) {
            XirOperand x = tempOperands[i];
            CiValue op = allocateOperand(snippet, x);
            operands[x.index] = op;
            operandArray[i + inputOperands.length + inputTempOperands.length] = op;
            operandIndicesArray[i + inputOperands.length + inputTempOperands.length] = x.index;
            if (C1XOptions.PrintXirTemplates) {
                TTY.println("Temp operand: " + x);
            }
        }

        for (CiValue operand : operands) {
            assert operand != null;
        }

        CiValue allocatedResultOperand = operands[resultOperand.index];
        if (!allocatedResultOperand.isVariableOrRegister()) {
            allocatedResultOperand = IllegalValue;
        }

        if (setInstructionResult && allocatedResultOperand.isLegal()) {
            if (instruction.operand().isIllegal()) {
                setResult(instruction, (CiVariable) allocatedResultOperand);
            } else {
                assert instruction.operand() == allocatedResultOperand;
            }
        }


        XirInstruction[] slowPath = snippet.template.slowPath;
        if (!operands[resultOperand.index].isConstant() || snippet.template.fastPath.length != 0 || (slowPath != null && slowPath.length > 0)) {
            // XIR instruction is only needed when the operand is not a constant!
            lir.xir(snippet, operands, allocatedResultOperand, inputTempOperands.length, tempOperands.length,
                    operandArray, operandIndicesArray,
                    (operands[resultOperand.index] == IllegalValue) ? -1 : resultOperand.index,
                    info, infoAfter, method, pointerSlots);
        }

        return operands[resultOperand.index];
    }

    @Override
    public void visitStoreField(StoreField x) {
        RiField field = x.field();
        LIRDebugInfo info = stateFor(x);

        if (x.isVolatile()) {
            vma.preVolatileWrite();
        }

        XirArgument receiver = toXirArgument(x.object());
        XirArgument value = toXirArgument(x.value());
        XirSnippet snippet = x.isStatic() ? xir.genPutStatic(site(x), receiver, field, value) : xir.genPutField(site(x), receiver, field, value);
        emitXir(snippet, x, info, null, true);

        if (x.isVolatile()) {
            vma.postVolatileWrite();
        }
    }

    @Override
    public void visitTableSwitch(TableSwitch x) {

        LIRItem value = new LIRItem(x.value(), this);
        // Making a copy of the switch value is necessary when generating a jump table
        value.setDestroysRegister();
        value.loadItem();

        CiValue tag = value.result();
        setNoResult(x);

        if (x.isSafepoint()) {
            emitXir(xir.genSafepoint(site(x)), x, stateFor(x), null, false);
        }

        // move values into phi locations
        moveToPhi(x.stateAfter());

        // TODO: tune the defaults for the controls used to determine what kind of translation to use
        if (x.numberOfCases() == 0 || x.numberOfCases() <= C1XOptions.SequentialSwitchLimit) {
            int loKey = x.lowKey();
            int len = x.numberOfCases();
            for (int i = 0; i < len; i++) {
                lir.cmp(Condition.EQ, tag, i + loKey);
                lir.branch(Condition.EQ, CiKind.Int, x.blockSuccessor(i));
            }
            lir.jump(x.defaultSuccessor());
        } else {
            SwitchRange[] switchRanges = createLookupRanges(x);
            int rangeDensity = x.numberOfCases() / switchRanges.length;
            if (rangeDensity >= C1XOptions.RangeTestsSwitchDensity) {
                visitSwitchRanges(switchRanges, tag, x.defaultSuccessor());
            } else {
                List<BlockBegin> nonDefaultSuccessors = x.blockSuccessors().subList(0, x.numberOfCases());
                BlockBegin[] targets = nonDefaultSuccessors.toArray(new BlockBegin[nonDefaultSuccessors.size()]);
                lir.tableswitch(tag, x.lowKey(), x.defaultSuccessor(), targets);
            }
        }
    }

    @Override
    public void visitThrow(Throw x) {
        setNoResult(x);
        CiValue exceptionOpr = load(x.exception());
        LIRDebugInfo info = stateFor(x, x.stateBefore());

        // move exception oop into fixed register
        CiCallingConvention callingConvention = compilation.frameMap().getCallingConvention(new CiKind[]{CiKind.Object}, RuntimeCall);
        CiValue argumentOperand = callingConvention.locations[0];
        lir.move(exceptionOpr, argumentOperand);

        lir.throwException(CiValue.IllegalValue, argumentOperand, info);
    }

    private void blockDoEpilog(BlockBegin block) {
        if (C1XOptions.PrintIRWithLIR) {
            TTY.println();
        }

        // clear out variables for local constants
        constants.clear();
        variablesForConstants.clear();
    }

    private void blockDoProlog(BlockBegin block) {
        if (C1XOptions.PrintIRWithLIR) {
            TTY.print(block.toString());
        }
        // set up the list of LIR instructions
        assert block.lir() == null : "LIR list already computed for this block";
        lir = new LIRList(this);
        block.setLir(lir);

        lir.branchDestination(block.label());
        if (block == ir.startBlock) {
            XirSnippet prologue = xir.genPrologue(null, compilation.method);
            if (prologue != null) {
                emitXir(prologue, null, null, null, false);
            }
            setOperandsForLocals(block.end().stateAfter());
        }
    }

    /**
     * Copies a given value into an operand that is forced to be a stack location.
     *
     * @param value a value to be forced onto the stack
     * @param kind the kind of new operand
     * @param mustStayOnStack specifies if the new operand must never be allocated to a register
     * @return the operand that is guaranteed to be a stack location when it is
     *         initially defined a by move from {@code value}
     */
    CiValue forceToSpill(CiValue value, CiKind kind, boolean mustStayOnStack) {
        assert value.isLegal() : "value should not be illegal";
        assert kind.jvmSlots == value.kind.jvmSlots : "size mismatch";
        if (!value.isVariableOrRegister()) {
            // force into a variable that must start in memory
            CiValue operand = operands.newVariable(value.kind, mustStayOnStack ? VariableFlag.MustStayInMemory : VariableFlag.MustStartInMemory);
            lir.move(value, operand);
            return operand;
        }

        // create a spill location
        CiValue operand = operands.newVariable(kind, mustStayOnStack ? VariableFlag.MustStayInMemory : VariableFlag.MustStartInMemory);
        // move from register to spill
        lir.move(value, operand);
        return operand;
    }

    private CiVariable loadConstant(Constant x) {
        return loadConstant(x.asConstant(), x.kind);
    }

    protected CiVariable loadConstant(CiConstant c, CiKind kind) {
        // XXX: linear search might be kind of slow for big basic blocks
        int index = constants.indexOf(c);
        if (index != -1) {
            C1XMetrics.LoadConstantIterations += index;
            return variablesForConstants.get(index);
        }
        C1XMetrics.LoadConstantIterations += constants.size();

        CiVariable result = newVariable(kind);
        lir.move(c, result);
        constants.add(c);
        variablesForConstants.add(result);
        return result;
    }

    /**
     * Allocates a variable operand to hold the result of a given instruction.
     * This can only be performed once for any given instruction.
     *
     * @param x an instruction that produces a result
     * @return the variable assigned to hold the result produced by {@code x}
     */
    protected CiVariable createResultVariable(Value x) {
        CiVariable operand = newVariable(x.kind);
        setResult(x, operand);
        return operand;
    }

    @Override
    public void visitRegisterFinalizer(RegisterFinalizer x) {
        CiValue receiver = load(x.object());
        LIRDebugInfo info = stateFor(x, x.stateBefore());
        callRuntime(CiRuntimeCall.RegisterFinalizer, info, receiver);
        setNoResult(x);
    }

    private void visitSwitchRanges(SwitchRange[] x, CiValue value, BlockBegin defaultSux) {
        for (int i = 0; i < x.length; i++) {
            SwitchRange oneRange = x[i];
            int lowKey = oneRange.lowKey;
            int highKey = oneRange.highKey;
            BlockBegin dest = oneRange.sux;
            if (lowKey == highKey) {
                lir.cmp(Condition.EQ, value, lowKey);
                lir.branch(Condition.EQ, CiKind.Int, dest);
            } else if (highKey - lowKey == 1) {
                lir.cmp(Condition.EQ, value, lowKey);
                lir.branch(Condition.EQ, CiKind.Int, dest);
                lir.cmp(Condition.EQ, value, highKey);
                lir.branch(Condition.EQ, CiKind.Int, dest);
            } else {
                Label l = new Label();
                lir.cmp(Condition.LT, value, lowKey);
                lir.branch(Condition.LT, l);
                lir.cmp(Condition.LE, value, highKey);
                lir.branch(Condition.LE, CiKind.Int, dest);
                lir.branchDestination(l);
            }
        }
        lir.jump(defaultSux);
    }

    protected void arithmeticOpFpu(int code, CiValue result, CiValue left, CiValue right, CiValue tmp) {
        CiValue leftOp = left;

        if (isTwoOperand && leftOp != result) {
            assert right != result : "malformed";
            lir.move(leftOp, result);
            leftOp = result;
        }

        switch (code) {
            case DADD:
            case FADD:
                lir.add(leftOp, right, result);
                break;
            case FMUL:
            case DMUL:
                lir.mul(leftOp, right, result);
                break;
            case DSUB:
            case FSUB:
                lir.sub(leftOp, right, result);
                break;
            case FDIV:
            case DDIV:
                lir.div(leftOp, right, result, null);
                break;
            default:
                Util.shouldNotReachHere();
        }
    }

    protected void arithmeticOpInt(int code, CiValue result, CiValue left, CiValue right, CiValue tmp) {
        CiValue leftOp = left;

        if (isTwoOperand && leftOp != result) {
            assert right != result : "malformed";
            lir.move(leftOp, result);
            leftOp = result;
        }

        switch (code) {
            case IADD:
                lir.add(leftOp, right, result);
                break;
            case IMUL:
                boolean didStrengthReduce = false;
                if (right.isConstant()) {
                    CiConstant rightConstant = (CiConstant) right;
                    int c = rightConstant.asInt();
                    if (CiUtil.isPowerOf2(c)) {
                        // do not need tmp here
                        lir.shiftLeft(leftOp, CiUtil.log2(c), result);
                        didStrengthReduce = true;
                    } else {
                        didStrengthReduce = strengthReduceMultiply(leftOp, c, result, tmp);
                    }
                }
                // we couldn't strength reduce so just emit the multiply
                if (!didStrengthReduce) {
                    lir.mul(leftOp, right, result);
                }
                break;
            case ISUB:
                lir.sub(leftOp, right, result);
                break;
            default:
                // idiv and irem are handled elsewhere
                Util.shouldNotReachHere();
        }
    }

    protected void arithmeticOpLong(int code, CiValue result, CiValue left, CiValue right, LIRDebugInfo info) {
        CiValue leftOp = left;

        if (isTwoOperand && leftOp != result) {
            assert right != result : "malformed";
            lir.move(leftOp, result);
            leftOp = result;
        }

        switch (code) {
            case LADD:
                lir.add(leftOp, right, result);
                break;
            case LMUL:
                lir.mul(leftOp, right, result);
                break;
            case LSUB:
                lir.sub(leftOp, right, result);
                break;
            default:
                // ldiv and lrem are handled elsewhere
                Util.shouldNotReachHere();
        }
    }

    protected final CiValue callRuntime(CiRuntimeCall runtimeCall, LIRDebugInfo info, CiValue... args) {
        // get a result register
        CiKind result = runtimeCall.resultKind;
        CiKind[] arguments = runtimeCall.arguments;

        CiValue physReg = result.isVoid() ? IllegalValue : resultOperandFor(result);

        List<CiValue> argumentList;
        if (arguments.length > 0) {
            // move the arguments into the correct location
            CiCallingConvention cc = compilation.frameMap().getCallingConvention(arguments, RuntimeCall);
            assert cc.locations.length == args.length : "argument count mismatch";
            for (int i = 0; i < args.length; i++) {
                CiValue arg = args[i];
                CiValue loc = cc.locations[i];
                if (loc.isRegister()) {
                    lir.move(arg, loc);
                } else {
                    assert loc.isStackSlot();
                    CiStackSlot slot = (CiStackSlot) loc;
                    if (slot.kind == CiKind.Long || slot.kind == CiKind.Double) {
                        lir.unalignedMove(arg, slot);
                    } else {
                        lir.move(arg, slot);
                    }
                }
            }
            argumentList = Arrays.asList(cc.locations);
        } else {
            // no arguments
            assert args == null || args.length == 0;
            argumentList = Util.uncheckedCast(Collections.emptyList());
        }

        lir.callRuntime(runtimeCall, physReg, argumentList, info);

        return physReg;
    }

    protected final CiVariable callRuntimeWithResult(CiRuntimeCall runtimeCall, LIRDebugInfo info, CiValue... args) {
        CiVariable result = newVariable(runtimeCall.resultKind);
        CiValue location = callRuntime(runtimeCall, info, args);
        lir.move(location, result);
        return result;
    }

    SwitchRange[] createLookupRanges(LookupSwitch x) {
        // we expect the keys to be sorted by increasing value
        List<SwitchRange> res = new ArrayList<SwitchRange>(x.numberOfCases());
        int len = x.numberOfCases();
        if (len > 0) {
            BlockBegin defaultSux = x.defaultSuccessor();
            int key = x.keyAt(0);
            BlockBegin sux = x.blockSuccessor(0);
            SwitchRange range = new SwitchRange(key, sux);
            for (int i = 1; i < len; i++) {
                int newKey = x.keyAt(i);
                BlockBegin newSux = x.blockSuccessor(i);
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

    SwitchRange[] createLookupRanges(TableSwitch x) {
        // XXX: try to merge this with the code for LookupSwitch
        List<SwitchRange> res = new ArrayList<SwitchRange>(x.numberOfCases());
        int len = x.numberOfCases();
        if (len > 0) {
            BlockBegin sux = x.blockSuccessor(0);
            int key = x.lowKey();
            BlockBegin defaultSux = x.defaultSuccessor();
            SwitchRange range = new SwitchRange(key, sux);
            for (int i = 0; i < len; i++, key++) {
                BlockBegin newSux = x.blockSuccessor(i);
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

    void doRoot(Instruction instr) {
        if (C1XOptions.TraceLIRGeneratorLevel >= 2) {
            TTY.println("Emitting LIR for instruction " + instr.toString());
        }
        currentInstruction = instr;
        assert !instr.hasSubst() : "shouldn't have missed substitution";

        if (C1XOptions.TraceLIRVisit) {
            TTY.println("Visiting    " + instr);
        }
        instr.accept(this);
        if (C1XOptions.TraceLIRVisit) {
            TTY.println("Operand for " + instr + " = " + instr.operand());
        }
    }

    protected void logicOp(int code, CiValue resultOp, CiValue leftOp, CiValue rightOp) {
        if (isTwoOperand && leftOp != resultOp) {
            assert rightOp != resultOp : "malformed";
            lir.move(leftOp, resultOp);
            leftOp = resultOp;
        }

        switch (code) {
            case IAND:
            case LAND:
                lir.logicalAnd(leftOp, rightOp, resultOp);
                break;

            case IOR:
            case LOR:
                lir.logicalOr(leftOp, rightOp, resultOp);
                break;

            case IXOR:
            case LXOR:
                lir.logicalXor(leftOp, rightOp, resultOp);
                break;

            default:
                Util.shouldNotReachHere();
        }
    }

    void moveToPhi(PhiResolver resolver, Value curVal, Value suxVal) {
        // move current value to referenced phi function
        if (suxVal instanceof Phi) {
            Phi phi = (Phi) suxVal;
            // curVal can be null without phi being null in conjunction with inlining
            if (!phi.isDeadPhi() && curVal != null && curVal != phi) {
                assert !phi.isIllegal() : "illegal phi cannot be marked as live";
                if (curVal instanceof Phi) {
                    operandForPhi((Phi) curVal);
                }
                CiValue operand = curVal.operand();
                if (operand.isIllegal()) {
                    assert curVal instanceof Constant || curVal instanceof Local : "these can be produced lazily";
                    operand = operandForInstruction(curVal);
                }
                resolver.move(operand, operandForPhi(phi));
            }
        }
    }

    protected void moveToPhi() {
        assert lastState != null;
        this.moveToPhi(lastState);
    }

    protected void moveToPhi(FrameState curState) {
        // Moves all stack values into their phi position
        BlockBegin bb = currentBlock;
        if (bb.numberOfSux() == 1) {
            BlockBegin sux = bb.suxAt(0);
            assert sux.numberOfPreds() > 0 : "invalid CFG";

            // a block with only one predecessor never has phi functions
            if (sux.numberOfPreds() > 1) {
                PhiResolver resolver = new PhiResolver(this);

                FrameState suxState = sux.stateBefore();

                for (int index = 0; index < suxState.stackSize(); index++) {
                    moveToPhi(resolver, curState.stackAt(index), suxState.stackAt(index));
                }

                for (int index = 0; index < suxState.localsSize(); index++) {
                    moveToPhi(resolver, curState.localAt(index), suxState.localAt(index));
                }
                resolver.dispose();
            }
        }
    }

    /**
     * Creates a new {@linkplain CiVariable variable}.
     *
     * @param kind the kind of the variable
     * @return a new variable
     */
    public CiVariable newVariable(CiKind kind) {
        return operands.newVariable(kind);
    }

    CiValue operandForInstruction(Value x) {
        CiValue operand = x.operand();
        if (operand.isIllegal()) {
            if (x instanceof Constant) {
                x.setOperand(x.asConstant());
            } else {
                assert x instanceof Phi || x instanceof Local : "only for Phi and Local";
                // allocate a variable for this local or phi
                createResultVariable(x);
            }
        }
        return x.operand();
    }

    private CiValue operandForPhi(Phi phi) {
        assert !phi.isDeadPhi();
        if (phi.operand().isIllegal()) {
            // allocate a variable for this phi
            CiVariable operand = newVariable(phi.kind);
            setResult(phi, operand);
        }
        return phi.operand();
    }

    protected void postGCWriteBarrier(CiValue addr, CiValue newVal) {
       XirSnippet writeBarrier = xir.genWriteBarrier(toXirArgument(addr));
       if (writeBarrier != null) {
           emitXir(writeBarrier, null, null, null, false);
       }
    }

    protected void preGCWriteBarrier(CiValue addrOpr, boolean patch, LIRDebugInfo info) {
    }

    protected void setNoResult(Instruction x) {
        x.clearOperand();
    }

    protected CiValue setResult(Value x, CiVariable operand) {
        x.setOperand(operand);
        if (C1XOptions.DetailedAsserts) {
            operands.recordResult(operand, x);
        }
        return operand;
    }

    protected void shiftOp(int code, CiValue resultOp, CiValue value, CiValue count, CiValue tmp) {
        if (isTwoOperand && value != resultOp) {
            assert count != resultOp : "malformed";
            lir.move(value, resultOp);
            value = resultOp;
        }

        assert count.isConstant() || count.isVariableOrRegister();
        switch (code) {
            case ISHL:
            case LSHL:
                lir.shiftLeft(value, count, resultOp, tmp);
                break;
            case ISHR:
            case LSHR:
                lir.shiftRight(value, count, resultOp, tmp);
                break;
            case IUSHR:
            case LUSHR:
                lir.unsignedShiftRight(value, count, resultOp, tmp);
                break;
            default:
                Util.shouldNotReachHere();
        }
    }

    protected void walkState(Instruction x, FrameState state) {
        if (state == null) {
            return;
        }
        for (int index = 0; index < state.stackSize(); index++) {
            Value value = state.stackAt(index);
            if (value != x) {
                walkStateValue(value);
            }
        }
        FrameState s = state;
        int bci = x.bci();
        if (bci == Instruction.SYNCHRONIZATION_ENTRY_BCI) {
            assert x instanceof ExceptionObject ||
                   x instanceof Throw ||
                   x instanceof MonitorEnter ||
                   x instanceof MonitorExit : x + ", " + x.getClass();
        }

        for (int index = 0; index < s.localsSize(); index++) {
            final Value value = s.localAt(index);
            if (value != null) {
                if (!value.isIllegal()) {
                    walkStateValue(value);
                }
            }
        }
    }

    private void walkStateValue(Value value) {
        if (value != null) {
            assert !value.hasSubst() : "missed substitution on " + value.toString();
            if (value instanceof Phi && !value.isIllegal()) {
                // phi's are special
                operandForPhi((Phi) value);
            } else if (value.operand().isIllegal()) {
                // instruction doesn't have an operand yet
                CiValue operand = makeOperand(value);
                assert operand.isLegal() : "must be evaluated now";
            }
        }
    }

    protected LIRDebugInfo stateFor(Instruction x) {
        assert lastState != null : "must have state before instruction for " + x;
        return stateFor(x, lastState);
    }

    protected LIRDebugInfo stateFor(Instruction x, FrameState state) {
        if (compilation.placeholderState != null) {
            state = compilation.placeholderState;
        }

        assert state != null;
        return new LIRDebugInfo(state, x.exceptionHandlers());
    }

    List<CiValue> visitInvokeArguments(CiCallingConvention cc, Invoke x, List<CiValue> pointerSlots) {
        // for each argument, load it into the correct location
        List<CiValue> argList = new ArrayList<CiValue>(x.argumentCount());
        int j = 0;
        for (int i = 0; i < x.argumentCount(); i++) {
            Value arg = x.argument(i);
            if (arg != null) {
                CiValue operand = cc.locations[j++];
                if (operand.isRegister()) {
                    force(arg, operand);
                } else {
                    LIRItem param = new LIRItem(arg, this);
                    assert operand.isStackSlot();
                    CiStackSlot slot = (CiStackSlot) operand;
                    assert !slot.inCallerFrame();
                    param.loadForStore(slot.kind);
                    if (slot.kind == CiKind.Long || slot.kind == CiKind.Double) {
                        lir.unalignedMove(param.result(), slot);
                    } else {
                        lir.move(param.result(), slot);
                    }

                    if (arg.kind == CiKind.Object && pointerSlots != null) {
                        // This slot must be marked explicitly in the pointer map.
                        pointerSlots.add(slot);
                    }
                }
                argList.add(operand);
            }
        }
        return argList;
    }

    /**
     * Ensures that an operand has been {@linkplain Value#setOperand(CiValue) initialized}
     * for storing the result of an instruction.
     *
     * @param instruction an instruction that produces a result value
     */
    protected CiValue makeOperand(Value instruction) {
        if (instruction == null) {
            return CiValue.IllegalValue;
        }
        CiValue operand = instruction.operand();
        if (operand.isIllegal()) {
            if (instruction instanceof Phi) {
                // a phi may not have an operand yet if it is for an exception block
                operand = operandForPhi((Phi) instruction);
            } else if (instruction instanceof Constant) {
                operand = operandForInstruction(instruction);
            }
        }
        // the value must be a constant or have a valid operand
        assert operand.isLegal() : "this root has not been visited yet";
        return operand;
    }

    /**
     * Gets the ABI specific operand used to return a value of a given kind from a method.
     *
     * @param kind the kind of value being returned
     * @return the operand representing the ABI defined location used return a value of kind {@code kind}
     */
    protected CiValue resultOperandFor(CiKind kind) {
        if (kind == CiKind.Void) {
            return IllegalValue;
        }
        CiRegister returnRegister = compilation.registerConfig.getReturnRegister(kind);
        return returnRegister.asValue(kind);
    }

    protected XirSupport site(Value x) {
        return xirSupport.site(x);
    }

    public void maybePrintCurrentInstruction() {
        if (currentInstruction != null && lastInstructionPrinted != currentInstruction) {
            lastInstructionPrinted = currentInstruction;
            InstructionPrinter ip = new InstructionPrinter(TTY.out());
            ip.printInstructionListing(currentInstruction);
        }
    }

    protected abstract boolean canInlineAsConstant(Value i);

    protected abstract boolean canStoreAsConstant(Value i, CiKind kind);

    protected abstract boolean strengthReduceMultiply(CiValue left, int constant, CiValue result, CiValue tmp);

    protected abstract CiAddress genAddress(CiValue base, CiValue index, int shift, int disp, CiKind kind);

    protected abstract void genCmpMemInt(Condition condition, CiValue base, int disp, int c, LIRDebugInfo info);

    protected abstract void genCmpRegMem(Condition condition, CiValue reg, CiValue base, int disp, CiKind kind, LIRDebugInfo info);

    /**
     * Implements site-specific information for the XIR interface.
     */
    static class XirSupport implements XirSite {
        Value current;

        XirSupport() {
        }

        public CiCodePos getCodePos() {
            // TODO: get the code position of the current instruction if possible
            return null;
        }

        public boolean isNonNull(XirArgument argument) {
            if (argument.constant == null && argument.object instanceof LIRItem) {
                // check the flag on the original value
                return ((LIRItem) argument.object).instruction.isNonNull();
            }
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

        XirSupport site(Value v) {
            current = v;
            return this;
        }

        @Override
        public String toString() {
            return "XirSupport<" + current + ">";
        }


    }

    @Override
    public void visitFrameState(FrameState i) {
        // nothing to do for now
    }
}
