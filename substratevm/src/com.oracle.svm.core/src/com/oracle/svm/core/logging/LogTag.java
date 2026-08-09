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

/// The supported log tags.
///
/// @see LogTagSetGenerator
public enum LogTag {
    // START GENERATED
    bytecode,
    cause,
    class_,
    dcmd,
    event,
    gc,
    image,
    init,
    jfr,
    load,
    logging,
    metadata,
    methodtrace,
    module,
    native_,
    oldobject,
    parser,
    periodic,
    safepoint,
    sampling,
    setting,
    start,
    startup,
    streaming,
    system,
    throttle;
    // END GENERATED

    /// External spelling retained when a Java keyword requires an escaped enum name.
    private final String label;

    LogTag() {
        String enumName = name();
        this.label = enumName.endsWith("_") ? enumName.substring(0, enumName.length() - 1) : enumName;
    }

    /// Gets the command-line spelling of this tag.
    public String label() {
        return label;
    }

    /// Finds a tag by its command-line spelling.
    public static LogTag fromString(String value) {
        for (LogTag tag : values()) {
            if (tag.label.equalsIgnoreCase(value)) {
                return tag;
            }
        }
        LogTag suggestion = closestTo(value);
        String suffix = suggestion == null ? "" : " Did you mean '" + suggestion.label + "'?";
        throw new IllegalArgumentException("Invalid log tag '" + value + "'." + suffix);
    }

    private static LogTag closestTo(String value) {
        LogTag result = null;
        double best = 0.5;
        for (LogTag tag : values()) {
            int maximumLength = Math.max(tag.label.length(), value.length());
            double score;
            score = maximumLength == 0 ? 1 : 1.0 - (double) LogLevel.editDistance(tag.label, value) / maximumLength;
            if (score >= best) {
                result = tag;
                best = score;
            }
        }
        return result;
    }
}
