/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.driver;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.oracle.svm.shared.option.APIOption;
import com.oracle.svm.shared.util.StringUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.util.json.JsonWriter;

final class ComprehensiveOptions {
    private static final Pattern HELP_OPTION_LINE_PATTERN = Pattern.compile("^\\s{4}([^\\s].*?)(?:\\s{2,}(.*))?$");
    private static final String HELP_CONTINUATION_PREFIX = "                          ";
    private static final String HELP_OPTION_META_PREFIX = "||";
    private static final String HELP_OPTION_META_CONTINUATION_PREFIX = "||>";
    private static final String MARKDOWN_ROW_FORMAT = "| `%s` | %s | %s | %s | %s |";
    private static final String TABLE_ROW_FORMAT = "%-30s %-10s %-50s %-15s %-25s";
    private static final String OPTION_TYPE_STRING = "String";

    private ComprehensiveOptions() {
    }

    static void printOptions(Consumer<String> println, String format, SortedMap<String, APIOptionHandler.OptionInfo> apiOptions, Map<String, GroupInfo> groupInfos) {
        OutputFormat outputFormat = OutputFormat.fromString(format);
        outputFormat.printOptions(println, apiOptions, groupInfos);
    }

    private enum OutputFormat {
        MARKDOWN("markdown", "md") {
            @Override
            void printOptions(Consumer<String> println, SortedMap<String, APIOptionHandler.OptionInfo> apiOptions, Map<String, GroupInfo> groupInfos) {
                println.accept("# Native Image Build Options");
                println.accept("");
                println.accept("| Command | Type | Description | Default | Usage |");
                println.accept("|---------|------|-------------|---------|-------|");

                SortedMap<String, APIOptionHandler.OptionInfo> allOptions = new TreeMap<>(apiOptions);
                Set<String> printedCommands = new HashSet<>();

                for (Map.Entry<String, APIOptionHandler.OptionInfo> entry : allOptions.entrySet()) {
                    APIOptionHandler.OptionInfo option = entry.getValue();
                    if (option.isDeprecated()) {
                        continue;
                    }
                    if (option.group() != null) {
                        continue;
                    }

                    String command = escapeMarkdown(entry.getKey());
                    String type = determineOptionType(option);
                    String description = escapeMarkdown(option.helpText());
                    String defaultValue = option.defaultValue() != null ? escapeMarkdown(option.defaultValue()) : "None";
                    String usage = "`" + escapeMarkdown(generateUsageExample(entry.getKey(), option)) + "`";
                    printedCommands.add(entry.getKey());

                    println.accept(String.format(MARKDOWN_ROW_FORMAT,
                                    command, type, description, defaultValue, usage));
                }

                SortedMap<String, GroupInfo> allGroups = new TreeMap<>(groupInfos);
                for (Map.Entry<String, GroupInfo> groupEntry : allGroups.entrySet()) {
                    String commandName = normalizeGroupCommand(groupEntry.getKey());
                    if (printedCommands.contains(commandName)) {
                        continue;
                    }

                    GroupInfo groupInfo = groupEntry.getValue();
                    String command = escapeMarkdown(commandName);
                    String type = "Enum";
                    String description = escapeMarkdown(describeGroupOption(groupInfo));
                    String defaultValue = groupInfo.defaultValues.isEmpty() ? "None" : escapeMarkdown(String.join(",", groupInfo.defaultValues));
                    String usage = "`" + escapeMarkdown(groupUsage(groupEntry.getKey())) + "`";
                    printedCommands.add(commandName);

                    println.accept(String.format(MARKDOWN_ROW_FORMAT,
                                    command, type, description, defaultValue, usage));
                }

                for (Map.Entry<String, DriverOptionInfo> entry : getDriverOnlyOptions().entrySet()) {
                    if (printedCommands.contains(entry.getKey())) {
                        continue;
                    }

                    DriverOptionInfo option = entry.getValue();
                    if (option.deprecated()) {
                        continue;
                    }
                    String command = escapeMarkdown(entry.getKey());
                    String type = option.type();
                    String description = escapeMarkdown(option.helpText());
                    String defaultValue = escapeMarkdown(option.defaultValue());
                    String usage = "`" + escapeMarkdown(option.usage()) + "`";

                    println.accept(String.format(MARKDOWN_ROW_FORMAT,
                                    command, type, description, defaultValue, usage));
                }
                println.accept("");
            }
        },
        JSON("json") {
            @Override
            void printOptions(Consumer<String> println, SortedMap<String, APIOptionHandler.OptionInfo> apiOptions, Map<String, GroupInfo> groupInfos) {
                try (StringWriter stringWriter = new StringWriter(); JsonWriter jsonWriter = new JsonWriter(stringWriter)) {
                    try (var rootObject = jsonWriter.objectBuilder(); var optionsArray = rootObject.append("nativeImageOptions").array()) {
                        SortedMap<String, APIOptionHandler.OptionInfo> allOptions = new TreeMap<>(apiOptions);
                        Set<String> printedCommands = new HashSet<>();
                        for (Map.Entry<String, APIOptionHandler.OptionInfo> entry : allOptions.entrySet()) {
                            APIOptionHandler.OptionInfo option = entry.getValue();
                            if (option.group() != null) {
                                continue;
                            }
                            printedCommands.add(entry.getKey());
                            try (var optionObject = optionsArray.nextEntry().object()) {
                                optionObject.append("command", entry.getKey());
                                optionObject.append("type", determineOptionType(option));
                                optionObject.append("description", option.helpText());
                                optionObject.append("defaultValue", option.defaultValue() != null ? option.defaultValue() : "");
                                optionObject.append("deprecated", option.isDeprecated());
                                optionObject.append("usage", generateUsageExample(entry.getKey(), option));
                            }
                        }

                        SortedMap<String, GroupInfo> allGroups = new TreeMap<>(groupInfos);
                        for (Map.Entry<String, GroupInfo> groupEntry : allGroups.entrySet()) {
                            String command = normalizeGroupCommand(groupEntry.getKey());
                            if (printedCommands.contains(command)) {
                                continue;
                            }

                            GroupInfo groupInfo = groupEntry.getValue();
                            try (var optionObject = optionsArray.nextEntry().object()) {
                                optionObject.append("command", command);
                                optionObject.append("type", "Enum");
                                optionObject.append("description", describeGroupOption(groupInfo));
                                optionObject.append("defaultValue", groupInfo.defaultValues.isEmpty() ? "" : String.join(",", groupInfo.defaultValues));
                                optionObject.append("deprecated", false);
                                optionObject.append("usage", groupUsage(groupEntry.getKey()));
                            }
                            printedCommands.add(command);
                        }

                        for (Map.Entry<String, DriverOptionInfo> entry : getDriverOnlyOptions().entrySet()) {
                            if (printedCommands.contains(entry.getKey())) {
                                continue;
                            }

                            DriverOptionInfo option = entry.getValue();
                            try (var optionObject = optionsArray.nextEntry().object()) {
                                optionObject.append("command", entry.getKey());
                                optionObject.append("type", option.type());
                                optionObject.append("description", option.helpText());
                                optionObject.append("defaultValue", option.defaultValue());
                                optionObject.append("deprecated", option.deprecated());
                                optionObject.append("usage", option.usage());
                            }
                        }
                    }
                    println.accept(stringWriter.toString());
                } catch (IOException e) {
                    throw VMError.shouldNotReachHere("Unexpected failure while generating JSON output", e);
                }
            }
        },
        TABLE("table") {
            @Override
            void printOptions(Consumer<String> println, SortedMap<String, APIOptionHandler.OptionInfo> apiOptions, Map<String, GroupInfo> groupInfos) {
                final String ellipsis = "...";

                println.accept("Native Image Build Options:");
                println.accept("");
                println.accept(String.format(TABLE_ROW_FORMAT, "Command", "Type", "Description", "Default", "Usage"));
                println.accept("=".repeat(130));

                SortedMap<String, APIOptionHandler.OptionInfo> allOptions = new TreeMap<>(apiOptions);
                Set<String> printedCommands = new HashSet<>();

                for (Map.Entry<String, APIOptionHandler.OptionInfo> entry : allOptions.entrySet()) {
                    APIOptionHandler.OptionInfo option = entry.getValue();
                    if (option.isDeprecated()) {
                        continue;
                    }
                    if (option.group() != null) {
                        continue;
                    }

                    String command = entry.getKey();
                    String type = determineOptionType(option);
                    String description = option.helpText();
                    String defaultValue = option.defaultValue() != null ? option.defaultValue() : "None";
                    String usage = generateUsageExample(entry.getKey(), option);
                    printedCommands.add(command);

                    if (description.length() > 50) {
                        description = description.substring(0, 47) + ellipsis;
                    }
                    if (defaultValue.length() > 15) {
                        defaultValue = defaultValue.substring(0, 12) + ellipsis;
                    }
                    if (usage.length() > 25) {
                        usage = usage.substring(0, 22) + ellipsis;
                    }

                    println.accept(String.format(TABLE_ROW_FORMAT, command, type, description, defaultValue, usage));
                }

                SortedMap<String, GroupInfo> allGroups = new TreeMap<>(groupInfos);
                for (Map.Entry<String, GroupInfo> groupEntry : allGroups.entrySet()) {
                    String command = normalizeGroupCommand(groupEntry.getKey());
                    if (printedCommands.contains(command)) {
                        continue;
                    }
                    GroupInfo groupInfo = groupEntry.getValue();
                    String type = "Enum";
                    String description = describeGroupOption(groupInfo);
                    String defaultValue = groupInfo.defaultValues.isEmpty() ? "None" : String.join(",", groupInfo.defaultValues);
                    String usage = groupUsage(groupEntry.getKey());
                    printedCommands.add(command);

                    if (description.length() > 50) {
                        description = description.substring(0, 47) + ellipsis;
                    }
                    if (defaultValue.length() > 15) {
                        defaultValue = defaultValue.substring(0, 12) + ellipsis;
                    }
                    if (usage.length() > 25) {
                        usage = usage.substring(0, 22) + ellipsis;
                    }

                    println.accept(String.format(TABLE_ROW_FORMAT, command, type, description, defaultValue, usage));
                }

                for (Map.Entry<String, DriverOptionInfo> entry : getDriverOnlyOptions().entrySet()) {
                    if (printedCommands.contains(entry.getKey())) {
                        continue;
                    }

                    DriverOptionInfo option = entry.getValue();
                    if (option.deprecated()) {
                        continue;
                    }
                    String command = entry.getKey();
                    String type = option.type();
                    String description = option.helpText();
                    String defaultValue = option.defaultValue();
                    String usage = option.usage();

                    if (description.length() > 50) {
                        description = description.substring(0, 47) + ellipsis;
                    }
                    if (defaultValue.length() > 15) {
                        defaultValue = defaultValue.substring(0, 12) + ellipsis;
                    }
                    if (usage.length() > 25) {
                        usage = usage.substring(0, 22) + ellipsis;
                    }

                    println.accept(String.format(TABLE_ROW_FORMAT, command, type, description, defaultValue, usage));
                }
                println.accept("");
            }
        };

        private final String[] aliases;

        OutputFormat(String... aliases) {
            this.aliases = aliases;
        }

        abstract void printOptions(Consumer<String> println, SortedMap<String, APIOptionHandler.OptionInfo> apiOptions, Map<String, GroupInfo> groupInfos);

        static OutputFormat fromString(String format) {
            String lowerFormat = format.toLowerCase(Locale.ROOT);
            for (OutputFormat outputFormat : values()) {
                for (String alias : outputFormat.aliases) {
                    if (alias.equals(lowerFormat)) {
                        return outputFormat;
                    }
                }
            }
            throw NativeImage.showError("Invalid format: '" + format + "'. Valid formats are: " +
                            Arrays.stream(values())
                                            .flatMap(f -> Arrays.stream(f.aliases))
                                            .collect(Collectors.joining(", ")));
        }
    }

    record DriverOptionInfo(String type, String helpText, String defaultValue, String usage, boolean deprecated) {
    }

    private static SortedMap<String, DriverOptionInfo> getDriverOnlyOptions() {
        SortedMap<String, DriverOptionInfo> options = new TreeMap<>();
        collectDriverOptionsFromHelp(options, NativeImage.HELP_TEXT);
        collectDriverOptionsFromHelp(options, NativeImage.HELP_EXTRA_TEXT);
        return options;
    }

    private static void collectDriverOptionsFromHelp(SortedMap<String, DriverOptionInfo> options, String helpText) {
        boolean hasMetaInfo = Arrays.stream(helpText.split("\\n"))
                        .map(String::stripLeading)
                        .anyMatch(line -> line.startsWith(HELP_OPTION_META_PREFIX));

        if (hasMetaInfo) {
            String currentMetaCommand = null;
            DriverOptionInfo currentMetaOption = null;
            for (String line : helpText.split("\\n")) {
                String strippedLine = line.stripLeading();
                if (strippedLine.startsWith(HELP_OPTION_META_CONTINUATION_PREFIX)) {
                    if (currentMetaCommand != null && currentMetaOption != null) {
                        String continuation = parseMetaContinuation(strippedLine);
                        if (!continuation.isEmpty()) {
                            String separator = currentMetaOption.helpText().isEmpty() ? "" : " ";
                            currentMetaOption = new DriverOptionInfo(currentMetaOption.type(), currentMetaOption.helpText() + separator + continuation,
                                            currentMetaOption.defaultValue(), currentMetaOption.usage(), currentMetaOption.deprecated());
                            options.put(currentMetaCommand, currentMetaOption);
                        }
                    }
                    continue;
                }
                if (strippedLine.startsWith(HELP_OPTION_META_PREFIX)) {
                    DriverOptionMeta meta = parseMetaOption(strippedLine);
                    if (meta != null) {
                        currentMetaCommand = meta.command();
                        currentMetaOption = new DriverOptionInfo(meta.type(), meta.description(), meta.defaultValue(), meta.usage(), meta.deprecated());
                        options.put(currentMetaCommand, currentMetaOption);
                    } else {
                        currentMetaCommand = null;
                        currentMetaOption = null;
                    }
                    continue;
                }

                currentMetaCommand = null;
                currentMetaOption = null;
            }
            return;
        }

        String currentSpec = null;
        StringBuilder currentDescription = new StringBuilder();
        for (String line : helpText.split("\\n")) {
            var matcher = HELP_OPTION_LINE_PATTERN.matcher(line);
            if (matcher.matches()) {
                flushDriverOption(options, currentSpec, currentDescription);
                currentSpec = matcher.group(1).trim();
                currentDescription.setLength(0);
                String inlineDescription = matcher.group(2);
                if (inlineDescription != null && !inlineDescription.isBlank()) {
                    currentDescription.append(inlineDescription.trim());
                }
            } else if (currentSpec != null && line.startsWith(HELP_CONTINUATION_PREFIX)) {
                String continuation = line.trim();
                if (!continuation.isEmpty()) {
                    if (!currentDescription.isEmpty()) {
                        currentDescription.append(' ');
                    }
                    currentDescription.append(continuation);
                }
            } else {
                flushDriverOption(options, currentSpec, currentDescription);
                currentSpec = null;
                currentDescription.setLength(0);
            }
        }
        flushDriverOption(options, currentSpec, currentDescription);
    }

    private record DriverOptionMeta(String command, String usage, String description, String type, String defaultValue, boolean deprecated) {
    }

    private static DriverOptionMeta parseMetaOption(String line) {
        if (!line.startsWith(HELP_OPTION_META_PREFIX) || line.startsWith(HELP_OPTION_META_CONTINUATION_PREFIX)) {
            return null;
        }
        List<String> parts = splitMetaFields(line.substring(HELP_OPTION_META_PREFIX.length()));
        if (parts.size() < 6) {
            return null;
        }

        String command = parts.get(0).trim();
        if (command.isEmpty()) {
            return null;
        }
        String usageSuffix = parts.get(1);
        String usage = command + usageSuffix;
        String description = parts.get(2).trim();
        String type = normalizeOptionType(parts.get(3).isBlank() ? inferDriverOptionType(usage) : parts.get(3).trim());
        String defaultValue = parts.get(4).trim();
        boolean deprecated = parseDeprecated(parts.get(5), description);
        return new DriverOptionMeta(command, usage, description, type, defaultValue, deprecated);
    }

    private static List<String> splitMetaFields(String value) {
        ArrayList<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaping) {
                current.append(ch);
                escaping = false;
            } else if (ch == '\\') {
                escaping = true;
            } else if (ch == '|') {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private static String parseMetaContinuation(String line) {
        String continuation = line.substring(HELP_OPTION_META_CONTINUATION_PREFIX.length());
        if (continuation.endsWith("|")) {
            continuation = continuation.substring(0, continuation.length() - 1);
        }
        return continuation.trim();
    }

    private static boolean parseDeprecated(String deprecatedField, String description) {
        if (deprecatedField == null || deprecatedField.isBlank()) {
            return description.toLowerCase(Locale.ROOT).contains("deprecated") || description.toLowerCase(Locale.ROOT).contains("legacy");
        }
        String normalized = deprecatedField.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("true") || normalized.equals("yes") || normalized.equals("deprecated");
    }

    private static String normalizeOptionType(String type) {
        if (type == null || type.isBlank()) {
            return OPTION_TYPE_STRING;
        }
        return type.equalsIgnoreCase("flag") ? "Boolean" : type;
    }

    private static void flushDriverOption(SortedMap<String, DriverOptionInfo> options, String spec, StringBuilder descriptionBuilder) {
        if (spec == null || spec.isEmpty()) {
            return;
        }
        String description = descriptionBuilder.toString().trim();
        String[] alternatives = spec.split(",\\s+(?=@|-)");
        for (String alternative : alternatives) {
            String usage = alternative.trim();
            if (usage.isEmpty()) {
                continue;
            }
            String command = normalizeDriverOptionCommand(usage.split("\\s+", 2)[0]);
            if (!(command.startsWith("-") || command.startsWith("@"))) {
                continue;
            }
            String type = inferDriverOptionType(usage);
            boolean deprecated = description.toLowerCase(Locale.ROOT).contains("deprecated") || description.toLowerCase(Locale.ROOT).contains("legacy");
            options.putIfAbsent(command, new DriverOptionInfo(type, description, "", usage, deprecated));
        }
    }

    private static String inferDriverOptionType(String usage) {
        if (usage.startsWith("@")) {
            return OPTION_TYPE_STRING;
        }
        if (usage.contains("<") || usage.contains("=") || usage.contains("[=")) {
            if (usage.contains("path") || usage.contains("Path")) {
                return "Path";
            }
            return OPTION_TYPE_STRING;
        }
        return "Boolean";
    }

    private static String normalizeDriverOptionCommand(String command) {
        int optionalPart = command.indexOf('[');
        if (optionalPart >= 0) {
            return command.substring(0, optionalPart);
        }
        return command;
    }

    private static String normalizeGroupCommand(String groupNameAndSeparator) {
        return groupNameAndSeparator.endsWith("=") ? groupNameAndSeparator.substring(0, groupNameAndSeparator.length() - 1) : groupNameAndSeparator;
    }

    private static String groupUsage(String groupNameAndSeparator) {
        return normalizeGroupCommand(groupNameAndSeparator) + "=<value>";
    }

    private static String describeGroupOption(GroupInfo groupInfo) {
        StringBuilder builder = new StringBuilder(startLowerCase(groupInfo.group.helpText()));
        if (!groupInfo.group.helpText().endsWith(".")) {
            builder.append('.');
        }
        if (!groupInfo.supportedValues.isEmpty()) {
            builder.append(" Allowed values: ");
            builder.append(StringUtil.joinSingleQuoted(groupInfo.supportedValues));
            builder.append('.');
        }
        return builder.toString();
    }

    private static String determineOptionType(APIOptionHandler.OptionInfo option) {
        if (option.group() != null) {
            return OPTION_TYPE_STRING;
        }
        return option.booleanOption() ? "Boolean" : OPTION_TYPE_STRING;
    }

    private static String generateUsageExample(String optionName, APIOptionHandler.OptionInfo option) {
        if (option.group() != null || option.booleanOption() || option.fixedValue()) {
            return optionName;
        }
        String valueSeparator = preferredValueSeparator(option.valueSeparator());
        String value = valueSeparator + "<value>";
        return option.defaultValue() != null ? optionName + "[" + value + "]" : optionName + value;
    }

    private static String preferredValueSeparator(char[] valueSeparators) {
        for (char valueSeparator : valueSeparators) {
            if (valueSeparator == '=') {
                return "=";
            }
        }
        char valueSeparator = valueSeparators[0];
        return APIOption.Utils.valueSeparatorToString(valueSeparator);
    }

    private static String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("|", "\\|").replace("\\", "\\\\");
    }

    private static String startLowerCase(String str) {
        return str.substring(0, 1).toLowerCase(Locale.ROOT) + str.substring(1);
    }
}
