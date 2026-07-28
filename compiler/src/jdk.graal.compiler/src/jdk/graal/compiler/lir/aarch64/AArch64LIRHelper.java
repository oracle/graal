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
package jdk.graal.compiler.lir.aarch64;

import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

public final class AArch64LIRHelper {

    private AArch64LIRHelper() {
    }

    protected static void loadExternalAddress(CompilationResultBuilder crb, AArch64MacroAssembler masm, Register dst, ArrayDataPointerConstant ptr) {
        crb.recordDataReferenceInCode(ptr);
        masm.adrpAdd(dst);
    }

    protected static void guaranteeFixedRegister(Value value, Register expected, String name) {
        GraalError.guarantee(asRegister(value).equals(expected), "expect %s at %s, but was %s", name, expected, value);
    }

    protected static ArrayDataPointerConstant pointerConstant(int alignment, byte[] bytes) {
        return new ArrayDataPointerConstant(bytes, alignment);
    }

    protected static ArrayDataPointerConstant pointerConstant(int alignment, int[] ints) {
        return new ArrayDataPointerConstant(ints, alignment);
    }

    protected static ArrayDataPointerConstant pointerConstant(int alignment, long[] longs) {
        return new ArrayDataPointerConstant(longs, alignment);
    }
}
