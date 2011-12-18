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

import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.sun.max.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;

/**
 * The {@code OptionSet} class parses and collects options from the command line and
 * configuration files.
 */
public class OptionSet {
    /**
     * The {@code Syntax} enum allows different options to be parsed differently,
     * depending on their usage.
     */
    public enum Syntax {
        REQUIRES_EQUALS {
            @Override
            public String getUsage(Option option) {
                return "-" + option.getName() + "=" + option.getType().getValueFormat();
            }
        },
        EQUALS_OR_BLANK {
            @Override
            public String getUsage(Option option) {
                return "-" + option.getName() + "[=" + option.getType().getValueFormat() + "]";
            }
        },
        REQUIRES_BLANK {
            @Override
            public String getUsage(Option option) {
                return "-" + option.getName();
            }
        },
        CONSUMES_NEXT {
            @Override
            public String getUsage(Option option) {
                return "-" + option.getName() + " " + option.getType().getValueFormat();
            }
        };

        public abstract String getUsage(Option option);
    }

    protected final Map<String, Option> optionMap;
    protected final Map<String, Syntax> optionSyntax;
    protected final Map<String, String> optionValues;
    protected final boolean allowUnrecognizedOptions;

    protected static final String[] NO_ARGUMENTS = {};

    protected String[] arguments = NO_ARGUMENTS;

    /**
     * Creates an option set that does not allow unrecognized options to be present when
     * {@linkplain #parseArguments(String[]) parsing command line arguments} or
     * {@linkplain #loadOptions(OptionSet) loading options from another option set}.
     */
    public OptionSet() {
        this(false);
    }

    /**
     * Creates an option set.
     *
     * @param allowUnrecognizedOptions
     *            specifies if this option set allows unrecognized options to be present when
     *            {@linkplain #parseArguments(String[]) parsing command line arguments} or
     *            {@linkplain #loadOptions(OptionSet) loading options from another option set}.
     */
    public OptionSet(boolean allowUnrecognizedOptions) {
        optionValues = new HashMap<>();
        // Using a LinkedHashMap to preserve insertion order when iterating over values
        optionMap = new LinkedHashMap<>();
        optionSyntax = new HashMap<>();
        this.allowUnrecognizedOptions = allowUnrecognizedOptions;
    }

    /**
     * Converts this option set into a list of command line arguments, to be used, for example, to pass to an external
     * tool. For each option in this set that has been explicitly set this method will prepend an appropriate option
     * string of appropriate syntactic form (e.g. "-name=value") to the array of arguments passed.
     *
     * @return a new array of program arguments that includes these options
     */
    public String[] asArguments() {
        String[] newArgs = Arrays.copyOf(arguments, arguments.length + optionValues.size());
        int i = 0;
        for (String name : optionValues.keySet()) {
            final String value = optionValues.get(name);
            final Syntax syntax = optionSyntax.get(name);
            if (syntax == Syntax.REQUIRES_BLANK) {
                newArgs[i++] = "-" + name;
            } else if (syntax == Syntax.CONSUMES_NEXT) {
                newArgs = Arrays.copyOf(newArgs, newArgs.length + 1);
                newArgs[i++] = "-" + name;
                newArgs[i++] = value;
            } else {
                newArgs[i++] = "-" + name + "=" + value;
            }
        }
        return newArgs;
    }

    /**
     * Gets an option set derived from this option set that contains all the unrecognized options that have been loaded
     * or parsed into this option set. The returned option set also includes a copy of the
     * {@linkplain #getArguments() non-option arguments} from this option set.
     * @return a new option set encapsulating all the arguments and options
     */
    public OptionSet getArgumentsAndUnrecognizedOptions() {
        final OptionSet argumentsAndUnrecognizedOptions = new OptionSet(true);
        for (Map.Entry<String, String> entry : optionValues.entrySet()) {
            if (!optionMap.containsKey(entry.getKey())) {
                argumentsAndUnrecognizedOptions.optionValues.put(entry.getKey(), entry.getValue());
            }
        }
        argumentsAndUnrecognizedOptions.arguments = arguments;
        return argumentsAndUnrecognizedOptions;
    }

    /**
     * Handles an Option.Error raised while loading or parsing values into this option set.
     * <p>
     * This default implementation is to print a usage message and the call {@link System#exit(int)}.
     * @param error the error that occurred
     * @param optionName the name of the option being parsed
     */
    protected void handleErrorDuringParseOrLoad(Option.Error error, String optionName) {
        System.out.println("Error parsing option -" + optionName + ": " + error.getMessage());
        printHelp(System.out, 78);
        System.exit(1);
    }

    /**
     * Parses a list of command line arguments, processing the leading options (i.e. arguments that start with '-')
     * and returning the "leftover" arguments to the caller. The longest tail of {@code arguments} that starts with a non-option argument can be retrieved after parsing with {@link #getArguments()}.
     *
     * @param args
     *            the arguments
     * @return this option set
     */
    public OptionSet parseArguments(String[] args) {
        // parse the options
        int i = 0;
        for (; i < args.length; i++) {
            final String argument = args[i];
            if (argument.charAt(0) == '-') {
                // is the beginning of a valid option.
                final int index = argument.indexOf('=');
                final String optionName = getOptionName(argument, index);
                String value = getOptionValue(argument, index);
                final Syntax syntax = optionSyntax.get(optionName);
                // check the syntax of this option
                try {
                    checkSyntax(optionName, syntax, value);
                    if (syntax == Syntax.CONSUMES_NEXT) {
                        value = args[++i];
                    }
                    setValue(optionName, value);
                } catch (Option.Error error) {
                    handleErrorDuringParseOrLoad(error, optionName);
                }
            } else {
                // is not an option, therefore the start of arguments
                break;
            }
        }

        final int left = args.length - i;
        arguments = new String[left];
        System.arraycopy(args, i, arguments, 0, left);
        return this;
    }

    /**
     * The {@code getArguments()} method gets the leftover command line options
     * from the last call to {@code parseArguments}.
     *
     * @return the leftover command line options
     */
    public String[] getArguments() {
        if (arguments.length == 0) {
            return arguments;
        }
        return Arrays.copyOf(arguments, arguments.length);
    }

    /**
     * Determines if this option set allows parsing or loading of unrecognized options.
     * @return {@code true} if this option set allows unrecognized options
     */
    public boolean allowsUnrecognizedOptions() {
        return allowUnrecognizedOptions;
    }

    /**
     * The {@code loadSystemProperties()} method loads the value of the valid
     * options from the systems properties with the specified prefix.
     *
     * @param prefix the prefix of each system property, used to disambiguate
     *               these options from other system properties.
     * @return this option set
     */
    public OptionSet loadSystemProperties(String prefix) {
        final Properties systemProperties = System.getProperties();
        final Properties properties = new Properties();
        for (String key : systemProperties.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                properties.setProperty(key.substring(prefix.length()), systemProperties.getProperty(key));
            }
        }
        return loadProperties(properties, true);
    }

    /**
     * The {@code storeSystemProperties()} method stores these option values
     * into the system properties.
     *
     * @param prefix the prefix to append to all option names when inserting them
     * into the systems properties
     */
    public void storeSystemProperties(String prefix) {
        for (Map.Entry<String, String> entry : optionValues.entrySet()) {
            System.setProperty(prefix + entry.getKey(), entry.getValue());
        }
    }

    /**
     * Loads the specified properties into this set of options.
     *
     * @param p
     *            the properties set to load into this set of options
     * @param loadall
     *            true if this method should load all properties in the property set into this option set; false if this
     *            method should only load properties for options already in this option set
     * @return this option set
     */
    public OptionSet loadProperties(Properties p, boolean loadall) {
        if (loadall) {
            // if loadall is specified, load all properties in the set
            for (Object object : p.keySet()) {
                final String name = (String) object;
                final String val = p.getProperty(name);
                try {
                    setValue(name, val);
                } catch (Option.Error error) {
                    handleErrorDuringParseOrLoad(error, name);
                }
            }
        } else {
            // if loadall is not specified, only load options that are in this option set.
            for (Object o : p.keySet()) {
                final String name = (String) o;
                if (optionMap.containsKey(name)) {
                    final String val = p.getProperty(name);
                    try {
                        setValue(name, val);
                    } catch (Option.Error error) {
                        handleErrorDuringParseOrLoad(error, name);
                    }
                }
            }
        }
        return this;
    }

    /**
     * The {@code loadFile()} method parses properties from a file and loads them into this set of options.
     *
     * @param fname
     *            the filename from while to load the properties
     * @param loadall
     *            true if this method should load all properties in the property set into this option set; false if this
     *            method should only load properties for options already in this option set
     * @return this option set
     * @throws java.io.IOException
     *             if there is a problem opening or reading the file
     * @throws Option.Error
     *             if there is a problem parsing an option
     */
    public OptionSet loadFile(String fname, boolean loadall) throws IOException, Option.Error {
        final Properties defs = new Properties();
        final FileInputStream stream = new FileInputStream(new File(fname));
        defs.load(stream);
        stream.close();
        return loadProperties(defs, loadall);
    }

    /**
     * Loads a set of options and {@linkplain #getArguments() arguments} from another option set.
     *
     * @param options the option set from which to load the option values
     * @return this option set
     */
    public OptionSet loadOptions(OptionSet options) {
        for (Map.Entry<String, String> entry : options.optionValues.entrySet()) {
            try {
                setValue(entry.getKey(), entry.getValue());
            } catch (Option.Error error) {
                handleErrorDuringParseOrLoad(error, entry.getKey());
            }
        }
        arguments = options.arguments;
        return this;
    }

    protected void checkSyntax(String optname, Syntax syntax, String value) {
        if (syntax == Syntax.REQUIRES_BLANK && value != null) {
            throw new Option.Error("syntax error: \"-" + optname + "\" required");
        }
        if (syntax == Syntax.REQUIRES_EQUALS && value == null) {
            throw new Option.Error("syntax error: \"-" + optname + "=value\" required");
        }
        if (syntax == Syntax.CONSUMES_NEXT && value != null) {
            throw new Option.Error("syntax error: \"-" + optname + " value\" required");
        }
    }

    protected String getOptionName(String argument, int equalIndex) {
        if (equalIndex < 0) { // naked option
            return argument.substring(1, argument.length());
        }
        return argument.substring(1, equalIndex);
    }

    protected String getOptionValue(String argument, int equalIndex) {
        if (equalIndex < 0) { // naked option
            return null;
        }
        return argument.substring(equalIndex + 1);
    }

    /**
     * Adds the options of an {@link OptionSet} to this set.
     * @param optionSet the set of options to add
     */
    public void addOptions(OptionSet optionSet) {
        for (Option<?> option : optionSet.getOptions()) {
            final Syntax syntax = optionSet.optionSyntax.get(option.getName());
            addOption(option, syntax);
        }
    }

    /**
     * The {@code addOption()} method adds an option with the {@link Syntax#REQUIRES_EQUALS} syntax to this option set.
     *
     * @param option the new option to add to this set
     * @return the option passed as the argument, after it has been added to this option set
     */
    public <T> Option<T> addOption(Option<T> option) {
        return addOption(option, Syntax.REQUIRES_EQUALS);
    }

    /**
     * The {@code addOption()} method adds an option to this option set.
     *
     * @param option the new option to add to this set
     * @param syntax the syntax of the option, which specifies how to parse the option
     *               from command line parameters
     * @return the option passed as the argument, after it has been added to this option set
     */
    public <T> Option<T> addOption(Option<T> option, Syntax syntax) {
        final String name = option.getName();
        final Option existingOption = optionMap.put(name, option);
        if (existingOption != null) {
            throw ProgramError.unexpected("Cannot register more than one option under the same name: " + option.getName());
        }
        optionSyntax.put(name, syntax);
        return option;
    }

    /**
     * The {@code setSyntax()} method sets the syntax of a particular option.
     *
     * @param option the option for which to change the syntax
     * @param syntax the new syntax for the instruction
     */
    public void setSyntax(Option option, Syntax syntax) {
        optionSyntax.put(option.getName(), syntax);
    }

    /**
     * The {@code setValue()} method sets the value of the specified option in
     * this option set. If there is no option by the specified name, the name/value
     * pair will simply be remembered.
     *
     * @param name the name of the option
     * @param value  the new value of the option as a string
     * @throws Option.Error if {@code name} denotes an unrecognized option and this
     */
    public void setValue(String name, String value) {
        final String v = value == null ? "" : value;
        final Option opt = optionMap.get(name);
        if (opt != null) {
            opt.setString(v);
        } else {
            if (!allowUnrecognizedOptions) {
                throw new Option.Error("unrecognized option -" + name);
            }
        }
        optionValues.put(name, v);
    }

    public void setValuesAgain() {
        for (String name : optionValues.keySet()) {
            final Option opt = optionMap.get(name);
            opt.setString(optionValues.get(name));
        }
    }

    public String getStringValue(String name) {
        return optionValues.get(name);
    }

    /**
     * The {@code hasOptionSpecified()} method checks whether an option with the specified
     * name has been assigned to. An option as been "assigned to" if its value has been set
     * by either parsing arguments (the {@code parseArguments() method} or loading properties
     * from a file or the system properties.
     *
     * @param name the name of the option to query
     * @return true if an option with the specified name has been set; false otherwise
     */
    public boolean hasOptionSpecified(String name) {
        return optionValues.containsKey(name);
    }

    /**
     * Retrieves the options from this option
     * set, in the order in which they were added.
     * @return an iterable collection of {@code Option} instances, sorted according to insertion order
     */
    public Iterable<Option<?>> getOptions() {
        return Utils.cast(optionMap.values());
    }

    /**
     * The {@code getSortedOptions()} method retrieves the options from this option
     * set, sorting all options by their names.
     * @return an iterable collection of {@code Option} instances, sorted according to the name of each option
     */
    public Iterable<Option<?>> getSortedOptions() {
        final List<Option<?>> list = new LinkedList<>();
        final TreeSet<String> tree = new TreeSet<>();
        for (String string : optionMap.keySet()) {
            tree.add(string);
        }
        for (String string : tree) {
            list.add(optionMap.get(string));
        }
        return list;
    }

    /**
     * Prints the help message header.
     *
     * @param stream the output stream to which to write the help text
     */
    protected void printHelpHeader(PrintStream stream) {
    }

    /**
     * The {@code printHelp()} method prints a textual listing of these options and their syntax
     * to the specified output stream.
     * @param stream the output stream to which to write the help text
     * @param width the length of the line to truncate
     */
    public void printHelp(PrintStream stream, int width) {
        printHelpHeader(stream);
        for (Option<?> option : getSortedOptions()) {
            if (option.type == OptionTypes.BLANK_BOOLEAN_TYPE && option instanceof FieldOption) {
                FieldOption fopt = (FieldOption) option;
                try {
                    if (!fopt.nullValue.equals(fopt.field.getBoolean(null))) {
                        // Don't show message for "-XX:+<opt>" option if the default is represented by the
                        // "-XX:-<opt>" option instead (and vice versa).
                        continue;
                    }
                } catch (Exception e) {
                }
            }


            final Option<Object> opt = Utils.cast(option);
            stream.print("    " + getUsage(opt));
            final Object defaultValue = opt.getDefaultValue();
            if (defaultValue != null) {
                stream.println(" (default: " + opt.getType().unparseValue(defaultValue) + ")");
            } else {
                stream.println();
            }
            final String help = opt.getHelp();
            if (help.length() > 0) {
                stream.print(Strings.formatParagraphs(help, 8, 0, width));
                stream.println();
            }
        }
    }

    /**
     * Prints a textual listing of these options and their values
     * to the specified output stream.
     * @param stream the output stream to which to write the text
     * @param indent the number of spaces to indent
     * @param verbose add each option's description when true
     */
    public void printValues(PrintStream stream, int indent, boolean verbose) {
        final String indentation = Strings.times(' ', indent);
        for (Option<?> option : getSortedOptions()) {
            if (option.type == OptionTypes.BLANK_BOOLEAN_TYPE && option instanceof FieldOption) {
                FieldOption fopt = (FieldOption) option;
                try {
                    if (!fopt.nullValue.equals(fopt.field.getBoolean(null))) {
                        // Don't show message for "-XX:+<opt>" option if the default is represented by the
                        // "-XX:-<opt>" option instead (and vice versa).
                        continue;
                    }
                } catch (Exception e) {
                }
            }


            final Option<Object> opt = Utils.cast(option);
            stream.print(indentation + opt.getName() + ": " + opt.getValue());
            if (verbose) {
                stream.println(opt.isAssigned() ? "" : " (default)");
                stream.println("        " + opt.getHelp());
                stream.println("        " + getUsage(opt));
            }
            stream.println();
        }
    }

    /**
     * This method gets a usage string for a particular option that describes
     * the range of valid string values that it accepts.
     * @param option the option for which to get usage
     * @return a string describing the usage syntax for the specified option
     */
    public String getUsage(Option option) {
        return optionSyntax.get(option.getName()).getUsage(option);
    }

    /**
     * This method adds all public static fields with appropriate types to the
     * option set with the specified prefix.
     * @param javaClass the java class containing the fields
     * @param prefix the prefix to add to the options
     * @param helpMap map from option names to the help message for the option (may be {@code null})
     */
    public void addFieldOptions(Class<?> javaClass, String prefix, Map<String, String> helpMap) {
        for (final Field field : javaClass.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers)) {
                field.setAccessible(true);
                final OptionSettings settings = field.getAnnotation(OptionSettings.class);
                String help;
                String name;
                if (settings != null) {
                    help = settings.help();
                    name = settings.name().isEmpty() ? field.getName().replace('_', '-') : settings.name();
                } else {
                    name = field.getName().replace('_', '-');
                    help = helpMap != null ? helpMap.get(name) : "";
                    if (help == null) {
                        help = "";
                    }
                }
                addFieldOption(prefix, name, null, field, help);
            }
        }
    }

    /**
     * Adds a new option whose value is stored in the specified reflection field.
     * @param prefix the name of the option
     * @param name the name of the option
     * @param object the object containing the field (if the field is not static)
     * @param field the field to store the value
     * @param help the help text for the option   @return a new option that will modify the field when parsed
     * @return the option created
     */
    @SuppressWarnings("unchecked")
    public Option<?> addFieldOption(String prefix, String name, Object object, Field field, String help) {
        final Class<?> fieldType = field.getType();
        final Object defaultValue;
        final String optionName = prefix + ":" + name;
        try {
            defaultValue = field.get(null);
        } catch (IllegalAccessException e) {
            return null;
        }
        if (fieldType == boolean.class) {
            if (prefix.length() > 0) {
                // setup a "-prefix+name" option
                String plusName = prefix + ":+" + name;
                FieldOption<Boolean> plusOption = new FieldOption<>(plusName, object, field, (Boolean) defaultValue, OptionTypes.BLANK_BOOLEAN_TYPE, help);
                plusOption.nullValue = true;
                addOption(plusOption, Syntax.REQUIRES_BLANK);

                // setup a "-prefix-name" option
                String minusName = prefix + ":-" + name;
                FieldOption<Boolean> minusOption = new FieldOption<>(minusName, object, field, (Boolean) defaultValue, OptionTypes.BLANK_BOOLEAN_TYPE, help);
                minusOption.nullValue = false;
                return addOption(minusOption, Syntax.REQUIRES_BLANK);
            }
            return addOption(new FieldOption<>(optionName, object, field, (Boolean) defaultValue, OptionTypes.BOOLEAN_TYPE, help));

        } else if (fieldType == int.class) {
            return addOption(new FieldOption<>(optionName, object, field, (Integer) defaultValue, OptionTypes.INT_TYPE, help));
        } else if (fieldType == float.class) {
            return addOption(new FieldOption<>(optionName, object, field, (Float) defaultValue, OptionTypes.FLOAT_TYPE, help));
        } else if (fieldType == long.class) {
            return addOption(new FieldOption<>(optionName, object, field, (Long) defaultValue, OptionTypes.LONG_TYPE, help));
        } else if (fieldType == double.class) {
            return addOption(new FieldOption<>(optionName, object, field, (Double) defaultValue, OptionTypes.DOUBLE_TYPE, help));
        } else if (fieldType == String.class) {
            return addOption(new FieldOption<>(optionName, object, field, (String) defaultValue, OptionTypes.STRING_TYPE, help));
        } else if (fieldType == File.class) {
            return addOption(new FieldOption<>(optionName, object, field, (File) defaultValue, OptionTypes.FILE_TYPE, help));
        } else if (fieldType.isEnum()) {
            final Class<? extends Enum> enumClass = Utils.cast(fieldType);
            return addOption(makeEnumFieldOption(optionName, object, field, defaultValue, enumClass, help));
        }
        return null;
    }

    private static <T extends Enum<T>> FieldOption<T> makeEnumFieldOption(String name, Object object, Field field, Object defaultValue, Class<T> enumClass, String help) {
        final OptionTypes.EnumType<T> optionType = new OptionTypes.EnumType<>(enumClass);
        final T defaultV = Utils.<T>cast(defaultValue);
        return new FieldOption<>(name, object, field, defaultV, optionType, help);
    }

    public Option<String> newStringOption(String name, String defaultValue, String help) {
        return addOption(new Option<>(name, defaultValue, OptionTypes.STRING_TYPE, help));
    }

    public Option<Integer> newIntegerOption(String name, Integer defaultValue, String help) {
        return addOption(new Option<>(name, defaultValue, OptionTypes.INT_TYPE, help));
    }

    public Option<Long> newLongOption(String name, Long defaultValue, String help) {
        return addOption(new Option<>(name, defaultValue, OptionTypes.LONG_TYPE, help));
    }

    public Option<Float> newFloatOption(String name, Float defaultValue, String help) {
        return addOption(new Option<>(name, defaultValue, OptionTypes.FLOAT_TYPE, help));
    }

    public Option<Double> newDoubleOption(String name, Double defaultValue, String help) {
        return addOption(new Option<>(name, defaultValue, OptionTypes.DOUBLE_TYPE, help));
    }

    public Option<List<String>> newStringListOption(String name, String defaultValue, String help) {
        return addOption(new Option<>(name, defaultValue == null ? null : OptionTypes.COMMA_SEPARATED_STRING_LIST_TYPE.parseValue(defaultValue), OptionTypes.COMMA_SEPARATED_STRING_LIST_TYPE, help));
    }

    public Option<List<String>> newStringListOption(String name, String[] defaultValue, String help) {
        List<String> list = null;
        if (defaultValue != null) {
            list = new ArrayList<>(defaultValue.length);
            list.addAll(Arrays.asList(defaultValue));
        }
        return addOption(new Option<>(name, list, OptionTypes.COMMA_SEPARATED_STRING_LIST_TYPE, help));
    }

    public Option<List<String>> newStringListOption(String name, String defaultValue, char separator, String help) {
        final OptionTypes.StringListType type = new OptionTypes.StringListType(separator);
        return addOption(new Option<>(name, defaultValue == null ? null : type.parseValue(defaultValue), type, help));
    }

    public <T> Option<List<T>> newListOption(String name, String defaultValue, Option.Type<T> elementOptionType, char separator, String help) {
        final OptionTypes.ListType<T> type = new OptionTypes.ListType<>(separator, elementOptionType);
        return addOption(new Option<>(name, defaultValue == null ? null : type.parseValue(defaultValue), type, help));
    }

    public Option<File> newFileOption(String name, String defaultValue, String help) {
        return newFileOption(name, OptionTypes.FILE_TYPE.parseValue(defaultValue), help);
    }

    public Option<File> newFileOption(String name, File defaultValue, String help) {
        return addOption(new Option<>(name, defaultValue, OptionTypes.FILE_TYPE, help));
    }

    public Option<URL> newURLOption(String name, URL defaultValue, String help) {
        return addOption(new Option<>(name, defaultValue, OptionTypes.URL_TYPE, help));
    }

    public <T> Option<T> newInstanceOption(String name, Class<T> klass, T defaultValue, String help) {
        return addOption(new Option<>(name, defaultValue, OptionTypes.createInstanceOptionType(klass), help));
    }

    public <T> Option<List<T>> newListInstanceOption(String name, String defaultValue, Class<T> klass, char separator, String help) {
        final OptionTypes.ListType<T> type = OptionTypes.createInstanceListOptionType(klass, separator);
        return addOption(new Option<>(name, (defaultValue == null) ? null : type.parseValue(defaultValue), type, help));
    }

    public Option<Boolean> newBooleanOption(String name, Boolean defaultValue, String help) {
        if (defaultValue != null && !defaultValue) {
            return addOption(new Option<>(name, defaultValue, OptionTypes.BOOLEAN_TYPE, help), Syntax.EQUALS_OR_BLANK);
        }
        return addOption(new Option<>(name, defaultValue, OptionTypes.BOOLEAN_TYPE, help));
    }

    public <T> Option<T> newOption(String name, String defaultValue, Option.Type<T> type, String help) {
        return newOption(name, type.parseValue(defaultValue), type, Syntax.REQUIRES_EQUALS, help);
    }

    public <T> Option<T> newOption(String name, T defaultValue, Option.Type<T> type, Syntax syntax, String help) {
        return addOption(new Option<>(name, defaultValue, type, help), syntax);
    }

    public <E extends Enum<E>> Option<E> newEnumOption(String name, E defaultValue, Class<E> enumClass, String help) {
        return addOption(new Option<>(name, defaultValue, new OptionTypes.EnumType<>(enumClass), help));
    }

    public <E extends Enum<E>> Option<List<E>> newEnumListOption(String name, Iterable<E> defaultValue, Class<E> enumClass, String help) {
        final List<E> list;
        if (defaultValue == null) {
            list = null;
        } else if (defaultValue instanceof List) {
            list = Utils.cast(defaultValue);
        } else if (defaultValue instanceof Collection) {
            final Collection<E> collection = Utils.cast(defaultValue);
            list = new ArrayList<>(collection);
        } else {
            list = new ArrayList<>();
            for (E value : defaultValue) {
                list.add(value);
            }
        }
        final Option<List<E>> option = new Option<>(name, list, new OptionTypes.EnumListType<>(enumClass, ','), help);
        return addOption(option);
    }

    public Option<File> newConfigOption(String name, File defaultFile, String help) {
        return addOption(new Option<>(name, defaultFile, new OptionTypes.ConfigFile(this), help));
    }
}
