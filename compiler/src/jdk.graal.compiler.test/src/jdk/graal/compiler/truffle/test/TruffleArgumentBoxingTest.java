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

import static jdk.graal.compiler.hotspot.HotSpotBackend.NEW_ARRAY_OR_NULL;
import static jdk.graal.compiler.hotspot.HotSpotBackend.NEW_INSTANCE_OR_NULL;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.truffle.hotspot.HotSpotTruffleCompilerImpl;
import jdk.graal.compiler.truffle.test.TruffleArgumentBoxingTestFactory.ImportValueNodeGen;

/**
 * Exercises the argument preparation pattern used by Truffle interop calls. A primitive value is
 * first boxed into an argument array and then passed through an Object-returning DSL dispatch. The
 * dispatch unboxes and reboxes a {@code double}; the identity comparison therefore initially keeps
 * the copy-on-change path alive even though both boxes contain the same primitive value.
 */
public class TruffleArgumentBoxingTest extends PartialEvaluationTest {

    @SuppressWarnings("truffle-inlining")
    public abstract static class ImportValueNode extends Node {

        public abstract Object executeWithTarget(Object value);

        @Specialization
        static double fromDouble(double value) {
            return value;
        }
    }

    static final class PrepareArgumentsNode extends Node {

        @Child private ImportValueNode importValue = ImportValueNodeGen.create();

        Object[] execute(Object[] args) {
            Object[] newArgs = args;
            boolean copy = false;
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                Object newArg = importValue.executeWithTarget(arg);
                if (copy) {
                    newArgs[i] = newArg;
                } else if (newArg != arg) {
                    newArgs = new Object[args.length];
                    System.arraycopy(args, 0, newArgs, 0, i);
                    newArgs[i] = newArg;
                    copy = true;
                }
            }
            return newArgs;
        }
    }

    static final class DirectRootNode extends RootNode {

        @Child private PrepareArgumentsNode prepare = new PrepareArgumentsNode();

        DirectRootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] frameArguments = frame.getArguments();
            double first = (double) frameArguments[0] + 1.0d;
            double second = (double) frameArguments[1] + 1.0d;
            Object[] args = new Object[]{first, second};
            Object[] prepared = prepare.execute(args);
            ((double[]) frameArguments[2])[0] = (double) prepared[0] + (double) prepared[1];
            return null;
        }
    }

    static final class CalleeRootNode extends RootNode {

        @Child private PrepareArgumentsNode prepare = new PrepareArgumentsNode();

        CalleeRootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] frameArguments = frame.getArguments();
            Object[] prepared = prepare.execute((Object[]) frameArguments[0]);
            ((double[]) frameArguments[1])[0] = (double) prepared[0] + (double) prepared[1];
            return null;
        }
    }

    static final class InlinedCallerRootNode extends RootNode {

        @Child private DirectCallNode callNode;

        InlinedCallerRootNode(RootNode callee) {
            super(null);
            callNode = DirectCallNode.create(callee.getCallTarget());
            callNode.forceInlining();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] frameArguments = frame.getArguments();
            double first = (double) frameArguments[0] + 1.0d;
            double second = (double) frameArguments[1] + 1.0d;
            Object[] args = new Object[]{first, second};
            return callNode.call(new Object[]{args, frameArguments[2]});
        }
    }

    @Test
    public void testDirectRoot() {
        testCompilation(new DirectRootNode());
    }

    @Test
    public void testInlinedChildRoot() {
        testCompilation(new InlinedCallerRootNode(new CalleeRootNode()));
    }

    private void testCompilation(RootNode root) {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) root.getCallTarget();
        Assume.assumeTrue("Allocation assertions use HotSpot descriptors", getTruffleCompiler(callTarget) instanceof HotSpotTruffleCompilerImpl);

        double[] interpretedResult = new double[1];
        Assert.assertNull(callTarget.call(10.25d, 20.5d, interpretedResult));
        Assert.assertEquals(32.75d, interpretedResult[0], 0.0d);

        double[] profilingResult = new double[1];
        StructuredGraph graph = partialEval(callTarget, new Object[]{30.25d, 40.5d, profilingResult});
        compile(callTarget, graph);
        Assert.assertTrue("CallTarget must have installed compiled code", callTarget.isValid());
        for (ForeignCallNode call : graph.getNodes().filter(ForeignCallNode.class)) {
            Assert.assertNotEquals("Unexpected instance allocation", NEW_INSTANCE_OR_NULL, call.getDescriptor());
            Assert.assertNotEquals("Unexpected array allocation", NEW_ARRAY_OR_NULL, call.getDescriptor());
        }

        double[] compiledResult = new double[1];
        Assert.assertNull(callTarget.call(50.25d, 60.5d, compiledResult));
        Assert.assertEquals(112.75d, compiledResult[0], 0.0d);
        Assert.assertTrue("Compiled execution must not invalidate the CallTarget", callTarget.isValid());
    }

}
