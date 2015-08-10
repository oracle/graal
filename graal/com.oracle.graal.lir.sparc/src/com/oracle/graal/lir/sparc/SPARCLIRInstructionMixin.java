/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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

public interface SPARCLIRInstructionMixin {

    default boolean leavesRegisterWindow() {
        return false;
    }

    default void setDelayedControlTransfer(SPARCDelayedControlTransfer holder) {
        assert this instanceof SPARCTailDelayedLIRInstruction : this;
        getSPARCLIRInstructionStore().delayedControlTransfer = holder;
    }

    default SPARCDelayedControlTransfer getDelayedControlTransfer() {
        return getSPARCLIRInstructionStore().delayedControlTransfer;
    }

    default SizeEstimate estimateSize() {
        return getSPARCLIRInstructionStore().estimate;
    }

    SPARCLIRInstructionMixinStore getSPARCLIRInstructionStore();

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

    public static class SPARCLIRInstructionMixinStore {
        public SizeEstimate estimate;
        public SPARCDelayedControlTransfer delayedControlTransfer = SPARCDelayedControlTransfer.DUMMY;

        public SPARCLIRInstructionMixinStore(SizeEstimate estimate) {
            this.estimate = estimate;
        }
    }
}
