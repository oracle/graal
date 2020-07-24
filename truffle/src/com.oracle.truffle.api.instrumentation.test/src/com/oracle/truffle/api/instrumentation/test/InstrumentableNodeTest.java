/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.BlockTag;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.ConstantTag;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.ExpressionNode;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.MaterializeChildExpressionNode;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.MaterializedChildExpressionNode;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

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

            InteropLibrary interop = InteropLibrary.getFactory().getUncached();

            assertTrue(interop.hasMembers(obj));
            Object keys = interop.getMembers(obj);

            for (int i = 0; i < properties.length; i = i + 2) {
                String expectedKey = (String) properties[i];
                Object expectedValue = properties[i + 1];
                Object key = interop.readArrayElement(keys, i / 2);
                assertEquals(expectedKey, key);
                assertEquals(expectedValue, interop.readMember(obj, interop.asString(key)));
            }
        } catch (InteropException e) {
            throw new AssertionError(e);
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

    @Test
    public void testMaterializationCount() {
        Engine engine = Engine.create();
        Instrument instr = engine.getInstruments().get(GetEnvInstrument.ID);
        GetEnvInstrument getEnvInstrument = instr.lookup(GetEnvInstrument.class);
        Context ctx = Context.newBuilder().engine(engine).build();
        Source source = Source.create(MaterializationLanguage.ID, "test");
        // Run a source that creates nodes which are instrumented later on from the instrumentation
        // thread:
        ctx.eval(source);
        int[] numInstrumentations = new int[]{0};
        getEnvInstrument.instrumentEnv.getInstrumenter().attachExecutionEventFactory(SourceSectionFilter.ANY, new ExecutionEventNodeFactory() {
            @Override
            public ExecutionEventNode create(EventContext ec) {
                numInstrumentations[0]++;
                return new ExecutionEventNode() {
                };
            }
        });
        int expectedNumNodes = ((int) Math.pow(MaterializationLanguage.NUM_CHILDREN, MaterializationLanguage.DEPTH + 1) - 1) / (MaterializationLanguage.NUM_CHILDREN - 1) - 1;
        long numMaterializations = ctx.eval(Source.create(MaterializationLanguage.ID, "numMaterializations")).asLong();
        assertEquals(expectedNumNodes, numMaterializations);
        ctx.eval(source); // To create instrumentation nodes
        assertEquals(expectedNumNodes, numInstrumentations[0]);
        numMaterializations = ctx.eval(Source.create(MaterializationLanguage.ID, "numMaterializations")).asLong();
        long numMultipleMaterializations = ctx.eval(Source.create(MaterializationLanguage.ID, "numMultipleMaterializations")).asLong();
        assertEquals(0, numMultipleMaterializations); // No multiple materializations
        assertEquals(expectedNumNodes, numMaterializations); // No new materializations
    }

    @TruffleInstrument.Registration(id = GetEnvInstrument.ID, services = GetEnvInstrument.class)
    public static class GetEnvInstrument extends TruffleInstrument {

        static final String ID = "test-materialization-get-env";
        TruffleInstrument.Env instrumentEnv;

        @Override
        protected void onCreate(TruffleInstrument.Env env) {
            env.registerService(this);
            this.instrumentEnv = env;
        }

    }

    @TruffleLanguage.Registration(id = MaterializationLanguage.ID, name = "Materialization Test Language", version = "1.0")
    @ProvidedTags({StandardTags.RootTag.class, StandardTags.StatementTag.class})
    public static class MaterializationLanguage extends ProxyLanguage {

        static final String ID = "truffle-materialization-test-language";
        static final int NUM_CHILDREN = 4;
        static final int DEPTH = 6;

        private int numMaterializations;
        private int numMultipleMaterializations;
        private final List<CallTarget> targets = new LinkedList<>(); // To prevent from GC

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            com.oracle.truffle.api.source.Source source = request.getSource();
            String code = source.getCharacters().toString();
            CallTarget target = Truffle.getRuntime().createCallTarget(new RootNode(this) {

                @Node.Children private MaterializableNode[] children = code.startsWith("num") ? new MaterializableNode[]{} : createChildren(MaterializationLanguage.this, source, 1);

                @Override
                @ExplodeLoop
                public Object execute(VirtualFrame frame) {
                    if (code.equals("numMaterializations")) {
                        return numMaterializations;
                    }
                    if (code.equals("numMultipleMaterializations")) {
                        return numMultipleMaterializations;
                    }
                    int sum = 0;
                    for (MaterializableNode node : children) {
                        sum += node.execute(frame);
                    }
                    return sum;
                }

                @Override
                public SourceSection getSourceSection() {
                    return source.createSection(1);
                }

            });
            targets.add(target);
            return target;
        }

        private static MaterializableNode[] createChildren(MaterializationLanguage language, com.oracle.truffle.api.source.Source source, int depth) {
            if (depth > DEPTH) {
                return new MaterializableNode[]{};
            }
            MaterializableNode[] children = new MaterializableNode[NUM_CHILDREN];
            for (int i = 0; i < NUM_CHILDREN; i++) {
                children[i] = new MaterializableNode(language, source, depth + 1);
            }
            return children;
        }

        @GenerateWrapper
        static class MaterializableNode extends Node implements InstrumentableNode {

            private final MaterializationLanguage language;
            private final SourceSection sourceSection;
            private final int depth;
            private boolean materialized;
            @Node.Children protected MaterializableNode[] children;

            MaterializableNode(MaterializationLanguage language, com.oracle.truffle.api.source.Source source, int depth) {
                this.language = language;
                this.depth = depth;
                this.sourceSection = source.createSection(1);
                children = createChildren(language, source, depth);
            }

            MaterializableNode(MaterializableNode copy) {
                this(copy, createChildren(copy.language, copy.sourceSection.getSource(), copy.depth));
            }

            MaterializableNode(MaterializableNode copy, MaterializableNode[] children) {
                this.language = copy.language;
                this.depth = copy.depth;
                this.materialized = copy.materialized;
                this.sourceSection = copy.sourceSection;
                this.children = children;
            }

            protected MaterializableNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
                if (this instanceof InstrumentableNode.WrapperNode) {
                    InstrumentableNode.WrapperNode wrapperNode = (InstrumentableNode.WrapperNode) this;
                    return cloneUninitialized((MaterializableNode) wrapperNode.getDelegateNode(), materializedTags);
                }

                return new MaterializableNode(this, cloneUninitialized(children, materializedTags));
            }

            @SuppressWarnings("unchecked")
            public static <T extends MaterializableNode> T cloneUninitialized(T node, Set<Class<? extends Tag>> materializedTags) {
                if (node == null) {
                    return null;
                } else {
                    T copy = node;
                    if (node.isInstrumentable()) {
                        copy = (T) node.materializeInstrumentableNodes(materializedTags);
                    }
                    if (node == copy) {
                        copy = (T) node.copyUninitialized(materializedTags);
                    }
                    return copy;
                }
            }

            public static <T extends MaterializableNode> T[] cloneUninitialized(T[] nodeArray, Set<Class<? extends Tag>> materializedTags) {
                if (nodeArray == null) {
                    return null;
                } else {
                    T[] copy = nodeArray.clone();
                    for (int i = 0; i < copy.length; i++) {
                        copy[i] = cloneUninitialized(copy[i], materializedTags);
                    }
                    return copy;
                }
            }

            @Override
            public boolean isInstrumentable() {
                return true;
            }

            @Override
            public WrapperNode createWrapper(ProbeNode probe) {
                return new MaterializableNodeWrapper(this, this, probe);
            }

            @Override
            public boolean hasTag(Class<? extends Tag> tag) {
                return tag.equals(StandardTags.StatementTag.class);
            }

            @Override
            public SourceSection getSourceSection() {
                return sourceSection;
            }

            @ExplodeLoop
            public int execute(@SuppressWarnings("unused") VirtualFrame frame) {
                int sum = 0;
                for (MaterializableNode node : children) {
                    sum += node.execute(frame);
                }
                return sum + 1;
            }

            @Override
            public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
                if (materialized) {
                    language.numMultipleMaterializations++;
                }
                materialized = true;
                language.numMaterializations++;
                return new MaterializedNode(this, cloneUninitialized(children, materializedTags));
            }
        }

        @GenerateWrapper
        static class MaterializedNode extends MaterializableNode {

            MaterializedNode(MaterializedNode copy) {
                super(copy);
            }

            MaterializedNode(MaterializableNode copy, MaterializableNode[] children) {
                super(copy, children);
            }

            @Override
            protected MaterializableNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
                return new MaterializedNode(this, cloneUninitialized(children, materializedTags));
            }

            @Override
            public WrapperNode createWrapper(ProbeNode probe) {
                return new MaterializedNodeWrapper(this, this, probe);
            }

            @Override
            public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
                return this;
            }
        }

    }
}
