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

import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.collections.EnumBitmask;

/// Represents a combination of tags for which complete messages can be logged.
/// Call sites put the folded [HasXlogSupport#get()] check before the level predicate so image
/// analysis can discard logging infrastructure and message preparation when support is absent.
/// Any preparation that may allocate stays after these guards but outside the uninterruptible
/// `log*` method that owns message construction and synchronous delivery:
///
/// ```java
/// private static void classLoaded(String className, String loaderDescription) {
///     if (HasXlogSupport.get() && LogTagSet.class_load.isInfo()) {
///         logClassLoad(className, loaderDescription);
///     }
/// }
///
/// @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_OWNER)
/// private static void logClassLoad(String className, String loaderDescription) {
///     LogMessage message = LogTagSet.class_load.message();
///     try {
///         message.info().string(className).string(" loader: ").string(loaderDescription);
///     } finally {
///         message.close();
///     }
/// }
/// ```
///
/// Appending components directly to [com.oracle.svm.core.log.NativeMemoryBuffer] avoids intermediate
/// Java strings. Both single-line and multi-line messages use [#message()]; additional calls to
/// [LogMessage#line(LogLevel)] or its level-specific helpers start further lines in the same scope.
/// The message facade is shared, while mutable state belongs to the current carrier thread.
/// Nested scopes, including scopes for different tag sets, are unsupported.
///
/// The owner uses plain [Uninterruptible] with its default options. Its transitive compiler checks
/// forbid Java heap allocation, safepoints, and runtime class initialization during formatting and
/// delivery. Runtime logging therefore uses only VM-internal classes and JDK classes initialized
/// at build time. An explicit try/finally closes the scope because try-with-resources introduces
/// interruptible suppressed-exception handling. See [LogMessage] for the scope contract.
///
/// Level predicates and message scopes require completed logging initialization, even when all
/// outputs are disabled. Neither formatting nor output delegates to legacy log handlers; legacy
/// GC logging uses a separate adapter rather than a fallback route through this API.
/// Logging can read Java heap objects, so producers must keep it outside collector regions that
/// move or scan those objects.
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

    /// Enforces allocation-free message construction and synchronous delivery without safepoints.
    public static final String LOG_MESSAGE_OWNER = "Logging must not allocate or safepoint.";

    /// Carrier-owned state and native pointers must remain within the caller's uninterruptible region.
    public static final String LOG_MESSAGE_ACCESS = "Carrier-owned logging state requires an uninterruptible caller.";

    static final LogTagSet[] VALUES = LogTagSet.values();

    /// External selector spelling in HotSpot tag order.
    private final String label;

    /// Decoration spelling used when the `tags` decorator is enabled.
    private final String commaSeparatedLabel;

    /// Component tags in the order of the selector spelling, or `null` for an empty tag set.
    private final LogTag[] tags;

    /// Bitmask form supports compact order-independent selector matching.
    private final int tagMask;

    /// Per-tag-set destination thresholds form the runtime configuration.
    private final LogOutputList outputList;

    /// Shared object for building a message for this tag set.
    private final LogMessage logMessage;

    @Platforms(Platform.HOSTED_ONLY.class)
    LogTagSet() {
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
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public String label() {
        return label;
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
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

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    LogOutputList outputList() {
        return outputList;
    }

    /// Returns whether `level` is enabled on a configured output.
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isLevel(LogLevel level) {
        LogConfiguration.guaranteeInitialized();
        return outputList.isLevel(level);
    }

    /// Returns whether trace messages are enabled on any output.
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isTrace() {
        return isLevel(LogLevel.TRACE);
    }

    /// Returns whether debug messages are enabled on any output.
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isDebug() {
        return isLevel(LogLevel.DEBUG);
    }

    /// Returns whether informational messages are enabled on any output.
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isInfo() {
        return isLevel(LogLevel.INFO);
    }

    /// Returns whether warning messages are enabled on any output.
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isWarning() {
        return isLevel(LogLevel.WARNING);
    }

    /// Returns whether error messages are enabled on any output.
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isError() {
        return isLevel(LogLevel.ERROR);
    }

    /// Gets the immutable facade for a producer adapter installed during image building.
    @Platforms(Platform.HOSTED_ONLY.class)
    public LogMessage messageFacade() {
        return logMessage;
    }

    /// Opens a scope owned by an uninterruptible `log*` method. Its try/finally must close the
    /// message before returning; allocating preparation stays outside that owner.
    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_ACCESS, callerMustBe = true)
    public LogMessage message() {
        LogConfiguration.guaranteeInitialized();
        LogThreadLocal.activate();
        return logMessage;
    }

    /// Writes one complete native memory message to every output enabled for one of its lines.
    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_ACCESS, callerMustBe = true)
    void write() {
        LogConfiguration.guaranteeInitialized();
        if (!LogConfiguration.beginWrite()) {
            return;
        }
        try {
            LogOutputList.Configuration configuration = outputList.configuration();
            LogOutputConfiguration[] outputs = configuration.outputsFor(LogMessage.getMostSevereLevel());
            LogDecorations.capture(configuration.decorators());
            for (LogOutputConfiguration outputConfiguration : outputs) {
                LogOutput output = outputConfiguration.output();
                LogLevel outputLevel = configuration.levelFor(output);
                output.write(this, outputLevel, outputConfiguration.decorators());
            }
        } finally {
            LogConfiguration.endWrite();
        }
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public LogLevel getMostDetailedLevel() {
        return outputList().getMostDetailedLevel();
    }
}
