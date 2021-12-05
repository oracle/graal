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
package org.graalvm.compiler.java;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BytecodeExceptionNodeSourceCollection {

    private static final ConcurrentLinkedQueue<NodeSourcePosition> originals = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<NodeSourcePosition> exceptionObjectsSourcePosition = new ConcurrentLinkedQueue<>();

    public void collect(StructuredGraph graph) {
        for (Node node : graph.getNodes()) {
            if (node instanceof BytecodeExceptionNode) {
                originals.add(node.getNodeSourcePosition());
            } else if (node instanceof ExceptionObjectNode) {
                /*
                 * These source positions are also important because ExceptionObjectNode is the
                 * entry to an exception handler with the exception coming from a call.
                 */
                exceptionObjectsSourcePosition.add(node.getNodeSourcePosition());
            }
        }
    }

    public static BytecodeExceptionNodeSourceCollection create() {
        return new BytecodeExceptionNodeSourceCollection();
    }

    public static boolean isOriginal(NodeSourcePosition nodeSourcePosition) {
        return originals.contains(nodeSourcePosition);
    }

    private static boolean isExceptionObjectPosition(NodeSourcePosition nodeSourcePosition) {
        return exceptionObjectsSourcePosition.contains(nodeSourcePosition);
    }

    private static NodeSourcePosition getRootNodeSourcePosition(NodeSourcePosition nodeSourcePosition) {
        ResolvedJavaMethod rootMethod = nodeSourcePosition.getRootMethod();
        return new NodeSourcePosition(nodeSourcePosition.getSourceLanguage(), null, rootMethod, getRootBci(nodeSourcePosition));
    }

    private static int getRootBci(NodeSourcePosition nodeSourcePosition) {
        NodeSourcePosition cur = nodeSourcePosition;
        while (cur.getCaller() != null) {
            cur = cur.getCaller();
        }
        return cur.getBCI();
    }

    private static boolean equals(NodeSourcePosition position1, NodeSourcePosition position2) {
        return position1.getBCI() == position2.getBCI() && Objects.equals(position1.getMethod(), position2.getMethod()) &&
                        Objects.equals(position1.getSourceLanguage(), position2.getSourceLanguage());
    }

    private static boolean foundPrefix(NodeSourcePosition original, NodeSourcePosition newNodeSourcePosition) {
        if (original.depth() > newNodeSourcePosition.depth()) {
            return false;
        }

        NodeSourcePosition position1 = original;
        NodeSourcePosition position2 = newNodeSourcePosition;

        while (position1 != null) {
            if (!equals(original, newNodeSourcePosition)) {
                return false;
            }
            position1 = position1.getCaller();
            position2 = position2.getCaller();
        }
        return true;
    }

    public static boolean hasOriginalPrefix(NodeSourcePosition nodeSourcePosition) {
        for (NodeSourcePosition org : originals) {
            if (foundPrefix(org, nodeSourcePosition)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasOriginalRoot(NodeSourcePosition nodeSourcePosition) {
        return isOriginal(getRootNodeSourcePosition(nodeSourcePosition));
    }

    public static boolean hasRootFromExceptionObject(NodeSourcePosition nodeSourcePosition) {
        return isExceptionObjectPosition(getRootNodeSourcePosition(nodeSourcePosition));
    }
}
