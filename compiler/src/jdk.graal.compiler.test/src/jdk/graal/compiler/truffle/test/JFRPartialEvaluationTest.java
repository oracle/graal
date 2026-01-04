/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

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
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class JFRPartialEvaluationTest extends PartialEvaluationTest {

    public static Object constant42() {
        return 42;
    }

    @Test
    public void testException() throws IOException, InterruptedException {
        runInSubprocessWithJFREnabled(this::performTestException);
    }

    private void performTestException() {
        RootTestNode root = new RootTestNode(new FrameDescriptor(), "NewException", new AbstractTestNode() {
            @Override
            public int execute(VirtualFrame frame) {
                try {
                    throw new TruffleExceptionImpl(42);
                } catch (TruffleExceptionImpl e) {
                    return e.value;
                }
            }
        });
        assertPartialEvalEquals(JFRPartialEvaluationTest::constant42, root);
    }

    @SuppressWarnings("serial")
    private static final class TruffleExceptionImpl extends AbstractTruffleException {
        final int value;

        TruffleExceptionImpl(int value) {
            this.value = value;
        }
    }

    @Test
    public void testError() throws IOException, InterruptedException {
        runInSubprocessWithJFREnabled(this::performTestError);
    }

    private void performTestError() {
        RootTestNode root = new RootTestNode(new FrameDescriptor(), "NewError", new AbstractTestNode() {
            @Override
            public int execute(VirtualFrame frame) {
                try {
                    throw new ErrorImpl(42);
                } catch (ErrorImpl e) {
                    return e.value;
                }
            }
        });
        // On JDK-22+ JFR exception tracing is unconditionally disabled by PartialEvaluator
        assertPartialEvalEquals(JFRPartialEvaluationTest::constant42, root);
    }

    @SuppressWarnings("serial")
    private static final class ErrorImpl extends Error {

        final int value;

        ErrorImpl(int value) {
            this.value = value;
        }

        @Override
        @SuppressWarnings("sync-override")
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    @Test
    public void testJFREvent() throws IOException, InterruptedException {
        runInSubprocessWithJFREnabled(this::performTestJFREvent);
    }

    private void performTestJFREvent() {
        ResolvedJavaMethod shouldCommit = getResolvedJavaMethod(TestEvent.class, "shouldCommit");
        ResolvedJavaMethod commit = getResolvedJavaMethod(TestEvent.class, "commit");
        RootTestNode root = new RootTestNode(new FrameDescriptor(), "JFREvent", new AbstractTestNode() {
            @Override
            public int execute(VirtualFrame frame) {
                TestEvent event = new TestEvent();
                if (event.shouldCommit()) {
                    event.commit();
                }
                return 1;
            }
        });
        OptimizedCallTarget callTarget = (OptimizedCallTarget) root.getCallTarget();
        StructuredGraph graph = partialEval(callTarget, new Object[0]);
        // Calls to JFR event methods must not be inlined.
        assertNotNull("The call to TestEvent#shouldCommit was not inlined or is missing.", findInvoke(graph, shouldCommit));
        assertNotNull("The call to TestEvent#commit was not inlined or is missing.", findInvoke(graph, commit));
        // Also make sure that the node count hasn't exploded.
        assertTrue("The number of graal nodes for an exception instantiation exceeded 100.", graph.getNodeCount() < 100);
    }

    @Name("test.JFRPartialEvaluationTestEvent")
    private static final class TestEvent extends Event {
    }

    private static MethodCallTargetNode findInvoke(StructuredGraph graph, ResolvedJavaMethod expectedMethod) {
        for (MethodCallTargetNode node : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod targetMethod = node.targetMethod();
            if (expectedMethod.equals(targetMethod)) {
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
                            .onExit((_) -> {
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
}
