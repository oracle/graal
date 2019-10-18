/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.GenerateWrapper.OutgoingConverter;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

public class GenerateWrapperTest extends AbstractPolyglotTest {

    /**
     * Default wrapper behavior.
     */
    @GenerateWrapper
    public static class DefaultNode extends TestExecutionSignatures {
        @Override
        public WrapperNode createWrapper(ProbeNode probeNode) {
            return new DefaultNodeWrapper(this, probeNode);
        }
    }

    @Test
    public void testDefaultWrapper() {
        setupEnv();
        DefaultNode instrumentedNode = new DefaultNode();
        Supplier<DefaultNode> node = adoptNode(instrumentedNode);

        VirtualFrame testFrame = Truffle.getRuntime().createVirtualFrame(new Object[0], new FrameDescriptor());
        assertExecutionEvent(instrumentedNode, testFrame, "execute2", () -> node.get().execute1(testFrame));
        assertExecutionEvent(instrumentedNode, testFrame, "execute2", () -> node.get().execute2(testFrame));
        assertExecutionEvent(instrumentedNode, testFrame, 42, () -> node.get().execute3(testFrame));
        assertExecutionEvent(instrumentedNode, testFrame, "execute4", () -> node.get().execute4(testFrame));
        assertExecutionEvent(instrumentedNode, testFrame, 42.0d, () -> node.get().execute5(testFrame));
        assertExecutionEvent(instrumentedNode, testFrame, 42L, () -> node.get().execute6(testFrame));
        assertExecutionEvent(instrumentedNode, testFrame, 42.0f, () -> node.get().execute7(testFrame));
        assertExecutionEvent(instrumentedNode, testFrame, (short) 42, () -> node.get().execute8(testFrame));
        assertExecutionEvent(instrumentedNode, testFrame, (byte) 42, () -> node.get().execute9(testFrame));
        assertExecutionEvent(instrumentedNode, testFrame, "execute10", () -> node.get().execute10(testFrame));
        assertExecutionEvent(instrumentedNode, testFrame, "execute11 42", () -> node.get().execute11(testFrame, 42, null));
        assertExecutionEvent(instrumentedNode, testFrame, "execute12 42", () -> node.get().execute12(testFrame, 42));
        assertExecutionEvent(instrumentedNode, testFrame, "execute13 42:42", () -> node.get().execute13(testFrame, 42, null, 42));
        assertExecutionEvent(instrumentedNode, testFrame, 42, () -> {
            try {
                node.get().execute15(testFrame);
                fail();
            } catch (UnexpectedResultException e) {
                assertEquals(42, e.getResult());
            }
        });
        assertExecutionEventFailed(instrumentedNode, testFrame, (e) -> assertTrue(e instanceof IOException), () -> {
            try {
                node.get().execute16(testFrame);
                fail();
            } catch (IOException e) {
            }
        });
        assertExecutionEvent(instrumentedNode, testFrame, 42, () -> {
            try {
                node.get().execute17(testFrame, 4, null, 4);
                fail();
            } catch (UnexpectedResultException e) {
                assertEquals(42, e.getResult());
            }
        });
    }

    @Test
    public void testUnwindReturnValueInEnter() {
        if (System.getProperty("java.vm.name").contains("Graal:graal-enterprise")) {
            return; // GR-16755
        }
        setupEnv();
        DefaultNode instrumentedNode = new DefaultNode();
        Supplier<DefaultNode> node = adoptNode(instrumentedNode);

        VirtualFrame testFrame = Truffle.getRuntime().createVirtualFrame(new Object[0], new FrameDescriptor());

        assertUnwindInEnter(instrumentedNode, testFrame, "executeUnwind2", () -> node.get().execute2(testFrame));
        assertUnwindInEnter(instrumentedNode, testFrame, 43, () -> node.get().execute3(testFrame));
        assertUnwindInEnter(instrumentedNode, testFrame, "executeUnwind4", () -> node.get().execute4(testFrame));
        assertUnwindInEnter(instrumentedNode, testFrame, 43.0d, () -> node.get().execute5(testFrame));
        assertUnwindInEnter(instrumentedNode, testFrame, 43L, () -> node.get().execute6(testFrame));
        assertUnwindInEnter(instrumentedNode, testFrame, 43.0f, () -> node.get().execute7(testFrame));
        assertUnwindInEnter(instrumentedNode, testFrame, (short) 43, () -> node.get().execute8(testFrame));
        assertUnwindInEnter(instrumentedNode, testFrame, (byte) 43, () -> node.get().execute9(testFrame));
        assertUnwindInEnter(instrumentedNode, testFrame, "executeUnwind10", () -> node.get().execute10(testFrame));
        assertUnwindInEnter(instrumentedNode, testFrame, "executeUnwind11 42", () -> node.get().execute11(testFrame, 42, null));
        assertUnwindInEnter(instrumentedNode, testFrame, "executeUnwind12 42", () -> node.get().execute12(testFrame, 42));
        assertUnwindInEnter(instrumentedNode, testFrame, "executeUnwind13 42:42", () -> node.get().execute13(testFrame, 42, null, 42));

        try {
            assertUnwindInEnter(instrumentedNode, testFrame, "", () -> node.get().execute7(testFrame));
            fail();
        } catch (ClassCastException e) {
            // expects to fail with ClassCastException cannot return the value.
        }
    }

    @Test
    public void testUnwindReturnValueInReturn() {
        if (System.getProperty("java.vm.name").contains("Graal:graal-enterprise")) {
            return; // GR-16755
        }
        setupEnv();
        DefaultNode instrumentedNode = new DefaultNode();
        Supplier<DefaultNode> node = adoptNode(instrumentedNode);

        VirtualFrame testFrame = Truffle.getRuntime().createVirtualFrame(new Object[0], new FrameDescriptor());

        assertUnwindInReturn(instrumentedNode, testFrame, "executeUnwind2", () -> node.get().execute2(testFrame));
        assertUnwindInReturn(instrumentedNode, testFrame, 43, () -> node.get().execute3(testFrame));
        assertUnwindInReturn(instrumentedNode, testFrame, "executeUnwind4", () -> node.get().execute4(testFrame));
        assertUnwindInReturn(instrumentedNode, testFrame, 43.0d, () -> node.get().execute5(testFrame));
        assertUnwindInReturn(instrumentedNode, testFrame, 43L, () -> node.get().execute6(testFrame));
        assertUnwindInReturn(instrumentedNode, testFrame, 43.0f, () -> node.get().execute7(testFrame));
        assertUnwindInReturn(instrumentedNode, testFrame, (short) 43, () -> node.get().execute8(testFrame));
        assertUnwindInReturn(instrumentedNode, testFrame, (byte) 43, () -> node.get().execute9(testFrame));
        assertUnwindInReturn(instrumentedNode, testFrame, "executeUnwind10", () -> node.get().execute10(testFrame));
        assertUnwindInReturn(instrumentedNode, testFrame, "executeUnwind11 42", () -> node.get().execute11(testFrame, 42, null));
        assertUnwindInReturn(instrumentedNode, testFrame, "executeUnwind12 42", () -> node.get().execute12(testFrame, 42));
        assertUnwindInReturn(instrumentedNode, testFrame, "executeUnwind13 42:42", () -> node.get().execute13(testFrame, 42, null, 42));

        try {
            assertUnwindInReturn(instrumentedNode, testFrame, "", () -> node.get().execute7(testFrame));
            fail();
        } catch (ClassCastException e) {
            // expects to fail with ClassCastException cannot return the value.
        }
    }

    private void assertUnwindInEnter(Node node, VirtualFrame expectedFrame, Object unwindValue, Supplier<Object> r) {
        List<String> events = new ArrayList<>();
        EventBinding<?> binding = instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
            public void onEnter(EventContext c, VirtualFrame frame) {
                events.add("onEnter");
                assertSame(c.getInstrumentedNode(), node);
                assertSame(expectedFrame, frame);
                throw c.createUnwind(unwindValue);
            }

            public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {
                fail();
            }

            public Object onUnwind(EventContext c, VirtualFrame frame, Object info) {
                events.add("onUnwind");
                assertSame(c.getInstrumentedNode(), node);
                assertSame(expectedFrame, frame);
                return info;
            }

            public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {
                events.add("onReturnExceptional");
                assertTrue(exception instanceof ThreadDeath);
            }
        });
        Object returnValue = r.get();
        assertEquals(unwindValue, returnValue);
        assertEquals("Execution event did not trigger.", Arrays.asList("onEnter", "onReturnExceptional", "onUnwind"), events);
        binding.dispose();
    }

    private void assertUnwindInReturn(Node node, VirtualFrame expectedFrame, Object unwindValue, Supplier<Object> r) {
        List<String> events = new ArrayList<>();
        EventBinding<?> binding = instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
            public void onEnter(EventContext c, VirtualFrame frame) {
                events.add("onEnter");
                assertSame(c.getInstrumentedNode(), node);
                assertSame(expectedFrame, frame);
            }

            public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {
                events.add("onReturnValue");
                assertSame(c.getInstrumentedNode(), node);
                assertSame(expectedFrame, frame);
                throw c.createUnwind(unwindValue);
            }

            public Object onUnwind(EventContext c, VirtualFrame frame, Object info) {
                events.add("onUnwind");
                assertSame(c.getInstrumentedNode(), node);
                assertSame(expectedFrame, frame);
                assertEquals(unwindValue, info);
                return info;
            }

            public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {
                fail();
            }
        });
        Object returnValue = r.get();
        assertEquals(unwindValue, returnValue);
        assertEquals("Execution event did not trigger.", Arrays.asList("onEnter", "onReturnValue", "onUnwind"), events);
        binding.dispose();
    }

    private void assertExecutionEventFailed(Node node, VirtualFrame expectedFrame, Consumer<Throwable> validator, Runnable r) {
        List<String> events = new ArrayList<>();
        EventBinding<?> binding = instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
            public void onEnter(EventContext c, VirtualFrame frame) {
                events.add("onEnter");
                assertSame(c.getInstrumentedNode(), node);
                assertSame(expectedFrame, frame);
            }

            public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {
                fail();
            }

            public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {
                assertSame(expectedFrame, frame);
                events.add("onReturnExceptional");
                if (exception instanceof AssertionError) {
                    throw (AssertionError) exception;
                }
                validator.accept(exception);
            }
        });
        r.run();
        assertEquals("Execution event did not trigger.", Arrays.asList("onEnter", "onReturnExceptional"), events);
        binding.dispose();
    }

    private void assertExecutionEvent(Node node, VirtualFrame expectedFrame, Object expectedResult, Runnable r) {
        List<String> events = new ArrayList<>();
        EventBinding<?> binding = instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
            public void onEnter(EventContext c, VirtualFrame frame) {
                events.add("onEnter");
                assertSame(expectedFrame, frame);
                assertSame(c.getInstrumentedNode(), node);
            }

            public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {
                events.add("onReturnValue");
                assertSame(expectedFrame, frame);
                assertEquals(expectedResult, result);
            }

            public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {
                if (exception instanceof AssertionError) {
                    throw (AssertionError) exception;
                }
                throw new AssertionError("Error not expected", exception);
            }
        });
        r.run();
        assertEquals("Execution event did not trigger.", Arrays.asList("onEnter", "onReturnValue"), events);
        binding.dispose();
    }

    @GenerateWrapper
    public static class OutgoingValueConverterTestNode extends TestExecutionSignatures {
        @Override
        public WrapperNode createWrapper(ProbeNode probeNode) {
            return new OutgoingValueConverterTestNodeWrapper(this, probeNode);
        }

        BiFunction<VirtualFrame, Object, Object> validator;

        @OutgoingConverter
        Object outgoing(VirtualFrame frame, Object object) {
            return validator.apply(frame, object);
        }
    }

    @Test
    public void testOutgoingValueConverter() {
        setupEnv();
        OutgoingValueNode instrumentedNode = new OutgoingValueNode();
        Supplier<OutgoingValueNode> node = adoptNode(instrumentedNode);

        VirtualFrame testFrame = Truffle.getRuntime().createVirtualFrame(new Object[0], new FrameDescriptor());
        assertOutgoingConverter(instrumentedNode, testFrame, "execute2", "executeUnwind2", () -> node.get().execute2(testFrame));
        assertOutgoingConverter(instrumentedNode, testFrame, 42, 43, () -> node.get().execute3(testFrame));
        assertOutgoingConverter(instrumentedNode, testFrame, "execute4", "executeUnwind4", () -> node.get().execute4(testFrame));
        assertOutgoingConverter(instrumentedNode, testFrame, 42d, 43.0d, () -> node.get().execute5(testFrame));
        assertOutgoingConverter(instrumentedNode, testFrame, 42L, 43L, () -> node.get().execute6(testFrame));
        assertOutgoingConverter(instrumentedNode, testFrame, 42.0f, 43.0f, () -> node.get().execute7(testFrame));
        assertOutgoingConverter(instrumentedNode, testFrame, (short) 42, (short) 43, () -> node.get().execute8(testFrame));
        assertOutgoingConverter(instrumentedNode, testFrame, (byte) 42, (byte) 43, () -> node.get().execute9(testFrame));
        assertOutgoingConverter(instrumentedNode, testFrame, "execute10", "executeUnwind10", () -> node.get().execute10(testFrame));
        assertOutgoingConverter(instrumentedNode, testFrame, "execute11 42", "executeUnwind11 42", () -> node.get().execute11(testFrame, 42, null));
        assertOutgoingConverter(instrumentedNode, testFrame, "execute12 42", "executeUnwind12 42", () -> node.get().execute12(testFrame, 42));
        assertOutgoingConverter(instrumentedNode, testFrame, "execute13 42:42", "executeUnwind13 42:42", () -> node.get().execute13(testFrame, 42, null, 42));
    }

    private void assertOutgoingConverter(OutgoingValueNode node, VirtualFrame expectedFrame, Object value, Object alternativeValue, Supplier<Object> r) {
        List<String> events = new ArrayList<>();
        EventBinding<?> binding = instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
            public void onEnter(EventContext c, VirtualFrame frame) {
                events.add("onEnter");
                assertSame(expectedFrame, frame);
                assertSame(c.getInstrumentedNode(), node);
            }

            public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {
                events.add("onReturnValue");
                assertSame(expectedFrame, frame);
                assertSame(c.getInstrumentedNode(), node);
                assertEquals(alternativeValue, result);
            }

            public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {
                fail();
            }
        });
        node.validator = (frame, o) -> {
            assertSame(expectedFrame, frame);
            assertEquals(value, o);
            return alternativeValue;
        };
        assertEquals(value, r.get());
        assertEquals("Execution event did not trigger.", Arrays.asList("onEnter", "onReturnValue"), events);
        binding.dispose();
    }

    @Test
    public void testIncomingValueConverter() {
        if (System.getProperty("java.vm.name").contains("Graal:graal-enterprise")) {
            return; // GR-16755
        }
        setupEnv();
        IncomingValueNode instrumentedNode = new IncomingValueNode();
        Supplier<IncomingValueNode> node = adoptNode(instrumentedNode);

        VirtualFrame testFrame = Truffle.getRuntime().createVirtualFrame(new Object[0], new FrameDescriptor());

        assertIncomingConverter(instrumentedNode, testFrame, "execute2", "executeUnwind2", () -> node.get().execute2(testFrame));
        assertIncomingConverter(instrumentedNode, testFrame, 42, 43, () -> node.get().execute3(testFrame));
        assertIncomingConverter(instrumentedNode, testFrame, "execute4", "executeUnwind4", () -> node.get().execute4(testFrame));
        assertIncomingConverter(instrumentedNode, testFrame, 42d, 43.0d, () -> node.get().execute5(testFrame));
        assertIncomingConverter(instrumentedNode, testFrame, 42L, 43L, () -> node.get().execute6(testFrame));
        assertIncomingConverter(instrumentedNode, testFrame, 42.0f, 43.0f, () -> node.get().execute7(testFrame));
        assertIncomingConverter(instrumentedNode, testFrame, (short) 42, (short) 43, () -> node.get().execute8(testFrame));
        assertIncomingConverter(instrumentedNode, testFrame, (byte) 42, (byte) 43, () -> node.get().execute9(testFrame));
        assertIncomingConverter(instrumentedNode, testFrame, "execute10", "executeUnwind10", () -> node.get().execute10(testFrame));
        assertIncomingConverter(instrumentedNode, testFrame, "execute11 42", "executeUnwind11 42", () -> node.get().execute11(testFrame, 42, null));
        assertIncomingConverter(instrumentedNode, testFrame, "execute12 42", "executeUnwind12 42", () -> node.get().execute12(testFrame, 42));
        assertIncomingConverter(instrumentedNode, testFrame, "execute13 42:42", "executeUnwind13 42:42", () -> node.get().execute13(testFrame, 42, null, 42));
    }

    private void assertIncomingConverter(IncomingValueNode node, VirtualFrame expectedFrame, Object value, Object unwindValue, Supplier<Object> r) {
        List<String> events = new ArrayList<>();
        EventBinding<?> binding = instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
            public void onEnter(EventContext c, VirtualFrame frame) {
                events.add("onEnter");
                assertSame(expectedFrame, frame);
                assertSame(c.getInstrumentedNode(), node);
            }

            public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {
                events.add("onReturnValue");
                assertSame(expectedFrame, frame);
                assertSame(c.getInstrumentedNode(), node);
                throw c.createUnwind(result);
            }

            public Object onUnwind(EventContext c, VirtualFrame frame, Object info) {
                events.add("onUnwind");
                assertSame(expectedFrame, frame);
                assertSame(c.getInstrumentedNode(), node);
                return info;
            }

            public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {
                fail();
            }
        });
        node.validator = (frame, o) -> {
            assertSame(expectedFrame, frame);
            assertEquals(value, o);
            return unwindValue;
        };
        assertEquals(unwindValue, r.get());
        assertEquals("Execution event did not trigger.", Arrays.asList("onEnter", "onReturnValue", "onUnwind"), events);
        binding.dispose();
    }

    @GenerateWrapper
    public static class IncomingValueNode extends TestExecutionSignatures {
        @Override
        public WrapperNode createWrapper(ProbeNode probeNode) {
            return new IncomingValueNodeWrapper(this, probeNode);
        }

        BiFunction<VirtualFrame, Object, Object> validator;

        @GenerateWrapper.IncomingConverter
        Object incoming(VirtualFrame frame, Object object) {
            return validator.apply(frame, object);
        }
    }

    @GenerateWrapper
    public static class OutgoingValueNode extends TestExecutionSignatures {
        @Override
        public WrapperNode createWrapper(ProbeNode probeNode) {
            return new OutgoingValueNodeWrapper(this, probeNode);
        }

        BiFunction<VirtualFrame, Object, Object> validator;

        @GenerateWrapper.OutgoingConverter
        Object outgoing(VirtualFrame frame, Object object) {
            return validator.apply(frame, object);
        }
    }

    // frame can be ommitted
    @GenerateWrapper
    public static class IncomingValueNode2 extends TestExecutionSignatures {
        @Override
        public WrapperNode createWrapper(ProbeNode probeNode) {
            return new IncomingValueNode2Wrapper(this, probeNode);
        }

        @GenerateWrapper.IncomingConverter
        Object convert(Object object) {
            return object;
        }
    }

    // compiles with static methods.
    @GenerateWrapper
    @SuppressWarnings("unused")
    public static class StaticMethodNode extends TestExecutionSignatures {
        @Override
        public WrapperNode createWrapper(ProbeNode probeNode) {
            return null;
        }

        @GenerateWrapper.OutgoingConverter
        static Object convert1(VirtualFrame frame, Object object) {
            return object;
        }

        @GenerateWrapper.IncomingConverter
        static Object convert2(VirtualFrame frame, Object object) {
            return object;
        }
    }

    @ExpectError("Only one @IncomingConverter method allowed, found multiple.")
    @GenerateWrapper
    public static class ErrorIncomingValueNode1 extends TestExecutionSignatures {
        @Override
        public WrapperNode createWrapper(ProbeNode probeNode) {
            return null;
        }

        @GenerateWrapper.IncomingConverter
        Object convert1(Object object) {
            return object;
        }

        @GenerateWrapper.IncomingConverter
        Object convert2(Object object) {
            return object;
        }
    }

    @ExpectError("Only one @OutgoingConverter method allowed, found multiple.")
    @GenerateWrapper
    public static class ErrorIncomingValueNode2 extends TestExecutionSignatures {
        @Override
        public WrapperNode createWrapper(ProbeNode probeNode) {
            return null;
        }

        @GenerateWrapper.OutgoingConverter
        Object convert1(Object object) {
            return object;
        }

        @GenerateWrapper.OutgoingConverter
        Object convert2(Object object) {
            return object;
        }
    }

    @GenerateWrapper
    public static class ErrorIncomingValueNode3 extends TestExecutionSignatures {
        @Override
        public WrapperNode createWrapper(ProbeNode probeNode) {
            return null;
        }

        @SuppressWarnings("static-method")
        @ExpectError("Method annotated with @OutgoingConverter must not be private.")
        @GenerateWrapper.OutgoingConverter
        private Object convert1(Object object) {
            return object;
        }

        @SuppressWarnings("static-method")
        @ExpectError("Method annotated with @IncomingConverter must not be private.")
        @GenerateWrapper.IncomingConverter
        private Object convert2(Object object) {
            return object;
        }

    }

    @GenerateWrapper
    @SuppressWarnings("unused")
    public static class ErrorIncomingValueNode4 extends TestExecutionSignatures {
        @Override
        public WrapperNode createWrapper(ProbeNode probeNode) {
            return null;
        }

        @ExpectError("Invalid @OutgoingConverter method signature. Must be either Object converter(Object) or Object converter(VirtualFrame, Object)")
        @GenerateWrapper.OutgoingConverter
        Object convert1(Object object, Object object2) {
            return object;
        }

        @ExpectError("Invalid @IncomingConverter method signature. Must be either Object converter(Object) or Object converter(VirtualFrame, Object)")
        @GenerateWrapper.IncomingConverter
        Object convert2(Object object, Object object2) {
            return object;
        }

    }

    @GenerateWrapper
    @SuppressWarnings("unused")
    public static class ErrorIncomingValueNode5 extends TestExecutionSignatures {
        @Override
        public WrapperNode createWrapper(ProbeNode probeNode) {
            return null;
        }

        @ExpectError("Invalid @OutgoingConverter method signature. Must be either Object converter(Object) or Object converter(VirtualFrame, Object)")
        @GenerateWrapper.OutgoingConverter
        void convert1(Object object) {
        }

        @ExpectError("Invalid @IncomingConverter method signature. Must be either Object converter(Object) or Object converter(VirtualFrame, Object)")
        @GenerateWrapper.IncomingConverter
        void convert2(Object object) {
        }

    }

    @GenerateWrapper
    public static class ErrorIncomingValueNode6 extends TestExecutionSignatures {
        @Override
        public WrapperNode createWrapper(ProbeNode probeNode) {
            return null;
        }

        @ExpectError("Invalid @OutgoingConverter method signature. Must be either Object converter(Object) or Object converter(VirtualFrame, Object)")
        @GenerateWrapper.OutgoingConverter
        Object convert1() {
            return null;
        }

        @ExpectError("Invalid @IncomingConverter method signature. Must be either Object converter(Object) or Object converter(VirtualFrame, Object)")
        @GenerateWrapper.IncomingConverter
        Object convert2() {
            return null;
        }

    }

    @GenerateWrapper
    @SuppressWarnings("unused")
    public static class ErrorIncomingValueNode7 extends TestExecutionSignatures {
        @Override
        public WrapperNode createWrapper(ProbeNode probeNode) {
            return null;
        }

        @ExpectError("Invalid @OutgoingConverter method signature. Must be either Object converter(Object) or Object converter(VirtualFrame, Object)")
        @GenerateWrapper.OutgoingConverter
        Object convert1(String s) {
            return null;
        }

        @ExpectError("Invalid @IncomingConverter method signature. Must be either Object converter(Object) or Object converter(VirtualFrame, Object)")
        @GenerateWrapper.IncomingConverter
        Object convert2(String s) {
            return null;
        }

    }

    @GenerateWrapper
    @SuppressWarnings("unused")
    public static class ErrorIncomingValueNode8 extends TestExecutionSignatures {
        @Override
        public WrapperNode createWrapper(ProbeNode probeNode) {
            return null;
        }

        @ExpectError("Invalid @OutgoingConverter method signature. Must be either Object converter(Object) or Object converter(VirtualFrame, Object)")
        @GenerateWrapper.OutgoingConverter
        Object convert1(Frame f, Object s) {
            return null;
        }

        @ExpectError("Invalid @IncomingConverter method signature. Must be either Object converter(Object) or Object converter(VirtualFrame, Object)")
        @GenerateWrapper.IncomingConverter
        Object convert2(Frame f, Object s) {
            return null;
        }

    }

    @SuppressWarnings("unused")
    public abstract static class TestExecutionSignatures extends Node implements InstrumentableNode {

        public boolean isInstrumentable() {
            return true;
        }

        public void execute1(VirtualFrame frame) {
        }

        public Object execute2(VirtualFrame frame) {
            return "execute2";
        }

        public int execute3(VirtualFrame frame) {
            return 42;
        }

        public String execute4(VirtualFrame frame) {
            return "execute4";
        }

        public double execute5(VirtualFrame frame) {
            return 42.0d;
        }

        public long execute6(VirtualFrame frame) {
            return 42L;
        }

        public float execute7(VirtualFrame frame) {
            return 42.0f;
        }

        public short execute8(VirtualFrame frame) {
            return 42;
        }

        public byte execute9(VirtualFrame frame) {
            return 42;
        }

        public Object execute10(VirtualFrame frame1) {
            return "execute10";
        }

        public Object execute11(VirtualFrame frame, int a, VirtualFrame frame1) {
            return "execute11 " + a;
        }

        public Object execute12(VirtualFrame frame1, int b) {
            return "execute12 " + b;
        }

        public Object execute13(VirtualFrame frame, int a, VirtualFrame frame1, int b) {
            return "execute13 " + a + ":" + b;
        }

        public byte execute15(VirtualFrame frame) throws UnexpectedResultException {
            throw new UnexpectedResultException(42);
        }

        public byte execute16(VirtualFrame frame) throws IOException {
            throw new IOException("io");
        }

        public Object execute17(VirtualFrame frame, int a, VirtualFrame frame1, int b) throws UnexpectedResultException {
            throw new UnexpectedResultException(42);
        }

    }

    // test constructor with source section
    @GenerateWrapper
    public abstract static class TestConstructorWithSource extends Node implements InstrumentableNode {

        private final SourceSection sourceSection;

        public TestConstructorWithSource(SourceSection sourceSection) {
            this.sourceSection = sourceSection;
        }

        @Override
        public WrapperNode createWrapper(ProbeNode probeNode) {
            return new TestConstructorWithSourceWrapper(sourceSection, this, probeNode);
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }

        public boolean isInstrumentable() {
            return false;
        }

        public abstract void execute1(VirtualFrame frame);

    }

    // test copy constructor
    @GenerateWrapper
    public abstract static class TestCopyConstructor extends Node implements InstrumentableNode {

        public TestCopyConstructor(@SuppressWarnings("unused") TestCopyConstructor sourceSection) {
        }

        public abstract void execute1(VirtualFrame frame);

        @Override
        public WrapperNode createWrapper(ProbeNode probeNode) {
            return new TestCopyConstructorWrapper(null, this, probeNode);
        }

        public boolean isInstrumentable() {
            return false;
        }
    }

    @ExpectError("Class must not be final to generate a wrapper.")
    @GenerateWrapper
    public static final class ErrorNode0 extends Node implements InstrumentableNode {

        public WrapperNode createWrapper(ProbeNode probe) {
            return null;
        }

        public boolean isInstrumentable() {
            return false;
        }
    }

    @ExpectError("Class must not be private to generate a wrapper.")
    @GenerateWrapper
    private static class ErrorNode2 extends Node implements InstrumentableNode {

        public WrapperNode createWrapper(ProbeNode probe) {
            return null;
        }

        public boolean isInstrumentable() {
            return false;
        }
    }

    @ExpectError("Inner class must be static to generate a wrapper.")
    @GenerateWrapper
    public class ErrorNode3 extends Node implements InstrumentableNode {

        public WrapperNode createWrapper(ProbeNode probe) {
            return null;
        }

        public boolean isInstrumentable() {
            return false;
        }
    }

    @ExpectError("No methods starting with name execute found to wrap.")
    @GenerateWrapper
    public static class ErrorNode4 extends Node implements InstrumentableNode {

        public WrapperNode createWrapper(ProbeNode probe) {
            return null;
        }

        public boolean isInstrumentable() {
            return false;
        }

        @SuppressWarnings("unused")
        private void execute1() {
        }

        public final void execute3() {
        }
    }

    @GenerateWrapper
    @SuppressWarnings("unused")
    public abstract static class DelegateAbstractMethod extends Node implements InstrumentableNode {

        public void execute(VirtualFrame frame) {
        }

        public WrapperNode createWrapper(ProbeNode probe) {
            return null;
        }

        public boolean isInstrumentable() {
            return false;
        }

        public abstract void foobar();
    }

    public void testDelegateAbstractMethod() {
        AtomicInteger foobarInvocations = new AtomicInteger();
        DelegateAbstractMethod node = new DelegateAbstractMethod() {

            @Override
            public void execute(VirtualFrame frame) {

            }

            @Override
            public void foobar() {
                foobarInvocations.incrementAndGet();
            }
        };

        DelegateAbstractMethod wrapper = new DelegateAbstractMethodWrapper(node, null);
        wrapper.foobar();
        assertEquals(1, foobarInvocations.get());
    }

    @ExpectError("No suiteable constructor found for wrapper factory generation. At least one default or copy constructor must be visible.")
    @GenerateWrapper
    @SuppressWarnings("unused")
    public abstract static class ErrorNode6 extends Node implements InstrumentableNode {

        public WrapperNode createWrapper(ProbeNode probe) {
            return null;
        }

        public boolean isInstrumentable() {
            return false;
        }

        private ErrorNode6() {
        }

        private ErrorNode6(SourceSection notVisible) {
        }

        private ErrorNode6(ErrorNode6 copyNotVisible) {
        }

        public ErrorNode6(int a, int b) {
        }

        public ErrorNode6(String foobar) {
        }
    }

    @ExpectError("Classes annotated with @GenerateWrapper must implement InstrumentableNode.")
    @GenerateWrapper
    public abstract static class ErrorNode7 extends Node {

    }

    @ExpectError("Classes annotated with @GenerateWrapper must extend Node.")
    @GenerateWrapper
    public abstract static class ErrorNode8 implements InstrumentableNode {

        public abstract void execute();

        @Override
        public WrapperNode createWrapper(ProbeNode probeNode) {
            return null;
        }

        public boolean isInstrumentable() {
            return false;
        }
    }

    @ExpectError("Classes annotated with @GenerateWrapper must extend Node.")
    @GenerateWrapper
    public abstract static class ErrorNode9 implements InstrumentableNode {

        public abstract void execute();

        @Override
        public WrapperNode createWrapper(ProbeNode probeNode) {
            return null;
        }

        public boolean isInstrumentable() {
            return false;
        }
    }

}
