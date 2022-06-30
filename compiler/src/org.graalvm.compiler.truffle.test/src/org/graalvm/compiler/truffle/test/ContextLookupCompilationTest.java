/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import static org.junit.Assert.assertSame;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.word.LocationIdentity;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.ContextLocal;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.ResolvedJavaField;

// we suppress deprecation until the old APIs are gone
// we should not break compilation of old APIs in the meantime
@SuppressWarnings("deprecation")
public class ContextLookupCompilationTest extends PartialEvaluationTest {

    static final String EXCLUSIVE = "ContextLookupCompilationTestExclusive";
    static final String SHARED1 = "ContextLookupCompilationTestShared1";
    static final String SHARED2 = "ContextLookupCompilationTestShared2";

    private static final Field LANGUAGE_CONTEXT_MAGIC_FIELD = lookupField(LanguageContext.class, "magicNumber");
    private static final Field CONTEXT_LOCAL_MAGIC_FIELD = lookupField(ContextLocalValue.class, "magicNumber");

    private static Engine createEngine(Engine.Builder engineBuilder) {
        return engineBuilder.allowExperimentalOptions(true).option("engine.CompileImmediately", "false").build();
    }

    private static Context createContext(Engine engine, String... languages) {
        Context.Builder b = Context.newBuilder(languages);
        if (engine != null) {
            b.engine(engine);
        }
        Context c = b.build();
        c.enter();
        for (String lang : languages) {
            c.initialize(lang);
        }
        return c;
    }

    /*
     * This test verifies that JVMCI has all the features it needs for a GraalTruffleRuntime.
     */
    @Test
    public void testJVMCIIsLatest() {
        Assume.assumeTrue(Truffle.getRuntime() instanceof GraalTruffleRuntime);
        GraalTruffleRuntime runtime = (GraalTruffleRuntime) Truffle.getRuntime();
        assertTrue(runtime.isLatestJVMCI());
    }

    @Test
    public void testContextLocalRead() {
        Engine engine = Engine.create();
        createContext(engine, SHARED1);
        Shared1 language = Shared1.get();

        assertCompiling(createContextLocalRead(language, 50));
        Assert.assertEquals("Invalid number of magic number reads.", 1,
                        countMagicFieldReads(lastCompiledGraph, CONTEXT_LOCAL_MAGIC_FIELD));

        // second context still folds to a single read.
        Context c1 = createContext(engine, SHARED1);
        assertSame(language, Shared1.get());
        assertCompiling(createContextLocalRead(language, 50));
        Assert.assertEquals("Invalid number of magic number reads.", 1,
                        countMagicFieldReads(lastCompiledGraph, CONTEXT_LOCAL_MAGIC_FIELD));
        touchOnThread(c1);

        assertCompiling(createContextLocalRead(language, 50));
        Assert.assertEquals("Invalid number of magic number reads.", 1,
                        countMagicFieldReads(lastCompiledGraph, CONTEXT_LOCAL_MAGIC_FIELD));

        c1.close();
        engine.close();
    }

    private static RootNode createContextLocalRead(Shared1 language, int lookups) {
        RootNode root = new RootNode(language) {
            @SuppressWarnings("unchecked")
            @Override
            @ExplodeLoop
            public Object execute(VirtualFrame frame) {
                int sum = 0;
                for (int i = 0; i < lookups; i++) {
                    sum += language.local.get().magicNumber;
                }
                return sum;
            }

            @Override
            public String getName() {
                return "ContextLocalRead" + lookups;
            }

        };
        return root;
    }

    @Test
    public void testContextThreadLocalRead() throws Throwable {
        Engine engine = Engine.create();
        Context c = createContext(engine, SHARED1);
        Shared1 language = Shared1.get();

        assertCompiling(createContextThreadLocalRead(language, 50));
        Assert.assertEquals("Invalid number of magic number reads.", 1,
                        countMagicFieldReads(lastCompiledGraph, CONTEXT_LOCAL_MAGIC_FIELD));

        // second context still folds to a single read.
        Context c2 = createContext(engine, SHARED1);
        assertCompiling(createContextThreadLocalRead(language, 50));
        Assert.assertEquals("Invalid number of magic number reads.", 1,
                        countMagicFieldReads(lastCompiledGraph, CONTEXT_LOCAL_MAGIC_FIELD));

        submitOnThreads(new Runnable() {
            @Override
            public void run() {
                c.enter();
                assertCompiling(createContextThreadLocalRead(language, 50));
                Assert.assertEquals("Invalid number of magic number reads thread index ", 1,
                                countMagicFieldReads(lastCompiledGraph, CONTEXT_LOCAL_MAGIC_FIELD));
                c.leave();
            }
        }, 10);

        c2.close();
        c.close();
        engine.close();

    }

    private static void submitOnThreads(Runnable run, int numberOfThreads) throws Throwable {
        ExecutorService service = Executors.newFixedThreadPool(numberOfThreads);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            futures.add(service.submit(run));
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                throw e.getCause();
            }
        }

        service.shutdown();
        service.awaitTermination(10000, TimeUnit.MILLISECONDS);
    }

    private static RootNode createContextThreadLocalRead(Shared1 language, int lookups) {
        RootNode root = new RootNode(language) {
            @SuppressWarnings("unchecked")
            @Override
            @ExplodeLoop
            public Object execute(VirtualFrame frame) {
                int sum = 0;
                for (int i = 0; i < lookups; i++) {
                    sum += language.threadLocal.get().magicNumber;
                }
                return sum;
            }
        };
        return root;
    }

    @Test
    public void testNoSharingContextMultiThreading() {
        Context context;

        context = createContext(null, EXCLUSIVE, SHARED1);
        touchOnThread(context);
        assertLookupsNoSharing();
        context.leave();
        context.close();

        context = createContext(null, EXCLUSIVE, SHARED1);
        touchOnThread(context);
        assertLookupsNoSharing();
        context.leave();
        context.close();
    }

    @Test
    public void testTwoContextMultiThreading() {
        Context context;

        Engine engine = createEngine(Engine.newBuilder());

        context = createContext(engine, EXCLUSIVE, SHARED1);
        touchOnThread(context);
        assertLookupsNoSharing();
        context.leave();
        context.close();

        context = createContext(engine, EXCLUSIVE, SHARED1);
        touchOnThread(context);
        assertLookupsNoSharing();
        context.leave();
        context.close();
    }

    /*
     * This triggers single threading assumptions to invalidate.
     */
    private static void touchOnThread(Context context) {
        Thread thread = new Thread((Runnable) () -> {
            context.enter();
            context.leave();
        }, "test");
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
        }
    }

    @Test
    public void testInnerContexts() {
        Context context;

        context = createContext(null, EXCLUSIVE, SHARED1);
        assertCompiling(createAssertConstantFromRef());
        assertLookupsNoSharing();

        TruffleContext innerContext = Shared1.getCurrentContext().env.newContextBuilder().build();
        Object prev = innerContext.enter(null);
        try {
            Context.getCurrent().initialize(EXCLUSIVE);
            Context.getCurrent().initialize(SHARED1);
            assertLookupsNoSharing();
        } finally {
            innerContext.leave(null, prev);
            innerContext.close();
        }
        assertLookupsNoSharing();
        context.leave();
        context.close();
    }

    @Test
    public void testInnerContextsShared() {
        Context context;

        Engine engine = createEngine(Engine.newBuilder());

        context = createContext(engine, SHARED1, SHARED2);
        assertBailout(createAssertConstantFromRef());
        assertLookupsSharedLayer();

        TruffleContext innerContext = Shared1.getCurrentContext().env.newContextBuilder().build();
        Object prev = innerContext.enter(null);
        try {
            Context.getCurrent().initialize(SHARED1);
            Context.getCurrent().initialize(SHARED2);
            assertLookupsSharedLayer();
        } finally {
            innerContext.leave(null, prev);
            innerContext.close();
        }
        assertLookupsSharedLayer();
        context.leave();
        context.close();
    }

    @Test
    public void testRefTwoConsecutiveContexts() {
        Context context;

        context = createContext(null, EXCLUSIVE, SHARED1);
        assertCompiling(createAssertConstantFromRef());
        assertLookupsNoSharing();

        context.leave();
        context.close();

        context = createContext(null, EXCLUSIVE, SHARED1);
        assertCompiling(createAssertConstantFromRef());
        assertLookupsNoSharing();
        context.leave();
        context.close();
    }

    private void assertLookupsNoSharing() {
        assertCompiling(createAssertConstantContextFromLookup(Exclusive.get(), Exclusive.get()));
        assertCompiling(createAssertConstantContextFromLookup(Shared1.get(), Exclusive.get()));
        assertCompiling(createAssertConstantContextFromLookup(Exclusive.get(), Shared1.get()));
        assertCompiling(createAssertConstantContextFromLookup(Shared1.get(), Shared1.get()));
        assertCompiling(createAssertConstantContextFromLookup(null, Exclusive.get()));
        assertCompiling(createAssertConstantContextFromLookup(null, Shared1.get()));

        assertCompiling(createAssertConstantLanguageFromLookup(Exclusive.get(), Exclusive.get()));
        assertCompiling(createAssertConstantLanguageFromLookup(Shared1.get(), Exclusive.get()));
        assertCompiling(createAssertConstantLanguageFromLookup(Exclusive.get(), Shared1.get()));
        assertCompiling(createAssertConstantLanguageFromLookup(Shared1.get(), Shared1.get()));
        assertCompiling(createAssertConstantLanguageFromLookup(null, Exclusive.get()));
        assertCompiling(createAssertConstantLanguageFromLookup(null, Shared1.get()));

        assertMagicNumberReads(0, Exclusive.get(), Exclusive.get());
        assertMagicNumberReads(0, Exclusive.get(), Shared1.get());
        assertMagicNumberReads(0, Shared1.get(), Exclusive.get());
        assertMagicNumberReads(0, Shared1.get(), Shared1.get());
        assertMagicNumberReads(0, null, Exclusive.get());
        assertMagicNumberReads(0, null, Shared1.get());
    }

    @Test
    public void testRefTwoContextsAtTheSameTime() {
        Context context1 = createContext(null, SHARED1, EXCLUSIVE);
        assertCompiling(createAssertConstantFromRef());
        assertLookupsNoSharing();
        context1.leave();

        Context context2 = createContext(null, SHARED1, EXCLUSIVE);
        assertCompiling(createAssertConstantFromRef());
        assertLookupsNoSharing();
        context2.leave();

        context1.close();
        context2.close();
    }

    @Test
    public void testRefTwoContextsWithSharedEngine() {
        Engine engine = createEngine(Engine.newBuilder());
        Context context1 = createContext(engine, SHARED1, SHARED2);
        // context must not be constant
        assertBailout(createAssertConstantFromRef());
        assertLookupsSharedLayer();
        context1.leave();

        Context context2 = createContext(engine, SHARED1, SHARED2);
        assertLookupsSharedLayer();
        context2.leave();

        context1.close();
        context2.close();
        engine.close();
    }

    private void assertLookupsSharedLayer() {
        assertBailout(createAssertConstantContextFromLookup(Shared1.get(), Shared1.get()));
        assertBailout(createAssertConstantContextFromLookup(Shared1.get(), Shared2.get()));
        assertBailout(createAssertConstantContextFromLookup(null, Shared1.get()));

        assertCompiling(createAssertConstantLanguageFromLookup(Shared1.get(), Shared1.get()));
        assertCompiling(createAssertConstantLanguageFromLookup(Shared1.get(), Shared2.get()));
        assertCompiling(createAssertConstantLanguageFromLookup(null, Shared1.get()));

        assertMagicNumberReads(1, Shared1.get(), Shared1.get());
        assertMagicNumberReads(1, Shared1.get(), Shared2.get());
        assertMagicNumberReads(1, null, Shared1.get());

        assertMagicNumberReadsFromUnused(0, Shared1.get(), Shared1.get());
        assertMagicNumberReadsFromUnused(0, Shared1.get(), Shared2.get());
        assertMagicNumberReadsFromUnused(0, null, Shared1.get());
    }

    private void assertMagicNumberReads(int expected, TruffleLanguage<?> sourceLanguage, TruffleLanguage<LanguageContext> accessLanguage) {
        assertCompiling(createContextFromLookup(sourceLanguage, accessLanguage, 50));
        try {
            Assert.assertEquals("Invalid number of magic number reads.", expected, countMagicFieldReads(lastCompiledGraph, LANGUAGE_CONTEXT_MAGIC_FIELD));
        } catch (AssertionError e) {
            System.out.println("Failed " + lastCompiledGraph.name);
            throw e;
        }
    }

    private static int countMagicFieldReads(StructuredGraph graph, Field field) {
        int count = 0;
        for (ReadNode readNode : graph.getNodes().filter(ReadNode.class)) {
            LocationIdentity location = readNode.getLocationIdentity();
            if (location instanceof FieldLocationIdentity) {
                ResolvedJavaField locationField = ((FieldLocationIdentity) location).getField();
                if (locationField.getName().equals(locationField.getName()) && locationField.getDeclaringClass().toJavaName().equals(field.getDeclaringClass().getName())) {
                    count++;
                }
            }
        }
        return count;
    }

    private static Field lookupField(Class<?> clazz, String fieldName) {
        Field field;
        try {
            field = clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException | SecurityException e) {
            throw new AssertionError(e);
        }
        return field;
    }

    @Test
    public void testStaticTwoConsecutiveContexts() {
        Context context;

        context = createContext(null, EXCLUSIVE, SHARED1);
        assertCompiling(createAssertConstantFromStatic());
        assertLookupsNoSharing();
        context.leave();
        context.close();

        context = createContext(null, EXCLUSIVE, SHARED1);
        assertCompiling(createAssertConstantFromStatic());
        assertLookupsNoSharing();
        context.leave();
        context.close();
    }

    @Test
    public void testStaticTwoContextsAtTheSameTime() {
        Context context1 = createContext(null, EXCLUSIVE, SHARED1);
        assertCompiling(createAssertConstantFromStatic());
        assertLookupsNoSharing();
        context1.leave();

        Context context2 = createContext(null, EXCLUSIVE, SHARED1);
        assertCompiling(createAssertConstantFromStatic());
        assertLookupsNoSharing();
        context2.leave();

        context1.close();
        context2.close();
    }

    private void assertMagicNumberReadsFromUnused(int expected, TruffleLanguage<?> sourceLanguage, TruffleLanguage<LanguageContext> accessLanguage) {
        assertCompiling(createContextNotUsed(sourceLanguage, accessLanguage, 50));
        Assert.assertEquals("Invalid number of magic number reads.", expected, countMagicFieldReads(lastCompiledGraph, LANGUAGE_CONTEXT_MAGIC_FIELD));
    }

    private static RootNode createContextNotUsed(TruffleLanguage<?> sourceLanguage, TruffleLanguage<LanguageContext> accessLanguage, int lookups) {
        RootNode root = new RootNode(sourceLanguage) {
            @SuppressWarnings("unchecked") final ContextReference<LanguageContext> ref = ContextReference.create(accessLanguage.getClass());

            @Override
            @ExplodeLoop
            @SuppressWarnings({"unused", "unchecked"})
            public Object execute(VirtualFrame frame) {
                int sum = 0;
                for (int i = 0; i < lookups; i++) {
                    sum += ref.get(this).magicNumber;
                }

                // we are not actually using the sum to test context references to fold
                return 0;
            }
        };
        return root;
    }

    @Test
    public void testStaticTwoContextsWithSharedEngine() {
        Engine engine = createEngine(Engine.newBuilder());
        Context context1 = createContext(engine, SHARED1, SHARED2);
        // context must not be constant
        assertBailout(createAssertConstantFromStatic());
        assertLookupsSharedLayer();
        context1.leave();

        Context context2 = createContext(engine, SHARED1, SHARED2);
        assertLookupsSharedLayer();
        context2.leave();
        context1.close();
        context2.close();
        engine.close();
    }

    private static RootNode createAssertConstantContextFromLookup(TruffleLanguage<?> sourceLanguage, TruffleLanguage<?> accessLanguage) {
        RootNode root = new RootNode(sourceLanguage) {
            @SuppressWarnings("unchecked") final ContextReference<?> ref = ContextReference.create(accessLanguage.getClass());

            @Override
            public Object execute(VirtualFrame frame) {
                Object ctx = ref.get(this);
                CompilerAsserts.partialEvaluationConstant(ctx);
                return ctx;
            }
        };
        return root;
    }

    private static RootNode createContextFromLookup(TruffleLanguage<?> sourceLanguage, TruffleLanguage<LanguageContext> accessLanguage, int lookups) {
        RootNode root = new RootNode(sourceLanguage) {
            @SuppressWarnings("unchecked") final ContextReference<LanguageContext> ref = ContextReference.create(accessLanguage.getClass());

            @SuppressWarnings("unchecked")
            @Override
            @ExplodeLoop
            public Object execute(VirtualFrame frame) {
                int sum = 0;
                for (int i = 0; i < lookups; i++) {
                    LanguageContext ctx = ref.get(this);
                    sum += ctx.magicNumber;
                }
                return sum;
            }
        };
        return root;
    }

    private static RootNode createAssertConstantLanguageFromLookup(TruffleLanguage<?> sourceLanguage, TruffleLanguage<?> accessLanguage) {
        RootNode root = new RootNode(sourceLanguage) {
            @SuppressWarnings("unchecked") final LanguageReference<?> ref = LanguageReference.create(accessLanguage.getClass());

            @SuppressWarnings("unchecked")
            @Override
            public Object execute(VirtualFrame frame) {
                Object ctx = ref.get(this);
                CompilerAsserts.partialEvaluationConstant(ctx);
                return ctx;
            }
        };
        return root;
    }

    private static RootNode createAssertConstantFromRef() {
        RootNode root = new RootNode(null) {
            final ContextReference<LanguageContext> ref = ContextReference.create(Shared1.class);

            @Override
            public Object execute(VirtualFrame frame) {
                Object ctx = ref.get(this);
                CompilerAsserts.partialEvaluationConstant(ctx);
                return ctx;
            }
        };
        return root;
    }

    private static final ContextReference<?> EXCLUSIVE_CONTEXT = ContextReference.create(Exclusive.class);
    private static final ContextReference<?> SHARED_CONTEXT = ContextReference.create(Shared1.class);

    private static RootNode createAssertConstantFromStatic() {
        RootNode root = new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                Object ctx = EXCLUSIVE_CONTEXT.get(this);
                CompilerAsserts.partialEvaluationConstant(ctx);
                ctx = SHARED_CONTEXT.get(this);
                CompilerAsserts.partialEvaluationConstant(ctx);
                return ctx;
            }
        };
        return root;
    }

    private void assertBailout(RootNode node) {
        try {
            compileHelper("assertBailout", node, new Object[0]);
            throw new AssertionError("bailout expected");
        } catch (BailoutException e) {
            // thats expected.
        }
    }

    private OptimizedCallTarget assertCompiling(RootNode node) {
        try {
            return compileHelper("assertCompiling", node, new Object[0]);
        } catch (BailoutException e) {
            throw new AssertionError("bailout not expected", e);
        }
    }

    @Registration(id = EXCLUSIVE, name = EXCLUSIVE, contextPolicy = ContextPolicy.EXCLUSIVE)
    public static class Exclusive extends TruffleLanguage<LanguageContext> {

        @Override
        protected LanguageContext createContext(Env env) {
            return new LanguageContext(env, 42);
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        public static LanguageContext getCurrentContext() {
            return getCurrentContext(Exclusive.class);
        }

        public static TruffleLanguage<LanguageContext> get() {
            return getCurrentLanguage(Exclusive.class);
        }

    }

    static class LanguageContext {

        final Env env;
        final int magicNumber;

        LanguageContext(Env env, int number) {
            this.env = env;
            this.magicNumber = number;
        }

    }

    static final class ContextLocalValue {

        int magicNumber = 42;

    }

    @Registration(id = SHARED1, name = SHARED1, contextPolicy = ContextPolicy.SHARED)
    public static class Shared1 extends TruffleLanguage<LanguageContext> {

        final ContextLocal<ContextLocalValue> local = createContextLocal((e) -> new ContextLocalValue());
        final ContextThreadLocal<ContextLocalValue> threadLocal = createContextThreadLocal((e, t) -> new ContextLocalValue());

        @Override
        protected LanguageContext createContext(Env env) {
            return new LanguageContext(env, 42);
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        public static LanguageContext getCurrentContext() {
            return getCurrentContext(Shared1.class);
        }

        public static Shared1 get() {
            return getCurrentLanguage(Shared1.class);
        }

    }

    @Registration(id = SHARED2, name = SHARED2, contextPolicy = ContextPolicy.SHARED)
    public static class Shared2 extends TruffleLanguage<LanguageContext> {

        final ContextLocal<ContextLocalValue> local = createContextLocal((e) -> new ContextLocalValue());
        final ContextThreadLocal<ContextLocalValue> threadLocal = createContextThreadLocal((e, t) -> new ContextLocalValue());

        @Override
        protected LanguageContext createContext(Env env) {
            return new LanguageContext(env, 42);
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        public static LanguageContext getCurrentContext() {
            return getCurrentContext(Shared2.class);
        }

        public static Shared2 get() {
            return getCurrentLanguage(Shared2.class);
        }

    }

}
