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

import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * This file contains the full source for the getting started guide. It can be read on its own, but
 * readers may find the amount of details overwhelming. We recommend reading the getting started
 * guide, which gradually works up to the end result here in this file.
 *
 * @see <a href=
 *      "https://github.com/oracle/graal/blob/master/truffle/docs/bytecode_dsl/GettingStarted.md">Getting
 *      started guide</a>
 */
public class GettingStarted {

    /**
     * The specification for the interpreter. Bytecode DSL uses the specification to generate a
     * bytecode encoding, interpreter, and all other supporting code.
     * <p>
     * Your class should be annotated with {@link GenerateBytecode}. The annotated class must be a
     * subclass of {@link RootNode}, and it must implement {@link BytecodeRootNode}.
     */
    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
    public abstract static class GettingStartedBytecodeNode extends RootNode implements BytecodeRootNode {

        /**
         * All Bytecode root nodes must define a constructor that takes only a
         * {@link TruffleLanguage} and a {@link FrameDescriptor} (or
         * {@link FrameDescriptor.Builder}).
         */
        protected GettingStartedBytecodeNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
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

    }

    /**
     * Let's make a small program that adds 1 to its first (and only) argument. Encoded as a tree of operations,
     * this program looks like
     *
     * @formatter:off
     * <code>
     * (Root
     *   (Return
     *     (Add
     *       (LoadArgument 0)
     *       (LoadConstant 1))))
     * </code>
     * @formatter:on
     *
     * This example uses some new operations:
     * <ul>
     * <li>{@code Root} is the top-level operation used to declare a root node. It executes its children.</li>
     * <li>{@code Return} returns the value produced by its child.</li>
     * <li>{@code Add} is the custom operation we defined in our specification.</li>
     * <li>{@code LoadArgument} loads an argument.</li>
     * <li>{@code LoadConstant} loads a constant.</li>
     * </ul>
     */
    @Test
    public void testPlusOne() {
        BytecodeParser<GettingStartedBytecodeNodeGen.Builder> parser = b -> {
            /**
             * Programs are constructed using a {@link BytecodeParser}, which invokes a series of
             * {@link BytecodeBuilder} methods to encode the "tree" of operations. The builder
             * translates these method calls to bytecode.
             * <p>
             * Each operation is specified using {@code begin} and {@code end} calls. Child
             * operations get specified in between. Operations that have no children are instead
             * specified with {@code emit} calls.
             */

            // @formatter:off
            b.beginRoot(null); // your language goes here
                b.beginReturn();
                    b.beginAdd();
                        b.emitLoadArgument(0);
                        b.emitLoadConstant(1);
                    b.endAdd();
                b.endReturn();
            b.endRoot();
            // @formatter:on
        };

        BytecodeRootNodes<GettingStartedBytecodeNode> rootNodes = GettingStartedBytecodeNodeGen.create(BytecodeConfig.DEFAULT, parser);
        GettingStartedBytecodeNode rootNode = rootNodes.getNode(0);

        assertEquals(42, rootNode.getCallTarget().call(41));
        assertEquals(123, rootNode.getCallTarget().call(122));
    }

}
