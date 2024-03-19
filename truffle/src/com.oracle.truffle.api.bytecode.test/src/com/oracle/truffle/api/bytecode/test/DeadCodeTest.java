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
import static org.junit.Assert.fail;

import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.AbstractBytecodeTruffleException;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLabel;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation.Operator;
import com.oracle.truffle.api.bytecode.test.DeadCodeTest.DeadCodeTestRootNode.ToBoolean;
import com.oracle.truffle.api.bytecode.test.DeadCodeTestRootNodeGen.Builder;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;

public class DeadCodeTest extends AbstractInstructionTest {

    protected static final BytecodeDSLTestLanguage LANGUAGE = null;

    @Test
    public void testUnreachableRoot() {
        // return - (arg0)
        DeadCodeTestRootNode node = (DeadCodeTestRootNode) parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();

            emitUnreachableCode(b);

            b.endRoot();
        }).getRootNode();

        assertInstructions(node,
                        "load.constant",
                        "return");
    }

    @Test
    public void testUnreachableIfThenElse() {
        // return - (arg0)
        DeadCodeTestRootNode node = (DeadCodeTestRootNode) parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginIfThenElse();
            b.emitLoadConstant(false);
            b.beginReturn();
            b.emitLoadConstant(41);
            b.endReturn();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endIfThenElse();

            emitUnreachableCode(b);

            b.endRoot();
        }).getRootNode();

        assertInstructions(node,
                        "load.constant",
                        "branch.false",
                        "load.constant",
                        "return",
                        "load.constant",
                        "return");

        assertEquals(42, node.getCallTarget().call(42));
    }

    @Test
    public void testUnreachableFinallyTryNoExcept1() {
        // return - (arg0)
        DeadCodeTestRootNode node = (DeadCodeTestRootNode) parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTryNoExcept();
            b.beginBlock();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            emitUnreachableCode(b);
            b.endBlock();

            b.beginBlock();
            b.beginReturn();
            b.emitLoadConstant(41);
            b.endReturn();
            emitUnreachableCode(b);
            b.endBlock();

            b.endFinallyTryNoExcept();

            emitUnreachableCode(b);

            b.endRoot();
        }).getRootNode();

        assertInstructions(node,
                        "load.constant",
                        "load.constant",
                        "return",
                        "return");

        assertEquals(42, node.getCallTarget().call(42));
    }

    @Test
    public void testUnreachableFinallyTryNoExcept2() {
        // return - (arg0)
        DeadCodeTestRootNode node = (DeadCodeTestRootNode) parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTryNoExcept();
            // finally block has no fallthrough
            b.beginBlock();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            emitUnreachableCode(b);
            b.endBlock();

            // try block has fallthrough
            b.beginBlock();
            b.emitLoadConstant(42);
            b.endBlock();

            b.endFinallyTryNoExcept();

            // not reachable after a non-fallthrough finally block
            emitUnreachableCode(b);

            b.endRoot();
        }).getRootNode();

        assertInstructions(node,
                        "load.constant",
                        "pop",
                        "load.constant",
                        "return");
        assertEquals(42, node.getCallTarget().call(42));
    }

    @Test
    public void testReachableInFinallyTryNoExcept1() {
        // return - (arg0)
        DeadCodeTestRootNode node = (DeadCodeTestRootNode) parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTryNoExcept();
            // finally block
            b.beginBlock();
            b.emitLoadConstant(41);
            b.endBlock();

            // try block
            b.beginBlock();
            b.beginIfThen();
            b.emitLoadConstant(false);
            b.beginReturn();
            b.emitLoadConstant(40);
            b.endReturn();
            b.endIfThen();
            b.endBlock();

            b.endFinallyTryNoExcept();

            // reachable

            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();

            b.endRoot();
        }).getRootNode();

        assertInstructions(node,
                        "load.constant",
                        "branch.false",
                        "load.constant",
                        "load.constant",
                        "pop",
                        "return",
                        "load.constant",
                        "pop",
                        "load.constant",
                        "return");

        assertEquals(42, node.getCallTarget().call(42));
    }

    @Test
    public void testUnreachableFinallyTry1() {
        // return - (arg0)
        DeadCodeTestRootNode node = (DeadCodeTestRootNode) parse(b -> {
            b.beginRoot(LANGUAGE);

            var local = b.createLocal();
            b.beginFinallyTry(local);

            b.beginBlock();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            emitUnreachableCode(b);
            b.endBlock();

            b.emitThrow();
            b.endFinallyTry();

            emitUnreachableCode(b);

            b.endRoot();
        }).getRootNode();

        assertInstructions(node,
                        "c.Throw",
                        "pop",
                        "load.constant",
                        "return",
                        "load.constant",
                        "return",
                        "throw");

        assertEquals(42, node.getCallTarget().call(42));
    }

    @Test
    public void testUnreachableTryCatch1() {
        // return - (arg0)
        DeadCodeTestRootNode node = (DeadCodeTestRootNode) parse(b -> {
            b.beginRoot(LANGUAGE);

            var local = b.createLocal();
            b.beginTryCatch(local);

            b.beginBlock();
            b.emitThrow();
            b.beginReturn();
            b.emitLoadConstant(41);
            b.endReturn();
            emitUnreachableCode(b);
            b.endBlock();

            b.beginBlock();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            emitUnreachableCode(b);
            b.endBlock();

            b.endTryCatch();

            emitUnreachableCode(b);

            b.endRoot();
        }).getRootNode();

        assertInstructions(node,
                        "c.Throw",
                        "pop",
                        "load.constant",
                        "return",
                        "load.constant",
                        "return");

        assertEquals(42, node.getCallTarget().call(42));
    }

    @Test
    public void testUnreachableWhile1() {
        // return - (arg0)
        DeadCodeTestRootNode node = (DeadCodeTestRootNode) parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginWhile();
            b.emitLoadConstant(true);
            b.beginBlock();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endBlock();
            b.endWhile();

            b.endRoot();
        }).getRootNode();

        // while loops always have a fallthrough
        // even if the body does not
        assertInstructions(node,
                        "load.constant",
                        "branch.false",
                        "load.constant",
                        "return",
                        "trap");

        assertEquals(42, node.getCallTarget().call(42));
    }

    @Test
    public void testUnreachableConditional1() {
        // return - (arg0)
        DeadCodeTestRootNode node = (DeadCodeTestRootNode) parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginConditional();
            b.emitLoadConstant(true);

            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();

            b.beginReturn();
            b.emitLoadConstant(41);
            b.endReturn();

            b.endConditional();

            emitUnreachableCode(b);

            b.endRoot();
        }).getRootNode();

        assertInstructions(node,
                        "load.constant",
                        "dup",
                        "branch.false",
                        "load.constant",
                        "return",
                        "load.constant",
                        "return");

        assertEquals(42, node.getCallTarget().call(42));
    }

    @Test
    public void testUnreachableBranch() {
        // return - (arg0)
        DeadCodeTestRootNode node = (DeadCodeTestRootNode) parse(b -> {
            b.beginRoot(LANGUAGE);

            var label = b.createLabel();

            b.emitBranch(label);

            b.beginIfThenElse();
            b.emitLoadConstant(true);
            b.beginBlock();

            b.beginReturn();
            b.emitLoadConstant(41);
            b.endReturn();
            emitUnreachableCode(b);
            b.endBlock();

            b.beginBlock();
            b.beginReturn();
            b.emitLoadConstant(41);
            b.endReturn();
            emitUnreachableCode(b);
            b.endBlock();

            b.endIfThenElse();

            b.emitLabel(label);
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();

            b.endRoot();
        }).getRootNode();

        // while loops always have a fallthrough
        // even if the body does not
        assertInstructions(node,
                        "branch",
                        "load.constant",
                        "return");

        assertEquals(42, node.getCallTarget().call(42));
    }

    @Test
    public void testUnreachableConditionConditional() {
        // return - (arg0)
        DeadCodeTestRootNode node = (DeadCodeTestRootNode) parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginConditional();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();

            b.emitLoadConstant(41);

            b.emitLoadConstant(41);
            b.endConditional();

            emitUnreachableCode(b);

            b.endRoot();
        }).getRootNode();

        assertInstructions(node,
                        "load.constant",
                        "return");

        assertEquals(42, node.getCallTarget().call(42));
    }

    @Test
    public void testUnreachableConditionIfThen() {
        // return - (arg0)
        DeadCodeTestRootNode node = (DeadCodeTestRootNode) parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginIfThen();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.emitLoadConstant(false);
            b.endIfThen();

            b.beginReturn();
            b.emitLoadConstant(41);
            b.endReturn();

            b.endRoot();
        }).getRootNode();

        assertInstructions(node,
                        "load.constant",
                        "return");

        assertEquals(42, node.getCallTarget().call(42));
    }

    @Test
    public void testUnreachableConditionWhile() {
        // return - (arg0)
        DeadCodeTestRootNode node = (DeadCodeTestRootNode) parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginWhile();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();

            b.emitLoadConstant(false);
            b.endWhile();

            b.beginReturn();
            b.emitLoadConstant(41);
            b.endReturn();

            b.endRoot();
        }).getRootNode();

        assertInstructions(node,
                        "load.constant",
                        "return");

        assertEquals(42, node.getCallTarget().call(42));
    }

    @Test
    public void testUnreachableConditionIfThenElse() {
        // return - (arg0)
        DeadCodeTestRootNode node = (DeadCodeTestRootNode) parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginIfThenElse();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();

            b.emitLoadConstant(41);
            b.emitLoadConstant(41);
            b.endIfThenElse();

            b.endRoot();
        }).getRootNode();

        assertInstructions(node,
                        "load.constant",
                        "return");

        assertEquals(42, node.getCallTarget().call(42));
    }

    @Test
    public void testUnreachableFinallyWithLabel() {
        DeadCodeTestRootNode node = (DeadCodeTestRootNode) parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();

            b.beginFinallyTry(b.createLocal());

            b.emitLoadArgument(0);

            b.beginBlock();
            b.emitLabel(b.createLabel());
            b.endBlock();

            b.endFinallyTry();

            b.endRoot();
        }).getRootNode();

        assertInstructions(node,
                        "load.constant",
                        "return");
    }

    @Test
    public void testUnreachableBranchIsNotPatched() {
        /**
         * This is a regression test for a branch fix-up bug. The branch instruction below is dead,
         * but its location was included in the list of "fix up" locations. When the label was
         * emitted, we would "fix up" that location, overwriting the load_argument (from the
         * exception handler) that happened to be at that location.
         *
         * @formatter:off
         * try {
         *   return throw();
         *   branch lbl;  // dead
         * } finally {
         *   load_argument(0);
         * }
         * lbl:
         * @formatter:on
         */
        DeadCodeTestRootNode node = (DeadCodeTestRootNode) parse(b -> {
            b.beginRoot(LANGUAGE);
            b.beginBlock();

            BytecodeLabel lbl = b.createLabel();
            b.beginFinallyTry(b.createLocal());

            b.emitLoadArgument(0); // finally

            b.beginBlock(); // begin try

            b.beginReturn();
            b.emitThrow();
            b.endReturn();

            b.emitBranch(lbl);

            b.endBlock(); // end try

            b.endFinallyTry();

            b.emitLabel(lbl);

            b.endBlock();
            b.endRoot();
        }).getRootNode();

        assertInstructions(node,
                        "c.Throw",
                        "load.argument",
                        "pop",
                        "return",
                        "load.argument",
                        "pop",
                        "throw",
                        "trap");
        node.getIntrospectionData().getInstructions().stream() //
                        .filter(insn -> insn.getName().equals("load.argument")) //
                        .forEach(insn -> assertEquals(0, insn.getArgumentValues().get(0).getInteger()));
        try {
            node.getCallTarget().call(42);
            fail("exception expected");
        } catch (TestException ex) {
            // pass
        } catch (Exception ex) {
            fail("Wrong exception encountered: " + ex.getMessage());
        }
    }

    private static void emitUnreachableCode(Builder b) {
        // custom operation
        b.beginAdd();
        b.emitLoadConstant(21);
        b.emitLoadArgument(21);
        b.endAdd();

        // if then
        b.beginIfThen();
        b.emitLoadConstant(false);
        b.beginReturn();
        b.emitLoadConstant(41);
        b.endReturn();
        b.endIfThen();

        b.beginFinallyTryNoExcept();
        b.emitLoadConstant(41);
        b.beginReturn();
        b.emitLoadConstant(41);
        b.endReturn();
        b.endFinallyTryNoExcept();

        var e = b.createLocal();
        b.beginFinallyTry(e);
        b.emitLoadConstant(41);
        b.beginReturn();
        b.emitLoadConstant(41);
        b.endReturn();
        b.endFinallyTry();

        e = b.createLocal();
        b.beginTryCatch(e);
        b.emitLoadConstant(41);
        b.emitLoadConstant(41);
        b.endTryCatch();

        b.beginIfThenElse();
        b.emitLoadConstant(false);
        b.beginReturn();
        b.emitLoadConstant(41);
        b.endReturn();
        b.emitLoadConstant(41);
        b.endIfThenElse();

        b.beginConditional();
        b.emitLoadConstant(true);
        b.emitLoadConstant(true);
        b.emitLoadConstant(true);
        b.endConditional();

        b.beginYield();
        b.emitLoadConstant(42);
        b.endYield();

        // while and locals
        var l = b.createLocal();
        b.beginStoreLocal(l);
        b.emitLoadConstant(21);
        b.endStoreLocal();
        b.beginWhile();
        b.beginIsNot();
        b.emitLoadLocal(l);
        b.emitLoadConstant(0);
        b.endIsNot();
        b.beginStoreLocal(l);
        b.beginAdd();
        b.emitLoadLocal(l);
        b.emitLoadConstant(-1);
        b.endAdd();
        b.endStoreLocal();
        b.endWhile();

        b.beginAnd();
        b.emitLoadConstant(42);
        b.emitLoadConstant(0);
        b.endAnd();

        b.beginIfThenElse();
        b.emitLoadConstant(true);
        b.emitLoadConstant(42);
        b.emitLoadConstant(42);
        b.endIfThenElse();

        b.beginWhile();
        b.emitLoadConstant(true);
        b.emitLoadConstant(true);
        b.endWhile();

    }

    // TODO conditional unreachable

    private static DeadCodeTestRootNode parse(BytecodeParser<DeadCodeTestRootNodeGen.Builder> builder) {
        BytecodeRootNodes<DeadCodeTestRootNode> nodes = DeadCodeTestRootNodeGen.create(BytecodeConfig.DEFAULT, builder);
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
    public abstract static class DeadCodeTestRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected DeadCodeTestRootNode(TruffleLanguage<?> language,
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
            public static boolean doInt(int a) {
                return a != 0;
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
        static final class Throw {
            @Specialization
            public static boolean doInt() {
                throw new TestException();
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

    @SuppressWarnings("serial")
    static class TestException extends AbstractBytecodeTruffleException {

    }

}
