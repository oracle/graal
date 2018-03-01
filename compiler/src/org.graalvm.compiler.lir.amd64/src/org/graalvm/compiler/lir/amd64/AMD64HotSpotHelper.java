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

import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbx;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdx;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.lir.asm.ArrayDataPointerConstant;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;

public class AMD64HotSpotHelper {

    // Checkstyle: stop
    static AMD64Address Address(Register reg, int offset) {
        return new AMD64Address(reg, offset);
    }

    static AMD64Address Address(Register reg, Register offset) {
        return new AMD64Address(reg, offset, AMD64Address.Scale.Times1);
    }

    static AMD64Address Address(Register reg, Register offset, AMD64Address.Scale scale, int displacement) {
        return new AMD64Address(reg, offset, scale, displacement);
    }

    static AMD64Address ExternalAddress(CompilationResultBuilder crb, ArrayDataPointerConstant ptr) {
        return (AMD64Address) crb.recordDataReferenceInCode(ptr);
    }

    static ArrayDataPointerConstant pointerConstant(int alignment, int[] ints) {
        return new ArrayDataPointerConstant(ints, alignment);
    }

    static void assert_different_registers(Register tmp1, Register tmp2, Register tmp3, Register tmp4, Register tmp5) {
    }

    static void assert_different_registers(Register tmp1, Register tmp2, Register tmp3, Register tmp4) {
    }

    static final Register eax = rax;
    static final Register edx = rdx;
    static final Register ecx = rcx;
    static final Register ebx = rbx;
    static final AMD64Assembler.ConditionFlag aboveEqual = AMD64Assembler.ConditionFlag.AboveEqual;
    static final AMD64Assembler.ConditionFlag above = AMD64Assembler.ConditionFlag.Above;
    static final AMD64Assembler.ConditionFlag notEqual = AMD64Assembler.ConditionFlag.NotEqual;
    static final AMD64Assembler.ConditionFlag equal = AMD64Assembler.ConditionFlag.Equal;
    static final AMD64Assembler.ConditionFlag greater = AMD64Assembler.ConditionFlag.Greater;
    static final AMD64Assembler.ConditionFlag greaterEqual = AMD64Assembler.ConditionFlag.GreaterEqual;
    static final AMD64Assembler.ConditionFlag less = AMD64Assembler.ConditionFlag.Less;
    static final AMD64Assembler.ConditionFlag lessEqual = AMD64Assembler.ConditionFlag.LessEqual;
    static final AMD64Assembler.ConditionFlag below = AMD64Assembler.ConditionFlag.Below;
    static final AMD64Assembler.ConditionFlag belowEqual = AMD64Assembler.ConditionFlag.BelowEqual;

    static final AMD64Address.Scale times_1 = AMD64Address.Scale.Times1;
    static final AMD64Address.Scale times_8 = AMD64Address.Scale.Times8;
    static final int INT_MIN = Integer.MIN_VALUE;
    // Checkstyle: resume
}
