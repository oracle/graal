/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.function.BiConsumer;

import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.Instruction.Argument;
import com.oracle.truffle.api.bytecode.Instruction.Argument.Kind;
import com.oracle.truffle.api.bytecode.LocalVariable;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;

public class LocalsTest extends AbstractBasicInterpreterTest {

    public LocalsTest(TestRun run) {
        super(run);
    }

    @Test
    public void testBasicLocals() {
        for (int i = 0; i < 100; i++) {
            assertBasicLocals(i);
        }
        assertBasicLocals(1000);
    }

    private void assertBasicLocals(int localCount) {
        // l = 42;
        // return l;
        BasicInterpreter root = parseNode("manyLocals" + localCount, b -> {
            b.beginRoot();

            BytecodeLocal[] locals = new BytecodeLocal[localCount];
            for (int i = 0; i < localCount; i++) {
                locals[i] = b.createLocal("name" + i, "info" + i);
            }

            for (int i = 0; i < localCount; i++) {
                b.beginStoreLocal(locals[i]);
                b.emitLoadConstant((long) i);
                b.endStoreLocal();
            }

            b.beginReturn();
            if (locals.length > 0) {
                b.emitLoadLocal(locals[0]);
            } else {
                b.emitLoadConstant(0L);
            }
            b.endReturn();

            b.endRoot();
        });

        BytecodeNode b = root.getBytecodeNode();

        Instruction last = b.getInstructionsAsList().getLast();
        assertEquals(localCount, b.getLocalCount(0));
        int lastBci = last.getBytecodeIndex();
        assertEquals(localCount, b.getLocalCount(lastBci));
        assertEquals(localCount, b.getLocals().size());

        for (int i = 0; i < localCount; i++) {
            LocalVariable l = b.getLocals().get(i);
            if (run.hasBlockScoping()) {
                assertEquals(0, l.getStartIndex());
                assertEquals(last.getNextBytecodeIndex(), l.getEndIndex());
            } else {
                assertEquals(-1, l.getStartIndex());
                assertEquals(-1, l.getEndIndex());
            }
            assertEquals("name" + i, l.getName());
            assertEquals("info" + i, l.getInfo());
            assertNotNull(l.toString());
        }
        assertEquals(0L, root.getCallTarget().call());
    }

    @Test
    public void testFinally() {
        // @formatter:off
        // l0 = 1;
        // try
        //   l1 = l0
        //   if (true) {
        //     return l1
        //   }
        // } finally {
        //   l2 = false
        // }
        // return l0;
        // @formatter:on
        BasicInterpreter root = parseNode("scopedLocals", b -> {
            b.beginRoot();

            BytecodeLocal l0 = b.createLocal("l0", null);

            // l0 = 1
            b.beginStoreLocal(l0);
            b.emitLoadConstant(1L);
            b.endStoreLocal();

            b.beginTryFinally(() -> {
                // finally block
                b.beginBlock();
                BytecodeLocal l2 = b.createLocal("l2", null);
                b.beginStoreLocal(l2);
                b.emitLoadConstant(false);
                b.endStoreLocal();
                b.endBlock();
            });

            // try block
            b.beginBlock();
            BytecodeLocal l1 = b.createLocal("l1", null);
            b.beginStoreLocal(l1);
            b.emitLoadLocal(l0);
            b.endStoreLocal();
            b.beginIfThen();
            b.emitLoadConstant(true);
            b.beginReturn();
            b.emitLoadLocal(l0);
            b.endReturn();
            b.endIfThen();
            b.emitLoadConstant(123L);
            b.endBlock();

            b.endTryFinally();

            b.beginReturn();
            b.emitLoadLocal(l0);
            b.endReturn();

            b.endRoot();
        });
        root.getBytecodeNode().setUncachedThreshold(0);
        assertEquals(1L, root.getCallTarget().call());

        BytecodeNode b = root.getBytecodeNode();
        List<LocalVariable> locals = b.getLocals();

        if (run.hasBlockScoping()) {
            assertEquals(6, locals.size());
            LocalVariable l0 = locals.get(0);  // can be merged
            LocalVariable l1a = locals.get(1);
            LocalVariable l2a = locals.get(2); // early return handler
            LocalVariable l1b = locals.get(3);
            LocalVariable l2b = locals.get(4); // fallthrough handler
            LocalVariable l2c = locals.get(5); // exceptional handler

            assertEquals("l0", l0.getName());
            assertEquals("l1", l1a.getName());
            assertEquals("l1", l1b.getName());
            assertEquals(l1a.getLocalOffset(), l1b.getLocalOffset());
            assertEquals(l1a.getLocalIndex(), l1b.getLocalIndex());
            assertEquals("l2", l2a.getName());
            assertEquals("l2", l2b.getName());
            assertEquals("l2", l2c.getName());
            assertTrue(l2a.getLocalIndex() != l2b.getLocalIndex());
            assertTrue(l2b.getLocalIndex() != l2c.getLocalIndex());

            if (run.hasBoxingElimination()) {
                assertEquals(FrameSlotKind.Long, l0.getTypeProfile());
                assertEquals(FrameSlotKind.Long, l1a.getTypeProfile());
                assertEquals(FrameSlotKind.Long, l1b.getTypeProfile());
                assertEquals(FrameSlotKind.Boolean, l2a.getTypeProfile());
                // Locals in finally handlers are unique. The fallthrough/exception handlers haven't
                // been hit.
                assertEquals(FrameSlotKind.Illegal, l2b.getTypeProfile());
                assertEquals(FrameSlotKind.Illegal, l2c.getTypeProfile());
            } else {
                assertNull(l0.getTypeProfile());
                assertNull(l1a.getTypeProfile());
                assertNull(l1b.getTypeProfile());
                assertNull(l2a.getTypeProfile());
                assertNull(l2b.getTypeProfile());
                assertNull(l2c.getTypeProfile());
            }

            // Use the load.constant consts to identify which block an instruction belongs to.
            for (Instruction instruction : b.getInstructions()) {
                if (!instruction.getName().equals("load.constant")) {
                    continue;
                }
                for (Argument arg : instruction.getArguments()) {
                    if (arg.getKind() != Kind.CONSTANT) {
                        continue;
                    }
                    Object constant = arg.asConstant();

                    if (constant == Long.valueOf(1L)) {
                        // root block
                        int bci = instruction.getBytecodeIndex();
                        assertEquals(1, b.getLocalCount(bci));
                        assertEquals("l0", b.getLocalName(bci, 0));
                        assertNull(b.getLocalInfo(bci, 0));
                    } else if (constant == Boolean.valueOf(true) || constant == Long.valueOf(123L)) {
                        // try block
                        int bci = instruction.getBytecodeIndex();
                        assertEquals(2, b.getLocalCount(bci));
                        assertEquals("l0", b.getLocalName(bci, 0));
                        assertNull(b.getLocalInfo(bci, 0));
                        assertEquals("l1", b.getLocalName(bci, 1));
                        assertNull(b.getLocalInfo(bci, 1));
                    } else if (constant == Boolean.valueOf(false)) {
                        // finally block
                        int bci = instruction.getBytecodeIndex();
                        assertEquals(2, b.getLocalCount(bci));
                        assertEquals("l0", b.getLocalName(bci, 0));
                        assertNull(b.getLocalInfo(bci, 0));
                        assertEquals("l2", b.getLocalName(bci, 1));
                        assertNull(b.getLocalInfo(bci, 1));
                    } else {
                        fail("Unexpected constant " + constant);
                    }
                }
            }
        } else {
            assertEquals(5, locals.size());
            LocalVariable l0 = locals.get(0);
            LocalVariable l1 = locals.get(1);
            LocalVariable l2a = locals.get(2); // early return handler
            LocalVariable l2b = locals.get(3); // fallthrough handler
            LocalVariable l2c = locals.get(4); // exceptional handler
            assertEquals("l0", l0.getName());
            assertEquals("l1", l1.getName());
            assertEquals("l2", l2a.getName());
            assertEquals("l2", l2b.getName());
            assertEquals("l2", l2c.getName());
        }
    }

    @Test
    public void testScopedLocals() {
        // @formatter:off
        // // B0
        // l0 = 1;
        // { // B1
        //   l1 = l0
        // }
        // l2 = 42
        // { // B2
        //   l3 = l0
        //   { // B3
        //     l4 = l3
        //     l3 = l2
        //   }
        //   l0 = l3
        // }
        // return l0
        // @formatter:on
        BasicInterpreter root = parseNode("scopedLocals", b -> {
            b.beginRoot();

            // l0 = 1
            BytecodeLocal l0 = b.createLocal("l0", null);
            b.beginStoreLocal(l0);
            b.emitLoadConstant(1L);
            b.endStoreLocal();

            b.beginBlock();
            // l1 = l0
            BytecodeLocal l1 = b.createLocal("l1", null);
            b.beginStoreLocal(l1);
            b.emitLoadLocal(l0);
            b.endStoreLocal();

            b.endBlock();

            // l2 = 42
            BytecodeLocal l2 = b.createLocal("l2", null);
            b.beginStoreLocal(l2);
            b.emitLoadConstant(42L);
            b.endStoreLocal();

            b.beginBlock();
            // l3 = l0
            BytecodeLocal l3 = b.createLocal("l3", null);
            b.beginStoreLocal(l3);
            b.emitLoadLocal(l0);
            b.endStoreLocal();

            b.beginBlock();

            // l4 = l3
            BytecodeLocal l4 = b.createLocal("l4", null);
            b.beginStoreLocal(l4);
            b.emitLoadLocal(l3);
            b.endStoreLocal();

            // l3 = l2
            b.beginStoreLocal(l3);
            b.emitLoadLocal(l2);
            b.endStoreLocal();

            b.endBlock();

            // l0 = l3
            b.beginStoreLocal(l0);
            b.emitLoadLocal(l3);
            b.endStoreLocal();

            b.endBlock();

            // return l0
            b.beginReturn();
            b.emitLoadLocal(l0);
            b.endReturn();

            b.endRoot();
        });

        BytecodeNode b = root.getBytecodeNode();
        List<Instruction> instructions = b.getInstructionsAsList();
        Instruction last = b.getInstructionsAsList().getLast();
        int endBci = last.getNextBytecodeIndex();
        List<LocalVariable> locals = b.getLocals();
        assertEquals(5, locals.size());
        assertEquals(42L, root.getCallTarget().call());
        if (run.hasBlockScoping()) {
            assertEquals(0, locals.get(0).getStartIndex());
            assertEquals(endBci, locals.get(0).getEndIndex());
            assertEquals("l0", locals.get(0).getName());

            assertEquals(instructions.get(2).getBytecodeIndex(), locals.get(1).getStartIndex());
            assertEquals(instructions.get(4).getBytecodeIndex(), locals.get(1).getEndIndex());
            assertEquals("l1", locals.get(1).getName());

            assertEquals(instructions.get(5).getBytecodeIndex(), locals.get(2).getStartIndex());
            assertEquals(endBci, locals.get(2).getEndIndex());
            assertEquals("l2", locals.get(2).getName());
            // l1 and l2 should use the same frame slot.
            assertEquals(locals.get(1).getLocalOffset(), locals.get(2).getLocalOffset());

            assertEquals(instructions.get(7).getBytecodeIndex(), locals.get(3).getStartIndex());
            assertEquals(instructions.get(16).getBytecodeIndex(), locals.get(3).getEndIndex());
            assertEquals("l3", locals.get(3).getName());

            assertEquals(instructions.get(9).getBytecodeIndex(), locals.get(4).getStartIndex());
            assertEquals(instructions.get(13).getBytecodeIndex(), locals.get(4).getEndIndex());
            assertEquals("l4", locals.get(4).getName());

            int bci;

            // B0
            bci = 0;
            assertEquals(1, b.getLocalCount(bci));
            assertEquals("l0", b.getLocalName(bci, 0));
            assertNull(b.getLocalInfo(bci, 0));

            bci = instructions.get(4).getBytecodeIndex();
            assertEquals(1, b.getLocalCount(bci));
            assertEquals("l0", b.getLocalName(bci, 0));
            assertNull(b.getLocalInfo(bci, 0));

            bci = instructions.get(5).getBytecodeIndex();
            assertEquals(2, b.getLocalCount(bci));
            assertEquals("l0", b.getLocalName(bci, 0));
            assertEquals("l2", b.getLocalName(bci, 1));
            assertNull(b.getLocalInfo(bci, 0));
            assertNull(b.getLocalInfo(bci, 1));

            bci = last.getBytecodeIndex();
            assertEquals(2, b.getLocalCount(bci));
            assertEquals("l0", b.getLocalName(bci, 0));
            assertEquals("l2", b.getLocalName(bci, 1));
            assertNull(b.getLocalInfo(bci, 0));
            assertNull(b.getLocalInfo(bci, 1));

            // B1
            bci = instructions.get(1).getBytecodeIndex();
            assertEquals(1, b.getLocalCount(bci));
            assertEquals("l0", b.getLocalName(bci, 0));
            assertNull(b.getLocalInfo(bci, 0));

            bci = instructions.get(2).getBytecodeIndex();
            assertEquals(2, b.getLocalCount(bci));
            assertEquals("l0", b.getLocalName(bci, 0));
            assertEquals("l1", b.getLocalName(bci, 1));
            assertNull(b.getLocalInfo(bci, 0));
            assertNull(b.getLocalInfo(bci, 1));

            bci = instructions.get(4).getBytecodeIndex();
            assertEquals(1, b.getLocalCount(bci));
            assertEquals("l0", b.getLocalName(bci, 0));
            assertNull(b.getLocalInfo(bci, 0));

            // B2
            bci = instructions.get(6).getBytecodeIndex();
            assertEquals(2, b.getLocalCount(bci));
            assertEquals("l0", b.getLocalName(bci, 0));
            assertEquals("l2", b.getLocalName(bci, 1));
            assertNull(b.getLocalInfo(bci, 0));
            assertNull(b.getLocalInfo(bci, 1));

            bci = instructions.get(8).getBytecodeIndex();
            assertEquals(3, b.getLocalCount(bci));
            assertEquals("l0", b.getLocalName(bci, 0));
            assertEquals("l2", b.getLocalName(bci, 1));
            assertEquals("l3", b.getLocalName(bci, 2));
            assertNull(b.getLocalInfo(bci, 0));
            assertNull(b.getLocalInfo(bci, 1));
            assertNull(b.getLocalInfo(bci, 2));

            bci = instructions.get(15).getBytecodeIndex();
            assertEquals(3, b.getLocalCount(bci));
            assertEquals("l0", b.getLocalName(bci, 0));
            assertEquals("l2", b.getLocalName(bci, 1));
            assertEquals("l3", b.getLocalName(bci, 2));
            assertNull(b.getLocalInfo(bci, 0));
            assertNull(b.getLocalInfo(bci, 1));
            assertNull(b.getLocalInfo(bci, 2));

            bci = instructions.get(16).getBytecodeIndex();
            assertEquals(2, b.getLocalCount(bci));
            assertEquals("l0", b.getLocalName(bci, 0));
            assertEquals("l2", b.getLocalName(bci, 1));
            assertNull(b.getLocalInfo(bci, 0));
            assertNull(b.getLocalInfo(bci, 1));

            // B3
            bci = instructions.get(8).getBytecodeIndex();
            assertEquals(3, b.getLocalCount(bci));
            assertEquals("l0", b.getLocalName(bci, 0));
            assertEquals("l2", b.getLocalName(bci, 1));
            assertEquals("l3", b.getLocalName(bci, 2));
            assertNull(b.getLocalInfo(bci, 0));
            assertNull(b.getLocalInfo(bci, 1));
            assertNull(b.getLocalInfo(bci, 2));

            bci = instructions.get(9).getBytecodeIndex();
            assertEquals(4, b.getLocalCount(bci));
            assertEquals("l0", b.getLocalName(bci, 0));
            assertEquals("l2", b.getLocalName(bci, 1));
            assertEquals("l3", b.getLocalName(bci, 2));
            assertEquals("l4", b.getLocalName(bci, 3));
            assertNull(b.getLocalInfo(bci, 0));
            assertNull(b.getLocalInfo(bci, 1));
            assertNull(b.getLocalInfo(bci, 2));
            assertNull(b.getLocalInfo(bci, 3));

            bci = instructions.get(13).getBytecodeIndex();
            assertEquals(3, b.getLocalCount(bci));
            assertEquals("l0", b.getLocalName(bci, 0));
            assertEquals("l2", b.getLocalName(bci, 1));
            assertEquals("l3", b.getLocalName(bci, 2));
            assertNull(b.getLocalInfo(bci, 0));
            assertNull(b.getLocalInfo(bci, 1));
            assertNull(b.getLocalInfo(bci, 2));

        }
    }

    @Test
    public void testScopedLocals2() {
        // @formatter:off
        // // B0
        // l0 = 42L;
        // {
        //   l1 = ""
        //   l2 = 42L
        // }
        // {
        //   l1 = 42L
        //   l2 = ""
        // }
        // return l0
        // @formatter:on
        BasicInterpreter root = parseNode("scopedLocals2", b -> {
            b.beginRoot();

            BytecodeLocal l0 = b.createLocal("l0", null);
            b.beginStoreLocal(l0);
            b.emitLoadConstant(42L);
            b.endStoreLocal();

            b.beginBlock();

            BytecodeLocal l1 = b.createLocal("l1", null);
            b.beginStoreLocal(l1);
            b.emitLoadConstant("");
            b.endStoreLocal();
            BytecodeLocal l2 = b.createLocal("l2", null);
            b.beginStoreLocal(l2);
            b.emitLoadConstant(42L);
            b.endStoreLocal();

            b.endBlock();

            b.beginBlock();

            l1 = b.createLocal("l1", null);
            b.beginStoreLocal(l1);
            b.emitLoadConstant(42L);
            b.endStoreLocal();
            l2 = b.createLocal("l2", null);
            b.beginStoreLocal(l2);
            b.emitLoadConstant("");
            b.endStoreLocal();

            b.endBlock();

            b.beginReturn();
            b.emitLoadLocal(l0);
            b.endReturn();

            b.endRoot();
        });

        List<LocalVariable> locals = root.getBytecodeNode().getLocals();
        assertEquals(5, locals.size());
        LocalVariable l0 = locals.get(0);
        LocalVariable l1a = locals.get(1);
        LocalVariable l2a = locals.get(2);
        LocalVariable l1b = locals.get(3);
        LocalVariable l2b = locals.get(4);

        assertEquals("l0", l0.getName());
        assertEquals("l1", l1a.getName());
        assertEquals("l2", l2a.getName());
        assertEquals("l1", l1b.getName());
        assertEquals("l2", l2b.getName());

        assertNull(l0.getInfo());
        assertNull(l1a.getInfo());
        assertNull(l2a.getInfo());
        assertNull(l1b.getInfo());
        assertNull(l2b.getInfo());

        assertNotNull(l0.toString());
        assertNotNull(l1a.toString());
        assertNotNull(l2a.toString());
        assertNotNull(l1b.toString());
        assertNotNull(l2b.toString());

        assertNull(l0.getTypeProfile());
        assertNull(l1a.getTypeProfile());
        assertNull(l2a.getTypeProfile());
        assertNull(l1b.getTypeProfile());
        assertNull(l2b.getTypeProfile());

        if (run.hasRootScoping()) {
            assertEquals(0, l0.getLocalOffset());
            assertEquals(1, l1a.getLocalOffset());
            assertEquals(2, l2a.getLocalOffset());
            assertEquals(3, l1b.getLocalOffset());
            assertEquals(4, l2b.getLocalOffset());
        } else {
            assertEquals(0, l0.getLocalOffset());
            assertEquals(1, l1a.getLocalOffset());
            assertEquals(2, l2a.getLocalOffset());
            assertEquals(1, l1b.getLocalOffset());
            assertEquals(2, l2b.getLocalOffset());
        }

        assertEquals(0, l0.getLocalIndex());
        assertEquals(1, l1a.getLocalIndex());
        assertEquals(2, l2a.getLocalIndex());
        assertEquals(3, l1b.getLocalIndex());
        assertEquals(4, l2b.getLocalIndex());

        root.getBytecodeNode().setUncachedThreshold(0);
        assertEquals(42L, root.getCallTarget().call());

        // re-read locals as old
        locals = root.getBytecodeNode().getLocals();
        l0 = locals.get(0);
        l1a = locals.get(1);
        l2a = locals.get(2);
        l1b = locals.get(3);
        l2b = locals.get(4);

        if (run.hasBoxingElimination()) {
            assertEquals(FrameSlotKind.Long, l0.getTypeProfile());
            assertEquals(FrameSlotKind.Object, l1a.getTypeProfile());
            assertEquals(FrameSlotKind.Long, l2a.getTypeProfile());
            assertEquals(FrameSlotKind.Long, l1b.getTypeProfile());
            assertEquals(FrameSlotKind.Object, l2b.getTypeProfile());
        } else {
            // no profile collected if not boxing-eliminated
            assertNull(l0.getTypeProfile());
            assertNull(l1a.getTypeProfile());
            assertNull(l2a.getTypeProfile());
            assertNull(l1b.getTypeProfile());
            assertNull(l2b.getTypeProfile());
        }

        assertEquals(42L, root.getCallTarget().call());

        if (run.hasBoxingElimination()) {
            assertEquals(FrameSlotKind.Long, l0.getTypeProfile());
            assertEquals(FrameSlotKind.Object, l1a.getTypeProfile());
            assertEquals(FrameSlotKind.Long, l2a.getTypeProfile());
            assertEquals(FrameSlotKind.Long, l1b.getTypeProfile());
            assertEquals(FrameSlotKind.Object, l2b.getTypeProfile());
        } else {
            // no profile collected if not boxing-eliminated
            assertNull(l0.getTypeProfile());
            assertNull(l1a.getTypeProfile());
            assertNull(l2a.getTypeProfile());
            assertNull(l1b.getTypeProfile());
            assertNull(l2b.getTypeProfile());
        }
    }

    @Test
    public void testMaterializedAccessUpdatesTag() {
        // @formatter:off
        // def outer(materializeFrame):
        //   x = 42L
        //   def inner(newValue):
        //     x = newValue;
        //   return materializeFrame ? materialize() : x
        // @formatter:on
        BytecodeRootNodes<BasicInterpreter> roots = createNodes(BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();

            BytecodeLocal x = b.createLocal("x", null);
            b.beginStoreLocal(x);
            b.emitLoadConstant(42L);
            b.endStoreLocal();

            b.beginRoot();
            b.beginStoreLocalMaterialized(x);
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endStoreLocalMaterialized();
            b.endRoot();

            b.beginReturn();
            b.beginConditional();
            b.emitLoadArgument(0);
            b.emitMaterializeFrame();
            b.emitLoadLocal(x);
            b.endConditional();
            b.endReturn();

            b.endRoot();
        });
        BasicInterpreter outer = roots.getNode(0);
        BasicInterpreter inner = roots.getNode(1);

        List<LocalVariable> locals = outer.getBytecodeNode().getLocals();
        assertEquals(1, locals.size());
        LocalVariable x = locals.get(0);
        assertEquals("x", x.getName());
        assertNull(x.getTypeProfile());

        // force cached
        outer.getBytecodeNode().setUncachedThreshold(0);

        assertEquals(42L, outer.getCallTarget().call(false));
        if (run.hasBoxingElimination()) {
            // The tag should be updated.
            assertEquals(FrameSlotKind.Long, outer.getBytecodeNode().getLocals().get(0).getTypeProfile());
        } else {
            assertNull(outer.getBytecodeNode().getLocals().get(0).getTypeProfile());
        }

        MaterializedFrame outerFrame = (MaterializedFrame) outer.getCallTarget().call(true);
        if (run.hasBoxingElimination()) {
            // The tag should stay the same.
            inner.getCallTarget().call(outerFrame, 123L);
            assertEquals(FrameSlotKind.Long, outer.getBytecodeNode().getLocals().get(0).getTypeProfile());
            // If we use a different type, it should reset the tag to Object.
            inner.getCallTarget().call(outerFrame, "hello");
            assertEquals(FrameSlotKind.Object, outer.getBytecodeNode().getLocals().get(0).getTypeProfile());
        } else {
            assertNull(outer.getBytecodeNode().getLocals().get(0).getTypeProfile());
        }

        // Outer should still execute even with updated tags.
        assertEquals(42L, outer.getCallTarget().call(false));
    }

    @Test
    public void testIllegalOrDefault() {
        // @formatter:off
        // // B0
        // result;
        // {
        //   var l0;
        //   if (arg0) {
        //     result = l0
        //   } else {
        //     l0 = 42L
        //   }
        // }
        // {
        //   var l1;
        //   result = l1;
        // }
        // return result
        // @formatter:on
        BasicInterpreter root = parseNode("illegalDefaults", b -> {
            b.beginRoot();

            BytecodeLocal result = b.createLocal("result", null);
            b.beginBlock();
            BytecodeLocal l = b.createLocal("l0", null);
            b.beginIfThenElse();
            b.emitLoadArgument(0);
            b.beginStoreLocal(result);
            b.emitLoadLocal(l);
            b.endStoreLocal();
            b.beginStoreLocal(l);
            b.emitLoadConstant(42L);
            b.endStoreLocal();
            b.endIfThenElse();
            b.endBlock();

            b.beginBlock();
            l = b.createLocal("l1", null);
            b.beginStoreLocal(result);
            b.emitLoadLocal(l);
            b.endStoreLocal();
            b.endBlock();

            b.beginReturn();
            b.emitLoadLocal(result);
            b.endReturn();

            b.endRoot();
        });

        Object defaultLocal = this.run.getDefaultLocalValue();
        if (defaultLocal == null) {
            assertThrows(FrameSlotTypeException.class, () -> {
                root.getCallTarget().call(false);
            });
            assertThrows(FrameSlotTypeException.class, () -> {
                root.getCallTarget().call(true);
            });
            root.getBytecodeNode().setUncachedThreshold(0);
            assertThrows(FrameSlotTypeException.class, () -> {
                root.getCallTarget().call(false);
            });
            assertThrows(FrameSlotTypeException.class, () -> {
                root.getCallTarget().call(true);
            });
        } else {
            assertSame(defaultLocal, root.getCallTarget().call(true));
            assertSame(defaultLocal, root.getCallTarget().call(false));
            root.getBytecodeNode().setUncachedThreshold(0);
            assertSame(defaultLocal, root.getCallTarget().call(true));
            assertSame(defaultLocal, root.getCallTarget().call(false));
        }

    }

    private <T extends BasicInterpreterBuilder> void assertParseFailure(BytecodeParser<T> parser) {
        assertThrows(IllegalArgumentException.class, () -> parseNode("invalid", parser));
    }

    private static <T extends BasicInterpreterBuilder> BytecodeParser<T> siblingRootsTest(BiConsumer<T, BytecodeLocal> accessGenerator) {
        return b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal("x", null);
            b.emitLoadNull();
            b.endRoot();

            b.beginRoot();
            b.createLocal("y", null);
            accessGenerator.accept(b, x);
            b.endRoot();
        };
    }

    private static <T extends BasicInterpreterBuilder> BytecodeParser<T> nestedRootsInnerAccessTest(BiConsumer<T, BytecodeLocal> accessGenerator) {
        return b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal("x", null);

            b.beginRoot(); // inner
            b.createLocal("y", null);
            accessGenerator.accept(b, x);
            b.endRoot();

            b.endRoot();
        };
    }

    private static <T extends BasicInterpreterBuilder> BytecodeParser<T> nestedRootsOuterAccessTest(BiConsumer<T, BytecodeLocal> accessGenerator) {
        return b -> {
            b.beginRoot();
            b.createLocal("x", null);

            b.beginRoot(); // inner
            BytecodeLocal y = b.createLocal("y", null);
            b.endRoot();

            accessGenerator.accept(b, y);
            b.endRoot();
        };
    }

    private static <T extends BasicInterpreterBuilder> BytecodeParser<T> outOfScopeTest(BiConsumer<T, BytecodeLocal> accessGenerator) {
        return b -> {
            b.beginRoot();
            b.beginBlock();
            BytecodeLocal x = b.createLocal("x", null);
            b.endBlock();
            b.beginBlock();
            accessGenerator.accept(b, x);
            b.endBlock();
            b.endRoot();
        };
    }

    private static <T extends BasicInterpreterBuilder> void loadLocal(T b, BytecodeLocal local) {
        b.emitLoadLocal(local);
    }

    private static <T extends BasicInterpreterBuilder> void storeLocal(T b, BytecodeLocal local) {
        b.beginStoreLocal(local);
        b.emitLoadNull();
        b.endStoreLocal();
    }

    private static <T extends BasicInterpreterBuilder> void teeLocal(T b, BytecodeLocal local) {
        b.beginTeeLocal(local);
        b.emitLoadNull();
        b.endTeeLocal();
    }

    private static <T extends BasicInterpreterBuilder> void teeLocalRange(T b, BytecodeLocal local) {
        b.beginTeeLocalRange(new BytecodeLocal[]{local});
        b.emitLoadNull();
        b.endTeeLocalRange();
    }

    @Test
    public void testInvalidLocalAccesses() {
        assertParseFailure(siblingRootsTest(LocalsTest::loadLocal));
        assertParseFailure(siblingRootsTest(LocalsTest::storeLocal));
        assertParseFailure(siblingRootsTest(LocalsTest::teeLocal));
        assertParseFailure(siblingRootsTest(LocalsTest::teeLocalRange));

        assertParseFailure(nestedRootsInnerAccessTest(LocalsTest::loadLocal));
        assertParseFailure(nestedRootsInnerAccessTest(LocalsTest::storeLocal));
        assertParseFailure(nestedRootsInnerAccessTest(LocalsTest::teeLocal));
        assertParseFailure(nestedRootsInnerAccessTest(LocalsTest::teeLocalRange));

        assertParseFailure(nestedRootsOuterAccessTest(LocalsTest::loadLocal));
        assertParseFailure(nestedRootsOuterAccessTest(LocalsTest::storeLocal));
        assertParseFailure(nestedRootsOuterAccessTest(LocalsTest::teeLocal));
        assertParseFailure(nestedRootsOuterAccessTest(LocalsTest::teeLocalRange));

        if (run.hasBlockScoping()) {
            assertParseFailure(outOfScopeTest(LocalsTest::loadLocal));
            assertParseFailure(outOfScopeTest(LocalsTest::storeLocal));
            assertParseFailure(outOfScopeTest(LocalsTest::teeLocal));
            assertParseFailure(outOfScopeTest(LocalsTest::teeLocalRange));
        }
    }

    private static <T extends BasicInterpreterBuilder> BytecodeParser<T> outOfScopeDifferentRootsTest(BiConsumer<T, BytecodeLocal> accessGenerator) {
        return b -> {
            b.beginRoot();
            b.beginBlock();
            BytecodeLocal x = b.createLocal("x", null);
            b.endBlock();
            b.beginBlock();
            b.createLocal("y", null);

            b.beginRoot(); // x is out of scope when inner root declared
            accessGenerator.accept(b, x);
            b.endRoot();

            b.endBlock();
            b.endRoot();
        };
    }

    private static <T extends BasicInterpreterBuilder> void loadLocalMaterialized(T b, BytecodeLocal local) {
        b.beginLoadLocalMaterialized(local);
        b.emitMaterializeFrame(); // uses current frame
        b.endLoadLocalMaterialized();
    }

    private static <T extends BasicInterpreterBuilder> void storeLocalMaterialized(T b, BytecodeLocal local) {
        b.beginStoreLocalMaterialized(local);
        b.emitMaterializeFrame(); // uses current frame
        b.emitLoadNull();
        b.endStoreLocalMaterialized();
    }

    @Test
    public void testInvalidMaterializedLocalAccesses() {
        assertParseFailure(siblingRootsTest(LocalsTest::loadLocalMaterialized));
        assertParseFailure(siblingRootsTest(LocalsTest::storeLocalMaterialized));

        // At run time we should fail if the wrong frame is passed.
        BasicInterpreter root1 = createNodes(BytecodeConfig.DEFAULT, nestedRootsInnerAccessTest(LocalsTest::loadLocalMaterialized)).getNode(1);
        assertThrows(IllegalArgumentException.class, () -> root1.getCallTarget().call());
        BasicInterpreter root2 = createNodes(BytecodeConfig.DEFAULT, nestedRootsInnerAccessTest(LocalsTest::storeLocalMaterialized)).getNode(1);
        assertThrows(IllegalArgumentException.class, () -> root2.getCallTarget().call());

        if (run.hasBlockScoping()) {
            assertParseFailure(outOfScopeTest(LocalsTest::loadLocalMaterialized));
            assertParseFailure(outOfScopeTest(LocalsTest::storeLocalMaterialized));

            assertParseFailure(outOfScopeDifferentRootsTest(LocalsTest::loadLocalMaterialized));
            assertParseFailure(outOfScopeDifferentRootsTest(LocalsTest::storeLocalMaterialized));

            if (run.storesBciInFrame()) {
                // At run time we should fail if the local is not in scope.
                BytecodeRootNodes<BasicInterpreter> roots = createNodes(BytecodeConfig.DEFAULT, b -> {
                    b.beginRoot();
                    b.beginBlock();
                    BytecodeLocal x = b.createLocal("x", null);
                    b.beginStoreLocal(x);
                    b.emitLoadConstant(42L);
                    b.endStoreLocal();

                    b.beginRoot(); // x is statically in scope
                    b.beginLoadLocalMaterialized(x);
                    b.emitLoadArgument(0);
                    b.endLoadLocalMaterialized();
                    b.endRoot();

                    b.beginRoot(); // x is statically in scope
                    b.beginStoreLocalMaterialized(x);
                    b.emitLoadArgument(0);
                    b.emitLoadNull();
                    b.endStoreLocalMaterialized();
                    b.endRoot();

                    b.endBlock();
                    b.beginBlock();
                    b.createLocal("y", null);
                    b.emitMaterializeFrame(); // x is out of scope in this frame
                    b.endBlock();
                    b.endRoot();
                });
                MaterializedFrame outerFrame = (MaterializedFrame) roots.getNode(0).getCallTarget().call();
                assertThrows(IllegalArgumentException.class, () -> roots.getNode(1).getCallTarget().call(outerFrame));
                assertThrows(IllegalArgumentException.class, () -> roots.getNode(2).getCallTarget().call(outerFrame));
            }
        }
    }

}
