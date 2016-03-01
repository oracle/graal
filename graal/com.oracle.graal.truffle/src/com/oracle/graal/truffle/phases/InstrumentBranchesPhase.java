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

import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.AbstractBeginNode;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.IfNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.java.StoreIndexedNode;
import com.oracle.graal.phases.BasePhase;
import com.oracle.graal.phases.tiers.HighTierContext;

public class InstrumentBranchesPhase extends BasePhase<HighTierContext> {

    public static final Pattern METHOD_REGEX_FILTER = Pattern.compile(TruffleInstrumentBranchesFilter.getValue());
    public static final int TABLE_SIZE = TruffleInstrumentBranchesCount.getValue();
    public static final boolean[] TABLE = new boolean[TABLE_SIZE];

    public static class BranchInstrumentation {

        public enum BranchState {
            NONE,
            IF,
            ELSE,
            BOTH;

            public static BranchState from(boolean ifVisited, boolean elseVisited) {
                if (ifVisited && elseVisited)
                    return BOTH;
                else if (ifVisited && !elseVisited)
                    return IF;
                else if (!ifVisited && elseVisited)
                    return ELSE;
                else
                    return NONE;
            }
        }

        private class Point {
            private int index;

            public Point(int index) {
                this.index = index;
            }

            public int getIndex() {
                return index;
            }

            public BranchState getBranchState() {
                int rawIndex = index * 2;
                boolean ifVisited = TABLE[rawIndex];
                boolean elseVisited = TABLE[rawIndex + 1];
                return BranchState.from(ifVisited, elseVisited);
            }

            public int getRawIndex(boolean isTrue) {
                int rawIndex = index * 2;
                if (!isTrue)
                    rawIndex += 1;
                return rawIndex;
            }

            @Override
            public String toString() {
                return "[" + index + "] state = " + getBranchState();
            }
        }

        public Map<String, Point> pointMap = new LinkedHashMap<>();
        public int tableCount = 0;

        public BranchInstrumentation() {
            // Dump profiling information when exiting.
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    if (TruffleInstrumentBranches.getValue()) {
                        System.out.println("Branch execution profile");
                        System.out.println("========================");
                        for (Map.Entry<String, Point> entry : pointMap.entrySet()) {
                            System.out.println(entry.getKey() + ": " + entry.getValue() + "\n");
                        }
                    }
                }
            });
        }

        /*
         * Node source location is determined by its inlining chain. The first location in the chain
         * refers to the location in the original method, so we use that to determine the unique
         * source location key.
         */
        private String encode(Node ifNode) {
            SourceLocation loc = ifNode.getNodeContext(SourceLocation.class);
            if (loc != null) {
                return loc.getPosition().toString().replace("\n", ",");
            } else {
                // IfNode has no position information, and is probably synthetic, so we do not
                // instrument it.
                return null;
            }
        }

        public synchronized Point getOrCreatePoint(IfNode n) {
            String key = encode(n);
            if (key == null)
                return null;
            Point existing = pointMap.get(key);
            if (existing != null)
                return existing;
            else {
                int index = tableCount++;
                Point p = new Point(index);
                pointMap.put(key, p);
                return p;
            }
        }

    }

    public static BranchInstrumentation instrumentation = new BranchInstrumentation();

    public static class SourceLocation {
        private BytecodePosition position;

        public SourceLocation(BytecodePosition position) {
            this.position = position;
        }

        public BytecodePosition getPosition() {
            return position;
        }
    }

    private void insertCounter(StructuredGraph graph, HighTierContext context, IfNode ifNode,
                    BranchInstrumentation.Point p, boolean isTrue) {
        Field javaField = null;
        try {
            javaField = InstrumentBranchesPhase.class.getField("TABLE");
        } catch (Exception e) {
            return;
        }
        AbstractBeginNode beginNode = (isTrue) ? ifNode.trueSuccessor() : ifNode.falseSuccessor();
        ResolvedJavaField tableField = context.getMetaAccess().lookupJavaField(javaField);
        JavaConstant tableConstant = context.getConstantReflection().readConstantFieldValue(tableField, null);
        assert (tableConstant != null);
        ConstantNode table = graph.unique(new ConstantNode(tableConstant, StampFactory.exactNonNull((ResolvedJavaType) tableField.getType())));
        ConstantNode rawIndex = graph.unique(ConstantNode.forInt(p.getRawIndex(isTrue)));
        ConstantNode v = graph.unique(ConstantNode.forBoolean(true));
        StoreIndexedNode store = graph.add(new StoreIndexedNode(table, rawIndex, JavaKind.Boolean, v));

        graph.addAfterFixed(beginNode, store);
    }

    public static void addNodeSourceLocation(Node node, BytecodePosition pos) {
        node.setNodeContext(new SourceLocation(pos));
    }

    @Override
    protected void run(StructuredGraph graph, HighTierContext context) {
        if (METHOD_REGEX_FILTER.matcher(graph.method().getName()).matches()) {
            try {
                for (IfNode n : graph.getNodes().filter(IfNode.class)) {
                    BranchInstrumentation.Point p = instrumentation.getOrCreatePoint(n);
                    if (p != null) {
                        insertCounter(graph, context, n, p, true);
                        insertCounter(graph, context, n, p, false);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
