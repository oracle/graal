/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.lir.amd64;

import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;

public final class AMD64PrefetchOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64PrefetchOp> TYPE = LIRInstructionClass.create(AMD64PrefetchOp.class);

    private final int instr;  // AllocatePrefetchInstr
    @Alive({OperandFlag.COMPOSITE}) protected AMD64AddressValue address;

    public AMD64PrefetchOp(AMD64AddressValue address, int instr) {
        super(TYPE);
        this.address = address;
        this.instr = instr;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        switch (instr) {
            case 0:
                masm.prefetchnta(address.toAddress());
                break;
            case 1:
                masm.prefetcht0(address.toAddress());
                break;
            case 2:
                masm.prefetcht2(address.toAddress());
                break;
            case 3:
                masm.prefetchw(address.toAddress());
                break;
            default:
                throw GraalError.shouldNotReachHere("unspported prefetch op " + instr); // ExcludeFromJacocoGeneratedReport

        }
    }
}
