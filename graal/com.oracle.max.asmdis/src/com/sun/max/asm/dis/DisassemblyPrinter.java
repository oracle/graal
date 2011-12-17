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

import java.io.*;
import java.util.*;

import com.sun.max.*;
import com.sun.max.lang.*;

/**
 * Utility for printing a textual listing from a set of {@link DisassembledObject}s.
 */
public class DisassemblyPrinter {

    protected final boolean includeHeader;

    public DisassemblyPrinter(boolean includeHeader) {
        this.includeHeader = includeHeader;
    }

    public static final String SPACE = "   ";
    public static final int NUMBER_OF_INSTRUCTION_CHARS = 48;

    /**
     * Prints a disassembly for a given sequence of disassembled objects.
     *
     * @param outputStream the stream to which the disassembly wil be printed
     * @param disassembledObjects the disassembled objects to be printed
     */
    public void print(Disassembler disassembler, OutputStream outputStream, List<DisassembledObject> disassembledObjects) throws IOException {
        final PrintStream stream = outputStream instanceof PrintStream ? (PrintStream) outputStream : new PrintStream(outputStream);
        final int nOffsetChars = Integer.toString(Utils.last(disassembledObjects).startPosition()).length();
        final int nLabelChars = disassembler.addressMapper().maximumLabelNameLength();
        if (includeHeader) {
            printHeading(disassembler, stream, nOffsetChars, nLabelChars);
        }
        for (DisassembledObject disassembledObject : disassembledObjects) {
            printDisassembledObject(disassembler, stream, nOffsetChars, nLabelChars, disassembledObject);
        }
    }

    protected void printDisassembledObject(Disassembler disassembler, final PrintStream stream, final int nOffsetChars, final int nLabelChars, DisassembledObject disassembledObject) {
        stream.print(addressString(disassembler, disassembledObject));
        stream.print(SPACE);
        stream.printf("%0" + nOffsetChars + "d", disassembledObject.startPosition());
        stream.print(SPACE);
        final DisassembledLabel label = disassembler.addressMapper().labelAt(disassembledObject.startAddress());
        if (label != null) {
            stream.print(Strings.padLengthWithSpaces(label.name(), nLabelChars) + ":");
        } else {
            stream.print(Strings.spaces(nLabelChars) + " ");
        }
        stream.print(SPACE);
        stream.print(Strings.padLengthWithSpaces(disassembledObjectString(disassembler, disassembledObject), NUMBER_OF_INSTRUCTION_CHARS));
        stream.print(SPACE);
        stream.print(DisassembledInstruction.toHexString(disassembledObject.bytes()));
        stream.println();
    }

    protected void printHeading(Disassembler disassembler, PrintStream stream, int nOffsetChars, int nLabelChars)  {
        String s = Strings.padLengthWithSpaces("Address", (disassembler.addressWidth().numberOfBytes * 2) + 2) + SPACE;
        s += Strings.padLengthWithSpaces("+", nOffsetChars) + SPACE;
        s += Strings.padLengthWithSpaces(":", nLabelChars + 1) + SPACE;
        s += Strings.padLengthWithSpaces("Instruction", NUMBER_OF_INSTRUCTION_CHARS) + SPACE;
        s += "Bytes";
        stream.println(s);
        stream.println(Strings.times('-', s.length()));
    }

    protected String addressString(Disassembler disassembler, DisassembledObject disassembledObject) {
        final String format = "0x%0" + disassembler.addressWidth().numberOfBytes + "X";
        return String.format(format, disassembledObject.startAddress().asLong());
    }

    protected String disassembledObjectString(Disassembler disassembler, DisassembledObject disassembledObject) {
        return disassembledObject.toString(disassembler.addressMapper());
    }
}
