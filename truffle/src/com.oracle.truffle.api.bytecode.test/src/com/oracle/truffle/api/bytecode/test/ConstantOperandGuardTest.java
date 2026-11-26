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
import static org.junit.Assert.assertThrows;

import java.util.function.Supplier;

import com.oracle.truffle.api.bytecode.BytecodeVersion;
import org.graalvm.polyglot.Context;
import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class ConstantOperandGuardTest {

    private static ConstantOperandGuardRootNode parse(BytecodeParser<ConstantOperandGuardRootNodeGen.Builder> parser) {
        return ConstantOperandGuardRootNodeGen.create(null, BytecodeConfig.DEFAULT, parser).getNode(0);
    }

    private static ConstantOperandGuardRootNode leading(GuardKey key) {
        return parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginLeadingGuard(key);
            b.emitLoadArgument(0);
            b.endLeadingGuard();
            b.endReturn();
            b.endRoot();
        });
    }

    private static void assertHoisted(Supplier<ConstantOperandGuardRootNode> createRoot, GuardKey key) {
        // Create the root in this context so the guard assertion runs even under forced compilation.
        try (Context context = BytecodeDSLTestLanguage.createPolyglotContextWithCompilationDisabled()) {
            ConstantOperandGuardRootNode root = createRoot.get();
            root.getBytecodeNode().setUncachedThreshold(0);
            assertEquals(42, root.getCallTarget().call(42));
            key.allowed = false;
            // A hoisted guard is checked by a debug assertion when we violate idempotence.
            AssertionError error = assertThrows(AssertionError.class, () -> root.getCallTarget().call(42));
            assertEquals("A guard was assumed idempotent, but returned a different value for a consecutive execution.", error.getMessage());
        }
    }

    @Test
    public void testLeadingConstantGuard() {
        GuardKey key = new GuardKey();
        assertHoisted(() -> leading(key), key);
    }

    @Test
    public void testTrailingConstantGuard() {
        GuardKey key = new GuardKey();
        assertHoisted(() -> parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginTrailingGuard();
            b.emitLoadArgument(0);
            b.endTrailingGuard(key);
            b.endReturn();
            b.endRoot();
        }), key);
    }

    @Test
    public void testLeadingAndTrailingConstantGuard() {
        GuardKey key = new GuardKey();
        assertHoisted(() -> parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginBothGuard(key);
            b.emitLoadArgument(0);
            b.endBothGuard(42);
            b.endReturn();
            b.endRoot();
        }), key);
    }

    @Test
    public void testBoundConstantGuard() {
        GuardKey key = new GuardKey();
        assertHoisted(() -> parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginBoundGuard(key);
            b.emitLoadArgument(0);
            b.endBoundGuard();
            b.endReturn();
            b.endRoot();
        }), key);
    }

    @Test
    public void testMixedGuardRemainsDynamic() {
        GuardKey key = new GuardKey();
        ConstantOperandGuardRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginMixedGuard(key);
            b.emitLoadArgument(0);
            b.endMixedGuard();
            b.endReturn();
            b.endRoot();
        });
        root.getBytecodeNode().setUncachedThreshold(0);
        assertEquals(42, root.getCallTarget().call(42));
        assertEquals("fallback", root.getCallTarget().call(43));
    }

    @Test
    public void testNonIdempotentGuardRemainsDynamic() {
        GuardKey key = new GuardKey();
        ConstantOperandGuardRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginMutableGuard(key);
            b.emitLoadArgument(0);
            b.endMutableGuard();
            b.endReturn();
            b.endRoot();
        });
        root.getBytecodeNode().setUncachedThreshold(0);
        assertEquals(42, root.getCallTarget().call(42));
        key.allowed = false;
        assertEquals("fallback", root.getCallTarget().call(42));
    }

    @Test
    public void testNonIdempotentBindingRemainsDynamic() {
        GuardKey key = new GuardKey();
        ConstantOperandGuardRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginMutableBoundGuard(key);
            b.emitLoadArgument(0);
            b.endMutableBoundGuard();
            b.endReturn();
            b.endRoot();
        });
        root.getBytecodeNode().setUncachedThreshold(0);
        assertEquals(42, root.getCallTarget().call(42));
        key.allowed = false;
        assertEquals("fallback", root.getCallTarget().call(42));
    }

    @Test
    public void testProxiedConstantGuard() {
        GuardKey key = new GuardKey();
        assertHoisted(() -> parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginProxyGuard(key);
            b.emitLoadArgument(0);
            b.endProxyGuard();
            b.endReturn();
            b.endRoot();
        }), key);
    }

    @Test
    public void testRegularExecuteArgumentRemainsDynamic() {
        GuardKey key = new GuardKey();
        ProxyGuardNode node = ConstantOperandGuardTestFactory.ProxyGuardNodeGen.create();
        assertEquals(42, node.execute(key, 42));
        key.allowed = false;
        assertEquals("fallback", node.execute(key, 42));
    }

    @Test
    public void testUncachedGuardStillEvaluates() {
        GuardKey key = new GuardKey();
        ConstantOperandGuardRootNode root = leading(key);
        root.getBytecodeNode().setUncachedThreshold(Integer.MAX_VALUE);
        assertEquals(42, root.getCallTarget().call(42));
        key.allowed = false;
        assertEquals("fallback", root.getCallTarget().call(42));
    }

    @Test
    public void testConstantGuardsAcrossRoots() {
        GuardKey allowed = new GuardKey();
        GuardKey rejected = new GuardKey();
        rejected.allowed = false;
        ConstantOperandGuardRootNode first = leading(allowed);
        ConstantOperandGuardRootNode second = leading(rejected);
        first.getBytecodeNode().setUncachedThreshold(0);
        second.getBytecodeNode().setUncachedThreshold(0);
        for (int i = 0; i < 5; i++) {
            assertEquals(42, first.getCallTarget().call(42));
            assertEquals("fallback", second.getCallTarget().call(42));
        }
    }

    @Test
    public void testConstantGuardsArePerInstruction() {
        GuardKey allowed = new GuardKey();
        GuardKey rejected = new GuardKey();
        rejected.allowed = false;
        ConstantOperandGuardRootNode root = parse(b -> {
            b.beginRoot();
            b.beginIfThenElse();
            b.emitLoadArgument(0);
            b.beginReturn();
            b.beginLeadingGuard(allowed);
            b.emitLoadArgument(1);
            b.endLeadingGuard();
            b.endReturn();
            b.beginReturn();
            b.beginLeadingGuard(rejected);
            b.emitLoadArgument(1);
            b.endLeadingGuard();
            b.endReturn();
            b.endIfThenElse();
            b.endRoot();
        });
        root.getBytecodeNode().setUncachedThreshold(0);
        for (int i = 0; i < 5; i++) {
            assertEquals(42, root.getCallTarget().call(true, 42));
            assertEquals("fallback", root.getCallTarget().call(false, 42));
        }
    }

    @Test
    public void testMutableFieldRemainsDynamic() {
        GuardKey key = new GuardKey();
        ConstantOperandGuardRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginFieldGuard(key);
            b.emitLoadArgument(0);
            b.endFieldGuard();
            b.endReturn();
            b.endRoot();
        });
        root.getBytecodeNode().setUncachedThreshold(0);
        assertEquals(42, root.getCallTarget().call(42));
        key.allowed = false;
        assertEquals("fallback", root.getCallTarget().call(42));
    }

    @Test
    public void testSourceReparsePreservesConstantGuard() {
        GuardKey key = new GuardKey();
        assertHoisted(() -> {
            ConstantOperandGuardRootNode root = leading(key);
            root.getBytecodeNode().setUncachedThreshold(0);
            assertEquals(42, root.getCallTarget().call(42));
            root.getRootNodes().update(BytecodeConfig.WITH_SOURCE);
            return root;
        }, key);
    }

    static final class GuardKey {
        boolean allowed = true;

        @Idempotent
        boolean isAllowed() {
            return allowed;
        }

        @Idempotent
        boolean matches(Object value) {
            return allowed && Integer.valueOf(42).equals(value);
        }

        @NonIdempotent
        GuardKey currentKey() {
            return this;
        }

        @NonIdempotent
        boolean isCurrentlyAllowed() {
            return allowed;
        }
    }

    @GenerateBytecode(version = BytecodeVersion.LATEST, languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true, enableQuickening = true)
    @OperationProxy(ProxyGuardNode.class)
    @SuppressWarnings("unused")
    public abstract static class ConstantOperandGuardRootNode extends RootNode implements BytecodeRootNode {
        protected ConstantOperandGuardRootNode(BytecodeDSLTestLanguage language, FrameDescriptor.Builder descriptor) {
            super(language, descriptor.build());
        }

        @Operation
        @ConstantOperand(type = GuardKey.class)
        static final class LeadingGuard {
            @Specialization(guards = "key.isAllowed()")
            static Object allowed(GuardKey key, Object value) {
                return value;
            }

            @Specialization(replaces = "allowed")
            static Object fallback(GuardKey key, Object value) {
                return "fallback";
            }
        }

        @Operation
        @ConstantOperand(type = GuardKey.class, specifyAtEnd = true)
        static final class TrailingGuard {
            @Specialization(guards = "key.isAllowed()")
            static Object allowed(Object value, GuardKey key) {
                return value;
            }

            @Specialization(replaces = "allowed")
            static Object fallback(Object value, GuardKey key) {
                return "fallback";
            }
        }

        @Operation
        @ConstantOperand(type = GuardKey.class)
        @ConstantOperand(type = int.class, specifyAtEnd = true)
        static final class BothGuard {
            @Specialization(guards = "key.matches(marker)")
            static Object allowed(GuardKey key, Object value, int marker) {
                return value;
            }

            @Specialization(replaces = "allowed")
            static Object fallback(GuardKey key, Object value, int marker) {
                return "fallback";
            }
        }

        @Operation
        @ConstantOperand(type = GuardKey.class)
        static final class BoundGuard {
            @Specialization(guards = "bound.isAllowed()")
            static Object allowed(GuardKey key, Object value, @Bind("key") GuardKey bound) {
                return value;
            }

            @Specialization(replaces = "allowed")
            static Object fallback(GuardKey key, Object value) {
                return "fallback";
            }
        }

        @Operation
        @ConstantOperand(type = GuardKey.class)
        static final class MutableBoundGuard {
            @Specialization(guards = "bound.isAllowed()")
            static Object allowed(GuardKey key, Object value, @Bind("key.currentKey()") GuardKey bound) {
                return value;
            }

            @Specialization(replaces = "allowed")
            static Object fallback(GuardKey key, Object value) {
                return "fallback";
            }
        }

        @Operation
        @ConstantOperand(type = GuardKey.class)
        static final class MixedGuard {
            @Specialization(guards = "key.matches(value)")
            static Object allowed(GuardKey key, Object value) {
                return value;
            }

            @Specialization(replaces = "allowed")
            static Object fallback(GuardKey key, Object value) {
                return "fallback";
            }
        }

        @Operation
        @ConstantOperand(type = GuardKey.class)
        static final class FieldGuard {
            @Specialization(guards = "key.allowed")
            static Object allowed(GuardKey key, Object value) {
                return value;
            }

            @Specialization(replaces = "allowed")
            static Object fallback(GuardKey key, Object value) {
                return "fallback";
            }
        }

        @Operation
        @ConstantOperand(type = GuardKey.class)
        static final class MutableGuard {
            @Specialization(guards = "key.isCurrentlyAllowed()")
            static Object allowed(GuardKey key, Object value) {
                return value;
            }

            @Specialization(replaces = "allowed")
            static Object fallback(GuardKey key, Object value) {
                return "fallback";
            }
        }
    }

    @OperationProxy.Proxyable(allowUncached = true)
    @ConstantOperand(type = GuardKey.class)
    @GenerateUncached
    @GenerateInline(false)
    @SuppressWarnings("unused")
    abstract static class ProxyGuardNode extends Node {
        abstract Object execute(GuardKey key, Object value);

        @Specialization(guards = "key.isAllowed()")
        static Object allowed(GuardKey key, Object value) {
            return value;
        }

        @Specialization(replaces = "allowed")
        static Object fallback(GuardKey key, Object value) {
            return "fallback";
        }
    }
}
