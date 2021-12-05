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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.java.BytecodeExceptionNodeSourceCollection;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode;
import org.graalvm.nativeimage.ImageSingletons;

public class ImageBuildStatistics {

    public enum CheckCountLocation {
        AFTER_PARSE_CANONICALIZATION,
        BEFORE_HIGH_TIER,
        AFTER_HIGH_TIER
    }

    final ConcurrentHashMap<String, CounterValue> counters;

    public void incDevirtualizedInvokeCounter() {
        counters.get(devirtualizedInvokes()).incCounter();
    }

    public void incByteCodeException(BytecodeExceptionNode.BytecodeExceptionKind kind, CheckCountLocation location, NodeSourcePosition nodeSourcePosition, ResolvedJavaMethod method) {
        counters.get(getName(kind.name(), location)).incCounter(nodeSourcePosition, method);
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

    public static ImageBuildStatistics singleton() {
        return ImageSingletons.lookup(ImageBuildStatistics.class);
    }

    public ImageBuildStatistics() {
        counters = new ConcurrentHashMap<>();
        counters.put(devirtualizedInvokes(), new CounterValue());
        for (BytecodeExceptionNode.BytecodeExceptionKind kind : BytecodeExceptionNode.BytecodeExceptionKind.values()) {
            for (CheckCountLocation location : CheckCountLocation.values()) {
                counters.put(getName(kind.name(), location), new CounterValue());
            }
        }
    }

    public static final class CounterValue {
        public CounterValue() {
            /*
             * Count and store original node source positions.
             */
            originalCounter = new AtomicLong();
            original = new ConcurrentLinkedQueue<>();
        }

        private void incCounter() {
            originalCounter.incrementAndGet();
        }

        private void incCounter(NodeSourcePosition nodeSourcePosition, ResolvedJavaMethod method) {
            if (original.contains(nodeSourcePosition)) {
                /*
                 * We have already seen this exception at the given source location, duplication
                 * occurred.
                 */
            } else {
                if (BytecodeExceptionNodeSourceCollection.isOriginal(nodeSourcePosition)) {
                    originalCounter.incrementAndGet();
                    original.add(nodeSourcePosition);
                } else if (BytecodeExceptionNodeSourceCollection.hasOriginalPrefix(nodeSourcePosition)) {
                    /*
                     * This node source position is from inlining, maybe new one or duplication
                     * occurred after something inlined.
                     */
                } else {
                    if (BytecodeExceptionNodeSourceCollection.hasRootFromExceptionObject(nodeSourcePosition)) {
                        /*
                         * This node source position is from exception coming from a call.
                         */
                    } else if (BytecodeExceptionNodeSourceCollection.hasOriginalRoot(nodeSourcePosition)) {
                        /*
                         * This node source position is coming from a virtual call.
                         */
                    } else {
                        throw GraalError.shouldNotReachHere("Found new node " + nodeSourcePosition + " after bytecode parsing in graph for " + method.format("%h.%n(%p)"));
                    }
                }
            }
        }

        public AtomicLong getOriginalCounter() {
            return originalCounter;
        }

        private final AtomicLong originalCounter;
        private final ConcurrentLinkedQueue<NodeSourcePosition> original;
    }

    class ImageBuildCountersReport {
        /**
         * Print statistics collected during image build as JSON formatted String.
         */
        void print(PrintWriter out) {
            TreeMap<String, CounterValue> sortedCounters = new TreeMap<>(counters);
            StringBuilder json = new StringBuilder();
            json.append("{").append(System.lineSeparator());
            for (Map.Entry<String, CounterValue> entry : sortedCounters.entrySet()) {
                /*
                 * Finally, we are interested in the original node source positions.
                 */
                json.append(INDENT + "\"").append(entry.getKey()).append("\":").append(entry.getValue().getOriginalCounter()).append(",").append(System.lineSeparator());
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
