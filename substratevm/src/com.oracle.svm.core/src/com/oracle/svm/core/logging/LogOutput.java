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

import static com.oracle.svm.core.logging.LogAsyncWriter.Options.AsyncLogBufferSize;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.shared.util.BasedOnJDKFile;

/// Base class for a configured log destination.
public abstract class LogOutput {
    /// Status bit indicating that the native write failed.
    protected static final int WRITE_FAILED = 1;

    /// Status bit indicating that rotating the old file name failed.
    protected static final int ROTATION_RENAME_FAILED = 2;

    /// Status bit indicating that reopening the active file failed.
    protected static final int ROTATION_OPEN_FAILED = 4;

    /// Configuration name used to identify repeated output arguments.
    private final String name;

    /// Compact command-line representation of the thresholds assigned to this output.
    private volatile String configString = "all=off";

    /// Largest value seen for each decorator keeps subsequent messages aligned.
    private final AtomicInteger[] decoratorPadding = createDecoratorPadding();

    /// Counts messages discarded before asynchronous delivery to this output while the shared
    /// queue was full.
    final AtomicInteger droppedAsyncMessages = new AtomicInteger();

    /// Formatting state used by configurations published after the latest reconfiguration.
    private volatile LogOutputConfiguration configuration;

    /// Controls whether newlines and backslashes are escaped onto one physical line.
    private volatile boolean foldMultilines;

    /// Prevents output options from being reapplied to an existing output.
    private boolean optionsConfigured;

    private static final NativeMemoryLog OUTPUT_BUFFER = new NativeMemoryLog(NativeMemoryLog.BufferKind.OUTPUT);

    private static final NativeMemoryLog DECORATOR_BUFFER = new NativeMemoryLog(NativeMemoryLog.BufferKind.DECORATOR);

    /// Tracks each output failure category that has already been reported.
    private final AtomicInteger reportedWriteErrors = new AtomicInteger();

    protected LogOutput(String name) {
        this.name = name;
        this.configuration = new LogOutputConfiguration(this, LogDecorators.DEFAULT);
    }

    private static AtomicInteger[] createDecoratorPadding() {
        AtomicInteger[] result = new AtomicInteger[LogDecorators.VALUES.length];
        for (int index = 0; index < result.length; index++) {
            result[index] = new AtomicInteger();
        }
        return result;
    }

    public String name() {
        return name;
    }

    /// Describes this output using the format used by HotSpot configuration diagnostics.
    String describe() {
        StringBuilder result = new StringBuilder(name).append(' ').append(configString);
        boolean hasDecorator = false;
        for (LogDecorators.Decorator decorator : LogDecorators.VALUES) {
            if (configuration.decorators().contains(decorator)) {
                result.append(hasDecorator ? ',' : ' ').append(decorator.label());
                hasDecorator = true;
            }
        }
        if (!hasDecorator) {
            result.append(" none");
        }
        return result.toString();
    }

    /// Creates and publishes immutable formatting state for new routes to this output.
    final LogOutputConfiguration configure(LogDecorators decorators) {
        LogOutputConfiguration newConfiguration = new LogOutputConfiguration(this, decorators);
        configuration = newConfiguration;
        return newConfiguration;
    }

    /// Gets the formatting state used by newly published routes.
    final LogOutputConfiguration configuration() {
        return configuration;
    }

    /// Reconstructs the compact threshold configuration from the current tag-set levels.
    void updateConfigString() {
        int[] onLevel = new int[LogLevel.VALUES.length];
        for (LogTagSet tagSet : LogTagSet.VALUES) {
            onLevel[tagSet.outputList().levelFor(this).ordinal()]++;
        }
        updateConfigString(onLevel);
    }

    /// Reconstructs the compact threshold configuration from per-level tag-set counts.
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+36/src/hotspot/share/logging/logOutput.cpp#L197-L322")
    void updateConfigString(int[] onLevel) {
        LogLevel mostCommonLevel = LogLevel.OFF;
        int maximum = onLevel[LogLevel.OFF.ordinal()];
        for (LogLevel level : LogLevel.VALUES) {
            if (level != LogLevel.OFF && onLevel[level.ordinal()] > maximum) {
                mostCommonLevel = level;
                maximum = onLevel[level.ordinal()];
            }
        }

        StringBuilder result = new StringBuilder("all=").append(mostCommonLevel.label());
        int deviatingTagSets = LogTagSet.VALUES.length - maximum;
        if (deviatingTagSets == 0) {
            configString = result.toString();
            return;
        }

        List<LogTagSet> deviates = new ArrayList<>(deviatingTagSets);
        List<LogSelection> selections = new ArrayList<>();
        for (LogTagSet tagSet : LogTagSet.VALUES) {
            LogLevel level = tagSet.outputList().levelFor(this);
            if (level != mostCommonLevel) {
                deviates.add(tagSet);
                addSelections(tagSet, level, selections);
            }
        }

        while (!deviates.isEmpty() && !selections.isEmpty()) {
            int previousDeviates = deviates.size();
            int maximumScore = 0;
            LogSelection bestSelection = selections.getFirst();
            for (LogSelection selection : selections) {
                int score = 0;
                for (LogTagSet tagSet : deviates) {
                    if (selection.selects(tagSet) && tagSet.outputList().levelFor(this) == selection.level()) {
                        score++;
                    }
                }
                if (score < maximumScore) {
                    continue;
                }
                for (LogTagSet tagSet : LogTagSet.VALUES) {
                    if (selection.selects(tagSet) && tagSet.outputList().levelFor(this) != selection.level()) {
                        score--;
                    }
                }
                if (score > maximumScore || (score == maximumScore && selection.tagCount() < bestSelection.tagCount())) {
                    maximumScore = score;
                    bestSelection = selection;
                }
            }

            result.append(',');
            bestSelection.describeOn(result);
            for (int index = 0; index < deviates.size();) {
                LogTagSet tagSet = deviates.get(index);
                if (tagSet.outputList().levelFor(this) == bestSelection.level() && bestSelection.selects(tagSet)) {
                    deviates.remove(index);
                } else {
                    index++;
                }
            }

            for (LogTagSet tagSet : LogTagSet.VALUES) {
                if (tagSet.outputList().levelFor(this) != bestSelection.level() && bestSelection.selects(tagSet) && !deviates.contains(tagSet)) {
                    deviates.add(tagSet);
                }
            }

            selections.clear();
            for (LogTagSet tagSet : deviates) {
                addSelections(tagSet, tagSet.outputList().levelFor(this), selections);
            }
            if (deviates.size() >= previousDeviates) {
                break;
            }
        }
        configString = result.toString();
    }

    /// Adds all useful exact and wildcard selections based on one tag set.
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+36/src/hotspot/share/logging/logOutput.cpp#L125-L195")
    private static void addSelections(LogTagSet tagSet, LogLevel level, List<LogSelection> selections) {
        if (tagSet.tagMask() == 0) {
            return;
        }
        LogTag[] tags = tagSet.tags();
        addSubsets(tags, 0, 0, level, selections);
    }

    /// Visits the subsets of `tags` from `index`, adding each non-empty subset to `selections`
    /// with `level` and using `tagMask` as the compact accumulator during recursion.
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+36/src/hotspot/share/logging/logOutput.cpp#L79-L123")
    private static void addSubsets(LogTag[] tags, int index, int tagMask, LogLevel level, List<LogSelection> selections) {
        if (index == tags.length) {
            if (tagMask == 0) {
                return;
            }
            addSelectionVariants(tagMask, level, selections);
            return;
        }
        addSubsets(tags, index + 1, tagMask, level, selections);
        addSubsets(tags, index + 1, tagMask | com.oracle.svm.shared.collections.EnumBitmask.flagBit(tags[index]), level, selections);
    }

    /// Adds exact and wildcard forms when they match an instantiated tag set.
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+36/src/hotspot/share/logging/logOutput.cpp#L141-L195")
    private static void addSelectionVariants(int tagMask, LogLevel level, List<LogSelection> selections) {
        for (LogSelection existing : selections) {
            if (existing.level() == level && existing.consistsOf(tagMask)) {
                return;
            }
        }
        LogSelection exact = new LogSelection(tagMask, false, level);
        if (matchesTagSet(exact)) {
            selections.add(exact);
        }
        LogSelection wildcard = new LogSelection(tagMask, true, level);
        if (matchesTagSet(wildcard)) {
            selections.add(wildcard);
        }
    }

    /// Returns whether a selection matches at least one instantiated tag set.
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+36/src/hotspot/share/logging/logOutput.cpp#L160-L175")
    private static boolean matchesTagSet(LogSelection selection) {
        for (LogTagSet tagSet : LogTagSet.VALUES) {
            if (selection.selects(tagSet)) {
                return true;
            }
        }
        return false;
    }

    /// Gets the decorators configured for this output.
    final LogDecorators decorators() {
        return configuration.decorators();
    }

    /// Parses output options only during the output's first configuration.
    final boolean parseOptionsIfFirstConfiguration(String options) {
        if (optionsConfigured) {
            return false;
        }
        boolean parsedFoldMultilines = foldMultilines;
        if (options != null && !options.isEmpty()) {
            for (String option : options.split(",", -1)) {
                int equals = option.indexOf('=');
                if (equals <= 0 || equals == option.length() - 1) {
                    throw new IllegalArgumentException("Invalid log output option '" + option + "'.");
                }
                String key = option.substring(0, equals);
                String value = option.substring(equals + 1);
                if (key.equals("foldmultilines")) {
                    if (!value.equals("true") && !value.equals("false")) {
                        throw new IllegalArgumentException("Invalid option: foldmultilines must be 'true' or 'false'.");
                    }
                    parsedFoldMultilines = Boolean.parseBoolean(value);
                } else if (!setOption(key, value)) {
                    throw new IllegalArgumentException("Invalid log output option '" + key + "'.");
                }
            }
        }
        /* Publish shared output state only after the complete option list was accepted. */
        foldMultilines = parsedFoldMultilines;
        optionsConfigured = true;
        return true;
    }

    /// Allows a concrete output to consume an output-specific option.
    protected boolean setOption(@SuppressWarnings("unused") String key, @SuppressWarnings("unused") String value) {
        return false;
    }

    /// Writes one complete message to this output.
    final void write(LogTagSet tagSet, LogDecorations decorations, LogMessage message, LogLevel outputLevel, LogDecorators configuredDecorators) {
        OUTPUT_BUFFER.reset();
        int lineCount = message.lineCount();
        boolean hasLine = false;
        for (int index = 0; index < lineCount; index++) {
            LogLevel lineLevel = message.lineLevel(index);
            if (outputLevel.enables(lineLevel)) {
                hasLine = true;
                int decoratorWidth = writeRecordPrefix(configuredDecorators, decorations, lineLevel, tagSet);
                message.writeLineTo(index, OUTPUT_BUFFER, foldMultilines, this, decoratorWidth);
                OUTPUT_BUFFER.newline();
            }
        }
        if (hasLine) {
            finishWrite();
        }
    }

    /// Writes one asynchronously queued message part using the copied event decorations.
    final void write(LogDecorations decorations, CCharPointer message, int messageLength, LogLevel level, LogDecorators configuredDecorators) {
        OUTPUT_BUFFER.reset();
        int decoratorWidth = writeDecorators(configuredDecorators, decorations, level);
        writeMessageBytes(message, messageLength, decoratorWidth);
        OUTPUT_BUFFER.newline();
        finishWrite();
    }

    /// Writes an untagged warning for messages dropped before asynchronous delivery to this output.
    final void writeDroppedAsyncMessages(int count) {
        OUTPUT_BUFFER.reset();
        LogDecorations decorations = LogDecorations.capture(LogDecorators.DROPPED_MESSAGE);
        writeDecorators(LogDecorators.DROPPED_MESSAGE, decorations, LogLevel.WARNING);
        Long asyncLogBufferSize = AsyncLogBufferSize.getValue();
        OUTPUT_BUFFER.unsigned(Integer.toUnsignedLong(count), 6, Log.RIGHT_ALIGN).string(" messages dropped due to async logging");
        if (asyncLogBufferSize < LogAsyncWriter.MAXIMUM_BUFFER_SIZE) {
            OUTPUT_BUFFER.string(" (try increasing ").string(AsyncLogBufferSize.getName()).string(")");
        }
        OUTPUT_BUFFER.newline();
        finishWrite();
    }

    private void finishWrite() {
        int status = writeRaw(OUTPUT_BUFFER.getBuffer(), Word.unsigned(OUTPUT_BUFFER.getPosition()));
        int unreported = claimUnreportedWriteErrors(status);
        if (unreported != 0) {
            if ((unreported & WRITE_FAILED) != 0) {
                Log.log().string("Could not write to log: ").string(name).newline();
            }
            if ((unreported & ROTATION_RENAME_FAILED) != 0) {
                Log.log().string("Could not rotate log file: ").string(name).newline();
            }
            if ((unreported & ROTATION_OPEN_FAILED) != 0) {
                Log.log().string("Could not reopen log file: ").string(name).newline();
            }
        }
        OUTPUT_BUFFER.reset();
    }

    /// Atomically claims failure categories that have not been diagnosed before.
    private int claimUnreportedWriteErrors(int status) {
        int reported;
        int unreported;
        do {
            reported = reportedWriteErrors.get();
            unreported = status & ~reported;
            if (unreported == 0) {
                return 0;
            }
        } while (!reportedWriteErrors.compareAndSet(reported, reported | unreported));
        return unreported;
    }

    /// Copies a queued message into the output buffer, applying its multiline policy.
    private void writeMessageBytes(CCharPointer message, int messageLength, int decoratorWidth) {
        for (int position = 0; position < messageLength; position++) {
            char value = (char) message.read(position);
            if (foldMultilines && value == '\\') {
                OUTPUT_BUFFER.character('\\').character('\\');
            } else if (foldMultilines && value == '\n') {
                OUTPUT_BUFFER.character('\\').character('n');
            } else if (!foldMultilines && value == '\n') {
                OUTPUT_BUFFER.newline();
                writeContinuationPrefix(decoratorWidth);
            } else {
                OUTPUT_BUFFER.character(value);
            }
        }
    }

    /// Writes [#decorators] to the thread-local output buffer and returns their bracketed width.
    /// Writes all metadata that precedes one physical log record.
    private int writeRecordPrefix(LogDecorators configuredDecorators, LogDecorations decorations, LogLevel level, LogTagSet tagSet) {
        int decoratorWidth = writeDecorators(configuredDecorators, decorations, level);
        tagSet.writePrefix(OUTPUT_BUFFER);
        return decoratorWidth;
    }

    /// Writes the blank marker that aligns an unfolded continuation with its first physical line.
    void writeContinuationPrefix(int decoratorWidth) {
        if (decoratorWidth != 0) {
            OUTPUT_BUFFER.character('[').spaces(decoratorWidth - 2).character(']').character(' ');
        }
    }

    /// Writes `enabledDecorators` using values captured in `decorations` and returns the combined
    /// width of their bracketed values, excluding the trailing separator.
    private int writeDecorators(LogDecorators enabledDecorators, LogDecorations decorations, LogLevel level) {
        int decoratorsLength = 0;
        boolean decorated = false;
        for (LogDecorators.Decorator decorator : LogDecorators.VALUES) {
            if (enabledDecorators.contains(decorator)) {
                decorated = true;
                OUTPUT_BUFFER.character('[');
                DECORATOR_BUFFER.reset();
                decorations.value(decorator, level, DECORATOR_BUFFER);
                int length = DECORATOR_BUFFER.getPosition();
                int index = decorator.ordinal();
                int padding;
                do {
                    padding = decoratorPadding[index].get();
                    if (padding >= length) {
                        break;
                    }
                } while (!decoratorPadding[index].compareAndSet(padding, length));
                padding = Math.max(padding, length);
                DECORATOR_BUFFER.writeTo(OUTPUT_BUFFER);
                OUTPUT_BUFFER.spaces(padding - length).character(']');
                decoratorsLength += padding + 2;
            }
        }
        if (decorated) {
            OUTPUT_BUFFER.character(' ');
        }
        return decoratorsLength;
    }

    /// Writes bytes already formatted in native memory by a `Log` operation.
    ///
    /// @return a bit mask describing failures encountered while writing or rotating the output:
    /// - `0` when the bytes were successfully written.
    /// - `WRITE_FAILED` when writing the bytes failed.
    /// - `ROTATION_RENAME_FAILED` when renaming an archived file failed during rotation.
    /// - `ROTATION_OPEN_FAILED` when reopening the active file failed after rotation.
    ///
    /// Multiple failures are combined with a bitwise OR, so callers can test each status bit
    /// independently.
    protected abstract int writeRaw(CCharPointer bytes, UnsignedWord length);

    /// Releases resources owned by this destination.
    public final void close() {
        closeOutput();
    }

    /// Releases output-specific resources owned by this destination.
    protected void closeOutput() {
    }
}
