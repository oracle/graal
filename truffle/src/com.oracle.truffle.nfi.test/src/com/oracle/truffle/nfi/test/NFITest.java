/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
