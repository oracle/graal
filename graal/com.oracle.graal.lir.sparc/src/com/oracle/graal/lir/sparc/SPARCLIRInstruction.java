/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * Convenience class to provide SPARCMacroAssembler for the {@link #emitCode} method.
 */
public abstract class SPARCLIRInstruction extends LIRInstruction {
    public static final LIRInstructionClass<SPARCLIRInstruction> TYPE = LIRInstructionClass.create(SPARCLIRInstruction.class);
    private final SizeEstimate size;

    protected SPARCLIRInstruction(LIRInstructionClass<? extends LIRInstruction> c) {
        this(c, null);
    }

    protected SPARCLIRInstruction(LIRInstructionClass<? extends LIRInstruction> c, SizeEstimate size) {
        super(c);
        this.size = size;
    }

    protected SPARCDelayedControlTransfer delayedControlTransfer = SPARCDelayedControlTransfer.DUMMY;

    @Override
    public final void emitCode(CompilationResultBuilder crb) {
        emitCode(crb, (SPARCMacroAssembler) crb.asm);
    }

    public abstract void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm);

    public boolean leavesRegisterWindow() {
        return false;
    }

    public void setDelayedControlTransfer(SPARCDelayedControlTransfer holder) {
        this.delayedControlTransfer = holder;
    }

    public SizeEstimate estimateSize() {
        return size;
    }

    /**
     * This class represents a size estimation of a particular LIR instruction. It contains a
     * pessimistic estimate of emitted SPARC instructions and emitted bytes into the constant
     * section.
     */
    public static class SizeEstimate {
        /**
         * Cache the first size definition (with just 0 as constant size).
         */
        private static final SizeEstimate[] cache = new SizeEstimate[5];
        static {
            for (int i = 0; i < cache.length; i++) {
                cache[i] = new SizeEstimate(i, 0);
            }
        }
        public final int instructionSize;
        public final int constantSize;

        public SizeEstimate(int instructionSize, int constantSize) {
            this.instructionSize = instructionSize;
            this.constantSize = constantSize;
        }

        public static SizeEstimate create(int instructionSize, int constantSize) {
            if (constantSize == 0 && instructionSize < cache.length) {
                return cache[instructionSize];
            } else {
                return new SizeEstimate(instructionSize, constantSize);
            }
        }

        public static SizeEstimate create(int instructionSize) {
            if (instructionSize < cache.length) {
                return cache[instructionSize];
            } else {
                return new SizeEstimate(instructionSize, 0);
            }
        }

        @Override
        public String toString() {
            return "SE[i=" + instructionSize + ", c=" + constantSize + "]";
        }
    }
}
