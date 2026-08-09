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
package com.oracle.svm.core.logging;

import static com.oracle.svm.core.logging.LogTagSet.logging;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.SubstrateJVM;

/// Owns the startup configuration for unified logging.
public final class LogConfiguration {
    /// Preserves command-line output insertion order for deterministic teardown and diagnostics.
    private static final Map<String, LogFileOutput> OUTPUTS = HasULSupport.get() ? new LinkedHashMap<>() : null;

    /// Native standard output destination created when runtime option parsing begins.
    private static final LogFileStreamOutput stdout = HasULSupport.get() ? new LogFileStreamOutput(false) : null;

    /// Native standard error destination, disabled by the default configuration.
    private static final LogFileStreamOutput stderr = HasULSupport.get() ? new LogFileStreamOutput(true) : null;

    /// Native host name cached before emergency logging can prohibit allocation.
    private static String hostname;

    /// Process identifier cached before emergency logging can prohibit allocation.
    private static long pid;

    /// Async writer requested by the command line and published after initialization.
    private static volatile LogAsyncWriter asyncWriter;

    /// Selects the HotSpot-compatible stall mode when async logging is enabled.
    private static boolean asyncStall;

    /// Records whether the command line requested asynchronous output.
    private static boolean asyncRequested;

    private LogConfiguration() {
    }

    /// Installs default `all=warning` stdout configuration.
    public static void initialize() {
        HasULSupport.require();
        hostname = LoggingSupport.singleton().hostname();
        pid = ProcessProperties.getProcessID();
        for (LogTagSet tagSet : LogTagSet.values()) {
            tagSet.outputList().setOutputLevel(stdout, LogLevel.WARNING);
            tagSet.updateDecorators();
        }
        stdout.updateConfigString();
        stderr.updateConfigString();
    }

    /// Parses and applies one complete `-Xlog` argument.
    public static synchronized boolean parseCommandLineArgument(String argument) {
        if (!argument.equals("-Xlog") && !argument.startsWith("-Xlog:")) {
            return false;
        }
        String options = argument.equals("-Xlog") ? "" : argument.substring("-Xlog:".length());
        if (options.equalsIgnoreCase("help")) {
            stdout.writePlain(HELP);
            return true;
        }
        if (options.equalsIgnoreCase("async") || options.startsWith("async:")) {
            configureAsync(options);
            return true;
        }
        if (options.equalsIgnoreCase("disable")) {
            disableLogging();
            return true;
        }

        List<String> parts = splitComponents(options);
        String selectionsText = component(parts, 0);
        String outputText = component(parts, 1);
        String decoratorsText = component(parts, 2);
        String outputOptions = component(parts, 3);

        LogSelectionList selections = LogSelectionList.parse(selectionsText);
        LogOutput output = findOrCreateOutput(outputText);
        output.setDecorators(LogDecorators.parse(decoratorsText));
        if (!output.parseOptionsIfFirstConfiguration(outputOptions) && outputOptions != null && !outputOptions.isEmpty()) {
            warn("Output options for existing outputs are ignored.");
        }

        updateConfig(selections, output);
        warnUnmatchedSelections(selections);
        return true;
    }

    /// Applies `selections` to `output` and notifies listeners of the configuration change.
    private static void updateConfig(LogSelectionList selections, LogOutput output) {
        for (LogTagSet tagSet : LogTagSet.VALUES) {
            LogLevel level = selections.levelFor(tagSet);
            if (level != null) {
                tagSet.outputList().setOutputLevel(output, level);
            }
            tagSet.updateDecorators();
        }
        output.updateConfigString();
        updateJfrLogLevels();
    }

    /// Emits the framework initialization event after every startup option has been applied.
    public static void logInitializationComplete() {
        initializeAsyncWriter();
        boolean loggingClassLoadCause = LogTagSet.class_load_cause.isLevel(LogLevel.INFO) || LogTagSet.class_load_cause_native.isLevel(LogLevel.INFO);
        if (loggingClassLoadCause && RuntimeClassLoading.Options.LogClassLoadingCauseFor.getValue() == null) {
            warn("Class load cause logging will not produce output without LogClassLoadingCauseFor.");
        }

        if (logging.isInfo()) {
            logging.info("Log configuration fully initialized.");
            for (String desc : AVAILABLE_DESCRIPTIONS) {
                logging.info(desc);
            }

            if (logging.isDebug()) {
                logging.debug(AVAILABLE_TAG_SETS);
            }

            logging.info("Log output configuration:");
            int index = 0;
            logging.info(describeOutput(index++, stdout));
            logging.info(describeOutput(index++, stderr));
            for (LogFileOutput output : OUTPUTS.values()) {
                logging.info(describeOutput(index++, output));
            }
        }
    }

    private static void updateJfrLogLevels() {
        if (HasJfrSupport.get()) {
            SubstrateJVM.getLogging().updateLogLevels();
        }
    }

    public static void disableLogging() {
        disableLogging(false);
    }

    /// Removes every configured output and closes file destinations.
    public static synchronized void disableLogging(boolean onShutdown) {
        if (onShutdown && logging.isDebug()) {
            for (LogTagSet tagSet : LogTagSet.values()) {
                tagSet.logNativeBufferUsage(logging, LogLevel.DEBUG);
            }
            for (LogOutput output : OUTPUTS.values()) {
                output.logNativeBufferUsage(logging, LogLevel.DEBUG);
            }
        }

        stopAsyncWriter();
        for (LogTagSet tagSet : LogTagSet.values()) {
            tagSet.outputList().clear();
            tagSet.updateDecorators();
            tagSet.clear();
        }
        stdout.updateConfigString();
        stderr.updateConfigString();
        updateJfrLogLevels();
        asyncRequested = false;
        for (LogOutput output : OUTPUTS.values()) {
            output.close();
        }
        OUTPUTS.clear();
        stdout.close();
        stderr.close();
    }

    /// Gets the initialized writer used to route log records asynchronously.
    static LogAsyncWriter asyncWriter() {
        return asyncWriter;
    }

    /// Parses the optional asynchronous logging mode from `options`.
    private static void configureAsync(String options) {
        String mode = options.length() == "async".length() ? "drop" : options.substring("async:".length());
        if (!mode.equalsIgnoreCase("drop") && !mode.equalsIgnoreCase("stall")) {
            throw new IllegalArgumentException("Invalid async logging mode '" + mode + "'. Expected 'drop' or 'stall'.");
        }
        asyncStall = mode.equalsIgnoreCase("stall");
        asyncRequested = true;
    }

    /// Starts asynchronous output after all startup logging selections are configured.
    private static void initializeAsyncWriter() {
        if (asyncRequested && asyncWriter == null) {
            asyncWriter = new LogAsyncWriter(asyncStall);
        }
    }

    /// Drains and stops the writer before configured outputs are closed or reused.
    private static void stopAsyncWriter() {
        LogAsyncWriter writer = asyncWriter;
        if (writer != null) {
            writer.shutdown();
            writer.clear();
            asyncWriter = null;
        }
    }

    /// Gets the host name cached during startup configuration.
    static String hostname() {
        return hostname;
    }

    /// Gets the process identifier cached during startup configuration.
    static long pid() {
        return pid;
    }

    private static void warnUnmatchedSelections(LogSelectionList selections) {
        for (LogSelection selection : selections.selections()) {
            boolean matched = false;
            for (LogTagSet tagSet : LogTagSet.values()) {
                if (selection.selects(tagSet)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                StringBuilder warning = new StringBuilder("No tag set matches the specified selection.");
                List<String> suggestions = selection.suggestions();
                if (!suggestions.isEmpty()) {
                    warning.append(" Did you mean one of: ").append(String.join(", ", suggestions)).append('?');
                }
                warn(warning.toString());
            }
        }
    }

    public static void warn(String message) {
        stderr.writePlain("[warning][logging] " + message + System.lineSeparator());
    }

    private static LogOutput findOrCreateOutput(String value) {
        String normalized = value == null || value.isEmpty() ? "stdout" : value;
        if (normalized.equals("#0")) {
            normalized = "stdout";
        } else if (normalized.equals("#1")) {
            normalized = "stderr";
        }
        if (normalized.equals("stdout")) {
            return stdout;
        }
        if (normalized.equals("stderr")) {
            return stderr;
        }

        String rawFilename = normalized.startsWith("file=") ? normalized.substring("file=".length()) : normalized;
        String filename = stripQuotes(rawFilename);
        if (filename.isEmpty()) {
            throw new IllegalArgumentException("Log output filename must not be empty.");
        }
        return OUTPUTS.computeIfAbsent(filename, _ -> new LogFileOutput(filename));
    }

    private static String stripQuotes(String value) {
        if (value.startsWith("\"") || value.endsWith("\"")) {
            if (value.length() < 2 || !value.startsWith("\"") || !value.endsWith("\"")) {
                throw new IllegalArgumentException("Output name has an unmatched quotation mark.");
            }
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static List<String> splitComponents(String value) {
        List<String> result = new ArrayList<>(4);
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        int componentStart = 0;
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (c == '"') {
                quoted = !quoted;
                current.append(c);
            } else if (c == ':' && !quoted && !isWindowsPathColon(value, componentStart, index)) {
                result.add(current.toString());
                current.setLength(0);
                componentStart = index + 1;
            } else {
                current.append(c);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Missing terminating quote in -Xlog option.");
        }
        result.add(current.toString());
        if (result.size() > 4) {
            throw new IllegalArgumentException("Too many ':' separated -Xlog components.");
        }
        return result;
    }

    /// Keeps a Windows drive-letter colon inside the output component.
    private static boolean isWindowsPathColon(String value, int componentStart, int index) {
        if (!Platform.includedIn(Platform.WINDOWS.class) || index + 1 >= value.length()) {
            return false;
        }
        char next = value.charAt(index + 1);
        return (next == '\\' || next == '/') &&
                        (index == componentStart + 1 || value.startsWith("file=", componentStart));
    }

    private static String component(List<String> components, int index) {
        return index < components.size() ? components.get(index) : null;
    }

    private static final String HELP = HasULSupport.get() ? initHelp() : null;

    @Platforms(Platform.HOSTED_ONLY.class)
    private static String initHelp() {
        var decorators = Stream.of(LogDecorators.VALUES) //
                        .map(d -> " %s (%s)".formatted(d.label(), d.abbreviation())) //
                        .collect(Collectors.joining(", ", " ", ""));
        var tags = Stream.of(LogTag.values()) //
                        .map(LogTag::label) //
                        .collect(Collectors.joining(", ", " ", ""));

        var describedTags = Stream.of(LogTagSet.values()) //
                        .filter(t -> t.description() != null) //
                        .map(t -> t.label() + ": " + t.description()) //
                        .collect(Collectors.joining("\n ", " ", ""));

        // @formatter:off
        return """
                -Xlog Usage: -Xlog[:[selections][:[output][:[decorators][:output-options]]]]
                        where 'selections' are combinations of tags and levels of the form tag1[+tag2...][*][=level][,...]
                        NOTE: Unless wildcard (*) is specified, only log messages tagged with exactly the tags specified will be matched.

                Available log levels:
                 off, trace, debug, info, warning, error

                Available log decorators:
                """ + decorators + """
                 Decorators can also be specified as 'none' for no decoration.

                Available log tags:
                """ + tags + """
                 Specifying 'all' instead of a tag combination matches all tag combinations.

                Described tag sets:""" + describedTags +  """
                Available log outputs:
                 stdout/stderr
                 file=<filename>
                  If the filename contains %%p, %%t and/or %%hn, they will expand to the JVM's PID, startup timestamp and host name, respectively.

                Available log output options:
                 foldmultilines=.. - If set to true, a log event that consists of multiple lines will be folded into a single line by replacing newline characters with the sequence '\\' and 'n' in the output.
                 Existing single backslash characters will also be replaced with a sequence of two backslashes so that the conversion can be reversed. This option is safe to use with UTF-8 character encodings, \
                 but other encodings may not work.

                Additional file output options:
                 filesize=..       - Target byte size for log rotation (supports K/M/G suffix). If set to 0, log rotation will not trigger automatically, but can be performed manually.
                 filecount=..      - Number of files to keep in rotation (not counting the active file). If set to 0, log rotation is disabled. This will cause existing log files to be overwritten.

                Asynchronous logging (off by default):
                 -Xlog:async[:[mode]]
                  All log messages are written to an intermediate buffer first and will then be flushed to the corresponding log outputs by a standalone thread. Write operations at logsites are guaranteed non-blocking.
                 A mode, either 'drop' or 'stall', may be provided. If 'drop' is provided then messages will be dropped if there is no room in the intermediate buffer.
                 If 'stall' is provided then the log operation will wait for room to be made by the output thread, without dropping any messages. The default mode is 'drop'.

                Some examples:
                 -Xlog
                        Log all messages up to 'info' level to stdout with 'uptime', 'level' and 'tags' decorations.
                        (Equivalent to -Xlog:all=info:stdout:uptime,level,tags).

                 -Xlog:gc
                        Log messages tagged with 'gc' tag up to 'info' level to stdout, with default decorations.

                 -Xlog:gc,safepoint
                        Log messages tagged either with 'gc' or 'safepoint' tags, both up to 'info' level, to stdout, with default decorations.
                        (Messages tagged with both 'gc' and 'safepoint' will not be logged.)

                 -Xlog:jfr+setting=debug
                        Log messages tagged with both 'jfr' and 'setting' tags, up to 'debug' level, to stdout, with default decorations.
                        (Messages tagged only with one of the two tags will not be logged.)

                 -Xlog:gc=debug:file=gc.txt:none
                        Log messages tagged with 'gc' tag up to 'debug' level to file 'gc.txt' with no decorations.

                 -Xlog:gc=trace:file=gctrace.txt:uptimemillis,pid:filecount=5,filesize=1m
                        Log messages tagged with 'gc' tag up to 'trace' level to a rotating fileset of 5 files of size 1MB,
                        using the base name 'gctrace.txt', with 'uptimemillis' and 'pid' decorations.

                 -Xlog:gc::uptime,tid
                        Log messages tagged with 'gc' tag up to 'info' level to output 'stdout', using 'uptime' and 'tid' decorations.

                 -Xlog:gc*=info,safepoint*=off
                        Log messages tagged with at least 'gc' up to 'info' level, but turn off logging of messages tagged with 'safepoint'.
                        (Messages tagged with both 'gc' and 'safepoint' will not be logged.)

                 -Xlog:disable -Xlog:safepoint=trace:safepointtrace.txt
                        Turn off all logging, including warnings and errors,
                        and then enable messages tagged with 'safepoint' up to 'trace' level to file 'safepointtrace.txt'.

                 -Xlog:async -Xlog:gc=debug:file=gc.log -Xlog:safepoint=trace
                        Write logs asynchronously. Enable messages tagged with 'safepoint' up to 'trace' level to stdout
                        and messages tagged with 'gc' up to 'debug' level to file 'gc.log'.
                """;
        // @formatter:on
    }

    /// Renders the available levels, decorators, tags, and descriptions for startup diagnostics.
    private static final String[] AVAILABLE_DESCRIPTIONS = HasULSupport.get() ? initAvailableDescriptions() : null;

    @Platforms(Platform.HOSTED_ONLY.class)
    private static String[] initAvailableDescriptions() {
        List<String> descriptions = new ArrayList<>(List.of(
                        "Available log levels: " + Stream.of(LogLevel.VALUES).map(LogLevel::label).collect(Collectors.joining(", ")),
                        "Available log decorators: " + Stream.of(LogDecorators.VALUES).map(d -> d.label() + " (" + d.abbreviation() + ")").collect(Collectors.joining(", ")),
                        "Available log tags: " + Stream.of(LogTag.values()).map(LogTag::label).collect(Collectors.joining(", ")),
                        "Described tag sets:"));
        Stream.of(LogTagSet.VALUES).map(LogTagSet::description).filter(Objects::nonNull).map(s -> " " + s).forEach(descriptions::add);
        return descriptions.toArray(new String[0]);
    }

    private static final String AVAILABLE_TAG_SETS = HasULSupport.get() ? "Available tag sets: " + //
                    Stream.of(LogTagSet.VALUES) //
                                    .map(LogTagSet::label) //
                                    .filter(s -> !s.isEmpty()) //
                                    .sorted() //
                                    .collect(Collectors.joining(", ")) : null;

    /// Describes one output and the thresholds currently assigned to its tag sets.
    private static String describeOutput(int index, LogOutput output) {
        return " #" + index + ": " + output.describe();
    }
}
