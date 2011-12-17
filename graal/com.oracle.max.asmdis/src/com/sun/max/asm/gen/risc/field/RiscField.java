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
package com.sun.max.asm.gen.risc.field;

import com.sun.max.asm.gen.risc.bitRange.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;

/**
 * A field describes a bit range and how it relates to an operand.
 */
public abstract class RiscField implements Cloneable, StaticFieldName, StaticFieldLiteral {

    private final BitRange bitRange;

    protected RiscField(BitRange bitRange) {
        this.bitRange = bitRange;
    }

    @Override
    public RiscField clone() {
        try {
            return (RiscField) super.clone();
        } catch (CloneNotSupportedException cloneNotSupportedException) {
            throw ProgramError.unexpected("Field.clone() not supported");
        }
    }

    private String name;

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private String literal;

    public String literal() {
        return literal;
    }

    public void setLiteral(String literal) {
        this.literal = literal;
    }

    private Class literalClass;

    public Class literalClass() {
        return literalClass;
    }

    public void setLiteralClass(Class literalClass) {
        this.literalClass = literalClass;
    }

    public BitRange bitRange() {
        return bitRange;
    }

    /**
     * Two RISC fields are considered equal if they define the same set of bits in an instruction
     * (i.e. their bit ranges are equal).
     */
    @Override
    public boolean equals(Object other) {
        if (other instanceof RiscField) {
            final RiscField riscField = (RiscField) other;
            return bitRange().equals(riscField.bitRange());
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = bitRange.hashCode();
        if (name != null) {
            result ^= name.hashCode();
        }
        return result;
    }

    @Override
    public String toString() {
        return name();
    }

}
