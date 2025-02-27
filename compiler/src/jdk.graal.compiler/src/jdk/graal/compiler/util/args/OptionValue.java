/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.util.args;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

/**
 * Holds the value of a program option, parsed from command line arguments. Value parsing should be
 * implemented by subclasses by overriding the {@link #parseValue(String)} method.
 *
 * @param <T> the type of the parsed value.
 */
public abstract class OptionValue<T> {
    /**
     * The parsed value of the option. A null value indicates the option has not been parsed (yet).
     */
    protected T value = null;

    /**
     * Default value to return if no value was parsed. Can be null.
     */
    protected final T defaultValue;

    /**
     * Explanatory name for the option.
     */
    private final String name;

    /**
     * If true, parsing will throw an exception if this option is missing from the command-line
     * arguments.
     */
    private final boolean required;

    /**
     * Help text explaining what the option does.
     */
    private final String description;

    /**
     * Constructs a required option with no default.
     *
     * @param name the name of the argument.
     * @param help the help message.
     */
    public OptionValue(String name, String help) {
        this.name = name;
        this.description = help;
        this.required = true;
        this.defaultValue = null;
    }

    /**
     * Constructs a not required option with a default value.
     *
     * @param name the name of the argument.
     * @param defaultValue the option's default value. Can be null.
     * @param help the help message.
     */
    public OptionValue(String name, T defaultValue, String help) {
        this.name = name;
        this.description = help;
        this.required = false;
        this.defaultValue = defaultValue;
    }

    /**
     * Gets the value of the option.
     *
     * @return the parsed option value, or a default if the argument wasn't parsed (yet).
     */
    public T getValue() {
        return isSet() ? value : defaultValue;
    }

    /**
     * @return true iff the option was successfully parsed from the program arguments.
     */
    public boolean isSet() {
        return value != null;
    }

    /**
     * If true, parsing will throw an exception if this option is missing from the command-line
     * arguments.
     *
     * @return true iff the option was constructed with no default value.
     */
    public boolean isRequired() {
        return required;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void clear() {
        this.value = null;
    }

    /**
     * Parses the option value from the given argument, updating {@link #value accordingly}. For
     * options which accept more than one argument, this function will be called multiple times,
     * once for each argument.
     *
     * @param arg single argument from which the option is to be parsed. May be null in case a named
     *            option was provided as the last argument without a following value. This may be
     *            valid behavior for e.g. flag options.
     * @return true if the argument was consumed by the option, or false if the argument is to be
     *         parsed by a separate option.
     * @throws InvalidArgumentException if parsing of the argument failed.
     */
    public abstract boolean parseValue(String arg) throws InvalidArgumentException;

    /**
     * Converts an option that will parse a single argument into one that will parse successive
     * occurrences of the same option into a list. The underlying option should not be used
     * alongside the returned one.
     *
     * @see ListValue
     */
    public OptionValue<List<T>> repeated() {
        if (defaultValue != null) {
            return new ListValue<>(name, List.of(defaultValue), description, this);
        }
        return new ListValue<>(name, description, this);
    }

    public final String getUsage(boolean detailed) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        printUsage(pw, detailed);
        return sw.toString();
    }

    public void printUsage(PrintWriter writer, boolean detailed) {
        writer.append(String.format(isRequired() && !detailed ? "<%s>" : "[%s]", name));
        if (detailed && defaultValue != null) {
            writer.append(String.format(" (default: \"%s\")", defaultValue));
        }
    }

    static final String INDENT = "  ";

    private static void printIndentedLine(PrintWriter writer, String line, int indentLevel) {
        for (int i = 0; i < indentLevel; ++i) {
            writer.append(INDENT);
        }
        writer.println(line);
    }

    static void printIndented(PrintWriter writer, String string, int indentLevel) {
        string.lines().forEach(line -> printIndentedLine(writer, line, indentLevel));
    }

    public void printHelp(PrintWriter writer, int indentLevel) {
        printIndented(writer, getDescription(), indentLevel);
    }
}
