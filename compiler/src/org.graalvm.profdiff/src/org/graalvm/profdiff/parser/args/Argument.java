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

/**
 * Represents a program argument that can parse its value from a string.
 */
public abstract class Argument {
    /**
     * The prefix of an option argument. Each argument may be an option argument or a positional
     * argument. Option arguments are explicitly named in the program arguments. Positional
     * arguments are parsed implicitly by their relative position in the program arguments.
     */
    public static final String OPTION_PREFIX = "--";

    /**
     * The value of a valued option argument may be specified as the successive program argument
     * (e.g. --arg value) or with an equal sign (e.g. --arg=value).
     */
    public static final char EQUAL_SIGN = '=';

    /**
     * The name of this argument.
     */
    private final String name;

    /**
     * Is the argument required?
     */
    private final boolean required;

    /**
     * The help message to be displayed in the program usage string.
     */
    private final String help;

    /**
     * Was the argument's value already set to the default value or parsed from the program
     * arguments?
     */
    protected boolean set = false;

    /**
     * Constructs an argument.
     *
     * @param name the name of the argument
     * @param required is the argument required
     * @param help the help message
     */
    Argument(String name, boolean required, String help) {
        this.name = name;
        this.required = required;
        this.help = help;
    }

    /**
     * Gets whether this argument is required.
     *
     * @return true iff this argument is required
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * Gets whether the argument was set. The argument is set when it is constructed with a default
     * value or its value is parsed from the program arguments.
     *
     * @return true iff the argument was set
     */
    public boolean isSet() {
        return set;
    }

    /**
     * Gets the help message.
     *
     * @return the help message
     */
    public String getHelp() {
        return help;
    }

    /**
     * Gets the argument name.
     *
     * @return the argument name
     */
    public String getName() {
        return name;
    }

    /**
     * Parse the argument's value from a given offset in the program arguments and return the next
     * offset.
     *
     * @param args the program arguments
     * @param offset the index in the program arguments where this argument begins
     * @return next value of the offset where the next argument is expected to begin
     * @throws InvalidArgumentException there was no value provided for this argument
     */
    abstract int parse(String[] args, int offset) throws InvalidArgumentException;

    /**
     * Finds out whether this argument is an option argument by looking at its prefix.
     *
     * @return true iff this argument is an option argument
     */
    public boolean isOptionArgument() {
        return name.startsWith(OPTION_PREFIX);
    }
}
