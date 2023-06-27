/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.profdiff.args;

import java.util.Optional;

/**
 * Represents a program argument with a value that may be a default value or parsed from the program
 * arguments. The purpose of this class is reduced code duplication while all argument types still
 * have the common ancestor {@link Argument}.
 *
 * @param <T> the type of the value
 */
abstract class ValuedArgument<T> extends Argument {
    /**
     * The value of the argument.
     */
    private T value;

    /**
     * Constructs a required argument with a value.
     *
     * @param name the name of the argument
     * @param help the help message
     */
    ValuedArgument(String name, String help) {
        super(name, true, help);
        value = null;
    }

    /**
     * Constructs an optional argument with a default value.
     *
     * @param name the name of the argument
     * @param defaultValue the default value
     * @param help the help message
     */
    ValuedArgument(String name, T defaultValue, String help) {
        super(name, false, help);
        set = true;
        value = defaultValue;
    }

    /**
     * Gets the value of the argument.
     */
    public T getValue() {
        return value;
    }

    @Override
    public int parse(String[] args, int offsetBase) throws InvalidArgumentException {
        int offset = offsetBase;
        if (isOptionArgument()) {
            if (args[offset].equals(getName())) {
                ++offset;
                if (offset >= args.length) {
                    throw new InvalidArgumentException(getName(), "No value was provided.");
                }
                value = parseValue(args[offset]);
            } else {
                assert args[offset].startsWith(getName() + EQUAL_SIGN);
                int equalSignIndex = args[offset].indexOf(EQUAL_SIGN);
                assert equalSignIndex != -1;
                value = parseValue(args[offset].substring(equalSignIndex + 1));
            }
        } else {
            value = parseValue(args[offset]);
        }
        set = true;
        return offset + 1;
    }

    /**
     * Parse the argument value from a string.
     *
     * @param s the value that is being parsed
     * @return the parsed value
     * @throws InvalidArgumentException the value could not be parsed
     */
    protected abstract T parseValue(String s) throws InvalidArgumentException;

    @Override
    public Optional<String> getDefaultValueRepresentation() {
        if (isRequired()) {
            return Optional.empty();
        } else {
            return Optional.of(String.valueOf(value));
        }
    }
}
