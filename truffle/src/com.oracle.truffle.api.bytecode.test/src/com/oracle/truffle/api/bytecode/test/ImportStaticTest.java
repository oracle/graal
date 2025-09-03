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
package com.oracle.truffle.api.bytecode.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;

public class ImportStaticTest {

    private static final BytecodeDSLTestLanguage LANGUAGE = null;

    private static ImportStaticTestRootNode parse(BytecodeParser<ImportStaticTestRootNodeGen.Builder> builder) {
        BytecodeRootNodes<ImportStaticTestRootNode> nodes = ImportStaticTestRootNodeGen.create(LANGUAGE, BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(0);
    }

    @Test
    public void testImportOnOperation() {
        ImportStaticTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitFoo();
            b.endReturn();
            b.endRoot();
        });
        assertEquals("foo", root.getCallTarget().call());
    }

    @Test
    public void testImportOnOperationProxy() {
        ImportStaticTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitFooProxy();
            b.endReturn();
            b.endRoot();
        });
        assertEquals("foo", root.getCallTarget().call());
    }

    @Test
    public void testImportOnRootNode() {
        ImportStaticTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitBar();
            b.endReturn();
            b.endRoot();
        });
        assertEquals("bar", root.getCallTarget().call());
    }

    @Test
    public void testImportOnOperationTakesPrecedence() {
        ImportStaticTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitOtherBar();
            b.endReturn();
            b.endRoot();
        });
        assertEquals("otherBar", root.getCallTarget().call());
    }

    @Test
    public void testImportOnOperationProxyTakesPrecedence() {
        ImportStaticTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitOtherBarProxy();
            b.endReturn();
            b.endRoot();
        });
        assertEquals("otherBar", root.getCallTarget().call());
    }

    @GenerateBytecode(//
                    languageClass = BytecodeDSLTestLanguage.class)
    @ImportStatic(BarImport.class)
    @OperationProxy(value = FooNode.class, name = "FooProxy")
    @OperationProxy(value = OtherBarNode.class, name = "OtherBarProxy")
    abstract static class ImportStaticTestRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected ImportStaticTestRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        // Operations can declare their own static imports.
        @Operation
        @ImportStatic(FooImport.class)
        public static final class Foo {
            @Specialization
            public static Object doFoo(@Bind("foo()") Object result) {
                return result;
            }
        }

        // Operations should inherit static imports from the root node.
        @Operation
        public static final class Bar {
            @Specialization
            public static Object doBar(@Bind("bar()") Object result) {
                return result;
            }
        }

        // Operations' static imports should take precedence.
        @Operation
        @ImportStatic(OtherBarImport.class)
        public static final class OtherBar {
            @Specialization
            public static Object doBar(@Bind("bar()") Object result) {
                return result;
            }
        }

    }

    @OperationProxy.Proxyable
    @ImportStatic(FooImport.class)
    @SuppressWarnings("truffle-inlining")
    public abstract static class FooNode extends Node {
        abstract Object execute();

        @Specialization
        public static Object doFoo(@Bind("foo()") Object result) {
            return result;
        }
    }

    @OperationProxy.Proxyable
    @ImportStatic(OtherBarImport.class)
    @SuppressWarnings("truffle-inlining")
    public abstract static class OtherBarNode extends Node {
        abstract Object execute();

        @Specialization
        public static Object doBar(@Bind("bar()") Object result) {
            return result;
        }
    }

    public static final class FooImport {
        public static Object foo() {
            return "foo";
        }
    }

    public static final class BarImport {
        public static Object bar() {
            return "bar";
        }
    }

    public static final class OtherBarImport {
        public static Object bar() {
            return "otherBar";
        }
    }
}
