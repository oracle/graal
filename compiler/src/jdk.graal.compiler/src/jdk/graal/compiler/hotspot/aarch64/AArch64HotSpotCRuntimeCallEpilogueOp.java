/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.aarch64;

import static jdk.vm.ci.aarch64.AArch64.zr;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.aarch64.AArch64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;

@Opcode("CRUNTIME_CALL_EPILOGUE")
public class AArch64HotSpotCRuntimeCallEpilogueOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64HotSpotCRuntimeCallEpilogueOp> TYPE = LIRInstructionClass.create(AArch64HotSpotCRuntimeCallEpilogueOp.class);

    private final int threadLastJavaSpOffset;
    private final int threadLastJavaPcOffset;
    private final Register thread;
    @SuppressWarnings("unused") private final Label label;

    public AArch64HotSpotCRuntimeCallEpilogueOp(int threadLastJavaSpOffset, int threadLastJavaPcOffset, Register thread, Label label) {
        super(TYPE);
        this.threadLastJavaSpOffset = threadLastJavaSpOffset;
        this.threadLastJavaPcOffset = threadLastJavaPcOffset;
        this.thread = thread;
        this.label = label;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        // Reset last Java frame:
        masm.str(64, zr, masm.makeAddress(64, thread, threadLastJavaSpOffset));
        masm.str(64, zr, masm.makeAddress(64, thread, threadLastJavaPcOffset));
    }
}
