/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

@RunWith(Parameterized.class)
public class HostAdapterTest extends AbstractPolyglotTest {

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    public enum Using {
        HostSymbol,
        HostClass,
        Deprecated,
    }

    @Parameter(0) public Using using;

    @Parameters(name = "{0}")
    public static List<Using> data() {
        return Arrays.asList(Using.values());
    }

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

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    private Object asHostType(TruffleLanguage.Env env, Class<?> c) {
        if (using == Using.HostClass) {
            return env.asGuestValue(c);
        } else {
            return env.asHostSymbol(c);
        }
    }

    private static Object verifyHostAdapterClass(TruffleLanguage.Env env, Object hostAdapterClass) {
        assertTrue(env.isHostObject(hostAdapterClass));
        assertTrue(env.isHostSymbol(hostAdapterClass));
        assertTrue(INTEROP.isMetaObject(hostAdapterClass));
        assertTrue(INTEROP.isInstantiable(hostAdapterClass));
        return hostAdapterClass;
    }

    @SuppressWarnings("deprecation")
    Object createHostAdapterClass(TruffleLanguage.Env env, Class<?>[] classes) {
        if (using == Using.Deprecated) {
            return verifyHostAdapterClass(env, env.createHostAdapterClass(classes));
        }
        Object[] hostTypes = Arrays.stream(classes).map(c -> asHostType(env, c)).toArray();
        return verifyHostAdapterClass(env, env.createHostAdapter(hostTypes));
    }

    @SuppressWarnings("deprecation")
    Object createHostAdapterClassWithClassOverrides(TruffleLanguage.Env env, Class<?>[] classes, Object classOverrides) {
        if (using == Using.Deprecated) {
            return verifyHostAdapterClass(env, env.createHostAdapterClassWithStaticOverrides(classes, classOverrides));
        }
        Object[] hostTypes = Arrays.stream(classes).map(c -> asHostType(env, c)).toArray();
        return verifyHostAdapterClass(env, env.createHostAdapterWithClassOverrides(hostTypes, classOverrides));
    }

    private static Object instantianteHostAdapter(TruffleLanguage.Env env, Object adapter, Object... arguments) throws InteropException {
        Object instance = INTEROP.instantiate(adapter, arguments);
        assertTrue(INTEROP.isMetaInstance(adapter, instance));
        assertTrue(env.isHostObject(instance));
        return instance;
    }

    @Test
    public void testCreateHostAdapterFromInterface() throws InteropException {
        try (TestContext c = new TestContext((b) -> b.allowHostAccess(explicitHostAccessAllowImplementations(Callable.class)))) {
            TruffleLanguage.Env env = c.env;
            Object adapter = createHostAdapterClass(env, new Class<?>[]{Callable.class});
            Object instance = instantianteHostAdapter(env, adapter, env.asGuestValue(ProxyObject.fromMap(Collections.singletonMap("call", (ProxyExecutable) (args) -> 42))));
            assertEquals(42, INTEROP.invokeMember(instance, "call"));

            assertTrue(INTEROP.isMetaInstance(env.asHostSymbol(Callable.class), instance));
        }
    }

    @Test
    public void testCreateHostAdapterFromClass() throws InteropException {
        try (TestContext c = new TestContext((b) -> b.allowHostAccess(explicitHostAccessAllowImplementations(Extensible.class)))) {
            TruffleLanguage.Env env = c.env;
            Object adapter = createHostAdapterClass(env, new Class<?>[]{Extensible.class});
            Object instance1 = instantianteHostAdapter(env, adapter, env.asGuestValue(ProxyObject.fromMap(Collections.singletonMap("abstractMethod", (ProxyExecutable) (args) -> "override"))));
            assertEquals("override", INTEROP.invokeMember(instance1, "abstractMethod"));
            assertEquals("base", INTEROP.invokeMember(instance1, "baseMethod"));

            Object instance2 = instantianteHostAdapter(env, adapter, env.asGuestValue(ProxyObject.fromMap(Collections.singletonMap("baseMethod", (ProxyExecutable) (args) -> "override"))));
            assertEquals("override", INTEROP.invokeMember(instance2, "baseMethod"));

            assertFails(() -> {
                return INTEROP.invokeMember(instance2, "abstractMethod");
            }, AbstractTruffleException.class, e -> assertTrue(e.toString(), env.isHostException(e)));

            assertTrue(INTEROP.isMetaInstance(env.asHostSymbol(Extensible.class), instance1));
            assertTrue(INTEROP.isMetaInstance(env.asHostSymbol(Extensible.class), instance2));
        }
    }

    @Test
    public void testCreateHostAdapterFromClassAndInterfaces() throws InteropException {
        Class<?>[] supertypes = new Class<?>[]{Extensible.class, Callable.class, Interface.class};
        try (TestContext c = new TestContext((b) -> b.allowHostAccess(explicitHostAccessAllowImplementations(supertypes)))) {
            TruffleLanguage.Env env = c.env;
            Object adapter = createHostAdapterClass(env, supertypes);
            Map<String, Object> impl = new HashMap<>();
            impl.put("abstractMethod", (ProxyExecutable) (args) -> "abstractMethodImpl");
            impl.put("baseMethod", (ProxyExecutable) (args) -> "baseMethodImpl");
            impl.put("call", (ProxyExecutable) (args) -> "callImpl");
            impl.put("defaultMethod", (ProxyExecutable) (args) -> "defaultMethodImpl");
            Object instance = instantianteHostAdapter(env, adapter, env.asGuestValue(ProxyObject.fromMap(impl)));

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

            for (Class<?> supertype : supertypes) {
                assertTrue(INTEROP.isMetaInstance(env.asHostSymbol(supertype), instance));
            }
        }
    }

    @Test
    public void testCreateHostAdapterFromClassWithConstructorParams() throws InteropException {
        try (TestContext c = new TestContext((b) -> b.allowHostAccess(explicitHostAccessAllowImplementations(NonDefaultConstructor.class)))) {
            TruffleLanguage.Env env = c.env;
            Object adapter = createHostAdapterClass(env, new Class<?>[]{NonDefaultConstructor.class});
            Map<String, Object> impl = new HashMap<>();
            impl.put("abstractMethod", (ProxyExecutable) (args) -> "abstractMethodImpl");
            impl.put("finalMethod", (ProxyExecutable) (args) -> "finalMethodImpl");
            Object instance = instantianteHostAdapter(env, adapter, "concreteName", env.asGuestValue(ProxyObject.fromMap(impl)));
            assertEquals("abstractMethodImpl", INTEROP.invokeMember(instance, "abstractMethod"));
            assertEquals("final", INTEROP.invokeMember(instance, "finalMethod"));
            assertEquals("concreteName", INTEROP.readMember(instance, "name"));
        }
    }

    @Test
    public void testCreateHostAdapterImplementationsNotAllowed() {
        try (TestContext c = new TestContext((b) -> b.allowHostAccess(HostAccess.EXPLICIT))) {
            final String expectedMessage = "Implementation not allowed";
            TruffleLanguage.Env env = c.env;
            assertFails(() -> {
                createHostAdapterClass(env, new Class<?>[]{Interface.class});
            }, IllegalArgumentException.class, e -> assertThat(e.getMessage(), containsString(expectedMessage)));
            assertFails(() -> {
                createHostAdapterClass(env, new Class<?>[]{Interface.class, Callable.class});
            }, IllegalArgumentException.class, e -> assertThat(e.getMessage(), containsString(expectedMessage)));
            assertFails(() -> {
                createHostAdapterClass(env, new Class<?>[]{Extensible.class});
            }, IllegalArgumentException.class, e -> assertThat(e.getMessage(), containsString(expectedMessage)));
            assertFails(() -> {
                createHostAdapterClass(env, new Class<?>[]{Extensible.class, Interface.class, Callable.class});
            }, IllegalArgumentException.class, e -> assertThat(e.getMessage(), containsString(expectedMessage)));
        }
    }

    @Test
    public void testCreateHostAdapterIllegalArgument() {
        try (TestContext c = new TestContext((b) -> b.allowHostAccess(HostAccess.EXPLICIT))) {
            TruffleLanguage.Env env = c.env;
            assertFails(() -> {
                createHostAdapterClass(env, new Class<?>[]{});
            }, IllegalArgumentException.class, e -> assertThat(e.getMessage(), containsString("Expected at least one type")));

            assertFails(() -> {
                createHostAdapterClass(env, null);
            }, NullPointerException.class);
        }
    }

    @Test
    public void testCreateHostAdapterFinalClass() {
        try (TestContext c = new TestContext((b) -> b.allowHostAccess(HostAccess.ALL))) {
            TruffleLanguage.Env env = c.env;
            assertFails(() -> {
                createHostAdapterClass(env, new Class<?>[]{Class.class});
            }, IllegalArgumentException.class, e -> assertThat(e.getMessage(), containsString("final class")));
        }
    }

    @Test
    public void testCreateHostAdapterMultipleClasses() {
        try (TestContext c = new TestContext((b) -> b.allowHostAccess(HostAccess.ALL))) {
            TruffleLanguage.Env env = c.env;
            assertFails(() -> {
                createHostAdapterClass(env, new Class<?>[]{Extensible.class, NonDefaultConstructor.class});
            }, IllegalArgumentException.class, e -> assertThat(e.getMessage(), containsString("multiple classes")));
        }
    }

    @Test
    public void testCreateHostAdapterIllegalArgumentType() {
        Assume.assumeFalse(using == Using.Deprecated);
        try (TestContext c = new TestContext((b) -> b.allowHostAccess(HostAccess.EXPLICIT))) {
            final String expectedMessage = "Types must be host symbols or host classes";
            TruffleLanguage.Env env = c.env;
            // java.lang.Class is not an allowed argument type.
            assertFails(() -> {
                env.createHostAdapter(new Object[]{Interface.class});
            }, IllegalArgumentException.class, e -> assertThat(e.getMessage(), containsString(expectedMessage)));

            // Arbitrary HostObject.
            assertFails(() -> {
                env.createHostAdapter(new Object[]{env.asGuestValue(new Object())});
            }, IllegalArgumentException.class, e -> assertThat(e.getMessage(), containsString(expectedMessage)));
            // Arbitrary TruffleObject.
            assertFails(() -> {
                env.createHostAdapter(new Object[]{env.asGuestValue(ProxyObject.fromMap(Collections.emptyMap()))});
            }, IllegalArgumentException.class, e -> assertThat(e.getMessage(), containsString(expectedMessage)));

            // Interop primitive types are not allowed either.
            assertFails(() -> {
                env.createHostAdapter(new Object[]{"invalid"});
            }, IllegalArgumentException.class, e -> assertThat(e.getMessage(), containsString(expectedMessage)));
            assertFails(() -> {
                env.createHostAdapter(new Object[]{42});
            }, IllegalArgumentException.class, e -> assertThat(e.getMessage(), containsString(expectedMessage)));
        }
    }

    @Test
    public void testCreateHostAdapterThis() throws InteropException {
        Class<?>[] supertypes = new Class<?>[]{Extensible.class, Interface.class};
        try (TestContext c = new TestContext((b) -> b.allowHostAccess(explicitHostAccessAllowImplementations(supertypes)))) {
            TruffleLanguage.Env env = c.env;
            Object adapter = createHostAdapterClass(env, supertypes);
            Map<String, Object> impl = new HashMap<>();
            impl.put("abstractMethod", (ProxyExecutable) (args) -> "abstractMethodImpl");
            impl.put("baseMethod", (ProxyExecutable) (args) -> "baseMethodImpl");
            impl.put("defaultMethod", (ProxyExecutable) (args) -> "defaultMethodImpl");
            Object guestObject = env.asGuestValue(ProxyObject.fromMap(impl));
            Object instance = instantianteHostAdapter(env, adapter, guestObject);

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
            Object adapter = createHostAdapterClass(env, supertypes);
            Map<String, Object> impl = new HashMap<>();
            impl.put("abstractMethod", (ProxyExecutable) (args) -> "abstractMethodImpl");
            impl.put("baseMethod", (ProxyExecutable) (args) -> "baseMethodImpl");
            impl.put("defaultMethod", (ProxyExecutable) (args) -> "defaultMethodImpl");
            Object guestObject = env.asGuestValue(ProxyObject.fromMap(impl));
            Object instance = instantianteHostAdapter(env, adapter, guestObject);

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
            Object adapterClass1 = createHostAdapterClassWithClassOverrides(env, supertypes, guestObject1);
            Object parent = instantianteHostAdapter(env, adapterClass1);

            assertEquals("abstractMethodImpl1", INTEROP.invokeMember(parent, "abstractMethod"));
            assertEquals("baseMethodImpl1", INTEROP.invokeMember(parent, "baseMethod"));
            assertEquals("defaultMethodImpl1", INTEROP.invokeMember(parent, "defaultMethod"));

            Map<String, Object> impl2 = new HashMap<>();
            impl2.put("abstractMethod", (ProxyExecutable) (args) -> "abstractMethodImpl2");
            impl2.put("baseMethod", (ProxyExecutable) (args) -> "baseMethodImpl2");
            impl2.put("defaultMethod", (ProxyExecutable) (args) -> "defaultMethodImpl2");
            Object guestObject2 = env.asGuestValue(ProxyObject.fromMap(impl2));
            Object adapterClass2 = createHostAdapterClass(env, new Class<?>[]{Interface.class, (Class<?>) env.asHostObject(adapterClass1)});
            Object instance = instantianteHostAdapter(env, adapterClass2, guestObject2);

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
            Object adapterClass1 = createHostAdapterClass(env, supertypes);
            Object parent = instantianteHostAdapter(env, adapterClass1, guestObject1);

            assertEquals("abstractMethodImpl1", INTEROP.invokeMember(parent, "abstractMethod"));
            assertEquals("baseMethodImpl1", INTEROP.invokeMember(parent, "baseMethod"));
            assertEquals("defaultMethodImpl1", INTEROP.invokeMember(parent, "defaultMethod"));

            Map<String, Object> impl2 = new HashMap<>();
            impl2.put("abstractMethod", (ProxyExecutable) (args) -> "abstractMethodImpl2");
            impl2.put("baseMethod", (ProxyExecutable) (args) -> "baseMethodImpl2");
            impl2.put("defaultMethod", (ProxyExecutable) (args) -> "defaultMethodImpl2");
            Object guestObject2 = env.asGuestValue(ProxyObject.fromMap(impl2));
            Object adapterClass2 = createHostAdapterClass(env, new Class<?>[]{Interface.class, (Class<?>) env.asHostObject(adapterClass1)});
            Object instance = instantianteHostAdapter(env, adapterClass2, guestObject1, guestObject2);

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
