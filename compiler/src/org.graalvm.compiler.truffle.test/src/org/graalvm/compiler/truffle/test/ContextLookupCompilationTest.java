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
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage.LanguageContext;

import jdk.vm.ci.code.BailoutException;

public class ContextLookupCompilationTest extends PartialEvaluationTest {

    @Before
    public void setup() {
        // the static context tests rely on a fresh host VM
        // this method resets the host vm level assumption to its initial state
        // independent which tests ran before.
        resetSingleContextState();
    }

    private static Context enter(Context context) {
        context.initialize(ProxyLanguage.ID);
        context.enter();
        return context;
    }

    @Test
    public void testRefTwoConsecutiveContexts() {
        Context context;

        context = enter(Context.create());
        assertCompiling(createAssertConstantFromRef());
        context.leave();
        context.close();

        context = enter(Context.create());
        assertCompiling(createAssertConstantFromRef());
        context.leave();
        context.close();
    }

    @Test
    public void testRefTwoContextsAtTheSameTime() {
        Context context1 = enter(Context.create());
        assertCompiling(createAssertConstantFromRef());
        context1.leave();

        Context context2 = enter(Context.create());
        assertCompiling(createAssertConstantFromRef());
        context2.leave();

        context1.close();
        context2.close();
    }

    @Test
    public void testRefTwoContextsWithSharedEngine() {
        Engine engine = Engine.create();
        Context context1 = enter(Context.newBuilder().engine(engine).build());
        RootNode root = createAssertConstantFromRef();
        assertCompiling(root);
        context1.leave();

        Context context2 = enter(Context.newBuilder().engine(engine).build());
        // this can no longer be a constant
        assertBailout(root);
        context2.leave();

        context1.close();
        context2.close();
        engine.close();
    }

    @Test
    public void testRefTwoContextsWithSharedEngineAlreadyCompiled() {
        Engine engine = Engine.create();
        Context context1 = enter(Context.newBuilder().engine(engine).build());
        RootNode root = createAssertConstantFromRef();
        OptimizedCallTarget target = assertCompiling(root);

        assertTrue("is valid", target.isValid());
        target.call();
        assertTrue("and keeps valid", target.isValid());
        context1.leave();
        context1.close();

        Context context2 = enter(Context.newBuilder().engine(engine).build());
        // the call target needs to be invalid
        assertFalse("no longer valid", target.isValid());
        context2.leave();
        context2.close();
        engine.close();
    }

    @Test
    public void testStaticTwoConsecutiveContexts() {
        Context context;

        context = enter(Context.create());
        assertCompiling(createAssertConstantFromStatic());
        context.leave();
        context.close();

        context = enter(Context.create());
        assertCompiling(createAssertConstantFromStatic());
        context.leave();
        context.close();
    }

    @Test
    public void testStaticTwoContextsAtTheSameTime() {
        Context context1 = enter(Context.create());
        assertCompiling(createAssertConstantFromStatic());
        context1.leave();

        Context context2 = enter(Context.create());
        assertBailout(createAssertConstantFromStatic());
        context2.leave();

        context1.close();
        context2.close();
    }

    @Test
    public void testStaticTwoContextsWithSharedEngine() {
        Engine engine = Engine.create();
        Context context1 = enter(Context.newBuilder().engine(engine).build());
        RootNode root = createAssertConstantFromStatic();
        assertCompiling(root);
        context1.leave();

        Context context2 = enter(Context.newBuilder().engine(engine).build());
        // this can no longer be a constant
        assertBailout(root);
        context2.leave();

        context1.close();
        context2.close();
    }

    @Test
    public void testStaticTwoContextsWithSharedEngineAlreadyCompiled() {
        Engine engine = Engine.create();
        Context context1 = enter(Context.newBuilder().engine(engine).build());
        RootNode root = createAssertConstantFromStatic();
        OptimizedCallTarget target = assertCompiling(root);

        assertTrue("is valid", target.isValid());
        target.call();
        assertTrue("and keeps valid", target.isValid());
        context1.leave();
        context1.close();

        Context context2 = enter(Context.newBuilder().engine(engine).build());
        // the call target needs to be invalid
        assertFalse("no longer valid", target.isValid());
        context2.leave();
        context2.close();

        engine.close();
    }

    private static RootNode createAssertConstantFromRef() {
        RootNode root = new RootNode(null) {
            final ContextReference<LanguageContext> ref = ProxyLanguage.getCurrentContextReference();

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
                Object ctx = ProxyLanguage.getCurrentContext();
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

}
