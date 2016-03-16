/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.phases;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.compiler.common.type.TypeReference;
import com.oracle.graal.debug.TTY;
import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.AbstractBeginNode;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.IfNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.java.StoreIndexedNode;
import com.oracle.graal.phases.BasePhase;
import com.oracle.graal.phases.tiers.HighTierContext;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static com.oracle.graal.truffle.TruffleCompilerOptions.TruffleInstrumentBranchesCount;
import static com.oracle.graal.truffle.TruffleCompilerOptions.TruffleInstrumentBranchesFilter;

public class InstrumentBranchesPhase extends BasePhase<HighTierContext> {

    private static final Pattern METHOD_REGEX_FILTER = Pattern.compile(TruffleInstrumentBranchesFilter.getValue());
    private static final int ACCESS_TABLE_SIZE = TruffleInstrumentBranchesCount.getValue();
    private static final Field ACCESS_TABLE_JAVA_FIELD;
    public static final boolean[] ACCESS_TABLE = new boolean[ACCESS_TABLE_SIZE];
    public static BranchInstrumentation instrumentation = new BranchInstrumentation();

    static {
        Field javaField = null;
        try {
            javaField = InstrumentBranchesPhase.class.getField("ACCESS_TABLE");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ACCESS_TABLE_JAVA_FIELD = javaField;
    }

    @Override
    protected void run(StructuredGraph graph, HighTierContext context) {
        if (METHOD_REGEX_FILTER.matcher(graph.method().getName()).matches()) {
            ResolvedJavaField tableField = context.getMetaAccess().lookupJavaField(ACCESS_TABLE_JAVA_FIELD);
            JavaConstant tableConstant = context.getConstantReflection().readConstantFieldValue(tableField, null);
            try {
                for (IfNode n : graph.getNodes().filter(IfNode.class)) {
                    BranchInstrumentation.Point p = instrumentation.getOrCreatePoint(n);
                    if (p != null) {
                        insertCounter(graph, tableField, tableConstant, n, p, true);
                        insertCounter(graph, tableField, tableConstant, n, p, false);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void insertCounter(StructuredGraph graph, ResolvedJavaField tableField, JavaConstant tableConstant,
                    IfNode ifNode, BranchInstrumentation.Point p, boolean isTrue) {
        assert (tableConstant != null);
        AbstractBeginNode beginNode = (isTrue) ? ifNode.trueSuccessor() : ifNode.falseSuccessor();
        TypeReference typeRef = TypeReference.createExactTrusted((ResolvedJavaType) tableField.getType());
        ConstantNode table = graph.unique(new ConstantNode(tableConstant, StampFactory.object(typeRef, true)));
        ConstantNode rawIndex = graph.unique(ConstantNode.forInt(p.getRawIndex(isTrue)));
        ConstantNode v = graph.unique(ConstantNode.forBoolean(true));
        StoreIndexedNode store = graph.add(new StoreIndexedNode(table, rawIndex, JavaKind.Boolean, v));

        graph.addAfterFixed(beginNode, store);
    }

    public static void addNodeSourceLocation(Node node, BytecodePosition pos) {
        node.setNodeContext(new SourceLocation(pos));
    }

    public static class SourceLocation {
        private BytecodePosition position;

        public SourceLocation(BytecodePosition position) {
            this.position = position;
        }

        public BytecodePosition getPosition() {
            return position;
        }
    }

    public static class BranchInstrumentation {

        public Map<String, Point> pointMap = new LinkedHashMap<>();
        public int tableCount = 0;

        /*
         * Node source location is determined by its inlining chain. The first location in the chain
         * refers to the location in the original method, so we use that to determine the unique
         * source location key.
         */
        private static String encode(Node ifNode) {
            SourceLocation loc = ifNode.getNodeContext(SourceLocation.class);
            if (loc != null) {
                return loc.getPosition().toString().replace("\n", ",");
            } else {
                // IfNode has no position information, and is probably synthetic, so we do not
                // instrument it.
                return null;
            }
        }

        public synchronized void dumpAccessTable() {
            // Dump accumulated profiling information.
            System.out.println("Branch execution profile");
            System.out.println("========================");
            for (Map.Entry<String, Point> entry : pointMap.entrySet()) {
                System.out.println(entry.getKey() + ": " + entry.getValue());
            }
        }

        public synchronized Point getOrCreatePoint(IfNode n) {
            String key = encode(n);
            if (key == null) {
                return null;
            }
            Point existing = pointMap.get(key);
            if (existing != null) {
                return existing;
            } else if (tableCount < ACCESS_TABLE.length) {
                int index = tableCount++;
                Point p = new Point(index);
                pointMap.put(key, p);
                return p;
            } else {
                if (tableCount == ACCESS_TABLE.length) {
                    TTY.println("Maximum number of branch instrumentation counters exceeded.");
                    tableCount += 1;
                }
                return null;
            }
        }

        public enum BranchState {
            NONE,
            IF,
            ELSE,
            BOTH;

            public static BranchState from(boolean ifVisited, boolean elseVisited) {
                if (ifVisited && elseVisited) {
                    return BOTH;
                } else if (ifVisited && !elseVisited) {
                    return IF;
                } else if (!ifVisited && elseVisited) {
                    return ELSE;
                } else {
                    return NONE;
                }
            }
        }

        private class Point {
            private int index;

            Point(int index) {
                this.index = index;
            }

            public BranchState getBranchState() {
                int rawIndex = index * 2;
                boolean ifVisited = ACCESS_TABLE[rawIndex];
                boolean elseVisited = ACCESS_TABLE[rawIndex + 1];
                return BranchState.from(ifVisited, elseVisited);
            }

            public int getRawIndex(boolean isTrue) {
                int rawIndex = index * 2;
                if (!isTrue) {
                    rawIndex += 1;
                }
                return rawIndex;
            }

            @Override
            public String toString() {
                return "[" + index + "] state = " + getBranchState();
            }
        }

    }
}
