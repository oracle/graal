/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_SIGNED_SCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.aarch64.AArch64.r10;
import static jdk.vm.ci.aarch64.AArch64.r5;
import static jdk.vm.ci.aarch64.AArch64.r6;
import static jdk.vm.ci.aarch64.AArch64.r7;
import static jdk.vm.ci.aarch64.AArch64.r8;
import static jdk.vm.ci.aarch64.AArch64.r9;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ShiftType;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * Emits code which fills an array with a constant value. The assembly code in this intrinsic was
 * based in the HotSpot's version of the same intrinsic.
 */
@Opcode("ARRAYS_FILL")
public final class AArch64ArrayFillOp extends AArch64ComplexVectorOp {
    public static final LIRInstructionClass<AArch64ArrayFillOp> TYPE = LIRInstructionClass.create(AArch64ArrayFillOp.class);

    private JavaKind elementType;
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

        this.elementType = kind;
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

    @Override
    @SuppressWarnings("fallthrough")
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        int shift = -1;
        int operationWidthB = 8;
        int operationWidthH = 16;
        int operationWidthW = 32;
        int operationWidthDW = 64;

        Register targetArray = r7;
        Register valueToFillWith = r8;
        Register numberOfElements = r9;
        Register numberOfEightByteWords = r10;

        Label fillElementsLabel = new Label();
        Label skipAlign1Label = new Label();
        Label skipAlign2Label = new Label();
        Label skipAlign4Label = new Label();
        Label fill2Label = new Label();
        Label fill4Label = new Label();
        Label doneLabel = new Label();

        masm.add(operationWidthDW, targetArray, asRegister(this.array), asRegister(this.arrayBaseOffset));
        masm.mov(operationWidthDW, valueToFillWith, asRegister(value));
        masm.mov(operationWidthDW, numberOfElements, asRegister(length));

        // Will jump to fillElementsLabel if there are less than 8 bytes to fill in target array.
        // Before jumping, adjust valueToFillWith to contain the 'pattern' to fill target
        // array with.
        switch (this.elementType) {
            case JavaKind.Boolean:
            case JavaKind.Byte:
                shift = 0;
                masm.compare(operationWidthW, numberOfElements, 8);
                masm.bfi(operationWidthW, valueToFillWith, valueToFillWith, 8, 8);
                masm.bfi(operationWidthW, valueToFillWith, valueToFillWith, 16, 16);

                // jump to fillElementsLabel if numberOfElements < 8 elements
                masm.branchConditionally(ConditionFlag.LO, fillElementsLabel);
                break;
            case JavaKind.Short:
                // Fallthrough
            case JavaKind.Char:
                shift = 1;
                masm.compare(operationWidthW, numberOfElements, 4);
                masm.bfi(operationWidthW, valueToFillWith, valueToFillWith, 16, 16);
                // jump to fillElementsLabel if numberOfElements < 4 elements
                masm.branchConditionally(ConditionFlag.LO, fillElementsLabel);
                break;
            case JavaKind.Int:
            case JavaKind.Float:
                shift = 2;
                masm.compare(operationWidthW, numberOfElements, 2);
                // jump to fillElementsLabel if numberOfElements < 2 elements
                masm.branchConditionally(ConditionFlag.LO, fillElementsLabel);
                break;
            case JavaKind.Long:
            case JavaKind.Double:
                shift = 3;
                masm.compare(operationWidthW, numberOfElements, 1);
                // jump to doneLabel if numberOfElements < 1 elements
                masm.branchConditionally(ConditionFlag.LO, doneLabel);
                break;
            default:
                GraalError.shouldNotReachHere("Should not reach here.");
        }

        // Align source address at 8 bytes address boundary.
        switch (this.elementType) {
            case JavaKind.Boolean:
            case JavaKind.Byte:
                masm.tbz(targetArray, 0, skipAlign1Label);
                masm.str(operationWidthB, valueToFillWith, AArch64Address.createImmediateAddress(operationWidthB, IMMEDIATE_POST_INDEXED, targetArray, 1));
                masm.sub(operationWidthW, numberOfElements, numberOfElements, 1);
                masm.bind(skipAlign1Label);
                // Fallthrough
            case JavaKind.Short:
                // Fallthrough
            case JavaKind.Char:
                masm.tbz(targetArray, 1, skipAlign2Label);
                masm.str(operationWidthH, valueToFillWith, AArch64Address.createImmediateAddress(operationWidthH, IMMEDIATE_POST_INDEXED, targetArray, 2));
                masm.sub(operationWidthW, numberOfElements, numberOfElements, 2 >> shift);
                masm.bind(skipAlign2Label);
                // Fallthrough
            case JavaKind.Int:
            case JavaKind.Float:
                masm.tbz(targetArray, 2, skipAlign4Label);
                masm.str(operationWidthW, valueToFillWith, AArch64Address.createImmediateAddress(operationWidthW, IMMEDIATE_POST_INDEXED, targetArray, 4));
                masm.sub(operationWidthW, numberOfElements, numberOfElements, 4 >> shift);
                masm.bind(skipAlign4Label);
                break;
            case JavaKind.Long:
            case JavaKind.Double:
                break;
            default:
                GraalError.shouldNotReachHere("Should not reach here.");
        }

        // Divide numberOfElements by 2^(3-shift), i.e., divide numberOfElements by the
        // number of elements that fit into an 8 byte word.
        masm.lsr(operationWidthW, numberOfEightByteWords, numberOfElements, 3 - shift);

        // If valueToFillWith isn't already 64 bits we'll make it so
        if (this.elementType != JavaKind.Long && this.elementType != JavaKind.Double) {
            masm.bfi(operationWidthDW, valueToFillWith, valueToFillWith, 32, 32);
        }

        // numberOfElements = numberOfElements - numberOfEightByteWords * elementsByEightByteWord
        masm.sub(operationWidthW, numberOfElements, numberOfElements, numberOfEightByteWords, ShiftType.LSL, 3 - shift);

        // fill numberOfEightByteWords bytes of the target array
        fillWords(masm, targetArray, numberOfEightByteWords, valueToFillWith);

        // Remaining numberOfElements is less than 8 bytes. Fill it by a single store.
        // Note that the total length is no less than 8 bytes.
        if (this.elementType == JavaKind.Byte || this.elementType == JavaKind.Boolean || this.elementType == JavaKind.Short || this.elementType == JavaKind.Char) {
            masm.cbz(operationWidthW, numberOfElements, doneLabel);
            masm.add(operationWidthDW, targetArray, targetArray, numberOfElements, ShiftType.LSL, shift);
            masm.str(operationWidthDW, valueToFillWith, masm.makeAddress(operationWidthDW, targetArray, -8));
            masm.jmp(doneLabel);
        }

        // Handle copies less than 8 bytes.
        masm.bind(fillElementsLabel);
        switch (this.elementType) {
            case JavaKind.Boolean:
            case JavaKind.Byte:
                masm.tbz(numberOfElements, 0, fill2Label);
                masm.str(operationWidthB, valueToFillWith, AArch64Address.createImmediateAddress(operationWidthB, IMMEDIATE_POST_INDEXED, targetArray, 1));
                masm.bind(fill2Label);
                masm.tbz(numberOfElements, 1, fill4Label);
                masm.str(operationWidthH, valueToFillWith, AArch64Address.createImmediateAddress(operationWidthH, IMMEDIATE_POST_INDEXED, targetArray, 2));
                masm.bind(fill4Label);
                masm.tbz(numberOfElements, 2, doneLabel);
                masm.str(operationWidthW, valueToFillWith, AArch64Address.createBaseRegisterOnlyAddress(operationWidthW, targetArray));
                break;
            case JavaKind.Short:
            case JavaKind.Char:
                masm.tbz(numberOfElements, 0, fill4Label);
                masm.str(operationWidthH, valueToFillWith, AArch64Address.createImmediateAddress(operationWidthH, IMMEDIATE_POST_INDEXED, targetArray, 2));
                masm.bind(fill4Label);
                masm.tbz(numberOfElements, 1, doneLabel);
                masm.str(operationWidthW, valueToFillWith, AArch64Address.createBaseRegisterOnlyAddress(operationWidthW, targetArray));
                break;
            case JavaKind.Int:
            case JavaKind.Float:
                masm.cbz(operationWidthW, numberOfElements, doneLabel);
                masm.str(operationWidthW, valueToFillWith, AArch64Address.createBaseRegisterOnlyAddress(operationWidthW, targetArray));
                break;
            case JavaKind.Long:
            case JavaKind.Double:
                break;
            default:
                GraalError.shouldNotReachHere("Should not reach here.");
        }
        masm.bind(doneLabel);
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
     * @param targetArray Address of a buffer to be filled, 8 bytes aligned.
     * @param numberOfEightByteWords Count in 8-byte unit.
     * @param valueToFillWith Value to be filled with.
     */
    @SuppressWarnings("static-method")
    private void fillWords(AArch64MacroAssembler masm, Register targetArray, Register numberOfEightByteWords, Register valueToFillWith) {
        int operationWidthDW = 64;
        int unroll = 8;

        Label finishedLabel = new Label();
        Label skipLabel = new Label();
        Label entryLabel = new Label();
        Label loopHeadLabel = new Label();

        // If nothing to do just jump to finishedLabel
        masm.cbz(operationWidthDW, numberOfEightByteWords, finishedLabel);

        // Because we didn't jump in the previous instruction then we certainly
        // have at least 8 bytes to fill in the target array.

        masm.tbz(targetArray, 3, skipLabel);
        masm.str(operationWidthDW, valueToFillWith, AArch64Address.createImmediateAddress(operationWidthDW, IMMEDIATE_POST_INDEXED, targetArray, 8));
        masm.sub(operationWidthDW, numberOfEightByteWords, numberOfEightByteWords, 1);
        masm.bind(skipLabel);

        masm.and(operationWidthDW, r5, numberOfEightByteWords, (unroll - 1) * 2);
        masm.sub(operationWidthDW, numberOfEightByteWords, numberOfEightByteWords, r5);
        masm.add(operationWidthDW, targetArray, targetArray, r5, ShiftType.LSL, 3);
        masm.adr(r6, entryLabel);
        masm.sub(operationWidthDW, r6, r6, r5, ShiftType.LSL, 1);
        masm.jmp(r6);

        masm.align(PREFERRED_LOOP_ALIGNMENT);
        masm.bind(loopHeadLabel);
        masm.add(operationWidthDW, targetArray, targetArray, unroll * 16);
        for (int i = -unroll; i < 0; i++) {
            masm.stp(operationWidthDW, valueToFillWith, valueToFillWith, AArch64Address.createImmediateAddress(operationWidthDW, IMMEDIATE_PAIR_SIGNED_SCALED, targetArray, i * 16));
        }
        masm.bind(entryLabel);
        masm.subs(operationWidthDW, numberOfEightByteWords, numberOfEightByteWords, unroll * 2);
        masm.branchConditionally(ConditionFlag.GE, loopHeadLabel);

        masm.tbz(numberOfEightByteWords, 0, finishedLabel);
        masm.str(operationWidthDW, valueToFillWith, AArch64Address.createImmediateAddress(operationWidthDW, IMMEDIATE_POST_INDEXED, targetArray, 8));
        masm.bind(finishedLabel);
    }
}
