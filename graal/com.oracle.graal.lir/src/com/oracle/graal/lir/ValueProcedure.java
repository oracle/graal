/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.lir.LIRInstruction.*;

/**
 * Similar to {@link InstructionValueProcedure} but without an {@link LIRInstruction} parameter.
 */
public abstract class ValueProcedure extends InstructionValueProcedure {

    /**
     * Iterator method to be overwritten. This version of the iterator does not take additional
     * parameters to keep the signature short.
     *
     * @param value The value that is iterated.
     * @return The new value to replace the value that was passed in.
     */
    public Value doValue(Value value) {
        throw GraalInternalError.shouldNotReachHere("One of the doValue() methods must be overwritten");
    }

    /**
     * Iterator method to be overwritten. This version of the iterator gets additional parameters
     * about the processed value.
     *
     * @param value The value that is iterated.
     * @param mode The operand mode for the value.
     * @param flags A set of flags for the value.
     * @return The new value to replace the value that was passed in.
     */
    public Value doValue(Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
        return doValue(value);
    }

    @Override
    public final Value doValue(LIRInstruction instruction, Value value) {
        throw GraalInternalError.shouldNotReachHere("This doValue() method should never be called");
    }

    @Override
    public final Value doValue(LIRInstruction instruction, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
        return doValue(value, mode, flags);
    }
}