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
import java.util.Comparator;
import java.util.List;

import com.oracle.svm.shared.collections.EnumBitmask;

/// Represents one selection of tags and a level, such as `class+load*=debug`.
public final class LogSelection {
    /// Tags named by this selection.
    private final int tagMask;

    /// A wildcard selects tag sets containing at least the named tags.
    private final boolean wildcard;

    /// Threshold installed on every matching tag set.
    private final LogLevel level;

    LogSelection(int tagMask, boolean wildcard, LogLevel level) {
        this.tagMask = tagMask;
        this.wildcard = wildcard;
        this.level = level;
    }

    /// Parses one selection from a command-line configuration.
    public static LogSelection parse(String value) {
        String tagsText = value;
        LogLevel level = LogLevel.INFO;
        int equals = value.indexOf('=');
        if (equals >= 0) {
            tagsText = value.substring(0, equals);
            String levelText = value.substring(equals + 1);
            if (levelText.isEmpty()) {
                throw new IllegalArgumentException("Missing log level in selection '" + value + "'.");
            }
            try {
                level = LogLevel.fromString(levelText);
            } catch (IllegalArgumentException ex) {
                throw selectionError("Invalid level '" + levelText + "' in log selection '" + value + "'.", ex);
            }
        }

        if (tagsText.equals("all")) {
            return new LogSelection(0, true, level);
        }
        boolean wildcard = tagsText.endsWith("*");
        if (wildcard) {
            tagsText = tagsText.substring(0, tagsText.length() - 1);
        }
        if (tagsText.isEmpty()) {
            throw new IllegalArgumentException("Missing log tags in selection '" + value + "'.");
        }

        int tagMask = 0;
        for (String tagText : tagsText.split("\\+", -1)) {
            if (tagText.isEmpty()) {
                throw new IllegalArgumentException("Invalid empty tag in selection '" + value + "'.");
            }
            LogTag tag;
            try {
                tag = LogTag.fromString(tagText);
            } catch (IllegalArgumentException ex) {
                throw selectionError("Invalid tag '" + tagText + "' in log selection '" + value + "'.", ex);
            }
            int tagBit = EnumBitmask.flagBit(tag);
            if ((tagMask & tagBit) != 0) {
                throw new IllegalArgumentException("Log selection contains duplicates of tag " + tag.label() + ".");
            }
            tagMask |= tagBit;
        }
        if (Integer.bitCount(tagMask) > 5) {
            throw new IllegalArgumentException("Log selections may contain at most five tags.");
        }
        return new LogSelection(tagMask, wildcard, level);
    }

    /// Adds the selection context while retaining the detailed parser diagnostic.
    private static IllegalArgumentException selectionError(String message, IllegalArgumentException cause) {
        String detail = cause.getMessage();
        return new IllegalArgumentException(detail == null ? message : message + " " + detail, cause);
    }

    /// Returns whether this selection selects `tagSet`.
    public boolean selects(LogTagSet tagSet) {
        int candidateMask = tagSet.tagMask();
        return wildcard ? (candidateMask & tagMask) == tagMask : candidateMask == tagMask;
    }

    public LogLevel level() {
        return level;
    }

    public boolean wildcard() {
        return wildcard;
    }

    /// Gets the number of tags named by this selection.
    int tagCount() {
        return Integer.bitCount(tagMask);
    }

    /// Returns whether this selection contains exactly `candidateTagMask`, ignoring wildcard mode.
    boolean consistsOf(int candidateTagMask) {
        return tagMask == candidateTagMask;
    }

    /// Appends this selection in the command-line configuration syntax.
    void describeOn(StringBuilder result) {
        boolean first = true;
        for (LogTag tag : LogTag.values()) {
            if (EnumBitmask.hasBit(tagMask, tag)) {
                if (!first) {
                    result.append('+');
                }
                result.append(tag.label());
                first = false;
            }
        }
        if (wildcard) {
            result.append('*');
        }
        result.append('=').append(level.label());
    }

    /// Finds the closest instantiated tag sets for an unmatched selection.
    List<String> suggestions() {
        List<LogTagSet> candidates = new ArrayList<>();
        for (LogTagSet tagSet : LogTagSet.values()) {
            if (tagSet.tagMask() != 0 && (tagSet.tagMask() & tagMask) != 0) {
                candidates.add(tagSet);
            }
        }
        candidates.sort(Comparator.comparingInt((LogTagSet tagSet) -> Integer.bitCount(tagSet.tagMask() & tagMask)).reversed()
                        .thenComparingInt(tagSet -> Integer.bitCount(tagSet.tagMask() ^ tagMask)).thenComparing(LogTagSet::label));
        return candidates.stream().limit(5).map(LogTagSet::label).toList();
    }
}
