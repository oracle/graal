/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.amd64;

import jdk.vm.ci.amd64.AMD64;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

/**
 * This is the base class for all unary math intrinsics. It assumes both input and output are xmm0.
 * Users are responsible for adding two mov LIRs before and after this LIR instruction, to move the
 * input to xmm0, and to move the result from xmm0.
 */
public abstract class AMD64MathIntrinsicUnaryOp extends AMD64LIRInstruction {

    @Temp protected Value[] temps;

    public AMD64MathIntrinsicUnaryOp(LIRInstructionClass<? extends AMD64MathIntrinsicUnaryOp> type, Register... registers) {
        super(type);

        temps = new Value[registers.length];
        for (int i = 0; i < registers.length; i++) {
            Register register = registers[i];
            if (AMD64.CPU.equals(register.getRegisterCategory())) {
                temps[i] = register.asValue(LIRKind.value(AMD64Kind.QWORD));
            } else if (AMD64.XMM.equals(register.getRegisterCategory())) {
                temps[i] = register.asValue(LIRKind.value(AMD64Kind.DOUBLE));
            } else {
                throw GraalError.shouldNotReachHere("Unsupported register type in math stubs.");
            }
        }
    }

}
