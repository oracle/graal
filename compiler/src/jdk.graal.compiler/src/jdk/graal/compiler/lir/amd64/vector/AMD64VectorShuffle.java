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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.EVEXTRACTF32X4;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.EVEXTRACTF32X8;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.EVEXTRACTF64X2;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.EVEXTRACTF64X4;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.EVEXTRACTI32X4;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.EVEXTRACTI32X8;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.EVEXTRACTI64X2;
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
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VSHUFPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VSHUFPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPERMT2B;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPSHUFB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPXOR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPSHUFB;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.ZMM;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64SIMDInstructionEncoding;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
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

public class AMD64VectorShuffle {

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
            EVPXOR.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(result), asRegister(result));
            if (isRegister(source)) {
                EVPERMT2B.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(selector), asRegister(source), mask != null ? asRegister(mask) : Register.None,
                                AMD64BaseAssembler.EVEXPrefixConfig.Z1,
                                AMD64BaseAssembler.EVEXPrefixConfig.B0);
            } else {
                EVPERMT2B.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(selector), (AMD64Address) crb.asAddress(source), mask != null ? asRegister(mask) : Register.None,
                                AMD64BaseAssembler.EVEXPrefixConfig.Z1,
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

            VexMRIOp op;
            if (encoding == AMD64SIMDInstructionEncoding.EVEX) {
                GraalError.guarantee(size.getBytes() >= AVXSize.YMM.getBytes(), "Unexpected vector size %s for extract-128-bits op", size);
                op = switch (kind.getScalar()) {
                    case DOUBLE -> masm.supports(CPUFeature.AVX512DQ) ? EVEXTRACTF64X2 : EVEXTRACTF32X4;
                    case DWORD -> EVEXTRACTI32X4;
                    case QWORD -> masm.supports(CPUFeature.AVX512DQ) ? EVEXTRACTI64X2 : EVEXTRACTI32X4;
                    default -> EVEXTRACTF32X4;
                };
            } else {
                GraalError.guarantee(size == AVXSize.YMM, "Unexpected vector size %s for extract-128-bits op", size);
                op = switch (kind.getScalar()) {
                    case SINGLE, DOUBLE -> VEXTRACTF128;
                    // if supported we want VEXTRACTI128
                    // on AVX1, we have to use VEXTRACTF128
                    default -> masm.supports(CPUFeature.AVX2) ? VEXTRACTI128 : VEXTRACTF128;
                };
            }

            if (isRegister(result)) {
                op.emit(masm, size, asRegister(result), asRegister(source), selector);
            } else {
                assert isStackSlot(result);
                op.emit(masm, size, (AMD64Address) crb.asAddress(result), asRegister(source), selector);
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
