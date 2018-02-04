/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.instrumentation.test;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.SourceSection;

public class GenerateWrapperTest {

    @GenerateWrapper
    public abstract static class GeneratedTestNode1 extends Node implements InstrumentableNode {

        @Override
        public WrapperNode createWrapper(ProbeNode probeNode) {
            return new GeneratedTestNode1Wrapper(this, probeNode);
        }

        public boolean isInstrumentable() {
            return false;
        }

        public abstract void execute1(VirtualFrame frame);

        public abstract Object execute2(VirtualFrame frame);

        public abstract int execute3(VirtualFrame frame);

        public abstract String execute4(VirtualFrame frame);

        public abstract double execute5(VirtualFrame frame);

        public abstract long execute6(VirtualFrame frame);

        public abstract float execute7(VirtualFrame frame);

        public abstract short execute8(VirtualFrame frame);

        public abstract byte execute9(VirtualFrame frame);

        public abstract Object execute10(VirtualFrame frame1);

        public abstract Object execute11(VirtualFrame frame, int a, VirtualFrame frame1);

        public abstract Object execute12(VirtualFrame frame1, int b);

        public abstract Object execute13(VirtualFrame frame, int a, VirtualFrame frame1, int b);

        public abstract byte execute15(VirtualFrame frame) throws UnexpectedResultException;

        public abstract byte execute16(VirtualFrame frame) throws IOException;

        public abstract Object execute17(VirtualFrame frame, int a, VirtualFrame frame1, int b) throws UnexpectedResultException;

        @SuppressWarnings("unused")
        public Object execute18(VirtualFrame frame, int a, VirtualFrame frame1, int b) throws UnexpectedResultException {
            return null;
        }
    }

    // test constructor with source section
    @GenerateWrapper
    public abstract static class GeneratedTestNode2 extends Node implements InstrumentableNode {

        private final SourceSection sourceSection;

        public GeneratedTestNode2(SourceSection sourceSection) {
            this.sourceSection = sourceSection;
        }

        @Override
        public WrapperNode createWrapper(ProbeNode probeNode) {
            return new GeneratedTestNode2Wrapper(sourceSection, this, probeNode);
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
    public abstract static class GeneratedTestNode3 extends Node implements InstrumentableNode {

        public GeneratedTestNode3(@SuppressWarnings("unused") GeneratedTestNode3 sourceSection) {
        }

        public abstract void execute1(VirtualFrame frame);

        @Override
        public WrapperNode createWrapper(ProbeNode probeNode) {
            return new GeneratedTestNode3Wrapper(null, this, probeNode);
        }

        public boolean isInstrumentable() {
            return false;
        }
    }

    @Test
    public void testTestNode1() {
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
    public abstract static class DelegateAbstractMethod extends Node implements InstrumentableNode {

        public void execute(@SuppressWarnings("unused") VirtualFrame frame) {
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

    @Test
    public void testUnexpectedResult() {
        org.graalvm.polyglot.Context context = org.graalvm.polyglot.Context.create(TestUnexpectedResultLanguage.ID);
        TestExecInterceptor execInterceptor = context.getEngine().getInstruments().get("testExecInterceptor").lookup(TestExecInterceptor.class);
        // Provide 42 long (42L) from boolean (Z) specialization:
        Value ret = context.eval(TestUnexpectedResultLanguage.ID, "42L\nZ");
        assertEquals(42L, ret.asLong());
        assertEquals("[Enter, Return 42]", execInterceptor.calls.toString());
    }

    public static class TestUnexpectedResultRootNode extends RootNode {

        @Node.Child TestGenUnexpectedResultNode testNode;
        private Class<?> type;

        TestUnexpectedResultRootNode(TruffleLanguage<?> language) {
            super(language);
        }

        void setTest(TestGenUnexpectedResultNode node, Class<?> type) {
            this.testNode = node;
            this.type = type;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                if (type == Long.TYPE) {
                    return testNode.executeLong(frame);
                } else if (type == Boolean.TYPE) {
                    return testNode.executeBoolean(frame);
                } else {
                    return testNode.executeGeneric(frame);
                }
            } catch (UnexpectedResultException urex) {
                return urex.getResult();
            }
        }
    }

    @GenerateWrapper
    public static class TestGenUnexpectedResultNode extends Node implements InstrumentableNode {

        private Object returnValue;
        private final SourceSection sourceSection;

        public TestGenUnexpectedResultNode(SourceSection sourceSection) {
            this.sourceSection = sourceSection;
        }

        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public WrapperNode createWrapper(ProbeNode probeNode) {
            return new TestGenUnexpectedResultNodeWrapper(sourceSection, this, probeNode);
        }

        public void setReturnValue(Object returnValue) {
            this.returnValue = returnValue;
        }

        @SuppressWarnings("unused")
        public Object executeGeneric(VirtualFrame frame) {
            return returnValue;
        }

        @SuppressWarnings("unused")
        public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
            if (returnValue instanceof Long) {
                return (Long) returnValue;
            } else {
                throw new UnexpectedResultException(returnValue);
            }
        }

        @SuppressWarnings("unused")
        public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
            if (returnValue instanceof Boolean) {
                return (Boolean) returnValue;
            } else {
                throw new UnexpectedResultException(returnValue);
            }
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }

    }

    @TruffleLanguage.Registration(id = TestUnexpectedResultLanguage.ID, name = "", version = "", mimeType = "testUnexpectedResultLang")
    public static class TestUnexpectedResultLanguage extends InstrumentationTestLanguage {

        static final String ID = "testUnexpectedResult-lang";

        @Override
        protected CallTarget parse(ParsingRequest request) {
            String code = request.getSource().getCharacters().toString();
            int rowEnd = code.indexOf('\n');
            String retVal = code.substring(0, rowEnd);
            TestGenUnexpectedResultNode node = new TestGenUnexpectedResultNode(request.getSource().createSection(1));
            assert retVal.endsWith("L");
            node.setReturnValue(Long.parseLong(retVal.substring(0, retVal.length() - 1)));
            TestUnexpectedResultRootNode root = new TestUnexpectedResultRootNode(this);
            Class<?> type;
            switch (code.charAt(code.length() - 1)) {
                case 'Z':
                    type = Boolean.TYPE;
                    break;
                case 'J':
                    type = Long.TYPE;
                    break;
                default:
                    throw new IllegalArgumentException(code);
            }
            root.setTest(node, type);
            RootCallTarget target = Truffle.getRuntime().createCallTarget(root);
            return target;
        }
    }

    @TruffleInstrument.Registration(id = "testExecInterceptor", services = TestExecInterceptor.class)
    public static class TestExecInterceptor extends TruffleInstrument implements ExecutionEventListener {

        List<String> calls = new ArrayList<>();

        @Override
        protected void onCreate(Env env) {
            env.registerService(this);
            env.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, this);
        }

        @Override
        public void onEnter(EventContext context, VirtualFrame frame) {
            calls.add("Enter");
        }

        @Override
        public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
            calls.add("Return " + result);
        }

        @Override
        public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
            calls.add("Exception " + exception);
        }

    }
}
