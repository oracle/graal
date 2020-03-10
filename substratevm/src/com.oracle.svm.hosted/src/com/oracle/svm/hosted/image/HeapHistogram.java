/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.image;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.image.NativeImageHeap.ObjectInfo;
import com.oracle.svm.hosted.meta.HostedClass;

/** Debug printing of statistics about the native image heap. */
public class HeapHistogram {

    protected static boolean PrintStrings = false;

    private final Map<HostedClass, HistogramEntry> data = new HashMap<>();

    static class HistogramEntry {
        protected final HostedClass clazz;
        protected long count;
        protected long size;

        HistogramEntry(HostedClass clazz) {
            this.clazz = clazz;
        }
    }

    private static final Comparator<HistogramEntry> SIZE_COMPARATOR = (o1, o2) -> {
        // Larger sizes first
        int result = Long.compare(o2.size, o1.size);
        if (result == 0) {
            // Then more instances
            result = Long.compare(o2.count, o1.count);
        }
        if (result == 0) {
            // Then sort by name
            result = o2.clazz.getName().compareTo(o1.clazz.getName());
        }
        return result;
    };

    public void add(ObjectInfo objectInfo, long size) {
        assert NativeImageOptions.PrintHeapHistogram.getValue();

        HistogramEntry entry = data.get(objectInfo.getClazz());
        if (entry == null) {
            entry = new HistogramEntry(objectInfo.getClazz());
            data.put(objectInfo.getClazz(), entry);
        }

        entry.count++;
        entry.size += size;

        if (PrintStrings && objectInfo.getObject() instanceof String) {
            String reason = String.valueOf(objectInfo.reason);
            String value = ((String) objectInfo.getObject()).replace("\n", "");
            if (!reason.startsWith("com.oracle.svm.core.hub.DynamicHub")) {
                System.out.format("%120s ::: %s\n", value, reason);
            }
        }
    }

    public void printHeadings(final String title) {
        assert NativeImageOptions.PrintHeapHistogram.getValue();
        System.out.format("\n%s\n", title);
        System.out.format(headerFormat, "Count", "Size", "Size%", "Cum%", "Class");
    }

    public void print() {
        assert NativeImageOptions.PrintHeapHistogram.getValue();

        HistogramEntry[] entries = data.values().toArray(new HistogramEntry[data.size()]);
        Arrays.sort(entries, SIZE_COMPARATOR);

        long totalSize = getTotalSize();
        long printedSize = 0;
        for (HistogramEntry entry : entries) {
            printedSize += entry.size;
            System.out.format(entryFormat, entry.count, entry.size, entry.size * 100d / totalSize, printedSize * 100d / totalSize, entry.clazz.toJavaName());
        }
    }

    public long getTotalSize() {
        long totalSize = 0;
        for (HistogramEntry entry : data.values()) {
            totalSize += entry.size;
        }
        return totalSize;
    }

    public long getTotalCount() {
        long totalCount = 0;
        for (HistogramEntry entry : data.values()) {
            totalCount += entry.count;
        }
        return totalCount;
    }

    // Constants.
    private final String headerFormat = "%8s %8s  %6s  %6s %s\n";
    private final String entryFormat = "%8d %8d %6.2f%% %6.2f%% %s\n";
}
