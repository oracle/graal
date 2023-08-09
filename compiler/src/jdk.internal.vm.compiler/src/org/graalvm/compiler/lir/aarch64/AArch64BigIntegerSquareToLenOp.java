/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.aarch64;

import static jdk.vm.ci.aarch64.AArch64.r10;
import static jdk.vm.ci.aarch64.AArch64.r11;
import static jdk.vm.ci.aarch64.AArch64.r12;
import static jdk.vm.ci.aarch64.AArch64.r13;
import static jdk.vm.ci.aarch64.AArch64.r14;
import static jdk.vm.ci.aarch64.AArch64.r15;
import static jdk.vm.ci.aarch64.AArch64.r16;
import static jdk.vm.ci.aarch64.AArch64.r17;
import static jdk.vm.ci.aarch64.AArch64.r19;
import static jdk.vm.ci.aarch64.AArch64.r20;
import static jdk.vm.ci.aarch64.AArch64.r21;
import static jdk.vm.ci.aarch64.AArch64.r22;
import static jdk.vm.ci.aarch64.AArch64.r23;
import static jdk.vm.ci.aarch64.AArch64.r5;
import static jdk.vm.ci.aarch64.AArch64.r6;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.aarch64.AArch64BigIntegerMultiplyToLenOp.multiplyToLen;

import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.SyncPort;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/d7b941640638b35f9ac1ef11cd6bf6ccb795c29a/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L4665-L4699",
          sha1 = "0ad03e74934e230a64b9eb107a413248daa5be88")
// @formatter:on
public final class AArch64BigIntegerSquareToLenOp extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64BigIntegerSquareToLenOp> TYPE = LIRInstructionClass.create(AArch64BigIntegerSquareToLenOp.class);

    @Alive({REG}) private Value xValue;
    @Alive({REG}) private Value lenValue;
    @Alive({REG}) private Value zValue;
    @Alive({REG}) private Value zlenValue;

    @Temp protected Value[] temps;

    public AArch64BigIntegerSquareToLenOp(
                    Value xValue,
                    Value lenValue,
                    Value zValue,
                    Value zlenValue) {
        super(TYPE);

        this.xValue = xValue;
        this.lenValue = lenValue;
        this.zValue = zValue;
        this.zlenValue = zlenValue;

        this.temps = new Value[]{
                        r5.asValue(),
                        r6.asValue(),
                        r10.asValue(),
                        r11.asValue(),
                        r12.asValue(),
                        r13.asValue(),
                        r14.asValue(),
                        r15.asValue(),
                        r16.asValue(),
                        r17.asValue(),
                        r19.asValue(),
                        r20.asValue(),
                        r21.asValue(),
                        r22.asValue(),
                        r23.asValue(),
        };
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        GraalError.guarantee(xValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid xValue kind: %s", xValue);
        GraalError.guarantee(lenValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid lenValue kind: %s", lenValue);
        GraalError.guarantee(zValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid zValue kind: %s", zValue);
        GraalError.guarantee(zlenValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid zlenValue kind: %s", zlenValue);

        Register x = asRegister(xValue);
        Register xlen = asRegister(lenValue);
        Register y = r5;
        Register ylen = r6;
        Register z = asRegister(zValue);
        Register zlen = asRegister(zlenValue);

        masm.mov(64, y, x);
        masm.mov(32, ylen, xlen);

        multiplyToLen(masm, x, xlen, y, ylen, z, zlen,
                        r10, r11, r12, r13, r14, r15, r16, r17,
                        r19, r20, r21, r22, r23);
    }

}
