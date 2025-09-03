/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.dcmd;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.VMError;

/* Abstract base class for diagnostic commands. */
public abstract class AbstractDCmd implements DCmd {
    private final String name;
    private final String description;
    private final Impact impact;
    private final DCmdOption<?>[] arguments;
    private final DCmdOption<?>[] options;
    private final String[] examples;

    @Platforms(Platform.HOSTED_ONLY.class)
    public AbstractDCmd(String name, String description, Impact impact) {
        this(name, description, impact, new DCmdOption<?>[0], new DCmdOption<?>[0], new String[0]);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public AbstractDCmd(String name, String description, Impact impact, DCmdOption<?>[] arguments, DCmdOption<?>[] options) {
        this(name, description, impact, arguments, options, new String[0]);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public AbstractDCmd(String name, String description, Impact impact, DCmdOption<?>[] arguments, DCmdOption<?>[] options, String[] examples) {
        this.name = name;
        this.description = description;
        this.impact = impact;
        this.arguments = arguments;
        this.options = options;
        this.examples = examples;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String parseAndExecute(String input) throws Throwable {
        DCmdArguments args = parse(input);
        return execute(args);
    }

    protected abstract String execute(DCmdArguments args) throws Throwable;

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+18/src/hotspot/share/services/diagnosticFramework.cpp#L189-L220")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+18/src/hotspot/share/services/diagnosticFramework.cpp#L234-L253")
    private DCmdArguments parse(String input) {
        DCmdArguments result = new DCmdArguments();
        DCmdArgCursor cursor = new DCmdArgCursor(input, ' ');

        /* Skip the first value in the input because it is the command-name. */
        boolean isCommandNamePresent = cursor.advance();
        assert isCommandNamePresent;
        assert name.equals(cursor.getKey());
        assert cursor.getValue() == null;

        /* Iterate and parse the remaining input. */
        while (cursor.advance()) {
            parseOption(cursor.getKey(), cursor.getValue(), result);
        }

        /* Check that all mandatory arguments have been set. */
        for (DCmdOption<?> arg : arguments) {
            if (arg.required() && !result.hasBeenSet(arg)) {
                throw new IllegalArgumentException("The argument '" + arg.name() + "' is mandatory.");
            }
        }

        /* Check that all mandatory options have been set. */
        for (DCmdOption<?> option : options) {
            if (option.required() && !result.hasBeenSet(option)) {
                throw new IllegalArgumentException("The option '" + option.name() + "' is mandatory.");
            }
        }

        return result;
    }

    private void parseOption(String left, String right, DCmdArguments result) {
        DCmdOption<?> matchingOption = findOption(left);
        if (matchingOption != null) {
            /* Found a matching option, so use the specified value. */
            Object value = parseValue(matchingOption, right);
            result.set(matchingOption, value);
            return;
        }

        /*
         * String doesn't match any option, so use the left part as the value for the next available
         * argument (and completely ignore the right part).
         */
        for (DCmdOption<?> option : arguments) {
            if (!result.hasBeenSet(option)) {
                Object value = parseValue(option, left);
                result.set(option, value);
                return;
            }
        }

        throw new IllegalArgumentException("Unknown argument '" + left + "' in diagnostic command");
    }

    private DCmdOption<?> findOption(String optionName) {
        for (DCmdOption<?> option : options) {
            if (option.name().equals(optionName)) {
                return option;
            }
        }
        return null;
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+26/src/hotspot/share/services/diagnosticArgument.cpp#L141-L171")
    private static Object parseValue(DCmdOption<?> option, String valueString) {
        Class<?> type = option.type();
        if (type == Boolean.class) {
            if (valueString == null || valueString.isEmpty() || "true".equals(valueString)) {
                return Boolean.TRUE;
            } else if ("false".equals(valueString)) {
                return Boolean.FALSE;
            } else {
                throw new IllegalArgumentException("Boolean parsing error in command argument '" + option.name() + "'. Could not parse: " + valueString + ".");
            }
        } else if (type == String.class) {
            return valueString;
        } else {
            throw VMError.shouldNotReachHere("Unexpected option type: " + type);
        }
    }

    @Override
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+18/src/hotspot/share/services/diagnosticFramework.cpp#L255-L299")
    public String getHelp() {
        String lineBreak = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append(getName()).append(lineBreak);
        if (description != null) {
            sb.append(description).append(lineBreak);
        }
        sb.append(lineBreak);
        sb.append("Impact: ").append(impact.name()).append(lineBreak);
        sb.append(lineBreak);

        String value = getSyntaxAndExamples();
        sb.append(value);

        return sb.toString();
    }

    protected String getSyntaxAndExamples() {
        String lineBreak = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("Syntax : ").append(getName());

        if (options.length > 0) {
            sb.append(" [options]");
        }

        for (DCmdOption<?> option : arguments) {
            sb.append(" ");
            if (!option.required()) {
                sb.append("[");
            }
            sb.append("<").append(option.name()).append(">");
            if (!option.required()) {
                sb.append("]");
            }
        }

        if (arguments.length > 0) {
            sb.append(lineBreak).append(lineBreak);
            sb.append("Arguments:");
            for (DCmdOption<?> arg : arguments) {
                sb.append(lineBreak);
                appendOption(sb, arg);
            }
        }

        if (options.length > 0) {
            sb.append(lineBreak).append(lineBreak);
            sb.append("Options: (options must be specified using the <key> or <key>=<value> syntax)");
            for (DCmdOption<?> option : options) {
                sb.append(lineBreak);
                appendOption(sb, option);
            }
        }

        if (examples.length > 0) {
            sb.append(lineBreak).append(lineBreak);
            sb.append("Example usage:");
            for (String example : examples) {
                sb.append(lineBreak);
                sb.append("\t").append(example);
            }
        }

        return sb.toString();
    }

    private static void appendOption(StringBuilder sb, DCmdOption<?> option) {
        sb.append("\t").append(option.name()).append(" : ");
        if (!option.required()) {
            sb.append("[optional] ");
        }
        sb.append(option.description());
        sb.append(" (").append(typeToString(option)).append(", ");
        if (option.defaultValue() != null) {
            sb.append(option.defaultValue());
        } else {
            sb.append("no default value");
        }
        sb.append(")");
    }

    private static String typeToString(DCmdOption<?> option) {
        Class<?> type = option.type();
        if (type == Boolean.class) {
            return "BOOLEAN";
        } else if (type == String.class) {
            return "STRING";
        } else {
            throw VMError.shouldNotReachHere("Unexpected option type: " + type);
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+18/src/hotspot/share/services/diagnosticFramework.hpp#L102-L121")
    private static class DCmdArgCursor {
        private final String input;
        private final int length;
        private final char delimiter;

        private int cursor;
        private int keyPos;
        private int keyLength;
        private int valuePos;
        private int valueLength;

        DCmdArgCursor(String input, char delimiter) {
            this.input = input;
            this.length = input.length();
            this.delimiter = delimiter;
        }

        String getKey() {
            if (keyLength == 0) {
                return null;
            }
            return input.substring(keyPos, keyPos + keyLength);
        }

        String getValue() {
            if (valueLength == 0) {
                return null;
            }
            return input.substring(valuePos, valuePos + valueLength);
        }

        @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+18/src/hotspot/share/services/diagnosticFramework.cpp#L67-L145")
        private boolean advance() {
            /* Skip delimiters. */
            while (cursor < length - 1 && input.charAt(cursor) == delimiter) {
                cursor++;
            }

            /* Handle end of input. */
            if (cursor == length - 1 && input.charAt(cursor) == delimiter) {
                keyPos = cursor;
                keyLength = 0;
                valuePos = cursor;
                valueLength = 0;
                return false;
            }

            /* Extract first item (argument or option name). */
            keyPos = cursor;
            boolean argHadQuotes = false;
            while (cursor <= length - 1 && input.charAt(cursor) != '=' && input.charAt(cursor) != delimiter) {
                /* Argument can be surrounded by single or double quotes. */
                if (input.charAt(cursor) == '\"' || input.charAt(cursor) == '\'') {
                    keyPos++;
                    char quote = input.charAt(cursor);
                    argHadQuotes = true;
                    while (cursor < length - 1) {
                        cursor++;
                        if (input.charAt(cursor) == quote && input.charAt(cursor - 1) != '\\') {
                            break;
                        }
                    }
                    if (input.charAt(cursor) != quote) {
                        throw new IllegalArgumentException("Format error in diagnostic command arguments");
                    }
                    break;
                }
                cursor++;
            }

            keyLength = cursor - keyPos;
            if (argHadQuotes) {
                /* If the argument was quoted, we need to step past the last quote here. */
                cursor++;
            }

            /* Check if the argument has the <key>=<value> format. */
            if (cursor <= length - 1 && input.charAt(cursor) == '=') {
                cursor++;
                valuePos = cursor;
                boolean valueHadQuotes = false;
                /* Extract the value. */
                while (cursor <= length - 1 && input.charAt(cursor) != delimiter) {
                    /* Value can be surrounded by simple or double quotes. */
                    if (input.charAt(cursor) == '\"' || input.charAt(cursor) == '\'') {
                        valuePos++;
                        char quote = input.charAt(cursor);
                        valueHadQuotes = true;
                        while (cursor < length - 1) {
                            cursor++;
                            if (input.charAt(cursor) == quote && input.charAt(cursor - 1) != '\\') {
                                break;
                            }
                        }
                        if (input.charAt(cursor) != quote) {
                            throw new IllegalArgumentException("Format error in diagnostic command arguments");
                        }
                        break;
                    }
                    cursor++;
                }
                valueLength = cursor - valuePos;
                if (valueHadQuotes) {
                    /* If the value was quoted, we need to step past the last quote here. */
                    cursor++;
                }
            } else {
                valuePos = 0;
                valueLength = 0;
            }
            return keyLength != 0;
        }
    }
}
