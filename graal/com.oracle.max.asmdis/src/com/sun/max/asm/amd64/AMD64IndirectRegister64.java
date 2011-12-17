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
 * Aliases for 64-bit AMD64 general registers to be used for indirect addressing.
 */
public enum AMD64IndirectRegister64 implements GeneralRegister<AMD64IndirectRegister64>, IndirectRegister {

    RAX_INDIRECT,
    RCX_INDIRECT,
    RDX_INDIRECT,
    RBX_INDIRECT,
    RSP_INDIRECT,
    RBP_INDIRECT,
    RSI_INDIRECT,
    RDI_INDIRECT,
    R8_INDIRECT,
    R9_INDIRECT,
    R10_INDIRECT,
    R11_INDIRECT,
    R12_INDIRECT,
    R13_INDIRECT,
    R14_INDIRECT,
    R15_INDIRECT;

    public static final Enumerator<AMD64IndirectRegister64> ENUMERATOR = new Enumerator<AMD64IndirectRegister64>(AMD64IndirectRegister64.class);

    public static AMD64IndirectRegister64 from(GeneralRegister generalRegister) {
        return ENUMERATOR.get(generalRegister.id());
    }

    public int id() {
        return ordinal();
    }

    public WordWidth width() {
        return WordWidth.BITS_64;
    }

    public int value() {
        return id();
    }

    public long asLong() {
        return value();
    }

    public String externalValue() {
        return AMD64GeneralRegister64.from(this).externalValue();
    }

    public String disassembledValue() {
        return AMD64GeneralRegister64.from(this).disassembledValue();
    }

    public Enumerator<AMD64IndirectRegister64> enumerator() {
        return ENUMERATOR;
    }

    public AMD64IndirectRegister64 exampleValue() {
        return RBX_INDIRECT;
    }
}
