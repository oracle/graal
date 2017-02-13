/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.nfi.NFILanguage;
import org.junit.Assert;
import org.junit.BeforeClass;

public class NFITest {

    protected static TruffleObject defaultLibrary;
    protected static TruffleObject testLibrary;

    private static CallTarget lookupAndBind;

    @BeforeClass
    public static void loadLibraries() {
        PolyglotEngine engine = PolyglotEngine.newBuilder().build();
        defaultLibrary = engine.eval(Source.newBuilder("default").name("(load default)").mimeType("application/x-native").build()).as(TruffleObject.class);
        testLibrary = engine.eval(Source.newBuilder("load '" + System.getProperty("native.test.lib") + "'").name("(load test)").mimeType("application/x-native").build()).as(TruffleObject.class);
        lookupAndBind = Truffle.getRuntime().createCallTarget(new LookupAndBindNode());
    }

    private static class LookupAndBindNode extends RootNode {

        @Child Node lookupSymbol = Message.READ.createNode();
        @Child Node bind = Message.createInvoke(1).createNode();

        private LookupAndBindNode() {
            super(NFILanguage.class, null, new FrameDescriptor());
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
                throw new AssertionError(e);
            }
        }
    }

    protected abstract static class TestRootNode extends RootNode {

        protected TestRootNode() {
            super(NFILanguage.class, null, new FrameDescriptor());
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

    private static class SendExecuteNode extends TestRootNode {

        private final TruffleObject receiver;

        @Child Node execute;

        private SendExecuteNode(TruffleObject receiver, int argCount) {
            this.receiver = receiver;
            execute = Message.createExecute(argCount).createNode();
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

    protected Object run(RootNode node, Object... arguments) {
        CallTarget callTarget = Truffle.getRuntime().createCallTarget(node);
        return callTarget.call(arguments);
    }

    protected Object sendExecute(TruffleObject receiver, Object... arguments) {
        SendExecuteNode sendExecute = new SendExecuteNode(receiver, arguments.length);
        return run(sendExecute, arguments);
    }
}
