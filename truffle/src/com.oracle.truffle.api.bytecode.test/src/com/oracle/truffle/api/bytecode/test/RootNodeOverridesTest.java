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
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;

public class RootNodeOverridesTest {

    private static final BytecodeDSLTestLanguage LANGUAGE = null;

    private static RootNodeWithOverrides parseRootNodeWithOverrides(BytecodeParser<RootNodeWithOverridesGen.Builder> builder) {
        BytecodeRootNodes<RootNodeWithOverrides> nodes = RootNodeWithOverridesGen.create(LANGUAGE, BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(0);
    }

    private static RootNodeWithParentOverrides parseRootNodeWithParentOverrides(BytecodeParser<RootNodeWithParentOverridesGen.Builder> builder) {
        BytecodeRootNodes<RootNodeWithParentOverrides> nodes = RootNodeWithParentOverridesGen.create(LANGUAGE, BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(0);
    }

    @Test
    public void testPrepareForCompilation() {
        RootNodeWithOverrides root = parseRootNodeWithOverrides(b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endRoot();
        });

        // As long as the interpreter is uncached, it should not be ready.
        assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());
        root.readyForCompilation = false;
        assertEquals(false, root.prepareForCompilation(true, 2, true));
        root.readyForCompilation = true;
        assertEquals(false, root.prepareForCompilation(true, 2, true));

        // Transition to cached.
        root.getBytecodeNode().setUncachedThreshold(0);
        root.getCallTarget().call();
        assertEquals(BytecodeTier.CACHED, root.getBytecodeNode().getTier());

        // When the interpreter is cached, the parent impl should be delegated to.
        root.readyForCompilation = false;
        assertEquals(false, root.prepareForCompilation(true, 2, true));
        root.readyForCompilation = true;
        assertEquals(true, root.prepareForCompilation(true, 2, true));
    }

    @Test
    public void testPrepareForCompilationContinuation() {
        RootNodeWithOverrides root = parseRootNodeWithOverrides(b -> {
            b.beginRoot();
            b.beginYield();
            b.emitLoadConstant(42L);
            b.endYield();
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endRoot();
        });

        // As long as the interpreter is uncached, it should not be ready.
        ContinuationResult cont = (ContinuationResult) root.getCallTarget().call();
        assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());
        root.readyForCompilation = false;
        assertEquals(false, invokePrepareForCompilation(cont.getContinuationRootNode()));
        root.readyForCompilation = true;
        assertEquals(false, invokePrepareForCompilation(cont.getContinuationRootNode()));

        // Transition to cached.
        root.getBytecodeNode().setUncachedThreshold(0);
        cont = (ContinuationResult) root.getCallTarget().call();
        assertEquals(BytecodeTier.CACHED, root.getBytecodeNode().getTier());

        // When the interpreter is cached, the parent impl should be delegated to.
        root.readyForCompilation = false;
        assertEquals(false, invokePrepareForCompilation(cont.getContinuationRootNode()));
        root.readyForCompilation = true;
        assertEquals(true, invokePrepareForCompilation(cont.getContinuationRootNode()));
    }

    @Test
    public void testPrepareForCompilationContinuationParentOverrides() {
        RootNodeWithParentOverrides root = parseRootNodeWithParentOverrides(b -> {
            b.beginRoot();
            b.beginYield();
            b.emitLoadConstant(42L);
            b.endYield();
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endRoot();
        });

        ContinuationResult cont = (ContinuationResult) root.getCallTarget().call();
        root.readyForCompilation = false;
        assertEquals(false, invokePrepareForCompilation(cont.getContinuationRootNode()));
        root.readyForCompilation = true;
        assertEquals(true, invokePrepareForCompilation(cont.getContinuationRootNode()));
    }

    private static boolean invokePrepareForCompilation(ContinuationRootNode cont) {
        try {
            return (boolean) cont.getClass().getMethod("prepareForCompilation", boolean.class, int.class, boolean.class).invoke(cont, true, 2, true);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Exception invoking prepareForCompilation", ex);
        }
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableYield = true, enableUncachedInterpreter = true)
    abstract static class RootNodeWithOverrides extends RootNode implements BytecodeRootNode {
        public boolean readyForCompilation = false;

        protected RootNodeWithOverrides(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        public static final class Add {
            @Specialization
            static int add(int x, int y) {
                return x + y;
            }
        }

        // The generated code should delegate to this method.
        @Override
        public boolean prepareForCompilation(boolean rootCompilation, int compilationTier, boolean lastTier) {
            return readyForCompilation;
        }

    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableYield = true)
    abstract static class RootNodeWithParentOverrides extends ParentClassWithOverride implements BytecodeRootNode {

        protected RootNodeWithParentOverrides(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        public static final class Add {
            @Specialization
            static int add(int x, int y) {
                return x + y;
            }
        }

        // This root node has no uncached interpreter, so there should be no override. The
        // continuation root node should still delegate to the parent implementation.
    }

    abstract static class ParentClassWithOverride extends RootNode {
        public boolean readyForCompilation = false;

        protected ParentClassWithOverride(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Override
        public boolean prepareForCompilation(boolean rootCompilation, int compilationTier, boolean lastTier) {
            return readyForCompilation;
        }
    }

}
