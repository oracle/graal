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
package com.sun.max.asm.dis.ia32;

import java.util.*;

import com.sun.max.asm.*;
import com.sun.max.asm.dis.x86.*;
import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.cisc.ia32.*;
import com.sun.max.asm.gen.cisc.x86.*;
import com.sun.max.asm.ia32.complete.*;
import com.sun.max.lang.*;

/**
 * Instantiate this class to disassemble IA32 instruction streams.
 */
public class IA32Disassembler extends X86Disassembler {

    public IA32Disassembler(int startAddress, InlineDataDecoder inlineDataDecoder) {
        super(new Immediate32Argument(startAddress), IA32Assembly.ASSEMBLY, inlineDataDecoder);
    }

    @Override
    protected boolean isRexPrefix(HexByte opcode) {
        return false;
    }

    @Override
    protected Assembler createAssembler(int position) {
        return new IA32Assembler((int) startAddress().asLong() + position);
    }

    private static Map<X86InstructionHeader, List<X86Template>> headerToTemplates = X86InstructionHeader.createMapping(IA32Assembly.ASSEMBLY, WordWidth.BITS_32);

    @Override
    protected Map<X86InstructionHeader, List<X86Template>> headerToTemplates() {
        return headerToTemplates;
    }
}
