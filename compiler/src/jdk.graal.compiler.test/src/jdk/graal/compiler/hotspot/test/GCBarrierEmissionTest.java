/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.junit.Test;

import jdk.graal.compiler.core.test.SubprocessTest;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotBackend;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.LogicCompareAndSwapNode;
import jdk.graal.compiler.nodes.java.LoweredAtomicReadAndWriteNode;
import jdk.graal.compiler.nodes.java.ValueCompareAndSwapNode;
import jdk.graal.compiler.test.AddExports;
import jdk.graal.compiler.test.SubprocessUtil;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;

/**
 * Test which exercises the individual operations which might require read or write barriers. It's
 * useful for comparing the output of HotSpot and Graal when generating these patterns. By default,
 * it just exercises these code sequences. By setting
 * {@code -Ddebug.jdk.graal.compiler.hotspot.test.GCBarrierEmissionTest.print=true} the assembly for
 * each test for both C2 and Graal will be saved to a file so that they can be manually compared.
 */
@AddExports("java.base/jdk.internal.misc")
public class GCBarrierEmissionTest extends SubprocessTest {

    public static final long f1FieldOffset;
    static {
        try {
            f1FieldOffset = UNSAFE.objectFieldOffset(TestObject.class.getDeclaredField("f1"));
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    static {
        objectArrayBaseOffset = UNSAFE.arrayBaseOffset(Object[].class);
    }

    private static final long objectArrayBaseOffset;

    static final class TestObject {
        private TestObject() {
            this(0);
        }

        private TestObject(int id) {
            this.id = id;
        }

        final int id;
        Object f1;
        volatile Object v1;
    }

    public static Object fieldReadBarrierSnippet(TestObject t) {
        return t.f1;
    }

    @Test
    public void fieldReadBarrier() {
        runTest(new TestObject());
    }

    public static void fieldWriteBarrierSnippet(TestObject t, Object value) {
        t.f1 = value;
    }

    @Test
    public void fieldWriteBarrier() {
        runTest(new TestObject(), "string");
    }

    public static void volatileFieldWriteBarrierSnippet(TestObject t, Object value) {
        t.v1 = value;
    }

    @Test
    public void volatileFieldWriteBarrier() {
        runTest(new TestObject(), "string");
    }

    public static void arrayWriteBarrierSnippet(Object[] t, Object value) {
        t[0] = value;
    }

    @Test
    public void arrayWriteBarrier() {
        runTest(new Object[1], "string");
    }

    public static void volatileArrayWriteBarrierSnippet(Object[] t, Object value) {
        if (t == null) {
            return;
        }
        UNSAFE.putReferenceVolatile(t, objectArrayBaseOffset, value);
    }

    @Test
    public void volatileArrayWriteBarrier() {
        runTest(new Object[1], "string");
    }

    public static void fieldWriteNullBarrierSnippet(TestObject t) {
        t.f1 = null;
    }

    @Test
    public void fieldWriteNullBarrier() {
        runTest(new TestObject());
    }

    public static void volatileFieldWriteNullBarrierSnippet(TestObject t) {
        t.v1 = null;
    }

    @Test
    public void volatileFieldWriteNullBarrier() {
        runTest(new TestObject());
    }

    public static void arrayWriteNullBarrierSnippet(Object[] t) {
        t[0] = null;
    }

    @Test
    public void arrayWriteNullBarrier() {
        runTest(new Object[]{new Object[1]});
    }

    public static Object valueCompareAndSwapBarrierSnippet(TestObject t1, Object value) {
        if (t1 == null) {
            return null;
        }
        return UNSAFE.compareAndExchangeReference(t1, f1FieldOffset, null, value);
    }

    @Test
    public void valueCompareAndSwapBarrier() {
        Predicate<StructuredGraph> nodePredicate = (StructuredGraph graph) -> {
            assertTrue(graph.getNodes().filter(ValueCompareAndSwapNode.class).isNotEmpty(), "expected ValueCompareAndSwapNode");
            return true;
        };
        runTest(nodePredicate, supply(TestObject::new), "string");
    }

    public static boolean logicCompareAndSwapBarrierSnippet(TestObject t1, Object value) {
        if (t1 == null) {
            return false;
        }
        return UNSAFE.compareAndSetReference(t1, f1FieldOffset, null, value);
    }

    @Test
    public void logicCompareAndSwapBarrier() {
        Predicate<StructuredGraph> nodePredicate = (StructuredGraph graph) -> {
            assertTrue(graph.getNodes().filter(LogicCompareAndSwapNode.class).isNotEmpty(), "expected LogicCompareAndSwapNode");
            return true;
        };
        runTest(nodePredicate, supply(TestObject::new), "string");
    }

    public static Object getAndSetBarrierSnippet(TestObject t1, Object value) {
        if (t1 == null) {
            return null;
        }
        return UNSAFE.getAndSetReference(t1, f1FieldOffset, value);
    }

    @Test
    public void getAndSetBarrier() {
        Predicate<StructuredGraph> nodePredicate = (StructuredGraph graph) -> {
            assertTrue(graph.getNodes().filter(LoweredAtomicReadAndWriteNode.class).isNotEmpty(), "expected LoweredAtomicReadAndWriteNode");
            return true;
        };
        runTest(nodePredicate, supply(TestObject::new), "string");
    }

    public static boolean phantomRefersToBarrierSnippet(PhantomReference<Object> phantom, Object value) {
        return phantom.refersTo(value);
    }

    @Test
    public void phantomRefersToBarrier() {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        runTest(new PhantomReference<>("string", queue), "string");
    }

    public static boolean weakRefersToBarrierSnippet(WeakReference<Object> weak, Object value) {
        return weak.refersTo(value);
    }

    @Test
    public void weakRefersToBarrier() {
        runTest(new WeakReference<>("string"), "string");
    }

    public static Object referenceGetBarrierSnippet(WeakReference<Object> weak) {
        return weak.get();
    }

    @Test
    public void referenceGetBarrier() {
        runTest(new WeakReference<>("string"));
    }

    public static TestObject objectAllocationBarrierSnippet() {
        return new TestObject();
    }

    @Test
    public void objectAllocationBarrier() {
        runTest();
    }

    public static String stringAllocationBarrierSnippet() {
        return new String("snippet");
    }

    @Test
    public void stringAllocationBarrier() {
        runTest();
    }

    private static TestObject obj6 = new TestObject(6);
    private static TestObject obj7 = new TestObject(7);

    public static Object testuuvCAESnippet() {
        AtomicReference<TestObject> a = new AtomicReference<>(obj6);
        return a.compareAndExchange(obj7, new TestObject(3));
    }

    @Test
    public void testuuvCAE() {
        runTest();
    }

    public static Object threadHandleBarrierSnippet() {
        return Thread.currentThread();
    }

    @Test
    public void threadHandleBarrier() {
        runTest();
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        if (graphPredicate != null) {
            graphPredicate.test(graph);
        }
    }

    Predicate<StructuredGraph> graphPredicate;

    public void runTest(Object... args) {
        runTest(null, args);
    }

    public void runTest(Predicate<StructuredGraph> predicate, Object... args) {
        String baseName = currentUnitTestName();
        String snippetName = baseName + "Snippet";
        String methodSpec = getClass().getName() + "::" + snippetName;
        Method m = getMethod(snippetName);

        Runnable run = () -> {
            // Force compilation with HotSpot
            for (int i = 0; i < 100000; i++) {
                try {
                    Object[] finalArgs = applyArgSuppliers(args);
                    m.invoke(null, finalArgs);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
            try {
                if (args.length != 0) {
                    Object[] nullArgs = args.clone();
                    for (int i = 0; i < nullArgs.length; i++) {
                        nullArgs[i] = null;
                    }
                    test(snippetName, nullArgs);
                }
                graphPredicate = predicate;
                // Now generate JVMCI code
                InstalledCode code = getCode(getResolvedJavaMethod(snippetName));
                for (int i = 0; i < 100000; i++) {
                    try {
                        Object[] finalArgs = applyArgSuppliers(args);
                        code.executeVarargs(finalArgs);
                        if (i % 1000 == 0) {
                            System.gc();
                        }
                    } catch (InvalidInstalledCodeException e) {
                        throw new RuntimeException(e);
                    }
                }
            } finally {
                graphPredicate = null;
            }
        };

        GraalHotSpotVMConfig config = ((HotSpotBackend) getBackend()).getRuntime().getVMConfig();
        SubprocessUtil.Subprocess subprocess = null;
        String logName = null;
        String[] vmArgs = new String[0];
        boolean print = Boolean.getBoolean("debug." + this.getClass().getName() + ".print");
        if (print) {
            logName = config.gc.name() + "_" + baseName + ".log";
            vmArgs = new String[]{"-XX:CompileCommand=print," + methodSpec,
                            "-XX:CompileCommand=dontinline," + methodSpec,
                            "-XX:+UnlockDiagnosticVMOptions",
                            "-XX:-DisplayVMOutput",
                            "-XX:-TieredCompilation",
                            "-XX:+LogVMOutput",
                            "-XX:+PreserveFramePointer",
                            "-Xbatch",
                            "-XX:LogFile=" + logName};
        }
        try {
            subprocess = launchSubprocess(run, vmArgs);
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
        if (subprocess != null && logName != null) {
            System.out.println("HotSpot output saved in " + logName);
        }
    }
}
