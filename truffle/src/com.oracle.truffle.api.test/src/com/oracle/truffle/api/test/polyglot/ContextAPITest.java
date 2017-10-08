/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.ForeignAccess.Factory26;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ContextAPITestLanguage.LanguageContext;

public class ContextAPITest {

    static LanguageContext langContext;

    @Test
    public void testContextCreateSingleLanguage() {
        Context context = Context.create(ContextAPITestLanguage.ID);
        try {
            context.eval(LanguageSPITestLanguage.ID, "");
            fail();
        } catch (IllegalStateException e) {
        }
        assertInternalNotAccessible(context);
        context.close();
    }

    private static void assertInternalNotAccessible(Context context) {
        try {
            context.eval(ContextAPITestInternalLanguage.ID, "");
            fail();
        } catch (IllegalStateException e) {
        }
        try {
            context.initialize(ContextAPITestInternalLanguage.ID);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            context.lookup(ContextAPITestInternalLanguage.ID, "foobar");
            fail();
        } catch (IllegalStateException e) {
        }
        assertFalse(context.getEngine().getLanguages().containsKey(ContextAPITestInternalLanguage.ID));
    }

    @Test
    public void testContextCreateAllLanguages() {
        Context context = Context.create();
        context.eval(ContextAPITestLanguage.ID, "");
        context.eval(LanguageSPITestLanguage.ID, "");
        assertInternalNotAccessible(context);
        context.close();
    }

    @Test
    public void testImportExport() {
        Context context = Context.create();
        context.exportSymbol("string", "bar");
        context.exportSymbol("null", null);
        context.exportSymbol("int", 42);
        Object object = new Object();
        context.exportSymbol("object", object);

        assertEquals("bar", context.importSymbol("string").asString());
        assertTrue(context.importSymbol("null").isNull());
        assertEquals(42, context.importSymbol("int").asInt());
        assertSame(object, context.importSymbol("object").asHostObject());
        assertNull(context.importSymbol("notexisting"));
        context.close();
    }

    @Test
    public void testInstrumentOption() {
        // Instrument options can be set to context builders with implicit engine:
        Context.Builder contextBuilder = Context.newBuilder();
        contextBuilder.option("optiontestinstr1.StringOption1", "Hello");
        contextBuilder.build();

    }

    @Test
    public void testInstrumentOptionAsContext() {
        // Instrument options are refused by context builders with an existing engine:
        Context.Builder contextBuilder = Context.newBuilder();
        contextBuilder.engine(Engine.create());
        contextBuilder.option("optiontestinstr1.StringOption1", "Hello");
        try {
            contextBuilder.build();
            fail();
        } catch (IllegalArgumentException ex) {
            // O.K.
            assertEquals("Option optiontestinstr1.StringOption1 is an engine option. Engine level options can only be configured for contexts without a shared engine set. " +
                            "To resolve this, configure the option when creating the Engine or create a context without a shared engine.", ex.getMessage());
        }
    }

    @Test
    public void testInvalidEngineOptionAsContext() {
        // Instrument options are refused by context builders with an existing engine:
        Context.Builder contextBuilder = Context.newBuilder();
        contextBuilder.engine(Engine.create());
        contextBuilder.option("optiontestinstr1.StringOption1+Typo", "100");
        try {
            contextBuilder.build();
            fail();
        } catch (IllegalArgumentException ex) {
            // O.K.
            assertTrue(ex.getMessage().startsWith("Could not find option with name optiontestinstr1.StringOption1+Typo."));
        }
    }

    public void testEnterLeave() {
        Context context = Context.create();
        testEnterLeave(context, 0);
        context.close();
    }

    private static void testEnterLeave(Context context, int depth) {
        try {
            context.leave();
            fail();
        } catch (IllegalStateException e) {
        }

        context.importSymbol("");
        context.enter();

        try {
            context.close();
            fail();
        } catch (IllegalStateException e) {
        }

        context.importSymbol("");
        context.enter();

        try {
            context.close();
            fail();
        } catch (IllegalStateException e) {
        }

        if (depth < 3) {
            Context innerContext = Context.create();
            testEnterLeave(innerContext, depth + 1);
            innerContext.close();
        }

        context.leave();

        try {
            context.close();
            fail();
        } catch (IllegalStateException e) {
        }

        context.leave();

        try {
            context.leave();
            fail();
        } catch (IllegalStateException e) {
        }

    }

    @Test
    public void testMultithreadeddEnterLeave() throws InterruptedException, ExecutionException {
        Context context = Context.create();
        ExecutorService service = Executors.newFixedThreadPool(20);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 200; i++) {
            futures.add(service.submit(() -> testEnterLeave(context, 0)));
        }

        for (Future<?> future : futures) {
            future.get();
        }

        context.close();
        service.shutdown();
        service.awaitTermination(1000, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testEnteredExecute() {
        Context context = Context.create(ContextAPITestLanguage.ID);

        // test outside
        testExecute(context);

        // test inside
        context.enter();
        testExecute(context);
        context.leave();

        // test enter twice
        context.enter();
        context.enter();
        testExecute(context);
        context.leave();
        testExecute(context);
        context.leave();
        testExecute(context);

        // test entered with inner context
        context.enter();

        Context context2 = Context.create(ContextAPITestLanguage.ID);
        testExecute(context2);
        context2.enter();
        testExecute(context2);
        context2.leave();
        context2.enter();
        context2.enter();
        testExecute(context2);
        context2.leave();
        testExecute(context2);
        context2.leave();
        context2.close();

        context.leave();

        // finally close the context
        context.close();
    }

    private static void testExecute(Context context) {
        ContextAPITestLanguage.runinside = (env) -> new MyTruffleObject();
        Value executable = context.eval(ContextAPITestLanguage.ID, "");
        assertEquals(42, executable.execute().asInt());
        assertEquals(42, executable.execute(42).asInt());
        executable.executeVoid();
        executable.executeVoid(42);
    }

    private static class MyTruffleObject implements TruffleObject {

        public ForeignAccess getForeignAccess() {
            return MyTruffleObjectFactory.INSTANCE;
        }

    }

    private static class MyTruffleObjectFactory implements Factory26 {

        private static final ForeignAccess INSTANCE = ForeignAccess.create(MyTruffleObject.class, new MyTruffleObjectFactory());

        public CallTarget accessIsExecutable() {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(true));
        }

        public CallTarget accessExecute(int argumentsLength) {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(42));
        }

        public CallTarget accessIsNull() {
            return null;
        }

        public CallTarget accessIsBoxed() {
            return null;
        }

        public CallTarget accessHasSize() {
            return null;
        }

        public CallTarget accessGetSize() {
            return null;
        }

        public CallTarget accessUnbox() {
            return null;
        }

        public CallTarget accessRead() {
            return null;
        }

        public CallTarget accessWrite() {
            return null;
        }

        public CallTarget accessInvoke(int argumentsLength) {
            return null;
        }

        public CallTarget accessNew(int argumentsLength) {
            return null;
        }

        public CallTarget accessKeys() {
            return null;
        }

        public CallTarget accessKeyInfo() {
            return null;
        }

        public CallTarget accessMessage(Message unknown) {
            return null;
        }

    }
}
