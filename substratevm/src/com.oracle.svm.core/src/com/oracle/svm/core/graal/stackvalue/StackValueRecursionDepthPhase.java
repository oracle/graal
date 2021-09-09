/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.stackvalue;

import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.Phase;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This phase computes the inlined recursion depth based on frame states (the documentation on
 * {@link StackValueNode} explains why that is necessary). As this phase uses the frame state, it
 * needs to run before frame state assignment (FSA).
 */
public class StackValueRecursionDepthPhase extends Phase {
    @Override
    protected void run(StructuredGraph graph) {
        for (StackValueNode node : graph.getNodes(StackValueNode.TYPE)) {
            if (!node.slotIdentity.shared) {
                int recursionDepth = computeRecursionDepth(node);
                node.setRecursionDepth(recursionDepth);
            }
        }
    }

    private static int computeRecursionDepth(StackValueNode node) {
        int result = 0;
        FrameState cur = node.stateAfter();
        ResolvedJavaMethod method = cur.getMethod();
        while ((cur = cur.outerFrameState()) != null) {
            if (method.equals(cur.getMethod())) {
                result++;
            }
        }
        return result;
    }
}
