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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;

/**
 * Build a histogram of class counts.
 *
 * This is designed to be called from a CollectionWatcher.afterCollection() method. It is allowed to
 * allocate storage. If you want to write something similar to be called from a
 * CollectionWatcher.beforeCollection() method, you have to be careful not to allocate anything.
 */
public class ClassHistogramVisitor implements ObjectVisitor {

    /*
     * State.
     */

    protected final List<Class<?>> classList;
    protected final HistogramEntry[] entryArray;

    /** Factory method. */
    public static ClassHistogramVisitor factory() {
        return new ClassHistogramVisitor();
    }

    /** Constructor. */
    protected ClassHistogramVisitor() {
        classList = new ArrayList<>();
        initializeClassList();
        entryArray = new HistogramEntry[classList.size()];
        initializeFromClassList();
    }

    /*
     * ObjectVisitor methods.
     */

    @Override
    /** Clear all the counters. */
    public boolean prologue() {
        reset();
        return true;
    }

    @Override
    /** Accumulate information about this instance. */
    public boolean visitObject(Object o) {
        final HistogramEntry entry = findEntry(o.getClass().getName());
        if (entry == null) {
            return false;
        }
        /* Count the number of instances. */
        entry.instanceCount += 1;
        /* Accumulate the space for the instances. */
        entry.instanceSpace += LayoutEncoding.getSizeFromObject(o).rawValue();
        return true;
    }

    /*
     * Additional methods.
     */

    private HistogramEntry findEntry(String s) {
        for (int index = 0; index < entryArray.length; index += 1) {
            if (entryArray[index].getClassName().equals(s)) {
                return entryArray[index];
            }
        }
        return null;
    }

    /** Reset all the counters. */
    public void reset() {
        for (int index = 0; index < entryArray.length; index += 1) {
            entryArray[index].reset();
        }
    }

    /** Log all the entries, sorted by class name. */
    public void toLogByName(final Log log, final long minimum) {
        final HistogramEntry[] filteredArray = filterEntries(minimum);
        Arrays.sort(filteredArray, HistogramEntry.byName);
        toLog(log, filteredArray);
    }

    /** Log all the entries, sorted by instance count. */
    public void toLogByCount(Log log, long minimum) {
        toLogByCount(log, minimum, true);
    }

    /** Log all the entries, by increasing or decreasing instance count. */
    public void toLogByCount(final Log log, final long minimum, boolean increasing) {
        final HistogramEntry[] filteredArray = filterEntries(minimum);
        Arrays.sort(filteredArray, (increasing ? HistogramEntry.byIncreasingCount : HistogramEntry.byDecreasingCount));
        toLog(log, filteredArray);
    }

    /** Log all the entries, sorted by occupied space. */
    public void toLogBySpace(Log log, long minimum) {
        toLogBySpace(log, minimum, true);
    }

    /** Log all the entries, by increasing or decreasing occupied space. */
    public void toLogBySpace(Log log, long minimum, boolean increasing) {
        final HistogramEntry[] filteredArray = filterEntries(minimum);
        Arrays.sort(filteredArray, (increasing ? HistogramEntry.byIncreasingSpace : HistogramEntry.byDecreasingSpace));
        toLog(log, filteredArray);
    }

    /* TODO: This method knows too much about formatting. */
    protected void toLog(Log log, HistogramEntry[] entry) {
        if (entry.length != 0) {
            log.string("  Count\tSize\tName").newline();
        }
        for (HistogramEntry e : entry) {
            toLog(log, e);
        }
    }

    /* TODO: This method knows too much about formatting. */
    protected void toLog(Log log, HistogramEntry entry) {
        log.string("  ");
        log.signed(entry.getInstanceCount());
        log.character('\t');
        log.signed(entry.getInstanceSpace());
        log.character('\t');
        log.string(entry.getClassName());
        log.newline();
    }

    /*
     * TODO: Should this method be available to clients? Among the problems is the lifetime of the
     * result.
     */
    protected HistogramEntry[] filterEntries(long minimumInstanceCount) {
        /* Count how many elements are above the minimum. */
        int count = 0;
        for (HistogramEntry entry : entryArray) {
            if (minimumInstanceCount <= entry.getInstanceCount()) {
                count += 1;
            }
        }
        /* entryArray is only valid as long as classMap remains unmodified. */
        HistogramEntry[] filteredArray = new HistogramEntry[count];
        int index = 0;
        for (HistogramEntry entry : entryArray) {
            if (minimumInstanceCount <= entry.getInstanceCount()) {
                filteredArray[index] = entry;
                index += 1;
            }
        }
        return filteredArray;
    }

    /* TODO: This could be done once, since the boot image heap doesn't change. */
    protected void initializeClassList() {
        /* Classes are read-only, reference-containing. */
        initializeClassList(NativeImageInfo.firstReadOnlyReferenceObject, NativeImageInfo.lastReadOnlyReferenceObject);
    }

    /*
     * This can be "interruptible" because all the pointers are to boot image heap objects, which do
     * not move. In particular, it can allocate.
     */
    private void initializeClassList(Object firstObject, Object lastObject) {
        if ((firstObject == null) || (lastObject == null)) {
            return;
        }
        final Pointer firstPointer = Word.objectToUntrackedPointer(firstObject);
        final Pointer lastPointer = Word.objectToUntrackedPointer(lastObject);
        Pointer currentPointer = firstPointer;
        while (currentPointer.belowOrEqual(lastPointer)) {
            Object currentObject = KnownIntrinsics.convertUnknownValue(currentPointer.toObject(), Object.class);
            if ((currentObject != null) && (currentObject instanceof Class<?>)) {
                classList.add((Class<?>) currentObject);
            }
            currentPointer = LayoutEncoding.getObjectEnd(currentObject);
        }
    }

    private void initializeFromClassList() {
        int index = 0;
        for (Class<?> c : classList) {
            entryArray[index] = new HistogramEntry(c.getName());
            index += 1;
        }
    }

    /** An entry for maps from class names to information about instances of those classes. */
    protected static class HistogramEntry {

        public static HistogramEntry factory(String className) {
            return new HistogramEntry(className);
        }

        /*
         * Access methods.
         */

        public String getClassName() {
            return className;
        }

        public long getInstanceCount() {
            return instanceCount;
        }

        public long getInstanceSpace() {
            return instanceSpace;
        }

        public void reset() {
            instanceCount = 0L;
            instanceSpace = 0L;
        }

        /** Constructor. */
        protected HistogramEntry(String className) {
            this.className = className;
            reset();
        }

        /*
         * State.
         */

        /** The name of the class. */
        protected final String className;
        /** The count of all the instances. */
        protected long instanceCount;
        /** The space taken up by all the instances. */
        protected long instanceSpace;

        /*
         * Static state.
         */

        /** Sort by name. */
        protected static Comparator<HistogramEntry> byName = (x, y) -> x.getClassName().compareTo(y.getClassName());

        /** Sort by count. */
        protected static Comparator<HistogramEntry> byIncreasingCount = (x, y) -> Long.signum(x.getInstanceCount() - y.getInstanceCount());
        protected static Comparator<HistogramEntry> byDecreasingCount = (x, y) -> Long.signum(y.getInstanceCount() - x.getInstanceCount());

        /** Sort by space. */
        protected static Comparator<HistogramEntry> byIncreasingSpace = (x, y) -> Long.signum(x.getInstanceSpace() - y.getInstanceSpace());
        protected static Comparator<HistogramEntry> byDecreasingSpace = (x, y) -> Long.signum(y.getInstanceSpace() - x.getInstanceSpace());

    }
}
