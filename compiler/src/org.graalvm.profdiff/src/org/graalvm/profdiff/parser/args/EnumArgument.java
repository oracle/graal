/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.parser.args;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A program argument that holds the value of an enum. The value is parsed as its case-insensitive
 * string representation.
 *
 * @param <T> the type of the enum
 */
public class EnumArgument<T extends Enum<T>> extends ValuedArgument<T> {

    private final Class<T> enumClass;

    /**
     * Constructs an enum argument.
     *
     * @param name the name of the argument
     * @param defaultValue the default value of the argument, must not be null
     * @param help the help message in the program usage string
     */
    EnumArgument(String name, T defaultValue, String help) {
        super(name, defaultValue, help);
        if (defaultValue == null) {
            throw new IllegalArgumentException("Default value must not be null");
        }
        enumClass = defaultValue.getDeclaringClass();
    }

    /**
     * Returns the list of all possible values for this argument in lowercase.
     */
    private List<String> getAllValues() {
        return EnumSet.allOf(enumClass).stream().map((key) -> key.name().toLowerCase()).collect(Collectors.toList());
    }

    /**
     * Parses the provided string case-insensitively as an identifier of the enum.
     *
     * @param s the value that is being parsed
     * @return the parsed enum constant
     * @throws InvalidArgumentException the provided string does not exactly match any identifier
     */
    @Override
    protected T parseValue(String s) throws InvalidArgumentException {
        for (T enumValue : enumClass.getEnumConstants()) {
            if (enumValue.name().compareToIgnoreCase(s) == 0) {
                return enumValue;
            }
        }
        throw new InvalidArgumentException(getName(), "\"" + s + "\" is an invalid value. Valid values are " + getAllValues());
    }

    /**
     * Returns a description of the argument by extending the user-provided description with the
     * list of accepted values.
     *
     * @return a description of the argument
     */
    @Override
    public String getDescription() {
        return super.getDescription() + ", accepted values are " + getAllValues();
    }
}
