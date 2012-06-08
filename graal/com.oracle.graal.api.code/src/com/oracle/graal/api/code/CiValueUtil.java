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

import com.oracle.graal.api.meta.*;

public class CiValueUtil {
    public static boolean isIllegal(RiValue value) {
        assert value != null;
        return value == RiValue.IllegalValue;
    }

    public static boolean isLegal(RiValue value) {
        return !isIllegal(value);
    }

    public static boolean isVirtualObject(RiValue value) {
        assert value != null;
        return value instanceof CiVirtualObject;
    }

    public static CiVirtualObject asVirtualObject(RiValue value) {
        assert value != null;
        return (CiVirtualObject) value;
    }

    public static boolean isConstant(RiValue value) {
        assert value != null;
        return value instanceof Constant;
    }

    public static Constant asConstant(RiValue value) {
        assert value != null;
        return (Constant) value;
    }


    public static boolean isStackSlot(RiValue value) {
        assert value != null;
        return value instanceof CiStackSlot;
    }

    public static CiStackSlot asStackSlot(RiValue value) {
        assert value != null;
        return (CiStackSlot) value;
    }

    public static boolean isAddress(RiValue value) {
        assert value != null;
        return value instanceof CiAddress;
    }

    public static CiAddress asAddress(RiValue value) {
        assert value != null;
        return (CiAddress) value;
    }


    public static boolean isRegister(RiValue value) {
        assert value != null;
        return value instanceof CiRegisterValue;
    }

    public static CiRegister asRegister(RiValue value) {
        assert value != null;
        return ((CiRegisterValue) value).reg;
    }

    public static CiRegister asIntReg(RiValue value) {
        assert value.kind == RiKind.Int || value.kind == RiKind.Jsr;
        return asRegister(value);
    }

    public static CiRegister asLongReg(RiValue value) {
        assert value.kind == RiKind.Long : value.kind;
        return asRegister(value);
    }

    public static CiRegister asObjectReg(RiValue value) {
        assert value.kind == RiKind.Object;
        return asRegister(value);
    }

    public static CiRegister asFloatReg(RiValue value) {
        assert value.kind == RiKind.Float;
        return asRegister(value);
    }

    public static CiRegister asDoubleReg(RiValue value) {
        assert value.kind == RiKind.Double;
        return asRegister(value);
    }


    public static boolean sameRegister(RiValue v1, RiValue v2) {
        return isRegister(v1) && isRegister(v2) && asRegister(v1) == asRegister(v2);
    }

    public static boolean sameRegister(RiValue v1, RiValue v2, RiValue v3) {
        return sameRegister(v1, v2) && sameRegister(v1, v3);
    }

    public static boolean differentRegisters(RiValue v1, RiValue v2) {
        return !isRegister(v1) || !isRegister(v2) || asRegister(v1) != asRegister(v2);
    }

    public static boolean differentRegisters(RiValue v1, RiValue v2, RiValue v3) {
        return differentRegisters(v1, v2) && differentRegisters(v1, v3) && differentRegisters(v2, v3);
    }
}
