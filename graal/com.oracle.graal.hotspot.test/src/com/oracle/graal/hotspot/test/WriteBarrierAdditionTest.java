/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.compiler.common.UnsafeAccess.*;

import java.lang.ref.*;

import org.junit.*;

import sun.misc.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.jvmci.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.phases.*;
import com.oracle.graal.hotspot.replacements.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.memory.*;
import com.oracle.graal.nodes.memory.HeapAccess.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.inlining.*;
import com.oracle.graal.phases.common.inlining.policy.*;
import com.oracle.graal.phases.tiers.*;

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

    private static final HotSpotVMConfig config = HotSpotGraalRuntime.runtime().getConfig();
    private static final long referentOffset = HotSpotReplacementsUtil.referentOffset();

    public static class Container {

        public Container a;
        public Container b;
    }

    /**
     * Expected 2 barriers for the Serial GC and 4 for G1 (2 pre + 2 post).
     */
    @Test
    public void test1() throws Exception {
        testHelper("test1Snippet", (config.useG1GC) ? 4 : 2);
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
        testHelper("test2Snippet", config.useG1GC ? 8 : 4);
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
        testHelper("test3Snippet", config.useG1GC ? 8 : 4);
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
        testHelper("test4Snippet", config.useG1GC ? 5 : 2);
    }

    public static Object test4Snippet() {
        WeakReference<Object> weakRef = new WeakReference<>(new Object());
        return weakRef.get();
    }

    static WeakReference<Object> wr = new WeakReference<>(new Object());
    static Container con = new Container();

    /**
     * Expected 4 barriers for the Serial GC and 9 for G1 (1 ref + 4 pre + 4 post). In this test, we
     * load the correct offset of the WeakReference object so naturally we assert the presence of
     * the pre barrier.
     */
    @Test
    public void test5() throws Exception {
        testHelper("test5Snippet", config.useG1GC ? 1 : 0);
    }

    public static Object test5Snippet() throws Exception {
        return unsafe.getObject(wr, config.useCompressedOops ? 12L : 16L);
    }

    /**
     * The following test concerns the runtime checks of the unsafe loads. In this test, we unsafely
     * load the java.lang.ref.Reference.referent field so the pre barier has to be executed.
     */
    @Test
    public void test6() throws Exception {
        test2("testUnsafeLoad", unsafe, wr, new Long(referentOffset), null);
    }

    /**
     * The following test concerns the runtime checks of the unsafe loads. In this test, we unsafely
     * load a matching offset of a wrong object so the pre barier must not be executed.
     */
    @Test
    public void test7() throws Exception {
        test2("testUnsafeLoad", unsafe, con, new Long(referentOffset), null);
    }

    /**
     * The following test concerns the runtime checks of the unsafe loads. In this test, we unsafely
     * load a non-matching offset field of the java.lang.ref.Reference object so the pre barier must
     * not be executed.
     */
    @Test
    public void test8() throws Exception {
        test2("testUnsafeLoad", unsafe, wr, new Long(config.useCompressedOops ? 20 : 32), null);
    }

    /**
     * The following test concerns the runtime checks of the unsafe loads. In this test, we unsafely
     * load a matching offset+disp field of the java.lang.ref.Reference object so the pre barier
     * must be executed.
     */
    @Test
    public void test10() throws Exception {
        test2("testUnsafeLoad", unsafe, wr, new Long(config.useCompressedOops ? 6 : 8), new Integer(config.useCompressedOops ? 6 : 8));
    }

    /**
     * The following test concerns the runtime checks of the unsafe loads. In this test, we unsafely
     * load a non-matching offset+disp field of the java.lang.ref.Reference object so the pre barier
     * must not be executed.
     */
    @Test
    public void test9() throws Exception {
        test2("testUnsafeLoad", unsafe, wr, new Long(config.useCompressedOops ? 10 : 16), new Integer(config.useCompressedOops ? 10 : 16));
    }

    static Object[] src = new Object[1];
    static Object[] dst = new Object[1];

    static {
        for (int i = 0; i < src.length; i++) {
            src[i] = new Object();
        }
        for (int i = 0; i < dst.length; i++) {
            dst[i] = new Object();
        }
    }

    public static void testArrayCopy(Object a, Object b, Object c) throws Exception {
        System.arraycopy(a, 0, b, 0, (int) c);
    }

    @Test
    public void test11() throws Exception {
        test2("testArrayCopy", src, dst, dst.length);
    }

    public static Object testUnsafeLoad(Unsafe theUnsafe, Object a, Object b, Object c) throws Exception {
        final int offset = (c == null ? 0 : ((Integer) c).intValue());
        final long displacement = (b == null ? 0 : ((Long) b).longValue());
        return theUnsafe.getObject(a, offset + displacement);
    }

    private HotSpotInstalledCode getInstalledCode(String name, boolean withUnsafePrefix) throws Exception {
        final ResolvedJavaMethod javaMethod = withUnsafePrefix ? getResolvedJavaMethod(WriteBarrierAdditionTest.class, name, Unsafe.class, Object.class, Object.class, Object.class)
                        : getResolvedJavaMethod(WriteBarrierAdditionTest.class, name, Object.class, Object.class, Object.class);
        final HotSpotInstalledCode installedCode = (HotSpotInstalledCode) getCode(javaMethod);
        return installedCode;
    }

    private void testHelper(final String snippetName, final int expectedBarriers) throws Exception, SecurityException {
        ResolvedJavaMethod snippet = getResolvedJavaMethod(snippetName);
        try (Scope s = Debug.scope("WriteBarrierAdditionTest", snippet)) {
            StructuredGraph graph = parseEager(snippet, AllowAssumptions.NO);
            HighTierContext highContext = getDefaultHighTierContext();
            MidTierContext midContext = new MidTierContext(getProviders(), getCodeCache().getTarget(), OptimisticOptimizations.ALL, graph.method().getProfilingInfo(), null);
            new InliningPhase(new InlineEverythingPolicy(), new CanonicalizerPhase()).apply(graph, highContext);
            new CanonicalizerPhase().apply(graph, highContext);
            new LoweringPhase(new CanonicalizerPhase(), LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, highContext);
            new GuardLoweringPhase().apply(graph, midContext);
            new LoweringPhase(new CanonicalizerPhase(), LoweringTool.StandardLoweringStage.MID_TIER).apply(graph, midContext);
            new WriteBarrierAdditionPhase(config).apply(graph);
            Debug.dump(graph, "After Write Barrier Addition");

            int barriers = 0;
            if (config.useG1GC) {
                barriers = graph.getNodes().filter(G1ReferentFieldReadBarrier.class).count() + graph.getNodes().filter(G1PreWriteBarrier.class).count() +
                                graph.getNodes().filter(G1PostWriteBarrier.class).count();
            } else {
                barriers = graph.getNodes().filter(SerialWriteBarrier.class).count();
            }
            Assert.assertEquals(expectedBarriers, barriers);
            for (WriteNode write : graph.getNodes().filter(WriteNode.class)) {
                if (config.useG1GC) {
                    if (write.getBarrierType() != BarrierType.NONE) {
                        Assert.assertEquals(1, write.successors().count());
                        Assert.assertTrue(write.next() instanceof G1PostWriteBarrier);
                        Assert.assertTrue(write.predecessor() instanceof G1PreWriteBarrier);
                    }
                } else {
                    if (write.getBarrierType() != BarrierType.NONE) {
                        Assert.assertEquals(1, write.successors().count());
                        Assert.assertTrue(write.next() instanceof SerialWriteBarrier);
                    }
                }
            }

            for (ReadNode read : graph.getNodes().filter(ReadNode.class)) {
                if (read.getBarrierType() != BarrierType.NONE) {
                    if (read.location() instanceof ConstantLocationNode) {
                        Assert.assertEquals(referentOffset, ((ConstantLocationNode) (read.location())).getDisplacement());
                    }
                    Assert.assertTrue(config.useG1GC);
                    Assert.assertEquals(BarrierType.PRECISE, read.getBarrierType());
                    Assert.assertTrue(read.next() instanceof G1ReferentFieldReadBarrier);
                }
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    private void test2(final String snippet, Object... args) throws Exception {
        HotSpotInstalledCode code = getInstalledCode(snippet, args[0] instanceof Unsafe);
        code.executeVarargs(args);
    }
}
