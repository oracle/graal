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

import static com.oracle.svm.core.logging.LogDecorators.Decorator.UPTIME;
import static com.oracle.svm.core.logging.LogFileStreamOutput.Target.STDERR;
import static com.oracle.svm.core.logging.LogFileStreamOutput.Target.STDOUT;
import static com.oracle.svm.core.logging.LogFileStreamOutput.Target.VMLOG;
import static com.oracle.svm.core.logging.LogTagSet.logging;
import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.svm.core.LibCHelper;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFilePath;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.guest.staging.SubstrateGCOptions;
import com.oracle.svm.guest.staging.core.memory.UntrackedNullableNativeMemory;
import com.oracle.svm.guest.staging.jdk.RuntimeSupport;
import com.oracle.svm.guest.staging.option.NotifyGCRuntimeOptionKey;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.TimeUtils;

/// Owns the configuration for `-Xlog` and the GC logging fallback used when that command line
/// interface is unavailable. Runtime changes wait for active log sites and drain asynchronous
/// records before mutable output state is replaced.
public final class LogConfiguration {
    /// Preserves configuration insertion order for deterministic teardown and diagnostics.
    private static final List<LogFileOutput> OUTPUTS = new ArrayList<>();

    private static final LogFileStreamOutput stdout = new LogFileStreamOutput(STDOUT);

    private static final LogFileStreamOutput stderr = new LogFileStreamOutput(STDERR);

    /// Redirects fallback logging to `Log.log()`. This output is not selectable through `-Xlog`.
    private static final LogFileStreamOutput vmlog = new LogFileStreamOutput(VMLOG);

    /// Native host name.
    private static String hostname;

    /// Process identifier.
    private static long pid;

    /// Local startup timestamp used for every `%t` filename expansion.
    private static String startupTimestamp;

    /// Producer-facing publication and enabled state of asynchronous logging. Log sites enqueue
    /// only while this field is non-null. Deactivation clears it before draining so that no new
    /// producer can enter the queue while existing records are written.
    private static volatile LogAsyncWriter asyncWriter;

    /// Operational lifetime reference to the sole asynchronous writer created by the VM. Its
    /// daemon consumer must still access the queue after [#asyncWriter] has been cleared to stop new
    /// producers, so the writer's identity cannot be represented by that producer-facing field.
    /// Ordinary deactivation retains this reference so that a later activation can reuse the same
    /// writer and daemon. Failed startup clears it after terminating the daemon. VM teardown also
    /// terminates the daemon because an embedded VM must detach it before destroying its isolate.
    private static volatile LogAsyncWriter asyncWriterInstance;

    /// If true, async logging stalls on a full queue.
    /// If false, async logging drops messages on a full queue.
    private static boolean asyncStall;

    /// Records whether asynchronous output was requested.
    private static volatile boolean asyncRequested;

    /// Records whether startup option parsing and logging initialization have completed.
    private static volatile boolean initializationComplete;

    /// Prevents the legacy GC option updates used for synchronization from reconfiguring logging.
    private static volatile boolean synchronizingLegacyGCOptions;

    /// Counts VM operation log messages that required synchronous output because the async queue
    /// could not be used without blocking.
    private static final AtomicLong VM_OPERATION_SYNCHRONOUS_ENQUEUE_COUNT = new AtomicLong();

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

            LogLevel legacyLevel = legacyGCLogLevel();
            if (legacyLevel != LogLevel.OFF) {
                updateGCLoggingLocked(legacyLevel);
            }
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
        if (options.equals("async") || options.startsWith("async:")) {
            configureAsync(options);
            return true;
        }
        if (options.equals("disable")) {
            disableLoggingLocked(false);
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
        registerAsyncOutput(output);
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
            if (level != null) {
                tagSet.outputList().setOutputLevel(output, level);
            }
        }
        for (LogTagSet tagSet : LogTagSet.VALUES) {
            if (affectedTagSets[tagSet.ordinal()]) {
                tagSet.waitUntilNoReaders();
            }
        }
        try {
            drainAsyncWriter();
            output.setDecorators(decorators);
            for (LogTagSet tagSet : LogTagSet.VALUES) {
                tagSet.updateDecorators();
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
        synchronizeLegacyGCOptions();
        updateJfrLogLevels();
        if (initializationComplete) {
            initializeAsyncWriter();
        }
    }

    /// Completes logging startup. When `-Xlog` is supported, this starts requested asynchronous
    /// output, emits initialization diagnostics, and registers teardown. Otherwise it installs the
    /// legacy GC logging fallback.
    public static void logInitializationComplete() {
        if (HasXlogSupport.get()) {
            boolean loggingClassLoadCause = LogTagSet.class_load_cause.isLevel(LogLevel.INFO);
            if (loggingClassLoadCause && RuntimeClassLoading.Options.LogClassLoadingCauseFor.getValue() == null) {
                warn("Class load cause logging will not produce output without LogClassLoadingCauseFor.");
            }

            initializeAsyncWriter();
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
        } else {
            /*
             * Do the equivalent of -Xlog:gc=[debug|info]:vmlog:uptime
             * if VerboseGC or PrintGC is enabled.
             */
            LogLevel level = SubstrateGCOptions.VerboseGC.getValue() ? LogLevel.DEBUG : //
                            SubstrateGCOptions.PrintGC.getValue() ? LogLevel.INFO : null;
            if (level != null) {
                updateGCLogging(level);
            }
        }
        initializationComplete = true;
    }

    /// Sets the GC logging threshold to `level` on standard output when `-Xlog` is supported, or on
    /// the low-level VM log otherwise. The fallback preserves the uptime-only decoration used by
    /// legacy `VerboseGC` and `PrintGC` output.
    public static void updateGCLogging(LogLevel level) {
        VMOperation.guaranteeNotInProgress("Cannot reconfigure GC logging within a VM operation.");
        synchronized (LogConfiguration.class) {
            updateGCLoggingLocked(level);
        }
    }

    /// Updates the GC threshold while holding the configuration monitor.
    private static void updateGCLoggingLocked(LogLevel level) {
        boolean hasXlogSupport = HasXlogSupport.get();
        LogOutput output = hasXlogSupport ? stdout : vmlog;
        registerAsyncOutput(output);
        LogDecorators decorators = !hasXlogSupport && level != LogLevel.OFF ? new LogDecorators(UPTIME.bit()) : output.decorators();
        if (level != LogLevel.OFF) {
            /* A concurrent log site can safely observe either side of the transition. */
            LogTagSet.gc.updateDecorators(output.decorators().union(decorators));
        }
        /* Publish the formatting state before making a newly enabled output visible. */
        output.setDecorators(decorators);
        LogTagSet.gc.outputList().setOutputLevel(output, level);
        LogTagSet.gc.waitUntilNoReaders();
        try {
            drainAsyncWriter();
            LogTagSet.gc.updateDecorators();
        } finally {
            LogTagSet.gc.allowReaders();
        }
        if (hasXlogSupport) {
            stdout.updateConfigString();
        }
        synchronizeLegacyGCOptions();
        if (initializationComplete) {
            initializeAsyncWriter();
        }
    }

    /// Applies a direct update of `PrintGC` or `VerboseGC` to the GC log configuration.
    public static void legacyGCOptionValueChanged(NotifyGCRuntimeOptionKey<?> key) {
        if (key != SubstrateGCOptions.PrintGC && key != SubstrateGCOptions.VerboseGC) {
            return;
        }
        VMOperation.guaranteeNotInProgress("Cannot reconfigure GC logging within a VM operation.");
        synchronized (LogConfiguration.class) {
            if (!synchronizingLegacyGCOptions) {
                LogLevel level;
                if (key == SubstrateGCOptions.PrintGC && !SubstrateGCOptions.PrintGC.getValue()) {
                    level = LogLevel.OFF;
                } else {
                    level = legacyGCLogLevel();
                }
                updateGCLoggingLocked(level);
            }
        }
    }

    /// Returns whether `key` is a direct runtime legacy GC update that the collector must observe.
    /// During startup the native G1 argument parser has already processed the complete command
    /// line, and synchronization from `-Xlog` must not replace that richer configuration.
    public static boolean shouldForwardLegacyGCOptionToHeap(NotifyGCRuntimeOptionKey<?> key) {
        boolean legacyLoggingOption = key == SubstrateGCOptions.PrintGC || key == SubstrateGCOptions.VerboseGC;
        return !legacyLoggingOption || initializationComplete && !synchronizingLegacyGCOptions;
    }

    /// Derives the GC threshold represented by the legacy options.
    private static LogLevel legacyGCLogLevel() {
        return SubstrateGCOptions.VerboseGC.getValue() ? LogLevel.DEBUG : SubstrateGCOptions.PrintGC.getValue() ? LogLevel.INFO : LogLevel.OFF;
    }

    /// Mirrors the GC threshold on its legacy option compatibility surface.
    private static void synchronizeLegacyGCOptions() {
        LogOutput output = HasXlogSupport.get() ? stdout : vmlog;
        LogLevel level = LogTagSet.gc.outputList().levelFor(output);
        synchronizingLegacyGCOptions = true;
        try {
            if (SubstrateGCOptions.PrintGC.getValue() != level.enables(LogLevel.INFO)) {
                SubstrateGCOptions.PrintGC.update(level.enables(LogLevel.INFO));
            }
            if (SubstrateGCOptions.VerboseGC.getValue() != level.enables(LogLevel.DEBUG)) {
                SubstrateGCOptions.VerboseGC.update(level.enables(LogLevel.DEBUG));
            }
        } finally {
            synchronizingLegacyGCOptions = false;
        }
    }

    private static void updateJfrLogLevels() {
        if (HasJfrSupport.get()) {
            SubstrateJVM.getLogging().updateLogLevels();
        }
    }

    /// Flushes asynchronous records, reports fallback statistics, and removes every output
    /// configuration.
    public static void disableLogging() {
        // VMOperations must not block which make them incompatible
        // with the locking done while disabling logging.
        VMOperation.guaranteeNotInProgress("Cannot disable logging within a VM operation.");
        synchronized (LogConfiguration.class) {
            disableLoggingLocked(true);
        }
    }

    /// Releases all logging resources when isolate initialization cannot complete.
    public static void abortInitialization() {
        VMOperation.guaranteeNotInProgress("Cannot abort logging initialization within a VM operation.");
        synchronized (LogConfiguration.class) {
            disableLoggingLocked(true);
            initializationComplete = false;
            if (asyncWriterInstance != null) {
                asyncWriterInstance.shutdown();
                asyncWriterInstance = null;
            }
        }
    }

    /// Disables all outputs while holding the configuration monitor.
    private static void disableLoggingLocked(boolean resetAsyncRequest) {
        flushAsyncWriter();
        reportSynchronousEnqueuesFromVMOperations();
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
        synchronizeLegacyGCOptions();
        updateJfrLogLevels();
        if (resetAsyncRequest) {
            asyncRequested = false;
        }
        for (LogOutput output : OUTPUTS) {
            output.close();
        }
        OUTPUTS.clear();
        stdout.close();
        stderr.close();
    }

    /// Disables logging and terminates the VM-lifetime asynchronous consumer before isolate
    /// teardown waits for attached threads to exit.
    private static void tearDownLogging() {
        synchronized (LogConfiguration.class) {
            disableLoggingLocked(true);
            if (asyncWriterInstance != null) {
                asyncWriterInstance.shutdown();
            }
        }
    }

    /// Gets the active writer used to route log records asynchronously.
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static LogAsyncWriter asyncWriter() {
        return asyncWriter;
    }

    /// Gets the operational writer retained across ordinary asynchronous logging deactivation.
    static LogAsyncWriter asyncWriterInstance() {
        return asyncWriterInstance;
    }

    /// Parses the optional asynchronous logging mode from `options`.
    private static void configureAsync(String options) {
        String mode = options.length() == "async".length() ? "drop" : options.substring("async:".length());
        if (!mode.equals("drop") && !mode.equals("stall")) {
            throw new IllegalArgumentException("Invalid async logging mode '" + mode + "'. Expected 'drop' or 'stall'.");
        }
        asyncStall = mode.equals("stall");
        asyncRequested = true;
        if (initializationComplete) {
            /* Reactivation applies a changed drop or stall policy to the existing consumer. */
            flushAsyncWriter();
            initializeAsyncWriter();
        }
    }

    /// Starts or reactivates asynchronous output after the current configuration is ready.
    private static void initializeAsyncWriter() {
        if (asyncRequested && asyncWriter == null) {
            boolean startWriter = false;
            if (asyncWriterInstance == null) {
                asyncWriterInstance = new LogAsyncWriter();
                startWriter = true;
            }
            for (LogTagSet tagSet : LogTagSet.VALUES) {
                for (LogOutput output : tagSet.outputList().outputsFor(LogLevel.ERROR)) {
                    asyncWriterInstance.registerOutput(output);
                }
            }
            if (startWriter) {
                try {
                    asyncWriterInstance.start();
                } catch (RuntimeException | Error throwable) {
                    asyncWriterInstance.shutdown();
                    asyncWriterInstance = null;
                    throw throwable;
                }
            }
            asyncWriterInstance.activate(asyncStall);
            asyncWriter = asyncWriterInstance;
        }
    }

    /// Returns whether thread-start listeners should install asynchronous logging state.
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static boolean isAsyncLoggingRequested() {
        return asyncRequested;
    }

    /// Registers a destination before an active asynchronous route can publish its slot.
    private static void registerAsyncOutput(LogOutput output) {
        LogAsyncWriter writer = asyncWriter;
        if (writer != null) {
            writer.registerOutput(output);
        }
    }

    /// Drains records that precede a runtime configuration change without disabling async output.
    private static void drainAsyncWriter() {
        LogAsyncWriter writer = asyncWriter;
        if (writer != null) {
            LogAsyncWriter.flush();
        }
    }

    /// Stops publication and drains the writer before configured outputs are closed or reused.
    private static void flushAsyncWriter() {
        LogAsyncWriter writer = asyncWriter;
        if (writer != null) {
            asyncWriter = null;
            writer.deactivateAndFlush();
        }
    }

    /// Waits until all log sites have released configurations published before a routing update.
    private static void waitUntilNoReaders() {
        for (LogTagSet tagSet : LogTagSet.VALUES) {
            tagSet.waitUntilNoReaders();
        }
    }

    /// Records a VM operation log message that required synchronous output.
    static void recordSynchronousEnqueueFromVMOperation() {
        VM_OPERATION_SYNCHRONOUS_ENQUEUE_COUNT.incrementAndGet();
    }

    /// Reports and resets the number of VM operation log messages that required synchronous output
    /// because the asynchronous queue could not be used without blocking.
    private static void reportSynchronousEnqueuesFromVMOperations() {
        long count = VM_OPERATION_SYNCHRONOUS_ENQUEUE_COUNT.getAndSet(0);
        if (count != 0 && logging.isDebug()) {
            LogMessage message = logging.message();
            try {
                message.debug().string("VM operation log messages that used synchronous mode because the asynchronous queue was unavailable: ").unsigned(count);
            } finally {
                message.close();
            }
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
                  If the filename contains %p, %t and/or %hn, they will expand to the JVM's PID, startup timestamp and host name, respectively.

                Available log output options:
                 foldmultilines=.. - If set to true, a log event that consists of multiple lines will be folded into a single line by replacing newline characters with the sequence '\\' and 'n' in the output.
                 Existing single backslash characters will also be replaced with a sequence of two backslashes so that the conversion can be reversed. This option is safe to use with UTF-8 character encodings, \
                 but other encodings may not work.

                Additional file output options:
                 filesize=..       - Target byte size for log rotation (supports K/M/G suffix). If set to 0, log rotation is disabled.
                 filecount=..      - Number of files to keep in rotation (not counting the active file). If set to 0, log rotation is disabled. This will cause existing log files to be overwritten.

                Asynchronous logging (off by default):
                 -Xlog:async[:[mode]]
                  Log messages are written to an intermediate buffer first and will then be flushed to the corresponding log outputs by a standalone thread.
                 Messages produced by VM operations use synchronous output when they cannot enqueue without blocking.
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
