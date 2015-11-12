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
package com.oracle.graal.lir.gen;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterAttributes;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

import com.oracle.graal.compiler.common.calc.Condition;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.compiler.common.spi.CodeGenProviders;
import com.oracle.graal.compiler.common.spi.ForeignCallLinkage;
import com.oracle.graal.compiler.common.spi.ForeignCallsProvider;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LabelRef;
import com.oracle.graal.lir.SwitchStrategy;
import com.oracle.graal.lir.Variable;

public interface LIRGeneratorTool extends BenchmarkCounterFactory {

    /**
     * Factory for creating moves.
     */
    public interface MoveFactory {

        /**
         * Checks whether the supplied constant can be used without loading it into a register for
         * most operations, i.e., for commonly used arithmetic, logical, and comparison operations.
         *
         * @param c The constant to check.
         * @return True if the constant can be used directly, false if the constant needs to be in a
         *         register.
         */
        boolean canInlineConstant(JavaConstant c);

        LIRInstruction createMove(AllocatableValue result, Value input);

        LIRInstruction createStackMove(AllocatableValue result, AllocatableValue input);

        LIRInstruction createLoad(AllocatableValue result, Constant input);
    }

    public abstract class BlockScope implements AutoCloseable {

        public abstract AbstractBlockBase<?> getCurrentBlock();

        public abstract void close();

    }

    ArithmeticLIRGeneratorTool getArithmetic();

    CodeGenProviders getProviders();

    TargetDescription target();

    MetaAccessProvider getMetaAccess();

    CodeCacheProvider getCodeCache();

    ForeignCallsProvider getForeignCalls();

    AbstractBlockBase<?> getCurrentBlock();

    LIRGenerationResult getResult();

    boolean hasBlockEnd(AbstractBlockBase<?> block);

    MoveFactory getMoveFactory();

    /**
     * Get a special {@link MoveFactory} for spill moves.
     *
     * The instructions returned by this factory must only depend on the input values. References to
     * values that require interaction with register allocation are strictly forbidden.
     */
    MoveFactory getSpillMoveFactory();

    BlockScope getBlockScope(AbstractBlockBase<?> block);

    Value emitConstant(LIRKind kind, Constant constant);

    Value emitJavaConstant(JavaConstant constant);

    /**
     * Some backends need to convert sub-word kinds to a larger kind in
     * {@link ArithmeticLIRGeneratorTool#emitLoad} and {@link #emitLoadConstant} because sub-word
     * registers can't be accessed. This method converts the {@link LIRKind} of a memory location or
     * constant to the {@link LIRKind} that will be used when it is loaded into a register.
     */
    LIRKind toRegisterKind(LIRKind kind);

    AllocatableValue emitLoadConstant(LIRKind kind, Constant constant);

    void emitNullCheck(Value address, LIRFrameState state);

    Variable emitCompareAndSwap(Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue);

    /**
     * Emit an atomic read-and-add instruction.
     *
     * @param address address of the value to be read and written
     * @param delta the value to be added
     */
    default Value emitAtomicReadAndAdd(Value address, Value delta) {
        throw JVMCIError.unimplemented();
    }

    /**
     * Emit an atomic read-and-write instruction.
     *
     * @param address address of the value to be read and written
     * @param newValue the new value to be written
     */
    default Value emitAtomicReadAndWrite(Value address, Value newValue) {
        throw JVMCIError.unimplemented();
    }

    void emitDeoptimize(Value actionAndReason, Value failedSpeculation, LIRFrameState state);

    Variable emitForeignCall(ForeignCallLinkage linkage, LIRFrameState state, Value... args);

    RegisterAttributes attributes(Register register);

    /**
     * Create a new {@link Variable}.
     *
     * @param kind The type of the value that will be stored in this {@link Variable}. See
     *            {@link LIRKind} for documentation on what to pass here. Note that in most cases,
     *            simply passing {@link Value#getLIRKind()} is wrong.
     * @return A new {@link Variable}.
     */
    Variable newVariable(LIRKind kind);

    Variable emitMove(Value input);

    void emitMove(AllocatableValue dst, Value src);

    void emitMoveConstant(AllocatableValue dst, Constant src);

    /**
     * Emits an op that loads the address of some raw data.
     *
     * @param dst the variable into which the address is loaded
     * @param data the data to be installed with the generated code
     */
    void emitData(AllocatableValue dst, byte[] data);

    Variable emitAddress(AllocatableValue stackslot);

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
    void emitReturn(JavaKind javaKind, Value input);

    AllocatableValue asAllocatable(Value value);

    Variable load(Value value);

    Value loadNonConst(Value value);

    /**
     * Determines if only oop maps are required for the code generated from the LIR.
     */
    boolean needOnlyOopMaps();

    /**
     * Gets the ABI specific operand used to return a value of a given kind from a method.
     *
     * @param javaKind the {@link JavaKind} of value being returned
     * @param lirKind the backend type of the value being returned
     * @return the operand representing the ABI defined location used return a value of kind
     *         {@code kind}
     */
    AllocatableValue resultOperandFor(JavaKind javaKind, LIRKind lirKind);

    <I extends LIRInstruction> I append(I op);

    void setInfo(BytecodePosition position);

    void emitJump(LabelRef label);

    void emitCompareBranch(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination,
                    double trueDestinationProbability);

    void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, LIRKind cmpKind, double overflowProbability);

    void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueSuccessorProbability);

    Variable emitConditionalMove(PlatformKind cmpKind, Value leftVal, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue);

    Variable emitIntegerTestMove(Value leftVal, Value right, Value trueValue, Value falseValue);

    void emitStrategySwitch(JavaConstant[] keyConstants, double[] keyProbabilities, LabelRef[] keyTargets, LabelRef defaultTarget, Variable value);

    void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget);

    CallingConvention getCallingConvention();

    Variable emitByteSwap(Value operand);

    Variable emitArrayEquals(JavaKind kind, Value array1, Value array2, Value length);

    void emitBlackhole(Value operand);

    LIRKind getLIRKind(Stamp stamp);
}
