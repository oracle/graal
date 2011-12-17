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
package com.sun.max.program.option;

import com.sun.max.*;

/**
 * The {@code Option} class represents a command-line or other configuration
 * option with a particular name, type, and description.
 */
public class Option<T> implements Cloneable {

    /**
     * The {@code Option.Type} class represents a type for an option. This class
     * implements method for parsing and unparsing values from strings.
     */
    public abstract static class Type<T> {
        protected final String typeName;
        public final Class<T> type;

        protected Type(Class<T> type, String typeName) {
            this.typeName = typeName;
            this.type = type;
        }

        public String getTypeName() {
            return typeName;
        }

        public String unparseValue(T value) {
            return String.valueOf(value);
        }

        public abstract T parseValue(String string) throws Option.Error;

        public abstract String getValueFormat();

        public Option<T> cast(Option option) {
            return Utils.cast(option);
        }
    }

    public static class Error extends java.lang.Error {

        public Error(String message) {
            super(message);
        }
    }

    protected final String name;
    protected T defaultValue;
    private boolean assigned;
    protected final Type<T> type;
    protected final String help;
    protected T value;

    /**
     * The constructor for the {@code Option} class creates constructs a new
     * option with the specified parameters.
     *
     * @param name     the name of the option as a string
     * @param defaultValue the default value of the option
     * @param type     the type of the option, which is used for parsing and unparsing values
     * @param help     a help description which is usually used to generate a formatted
     *                 help output
     */
    public Option(String name, T defaultValue, Type<T> type, String help) {
        this.defaultValue = defaultValue;
        this.name = name;
        this.type = type;
        this.help = help;
        value = null;
    }

    /**
     * The {@code getName()} method returns the name of this option as a string.
     *
     * @return the name of this option
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the default value for this option,
     * which is the value that the option retains if no assignment is made.
     *
     * @param val the default value of the option
     */
    public void setDefaultValue(T val) {
        defaultValue = val;
    }

    /**
     * The {@code getDefaultValue()} method returns the default value for this option,
     * which is the value that the option retains if no assignment is made.
     *
     * @return the default value of the option
     */
    public T getDefaultValue() {
        return defaultValue;
    }

    /**
     * The {@code getValue()} method retrieves the current value of this option.
     *
     * @return the current value of this option
     */
    public T getValue() {
        return !assigned ? defaultValue : value;
    }

    /**
     * Whether the option has been explicitly given a value.
     *
     * @return true if value explicitly assigned; false if relying on default value.
     */
    public boolean isAssigned() {
        return assigned;
    }

    /**
     * The {@code setValue()) method sets the value of this option.
     *
     * @param value the new value to this option
     */
    public void setValue(T value) {
        assigned = true;
        this.value = value;
    }

    /**
     * The {@code setValue()} method sets the value of this option, given a string value.
     * The type of this option is used to determine how to parse the string into a value
     * of the appropriate type. Thus this method may potentially throw runtime exceptions
     * if parsing fails.
     *
     * @param string the new value of this option as a string
     */
    public void setString(String string) {
        setValue(type.parseValue(string));
    }

    /**
     * The {@code getType()} method returns the type of this option.
     * @return the type of this option.
     */
    public Type<T> getType() {
        return type;
    }

    /**
     * The {@code getString()} method retrieves the value of this option as a string.
     * The type of this option is used to determine how to unparse the value into a string.
     *
     * @return the value of this option as a string
     */
    public String getString() {
        return type.unparseValue(getValue());
    }

    public String getHelp() {
        return help;
    }

    @Override
    public String toString() {
        return getName();
    }
}
