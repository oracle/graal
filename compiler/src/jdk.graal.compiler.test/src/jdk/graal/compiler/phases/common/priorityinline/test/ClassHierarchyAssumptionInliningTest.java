/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.priorityinline.test;

import java.util.ListIterator;

import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.core.test.TestGraphBuilderPhase;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;

public class ClassHierarchyAssumptionInliningTest extends GraalCompilerTest {

    public static int interfaceCallee(Interface obj) {
        return obj.m();
    }

    private StructuredGraph parseGraph(String method, Runnable init1) {
        init1.run();
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        StructuredGraph graph = parseEager(method, StructuredGraph.AllowAssumptions.YES);
        canonicalizer.apply(graph, getDefaultHighTierContext());
        new PriorityInliningPhase(canonicalizer, getInitialOptions()).apply(graph, getDefaultHighTierContext());
        return graph;
    }

    @Test
    public void testCHA() {
        Runnable init1 = new Runnable() {
            @SuppressWarnings("unused")
            @Override
            public void run() {
                new ImplClass1();
            }
        };
        StructuredGraph graph1 = parseGraph("interfaceCallee", init1);
        assertFalse(graph1.getInvokes().iterator().hasNext());

        Runnable init2 = new Runnable() {
            @SuppressWarnings("unused")
            @Override
            public void run() {
                new ImplClass2();
            }
        };
        StructuredGraph graph2 = parseGraph("interfaceCallee", init2);
        assertFalse(graph2.getInvokes().iterator().hasNext());

        Runnable init3 = new Runnable() {
            @SuppressWarnings("unused")
            @Override
            public void run() {
                new UnrelatedClass1();
            }
        };
        StructuredGraph graph3 = parseGraph("interfaceCallee", init3);
        assertFalse(graph3.getInvokes().iterator().hasNext());

        Runnable init4 = new Runnable() {
            @SuppressWarnings("unused")
            @Override
            public void run() {
                new SiblingClass1();
            }
        };
        StructuredGraph graph4 = parseGraph("interfaceCallee", init4);
        assertTrue(graph4.getInvokes().iterator().hasNext());
    }

    public static int superInterfaceCallee(SuperInterface obj) {
        return obj.m();
    }

    @Test
    public void testDefaultMethods() {
        Runnable init1 = new Runnable() {
            @SuppressWarnings("unused")
            @Override
            public void run() {
                new UnrelatedClass1();
            }
        };
        StructuredGraph graph1 = parseGraph("superInterfaceCallee", init1);
        assertFalse(graph1.getInvokes().iterator().hasNext());
    }

    interface SuperInterface {
        default int m() {
            return -1;
        }
    }

    class UnrelatedClass1 implements SuperInterface {
    }

    interface Interface {
        int m();
    }

    static class BaseClass implements Interface {
        @Override
        public int m() {
            return 1;
        }
    }

    static class ImplClass1 extends BaseClass {
    }

    static class ImplClass2 extends BaseClass {
    }

    static class SiblingClass1 implements Interface {
        @Override
        public int m() {
            return 3;
        }
    }

    @Override
    protected PhaseSuite<HighTierContext> getDefaultGraphBuilderSuite() {
        // Pass @BytecodeParserNeverInline plugins to the inliner.
        PhaseSuite<HighTierContext> suite = super.getDefaultGraphBuilderSuite();
        ListIterator<BasePhase<? super HighTierContext>> iterator = suite.findPhase(GraphBuilderPhase.class);
        GraphBuilderConfiguration gbConfCopy = editGraphBuilderConfiguration(GraphBuilderConfiguration.getDefault(getDefaultGraphBuilderPlugins()));
        iterator.remove();
        iterator.add(new TestGraphBuilderPhase(gbConfCopy));
        return suite;
    }
}
