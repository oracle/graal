/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.alloc;

import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.ir.*;
import com.sun.cri.ci.*;

/**
 * An ordered, 0-based indexable pool of instruction operands for a method being compiled.
 * The physical {@linkplain CiRegister registers} of the platform occupy the front of the
 * pool (starting at index 0) followed by {@linkplain CiVariable variable} operands.
 * The index of an operand in the pool is its {@linkplain #operandNumber(CiValue) operand number}.
 *
 * In the original HotSpot C1 source code, this pool corresponds to the
 * "flat register file" mentioned in c1_LinearScan.cpp.
 *
 * @author Doug Simon
 */
public final class OperandPool {

    public static final int INITIAL_VARIABLE_CAPACITY = 20;

    /**
     * The physical registers occupying the head of the operand pool. This is the complete
     * {@linkplain CiArchitecture#registers register set} of the target architecture, not
     * just the allocatable registers.
     */
    private final CiRegister[] registers;

    /**
     * The variable operands allocated from this pool. The {@linkplain #operandNumber(CiValue) number}
     * of the first variable operand in this pool is one greater than the number of the last
     * register operand in the pool.
     */
    private final ArrayList<CiVariable> variables;

    /**
     * Map from a {@linkplain CiVariable#index variable index} to the instruction whose result is stored in the denoted variable.
     * This map is only populated and used if {@link C1XOptions#DetailedAsserts} is {@code true}.
     */
    private final ArrayList<Value> variableDefs;

    /**
     * The {@linkplain #operandNumber(CiValue) number} of the first variable operand
     * {@linkplain #newVariable(CiKind) allocated} from this pool.
     */
    private final int firstVariableNumber;

    /**
     * Records which variable operands have the {@link VariableFlag#MustBeByteRegister} flag set.
     */
    private CiBitMap mustBeByteRegister;

    /**
     * Records which variable operands have the {@link VariableFlag#MustStartInMemory} flag set.
     */
    private CiBitMap mustStartInMemory;

    /**
     * Records which variable operands have the {@link VariableFlag#MustStayInMemory} flag set.
     */
    private CiBitMap mustStayInMemory;

    /**
     * Flags that can be set for {@linkplain CiValue#isVariable() variable} operands.
     */
    public enum VariableFlag {
        /**
         * Denotes a variable that needs to be assigned a memory location
         * at the beginning, but may then be loaded in a register.
         */
        MustStartInMemory,

        /**
         * Denotes a variable that needs to be assigned a memory location
         * at the beginning and never subsequently loaded in a register.
         */
        MustStayInMemory,

        /**
         * Denotes a variable that must be assigned to a byte-sized register.
         */
        MustBeByteRegister;

        public static final VariableFlag[] VALUES = values();
    }

    private static CiBitMap set(CiBitMap map, CiVariable variable) {
        if (map == null) {
            int length = CiBitMap.roundUpLength(variable.index + 1);
            map = new CiBitMap(length);
        } else if (map.size() <= variable.index) {
            int length = CiBitMap.roundUpLength(variable.index + 1);
            map.grow(length);
        }
        map.set(variable.index);
        return map;
    }

    private static boolean get(CiBitMap map, CiVariable variable) {
        if (map == null || map.size() <= variable.index) {
            return false;
        }
        return map.get(variable.index);
    }

    /**
     * Creates a new operand pool.
     *
     * @param target description of the target architecture for a compilation
     */
    public OperandPool(CiTarget target) {
        CiRegister[] registers = target.arch.registers;
        this.firstVariableNumber = registers.length;
        this.registers = registers;
        variables = new ArrayList<CiVariable>(INITIAL_VARIABLE_CAPACITY);
        variableDefs = C1XOptions.DetailedAsserts ? new ArrayList<Value>(INITIAL_VARIABLE_CAPACITY) : null;
    }

    /**
     * Creates a new {@linkplain CiVariable variable} operand.
     *
     * @param kind the kind of the variable
     * @return a new variable
     */
    public CiVariable newVariable(CiKind kind) {
        return newVariable(kind, kind == CiKind.Boolean || kind == CiKind.Byte ? VariableFlag.MustBeByteRegister : null);
    }

    /**
     * Creates a new {@linkplain CiVariable variable} operand.
     *
     * @param kind the kind of the variable
     * @param flag a flag that is set for the new variable operand (ignored if {@code null})
     * @return a new variable operand
     */
    public CiVariable newVariable(CiKind kind, VariableFlag flag) {
        assert kind != CiKind.Void;
        int varIndex = variables.size();
        CiVariable var = CiVariable.get(kind, varIndex);
        if (flag == VariableFlag.MustBeByteRegister) {
            mustBeByteRegister = set(mustBeByteRegister, var);
        } else if (flag == VariableFlag.MustStartInMemory) {
            mustStartInMemory = set(mustStartInMemory, var);
        } else if (flag == VariableFlag.MustStayInMemory) {
            mustStayInMemory = set(mustStayInMemory, var);
        } else {
            assert flag == null;
        }
        variables.add(var);
        return var;
    }

    /**
     * Gets the unique number for an operand contained in this pool.
     *
     *
     * @param operand an operand
     * @return the unique number for {@code operand} in the range {@code [0 .. size())}
     */
    public int operandNumber(CiValue operand) {
        if (operand.isRegister()) {
            int number = operand.asRegister().number;
            assert number < firstVariableNumber;
            return number;
        }
        assert operand.isVariable();
        return firstVariableNumber + ((CiVariable) operand).index;
    }

    /**
     * Gets the operand in this pool denoted by a given operand number.
     *
     * @param operandNumber a value that must be in the range {@code [0 .. size())}
     * @return the operand in this pool denoted by {@code operandNumber}
     */
    public CiValue operandFor(int operandNumber) {
        if (operandNumber < firstVariableNumber) {
            assert operandNumber >= 0;
            return registers[operandNumber].asValue();
        }
        int index = operandNumber - firstVariableNumber;
        CiVariable variable = variables.get(index);
        assert variable.index == index;
        return variable;
    }

    /**
     * Records that the result of {@code instruction} is stored in {@code result}.
     *
     * @param result the variable storing the result of {@code instruction}
     * @param instruction an instruction that produces a result (i.e. pushes a value to the stack)
     */
    public void recordResult(CiVariable result, Value instruction) {
        while (variableDefs.size() <= result.index) {
            variableDefs.add(null);
        }
        variableDefs.set(result.index, instruction);
    }

    /**
     * Gets the instruction whose result is recorded in a given variable.
     *
     * @param result the variable storing the result of an instruction
     * @return the instruction that stores its result in {@code result}
     */
    public Value instructionForResult(CiVariable result) {
        if (variableDefs.size() > result.index) {
            return variableDefs.get(result.index);
        }
        return null;
    }

    public boolean mustStartInMemory(CiVariable operand) {
        return get(mustStartInMemory, operand) || get(mustStayInMemory, operand);
    }

    public boolean mustStayInMemory(CiVariable operand) {
        return get(mustStayInMemory, operand);
    }

    public boolean mustBeByteRegister(CiValue operand) {
        return get(mustBeByteRegister, (CiVariable) operand);
    }

    public void setMustBeByteRegister(CiVariable operand) {
        mustBeByteRegister = set(mustBeByteRegister, operand);
    }

    /**
     * Gets the number of operands in this pool. This value will increase by 1 for
     * each new variable operand {@linkplain #newVariable(CiKind) allocated} from this pool.
     */
    public int size() {
        return firstVariableNumber + variables.size();
    }

    /**
     * Gets the highest operand number for a register operand in this pool. This value will
     * never change for the lifetime of this pool.
     */
    public int maxRegisterNumber() {
        return firstVariableNumber - 1;
    }
}
