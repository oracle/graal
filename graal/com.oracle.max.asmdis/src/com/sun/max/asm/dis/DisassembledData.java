/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm.dis;

import com.sun.max.asm.gen.*;

/**
 * Boxes inline data decoded from an instruction stream.
 */
public abstract class DisassembledData implements DisassembledObject {

    private final ImmediateArgument startAddress;
    private final int startPosition;
    private final byte[] bytes;
    private final String mnemonic;
    private final ImmediateArgument targetAddress;

    /**
     * Creates an object encapsulating some inline data that starts at a given position.
     *
     * @param startAddress the absolute address at which the inline data starts
     * @param startPosition the instruction stream relative position at which the inline data starts
     * @param mnemonic an assembler directive like name for the data
     * @param bytes the raw bytes of the inline data
     */
    public DisassembledData(ImmediateArgument startAddress, int startPosition, String mnemonic, byte[] bytes, ImmediateArgument targetAddress) {
        this.startAddress = startAddress;
        this.startPosition = startPosition;
        this.mnemonic = mnemonic;
        this.bytes = bytes;
        this.targetAddress = targetAddress;
    }

    public ImmediateArgument startAddress() {
        return startAddress;
    }

    public ImmediateArgument endAddress() {
        return startAddress().plus(startPosition());
    }

    public int startPosition() {
        return startPosition;
    }

    public int endPosition() {
        return startPosition + bytes.length;
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    public boolean isCode() {
        return false;
    }

    public ImmediateArgument targetAddress() {
        return targetAddress;
    }

    public String mnemonic() {
        return mnemonic;
    }

    public abstract String operandsToString(AddressMapper addressMapper);

    public String toString(AddressMapper addressMapper) {
        return mnemonic() + " " + operandsToString(addressMapper);
    }

    @Override
    public abstract String toString();
}
