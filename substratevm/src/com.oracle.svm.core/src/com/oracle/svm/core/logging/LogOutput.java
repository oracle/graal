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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.guest.staging.log.Log;

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
    private String configString = "all=off";

    /// Largest value seen for each decorator keeps subsequent messages aligned.
    private final AtomicInteger[] decoratorPadding = createDecoratorPadding();

    /// Decorators enabled for this destination.
    private LogDecorators decorators = LogDecorators.DEFAULT;

    /// Controls whether newlines and backslashes are escaped onto one physical line.
    private boolean foldMultilines;

    /// Prevents output options from being reapplied to an existing output.
    private boolean optionsConfigured;

    private static final NativeMemoryLog OUTPUT_BUFFER = new NativeMemoryLog(NativeMemoryLog.BufferKind.OUTPUT);

    private static final NativeMemoryLog DECORATOR_BUFFER = new NativeMemoryLog(NativeMemoryLog.BufferKind.DECORATOR);

    private final AtomicBoolean writeErrorIsShown = new AtomicBoolean();

    protected LogOutput(String name) {
        this.name = name;
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
            if (decorators.contains(decorator)) {
                result.append(hasDecorator ? ',' : ' ').append(decorator.label());
                hasDecorator = true;
            }
        }
        if (!hasDecorator) {
            result.append(" none");
        }
        return result.toString();
    }

    protected final void setDecorators(LogDecorators decorators) {
        this.decorators = decorators;
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
    private static void addSelections(LogTagSet tagSet, LogLevel level, List<LogSelection> selections) {
        List<LogTag> tags = tagSet.tags();
        if (tags.isEmpty()) {
            return;
        }
        addSubsets(tags, 0, EnumSet.noneOf(LogTag.class), level, selections);
    }

    /// Visits the subsets of `tags` from `index`, adding each non-empty subset to `selections`
    /// with `level` and using `subset` as the mutable accumulator during recursion.
    private static void addSubsets(List<LogTag> tags, int index, EnumSet<LogTag> subset, LogLevel level, List<LogSelection> selections) {
        if (index == tags.size()) {
            if (subset.isEmpty()) {
                return;
            }
            addSelectionVariants(subset, level, selections);
            return;
        }
        addSubsets(tags, index + 1, subset, level, selections);
        subset.add(tags.get(index));
        addSubsets(tags, index + 1, subset, level, selections);
        subset.remove(tags.get(index));
    }

    /// Adds exact and wildcard forms when they match an instantiated tag set.
    private static void addSelectionVariants(Set<LogTag> subset, LogLevel level, List<LogSelection> selections) {
        for (LogSelection existing : selections) {
            if (existing.level() == level && existing.consistsOf(subset)) {
                return;
            }
        }
        LogSelection exact = new LogSelection(subset, false, level);
        if (matchesTagSet(exact)) {
            selections.add(exact);
        }
        LogSelection wildcard = new LogSelection(subset, true, level);
        if (matchesTagSet(wildcard)) {
            selections.add(wildcard);
        }
    }

    /// Returns whether a selection matches at least one instantiated tag set.
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
        return decorators;
    }

    /// Parses output options only during the output's first configuration.
    final boolean parseOptionsIfFirstConfiguration(String options) {
        if (optionsConfigured) {
            return false;
        }
        optionsConfigured = true;
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
                    foldMultilines = Boolean.parseBoolean(value);
                } else if (!setOption(key, value)) {
                    throw new IllegalArgumentException("Invalid log output option '" + key + "'.");
                }
            }
        }
        return true;
    }

    /// Allows a concrete output to consume an output-specific option.
    protected boolean setOption(@SuppressWarnings("unused") String key, @SuppressWarnings("unused") String value) {
        return false;
    }

    /// Writes one complete message to this output.
    final void write(LogTagSet tagSet, LogDecorations decorations, LogMessage message, LogLevel outputLevel) {
        OUTPUT_BUFFER.reset();
        int lineCount = message.lineCount();
        boolean hasLine = false;
        for (int index = 0; index < lineCount; index++) {
            LogLevel lineLevel = message.lineLevel(index);
            if (outputLevel.enables(lineLevel)) {
                hasLine = true;
                int decoratorsLength = writeDecorators(decorations, lineLevel);
                tagSet.writePrefix(OUTPUT_BUFFER);
                message.writeLineTo(index, OUTPUT_BUFFER, foldMultilines, decoratorsLength);
                OUTPUT_BUFFER.newline();
            }
        }
        if (hasLine) {
            finishWrite();
        }
    }

    /// Writes one asynchronously queued message part using the copied event decorations.
    final void write(LogDecorations decorations, NativeMemoryLog message, LogLevel level) {
        OUTPUT_BUFFER.reset();
        int decoratorsLength = writeDecorators(decorations, level);
        decorations.getTagSet().writePrefix(OUTPUT_BUFFER);
        writeMessageBytes(message, decoratorsLength);
        OUTPUT_BUFFER.newline();
        finishWrite();
    }

    private void finishWrite() {
        int status = writeRaw(OUTPUT_BUFFER.getBuffer(), Word.unsigned(OUTPUT_BUFFER.getPosition()));
        if (status != 0 && writeErrorIsShown.compareAndSet(false, true)) {
            if ((status & WRITE_FAILED) != 0) {
                Log.log().string("Could not write to log: ").string(name).newline();
            }
            if ((status & ROTATION_RENAME_FAILED) != 0) {
                Log.log().string("Could not rotate log file: ").string(name).newline();
            }
            if ((status & ROTATION_OPEN_FAILED) != 0) {
                Log.log().string("Could not reopen log file: ").string(name).newline();
            }
        }
        OUTPUT_BUFFER.reset();
    }

    /// Copies a queued message into the output buffer, applying its multiline policy.
    private void writeMessageBytes(NativeMemoryLog message, int prefixLength) {
        for (int position = 0; position < message.getPosition(); position++) {
            char value = (char) message.getBuffer().read(position);
            if (foldMultilines && value == '\\') {
                OUTPUT_BUFFER.character('\\').character('\\');
            } else if (foldMultilines && value == '\n') {
                OUTPUT_BUFFER.character('\\').character('n');
            } else if (!foldMultilines && value == '\n') {
                OUTPUT_BUFFER.newline();
                if (prefixLength != 0) {
                    OUTPUT_BUFFER.character('[').spaces(prefixLength - 3).string("] ");
                }
            } else {
                OUTPUT_BUFFER.character(value);
            }
        }
    }

    /// Writes [#decorators] to the thread-local output buffer and returns their display width.
    private int writeDecorators(LogDecorations decorations, LogLevel level) {
        int decoratorsLength = 0;
        boolean decorated = false;
        for (LogDecorators.Decorator decorator : LogDecorators.VALUES) {
            if (decorators.contains(decorator)) {
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
            decoratorsLength++;
        }
        return decoratorsLength;
    }

    /// Writes bytes already formatted by an allocation-free `Log` operation.
    ///
    /// @return a bit mask describing failures encountered while writing or rotating the output:
    /// - `0` when the bytes were successfully written or this is a file output whose
    /// file descriptor was 0 prior to this due to an earlier error.
    /// - `WRITE_FAILED` when writing the bytes failed.
    /// - `ROTATION_RENAME_FAILED` when renaming an archived file failed during rotation.
    /// - `ROTATION_OPEN_FAILED` when reopening the active file failed after rotation.
    ///
    /// Multiple failures are combined with a bitwise OR, so callers can test each status bit
    /// independently.
    protected abstract int writeRaw(CCharPointer bytes, UnsignedWord length);

    /// Releases the native buffers retained while formatting log output.
    public final void close() {
        closeOutput();
    }

    /// Releases output-specific resources owned by this destination.
    protected void closeOutput() {
    }
}
