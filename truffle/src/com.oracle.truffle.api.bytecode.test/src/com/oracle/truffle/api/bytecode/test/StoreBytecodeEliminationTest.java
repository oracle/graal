/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.Instrumentation;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation.Operator;
import com.oracle.truffle.api.bytecode.StoreBytecodeIndex;
import com.oracle.truffle.api.bytecode.test.error_tests.ExpectError;
import com.oracle.truffle.api.bytecode.test.error_tests.ExpectWarning;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class StoreBytecodeEliminationTest extends AbstractInstructionTest {

    @SuppressWarnings("unchecked")
    public static StoreBytecodeEliminationRootNode parseNode(BytecodeParser<?> builder) {
        return StoreBytecodeEliminationRootNodeGen.create(null, BytecodeConfig.WITH_SOURCE,
                        (BytecodeParser<StoreBytecodeEliminationRootNodeGen.Builder>) builder).getNode(0);
    }

    @Test
    public void testInferred() {
        StoreBytecodeEliminationRootNode root = parseNode((StoreBytecodeEliminationRootNodeGen.Builder b) -> {
            b.beginRoot();
            b.beginReturn();
            b.beginInferredTest();
            b.emitLoadArgument(0);
            b.endInferredTest();
            b.endReturn();
            b.endRoot();
        });

        assertEquals(4, root.getCallTarget().call(0));
        assertEquals(-1, root.getCallTarget().call(1));

    }

    @Test
    public void testExplicit() {
        StoreBytecodeEliminationRootNode root = parseNode((StoreBytecodeEliminationRootNodeGen.Builder b) -> {
            b.beginRoot();
            b.beginReturn();
            b.beginExplicitTest();
            b.emitLoadArgument(0);
            b.endExplicitTest();
            b.endReturn();
            b.endRoot();
        });

        // other specializations will then update it still
        assertEquals(-1, root.getCallTarget().call(0));

        // executeAndSpecialize first so its updated
        assertEquals(4, root.getCallTarget().call(1));
    }

    @Test
    public void testExplicit2() {
        StoreBytecodeEliminationRootNode root = parseNode((StoreBytecodeEliminationRootNodeGen.Builder b) -> {
            b.beginRoot();
            b.beginReturn();
            b.beginExplicitTest2();
            b.emitLoadArgument(0);
            b.endExplicitTest2();
            b.endReturn();
            b.endRoot();
        });

        // other specializations will then update it still
        assertEquals(-1, root.getCallTarget().call(0));

        // executeAndSpecialize first so its updated
        assertEquals(-1, root.getCallTarget().call(1));
    }

    @Test
    public void testExplicitUsesSingleton() {
        StoreBytecodeEliminationRootNode root = parseNode((StoreBytecodeEliminationRootNodeGen.Builder b) -> {
            b.beginRoot();
            b.beginReturn();
            b.beginExplicitTest();
            b.emitLoadArgument(0);
            b.endExplicitTest();
            b.endReturn();
            b.endRoot();
        });

        root.getBytecodeNode().setUncachedThreshold(0);

        assertEquals(-1, root.getCallTarget().call(0));

        Instruction instruction = root.getBytecodeNode().getInstructionsAsList().get(1);
        assertEquals("c.ExplicitTest", instruction.getName());
        Instruction.Argument nodeArgument = findNodeArgument(instruction);
        assertNotNull(nodeArgument);

        assertSingletonNode(nodeArgument);
        assertTrue(nodeArgument.getSpecializationInfo().get(0).isActive());
    }

    /*
     * This tests that if the frame update was optimized out, that capture stack does not fail, but
     * instead returns -1 as bytecode index.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testClearBci() {
        StoreBytecodeEliminationRootNode root = parseNode((StoreBytecodeEliminationRootNodeGen.Builder b) -> {
            b.beginRoot();
            b.emitClearBCI();
            b.beginReturn();
            b.emitCaptureStack();
            b.endReturn();
            b.endRoot();
        });

        root.getBytecodeNode().setUncachedThreshold(0);

        List<TruffleStackTraceElement> t = (List<TruffleStackTraceElement>) root.getCallTarget().call();
        assertEquals(-1, t.get(0).getBytecodeIndex());
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableQuickening = true, enableUncachedInterpreter = true, storeBytecodeIndexInFrame = true, enableSpecializationIntrospection = true)
    abstract static class StoreBytecodeEliminationRootNode extends RootNode implements BytecodeRootNode {

        private static final int BCI_INDEX;
        static {
            try {
                // this is assuming an implementation detail and expected to break
                // if changes are implemented. Please adapt this test if it breaks.
                Field f = StoreBytecodeEliminationRootNodeGen.class.getDeclaredField("BCI_INDEX");
                f.setAccessible(true);
                BCI_INDEX = (int) f.get(null);
            } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException e) {
                throw new AssertionError(e);
            }
        }

        static int readBCI(VirtualFrame frame) {
            if (!frame.isInt(BCI_INDEX)) {
                // its invalid if its never set
                return -1;
            }
            return frame.getInt(BCI_INDEX);
        }

        protected StoreBytecodeEliminationRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Instrumentation(storeBytecodeIndex = false)
        @SuppressWarnings("unused")
        public static final class InstrumentationTest {

            @Specialization(guards = "v == 0")
            public static int s0(VirtualFrame f, int v, @Bind Node node) {
                return readBCI(f);
            }

            @Specialization(guards = "v == 1")
            @StoreBytecodeIndex
            public static int s1(VirtualFrame f, int v, @Bind Node node) {
                return readBCI(f);
            }

        }

        @Operation(storeBytecodeIndex = true)
        @SuppressWarnings("unused")
        public static final class InferredTest {

            @Specialization(guards = "v == 0")
            public static int s0(VirtualFrame f, int v, @Bind Node node) {
                return readBCI(f);
            }

            @Specialization(guards = "v == 1")
            public static int s1(VirtualFrame frame, int v) {
                return readBCI(frame);
            }

        }

        // enable explicitly for one specialization
        @Operation(storeBytecodeIndex = false)
        @SuppressWarnings("unused")
        public static final class ExplicitTest {

            @Specialization(guards = "v == 0")
            public static int s0(VirtualFrame f, int v, @Bind Node node) {
                return readBCI(f);
            }

            @StoreBytecodeIndex
            @Specialization(guards = "v == 1")
            public static int s1(VirtualFrame frame, int v, @Bind Node node) {
                return readBCI(frame);
            }

        }

        // enable explicitly for no specialization
        @Operation(storeBytecodeIndex = false)
        @SuppressWarnings("unused")
        public static final class ExplicitTest2 {

            @Specialization(guards = "v == 0")
            public static int s0(VirtualFrame f, int v, @Bind Node node) {
                return readBCI(f);
            }

            @Specialization(guards = "v == 1")
            public static int s1(VirtualFrame frame, int v, @Bind Node node) {
                return readBCI(frame);
            }

        }

        // enable explicitly for no specialization
        @Operation(storeBytecodeIndex = true)
        @SuppressWarnings("unused")
        public static final class ExplicitTest3 {

            @Specialization(guards = "v == 0")
            public static int s0(VirtualFrame f, int v, @Bind Node node) {
                return readBCI(f);
            }

            @Specialization(guards = "v == 1")
            public static int s1(VirtualFrame frame, int v, @Bind Node node) {
                return readBCI(frame);
            }

        }

        @Operation(storeBytecodeIndex = false)
        public static final class ClearBCI {

            @Specialization
            public static void doDefault(VirtualFrame f) {
                f.clear(BCI_INDEX);
            }

        }

        @Operation(storeBytecodeIndex = false)
        @SuppressWarnings("unused")
        public static final class CaptureStack {

            @Specialization
            @TruffleBoundary
            public static Object doDefault(@Bind Node node) {
                return TruffleStackTrace.getStackTrace(new TestException(node));
            }

        }

    }

    @SuppressWarnings("serial")
    static class TestException extends AbstractTruffleException {
        TestException(Node node) {
            super(node);
        }

    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableQuickening = true, enableUncachedInterpreter = true, storeBytecodeIndexInFrame = true)
    @ExpectWarning("For this operation it is recommended to specify @Proxyable(storeBytecodeIndex=true|false). %")
    @OperationProxy(NoStoreBytecodeIndexProxyTest1.class)
    // no warning if the node does not bind the node
    @OperationProxy(NoStoreBytecodeIndexProxyTest2.class)
    @OperationProxy(StoreBytecodeIndexNoWarningProxy.class)
    abstract static class StoreBytecodeEliminationWarningTestRootNode extends RootNode implements BytecodeRootNode {

        protected StoreBytecodeEliminationWarningTestRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        // no warning if it can be proven by the DSL (does not bind a node)
        @Operation
        public static final class TestNoWarning1 {

            @Specialization(guards = "v == 0")
            public static int s0(int v) {
                return v;
            }

            @Specialization(guards = "v == 1")
            public static int s1(int v) {
                return v;
            }
        }

        @ExpectError("For this operation it is recommended to specify @Operation(storeBytecodeIndex=true|false).%")
        @Operation
        public static final class TestWarning1 {

            @Specialization(guards = "v == 0")
            public static int s0(int v, @SuppressWarnings("unused") @Bind Node node) {
                return v;
            }

            @Specialization(guards = "v == 1")
            public static int s1(int v) {
                return v;
            }
        }

        // no warning if set explicitly to true
        @Operation(storeBytecodeIndex = true)
        public static final class TestWarning2 {

            @Specialization(guards = "v == 0")
            public static int s0(int v, @SuppressWarnings("unused") @Bind Node node) {
                return v;
            }

            @Specialization(guards = "v == 1")
            public static int s1(int v) {
                return v;
            }
        }

        // no warning if set explicitly to false
        @Operation(storeBytecodeIndex = false)
        public static final class TestWarning3 {

            @Specialization(guards = "v == 0")
            public static int s0(int v, @SuppressWarnings("unused") @Bind Node node) {
                return v;
            }

            @Specialization(guards = "v == 1")
            public static int s1(int v) {
                return v;
            }
        }

        @Operation(storeBytecodeIndex = true)
        public static final class TestWarning4 {

            @ExpectError("The annotation @StoreBytecodeIndex has no effect on a specialization if @Operation(storeBytecodeIndex=true) is kept default or set to true.")
            @Specialization(guards = "v == 0")
            @StoreBytecodeIndex
            public static int s0(int v, @SuppressWarnings("unused") @Bind Node node) {
                return v;
            }

            @Specialization(guards = "v == 1")
            public static int s1(int v) {
                return v;
            }
        }

        @Operation(storeBytecodeIndex = false)
        public static final class TestWarning5 {

            @StoreBytecodeIndex // has effect
            @Specialization(guards = "v == 0")
            public static int s0(int v, @SuppressWarnings("unused") @Bind Node node) {
                return v;
            }

            @ExpectError("The annotation @StoreBytecodeIndex has no effect on a specialization if the DSL can infer that this specialization does not require a stored bytecode index. Remove the annotation to resolve this error.")
            @StoreBytecodeIndex // has no effect -> proven no bytecode update
            @Specialization(guards = "v == 1")
            public static int s1(int v) {
                return v;
            }
        }

        @ExpectError("The attribute @Operation(storeBytecodeIndex=true) is set, but the DSL infers that this operation does need an updated bytecode index. Please suppress this warning if the DSL is wrong and the operation does need it.%")
        @Operation(storeBytecodeIndex = true)
        public static final class TestWarning6 {
            @Specialization(guards = "v == 0")
            public static int s0(int v) {
                return v;
            }

            @Specialization(guards = "v == 1")
            public static int s1(int v) {
                return v;
            }
        }

        // make sure validation works for instrumentations too
        // it is the same code so we do not perform additional testing.
        @Instrumentation(storeBytecodeIndex = false)
        @SuppressWarnings("unused")
        public static final class InstrumentationTest {
            @ExpectError("The annotation @StoreBytecodeIndex has no effect on a specialization if the DSL can infer that this specialization does not require a stored bytecode index. Remove the annotation to resolve this error.")
            @StoreBytecodeIndex
            @Specialization(guards = "v == 0")
            public static int s0(int v) {
                return v;
            }

            @Specialization(guards = "v == 1")
            public static int s1(int v) {
                return v;
            }
        }

    }

    // make sure validation works for instrumentations too
    // it is the same code so we do not perform additional testing.
    @OperationProxy.Proxyable(allowUncached = true)
    public static final class NoStoreBytecodeIndexProxyTest1 {

        @Specialization(guards = "v == 0")
        @SuppressWarnings("unused")
        public static int s0(int v, @Bind Node node) {
            return v;
        }

        @Specialization(guards = "v == 1")
        public static int s1(int v) {
            return v;
        }
    }

    @OperationProxy.Proxyable(allowUncached = true)
    public static final class NoStoreBytecodeIndexProxyTest2 {

        @Specialization(guards = "v == 0")
        @SuppressWarnings("unused")
        public static int s0(int v) {
            return v;
        }

        @Specialization(guards = "v == 1")
        public static int s1(int v) {
            return v;
        }
    }

    @OperationProxy.Proxyable(allowUncached = true, storeBytecodeIndex = true)
    public static final class StoreBytecodeIndexNoWarningProxy {

        @Specialization(guards = "v == 0")
        @SuppressWarnings("unused")
        public static int s0(int v, @Bind Node node) {
            return v;
        }

        @Specialization(guards = "v == 1")
        public static int s1(int v) {
            return v;
        }
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableQuickening = true, enableUncachedInterpreter = true, storeBytecodeIndexInFrame = true)
    @ExpectError("For this operation it is recommended to specify @Operation(storeBytecodeIndex=true|false).%")
    @ShortCircuitOperation(name = "BoolAnd", booleanConverter = ExternalProxyNode.class, operator = Operator.AND_RETURN_VALUE)
    abstract static class StoreBytecodeEliminationWarningTestRootNode2 extends RootNode implements BytecodeRootNode {

        protected StoreBytecodeEliminationWarningTestRootNode2(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

    }

}
