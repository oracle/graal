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

import com.oracle.truffle.api.RootCallTarget;
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
 * This code contains a minimal example of a Bytecode DSL interpreter. It is mirrored in the
 * markdown documentation.
 *
 * @see <a href=
 *      "https://github.com/oracle/graal/blob/master/truffle/docs/bytecode_dsl/BytecodeDSL.md">Introduction
 *      to Bytecode DSL</a>
 */
public class Introduction {

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
    public abstract static class SampleInterpreter extends RootNode implements BytecodeRootNode {

        protected SampleInterpreter(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        public static final class Add {
            @Specialization
            public static int doInts(int a, int b) {
                return a + b;
            }

            @Specialization
            public static String doStrings(String a, String b) {
                return a + b;
            }
        }
    }

    @Test
    public void testInterpreter() {
        BytecodeParser<SampleInterpreterGen.Builder> parser = b -> {
            // @formatter:off
            b.beginRoot();
                b.beginReturn();
                    b.beginAdd();
                        b.emitLoadArgument(0);
                        b.emitLoadArgument(1);
                    b.endAdd();
                b.endReturn();
            b.endRoot();
            // @formatter:on
        };
        BytecodeRootNodes<SampleInterpreter> rootNodes = SampleInterpreterGen.create(getLanguage(), BytecodeConfig.DEFAULT, parser);

        SampleInterpreter rootNode = rootNodes.getNode(0);
        // System.out.println(rootNode.dump());

        RootCallTarget callTarget = rootNode.getCallTarget();
        assertEquals(42, callTarget.call(40, 2));
        assertEquals("Hello, world!", callTarget.call("Hello, ", "world!"));
    }

    /**
     * One of the parameters to {@code create} is a language instance. For simplicity, we return
     * null here.
     */
    private static BytecodeDSLTestLanguage getLanguage() {
        return null;
    }
}
