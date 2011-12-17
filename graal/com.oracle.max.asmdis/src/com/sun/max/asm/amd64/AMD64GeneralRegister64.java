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
public enum AMD64GeneralRegister64 implements GeneralRegister<AMD64GeneralRegister64> {

    // Note: keep the order such that 'value()' can rely on ordinals:

    RAX, RCX, RDX, RBX, RSP, RBP, RSI, RDI, R8, R9, R10, R11, R12, R13, R14, R15;

    public static final Enumerator<AMD64GeneralRegister64> ENUMERATOR = new Enumerator<AMD64GeneralRegister64>(AMD64GeneralRegister64.class);

    public static AMD64GeneralRegister64 from(GeneralRegister generalRegister) {
        return ENUMERATOR.get(generalRegister.id());
    }

    public AMD64IndirectRegister64 indirect() {
        return AMD64IndirectRegister64.from(this);
    }

    public AMD64BaseRegister64 base() {
        return AMD64BaseRegister64.from(this);
    }

    public AMD64IndexRegister64 index() {
        return AMD64IndexRegister64.from(this);
    }

    public WordWidth width() {
        return WordWidth.BITS_64;
    }

    public int value() {
        return ordinal();
    }

    public int id() {
        return ordinal();
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

    public Enumerator<AMD64GeneralRegister64> enumerator() {
        return ENUMERATOR;
    }

    public AMD64GeneralRegister64 exampleValue() {
        return RAX;
    }
}
