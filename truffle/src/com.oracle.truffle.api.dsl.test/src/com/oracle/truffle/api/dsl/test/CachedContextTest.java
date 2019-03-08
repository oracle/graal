/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.CachedContextTestFactory.Valid1NodeGen;
import com.oracle.truffle.api.dsl.test.CachedContextTestFactory.Valid2NodeGen;
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
    abstract static class Valid1Node extends Node {

        abstract Object execute(Object argument);

        @Specialization
        static String s0(int value,
                        @CachedContext(CachedContextTestLanguage.class) Env context,
                        @CachedContext(CachedContextTestLanguage.class) Supplier<Env> contextSupplier) {
            assertSame(CachedContextTestLanguage.getCurrentContext(), context);
            assertSame(CachedContextTestLanguage.getCurrentContext(), contextSupplier.get());
            return "s0";
        }

        @Specialization
        static String s1(double value,
                        @CachedContext(CachedContextTestLanguage.class) Env context,
                        @CachedContext(CachedContextTestLanguage.class) Supplier<Env> contextSupplier) {
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

    private static final String TEST_LANGUAGE = "CachedContextTestLanguage";

    @Registration(id = TEST_LANGUAGE, name = TEST_LANGUAGE, contextPolicy = ContextPolicy.SHARED)
    public static class CachedContextTestLanguage extends TruffleLanguage<Env> {

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
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

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
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

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
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
                        @ExpectError("Invalid @CachedContext specification. The parameter type must match the context type 'Env' or 'Supplier<Env>'.") //
                        @CachedContext(CachedContextTestLanguage.class) Object context) {
            throw new AssertionError();
        }
    }

    abstract static class CachedLanguageError5Node extends Node {

        abstract Object execute(Object argument);

        @Specialization
        static String s0(Object value,
                        @ExpectError("Invalid @CachedContext specification. The parameter type must match the context type 'Env' or 'Supplier<Env>'.")//
                        @CachedContext(CachedContextTestLanguage.class) Supplier<Object> context) {
            throw new AssertionError();
        }
    }

    abstract static class CachedLanguageError6Node extends Node {

        abstract Object execute(Object argument);

        @Specialization
        static String s0(Object value,
                        @ExpectError("Invalid @CachedContext specification. The parameter type must match the context type 'Env' or 'Supplier<Env>'.") //
                        @CachedContext(CachedContextTestLanguage.class) Supplier<?> context) {
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
        final Object m1(@CachedContext(CachedContextTestLanguage.class) Supplier<Env> env) {
            return "m1";
        }
    }

}
