/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class HostAdapterTest {

    static class TestContext implements AutoCloseable {
        protected Context context;
        protected TruffleLanguage.Env env;

        TestContext() {
            this(builder -> builder.allowHostAccess(HostAccess.EXPLICIT));
        }

        TestContext(Consumer<Context.Builder> customize) {
            Context.Builder builder = Context.newBuilder();
            customize.accept(builder);
            context = builder.build();
            ProxyLanguage.setDelegate(new ProxyLanguage() {
                @Override
                protected LanguageContext createContext(TruffleLanguage.Env contextEnv) {
                    env = contextEnv;
                    return super.createContext(contextEnv);
                }
            });
            context.initialize(ProxyLanguage.ID);
            context.enter();
            assertNotNull(env);
        }

        @Override
        public void close() {
            context.leave();
            context.close();
        }
    }

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    @Test
    public void testCreateHostAdapterFromInterface() throws InteropException {
        try (TestContext c = new TestContext((b) -> b.allowHostAccess(explicitHostAccessAllowImplementations(Callable.class)))) {
            TruffleLanguage.Env env = c.env;
            Object adapter = env.createHostAdapterClass(new Class<?>[]{Callable.class});
            Object instance = INTEROP.instantiate(adapter, env.asGuestValue(ProxyObject.fromMap(Collections.singletonMap("call", (ProxyExecutable) (args) -> 42))));
            assertEquals(42, INTEROP.invokeMember(instance, "call"));
        }
    }

    @Test
    public void testCreateHostAdapterFromClass() throws InteropException {
        try (TestContext c = new TestContext((b) -> b.allowHostAccess(explicitHostAccessAllowImplementations(Extensible.class)))) {
            TruffleLanguage.Env env = c.env;
            Object adapter = env.createHostAdapterClass(new Class<?>[]{Extensible.class});
            Object instance1 = INTEROP.instantiate(adapter, env.asGuestValue(ProxyObject.fromMap(Collections.singletonMap("abstractMethod", (ProxyExecutable) (args) -> "override"))));
            assertEquals("override", INTEROP.invokeMember(instance1, "abstractMethod"));
            assertEquals("base", INTEROP.invokeMember(instance1, "baseMethod"));

            Object instance2 = INTEROP.instantiate(adapter, env.asGuestValue(ProxyObject.fromMap(Collections.singletonMap("baseMethod", (ProxyExecutable) (args) -> "override"))));
            assertEquals("override", INTEROP.invokeMember(instance2, "baseMethod"));
            try {
                INTEROP.invokeMember(instance2, "abstractMethod");
                fail("should have thrown");
            } catch (Exception e) {
            }
        }
    }

    @Test
    public void testCreateHostAdapterFromClassAndInterfaces() throws InteropException {
        Class<?>[] supertypes = new Class<?>[]{Extensible.class, Callable.class, Interface.class};
        try (TestContext c = new TestContext((b) -> b.allowHostAccess(explicitHostAccessAllowImplementations(supertypes)))) {
            TruffleLanguage.Env env = c.env;
            Object adapter = env.createHostAdapterClass(supertypes);
            Map<String, Object> impl = new HashMap<>();
            impl.put("abstractMethod", (ProxyExecutable) (args) -> "abstractMethodImpl");
            impl.put("baseMethod", (ProxyExecutable) (args) -> "baseMethodImpl");
            impl.put("call", (ProxyExecutable) (args) -> "callImpl");
            impl.put("defaultMethod", (ProxyExecutable) (args) -> "defaultMethodImpl");
            Object instance = INTEROP.instantiate(adapter, env.asGuestValue(ProxyObject.fromMap(impl)));

            assertEquals("abstractMethodImpl", INTEROP.invokeMember(instance, "abstractMethod"));
            assertEquals("baseMethodImpl", INTEROP.invokeMember(instance, "baseMethod"));
            assertEquals("callImpl", INTEROP.invokeMember(instance, "call"));
            assertEquals("defaultMethodImpl", INTEROP.invokeMember(instance, "defaultMethod"));

            // call super methods directly via 'super' member
            Object superInstance = INTEROP.readMember(instance, "super");
            assertEquals("base", INTEROP.invokeMember(superInstance, "baseMethod"));
            assertEquals("default", INTEROP.invokeMember(superInstance, "defaultMethod"));

            // members are dynamically resolved
            impl.remove("baseMethod");
            impl.remove("defaultMethod");
            assertEquals("base", INTEROP.invokeMember(instance, "baseMethod"));
            assertEquals("default", INTEROP.invokeMember(instance, "defaultMethod"));
        }
    }

    @Test
    public void testCreateHostAdapterFromClassWithConstructorParams() throws InteropException {
        try (TestContext c = new TestContext((b) -> b.allowHostAccess(explicitHostAccessAllowImplementations(NonDefaultConstructor.class)))) {
            TruffleLanguage.Env env = c.env;
            Object adapter = env.createHostAdapterClass(new Class<?>[]{NonDefaultConstructor.class});
            Map<String, Object> impl = new HashMap<>();
            impl.put("abstractMethod", (ProxyExecutable) (args) -> "abstractMethodImpl");
            impl.put("finalMethod", (ProxyExecutable) (args) -> "finalMethodImpl");
            Object instance = INTEROP.instantiate(adapter, "concreteName", env.asGuestValue(ProxyObject.fromMap(impl)));
            assertEquals("abstractMethodImpl", INTEROP.invokeMember(instance, "abstractMethod"));
            assertEquals("final", INTEROP.invokeMember(instance, "finalMethod"));
            assertEquals("concreteName", INTEROP.readMember(instance, "name"));
        }
    }

    @Test
    public void testCreateHostAdapterImplementationsNotAllowed() {
        try (TestContext c = new TestContext((b) -> b.allowHostAccess(HostAccess.EXPLICIT))) {
            TruffleLanguage.Env env = c.env;
            try {
                env.createHostAdapterClass(new Class<?>[]{Interface.class});
                fail("should have thrown");
            } catch (IllegalArgumentException | SecurityException e) {
            }
            try {
                env.createHostAdapterClass(new Class<?>[]{Interface.class, Callable.class});
                fail("should have thrown");
            } catch (IllegalArgumentException | SecurityException e) {
            }
            try {
                env.createHostAdapterClass(new Class<?>[]{Extensible.class});
                fail("should have thrown");
            } catch (IllegalArgumentException | SecurityException e) {
            }
            try {
                env.createHostAdapterClass(new Class<?>[]{Extensible.class, Interface.class, Callable.class});
                fail("should have thrown");
            } catch (IllegalArgumentException | SecurityException e) {
            }
        }
    }

    @Test
    public void testCreateHostAdapterIllegalArgument() {
        try (TestContext c = new TestContext((b) -> b.allowHostAccess(HostAccess.EXPLICIT))) {
            TruffleLanguage.Env env = c.env;
            try {
                env.createHostAdapterClass(new Class<?>[]{});
                fail("should have thrown");
            } catch (IllegalArgumentException e) {
            }
            try {
                env.createHostAdapterClass(null);
                fail("should have thrown");
            } catch (NullPointerException e) {
            }
        }
    }

    @Test
    public void testCreateHostAdapterThis() throws InteropException {
        Class<?>[] supertypes = new Class<?>[]{Extensible.class, Interface.class};
        try (TestContext c = new TestContext((b) -> b.allowHostAccess(explicitHostAccessAllowImplementations(supertypes)))) {
            TruffleLanguage.Env env = c.env;
            Object adapter = env.createHostAdapterClass(supertypes);
            Map<String, Object> impl = new HashMap<>();
            impl.put("abstractMethod", (ProxyExecutable) (args) -> "abstractMethodImpl");
            impl.put("baseMethod", (ProxyExecutable) (args) -> "baseMethodImpl");
            impl.put("defaultMethod", (ProxyExecutable) (args) -> "defaultMethodImpl");
            Object guestObject = env.asGuestValue(ProxyObject.fromMap(impl));
            Object instance = INTEROP.instantiate(adapter, guestObject);

            assertEquals("abstractMethodImpl", INTEROP.invokeMember(instance, "abstractMethod"));
            assertEquals("baseMethodImpl", INTEROP.invokeMember(instance, "baseMethod"));
            assertEquals("defaultMethodImpl", INTEROP.invokeMember(instance, "defaultMethod"));

            // call super methods directly via 'super' member
            Object superInstance = INTEROP.readMember(instance, "super");
            assertEquals("base", INTEROP.invokeMember(superInstance, "baseMethod"));
            assertEquals("default", INTEROP.invokeMember(superInstance, "defaultMethod"));

            // get guest object via 'this' member
            Object thisInstance = INTEROP.readMember(instance, "this");
            assertEquals(guestObject, thisInstance);
        }
    }

    @Test
    public void testHostAdapterWithCustomHostAccess() throws InteropException {
        Class<?>[] supertypes = new Class<?>[]{Extensible.class, Interface.class};
        try (TestContext c = new TestContext((b) -> b.allowHostAccess(minimalHostAccessAllowImplementations(supertypes)))) {
            TruffleLanguage.Env env = c.env;
            Object adapter = env.createHostAdapterClass(supertypes);
            Map<String, Object> impl = new HashMap<>();
            impl.put("abstractMethod", (ProxyExecutable) (args) -> "abstractMethodImpl");
            impl.put("baseMethod", (ProxyExecutable) (args) -> "baseMethodImpl");
            impl.put("defaultMethod", (ProxyExecutable) (args) -> "defaultMethodImpl");
            Object guestObject = env.asGuestValue(ProxyObject.fromMap(impl));
            Object instance = INTEROP.instantiate(adapter, guestObject);

            assertEquals("abstractMethodImpl", INTEROP.invokeMember(instance, "abstractMethod"));
            assertEquals("baseMethodImpl", INTEROP.invokeMember(instance, "baseMethod"));
            assertEquals("defaultMethodImpl", INTEROP.invokeMember(instance, "defaultMethod"));

            // get guest object via 'this' member
            Object thisInstance = INTEROP.readMember(instance, "this");
            assertEquals(guestObject, thisInstance);
        }
    }

    @Test
    public void testStackedHostAdaptersWithClassOverrides() throws InteropException {
        Class<?>[] supertypes = new Class<?>[]{Extensible.class, Interface.class};
        try (TestContext c = new TestContext((b) -> b.allowHostAccess(HostAccess.newBuilder(HostAccess.EXPLICIT).allowAllImplementations(true).allowAllClassImplementations(true).build()))) {
            TruffleLanguage.Env env = c.env;
            Map<String, Object> impl1 = new HashMap<>();
            impl1.put("abstractMethod", (ProxyExecutable) (args) -> "abstractMethodImpl1");
            impl1.put("baseMethod", (ProxyExecutable) (args) -> "baseMethodImpl1");
            impl1.put("defaultMethod", (ProxyExecutable) (args) -> "defaultMethodImpl1");
            Object guestObject1 = env.asGuestValue(ProxyObject.fromMap(impl1));
            Object adapterClass1 = env.createHostAdapterClassWithStaticOverrides(supertypes, guestObject1);
            Object parent = INTEROP.instantiate(adapterClass1);

            assertEquals("abstractMethodImpl1", INTEROP.invokeMember(parent, "abstractMethod"));
            assertEquals("baseMethodImpl1", INTEROP.invokeMember(parent, "baseMethod"));
            assertEquals("defaultMethodImpl1", INTEROP.invokeMember(parent, "defaultMethod"));

            Map<String, Object> impl2 = new HashMap<>();
            impl2.put("abstractMethod", (ProxyExecutable) (args) -> "abstractMethodImpl2");
            impl2.put("baseMethod", (ProxyExecutable) (args) -> "baseMethodImpl2");
            impl2.put("defaultMethod", (ProxyExecutable) (args) -> "defaultMethodImpl2");
            Object guestObject2 = env.asGuestValue(ProxyObject.fromMap(impl2));
            Object adapterClass2 = env.createHostAdapterClass(new Class<?>[]{Interface.class, (Class<?>) env.asHostObject(adapterClass1)});
            Object instance = INTEROP.instantiate(adapterClass2, guestObject2);

            assertEquals("abstractMethodImpl2", INTEROP.invokeMember(instance, "abstractMethod"));
            assertEquals("baseMethodImpl2", INTEROP.invokeMember(instance, "baseMethod"));
            assertEquals("defaultMethodImpl2", INTEROP.invokeMember(instance, "defaultMethod"));

            // call super methods directly via 'super' member
            Object superInstance = INTEROP.readMember(instance, "super");
            assertEquals("abstractMethodImpl1", INTEROP.invokeMember(superInstance, "abstractMethod"));
            assertEquals("baseMethodImpl1", INTEROP.invokeMember(superInstance, "baseMethod"));
            assertEquals("defaultMethodImpl1", INTEROP.invokeMember(superInstance, "defaultMethod"));
        }
    }

    @Test
    public void testStackedHostAdaptersWithoutClassOverrides() throws InteropException {
        Class<?>[] supertypes = new Class<?>[]{Extensible.class, Interface.class};
        try (TestContext c = new TestContext((b) -> b.allowHostAccess(HostAccess.newBuilder(HostAccess.EXPLICIT).allowAllImplementations(true).allowAllClassImplementations(true).build()))) {
            TruffleLanguage.Env env = c.env;
            Map<String, Object> impl1 = new HashMap<>();
            impl1.put("abstractMethod", (ProxyExecutable) (args) -> "abstractMethodImpl1");
            impl1.put("baseMethod", (ProxyExecutable) (args) -> "baseMethodImpl1");
            impl1.put("defaultMethod", (ProxyExecutable) (args) -> "defaultMethodImpl1");
            Object guestObject1 = env.asGuestValue(ProxyObject.fromMap(impl1));
            Object adapterClass1 = env.createHostAdapterClass(supertypes);
            Object parent = INTEROP.instantiate(adapterClass1, guestObject1);

            assertEquals("abstractMethodImpl1", INTEROP.invokeMember(parent, "abstractMethod"));
            assertEquals("baseMethodImpl1", INTEROP.invokeMember(parent, "baseMethod"));
            assertEquals("defaultMethodImpl1", INTEROP.invokeMember(parent, "defaultMethod"));

            Map<String, Object> impl2 = new HashMap<>();
            impl2.put("abstractMethod", (ProxyExecutable) (args) -> "abstractMethodImpl2");
            impl2.put("baseMethod", (ProxyExecutable) (args) -> "baseMethodImpl2");
            impl2.put("defaultMethod", (ProxyExecutable) (args) -> "defaultMethodImpl2");
            Object guestObject2 = env.asGuestValue(ProxyObject.fromMap(impl2));
            Object adapterClass2 = env.createHostAdapterClass(new Class<?>[]{Interface.class, (Class<?>) env.asHostObject(adapterClass1)});
            Object instance = INTEROP.instantiate(adapterClass2, guestObject1, guestObject2);

            assertEquals("abstractMethodImpl2", INTEROP.invokeMember(instance, "abstractMethod"));
            assertEquals("baseMethodImpl2", INTEROP.invokeMember(instance, "baseMethod"));
            assertEquals("defaultMethodImpl2", INTEROP.invokeMember(instance, "defaultMethod"));

            // call super methods directly via 'super' member
            Object superInstance = INTEROP.readMember(instance, "super");
            assertEquals("abstractMethodImpl1", INTEROP.invokeMember(superInstance, "abstractMethod"));
            assertEquals("baseMethodImpl1", INTEROP.invokeMember(superInstance, "baseMethod"));
            assertEquals("defaultMethodImpl1", INTEROP.invokeMember(superInstance, "defaultMethod"));
        }
    }

    private static HostAccess explicitHostAccessAllowImplementations(Class<?>... types) {
        HostAccess.Builder b = HostAccess.newBuilder(HostAccess.EXPLICIT);
        for (Class<?> type : types) {
            b.allowImplementations(type);
        }
        return b.build();
    }

    private static HostAccess minimalHostAccessAllowImplementations(Class<?>... types) {
        HostAccess.Builder b = HostAccess.newBuilder();
        for (Class<?> type : types) {
            b.allowImplementations(type);
        }
        return b.build();
    }

    public abstract static class Extensible {
        @HostAccess.Export
        public String baseMethod() {
            return "base";
        }

        @HostAccess.Export
        public abstract String abstractMethod();
    }

    public interface Interface {
        @HostAccess.Export
        String interfaceMethod();

        @HostAccess.Export
        default String defaultMethod() {
            return "default";
        }
    }

    public abstract static class NonDefaultConstructor {

        @HostAccess.Export public final String name;

        public NonDefaultConstructor(String name) {
            this.name = name;
        }

        public NonDefaultConstructor(String name1, String name2) {
            this.name = name1 + ":" + name2;
        }

        @HostAccess.Export
        @SuppressWarnings("static-method")
        public final String finalMethod() {
            return "final";
        }

        @HostAccess.Export
        public abstract String abstractMethod();
    }
}
