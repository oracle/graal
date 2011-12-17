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

import com.sun.max.asm.*;
import com.sun.max.asm.gen.*;

/**
 * Extends the abstraction of an object in an assembled instruction stream with extra
 * properties that are only relevant for an object decoded from an instruction stream.
 */
public interface DisassembledObject extends AssemblyObject {

    /**
     * Gets a name for this object. If this is a disassembled instruction, it will be an assembler mnemonic. For
     * disassembled inline data, it will resemble an assembler directive.
     */
    String mnemonic();

    /**
     * Gets the address of the instruction or inline data addressed (either relatively or absolutely) by this object.
     *
     * @return null if this object does not address an instruction or inline data
     */
    ImmediateArgument targetAddress();

    /**
     * Gets the absolute address of this disassembled object's first byte.
     */
    ImmediateArgument startAddress();

    /**
     * Gets a string representation of this object. The recommended format is one resembling that of a native
     * disassembler for the platform corresponding to the ISA.
     *
     * @param addressMapper object used to map addresses to {@linkplain DisassembledLabel labels}. This value may be null.
     */
    String toString(AddressMapper addressMapper);

    /**
     * Gets the raw bytes of this object.
     */
    byte[] bytes();
}
