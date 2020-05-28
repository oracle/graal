/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import java.util.Arrays;
import java.util.Comparator;

import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;

/** Builds per-class instance count and space usage histograms. */
public class ClassHistogramVisitor implements ObjectVisitor {
    private final HistogramEntry[] entries;

    public ClassHistogramVisitor() {
        // NOTE: we cannot use a map because lookups in the visitor must be allocation-free
        entries = Heap.getHeap().getClassList().stream().map(Class::getName).sorted()
                        .map(HistogramEntry::new).toArray(HistogramEntry[]::new);
    }

    @Override
    public boolean visitObject(Object o) {
        HistogramEntry entry = findEntry(o.getClass().getName());
        if (entry == null) {
            return false;
        }
        entry.instanceCount++;
        entry.instanceSpace += LayoutEncoding.getSizeFromObject(o).rawValue();
        return true;
    }

    public void prologue() {
        reset();
    }

    public void epilogue() {
    }

    private HistogramEntry findEntry(String s) {
        // No allocations: binary search inlined so analysis doesn't see problematic virtual calls
        int lo = 0;
        int hi = entries.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            HistogramEntry entry = entries[mid];
            int cmp = entry.className.compareTo(s);
            if (cmp < 0) {
                lo = mid + 1;
            } else if (cmp > 0) {
                hi = mid - 1;
            } else {
                return entry; // key found
            }
        }
        return null;
    }

    /** Reset the counters of all classes. */
    public void reset() {
        for (HistogramEntry entry : entries) {
            entry.reset();
        }
    }

    /** Log all the entries, sorted by class name. */
    public void toLogByName(Log log, long minimum) {
        toLogWithComparator(log, minimum, true, Comparator.comparing(e -> e.className));
    }

    /** Log all the entries, sorted by instance count. */
    public void toLogByCount(Log log, long minimum) {
        toLogWithComparator(log, minimum, true, Comparator.comparingLong(e -> e.instanceCount));
    }

    /** Log all the entries, sorted by increasing or decreasing occupied space. */
    public void toLogBySpace(Log log, long minimum, boolean increasing) {
        toLogWithComparator(log, minimum, increasing, Comparator.comparingLong(e -> e.instanceSpace));
    }

    private void toLogWithComparator(Log log, long minimum, boolean increasing, Comparator<HistogramEntry> comparator) {
        Comparator<HistogramEntry> cmp = increasing ? comparator : comparator.reversed();
        HistogramEntry[] filteredArray = Arrays.stream(entries).filter(e -> e.instanceCount >= minimum)
                        .sorted(cmp).toArray(HistogramEntry[]::new);
        toLog(log, filteredArray);
    }

    protected static void toLog(Log log, HistogramEntry[] entries) {
        if (entries.length != 0) {
            log.string("  Count\tSize\tName").newline();
        }
        for (HistogramEntry e : entries) {
            toLog(log, e);
        }
    }

    private static void toLog(Log log, HistogramEntry entry) {
        log.string("  ").signed(entry.instanceCount).character('\t').signed(entry.instanceSpace).character('\t').string(entry.className).newline();
    }

    /** Information about the instances of a class. */
    private static class HistogramEntry {
        final String className;

        long instanceCount;

        /** The space taken up by all the instances. */
        long instanceSpace;

        HistogramEntry(String className) {
            this.className = className;
            reset();
        }

        void reset() {
            instanceCount = 0L;
            instanceSpace = 0L;
        }
    }
}
