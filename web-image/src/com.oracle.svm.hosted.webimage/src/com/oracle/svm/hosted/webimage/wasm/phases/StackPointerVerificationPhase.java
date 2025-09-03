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

package com.oracle.svm.hosted.webimage.wasm.phases;

import org.graalvm.nativeimage.AnnotationAccess;

import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.hosted.webimage.wasm.debug.NoStackVerification;
import com.oracle.svm.hosted.webimage.wasm.debug.WasmDebug;

import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;

/**
 * Inserts instrumentation into each method graph to verify that the stack pointer isn't changed
 * during the method execution (called methods should restore the correct stack pointer).
 * <p>
 * For this, at the beginning a fixed node is inserted to read the stack pointer (using
 * {@link KnownIntrinsics#readStackPointer()} generally results in floating nodes). Later check
 * points are inserted that call WasmDebug#checkStackPointer(long) to verify the stack pointer
 * hasn't changed.
 */
public class StackPointerVerificationPhase extends BasePhase<CoreProviders> {
    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        if (AnnotationAccess.isAnnotationPresent(graph.method(), NoStackVerification.class)) {
            return;
        }

        ForeignCallNode originalStackPointer = graph.addOrUnique(new ForeignCallNode(WasmDebug.READ_STACK_POINTER));

        graph.addAfterFixed(graph.start(), originalStackPointer);

        for (ControlSinkNode controlSink : graph.getNodes().filter(ControlSinkNode.class)) {
            addCheckBeforeFixed(graph, controlSink, originalStackPointer);
        }

        for (InvokeNode invoke : graph.getNodes().filter(InvokeNode.class)) {
            addCheckBeforeAndAfter(graph, invoke, originalStackPointer);
        }

        for (InvokeWithExceptionNode invoke : graph.getNodes(InvokeWithExceptionNode.TYPE)) {
            addCheckBeforeFixed(graph, invoke, originalStackPointer);
            addCheckAfterFixed(graph, invoke.next(), originalStackPointer);
            addCheckAfterFixed(graph, invoke.exceptionEdge(), originalStackPointer);
        }
    }

    private static void addCheckBeforeAndAfter(StructuredGraph graph, FixedWithNextNode fixed, ValueNode originalStackPointer) {
        addCheckBeforeFixed(graph, fixed, originalStackPointer);
        addCheckAfterFixed(graph, fixed, originalStackPointer);
    }

    private static void addCheckBeforeFixed(StructuredGraph graph, FixedNode fixed, ValueNode originalStackPointer) {
        ForeignCallNode checkStackPointer = graph.addOrUnique(new ForeignCallNode(WasmDebug.CHECK_STACK_POINTER, originalStackPointer));
        graph.addBeforeFixed(fixed, checkStackPointer);
    }

    private static void addCheckAfterFixed(StructuredGraph graph, FixedWithNextNode fixed, ValueNode originalStackPointer) {
        ForeignCallNode checkStackPointer = graph.addOrUnique(new ForeignCallNode(WasmDebug.CHECK_STACK_POINTER, originalStackPointer));
        graph.addAfterFixed(fixed, checkStackPointer);
    }
}
