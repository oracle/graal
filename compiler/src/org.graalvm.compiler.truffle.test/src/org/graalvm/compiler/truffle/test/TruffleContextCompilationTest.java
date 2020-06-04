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

import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.code.BailoutException;

public class TruffleContextCompilationTest extends PartialEvaluationTest {

    static final String LANGUAGE = "TruffleContextCompilationTestLanguage";

    private static final Object FIRST_RUN = new Object();

    @TruffleBoundary
    static void barrier() {
    }

    @Test
    public void testInnerContextsDeoptimize() {
        setupContext();
        getContext().initialize(LANGUAGE);
        Env env = Language.getCurrentContext();

        TruffleContext context = env.newContextBuilder().build();
        OptimizedCallTarget target = assertCompiling(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                Object prev = context.enter();
                try {
                    // barrier ensures that the deopt does not move up or downwards
                    barrier();
                    Object arg = frame.getArguments()[0];
                    if (arg != FIRST_RUN) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                    }
                    barrier();
                } finally {
                    context.leave(prev);
                }
                return null;
            }
        });
        assertTrue(target.isValid());
        target.call(new Object());
        assertFalse(target.isValid());
    }

    private OptimizedCallTarget assertCompiling(RootNode node) {
        try {
            return compileHelper("assertCompiling", node, new Object[]{FIRST_RUN});
        } catch (BailoutException e) {
            throw new AssertionError("bailout not expected", e);
        }
    }

    @Registration(id = LANGUAGE, name = LANGUAGE, contextPolicy = ContextPolicy.EXCLUSIVE)
    public static class Language extends TruffleLanguage<Env> {

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        public static Env getCurrentContext() {
            return getCurrentContext(Language.class);
        }

        public static TruffleLanguage<?> get() {
            return getCurrentLanguage(Language.class);
        }

    }

}
