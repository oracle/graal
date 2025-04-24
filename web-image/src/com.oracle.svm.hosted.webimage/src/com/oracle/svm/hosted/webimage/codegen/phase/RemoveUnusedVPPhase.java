/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage.codegen.phase;

import java.util.List;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.Phase;

public class RemoveUnusedVPPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        for (LoopExitNode exit : graph.getNodes(LoopExitNode.TYPE)) {
            FrameState stateAfter = exit.stateAfter();
            if (stateAfter != null) {
                exit.setStateAfter(null);
                if (stateAfter.usages().isEmpty()) {
                    GraphUtil.killWithUnusedFloatingInputs(stateAfter);
                }
            }
        }
        for (LoopExitNode exit : graph.getNodes(LoopExitNode.TYPE)) {
            proxies: for (ProxyNode proxy : exit.proxies().snapshot()) {
                if (!hasNonFrameStateUsages(proxy)) {
                    for (Node usage : proxy.usages().snapshot()) {
                        if (usage instanceof FrameState && usage.usages().count() == 0) {
                            FrameState f = (FrameState) usage;
                            f.safeDelete();
                        } else {
                            continue proxies;
                        }
                    }
                    proxy.safeDelete();
                }
            }
        }
    }

    private static boolean hasNonFrameStateUsages(ProxyNode proxy) {
        List<Node> usages = proxy.usages().snapshot();
        for (Node usage : usages) {
            if (!(usage instanceof FrameState)) {
                return true;
            }
        }
        return false;
    }
}
