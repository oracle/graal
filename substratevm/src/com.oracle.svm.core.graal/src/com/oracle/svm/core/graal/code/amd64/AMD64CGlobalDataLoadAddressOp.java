/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.svm.core.graal.code.amd64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.amd64.AMD64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import com.oracle.svm.core.c.CGlobalDataImpl;
import com.oracle.svm.core.graal.code.CGlobalDataReference;

import jdk.vm.ci.meta.AllocatableValue;

public final class AMD64CGlobalDataLoadAddressOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64CGlobalDataLoadAddressOp> TYPE = LIRInstructionClass.create(AMD64CGlobalDataLoadAddressOp.class);

    @Def(REG) private AllocatableValue result;

    private final CGlobalDataImpl<?> data;

    AMD64CGlobalDataLoadAddressOp(CGlobalDataImpl<?> data, AllocatableValue result) {
        super(TYPE);
        this.data = data;
        this.result = result;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        int before = masm.position();
        AMD64Address address = masm.getPlaceholder(before);
        if (data.bytesSupplier != null || data.sizeSupplier != null) {
            // Data: load its address
            masm.leaq(asRegister(result), address);
        } else {
            // Pure symbol reference: the data contains the symbol's address, load it
            assert data.symbolName != null;
            masm.movq(asRegister(result), address);
        }
        crb.compilationResult.recordDataPatch(before, new CGlobalDataReference(data));
    }
}
