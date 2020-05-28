/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.instrumentation.test;

import static com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.GCUtils;

public class GradualInstrumentationTest {
    private Context context;
    private TruffleInstrument.Env instrumentEnv;

    @Before
    public void setup() {
        context = Context.create(ID);
        instrumentEnv = context.getEngine().getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
    }

    @After
    public void teardown() {
        if (context != null) {
            context.close();
        }
    }

    /**
     * Test that retired subtree is till reachable allowing us to add new execution listeners to
     * nodes that are still executing but are not reachable in the current AST.
     */
    @Test
    public void testRetiredTreeStillReachable() throws InterruptedException {
        Source source = Source.create(ID, "ROOT(MATERIALIZE_CHILD_EXPRESSION(LOOP(3, STATEMENT(EXPRESSION))))");
        RecordingExecutionEventListener listener1 = new RecordingExecutionEventListener(true);
        EventBinding<ExecutionEventListener> binding = attachRecordingListener(listener1, StandardTags.StatementTag.class);
        // no materialization happens on first execution because the expression tag is not specified
        Thread t = evalInNewThread(source, listener1);
        listener1.go("$START", "+S", "+S", "-S");
        listener1.waitUntilStopped();
        RecordingExecutionEventListener listener2 = attachRecordingListener();
        listener1.go("+S", "-S");
        listener1.waitUntilStopped();
        RecordingExecutionEventListener listener3 = attachRecordingListener();
        listener1.go("+S", "-S", "-S", "$END");
        t.join();
        binding.dispose();
        RecordingExecutionEventListener listener4 = attachRecordingListener();
        context.eval(source);
        assertEquals("+E-E-S+S+E-E-S-S+R+S+E-E+L+S+E-E-S+S+E-E-S+S+E-E-S-L-S-R", listener2.getRecording());
        assertEquals("+E-E-S-S+R+S+E-E+L+S+E-E-S+S+E-E-S+S+E-E-S-L-S-R", listener3.getRecording());
        assertEquals("+R+S+E-E+L+S+E-E-S+S+E-E-S+S+E-E-S-L-S-R", listener4.getRecording());
    }

    private RecordingExecutionEventListener attachRecordingListener() {
        RecordingExecutionEventListener listener = new RecordingExecutionEventListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, listener);
        return listener;
    }

    private RecordingExecutionEventListener attachRecordingListener(Class<?>... tags) {
        return attachRecordingListener(false, tags);
    }

    private RecordingExecutionEventListener attachRecordingListener(boolean stepping, Class<?>... tags) {
        RecordingExecutionEventListener listener = new RecordingExecutionEventListener(stepping);
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(tags).build(), listener);
        return listener;
    }

    private EventBinding<ExecutionEventListener> attachRecordingListener(ExecutionEventListener listener, Class<?> tags) {
        return instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(tags).build(), listener);
    }

    private Thread evalInNewThread(Source source, RecordingExecutionEventListener listener1) {
        Thread t = new Thread(() -> {
            listener1.start();
            try {
                context.eval(source);
            } finally {
                listener1.end();
            }
        });
        t.start();
        return t;
    }

    /**
     * Test that instrumentation wrappers are created even for nodes which do not emit any
     * instrumentation events when those wrappers are needed to store references to retired nodes.
     */
    @Test
    public void testForcedCreationOfWrappers() throws InterruptedException {
        Source source = Source.create(ID, "ROOT(MATERIALIZE_CHILD_EXPRESSION(LOOP(0, STATEMENT), LOOP(3, STATEMENT(EXPRESSION))))");
        /*
         * No materialization happens on first execution because the expression tag is not
         * specified, MATERIALIZE_CHILD_EXPRESSION node is not instrumented because the statement
         * tag is not specified.
         */
        RecordingExecutionEventListener listener1 = attachRecordingListener(true, InstrumentationTestLanguage.LoopTag.class);
        Thread t = evalInNewThread(source, listener1);
        listener1.go("$START", "+L");
        RecordingExecutionEventListener listener2 = attachRecordingListener(StandardTags.ExpressionTag.class);
        RecordingExecutionEventListener listener3 = attachRecordingListener(StandardTags.StatementTag.class);
        listener1.go("-L", "+L", "-L", "$END");
        t.join();
        assertEquals("+E-E+E-E+E-E", listener2.getRecording());
        assertEquals("+S-S+S-S+S-S", listener3.getRecording());
    }

    /**
     * Test that even if a binding that causes a certain node to be instrumented is disposed leading
     * to no instrumentation events being emitted for that node. The wrapper is still not removed
     * when it contains a reference to a retired subtree that is still executing.
     */
    @Test
    public void testProbesWithRetiredNodeReferencesAreNotDisposed() throws InterruptedException {
        Source source = Source.create(ID, "ROOT(MATERIALIZE_CHILD_EXPRESSION(LOOP(0, STATEMENT), LOOP(3, STATEMENT(EXPRESSION))))");
        /*
         * No materialization happens on first execution because the expression tag is not
         * specified, MATERIALIZE_CHILD_EXPRESSION node is not instrumented because the statement
         * tag is not specified.
         */
        RecordingExecutionEventListener listener1 = attachRecordingListener(true, InstrumentationTestLanguage.LoopTag.class);
        Thread t = evalInNewThread(source, listener1);
        listener1.go("$START", "+L");
        listener1.disableSteppingWhileWaiting();
        RecordingExecutionEventListener listener2 = new RecordingExecutionEventListener();
        EventBinding<ExecutionEventListener> binding = attachRecordingListener(listener2, StandardTags.ExpressionTag.class);
        binding.dispose();
        context.eval(source);
        RecordingExecutionEventListener listener3 = attachRecordingListener(StandardTags.StatementTag.class);
        listener1.go("-L");
        t.join();
        assertEquals("+L+L-L+L-L-L+L-L", listener1.getRecording());
        assertEquals("+S-S+S-S+S-S", listener3.getRecording());
    }

    /**
     * Test that problems caused by a single node being reachable both from the retired nodes and
     * new materialized subtree are not present when the subtree is cloned during materialization.
     */
    @Test
    public void testRepeatedInstrumentationDoesNotChangeParentsInMaterializedTree() {
        Source source = Source.create(ID, "ROOT(MATERIALIZE_CHILD_STMT_AND_EXPR(EXPRESSION(EXPRESSION)))");
        // execute first so that the next execution cannot take advantage of the "onFirstExecution"
        // optimization
        context.eval(source);
        RecordingExecutionEventListener listener1 = new RecordingExecutionEventListener();
        EventBinding<ExecutionEventListener> binding = attachRecordingListener(listener1, StandardTags.StatementTag.class);
        context.eval(source);
        assertEquals("+S+S-S-S", listener1.getRecording());
        binding.dispose();
        RecordingExecutionEventListener listener2 = attachRecordingListener(StandardTags.StatementTag.class, StandardTags.ExpressionTag.class);
        context.eval(source);
        /*
         * Each materialized node, which itself is a statement, has an extra statement. If the
         * materialized node has an expression as a child and the children of that expression
         * consist of only one expression, then the nested expression is connected as a child
         * directly to the materialized node in place of its parent and for each expression which is
         * ommited this way, one child expression is added to the extra statement node.
         */
        assertEquals("+S+S+E-E-S+E-E-S", listener2.getRecording());
    }

    /**
     * Test that a single node being reachable both from the retired nodes and new materialized
     * subtree can lead to some nodes in the new tree not be materialized.
     */
    @Test
    public void testRepeatedInstrumentationChangesParentsInMaterializedTreeIfSubtreesAreNotCloned() {
        Source source = Source.create(ID, "ROOT(MATERIALIZE_CHILD_STMT_AND_EXPR_NC(EXPRESSION(EXPRESSION)))");
        // execute first so that the next execution cannot take advantage of the "onFirstExecution"
        // optimization
        context.eval(source);
        RecordingExecutionEventListener listener1 = new RecordingExecutionEventListener();
        EventBinding<ExecutionEventListener> binding = attachRecordingListener(listener1, StandardTags.StatementTag.class);
        context.eval(source);
        assertEquals("+S+S-S-S", listener1.getRecording());
        binding.dispose();
        RecordingExecutionEventListener listener2 = attachRecordingListener(StandardTags.StatementTag.class, StandardTags.ExpressionTag.class);
        context.eval(source);
        /*
         * Each materialized node, which itself is a statement, has an extra statement. If the
         * materialized node has an expression as a child and the children of that expression
         * consist of only one expression, then the nested expression is connected as a child
         * directly to the materialized node in place of its parent and for each expression which is
         * ommited this way, one child expression is added to the extra statement node. Since the NC
         * node erroneously does not clone the subtree on materialization, the repeated
         * instrumentation changes the parent of the nested expression to its original expression
         * parent, and so in the new materialized tree, the nested expression node which is now a
         * direct child of the materialized NC node is not instrumented in the new tree, because it
         * was already instrumented in the old tree (it already has instrumentation wrapper).
         */
        assertEquals("+S+S+E-E-S-S", listener2.getRecording());
    }

    @Test
    public void testMultipleMaterializationNode() {
        Source source = Source.create(ID, "ROOT(MATERIALIZE_CHILD_STMT_AND_EXPR_SEPARATELY(STATEMENT(EXPRESSION)))");
        RecordingExecutionEventListener listener1 = attachRecordingListener();
        context.eval(source);
        assertEquals("+R+S+S-S+E-E+S+E-E-S-S-R", listener1.getRecording());
    }

    /**
     * Test that a retired subtree is still reachable even though the materialized node that caused
     * the first subtree to be retired is retired during further materializaton.
     */
    @Test
    public void testRetiredTreeStillReachableOnMultipleMaterializationOfTheSameNode() throws InterruptedException {
        Source source = Source.create(ID, "ROOT(MATERIALIZE_CHILD_STMT_AND_EXPR_SEPARATELY(BLOCK, LOOP(0, STATEMENT), LOOP(3, STATEMENT(CONSTANT(42)))))");
        RecordingExecutionEventListener listener1 = attachRecordingListener(true, InstrumentationTestLanguage.LoopTag.class);
        Thread t1 = evalInNewThread(source, listener1);
        listener1.go("$START", "+L");
        listener1.disableSteppingWhileWaiting();
        attachRecordingListener(StandardTags.StatementTag.class);
        RecordingExecutionEventListener listener2 = attachRecordingListener(true, InstrumentationTestLanguage.BlockTag.class);
        Thread t2 = evalInNewThread(source, listener2);
        listener2.go("$START", "+BL");
        listener2.disableSteppingWhileWaiting();
        attachRecordingListener(StandardTags.ExpressionTag.class);
        RecordingExecutionEventListener listener5 = attachRecordingListener(InstrumentationTestLanguage.ConstantTag.class);
        listener1.go("-L");
        t1.join();
        listener2.go("-BL");
        t2.join();
        context.eval(source);
        assertEquals("+C-C+C-C+C-C+C-C+C-C+C-C+C-C+C-C+C-C", listener5.getRecording());
    }

    /**
     * Test that when a tree that already contains reference to a retired subtree is retired, the
     * first retired subtree is still reachable and can be instrumented.
     */
    @Test
    public void testRetiredTreeOfRetiredTreeStillReachable() throws InterruptedException {
        Source source = Source.create(ID, "ROOT(MATERIALIZE_CHILD_STATEMENT(MATERIALIZE_CHILD_EXPRESSION(LOOP(0, STATEMENT), LOOP(3, STATEMENT(CONSTANT(42))))))");
        RecordingExecutionEventListener listener1 = attachRecordingListener(true, InstrumentationTestLanguage.LoopTag.class);
        Thread t1 = evalInNewThread(source, listener1);
        listener1.go("$START", "+L");
        listener1.disableSteppingWhileWaiting();
        attachRecordingListener(StandardTags.ExpressionTag.class);
        attachRecordingListener(StandardTags.StatementTag.class);
        RecordingExecutionEventListener listener4 = attachRecordingListener(InstrumentationTestLanguage.ConstantTag.class);
        listener1.go("-L");
        t1.join();
        assertEquals("+C-C+C-C+C-C", listener4.getRecording());
    }

    /**
     * Test that instrumentation wrappers can be removed even if their probe nodes contain
     * references to retired nodes. When retired nodes are no longer executing the weak references
     * should be cleared which should allow the wrappers to be removed.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testWrappersAreRemoved() throws InterruptedException, NoSuchFieldException, IllegalAccessException {
        Source source = Source.create(ID, "ROOT(MATERIALIZE_CHILD_STATEMENT(LOOP(0, STATEMENT), LOOP(3, STATEMENT(CONSTANT(42)))))");
        RecordingExecutionEventListener listener1 = attachRecordingListener(true, InstrumentationTestLanguage.LoopTag.class);
        Thread t1 = evalInNewThread(source, listener1);
        listener1.go("$START", "+L");
        listener1.disableSteppingWhileWaiting();
        RecordingExecutionEventListener listener2 = new RecordingExecutionEventListener(false, true);
        EventBinding<ExecutionEventListener> binding = attachRecordingListener(listener2, StandardTags.StatementTag.class);
        context.eval(source);
        listener1.go("-L");
        t1.join();
        assertEquals("+S+S-S+S-S+S-S+S-S-S+S-S+S-S+S-S", listener2.getRecording());
        List<Node> enteredMaterializedStatementNodes = listener2.getEnteredNodes().stream().filter(node -> node instanceof InstrumentationTestLanguage.MaterializedChildStatementNode).collect(
                        Collectors.toList());
        // only the materialized node with reference to the retired tree
        assertEquals(1, enteredMaterializedStatementNodes.size());
        List<WeakReference<Node>> retiredStatementNodes = new ArrayList<>();
        for (Node enteredNode : enteredMaterializedStatementNodes) {
            assertTrue(enteredNode.getParent() instanceof InstrumentableNode.WrapperNode);
            InstrumentableNode.WrapperNode wrapperNode = (InstrumentableNode.WrapperNode) enteredNode.getParent();
            Class<?> probeNodeClass = wrapperNode.getProbeNode().getClass();
            Field retiredNodeReferenceField = probeNodeClass.getDeclaredField("retiredNodeReference");
            retiredNodeReferenceField.setAccessible(true);
            Object retiredNodeReference = retiredNodeReferenceField.get(wrapperNode.getProbeNode());
            assertNotNull(retiredNodeReference);
            Class<?> retiredNodeReferenceClass = retiredNodeReference.getClass();
            Field retiredNodeWeakReferenceField = retiredNodeReferenceClass.getDeclaredField("node");
            retiredNodeWeakReferenceField.setAccessible(true);
            Object retiredNodeWeakReference = retiredNodeWeakReferenceField.get(retiredNodeReference);
            assertNotNull(retiredNodeWeakReference);
            assertTrue(retiredNodeWeakReference instanceof WeakReference);
            retiredStatementNodes.add((WeakReference<Node>) retiredNodeWeakReference);
        }
        /*
         * Clear entered nodes before calling gc so that the old subtree root is no longer reachable
         * via parents.
         */
        listener2.clearEnteredNodes();
        for (WeakReference<Node> retiredStatementNodeWeakReference : retiredStatementNodes) {
            GCUtils.assertGc("Retired node is was not collected!", retiredStatementNodeWeakReference);
        }
        binding.dispose();
        context.eval(source);
        for (Node enteredNode : enteredMaterializedStatementNodes) {
            assertFalse(enteredNode.getParent() instanceof InstrumentableNode.WrapperNode);
        }
        assertEquals("+L+L-L+L-L-L+L-L+L-L+L-L", listener1.getRecording());
    }

    public static class RecordingExecutionEventListener implements ExecutionEventListener {
        private volatile boolean error;
        private volatile String waiting = "";
        private boolean stepping;
        private final boolean recordEnteredNodes;
        private final StringBuilder sb = new StringBuilder();
        private final Object sync = new Object();
        private List<Node> enteredNodes = new ArrayList<>();

        public RecordingExecutionEventListener() {
            this(false, false);
        }

        public RecordingExecutionEventListener(boolean stepping) {
            this(stepping, false);
        }

        public RecordingExecutionEventListener(boolean stepping, boolean recordEnteredNodes) {
            this.stepping = stepping;
            this.recordEnteredNodes = recordEnteredNodes;
        }

        public void disableSteppingWhileWaiting() {
            if (stepping) {
                synchronized (sync) {
                    while (waiting.isEmpty()) {
                        try {
                            sync.wait(1000);
                        } catch (InterruptedException ie) {
                        }
                    }
                    stepping = false;
                }
            } else {
                throw new IllegalStateException("Cannot disable stepping if it is not enabled!");
            }
        }

        private String getStepId(String prefix, EventContext c) {
            if (recordEnteredNodes && "+".equals(prefix)) {
                enteredNodes.add(c.getInstrumentedNode());
            }
            return prefix + ((InstrumentationTestLanguage.BaseNode) c.getInstrumentedNode()).getShortId();
        }

        public List<Node> getEnteredNodes() {
            return Collections.unmodifiableList(enteredNodes);
        }

        public void clearEnteredNodes() {
            enteredNodes.clear();
        }

        public void go(String... stepIds) {
            for (String stepId : stepIds) {
                synchronized (sync) {
                    while (waiting.isEmpty()) {
                        try {
                            sync.wait(1000);
                        } catch (InterruptedException ie) {
                        }
                    }
                    try {
                        assertEquals("Unexpected step encountered!", stepId, waiting);
                    } catch (AssertionError ae) {
                        error = true;
                        throw ae;
                    } finally {
                        waiting = "";
                        sync.notifyAll();
                    }
                }
            }
        }

        public void waitUntilStopped() {
            synchronized (sync) {
                while (waiting.isEmpty()) {
                    try {
                        sync.wait(1000);
                    } catch (InterruptedException ie) {
                    }
                }
            }
        }

        public void start() {
            waitBeforeStep("$START");
        }

        public void end() {
            waitBeforeStep("$END");
        }

        private void waitBeforeStep(String stepId) {
            if (stepping && !error) {
                synchronized (sync) {
                    waiting = stepId;
                    while (!waiting.isEmpty()) {
                        sync.notifyAll();
                        try {
                            sync.wait(1000);
                        } catch (InterruptedException ie) {
                        }
                    }
                }
            }
        }

        @Override
        public void onEnter(EventContext context, VirtualFrame frame) {
            String stepId = getStepId("+", context);
            waitBeforeStep(stepId);
            sb.append(stepId);
        }

        @Override
        public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
            String stepId = getStepId("-", context);
            waitBeforeStep(stepId);
            sb.append(stepId);
        }

        @Override
        public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
            String stepId = getStepId("*", context);
            waitBeforeStep(stepId);
            sb.append(stepId);
        }

        public String getRecording() {
            return sb.toString();
        }
    }
}
