/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.ref.WeakReference;
import java.util.function.Supplier;

import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.ContextLocal;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.GCUtils;

public class LanguageContextFreedTest {

    private static final int COMPILATION_THRESHOLD = 10;

    @Test
    public void testLanguageContexFreedNoSharing() {
        doTest(() -> {
            return Context.newBuilder().allowAllAccess(true).allowExperimentalOptions(true).//
            option("engine.BackgroundCompilation", Boolean.FALSE.toString()).//
            option("engine.MultiTier", Boolean.FALSE.toString()).//
            option("engine.SingleTierCompilationThreshold", String.valueOf(COMPILATION_THRESHOLD)).//
            option("engine.CompileImmediately", Boolean.FALSE.toString()).build();
        });
    }

    @Test
    public void testLanguageContexFreedSharedEngine() {
        doTest(() -> {
            Engine engine = Engine.newBuilder().allowExperimentalOptions(true).//
            option("engine.BackgroundCompilation", Boolean.FALSE.toString()).//
            option("engine.MultiTier", Boolean.FALSE.toString()).//
            option("engine.SingleTierCompilationThreshold", String.valueOf(COMPILATION_THRESHOLD)).//
            option("engine.CompileImmediately", Boolean.FALSE.toString()).build();
            return Context.newBuilder().engine(engine).allowAllAccess(true).build();
        });
    }

    private static void doTest(Supplier<Context> contextFactory) {
        try (Context ctx = contextFactory.get()) {
            testRun(ctx, Exclusive.ID, Exclusive.ID);
        }
        try (Context ctx = contextFactory.get()) {
            testRun(ctx, Exclusive.ID, Shared.ID);
        }
        try (Context ctx = contextFactory.get()) {
            testRun(ctx, Shared.ID, Exclusive.ID);
        }
        try (Context ctx = contextFactory.get()) {
            testRun(ctx, Shared.ID, Shared.ID);
        }
    }

    private static void testRun(Context ctx, String sourceLanguage, String targetLanguage) {
        Source src = Source.create(sourceLanguage, targetLanguage);
        ctx.initialize(Exclusive.ID);
        ctx.initialize(Shared.ID);
        LanguageContext sourceContext;
        ContextLocalValue contextLocal;
        ContextLocalValue threadLocal;
        ctx.enter();
        try {
            sourceContext = Base.getAccessContext(sourceLanguage);
            contextLocal = sourceContext.language.contextLocal.get();
            threadLocal = sourceContext.language.threadLocal.get();
        } finally {
            ctx.leave();
        }

        for (int i = 0; i < COMPILATION_THRESHOLD; i++) {
            ctx.eval(src);
        }
        assertTrue(sourceContext.currentTarget.isValid());
        ctx.close();

        WeakReference<?> langContextRef = new WeakReference<>(sourceContext);
        WeakReference<?> contextLocalRef = new WeakReference<>(contextLocal);
        WeakReference<?> threadLocalRef = new WeakReference<>(threadLocal);

        sourceContext = null;
        contextLocal = null;
        threadLocal = null;

        GCUtils.assertGc("Language context should be freed when polyglot Context is closed.", langContextRef);
        GCUtils.assertGc("Context local should be freed when polyglot Context is closed.",
                        contextLocalRef);
        GCUtils.assertGc("Context thread local should be freed when polyglot Context is closed.",
                        threadLocalRef);
    }

    static final class LanguageContext {

        private final Base language;

        OptimizedCallTarget currentTarget;

        LanguageContext(Base language) {
            this.language = language;
        }

    }

    public abstract static class Base extends TruffleLanguage<LanguageContext> {

        final ContextLocal<ContextLocalValue> contextLocal = createContextLocal((e) -> new ContextLocalValue());
        final ContextThreadLocal<ContextLocalValue> threadLocal = createContextThreadLocal((e, t) -> new ContextLocalValue());

        @Override
        protected LanguageContext createContext(Env env) {
            return new LanguageContext(this);
        }

        protected abstract ContextReference<LanguageContext> getContextReference0();

        @Override
        protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
            OptimizedCallTarget target = (OptimizedCallTarget) new RootNode(this) {

                @SuppressWarnings("unchecked")
                @Override
                public Object execute(VirtualFrame frame) {
                    getContextReference0().get(this).currentTarget = (OptimizedCallTarget) getCallTarget();
                    return true;
                }
            }.getCallTarget();
            getContextReference0().get(null).currentTarget = target;

            assertEquals(COMPILATION_THRESHOLD, (int) target.getOptionValue(PolyglotCompilerOptions.SingleTierCompilationThreshold));
            return target;
        }

        private static LanguageContext getAccessContext(String id) {
            switch (id) {
                case Shared.ID:
                    return Shared.REFERENCE.get(null);
                case Exclusive.ID:
                    return Exclusive.REFERENCE.get(null);
                default:
                    throw new IllegalArgumentException(id);
            }
        }

    }

    @Registration(id = Exclusive.ID, name = Exclusive.ID, contextPolicy = ContextPolicy.EXCLUSIVE)
    public static class Exclusive extends Base {

        static final String ID = "LanguageContextFreedTestExclusive";

        @Override
        protected ContextReference<LanguageContext> getContextReference0() {
            return REFERENCE;
        }

        private static final ContextReference<LanguageContext> REFERENCE = ContextReference.create(Exclusive.class);

    }

    @Registration(id = Shared.ID, name = Shared.ID, contextPolicy = ContextPolicy.SHARED)
    public static class Shared extends Base {

        static final String ID = "LanguageContextFreedTestShared";

        @Override
        protected ContextReference<LanguageContext> getContextReference0() {
            return REFERENCE;
        }

        private static final ContextReference<LanguageContext> REFERENCE = ContextReference.create(Shared.class);

    }

    private static class ContextLocalValue {
    }

}
