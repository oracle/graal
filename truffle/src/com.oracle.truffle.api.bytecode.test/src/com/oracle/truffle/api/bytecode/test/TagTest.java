/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.test;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.EpilogExceptional;
import com.oracle.truffle.api.bytecode.EpilogReturn;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.Prolog;
import com.oracle.truffle.api.bytecode.introspection.TagTree;
import com.oracle.truffle.api.bytecode.test.error_tests.ExpectError;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootBodyTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.Node;

public class TagTest extends AbstractInstructionTest {

    private static TagInstrumentationTestRootNode parse(BytecodeParser<TagInstrumentationTestRootNodeGen.Builder> parser) {
        BytecodeRootNodes<TagInstrumentationTestRootNode> nodes = TagInstrumentationTestRootNodeGen.create(BytecodeConfig.DEFAULT, parser);
        TagInstrumentationTestRootNode root = nodes.getNodes().get(nodes.getNodes().size() - 1);
        return root;
    }

    private static TagInstrumentationTestWithPrologRootNode parseProlog(BytecodeParser<TagInstrumentationTestWithPrologRootNodeGen.Builder> parser) {
        BytecodeRootNodes<TagInstrumentationTestWithPrologRootNode> nodes = TagInstrumentationTestWithPrologRootNodeGen.create(BytecodeConfig.DEFAULT, parser);
        TagInstrumentationTestWithPrologRootNode root = nodes.getNodes().get(nodes.getNodes().size() - 1);
        return root;
    }

    Context context;
    Instrumenter instrumenter;

    @Before
    public void setup() {
        context = Context.create(TagTestLanguage.ID);
        context.initialize(TagTestLanguage.ID);
        context.enter();
        instrumenter = context.getEngine().getInstruments().get(TagTestInstrumentation.ID).lookup(Instrumenter.class);
    }

    @After
    public void tearDown() {
        context.close();
    }

    enum EventKind {
        ENTER,
        RETURN_VALUE
    }

    @SuppressWarnings("unchecked")
    record Event(int id, EventKind kind, int startBci, int endBci, Object value, Class<?>... tags) {
        Event(EventKind kind, int startBci, int endBci, Object value, Class<?>... tags) {
            this(-1, kind, startBci, endBci, value, tags);
        }
    }

    private List<Event> attachEventListener(SourceSectionFilter filter) {
        List<Event> events = new ArrayList<>();
        instrumenter.attachExecutionEventFactory(filter, (e) -> {
            TagTree tree = (TagTree) e.getInstrumentedNode();
            return new ExecutionEventNode() {

                @Override
                public void onEnter(VirtualFrame f) {
                    events.add(new Event(TagTestLanguage.REF.get(this).threadLocal.get().newEvent(), EventKind.ENTER, tree.getStartBci(), tree.getEndBci(), null,
                                    tree.getTags().toArray(Class[]::new)));
                }

                @Override
                public void onReturnValue(VirtualFrame f, Object arg) {
                    events.add(new Event(TagTestLanguage.REF.get(this).threadLocal.get().newEvent(), EventKind.RETURN_VALUE, tree.getStartBci(), tree.getEndBci(), arg,
                                    tree.getTags().toArray(Class[]::new)));
                }
            };
        });
        return events;
    }

    @Test
    public void testStatementsCached() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot(TagTestLanguage.REF.get(null));

            var local = b.createLocal();
            b.beginBlock();

            b.beginTag(StatementTag.class);
            b.beginStoreLocal(local);
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(42);
            b.endTag(ExpressionTag.class);
            b.endStoreLocal();
            b.endTag(StatementTag.class);

            b.beginTag(StatementTag.class, ExpressionTag.class);
            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.emitLoadLocal(local);
            b.endTag(ExpressionTag.class);
            b.endReturn();
            b.endTag(StatementTag.class, ExpressionTag.class);

            b.endBlock();

            b.endRoot();
        });
        node.getBytecodeNode().setUncachedThreshold(0);

        assertInstructions(node,
                        "load.constant",
                        "store.local",
                        "load.local",
                        "return");
        assertEquals(42, node.getCallTarget().call());
        assertQuickenings(node, 3, 2);

        assertInstructions(node,
                        "load.constant$Int",
                        "store.local$Int$unboxed",
                        "load.local$Int",
                        "return");

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build());

        assertInstructions(node,
                        "tag.enter",
                        "load.constant",
                        "store.local",
                        "tag.leaveVoid",
                        "tag.enter",
                        "load.local",
                        "tag.leave",
                        "return");

        assertEquals(42, node.getCallTarget().call());

        assertInstructions(node,
                        "tag.enter",
                        "load.constant$Int",
                        "store.local$Int$unboxed",
                        "tag.leaveVoid",
                        "tag.enter",
                        "load.local$Int$unboxed",
                        "tag.leave$Int",
                        "return");

        QuickeningCounts counts = assertQuickenings(node, 8, 4);

        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x0007, null, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x0007, null, StatementTag.class),
                        new Event(EventKind.ENTER, 0x0009, 0x0011, null, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0009, 0x00011, 42, StatementTag.class));

        assertStable(counts, node);

    }

    @Test
    public void testStatementsUncached() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot(TagTestLanguage.REF.get(null));

            var local = b.createLocal();
            b.beginBlock();

            b.beginTag(StatementTag.class);
            b.beginStoreLocal(local);
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(42);
            b.endTag(ExpressionTag.class);
            b.endStoreLocal();
            b.endTag(StatementTag.class);

            b.beginTag(StatementTag.class, ExpressionTag.class);
            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.emitLoadLocal(local);
            b.endTag(ExpressionTag.class);
            b.endReturn();
            b.endTag(StatementTag.class, ExpressionTag.class);

            b.endBlock();

            b.endRoot();
        });
        node.getBytecodeNode().setUncachedThreshold(Integer.MAX_VALUE);

        assertInstructions(node,
                        "load.constant",
                        "store.local",
                        "load.local",
                        "return");
        assertEquals(42, node.getCallTarget().call());
        assertQuickenings(node, 0, 0);

        assertInstructions(node,
                        "load.constant",
                        "store.local",
                        "load.local",
                        "return");

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build());

        assertInstructions(node,
                        "tag.enter",
                        "load.constant",
                        "store.local",
                        "tag.leaveVoid",
                        "tag.enter",
                        "load.local",
                        "tag.leave",
                        "return");

        assertEquals(42, node.getCallTarget().call());

        assertInstructions(node,
                        "tag.enter",
                        "load.constant",
                        "store.local",
                        "tag.leaveVoid",
                        "tag.enter",
                        "load.local",
                        "tag.leave",
                        "return");

        QuickeningCounts counts = assertQuickenings(node, 0, 0);

        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x0007, null, StatementTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x0007, null, StatementTag.class),
                        new Event(EventKind.ENTER, 0x0009, 0x0011, null, StatementTag.class), new Event(EventKind.RETURN_VALUE, 0x0009, 0x0011, 42, StatementTag.class));

        assertStable(counts, node);
    }

    private static void assertEvents(BytecodeRootNode node, List<Event> actualEvents, Event... expectedEvents) {
        try {
            assertEquals("expectedEvents: " + Arrays.toString(expectedEvents) + " actualEvents:" + actualEvents, expectedEvents.length, actualEvents.size());

            for (int i = 0; i < expectedEvents.length; i++) {
                Event actualEvent = actualEvents.get(i);
                Event expectedEvent = expectedEvents[i];

                assertEquals("event kind at at index " + i, expectedEvent.kind, actualEvent.kind);
                assertEquals("event value at at index " + i, expectedEvent.value, actualEvent.value);
                assertEquals("start bci at at index " + i, "0x" + Integer.toHexString(expectedEvent.startBci), "0x" + Integer.toHexString(actualEvent.startBci));
                assertEquals("end bci at at index " + i, "0x" + Integer.toHexString(expectedEvent.endBci), "0x" + Integer.toHexString(actualEvent.endBci));
                assertEquals("end bci at at index " + i, Set.of(expectedEvent.tags), Set.of(actualEvent.tags));

                if (expectedEvent.id != -1) {
                    assertEquals("event id at at index " + i, expectedEvent.id, actualEvent.id);
                }
            }
        } catch (AssertionError e) {
            System.out.println(node.dump());
            throw e;
        }
    }

    @Test
    public void testStatementsAndExpressionUncached() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot(TagTestLanguage.REF.get(null));

            var local = b.createLocal();
            b.beginBlock();

            b.beginTag(StatementTag.class);
            b.beginStoreLocal(local);
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(42);
            b.endTag(ExpressionTag.class);
            b.endStoreLocal();
            b.endTag(StatementTag.class);

            b.beginTag(StatementTag.class, ExpressionTag.class);
            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.emitLoadLocal(local);
            b.endTag(ExpressionTag.class);
            b.endReturn();
            b.endTag(StatementTag.class, ExpressionTag.class);

            b.endBlock();

            b.endRoot();
        });
        node.getBytecodeNode().setUncachedThreshold(Integer.MAX_VALUE);

        assertInstructions(node,
                        "load.constant",
                        "store.local",
                        "load.local",
                        "return");
        assertEquals(42, node.getCallTarget().call());
        assertQuickenings(node, 0, 0);

        assertInstructions(node,
                        "load.constant",
                        "store.local",
                        "load.local",
                        "return");

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class, StandardTags.ExpressionTag.class).build());

        assertInstructions(node,
                        "tag.enter",
                        "tag.enter",
                        "load.constant",
                        "tag.leave",
                        "store.local",
                        "tag.leaveVoid",
                        "tag.enter",
                        "tag.enter",
                        "load.local",
                        "tag.leave",
                        "tag.leave",
                        "return");

        assertEquals(42, node.getCallTarget().call());
        assertInstructions(node,
                        "tag.enter",
                        "tag.enter",
                        "load.constant",
                        "tag.leave",
                        "store.local",
                        "tag.leaveVoid",
                        "tag.enter",
                        "tag.enter",
                        "load.local",
                        "tag.leave",
                        "tag.leave",
                        "return");

        QuickeningCounts counts = assertQuickenings(node, 0, 0);

        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x000C, null, StatementTag.class),
                        new Event(EventKind.ENTER, 0x0002, 0x0006, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0002, 0x0006, 42, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x000C, null, StatementTag.class),
                        new Event(EventKind.ENTER, 0x000e, 0x001b, null, StatementTag.class, ExpressionTag.class),
                        new Event(EventKind.ENTER, 0x0010, 0x0014, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0010, 0x0014, 42, ExpressionTag.class), new Event(EventKind.RETURN_VALUE, 0x000e, 0x001b, 42, StatementTag.class, ExpressionTag.class));

        assertStable(counts, node);
    }

    @Test
    public void testImplicitRootTagsNoProlog() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot(TagTestLanguage.REF.get(null));
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endRoot();
        });
        assertEquals(42, node.getCallTarget().call());

        assertInstructions(node,
                        "load.constant",
                        "return");

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootBodyTag.class, StandardTags.RootTag.class).build());
        assertInstructions(node,
                        "tag.enter",
                        "load.constant",
                        "tag.leave",
                        "return");

        assertEquals(42, node.getCallTarget().call());
        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x0008, null, RootTag.class, RootBodyTag.class), new Event(EventKind.RETURN_VALUE, 0x0000, 0x0008, 42, RootTag.class, RootBodyTag.class));

    }

    @Test
    public void testImplicitRootBodyTagNoProlog() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot(TagTestLanguage.REF.get(null));
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endRoot();
        });
        assertEquals(42, node.getCallTarget().call());

        assertInstructions(node,
                        "load.constant",
                        "return");

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootBodyTag.class).build());
        assertInstructions(node,
                        "tag.enter",
                        "load.constant",
                        "tag.leave",
                        "return");

        assertEquals(42, node.getCallTarget().call());

        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x0008, null, RootBodyTag.class), new Event(EventKind.RETURN_VALUE, 0x0000, 0x0008, 42, RootBodyTag.class));

    }

    @Test
    public void testImplicitRootTagNoProlog() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot(TagTestLanguage.REF.get(null));
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endRoot();
        });
        assertEquals(42, node.getCallTarget().call());

        assertInstructions(node,
                        "load.constant",
                        "return");

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class).build());
        assertInstructions(node,
                        "tag.enter",
                        "load.constant",
                        "tag.leave",
                        "return");

        assertEquals(42, node.getCallTarget().call());

        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x0008, null, RootTag.class), new Event(EventKind.RETURN_VALUE, 0x0000, 0x0008, 42, RootTag.class));

    }

    @Test
    public void testImplicitRootTagProlog() {
        TagInstrumentationTestWithPrologRootNode node = parseProlog((b) -> {
            b.beginRoot(TagTestLanguage.REF.get(null));
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endRoot();
        });
        ThreadLocalData tl = TagTestLanguage.getThreadData(null);
        tl.trackProlog = true;

        assertEquals(42, node.getCallTarget().call());

        assertInstructions(node,
                        "c.EnterMethod",
                        "load.constant",
                        "c.LeaveValue",
                        "return",
                        "load.local",
                        "c.LeaveExceptional",
                        "throw");

        assertEquals(0, tl.prologIndex);
        assertEquals(1, tl.epilogValue);
        assertEquals(42, tl.epilogValueObject);
        assertEquals(-1, tl.epilogExceptional);
        tl.reset();

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class).build());
        assertInstructions(node,
                        "tag.enter",
                        "c.EnterMethod",
                        "load.constant",
                        "c.LeaveValue",
                        "tag.leave",
                        "return",
                        "load.local",
                        "c.LeaveExceptional",
                        "throw");

        assertEquals(42, node.getCallTarget().call());

        assertEquals(1, tl.prologIndex);
        assertEquals(2, tl.epilogValue);
        assertEquals(42, tl.epilogValueObject);
        assertEquals(-1, tl.epilogExceptional);

        assertEvents(node,
                        events,
                        new Event(0, EventKind.ENTER, 0x0000, 0x0013, null, RootTag.class),
                        new Event(3, EventKind.RETURN_VALUE, 0x0000, 0x0013, 42, RootTag.class));

    }

    @Test
    public void testImplicitRootBodyTagProlog() {
        TagInstrumentationTestWithPrologRootNode node = parseProlog((b) -> {
            b.beginRoot(TagTestLanguage.REF.get(null));
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endRoot();
        });
        ThreadLocalData tl = TagTestLanguage.getThreadData(null);
        tl.trackProlog = true;

        assertEquals(42, node.getCallTarget().call());

        assertInstructions(node,
                        "c.EnterMethod",
                        "load.constant",
                        "c.LeaveValue",
                        "return",
                        "load.local",
                        "c.LeaveExceptional",
                        "throw");

        assertEquals(0, tl.prologIndex);
        assertEquals(1, tl.epilogValue);
        assertEquals(42, tl.epilogValueObject);
        assertEquals(-1, tl.epilogExceptional);
        tl.reset();

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootBodyTag.class).build());
        assertInstructions(node,
                        "c.EnterMethod",
                        "tag.enter",
                        "load.constant",
                        "tag.leave",
                        "c.LeaveValue",
                        "return",
                        "load.local",
                        "c.LeaveExceptional",
                        "throw");

        assertEquals(42, node.getCallTarget().call());

        assertEquals(0, tl.prologIndex);
        assertEquals(3, tl.epilogValue);
        assertEquals(42, tl.epilogValueObject);
        assertEquals(-1, tl.epilogExceptional);

        assertEvents(node,
                        events,
                        new Event(1, EventKind.ENTER, 0x0002, 0x000d, null, RootBodyTag.class), new Event(2, EventKind.RETURN_VALUE, 0x0002, 0x000d, 42, RootBodyTag.class));

    }

    @Test
    public void testImplicitRootTagsProlog() {
        TagInstrumentationTestWithPrologRootNode node = parseProlog((b) -> {
            b.beginRoot(TagTestLanguage.REF.get(null));
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endRoot();
        });
        ThreadLocalData tl = TagTestLanguage.getThreadData(null);
        tl.trackProlog = true;

        assertEquals(42, node.getCallTarget().call());
        assertInstructions(node,
                        "c.EnterMethod",
                        "load.constant",
                        "c.LeaveValue",
                        "return",
                        "load.local",
                        "c.LeaveExceptional",
                        "throw");

        assertEquals(0, tl.prologIndex);
        assertEquals(1, tl.epilogValue);
        assertEquals(42, tl.epilogValueObject);
        assertEquals(-1, tl.epilogExceptional);
        tl.reset();

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootBodyTag.class, StandardTags.RootTag.class).build());
        assertInstructions(node,
                        "tag.enter",
                        "c.EnterMethod",
                        "tag.enter",
                        "load.constant",
                        "tag.leave",
                        "c.LeaveValue",
                        "tag.leave",
                        "return",
                        "load.local",
                        "c.LeaveExceptional",
                        "throw");

        assertEquals(42, node.getCallTarget().call());

        assertEquals(1, tl.prologIndex);
        assertEquals(4, tl.epilogValue);
        assertEquals(42, tl.epilogValueObject);
        assertEquals(-1, tl.epilogExceptional);

        assertEvents(node,
                        events,
                        new Event(0, EventKind.ENTER, 0x0000, 0x0018, null, RootTag.class),
                        new Event(2, EventKind.ENTER, 0x0004, 0x0012, null, RootBodyTag.class),
                        new Event(3, EventKind.RETURN_VALUE, 0x0004, 0x0012, 42, RootBodyTag.class),
                        new Event(5, EventKind.RETURN_VALUE, 0x0000, 0x0018, 42, RootTag.class));

    }

    /*
     * Tests that return reachability optimization does not eliminate instrutions if there is a
     * jump.
     */
    @Test
    public void testImplicitJumpAfterReturn() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot(TagTestLanguage.REF.get(null));

            var l = b.createLabel();

            b.beginTag(ExpressionTag.class);
            b.emitBranch(l);
            b.endTag(ExpressionTag.class);
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();

            b.emitLabel(l);

            b.endRoot();
        });

        assertInstructions(node,
                        "branch",
                        "trap");

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class, StandardTags.ExpressionTag.class).build());
        assertFails(() -> node.getCallTarget().call(), AssertionError.class, (e) -> {
            assertEquals("Control reached past the end of the bytecode.", e.getMessage());
        });

        assertInstructions(node,
                        "tag.enter",
                        "tag.enter",
                        "tag.leaveVoid",
                        "branch",
                        "tag.leaveVoid",
                        "trap");

        // instrumentation events should be correct even if we hit a trap
        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x0008, null, RootTag.class),
                        new Event(EventKind.ENTER, 0x0002, 0x0008, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0002, 0x0008, null, ExpressionTag.class), new Event(EventKind.RETURN_VALUE, 0x0000, 0x0008, null, RootTag.class));

    }

    @Test
    public void testImplicitJump() {
        TagInstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot(TagTestLanguage.REF.get(null));

            var l = b.createLabel();

            b.beginTag(ExpressionTag.class);
            b.emitBranch(l);
            b.endTag(ExpressionTag.class);

            b.emitLabel(l);

            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();

            b.endRoot();
        });

        assertInstructions(node,
                        "branch",
                        "load.constant",
                        "return");

        assertEquals(42, node.getCallTarget().call());

        List<Event> events = attachEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class,
                        StandardTags.ExpressionTag.class).build());

        assertInstructions(node,
                        "tag.enter",
                        "tag.enter",
                        "tag.leaveVoid",
                        "branch",
                        "load.constant",
                        "tag.leave",
                        "return");

        assertEquals(42, node.getCallTarget().call());

        assertEvents(node,
                        events,
                        new Event(EventKind.ENTER, 0x0000, 0x000e, null, RootTag.class),
                        new Event(EventKind.ENTER, 0x0002, 0x0008, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0002, 0x0008, null, ExpressionTag.class),
                        new Event(EventKind.RETURN_VALUE, 0x0000, 0x000e, 42, RootTag.class));

    }

    @GenerateBytecode(languageClass = TagTestLanguage.class, //
                    enableQuickening = true, //
                    enableUncachedInterpreter = true,  //
                    enableTagInstrumentation = true, //
                    enableSerialization = true, boxingEliminationTypes = {int.class})
    public abstract static class TagInstrumentationTestRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected TagInstrumentationTestRootNode(TruffleLanguage<?> language,
                        FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Add {
            @Specialization
            public static int doInt(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class IsNot {
            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand != value;
            }
        }

        @Operation
        static final class Is {

            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand == value;
            }
        }

    }

    @GenerateBytecode(languageClass = TagTestLanguage.class, //
                    enableQuickening = true, //
                    enableUncachedInterpreter = true,  //
                    enableTagInstrumentation = true, //
                    enableSerialization = true, boxingEliminationTypes = {int.class})
    public abstract static class TagInstrumentationTestWithPrologRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected TagInstrumentationTestWithPrologRootNode(TruffleLanguage<?> language,
                        FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Prolog
        static final class EnterMethod {
            @Specialization
            public static void doDefault(@Bind("$node") Node node) {
                TagTestLanguage.getThreadData(node).notifyProlog();
            }
        }

        @EpilogExceptional
        static final class LeaveExceptional {
            @Specialization
            public static void doDefault(@SuppressWarnings("unused") AbstractTruffleException t, @Bind("$node") Node node) {
                TagTestLanguage.getThreadData(node).notifyEpilogExceptional();
            }
        }

        @EpilogReturn
        static final class LeaveValue {
            @Specialization
            public static int doDefault(int a, @Bind("$node") Node node) {
                TagTestLanguage.getThreadData(node).notifyEpilogValue(a);
                return a;
            }
        }

        @Operation
        static final class Add {
            @Specialization
            public static int doInt(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class IsNot {
            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand != value;
            }
        }

        @Operation
        static final class Is {

            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand == value;
            }
        }

    }

    @TruffleInstrument.Registration(id = TagTestInstrumentation.ID, services = Instrumenter.class)
    public static class TagTestInstrumentation extends TruffleInstrument {

        public static final String ID = "bytecode_TagTestInstrument";

        @Override
        protected void onCreate(Env env) {
            env.registerService(env.getInstrumenter());
        }
    }

    static class ThreadLocalData {

        private final AtomicInteger eventCount = new AtomicInteger(0);

        public int newEvent() {
            return eventCount.getAndIncrement();
        }

        boolean trackProlog;

        int prologIndex = -1;
        int epilogValue = -1;
        Object epilogValueObject;
        int epilogExceptional = -1;

        public void reset() {
            prologIndex = -1;
            epilogValue = -1;
            epilogExceptional = -1;
            eventCount.set(0);
            epilogValueObject = null;
        }

        public void notifyProlog() {
            if (!trackProlog) {
                return;
            }
            if (prologIndex != -1) {
                throw new AssertionError("already executed");
            }
            prologIndex = newEvent();
        }

        public void notifyEpilogValue(int a) {
            if (!trackProlog) {
                return;
            }
            if (epilogValue != -1) {
                throw new AssertionError("already executed");
            }
            epilogValue = newEvent();
            epilogValueObject = a;
        }

        public void notifyEpilogExceptional() {
            if (!trackProlog) {
                return;
            }
            if (epilogExceptional != -1) {
                throw new AssertionError("already executed");
            }
            epilogExceptional = newEvent();
        }

    }

    @TruffleLanguage.Registration(id = TagTestLanguage.ID)
    @ProvidedTags({StandardTags.RootBodyTag.class, StandardTags.ExpressionTag.class, StandardTags.StatementTag.class, StandardTags.RootTag.class})
    public static class TagTestLanguage extends TruffleLanguage<Object> {

        public static final String ID = "bytecode_TagTestLanguage";

        final ContextThreadLocal<ThreadLocalData> threadLocal = this.locals.createContextThreadLocal((c, t) -> new ThreadLocalData());

        @Override
        protected Object createContext(Env env) {
            return new Object();
        }

        static final LanguageReference<TagTestLanguage> REF = LanguageReference.create(TagTestLanguage.class);

        static ThreadLocalData getThreadData(Node node) {
            return TagTestLanguage.REF.get(node).threadLocal.get();
        }

    }

    @TruffleLanguage.Registration(id = NoRootTagTestLanguage.ID)
    @ProvidedTags({RootBodyTag.class, ExpressionTag.class})
    public static class NoRootTagTestLanguage extends TruffleLanguage<Object> {

        public static final String ID = "bytecode_NoRootTagTestLanguage";

        @Override
        protected Object createContext(Env env) {
            return new Object();
        }

    }

    @ExpectError("Tag instrumentation uses implicit root tagging, but the RootTag was not provded by the language class 'com.oracle.truffle.api.bytecode.test.TagTest.NoRootTagTestLanguage'. " +
                    "Specify the tag using @ProvidedTags(RootTag.class) on the language class or explicitly disable root tagging using @GenerateBytecode(.., enableRootTagging=false) to resolve this.")
    @GenerateBytecode(languageClass = NoRootTagTestLanguage.class, //
                    enableTagInstrumentation = true)
    public abstract static class ErrorNoRootTag extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected ErrorNoRootTag(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Is {

            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand == value;
            }
        }

    }

    @GenerateBytecode(languageClass = NoRootTagTestLanguage.class, //
                    enableTagInstrumentation = true, enableRootTagging = false)
    public abstract static class NoRootTagNoError extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected NoRootTagNoError(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Is {

            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand == value;
            }
        }

    }

    @TruffleLanguage.Registration(id = NoRootBodyTagTestLanguage.ID)
    @ProvidedTags({RootTag.class, ExpressionTag.class})
    public static class NoRootBodyTagTestLanguage extends TruffleLanguage<Object> {

        public static final String ID = "bytecode_NoRootBodyTagTestLanguage";

        @Override
        protected Object createContext(Env env) {
            return new Object();
        }

    }

    @ExpectError("Tag instrumentation uses implicit root body tagging, but the RootTag was not provded by the language class 'com.oracle.truffle.api.bytecode.test.TagTest.NoRootBodyTagTestLanguage'. " +
                    "Specify the tag using @ProvidedTags(RootBodyTag.class) on the language class or explicitly disable root tagging using @GenerateBytecode(.., enableRootBodyTagging=false) to resolve this.")
    @GenerateBytecode(languageClass = NoRootBodyTagTestLanguage.class, //
                    enableTagInstrumentation = true)
    public abstract static class ErrorNoRootBodyTag extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected ErrorNoRootBodyTag(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Is {

            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand == value;
            }
        }

    }

    @GenerateBytecode(languageClass = NoRootBodyTagTestLanguage.class, //
                    enableTagInstrumentation = true, enableRootBodyTagging = false)
    public abstract static class NoRootBodyTagNoError extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected NoRootBodyTagNoError(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Is {

            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand == value;
            }
        }

    }

}
