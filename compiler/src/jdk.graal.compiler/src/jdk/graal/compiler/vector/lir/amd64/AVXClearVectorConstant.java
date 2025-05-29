/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.vector.nodes.simd.SimdConstant;

import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64SIMDInstructionEncoding;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.StandardOp.LoadConstantOp;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorClearOp;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;

public final class AVXClearVectorConstant extends AMD64VectorClearOp implements LoadConstantOp {
    public static final LIRInstructionClass<AVXClearVectorConstant> TYPE = LIRInstructionClass.create(AVXClearVectorConstant.class);

    private final SimdConstant input;

    public AVXClearVectorConstant(AllocatableValue result, SimdConstant input) {
        this(result, input, AMD64SIMDInstructionEncoding.VEX);
    }

    public AVXClearVectorConstant(AllocatableValue result, SimdConstant input, AMD64SIMDInstructionEncoding encoding) {
        super(TYPE, result, encoding);
        this.input = input;
    }

    @Override
    public AllocatableValue getResult() {
        return result;
    }

    @Override
    public Constant getConstant() {
        return input;
    }

    @Override
    public boolean canRematerializeToStack() {
        return false;
    }
}
