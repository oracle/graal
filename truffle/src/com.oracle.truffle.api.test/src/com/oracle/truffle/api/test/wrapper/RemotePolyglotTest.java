/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.wrapper;

import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Field;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

import sun.misc.Unsafe;

/**
 * This test shows how the polyglot API can be intercepted such that its implementation can be
 * implemented remotely.
 */
/*
 * This test is pretty hacky, modifying the polyglot impl with unsafe is arguably pretty bad. But we
 * should not install a polyglot wrapper for all the tests and at the same time the impl field
 * should stay a static final.
 */
public class RemotePolyglotTest {

    private static AbstractPolyglotImpl previousPolyglot;

    @BeforeClass
    public static void setupClass() throws Throwable {
        // ensure polyglot initialized
        Engine.create().close();

        previousPolyglot = getPolylgotImpl();
        RemotePolyglotDispatch dispatch = new RemotePolyglotDispatch();
        dispatch.setConstructors(previousPolyglot.getAPIAccess());
        dispatch.setNext(previousPolyglot);
        setPolylgotImpl(dispatch);

        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(new RootNode(languageInstance) {

                    @Override
                    public Object execute(VirtualFrame frame) {
                        return new TestGuestFunction();
                    }
                });
            }
        });
    }

    @AfterClass
    public static void tearDownClass() throws Throwable {
        setPolylgotImpl(previousPolyglot);
        ProxyLanguage.setDelegate(null);
    }

    public Object testFunction(Object v) {
        return v;
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static class TestGuestFunction implements TruffleObject {

        @ExportMessage
        final boolean isExecutable() {
            return true;
        }

        @ExportMessage
        final Object execute(Object[] args) {
            return args[0];
        }

    }

    @Test
    public void test() {
        try (Context context = Context.newBuilder().option("engine.SpawnRemote", "true").allowHostAccess(HostAccess.ALL).build()) {
            // basic test. Needs more
            assertNotNull(context);
            Value directHostValue = context.asValue(this);

            // invoke host directly
            Value hostFunction = directHostValue.getMember("testFunction");
            Value guestfunction = context.eval(ProxyLanguage.ID, "");
            Value guestToGuestValue = guestfunction.execute(guestfunction);
            Value guestToHostValue = guestfunction.execute(hostFunction);
            Value hostToGuestValue = guestfunction.execute(guestfunction);
            Value hostToHostValue = guestfunction.execute(hostFunction);

            assertNotNull(guestToGuestValue);
            assertNotNull(guestToHostValue);
            assertNotNull(hostToGuestValue);
            assertNotNull(hostToHostValue);

            // TODO exceptions!?
        }
    }

    private static final Unsafe UNSAFE;

    private static AbstractPolyglotImpl getPolylgotImpl() throws Throwable {
        Class<?> implHolder = Class.forName(Engine.class.getName() + "$ImplHolder");
        Field f = implHolder.getDeclaredField("IMPL");
        return (AbstractPolyglotImpl) UNSAFE.getObject(UNSAFE.staticFieldBase(f), UNSAFE.staticFieldOffset(f));
    }

    private static void setPolylgotImpl(AbstractPolyglotImpl impl) throws Throwable {
        Class<?> implHolder = Class.forName(Engine.class.getName() + "$ImplHolder");
        Field f = implHolder.getDeclaredField("IMPL");
        UNSAFE.putObject(UNSAFE.staticFieldBase(f), UNSAFE.staticFieldOffset(f), impl);
    }

    static {
        Unsafe unsafe;
        try {
            unsafe = Unsafe.getUnsafe();
        } catch (SecurityException e) {
            try {
                Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafeInstance.setAccessible(true);
                unsafe = (Unsafe) theUnsafeInstance.get(Unsafe.class);
            } catch (Exception e2) {
                throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e2);
            }
        }
        UNSAFE = unsafe;
    }

}
