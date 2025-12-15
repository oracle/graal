/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.EVEXTRACTF32X8;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.EVEXTRACTF64X4;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.EVEXTRACTI32X8;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.EVEXTRACTI64X4;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VEXTRACTF128;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VEXTRACTI128;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VPEXTRB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VPEXTRD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VPEXTRQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VPEXTRW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU64;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVINSERTF32X4;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVINSERTF32X8;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVINSERTF64X2;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVINSERTF64X4;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVINSERTI32X4;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVINSERTI32X8;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVINSERTI64X2;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVINSERTI64X4;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVSHUFI64X2;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VINSERTF128;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VINSERTI128;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VPINSRB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VPINSRD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VPINSRQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VPINSRW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VSHUFPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VSHUFPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPERMT2B;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPSHUFB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPXORD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VMOVHPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VMOVLHPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VMOVLPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VMOVSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPSHUFB;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.YMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.ZMM;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64SIMDInstructionEncoding;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveMaskOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMROp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.core.amd64.AMD64LIRGenerator;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

public class AMD64VectorShuffle {

    /**
     * General purpose permutation, this node looks up elements from a source vector using the index
     * vector as the selector.
     */
    public static final class PermuteOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<PermuteOp> TYPE = LIRInstructionClass.create(PermuteOp.class);

        @Def protected AllocatableValue result;
        @Use protected AllocatableValue source;
        @Use protected AllocatableValue indices;
        private final AMD64SIMDInstructionEncoding encoding;

        private PermuteOp(AllocatableValue result, AllocatableValue source, AllocatableValue indices, AMD64SIMDInstructionEncoding encoding) {
            super(TYPE);
            this.result = result;
            this.source = source;
            this.indices = indices;
            this.encoding = encoding;
        }

        public static AMD64LIRInstruction create(AMD64LIRGenerator gen, AllocatableValue result, AllocatableValue source, AllocatableValue indices, AMD64SIMDInstructionEncoding encoding) {
            AMD64Kind eKind = ((AMD64Kind) result.getPlatformKind()).getScalar();
            AVXSize avxSize = AVXKind.getRegisterSize(result);
            return switch (eKind) {
                case BYTE -> {
                    if (gen.supportsCPUFeature(CPUFeature.AVX512_VBMI) || avxSize == XMM) {
                        yield new PermuteOp(result, source, indices, encoding);
                    } else {
                        yield switch (avxSize) {
                            case YMM -> new PermuteOpWithTemps(gen, result, source, indices, encoding, 3, false);
                            case ZMM -> new PermuteOpWithTemps(gen, result, source, indices, encoding, 3, true);
                            default -> throw GraalError.shouldNotReachHereUnexpectedValue(avxSize);
                        };
                    }
                }
                case WORD -> {
                    if (encoding == AMD64SIMDInstructionEncoding.EVEX) {
                        GraalError.guarantee(gen.supportsCPUFeature(CPUFeature.AVX512BW) && gen.supportsCPUFeature(CPUFeature.AVX512VL), "must support basic avx512");
                        yield new PermuteOp(result, source, indices, encoding);
                    } else {
                        GraalError.guarantee(avxSize.getBytes() < ZMM.getBytes(), "zmm requires evex");
                        yield switch (avxSize) {
                            case XMM, YMM -> new PermuteOpWithTemps(gen, result, source, indices, encoding, 3, false);
                            default -> throw GraalError.shouldNotReachHereUnexpectedValue(avxSize);
                        };
                    }
                }
                case DWORD, SINGLE -> new PermuteOp(result, source, indices, encoding);
                case QWORD, DOUBLE -> {
                    if (encoding == AMD64SIMDInstructionEncoding.EVEX && avxSize != XMM) {
                        yield new PermuteOp(result, source, indices, encoding);
                    } else if (avxSize == YMM) {
                        yield new PermuteOpWithTemps(gen, result, source, indices, encoding, 2, false);
                    } else {
                        yield new PermuteOpWithTemps(gen, result, source, indices, encoding, 1, false);
                    }
                }
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(eKind);
            };
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind eKind = ((AMD64Kind) result.getPlatformKind()).getScalar();
            AVXSize avxSize = AVXKind.getRegisterSize(result);
            switch (eKind) {
                case BYTE -> {
                    if (avxSize == XMM) {
                        VexRVMOp.VPSHUFB.encoding(encoding).emit(masm, XMM, asRegister(result), asRegister(source), asRegister(indices));
                    } else {
                        VexRVMOp.EVPERMB.encoding(encoding).emit(masm, avxSize, asRegister(result), asRegister(indices), asRegister(source));
                    }
                }
                case WORD -> VexRVMOp.EVPERMW.encoding(encoding).emit(masm, avxSize, asRegister(result), asRegister(indices), asRegister(source));
                case DWORD, SINGLE -> {
                    if (avxSize.getBytes() <= XMM.getBytes()) {
                        VexRVMOp.VPERMILPS.encoding(encoding).emit(masm, XMM, asRegister(result), asRegister(source), asRegister(indices));
                    } else if (((AMD64Kind) result.getPlatformKind()).getScalar().isInteger()) {
                        VexRVMOp.VPERMD.encoding(encoding).emit(masm, avxSize, asRegister(result), asRegister(indices), asRegister(source));
                    } else {
                        VexRVMOp.VPERMPS.encoding(encoding).emit(masm, avxSize, asRegister(result), asRegister(indices), asRegister(source));
                    }
                }
                case QWORD, DOUBLE -> {
                    if (avxSize.getBytes() <= XMM.getBytes()) {
                        VexRVMOp.VPERMILPD.encoding(encoding).emit(masm, XMM, asRegister(result), asRegister(source), asRegister(indices));
                    } else if (((AMD64Kind) result.getPlatformKind()).getScalar().isInteger()) {
                        VexRVMOp.EVPERMQ.encoding(encoding).emit(masm, avxSize, asRegister(result), asRegister(indices), asRegister(source));
                    } else {
                        VexRVMOp.EVPERMPD.encoding(encoding).emit(masm, avxSize, asRegister(result), asRegister(indices), asRegister(source));
                    }
                }
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(eKind);
            }
        }
    }

    /**
     * Similar to {@code PermuteOp}, the difference is that this node may use additional temp
     * registers. As a result, it is split out so the inputs of {@code PermuteOp} does not need to
     * be {@link jdk.graal.compiler.lir.LIRInstruction.Alive}.
     */
    private static final class PermuteOpWithTemps extends AMD64LIRInstruction {
        public static final LIRInstructionClass<PermuteOpWithTemps> TYPE = LIRInstructionClass.create(PermuteOpWithTemps.class);

        @Def protected AllocatableValue result;
        @Alive protected AllocatableValue source;
        @Alive protected AllocatableValue indices;
        @Temp protected AllocatableValue[] xtmps;
        @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) protected AllocatableValue ktmp;
        private final AMD64SIMDInstructionEncoding encoding;

        private PermuteOpWithTemps(AMD64LIRGenerator gen, AllocatableValue result, AllocatableValue source, AllocatableValue indices, AMD64SIMDInstructionEncoding encoding, int xtmpRegs,
                        boolean ktmpReg) {
            super(TYPE);
            GraalError.guarantee(xtmpRegs <= 3, "too many temporaries, %d", xtmpRegs);
            this.result = result;
            this.source = source;
            this.indices = indices;
            this.xtmps = new AllocatableValue[xtmpRegs];
            for (int i = 0; i < xtmpRegs; i++) {
                this.xtmps[i] = gen.newVariable(indices.getValueKind());
            }
            this.ktmp = ktmpReg ? gen.newVariable(LIRKind.value(AMD64Kind.MASK64)) : Value.ILLEGAL;
            this.encoding = encoding;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind eKind = ((AMD64Kind) result.getPlatformKind()).getScalar();
            AVXSize avxSize = AVXKind.getRegisterSize(result);
            switch (eKind) {
                case BYTE -> {
                    GraalError.guarantee(!masm.supports(CPUFeature.AVX512_VBMI) && avxSize.getBytes() > XMM.getBytes(), "should be a PermuteOp");
                    emitBytePermute(crb, masm, asRegister(indices));
                }
                case WORD -> {
                    GraalError.guarantee(!masm.supports(CPUFeature.AVX512BW) && avxSize != ZMM, "should be PermuteOp");
                    Register indexReg = asRegister(indices);
                    Register xtmp1Reg = asRegister(xtmps[0]);
                    Register xtmp2Reg = asRegister(xtmps[1]);
                    Register xtmp3Reg = asRegister(xtmps[2]);

                    // Transform into a byte permute by transforming a 16-bit index with value v
                    // into a pair of 8-bit indices v * 2, v * 2 + 1
                    VexShiftOp.VPSLLW.encoding(encoding).emit(masm, avxSize, xtmp1Reg, indexReg, Byte.SIZE + 1);
                    AMD64Address inc = (AMD64Address) crb.recordDataReferenceInCode(JavaConstant.forInt(0x01000100), Integer.BYTES);
                    VexRMOp broadcastOp = masm.supports(CPUFeature.AVX2) ? VexRMOp.VPBROADCASTD : VexRMOp.VBROADCASTSS;
                    broadcastOp.encoding(encoding).emit(masm, avxSize, xtmp2Reg, inc);
                    VexRVMOp.VPOR.encoding(encoding).emit(masm, avxSize, xtmp1Reg, xtmp1Reg, xtmp2Reg);
                    VexShiftOp.VPSLLW.encoding(encoding).emit(masm, avxSize, xtmp2Reg, indexReg, 1);
                    VexRVMOp.VPOR.encoding(encoding).emit(masm, avxSize, xtmp3Reg, xtmp1Reg, xtmp2Reg);
                    emitBytePermute(crb, masm, xtmp3Reg);
                }
                case DWORD, SINGLE -> throw GraalError.shouldNotReachHere("should be PermuteOp");
                case QWORD, DOUBLE -> {
                    if (avxSize == YMM) {
                        GraalError.guarantee(encoding == AMD64SIMDInstructionEncoding.VEX, "should be PermuteOp");
                        Register indexReg = asRegister(indices);
                        Register xtmp1Reg = asRegister(xtmps[0]);
                        Register xtmp2Reg = asRegister(xtmps[1]);

                        // Transform into an int permute by transforming a 64-bit index with value v
                        // into a pair of 32-bit indices v + 2, v * 2 + 1
                        VexShiftOp.VPSLLQ.encoding(encoding).emit(masm, YMM, xtmp1Reg, indexReg, Integer.SIZE + 1);
                        AMD64Address inc = (AMD64Address) crb.asLongConstRef(JavaConstant.forLong(1L << Integer.SIZE));
                        VexRMOp.VPBROADCASTQ.encoding(encoding).emit(masm, YMM, xtmp2Reg, inc);
                        VexRVMOp.VPOR.encoding(encoding).emit(masm, YMM, xtmp2Reg, xtmp1Reg, xtmp2Reg);
                        VexShiftOp.VPSLLQ.encoding(encoding).emit(masm, YMM, xtmp1Reg, indexReg, 1);
                        VexRVMOp.VPOR.encoding(encoding).emit(masm, YMM, xtmp1Reg, xtmp1Reg, xtmp2Reg);
                        VexRVMOp op = eKind == AMD64Kind.QWORD ? VexRVMOp.VPERMD : VexRVMOp.VPERMPS;
                        op.encoding(encoding).emit(masm, YMM, asRegister(result), xtmp1Reg, asRegister(source));
                    } else {
                        GraalError.guarantee(avxSize == XMM, "should be PermuteOp");
                        Register xtmpReg = asRegister(xtmps[0]);
                        /*
                         * VPERMILPD uses the SECOND bit in each element as the index. Note that
                         * although the textual description of the instruction in the Intel SDM
                         * (March 2025) says that "The control bits are located at bit 0 of each
                         * quadword element", the pseudocode in the same manual as well as
                         * experiments show that it is actually the bit 1 that is the control bit.
                         */
                        VexShiftOp.VPSLLQ.encoding(encoding).emit(masm, XMM, xtmpReg, asRegister(indices), 1);
                        VexRVMOp.VPERMILPD.encoding(encoding).emit(masm, XMM, asRegister(result), asRegister(source), xtmpReg);
                    }
                }
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(eKind);
            }
        }

        private void emitBytePermute(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register indexReg) {
            AVXSize avxSize = AVXKind.getRegisterSize(result);
            switch (avxSize) {
                case XMM -> VexRVMOp.VPSHUFB.encoding(encoding).emit(masm, XMM, asRegister(result), asRegister(source), indexReg);
                case YMM -> {
                    Register sourceReg = asRegister(source);
                    Register xtmp1Reg = asRegister(xtmps[0]);
                    Register xtmp2Reg = asRegister(xtmps[1]);
                    Register xtmp3Reg = asRegister(xtmps[2]);
                    GraalError.guarantee(!indexReg.equals(xtmp1Reg) && !indexReg.equals(xtmp2Reg), "cannot alias");

                    // Find the elements that are collected from the first YMM half
                    VexRVMIOp.VPERM2I128.emit(masm, YMM, xtmp1Reg, sourceReg, sourceReg, 0x00);
                    VexRVMOp.VPSHUFB.encoding(encoding).emit(masm, YMM, xtmp1Reg, xtmp1Reg, indexReg);

                    // Find the elements that are collected from the second YMM half
                    VexRVMIOp.VPERM2I128.emit(masm, YMM, xtmp2Reg, sourceReg, sourceReg, 0x11);
                    VexRVMOp.VPSHUFB.encoding(encoding).emit(masm, YMM, xtmp2Reg, xtmp2Reg, indexReg);

                    // Blend the results, the 5-th bit of the index vector is the selector (0 - 15
                    // has the 5-th bit being 0 while 16 - 31 has the 5-bit being 1)
                    // Shift the 5-th bit to the position of the sign bit to use vpblendvb
                    VexShiftOp.VPSLLD.encoding(encoding).emit(masm, YMM, xtmp3Reg, indexReg, 3);
                    VexRVMROp.VPBLENDVB.emit(masm, YMM, asRegister(result), xtmp3Reg, xtmp1Reg, xtmp2Reg);
                }
                case ZMM -> {
                    Register sourceReg = asRegister(source);
                    Register xtmp1Reg = asRegister(xtmps[0]);
                    Register xtmp2Reg = asRegister(xtmps[1]);
                    Register xtmp3Reg = asRegister(xtmps[2]);
                    Register ktmpReg = asRegister(ktmp);
                    GraalError.guarantee(!indexReg.equals(xtmp1Reg) && !indexReg.equals(xtmp2Reg) && !indexReg.equals(xtmp3Reg), "cannot alias");

                    // Process the even-index elements
                    // Find the 2-byte location in the source vector and move to the correct 2-byte
                    // location in the result
                    VexShiftOp.EVPSRLD.emit(masm, ZMM, xtmp1Reg, indexReg, 1);
                    VexRVMOp.EVPERMW.emit(masm, ZMM, xtmp1Reg, xtmp1Reg, sourceReg);

                    // Elements with indices end with 0 are at the correct position, while the ones
                    // that have their indices end with 1 need to shift right by 8
                    VexShiftOp.EVPSLLD.emit(masm, ZMM, xtmp3Reg, indexReg, Short.SIZE - 1);
                    VexRMOp.EVPMOVW2M.emit(masm, ZMM, ktmpReg, xtmp3Reg);
                    VexShiftOp.EVPSRLD.emit(masm, ZMM, xtmp3Reg, xtmp1Reg, Byte.SIZE);
                    VexRVMOp.EVPBLENDMW.emit(masm, ZMM, xtmp1Reg, xtmp1Reg, xtmp3Reg, ktmpReg);

                    // Process the odd-index elements
                    // Find the 2-byte location in the source vector and move to the correct 2-byte
                    // location in the result
                    VexShiftOp.EVPSRLD.emit(masm, ZMM, xtmp2Reg, indexReg, Byte.SIZE + 1);
                    VexRVMOp.EVPERMW.emit(masm, ZMM, xtmp2Reg, xtmp2Reg, sourceReg);

                    // Elements with indices end with 1 are at the correct position, while the ones
                    // that have their indices end with 0 need to shift left by 8
                    VexShiftOp.EVPSLLD.emit(masm, ZMM, xtmp3Reg, indexReg, Byte.SIZE - 1);
                    VexRMOp.EVPMOVW2M.emit(masm, ZMM, ktmpReg, xtmp3Reg);
                    VexShiftOp.EVPSLLD.emit(masm, ZMM, xtmp3Reg, xtmp2Reg, Byte.SIZE);
                    VexRVMOp.EVPBLENDMW.emit(masm, ZMM, xtmp2Reg, xtmp3Reg, xtmp2Reg, ktmpReg);

                    // Blend the odd and even index
                    AMD64Address mask = (AMD64Address) crb.asLongConstRef(JavaConstant.forLong(0x5555555555555555L));
                    VexMoveMaskOp.KMOVQ.emit(masm, XMM, ktmpReg, mask);
                    VexRVMOp.EVPBLENDMB.emit(masm, ZMM, asRegister(result), xtmp2Reg, xtmp1Reg, ktmpReg);
                }
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(avxSize);
            }
        }
    }

    /**
     * A slice operation, see {@link jdk.graal.compiler.vector.nodes.amd64.AMD64SimdSliceNode}.
     */
    public static final class SliceOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<SliceOp> TYPE = LIRInstructionClass.create(SliceOp.class);

        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Alive({OperandFlag.REG}) protected AllocatableValue src1;
        @Alive({OperandFlag.REG}) protected AllocatableValue src2;
        @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) protected AllocatableValue tmp1;
        @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) protected AllocatableValue tmp2;
        private final int originInBytes;
        private final AMD64SIMDInstructionEncoding encoding;

        public SliceOp(AMD64LIRGenerator gen, AllocatableValue result, AllocatableValue src1, AllocatableValue src2, int origin, AMD64SIMDInstructionEncoding encoding) {
            super(TYPE);
            AMD64Kind eKind = ((AMD64Kind) result.getPlatformKind()).getScalar();
            this.result = result;
            this.src1 = src1;
            this.src2 = src2;
            this.originInBytes = origin * eKind.getSizeInBytes();
            this.encoding = encoding;
            allocateTempIfNecessary(gen);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            int resultSize = result.getPlatformKind().getSizeInBytes();
            switch (resultSize) {
                case 4 -> {
                    if (src1.equals(src2) && originInBytes == 2) {
                        VexRMIOp.VPSHUFLW.encoding(encoding).emit(masm, XMM, asRegister(result), asRegister(src1), 0x1);
                    } else {
                        VexRMIOp.VPSHUFD.encoding(encoding).emit(masm, XMM, asRegister(tmp1), asRegister(src1), 0);
                        VexRVMIOp.VPALIGNR.encoding(encoding).emit(masm, XMM, asRegister(result), asRegister(src2), asRegister(tmp1), originInBytes + 12);
                    }
                }
                case 8 -> {
                    if (src1.equals(src2) && originInBytes % 2 == 0) {
                        int imm;
                        if (originInBytes == 2) {
                            imm = 0b00111001;
                        } else if (originInBytes == 4) {
                            imm = 0b01001110;
                        } else {
                            GraalError.guarantee(originInBytes == 6, "unexpected originInBytes %d", originInBytes);
                            imm = 0b10010011;
                        }
                        VexRMIOp.VPSHUFLW.encoding(encoding).emit(masm, XMM, asRegister(result), asRegister(src1), imm);
                    } else {
                        VexRMIOp.VPSHUFD.encoding(encoding).emit(masm, XMM, asRegister(tmp1), asRegister(src1), 0x40);
                        VexRVMIOp.VPALIGNR.encoding(encoding).emit(masm, XMM, asRegister(result), asRegister(src2), asRegister(tmp1), originInBytes + 8);
                    }
                }
                case 16 -> VexRVMIOp.VPALIGNR.encoding(encoding).emit(masm, XMM, asRegister(result), asRegister(src2), asRegister(src1), originInBytes);
                case 32 -> {
                    if (encoding == AMD64SIMDInstructionEncoding.VEX || originInBytes % Integer.BYTES != 0) {
                        Register tmp = originInBytes == 16 ? asRegister(result) : asRegister(tmp1);
                        if (encoding == AMD64SIMDInstructionEncoding.VEX) {
                            VexRVMIOp.VPERM2I128.emit(masm, YMM, tmp, asRegister(src1), asRegister(src2), 0x21);
                        } else {
                            VexRVMIOp.EVALIGND.emit(masm, YMM, tmp, asRegister(src2), asRegister(src1), 4);
                        }
                        if (originInBytes < 16) {
                            VexRVMIOp.VPALIGNR.encoding(encoding).emit(masm, YMM, asRegister(result), asRegister(tmp1), asRegister(src1), originInBytes);
                        } else if (originInBytes > 16) {
                            VexRVMIOp.VPALIGNR.encoding(encoding).emit(masm, YMM, asRegister(result), asRegister(src2), asRegister(tmp1), originInBytes - 16);
                        }
                    } else {
                        VexRVMIOp.EVALIGND.emit(masm, YMM, asRegister(result), asRegister(src2), asRegister(src1), originInBytes / Integer.BYTES);
                    }
                }
                case 64 -> {
                    GraalError.guarantee(encoding == AMD64SIMDInstructionEncoding.EVEX, "unexpected encoding with 512-bit vector");
                    if (originInBytes % 4 != 0) {
                        if (originInBytes < 16) {
                            VexRVMIOp.EVALIGND.emit(masm, ZMM, asRegister(tmp1), asRegister(src2), asRegister(src1), 4);
                            VexRVMIOp.EVPALIGNR.emit(masm, ZMM, asRegister(result), asRegister(tmp1), asRegister(src1), originInBytes);
                        } else if (originInBytes < 32) {
                            VexRVMIOp.EVALIGND.emit(masm, ZMM, asRegister(tmp1), asRegister(src2), asRegister(src1), 4);
                            VexRVMIOp.EVALIGND.emit(masm, ZMM, asRegister(tmp2), asRegister(src2), asRegister(src1), 8);
                            VexRVMIOp.EVPALIGNR.emit(masm, ZMM, asRegister(result), asRegister(tmp2), asRegister(tmp1), originInBytes - 16);
                        } else if (originInBytes < 48) {
                            VexRVMIOp.EVALIGND.emit(masm, ZMM, asRegister(tmp1), asRegister(src2), asRegister(src1), 8);
                            VexRVMIOp.EVALIGND.emit(masm, ZMM, asRegister(tmp2), asRegister(src2), asRegister(src1), 12);
                            VexRVMIOp.EVPALIGNR.emit(masm, ZMM, asRegister(result), asRegister(tmp2), asRegister(tmp1), originInBytes - 32);
                        } else {
                            VexRVMIOp.EVALIGND.emit(masm, ZMM, asRegister(tmp1), asRegister(src2), asRegister(src1), 12);
                            VexRVMIOp.EVPALIGNR.emit(masm, ZMM, asRegister(result), asRegister(src2), asRegister(tmp1), originInBytes - 48);
                        }
                    } else {
                        VexRVMIOp.EVALIGND.emit(masm, ZMM, asRegister(result), asRegister(src2), asRegister(src1), originInBytes / Integer.BYTES);
                    }
                }
                default -> GraalError.shouldNotReachHereUnexpectedValue(resultSize);
            }
        }

        private void allocateTempIfNecessary(AMD64LIRGenerator gen) {
            PlatformKind resultKind = result.getPlatformKind();
            boolean needsTemp;
            if (resultKind.getSizeInBytes() < XMM.getBytes()) {
                needsTemp = !src1.equals(src2) || originInBytes % 2 != 0;
            } else if (resultKind.getSizeInBytes() == XMM.getBytes()) {
                needsTemp = false;
            } else if (encoding == AMD64SIMDInstructionEncoding.VEX) {
                needsTemp = true;
            } else {
                needsTemp = (originInBytes % Integer.BYTES != 0);
            }
            if (needsTemp) {
                tmp1 = gen.newVariable(LIRKind.value(resultKind));
            } else {
                tmp1 = Value.ILLEGAL;
            }

            if (resultKind.getSizeInBytes() == ZMM.getBytes() && originInBytes % Integer.BYTES != 0 &&
                            originInBytes > 16 && originInBytes < 48) {
                GraalError.guarantee(!tmp1.equals(Value.ILLEGAL), "must have tmp1 with originInBytes = %d", originInBytes);
                tmp2 = gen.newVariable(LIRKind.value(resultKind));
            } else {
                tmp2 = Value.ILLEGAL;
            }
        }
    }

    public static final class IntToVectorOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<IntToVectorOp> TYPE = LIRInstructionClass.create(IntToVectorOp.class);

        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Use({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue value;
        private final AMD64SIMDInstructionEncoding encoding;

        public IntToVectorOp(AllocatableValue result, AllocatableValue value, AMD64SIMDInstructionEncoding encoding) {
            super(TYPE);
            assert ((AMD64Kind) result.getPlatformKind()).getScalar().isInteger() : result.getPlatformKind();
            this.result = result;
            this.value = value;
            this.encoding = encoding;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(value)) {
                VMOVD.encoding(encoding).emit(masm, XMM, asRegister(result), asRegister(value));
            } else {
                assert isStackSlot(value);
                VMOVD.encoding(encoding).emit(masm, XMM, asRegister(result), (AMD64Address) crb.asAddress(value));
            }
        }
    }

    public static final class LongToVectorOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<LongToVectorOp> TYPE = LIRInstructionClass.create(LongToVectorOp.class);
        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Use({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue value;
        private final AMD64SIMDInstructionEncoding encoding;

        public LongToVectorOp(AllocatableValue result, AllocatableValue value, AMD64SIMDInstructionEncoding encoding) {
            super(TYPE);
            assert result.getPlatformKind() == AMD64Kind.V128_QWORD || result.getPlatformKind() == AMD64Kind.V256_QWORD || result.getPlatformKind() == AMD64Kind.V512_QWORD : result;
            this.result = result;
            this.value = value;
            this.encoding = encoding;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(value)) {
                VMOVQ.encoding(encoding).emit(masm, XMM, asRegister(result), asRegister(value));
            } else {
                assert isStackSlot(value);
                VMOVQ.encoding(encoding).emit(masm, XMM, asRegister(result), (AMD64Address) crb.asAddress(value));
            }
        }
    }

    public static final class ShuffleBytesOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<ShuffleBytesOp> TYPE = LIRInstructionClass.create(ShuffleBytesOp.class);
        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Use({OperandFlag.REG}) protected AllocatableValue source;
        @Use({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue selector;
        private final AMD64SIMDInstructionEncoding encoding;

        public ShuffleBytesOp(AllocatableValue result, AllocatableValue source, AllocatableValue selector, AMD64SIMDInstructionEncoding encoding) {
            super(TYPE);
            this.result = result;
            this.source = source;
            this.selector = selector;
            this.encoding = encoding;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind kind = (AMD64Kind) result.getPlatformKind();
            if (isRegister(selector)) {
                VPSHUFB.encoding(encoding).emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(source), asRegister(selector));
            } else {
                assert isStackSlot(selector);
                VPSHUFB.encoding(encoding).emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(source), (AMD64Address) crb.asAddress(selector));
            }
        }
    }

    public static final class ConstPermuteBytesUsingTableOp extends AMD64LIRInstruction implements AVX512Support {
        public static final LIRInstructionClass<ConstPermuteBytesUsingTableOp> TYPE = LIRInstructionClass.create(ConstPermuteBytesUsingTableOp.class);
        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Alive({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue source;
        @Use({OperandFlag.REG}) protected AllocatableValue mask;
        @Temp({OperandFlag.REG}) protected AllocatableValue selector;

        byte[] selectorData;

        public ConstPermuteBytesUsingTableOp(LIRGeneratorTool tool, AllocatableValue result, AllocatableValue source, byte[] selectorData, AllocatableValue mask) {
            super(TYPE);
            this.result = result;
            this.source = source;
            this.selectorData = selectorData;
            this.selector = tool.newVariable(LIRKind.value(source.getPlatformKind()));
            this.mask = mask;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind kind = (AMD64Kind) source.getPlatformKind();
            int alignment = crb.dataBuilder.ensureValidDataAlignment(selectorData.length);
            AMD64Address address = (AMD64Address) crb.recordDataReferenceInCode(selectorData, alignment);
            EVMOVDQU64.emit(masm, AVXKind.getRegisterSize(kind), asRegister(selector), address);
            EVPXORD.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(result), asRegister(result));
            if (isRegister(source)) {
                EVPERMT2B.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(selector), asRegister(source), mask != null ? asRegister(mask) : Register.None,
                                mask != null ? AMD64BaseAssembler.EVEXPrefixConfig.Z1 : AMD64BaseAssembler.EVEXPrefixConfig.Z0,
                                AMD64BaseAssembler.EVEXPrefixConfig.B0);
            } else {
                EVPERMT2B.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(selector), (AMD64Address) crb.asAddress(source), mask != null ? asRegister(mask) : Register.None,
                                mask != null ? AMD64BaseAssembler.EVEXPrefixConfig.Z1 : AMD64BaseAssembler.EVEXPrefixConfig.Z0,
                                AMD64BaseAssembler.EVEXPrefixConfig.B0);
            }
        }

        @Override
        public AllocatableValue getOpmask() {
            return mask;
        }
    }

    public static class ConstShuffleBytesOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<ConstShuffleBytesOp> TYPE = LIRInstructionClass.create(ConstShuffleBytesOp.class);
        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Use({OperandFlag.REG}) protected AllocatableValue source;
        protected final byte[] selector;
        private final AMD64SIMDInstructionEncoding encoding;

        public ConstShuffleBytesOp(AllocatableValue result, AllocatableValue source, AMD64SIMDInstructionEncoding encoding, byte... selector) {
            this(TYPE, result, source, encoding, selector);
        }

        public ConstShuffleBytesOp(LIRInstructionClass<? extends AMD64LIRInstruction> c, AllocatableValue result, AllocatableValue source, AMD64SIMDInstructionEncoding encoding, byte... selector) {
            super(c);
            assert AVXKind.getRegisterSize(((AMD64Kind) source.getPlatformKind())).getBytes() == selector.length : " Register size=" +
                            AVXKind.getRegisterSize(((AMD64Kind) source.getPlatformKind())).getBytes() + " select length=" + selector.length;
            this.result = result;
            this.source = source;
            this.encoding = encoding;
            this.selector = selector;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind kind = (AMD64Kind) source.getPlatformKind();
            int alignment = crb.dataBuilder.ensureValidDataAlignment(selector.length);
            AMD64Address address = (AMD64Address) crb.recordDataReferenceInCode(selector, alignment);
            VPSHUFB.encoding(encoding).emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(source), address);
        }
    }

    public static final class ConstShuffleBytesOpWithMask extends ConstShuffleBytesOp implements AVX512Support {
        public static final LIRInstructionClass<ConstShuffleBytesOpWithMask> TYPE = LIRInstructionClass.create(ConstShuffleBytesOpWithMask.class);
        @Use({OperandFlag.REG}) protected AllocatableValue mask;

        public ConstShuffleBytesOpWithMask(AllocatableValue result, AllocatableValue source, AllocatableValue mask, byte... selector) {
            super(TYPE, result, source, AMD64SIMDInstructionEncoding.EVEX, selector);
            this.mask = mask;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind kind = (AMD64Kind) source.getPlatformKind();
            int alignment = crb.dataBuilder.ensureValidDataAlignment(selector.length);
            AMD64Address address = (AMD64Address) crb.recordDataReferenceInCode(selector, alignment);
            EVPSHUFB.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(source), address, asRegister(mask));
        }

        @Override
        public AllocatableValue getOpmask() {
            return mask;
        }
    }

    public static class ShuffleWordOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<ShuffleWordOp> TYPE = LIRInstructionClass.create(ShuffleWordOp.class);
        protected final VexRMIOp op;
        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Use({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue source;
        protected final int selector;

        public ShuffleWordOp(VexRMIOp op, AllocatableValue result, AllocatableValue source, int selector) {
            this(TYPE, op, result, source, selector);
        }

        protected ShuffleWordOp(LIRInstructionClass<? extends AMD64LIRInstruction> c, VexRMIOp op, AllocatableValue result, AllocatableValue source, int selector) {
            super(c);
            this.op = op;
            this.result = result;
            this.source = source;
            this.selector = selector;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind kind = (AMD64Kind) source.getPlatformKind();
            if (isRegister(source)) {
                op.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(source), selector);
            } else {
                op.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), (AMD64Address) crb.asAddress(source), selector);
            }
        }
    }

    public static class ShuffleWordOpWithMask extends ShuffleWordOp implements AVX512Support {
        public static final LIRInstructionClass<ShuffleWordOpWithMask> TYPE = LIRInstructionClass.create(ShuffleWordOpWithMask.class);

        @Use({OperandFlag.REG}) protected AllocatableValue mask;

        public ShuffleWordOpWithMask(VexRMIOp op, AllocatableValue result, AllocatableValue source, int selector, AllocatableValue mask) {
            super(TYPE, op, result, source, selector);
            this.mask = mask;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind kind = (AMD64Kind) source.getPlatformKind();
            if (isRegister(source)) {
                op.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(source), selector, asRegister(mask), AMD64BaseAssembler.EVEXPrefixConfig.Z1,
                                AMD64BaseAssembler.EVEXPrefixConfig.B0);
            } else {
                op.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), (AMD64Address) crb.asAddress(source), selector, asRegister(mask), AMD64BaseAssembler.EVEXPrefixConfig.Z1,
                                AMD64BaseAssembler.EVEXPrefixConfig.B0);
            }
        }

        @Override
        public AllocatableValue getOpmask() {
            return mask;
        }
    }

    public static class ShuffleFloatOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<ShuffleFloatOp> TYPE = LIRInstructionClass.create(ShuffleFloatOp.class);
        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Use({OperandFlag.REG}) protected AllocatableValue source1;
        @Use({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue source2;
        private final int selector;
        private final AMD64SIMDInstructionEncoding encoding;

        public ShuffleFloatOp(AllocatableValue result, AllocatableValue source1, AllocatableValue source2, int selector, AMD64SIMDInstructionEncoding encoding) {
            super(TYPE);
            this.result = result;
            this.source1 = source1;
            this.source2 = source2;
            this.selector = selector;
            this.encoding = encoding;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind kind = (AMD64Kind) result.getPlatformKind();

            VexRVMIOp op = switch (kind.getScalar()) {
                case SINGLE -> VSHUFPS.encoding(encoding);
                case DOUBLE -> VSHUFPD.encoding(encoding);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };

            if (isRegister(source2)) {
                op.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(source1), asRegister(source2), selector);
            } else {
                assert isStackSlot(source2);
                op.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(source1), (AMD64Address) crb.asAddress(source2), selector);
            }
        }
    }

    public static final class Extract128Op extends AMD64LIRInstruction {
        public static final LIRInstructionClass<Extract128Op> TYPE = LIRInstructionClass.create(Extract128Op.class);
        @Def({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue result;
        @Use({OperandFlag.REG}) protected AllocatableValue source;
        private final int selector;
        private final AMD64SIMDInstructionEncoding encoding;

        public Extract128Op(AllocatableValue result, AllocatableValue source, int selector, AMD64SIMDInstructionEncoding encoding) {
            super(TYPE);
            this.result = result;
            this.source = source;
            this.selector = selector;
            this.encoding = encoding;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind kind = (AMD64Kind) source.getPlatformKind();
            AVXSize size = AVXKind.getRegisterSize(kind);

            VexMRIOp op = switch (kind.getScalar()) {
                case SINGLE, DOUBLE -> VEXTRACTF128;
                // if supported we want VEXTRACTI128
                // on AVX1, we have to use VEXTRACTF128
                default -> masm.supports(CPUFeature.AVX2) ? VEXTRACTI128 : VEXTRACTF128;
            };

            if (isRegister(result)) {
                op.encoding(encoding).emit(masm, size, asRegister(result), asRegister(source), selector);
            } else {
                assert isStackSlot(result);
                op.encoding(encoding).emit(masm, size, (AMD64Address) crb.asAddress(result), asRegister(source), selector);
            }
        }
    }

    public static class Extract256Op extends AMD64LIRInstruction {
        public static final LIRInstructionClass<Extract256Op> TYPE = LIRInstructionClass.create(Extract256Op.class);
        @Def({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue result;
        @Use({OperandFlag.REG}) protected AllocatableValue source;
        private final int selector;

        public Extract256Op(AllocatableValue result, AllocatableValue source, int selector) {
            super(TYPE);
            this.result = result;
            this.source = source;
            this.selector = selector;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind kind = (AMD64Kind) source.getPlatformKind();

            assert AVXKind.getRegisterSize(kind) == ZMM : "Can only extract 256 bits from ZMM register";

            VexMRIOp op;
            switch (kind.getScalar()) {
                case DOUBLE:
                    op = EVEXTRACTF64X4;
                    break;
                case DWORD:
                    // the 32x8 versions require additional features (DQ),
                    // thus we fall back to the 64x4 versions unless provided
                    op = masm.supports(CPUFeature.AVX512DQ) ? EVEXTRACTI32X8 : EVEXTRACTI64X4;
                    break;
                case QWORD:
                    op = EVEXTRACTI64X4;
                    break;
                default:
                    op = masm.supports(CPUFeature.AVX512DQ) ? EVEXTRACTF32X8 : EVEXTRACTF64X4;
                    break;
            }

            if (isRegister(result)) {
                op.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(source), selector);
            } else {
                assert isStackSlot(result);
                op.emit(masm, AVXKind.getRegisterSize(kind), (AMD64Address) crb.asAddress(result), asRegister(source), selector);
            }
        }
    }

    public static final class Insert128Op extends AMD64LIRInstruction {
        public static final LIRInstructionClass<Insert128Op> TYPE = LIRInstructionClass.create(Insert128Op.class);
        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Use({OperandFlag.REG}) protected AllocatableValue source1;
        @Use({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue source2;
        private final int selector;
        private final AMD64SIMDInstructionEncoding encoding;

        public Insert128Op(AllocatableValue result, AllocatableValue source1, AllocatableValue source2, int selector, AMD64SIMDInstructionEncoding encoding) {
            super(TYPE);
            this.result = result;
            this.source1 = source1;
            this.source2 = source2;
            this.selector = selector;
            this.encoding = encoding;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind kind = (AMD64Kind) result.getPlatformKind();

            AVXSize size = AVXKind.getRegisterSize(kind);

            VexRVMIOp op;
            if (encoding == AMD64SIMDInstructionEncoding.EVEX) {
                GraalError.guarantee(size.getBytes() >= AVXSize.YMM.getBytes(), "Unexpected vector size %s for extract-128-bits op", size);
                op = switch (kind.getScalar()) {
                    case DOUBLE -> masm.supports(CPUFeature.AVX512DQ) ? EVINSERTF64X2 : EVINSERTF32X4;
                    case DWORD -> EVINSERTI32X4;
                    case QWORD -> masm.supports(CPUFeature.AVX512DQ) ? EVINSERTI64X2 : EVINSERTI32X4;
                    default -> EVINSERTF32X4;
                };
            } else {
                GraalError.guarantee(size.getBytes() == AVXSize.YMM.getBytes(), "Unexpected vector size %s for extract-128-bits op", size);
                op = switch (kind.getScalar()) {
                    case SINGLE, DOUBLE -> VINSERTF128;
                    /*
                     * if supported we want VINSERTI128 - on AVX1, we have to use VINSERTF128. Using
                     * instructions with an incorrect data type is possible but typically results in
                     * an additional overhead whenever the value is being accessed.
                     */
                    default -> masm.supports(CPUFeature.AVX2) ? VINSERTI128 : VINSERTF128;
                };
            }

            if (isRegister(source2)) {
                op.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(source1), asRegister(source2), selector);
            } else {
                assert isStackSlot(source2);
                op.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(source1), (AMD64Address) crb.asAddress(source2), selector);
            }
        }
    }

    public static final class Insert256Op extends AMD64LIRInstruction {
        public static final LIRInstructionClass<Insert256Op> TYPE = LIRInstructionClass.create(Insert256Op.class);
        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Use({OperandFlag.REG}) protected AllocatableValue source1;
        @Use({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue source2;
        private final int selector;

        public Insert256Op(AllocatableValue result, AllocatableValue source1, AllocatableValue source2, int selector) {
            super(TYPE);
            this.result = result;
            this.source1 = source1;
            this.source2 = source2;
            this.selector = selector;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind kind = (AMD64Kind) result.getPlatformKind();

            assert AVXKind.getRegisterSize(kind) == ZMM : "Can only extract 256 bits from ZMM register";

            VexRVMIOp op;
            switch (kind.getScalar()) {
                case DOUBLE:
                    op = EVINSERTF64X4;
                    break;
                case DWORD:
                    op = masm.supports(CPUFeature.AVX512DQ) ? EVINSERTI32X8 : EVINSERTI64X4;
                    break;
                case QWORD:
                    op = EVINSERTI64X4;
                    break;
                default:
                    op = masm.supports(CPUFeature.AVX512DQ) ? EVINSERTF32X8 : EVINSERTF64X4;
                    break;
            }

            if (isRegister(source2)) {
                op.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(source1), asRegister(source2), selector);
            } else {
                assert isStackSlot(source2);
                op.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(source1), (AMD64Address) crb.asAddress(source2), selector);
            }
        }
    }

    public static final class ExtractByteOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<ExtractByteOp> TYPE = LIRInstructionClass.create(ExtractByteOp.class);
        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Use({OperandFlag.REG}) protected AllocatableValue vector;
        private final int selector;
        private final AMD64SIMDInstructionEncoding encoding;

        public ExtractByteOp(AllocatableValue result, AllocatableValue vector, int selector, AMD64SIMDInstructionEncoding encoding) {
            super(TYPE);
            assert result.getPlatformKind() == AMD64Kind.DWORD : result;
            assert ((AMD64Kind) vector.getPlatformKind()).getScalar() == AMD64Kind.BYTE : Assertions.errorMessage(vector);
            this.result = result;
            this.vector = vector;
            this.selector = selector;
            this.encoding = encoding;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            VPEXTRB.encoding(encoding).emit(masm, XMM, asRegister(result), asRegister(vector), selector);
        }
    }

    public static final class ExtractShortOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<ExtractShortOp> TYPE = LIRInstructionClass.create(ExtractShortOp.class);
        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Use({OperandFlag.REG}) protected AllocatableValue vector;
        private final int selector;
        private final AMD64SIMDInstructionEncoding encoding;

        public ExtractShortOp(AllocatableValue result, AllocatableValue vector, int selector, AMD64SIMDInstructionEncoding encoding) {
            super(TYPE);
            assert result.getPlatformKind() == AMD64Kind.DWORD : result;
            assert ((AMD64Kind) vector.getPlatformKind()).getScalar() == AMD64Kind.WORD : vector;
            this.result = result;
            this.vector = vector;
            this.selector = selector;
            this.encoding = encoding;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            VPEXTRW.encoding(encoding).emit(masm, XMM, asRegister(result), asRegister(vector), selector);
        }
    }

    public static final class ExtractIntOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<ExtractIntOp> TYPE = LIRInstructionClass.create(ExtractIntOp.class);
        @Def({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue result;
        @Use({OperandFlag.REG}) protected AllocatableValue vector;
        private final int selector;
        private final AMD64SIMDInstructionEncoding encoding;

        public ExtractIntOp(AllocatableValue result, AllocatableValue vector, int selector, AMD64SIMDInstructionEncoding encoding) {
            super(TYPE);
            assert result.getPlatformKind() == AMD64Kind.DWORD : result;
            assert ((AMD64Kind) vector.getPlatformKind()).getScalar() == AMD64Kind.DWORD : vector;
            this.result = result;
            this.vector = vector;
            this.selector = selector;
            this.encoding = encoding;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(result)) {
                if (selector == 0) {
                    VMOVD.encoding(encoding).emitReverse(masm, XMM, asRegister(result), asRegister(vector));
                } else {
                    VPEXTRD.encoding(encoding).emit(masm, XMM, asRegister(result), asRegister(vector), selector);
                }
            } else {
                assert isStackSlot(result);
                if (selector == 0) {
                    VMOVD.encoding(encoding).emit(masm, XMM, (AMD64Address) crb.asAddress(result), asRegister(vector));
                } else {
                    VPEXTRD.encoding(encoding).emit(masm, XMM, (AMD64Address) crb.asAddress(result), asRegister(vector), selector);
                }
            }
        }
    }

    public static final class ExtractLongOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<ExtractLongOp> TYPE = LIRInstructionClass.create(ExtractLongOp.class);
        @Def({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue result;
        @Use({OperandFlag.REG}) protected AllocatableValue vector;
        private final int selector;
        private final AMD64SIMDInstructionEncoding encoding;

        public ExtractLongOp(AllocatableValue result, AllocatableValue vector, int selector, AMD64SIMDInstructionEncoding encoding) {
            super(TYPE);
            assert result.getPlatformKind() == AMD64Kind.QWORD : result;
            assert ((AMD64Kind) vector.getPlatformKind()).getScalar() == AMD64Kind.QWORD : vector;
            this.result = result;
            this.vector = vector;
            this.selector = selector;
            this.encoding = encoding;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(result)) {
                if (selector == 0) {
                    VMOVQ.encoding(encoding).emitReverse(masm, XMM, asRegister(result), asRegister(vector));
                } else {
                    VPEXTRQ.encoding(encoding).emit(masm, XMM, asRegister(result), asRegister(vector), selector);
                }
            } else {
                assert isStackSlot(result);
                if (selector == 0) {
                    VMOVQ.encoding(encoding).emit(masm, XMM, (AMD64Address) crb.asAddress(result), asRegister(vector));
                } else {
                    VPEXTRQ.encoding(encoding).emit(masm, XMM, (AMD64Address) crb.asAddress(result), asRegister(vector), selector);
                }
            }
        }
    }

    /**
     * This node inserts a scalar or a smaller vector into a larger one at the specified offset and
     * returns the new inserted vector. The semantics is similar to vpinsrd but with various types.
     */
    public static final class InsertOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<InsertOp> TYPE = LIRInstructionClass.create(InsertOp.class);

        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Use({OperandFlag.REG}) protected AllocatableValue vec;
        @Use({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue val;
        private final int offset;
        AMD64SIMDInstructionEncoding encoding;

        public InsertOp(AllocatableValue result, AllocatableValue vec, AllocatableValue val, int offset, AMD64SIMDInstructionEncoding encoding) {
            super(TYPE);
            this.result = result;
            this.vec = vec;
            this.val = val;
            this.offset = offset;
            this.encoding = encoding;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind vecKind = (AMD64Kind) vec.getPlatformKind();
            // This may be wrong for byte/word, see AMD64ArithmeticLIRGenerator::emitNarrow
            AMD64Kind valKind = (AMD64Kind) val.getPlatformKind();
            GraalError.guarantee(vecKind.getScalar() == valKind.getScalar() || (vecKind.getScalar().getSizeInBytes() < Integer.BYTES && valKind == AMD64Kind.DWORD), "element types must match %s, %s",
                            vecKind, valKind);
            AMD64Kind elementKind = vecKind.getScalar();
            AVXSize size = AVXKind.getRegisterSize(vec);
            int bitOffset = offset * elementKind.getSizeInBytes() * Byte.SIZE;
            int valBits = elementKind.getSizeInBytes() * valKind.getVectorLength() * Byte.SIZE;
            GraalError.guarantee(vecKind.getSizeInBytes() <= 16 || valKind.getSizeInBytes() >= 16, "must be insertable");

            if (valKind.isInteger()) {
                VexRVMIOp op = switch (valBits) {
                    case 8 -> VPINSRB;
                    case 16 -> VPINSRW;
                    case 32 -> VPINSRD;
                    case 64 -> VPINSRQ;
                    default -> throw GraalError.shouldNotReachHereUnexpectedValue(valKind);
                };
                emitHelper(crb, op.encoding(encoding), masm, XMM, asRegister(result), asRegister(vec), val, offset);
                return;
            }

            if (valBits == 128) {
                VexRVMIOp op = vecKind.getScalar().isInteger() && masm.supports(CPUFeature.AVX2) ? VINSERTI128 : VINSERTF128;
                emitHelper(crb, op.encoding(encoding), masm, size, asRegister(result), asRegister(vec), val, bitOffset / 128);
                return;
            } else if (valBits == 256) {
                VexRVMIOp op = vecKind.getScalar().isInteger() ? EVINSERTI64X4 : EVINSERTF64X4;
                emitHelper(crb, op, masm, size, asRegister(result), asRegister(vec), val, bitOffset / 256);
                return;
            }

            if (isRegister(val)) {
                switch (valBits) {
                    case 32 -> {
                        if (bitOffset == 0) {
                            AMD64Assembler.VexRVMOp.VMOVSS.encoding(encoding).emit(masm, XMM, asRegister(result), asRegister(vec), asRegister(val));
                        } else {
                            VexRVMIOp.VINSERTPS.encoding(encoding).emit(masm, XMM, asRegister(result), asRegister(vec), asRegister(val), bitOffset >>> 1);
                        }
                    }
                    case 64 -> {
                        AMD64Assembler.VexRVMOp op = bitOffset == 0 ? VMOVSD : VMOVLHPS;
                        op.encoding(encoding).emit(masm, XMM, asRegister(result), asRegister(vec), asRegister(val));
                    }
                    default -> throw GraalError.shouldNotReachHereUnexpectedValue(valKind);
                }
            } else {
                AMD64Address addr = (AMD64Address) crb.asAddress(val);
                switch (valBits) {
                    case 32 -> {
                        VexRVMIOp.VINSERTPS.encoding(encoding).emit(masm, XMM, asRegister(result), asRegister(vec), addr, bitOffset >>> 1);
                    }
                    case 64 -> {
                        AMD64Assembler.VexRVMOp op = bitOffset == 0 ? VMOVLPD : VMOVHPD;
                        op.encoding(encoding).emit(masm, XMM, asRegister(result), asRegister(vec), addr);
                    }
                }
            }
        }

        private static void emitHelper(CompilationResultBuilder crb, VexRVMIOp op, AMD64MacroAssembler masm, AVXSize size, Register dst, Register nds, AllocatableValue src, int imm8) {
            if (isRegister(src)) {
                op.emit(masm, size, dst, nds, asRegister(src), imm8);
            } else {
                op.emit(masm, size, dst, nds, (AMD64Address) crb.asAddress(src), imm8);
            }
        }
    }

    public static final class ShuffleIntegerLanesOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<ShuffleIntegerLanesOp> TYPE = LIRInstructionClass.create(ShuffleIntegerLanesOp.class);
        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Use({OperandFlag.REG}) protected AllocatableValue vector;
        private final int selector;

        public ShuffleIntegerLanesOp(AllocatableValue result, AllocatableValue vector, int selector) {
            super(TYPE);
            this.result = result;
            this.vector = vector;
            this.selector = selector;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AVXSize avxSize = AVXKind.getRegisterSize(vector);
            assert avxSize.getBytes() >= AVXSize.YMM.getBytes() : "expected size is YMM or ZMM, got " + avxSize;
            /*
             * This LIR instruction is currently only used to shuffle the lanes of YMM or ZMM sized
             * BYTE vectors without a mask. When using this instruction with masks, the correct
             * element size of VSHUFI must be implemented and used.
             */
            EVSHUFI64X2.emit(masm, AVXKind.getRegisterSize(vector), asRegister(result), asRegister(vector), asRegister(vector), selector);
        }
    }
}
