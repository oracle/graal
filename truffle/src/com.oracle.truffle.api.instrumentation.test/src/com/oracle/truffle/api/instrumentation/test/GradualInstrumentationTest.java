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
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.Node;

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

    @Test
    public void testOldTreeStillReachable() throws InterruptedException {
        Source source = Source.create(ID, "ROOT(MATERIALIZE_CHILD_EXPRESSION(LOOP(3, STATEMENT(EXPRESSION))))");
        InstrumentationTestLanguage.RecordingExecutionEventListener listener1 = new InstrumentationTestLanguage.RecordingExecutionEventListener(true);
        EventBinding<ExecutionEventListener> binding = instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(),
                        listener1);
        // no materialization happens on first execution because the expression tag is not specified
        Thread t = new Thread(() -> {
            listener1.start();
            try {
                context.eval(source);
            } finally {
                listener1.end();
            }
        });
        t.start();
        listener1.go("$START", "+S", "+S", "-S");
        InstrumentationTestLanguage.RecordingExecutionEventListener listener2 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, listener2);
        listener1.go("+S", "-S");
        InstrumentationTestLanguage.RecordingExecutionEventListener listener3 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, listener3);
        listener1.go("+S", "-S", "-S", "$END");
        t.join();
        binding.dispose();
        InstrumentationTestLanguage.RecordingExecutionEventListener listener4 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, listener4);
        context.eval(source);
        assertEquals("+E-E-S+S+E-E-S-S+R+S+E-E+L+S+E-E-S+S+E-E-S+S+E-E-S-L-S-R", listener2.getRecording());
        assertEquals("+E-E-S-S+R+S+E-E+L+S+E-E-S+S+E-E-S+S+E-E-S-L-S-R", listener3.getRecording());
        assertEquals("+R+S+E-E+L+S+E-E-S+S+E-E-S+S+E-E-S-L-S-R", listener4.getRecording());
    }

    @Test
    public void testForcedCreationOfWrappers() throws InterruptedException {
        Source source = Source.create(ID, "ROOT(MATERIALIZE_CHILD_EXPRESSION(LOOP(0, STATEMENT), LOOP(3, STATEMENT(EXPRESSION))))");
        // no materialization happens on first execution because the expression tag is not
        // specified, MATERIALIZE_CHILD_EXPRESSION node is not instrumented because the statement
        // tag is not specified
        InstrumentationTestLanguage.RecordingExecutionEventListener listener1 = new InstrumentationTestLanguage.RecordingExecutionEventListener(true);
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.LoopTag.class).build(), listener1);
        Thread t = new Thread(() -> {
            listener1.start();
            try {
                context.eval(source);
            } finally {
                listener1.end();
            }
        });
        t.start();
        listener1.go("$START", "+L");
        InstrumentationTestLanguage.RecordingExecutionEventListener listener2 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build(), listener2);
        InstrumentationTestLanguage.RecordingExecutionEventListener listener3 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(), listener3);
        listener1.go("-L", "+L", "-L", "$END");
        t.join();
        assertEquals("+E-E+E-E+E-E", listener2.getRecording());
        assertEquals("+S-S+S-S+S-S", listener3.getRecording());
    }

    @Test
    public void testProbesWithOldNodeReferencesAreNotDisposed() throws InterruptedException {
        Source source = Source.create(ID, "ROOT(MATERIALIZE_CHILD_EXPRESSION(LOOP(0, STATEMENT), LOOP(3, STATEMENT(EXPRESSION))))");
        // no materialization happens on first execution because the expression tag is not
        // specified, MATERIALIZE_CHILD_EXPRESSION node is not instrumented because the statement
        // tag is not specified
        InstrumentationTestLanguage.RecordingExecutionEventListener listener1 = new InstrumentationTestLanguage.RecordingExecutionEventListener(true);
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.LoopTag.class).build(), listener1);
        Thread t = new Thread(() -> {
            listener1.start();
            try {
                context.eval(source);
            } finally {
                listener1.end();
            }
        });
        t.start();
        listener1.go("$START", "+L");
        listener1.disableSteppingWhileWaiting();
        InstrumentationTestLanguage.RecordingExecutionEventListener listener2 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        EventBinding<ExecutionEventListener> binding = instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build(),
                        listener2);
        binding.dispose();
        context.eval(source);
        InstrumentationTestLanguage.RecordingExecutionEventListener listener3 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(), listener3);
        listener1.go("-L");
        t.join();
        assertEquals("+L+L-L+L-L-L+L-L", listener1.getRecording());
        assertEquals("+S-S+S-S+S-S", listener3.getRecording());
    }

    @Test
    public void testRepeatedInstrumentationDoesNotChangeParentsInMaterializedTree() {
        Source source = Source.create(ID, "ROOT(MATERIALIZE_CHILD_STMT_AND_EXPR(MATERIALIZE_CHILD_STMT_AND_EXPR(EXPRESSION(EXPRESSION))))");
        // execute first so that the next execution cannot take advantage of the "onFirstExecution"
        // optimization
        context.eval(source);
        InstrumentationTestLanguage.RecordingExecutionEventListener listener1 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        EventBinding<ExecutionEventListener> binding = instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(),
                        listener1);
        context.eval(source);
        assertEquals("+S+S-S+S+S-S-S-S", listener1.getRecording());
        binding.dispose();
        InstrumentationTestLanguage.RecordingExecutionEventListener listener2 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class, StandardTags.ExpressionTag.class).build(), listener2);
        context.eval(source);
        // Each materialized node, which itself is a statement, has an extra statement. If the
        // materialized node has an expression as a child and the children of that expression
        // consist of only one expression, then the nested expression is connected as a child
        // directly to the materialized node in place of its parent and for each expression which is
        // ommited this way, one child expression is added to the extra statement node.
        assertEquals("+S+S-S+S+S+E-E-S+E-E-S-S", listener2.getRecording());
    }

    @Test
    public void testRepeatedInstrumentationChangesParentsInMaterializedTreeIfSubtreesAreNotCloned() {
        Source source = Source.create(ID, "ROOT(MATERIALIZE_CHILD_STMT_AND_EXPR_NC(MATERIALIZE_CHILD_STMT_AND_EXPR_NC(EXPRESSION(EXPRESSION))))");
        // execute first so that the next execution cannot take advantage of the "onFirstExecution"
        // optimization
        context.eval(source);
        InstrumentationTestLanguage.RecordingExecutionEventListener listener1 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        EventBinding<ExecutionEventListener> binding = instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(),
                        listener1);
        context.eval(source);
        assertEquals("+S+S-S+S+S-S-S-S", listener1.getRecording());
        binding.dispose();
        InstrumentationTestLanguage.RecordingExecutionEventListener listener2 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class, StandardTags.ExpressionTag.class).build(), listener2);
        context.eval(source);
        // Each materialized node, which itself is a statement, has an extra statement. If the
        // materialized node has an expression as a child and the children of that expression
        // consist of only one expression, then the nested expression is connected as a child
        // directly to the materialized node in place of its parent and for each expression which is
        // ommited this way, one child expression is added to the extra statement node. Since
        // the NC node erroneously does not clone the subtree on materialization, the repeated
        // instrumentation changes the parent of the nested expression to its original expression
        // parent, and so in the new materialized tree, the nested expression node which is now
        // a direct child of the materialized NC node is not instrumented in the new tree, because
        // it was already instrumented in the old tree (it already has instrumentation wrapper).
        assertEquals("+S+S-S+S+S+E-E-S-S-S", listener2.getRecording());
    }

    @Test
    public void testMultipleMaterializationNode() {
        Source source = Source.create(ID, "ROOT(MATERIALIZE_CHILD_STMT_AND_EXPR_SEPARATELY(STATEMENT(EXPRESSION)))");
        InstrumentationTestLanguage.RecordingExecutionEventListener listener1 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, listener1);
        context.eval(source);
        assertEquals("+R+S+S-S+E-E+S+E-E-S-S-R", listener1.getRecording());
    }

    @Test
    public void testOldTreeStillReachableOnMultipleMaterializationOfTheSameNode() throws InterruptedException {
        Source source = Source.create(ID, "ROOT(MATERIALIZE_CHILD_STMT_AND_EXPR_SEPARATELY(BLOCK, LOOP(0, STATEMENT), LOOP(3, STATEMENT(CONSTANT(42)))))");
        InstrumentationTestLanguage.RecordingExecutionEventListener listener1 = new InstrumentationTestLanguage.RecordingExecutionEventListener(true);
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.LoopTag.class).build(), listener1);
        Thread t1 = new Thread(() -> {
            listener1.start();
            try {
                context.eval(source);
            } finally {
                listener1.end();
            }
        });
        t1.start();
        listener1.go("$START", "+L");
        listener1.disableSteppingWhileWaiting();
        InstrumentationTestLanguage.RecordingExecutionEventListener listener3 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(), listener3);
        InstrumentationTestLanguage.RecordingExecutionEventListener listener2 = new InstrumentationTestLanguage.RecordingExecutionEventListener(true);
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.BlockTag.class).build(), listener2);
        Thread t2 = new Thread(() -> {
            listener2.start();
            try {
                context.eval(source);
            } finally {
                listener2.end();
            }
        });
        t2.start();
        listener2.go("$START", "+BL");
        listener2.disableSteppingWhileWaiting();
        InstrumentationTestLanguage.RecordingExecutionEventListener listener4 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build(), listener4);
        InstrumentationTestLanguage.RecordingExecutionEventListener listener5 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.ConstantTag.class).build(), listener5);
        listener1.go("-L");
        t1.join();
        listener2.go("-BL");
        t2.join();
        context.eval(source);
        assertEquals("+C-C+C-C+C-C+C-C+C-C+C-C+C-C+C-C+C-C", listener5.getRecording());
    }

    @Test
    public void testOldTreeOfOldTreeStillReachable() throws InterruptedException {
        Source source = Source.create(ID, "ROOT(MATERIALIZE_CHILD_STATEMENT(MATERIALIZE_CHILD_EXPRESSION(LOOP(0, STATEMENT), LOOP(3, STATEMENT(CONSTANT(42))))))");
        InstrumentationTestLanguage.RecordingExecutionEventListener listener1 = new InstrumentationTestLanguage.RecordingExecutionEventListener(true);
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.LoopTag.class).build(), listener1);
        Thread t1 = new Thread(() -> {
            listener1.start();
            try {
                context.eval(source);
            } finally {
                listener1.end();
            }
        });
        t1.start();
        listener1.go("$START", "+L");
        listener1.disableSteppingWhileWaiting();
        InstrumentationTestLanguage.RecordingExecutionEventListener listener2 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build(), listener2);
        InstrumentationTestLanguage.RecordingExecutionEventListener listener3 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(), listener3);
        InstrumentationTestLanguage.RecordingExecutionEventListener listener4 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.ConstantTag.class).build(), listener4);
        listener1.go("-L");
        t1.join();
        assertEquals("+C-C+C-C+C-C", listener4.getRecording());
    }

    @Test
    public void unnecesaryWrappersWithReferencesToOldTreeAreRemovedWhenOldTreeIsNoLongerExecuting() throws InterruptedException {
        Source source = Source.create(ID, "ROOT(MATERIALIZE_CHILD_STATEMENT(LOOP(0, STATEMENT), LOOP(3, STATEMENT(CONSTANT(42)))))");
        InstrumentationTestLanguage.RecordingExecutionEventListener listener1 = new InstrumentationTestLanguage.RecordingExecutionEventListener(true, false);
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.LoopTag.class).build(), listener1);
        Thread t1 = new Thread(() -> {
            listener1.start();
            try {
                context.eval(source);
            } finally {
                listener1.end();
            }
        });
        t1.start();
        listener1.go("$START", "+L");
        listener1.disableSteppingWhileWaiting();
        InstrumentationTestLanguage.RecordingExecutionEventListener listener2 = new InstrumentationTestLanguage.RecordingExecutionEventListener(false, true);
        EventBinding<ExecutionEventListener> binding = instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(),
                        listener2);
        context.eval(source);
        listener1.go("-L");
        t1.join();
        assertEquals("+S+S-S+S-S+S-S+S-S-S+S-S+S-S+S-S", listener2.getRecording());
        List<Node> enteredStatementNodes = listener2.getEnteredNodes().stream().filter(node -> node instanceof InstrumentationTestLanguage.MaterializedChildStatementNode).collect(Collectors.toList());
        // only the materialized node with reference to the old tree
        assertEquals(1, enteredStatementNodes.size());
        for (Node enteredNode : enteredStatementNodes) {
            assertTrue(enteredNode.getParent() instanceof InstrumentableNode.WrapperNode);
        }
        // clear entered nodes before calling gc so that the old subtree root is no longer reachable
        // via parents
        listener2.clearEnteredNodes();
        System.gc();
        binding.dispose();
        context.eval(source);
        for (Node enteredNode : enteredStatementNodes) {
            assertFalse(enteredNode.getParent() instanceof InstrumentableNode.WrapperNode);
        }
        assertEquals("+L+L-L+L-L-L+L-L+L-L+L-L", listener1.getRecording());
    }
}
