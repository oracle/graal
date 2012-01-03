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
package com.oracle.max.cri.ci;

public class CiValueUtil {
    public static boolean isIllegal(CiValue value) {
        assert value != null;
        return value == CiValue.IllegalValue;
    }

    public static boolean isLegal(CiValue value) {
        return !isIllegal(value);
    }

    public static boolean isVirtualObject(CiValue value) {
        assert value != null;
        return value instanceof CiVirtualObject;
    }


    public static boolean isConstant(CiValue value) {
        assert value != null;
        return value instanceof CiConstant;
    }


    public static boolean isStackSlot(CiValue value) {
        assert value != null;
        return value instanceof CiStackSlot;
    }

    public static CiStackSlot asStackSlot(CiValue value) {
        assert value != null;
        return (CiStackSlot) value;
    }


    public static boolean isRegister(CiValue value) {
        assert value != null;
        return value instanceof CiRegisterValue;
    }

    public static CiRegister asRegister(CiValue value) {
        assert value != null;
        return ((CiRegisterValue) value).reg;
    }

    public static CiRegister asIntReg(CiValue value) {
        assert value.kind == CiKind.Int || value.kind == CiKind.Jsr;
        return asRegister(value);
    }

    public static CiRegister asLongReg(CiValue value) {
        assert value.kind == CiKind.Long : value.kind;
        return asRegister(value);
    }

    public static CiRegister asObjectReg(CiValue value) {
        assert value.kind == CiKind.Object;
        return asRegister(value);
    }

    public static CiRegister asFloatReg(CiValue value) {
        assert value.kind == CiKind.Float;
        return asRegister(value);
    }

    public static CiRegister asDoubleReg(CiValue value) {
        assert value.kind == CiKind.Double;
        return asRegister(value);
    }
}
