/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import org.graalvm.compiler.graph.Node;

/**
 * Utility class to obtain information about nodes for the purpose of {@link OptimizationLog}.
 */
public class OptimizationLogUtil {
    /**
     * Returns the bci of a node. First, it tries to get from the {@link FrameState} after the execution of this node,
     * then it tries to use the node's {@link org.graalvm.compiler.graph.NodeSourcePosition}. If everything fails, it
     * returns {@code OptimizationLog#NO_BCI}.
     * @param node the node whose bci we want to find
     * @return the bci of the node ({@code OptimizationLog#NO_BCI} if no fitting bci found)
     */
    public static int findBCI(Node node) {
        if (node instanceof StateSplit) {
            StateSplit stateSplit = (StateSplit) node;
            if (stateSplit.stateAfter() != null) {
                return stateSplit.stateAfter().bci;
            }
        }
        if (node.getNodeSourcePosition() != null) {
            return node.getNodeSourcePosition().getBCI();
        }
        return OptimizationLog.NO_BCI;
    }
}
