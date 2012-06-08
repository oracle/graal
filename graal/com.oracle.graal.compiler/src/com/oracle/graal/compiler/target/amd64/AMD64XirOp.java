/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.target.amd64;

import static com.oracle.graal.api.code.CiCallingConvention.Type.*;
import static com.oracle.graal.api.code.CiValueUtil.*;
import static com.oracle.graal.api.meta.Value.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CiTargetMethod.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.asm.target.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.max.cri.xir.*;
import com.oracle.max.cri.xir.CiXirAssembler.RuntimeCallInformation;
import com.oracle.max.cri.xir.CiXirAssembler.XirInstruction;
import com.oracle.max.cri.xir.CiXirAssembler.XirLabel;
import com.oracle.max.cri.xir.CiXirAssembler.XirMark;

public class AMD64XirOp extends LIRXirInstruction {
    public AMD64XirOp(XirSnippet snippet, Value[] operands, Value outputOperand, Value[] inputs, Value[] temps, int[] inputOperandIndices, int[] tempOperandIndices, int outputOperandIndex,
                        LIRDebugInfo info, LIRDebugInfo infoAfter, LabelRef trueSuccessor, LabelRef falseSuccessor) {
        super("XIR", snippet, operands, outputOperand, inputs, temps, inputOperandIndices, tempOperandIndices, outputOperandIndex, info, infoAfter, trueSuccessor, falseSuccessor);
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm) {
        AMD64MacroAssembler masm = (AMD64MacroAssembler) tasm.asm;

        Label endLabel = null;
        Label[] labels = new Label[snippet.template.labels.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = new Label();
            if (snippet.template.labels[i].name == XirLabel.TrueSuccessor) {
                if (trueSuccessor == null) {
                    assert endLabel == null;
                    endLabel = new Label();
                    labels[i] = endLabel;
                } else {
                    labels[i] = trueSuccessor.label();
                }
            } else if (snippet.template.labels[i].name == XirLabel.FalseSuccessor) {
                if (falseSuccessor == null) {
                    assert endLabel == null;
                    endLabel = new Label();
                    labels[i] = endLabel;
                } else {
                    labels[i] = falseSuccessor.label();
                }
            }
        }
        emitXirInstructions(tasm, masm, snippet.template.fastPath, labels, getOperands(), snippet.marks);
        if (endLabel != null) {
            masm.bind(endLabel);
        }

        if (snippet.template.slowPath != null) {
            tasm.stubs.add(new SlowPath(labels));
        }
    }

    private class SlowPath extends AMD64Code {
        public final Label[] labels;

        public SlowPath(Label[] labels) {
            this.labels = labels;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            emitXirInstructions(tasm, masm, snippet.template.slowPath, labels, getOperands(), snippet.marks);
            masm.nop();
        }

        @Override
        public String description() {
            return "slow path for " + snippet.template.name;
        }
    }


    protected void emitXirInstructions(TargetMethodAssembler tasm, AMD64MacroAssembler masm, XirInstruction[] instructions, Label[] labels, Value[] operands, Map<XirMark, Mark> marks) {
        for (XirInstruction inst : instructions) {
            switch (inst.op) {
                case Add:
                    emitXirViaLir(tasm, masm, AMD64Arithmetic.IADD, AMD64Arithmetic.LADD, AMD64Arithmetic.FADD, AMD64Arithmetic.DADD, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Sub:
                    emitXirViaLir(tasm, masm, AMD64Arithmetic.ISUB, AMD64Arithmetic.LSUB, AMD64Arithmetic.FSUB, AMD64Arithmetic.DSUB, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Div:
                    emitXirViaLir(tasm, masm, AMD64Arithmetic.IDIV, AMD64Arithmetic.LDIV, AMD64Arithmetic.FDIV, AMD64Arithmetic.DDIV, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Mul:
                    emitXirViaLir(tasm, masm, AMD64Arithmetic.IMUL, AMD64Arithmetic.LMUL, AMD64Arithmetic.FMUL, AMD64Arithmetic.DMUL, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Mod:
                    emitXirViaLir(tasm, masm, AMD64Arithmetic.IREM, AMD64Arithmetic.LREM, null, null, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Shl:
                    emitXirViaLir(tasm, masm, AMD64Arithmetic.ISHL, AMD64Arithmetic.LSHL, null, null, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Sar:
                    emitXirViaLir(tasm, masm, AMD64Arithmetic.ISHR, AMD64Arithmetic.LSHR, null, null, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Shr:
                    emitXirViaLir(tasm, masm, AMD64Arithmetic.IUSHR, AMD64Arithmetic.LUSHR, null, null, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case And:
                    emitXirViaLir(tasm, masm, AMD64Arithmetic.IAND, AMD64Arithmetic.LAND, null, null, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Or:
                    emitXirViaLir(tasm, masm, AMD64Arithmetic.IOR, AMD64Arithmetic.LOR, null, null, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Xor:
                    emitXirViaLir(tasm, masm, AMD64Arithmetic.IXOR, AMD64Arithmetic.LXOR, null, null, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Mov: {
                    Value result = operands[inst.result.index];
                    Value source = operands[inst.x().index];
                    AMD64Move.move(tasm, masm, result, source);
                    break;
                }

                case PointerLoad: {
                    Value result = operands[inst.result.index];
                    Value pointer = operands[inst.x().index];
                    CiRegisterValue register = assureInRegister(tasm, masm, pointer);

                    AMD64Move.load(tasm, masm, result, new CiAddress(inst.kind, register), (Boolean) inst.extra ? info : null);
                    break;
                }

                case PointerStore: {
                    Value value = assureNot64BitConstant(tasm, masm, operands[inst.y().index]);
                    Value pointer = operands[inst.x().index];
                    assert isRegister(pointer);

                    AMD64Move.store(tasm, masm, new CiAddress(inst.kind, pointer), value, (Boolean) inst.extra ? info : null);
                    break;
                }

                case PointerLoadDisp: {
                    CiXirAssembler.AddressAccessInformation addressInformation = (CiXirAssembler.AddressAccessInformation) inst.extra;
                    boolean canTrap = addressInformation.canTrap;

                    CiAddress.Scale scale = addressInformation.scale;
                    int displacement = addressInformation.disp;

                    Value result = operands[inst.result.index];
                    Value pointer = operands[inst.x().index];
                    Value index = operands[inst.y().index];

                    pointer = assureInRegister(tasm, masm, pointer);
                    assert isRegister(pointer);

                    CiAddress src;
                    if (isConstant(index)) {
                        assert index.kind == Kind.Int;
                        Constant constantIndex = (Constant) index;
                        src = new CiAddress(inst.kind, pointer, constantIndex.asInt() * scale.value + displacement);
                    } else {
                        src = new CiAddress(inst.kind, pointer, index, scale, displacement);
                    }

                    AMD64Move.load(tasm, masm, result, src, canTrap ? info : null);
                    break;
                }

                case LoadEffectiveAddress: {
                    CiXirAssembler.AddressAccessInformation addressInformation = (CiXirAssembler.AddressAccessInformation) inst.extra;

                    CiAddress.Scale scale = addressInformation.scale;
                    int displacement = addressInformation.disp;

                    Value result = operands[inst.result.index];
                    Value pointer = operands[inst.x().index];
                    Value index = operands[inst.y().index];

                    pointer = assureInRegister(tasm, masm, pointer);
                    assert isRegister(pointer);
                    CiAddress src = new CiAddress(Kind.Illegal, pointer, index, scale, displacement);
                    masm.leaq(asRegister(result), src);
                    break;
                }

                case PointerStoreDisp: {
                    CiXirAssembler.AddressAccessInformation addressInformation = (CiXirAssembler.AddressAccessInformation) inst.extra;
                    boolean canTrap = addressInformation.canTrap;

                    CiAddress.Scale scale = addressInformation.scale;
                    int displacement = addressInformation.disp;

                    Value value = assureNot64BitConstant(tasm, masm, operands[inst.z().index]);
                    Value pointer = operands[inst.x().index];
                    Value index = operands[inst.y().index];

                    pointer = assureInRegister(tasm, masm, pointer);
                    assert isRegister(pointer);

                    CiAddress dst;
                    if (isConstant(index)) {
                        assert index.kind == Kind.Int;
                        Constant constantIndex = (Constant) index;
                        dst = new CiAddress(inst.kind, pointer, IllegalValue, scale, constantIndex.asInt() * scale.value + displacement);
                    } else {
                        dst = new CiAddress(inst.kind, pointer, index, scale, displacement);
                    }

                    AMD64Move.store(tasm, masm, dst, value, canTrap ? info : null);
                    break;
                }

                case RepeatMoveBytes:
                    assert asRegister(operands[inst.x().index]).equals(AMD64.rsi) : "wrong input x: " + operands[inst.x().index];
                    assert asRegister(operands[inst.y().index]).equals(AMD64.rdi) : "wrong input y: " + operands[inst.y().index];
                    assert asRegister(operands[inst.z().index]).equals(AMD64.rcx) : "wrong input z: " + operands[inst.z().index];
                    masm.repeatMoveBytes();
                    break;

                case RepeatMoveWords:
                    assert asRegister(operands[inst.x().index]).equals(AMD64.rsi) : "wrong input x: " + operands[inst.x().index];
                    assert asRegister(operands[inst.y().index]).equals(AMD64.rdi) : "wrong input y: " + operands[inst.y().index];
                    assert asRegister(operands[inst.z().index]).equals(AMD64.rcx) : "wrong input z: " + operands[inst.z().index];
                    masm.repeatMoveWords();
                    break;

                case PointerCAS:
                    assert asRegister(operands[inst.x().index]).equals(AMD64.rax) : "wrong input x: " + operands[inst.x().index];

                    Value exchangedVal = operands[inst.y().index];
                    Value exchangedAddress = operands[inst.x().index];
                    CiRegisterValue pointerRegister = assureInRegister(tasm, masm, exchangedAddress);
                    CiAddress addr = new CiAddress(tasm.target.wordKind, pointerRegister);

                    if ((Boolean) inst.extra && info != null) {
                        tasm.recordImplicitException(masm.codeBuffer.position(), info);
                    }
                    masm.cmpxchgq(asRegister(exchangedVal), addr);

                    break;

                case CallRuntime: {
                    Kind[] signature = new Kind[inst.arguments.length];
                    for (int i = 0; i < signature.length; i++) {
                        signature[i] = inst.arguments[i].kind;
                    }

                    CiCallingConvention cc = tasm.frameMap.registerConfig.getCallingConvention(RuntimeCall, signature, tasm.target, false);
                    for (int i = 0; i < inst.arguments.length; i++) {
                        Value argumentLocation = cc.locations[i];
                        Value argumentSourceLocation = operands[inst.arguments[i].index];
                        if (argumentLocation != argumentSourceLocation) {
                            AMD64Move.move(tasm, masm, argumentLocation, argumentSourceLocation);
                        }
                    }

                    RuntimeCallInformation runtimeCallInformation = (RuntimeCallInformation) inst.extra;
                    AMD64Call.directCall(tasm, masm, runtimeCallInformation.target, (runtimeCallInformation.useInfoAfter) ? infoAfter : info);

                    if (inst.result != null && inst.result.kind != Kind.Illegal && inst.result.kind != Kind.Void) {
                        CiRegister returnRegister = tasm.frameMap.registerConfig.getReturnRegister(inst.result.kind);
                        Value resultLocation = returnRegister.asValue(inst.result.kind.stackKind());
                        AMD64Move.move(tasm, masm, operands[inst.result.index], resultLocation);
                    }
                    break;
                }
                case Jmp: {
                    if (inst.extra instanceof XirLabel) {
                        Label label = labels[((XirLabel) inst.extra).index];
                        masm.jmp(label);
                    } else {
                        AMD64Call.directJmp(tasm, masm, inst.extra);
                    }
                    break;
                }
                case DecAndJumpNotZero: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    Value value = operands[inst.x().index];
                    if (value.kind == Kind.Long) {
                        masm.decq(asRegister(value));
                    } else {
                        assert value.kind == Kind.Int;
                        masm.decl(asRegister(value));
                    }
                    masm.jcc(ConditionFlag.notZero, label);
                    break;
                }
                case Jeq: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    emitXirCompare(tasm, masm, inst, ConditionFlag.equal, operands, label);
                    break;
                }
                case Jneq: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    emitXirCompare(tasm, masm, inst, ConditionFlag.notEqual, operands, label);
                    break;
                }

                case Jgt: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    emitXirCompare(tasm, masm, inst, ConditionFlag.greater, operands, label);
                    break;
                }

                case Jgteq: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    emitXirCompare(tasm, masm, inst, ConditionFlag.greaterEqual, operands, label);
                    break;
                }

                case Jugteq: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    emitXirCompare(tasm, masm, inst, ConditionFlag.aboveEqual, operands, label);
                    break;
                }

                case Jlt: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    emitXirCompare(tasm, masm, inst, ConditionFlag.less, operands, label);
                    break;
                }

                case Jlteq: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    emitXirCompare(tasm, masm, inst, ConditionFlag.lessEqual, operands, label);
                    break;
                }

                case Jbset: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    Value pointer = operands[inst.x().index];
                    Value offset = operands[inst.y().index];
                    Value bit = operands[inst.z().index];
                    assert isConstant(offset) && isConstant(bit);
                    Constant constantOffset = (Constant) offset;
                    Constant constantBit = (Constant) bit;
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
                    Value pointer = operands[inst.x().index];
                    masm.nullCheck(asRegister(pointer));
                    break;
                }
                case Push: {
                    CiRegisterValue value = assureInRegister(tasm, masm, operands[inst.x().index]);
                    masm.push(asRegister(value));
                    break;
                }
                case Pop: {
                    Value result = operands[inst.result.index];
                    if (isRegister(result)) {
                        masm.pop(asRegister(result));
                    } else {
                        CiRegister rscratch = tasm.frameMap.registerConfig.getScratchRegister();
                        masm.pop(rscratch);
                        AMD64Move.move(tasm, masm, result, rscratch.asValue());
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
                case ShouldNotReachHere: {
                    AMD64Call.shouldNotReachHere(tasm, masm);
                    break;
                }
                default:
                    throw GraalInternalError.shouldNotReachHere("Unknown XIR operation " + inst.op);
            }
        }
    }

    private static void emitXirViaLir(TargetMethodAssembler tasm, AMD64MacroAssembler masm, AMD64Arithmetic intOp, AMD64Arithmetic longOp, AMD64Arithmetic floatOp,
                    AMD64Arithmetic doubleOp, Value left, Value right, Value result) {
        AMD64Arithmetic code;
        switch (result.kind) {
            case Int: code = intOp; break;
            case Long: code = longOp; break;
            case Float: code = floatOp; break;
            case Double: code = doubleOp; break;
            default: throw GraalInternalError.shouldNotReachHere();
        }
        assert left == result;
        if (isRegister(right) && right.kind != result.kind) {
            // XIR is not strongly typed, so we can have a type mismatch that we have to fix here.
            AMD64Arithmetic.emit(tasm, masm, code, result, asRegister(right).asValue(result.kind), null);
        } else {
            AMD64Arithmetic.emit(tasm, masm, code, result, right, null);
        }
    }

    private static void emitXirCompare(TargetMethodAssembler tasm, AMD64MacroAssembler masm, XirInstruction inst, ConditionFlag cflag, Value[] ops, Label label) {
        Value x = ops[inst.x().index];
        Value y = ops[inst.y().index];
        AMD64Compare code;
        switch (x.kind) {
            case Int: code = AMD64Compare.ICMP; break;
            case Long: code = AMD64Compare.LCMP; break;
            case Object: code = AMD64Compare.ACMP; break;
            case Float: code = AMD64Compare.FCMP; break;
            case Double: code = AMD64Compare.DCMP; break;
            default: throw GraalInternalError.shouldNotReachHere();
        }
        AMD64Compare.emit(tasm, masm, code, x, y);
        masm.jcc(cflag, label);
    }

    private static Value assureNot64BitConstant(TargetMethodAssembler tasm, AMD64MacroAssembler masm, Value value) {
        if (isConstant(value) && (value.kind == Kind.Long || value.kind == Kind.Object)) {
            CiRegisterValue register = tasm.frameMap.registerConfig.getScratchRegister().asValue(value.kind);
            AMD64Move.move(tasm, masm, register, value);
            return register;
        }
        return value;
    }

    private static CiRegisterValue assureInRegister(TargetMethodAssembler tasm, AMD64MacroAssembler masm, Value pointer) {
        if (isConstant(pointer)) {
            CiRegisterValue register = tasm.frameMap.registerConfig.getScratchRegister().asValue(pointer.kind);
            AMD64Move.move(tasm, masm, register, pointer);
            return register;
        }

        assert isRegister(pointer) : "should be register, but is: " + pointer;
        return (CiRegisterValue) pointer;
    }
}
