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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instrumentation;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

public class ValueInterceptionTest {

    private static final SourceSectionFilter EXPRESSION_FILTER = SourceSectionFilter.newBuilder().tagIs(ExpressionTag.class).build();
    private static final SourceSectionFilter STATEMENT_FILTER = SourceSectionFilter.newBuilder().tagIs(StatementTag.class).build();

    private Context context;
    private Instrumenter instrumenter;

    @Before
    public void setup() {
        context = Context.create(TagTest.TagTestLanguage.ID);
        context.initialize(TagTest.TagTestLanguage.ID);
        context.enter();
        instrumenter = context.getEngine().getInstruments().get(TagTest.TagTestInstrumentation.ID).lookup(Instrumenter.class);
    }

    @After
    public void tearDown() {
        try {
            if (context != null) {
                context.close();
            }
        } finally {
            context = null;
            instrumenter = null;
        }
    }

    @Test
    public void testOutgoingReturnListenerAndFactory() {
        InternalValue value = new InternalValue(42);
        ValueInterceptionTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(value);
            b.endTag(ExpressionTag.class);
            b.endReturn();
            b.endRoot();
        });

        AtomicReference<Object> listenerValue = new AtomicReference<>();
        AtomicReference<Object> factoryValue = new AtomicReference<>();
        EventBinding<?> listener = instrumenter.attachExecutionEventListener(EXPRESSION_FILTER, new RecordingListener(listenerValue, null));
        EventBinding<?> factory = instrumenter.attachExecutionEventFactory(EXPRESSION_FILTER, eventContext -> new RecordingEventNode(factoryValue, null));
        try {
            assertSame(value, root.getCallTarget().call());
            assertEquals(new ExportedValue(42), listenerValue.get());
            assertEquals(new ExportedValue(42), factoryValue.get());
            assertEquals(1, root.outgoingConversions);
        } finally {
            listener.dispose();
            factory.dispose();
        }
    }

    @Test
    public void testOutgoingPrimitiveListenerAndFactory() {
        ValueInterceptionTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(42);
            b.endTag(ExpressionTag.class);
            b.endReturn();
            b.endRoot();
        });

        AtomicReference<Object> listenerValue = new AtomicReference<>();
        AtomicReference<Object> factoryValue = new AtomicReference<>();
        EventBinding<?> listener = instrumenter.attachExecutionEventListener(EXPRESSION_FILTER, new RecordingListener(listenerValue, null));
        EventBinding<?> factory = instrumenter.attachExecutionEventFactory(EXPRESSION_FILTER, eventContext -> new RecordingEventNode(factoryValue, null));
        try {
            assertEquals(42, root.getCallTarget().call());
            assertEquals(42L, listenerValue.get());
            assertEquals(42L, factoryValue.get());
            assertEquals(1, root.outgoingConversions);
        } finally {
            listener.dispose();
            factory.dispose();
        }
    }

    @Test
    public void testOutgoingYieldListenerAndFactory() {
        InternalValue value = new InternalValue(42);
        ValueInterceptionTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginTag(ExpressionTag.class);
            b.beginYield();
            b.emitLoadConstant(value);
            b.endYield();
            b.endTag(ExpressionTag.class);
            b.endRoot();
        });

        AtomicReference<Object> listenerValue = new AtomicReference<>();
        AtomicReference<Object> factoryValue = new AtomicReference<>();
        EventBinding<?> listener = instrumenter.attachExecutionEventListener(EXPRESSION_FILTER, new RecordingListener(null, listenerValue));
        EventBinding<?> factory = instrumenter.attachExecutionEventFactory(EXPRESSION_FILTER, eventContext -> new RecordingEventNode(null, factoryValue));
        try {
            ContinuationResult continuation = (ContinuationResult) root.getCallTarget().call();
            assertSame(value, continuation.getResult());
            assertEquals(new ExportedValue(42), listenerValue.get());
            assertEquals(new ExportedValue(42), factoryValue.get());
            assertEquals(1, root.outgoingConversions);
        } finally {
            listener.dispose();
            factory.dispose();
        }
    }

    @Test
    public void testNoReturnValueIsNotConverted() {
        ValueInterceptionTestRootNode leaveRoot = parse(b -> {
            b.beginRoot();
            b.beginTag(StatementTag.class);
            b.emitNop();
            b.endTag(StatementTag.class);
            b.endRoot();
        });
        AtomicReference<Object> returnValue = new AtomicReference<>(this);
        EventBinding<?> leaveBinding = instrumenter.attachExecutionEventFactory(STATEMENT_FILTER, eventContext -> new RecordingEventNode(returnValue, null));
        try {
            leaveRoot.getCallTarget().call();
            assertNull(returnValue.get());
            assertEquals(0, leaveRoot.outgoingConversions);
        } finally {
            leaveBinding.dispose();
        }
    }

    @Test
    public void testInputValueConvertedExactlyOnce() {
        InternalValue value = new InternalValue(42);
        ValueInterceptionTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginTag(StatementTag.class);
            b.beginConsume();
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(value);
            b.endTag(ExpressionTag.class);
            b.endConsume();
            b.endTag(StatementTag.class);
            b.endRoot();
        });

        AtomicReference<Object> listenerValue = new AtomicReference<>();
        AtomicReference<Object> factoryValue = new AtomicReference<>();
        AtomicReference<Object> inputValue = new AtomicReference<>();
        EventBinding<?> listener = instrumenter.attachExecutionEventListener(EXPRESSION_FILTER, new RecordingListener(listenerValue, null));
        EventBinding<?> factory = instrumenter.attachExecutionEventFactory(EXPRESSION_FILTER, eventContext -> new RecordingEventNode(factoryValue, null));
        EventBinding<?> inputFactory = instrumenter.attachExecutionEventFactory(STATEMENT_FILTER, EXPRESSION_FILTER, eventContext -> new ExecutionEventNode() {
            @Override
            protected void onInputValue(VirtualFrame frame, EventContext inputContext, int inputIndex, Object input) {
                assertEquals(0, inputIndex);
                inputValue.set(input);
            }
        });
        try {
            root.getCallTarget().call();
            ExportedValue expected = new ExportedValue(42);
            assertEquals(expected, listenerValue.get());
            assertEquals(expected, factoryValue.get());
            assertEquals(expected, inputValue.get());
            assertSame(value, root.consumedValue);
            assertEquals(1, root.outgoingConversions);
        } finally {
            listener.dispose();
            factory.dispose();
            inputFactory.dispose();
        }
    }

    @Test
    public void testIncomingValueFromEnterEventNode() {
        ValueInterceptionTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(0);
            b.endTag(ExpressionTag.class);
            b.endReturn();
            b.endRoot();
        });
        EventBinding<?> binding = instrumenter.attachExecutionEventFactory(EXPRESSION_FILTER, eventContext -> new ExecutionEventNode() {
            @Override
            protected void onEnter(VirtualFrame frame) {
                throw eventContext.createUnwind(null);
            }

            @Override
            protected Object onUnwind(VirtualFrame frame, Object info) {
                return (short) 42;
            }
        });
        assertIncomingValueAndDisposeBinding(root, binding);
    }

    @Test
    public void testIncomingValueFromEnterListener() {
        ValueInterceptionTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(0);
            b.endTag(ExpressionTag.class);
            b.endReturn();
            b.endRoot();
        });
        EventBinding<?> binding = instrumenter.attachExecutionEventListener(EXPRESSION_FILTER, new ExecutionEventListener() {
            @Override
            public void onEnter(EventContext eventContext, VirtualFrame frame) {
                throw eventContext.createUnwind(null);
            }

            @Override
            public void onReturnValue(EventContext eventContext, VirtualFrame frame, Object result) {
            }

            @Override
            public void onReturnExceptional(EventContext eventContext, VirtualFrame frame, Throwable exception) {
            }

            @Override
            public Object onUnwind(EventContext eventContext, VirtualFrame frame, Object info) {
                return (short) 42;
            }
        });
        assertIncomingValueAndDisposeBinding(root, binding);
    }

    @Test
    public void testIncomingValueFromInputEventNode() {
        ValueInterceptionTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginTag(StatementTag.class);
            b.beginIdentity();
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(0);
            b.endTag(ExpressionTag.class);
            b.endIdentity();
            b.endTag(StatementTag.class);
            b.endReturn();
            b.endRoot();
        });
        EventBinding<?> binding = instrumenter.attachExecutionEventFactory(STATEMENT_FILTER, EXPRESSION_FILTER, eventContext -> new ExecutionEventNode() {
            @Override
            protected void onInputValue(VirtualFrame frame, EventContext inputContext, int inputIndex, Object inputValue) {
                throw eventContext.createUnwind(null);
            }

            @Override
            protected Object onUnwind(VirtualFrame frame, Object info) {
                return (short) 42;
            }
        });
        assertIncomingValueAndDisposeBinding(root, binding);
    }

    @Test
    public void testIncomingValueFromReturnEventNode() {
        ValueInterceptionTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(0);
            b.endTag(ExpressionTag.class);
            b.endReturn();
            b.endRoot();
        });
        EventBinding<?> binding = instrumenter.attachExecutionEventFactory(EXPRESSION_FILTER, eventContext -> new ExecutionEventNode() {
            @Override
            protected void onReturnValue(VirtualFrame frame, Object result) {
                throw eventContext.createUnwind(null);
            }

            @Override
            protected Object onUnwind(VirtualFrame frame, Object info) {
                return (short) 42;
            }
        });
        assertIncomingValueAndDisposeBinding(root, binding);
    }

    @Test
    public void testIncomingValueFromReturnListener() {
        ValueInterceptionTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(0);
            b.endTag(ExpressionTag.class);
            b.endReturn();
            b.endRoot();
        });
        EventBinding<?> binding = instrumenter.attachExecutionEventListener(EXPRESSION_FILTER, new ExecutionEventListener() {
            @Override
            public void onEnter(EventContext eventContext, VirtualFrame frame) {
            }

            @Override
            public void onReturnValue(EventContext eventContext, VirtualFrame frame, Object result) {
                throw eventContext.createUnwind(null);
            }

            @Override
            public void onReturnExceptional(EventContext eventContext, VirtualFrame frame, Throwable exception) {
            }

            @Override
            public Object onUnwind(EventContext eventContext, VirtualFrame frame, Object info) {
                return (short) 42;
            }
        });
        assertIncomingValueAndDisposeBinding(root, binding);
    }

    @Test
    public void testIncomingValueFromExceptionalEventNode() {
        ValueInterceptionTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.emitThrowGuestException();
            b.endTag(ExpressionTag.class);
            b.endReturn();
            b.endRoot();
        });
        EventBinding<?> binding = instrumenter.attachExecutionEventFactory(EXPRESSION_FILTER, eventContext -> new ExecutionEventNode() {
            @Override
            protected void onReturnExceptional(VirtualFrame frame, Throwable exception) {
                throw eventContext.createUnwind(null);
            }

            @Override
            protected Object onUnwind(VirtualFrame frame, Object info) {
                return (short) 42;
            }
        });
        assertIncomingValueAndDisposeBinding(root, binding);
    }

    @Test
    public void testIncomingValueFromExceptionalListener() {
        ValueInterceptionTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.emitThrowGuestException();
            b.endTag(ExpressionTag.class);
            b.endReturn();
            b.endRoot();
        });
        EventBinding<?> binding = instrumenter.attachExecutionEventListener(EXPRESSION_FILTER, new ExecutionEventListener() {
            @Override
            public void onEnter(EventContext eventContext, VirtualFrame frame) {
            }

            @Override
            public void onReturnValue(EventContext eventContext, VirtualFrame frame, Object result) {
            }

            @Override
            public void onReturnExceptional(EventContext eventContext, VirtualFrame frame, Throwable exception) {
                throw eventContext.createUnwind(null);
            }

            @Override
            public Object onUnwind(EventContext eventContext, VirtualFrame frame, Object info) {
                return (short) 42;
            }
        });
        assertIncomingValueAndDisposeBinding(root, binding);
    }

    private static void assertIncomingValueAndDisposeBinding(ValueInterceptionTestRootNode root, EventBinding<?> binding) {
        try {
            assertEquals(42, root.getCallTarget().call());
            assertEquals(1, root.incomingConversions);
        } finally {
            binding.dispose();
        }
    }

    @Test
    public void testIncomingControlResultsAreNotConverted() {
        AtomicBoolean unwind = new AtomicBoolean();
        ValueInterceptionTestRootNode reenterRoot = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(7);
            b.endTag(ExpressionTag.class);
            b.endReturn();
            b.endRoot();
        });
        EventBinding<?> reenterBinding = instrumenter.attachExecutionEventFactory(EXPRESSION_FILTER, eventContext -> new ExecutionEventNode() {
            @Override
            protected void onEnter(VirtualFrame frame) {
                if (unwind.compareAndSet(false, true)) {
                    throw eventContext.createUnwind(null);
                }
            }

            @Override
            protected Object onUnwind(VirtualFrame frame, Object info) {
                return ProbeNode.UNWIND_ACTION_REENTER;
            }
        });
        try {
            assertEquals(7, reenterRoot.getCallTarget().call());
            assertEquals(0, reenterRoot.incomingConversions);
        } finally {
            reenterBinding.dispose();
        }

        ValueInterceptionTestRootNode continueRoot = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(7);
            b.endTag(ExpressionTag.class);
            b.endReturn();
            b.endRoot();
        });
        EventBinding<?> continueBinding = instrumenter.attachExecutionEventFactory(EXPRESSION_FILTER, eventContext -> new ExecutionEventNode() {
            @Override
            protected void onEnter(VirtualFrame frame) {
                throw eventContext.createUnwind(null);
            }

            @Override
            protected Object onUnwind(VirtualFrame frame, Object info) {
                return null;
            }
        });
        try {
            try {
                continueRoot.getCallTarget().call();
                fail("Expected the unwind to continue.");
            } catch (ThreadDeath expected) {
            }
            assertEquals(0, continueRoot.incomingConversions);
        } finally {
            continueBinding.dispose();
        }

        ValueInterceptionTestRootNode voidRoot = parse(b -> {
            b.beginRoot();
            b.beginTag(StatementTag.class);
            b.emitNop();
            b.endTag(StatementTag.class);
            b.endRoot();
        });
        EventBinding<?> voidBinding = instrumenter.attachExecutionEventFactory(STATEMENT_FILTER, eventContext -> new ExecutionEventNode() {
            @Override
            protected void onEnter(VirtualFrame frame) {
                throw eventContext.createUnwind(null);
            }

            @Override
            protected Object onUnwind(VirtualFrame frame, Object info) {
                return (short) 42;
            }
        });
        try {
            assertNull(voidRoot.getCallTarget().call());
            assertEquals(0, voidRoot.incomingConversions);
        } finally {
            voidBinding.dispose();
        }
    }

    @Test
    public void testCustomInstrumentationDoesNotInterceptValues() {
        InternalValue value = new InternalValue(42);
        BytecodeConfig config = ValueInterceptionTestRootNodeGen.BYTECODE.newConfigBuilder().addInstrumentation(ValueInterceptionTestRootNode.CustomInstrumentation.class).build();
        ValueInterceptionTestRootNode root = parse(config, b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginCustomInstrumentation();
            b.emitLoadConstant(value);
            b.endCustomInstrumentation();
            b.endReturn();
            b.endRoot();
        });

        assertSame(value, root.getCallTarget().call());
        assertEquals(0, root.incomingConversions);
        assertEquals(0, root.outgoingConversions);
    }

    private static ValueInterceptionTestRootNode parse(BytecodeParser<ValueInterceptionTestRootNodeGen.Builder> parser) {
        return parse(BytecodeConfig.DEFAULT, parser);
    }

    private static ValueInterceptionTestRootNode parse(BytecodeConfig config, BytecodeParser<ValueInterceptionTestRootNodeGen.Builder> parser) {
        BytecodeRootNodes<ValueInterceptionTestRootNode> nodes = ValueInterceptionTestRootNodeGen.create(TagTest.TagTestLanguage.REF.get(null), config, parser);
        return nodes.getNode(0);
    }

    private static final class RecordingListener implements ExecutionEventListener {

        private final AtomicReference<Object> returnValue;
        private final AtomicReference<Object> yieldValue;

        RecordingListener(AtomicReference<Object> returnValue, AtomicReference<Object> yieldValue) {
            this.returnValue = returnValue;
            this.yieldValue = yieldValue;
        }

        @Override
        public void onEnter(EventContext eventContext, VirtualFrame frame) {
        }

        @Override
        public void onReturnValue(EventContext eventContext, VirtualFrame frame, Object result) {
            if (returnValue != null) {
                returnValue.set(result);
            }
        }

        @Override
        public void onReturnExceptional(EventContext eventContext, VirtualFrame frame, Throwable exception) {
        }

        @Override
        public void onYield(EventContext eventContext, VirtualFrame frame, Object value) {
            if (yieldValue != null) {
                yieldValue.set(value);
            }
        }
    }

    private static final class RecordingEventNode extends ExecutionEventNode {

        private final AtomicReference<Object> returnValue;
        private final AtomicReference<Object> yieldValue;

        RecordingEventNode(AtomicReference<Object> returnValue, AtomicReference<Object> yieldValue) {
            this.returnValue = returnValue;
            this.yieldValue = yieldValue;
        }

        @Override
        protected void onReturnValue(VirtualFrame frame, Object result) {
            if (returnValue != null) {
                returnValue.set(result);
            }
        }

        @Override
        protected void onYield(VirtualFrame frame, Object value) {
            if (yieldValue != null) {
                yieldValue.set(value);
            }
        }
    }

    private record InternalValue(int value) {
    }

    private record ExportedValue(int value) implements TruffleObject {
    }

    @SuppressWarnings("serial")
    private static final class GuestException extends AbstractTruffleException {
        GuestException(Node location) {
            super(location);
        }
    }

    @GenerateBytecode(languageClass = TagTest.TagTestLanguage.class, enableQuickening = true, enableUncachedInterpreter = false, enableTagInstrumentation = true, enableYield = true, boxingEliminationTypes = int.class)
    abstract static class ValueInterceptionTestRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        int incomingConversions;
        int outgoingConversions;
        Object consumedValue;

        protected ValueInterceptionTestRootNode(TagTest.TagTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Override
        public Object interceptIncomingValue(Object value) {
            incomingConversions++;
            if (value instanceof Short shortValue) {
                return shortValue.intValue();
            }
            return value;
        }

        @Override
        public Object interceptOutgoingValue(Object value) {
            outgoingConversions++;
            if (value instanceof ExportedValue) {
                throw new AssertionError("Outgoing value converted twice.");
            } else if (value instanceof InternalValue internalValue) {
                return new ExportedValue(internalValue.value());
            } else if (value instanceof Integer intValue) {
                return intValue.longValue();
            }
            return value;
        }

        @Operation
        static final class Consume {
            @Specialization
            static void doDefault(Object value, @Bind ValueInterceptionTestRootNode root) {
                root.consumedValue = value;
            }
        }

        @Operation
        static final class Identity {
            @Specialization
            static Object doDefault(Object value) {
                return value;
            }
        }

        @Operation
        static final class Nop {
            @Specialization
            static void doDefault() {
            }
        }

        @Operation
        static final class ThrowGuestException {
            @Specialization
            static Object doDefault(@Bind Node node) {
                throw new GuestException(node);
            }
        }

        @Instrumentation
        static final class CustomInstrumentation {
            @Specialization
            static Object doDefault(Object value) {
                return value;
            }
        }
    }
}
