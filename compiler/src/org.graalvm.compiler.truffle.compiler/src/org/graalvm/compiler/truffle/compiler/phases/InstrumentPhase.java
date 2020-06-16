/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.phases;

import static org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions.getPolyglotOptionValue;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InstrumentBoundaries;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InstrumentBoundariesPerInlineSite;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InstrumentBranches;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InstrumentBranchesPerInlineSite;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InstrumentationTableSize;

import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
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
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.options.OptionValues;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaUtil;

public abstract class InstrumentPhase extends BasePhase<CoreProviders> {

    private static boolean checkMethodExists(String declaringClassName, String methodName) {
        try {
            Class<?> declaringClass = Class.forName(declaringClassName);
            for (Method m : declaringClass.getDeclaredMethods()) {
                if (m.getName().equals(methodName)) {
                    return true;
                }
            }
        } catch (ClassNotFoundException e) {
            throw new NoClassDefFoundError(declaringClassName);
        }
        throw new NoSuchMethodError(declaringClassName + "." + methodName);
    }

    private static String asStackPattern(String declaringClassName, String methodName) {
        assert checkMethodExists(declaringClassName, methodName);
        return declaringClassName + "." + methodName;
    }

    private static final String[] OMITTED_STACK_PATTERNS = new String[]{
                    asStackPattern("org.graalvm.compiler.truffle.runtime.OptimizedCallTarget", "executeRootNode"),
                    asStackPattern("org.graalvm.compiler.truffle.runtime.OptimizedCallTarget", "profiledPERoot"),
                    asStackPattern("org.graalvm.compiler.truffle.runtime.OptimizedCallTarget", "callDirect"),
                    asStackPattern("org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode", "call"),
    };
    private final Instrumentation instrumentation;
    protected final MethodFilter methodFilter;
    protected final SnippetReflectionProvider snippetReflection;

    public InstrumentPhase(OptionValues options, SnippetReflectionProvider snippetReflection, Instrumentation instrumentation) {
        String filterValue = instrumentationFilter(options);
        if (filterValue != null) {
            methodFilter = MethodFilter.parse(filterValue);
        } else {
            methodFilter = MethodFilter.matchNothing();
        }
        this.snippetReflection = snippetReflection;
        this.instrumentation = instrumentation;
    }

    public Instrumentation getInstrumentation() {
        return instrumentation;
    }

    protected String instrumentationFilter(OptionValues options) {
        return getPolyglotOptionValue(options, PolyglotCompilerOptions.InstrumentFilter);
    }

    protected static void insertCounter(StructuredGraph graph, CoreProviders context, JavaConstant tableConstant,
                    FixedWithNextNode targetNode, int slotIndex) {
        assert (tableConstant != null);
        TypeReference typeRef = TypeReference.createExactTrusted(context.getMetaAccess().lookupJavaType(tableConstant));
        ConstantNode table = graph.unique(new ConstantNode(tableConstant, StampFactory.object(typeRef, true)));
        ConstantNode rawIndex = graph.unique(ConstantNode.forInt(slotIndex));
        LoadIndexedNode load = graph.add(new LoadIndexedNode(null, table, rawIndex, null, JavaKind.Long));
        ConstantNode one = graph.unique(ConstantNode.forLong(1L));
        ValueNode add = graph.unique(new AddNode(load, one));
        StoreIndexedNode store = graph.add(new StoreIndexedNode(table, rawIndex, null, null, JavaKind.Long, add));

        graph.addAfterFixed(targetNode, load);
        graph.addAfterFixed(load, store);
    }

    @Override
    public float codeSizeIncrease() {
        return 2.5f;
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        JavaConstant tableConstant = snippetReflection.forObject(instrumentation.getAccessTable());
        try {
            instrumentGraph(graph, context, tableConstant);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract void instrumentGraph(StructuredGraph graph, CoreProviders context, JavaConstant tableConstant);

    protected abstract int instrumentationPointSlotCount();

    protected abstract boolean instrumentPerInlineSite();

    protected abstract Point createPoint(int id, int startIndex, Node n);

    public Point getOrCreatePoint(Node n) {
        Point point = instrumentation.getOrCreatePoint(methodFilter, n, this);
        assert point == null || point.slotCount() == instrumentationPointSlotCount() : "Slot count mismatch between instrumentation point and expected value.";
        return point;
    }

    public static class Instrumentation {
        private Comparator<Point> pointsComparator = new Comparator<Point>() {
            @Override
            public int compare(Point x, Point y) {
                long diff = y.getHotness() - x.getHotness();
                if (diff < 0) {
                    return -1;
                } else if (diff == 0) {
                    return 0;
                } else {
                    return 1;
                }
            }
        };
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
        private final long[] accessTable;
        public Map<String, Point> pointMap = new LinkedHashMap<>();
        public int tableIdCount;
        public int tableStartIndex;

        public Instrumentation(long[] accessTable) {
            this.accessTable = accessTable;
        }

        /*
         * Node source location is determined by its inlining chain. A flag value controls whether
         * we discriminate nodes by their inlining site, or only by the method in which they were
         * defined.
         */
        private static String filterAndEncode(MethodFilter methodFilter, Node node, InstrumentPhase phase) {
            NodeSourcePosition pos = node.getNodeSourcePosition();
            if (pos != null) {
                if (!methodFilter.matches(pos.getMethod())) {
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

            /*
             * Using sortedEntries.addAll(pointMap.entrySet(), instead of the iteration below, is
             * not safe and is detected by FindBugs. From FindBugs:
             *
             * "The entrySet() method is allowed to return a view of the underlying Map in which a
             * single Entry object is reused and returned during the iteration. As of Java 1.6, both
             * IdentityHashMap and EnumMap did so. When iterating through such a Map, the Entry
             * value is only valid until you advance to the next iteration. If, for example, you try
             * to pass such an entrySet to an addAll method, things will go badly wrong."
             */
            List<Map.Entry<String, Point>> sortedEntries = new ArrayList<>();
            for (Map.Entry<String, Point> entry : pointMap.entrySet()) {
                if (entry.getValue().shouldInclude()) {
                    Map.Entry<String, Point> immutableEntry = new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue());
                    sortedEntries.add(immutableEntry);
                }
            }

            Collections.sort(sortedEntries, entriesComparator);

            ArrayList<String> list = new ArrayList<>();
            for (Map.Entry<String, Point> entry : sortedEntries) {
                list.add(prettify(entry.getKey(), entry.getValue()) + CodeUtil.NEW_LINE + entry.getValue());
            }
            return list;
        }

        public synchronized ArrayList<String> accessTableToHistogram() {
            long totalExecutions = 0;
            for (Point point : pointMap.values()) {
                totalExecutions += point.getHotness();
            }

            List<Point> sortedPoints = new ArrayList<>();
            for (Point p : pointMap.values()) {
                if (p.shouldInclude()) {
                    sortedPoints.add(p);
                }
            }
            Collections.sort(sortedPoints, pointsComparator);

            ArrayList<String> histogram = new ArrayList<>();
            for (Point point : sortedPoints) {
                int length = (int) ((1.0 * point.getHotness() / totalExecutions) * 80);
                String bar = String.join("", Collections.nCopies(length, "*"));
                histogram.add(String.format("%3d: %s", point.getId(), bar));
            }
            return histogram;
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

        public synchronized Point getOrCreatePoint(MethodFilter methodFilter, Node n, InstrumentPhase phase) {
            String key = filterAndEncode(methodFilter, n, phase);
            if (key == null) {
                return null;
            }
            Point existing = pointMap.get(key);
            int slotCount = phase.instrumentationPointSlotCount();
            if (existing != null) {
                return existing;
            } else if (tableStartIndex + slotCount < phase.getInstrumentation().getAccessTable().length) {
                int id = tableIdCount++;
                int startIndex = tableStartIndex;
                tableStartIndex += slotCount;
                Point p = phase.createPoint(id, startIndex, n);
                pointMap.put(key, p);
                return p;
            } else {
                if (tableStartIndex < phase.getInstrumentation().getAccessTable().length) {
                    TTY.println("Maximum number of instrumentation counters exceeded.");
                    tableStartIndex += slotCount;
                }
                return null;
            }
        }

        public long[] getAccessTable() {
            return accessTable;
        }
    }

    public static final class InstrumentationConfiguration {

        public final boolean instrumentBranches;
        public final boolean instrumentBranchesPerInlineSite;
        public final boolean instrumentBoundaries;
        public final boolean instrumentBoundariesPerInlineSite;
        public final int instrumentationTableSize;

        public InstrumentationConfiguration(OptionValues options) {
            this.instrumentBranches = getPolyglotOptionValue(options, InstrumentBranches);
            this.instrumentBranchesPerInlineSite = getPolyglotOptionValue(options, InstrumentBranchesPerInlineSite);
            this.instrumentBoundaries = getPolyglotOptionValue(options, InstrumentBoundaries);
            this.instrumentBoundariesPerInlineSite = getPolyglotOptionValue(options, InstrumentBoundariesPerInlineSite);
            this.instrumentationTableSize = getPolyglotOptionValue(options, InstrumentationTableSize);
        }
    }

    public abstract static class Point {
        protected int id;
        protected int rawIndex;
        protected NodeSourcePosition position;

        public Point(int id, int rawIndex, NodeSourcePosition position) {
            this.id = id;
            this.rawIndex = rawIndex;
            this.position = position;
        }

        public int slotIndex(int offset) {
            assert offset < slotCount() : "Offset exceeds instrumentation point's slot count: " + offset;
            return rawIndex + offset;
        }

        public int getId() {
            return id;
        }

        public NodeSourcePosition getPosition() {
            return position;
        }

        public abstract int slotCount();

        public abstract long getHotness();

        public abstract boolean isPrettified();

        public boolean shouldInclude() {
            return true;
        }
    }
}
