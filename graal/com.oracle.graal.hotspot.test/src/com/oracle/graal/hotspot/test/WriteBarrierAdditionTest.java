/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.test;

import java.lang.ref.*;
import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.phases.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.HeapAccess.WriteBarrierType;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.Lowerable.LoweringType;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;

/**
 * The following unit tests assert the presence of write barriers for both Serial and G1 GCs.
 * Normally, the tests check for compile time inserted barriers. However, there are the cases of
 * unsafe loads of the java.lang.ref.Reference.referent field where runtime checks have to be
 * performed also. For those cases, the unit tests check the presence of the compile-time inserted
 * barriers. Concerning the runtime checks, the results of variable inputs (object types and
 * offsets) passed as input parameters can be checked against printed output from the G1 write
 * barrier snippets. The runtime checks have been validated offline.
 */

public class WriteBarrierAdditionTest extends GraalCompilerTest {

    private final MetaAccessProvider metaAccessProvider;

    public WriteBarrierAdditionTest() {
        this.metaAccessProvider = Graal.getRequiredCapability(MetaAccessProvider.class);
    }

    public static class Container {

        public Container a;
        public Container b;
    }

    /**
     * Expected 2 barriers for the Serial GC and 4 for G1 (2 pre + 2 post).
     */
    @Test
    public void test1() throws Exception {
        test("test1Snippet", useG1GC() ? 4 : 2);
    }

    public static void test1Snippet() {
        Container main = new Container();
        Container temp1 = new Container();
        Container temp2 = new Container();
        main.a = temp1;
        main.b = temp2;
    }

    /**
     * Expected 4 barriers for the Serial GC and 8 for G1 (4 pre + 4 post).
     */
    @Test
    public void test2() throws Exception {
        test("test2Snippet", useG1GC() ? 8 : 4);
    }

    public static void test2Snippet(boolean test) {
        Container main = new Container();
        Container temp1 = new Container();
        Container temp2 = new Container();
        for (int i = 0; i < 10; i++) {
            if (test) {
                main.a = temp1;
                main.b = temp2;
            } else {
                main.a = temp2;
                main.b = temp1;
            }
        }
    }

    /**
     * Expected 4 barriers for the Serial GC and 8 for G1 (4 pre + 4 post).
     */
    @Test
    public void test3() throws Exception {
        test("test3Snippet", useG1GC() ? 8 : 4);
    }

    public static void test3Snippet() {
        Container[] main = new Container[10];
        Container temp1 = new Container();
        Container temp2 = new Container();
        for (int i = 0; i < 10; i++) {
            main[i].a = main[i].b = temp1;
        }

        for (int i = 0; i < 10; i++) {
            main[i].a = main[i].b = temp2;
        }
    }

    /**
     * Expected 2 barriers for the Serial GC and 5 for G1 (3 pre + 2 post) The (2 or 4) barriers are
     * emitted while initializing the fields of the WeakReference instance. The extra pre barrier of
     * G1 concerns the read of the referent field.
     */
    @Test
    public void test4() throws Exception {
        test("test4Snippet", useG1GC() ? 5 : 2);
    }

    public static Object test4Snippet() {
        WeakReference<Object> weakRef = new WeakReference<>(new Object());
        return weakRef.get();
    }

    static WeakReference<Object> wr = new WeakReference<>(new Object());
    static Container con = new Container();

    /**
     * Expected 4 barriers for the Serial GC and 9 for G1 (5 pre + 4 post). In this test, we load
     * the correct offset of the WeakReference object so naturally we assert the presence of the pre
     * barrier.
     */
    @Test
    public void test5() throws Exception {
        test("test5Snippet", useG1GC() ? 9 : 4);
    }

    public static Object test5Snippet() throws Exception {
        return UnsafeLoadNode.load(wr, 0, 16, Kind.Object);
    }

    /**
     * The following test concerns the runtime checks of the unsafe loads. In this test, we unsafely
     * load the java.lang.ref.Reference.referent field so the pre barier has to be executed.
     */
    @Test
    public void test6() throws Exception {
        test2("testUnsafeLoad", wr, new Long(referentOffset()), null);
    }

    /**
     * The following test concerns the runtime checks of the unsafe loads. In this test, we unsafely
     * load a matching offset of a wrong object so the pre barier must not be executed.
     */
    @Test
    public void test7() throws Exception {
        test2("testUnsafeLoad", con, new Long(referentOffset()), null);
    }

    /**
     * The following test concerns the runtime checks of the unsafe loads. In this test, we unsafely
     * load a non-matching offset field of the java.lang.ref.Reference object so the pre barier must
     * not be executed.
     */
    @Test
    public void test8() throws Exception {
        test2("testUnsafeLoad", wr, new Long(32), null);
    }

    /**
     * The following test concerns the runtime checks of the unsafe loads. In this test, we unsafely
     * load a matching offset+disp field of the java.lang.ref.Reference object so the pre barier
     * must be executed.
     */
    @Test
    public void test10() throws Exception {
        test2("testUnsafeLoad", wr, new Long(8), new Integer(8));
    }

    /**
     * The following test concerns the runtime checks of the unsafe loads. In this test, we unsafely
     * load a non-matching offset+disp field of the java.lang.ref.Reference object so the pre barier
     * must not be executed.
     */
    @Test
    public void test9() throws Exception {
        test2("testUnsafeLoad", wr, new Long(16), new Integer(16));
    }

    public static Object testUnsafeLoad(Object a, Object b, Object c) throws Exception {
        final int offset = (c == null ? 0 : ((Integer) c).intValue());
        final long displacement = (b == null ? 0 : ((Long) b).longValue());
        return UnsafeLoadNode.load(a, offset, displacement, Kind.Object);
    }

    private HotSpotInstalledCode getInstalledCode(String name) throws Exception {
        final Method method = WriteBarrierAdditionTest.class.getMethod(name, Object.class, Object.class, Object.class);
        final HotSpotResolvedJavaMethod javaMethod = (HotSpotResolvedJavaMethod) metaAccessProvider.lookupJavaMethod(method);
        final HotSpotInstalledCode installedBenchmarkCode = (HotSpotInstalledCode) getCode(javaMethod, parse(method));
        return installedBenchmarkCode;
    }

    private void test(final String snippet, final int expectedBarriers) throws Exception, SecurityException {
        Debug.scope("WriteBarrierAditionTest", new DebugDumpScope(snippet), new Runnable() {

            public void run() {
                StructuredGraph graph = parse(snippet);
                HighTierContext context = new HighTierContext(runtime(), new Assumptions(false), replacements);
                new InliningPhase(runtime(), replacements, context.getAssumptions(), null, getDefaultPhasePlan(), OptimisticOptimizations.ALL, new InliningPhase.InlineEverythingPolicy()).apply(graph);
                new LoweringPhase(LoweringType.BEFORE_GUARDS).apply(graph, context);
                new WriteBarrierAdditionPhase().apply(graph);
                Debug.dump(graph, "After Write Barrier Addition");

                int barriers = 0;
                if (useG1GC()) {
                    barriers = graph.getNodes(G1PreWriteBarrier.class).count() + graph.getNodes(G1PostWriteBarrier.class).count();
                } else {
                    barriers = graph.getNodes(SerialWriteBarrier.class).count();
                }
                Assert.assertTrue(barriers == expectedBarriers);
                for (WriteNode write : graph.getNodes(WriteNode.class)) {
                    if (useG1GC()) {
                        if (write.getWriteBarrierType() != WriteBarrierType.NONE) {
                            Assert.assertTrue(write.successors().count() == 1);
                            Assert.assertTrue(write.next() instanceof G1PostWriteBarrier);
                            Assert.assertTrue(write.predecessor() instanceof G1PreWriteBarrier);
                        }
                    } else {
                        if (write.getWriteBarrierType() != WriteBarrierType.NONE) {
                            Assert.assertTrue(write.successors().count() == 1);
                            Assert.assertTrue(write.next() instanceof SerialWriteBarrier);
                        }
                    }
                }

                for (ReadNode read : graph.getNodes(ReadNode.class)) {
                    if (read.getWriteBarrierType() != WriteBarrierType.NONE) {
                        if (read.location() instanceof ConstantLocationNode) {
                            Assert.assertTrue(((ConstantLocationNode) (read.location())).getDisplacement() == referentOffset());
                        } else {
                            Assert.assertTrue(((IndexedLocationNode) (read.location())).getDisplacement() == referentOffset());
                        }
                        Assert.assertTrue(useG1GC());
                        Assert.assertTrue(read.getWriteBarrierType() == WriteBarrierType.PRECISE);
                        Assert.assertTrue(read.next() instanceof G1PreWriteBarrier);
                    }
                }
            }
        });
    }

    private void test2(final String snippet, Object a, Object b, Object c) throws Exception {
        HotSpotInstalledCode code = getInstalledCode(snippet);
        code.execute(a, b, c);
    }
}
