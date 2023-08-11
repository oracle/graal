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

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.asm.ArrayDataPointerConstant;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

public final class AMD64LIRHelper {

    private AMD64LIRHelper() {
    }

    protected static Value[] registersToValues(Register[] registers) {
        Value[] temps = new Value[registers.length];
        for (int i = 0; i < registers.length; i++) {
            Register register = registers[i];
            if (AMD64.CPU.equals(register.getRegisterCategory())) {
                temps[i] = register.asValue(LIRKind.value(AMD64Kind.QWORD));
            } else if (AMD64.XMM.equals(register.getRegisterCategory())) {
                temps[i] = register.asValue(LIRKind.value(AMD64Kind.DOUBLE));
            } else {
                throw GraalError.shouldNotReachHere("Unsupported register type in math stubs."); // ExcludeFromJacocoGeneratedReport
            }
        }
        return temps;
    }

    protected static AMD64Address recordExternalAddress(CompilationResultBuilder crb, ArrayDataPointerConstant ptr) {
        return (AMD64Address) crb.recordDataReferenceInCode(ptr);
    }

    protected static ArrayDataPointerConstant pointerConstant(int alignment, int[] ints) {
        return new ArrayDataPointerConstant(ints, alignment);
    }

    protected static ArrayDataPointerConstant pointerConstant(int alignment, long[] longs) {
        return new ArrayDataPointerConstant(longs, alignment);
    }
}
