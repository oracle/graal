/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.target.amd64;

import static com.sun.cri.ci.CiCallingConvention.Type.*;
import static com.sun.cri.ci.CiValue.*;

import java.util.*;

import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.asm.target.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.nodes.calc.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiTargetMethod.Mark;
import com.sun.cri.ri.*;
import com.sun.cri.xir.*;
import com.sun.cri.xir.CiXirAssembler.RuntimeCallInformation;
import com.sun.cri.xir.CiXirAssembler.XirInstruction;
import com.sun.cri.xir.CiXirAssembler.XirLabel;
import com.sun.cri.xir.CiXirAssembler.XirMark;

public enum AMD64XirOpcode implements StandardOpcode.XirOpcode {
    XIR;

    public LIRInstruction create(XirSnippet snippet, CiValue[] operands, CiValue outputOperand, CiValue[] inputs, CiValue[] temps, int[] inputOperandIndices, int[] tempOperandIndices, int outputOperandIndex,
                        LIRDebugInfo info, LIRDebugInfo infoAfter, RiMethod method) {
        return new LIRXirInstruction(this, snippet, operands, outputOperand, inputs, temps, inputOperandIndices, tempOperandIndices, outputOperandIndex, info, infoAfter, method) {
            @Override
            public void emitCode(TargetMethodAssembler tasm) {
                emit(tasm, (AMD64MacroAssembler) tasm.asm, this);
            }
        };
    }

    private static void emit(TargetMethodAssembler tasm, AMD64MacroAssembler masm, LIRXirInstruction op) {
        XirSnippet snippet = op.snippet;
        Label endLabel = null;
        Label[] labels = new Label[snippet.template.labels.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = new Label();
            if (snippet.template.labels[i].name == XirLabel.TrueSuccessor) {
                if (op.trueSuccessor() == null) {
                    assert endLabel == null;
                    endLabel = new Label();
                    labels[i] = endLabel;
                } else {
                    labels[i] = op.trueSuccessor().label();
                }
            } else if (snippet.template.labels[i].name == XirLabel.FalseSuccessor) {
                if (op.falseSuccessor() == null) {
                    assert endLabel == null;
                    endLabel = new Label();
                    labels[i] = endLabel;
                } else {
                    labels[i] = op.falseSuccessor().label();
                }
            }
        }
        emitXirInstructions(tasm, masm, op, snippet.template.fastPath, labels, op.getOperands(), snippet.marks);
        if (endLabel != null) {
            masm.bind(endLabel);
        }

        if (snippet.template.slowPath != null) {
            tasm.compilation.lir().slowPaths.add(new SlowPath(op, labels, snippet.marks));
        }
    }

    private static class SlowPath implements LIR.SlowPath {
        public final LIRXirInstruction instruction;
        public final Label[] labels;
        public final Map<XirMark, Mark> marks;

        public SlowPath(LIRXirInstruction instruction, Label[] labels, Map<XirMark, Mark> marks) {
            this.instruction = instruction;
            this.labels = labels;
            this.marks = marks;
        }

        public void emitCode(TargetMethodAssembler tasm) {
            emitSlowPath(tasm, (AMD64MacroAssembler) tasm.asm, this);
        }
    }


    private static void emitSlowPath(TargetMethodAssembler tasm, AMD64MacroAssembler masm, SlowPath sp) {
        int start = -1;
        if (GraalOptions.TraceAssembler) {
            TTY.println("Emitting slow path for XIR instruction " + sp.instruction.snippet.template.name);
            start = masm.codeBuffer.position();
        }
        emitXirInstructions(tasm, masm, sp.instruction, sp.instruction.snippet.template.slowPath, sp.labels, sp.instruction.getOperands(), sp.marks);
        masm.nop();
        if (GraalOptions.TraceAssembler) {
            TTY.println("From " + start + " to " + masm.codeBuffer.position());
        }
    }

    protected static void emitXirInstructions(TargetMethodAssembler tasm, AMD64MacroAssembler masm, LIRXirInstruction xir, XirInstruction[] instructions, Label[] labels, CiValue[] operands, Map<XirMark, Mark> marks) {
        LIRDebugInfo info = xir == null ? null : xir.info;
        LIRDebugInfo infoAfter = xir == null ? null : xir.infoAfter;

        for (XirInstruction inst : instructions) {
            switch (inst.op) {
                case Add:
                    emitXirViaLir(tasm, masm, AMD64ArithmeticOpcode.IADD, AMD64ArithmeticOpcode.LADD, AMD64ArithmeticOpcode.FADD, AMD64ArithmeticOpcode.DADD, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Sub:
                    emitXirViaLir(tasm, masm, AMD64ArithmeticOpcode.ISUB, AMD64ArithmeticOpcode.LSUB, AMD64ArithmeticOpcode.FSUB, AMD64ArithmeticOpcode.DSUB, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Div:
                    emitXirViaLir(tasm, masm, AMD64DivOpcode.IDIV, AMD64DivOpcode.LDIV, AMD64ArithmeticOpcode.FDIV, AMD64ArithmeticOpcode.DDIV, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Mul:
                    emitXirViaLir(tasm, masm, AMD64MulOpcode.IMUL, AMD64MulOpcode.LMUL, AMD64ArithmeticOpcode.FMUL, AMD64ArithmeticOpcode.DMUL, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Mod:
                    emitXirViaLir(tasm, masm, AMD64DivOpcode.IREM, AMD64DivOpcode.LREM, null, null, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Shl:
                    emitXirViaLir(tasm, masm, AMD64ShiftOpcode.ISHL, AMD64ShiftOpcode.LSHL, null, null, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Sar:
                    emitXirViaLir(tasm, masm, AMD64ShiftOpcode.ISHR, AMD64ShiftOpcode.LSHR, null, null, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Shr:
                    emitXirViaLir(tasm, masm, AMD64ShiftOpcode.UISHR, AMD64ShiftOpcode.ULSHR, null, null, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case And:
                    emitXirViaLir(tasm, masm, AMD64ArithmeticOpcode.IAND, AMD64ArithmeticOpcode.LAND, null, null, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Or:
                    emitXirViaLir(tasm, masm, AMD64ArithmeticOpcode.IOR, AMD64ArithmeticOpcode.LOR, null, null, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Xor:
                    emitXirViaLir(tasm, masm, AMD64ArithmeticOpcode.IXOR, AMD64ArithmeticOpcode.LXOR, null, null, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Mov: {
                    CiValue result = operands[inst.result.index];
                    CiValue source = operands[inst.x().index];
                    AMD64MoveOpcode.move(tasm, masm, result, source);
                    break;
                }

                case PointerLoad: {
                    CiValue result = operands[inst.result.index];
                    CiValue pointer = operands[inst.x().index];
                    CiRegisterValue register = assureInRegister(tasm, masm, pointer);

                    AMD64MoveOpcode.load(tasm, masm, result, new CiAddress(inst.kind, register, 0), inst.kind, (Boolean) inst.extra ? info : null);
                    break;
                }

                case PointerStore: {
                    CiValue value = assureNot64BitConstant(tasm, masm, operands[inst.y().index]);
                    CiValue pointer = operands[inst.x().index];
                    assert pointer.isVariableOrRegister();

                    AMD64MoveOpcode.store(tasm, masm, new CiAddress(inst.kind, pointer, 0), value, inst.kind, (Boolean) inst.extra ? info : null);
                    break;
                }

                case PointerLoadDisp: {
                    CiXirAssembler.AddressAccessInformation addressInformation = (CiXirAssembler.AddressAccessInformation) inst.extra;
                    boolean canTrap = addressInformation.canTrap;

                    CiAddress.Scale scale = addressInformation.scale;
                    int displacement = addressInformation.disp;

                    CiValue result = operands[inst.result.index];
                    CiValue pointer = operands[inst.x().index];
                    CiValue index = operands[inst.y().index];

                    pointer = assureInRegister(tasm, masm, pointer);
                    assert pointer.isVariableOrRegister();

                    CiAddress src;
                    if (index.isConstant()) {
                        assert index.kind == CiKind.Int;
                        CiConstant constantIndex = (CiConstant) index;
                        src = new CiAddress(inst.kind, pointer, constantIndex.asInt() * scale.value + displacement);
                    } else {
                        src = new CiAddress(inst.kind, pointer, index, scale, displacement);
                    }

                    AMD64MoveOpcode.load(tasm, masm, result, src, inst.kind, canTrap ? info : null);
                    break;
                }

                case LoadEffectiveAddress: {
                    CiXirAssembler.AddressAccessInformation addressInformation = (CiXirAssembler.AddressAccessInformation) inst.extra;

                    CiAddress.Scale scale = addressInformation.scale;
                    int displacement = addressInformation.disp;

                    CiValue result = operands[inst.result.index];
                    CiValue pointer = operands[inst.x().index];
                    CiValue index = operands[inst.y().index];

                    pointer = assureInRegister(tasm, masm, pointer);
                    assert pointer.isVariableOrRegister();
                    CiAddress src = new CiAddress(CiKind.Illegal, pointer, index, scale, displacement);
                    masm.leaq(result.asRegister(), src);
                    break;
                }

                case PointerStoreDisp: {
                    CiXirAssembler.AddressAccessInformation addressInformation = (CiXirAssembler.AddressAccessInformation) inst.extra;
                    boolean canTrap = addressInformation.canTrap;

                    CiAddress.Scale scale = addressInformation.scale;
                    int displacement = addressInformation.disp;

                    CiValue value = assureNot64BitConstant(tasm, masm, operands[inst.z().index]);
                    CiValue pointer = operands[inst.x().index];
                    CiValue index = operands[inst.y().index];

                    pointer = assureInRegister(tasm, masm, pointer);
                    assert pointer.isVariableOrRegister();

                    CiAddress dst;
                    if (index.isConstant()) {
                        assert index.kind == CiKind.Int;
                        CiConstant constantIndex = (CiConstant) index;
                        dst = new CiAddress(inst.kind, pointer, IllegalValue, scale, constantIndex.asInt() * scale.value + displacement);
                    } else {
                        dst = new CiAddress(inst.kind, pointer, index, scale, displacement);
                    }

                    AMD64MoveOpcode.store(tasm, masm, dst, value, inst.kind, canTrap ? info : null);
                    break;
                }

                case RepeatMoveBytes:
                    assert operands[inst.x().index].asRegister().equals(AMD64.rsi) : "wrong input x: " + operands[inst.x().index];
                    assert operands[inst.y().index].asRegister().equals(AMD64.rdi) : "wrong input y: " + operands[inst.y().index];
                    assert operands[inst.z().index].asRegister().equals(AMD64.rcx) : "wrong input z: " + operands[inst.z().index];
                    masm.repeatMoveBytes();
                    break;

                case RepeatMoveWords:
                    assert operands[inst.x().index].asRegister().equals(AMD64.rsi) : "wrong input x: " + operands[inst.x().index];
                    assert operands[inst.y().index].asRegister().equals(AMD64.rdi) : "wrong input y: " + operands[inst.y().index];
                    assert operands[inst.z().index].asRegister().equals(AMD64.rcx) : "wrong input z: " + operands[inst.z().index];
                    masm.repeatMoveWords();
                    break;

                case PointerCAS:
                    assert operands[inst.x().index].asRegister().equals(AMD64.rax) : "wrong input x: " + operands[inst.x().index];

                    CiValue exchangedVal = operands[inst.y().index];
                    CiValue exchangedAddress = operands[inst.x().index];
                    CiRegisterValue pointerRegister = assureInRegister(tasm, masm, exchangedAddress);
                    CiAddress addr = new CiAddress(tasm.target.wordKind, pointerRegister);

                    if ((Boolean) inst.extra && info != null) {
                        tasm.recordImplicitException(masm.codeBuffer.position(), info);
                    }
                    masm.cmpxchgq(exchangedVal.asRegister(), addr);

                    break;

                case CallStub: {
                    XirTemplate stubId = (XirTemplate) inst.extra;
                    CiValue result = CiValue.IllegalValue;
                    if (inst.result != null) {
                        result = operands[inst.result.index];
                    }
                    CiValue[] args = new CiValue[inst.arguments.length];
                    for (int i = 0; i < args.length; i++) {
                        args[i] = operands[inst.arguments[i].index];
                    }
                    AMD64CallOpcode.callStub(tasm, masm, tasm.compilation.compiler.lookupStub(stubId), stubId.resultOperand.kind, info, result, args);
                    break;
                }
                case CallRuntime: {
                    CiKind[] signature = new CiKind[inst.arguments.length];
                    for (int i = 0; i < signature.length; i++) {
                        signature[i] = inst.arguments[i].kind;
                    }

                    CiCallingConvention cc = tasm.compilation.registerConfig.getCallingConvention(RuntimeCall, signature, tasm.target, false);
                    tasm.compilation.frameMap().adjustOutgoingStackSize(cc, RuntimeCall);
                    for (int i = 0; i < inst.arguments.length; i++) {
                        CiValue argumentLocation = cc.locations[i];
                        CiValue argumentSourceLocation = operands[inst.arguments[i].index];
                        if (argumentLocation != argumentSourceLocation) {
                            AMD64MoveOpcode.move(tasm, masm, argumentLocation, argumentSourceLocation);
                        }
                    }

                    RuntimeCallInformation runtimeCallInformation = (RuntimeCallInformation) inst.extra;
                    AMD64CallOpcode.directCall(tasm, masm, runtimeCallInformation.target, (runtimeCallInformation.useInfoAfter) ? infoAfter : info);

                    if (inst.result != null && inst.result.kind != CiKind.Illegal && inst.result.kind != CiKind.Void) {
                        CiRegister returnRegister = tasm.compilation.registerConfig.getReturnRegister(inst.result.kind);
                        CiValue resultLocation = returnRegister.asValue(inst.result.kind.stackKind());
                        AMD64MoveOpcode.move(tasm, masm, operands[inst.result.index], resultLocation);
                    }
                    break;
                }
                case Jmp: {
                    if (inst.extra instanceof XirLabel) {
                        Label label = labels[((XirLabel) inst.extra).index];
                        masm.jmp(label);
                    } else {
                        AMD64CallOpcode.directJmp(tasm, masm, inst.extra);
                    }
                    break;
                }
                case DecAndJumpNotZero: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    CiValue value = operands[inst.x().index];
                    if (value.kind == CiKind.Long) {
                        masm.decq(value.asRegister());
                    } else {
                        assert value.kind == CiKind.Int;
                        masm.decl(value.asRegister());
                    }
                    masm.jcc(ConditionFlag.notZero, label);
                    break;
                }
                case Jeq: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    emitXirCompare(tasm, masm, inst, Condition.EQ, ConditionFlag.equal, operands, label);
                    break;
                }
                case Jneq: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    emitXirCompare(tasm, masm, inst, Condition.NE, ConditionFlag.notEqual, operands, label);
                    break;
                }

                case Jgt: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    emitXirCompare(tasm, masm, inst, Condition.GT, ConditionFlag.greater, operands, label);
                    break;
                }

                case Jgteq: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    emitXirCompare(tasm, masm, inst, Condition.GE, ConditionFlag.greaterEqual, operands, label);
                    break;
                }

                case Jugteq: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    emitXirCompare(tasm, masm, inst, Condition.AE, ConditionFlag.aboveEqual, operands, label);
                    break;
                }

                case Jlt: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    emitXirCompare(tasm, masm, inst, Condition.LT, ConditionFlag.less, operands, label);
                    break;
                }

                case Jlteq: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    emitXirCompare(tasm, masm, inst, Condition.LE, ConditionFlag.lessEqual, operands, label);
                    break;
                }

                case Jbset: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    CiValue pointer = operands[inst.x().index];
                    CiValue offset = operands[inst.y().index];
                    CiValue bit = operands[inst.z().index];
                    assert offset.isConstant() && bit.isConstant();
                    CiConstant constantOffset = (CiConstant) offset;
                    CiConstant constantBit = (CiConstant) bit;
                    CiAddress src = new CiAddress(inst.kind, pointer, constantOffset.asInt());
                    masm.btli(src, constantBit.asInt());
                    masm.jcc(ConditionFlag.aboveEqual, label);
                    break;
                }

                case Bind: {
                    XirLabel l = (XirLabel) inst.extra;
                    Label label = labels[l.index];
                    masm.bind(label);
                    break;
                }
                case Safepoint: {
                    assert info != null : "Must have debug info in order to create a safepoint.";
                    tasm.recordSafepoint(masm.codeBuffer.position(), info);
                    break;
                }
                case NullCheck: {
                    tasm.recordImplicitException(masm.codeBuffer.position(), info);
                    CiValue pointer = operands[inst.x().index];
                    masm.nullCheck(pointer.asRegister());
                    break;
                }
                case Align: {
                    masm.align((Integer) inst.extra);
                    break;
                }
                case StackOverflowCheck: {
                    int frameSize = tasm.compilation.frameMap().frameSize();
                    int lastFramePage = frameSize / tasm.target.pageSize;
                    // emit multiple stack bangs for methods with frames larger than a page
                    for (int i = 0; i <= lastFramePage; i++) {
                        int offset = (i + GraalOptions.StackShadowPages) * tasm.target.pageSize;
                        // Deduct 'frameSize' to handle frames larger than the shadow
                        bangStackWithOffset(tasm, masm, offset - frameSize);
                    }
                    break;
                }
                case PushFrame: {
                    int frameSize = tasm.compilation.frameMap().frameSize();
                    masm.decrementq(AMD64.rsp, frameSize); // does not emit code for frameSize == 0
                    if (GraalOptions.ZapStackOnMethodEntry) {
                        final int intSize = 4;
                        for (int i = 0; i < frameSize / intSize; ++i) {
                            masm.movl(new CiAddress(CiKind.Int, AMD64.rsp.asValue(), i * intSize), 0xC1C1C1C1);
                        }
                    }
                    CiCalleeSaveLayout csl = tasm.compilation.registerConfig.getCalleeSaveLayout();
                    if (csl != null && csl.size != 0) {
                        int frameToCSA = tasm.compilation.frameMap().offsetToCalleeSaveAreaStart();
                        assert frameToCSA >= 0;
                        masm.save(csl, frameToCSA);
                    }
                    break;
                }
                case PopFrame: {
                    int frameSize = tasm.compilation.frameMap().frameSize();

                    CiCalleeSaveLayout csl = tasm.compilation.registerConfig.getCalleeSaveLayout();
                    if (csl != null && csl.size != 0) {
                        tasm.targetMethod.setRegisterRestoreEpilogueOffset(masm.codeBuffer.position());
                        // saved all registers, restore all registers
                        int frameToCSA = tasm.compilation.frameMap().offsetToCalleeSaveAreaStart();
                        masm.restore(csl, frameToCSA);
                    }

                    masm.incrementq(AMD64.rsp, frameSize);
                    break;
                }
                case Push: {
                    CiRegisterValue value = assureInRegister(tasm, masm, operands[inst.x().index]);
                    masm.push(value.asRegister());
                    break;
                }
                case Pop: {
                    CiValue result = operands[inst.result.index];
                    if (result.isRegister()) {
                        masm.pop(result.asRegister());
                    } else {
                        CiRegister rscratch = tasm.compilation.registerConfig.getScratchRegister();
                        masm.pop(rscratch);
                        AMD64MoveOpcode.move(tasm, masm, result, rscratch.asValue());
                    }
                    break;
                }
                case Mark: {
                    XirMark xmark = (XirMark) inst.extra;
                    Mark[] references = new Mark[xmark.references.length];
                    for (int i = 0; i < references.length; i++) {
                        references[i] = marks.get(xmark.references[i]);
                        assert references[i] != null;
                    }
                    Mark mark = tasm.recordMark(xmark.id, references);
                    marks.put(xmark, mark);
                    break;
                }
                case Nop: {
                    for (int i = 0; i < (Integer) inst.extra; i++) {
                        masm.nop();
                    }
                    break;
                }
                case RawBytes: {
                    for (byte b : (byte[]) inst.extra) {
                        masm.codeBuffer.emitByte(b & 0xff);
                    }
                    break;
                }
                case ShouldNotReachHere: {
                    AMD64CallOpcode.shouldNotReachHere(tasm, masm);
                    break;
                }
                default:
                    throw Util.shouldNotReachHere("Unknown XIR operation " + inst.op);
            }
        }
    }

    private static void emitXirViaLir(TargetMethodAssembler tasm, AMD64MacroAssembler masm, LIROpcode intOp, LIROpcode longOp, LIROpcode floatOp, LIROpcode doubleOp, CiValue left, CiValue right, CiValue result) {
        LIROpcode code;
        switch (result.kind) {
            case Int: code = intOp; break;
            case Long: code = longOp; break;
            case Float: code = floatOp; break;
            case Double: code = doubleOp; break;
            default: throw Util.shouldNotReachHere();
        }

        if (code instanceof AMD64ArithmeticOpcode) {
            ((AMD64ArithmeticOpcode) code).emit(tasm, masm, left, right);
        } else if (code instanceof AMD64MulOpcode) {
            ((AMD64MulOpcode) code).emit(tasm, masm, left, right);
        } else if (code instanceof AMD64DivOpcode) {
            ((AMD64DivOpcode) code).emit(tasm, masm, tasm.asRegister(result), null, tasm.asRegister(left), tasm.asRegister(right));
        } else if (code instanceof AMD64ShiftOpcode) {
            ((AMD64ShiftOpcode) code).emit(tasm, masm, left, right);
        }
    }

    private static void emitXirCompare(TargetMethodAssembler tasm, AMD64MacroAssembler masm, XirInstruction inst, Condition condition, ConditionFlag cflag, CiValue[] ops, Label label) {
        CiValue x = ops[inst.x().index];
        CiValue y = ops[inst.y().index];
        AMD64CompareOpcode code;
        switch (x.kind) {
            case Int: code = AMD64CompareOpcode.ICMP; break;
            case Long: code = AMD64CompareOpcode.LCMP; break;
            case Object: code = AMD64CompareOpcode.ACMP; break;
            case Float: code = AMD64CompareOpcode.FCMP; break;
            case Double: code = AMD64CompareOpcode.DCMP; break;
            default: throw Util.shouldNotReachHere();
        }
        code.emit(tasm, masm, x, y);
        masm.jcc(cflag, label);
    }

    /**
     * @param offset the offset RSP at which to bang. Note that this offset is relative to RSP after RSP has been
     *            adjusted to allocated the frame for the method. It denotes an offset "down" the stack.
     *            For very large frames, this means that the offset may actually be negative (i.e. denoting
     *            a slot "up" the stack above RSP).
     */
    private static void bangStackWithOffset(TargetMethodAssembler tasm, AMD64MacroAssembler masm, int offset) {
        masm.movq(new CiAddress(tasm.target.wordKind, AMD64.RSP, -offset), AMD64.rax);
    }

    private static CiValue assureNot64BitConstant(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue value) {
        if (value.isConstant() && (value.kind == CiKind.Long || value.kind == CiKind.Object)) {
            CiRegisterValue register = tasm.compilation.registerConfig.getScratchRegister().asValue(value.kind);
            AMD64MoveOpcode.move(tasm, masm, register, value);
            return register;
        }
        return value;
    }

    private static CiRegisterValue assureInRegister(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue pointer) {
        if (pointer.isConstant()) {
            CiRegisterValue register = tasm.compilation.registerConfig.getScratchRegister().asValue(pointer.kind);
            AMD64MoveOpcode.move(tasm, masm, register, pointer);
            return register;
        }

        assert pointer.isRegister() : "should be register, but is: " + pointer;
        return (CiRegisterValue) pointer;
    }
}
