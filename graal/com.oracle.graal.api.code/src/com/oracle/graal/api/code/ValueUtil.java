/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.code;

import java.util.*;

import com.oracle.graal.api.meta.*;

/**
 * Utility class for working with the {@link Value} class and its subclasses.
 */
public final class ValueUtil {

    public static boolean isIllegal(Value value) {
        assert value != null;
        return value.equals(Value.ILLEGAL);
    }

    public static boolean isLegal(Value value) {
        return !isIllegal(value);
    }

    public static boolean isVirtualObject(Value value) {
        assert value != null;
        return value instanceof VirtualObject;
    }

    public static VirtualObject asVirtualObject(Value value) {
        assert value != null;
        return (VirtualObject) value;
    }

    public static boolean isConstant(Value value) {
        assert value != null;
        return value instanceof Constant;
    }

    public static Constant asConstant(Value value) {
        assert value != null;
        return (Constant) value;
    }

    public static boolean isAllocatableValue(Value value) {
        assert value != null;
        return value instanceof AllocatableValue;
    }

    public static AllocatableValue asAllocatableValue(Value value) {
        assert value != null;
        return (AllocatableValue) value;
    }

    public static boolean isStackSlot(Value value) {
        assert value != null;
        return value instanceof StackSlot;
    }

    public static StackSlot asStackSlot(Value value) {
        assert value != null;
        return (StackSlot) value;
    }

    public static boolean isRegister(Value value) {
        assert value != null;
        return value instanceof RegisterValue;
    }

    public static Register asRegister(Value value) {
        assert value != null;
        return ((RegisterValue) value).getRegister();
    }

    public static boolean isRawData(Value value) {
        assert value != null;
        return value instanceof RawDataValue;
    }

    public static RawDataValue asRawData(Value value) {
        assert value != null;
        return (RawDataValue) value;
    }

    public static Register asIntReg(Value value) {
        if (value.getKind() != Kind.Int) {
            throw new InternalError("needed Int got: " + value.getKind());
        } else {
            return asRegister(value);
        }
    }

    public static Register asLongReg(Value value) {
        if (value.getKind() != Kind.Long) {
            throw new InternalError("needed Long got: " + value.getKind());
        } else {
            return asRegister(value);
        }
    }

    public static Register asObjectReg(Value value) {
        assert value.getKind() == Kind.Object;
        return asRegister(value);
    }

    public static Register asFloatReg(Value value) {
        assert value.getKind() == Kind.Float;
        return asRegister(value);
    }

    public static Register asDoubleReg(Value value) {
        assert value.getKind() == Kind.Double;
        return asRegister(value);
    }

    public static boolean sameRegister(Value v1, Value v2) {
        return isRegister(v1) && isRegister(v2) && asRegister(v1).equals(asRegister(v2));
    }

    public static boolean sameRegister(Value v1, Value v2, Value v3) {
        return sameRegister(v1, v2) && sameRegister(v1, v3);
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
}
