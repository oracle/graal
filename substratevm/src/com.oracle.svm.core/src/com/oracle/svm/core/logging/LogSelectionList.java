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
import java.util.List;

/// Holds the ordered selections from one `-Xlog` argument.
public final class LogSelectionList {
    /// Command-line order is significant because the final matching selection wins.
    private final List<LogSelection> selections;

    private LogSelectionList(List<LogSelection> selections) {
        this.selections = List.copyOf(selections);
    }

    /// Parses a comma-separated selection list.
    public static LogSelectionList parse(String value) {
        String effective = value == null || value.isEmpty() ? "all=info" : value;
        List<LogSelection> result = new ArrayList<>();
        for (String selection : effective.split(",", -1)) {
            if (selection.isEmpty()) {
                throw new IllegalArgumentException("Invalid empty log selection.");
            }
            result.add(LogSelection.parse(selection));
        }
        return new LogSelectionList(result);
    }

    /// Gets the final level selected for `tagSet`, or `null` when it is not selected.
    public LogLevel levelFor(LogTagSet tagSet) {
        LogLevel result = null;
        for (LogSelection selection : selections) {
            if (selection.selects(tagSet)) {
                result = selection.level();
            }
        }
        return result;
    }

    public List<LogSelection> selections() {
        return selections;
    }
}
