/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.HalfReg;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.Byte;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.HalfWord;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createImmediateAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createStructureNoOffsetAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.guaranteeFixedRegister;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsLd3Post;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsSt2Post;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r1;
import static jdk.vm.ci.aarch64.AArch64.r2;
import static jdk.vm.ci.aarch64.AArch64.r3;
import static jdk.vm.ci.aarch64.AArch64.r11;
import static jdk.vm.ci.aarch64.AArch64.v0;
import static jdk.vm.ci.aarch64.AArch64.v1;
import static jdk.vm.ci.aarch64.AArch64.v2;
import static jdk.vm.ci.aarch64.AArch64.v3;
import static jdk.vm.ci.aarch64.AArch64.v4;
import static jdk.vm.ci.aarch64.AArch64.v5;
import static jdk.vm.ci.aarch64.AArch64.v6;
import static jdk.vm.ci.aarch64.AArch64.v7;
import static jdk.vm.ci.aarch64.AArch64.v8;
import static jdk.vm.ci.aarch64.AArch64.v9;
import static jdk.vm.ci.aarch64.AArch64.v10;
import static jdk.vm.ci.aarch64.AArch64.v11;
import static jdk.vm.ci.aarch64.AArch64.v12;
import static jdk.vm.ci.aarch64.AArch64.v13;
import static jdk.vm.ci.aarch64.AArch64.v14;
import static jdk.vm.ci.aarch64.AArch64.v15;
import static jdk.vm.ci.aarch64.AArch64.v16;
import static jdk.vm.ci.aarch64.AArch64.v17;
import static jdk.vm.ci.aarch64.AArch64.v18;
import static jdk.vm.ci.aarch64.AArch64.v19;
import static jdk.vm.ci.aarch64.AArch64.v20;
import static jdk.vm.ci.aarch64.AArch64.v21;
import static jdk.vm.ci.aarch64.AArch64.v22;
import static jdk.vm.ci.aarch64.AArch64.v23;
import static jdk.vm.ci.aarch64.AArch64.v24;
import static jdk.vm.ci.aarch64.AArch64.v25;
import static jdk.vm.ci.aarch64.AArch64.v26;
import static jdk.vm.ci.aarch64.AArch64.v27;
import static jdk.vm.ci.aarch64.AArch64.v28;
import static jdk.vm.ci.aarch64.AArch64.v29;
import static jdk.vm.ci.aarch64.AArch64.v30;
import static jdk.vm.ci.aarch64.AArch64.v31;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.VectorRegisterSeq;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fe611a2a3386d097f636c15bd4d396a82dc695e/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L6033-L6234",
          sha1 = "3a993e4c57a4c366385e3843e3bbf153aa676ae6")
// @formatter:on
public final class AArch64Kyber12To16Op extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64Kyber12To16Op> TYPE = LIRInstructionClass.create(AArch64Kyber12To16Op.class);

    private static final ArrayDataPointerConstant F00 = pointerConstant(16, new long[]{
                    0x0F000F000F000F00L, 0x0F000F000F000F00L,
    });

    @Def private Value resultValue;
    @Use private Value condensedValue;
    @Use private Value condensedOffsValue;
    @Use private Value parsedValue;
    @Use private Value parsedLengthValue;
    @Temp private Value[] temps;

    public AArch64Kyber12To16Op(AllocatableValue resultValue, AllocatableValue condensedValue, AllocatableValue condensedOffsValue, AllocatableValue parsedValue, AllocatableValue parsedLengthValue) {
        super(TYPE);
        guaranteeFixedRegister(resultValue, r0, "resultValue");
        guaranteeFixedRegister(condensedValue, r0, "condensedValue");
        guaranteeFixedRegister(condensedOffsValue, r1, "condensedOffsValue");
        guaranteeFixedRegister(parsedValue, r2, "parsedValue");
        guaranteeFixedRegister(parsedLengthValue, r3, "parsedLengthValue");
        this.resultValue = resultValue;
        this.condensedValue = condensedValue;
        this.condensedOffsValue = condensedOffsValue;
        this.parsedValue = parsedValue;
        this.parsedLengthValue = parsedLengthValue;
        this.temps = new Value[]{
                        r0.asValue(),
                        r2.asValue(),
                        r3.asValue(),
                        r11.asValue(),
                        v0.asValue(),
                        v1.asValue(),
                        v2.asValue(),
                        v3.asValue(),
                        v4.asValue(),
                        v5.asValue(),
                        v6.asValue(),
                        v7.asValue(),
                        v8.asValue(),
                        v9.asValue(),
                        v10.asValue(),
                        v11.asValue(),
                        v12.asValue(),
                        v13.asValue(),
                        v14.asValue(),
                        v15.asValue(),
                        v16.asValue(),
                        v17.asValue(),
                        v18.asValue(),
                        v19.asValue(),
                        v20.asValue(),
                        v21.asValue(),
                        v22.asValue(),
                        v23.asValue(),
                        v24.asValue(),
                        v25.asValue(),
                        v26.asValue(),
                        v27.asValue(),
                        v28.asValue(),
                        v29.asValue(),
                        v30.asValue(),
                        v31.asValue(),
        };
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register result = asRegister(resultValue);
        Register condensed = asRegister(condensedValue);
        Register condensedOffs = asRegister(condensedOffsValue);
        Register parsed = asRegister(parsedValue);
        Register parsedLength = asRegister(parsedLengthValue);

        // Data is input 96 bytes at a time i.e. in groups of 6 x 16B
        // quadwords so we need a 6 vector sequence for the inputs.
        // Parsing produces 64 shorts, employing two 8 vector
        // sequences to store and combine the intermediate data.
        VectorRegisterSeq vin = VectorRegisterSeq.create(24, 6);
        VectorRegisterSeq va = VectorRegisterSeq.create(0, 8);
        VectorRegisterSeq vb = VectorRegisterSeq.create(16, 8);
        Label loop = new Label();
        Label end = new Label();

        crb.recordDataReferenceInCode(F00);
        masm.adrpAdd(r11);
        masm.fldr(128, v31, createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, r11, 0));
        masm.add(64, condensed, condensed, condensedOffs);

        masm.bind(loop);
        // load 96 (6 x 16B) byte values
        vsLd3Post(masm, vin, FullReg, Byte, condensed);

        // The front half of sequence vin (vin[0], vin[1] and vin[2])
        // holds 48 (16x3) contiguous bytes from memory striped
        // horizontally across each of the 16 byte lanes. Equivalently,
        // that is 16 pairs of 12-bit integers. Likewise the back half
        // holds the next 48 bytes in the same arrangement.

        // Each vector in the front half can also be viewed as a vertical
        // strip across the 16 pairs of 12 bit integers. Each byte in
        // vin[0] stores the low 8 bits of the first int in a pair. Each
        // byte in vin[1] stores the high 4 bits of the first int and the
        // low 4 bits of the second int. Each byte in vin[2] stores the
        // high 8 bits of the second int. Likewise the vectors in second
        // half.

        // Converting the data to 16-bit shorts requires first of all
        // expanding each of the 6 x 16B vectors into 6 corresponding
        // pairs of 8H vectors. Mask, shift and add operations on the
        // resulting vector pairs can be used to combine 4 and 8 bit
        // parts of related 8H vector elements.
        //
        // The middle vectors (vin[2] and vin[5]) are actually expanded
        // twice, one copy manipulated to provide the lower 4 bits
        // belonging to the first short in a pair and another copy
        // manipulated to provide the higher 4 bits belonging to the
        // second short in a pair. This is why the the vector sequences va
        // and vb used to hold the expanded 8H elements are of length 8.

        // Expand vin[0] into va[0:1], and vin[1] into va[2:3] and va[4:5]
        // n.b. target elements 2 and 3 duplicate elements 4 and 5
        masm.neon.ushllVVI(Byte, va.get(0), vin.get(0), 0);
        masm.neon.ushll2VVI(Byte, va.get(1), vin.get(0), 0);
        masm.neon.ushllVVI(Byte, va.get(2), vin.get(1), 0);
        masm.neon.ushll2VVI(Byte, va.get(3), vin.get(1), 0);
        masm.neon.ushllVVI(Byte, va.get(4), vin.get(1), 0);
        masm.neon.ushll2VVI(Byte, va.get(5), vin.get(1), 0);

        // likewise expand vin[3] into vb[0:1], and vin[4] into vb[2:3]
        // and vb[4:5]
        masm.neon.ushllVVI(Byte, vb.get(0), vin.get(3), 0);
        masm.neon.ushll2VVI(Byte, vb.get(1), vin.get(3), 0);
        masm.neon.ushllVVI(Byte, vb.get(2), vin.get(4), 0);
        masm.neon.ushll2VVI(Byte, vb.get(3), vin.get(4), 0);
        masm.neon.ushllVVI(Byte, vb.get(4), vin.get(4), 0);
        masm.neon.ushll2VVI(Byte, vb.get(5), vin.get(4), 0);

        // shift lo byte of copy 1 of the middle stripe into the high byte
        masm.neon.shlVVI(FullReg, HalfWord, va.get(2), va.get(2), 8);
        masm.neon.shlVVI(FullReg, HalfWord, va.get(3), va.get(3), 8);
        masm.neon.shlVVI(FullReg, HalfWord, vb.get(2), vb.get(2), 8);
        masm.neon.shlVVI(FullReg, HalfWord, vb.get(3), vb.get(3), 8);

        // expand vin[2] into va[6:7] and vin[5] into vb[6:7] but this
        // time pre-shifted by 4 to ensure top bits of input 12-bit int
        // are in bit positions [4..11].
        masm.neon.ushllVVI(Byte, va.get(6), vin.get(2), 4);
        masm.neon.ushll2VVI(Byte, va.get(7), vin.get(2), 4);
        masm.neon.ushllVVI(Byte, vb.get(6), vin.get(5), 4);
        masm.neon.ushll2VVI(Byte, vb.get(7), vin.get(5), 4);

        // mask hi 4 bits of the 1st 12-bit int in a pair from copy1 and
        // shift lo 4 bits of the 2nd 12-bit int in a pair to the bottom of
        // copy2
        masm.neon.andVVV(FullReg, va.get(2), va.get(2), v31);
        masm.neon.andVVV(FullReg, va.get(3), va.get(3), v31);
        masm.neon.ushrVVI(FullReg, HalfWord, va.get(4), va.get(4), 4);
        masm.neon.ushrVVI(FullReg, HalfWord, va.get(5), va.get(5), 4);
        masm.neon.andVVV(FullReg, vb.get(2), vb.get(2), v31);
        masm.neon.andVVV(FullReg, vb.get(3), vb.get(3), v31);
        masm.neon.ushrVVI(FullReg, HalfWord, vb.get(4), vb.get(4), 4);
        masm.neon.ushrVVI(FullReg, HalfWord, vb.get(5), vb.get(5), 4);

        // sum hi 4 bits and lo 8 bits of the 1st 12-bit int in each pair and
        // hi 8 bits plus lo 4 bits of the 2nd 12-bit int in each pair
        // n.b. the ordering ensures: i) inputs are consumed before they
        // are overwritten ii) the order of 16-bit results across successive
        // pairs of vectors in va and then vb reflects the order of the
        // corresponding 12-bit inputs
        masm.neon.addVVV(FullReg, HalfWord, va.get(0), va.get(0), va.get(2));
        masm.neon.addVVV(FullReg, HalfWord, va.get(2), va.get(1), va.get(3));
        masm.neon.addVVV(FullReg, HalfWord, va.get(1), va.get(4), va.get(6));
        masm.neon.addVVV(FullReg, HalfWord, va.get(3), va.get(5), va.get(7));
        masm.neon.addVVV(FullReg, HalfWord, vb.get(0), vb.get(0), vb.get(2));
        masm.neon.addVVV(FullReg, HalfWord, vb.get(2), vb.get(1), vb.get(3));
        masm.neon.addVVV(FullReg, HalfWord, vb.get(1), vb.get(4), vb.get(6));
        masm.neon.addVVV(FullReg, HalfWord, vb.get(3), vb.get(5), vb.get(7));

        // store 64 results interleaved as shorts
        vsSt2Post(masm, va.front(), FullReg, HalfWord, parsed);
        vsSt2Post(masm, vb.front(), FullReg, HalfWord, parsed);

        masm.sub(64, parsedLength, parsedLength, 64);
        masm.compare(64, parsedLength, 64);
        masm.branchConditionally(ConditionFlag.GE, loop);
        masm.cbz(64, parsedLength, end);

        // if anything is left it should be a final 72 bytes of input
        // i.e. a final 48 12-bit values. so we handle this by loading
        // 48 bytes into all 16B lanes of vin.front() and only 24
        // bytes into the lower 8B lane of vin.back()
        vsLd3Post(masm, vin.front(), FullReg, Byte, condensed);
        VectorRegisterSeq backVin = vin.back();
        masm.neon.ld3MultipleVVV(HalfReg, Byte, backVin.get(0), backVin.get(1), backVin.get(2), createStructureNoOffsetAddress(condensed));

        // Expand vin[0] into va[0:1], and vin[1] into va[2:3] and va[4:5]
        // n.b. target elements 2 and 3 of va duplicate elements 4 and
        // 5 and target element 2 of vb duplicates element 4.
        masm.neon.ushllVVI(Byte, va.get(0), vin.get(0), 0);
        masm.neon.ushll2VVI(Byte, va.get(1), vin.get(0), 0);
        masm.neon.ushllVVI(Byte, va.get(2), vin.get(1), 0);
        masm.neon.ushll2VVI(Byte, va.get(3), vin.get(1), 0);
        masm.neon.ushllVVI(Byte, va.get(4), vin.get(1), 0);
        masm.neon.ushll2VVI(Byte, va.get(5), vin.get(1), 0);

        // This time expand just the lower 8 lanes
        masm.neon.ushllVVI(Byte, vb.get(0), vin.get(3), 0);
        masm.neon.ushllVVI(Byte, vb.get(2), vin.get(4), 0);
        masm.neon.ushllVVI(Byte, vb.get(4), vin.get(4), 0);

        // shift lo byte of copy 1 of the middle stripe into the high byte
        masm.neon.shlVVI(FullReg, HalfWord, va.get(2), va.get(2), 8);
        masm.neon.shlVVI(FullReg, HalfWord, va.get(3), va.get(3), 8);
        masm.neon.shlVVI(FullReg, HalfWord, vb.get(2), vb.get(2), 8);

        // expand vin[2] into va[6:7] and lower 8 lanes of vin[5] into
        // vb[6] pre-shifted by 4 to ensure top bits of the input 12-bit
        // int are in bit positions [4..11].
        masm.neon.ushllVVI(Byte, va.get(6), vin.get(2), 4);
        masm.neon.ushll2VVI(Byte, va.get(7), vin.get(2), 4);
        masm.neon.ushllVVI(Byte, vb.get(6), vin.get(5), 4);

        // mask hi 4 bits of each 1st 12-bit int in pair from copy1 and
        // shift lo 4 bits of each 2nd 12-bit int in pair to bottom of
        // copy2
        masm.neon.andVVV(FullReg, va.get(2), va.get(2), v31);
        masm.neon.andVVV(FullReg, va.get(3), va.get(3), v31);
        masm.neon.ushrVVI(FullReg, HalfWord, va.get(4), va.get(4), 4);
        masm.neon.ushrVVI(FullReg, HalfWord, va.get(5), va.get(5), 4);
        masm.neon.andVVV(FullReg, vb.get(2), vb.get(2), v31);
        masm.neon.ushrVVI(FullReg, HalfWord, vb.get(4), vb.get(4), 4);

        // sum hi 4 bits and lo 8 bits of each 1st 12-bit int in pair and
        // hi 8 bits plus lo 4 bits of each 2nd 12-bit int in pair

        // n.b. ordering ensures: i) inputs are consumed before they are
        // overwritten ii) order of 16-bit results across succsessive
        // pairs of vectors in va and then lower half of vb reflects order
        // of corresponding 12-bit inputs
        masm.neon.addVVV(FullReg, HalfWord, va.get(0), va.get(0), va.get(2));
        masm.neon.addVVV(FullReg, HalfWord, va.get(2), va.get(1), va.get(3));
        masm.neon.addVVV(FullReg, HalfWord, va.get(1), va.get(4), va.get(6));
        masm.neon.addVVV(FullReg, HalfWord, va.get(3), va.get(5), va.get(7));
        masm.neon.addVVV(FullReg, HalfWord, vb.get(0), vb.get(0), vb.get(2));
        masm.neon.addVVV(FullReg, HalfWord, vb.get(1), vb.get(4), vb.get(6));

        // store 48 results interleaved as shorts
        vsSt2Post(masm, va.front(), FullReg, HalfWord, parsed);
        vsSt2Post(masm, vb.front().front(), FullReg, HalfWord, parsed);

        masm.bind(end);
        masm.mov(32, result, zr);
    }
}
