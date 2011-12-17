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

import java.io.*;
import java.util.*;

import com.sun.max.asm.*;
import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.risc.*;
import com.sun.max.io.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;

/**
 */
public abstract class SPARCAssemblyTester extends RiscAssemblyTester<RiscTemplate> {

    public SPARCAssemblyTester(SPARCAssembly assembly, WordWidth addressWidth, EnumSet<AssemblyTestComponent> components) {
        super(assembly, addressWidth, components);
    }

    @Override
    public SPARCAssembly assembly() {
        return (SPARCAssembly) super.assembly();
    }

    @Override
    protected String assemblerCommand() {
        return "as -xarch=v9a";
    }

    private RiscTemplate lastTemplate;

    @Override
    protected void assembleExternally(IndentWriter writer, RiscTemplate template, List<Argument> argumentList, String label) {

        // This is a workaround for SPARC V9 ABI compliance checks: http://developers.sun.com/solaris/articles/sparcv9abi.html
        if (lastTemplate == null || template != lastTemplate) {
            writer.println(".register %g2,#scratch");
            writer.println(".register %g3,#scratch");
            writer.println(".register %g6,#scratch");
            writer.println(".register %g7,#scratch");
            lastTemplate = template;
        }
        final RiscExternalInstruction instruction = new RiscExternalInstruction(template, argumentList);
        writer.println(instruction.toString());
        writer.println("nop"); // fill potential DCTI slot with something - see below
    }

    @Override
    protected boolean readNop(InputStream stream) throws IOException {
        final int instruction = Endianness.BIG.readInt(stream);
        return instruction == 0x01000000;
    }

    @Override
    protected byte[] readExternalInstruction(PushbackInputStream externalInputStream, RiscTemplate template, byte[] internalBytes) throws IOException {
        final byte[] result = super.readExternalInstruction(externalInputStream, template, internalBytes);
        if (!readNop(externalInputStream)) { // read potential DCTI slot place holder contents - see above
            throw ProgramError.unexpected("nop missing after external instruction");
        }
        return result;
    }
}
