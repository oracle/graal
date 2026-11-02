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
package com.oracle.truffle.api.bytecode.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class ReportPolymorphismTest {

    @Test
    public void testOperation() {
        ReportPolymorphismRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginAdd();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endAdd();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(42L, root.getCallTarget().call(40L, 2L));
        assertEquals(42.0, root.getCallTarget().call(40.0, 2.0));
        assertEquals("hello world", root.getCallTarget().call("hello ", "world"));
        assertOperationNode(root, false);
    }

    @Test
    public void testOperationProxy() {
        ReportPolymorphismRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginAddProxy();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endAddProxy();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(42L, root.getCallTarget().call(40L, 2L));
        assertEquals(42.0, root.getCallTarget().call(40.0, 2.0));
        assertEquals("hello world", root.getCallTarget().call("hello ", "world"));
        assertOperationNode(root, false);
    }

    @Test
    public void testMegamorphicSpecialization() {
        ReportPolymorphismRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginMegamorphic();
            b.emitLoadArgument(0);
            b.endMegamorphic();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(42L, root.getCallTarget().call(42L));
        assertEquals("generic", root.getCallTarget().call("generic"));
        assertOperationNode(root, false);
    }

    @Test
    public void testMultipleCacheInstances() {
        ReportPolymorphismRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginCachedValue();
            b.emitLoadArgument(0);
            b.endCachedValue();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(42L, root.getCallTarget().call(42L));
        assertEquals(43L, root.getCallTarget().call(43L));
        assertEquals(44L, root.getCallTarget().call(44L));
        assertEquals(45L, root.getCallTarget().call(45L));
        assertOperationNode(root, true);
    }

    private static void assertOperationNode(ReportPolymorphismRootNode root, boolean adoptable) {
        for (var instruction : root.getBytecodeNode().getInstructions()) {
            if (instruction.getName().startsWith("c.")) {
                Node node = AbstractInstructionTest.findNodeArgument(instruction).asCachedNode();
                assertEquals(adoptable, node.isAdoptable());
                if (adoptable) {
                    assertSame(root, node.getRootNode());
                }
                return;
            }
        }
        throw new AssertionError("Missing custom operation");
    }

    private static ReportPolymorphismRootNode parse(BytecodeParser<ReportPolymorphismRootNodeGen.Builder> parser) {
        ReportPolymorphismRootNode root = ReportPolymorphismRootNodeGen.create(null, BytecodeConfig.DEFAULT, parser).getNode(0);
        root.getBytecodeNode().setUncachedThreshold(0);
        return root;
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true, enableQuickening = true)
    @OperationProxy(AddProxy.class)
    public abstract static class ReportPolymorphismRootNode extends RootNode implements BytecodeRootNode {

        protected ReportPolymorphismRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        @ReportPolymorphism
        public static final class Add {

            @Specialization
            public static long addLongs(long left, long right) {
                return left + right;
            }

            @Specialization
            public static double addDoubles(double left, double right) {
                return left + right;
            }

            @Specialization
            public static String concat(String left, String right) {
                return left + right;
            }
        }

        @Operation
        public static final class Megamorphic {

            @Specialization
            public static long doLong(long value) {
                return value;
            }

            @Specialization(replaces = "doLong")
            @ReportPolymorphism.Megamorphic
            public static Object doGeneric(Object value) {
                return value;
            }
        }

        @Operation
        @ReportPolymorphism
        public static final class CachedValue {

            @Specialization(guards = "value == cachedValue", limit = "3")
            public static long doCached(long value, @Cached("value") @SuppressWarnings("unused") long cachedValue) {
                return value;
            }

            @Specialization(replaces = "doCached")
            public static long doGeneric(long value) {
                return value;
            }
        }
    }

    @OperationProxy.Proxyable(allowUncached = true)
    @ReportPolymorphism
    @SuppressWarnings("truffle-inlining")
    public abstract static class AddProxy extends Node {

        abstract Object execute(Object left, Object right);

        @Specialization
        public static long addLongs(long left, long right) {
            return left + right;
        }

        @Specialization
        public static double addDoubles(double left, double right) {
            return left + right;
        }

        @Specialization
        public static String concat(String left, String right) {
            return left + right;
        }
    }
}
