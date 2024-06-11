/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.igvutil.args;

import java.util.List;

/**
 * An argument that represents a value.
 *
 * @param <T> the type of the parsed value.
 */
public abstract class OptionValue<T> {
    /**
     * The parsed value of the argument.
     */
    protected T value = null;

    /**
     * Default value to return if no value was parsed.
     * Null if there is no default.
     */
    private final T defaultValue;

    private boolean set = false;

    private final String name;
    private final boolean required;
    private final String description;

    /**
     * Constructs a required value argument with no default.
     *
     * @param name the name of the argument
     * @param help the help message
     */
    public OptionValue(String name, String help) {
        this.name = name;
        this.description = help;
        this.required = true;
        this.defaultValue = null;
    }

    /**
     * Constructs an optional argument with a default value (which can be null).
     *
     * @param name the name of the argument
     * @param help the help message
     */
    public OptionValue(String name, T defaultValue, String help) {
        this.name = name;
        this.description = help;
        this.required = false;
        this.defaultValue = defaultValue;
    }

    /**
     * Gets the value of the argument.
     *
     * @return the parsed argument value, or a default if the argument wasn't parsed (yet).
     */
    public T getValue() {
        return value == null ? defaultValue : value;
    }

    public boolean isSet() {
        return set;
    }

    public boolean isRequired() {
        return required;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Parses the value from one or more arguments
     * and updates {@link #value} if parsing succeeded.
     *
     * @return the index of the next argument to consume.
     */
    abstract int parseValue(String[] args, int offset);

    public int parse(String[] args, int offsetBase) throws InvalidArgumentException {
        int index = parseValue(args, offsetBase);
        if (value == null) {
            throw new InvalidArgumentException(name, "couldn't parse value");
        }
        set = true;
        return index;
    }

    public OptionValue<List<T>> repeated() {
        if (defaultValue != null) {
            return new ListValue<>(name, List.of(defaultValue), description, this);
        }
        return new ListValue<>(name, description, this);
    }

    public String getUsage() {
        String usage = String.format("<%s>", name);
        if (defaultValue != null) {
            usage += String.format(" (default: %s)", defaultValue);
        }
        return usage;
    }
}
