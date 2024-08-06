/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.amd64.AMD64.CPUFeature.CLWB;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.FLUSHOPT;

import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;

/**
 * Implements {@code jdk.internal.misc.Unsafe.writebackPostSync0(long)}.
 */
public final class AMD64CacheWritebackPostSyncOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64CacheWritebackPostSyncOp> TYPE = LIRInstructionClass.create(AMD64CacheWritebackPostSyncOp.class);

    public AMD64CacheWritebackPostSyncOp() {
        super(TYPE);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        boolean optimized = masm.supports(FLUSHOPT);
        boolean noEvict = masm.supports(CLWB);

        // pick the correct implementation
        if (optimized || noEvict) {
            // need an sfence for post flush when using clflushopt or clwb
            // otherwise no need for any synchroniaztion
            masm.sfence();
        }
    }
}
