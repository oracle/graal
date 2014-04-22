/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.gen;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;

public interface LIRGeneratorTool extends ArithmeticLIRGenerator {

    TargetDescription target();

    MetaAccessProvider getMetaAccess();

    CodeCacheProvider getCodeCache();

    ForeignCallsProvider getForeignCalls();

    Value emitLoad(PlatformKind kind, Value address, Access access);

    void emitStore(PlatformKind kind, Value address, Value input, Access access);

    Value emitCompareAndSwap(Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue);

    /**
     * Emit an atomic read-and-add instruction.
     *
     * @param address address of the value to be read and written
     * @param delta the value to be added
     */
    default Value emitAtomicReadAndAdd(Value address, Value delta) {
        throw GraalInternalError.unimplemented();
    }

    /**
     * Emit an atomic read-and-write instruction.
     *
     * @param address address of the value to be read and written
     * @param newValue the new value to be written
     */
    default Value emitAtomicReadAndWrite(Value address, Value newValue) {
        throw GraalInternalError.unimplemented();
    }

    void emitDeoptimize(Value actionAndReason, Value failedSpeculation, DeoptimizingNode deopting);

    Value emitForeignCall(ForeignCallLinkage linkage, DeoptimizingNode info, Value... args);

    /**
     * Checks whether the supplied constant can be used without loading it into a register for most
     * operations, i.e., for commonly used arithmetic, logical, and comparison operations.
     *
     * @param c The constant to check.
     * @return True if the constant can be used directly, false if the constant needs to be in a
     *         register.
     */
    boolean canInlineConstant(Constant c);

    boolean canStoreConstant(Constant c, boolean isCompressed);

    RegisterAttributes attributes(Register register);

    AllocatableValue newVariable(PlatformKind kind);

    AllocatableValue emitMove(Value input);

    void emitMove(AllocatableValue dst, Value src);

    /**
     * Emits an op that loads the address of some raw data.
     *
     * @param dst the variable into which the address is loaded
     * @param data the data to be installed with the generated code
     */
    void emitData(AllocatableValue dst, byte[] data);

    Value emitAddress(Value base, long displacement, Value index, int scale);

    Value emitAddress(StackSlot slot);

    void emitMembar(int barriers);

    void emitUnwind(Value operand);

    /**
     * Called just before register allocation is performed on the LIR owned by this generator.
     * Overriding implementations of this method must call the overridden method.
     */
    void beforeRegisterAllocation();

    void emitIncomingValues(Value[] params);

    /**
     * Emits a return instruction. Implementations need to insert a move if the input is not in the
     * correct location.
     */
    void emitReturn(Value input);
}
