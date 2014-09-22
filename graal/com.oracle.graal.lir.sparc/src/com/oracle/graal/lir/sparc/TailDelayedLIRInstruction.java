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

import com.oracle.graal.lir.*;

/**
 * Implementors of this interface are able to place the last instruction to the delay slot of the
 * given {@link DelaySlotHolder}.
 *
 * This LIR instruction is still emitted in the usual way. But when emitting code for this LIR
 * instruction before the last instruction, it can transfer control over to the delay slot holder
 * LIR instruction, which then can emit code in order to get to the delay slot.
 *
 * Steps for emit delayed code
 * <ol>
 * <li>If this instruction contains more than one instruction, emit everything up to the second last
 * instruction.</li>
 * <li>Then call the
 * {@link DelaySlotHolder#emitForDelay(com.oracle.graal.lir.asm.CompilationResultBuilder, com.oracle.graal.asm.sparc.SPARCMacroAssembler)}
 * to let the delay-slot holder emit its code.</li>
 * <li>emit the last instruction for this {@link LIRInstruction}</li>
 * </ol>
 *
 * Note: If this instruction decides not to use the delay slot, it can skip the call of
 * {@link DelaySlotHolder#emitForDelay(com.oracle.graal.lir.asm.CompilationResultBuilder, com.oracle.graal.asm.sparc.SPARCMacroAssembler)}
 * and the code generation will continue without using the delay slot. Nothing other steps are
 * required.
 */
public interface TailDelayedLIRInstruction {
    public void setDelaySlotHolder(DelaySlotHolder holder);
}
