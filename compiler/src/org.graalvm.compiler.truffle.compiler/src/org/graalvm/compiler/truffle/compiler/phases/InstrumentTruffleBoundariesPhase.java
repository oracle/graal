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
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.options.OptionValues;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Instruments calls to {@code TruffleBoundary}-annotated methods in the graph, by adding execution
 * counters to respective callsites. If this phase is enabled, the runtime outputs a summary of all
 * such compiled callsites and their execution counts, when the program exits.
 *
 * The phase is enabled with the following flag:
 *
 * <pre>
 * -Dgraal.TruffleInstrumentBoundaries
 * </pre>
 *
 * The phase can be configured to only instrument callsites in specific methods, by providing the
 * following method filter flag:
 *
 * <pre>
 * -Dgraal.TruffleInstrumentBoundariesFilter
 * </pre>
 *
 * The flag:
 *
 * <pre>
 * -Dgraal.TruffleInstrumentBoundariesPerInlineSite
 * </pre>
 *
 * decides whether to treat different inlining sites separately when tracking the execution counts.
 */
public class InstrumentTruffleBoundariesPhase extends InstrumentPhase {

    private final boolean isInstrumentPerInlineSite;

    public InstrumentTruffleBoundariesPhase(OptionValues options, SnippetReflectionProvider snippetReflection, Instrumentation instrumentation, boolean instrumentPerInlineSite) {
        super(options, snippetReflection, instrumentation);
        isInstrumentPerInlineSite = instrumentPerInlineSite;
    }

    @Override
    protected void instrumentGraph(StructuredGraph graph, CoreProviders context, JavaConstant tableConstant) {
        TruffleCompilerRuntime runtime = TruffleCompilerRuntime.getRuntime();
        for (Node n : graph.getNodes()) {
            if (n instanceof Invoke && runtime.isTruffleBoundary(((Invoke) n).callTarget().targetMethod())) {
                Point p = getOrCreatePoint(n);
                if (p != null) {
                    insertCounter(graph, context, tableConstant, (FixedWithNextNode) n.predecessor(), p.slotIndex(0));
                }
            }
        }
    }

    @Override
    protected int instrumentationPointSlotCount() {
        return 1;
    }

    @Override
    protected boolean instrumentPerInlineSite() {
        return isInstrumentPerInlineSite;
    }

    @Override
    protected Point createPoint(int id, int startIndex, Node n) {
        return new BoundaryPoint(id, startIndex, n.getNodeSourcePosition());
    }

    public class BoundaryPoint extends Point {
        BoundaryPoint(int id, int rawIndex, NodeSourcePosition position) {
            super(id, rawIndex, position);
        }

        @Override
        public int slotCount() {
            return 1;
        }

        @Override
        public boolean isPrettified() {
            return isInstrumentPerInlineSite;
        }

        @Override
        public long getHotness() {
            return getInstrumentation().getAccessTable()[rawIndex];
        }

        @Override
        public String toString() {
            return "[" + id + "] count = " + getHotness();
        }
    }
}
