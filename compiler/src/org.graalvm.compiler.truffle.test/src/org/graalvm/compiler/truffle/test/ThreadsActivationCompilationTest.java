/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.polyglot.Context;
import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ThreadsActivationListener;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class ThreadsActivationCompilationTest extends AbstractPolyglotTest {

    @Test
    public void testThreadActivationCompilation() {
        setupEnv(Context.newBuilder().allowExperimentalOptions(true).option("engine.CompileImmediately", "false").option("engine.BackgroundCompilation", "false").build());

        AtomicReference<Boolean> compiledEnter = new AtomicReference<>();
        AtomicReference<Boolean> compiledLeave = new AtomicReference<>();
        compiledEnter.set(Boolean.FALSE);
        compiledLeave.set(Boolean.FALSE);
        Assumption singleContext = Truffle.getRuntime().createAssumption();
        instrumentEnv.getInstrumenter().attachThreadsActivationListener(new ThreadsActivationListener() {
            @Override
            public void onLeaveThread(TruffleContext c) {
                if (singleContext.isValid()) {
                    CompilerAsserts.partialEvaluationConstant(c);
                    CompilerAsserts.partialEvaluationConstant(this);
                }
                if (CompilerDirectives.inCompiledCode()) {
                    compiledLeave.set(Boolean.TRUE);
                }
            }

            @Override
            public void onEnterThread(TruffleContext c) {
                if (singleContext.isValid()) {
                    CompilerAsserts.partialEvaluationConstant(c);
                    CompilerAsserts.partialEvaluationConstant(this);
                }
                if (CompilerDirectives.inCompiledCode()) {
                    compiledEnter.set(Boolean.TRUE);
                }
            }
        });

        context.enter();

        compiledEnter.set(Boolean.FALSE);
        compiledLeave.set(Boolean.FALSE);

        OptimizedCallTarget target = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                TruffleContext tc = (TruffleContext) frame.getArguments()[0];
                Object prev = tc.enter(this);

                if (CompilerDirectives.inCompiledCode()) {
                    if (!compiledEnter.get()) {
                        CompilerDirectives.shouldNotReachHere();
                    }
                }

                tc.leave(this, prev);

                if (CompilerDirectives.inCompiledCode()) {
                    if (!compiledLeave.get()) {
                        CompilerDirectives.shouldNotReachHere();
                    }
                }
                return null;
            }
        });
        TruffleContext tc = ProxyLanguage.getCurrentContext().getEnv().getContext();
        singleContext.invalidate();
        target.call(tc);
        target.compile(true);
        assertTrue(target.isValidLastTier());
        target.call(tc);

        assertTrue(target.isValidLastTier());
        assertTrue(compiledEnter.get());
        assertTrue(compiledLeave.get());

        context.leave();
        context.close();

    }
}
