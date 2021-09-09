/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.word.LocationIdentity;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.ContextLocal;
import com.oracle.truffle.api.ContextThreadLocal;
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

    static final String EXCLUSIVE_LANGUAGE = "ContextLookupCompilationTestExclusive";
    static final String SHARED_LANGUAGE = "ContextLookupCompilationTestShared";

    private static final Field LANGUAGE_CONTEXT_MAGIC_FIELD = lookupField(LanguageContext.class, "magicNumber");
    private static final Field CONTEXT_LOCAL_MAGIC_FIELD = lookupField(ContextLocalValue.class, "magicNumber");

    private static Engine createEngine(Engine.Builder engineBuilder) {
        return engineBuilder.allowExperimentalOptions(true).option("engine.CompileImmediately", "false").build();
    }

    private static Context createContext(Engine engine) {
        return enter(Context.newBuilder().engine(engine).build());
    }

    private static Context createContext(Context.Builder contextBuilder) {
        return enter(contextBuilder.allowExperimentalOptions(true).option("engine.CompileImmediately", "false").build());
    }

    private static Context enter(Context context) {
        context.initialize(EXCLUSIVE_LANGUAGE);
        context.initialize(SHARED_LANGUAGE);
        context.enter();
        return context;
    }

    @Test
    public void testContextLocalRead() {
        Engine engine = Engine.create();
        createContext(engine);
        Shared language = Shared.get();

        assertCompiling(createContextLocalRead(language, 50));
        Assert.assertEquals("Invalid number of magic number reads.", 1,
                        countMagicFieldReads(lastCompiledGraph, CONTEXT_LOCAL_MAGIC_FIELD));

        // second context still folds to a single read.
        Context c1 = createContext(engine);
        assertCompiling(createContextLocalRead(language, 50));
        Assert.assertEquals("Invalid number of magic number reads.", 1,
                        countMagicFieldReads(lastCompiledGraph, CONTEXT_LOCAL_MAGIC_FIELD));
        touchOnThread(c1);

        assertCompiling(createContextLocalRead(language, 50));
        Assert.assertEquals("Invalid number of magic number reads.", 1,
                        countMagicFieldReads(lastCompiledGraph, CONTEXT_LOCAL_MAGIC_FIELD));
    }

    private static RootNode createContextLocalRead(Shared language, int lookups) {
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
        Context c = createContext(engine);
        Shared language = Shared.get();

        assertCompiling(createContextThreadLocalRead(language, 50));
        Assert.assertEquals("Invalid number of magic number reads.", 1,
                        countMagicFieldReads(lastCompiledGraph, CONTEXT_LOCAL_MAGIC_FIELD));

        // second context still folds to a single read.
        createContext(engine);
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

    private static RootNode createContextThreadLocalRead(Shared language, int lookups) {
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

        context = createContext(Context.newBuilder());
        touchOnThread(context);
        assertLookupsNoSharing();
        context.leave();
        context.close();

        context = createContext(Context.newBuilder());
        touchOnThread(context);
        assertLookupsNoSharing();
        context.leave();
        context.close();
    }

    @Test
    public void testTwoContextMultiThreading() {
        Context context;

        Engine engine = createEngine(Engine.newBuilder());

        context = createContext(engine);
        touchOnThread(context);
        assertLookupsSharedMultipleThreads(false);
        context.leave();
        context.close();

        context = createContext(engine);
        touchOnThread(context);
        assertLookupsSharedMultipleThreads(true);
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

        context = createContext(Context.newBuilder());
        assertCompiling(createAssertConstantFromRef());
        assertLookupsNoSharing();

        TruffleContext innerContext = Shared.getCurrentContext().env.newContextBuilder().build();
        Object prev = innerContext.enter(null);
        try {
            Context.getCurrent().initialize(EXCLUSIVE_LANGUAGE);
            Context.getCurrent().initialize(SHARED_LANGUAGE);
            assertLookupsInnerContext();
        } finally {
            innerContext.leave(null, prev);
        }
        assertLookupsInnerContext();
        context.leave();
        context.close();
    }

    @Test
    public void testRefTwoConsecutiveContexts() {
        Context context;

        context = createContext(Context.newBuilder());
        assertCompiling(createAssertConstantFromRef());
        assertLookupsNoSharing();

        context.leave();
        context.close();

        context = createContext(Context.newBuilder());
        assertCompiling(createAssertConstantFromRef());
        assertLookupsNoSharing();
        context.leave();
        context.close();
    }

    private void assertLookupsNoSharing() {
        assertCompiling(createAssertConstantContextFromLookup(Exclusive.get(), Exclusive.get()));
        assertCompiling(createAssertConstantContextFromLookup(Shared.get(), Exclusive.get()));
        assertCompiling(createAssertConstantContextFromLookup(Exclusive.get(), Shared.get()));
        assertCompiling(createAssertConstantContextFromLookup(Shared.get(), Shared.get()));
        assertCompiling(createAssertConstantContextFromLookup(null, Exclusive.get()));
        assertCompiling(createAssertConstantContextFromLookup(null, Shared.get()));

        assertCompiling(createAssertConstantLanguageFromLookup(Exclusive.get(), Exclusive.get()));
        assertCompiling(createAssertConstantLanguageFromLookup(Shared.get(), Exclusive.get()));
        assertCompiling(createAssertConstantLanguageFromLookup(Exclusive.get(), Shared.get()));
        assertCompiling(createAssertConstantLanguageFromLookup(Shared.get(), Shared.get()));
        assertCompiling(createAssertConstantLanguageFromLookup(null, Exclusive.get()));
        assertCompiling(createAssertConstantLanguageFromLookup(null, Shared.get()));

        assertMagicNumberReads(0, Exclusive.get(), Exclusive.get());
        assertMagicNumberReads(0, Exclusive.get(), Shared.get());
        assertMagicNumberReads(0, Shared.get(), Exclusive.get());
        assertMagicNumberReads(0, Shared.get(), Shared.get());
        assertMagicNumberReads(0, null, Exclusive.get());
        assertMagicNumberReads(0, null, Shared.get());
    }

    @Test
    public void testRefTwoContextsAtTheSameTime() {
        Context context1 = createContext(Context.newBuilder());
        assertCompiling(createAssertConstantFromRef());
        assertLookupsNoSharing();
        context1.leave();

        Context context2 = createContext(Context.newBuilder());
        assertCompiling(createAssertConstantFromRef());
        assertLookupsNoSharing();
        context2.leave();

        context1.close();
        context2.close();
    }

    @Test
    public void testRefTwoContextsWithSharedEngine() {
        Engine engine = createEngine(Engine.newBuilder());
        Context context1 = createContext(engine);
        // context must not be constant
        assertBailout(createAssertConstantFromRef());
        assertLookupsSharedEngine(false);

        OptimizedCallTarget target = assertCompiling(createGetFromRef());
        assertTrue("is valid", target.isValid());
        target.call();
        assertTrue("and keeps valid", target.isValid());
        context1.leave();

        Context context2 = createContext(engine);
        assertTrue("still valid in second Context", target.isValid());
        assertLookupsSharedEngine(true);
        context2.leave();

        context1.close();
        context2.close();
        engine.close();
    }

    private void assertLookupsInnerContext() {
        /*
         * We currently have some optimizations disabled with inner contexts.
         */
        assertCompiling(createAssertConstantContextFromLookup(Exclusive.get(), Exclusive.get()));
        assertBailout(createAssertConstantContextFromLookup(Exclusive.get(), Shared.get()));
        assertBailout(createAssertConstantContextFromLookup(Shared.get(), Exclusive.get()));
        assertBailout(createAssertConstantContextFromLookup(Shared.get(), Shared.get()));
        assertBailout(createAssertConstantContextFromLookup(null, Exclusive.get()));
        assertBailout(createAssertConstantContextFromLookup(null, Shared.get()));

        assertCompiling(createAssertConstantLanguageFromLookup(Exclusive.get(), Exclusive.get()));
        assertCompiling(createAssertConstantLanguageFromLookup(Exclusive.get(), Shared.get()));
        assertBailout(createAssertConstantLanguageFromLookup(Shared.get(), Exclusive.get()));
        assertBailout(createAssertConstantLanguageFromLookup(null, Exclusive.get()));
        assertCompiling(createAssertConstantLanguageFromLookup(Shared.get(), Shared.get()));
        assertCompiling(createAssertConstantLanguageFromLookup(null, Shared.get()));

        assertMagicNumberReads(0, Exclusive.get(), Exclusive.get());
        assertMagicNumberReads(1, Exclusive.get(), Shared.get());
        assertMagicNumberReads(1, Shared.get(), Exclusive.get());
        assertMagicNumberReads(1, Shared.get(), Shared.get());
        assertMagicNumberReads(1, null, Exclusive.get());
        assertMagicNumberReads(1, null, Shared.get());

        assertMagicNumberReadsFromUnused(0, Exclusive.get(), Exclusive.get());
        assertMagicNumberReadsFromUnused(0, Exclusive.get(), Shared.get());
        assertMagicNumberReadsFromUnused(0, Shared.get(), Exclusive.get());
        assertMagicNumberReadsFromUnused(0, Shared.get(), Shared.get());
        assertMagicNumberReadsFromUnused(0, null, Exclusive.get());
        assertMagicNumberReadsFromUnused(0, null, Shared.get());
    }

    private void assertLookupsSharedEngine(boolean secondContext) {
        assertCompiling(createAssertConstantContextFromLookup(Exclusive.get(), Exclusive.get()));
        assertBailout(createAssertConstantContextFromLookup(Exclusive.get(), Shared.get()));
        if (secondContext) {
            assertBailout(createAssertConstantContextFromLookup(Shared.get(), Exclusive.get()));
            assertBailout(createAssertConstantContextFromLookup(null, Exclusive.get()));
        } else {
            assertCompiling(createAssertConstantContextFromLookup(Shared.get(), Exclusive.get()));
            assertCompiling(createAssertConstantContextFromLookup(null, Exclusive.get()));
        }
        assertBailout(createAssertConstantContextFromLookup(Shared.get(), Shared.get()));
        assertBailout(createAssertConstantContextFromLookup(null, Shared.get()));

        assertCompiling(createAssertConstantLanguageFromLookup(Exclusive.get(), Exclusive.get()));
        assertCompiling(createAssertConstantLanguageFromLookup(Exclusive.get(), Shared.get()));
        if (secondContext) {
            assertBailout(createAssertConstantLanguageFromLookup(Shared.get(), Exclusive.get()));
            assertBailout(createAssertConstantLanguageFromLookup(null, Exclusive.get()));
        } else {
            assertCompiling(createAssertConstantLanguageFromLookup(Shared.get(), Exclusive.get()));
            assertCompiling(createAssertConstantLanguageFromLookup(null, Exclusive.get()));
        }
        assertCompiling(createAssertConstantLanguageFromLookup(Shared.get(), Shared.get()));
        assertCompiling(createAssertConstantLanguageFromLookup(null, Shared.get()));

        assertMagicNumberReads(0, Exclusive.get(), Exclusive.get());
        assertMagicNumberReads(1, Exclusive.get(), Shared.get());
        if (secondContext) {
            assertMagicNumberReads(1, Shared.get(), Exclusive.get());
            assertMagicNumberReads(1, null, Exclusive.get());
        } else {
            assertMagicNumberReads(0, Shared.get(), Exclusive.get());
            assertMagicNumberReads(0, null, Exclusive.get());
        }
        assertMagicNumberReads(1, Shared.get(), Shared.get());
        assertMagicNumberReads(1, null, Shared.get());

        assertMagicNumberReadsFromUnused(0, Exclusive.get(), Exclusive.get());
        assertMagicNumberReadsFromUnused(0, Exclusive.get(), Shared.get());
        assertMagicNumberReadsFromUnused(0, Shared.get(), Exclusive.get());
        assertMagicNumberReadsFromUnused(0, Shared.get(), Shared.get());
        assertMagicNumberReadsFromUnused(0, null, Exclusive.get());
        assertMagicNumberReadsFromUnused(0, null, Shared.get());
    }

    private void assertLookupsSharedMultipleThreads(boolean secondContext) {
        assertCompiling(createAssertConstantContextFromLookup(Exclusive.get(), Exclusive.get()));
        assertBailout(createAssertConstantContextFromLookup(Exclusive.get(), Shared.get()));
        if (secondContext) {
            assertBailout(createAssertConstantContextFromLookup(Shared.get(), Exclusive.get()));
            assertBailout(createAssertConstantContextFromLookup(null, Exclusive.get()));
        } else {
            assertCompiling(createAssertConstantContextFromLookup(Shared.get(), Exclusive.get()));
            assertCompiling(createAssertConstantContextFromLookup(null, Exclusive.get()));
        }
        assertBailout(createAssertConstantContextFromLookup(Shared.get(), Shared.get()));
        assertBailout(createAssertConstantContextFromLookup(null, Shared.get()));

        assertCompiling(createAssertConstantLanguageFromLookup(Exclusive.get(), Exclusive.get()));
        assertCompiling(createAssertConstantLanguageFromLookup(Exclusive.get(), Shared.get()));
        if (secondContext) {
            assertBailout(createAssertConstantLanguageFromLookup(Shared.get(), Exclusive.get()));
            assertBailout(createAssertConstantLanguageFromLookup(null, Exclusive.get()));
        } else {
            assertCompiling(createAssertConstantLanguageFromLookup(Shared.get(), Exclusive.get()));
            assertCompiling(createAssertConstantLanguageFromLookup(null, Exclusive.get()));
        }
        assertCompiling(createAssertConstantLanguageFromLookup(Shared.get(), Shared.get()));
        assertCompiling(createAssertConstantLanguageFromLookup(null, Shared.get()));

        assertMagicNumberReads(0, Exclusive.get(), Exclusive.get());
        assertMagicNumberReads(1, Exclusive.get(), Shared.get());
        if (secondContext) {
            assertMagicNumberReads(1, Shared.get(), Exclusive.get());
            assertMagicNumberReads(1, null, Exclusive.get());
        } else {
            assertMagicNumberReads(0, Shared.get(), Exclusive.get());
            assertMagicNumberReads(0, null, Exclusive.get());
        }

        assertMagicNumberReadsFromUnused(0, Exclusive.get(), Exclusive.get());
        assertMagicNumberReadsFromUnused(0, Exclusive.get(), Shared.get());
        assertMagicNumberReadsFromUnused(0, Shared.get(), Exclusive.get());
        assertMagicNumberReadsFromUnused(0, Shared.get(), Shared.get());
        assertMagicNumberReadsFromUnused(0, null, Exclusive.get());
        assertMagicNumberReadsFromUnused(0, null, Shared.get());
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

        context = createContext(Context.newBuilder());
        assertCompiling(createAssertConstantFromStatic());
        assertLookupsNoSharing();
        context.leave();
        context.close();

        context = createContext(Context.newBuilder());
        assertCompiling(createAssertConstantFromStatic());
        assertLookupsNoSharing();
        context.leave();
        context.close();
    }

    @Test
    public void testStaticTwoContextsAtTheSameTime() {
        Context context1 = createContext(Context.newBuilder());
        assertCompiling(createAssertConstantFromStatic());
        assertLookupsNoSharing();
        context1.leave();

        Context context2 = createContext(Context.newBuilder());
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
            @CompilationFinal ContextReference<LanguageContext> ref;

            @Override
            @ExplodeLoop
            @SuppressWarnings({"unused", "unchecked"})
            public Object execute(VirtualFrame frame) {
                if (ref == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    ref = lookupContextReference(accessLanguage.getClass());
                }
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
        Context context1 = createContext(engine);
        // context must not be constant
        assertBailout(createAssertConstantFromStatic());
        assertLookupsSharedEngine(false);

        OptimizedCallTarget target = assertCompiling(createGetFromStatic());
        assertTrue("is valid", target.isValid());
        target.call();
        assertTrue("and keeps valid", target.isValid());
        context1.leave();

        Context context2 = createContext(engine);
        assertTrue("still valid in second Context", target.isValid());
        assertLookupsSharedEngine(true);
        context2.leave();
        context1.close();
        context2.close();
        engine.close();
    }

    private static RootNode createAssertConstantContextFromLookup(TruffleLanguage<?> sourceLanguage, TruffleLanguage<?> accessLanguage) {
        RootNode root = new RootNode(sourceLanguage) {
            @CompilationFinal ContextReference<?> ref;

            @SuppressWarnings("unchecked")
            @Override
            public Object execute(VirtualFrame frame) {
                if (ref == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    ref = lookupContextReference(accessLanguage.getClass());
                }
                Object ctx = ref.get(this);
                CompilerAsserts.partialEvaluationConstant(ctx);
                return ctx;
            }
        };
        return root;
    }

    private static RootNode createContextFromLookup(TruffleLanguage<?> sourceLanguage, TruffleLanguage<LanguageContext> accessLanguage, int lookups) {
        RootNode root = new RootNode(sourceLanguage) {
            @CompilationFinal ContextReference<LanguageContext> ref;

            @SuppressWarnings("unchecked")
            @Override
            @ExplodeLoop
            public Object execute(VirtualFrame frame) {
                if (ref == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    ref = lookupContextReference(accessLanguage.getClass());
                }
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
            @CompilationFinal LanguageReference<?> ref;

            @SuppressWarnings("unchecked")
            @Override
            public Object execute(VirtualFrame frame) {
                if (ref == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    ref = lookupLanguageReference(accessLanguage.getClass());
                }
                Object ctx = ref.get(this);
                CompilerAsserts.partialEvaluationConstant(ctx);
                return ctx;
            }
        };
        return root;
    }

    private static RootNode createAssertConstantFromRef() {
        RootNode root = new RootNode(null) {
            final ContextReference<LanguageContext> ref = ContextReference.create(Shared.class);

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
    private static final ContextReference<?> SHARED_CONTEXT = ContextReference.create(Shared.class);

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

    private static RootNode createGetFromRef() {
        RootNode root = new RootNode(null) {
            final ContextReference<LanguageContext> ref = ContextReference.create(Shared.class);

            @Override
            public Object execute(VirtualFrame frame) {
                return ref.get(this);
            }
        };
        return root;
    }

    private static RootNode createGetFromStatic() {
        RootNode root = new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                Object ctx = Exclusive.getCurrentContext();
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

    @Registration(id = EXCLUSIVE_LANGUAGE, name = EXCLUSIVE_LANGUAGE, contextPolicy = ContextPolicy.EXCLUSIVE)
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

    @Registration(id = SHARED_LANGUAGE, name = SHARED_LANGUAGE, contextPolicy = ContextPolicy.SHARED)
    public static class Shared extends TruffleLanguage<LanguageContext> {

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
            return getCurrentContext(Shared.class);
        }

        public static Shared get() {
            return getCurrentLanguage(Shared.class);
        }

    }

}
