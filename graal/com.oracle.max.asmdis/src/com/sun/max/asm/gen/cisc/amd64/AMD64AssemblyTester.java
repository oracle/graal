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
package com.sun.max.asm.gen.cisc.amd64;

import java.util.*;

import com.sun.max.asm.*;
import com.sun.max.asm.amd64.*;
import com.sun.max.asm.amd64.complete.*;
import com.sun.max.asm.dis.amd64.*;
import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.cisc.x86.*;
import com.sun.max.asm.x86.*;
import com.sun.max.lang.*;

/**
 */
public class AMD64AssemblyTester extends X86AssemblyTester<AMD64Template> {

    public AMD64AssemblyTester(EnumSet<AssemblyTestComponent> components) {
        super(AMD64Assembly.ASSEMBLY, WordWidth.BITS_64, components);
    }

    @Override
    protected String assemblerCommand() {
        return System.getProperty("os.name").equals("Mac OS X") ? "as -arch x86_64" : "gas -64";
    }

    @Override
    protected Assembler createTestAssembler() {
        return new AMD64Assembler(0L);
    }

    @Override
    protected AMD64Disassembler createTestDisassembler() {
        return new AMD64Disassembler(0L, null);
    }

    @Override
    protected boolean isLegalArgumentList(AMD64Template template, List<Argument> arguments) {
        final WordWidth externalCodeSizeAttribute = template.externalCodeSizeAttribute();
        for (Argument argument : arguments) {
            if (argument instanceof AMD64GeneralRegister8) {
                final AMD64GeneralRegister8 generalRegister8 = (AMD64GeneralRegister8) argument;
                if (generalRegister8.isHighByte()) {
                    if (template.hasRexPrefix(arguments)) {
                        return false;
                    }
                } else if (generalRegister8.value() >= 4 && externalCodeSizeAttribute != null && externalCodeSizeAttribute.lessThan(WordWidth.BITS_64)) {
                    return false;
                }
            } else if (externalCodeSizeAttribute != null && externalCodeSizeAttribute.lessThan(WordWidth.BITS_64)) {
                // exclude cases that gas does not support (but that otherwise seem plausible):
                if (argument instanceof GeneralRegister) {
                    final GeneralRegister generalRegister = (GeneralRegister) argument;
                    if ((generalRegister.value() >= 8) || (generalRegister.width() == WordWidth.BITS_64)) {
                        return false;
                    }
                } else if (argument instanceof AMD64XMMRegister) {
                    final AMD64XMMRegister xmmRegister = (AMD64XMMRegister) argument;
                    if (xmmRegister.value() >= 8) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

}
