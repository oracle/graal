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
package jdk.graal.compiler.truffle.test;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class GR75881Test extends PartialEvaluationTest {

    @Test
    public void testInCompilationRootFoldedAfterInlining() {
        RootNode callee = new CalleeRootNode();
        StructuredGraph graph = partialEval(new CallerRootNode(callee.getCallTarget()));

        Assert.assertEquals("root marker should survive as a positive control", 1, countCallsTo(graph, "rootBoundaryMarker"));
        Assert.assertEquals("inlined callee marker should be removed when inCompilationRoot folds to false", 0, countCallsTo(graph, "calleeBoundaryMarker"));
    }

    private static long countCallsTo(StructuredGraph graph, String methodName) {
        return graph.getNodes(MethodCallTargetNode.TYPE).stream().filter(callTarget -> {
            ResolvedJavaMethod targetMethod = callTarget.targetMethod();
            return targetMethod.getDeclaringClass().toJavaName().equals(GR75881Test.class.getName()) && targetMethod.getName().equals(methodName);
        }).count();
    }

    private static final class CallerRootNode extends RootNode {
        @Child private DirectCallNode callNode;

        CallerRootNode(CallTarget calleeTarget) {
            super(null);
            this.callNode = DirectCallNode.create(calleeTarget);
            this.callNode.forceInlining();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            int result = (int) callNode.call(frame.getArguments());
            if (CompilerDirectives.inCompilationRoot()) {
                result = rootBoundaryMarker(result);
            }
            return result;
        }

        @Override
        public String getName() {
            return "GR75881Caller";
        }
    }

    private static final class CalleeRootNode extends RootNode {

        CalleeRootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (CompilerDirectives.inCompilationRoot()) {
                return calleeBoundaryMarker(42);
            }
            return 7;
        }

        @Override
        public String getName() {
            return "GR75881Callee";
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = false)
    private static int rootBoundaryMarker(int value) {
        return value;
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = false)
    private static int calleeBoundaryMarker(int value) {
        return value;
    }
}
