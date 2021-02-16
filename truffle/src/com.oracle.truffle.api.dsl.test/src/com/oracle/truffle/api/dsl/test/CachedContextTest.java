/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Introspectable;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.dsl.test.CachedContextTestFactory.CachedWithFallbackNodeGen;
import com.oracle.truffle.api.dsl.test.CachedContextTestFactory.TestContextCachingNodeGen;
import com.oracle.truffle.api.dsl.test.CachedContextTestFactory.TransitiveCachedLibraryNodeGen;
import com.oracle.truffle.api.dsl.test.CachedContextTestFactory.Valid1NodeGen;
import com.oracle.truffle.api.dsl.test.CachedContextTestFactory.Valid2NodeGen;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

@SuppressWarnings("unused")
public class CachedContextTest extends AbstractPolyglotTest {

    @Test
    public void testContextLookup1() {
        setupEnv();
        context.initialize(TEST_LANGUAGE);
        CachedContextTestLanguage testLanguage = CachedContextTestLanguage.getCurrentLanguage();
        Valid1Node node;
        node = adoptNode(testLanguage, Valid1NodeGen.create()).get();
        assertEquals("s0", node.execute(42));
        assertEquals("s1", node.execute(42d));

        node = adoptNode(this.language, Valid1NodeGen.create()).get();
        assertEquals("s0", node.execute(42));
        assertEquals("s1", node.execute(42d));

        node = adoptNode(null, Valid1NodeGen.create()).get();
        assertEquals("s0", node.execute(42));
        assertEquals("s1", node.execute(42d));

        node = Valid1NodeGen.getUncached();
        assertEquals("s0", node.execute(42));
        assertEquals("s1", node.execute(42d));
    }

    @GenerateUncached
    @Introspectable
    abstract static class Valid1Node extends Node {

        abstract Object execute(Object argument);

        @Specialization
        static String s0(int value,
                        @CachedContext(CachedContextTestLanguage.class) Env context,
                        @CachedContext(CachedContextTestLanguage.class) ContextReference<Env> contextSupplier) {
            assertSame(CachedContextTestLanguage.getCurrentContext(), context);
            assertSame(CachedContextTestLanguage.getCurrentContext(), contextSupplier.get());
            return "s0";
        }

        @Specialization
        static String s1(double value,
                        @CachedContext(CachedContextTestLanguage.class) Env context,
                        @CachedContext(CachedContextTestLanguage.class) ContextReference<Env> contextSupplier) {
            assertSame(CachedContextTestLanguage.getCurrentContext(), context);
            assertSame(CachedContextTestLanguage.getCurrentContext(), contextSupplier.get());
            return "s1";
        }
    }

    @Test
    public void testContextLookup2() {
        Engine engine = Engine.create();
        setupEnv(Context.newBuilder().engine(engine).build());
        context.initialize(TEST_LANGUAGE);
        CachedContextTestLanguage testLanguage = CachedContextTestLanguage.getCurrentLanguage();
        Env currentContext = CachedContextTestLanguage.getCurrentContext();
        Valid2Node node;
        node = adoptNode(testLanguage, Valid2NodeGen.create()).get();
        assertEquals(currentContext, node.execute());
        assertEquals(1, node.guardExecuted);

        setupEnv(Context.newBuilder().engine(engine).build());
        context.initialize(TEST_LANGUAGE);
        assertSame(testLanguage, CachedContextTestLanguage.getCurrentLanguage());
        Env otherContext = CachedContextTestLanguage.getCurrentContext();
        assertNotSame(currentContext, otherContext);
        assertEquals(otherContext, node.execute());
        assertEquals(2, node.guardExecuted);
    }

    public abstract static class Valid2Node extends Node {

        abstract Object execute();

        int guardExecuted = 0;

        boolean g0(Env ctx) {
            guardExecuted++;
            return true;
        }

        @Specialization(guards = "g0(ctx)")
        Object s0(@CachedContext(CachedContextTestLanguage.class) Env ctx) {
            return ctx;
        }
    }

    @Test
    public void testTransitiveCached() {
        setupEnv();
        context.initialize(TEST_LANGUAGE);
        TransitiveCachedLibraryNode node = adoptNode(TransitiveCachedLibraryNodeGen.create()).get();
        node.execute(1);
        node.execute(2);
        node.execute(2);
        try {
            node.execute(3);
            fail();
        } catch (UnsupportedSpecializationException o) {
        }
        assertEquals(3, node.invocationCount);
    }

    @SuppressWarnings("unused")
    public abstract static class TransitiveCachedLibraryNode extends Node {

        abstract Object execute(Object arg);

        private int invocationCount = 0;

        @Specialization(guards = "arg == cachedArg", limit = "2")
        Object doNative(Object arg,
                        @CachedContext(CachedContextTestLanguage.class) Env ctx,
                        @Cached("initArg(arg, ctx)") Object cachedArg,
                        @CachedLibrary("cachedArg") InteropLibrary interop) {
            invocationCount++;
            return null;
        }

        protected static final Object initArg(Object arg, Object ctx) {
            return arg;
        }
    }

    @Test
    public void testCacheWithFallback() {
        CachedWithFallback node;
        setupEnv();
        context.initialize(TEST_LANGUAGE);

        node = adoptNode(CachedWithFallbackNodeGen.create()).get();
        assertEquals("s0", node.execute(""));
        assertEquals("s0", node.execute(""));
        node.guard = false;
        assertEquals("fallback", node.execute(""));

        node = adoptNode(CachedWithFallbackNodeGen.create()).get();
        node.guard = false;
        assertEquals("fallback", node.execute(""));
        node.guard = true;
        assertEquals("s0", node.execute(""));
        assertEquals("s0", node.execute(""));
    }

    @SuppressWarnings("static-method")
    abstract static class CachedWithFallback extends Node {

        boolean guard = true;

        public abstract String execute(Object o);

        boolean isGuard(Object o) {
            return guard;
        }

        @Specialization(guards = {"context == cachedContext", "isGuard(o)"}, limit = "1")
        String s0(String o,
                        @CachedContext(CachedContextTestLanguage.class) Env context,
                        @Cached("context") Env cachedContext) {
            return "s0";
        }

        @Fallback
        String fallback(Object o) {
            return "fallback";
        }
    }

    @Test
    public void testContextCaching() {
        TestContextCaching node;
        Engine engine = Engine.create();
        Context c = Context.newBuilder().engine(engine).build();
        c.initialize(TEST_LANGUAGE);
        c.enter();
        node = adoptNode(TestContextCachingNodeGen.create()).get();
        assertEquals("s0", node.execute(""));
        c.leave();
        c.close();

        c = Context.newBuilder().engine(engine).build();
        c.initialize(TEST_LANGUAGE);
        c.enter();
        assertEquals("s0", node.execute(""));
        c.leave();
        c.close();

        c = Context.newBuilder().engine(engine).build();
        c.initialize(TEST_LANGUAGE);
        c.enter();
        try {
            node.execute("");
            fail();
        } catch (UnsupportedSpecializationException e) {
        }
        c.leave();
        c.close();
    }

    @SuppressWarnings("static-method")
    abstract static class TestContextCaching extends Node {

        public abstract String execute(Object o);

        @Specialization(guards = {"ref.get() == cachedContext"}, limit = "2")
        String s0(String o,
                        @CachedContext(CachedContextTestLanguage.class) ContextReference<Env> ref,
                        @Cached("ref.get()") Env cachedContext) {
            return "s0";
        }

    }

    @SuppressWarnings("static-method")
    abstract static class DynamicBindingError1 extends Node {

        public abstract String execute(Object o);

        @ExpectError("Limit expressions must not bind dynamic parameter values.")
        @Specialization(guards = {"ref.get() == cachedContext"}, limit = "computeLimit(ref.get())")
        String s0(String o,
                        @CachedContext(CachedContextTestLanguage.class) ContextReference<Env> ref,
                        @Cached("ref.get()") Env cachedContext) {
            return "s0";
        }

        static int computeLimit(Object o) {
            return 1;
        }

    }

    @SuppressWarnings("static-method")
    abstract static class DynamicBindingError2 extends Node {

        public abstract String execute(Object o);

        @ExpectError("Assumption expressions must not bind dynamic parameter values.")
        @Specialization(assumptions = "computeLimit(ref.get())")
        String s0(String o,
                        @CachedContext(CachedContextTestLanguage.class) ContextReference<Env> ref) {
            return "s0";
        }

        static Assumption computeLimit(Env o) {
            return null;
        }

    }

    @SuppressWarnings("static-method")
    abstract static class DynamicBindingError3 extends Node {

        public abstract String execute(Object o);

        @ExpectError("Assumption expressions must not bind dynamic parameter values.")
        @Specialization(assumptions = "computeLimit(ref.get())")
        String s0(String o,
                        @CachedLanguage LanguageReference<CachedContextTestLanguage> ref) {
            return "s0";
        }

        static Assumption computeLimit(CachedContextTestLanguage o) {
            return null;
        }

    }

    private static final String TEST_LANGUAGE = "CachedContextTestLanguage";

    @Registration(id = TEST_LANGUAGE, name = TEST_LANGUAGE, contextPolicy = ContextPolicy.SHARED)
    public static class CachedContextTestLanguage extends TruffleLanguage<Env> {

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        public static Env getCurrentContext() {
            return getCurrentContext(CachedContextTestLanguage.class);
        }

        public static CachedContextTestLanguage getCurrentLanguage() {
            return getCurrentLanguage(CachedContextTestLanguage.class);
        }

    }

    public static class InvalidLanguage1 extends TruffleLanguage<Env> {

        @Override
        protected Env createContext(Env env) {
            return env;
        }

    }

    /*
     * Language without registration is an invalid language.
     */
    private static final String INVALID_LANGUAGE2 = "CachedContextInvalidLanguage1";

    @Registration(id = INVALID_LANGUAGE2, name = INVALID_LANGUAGE2)
    public static class InvalidLanguage2<T> extends TruffleLanguage<T> {

        @Override
        protected T createContext(Env env) {
            return null;
        }

    }

    abstract static class CachedLanguageError1Node extends Node {

        abstract Object execute(Object argument);

        @Specialization
        static String s0(Object value,
                        @ExpectError("Invalid @CachedContext specification. The type 'InvalidLanguage1' is not a valid language type. Valid language types must be annotated with @Registration.")//
                        @CachedContext(InvalidLanguage1.class) Env context) {
            throw new AssertionError();
        }
    }

    abstract static class CachedLanguageError2Node extends Node {

        abstract Object execute(Object argument);

        @Specialization
        static String s0(Object value,
                        @ExpectError("Invalid @CachedContext specification. The type 'TruffleLanguage' is not a valid language type. Valid language types must be annotated with @Registration.")//
                        @CachedContext(TruffleLanguage.class) Env context) {
            throw new AssertionError();
        }
    }

    abstract static class CachedLanguageError3Node extends Node {

        abstract Object execute(Object argument);

        @Specialization
        static String s0(Object value,
                        @ExpectError("Invalid @CachedContext specification. The context type could not be inferred from super type 'TruffleLanguage<T>' in language 'InvalidLanguage2'.")//
                        @CachedContext(InvalidLanguage2.class) Object language) {
            throw new AssertionError();
        }
    }

    abstract static class CachedLanguageError4Node extends Node {

        abstract Object execute(Object argument);

        @Specialization
        static String s0(Object value,
                        @ExpectError("Invalid @CachedContext specification. The parameter type must match the context type 'Env' or 'ContextReference<Env>'.") //
                        @CachedContext(CachedContextTestLanguage.class) Object context) {
            throw new AssertionError();
        }
    }

    abstract static class CachedLanguageError5Node extends Node {

        abstract Object execute(Object argument);

        @Specialization
        static String s0(Object value,
                        @ExpectError("Invalid @CachedContext specification. The parameter type must match the context type 'Env' or 'ContextReference<Env>'.")//
                        @CachedContext(CachedContextTestLanguage.class) ContextReference<Object> context) {
            throw new AssertionError();
        }
    }

    abstract static class CachedLanguageError6Node extends Node {

        abstract Object execute(Object argument);

        @Specialization
        static String s0(Object value,
                        @ExpectError("Invalid @CachedContext specification. The parameter type must match the context type 'Env' or 'ContextReference<Env>'.") //
                        @CachedContext(CachedContextTestLanguage.class) ContextReference<?> context) {
            throw new AssertionError();
        }
    }

    abstract static class CachedLanguageError7Node extends Node {

        abstract Object execute(Object argument);

        /*
         * Warning expected here as cachedEnv does not bind any dynamic parameter.
         */
        @ExpectError("The limit expression has no effect. %")
        @Specialization(guards = "cachedEnv != null", limit = "3")
        static String s0(Object value,
                        @CachedContext(CachedContextTestLanguage.class) Env env,
                        @Cached("env") Env cachedEnv) {
            throw new AssertionError();
        }
    }

    @GenerateLibrary
    public abstract static class CachedContextTestLibrary extends Library {

        public abstract Object m0(Object receiver);

        public abstract Object m1(Object receiver);
    }

    @ExportLibrary(CachedContextTestLibrary.class)
    @SuppressWarnings("static-method")
    static class CachedContextLibraryReceiver {

        @ExportMessage
        final Object m0(@CachedContext(CachedContextTestLanguage.class) Env env) {
            return "m0";
        }

        @ExportMessage
        final Object m1(@CachedContext(CachedContextTestLanguage.class) Env env) {
            return "m1";
        }
    }

    abstract static class TestBaseNode extends Node {

        abstract Object execute();

    }

    @NodeChild
    public abstract static class CachedContextInvalidParametersNode extends TestBaseNode {

        @ExpectError("Method signature (Env) does not match to the expected signature: %")
        @Specialization
        Object doSomething(@CachedContext(CachedContextTestLanguage.class) Env ctx) {
            return null;
        }
    }

    public abstract static class CacheCachedContextTest extends Node {

        protected abstract boolean executeMatch(Object arg0);

        @ExpectError("The limit expression has no effect.%")
        @Specialization(guards = {"cachedGuard"}, limit = "1")
        boolean s0(Object arg0,
                        @CachedContext(CachedContextTestLanguage.class) Env env,
                        @Cached("getBoolean(env)") boolean cachedGuard) {
            return false;
        }

        static boolean getBoolean(Env context) {
            return context != null;
        }
    }

}
