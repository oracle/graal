/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.IndexRange;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.ReflectionUtils;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

public class SourceSectionFilterTest extends AbstractPolyglotTest {

    private static final int ROOT_NODE_BITS_UNINITIALIZED = 0;
    private static final int ROOT_NODE_BITS_SAME_SOURCE = 1 << 1;
    private static final int ROOT_NODE_BITS_NO_SOURCE_SECTION = 1 << 2;
    private static final int ROOT_NODE_BITS_SOURCE_SECTION_HIERARCHICAL = 1 << 3;

    private static final int LINE_LENGTH = 6;

    private static final Set<Class<?>> ALL_TAGS = new HashSet<>(Arrays.asList(InstrumentationTestLanguage.TAGS));

    private static boolean isInstrumentedNode(SourceSectionFilter filter, Node instrumentedNode) {
        try {
            boolean includes = filter.includes(instrumentedNode);
            Method m = filter.getClass().getDeclaredMethod("isInstrumentedNode", Set.class, Node.class, SourceSection.class);
            ReflectionUtils.setAccessible(m, true);
            return includes && (boolean) m.invoke(filter, ALL_TAGS, instrumentedNode, instrumentedNode != null ? instrumentedNode.getSourceSection() : null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isInstrumented(SourceSectionFilter filter, Node rootNode, Node node) {
        return isInstrumentedRoot(filter, rootNode) && isInstrumentedNode(filter, node);
    }

    private static boolean isInstrumentedRoot(SourceSectionFilter filter, Node root) {
        return isInstrumentedRoot(filter, root, ROOT_NODE_BITS_UNINITIALIZED);
    }

    @Before
    public void setup() {
        setupEnv();
        context.initialize(InstrumentationTestLanguage.ID);
    }

    private static boolean isInstrumentedRoot(SourceSectionFilter filter, Node root, int rootNodeBits) {
        try {
            Method m = filter.getClass().getDeclaredMethod("isInstrumentedRoot", Set.class, SourceSection.class, RootNode.class, int.class);
            ReflectionUtils.setAccessible(m, true);
            RootNode rootNode = null;
            if (root instanceof RootNode) {
                rootNode = (RootNode) root;
            }
            return (boolean) m.invoke(filter, ALL_TAGS, root != null ? root.getSourceSection() : null, rootNode, rootNodeBits);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static class SourceSectionNode extends Node implements InstrumentableNode {
        private final SourceSection sourceSection;
        private final Class<?>[] tags;

        SourceSectionNode(SourceSection sourceSection, final Class<?>... tags) {
            this.sourceSection = sourceSection;
            this.tags = tags;
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }

        public boolean isInstrumentable() {
            return sourceSection != null;
        }

        public boolean hasTag(Class<? extends Tag> tag) {
            for (int i = 0; i < tags.length; i++) {
                if (tags[i] == tag) {
                    return true;
                }
            }
            return false;
        }

        public WrapperNode createWrapper(ProbeNode probe) {
            return null;
        }

    }

    private static Node createNode(final SourceSection section, final Class<?>... tags) {
        SourceSectionNode node = new SourceSectionNode(section, tags);
        try {
            RootNode rootNode = createRootNode(section, null, node);
            rootNode.adoptChildren();
        } catch (Exception e) {
            Assert.fail();
        }
        return node;
    }

    static RootNode createRootNode(final SourceSection section, final Boolean internal, Node... children) throws Exception {
        TruffleLanguage<?> truffleLanguage = InstrumentationTestLanguage.current();
        return new RootNode(truffleLanguage) {

            @Node.Children Node[] rootChildren = children;

            @Override
            protected boolean isInstrumentable() {
                return true;
            }

            @Override
            public SourceSection getSourceSection() {
                return section;
            }

            @Override
            public boolean isInternal() {
                if (internal == null) {
                    return super.isInternal();
                } else {
                    return internal;
                }
            }

            @Override
            public Object execute(VirtualFrame frame) {
                return null;
            }

        };
    }

    @Test
    public void testEmpty() {
        Source sampleSource = Source.newBuilder(InstrumentationTestLanguage.ID, "line1\nline2\nline3\nline4", "sampleSource").build();
        Node root = createNode(sampleSource.createSection(0, 23));
        Node unavailable = createNode(sampleSource.createUnavailableSection());
        SourceSectionFilter filter = SourceSectionFilter.ANY;
        Node sampleSection = createNode(sampleSource.createSection(2));

        Assert.assertTrue(isInstrumentedNode(filter, source()));
        Assert.assertTrue(isInstrumentedNode(filter, unavailable));
        Assert.assertTrue(isInstrumentedNode(filter, sampleSection));

        Assert.assertTrue(isInstrumentedRoot(filter, null));
        Assert.assertTrue(isInstrumentedRoot(filter, unavailable));
        Assert.assertTrue(isInstrumentedRoot(filter, sampleSection));

        Assert.assertTrue(isInstrumented(filter, root, source()));
        Assert.assertTrue(isInstrumented(filter, root, unavailable));
        Assert.assertTrue(isInstrumented(filter, root, sampleSection));

        Class<?> prevTag = null;
        for (Class<?> tag : InstrumentationTestLanguage.TAGS) {
            Assert.assertTrue(isInstrumented(filter, root, source(tag)));
            Assert.assertTrue(isInstrumented(filter, root, unavailable));
            Assert.assertTrue(isInstrumented(filter, root, createNode(sampleSource.createSection(0, 4), tag)));
            if (prevTag != null) {
                Assert.assertTrue(isInstrumented(filter, root, source(tag)));
                Assert.assertTrue(isInstrumented(filter, root, unavailable));
                Assert.assertTrue(isInstrumented(filter, root, createNode(sampleSource.createSection(0, 4), tag, prevTag)));
            }
            prevTag = tag;
        }
        Assert.assertNotNull(filter.toString());

    }

    @Test
    public void testLineIn() {
        Source sampleSource = Source.newBuilder(InstrumentationTestLanguage.ID, "line1\nline2\nline3\nline4", "sampleSource").build();
        Node root = createNode(sampleSource.createSection(0, 23));
        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 1).build(),
                        root, createNode(sampleSource.createSection(6, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineIn(1, 2).build(),
                        root, createNode(sampleSource.createSection(2, 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineIn(1, 1).build(),
                        root, createNode(sampleSource.createSection(6, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(3 * LINE_LENGTH, 1), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(3 * LINE_LENGTH - 1, 1), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(3 * LINE_LENGTH - 2, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(0, LINE_LENGTH), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(0, LINE_LENGTH + 1), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(1 * LINE_LENGTH - 2, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(1 * LINE_LENGTH, 1), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(1 * LINE_LENGTH - 2, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineIn(1, 1).build(),
                        root, createNode(sampleSource.createUnavailableSection())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineIs(1).build(),
                        root, createNode(sampleSource.createSection(0, LINE_LENGTH), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineIn(IndexRange.byLength(1, 1)).build(),
                        root, createNode(sampleSource.createSection(LINE_LENGTH, LINE_LENGTH), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineIn(IndexRange.byLength(1, 1), IndexRange.between(2, 3), IndexRange.byLength(3, 1)).build(),
                        root, createNode(sampleSource.createSection(LINE_LENGTH, LINE_LENGTH), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineIn(IndexRange.byLength(1, 1), IndexRange.byLength(3, 1)).build(),
                        root, createNode(sampleSource.createSection(LINE_LENGTH, LINE_LENGTH), tags())));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().lineIn(2, 2).build().toString());
    }

    @Test
    public void testLineStartIn() {
        Source sampleSource = Source.newBuilder(InstrumentationTestLanguage.ID, "line1\nline2\nline3\nline4", "sampleSource").build();
        Node root = createNode(sampleSource.createSection(0, 23));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineStartsIn(IndexRange.byLength(2, 1)).build(),
                        root, createNode(sampleSource.createSection(6, 15))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineStartsIn(IndexRange.byLength(2, 1)).build(),
                        root, createNode(sampleSource.createSection(0, 15))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineStartsIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(0, 15))));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineStartsIn(IndexRange.byLength(1, 2)).build(),
                        root, createNode(sampleSource.createSection(0, 15))));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineStartsIn(IndexRange.byLength(1, 2)).build(),
                        root, createNode(sampleSource.createSection(6, 15))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineStartsIn(IndexRange.byLength(1, 2)).build(),
                        root, createNode(sampleSource.createSection(12, 6))));
    }

    @Test
    public void testLineEndsIn() {
        Source sampleSource = Source.newBuilder(InstrumentationTestLanguage.ID, "line1\nline2\nline3\nline4", "sampleSource").build();
        Node root = createNode(sampleSource.createSection(0, 23));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineEndsIn(IndexRange.byLength(2, 1)).build(),
                        root, createNode(sampleSource.createSection(6, 6))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineEndsIn(IndexRange.byLength(2, 1)).build(),
                        root, createNode(sampleSource.createSection(0, 6))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineEndsIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(0, 6))));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineEndsIn(IndexRange.byLength(1, 2)).build(),
                        root, createNode(sampleSource.createSection(0, 6))));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineEndsIn(IndexRange.byLength(1, 2)).build(),
                        root, createNode(sampleSource.createSection(6, 6))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineEndsIn(IndexRange.byLength(1, 2)).build(),
                        root, createNode(sampleSource.createSection(12, 6))));
    }

    @Test
    public void testLineNotIn() {
        Source sampleSource = Source.newBuilder(InstrumentationTestLanguage.ID, "line1\nline2\nline3\nline4", "sampleSource").build();
        Node root = createNode(sampleSource.createSection(0, 23));
        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(2, 1)).build(),
                        root, createNode(sampleSource.createSection(6, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(1, 2)).build(),
                        root, createNode(sampleSource.createSection(2, 1), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(1, 1)).build(),
                        root, createNode(sampleSource.createSection(6, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(3 * LINE_LENGTH, 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(3 * LINE_LENGTH - 1, 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(3 * LINE_LENGTH - 2, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(0, LINE_LENGTH), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(0, LINE_LENGTH + 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(1 * LINE_LENGTH - 2, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(1 * LINE_LENGTH, 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(1 * LINE_LENGTH - 2, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(1, 1)).build(),
                        root, createNode(sampleSource.createUnavailableSection())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(1, 1)).build(),
                        root, createNode(sampleSource.createSection(0, LINE_LENGTH), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(1, 1)).build(),
                        root, createNode(sampleSource.createSection(LINE_LENGTH, LINE_LENGTH), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(1, 1), IndexRange.between(2, 3), IndexRange.byLength(3, 1)).build(),
                        root, createNode(sampleSource.createSection(LINE_LENGTH, LINE_LENGTH), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(1, 1), IndexRange.byLength(3, 1)).build(),
                        root, createNode(sampleSource.createSection(LINE_LENGTH, LINE_LENGTH), tags())));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().lineIn(2, 2).build().toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLineInFail1() {
        SourceSectionFilter.newBuilder().lineIn(0, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLineInFail2() {
        SourceSectionFilter.newBuilder().lineIn(3, -1);
    }

    @Test
    public void testColumnIn() {
        Source sampleSource = Source.newBuilder(InstrumentationTestLanguage.ID, "123456789\n123456789", "sampleSource").build();
        Node root = createNode(sampleSource.createSection(0, 19));
        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().columnIn(2, 1).build(),
                        root, createNode(sampleSource.createSection(1, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().columnIn(1, 2).build(),
                        root, createNode(sampleSource.createSection(0, 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().columnIn(1, 1).build(),
                        root, createNode(sampleSource.createSection(1, 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().columnIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(4, 1), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().columnIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(1, 1), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().columnIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(1, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().columnIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(0, 1), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().columnIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(0, 2), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().columnIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(1, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().columnIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(1, 1), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().columnIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(0, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().columnIn(1, 1).build(),
                        root, createNode(sampleSource.createUnavailableSection())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().columnIn(IndexRange.byLength(1, 1)).build(),
                        root, createNode(sampleSource.createSection(1, 1), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().columnIn(IndexRange.byLength(1, 1), IndexRange.between(2, 3), IndexRange.byLength(3, 1)).build(),
                        root, createNode(sampleSource.createSection(1, 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().columnIn(IndexRange.byLength(1, 1), IndexRange.byLength(3, 1)).build(),
                        root, createNode(sampleSource.createSection(1, 1), tags())));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().columnIn(2, 2).build().toString());
    }

    @Test
    public void testColumnStartIn() {
        Source sampleSource = Source.newBuilder(InstrumentationTestLanguage.ID, "123456789\n123456789", "sampleSource").build();
        Node root = createNode(sampleSource.createSection(0, 19));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().columnStartsIn(IndexRange.byLength(2, 1)).build(),
                        root, createNode(sampleSource.createSection(1, 1))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().columnStartsIn(IndexRange.byLength(2, 1)).build(),
                        root, createNode(sampleSource.createSection(0, 2))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().columnStartsIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(0, 2))));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().columnStartsIn(IndexRange.byLength(1, 2)).build(),
                        root, createNode(sampleSource.createSection(0, 2))));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().columnStartsIn(IndexRange.byLength(1, 2)).build(),
                        root, createNode(sampleSource.createSection(1, 2))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().columnStartsIn(IndexRange.byLength(1, 2)).build(),
                        root, createNode(sampleSource.createSection(2, 1))));
    }

    @Test
    public void testColumnEndsIn() {
        Source sampleSource = Source.newBuilder(InstrumentationTestLanguage.ID, "123456789\n123456789", "sampleSource").build();
        Node root = createNode(sampleSource.createSection(0, 19));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().columnEndsIn(IndexRange.byLength(2, 1)).build(),
                        root, createNode(sampleSource.createSection(1, 1))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().columnEndsIn(IndexRange.byLength(2, 1)).build(),
                        root, createNode(sampleSource.createSection(0, 1))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().columnEndsIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(0, 1))));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().columnEndsIn(IndexRange.byLength(1, 2)).build(),
                        root, createNode(sampleSource.createSection(0, 1))));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().columnEndsIn(IndexRange.byLength(1, 2)).build(),
                        root, createNode(sampleSource.createSection(1, 1))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().columnEndsIn(IndexRange.byLength(1, 2)).build(),
                        root, createNode(sampleSource.createSection(2, 1))));
    }

    @Test
    public void testColumnNotIn() {
        Source sampleSource = Source.newBuilder(InstrumentationTestLanguage.ID, "123456789\n123456789", "sampleSource").build();
        Node root = createNode(sampleSource.createSection(0, 19));
        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().columnNotIn(IndexRange.byLength(2, 1)).build(),
                        root, createNode(sampleSource.createSection(1, 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().columnNotIn(IndexRange.byLength(1, 2)).build(),
                        root, createNode(sampleSource.createSection(0, 1), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().columnNotIn(IndexRange.byLength(1, 1)).build(),
                        root, createNode(sampleSource.createSection(1, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().columnNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(3, 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().columnNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(2, 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().columnNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(2, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().columnNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(0, 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().columnNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(0, 2), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().columnNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(0, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().columnNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(1, 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().columnNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(1, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().columnNotIn(IndexRange.byLength(1, 1)).build(),
                        root, createNode(sampleSource.createUnavailableSection())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().columnNotIn(IndexRange.byLength(1, 1)).build(),
                        root, createNode(sampleSource.createSection(0, 1), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().columnNotIn(IndexRange.byLength(1, 1)).build(),
                        root, createNode(sampleSource.createSection(1, 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().columnNotIn(IndexRange.byLength(1, 1), IndexRange.between(2, 3), IndexRange.byLength(3, 1)).build(),
                        root, createNode(sampleSource.createSection(1, 1), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().columnNotIn(IndexRange.byLength(1, 1), IndexRange.byLength(3, 1)).build(),
                        root, createNode(sampleSource.createSection(1, 1), tags())));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().columnIn(2, 2).build().toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testColumnInFail1() {
        SourceSectionFilter.newBuilder().columnIn(0, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testColumnInFail2() {
        SourceSectionFilter.newBuilder().columnIn(3, -1);
    }

    @Test
    public void testMimeType() {
        String src = "line1\nline2\nline3\nline4";
        Source mime1 = Source.newBuilder("mime", src, "mime1").mimeType("text/mime1").build();
        Source mime2 = Source.newBuilder("mime", src, "mime2").mimeType("text/mime2").build();
        Source mime3 = Source.newBuilder("mime", src, "mime3").mimeType("text/mime3").build();

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().mimeTypeIs().build(),
                                        null, createNode(mime3.createSection(0, 5), tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().mimeTypeIs("text/mime1").build(),
                                        null, createNode(mime1.createSection(0, 5), tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().mimeTypeIs("text/mime1").build(),
                                        null, createNode(mime2.createSection(0, 5), tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().mimeTypeIs("text/mime1", "text/mime2").build(),
                                        null, createNode(mime2.createSection(0, 5), tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().mimeTypeIs("text/mime1", "text/mime2").build(),
                                        null, createNode(mime3.createSection(0, 5), tags())));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().mimeTypeIs("text/mime1", "text/mime2").build().toString());
    }

    @Test
    public void testIndexIn() {
        Source sampleSource = Source.newBuilder(InstrumentationTestLanguage.ID, "line1\nline2\nline3\nline4", "sampleSource").build();
        Node root = createNode(sampleSource.createSection(0, 23));
        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexIn(0, 0).build(),
                        root, createNode(sampleSource.createSection(0, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexIn(0, 1).build(),
                        root, createNode(sampleSource.createSection(0, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                        root, createNode(sampleSource.createSection(5, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                        root, createNode(sampleSource.createSection(0, 4), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                        root, createNode(sampleSource.createSection(0, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                        root, createNode(sampleSource.createSection(4, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                        root, createNode(sampleSource.createSection(4, 6), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                        root, createNode(sampleSource.createSection(5, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                        root, createNode(sampleSource.createSection(10, 1), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                        root, createNode(sampleSource.createSection(9, 1), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                        root, createNode(sampleSource.createSection(9, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                        root, createNode(sampleSource.createUnavailableSection())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexIn(IndexRange.between(0, 5)).build(),
                        root, createNode(sampleSource.createSection(5, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexIn(IndexRange.between(0, 5), IndexRange.between(5, 6)).build(),
                        root, createNode(sampleSource.createSection(5, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexIn(IndexRange.between(0, 5), IndexRange.between(11, 12)).build(),
                        root, createNode(sampleSource.createSection(5, 5), tags())));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().indexIn(5, 5).build().toString());

    }

    @Test
    public void testIndexNotIn() {
        Source sampleSource = Source.newBuilder(InstrumentationTestLanguage.ID, "line1\nline2\nline3\nline4", "sampleSource").build();
        Node root = createNode(sampleSource.createSection(0, 23));
        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(0, 0)).build(),
                        root, createNode(sampleSource.createSection(0, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(0, 1)).build(),
                        root, createNode(sampleSource.createSection(0, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(5, 5)).build(),
                        root, createNode(sampleSource.createSection(5, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(5, 5)).build(),
                        root, createNode(sampleSource.createSection(0, 4), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(5, 5)).build(),
                        root, createNode(sampleSource.createSection(0, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(5, 5)).build(),
                        root, createNode(sampleSource.createSection(4, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(5, 5)).build(),
                        root, createNode(sampleSource.createSection(4, 6), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(5, 5)).build(),
                        root, createNode(sampleSource.createSection(5, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(5, 5)).build(),
                        root, createNode(sampleSource.createSection(10, 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(5, 5)).build(),
                        root, createNode(sampleSource.createSection(9, 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(5, 5)).build(),
                        root, createNode(sampleSource.createSection(9, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(5, 5)).build(),
                        root, createNode(sampleSource.createUnavailableSection())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.between(0, 5)).build(),
                        root, createNode(sampleSource.createSection(5, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.between(0, 5), IndexRange.between(5, 6)).build(),
                        root, createNode(sampleSource.createSection(5, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.between(0, 5), IndexRange.between(11, 12)).build(),
                        root, createNode(sampleSource.createSection(5, 5), tags())));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().indexIn(5, 5).build().toString());

    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexInFail1() {
        SourceSectionFilter.newBuilder().indexIn(-1, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexInFail2() {
        SourceSectionFilter.newBuilder().indexIn(3, -1);
    }

    @Test
    public void testRootNodeBits() {
        Source sampleSource1 = Source.newBuilder(InstrumentationTestLanguage.ID, "line1\nline2\nline3\nline4", "sampleSource1").mimeType("text/mime2").build();
        Source sampleSource2 = Source.newBuilder(InstrumentationTestLanguage.ID, "line1\nline2\nline3\nline4", "sampleSource2").mimeType("text/mime2").build();

        SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.DEFINE).//
                        tagIsNot(InstrumentationTestLanguage.DEFINE, InstrumentationTestLanguage.ROOT).//
                        indexIn(0, 3).//
                        sourceIs(sampleSource1).sourceSectionEquals(sampleSource1.createSection(0, 5)).//
                        lineIn(1, 1).lineIs(1).mimeTypeIs("text/mime1", "text/mime2").build();

        Assert.assertFalse(isInstrumentedRoot(filter, null, ROOT_NODE_BITS_NO_SOURCE_SECTION));
        Assert.assertFalse(isInstrumentedRoot(filter, createNode(sampleSource1.createSection(1)), ROOT_NODE_BITS_NO_SOURCE_SECTION));
        Assert.assertFalse(isInstrumentedRoot(filter, createNode(sampleSource1.createSection(1)), ROOT_NODE_BITS_NO_SOURCE_SECTION));

        Assert.assertTrue(isInstrumentedRoot(filter, null, ROOT_NODE_BITS_SAME_SOURCE));
        Assert.assertTrue(isInstrumentedRoot(filter, createNode(sampleSource1.createSection(1)), ROOT_NODE_BITS_SAME_SOURCE));
        Assert.assertTrue(isInstrumentedRoot(filter, createNode(sampleSource1.createSection(1)), ROOT_NODE_BITS_UNINITIALIZED));
        Assert.assertTrue(isInstrumentedRoot(filter, createNode(sampleSource2.createSection(1)), ROOT_NODE_BITS_UNINITIALIZED));
        Assert.assertFalse(isInstrumentedRoot(filter, createNode(sampleSource2.createSection(1)), ROOT_NODE_BITS_SAME_SOURCE));

        Assert.assertTrue(isInstrumentedRoot(filter, null, ROOT_NODE_BITS_SOURCE_SECTION_HIERARCHICAL));
        Assert.assertTrue(isInstrumentedRoot(filter, createNode(sampleSource1.createSection(1)), ROOT_NODE_BITS_SOURCE_SECTION_HIERARCHICAL));
        Assert.assertFalse(isInstrumentedRoot(filter, createNode(sampleSource1.createSection(5, 10)), ROOT_NODE_BITS_SOURCE_SECTION_HIERARCHICAL));
        Assert.assertTrue(isInstrumentedRoot(filter, createNode(sampleSource1.createSection(5, 10)), ROOT_NODE_BITS_UNINITIALIZED));
    }

    @Test
    public void testSourceIn() {
        Source sampleSource1 = Source.newBuilder(InstrumentationTestLanguage.ID, "line1\nline2\nline3\nline4", "sampleSource1").build();
        Source sampleSource2 = Source.newBuilder(InstrumentationTestLanguage.ID, "line1\nline2\nline3\nline4", "sampleSource2").build();
        Source sampleSource3 = Source.newBuilder(InstrumentationTestLanguage.ID, "line1\nline2\nline3\nline4", "sampleSource3").build();

        Node root1 = createNode(sampleSource1.createSection(0, 23));
        Node root2 = createNode(sampleSource2.createSection(0, 23));
        Node root3 = createNode(sampleSource3.createSection(0, 23));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceIs(sampleSource1).build(),
                                        root1, createNode(sampleSource1.createSection(0, 5), tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceIs(sampleSource1).build(),
                                        null, createNode(sampleSource2.createUnavailableSection())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceIs(sampleSource1).build(),
                                        root2, createNode(sampleSource2.createSection(0, 5), tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceIs(sampleSource1, sampleSource2).build(),
                                        root2, createNode(sampleSource2.createSection(0, 5), tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceIs(sampleSource1, sampleSource2).build(),
                                        root1, createNode(sampleSource1.createSection(0, 5), tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceIs(sampleSource1, sampleSource2).build(),
                                        root3, createNode(sampleSource3.createSection(0, 5), tags())));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().sourceIs(sampleSource1, sampleSource2).build().toString());
    }

    @Test
    public void testSourceSectionEquals() {
        Source sampleSource1 = Source.newBuilder(InstrumentationTestLanguage.ID, "line1\nline2\nline3\nline4", "sampleSource1").build();
        Source sampleSource2 = Source.newBuilder(InstrumentationTestLanguage.ID, "line1\nline2\nline3\nline4", "sampleSource2").build();
        Node root1 = createNode(sampleSource1.createSection(0, 23));
        Node root2 = createNode(sampleSource2.createSection(0, 23));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(1, 6)).build(),
                                        root1, createNode(sampleSource1.createSection(1, 6), tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(1, 6)).build(),
                                        root2, createNode(sampleSource2.createSection(1, 6), tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(1, 7)).build(),
                                        root1, createNode(sampleSource1.createSection(1, 6), tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(2, 6)).build(),
                                        root1, createNode(sampleSource1.createSection(1, 6), tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(2, 6), sampleSource1.createSection(2, 7)).build(),
                                        root1, createNode(sampleSource1.createSection(2, 7), tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(2, 6), sampleSource1.createSection(2, 7)).build(),
                                        root1, createNode(sampleSource1.createSection(2, 8), tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(2, 6), sampleSource1.createSection(2, 7)).build(),
                                        null, createNode(sampleSource1.createUnavailableSection())));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(1, 6)).build().toString());
    }

    @Test
    public void testRootSourceSectionEquals() {
        Source sampleSource1 = Source.newBuilder(InstrumentationTestLanguage.ID, "line1\nline2\nline3\nline4", "sampleSource1").build();

        Assert.assertTrue(isInstrumentedNode(SourceSectionFilter.newBuilder().rootSourceSectionEquals(sampleSource1.createSection(1, 6)).build(),
                        createNode(sampleSource1.createSection(6, 4))));
        Assert.assertTrue(isInstrumentedRoot(SourceSectionFilter.newBuilder().rootSourceSectionEquals(sampleSource1.createSection(1, 6)).build(),
                        createNode(sampleSource1.createSection(1, 6))));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().rootSourceSectionEquals(sampleSource1.createSection(1, 6)).build(),
                        createNode(sampleSource1.createSection(1, 6)), createNode(sampleSource1.createSection(6, 4))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().rootSourceSectionEquals(sampleSource1.createSection(1, 6)).build(),
                        createNode(sampleSource1.createSection(2, 6)), createNode(sampleSource1.createSection(1, 4))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().rootSourceSectionEquals(sampleSource1.createSection(1, 6)).build(),
                        createNode(sampleSource1.createSection(1, 7)), createNode(sampleSource1.createSection(1, 4))));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().rootSourceSectionEquals(sampleSource1.createSection(1, 6)).build(),
                        createNode(sampleSource1.createSection(1, 6)), createNode(sampleSource1.createUnavailableSection())));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().rootSourceSectionEquals(sampleSource1.createSection(1, 6)).build().toString());
    }

    private static Node source(Class<?>... tags) {
        return createNode(Source.newBuilder(InstrumentationTestLanguage.ID, "foo", "sampleSource1").build().createSection(0, 3), tags);
    }

    @Test
    public void testTagsIn() {
        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs().build(), null, source()));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs().build(), null, source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(), null, source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        null, source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        null, source(InstrumentationTestLanguage.ROOT)));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        null, source()));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        null, source(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT)));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(),
                                        null, source(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT)));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT, InstrumentationTestLanguage.EXPRESSION).build().toString());
    }

    @Test
    public void testTagsNotIn() {
        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot().build(),
                                        null, source()));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot().build(),
                                        null, source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.STATEMENT).build(),
                                        null, source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        null, source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        null, source(InstrumentationTestLanguage.ROOT)));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        null, source()));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        null, source(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT)));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.STATEMENT).build(),
                                        null, source(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT)));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.STATEMENT, InstrumentationTestLanguage.EXPRESSION).build().toString());
    }

    @Test
    public void testIgnoreInternal() throws Exception {
        SourceSectionFilter internalFilter = SourceSectionFilter.newBuilder().includeInternal(false).build();
        SourceSectionFilter defaultFilter = SourceSectionFilter.newBuilder().build();
        Assert.assertTrue(isInstrumented(internalFilter, null, source()));

        Source nonInternalSource = Source.newBuilder(InstrumentationTestLanguage.ID, "line1\nline2\nline3\nline4", "nonInternalSource").build();
        // Default non-internal RootNode
        RootNode root = createRootNode(nonInternalSource.createSection(0, 23), null);
        Assert.assertTrue(
                        isInstrumented(internalFilter, root, createNode(nonInternalSource.createSection(1))));
        // Internal RootNode
        root = createRootNode(nonInternalSource.createSection(0, 23), true);
        Assert.assertFalse(
                        isInstrumented(internalFilter, root, createNode(nonInternalSource.createSection(1))));
        Assert.assertTrue(
                        isInstrumented(defaultFilter, root, createNode(nonInternalSource.createSection(1))));
        Source internalSource = Source.newBuilder(InstrumentationTestLanguage.ID, "line1\nline2\nline3\nline4", "internalSource").internal(true).build();
        // Default internal RootNode
        root = createRootNode(internalSource.createSection(0, 23), null);
        Assert.assertFalse(
                        isInstrumented(internalFilter, root, createNode(internalSource.createSection(1))));
        // Non-internal RootNode
        root = createRootNode(nonInternalSource.createSection(0, 23), false);
        Assert.assertFalse(
                        isInstrumented(internalFilter, root, createNode(internalSource.createSection(1))));
    }

    @Test
    public void testComplexFilter() {
        Source sampleSource1 = Source.newBuilder(InstrumentationTestLanguage.ID, "line1\nline2\nline3\nline4", "sampleSource1").mimeType("text/mime2").build();
        Node root = createNode(sampleSource1.createSection(0, 23));

        SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.DEFINE).//
                        tagIsNot(InstrumentationTestLanguage.DEFINE, InstrumentationTestLanguage.ROOT).//
                        indexIn(0, 3).//
                        sourceIs(sampleSource1).sourceSectionEquals(sampleSource1.createSection(0, 5)).//
                        lineIn(1, 1).lineIs(1).mimeTypeIs("text/mime1", "text/mime2").build();

        Assert.assertFalse(isInstrumented(filter, root, source()));
        Assert.assertTrue(isInstrumentedRoot(filter, null));
        Assert.assertFalse(isInstrumentedNode(filter, source()));

        Assert.assertFalse(isInstrumented(filter, root, createNode(sampleSource1.createUnavailableSection())));
        Assert.assertTrue(isInstrumentedRoot(filter, createNode(sampleSource1.createUnavailableSection())));
        Assert.assertFalse(isInstrumentedNode(filter, createNode(sampleSource1.createUnavailableSection())));

        Assert.assertTrue(isInstrumented(filter, root, createNode(sampleSource1.createSection(0, 5), tags(InstrumentationTestLanguage.EXPRESSION))));
        Assert.assertTrue(isInstrumentedRoot(filter, createNode(sampleSource1.createSection(0, 5))));
        Assert.assertTrue(isInstrumentedNode(filter, createNode(sampleSource1.createSection(0, 5), tags(InstrumentationTestLanguage.EXPRESSION))));

        Assert.assertFalse(isInstrumented(filter, root, createNode(sampleSource1.createSection(0, 5), tags(InstrumentationTestLanguage.STATEMENT))));
        Assert.assertTrue(isInstrumentedRoot(filter, createNode(sampleSource1.createSection(0, 5))));
        Assert.assertTrue(isInstrumentedRoot(filter, createNode(sampleSource1.createSection(10, 5))));
        Assert.assertFalse(isInstrumentedNode(filter, createNode(sampleSource1.createSection(0, 5), tags(InstrumentationTestLanguage.STATEMENT))));

        Assert.assertNotNull(filter.toString());
    }

    private static Class<?>[] tags(Class<?>... tags) {
        return tags;
    }

}
