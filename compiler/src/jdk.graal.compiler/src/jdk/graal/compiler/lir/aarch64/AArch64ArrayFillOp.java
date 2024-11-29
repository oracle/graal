/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.aarch64;

import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_SIGNED_SCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.aarch64.AArch64.r5;
import static jdk.vm.ci.aarch64.AArch64.r6;
import static jdk.vm.ci.aarch64.AArch64.r7;
import static jdk.vm.ci.aarch64.AArch64.r8;
import static jdk.vm.ci.aarch64.AArch64.r9;
import static jdk.vm.ci.aarch64.AArch64.r10;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.vm.ci.meta.JavaKind;
import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ShiftType;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;
import jdk.graal.compiler.nodes.ConstantNode;

/**
 * Emits code which fills an array with a constant value.
 */
@Opcode("ARRAYS_FILL")
public final class AArch64ArrayFillOp extends AArch64ComplexVectorOp {
    public static final LIRInstructionClass<AArch64ArrayFillOp> TYPE = LIRInstructionClass.create(AArch64ArrayFillOp.class);

    private JavaKind kind;
    @Alive({REG}) protected Value array;
    @Alive({REG}) protected Value arrayBaseOffset;
    @Alive({REG}) protected Value length;
    @Alive({REG}) protected Value value;
    @Temp protected Value[] temps;

    public AArch64ArrayFillOp(JavaKind kind, Value array, Value arrayBaseOffset, Value length, Value value) {
        super(TYPE);

        GraalError.guarantee(array.getPlatformKind() == AArch64Kind.QWORD, "pointer value expected");
        GraalError.guarantee(length.getPlatformKind() == AArch64Kind.DWORD, "integer value expected in 'length'");
        GraalError.guarantee(value.getPlatformKind() == AArch64Kind.DWORD, "integer value expected in 'value'.");

        this.kind = kind;
        this.array = array;
        this.arrayBaseOffset = arrayBaseOffset;
        this.length = length;
        this.value = value;

        this.temps = new Value[]{
                        r5.asValue(),
                        r6.asValue(),
                        r7.asValue(),
                        r8.asValue(),
                        r9.asValue(),
                        r10.asValue()};
    }

    /**
     * Generate stub for array fill. If "aligned" is true, the "to" address is assumed to be
     * heapword aligned.
     *
     */
    @Override
    @SuppressWarnings("fallthrough")
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        int shift = -1;
        boolean aligned = false;
        int operation_width_B = 8;
        int operation_width_H = 16;
        int operation_width_W = 32;
        int operation_width_DW = 64;
        JavaKind element_type = this.kind;

        // masm.brk(AArch64MacroAssembler.AArch64ExceptionCode.BREAKPOINT);

        try (ScratchRegister scr1 = masm.getScratchRegister()) {
            Register target_array = r7;
            Register value_to_fill_with = r8;
            Register number_of_elements = r9;
            Register number_of_eight_byte_words = r10;

            Label L_fill_elements = new Label();
            Label L_skip_align1 = new Label();
            Label L_skip_align2 = new Label();
            Label L_skip_align4 = new Label();
            Label L_fill_2 = new Label();
            Label L_fill_4 = new Label();
            Label L_done = new Label();

            masm.add(64, target_array, asRegister(this.array), asRegister(this.arrayBaseOffset));
            masm.mov(64, value_to_fill_with, asRegister(value));
            masm.mov(64, number_of_elements, asRegister(length));

            // Will jump to L_fill_elements if there are less than 8 bytes to fill in target array.
            // Before jumping, adjust value_to_fill_with to contain the 'pattern' to fill target
            // array with.
            switch (element_type) {
                case JavaKind.Byte:
                    shift = 0;
                    masm.compare(operation_width_W, number_of_elements, 8 >> shift);
                    masm.bfi(operation_width_W, value_to_fill_with, value_to_fill_with, 8, 8);
                    masm.bfi(operation_width_W, value_to_fill_with, value_to_fill_with, 16, 16);
                    masm.branchConditionally(ConditionFlag.LO, L_fill_elements);
                    break;
                case JavaKind.Short:
                    shift = 1;
                    masm.compare(operation_width_W, number_of_elements, 8 >> shift);
                    masm.bfi(operation_width_W, value_to_fill_with, value_to_fill_with, 16, 16);
                    masm.branchConditionally(ConditionFlag.LO, L_fill_elements);
                    break;
                case JavaKind.Int:
                    shift = 2;
                    masm.compare(operation_width_W, number_of_elements, 8 >> shift);
                    masm.branchConditionally(ConditionFlag.LO, L_fill_elements);
                    break;
                default:
                    GraalError.shouldNotReachHere("Should not reach here.");
            }

            // Align source address at 8 bytes address boundary.
            if (!aligned) {
                switch (element_type) {
                    case JavaKind.Byte:
                        masm.tbz(target_array, 0, L_skip_align1);
                        masm.str(operation_width_B, value_to_fill_with, AArch64Address.createImmediateAddress(operation_width_B, IMMEDIATE_POST_INDEXED, target_array, 1));
                        masm.sub(operation_width_W, number_of_elements, number_of_elements, 1);
                        masm.bind(L_skip_align1);
                        // Fallthrough
                    case JavaKind.Short:
                        masm.tbz(target_array, 1, L_skip_align2);
                        masm.str(operation_width_H, value_to_fill_with, AArch64Address.createImmediateAddress(operation_width_H, IMMEDIATE_POST_INDEXED, target_array, 2));
                        masm.sub(operation_width_W, number_of_elements, number_of_elements, 2 >> shift);
                        masm.bind(L_skip_align2);
                        // Fallthrough
                    case JavaKind.Int:
                        masm.tbz(target_array, 2, L_skip_align4);
                        masm.str(operation_width_W, value_to_fill_with, AArch64Address.createImmediateAddress(operation_width_W, IMMEDIATE_POST_INDEXED, target_array, 4));
                        masm.sub(operation_width_W, number_of_elements, number_of_elements, 4 >> shift);
                        masm.bind(L_skip_align4);
                        break;
                    default:
                        GraalError.shouldNotReachHere("Should not reach here.");
                }
            }

            // Divide number_of_elements by 2^(3-shift), i.e., divide number_of_elements by the
            // number of elements that fit into an 8 byte word.
            masm.lsr(operation_width_W, number_of_eight_byte_words, number_of_elements, 3 - shift);

            // expand from 32 bits to 64 bits
            masm.bfi(operation_width_DW, value_to_fill_with, value_to_fill_with, 32, 32);

            // number_of_elements = number_of_elements -
            // number_of_eight_byte_words*(elements_by_eight_byte_word)
            masm.sub(operation_width_W, number_of_elements, number_of_elements, number_of_eight_byte_words, ShiftType.LSL, 3 - shift);

            // fill number_of_eight_byte_words bytes of the target array
            fillWords(masm, target_array, number_of_eight_byte_words, value_to_fill_with);

            // Remaining number_of_elements is less than 8 bytes. Fill it by a single store.
            // Note that the total length is no less than 8 bytes.
            if (element_type == JavaKind.Byte || element_type == JavaKind.Short) {
                masm.cbz(operation_width_W, number_of_elements, L_done);
                masm.add(operation_width_DW, target_array, target_array, number_of_elements, ShiftType.LSL, shift);
                masm.str(operation_width_DW, value_to_fill_with, masm.makeAddress(operation_width_DW, target_array, -8));
                masm.jmp(L_done);
            }

            // Handle copies less than 8 bytes.
            masm.bind(L_fill_elements);
            switch (element_type) {
                case JavaKind.Byte:
                    masm.tbz(number_of_elements, 0, L_fill_2);
                    masm.str(operation_width_B, value_to_fill_with, AArch64Address.createImmediateAddress(operation_width_B, IMMEDIATE_POST_INDEXED, target_array, 1));
                    masm.bind(L_fill_2);
                    masm.tbz(number_of_elements, 1, L_fill_4);
                    masm.str(operation_width_H, value_to_fill_with, AArch64Address.createImmediateAddress(operation_width_H, IMMEDIATE_POST_INDEXED, target_array, 2));
                    masm.bind(L_fill_4);
                    masm.tbz(number_of_elements, 2, L_done);
                    masm.str(operation_width_W, value_to_fill_with, AArch64Address.createBaseRegisterOnlyAddress(operation_width_W, target_array));
                    break;
                case JavaKind.Short:
                    masm.tbz(number_of_elements, 0, L_fill_4);
                    masm.str(operation_width_H, value_to_fill_with, AArch64Address.createImmediateAddress(operation_width_H, IMMEDIATE_POST_INDEXED, target_array, 2));
                    masm.bind(L_fill_4);
                    masm.tbz(number_of_elements, 1, L_done);
                    masm.str(operation_width_W, value_to_fill_with, AArch64Address.createBaseRegisterOnlyAddress(operation_width_W, target_array));
                    break;
                case JavaKind.Int:
                    masm.cbz(operation_width_W, number_of_elements, L_done);
                    masm.str(operation_width_W, value_to_fill_with, AArch64Address.createBaseRegisterOnlyAddress(operation_width_W, target_array));
                    break;
                default:
                    GraalError.shouldNotReachHere("Should not reach here.");
            }
            masm.bind(L_done);
        }
    }

    /**
     * Algorithm:
     *
     * <code>
     * if (cnt == 0) return ;
     * if ((p & 8) != 0) *p++ = v;
     *
     * scratch1 = cnt & 14;
     * cnt     -= scratch1;
     * p       += scratch1;
     *
     * switch (scratch1 / 2) {
     *     do {
     *         cnt -= 16;
     *         p[-16] = v;
     *         p[-15] = v;
     *         case 7:
     *             p[-14] = v;
     *             p[-13] = v;
     *         case 6:
     *             p[-12] = v;
     *             p[-11] = v;
     *         // ...
     *         case 1:
     *             p[-2] = v;
     *             p[-1] = v;
     *         case 0:
     *             p += 16;
     *     } while (cnt);
     * }
     *
     * if ((cnt & 1) == 1) {
     *     *p++ = v;
     * }
     * </code>
     *
     * Base will point to the end of the buffer after filling.
     *
     * @param masm
     * @param target_array Address of a buffer to be filled, 8 bytes aligned.
     * @param count_words Count in 8-byte unit.
     * @param value_to_fill_with Value to be filled with.
     */
    @SuppressWarnings("static-method")
    private void fillWords(AArch64MacroAssembler masm, Register target_array, Register count_words, Register value_to_fill_with) {
        int operation_width_DW = 64;
        int unroll = 8;

        Label fini = new Label();
        Label skip = new Label();
        Label entry = new Label();
        Label loop = new Label();

        masm.cbz(operation_width_DW, count_words, fini);
        masm.tbz(target_array, 3, skip);
        masm.str(operation_width_DW, value_to_fill_with, AArch64Address.createImmediateAddress(operation_width_DW, IMMEDIATE_POST_INDEXED, target_array, 8));
        masm.sub(operation_width_DW, count_words, count_words, 1);
        masm.bind(skip);

        masm.and(operation_width_DW, r5, count_words, (unroll - 1) * 2);
        masm.sub(operation_width_DW, count_words, count_words, r5);
        masm.add(operation_width_DW, target_array, target_array, r5, ShiftType.LSL, 3);
        masm.adr(r6, entry);
        masm.sub(operation_width_DW, r6, r6, r5, ShiftType.LSL, 1);
        masm.jmp(r6);

        masm.bind(loop);
        masm.add(operation_width_DW, target_array, target_array, unroll * 16);
        for (int i = -unroll; i < 0; i++) {
            masm.stp(operation_width_DW, value_to_fill_with, value_to_fill_with, AArch64Address.createImmediateAddress(operation_width_DW, IMMEDIATE_PAIR_SIGNED_SCALED, target_array, i * 16));
        }
        masm.bind(entry);
        masm.subs(operation_width_DW, count_words, count_words, unroll * 2);
        masm.branchConditionally(ConditionFlag.GE, loop);

        masm.tbz(count_words, 0, fini);
        masm.str(operation_width_DW, value_to_fill_with, AArch64Address.createImmediateAddress(operation_width_DW, IMMEDIATE_POST_INDEXED, target_array, 8));
        masm.bind(fini);
    }
}
