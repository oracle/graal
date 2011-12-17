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
package com.sun.max.asm.dis;

import java.util.*;

import com.sun.max.asm.*;
import com.sun.max.asm.gen.*;

/**
 * A assembly instruction in internal format, combined with the bytes that it was disassembled from.
 */
public class DisassembledInstruction implements DisassembledObject {

    private final Disassembler disassembler;
    private final int startPosition;
    private final byte[] bytes;
    private final Template template;
    private final List<Argument> arguments;

    public DisassembledInstruction(Disassembler disassembler, int position, byte[] bytes, Template template, List<Argument> arguments) {
        assert bytes.length != 0;
        this.disassembler = disassembler;
        this.startPosition = position;
        this.bytes = bytes;
        this.template = template;
        this.arguments = arguments;
    }

    public ImmediateArgument startAddress() {
        return disassembler.startAddress().plus(startPosition);
    }

    /**
     * Gets the address to which an offset argument of this instruction is relative.
     */
    public ImmediateArgument addressForRelativeAddressing() {
        return disassembler.addressForRelativeAddressing(this);
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

    public Template template() {
        return template;
    }

    public boolean isCode() {
        return true;
    }

    public List<Argument> arguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return toString(disassembler.addressMapper());
    }

    public String mnemonic() {
        return disassembler.mnemonic(this);
    }

    public String toString(AddressMapper addressMapper) {
        return disassembler.toString(this, addressMapper);
    }

    public String operandsToString(AddressMapper addressMapper) {
        return disassembler.operandsToString(this, addressMapper);
    }

    public ImmediateArgument targetAddress() {
        final int parameterIndex = template().labelParameterIndex();
        if (parameterIndex >= 0) {
            final ImmediateArgument immediateArgument = (ImmediateArgument) arguments().get(parameterIndex);
            final Parameter parameter = template().parameters().get(parameterIndex);
            if (parameter instanceof OffsetParameter) {
                return addressForRelativeAddressing().plus(immediateArgument);
            }
            return immediateArgument;
        }
        return null;
    }

    public static String toHexString(byte[] bytes) {
        String result = "[";
        String separator = "";
        for (byte b : bytes) {
            result += separator + String.format("%02X", b);
            separator = " ";
        }
        result += "]";
        return result;
    }
}
