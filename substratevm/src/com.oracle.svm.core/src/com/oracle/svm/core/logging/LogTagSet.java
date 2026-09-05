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

import static com.oracle.svm.guest.staging.core.heap.RestrictHeapAccess.Access.NO_ALLOCATION;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.guest.staging.core.heap.RestrictHeapAccess;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.shared.AlwaysInline;
import com.oracle.svm.shared.util.VMError;

/// Represents a combination of tags for which messages can be logged.
/// Single-line messages are logged with [#info], [#trace], [#debug],
/// [#error] or [#warning]:
///
/// ```
/// LogTagSet.class_load.info(className + " loader=" + loaderDesc);
/// ```
///
/// Multi-line messages are logged by [#message]. See [LogMessage] for
/// more details.
///
/// @see LogTagSetGenerator
public enum LogTagSet {
    // START GENERATED
    class_init,
    class_load,
    class_load_cause,
    class_load_cause_native,
    class_load_image,
    gc,
    jfr,
    jfr_dcmd,
    jfr_event,
    jfr_metadata,
    jfr_methodtrace,
    jfr_oldobject_sampling,
    jfr_periodic,
    jfr_setting,
    jfr_start,
    jfr_startup,
    jfr_system,
    jfr_system_bytecode,
    jfr_system_event,
    jfr_system_metadata,
    jfr_system_parser,
    jfr_system_periodic,
    jfr_system_sampling,
    jfr_system_setting,
    jfr_system_streaming,
    jfr_system_throttle,
    logging,
    module_load,
    module_load_image,
    safepoint;
    // END GENERATED

    static final LogTagSet[] VALUES = LogTagSet.values();

    /// External selector spelling in HotSpot tag order.
    private final String label;

    /// Decoration spelling used when the `tags` decorator is enabled.
    private final String commaSeparatedLabel;

    /// Ordered tags preserve the instantiated `LogTagSetMapping` template arguments.
    private final List<LogTag> tags;

    /// Set form supports order-independent selector matching.
    private final EnumSet<LogTag> tagSet;

    /// Per-tag-set destination thresholds form the runtime configuration.
    private final LogOutputList outputList;

    /// Union of decorators configured on all active outputs for this tag set.
    private LogDecorators decorators = LogDecorators.NONE;

    /// Shared object for building a message for this tag set.
    private final LogMessage logMessage;

    @Platforms(Platform.HOSTED_ONLY.class)
    LogTagSet() {
        /* A builder can create multiple images with different unified logging option values. */
        outputList = new LogOutputList();
        logMessage = new LogMessage(this);
        String enumName = name();
        String derivedLabel;
        if (enumName.equals("_no_tag")) {
            derivedLabel = "";
        } else if (enumName.endsWith("_")) {
            derivedLabel = enumName.substring(0, enumName.length() - 1);
        } else {
            derivedLabel = enumName.replace('_', '+');
        }
        label = derivedLabel;
        commaSeparatedLabel = derivedLabel.replace('+', ',');
        if (derivedLabel.isEmpty()) {
            tags = List.of();
            tagSet = EnumSet.noneOf(LogTag.class);
        } else {
            tags = Arrays.stream(derivedLabel.split("\\+")).map(LogTag::fromString).toList();
            tagSet = EnumSet.copyOf(tags);
        }
        isGC = tagSet.contains(LogTag.gc);
    }

    private final boolean isGC;

    public void writePrefix(Log log) {
        if (isGC) {
            Heap.getHeap().getGC().writeLogPrefix(this, log);
        }
    }

    public String label() {
        return label;
    }

    public String commaSeparatedLabel() {
        return commaSeparatedLabel;
    }

    /// Gets the optional description for this tag set.
    String description() {
        return this == logging ? "Logging for the log framework itself" : null;
    }

    public List<LogTag> tags() {
        return tags;
    }

    Set<LogTag> tagSet() {
        return tagSet;
    }

    LogOutputList outputList() {
        return outputList;
    }

    /// Recomputes the decorator union from all currently active outputs.
    void updateDecorators() {
        LogDecorators updatedDecorators = LogDecorators.NONE;
        for (LogOutput output : outputList.outputsFor(LogLevel.ERROR)) {
            updatedDecorators = updatedDecorators.union(output.decorators());
        }
        decorators = updatedDecorators;
    }

    /// Returns whether `level` is enabled on any configured output.
    @AlwaysInline(HasULSupport.UL_CONDITIONAL)
    public boolean isLevel(LogLevel level) {
        return HasULSupport.get() && outputList.isLevel(level);
    }

    /// Returns whether trace messages are enabled on any output.
    @AlwaysInline(HasULSupport.UL_CONDITIONAL)
    public boolean isTrace() {
        return isLevel(LogLevel.TRACE);
    }

    /// Returns whether debug messages are enabled on any output.
    @AlwaysInline(HasULSupport.UL_CONDITIONAL)
    public boolean isDebug() {
        return isLevel(LogLevel.DEBUG);
    }

    /// Returns whether informational messages are enabled on any output.
    @AlwaysInline(HasULSupport.UL_CONDITIONAL)
    public boolean isInfo() {
        return isLevel(LogLevel.INFO);
    }

    /// Returns whether warning messages are enabled on any output.
    @AlwaysInline(HasULSupport.UL_CONDITIONAL)
    public boolean isWarning() {
        return isLevel(LogLevel.WARNING);
    }

    /// Returns whether error messages are enabled on any output.
    @AlwaysInline(HasULSupport.UL_CONDITIONAL)
    public boolean isError() {
        return isLevel(LogLevel.ERROR);
    }

    /// Writes one complete message to every output enabled for `level`.
    ///
    /// Within one thread, calls to this method for a specific tag set
    /// must not be made within the scope of an open message. That is,
    /// this must not be called from a thread that currently that has
    /// [started][LogMessage#line(LogLevel)] writing to this object's
    /// [message][#message()] and has not yet [closed][LogMessage#close()]
    /// the message.
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Unified logging must not allocate at run time.")
    public void log(LogLevel level, String message) {
        VMError.guarantee(HasULSupport.get());
        if (!isLevel(level)) {
            return;
        }
        LogMessage msg = message();
        // Cannot use try-with-resources here as it violates RestrictHeapAccess
        try {
            msg.line(level).string(message);
        } finally {
            msg.close();
        }
    }

    /// Gets the message facade for this tag set. Mutable event state is held by the current
    /// carrier thread. This must be used in a try-with-resources or try-finally statement as
    /// documented in [LogMessage].
    public LogMessage message() {
        VMError.guarantee(HasULSupport.get());
        LogThreadLocal.activate(this);
        return logMessage;
    }

    /// Writes one complete native memory message to every output enabled for one of its lines.
    void write(LogMessage message) {
        LogOutput[] outputs = outputList.outputsFor(message.getMostSevereLevel());
        LogAsyncWriter asyncWriter = LogConfiguration.asyncWriter();
        LogDecorations decorations = LogDecorations.capture(decorators);
        for (LogOutput output : outputs) {
            LogLevel outputLevel = outputList.levelFor(output);
            if (asyncWriter == null || !asyncWriter.enqueue(output, decorations, message, outputLevel)) {
                output.write(this, decorations, message, outputLevel);
            }
        }
    }

    /// Writes a trace message as one atomic event.
    ///
    /// Must not be called on a thread that has an open [#message()].
    /// See [#log] for more details.
    public void trace(String message) {
        log(LogLevel.TRACE, message);
    }

    /// Writes a debug message as one atomic event.
    ///
    /// Must not be called on a thread that has an open [#message()].
    /// See [#log] for more details.
    public void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    /// Writes an informational message as one atomic event.
    ///
    /// Must not be called on a thread that has an open [#message()].
    /// See [#log] for more details.
    public void info(String message) {
        log(LogLevel.INFO, message);
    }

    /// Writes a warning message as one atomic event.
    ///
    /// Must not be called on a thread that has an open [#message()].
    /// See [#log] for more details.
    public void warning(String message) {
        log(LogLevel.WARNING, message);
    }

    /// Writes an error message as one atomic event.
    ///
    /// Must not be called on a thread that has an open [#message()].
    /// See [#log] for more details.
    public void error(String message) {
        log(LogLevel.ERROR, message);
    }

    public LogLevel getMostDetailedLevel() {
        return outputList().getMostDetailedLevel();
    }
}
