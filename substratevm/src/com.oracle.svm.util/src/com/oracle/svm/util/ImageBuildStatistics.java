/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.util;

import java.io.PrintWriter;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.nativeimage.ImageSingletons;

public class ImageBuildStatistics {

    public static class Options {
        @Option(help = "Collect information during image build about devirtualized invokes and bytecode exceptions.")//
        public static final OptionKey<Boolean> CollectImageBuildStatistics = new OptionKey<>(false);
        @Option(help = "File for printing image build statistics")//
        public static final OptionKey<String> ImageBuildStatisticsFile = new OptionKey<>(null);
    }

    public enum CheckCountLocation {
        AFTER_PARSE_CANONICALIZATION,
        BEFORE_HIGH_TIER,
        AFTER_HIGH_TIER
    }

    final TreeMap<String, AtomicLong> counters;

    public void incDevirtualizedInvokeCounter() {
        counters.get(devirtualizedInvokes()).incrementAndGet();
    }

    public void incByteCodeException(BytecodeExceptionNode.BytecodeExceptionKind kind, CheckCountLocation location) {
        counters.get(getName(kind.name(), location)).incrementAndGet();
    }

    public Consumer<PrintWriter> getReporter() {
        ImageBuildCountersReport printer = new ImageBuildCountersReport();
        return printer::print;
    }

    private static String getName(String name, CheckCountLocation location) {
        return ("total_" + name + "_" + location.name()).toLowerCase(Locale.ROOT);
    }

    private static String devirtualizedInvokes() {
        return "total_devirtualized_invokes";
    }

    public static ImageBuildStatistics counters() {
        return ImageSingletons.lookup(ImageBuildStatistics.class);
    }

    public ImageBuildStatistics() {
        counters = new TreeMap<>();
        counters.put(devirtualizedInvokes(), new AtomicLong());
        for (BytecodeExceptionNode.BytecodeExceptionKind kind : BytecodeExceptionNode.BytecodeExceptionKind.values()) {
            for (CheckCountLocation location : CheckCountLocation.values()) {
                counters.put(getName(kind.name(), location), new AtomicLong());
            }
        }
    }

    class ImageBuildCountersReport {
        /**
         * Print statistics collected during image build as JSON formatted String.
         */
        void print(PrintWriter out) {
            StringBuilder json = new StringBuilder();
            json.append("{").append(System.lineSeparator());
            for (Map.Entry<String, AtomicLong> entry : counters.entrySet()) {
                json.append(INDENT + "\"").append(entry.getKey()).append("\":").append(entry.getValue().get()).append(",").append(System.lineSeparator());
            }
            json.append("}").append(System.lineSeparator());
            out.print(fixLast(json));
        }

        static final String INDENT = "   ";

        protected String fixLast(StringBuilder json) {
            return json.toString().replace("," + System.lineSeparator() + "}", System.lineSeparator() + "}");
        }
    }
}
