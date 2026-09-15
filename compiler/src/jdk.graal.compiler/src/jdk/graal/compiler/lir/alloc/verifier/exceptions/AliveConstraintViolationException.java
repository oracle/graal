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
package jdk.graal.compiler.lir.alloc.verifier.exceptions;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.alloc.verifier.RAVInstruction;
import jdk.graal.compiler.lir.alloc.verifier.RAValue;

/**
 * Violation of the alive inputs occurred, the same location was marked as alive argument as well as
 * temp or output.
 */
@SuppressWarnings("serial")
public class AliveConstraintViolationException extends RAVException {
    public final RAVInstruction.Op instruction;

    /**
     * Construct an AliveConstraintViolationException.
     *
     * @param instruction Instruction where violation occurred
     * @param block Block where violation occurred
     * @param location Location that is being shared
     * @param asDest Alive location was used as an output
     */
    public AliveConstraintViolationException(RAVInstruction.Op instruction, BasicBlock<?> block, RAValue location, boolean asDest) {
        super(AliveConstraintViolationException.getErrorMessage(location, asDest), instruction, block);
        this.instruction = instruction;
    }

    static String getErrorMessage(RAValue location, boolean asDest) {
        if (asDest) {
            return "Location " + location + " used as both alive and output";
        }

        return "Location " + location + " used as both alive and temp";
    }
}
