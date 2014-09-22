/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.sparc;

import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;

/**
 * This interface can be used for {@link LIRInstruction}s which may provide a delay slot. If a delay
 * slot for this LIRInstruction is requrested, the requester just calls the method
 * {@link #emitForDelay(CompilationResultBuilder, SPARCMacroAssembler)}.
 *
 * @see TailDelayedLIRInstruction
 */
public interface DelaySlotHolder {

    DelaySlotHolder DUMMY = new DelaySlotHolder() {
        public void emitForDelay(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            // do nothing
        }

        @Override
        public String toString() {
            return "null";
        }
    };

    public void emitForDelay(CompilationResultBuilder crb, SPARCMacroAssembler masm);

}
