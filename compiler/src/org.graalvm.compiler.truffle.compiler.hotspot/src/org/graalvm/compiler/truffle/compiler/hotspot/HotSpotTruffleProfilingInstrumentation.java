/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.hotspot;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.graph.SourceLanguagePosition;
import org.graalvm.compiler.hotspot.debug.BenchmarkCounters;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.PrefetchAllocateNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.debug.DynamicCounterNode;
import org.graalvm.compiler.nodes.extended.SwitchNode;
import org.graalvm.compiler.nodes.java.AbstractCompareAndSwapNode;
import org.graalvm.compiler.nodes.memory.AbstractWriteNode;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.tiers.LowTierContext;
import org.graalvm.compiler.replacements.nodes.ZeroMemoryNode;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class HotSpotTruffleProfilingInstrumentation {

    public static class Options {
        // @formatter:off
        @Option(help = "A", type = OptionType.Debug)
        public static final OptionKey<Boolean> TruffleHostCounters = new OptionKey<>(false) {
            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
                if (newValue) {
                    BenchmarkCounters.Options.GenericDynamicCounters.update(values, true);
                    GraalOptions.TrackNodeSourcePosition.update(values, true);
                }
            }
        };

        @Option(help = "A", type = OptionType.Debug)
        public static final OptionKey<Boolean> TruffleGuestCounters = new OptionKey<>(false) {
            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
                if (newValue) {
                    BenchmarkCounters.Options.GenericDynamicCounters.update(values, true);
                    GraalOptions.TrackNodeSourcePosition.update(values, true);
                }
            }
        };
        // @formatter:on
    }

    public static void installHost(OptionValues options, PhaseSuite<LowTierContext> lowTier) {
        if (Options.TruffleHostCounters.getValue(options)) {
            var schedule = lowTier.findPhase(SchedulePhase.class);
            schedule.previous();
            schedule.add(new InstrumentPhase(true));
        }
    }

    public static void installGuest(OptionValues options, PhaseSuite<LowTierContext> lowTier) {
        if (Options.TruffleGuestCounters.getValue(options)) {
            var schedule = lowTier.findPhase(SchedulePhase.class);
            schedule.previous();
            schedule.add(new InstrumentPhase(false));
        }
    }

    public static class InstrumentPhase extends Phase {

        private final boolean host;

        InstrumentPhase(boolean host) {
            this.host = host;
        }

        @Override
        protected void run(StructuredGraph graph) {
            graph.getNodes().forEach((node) -> {
                if (node instanceof PrefetchAllocateNode) {
                    DynamicCounterNode.addCounterBefore("allocate", formatCounterLocation(node, null, host), 1, false, (PrefetchAllocateNode) node);
                } else if (node instanceof SwitchNode) {
                    DynamicCounterNode.addCounterBefore("switch", formatCounterLocation(node, null, host), 1, false, (SwitchNode) node);
                } else if (node instanceof IfNode) {
                    DynamicCounterNode.addCounterBefore("if", formatCounterLocation(node, null, host), 1, false, (IfNode) node);
                } else if (node instanceof Invoke) {
                    DynamicCounterNode.addCounterBefore("invoke", formatCounterLocation(node, ((Invoke) node).callTarget(), host), 1, false, ((Invoke) node).asFixedNode());
                } else if (node instanceof ReadNode) {
                    DynamicCounterNode.addCounterBefore("read", formatCounterLocation(node, null, host), 1, false, (ReadNode) node);
                } else if (node instanceof AbstractWriteNode || node instanceof AbstractCompareAndSwapNode) {
                    DynamicCounterNode.addCounterBefore("write", formatCounterLocation(node, null, host), 1, false, (FixedAccessNode) node);
                } else if (node instanceof ZeroMemoryNode) {
                    ZeroMemoryNode zeroNode = (ZeroMemoryNode) node;
                    DynamicCounterNode.addCounterBefore("write", formatCounterLocation(node, null, host), zeroNode.getLength(), false, zeroNode);
                }
            });
        }
    }

    static String formatCounterLocation(Node node, CallTargetNode target, boolean host) {
        StackTraceElement[] elements = GraphUtil.approxSourceStackTraceElement(node);
        StringBuilder b = new StringBuilder(100);
        String sep = "";
        for (StackTraceElement stackTraceElement : elements) {
            b.append(sep);
            b.append(stackTraceElement.toString());
            if (sep.isEmpty()) {
                sep = "\n                          ";
            }
        }
        if (target != null) {
            b.append("\n                target => ");
            b.append(target.targetMethod().format("%H.%n(%p)"));
        }
        if (b.length() == 0) {
            ResolvedJavaMethod method = ((StructuredGraph) node.graph()).method();
            if (method == null) {
                return "no-location(" + node.toString() + ")";
            } else {
                return "no-location in " + method.format("%H.%n(%p) (" + node.toString() + ")");
            }
        } else {
            if (!host) {
                NodeSourcePosition nodeSource = node.getNodeSourcePosition();
                if (nodeSource != null) {
                    b.append("\n                          Source AST Nodes:");
                    printSourceLanguageStackTrace(b, "\n                            ", nodeSource);
                }
            }
            return b.toString();
        }
    }

    public static void printSourceLanguageStackTrace(StringBuilder b, String sep, NodeSourcePosition sourcePosition) {
        NodeSourcePosition position = sourcePosition;
        String prev = null;
        while (position != null) {
            SourceLanguagePosition source = position.getSourceLanguage();
            if (source != null) {
                String trace = formatStackTrace(source);
                if (!trace.equals(prev)) {
                    b.append(sep);
                    b.append(trace);
                }
                prev = trace;
            }
            position = position.getCaller();
        }
    }

    private static String formatStackTrace(SourceLanguagePosition source) {
        StringBuilder b = new StringBuilder();
        String language = source.getLanguage();
        if (language != null) {
            b.append("<" + language + "> ");
        }
        String path = source.getURI() != null ? source.getURI().getPath() : null;
        if (path != null) {
            int lastIndex = path.lastIndexOf('/');
            if (lastIndex != -1) {
                path = path.substring(lastIndex + 1, path.length());
            }
        }
        String methodName = source.getQualifiedRootName();
        if (methodName != null) {
            b.append(methodName);
        }
        String fileName = path != null ? path : "Unknown";
        b.append("(").append(fileName);
        if (path != null) {
            b.append(":").append(source.getLineNumber());
        }
        b.append(") (").append(source.getNodeId()).append("|").append(source.getNodeClassName()).append(")");
        return b.toString();
    }

}
