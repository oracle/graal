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

import com.sun.max.asm.gen.risc.field.*;

/**
 * An Option is one of the values that an {@link OptionField optional field} can take.
 * An example is the value of the <i>predict bit</i> for the SPARC Branch on Equal with
 * Prediction instruction that specifies the bit is set. The format of this instruction is:
 *
 *    bge_pt(...)  // assembler method
 *    bge,pt ...   // external assembler syntax
 */
public class Option {

    /**
     * The addition to the assembler method's name used to specify this option value.
     */
    protected final String name;

    /**
     * The addition to the external assembler syntax used to specify this option value.
     */
    protected final String externalName;

    /**
     * The value of the option.
     */
    protected final int value;

    /**
     * The field to which this option applies.
     */
    private final OptionField field;

    public String name() {
        return name;
    }

    public String externalName() {
        return externalName;
    }

    public int value() {
        return value;
    }

    public Option() {
        this("");
    }

    public Option(String name) {
        this(name, 0);
    }

    public Option(String name, int value) {
        this(name, value, "");
    }

    public Option(String name, int value, String externalName) {
        this(name, value, externalName, null);
    }

    public Option(String name, int value, String externalName, OptionField field) {
        this.name = name;
        this.value = value;
        this.externalName = externalName;
        this.field = field;
    }

    public OptionField field() {
        return field;
    }

    public boolean isRedundant() {
        return (field.defaultOption() != null) && (value == field.defaultOption().value) && (!(equals(field.defaultOption())));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Option && name.equals(((Option) other).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
