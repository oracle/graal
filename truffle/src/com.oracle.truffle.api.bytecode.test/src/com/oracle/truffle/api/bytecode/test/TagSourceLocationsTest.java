/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLabel;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants.Variant;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.TagTree;
import com.oracle.truffle.api.bytecode.test.TagSourceInterpreterBuilder.BytecodeVariant;
import com.oracle.truffle.api.bytecode.test.TagTest.TagTestLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

@RunWith(Parameterized.class)
public class TagSourceLocationsTest extends AbstractPolyglotTest {
    private final BytecodeVariant variant;
    private final boolean suffix;

    public TagSourceLocationsTest(BytecodeVariant variant, boolean suffix) {
        this.variant = variant;
        this.suffix = suffix;
    }

    @Parameters(name = "{0}, suffix={1}")
    public static List<Object[]> parameters() {
        List<Object[]> parameters = new ArrayList<>();
        for (BytecodeVariant variant : TagSourceInterpreterBuilder.variants()) {
            parameters.add(new Object[]{variant, false});
            parameters.add(new Object[]{variant, true});
        }
        return parameters;
    }

    @Before
    public void setup() {
        setupEnv(Context.newBuilder().build());
        context.initialize(TagTestLanguage.ID);
        context.enter();
    }

    @After
    public void leaveContext() {
        context.leave();
    }

    private BytecodeConfig config(boolean sources, boolean statements) {
        var builder = variant.newConfigBuilder().addTag(ExpressionTag.class);
        if (sources) {
            builder.addSource();
        }
        if (statements) {
            builder.addTag(StatementTag.class);
        }
        return builder.build();
    }

    private void beginSourceSection(TagSourceInterpreterBuilder b, int index) {
        if (suffix) {
            b.beginSourceSection();
        } else {
            b.beginSourceSection(index, 1);
        }
    }

    private void endSourceSection(TagSourceInterpreterBuilder b, int index) {
        if (suffix) {
            b.endSourceSection(index, 1);
        } else {
            b.endSourceSection();
        }
    }

    private BytecodeRootNodes<TagSourceInterpreter> create(int count, BytecodeConfig config) {
        return create(count, config, false);
    }

    private BytecodeRootNodes<TagSourceInterpreter> create(int count, BytecodeConfig config, boolean withUnreachableTags) {
        Source source = Source.newBuilder(TagTestLanguage.ID, "x".repeat(count), "tags").build();
        return variant.create(TagTestLanguage.REF.get(null), config, b -> {
            b.beginRoot();
            b.beginSource(source);
            b.beginBlock();
            BytecodeLabel resume = withUnreachableTags ? b.createLabel() : null;
            for (int i = 0; i < count; i++) {
                if (withUnreachableTags && i == count / 2) {
                    b.emitBranch(resume);
                }
                // Include gaps, unavailable sections, nested sections, and equal-range sections.
                if (i % 4 == 1) {
                    b.beginSourceSectionUnavailable();
                } else if (i % 4 >= 2) {
                    beginSourceSection(b, i);
                }
                b.beginTag(StatementTag.class);
                if (i % 4 == 3) {
                    beginSourceSection(b, i - 1);
                    beginSourceSection(b, i);
                }
                b.beginTag(ExpressionTag.class);
                b.beginIdentity();
                b.emitLoadConstant(i);
                b.endIdentity();
                b.endTag(ExpressionTag.class);
                if (i % 4 == 3) {
                    endSourceSection(b, i);
                    endSourceSection(b, i - 1);
                }
                b.endTag(StatementTag.class);
                if (i % 4 == 1) {
                    b.endSourceSectionUnavailable();
                } else if (i % 4 >= 2) {
                    endSourceSection(b, i);
                }
            }
            if (withUnreachableTags) {
                b.emitLabel(resume);
                beginSourceSection(b, count - 1);
                b.beginReturn();
                b.beginTag(ExpressionTag.class);
                b.emitLoadConstant(42);
                b.endTag(ExpressionTag.class);
                b.endReturn();
                endSourceSection(b, count - 1);
            }
            b.endBlock();
            b.endSource();
            b.endRoot();
        });
    }

    private static void collectTags(TagTree tree, List<TagTree> tags) {
        if (tree.getEnterBytecodeIndex() >= 0) {
            tags.add(tree);
        }
        for (TagTree child : tree.getTreeChildren()) {
            collectTags(child, tags);
        }
    }

    private static void assertTagSources(TagSourceInterpreter root) {
        root.getCallTarget(); // Adopt the tag tree before looking up source sections.
        BytecodeNode bytecode = root.getBytecodeNode();
        List<TagTree> tags = new ArrayList<>();
        collectTags(bytecode.getTagTree(), tags);
        assertFalse(tags.isEmpty());
        for (TagTree tag : tags) {
            assertEquals(bytecode.getSourceLocation(tag.getEnterBytecodeIndex()), tag.getSourceSection());
        }
    }

    @Test
    public void testSourceLookupMatchesLinearLookup() {
        for (int count : new int[]{4, 128}) {
            TagSourceInterpreter root = create(count, config(true, true)).getNode(0);
            assertTagSources(root);
        }
    }

    @Test
    public void testDuplicateBcis() {
        // Unreachable tags share the following reachable tag's BCI because no instructions are
        // emitted.
        TagSourceInterpreter root = create(128, config(true, true), true).getNode(0);
        List<TagTree> tags = new ArrayList<>();
        collectTags(root.getBytecodeNode().getTagTree(), tags);
        assertTrue(tags.stream().mapToInt(TagTree::getEnterBytecodeIndex).distinct().count() < tags.size());
        assertTagSources(root);
        assertEquals(42, root.getCallTarget().call());
    }

    @Test
    public void testEmptySourceTable() {
        BytecodeRootNodes<TagSourceInterpreter> nodes = variant.create(TagTestLanguage.REF.get(null), config(true, true), b -> {
            b.beginRoot();
            for (int i = 0; i < 128; i++) {
                b.beginTag(ExpressionTag.class);
                b.beginIdentity();
                b.emitLoadConstant(i);
                b.endIdentity();
                b.endTag(ExpressionTag.class);
            }
            b.endRoot();
        });
        assertEquals(0, nodes.getNode(0).getBytecodeNode().getSourceInformation().size());
        assertTagSources(nodes.getNode(0));
    }

    @Test
    public void testUpdatesAndClone() {
        BytecodeRootNodes<TagSourceInterpreter> nodes = create(128, config(false, false));
        TagSourceInterpreter root = nodes.getNode(0);
        assertTagSources(root);
        nodes.update(config(true, false));
        assertTagSources(root);
        root.getBytecodeNode().setUncachedThreshold(0);
        root.getCallTarget().call();
        assertTagSources(root);
        nodes.update(config(true, true));
        assertTagSources(root);
        TagSourceInterpreter clone = NodeUtil.cloneNode(root);
        assertTagSources(clone);
        clone.getCallTarget().call();
        assertTagSources(clone);
    }

    @Test
    public void testConcurrentLookup() throws Exception {
        TagSourceInterpreter root = create(128, config(true, true)).getNode(0);
        root.getCallTarget();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> assertTagSources(root));
            Future<?> second = executor.submit(() -> assertTagSources(root));
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }
    }
}

@GenerateBytecodeTestVariants({
                @Variant(suffix = "Compressed", configuration = @GenerateBytecode(languageClass = TagTestLanguage.class, enableTagInstrumentation = true, enableUncachedInterpreter = true)),
                @Variant(suffix = "Uncompressed", configuration = @GenerateBytecode(languageClass = TagTestLanguage.class, enableTagInstrumentation = true, enableUncachedInterpreter = true, enableCompressedSources = false))})
abstract class TagSourceInterpreter extends DebugBytecodeRootNode implements BytecodeRootNode {
    protected TagSourceInterpreter(TagTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Operation
    static final class Identity {
        @Specialization
        static int doInt(int value) {
            return value;
        }
    }
}
