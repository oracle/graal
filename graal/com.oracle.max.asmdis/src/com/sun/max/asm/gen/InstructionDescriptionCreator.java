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
package com.sun.max.asm.gen;

import java.util.*;

/**
 * Wraps mere object arrays into instruction descriptions.
 */
public abstract class InstructionDescriptionCreator<InstructionDescription_Type extends InstructionDescription> {

    private final Assembly assembly;

    protected InstructionDescriptionCreator(Assembly assembly) {
        this.assembly = assembly;
    }

    public Assembly assembly() {
        return assembly;
    }

    protected abstract InstructionDescription_Type createInstructionDescription(List<Object> specifications);

    protected InstructionDescription_Type defineInstructionDescription(List<Object> specifications) {
        final InstructionDescription_Type instructionDescription = createInstructionDescription(specifications);
        instructionDescriptions.add(instructionDescription);
        instructionDescription.setArchitectureManualSection(currentArchitectureManualSection);
        return instructionDescription;
    }

    private final List<InstructionDescription_Type> instructionDescriptions = new LinkedList<InstructionDescription_Type>();

    private static void deepCopy(Object[] src, List<Object> dst) {
        for (Object object : src) {
            if (object instanceof Object[]) {
                deepCopy((Object[]) object, dst);
            } else {
                dst.add(object);
            }
        }
    }

    protected InstructionDescription_Type define(Object... specifications) {
        List<Object> specList = new ArrayList<Object>(specifications.length * 2);
        deepCopy(specifications, specList);
        return defineInstructionDescription(specList);
    }

    private String currentArchitectureManualSection;

    /**
     * Sets the name of the architecture manual section for which instruction descriptions are
     * currently being {@link #define defined}.
     */
    public void setCurrentArchitectureManualSection(String section) {
        currentArchitectureManualSection = section;
    }

    public List<InstructionDescription_Type> instructionDescriptions() {
        return instructionDescriptions;
    }
}
