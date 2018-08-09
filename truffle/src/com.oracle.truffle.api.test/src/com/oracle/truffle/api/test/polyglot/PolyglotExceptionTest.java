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
package com.oracle.truffle.api.test.polyglot;

import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.ForeignAccess.StandardFactory;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class PolyglotExceptionTest {

    @SuppressWarnings("serial")
    private static class TestGuestError extends RuntimeException implements TruffleException {

        TestGuestError() {
            super("MyError");
        }

        public Node getLocation() {
            return null;
        }

    }

    @Test
    public void testExceptionWrapping() {
        Context context = Context.create();
        Context otherContext = Context.create();

        CauseErrorTruffleObject causeError = new CauseErrorTruffleObject();
        causeError.thrownError = new TestGuestError();

        Value throwError = context.asValue(causeError);
        Value throwErrorOtherContext = otherContext.asValue(causeError);

        try {
            throwError.execute();
            Assert.fail();
        } catch (PolyglotException e) {
            Assert.assertEquals(e.getMessage(), "MyError");
            Assert.assertTrue(e.isGuestException());
        }

        Value verifyError = context.asValue(new ProxyExecutable() {
            public Object execute(Value... arguments) {
                try {
                    throwError.execute();
                    Assert.fail();
                } catch (PolyglotException e) {
                    Assert.assertEquals(e.getMessage(), "MyError");
                    Assert.assertTrue(e.isGuestException());
                    throw e;
                }
                return null;
            }
        });
        try {
            verifyError.execute();
            Assert.fail();
        } catch (PolyglotException e) {
            Assert.assertEquals(e.getMessage(), "MyError");
            Assert.assertTrue(e.isGuestException());
        }

        // if the exception was thrown by a different context it will be treated
        // as a host exception.
        Value verifyErrorOtherContext = context.asValue(new ProxyExecutable() {
            public Object execute(Value... arguments) {
                try {
                    throwErrorOtherContext.execute();
                } catch (PolyglotException e) {
                    Assert.assertEquals(e.getMessage(), "MyError");
                    Assert.assertTrue(e.isGuestException());
                    throw e;
                }
                return null;
            }
        });
        try {
            verifyErrorOtherContext.execute();
            Assert.fail();
        } catch (PolyglotException e) {
            // assert that polyglot exception was not unboxed if from other context
            Assert.assertTrue(e.asHostException() instanceof PolyglotException);
            PolyglotException polyglot = (PolyglotException) e.asHostException();
            Assert.assertEquals(polyglot.getMessage(), "MyError");
            Assert.assertTrue(polyglot.isGuestException());
        }

        context.close();
        otherContext.close();
    }

    @Test
    public void testLanguageExceptionUnwrapping() {
        Context context = Context.create();

        Value throwError = context.asValue(new ProxyExecutable() {
            public Object execute(Value... arguments) {
                throw new RuntimeException();
            }
        });

        VerifyErrorTruffleObject checkErrorObj = new VerifyErrorTruffleObject();
        Value checkError = context.asValue(checkErrorObj);

        checkErrorObj.verifyError = (e) -> {
            Assert.assertTrue(e instanceof TruffleException);
            Assert.assertEquals("HostException", e.getClass().getSimpleName());
        };
        Assert.assertTrue(checkError.execute(throwError).asBoolean());

        context.close();
    }

    private static class CauseErrorTruffleObject implements TruffleObject {

        RuntimeException thrownError;

        public ForeignAccess getForeignAccess() {
            return CauseErrorObjectFactory.INSTANCE;
        }

    }

    private static class CauseErrorObjectFactory implements StandardFactory {

        private static final ForeignAccess INSTANCE = ForeignAccess.create(CauseErrorTruffleObject.class, new CauseErrorObjectFactory());

        public CallTarget accessIsExecutable() {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(true));
        }

        public CallTarget accessExecute(int argumentsLength) {
            return Truffle.getRuntime().createCallTarget(new RootNode(null) {

                @Child private Node execute = Message.EXECUTE.createNode();

                @Override
                public Object execute(VirtualFrame frame) {
                    CauseErrorTruffleObject to = (CauseErrorTruffleObject) frame.getArguments()[0];
                    throw to.thrownError;
                }
            });
        }

    }

    private static class VerifyErrorTruffleObject implements TruffleObject {

        Consumer<Throwable> verifyError;

        public ForeignAccess getForeignAccess() {
            return VerifyErrorObjectFactory.INSTANCE;
        }

    }

    private static class VerifyErrorObjectFactory implements StandardFactory {

        private static final ForeignAccess INSTANCE = ForeignAccess.create(VerifyErrorTruffleObject.class, new VerifyErrorObjectFactory());

        public CallTarget accessIsExecutable() {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(true));
        }

        public CallTarget accessExecute(int argumentsLength) {
            return Truffle.getRuntime().createCallTarget(new RootNode(null) {

                @Child private Node execute = Message.EXECUTE.createNode();

                @Override
                public Object execute(VirtualFrame frame) {
                    VerifyErrorTruffleObject to = (VerifyErrorTruffleObject) frame.getArguments()[0];
                    Object arg = frame.getArguments()[1];
                    try {
                        ForeignAccess.sendExecute(execute, (TruffleObject) arg, new Object[0]);
                        Assert.fail();
                    } catch (Throwable e) {
                        to.verifyError.accept(e);
                    }
                    return true;
                }
            });
        }

    }
}
