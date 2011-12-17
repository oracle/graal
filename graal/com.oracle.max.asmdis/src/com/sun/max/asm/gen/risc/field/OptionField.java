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

import java.util.*;

import com.sun.max.asm.*;
import com.sun.max.asm.gen.risc.*;
import com.sun.max.asm.gen.risc.bitRange.*;
import com.sun.max.program.*;

/**
 * An OptionField is a field whose value is specified as an optional part of the assembler
 * mnemonic or assembler method name. The field has a default value if it is not specified.
 * An example of an optional field is the {@link com.sun.max.asm.gen.risc.sparc.SPARCFields#_p_option
 * predict bit} for the SPARC Branch on Equal with Prediction instruction:
 *
 *     bge        // predict that branch will be taken (default)
 *     bge,pt     // predict that branch will be taken
 *     bge,pn     // predict that branch will not be taken
 *
 * The definition of this field therefore has three {@link Option options}.
 */
public class OptionField extends RiscField {

    public OptionField(BitRange bitRange) {
        super(bitRange);
    }

    public static OptionField createAscending(int... bits) {
        final BitRange bitRange = BitRange.create(bits, BitRangeOrder.ASCENDING);
        return new OptionField(bitRange);
    }

    public static OptionField createDescending(int... bits) {
        final BitRange bitRange = BitRange.create(bits, BitRangeOrder.DESCENDING);
        return new OptionField(bitRange);
    }

    public RiscConstant constant(int value) {
        return new RiscConstant(this, value);
    }

    protected Option defaultOption;

    public Option defaultOption() {
        return defaultOption;
    }

    protected List<Option> options = new LinkedList<Option>();

    public Iterable<Option> options() {
        return options;
    }

    @Override
    public OptionField clone() {
        final OptionField result = (OptionField) super.clone();
        result.options = new LinkedList<Option>(options);
        return result;
    }

    /**
     * Creates a copy of this field that can take an additional value.
     *
     * @param name   addition to the assembler method's name used to specify the option value
     * @param value  the option value
     * @param externalName addition to the external assembler syntax used to specify the option value
     * @return the extended field
     */
    public OptionField withOption(String name, int value, String externalName) {
        final OptionField result = clone();
        final Option newOption = new Option(name, value, externalName, result);
        for (Option option : options) {
            if (option.equals(newOption)) {
                throw ProgramError.unexpected("duplicate option: " + option);
            }
        }
        result.options.add(newOption);

        if (name.equals("")) {
            result.defaultOption = newOption;
        }
        return result;
    }

    /**
     * Creates a copy of this field that can take an additional value.
     *
     * @param name   addition to the assembler method's name used to specify the option value
     * @param value  the option value
     * @return the extended field
     */
    public OptionField withOption(String name, int value) {
        return withOption(name, value, name);
    }

    /**
     * Creates a copy of this field that can take an additional value.
     *
     * @param value  the option value
     * @return the extended field
     */
    public OptionField withOption(int value) {
        return withOption("", value);
    }

    /**
     * Creates a copy of this field that can take an additional value.
     *
     * @param name       addition to the assembler method's name used to specify the option value
     * @param argument   the option value represented as a symbol
     * @return the extended field
     */
    public OptionField withOption(String name, SymbolicArgument argument) {
        if (argument instanceof ExternalMnemonicSuffixArgument) {
            return withOption(name, argument.value(), argument.externalValue());
        }
        return withOption(name, argument.value());
    }
}
