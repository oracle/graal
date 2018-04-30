/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class FunctionalInterfaceTest {
    private Context context;
    private Env env;

    @Before
    public void before() {
        context = Context.newBuilder().allowAllAccess(true).build();
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected LanguageContext createContext(Env contextEnv) {
                env = contextEnv;
                return super.createContext(contextEnv);
            }
        });
        context.initialize(ProxyLanguage.ID);
        context.enter();
        assertNotNull(env);
    }

    @After
    public void after() {
        context.leave();
        context.close();
    }

    @SuppressWarnings("static-method")
    public static final class HttpServer {
        public String requestHandler(Supplier<String> handler) {
            return handler.get();
        }

        public Supplier<String> requestHandler() {
            return null;
        }
    }

    @Test
    public void testFunctionalInterface() throws InteropException {
        TruffleObject server = (TruffleObject) env.asGuestValue(new HttpServer());
        Object result = ForeignAccess.sendInvoke(Message.createInvoke(1).createNode(), server, "requestHandler", new TestExecutable());
        assertEquals("narf", result);
    }

    @Test
    public void testThread() throws InteropException {
        TruffleObject threadClass = (TruffleObject) env.lookupHostSymbol("java.lang.Thread");
        Object result = ForeignAccess.sendNew(Message.createNew(1).createNode(), threadClass, new TestExecutable());
        assertTrue(env.isHostObject(result));
        Object thread = env.asHostObject(result);
        assertTrue(thread instanceof Thread);
    }

    @MessageResolution(receiverType = TestExecutable.class)
    static final class TestExecutable implements TruffleObject {
        static boolean isInstance(TruffleObject obj) {
            return obj instanceof TestExecutable;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return TestExecutableForeign.ACCESS;
        }

        @SuppressWarnings("unused")
        @Resolve(message = "IS_EXECUTABLE")
        abstract static class IsExecutable extends Node {
            boolean access(TestExecutable obj) {
                return true;
            }
        }

        @SuppressWarnings("unused")
        @Resolve(message = "EXECUTE")
        abstract static class Execute extends Node {
            String access(TestExecutable obj, Object... args) {
                return "narf";
            }
        }
    }
}
