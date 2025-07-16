/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import static org.junit.Assert.assertEquals;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.compiler.TruffleCompilerListener;
import com.oracle.truffle.runtime.AbstractCompilationTask;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedTruffleRuntimeListener;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.Assume;
import org.junit.Test;

public class MaximumCompilationsTest {
    public static class AllwaysDeoptRoot extends RootNode {

        AllwaysDeoptRoot() {
            super(null);
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return null;
        }

        @Override
        public String getName() {
            return "allwaysDeopt";
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    @Test
    public void testMaximumCompilations() {
        Assume.assumeTrue(Truffle.getRuntime() instanceof OptimizedTruffleRuntime);
        OptimizedTruffleRuntime optimizedTruffleRuntime = (OptimizedTruffleRuntime) Truffle.getRuntime();
        AtomicReference<CallTarget> callTargetRef = new AtomicReference<>();
        AtomicBoolean callTargetCompilationFailed = new AtomicBoolean();
        optimizedTruffleRuntime.addListener(new OptimizedTruffleRuntimeListener() {
            @Override
            public void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout, int tier, Supplier<String> lazyStackTrace) {
                if (target == callTargetRef.get() && "Maximum compilation count 100 reached.".equals(reason)) {
                    callTargetCompilationFailed.set(true);
                }
            }
        });

        Context.Builder builder = Context.newBuilder().option("engine.CompilationFailureAction", "Silent");
        try (Context context = (Runtime.version().feature() >= 25 ? builder.option("compiler.DeoptCycleDetectionThreshold", "-1") : builder).build()) {
            context.enter();
            CallTarget callTarget = new AllwaysDeoptRoot().getCallTarget();
            callTargetRef.set(callTarget);
            while (!callTargetCompilationFailed.get()) {
                callTarget.call();
            }
        }
    }

    @Test
    public void testUnlimitedRecompilations() {
        Assume.assumeTrue(Truffle.getRuntime() instanceof OptimizedTruffleRuntime);
        OptimizedTruffleRuntime optimizedTruffleRuntime = (OptimizedTruffleRuntime) Truffle.getRuntime();
        AtomicBoolean compilationResult = new AtomicBoolean();

        try (Engine eng = Engine.newBuilder().allowExperimentalOptions(true).//
                        option("engine.BackgroundCompilation", "false").//
                        option("engine.CompileImmediately", "true").build()) {
            try (Context ctx = Context.newBuilder().engine(eng).build()) {
                ctx.enter();
                CallTarget callTarget = new AllwaysDeoptRoot().getCallTarget();
                optimizedTruffleRuntime.addListener(new CustomListener(callTarget, compilationResult));

                for (int i = 0; i < 16; i++) {
                    callTarget.call();
                    assertEquals(true, compilationResult.get());
                }
            }
        }
    }

    @Test
    public void testMaxTwoCompilations() throws InterruptedException {
        Assume.assumeTrue(Truffle.getRuntime() instanceof OptimizedTruffleRuntime);
        OptimizedTruffleRuntime optimizedTruffleRuntime = (OptimizedTruffleRuntime) Truffle.getRuntime();
        AtomicBoolean compilationResult = new AtomicBoolean();

        try (Engine eng = Engine.newBuilder().allowExperimentalOptions(true).//
                        option("engine.BackgroundCompilation", "false").//
                        option("engine.CompileImmediately", "true").//
                        option("engine.MaximumCompilations", "2").build()) {
            try (Context ctx = Context.newBuilder().engine(eng).build()) {
                ctx.enter();

                CallTarget callTarget = new AllwaysDeoptRoot().getCallTarget();
                optimizedTruffleRuntime.addListener(new CustomListener(callTarget, compilationResult));

                callTarget.call();
                assertEquals(true, compilationResult.get());

                callTarget.call();
                assertEquals(true, compilationResult.get());

                callTarget.call();
                assertEquals(false, compilationResult.get());

                TimeUnit.SECONDS.sleep(90);

                // The method will not be recompiled because it has reached all compilations
                // possible in its lifetime
                callTarget.call();
                assertEquals(false, compilationResult.get());
            }
        }
    }

    @Test
    public void testMaxTwoCompilationsPerMinute() throws InterruptedException {
        Assume.assumeTrue(Truffle.getRuntime() instanceof OptimizedTruffleRuntime);
        OptimizedTruffleRuntime optimizedTruffleRuntime = (OptimizedTruffleRuntime) Truffle.getRuntime();
        AtomicBoolean compilationResult = new AtomicBoolean();

        try (Engine eng = Engine.newBuilder().allowExperimentalOptions(true).//
                        option("engine.BackgroundCompilation", "false").//
                        option("engine.CompileImmediately", "true").//
                        option("engine.MaximumCompilations", "2").//
                        option("engine.MaximumCompilationsWindow", "1").build()) {
            try (Context ctx = Context.newBuilder().engine(eng).build()) {
                ctx.enter();

                CallTarget callTarget = new AllwaysDeoptRoot().getCallTarget();
                optimizedTruffleRuntime.addListener(new CustomListener(callTarget, compilationResult));

                callTarget.call();
                assertEquals(true, compilationResult.get());

                callTarget.call();
                assertEquals(true, compilationResult.get());

                // This shouldn't trigger the compilation because there was already two compilations
                // of this call target in the last minute.
                callTarget.call();
                assertEquals(false, compilationResult.get());

                // Wait to make sure we overflow the compilation period
                TimeUnit.SECONDS.sleep(90);

                // this should trigger a new compilation as there was no compilation of this call
                // target in the last minute
                callTarget.call();
                assertEquals(true, compilationResult.get());
            }
        }
    }

    private class CustomListener implements OptimizedTruffleRuntimeListener {
        public CallTarget callTargetFilter = null;
        public AtomicBoolean compilationResult = new AtomicBoolean();

        CustomListener(CallTarget callTarget, AtomicBoolean compilationResult) {
            this.callTargetFilter = callTarget;
            this.compilationResult = compilationResult;
        }

        @Override
        public void onCompilationSuccess(OptimizedCallTarget target, AbstractCompilationTask task, TruffleCompilerListener.GraphInfo graph, TruffleCompilerListener.CompilationResultInfo result) {
            OptimizedTruffleRuntimeListener.super.onCompilationSuccess(target, task, graph, result);
            if (target == callTargetFilter) {
                compilationResult.set(true);
            }
        }

        @Override
        public void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout, int tier, Supplier<String> lazyStackTrace) {
            if (target == callTargetFilter) {
                compilationResult.set(false);
            }
        }
    }
}
