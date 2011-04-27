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

import java.lang.reflect.*;
import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.alloc.*;
import com.sun.c1x.alloc.OperandPool.VariableFlag;
import com.sun.c1x.asm.*;
import com.sun.c1x.debug.*;
import com.sun.c1x.globalstub.*;
import com.sun.c1x.graph.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.ir.Value.Flag;
import com.sun.c1x.lir.FrameMap.StackBlock;
import com.sun.c1x.lir.*;
import com.sun.c1x.opt.*;
import com.sun.c1x.util.*;
import com.sun.c1x.value.*;
import com.sun.c1x.value.FrameState.PhiProcedure;
import com.sun.cri.bytecode.*;
import com.sun.cri.bytecode.Bytecodes.MemoryBarriers;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiAddress.Scale;
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

        // mark the liveness of all instructions if it hasn't already been done by the optimizer
        LivenessMarker livenessMarker = new LivenessMarker(ir);
        C1XMetrics.LiveHIRInstructions += livenessMarker.liveCount();
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

    public final void emitGuard(Guard x) {
        FrameState state = x.stateBefore();
        assert state != null : "deoptimize instruction always needs a state";

        if (deoptimizationStubs == null) {
            deoptimizationStubs = new ArrayList<DeoptimizationStub>();
        }

        // (tw) TODO: Try to reuse an existing stub if possible.
        // It is only allowed if there are no LIR instructions in between that can modify registers.

        DeoptimizationStub stub = new DeoptimizationStub(state);
        deoptimizationStubs.add(stub);
        lir.branch(x.condition.negate(), stub.label, stub.info);
    }

    public void doBlock(BlockBegin block) {
        blockDoProlog(block);
        this.currentBlock = block;

        for (Instruction instr = block; instr != null; instr = instr.next()) {
            if (instr.isLive()) {
                walkState(instr, instr.stateBefore());
                doRoot(instr);
            }
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
        emitXir(snippet, x, x.needsNullCheck() ? stateFor(x) : null, null, true);
        return x.operand();
    }

    @Override
    public void visitBase(Base x) {
        // emit phi-instruction move after safepoint since this simplifies
        // describing the state at the safepoint.
        moveToPhi(x.stateAfter());

        // all blocks with a successor must end with an unconditional jump
        // to the successor even if they are consecutive
        lir.jump(x.defaultSuccessor());
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
            if (local.isLive()) {
                setResult(local, dest);
            }
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
        XirSnippet snippet = xir.genCheckCast(site(x), obj, toXirArgument(x.targetClassInstruction), x.targetClass());
        emitXir(snippet, x, stateFor(x), null, true);
    }

    @Override
    public void visitInstanceOf(InstanceOf x) {
        XirArgument obj = toXirArgument(x.object());
        XirSnippet snippet = xir.genInstanceOf(site(x), obj, toXirArgument(x.targetClassInstruction), x.targetClass());
        emitXir(snippet, x, maybeStateFor(x), null, true);
    }

    @Override
    public void visitMonitorEnter(MonitorEnter x) {
        XirArgument obj = toXirArgument(x.object());
        XirArgument lockAddress = toXirArgument(x.lockAddress());
        XirSnippet snippet = xir.genMonitorEnter(site(x), obj, lockAddress);
        emitXir(snippet, x, maybeStateFor(x), stateFor(x, x.stateAfter()), null, true, null);
    }

    @Override
    public void visitMonitorExit(MonitorExit x) {
        XirArgument obj = toXirArgument(x.object());
        XirArgument lockAddress = toXirArgument(x.lockAddress());
        XirSnippet snippet = xir.genMonitorExit(site(x), obj, lockAddress);
        emitXir(snippet, x, maybeStateFor(x), null, true);
    }

    @Override
    public void visitStoreIndexed(StoreIndexed x) {
        XirArgument array = toXirArgument(x.array());
        XirArgument length = x.length() == null ? null : toXirArgument(x.length());
        XirArgument index = toXirArgument(x.index());
        XirArgument value = toXirArgument(x.value());
        XirSnippet snippet = xir.genArrayStore(site(x), array, index, length, value, x.elementKind(), null);
        emitXir(snippet, x, maybeStateFor(x), null, true);
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
    public void visitNewObjectArrayClone(NewObjectArrayClone x) {
        XirArgument length = toXirArgument(x.length());
        XirArgument referenceArray = toXirArgument(x.referenceArray());
        XirSnippet snippet = xir.genNewObjectArrayClone(site(x), length, referenceArray);
        emitXir(snippet, x, stateFor(x), null, true);
    }

    @Override
    public void visitNewMultiArray(NewMultiArray x) {
        XirArgument[] dims = new XirArgument[x.dimensions().length];

        for (int i = 0; i < dims.length; i++) {
            dims[i] = toXirArgument(x.dimensions()[i]);
        }

        XirSnippet snippet = xir.genNewMultiArray(site(x), dims, x.elementKind);
        emitXir(snippet, x, stateFor(x), null, true);
    }

    @Override
    public void visitConstant(Constant x) {
        if (canInlineAsConstant(x)) {
            //setResult(x, loadConstant(x));
        } else {
            CiValue res = x.operand();
            if (!(res.isLegal())) {
                res = x.asConstant();
            }
            if (res.isConstant()) {
                if (isUsedForValue(x)) {
                    CiVariable reg = createResultVariable(x);
                    lir.move(res, reg);
                } else {
                    assert x.checkFlag(Value.Flag.LiveDeopt);
                    x.setOperand(res);
                }
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
        emitXir(snippet, x, maybeStateFor(x), null, true);
    }

    @Override
    public void visitGoto(Goto x) {
        setNoResult(x);

        if (currentBlock.next() instanceof OsrEntry) {
            // need to free up storage used for OSR entry point
            CiValue osrBuffer = currentBlock.next().operand();
            callRuntime(CiRuntimeCall.OSRMigrationEnd, null, osrBuffer);
            emitXir(xir.genSafepoint(site(x)), x, stateFor(x, x.stateAfter()), null, false);
        }

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

    @Override
    public void visitIntrinsic(Intrinsic x) {
        Value[] vals = x.arguments();
        XirSnippet snippet;

        switch (x.intrinsic()) {
            case java_lang_Float$intBitsToFloat:
            case java_lang_Double$doubleToRawLongBits:
            case java_lang_Double$longBitsToDouble:
            case java_lang_Float$floatToRawIntBits: {
                visitFPIntrinsics(x);
                return;
            }

            case java_lang_System$currentTimeMillis: {
                assert x.numberOfArguments() == 0 : "wrong type";
                CiValue reg = callRuntimeWithResult(CiRuntimeCall.JavaTimeMillis, null, (CiValue[]) null);
                CiValue result = createResultVariable(x);
                lir.move(reg, result);
                return;
            }

            case java_lang_System$nanoTime: {
                assert x.numberOfArguments() == 0 : "wrong type";
                CiValue reg = callRuntimeWithResult(CiRuntimeCall.JavaTimeNanos, null, (CiValue[]) null);
                CiValue result = createResultVariable(x);
                lir.move(reg, result);
                return;
            }

            case java_lang_Object$init:
                visitRegisterFinalizer(x);
                return;

            case java_lang_Math$log:   // fall through
            case java_lang_Math$log10: // fall through
            case java_lang_Math$abs:   // fall through
            case java_lang_Math$sqrt:  // fall through
            case java_lang_Math$tan:   // fall through
            case java_lang_Math$sin:   // fall through
            case java_lang_Math$cos:
                genMathIntrinsic(x);
                return;

            case sun_misc_Unsafe$compareAndSwapObject:
                genCompareAndSwap(x, CiKind.Object);
                return;
            case sun_misc_Unsafe$compareAndSwapInt:
                genCompareAndSwap(x, CiKind.Int);
                return;
            case sun_misc_Unsafe$compareAndSwapLong:
                genCompareAndSwap(x, CiKind.Long);
                return;

            case java_lang_Thread$currentThread:
                snippet = xir.genCurrentThread(site(x));
                if (snippet != null) {
                    emitXir(snippet, x, null, null, true);
                    return;
                }
                break;

            case java_lang_Object$getClass:
                snippet = xir.genGetClass(site(x), toXirArgument(vals[0]));
                if (snippet != null) {
                    emitXir(snippet, x, stateFor(x), null, true);
                    return;
                }
                break;
        }


        XirArgument[] args = new XirArgument[vals.length];
        for (int i = 0; i < vals.length; i++) {
            args[i] = toXirArgument(vals[i]);
        }
        snippet = xir.genIntrinsic(site(x), args, x.target());
        if (snippet != null) {
            emitXir(snippet, x, x.stateBefore() == null ? null : stateFor(x), null, true);
            return;
        }
        x.setOperand(emitInvokeKnown(x.target(), x.stateBefore(), vals));
    }

    @Override
    public void visitInvoke(Invoke x) {
        RiMethod target = x.target();
        LIRDebugInfo info = stateFor(x, x.stateBefore());

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
        List<CiValue> argList = visitInvokeArguments(cc, x.arguments(), pointerSlots);

        if (C1XOptions.InvokeSnippetAfterArguments) {
            destinationAddress = emitXir(snippet, x, info.copy(), null, x.target(), false, pointerSlots);
        }

        // emit direct or indirect call to the destination address
        if (destinationAddress instanceof CiConstant) {
            // Direct call
            assert ((CiConstant) destinationAddress).isDefaultValue() : "destination address should be zero";
            lir.callDirect(target, resultOperand, argList, info, snippet.marks, pointerSlots);
        } else {
            // Indirect call
            argList.add(destinationAddress);
            lir.callIndirect(target, resultOperand, argList, info, snippet.marks, pointerSlots);
        }

        if (resultOperand.isLegal()) {
            CiValue result = createResultVariable(x);
            lir.move(resultOperand, result);
        }
    }

    @Override
    public void visitNativeCall(NativeCall x) {
        LIRDebugInfo info = stateFor(x, x.stateBefore());
        CiValue resultOperand = resultOperandFor(x.kind);
        CiValue callAddress = load(x.address());
        CiKind[] signature = Util.signatureToKinds(x.signature, null);
        CiCallingConvention cc = compilation.frameMap().getCallingConvention(signature, NativeCall);
        List<CiValue> argList = visitInvokeArguments(cc, x.arguments, null);
        argList.add(callAddress);
        lir.callNative(x.nativeMethod.jniSymbol(), resultOperand, argList, info, null);
        if (resultOperand.isLegal()) {
            CiValue result = createResultVariable(x);
            lir.move(resultOperand, result);
        }
    }

    @Override
    public void visitTemplateCall(TemplateCall x) {
        CiValue resultOperand = resultOperandFor(x.kind);
        List<CiValue> argList;
        if (x.receiver() != null) {
            CiCallingConvention cc = compilation.frameMap().getCallingConvention(new CiKind[] {CiKind.Object}, JavaCall);
            argList = visitInvokeArguments(cc, new Value[] {x.receiver()}, null);
        } else {
            argList = new ArrayList<CiValue>();
        }

        if (x.address() != null) {
            CiValue callAddress = load(x.address());
            argList.add(callAddress);
        }
        lir.templateCall(resultOperand, argList);
        if (resultOperand.isLegal()) {
            CiValue result = createResultVariable(x);
            lir.move(resultOperand, result);
        }
    }

    @Override
    public void visitLoadRegister(LoadRegister x) {
        x.setOperand(x.register.asValue(x.kind));
    }

    @Override
    public void visitPause(Pause i) {
        lir.pause();
    }

    @Override
    public void visitBreakpointTrap(BreakpointTrap i) {
        lir.breakpoint();
    }

    protected CiAddress getAddressForPointerOp(PointerOp x, CiKind kind, CiValue pointer) {
        CiAddress addr;
        Value offset = x.offset();
        Value index = x.index();
        if (x.displacement() == null) {
            // address is [pointer + offset]
            if (offset.isConstant() && offset.kind.isInt()) {
                int displacement = x.offset().asConstant().asInt();
                addr = new CiAddress(kind, pointer, displacement);
            } else {
                addr = new CiAddress(kind, pointer, load(offset));
            }
        } else {
            // address is [pointer + disp + (index * scale)]
            assert (x.opcode & 0xff) == PGET || (x.opcode & 0xff) == PSET;
            if (!x.displacement().isConstant()) {
                CiVariable tmp = newVariable(CiKind.Word);
                arithmeticOpLong(Bytecodes.LADD, tmp, pointer, load(x.displacement()), null);
                int kindSize = compilation.target.sizeInBytes(kind);
                Scale scale = Scale.fromInt(kindSize);
                if (index.isConstant()) {
                    addr = new CiAddress(kind, tmp, index.asConstant().asInt() * kindSize);
                } else {
                    addr = new CiAddress(kind, tmp, load(index), scale, 0);
                }
            } else {
                int displacement = x.displacement().asConstant().asInt();
                int kindSize = compilation.target.sizeInBytes(kind);
                Scale scale = Scale.fromInt(kindSize);
                if (index.isConstant()) {
                    displacement += index.asConstant().asInt() * kindSize;
                    addr = new CiAddress(kind, pointer, displacement);
                } else {
                    addr = new CiAddress(kind, pointer, load(index), scale, displacement);
                }
            }
        }
        return addr;
    }

    @Override
    public void visitAllocateStackHandle(StackHandle x) {
        CiValue value = load(x.value());
        CiValue src = forceToSpill(value, x.value().kind, true);
        CiValue dst = createResultVariable(x);

        CiConstant constant = x.value().isConstant() ? x.value().asConstant() : null;
        if (constant == null) {
            CiConstant zero = CiConstant.defaultValue(x.value().kind);
            lir.cmp(Condition.EQ, src, zero);
        }
        lir.lea(src, dst);
        if (constant != null) {
            if (constant.isDefaultValue()) {
                lir.move(value, dst);
            }
        } else {
            lir.cmove(Condition.EQ, CiConstant.ZERO, dst, dst);
        }
    }

    @Override
    public void visitLoadPointer(LoadPointer x) {
        LIRDebugInfo info = maybeStateFor(x);
        CiValue pointer = load(x.pointer());
        CiValue dst = createResultVariable(x);
        CiAddress src = getAddressForPointerOp(x, x.dataKind, pointer);
        lir.load(src, dst, info);
    }

    @Override
    public void visitStorePointer(StorePointer x) {
        LIRDebugInfo info = maybeStateFor(x);
        LIRItem value = new LIRItem(x.value(), this);
        CiValue pointer = load(x.pointer());
        value.loadItem(x.dataKind);
        CiAddress dst = getAddressForPointerOp(x, x.dataKind, pointer);
        lir.store(value.result(), dst, info);
    }

    @Override
    public void visitInfopoint(Infopoint x) {
        LIRDebugInfo info = stateFor(x);
        if (x.opcode == SAFEPOINT) {
            emitXir(xir.genSafepoint(site(x)), x, info, null, false);
            return;
        }
        assert x.opcode == HERE || x.opcode == INFO;
        CiValue result = x.kind.isVoid() ? CiValue.IllegalValue : createResultVariable(x);
        LIROpcode opcode = x.opcode == HERE ? LIROpcode.Here : LIROpcode.Info;
        lir.infopoint(opcode, result, info);
    }

    @Override
    public void visitStackAllocate(StackAllocate x) {
        CiValue result = createResultVariable(x);
        assert x.size().isConstant() : "ALLOCA bytecode 'size' operand is not a constant: " + x.size();
        StackBlock stackBlock = compilation.frameMap().reserveStackBlock(x.size().asConstant().asInt());
        lir.alloca(stackBlock, result);
    }

    @Override
    public void visitMonitorAddress(MonitorAddress x) {
        CiValue result = createResultVariable(x);
        lir.monitorAddress(x.monitor(), result);
    }

    @Override
    public void visitMemoryBarrier(MemoryBarrier x) {
        if (x.barriers != 0) {
            lir.membar(x.barriers);
        }
    }

    @Override
    public void visitUnsafeCast(UnsafeCast i) {
        assert !i.redundant : "redundant UnsafeCasts must be eliminated by the front end";
        CiValue src = load(i.value());
        CiValue dst = createResultVariable(i);
        lir.move(src, dst);
    }

    /**
     * For note on volatile fields, see {@link #visitStoreField(StoreField)}.
     */
    @Override
    public void visitLoadField(LoadField x) {
        RiField field = x.field();
        boolean needsPatching = x.needsPatching();
        LIRDebugInfo info = null;
        if (needsPatching || x.needsNullCheck()) {
            info = stateFor(x, x.stateBefore());
            assert info != null;
        }

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
        emitXir(snippet, x, maybeStateFor(x), null, true);
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
            emitXir(xir.genSafepoint(site(x)), x, stateFor(x, x.stateAfter()), null, false);
        }

        // move values into phi locations
        moveToPhi(x.stateAfter());

        if (x.numberOfCases() == 0 || x.numberOfCases() < C1XOptions.SequentialSwitchLimit) {
            int len = x.numberOfCases();
            for (int i = 0; i < len; i++) {
                lir.cmp(Condition.EQ, tag, x.keyAt(i));
                lir.branch(Condition.EQ, CiKind.Int, x.suxAt(i));
            }
            lir.jump(x.defaultSuccessor());
        } else {
            visitSwitchRanges(createLookupRanges(x), tag, x.defaultSuccessor());
        }
    }

    @Override
    public void visitNullCheck(NullCheck x) {
        // TODO: this is suboptimal because it may result in an unnecessary move
        CiValue value = load(x.object());
        if (x.canTrap()) {
            LIRDebugInfo info = stateFor(x);
            lir.nullCheck(value, info);
        }
        x.setOperand(value);
    }

    @Override
    public void visitOsrEntry(OsrEntry x) {
        // construct our frame and model the production of incoming pointer
        // to the OSR buffer.
        lir.osrEntry(osrBufferPointer());
        CiValue result = createResultVariable(x);
        lir.move(osrBufferPointer(), result);
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
                emitXir(epilogue, x, stateFor(x, x.stateAfter()), compilation.method, false);
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
                item.loadItem(var.kind);
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
    public void visitIncrementRegister(IncrementRegister x) {
        CiValue reg = x.register.asValue(CiKind.Word);
        if (x.delta().isConstant()) {
            int delta = x.delta().asConstant().asInt();
            if (delta < 0) {
                lir.sub(reg, CiConstant.forInt(-delta), reg);
            } else {
                lir.add(reg, CiConstant.forInt(delta), reg);
            }
        } else {
            lir.add(reg, makeOperand(x.delta()), reg);
        }
    }

    @Override
    public void visitStoreRegister(StoreRegister x) {
        CiValue reg = x.register.asValue(x.kind);
        lir.move(makeOperand(x.value()), reg);
    }

    @Override
    public void visitStoreField(StoreField x) {
        RiField field = x.field();
        boolean needsPatching = x.needsPatching();

        LIRDebugInfo info = null;
        if (needsPatching || x.needsNullCheck()) {
            info = stateFor(x, x.stateBefore());
        }

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
            emitXir(xir.genSafepoint(site(x)), x, stateFor(x, x.stateAfter()), null, false);
        }

        // move values into phi locations
        moveToPhi(x.stateAfter());

        // TODO: tune the defaults for the controls used to determine what kind of translation to use
        if (x.numberOfCases() == 0 || x.numberOfCases() <= C1XOptions.SequentialSwitchLimit) {
            int loKey = x.lowKey();
            int len = x.numberOfCases();
            for (int i = 0; i < len; i++) {
                lir.cmp(Condition.EQ, tag, i + loKey);
                lir.branch(Condition.EQ, CiKind.Int, x.suxAt(i));
            }
            lir.jump(x.defaultSuccessor());
        } else {
            SwitchRange[] switchRanges = createLookupRanges(x);
            int rangeDensity = x.numberOfCases() / switchRanges.length;
            if (rangeDensity >= C1XOptions.RangeTestsSwitchDensity) {
                visitSwitchRanges(switchRanges, tag, x.defaultSuccessor());
            } else {
                List<BlockBegin> nonDefaultSuccessors = x.successors().subList(0, x.numberOfCases());
                BlockBegin[] targets = nonDefaultSuccessors.toArray(new BlockBegin[nonDefaultSuccessors.size()]);
                lir.tableswitch(tag, x.lowKey(), x.defaultSuccessor(), targets);
            }
        }
    }

    @Override
    public void visitThrow(Throw x) {
        setNoResult(x);
        CiValue exceptionOpr = load(x.exception());
        LIRDebugInfo info = stateFor(x, x.stateAfter());

        // check if the instruction has an xhandler in any of the nested scopes
        boolean unwind = false;
        if (x.exceptionHandlers().size() == 0) {
            // this throw is not inside an xhandler
            unwind = true;
        } else {
            // get some idea of the throw type
            boolean typeIsExact = true;
            RiType throwType = x.exception().exactType();
            if (throwType == null) {
                typeIsExact = false;
                throwType = x.exception().declaredType();
            }
            if (throwType != null && throwType.isResolved() && throwType.isInstanceClass()) {
                unwind = !ExceptionHandler.couldCatch(x.exceptionHandlers(), throwType, typeIsExact);
            }
        }

        assert !currentBlock.checkBlockFlag(BlockBegin.BlockFlag.DefaultExceptionHandler) || unwind : "should be no more handlers to dispatch to";

        // move exception oop into fixed register
        CiCallingConvention callingConvention = compilation.frameMap().getCallingConvention(new CiKind[]{CiKind.Object}, RuntimeCall);
        CiValue argumentOperand = callingConvention.locations[0];
        lir.move(exceptionOpr, argumentOperand);

        if (unwind) {
            lir.unwindException(exceptionPcOpr(), exceptionOpr, info);
        } else {
            lir.throwException(exceptionPcOpr(), argumentOperand, info);
        }
    }

    @Override
    public void visitUnsafeGetObject(UnsafeGetObject x) {
        CiKind kind = x.unsafeOpKind;

        CiValue off = load(x.offset());
        CiValue src = load(x.object());

        CiValue reg = createResultVariable(x);

        if (x.isVolatile()) {
            vma.preVolatileRead();
        }
        genGetObjectUnsafe(reg, src, off, kind, x.isVolatile());
        if (x.isVolatile()) {
            vma.postVolatileRead();
        }
    }

    @Override
    public void visitUnsafeGetRaw(UnsafeGetRaw x) {
        LIRItem idx = new LIRItem(this);
        CiValue base = load(x.base());
        if (x.hasIndex()) {
            idx.setInstruction(x.index());
            idx.loadNonconstant();
        }

        CiValue reg = createResultVariable(x);

        int log2scale = 0;
        if (x.hasIndex()) {
            assert x.index().kind.isInt() : "should not find non-int index";
            log2scale = x.log2Scale();
        }

        assert !x.hasIndex() || idx.instruction == x.index() : "should match";

        CiKind dstKind = x.unsafeOpKind;
        CiValue indexOp = idx.result();

        CiAddress addr = null;
        if (indexOp.isConstant()) {
            assert log2scale == 0 : "must not have a scale";
            CiConstant constantIndexOp = (CiConstant) indexOp;
            addr = new CiAddress(dstKind, base, constantIndexOp.asInt());
        } else {

            if (compilation.target.arch.isX86()) {
                addr = new CiAddress(dstKind, base, indexOp, CiAddress.Scale.fromInt(2 ^ log2scale), 0);

            } else if (compilation.target.arch.isSPARC()) {
                if (indexOp.isIllegal() || log2scale == 0) {
                    addr = new CiAddress(dstKind, base, indexOp);
                } else {
                    CiValue tmp = newVariable(CiKind.Int);
                    lir.shiftLeft(indexOp, log2scale, tmp);
                    addr = new CiAddress(dstKind, base, tmp);
                }

            } else {
                Util.shouldNotReachHere();
            }
        }

        if (x.mayBeUnaligned() && (dstKind == CiKind.Long || dstKind == CiKind.Double)) {
            lir.unalignedMove(addr, reg);
        } else {
            lir.move(addr, reg);
        }
    }

    @Override
    public void visitUnsafePrefetchRead(UnsafePrefetchRead x) {
        visitUnsafePrefetch(x, false);
    }

    @Override
    public void visitUnsafePrefetchWrite(UnsafePrefetchWrite x) {
        visitUnsafePrefetch(x, true);
    }

    @Override
    public void visitUnsafePutObject(UnsafePutObject x) {
        CiKind kind = x.unsafeOpKind;
        LIRItem data = new LIRItem(x.value(), this);

        CiValue src = load(x.object());
        data.loadItem(kind);
        CiValue off = load(x.offset());

        setNoResult(x);

        if (x.isVolatile()) {
            vma.preVolatileWrite();
        }
        genPutObjectUnsafe(src, off, data.result(), kind, x.isVolatile());
        if (x.isVolatile()) {
            vma.postVolatileWrite();
        }
    }

    @Override
    public void visitUnsafePutRaw(UnsafePutRaw x) {
        int log2scale = 0;
        CiKind kind = x.unsafeOpKind;

        if (x.hasIndex()) {
            assert x.index().kind.isInt() : "should not find non-int index";
            log2scale = x.log2scale();
        }

        LIRItem value = new LIRItem(x.value(), this);
        LIRItem idx = new LIRItem(this);

        CiValue base = load(x.base());
        if (x.hasIndex()) {
            idx.setInstruction(x.index());
            idx.loadItem();
        }

        value.loadItem(kind);

        setNoResult(x);

        CiValue indexOp = idx.result();
        if (log2scale != 0) {
            // temporary fix (platform dependent code without shift on Intel would be better)
            indexOp = newVariable(CiKind.Int);
            lir.move(idx.result(), indexOp);
            lir.shiftLeft(indexOp, log2scale, indexOp);
        }

        CiValue addr = new CiAddress(x.unsafeOpKind, base, indexOp);
        lir.move(value.result(), addr);
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

    private void visitFPIntrinsics(Intrinsic x) {
        assert x.numberOfArguments() == 1 : "wrong type";
        CiValue reg = createResultVariable(x);
        CiValue value = load(x.argumentAt(0));
        CiValue tmp = forceToSpill(value, x.kind, false);
        lir.move(tmp, reg);
    }

    private void visitRegisterFinalizer(Intrinsic x) {
        assert x.numberOfArguments() == 1 : "wrong type";
        CiValue receiver = load(x.argumentAt(0));
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

    private void visitUnsafePrefetch(UnsafePrefetch x, boolean isStore) {
        LIRItem src = new LIRItem(x.object(), this);
        LIRItem off = new LIRItem(x.offset(), this);

        src.loadItem();
        if (!(off.result().isConstant() && canInlineAsConstant(x.offset()))) {
            off.loadItem();
        }

        setNoResult(x);

        CiAddress addr = genAddress(src.result(), off.result(), 0, 0, CiKind.Byte);
        lir.prefetch(addr, isStore);
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
            BlockBegin sux = x.suxAt(0);
            SwitchRange range = new SwitchRange(key, sux);
            for (int i = 1; i < len; i++) {
                int newKey = x.keyAt(i);
                BlockBegin newSux = x.suxAt(i);
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
            BlockBegin sux = x.suxAt(0);
            int key = x.lowKey();
            BlockBegin defaultSux = x.defaultSuccessor();
            SwitchRange range = new SwitchRange(key, sux);
            for (int i = 0; i < len; i++, key++) {
                BlockBegin newSux = x.suxAt(i);
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
        currentInstruction = instr;
        assert instr.isLive() : "use only with roots";
        assert !instr.hasSubst() : "shouldn't have missed substitution";

        if (C1XOptions.TraceLIRVisit) {
            TTY.println("Visiting    " + instr);
        }
        instr.accept(this);
        if (C1XOptions.TraceLIRVisit) {
            TTY.println("Operand for " + instr + " = " + instr.operand());
        }

        assert (instr.operand().isLegal()) || !isUsedForValue(instr) || instr.isConstant() || instr instanceof UnsafeCast : "operand was not set for live instruction";
    }

    private boolean isUsedForValue(Instruction instr) {
        return instr.checkFlag(Value.Flag.LiveValue);
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
            if (phi.isLive() && curVal != null && curVal != phi) {
                assert curVal.isLive() : "value not live: " + curVal + ", suxVal=" + suxVal;
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

                // walk up the inlined scopes until locals match
                while (curState.scope() != suxState.scope()) {
                    curState = curState.callerState();
                    assert curState != null : "scopes don't match up";
                }

                for (int index = 0; index < suxState.localsSize(); index++) {
                    moveToPhi(resolver, curState.localAt(index), suxState.localAt(index));
                }

                assert curState.scope().callerState == suxState.scope().callerState : "caller states must be equal";
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
        assert !isUsedForValue(x) : "can't have use";
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
            walkStateValue(state.stackAt(index));
        }
        FrameState s = state;
        int bci = x.bci();

        while (s != null) {
            IRScope scope = s.scope();
            if (bci == Instruction.SYNCHRONIZATION_ENTRY_BCI) {
                assert x instanceof ExceptionObject ||
                       x instanceof Throw ||
                       x instanceof MonitorEnter ||
                       x instanceof MonitorExit;
            }

            for (int index = 0; index < s.localsSize(); index++) {
                final Value value = s.localAt(index);
                if (value != null) {
                    if (!value.isIllegal()) {
                        walkStateValue(value);
                    }
                }
            }
            bci = scope.callerBCI();
            s = s.callerState();
        }
    }

    private void walkStateValue(Value value) {
        if (value != null) {
            assert !value.hasSubst() : "missed substitution";
            assert value.isLive() : "value must be marked live in frame state";
            if (value instanceof Phi && !value.isIllegal()) {
                // phi's are special
                operandForPhi((Phi) value);
            } else if (value.operand().isIllegal() && !(value instanceof UnsafeCast)) {
                // instruction doesn't have an operand yet
                CiValue operand = makeOperand(value);
                assert operand.isLegal() : "must be evaluated now";
            }
        }
    }

    protected LIRDebugInfo maybeStateFor(Instruction x) {
        FrameState stateBefore = x.stateBefore();
        if (stateBefore == null) {
            return null;
        }
        return stateFor(x, stateBefore);
    }

    protected LIRDebugInfo stateFor(Instruction x) {
        assert x.stateBefore() != null : "must have state before instruction for " + x;
        return stateFor(x, x.stateBefore());
    }

    protected LIRDebugInfo stateFor(Instruction x, FrameState state) {
        if (compilation.placeholderState != null) {
            state = compilation.placeholderState;
        }

        return new LIRDebugInfo(state, x.exceptionHandlers());
    }

    List<CiValue> visitInvokeArguments(CiCallingConvention cc, Value[] args, List<CiValue> pointerSlots) {
        // for each argument, load it into the correct location
        List<CiValue> argList = new ArrayList<CiValue>(args.length);
        int j = 0;
        for (Value arg : args) {
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
                        // This slot must be marked explicitedly in the pointer map.
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
        assert instruction.isLive();
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

    protected abstract CiValue exceptionPcOpr();

    protected abstract CiValue osrBufferPointer();

    protected abstract boolean strengthReduceMultiply(CiValue left, int constant, CiValue result, CiValue tmp);

    protected abstract CiAddress genAddress(CiValue base, CiValue index, int shift, int disp, CiKind kind);

    protected abstract void genCmpMemInt(Condition condition, CiValue base, int disp, int c, LIRDebugInfo info);

    protected abstract void genCmpRegMem(Condition condition, CiValue reg, CiValue base, int disp, CiKind kind, LIRDebugInfo info);

    protected abstract void genGetObjectUnsafe(CiValue dest, CiValue src, CiValue offset, CiKind kind, boolean isVolatile);

    protected abstract void genPutObjectUnsafe(CiValue src, CiValue offset, CiValue data, CiKind kind, boolean isVolatile);

    protected abstract void genCompareAndSwap(Intrinsic x, CiKind kind);

    protected abstract void genMathIntrinsic(Intrinsic x);

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
            return current == null || current.needsNullCheck();
        }

        public boolean requiresBoundsCheck() {
            return current == null || !current.checkFlag(Value.Flag.NoBoundsCheck);
        }

        public boolean requiresReadBarrier() {
            return current == null || !current.checkFlag(Value.Flag.NoReadBarrier);
        }

        public boolean requiresWriteBarrier() {
            return current == null || !current.checkFlag(Value.Flag.NoWriteBarrier);
        }

        public boolean requiresArrayStoreCheck() {
            return current == null || !current.checkFlag(Value.Flag.NoStoreCheck);
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

    public void arrayCopy(RiType type, ArrayCopy arrayCopy, XirSnippet snippet) {
        emitXir(snippet, arrayCopy, stateFor(arrayCopy), null, false);
    }

    @Override
    public void visitArrayCopy(ArrayCopy arrayCopy) {
        Value src = arrayCopy.src();
        Value dest = arrayCopy.dest();
        Value srcPos = arrayCopy.srcPos();
        Value destPos = arrayCopy.destPos();
        Value length = arrayCopy.length();
        RiType srcType = src.declaredType();
        RiType destType = dest.declaredType();
        if ((srcType != null && srcType.isArrayClass()) || (destType != null && destType.isArrayClass())) {
            RiType type = (srcType == null) ? destType : srcType;
            if ((srcType == null || destType == null || srcType.kind() != destType.kind()) && type.kind() != CiKind.Object) {
                TypeEqualityCheck typeCheck = new TypeEqualityCheck(src, dest, arrayCopy.stateBefore(), Condition.EQ);
                visitTypeEqualityCheck(typeCheck);
            }
            boolean inputsSame = (src == dest);
            boolean inputsDifferent = !inputsSame && (src.checkFlag(Flag.ResultIsUnique) || dest.checkFlag(Flag.ResultIsUnique));
            boolean needsStoreCheck = type.componentType().kind() == CiKind.Object && destType != srcType;
            if (!needsStoreCheck) {
                arrayCopy.setFlag(Flag.NoStoreCheck);
            }
            XirSnippet snippet = xir.genArrayCopy(site(arrayCopy), toXirArgument(src), toXirArgument(srcPos), toXirArgument(dest), toXirArgument(destPos), toXirArgument(length), type.componentType(), inputsSame, inputsDifferent);
            arrayCopy(type, arrayCopy, snippet);
            return;
        }
        arrayCopySlow(arrayCopy);
    }

    private void arrayCopySlow(ArrayCopy arrayCopy) {
        emitInvokeKnown(arrayCopy.arrayCopyMethod, arrayCopy.stateBefore(), arrayCopy.src(), arrayCopy.srcPos(), arrayCopy.dest(), arrayCopy.destPos(), arrayCopy.length());
    }

    private CiValue emitInvokeKnown(RiMethod method, FrameState stateBefore, Value... args) {
        boolean isStatic = Modifier.isStatic(method.accessFlags());
        Invoke invoke = new Invoke(isStatic ? Bytecodes.INVOKESTATIC : Bytecodes.INVOKESPECIAL, method.signature().returnKind(), args, isStatic, method, null, stateBefore);
        visitInvoke(invoke);
        return invoke.operand();
    }

    @Override
    public void visitTypeEqualityCheck(TypeEqualityCheck typeEqualityCheck) {
        Value x = typeEqualityCheck.left();
        Value y = typeEqualityCheck.right();

        CiValue leftValue = emitXir(xir.genGetClass(site(typeEqualityCheck), toXirArgument(x)), typeEqualityCheck, stateFor(typeEqualityCheck), null, false);
        CiValue rightValue = emitXir(xir.genGetClass(site(typeEqualityCheck), toXirArgument(y)), typeEqualityCheck, stateFor(typeEqualityCheck), null, false);
        lir.cmp(typeEqualityCheck.condition.negate(), leftValue, rightValue);
        emitGuard(typeEqualityCheck);
    }
}
