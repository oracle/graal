/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import static jdk.graal.compiler.api.directives.GraalDirectives.controlFlowAnchor;
import static jdk.graal.compiler.api.directives.GraalDirectives.deoptimize;
import static jdk.graal.compiler.api.directives.GraalDirectives.injectBranchProbability;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeFlood;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MonitorEnterNode;
import jdk.graal.compiler.nodes.java.MonitorExitNode;

/**
 * Tests that PEA preserves the monitorenter order. This is essential for lightweight locking.
 */
public final class MonitorPEATest extends HotSpotGraalCompilerTest {

    @Before
    public void checkUseLightweightLocking() {
        Assume.assumeTrue(HotSpotReplacementsUtil.useLightweightLocking(runtime().getVMConfig()));
    }

    static int staticInt = 0;
    static Object staticObj;
    static Object staticObj1;

    public static void snippet0(boolean flag) {
        Object escaped0 = new Object();
        Object escaped1 = new Object();

        if (injectBranchProbability(0.01, flag)) {
            synchronized (escaped0) {
                synchronized (escaped1) {
                    staticObj1 = escaped1;
                    staticObj = escaped0;
                    staticInt++;
                }
            }
        }
    }

    @Test
    public void testSnippet0() {
        test("snippet0", true);
    }

    private static native void foo(Object a, Object b);

    public static void snippet1(boolean flag) {
        Object escaped1 = new Object();
        Object escaped0 = new Object();

        if (injectBranchProbability(0.01, flag)) {
            synchronized (escaped0) {
                synchronized (escaped1) {
                    foo(escaped1, escaped0);
                    staticInt++;
                }
            }
        }
    }

    @Test
    public void testSnippet1() {
        test("snippet1", false);
    }

    public static void snippet2(boolean flag, boolean flag2) {
        Object escaped = new Object();
        Object nonEscaped = new Object();

        if (injectBranchProbability(0.01, flag)) {
            synchronized (nonEscaped) {
                synchronized (escaped) {
                    staticObj = escaped;
                    staticInt++;
                    if (flag2) {
                        deoptimize();
                    }
                    controlFlowAnchor();
                }
            }
        }
    }

    @Test
    public void testSnippet2() {
        test("snippet2", true, true);
    }

    public static void snippet3(Object external, boolean flag) {
        Object escaped = new Object();

        if (injectBranchProbability(0.01, flag)) {
            synchronized (escaped) {
                synchronized (external) {
                    staticObj = escaped;
                }
            }
        }
    }

    @Test
    public void testSnippet3() {
        test("snippet3", new Object(), true);
    }

    static class A {
        Object o = new Object();
    }

    @SuppressWarnings("unused")
    public static void snippet4(Object external, boolean flag, boolean flag1) {
        A escaped = new A();

        synchronized (escaped) {
            synchronized (external) {
                if (injectBranchProbability(0.1, flag)) {
                    staticInt++;
                    staticObj1 = escaped.o;
                    staticObj = escaped;
                } else {
                    staticInt += 2;
                    staticObj = escaped.o;
                    staticObj1 = escaped;
                }
            }
        }
    }

    @Test
    public void testSnippet4() {
        test("snippet4", new Object(), true, true);
    }

    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        // The following lock depth check only works with simple control flow.
        NodeFlood flood = graph.createNodeFlood();
        flood.add(graph.start());

        int lockDepth = -1;

        for (Node current : flood) {
            if (current instanceof AbstractEndNode) {
                AbstractEndNode end = (AbstractEndNode) current;
                flood.add(end.merge());
            } else {
                if (current instanceof MonitorEnterNode enter) {
                    int depth = enter.getMonitorId().getLockDepth();
                    assertTrue(lockDepth < depth);
                    lockDepth = depth;
                } else if (current instanceof MonitorExitNode exit) {
                    int depth = exit.getMonitorId().getLockDepth();
                    assertTrue(lockDepth >= depth);
                    lockDepth = depth;
                }

                for (Node successor : current.successors()) {
                    flood.add(successor);
                }
            }
        }
    }
}
