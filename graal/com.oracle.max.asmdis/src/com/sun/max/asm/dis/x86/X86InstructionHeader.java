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
package com.sun.max.asm.dis.x86;

import java.util.*;

import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.cisc.x86.*;
import com.sun.max.lang.*;

/**
 * Info about the first few bytes of an x86 instruction,
 * narrowing the set of possible instructions to probe by the disassembler.
 */
public class X86InstructionHeader {

    public static final X86InstructionHeader INVALID = new X86InstructionHeader();

    protected boolean hasAddressSizePrefix;
    protected HexByte rexPrefix;
    protected HexByte instructionSelectionPrefix;
    protected HexByte opcode1;
    protected HexByte opcode2;

    X86InstructionHeader() {
    }

    private X86InstructionHeader(WordWidth addressWidth, X86Template template) {
        hasAddressSizePrefix = template.addressSizeAttribute() != addressWidth;
        instructionSelectionPrefix = template.instructionSelectionPrefix();
        opcode1 = template.opcode1();
        opcode2 = template.opcode2();
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof X86InstructionHeader) {
            final X86InstructionHeader header = (X86InstructionHeader) other;
            return hasAddressSizePrefix == header.hasAddressSizePrefix &&
                instructionSelectionPrefix == header.instructionSelectionPrefix && opcode1 == header.opcode1 && opcode2 == header.opcode2;
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("Instruction header: rexPrefix=%s, instructionSelectionPrefix=%s, opcode1=%s, opcode2=%s, hasAddressSizePrefix=%b", rexPrefix, instructionSelectionPrefix, opcode1, opcode2, hasAddressSizePrefix);
    }

    @Override
    public int hashCode() {
        int result = hasAddressSizePrefix ? -1 : 1;
        if (instructionSelectionPrefix != null) {
            result *= instructionSelectionPrefix.ordinal();
        }
        if (opcode1 != null) {
            result *= opcode1.ordinal();
        }
        if (opcode2 != null) {
            result ^= opcode2.ordinal();
        }
        if (instructionSelectionPrefix != null) {
            result += instructionSelectionPrefix.ordinal() * 1024;
        }
        if (opcode2 != null) {
            result += opcode2.ordinal() * 256;
        }
        if (opcode1 != null) {
            result += opcode1.ordinal();
        }
        return result;
    }

    public static Map<X86InstructionHeader, List<X86Template>> createMapping(Assembly<? extends X86Template> assembly, WordWidth addressWidth) {
        final Map<X86InstructionHeader, List<X86Template>> result = new HashMap<X86InstructionHeader, List<X86Template>>();
        for (X86Template template : assembly.templates()) {
            X86InstructionHeader header = new X86InstructionHeader(addressWidth, template);
            List<X86Template> matchingTemplates = result.get(header);
            if (matchingTemplates == null) {
                matchingTemplates = new LinkedList<X86Template>();
                result.put(header, matchingTemplates);
            }
            matchingTemplates.add(template);
            for (X86Parameter parameter : template.parameters()) {
                switch (parameter.place()) {
                    case OPCODE1_REXB:
                    case OPCODE1:
                        for (int i = 0; i < 8; i++) {
                            header = new X86InstructionHeader(addressWidth, template);
                            header.opcode1 = HexByte.VALUES.get(header.opcode1.ordinal() + i);
                            result.put(header, matchingTemplates);
                        }
                        break;
                    case OPCODE2_REXB:
                    case OPCODE2:
                        for (int i = 0; i < 8; i++) {
                            header = new X86InstructionHeader(addressWidth, template);
                            header.opcode2 = HexByte.VALUES.get(header.opcode2.ordinal() + i);
                            result.put(header, matchingTemplates);
                        }
                        break;
                    default:
                        break;
                }
            }
        }
        return result;
    }

}
