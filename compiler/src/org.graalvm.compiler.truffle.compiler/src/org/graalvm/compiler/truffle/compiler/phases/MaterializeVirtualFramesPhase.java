/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.phases;

import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.truffle.compiler.nodes.frame.AllowMaterializeNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.NewFrameNode;

public class MaterializeVirtualFramesPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        for (InvokeWithExceptionNode node : graph.getNodes(InvokeWithExceptionNode.TYPE)) {
            checkInvokeForVirtualFrame(graph, node);
        }
    }

    private static void checkInvokeForVirtualFrame(StructuredGraph graph, InvokeWithExceptionNode invoke) {
        final CallTargetNode callTargetNode = invoke.callTarget();
        for (ValueNode argument : callTargetNode.arguments()) {
            if (argument instanceof NewFrameNode) {
                callTargetNode.arguments().remove(argument);
                final AllowMaterializeNode allowMaterializeNode = graph.add(new AllowMaterializeNode(argument));
                graph.addBeforeFixed(invoke, allowMaterializeNode);
                callTargetNode.arguments().add(allowMaterializeNode);
                return;
            }
        }
    }
}
