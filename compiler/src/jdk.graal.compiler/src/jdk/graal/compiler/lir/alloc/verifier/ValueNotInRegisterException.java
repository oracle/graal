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

/**
 * Value was not found in the location we needed it in.
 */
@SuppressWarnings("serial")
public class ValueNotInRegisterException extends RAVException {
    public RAVInstruction.Op instruction;

    /**
     * Symbol that was not found at the location.
     *
     * <p>
     * Can be a constant, variable, or other symbolic value.
     * </p>
     */
    public RAValue variable;

    /**
     * Location where the symbol was not found.
     *
     * <p>
     * Can be a register or a (virtual) stack slot.
     * </p>
     */
    public RAValue location;
    public AllocationState state;
    public BlockVerifierState blockVerifierState;

    /**
     * Construct a ValueNotInRegisterException.
     *
     * @param instruction Instruction where violation occurred
     * @param block Block where violation occurred
     * @param variable Target variable we are looking for
     * @param location Location where we couldn't find it
     * @param state The actual state that the location is in
     */
    public ValueNotInRegisterException(RAVInstruction.Op instruction, BasicBlock<?> block, RAValue variable, RAValue location, AllocationState state, BlockVerifierState blockVerifierState) {
        super(ValueNotInRegisterException.getErrorMessage(variable, location, state), instruction, block);

        this.variable = variable;
        this.location = location;
        this.state = state;
        this.blockVerifierState = new BlockVerifierState(block, blockVerifierState);
        this.instruction = instruction;
    }

    static String getErrorMessage(RAValue variable, RAValue location, AllocationState state) {
        return "Value " + variable + " not found in " + location + " the actual state is " + state;
    }
}
