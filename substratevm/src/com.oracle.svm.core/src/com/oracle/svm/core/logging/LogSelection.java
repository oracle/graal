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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/// Represents one tag(s) and level selection such as `gc+ref*=debug`.
public final class LogSelection {
    /// Tags named by this selection.
    private final Set<LogTag> tags;

    /// A wildcard selects tag sets containing at least the named tags.
    private final boolean wildcard;

    /// Threshold installed on every matching tag set.
    private final LogLevel level;

    LogSelection(Set<LogTag> tags, boolean wildcard, LogLevel level) {
        this.tags = tags.isEmpty() ? EnumSet.noneOf(LogTag.class) : EnumSet.copyOf(tags);
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

        if (tagsText.equalsIgnoreCase("all")) {
            return new LogSelection(EnumSet.noneOf(LogTag.class), true, level);
        }
        boolean wildcard = tagsText.endsWith("*");
        if (wildcard) {
            tagsText = tagsText.substring(0, tagsText.length() - 1);
        }
        if (tagsText.isEmpty()) {
            throw new IllegalArgumentException("Missing log tags in selection '" + value + "'.");
        }

        EnumSet<LogTag> tags = EnumSet.noneOf(LogTag.class);
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
            if (!tags.add(tag)) {
                throw new IllegalArgumentException("Log selection contains duplicates of tag " + tag.label() + ".");
            }
        }
        if (tags.size() > 5) {
            throw new IllegalArgumentException("Log selections may contain at most five tags.");
        }
        return new LogSelection(tags, wildcard, level);
    }

    /// Adds the selection context while retaining the detailed parser diagnostic.
    private static IllegalArgumentException selectionError(String message, IllegalArgumentException cause) {
        String detail = cause.getMessage();
        return new IllegalArgumentException(detail == null ? message : message + " " + detail, cause);
    }

    /// Returns whether this selection selects `tagSet`.
    public boolean selects(LogTagSet tagSet) {
        Set<LogTag> candidate = tagSet.tagSet();
        return wildcard ? candidate.containsAll(tags) : candidate.equals(tags);
    }

    public LogLevel level() {
        return level;
    }

    public boolean wildcard() {
        return wildcard;
    }

    /// Gets the number of tags named by this selection.
    int tagCount() {
        return tags.size();
    }

    /// Returns whether this selection contains exactly `candidateTags`, ignoring wildcard mode.
    boolean consistsOf(Set<LogTag> candidateTags) {
        return tags.equals(candidateTags);
    }

    /// Appends this selection in the command-line configuration syntax.
    void describeOn(StringBuilder result) {
        boolean first = true;
        for (LogTag tag : tags) {
            if (!first) {
                result.append('+');
            }
            result.append(tag.label());
            first = false;
        }
        if (wildcard) {
            result.append('*');
        }
        result.append('=').append(level.label());
    }

    public Set<LogTag> tags() {
        return Set.copyOf(tags);
    }

    /// Finds the closest instantiated tag sets for an unmatched selection.
    List<String> suggestions() {
        List<LogTagSet> candidates = new ArrayList<>();
        for (LogTagSet tagSet : LogTagSet.values()) {
            if (!tagSet.tagSet().isEmpty() && !disjoint(tagSet.tagSet(), tags)) {
                candidates.add(tagSet);
            }
        }
        candidates.sort(Comparator.comparingInt((LogTagSet tagSet) -> overlap(tagSet.tagSet(), tags)).reversed().thenComparingInt(tagSet -> symmetricDifference(tagSet.tagSet(), tags)).thenComparing(
                        LogTagSet::label));
        return candidates.stream().limit(5).map(LogTagSet::label).toList();
    }

    private static boolean disjoint(Set<LogTag> left, Set<LogTag> right) {
        for (LogTag tag : left) {
            if (right.contains(tag)) {
                return false;
            }
        }
        return true;
    }

    private static int overlap(Set<LogTag> left, Set<LogTag> right) {
        int result = 0;
        for (LogTag tag : left) {
            if (right.contains(tag)) {
                result++;
            }
        }
        return result;
    }

    private static int symmetricDifference(Set<LogTag> left, Set<LogTag> right) {
        return left.size() + right.size() - 2 * overlap(left, right);
    }
}
