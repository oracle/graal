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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;

import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.ForceQuickening;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation.Operator;
import com.oracle.truffle.api.bytecode.test.BoxingEliminationTest.BoxingEliminationTestRootNode.ToBoolean;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public class BoxingEliminationTest extends AbstractInstructionTest {

    protected static final BytecodeDSLTestLanguage LANGUAGE = null;

    @Test
    public void testArgumentAbs() {
        // return - (arg0)
        BoxingEliminationTestRootNode node = (BoxingEliminationTestRootNode) parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAbs();
            b.emitLoadArgument(0);
            b.endAbs();
            b.endReturn();
            b.endRoot();
        }).getRootNode();

        assertInstructions(node,
                        "load.argument",
                        "c.Abs",
                        "return");
        assertQuickenings(node, 0, 0);

        assertEquals(42L, node.getCallTarget().call(42L));
        assertQuickenings(node, 2, 1);

        assertInstructions(node,
                        "load.argument$Long",
                        "c.Abs$GreaterZero",
                        "return");

        assertEquals(42L, node.getCallTarget().call(-42L));
        assertQuickenings(node, 4, 2);

        assertInstructions(node,
                        "load.argument$Long",
                        "c.Abs$GreaterZero#LessThanZero",
                        "return");

        assertEquals("42", node.getCallTarget().call("42"));
        var stable = assertQuickenings(node, 7, 3);

        assertInstructions(node,
                        "load.argument",
                        "c.Abs",
                        "return");

        assertStable(stable, node, 42L);
        assertStable(stable, node, "42");
        assertStable(stable, node, -42L);
    }

    @Test
    public void testArgumentAdd() {
        // return - (arg0)
        BoxingEliminationTestRootNode node = (BoxingEliminationTestRootNode) parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAdd();
            b.beginAbs();
            b.emitLoadArgument(0);
            b.endAbs();
            b.beginAbs();
            b.emitLoadArgument(1);
            b.endAbs();
            b.endAdd();
            b.endReturn();

            b.endRoot();
        }).getRootNode();

        assertInstructions(node,
                        "load.argument",
                        "c.Abs",
                        "load.argument",
                        "c.Abs",
                        "c.Add",
                        "return");

        assertQuickenings(node, 0, 0);

        assertEquals(42L, node.getCallTarget().call(21L, -21L));
        assertQuickenings(node, 7, 3);

        assertInstructions(node,
                        "load.argument$Long",
                        "c.Abs$GreaterZero$unboxed",
                        "load.argument$Long",
                        "c.Abs$LessThanZero$unboxed",
                        "c.Add$Long",
                        "return");

        assertEquals(42L, node.getCallTarget().call(-21L, 21L));

        assertQuickenings(node, 11, 5);
        assertInstructions(node,
                        "load.argument$Long",
                        "c.Abs$GreaterZero#LessThanZero$unboxed",
                        "load.argument$Long",
                        "c.Abs$GreaterZero#LessThanZero$unboxed",
                        "c.Add$Long",
                        "return");

        assertEquals("42", node.getCallTarget().call("4", "2"));
        var stable = assertQuickenings(node, 20, 8);

        assertInstructions(node,
                        "load.argument",
                        "c.Abs",
                        "load.argument",
                        "c.Abs",
                        "c.Add",
                        "return");

        assertStable(stable, node, 21L, -21L);
        assertStable(stable, node, -21L, 21L);
        assertStable(stable, node, "4", "2");
    }

    @Test
    public void testConstantAbs() {
        // return - (arg0)
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAbs();
            b.emitLoadConstant(42L);
            b.endAbs();
            b.endReturn();
            b.endRoot();
        });

        assertInstructions(node,
                        "load.constant",
                        "c.Abs",
                        "return");

        assertEquals(42L, node.getCallTarget().call());

        assertInstructions(node,
                        "load.constant$Long",
                        "c.Abs$GreaterZero",
                        "return");

    }

    @Test
    public void testConditionalConstants0() {
        // return - (arg0)
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAbs();

            b.beginConditional();
            b.emitLoadArgument(0);
            b.emitLoadConstant(-42L);
            b.emitLoadConstant(22L);
            b.endConditional();

            b.endAbs();
            b.endReturn();
            b.endRoot();
        });

        assertInstructions(node,
                        "load.argument",
                        "dup",
                        "branch.false",
                        "load.constant",
                        "branch",
                        "load.constant",
                        "merge.conditional",
                        "c.Abs",
                        "return");

        assertEquals(42L, node.getCallTarget().call(true));

        assertInstructions(node,
                        "load.argument$Boolean",
                        "dup",
                        "branch.false$Boolean",
                        "load.constant$Long",
                        "branch",
                        "load.constant",
                        "merge.conditional$Long$unboxed",
                        "c.Abs$LessThanZero",
                        "return");

        assertEquals(22L, node.getCallTarget().call(false));

        assertInstructions(node,
                        "load.argument$Boolean",
                        "dup",
                        "branch.false$Boolean",
                        "load.constant$Long",
                        "branch",
                        "load.constant$Long",
                        "merge.conditional$Long$unboxed",
                        "c.Abs$GreaterZero#LessThanZero",
                        "return");
    }

    @Test
    public void testConditionalConstants1() {
        // return - (arg0)
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAbs();

            b.beginConditional();
            b.emitLoadArgument(0);
            b.emitLoadConstant(-42L);
            b.emitLoadConstant("42"); // note the string!
            b.endConditional();

            b.endAbs();
            b.endReturn();
            b.endRoot();
        });
        assertInstructions(node,
                        "load.argument",
                        "dup",
                        "branch.false",
                        "load.constant",
                        "branch",
                        "load.constant",
                        "merge.conditional",
                        "c.Abs",
                        "return");

        assertEquals(42L, node.getCallTarget().call(true));

        assertInstructions(node,
                        "load.argument$Boolean",
                        "dup",
                        "branch.false$Boolean",
                        "load.constant$Long",
                        "branch",
                        "load.constant",
                        "merge.conditional$Long$unboxed",
                        "c.Abs$LessThanZero",
                        "return");

        assertEquals("42", node.getCallTarget().call(false));

        assertInstructions(node,
                        "load.argument$Boolean",
                        "dup",
                        "branch.false$Boolean",
                        "load.constant",
                        "branch",
                        "load.constant",
                        "merge.conditional$generic",
                        "c.Abs",
                        "return");
    }

    @Test
    public void testConditionalConstants2() {
        // return - (arg0)
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAbs();

            b.beginConditional();
            b.emitLoadArgument(0);
            b.emitLoadConstant(-42L);
            b.emitLoadConstant("42"); // note the string!
            b.endConditional();

            b.endAbs();
            b.endReturn();
            b.endRoot();
        });
        assertInstructions(node,
                        "load.argument",
                        "dup",
                        "branch.false",
                        "load.constant",
                        "branch",
                        "load.constant",
                        "merge.conditional",
                        "c.Abs",
                        "return");

        assertEquals("42", node.getCallTarget().call(false));
        assertInstructions(node,
                        "load.argument$Boolean",
                        "dup",
                        "branch.false$Boolean",
                        "load.constant",
                        "branch",
                        "load.constant",
                        "merge.conditional$generic",
                        "c.Abs",
                        "return");

        assertEquals(42L, node.getCallTarget().call(true));
        assertInstructions(node,
                        "load.argument$Boolean",
                        "dup",
                        "branch.false$Boolean",
                        "load.constant",
                        "branch",
                        "load.constant",
                        "merge.conditional$generic",
                        "c.Abs",
                        "return");
    }

    @Test
    public void testConditionalUnquickenable() {
        // return arg0 ? { return 42L; 123L } : "not a long";
        /**
         * Regression test: because of the early return, the "child bci" of the positive branch is
         * invalid. We should not try to boxing eliminate the conditional value.
         */
        BoxingEliminationTestRootNode node = (BoxingEliminationTestRootNode) parse(b -> {
            b.beginRoot(LANGUAGE);

            /**
             * Setup: The invalid child bci points to the LoadConstant(42L)'s immediate. Allocate
             * some constants before to make this immediate look like a quickened opcode, and then
             * hit the negative branch so that unquickening is triggered. If it tries to quicken the
             * invalid bci, the immediate will be rewritten to a different constant.
             */
            short unquickenableOpcode = findInstructionOpcode("LOAD_ARGUMENT$LONG");
            for (int i = 1; i <= unquickenableOpcode; i++) {
                b.emitLoadConstant(-i);
            }

            b.beginReturn();
            b.beginConditional();
            b.emitLoadArgument(0);

            b.beginBlock();
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.emitLoadConstant(123L);
            b.endBlock();

            b.emitLoadConstant("not a long");

            b.endConditional();
            b.endReturn();

            b.endRoot();
        }).getRootNode();

        assertEquals("not a long", node.getCallTarget().call(false));
        assertEquals(42L, node.getCallTarget().call(true));

        // Force a reparse to trigger validation and ensure the quickened bytecodes validate.
        node.getRootNodes().ensureSources();
    }

    private static short findInstructionOpcode(String instruction) {
        for (var innerClass : BoxingEliminationTestRootNodeGen.class.getDeclaredClasses()) {
            if (!innerClass.getSimpleName().equals("Instructions")) {
                continue;
            }
            try {
                Field instructionOpcode = innerClass.getDeclaredField(instruction);
                instructionOpcode.setAccessible(true);
                return instructionOpcode.getShort(null);
            } catch (NoSuchFieldException ex) {
                throw new AssertionError("Could not find instruction " + instruction, ex);
            } catch (IllegalAccessException ex) {
                throw new AssertionError("Could not access instruction opcode.", ex);
            }
        }
        throw new AssertionError("Could not find Instructions class");
    }

    @Test
    public void testLocalAbs() {
        // return - (arg0)
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            BytecodeLocal local = b.createLocal();

            b.beginStoreLocal(local);
            b.emitLoadConstant(42L);
            b.endStoreLocal();

            b.beginReturn();
            b.beginAbs();
            b.emitLoadLocal(local);
            b.endAbs();
            b.endReturn();
            b.endRoot();
        });

        assertInstructions(node,
                        "load.constant",
                        "store.local",
                        "load.local",
                        "c.Abs",
                        "return");

        assertEquals(42L, node.getCallTarget().call());

        assertInstructions(node,
                        "load.constant$Long",
                        "store.local$Long$Long",
                        "load.local$Long$unboxed",
                        "c.Abs$GreaterZero",
                        "return");

        assertEquals(42L, node.getCallTarget().call());

        assertInstructions(node,
                        "load.constant$Long",
                        "store.local$Long$Long",
                        "load.local$Long$unboxed",
                        "c.Abs$GreaterZero",
                        "return");

    }

    @Test
    public void testLocalSet() {
        // return - (arg0)
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            BytecodeLocal local = b.createLocal();

            b.beginStoreLocal(local);
            b.emitLoadArgument(0);
            b.endStoreLocal();

            b.beginReturn();
            b.beginAbs();
            b.emitLoadLocal(local);
            b.endAbs();
            b.endReturn();

            b.endRoot();
        });

        assertInstructions(node,
                        "load.argument",
                        "store.local",
                        "load.local",
                        "c.Abs",
                        "return");

        assertEquals(42L, node.getCallTarget().call(-42L));

        assertInstructions(node,
                        "load.argument$Long",
                        "store.local$Long$Long",
                        "load.local$Long$unboxed",
                        "c.Abs$LessThanZero",
                        "return");

        assertEquals("42", node.getCallTarget().call("42"));

        assertInstructions(node,
                        "load.argument",
                        "store.local$generic",
                        "load.local$generic",
                        "c.Abs",
                        "return");

    }

    @Test
    public void testLocalSet2() {
        // return - (arg0)
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            BytecodeLocal local = b.createLocal();

            b.beginStoreLocal(local);
            b.emitLoadArgument(0);
            b.endStoreLocal();

            b.beginStoreLocal(local);
            b.emitLoadArgument(1);
            b.endStoreLocal();

            b.beginReturn();
            b.beginAbs();
            b.emitLoadLocal(local);
            b.endAbs();
            b.endReturn();

            b.endRoot();
        });

        assertInstructions(node,
                        "load.argument",
                        "store.local",
                        "load.argument",
                        "store.local",
                        "load.local",
                        "c.Abs",
                        "return");

        assertEquals(42L, node.getCallTarget().call(Boolean.TRUE, -42L));

        assertInstructions(node,
                        "load.argument$Boolean",
                        "store.local$Boolean$Boolean",
                        "load.argument",
                        "store.local$generic",
                        "load.local$generic",
                        "c.Abs",
                        "return");

        assertEquals("42", node.getCallTarget().call(Boolean.TRUE, "42"));

        assertInstructions(node,
                        "load.argument",
                        "store.local$generic",
                        "load.argument",
                        "store.local$generic",
                        "load.local$generic",
                        "c.Abs",
                        "return");

    }

    @Test
    public void testSpecializedLocalUndefined() {
        // if (arg0) { x = 42 } else { x /* undefined */ }
        // return 123
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            BytecodeLocal x = b.createLocal();

            b.beginIfThenElse();
            b.emitLoadArgument(0);

            b.beginStoreLocal(x);
            b.emitLoadConstant(42);
            b.endStoreLocal();

            b.emitLoadLocal(x);

            b.endIfThenElse();

            b.beginReturn();
            b.emitLoadConstant(123);
            b.endReturn();

            b.endRoot();
        });

        assertInstructions(node,
                        "load.argument",
                        "branch.false",
                        "load.constant",
                        "store.local",
                        "branch",
                        "load.local",
                        "pop",
                        "load.constant",
                        "return");

        assertEquals(123, node.getCallTarget().call(true));

        assertInstructions(node,
                        "load.argument$Boolean",
                        "branch.false$Boolean",
                        "load.constant$Int",
                        "store.local$Int$Int",
                        "branch",
                        "load.local",
                        "pop",
                        "load.constant",
                        "return");

        /**
         * After the first call, the local frame slot is set to Int. During this second call, the
         * "false" branch will run the unquickened load.local, which sees the frame slot and tries
         * to read an int. Since the local is undefined, the int read should fail gracefully, and
         * the load.local should quicken to load.local$generic.
         */
        assertEquals(123, node.getCallTarget().call(false));

        assertInstructions(node,
                        "load.argument$Boolean",
                        "branch.false$Boolean",
                        "load.constant$Int",
                        "store.local$Int$Int",
                        "branch",
                        "load.local$generic",
                        "pop$generic",
                        "load.constant",
                        "return");

        var quickenings = assertQuickenings(node, 7, 4);
        assertStable(quickenings, node, true);
        assertStable(quickenings, node, false);
    }

    @Test
    public void testGetLocals() {
        // local0 = arg0
        // local1 = arg1
        // local2 = arg2
        // return getLocals()
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginStoreLocal(b.createLocal());
            b.emitLoadArgument(0);
            b.endStoreLocal();

            b.beginStoreLocal(b.createLocal());
            b.emitLoadArgument(1);
            b.endStoreLocal();

            b.beginStoreLocal(b.createLocal());
            b.emitLoadArgument(2);
            b.endStoreLocal();

            b.beginReturn();
            b.emitGetLocals();
            b.endReturn();

            b.endRoot();
        });

        assertInstructions(node,
                        "load.argument",
                        "store.local",
                        "load.argument",
                        "store.local",
                        "load.argument",
                        "store.local",
                        "c.GetLocals",
                        "return");

        assertArrayEquals(new Object[]{42L, 123, true}, (Object[]) node.getCallTarget().call(42L, 123, true));

        assertInstructions(node,
                        "load.argument$Long",
                        "store.local$Long$Long",
                        "load.argument$Int",
                        "store.local$Int$Int",
                        "load.argument$Boolean",
                        "store.local$Boolean$Boolean",
                        "c.GetLocals",
                        "return");

        assertArrayEquals(new Object[]{"42", 123, 1024}, (Object[]) node.getCallTarget().call("42", 123, 1024));

        assertInstructions(node,
                        "load.argument",
                        "store.local$generic",
                        "load.argument$Int",
                        "store.local$Int$Int",
                        "load.argument",
                        "store.local$generic",
                        "c.GetLocals",
                        "return");
    }

    @Test
    public void testGetLocal() {
        // local0 = arg0
        // local1 = arg1
        // return getLocal(arg2)
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginStoreLocal(b.createLocal());
            b.emitLoadArgument(0);
            b.endStoreLocal();

            b.beginStoreLocal(b.createLocal());
            b.emitLoadArgument(1);
            b.endStoreLocal();

            b.beginReturn();
            b.beginGetLocal();
            b.emitLoadArgument(2);
            b.endGetLocal();
            b.endReturn();

            b.endRoot();
        });

        assertInstructions(node,
                        "load.argument",
                        "store.local",
                        "load.argument",
                        "store.local",
                        "load.argument",
                        "c.GetLocal",
                        "return");

        assertEquals(42L, node.getCallTarget().call(42L, 123, 0));

        assertInstructions(node,
                        "load.argument$Long",
                        "store.local$Long$Long",
                        "load.argument$Int",
                        "store.local$Int$Int",
                        "load.argument$Int",
                        "c.GetLocal$Perform",
                        "return");

        assertEquals(1024, node.getCallTarget().call(true, 1024, 1));

        assertInstructions(node,
                        "load.argument",
                        "store.local$generic",
                        "load.argument$Int",
                        "store.local$Int$Int",
                        "load.argument$Int",
                        "c.GetLocal$Perform",
                        "return");
    }

    /*
     * Test that if the generic type of a custom node uses boxing eliminate type we automatically
     * quicken.
     */
    @Test
    public void testGenericBoxingElimination() {
        // return - (arg0)
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);
            b.beginReturn();
            b.beginGenericOperationWithLong();
            b.beginGenericOperationWithLong();
            b.emitLoadConstant(-42L);
            b.endGenericOperationWithLong();
            b.endGenericOperationWithLong();
            b.endReturn();
            b.endRoot();

        });

        assertInstructions(node,
                        "load.constant",
                        "c.GenericOperationWithLong",
                        "c.GenericOperationWithLong",
                        "return");

        assertEquals(42L, node.getCallTarget().call());

        var stable = assertQuickenings(node, 4, 2);
        assertInstructions(node,
                        "load.constant$Long",
                        "c.GenericOperationWithLong$Long$unboxed",
                        "c.GenericOperationWithLong$Long",
                        "return");

        assertStable(stable, node);
    }

    @Test
    public void testIfEnd() {
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);
            b.beginIfThen();
            b.emitTrue();
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();

            b.endIfThen();

            b.beginReturn();
            b.emitLoadConstant(41L);
            b.endReturn();

            b.endRoot();

        });

        assertQuickenings(node, 0, 0);

        assertInstructions(node,
                        "c.True",
                        "branch.false",
                        "load.constant",
                        "return",
                        "load.constant",
                        "return");

        assertEquals(42L, node.getCallTarget().call());

        assertInstructions(node,
                        "c.True$unboxed",
                        "branch.false$Boolean",
                        "load.constant",
                        "return",
                        "load.constant",
                        "return");
        var quickenings = assertQuickenings(node, 2, 1);
        assertStable(quickenings, node);
    }

    @Test
    public void testIfEndElse() {
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginIfThenElse();
            b.emitFalse();
            b.beginReturn();
            b.emitLoadConstant(41L);
            b.endReturn();
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endIfThenElse();

            b.endRoot();

        });

        assertQuickenings(node, 0, 0);
        assertInstructions(node,
                        "c.False",
                        "branch.false",
                        "load.constant",
                        "return",
                        "load.constant",
                        "return");

        assertEquals(42L, node.getCallTarget().call());

        assertInstructions(node,
                        "c.False$unboxed",
                        "branch.false$Boolean",
                        "load.constant",
                        "return",
                        "load.constant",
                        "return");
        var quickenings = assertQuickenings(node, 2, 1);
        assertStable(quickenings, node);
    }

    @Test
    public void testWhile() {
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);
            b.beginWhile();
            b.emitTrue();
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endWhile();

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();

            b.endRoot();

        });

        assertQuickenings(node, 0, 0);
        assertInstructions(node,
                        "c.True",
                        "branch.false",
                        "load.constant",
                        "return",
                        "load.constant",
                        "return");

        assertEquals(42L, node.getCallTarget().call());

        assertInstructions(node,
                        "c.True$unboxed",
                        "branch.false$Boolean",
                        "load.constant",
                        "return",
                        "load.constant",
                        "return");

        var quickenings = assertQuickenings(node, 2, 1);
        assertStable(quickenings, node);
    }

    /*
     * Tests that if you switch from a boxing eliminated operand to a non boxing eliminated operand
     * that the boxing elimination is disabled.
     */
    @Test
    public void testSwitchQuickening0() {
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginSwitchQuickening0();
            b.emitLoadArgument(0);
            b.endSwitchQuickening0();
            b.endReturn();

            b.endRoot();

        });

        assertQuickenings(node, 0, 0);
        assertInstructions(node,
                        "load.argument",
                        "c.SwitchQuickening0",
                        "return");

        assertEquals(1L, node.getCallTarget().call(1L));

        assertInstructions(node,
                        "load.argument$Long",
                        "c.SwitchQuickening0$One",
                        "return");

        assertEquals("42", node.getCallTarget().call("42"));

        assertInstructions(node,
                        "load.argument",
                        "c.SwitchQuickening0$NonNull",
                        "return");

        assertEquals(null, node.getCallTarget().call((Object) null));

        assertInstructions(node,
                        "load.argument",
                        "c.SwitchQuickening0$Object",
                        "return");

        var quickenings = assertQuickenings(node, 7, 3);
        assertStable(quickenings, node, "42");
        assertStable(quickenings, node, 1L);
        assertStable(quickenings, node, (Object) null);
    }

    @Test
    public void testSwitchQuickening1() {
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginGenericOperationWithLong();
            b.beginGenericOperationWithLong();
            b.beginSwitchQuickening1();
            b.emitLoadArgument(0);
            b.endSwitchQuickening1();
            b.endGenericOperationWithLong();
            b.endGenericOperationWithLong();
            b.endReturn();

            b.endRoot();
        });

        assertQuickenings(node, 0, 0);
        assertInstructions(node,
                        "load.argument",
                        "c.SwitchQuickening1",
                        "c.GenericOperationWithLong",
                        "c.GenericOperationWithLong",
                        "return");

        assertEquals(1L, node.getCallTarget().call(1L));

        assertInstructions(node,
                        "load.argument$Long",
                        "c.SwitchQuickening1$One$unboxed",
                        "c.GenericOperationWithLong$Long$unboxed",
                        "c.GenericOperationWithLong$Long",
                        "return");

        assertEquals(2L, node.getCallTarget().call(2L));

        assertInstructions(node,
                        "load.argument$Long",
                        // assert that instructions stay unboxed during respecializations
                        "c.SwitchQuickening1$GreaterEqualOne$unboxed",
                        "c.GenericOperationWithLong$Long$unboxed",
                        "c.GenericOperationWithLong$Long",
                        "return");

        var quickenings = assertQuickenings(node, 8, 4);
        assertStable(quickenings, node, 1L);
    }

    @Test
    public void testSwitchQuickening2() {
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginPassLongOrInt();
            b.beginPassLongOrInt();
            b.beginLongToInt();
            b.emitLoadArgument(0);
            b.endLongToInt();
            b.endPassLongOrInt();
            b.endPassLongOrInt();
            b.endReturn();

            b.endRoot();

        });

        assertQuickenings(node, 0, 0);
        assertInstructions(node,
                        "load.argument",
                        "c.LongToInt",
                        "c.PassLongOrInt",
                        "c.PassLongOrInt",
                        "return");

        assertEquals(1L, node.getCallTarget().call(1L));

        assertInstructions(node,
                        "load.argument$Long",
                        "c.LongToInt$One$unboxed",
                        "c.PassLongOrInt$Long$unboxed",
                        "c.PassLongOrInt$Long",
                        "return");

        assertEquals(2, node.getCallTarget().call(2L));

        assertInstructions(node,
                        "load.argument$Long",
                        "c.LongToInt$GreaterEqualOne$unboxed",
                        // test that this is unboxed even if
                        // it was previously specialized to long
                        "c.PassLongOrInt$Int$unboxed",
                        "c.PassLongOrInt$Int",
                        "return");

        var quickenings = assertQuickenings(node, 12, 6);
        assertStable(quickenings, node, 1L);
        assertStable(quickenings, node, 2L);
    }

    @Test
    public void testPopUnboxed() {
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginBlock();
            b.beginPassLongOrInt();
            b.beginPassLongOrInt();
            b.beginLongToInt();
            b.emitLoadArgument(0);
            b.endLongToInt();
            b.endPassLongOrInt();
            b.endPassLongOrInt();
            b.endBlock();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();

            b.endRoot();
        });

        assertQuickenings(node, 0, 0);
        assertInstructions(node,
                        "load.argument",
                        "c.LongToInt",
                        "c.PassLongOrInt",
                        "c.PassLongOrInt",
                        "pop",
                        "load.constant",
                        "return");

        assertEquals(42, node.getCallTarget().call(1L));

        assertInstructions(node,
                        "load.argument$Long",
                        "c.LongToInt$One$unboxed",
                        "c.PassLongOrInt$Long$unboxed",
                        "c.PassLongOrInt$Long$unboxed",
                        "pop$Long",
                        "load.constant",
                        "return");

        assertEquals(42, node.getCallTarget().call(2L));

        assertInstructions(node,
                        "load.argument$Long",
                        "c.LongToInt$GreaterEqualOne$unboxed",
                        "c.PassLongOrInt$Int$unboxed",
                        "c.PassLongOrInt$Int$unboxed",
                        "pop$Int",
                        "load.constant",
                        "return");

        var quickenings = assertQuickenings(node, 16, 6);
        assertStable(quickenings, node, 1L);
        assertStable(quickenings, node, 2L);
    }

    @Test
    public void testShortCircuitOrNoReturn() {
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAnd();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endAnd();
            b.endReturn();

            b.endRoot();
        });

        assertQuickenings(node, 0, 0);
        assertInstructions(node,
                        "load.argument",
                        "c.ToBoolean",
                        "sc.And",
                        "load.argument",
                        "c.ToBoolean",
                        "return");

        assertEquals(true, node.getCallTarget().call(1L, Boolean.TRUE));
        assertInstructions(node,
                        "load.argument$Long",
                        "c.ToBoolean$Long",
                        "sc.And",
                        "load.argument$Boolean",
                        "c.ToBoolean$Boolean",
                        "return");

        var quickenings = assertQuickenings(node, 4, 2);
        assertStable(quickenings, node, 1L, Boolean.TRUE);
        assertStable(quickenings, node, 1L, Boolean.TRUE);
    }

    @Test
    public void testShortCircuitAndNoReturn() {
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginOr();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endOr();
            b.endReturn();

            b.endRoot();
        });

        assertQuickenings(node, 0, 0);
        assertInstructions(node,
                        "load.argument",
                        "c.ToBoolean",
                        "sc.Or",
                        "load.argument",
                        "c.ToBoolean",
                        "return");

        assertEquals(true, node.getCallTarget().call(Boolean.FALSE, 1L));
        assertInstructions(node,
                        "load.argument$Boolean",
                        "c.ToBoolean$Boolean",
                        "sc.Or",
                        "load.argument$Long",
                        "c.ToBoolean$Long",
                        "return");

        var quickenings = assertQuickenings(node, 4, 2);
        assertStable(quickenings, node, Boolean.FALSE, 1L);
        assertStable(quickenings, node, Boolean.FALSE, 1L);
    }

    @Test
    public void testShortCircuitOrReturn() {
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginOrReturn();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endOrReturn();
            b.endReturn();

            b.endRoot();
        });

        assertQuickenings(node, 0, 0);
        assertInstructions(node,
                        "load.argument",
                        "dup",
                        "c.ToBoolean",
                        "sc.OrReturn",
                        "load.argument",
                        "return");

        assertEquals(1L, node.getCallTarget().call(Boolean.FALSE, 1L));
        assertInstructions(node,
                        "load.argument",
                        "dup",
                        "c.ToBoolean",
                        "sc.OrReturn",
                        "load.argument",
                        "return");

        var quickenings = assertQuickenings(node, 1, 1);
        assertStable(quickenings, node, Boolean.FALSE, 1L);
        assertStable(quickenings, node, Boolean.FALSE, 1L);
    }

    @Test
    public void testShortCircuitAndReturn() {
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAndReturn();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endAndReturn();
            b.endReturn();

            b.endRoot();
        });

        assertQuickenings(node, 0, 0);
        assertInstructions(node,
                        "load.argument",
                        "dup",
                        "c.ToBoolean",
                        "sc.AndReturn",
                        "load.argument",
                        "return");
        assertEquals(Boolean.FALSE, node.getCallTarget().call(Boolean.FALSE, 1L));
        assertInstructions(node,
                        "load.argument",
                        "dup",
                        "c.ToBoolean",
                        "sc.AndReturn",
                        "load.argument",
                        "return");

        var quickenings = assertQuickenings(node, 1, 1);
        assertStable(quickenings, node, Boolean.FALSE, 1L);
        assertStable(quickenings, node, Boolean.FALSE, 1L);
    }

    @Test
    public void testConstantOperandsAreNotQuickened() {
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginOperationWithConstants(0);
            b.emitLoadArgument(0);
            b.endOperationWithConstants(1);
            b.endReturn();

            b.endRoot();
        });

        assertQuickenings(node, 0, 0);
        assertInstructions(node,
                        "load.argument",
                        "c.OperationWithConstants",
                        "return");
        assertEquals(42, node.getCallTarget().call(42));
        assertInstructions(node,
                        "load.argument",
                        "c.OperationWithConstants",
                        "return");

        var quickenings = assertQuickenings(node, 0, 1);
        assertStable(quickenings, node, 42);
    }

    @Test
    public void testSameNameSpecializationBoxing() {
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);
            b.beginReturn();
            b.beginAddSameName();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endAddSameName();
            b.endReturn();
            b.endRoot();
        });

        assertQuickenings(node, 0, 0);
        assertInstructions(node,
                        "load.argument",
                        "load.argument",
                        "c.AddSameName",
                        "return");
        assertEquals(42, node.getCallTarget().call(20, 22));
        assertInstructions(node,
                        "load.argument$Int",
                        "load.argument$Int",
                        "c.AddSameName$SameName3",
                        "return");

        assertEquals(42L, node.getCallTarget().call(20, 22L));

        assertInstructions(node,
                        "load.argument",
                        "load.argument",
                        "c.AddSameName",
                        "return");

        var quickenings = assertQuickenings(node, 7, 2);
        assertStable(quickenings, node, 42, 42L);
        assertStable(quickenings, node, 42, 42);
    }

    private static BoxingEliminationTestRootNode parse(BytecodeParser<BoxingEliminationTestRootNodeGen.Builder> builder) {
        BytecodeRootNodes<BoxingEliminationTestRootNode> nodes = BoxingEliminationTestRootNodeGen.create(BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(nodes.count() - 1);
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                    enableYield = true, enableSerialization = true, //
                    enableQuickening = true, //
                    boxingEliminationTypes = {long.class, int.class, boolean.class})
    @ShortCircuitOperation(name = "And", operator = Operator.AND_RETURN_CONVERTED, booleanConverter = ToBoolean.class)
    @ShortCircuitOperation(name = "Or", operator = Operator.OR_RETURN_CONVERTED, booleanConverter = ToBoolean.class)
    @ShortCircuitOperation(name = "AndReturn", operator = Operator.AND_RETURN_VALUE, booleanConverter = ToBoolean.class)
    @ShortCircuitOperation(name = "OrReturn", operator = Operator.OR_RETURN_VALUE, booleanConverter = ToBoolean.class)
    public abstract static class BoxingEliminationTestRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected BoxingEliminationTestRootNode(TruffleLanguage<?> language,
                        FrameDescriptor.Builder frameDescriptor) {
            super(language, customize(frameDescriptor).build());
        }

        private static FrameDescriptor.Builder customize(FrameDescriptor.Builder b) {
            b.defaultValue("Nil");
            return b;
        }

        @Operation
        static final class ToBoolean {
            @Specialization
            public static boolean doBoolean(boolean v) {
                return v;
            }

            @Specialization
            public static boolean doLong(long v) {
                return v != 0;
            }

        }

        /*
         * Tests that the boxing elimination is not confused by same name specializations.
         */
        @Operation
        static final class AddSameName {
            @Specialization
            public static long sameName(long lhs, long rhs) {
                return lhs + rhs;
            }

            @Specialization
            public static long sameName(int lhs, long rhs) {
                return lhs + rhs;
            }

            @Specialization
            public static long sameName(long lhs, int rhs) {
                return lhs + rhs;
            }

            @Specialization
            public static int sameName(int lhs, int rhs) {
                return lhs + rhs;
            }

            @TruffleBoundary
            @Specialization
            public static String sameName(String lhs, String rhs) {
                return lhs + rhs;
            }
        }

        @Operation
        static final class Add {
            @Specialization
            public static long doLong(long lhs, long rhs) {
                return lhs + rhs;
            }

            @Specialization
            public static int doInt(int lhs, int rhs) {
                return lhs + rhs;
            }

            @TruffleBoundary
            @Specialization
            public static String doString(String lhs, String rhs) {
                return lhs + rhs;
            }
        }

        @Operation
        static final class Abs {

            @Specialization(guards = "v >= 0")
            @ForceQuickening("positiveAndNegative")
            @ForceQuickening
            public static long doGreaterZero(long v) {
                return v;
            }

            @Specialization(guards = "v < 0")
            @ForceQuickening("positiveAndNegative")
            @ForceQuickening
            public static long doLessThanZero(long v) {
                return -v;
            }

            @Specialization
            public static int doInt(int v) {
                return -v;
            }

            @Specialization
            public static String doString(String v) {
                return v;
            }
        }

        /*
         * If the type is known statically we should be able to do boxing elimination without
         * quickening.
         */
        @Operation
        static final class GenericOperationWithLong {

            @Specialization
            static long doLong(long v) {
                if (v < 0L) {
                    return -v;
                }
                return v;
            }

        }

        @Operation
        static final class False {

            @Specialization
            static boolean doBoolean() {
                return false;
            }

        }

        @Operation
        static final class True {

            @Specialization
            static boolean doBoolean() {
                return true;
            }

        }

        @Operation
        static final class ConsumeObject {

            @Specialization
            static void doObject(@SuppressWarnings("unused") Object o) {
            }

        }

        @Operation
        static final class ProvideUnexpectedObject {

            @Specialization(rewriteOn = UnexpectedResultException.class)
            static boolean doObject(@SuppressWarnings("unused") Object o) throws UnexpectedResultException {
                throw new UnexpectedResultException(o);
            }

            @Specialization(replaces = "doObject")
            static boolean doExpected(@SuppressWarnings("unused") Object o) {
                return o != null;
            }

        }

        @Operation
        static final class SwitchQuickening0 {

            @Specialization(guards = "o == 1")
            @ForceQuickening
            static long doOne(long o) {
                return o;
            }

            @Specialization(guards = "o != null", replaces = "doOne")
            @ForceQuickening
            static Object doNonNull(Object o) {
                return o;
            }

            @Specialization(replaces = "doNonNull")
            @ForceQuickening
            static Object doObject(Object o) {
                return o;
            }

        }

        @Operation
        static final class SwitchQuickening1 {

            @Specialization(guards = "o == 1")
            @ForceQuickening
            static long doOne(long o) {
                return o;
            }

            @Specialization(guards = "o >= 1", replaces = "doOne")
            @ForceQuickening
            static long doGreaterEqualOne(long o) {
                return o;
            }

        }

        @Operation
        static final class LongToInt {

            @Specialization(guards = "o == 1")
            static long doOne(long o) {
                return o;
            }

            @Specialization(guards = "o >= 1", replaces = "doOne")
            static int doGreaterEqualOne(long o) {
                return (int) o;
            }

        }

        @Operation
        static final class PassLongOrInt {

            @Specialization
            static long doLong(long o) {
                return o;
            }

            @Specialization(replaces = "doLong")
            static int doInt(int o) {
                return o;
            }

        }

        @Operation
        static final class GetLocals {
            @Specialization
            static Object[] perform(VirtualFrame frame,
                            @Bind BytecodeNode bytecode,
                            @Bind("$bytecodeIndex") int bci) {
                return bytecode.getLocalValues(bci, frame);
            }
        }

        @Operation
        static final class GetLocal {
            @Specialization
            static Object perform(VirtualFrame frame, int i,
                            @Bind BytecodeNode bytecode,
                            @Bind("$bytecodeIndex") int bci) {
                return bytecode.getLocalValue(bci, frame, i);
            }
        }

        @Operation
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class, specifyAtEnd = true)
        static final class OperationWithConstants {
            /**
             * Regression test: constant operands of BE-able type should not be considered when
             * computing quickening groups. (The cached parameter is included trigger generation of
             * executeAndSpecialize and quicken methods.)
             */
            @Specialization
            @SuppressWarnings("unused")
            public static Object doInt(int constant1, Object dynamic, int constant2, @Cached(value = "constant1", neverDefault = false) int cachedConstant1) {
                return dynamic;
            }
        }

    }

}