/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.TestPhase;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.CheckFastPathMonitorEnterNode;
import jdk.graal.compiler.nodes.java.MonitorEnterNode;
import jdk.graal.compiler.nodes.java.MonitorIdNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.MidTierLoweringPhase;
import jdk.graal.compiler.phases.tiers.Suites;

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
    private boolean verifySnippet23Graph;

    @Override
    protected Suites createSuites(OptionValues opts) {
        Suites suites = super.createSuites(opts);
        suites.getMidTier().insertBeforePhase(MidTierLoweringPhase.class, new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                if (shouldVerifySnippet23Graph(graph)) {
                    Assert.assertTrue("expected snippet23 to materialize at least one virtual object before mid-tier lowering", countCommitAllocations(graph) > 0);
                    Assert.assertEquals("eliminated locks should not be carried into materialization before mid-tier lowering", 0, countEliminatedCommitLocks(graph));
                }
            }
        });
        suites.getMidTier().insertAfterPhase(MidTierLoweringPhase.class, new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                if (shouldVerifySnippet23Graph(graph)) {
                    Assert.assertEquals("all commit allocations should be lowered", 0, countCommitAllocations(graph));
                    Assert.assertEquals("eliminated locks should not produce monitor enters", 0, countEliminatedMonitorEnters(graph));
                    Assert.assertTrue("expected snippet23 to emit monitor enters after lowering", countMonitorEnters(graph) > 0);
                    Assert.assertTrue("expected snippet23 to emit fast-path checks for materialized locks", countFastPathMonitorEnterLocks(graph) > 0);
                }
            }
        });
        return suites;
    }

    private boolean shouldVerifySnippet23Graph(StructuredGraph graph) {
        return verifySnippet23Graph && graph.method() != null && "snippet23".equals(graph.method().getName());
    }

    private static int countCommitAllocations(StructuredGraph graph) {
        return graph.getNodes().filter(CommitAllocationNode.class).count();
    }

    private static int countEliminatedCommitLocks(StructuredGraph graph) {
        int count = 0;
        for (CommitAllocationNode commit : graph.getNodes().filter(CommitAllocationNode.class)) {
            for (MonitorIdNode lock : commit.getLocks()) {
                if (lock.isEliminated()) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countMonitorEnters(StructuredGraph graph) {
        return graph.getNodes().filter(MonitorEnterNode.class).count();
    }

    private static int countEliminatedMonitorEnters(StructuredGraph graph) {
        int count = 0;
        for (MonitorEnterNode enter : graph.getNodes().filter(MonitorEnterNode.class)) {
            if (enter.getMonitorId().isEliminated()) {
                count++;
            }
        }
        return count;
    }

    private static int countFastPathMonitorEnterLocks(StructuredGraph graph) {
        int count = 0;
        for (CheckFastPathMonitorEnterNode check : graph.getNodes().filter(CheckFastPathMonitorEnterNode.class)) {
            count += check.lockDepth();
        }
        return count;
    }

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

    record A(Object o) {
    }

    @SuppressWarnings("unused")
    public static void snippet4(Object external, boolean flag, boolean flag1) {
        A escaped = new A(new Object());

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

    public static class B {
        Float f = 1.0f;
    }

    public static void snippet5() {
        synchronized (new B()) {
            synchronized (new StringBuilder()) {
                synchronized (B.class) {
                }
            }
        }
    }

    @Test
    public void testSnippet5() {
        test("snippet5");
    }

    static class C {
        B b;

        C(B b) {
            this.b = b;
        }
    }

    public static void snippet6() {
        B b = new B();
        C c = new C(b);
        synchronized (c) {
            synchronized (b) {
                synchronized (B.class) {
                }
            }
        }
    }

    @Test
    public void testSnippet6() {
        test("snippet6");
    }

    public static void snippet7() {
        B b = new B();
        C c = new C(b);
        synchronized (c) {
            synchronized (c) {
                synchronized (b) {
                    synchronized (b) {
                        synchronized (B.class) {
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testSnippet7() {
        test("snippet7");
    }

    public static void snippet8() {
        B b = new B();
        C c = new C(b);
        synchronized (c) {
            synchronized (b) {
                synchronized (c) {
                    synchronized (b) {
                        synchronized (B.class) {
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testSnippet8() {
        test("snippet8");
    }

    public static void snippet9() {
        B b = new B();
        C c = new C(b);
        synchronized (c) {
            synchronized (b) {
                synchronized (b) {
                    synchronized (c) {
                        synchronized (B.class) {
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testSnippet9() {
        test("snippet9");
    }

    @BytecodeParserNeverInline
    public static void callee0() {
        synchronized (B.class) {
        }
    }

    @BytecodeParserNeverInline
    public static void callee1() {
        staticObj = new B();
        callee0();
    }

    @BytecodeParserNeverInline
    public static void callee() {
        callee1();
    }

    public static void snippet10() {
        B b = new B();
        synchronized (b) {
            callee();
        }
    }

    @Test
    public void testSnippet10() {
        test("snippet10");
    }

    static RuntimeException cachedException = new RuntimeException();

    public static void snippet11(boolean flag) {
        B b = new B();
        synchronized (b) {
            if (flag) {
                staticObj = b;
                throw cachedException;
            }
        }
    }

    @Test
    public void testSnippet11() {
        test("snippet11", true);
    }

    public static void snippet12() {
        B b = new B();
        synchronized (b) {
            callee();
            staticObj = b;
        }
    }

    @Test
    public void testSnippet12() {
        test("snippet12");
    }

    public static void snippet13(boolean flag, boolean escape) {
        B b = new B();

        synchronized (b) {
            synchronized (b) {
                if (GraalDirectives.injectBranchProbability(0.9, flag)) {
                    GraalDirectives.controlFlowAnchor();
                    if (GraalDirectives.injectBranchProbability(0.1, escape)) {
                        staticObj = b;
                    }
                } else {
                    staticObj1 = b;
                    throw cachedException;
                }
            }
        }
    }

    @Test
    public void testSnippet13() {
        test("snippet13", true, true);
    }

    public static void snippet15(boolean deoptimize) {
        synchronized (new B()) {
            synchronized (new StringBuilder()) {
                synchronized (B.class) {
                    if (deoptimize) {
                        GraalDirectives.deoptimize();
                    }
                }
            }
        }
    }

    @Test
    public void testSnippet15() {
        test("snippet15", true);
    }

    public static void snippet16(boolean deoptimize) {
        B b = new B();
        C c = new C(b);
        synchronized (c) {
            synchronized (b) {
                synchronized (B.class) {
                    if (deoptimize) {
                        GraalDirectives.deoptimize();
                    }
                }
            }
        }
    }

    @Test
    public void testSnippet16() {
        test("snippet16", true);
    }

    public static void snippet17(boolean deoptimize) {
        B b = new B();
        C c = new C(b);
        synchronized (c) {
            synchronized (c) {
                synchronized (b) {
                    synchronized (b) {
                        synchronized (B.class) {
                            if (deoptimize) {
                                GraalDirectives.deoptimize();
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testSnippet17() {
        test("snippet17", true);
    }

    public static void snippet18(boolean deoptimize) {
        B b = new B();
        C c = new C(b);
        synchronized (c) {
            synchronized (b) {
                synchronized (c) {
                    synchronized (b) {
                        synchronized (B.class) {
                            if (deoptimize) {
                                GraalDirectives.deoptimize();
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testSnippet18() {
        test("snippet18", true);
    }

    public static void snippet19(boolean deoptimize) {
        B b = new B();
        C c = new C(b);
        synchronized (c) {
            synchronized (b) {
                synchronized (b) {
                    synchronized (c) {
                        synchronized (B.class) {
                            if (deoptimize) {
                                GraalDirectives.deoptimize();
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testSnippet19() {
        test("snippet19", true);
    }

    public static void snippet20(Object o) {
        synchronized (A.class) {
            synchronized ((new Object())) {
                synchronized ((new Object())) {
                    staticObj = o;
                }
                // The following monitorenter's stateBefore will contain the preceding eliminated
                // lock, whose monitor ID is not marked as eliminated
                synchronized (B.class) {
                }
            }
        }
    }

    @Test
    public void testSnippet20() {
        test("snippet20", new Object());
    }

    public static void snippet21() {
        Object l1 = new Object();
        Object l2 = new Object();
        synchronized (l1) {
            synchronized (l2) {
                staticObj = new Object[]{l2};
                synchronized (A.class) {
                }
            }
        }
    }

    @Test
    public void testSnippet21() {
        test("snippet21");
    }

    public static void snippet22() {
        Object l2 = new Object();
        Object l1 = new A(l2);

        synchronized (l1) {
            synchronized (l2) {
                staticObj = new Object[]{l2};
                synchronized (A.class) {
                }
            }
        }
    }

    @Test
    public void testSnippet22() {
        test("snippet22");
    }

    public static void snippet23(boolean flag) {
        boolean var21;
        boolean var3;
        B var0;
        B var17;
        B var25;
        B var6 = new B();
        B var10 = new B();
        B var26 = new B();
        boolean var20 = flag;
        var21 = var20;
        synchronized (var10) {
            var25 = var26;
            var17 = var25;
            var3 = var21;
            synchronized (var20 ? var17 : var10) {
                synchronized (var3 ? var10 : var17) {
                }
            }
        }
        var0 = var6;
        staticObj = var0;
    }

    @Test
    public void testSnippet23() {
        // Matches the first boolean produced by the fuzz seed that found GR-64240.
        verifySnippet23Graph = isEnterpriseCompilerConfiguration();
        try {
            test("snippet23", true);
        } finally {
            verifySnippet23Graph = false;
        }
    }

    private static boolean isEnterpriseCompilerConfiguration() {
        return "enterprise".equals(System.getProperty("jdk.graal.CompilerConfiguration"));
    }
}
