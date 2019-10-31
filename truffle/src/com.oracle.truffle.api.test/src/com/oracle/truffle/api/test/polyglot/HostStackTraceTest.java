/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Supplier;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public class HostStackTraceTest extends AbstractPolyglotTest {

    @ExportLibrary(InteropLibrary.class)
    class HostStackTraceExecutable implements TruffleObject {

        final CallTarget callTarget;

        HostStackTraceExecutable(String name, SourceSection rootSection, SourceSection callSection) {
            this.callTarget = Truffle.getRuntime().createCallTarget(new ExecuteRootNode(name, rootSection, callSection));
        }

        @ExportMessage
        final boolean accepts(@Cached("this") HostStackTraceExecutable cachedInstance) {
            // to make it easier to overflow the cache in tests we use an identity cache here.
            return this == cachedInstance;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        final boolean isExecutable() {
            return true;
        }

        // unached version does callTarget.call
        @ExportMessage
        final Object execute(Object[] arguments, @Cached IndirectCallNode callNode) {
            return callNode.call(callTarget, arguments);
        }

    }

    final class ExecuteRootNode extends RootNode {

        private final String name;
        private final SourceSection rootSection;
        @Child private CallNode callNode;

        ExecuteRootNode(String name, SourceSection rootSection, SourceSection callSection) {
            super(language);
            this.name = name;
            this.rootSection = rootSection;
            this.callNode = new CallNode(callSection);
        }

        @Override
        public SourceSection getSourceSection() {
            return rootSection;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isInternal() {
            return false;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return callNode.execute(frame);
        }

    }

    static class CallNode extends Node {

        @Child private InteropLibrary interop = InteropLibrary.getFactory().createDispatched(1);

        private final SourceSection sourceSection;

        CallNode(SourceSection sourceSection) {
            this.sourceSection = sourceSection;
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }

        Object execute(VirtualFrame frame) {
            try {
                Object receiver = frame.getArguments()[0];
                Object[] prunedArgs = Arrays.copyOfRange(frame.getArguments(), 1, frame.getArguments().length);
                return interop.execute(receiver, prunedArgs);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw new AssertionError(e);
            }
        }

    }

    @Before
    public void setup() {
        setupEnv();
    }

    @Test
    public void testExecute() {
        Value v = context.asValue(new Supplier<Object>() {
            public Object get() {
                throw new RuntimeException();
            }
        });
        try {
            v.execute();
            Assert.fail();
        } catch (PolyglotException e) {
            assertTrue(e.asHostException() instanceof RuntimeException);
            Iterator<StackFrame> frames = e.getPolyglotStackTrace().iterator();
            StackFrame frame;

            frame = frames.next();
            assertTrue(frame.isHostFrame());
            assertEquals("get", frame.toHostFrame().getMethodName());

            frame = frames.next();
            assertTrue(frame.isHostFrame());
            assertEquals("execute", frame.toHostFrame().getMethodName());
        }
    }

    public void v0() {
        throw new RuntimeException();
    }

    @Test
    public void testExecuteGuestOneFrame() {
        Source source = Source.newBuilder(ProxyLanguage.ID, "    ", "").build();

        Value v0 = context.asValue(this).getMember("v0");
        Value v1 = context.asValue(new HostStackTraceExecutable("v1", source.createSection(0, 1), source.createSection(1, 1)));
        try {
            v1.execute(v0);
            Assert.fail();
        } catch (PolyglotException e) {
            assertTrue(e.asHostException() instanceof RuntimeException);
            Iterator<StackFrame> frames = e.getPolyglotStackTrace().iterator();
            StackFrame frame;

            frame = frames.next();
            assertTrue(frame.isHostFrame());
            assertEquals("v0", frame.toHostFrame().getMethodName());

            frame = frames.next();
            assertTrue(frame.isGuestFrame());
            assertEquals("v1", frame.getRootName());
            assertEquals(1, frame.getSourceLocation().getCharIndex());

            frame = frames.next();
            assertTrue(frame.isHostFrame());
            assertEquals("execute", frame.toHostFrame().getMethodName());
        }
    }

    @Test
    public void testExecuteGuestTwoFrames() {
        Source source = Source.newBuilder(ProxyLanguage.ID, "    ", "").build();

        Value v0 = context.asValue(this).getMember("v0");
        Value v1 = context.asValue(new HostStackTraceExecutable("v1", source.createSection(0, 1), source.createSection(1, 1)));
        Value v2 = context.asValue(new HostStackTraceExecutable("v2", source.createSection(2, 1), source.createSection(3, 1)));
        try {
            v2.execute(v1, v0);
            Assert.fail();
        } catch (PolyglotException e) {
            assertTrue(e.asHostException() instanceof RuntimeException);
            Iterator<StackFrame> frames = e.getPolyglotStackTrace().iterator();
            StackFrame frame;

            frame = frames.next();
            assertTrue(frame.isHostFrame());
            assertEquals("v0", frame.toHostFrame().getMethodName());

            frame = frames.next();
            assertTrue(frame.isGuestFrame());
            assertEquals("v1", frame.getRootName());
            assertEquals(1, frame.getSourceLocation().getCharIndex());

            frame = frames.next();
            assertTrue(frame.isGuestFrame());
            assertEquals("v2", frame.getRootName());
            assertEquals(3, frame.getSourceLocation().getCharIndex());

            frame = frames.next();
            assertTrue(frame.isHostFrame());
            assertEquals("execute", frame.toHostFrame().getMethodName());
        }
    }

    @Test
    public void testExecuteUncached() {
        Source source = Source.newBuilder(ProxyLanguage.ID, "    ", "").build();

        Value v0 = context.asValue(this).getMember("v0");
        Value v1 = context.asValue(new HostStackTraceExecutable("v1", source.createSection(0, 1), source.createSection(1, 1)));
        Value v2 = context.asValue(new HostStackTraceExecutable("v2", source.createSection(2, 1), source.createSection(3, 1)));

        // TODO support host stack trace
        try {
            v2.execute(v0);
        } catch (PolyglotException e) {
        }

        // make the call site uncached
        try {
            v2.execute(v1, v0);
        } catch (PolyglotException e) {
        }

        // now uncached
        try {
            v2.execute(v0);
        } catch (PolyglotException e) {
        }

    }

}
