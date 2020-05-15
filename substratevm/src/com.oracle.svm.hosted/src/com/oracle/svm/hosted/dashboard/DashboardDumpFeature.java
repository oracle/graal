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
package com.oracle.svm.hosted.dashboard;

import com.oracle.graal.pointsto.reports.ReportUtils;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

@AutomaticFeature
public class DashboardDumpFeature implements Feature {
    static class Dict {
        final LinkedHashMap<String, Object> sections;

        Dict() {
            this.sections = new LinkedHashMap<>();
        }

        void insert(String key, Object value) {
            sections.put(key, value);
        }

        boolean hasKey(String key) {
            return sections.containsKey(key);
        }

        Object get(String key) {
            return sections.get(key);
        }

        Integer getInt(String key) {
            return (Integer) get(key);
        }

        Number getNumber(String key) {
            return (Number) get(key);
        }

        @SuppressWarnings("unchecked")
        ArrayList<Object> getList(String key) {
            return (ArrayList<Object>) get(key);
        }

        String getString(String key) {
            return (String) get(key);
        }

        Dict getDict(String key) {
            return (Dict) get(key);
        }

        public void dump(PrintWriter writer) {
            writer.println();
            writer.println("{");
            int index = 0;
            for (Map.Entry<String, Object> entry : sections.entrySet()) {
                writer.print("\"");
                writer.print(escape(entry.getKey()));
                writer.print("\": ");
                final Object value = entry.getValue();
                dumpValue(writer, value);
                index++;
                if (index < sections.size()) {
                    writer.print(",");
                }
                writer.println();
            }
            writer.println("}");
            writer.println();
        }

        static String escape(String input) {
            String escaped = input;
            escaped = escaped.replace("\\", "\\\\");
            escaped = escaped.replace("\"", "\\\"");
            escaped = escaped.replace("\b", "\\b");
            escaped = escaped.replace("\f", "\\f");
            escaped = escaped.replace("\n", "\\n");
            escaped = escaped.replace("\r", "\\r");
            escaped = escaped.replace("\t", "\\t");
            escaped = escaped.replace("/", "\\/");
            return escaped;
        }

        @SuppressWarnings("unchecked")
        private void dumpValue(PrintWriter writer, Object value) {
            if (value instanceof Number) {
                writer.print(value);
            } else if (value instanceof String) {
                writer.print("\"");
                writer.print(escape((String) value));
                writer.print("\"");
            } else if (value instanceof ArrayList) {
                dumpList(writer, (ArrayList<Object>) value);
            } else if (value instanceof Dict) {
                ((Dict) value).dump(writer);
            } else if (value == null) {
                writer.print("null");
            } else {
                throw GraalError.shouldNotReachHere("Unknown value: " + value + ", type: " + value.getClass());
            }
        }

        private void dumpList(PrintWriter writer, ArrayList<Object> list) {
            writer.print("[");
            int index = 0;
            for (Object value : list) {
                dumpValue(writer, value);
                index++;
                if (index < list.size()) {
                    writer.print(", ");
                }
            }
            writer.print("]");
        }
    }

    private static boolean isHeapBreakdownDumped() {
        return DashboardOptions.DashboardAll.getValue() || DashboardOptions.DashboardHeap.getValue();
    }

    private static boolean isPointsToDumped() {
        return DashboardOptions.DashboardAll.getValue() || DashboardOptions.DashboardPointsTo.getValue();
    }

    private static boolean isCodeBreakdownDumped() {
        return DashboardOptions.DashboardAll.getValue() || DashboardOptions.DashboardCode.getValue();
    }

    private static void dumpToFile(Dict dumpContent, String dumpPath) {
        final File file = new File(dumpPath).getAbsoluteFile();
        ReportUtils.report("Dashboard dump output", file.toPath(), writer -> {
            dumpContent.dump(writer);
        });
    }

    private final Dict dumpRoot;

    public DashboardDumpFeature() {
        dumpRoot = new Dict();
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return DashboardOptions.DashboardDump.getValue() != null;
    }

    @Override
    public void onAnalysisExit(OnAnalysisExitAccess access) {
        if (isPointsToDumped()) {
            final PointsToDumper pointsToDumper = new PointsToDumper();
            final Dict pointsTo = pointsToDumper.dump(access);
            dumpRoot.insert("points-to", pointsTo);
        }
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        if (isCodeBreakdownDumped()) {
            final CodeBreakdownDumper codeBreakdownDumper = new CodeBreakdownDumper();
            final Dict breakdown = codeBreakdownDumper.dump(access);
            dumpRoot.insert("code-breakdown", breakdown);
        }
    }

    @Override
    public void afterHeapLayout(AfterHeapLayoutAccess access) {
        if (isHeapBreakdownDumped()) {
            final HeapBreakdownDumper heapBreakdownDumper = new HeapBreakdownDumper();
            final Dict breakdown = heapBreakdownDumper.dump(access);
            dumpRoot.insert("heap-breakdown", breakdown);
        }
    }

    @Override
    public void cleanup() {
        dumpToFile(dumpRoot, DashboardOptions.DashboardDump.getValue());
    }
}
