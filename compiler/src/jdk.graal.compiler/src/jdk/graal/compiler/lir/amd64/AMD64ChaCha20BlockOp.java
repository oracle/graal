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
package jdk.graal.compiler.lir.amd64;

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.EVEXTRACTI32X4;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VEXTRACTI128;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU32;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.EVPSHUFD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VBROADCASTF128;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPADDD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPXORD;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.vm.ci.amd64.AMD64.r8;
import static jdk.vm.ci.amd64.AMD64.r9;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.amd64.AMD64.xmm1;
import static jdk.vm.ci.amd64.AMD64.xmm10;
import static jdk.vm.ci.amd64.AMD64.xmm11;
import static jdk.vm.ci.amd64.AMD64.xmm12;
import static jdk.vm.ci.amd64.AMD64.xmm13;
import static jdk.vm.ci.amd64.AMD64.xmm14;
import static jdk.vm.ci.amd64.AMD64.xmm15;
import static jdk.vm.ci.amd64.AMD64.xmm16;
import static jdk.vm.ci.amd64.AMD64.xmm17;
import static jdk.vm.ci.amd64.AMD64.xmm18;
import static jdk.vm.ci.amd64.AMD64.xmm19;
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm20;
import static jdk.vm.ci.amd64.AMD64.xmm21;
import static jdk.vm.ci.amd64.AMD64.xmm22;
import static jdk.vm.ci.amd64.AMD64.xmm23;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm4;
import static jdk.vm.ci.amd64.AMD64.xmm5;
import static jdk.vm.ci.amd64.AMD64.xmm6;
import static jdk.vm.ci.amd64.AMD64.xmm7;
import static jdk.vm.ci.amd64.AMD64.xmm8;
import static jdk.vm.ci.amd64.AMD64.xmm9;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/c59e44a7aa2aeff0823830b698d524523b996650/src/hotspot/cpu/x86/stubGenerator_x86_64_chacha.cpp#L112-L584",
          sha1 = "11d31b17bc6575fc5b32fa820440fc3852516d94")
// @formatter:on
public final class AMD64ChaCha20BlockOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64ChaCha20BlockOp> TYPE = LIRInstructionClass.create(AMD64ChaCha20BlockOp.class);

    @Use({OperandFlag.REG}) private Value stateValue;
    @Use({OperandFlag.REG}) private Value resultValue;
    @Def({OperandFlag.REG}) private Value outputLengthValue;

    @Temp({OperandFlag.REG}) private Value[] temps;

    private static final ArrayDataPointerConstant CC20_COUNTER_ADD_AVX = pointerConstant(16, new long[]{
                    0x0000000000000000L, 0x0000000000000000L,
                    0x0000000000000001L, 0x0000000000000000L,
                    0x0000000000000002L, 0x0000000000000000L,
                    0x0000000000000002L, 0x0000000000000000L,
    });

    private static final ArrayDataPointerConstant CC20_COUNTER_ADD_AVX512 = pointerConstant(16, new long[]{
                    0x0000000000000000L, 0x0000000000000000L,
                    0x0000000000000001L, 0x0000000000000000L,
                    0x0000000000000002L, 0x0000000000000000L,
                    0x0000000000000003L, 0x0000000000000000L,

                    0x0000000000000004L, 0x0000000000000000L,
                    0x0000000000000004L, 0x0000000000000000L,
                    0x0000000000000004L, 0x0000000000000000L,
                    0x0000000000000004L, 0x0000000000000000L,
    });

    private static final ArrayDataPointerConstant CC20_LROT_CONSTS = pointerConstant(16, new long[]{
                    0x0605040702010003L, 0x0E0D0C0F0A09080BL,
                    0x0605040702010003L, 0x0E0D0C0F0A09080BL,

                    0x0504070601000302L, 0x0D0C0F0E09080B0AL,
                    0x0504070601000302L, 0x0D0C0F0E09080B0AL,
    });

    public AMD64ChaCha20BlockOp(AllocatableValue state, AllocatableValue result, AllocatableValue outputLength) {
        super(TYPE);

        GraalError.guarantee(asRegister(state).equals(rdi), "expect stateValue at rdi, but was %s", state);
        GraalError.guarantee(asRegister(result).equals(rsi), "expect resultValue at rsi, but was %s", result);
        GraalError.guarantee(asRegister(outputLength).equals(rax), "expect outputLengthValue at rax, but was %s", outputLength);

        this.stateValue = state;
        this.resultValue = result;
        this.outputLengthValue = outputLength;
        this.temps = new Value[]{
                        r8.asValue(),
                        r9.asValue(),
                        xmm0.asValue(),
                        xmm1.asValue(),
                        xmm2.asValue(),
                        xmm3.asValue(),
                        xmm4.asValue(),
                        xmm5.asValue(),
                        xmm6.asValue(),
                        xmm7.asValue(),
                        xmm8.asValue(),
                        xmm9.asValue(),
                        xmm10.asValue(),
                        xmm11.asValue(),
                        xmm12.asValue(),
                        xmm13.asValue(),
                        xmm14.asValue(),
                        xmm15.asValue(),
                        xmm16.asValue(),
                        xmm17.asValue(),
                        xmm18.asValue(),
                        xmm19.asValue(),
                        xmm20.asValue(),
                        xmm21.asValue(),
                        xmm22.asValue(),
                        xmm23.asValue(),
        };
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        GraalError.guarantee(stateValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid stateValue kind: %s", stateValue);
        GraalError.guarantee(resultValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid resultValue kind: %s", resultValue);
        GraalError.guarantee(outputLengthValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid outputLengthValue kind: %s", outputLengthValue);

        Register state = asRegister(stateValue);
        Register result = asRegister(resultValue);
        Register outLength = asRegister(outputLengthValue);

        if (masm.supports(AMD64.CPUFeature.AVX512F)) {
            emitChaCha20BlockAvx512(crb, masm, state, result);
            masm.movq(outLength, 1024L);
            return;
        }

        if (masm.supports(AMD64.CPUFeature.AVX2)) {
            emitChaCha20BlockAvx(crb, masm, state, result, AVXSize.YMM, 256);
            masm.movq(outLength, 256L);
            masm.vzeroupper();
            return;
        }

        emitChaCha20BlockAvx(crb, masm, state, result, AVXSize.XMM, 128);
        masm.movq(outLength, 128L);
    }

    private static void emitChaCha20BlockAvx(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register state, Register result, AVXSize vectorLen, int outlen) {
        Label twoRounds = new Label();
        Register loopCounter = r8;
        Register rotAddr = r9;

        Register aState = xmm0;
        Register bState = xmm1;
        Register cState = xmm2;
        Register dState = xmm3;
        Register a1Vec = xmm4;
        Register b1Vec = xmm5;
        Register c1Vec = xmm6;
        Register d1Vec = xmm7;
        Register a2Vec = xmm8;
        Register b2Vec = xmm9;
        Register c2Vec = xmm10;
        Register d2Vec = xmm11;
        Register scratch = xmm12;
        Register d2State = xmm13;
        Register lrot8 = xmm14;
        Register lrot16 = xmm15;

        // Load the initial state in columnar orientation and then copy
        // that starting state to the working register set.
        // Also load the address of the add mask for later use in handling
        // multi-block counter increments.
        masm.leaq(rotAddr, recordExternalAddress(crb, CC20_LROT_CONSTS));
        masm.leaq(rax, recordExternalAddress(crb, CC20_COUNTER_ADD_AVX));

        if (vectorLen == AVXSize.XMM) {
            // Bytes 0 - 15 -> a1Vec
            masm.movdqu(aState, new AMD64Address(state, 0));
            // Bytes 16 - 31 -> b1Vec
            masm.movdqu(bState, new AMD64Address(state, 16));
            // Bytes 32 - 47 -> c1Vec
            masm.movdqu(cState, new AMD64Address(state, 32));
            // Bytes 48 - 63 -> d1Vec
            masm.movdqu(dState, new AMD64Address(state, 48));

            masm.movdqu(a1Vec, aState);
            masm.movdqu(b1Vec, bState);
            masm.movdqu(c1Vec, cState);
            masm.movdqu(d1Vec, dState);

            masm.movdqu(a2Vec, aState);
            masm.movdqu(b2Vec, bState);
            masm.movdqu(c2Vec, cState);
            masm.vpaddd(d2State, dState, new AMD64Address(rax, 16), vectorLen);
            masm.movdqu(d2Vec, d2State);
            // Load 8-bit lrot const
            masm.movdqu(lrot8, new AMD64Address(rotAddr, 0));
            // Load 16-bit lrot const
            masm.movdqu(lrot16, new AMD64Address(rotAddr, 32));
        } else {
            // We will broadcast each 128-bit segment of the state array into
            // the high and low halves of ymm state registers. Then apply the add
            // mask to the dState register. These will then be copied into the
            // a/b/c/d1Vec working registers.
            VBROADCASTF128.emit(masm, AVXSize.YMM, aState, new AMD64Address(state, 0));
            VBROADCASTF128.emit(masm, AVXSize.YMM, bState, new AMD64Address(state, 16));
            VBROADCASTF128.emit(masm, AVXSize.YMM, cState, new AMD64Address(state, 32));
            VBROADCASTF128.emit(masm, AVXSize.YMM, dState, new AMD64Address(state, 48));
            masm.vpaddd(dState, dState, new AMD64Address(rax, 0), AVXSize.YMM);
            masm.vpaddd(d2State, dState, new AMD64Address(rax, 32), AVXSize.YMM);

            masm.vmovdqu(a1Vec, aState);
            masm.vmovdqu(b1Vec, bState);
            masm.vmovdqu(c1Vec, cState);
            masm.vmovdqu(d1Vec, dState);

            masm.vmovdqu(a2Vec, aState);
            masm.vmovdqu(b2Vec, bState);
            masm.vmovdqu(c2Vec, cState);
            masm.vmovdqu(d2Vec, d2State);
            // Load 8-bit lrot const
            masm.vmovdqu(lrot8, new AMD64Address(rotAddr, 0));
            // Load 16-bit lrot const
            masm.vmovdqu(lrot16, new AMD64Address(rotAddr, 32));
        }

        // Set 10 2-round iterations
        masm.movl(loopCounter, 10);
        masm.bind(twoRounds);

        // @formatter:off
        // The first quarter round macro call covers the first 4 QR operations:
        //  Qround(state, 0, 4, 8,12)
        //  Qround(state, 1, 5, 9,13)
        //  Qround(state, 2, 6,10,14)
        //  Qround(state, 3, 7,11,15)
        // @formatter:on
        quarterRoundAvx(masm, a1Vec, b1Vec, c1Vec, d1Vec, scratch, lrot8, lrot16, vectorLen);
        quarterRoundAvx(masm, a2Vec, b2Vec, c2Vec, d2Vec, scratch, lrot8, lrot16, vectorLen);

        // Shuffle the b1Vec/c1Vec/d1Vec to reorganize the state vectors
        // to diagonals. The a1Vec does not need to change orientation.
        shiftLaneOrg(masm, b1Vec, c1Vec, d1Vec, vectorLen, true);
        shiftLaneOrg(masm, b2Vec, c2Vec, d2Vec, vectorLen, true);

        // @formatter:off
        // The second set of operations on the vectors covers the second 4 quarter
        // round operations, now acting on the diagonals:
        //  Qround(state, 0, 5,10,15)
        //  Qround(state, 1, 6,11,12)
        //  Qround(state, 2, 7, 8,13)
        //  Qround(state, 3, 4, 9,14)
        // @formatter:on
        quarterRoundAvx(masm, a1Vec, b1Vec, c1Vec, d1Vec, scratch, lrot8, lrot16, vectorLen);
        quarterRoundAvx(masm, a2Vec, b2Vec, c2Vec, d2Vec, scratch, lrot8, lrot16, vectorLen);

        // Before we start the next iteration, we need to perform shuffles
        // on the b/c/d vectors to move them back to columnar organizations
        // from their current diagonal orientation.
        shiftLaneOrg(masm, b1Vec, c1Vec, d1Vec, vectorLen, false);
        shiftLaneOrg(masm, b2Vec, c2Vec, d2Vec, vectorLen, false);

        masm.decrementq(loopCounter, 1);
        masm.jcc(ConditionFlag.NotZero, twoRounds);

        // Add the original start state back into the current state.
        masm.vpaddd(a1Vec, a1Vec, aState, vectorLen);
        masm.vpaddd(b1Vec, b1Vec, bState, vectorLen);
        masm.vpaddd(c1Vec, c1Vec, cState, vectorLen);
        masm.vpaddd(d1Vec, d1Vec, dState, vectorLen);

        masm.vpaddd(a2Vec, a2Vec, aState, vectorLen);
        masm.vpaddd(b2Vec, b2Vec, bState, vectorLen);
        masm.vpaddd(c2Vec, c2Vec, cState, vectorLen);
        masm.vpaddd(d2Vec, d2Vec, d2State, vectorLen);

        // Write the data to the keystream array
        if (outlen == 128) {
            masm.movdqu(new AMD64Address(result, 0), a1Vec);
            masm.movdqu(new AMD64Address(result, 16), b1Vec);
            masm.movdqu(new AMD64Address(result, 32), c1Vec);
            masm.movdqu(new AMD64Address(result, 48), d1Vec);
            masm.movdqu(new AMD64Address(result, 64), a2Vec);
            masm.movdqu(new AMD64Address(result, 80), b2Vec);
            masm.movdqu(new AMD64Address(result, 96), c2Vec);
            masm.movdqu(new AMD64Address(result, 112), d2Vec);
        } else {
            // Each half of the YMM has to be written 64 bytes apart from
            // each other in memory so the final keystream buffer holds
            // two consecutive keystream blocks.
            VEXTRACTI128.emit(masm, AVXSize.YMM, new AMD64Address(result, 0), a1Vec, 0);
            VEXTRACTI128.emit(masm, AVXSize.YMM, new AMD64Address(result, 64), a1Vec, 1);
            VEXTRACTI128.emit(masm, AVXSize.YMM, new AMD64Address(result, 16), b1Vec, 0);
            VEXTRACTI128.emit(masm, AVXSize.YMM, new AMD64Address(result, 80), b1Vec, 1);
            VEXTRACTI128.emit(masm, AVXSize.YMM, new AMD64Address(result, 32), c1Vec, 0);
            VEXTRACTI128.emit(masm, AVXSize.YMM, new AMD64Address(result, 96), c1Vec, 1);
            VEXTRACTI128.emit(masm, AVXSize.YMM, new AMD64Address(result, 48), d1Vec, 0);
            VEXTRACTI128.emit(masm, AVXSize.YMM, new AMD64Address(result, 112), d1Vec, 1);

            VEXTRACTI128.emit(masm, AVXSize.YMM, new AMD64Address(result, 128), a2Vec, 0);
            VEXTRACTI128.emit(masm, AVXSize.YMM, new AMD64Address(result, 192), a2Vec, 1);
            VEXTRACTI128.emit(masm, AVXSize.YMM, new AMD64Address(result, 144), b2Vec, 0);
            VEXTRACTI128.emit(masm, AVXSize.YMM, new AMD64Address(result, 208), b2Vec, 1);
            VEXTRACTI128.emit(masm, AVXSize.YMM, new AMD64Address(result, 160), c2Vec, 0);
            VEXTRACTI128.emit(masm, AVXSize.YMM, new AMD64Address(result, 224), c2Vec, 1);
            VEXTRACTI128.emit(masm, AVXSize.YMM, new AMD64Address(result, 176), d2Vec, 0);
            VEXTRACTI128.emit(masm, AVXSize.YMM, new AMD64Address(result, 240), d2Vec, 1);
        }
    }

    private static void emitChaCha20BlockAvx512(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register state, Register result) {
        Label twoRounds = new Label();

        Register loopCounter = r8;

        Register aState = xmm0;
        Register bState = xmm1;
        Register cState = xmm2;
        Register dState = xmm3;
        Register a1Vec = xmm4;
        Register b1Vec = xmm5;
        Register c1Vec = xmm6;
        Register d1Vec = xmm7;
        Register a2Vec = xmm8;
        Register b2Vec = xmm9;
        Register c2Vec = xmm10;
        Register d2Vec = xmm11;
        Register a3Vec = xmm12;
        Register b3Vec = xmm13;
        Register c3Vec = xmm14;
        Register d3Vec = xmm15;
        Register a4Vec = xmm16;
        Register b4Vec = xmm17;
        Register c4Vec = xmm18;
        Register d4Vec = xmm19;
        Register d2State = xmm20;
        Register d3State = xmm21;
        Register d4State = xmm22;
        Register scratch = xmm23;

        // Load the initial state in columnar orientation.
        // We will broadcast each 128-bit segment of the state array into
        // all four double-quadword slots on ZMM State registers. They will
        // be copied into the working ZMM registers and then added back in
        // at the very end of the block function. The add mask should be
        // applied to the dState register so it does not need to be fetched
        // when adding the start state back into the final working state.
        masm.leaq(rax, recordExternalAddress(crb, CC20_COUNTER_ADD_AVX512));
        masm.evbroadcasti32x4(aState, new AMD64Address(state, 0));
        masm.evbroadcasti32x4(bState, new AMD64Address(state, 16));
        masm.evbroadcasti32x4(cState, new AMD64Address(state, 32));
        masm.evbroadcasti32x4(dState, new AMD64Address(state, 48));
        EVPADDD.emit(masm, AVXSize.ZMM, dState, dState, new AMD64Address(rax, 0));
        EVMOVDQU32.emit(masm, AVXSize.ZMM, scratch, new AMD64Address(rax, 64));
        EVPADDD.emit(masm, AVXSize.ZMM, d2State, dState, scratch);
        EVPADDD.emit(masm, AVXSize.ZMM, d3State, d2State, scratch);
        EVPADDD.emit(masm, AVXSize.ZMM, d4State, d3State, scratch);

        EVMOVDQU32.emit(masm, AVXSize.ZMM, a1Vec, aState);
        EVMOVDQU32.emit(masm, AVXSize.ZMM, b1Vec, bState);
        EVMOVDQU32.emit(masm, AVXSize.ZMM, c1Vec, cState);
        EVMOVDQU32.emit(masm, AVXSize.ZMM, d1Vec, dState);

        EVMOVDQU32.emit(masm, AVXSize.ZMM, a2Vec, aState);
        EVMOVDQU32.emit(masm, AVXSize.ZMM, b2Vec, bState);
        EVMOVDQU32.emit(masm, AVXSize.ZMM, c2Vec, cState);
        EVMOVDQU32.emit(masm, AVXSize.ZMM, d2Vec, d2State);

        EVMOVDQU32.emit(masm, AVXSize.ZMM, a3Vec, aState);
        EVMOVDQU32.emit(masm, AVXSize.ZMM, b3Vec, bState);
        EVMOVDQU32.emit(masm, AVXSize.ZMM, c3Vec, cState);
        EVMOVDQU32.emit(masm, AVXSize.ZMM, d3Vec, d3State);

        EVMOVDQU32.emit(masm, AVXSize.ZMM, a4Vec, aState);
        EVMOVDQU32.emit(masm, AVXSize.ZMM, b4Vec, bState);
        EVMOVDQU32.emit(masm, AVXSize.ZMM, c4Vec, cState);
        EVMOVDQU32.emit(masm, AVXSize.ZMM, d4Vec, d4State);

        // Set 10 2-round iterations
        masm.movl(loopCounter, 10);
        masm.bind(twoRounds);

        // @formatter:off
        // The first set of operations on the vectors covers the first 4 quarter
        // round operations:
        //  Qround(state, 0, 4, 8,12)
        //  Qround(state, 1, 5, 9,13)
        //  Qround(state, 2, 6,10,14)
        //  Qround(state, 3, 7,11,15)
        // @formatter:on
        quarterRoundAvx512(masm, a1Vec, b1Vec, c1Vec, d1Vec, scratch);
        quarterRoundAvx512(masm, a2Vec, b2Vec, c2Vec, d2Vec, scratch);
        quarterRoundAvx512(masm, a3Vec, b3Vec, c3Vec, d3Vec, scratch);
        quarterRoundAvx512(masm, a4Vec, b4Vec, c4Vec, d4Vec, scratch);

        // Shuffle the b1Vec/c1Vec/d1Vec to reorganize the state vectors
        // to diagonals. The a1Vec does not need to change orientation.
        shiftLaneOrg(masm, b1Vec, c1Vec, d1Vec, AVXSize.ZMM, true);
        shiftLaneOrg(masm, b2Vec, c2Vec, d2Vec, AVXSize.ZMM, true);
        shiftLaneOrg(masm, b3Vec, c3Vec, d3Vec, AVXSize.ZMM, true);
        shiftLaneOrg(masm, b4Vec, c4Vec, d4Vec, AVXSize.ZMM, true);

        // @formatter:off
        // The second set of operations on the vectors covers the second 4 quarter
        // round operations, now acting on the diagonals:
        //  Qround(state, 0, 5,10,15)
        //  Qround(state, 1, 6,11,12)
        //  Qround(state, 2, 7, 8,13)
        //  Qround(state, 3, 4, 9,14)
        // @formatter:on
        quarterRoundAvx512(masm, a1Vec, b1Vec, c1Vec, d1Vec, scratch);
        quarterRoundAvx512(masm, a2Vec, b2Vec, c2Vec, d2Vec, scratch);
        quarterRoundAvx512(masm, a3Vec, b3Vec, c3Vec, d3Vec, scratch);
        quarterRoundAvx512(masm, a4Vec, b4Vec, c4Vec, d4Vec, scratch);

        // Before we start the next iteration, we need to perform shuffles
        // on the b/c/d vectors to move them back to columnar organizations
        // from their current diagonal orientation.
        shiftLaneOrg(masm, b1Vec, c1Vec, d1Vec, AVXSize.ZMM, false);
        shiftLaneOrg(masm, b2Vec, c2Vec, d2Vec, AVXSize.ZMM, false);
        shiftLaneOrg(masm, b3Vec, c3Vec, d3Vec, AVXSize.ZMM, false);
        shiftLaneOrg(masm, b4Vec, c4Vec, d4Vec, AVXSize.ZMM, false);

        masm.decrementq(loopCounter, 1);
        masm.jcc(ConditionFlag.NotZero, twoRounds);

        // Add the initial state now held on the a/b/c/dState registers to the
        // final working register values. We will also add in the counter add
        // mask onto zmm3 after adding in the start state.
        EVPADDD.emit(masm, AVXSize.ZMM, a1Vec, a1Vec, aState);
        EVPADDD.emit(masm, AVXSize.ZMM, b1Vec, b1Vec, bState);
        EVPADDD.emit(masm, AVXSize.ZMM, c1Vec, c1Vec, cState);
        EVPADDD.emit(masm, AVXSize.ZMM, d1Vec, d1Vec, dState);

        EVPADDD.emit(masm, AVXSize.ZMM, a2Vec, a2Vec, aState);
        EVPADDD.emit(masm, AVXSize.ZMM, b2Vec, b2Vec, bState);
        EVPADDD.emit(masm, AVXSize.ZMM, c2Vec, c2Vec, cState);
        EVPADDD.emit(masm, AVXSize.ZMM, d2Vec, d2Vec, d2State);

        EVPADDD.emit(masm, AVXSize.ZMM, a3Vec, a3Vec, aState);
        EVPADDD.emit(masm, AVXSize.ZMM, b3Vec, b3Vec, bState);
        EVPADDD.emit(masm, AVXSize.ZMM, c3Vec, c3Vec, cState);
        EVPADDD.emit(masm, AVXSize.ZMM, d3Vec, d3Vec, d3State);

        EVPADDD.emit(masm, AVXSize.ZMM, a4Vec, a4Vec, aState);
        EVPADDD.emit(masm, AVXSize.ZMM, b4Vec, b4Vec, bState);
        EVPADDD.emit(masm, AVXSize.ZMM, c4Vec, c4Vec, cState);
        EVPADDD.emit(masm, AVXSize.ZMM, d4Vec, d4Vec, d4State);

        // Write the ZMM state registers out to the key stream buffer
        // Each ZMM is divided into 4 128-bit segments. Each segment
        // is written to memory at 64-byte displacements from one
        // another. The result is that all 4 blocks will be in their
        // proper order when serialized.
        keystreamCollateAvx512(masm, a1Vec, b1Vec, c1Vec, d1Vec, result, 0);
        keystreamCollateAvx512(masm, a2Vec, b2Vec, c2Vec, d2Vec, result, 256);
        keystreamCollateAvx512(masm, a3Vec, b3Vec, c3Vec, d3Vec, result, 512);
        keystreamCollateAvx512(masm, a4Vec, b4Vec, c4Vec, d4Vec, result, 768);
    }

    /**
     * Provide a function that implements the ChaCha20 quarter round function.
     *
     * @param aVec the SIMD register containing only the "a" values
     * @param bVec the SIMD register containing only the "b" values
     * @param cVec the SIMD register containing only the "c" values
     * @param dVec the SIMD register containing only the "d" values
     * @param scratch SIMD register used for non-byte-aligned left rotations
     * @param lrot8 shuffle control mask for an 8-byte left rotation (32-bit lane)
     * @param lrot16 shuffle control mask for a 16-byte left rotation (32-bit lane)
     * @param vectorLen the length of the vector
     */
    private static void quarterRoundAvx(AMD64MacroAssembler masm,
                    Register aVec,
                    Register bVec,
                    Register cVec,
                    Register dVec,
                    Register scratch,
                    Register lrot8,
                    Register lrot16,
                    AVXSize vectorLen) {
        // a += b; d ^= a; d <<<= 16
        masm.vpaddd(aVec, aVec, bVec, vectorLen);
        masm.vpxor(dVec, dVec, aVec, vectorLen);
        masm.vpshufb(dVec, dVec, lrot16, vectorLen);

        // c += d; b ^= c; b <<<= 12 (b << 12 | scratch >>> 20)
        masm.vpaddd(cVec, cVec, dVec, vectorLen);
        masm.vpxor(bVec, bVec, cVec, vectorLen);
        masm.vpsrld(scratch, bVec, 20, vectorLen);
        masm.vpslld(bVec, bVec, 12, vectorLen);
        masm.vpor(bVec, bVec, scratch, vectorLen);

        // a += b; d ^= a; d <<<= 8 (d << 8 | scratch >>> 24)
        masm.vpaddd(aVec, aVec, bVec, vectorLen);
        masm.vpxor(dVec, dVec, aVec, vectorLen);
        masm.vpshufb(dVec, dVec, lrot8, vectorLen);

        // c += d; b ^= c; b <<<= 7 (b << 7 | scratch >>> 25)
        masm.vpaddd(cVec, cVec, dVec, vectorLen);
        masm.vpxor(bVec, bVec, cVec, vectorLen);
        masm.vpsrld(scratch, bVec, 25, vectorLen);
        masm.vpslld(bVec, bVec, 7, vectorLen);
        masm.vpor(bVec, bVec, scratch, vectorLen);
    }

    /**
     * Provide a function that implements the ChaCha20 quarter round function.
     *
     * @param aVec the SIMD register containing only the "a" values
     * @param bVec the SIMD register containing only the "b" values
     * @param cVec the SIMD register containing only the "c" values
     * @param dVec the SIMD register containing only the "d" values
     * @param scratch SIMD register used for non-byte-aligned left rotations
     */
    private static void quarterRoundAvx512(AMD64MacroAssembler masm,
                    Register aVec,
                    Register bVec,
                    Register cVec,
                    Register dVec,
                    Register scratch) {
        // a += b; d ^= a; d <<<= 16
        EVPADDD.emit(masm, AVXSize.ZMM, aVec, aVec, bVec);
        EVPXORD.emit(masm, AVXSize.ZMM, dVec, dVec, aVec);
        masm.evprold(dVec, dVec, 16);

        // c += d; b ^= c; b <<<= 12 (b << 12 | scratch >>> 20)
        EVPADDD.emit(masm, AVXSize.ZMM, cVec, cVec, dVec);
        EVPXORD.emit(masm, AVXSize.ZMM, bVec, bVec, cVec);
        masm.evprold(bVec, bVec, 12);

        // a += b; d ^= a; d <<<= 8 (d << 8 | scratch >>> 24)
        EVPADDD.emit(masm, AVXSize.ZMM, aVec, aVec, bVec);
        EVPXORD.emit(masm, AVXSize.ZMM, dVec, dVec, aVec);
        masm.evprold(dVec, dVec, 8);

        // c += d; b ^= c; b <<<= 7 (b << 7 | scratch >>> 25)
        EVPADDD.emit(masm, AVXSize.ZMM, cVec, cVec, dVec);
        EVPXORD.emit(masm, AVXSize.ZMM, bVec, bVec, cVec);
        masm.evprold(bVec, bVec, 7);
    }

    // @formatter:off
    /**
     * Shift the b, c, and d vectors between columnar and diagonal representations.
     * Note that the "a" vector does not shift.
     *
     * @param bVec the SIMD register containing only the "b" values
     * @param cVec the SIMD register containing only the "c" values
     * @param dVec the SIMD register containing only the "d" values
     * @param vectorLen the size of the SIMD register to operate upon
     * @param colToDiag true if moving columnar to diagonal, false if
     *                  moving diagonal back to columnar.
     */
    // @formatter:on
    private static void shiftLaneOrg(AMD64MacroAssembler masm, Register bVec, Register cVec, Register dVec, AVXSize vectorLen, boolean colToDiag) {
        int bShift = colToDiag ? 0x39 : 0x93;
        int cShift = 0x4E;
        int dShift = colToDiag ? 0x93 : 0x39;

        if (vectorLen == AVXSize.ZMM) {
            EVPSHUFD.emit(masm, vectorLen, bVec, bVec, bShift);
            EVPSHUFD.emit(masm, vectorLen, cVec, cVec, cShift);
            EVPSHUFD.emit(masm, vectorLen, dVec, dVec, dShift);
        } else {
            masm.vpshufd(bVec, bVec, bShift, vectorLen);
            masm.vpshufd(cVec, cVec, cShift, vectorLen);
            masm.vpshufd(dVec, dVec, dShift, vectorLen);
        }
    }

    /**
     * Write 256 bytes of keystream output held in 4 AVX512 SIMD registers in a quarter round
     * parallel organization.
     *
     * @param aVec the SIMD register containing only the "a" values
     * @param bVec the SIMD register containing only the "b" values
     * @param cVec the SIMD register containing only the "c" values
     * @param dVec the SIMD register containing only the "d" values
     * @param baseAddr the register holding the base output address
     * @param baseOffset the offset from baseAddr for writes
     */
    private static void keystreamCollateAvx512(AMD64MacroAssembler masm,
                    Register aVec,
                    Register bVec,
                    Register cVec,
                    Register dVec,
                    Register baseAddr,
                    int baseOffset) {
        EVEXTRACTI32X4.emit(masm, AVXSize.ZMM, new AMD64Address(baseAddr, baseOffset + 0), aVec, 0);
        EVEXTRACTI32X4.emit(masm, AVXSize.ZMM, new AMD64Address(baseAddr, baseOffset + 64), aVec, 1);
        EVEXTRACTI32X4.emit(masm, AVXSize.ZMM, new AMD64Address(baseAddr, baseOffset + 128), aVec, 2);
        EVEXTRACTI32X4.emit(masm, AVXSize.ZMM, new AMD64Address(baseAddr, baseOffset + 192), aVec, 3);

        EVEXTRACTI32X4.emit(masm, AVXSize.ZMM, new AMD64Address(baseAddr, baseOffset + 16), bVec, 0);
        EVEXTRACTI32X4.emit(masm, AVXSize.ZMM, new AMD64Address(baseAddr, baseOffset + 80), bVec, 1);
        EVEXTRACTI32X4.emit(masm, AVXSize.ZMM, new AMD64Address(baseAddr, baseOffset + 144), bVec, 2);
        EVEXTRACTI32X4.emit(masm, AVXSize.ZMM, new AMD64Address(baseAddr, baseOffset + 208), bVec, 3);

        EVEXTRACTI32X4.emit(masm, AVXSize.ZMM, new AMD64Address(baseAddr, baseOffset + 32), cVec, 0);
        EVEXTRACTI32X4.emit(masm, AVXSize.ZMM, new AMD64Address(baseAddr, baseOffset + 96), cVec, 1);
        EVEXTRACTI32X4.emit(masm, AVXSize.ZMM, new AMD64Address(baseAddr, baseOffset + 160), cVec, 2);
        EVEXTRACTI32X4.emit(masm, AVXSize.ZMM, new AMD64Address(baseAddr, baseOffset + 224), cVec, 3);

        EVEXTRACTI32X4.emit(masm, AVXSize.ZMM, new AMD64Address(baseAddr, baseOffset + 48), dVec, 0);
        EVEXTRACTI32X4.emit(masm, AVXSize.ZMM, new AMD64Address(baseAddr, baseOffset + 112), dVec, 1);
        EVEXTRACTI32X4.emit(masm, AVXSize.ZMM, new AMD64Address(baseAddr, baseOffset + 176), dVec, 2);
        EVEXTRACTI32X4.emit(masm, AVXSize.ZMM, new AMD64Address(baseAddr, baseOffset + 240), dVec, 3);
    }
}
