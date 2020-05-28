/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.options.OptionValues;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Instruments {@link IfNode}s in the graph, by adding execution counters to the true and the false
 * branch of each {@link IfNode}. If this phase is enabled, the runtime outputs a summary of all the
 * compiled {@link IfNode}s and the execution count of their branches, when the program exits.
 *
 * The phase is enabled with the following flag:
 *
 * <pre>
 * -Dgraal.TruffleInstrumentBranches
 * </pre>
 *
 * The flag:
 *
 * <pre>
 * -Dgraal.TruffleInstrumentBranchesPerInlineSite
 * </pre>
 *
 * decides whether to treat different inlining sites separately when tracking the execution counts
 * of an {@link IfNode}.
 */
public class InstrumentBranchesPhase extends InstrumentPhase {

    private final boolean isInstrumentPerInlineSite;

    public InstrumentBranchesPhase(OptionValues options, SnippetReflectionProvider snippetReflection, Instrumentation instrumentation, boolean instrumentPerInlineSite) {
        super(options, snippetReflection, instrumentation);
        isInstrumentPerInlineSite = instrumentPerInlineSite;
    }

    @Override
    protected void instrumentGraph(StructuredGraph graph, CoreProviders context, JavaConstant tableConstant) {
        for (IfNode n : graph.getNodes().filter(IfNode.class)) {
            Point p = getOrCreatePoint(n);
            if (p != null) {
                insertCounter(graph, context, tableConstant, n.trueSuccessor(), p.slotIndex(0));
                insertCounter(graph, context, tableConstant, n.falseSuccessor(), p.slotIndex(1));
            }
        }
    }

    @Override
    protected int instrumentationPointSlotCount() {
        return 2;
    }

    @Override
    protected boolean instrumentPerInlineSite() {
        return isInstrumentPerInlineSite;
    }

    @Override
    protected Point createPoint(int id, int startIndex, Node n) {
        return new IfPoint(id, startIndex, n.getNodeSourcePosition());
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

    public class IfPoint extends InstrumentPhase.Point {
        IfPoint(int id, int rawIndex, NodeSourcePosition position) {
            super(id, rawIndex, position);
        }

        @Override
        public int slotCount() {
            return 2;
        }

        @Override
        public boolean isPrettified() {
            return isInstrumentPerInlineSite;
        }

        public long ifVisits() {
            return getInstrumentation().getAccessTable()[rawIndex];
        }

        public long elseVisits() {
            return getInstrumentation().getAccessTable()[rawIndex + 1];
        }

        public BranchState getBranchState() {
            return BranchState.from(ifVisits() > 0, elseVisits() > 0);
        }

        public String getCounts() {
            return "if=" + ifVisits() + "#, else=" + elseVisits() + "#";
        }

        @Override
        public long getHotness() {
            return ifVisits() + elseVisits();
        }

        @Override
        public String toString() {
            return "[" + id + "] state = " + getBranchState() + "(" + getCounts() + ")";
        }
    }
}
