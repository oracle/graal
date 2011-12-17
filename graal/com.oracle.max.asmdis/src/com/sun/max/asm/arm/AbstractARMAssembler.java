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

package com.sun.max.asm.arm;

import com.sun.max.asm.*;
import com.sun.max.lang.*;

/**
 */
public abstract class AbstractARMAssembler extends BigEndianAssembler implements Assembler32 {

    private int startAddress; // address of first instruction

    public AbstractARMAssembler(int startAddress) {
        this.startAddress = startAddress;
    }

    public AbstractARMAssembler() {
    }

    @Override
    protected void emitPadding(int numberOfBytes) throws AssemblyException {
        if ((numberOfBytes % 4) != 0) {
            throw new AssemblyException("Cannot pad instruction stream with a number of bytes not divisble by 4");
        }
        for (int i = 0; i < numberOfBytes / 4; i++) {
            emitInt(0);
        }
    }

    public int startAddress() {
        return startAddress;
    }

    public void setStartAddress(int address) {
        startAddress = address;
    }

    @Override
    public long baseAddress() {
        return startAddress;
    }

    public void fixLabel(Label label, int address) {
        fixLabel32(label, address);
    }

    public int address(Label label) throws AssemblyException {
        return address32(label);
    }

    @Override
    public final ISA isa() {
        return ISA.ARM;
    }

    @Override
    public WordWidth wordWidth() {
        return WordWidth.BITS_32;
    }
}
