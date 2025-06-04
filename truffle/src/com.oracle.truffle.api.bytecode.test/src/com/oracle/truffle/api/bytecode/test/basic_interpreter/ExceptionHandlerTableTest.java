/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.test.basic_interpreter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.bytecode.BytecodeLabel;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ExceptionHandler;
import com.oracle.truffle.api.bytecode.ExceptionHandler.HandlerKind;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;

@RunWith(Parameterized.class)
public class ExceptionHandlerTableTest extends AbstractBasicInterpreterTest {

    public ExceptionHandlerTableTest(TestRun run) {
        super(run);
    }

    private record ExceptionRangeTree(int index, String name, HandlerKind kind, ExceptionRangeTree[] nested) {
    }

    private static ExceptionRangeTree handler(int index, ExceptionRangeTree... nested) {
        return new ExceptionRangeTree(index, null, HandlerKind.CUSTOM, nested);
    }

    private static ExceptionRangeTree handler(int index, String name, ExceptionRangeTree... nested) {
        return new ExceptionRangeTree(index, name, HandlerKind.CUSTOM, nested);
    }

    private static ExceptionRangeTree tag(int index, ExceptionRangeTree... nested) {
        return new ExceptionRangeTree(index, null, HandlerKind.TAG, nested);
    }

    private static void assertHandlers(BytecodeRootNode node, ExceptionRangeTree... trees) {
        new HandlerRangeValidator(node).validate(trees);
    }

    private static final class HandlerRangeValidator {
        final BytecodeNode bytecodeNode;
        final List<ExceptionHandler> handlers;
        final Set<ExceptionHandler> uncheckedHandlers;
        final Map<String, Integer> handlersByName;
        final Map<Integer, String> handlerToName;

        HandlerRangeValidator(BytecodeRootNode node) {
            this.bytecodeNode = node.getBytecodeNode();
            // copy the list elements so that hash comparisons work properly.
            this.handlers = new ArrayList<>(bytecodeNode.getExceptionHandlers());
            this.uncheckedHandlers = new HashSet<>(this.handlers);
            this.handlersByName = new HashMap<>();
            this.handlerToName = new HashMap<>();
        }

        private void validate(ExceptionRangeTree[] trees) {
            try {
                assertOrdered(trees);
                for (int i = 0; i < trees.length; i++) {
                    assertHandlersRecursive(trees[i]);
                }
                assertTrue(String.format("Some handlers were not checked by the test spec: %s", uncheckedHandlers), uncheckedHandlers.isEmpty());
            } catch (AssertionError err) {
                throw new AssertionError(err.getMessage() + "\n" + bytecodeNode.dump());
            }
        }

        private void assertHandlersRecursive(ExceptionRangeTree tree) {
            ExceptionHandler handler = getHandler(tree);
            uncheckedHandlers.remove(handler);
            if (tree.name != null) {
                if (handlersByName.containsKey(tree.name)) {
                    // Check that two handler ranges with the same name match
                    int existingIndex = handlersByName.get(tree.name);
                    assertEquals(String.format("Handler range at index %d with name %s has a different handler than another handler range with the same name.", tree.index, tree.name),
                                    existingIndex, handler.getHandlerBytecodeIndex());
                } else {
                    if (handlerToName.containsKey(handler.getHandlerBytecodeIndex())) {
                        // Check that two handler ranges with the same handler have the same name
                        String existingName = handlerToName.get(handler.getHandlerBytecodeIndex());
                        fail(String.format("Handler range at index %d has the same handler as another handler range, but they have different names (%s and %s).", tree.index, tree.name, existingName));
                    }
                    handlersByName.put(tree.name, handler.getHandlerBytecodeIndex());
                    handlerToName.put(handler.getHandlerBytecodeIndex(), tree.name);
                }
            }

            assertEquals(tree.kind, handler.getKind());
            assertOrdered(tree.nested);
            for (ExceptionRangeTree nested : tree.nested) {
                assertContains(tree, nested);
                assertHandlersRecursive(nested);
            }
        }

        private ExceptionHandler getHandler(ExceptionRangeTree tree) {
            if (tree.index < 0 || tree.index >= handlers.size()) {
                fail(String.format("No handler with index %d", tree.index));
            }
            return handlers.get(tree.index);
        }

        private void assertContains(ExceptionRangeTree outerTree, ExceptionRangeTree innerTree) {
            ExceptionHandler outer = getHandler(outerTree);
            ExceptionHandler inner = getHandler(innerTree);
            assertTrue(outer.getStartBytecodeIndex() <= inner.getStartBytecodeIndex());
            assertTrue(inner.getEndBytecodeIndex() <= outer.getEndBytecodeIndex());

        }

        private void assertOrdered(ExceptionRangeTree[] trees) {
            for (int i = 0; i < trees.length - 1; i++) {
                ExceptionHandler left = getHandler(trees[i]);
                ExceptionHandler right = getHandler(trees[i + 1]);
                assertTrue(left.getEndBytecodeIndex() <= right.getStartBytecodeIndex());
            }
        }
    }

    Context context;

    @Before
    public void setup() {
        context = Context.create(BytecodeDSLTestLanguage.ID);
        context.initialize(BytecodeDSLTestLanguage.ID);
        context.enter();
    }

    @After
    public void tearDown() {
        context.close();
    }

    private static void emitNop(BasicInterpreterBuilder b, Object marker) {
        b.emitLoadConstant(marker);
    }

    // @formatter:off
    @Test
    public void testTryCatch() {
        // try {
        //   return 42;
        // } catch ex {
        //   return 123;
        // }
        BasicInterpreter root = parseNode("tryCatch", b -> {
            b.beginRoot();
            b.beginTryCatch();
            emitReturn(b, 42);
            emitReturn(b, 123);
            b.endTryCatch();
            b.endRoot();
        });
        assertEquals(42L, root.getCallTarget().call());
        assertHandlers(root, handler(0));
    }

    @Test
    public void testTryCatchNestedInTry() {
        // try {
        //   try {
        //     return 42
        //   } catch ex2 {
        //     return 123
        //   }
        // } catch ex1 {
        //   return 100
        // }
        BasicInterpreter root = parseNode("tryCatchNestedInTry", b -> {
            b.beginRoot();

            b.beginTryCatch();
                b.beginTryCatch();
                emitReturn(b, 42);
                emitReturn(b, 123);
                b.endTryCatch();

                emitReturn(b, 100);
            b.endTryCatch();
            b.endRoot();
        });
        assertEquals(42L, root.getCallTarget().call());
        assertHandlers(root, handler(1, "ex1", handler(0, "ex2")), handler(3, "ex1", handler(2, "ex2")));
    }

    @Test
    public void testTryCatchNestedInCatch() {
        // try {
        //   return 42
        // } catch ex1 {
        //   try {
        //     return 123
        //   } catch ex2 {
        //     return 100
        //   }
        // }
        BasicInterpreter root = parseNode("tryCatchNestedInCatch", b -> {
            b.beginRoot();

            b.beginTryCatch();
                emitReturn(b, 42);

                b.beginTryCatch();
                emitReturn(b, 123);
                emitReturn(b, 100);
                b.endTryCatch();
            b.endTryCatch();
            b.endRoot();
        });
        assertEquals(42L, root.getCallTarget().call());
        assertHandlers(root, handler(0, "ex1"), handler(1, "ex2"));
    }

    @Test
    public void testTryCatchInTag() {
        // expressionTag {
        //   try {
        //     if (arg0) return 42
        //     A
        //   } catch ex1 {
        //     B
        //   }
        // }
        BasicInterpreter root = parseNode("tryCatchNestedInCatch", b -> {
            b.beginRoot();

            b.beginTag(ExpressionTag.class);
            b.beginTryCatch();
                b.beginBlock();
                    emitReturnIf(b, 0, 42);
                    emitNop(b, "A");
                b.endBlock();
                emitNop(b, "B");
            b.endTryCatch();
            b.endTag(ExpressionTag.class);
            b.endRoot();
        });
        assertEquals(42L, root.getCallTarget().call(true));
        assertEquals(null, root.getCallTarget().call(false));
        assertHandlers(root, handler(0, "ex1"));

        root.getRootNodes().update(createBytecodeConfigBuilder().addTag(ExpressionTag.class).build());
        assertEquals(42L, root.getCallTarget().call(true));
        assertEquals(null, root.getCallTarget().call(false));
        assertHandlers(root, tag(1, handler(0, "ex1")), tag(3, handler(2, "ex1")));
    }

    @Test
    public void testTryCatchBranchOutOfTag() {
        // expressionTag {
        //   try {
        //     if(arg0) goto lbl;
        //     A
        //   } catch ex1 {
        //     B
        //   }
        // }
        // lbl:
        BasicInterpreter root = parseNode("tryCatchBranchOutOfTag", b -> {
            b.beginRoot();

            b.beginBlock();
            BytecodeLabel lbl = b.createLabel();

            b.beginTag(ExpressionTag.class);
            b.beginTryCatch();
                b.beginBlock();
                    emitBranchIf(b, 0, lbl);
                    emitNop(b, "A");
                b.endBlock();
                emitNop(b, "B");
            b.endTryCatch();
            b.endTag(ExpressionTag.class);

            b.emitLabel(lbl);
            b.endBlock();

            b.endRoot();
        });
        assertEquals(null, root.getCallTarget().call(true));
        assertEquals(null, root.getCallTarget().call(false));
        assertHandlers(root, handler(0));

        root.getRootNodes().update(createBytecodeConfigBuilder().addTag(ExpressionTag.class).build());
        assertEquals(null, root.getCallTarget().call(true));
        assertEquals(null, root.getCallTarget().call(false));
        assertHandlers(root, tag(1, handler(0, "ex1")), tag(3, handler(2, "ex1")));
    }

    @Test
    public void testTryCatchBranchWithinTag() {
        // This test is like the previous, but the branch targets a label inside the tag, so we don't need to emit multiple ranges for the try-catch.

        // expressionTag {
        //   try {
        //     if(arg0) goto lbl;
        //     A
        //   } catch ex1 {
        //     B
        //   }
        //   lbl:
        // }
        BasicInterpreter root = parseNode("tryCatchBranchWithinTag", b -> {
            b.beginRoot();
            b.beginTag(ExpressionTag.class);
            b.beginBlock();
            BytecodeLabel lbl = b.createLabel();

            b.beginTryCatch();
                b.beginBlock();
                    emitBranchIf(b, 0, lbl);
                    emitNop(b, "A");
                b.endBlock();
                emitNop(b, "B");
            b.endTryCatch();

            b.emitLabel(lbl);
            b.endBlock();
            b.endTag(ExpressionTag.class);
            b.endRoot();
        });
        assertEquals(null, root.getCallTarget().call(true));
        assertEquals(null, root.getCallTarget().call(false));
        assertHandlers(root, handler(0));

        root.getRootNodes().update(createBytecodeConfigBuilder().addTag(ExpressionTag.class).build());
        assertEquals(null, root.getCallTarget().call(true));
        assertEquals(null, root.getCallTarget().call(false));
        assertHandlers(root, tag(1, handler(0)));
    }

    @Test
    public void testTryFinally() {
        // try {
        //   A
        // } finally {
        //   B
        // }
        BasicInterpreter root = parseNode("finallyTry", b -> {
            b.beginRoot();
            b.beginTryFinally(() -> emitNop(b, "B"));
                emitNop(b, "A");
            b.endTryFinally();
            b.endRoot();
        });
        assertEquals(null, root.getCallTarget().call());
        assertHandlers(root, handler(0));
    }

    @Test
    public void testTryFinallyEarlyExitsInTry() {
        // try {
        //   A
        //   if (arg0) return 42
        //   B
        //   if (arg1) goto lbl
        //   C
        // } finally ex1 {
        //   D
        // }
        // lbl:
        BasicInterpreter root = parseNode("finallyTry", b -> {
            b.beginRoot();
            b.beginBlock();
            BytecodeLabel lbl = b.createLabel();

            b.beginTryFinally(() -> emitNop(b, "D"));
                b.beginBlock();
                    emitNop(b, "A");
                    emitReturnIf(b, 0, 42);
                    emitNop(b, "B");
                    emitBranchIf(b, 1, lbl);
                    emitNop(b, "C");
                b.endBlock();
            b.endTryFinally();

            b.emitLabel(lbl);
            b.endBlock();
            b.endRoot();
        });
        assertEquals(null, root.getCallTarget().call(false, false));
        assertEquals(42L, root.getCallTarget().call(true, false));
        assertEquals(null, root.getCallTarget().call(false, true));
        assertHandlers(root, handler(0, "ex1"), handler(1, "ex1"), handler(2, "ex1"));
    }

    @Test
    public void testTryFinallyEarlyExitsInCatch() {
        // try {
        //   A
        // } finally ex1 {
        //   B
        //   if (arg0) return 42
        //   C
        //   if (arg1) goto lbl
        //   D
        // }
        // lbl:
        BasicInterpreter root = parseNode("finallyTry", b -> {
            b.beginRoot();
            b.beginBlock();
            BytecodeLabel lbl = b.createLabel();

            b.beginTryFinally(() -> {
                b.beginBlock();
                    emitNop(b, "B");
                    emitReturnIf(b, 0, 42);
                    emitNop(b, "C");
                    emitBranchIf(b, 1, lbl);
                    emitNop(b, "D");
                b.endBlock();
            });
                emitNop(b, "A");
            b.endTryFinally();

            b.emitLabel(lbl);
            b.endBlock();
            b.endRoot();
        });
        assertEquals(null, root.getCallTarget().call(false, false));
        assertEquals(42L, root.getCallTarget().call(true, false));
        assertEquals(null, root.getCallTarget().call(false, true));
        assertHandlers(root, handler(0));
    }

    @Test
    public void testTryFinallyNested() {
        // try {
        //   try {
        //     A
        //   } finally ex2 {
        //     B
        //   }
        // } finally ex1 {
        //   C
        // }
        BasicInterpreter root = parseNode("finallyTry", b -> {
            b.beginRoot();
            b.beginTryFinally(() -> emitNop(b, "C"));
                b.beginTryFinally(() -> emitNop(b, "B"));
                    emitNop(b, "A");
                b.endTryFinally();
            b.endTryFinally();
            b.endRoot();
        });
        assertEquals(null, root.getCallTarget().call());
        assertHandlers(root, handler(1, "ex1", handler(0, "ex2")));
    }


    @Test
    public void testTryFinallyNestedEarlyExitsInTry() {
        // try {
        //   try {                          // opens inner + outer
        //     A
        //     if (arg0) return 42          // closes and reopens inner + outer
        //     B
        //     if (arg1) branch outerLbl    // closes and reopens inner + outer
        //     C
        //     if (arg2) branch innerLbl    // closes and reopens inner (*not* outer)
        //     D
        //   } finally ex2 {
        //     E
        //   }
        //   innerLbl:
        //   F
        // } finally ex1 {
        //   G
        // }
        // outerLbl:
        BasicInterpreter root = parseNode("finallyTry", b -> {
            b.beginRoot();
            b.beginBlock();

            BytecodeLabel outerLbl = b.createLabel();
            b.beginTryFinally(() -> emitNop(b, "G"));
                b.beginBlock();
                    BytecodeLabel innerLbl = b.createLabel();
                    b.beginTryFinally(() -> emitNop(b, "E"));
                        b.beginBlock();
                            emitNop(b, "A");
                            emitReturnIf(b, 0, 42);
                            emitNop(b, "B");
                            emitBranchIf(b, 1, outerLbl);
                            emitNop(b, "C");
                            emitBranchIf(b, 2, innerLbl);
                            emitNop(b, "D");
                        b.endBlock();
                    b.endTryFinally();
                    b.emitLabel(innerLbl);
                    emitNop(b, "F");
                b.endBlock();
            b.endTryFinally();

            b.emitLabel(outerLbl);
            b.endBlock();
            b.endRoot();
        });
        assertEquals(null, root.getCallTarget().call(false, false, false));
        assertEquals(42L, root.getCallTarget().call(true, false, false));
        assertEquals(null, root.getCallTarget().call(false, true, false));
        assertEquals(null, root.getCallTarget().call(false, false, true));
        assertHandlers(root,
                        handler(1, "ex1", handler(0, "ex2")),
                        handler(3, "ex1", handler(2, "ex2")),
                        handler(6, "ex1", handler(4, "ex2"), handler(5, "ex2"))
                        );
    }

    @Test
    public void testTryFinallyNestedEarlyExitsInFinally() {
        // try {
        //   try {                          // opens inner + outer
        //     A
        //   } finally ex2 {                // closes inner
        //     B
        //     if (arg0) return 42          // closes and reopens outer
        //     C
        //   }
        // } finally ex1 {
        //   G
        // }
        // outerLbl:
        BasicInterpreter root = parseNode("finallyTryNestedEarlyExitsInFinally", b -> {
            b.beginRoot();
            b.beginTryFinally(() -> emitNop(b, "G"));
                b.beginBlock();
                    b.beginTryFinally(() -> {
                        b.beginBlock();
                            emitNop(b, "B");
                            emitReturnIf(b, 0, 42);
                            emitNop(b, "C");
                        b.endBlock();
                    });
                        emitNop(b, "A");
                    b.endTryFinally();
                b.endBlock();
            b.endTryFinally();
            b.endRoot();
        });

        assertEquals(null, root.getCallTarget().call(false));
        assertEquals(42L, root.getCallTarget().call(true));
        assertHandlers(root,
                        handler(1, "ex1", handler(0, "ex2")),
                        handler(2, "ex1"),
                        handler(3, "ex1")
                        );
    }

    @Test
    public void testContiguousTagRanges() {
        // Contiguous ranges get merged into one.
        // statementTag {
        //   if (arg0) return 42
        //   123
        // }
        BasicInterpreter root = parseNode("contiguousTagRanges", b -> {
            b.beginRoot();

            b.beginTag(StatementTag.class);
            b.beginBlock();
            emitReturnIf(b, 0, 42L);
            b.emitLoadConstant(123L);
            b.endBlock();
            b.endTag(StatementTag.class);
            b.endRoot();
        });
        assertEquals(42L, root.getCallTarget().call(true));
        assertEquals(123L, root.getCallTarget().call(false));
        assertHandlers(root);

        root.getRootNodes().update(createBytecodeConfigBuilder().addTag(StatementTag.class).build());
        assertEquals(42L, root.getCallTarget().call(true));
        assertEquals(123L, root.getCallTarget().call(false));
        assertHandlers(root, tag(0));
    }


    @Test
    public void testContiguousTagsNotMerged() {
        // Though their exception ranges are contiguous, the tag nodes differ, so they should not be merged.
        // statementTag {
        //   A
        // }
        // statementTag {
        //   B
        // }
        BasicInterpreter root = parseNode("contiguousTagsNotMerged", b -> {
            b.beginRoot();

            b.beginTag(StatementTag.class);
            emitNop(b, "A");
            b.endTag(StatementTag.class);

            b.beginTag(StatementTag.class);
            emitNop(b, "B");
            b.endTag(StatementTag.class);
            b.endRoot();
        });
        assertEquals("B", root.getCallTarget().call());
        assertHandlers(root);

        root.getRootNodes().update(createBytecodeConfigBuilder().addTag(StatementTag.class).build());
        assertEquals("B", root.getCallTarget().call());
        assertHandlers(root, tag(0), tag(1));
    }

    // @formatter:on
}
