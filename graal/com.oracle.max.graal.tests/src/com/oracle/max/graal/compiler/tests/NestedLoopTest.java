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

import com.oracle.max.graal.compiler.loop.*;
import com.oracle.max.graal.nodes.*;

/**
 * In the following tests, the usages of local variable "a" are replaced with the integer constant 0.
 * Then canonicalization is applied and it is verified that the resulting graph is equal to the
 * graph of the method that just has a "return 1" statement in it.
 */
public class NestedLoopTest extends GraphTest {

    @Test
    public void test1() {
        test("test1Snippet");
    }

    @Test
    public void test2() {
        test("test2Snippet");
    }

    @Test
    public void test3() {
        test("test3Snippet");
    }

    @Test
    public void test4() {
        test("test4Snippet");
    }

    public static void test1Snippet(int a) {
        while (a()) {
            m1: while (b()) {
                while (c()) {
                    if (d()) {
                        break m1;
                    }
                }
            }
        }
    }

    public static void test2Snippet(int a) {
        while (a()) {
            try {
                m1: while (b()) {
                    while (c()) {
                        if (d()) {
                            break m1;
                        }
                    }
                }
            } catch (Throwable t) {
            }
        }
    }

    public static void test3Snippet(int a) {
        while (a == 0) {
            try {
                m1: while (b()) {
                    while (c()) {
                        if (d()) {
                            a();
                            break m1;
                        }
                    }
                }
            } catch (Throwable t) {
            }
        }
    }

    public static void test4Snippet(int a) {
        while (a != 0) {
            try {
                m1: while (a != 0) {
                    b();
                    while (c()) {
                        if (d()) {
                            break m1;
                        }
                    }
                    if (a != 2) {
                        a();
                        throw new Exception();
                    }
                }
            } catch (Throwable t) {
            }
        }
    }

    private static boolean a() {
        return false;
    }

    private static boolean b() {
        return false;
    }

    private static boolean c() {
        return false;
    }

    private static boolean d() {
        return false;
    }

    private Invoke getInvoke(String name, StructuredGraph graph) {
        for (Invoke invoke : graph.getInvokes()) {
            if (invoke.callTarget().targetMethod().name().equals(name)) {
                return invoke;
            }
        }
        return null;
    }

    private void test(String snippet) {
        StructuredGraph graph = parse(snippet);
        print(graph);
        LoopInfo loopInfo = LoopUtil.computeLoopInfo(graph);
        loopInfo.print();
        Loop rootLoop = loopInfo.rootLoops().get(0);
        Loop nestedLoop = rootLoop.children().get(0);
        Loop innerMostLoop = nestedLoop.children().get(0);
        Invoke a = getInvoke("a", graph);
        Invoke b = getInvoke("b", graph);
        Invoke c = getInvoke("c", graph);
        Invoke d = getInvoke("d", graph);
        Assert.assertTrue(rootLoop.localContainsFixed((FixedNode) a));
        Assert.assertTrue(nestedLoop.localContainsFixed((FixedNode) b));
        Assert.assertTrue(innerMostLoop.localContainsFixed((FixedNode) c));
        Assert.assertTrue(innerMostLoop.localContainsFixed((FixedNode) d));
        print(graph);
    }
}
