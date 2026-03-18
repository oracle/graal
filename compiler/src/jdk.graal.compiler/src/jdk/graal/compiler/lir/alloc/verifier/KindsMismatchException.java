/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.LIRInstruction;

/**
 * Kinds are not matching between two values.
 */
@SuppressWarnings("serial")
public class KindsMismatchException extends RAVException {
    public LIRInstruction instruction;
    public BasicBlock<?> block;
    public RAValue value1;
    public RAValue value2;
    public boolean origVsCurr;

    /**
     * Construct a KindsMismatchException.
     *
     * @param instruction Instruction where violation occurred
     * @param block Block where violation occurred
     * @param value1 First value in comparison, original variable.
     * @param value2 Second value in comparison, either current location or value stored in state
     * @param origVsCurr Comparing original variable to current location
     */
    public KindsMismatchException(LIRInstruction instruction, BasicBlock<?> block, RAValue value1, RAValue value2, boolean origVsCurr) {
        super(KindsMismatchException.getErrorMessage(instruction, block, value1, value2, origVsCurr));

        this.instruction = instruction;
        this.block = block;
        this.value1 = value1;
        this.value2 = value2;
        this.origVsCurr = origVsCurr;
    }

    static String getErrorMessage(LIRInstruction instruction, BasicBlock<?> block, RAValue value1, RAValue value2, boolean origVsCurr) {
        if (origVsCurr) {
            return value1.getValue() + " has different kind after allocation: " + value2.getValue() + " in " + instruction + " in block " + block;
        }

        return "Value in location has different kind: " + value1.getValue().getValueKind() + " vs. " + value2.getValue().getValueKind() + " in " + instruction + " in block " + block;
    }
}
