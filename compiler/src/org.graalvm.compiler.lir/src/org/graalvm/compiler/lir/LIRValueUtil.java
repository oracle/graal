/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.ArrayList;
import java.util.List;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

public final class LIRValueUtil {

    /**
     * Determine if the value, after removing an optional {@link CastValue cast}, is a variable.
     * This method should always be used instead of an {@code instanceof Variable} check.
     */
    public static boolean isVariable(Value value) {
        assert value != null;
        return stripCast(value) instanceof Variable;
    }

    /**
     * Return the value as a {@link Variable}, removing an optional {@link CastValue cast} from it.
     * {@link #isVariable(Value)} must have returned {@code true} on this value. This method should
     * always be used instead of a cast to {@link Variable}.
     */
    public static Variable asVariable(Value value) {
        assert value != null;
        return (Variable) stripCast(value);
    }

    public static boolean isConstantValue(Value value) {
        assert value != null;
        return value instanceof ConstantValue;
    }

    public static ConstantValue asConstantValue(Value value) {
        assert value != null;
        return (ConstantValue) value;
    }

    public static Constant asConstant(Value value) {
        return asConstantValue(value).getConstant();
    }

    public static boolean isJavaConstant(Value value) {
        return isConstantValue(value) && asConstantValue(value).isJavaConstant();
    }

    public static JavaConstant asJavaConstant(Value value) {
        return asConstantValue(value).getJavaConstant();
    }

    public static boolean isNullConstant(Value value) {
        assert value != null;
        return isJavaConstant(value) && asJavaConstant(value).isNull();
    }

    public static boolean isIntConstant(Value value, long expected) {
        if (isJavaConstant(value)) {
            JavaConstant javaConstant = asJavaConstant(value);
            if (javaConstant != null && javaConstant.getJavaKind().isNumericInteger()) {
                return javaConstant.asLong() == expected;
            }
        }
        return false;
    }

    public static boolean isStackSlotValue(Value value) {
        assert value != null;
        return value instanceof StackSlot || value instanceof VirtualStackSlot;
    }

    public static boolean isVirtualStackSlot(Value value) {
        assert value != null;
        return value instanceof VirtualStackSlot;
    }

    public static VirtualStackSlot asVirtualStackSlot(Value value) {
        assert value != null;
        return (VirtualStackSlot) value;
    }

    public static boolean sameRegister(Value v1, Value v2) {
        return isRegister(v1) && isRegister(v2) && asRegister(v1).equals(asRegister(v2));
    }

    public static boolean sameRegister(Value v1, Value v2, Value v3) {
        return sameRegister(v1, v2) && sameRegister(v1, v3);
    }

    public static boolean isCast(Value value) {
        assert value != null;
        return value instanceof CastValue;
    }

    public static Value stripCast(Value value) {
        return isCast(value) ? ((CastValue) value).underlyingValue() : value;
    }

    /**
     * Checks if all the provided values are different physical registers. The parameters can be
     * either {@link Register registers}, {@link Value values} or arrays of them. All values that
     * are not {@link RegisterValue registers} are ignored.
     */
    public static boolean differentRegisters(Object... values) {
        List<Register> registers = collectRegisters(values, new ArrayList<Register>());
        for (int i = 1; i < registers.size(); i++) {
            Register r1 = registers.get(i);
            for (int j = 0; j < i; j++) {
                Register r2 = registers.get(j);
                if (r1.equals(r2)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<Register> collectRegisters(Object[] values, List<Register> registers) {
        for (Object o : values) {
            if (o instanceof Register) {
                registers.add((Register) o);
            } else if (o instanceof Value) {
                if (isRegister((Value) o)) {
                    registers.add(asRegister((Value) o));
                }
            } else if (o instanceof Object[]) {
                collectRegisters((Object[]) o, registers);
            } else {
                throw new IllegalArgumentException("Not a Register or Value: " + o);
            }
        }
        return registers;
    }

    /**
     * Subtract sets of registers (x - y).
     *
     * @param x a set of register to subtract from.
     * @param y a set of registers to subtract.
     * @return resulting set of registers (x - y).
     */
    public static Value[] subtractRegisters(Value[] x, Value[] y) {
        ArrayList<Value> result = new ArrayList<>(x.length);
        for (Value i : x) {
            boolean append = true;
            for (Value j : y) {
                if (sameRegister(i, j)) {
                    append = false;
                    break;
                }
            }
            if (append) {
                result.add(i);
            }
        }
        Value[] resultArray = new Value[result.size()];
        return result.toArray(resultArray);
    }
}
