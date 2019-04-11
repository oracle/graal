/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.SharedTruffleRuntimeOptions;
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.code.BailoutException;

public class ContextLookupCompilationTest extends PartialEvaluationTest {

    private static TruffleRuntimeOptions.TruffleRuntimeOptionsOverrideScope immediateCompilationScope;
    static final String EXCLUSIVE_LANGUAGE = "ContextLookupCompilationTestExclusive";
    static final String SHARED_LANGUAGE = "ContextLookupCompilationTestShared";

    @BeforeClass
    public static void classSetup() {
        immediateCompilationScope = TruffleRuntimeOptions.overrideOptions(SharedTruffleRuntimeOptions.TruffleCompileImmediately, false);
    }

    @AfterClass
    public static void tearDown() {
        immediateCompilationScope.close();
    }

    @Before
    public void setup() {
        // the static context tests rely on a fresh host VM
        // this method resets the host vm level assumption to its initial state
        // independent which tests ran before.
        resetSingleContextState();
    }

    private static Context enter(Context context) {
        context.initialize(EXCLUSIVE_LANGUAGE);
        context.initialize(SHARED_LANGUAGE);
        context.enter();
        return context;
    }

    @Test
    public void testInnerContexts() {
        Context context;

        context = enter(Context.create());
        assertCompiling(createAssertConstantFromRef());
        assertLookupsNoSharing();

        TruffleContext innerContext = Shared.getCurrentContext().newContextBuilder().build();
        Object prev = innerContext.enter();
        try {
            Context.getCurrent().initialize(EXCLUSIVE_LANGUAGE);
            Context.getCurrent().initialize(SHARED_LANGUAGE);
            assertLookupsInnerContext();
        } finally {
            innerContext.leave(prev);
        }
        assertLookupsInnerContext();
        context.leave();
        context.close();
    }

    @Test
    public void testRefTwoConsecutiveContexts() {
        Context context;

        context = enter(Context.create());
        assertCompiling(createAssertConstantFromRef());
        assertLookupsNoSharing();

        context.leave();
        context.close();

        context = enter(Context.create());
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
    }

    @Test
    public void testRefTwoContextsAtTheSameTime() {
        Context context1 = enter(Context.create());
        assertCompiling(createAssertConstantFromRef());
        assertLookupsNoSharing();
        context1.leave();

        Context context2 = enter(Context.create());
        assertCompiling(createAssertConstantFromRef());
        assertLookupsNoSharing();
        context2.leave();

        context1.close();
        context2.close();
    }

    @Test
    public void testRefTwoContextsWithSharedEngine() {
        Engine engine = Engine.create();
        Context context1 = enter(Context.newBuilder().engine(engine).build());
        // context must not be constant
        assertBailout(createAssertConstantFromRef());
        assertLookupsSharedEngine(false);

        OptimizedCallTarget target = assertCompiling(createGetFromRef());
        assertTrue("is valid", target.isValid());
        target.call();
        assertTrue("and keeps valid", target.isValid());
        context1.leave();

        Context context2 = enter(Context.newBuilder().engine(engine).build());
        assertTrue("still valid in second Context", target.isValid());
        assertLookupsSharedEngine(true);
        context2.leave();

        context1.close();
        context2.close();
        engine.close();
    }

    private void assertLookupsInnerContext() {
        /*
         * We currently have all optimizations disabled with inner contexts.
         */
        assertBailout(createAssertConstantContextFromLookup(Exclusive.get(), Exclusive.get()));
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
    }

    private void assertLookupsSharedEngine(boolean secondContext) {
        assertCompiling(createAssertConstantContextFromLookup(Exclusive.get(), Exclusive.get()));
        assertCompiling(createAssertConstantContextFromLookup(Exclusive.get(), Shared.get()));
        assertBailout(createAssertConstantContextFromLookup(Shared.get(), Exclusive.get()));
        assertBailout(createAssertConstantContextFromLookup(Shared.get(), Shared.get()));
        assertBailout(createAssertConstantContextFromLookup(null, Exclusive.get()));
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
    }

    @Test
    public void testStaticTwoConsecutiveContexts() {
        Context context;

        context = enter(Context.create());
        assertCompiling(createAssertConstantFromStatic());
        assertLookupsNoSharing();
        context.leave();
        context.close();

        context = enter(Context.create());
        assertCompiling(createAssertConstantFromStatic());
        assertLookupsNoSharing();
        context.leave();
        context.close();
    }

    @Test
    public void testStaticTwoContextsAtTheSameTime() {
        Context context1 = enter(Context.create());
        assertCompiling(createAssertConstantFromStatic());
        assertLookupsNoSharing();
        context1.leave();

        Context context2 = enter(Context.create());
        assertBailout(createAssertConstantFromStatic());
        assertLookupsNoSharing();
        context2.leave();

        context1.close();
        context2.close();
    }

    @Test
    public void testStaticTwoContextsWithSharedEngine() {
        Engine engine = Engine.create();
        Context context1 = enter(Context.newBuilder().engine(engine).build());
        // context must not be constant
        assertBailout(createAssertConstantFromStatic());
        assertLookupsSharedEngine(false);

        OptimizedCallTarget target = assertCompiling(createGetFromStatic());
        assertTrue("is valid", target.isValid());
        target.call();
        assertTrue("and keeps valid", target.isValid());
        context1.leave();

        Context context2 = enter(Context.newBuilder().engine(engine).build());
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
                Object ctx = ref.get();
                CompilerAsserts.partialEvaluationConstant(ctx);
                return ctx;
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
                Object ctx = ref.get();
                CompilerAsserts.partialEvaluationConstant(ctx);
                return ctx;
            }
        };
        return root;
    }

    private static RootNode createAssertConstantFromRef() {
        RootNode root = new RootNode(null) {
            final ContextReference<Env> ref = Shared.getCurrentContextReference();

            @Override
            public Object execute(VirtualFrame frame) {
                Object ctx = ref.get();
                CompilerAsserts.partialEvaluationConstant(ctx);
                return ctx;
            }
        };
        return root;
    }

    private static RootNode createAssertConstantFromStatic() {
        RootNode root = new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                Object ctx = Exclusive.getCurrentContext();
                CompilerAsserts.partialEvaluationConstant(ctx);
                ctx = Shared.getCurrentContext();
                CompilerAsserts.partialEvaluationConstant(ctx);
                return ctx;
            }
        };
        return root;
    }

    private static RootNode createGetFromRef() {
        RootNode root = new RootNode(null) {
            final ContextReference<Env> ref = Shared.getCurrentContextReference();

            @Override
            public Object execute(VirtualFrame frame) {
                return ref.get();
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

    private static void resetSingleContextState() {
        try {
            Class<?> c = Class.forName("com.oracle.truffle.polyglot.PolyglotContextImpl");
            java.lang.reflect.Method m = c.getDeclaredMethod("resetSingleContextState");
            m.setAccessible(true);
            m.invoke(null);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Registration(id = EXCLUSIVE_LANGUAGE, name = EXCLUSIVE_LANGUAGE, contextPolicy = ContextPolicy.EXCLUSIVE)
    public static class Exclusive extends TruffleLanguage<Env> {

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

        public static ContextReference<Env> getCurrentContextReference() {
            return getCurrentLanguage(Exclusive.class).getContextReference();
        }

        public static Env getCurrentContext() {
            return getCurrentContext(Exclusive.class);
        }

        public static TruffleLanguage<?> get() {
            return getCurrentLanguage(Exclusive.class);
        }

    }

    @Registration(id = SHARED_LANGUAGE, name = SHARED_LANGUAGE, contextPolicy = ContextPolicy.SHARED)
    public static class Shared extends TruffleLanguage<Env> {

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

        public static ContextReference<Env> getCurrentContextReference() {
            return getCurrentLanguage(Shared.class).getContextReference();
        }

        public static Env getCurrentContext() {
            return getCurrentContext(Shared.class);
        }

        public static TruffleLanguage<?> get() {
            return getCurrentLanguage(Shared.class);
        }

    }

}
