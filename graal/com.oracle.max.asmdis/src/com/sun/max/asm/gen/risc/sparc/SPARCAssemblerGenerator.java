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
package com.sun.max.asm.gen.risc.sparc;

import java.util.*;

import com.sun.max.asm.*;
import com.sun.max.asm.dis.*;
import com.sun.max.asm.dis.sparc.*;
import com.sun.max.asm.gen.risc.*;
import com.sun.max.asm.sparc.complete.*;

/**
 */
public final class SPARCAssemblerGenerator extends RiscAssemblerGenerator<RiscTemplate> {

    private SPARCAssemblerGenerator() {
        super(SPARCAssembly.ASSEMBLY);
    }

    @Override
    protected String getJavadocManualReference(RiscTemplate template) {
        return "\"<a href=\"http://developers.sun.com/solaris/articles/sparcv9.pdf\">The SPARC Architecture Manual, Version 9</a> - Section " +
            template.instructionDescription().architectureManualSection() + "\"";
    }

    public static void main(String[] programArguments) {
        final SPARCAssemblerGenerator generator = new SPARCAssemblerGenerator();
        generator.options.parseArguments(programArguments);
        generator.generate();
    }

    @Override
    protected DisassembledInstruction generateExampleInstruction(RiscTemplate template, List<Argument> arguments) throws AssemblyException {
        final SPARCAssembler assembler = new SPARC64Assembler(0);
        assembly().assemble(assembler, template, arguments);
        final byte[] bytes = assembler.toByteArray();
        return new DisassembledInstruction(new SPARC64Disassembler(0, null), 0, bytes, template, arguments);
    }
}
