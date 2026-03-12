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
package com.oracle.svm.hosted.image;

import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.image.ImageHeapPartition;
import com.oracle.svm.hosted.NativeImageOptions;
import org.graalvm.collections.EconomicSet;

import java.io.File;
import java.io.PrintWriter;

public final class HeapHistogramPrinter {

    private final ImageHeapPartition[] partitions;
    private final NativeImageHeap heap;

    public HeapHistogramPrinter(NativeImageHeap heap, ImageHeapPartition[] partitions) {
        this.partitions = partitions;
        this.heap = heap;
    }

    public static void print(NativeImageHeap heap, ImageHeapPartition[] partitions) {
        if (NativeImageOptions.PrintHeapHistogram.getValue()) {
            new HeapHistogramPrinter(heap, partitions).printHeapHistogram();
        }
        if (NativeImageOptions.PrintImageHeapPartitionSizes.getValue()) {
            printSizes(partitions);
        }
    }

    private static void printSizes(ImageHeapPartition[] partitions) {
        for (ImageHeapPartition partition : partitions) {
            printSize(partition);
        }
    }

    private static void printSize(ImageHeapPartition partition) {
        System.out.printf("PrintImageHeapPartitionSizes:  partition: %s  size: %d%n", partition.getName(), partition.getSize());
    }

    private void printHeapHistogram() {
        File file = ReportUtils.reportFile(SubstrateOptions.reportsPath(), "histogram", "txt");
        ReportUtils.report("histogram", file.toPath(), this::printHistogram);
    }

    private void printHistogram(PrintWriter out) {
        // A histogram for the whole heap.
        ObjectGroupHistogram.print(heap, out);

        // Histograms for each partition.
        for (ImageHeapPartition partition : partitions) {
            printHistogram(partition, heap.getObjects(), out);
        }
    }

    private static void printHistogram(ImageHeapPartition partition, Iterable<NativeImageHeap.ObjectInfo> objects, PrintWriter out) {
        HeapHistogram histogram = new HeapHistogram(out);
        EconomicSet<NativeImageHeap.ObjectInfo> uniqueObjectInfo = EconomicSet.create();

        long uniqueCount = 0L;
        long uniqueSize = 0L;
        long canonicalizedCount = 0L;
        long canonicalizedSize = 0L;
        for (NativeImageHeap.ObjectInfo info : objects) {
            if (info.getConstant().isWrittenInPreviousLayer()) {
                continue;
            }
            if (partition == info.getPartition()) {
                if (uniqueObjectInfo.add(info)) {
                    histogram.add(info, info.getSize());
                    uniqueCount += 1L;
                    uniqueSize += info.getSize();
                } else {
                    canonicalizedCount += 1L;
                    canonicalizedSize += info.getSize();
                }
            }
        }

        long nonuniqueCount = uniqueCount + canonicalizedCount;
        long nonuniqueSize = uniqueSize + canonicalizedSize;
        assert partition.getSize() >= nonuniqueSize : "the total size can contain some overhead";

        double countPercent = 100.0D * ((double) uniqueCount / (double) nonuniqueCount);
        double sizePercent = 100.0D * ((double) uniqueSize / (double) nonuniqueSize);
        double sizeOverheadPercent = 100.0D * (1.0D - ((double) partition.getSize() / (double) nonuniqueSize));
        histogram.printHeadings(String.format("=== Partition: %s   count: %d / %d = %.1f%%  object size: %d / %d = %.1f%%  total size: %d (%.1f%% overhead) ===", //
                        partition.getName(), //
                        uniqueCount, nonuniqueCount, countPercent, //
                        uniqueSize, nonuniqueSize, sizePercent, //
                        partition.getSize(), sizeOverheadPercent));
        histogram.print();
    }
}
