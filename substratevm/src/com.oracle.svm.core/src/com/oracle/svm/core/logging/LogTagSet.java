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

import java.util.Arrays;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.shared.collections.EnumBitmask;
import com.oracle.svm.shared.util.VMError;

/// Represents a combination of tags for which messages can be logged.
/// Code that prepares a message for a tag set other than `gc` must put the folded
/// `HasXlogSupport.get()` check before the level predicate and write the message through
/// [#message()]:
///
/// ```
/// if (HasXlogSupport.get() && LogTagSet.class_load.isInfo()) {
///     try (LogMessage message = LogTagSet.class_load.message()) {
///         message.info().string(className).string(" loader=").string(loaderDesc);
///     }
/// }
/// ```
///
/// Keeping the support check visible at the call site lets image analysis discard the guarded
/// code, including message construction, when `-Xlog` support is unavailable. Writing values
/// directly to the message's [NativeMemoryLog] also avoids intermediate string allocation. These
/// properties keep the minimal image size small and let allocation-restricted callers construct
/// messages without Java heap allocation. Such callers must use `try`-`finally` as documented in
/// [LogMessage]. The `gc` tag set deliberately omits the support check because legacy GC logging
/// remains available without `-Xlog` support.
///
/// Both single-line and multi-line messages are logged by [#message()].
///
/// In an image without `-Xlog` support, the `gc` tag set can be routed to the low-level VM log by
/// the legacy `VerboseGC` and `PrintGC` options. The same level predicates and message APIs apply
/// to configured and fallback routes.
///
/// Runtime logging is VM-internal infrastructure. Emitting a message must either succeed or
/// terminate with a fatal VM error; it must not let an ordinary exception escape into its caller.
/// Any code reachable while emitting a message may therefore use only VM-internal classes or JDK
/// classes that are guaranteed to be initialized at build time. Otherwise, logging during class
/// initialization could trigger another class initialization, recursively reenter logging, and
/// cause incorrect behavior or a deadlock.
///
/// @see LogTagSetGenerator
public enum LogTagSet {
    // START GENERATED
    class_init,
    class_load,
    class_load_cause,
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

    /// Ordered tags preserve the instantiated `LogTagSetMapping` template arguments, or are null
    /// for the no-tag set.
    private final LogTag[] tags;

    /// Bitmask form supports compact order-independent selector matching.
    private final int tagMask;

    /// Per-tag-set destination thresholds form the runtime configuration.
    private final LogOutputList outputList;

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
            /* The no-tag set has no ordered representation to retain. */
            tags = null;
        } else {
            tags = Arrays.stream(derivedLabel.split("\\+")).map(LogTag::fromString).toArray(LogTag[]::new);
        }
        tagMask = tags == null ? 0 : EnumBitmask.computeBitmask(tags);
        isGC = EnumBitmask.hasBit(tagMask, LogTag.gc);
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

    LogTag[] tags() {
        return tags;
    }

    int tagMask() {
        return tagMask;
    }

    LogOutputList outputList() {
        return outputList;
    }

    /// Returns whether `level` is enabled on any configured or fallback output.
    public boolean isLevel(LogLevel level) {
        VMError.guarantee(isGC || HasXlogSupport.get(), "Only GC logging is available without -Xlog support.");
        return outputList.isLevel(level);
    }

    /// Returns whether trace messages are enabled on any output.
    public boolean isTrace() {
        return isLevel(LogLevel.TRACE);
    }

    /// Returns whether debug messages are enabled on any output.
    public boolean isDebug() {
        return isLevel(LogLevel.DEBUG);
    }

    /// Returns whether informational messages are enabled on any output.
    public boolean isInfo() {
        return isLevel(LogLevel.INFO);
    }

    /// Returns whether warning messages are enabled on any output.
    public boolean isWarning() {
        return isLevel(LogLevel.WARNING);
    }

    /// Returns whether error messages are enabled on any output.
    public boolean isError() {
        return isLevel(LogLevel.ERROR);
    }

    /// Opens a message for this tag set. This must be used in a try-with-resources or try-finally
    /// statement as documented in [LogMessage].
    public LogMessage message() {
        LogThreadLocal.activate(this);
        return logMessage;
    }

    /// Writes one complete native memory message to every output enabled for one of its lines.
    void write(LogMessage message) {
        LogOutputList.Configuration configuration = outputList.configuration();
        LogOutputConfiguration[] outputs = configuration.outputsFor(message.getMostSevereLevel());
        LogAsyncWriter asyncWriter = LogConfiguration.asyncWriter();
        LogDecorations decorations = LogDecorations.capture(configuration.decorators());
        boolean recordedVMOperationFallback = false;
        for (LogOutputConfiguration outputConfiguration : outputs) {
            LogOutput output = outputConfiguration.output();
            LogLevel outputLevel = configuration.levelFor(output);
            if (asyncWriter == null || !asyncWriter.enqueue(outputConfiguration, decorations, message, outputLevel)) {
                if (asyncWriter != null && com.oracle.svm.core.thread.VMOperationControl.mayExecuteVmOperations() && !recordedVMOperationFallback) {
                    LogConfiguration.recordSynchronousEnqueueFromVMOperation();
                    recordedVMOperationFallback = true;
                }
                output.write(this, decorations, message, outputLevel, outputConfiguration.decorators());
            }
        }
    }

    public LogLevel getMostDetailedLevel() {
        return outputList().getMostDetailedLevel();
    }
}
