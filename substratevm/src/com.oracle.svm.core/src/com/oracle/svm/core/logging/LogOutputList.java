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

/// Tracks the destinations configured for one tag set.
///
/// The `outputsByLevel` array is indexed by message level. Each entry contains the
/// destinations whose threshold enables that level, in configuration order. For example, with
/// the default `stdout=WARNING` configuration and this command-line setting:
///
/// ```text
/// -Xlog:all=info:file=app.log
///
/// outputsByLevel[OFF]     = []
/// outputsByLevel[TRACE]   = []
/// outputsByLevel[DEBUG]   = []
/// outputsByLevel[INFO]    = [app.log]
/// outputsByLevel[WARNING] = [stdout, app.log]
/// outputsByLevel[ERROR]   = [stdout, app.log]
///
/// mostDetailedLevel       = INFO
/// ```
///
/// A single-line message is routed through the array for its line level. A multi-line message
/// uses its most severe line to select every destination that enables at least one line. Each
/// destination's configured threshold is then recovered from these arrays and used to filter
/// individual lines, without retaining a separate output-to-threshold map or scanning disabled
/// destinations during logging.
///
/// Readers retain one immutable configuration without locking. Runtime configuration publishes a
/// replacement configuration, while readers that already started continue to use the routing and
/// formatting state that they observed.
///
/// For example, consider the following message:
///
/// ```
/// try (LogMessage msg = LogTagSet.class_load.message()) {
///     msg.info().string("info message");
///     msg.debug().string("debug message");
/// }
/// ```
///
/// If `info.log` is configured at INFO and `debug.log` is configured at DEBUG, INFO is the most
/// severe line level, so `outputsFor(INFO)` selects both destinations. Filtering with each
/// destination's threshold produces:
///
/// ```text
/// info.log  = [info message]
/// debug.log = [info message, debug message]
/// ```
public final class LogOutputList {
    /// Shared empty result avoids allocation when a level is disabled.
    private static final LogOutputConfiguration[] NO_OUTPUTS = {};

    /// Complete routing and formatting state published with one volatile write.
    private volatile Configuration configuration = Configuration.empty();

    public LogLevel getMostDetailedLevel() {
        return configuration.mostDetailedLevel;
    }

    synchronized void setOutputLevel(LogOutputConfiguration outputConfiguration, LogLevel level) {
        Configuration current = configuration;
        LogOutput output = outputConfiguration.output();
        // Every non-off destination enables error messages, so this array preserves global order.
        LogOutputConfiguration[] configuredOutputs = current.outputsByLevel[LogLevel.ERROR.ordinal()];
        int outputOrder = indexOf(configuredOutputs, output);
        if (outputOrder < 0) {
            outputOrder = configuredOutputs.length;
        }

        LogOutputConfiguration[][] newOutputsByLevel = emptyOutputsByLevel();
        for (LogLevel messageLevel : LogLevel.VALUES) {
            if (messageLevel != LogLevel.OFF) {
                newOutputsByLevel[messageLevel.ordinal()] = updateOutputs(current.outputsByLevel[messageLevel.ordinal()], configuredOutputs, outputConfiguration, outputOrder,
                                level.enables(messageLevel));
            }
        }
        configuration = new Configuration(newOutputsByLevel);
    }

    synchronized void clear() {
        configuration = Configuration.empty();
    }

    boolean isLevel(LogLevel level) {
        return configuration.mostDetailedLevel.enables(level);
    }

    /// Gets the immutable routing configuration currently published for this tag set.
    Configuration configuration() {
        return configuration;
    }

    /// Gets the threshold configured for `output`, or [LogLevel#OFF] when it is disabled.
    LogLevel levelFor(LogOutput output) {
        return configuration.levelFor(output);
    }

    /// Updates one level's immutable output list while preserving configuration order.
    private static LogOutputConfiguration[] updateOutputs(LogOutputConfiguration[] currentOutputs, LogOutputConfiguration[] configuredOutputs, LogOutputConfiguration outputConfiguration,
                    int outputOrder, boolean enabled) {
        LogOutput output = outputConfiguration.output();
        int currentOutputIndex = indexOf(currentOutputs, output);
        int newLength = currentOutputs.length - (currentOutputIndex < 0 ? 0 : 1) + (enabled ? 1 : 0);
        if (newLength == 0) {
            return NO_OUTPUTS;
        }

        LogOutputConfiguration[] newOutputs = new LogOutputConfiguration[newLength];
        int insertIndex = 0;
        if (enabled) {
            for (LogOutputConfiguration currentOutput : currentOutputs) {
                if (currentOutput.output() != output && indexOf(configuredOutputs, currentOutput.output()) < outputOrder) {
                    insertIndex++;
                }
            }
        }
        int newOutputIndex = 0;
        for (LogOutputConfiguration currentOutput : currentOutputs) {
            if (currentOutput.output() == output) {
                continue;
            }
            if (enabled && newOutputIndex == insertIndex) {
                newOutputs[newOutputIndex++] = outputConfiguration;
            }
            newOutputs[newOutputIndex++] = currentOutput;
        }
        if (enabled && newOutputIndex < newOutputs.length) {
            newOutputs[newOutputIndex] = outputConfiguration;
        }
        return newOutputs;
    }

    /// Finds the most detailed message level with at least one configured destination.
    private static LogLevel findMostDetailedLevel(LogOutputConfiguration[][] outputsByLevel) {
        for (LogLevel messageLevel : LogLevel.VALUES) {
            if (messageLevel != LogLevel.OFF && outputsByLevel[messageLevel.ordinal()].length != 0) {
                return messageLevel;
            }
        }
        return LogLevel.OFF;
    }

    /// Finds `output` in the immutable output list by identity.
    private static int indexOf(LogOutputConfiguration[] outputs, LogOutput output) {
        for (int index = 0; index < outputs.length; index++) {
            if (outputs[index].output() == output) {
                return index;
            }
        }
        return -1;
    }

    private static LogOutputConfiguration[][] emptyOutputsByLevel() {
        LogOutputConfiguration[][] result = new LogOutputConfiguration[LogLevel.VALUES.length][];
        Arrays.fill(result, NO_OUTPUTS);
        return result;
    }

    /// One immutable copy-on-write routing configuration.
    static final class Configuration {
        private final LogOutputConfiguration[][] outputsByLevel;
        private final LogLevel mostDetailedLevel;
        private final LogDecorators decorators;

        private Configuration(LogOutputConfiguration[][] outputsByLevel) {
            this.outputsByLevel = outputsByLevel;
            this.mostDetailedLevel = findMostDetailedLevel(outputsByLevel);
            LogDecorators decoratorUnion = LogDecorators.NONE;
            for (LogOutputConfiguration output : outputsByLevel[LogLevel.ERROR.ordinal()]) {
                decoratorUnion = decoratorUnion.union(output.decorators());
            }
            this.decorators = decoratorUnion;
        }

        static Configuration empty() {
            return new Configuration(emptyOutputsByLevel());
        }

        LogOutputConfiguration[] outputsFor(LogLevel level) {
            return outputsByLevel[level.ordinal()];
        }

        LogLevel levelFor(LogOutput output) {
            for (int index = LogLevel.TRACE.ordinal(); index < LogLevel.VALUES.length; index++) {
                LogLevel level = LogLevel.VALUES[index];
                if (indexOf(outputsByLevel[level.ordinal()], output) >= 0) {
                    return level;
                }
            }
            return LogLevel.OFF;
        }

        LogDecorators decorators() {
            return decorators;
        }
    }
}
