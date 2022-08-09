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
package org.graalvm.bisect.parser.args;

import java.util.EnumSet;

/**
 * A program argument that holds the value of an enum. The value is parsed as its string
 * representation.
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
        this.enumClass = defaultValue.getDeclaringClass();
    }

    /**
     * Returns the set of possible values for this argument.
     */
    private EnumSet<T> getAllValues() {
        return EnumSet.allOf(enumClass);
    }

    /**
     * Parses the provided string as an identifier of the enum.
     *
     * @param s the value that is being parsed
     * @return the parsed enum constant
     * @throws InvalidArgumentException the provided string does not exactly match any identifier
     */
    @Override
    protected T parseValue(String s) throws InvalidArgumentException {
        try {
            return Enum.valueOf(enumClass, s);
        } catch (IllegalArgumentException e) {
            throw new InvalidArgumentException(getName(), "\"" + s + "\" is an invalid value. Valid values are " + getAllValues());
        }
    }

    /**
     * Returns a help message by extending the user-provided help string with the list of accepted
     * values.
     *
     * @return a help message
     */
    @Override
    public String getHelp() {
        return super.getHelp() + ", accepted values " + getAllValues();
    }
}
