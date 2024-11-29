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

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLabel;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.bytecode.test.examples.GettingStarted.GettingStartedBytecodeRootNode;

/**
 * This tutorial demonstrates how to programmatically parse bytecode for a Bytecode DSL interpreter.
 * It refers to the interpreter defined in the {@link GettingStarted} guide. It is recommended to
 * read that guide first.
 */
public class ParsingTutorial {
    /**
     * In the getting started guide, we defined a Bytecode DSL interpreter and demonstrated how to
     * implement common language constructs using operations. All of the parsers were hard-coded for
     * specific programs. Our next step is to write a parser that works for <i>any</i> guest
     * program.
     * <p>
     * The key insight is that most nodes in a language's AST mirror operations in its Bytecode DSL
     * interpreter (e.g., an if-then node can be implemented with an {@code IfThen} operation).
     * Consequently, parsing can often be performed with a simple tree traversal.
     * <p>
     * Let's assume that the guest program can be parsed to an AST made up of the following nodes.
     * We will implement the tree traversal using the visitor pattern.
     */
    interface AST {
        void accept(Visitor v);
    }

    record Method(AST body, String[] locals) implements AST {
        public void accept(Visitor v) {
            v.visitMethod(this);
        }
    }

    record Return(AST expression) implements AST {
        public void accept(Visitor v) {
            v.visitReturn(this);
        }
    }

    record Block(AST[] statements) implements AST {
        public void accept(Visitor v) {
            v.visitBlock(this);
        }
    }

    record IfThenElse(AST condition, AST thens, AST elses) implements AST {
        public void accept(Visitor v) {
            v.visitIfThenElse(this);
        }
    }

    record WhileLoop(AST condition, AST body) implements AST {
        public void accept(Visitor v) {
            v.visitWhileLoop(this);
        }
    }

    record Break() implements AST {
        public void accept(Visitor v) {
            v.visitBreak(this);
        }
    }

    record Or(AST[] operands) implements AST {
        public void accept(Visitor v) {
            v.visitOr(this);
        }
    }

    record Equals(AST lhs, AST rhs) implements AST {
        public void accept(Visitor v) {
            v.visitEquals(this);
        }
    }

    record LessThan(AST lhs, AST rhs) implements AST {
        public void accept(Visitor v) {
            v.visitLessThan(this);
        }
    }

    record Add(AST lhs, AST rhs) implements AST {
        public void accept(Visitor v) {
            v.visitAdd(this);
        }
    }

    record Div(AST lhs, AST rhs) implements AST {
        public void accept(Visitor v) {
            v.visitDiv(this);
        }
    }

    record Argument(int index) implements AST {
        public void accept(Visitor v) {
            v.visitArgument(this);
        }
    }

    record ReadLocal(String name) implements AST {
        public void accept(Visitor v) {
            v.visitReadLocal(this);
        }
    }

    record WriteLocal(String name, AST expression) implements AST {
        public void accept(Visitor v) {
            v.visitWriteLocal(this);
        }
    }

    record Constant(Object constant) implements AST {
        public void accept(Visitor v) {
            v.visitConstant(this);
        }
    }

    interface Visitor {
        void visitMethod(Method m);

        void visitReturn(Return r);

        void visitBlock(Block blk);

        void visitIfThenElse(IfThenElse ifThenElse);

        void visitWhileLoop(WhileLoop loop);

        void visitBreak(Break brk);

        void visitOr(Or or);

        void visitEquals(Equals eq);

        void visitLessThan(LessThan lt);

        void visitAdd(Add a);

        void visitDiv(Div d);

        void visitArgument(Argument a);

        void visitReadLocal(ReadLocal r);

        void visitWriteLocal(WriteLocal w);

        void visitConstant(Constant c);

        void visitForEach(ForEach f);
    }

    /**
     * The tree traversal to parse the AST to bytecode is defined by this visitor. Most of the
     * visitor methods are straightforward; comments are included in the trickier spots.
     */
    static class BytecodeVisitor implements Visitor {
        final GettingStartedBytecodeRootNodeGen.Builder b;
        final Map<String, BytecodeLocal> locals;

        BytecodeLabel currentBreakLabel = null;

        BytecodeVisitor(GettingStartedBytecodeRootNodeGen.Builder b) {
            this.b = b;
            this.locals = new HashMap<>();
        }

        public void visitMethod(Method m) {
            b.beginRoot();
            // Allocate locals for this method. Remember the BytecodeLocal instances.
            for (String name : m.locals) {
                locals.put(name, b.createLocal());
            }
            m.body.accept(this);
            b.endRoot();
        }

        public void visitReturn(Return r) {
            b.beginReturn();
            r.expression.accept(this);
            b.endReturn();
        }

        public void visitBlock(Block blk) {
            b.beginBlock();
            for (AST statement : blk.statements) {
                statement.accept(this);
            }
            b.endBlock();
        }

        public void visitIfThenElse(IfThenElse ifThenElse) {
            if (ifThenElse.elses == null) {
                b.beginIfThen();
            } else {
                b.beginIfThenElse();
            }

            ifThenElse.condition.accept(this);
            ifThenElse.thens.accept(this);

            if (ifThenElse.elses == null) {
                b.endIfThen();
            } else {
                ifThenElse.elses.accept(this);
                b.endIfThenElse();
            }
        }

        public void visitWhileLoop(WhileLoop loop) {
            /**
             * When we enter a while loop, the break statement becomes valid, and we need to give it
             * a label to branch to. We are careful to save and restore any existing break label (in
             * case this while loop is nested in another) before overwriting the field.
             */
            BytecodeLabel oldBreakLabel = currentBreakLabel;

            b.beginBlock();
            currentBreakLabel = b.createLabel();

            b.beginWhile();
            loop.condition.accept(this);
            loop.body.accept(this);
            b.endWhile();

            b.emitLabel(currentBreakLabel);
            b.endBlock();

            currentBreakLabel = oldBreakLabel;
        }

        public void visitBreak(Break brk) {
            if (currentBreakLabel == null) {
                throw new AssertionError("Break statement is invalid outside of a while loop!");
            }
            b.emitBranch(currentBreakLabel);
        }

        public void visitOr(Or or) {
            b.beginScOr();
            for (AST operand : or.operands) {
                operand.accept(this);
            }
            b.endScOr();
        }

        public void visitEquals(Equals eq) {
            b.beginEquals();
            eq.lhs.accept(this);
            eq.rhs.accept(this);
            b.endEquals();
        }

        public void visitLessThan(LessThan lt) {
            b.beginLessThan();
            lt.lhs.accept(this);
            lt.rhs.accept(this);
            b.endLessThan();
        }

        public void visitAdd(Add a) {
            b.beginAdd();
            a.lhs.accept(this);
            a.rhs.accept(this);
            b.endAdd();

        }

        public void visitDiv(Div d) {
            b.beginDiv();
            d.lhs.accept(this);
            d.rhs.accept(this);
            b.endDiv();
        }

        public void visitArgument(Argument a) {
            b.emitLoadArgument(a.index);
        }

        public void visitReadLocal(ReadLocal r) {
            BytecodeLocal local = locals.get(r.name);
            assert local != null;
            b.emitLoadLocal(local);
        }

        public void visitWriteLocal(WriteLocal w) {
            BytecodeLocal local = locals.get(w.name);
            assert local != null;
            b.beginStoreLocal(local);
            w.expression.accept(this);
            b.endStoreLocal();
        }

        public void visitConstant(Constant c) {
            b.emitLoadConstant(c.constant);
        }

        public void visitForEach(ForEach f) {
            throw new AssertionError("not implemented");
        }
    }

    /**
     * For convenience, let's define a helper method that performs the parse.
     * <p>
     * Note: For simplicity, this {@link BytecodeParser} captures {@code method}, which means the
     * AST will be kept alive in the heap in order to perform reparsing. To reduce footprint, it is
     * recommended for the {@link BytecodeParser} to parse from source when possible. For example,
     * the <a href=
     * "https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.sl/src/com/oracle/truffle/sl/parser/SLBytecodeParser.java">Simple
     * Language parser</a> invokes the ANTLR parser to obtain a fresh parse tree and then visits the
     * parse tree to build bytecode.
     */
    public static GettingStartedBytecodeRootNode parse(Method method) {
        BytecodeParser<GettingStartedBytecodeRootNodeGen.Builder> parser = b -> {
            method.accept(new BytecodeVisitor(b));
        };
        BytecodeRootNodes<GettingStartedBytecodeRootNode> rootNodes = GettingStartedBytecodeRootNodeGen.create(getLanguage(), BytecodeConfig.DEFAULT, parser);
        return rootNodes.getNode(0);
    }

    /**
     * That's it! We can write some tests to validate our implementation.
     */
    @Test
    public void testPlusOne() {
        // @formatter:off
        Method method = new Method(
            new Return(
                new Add(new Argument(0), new Constant(1))
            ),
            new String[0]
        );
        // @formatter:on
        GettingStartedBytecodeRootNode plusOne = parse(method);

        assertEquals(42, plusOne.getCallTarget().call(41));
        assertEquals(123, plusOne.getCallTarget().call(122));
    }

    @Test
    public void testIfThenElse() {
        // @formatter:off
        Method method = new Method(
            new IfThenElse(
                new Equals(new Argument(0), new Constant(1337)),
                new Return(new Constant("Access granted.")),
                new Return(new Constant("Access denied."))
            ),
            new String[0]
        );
        // @formatter:on
        GettingStartedBytecodeRootNode checkPassword = parse(method);

        assertEquals("Access granted.", checkPassword.getCallTarget().call(1337));
        assertEquals("Access denied.", checkPassword.getCallTarget().call(1338));
    }

    @Test
    public void testLoop() {
        // @formatter:off
        Method method = new Method(
            new Block(new AST[] {
                new WriteLocal("total", new Constant(0)),
                new WriteLocal("i", new Constant(0)),
                new WhileLoop(
                    new LessThan(new ReadLocal("i"), new Argument(0)),
                    new Block(new AST[] {
                        new WriteLocal("i", new Add(new ReadLocal("i"), new Constant(1))),
                        new WriteLocal("total", new Add(new ReadLocal("total"), new ReadLocal("i")))
                    })
                ),
                new Return(new ReadLocal("total"))
            }),
            new String[] {"total", "i"}
        );
        // @formatter:on
        GettingStartedBytecodeRootNode sumToN = parse(method);

        assertEquals(10, sumToN.getCallTarget().call(4));
        assertEquals(55, sumToN.getCallTarget().call(10));
    }

    @Test
    public void testShortCircuitOr() {
        // @formatter:off
        Method method = new Method(
            new Return(
                new Or(new AST[] {
                    new Argument(0),
                    new Div(new Constant(42), new Constant(0))
                })
            ),
            new String[0]
        );
        // @formatter:on
        GettingStartedBytecodeRootNode shortCircuitOr = parse(method);

        assertEquals(true, shortCircuitOr.getCallTarget().call(123));
        try {
            shortCircuitOr.getCallTarget().call(0);
            fail("should not reach here.");
        } catch (ArithmeticException ex) {
        }
    }

    /**
     * The above AST was intentionally designed so each AST node had a clear translation to
     * operations. Sometimes, a node is more complicated and there is no obvious mapping to
     * operations.
     * <p>
     * Typically, this means you should introduce new operation(s) to implement it. Sometimes one
     * new operation is sufficient, but if the node encompasses complex behaviour (e.g., it has
     * control flow), you should try to break it down into multiple smaller operations.
     * <p>
     * For example, suppose we wanted to support a "for each" node that iterates over an array:
     */
    record ForEach(String variable, AST array, AST body) implements AST {
        public void accept(Visitor v) {
            v.visitForEach(this);
        }
    }

    /**
     * We cannot implement {@code ForEach} with a single operation: we need to execute the body an
     * unspecified number of times, and a custom operation cannot do that. We instead need to
     * "desugar" the node into simpler behaviour that can be implemented with operations.
     * <p>
     * The general approach is to break down the node's behaviour into multiple smaller steps that
     * can be expressed with operations:
     *
     * <pre>
     * array = [evaluate the array]
     * i = 0
     * while i < array.length:
     *   [store array[i] into variable]
     *   [body]
     *   i += 1
     * </pre>
     *
     * We have already figured out how to implement most of these features. What's missing is a
     * couple of operations: one to compute an array length, and one to index into an array. (Since
     * we only have one interpreter definition, they are already included in the original
     * interpreter, but we have ignored them until now.) Let's implement a new visitor that supports
     * {@link ForEach}.
     * <p>
     * An alternative approach could be to define a simplifying pass over the AST that translates it
     * to a simpler AST before generating bytecode. Then, you would not need to define how to
     * translate complex nodes directly to operations. This approach would likely be slower
     * (requiring another AST traversal), but could improve readability.
     */
    class BytecodeVisitorWithForEach extends BytecodeVisitor {
        BytecodeVisitorWithForEach(GettingStartedBytecodeRootNodeGen.Builder b) {
            super(b);
        }

        @Override
        public void visitForEach(ForEach f) {
            // @formatter:off
            b.beginBlock();
                BytecodeLocal array = b.createLocal();
                BytecodeLocal i = b.createLocal();
                BytecodeLocal boundVariable = b.createLocal();
                // For simplicity, assume these names do not conflict with existing locals. A more
                // robust implementation should implement scoping.
                locals.put("array", array);
                locals.put("i", i);
                locals.put(f.variable, boundVariable);

                b.beginStoreLocal(array);
                    f.array.accept(this);
                b.endStoreLocal();

                b.beginStoreLocal(i);
                    b.emitLoadConstant(0);
                b.endStoreLocal();

                b.beginWhile();
                    b.beginLessThan();
                        b.emitLoadLocal(i);
                        b.beginArrayLength();
                            b.emitLoadLocal(array);
                        b.endArrayLength();
                    b.endLessThan();

                    b.beginBlock();
                        b.beginStoreLocal(boundVariable);
                            b.beginArrayIndex();
                                b.emitLoadLocal(array);
                                b.emitLoadLocal(i);
                            b.endArrayIndex();
                        b.endStoreLocal();

                        f.body.accept(this);

                        b.beginStoreLocal(i);
                            b.beginAdd();
                                b.emitLoadLocal(i);
                                b.emitLoadConstant(1);
                            b.endAdd();
                        b.endStoreLocal();
                    b.endBlock();

                b.endWhile();
            b.endBlock();
            // @formatter:on
        }
    }

    /**
     * Finally, let's test it below.
     *
     * This tutorial demonstrated how to parse programs using visitors. It also demonstrated how to
     * "desugar" complex nodes into simpler operations.
     * <p>
     * Still, some more advanced features (e.g., {@code TryFinally} operations) have not been
     * covered. We encourage you to consult the other resources available:
     *
     * @see <a href=
     *      "https://github.com/oracle/graal/blob/master/truffle/docs/bytecode_dsl/UserGuide.md">User
     *      guide</a>.
     *
     * @see <a href=
     *      "https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/package-summary.html">Javadoc
     *      </a>.
     *
     * @see <a href=
     *      "https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.sl/src/com/oracle/truffle/sl/bytecode/SLBytecodeRootNode.java">Simple
     *      Language implementation</a>.
     */
    public void testForEach() {
        // @formatter:off
        Method method = new Method(
            new Block(new AST[] {
                new WriteLocal("sum", new Constant(0)),
                new ForEach("element", new Argument(0),
                    new WriteLocal("sum", new Add(new ReadLocal("sum"), new ReadLocal("element")))
                ),
                new Return(new ReadLocal("sum"))
            }),
            new String[] {"sum"}
        );
        // @formatter:on
        BytecodeParser<GettingStartedBytecodeRootNodeGen.Builder> parser = b -> {
            method.accept(new BytecodeVisitorWithForEach(b));
        };
        BytecodeRootNodes<GettingStartedBytecodeRootNode> rootNodes = GettingStartedBytecodeRootNodeGen.create(getLanguage(), BytecodeConfig.DEFAULT, parser);
        GettingStartedBytecodeRootNode sumArray = rootNodes.getNode(0);

        assertEquals(42, sumArray.getCallTarget().call(new int[]{40, 2}));
        assertEquals(0, sumArray.getCallTarget().call(new int[0]));
        assertEquals(28, sumArray.getCallTarget().call(new int[]{1, 2, 3, 4, 5, 6, 7}));
    }

    /**
     * One of the parameters to {@code create} is a language instance. For simplicity, we return
     * null here.
     */
    private static BytecodeDSLTestLanguage getLanguage() {
        return null;
    }

}
