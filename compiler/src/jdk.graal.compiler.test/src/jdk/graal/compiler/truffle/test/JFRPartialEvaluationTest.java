/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.test.SubprocessTestUtils;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.truffle.test.nodes.AbstractTestNode;
import jdk.graal.compiler.truffle.test.nodes.RootTestNode;
import jdk.jfr.Event;
import jdk.jfr.Name;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.Assert.assertNotNull;

public class JFRPartialEvaluationTest extends PartialEvaluationTest {

    @Test
    public void testException() throws IOException, InterruptedException {
        runInSubprocessWithJFREnabled(this::performTestException);
    }

    private void performTestException() {
        RootTestNode root = new RootTestNode(new FrameDescriptor(), "NewException", new NewTruffleExceptionNode());
        OptimizedCallTarget callTarget = (OptimizedCallTarget) root.getCallTarget();
        StructuredGraph graph = partialEval(callTarget, new Object[0]);
        // The call from the exception constructor to the JFR tracing must not be inlined.
        assertNotNull("The call to ThrowableTracer#traceThrowable was not inlined or is missing.", findAnyInvoke(graph,
                        "jdk.jfr.internal.instrument.ThrowableTracer#traceThrowable",
                        "jdk.internal.event.ThrowableTracer#traceThrowable"));
        // Also make sure that the node count hasn't exploded.
        assertTrue("The number of graal nodes for an exception instantiation exceeded 100.", graph.getNodeCount() < 100);
    }

    @Test
    public void testError() throws IOException, InterruptedException {
        runInSubprocessWithJFREnabled(this::performTestError);
    }

    private void performTestError() {
        RootTestNode root = new RootTestNode(new FrameDescriptor(), "NewError", new NewErrorNode());
        OptimizedCallTarget callTarget = (OptimizedCallTarget) root.getCallTarget();
        StructuredGraph graph = partialEval(callTarget, new Object[0]);
        // The call from the exception constructor to the JFR tracing must not be inlined.
        assertNotNull("The call to ThrowableTracer#traceThrowable was not inlined or is missing.", findAnyInvoke(graph,
                        "jdk.jfr.internal.instrument.ThrowableTracer#traceThrowable",
                        "jdk.internal.event.ThrowableTracer#traceThrowable"));
        assertNotNull("The call to ThrowableTracer#traceError was not inlined or is missing.", findAnyInvoke(graph,
                        "jdk.jfr.internal.instrument.ThrowableTracer#traceError",
                        "jdk.internal.event.ThrowableTracer#traceError"));
        // Also make sure that the node count hasn't exploded.
        assertTrue("The number of graal nodes for an exception instantiation exceeded 100.", graph.getNodeCount() < 100);
    }

    @Test
    public void testJFREvent() throws IOException, InterruptedException {
        runInSubprocessWithJFREnabled(this::performTestJFREvent);
    }

    private void performTestJFREvent() {
        RootTestNode root = new RootTestNode(new FrameDescriptor(), "JFREvent", new JFREventNode());
        OptimizedCallTarget callTarget = (OptimizedCallTarget) root.getCallTarget();
        StructuredGraph graph = partialEval(callTarget, new Object[0]);
        // Calls to JFR event methods must not be inlined.
        assertNotNull("The call to TestEvent#shouldCommit was not inlined or is missing.", findAnyInvoke(graph,
                        "jdk.graal.compiler.truffle.test.JFRPartialEvaluationTest$TestEvent#shouldCommit"));
        assertNotNull("The call to TestEvent#commit was not inlined or is missing.", findAnyInvoke(graph,
                        "jdk.graal.compiler.truffle.test.JFRPartialEvaluationTest$TestEvent#commit"));
        // Also make sure that the node count hasn't exploded.
        assertTrue("The number of graal nodes for an exception instantiation exceeded 100.", graph.getNodeCount() < 100);
    }

    private static MethodCallTargetNode findAnyInvoke(StructuredGraph graph, String... expectedMethodNames) {
        Set<String> expectedSet = Set.of(expectedMethodNames);
        for (MethodCallTargetNode node : graph.getNodes(MethodCallTargetNode.TYPE)) {
            String targetMethodName = node.targetMethod().format("%H#%n");
            if (expectedSet.contains(targetMethodName)) {
                return node;
            }
        }
        return null;
    }

    private static void runInSubprocessWithJFREnabled(Runnable action) throws IOException, InterruptedException {
        Path jfrFile;
        if (SubprocessTestUtils.isSubprocess()) {
            jfrFile = null;
        } else {
            jfrFile = Files.createTempFile("new_truffle_exception", ".jfr");
        }
        try {
            SubprocessTestUtils.newBuilder(JFRPartialEvaluationTest.class, action) //
                            .prefixVmOption(String.format("-XX:StartFlightRecording=exceptions=all,filename=%s", jfrFile)) //
                            .onExit((p) -> {
                                try {
                                    assertTrue(String.format("JFR event file %s is missing", jfrFile), Files.size(jfrFile) > 0);
                                } catch (IOException ioe) {
                                    throw new AssertionError("Failed to stat JFR event file " + jfrFile, ioe);
                                }
                            }) //
                            .run();
        } finally {
            if (jfrFile != null) {
                Files.deleteIfExists(jfrFile);
            }
        }
    }

    private static final class NewTruffleExceptionNode extends AbstractTestNode {

        @Override
        public int execute(VirtualFrame frame) {
            new AbstractTruffleException() {
            };
            return 1;
        }
    }

    private static final class NewErrorNode extends AbstractTestNode {

        @Override
        public int execute(VirtualFrame frame) {
            new ErrorImpl();
            return 1;
        }

        @SuppressWarnings("serial")
        private static final class ErrorImpl extends Error {

            @Override
            @SuppressWarnings("sync-override")
            public Throwable fillInStackTrace() {
                return this;
            }
        }
    }

    private static final class JFREventNode extends AbstractTestNode {

        @Override
        public int execute(VirtualFrame frame) {
            TestEvent event = new TestEvent();
            if (event.shouldCommit()) {
                event.commit();
            }
            return 1;
        }
    }

    @Name("test.JFRPartialEvaluationTestEvent")
    private static class TestEvent extends Event {
    }
}
