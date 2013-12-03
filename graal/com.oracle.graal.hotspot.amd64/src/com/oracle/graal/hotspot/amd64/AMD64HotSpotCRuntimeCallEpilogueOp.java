/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.amd64;

import com.oracle.graal.api.code.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;

@Opcode("CRUNTIME_CALL_EPILOGUE")
final class AMD64HotSpotCRuntimeCallEpilogueOp extends AMD64LIRInstruction {

    private final int threadLastJavaSpOffset;
    private final int threadLastJavaFpOffset;
    private final Register thread;

    public AMD64HotSpotCRuntimeCallEpilogueOp(int threadLastJavaSpOffset, int threadLastJavaFpOffset, Register thread) {
        this.threadLastJavaSpOffset = threadLastJavaSpOffset;
        this.threadLastJavaFpOffset = threadLastJavaFpOffset;
        this.thread = thread;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        // reset last Java frame:
        masm.movslq(new AMD64Address(thread, threadLastJavaSpOffset), 0);
        masm.movslq(new AMD64Address(thread, threadLastJavaFpOffset), 0);
    }
}
