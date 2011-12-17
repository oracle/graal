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
package com.sun.max.asm.gen.risc;

import java.io.*;
import java.util.*;

import com.sun.max.asm.dis.risc.*;
import com.sun.max.asm.gen.*;
import com.sun.max.collect.*;
import com.sun.max.lang.*;

/**
 */
public abstract class RiscAssembly extends Assembly<RiscTemplate> {

    protected RiscAssembly(ISA isa, Class<RiscTemplate> templateType) {
        super(isa, templateType);
    }

    private List<SpecificityGroup> specificityGroups;

    private void initialize() {
        final IntHashMap<IntHashMap<OpcodeMaskGroup>> specificityTable = new IntHashMap<IntHashMap<OpcodeMaskGroup>>();
        for (RiscTemplate template : templates()) {
            if (!template.isRedundant()) {
                IntHashMap<OpcodeMaskGroup> opcodeMaskGroups = specificityTable.get(template.specificity());
                if (opcodeMaskGroups == null) {
                    opcodeMaskGroups = new IntHashMap<OpcodeMaskGroup>();
                    specificityTable.put(template.specificity(), opcodeMaskGroups);
                }
                final int opcodeMask = template.opcodeMask();
                OpcodeMaskGroup opcodeMaskGroup = opcodeMaskGroups.get(opcodeMask);
                if (opcodeMaskGroup == null) {
                    opcodeMaskGroup = new OpcodeMaskGroup(opcodeMask);
                    opcodeMaskGroups.put(opcodeMask, opcodeMaskGroup);
                }
                opcodeMaskGroup.add(template);
            }
        }
        specificityGroups = new LinkedList<SpecificityGroup>();
        for (int specificity = 33; specificity >= 0; specificity--) {
            final IntHashMap<OpcodeMaskGroup> opcodeGroupTable = specificityTable.get(specificity);
            if (opcodeGroupTable != null) {
                final List<OpcodeMaskGroup> opcodeMaskGroups = opcodeGroupTable.toList();
                final SpecificityGroup specificityGroup = new SpecificityGroup(specificity, opcodeMaskGroups);
                specificityGroups.add(specificityGroup);
            }
        }
    }

    public void printSpecificityGroups(PrintStream out) {
        for (SpecificityGroup specificityGroup : specificityGroups) {
            out.println("Specificity group " + specificityGroup.specificity());
            for (OpcodeMaskGroup opcodeMaskGroup : specificityGroup.opcodeMaskGroups()) {
                out.println("  Opcode mask group " + Integer.toBinaryString(opcodeMaskGroup.mask()));
                for (RiscTemplate template : opcodeMaskGroup.templates()) {
                    out.println("    " + template);
                }
            }
        }
    }

    public List<SpecificityGroup> specificityGroups() {
        if (specificityGroups == null) {
            initialize();
        }
        return specificityGroups;
    }

}
