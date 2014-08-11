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
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;

public class FloatingReadTest extends GraphScheduleTest {

    public static class Container {

        public int a;
    }

    public static void changeField(Container c) {
        c.a = 0xcafebabe;
    }

    public static synchronized int test1Snippet() {
        Container c = new Container();
        return c.a;
    }

    @Test
    public void test1() {
        test("test1Snippet");
    }

    private void test(final String snippet) {
        try (Scope s = Debug.scope("FloatingReadTest", new DebugDumpScope(snippet))) {

            StructuredGraph graph = parseEager(snippet);
            PhaseContext context = new PhaseContext(getProviders(), new Assumptions(false));
            new LoweringPhase(new CanonicalizerPhase(true), LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
            new FloatingReadPhase().apply(graph);

            ReturnNode returnNode = null;
            MonitorExit monitorexit = null;

            for (Node n : graph.getNodes()) {
                if (n instanceof ReturnNode) {
                    assert returnNode == null;
                    returnNode = (ReturnNode) n;
                } else if (n instanceof MonitorExit) {
                    monitorexit = (MonitorExit) n;
                }
            }

            Debug.dump(graph, "After lowering");

            Assert.assertNotNull(returnNode);
            Assert.assertNotNull(monitorexit);
            Assert.assertTrue(returnNode.result() instanceof FloatingReadNode);

            FloatingReadNode read = (FloatingReadNode) returnNode.result();

            assertOrderedAfterSchedule(graph, read, (Node) monitorexit);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }
}
