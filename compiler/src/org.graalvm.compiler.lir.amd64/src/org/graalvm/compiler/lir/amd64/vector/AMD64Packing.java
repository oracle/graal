/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.amd64.vector;

import java.nio.ByteBuffer;
import java.util.List;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.amd64.AMD64AddressValue;
import org.graalvm.compiler.lir.amd64.AMD64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

import static java.lang.Double.doubleToRawLongBits;
import static java.lang.Float.floatToRawIntBits;

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.COMPOSITE;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static org.graalvm.compiler.lir.LIRValueUtil.asJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;

import static jdk.vm.ci.amd64.AMD64.rsp;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

public final class AMD64Packing {

    private AMD64Packing() { }

    private static AMD64Kind twice(AMD64Kind kind) {
        switch (kind) {
            case BYTE:
                return AMD64Kind.WORD;
            case WORD:
                return AMD64Kind.DWORD;
            case DWORD:
                return AMD64Kind.QWORD;
            case SINGLE:
                return AMD64Kind.QWORD;
            default:
                return kind;
        }
    }

    public static final class PackConstantsOp extends AMD64LIRInstruction {

        public static final LIRInstructionClass<PackConstantsOp> TYPE = LIRInstructionClass.create(PackConstantsOp.class);

        private final ByteBuffer byteBuffer;

        @Def({REG}) private AllocatableValue result;

        public PackConstantsOp(AllocatableValue result, ByteBuffer byteBuffer) {
            this(TYPE, result, byteBuffer);
        }

        protected PackConstantsOp(LIRInstructionClass<? extends PackConstantsOp> c, AllocatableValue result, ByteBuffer byteBuffer) {
            super(c);
            this.result = result;
            this.byteBuffer = byteBuffer;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            final PlatformKind pc = result.getPlatformKind();
            final int alignment = pc.getSizeInBytes() / pc.getVectorLength();
            final AMD64Address address = (AMD64Address) crb.recordDataReferenceInCode(byteBuffer.array(), alignment);
            masm.movdqu(asRegister(result), address);
        }
    }

    private static void reg2addr(CompilationResultBuilder crb, AMD64MacroAssembler masm, AMD64Kind kind, AMD64Address dst, Register src) {
        switch (kind) {
            case BYTE:
                masm.movb(dst, src);
                break;
            case WORD:
                masm.movw(dst, src);
                break;
            case DWORD:
                masm.movl(dst, src);
                break;
            case QWORD:
                masm.movq(dst, src);
                break;
            case SINGLE:
                masm.movl(dst, src);
                break;
            case DOUBLE:
                masm.movq(dst, src);
                break;
        }
    }

    private static void addr2reg(CompilationResultBuilder crb, AMD64MacroAssembler masm, AMD64Kind kind, Register dst, AMD64Address src) {
        switch (kind) {
            case BYTE:
                masm.movb(dst, src);
                break;
            case WORD:
                masm.movw(dst, src);
                break;
            case DWORD:
                masm.movl(dst, src);
                break;
            case QWORD:
                masm.movq(dst, src);
                break;
            case SINGLE:
                masm.movl(dst, src);
                break;
            case DOUBLE:
                masm.movq(dst, src);
                break;
        }
    }

    private static void const2addr(CompilationResultBuilder crb, AMD64MacroAssembler masm, AMD64Kind kind, AMD64Address dst, JavaConstant src) {
        switch (kind) {
            case BYTE:
                masm.movb(dst, src.asInt());
                break;
            case WORD:
                masm.movw(dst, src.asInt());
                break;
            case DWORD:
                masm.movl(dst, src.asInt());
                break;
            case QWORD:
                masm.movlong(dst, src.asLong());
                break;
            case SINGLE:
                masm.movl(dst, floatToRawIntBits(src.asFloat()));
                break;
            case DOUBLE:
                masm.movlong(dst, doubleToRawLongBits(src.asDouble()));
                break;
        }
    }

    private static void move(CompilationResultBuilder crb, AMD64MacroAssembler masm, AMD64Address dst, Value src, Register scratch) {
        if (isRegister(src)) {
            reg2addr(crb, masm, (AMD64Kind) src.getPlatformKind(), dst, asRegister(src));
        } else if (isStackSlot(src)) {
            addr2reg(crb, masm, (AMD64Kind) src.getPlatformKind(), scratch, (AMD64Address) crb.asAddress(src));
            reg2addr(crb, masm, (AMD64Kind) src.getPlatformKind(), dst, scratch);
        } else if (isJavaConstant(src)) {
            const2addr(crb, masm, (AMD64Kind) src.getPlatformKind(), dst, asJavaConstant(src));
        }
    }

    public static final class LoadStackOp extends AMD64LIRInstruction {

        private static final int YMM_LENGTH_IN_BYTES = 32;
        public static final LIRInstructionClass<LoadStackOp> TYPE = LIRInstructionClass.create(LoadStackOp.class);

        @Def({REG}) private AllocatableValue result;
        @Temp({REG}) private AllocatableValue scratch;
        @Alive({COMPOSITE}) private AMD64AddressValue input;
        private final int valcount;

        public LoadStackOp(LIRGeneratorTool tool, AllocatableValue result, AMD64AddressValue input, int valcount) {
            this(TYPE, tool, result, input, valcount);
        }

        protected LoadStackOp(LIRInstructionClass<? extends LoadStackOp> c, LIRGeneratorTool tool, AllocatableValue result, AMD64AddressValue input, int valcount) {
            super(c);
            assert valcount > 0 : "vector store must store at least one element";
            this.result = result;
            this.input = input;
            this.scratch = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
            this.valcount = valcount;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            assert isRegister(scratch) : "scratch must be a register";
            final AMD64Kind vectorKind = (AMD64Kind) result.getPlatformKind();
            final AMD64Kind scalarKind = vectorKind.getScalar();
            final int sizeInBytes = valcount * scalarKind.getSizeInBytes();

            final AMD64Address inputAddress = input.toAddress();
            if (sizeInBytes == YMM_LENGTH_IN_BYTES) {
                masm.vmovdqu(asRegister(result), inputAddress);
            } else {
                // Lowest multiple of YMM_LENGTH_IN_BYTES that can fit all elements.
                int amt = (int) Math.ceil(((double) sizeInBytes) / YMM_LENGTH_IN_BYTES) * YMM_LENGTH_IN_BYTES;

                // Open scratch space for us to dump our values to.
                masm.subq(rsp, amt);
                int offset = 0;
                for (int i = 0; i < valcount;) {
                    AMD64Kind movKind = scalarKind;
                    int movSize = movKind.getSizeInBytes();
                    while (i + (movSize / scalarKind.getSizeInBytes()) * 2 < valcount && movSize < 8) {
                        AMD64Kind prev = movKind;
                        movKind = twice(movKind);
                        movSize = movKind.getSizeInBytes();
                        if (prev == movKind) {
                            break;
                        }
                    }

                    final AMD64Address source = new AMD64Address(inputAddress.getBase(), inputAddress.getIndex(), inputAddress.getScale(), inputAddress.getDisplacement() + offset);
                    final AMD64Address target = new AMD64Address(rsp, offset);

                    addr2reg(crb, masm, movKind, asRegister(scratch), source);
                    reg2addr(crb, masm, movKind, target, asRegister(scratch));
                    offset += movSize;

                    i += movSize / scalarKind.getSizeInBytes();
                }

                // Write memory into vector register.
                masm.vmovdqu(asRegister(result), new AMD64Address(rsp, 0));

                // Pop scratch space.
                masm.addq(rsp, amt);
            }
        }
    }

    public static final class StoreStackOp extends AMD64LIRInstruction {

        private static final int YMM_LENGTH_IN_BYTES = 32;
        public static final LIRInstructionClass<StoreStackOp> TYPE = LIRInstructionClass.create(StoreStackOp.class);

        @Alive({COMPOSITE}) private AMD64AddressValue result;
        @Temp({REG}) private AllocatableValue scratch;
        @Use({REG}) private AllocatableValue input;
        private final int valcount;

        public StoreStackOp(LIRGeneratorTool tool, AMD64AddressValue result, AllocatableValue input, int valcount) {
            this(TYPE, tool, result, input, valcount);
        }

        protected StoreStackOp(LIRInstructionClass<? extends StoreStackOp> c, LIRGeneratorTool tool, AMD64AddressValue result, AllocatableValue input, int valcount) {
            super(c);
            assert valcount > 0 : "vector store must store at least one element";
            this.result = result;
            this.scratch = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
            this.input = input;
            this.valcount = valcount;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            assert isRegister(scratch) : "scratch must be a register";
            final AMD64Kind vectorKind = (AMD64Kind) input.getPlatformKind();
            final AMD64Kind scalarKind = vectorKind.getScalar();
            final int sizeInBytes = valcount * scalarKind.getSizeInBytes();

            final AMD64Address outputAddress = result.toAddress();
            if (sizeInBytes == YMM_LENGTH_IN_BYTES) {
                masm.vmovdqu(outputAddress, asRegister(input));
            } else {
                // Lowest multiple of YMM_LENGTH_IN_BYTES that can fit all elements.
                final int amt = (int) Math.ceil(((double) sizeInBytes) / YMM_LENGTH_IN_BYTES) * YMM_LENGTH_IN_BYTES;

                // Open scratch space for us to dump our values to.
                masm.subq(rsp, amt);

                // Write memory from vector register.
                masm.vmovdqu(new AMD64Address(rsp, 0), asRegister(input));

                int offset = 0;
                for (int i = 0; i < valcount;) {
                    AMD64Kind movKind = scalarKind;
                    int movSize = movKind.getSizeInBytes();
                    while (i + (movSize / scalarKind.getSizeInBytes()) * 2 < valcount && movSize < 8) {
                        final AMD64Kind prev = movKind;
                        movKind = twice(movKind);
                        movSize = movKind.getSizeInBytes();
                        if (prev == movKind) {
                            break;
                        }
                    }

                    final AMD64Address source = new AMD64Address(rsp, offset);
                    final AMD64Address target = new AMD64Address(outputAddress.getBase(), outputAddress.getIndex(), outputAddress.getScale(), outputAddress.getDisplacement() + offset);

                    addr2reg(crb, masm, movKind, asRegister(scratch), source);
                    reg2addr(crb, masm, movKind, target, asRegister(scratch));
                    offset += movSize;

                    i += movSize / scalarKind.getSizeInBytes();
                }

                // Pop scratch space.
                masm.addq(rsp, amt);
            }
        }
    }

    public static final class PackStackOp extends AMD64LIRInstruction {

        private static final int YMM_LENGTH_IN_BYTES = 32;
        public static final LIRInstructionClass<PackStackOp> TYPE = LIRInstructionClass.create(PackStackOp.class);

        @Def({REG}) private AllocatableValue result;
        @Temp({REG}) private AllocatableValue scratch;
        @Use({REG, STACK}) private AllocatableValue[] values;

        public PackStackOp(LIRGeneratorTool tool, AllocatableValue result, List<AllocatableValue> values) {
            this(TYPE, tool, result, values);
        }

        protected PackStackOp(LIRInstructionClass<? extends PackStackOp> c, LIRGeneratorTool tool, AllocatableValue result, List<AllocatableValue> values) {
            super(c);
            assert !values.isEmpty() : "values to pack for pack op cannot be empty";
            this.result = result;
            this.scratch = tool.newVariable(result.getValueKind());
            this.values = values.toArray(new AllocatableValue[0]);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            assert isRegister(scratch) : "scratch must be a register";
            final AMD64Kind vectorKind = (AMD64Kind) result.getPlatformKind();
            final AMD64Kind scalarKind = vectorKind.getScalar();

            // Lowest multiple of YMM_LENGTH_IN_BYTES that can fit all elements.
            int amt = (int) Math.ceil(values.length * scalarKind.getSizeInBytes() / (double) YMM_LENGTH_IN_BYTES) * YMM_LENGTH_IN_BYTES;

            // Open scratch space for us to dump our values to.
            masm.subq(rsp, amt);
            int offset = 0;
            for (AllocatableValue value : values) {
                AMD64Address target = new AMD64Address(rsp, offset);
                move(crb, masm, target, value, asRegister(scratch));
                offset += scalarKind.getSizeInBytes();
            }

            // Write memory into vector register.
            masm.vmovdqu(asRegister(result), new AMD64Address(rsp, 0));

            // Pop scratch space.
            masm.addq(rsp, amt);
        }
    }

    public static final class PackOp extends AMD64LIRInstruction {

        private static final int XMM_LENGTH_IN_BYTES = 16;
        public static final LIRInstructionClass<PackOp> TYPE = LIRInstructionClass.create(PackOp.class);

        @Def({REG}) private AllocatableValue result;
        @Temp({REG}) private AllocatableValue temp;
        @Use({REG, STACK}) private AllocatableValue[] values;

        public PackOp(LIRGeneratorTool tool, AllocatableValue result, List<AllocatableValue> values) {
            this(TYPE, tool, result, values);
        }

        protected PackOp(LIRInstructionClass<? extends PackOp> c, LIRGeneratorTool tool, AllocatableValue result, List<AllocatableValue> values) {
            super(c);
            assert !values.isEmpty() : "values to pack for pack op cannot be empty";
            this.result = result;
            this.temp = tool.newVariable(result.getValueKind());
            this.values = values.toArray(new AllocatableValue[0]);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            final AMD64Kind vectorKind = (AMD64Kind) result.getPlatformKind();
            final AMD64Kind scalarKind = vectorKind.getScalar();

            // How many scalars can we fit into an XMM register?
            final int xmmLengthInElements = XMM_LENGTH_IN_BYTES / scalarKind.getSizeInBytes();
            final int numOverflows = values.length / xmmLengthInElements;

            final AllocatableValue target = numOverflows > 0 ? temp : result;

            for (int i = 0; i < values.length; i++) {
                // If we've filled up the bottom 128 bits.
                if (i > 0 && i % xmmLengthInElements == 0) {
                    new AMD64VectorShuffle.Insert128Op(result, target, result, i / xmmLengthInElements).emitCode(crb, masm);
                }

                final int targetIndex = i % xmmLengthInElements;
                final AllocatableValue scalarValue = values[i];

                switch (scalarKind) {
                    case BYTE:
                        new AMD64VectorShuffle.InsertByteOp(target, scalarValue, targetIndex).emitCode(crb, masm);
                        break;
                    case WORD:
                        new AMD64VectorShuffle.InsertShortOp(target, scalarValue, targetIndex).emitCode(crb, masm);
                        break;
                    case DWORD:
                        new AMD64VectorShuffle.InsertIntOp(target, scalarValue, targetIndex).emitCode(crb, masm);
                        break;
                    case QWORD:
                        new AMD64VectorShuffle.InsertLongOp(target, scalarValue, targetIndex).emitCode(crb, masm);
                        break;
                    case SINGLE:
                        new AMD64VectorShuffle.InsertFloatOp(target, scalarValue, targetIndex).emitCode(crb, masm);
                        break;
                    case DOUBLE:
                        new AMD64VectorShuffle.InsertDoubleOp(target, scalarValue, targetIndex).emitCode(crb, masm);
                        break;
                }
            }

            // If we're using a scratch register, write it into the result.
            if (numOverflows > 0) {
                new AMD64VectorShuffle.Insert128Op(result, target, result, numOverflows).emitCode(crb, masm);
            }
        }
    }

}
