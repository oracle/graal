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
package com.sun.max.asm.gen.risc.ppc;

import java.util.*;

import com.sun.max.asm.gen.risc.*;
import com.sun.max.asm.gen.risc.bitRange.*;
import com.sun.max.lang.*;

/**
 */
public final class PPCAssembly extends RiscAssembly {

    private static final boolean GENERATING_DEPRECATED_INSTRUCTIONS = true;
    private static final boolean GENERATING_64BIT_INSTRUCTIONS = true;
    private static final boolean GENERATING_POWER5_INSTRUCTIONS = false;

    private PPCAssembly() {
        super(ISA.PPC, RiscTemplate.class);
    }

    public boolean generatingDeprecatedInstructions() {
        return GENERATING_DEPRECATED_INSTRUCTIONS;
    }

    public boolean generating64BitInstructions() {
        return GENERATING_64BIT_INSTRUCTIONS;
    }

    public boolean generatingPower5Instructions() {
        return GENERATING_POWER5_INSTRUCTIONS;
    }

    /**
     * The existence of this method documents a bug in the Apple version of GNU 'as' where bit 11
     * in an mtcrf instruction is encoded as 1 but specified as 0 in the architecture manual.
     *
     * This will have to be a non-constant method should another non-broken external assembler
     * be used for testing.
     */
    public boolean isExternalMTCRFEncodingBroken() {
        return true;
    }

    @Override
    public BitRangeOrder bitRangeEndianness() {
        return BitRangeOrder.ASCENDING;
    }

    @Override
    protected List<RiscTemplate> createTemplates() {
        final RiscTemplateCreator creator = new RiscTemplateCreator();
        creator.createTemplates(new RawInstructions(creator));
        creator.createTemplates(new SyntheticInstructions(creator));
        return creator.templates();
    }

    public static final PPCAssembly ASSEMBLY = new PPCAssembly();
}
