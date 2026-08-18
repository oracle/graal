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
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedDirectCallNode;

import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.AbstractBoxingNode;

public class ConstantArgumentReplacementTest extends PartialEvaluationTest {

    static class CalleeRootNode extends RootNode {
        CalleeRootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return frame.getArguments()[0];
        }
    }

    static class CallerRootNode extends RootNode {
        @Child DirectCallNode directCall;

        CallerRootNode(CallTarget calleeTarget) {
            super(null);
            directCall = OptimizedDirectCallNode.create(calleeTarget);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] args = new Object[]{54321};
            directCall.call(args);
            args[0] = 12345;
            return directCall.call(args);
        }
    }

    @Test
    public void testBoxedPrimitive() {
        RootCallTarget callee = (new CalleeRootNode()).getCallTarget();
        RootCallTarget caller = (new CallerRootNode(callee)).getCallTarget();
        StructuredGraph graph = partialEval((OptimizedCallTarget) caller, new Object[]{});
        NodeIterable<ReturnNode> returnNodes = graph.getNodes().filter(ReturnNode.class);
        Assert.assertEquals(1, returnNodes.count());
        ValueNode returnValue = returnNodes.first().result();
        // Regression test: constant argument replacement used to incorrectly replace the argument
        // at the second call site with 54321.
        Assert.assertEquals(12345, ((AbstractBoxingNode) returnValue).getValue().asJavaConstant().asInt());
    }
}
