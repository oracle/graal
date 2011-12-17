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
package com.sun.max.asm.gen.cisc.ia32;

import java.util.*;

import com.sun.max.asm.*;
import com.sun.max.asm.dis.*;
import com.sun.max.asm.dis.ia32.*;
import com.sun.max.asm.gen.cisc.x86.*;
import com.sun.max.asm.ia32.*;
import com.sun.max.asm.ia32.complete.*;
import com.sun.max.io.*;
import com.sun.max.lang.*;

/**
 * Run this program to generate the IA32RawAssembler and IA32LabelAssembler classes.
 */
public class IA32AssemblerGenerator extends X86AssemblerGenerator<IA32Template> {

    public IA32AssemblerGenerator() {
        super(IA32Assembly.ASSEMBLY, WordWidth.BITS_32);
    }

    public static void main(String[] programArguments) {
        final IA32AssemblerGenerator generator = new IA32AssemblerGenerator();
        generator.options.parseArguments(programArguments);
        generator.generate();
    }

    @Override
    protected void printModVariants(IndentWriter stream, IA32Template template) {
        if (template.modCase() != X86TemplateContext.ModCase.MOD_0 || template.parameters().size() == 0) {
            return;
        }
        switch (template.rmCase()) {
            case NORMAL: {
                switch (template.addressSizeAttribute()) {
                    case BITS_16:
                        printModVariant(stream, template, IA32IndirectRegister16.BP_INDIRECT);
                        break;
                    default:
                        printModVariant(stream, template, IA32IndirectRegister32.EBP_INDIRECT);
                        break;
                }
                break;
            }
            case SIB: {
                switch (template.sibBaseCase()) {
                    case GENERAL_REGISTER:
                        printModVariant(stream, template, IA32BaseRegister32.EBP_BASE);
                        break;
                    default:
                        break;
                }
                break;
            }
            default: {
                break;
            }
        }
    }

    @Override
    protected void printSibVariants(IndentWriter stream, IA32Template template) {
        if (template.modCase() != null && template.modCase() != X86TemplateContext.ModCase.MOD_3 &&
                                          template.rmCase() == X86TemplateContext.RMCase.NORMAL &&
                                          template.addressSizeAttribute() == WordWidth.BITS_32 &&
                                          template.parameters().size() > 0) {
            printSibVariant(stream, template, IA32IndirectRegister32.ESP_INDIRECT);
        }
    }

    @Override
    protected DisassembledInstruction generateExampleInstruction(IA32Template template, List<Argument> arguments) throws AssemblyException {
        final IA32Assembler assembler = new IA32Assembler(0);
        assembly().assemble(assembler, template, arguments);
        final byte[] bytes = assembler.toByteArray();
        return new DisassembledInstruction(new IA32Disassembler(0, null), 0, bytes, template, arguments);
    }
}
