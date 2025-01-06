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
package com.oracle.truffle.api.bytecode.test.examples;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLabel;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * This tutorial explains how to define a basic Bytecode DSL interpreter. The source code itself is
 * meant to be read in order from top to bottom.
 *
 * @see <a href=
 *      "https://github.com/oracle/graal/blob/master/truffle/docs/bytecode_dsl/UserGuide.md">User
 *      Guide</a>
 */
public class GettingStarted {

    /**
     * The first step in creating a Bytecode DSL interpreter is to define the interpreter class.
     * <p>
     * The specification for the interpreter consists of the {@link GenerateBytecode} annotation,
     * plus the operation specifications, which come in the form of {@link Operation} inner classes
     * or {@link OperationProxy} and {@link ShortCircuitOperation} annotations. The Bytecode DSL
     * uses the specification to generate a bytecode interpreter with all supporting code.
     * <p>
     * Your class should be annotated with {@link GenerateBytecode}. The annotated class must be
     * abstract, must be a subclass of {@link RootNode}, and must implement
     * {@link BytecodeRootNode}.
     */
    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
    /*
     * Defines a new {@code ScOr} operation ({@code Sc} stands for "short-circuit"). It uses {@code
     * OR} semantics, converts values to boolean using {@link ToBool}, and produces the converted
     * boolean values.
     */
    @ShortCircuitOperation(name = "ScOr", operator = ShortCircuitOperation.Operator.OR_RETURN_CONVERTED, booleanConverter = GettingStartedBytecodeRootNode.ToBool.class)
    public abstract static class GettingStartedBytecodeRootNode extends RootNode implements BytecodeRootNode {

        /**
         * All Bytecode root nodes must define a constructor that takes only a
         * {@link TruffleLanguage} and a {@link FrameDescriptor} (or
         * {@link FrameDescriptor.Builder}).
         */
        protected GettingStartedBytecodeRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        /**
         * Bytecode root nodes can define fields. Because the constructor cannot take additional
         * parameters, these fields must be initialized at a later time (consider annotations like
         * {@link CompilationFinal} if the field is effectively final).
         */
        @CompilationFinal String name;

        public void setName(String name) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.name = name;
        }

        /**
         * Operations can be defined inside the bytecode root node class. They declare their
         * semantics in much the same way as Truffle DSL nodes, with some additional restrictions
         * (see {@link Operation}).
         */

        @Operation
        public static final class Add {
            @Specialization
            public static int doInts(int a, int b) {
                return a + b;
            }
        }

        @Operation
        public static final class Div {
            @Specialization
            public static int doInts(int a, int b) {
                return a / b;
            }
        }

        @Operation
        public static final class Equals {
            @Specialization
            public static boolean doInts(int a, int b) {
                return a == b;
            }
        }

        @Operation
        public static final class LessThan {
            @Specialization
            public static boolean doInts(int a, int b) {
                return a < b;
            }
        }

        /**
         * This is an eager OR operation. It does not use the Bytecode DSL's short-circuiting
         * capabilities.
         */
        @Operation
        public static final class EagerOr {
            @Specialization
            public static boolean doBools(boolean a, boolean b) {
                return a | b;
            }
        }

        /**
         * This class is used as a boolean converter for the short-circuit {@code ScOr} operation
         * defined above. There are some additional restrictions on boolean converters, namely that
         * they must take a single argument and they must return boolean.
         */
        @Operation
        public static final class ToBool {
            @Specialization
            public static boolean doBool(boolean b) {
                return b;
            }

            @Specialization
            public static boolean doInt(int i) {
                return i != 0;
            }
        }

        /**
         * These operations are used in {@link ParsingTutorial}. You can ignore them for now.
         */
        @Operation
        public static final class ArrayLength {
            @Specialization
            public static int doInt(int[] array) {
                return array.length;
            }
        }

        @Operation
        public static final class ArrayIndex {
            @Specialization
            public static int doInt(int[] array, int index) {
                return array[index];
            }
        }

    }

    /**
     * When implementing a language with a Bytecode DSL interpreter, the main challenge is to
     * express the language's semantics using operations. There are many built-in operations for
     * common language behaviour; this tutorial will introduce them gradually.
     * <p>
     * Consider a small function that adds 1 to its first (and only) argument:
     *
     * <pre>
     * def plusOne(arg0):
     *   return arg0 + 1
     * </pre>
     *
     * This function can be encoded using the following "tree" of operations:
     *
     * <pre>
     * (Root
     *   (Return
     *     (Add
     *       (LoadArgument 0)
     *       (LoadConstant 1))))
     * </pre>
     *
     * This example uses some new operations:
     * <ul>
     * <li>{@code Root} is the top-level operation used to declare a root node. It executes its
     * children.</li>
     * <li>{@code Return} returns the value produced by its child.</li>
     * <li>{@code Add} is the custom operation we defined in our specification.</li>
     * <li>{@code LoadArgument} loads an argument.</li>
     * <li>{@code LoadConstant} loads a constant.</li>
     * </ul>
     *
     * In words, the above tree declares a root node that returns the result of adding its first
     * argument and the integer constant {@code 1}. Let's next show how to implement this function
     * in source code.
     */
    @Test
    public void testPlusOne() {
        /**
         * Programs are constructed using a {@link BytecodeParser}, which invokes
         * {@link BytecodeBuilder} methods to encode the "tree" of operations. The builder
         * translates these method calls to bytecode.
         * <p>
         * Each operation is specified using {@code begin} and {@code end} calls. Each child
         * operation is specified between these calls. Operations that have no children (i.e., no
         * dynamic data dependency) are instead specified with {@code emit} calls. Observe the
         * symmetry between the builder calls and the abstract tree representation above.
         */
        BytecodeParser<GettingStartedBytecodeRootNodeGen.Builder> parser = b -> {
            // @formatter:off
            b.beginRoot();
                b.beginReturn();
                    b.beginAdd();
                        b.emitLoadArgument(0);
                        b.emitLoadConstant(1);
                    b.endAdd();
                b.endReturn();
            b.endRoot();
            // @formatter:on
        };

        /**
         * The static {@code create} method invokes the parser and produces a
         * {@link BytecodeRootNodes} instance containing the root node(s). These root nodes contain
         * bytecode that implements the series of operations.
         */
        BytecodeRootNodes<GettingStartedBytecodeRootNode> rootNodes = GettingStartedBytecodeRootNodeGen.create(getLanguage(), BytecodeConfig.DEFAULT, parser);
        GettingStartedBytecodeRootNode plusOne = rootNodes.getNode(0);

        /**
         * When we call the root node, it will execute the bytecode that implements {@code plusOne},
         * producing the expected outputs.
         */
        assertEquals(42, plusOne.getCallTarget().call(41));
        assertEquals(123, plusOne.getCallTarget().call(122));
    }

    /**
     * Let's introduce some more features: sequencing and local variables.
     * <p>
     * A {@code Block} operation executes its children in sequence. It can produce a value if its
     * last child produces a value.
     * <p>
     * Programs can reserve space in the frame using locals. This space can be accessed using
     * {@code StoreLocal} and {@code LoadLocal} operations. A local is scoped to the operation it is
     * created in.
     * <p>
     * To demonstrate these operations, we could rewrite the {@code plusOne} program above as
     * follows:
     *
     * <pre>
     * def plusOne(arg0):
     *   x = arg0 + 1
     *   return x
     * </pre>
     *
     * As operations, this function can be encoded as:
     *
     * <pre>
     * (Root
     *   (Block
     *     (CreateLocal x)
     *     (StoreLocal x
     *       (Add
     *         (LoadArgument 0)
     *         (LoadConstant 1)))
     *     (Return
     *       (LoadLocal x))))
     * </pre>
     */
    @Test
    public void testPlusOneWithLocals() {
        BytecodeParser<GettingStartedBytecodeRootNodeGen.Builder> parser = b -> {
            // @formatter:off
            b.beginRoot();
                b.beginBlock();
                    // Allocate the local.
                    BytecodeLocal x = b.createLocal();

                    // Store the value computed by the child operation into the local.
                    b.beginStoreLocal(x);
                        b.beginAdd();
                            b.emitLoadArgument(0);
                            b.emitLoadConstant(1);
                        b.endAdd();
                    b.endStoreLocal();

                    b.beginReturn();
                        // Read the value of the local.
                        b.emitLoadLocal(x);
                    b.endReturn();
                b.endBlock();
            b.endRoot();
            // @formatter:on
        };

        BytecodeRootNodes<GettingStartedBytecodeRootNode> rootNodes = GettingStartedBytecodeRootNodeGen.create(getLanguage(), BytecodeConfig.DEFAULT, parser);
        GettingStartedBytecodeRootNode plusOne = rootNodes.getNode(0);

        assertEquals(42, plusOne.getCallTarget().call(41));
        assertEquals(123, plusOne.getCallTarget().call(122));
    }

    /**
     * Of course, languages usually support non-linear control flow. The Bytecode DSL has built-in
     * operations to support common control flow mechanisms like conditional branching, looping, and
     * exception handling.
     * <p>
     * From hereon, we will omit the operation "tree" representation, since it can be inferred from
     * the builder calls.
     */
    @Test
    public void testIfThenElse() {
        /**
         * First, let's demonstrate the {@code IfThenElse} operation by implementing the following
         * function:
         *
         * <pre>
         * def checkPassword(arg0):
         *   if arg0 == 1337:
         *     return "Access granted."
         *   else:
         *     return "Access denied."
         * </pre>
         */
        BytecodeParser<GettingStartedBytecodeRootNodeGen.Builder> ifThenElseParser = b -> {
            // @formatter:off
            b.beginRoot();
                b.beginIfThenElse();
                    // The first operation produces a boolean condition.
                    b.beginEquals();
                        b.emitLoadArgument(0);
                        b.emitLoadConstant(1337);
                    b.endEquals();

                    // The second operation is executed in the "true" case.
                    b.beginReturn();
                        b.emitLoadConstant("Access granted.");
                    b.endReturn();

                    // The third operation is executed in the "false" case.
                    b.beginReturn();
                        b.emitLoadConstant("Access denied.");
                    b.endReturn();
                b.endIfThenElse();
            b.endRoot();
            // @formatter:on
        };
        BytecodeRootNodes<GettingStartedBytecodeRootNode> rootNodes = GettingStartedBytecodeRootNodeGen.create(getLanguage(), BytecodeConfig.DEFAULT, ifThenElseParser);
        GettingStartedBytecodeRootNode checkPassword = rootNodes.getNode(0);

        assertEquals("Access granted.", checkPassword.getCallTarget().call(1337));
        assertEquals("Access denied.", checkPassword.getCallTarget().call(1338));

        /**
         * There is also an {@code IfThen} operation, which omits the "false" case, and a
         * {@code Conditional} operation, which produces the value from its conditionally-executed
         * child. We can rewrite the above program with a conditional as follows:
         *
         * <pre>
         * def checkPassword(arg0):
         *   return arg0 == 1337 ? "Access granted." : "Access denied."
         * </pre>
         */
        BytecodeParser<GettingStartedBytecodeRootNodeGen.Builder> conditionalParser = b -> {
            // @formatter:off
            b.beginRoot();
                b.beginReturn();
                    b.beginConditional();
                        // The first operation produces a boolean condition.
                        b.beginEquals();
                            b.emitLoadArgument(0);
                            b.emitLoadConstant(1337);
                        b.endEquals();

                        // The second operation produces a value for the "true" case.
                        b.emitLoadConstant("Access granted.");

                        // The third operation produces a value for the "false" case.
                        b.emitLoadConstant("Access denied.");
                    b.endConditional();
                b.endReturn();
            b.endRoot();
            // @formatter:on
        };
        rootNodes = GettingStartedBytecodeRootNodeGen.create(getLanguage(), BytecodeConfig.DEFAULT, conditionalParser);
        checkPassword = rootNodes.getNode(0);

        assertEquals("Access granted.", checkPassword.getCallTarget().call(1337));
        assertEquals("Access denied.", checkPassword.getCallTarget().call(1338));
    }

    /**
     * The Bytecode DSL has a {@code While} operation for implementing loops. Let's implement the
     * following function:
     *
     * <pre>
     * def sumToN(n):
     *   total = 0
     *   i = 0
     *   while i < n:
     *     i += 1
     *     total += i
     *   return total
     * </pre>
     */
    @Test
    public void testLoop() {
        BytecodeParser<GettingStartedBytecodeRootNodeGen.Builder> parser = b -> {
            // @formatter:off
            b.beginRoot();
                b.beginBlock();
                    BytecodeLocal total = b.createLocal();
                    BytecodeLocal i = b.createLocal();

                    b.beginStoreLocal(total);
                        b.emitLoadConstant(0);
                    b.endStoreLocal();

                    b.beginStoreLocal(i);
                        b.emitLoadConstant(0);
                    b.endStoreLocal();

                    b.beginWhile();
                        // The first operation produces a boolean condition.
                        b.beginLessThan();
                            b.emitLoadLocal(i);
                            b.emitLoadArgument(0);
                        b.endLessThan();

                        // The second operation is the loop body.
                        b.beginBlock();
                            b.beginStoreLocal(i);
                                b.beginAdd();
                                    b.emitLoadLocal(i);
                                    b.emitLoadConstant(1);
                                b.endAdd();
                            b.endStoreLocal();

                            b.beginStoreLocal(total);
                                b.beginAdd();
                                    b.emitLoadLocal(total);
                                    b.emitLoadLocal(i);
                                b.endAdd();
                            b.endStoreLocal();
                        b.endBlock();
                    b.endWhile();

                    b.beginReturn();
                        b.emitLoadLocal(total);
                    b.endReturn();
                b.endBlock();
            b.endRoot();
            // @formatter:on
        };

        BytecodeRootNodes<GettingStartedBytecodeRootNode> rootNodes = GettingStartedBytecodeRootNodeGen.create(getLanguage(), BytecodeConfig.DEFAULT, parser);
        GettingStartedBytecodeRootNode sumToN = rootNodes.getNode(0);

        assertEquals(10, sumToN.getCallTarget().call(4));
        assertEquals(55, sumToN.getCallTarget().call(10));
    }

    /**
     * For more advanced control flow, the Bytecode DSL also allows you to define and branch to
     * labels. Programs can branch forward to labels using the {@code Branch} operation. Let's use
     * labels to implement {@code sumToN} using a {@code break}:
     *
     * <pre>
     * def sumToN(n):
     *   total = 0
     *   i = 0
     *   while true:
     *     i += 1
     *     if (n < i):
     *       break
     *     total += i
     *   return total
     * </pre>
     */
    @Test
    public void testLoopWithBreak() {
        BytecodeParser<GettingStartedBytecodeRootNodeGen.Builder> parser = b -> {
            // @formatter:off
            b.beginRoot();
                b.beginBlock();
                    BytecodeLocal total = b.createLocal();
                    BytecodeLocal i = b.createLocal();

                    b.beginStoreLocal(total);
                        b.emitLoadConstant(0);
                    b.endStoreLocal();

                    b.beginStoreLocal(i);
                        b.emitLoadConstant(0);
                    b.endStoreLocal();

                    // Create a label. Labels can only be created in Block/Root operations.
                    BytecodeLabel lbl = b.createLabel();

                    b.beginWhile();
                        b.emitLoadConstant(true);

                        b.beginBlock();
                            b.beginStoreLocal(i);
                                b.beginAdd();
                                    b.emitLoadLocal(i);
                                    b.emitLoadConstant(1);
                                b.endAdd();
                            b.endStoreLocal();

                            b.beginIfThen();
                                b.beginLessThan();
                                    b.emitLoadArgument(0);
                                    b.emitLoadLocal(i);
                                b.endLessThan();

                                // Branch to the label.
                                // Only forward branches are permitted (for backward branches, use While).
                                b.emitBranch(lbl);
                            b.endIfThen();

                            b.beginStoreLocal(total);
                                b.beginAdd();
                                    b.emitLoadLocal(total);
                                    b.emitLoadLocal(i);
                                b.endAdd();
                            b.endStoreLocal();
                        b.endBlock();
                    b.endWhile();

                    // Declare the label here. Labels must be emitted in the same operation they are created in.
                    b.emitLabel(lbl);

                    b.beginReturn();
                        b.emitLoadLocal(total);
                    b.endReturn();
                b.endBlock();
            b.endRoot();
            // @formatter:on
        };

        BytecodeRootNodes<GettingStartedBytecodeRootNode> rootNodes = GettingStartedBytecodeRootNodeGen.create(getLanguage(), BytecodeConfig.DEFAULT, parser);
        GettingStartedBytecodeRootNode sumToN = rootNodes.getNode(0);

        assertEquals(10, sumToN.getCallTarget().call(4));
        assertEquals(55, sumToN.getCallTarget().call(10));
    }

    /*
     * In addition to the condition and looping constructs, the Bytecode DSL has other control flow
     * mechanisms for exception handling ({@code TryCatch}, {@code TryFinally}, and {@code
     * TryCatchOtherwise}) and continuations ({@code Yield}). We will not cover those here.
     */

    /**
     * One last class of operations we'll discuss in this tutorial is short-circuit operations.
     * <p>
     * All of the custom operations we have used so far have been eager: they evaluate all of their
     * child operations before executing themselves. Interpreters can also define custom
     * short-circuit operations that evaluate a subset of their child operations until some
     * condition is met.
     * <p>
     * Short-circuit operations can implement {@code AND} semantics (keep executing while true) or
     * {@code OR} semantics (keep executing until true). They use a boolean "converter" operation to
     * coerce operand values to boolean (e.g., to support truthy/falsy values). Short-circuit
     * operations can also choose to return the original operand value or the converted boolean
     * value.
     * <p>
     * Short-circuit operations are specified using the {@link ShortCircuitOperation} annotation.
     * Observe the difference between the {@code Or} and {@code ScOr} operations defined above.
     */
    @Test
    public void testShortCircuitOr() {
        BytecodeParser<GettingStartedBytecodeRootNodeGen.Builder> parser = b -> {
            /**
             * @formatter:off
             * <pre>
             * def eagerOr(arg0):
             *   return arg0 or 42 / 0
             * </pre>
             */
            b.beginRoot();
                b.beginReturn();
                    b.beginEagerOr();
                        b.beginToBool();
                            b.emitLoadArgument(0);
                        b.endToBool();

                        b.beginToBool();
                            b.beginDiv();
                                b.emitLoadConstant(42);
                                b.emitLoadConstant(0);
                            b.endDiv();
                        b.endToBool();
                    b.endEagerOr();
                b.endReturn();
            b.endRoot();

            /**
             * <pre>
             * def shortCircuitOr(arg0):
             *   return arg0 sc_or 42 / 0
             * </pre>
             */
            b.beginRoot();
                b.beginReturn();
                    // This operation produces the converted boolean value.
                    // Note that each operand is implicitly converted (it isn't necessary to emit a ToBool operation).
                    b.beginScOr();
                        // This operation executes first.
                        b.emitLoadArgument(0);
                        // If the first operation produces a falsy value, this second operation executes.
                        b.beginDiv();
                            b.emitLoadConstant(42);
                            b.emitLoadConstant(0);
                        b.endDiv();
                    b.endScOr();
                b.endReturn();
            b.endRoot();
            // @formatter:on
        };
        BytecodeRootNodes<GettingStartedBytecodeRootNode> rootNodes = GettingStartedBytecodeRootNodeGen.create(getLanguage(), BytecodeConfig.DEFAULT, parser);
        GettingStartedBytecodeRootNode eagerOr = rootNodes.getNode(0);
        GettingStartedBytecodeRootNode shortCircuitOr = rootNodes.getNode(1);

        // The eager OR always evaluates its operands, so the divide-by-zero is executed even when
        // its first argument is truthy.
        try {
            eagerOr.getCallTarget().call(123);
            fail("should not reach here.");
        } catch (ArithmeticException ex) {
        }
        try {
            eagerOr.getCallTarget().call(0);
            fail("should not reach here.");
        } catch (ArithmeticException ex) {
        }

        // The short circuit OR does not evaluate its second operand unless the first is falsy.
        assertEquals(true, shortCircuitOr.getCallTarget().call(123));
        try {
            shortCircuitOr.getCallTarget().call(0);
            fail("should not reach here.");
        } catch (ArithmeticException ex) {
        }
    }

    /**
     * One of the parameters to {@code create} is a language instance. For simplicity, we return
     * null here.
     */
    private static BytecodeDSLTestLanguage getLanguage() {
        return null;
    }

    /**
     * This tutorial demonstrated how to represent common language constructs using operations.
     * Hopefully it has helped build some intuition about how operations work.
     * <p>
     * So far, the parsers have been hard-coded for specific programs. Our next step is to write a
     * parser that works for <i>any</i> guest program. This topic is covered in the
     * {@link ParsingTutorial}.
     */
}
