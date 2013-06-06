/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.hotspot.phases.*;
import com.oracle.graal.nodes.HeapAccess.WriteBarrierType;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.Lowerable.LoweringType;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;

public class WriteBarrierAdditionTest extends GraalCompilerTest {

    public static class Container {

        public Container a;
        public Container b;
    }

    public static void test1Snippet() {
        Container main = new Container();
        Container temp1 = new Container();
        Container temp2 = new Container();
        main.a = temp1;
        main.b = temp2;
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

    @Test
    public void test1() {
        test("test1Snippet", 2);
    }

    @Test
    public void test2() {
        test("test2Snippet", 4);
    }

    @Test
    public void test3() {
        test("test3Snippet", 4);
    }

    private void test(final String snippet, final int expectedBarriers) {
        Debug.scope("WriteBarrierAditionTest", new DebugDumpScope(snippet), new Runnable() {

            public void run() {
                StructuredGraph graph = parse(snippet);
                HighTierContext context = new HighTierContext(runtime(), new Assumptions(false), replacements, new CanonicalizerPhase(true));
                new LoweringPhase(LoweringType.BEFORE_GUARDS).apply(graph, context);
                new WriteBarrierAdditionPhase().apply(graph);
                Debug.dump(graph, "After Write Barrier Addition");
                final int barriers = graph.getNodes(SerialWriteBarrier.class).count();
                Assert.assertTrue(barriers == expectedBarriers);
                for (WriteNode write : graph.getNodes(WriteNode.class)) {
                    if (write.getWriteBarrierType() != WriteBarrierType.NONE) {
                        Assert.assertTrue(write.successors().count() == 1);
                        Assert.assertTrue(write.next() instanceof SerialWriteBarrier);
                    }
                }
            }
        });
    }
}
