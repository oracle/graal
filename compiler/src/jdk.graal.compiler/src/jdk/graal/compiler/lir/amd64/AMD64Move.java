/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.lir.amd64;

import static java.lang.Double.doubleToRawLongBits;
import static java.lang.Float.floatToRawIntBits;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Equal;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.NotEqual;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64MIOp;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.spi.LIRKindTool;
import jdk.graal.compiler.core.common.type.DataPointerConstant;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

public class AMD64Move {

    private abstract static class AbstractMoveOp extends AMD64LIRInstruction implements StandardOp.ValueMoveOp {
        public static final LIRInstructionClass<AbstractMoveOp> TYPE = LIRInstructionClass.create(AbstractMoveOp.class);

        private AMD64Kind moveKind;

        protected AbstractMoveOp(LIRInstructionClass<? extends AbstractMoveOp> c, AMD64Kind moveKind) {
            super(c);
            this.moveKind = moveKind;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            move(moveKind, crb, masm, getResult(), getInput());
        }
    }

    @Opcode("MOVE")
    public static final class MoveToRegOp extends AbstractMoveOp {
        public static final LIRInstructionClass<MoveToRegOp> TYPE = LIRInstructionClass.create(MoveToRegOp.class);

        @Def({OperandFlag.REG, OperandFlag.STACK, OperandFlag.HINT}) protected AllocatableValue result;
        @Use({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue input;

        public MoveToRegOp(AMD64Kind moveKind, AllocatableValue result, AllocatableValue input) {
            super(TYPE, moveKind);
            this.result = result;
            this.input = input;
        }

        @Override
        public AllocatableValue getInput() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }
    }

    @Opcode("MOVE")
    public static final class MoveFromRegOp extends AbstractMoveOp {
        public static final LIRInstructionClass<MoveFromRegOp> TYPE = LIRInstructionClass.create(MoveFromRegOp.class);

        @Def({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue result;
        @Use({OperandFlag.REG, OperandFlag.HINT}) protected AllocatableValue input;

        public MoveFromRegOp(AMD64Kind moveKind, AllocatableValue result, AllocatableValue input) {
            super(TYPE, moveKind);
            this.result = result;
            this.input = input;
        }

        @Override
        public AllocatableValue getInput() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }
    }

    @Opcode("MOVE")
    public static class MoveFromConstOp extends AMD64LIRInstruction implements StandardOp.LoadConstantOp {
        public static final LIRInstructionClass<MoveFromConstOp> TYPE = LIRInstructionClass.create(MoveFromConstOp.class);

        @Def({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue result;
        private final JavaConstant input;

        public MoveFromConstOp(AllocatableValue result, JavaConstant input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(result)) {
                const2reg(crb, masm, asRegister(result), input, (AMD64Kind) result.getPlatformKind());
            } else {
                assert isStackSlot(result);
                const2stack(crb, masm, result, input);
            }
        }

        @Override
        public Constant getConstant() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }
    }

    @Opcode("STACKMOVE")
    public static final class AMD64StackMove extends AMD64LIRInstruction implements StandardOp.ValueMoveOp {
        public static final LIRInstructionClass<AMD64StackMove> TYPE = LIRInstructionClass.create(AMD64StackMove.class);

        @Def({OperandFlag.STACK}) protected AllocatableValue result;
        @Use({OperandFlag.STACK, OperandFlag.HINT}) protected AllocatableValue input;
        @Alive({OperandFlag.STACK, OperandFlag.UNINITIALIZED}) private AllocatableValue backupSlot;

        private Register scratch;

        public AMD64StackMove(AllocatableValue result, AllocatableValue input, Register scratch, AllocatableValue backupSlot) {
            super(TYPE);
            this.result = result;
            this.input = input;
            this.backupSlot = backupSlot;
            this.scratch = scratch;
        }

        @Override
        public AllocatableValue getInput() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }

        public Register getScratchRegister() {
            return scratch;
        }

        public AllocatableValue getBackupSlot() {
            return backupSlot;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind backupKind = (AMD64Kind) backupSlot.getPlatformKind();
            if (backupKind.isXMM()) {
                // graal doesn't use vector values, so it's safe to backup using DOUBLE
                backupKind = AMD64Kind.DOUBLE;
            }

            // backup scratch register
            reg2stack(backupKind, crb, masm, backupSlot, scratch);
            // move stack slot
            stack2reg((AMD64Kind) getInput().getPlatformKind(), crb, masm, scratch, getInput());
            reg2stack((AMD64Kind) getResult().getPlatformKind(), crb, masm, getResult(), scratch);
            // restore scratch register
            stack2reg(backupKind, crb, masm, scratch, backupSlot);
        }
    }

    @Opcode("MULTISTACKMOVE")
    public static final class AMD64MultiStackMove extends AMD64LIRInstruction {
        public static final LIRInstructionClass<AMD64MultiStackMove> TYPE = LIRInstructionClass.create(AMD64MultiStackMove.class);

        @Def({OperandFlag.STACK}) protected AllocatableValue[] results;
        @Use({OperandFlag.STACK}) protected Value[] inputs;
        @Alive({OperandFlag.STACK, OperandFlag.UNINITIALIZED}) private AllocatableValue backupSlot;

        private Register scratch;

        public AMD64MultiStackMove(AllocatableValue[] results, Value[] inputs, Register scratch, AllocatableValue backupSlot) {
            super(TYPE);
            this.results = results;
            this.inputs = inputs;
            this.backupSlot = backupSlot;
            this.scratch = scratch;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind backupKind = (AMD64Kind) backupSlot.getPlatformKind();
            if (backupKind.isXMM()) {
                // graal doesn't use vector values, so it's safe to backup using DOUBLE
                backupKind = AMD64Kind.DOUBLE;
            }

            // backup scratch register
            move(backupKind, crb, masm, backupSlot, scratch.asValue(backupSlot.getValueKind()));
            for (int i = 0; i < results.length; i++) {
                Value input = inputs[i];
                AllocatableValue result = results[i];
                // move stack slot
                move((AMD64Kind) input.getPlatformKind(), crb, masm, scratch.asValue(input.getValueKind()), input);
                move((AMD64Kind) result.getPlatformKind(), crb, masm, result, scratch.asValue(result.getValueKind()));
            }
            // restore scratch register
            move(backupKind, crb, masm, scratch.asValue(backupSlot.getValueKind()), backupSlot);
        }
    }

    public static final class LeaOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<LeaOp> TYPE = LIRInstructionClass.create(LeaOp.class);

        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Use({OperandFlag.COMPOSITE, OperandFlag.UNINITIALIZED}) protected AMD64AddressValue address;
        private final OperandSize size;

        public LeaOp(AllocatableValue result, AMD64AddressValue address, OperandSize size) {
            super(TYPE);
            this.result = result;
            this.address = address;
            this.size = size;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (size == OperandSize.QWORD) {
                masm.leaq(asRegister(result, AMD64Kind.QWORD), address.toAddress());
            } else {
                assert size == OperandSize.DWORD : size;
                masm.lead(asRegister(result, AMD64Kind.DWORD), address.toAddress());
            }
        }
    }

    public static final class LeaDataOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<LeaDataOp> TYPE = LIRInstructionClass.create(LeaDataOp.class);

        @Def({OperandFlag.REG}) protected AllocatableValue result;
        private final DataPointerConstant data;

        public LeaDataOp(AllocatableValue result, DataPointerConstant data) {
            super(TYPE);
            this.result = result;
            this.data = data;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            masm.leaq(asRegister(result), (AMD64Address) crb.recordDataReferenceInCode(data));
        }
    }

    public static final class StackLeaOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<StackLeaOp> TYPE = LIRInstructionClass.create(StackLeaOp.class);

        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Use({OperandFlag.STACK, OperandFlag.UNINITIALIZED}) protected AllocatableValue slot;

        public StackLeaOp(AllocatableValue result, AllocatableValue slot) {
            super(TYPE);
            this.result = result;
            this.slot = slot;
            assert slot instanceof VirtualStackSlot || slot instanceof StackSlot : slot;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            masm.leaq(asRegister(result, AMD64Kind.QWORD), (AMD64Address) crb.asAddress(slot));
        }
    }

    public static final class MembarOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<MembarOp> TYPE = LIRInstructionClass.create(MembarOp.class);

        private final int barriers;

        public MembarOp(final int barriers) {
            super(TYPE);
            this.barriers = barriers;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            masm.membar(barriers);
        }
    }

    public static final class NullCheckOp extends AMD64LIRInstruction implements StandardOp.NullCheck {
        public static final LIRInstructionClass<NullCheckOp> TYPE = LIRInstructionClass.create(NullCheckOp.class);

        @Use({OperandFlag.COMPOSITE}) protected AMD64AddressValue address;
        @State protected LIRFrameState state;

        public NullCheckOp(AMD64AddressValue address, LIRFrameState state) {
            super(TYPE);
            this.address = address;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            crb.recordImplicitException(masm.position(), state);
            masm.nullCheck(address.toAddress());
        }

        @Override
        public Value getCheckedValue() {
            return address.base;
        }

        @Override
        public LIRFrameState getState() {
            return state;
        }
    }

    @Opcode("CAS")
    public static final class CompareAndSwapOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<CompareAndSwapOp> TYPE = LIRInstructionClass.create(CompareAndSwapOp.class);

        private final AMD64Kind accessKind;

        @Def protected AllocatableValue result;
        @Use({OperandFlag.COMPOSITE}) protected AMD64AddressValue address;
        @Use protected AllocatableValue cmpValue;
        @Use protected AllocatableValue newValue;

        public CompareAndSwapOp(AMD64Kind accessKind, AllocatableValue result, AMD64AddressValue address, AllocatableValue cmpValue, AllocatableValue newValue) {
            super(TYPE);
            this.accessKind = accessKind;
            this.result = result;
            this.address = address;
            this.cmpValue = cmpValue;
            this.newValue = newValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            assert asRegister(cmpValue).equals(AMD64.rax) : cmpValue;
            assert asRegister(result).equals(AMD64.rax) : result;

            if (crb.target.isMP) {
                masm.lock();
            }
            switch (accessKind) {
                case BYTE:
                    masm.cmpxchgb(asRegister(newValue), address.toAddress());
                    break;
                case WORD:
                    masm.cmpxchgw(asRegister(newValue), address.toAddress());
                    break;
                case DWORD:
                    masm.cmpxchgl(asRegister(newValue), address.toAddress());
                    break;
                case QWORD:
                    masm.cmpxchgq(asRegister(newValue), address.toAddress());
                    break;
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(accessKind); // ExcludeFromJacocoGeneratedReport
            }
        }
    }

    @Opcode("ATOMIC_READ_AND_ADD")
    public static final class AtomicReadAndAddOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<AtomicReadAndAddOp> TYPE = LIRInstructionClass.create(AtomicReadAndAddOp.class);

        private final AMD64Kind accessKind;

        @Def protected AllocatableValue result;
        @Alive({OperandFlag.COMPOSITE}) protected AMD64AddressValue address;
        @Use protected AllocatableValue delta;

        public AtomicReadAndAddOp(AMD64Kind accessKind, AllocatableValue result, AMD64AddressValue address, AllocatableValue delta) {
            super(TYPE);
            this.accessKind = accessKind;
            this.result = result;
            this.address = address;
            this.delta = delta;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            move(accessKind, crb, masm, result, delta);
            if (crb.target.isMP) {
                masm.lock();
            }
            switch (accessKind) {
                case BYTE:
                    masm.xaddb(address.toAddress(), asRegister(result));
                    break;
                case WORD:
                    masm.xaddw(address.toAddress(), asRegister(result));
                    break;
                case DWORD:
                    masm.xaddl(address.toAddress(), asRegister(result));
                    break;
                case QWORD:
                    masm.xaddq(address.toAddress(), asRegister(result));
                    break;
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(accessKind); // ExcludeFromJacocoGeneratedReport
            }
        }
    }

    @Opcode("ATOMIC_READ_AND_WRITE")
    public static final class AtomicReadAndWriteOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<AtomicReadAndWriteOp> TYPE = LIRInstructionClass.create(AtomicReadAndWriteOp.class);

        private final AMD64Kind accessKind;

        @Def protected AllocatableValue result;
        @Alive({OperandFlag.COMPOSITE}) protected AMD64AddressValue address;
        @Use protected AllocatableValue newValue;

        public AtomicReadAndWriteOp(AMD64Kind accessKind, AllocatableValue result, AMD64AddressValue address, AllocatableValue newValue) {
            super(TYPE);
            this.accessKind = accessKind;
            this.result = result;
            this.address = address;
            this.newValue = newValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            move(accessKind, crb, masm, result, newValue);
            switch (accessKind) {
                case BYTE:
                    masm.xchgb(asRegister(result), address.toAddress());
                    break;
                case WORD:
                    masm.xchgw(asRegister(result), address.toAddress());
                    break;
                case DWORD:
                    masm.xchgl(asRegister(result), address.toAddress());
                    break;
                case QWORD:
                    masm.xchgq(asRegister(result), address.toAddress());
                    break;
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(accessKind); // ExcludeFromJacocoGeneratedReport
            }
        }
    }

    public static void move(CompilationResultBuilder crb, AMD64MacroAssembler masm, Value result, Value input) {
        move((AMD64Kind) result.getPlatformKind(), crb, masm, result, input);
    }

    public static void move(AMD64Kind moveKind, CompilationResultBuilder crb, AMD64MacroAssembler masm, Value result, Value input) {
        if (isRegister(input)) {
            if (isRegister(result)) {
                reg2reg(moveKind, masm, result, input);
                return;
            } else if (isStackSlot(result)) {
                reg2stack(moveKind, crb, masm, result, asRegister(input));
                return;
            }
        } else if (isStackSlot(input)) {
            if (isRegister(result)) {
                stack2reg(moveKind, crb, masm, asRegister(result), input);
                return;
            }
        } else if (LIRValueUtil.isJavaConstant(input)) {
            if (isRegister(result)) {
                const2reg(crb, masm, asRegister(result), LIRValueUtil.asJavaConstant(input), moveKind);
                return;
            } else if (isStackSlot(result)) {
                const2stack(crb, masm, result, LIRValueUtil.asJavaConstant(input));
                return;
            }
        }
        throw GraalError.shouldNotReachHere("input=" + input + " input.class=" + input.getClass().getName() + " " + "result=" + result + " result.class=" + result.getClass().getName()); // ExcludeFromJacocoGeneratedReport
    }

    private static void reg2reg(AMD64Kind kind, AMD64MacroAssembler masm, Value result, Value input) {
        if (asRegister(input).equals(asRegister(result))) {
            return;
        }
        if (!kind.isMask()) {
            /*
             * Non-mask moves are only allowed within a category. Mask moves are also allowed
             * between a mask register and a CPU register (but not between two CPU registers). The
             * definitions of the kmov[bwdq] instructions check all those constraints on mask moves.
             */
            assert asRegister(result).getRegisterCategory().equals(asRegister(input).getRegisterCategory());
        }
        switch (kind) {
            case BYTE:
            case WORD:
            case DWORD:
                masm.movl(asRegister(result), asRegister(input));
                break;
            case QWORD:
                masm.movq(asRegister(result), asRegister(input));
                break;
            case SINGLE:
                masm.movflt(asRegister(result, AMD64Kind.SINGLE), asRegister(input, AMD64Kind.SINGLE));
                break;
            case DOUBLE:
                masm.movdbl(asRegister(result, AMD64Kind.DOUBLE), asRegister(input, AMD64Kind.DOUBLE));
                break;
            case MASK8:
                masm.kmovb(asRegister(result), asRegister(input));
                break;
            case MASK16:
                masm.kmovw(asRegister(result), asRegister(input));
                break;
            case MASK32:
                masm.kmovd(asRegister(result), asRegister(input));
                break;
            case MASK64:
                masm.kmovq(asRegister(result), asRegister(input));
                break;
            default:
                throw GraalError.shouldNotReachHere("kind=" + kind + " input=" + input + " result=" + result); // ExcludeFromJacocoGeneratedReport
        }
    }

    public static void reg2stack(AMD64Kind kind, CompilationResultBuilder crb, AMD64MacroAssembler masm, Value result, Register input) {
        AMD64Address dest = (AMD64Address) crb.asAddress(result);
        switch (kind) {
            case BYTE:
                masm.movb(dest, input);
                break;
            case WORD:
                masm.movw(dest, input);
                break;
            case DWORD:
                masm.movl(dest, input);
                break;
            case QWORD:
                masm.movq(dest, input);
                break;
            case SINGLE:
                masm.movflt(dest, input);
                break;
            case DOUBLE:
                masm.movsd(dest, input);
                break;
            case V128_QWORD:
                masm.movdqu(dest, input);
                break;
            case V256_QWORD:
                masm.vmovdqu(dest, input);
                break;
            case V512_QWORD:
                masm.evmovdqu64(dest, input);
                break;
            case MASK8:
                masm.kmovb(dest, input);
                break;
            case MASK16:
                masm.kmovw(dest, input);
                break;
            case MASK32:
                masm.kmovd(dest, input);
                break;
            case MASK64:
                masm.kmovq(dest, input);
                break;
            default:
                throw GraalError.shouldNotReachHere("kind=" + kind + " input=" + input + " result=" + result); // ExcludeFromJacocoGeneratedReport
        }
    }

    public static void stack2reg(AMD64Kind kind, CompilationResultBuilder crb, AMD64MacroAssembler masm, Register result, Value input) {
        AMD64Address src = (AMD64Address) crb.asAddress(input);
        /*
         * Mask moves from memory can't target CPU registers directly. If such a move is needed, use
         * a general purpose move instead. Note that mask moves have zero extending semantics.
         */
        boolean isMaskToCPU = kind.isMask() && !result.getRegisterCategory().equals(AMD64.MASK);
        switch (kind) {
            case BYTE:
                masm.movsbl(result, src);
                break;
            case WORD:
                masm.movswl(result, src);
                break;
            case DWORD:
                masm.movl(result, src);
                break;
            case QWORD:
                masm.movq(result, src);
                break;
            case SINGLE:
                masm.movflt(result, src);
                break;
            case DOUBLE:
                masm.movdbl(result, src);
                break;
            case V128_QWORD:
                masm.movdqu(result, src);
                break;
            case V256_QWORD:
                masm.vmovdqu(result, src);
                break;
            case V512_QWORD:
                masm.evmovdqu64(result, src);
                break;
            case MASK8:
                if (isMaskToCPU) {
                    masm.movzbl(result, src);
                } else {
                    masm.kmovb(result, src);
                }
                break;
            case MASK16:
                if (isMaskToCPU) {
                    masm.movzwl(result, src);
                } else {
                    masm.kmovw(result, src);
                }
                break;
            case MASK32:
                if (isMaskToCPU) {
                    masm.movl(result, src);
                } else {
                    masm.kmovd(result, src);
                }
                break;
            case MASK64:
                if (isMaskToCPU) {
                    masm.movq(result, src);
                } else {
                    masm.kmovq(result, src);
                }
                break;
            default:
                throw GraalError.shouldNotReachHere("kind=" + kind + " input=" + input + " result=" + result); // ExcludeFromJacocoGeneratedReport
        }
    }

    public static void const2reg(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register result, JavaConstant input, AMD64Kind moveKind) {
        /*
         * Note: we use the kind of the input operand (and not the kind of the result operand)
         * because they don't match in all cases. For example, an object constant can be loaded to a
         * long register when unsafe casts occurred (e.g., for a write barrier where arithmetic
         * operations are then performed on the pointer).
         */
        assert !result.getRegisterCategory().equals(AMD64.MASK) : "no general const-to-mask moves supported";
        switch (input.getJavaKind().getStackKind()) {
            case Int:
                // Do not optimize with an XOR as this instruction may be between
                // a CMP and a Jcc in which case the XOR will modify the condition
                // flags and interfere with the Jcc.
                masm.movl(result, input.asInt());

                break;
            case Long:
                // Do not optimize with an XOR as this instruction may be between
                // a CMP and a Jcc in which case the XOR will modify the condition
                // flags and interfere with the Jcc.
                if (input.asLong() == (int) input.asLong()) {
                    // Sign extended to long
                    masm.movslq(result, (int) input.asLong());
                } else if ((input.asLong() & 0xFFFFFFFFL) == input.asLong()) {
                    // Zero extended to long
                    masm.movl(result, (int) input.asLong());
                } else {
                    masm.movq(result, input.asLong());
                }
                break;
            case Float:
                // This is *not* the same as 'constant == 0.0f' in the case where constant is -0.0f
                if (Float.floatToRawIntBits(input.asFloat()) == Float.floatToRawIntBits(0.0f)) {
                    masm.xorps(result, result);
                } else {
                    masm.movflt(result, (AMD64Address) crb.asFloatConstRef(input));
                }
                break;
            case Double:
                // This is *not* the same as 'constant == 0.0d' in the case where constant is -0.0d
                if (Double.doubleToRawLongBits(input.asDouble()) == Double.doubleToRawLongBits(0.0d)) {
                    masm.xorpd(result, result);
                } else {
                    masm.movdbl(result, (AMD64Address) crb.asDoubleConstRef(input));
                }
                break;
            case Object:
                assert moveKind != null : "a nun-null moveKind is required for loading an object constant";
                // Do not optimize with an XOR as this instruction may be between
                // a CMP and a Jcc in which case the XOR will modify the condition
                // flags and interfere with the Jcc.
                if (input.isNull()) {
                    if (moveKind == AMD64Kind.QWORD && crb.mustReplaceWithUncompressedNullRegister(input)) {
                        masm.movq(result, crb.uncompressedNullRegister);
                    } else {
                        // Upper bits will be zeroed so this also works for narrow oops
                        masm.movslq(result, 0);
                    }
                } else {
                    if (crb.target.inlineObjects) {
                        crb.recordInlineDataInCode(input);
                        if (moveKind == AMD64Kind.DWORD) {
                            // Support for narrow oops
                            masm.movl(result, 0xDEADDEAD, true);
                        } else {
                            masm.movq(result, 0xDEADDEADDEADDEADL, true);
                        }
                    } else {
                        if (moveKind == AMD64Kind.DWORD) {
                            // Support for narrow oops
                            masm.movl(result, (AMD64Address) crb.recordDataReferenceInCode(input, 0));
                        } else {
                            masm.movq(result, (AMD64Address) crb.recordDataReferenceInCode(input, 0));
                        }
                    }
                }
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(input.getJavaKind().getStackKind()); // ExcludeFromJacocoGeneratedReport
        }
    }

    public static boolean canMoveConst2Stack(JavaConstant input) {
        switch (input.getJavaKind().getStackKind()) {
            case Int:
                break;
            case Long:
                break;
            case Float:
                break;
            case Double:
                break;
            case Object:
                if (input.isNull()) {
                    return true;
                } else {
                    return false;
                }
            default:
                return false;
        }
        return true;
    }

    public static void const2stack(CompilationResultBuilder crb, AMD64MacroAssembler masm, Value result, JavaConstant input) {
        AMD64Address dest = (AMD64Address) crb.asAddress(result);
        final long imm;
        switch (input.getJavaKind().getStackKind()) {
            case Int:
                imm = input.asInt();
                break;
            case Long:
                imm = input.asLong();
                break;
            case Float:
                imm = floatToRawIntBits(input.asFloat());
                break;
            case Double:
                imm = doubleToRawLongBits(input.asDouble());
                break;
            case Object:
                if (input.isNull()) {
                    if (crb.mustReplaceWithUncompressedNullRegister(input)) {
                        masm.movq(dest, crb.uncompressedNullRegister);
                        return;
                    }
                    imm = 0;
                } else {
                    throw GraalError.shouldNotReachHere("Non-null object constants must be in a register"); // ExcludeFromJacocoGeneratedReport
                }
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(input.getJavaKind().getStackKind()); // ExcludeFromJacocoGeneratedReport
        }

        switch ((AMD64Kind) result.getPlatformKind()) {
            case BYTE:
                assert NumUtil.isByte(imm) : "Is not in byte range: " + imm;
                AMD64MIOp.MOVB.emit(masm, OperandSize.BYTE, dest, (int) imm);
                break;
            case WORD:
                assert NumUtil.isShort(imm) : "Is not in short range: " + imm;
                AMD64MIOp.MOV.emit(masm, OperandSize.WORD, dest, (int) imm);
                break;
            case DWORD:
            case SINGLE:
                assert NumUtil.isInt(imm) : "Is not in int range: " + imm;
                masm.movl(dest, (int) imm);
                break;
            case QWORD:
            case DOUBLE:
                masm.movlong(dest, imm);
                break;
            default:
                throw GraalError.shouldNotReachHere("Unknown result Kind: " + result.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
    }

    public abstract static class PointerCompressionOp extends AMD64LIRInstruction {
        protected final LIRKindTool lirKindTool;
        protected final CompressEncoding encoding;
        protected final boolean nonNull;

        @Def({OperandFlag.REG, OperandFlag.HINT}) private AllocatableValue result;
        @Use({OperandFlag.REG, OperandFlag.CONST}) private Value input;
        @Alive({OperandFlag.REG, OperandFlag.ILLEGAL, OperandFlag.UNINITIALIZED}) private AllocatableValue baseRegister;

        protected PointerCompressionOp(LIRInstructionClass<? extends PointerCompressionOp> type,
                        AllocatableValue result,
                        Value input,
                        AllocatableValue baseRegister,
                        CompressEncoding encoding,
                        boolean nonNull,
                        LIRKindTool lirKindTool) {

            super(type);
            this.result = result;
            this.input = input;
            this.baseRegister = baseRegister;
            this.encoding = encoding;
            this.nonNull = nonNull;
            this.lirKindTool = lirKindTool;
        }

        public static boolean hasBase(CompressEncoding encoding) {
            return encoding.hasBase();
        }

        public final Value getInput() {
            return input;
        }

        public final AllocatableValue getResult() {
            return result;
        }

        protected final Register getResultRegister() {
            return asRegister(result);
        }

        protected final Register getBaseRegister() {
            return hasBase(encoding) ? asRegister(baseRegister) : Register.None;
        }

        protected final int getShift() {
            return encoding.getShift();
        }

        /**
         * Emits code to move {@linkplain #getInput input} to {@link #getResult result}.
         */
        protected final void move(LIRKind kind, CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Move.move((AMD64Kind) kind.getPlatformKind(), crb, masm, result, input);
        }

        /**
         * Emits code to uncompress the compressed oop in {@code inputAndResultReg} by left shifting
         * it {@code shift} bits, adding it to {@code baseReg} and storing the result back in
         * {@code inputAndResultReg}.
         */
        public static void emitUncompressWithBaseRegister(AMD64MacroAssembler masm, Register inputAndResultReg, Register baseReg, int shift, boolean preserveFlagsRegister) {
            emitUncompressWithBaseRegister(masm, inputAndResultReg, baseReg, inputAndResultReg, shift, preserveFlagsRegister);
        }

        /**
         * Emits code to uncompress the compressed oop in {@code inputReg} by left shifting it
         * {@code shift} bits, adding it to {@code baseReg} and storing the result in
         * {@code resultReg}.
         */
        public static void emitUncompressWithBaseRegister(AMD64MacroAssembler masm, Register resultReg, Register baseReg, Register inputReg, int shift, boolean preserveFlagsRegister) {
            assert !baseReg.equals(Register.None) || shift != 0 : "compression not enabled";
            if (AMD64Address.isScaleShiftSupported(shift)) {
                Stride stride = Stride.fromLog2(shift);
                masm.leaq(resultReg, new AMD64Address(baseReg, inputReg, stride));
            } else {
                if (preserveFlagsRegister) {
                    throw GraalError.shouldNotReachHere("No valid flag-effect-free instruction available to uncompress oop"); // ExcludeFromJacocoGeneratedReport
                }
                if (!resultReg.equals(inputReg)) {
                    masm.movq(resultReg, inputReg);
                }
                masm.shlq(resultReg, shift);
                masm.addq(resultReg, baseReg);
            }
        }
    }

    public static class CompressPointerOp extends PointerCompressionOp {
        public static final LIRInstructionClass<CompressPointerOp> TYPE = LIRInstructionClass.create(CompressPointerOp.class);

        public CompressPointerOp(AllocatableValue result, Value input, AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull, LIRKindTool lirKindTool) {
            this(TYPE, result, input, baseRegister, encoding, nonNull, lirKindTool);
        }

        private CompressPointerOp(LIRInstructionClass<? extends PointerCompressionOp> type, AllocatableValue result, Value input,
                        AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull, LIRKindTool lirKindTool) {

            super(type, result, input, baseRegister, encoding, nonNull, lirKindTool);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            move(lirKindTool.getObjectKind(), crb, masm);

            final Register resReg = getResultRegister();
            final Register baseReg = getBaseRegister();
            if (!baseReg.equals(Register.None)) {
                if (!nonNull) {
                    masm.testq(resReg, resReg);
                    masm.cmovq(Equal, resReg, baseReg);
                }
                masm.subq(resReg, baseReg);
            }

            int shift = getShift();
            if (shift != 0) {
                masm.shrq(resReg, shift);
            }
        }
    }

    public static class UncompressPointerOp extends PointerCompressionOp {
        public static final LIRInstructionClass<UncompressPointerOp> TYPE = LIRInstructionClass.create(UncompressPointerOp.class);

        public UncompressPointerOp(AllocatableValue result, Value input, AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull, LIRKindTool lirKindTool) {
            this(TYPE, result, input, baseRegister, encoding, nonNull, lirKindTool);
        }

        private UncompressPointerOp(LIRInstructionClass<? extends PointerCompressionOp> type, AllocatableValue result, Value input,
                        AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull, LIRKindTool lirKindTool) {
            super(type, result, input, baseRegister, encoding, nonNull, lirKindTool);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            Register baseReg = getBaseRegister();
            int shift = getShift();
            Register resReg = getResultRegister();
            if (nonNull && !baseReg.equals(Register.None) && getInput() instanceof RegisterValue) {
                Register inputReg = ((RegisterValue) getInput()).getRegister();
                if (!inputReg.equals(resReg)) {
                    emitUncompressWithBaseRegister(masm, resReg, baseReg, inputReg, shift, false);
                    return;
                }
            }
            move(lirKindTool.getNarrowOopKind(), crb, masm);
            emitUncompressCode(masm, resReg, shift, baseReg, nonNull);
        }

        public static void emitUncompressCode(AMD64MacroAssembler masm, Register resReg, int shift, Register baseReg, boolean nonNull) {
            if (nonNull) {
                if (!baseReg.equals(Register.None)) {
                    emitUncompressWithBaseRegister(masm, resReg, baseReg, shift, false);
                } else if (shift != 0) {
                    masm.shlq(resReg, shift);
                }
            } else {
                if (shift != 0) {
                    masm.shlq(resReg, shift);
                }

                if (!baseReg.equals(Register.None)) {
                    if (shift == 0) {
                        // if encoding.shift != 0, the flags are already set by the shlq
                        masm.testq(resReg, resReg);
                    }

                    Label done = new Label();
                    masm.jccb(Equal, done);
                    masm.addq(resReg, baseReg);
                    masm.bind(done);
                }
            }
        }
    }

    private abstract static class ZeroNullConversionOp extends AMD64LIRInstruction {
        @Def({OperandFlag.REG, OperandFlag.HINT}) protected AllocatableValue result;
        @Use({OperandFlag.REG}) protected AllocatableValue input;

        protected ZeroNullConversionOp(LIRInstructionClass<? extends ZeroNullConversionOp> type, AllocatableValue result, AllocatableValue input) {
            super(type);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            Register nullRegister = crb.uncompressedNullRegister;
            if (!nullRegister.equals(Register.None)) {
                emitConversion(asRegister(result), asRegister(input), nullRegister, masm);
            }
        }

        protected abstract void emitConversion(Register resultRegister, Register inputRegister, Register nullRegister, AMD64MacroAssembler masm);
    }

    public static class ConvertNullToZeroOp extends ZeroNullConversionOp {
        public static final LIRInstructionClass<ConvertNullToZeroOp> TYPE = LIRInstructionClass.create(ConvertNullToZeroOp.class);

        public ConvertNullToZeroOp(AllocatableValue result, AllocatableValue input) {
            super(TYPE, result, input);
        }

        @Override
        protected final void emitConversion(Register resultRegister, Register inputRegister, Register nullRegister, AMD64MacroAssembler masm) {
            if (inputRegister.equals(resultRegister)) {
                Label done = new Label();
                masm.subqAndJcc(inputRegister, nullRegister, Equal, done, true);
                masm.addq(inputRegister, nullRegister);
                masm.bind(done);
            } else {
                masm.subq(resultRegister, resultRegister);
                masm.cmpq(inputRegister, nullRegister);
                masm.cmovq(NotEqual, resultRegister, inputRegister);
            }
        }
    }

    public static class ConvertZeroToNullOp extends ZeroNullConversionOp {
        public static final LIRInstructionClass<ConvertZeroToNullOp> TYPE = LIRInstructionClass.create(ConvertZeroToNullOp.class);

        public ConvertZeroToNullOp(AllocatableValue result, AllocatableValue input) {
            super(TYPE, result, input);
        }

        @Override
        protected final void emitConversion(Register resultRegister, Register inputRegister, Register nullRegister, AMD64MacroAssembler masm) {
            if (!inputRegister.equals(resultRegister)) {
                masm.movq(resultRegister, inputRegister);
            }
            masm.testq(inputRegister, inputRegister);
            masm.cmovq(Equal, resultRegister, nullRegister);
        }
    }
}
