/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.amd64.test;

import static org.junit.Assume.assumeTrue;

import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.amd64.AMD64;

public class AMD64VolatileWriteMembarEliminationTest extends GraalCompilerTest {
    static Object[] objectArray = new Object[3];
    static int field1;
    static int field2;

    static {
        objectArrayBaseOffset = UNSAFE.arrayBaseOffset(Object[].class);
        objectArrayIndexScale = UNSAFE.arrayIndexScale(Object[].class);
    }

    private static final long objectArrayBaseOffset;
    private static final long objectArrayIndexScale;

    @Before
    public void checkAMD64() {
        assumeTrue("skipping AMD64 specific test", getTarget().arch instanceof AMD64);
    }

    public static void testMethod1() {
        Object[] array = objectArray;
        UNSAFE.putObjectVolatile(array, objectArrayBaseOffset, array);
        UNSAFE.putObjectVolatile(array, objectArrayBaseOffset + (1 * objectArrayIndexScale), array);
        UNSAFE.putObjectVolatile(array, objectArrayBaseOffset + (2 * objectArrayIndexScale), array);
    }

    static int expectedVolatiles;

    @Test
    public void test1() {
        expectedVolatiles = 1;
        test("testMethod1");
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        assertDeepEquals("Wrong number of membars", expectedVolatiles,
                        graph.getNodes().filter(WriteNode.class).filter(n -> ((WriteNode) n).getMemoryOrder() == MemoryOrderMode.VOLATILE).count());
    }

    public static int testMethod2() {
        int result = 0;
        Object[] array = objectArray;
        UNSAFE.putObjectVolatile(array, objectArrayBaseOffset, array);
        result += field1;
        UNSAFE.putObjectVolatile(array, objectArrayBaseOffset + (1 * objectArrayIndexScale), array);
        UNSAFE.putObjectVolatile(array, objectArrayBaseOffset + (2 * objectArrayIndexScale), array);
        return result;
    }

    @Test
    public void test2() {
        expectedVolatiles = 2;
        test("testMethod2");
    }

    public static int testMethod3() {
        int result = 0;
        Object[] array = objectArray;
        UNSAFE.putObjectVolatile(array, objectArrayBaseOffset, array);
        field1 = 1;
        field2 = 2;
        UNSAFE.putObjectVolatile(array, objectArrayBaseOffset + (1 * objectArrayIndexScale), array);
        UNSAFE.putObjectVolatile(array, objectArrayBaseOffset + (2 * objectArrayIndexScale), array);
        return result;
    }

    @Test
    public void test3() {
        expectedVolatiles = 1;
        test("testMethod3");
    }

}
