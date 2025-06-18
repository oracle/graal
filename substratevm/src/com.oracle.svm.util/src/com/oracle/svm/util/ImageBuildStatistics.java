/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;

/**
 * This class collects detailed counters that are then saved into the image build statistics file.
 */
public class ImageBuildStatistics {

    public static class Options {
        @Option(help = "Collect information during image build about devirtualized invokes and bytecode exceptions.")//
        public static final OptionKey<Boolean> CollectImageBuildStatistics = new OptionKey<>(false);
    }

    /**
     * Phases in the compilation pipeline where the counters may be collected.
     */
    public enum CheckCountLocation {
        BEFORE_STRENGTHEN_GRAPHS,
        AFTER_STRENGTHEN_GRAPHS,
        AFTER_PARSE_CANONICALIZATION,
        BEFORE_HIGH_TIER,
        AFTER_HIGH_TIER
    }

    final TreeMap<String, AtomicLong> counters;

    /**
     * Create a new counter based on the given unique {@code key}. Each counter is represented as an
     * {@link AtomicLong} so that it can be updated from multiple compilations happening in
     * parallel.
     */
    public AtomicLong createCounter(String key) {
        AtomicLong result = new AtomicLong();
        var existing = counters.put(key, result);
        if (existing != null) {
            throw GraalError.shouldNotReachHere("Key already used: " + key);
        }
        return result;
    }

    /**
     * @see #createCounter(String)
     */
    public AtomicLong createCounter(String key, CheckCountLocation location) {
        return createCounter(getName(key, location));
    }

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
            out.print(json);
            printTimerStats(out);
            printTesaStats(out);
            out.format("}%n");
        }

        private void printTimerStats(PrintWriter out) {
            TimerCollectionPrinter dumper = ImageSingletons.lookup(TimerCollectionPrinter.class);
            dumper.printTimerStats(out);
        }

        private void printTesaStats(PrintWriter out) {
            if (ImageSingletons.contains(TesaPrinter.class)) {
                ImageSingletons.lookup(TesaPrinter.class).printTesaResults(out);
            }
        }

        static final String INDENT = "   ";
    }

    public interface TimerCollectionPrinter {
        void printTimerStats(PrintWriter out);
    }

    public interface TesaPrinter {
        void printTesaResults(PrintWriter out);
    }
}
