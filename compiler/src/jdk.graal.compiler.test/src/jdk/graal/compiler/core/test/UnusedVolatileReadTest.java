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
package jdk.graal.compiler.core.test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import org.junit.Test;

import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.memory.ReadNode;

/**
 * Checks that unused volatile and acquire reads survive canonicalization.
 */
public class UnusedVolatileReadTest extends GraalCompilerTest {
    private static volatile int value;
    private static int data;
    private static final VarHandle VALUE;

    static {
        try {
            VALUE = MethodHandles.lookup().findStaticVarHandle(UnusedVolatileReadTest.class, "value", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Reads a volatile field without using its value.
     */
    public static void unusedVolatileRead() {
        int ignored = value;
    }

    /**
     * Checks that an unused volatile read survives HIR optimization.
     */
    @Test
    public void testUnusedVolatileRead() {
        test("unusedVolatileRead");
    }

    /**
     * Reads a publication flag opaquely, acquires it without using the value, then reads the data.
     */
    public static long unusedAcquireRead() {
        int flag = (int) VALUE.getOpaque();
        int ignored = (int) VALUE.getAcquire();
        int observedData = data;
        return ((long) flag << Integer.SIZE) | Integer.toUnsignedLong(observedData);
    }

    /**
     * Checks that an unused acquire read survives HIR optimization.
     */
    @Test
    public void testUnusedAcquireRead() {
        /* Initialize VarHandle linkage before checking the compiled graph shape. */
        unusedAcquireRead();
        test("unusedAcquireRead");
    }

    /**
     * Checks that unused ordered reads survive the regular HIR optimization pipeline.
     */
    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        boolean acquire = graph.method().getName().equals("unusedAcquireRead");
        assertDeepEquals(acquire ? 3 : 1, graph.getNodes().filter(ReadNode.class).count());
        MemoryOrderMode expectedOrder = acquire ? MemoryOrderMode.ACQUIRE : MemoryOrderMode.VOLATILE;
        var orderedReads = graph.getNodes().filter(ReadNode.class).filter(read -> ((ReadNode) read).getMemoryOrder() == expectedOrder);
        assertDeepEquals(1, orderedReads.count());
        ReadNode read = orderedReads.first();
        assertTrue(read.hasNoUsages());
        if (acquire) {
            assertDeepEquals(1, graph.getNodes().filter(ReadNode.class).filter(r -> ((ReadNode) r).getMemoryOrder() == MemoryOrderMode.OPAQUE).count());
            assertDeepEquals(1, graph.getNodes().filter(ReadNode.class).filter(r -> ((ReadNode) r).getMemoryOrder() == MemoryOrderMode.PLAIN).count());
        }
    }
}
