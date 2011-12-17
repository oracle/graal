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

import java.util.*;

import com.sun.max.*;

/**
 * This class provides a mechanism for making modifications to a set of RISC instruction descriptions.
 */
public class RiscInstructionDescriptionModifier {

    private final List<RiscInstructionDescription> instructionDescriptions;

    public RiscInstructionDescriptionModifier(List<RiscInstructionDescription> instructionDescriptions) {
        this.instructionDescriptions = instructionDescriptions;
    }

    /**
     * Replaces a specification in the set of instruction descriptions.
     *
     * @param before  the specification to be replaced (matched with {@link Object#equals})
     * @param after   the replacement value
     */
    public RiscInstructionDescriptionModifier replace(Object before, Object after) {
        for (RiscInstructionDescription instructionDescription : instructionDescriptions) {
            final List<Object> specifications = instructionDescription.specifications();
            for (int i = 0; i < specifications.size(); i++) {
                if (specifications.get(i).equals(before)) {
                    specifications.set(i, after);
                }
            }
        }
        return this;
    }

    public RiscInstructionDescriptionModifier swap(Object a, Object b) {
        for (RiscInstructionDescription instructionDescription : instructionDescriptions) {
            final List<Object> specifications = instructionDescription.specifications();
            final int aIndex = Utils.indexOfIdentical(specifications, a);
            final int bIndex = Utils.indexOfIdentical(specifications, b);
            if (aIndex != -1 && bIndex != -1) {
                specifications.set(aIndex, b);
                specifications.set(bIndex, a);
            }
        }
        return this;
    }

    public RiscInstructionDescriptionModifier setExternalName(String externalName) {
        for (RiscInstructionDescription instructionDescription : instructionDescriptions) {
            instructionDescription.setExternalName(externalName);
        }
        return this;
    }
}
