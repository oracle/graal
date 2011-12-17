/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm.amd64;

import com.sun.max.asm.x86.*;
import com.sun.max.lang.*;
import com.sun.max.util.*;

/**
 */
public enum AMD64GeneralRegister8 implements GeneralRegister<AMD64GeneralRegister8> {

    AL(0, false),
    CL(1, false),
    DL(2, false),
    BL(3, false),
    SPL(4, false),
    BPL(5, false),
    SIL(6, false),
    DIL(7, false),
    R8B(8, false),
    R9B(9, false),
    R10B(10, false),
    R11B(11, false),
    R12B(12, false),
    R13B(13, false),
    R14B(14, false),
    R15B(15, false),
    AH(4, true),
    CH(5, true),
    DH(6, true),
    BH(7, true);

    public static final Enumerator<AMD64GeneralRegister8> ENUMERATOR = new Enumerator<AMD64GeneralRegister8>(AMD64GeneralRegister8.class);

    private final int value;
    private final boolean isHighByte;

    private AMD64GeneralRegister8(int value, boolean isHighByte) {
        this.value = value;
        this.isHighByte = isHighByte;
    }

    public static AMD64GeneralRegister8 lowFrom(GeneralRegister generalRegister) {
        return ENUMERATOR.get(generalRegister.id());
    }

    public static AMD64GeneralRegister8 highFrom(GeneralRegister generalRegister) {
        return ENUMERATOR.get(generalRegister.id() + 16);
    }

    public static AMD64GeneralRegister8 fromValue(int value, boolean isRexBytePresent) {
        if (!isRexBytePresent && value >= AH.value) {
            return ENUMERATOR.get((value - AH.value) + AH.ordinal());
        }
        return ENUMERATOR.fromValue(value);
    }

    public boolean isHighByte() {
        return isHighByte;
    }

    public boolean requiresRexPrefix() {
        return value >= 4 && !isHighByte;
    }

    public WordWidth width() {
        return WordWidth.BITS_8;
    }

    public int id() {
        return ordinal() % 16;
    }

    public int value() {
        return value;
    }

    public long asLong() {
        return value();
    }

    public String externalValue() {
        return "%" + name().toLowerCase();
    }

    public String disassembledValue() {
        return name().toLowerCase();
    }

    public Enumerator<AMD64GeneralRegister8> enumerator() {
        return ENUMERATOR;
    }

    public AMD64GeneralRegister8 exampleValue() {
        return AL;
    }
}
