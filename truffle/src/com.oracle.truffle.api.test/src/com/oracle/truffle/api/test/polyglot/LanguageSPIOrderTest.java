/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.source.Source;

public class LanguageSPIOrderTest {

    private static final AtomicInteger NEXT_ORDER_INDEX = new AtomicInteger();
    private static final Map<String, OrderContext> currentContext = new HashMap<>();

    static final String PUBLIC = "SPIPublicLanguage";
    static final String INTERNAL = "SPIInternalLanguage";
    static final String DEPENDENT = "SPIDependentLanguage";
    static final String TRANSITIVE = "SPITransitiveLanguage";
    static final String CYCLIC1 = "SPICyclicLanguage1";
    static final String CYCLIC2 = "SPICyclicLanguage2";
    static final String ACTIVATE_DURING_FINALIZE = "SPIActivateDuringFinalize";
    static final String ACTIVATE_DURING_DISPOSE = "SPIActivateDuringDispose";

    @Before
    public void setup() {
        currentContext.clear();
        NEXT_ORDER_INDEX.set(0);
    }

    private static OrderContext getOrder(String languageId) {
        return currentContext.get(languageId);
    }

    @Test
    public void testOrderSimple() {
        Context context = Context.create();
        context.initialize(PUBLIC);
        assertNotNull(getOrder(PUBLIC));
        assertNotNull(getOrder(INTERNAL));
        assertEquals(0, getOrder(PUBLIC).createContext);
        assertEquals(1, getOrder(INTERNAL).createContext);
        assertEquals(2, getOrder(INTERNAL).initializeThread);
        assertEquals(3, getOrder(INTERNAL).initializeContext);
        assertEquals(4, getOrder(PUBLIC).initializeThread);
        assertEquals(5, getOrder(PUBLIC).initializeContext);
        context.close();
        assertEquals(6, getOrder(PUBLIC).finalizeContext);
        assertEquals(7, getOrder(INTERNAL).finalizeContext);
        assertEquals(8, getOrder(PUBLIC).disposeThread);
        assertEquals(9, getOrder(PUBLIC).disposeContext);
        assertEquals(10, getOrder(INTERNAL).disposeThread);
        assertEquals(11, getOrder(INTERNAL).disposeContext);
    }

    @Test
    public void testOrderDependent() {
        Context context = Context.create();
        context.initialize(TRANSITIVE);
        assertNotNull(getOrder(TRANSITIVE));
        assertNotNull(getOrder(DEPENDENT));
        assertNotNull(getOrder(PUBLIC));
        assertNotNull(getOrder(INTERNAL));
        assertEquals(0, getOrder(TRANSITIVE).createContext);
        assertEquals(1, getOrder(TRANSITIVE).initializeThread);
        assertEquals(2, getOrder(TRANSITIVE).initializeContext);
        assertEquals(3, getOrder(DEPENDENT).createContext);
        assertEquals(4, getOrder(DEPENDENT).initializeThread);
        assertEquals(5, getOrder(DEPENDENT).initializeContext);
        assertEquals(6, getOrder(PUBLIC).createContext);
        assertEquals(7, getOrder(INTERNAL).createContext);
        assertEquals(8, getOrder(INTERNAL).initializeThread);
        assertEquals(9, getOrder(INTERNAL).initializeContext);
        assertEquals(10, getOrder(PUBLIC).initializeThread);
        assertEquals(11, getOrder(PUBLIC).initializeContext);
        context.close();
        assertEquals(12, getOrder(TRANSITIVE).finalizeContext);
        assertEquals(13, getOrder(DEPENDENT).finalizeContext);
        assertEquals(14, getOrder(PUBLIC).finalizeContext);
        assertEquals(15, getOrder(INTERNAL).finalizeContext);

        assertEquals(16, getOrder(TRANSITIVE).disposeThread);
        assertEquals(17, getOrder(TRANSITIVE).disposeContext);

        assertEquals(18, getOrder(DEPENDENT).disposeThread);
        assertEquals(19, getOrder(DEPENDENT).disposeContext);

        assertEquals(20, getOrder(PUBLIC).disposeThread);
        assertEquals(21, getOrder(PUBLIC).disposeContext);

        assertEquals(22, getOrder(INTERNAL).disposeThread);
        assertEquals(23, getOrder(INTERNAL).disposeContext);

    }

    @Test
    public void testCyclicLanguageDependencies() {
        Context context = Context.create();
        try {
            context.initialize(CYCLIC1);
            fail();
        } catch (IllegalArgumentException e) {
        }

        try {
            context.initialize(CYCLIC1);
            fail();
        } catch (IllegalArgumentException e) {
        }
        try {
            context.initialize(CYCLIC2);
            fail();
        } catch (IllegalArgumentException e) {
        }
        context.close();
    }

    @Test
    public void testLanguageAccessRights() {
        Context context = Context.create(DEPENDENT);
        try {
            context.initialize(PUBLIC);
            fail();
        } catch (IllegalArgumentException e) {
        }
        try {
            context.initialize(TRANSITIVE);
            fail();
        } catch (IllegalArgumentException e) {
        }
        try {
            context.initialize(INTERNAL);
            fail();
        } catch (IllegalArgumentException e) {
        }
        context.initialize(DEPENDENT);
        try {
            context.initialize(PUBLIC);
            fail();
        } catch (IllegalArgumentException e) {
        }
        try {
            context.initialize(TRANSITIVE);
            fail();
        } catch (IllegalArgumentException e) {
        }
        try {
            context.initialize(INTERNAL);
            fail();
        } catch (IllegalArgumentException e) {
        }
        context.close();
    }

    @Test
    public void testActivateDuringFinalize() {
        Context context = Context.create();
        context.initialize(ACTIVATE_DURING_FINALIZE);
        assertNotNull(getOrder(ACTIVATE_DURING_FINALIZE));
        assertEquals(0, getOrder(ACTIVATE_DURING_FINALIZE).createContext);
        assertEquals(1, getOrder(ACTIVATE_DURING_FINALIZE).initializeThread);
        assertEquals(2, getOrder(ACTIVATE_DURING_FINALIZE).initializeContext);

        /*
         * context uses PUBLIC which uses INTERNAL in the finalizeContext methods
         */
        context.close();
        assertEquals(3, getOrder(ACTIVATE_DURING_FINALIZE).finalizeContext);
        assertEquals(4, getOrder(PUBLIC).createContext);
        assertEquals(5, getOrder(INTERNAL).createContext);
        assertEquals(6, getOrder(INTERNAL).initializeThread);
        assertEquals(7, getOrder(INTERNAL).initializeContext);
        assertEquals(8, getOrder(PUBLIC).initializeThread);
        assertEquals(9, getOrder(PUBLIC).initializeContext);

        assertEquals(10, getOrder(PUBLIC).finalizeContext);
        assertEquals(11, getOrder(INTERNAL).finalizeContext);
        assertEquals(12, getOrder(ACTIVATE_DURING_FINALIZE).disposeThread);
        assertEquals(13, getOrder(ACTIVATE_DURING_FINALIZE).disposeContext);
        assertEquals(14, getOrder(PUBLIC).disposeThread);
        assertEquals(15, getOrder(PUBLIC).disposeContext);
        assertEquals(16, getOrder(INTERNAL).disposeThread);
        assertEquals(17, getOrder(INTERNAL).disposeContext);
    }

    @Test
    public void testActivateDuringDispose() {
        Context context = Context.create();
        context.initialize(ACTIVATE_DURING_DISPOSE);
        assertNotNull(getOrder(ACTIVATE_DURING_DISPOSE));
        assertEquals(0, getOrder(ACTIVATE_DURING_DISPOSE).createContext);
        assertEquals(1, getOrder(ACTIVATE_DURING_DISPOSE).initializeThread);
        assertEquals(2, getOrder(ACTIVATE_DURING_DISPOSE).initializeContext);

        context.close();
        assertEquals(3, getOrder(ACTIVATE_DURING_DISPOSE).finalizeContext);
        assertEquals(4, getOrder(ACTIVATE_DURING_DISPOSE).disposeThread);
        assertEquals(5, getOrder(ACTIVATE_DURING_DISPOSE).disposeContext);
    }

    static class OrderContext {

        TruffleLanguage.Env env;
        int createContext = -1;
        int initializeContext = -1;
        int initializeThread = -1;
        int finalizeContext = -1;
        int disposeThread = -1;
        int disposeContext = -1;

    }

    static class BaseLang extends TruffleLanguage<OrderContext> {

        @Override
        protected OrderContext createContext(TruffleLanguage.Env env) {
            OrderContext context = new OrderContext();
            context.env = env;
            context.createContext = nextId();

            // there is no better way to get to the language id dynamically.
            String languageId = new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return null;
                }
            }.getLanguageInfo().getId();
            if (currentContext.containsKey(languageId)) {
                throw new AssertionError("Created twice " + languageId);
            }
            currentContext.put(languageId, context);
            return context;
        }

        private static int nextId() {
            int id = NEXT_ORDER_INDEX.getAndIncrement();
            return id;
        }

        @Override
        protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(42));
        }

        @Override
        protected void initializeContext(OrderContext context) throws Exception {
            context.initializeContext = nextId();
        }

        @Override
        protected void initializeThread(OrderContext context, Thread thread) {
            context.initializeThread = nextId();
        }

        @Override
        protected void finalizeContext(OrderContext context) {
            context.finalizeContext = nextId();
        }

        @Override
        protected void disposeThread(OrderContext context, Thread thread) {
            context.disposeThread = nextId();
        }

        @Override
        protected void disposeContext(OrderContext context) {
            context.disposeContext = nextId();
        }

        protected void useLanguage(OrderContext context, String id) {
            // initializes the language
            context.env.parseInternal(Source.newBuilder(id, "", "").build());
        }
    }

    @Registration(id = PUBLIC, name = "", version = "", internal = false)
    public static class PublicLanguage extends BaseLang {

        @Override
        protected OrderContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
            OrderContext context = super.createContext(env);
            useLanguage(context, LanguageSPIOrderTest.INTERNAL);
            return context;
        }

        @Override
        protected void initializeContext(OrderContext context) throws Exception {
            super.initializeContext(context);
            useLanguage(context, LanguageSPIOrderTest.INTERNAL);
        }

    }

    @Registration(id = INTERNAL, name = "", version = "", internal = true)
    public static class InternalLanguage extends BaseLang {

        @Override
        protected OrderContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
            OrderContext context = super.createContext(env);
            try {
                // cyclic language use should fail in createContext.
                useLanguage(context, PUBLIC);
                Assert.fail();
            } catch (Exception e) {
            }
            return context;
        }

        @Override
        protected void initializeContext(OrderContext context) throws Exception {
            super.initializeContext(context);
            // cyclic language use is allowed in initializeContext.
            useLanguage(context, LanguageSPIOrderTest.INTERNAL);
        }

    }

    @Registration(id = LanguageSPIOrderTest.DEPENDENT, name = "", dependentLanguages = LanguageSPIOrderTest.PUBLIC)
    public static class DependentLanguage extends BaseLang {

        @Override
        protected void initializeContext(OrderContext context) throws Exception {
            super.initializeContext(context);
            useLanguage(context, LanguageSPIOrderTest.PUBLIC);
        }

    }

    @Registration(id = TRANSITIVE, name = "", dependentLanguages = LanguageSPIOrderTest.DEPENDENT)
    public static class TransitiveLanguage extends BaseLang {

        @Override
        protected void initializeContext(OrderContext context) throws Exception {
            super.initializeContext(context);
            useLanguage(context, LanguageSPIOrderTest.DEPENDENT);
        }

    }

    @Registration(id = CYCLIC2, name = "", dependentLanguages = CYCLIC1)
    public static class CyclicLanguage2 extends BaseLang {
    }

    @Registration(id = CYCLIC1, name = "", dependentLanguages = LanguageSPIOrderTest.CYCLIC2)
    public static class CyclicLanguage1 extends BaseLang {
    }

    @Registration(id = ACTIVATE_DURING_FINALIZE, name = "", dependentLanguages = PUBLIC)
    public static class ActivateDuringFinalizeLanguage extends BaseLang {

        @Override
        protected void finalizeContext(OrderContext context) {
            super.finalizeContext(context);
            useLanguage(context, LanguageSPIOrderTest.PUBLIC);
        }

    }

    @Registration(id = ACTIVATE_DURING_DISPOSE, name = "", dependentLanguages = PUBLIC)
    public static class ActivateDuringDisposeLanguage extends BaseLang {

        @Override
        protected void disposeContext(OrderContext context) {
            try {
                useLanguage(context, LanguageSPIOrderTest.PUBLIC);
                fail();
            } catch (IllegalStateException e) {
            }
            super.disposeContext(context);
        }

    }
}
