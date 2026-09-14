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

import static com.oracle.svm.core.logging.LogFileStreamOutput.Target.STDERR;
import static com.oracle.svm.core.logging.LogFileStreamOutput.Target.STDOUT;
import static com.oracle.svm.core.logging.LogTagSet.logging;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.svm.core.LibCHelper;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFilePath;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.guest.staging.core.memory.UntrackedNullableNativeMemory;
import com.oracle.svm.guest.staging.jdk.RuntimeSupport;
import com.oracle.svm.shared.util.TimeUtils;

/// Owns the configuration for `-Xlog`. Runtime changes wait for active log sites before mutable
/// output state is replaced.
public final class LogConfiguration {
    /// Preserves configuration insertion order for deterministic teardown and diagnostics.
    private static final List<LogFileOutput> OUTPUTS = new ArrayList<>();

    private static final LogFileStreamOutput stdout = new LogFileStreamOutput(STDOUT);

    private static final LogFileStreamOutput stderr = new LogFileStreamOutput(STDERR);

    /// Native host name.
    private static String hostname;

    /// Process identifier.
    private static long pid;

    /// Local startup timestamp used for every `%t` filename expansion.
    private static String startupTimestamp;

    /// Records whether startup option parsing and logging initialization have completed.
    private static volatile boolean initializationComplete;

    private LogConfiguration() {
    }

    /// Captures process metadata and installs the baseline logging configuration before runtime
    /// options are parsed when the `-Xlog` interface is supported.
    public static void initialize() {
        if (HasXlogSupport.get()) {
            hostname = LoggingSupport.singleton().hostname();
            pid = ProcessProperties.getProcessID();
            long systemMillis = TimeUtils.currentTimeMillis();
            startupTimestamp = formatStartupTimestamp(systemMillis, LibCHelper.SVM_localUTCOffsetSeconds(systemMillis));
            for (LogTagSet tagSet : LogTagSet.values()) {
                tagSet.outputList().setOutputLevel(stdout, LogLevel.WARNING);
                tagSet.updateDecorators();
            }
            stdout.updateConfigString();
            stderr.updateConfigString();

        }
    }

    /// Formats the `%t` filename substitution without consulting JDK locale or time zone defaults.
    /// Logging initializes before command-line properties are installed, so its startup timestamp
    /// must use the native local offset without initializing JDK state that depends on those
    /// properties.
    static String formatStartupTimestamp(long systemMillis, int localUTCOffsetSeconds) {
        long localSeconds = Math.floorDiv(systemMillis, 1_000) + localUTCOffsetSeconds;
        LocalDateTime localDateTime = LocalDateTime.ofEpochSecond(localSeconds, 0, ZoneOffset.UTC);
        StringBuilder result = new StringBuilder(19);
        appendPadded(result, localDateTime.getYear(), 4);
        result.append('-');
        appendPadded(result, localDateTime.getMonthValue(), 2);
        result.append('-');
        appendPadded(result, localDateTime.getDayOfMonth(), 2);
        result.append('_');
        appendPadded(result, localDateTime.getHour(), 2);
        result.append('-');
        appendPadded(result, localDateTime.getMinute(), 2);
        result.append('-');
        appendPadded(result, localDateTime.getSecond(), 2);
        return result.toString();
    }

    /// Appends nonnegative `value` using at least `width` decimal digits.
    private static void appendPadded(StringBuilder result, int value, int width) {
        int divisor = 1;
        for (int index = 1; index < width; index++) {
            divisor *= 10;
        }
        while (divisor > value && divisor > 1) {
            result.append('0');
            divisor /= 10;
        }
        result.append(value);
    }

    /// Parses and applies one complete `-Xlog` argument.
    public static boolean parseCommandLineArgument(String argument) {
        VMOperation.guaranteeNotInProgress("Cannot reconfigure logging within a VM operation.");
        synchronized (LogConfiguration.class) {
            return parseCommandLineArgumentLocked(argument);
        }
    }

    /// Parses an argument while holding the configuration monitor.
    private static boolean parseCommandLineArgumentLocked(String argument) {
        HasXlogSupport.require();
        if (!argument.equals("-Xlog") && !argument.startsWith("-Xlog:")) {
            return false;
        }
        String options = argument.equals("-Xlog") ? "" : argument.substring("-Xlog:".length());
        if (options.equals("help")) {
            stdout.writePlain(HELP);
            return true;
        }
        if (options.equals("disable")) {
            disableLoggingLocked();
            return true;
        }

        List<String> parts = splitComponents(options);
        String selectionsText = component(parts, 0);
        String outputText = component(parts, 1);
        String decoratorsText = component(parts, 2);
        String outputOptions = component(parts, 3);

        LogSelectionList selections = LogSelectionList.parse(selectionsText);
        LogDecorators decorators = LogDecorators.parse(decoratorsText);
        LogOutput output = findOrCreateOutput(outputText);
        boolean initializeFileOutput = output instanceof LogFileOutput fileOutput && !fileOutput.isInitialized();
        try {
            if (!output.parseOptionsIfFirstConfiguration(outputOptions) && outputOptions != null && !outputOptions.isEmpty()) {
                warn("Output options for existing outputs are ignored.");
            }
            if (initializeFileOutput) {
                ((LogFileOutput) output).initialize();
            }
        } catch (RuntimeException | Error ex) {
            if (initializeFileOutput) {
                OUTPUTS.remove(output);
                output.close();
            }
            throw ex;
        }

        configureOutput(selections, output, decorators);
        warnUnmatchedSelections(selections);
        return true;
    }

    /// Applies `selections` and `decorators` to `output` while preserving concurrent log records.
    private static void configureOutput(LogSelectionList selections, LogOutput output, LogDecorators decorators) {
        LogDecorators transitionDecorators = output.decorators().union(decorators);
        boolean[] affectedTagSets = new boolean[LogTagSet.VALUES.length];
        for (LogTagSet tagSet : LogTagSet.VALUES) {
            LogLevel level = selections.levelFor(tagSet);
            boolean hasOutput = tagSet.outputList().levelFor(output) != LogLevel.OFF;
            boolean affected = hasOutput || level != null;
            affectedTagSets[tagSet.ordinal()] = affected;
            if (affected && level != LogLevel.OFF) {
                /* Capture every value required by either side of the configuration transition. */
                tagSet.updateDecorators(transitionDecorators);
            }
        }
        for (LogTagSet tagSet : LogTagSet.VALUES) {
            if (affectedTagSets[tagSet.ordinal()]) {
                tagSet.waitUntilNoReaders();
            }
        }
        try {
            output.setDecorators(decorators);
            for (LogTagSet tagSet : LogTagSet.VALUES) {
                if (affectedTagSets[tagSet.ordinal()]) {
                    LogLevel level = selections.levelFor(tagSet);
                    if (level != null) {
                        tagSet.outputList().setOutputLevel(output, level);
                    }
                    tagSet.updateDecorators();
                }
            }
        } finally {
            /* A failed reconfiguration must not leave future logging blocked. */
            for (LogTagSet tagSet : LogTagSet.VALUES) {
                if (affectedTagSets[tagSet.ordinal()]) {
                    tagSet.allowReaders();
                }
            }
        }
        output.updateConfigString();
    }

    /// Completes logging startup once by emitting initialization diagnostics and registering
    /// teardown when `-Xlog` is supported.
    public static synchronized void logInitializationComplete() {
        if (initializationComplete) {
            return;
        }
        if (HasXlogSupport.get()) {
            boolean loggingClassLoadCause = LogTagSet.class_load_cause.isLevel(LogLevel.INFO);
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
                for (LogFileOutput output : OUTPUTS) {
                    logging.info(describeOutput(index++, output));
                }
            }
            RuntimeSupport.getRuntimeSupport().addTearDownHook(_ -> LogConfiguration.tearDownLogging());
        }
        initializationComplete = true;
    }

    /// Removes every output configuration.
    public static void disableLogging() {
        // VMOperations must not block which make them incompatible
        // with the locking done while disabling logging.
        VMOperation.guaranteeNotInProgress("Cannot disable logging within a VM operation.");
        synchronized (LogConfiguration.class) {
            disableLoggingLocked();
        }
    }

    /// Releases all logging resources when isolate initialization cannot complete.
    public static void abortInitialization() {
        VMOperation.guaranteeNotInProgress("Cannot abort logging initialization within a VM operation.");
        synchronized (LogConfiguration.class) {
            disableLoggingLocked();
            initializationComplete = false;
        }
    }

    /// Disables all outputs while holding the configuration monitor.
    private static void disableLoggingLocked() {
        for (LogTagSet tagSet : LogTagSet.values()) {
            tagSet.outputList().clear();
        }
        try {
            waitUntilNoReaders();
            for (LogTagSet tagSet : LogTagSet.values()) {
                tagSet.updateDecorators();
            }
        } finally {
            /* Teardown and failed initialization must not strand a blocked logging thread. */
            for (LogTagSet tagSet : LogTagSet.values()) {
                tagSet.allowReaders();
            }
        }
        stdout.updateConfigString();
        stderr.updateConfigString();
        for (LogOutput output : OUTPUTS) {
            output.close();
        }
        OUTPUTS.clear();
        stdout.close();
        stderr.close();
    }

    /// Disables logging and releases file outputs before isolate teardown.
    private static void tearDownLogging() {
        synchronized (LogConfiguration.class) {
            disableLoggingLocked();
        }
    }

    /// Waits until all log sites have released configurations published before a routing update.
    private static void waitUntilNoReaders() {
        for (LogTagSet tagSet : LogTagSet.VALUES) {
            tagSet.waitUntilNoReaders();
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

    /// Gets the local startup timestamp cached before runtime properties are parsed.
    static String startupTimestamp() {
        return startupTimestamp;
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
        if (normalized.startsWith("#")) {
            return findOutputByIndex(normalized);
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
        String expandedFilename = LogFileOutput.expandFilename(filename);
        RawFileOperationSupport fileSupport = RawFileOperationSupport.nativeByteOrder();
        RawFilePath path = fileSupport.allocatePath(expandedFilename);
        if (path.isNull()) {
            throw new IllegalArgumentException("Could not allocate native path for unified log file '" + expandedFilename + "'.");
        }
        for (LogFileOutput output : OUTPUTS) {
            /*
             * After a successful open, output.path() is guaranteed to denote an existing file, so
             * sameFiles can recognize aliases. If opening failed, an identical raw path still
             * finds the output.
             */
            if (fileSupport.sameFiles(output.path(), path)) {
                UntrackedNullableNativeMemory.free(path);
                return output;
            }
        }
        LogFileOutput output = new LogFileOutput(filename, expandedFilename, path);
        OUTPUTS.add(output);
        return output;
    }

    /// Resolves the numeric identifiers emitted by the configuration description.
    private static LogOutput findOutputByIndex(String value) {
        int index;
        try {
            index = Integer.parseInt(value.substring(1));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid log output index '" + value + "'.", ex);
        }
        if (index == 0) {
            return stdout;
        }
        if (index == 1) {
            return stderr;
        }
        int fileIndex = index - 2;
        if (fileIndex >= 0 && fileIndex < OUTPUTS.size()) {
            return OUTPUTS.get(fileIndex);
        }
        throw new IllegalArgumentException("Invalid log output index '" + value + "'.");
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

    private static final String HELP = initHelp();

    @Platforms(Platform.HOSTED_ONLY.class)
    private static String initHelp() {
        var decorators = Stream.of(LogDecorators.VALUES) //
                        .map(d -> "%s (%s)".formatted(d.label(), d.abbreviation())) //
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

                Described tag sets:
                """ + describedTags +  """


                Available log outputs:
                 stdout/stderr
                 file=<filename>
                  If the filename contains %p, %i, %t and/or %hn, they will expand to the JVM's PID, isolate ID, startup timestamp and host name, respectively.

                Available log output options:
                 foldmultilines=.. - If set to true, a log event that consists of multiple lines will be folded into a single line by replacing newline characters with the sequence '\\' and 'n' in the output.
                 Existing single backslash characters will also be replaced with a sequence of two backslashes so that the conversion can be reversed. This option is safe to use with UTF-8 character encodings, \
                 but other encodings may not work.

                Additional file output options:
                 filesize=..       - Target byte size for log rotation (supports K/M/G suffix). If set to 0, log rotation is disabled.
                 filecount=..      - Number of files to keep in rotation (not counting the active file). If set to 0, log rotation is disabled. The active file is overwritten when logging starts.

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

                """;
        // @formatter:on
    }

    /// Renders the available levels, decorators, tags, and descriptions for startup diagnostics.
    private static final String[] AVAILABLE_DESCRIPTIONS = initAvailableDescriptions();

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

    private static final String AVAILABLE_TAG_SETS = "Available tag sets: " + //
                    Stream.of(LogTagSet.VALUES) //
                                    .map(LogTagSet::label) //
                                    .filter(s -> !s.isEmpty()) //
                                    .sorted() //
                                    .collect(Collectors.joining(", "));

    /// Describes one output and the thresholds currently assigned to its tag sets.
    private static String describeOutput(int index, LogOutput output) {
        return " #" + index + ": " + output.describe();
    }
}
