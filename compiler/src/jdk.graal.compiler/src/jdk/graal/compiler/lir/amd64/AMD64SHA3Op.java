/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.LessEqual;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.NotEqual;
import static jdk.graal.compiler.lir.amd64.AMD64AESEncryptOp.asXMMRegister;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.vm.ci.amd64.AMD64.k1;
import static jdk.vm.ci.amd64.AMD64.k2;
import static jdk.vm.ci.amd64.AMD64.k3;
import static jdk.vm.ci.amd64.AMD64.k4;
import static jdk.vm.ci.amd64.AMD64.k5;
import static jdk.vm.ci.amd64.AMD64.r10;
import static jdk.vm.ci.amd64.AMD64.r11;
import static jdk.vm.ci.amd64.AMD64.r12;
import static jdk.vm.ci.amd64.AMD64.r13;
import static jdk.vm.ci.amd64.AMD64.r14;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.amd64.AMD64.xmm1;
import static jdk.vm.ci.amd64.AMD64.xmm17;
import static jdk.vm.ci.amd64.AMD64.xmm18;
import static jdk.vm.ci.amd64.AMD64.xmm19;
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm20;
import static jdk.vm.ci.amd64.AMD64.xmm21;
import static jdk.vm.ci.amd64.AMD64.xmm22;
import static jdk.vm.ci.amd64.AMD64.xmm23;
import static jdk.vm.ci.amd64.AMD64.xmm24;
import static jdk.vm.ci.amd64.AMD64.xmm25;
import static jdk.vm.ci.amd64.AMD64.xmm26;
import static jdk.vm.ci.amd64.AMD64.xmm27;
import static jdk.vm.ci.amd64.AMD64.xmm28;
import static jdk.vm.ci.amd64.AMD64.xmm29;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm30;
import static jdk.vm.ci.amd64.AMD64.xmm31;
import static jdk.vm.ci.amd64.AMD64.xmm4;
import static jdk.vm.ci.amd64.AMD64.xmm5;
import static jdk.vm.ci.amd64.AMD64.xmm6;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/4994bd594299e91e804438692e068b1c5dd5cc02/src/hotspot/cpu/x86/stubGenerator_x86_64_sha3.cpp#L43-L320",
          sha1 = "1e35ec749256703493e70d96d2818be65c659756")
// @formatter:on
public final class AMD64SHA3Op extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64SHA3Op> TYPE = LIRInstructionClass.create(AMD64SHA3Op.class);

    @Use({OperandFlag.REG}) private Value bufValue;
    @Use({OperandFlag.REG}) private Value stateValue;
    @Use({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value blockSizeValue;
    @Use({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value ofsValue;
    @Use({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value limitValue;

    @Temp({OperandFlag.REG}) private Value[] gprTemps;
    @Temp({OperandFlag.REG}) private Value[] xmmTemps;

    private final boolean multiBlock;

    public AMD64SHA3Op(AllocatableValue bufValue, AllocatableValue stateValue, AllocatableValue blockSizeValue) {
        this(bufValue, stateValue, blockSizeValue, Value.ILLEGAL, Value.ILLEGAL, false);
    }

    public AMD64SHA3Op(AllocatableValue bufValue, AllocatableValue stateValue, AllocatableValue blockSizeValue, AllocatableValue ofsValue, AllocatableValue limitValue, boolean multiBlock) {
        super(TYPE);

        this.bufValue = bufValue;
        this.stateValue = stateValue;
        this.blockSizeValue = blockSizeValue;
        this.ofsValue = ofsValue;
        this.limitValue = limitValue;

        this.multiBlock = multiBlock;

        // r12-r14 will be restored
        if (multiBlock) {
            // For modified value, ensure they are mapped to fixed registers and kill with @Temp
            GraalError.guarantee(asRegister(bufValue).equals(rdi), "expect bufValue at rdi, but was %s", bufValue);
            GraalError.guarantee(asRegister(ofsValue).equals(rcx), "expect ofsValue at rcx, but was %s", ofsValue);

            this.gprTemps = new Value[]{
                            rax.asValue(),
                            rcx.asValue(),
                            rdi.asValue(),
                            r10.asValue(),
                            r11.asValue(),
            };
        } else {
            this.gprTemps = new Value[]{
                            rax.asValue(),
                            r10.asValue(),
                            r11.asValue(),
            };
        }

        this.xmmTemps = new Value[]{
                        k1.asValue(),
                        k2.asValue(),
                        k3.asValue(),
                        k4.asValue(),
                        k5.asValue(),
                        xmm0.asValue(),
                        xmm1.asValue(),
                        xmm2.asValue(),
                        xmm3.asValue(),
                        xmm4.asValue(),
                        xmm5.asValue(),
                        xmm6.asValue(),
                        xmm17.asValue(),
                        xmm18.asValue(),
                        xmm19.asValue(),
                        xmm20.asValue(),
                        xmm20.asValue(),
                        xmm21.asValue(),
                        xmm22.asValue(),
                        xmm23.asValue(),
                        xmm24.asValue(),
                        xmm25.asValue(),
                        xmm26.asValue(),
                        xmm27.asValue(),
                        xmm28.asValue(),
                        xmm29.asValue(),
                        xmm30.asValue(),
                        xmm31.asValue(),
        };
    }

    static ArrayDataPointerConstant roundConstsAddr = pointerConstant(16, new long[]{
                    // Constants
                    0x0000000000000001L, 0x0000000000008082L, 0x800000000000808AL,
                    0x8000000080008000L, 0x000000000000808BL, 0x0000000080000001L,
                    0x8000000080008081L, 0x8000000000008009L, 0x000000000000008AL,
                    0x0000000000000088L, 0x0000000080008009L, 0x000000008000000AL,
                    0x000000008000808BL, 0x800000000000008BL, 0x8000000000008089L,
                    0x8000000000008003L, 0x8000000000008002L, 0x8000000000000080L,
                    0x000000000000800AL, 0x800000008000000AL, 0x8000000080008081L,
                    0x8000000000008080L, 0x0000000080000001L, 0x8000000080008008L
    });

    static ArrayDataPointerConstant permsAndRotsAddr = pointerConstant(16, new long[]{
                    // permutation in combined rho and pi
                    9, 2, 11, 0, 1, 2, 3, 4,   // step 1 and 3
                    8, 1, 9, 2, 11, 4, 12, 0,  // step 2
                    9, 2, 10, 3, 11, 4, 12, 0, // step 4
                    8, 9, 2, 3, 4, 5, 6, 7,    // step 5
                    0, 8, 9, 10, 15, 0, 0, 0,  // step 6
                    4, 5, 8, 9, 6, 7, 10, 11,  // step 7 and 8
                    0, 1, 2, 3, 13, 0, 0, 0,   // step 9
                    2, 3, 0, 1, 11, 0, 0, 0,   // step 10
                    4, 5, 6, 7, 14, 0, 0, 0,   // step 11
                    14, 15, 12, 13, 4, 0, 0, 0, // step 12
                    // size of rotations (after step 5)
                    1, 6, 62, 55, 28, 20, 27, 36,
                    3, 45, 10, 15, 25, 8, 39, 41,
                    44, 43, 21, 18, 2, 61, 56, 14,
                    // rotation of row elements
                    12, 8, 9, 10, 11, 5, 6, 7,
                    9, 10, 11, 12, 8, 5, 6, 7
    });

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register buf = asRegister(bufValue);
        Register state = asRegister(stateValue);
        Register blockSize = asRegister(blockSizeValue);

        Register permsAndRots = r10;
        Register roundConsts = r11;
        Register constant2use = r13;
        Register roundsLeft = r14;

        Label sha3Loop = new Label();
        Label rounds24Loop = new Label();
        Label block104 = new Label();
        Label block136 = new Label();
        Label block144 = new Label();
        Label block168 = new Label();

        masm.push(r12);
        masm.push(r13);
        masm.push(r14);

        masm.leaq(permsAndRots, recordExternalAddress(crb, permsAndRotsAddr));
        masm.leaq(roundConsts, recordExternalAddress(crb, roundConstsAddr));

        // set up the masks
        masm.movl(rax, 0x1F);
        masm.kmovw(k5, rax);
        masm.kshiftrw(k4, k5, 1);
        masm.kshiftrw(k3, k5, 2);
        masm.kshiftrw(k2, k5, 3);
        masm.kshiftrw(k1, k5, 4);

        // load the state
        for (int i = 0; i < 5; i++) {
            masm.evmovdqu64(asXMMRegister(i), k5, new AMD64Address(state, i * 40));
        }

        // load the permutation and rotation constants
        for (int i = 0; i < 15; i++) {
            masm.evmovdqu64(asXMMRegister(i + 17), new AMD64Address(permsAndRots, i * 64));
        }

        masm.align(preferredLoopAlignment(crb));
        masm.bind(sha3Loop);

        // there will be 24 keccak rounds
        masm.movl(roundsLeft, 24);
        // load round_constants base
        masm.movq(constant2use, roundConsts);

        // load input: 72, 104, 136, 144 or 168 bytes
        // i.e. 5+4, 2*5+3, 3*5+2, 3*5+3 or 4*5+1 longs
        masm.evpxorq(xmm0, k5, xmm0, new AMD64Address(buf, 0));

        // if(blockSize == 72) SHA3-512
        masm.cmplAndJcc(blockSize, 72, NotEqual, block104, false);
        masm.evpxorq(xmm1, k4, xmm1, new AMD64Address(buf, 40));
        masm.jmp(rounds24Loop);

        // if(blockSize == 104) SHA3-384
        masm.bind(block104);
        masm.cmplAndJcc(blockSize, 104, NotEqual, block136, false);
        masm.evpxorq(xmm1, k5, xmm1, new AMD64Address(buf, 40));
        masm.evpxorq(xmm2, k3, xmm2, new AMD64Address(buf, 80));
        masm.jmp(rounds24Loop);

        // if(blockSize == 136) SHA3-256 and SHAKE256
        masm.bind(block136);
        masm.cmplAndJcc(blockSize, 136, NotEqual, block144, false);
        masm.evpxorq(xmm1, k5, xmm1, new AMD64Address(buf, 40));
        masm.evpxorq(xmm2, k5, xmm2, new AMD64Address(buf, 80));
        masm.evpxorq(xmm3, k2, xmm3, new AMD64Address(buf, 120));
        masm.jmp(rounds24Loop);

        // if(blockSize == 144) SHA3-224
        masm.bind(block144);
        masm.cmplAndJcc(blockSize, 144, NotEqual, block168, false);
        masm.evpxorq(xmm1, k5, xmm1, new AMD64Address(buf, 40));
        masm.evpxorq(xmm2, k5, xmm2, new AMD64Address(buf, 80));
        masm.evpxorq(xmm3, k3, xmm3, new AMD64Address(buf, 120));
        masm.jmp(rounds24Loop);

        // if(blockSize == 168) SHAKE128
        masm.bind(block168);
        masm.evpxorq(xmm1, k5, xmm1, new AMD64Address(buf, 40));
        masm.evpxorq(xmm2, k5, xmm2, new AMD64Address(buf, 80));
        masm.evpxorq(xmm3, k5, xmm3, new AMD64Address(buf, 120));
        masm.evpxorq(xmm4, k1, xmm4, new AMD64Address(buf, 160));

        // The 24 rounds of the keccak transformation.
        // The implementation closely follows the Java version, with the state
        // array "rows" in the lowest 5 64-bit slots of zmm0 - zmm4, i.e.
        // each row of the SHA3 specification is located in one zmm register.
        masm.align(preferredLoopAlignment(crb));
        masm.bind(rounds24Loop);
        masm.subl(roundsLeft, 1);

        masm.evmovdqu16(xmm5, xmm0);
        // vpternlogq(x, 150, y, z) does x = x ^ y ^ z
        masm.evpternlogq(xmm5, 150, xmm1, xmm2);
        masm.evpternlogq(xmm5, 150, xmm3, xmm4);
        // Now the "c row", i.e. c0-c4 are in zmm5.
        // Rotate each element of the c row by one bit to zmm6, call the
        // rotated version c'.
        masm.evprolq(xmm6, xmm5, 1);
        // Rotate elementwise the c row so that c4 becomes c0,
        // c0 becomes c1, etc.
        masm.evpermt2q(xmm5, xmm30, xmm5);
        // rotate elementwise the c' row so that c'0 becomes c'4,
        // c'1 becomes c'0, etc.
        masm.evpermt2q(xmm6, xmm31, xmm6);
        masm.evpternlogq(xmm0, 150, xmm5, xmm6);
        masm.evpternlogq(xmm1, 150, xmm5, xmm6);
        masm.evpternlogq(xmm2, 150, xmm5, xmm6);
        masm.evpternlogq(xmm3, 150, xmm5, xmm6);
        masm.evpternlogq(xmm4, 150, xmm5, xmm6);
        // Now the theta mapping has been finished.

        // Do the cyclical permutation of the 24 moving state elements
        // and the required rotations within each element (the combined
        // rho and pi steps).
        masm.evpermt2q(xmm4, xmm17, xmm3);
        masm.evpermt2q(xmm3, xmm18, xmm2);
        masm.evpermt2q(xmm2, xmm17, xmm1);
        masm.evpermt2q(xmm1, xmm19, xmm0);
        masm.evpermt2q(xmm4, xmm20, xmm2);
        // The 24 moving elements are now in zmm1, zmm3 and zmm4,
        // do the rotations now.
        masm.evprolvq(xmm1, xmm1, xmm27);
        masm.evprolvq(xmm3, xmm3, xmm28);
        masm.evprolvq(xmm4, xmm4, xmm29);
        masm.evmovdqu16(xmm2, xmm1);
        masm.evmovdqu16(xmm5, xmm3);
        masm.evpermt2q(xmm0, xmm21, xmm4);
        masm.evpermt2q(xmm1, xmm22, xmm3);
        masm.evpermt2q(xmm5, xmm22, xmm2);
        masm.evmovdqu16(xmm3, xmm1);
        masm.evmovdqu16(xmm2, xmm5);
        masm.evpermt2q(xmm1, xmm23, xmm4);
        masm.evpermt2q(xmm2, xmm24, xmm4);
        masm.evpermt2q(xmm3, xmm25, xmm4);
        masm.evpermt2q(xmm4, xmm26, xmm5);
        // The combined rho and pi steps are done.

        // Do the chi step (the same operation on all 5 rows).
        // vpternlogq(x, 180, y, z) does x = x ^ (y & ~z).
        masm.evpermt2q(xmm5, xmm31, xmm0);
        masm.evpermt2q(xmm6, xmm31, xmm5);
        masm.evpternlogq(xmm0, 180, xmm6, xmm5);

        masm.evpermt2q(xmm5, xmm31, xmm1);
        masm.evpermt2q(xmm6, xmm31, xmm5);
        masm.evpternlogq(xmm1, 180, xmm6, xmm5);

        // xor the round constant into a0 (the lowest 64 bits of zmm0
        masm.evpxorq(xmm0, k1, xmm0, new AMD64Address(constant2use, 0));
        masm.addq(constant2use, 8);

        masm.evpermt2q(xmm5, xmm31, xmm2);
        masm.evpermt2q(xmm6, xmm31, xmm5);
        masm.evpternlogq(xmm2, 180, xmm6, xmm5);

        masm.evpermt2q(xmm5, xmm31, xmm3);
        masm.evpermt2q(xmm6, xmm31, xmm5);
        masm.evpternlogq(xmm3, 180, xmm6, xmm5);

        masm.evpermt2q(xmm5, xmm31, xmm4);
        masm.evpermt2q(xmm6, xmm31, xmm5);
        masm.evpternlogq(xmm4, 180, xmm6, xmm5);
        masm.cmplAndJcc(roundsLeft, 0, NotEqual, rounds24Loop, false);

        if (multiBlock) {
            Register ofs = asRegister(ofsValue);
            Register limit = asRegister(limitValue);

            masm.addq(buf, blockSize);
            masm.addl(ofs, blockSize);
            masm.cmplAndJcc(ofs, limit, LessEqual, sha3Loop, false);
            masm.movq(rax, ofs); // return ofs
        } else {
            masm.xorq(rax, rax); // return 0
        }

        // store the state
        for (int i = 0; i < 5; i++) {
            masm.evmovdqu64(new AMD64Address(state, i * 40), k5, asXMMRegister(i));
        }

        masm.pop(r14);
        masm.pop(r13);
        masm.pop(r12);
    }
}
