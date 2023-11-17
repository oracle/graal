/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.amd64.vector;

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVDQU32;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVSS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVUPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVUPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VXORPD;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.core.common.type.DataPointerConstant;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.amd64.AMD64Move;
import jdk.graal.compiler.lir.amd64.AMD64RestoreRegistersOp;
import jdk.graal.compiler.lir.amd64.AMD64SaveRegistersOp;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

public class AMD64VectorMove {

    @Opcode("VMOVE")
    public static final class MoveToRegOp extends AMD64LIRInstruction implements StandardOp.ValueMoveOp {
        public static final LIRInstructionClass<MoveToRegOp> TYPE = LIRInstructionClass.create(MoveToRegOp.class);

        @Def({OperandFlag.REG, OperandFlag.STACK, OperandFlag.HINT}) protected AllocatableValue result;
        @Use({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue input;

        public MoveToRegOp(AllocatableValue result, AllocatableValue input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            move(crb, masm, result, input);
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

    @Opcode("VMOVE")
    public static final class MoveFromRegOp extends AMD64LIRInstruction implements StandardOp.ValueMoveOp {
        public static final LIRInstructionClass<MoveFromRegOp> TYPE = LIRInstructionClass.create(MoveFromRegOp.class);

        @Def({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue result;
        @Use({OperandFlag.REG, OperandFlag.HINT}) protected AllocatableValue input;

        public MoveFromRegOp(AllocatableValue result, AllocatableValue input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            move(crb, masm, result, input);
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

    @Opcode("VMOVE")
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
                const2reg(crb, masm, (RegisterValue) result, input);
            } else {
                assert isStackSlot(result);
                AMD64Move.const2stack(crb, masm, result, input);
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

    @Opcode("VMOVE")
    public static class MoveFromArrayConstOp extends AMD64LIRInstruction implements StandardOp.LoadConstantOp {
        public static final LIRInstructionClass<MoveFromArrayConstOp> TYPE = LIRInstructionClass.create(MoveFromArrayConstOp.class);

        @Def({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue result;
        private final DataPointerConstant input;

        public MoveFromArrayConstOp(AllocatableValue result, DataPointerConstant input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind kind = (AMD64Kind) result.getPlatformKind();
            assert kind.isXMM() : "Can only move array to XMM register";
            int alignment = crb.dataBuilder.ensureValidDataAlignment(input.getAlignment());
            VMOVDQU32.emit(masm, AVXKind.getRegisterSize(result), asRegister(result), (AMD64Address) crb.recordDataReferenceInCode(input, alignment));
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

    @Opcode("VSTACKMOVE")
    public static final class StackMoveOp extends AMD64LIRInstruction implements StandardOp.ValueMoveOp {
        public static final LIRInstructionClass<StackMoveOp> TYPE = LIRInstructionClass.create(StackMoveOp.class);

        @Def({OperandFlag.STACK}) protected AllocatableValue result;
        @Use({OperandFlag.STACK, OperandFlag.HINT}) protected AllocatableValue input;
        @Alive({OperandFlag.STACK, OperandFlag.UNINITIALIZED}) private AllocatableValue backupSlot;

        private Register scratch;

        public StackMoveOp(AllocatableValue result, AllocatableValue input, Register scratch, AllocatableValue backupSlot) {
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

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            // backup scratch register
            move(crb, masm, backupSlot, scratch.asValue(backupSlot.getValueKind()));
            // move stack slot
            move(crb, masm, scratch.asValue(getInput().getValueKind()), getInput());
            move(crb, masm, getResult(), scratch.asValue(getResult().getValueKind()));
            // restore scratch register
            move(crb, masm, scratch.asValue(backupSlot.getValueKind()), backupSlot);

        }
    }

    public abstract static class VectorMemOp extends AMD64VectorInstruction {

        protected final VexMoveOp op;

        @Use({OperandFlag.COMPOSITE}) protected AMD64AddressValue address;
        @State protected LIRFrameState state;

        protected VectorMemOp(LIRInstructionClass<? extends VectorMemOp> c, AVXSize size, VexMoveOp op, AMD64AddressValue address, LIRFrameState state) {
            super(c, size);
            this.op = op;
            this.address = address;
            this.state = state;
        }

        protected abstract void emitMemAccess(AMD64MacroAssembler masm);

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (state != null) {
                crb.recordImplicitException(masm.position(), state);
            }
            emitMemAccess(masm);
        }
    }

    public static final class VectorLoadOp extends VectorMemOp {
        public static final LIRInstructionClass<VectorLoadOp> TYPE = LIRInstructionClass.create(VectorLoadOp.class);

        @Def({OperandFlag.REG}) protected AllocatableValue result;

        public VectorLoadOp(AVXSize size, VexMoveOp op, AllocatableValue result, AMD64AddressValue address, LIRFrameState state) {
            super(TYPE, size, op, address, state);
            this.result = result;
        }

        @Override
        public void emitMemAccess(AMD64MacroAssembler masm) {
            op.emit(masm, size, asRegister(result), address.toAddress());
        }
    }

    public static class VectorStoreOp extends VectorMemOp {
        public static final LIRInstructionClass<VectorStoreOp> TYPE = LIRInstructionClass.create(VectorStoreOp.class);

        @Use({OperandFlag.REG}) protected AllocatableValue input;

        public VectorStoreOp(AVXSize size, VexMoveOp op, AMD64AddressValue address, AllocatableValue input, LIRFrameState state) {
            super(TYPE, size, op, address, state);
            this.input = input;
        }

        @Override
        public void emitMemAccess(AMD64MacroAssembler masm) {
            op.emit(masm, size, address.toAddress(), asRegister(input));
        }
    }

    @Opcode("SAVE_REGISTER")
    public static class SaveRegistersOp extends AMD64SaveRegistersOp {
        public static final LIRInstructionClass<SaveRegistersOp> TYPE = LIRInstructionClass.create(SaveRegistersOp.class);

        public SaveRegistersOp(Register[] savedRegisters, AllocatableValue[] slots) {
            super(TYPE, savedRegisters, slots);
        }

        @Override
        protected void saveRegister(CompilationResultBuilder crb, AMD64MacroAssembler masm, StackSlot result, Register register) {
            AMD64Kind kind = (AMD64Kind) result.getPlatformKind();
            if (kind.isXMM()) {
                VexMoveOp op;
                if (kind.getVectorLength() > 1) {
                    op = getVectorMoveOp(kind.getScalar());
                } else {
                    op = getScalarMoveOp(kind);
                }

                AMD64Address addr = (AMD64Address) crb.asAddress(result);
                op.emit(masm, AVXKind.getRegisterSize(kind), addr, register);
            } else {
                super.saveRegister(crb, masm, result, register);
            }
        }
    }

    @Opcode("RESTORE_REGISTER")
    public static final class RestoreRegistersOp extends AMD64RestoreRegistersOp {
        public static final LIRInstructionClass<RestoreRegistersOp> TYPE = LIRInstructionClass.create(RestoreRegistersOp.class);

        public RestoreRegistersOp(AllocatableValue[] source, AMD64SaveRegistersOp save) {
            super(TYPE, source, save);
        }

        @Override
        protected void restoreRegister(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register register, StackSlot input) {
            AMD64Kind kind = (AMD64Kind) input.getPlatformKind();
            if (kind.isXMM()) {
                VexMoveOp op;
                if (kind.getVectorLength() > 1) {
                    op = getVectorMoveOp(kind.getScalar());
                } else {
                    op = getScalarMoveOp(kind);
                }

                AMD64Address addr = (AMD64Address) crb.asAddress(input);
                op.emit(masm, AVXKind.getRegisterSize(kind), register, addr);
            } else {
                super.restoreRegister(crb, masm, register, input);
            }
        }
    }

    private static VexMoveOp getScalarMoveOp(AMD64Kind kind) {
        switch (kind) {
            case SINGLE:
                return VMOVSS;
            case DOUBLE:
                return VMOVSD;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(kind); // ExcludeFromJacocoGeneratedReport
        }
    }

    private static VexMoveOp getVectorMoveOp(AMD64Kind kind) {
        switch (kind) {
            case SINGLE:
                return VMOVUPS;
            case DOUBLE:
                return VMOVUPD;
            default:
                return VMOVDQU32;
        }
    }

    public static VexMoveOp getVectorMemMoveOp(AMD64Kind kind) {
        switch (AVXKind.getDataSize(kind)) {
            case DWORD:
                return VMOVD;
            case QWORD:
                return VMOVQ;
            default:
                return getVectorMoveOp(kind.getScalar());
        }
    }

    private static void move(CompilationResultBuilder crb, AMD64MacroAssembler masm, AllocatableValue result, Value input) {
        VexMoveOp op;
        AVXSize size;
        AMD64Kind kind = (AMD64Kind) result.getPlatformKind();
        if (kind.getVectorLength() > 1) {
            size = AVXKind.getRegisterSize(kind);
            if (isRegister(input) && isRegister(result)) {
                op = getVectorMoveOp(kind.getScalar());
            } else {
                op = getVectorMemMoveOp(kind);
            }
        } else {
            size = AVXSize.XMM;
            if (isRegister(input) && isRegister(result)) {
                op = getVectorMoveOp(kind);
            } else {
                op = getScalarMoveOp(kind);
            }
        }

        if (isRegister(input)) {
            if (isRegister(result)) {
                if (!asRegister(input).equals(asRegister(result))) {
                    op.emit(masm, size, asRegister(result), asRegister(input));
                }
            } else {
                assert isStackSlot(result);
                op.emit(masm, size, (AMD64Address) crb.asAddress(result), asRegister(input));
            }
        } else {
            assert isStackSlot(input) : Assertions.errorMessageContext("input", input);
            assert isRegister(result) : Assertions.errorMessageContext("result", result);
            op.emit(masm, size, asRegister(result), (AMD64Address) crb.asAddress(input));
        }
    }

    private static void const2reg(CompilationResultBuilder crb, AMD64MacroAssembler masm, RegisterValue result, JavaConstant input) {
        if (input.isDefaultForKind()) {
            AMD64Kind kind = (AMD64Kind) result.getPlatformKind();
            Register register = result.getRegister();
            VXORPD.emit(masm, AVXKind.getRegisterSize(kind), register, register, register);
            return;
        }

        AMD64Address address;
        switch (input.getJavaKind()) {
            case Float:
                address = (AMD64Address) crb.asFloatConstRef(input);
                break;

            case Double:
                address = (AMD64Address) crb.asDoubleConstRef(input);
                break;

            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(input.getJavaKind()); // ExcludeFromJacocoGeneratedReport
        }
        VexMoveOp op = getScalarMoveOp((AMD64Kind) result.getPlatformKind());
        op.emit(masm, AVXSize.XMM, asRegister(result), address);
    }

    public static final class AVXMoveToIntOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<AVXMoveToIntOp> TYPE = LIRInstructionClass.create(AVXMoveToIntOp.class);

        @Opcode private final VexMoveOp opcode;

        @Def({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue result;
        @Use({OperandFlag.REG}) protected AllocatableValue input;

        public AVXMoveToIntOp(VexMoveOp opcode, AllocatableValue result, AllocatableValue input) {
            super(TYPE);
            this.opcode = opcode;
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(result)) {
                opcode.emitReverse(masm, AVXSize.XMM, asRegister(result), asRegister(input));
            } else {
                opcode.emit(masm, AVXSize.XMM, (AMD64Address) crb.asAddress(result), asRegister(input));
            }
        }
    }
}
