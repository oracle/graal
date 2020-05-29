/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import java.util.function.Consumer;

import org.graalvm.polyglot.management.ExecutionEvent;
import org.graalvm.polyglot.management.ExecutionListener;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class ExecutionListenerCompilerTest extends PartialEvaluationTest {

    static final SourceSection DUMMY_SECTION = com.oracle.truffle.api.source.Source.newBuilder(ProxyLanguage.ID, "", "").name("").build().createSection(0, 0);

    ProxyLanguage langauge;

    static int counter;

    public static Object return42() {
        return 42;
    }

    public static Object return84() {
        return 84;
    }

    public static Object returnNull() {
        return null;
    }

    public static Object returnNullAndIncrement() {
        counter++;
        return null;
    }

    public static Object return42AndIncrement() {
        counter++;
        return 42;
    }

    public static Object return84AndIncrementThrice() {
        counter++;
        counter++;
        counter++;
        return 84;
    }

    public static Object throwError() {
        throw new IgnoreError();
    }

    public static Object throwErrorAndIncrement() {
        try {
            throw new IgnoreError();
        } catch (Throwable e) {
            counter++;
            throw e;
        }
    }

    private final Consumer<ExecutionEvent> empty = (e) -> {
    };

    private final Consumer<ExecutionEvent> counting = (e) -> {
        counter++;
    };

    @Test
    public void testOnEnterCompilation() {
        testListener(null, "return42", new Return42Node());

        testListener(ExecutionListener.newBuilder().onEnter(empty).expressions(true),
                        "return42", //
                        new Return42Node());

        testListener(ExecutionListener.newBuilder().onEnter(counting).expressions(true),
                        "return42AndIncrement", //
                        new Return42Node());
    }

    @Test
    public void testOnReturnCompilation() {
        testListener(null, "return42", new Return42Node());

        testListener(ExecutionListener.newBuilder().onReturn(empty).expressions(true),
                        "return42",
                        new Return42Node());

        testListener(ExecutionListener.newBuilder().onReturn(counting).expressions(true),
                        "return42AndIncrement", //
                        new Return42Node());
    }

    @Test
    public void testOnErrorCompilation() {
        testListener(null, "throwError", new ThrowErrorNode());

        testListener(ExecutionListener.newBuilder().onReturn(empty).expressions(true),
                        "throwError", //
                        new ThrowErrorNode());

        testListener(ExecutionListener.newBuilder().onReturn(counting).expressions(true),
                        "throwErrorAndIncrement", //
                        new ThrowErrorNode());
    }

    /*
     * Collect all the data but without input events return value or error no additional code should
     * be produced. The polyglot listener nodes should specialize and deopt if it changes.
     */
    @Test
    public void testOnErrorNoException() {
        testListener(null, "returnNull", new ReturnNullNode());

        testListener(ExecutionListener.newBuilder().onEnter(empty).onReturn(empty).expressions(true).collectReturnValue(true).collectInputValues(true), //
                        "returnNull",
                        new ReturnNullNode());

        testListener(ExecutionListener.newBuilder().onEnter(empty).onReturn(counting).expressions(true).collectReturnValue(true).collectInputValues(true), //
                        "returnNullAndIncrement",
                        new ReturnNullNode());

    }

    @Test
    public void testMultipleCounts() {
        testListener(null, "return84", new ExecuteTwoChildrenNode(new Return42Node(), new Return42Node()));

        testListener(ExecutionListener.newBuilder().onEnter(counting).expressions(true),
                        "return84AndIncrementThrice",
                        new ExecuteTwoChildrenNode(new Return42Node(), new Return42Node()));
    }

    private void testListener(ExecutionListener.Builder builder, String expectedMethodName, BaseNode baseNode) {
        ExecutionListener listener = null;
        if (builder != null) {
            listener = builder.attach(getContext().getEngine());
        }
        assertPartialEvalEquals(expectedMethodName, createRoot(baseNode));
        if (listener != null) {
            listener.close();
        }
    }

    @Before
    public void setup() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return super.parse(request);
            }
        });
        setupContext();
        getContext().initialize(ProxyLanguage.ID);
        langauge = ProxyLanguage.getCurrentLanguage();
        counter = 0;
    }

    private RootNode createRoot(BaseNode node) {
        return new RootNode(langauge) {
            @Child BaseNode child = node;

            @Override
            public SourceSection getSourceSection() {
                return DUMMY_SECTION;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                return child.execute(frame);
            }
        };
    }

    static class ExecuteTwoChildrenNode extends BaseNode {

        @Child BaseNode child0;
        @Child BaseNode child1;

        ExecuteTwoChildrenNode(BaseNode child0, BaseNode child1) {
            this.child0 = child0;
            this.child1 = child1;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return (int) child0.execute(frame) + (int) child1.execute(frame);
        }

    }

    static class Return42Node extends BaseNode {

        @Override
        public Object execute(VirtualFrame frame) {
            return 42;
        }

    }

    static class ReturnNullNode extends BaseNode {

        @Override
        public Object execute(VirtualFrame frame) {
            return null;
        }

    }

    static class ThrowErrorNode extends BaseNode {

        @Override
        public Object execute(VirtualFrame frame) {
            throw new IgnoreError();
        }

    }

    @GenerateWrapper
    abstract static class BaseNode extends Node implements InstrumentableNode {

        public abstract Object execute(VirtualFrame frame);

        @Override
        public SourceSection getSourceSection() {
            return DUMMY_SECTION;
        }

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public WrapperNode createWrapper(ProbeNode probe) {
            return new BaseNodeWrapper(this, probe);
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return tag == ExpressionTag.class || tag == StatementTag.class || tag == RootTag.class;
        }

    }

}
