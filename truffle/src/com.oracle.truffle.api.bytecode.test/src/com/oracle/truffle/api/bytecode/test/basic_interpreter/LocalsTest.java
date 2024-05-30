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
package com.oracle.truffle.api.bytecode.test.basic_interpreter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.LocalVariable;
import com.oracle.truffle.api.frame.FrameSlotKind;

public class LocalsTest extends AbstractBasicInterpreterTest {

    @Test
    public void testBasicLocals() {
        for (int i = 0; i < 100; i++) {
            assertBasicLocals(i);
        }
        assertBasicLocals(3000);
    }

    private void assertBasicLocals(int localCount) {
        // l = 42;
        // return l;
        BasicInterpreter root = parseNode("manyLocals", b -> {
            b.beginRoot(LANGUAGE);

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
            if (run.hasLocalScopes()) {
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
        //   if (true) {
        //     return l0
        //   }
        // } finally {
        //   l1 = 42
        // }
        // return l0;
        // @formatter:on
        BasicInterpreter root = parseNode("scopedLocals", b -> {
            b.beginRoot(LANGUAGE);

            BytecodeLocal l0 = b.createLocal("l0", null);
            BytecodeLocal e = b.createLocal("e", null);

            // l0 = 1
            b.beginStoreLocal(l0);
            b.emitLoadConstant(1L);
            b.endStoreLocal();

            b.beginFinallyTry(e);
            // finally block
            b.beginBlock();
            BytecodeLocal l1 = b.createLocal("l1", null);
            b.beginStoreLocal(l1);
            b.emitLoadConstant(42L);
            b.endStoreLocal();
            b.endBlock();

            // if block
            b.beginIfThen();
            b.emitLoadConstant(true);
            b.beginReturn();
            b.emitLoadLocal(l0);
            b.endReturn();
            b.endIfThen();

            b.endFinallyTry();

            b.beginReturn();
            b.emitLoadLocal(l0);
            b.endReturn();

            b.endRoot();
        });

        // TODO proper assertions when properly implemented

        assertEquals(1L, root.getCallTarget().call());
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
            b.beginRoot(LANGUAGE);

            // l0 = 1
            BytecodeLocal l0 = b.createLocal("l0", null);
            b.beginStoreLocal(l0);
            b.emitLoadConstant(1L);
            b.endStoreLocal();

            b.beginBlock();
            // l1 = l0
            BytecodeLocal l1 = b.createLocal("l1", null);
            b.beginStoreLocal(l1);
            b.emitLoadLocal(l1);
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
        if (run.hasLocalScopes()) {
            assertEquals(0, locals.get(0).getStartIndex());
            assertEquals(endBci, locals.get(0).getEndIndex());
            assertEquals("l0", locals.get(0).getName());

            assertEquals(instructions.get(2).getBytecodeIndex(), locals.get(1).getStartIndex());
            assertEquals(instructions.get(4).getBytecodeIndex(), locals.get(1).getEndIndex());
            assertEquals("l1", locals.get(1).getName());

            assertEquals(instructions.get(5).getBytecodeIndex(), locals.get(2).getStartIndex());
            assertEquals(endBci, locals.get(2).getEndIndex());
            assertEquals("l2", locals.get(2).getName());

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
    public void testFrameSlotKind() {
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
        BasicInterpreter root = parseNode("scopedLocals", b -> {
            b.beginRoot(LANGUAGE);

            BytecodeLocal l0 = b.createLocal("l0", null);
            b.beginStoreLocal(l0);
            b.emitLoadConstant(42L);
            b.endStoreLocal();

            {
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
            }
            {
                b.beginBlock();

                BytecodeLocal l1 = b.createLocal("l1", null);
                b.beginStoreLocal(l1);
                b.emitLoadConstant(42L);
                b.endStoreLocal();
                BytecodeLocal l2 = b.createLocal("l2", null);
                b.beginStoreLocal(l2);
                b.emitLoadConstant("");
                b.endStoreLocal();

                b.endBlock();
            }

            b.beginReturn();
            b.emitLoadLocal(l0);
            b.endReturn();

            b.endRoot();
        });

        List<LocalVariable> locals = root.getBytecodeNode().getLocals();
        assertEquals(5, locals.size());
        LocalVariable l0 = locals.get(0);
        LocalVariable l1_0 = locals.get(1);
        LocalVariable l2_0 = locals.get(2);
        LocalVariable l1_1 = locals.get(3);
        LocalVariable l2_2 = locals.get(4);

        assertEquals("l0", l0.getName());
        assertEquals("l1", l1_0.getName());
        assertEquals("l2", l2_0.getName());
        assertEquals("l1", l1_1.getName());
        assertEquals("l2", l2_2.getName());

        assertNull(l0.getInfo());
        assertNull(l1_0.getInfo());
        assertNull(l2_0.getInfo());
        assertNull(l1_1.getInfo());
        assertNull(l2_2.getInfo());

        assertNotNull(l0.toString());
        assertNotNull(l1_0.toString());
        assertNotNull(l2_0.toString());
        assertNotNull(l1_1.toString());
        assertNotNull(l2_2.toString());

        assertNull(l0.getTypeProfile());
        assertNull(l1_0.getTypeProfile());
        assertNull(l2_0.getTypeProfile());
        assertNull(l1_1.getTypeProfile());
        assertNull(l2_2.getTypeProfile());

        if (run.hasGlobalScopes()) {
            assertEquals(0, l0.getLocalOffset());
            assertEquals(1, l1_0.getLocalOffset());
            assertEquals(2, l2_0.getLocalOffset());
            assertEquals(3, l1_1.getLocalOffset());
            assertEquals(4, l2_2.getLocalOffset());
        } else {
            assertEquals(0, l0.getLocalOffset());
            assertEquals(1, l1_0.getLocalOffset());
            assertEquals(2, l2_0.getLocalOffset());
            assertEquals(1, l1_1.getLocalOffset());
            assertEquals(2, l2_2.getLocalOffset());
        }

        assertEquals(0, l0.getLocalIndex());
        assertEquals(1, l1_0.getLocalIndex());
        assertEquals(2, l2_0.getLocalIndex());
        assertEquals(3, l1_1.getLocalIndex());
        assertEquals(4, l2_2.getLocalIndex());

        root.getBytecodeNode().setUncachedThreshold(0);
        assertEquals(42L, root.getCallTarget().call());

        // re-read locals as old
        locals = root.getBytecodeNode().getLocals();
        l0 = locals.get(0);
        l1_0 = locals.get(1);
        l2_0 = locals.get(2);
        l1_1 = locals.get(3);
        l2_2 = locals.get(4);

        if (run.hasBoxingElimination()) {
            assertEquals(FrameSlotKind.Long, l0.getTypeProfile());
            assertEquals(FrameSlotKind.Object, l1_0.getTypeProfile());
            assertEquals(FrameSlotKind.Long, l2_0.getTypeProfile());
            assertEquals(FrameSlotKind.Long, l1_1.getTypeProfile());
            assertEquals(FrameSlotKind.Object, l2_2.getTypeProfile());
        } else {
            // no profile collected if not boxing-eliminated
            assertNull(l0.getTypeProfile());
            assertNull(l1_0.getTypeProfile());
            assertNull(l2_0.getTypeProfile());
            assertNull(l1_1.getTypeProfile());
            assertNull(l2_2.getTypeProfile());
        }

        assertEquals(42L, root.getCallTarget().call());

        if (run.hasBoxingElimination()) {
            assertEquals(FrameSlotKind.Long, l0.getTypeProfile());
            assertEquals(FrameSlotKind.Object, l1_0.getTypeProfile());
            assertEquals(FrameSlotKind.Long, l2_0.getTypeProfile());
            assertEquals(FrameSlotKind.Long, l1_1.getTypeProfile());
            assertEquals(FrameSlotKind.Object, l2_2.getTypeProfile());
        } else {
            // no profile collected if not boxing-eliminated
            assertNull(l0.getTypeProfile());
            assertNull(l1_0.getTypeProfile());
            assertNull(l2_0.getTypeProfile());
            assertNull(l1_1.getTypeProfile());
            assertNull(l2_2.getTypeProfile());
        }
    }

}
