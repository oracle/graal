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
package com.sun.max.asm.gen.cisc.x86;

import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.risc.bitRange.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;

/**
 */
public abstract class X86Assembly<Template_Type extends X86Template> extends Assembly<Template_Type> {

    public X86Assembly(ISA isa, Class<Template_Type> templateType) {
        super(isa, templateType);
    }

    @Override
    public BitRangeOrder bitRangeEndianness() {
        return BitRangeOrder.DESCENDING;
    }

    /**
     * Whether to support 16 bit addressing.
     */
    private static boolean are16BitAddressesSupported;

    public static boolean are16BitAddressesSupported() {
        return are16BitAddressesSupported;
    }

    public static void support16BitAddresses() {
        are16BitAddressesSupported = true;
    }

    /**
     * Whether to support 16 bit addressing.
     */
    private static boolean are16BitOffsetsSupported;

    public static boolean are16BitOffsetsSupported() {
        return are16BitOffsetsSupported;
    }

    public static void support16BitOffsets() {
        are16BitOffsetsSupported = true;
    }

    private static <Template_Type extends X86Template> boolean parametersMatching(Template_Type original, Template_Type candidate, Class argumentType) {
        int i = 0;
        int j = 0;
        while (i < original.parameters().size()) {
            final Class originalType = original.parameters().get(i).type();
            Class candidateType = candidate.parameters().get(j).type();
            if (originalType == argumentType) {
                if (candidateType != byte.class) {
                    return false;
                }
                j++;
                candidateType = candidate.parameters().get(j).type();
            }
            if (originalType != candidateType) {
                return false;
            }
            i++;
            j++;
        }
        return true;
    }

    public static <Template_Type extends X86Template> Template_Type getModVariantTemplate(Iterable<Template_Type> templates, Template_Type original, Class argumentType) {
        for (Template_Type candidate : templates) {
            if (candidate.opcode1() == original.opcode1() && candidate.opcode2() == original.opcode2() &&
                    candidate.instructionSelectionPrefix() == original.instructionSelectionPrefix() &&
                    candidate.modRMGroupOpcode() == original.modRMGroupOpcode() &&
                    candidate.addressSizeAttribute() == original.addressSizeAttribute() &&
                    candidate.operandSizeAttribute() == original.operandSizeAttribute() &&
                    parametersMatching(original, candidate, argumentType)) {
                return candidate;
            }
        }
        throw ProgramError.unexpected("could not find mod variant for: " + original);
    }

}
