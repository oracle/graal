/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.compiler.common.alloc;

import java.util.List;

import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.Indent;

public final class TraceStatisticsPrinter {
    private static final String SEP = ";";
    private static final int TRACE_DUMP_LEVEL = 3;

    @SuppressWarnings("try")
    public static <T extends AbstractBlockBase<T>> void printTraceStatistics(TraceBuilderResult<T> result, String compilationUnitName) {
        try (Scope s = Debug.scope("DumpTraceStatistics")) {
            if (Debug.isLogEnabled(TRACE_DUMP_LEVEL)) {
                print(result, compilationUnitName);
            }
        } catch (Throwable e) {
            Debug.handle(e);
        }
    }

    @SuppressWarnings("try")
    protected static <T extends AbstractBlockBase<T>> void print(TraceBuilderResult<T> result, String compilationUnitName) {
        List<Trace<T>> traces = result.getTraces();
        int numTraces = traces.size();

        try (Indent indent0 = Debug.logAndIndent(TRACE_DUMP_LEVEL, "<tracestatistics>")) {
            Debug.log(TRACE_DUMP_LEVEL, "<name>%s</name>", compilationUnitName != null ? compilationUnitName : "null");
            try (Indent indent1 = Debug.logAndIndent(TRACE_DUMP_LEVEL, "<traces>")) {
                printRawLine("tracenumber", "total", "min", "max", "numBlocks");
                for (int i = 0; i < numTraces; i++) {
                    List<T> t = traces.get(i).getBlocks();
                    double total = 0;
                    double max = Double.NEGATIVE_INFINITY;
                    double min = Double.POSITIVE_INFINITY;
                    for (T block : t) {
                        double probability = block.probability();
                        total += probability;
                        if (probability < min) {
                            min = probability;
                        }
                        if (probability > max) {
                            max = probability;
                        }
                    }
                    printLine(i, total, min, max, t.size());
                }
            }
            Debug.log(TRACE_DUMP_LEVEL, "</traces>");
        }
        Debug.log(TRACE_DUMP_LEVEL, "</tracestatistics>");

    }

    private static void printRawLine(Object tracenr, Object totalTime, Object minProb, Object maxProb, Object numBlocks) {
        Debug.log(TRACE_DUMP_LEVEL, "%s", String.join(SEP, tracenr.toString(), totalTime.toString(), minProb.toString(), maxProb.toString(), numBlocks.toString()));
    }

    private static void printLine(int tracenr, double totalTime, double minProb, double maxProb, int numBlocks) {
        printRawLine(tracenr, totalTime, minProb, maxProb, numBlocks);
    }
}
