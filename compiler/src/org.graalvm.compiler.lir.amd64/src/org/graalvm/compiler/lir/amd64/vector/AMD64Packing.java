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

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.amd64.AMD64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;

import static jdk.vm.ci.code.ValueUtil.asRegister;

public final class AMD64Packing {

    private AMD64Packing() { }

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

    public static final class PackOp extends AMD64LIRInstruction {

        private static final int XMM_LENGTH_IN_BYTES = 16;
        public static final LIRInstructionClass<PackOp> TYPE = LIRInstructionClass.create(PackOp.class);

        @Def({REG}) private AllocatableValue result;
        @Temp({REG, STACK}) private AllocatableValue temp;
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
