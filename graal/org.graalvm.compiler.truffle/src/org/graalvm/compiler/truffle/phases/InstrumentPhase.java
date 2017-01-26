/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.phases;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaField;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.MethodFilter;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleInstrumentationTableSize;

public abstract class InstrumentPhase extends BasePhase<HighTierContext> {
    private static final String[] OMITTED_STACK_PATTERNS = new String[]{
            "org.graalvm.compiler.truffle.OptimizedCallTarget.callProxy",
            "org.graalvm.compiler.truffle.OptimizedCallTarget.callRoot",
            "org.graalvm.compiler.truffle.OptimizedCallTarget.callInlined",
            "org.graalvm.compiler.truffle.OptimizedDirectCallNode.callProxy",
            "org.graalvm.compiler.truffle.OptimizedDirectCallNode.call"
    };
    public static Instrumentation instrumentation = new Instrumentation();
    private static final String ACCESS_TABLE_FIELD_NAME = "ACCESS_TABLE";
    private static final int ACCESS_TABLE_SIZE = TruffleInstrumentationTableSize.getValue();
    public static final long[] ACCESS_TABLE = new long[ACCESS_TABLE_SIZE];
    protected final MethodFilter[] methodFilter;

    public InstrumentPhase() {
        String filterValue = instrumentationFilter();
        if (filterValue != null) {
            methodFilter = MethodFilter.parse(filterValue);
        } else {
            methodFilter = new MethodFilter[0];
        }
    }

    protected static void insertCounter(StructuredGraph graph, HighTierContext context, JavaConstant tableConstant,
                                        FixedWithNextNode targetNode, int slotIndex) {
        assert (tableConstant != null);
        TypeReference typeRef = TypeReference.createExactTrusted(context.getMetaAccess().lookupJavaType(tableConstant));
        ConstantNode table = graph.unique(new ConstantNode(tableConstant, StampFactory.object(typeRef, true)));
        ConstantNode rawIndex = graph.unique(ConstantNode.forInt(slotIndex));
        LoadIndexedNode load = graph.add(new LoadIndexedNode(null, table, rawIndex, JavaKind.Long));
        ConstantNode one = graph.unique(ConstantNode.forLong(1L));
        ValueNode add = graph.unique(new AddNode(load, one));
        StoreIndexedNode store = graph.add(new StoreIndexedNode(table, rawIndex, JavaKind.Long, add));

        graph.addAfterFixed(targetNode, load);
        graph.addAfterFixed(load, store);
    }

    @Override
    public float codeSizeIncrease() {
        return 2.5f;
    }

    @Override
    protected void run(StructuredGraph graph, HighTierContext context) {
        JavaConstant tableConstant = lookupTableConstant(context);
        try {
            instrumentGraph(graph, context, tableConstant);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract void instrumentGraph(StructuredGraph graph, HighTierContext context, JavaConstant tableConstant);

    protected abstract int instrumentationPointSlotCount();

    protected abstract String instrumentationFilter();

    protected abstract boolean instrumentPerInlineSite();

    protected abstract Instrumentation.Point createPoint(int id, int startIndex, Node n);

    public Instrumentation.Point getOrCreatePoint(MethodFilter[] methodFilter, Node n) {
        Instrumentation.Point point = instrumentation.getOrCreatePoint(methodFilter, n, this);
        assert point.slotCount() == instrumentationPointSlotCount() : "Slot count mismatch between instrumentation point and expected value.";
        return point;
    }

    protected JavaConstant lookupTableConstant(HighTierContext context) {
        ResolvedJavaField[] fields = context.getMetaAccess().lookupJavaType(InstrumentPhase.class).getStaticFields();
        ResolvedJavaField tableField = null;
        for (ResolvedJavaField field : fields) {
            if (field.getName().equals(ACCESS_TABLE_FIELD_NAME)) {
                tableField = field;
                break;
            }
        }
        JavaConstant tableConstant = context.getConstantReflection().readFieldValue(tableField, null);
        return tableConstant;
    }

    public static class Instrumentation {

        private Comparator<Map.Entry<String, Point>> entriesComparator = new Comparator<Map.Entry<String, Point>>() {
            @Override
            public int compare(Map.Entry<String, Point> x, Map.Entry<String, Point> y) {
                long diff = y.getValue().getHotness() - x.getValue().getHotness();
                if (diff < 0) {
                    return -1;
                } else if (diff == 0) {
                    return 0;
                } else {
                    return 1;
                }
            }
        };
        public Map<String, Point> pointMap = new LinkedHashMap<>();
        public int tableIdCount = 0;
        public int tableStartIndex = 0;

        /*
         * Node source location is determined by its inlining chain. A flag value controls whether
         * we discriminate nodes by their inlining site, or only by the method in which they were
         * defined.
         */
        private static String filterAndEncode(MethodFilter[] methodFilter, Node node, InstrumentPhase phase) {
            NodeSourcePosition pos = node.getNodeSourcePosition();
            if (pos != null) {
                if (!MethodFilter.matches(methodFilter, pos.getMethod())) {
                    return null;
                }
                if (phase.instrumentPerInlineSite()) {
                    StringBuilder sb = new StringBuilder();
                    while (pos != null) {
                        MetaUtil.appendLocation(sb.append("at "), pos.getMethod(), pos.getBCI());
                        pos = pos.getCaller();
                        if (pos != null) {
                            sb.append(CodeUtil.NEW_LINE);
                        }
                    }
                    return sb.toString();
                } else {
                    return MetaUtil.appendLocation(new StringBuilder(), pos.getMethod(), pos.getBCI()).toString();
                }
            } else {
                // IfNode has no position information, and is probably synthetic, so we do not
                // instrument it.
                return null;
            }
        }

        private static String prettify(String key, Point p) {
            if (p.isPrettified()) {
                StringBuilder sb = new StringBuilder();
                NodeSourcePosition pos = p.getPosition();
                NodeSourcePosition lastPos = null;
                int repetitions = 1;

                callerChainLoop: while (pos != null) {
                    // Skip stack frame if it is a known pattern.
                    for (String pattern : OMITTED_STACK_PATTERNS) {
                        if (pos.getMethod().format("%H.%n(%p)").contains(pattern)) {
                            pos = pos.getCaller();
                            continue callerChainLoop;
                        }
                    }

                    if (lastPos == null) {
                        // Always output first method.
                        lastPos = pos;
                        MetaUtil.appendLocation(sb, pos.getMethod(), pos.getBCI());
                    } else if (!lastPos.getMethod().equals(pos.getMethod())) {
                        // Output count for identical BCI outputs, and output next method.
                        if (repetitions > 1) {
                            sb.append(" x" + repetitions);
                            repetitions = 1;
                        }
                        sb.append(CodeUtil.NEW_LINE);
                        lastPos = pos;
                        MetaUtil.appendLocation(sb, pos.getMethod(), pos.getBCI());
                    } else if (lastPos.getBCI() != pos.getBCI()) {
                        // Conflate identical BCI outputs.
                        if (repetitions > 1) {
                            sb.append(" x" + repetitions);
                            repetitions = 1;
                        }
                        lastPos = pos;
                        sb.append(" [bci: " + pos.getBCI() + "]");
                    } else {
                        // Identical BCI to the one seen previously.
                        repetitions++;
                    }
                    pos = pos.getCaller();
                }
                if (repetitions > 1) {
                    sb.append(" x" + repetitions);
                    repetitions = 1;
                }
                return sb.toString();
            } else {
                return key;
            }
        }

        public synchronized ArrayList<String> accessTableToList() {
            return pointMap.entrySet().stream().sorted(entriesComparator).map(entry -> prettify(entry.getKey(), entry.getValue()) + CodeUtil.NEW_LINE + entry.getValue()).collect(
                            Collectors.toCollection(ArrayList::new));
        }

        public synchronized ArrayList<String> accessTableToHistogram() {
            long totalExecutions = pointMap.values().stream().mapToLong(v -> v.getHotness()).sum();
            return pointMap.entrySet().stream().sorted(entriesComparator).map(entry -> {
                int length = (int) ((1.0 * entry.getValue().getHotness() / totalExecutions) * 80);
                String bar = String.join("", Collections.nCopies(length, "*"));
                return String.format("%3d: %s", entry.getValue().getId(), bar);
            }).collect(Collectors.toCollection(ArrayList::new));
        }

        public synchronized void dumpAccessTable() {
            // Dump accumulated profiling information.
            TTY.println("Execution profile (sorted by hotness)");
            TTY.println("=====================================");
            for (String line : accessTableToHistogram()) {
                TTY.println(line);
            }
            TTY.println();
            for (String line : accessTableToList()) {
                TTY.println(line);
                TTY.println();
            }
        }

        public synchronized Instrumentation.Point getOrCreatePoint(MethodFilter[] methodFilter, Node n, InstrumentPhase phase) {
            String key = filterAndEncode(methodFilter, n, phase);
            if (key == null) {
                return null;
            }
            Instrumentation.Point existing = pointMap.get(key);
            int slotCount = phase.instrumentationPointSlotCount();
            if (existing != null) {
                return existing;
            } else if (tableStartIndex + slotCount < ACCESS_TABLE.length) {
                int id = tableIdCount++;
                int startIndex = tableStartIndex;
                tableStartIndex += slotCount;
                Instrumentation.Point p = phase.createPoint(id, startIndex, n);
                pointMap.put(key, p);
                return p;
            } else {
                if (tableStartIndex < ACCESS_TABLE.length) {
                    TTY.println("Maximum number of instrumentation counters exceeded.");
                    tableStartIndex += slotCount;
                }
                return null;
            }
        }

        public abstract static class Point {
            protected int id;
            protected int rawIndex;
            protected NodeSourcePosition position;
            protected boolean prettify;

            public Point(int id, int rawIndex, NodeSourcePosition position, boolean prettify) {
                this.id = id;
                this.rawIndex = rawIndex;
                this.position = position;
                this.prettify = prettify;
            }

            public int slotIndex(int offset) {
                assert offset < slotCount() : "Offset exceeds instrumentation point's slot count: " + offset;
                return rawIndex + offset;
            }

            public int getId() {
                return id;
            }

            public boolean isPrettified() {
                return prettify;
            }

            public NodeSourcePosition getPosition() {
                return position;
            }

            public abstract int slotCount();

            public abstract long getHotness();
        }
    }
}
