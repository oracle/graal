/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;

public class InstanceOfCanonicalizerTest extends GraphTest {

    /**
     * The problem tested here is the following: When canonicalizing a negated instanceof for which the exact type
     * suggests that the instanceof is true, we still need to check if the value is null. (because this would make the
     * instanceof false, and thus the negated instanceof true).
     *
     * This test case is constructed by replacing an instanceof with its negated counterpart, since negated instanceof
     * operations will only be created in complicated cases.
     */
    @Test
    public void test1() {
        StructuredGraph graph = parse("testSnippet1");
        Debug.dump(graph, "Graph");
        for (Node node : graph.getNodes().snapshot()) {
            if (node instanceof InstanceOfNode) {
                graph.replaceFloating((InstanceOfNode) node, ((InstanceOfNode) node).negate());
            }
        }
        new CanonicalizerPhase(null, runtime(), null).apply(graph);
        Debug.dump(graph, "Graph");
        for (Node node : graph.getNodes()) {
            if (node instanceof InstanceOfNode) {
                Assert.fail("InstanceOfNode should have been canonicalized");
            } else if (node instanceof ReturnNode) {
                ReturnNode ret = (ReturnNode) node;
                Assert.assertTrue("return value should be a MaterializeNode " + ret.result(), ret.result() instanceof MaterializeNode);
                MaterializeNode materialize = (MaterializeNode) ret.result();
                Assert.assertTrue("return value should depend on nullness of parameter " + materialize.condition(), materialize.condition() instanceof NullCheckNode);
            }

        }
    }

    @SuppressWarnings("all")
    public static boolean testSnippet1(String s) {
        return s instanceof String;
    }
}
