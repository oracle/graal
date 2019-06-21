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

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.PlatformKind;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.amd64.AMD64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import java.nio.ByteBuffer;

import static jdk.vm.ci.code.ValueUtil.asRegister;

public final class AMD64Packing {

    private AMD64Packing() { }

    public static final class PackConstantsOp extends AMD64LIRInstruction {

        public static final LIRInstructionClass<PackConstantsOp> TYPE = LIRInstructionClass.create(PackConstantsOp.class);

        private final ByteBuffer byteBuffer;

        @Def({OperandFlag.REG}) private final AllocatableValue result;

        public PackConstantsOp(AllocatableValue result, ByteBuffer byteBuffer) {
            this(TYPE, result, byteBuffer);
        }

        protected PackConstantsOp(LIRInstructionClass<? extends PackConstantsOp> c, AllocatableValue result, ByteBuffer byteBuffer) {
            super(TYPE);
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

}
