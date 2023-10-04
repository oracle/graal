/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import jdk.compiler.graal.graph.Node;
import jdk.compiler.graal.nodes.FrameState;
import jdk.compiler.graal.nodes.FullInfopointNode;
import jdk.compiler.graal.nodes.ParameterNode;
import jdk.compiler.graal.nodes.StartNode;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.extended.ValueAnchorNode;
import jdk.compiler.graal.nodes.java.MethodCallTargetNode;
import jdk.compiler.graal.nodes.spi.ValueProxy;
import jdk.compiler.graal.replacements.nodes.MethodHandleWithExceptionNode;

import com.oracle.svm.core.SubstrateOptions;

public class InliningUtilities {

    public static boolean isTrivialMethod(StructuredGraph graph) {
        int numInvokes = 0;
        int numOthers = 0;
        for (Node n : graph.getNodes()) {
            if (n instanceof StartNode || n instanceof ParameterNode || n instanceof FullInfopointNode || n instanceof ValueProxy || n instanceof ValueAnchorNode || n instanceof FrameState) {
                continue;
            }
            if (n instanceof MethodCallTargetNode || n instanceof MethodHandleWithExceptionNode) {
                numInvokes++;
            } else {
                numOthers++;
            }

            if (!shouldBeTrivial(numInvokes, numOthers, graph)) {
                return false;
            }
        }

        return true;
    }

    private static boolean shouldBeTrivial(int numInvokes, int numOthers, StructuredGraph graph) {
        if (numInvokes == 0) {
            // This is a leaf method => we can be generous.
            return numOthers <= SubstrateOptions.MaxNodesInTrivialLeafMethod.getValue(graph.getOptions());
        } else if (numInvokes <= SubstrateOptions.MaxInvokesInTrivialMethod.getValue(graph.getOptions())) {
            return numOthers <= SubstrateOptions.MaxNodesInTrivialMethod.getValue(graph.getOptions());
        } else {
            return false;
        }
    }
}
