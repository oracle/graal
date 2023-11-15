/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode.CheckAll;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.EnumSet;

import jdk.graal.compiler.core.test.SubprocessTest;
import jdk.graal.compiler.hotspot.HotSpotBackend;
import jdk.graal.compiler.hotspot.meta.DefaultHotSpotLoweringProvider;
import jdk.graal.compiler.hotspot.stubs.CreateExceptionStub;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.junit.Assume;
import org.junit.Test;

/**
 * This test exercises the deoptimization in the {@link BytecodeExceptionNode} foreign call path.
 */
public class HotSpotDeoptExplicitExceptions extends SubprocessTest {

    static class Fields {
        Object a;
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        return super.editGraphBuilderConfiguration(conf).withBytecodeExceptionMode(CheckAll);
    }

    static String nullCheckSnippet(Object o) {
        return o.toString();
    }

    static int divByZeroSnippet(int x, int y) {
        return x / y;
    }

    static String classCastSnippet(Object o) {
        return (String) o;
    }

    static void aastoreSnippet(Object[] array, int index, Object value) {
        array[index] = value;
    }

    static Object outOfBoundsSnippet(Object[] array, int index) {
        return array[index];
    }

    static Object getFieldSnippet(Fields o) {
        return o.a;
    }

    static void putFieldSnippet(Fields a, Object o) {
        a.a = o;
    }

    static Object[] allocateSnippet(int length) {
        return new Object[length];
    }

    static int arrayLengthSnippet(Object o) {
        return Array.getLength(o);
    }

    static int intExactOverflowSnippet(int a, int b) {
        return Math.addExact(a, b);
    }

    static long longExactOverflowSnippet(long a, long b) {
        return Math.addExact(a, b);
    }

    /**
     * These are the {@link BytecodeExceptionKind}s that are supported on HotSpot. Some kinds are
     * only required for use in native image and any missing ones would result in lowering failures.
     */
    private EnumSet<BytecodeExceptionKind> needsTesting = EnumSet.copyOf(DefaultHotSpotLoweringProvider.RuntimeCalls.runtimeCalls.keySet());

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        for (BytecodeExceptionNode node : graph.getNodes().filter(BytecodeExceptionNode.class)) {
            // Mark this kind as exercised by one of unit tests. The full set of tests will
            // exercise some of these multiple times but we want to ensure they are exercised at
            // least once.
            needsTesting.remove(node.getExceptionKind());
        }
        super.checkHighTierGraph(graph);
    }

    void testBody() {
        test("nullCheckSnippet", (Object) null);
        test("divByZeroSnippet", 1, 0);
        test("classCastSnippet", Boolean.TRUE);
        test("aastoreSnippet", new String[1], 0, new Object());
        test("outOfBoundsSnippet", new String[0], 0);
        test("getFieldSnippet", (Object) null);
        test("putFieldSnippet", null, null);
        test("allocateSnippet", -1);
        test("arrayLengthSnippet", "s");
        test("intExactOverflowSnippet", Integer.MAX_VALUE, Integer.MAX_VALUE);
        test("longExactOverflowSnippet", Long.MAX_VALUE, Long.MAX_VALUE);

        assertTrue(needsTesting.isEmpty(), "missing tests for %s", needsTesting);
    }

    @Test
    public void explicitExceptions() throws IOException, InterruptedException {
        Assume.assumeTrue("required entry point is missing", ((HotSpotBackend) getBackend()).getRuntime().getVMConfig().deoptBlobUnpackWithExceptionInTLS != 0);
        if (!CreateExceptionStub.Options.HotSpotDeoptExplicitExceptions.getValue(getInitialOptions())) {
            launchSubprocess(this::testBody, "-Djdk.graal.HotSpotDeoptExplicitExceptions=true");
        } else {
            testBody();
        }
    }

}
