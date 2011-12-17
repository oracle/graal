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
package com.oracle.max.graal.compiler.tests;

import org.junit.*;

import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;

/**
 * In the following tests, the usages of local variable "a" are replaced with the integer constant 0.
 * Then canonicalization is applied and it is verified that the resulting graph is equal to the
 * graph of the method that just has a "return 1" statement in it.
 */
public class IfCanonicalizerTest extends GraphTest {

    private static final String REFERENCE_SNIPPET = "referenceSnippet";

    public static int referenceSnippet(int a) {
        return 1;
    }

    @Test
    public void test1() {
        test("test1Snippet");
    }

    public static int test1Snippet(int a) {
        if (a == 0) {
            return 1;
        } else {
            return 2;
        }
    }

    @Test
    public void test2() {
        test("test2Snippet");
    }

    public static int test2Snippet(int a) {
        if (a == 0) {
            if (a == 0) {
                if (a == 0) {
                    return 1;
                }
            }
        } else {
            return 2;
        }
        return 3;
    }

    @Test
    public void test3() {
        test("test3Snippet");
    }

    public static int test3Snippet(int a) {
        if (a == 0) {
            if (a != 1) {
                if (a == 1) {
                    return 3;
                } else {
                    if (a >= 0) {
                        if (a <= 0) {
                            if (a > -1) {
                                if (a < 1) {
                                    return 1;
                                }
                            }
                        }
                    }
                }
            }
        } else {
            return 2;
        }
        return 3;
    }

    @Test
    public void test4() {
        test("test4Snippet");
    }

    public static int test4Snippet(int a) {
        if (a == 0) {
            return 1;
        }
        return 1;
    }

    @Test
    public void test5() {
        test("test5Snippet");
    }

    public static int test5Snippet(int a) {
        int val = 2;
        if (a == 0) {
            val = 1;
        }
        if (a * (3 + val) == 0) {
            return 1;
        }
        return 1;
    }

    private void test(String snippet) {
        StructuredGraph graph = parse(snippet);
        LocalNode local = graph.getNodes(LocalNode.class).iterator().next();
        ConstantNode constant = ConstantNode.forInt(0, graph);
        for (Node n : local.usages().snapshot()) {
            if (n instanceof FrameState) {
                // Do not replace.
            } else {
                n.replaceFirstInput(local, constant);
            }
        }
        print(graph);
        new CanonicalizerPhase(null, runtime(), null).apply(graph);
        StructuredGraph referenceGraph = parse(REFERENCE_SNIPPET);
        assertEquals(referenceGraph, graph);
    }
}
