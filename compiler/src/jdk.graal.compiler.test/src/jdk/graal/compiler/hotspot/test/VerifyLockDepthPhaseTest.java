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
package jdk.graal.compiler.hotspot.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.hotspot.phases.VerifyLockDepthPhase;
import jdk.graal.compiler.hotspot.phases.VerifyLockDepthPhase.LockStructureError;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.java.MonitorEnterNode;
import jdk.graal.compiler.nodes.java.MonitorExitNode;
import jdk.graal.compiler.nodes.java.MonitorIdNode;
import jdk.graal.compiler.nodes.util.GraphUtil;

public class VerifyLockDepthPhaseTest extends HotSpotGraalCompilerTest {

    public static void snippet(Object object, boolean condition) {
        if (condition) {
            synchronized (object) {
                GraalDirectives.controlFlowAnchor();
            }
        }
    }

    private static final class TestPhase extends VerifyLockDepthPhase {
        void verify(StructuredGraph graph) {
            run(graph);
        }
    }

    @Test
    public void balancedMonitorsPass() {
        StructuredGraph graph = parseEager("snippet", AllowAssumptions.NO);
        new TestPhase().verify(graph);
    }

    @Test
    public void unequalLocksAtMergeReportLockStructureError() {
        StructuredGraph graph = parseEager("snippet", AllowAssumptions.NO);
        MonitorExitNode exit = graph.getNodes(MonitorExitNode.TYPE).first();
        Assert.assertNotNull(exit);
        GraphUtil.removeFixedWithUnusedInputs(exit);
        LockStructureError error = Assert.assertThrows(LockStructureError.class, () -> new TestPhase().verify(graph));
        assertTrue(error.getMessage(), error.getMessage().contains("lock stack mismatch"));
    }

    @Test
    public void wrongFrameMonitorReportsLockStructureError() {
        StructuredGraph graph = parseEager("snippet", AllowAssumptions.NO);
        MonitorEnterNode enter = graph.getNodes(MonitorEnterNode.TYPE).first();
        Assert.assertNotNull(enter);
        enter.stateAfter().monitorIds().set(0, graph.add(new MonitorIdNode(0)));
        LockStructureError error = Assert.assertThrows(LockStructureError.class, () -> new TestPhase().verify(graph));
        assertTrue(error.getMessage(), error.getMessage().contains("mismatched lock at depth 0"));
    }
}
