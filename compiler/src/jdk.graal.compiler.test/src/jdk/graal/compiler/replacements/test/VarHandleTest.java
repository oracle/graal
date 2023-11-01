/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;

import jdk.graal.compiler.core.test.SubprocessTest;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MultiMemoryKill;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import org.graalvm.word.LocationIdentity;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests that {@link VarHandle} accesses are compiled as expected.
 *
 * As of JDK-8282143, {@link Objects#requireNonNull(Object)} is force inlined. This method is used
 * in VarHandle internals and if the NPE throwing path is compiled, the assumptions in
 * {@link #testAccess} are invalidated. As such, the throwing path must have 0 probability and the
 * compiler must use that probability to omit parsing the path. To guarantee the 0 probability, this
 * test uses a subprocess to isolate the profile of {@link Objects#requireNonNull(Object)} from all
 * other code.
 */
public class VarHandleTest extends SubprocessTest {

    static class Holder {
        /* Field is declared volatile, but accessed with non-volatile semantics in the tests. */
        volatile int volatileField = 42;

        /* Field is declared non-volatile, but accessed with volatile semantics in the tests. */
        int field = 2018;

        static final VarHandle VOLATILE_FIELD;
        static final VarHandle FIELD;

        static {
            try {
                VOLATILE_FIELD = MethodHandles.lookup().findVarHandle(Holder.class, "volatileField", int.class);
                FIELD = MethodHandles.lookup().findVarHandle(Holder.class, "field", int.class);
            } catch (ReflectiveOperationException ex) {
                throw GraalError.shouldNotReachHere(ex);
            }
        }
    }

    @Test
    public void test() throws IOException, InterruptedException {
        launchSubprocess(this::testBody);
    }

    public static int testRead1Snippet(Holder h) {
        /* Explicitly access the volatile field with non-volatile access semantics. */
        return (int) Holder.VOLATILE_FIELD.get(h);
    }

    public static int testRead2Snippet(Holder h) {
        /* Explicitly access the volatile field with volatile access semantics. */
        return (int) Holder.VOLATILE_FIELD.getVolatile(h);
    }

    public static int testRead3Snippet(Holder h) {
        /* Explicitly access the non-volatile field with non-volatile access semantics. */
        return (int) Holder.FIELD.get(h);
    }

    public static int testRead4Snippet(Holder h) {
        /* Explicitly access the non-volatile field with volatile access semantics. */
        return (int) Holder.FIELD.getVolatile(h);
    }

    public static void testWrite1Snippet(Holder h) {
        /* Explicitly access the volatile field with non-volatile access semantics. */
        Holder.VOLATILE_FIELD.set(h, 123);
    }

    public static void testWrite2Snippet(Holder h) {
        /* Explicitly access the volatile field with volatile access semantics. */
        Holder.VOLATILE_FIELD.setVolatile(h, 123);
    }

    public static void testWrite3Snippet(Holder h) {
        /* Explicitly access the non-volatile field with non-volatile access semantics. */
        Holder.FIELD.set(h, 123);
    }

    public static void testWrite4Snippet(Holder h) {
        /* Explicitly access the non-volatile field with volatile access semantics. */
        Holder.FIELD.setVolatile(h, 123);
    }

    void testAccess(String name, int expectedReads, int expectedWrites, int expectedMembars, int expectedAnyKill) {
        ResolvedJavaMethod method = getResolvedJavaMethod(name);

        // Ensures that all VarHandles in the snippet are fully resolved.
        // Works around outlining of methods in VarHandle resolution (JDK-8265135).
        Holder h = new Holder();
        executeExpected(method, null, h);

        StructuredGraph graph = parseForCompile(method);
        compile(method, graph);
        Assert.assertEquals(expectedReads, graph.getNodes().filter(ReadNode.class).count());
        Assert.assertEquals(expectedWrites, graph.getNodes().filter(WriteNode.class).count());
        Assert.assertEquals(expectedMembars, graph.getNodes().filter(MembarNode.class).count());
        Assert.assertEquals(expectedAnyKill, countAnyKill(graph));
    }

    @Override
    protected OptimisticOptimizations getOptimisticOptimizations() {
        ResolvedJavaMethod requireNonNull = getResolvedJavaMethod(Objects.class, "requireNonNull", Object.class);
        ProfilingInfo info = requireNonNull.getProfilingInfo();
        Assert.assertNotNull(info);
        double taken = info.getBranchTakenProbability(1);
        Assert.assertTrue("test assumes that Objects.requireNonNull(Object) has never thrown an NPE", taken == 1.0D);

        return OptimisticOptimizations.ALL;
    }

    void testBody() {
        testAccess("testRead1Snippet", 1, 0, 0, 0);
        testAccess("testRead2Snippet", 1, 0, 0, 1);
        testAccess("testRead3Snippet", 1, 0, 0, 0);
        testAccess("testRead4Snippet", 1, 0, 0, 1);
        testAccess("testWrite1Snippet", 0, 1, 0, 0);
        testAccess("testWrite2Snippet", 0, 1, 0, 1);
        testAccess("testWrite3Snippet", 0, 1, 0, 0);
        testAccess("testWrite4Snippet", 0, 1, 0, 1);
    }

    private static int countAnyKill(StructuredGraph graph) {
        int anyKillCount = 0;
        int startNodes = 0;
        for (Node n : graph.getNodes()) {
            if (n instanceof StartNode) {
                startNodes++;
            } else if (MemoryKill.isSingleMemoryKill(n)) {
                SingleMemoryKill single = (SingleMemoryKill) n;
                if (single.getKilledLocationIdentity().isAny()) {
                    anyKillCount++;
                }
            } else if (MemoryKill.isMultiMemoryKill(n)) {
                MultiMemoryKill multi = (MultiMemoryKill) n;
                for (LocationIdentity loc : multi.getKilledLocationIdentities()) {
                    if (loc.isAny()) {
                        anyKillCount++;
                        break;
                    }
                }
            }
        }
        // Ignore single StartNode.
        Assert.assertEquals(1, startNodes);
        return anyKillCount;
    }
}
