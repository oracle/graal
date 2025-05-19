/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.lir.amd64;

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveMaskOp.KMOVQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVAPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVAPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVDQA32;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVDQU32;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVUPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVUPS;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64SIMDInstructionEncoding;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.code.DataSection.Data;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.StandardOp.LoadConstantOp;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;

public final class AVXLoadConstantVectorOp extends AMD64LIRInstruction implements LoadConstantOp {
    public static final LIRInstructionClass<AVXLoadConstantVectorOp> TYPE = LIRInstructionClass.create(AVXLoadConstantVectorOp.class);

    @Def({REG}) protected AllocatableValue result;
    protected SimdConstant value;
    private final AMD64Kind elementKind;
    private final AMD64SIMDInstructionEncoding encoding;

    public AVXLoadConstantVectorOp(AllocatableValue result, SimdConstant value, AMD64Kind elementKind, AMD64SIMDInstructionEncoding encoding) {
        super(TYPE);
        this.result = result;
        this.value = value;
        this.elementKind = elementKind;
        this.encoding = encoding;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        AMD64Kind kind = (AMD64Kind) result.getPlatformKind();

        if (kind.isMask()) {
            Data data = crb.dataBuilder.createDataItem(value.toBitMask());
            AMD64Address address = (AMD64Address) crb.recordDataSectionReference(data);
            KMOVQ.emit(masm, AVXKind.AVXSize.XMM, asRegister(result), address);
            return;
        }

        AVXKind.AVXSize dataSize = AVXKind.getDataSize(kind);
        boolean canForceAlignment = crb.dataBuilder.canForceAlignmentOf(dataSize.getBytes());

        VexMoveOp op;
        switch (elementKind) {
            case BYTE:
            case WORD:
            case DWORD:
            case QWORD:
                switch (dataSize) {
                    case DWORD:
                        op = VMOVD.encoding(encoding);
                        break;
                    case QWORD:
                        op = VMOVQ.encoding(encoding);
                        break;
                    default:
                        op = canForceAlignment ? VMOVDQA32.encoding(encoding) : VMOVDQU32.encoding(encoding);
                        break;
                }
                break;
            case SINGLE:
                assert dataSize == AVXKind.getRegisterSize(kind) : dataSize + " " + kind;
                op = canForceAlignment ? VMOVAPS.encoding(encoding) : VMOVUPS.encoding(encoding);
                break;
            case DOUBLE:
                assert dataSize == AVXKind.getRegisterSize(kind) : dataSize + " " + kind;
                op = canForceAlignment ? VMOVAPD.encoding(encoding) : VMOVUPD.encoding(encoding);
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(elementKind); // ExcludeFromJacocoGeneratedReport
        }

        Data data = crb.dataBuilder.createMultiDataItem(value.getValues());
        int alignment = crb.dataBuilder.ensureValidDataAlignment(kind.getSizeInBytes());
        crb.dataBuilder.updateAlignment(data, alignment);
        AMD64Address address = (AMD64Address) crb.recordDataSectionReference(data);
        op.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), address);
    }

    @Override
    public Constant getConstant() {
        return value;
    }

    @Override
    public AllocatableValue getResult() {
        return result;
    }

    @Override
    public boolean canRematerializeToStack() {
        return false;
    }
}
