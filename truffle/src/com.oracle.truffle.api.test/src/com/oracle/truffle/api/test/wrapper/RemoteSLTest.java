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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.test.ReflectionUtils;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

/**
 * This test shows how the polyglot API can be intercepted such that its implementation can be
 * implemented remotely.
 */
/*
 * This test is pretty hacky, modifying the polyglot impl with unsafe is arguably pretty bad. But we
 * should not install a polyglot wrapper for all the tests and at the same time the impl field
 * should stay a static final.
 */
public class RemoteSLTest {

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    private AbstractPolyglotImpl previousPolyglot;

    @Before
    public void setup() throws Throwable {
        // ensure polyglot initialized
        Engine.create().close();

        previousPolyglot = getPolyglotImpl();
        HostPolyglotDispatch dispatch = new HostPolyglotDispatch();
        dispatch.setConstructors(previousPolyglot.getAPIAccess());
        dispatch.setNext(previousPolyglot);
        setPolyglotImpl(dispatch);
    }

    @After
    public void tearDown() throws Throwable {
        setPolyglotImpl(previousPolyglot);
    }

    public Object testFunction(Object v) {
        return v;
    }

    public Object triggerHostError() {
        throw new RuntimeException("test message");
    }

    @Test
    public void test() {
        try (Context context = Context.newBuilder().logHandler(System.err).option("engine.SpawnRemote", "true").allowHostAccess(HostAccess.ALL).build()) {
            Value guestFunction = context.eval("sl", "" + //
                            "function error() {eval(\"sl\", \"asdf(\");}\n" + //
                            "function identity(v) {return v;}\n" + //
                            "function call(v) {return v();}\n" + //
                            "function main() {\n" + //
                            "  return identity;\n" + //
                            "}");

            Value thisValue = context.asValue(this);
            Value hostFunction = thisValue.getMember("testFunction");

            assertEquals(guestFunction, guestFunction.execute(guestFunction));
            assertEquals(hostFunction, guestFunction.execute(hostFunction));
            assertEquals(guestFunction, hostFunction.execute(guestFunction));
            assertEquals(hostFunction, hostFunction.execute(hostFunction));

            // host to guest error
            Value guestErrorExecutable = context.getBindings("sl").getMember("error");
            try {
                guestErrorExecutable.execute();
                fail();
            } catch (PolyglotException e) {
                assertTrue(e.isGuestException());
                assertFalse(e.isInternalError());
                assertTrue(e.getMessage(), e.getMessage().startsWith("Error(s) parsing script"));
            }

            // host to host error
            Value hostErrorExecutable = thisValue.getMember("triggerHostError");
            try {
                hostErrorExecutable.execute();
                fail();
            } catch (PolyglotException e) {
                assertTrue(e.isHostException());
                assertFalse(e.isInternalError());
                assertTrue(e.getMessage(), e.getMessage().startsWith("test message"));
            }

            // host to guest to host error
            // not yet supported (GR-22699)
            // testGuestHostGuestError(context, hostErrorExecutable);
        }
    }

    static void testGuestHostGuestError(Context context, Value hostErrorExecutable) {
        Value guestCallFunction = context.getBindings("sl").getMember("call");
        try {
            // not yet supported.
            guestCallFunction.execute(hostErrorExecutable);
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.isHostException());
            assertFalse(e.isInternalError());
            assertTrue(e.getMessage(), e.getMessage().startsWith("test message"));
        }
    }

    private static AbstractPolyglotImpl getPolyglotImpl() throws Throwable {
        Class<?> implHolder = Class.forName(Engine.class.getName() + "$ImplHolder");
        Field f = implHolder.getDeclaredField("IMPL");
        ReflectionUtils.setAccessible(f, true);
        return (AbstractPolyglotImpl) f.get(null);
    }

    private static void setPolyglotImpl(AbstractPolyglotImpl impl) throws Throwable {
        Class<?> implHolder = Class.forName(Engine.class.getName() + "$ImplHolder");
        Field f = implHolder.getDeclaredField("IMPL");
        ReflectionUtils.setAccessible(f, true);
        f.set(null, impl);
    }

}
