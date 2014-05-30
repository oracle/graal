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

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.compiler.common.spi.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.gen.LIRGenerator.*;

public interface LIRGeneratorTool extends ArithmeticLIRGenerator {

    CodeGenProviders getProviders();

    TargetDescription target();

    MetaAccessProvider getMetaAccess();

    CodeCacheProvider getCodeCache();

    ForeignCallsProvider getForeignCalls();

    AbstractBlock<?> getCurrentBlock();

    LIRGenerationResult getResult();

    boolean hasBlockEnd(AbstractBlock<?> block);

    void doBlockStart(AbstractBlock<?> block);

    void doBlockEnd(AbstractBlock<?> block);

    Map<Constant, LoadConstant> getConstantLoads();

    void setConstantLoads(Map<Constant, LoadConstant> constantLoads);

    Value emitLoad(PlatformKind kind, Value address, LIRFrameState state);

    void emitStore(PlatformKind kind, Value address, Value input, LIRFrameState state);

    void emitNullCheck(Value address, LIRFrameState state);

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

    void emitDeoptimize(Value actionAndReason, Value failedSpeculation, LIRFrameState state);

    Variable emitForeignCall(ForeignCallLinkage linkage, LIRFrameState state, Value... args);

    /**
     * Checks whether the supplied constant can be used without loading it into a register for most
     * operations, i.e., for commonly used arithmetic, logical, and comparison operations.
     *
     * @param c The constant to check.
     * @return True if the constant can be used directly, false if the constant needs to be in a
     *         register.
     */
    boolean canInlineConstant(Constant c);

    /**
     * Checks whether the supplied constant can be used without loading it into a register for store
     * operations, i.e., on the right hand side of a memory access.
     *
     * @param c The constant to check.
     * @return True if the constant can be used directly, false if the constant needs to be in a
     *         register.
     */
    boolean canStoreConstant(Constant c);

    RegisterAttributes attributes(Register register);

    Variable newVariable(PlatformKind kind);

    Variable emitMove(Value input);

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

    AllocatableValue asAllocatable(Value value);

    Variable load(Value value);

    Value loadNonConst(Value value);

    /**
     * Returns true if the redundant move elimination optimization should be done after register
     * allocation.
     */
    boolean canEliminateRedundantMoves();

    /**
     * Determines if only oop maps are required for the code generated from the LIR.
     */
    boolean needOnlyOopMaps();

    /**
     * Gets the ABI specific operand used to return a value of a given kind from a method.
     *
     * @param kind the kind of value being returned
     * @return the operand representing the ABI defined location used return a value of kind
     *         {@code kind}
     */
    AllocatableValue resultOperandFor(Kind kind);

    void append(LIRInstruction op);

    void emitJump(LabelRef label);

    void emitCompareBranch(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination,
                    double trueDestinationProbability);

    void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, double overflowProbability);

    void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueSuccessorProbability);

    Variable emitConditionalMove(PlatformKind cmpKind, Value leftVal, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue);

    Variable emitIntegerTestMove(Value leftVal, Value right, Value trueValue, Value falseValue);

    void emitStrategySwitch(Constant[] keyConstants, double[] keyProbabilities, LabelRef[] keyTargets, LabelRef defaultTarget, Variable value);

    void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget);

    CallingConvention getCallingConvention();

    void emitBitCount(Variable result, Value operand);

    void emitBitScanForward(Variable result, Value operand);

    void emitBitScanReverse(Variable result, Value operand);

    void emitByteSwap(Variable result, Value operand);

    void emitArrayEquals(Kind kind, Variable result, Value array1, Value array2, Value length);

}
