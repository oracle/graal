/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.test;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.tck.TruffleRunner;
import org.graalvm.polyglot.Context;

public class NFITest {

    @ClassRule public static TruffleRunner.RunWithPolyglotRule runWithPolyglot = new TruffleRunner.RunWithPolyglotRule(Context.newBuilder().allowNativeAccess(true));

    protected static TruffleObject defaultLibrary;
    protected static TruffleObject testLibrary;

    private static CallTarget lookupAndBind;

    private static TruffleObject loadLibrary(String lib) {
        String testBackend = System.getProperty("native.test.backend");
        String sourceString;
        if (testBackend != null) {
            sourceString = String.format("with %s %s", testBackend, lib);
        } else {
            sourceString = lib;
        }

        Source source = Source.newBuilder("nfi", sourceString, "loadLibrary").build();
        CallTarget target = runWithPolyglot.getTruffleTestEnv().parse(source);
        return (TruffleObject) target.call();
    }

    @BeforeClass
    public static void loadLibraries() {
        defaultLibrary = loadLibrary("default");
        testLibrary = loadLibrary("load '" + System.getProperty("native.test.lib") + "'");
        lookupAndBind = Truffle.getRuntime().createCallTarget(new LookupAndBindNode());
    }

    private static final class LookupAndBindNode extends RootNode {

        @Child Node lookupSymbol = Message.READ.createNode();
        @Child Node bind = Message.INVOKE.createNode();

        private LookupAndBindNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            TruffleObject library = (TruffleObject) frame.getArguments()[0];
            String symbolName = (String) frame.getArguments()[1];
            String signature = (String) frame.getArguments()[2];

            try {
                TruffleObject symbol = (TruffleObject) ForeignAccess.sendRead(lookupSymbol, library, symbolName);
                return ForeignAccess.sendInvoke(bind, symbol, "bind", signature);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            }
        }
    }

    protected abstract static class NFITestRootNode extends RootNode {

        protected NFITestRootNode() {
            super(null);
        }

        @TruffleBoundary
        protected static void assertEquals(Object expected, Object actual) {
            Assert.assertEquals(expected, actual);
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            try {
                return executeTest(frame);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            }
        }

        public abstract Object executeTest(VirtualFrame frame) throws InteropException;
    }

    protected static class SendExecuteNode extends NFITestRootNode {

        private final TruffleObject receiver;

        @Child Node execute;

        protected SendExecuteNode(String symbol, String signature) {
            this(lookupAndBind(symbol, signature));
        }

        protected SendExecuteNode(TruffleObject receiver) {
            this.receiver = receiver;
            execute = Message.EXECUTE.createNode();
        }

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            return ForeignAccess.sendExecute(execute, receiver, frame.getArguments());
        }
    }

    protected static TruffleObject lookupAndBind(String name, String signature) {
        return lookupAndBind(testLibrary, name, signature);
    }

    protected static TruffleObject lookupAndBind(TruffleObject library, String name, String signature) {
        return (TruffleObject) lookupAndBind.call(library, name, signature);
    }

    protected static boolean isBoxed(TruffleObject obj) {
        return ForeignAccess.sendIsBoxed(Message.IS_BOXED.createNode(), obj);
    }

    protected static Object unbox(TruffleObject obj) {
        try {
            return ForeignAccess.sendUnbox(Message.UNBOX.createNode(), obj);
        } catch (UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    protected static boolean isNull(TruffleObject foreignObject) {
        return ForeignAccess.sendIsNull(Message.IS_NULL.createNode(), foreignObject);
    }
}
