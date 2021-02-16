/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.reports;

import static com.oracle.graal.pointsto.reports.ReportUtils.fieldComparator;
import static com.oracle.graal.pointsto.reports.ReportUtils.positionComparator;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.vm.ci.meta.JavaConstant;

public final class AnalysisHeapHistogramPrinter extends ObjectScanner {

    public static void print(BigBang bigbang, String path, String reportName) {
        ReportUtils.report("analysis heap histogram", path + File.separatorChar + "reports", "analysis_heap_histogram_" + reportName, "txt",
                        writer -> AnalysisHeapHistogramPrinter.doPrint(writer, bigbang));
    }

    private static void doPrint(PrintWriter out, BigBang bigbang) {
        if (!PointstoOptions.ExhaustiveHeapScan.getValue(bigbang.getOptions())) {
            String types = Arrays.stream(bigbang.skippedHeapTypes()).map(t -> t.toJavaName()).collect(Collectors.joining(", "));
            System.out.println("Exhaustive heap scanning is disabled. The analysis heap histogram will not contain all instances of types: " + types);
            System.out.println("Exhaustive heap scanning can be turned on using -H:+ExhaustiveHeapScan.");
        }
        AnalysisHeapHistogramPrinter printer = new AnalysisHeapHistogramPrinter(bigbang);
        printer.scanBootImageHeapRoots(fieldComparator, positionComparator);
        printer.printHistogram(out);
    }

    private void printHistogram(PrintWriter out) {
        out.println("Heap histogram");
        out.format("%8s %s %n", "Count", "Class");

        histogram.entrySet().stream().sorted(Map.Entry.<AnalysisType, Integer> comparingByValue().reversed())
                        .forEach(entry -> out.format("%8d %8s %n", entry.getValue(), entry.getKey().toJavaName()));
    }

    private final Map<AnalysisType, Integer> histogram = new HashMap<>();

    private AnalysisHeapHistogramPrinter(BigBang bigbang) {
        super(bigbang, new ReusableSet());
    }

    @Override
    protected void forScannedConstant(JavaConstant scannedValue, ScanReason reason) {
        AnalysisType type = constantType(bb, scannedValue);
        int count = histogram.getOrDefault(type, 0);
        histogram.put(type, count + 1);
    }

    @Override
    public void forRelocatedPointerFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue) {
    }

    @Override
    public void forNullFieldValue(JavaConstant receiver, AnalysisField field) {
    }

    @Override
    public void forNonNullFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue) {
    }

    @Override
    public void forNullArrayElement(JavaConstant array, AnalysisType arrayType, int index) {
    }

    @Override
    public void forNonNullArrayElement(JavaConstant array, AnalysisType arrayType, JavaConstant elementConstant, AnalysisType elementType, int index) {
    }
}
