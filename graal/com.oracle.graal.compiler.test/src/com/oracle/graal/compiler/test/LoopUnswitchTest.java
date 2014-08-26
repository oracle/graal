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
package com.oracle.graal.compiler.test;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.*;
import com.oracle.graal.loop.phases.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;

public class LoopUnswitchTest extends GraalCompilerTest {

    public static int referenceSnippet1(int a) {
        int sum = 0;
        if (a > 2) {
            for (int i = 0; i < 1000; i++) {
                sum += 2;
            }
        } else {
            for (int i = 0; i < 1000; i++) {
                sum += a;
            }
        }
        return sum;
    }

    public static int test1Snippet(int a) {
        int sum = 0;
        for (int i = 0; i < 1000; i++) {
            if (a > 2) {
                sum += 2;
            } else {
                sum += a;
            }
        }
        return sum;
    }

    public static int referenceSnippet2(int a) {
        int sum = 0;
        switch (a) {
            case 0:
                for (int i = 0; i < 1000; i++) {
                    sum += System.currentTimeMillis();
                }
                break;
            case 1:
                for (int i = 0; i < 1000; i++) {
                    sum += 1;
                    sum += 5;
                }
                break;
            case 55:
                for (int i = 0; i < 1000; i++) {
                    sum += 5;
                }
                break;
            default:
                for (int i = 0; i < 1000; i++) {
                    // nothing
                }
                break;
        }
        return sum;
    }

    public static int test2Snippet(int a) {
        int sum = 0;
        for (int i = 0; i < 1000; i++) {
            switch (a) {
                case 0:
                    sum += System.currentTimeMillis();
                    break;
                case 1:
                    sum += 1;
                    // fall through
                case 55:
                    sum += 5;
                    break;
                default:
                    // nothing
                    break;
            }
        }
        return sum;
    }

    @Test
    public void test1() {
        test("test1Snippet", "referenceSnippet1");
    }

    @Test
    public void test2() {
        test("test2Snippet", "referenceSnippet2");
    }

    private void test(String snippet, String referenceSnippet) {
        final StructuredGraph graph = parseEager(snippet);
        final StructuredGraph referenceGraph = parseEager(referenceSnippet);

        new LoopTransformLowPhase().apply(graph);

        // Framestates create comparison problems
        for (Node stateSplit : graph.getNodes().filterInterface(StateSplit.class)) {
            ((StateSplit) stateSplit).setStateAfter(null);
        }
        for (Node stateSplit : referenceGraph.getNodes().filterInterface(StateSplit.class)) {
            ((StateSplit) stateSplit).setStateAfter(null);
        }

        Assumptions assumptions = new Assumptions(false);
        new CanonicalizerPhase(true).apply(graph, new PhaseContext(getProviders(), assumptions));
        new CanonicalizerPhase(true).apply(referenceGraph, new PhaseContext(getProviders(), assumptions));
        try (Scope s = Debug.scope("Test", new DebugDumpScope("Test:" + snippet))) {
            assertEquals(referenceGraph, graph);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }
}
