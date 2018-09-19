/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.instrumentation.test.InstrumentationEventTest.EventKind.ENTER;
import static com.oracle.truffle.api.instrumentation.test.InstrumentationEventTest.EventKind.INPUT_VALUE;
import static com.oracle.truffle.api.instrumentation.test.InstrumentationEventTest.EventKind.RETURN_VALUE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Source;
import org.junit.Test;

import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.BlockTag;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.ConstantTag;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.ExpressionNode;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.MaterializeChildExpressionNode;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.MaterializedChildExpressionNode;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

public class InstrumentableNodeTest extends InstrumentationEventTest {

    /*
     * Directly instrument and materialize all nodes.
     */
    @Test
    public void testSimpleMaterializeSyntax() {
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class, StandardTags.ExpressionTag.class).build();
        instrumenter.attachExecutionEventFactory(filter, null, factory);
        execute("MATERIALIZE_CHILD_EXPRESSION");
        assertOn(ENTER, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof MaterializedChildExpressionNode);
        });
        assertOn(ENTER, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof ExpressionNode);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof ExpressionNode);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof MaterializedChildExpressionNode);
        });
    }

    /*
     * Directly instrument and materialize all nodes with input values.
     */
    @Test
    public void testSimpleMaterializeSyntaxWithInput() {
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class, StandardTags.ExpressionTag.class).build();
        instrumenter.attachExecutionEventFactory(filter, filter, factory);
        execute("MATERIALIZE_CHILD_EXPRESSION");
        assertOn(ENTER, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof MaterializedChildExpressionNode);
        });
        assertOn(ENTER, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof ExpressionNode);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof ExpressionNode);
        });
        assertOn(INPUT_VALUE, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof MaterializedChildExpressionNode);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof MaterializedChildExpressionNode);
        });
    }

    /*
     * First instrument statements and then instrument expressions to test materialization at
     * locations where there is already a wrapper.
     */
    @Test
    public void testLateMaterializeSyntax() {
        Source source = createSource("MATERIALIZE_CHILD_EXPRESSION");
        SourceSectionFilter filter;

        filter = SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build();
        instrumenter.attachExecutionEventFactory(filter, null, factory);
        execute(source);
        assertOn(ENTER, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof MaterializeChildExpressionNode);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof MaterializeChildExpressionNode);
        });
        assertAllEventsConsumed();
        filter = SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build();
        instrumenter.attachExecutionEventFactory(filter, null, factory);
        execute(source);
        assertOn(ENTER, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof MaterializedChildExpressionNode);
        });
        assertOn(ENTER, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof ExpressionNode);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof ExpressionNode);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof MaterializedChildExpressionNode);
        });

    }

    /*
     * First instrument statements and then instrument expressions to test materialization at
     * locations where there is already a wrapper.
     */
    @Test
    public void testTagIsNot() {
        SourceSectionFilter filter;
        filter = SourceSectionFilter.newBuilder().tagIsNot(StandardTags.RootTag.class).build();
        instrumenter.attachExecutionEventFactory(filter, null, factory);
        execute("MATERIALIZE_CHILD_EXPRESSION");

        assertOn(ENTER, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof MaterializedChildExpressionNode);
        });
        assertOn(ENTER, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof ExpressionNode);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof ExpressionNode);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof MaterializedChildExpressionNode);
        });

    }

    /*
     * Test materialize if the parent node is not instrumented. We need to call materializeSyntax
     * for all visited instrumentable nodes. Not just for instrumented ones.
     */
    @Test
    public void testMaterializeSyntaxNotInstrumented() {
        SourceSectionFilter expressionFilter = SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build();
        instrumenter.attachExecutionEventFactory(expressionFilter, null, factory);
        execute("MATERIALIZE_CHILD_EXPRESSION");
        assertOn(ENTER, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof ExpressionNode);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof ExpressionNode);
        });
    }

    @Test
    public void testGetNodeObject() {
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(ExpressionTag.class, ConstantTag.class).build();
        instrumenter.attachExecutionEventFactory(filter, null, factory);
        execute("EXPRESSION(CONSTANT(42))");
        assertOn(ENTER, (e) -> {
            assertProperties(e.context.getNodeObject(), "simpleName", "ExpressionNode");
        });
        assertOn(ENTER, (e) -> {
            assertProperties(e.context.getNodeObject(), "simpleName", "ConstantNode", "constant", 42);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertProperties(e.context.getNodeObject(), "simpleName", "ConstantNode", "constant", 42);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertProperties(e.context.getNodeObject(), "simpleName", "ExpressionNode");
        });
    }

    private static void assertProperties(Object receiver, Object... properties) {
        try {
            assertTrue(receiver instanceof TruffleObject);
            TruffleObject obj = (TruffleObject) receiver;

            Node hasKeysNode = Message.HAS_KEYS.createNode();
            Node keysNode = Message.KEYS.createNode();
            assertTrue(ForeignAccess.sendHasKeys(hasKeysNode, obj));
            TruffleObject keys = ForeignAccess.sendKeys(keysNode, obj);

            for (int i = 0; i < properties.length; i = i + 2) {
                String expectedKey = (String) properties[i];
                Object expectedValue = properties[i + 1];
                Node readNode = Message.READ.createNode();
                Object key = ForeignAccess.sendRead(readNode, keys, i / 2);
                assertEquals(expectedKey, key);
                assertEquals(expectedValue, ForeignAccess.sendRead(readNode, obj, key));
            }
        } catch (InteropException e) {
            throw e.raise();
        }

    }

    @Test
    public void testNoSourceSectionWithFilter() {
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(BlockTag.class).lineIn(1, 1).build();
        instrumenter.attachExecutionEventFactory(filter, null, factory);
        execute("BLOCK_NO_SOURCE_SECTION(BLOCK())");
        assertOn(ENTER, (e) -> {
            assertProperties(e.context.getNodeObject(), "simpleName", "BlockNode");
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertProperties(e.context.getNodeObject(), "simpleName", "BlockNode");
        });
    }

    @Test
    public void testNoSourceSectionNoFilter() {
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(BlockTag.class).build();
        instrumenter.attachExecutionEventFactory(filter, null, factory);
        execute("BLOCK_NO_SOURCE_SECTION(BLOCK())");
        assertOn(ENTER, (e) -> {
            assertProperties(e.context.getNodeObject(), "simpleName", "BlockNoSourceSectionNode");
        });
        assertOn(ENTER, (e) -> {
            assertProperties(e.context.getNodeObject(), "simpleName", "BlockNode");
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertProperties(e.context.getNodeObject(), "simpleName", "BlockNode");
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertProperties(e.context.getNodeObject(), "simpleName", "BlockNoSourceSectionNode");
        });
    }

}
