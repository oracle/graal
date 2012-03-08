/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.tests;

import org.junit.*;

import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.compiler.types.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;

/**
 * In the following tests, the scalar type system of the compiler should be complete enough to see the relation between the different conditions.
 */
public class TypeSystemTest extends GraphTest {

    @Test
    public void test1() {
        test("test1Snippet", CheckCastNode.class);
    }

    public static int test1Snippet(Object a) {
        if (a instanceof Boolean) {
            return ((Boolean) a).booleanValue() ? 0 : 1;
        }
        return 1;
    }

    @Test
    public void test2() {
        test("test2Snippet", CheckCastNode.class);
    }

    public static int test2Snippet(Object a) {
        if (a instanceof Integer) {
            return ((Number) a).intValue();
        }
        return 1;
    }

    private <T extends Node & Node.IterableNodeType> void test(String snippet, Class<T> clazz) {
        StructuredGraph graph = parse(snippet);
        Debug.dump(graph, "Graph");
        new CanonicalizerPhase(null, runtime(), null).apply(graph);
        new PropagateTypesPhase(null, null, null).apply(graph);
        Debug.dump(graph, "Graph");
        Assert.assertFalse("shouldn't have nodes of type " + clazz, graph.getNodes(clazz).iterator().hasNext());
    }
}
