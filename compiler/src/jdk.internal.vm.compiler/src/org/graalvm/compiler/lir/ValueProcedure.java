/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir;

import java.util.EnumSet;

import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;

import jdk.vm.ci.meta.Value;

/**
 * Similar to {@link InstructionValueProcedure} but without an {@link LIRInstruction} parameter.
 */
@FunctionalInterface
public interface ValueProcedure extends InstructionValueProcedure {

    /**
     * Iterator method to be overwritten.
     *
     * @param value The value that is iterated.
     * @param mode The operand mode for the value.
     * @param flags A set of flags for the value.
     * @return The new value to replace the value that was passed in.
     */
    Value doValue(Value value, OperandMode mode, EnumSet<OperandFlag> flags);

    @Override
    default Value doValue(LIRInstruction instruction, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
        return doValue(value, mode, flags);
    }
}
