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
package com.oracle.truffle.api.bytecode.test.error_tests;

import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public class ProxyExecutionMetadataErrorTest {

    @SuppressWarnings("truffle-inlining")
    @OperationProxy.Proxyable(bindBytecodeNode = true)
    abstract static class MissingBytecodeNode extends Node {

        @ExpectError("Invalid execute method metadata prefix:%")
        abstract Object execute();

        @Specialization
        static Object doDefault() {
            return null;
        }
    }

    @SuppressWarnings("truffle-inlining")
    @OperationProxy.Proxyable(bindBytecodeNode = true, bindBytecodeIndex = true)
    abstract static class MissingBytecodeIndexOnOneOverload extends Node {

        abstract Object execute(BytecodeNode bytecode, int bytecodeIndex);

        @ExpectError("Invalid execute method metadata prefix:%")
        abstract int executeInt(BytecodeNode bytecode) throws UnexpectedResultException;

        @Specialization
        static int doDefault() {
            return 1;
        }
    }

    @SuppressWarnings("truffle-inlining")
    @OperationProxy.Proxyable(bindBytecodeNode = true, bindBytecodeIndex = true)
    abstract static class ReversedMetadata extends Node {

        @ExpectError({"Invalid execute method metadata prefix:%", "Invalid execute method metadata prefix:%"})
        abstract Object execute(int bytecodeIndex, BytecodeNode bytecode);

        @Specialization
        static Object doDefault() {
            return null;
        }
    }

    @SuppressWarnings("truffle-inlining")
    @OperationProxy.Proxyable(bindFrame = false, bindBytecodeIndex = true)
    abstract static class BoxedBytecodeIndex extends Node {

        @ExpectError("Invalid execute method metadata prefix:%")
        abstract Object execute(Integer bytecodeIndex);

        @Specialization
        static Object doDefault() {
            return null;
        }
    }

    @SuppressWarnings("truffle-inlining")
    @OperationProxy.Proxyable(bindFrame = false, bindBytecodeIndex = true)
    abstract static class MetadataChild extends Node {

        abstract int execute(int bytecodeIndex);

        @Specialization
        static int doDefault(@Bind("$bytecodeIndex") int bytecodeIndex) {
            return bytecodeIndex;
        }
    }

    @NodeChild(value = "child", type = MetadataChild.class)
    abstract static class MissingChildMetadata extends Node {

        @ExpectError("Child execution method int MetadataChild::execute(int) requires bytecode-index execution metadata, but this execute method does not supply it.%")
        abstract int execute();

        @Specialization
        static int doDefault(int child) {
            return child;
        }
    }

    @GenerateInline
    @OperationProxy.Proxyable(bindBytecodeNode = true)
    abstract static class InlineTargetBeforeMetadata extends Node {

        @ExpectError("Invalid execute method metadata prefix:%")
        abstract Object execute(Node inliningTarget, BytecodeNode bytecode);

        @Specialization
        static Object doDefault() {
            return null;
        }
    }
}
